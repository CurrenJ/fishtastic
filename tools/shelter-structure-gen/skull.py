"""
The Reef-Crowned Skull: a human skull lying in the sand, claimed by the reef. Built with gen.py's
Build and exported like its floor designs; kept in its own file so it runs on its own:

    python tools/shelter-structure-gen/skull.py --preview

The skull is sculpted in its own frame (u east, v up, w toward the face) as a smooth union of
anatomical masses, carved, then rolled onto one cheek and voxelised. Shelters are boxes in the
build grid, laid over the rolled skull afterwards, so they stay exact to the cell.

What fish do with it (docs/fish-shelters.md §12.9, §12.13):
- the temple tunnel (hollow, two mouths): in at one temple, out the other, behind the eyes. A
  tunnel, so untagged fish pass straight through and visitors linger. The dark left orbit opens
  into it: a window from the front, and a third mouth for a tank group that runs front to back;
- the jaw's cup (open, capacity 1): the water held in the fallen mandible, for small fish;
- a reaction: a fish noses the jaw, the jaw rocks on the sand, and the skull breathes out bubbles
  through its eye.
"""
import math
import os
import random
import sys

import gen
from gen import Build, value_noise
from sculpt import MUSHROOM_INSIDE, N6, Openings, Ramp, add

SCALE = 0.04
ROLL = math.radians(9)          # the head tilts toward its right shoulder: the viewer's left dips
SINK = 0.6                      # how far the skull has settled into the sand, voxels
OX, OZ = 1.0, -0.8              # where the skull's frame sits on the build grid

gen.FULL_CUBES.update({"black_concrete", "moss_block", "smooth_quartz", "mud", "dead_brain_coral_block", "dead_tube_coral_block"})
gen.COLORS.update({
    "quartz_block": "#ece6dc", "mushroom_stem": "#cbc4b6", "brown_mushroom_block": "#b8a690", "packed_mud": "#8c6a50",
    "mud": "#3c393c", "black_concrete": "#08080c", "polished_blackstone": "#35303a", "moss_block": "#5a7a30", "smooth_quartz": "#f2ede4", "quartz_stairs": "#ece6dc",
    "quartz_slab": "#ece6dc",
})

# Bone, sunlit crown to silted base: the Leviathan's Seat's ramp, so the bones in a tank match.
BONE = Ramp("quartz_block", "bone_block", "mushroom_stem", MUSHROOM_INSIDE, "packed_mud")
# The inside of the skull, seen through its openings: shadow, not bone.
SHADOW = Ramp("polished_blackstone", "black_concrete")     # the fog lifts anything paler to grey


def ellipsoid(p, c, r):
    """Roughly the signed distance to an ellipsoid, in voxels: negative inside."""
    q = [(p[i] - c[i]) / r[i] for i in range(3)]
    return (math.sqrt(q[0] ** 2 + q[1] ** 2 + q[2] ** 2) - 1.0) * min(r)


def capsule(p, a, b, r):
    ab = [b[i] - a[i] for i in range(3)]
    ap = [p[i] - a[i] for i in range(3)]
    t = max(0.0, min(1.0, sum(ap[i] * ab[i] for i in range(3)) / sum(x * x for x in ab)))
    return math.dist(p, [a[i] + ab[i] * t for i in range(3)]) - r


def smin(a, b, k):
    """Smooth union: the masses blend into one another over about k voxels instead of creasing."""
    h = max(0.0, min(1.0, 0.5 + 0.5 * (b - a) / k))
    return b + (a - b) * h - k * h * (1 - h)


# ── The skull, in its own frame: the face looks along +w. The cranium's base is cut flat at ear
# level, so from the front a wide dome sits over a narrower face, the outline that reads as a skull;
# it stands on its teeth and the mastoid knobs, a shadowed gap under the base. ──
EYE = (2.9, 6.8)                # orbit centre: |u|, v
BASE_V = 3.0                    # the cranium's flat base, at the level of the ears
NOSE_V = (2.4, 4.6)             # nasal aperture: bottom, top
FACE_W = 4.6                    # the face plane at the orbits


def cranium_width(w):
    # Widest over the ears and behind, narrowing to the forehead.
    return 6.4 * (1.0 - 0.14 * max(0.0, min(1.0, (w + 2.0) / 6.5)))


