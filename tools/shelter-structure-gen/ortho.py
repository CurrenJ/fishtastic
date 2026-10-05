"""
Orthographic, ray-marched views of a Build: each pixel's ray steps through the voxel grid until it
hits a part, and is shaded by the block's colour, the face it hit (lit from above and the front)
and how deep it went. Easier to judge a sculpted form by than the matplotlib voxel preview.

    python -c "import skull, ortho; b, n, s = skull.reef_crowned_skull(); ortho.render(b, 'preview/x.png')"
"""
import math

import numpy as np
from PIL import Image

import gen

WATER = (42, 58, 74)


def colour(block):
    c = gen.COLORS.get(block)
    if c is None:
        stem = block.replace("_wall_fan", "_fan")
        if stem.endswith("_stairs") or stem.endswith("_slab"):
            c = gen.COLORS.get(stem.rsplit("_", 1)[0] + ("_block" if "quartz" in stem else ""), "#ece6dc")
        elif "coral" in stem:
            for k, v in (("brain", "#d060a0"), ("tube", "#3050d0"), ("bubble", "#a020a0"), ("fire", "#d03030"), ("horn", "#d0c030")):
                if k in stem:
                    c = v if "dead" not in stem else "#8a847c"
        c = c or "#ff00ff"
    c = c.lstrip("#")[:6]
    return [int(c[i:i + 2], 16) for i in (0, 2, 4)]


def view(b, yaw_deg, pitch_deg, half=11.5, res=300):
    pts = list(b.v)
    lo = np.array([min(p[i] for p in pts) for i in range(3)]) - 1
    hi = np.array([max(p[i] for p in pts) for i in range(3)]) + 1
    dims = hi - lo + 1
    grid = np.zeros(dims, dtype=np.int32)                 # 0 empty, else palette index + 1
    palette = [WATER]
    index = {}
    for p, (block, _) in b.v.items():
        if block not in index:
            index[block] = len(palette)
            palette.append(colour(block))
        grid[tuple(np.array(p) - lo)] = index[block]
    palette = np.array(palette, dtype=float)

    yaw, pitch = math.radians(yaw_deg), math.radians(pitch_deg)
    # Camera looks toward -forward; forward points from the model to the camera (yaw 0: from the south, +z).
    fwd = np.array([math.sin(yaw) * math.cos(pitch), math.sin(pitch), math.cos(yaw) * math.cos(pitch)])
    right = np.array([math.cos(yaw), 0.0, -math.sin(yaw)])
    up = np.cross(fwd, right)
    centre = np.array([0.0, 6.0, 0.0])
    s = np.linspace(-half, half, res)
    gx, gy = np.meshgrid(s, -s)
    origin = centre + gx[..., None] * right + gy[..., None] * up + fwd * 30
    d = -fwd
    step = 0.06
    n = int(60 / step)
    hit = np.zeros((res, res), dtype=np.int32)
    depth = np.zeros((res, res))
    normal = np.zeros((res, res, 3))
    prev = np.floor(origin + 0.5).astype(int)
    for k in range(n):
        alive = hit == 0
        if not alive.any():
            break
        p = origin + d * (k * step)
        c = np.floor(p + 0.5).astype(int)
        idx = c - lo
        inside = np.all((idx >= 0) & (idx < dims), axis=-1) & alive
        vals = np.zeros((res, res), dtype=np.int32)
        ii = idx[inside]
        vals[inside] = grid[ii[:, 0], ii[:, 1], ii[:, 2]]
        new = (vals > 0) & alive
        hit[new] = vals[new]
        depth[new] = k * step
        normal[new] = (prev - c)[new]
        prev = c
    light = np.array([0.35, 0.85, 0.4])
    light /= np.linalg.norm(light)
    nl = np.linalg.norm(normal, axis=-1, keepdims=True)
    nn = np.where(nl > 0, normal / np.maximum(nl, 1e-9), 0)
    lam = 0.5 + 0.5 * np.clip((nn * light).sum(-1), 0, 1)
    fog = np.clip(1.15 - (depth - 18) / 40, 0.6, 1.0)
    img = palette[hit] * (lam * fog)[..., None]
    img[hit == 0] = WATER
    return img


def render(b, out, views=((0, 6), (38, 20), (-38, 20), (90, 4), (180, 18), (0, 85))):
    tiles = [view(b, yw, pt) for (yw, pt) in views]
    rows = [np.concatenate(tiles[i:i + 3], axis=1) for i in range(0, len(tiles), 3)]
    im = np.concatenate(rows, axis=0).clip(0, 255).astype(np.uint8)
    Image.fromarray(im).save(out)
    return out
