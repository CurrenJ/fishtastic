# Cosmetic render cost (2026-10-03)

What tank cosmetics cost to draw, measured rather than estimated. Every number here comes from a
benchmark run on one machine. Where a measurement couldn't be taken, it's listed as not measured.

## Setup

- **Machine.** AMD Ryzen 9 5900X (12 cores), 32 GB RAM, NVIDIA GeForce RTX 3080 Ti (driver
  591.86), Windows 11.
- **Client.** Fabric dev client: MC 26.1.2, Sodium 0.9.1, Iris 1.11.3. Shaders are Complementary
  Reimagined r5.9.3 + IRLights (the dev config's pack). Fullscreen at 2560×1440 unless noted,
  vsync off, frame rate uncapped.
- **NeoForge (vanilla renderer) was not measured.** Both NeoForge runs hung after the self-test's
  `creating flat world`, with no error in either log. The Oct 1 perfbench ran on NeoForge, so
  this broke since then and is undiagnosed. Nothing below should be read as a NeoForge result,
  except the bake and scan microbenchmarks, which are plain Java and don't touch the renderer.

## How cosmetics are drawn (from the code)

- **Static cosmetics are baked into the tank's chunk mesh** (`TankCosmeticMesh`): snapshot per
  tank → bake on a meshing thread → cached by snapshot value. Only chests are drawn per frame.
- **Spans drop faces against solid neighbours; floor structures don't** (`cullMask` is 0 in
  `addStructures`).
- **Every frame, for every visible tank with a floor structure**, `FishTankBlockEntityRenderer`
  walks all of the structure's parts twice. `renderStructureCosmetics` looks for chests and
  `spawnDueFurnaceParticles` looks for lit furnaces, and both call `part.state().rotate(rotation)`
  on every part. Spans are filtered out of this path.

## Method

- **`bakebench`** (`RenderSelfTest` + `client/perf/CosmeticCostBench`). For every shipped
  structure: pieces, quads by chunk layer, and cold-cache bake time (21 timed repetitions after a
  JIT warm-up). For this investigation it also counted quads under the other face-culling rule,
  and its `[scanbench]` lines replicated the renderer's two per-frame scan loops and timed
  `rotate(Rotation.NONE)` per block type. Both were removed once their questions were settled
  (§3, §2); their results are below.
- **`scalebench`**. Copies of a structure laid out in view: spans 1, 4 and 9 boxes; floor
  structures 9 and 36 tanks. Each layout is measured in two arms, 6 paired rounds of 3,000 frames,
  with the arm order alternating by round. Per round, the difference in mean frame, CPU and GPU
  time; reported as the mean difference ± a 95% Student-t interval over rounds. The arms are:
  - `ab=mesh`: cosmetics off (NONE) against baked into the mesh (MESH);
  - `ab=scan`: both arms meshed, the per-frame floor-structure part scans on against skipped
    (`CosmeticBenchmark.skipStructureScans`, benchmark only). Since the fix it is a regression
    check: the difference should stay at zero.
  - `ab=cull` (removed): both arms meshed, floor structures with and without neighbour face
    culling, through a temporary switch.
- **A/A control.** `empty`, tanks with nothing in them, so both arms draw the same thing. Its
  intervals are the noise floor.
- **Timing.** CPU time is `renderFrame` up to present. GPU time is a timer query over the whole
  frame.

Reproduce:

```
# marker file fabric/run/fishtastic_render_selftest, one item per line:
bakebench
# or
scalebench
frames=3000
rounds=6
label=noshaders
fullscreen=true
ab=mesh
structure=empty
structure=leviathans_seat
spanCounts=1,4,9
floorCounts=9,36

python tools/perfbench/bake_table.py fabric/run/logs/latest.log
python tools/perfbench/scale_analyze.py fabric/run/fishtastic_bench fabric/run/logs/latest.log
```

## Findings

### 1. Without shaders, the meshed geometry has no measurable frame cost

Fabric/Sodium, 2560×1440, frames at about 0.75 ms. Across 22 scenes from 3,476 up to 126,252
cosmetic quads, every MESH − NONE interval for frame and GPU time spans zero. The pooled fit is
+0.03 ± 0.9 µs per 1,000 quads (R² 0.00). At 126,252 quads (36 Moon Gates), the upper bound on the
frame cost is 0.11 ms. The A/A control resolves about ±0.08 ms of frame time and ±0.01 ms of CPU.

Two small effects were seen and are not explained. In MESH mode, span scenes show about +0.02 ms
of CPU that doesn't grow from 1 to 9 copies; `coral_reef_1` shows about −0.02 ms.

### 2. With shaders, the meshed geometry costs GPU time, and floor quads cost more than span quads

Fabric/Sodium/Iris, 2560×1440, frames at about 4.3 ms, GPU-bound. MESH − NONE:

