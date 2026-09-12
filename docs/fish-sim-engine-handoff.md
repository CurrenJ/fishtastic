# Fish Sim Engine Extraction — Implementation Handoff

**Status:** Design complete (see [`fish-sim-engine-plan.md`](fish-sim-engine-plan.md)), no code
written. **Written:** 2026-08-22 · All file:line references below verified against
`feature/26.1.2/fish_simulation` on that date.

---

## 0. Read these first (≈20 minutes)

| Order | Document | What you get from it |
|---|---|---|
| 1 | [`fish-sim-engine-plan.md`](fish-sim-engine-plan.md) | The what and why: module layout, engine design, the 10-invariant verification model, viewer rationale |
| 2 | [`fish-simulation-handoff.md`](fish-simulation-handoff.md) §3 + §5 | The Phase 1 invariants (still binding) and its "things that will bite you" list. Its build order and MCP-bridge steps are **superseded** — the bridge is retired; never use it |
| 3 | `TankFlockSimulation.java` top-of-class javadoc | The coordinate model (lateral/vertical/depth), 2.5D rules, and why every guard exists |

The original PDF and `docs/reviews/fish-simulation-*.md` are background only.

## 1. The job, in one paragraph

Extract the flocking simulation out of `common` into **`:fishsim`, a plain-Java Gradle subproject
with zero Minecraft imports**, generalize its domain from one AABB to a voxel occupancy grid with a
precomputed distance field (multi-tank aquariums are rectilinear unions), and stand up the
verification stack — invariant unit tests, headless PNG/GIF export, and a desktop viewer — so
motion quality is proven by numbers and rendered frames, not by running the game. The Minecraft
side shrinks to a thin adapter. Behavior in-game must be unchanged until the voxel domain lands.

## 2. Map of what exists today

| Concern | Where | Notes |
|---|---|---|
| The whole sim | `common/.../client/renderer/TankFlockSimulation.java` | Constants `:41`–`:87`, `sync` `:151`, `rebuild` `:165`, `step` `:292`, `stepFish` `:306`, `wallAvoidance` `:432`, `speedFactor` `:441`, `interpolate` `:451`, `renderedLength` `:496` |
| Its MC entanglements | imports of `TankFlockSimulation` | Exactly: `FishTankBlockEntity`, `FishAnimationConfig`, `SwarmConfig`, `ItemSizeHelper`, `ItemStackRenderState`, `ItemStack`, `Level`, `Mth`. This list is the extraction's scope |
| Domain interface | `common/.../client/renderer/FlockDomain.java` | Already an interface with a `Box` record — made for this. Moves to `fishsim` |
| Registry / lifecycle | `common/.../client/util/ClientTankFlocks.java` | `getOrCreate` `:36`, `tickAll` `:52`, `clear` `:61`, 30 s eviction `:25`. Stays in common; will hold adapters instead of sims |
| Renderer touchpoint | `FishTankBlockEntityRenderer.java:296` | `state.flock = ClientTankFlocks.getOrCreate(...)` in extract; submit reads the flock's arrays |
| Tick + lifecycle call sites | `FishtasticFabricClient.java` `:174`/`:183` (`clear`), `:200` (`tickAll`); `FishtasticNeoForgeClient.java` `:213`/`:222`, `:247` | Keep all six wired through the refactor |
| Plain-subproject precedent | `settings.gradle:23`–`24`, root `build.gradle` `:28` guard + `:86` `configure(:tools:*)` block | `tools:tank-shape-gen` (java-library + JUnit) and `tools:tank-shape-previewer` (JavaFX `application`) are your templates |
| Production bundling | `fabric/build.gradle` (`shadowBundle` → `shadowJar`), `neoforge/build.gradle:50` | How `common` reaches shipped jars: `shadowBundle project(path: ':common', configuration: 'transformProduction<Platform>')` |
| Multi-tank adjacency | `FishTankBlockEntity.updateConnections` / `openFaces` | Source for deriving voxel occupancy client-side (Task 8) |

## 3. Invariants — do not break these

All of Phase 1's (handoff §3): extract never advances sim time; fixed 20 Hz step + `partialTick`
interpolation only; floor-anchored species untouched; gate failures render pixel-identical to
today; `mirrored` is a 180° yaw, never a negative scale; sim stays client-only and
non-authoritative; no maintenance mechanics ever. Plus three new ones:

8. **`fishsim` never imports Minecraft or mod code.** The subproject's compile classpath is the
   enforcement — if you need something from `common`, you're putting it on the wrong side of the
   adapter.
9. **Soft containment stays soft.** Wall avoidance shapes the desired velocity; the hard clamp is
   a backstop the tests must show is never load-bearing. (Empirical: a hard wall force caused
   vertical jitter + mirror flips every 2–3 s.)
10. **No behavior change lands without its parity or invariant test landing first.** The point of
    this project is that you never have to boot the game to know the sim is right.

## 4. Task breakdown

