# Fish Tank Simulation — Implementation Handoff

**Status:** Design complete, no code written. Phase 1 is ready to start.
**Written:** 2026-08-20

---

## 0. Read these first (≈15 minutes)

| Order | Document | What you get from it |
|---|---|---|
| 1 | [`docs/reviews/fish-simulation-architecture-review.md`](reviews/fish-simulation-architecture-review.md) | Critique of the original boids proposal, and the corrected simulation core (§4) and build order (§5) |
| 2 | [`docs/reviews/fish-simulation-feasibility.md`](reviews/fish-simulation-feasibility.md) | Why the tank is the right home, the fish-size constraint (§A.0), the locked-aquarium design (§A.7), and the phase split (§4) |
| 3 | [`docs/feature-design-overview.md`](feature-design-overview.md) | The design principles this feature must not violate |

The original PDF (`minecraft_fish_simulation_architecture_report.pdf`) is background only. Roughly
half its recommendations — spatial hashing, voxel probes, flood-fill, GPU instancing — do not apply
inside a fish tank and should not be built. Read the review before the PDF, not after.

---

## 1. The decision, in one paragraph

Fish in tanks currently do not move; they sit at hash-derived offsets and play closed-form sine
animations. We are replacing that with a **client-only, non-authoritative 2.5D flocking simulation
confined to the tank interior**. It is client-side and cosmetic: no entities, no server tick, no
network sync, and **no requirement that two players see identical fish positions**. Because a
100 cm fish renders roughly as long as the entire block it lives in, fish free-swim only when the
domain gives them room — everything else keeps today's hover animation. Phase 1 delivers this inside
a single tank (so: small fish only). Phase 2 adds a player-locked multiblock aquarium, which is what
gives large fish somewhere to swim.

**Do not start Phase 2.** Phase 1 exists to answer whether the simulation looks good before we
commit to the persistent state, the pooled inventory and the cross-block rendering work.

---

## 2. Map of what exists today

Everything below is current on branch `26.1.2`.

| Concern | Where | Notes |
|---|---|---|
| Per-frame fish layout | `FishTankBlockEntityRenderer.buildSwarmInstances` (`:387`) | Rejection-sampled scatter, seeded from `blockPos.hashCode()`, rebuilt **every** extract |
| Per-fish render data | `SwarmFishInstance` | `stack`, `animationConfig`, `renderCalibration`, `baseRotation`, `x/y/zOffset`, `seed`, `mirrored` |
| Swim volume | `TANK_HALF_EXTENT = 0.35` (`:255`), `LAYER_Z = {-0.25, 0, 0.25}` (`:259`) | 0.7 blocks lateral, 0.5 deep, three fixed planes |
| Motion | `FishAnimator.apply` | Six sealed animation modes. Stateless, closed-form, driven by `gameTimeTicks` |
| Draw + submit | `FishTankBlockEntityRenderer.submit` (`:335`–`:376`) | Where positions become pose-stack transforms |
| Render state | `FishTankRenderState` | **Reused across frames** — see `chestLastBubbleSpawnTick` (`:45`) and its javadoc |
| Species tuning | `SwarmConfig`, on `FishProfile.swarm()` | `count`, `depth_layers`, `xz_spread`, `y_range`, `rotation_jitter` |
| Occupancy gate | `TankCapacity` | Size-budget, `BASE_BUDGET = 5.0`; separate from `SwarmConfig.count`'s draw ceiling |
| Fish length | `(ItemSizeHelper.getSize(stack) / 100f) × FishProfile.renderCalibration()` | Falls back to `DEFAULT_RENDER_CALIBRATION = 0.8f`; stacks with no rolled size render at flat `0.5` |
| Client tick | `ClientTickHandler` | A passive counter. Actually *called* from `FishtasticFabricClient.java:194` and `FishtasticNeoForgeClient.java:243` |
| Tank grouping | `FishTankBlockEntity.updateConnections` (`:262`), `openFaces` (`:79`) | Already computes adjacency. Phase 2 builds on this; Phase 1 ignores it |

**Two useful facts you get for free:**

- `extractRenderState` runs only for block entities the client is actually rendering. That is your
  visibility LOD, already implemented by vanilla.
- `FishTankRenderState` persists per block entity across frames, and the codebase already relies on
  that (`chestLastBubbleSpawnTick`). You do not need new plumbing to hold state.

---

## 3. Invariants — do not break these

1. **Never advance simulation time inside `extractRenderState`.** It can run more than once per game
   tick; `chestLastBubbleSpawnTick` exists precisely because of that. Extract *reads and
   interpolates*, it never steps.
2. **Fixed timestep.** Step at 20 Hz from the client tick, interpolate with `partialTick` at render
   time. A simulation whose speed depends on frame rate is a bug, not a tuning knob — verify at
   30 fps and at 240 fps.
