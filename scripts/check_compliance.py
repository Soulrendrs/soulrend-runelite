#!/usr/bin/env python3
"""Conservative source guard; not a substitute for Plugin Hub review."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]
patterns = {
    "retired clan coffer integration": r"\b(?:ClanCofferTracker|submitClanCoffer|submitCofferLine|syncClanCoffer)\b",
    "retired player menu lookup or vestige prediction": r"\b(?:ProfileLookup|VestigeRolls|trackVestigeRolls|profileLookupMenu)\b",
    "blocking sleep": r"\b(?:Thread\s*\.\s*sleep|TimeUnit\s*\.\s*\w+\s*\.\s*sleep)\s*\(",
    "thread interrupt": r"\bThread\s*\.\s*currentThread\s*\(\s*\)\s*\.\s*interrupt\s*\(",
    "reflection": r"\b(?:java\.lang\.reflect|Class\s*\.\s*forName|getDeclaredMethod|getDeclaredField|setAccessible)\b",
    "external process": r"\b(?:new\s+ProcessBuilder|Runtime\s*\.\s*getRuntime)\s*\(",
    "synthetic input": r"\b(?:new\s+(?:Robot|MouseEvent|KeyEvent)|invokeMenuAction|menuAction|dispatchEvent)\s*\(",
    "unapproved network clients": r"\b(?:new\s+(?:OkHttpClient|Gson)\s*\(|HttpURLConnection|java\.net\.http\.HttpClient)",
}
errors = []
for path in (root / "src/main/java").rglob("*.java"):
    source = path.read_text(encoding="utf-8")
    # Preserve line numbers and ignore comments and quoted literals.
    source = re.sub(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'',
                    lambda m: "\n" * m.group().count("\n") + " ", source)
    for label, pattern in patterns.items():
        for match in re.finditer(pattern, source):
            errors.append(f"{path.relative_to(root)}:{source.count(chr(10), 0, match.start()) + 1}: {label}")
if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
print("PASS: targeted compliance source checks")
