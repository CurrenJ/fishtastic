"""
Sculpting tools for the shelter generator: the second pass that takes builds off the box.

A design describes its mass as a set of solid cells (usually a density field thresholded at zero),
then calls these in turn:

- `grounded` drops rock that no longer reaches the sand after carving;
- `Openings` carves each shelter's interior, the water in front of its mouths, and walls it in,
  so a noisy surface can never leak an extra mouth or seal a planned one;
- `exposure` and `occlusion` read how much water a face sees, so materials can follow light and
  weather (bleached crowns, dark undersides) instead of dice rolls;
- `bevel` turns exposed edges into stairs and slabs, which rounds the silhouette.

Everything works in the generator's build grid (x east, y up from the sand, z south).
"""
import math
import random

N6 = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
HORIZONTAL = {(1, 0, 0): "east", (-1, 0, 0): "west", (0, 0, 1): "south", (0, 0, -1): "north"}
DIRECTION = {v: k for k, v in HORIZONTAL.items()}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}
CCW = {v: k for k, v in CW.items()}


def add(p, d, k=1):
    return (p[0] + d[0] * k, p[1] + d[1] * k, p[2] + d[2] * k)


def fbm(noise, x, y, z, octaves=3, lacunarity=2.03, gain=0.5):
    """Fractal noise from a `gen.value_noise` sampler: big forms with smaller ones riding on them."""
    total, amp, freq = 0.0, 1.0, 1.0
    for _ in range(octaves):
        total += amp * noise(x * freq, y * freq, z * freq)
        amp *= gain
        freq *= lacunarity
    return total


def grounded(solid):
    """The cells of `solid` joined to the sand (y 0) through faces; floating crumbs dropped."""
    seen = {p for p in solid if p[1] == 0}
    stack = list(seen)
    while stack:
        p = stack.pop()
        for d in N6:
            n = add(p, d)
            if n in solid and n not in seen:
                seen.add(n)
                stack.append(n)
    return seen


def despeckle(solid, bounds, protect=frozenset(), passes=1):
    """A light cellular-automaton pass: rock with almost no rock round it erodes away, and a pit
    with rock nearly all round fills in, so noise reads as weathering, not static."""
    nx, ny, nz = bounds
    for _ in range(passes):
        out = set(solid)
        for x in range(nx):
            for y in range(ny):
                for z in range(nz):
                    p = (x, y, z)
                    if p in protect:
                        continue
                    faces = sum(1 for d in N6 if add(p, d) in solid or add(p, d)[1] < 0)
                    if p in solid and faces <= 1:
                        out.discard(p)
                    elif p not in solid and faces >= 5:
                        out.add(p)
        solid = out
    return solid


def exposure(solid, p, top):
    """How much open water a cell's top looks up into: 0..5 of five rays (straight up and four
    leaning 45 degrees) that clear the rock before the lid at `top`."""
    seen = 0
    for (dx, dz) in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        x, y, z = p
        blocked = False
        while y < top:
            x, y, z = x + dx, y + 1, z + dz
            if (x, y, z) in solid:
                blocked = True
                break
        seen += 0 if blocked else 1
    return seen


def occlusion(solid, p):
    """Share of the 26 cells round `p` that are rock (or sand): 0 in open water, 1 buried."""
    n = 0
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if (dx, dy, dz) != (0, 0, 0):
                    q = (p[0] + dx, p[1] + dy, p[2] + dz)
                    if q in solid or q[1] < 0:
                        n += 1
    return n / 26


