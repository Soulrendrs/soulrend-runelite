#!/usr/bin/env python3
"""Build-time, dependency-free isolation checks for the Soulrend RuneLite fork."""
from pathlib import Path
import re
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src/main"
ERRORS = []

def ensure(value: bool, message: str):
    if not value:
        ERRORS.append(message)

def file(path: str) -> str:
    p = ROOT / path
    ensure(p.exists(), f"missing: {path}")
    return p.read_text(encoding="utf-8") if p.exists() else ""

manifest = file("runelite-plugin.properties")
plugin = file("src/main/java/com/soulrend/SoulrendPlugin.java")
config = file("src/main/java/com/soulrend/SoulrendConfig.java")
api = file("src/main/java/com/soulrend/BingoApiClient.java")
license = file("LICENSE")
ensure("plugins=com.soulrend.SoulrendPlugin" in manifest, "Plugin Hub entry class")
ensure("displayName=Soulrend Bingo" in manifest, "Plugin Hub displayName")
ensure('name = "Soulrend Bingo"' in plugin, "RuneLite sidebar plugin descriptor name")
ensure('internalName = "soulrend-bingo"' in plugin, "RuneLite internal name")
ensure('@ConfigGroup("soulrendbingo")' in config, "unique config group")
ensure('CANONICAL_SITE = "https://soulrend.events"' in api, "fixed Soulrend destination")
ensure("BSD 2-Clause License" in license and "Copyright (c) 2026, AhmedFathy2001" in license, "upstream BSD copyright")
ensure(not (ROOT / "src/main/java/com/anvil").exists(), "no old Java package directory")
ensure(not (ROOT / "src/main/resources/com/anvil").exists(), "no old resource directory")
ensure(not (ROOT / "src/main/java/com/soulrend/AnvilPlugin.java").exists(), "no old plugin class")
for path in SRC.rglob("*"):
    if not path.is_file() or path.suffix not in (".java", ".json", ".txt", ".properties"):
        continue
    text = path.read_text(encoding="utf-8")
    for label, pattern in (
        ("old config group", r"osrsbingo"),
        ("old site address", r"anvilosrs\.com"),
        ("legacy queue", r"osrs-bingo-pending"),
        ("automatic legacy data migration", r"legacyDataDirectory\s*="),
        ("old Java package", r"com\.anvil"),
    ):
        if re.search(pattern, text, flags=re.IGNORECASE):
            ERRORS.append(f"{path.relative_to(ROOT)}: {label}")
# First-party icon should meet the documented Plugin Hub dimensions.
icon = ROOT / "icon.png"
if icon.is_file():
    b = icon.read_bytes()
    ensure(b.startswith(b"\x89PNG\r\n\x1a\n"), "root icon is PNG")
    if len(b) >= 24:
        w, h = struct.unpack(">II", b[16:24])
        ensure(w <= 48 and h <= 72, f"root icon oversized: {w}x{h}")
else:
    ERRORS.append("root icon missing")
if ERRORS:
    for m in ERRORS:
        print("ERROR:", m, file=sys.stderr)
    raise SystemExit(1)
print("PASS: unique plugin identity, isolated config and disk directories, original license, fixed Soulrend endpoint and root icon")
