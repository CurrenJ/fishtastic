# Fish Simulation in Fishtastic — Feasibility & Gameplay Fit

*Companion to [the architecture review](fish-simulation-architecture-review.md). That document
critiques the proposal on its own terms; this one asks whether it belongs in **this** mod.*

**Date:** 2026-08-20 · **Scope reviewed:** `FishTankBlockEntityRenderer`, `FishTankRenderState`,
`SwarmFishInstance`, `FishAnimator`, `SwarmConfig`, `TankCapacity`, `FishTankBlockEntity`,
`docs/feature-design-overview.md`.

**Bottom line:**

- **Technically feasible — yes**, in one form, delivered in two phases. A **client-only,
  non-authoritative flocking sim confined to tank interiors** is a few hundred lines on top of
  machinery the mod already has, and needs no entities and no server tick. Phase 1 (single tanks,
  small fish only) needs no persistent state at all; Phase 2 (player-locked multiblock aquariums)
  adds a small amount — see §2.
- **Not feasible in the form the report implies** — open-world schools of simulated fish entities.
  The cost is server-side and permanent, the payoff is ambience, and it serves none of the mod's
  loops. Recommend against.
- **Gameplay fit — strong, in exactly one slot.** The design overview names two legitimate reward
  destinations: *change how the minigame feels* and *make the tank display more impressive*. A tank
  sim is the purest possible expression of the second. There is no slot for it in the minigame, and
  attaching survival mechanics (hunger, death, water quality) to it would directly violate the mod's
  stated identity.

---

## 1. What the mod does today

The current tank "swarm" is **not a simulation**. It is deterministic decoration, rebuilt from
scratch every frame:

| Concern | Current implementation |
|---|---|
| Fish placement | `buildSwarmInstances` (`FishTankBlockEntityRenderer.java:387`) — rejection-sampled scatter, seeded from `blockPos.hashCode()`, recomputed **every** `extractRenderState` call |
| Depth | Three fixed Z planes, `LAYER_Z = {-0.25, 0, 0.25}` (`:259`) — fish never change layer |
| Volume | A ±0.35-block box, `TANK_HALF_EXTENT` (`:255`) |
| Motion | Closed-form sine stacks in `FishAnimator` — bob, surf angle, four-octave "organic wiggle." No velocity, no position integration, no state |
| Interaction | None. Fish do not see each other, the walls, the cosmetics, or the player |
| Count | Draw ceiling `SwarmConfig.DEFAULT.count = 25`; the real gate is `TankCapacity.BASE_BUDGET = 5.0` size-budget units |
| Fish geometry | Flat `item/generated` sprites rendered in `FIXED` display context, with a `mirrored` flag implemented as a 180° yaw so the pre-mirrored back face reads correctly (`FishAnimator` javadoc) |

Two consequences follow, and they shape everything below.

**(a) Adding real motion is an upgrade in kind, not a rewrite.** Every fish already has a per-frame
position offset, a rotation, a seed and a mirror flag (`SwarmFishInstance`). Replacing "recompute the
offset from a hash each frame" with "integrate the offset each tick" changes where the numbers come
from, not what consumes them.

**(b) The render state object already persists across frames.** `FishTankRenderState`'s
`chestLastBubbleSpawnTick` map is explicitly documented as *"Persists across frames — not reset in
extractRenderState"* (`FishTankRenderState.java:45`). That is exactly the storage a simulation needs,
and it is already proven in production in this codebase. **No new plumbing is required to hold
simulation state.**

---

## 2. Technical feasibility, per option

### Option A — Tank-interior flocking (client-only, non-authoritative) ✅ Recommended

#### A.0 The constraint that shapes everything: fish are too big for one block

