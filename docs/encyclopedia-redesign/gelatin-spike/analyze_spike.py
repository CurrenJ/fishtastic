"""
Measures the gelatin-ui spike captures (docs/encyclopedia-redesign/gelatin-spike.md).

For each window/GUI-scale config it reads the layout NOTE the self-test logged and the canvas
screenshot, then checks:
  * crispness: in the white-on-black text probe, every horizontal run of white pixels must be a
    whole number of texels (texel = guiScale * k framebuffer px). Anything else = uneven glyph pixels.
  * the fractional control (same text in a 1.25x container) for contrast;
  * clipping: magenta drawn 30 px past the clip probe must stay inside the probe's box.

Run: python -I docs/encyclopedia-redesign/gelatin-spike/analyze_spike.py <client log>
"""
import collections
import os
import re
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SHOTS = os.path.join(HERE, "captures")

# keep in sync with EncyclopediaCanvasSpike
TEXT_X, TEXT_Y, TEXT_W, TEXT_H = 8, 110, 200, 30
CONTROL_X, CONTROL_Y, CONTROL_W, CONTROL_H, CONTROL_S = 220, 110, 80, 24, 1.25
CLIP_X, CLIP_Y, CLIP_W, CLIP_H = 330, 110, 60, 30


def runs(img, box):
    """Lengths of horizontal runs of near-white pixels inside box (framebuffer px)."""
    x0, y0, x1, y1 = box
    px = img.load()
    out = []
    for y in range(y0, y1):
        n = 0
        for x in range(x0, x1 + 1):
            white = x < x1 and all(c >= 235 for c in px[x, y][:3])
            if white:
                n += 1
            elif n:
                out.append(n)
                n = 0
    return out


def crisp_report(lengths, unit):
    counts = collections.Counter(lengths)
    if unit != int(unit):
        ok = 0
    else:
        ok = sum(c for L, c in counts.items() if L % int(unit) == 0)
    total = sum(counts.values())
    return ok, total, sorted(counts)[:10]


def magenta_bbox(img, region):
    x0, y0, x1, y1 = region
    px = img.load()
    xs, ys = [], []
    for y in range(max(0, y0), min(img.height, y1)):
        for x in range(max(0, x0), min(img.width, x1)):
            r, g, b = px[x, y][:3]
            if r > 240 and g < 20 and b > 240:
                xs.append(x)
                ys.append(y)
    return (min(xs), min(ys), max(xs) + 1, max(ys) + 1) if xs else None


def main(log_path):
    notes = {}
    pat = re.compile(r"gelatinspike\.layout (\S+) screen=(\d+)x(\d+) gui=(\d+) fb=(\d+)x(\d+) k=(\d+) origin=(-?\d+),(-?\d+)")
    for line in open(log_path, encoding="utf-8", errors="replace"):
        m = pat.search(line)
        if m:
            tag = m.group(1)
            notes[tag] = dict(gui=int(m.group(4)), k=int(m.group(7)), ox=int(m.group(8)), oy=int(m.group(9)),
                              screen=(int(m.group(2)), int(m.group(3))), fb=(int(m.group(5)), int(m.group(6))))
    checks = [ln.split("[selftest] ")[1].strip() for ln in open(log_path, encoding="utf-8", errors="replace")
              if "[selftest] CHECK gelatinspike" in ln]

    rows = []
    for tag, n in notes.items():
        path = os.path.join(SHOTS, f"selftest-fabric-gelatinspike-{tag}_canvas.png")
        img = Image.open(path).convert("RGB")
        g, k = n["gui"], n["k"]
        f = lambda cx, cy: ((n["ox"] + cx * k) * g, (n["oy"] + cy * k) * g)
        tx0, ty0 = f(TEXT_X, TEXT_Y)
        tx1, ty1 = f(TEXT_X + TEXT_W, TEXT_Y + TEXT_H)
        ok, total, sample = crisp_report(runs(img, (tx0, ty0, tx1, ty1)), g * k)
        cx0, cy0 = f(CONTROL_X, CONTROL_Y)
        cx1, cy1 = f(CONTROL_X + CONTROL_W * CONTROL_S, CONTROL_Y + CONTROL_H * CONTROL_S)
        cunit = g * k * CONTROL_S
        cok, ctotal, csample = crisp_report(runs(img, (int(cx0), int(cy0), int(cx1), int(cy1))), cunit)
        ex0, ey0 = f(CLIP_X, CLIP_Y)
        ex1, ey1 = f(CLIP_X + CLIP_W, CLIP_Y + CLIP_H)
        pad = 40 * k * g
        bbox = magenta_bbox(img, (ex0 - pad, ey0 - pad, ex1 + pad, ey1 + pad))
        clip_ok = bbox == (ex0, ey0, ex1, ey1)
        rows.append((tag, n, ok, total, sample, cunit, cok, ctotal, csample, bbox, (ex0, ey0, ex1, ey1), clip_ok))

    print("| Config | Screen | k | Text runs on texel grid | Control (1.25x) texel | Control runs | Clip |")
    print("|---|---|---|---|---|---|---|")
    for tag, n, ok, total, sample, cunit, cok, ctotal, csample, bbox, exp, clip_ok in rows:
        ctl = f"{cok}/{ctotal} on grid" if cunit == int(cunit) else f"lengths {csample}"
        print(f"| {tag} | {n['screen'][0]}x{n['screen'][1]} | {n['k']} | **{ok}/{total}** (lengths {sample}) "
              f"| {cunit:g} px | {ctl} | {'exact' if clip_ok else f'MISMATCH {bbox} vs {exp}'} |")
    print()
    passed = sum(1 for c in checks if ": PASS" in c)
    print(f"self-test checks: {passed}/{len(checks)} PASS")
    for c in checks:
        if ": PASS" not in c:
            print("  ", c)


if __name__ == "__main__":
    main(sys.argv[1])
