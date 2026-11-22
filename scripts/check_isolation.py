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
ensure("displayName=Soulrend\n" in manifest, "Plugin Hub displayName")
ensure('name = "Soulrend",' in plugin, "RuneLite sidebar plugin descriptor name")
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
# Plugin Hub limits the icon to 48x72 px. Validate the shipped artwork and
# the matching sidebar image (which RuneLite also uses as a world-map marker).
# Python stdlib-only so the check runs without Pillow or extra CI dependencies.
def check_brand_icon(path, width, height, canonical):
    asset = ROOT / path
    brand = ROOT / canonical
    if not asset.exists():
        ERRORS.append(f"Missing Soulrend icon: {path}")
        return
    if not brand.exists():
        ERRORS.append(f"Missing official brand source: {canonical}")
        return
    raw = asset.read_bytes()
    ensure(raw == brand.read_bytes(), f"{path} must match approved {canonical}")
    if not raw.startswith(b"\x89PNG\r\n\x1a\n") or len(raw) < 33:
        ERRORS.append(f"{path} is not PNG")
        return
    w, h, depth, kind, compression, filtering, interlace = struct.unpack(">IIBBBBB", raw[16:29])
    ensure((w, h) == (width, height), f"{path}: expected {width}x{height}, found {w}x{h}")
    ensure((depth, kind, compression, filtering, interlace) == (8, 3, 0, 0, 0),
           f"{path}: must be 8-bit indexed PNG, no interlace")
    chunks = {}
    offset = 8
    while offset < len(raw):
        if offset + 12 > len(raw):
            ERRORS.append(f"{path}: incomplete PNG chunk")
            return
        chunk_len = struct.unpack(">I", raw[offset:offset + 4])[0]
        tag = raw[offset + 4:offset + 8]
        end = offset + 12 + chunk_len
        if end > len(raw):
            ERRORS.append(f"{path}: truncated PNG payload")
            return
        chunks.setdefault(tag, raw[offset + 8:offset + 8 + chunk_len])
        offset = end
        if tag == b"IEND":
            break
    ensure(b"IDAT" in chunks and b"IEND" in chunks, f"{path}: incomplete PNG")
    palette = chunks.get(b"PLTE", b"")
    alpha = chunks.get(b"tRNS", b"")
    ensure(len(palette) >= 3 and len(palette) % 3 == 0, f"{path}: invalid PNG palette")
    ensure(bool(alpha) and min(alpha) == 0 and max(alpha) == 255, f"{path}: transparent background is required")
    colors = [palette[k:k + 3] for k in range(0, len(palette), 3)]
    ensure(any(r > 170 and g < 120 and b < 120 for r, g, b in colors), f"{path}: red Soulrend marking missing")
    ensure(any(r > 170 and g > 130 and b < 130 for r, g, b in colors), f"{path}: yellow Soulrend eye missing")
    ensure(any(min(r, g, b) > 160 for r, g, b in colors), f"{path}: silver Soulrend mask missing")

check_brand_icon("icon.png", 48, 72, "branding/pluginhub-48x72.png")
check_brand_icon("src/main/resources/com/soulrend/sidebar_icon.png", 32, 32, "branding/sidebar-32x32.png")
if ERRORS:
    for m in ERRORS:
        print("ERROR:", m, file=sys.stderr)
    raise SystemExit(1)
print("PASS: unique plugin identity, isolated config and disk directories, original license, fixed Soulrend endpoint and root icon")
