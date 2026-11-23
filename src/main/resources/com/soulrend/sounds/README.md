# Soulrend bingo notification audio assets

Three small, normalized (−18 LUFS) WAV resources belong **in this folder**, under these exact names:

| Event | Resource filename |
| --- | --- |
| Team-mate progress | `mixkit-happy-bell-alert-601-normalized.wav` |
| Personal progress | `mixkit-retro-game-notification-212-normalized.wav` |
| Completed bingo objective (BINGOOOOO!) | `mixkit-small-win-2020-normalized.wav` |

The recordings were selected and uploaded by the Soulrend project owner. Do not replace them with generic OSRS game effects. They are not currently committed to this draft PR; **do not merge until the binary WAV files are added** and verified via the RuneLite client.

The RuneLite `AudioPlayer.play(Class, String, gain)` API reads them as classpath resources, so no extra download or file access is required during playback. Plugin preferences use a shared 0–100% gain control that defaults to 40%, plus an independent mute toggle for each cue. Preview buttons are in the panel's sound settings.

Mixkit Sound Effects Free License: https://mixkit.co/license/modal/sfxFree/ (verify final release terms). Record the original sound attribution/source in the PR for future maintainers.

Recommended acceptance tests after bundling:
1. Run `./gradlew test` and `./gradlew runClient` on a workstation with a RuneLite client.
2. Use all three sound Preview buttons at default volume.
3. Confirm real events are categorized correctly and team progress is not duplicated.
4. Verify audio does not overlap unboundedly or replay on reconnection.
5. Verify that old manually imported banner/mission sounds still work.

**Note:** The `README.md` is documentation only. Missing WAV files fall back to the existing imported banner sounds, but the new selected Mixkit cues cannot play until the assets are included.