3. **Floor-anchored species are not swimmers.** `isFloorAnchored` (`:484`) already identifies them;
   `computeBaseY` (`:490`) already pins them to `COSMETIC_FLOOR_Y`. Crabs, plants and bottom-sitters
   must be untouched by this work. Only `HorizontalSwim` is in scope for Phase 1.
4. **A fish that fails the size gate must render exactly as it does today.** This is the regression
   gate. A 100 cm fish in a 1-block tank should be pixel-identical before and after your change.
5. **`mirrored` is a 180° yaw, not a negative scale.** See the `FishAnimator.apply` javadoc — the
   generated model's back face carries a pre-mirrored UV, and a negative scale would corrupt face
   winding. When a fish reverses direction, flip the flag; never negate the scale.
6. **Client-only.** Phase 1 touches no server code, no packets, no NBT, no datapack schema.
7. **No maintenance mechanics, ever.** No hunger, no water quality, no fish death. See feasibility
   §3.2 — this would directly violate the mod's stated identity.

---

## 4. Phase 1 task breakdown

Build in this order. Stop and *look* at the result after each step — steps 4 and 6 are where the
quality either appears or doesn't.

### Task 1 — Kill the per-frame allocations

**Files:** `FishTankBlockEntityRenderer.java:335`–`:451`

`submit` allocates `new Random(fish.seed())` (`:362`) and `new ItemStackRenderState()` (`:367`) per
fish per frame; `buildSwarmInstances` allocates a fresh `ArrayList`, runs rejection sampling and
sorts with a `Comparator` on every extract. At one tank this is invisible; at a wall of connected
tanks × 25 fish × 120 fps it is not, and the simulation makes it worse.

Move to persistent per-tank storage — parallel `float[]` arrays, a reused `ItemStackRenderState`, a
reusable `Random`. **This is worth doing on its own merits and is a prerequisite for everything
below.**

**Done when:** a profiler shows no allocation in the tank submit path.

### Task 2 — Somewhere to keep simulation state

Recommended: a client-side `ClientTankFlocks` registry keyed by `BlockPos`, living in
`client/renderer/` or `client/util/`. Entries are created on first extract, stepped from the client
tick, and evicted after N seconds without an extract.

Why a registry rather than a field on `FishTankBlockEntity`: the block entity is common code shared
with the server, and the registry makes "only tick tanks that are being rendered" explicit rather
than implicit. The tradeoff is that you own eviction; a BE field would get it free from BE lifecycle.
Either is defensible — pick one and write down why.

**Done when:** state survives across frames, is created lazily, and is evicted for tanks the player
walked away from.

### Task 3 — The client tick hook

`ClientTickHandler` is a counter, not an event bus. The actual per-tick call sites are
`FishtasticFabricClient.java:194` and `FishtasticNeoForgeClient.java:243`. Add a shared common entry
point (e.g. `ClientTankFlocks.tickAll()`) and call it from both, rather than duplicating logic.

> Ignore anything under `.claude/worktrees/` when grepping — those are stale agent copies of the
> same files and will give you four hits instead of two.

**Done when:** the sim steps exactly once per client tick on both loaders.

### Task 4 — Single-fish motion

Integrate position and velocity with **max speed, min speed and max turn rate** clamps. Interpolate
from previous→current position in `extractRenderState` using `partialTick`. Containment against the
`TANK_HALF_EXTENT` box, using the hard/soft arbitration from review §4.1 — containment *overrides*,
it does not sum into the steering vector.

**Done when:** one fish cruises the tank, turns at the walls without jitter, and moves at the same
speed regardless of frame rate.

### Task 5 — The size gate

```
length      = (size / 100) × renderCalibration       // ItemSizeHelper + FishProfile
freeSwims   = !isFloorAnchored(mode)
           && mode instanceof HorizontalSwim
           && domainShortestRun >= GATE_FACTOR × length     // GATE_FACTOR ≈ 2.5
```

Fish that fail keep today's `FishAnimator` path untouched. Fish that pass are driven by the
simulation. Both kinds can coexist in one tank — and a hovering large fish should act as a static
obstacle that the shoal steers around, which is a nice touch for very little work.

Keep `GATE_FACTOR` a named constant. It needs playtesting; do not make it configurable yet.

**Done when:** small fish swim, large fish are visually unchanged, and both render correctly in the
same tank.

### Task 6 — Flocking

Separation, alignment and cohesion over **topological** neighbours (each fish tracks its ~6 nearest,
not everything within a radius — see review §4.2). Brute force is correct here: 25 fish is 600
distance tests, well under 10 µs. **Do not build a spatial hash.**

Then add a slow wander/school-direction noise field, which is what turns a clump into a school.