A fish renders at `(size / 100) × renderCalibration` blocks, and an `item/generated` quad spans a
full block at scale 1.0 (`FishAnimator.PLANTED_PIVOT_Y` javadoc). So **a 100 cm fish is ~1.0 block
nose-to-tail before calibration**, while the swim volume is `TANK_HALF_EXTENT = 0.35` — 0.7 blocks of
lateral range and 0.5 of depth (`LAYER_Z`). A one-meter fish is longer than the box containing it.
It cannot translate meaningfully, it cannot turn, and a mirror-flip turnaround would be a
full-tank-width pop. **Simulating a large fish in a single tank would look worse than today's
hover, not better.**

This is geometric, not a tuning problem. Free swimming needs roughly 2.5 body lengths of run:

| Fish size | Rendered length | Run needed (≈2.5×) | Minimum tank |
|---|---|---|---|
| 20 cm | 0.2 blocks | 0.5 | fits 1×1×1 |
| 50 cm (`FishProfile.DEFAULT_MEAN_SIZE`) | 0.5 | 1.25 | 2 wide |
| 100 cm | 1.0 | 2.5 | 3–4 wide |
| 200 cm (e.g. sawfish) | 2.0 | 5.0 | 6+ wide |

Two design consequences follow, and they are the backbone of the rest of this section:

1. **Gate per fish, not per tank.** A fish free-swims if the domain's shortest usable run is
   ≥ ~2.5× its rendered length; otherwise it keeps the existing `FishAnimator` hover. One system
   with graceful degradation, not two systems. A large fish cruising slowly while small fish shoal
   around it is what real aquaria look like anyway.
2. **Big fish need big tanks — so make that the reward.** "Your 2 m sawfish needs a 6-block
   aquarium" is precisely the design overview's *make the tank display more impressive*. The size
   constraint stops being a limitation and becomes the progression hook.

**Domain.** A ±0.35-block box, or a union of such boxes for a locked multiblock group (§A.7). Every problem the
report spends §§3–8 on — voxel classification, probe fans, flood-fill escalation, spatial hashing —
**disappears**. Containment is a signed distance to an AABB. Cosmetics are a handful of static AABBs.
There are no caves, overhangs or one-block pillars inside a fish tank.

**Scale.** Realistically 5–25 fish per tank. Brute-force all-pairs neighbor search over 25 fish is
600 squared-distance tests — roughly a microsecond. **The spatial hash is unambiguously unnecessary
here**, which retires the report's §3 entirely.

**Timing.** The mod already has `ClientTickHandler`. Step the sim at a fixed 20 Hz there, store
positions and velocities on the block entity's client-side state, and have `extractRenderState`
interpolate with `partialTick` — which it already receives and already uses for `gameTimeTicks`.
This gets frame-rate independence for free and sidesteps §3.1 of the review.

**LOD, for free.** `extractRenderState` only runs for block entities the client is actually
rendering. Tick tanks in a "recently extracted" set and drop them when they go cold; a tank that
falls out of range simply stops, and on re-entry re-seeds from `blockPosHash` exactly as today. This
is the visibility gating the report's §9 omits, and here it is one `Set` and a timestamp.

**Cost to fix first.** The current hot path allocates per fish per frame — `new Random(fish.seed())`
(`:362`) and `new ItemStackRenderState()` (`:367`) inside the submit loop, plus a fresh `ArrayList`,
`Comparator` sort and rejection-sampling pass in every extract (`:387`–`:451`). At one tank this is
invisible; at a wall of 30 connected tanks × 25 fish × 120 fps it is not. Moving to persistent
primitive arrays per tank is a prerequisite for the sim and an independent win regardless.

**The genuinely hard part is the sprite constraint, not the simulation.** Fish here are flat quads.
Full 3D boids will steer them edge-on to the camera, where a fish becomes a one-pixel line. The
`mirrored` mechanism in `FishAnimator` exists precisely because a sprite only reads correctly
broadside. So the sim should be deliberately **2.5D**:

- free movement in the tank's local X (lateral) and Y (vertical);
- damped, slow drift in Z, biased back toward the three existing depth planes rather than free;
- heading constrained to a window around broadside, with a **mirror flip** — not a yaw sweep — when a
  fish reverses direction;
