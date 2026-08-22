# Fish Simulation Engine — Abstraction Plan

**Written:** 2026-08-22 · **Supersedes** the Phase 1/Phase 2 split in
[`fish-simulation-handoff.md`](fish-simulation-handoff.md) as the forward plan. The handoff doc's
invariants and empirical lessons remain binding; its build order and MCP-bridge verification steps
do not.

**Decision (user, 2026-08-22):** skip single-tank-first gating and target multi-tank area
simulation directly; extract the simulation into a **completely Minecraft-free engine** modeling
fish in a voxel area, so correctness and motion quality are verified by unit tests and headless
output rather than in-game capture or eyeballing. The MCP bridge is retired.

---

## 1. Module layout

### `:fishsim` — a new plain-Java Gradle subproject

A `java-library` subproject with **no Minecraft, no loom, no loader dependencies** — just JUnit.
This is deliberately a subproject rather than a package inside `common/`: the compile classpath
*enforces* the abstraction (an accidental `net.minecraft` import is a compile error, not a code
review catch), and its tests run in seconds without bootstrapping loom.

```
fishsim/
  src/main/java/grill24/fishsim/
    core/      FlockEngine, FishSpec, Tunables, SimMath
    domain/    VoxelDomain, DistanceField, RunLengths
    render/    FrameRenderer (Java2D → BufferedImage)
    harness/   HeadlessRunner, Metrics, FrameExport (PNG / animated GIF)
    viewer/    SimViewer (Swing window; optional main class)
  src/test/java/grill24/fishsim/   invariant + parity tests
```

`common` depends on `:fishsim` (core + domain only reach the mod jar; harness/viewer are excluded
from shading). Wiring note: keep the subproject out of loom's remap path — it is plain Java.

### The adapter — the only code that knows both worlds

`common/.../client/renderer/TankFlockAdapter` replaces the MC-facing half of today's
`TankFlockSimulation`:

- **In:** tank contents → `FishSpec[]` (rendered length via `ItemSizeHelper` × `renderCalibration`,
  `floorAnchored`/`swim` flags from `FishAnimationConfig`, seed, base rotation). The engine never
  sees an `ItemStack` or an animation config.
- **Out:** engine state → pose-stack transforms, per-fish `ItemStackRenderState[]` (still one per
  fish — submit defers to end of frame), `mirrored` flag from engine heading, bank/speed into the
  `FishAnimator` coupling.
- `ClientTankFlocks` (registry, 20 Hz tick, 30 s eviction) stays as-is, now ticking engines.

---

## 2. Engine design

### 2.1 Core (`FlockEngine`)

A port of `TankFlockSimulation`'s step logic, stripped of MC types (`Mth` → `SimMath`), with the
Phase-1-proven rules kept as first-class model rules, not render afterthoughts:

- **Fixed `dt = 1/20`** step; engine stores prev + current positions and exposes a pure
  `interpolate(alpha)` — frame-rate independence by construction.
- **2.5D broadside constraint:** binary heading (±lateral) with hysteresis deadzone; depth damped
  toward home planes; reversal = flip flag, never a yaw sweep.
- **Soft wall avoidance shapes the desired velocity** (the hard-force version empirically caused
  jitter + 2–3 s flip storms); hard clamp remains only as an unreachable backstop.
- **No min-speed floor** (velocity zero-crossing sells the turn); wander field prevents stalls.
- **Topological flocking** (k=6 nearest), brute force, zero allocation per step.
- **`Tunables` becomes a record** passed to the engine instead of static finals — this is what lets
  tests and the harness sweep parameter sets. The mod passes one canonical instance.
- **Layer planes generalize:** instead of the fixed `LAYER_Z = {−0.25, 0, 0.25}` of one tank, home
  depth planes are spaced 0.25 blocks across the domain's local depth extent.

### 2.2 Domain (`VoxelDomain` + `DistanceField`)

A locked multi-tank group is a rectilinear union — exactly a **voxel occupancy grid at block
resolution** (an L-shaped aquarium is a handful of bits). On domain change (rare — build/lock
time), precompute:

- **Occupancy bitset** over the group's bounding box, each cell shrunk by the shape's interior
  inset (ties into `FishTankShape`; a uniform inset first, per-shape later).
- **Distance field** sampled at sub-voxel resolution (~4 samples/block): distance to nearest wall
  + gradient. Wall avoidance becomes one array lookup per fish per tick, and concave corners (the
  L-bend) come out right — which AABB-union signed distance gets wrong at the seam.
