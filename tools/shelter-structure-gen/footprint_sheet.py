"""
Top-down contact sheet of every multi-cell structure's floor footprint, current against proposed
(footprint_audit.proposed). Writes tools/shelter-structure-gen/preview/footprints.png.

    python tools/shelter-structure-gen/footprint_sheet.py
"""
import json
import math
import os
import sys

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle

sys.path.insert(0, os.path.dirname(__file__))
import footprint_audit as fa

SKIP_PREFIX = ("cosmetic_fence_arch_", "cosmetic_lamp_")
KEEP = {"cosmetic_fence_arch_oak"}


def draw(ax, name, data):
    parts = fa.boxes(data)
    old, new = fa.claimed_keys(data), fa.proposed(data, parts)
    cells = {k: (x, z) for k, _, x, z in fa.candidates(data)}
    for b in sorted(parts, key=lambda b: (not b[5], b[4] < fa.PLANT_HEIGHT)):
        x0, x1, z0, z1, y0, soft = b
        if soft:
            colour = "#9bd59b"
        elif y0 < fa.PLANT_HEIGHT:
            colour = "#444444"
        else:
            colour = "#c8c8c8"
        ax.add_patch(Rectangle((x0, z0), x1 - x0, z1 - z0, color=colour, lw=0, zorder=1))
    for k, (x, z) in cells.items():
        if k in new:
            ax.add_patch(Rectangle((x, z), fa.CW, fa.CW, facecolor="#2e9e4f", alpha=0.30, lw=0, zorder=2))
        if k in old:
            ax.add_patch(Rectangle((x, z), fa.CW, fa.CW, fill=False, ec="#d62728", lw=1.6, zorder=3,
                                   ls="-" if k in new else "--"))
    if "span" in data:
        sp = data["span"]
        xs, zs = (0, sp["x"]), (0, sp["z"])
        for bx in range(sp["x"] + 1):
            ax.axvline(bx, color="k", lw=0.8, zorder=4)
        for bz in range(sp["z"] + 1):
            ax.axhline(bz, color="k", lw=0.8, zorder=4)
    else:
        used = [cells[k] for k in old | new]
        lo_x = min(min(x for x, _ in used), -fa.CW * 1.5)
        lo_z = min(min(z for _, z in used), -fa.CW * 1.5)
        xs = (lo_x, max(max(x for x, _ in used) + fa.CW, fa.CW * 1.5))
        zs = (lo_z, max(max(z for _, z in used) + fa.CW, fa.CW * 1.5))
    for k, (x, z) in cells.items():
        ax.add_patch(Rectangle((x, z), fa.CW, fa.CW, fill=False, ec="#999999", lw=0.3, zorder=2))
    ax.set_xlim(*xs)
    ax.set_ylim(zs[1], zs[0])  # z grows toward the viewer (south = front) at the bottom
    ax.set_aspect("equal")
    ax.set_xticks([])
    ax.set_yticks([])
    ax.set_title(f"{name}\n{len(old)} -> {len(new)} cells", fontsize=8)


def main():
    names = []
    for f in sorted(os.listdir(fa.DIR)):
        n = f[:-5]
        if n.startswith(SKIP_PREFIX) and n not in KEEP:
            continue
        with open(os.path.join(fa.DIR, f), encoding="utf-8") as fh:
            data = json.load(fh)
        if "span" not in data and len(data["footprint_cells"]) <= 1:
            continue
        names.append((n, data))
    spans = [nd for nd in names if "span" in nd[1]]
    floors = [nd for nd in names if "span" not in nd[1]]
    cols = 6
    rows_f = math.ceil(len(floors) / cols)
    rows_s = math.ceil(len(spans) / 3)
    fig = plt.figure(figsize=(cols * 2.4, rows_f * 2.6 + rows_s * 3.2))
    gs = fig.add_gridspec(rows_f + rows_s, cols, height_ratios=[2.6] * rows_f + [3.2] * rows_s)
    for i, (n, d) in enumerate(floors):
        draw(fig.add_subplot(gs[i // cols, i % cols]), n, d)
    for i, (n, d) in enumerate(spans):
        r, c = rows_f + i // 3, (i % 3) * 2
        draw(fig.add_subplot(gs[r, c:c + 2]), n, d)
    fig.suptitle("Floor footprints, top down (front at the bottom). Red outline = claimed now "
                 "(dashed = would be freed); green = proposed.\nDark = solid parts within 0.3 of the sand; "
                 "light grey = solid higher up; pale green = soft parts.", fontsize=9)
    fig.tight_layout(rect=(0, 0, 1, 0.97))
    out = os.path.join(os.path.dirname(__file__), "preview", "footprints.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    fig.savefig(out, dpi=110)
    print(out)


if __name__ == "__main__":
    main()