| scene | quads | GPU | per 1,000 quads |
|---|---|---|---|
| empty, 9 tanks (A/A) | 0 | −0.030 ± 0.071 ms | — |
| empty, 36 tanks (A/A) | 0 | +0.025 ± 0.038 ms | — |
| Leviathan's Seat ×9 | 20,088 | +0.106 ± 0.043 ms | 5.3 µs |
| Leviathan's Seat ×36 | 80,352 | +0.282 ± 0.016 ms | 3.5 µs |
| Moon Gate ×9 | 31,563 | +0.111 ± 0.008 ms | 3.5 µs |
| Moon Gate ×36 | 126,252 | +0.283 ± 0.016 ms | 2.2 µs |
| Sunken Ziggurat ×1 | 8,399 | +0.078 ± 0.027 ms | — |
| Sunken Ziggurat ×9 | 75,591 | +0.034 ± 0.008 ms | 0.45 µs |
| Drowned Cathedral ×1 | 5,227 | +0.073 ± 0.045 ms | — |
| Drowned Cathedral ×9 | 47,043 | +0.146 ± 0.270 ms | (too noisy) |

Floor-structure quads cost 2.2–5.3 µs per 1,000; the nine Ziggurats cost 0.45. A single span
copy measures about +0.07 ms, more than nine Ziggurats do. That isn't explained either.

An earlier shader run came up at 854×480, because that launch started windowed even with
fullscreen set in `options.txt`. It is kept separately and isn't mixed into these tables. At that
size, 36 Moon Gates measured +0.255 ± 0.013 ms of GPU. The harness now forces fullscreen
(`fullscreen=true`) and logs the size it ran at.

**Does neighbour face culling for floor structures recover the gap? Not established.** `ab=cull`
(shaders, 2560×1440, 36 tanks; the cull checked in the bake counters: Leviathan 2,232 → 1,283
quads, Moon Gate 3,507 → 2,395). Criteria fixed before the confirmatory run (12 rounds ×
1,500 frames): mean ± 95% CI, median, sign count.

| scene | mean cull − nocull (GPU) | median | rounds culled faster |
|---|---|---|---|
| empty ×36 (A/A) | +0.027 ± 0.089 ms | −0.009 ms | 9/12 |
| Leviathan's Seat ×36 | −0.065 ± 0.134 ms | −0.092 ms | 11/12 |
| Moon Gate ×36 | −0.123 ± 0.129 ms | −0.063 ms | 12/12 |

