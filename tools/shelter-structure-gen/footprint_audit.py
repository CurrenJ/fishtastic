"""
Audit of how much of each claimed floor grid cell a cosmetic structure actually fills.

For every structure JSON, prints its claimed cells (footprint_cells for a floor piece; the span's
derived footprint, per tank, for a span) and for each cell: the fraction of the cell's area under
solid parts at any height ("solid"), the fraction of its central half-width square under solid parts
below PLANT_HEIGHT ("core-low": where a neighbouring plant would clip), and whether only soft parts
(seagrass, coral, leaves, lanterns...) or high overhangs put it in the footprint.

    python tools/shelter-structure-gen/footprint_audit.py [name...]           # report
    python tools/shelter-structure-gen/footprint_audit.py --apply name...     # write occupied_cells

The generators (shelter-structure-gen, span-structure-gen) call with_occupied() on every export;
--apply is for hand-captured structures. docs/fish-shelters.md §12.10.
"""
import json
import math
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DIR = os.path.join(ROOT, "common/src/main/resources/data/fishtastic/fishtastic/cosmetic_structure")
WALL = 1 / 16
CW = (1 - 2 * WALL) / 3
DEFAULT_SCALE = CW  # CosmeticStructure codec default
PLANT_HEIGHT = 0.30  # blocks above the sand a floor cosmetic in a neighbouring cell roughly reaches
N = 24               # raster samples per cell edge

SOFT_EXACT = {"kelp", "kelp_plant", "seagrass", "tall_seagrass", "vine", "pink_petals", "leaf_litter",
              "moss_carpet", "crimson_roots", "warped_roots", "short_grass", "fern", "sea_pickle",
              "red_mushroom", "brown_mushroom", "lantern", "soul_lantern", "copper_lantern"}


def soft(name):
    n = name.split(":")[-1]
    if n in SOFT_EXACT or n.endswith("leaves") or n.endswith("_lantern"):
        return True
    return "coral" in n and not n.endswith("coral_block")


def boxes(data):
    """Each part as (x0, x1, z0, z1, y0, soft) in blocks. Floor: relative to the anchor cell's
    centre. Span: relative to the box's min corner."""
    s = data.get("scale", DEFAULT_SCALE)
    out = []
    for p in data["parts"]:
        name = p["state"]["Name"]
        if "span" in data:
            x0 = WALL + p.get("offsetX", 0) * s
            z0 = WALL + p.get("offsetZ", 0) * s
        else:
            x0 = p.get("offsetX", 0) * CW - s / 2
            z0 = p.get("offsetZ", 0) * CW - s / 2
        out.append((x0, x0 + s, z0, z0 + s, p.get("offsetY", 0) * s, soft(name)))
    return out


def coverage(parts, cx0, cz0, solid_only, max_y=None, core=False):
    """Fraction of the cell [cx0, cx0+CW] x [cz0, cz0+CW] (or its central half) under parts."""
    lo, w = (CW / 4, CW / 2) if core else (0, CW)
    rel = [b for b in parts if (not solid_only or not b[5]) and (max_y is None or b[4] < max_y)
           and b[1] > cx0 + lo and b[0] < cx0 + lo + w and b[3] > cz0 + lo and b[2] < cz0 + lo + w]
    hit = 0
    for i in range(N):
        x = cx0 + lo + (i + 0.5) * w / N
        for j in range(N):
            z = cz0 + lo + (j + 0.5) * w / N
            if any(b[0] <= x < b[1] and b[2] <= z < b[3] for b in rel):
                hit += 1
    return hit / (N * N)


def claimed(data):
    """[(label, cell min x, cell min z)] in the boxes() frame."""
    if "span" in data:
        s = data.get("scale", DEFAULT_SCALE)
        cells = set()
        for p in data["parts"]:
            if p.get("offsetY", 0) >= 1:
                continue
            cx = WALL + (p.get("offsetX", 0) + 0.5) * s
            cz = WALL + (p.get("offsetZ", 0) + 0.5) * s
            bx, bz = math.floor(cx), math.floor(cz)
            gx = min(2, max(0, math.floor((cx - bx - WALL) / CW)))
            gz = min(2, max(0, math.floor((cz - bz - WALL) / CW)))
            cells.add((bx, bz, gx, gz))
        return [(f"t{bx},{bz} c{gx},{gz}", bx + WALL + gx * CW, bz + WALL + gz * CW)
                for bx, bz, gx, gz in sorted(cells)]
    return [(f"{c['dx']:+d},{c['dz']:+d}", c["dx"] * CW - CW / 2, c["dz"] * CW - CW / 2)
            for c in data["footprint_cells"]]


THRESHOLD = 0.12  # core-low coverage a cell needs to be claimed


def candidates(data):
    """Every cell a part could stand in: [(key, label, cell min x, cell min z)]. Keys match claimed_keys()."""
    if "span" in data:
        sp = data["span"]
        return [((bx, bz, gx, gz), f"t{bx},{bz} c{gx},{gz}", bx + WALL + gx * CW, bz + WALL + gz * CW)
                for bx in range(sp["x"]) for bz in range(sp["z"]) for gx in range(3) for gz in range(3)]
    return [((dx, dz), f"{dx:+d},{dz:+d}", dx * CW - CW / 2, dz * CW - CW / 2)
            for dx in range(-2, 3) for dz in range(-2, 3)]


