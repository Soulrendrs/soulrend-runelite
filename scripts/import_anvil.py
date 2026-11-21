#!/usr/bin/env python3
"""Import the pinned BSD-licensed Anvil Java plugin as an isolated Soulrend fork.

Executed by GitHub Actions against a separately checked-out, SHA-pinned upstream.
This importer is intentionally one-shot. It never writes to the private Django repo.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import struct
import zlib

UPSTREAM_SHA = "7e45a7708b8513e90e651ff685ba5329a2896aa5"
ALLOWED_ROOT_FILES = {
    "LICENSE", ".gitignore", "build.gradle", "settings.gradle",
    "gradlew", "gradlew.bat", "runelite-plugin.properties", "icon.png",
}
TEXT_EXTENSIONS = {".java", ".gradle", ".properties", ".txt", ".json", ".md", ".sh", ".bat", ".yml", ".yaml"}
BLOCKED_MAIN_PATTERNS = {
    "Anvil configuration group": re.compile(r"osrsbingo", re.IGNORECASE),
    "Anvil website endpoint": re.compile(r"anvilosrs\.com", re.IGNORECASE),
    "Anvil legacy proof queue": re.compile(r"osrs-bingo-pending", re.IGNORECASE),
    "cross-plugin directory migration": re.compile(r"legacyDataDirectory\s*=", re.IGNORECASE),
}


def allowed(path: Path) -> bool:
    parts = path.parts
    if path.as_posix() in ALLOWED_ROOT_FILES:
        return True
    if parts[0] == "gradle":
        return True
    if parts[0] == "src" and len(parts) > 2 and parts[1] in ("main", "test"):
        return True
    return False


def transform_path(path: Path) -> Path:
    # Java package, class/fixture filenames, resource directory and test path are all unique.
    return Path(path.as_posix().replace("Anvil", "Soulrend").replace("anvil", "soulrend"))


def text_transform(path: Path, source: str) -> str:
    if path.as_posix() == "LICENSE":
        return source  # Preserve original copyright and license verbatim.

    # The upstream plugin *moves* a shared legacy proof directory on startup;
    # the Soulrend fork must never migrate another plugin's evidence.
    if path.as_posix().endswith("AnvilPlugin.java"):
        source, n = re.subn(
            r'(?ms)^\s*// ONE FOLDER MOVES, AND THIS IS THE ONLY ONE WORTH MOVING\..*?'
            r'^\s*legacyDataDirectory\s*=\s*"osrs-bingo-pending"\s*',
            "",
            source,
            count=1,
        )
        if n != 1:
            raise RuntimeError("Upstream PluginDescriptor migration did not match audited version")
        source = source.replace('internalName = "anvil",', 'internalName = "soulrend-bingo"')
        source = source.replace(
            'Companion plugin for the Anvil clan-events platform — codeword overlay, auto-submits tracked bingo drops, clan Discord notifications',
            'Soulrend Bingo companion (development build; site integration pending)',
        )
    if path.as_posix().endswith("PendingSubmissionStore.java"):
        # The fork has an independent root (getPluginDirectory), never a migrated Anvil directory.
        source = re.sub(
            r'(?ms)^\s*\* THE ROOT ITSELF, not a subfolder, and that is the migration.s doing rather than a preference:.*?'
            r'^\s*\* finding the very files the migration exists to carry across\.\s*\n',
            " * The queue uses only the Soulrend plugin's dedicated directory.\n",
            source,
            count=1,
        )
    # Do URL/legacy replacements before the generic branding replacement.
    source = source.replace("anvilosrs.com", "soulrend.events")
    source = source.replace("osrsbingo", "soulrendbingo")
    source = source.replace("osrs-bingo-pending", "soulrend-proof-pending")
    source = source.replace("ANVIL", "SOULREND")
    source = source.replace("Anvil", "Soulrend")
    source = source.replace("anvil", "soulrend")
    if path.name == "runelite-plugin.properties":
        source = (
            "displayName=Soulrend Bingo\n"
            "author=Soulrendrs\n"
            "build=standard\n"
            "description=RuneLite companion for Soulrend Bingo Portal. Account linking and site integration are under development.\n"
            "tags=soulrend,bingo,clan,event,screenshot,loot,objective\n"
            "plugins=com.soulrend.SoulrendPlugin\n"
        )
    if path.name == "settings.gradle":
        source = "rootProject.name = 'soulrend-runelite'\n"
    if path.name == "build.gradle":
        source = source.replace("group = 'com.soulrend'", "group = 'com.soulrend'")
    if path.as_posix().endswith("src/main/resources/com/anvil/version.txt"):
        source = "0.1.0-dev\n"
    return source


def chunk(tag: bytes, payload: bytes) -> bytes:
    return struct.pack(">I", len(payload)) + tag + payload + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF)


def icon_png(n: int) -> bytes:
    """Simple original S mark; deterministic standard-library RGBA PNG, no reused Anvil artwork."""
    glyph = [
        "  ####  ",
        " ##  ## ",
        " ##     ",
        "  ####  ",
        "     ## ",
        " ##  ## ",
        "  ####  ",
    ]
    scale = max(1, n // 12)
    x0, y0 = (n - 8 * scale) // 2, (n - 7 * scale) // 2
    buf = bytearray()
    for y in range(n):
        buf.append(0)  # PNG scanline filter
        for x in range(n):
            bg = (42, 19, 68, 255)
            gx, gy = (x - x0) // scale, (y - y0) // scale
            is_s = x >= x0 and y >= y0 and 0 <= gy < 7 and 0 <= gx < 8 and glyph[gy][gx] == "#"
            buf.extend((230, 211, 255, 255) if is_s else bg)
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", n, n, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(bytes(buf), 9))
        + chunk(b"IEND", b"")
    )


def run(upstream: Path, destination: Path) -> None:
    if not (upstream / "src/main/java/com/anvil/AnvilPlugin.java").exists():
        raise RuntimeError("Expected pinned Anvil plugin checkout not found")
    if (destination / "src/main/java/com/soulrend/SoulrendPlugin.java").exists():
        print("Source already imported; skipping one-time bootstrap")
        return

    copied = []
    for file in sorted(upstream.rglob("*")):
        if not file.is_file():
            continue
        rel = file.relative_to(upstream)
        if ".git" in rel.parts or not allowed(rel):
            continue
        target = destination / transform_path(rel)
        target.parent.mkdir(parents=True, exist_ok=True)
        if rel.suffix in TEXT_EXTENSIONS or rel.as_posix() in {".gitignore", "LICENSE", "gradlew"}:
            content = file.read_text(encoding="utf-8")
            target.write_text(text_transform(rel, content), encoding="utf-8")
            if rel.name == "gradlew":
                target.chmod(0o755)
        else:
            target.write_bytes(file.read_bytes())
        copied.append(target.relative_to(destination).as_posix())

    # Never ship old logos. Keep resource names used by the compiled Java code.
    (destination / "icon.png").write_bytes(icon_png(40))
    for basename in ("sidebar_icon.png", "clog_banner.png", "tenure.png"):
        file = destination / "src/main/resources/com/soulrend" / basename
        if file.exists():
            file.write_bytes(icon_png(32))

    (destination / "UPSTREAM.md").write_text(
        "# Fork origin and license\n\n"
        "This repository is an independently named adaptation of the BSD-2-Clause licensed "
        "[Anvil Java plugin](https://github.com/AhmedFathy2001/anvil-plugin), "
        f"pinned at commit [{UPSTREAM_SHA}](https://github.com/AhmedFathy2001/anvil-plugin/commit/{UPSTREAM_SHA}).\n\n"
        "The upstream LICENSE is copied **verbatim**, including original copyright and disclaimer. "
        "The separately licensed Anvil website is not incorporated.\n\n"
        "Initial source changes: Java package com.soulrend, Soulrend class names and "
        "Gradle metadata, RuneLite display/internal name, config group soulrendbingo, "
        "fixed https://soulrend.events service origin, independent plugin data directory, "
        "and original replacement branding icon. "
        "The upstream legacyDataDirectory migration is REMOVED so installation does not "
        "move existing Anvil proof queues. Anvil routes were not yet implemented in Django; "
        "this source import is a development starting point, not a working release.\n",
        encoding="utf-8",
    )

    violations = []
    for root in ("src/main", "runelite-plugin.properties"):
        path = destination / root
        files = path.rglob("*") if path.is_dir() else [path]
        for f in files:
            if not f.is_file() or f.suffix not in TEXT_EXTENSIONS:
                continue
            text = f.read_text(encoding="utf-8")
            for label, pattern in BLOCKED_MAIN_PATTERNS.items():
                if pattern.search(text):
                    violations.append(f"{f.relative_to(destination)}: {label}")
    if violations:
        raise RuntimeError("Plugin isolation audit failed: \n" + "\n".join(violations))
    if not (destination / "LICENSE").read_bytes() == (upstream / "LICENSE").read_bytes():
        raise RuntimeError("Upstream LICENSE was altered")
    manifest = (destination / "runelite-plugin.properties").read_text(encoding="utf-8")
    if "plugins=com.soulrend.SoulrendPlugin" not in manifest:
        raise RuntimeError("Wrong entry point")
    if (destination / "src/main/java/com/soulrend/SoulrendPlugin.java").exists() is False:
        raise RuntimeError("Renamed plugin class missing")
    print(f"Imported {len(copied)} upstream files, with BSD license and strict config/data isolation")


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("upstream", type=Path)
    p.add_argument("destination", type=Path)
    a = p.parse_args()
    run(a.upstream.resolve(), a.destination.resolve())
