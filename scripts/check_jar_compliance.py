#!/usr/bin/env python3
"""Check all shipped classes, including inactive legacy code; not the official Hub verifier."""
from pathlib import Path
import re
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com/soulrend/"
REQUIRED = {
    "META-INF/LICENSE-Anvil.txt",
    PACKAGE + "SoulrendPlugin.class",
    PACKAGE + "version.txt",
    PACKAGE + "sidebar_icon.png",
    *(PACKAGE + "sounds/" + name for name in (
        "soulrend-teammate-progress.wav", "soulrend-personal-progress.wav",
        "soulrend-bingo-complete.wav", "soulrend-team-bingo-complete.wav")),
}
# Match resolved constant-pool member references, including lambda method handles.
# Byte arrays, in-memory streams and IOException are not filesystem access.
CLASS_DENY = re.compile(
    r"^(?:java/lang/reflect/|java/lang/management/|com/sun/jna/|sun/misc/Unsafe$|"
    r"jdk/internal/misc/Unsafe$|java/lang/(?:Runtime|ProcessBuilder|ClassLoader)$|"
    r"java/awt/(?:Robot|KeyboardFocusManager)$|java/net/(?:HttpURLConnection|URLClassLoader)$|"
    r"java/net/http/HttpClient$|java/io/(?:File|FileInputStream|FileOutputStream|FileReader|FileWriter|"
    r"RandomAccessFile|ObjectInputStream|ObjectOutputStream)$|java/nio/file/Files$)"
)
MEMBER_DENY = re.compile(
    r"^(?:java/lang/Thread\.(?:sleep|interrupt)|java/util/concurrent/TimeUnit\.sleep|"
    r"java/lang/Class\.(?:forName|getDeclaredMethods?|getDeclaredFields?|getDeclaredConstructors?|newInstance)|"
    r"java/awt/Desktop\.(?:open|browse)|net/runelite/client/util/LinkBrowser\.open|"
    r"net/runelite/api/Client\.menuAction|"
    r"(?:okhttp3/OkHttpClient|com/google/gson/Gson)\.\"?<init>\"?)$"
)

def main(path):
    errors = []
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()
        if len(set(names)) != len(names):
            errors.append("Duplicate JAR entries")
        for missing in REQUIRED - set(names):
            errors.append("Missing packaged resource: " + missing)
        if "META-INF/LICENSE-Anvil.txt" in names and jar.read("META-INF/LICENSE-Anvil.txt") != (ROOT / "LICENSE").read_bytes():
            errors.append("Packaged upstream license differs from repository LICENSE")
        classes = []
        for name in names:
            if name.endswith(".class"):
                if not name.startswith(PACKAGE):
                    errors.append("Unexpected bundled class: " + name)
                classes.append(name[:-6].replace("/", "."))
                header = jar.read(name)[:8]
                if len(header) != 8 or header[:4] != b"\xca\xfe\xba\xbe" or int.from_bytes(header[6:8], "big") > 55:
                    errors.append("Not Java 11 compatible: " + name)
            elif name.lower().endswith((".jar", ".so", ".dll", ".dylib", ".exe")):
                errors.append("Unexpected bundled executable: " + name)
        if not classes:
            errors.append("No compiled classes")
    # javap is provided by the JDK in CI. It reports actual constant-pool references
    # rather than comments or arbitrary string literals.
    for offset in range(0, len(classes), 50):
        output = subprocess.run(["javap", "-verbose", "-p", "-classpath", str(path),
                                 *classes[offset:offset + 50]], check=True,
                                capture_output=True, text=True).stdout
        current = ""
        for line in output.splitlines():
            if line.startswith("Classfile "):
                current = line.split("!", 1)[-1]
            if not re.match(r"\s*#\d+\s+=\s+(?:Class|Methodref|InterfaceMethodref|Fieldref)\s", line):
                continue
            if "//" not in line:
                continue
            ref = line.split("//", 1)[1].strip().split(":", 1)[0].strip('"')
            owner = ref.rsplit(".", 1)[0] if "." in ref else ref
            if CLASS_DENY.match(owner) or MEMBER_DENY.match(ref):
                errors.append(f"{current}: prohibited reference {ref}")
    if errors:
        print("\n".join(sorted(set(errors))), file=sys.stderr)
        return 1
    print(f"PASS: {len(classes)} compiled classes; Java 11, targeted forbidden references, package isolation and required resources")
    return 0

if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("Usage: check_jar_compliance.py <plugin.jar>")
    sys.exit(main(Path(sys.argv[1]).resolve()))
