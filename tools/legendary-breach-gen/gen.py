"""Generates the legendary breach celebration's soft textures (legendary-fish-breach-celebration spec §8).

White RGB with the shape in alpha, so a vertex/blit tint alone sets the hue, like the school motes.
Deterministic: a fixed seed, so re-running reproduces the committed PNGs byte for byte.

    python tools/legendary-breach-gen/gen.py

Writes into common/src/main/resources/assets/fishtastic/textures/particle/:
  legendary_sunburst.png  256x256  soft tapered rays of seeded length/width, a hole round the fish
  legendary_sparkle.png    64x64   four-point star with concave arms, bright core and soft halo
  legendary_sparkle8.png   64x64   the hero star: four long arms plus four short diagonals
Each gets a .mcmeta with blur on, so they stay smooth when drawn far larger than their pixels.
"""
import json
import math
import os
import random

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "common", "src", "main", "resources",
                   "assets", "fishtastic", "textures", "particle")


def save(name, alpha):
    alpha = np.clip(alpha, 0.0, 1.0)
    a = (alpha * 255.0 + 0.5).astype(np.uint8)
    rgba = np.zeros(a.shape + (4,), dtype=np.uint8)
    rgba[..., 0:3] = 255
    rgba[..., 3] = a
    Image.fromarray(rgba, "RGBA").save(os.path.join(OUT, name + ".png"), optimize=True)
    with open(os.path.join(OUT, name + ".png.mcmeta"), "w", newline="\n") as f:
        json.dump({"texture": {"blur": True, "clamp": True}}, f, indent=2)
        f.write("\n")


def grid(size):
    c = (np.arange(size) + 0.5) / size * 2.0 - 1.0  # pixel centres in [-1, 1]
    x, y = np.meshgrid(c, -c)
    return x, y, np.hypot(x, y), np.arctan2(y, x)


def sunburst(size=256, rays=22, seed=0x5EB0B57):
    rng = random.Random(seed)
    _, _, r, theta = grid(size)
    out = np.zeros_like(r)
    for i in range(rays):
        centre = (i + rng.uniform(-0.28, 0.28)) / rays * 2.0 * math.pi
        long_ray = i % 2 == 0
        length = rng.uniform(0.78, 1.0) if long_ray else rng.uniform(0.42, 0.66)
        width = rng.uniform(0.045, 0.075) if long_ray else rng.uniform(0.03, 0.05)  # radians, at the root
        strength = rng.uniform(0.75, 1.0) if long_ray else rng.uniform(0.45, 0.7)
        d = np.angle(np.exp(1j * (theta - centre)))  # wrapped angular distance
        # Wedge: a little wider in angle further out, Gaussian across so the edges are soft.
        w = width * (0.75 + 0.5 * np.clip(r / length, 0, 1))
        across = np.exp(-(d / w) ** 2)
        # Along: fades in past a hole round the fish, peaks early, eases out to nothing at its length.
        inner = np.clip((r - 0.07) / 0.12, 0, 1)
        inner = inner * inner * (3 - 2 * inner)
        along = inner * np.clip(1.0 - r / length, 0, 1) ** 1.6
        out = np.maximum(out, strength * across * along)
    # A faint halo ring ties the rays together without filling the hole.
    halo = np.exp(-((r - 0.2) / 0.09) ** 2) * 0.22
    return np.maximum(out, halo)


def star(size=64, diagonals=False):
    x, y, r, _ = grid(size)
    ax, ay = np.abs(x), np.abs(y)

    def arms(u, v, reach, root):
        # Concave arm along u: its half-width narrows from `root` to a point at `reach`.
        t = np.clip(u / reach, 0, 1)
        half = root * (1 - t) ** 2.2 + 1e-4
        return np.exp(-(v / half) ** 2) * (1 - t) ** 0.7 * (u < reach)

    shape = np.maximum(arms(ax, y, 0.96, 0.16), arms(ay, x, 0.96, 0.16))
    if diagonals:
        du, dv = (ax + ay) / math.sqrt(2), np.abs(ax - ay) / math.sqrt(2)
        shape = np.maximum(shape, 0.9 * arms(du, dv, 0.62, 0.13))
    core = np.exp(-(r / 0.14) ** 2)
    halo = np.exp(-(r / 0.42) ** 2) * 0.35
    return np.maximum.reduce([shape, core, halo])


if __name__ == "__main__":
    save("legendary_sunburst", sunburst())
    save("legendary_sparkle", star())
    save("legendary_sparkle8", star(diagonals=True))
    print("wrote legendary_sunburst, legendary_sparkle, legendary_sparkle8 to", os.path.normpath(OUT))
