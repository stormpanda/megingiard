"""
generate_icon_names.py
Generates RoundedIconNames.kt — the sorted list of all Icons.Rounded names
from the material-icons-extended library bundled in the Gradle cache.

Usage:
    python3 scripts/generate_icon_names.py

Re-run whenever the Compose BOM / material-icons-extended version is updated.
The output file is version-controlled so the app never needs to do this at runtime.
"""

import os
import zipfile
import glob
import sys

GRADLE_CACHE = os.path.expanduser(
    "~/.gradle/caches/modules-2/files-2.1"
    "/androidx.compose.material/material-icons-extended-android"
)
OUTPUT_FILE = os.path.join(
    os.path.dirname(__file__),
    "../app/src/main/java/com/stormpanda/megingiard/macropad/RoundedIconNames.kt",
)


def find_aar() -> str:
    pattern = os.path.join(GRADLE_CACHE, "**", "material-icons-extended-release.aar")
    matches = glob.glob(pattern, recursive=True)
    if not matches:
        sys.exit(
            "ERROR: material-icons-extended AAR not found in Gradle cache.\n"
            "Run a Gradle sync first so the dependency is downloaded."
        )
    # Pick the newest version by path depth / name
    return sorted(matches)[-1]


def extract_rounded_names(aar_path: str) -> list[str]:
    names = []
    with zipfile.ZipFile(aar_path) as aar:
        with aar.open("classes.jar") as raw:
            import io
            jar_bytes = io.BytesIO(raw.read())
    with zipfile.ZipFile(jar_bytes) as jar:
        for entry in jar.namelist():
            if (
                entry.startswith("androidx/compose/material/icons/rounded/")
                and entry.endswith("Kt.class")
            ):
                names.append(entry.split("/")[-1].replace("Kt.class", ""))
    return sorted(names)


def write_kotlin(names: list[str], output_path: str, version: str) -> None:
    lines = [
        "package com.stormpanda.megingiard.macropad",
        "",
        "// AUTO-GENERATED — do not edit manually.",
        "// Regenerate by running: python3 scripts/generate_icon_names.py",
        f"// Source: material-icons-extended {version}, Icons.Rounded set",
        "",
        "internal val ALL_ROUNDED_ICON_NAMES: List<String> = listOf(",
    ]
    for i, name in enumerate(names):
        comma = "," if i < len(names) - 1 else ""
        lines.append(f'    "{name}"{comma}')
    lines.append(")")
    output_path = os.path.normpath(output_path)
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, "w") as f:
        f.write("\n".join(lines))


def main() -> None:
    aar = find_aar()
    # Extract version from path: …/1.6.8/hash/…
    parts = aar.replace("\\", "/").split("/")
    version = "unknown"
    for i, part in enumerate(parts):
        if part == "material-icons-extended-android" and i + 1 < len(parts):
            version = parts[i + 1]
            break

    names = extract_rounded_names(aar)
    write_kotlin(names, OUTPUT_FILE, version)
    print(f"Generated {len(names)} Rounded icons → {os.path.normpath(OUTPUT_FILE)}")


if __name__ == "__main__":
    main()
