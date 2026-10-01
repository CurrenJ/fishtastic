"""
Procedural generator for fishtastic's spanning cosmetic structures (CosmeticStructure with a `span`,
see common/.../fishtank/SpanStructures.java). Each design is a voxel build in the box's own build
grid: x along the box's length (west -> east), y up from the sand, z north -> south; the structure
is authored facing south. One voxel is one block of `scale` (in world blocks).

    python tools/span-structure-gen/gen.py            # writes both JSONs into the datapack
    python tools/span-structure-gen/gen.py --preview  # also renders quick matplotlib views to ./preview/

Hidden voxels (every face against another full cube, or the sand below) are culled before export,
because each part is a separate block-model draw in game.
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT_DIR = os.path.join(ROOT, "common/src/main/resources/data/fishtastic/fishtastic/cosmetic_structure")

FULL_CUBES = {
    "bone_block", "sculk", "sculk_catalyst", "brain_coral_block", "bubble_coral_block", "tube_coral_block",
    "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "calcite", "stripped_mangrove_log",
    "mangrove_planks", "dark_oak_planks", "waxed_oxidized_cut_copper", "gold_block", "ochre_froglight",
    "pearlescent_froglight", "mossy_cobblestone",
}


class Build:
    def __init__(self, nx, ny, nz):
        self.nx, self.ny, self.nz = nx, ny, nz
        self.v = {}

    def inside(self, x, y, z):
        return 0 <= x < self.nx and 0 <= y < self.ny and 0 <= z < self.nz

    def set(self, x, y, z, name, **props):
        x, y, z = int(x), int(y), int(z)
        if self.inside(x, y, z):
            self.v[(x, y, z)] = (name, {k: str(v).lower() for k, v in props.items()})

    def setdefault(self, x, y, z, name, **props):
        if (int(x), int(y), int(z)) not in self.v:
            self.set(x, y, z, name, **props)

    def clear(self, x, y, z):
        self.v.pop((int(x), int(y), int(z)), None)

    def get(self, x, y, z):
        return self.v.get((x, y, z))

    def full(self, x, y, z):
        if y < 0:
            return True  # the sand
        b = self.v.get((x, y, z))
        return b is not None and b[0] in FULL_CUBES

    def cull(self):
        hidden = [p for p in self.v if self.v[p][0] in FULL_CUBES and all(
            self.full(p[0] + dx, p[1] + dy, p[2] + dz)
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))]
        for p in hidden:
            del self.v[p]
        return len(hidden)

    def export(self, name, span, scale, item_icon=None):
        parts = []
        for (x, y, z), (block, props) in sorted(self.v.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
            state = {"Name": "minecraft:" + block}
            if props:
                state["Properties"] = props
            parts.append({"state": state, "offsetX": x, "offsetY": y, "offsetZ": z})
        data = {"span": {"x": span[0], "y": span[1], "z": span[2]}, "scale": scale, "parts": parts}
        if item_icon:
            data["item_icon"] = item_icon
        path = os.path.join(OUT_DIR, name + ".json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=1)
            f.write("\n")
        return path, len(parts)


def grid_dims(span, scale):
    """Build-grid size for a span: wall-to-wall and sand-to-lid interior, in voxels."""
    ix = span[0] - 2 / 16
    iy = span[1] - 3 / 16
    iz = span[2] - 2 / 16
    return int(ix / scale + 1e-6), int(iy / scale + 1e-6), int(iz / scale + 1e-6)


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def axis_of(dx, dy, dz):
    a = max((abs(dx), "x"), (abs(dy), "y"), (abs(dz), "z"))
    return a[1]


# ─────────────────────────────────────────────────────────────────────────────────────────────────
# 1. Whale Fall — a great whale's skeleton settled on the sand of a long, two-storey aquarium. Ribs
#    arch up into the second storey like a nave; the south flank has collapsed outward, two ribs
#    snapped and lying in the sand. The carcass feeds a whole ecosystem: a dark sculk bacterial mat
#    spreading beneath it, red bone-worm plumes on the vertebrae, glowing sea pickles, coral lumps.
# ─────────────────────────────────────────────────────────────────────────────────────────────────
def whale_fall():
    span, scale = (4, 2, 2), 0.125
    nx, ny, nz = grid_dims(span, scale)          # 31 x 14 x 15
    b = Build(nx, ny, nz)
    rng = random.Random(1851)
    cz = nz / 2.0                                 # 7.5: spine runs along voxel z = 7
    spine_z = int(cz)

    def H(x):  # spine height along the body
        if x <= 13:
            return 5 + 6 * smooth((x - 8) / 5)
        if x <= 17:
            return 11
        return 11 - 10 * smooth((x - 17) / 12)

    def bone(x, y, z, axis):
        b.set(x, y, z, "bone_block", axis=axis)

    # Skull: a broad, flat baleen-whale skull, rostrum narrowing toward the snout (x = 0).
    for x in range(0, 9):
        w = 1.4 + 3.4 * (x / 8)
        top = round(1 + 3.2 * (x / 8) ** 1.3)
        z0, z1 = round(cz - w - 0.5), round(cz + w - 0.5)
        for z in range(z0, z1 + 1):
            for y in range(0, top + 1):
                edge = z in (z0, z1) or y == top or x in (0, 8)
                if edge:
                    bone(x, y, z, "x" if y == top else "y")
        # rostrum ridge
        if 1 <= x <= 6:
            bone(x, top + 1, spine_z, "x")
    for z in range(round(cz - 4.5), round(cz + 4.5)):   # occipital crest rising toward the spine
        for y in range(4, 6):
            bone(8, y, z, "z")
    b.clear(5, round(1 + 3.2 * (5 / 8) ** 1.3), spine_z)   # blowhole
    for side in (-1, 1):                                # eye sockets
        z = round(cz + side * (1.4 + 3.4 * 6 / 8) - 0.5)
        b.clear(6, 1, z)
        b.clear(6, 2, z)

    # Mandibles: two long jaw bones lying on the sand, bowed outward, tips meeting at the snout.
    for side in (-1, 1):
        for x in range(0, 11):
            off = 1.4 + 3.4 * min(x, 8) / 8 + 1.0 + 1.3 * math.sin(math.pi * x / 11)
            bone(x, 0, round(cz + side * off - 0.5), "x")

    # Spine: segmented vertebrae, thick through the chest, thinning down the tail; two tail
    # vertebrae have drifted out of line.
    for x in range(8, nx - 1):
        h = round(H(x))
        if x in (26, 28):
            continue
        chest = x <= 21
        widths = (spine_z - 1, spine_z, spine_z + 1) if chest and x % 3 == 1 else (spine_z,)
        for z in widths:
            bone(x, h, z, "x")
        if chest:
            bone(x, h - 1, spine_z, "x")
        if x % 3 == 1 and x <= 25:                      # neural spines
            for dy in range(1, 3 if 11 <= x <= 19 else 2):
                bone(x, h + dy, spine_z, "y")

    # Ribs: arches from the spine down to the sand, swept back toward the tail as they fall.
    # The far flank (authored north) stands; the near flank (authored south, the front, which
    # faces the player after the placement turn) has collapsed outward: some ribs snapped and leaning, others lying flat in the
    # sand — which also opens the cage to the viewer.
    rib_xs = [10, 13, 16, 19, 22]
    def rib(x, side, top, r, tmax, sweep_amt):
        prev = None
        for i in range(0, 90):
            t = tmax * i / 89
            zf = cz + side * (0.9 + (r - 0.9) * math.sin(t) ** 0.6)
            yf = top * math.cos(t)
            sweep = round(sweep_amt * (1 - math.cos(t)))
            p = (x + sweep, round(yf), int(math.floor(zf)))
            if p != prev:
                bone(p[0], p[1], max(0, min(p[2], nz - 1)), "z" if math.cos(t) > math.sin(t) else "y")
                prev = p
        return prev

    def lying(x0, z0, x1, z1):
        n = max(abs(x1 - x0), abs(z1 - z0)) * 2 + 1
        for k in range(n + 1):
            f = k / n
            bone(round(x0 + (x1 - x0) * f), 0, max(0, min(nz - 1, round(z0 + (z1 - z0) * f))), "z" if abs(z1 - z0) >= abs(x1 - x0) else "x")

    for x in rib_xs:
        top = H(x) - 1
        reach = 6.0 - max(0, x - 16) * 0.25 - max(0, 12 - x) * 0.4
        rib(x, -1, top, reach, math.pi / 2, 2.6)                      # far flank: intact
        if x in (10, 16, 22):                                          # near flank: snapped, leaning out
            rib(x, 1, top, reach + 1.2, math.pi / 2 * 0.55, 2.6)
            lying(x + 2, int(cz + reach + 0.5), x + 4, nz - 1)          # its lower half, in the sand
        else:                                                          # near flank: fallen flat
            lying(x + 1, spine_z + 2, x + 4, nz - 1)

    # Pectoral flipper: arm bones and long fingers (end rods) laid out on the front sand by the skull.
    S = nz - 1   # mirror onto the south (front) side
    for x in (9, 10):
        bone(x, 0, S - 1, "x")
    for x in (7, 8):
        bone(x, 0, S - 1, "x")
        bone(x, 0, S, "x")
    for z, (x0, x1) in ((0, (3, 6)), (1, (2, 6)), (2, (4, 6))):
        for x in range(x0, x1 + 1):
            b.set(x, 0, S - z, "end_rod", facing="west")

    # Loose vertebrae scattered past the tail.
    for (x, z, ax) in ((26, 11, "z"), (28, 3, "x"), (30, 9, "y"), (27, 12, "x")):
        bone(x, 0, z, ax)

    # The ecosystem it feeds.
    # Bacterial mat: dark sculk spreading under the carcass, ragged at the edge.
    for x in range(4, nx):
        for z in range(0, nz):
            if (x, 0, z) in b.v:
                continue
            half = 6.3 - 0.18 * abs(x - 15)
            d = abs(z + 0.5 - cz) / max(half, 0.5)
            if d < 1.0 - 0.45 * rng.random():
                b.set(x, 0, z, "sculk")
    b.set(16, 0, 9, "sculk_catalyst")
    b.set(21, 0, 5, "sculk_catalyst")
    # Bone-worm plumes (red) on the vertebrae and skull.
    for x in (9, 13, 15, 17, 21, 24):
        h = round(H(x))
        b.setdefault(x, h + 1, spine_z, "crimson_roots")
    for (x, z) in ((3, 6), (6, 9)):
        b.setdefault(x, round(1 + 3.2 * (x / 8) ** 1.3) + 1, z, "crimson_roots")
    for (x, y, z) in ((12, 0, 13), (18, 0, 2), (22, 0, 12)):
        b.setdefault(x, y + 1, z, "fire_coral_fan", waterlogged=True)
    # Coral lumps where ribs meet the sand.
    for (x, z, block) in ((10, 1, "brain_coral_block"), (16, 14, "bubble_coral_block"), (22, 2, "tube_coral_block")):
        b.set(x, 0, z, block)
        b.setdefault(x, 1, z, "brain_coral_fan" if block == "brain_coral_block" else "tube_coral_fan", waterlogged=True)
    # Glowing sea pickles in the hollows.
    for (x, y, z, n) in ((2, 0, 4, 3), (14, 0, 10, 4), (19, 0, 7, 2), (25, 0, 6, 3), (29, 0, 12, 4), (11, 0, 4, 2)):
        if (x, y, z) in b.v and b.v[(x, y, z)][0] == "sculk":
            y += 1
        b.setdefault(x, y, z, "sea_pickle", pickles=n, waterlogged=True)
    for x in (2, 4):
        b.setdefault(x, round(1 + 3.2 * (x / 8) ** 1.3) + 1, spine_z + 2, "sea_pickle", pickles=2, waterlogged=True)

    culled = b.cull()
    return b, "whale_fall", span, scale, culled, {"rotY": -30, "rotX": 20, "scale": 1.1}


# ─────────────────────────────────────────────────────────────────────────────────────────────────
# 2. Drowned Pagoda — a five-tiered pagoda on a mossy stone plinth, rising through a 2x2 tank three
#    storeys tall. Verdigris copper roofs with upturned corners, red mangrove posts, white plaster,
#    a lantern hung from every eave corner, windows still glowing warm. Kelp has climbed the back
#    corners; stone lanterns stand at the plinth corners; seagrass and sea pickles in the cracks.
# ─────────────────────────────────────────────────────────────────────────────────────────────────
def drowned_pagoda():
    span, scale = (2, 3, 2), 0.125
    nx, ny, nz = grid_dims(span, scale)          # 15 x 22 x 15
    b = Build(nx, ny, nz)
    rng = random.Random(608)
    c = nx // 2                                  # 7

    def r_of(x, z):
        return max(abs(x - c), abs(z - c))

    def inward(x, z):
        """Facing for a stair whose high side points toward the centre (roof sloping down outward)."""
        dx, dz = x - c, z - c
        if abs(dx) >= abs(dz):
            return "west" if dx > 0 else "east"
        return "north" if dz > 0 else "south"

    def outward(x, z):
        return {"west": "east", "east": "west", "north": "south", "south": "north"}[inward(x, z)]

    def is_corner(x, z, r):
        return abs(x - c) == r and abs(z - c) == r

    COPPER = "waxed_oxidized_cut_copper"
    STAIR = "waxed_oxidized_cut_copper_stairs"

    # Plinth: two steps of stone brick, the outer edges stepped down with stairs.
    for x in range(nx):
        for z in range(nz):
            r = r_of(x, z)
            if r >= 5:
                if r == 7 and not is_corner(x, z, 7):
                    b.set(x, 0, z, "stone_brick_stairs", facing=inward(x, z), half="bottom")
                else:
                    b.set(x, 0, z, rng.choice(["mossy_stone_bricks", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks"]))
            if r == 5:
                if is_corner(x, z, 5):
                    b.set(x, 1, z, "mossy_stone_bricks")
                else:
                    b.set(x, 1, z, "stone_brick_stairs", facing=inward(x, z), half="bottom")

    # Tiers: (body half-width, body bottom y, body height). Each roof is one thin flared layer
    # directly above its body; the next tier's body stands on it.
    tiers = [(4, 2, 3), (4, 6, 2), (3, 9, 2), (3, 12, 2), (2, 15, 2)]
    for i, (bw, y0, hgt) in enumerate(tiers):
        nxt = tiers[i + 1][0] if i + 1 < len(tiers) else 1
        # Body: white plaster ring, red posts at the corners and flanking each side's opening.
        for x in range(c - bw, c + bw + 1):
            for z in range(c - bw, c + bw + 1):
                if r_of(x, z) != bw:
                    continue
                along = (z - c) if abs(x - c) == bw else (x - c)
                for y in range(y0, y0 + hgt):
                    if is_corner(x, z, bw) or abs(along) == 1:
                        b.set(x, y, z, "stripped_mangrove_log", axis="y")
                    else:
                        b.set(x, y, z, "calcite")
                # The opening in the middle of each side: a door on the ground tier, a lit window above.
                if along == 0:
                    rows = range(y0, y0 + 2) if i == 0 else range(y0 + hgt - 1, y0 + hgt)
                    ix = x - (1 if x > c else -1 if x < c else 0)
                    iz = z - (1 if z > c else -1 if z < c else 0)
                    for y in rows:
                        b.clear(x, y, z)
                        b.set(ix, y, iz, "dark_oak_planks" if i == 0 else "ochre_froglight")
        ye = y0 + hgt
        # Roof: stairs on the rim (low edge outward), copper in toward the next body; the corners
        # flare up a step and a lantern hangs beneath each.
        for x in range(c - bw - 2, c + bw + 3):
            for z in range(c - bw - 2, c + bw + 3):
                r = r_of(x, z)
                if r > bw + 2 or r <= nxt:   # r <= nxt is under the next body or inside it: never seen
                    continue
                if r == bw + 2 and not is_corner(x, z, r):
                    b.set(x, ye, z, STAIR, facing=inward(x, z), half="bottom")
                elif r == bw + 2:
                    b.set(x, ye, z, "waxed_oxidized_cut_copper_slab", type="bottom")
                    b.set(x, ye + 1, z, STAIR, facing="east" if x > c else "west", half="bottom")
                    b.set(x, ye - 1, z, "lantern", hanging=True)
                elif r == bw + 1:
                    b.set(x, ye, z, STAIR, facing=inward(x, z), half="top") if not is_corner(x, z, r) else b.set(x, ye, z, COPPER)
                else:
                    b.set(x, ye, z, COPPER)
        # A ridge of copper stairs climbing to the next body (only where the next tier steps in).
        if nxt < bw:
            for x in range(c - bw, c + bw + 1):
                for z in range(c - bw, c + bw + 1):
                    if r_of(x, z) == bw and not is_corner(x, z, bw):
                        b.set(x, ye + 1, z, STAIR, facing=inward(x, z), half="bottom")
                    elif r_of(x, z) == bw:
                        b.set(x, ye + 1, z, COPPER)

    # Crown: a small copper cap, a gold boss, and a spire.
    top = tiers[-1][1] + tiers[-1][2] + 2      # above the last roof and its ridge
    for x in range(c - 1, c + 2):
        for z in range(c - 1, c + 2):
            if r_of(x, z) == 1:
                b.set(x, top - 1, z, COPPER) if is_corner(x, z, 1) else b.set(x, top - 1, z, STAIR, facing=inward(x, z), half="bottom")
    b.set(c, top - 1, c, "gold_block")
    b.set(c, top, c, "gold_block")
    for y in range(top + 1, ny):
        b.set(c, y, c, "oxidized_lightning_rod" if y < ny - 1 else "end_rod", facing="up")

    # Stone lanterns (toro) at the four plinth corners.
    for sx in (-1, 1):
        for sz in (-1, 1):
            x, z = c + sx * 6, c + sz * 6
            b.set(x, 1, z, "stone_brick_wall", up=True)
            b.set(x, 2, z, "lantern", hanging=False)
    # Kelp climbing the two back corners, seagrass and pickles in the cracks.
    for (x, z, h) in ((c - 6, c - 5, 13), (c + 5, c - 6, 9), (c + 6, c - 4, 6)):
        for y in range(1, 1 + h):
            b.set(x, y, z, "kelp_plant" if y < h else "kelp")
    for (x, z) in ((c - 5, c + 6), (c + 6, c + 2), (c - 6, c + 1), (c + 2, c + 6), (c - 2, c - 6)):
        b.setdefault(x, 1, z, "seagrass")
    for (x, z, n) in ((c + 6, c - 1, 3), (c - 6, c - 2, 2), (c + 4, c + 6, 1)):
        b.setdefault(x, 1, z, "sea_pickle", pickles=n, waterlogged=True)
    b.setdefault(c - 5, 9, c + 5, "brain_coral_fan", waterlogged=True)  # coral on a lower roof

    culled = b.cull()
    return b, "drowned_pagoda", span, scale, culled, {"rotY": -30, "rotX": 15, "scale": 1.0}


COLORS = {
    "bone_block": "#e8e2c8", "end_rod": "#ffffff", "sculk": "#0c3a46", "sculk_catalyst": "#1d4f52",
    "crimson_roots": "#c0303a", "fire_coral_fan": "#d83a32", "brain_coral_block": "#e070b0",
    "brain_coral_fan": "#e070b0", "bubble_coral_block": "#a040c0", "tube_coral_block": "#3050d0",
    "tube_coral_fan": "#3050d0", "sea_pickle": "#a8d040", "stone_bricks": "#7a7a7a",
    "mossy_stone_bricks": "#6a7a5a", "cracked_stone_bricks": "#707070", "stone_brick_stairs": "#858585",
    "stone_brick_wall": "#808080", "calcite": "#f0f0ea", "stripped_mangrove_log": "#a33a2e",
    "dark_oak_planks": "#3c2a18", "ochre_froglight": "#f5d070", "waxed_oxidized_cut_copper": "#4fa892",
    "waxed_oxidized_cut_copper_stairs": "#5ab89f", "gold_block": "#f4cc30", "lantern": "#ffb040",
    "oxidized_lightning_rod": "#4fa892", "kelp_plant": "#3a7a30", "kelp": "#3a7a30", "seagrass": "#4a9a40",
}


def preview(b, name):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    import numpy as np
    os.makedirs(os.path.join(os.path.dirname(__file__), "preview"), exist_ok=True)
    filled = np.zeros((b.nx, b.nz, b.ny), dtype=bool)
    colors = np.empty((b.nx, b.nz, b.ny), dtype=object)
    for (x, y, z), (block, _) in b.v.items():
        filled[x, b.nz - 1 - z, y] = True
        colors[x, b.nz - 1 - z, y] = COLORS.get(block, "#ff00ff")
    fig = plt.figure(figsize=(16, 9))
    for i, (elev, azim) in enumerate(((20, -60), (20, 30), (20, 120), (60, -120))):
        ax = fig.add_subplot(2, 2, i + 1, projection="3d")
        ax.voxels(filled, facecolors=colors, edgecolor=(0, 0, 0, 0.15), linewidth=0.2)
        ax.set_box_aspect((b.nx, b.nz, b.ny))
        ax.view_init(elev=elev, azim=azim)
        ax.set_axis_off()
    plt.tight_layout()
    out = os.path.join(os.path.dirname(__file__), "preview", name + ".png")
    plt.savefig(out, dpi=80, facecolor="#2a3a4a")
    plt.close(fig)
    return out


if __name__ == "__main__":
    for design in (whale_fall, drowned_pagoda):
        b, name, span, scale, culled, icon = design()
        path, n = b.export(name, span, scale, icon)
        print(f"{name}: {n} parts ({culled} hidden culled), grid {b.nx}x{b.ny}x{b.nz}, span {span}, scale {scale} -> {path}")
        if "--preview" in sys.argv:
            print("  preview:", preview(b, name))