def skull_field(u, v, w):
    p = (u, v, w)
    d = ellipsoid(p, (0, 7.4, -2.4), (cranium_width(w), 6.0, 7.2))
    d = smin(d, ellipsoid(p, (0, 5.9, -6.0), (5.1, 4.2, 3.6)), 1.5)                 # the occiput's fullness
    d = max(d, BASE_V - v)                                                          # the base, cut flat
    # The face below the brow: a rounded block carrying the upper teeth on its arch.
    arch = ((u / 3.9) ** 2 + ((w - 0.6) / 4.2) ** 2) ** 0.5
    face = max((arch - 1.0) * 3.9, 2.0 - v, v - 6.0)
    d = smin(d, face, 1.2)
    # The cheekbones: knobs under the outer orbits and arches swept back along the temples.
    for s in (-1, 1):
        d = smin(d, ellipsoid(p, (s * 4.7, 4.9, 2.6), (1.7, 1.4, 1.9)), 1.0)
        d = smin(d, capsule(p, (s * 5.3, 4.6, 1.6), (s * 6.0, 4.9, -2.6), 0.75), 0.6)
    # The brow ridge: a heavy bar over both orbits, arched over each, curving back at the sides.
    for i in range(-10, 11):
        x = i / 2.0
        y = 8.25 + 0.45 * math.cos(math.pi * max(-1.0, min(1.0, (abs(x) - EYE[0]) / EYE[0])))
        d = min(d, math.dist(p, (x, y, FACE_W + 0.2 - 0.055 * x * x)) - 1.0)
    # The mastoid knobs behind where the ears were.
    for s in (-1, 1):
        d = smin(d, ellipsoid(p, (s * 4.4, 2.6, -3.0), (1.1, 1.9, 1.2)), 0.8)
    return d


def in_orbit(u, v, side):
    du, dv = (u - side * EYE[0]) / 1.85, (v - EYE[1]) / 1.7
    return abs(du) ** 2.6 + abs(dv) ** 2.6 <= 1.0           # rounded squares, as real orbits are


def in_nose(u, v):
    """The nasal aperture: an upside-down heart, broad at the base, pinched at the bridge."""
    if not NOSE_V[0] <= v <= NOSE_V[1]:
        return False
    t = (v - NOSE_V[0]) / (NOSE_V[1] - NOSE_V[0])
    half = 1.6 * math.sin(math.pi * (0.25 + 0.75 * t)) if t < 0.85 else 0.45
    notch = t < 0.18 and abs(u) < 0.4                       # the nasal spine splitting the base
    return abs(u) <= half + 0.15 and not notch


def to_world(u, v, w):
    c, s = math.cos(ROLL), math.sin(ROLL)
    return (u * c - v * s + OX, u * s + v * c - SINK, w + OZ)


def to_skull(x, y, z):
    c, s = math.cos(ROLL), math.sin(ROLL)
    x, y = x - OX, y + SINK
    return (x * c + y * s, -x * s + y * c, z - OZ)


# ── The fallen jaw: a mandible lying on its base beside the face, chin out toward the east. ──
JAW_AT = (6.6, 6.2)             # the jaw's frame on the grid: x, z
JAW_YAW = math.radians(90)      # the chin's heading, from straight out (south): east, so the front sees its profile
JAW_SIZE = 0.92                 # a touch small for the skull: it reads as fallen, not as a second head


def jaw_local(x, z):
    c, s = math.cos(JAW_YAW), math.sin(JAW_YAW)
    dx, dz = (x - JAW_AT[0]) / JAW_SIZE, (z - JAW_AT[1]) / JAW_SIZE
    return (dx * c - dz * s, dx * s + dz * c)


def jaw_world(a, c_):
    c, s = math.cos(JAW_YAW), math.sin(JAW_YAW)
    return ((a * c + c_ * s) * JAW_SIZE + JAW_AT[0], (-a * s + c_ * c) * JAW_SIZE + JAW_AT[1])


