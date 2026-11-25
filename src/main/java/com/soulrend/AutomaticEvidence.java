package com.soulrend;

import com.soulrend.PendingSubmissionStore.PendingSubmission;
import net.runelite.client.game.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.IntUnaryOperator;

/** Reuses the gameplay signals, next game frame, and durable proof store from Anvil. */
final class AutomaticEvidence {
    private static final long RULE_TTL_MS = 90_000;
    static final long EVIDENCE_FLUSH_DELAY_MS = 250;
    private static final long CA_CAPTURE_COOLDOWN_MS = 12_000;
    private static final int MAX_ACTIVE_BOARDS = 5;
    private static final int MAX_PENDING_FOR_SESSION = 40;

    private static final Set<String> GATHERING_SKILLS = Set.of("fishing", "mining", "woodcutting", "cooking", "farming", "hunter");

    static final class Rule {
        int tile;
        String kind;
        Integer item_id, quantity, task_id, npc_id;
        int[] npc_ids;
        String source, task_name, npc_name, objective_name, icon_path;
        boolean allow_repeat;

        boolean matchesNpc(int observedNpcId) {
            if (!"npc_kill".equals(kind) || npc_id == null || observedNpcId <= 0) return false;
            if (npc_ids != null && npc_ids.length > 0) {
                for (int allowed : npc_ids) if (allowed == observedNpcId) return true;
                return false;
            }
            return npc_id == observedNpcId;
        }

        boolean matchesDrop(Map<Integer, Integer> items, String actualSource) {
            return "drop".equals(kind) && item_id != null
                && items.getOrDefault(item_id, 0) > 0
                && (source == null || source.isEmpty()
                    ? "npc".equals(actualSource) || "event".equals(actualSource)
                    : source.equals(actualSource));
        }

        boolean matchesGain(String skill, Map<Integer, Integer> gains) {
            return "gain".equals(kind) && item_id != null && skill.equals(source)
                && gains.getOrDefault(item_id, 0) > 0;
        }

        boolean matchesTask(String task, boolean repeat) {
            return "ca".equals(kind) && task_id != null && task_name != null
                && task_name.strip().equalsIgnoreCase(task.strip()) && (!repeat || allow_repeat);
        }
    }

    private static final class Match {
        final int event;
        final Rule rule;

        Match(int event, Rule rule) {
            this.event = event;
            this.rule = rule;
        }
    }

    private static final class BoardRules {
        final String session;
        final String character;
        final long expires;
        final List<Match> matches;
        final int pending;

        BoardRules(String session, String character, long expires, List<Match> matches, int pending) {
            this.session = session;
            this.character = character;
            this.expires = expires;
            this.matches = matches;
            this.pending = pending;
        }
    }

