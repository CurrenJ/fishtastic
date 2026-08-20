"""Upscale every fish item texture with nearest-neighbor (pixel-art safe).

Usage:
    python tools/upscale-fish-textures.py (--scale N | --width PX) [--src DIR] --out DIR

Animated textures (32x64, 32x192, ...) upscale fine because nearest-neighbor
scales every frame uniformly; the accompanying .mcmeta files are copied as-is.
"""

import argparse
import shutil
from pathlib import Path

from PIL import Image

REPO = Path(__file__).resolve().parent.parent
DEFAULT_SRC = REPO / "common/src/main/resources/assets/fishtastic/textures/item/fish"


def main():
    p = argparse.ArgumentParser()
    g = p.add_mutually_exclusive_group()
    g.add_argument("--scale", type=int, help="integer multiplier applied to every texture")
    g.add_argument("--width", type=int, help="target width in px; the factor is derived per texture "
                                             "(height follows, so animation sheets keep their aspect)")
    p.add_argument("--src", type=Path, default=DEFAULT_SRC)
    p.add_argument("--out", type=Path, required=True)
    args = p.parse_args()

    if args.width is None and args.scale is None:
        args.scale = 2
    if args.scale is not None and args.scale < 2:
        p.error("--scale must be >= 2")

    args.out.mkdir(parents=True, exist_ok=True)

    for png in sorted(args.src.glob("*.png")):
        with Image.open(png) as img:
            img = img.convert("RGBA")
            w, h = img.size
            if args.width is not None:
                if args.width % w:
                    p.error(f"{png.name}: width {w} does not divide target {args.width} evenly")
                scale = args.width // w
            else:
                scale = args.scale
            img.resize((w * scale, h * scale), Image.NEAREST).save(args.out / png.name)
        print(f"{png.name}: {w}x{h} -> {w * scale}x{h * scale} ({scale}x)")

    for meta in sorted(args.src.glob("*.mcmeta")):
        shutil.copy2(meta, args.out / meta.name)
        print(f"{meta.name}: copied")


if __name__ == "__main__":
    main()