**Done when:** a dozen small fish read as a shoal — coherent, not clumped, not jittering at walls.

### Task 7 — 2.5D constraint and the turnaround

- Free movement in tank-local X (lateral) and Y (vertical).
- Damped Z drift, biased back toward the three existing `LAYER_Z` planes rather than free.
- Heading clamped to a window around broadside; **never let a fish reach fully edge-on.**
- Direction reversal is a mirror flip (invariant 5), and the last part of the flip should be fast.

Fish item models are plain `minecraft:item/generated`, which vanilla extrudes to a ~1/16-block-thick
mesh with real side faces — so an edge-on fish is a dark sliver rather than invisible. That gives you
some margin, but not much.

**Done when:** no fish ever presents as a line, and turnarounds don't read as pops.

### Task 8 — Animation coupling

Swim frequency scales with speed; bank/roll responds to turn rate. This is the cheapest large quality
gain available and it reuses the existing `FishAnimator` wave helpers.

### Task 9 — Depth sorting

**This is a real correctness bug waiting to happen.** `instances.sort(...)` at `:451` currently runs
once at build time and stays valid because positions never change. Once fish move, depth order
changes continuously — the sort must run **every extract**, or foreground fish will draw behind
background ones. Sort in place over the persistent arrays; do not allocate a comparator per frame.

---

## 5. Things that will bite you

- **`extractRenderState` runs multiple times per tick.** Invariant 1. This is the single easiest way
  to get a simulation that runs at different speeds on different machines.
- **A tank that leaves view stops being extracted.** On re-entry, if state was evicted, fish will
  jump. Mitigate by keeping state warm for a while, and by seeding cold starts from the existing
  deterministic `blockPosHash` scatter so the first frame matches today's layout.
- **`openFaces` means the box is wrong at a connected boundary.** In Phase 1, fish bounce off the
  internal wall. That is expected and acceptable. Do not try to be clever here — that is Phase 2's
  entire job.
- **Uncalibrated species.** `renderCalibration` falls back to `0.8f`, and stacks with no rolled size
  render at a flat `0.5` scale. Your gate must handle both without dividing by zero or letting an
  unmeasured species free-swim when it shouldn't.
- **Don't profile with the MCP bridge attached.** `ideas.txt` records that it causes lag buildup and
  eventual softlock. It's fine for eyeballing the result via `orbit_screenshot`; it is not fine for
  performance numbers.
- **The NeoForge gametest pre-commit hook frequently hangs.** Run commits in the background; a hang
  is not evidence your change is broken.

---

## 6. Open decisions for you to make

| Decision | Recommendation |
|---|---|
| Sim state location — registry vs. block-entity field | Registry (Task 2), but either is fine if you document the choice |
| Do `UprightFloat` species (seahorses etc.) join the sim? | **No** for Phase 1. Keep scope to `HorizontalSwim` |
| Where do the flocking weights live? | New optional fields on `SwarmConfig` — it is already the per-species render-tuning record, and adding fields there needs no new codec |
| `GATE_FACTOR` value | Start at 2.5, playtest, keep it a constant |
| Eviction timeout for cold tanks | Start generous (30 s); tune if memory shows up |

---

## 7. Definition of done for Phase 1

- [ ] A dozen small fish in one tank read as a shoal — coherent, no wall jitter, no clumping.
- [ ] Identical swim speed at 30 fps and 240 fps.
- [ ] Zero allocation in the tank submit path under a profiler.
- [ ] A 100 cm fish in a 1-block tank is visually unchanged from `master`.
- [ ] Floor-anchored species (crabs, plants, bottom-sitters) are visually unchanged.
- [ ] Walking away from a tank and back does not produce a visible jump.
- [ ] Depth order is correct as fish cross each other.
- [ ] Both loaders behave identically. No server-side diff in the changeset.

---

## 8. Phase 2 preview — design for it, don't build it

You do not need to implement any of this, but Task 2's state layout should not make it painful:

- **Domain becomes a union of AABBs**, not one box. Put the domain behind a small interface now.
- **A player-locked group**, converted with a wand-style item, with membership persisted as an anchor
  `BlockPos` + member flag and synced to clients. Note this is the point where the feature stops
  being purely client-side.
- **Anchor-renders-all**, which needs an expanded render bounding box (`getRenderBoundingBox` /
  `getViewDistance` are overridden nowhere in the mod today) and per-fish light sampling instead of
  one `state.lightCoords` per block entity. Budget this properly; it is the largest item in Phase 2.
- **Pooled member inventories** under one combined `TankCapacity` budget.
- **`interiorInset` on `FishTankShape`**, beside the existing `CornerTaperProfile`, rather than
  modelling all 18 shapes × 64 permutations of interior geometry.

Full detail in feasibility §A.7.