Build strictly in this order — Tasks 2→3→4 are one atomic refactor from the game's point of view
and should merge together with parity green.

### Task 1 — Wire the subproject

`include 'fishsim'` in `settings.gradle` (top-level, not under `tools:` — it ships in the mod).
Root `build.gradle:28` guards loom application with `project.path.startsWith(':tools:')`; extend it
to also skip `:fishsim`, and give `fishsim` the same java-library + JUnit setup as
`tools/tank-shape-gen/build.gradle` (Java 25 toolchain to match the root).

Then bundling — **this is the trap in this task**: `tools:tank-shape-gen` is `implementation` on
fabric only and is *not* in any `shadowBundle`, which is fine for datagen-time code and fatal for
runtime code. `fishsim` is runtime code. Each platform needs both a classpath dep (dev runs) and a
`shadowBundle project(':fishsim')` (production), mirroring how `:common` itself is bundled. If you
skip this, dev runs work and the shipped jar throws `NoClassDefFoundError`.

**Done when:** `./gradlew :fishsim:test` runs a trivial test in seconds with no loom bootstrap, and
`jar tf` on **both** built platform jars lists `grill24/fishsim/` classes.

### Task 2 — Port the core

Into `grill24.fishsim.core`: `SimMath` (port `Mth.clamp`/`Mth.lerp` with *identical float
semantics* — `lerp = start + t * (end - start)` — parity in Task 3 depends on bitwise-equal ops),
`Tunables` record (fields = the constants at `TankFlockSimulation.java:41`–`:87`, defaults
unchanged), `FishSpec` record (`length`, `swims`, `homeDepth`, `seed`, plus wander phases), and
`FlockEngine` — the state arrays and `step`/`stepFish`/`findNearestSwimmers`/`wander*`/
`wallAvoidance`/`interpolate` logic, minus everything touching `ItemStack`, `ItemStackRenderState`,
or the block entity. Move `FlockDomain` to `grill24.fishsim.domain` unchanged. Preserve the
solo-fish special case (`rebuild`, n == 1: centered, no scatter, no jitter) and the flat-`0.5`
length for unmeasured stacks (`renderedLength:496` — that mapping moves to the adapter, but the
gate math consuming it moves to the engine).

**Done when:** `:fishsim` compiles standalone; grep confirms no `net.minecraft` / mod imports.

### Task 3 — Parity, before deleting anything

In `fishsim`'s *test* sources, keep a `LegacyStepReference` — a copy of the old
`stepFish`/`wander*`/`wallAvoidance` float math (it touches nothing but floats and `Mth`, so the
copy needs no MC imports). Test: identical seeds, specs, and tick counts (≥10k) produce
**bitwise-identical** trajectories from `FlockEngine` and the reference, across a matrix of fish
counts and domains. Also record a small golden-trajectory fixture file as a long-term drift guard.
Delete `LegacyStepReference` only after Task 4 ships; keep the golden fixture forever.

**Done when:** parity is green across the matrix.

### Task 4 — The adapter rewire

`TankFlockAdapter` in `common/.../client/renderer/` owns everything the engine lost: content-change
detection (`sync:151`'s slot scan), `ItemStack` → `FishSpec` mapping (`ItemSizeHelper` ×
`renderCalibration`; `FishAnimationConfig` → `swims`/floor-anchored flags — the engine never learns
what an animation config is), the per-fish `ItemStackRenderState[]` (still one per fish — submit
defers to end of frame), hover-path passthrough for gate failures, and engine output → pose
transforms including the `mirrored` flag. `ClientTankFlocks` keys adapters instead of sims; all six
tick/clear call sites unchanged. Delete `TankFlockSimulation` once the renderer compiles against
the adapter.

**Done when:** both loaders build and run, in-game behavior is unchanged (parity + golden fixtures
are the evidence; a quick manual look is a courtesy, not the gate), and no server-side diff exists.

### Task 5 — Invariant tests + metrics

Implement the 10-test suite from plan §3.1 with a shared `Metrics` class (also used by Task 7's
harness and the tunables-sweep CSV). Tests 3 and 4 are regression tests for the two empirically-hit
Phase 1 bugs (flip storms, wall jitter) — get their thresholds from measuring current behavior,
then tighten.

**Done when:** suite is green in under ~30 s and each of the 10 invariants has at least one test.

### Task 6 — Voxel domain

`VoxelDomain` (occupancy bitset at block resolution, per-cell interior inset — uniform inset
first), `DistanceField` (~4 samples/block, distance + gradient, rebuilt only on domain change),
`RunLengths` (per-row lateral runs; size gate reads the longest). Generalize home layer planes to
0.25-block spacing across the local depth extent. Wall avoidance switches from per-axis box margins
to one field lookup. Add L-shape and 2×2-slab domains to every test matrix, including the
corner-traversal and stuck-detector tests. The single 1×1×1 voxel domain must reproduce `Box`
behavior (parity fixtures again).

