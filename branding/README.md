# Soulrend plugin icon source and variants

This is the approved Soulrend silver-and-red flame mask supplied by the project owner.
The plugin name visible to players is **Soulrend**. The RuneLite configuration group
remains **soulrendbingo**, independent from Anvil's configuration.

- `pluginhub-48x72.png`: transparent indexed PNG, 48×72 px, approved Plugin Hub icon.
  Published at repository root as `icon.png`.
- `sidebar-32x32.png`: square transparent PNG optimized for the RuneLite side navigation
  button and its 20×20 world-map marker. Published at
  `src/main/resources/com/soulrend/sidebar_icon.png`.
- Source artwork: the owner's embroidered silver/red mask, rendered as a clean, readable
  flat emblem for RuneLite's very small display sizes.
- `src/main/resources/com/soulrend/clog_banner.png` is a notification-panel background,
  not an icon; deliberately unchanged by this branding update.

**Do not flatten the icons against black or white.** Transparency is necessary for RuneLite's
light/dark UI. The static isolation test checks transparency, the exact dimensions,
the silver/red/yellow color identity, and that the published files match these canonical assets.

RuneLite Plugin Hub instructions: https://github.com/runelite/plugin-hub/blob/master/README.md
