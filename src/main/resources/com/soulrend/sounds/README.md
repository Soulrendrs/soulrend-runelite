# Soulrend bingo notification sounds

The four owner-supplied, normalized WAV assets must be committed in this directory and
packaged in the RuneLite plugin JAR. They are deliberately renamed for stable, descriptive
classpath paths. Do not rename/re-encode them again after installation.

| Notification | Bundled filename | Original supplied filename |
| --- | --- | --- |
| Teammate objective progress | `soulrend-teammate-progress.wav` | `mixkit-happy-bell-alert-601-normalized.wav` |
| Your objective progress | `soulrend-personal-progress.wav` | `mixkit-retro-game-notification-212-normalized.wav` |
| Confirmed objective completion (BINGOOOOO!) | `soulrend-bingo-complete.wav` | `mixkit-small-win-2020-normalized.wav` |
| Entire team bingo completion | `soulrend-team-bingo-complete.wav` | `winning-sound-effect-button.mp3` |

To install the normalized recordings from the owner's local files:

```sh
python3 scripts/install_bingo_sounds.py /path/to/normalized-audio-directory
# or:
python3 scripts/install_bingo_sounds.py /path/to/soulrend-normalized-notification-sounds.zip
./gradlew clean test build
```

Commit the three resulting `soulrend-*.wav` files alongside the Java changes. The fourth, team victory WAV is supplied separately and must remain in this directory.
The `BannerSoundServiceTest` suite fails when any required resource is missing, so
GitHub CI cannot incorrectly report a release-ready build containing silent cues.

The cue mapping uses RuneLite AudioPlayer, plays packaged files offline without
network downloads, and preserves existing per-category switches and the adjustable
25% default volume. These resources are separate from user-imported banner/mission
WAV folders.

Source: recordings supplied by project owner and derived from Mixkit sound effects
601, 212, and 2020. The owner states they have distribution permission. **A changed
filename does not change licensing terms.** Permission must cover distribution of
the sound files in a publicly available plugin source repository / RuneLite Plugin Hub;
keep the relevant license or authorization evidence with the project.

Mixkit sound effects license: https://mixkit.co/license/modal/sfxFree/

The team victory recording was separately supplied by the project owner. Its original source and redistribution authorization must be documented before public Plugin Hub distribution; it is not covered by the Mixkit attribution above.
