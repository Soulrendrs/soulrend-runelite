# Plugin Hub preparation — phases A–D

This change is a conservative first hardening pass, not approval. Official guidance:
- https://github.com/runelite/runelite/wiki/Plugin-Hub-Review
- https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features
- https://github.com/runelite/plugin-hub-tooling/blob/master/templateplugin/AGENTS.md

## Network inventory (production)
All plugin HTTP clients derive from the injected OkHttp client. Only HTTPS soulrend.events:443 and the /api/runelite/v1/ family plus exact public objective artwork paths are accepted; redirects are disabled. Test launchers may set a separate exact origin. No image request for public artwork includes account headers.
- Device start: random installation identifier, platform, display label, plugin version; device polling: device code; browser approval is user initiated.
- Identity, event/board/rules, completion and inbox reads: Soulrend bearer token and, where needed, selected character/event identifier; notification mutations: token and notification identifier.
- Evidence writes: bearer token, event/character, tile ID, capture UUID/time, observed objective ID, kind, source/count or CA repeat flag, JPEG game frame. Requires the automatic evidence preference.
- Rename check: bearer token and observed character.
- Public objective artwork: path and plugin version, no bearer.
- Revocation: bearer token.
- Network metadata (IP, user agent) necessarily reaches the service. No Jagex password is collected by this flow.

Inherited /api/plugin and old clan-scoped routes remain blocked in production. Roster export is retired at the caller and transport method; raw recent chat is no longer added to legacy proof metadata; observed teammate names are no longer exported in cooperative fingerprints. Local profile lookup suggestions are not an automatic roster upload.

## Consent
Connection has an explicit confirmation before contacting the service. Automatic evidence and optional legacy sharing/import toggles default off, with warnings. Previously stored explicit configuration values are not automatically erased; re-consent and all alternative capture/queue paths require further review. PR #43 independently changes the evidence display and must be reconciled.

## Remaining review
Inspect every production route and backend retention/access, screenshot redaction, PvP/other-player information, dynamic objective rules, game-widget modifications, and all remaining inherited paths. Verify official bytecode checks and live manual client behavior; this source scanner is deliberately incomplete. Do not claim compliance certification or submit until these and asset licensing are resolved.
