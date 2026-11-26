# Wave 1: retain and isolate useful Anvil components

Current production support stays drop, gain, npc_kill and ca. ObjectiveRule
extracts pure matching from AutomaticEvidence, preserving Gson's wire subtype.
The v1 fixture is mirrored in Soulrendrs/soulrend-bingos
bingo/data/runelite_objective_contract_v1.json; update both in coordinated PRs.

## Retain for later extraction, do not enable inherited endpoints

| Planned owner | Reusable code | Restriction |
| --- | --- | --- |
| Items | RuleEngine, DropSource, inventory-diff helpers, GameValueCounters | Server-authoritative sets and valuation; no arbitrary client totals |
| PvM | TimedClearParser, RaidGates, death/raid parsers | Curated post-completion signals; no opponent or party name exports; no unofficial PB hiscores |
| Activities | ChatPatterns, GameValueCounters, ActivityStats | Reviewed game-origin signals; no arbitrary server regex |
| Team UI | TileNotes, TileTracker, existing overlay layout | Own-team data only; voluntary claims, no automatic player tracking |
| Evidence | PendingSubmissionStore, EvidenceFramePrivacy | Consent, redaction and stable capture identity |
| Recaps | Event counters, activity summaries | Optional own-player event-scoped telemetry |

LMS placements, achievement diary objectives, collection-log sync, chat relay,
roster export and PvP opponent tracking are excluded. Existing production
quarantine remains; do not reactivate old Anvil network, config or UI paths.

Future types require explicit capabilities, default-off rollout, server rule
validation, immutable observation ledger, manual fallback and regression vectors.
The v1 JSON remains unchanged. Do not send unknown kinds to older clients.
Use fixed activity/source sets, not downloaded executable behavior or patterns.

## Remaining compliance

PR #51 was merged. Its targeted bytecode scanner is not an official Plugin Hub
verification. The owner states that redistribution rights for all four bundled
sounds are covered; retain the audio and keep supporting authorization available
for reviewers. No independent license verification is claimed.

Physical removal of unrelated legacy functionality, complete backend privacy
and retention checks, actual Hub packaging and live client validation remain.
Do not remove the listed useful parsers before replacement coverage exists.

## Physical cleanup slice

Removed the inherited right-click profile menu, its settings/event listeners,
and the vestige prediction feature (including server-supplied roll tables).
Neither was enabled in production. Existing loot collection, supported evidence,
raid completion parsers and item/overlay components remain intact. Historical
vestige config keys are ignored; no stored user data is deleted. The targeted
source guard prevents these retired implementations from being reintroduced.

Removed the unused Clan Coffer widget polling, chat attribution, settings,
tracker and network methods. The general widget walker and gameplay chat
parsing remain for supported evidence and future reviewed activities.

Removed the retired clan-roster synchronization entry points, hidden configuration,
roster-readability polling, dormant header button and sidebar action. General
team management, active-account identity and own clan membership are unaffected.

Removed personal-best import from RuneLite's profile configuration and upload
of personal-best records. Kept the pure time parsers and existing parser tests
for future event-scoped timed-clear work. Historical local PB keys are ignored;
no stored records are deleted. The character-switch highlight reset remains.

Removed the live game-name painting subsystem, its frame/script/menu hooks,
settings, supporter-list networking and chat icon registration. Ordinary
Soulrend notifications now use plain player names. Existing screenshot
privacy helpers and sidebar artwork remain; pure ChatFlair text helpers and
badge art are retained for shared parsing and existing sidebar compatibility.

PvP cleanup: player loot and opened Wilderness loot keys are rejected before the shared loot pipeline; automatic evidence also rejects explicit `pvp` rules. Opponent hit/death attribution, named kill submissions, and PvP roster deserialization are removed. NPC/raid loot and timed/deathless parser helpers remain. Team-only deathless validation stays fail-closed pending a reviewed server-side contract; no opponent roster is accepted. The quarantined legacy recap payload retains a zero-valued PvP field for compatibility.

## Wave 2A: canonical item batches (inactive)
The mirrored v2 item-batch vectors define canonical noted/unnoted aggregation: each raw stack contributes once, duplicate stacks combine, alias chains/cycles and integer overflow reject the entire batch. Limits are 256 raw stacks and signed 32-bit positive IDs/counts. The resolver must use trusted local item composition or a server-owned catalog; the eventual server must independently validate identity and reject unknown aliases. Raw stack identity and quantities must be retained, not replaced by a client-supplied canonical total. The v1 single-item matcher is unchanged. This is a pure helper only, not a new evidence route, feature flag, editor, observation store, or score award. A batch capture ID must eventually identify one occurrence per eligible tile, with item lines atomically accepted together; integrate with #366 instead of adding a second ledger. Loot valuation must use recorded server-owned prices, not plugin totals or historical revaluation.

## Wave 2A: item pool rule (inactive)
The fixed v2 rule is `kind: item_pool`, `strategy: sets|distinct|quantity`, `scope: team|solo`, positive 32-bit `target`, and `items: [{item_id, quantity}]` (1–128 unique canonical catalog IDs). Each line quantity is positive; distinct and alternative quantity pools require unit weights, and a distinct target cannot exceed pool size. Sets use per-item requirements and target number of full sets. Only ordinary NPC and reward-window loot match. No PvP, pickpocket, arbitrary rules, client price or wildcard sources. The server must independently resolve catalog identity and retain the rule snapshot; the plugin selector cannot award score. GP valuation has a separate future contract. This parser and mirrored vectors do not register a live evidence kind, capability or editor. The eventual intake must reject duplicate JSON keys at the raw boundary before parsing and integrate with the reconciled #366 ledger.

## Wave 2A: batch evidence boundary (inactive)
One UUIDv4 per observed loot occurrence is reused for matching tiles and retries. For each tile the client prepares only relevant raw `[item_id, quantity]` stacks, sorted deterministically, with `capture_id` and the fixed NPC/reward source; no client totals/prices, unrelated loot or player names. The server strictly parses bounded UTF-8 JSON, rejects duplicate fields, unknown IDs, unrelated lines and invalid UUIDs, and independently derives canonical quantities. Retry comparison includes the original raw stack multiset, UUID and source, not recomputed quantities; the actual ledger must also compare event/tile/team/participant/account/captured-at and proof identity. Use one existing observation parent with per-item child lines and one screenshot digest, atomically under existing portal lock. Do not create one parent per item or a second independent proof ledger. Keep the existing unique capture constraint, cross-team replay checks and rejected-attempt cutoff; aggregate eligible child quantities per player only after all existing filters. Manual per-item claims require submitted/approved lines tied to existing Submission review and duplicate-image exclusion; a scalar manual claim must never satisfy an item pool. No schema changes, persistence, upload, proof scoring or feature activation in this slice; #366 is still draft.

## Wave 2A: client capability, capture and durable transport
The existing consent/session-scoped automatic evidence worker now advertises item_batch=2 during evidence discovery; older servers ignore the query and return ordinary v1 rules. Fixed pool/loot_value rules select relevant raw NPC or reward-window stacks only, and one existing capture UUID is shared across matches. The original JSON batch is persisted with the existing masked screenshot and retried using the same first-party multipart route; legacy proof envelopes stay unchanged. No prices, client totals, unrelated stacks or new external endpoints. GP receipt totals use signed 64-bit values. Until independent server note mapping exists, a relevant noted stack causes that advanced batch to be skipped rather than rewriting the raw ID; ordinary v1 behavior is unchanged. Server launch gate remains false, so no production advanced rules are advertised. Actual game-client testing, note mapping, other modes and website UI remain pending.
