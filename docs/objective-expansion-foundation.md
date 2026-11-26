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
