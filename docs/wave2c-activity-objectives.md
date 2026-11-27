# Wave 2C: activity objective boundary (inactive)

This slice is deliberately not registered with evidence discovery, tile validation,
upload or scoring. Do not turn on a capability until the existing evidence ledger,
manual proof service, privacy and board eligibility are integrated and tested.

## Scope and review

The inherited Anvil chat counters and GameValueCounters are not a trust boundary.
Anvil credits individual messages and inferred balance changes; do not enable the
legacy generic site-configured varbit/varp readers. Use a fixed catalog instead.
RuneLite's own ChatCommandsPlugin recognizes Sepulchre floor completions, grand
coffin openings and Hunter Guild rumours. These are candidates for retrospective
observation only; no action automation, overlays guiding obstacles, opponent data,
arbitrary IDs, external endpoints, or raw chat uploads. The grand coffin is a
reward claim, not proof of a full deathless run. Floor 5 is a floor, not proof of
all five floors in one run. No timing or raid logic (Wave 2B).

References reviewed 2026-11-27:
- https://legal.jagex.com/docs/rules/macro-and-client-features-not-permitted
- https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features
- https://github.com/runelite/runelite/wiki/Plugin-Hub-Review
- https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/chatcommands/ChatCommandsPlugin.java
- https://github.com/AhmedFathy2001/anvil-plugin

Gathering and cooking already have gain evidence; XP and boss KC already have WOM
objectives. For production not covered by gain, and activity/quantity targets
without a verified detector, use the existing ordinary manual quantity objectives
(team or solo) and require screenshots of the actual action. Do not equate inventory
changes, XP, animations, minigame participation, spendable points balances or chest
loot with completed activities. GOTR reward searches/energy, Tithe Farm, Foundry,
slayer tasks, agility laps, clues and additional production remain manual or existing
WOM where supported until exact semantics and live client evidence are established.
No LMS, diary, collection log sync, chat relay or PvP opponent tracking.

## Candidate contract

Rule: exact {kind: "activity", activity: fixed key, quantity: positive signed
32-bit integer, scope: "team"|"solo"}. Each accepted observation represents one
occurrence, never the lifetime counter or a client-supplied total. Fixed numeric
IDs in the mirrored vectors are internal contract identifiers, NOT game IDs.
A client detector must consume only local game-generated messages; the pure
parser accepts normalized text and must never be wired to player chat. Require a
fresh baseline per character/session/world/eligible rule epoch. Only a consecutive
increase of one is a candidate; duplicate, out-of-order, first, gap or reset
cannot score. Gaps rebaseline without retro-credit; a lower count cannot lower
the high-water mark during an epoch. This is deliberately conservative and can
miss the first completion. Do not substitute guessed credit. Real-client tests
must establish that reports cannot be triggered by historical queries and that
messages are delivered once in supported modes. Until then automatic tracking is
unavailable. The parser is not a proof of occurrence by itself.

## Integration requirements

Reuse the existing bearer evidence intake, same-origin/consent/capture queue,
normalized screenshot digest, RuneLiteObjectiveObservation parent, manual
Submission review, roster/account/event/unlock boundaries and scalar projection.
Do not add an activity ledger. Persist immutable activity counter identity with
the capture and reject replay across retries, sessions and team transfers; compare
capture ID, account, event, tile, activity and counter under existing locks.
Historical corrections/rejected attempts must not allow counter replay. Define
seasonal/reset identity before accepting any automatic captures. No credit before
unlock; no hidden rule discovery; preserve existing proof corrections and
deduplication. Manual fallback must combine only eligible approved claims and
prevent overlap with automatic evidence. Test Classic layouts, every other mode,
secret/final, partial approvals, withdrawal, concurrent submissions and account
switches. Official Hub approval and live gameplay tests remain release gates.
