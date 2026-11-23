# Soulrend bingo notification audio assets

Three small, normalized (−18 LUFS) WAV resources belong **in this folder**, under these exact names:

| Event | Resource filename |
| --- | --- |
| Team-mate progress | `mixkit-happy-bell-alert-601-normalized.wav` |
| Personal progress | `mixkit-retro-game-notification-212-normalized.wav` |
| Completed bingo objective (BINGOOOOO!) | `mixkit-small-win-2020-normalized.wav` |

The Mixkit Sound Effects Free License permits incorporation into a finished game
but restricts redistribution with source files. The three WAVs are ignored in Git.
For a local client build, extract the owner's ZIP at the repository root. Gradle
automatically puts the files in the JAR under `com/soulrend/sounds/`.

For a Plugin Hub release, obtain written permission that covers the Hub's source
build or use recordings with terms that explicitly permit redistributing the WAVs
alongside the plugin source.

RuneLite's `AudioPlayer` loads the packaged classpath resources. No gameplay
downloads are required. The 0–100% notification volume defaults to 40% and has
a separate mute switch for each cue. In a checkout that lacks the WAVs, the three
bingo cues are silent. User-imported banners and mission audio have their own
existing playback paths.

License: https://mixkit.co/license/modal/sfxFree/