    private final SoulrendPlugin plugin;
    private final AtomicBoolean syncing = new AtomicBoolean();
    private final Map<Integer, Long> attempted = new HashMap<>(); // CA de-dup only, client thread
    private final AtomicInteger queuedThisSync = new AtomicInteger();
    private final AtomicBoolean flushScheduled = new AtomicBoolean();
    private volatile BoardRules rules;
    // Sync runs on the worker. Bound retries for missing portraits; capture never waits for a fetch.
    private final Map<String, Long> portraitAttempts = new LinkedHashMap<String, Long>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Long> entry) {
            return size() > 256;
        }
    };

    private void prefetchPortraits(List<Match> matches) {
        int requested = 0;
        long now = System.currentTimeMillis();
        for (var match : matches) {
            String path = match.rule.icon_path;
            if (!BingoApiClient.isBingoIconPath(path) || plugin.apiClient.cachedObjectiveIcon(path) != null)
                continue;
            Long previous = portraitAttempts.get(path);
            if (previous != null && now - previous < 60_000) continue;
            portraitAttempts.put(path, now);
            plugin.apiClient.thumb(path, 32, 32, false, icon -> {});
            if (++requested >= 8) break;
        }
    }
    private final Map<String, Integer> skillTicks = new HashMap<>(); // client thread

    AutomaticEvidence(SoulrendPlugin plugin) {
        this.plugin = plugin;
    }

    void clear() {
        rules = null;
        queuedThisSync.set(0);
    }

    private boolean enabled() {
        return plugin.config != null && plugin.configManager != null && plugin.apiClient != null
            && EvidenceConsent.enabled(plugin.config.automaticEvidence(), plugin.apiClient.playerToken,
                plugin.cfgGet(EvidenceConsent.KEY))
            && BingoApiClient.CANONICAL_SITE.equals(BingoApiClient.site())
            && !plugin.apiClient.needsSignIn() && !plugin.apiClient.isAccountPaused();
    }

    void sync() {
        if (!enabled()) clear();
        if (!enabled() && !plugin.bingoCompletions.enabled() && !BingoStartReminder.enabled(plugin)) {
            plugin.bingoCompletions.clear();
            return;
        }
        if (!syncing.compareAndSet(false, true)) return;
        try {
            refreshAndRetry();
        } finally {
            syncing.set(false);
        }
    }

    private void refreshAndRetry() {
        String token = plugin.apiClient.playerToken;
        String character = plugin.apiClient.getCurrentRsn();
        if (token == null || token.isBlank() || character == null || character.isBlank()) {
            clear();
            plugin.bingoCompletions.clear();
            return;
        }
        String session = DiskFiles.fingerprint(token);
        if (plugin.bingoCompletions.enabled()) plugin.bingoCompletions.useScope(session, character);
        else plugin.bingoCompletions.clear();
        var events = plugin.apiClient.bingoEvents(token, character);
        if (events == null || events.events == null) return;
        plugin.bingoStartReminder.showDue(plugin, events.events, token, character);
        if (plugin.bingoCompletions.enabled()) {
            plugin.bingoCompletions.poll(events.events, token, session, character);
        }
        if (!sameSession(session, character)) {
            clear();
            return;
        }
        var matches = new ArrayList<Match>();
        var reachable = new HashSet<Integer>();
        var considered = 0;
        for (var event : events.events) {
            if (!"active".equals(event.status) || event.characters == null
                || event.characters.stream().noneMatch(name -> Rsn.same(character, name))) continue;
            if (++considered > MAX_ACTIVE_BOARDS) break;
            var response = plugin.apiClient.evidenceRules(event.id, token, character);
            if (response == null || response.rules == null) continue;
            reachable.add(event.id);
            for (var rule : response.rules) {
                if (rule != null && rule.tile > 0) matches.add(new Match(event.id, rule));
            }
        }
        if (!sameSession(session, character)) {
            clear();
            return;
        }
        var pending = plugin.pendingSubmissionStore.loadAll();
        int sent = 0;
        boolean progressUploaded = false;
        var completedEvents = new HashSet<Integer>();
        int held = (int) pending.stream().filter(proof -> proof.runeliteCaptureId != null
            && session.equals(proof.runeliteSession)).count();
        for (var proof : pending) {
            if (proof.runeliteCaptureId == null || !session.equals(proof.runeliteSession)) continue;
            if (sent >= 32 || !Rsn.same(character, proof.capturedRsn) || !sameSession(session, character)
                || !reachable.contains(proof.eventId)) continue;
            ++sent;
            var screenshot = plugin.pendingSubmissionStore.readScreenshot(proof);
            if (!sameSession(session, character)) break;
            if (screenshot == null) {
                plugin.pendingSubmissionStore.remove(proof);
                held--;
                continue;
            }
            try {
                var upload = plugin.apiClient.uploadEvidence(proof, token, screenshot);
                int status = upload.httpStatus;
                if (status >= 200 && status < 300 && sameSession(session, character)) {
                    // A small individual observation updates the website's
                    // progress bar; printing every +1 kill/drop would flood chat.
                    String message = null;
                    if ("tracking".equals(upload.proofStatus) || "approved".equals(upload.proofStatus)) {
                        progressUploaded = true;
                    } else if ("pending".equals(upload.proofStatus)) message = "Bingo evidence sent for review.";
                    else if ("rejected".equals(upload.proofStatus)) message = "Bingo evidence was rejected. See the website.";
                    if ("approved".equals(upload.proofStatus)) completedEvents.add(proof.eventId);
                    if ("npc_kill".equals(proof.evidenceKind) && "approved".equals(upload.proofStatus)
                        && upload.completion != null && upload.completion.id == proof.tileId) {
                        events.events.stream().filter(event -> event.id == proof.eventId).findFirst()
                            .ifPresent(event -> plugin.bingoCompletions.announceUploadedNpc(
                                event, upload.completion, session, character));
                    }
                    if ("approved".equals(upload.proofStatus) && !plugin.bingoCompletions.enabled()) {
                        message = "Bingo objective completed automatically.";
                    }
                    if (message != null) plugin.sendChatMessage(ChatPalette.Tone.DONE, message);
                }
                if ((status >= 200 && status < 300) || status == 400 || status == 404 || status == 413) {
                    plugin.pendingSubmissionStore.remove(proof);
                    held--;
                }
                if (status == 429 || status >= 500) break;
            } catch (IOException e) {
                break; // Leave stable capture IDs on disk for the next retry pass.
            }
        }
        if (progressUploaded && plugin.bingoCompletions.enabled()) {
            plugin.bingoCompletions.poll(events.events, token, session, character);
        }
        // Rules were fetched before uploads. Re-read only affected boards so a
        // completed parent does not leave its children untracked for another poll.
        for (int eventId : completedEvents) {
            if (!sameSession(session, character)) break;
            matches.removeIf(match -> match.event == eventId);
            var response = plugin.apiClient.evidenceRules(eventId, token, character);
            if (response != null && response.rules != null) {
                for (var rule : response.rules) {
                    if (rule != null && rule.tile > 0) matches.add(new Match(eventId, rule));
                }
            }
        }
        if (sameSession(session, character)) {
            prefetchPortraits(matches);
            rules = new BoardRules(session, character,
                System.currentTimeMillis() + RULE_TTL_MS, List.copyOf(matches), held);
            queuedThisSync.set(0);
        }
    }

    private boolean sameSession(String session, String character) {
        return enabled() && Rsn.same(character, plugin.apiClient.getCurrentRsn())
            && session.equals(DiskFiles.fingerprint(plugin.apiClient.playerToken));
    }

    private BoardRules ready() {
        var snapshot = rules;
        if (snapshot == null || System.currentTimeMillis() >= snapshot.expires
            || snapshot.pending >= MAX_PENDING_FOR_SESSION || !plugin.loggedIn()
            || plugin.statsAreArtificial() || !sameSession(snapshot.session, snapshot.character)
            || !Rsn.same(snapshot.character, plugin.getLocalPlayerName())) return null;
        return snapshot;
    }

    void drop(Collection<ItemStack> items, String source) {
        var snapshot = ready();
        if (snapshot == null || items == null || items.isEmpty()) return;
        var quantities = lootQuantities(items, id -> {
            var definition = plugin.itemManager.getItemComposition(id);
            return definition != null && definition.getNote() != -1 ? definition.getLinkedNoteId() : id;
        });
        var captures = new ArrayList<PendingSubmission>();
        var captureId = UUID.randomUUID().toString();
        for (var match : snapshot.matches) {
            if (match.rule.matchesDrop(quantities, source)) {
                var proof = prepare(snapshot, match, captureId, match.rule.item_id,
                    quantities.get(match.rule.item_id), source, false);
                if (proof != null) captures.add(proof);
            }
        }
        capture(snapshot, captures);
    }

    /** Retain exact-ID rules while also crediting the base ID of a noted drop. */
    static Map<Integer, Integer> lootQuantities(Collection<ItemStack> items, IntUnaryOperator unnote) {
        var counts = new HashMap<Integer, Integer>();
        for (var item : items) {
            int id = item.getId();
            if (id <= 0 || item.getQuantity() <= 0) continue;
            addQuantity(counts, id, item.getQuantity());
            int base = unnote.applyAsInt(id);
            if (base > 0 && base != id) addQuantity(counts, base, item.getQuantity());
        }
        return counts;
    }

    private static void addQuantity(Map<Integer, Integer> counts, int id, int quantity) {
        counts.merge(id, quantity, (first, second) ->
            (int) Math.min(Integer.MAX_VALUE, (long) first + second));
    }

    void npcKill(int npcId) {
        var snapshot = ready();
        if (snapshot == null || npcId <= 0) return;
        var captures = new ArrayList<PendingSubmission>();
        var captureId = UUID.randomUUID().toString();
        for (var match : snapshot.matches) {
            if (match.rule.matchesNpc(npcId)) {
                var proof = prepare(snapshot, match, captureId, npcId, 1, "", false);
                if (proof != null) captures.add(proof);
            }
        }
        capture(snapshot, captures);
    }

    boolean hasGatheringRules() {
        var snapshot = rules;
        return snapshot != null && snapshot.matches.stream().anyMatch(match -> "gain".equals(match.rule.kind));
    }

    void observeSkill(String skill, int tick) {
        String key = skill.toLowerCase(Locale.ROOT);
        if (GATHERING_SKILLS.contains(key)) skillTicks.put(key, tick);
    }

    void clearSkillObservations() {
        skillTicks.clear();
    }

    static String gatheringSkill(Map<String, Integer> skillTicks, int tick) {
        String eligible = null;
        for (var entry : skillTicks.entrySet()) {
            if (entry.getValue() != tick) continue;
            if (eligible != null) return null; // Cannot attribute one change to two skills.
            eligible = entry.getKey();
        }
        return eligible;
    }

    /** Called only after the fork's bank, trade, shop, storage and pickup guards pass. */
    void gathering(Map<Integer, Integer> before, Map<Integer, Integer> after, int tick) {
        var snapshot = ready();
        String skill = gatheringSkill(skillTicks, tick);
        if (snapshot == null || skill == null) return;
        var gains = new HashMap<Integer, Integer>();
        after.forEach((id, count) -> {
            int increase = count - before.getOrDefault(id, 0);
            if (increase > 0) gains.put(id, increase);
        });
        var captures = new ArrayList<PendingSubmission>();
        var captureId = UUID.randomUUID().toString();
        for (var match : snapshot.matches) {
            if (match.rule.matchesGain(skill, gains)) {
                var id = match.rule.item_id;
                var proof = prepare(snapshot, match, captureId, id, gains.get(id), skill, false);
                if (proof != null) captures.add(proof);
            }
        }
        capture(snapshot, captures);
    }

    /** A positive aggregate can identify a full batch only when every distinct task accounts for it. */
    static List<String> confirmedCombatTasks(List<SoulrendPlugin.PendingCaTask> tasks, int pointGain) {
        if (pointGain < 0) return List.of();
        var unique = new LinkedHashMap<String, CombatAchievementTier>();
        for (var task : tasks) {
            if (task.task == null || task.task.isBlank() || task.tier == null) continue;
            var name = task.task.strip().toLowerCase(Locale.ROOT);
            var previous = unique.putIfAbsent(name, task.tier);
            if (previous != null && previous != task.tier) return List.of();
        }
        int allNewPoints = unique.values().stream().mapToInt(tier -> tier.ordinal() + 1).sum();
        return pointGain == 0 || pointGain == allNewPoints ? List.copyOf(unique.keySet()) : List.of();
    }

    void combatBatch(List<SoulrendPlugin.PendingCaTask> tasks, boolean initialized, int before, int after) {
        var snapshot = ready();
        if (snapshot == null || !initialized) return;
        var captures = new ArrayList<PendingSubmission>();
        var captureId = UUID.randomUUID().toString();
        boolean repeat = after == before;
        for (var task : confirmedCombatTasks(tasks, after - before)) {
            for (var match : snapshot.matches) {
                if (match.rule.matchesTask(task, repeat)) {
                    var proof = prepare(snapshot, match, captureId, match.rule.task_id, 1, "", repeat);
                    if (proof != null) captures.add(proof);
                }
            }
        }
        capture(snapshot, captures);
    }

    private PendingSubmission prepare(BoardRules snapshot, Match match, String captureId,
                                      int evidenceId, int quantity, String source, boolean repeat) {
        long now = System.currentTimeMillis();
        boolean cumulative = "drop".equals(match.rule.kind) || "gain".equals(match.rule.kind)
            || "npc_kill".equals(match.rule.kind);
        if (cumulative) {
            // Repeated drops/kills are distinct legitimate observations:
            // cooldown-based suppression would silently lose cumulative gains.
            if (queuedThisSync.incrementAndGet() + snapshot.pending > MAX_PENDING_FOR_SESSION) {
                queuedThisSync.decrementAndGet();
                return null;
            }
        } else {
            attempted.values().removeIf(at -> now - at >= CA_CAPTURE_COOLDOWN_MS);
            if (attempted.size() >= MAX_PENDING_FOR_SESSION - snapshot.pending
                || attempted.putIfAbsent(match.rule.tile, now) != null) return null;
        }
        var pending = new PendingSubmission();
        pending.runeliteCaptureId = captureId;
        pending.runeliteSession = snapshot.session;
        pending.capturedRsn = snapshot.character;
        pending.eventId = match.event;
        pending.tileId = match.rule.tile;
        pending.evidenceKind = match.rule.kind;
        pending.evidenceId = evidenceId;
        pending.amount = quantity;
        pending.evidenceSource = source;
        pending.evidenceRepeat = repeat;
        pending.label = "Bingo evidence";
        pending.objectiveName = match.rule.objective_name;
        pending.evidenceIconPath = match.rule.icon_path;
        if ("npc_kill".equals(pending.evidenceKind)) {
            pending.evidenceName = match.rule.npc_name;
        } else if ("ca".equals(pending.evidenceKind)) {
            pending.evidenceName = match.rule.task_name;
        } else if ("drop".equals(pending.evidenceKind) || "gain".equals(pending.evidenceKind)) {
            var item = plugin.itemManager.getItemComposition(evidenceId);
            if (item != null) pending.evidenceName = item.getName();
        }
        pending.timestamp = now;
        return pending;
    }

    private void capture(BoardRules snapshot, List<PendingSubmission> captures) {
        if (captures.isEmpty()) return;
        plugin.onEvidenceFrame(image -> {
            if (!sameSession(snapshot.session, snapshot.character)) return;
            try {
                boolean saved = false;
                for (var proof : captures) {
                    if (!sameSession(snapshot.session, snapshot.character)) break;
                    var card = EvidenceCaptureCard.forProof(proof);
                    card.icon = plugin.apiClient.cachedObjectiveIcon(card.iconPath);
                    byte[] screenshot = SoulrendPlugin.jpeg(EvidenceCaptureCard.annotate(image, card));
                    if (plugin.pendingSubmissionStore.save(proof, screenshot) != null) {
                        saved = true;
                        plugin.clientThread.invokeLater(() -> {
                            if (sameSession(snapshot.session, snapshot.character) && plugin.config.evidenceCaptureBanner())
                                plugin.clogBanner.bingoCards.offer(card);
                        });
                    }
                }
                if (saved) flushSoon();
            } catch (IOException ignored) {
                // No evidence file was committed. Never send a capture without an image.
            }
        });
    }

    /** Keep the server's progress counter responsive after each game occurrence. */
    private void flushSoon() {
        var executor = plugin.executor;
        if (executor == null || executor.isShutdown() || !flushScheduled.compareAndSet(false, true)) return;
        try {
            executor.schedule(() -> {
                flushScheduled.set(false);
                if (enabled()) sync();
            }, EVIDENCE_FLUSH_DELAY_MS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ignored) {
            flushScheduled.set(false);
        }
    }
}