- the turn itself sold by the existing bank/wiggle animation rather than by actual yaw.

This is a constraint that improves the result. A shoal moving in a shallow slab with mirror-flip
turnarounds is closer to how a real aquarium reads through glass than a free 3D swarm would be.

**The turnaround needs care, and it gets *harder* in a large tank, not easier** — the fish is bigger
on screen and the whole flip is visible. One mitigating fact, verified: fish item models are plain
`minecraft:item/generated` (e.g. `models/item/largetooth_sawfish.json`), which vanilla extrudes into
a ~1/16-block-thick mesh with real generated side faces. An edge-on fish is therefore a dark sliver,
not nothing. The rule: clamp yaw so a fish never quite reaches edge-on, and take the last part of
the flip fast.

#### A.7 Connected tanks: the player-locked aquarium

`FishTankBlockEntity.updateConnections` already opens faces between adjacent tanks so a multiblock
build reads as one continuous volume — but each block entity renders and would simulate its own
occupants independently. Since §A.0 establishes that large fish *require* a multiblock domain, this
is not an optional refinement; it is where the feature's value lives.

The naive version is to elect a deterministic anchor per connected group (lowest `BlockPos`) and
re-elect on every place and break. **A player-triggered lock is better**, and not only for the
visuals:

- **It removes the hardest part.** Topology changes only at one known moment, under player intent.
  No live re-election, no mid-flight domain resize, no partial groups during a build.
- **It is opt-in, so there is no perf regression.** Every existing tank in every existing world keeps
  today's cheap decorative path. Only tanks a player deliberately converted pay simulation cost.
- **It has precedent in this codebase.** Waxed tanks already "refuse to open NEW connections on any
  face" (`FishTankBlockEntity.java:81`). Locked is a generalization of a concept the block entity
  already models.
- **It creates a gameplay artifact.** The converting item — a wand, a charter, whatever it ends up
  called — is a natural quest reward, which is exactly how this mod is supposed to hand out tank
  upgrades.

**What "locked" freezes.** The *member set*, not the silhouette. Deriving a swim volume from 18
shapes × 64 connection permutations of interior geometry is disproportionate work. Instead take the
group's inner axis-aligned box, shrunk by a per-shape inset. `FishTankShape` already carries a
`CornerTaperProfile`, so an `interiorInset` is nearly free metadata beside it. Fish may clip a
tapered corner slightly; that is acceptable and improvable later.

**Rules the lock needs.** These are the ones that bite if left undefined:

| Event | Behavior |
|---|---|
| Place/break a tank inside a locked group | Dissolve the group back to display mode first (or refuse the edit) — never mutate a live domain |
| Group dissolved | Fish redistribute into their nearest member's container. **Must not void items** |
| Anchor chunk unloaded, member visible | Sim does not run; members fall back to the static hover path |
| Group spans chunk borders | Fine, but the render AABB (below) must too |

**Pooled inventory and capacity.** A locked group should pool its members' containers and run one
combined `TankCapacity` budget (`BASE_BUDGET` × member count is the obvious start), with a separate
draw-count ceiling for render cost. This is what lets a large aquarium hold both a real shoal and a
couple of large specimens.

**The real engineering cost is rendering across the group, not the simulation.** Anchor-renders-all
needs two things the mod does not currently do:

- **An expanded render bounding box.** `getRenderBoundingBox` / `getViewDistance` are overridden
  nowhere in the codebase today (verified). Without it, every fish in the group is frustum-culled the
  moment the anchor block leaves the screen.
- **Per-fish light sampling.** `submit` currently passes one `state.lightCoords` for the whole block
  entity; a fish twelve blocks away in a dim corner would be lit by the anchor's value.