Both means' intervals include zero. The medians and signs lean toward a saving of
0.06–0.09 ms (about a quarter of these scenes' geometry cost), but the A/A control was "faster"
in 9/12 too, so the sign test carries a bias this design can't remove. An earlier 6-round run
was also inconclusive. **Noise source:** under shaders the GPU switches between two performance
levels (~3.78 and ~4.5 ms per frame) mid-run, visible in the A/A control. A switch inside a
pair adds ±0.7 ms to that round. Locking GPU clocks (`nvidia-smi -lgc`, needs admin) would be
the next step.

### 3. The real per-frame CPU cost is the floor-structure part scans, set by block choice

`ab=scan` (both arms meshed, Fabric, no shaders, 2560×1440). Skipping the two scans saves:

| structure | parts | 9 tanks | 36 tanks | per tank-frame | microbenchmark |
|---|---|---|---|---|---|
| castle_ruin | 63 | +0.011 ± 0.017 ms | +0.036 ± 0.009 ms | 1.0 µs | 0.41 µs |
| coral_reef_1 | 116 | +0.013 ± 0.008 ms | +0.045 ± 0.012 ms | 1.25 µs | 1.0 µs |
| moon_gate | 505 | +0.043 ± 0.007 ms | +0.193 ± 0.014 ms | 5.4 µs | 6.4 µs |
| leviathans_seat | 328 | +0.150 ± 0.008 ms | **+0.586 ± 0.018 ms** | 16.3 µs | 16.2 µs |
| empty (A/A) | 0 | +0.006 ± 0.011 ms | −0.007 ± 0.014 ms | — | — |

The cost is linear in tanks. The microbenchmark, run outside the frame, reproduces the in-frame
figures. Part count doesn't explain it: the Leviathan has fewer parts than the Moon Gate and costs
3× as much. What explains it is `rotate(Rotation.NONE)`:

| block | properties | rotate(NONE) |
|---|---|---|
| stone_bricks, bone_block (no rotate logic) | 0–1 | 2.8 ns |
| quartz_stairs (one `setValue`) | 4 | 6.5 ns |
| mushroom_stem, brown_mushroom_block | 6 | **40 ns** |

Across all 141 block types in floor structures the range is 2.4–40 ns.
`HugeMushroomBlock.rotate` reads and re-sets all six face properties even for `Rotation.NONE`.
The Leviathan's Seat is 134 of 328 parts mushroom blocks: 134 × 2 scans × 40 ns ≈ 10.7 µs of its
16. The mushroom blocks went in during the 2026-10-03 de-boxing pass, as bone tones.

With shaders on, the same effect shows: 36 Leviathan tanks in NONE mode used +0.70 ms of CPU over
36 empty tanks (480p run) and +0.53 ms (1440p run).

### 4. Bake cost is small, linear in quads, and paid once

Cold-cache bake time fits quads at R² 0.95: about 0.16 ms per 1,000 quads, on a meshing thread.
The heaviest structure, the Sunken Ziggurat (8,399 quads across 6 tank shares), bakes in
1.30 ms (p10–p90 1.29–1.36). Identical snapshots share one bake. Top of the table (all 53 are in
the `bake_table.py` output):

| structure | kind | parts | quads | bake ms p50 | other culling rule: quads |
|---|---|---|---|---|---|
| sunken_ziggurat | span | 2,393 | 8,399 | 1.30 | 15,124 (unculled) |
| drowned_pagoda | span | 1,080 | 6,925 | 0.90 | 8,722 |
| drowned_cathedral | span | 1,680 | 5,227 | 0.85 | 10,518 |
| coral_warren | span | 1,570 | 5,219 | 0.83 | 9,603 |
| capsized_galleon | span | 1,470 | 4,906 | 0.80 | 9,192 |
| moon_gate | floor | 505 | 3,507 | 0.52 | 2,395 (culled) |
| sea_arch | span | 1,087 | 3,476 | 0.98 | 6,830 |
| mangrove_knees | floor | 291 | 2,606 | 0.38 | 2,464 |
| leviathans_seat | floor | 328 | 2,232 | 0.41 | 1,283 |
| basalt_grotto | floor | 346 | 2,130 | 0.27 | 894 |
| drowned_bell | floor | 349 | 2,104 | 0.27 | 830 |
| amphora | floor | 269 | 1,653 | 0.21 | 694 |

Culling halves span quads, and would cut floor quads by 5–61% (Moon Gate 32%, Leviathan 43%,
Basalt Grotto 58%, Drowned Bell 61%). Translucent quads are rare: only the Drowned Cathedral
(265) and Vintage Vine (96) have any.

Rebuild latency in the frame couldn't be measured on Sodium. `hasRenderedAllSections()` returned
true within 2 ticks every time, so it doesn't track Sodium's meshing.

### 5. GPU memory is vertex data proportional to quads

Vertex size, read from the code that runs: vanilla chunk layers 28 bytes (logged at runtime, all
three layers); Sodium `CompactChunkVertex.STRIDE` = 20 bytes (0.9.1, from its bytecode). Per copy,
vertex data = quads × 4 × stride. A Sunken Ziggurat is 919 KiB on vanilla and 656 KiB on Sodium.
Each copy has its own, since section meshes aren't shared.

Not measured: Iris's terrain format, which is built at runtime from what the shader pack asks
for, so its stride can't be read from the jar; index buffers; and the JVM-side bake cache.

## What this means

1. **Fixed: the per-frame floor-structure scans.** `CosmeticStructure.liveParts()` finds each
   structure's chests and lit furnaces once per structure instance. The renderer's per-frame loops
   now visit only those parts, rotating only the chest it draws and a furnace when a particle is
   due. `LivePartsTest` checks that, for every shipped structure at every rotation, it picks out
   exactly what the old scan did.
   **Verified** (`ab=scan`, 36 Leviathan's Seats, no shaders, 2560×1440, 6 rounds × 1,500 frames):
   the scans' CPU cost went from +0.586 ± 0.018 ms to **−0.000 ± 0.019 ms**. That scene's CPU time
   (0.47 ms) is now in line with 36 empty tanks (0.45 ms). A floor structure's per-frame CPU cost no
   longer depends on how many parts it has or which blocks they are, so the mushroom-block
   penalty is gone too.
2. **Not pursued: neighbour face culling for floor structures.** It would cut floor quads by 5–61%,
   but there is nothing to gain without shaders (§1), and under shaders the gain was suggestive
   but not significant (§2). Not worth the risk of changing which faces structures show. The
   temporary switch and the bakebench counterfactual were removed.
3. **Bake cost and memory need no action** at the sizes measured. What still scales with build size
   is GPU time under shaders (2–3.5 µs per 1,000 floor quads) and vertex memory (§5).

## Not measured

- NeoForge / the vanilla renderer (both runs hung at world creation).
- Other GPUs or CPUs; only one machine was measured.
- Close-up views where a structure fills the screen. Scenes were wide shots, so fragment-heavy
  cases may cost more.
- Rebuild latency on Sodium, Iris's vertex stride, and JVM memory.
- Causes of: the constant ~0.02 ms CPU in meshed span scenes; one span copy measuring more GPU time
  than nine Ziggurats under shaders.

## Data

Raw per-frame CSVs are in `fabric/run/fishtastic_bench/scale_fabric_*`: no shaders, shaders at
1440p, the 12-round cull A/B, and the post-fix scan check. The pre-fix scan A/B, the 6-round cull
A/B and the 480p shader run were set aside outside the repo, not deleted.