class Openings:
    """The shelters of one design and the water each needs, carved into a solid set.

    Each opening is an interior box plus the directions its mouths face. Carving clears the box
    and a corridor `reach` cells out from each mouth face; walling makes every other face of the
    box rock, so the engine finds exactly the planned mouths. `keep` is everything decoration must
    leave alone: interiors, corridors, and the rock rim of each mouth."""

    def __init__(self):
        self.items = []

    def add(self, box, kind, mouths, capacity=None, min_length=None, reach=3):
        x0, y0, z0, x1, y1, z1 = box
        self.items.append(dict(box=(min(x0, x1), min(y0, y1), min(z0, z1), max(x0, x1), max(y0, y1), max(z0, z1)),
                               kind=kind, mouths=[tuple(m) for m in mouths], capacity=capacity,
                               min_length=min_length, reach=reach))

    @staticmethod
    def cells(box):
        x0, y0, z0, x1, y1, z1 = box
        return [(x, y, z) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1) for z in range(z0, z1 + 1)]

    def interiors(self):
        out = set()
        for it in self.items:
            out.update(self.cells(it["box"]))
        return out

    def corridors(self):
        out = set()
        for it in self.items:
            box = set(self.cells(it["box"]))
            for m in it["mouths"]:
                for c in box:
                    if add(c, m) not in box:
                        for k in range(1, it["reach"] + 1):
                            out.add(add(c, m, k))
        return out - self.interiors()

    def keep(self):
        k = self.interiors() | self.corridors()
        for c in list(k):
            for d in N6:
                k.add(add(c, d))
        return k

    def carve(self, solid):
        return solid - self.interiors() - self.corridors()

    def wall(self, solid, inside):
        """Rock on every face of every interior box that isn't a mouth (and isn't the sand)."""
        solid = set(solid)
        interiors = self.interiors()
        for it in self.items:
            box = set(self.cells(it["box"]))
            for c in box:
                for d in N6:
                    n = add(c, d)
                    if n in box or n[1] < 0 or d in it["mouths"] or n in interiors or not inside(n):
                        continue
                    if it["kind"] == "open" and d[1] == 0:
                        continue        # an open shelter's sides are water unless the design builds them
                    solid.add(n)
        return solid

    def register(self, b):
        for it in self.items:
            b.shelter(*it["box"], kind=it["kind"], capacity=it["capacity"], min_length=it["min_length"])


def stair_state(facing, half="bottom", shape="straight"):
    return {"facing": facing, "half": half, "shape": shape, "waterlogged": True}


def bevel(b, solid, stairs, slabs=None, keep=frozenset(), rng=None, chance=1.0):
    """Rounds exposed edges. A cube with water above and on one side becomes a stair rising away
    from the water; with water on two neighbouring sides, an outer-corner stair; with water on three,
    a slab. Undersides with water below and to one side get an upside-down stair, so overhangs
    curve back into the rock. `stairs` maps a cube block to its stair block (cubes not in it stay)."""
    slabs = slabs or {}
    rng = rng or random.Random(0)
    changes = {}
    for p in list(solid):
        if p in keep or p not in b.v:
            continue
        name = b.v[p][0]
        if name not in stairs or rng.random() > chance:
            continue
        up, down = add(p, (0, 1, 0)), add(p, (0, -1, 0))
        open_sides = [d for d in HORIZONTAL if add(p, d) not in solid]
        if len(open_sides) == 0 or len(open_sides) == 4:
            continue
        if up not in solid:
            half = "bottom"
        elif down not in solid and down[1] >= 0:
            half = "top"
        else:
            continue
        if len(open_sides) == 3:
            if half == "bottom" and name in slabs:
                changes[p] = (slabs[name], {"type": "bottom", "waterlogged": "true"})
            continue
        if len(open_sides) == 1:
            o = open_sides[0]
            facing = HORIZONTAL[(-o[0], 0, -o[2])]
            changes[p] = (stairs[name], {k: str(v).lower() for k, v in stair_state(facing, half).items()})
        else:
            a, c = open_sides
            if a[0] == -c[0] and a[2] == -c[2]:
                continue                            # water on opposite sides: a fin, leave it
            solid_a = HORIZONTAL[(-a[0], 0, -a[2])]
            solid_c = HORIZONTAL[(-c[0], 0, -c[2])]
            shape = "outer_right" if CW[solid_a] == solid_c else "outer_left"
            changes[p] = (stairs[name], {k: str(v).lower() for k, v in stair_state(solid_a, half, shape).items()})
    for p, blk in changes.items():
        b.v[p] = blk
    return len(changes)


class Ramp:
    """An ordered run of blocks from light to dark, chosen so each step is a small move in value
    and hue from the last. A tone in [0, 1] picks a place on it, and the fraction between two
    steps is dithered, so neighbouring tones blend instead of meeting at a hard line.

    Steps are block names, or (name, props) for one that needs a state (a mushroom block's inside)."""

    def __init__(self, *steps):
        self.steps = [s if isinstance(s, tuple) else (s, {}) for s in steps]

    def pick(self, tone, rng):
        f = max(0.0, min(1.0, tone)) * (len(self.steps) - 1)
        i = int(f)
        if i < len(self.steps) - 1 and rng.random() < f - i:
            i += 1
        return self.steps[i]


# Mushroom blocks show their pale speckled inside on a face set false.
MUSHROOM_INSIDE = ("brown_mushroom_block", {k: False for k in ("up", "down", "north", "south", "east", "west")})

# Warm limestone, sunlit to deep shade: brightness 213 -> 56, hue held in 22..49 degrees.
LIMESTONE = Ramp("smooth_sandstone", "sandstone", "mushroom_stem", MUSHROOM_INSIDE, "packed_mud", "dripstone_block", "coarse_dirt", "brown_terracotta")
