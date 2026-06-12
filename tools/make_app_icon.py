"""Generate all Android launcher icons from one square source image.

Usage (from the Android repo root):
  uv run --with pillow python tools/make_app_icon.py <source.png>

Outputs:
  - res/drawable-nodpi/ic_launcher_foreground_art.png  (adaptive foreground,
    432px canvas, artwork scaled into the safe zone on transparency)
  - res/values/ic_launcher_colors.xml                  (background = sampled
    from the source image corner, so foreground blends seamlessly)
  - res/mipmap-{m,h,x,xx,xxx}dpi/ic_launcher.webp + ic_launcher_round.webp
    (legacy icons: square resize + circle-masked variant)
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw

RES = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
FOREGROUND_CANVAS = 432  # 108dp @ 4x
SAFE_ZONE_RATIO = 0.62   # artwork fits the adaptive-icon safe zone


def main() -> None:
    src_path = Path(sys.argv[1])
    img = Image.open(src_path).convert("RGBA")
    side = min(img.size)
    img = img.crop(((img.width - side) // 2, (img.height - side) // 2,
                    (img.width + side) // 2, (img.height + side) // 2))

    # Background color = corner pixel (averaged 8x8 patch for stability).
    patch = img.crop((2, 2, 10, 10)).resize((1, 1))
    r, g, b, _ = patch.getpixel((0, 0))
    bg_hex = f"#{r:02X}{g:02X}{b:02X}"
    (RES / "values" / "ic_launcher_colors.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        f'    <color name="ic_launcher_background_color">{bg_hex}</color>\n'
        "</resources>\n", encoding="utf-8")

    # Adaptive foreground: artwork centered in the safe zone on transparency.
    art = img.resize((int(FOREGROUND_CANVAS * SAFE_ZONE_RATIO),) * 2, Image.LANCZOS)
    fg = Image.new("RGBA", (FOREGROUND_CANVAS, FOREGROUND_CANVAS), (0, 0, 0, 0))
    off = (FOREGROUND_CANVAS - art.width) // 2
    fg.paste(art, (off, off), art)
    fg.save(RES / "drawable-nodpi" / "ic_launcher_foreground_art.png")

    # Legacy mipmaps.
    for density, size in DENSITIES.items():
        scaled = img.resize((size, size), Image.LANCZOS)
        out_dir = RES / f"mipmap-{density}"
        scaled.convert("RGB").save(out_dir / "ic_launcher.webp", quality=92)
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
        round_img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        round_img.paste(scaled, (0, 0), mask)
        round_img.save(out_dir / "ic_launcher_round.webp")

    print(f"icons written (background {bg_hex}) from {src_path}")


if __name__ == "__main__":
    main()
