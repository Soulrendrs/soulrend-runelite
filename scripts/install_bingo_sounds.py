#!/usr/bin/env python3
"""Install the owner's licensed, normalized WAV cues under stable plugin resource names.

Usage:
    python3 scripts/install_bingo_sounds.py /path/to/normalized-wavs
    python3 scripts/install_bingo_sounds.py /path/to/normalized-wavs.zip
"""
import argparse
import hashlib
from pathlib import Path, PurePosixPath
from zipfile import ZipFile

RESOURCES = Path(__file__).resolve().parents[1] / "src/main/resources/com/soulrend/sounds"
CUES = {
    "mixkit-happy-bell-alert-601-normalized.wav": "soulrend-teammate-progress.wav",
    "mixkit-retro-game-notification-212-normalized.wav": "soulrend-personal-progress.wav",
    "mixkit-small-win-2020-normalized.wav": "soulrend-bingo-complete.wav",
}


def load_normalized(source: Path, original: str) -> bytes:
    if source.is_dir():
        return (source / original).read_bytes()
    if source.is_file() and source.suffix.lower() == ".zip":
        with ZipFile(source) as archive:
            matches = [
                entry for entry in archive.namelist()
                if not entry.endswith("/") and PurePosixPath(entry).name == original
            ]
            if len(matches) != 1:
                raise ValueError(f"{original}: expected exactly one ZIP entry; found {len(matches)}")
            return archive.read(matches[0])
    raise ValueError("Source must be a directory with normalized WAVs or a ZIP archive")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="Directory or ZIP containing the three normalized WAVs")
    args = parser.parse_args()
    payloads = {}
    for original, target in CUES.items():
        try:
            content = load_normalized(args.source, original)
        except (OSError, ValueError, KeyError) as exc:
            parser.error(f"Unable to load {original}: {exc}")
        if len(content) < 44 or content[:4] != b"RIFF" or content[8:12] != b"WAVE":
            parser.error(f"{original} is not a valid RIFF/WAVE file")
        payloads[target] = content

    # Do not partially install a subset of sounds: validate all three first.
    RESOURCES.mkdir(parents=True, exist_ok=True)
    for target, content in payloads.items():
        out = RESOURCES / target
        out.write_bytes(content)
        print(f"{out.relative_to(RESOURCES.parent)}: {len(content)} bytes, SHA256 {hashlib.sha256(content).hexdigest()}")
    print("Installed all three cues. Commit the WAV files and run ./gradlew clean test build.")


if __name__ == "__main__":
    main()