**Correction to an earlier claim.** The companion review and the first draft of this document said
the sim needs no NBT and no server changes. **With the lock model that is no longer true.** Group
membership — an anchor `BlockPos` plus a member flag per tank — is persistent world state that must
save and sync to clients. It is small, but the "purely client-side" framing does not survive the
lock, and Phase 2 should be planned with that in mind. The *simulation itself* remains client-only
and non-authoritative; only the domain definition becomes server state.

#### A.8 Estimate, by phase

**Phase 1 — size-gated free-swim in single tanks.** ~300–450 lines of new client code: a
`TankFlockSimulation` holding parallel float arrays, an AABB domain/containment helper, a
`ClientTickHandler` hook, and an interpolating read in `extractRenderState`; plus the refactor of the
existing swarm build/submit path to stop allocating per frame. Small fish shoal; anything failing the
§A.0 gate keeps today's hover. **No new blocks, no new items, no NBT, no packets, no server code.**
`FishProfile` would want a few optional tuning fields beside the existing `SwarmConfig`.

**Phase 2 — the locked multiblock aquarium.** The wand item and its recipe/quest reward, locked-group
membership as persisted + synced block-entity state, group discovery and dissolution, pooled
inventory and combined capacity, the expanded render AABB, and per-fish light sampling. Materially
larger than Phase 1 and touching both sides of the client/server line, but every part of it is
conventional block-entity work — nothing here is novel or risky in the way the simulation itself is.

Phase 1 exists to answer the only question that actually matters before committing to Phase 2:
**does the sim look good?** Its gating rule is a strict subset of Phase 2's, so nothing built in
Phase 1 is thrown away.

### Option B — Ambient schools in open water ⚠️ Possible, not advisable

Technically doable as a **client-only particle-like system** (never entities): spawn a few schools
near the player in water, simulate on the client, despawn on distance. This is where the report's
voxel-avoidance work actually earns its keep, and where the cached-occupancy-bitset revision from the
review becomes mandatory rather than optional.

But it is a large, permanent client cost buying pure ambience, in a mod whose stated principle is
that *"nothing in this loop routes the player away from fishing."* Ambient schools are visible
mostly while swimming, which is not where the mod wants attention. **Recommend against, unless it is
later scoped tightly as "schools visible around an active bobber"** — which at least touches the
minigame.

### Option C — Simulated fish as real entities ❌ Recommend against

This is what the report's §§6–9 implicitly describe, and it is the wrong project for this mod:

- Server tick cost that scales with world population, paid by every player on a server forever.
- Entity/mob-cap interaction, chunk-lifecycle handling, persistence, and position sync bandwidth.
- Fish would become killable, pushable, despawnable, and dupe-able — a large new surface of bugs.
- Most importantly it buys nothing the mod wants. The mod's fish are *caught through a minigame and
  displayed*; they are not creatures the player hunts in the world.

---

## 3. Gameplay fit

### 3.1 Where it fits: the tank, as prestige

`docs/feature-design-overview.md` states the loop and its acceptance test directly:

> *Every reward either changes how the minigame feels or makes the tank display more impressive.*

A living tank is the strongest possible instance of the second half. It also fixes a real weakness:
the tank is currently the mod's terminal reward, and terminal rewards need to keep giving. A static
diorama is looked at once. A tank where the shoal drifts, tightens, scatters and reforms is looked at
repeatedly — which is what makes the fishing that filled it feel worthwhile.

It composes cleanly with what already exists:

| Existing system | How the sim uses it — no new content pipeline needed |
|---|---|
| `FishProfile` temperament / zone | Bottom-zone species hug the sand; midwater species shoal; predators get larger separation and make same-tank shoals scatter |
| `SwarmConfig` | Already the per-species tuning record. Add flocking weights beside `xz_spread` / `y_range` |
| `TankCapacity` size budget | Density already varies by fish size, so crowding behavior varies for free |
| Rolled fish size | Larger individuals swim slower with a wider turn radius — a per-catch difference the player can see |
| Cosmetics on the tank floor | Static AABBs to swim around; a reason to buy them beyond looks |
| Tank shapes (18 shipped) | The 2.5D domain is derived from the shape, so shape choice visibly changes how the shoal moves |
| Tank-composition quests (`tank_snapshot`) | Could gain live-behavior objectives — "keep a shoal of 12+ of one species" — reusing the existing state-check design |

