# Plugin Hub preparation — phases A–D

This change is a conservative first hardening pass, not approval. Official guidance:
- https://github.com/runelite/runelite/wiki/Plugin-Hub-Review
- https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features
- https://github.com/runelite/plugin-hub-tooling/blob/master/templateplugin/AGENTS.md

## Network inventory (production)
All plugin HTTP clients derive from the injected OkHttp client. Only HTTPS soulrend.events:443 and explicitly enumerated /api/runelite/v1/ endpoints/methods plus exact public objective artwork paths are accepted; redirects are disabled. Test launchers may set a separate exact origin. No image request for public artwork includes account headers.
- Device start: random installation identifier, platform, display label, plugin version; device polling: device code; browser approval is user initiated.
- Identity, event/board/rules, completion and inbox reads: Soulrend bearer token and, where needed, selected character/event identifier; notification mutations: token and notification identifier.
- Evidence writes: bearer token, event/character, tile ID, capture UUID/time, observed objective ID, kind, source/count or CA repeat flag, JPEG game frame. Requires the automatic evidence preference.
- Rename check: bearer token and observed character.
- Public objective artwork: path and plugin version, no bearer.
- Revocation: bearer token.
- Network metadata (IP, user agent) necessarily reaches the service. No Jagex password is collected by this flow.

Inherited /api/plugin and old clan-scoped routes remain blocked in production. Roster export is retired at the caller and transport method; raw recent chat is no longer added to legacy proof metadata; observed teammate names are no longer exported in cooperative fingerprints. Local profile lookup suggestions are not an automatic roster upload.

## Consent
Connection offers explicit choices to connect with or without automatic evidence, plus cancel. The default-focused action does not enable evidence; successful authentication is required before saving a decision. Existing connected sessions without a versioned consent marker receive a sidebar review action; automatic evidence (capture and retry) is blocked until they decide, even if the old preference is true. The marker is tied to the session token fingerprint, not the token itself. Disabling the RuneLite setting stops new automatic evidence; queued images can retry on re-enable, and an already-started upload may finish. Optional legacy sharing/import toggles default off; alternative legacy capture/queue paths and backend retention still require review. PR #43 independently changes evidence display and must be reconciled.

## Second audit
Production does not load legacy cached config, poll old config, capture legacy facts/notifications/start shots, replay legacy proof queues, render legacy supporter cosmetics in game, or report vestige roll predictions. This quarantines unsupported inherited functionality; physical removal is still required before Hub submission. Automatic bingo screenshots redact chat/input/prompt and split-PM regions in a copied frame instead of hiding live game widgets. This conservative redaction also removes visible game-message text in the scroll area; verify that the remaining objective evidence is sufficient and verify widget bounds in both fixed/resizable and stretched layouts manually. The source and tests are not official approval.

## Remaining review
Inspect every production route and backend retention/access, screenshot redaction, PvP/other-player information, dynamic objective rules, game-widget modifications, and all remaining inherited paths. Verify official bytecode checks and live manual client behavior; this source scanner is deliberately incomplete. Do not claim compliance certification or submit until these and asset licensing are resolved.

## Phases E–G: 2026-11-26 follow-up

Baseline main: `252e1fbcfc6582c92b7b4ebb2c81b92f14aadb51`. Its post-merge CI passed (run 45794364961).
Current RuneLite rejected-feature list was updated 2026-11-23. See the references above and
https://github.com/runelite/plugin-hub/blob/master/README.md.

### E: inherited paths and dynamic rules
Production now skips legacy player spawn, hitsplat, death, interaction and player-loot
handlers, nearby/clan/friends lookup suggestions, in-game profile menu additions, and
starting-shot routing. Instance membership names are no longer collected by the tick path
on the canonical site. This is further quarantine, not physical removal of the imported
code. Generic own loot observations remain passive, including explicitly selected PvP
loot (no victim name in the v1 proof payload). The ordinary combined drop rule still
matches NPC/reward-window drops only. Fixed loot/skill source sets, positive item IDs and nonnegative CA IDs
are checked in the client. A server rule is a selector over locally observed events, not
executable code; no attack, movement, prayer or menu automation is intended.
RuneLite's prohibition on unrestricted ID-based plugins warrants explicit reviewer
confirmation of the catalog-selected bingo objective model. No claim of preapproval.

### F: packaging and assets
The standard Hub build replaces this project's Gradle scripts. Put the original BSD notice
in the resources tree itself and keep the development JAR equality check. The new CI
bytecode/resource check examines every packaged class, including dormant code, for a
conservative list of forbidden references; it is not RuneLite's official checker.
No additional production runtime dependencies are declared in the repository Gradle file;
Lombok is compile-time, JUnit and jshell are tests. The actual Hub build remains unverified.

**Release blocker:** Mixkit's sound effects license permits incorporation in end products
but prohibits redistribution with source files, in tools/templates, or on their own:
https://mixkit.co/license/modal/sfxFree/. The three supplied Mixkit WAVs need separate
documented redistribution permission covering the public source repo and plugin JAR,
or replacement with appropriately licensed/original sounds. The fourth recording
(winning-sound-effect-button.mp3) lacks source and rights documentation. A file name change,
normalization, or project-owner assertion is insufficient evidence of the rights.
Do not publish to Plugin Hub until all four are resolved. Existing assets not changed here.

### G: backend and real client verification
Reviewed website `bingo/runelite_auth.py`, `bingo/runelite_evidence.py` and objective
matrix on main. Device bearer tokens are hashed in storage with expiry and revocation;
the evidence endpoint authenticates the session, limits requests and validates allowed
sources. This is not a full backend privacy/authorization audit.
Still required: trace screenshot storage/serving, retention/deletion and backups; verify
all guest/member/team/staff access boundaries; verify account unlink/revoke and queued
evidence behavior in a real client; test fixed/resizable/stretched screenshot redaction,
no-chat leakage, offline retries, own/team-only sidebar and Wilderness/PvP loot.
Verify actual Hub packager output (including BSD notice), native consent prompt and
official reviewer feedback. Do not equate passing repository CI with Hub approval.