- **Run lengths:** per-row lateral runs from the occupancy grid; the size gate reads the domain's
  longest run (`GATE_FACTOR = 2.5` body lengths, unchanged; gate failures keep the static hover).

Static obstacles (hovering gated fish, floor cosmetics) can later be stamped into the field.

A single tank is the degenerate 1-voxel domain — small fish still shoal there for free; parity
with today's behavior in that case is a test (§4.1).

---

## 3. Verification model

### 3.1 What tests prove (the smoothness guarantees, as numbers)

All in `:fishsim:test`, seconds to run, over a matrix of seeds × domains (1×1×1, 3×1×1, L-shape,
2×2 slab) × fish counts:

| # | Invariant | Guards against |
|---|---|---|
| 1 | Zero wall penetration over 10k ticks | containment bugs |
| 2 | Accel ≤ `maxForce`, speed ≤ `maxSpeed`, bounded jerk | darting, snapping |
| 3 | Mirror-flip rate ≤ bound per fish per 10 s; no deadzone violations | the flip-storm regression |
| 4 | Near-wall vertical-velocity variance below threshold | the wall-jitter regression |
| 5 | Bitwise-identical trajectories for identical seed + tunables | non-determinism |
| 6 | Mean nearest-neighbor distance inside a band after warmup; min pairwise above floor | clumping / scattering |
| 7 | L-domain: fish occupy both arms over time; windowed displacement never below stuck threshold | corner-stuck fish |
| 8 | Size-gate table: spec length × domain runs → swims/hovers exactly per §A.0 math | wrong fish free-swimming |
| 9 | `interpolate` is the only time-varying render-path call; step count decoupled from call count | frame-rate dependence |
| 10 | Single-voxel domain parity vs. recorded Phase 1 trajectories | silent behavior drift during extraction |

A shared `Metrics` class feeds both the tests and a sweep harness that writes per-tunable-set CSV —
tuning becomes reading a table, not playtesting.

### 3.2 Headless visual output — how I iterate alone

`HeadlessRunner` runs N ticks and exports PNG trajectory plots / heatmaps and short animated GIFs
via `FrameRenderer`. I can read those directly and tune `Tunables` without the game or your eyes.

### 3.3 The standalone viewer — yes, scoped

**Verdict: worth building, precisely because it is nearly free once §3.2 exists.** The frame
export already requires drawing fish, walls, and the distance field to a `BufferedImage`; the
"viewer" is a ~150-line Swing shell (not an applet — applets are dead tech; this is a desktop
window via `./gradlew :fishsim:runViewer`) around that same `FrameRenderer`:

- Orthographic side view — the same view a player has through the glass, so what reads well in the
  viewer transfers — plus an optional top view for depth-layer behavior.
- Fish drawn as oriented capsules scaled to spec length, heading + bank visible, gated hover-fish
  drawn dimmed as obstacles.
- Pause / single-step / speed, reseed, and **live `Tunables` sliders** — this is where the viewer
  pays for itself: a tuning loop measured in seconds.
- Overlays: distance-field heatmap, neighbor links, per-fish velocity vectors.

Because the window and the headless export share one renderer, they can never disagree, and the
viewer adds no dependency and never ships in the mod jar. What it does **not** replace: one
eventual in-game acceptance pass for sprite readability (tail-beat feel, glass refraction context)
— but that becomes a final check, not the iteration loop.

---

## 4. Build order

1. **Extract:** create `:fishsim`, port the engine core with `Tunables`, keep the single-box
   domain, and record parity fixtures from the current `TankFlockSimulation` **before** deleting
   its logic (test 10 depends on capturing them first).
2. **Adapter:** rewire `common` through `TankFlockAdapter`; both loaders compile; no behavior
   change intended — parity test is the gate.
3. **Tests + metrics:** land the §3.1 suite against the single-box domain.
4. **Voxel domain:** `VoxelDomain` + `DistanceField` + run lengths; L-shape and slab domains join
   the test matrix; generalize layer planes.
5. **Headless export**, then the **viewer shell**; tune `Tunables` against metrics + GIFs.
6. **Feed real multi-tank domains:** short-term, derive occupancy client-side from the existing
   `openFaces` adjacency for preview; the server-side lock model, pooled `TankCapacity`, expanded
   render AABB, and per-fish light sampling remain the separate Minecraft-side workstream they
   always were (feasibility §A.7) — now unblocked from simulation iteration.

## 5. Invariants carried forward unchanged

Renderer extract never advances sim time; floor-anchored species untouched; gate failures render
pixel-identical to today; `mirrored` is a 180° yaw, never a negative scale; simulation stays
client-only and non-authoritative; **no maintenance mechanics, ever**.