def claimed_keys(data):
    if "span" in data:
        return {tuple(int(t) for t in l.replace("t", "").replace("c", ",").replace(" ", "").split(","))
                for l, _, _ in claimed(data)}
    return {(c["dx"], c["dz"]) for c in data["footprint_cells"]}


def proposed(data, parts=None):
    """The cells the structure really needs: solid parts near the sand over THRESHOLD of the
    cell's core. Only ever drops cells; a floor piece keeps its anchor cell."""
    parts = parts if parts is not None else boxes(data)
    current = claimed_keys(data)
    keep = {k for k, _, x, z in candidates(data)
            if k in current and coverage(parts, x, z, True, PLANT_HEIGHT, core=True) >= THRESHOLD}
    if (0, 0) in current:
        keep.add((0, 0))
    return keep


def occupied_cells(data):
    """The JSON "occupied_cells" list for a structure, or None when every claimed cell is needed
    (the field is then left out and the engine uses the whole footprint). Floor cells are anchor
    offsets; span cells count floor cells across the whole box (tank * 3 + cell)."""
    keep = proposed(data)
    if keep == claimed_keys(data):
        return None
    if "span" in data:
        cells = sorted((bx * 3 + gx, bz * 3 + gz) for bx, bz, gx, gz in keep)
    else:
        cells = sorted(keep, key=lambda c: (c != (0, 0), c[1], c[0]))
    return [{"dx": dx, "dz": dz} for dx, dz in cells]


def with_occupied(data):
    """data with "occupied_cells" set (placed after "footprint_cells") or removed, as the rule says."""
    out = {k: v for k, v in data.items() if k != "occupied_cells"}
    occ = occupied_cells(out)
    if occ is None:
        return out
    ordered = {}
    for k, v in out.items():
        ordered[k] = v
        if k == "footprint_cells":
            ordered["occupied_cells"] = occ
    if "occupied_cells" not in ordered:
        ordered["occupied_cells"] = occ
    return ordered


def apply(name):
    """Writes the rule's occupied_cells into a hand-captured structure JSON, keeping its indent."""
    path = os.path.join(DIR, name + ".json")
    with open(path, encoding="utf-8") as f:
        text = f.read()
    data = json.loads(text)
    assert "occupied_cells" not in data, f"{name} already has occupied_cells"
    occ = occupied_cells(data)
    if occ is None:
        return None
    # Spliced in as text after footprint_cells' closing bracket, so the rest of a hand-formatted
    # file is left exactly as it was.
    start = text.index("[", text.index('"footprint_cells"'))
    depth, end = 0, start
    for end in range(start, len(text)):
        depth += {"[": 1, "]": -1}.get(text[end], 0)
        if depth == 0:
            break
    line_start = text.rfind(chr(10), 0, start) + 1
    pad = text[line_start:len(text) - len(text[line_start:].lstrip(" "))]
    step = len(pad) or 2
    block = json.dumps(occ, indent=step).replace(chr(10), chr(10) + pad)
    text = text[:end + 1] + "," + chr(10) + pad + '"occupied_cells": ' + block + text[end + 1:]
    json.loads(text)
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(text)
    return occ


def audit(name):
    with open(os.path.join(DIR, name + ".json"), encoding="utf-8") as f:
        data = json.load(f)
    parts = boxes(data)
    rows = []
    for label, x0, z0 in claimed(data):
        rows.append((label,
                     coverage(parts, x0, z0, False),
                     coverage(parts, x0, z0, True),
                     coverage(parts, x0, z0, True, PLANT_HEIGHT, core=True)))
    kind = "span %dx%dx%d" % (data["span"]["x"], data["span"]["y"], data["span"]["z"]) if "span" in data else "floor"
    print(f"\n{name}  ({kind}, scale {data.get('scale', DEFAULT_SCALE)}, {len(parts)} parts, {len(rows)} cells)")
    print(f"  {'cell':<14}{'any':>6}{'solid':>7}{'core-low':>10}")
    for label, a, sd, cl in rows:
        flag = "" if cl >= 0.25 else ("  <- soft/overhang only" if sd < 0.05 else "  <- thin")
        print(f"  {label:<14}{a:6.0%}{sd:7.0%}{cl:10.0%}{flag}")
    new = proposed(data, parts)
    old = claimed_keys(data)
    print(f"  proposed: {len(new)} cells (was {len(old)}); added {sorted(new - old)}")


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if a != "--apply"]
    names = args or sorted(f[:-5] for f in os.listdir(DIR) if f.endswith(".json"))
    for n in names:
        if "--apply" in sys.argv:
            occ = apply(n)
            print(f"{n}: {'unchanged' if occ is None else f'{len(occ)} occupied cells'}")
        else:
            audit(n)
