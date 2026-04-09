"""
generate_icon_names.py
Generates RoundedIconNames.kt — the sorted list of all Material Symbols Rounded
icon names extracted directly from the bundled variable font via its GSUB ligature
table. This guarantees the list is always in sync with the font file.

Requires:  pip install fonttools

Usage:
    python3 scripts/generate_icon_names.py

Re-run whenever the font file is updated.
The output file is version-controlled so the app never needs to do this at runtime.
"""

import os
import re
import sys

try:
    from fontTools.ttLib import TTFont
except ImportError:
    sys.exit(
        "ERROR: fonttools is not installed.\n"
        "Run: pip install fonttools"
    )

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
FONT_FILE = os.path.join(
    SCRIPT_DIR,
    "../app/src/main/res/font/material_symbols_rounded.ttf",
)
OUTPUT_FILE = os.path.join(
    SCRIPT_DIR,
    "../app/src/main/java/com/stormpanda/megingiard/macropad/RoundedIconNames.kt",
)

# Valid Material Symbols ligature: lowercase letters, digits, underscores; ≥ 2 chars
_LIGATURE_RE = re.compile(r"^[a-z][a-z0-9_]+$")


def extract_names_from_font(ttf_path: str) -> list[str]:
    """
    Parses the OpenType GSUB table of the Material Symbols font and returns all
    icon names as snake_case strings (e.g. "arrow_back", "home").

    Material Symbols encodes every icon as a GSUB type-4 ligature (often wrapped
    in a type-7 Extension lookup): the string "home" is substituted to a single
    icon glyph when rendered.  Reading the GSUB table therefore gives the complete,
    authoritative icon name list.
    """
    font = TTFont(ttf_path)

    # Build reverse cmap: glyph name → Unicode character
    cmap = font.getBestCmap()        # code_point → glyph_name
    rev_cmap = {v: chr(k) for k, v in cmap.items()}

    snake_names: set[str] = set()

    gsub = font.get("GSUB")
    if gsub is None:
        sys.exit("ERROR: Font has no GSUB table — cannot extract ligature names.")

    for lookup in gsub.table.LookupList.Lookup:
        for subtable in lookup.SubTable:
            # Type 7 = Extension: unwrap to get the real subtable
            lig_subtable = getattr(subtable, 'ExtSubTable', subtable)
            ligs = getattr(lig_subtable, 'ligatures', None)
            if ligs is None:
                continue
            for first_glyph, lig_set in ligs.items():
                first_char = rev_cmap.get(first_glyph, "")
                if not first_char:
                    continue
                for lig in lig_set:
                    rest = "".join(rev_cmap.get(g, "") for g in lig.Component)
                    name = first_char + rest
                    if _LIGATURE_RE.match(name):
                        snake_names.add(name)

    return sorted(snake_names)


def write_kotlin(names: list[str], output_path: str, font_filename: str) -> None:
    lines = [
        "package com.stormpanda.megingiard.macropad",
        "",
        "// AUTO-GENERATED — do not edit manually.",
        "// Regenerate by running: python3 scripts/generate_icon_names.py",
        f"// Source: {font_filename} — GSUB ligature table",
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
    font_path = os.path.normpath(FONT_FILE)
    if not os.path.isfile(font_path):
        sys.exit(f"ERROR: Font not found at {font_path}")

    snake_names = extract_names_from_font(font_path)

    write_kotlin(snake_names, OUTPUT_FILE, os.path.basename(font_path))
    print(f"Generated {len(snake_names)} icons → {os.path.normpath(OUTPUT_FILE)}")


if __name__ == "__main__":
    main()