**Done when:** L-domain tests pass — fish visit both arms, never penetrate the concave corner,
never go stuck — and 1-voxel parity holds.

### Task 7 — Headless export

`FrameRenderer` draws a frame (fish as oriented capsules scaled by length, heading + bank visible,
gated hover-fish dimmed, walls, optional distance-field heatmap) to a `BufferedImage` using
**Java2D only — no JavaFX/Swing imports here**, so it runs headless. `HeadlessRunner` gradle task
exports PNG trajectory plots and short animated GIFs for a given seed + tunables.

**Done when:** one gradle command produces frames Claude (or you) can inspect without a display or
the game.

### Task 8 — The viewer

A desktop shell around the *same* `FrameRenderer` — blit the `BufferedImage`, add pause /
single-step / speed / reseed and live `Tunables` sliders. Follow the `tools:tank-shape-previewer`
JavaFX `application` precedent, or plain Swing if you prefer zero plugins; either way the renderer
stays shell-agnostic. Never shipped in the mod jar (it lives in `fishsim` but its classes are
harness/viewer packages — exclude them from the shadowBundle, or accept the few KB; do not let the
JavaFX plugin leak onto the platforms' classpaths).

**Done when:** `./gradlew :fishsim:runViewer` opens the sim and slider changes take effect live.

### Task 9 — Multi-tank occupancy feed

Client-side preview only: derive `VoxelDomain` occupancy from the existing
`FishTankBlockEntity.updateConnections`/`openFaces` adjacency for a connected group, with one
elected sim per group. The server-side lock model, pooled `TankCapacity`, expanded render bounding
box, and per-fish light sampling are **out of scope** — they're the separate Minecraft-side
workstream (feasibility §A.7) that this project unblocks but does not include. Expect rendering
artifacts (frustum culling at the anchor, single-block lighting) in this preview; note them, don't
fix them here.

**Done when:** a 3×1 row of tanks runs one shoal across the shared volume in-game.

## 5. Things that will bite you

- **The bundling trap** (Task 1). Dev runs lie: everything on the loom runtime classpath works in
  `runClient` and vanishes from the shadowJar. Verify with `jar tf` on both platform jars.
- **The root loom guard** (`build.gradle:28`). Miss it and loom tries to configure `:fishsim`,
  failing with errors that say nothing about the actual cause.
- **`extractRenderState` runs more than once per game tick.** Same as always — the adapter reads
  and interpolates; only `ClientTankFlocks.tickAll()` steps.
- **Float-parity is fragile.** Keep operation order identical when porting math; `Math.fma`,
  reassociation, or "cleaning up" an expression breaks bitwise parity and you won't know if the
  diff is a port bug or noise. Clean up *after* Task 3's gate is green, with the tests watching.
- **Don't let `FrameRenderer` grow a UI dependency.** The moment it imports JavaFX/Swing, headless
  export breaks on CI-like environments and the shared-renderer guarantee dies.
- **The MCP bridge is retired.** Do not use it for verification or screenshots, even though older
  docs mention it. The headless export is its replacement.
- **The NeoForge gametest pre-commit hook frequently hangs.** Run commits in the background; a
  hang is not evidence your change is broken.
- **Windows dev box** (PowerShell primary). Watch path separators in any scripts/gradle exec tasks;
  see the `python` vs `python3` note in `fabric/build.gradle` for the established pattern.

## 6. Open decisions for you to make

| Decision | Recommendation |
|---|---|
| Viewer shell: JavaFX vs Swing | JavaFX matches the `tank-shape-previewer` precedent; Swing avoids the plugin. Either — the renderer must not care |
| GIF encoding | Simplest thing that works: PNG frame strips first; add an animated-GIF encoder only if strips prove insufficient |
| `FlockDomain` interface: keep or collapse into `VoxelDomain` | Keep through Task 6 (parity needs `Box`); collapse after if `Box` has no remaining caller |
| Distance-field resolution (4 samples/block) | Start there; it's a `Tunables`-adjacent constant, tune with the L-domain tests watching |
| Where harness/viewer classes live vs. what ships | Same subproject, separate packages, excluded from shadowBundle — revisit only if jar size ever matters |

## 7. Definition of done

- [ ] `:fishsim` has zero Minecraft/mod imports, and its test suite runs standalone in seconds.
- [ ] Bitwise parity with the legacy step across the seed/count/domain matrix; golden fixtures
      committed.
- [ ] All 10 invariant tests green, including L-domain traversal and the flip-storm / wall-jitter
      regression tests.
- [ ] Both platform jars contain the engine classes (`jar tf` verified), both loaders run, and
      in-game single-tank behavior is unchanged.
- [ ] Floor-anchored species and gate-failing fish render exactly as today.
- [ ] Headless export produces PNG/GIF output from one gradle command; the viewer runs with live
      tunables.
- [ ] A 3×1 connected row runs one shoal across the shared volume (client-side preview).
- [ ] No server-side diff anywhere in the changeset.
