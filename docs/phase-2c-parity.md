# Phase 2C bingo parity

This is a code and test inventory. Live validation against a staged Django deployment remains separate.

| Objective | Fork code reused | Soulrend support in this phase | Remaining validation or gap |
| --- | --- | --- | --- |
| Item drops | NPC loot, normalized reward container events, next game frame, proof storage | Configured item ID, minimum stack and optional source category. Automatic capture, retries and private review when enabled. | Verify NPC, raid, Barrows and clue signals in RuneLite. Inventory-diff sources and chat-only inferred items are excluded. |
| New and repeated Combat Achievements | Existing CA chat parser and points baseline | Organizer binds the exact task name to a stable task ID. Repeat requires an explicit rule. | Verify live messages with repeat announcements enabled. Mixed batches with a points increase are skipped because the delta cannot identify the new task. |
| Skills and boss KC | StatChanged and the fork's KC parser | Client session observations, separate WOM-backed scoring. | Bosses without reliable observable counts use official WOM progress only. |
| Collection log and diaries | Existing unlock and completion handlers | Website proof remains available. | Dedicated evidence rules and live tests still needed. |
| Timed and deathless clears | Existing raid timing and death gates | Website proof remains available. | Review reliable start, completion and death signals per raid before adding automatic evidence. |

The plugin setting is off by default. Django must separately enable its first-party evidence API.
The local queue uses the original session and character. A rotated or revoked session does not inherit old evidence.
