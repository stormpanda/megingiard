#!/usr/bin/env python3
"""generate_icon_assets.py – Generates all Android app icon assets from two source PNGs.

Usage:
    python3 scripts/generate_icon_assets.py <foreground.png> <background.png>

    foreground.png  – The belt artwork on a white background (the icon design).
    background.png  – The background color reference image.

Requirements:
    pip install Pillow

What this script does:
    1. Removes the white background from the foreground PNG → transparent RGBA.
    2. Samples the average center color of background.png.
    3. Saves the transparent foreground as:
           app/src/main/res/drawable/ic_launcher_foreground.png
       and removes ic_launcher_foreground.xml so Android resolves the PNG instead.
    4. Updates ic_launcher_background.xml with the exact sampled color.
    5. Generates composited WebP launcher icons for all five density buckets:
           mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.webp (square)
           mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_round.webp (circle-masked)
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw
except ImportError:
    sys.exit(
        "Error: Pillow is not installed.\n"
        "Run:  pip install Pillow\n"
        "      (or: pip3 install Pillow)"
    )

# ──────────────────────────────────────────────────────────────────────────────
# Paths
# ──────────────────────────────────────────────────────────────────────────────

SCRIPT_DIR   = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parent
RES_DIR      = PROJECT_ROOT / "app" / "src" / "main" / "res"
DRAWABLE_DIR = RES_DIR / "drawable"

FOREGROUND_PNG_PATH = DRAWABLE_DIR / "ic_launcher_foreground.png"
FOREGROUND_XML_PATH = DRAWABLE_DIR / "ic_launcher_foreground.xml"
BACKGROUND_XML_PATH = DRAWABLE_DIR / "ic_launcher_background.xml"

# ──────────────────────────────────────────────────────────────────────────────
# Configuration
# ──────────────────────────────────────────────────────────────────────────────

# Standard Android launcher icon sizes per density bucket
DENSITY_SIZES: dict[str, int] = {
    "mipmap-mdpi":    48,
    "mipmap-hdpi":    72,
    "mipmap-xhdpi":   96,
    "mipmap-xxhdpi":  144,
    "mipmap-xxxhdpi": 192,
}

# Foreground PNG size for the adaptive icon layer (108dp @ xxxhdpi = 4×)
ADAPTIVE_FG_SIZE = 432

# Pixels whose minimum RGB channel is at or above this value are treated as white.
# A linear alpha ramp is applied in the 20-unit zone below this threshold to
# preserve anti-aliased edges.
WHITE_THRESHOLD = 230
WHITE_FEATHER   = 20

# ──────────────────────────────────────────────────────────────────────────────
# Image helpers
# ──────────────────────────────────────────────────────────────────────────────

def remove_white_background(img: Image.Image) -> Image.Image:
    """Replace near-white pixels with transparency, feathering anti-aliased edges."""
    img = img.convert("RGBA")
    data = list(img.getdata())
    out: list[tuple[int, int, int, int]] = []
    for r, g, b, a in data:
        min_ch = min(r, g, b)
        if min_ch >= WHITE_THRESHOLD:
            out.append((r, g, b, 0))
        elif min_ch >= WHITE_THRESHOLD - WHITE_FEATHER:
            ratio   = (WHITE_THRESHOLD - min_ch) / WHITE_FEATHER
            new_a   = int(255 * ratio)
            out.append((r, g, b, new_a))
        else:
            out.append((r, g, b, a))
    img.putdata(out)
    return img


def sample_center_color(img: Image.Image) -> tuple[int, int, int]:
    """Return the average (R, G, B) from the central 50% of the image."""
    img = img.convert("RGB")
    w, h = img.size
    region = img.crop((w // 4, h // 4, 3 * w // 4, 3 * h // 4))
    pixels = list(region.getdata())
    n = len(pixels)
    r = sum(p[0] for p in pixels) // n
    g = sum(p[1] for p in pixels) // n
    b = sum(p[2] for p in pixels) // n
    return r, g, b


def fit_to_canvas(
    fg: Image.Image,
    canvas_size: int,
    bg_color: tuple[int, int, int] | None = None,
) -> Image.Image:
    """
    Scale the foreground so its WIDTH fills the canvas, then center it vertically.
    The belt design is intentionally wide – this lets it span the full icon width
    (extending slightly beyond the adaptive icon safe zone, as designed).

    If bg_color is given, returns an RGB image composited onto that solid color.
    Otherwise returns an RGBA image on a transparent background.
    """
    fg_w, fg_h = fg.size
    # Scale so width = canvas_size
    target_w = canvas_size
    target_h = max(1, round(fg_h * target_w / fg_w))

    fg_scaled = fg.resize((target_w, target_h), Image.LANCZOS)

    y_offset = (canvas_size - target_h) // 2
    if bg_color is not None:
        canvas = Image.new("RGB", (canvas_size, canvas_size), bg_color)
        canvas.paste(fg_scaled, (0, y_offset), fg_scaled)
        return canvas
    else:
        canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
        canvas.paste(fg_scaled, (0, y_offset), fg_scaled)
        return canvas


def apply_circle_mask(img: Image.Image) -> Image.Image:
    """Return an RGB image with pixels outside the inscribed circle set to black."""
    size = img.size[0]
    assert img.size[0] == img.size[1], "apply_circle_mask expects a square image"
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size - 1, size - 1), fill=255)
    result = Image.new("RGB", (size, size), (0, 0, 0))
    result.paste(img.convert("RGB"), mask=mask)
    return result


def save_webp(img: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, "WEBP", quality=90, method=6)
    print(f"  ✓  {path.relative_to(PROJECT_ROOT)}")


def update_background_xml(color_hex: str) -> None:
    """Overwrite ic_launcher_background.xml with a solid fill of color_hex."""
    content = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp"\n'
        '    android:height="108dp"\n'
        '    android:viewportWidth="108"\n'
        '    android:viewportHeight="108">\n'
        '    <path\n'
        f'        android:fillColor="{color_hex}"\n'
        '        android:pathData="M0,0h108v108h-108z" />\n'
        '</vector>\n'
    )
    BACKGROUND_XML_PATH.write_text(content, encoding="utf-8")
    print(f"  ✓  {BACKGROUND_XML_PATH.relative_to(PROJECT_ROOT)}  →  fill {color_hex}")


# ──────────────────────────────────────────────────────────────────────────────
# Main
# ──────────────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("foreground", help="Path to the foreground PNG (belt artwork on white)")
    parser.add_argument("background", help="Path to the background PNG (color reference)")
    args = parser.parse_args()

    fg_path = Path(args.foreground).resolve()
    bg_path = Path(args.background).resolve()

    for p in (fg_path, bg_path):
        if not p.exists():
            sys.exit(f"Error: File not found: {p}")

    # ── 1. Process foreground ─────────────────────────────────────────────
    print(f"\n[1/4] Processing foreground: {fg_path.name}")
    fg_raw  = Image.open(fg_path)
    fg_rgba = remove_white_background(fg_raw)
    print(f"  White background removed  ({fg_raw.width}×{fg_raw.height} px)")

    # Save as adaptive icon foreground drawable (PNG replaces the XML vector)
    DRAWABLE_DIR.mkdir(parents=True, exist_ok=True)
    fg_adaptive = fit_to_canvas(fg_rgba, ADAPTIVE_FG_SIZE)
    fg_adaptive.save(FOREGROUND_PNG_PATH, "PNG", optimize=True)
    print(f"  ✓  {FOREGROUND_PNG_PATH.relative_to(PROJECT_ROOT)}  ({ADAPTIVE_FG_SIZE}×{ADAPTIVE_FG_SIZE} px)")

    if FOREGROUND_XML_PATH.exists():
        FOREGROUND_XML_PATH.unlink()
        print(f"  ✓  Removed {FOREGROUND_XML_PATH.relative_to(PROJECT_ROOT)}"
              "  (@drawable/ic_launcher_foreground now resolves to the PNG)")

    # ── 2. Sample background color ────────────────────────────────────────
    print(f"\n[2/4] Sampling background color from: {bg_path.name}")
    bg_raw     = Image.open(bg_path)
    r, g, b    = sample_center_color(bg_raw)
    color_hex  = f"#{r:02X}{g:02X}{b:02X}"
    print(f"  Sampled color: {color_hex}  (R={r} G={g} B={b})")
    update_background_xml(color_hex)

    # ── 3. Generate mipmap WebP rasters ───────────────────────────────────
    print(f"\n[3/4] Generating mipmap WebP rasters …")
    bg_color = (r, g, b)
    for density, size in DENSITY_SIZES.items():
        flat       = fit_to_canvas(fg_rgba, size, bg_color=bg_color)
        round_icon = apply_circle_mask(flat)
        mipmap_dir = RES_DIR / density
        save_webp(flat,       mipmap_dir / "ic_launcher.webp")
        save_webp(round_icon, mipmap_dir / "ic_launcher_round.webp")

    # ── 4. Summary ────────────────────────────────────────────────────────
    print(f"\n[4/4] Done ✓")
    print(
        "\nAssets written:"
        "\n  drawable/ic_launcher_foreground.png  ← adaptive icon foreground layer"
        f"\n  drawable/ic_launcher_background.xml  ← background fill {color_hex}"
        "\n  mipmap-*/ic_launcher.webp             ← 5 density fallbacks (square)"
        "\n  mipmap-*/ic_launcher_round.webp       ← 5 density fallbacks (round)"
        "\n\nNext: Sync Project in Android Studio (File → Sync Project with Gradle Files)"
    )


if __name__ == "__main__":
    main()
