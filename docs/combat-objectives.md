# Wave 2B combat objective contract (inactive)

This change does not register an objective kind, enable a capture endpoint, or
award gameplay credit. It deliberately avoids modifying shared Wave 2A, 2C or
Wave 3 UI/scoring code.

## Shared contract

Fixed kind `combat_clear`. A rule has activity, exact difficulty, team/solo
scope, positive target (participant clears), optional maximum duration in
centiseconds, own_deathless boolean, optional minimum final ToA raid level.
A fact has a canonical UUIDv4 capture ID, activity, difficulty, duration_cs,
own_deaths and raid_level. Unknown measurement is null, never zero. No other
fields, player names, PBs, arbitrary regex, chat transcript or client score.
Each completed occurrence contributes one unit. Team sums eligible participant
clears; solo requires one participant to reach the target. A group raid's
multiple participating clan members are separate participant clears; counting
unique shared raids is manual-only until a reliable privacy-safe run identity
is defined. The scope does not claim a raid was solo or that the whole party
was deathless.

The candidate parser accepts Vorkath, Zulrah, Gauntlet/Corrupted, normal/CM
CoX, normal/HM ToB, normal/expert ToA, with exact difficulty and a final
game-reported time. Entry modes, Fractured Archive, individual invocation
selection, verified own-deathless, final raid level, unsupported encounters
and whole-party requirements remain manual-only. Candidate is not enabled.

## Compliance and activation gates

- [Jagex third-party client guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1)
- [RuneLite rejected features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)
- [Plugin Hub review](https://github.com/runelite/runelite/wiki/Plugin-Hub-Review)

Only post-completion local-player game signals. No predictions, combat
assistance, in-fight counters, party names, PB leaderboard or chat relay.
Do not register or send a fact until the client binds trusted game-message
types to a same-session, bounded occurrence window, invalidates on character
change, logout or ambiguous events, and reuses existing screenshot consent,
privacy, capture UUID and queue. Real client fixtures must verify each exact
mode. For own-deathless, require an observed uninterrupted start/end boundary
or trustworthy final summary; for raid level, use the final chest value, not
the lobby value. Never infer zero deaths or successful invocations from silence.
Do not export other players' information. Rule review remains required.

Server integration must reuse RuneLiteObjectiveObservation and Submission
rather than introduce a second score ledger. Persist normalized per-occurrence
combat facts in that ledger, bind to authenticated account/seat/team/event,
recorded and captured timestamps, screenshot fingerprint, unlock and rejection
boundaries. Reuse the existing review/removal/approval and duplicate-image
policy. Manual evidence should describe one qualifying run per approved claim;
pending/denied claims do not confirm a threshold. Use the scalar projection only
after eligibility/deduplication, not to grant credit from raw requests.

Negotiate a separate capability and default-off gate, then integrate existing
authoritative board-state and claim replay across layouts/modes, including
secret/final visibility. Verify concurrent replay, changed accounts, rejection,
late capture, team/solo, all relevant game modes, shared ownership, manual
review, browser editor and real client smoke tests before activation.
No changes to production configuration.
