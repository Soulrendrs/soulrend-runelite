package com.soulrend;

import com.soulrend.PendingSubmissionStore.PendingSubmission;
import net.runelite.client.game.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Reuses the gameplay signals, next game frame, and durable proof store from Anvil. */
final class AutomaticEvidence {
    private static final long RULE_TTL_MS = 90_000;
    private static final long CAPTURE_COOLDOWN_MS = 12_000;
    private static final int MAX_ACTIVE_BOARDS = 5;
    private static final int MAX_PENDING_FOR_SESSION = 40;

    private static final Set<String> GATHERING_SKILLS = Set.of("fishing", "mining", "woodcutting", "cooking", "farming", "hunter");

    static final class Rule {
        int tile;
        String kind;
        Integer item_id, quantity, task_id, npc_id, diary_id;
        String source, task_name, item_name, diary_name;
        boolean allow_repeat;

        boolean matchesDrop(Map<Integer, Integer> items, String actualSource) {
            return "drop".equals(kind) && item_id != null
                && items.getOrDefault(item_id, 0) >= (quantity == null ? 1 : quantity)
                && (source == null || source.isEmpty() || source.equals(actualSource));
        }

        boolean matchesGain(String skill, Map<Integer, Integer> gains) {
            return "gain".equals(kind) && item_id != null && skill.equals(source)
                && gains.getOrDefault(item_id, 0) >= (quantity == null ? 1 : quantity);
        }

        Integer namedId(String type, String name) {
            if (!type.equals(kind)) return null;
            String expected = "diary".equals(type) ? diary_name : item_name;
            if (expected == null || !expected.strip().equalsIgnoreCase(name.strip())) return null;
            return "diary".equals(type) ? diary_id : item_id;
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
    private final Map<Integer, Long> attempted = new HashMap<>(); // client thread
    private volatile BoardRules rules;
    private final Map<String, Integer> skillTicks = new HashMap<>(); // client thread

    AutomaticEvidence(SoulrendPlugin plugin) {
        this.plugin = plugin;
    }

    void clear() {
        rules = null;
    }

    private boolean enabled() {
        return plugin.config != null && plugin.config.automaticEvidence()
            && plugin.apiClient != null && BingoApiClient.CANONICAL_SITE.equals(BingoApiClient.site())
            && !plugin.apiClient.needsSignIn() && !plugin.apiClient.isAccountPaused();
    }

    void sync() {
        if (!enabled()) {
            clear();
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
            return;
        }
        String session = DiskFiles.fingerprint(token);
        var events = plugin.apiClient.bingoRead("", token, character, BingoApiClient.EventList.class);
        if (events == null || events.events == null) return;
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
        int held = (int) pending.stream().filter(proof -> proof.runeliteCaptureId != null
            && session.equals(proof.runeliteSession)).count();
        for (var proof : pending) {
            if (proof.runeliteCaptureId == null || !session.equals(proof.runeliteSession)) continue;
            if (sent >= 8 || !Rsn.same(character, proof.capturedRsn) || !sameSession(session, character)
                || !reachable.contains(proof.eventId)) continue;
            ++sent;
            var screenshot = plugin.pendingSubmissionStore.readScreenshot(proof);
            if (screenshot == null) {
                plugin.pendingSubmissionStore.remove(proof);
                held--;
                continue;
            }
            try {
                int status = plugin.apiClient.uploadEvidence(proof, token, screenshot);
                if (status >= 200 && status < 300 && sameSession(session, character)) {
                    plugin.sendChatMessage(ChatPalette.Tone.DONE, "Bingo evidence sent for review.");
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
        if (sameSession(session, character)) rules = new BoardRules(session, character,
            System.currentTimeMillis() + RULE_TTL_MS, List.copyOf(matches), held);
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
        var quantities = new HashMap<Integer, Integer>();
        for (var item : items) {
            if (item.getId() > 0 && item.getQuantity() > 0) {
                quantities.merge(item.getId(), item.getQuantity(),
                    (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
            }
        }
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

    void npcKill(int npcId) {
        var snapshot = ready();
        if (snapshot == null || npcId <= 0) return;
        var captures = new ArrayList<PendingSubmission>();
        var captureId = UUID.randomUUID().toString();
        for (var match : snapshot.matches) {
            if ("npc_kill".equals(match.rule.kind) && Integer.valueOf(npcId).equals(match.rule.npc_id)) {
                var proof = prepare(snapshot, match, captureId, npcId, 1, "", false);
                if (proof != null) captures.add(proof);
            }
        }
        capture(snapshot, captures);
    }

    void named(String kind, String name) {
        var snapshot = ready();
        if (snapshot == null || name == null) return;
        var captures = new ArrayList<PendingSubmission>();
        var captureId = UUID.randomUUID().toString();
        for (var match : snapshot.matches) {
            var id = match.rule.namedId(kind, name);
            if (id != null) {
                var proof = prepare(snapshot, match, captureId, id, 1, "", false);
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

    void combatBatch(List<SoulrendPlugin.PendingCaTask> tasks, boolean initialized, int before, int after) {
        var snapshot = ready();
        if (snapshot == null || !initialized || after < before) return;
        var unique = new HashSet<String>();
        for (var task : tasks) if (task.task != null) unique.add(task.task.strip().toLowerCase(Locale.ROOT));
        // An aggregate points increase cannot identify the new task in a mixed batch.
        if (after > before && unique.size() != 1) return;
        boolean repeat = after == before;
        var captures = new ArrayList<PendingSubmission>();
        var captureId = UUID.randomUUID().toString();
        for (var task : unique) {
            if (task.isEmpty()) continue;
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
        attempted.values().removeIf(at -> now - at >= CAPTURE_COOLDOWN_MS);
        if (attempted.size() >= MAX_PENDING_FOR_SESSION - snapshot.pending
            || attempted.putIfAbsent(match.rule.tile, now) != null) return null;
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
        pending.timestamp = now;
        return pending;
    }

    private void capture(BoardRules snapshot, List<PendingSubmission> captures) {
        if (captures.isEmpty()) return;
        plugin.onNextFrame(image -> {
            if (!sameSession(snapshot.session, snapshot.character)) return;
            try {
                byte[] screenshot = SoulrendPlugin.jpeg(image);
                for (var proof : captures) plugin.pendingSubmissionStore.save(proof, screenshot);
            } catch (IOException ignored) {
                // No evidence file was committed. Never send a capture without an image.
            }
        }, null);
    }
}