### 3.2 Where it does **not** fit

- **The minigame.** It is a reflex bar. There is no room for flocking, and no player attention to
  spare.
- **As a maintenance system.** Feeding schedules, hunger, water quality, fish dying — this is the
  obvious "make the sim matter" instinct and it is directly against the mod's identity. The design
  overview rejects *"crafting chains that consume fish"* and *"making catching fish feel like a
  chore."* Fish starving in a tank while the player is away is the single fastest way to turn the
  mod's trophy case into an obligation. **The sim should be zero-maintenance ambience, full stop.**
- **As a gate.** Nothing should require the sim to be running. It is a visual upgrade to an existing
  display, not a new subsystem players must engage with.

### 3.3 The one optional interaction worth considering

If an interaction is wanted at all, the minimal one that stays in-identity: **right-clicking the
tank with fish food scatters a brief feeding response** — the shoal converges, tightens, disperses
over a few seconds. Purely cosmetic, no state, no timer, no penalty for never doing it. That is the
report's "Feed" school mode (§6) reduced to something that costs nothing and cannot become a chore.

---

## 4. Recommendation

Adopt the revised sequence from the review, scoped to product shape (A) — tank-interior, client-only,
non-authoritative — and deliver it in two phases split by the size gate of §A.0.

### Phase 1 — size-gated free-swim in a single tank

1. **Allocation cleanup first** (`FishTankBlockEntityRenderer.java:335`–`:451`). Required for the
   sim, and worth doing on its own merits.
2. **Build in this order**, matching §5 of the review: fixed-timestep integration → AABB containment
   with hard/soft arbitration → topological separation/alignment/cohesion → wander → animation
   coupling from speed and turn rate. Stop and look at it after each step; steps 3 and 5 are where
   the quality actually appears.
3. **Constrain to 2.5D from the start.** Do not build free 3D boids and then try to hide the sprite
   problem afterwards.
4. **Implement the per-fish size gate**, not a per-tank mode switch. Anything failing it keeps
   today's `FishAnimator` hover — which in a 1-block tank is every fish over ~30 cm.
5. **Put the domain behind a flock-domain abstraction** that is a single AABB now and a union of
   AABBs in Phase 2.

Phase 1's purpose is to answer *does this look good?* before any of Phase 2 is committed to.

### Phase 2 — the player-locked multiblock aquarium

6. **Add the converting item and the lock**, per §A.7: persisted + synced group membership, explicit
   dissolve-on-edit, no live topology mutation.
7. **Pool member inventories** into one combined `TankCapacity` budget with a separate draw ceiling.
8. **Do the rendering work**: expanded render bounding box and per-fish light sampling. Budget this
   properly — it is the largest single item in the phase.
9. **Derive the domain as an inset inner box**, adding `interiorInset` to `FishTankShape` rather than
   modelling all shape × permutation interiors.
10. **Let large fish unlock as the tank grows**, and surface that in the UI — a player should be able
    to see that their sawfish needs a longer aquarium.

### Regardless of phase

11. **Defer everything else in the report** — spatial hashing, voxel probes, flood-fill, school
    modes, GPU instancing — until profiling or a specific gameplay hook asks for it. In a fish tank,
    most of it never will.
12. **No maintenance mechanics.** No hunger, no water quality, no fish death. See §3.2.
13. **Do not** pursue open-world schools or fish entities as part of this work.

---

*Next step:* [Implementation handoff](../fish-simulation-handoff.md) — orientation, Phase 1 task
breakdown, invariants, and definition of done.
