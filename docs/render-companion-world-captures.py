"""Make review GIFs/contact sheets from companionworld's overlay-off first-person frames.

python docs/render-companion-world-captures.py fabric
Requires Pillow. Run only after the corresponding client capture has finished.
"""
from pathlib import Path
import sys
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1]
loader = sys.argv[1]
if loader not in ("fabric", "neoforge"):
    raise SystemExit("Expected fabric or neoforge")
source = root / loader / "build/companion-world-validation/screenshots"
output = root / "docs/fish-companions-world-review" / loader
output.mkdir(parents=True, exist_ok=True)
scenes = [(name, 20, 200) for name in ("jukebox", "notes", "water", "light", "warm", "sleepy", "glum")]
scenes += [("percher-left", 8, 550), ("percher-right", 8, 550)]
for feature, expected_frames, duration in scenes:
    paths = sorted(source.glob(f"selftest-{loader}-companionworld-{feature}-frame*.png"))
    if len(paths) != expected_frames:
        raise SystemExit(f"{feature}: expected {expected_frames} frames, found {len(paths)}")
    frames = []
    for path in paths:
        with Image.open(path) as image:
            frames.append(image.convert("RGB").resize((640, 360), Image.Resampling.LANCZOS))
    frames[0].save(output / f"{feature}.gif", save_all=True, append_images=frames[1:],
                   duration=duration, loop=0, optimize=False)
    sheet = Image.new("RGB", (960, 560), "#202020")
    draw = ImageDraw.Draw(sheet)
    indices = [round(i * (len(frames) - 1) / 5) for i in range(6)]
    for cell, index in enumerate(indices):
        x, y = cell % 3 * 320, cell // 3 * 280
        sheet.paste(frames[index].resize((320, 180)), (x, y + 24))
        draw.text((x + 8, y + 6), f"{feature} frame {index:02}", fill="white")
    sheet.save(output / f"{feature}-sheet.png")
    print(f"{loader}: {feature}: {len(frames)} frames")