def jaw_field(a, y, c_):
    """The mandible in its own frame: a U of bone (chin at +c), rami rising at the back."""
    d = 99.0
    for i in range(-16, 17):
        phi = math.radians(86 * i / 16)
        pa, pc = 3.7 * math.sin(phi), 3.0 * math.cos(phi) - 0.7
        thick = 1.0 if abs(i) > 4 else 1.25                 # the chin is the stoutest part
        d = min(d, math.dist((a, y, c_), (pa, 0.9, pc)) - thick)
    for s in (-1, 1):
        # Ramus: up from the angle, its back edge leaning; the condyle knob tops it.
        d = min(d, capsule((a, y, c_), (s * 3.75, 0.9, -0.9), (s * 3.5, 3.6, -1.7), 0.8))
        d = min(d, ellipsoid((a, y, c_), (s * 3.45, 4.2, -1.9), (0.75, 0.65, 0.95)))
        d = min(d, capsule((a, y, c_), (s * 3.6, 2.6, -0.5), (s * 3.55, 3.8, 0.1), 0.45))   # coronoid
    return d


def reef_crowned_skull():
    b = Build()
    rng = random.Random(1912)
    noise = value_noise(1912)

    # Voxelise the skull and the jaw.
    skull, jaw = set(), set()
    for x in range(-10, 11):
        for y in range(0, 16):
            for z in range(-10, 11):
                if skull_field(*to_skull(x, y, z)) <= 0:
                    skull.add((x, y, z))
                elif y <= 5 and jaw_field(*jaw_local(x, z)[:1], y, jaw_local(x, z)[1]) <= 0:
                    jaw.add((x, y, z))

    # The face: orbits sunk into it, the aperture of the nose, the gaps between the teeth.
    left_eye, right_eye, nose = set(), set(), set()
    for p in list(skull):
        u, v, w = to_skull(*p)
        if in_orbit(u, v, -1) and w > FACE_W - 2.2:
            left_eye.add(p)
        elif in_orbit(u, v, 1) and w > FACE_W - 1.3:
            right_eye.add(p)
        elif in_nose(u, v) and w > FACE_W - 1.0:
            nose.add(p)
    skull -= left_eye | right_eye | nose
    # Under the face the teeth hang in a ring, with the mouth's darkness right behind them: white
    # teeth on black is what reads as a skull from across the room.
    mouth = set()
    for p in list(skull):
        u, v, w = to_skull(*p)
        if v < 2.6:
            if ((u / 3.9) ** 2 + ((w - 0.6) / 4.2) ** 2) > 0.7:
                skull.discard(p)
            elif v < 2.0:
                mouth.add(p)                                  # only the band the teeth hang in

    # Teeth: the upper row along the arch, hanging below the face; two lost, one gold.
    teeth = {}
    for i in range(10):
        phi = math.radians(-64 + 128 * i / 9)
        u, w = 3.55 * math.sin(phi), 0.6 + 3.95 * math.cos(phi)
        for v in (1.0, 2.0):
            teeth[tuple(round(c) for c in to_world(u, v, w))] = i
    MISSING, GOLD = {3, 7}, 5

    # The temple tunnel, laid over the rolled skull as an exact box, walled except at its mouths.
    # The left orbit's window into it is a clean rectangle: a ragged one splits into slivers of
    # mouth too thin for any fish. The orbit's rounded rim is carved only one voxel deep.
    TUNNEL = (-5, 4, -1, 5, 7, 2)
    WINDOW = (-4, 5, -1, 7)                                # x0, y0, x1, y1
    window = {(x, y, z) for x in range(WINDOW[0], WINDOW[2] + 1) for y in range(WINDOW[1], WINDOW[3] + 1)
              for z in range(TUNNEL[5] + 1, TUNNEL[5] + 4)}
    for p in list(left_eye):
        if p[2] <= TUNNEL[5] + 1 and p not in window:
            left_eye.discard(p)
            skull.add(p)
    left_eye |= window
    skull -= left_eye
    op = Openings()
    op.add(TUNNEL, "hollow", [(-1, 0, 0), (1, 0, 0)], capacity=3, reach=3)
    solid = op.carve(skull)
    solid = op.wall(solid, lambda p: p not in left_eye)
    # The window: the left orbit carved through into the tunnel.
    solid -= left_eye
    # The breaches at the temples: round, ragged holes broken through the bone round each mouth,
    # just wider than it, so they read as a shattered temple and not as a doorway.
    cy, cz = (TUNNEL[1] + TUNNEL[4]) / 2, (TUNNEL[2] + TUNNEL[5]) / 2
    for p in list(solid):
        x, y, z = p
        if abs(x) <= TUNNEL[3] or abs(x) > TUNNEL[3] + 3:
            continue
        r = math.hypot((y - cy) / 2.5, (z - cz) / 2.6)
        if r < 1.0 + 0.2 * noise(x / 1.5 + 20, y / 1.3, z / 1.3) and y >= 2:
            solid.discard(p)
    keep = op.keep() | left_eye | nose
    interior = op.interiors()

    # Tone: pale where light falls, silt creeping up from the sand, the sutures between the
    # skull's plates as faint dark seams, and shadow wherever an opening looks into the skull.
    def suture(u, v, w):
        zig = 0.45 * math.copysign(1, math.sin(u * 2.6))
        coronal = v > 9.0 and abs(w - (1.3 + zig + 0.05 * u * u)) < 0.6
        sagittal = v > 11.2 and w < 1.3 and abs(u - 0.45 * math.copysign(1, math.sin(w * 2.6))) < 0.55
        lambdoid = w < -5.5 and v > 7.0 and abs((w + 7.6) - 0.55 * abs(u) + zig * 0.6 - (v - 9.5) * 0.35) < 0.55
        return coronal or sagittal or lambdoid

    openings = interior | left_eye | nose | right_eye

    def looks_inside(p):
        """A cell whose faces are seen only through an opening: the tunnel's walls, the backs of the
        orbits and the nose. One that also faces the open water is the face itself, and stays bone."""
        through = False
        for d in N6:
            n = add(p, d)
            if n in openings:
                through = True
            elif n not in solid and n[1] >= 0:
                return False
        return through

    def tone_of(p, base=0.42):
        x, y, z = p
        u, v, w = to_skull(*p)
        t = base + 0.1 * noise(x / 2.4, y / 2.4, z / 2.4)
        if w > 1.5:
            t -= 0.12                                       # the face kept clean and pale round its holes
        t += 0.11 * (5 - y) / 5                             # darker toward the sand
        if (x, y + 1, z) not in solid:
            t -= 0.18                                       # sunlit tops bleach
        if y <= 1:
            t += 0.2                                        # silt stain
        if y >= 1 and (x, y - 1, z) not in solid and w < 1.5:
            t += 0.4                                        # undersides in shadow: the gap the skull stands over
        t += 0.05 * (sum(1 for d in N6 if add(p, d) in solid) - 3)
        if suture(u, v, w):
            t += 0.32
        return t

    for p in solid:
        # The tunnel's square ends sunk in shadow; never the face in front of it.
        lip = abs(p[0]) >= TUNNEL[3] - 1 and p[2] <= TUNNEL[5] and any(add(p, d) in interior for d in N6)
        u, v, w = to_skull(*p)
        # The floor of a socket, whatever depth it was carved to: dark inside the outline, so both
        # eyes and the nose read as holes, the rims round them left bone.
        socket = w > 1.5 and (in_orbit(u, v, -1) or in_orbit(u, v, 1) or in_nose(u, v))
        if looks_inside(p) or p in mouth or lip or socket:
            name, props = SHADOW.pick(0.35 + 0.5 * rng.random(), rng)
        else:
            name, props = BONE.pick(tone_of(p), rng)
            if name == "bone_block":
                props = {"axis": "y"}
        b.set(*p, name, **props)
    for p in jaw:
        name, props = BONE.pick(tone_of(p, 0.34), rng)
        if name == "bone_block":
            props = {"axis": "x"}
        b.set(*p, name, group="jaw", **props)
    for p, i in teeth.items():
        if i not in MISSING and p not in b.v and p not in keep:
            b.set(*p, "gold_block" if i == GOLD else "smooth_quartz")

    # The lower teeth, a crooked row along the top of the fallen jaw's front.
    for i in range(8):
        phi = math.radians(-54 + 108 * i / 7)
        x, z = (round(c) for c in jaw_world(3.5 * math.sin(phi), 2.8 * math.cos(phi) - 0.7))
        if i != 2 and (x, 1, z) in jaw and (x, 2, z) not in b.v:
            b.set(x, 2, z, "smooth_quartz", group="jaw")

    # ── The reef claims it: a crack over the right of the crown, a colony erupting from its middle. ──
    crack = [(1.6, 9.4, 4.0), (2.9, 11.4, 1.2), (3.9, 11.9, -2.2), (4.8, 10.4, -5.6), (5.0, 8.6, -7.2)]
    blobs = [((3.3, 11.8, -0.4), 2.6), ((4.3, 11.2, -3.8), 2.1), ((2.3, 10.4, 2.6), 1.3)]

    def crack_dist(u, v, w):
        return min(capsule((u, v, w), crack[i], crack[i + 1], 0.0) for i in range(len(crack) - 1))

    def colony_at(p):
        """How deep in the colony a cell is: > 0 inside, about 1 at a blob's heart."""
        u, v, w = to_skull(*p)
        dens = max(1.0 - math.dist((u, v, w), c) / r for c, r in blobs)
        return dens + 0.3 * noise(p[0] / 1.8 + 9, p[1] / 1.8, p[2] / 1.8)

    def coral_kind(p):
        n = noise(p[0] / 1.6, p[1] / 1.6 + 30, p[2] / 1.6)
        # Mostly brain coral, pink; fire coral red in streaks, tube coral blue in the hollows.
        return "brain" if n > -0.3 else ("tube" if n < -0.62 else "fire")

    bone_skin = [p for p in sorted(solid) if p not in keep and any(add(p, d) not in solid and add(p, d)[1] >= 0 for d in N6)]
    colony = set()
    for p in bone_skin:
        dens = colony_at(p)
        if dens > 0:
            b.set(*p, ("bubble" if crack_dist(*to_skull(*p)) < 0.7 and rng.random() < 0.4 else coral_kind(p)) + "_coral_block")
            colony.add(p)
        elif crack_dist(*to_skull(*p)) < 0.62:
            b.set(*p, "mud")                                  # the open crack, where the colony hasn't reached
    # Mounding out over the bone: a layer where the colony is established, two at its heart.
    for layer, need in ((1, 0.3), (2, 0.72)):
        for p in sorted(colony):
            q = (p[0], p[1] + 1, p[2])
            if q not in b.v and q not in keep and colony_at(q) > need and rng.random() < 0.85:
                b.set(*q, coral_kind(q) + "_coral_block")
        colony = {p for p in b.v if b.v[p][0].endswith("coral_block") and not b.v[p][0].startswith("dead")}
    for p in sorted(colony):
        top = (p[0], p[1] + 1, p[2])
        if top not in b.v and top not in keep and rng.random() < 0.7:
            plant = rng.choice(("fire_coral", "horn_coral", "tube_coral", "brain_coral_fan", "fire_coral_fan",
                                "bubble_coral", "horn_coral_fan", "bubble_coral_fan"))
            b.set(*top, plant, waterlogged=True)

    # The right orbit stays a dark socket, so the skull keeps both its eyes; the reef has only
    # got a foothold, a few sprigs on its lower rim and fans spilling over it.
    floor_y = min(p[1] for p in right_eye)
    for p in sorted(right_eye):
        if p[1] == floor_y and rng.random() < 0.35:
            b.set(*p, rng.choice(("horn_coral", "fire_coral", "tube_coral")), waterlogged=True)
    front = {}
    for p in right_eye:
        front[(p[0], p[1])] = max(front.get((p[0], p[1]), -99), p[2])
    for (x, y), z in front.items():
        u, v, w = to_skull(x, y, z)
        if v < EYE[1] - 0.4 and (x, y, z + 1) not in b.v:
            b.set(x, y, z + 1, "horn_coral_wall_fan" if x % 2 else "fire_coral_wall_fan", facing="south", waterlogged=True)

    # Seagrass growing out of the nose.
    for p in sorted(nose, key=lambda q: q[1]):
        if (p[0], p[1] - 1, p[2]) in b.v and p[1] <= min(q[1] for q in nose) + 1 and p not in b.v:
            b.set(*p, "seagrass")

    # Barnacle crust and moss where the sunken cheek meets the sand, on the side away from the reef.
    for p in bone_skin:
        if b.v.get(p, ("",))[0].endswith("coral_block") or looks_inside(p):
            continue
        u, v, w = to_skull(*p)
        n = noise(p[0] / 1.8 + 50, p[1] / 1.8, p[2] / 1.8)
        if p[1] <= 3 and u < -1.5 and n > 0.2:
            b.set(*p, "dead_brain_coral_block" if n > 0.45 else "dead_tube_coral_block")
        elif p[1] <= 2 and n < -0.45:
            b.set(*p, "moss_block")

    # Life round its foot: seagrass, a strand of kelp behind, never in a corridor.
    for (x, z) in ((-8, 3), (-9, -2), (-7, -7), (7, -6), (-4, 8), (9, -2), (-8, 6), (0, 9)):
        if (x, 0, z) not in b.v and (x, 0, z) not in keep:
            b.set(x, 0, z, "seagrass")
    for y in range(0, 12):
        b.setdefault(-6, y, -9, "kelp_plant")
    b.setdefault(-6, 12, -9, "kelp", age=20)

    op.register(b)

    # The jaw's cup: the biggest box of open water held inside the U of the fallen mandible and
    # between its rami, standing on the sand.
    def in_cup(x, z):
        a, c_ = jaw_local(x, z)
        return abs(a) <= 2.7 and -2.2 <= c_ <= 1.4          # inside the U and between the rami
    best = None
    for x0 in range(0, 11):
        for z0 in range(0, 11):
            for x1 in range(x0, 11):
                for z1 in range(z0, 11):
                    cells = [(x, y, z) for x in range(x0, x1 + 1) for y in (0, 1, 2) for z in range(z0, z1 + 1)]
                    if all(in_cup(x, z) and c not in b.v for c in cells for (x, _, z) in [c]):
                        area = (x1 - x0 + 1) * (z1 - z0 + 1)
                        if best is None or area > best[0]:
                            best = (area, (x0, 0, z0, x1, 2, z1))
    if best:
        b.shelter(*best[1], kind="open", capacity=1)

    # The reaction: a fish noses the chin, the jaw rocks on the sand, and the skull breathes out
    # through its eye and nose.
    chin_dir = (math.sin(JAW_YAW), math.cos(JAW_YAW))
    chin = max((p for p in jaw if p[1] == 1), key=lambda p: p[0] * chin_dir[0] + p[2] * chin_dir[1])
    eye = [round(c, 2) for c in to_world(-EYE[0], EYE[1], FACE_W - 1.0)]
    snout = [round(c, 2) for c in to_world(0, NOSE_V[0] + 1, FACE_W - 0.5)]
    b.reactions.append({
        "nose": {"min": {"x": chin[0], "y": 0, "z": chin[2]}, "max": {"x": chin[0], "y": 1, "z": chin[2]}, "facing": "east"},
        "hold_seconds": 1.8,
        "open_ticks": 6, "hold_ticks": 48, "close_ticks": 20,
        "motions": [{"group": "jaw", "pivot": [JAW_AT[0], 0, JAW_AT[1]], "axis": "z", "degrees": -8, "sway_ticks": 14}],
        "bursts": [{"at": eye, "count": 10}, {"at": snout, "count": 4}],
        "open_sound": {"id": "minecraft:block.bone_block.place", "volume": 0.22, "pitch": 0.55},
        "close_sound": {"id": "minecraft:block.bubble_column.bubble_pop", "volume": 0.25, "pitch": 0.8},
    })
    return b, "reef_crowned_skull", SCALE


FLOOR = (reef_crowned_skull,)


if __name__ == "__main__":
    for design in FLOOR:
        b, name, scale = design()
        culled = b.cull()
        path, n = b.export_floor(name, scale)
        shelters = ", ".join(f"{s['kind']} {len(s['cells'])}" for s in b.shelters)
        print(f"{name}: {n} parts ({culled} hidden culled), floor, scale {scale}, shelters [{shelters}] -> {os.path.relpath(path, gen.ROOT)}")
        if "--preview" in sys.argv:
            import ortho
            print("  preview:", ortho.render(b, os.path.join(os.path.dirname(__file__), "preview", name + ".png")))
