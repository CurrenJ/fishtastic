# Simulating Every Fish — Locomotion Classes for the Flock Engine

**Written:** 2026-09-10 · **Scope:** `:fishsim` engine + `TankFlockAdapter` +
`FishAnimationConfig`. Companion to [`fish-sim-engine-plan.md`](fish-sim-engine-plan.md) (module
layout, verification model) and [`fish-swarm-realism.md`](fish-swarm-realism.md) (planar model
behaviour); [`fish-sim-locomotion-handoff.md`](fish-sim-locomotion-handoff.md) is the cold-start
context for picking the work back up. Both documents' invariants remain binding — in particular the binary 2.5D single-tank
model (`Tunables.DEFAULT`, `FlockEngine.stepFish`) is bitwise-locked by `GoldenTrajectoryTest` /
`ParityTest` and **nothing here may perturb it**.

This started as a research + spec document: what was broken, why the abstraction could not express
the fix, what the replacement was, and in what order to build it. **All of it is now built** —
Phases 0 through 4 and the cross-cutting squash-and-stretch, landed 2026-09-10, each with its
result logged in §5. The spec sections above that log describe the design as shipped; where the
implementation departed from the spec, the phase log says so and why. What is left is in §7 of the
handoff: the in-game look at Phase 4 and §3.6's amplitudes, the mixed-class harness scenario, and
obstructing the *water* with cosmetics rather than only the floor.

---

## 1. Where things stand

### 1.1 One boolean decides whether a creature is alive

`TankFlockAdapter.canSwim`:

```java
private static boolean canSwim(FishAnimationConfig anim) {
    return !FishTankBlockEntityRenderer.isFloorAnchored(anim)
            && anim instanceof FishAnimationConfig.HorizontalSwim;
}
```

That boolean becomes `FishSpec.canSwim`, and `FlockEngine.step` opens with
`if (!swimmers[i]) continue;`. Everything that is not a `horizontal_swim` fish is therefore
**never stepped at all**. It holds the position the rebuild scattered it to, forever, and is
animated purely open-loop by `FishAnimator` against `gameTimeTicks`.

Five of the six animation modes fall on the dead side of that line:

| Mode | Profiles today | Engine state |
|---|---|---|
| `horizontal_swim` | the default — 43 of 60 profiles carry no `animation` block at all, plus 4 explicit | simulated |
| `upright_sit` | `common_octopus`, `japanese_spider_crab`, `trapania_scurra`, `willans_chromodoris` | frozen |
| `upright_float` | `flapjack_octopus`, `glass_squid`, `portuguese_man_o_war` | frozen |
| `belly_down` | `acute_iaspis`, `giant_manta_ray`, `frozen_giant_manta_ray` | frozen |
| `planted` | `garden_eel`, `royal_garden_eel` | frozen |
| `floor_sit` | `starfish` | frozen |

Thirteen species — including three of the mod's most spectacular catches (the two manta rays and
the man o' war) — render as static props with a sine wobble.

### 1.2 The abstraction conflates two orthogonal things

`FishAnimationConfig.mode` currently answers two unrelated questions at once:

* **Pose** — which way the sprite is oriented, what its idle wobble looks like, whether its
  texture needs the 45° diagonal correction.
* **Locomotion** — how the creature moves through the tank volume, if at all.

They are genuinely independent. A flounder is a `horizontal_swim` *pose* with benthic
*locomotion*. A garden eel is an upright pose anchored to a fixed footprint. Because one enum
carries both, "make jellyfish drift" is currently unexpressible without inventing a seventh mode
that duplicates `upright_float`'s pose code — and that is the fork the mode list has been quietly
heading toward.

### 1.3 Consequences beyond "they don't move"

1. **Group aquariums fall apart.** In multi-tank mode only free swimmers join the anchor's
   `VoxelDomain` engine; everything else stays on the per-member hover path
   (`TankFlockAdapter.rebuildGroupMode`). Build a 3×3 wall of tanks, stock it with jellyfish, and
   you get nine visibly separate boxes of frozen jellyfish inside one continuous aquarium. This is
   the most damaging symptom, because the group feature is what the tank-shape and scaling work
   was all for.
2. **Floor creatures overlap.** The rebuild scatter (`sampleInDomain`) samples uniformly in 3D and
   rejection-tests min separation **in 3D**; the renderer then collapses floor-anchored fish to
   `COSMETIC_FLOOR_Y` (`computeBaseY`). Two crabs separated only in Y land on the same patch of
   sand. The separation test never sees the position the player sees.
3. **The size gate asks the wrong question.** `swimmers[i] = canSwim && domain.sizeGateRun() >=
   gateFactor * length` measures the longest horizontal *run*. That is the right gate for a
   swimmer and the wrong one for everything else — a spider crab needs floor *footprint*, a
   jellyfish needs vertical *extent*. Today the gate is simply skipped for them, so a manta ray
   that fails the swim gate and a starfish that could never pass it end up on the same accidental
   code path.
4. **Non-swimmers are inert obstacles.** They do feed the separation term as static obstacles, but
   only inside their own tank's local engine. In group mode they are invisible to the group
   engine, so a shoal swims straight through a hovering man o' war.

---

## 2. The proposal: split pose from locomotion

Introduce a **locomotion class** as a first-class engine concept, orthogonal to the render mode.
`FishAnimationConfig` keeps doing exactly what it does today (pose + idle wobble); a new
`LocomotionConfig` says how the creature moves. The engine learns the second and stays ignorant of
the first, exactly as `fish-sim-engine-plan.md` §1 requires.

### 2.1 The classes

| Class | Motion model | Fits |
|---|---|---|
| `FREE_SWIM` | today's flocking, unchanged in every respect | most fish |
| `GLIDE` | free-swim with a hard turn-rate cap, wide separation, weak cohesion, a mild floor-hugging bias and a slow vertical sine | mantas, sawfish, large solitary fish |
| `DRIFT` | near-zero forward drive; pulse-and-sink vertical cycle, horizontal motion is advection-like OU wander only; no alignment, weak same-species cohesion | jellyfish, man o' war, glass squid |
| `BENTHIC` | 2D walk constrained to the floor surface: yaw-limited crawl, floor-footprint separation, stop-and-turn dwell cycle | crabs, octopus, nudibranchs, starfish |
| `ANCHORED` | fixed footprint chosen at rebuild; sway only, plus a retract/emerge reaction to a large neighbour passing | garden eels |
| `STATIC` | today's frozen-hover behaviour | the explicit fallback, and the gate-failure state |

Default mapping, so **no existing data file changes**:

```
horizontal_swim → FREE_SWIM     belly_down   → GLIDE      upright_float → DRIFT
floor_sit       → BENTHIC       upright_sit  → BENTHIC    planted       → ANCHORED
```

A profile may override the class explicitly, which is what makes the split pay for itself: a
flounder is `horizontal_swim` + `BENTHIC`, a hovering seahorse is `horizontal_swim` + `DRIFT`,
with no new pose code.

### 2.2 What the engine gains

`FishSpec.canSwim` (a boolean) becomes `FishSpec.locomotion` (the enum) plus a small
`LocomotionParams` record of per-species scalar overrides (speed scale, turn rate, vertical band,
school affinity, wall shyness). `FlockEngine.step` dispatches:

```java
for (int i = 0; i < count; i++) {
    switch (locomotion[i]) {
        case FREE_SWIM -> { if (planar) stepFishPlanar(i); else stepFish(i); }   // untouched
        case GLIDE     -> stepFishPlanar(i);                                     // params only
        case DRIFT     -> stepDrift(i);
        case BENTHIC   -> stepBenthic(i);
        case ANCHORED  -> stepAnchored(i);
        case STATIC    -> { }                                                    // today's `continue`
    }
    tailPhase[i] += speedFactor(i);
}
```

**Parity is preserved by construction:** `FREE_SWIM` reaches byte-identical code and `STATIC` is
the existing `continue`. The golden/parity suite must stay green with no expected-value edits —
that is the acceptance gate for Phase 0, and if a single golden needs updating, the refactor is
wrong.

Everything the new classes need already exists in the engine and should be reused rather than
reinvented: the neighbour grid, species-scoped separation, OU wander, trait jitter, the burst
envelope, distance-field wall avoidance, `interpolate`, and the hard backstop.

### 2.3 The one genuinely new engine capability: a floor

`BENTHIC` and `ANCHORED` need to know where the sand is, in group space as well as in a single
tank. `FlockDomain` gains:

```java
/** Height of the walkable floor at this horizontal position, or NaN if the column has no floor. */
float floorHeight(float l, float d);
/** The walkable floor area, in blocks² — the size gate for benthic creatures. */
float floorArea();
```

* `Box`: `floorHeight` is `minVertical()` everywhere; `floorArea` is the lateral×depth interior.
* `VoxelDomain`: the lowest occupied cell in each column, precomputed once alongside the distance
  field. Two properties matter. First, it is a **2D field, not 3D** — cheap, and it falls out of
  the pass `DistanceField` already walks. Second, it must live on the shared per-group entry in
  `ClientTankGroups`, keyed by the membership epoch exactly like the distance field, or we
  re-introduce the quadratic rebuild freeze that `c08eca8` fixed.

A column with no floor (an overhang in an L-shaped group) returns NaN, and the benthic walk treats
it as a wall — crabs walk the sand of the whole group and never step into space.

### 2.4 Per-class size gates

Replace the single gate with a per-class predicate, evaluated in `initFish` where the current one
lives:

| Class | Gate | On failure |
|---|---|---|
| `FREE_SWIM`, `GLIDE` | `sizeGateRun() >= gateFactor * length` (today's rule) | → `STATIC` |
| `BENTHIC` | `floorArea() >= footprintFactor * length²` | → `STATIC` |
| `DRIFT` | vertical extent `>= driftFactor * length` | → `STATIC` |
| `ANCHORED` | a free floor footprint exists | → `STATIC` |

Demoting to `STATIC` rather than to a separate "gate-failed" flag is deliberate: it collapses the
current accidental overlap between "too big to swim" and "does not swim" into one explicit state —
and it is the state the renderer already knows how to draw.

---

## 3. Motion models, in detail

### 3.1 `BENTHIC` — the biggest visual win

Eight of the thirteen frozen species are benthic. The model is a 2D version of the planar walker
already shipped, which is why it should come first despite needing the floor field.

* Position is `(lateral, depth)`; `y` is `floorHeight(l, d)` plus a small per-species offset — the
  engine, not the renderer, owns the Y. `computeBaseY`'s three special cases collapse into "engine
  Y + the existing centre-pivot compensation".
* Speed is an order of magnitude below cruise, with a **dwell cycle**: reuse the burst envelope
  with a long period and a low duty, so a crab scuttles, stops, turns, scuttles.
* Yaw is continuous with a low turn rate (crawlers pivot in place; they do not bank).
* Separation is 2D and uses a **footprint radius** derived from `length`, so crabs stop landing on
  top of each other — this alone fixes §1.3.2.
* Wall avoidance is the existing distance field sampled at the fish's own height, plus the NaN
  columns from §2.3.
* Free swimmers should treat benthic creatures as obstacles; they already feed local separation,
  and §3.5 puts them into the group engine where they currently do not exist.

### 3.2 `DRIFT` — jellyfish

* Forward drive ≈ 0. Horizontal position moves only by OU wander at low sigma and a long
  correlation time, which reads as being carried by a current.
* Vertical is the interesting axis and the one the current model damps hardest: a **pulse-and-sink
  cycle** — a short upward impulse, then a slow passive sink, phase-jittered per fish. This is the
  burst envelope again, reused on the vertical axis, and the existing asymmetric
  `BURST_ATTACK_RATE` / `BURST_DECAY_RATE` split is already the right shape for it.
* No alignment (jellies do not school); same-species cohesion weak enough to produce loose smacks
  rather than a ball.
* The `spin_rate` in the `upright_float` pose stays exactly where it is — it is pose, not motion.

### 3.3 `GLIDE` — rays

Mostly a parameter set over `stepFishPlanar`, which is why it is cheap and late in the order:
`turnRateDegPerTick` well below the shoal's, `separationRadiusOther` well above it, alignment and
cohesion near zero (rays are solitary), plus a floor-hugging vertical bias and a long-period
vertical sine. The `belly_down` pose's `bank_amplitude` should become **driven by the engine's
`bank[i]`** the way `applySwimming` already drives horizontal swimmers, instead of an open-loop
sine — a ray banking into its own turns is most of the effect.

### 3.4 `ANCHORED` — garden eels

The cheapest charm on the list. The footprint is fixed at rebuild (chosen by 2D floor scatter, so
a colony spaces itself properly). The only motion is a **retract/emerge** state driven by a
neighbour query the engine already performs: when a fish above a size threshold passes within a
radius, the eel retracts into the sand over ~0.3 s and re-emerges a couple of seconds later. One
float of state, and it makes an eel colony read as alive.

### 3.5 Cross-cutting: everything joins the group

`rebuildGroupMode`'s split becomes "join the group engine unless `STATIC`". Non-swimmers get group
coordinates, group separation, and the group's floor. The per-tank quota
(`TankGroups.perTankFishQuota`) then needs applying per class — a tank of crabs and a tank of
tetras should not compete for the same budget line, since the binding cost for benthic creatures
is floor area, not the fish budget.

### 3.6 Cross-cutting: squash-and-stretch on the locomotion drive

Not a motion model — a **pose** that reads an engine signal, the same shape as `applyBenthic`
already taking the engine's yaw. The engine stays ignorant of rendering; the renderer gains a
number it did not have.

The idea: when a creature *does* its move — a jellyfish contracting its bell, a crab pushing off
into a scuttle — it briefly deforms. Squeeze horizontally, stretch vertically, relax. It is the
cheapest "juice" available here because **the tween already exists**: `FlockEngine.burstDrive[i]`
is an integrated, continuous 0→1 envelope with asymmetric attack and decay, and both classes
already carry one — it is the drift pulse in `stepDrift` and the scuttle-and-pause in
`stepBenthic`. No new timing code, no new cadence to author.

Deliberately **not** for `FREE_SWIM`, which also has a `burstDrive`: `horizontal_swim` already has
a tail wiggle and a bank driven by the same motion, and a third deformation on top would fight
both. This is for the classes whose poses are otherwise nearly still.

#### What it needs, in order

**a. Interpolate the drive to render time.** `burstDrive` is a tick value and everything the
animator reads is interpolated (`renderPhase`, `renderYaw`); a raw tick value strobes at 20 Hz
against the frame rate and reads as a stutter rather than a squeeze. Mirror `tailPhase` exactly:

* `float[] prevBurstDrive`, copied in `step()` in the same `System.arraycopy` block as
  `prevTailPhase` — *before* the per-fish loop, or the first fish stepped poisons the copy.
* `public float[] renderDrive`, filled in `interpolate()` with
  `SimMath.lerp(partialTick, prevBurstDrive[i], burstDrive[i])`, sized in `allocate()`.
* Rebuild carry needs nothing new: `cBurstDrive` already exists and is already restored, so a
  carried fish keeps its envelope across a rebuild
  (`RebuildCarryTest.aCarriedDrifterKeepsItsPulseCycle` covers the state; the render mirror
  follows it for free).

**b. Give the shape its own envelope — the drive is shaped for velocity, not for silhouette.**
This is the one genuine design decision and the easiest thing to get wrong. Drift's drive rises in
~0.8 s and decays over ~3.7 s, because that is what makes the *motion* right: a quick push and a
long coast. Map scale straight onto it and the bell snaps in and then takes four seconds to refill,
which is not what a jellyfish does — real ones recover quickly and then simply hang. So the squash
wants a **second envelope off the same trigger**, sharing the attack and relaxing far faster
(order of `DRIFT_PULSE_DECAY_RATE` 0.9 → ~3.0/s; tune by eye, not by metric).

Put that second envelope in the **engine**, not the renderer, despite it being purely cosmetic:
`tailPhase` is the precedent — it is equally cosmetic and lives in the engine because that is where
it can be integrated at a fixed 20 Hz, interpolated, carried across rebuilds and tested headlessly.
A render-side follower would be re-derived per frame at a variable rate and none of that would
hold. Suggested shape: `shapeDrive[i]` + `prevShapeDrive[i]` + `renderShape[i]`, advanced next to
`burstDrive` in both `stepDrift` and `stepBenthic`, with its own `*_SHAPE_ATTACK_RATE` /
`*_SHAPE_DECAY_RATE` constants alongside the existing per-model ones.

This is the burst-"hop" lesson pointed the other way (see `fish-swarm-realism.md` §4): the envelope
that makes the movement read right is not automatically the one that makes the silhouette read
right.

**c. Apply the scale in the creature's own frame — after the roll.** `applyUprightFloat` and
`applyUprightSit` end with a −45° `Axis.ZP` roll for diagonal textures. A scale applied *before*
that roll deforms along the item canvas's axes, so the squeeze comes out diagonal: invisible in
review, unmistakable in game. The scale goes **last**, after the roll.

**d. Pivot about the right point, which differs by class.**

* **`DRIFT` (jellyfish)** — suspended, so scaling about the item's centre is correct and needs no
  sandwich at all.
* **`BENTHIC` (crawlers)** — scaling about the centre makes a crab sink into the sand on the squash
  and float above it on the stretch. It must scale about its **floor contact point**:
  `translate(0, −pivotFraction × scale, 0)`, scale, `translate(0, +pivotFraction × scale, 0)`.
  `UprightSit.pivotFraction` is already measured per species by
  `tools/fish-render-calibration.ps1` (Phase 1), so the number exists — but note
  **`applyUprightSit` does not currently take `scale`** and will need it, because the animator runs
  *before* the renderer's `poseStack.scale(scale, scale, scale)` and its translates are therefore
  in block units while `pivotFraction` is a fraction of the item.
  `FishAnimator.floorPoseLift` already does this multiplication (`us.pivotFraction() * scale`) and
  is the reference for getting the units right; `FishAnimatorFloorLiftTest` pins those numbers.

**e. Keep it volume-preserving and small.** `sy = 1 + a·d`, `sx = sz = 1/√sy`, where `d` is the
interpolated shape drive. Volume preservation is what makes it read as *flexing* rather than as the
fish changing size — which matters more here than in a typical game, because `render_calibration`
exists specifically to render these species true-to-scale. Start around `a ≈ 0.10` for a jellyfish
bell and lower for a crab, whose deformation should be a hint rather than a pump.

#### Authoring surface

Optional codec fields with non-zero defaults, so the effect ships without touching any of the 60
`fish_profile` files and a species that looks wrong can opt down:

* `FishAnimationConfig.UprightFloat` — `pulse_squash` (bell contraction).
* `FishAnimationConfig.UprightSit` and `FloorSit` — `scuttle_squash` (push-off).

#### Verification

Headless, because all of it except the amplitude is plumbing:

* `:fishsim` — `renderDrive` / `renderShape` stay in [0, 1]; the shape envelope's decay is strictly
  faster than the drive's; the interpolated value at `partialTick` 0 and 1 matches the two tick
  values exactly.
* `:common` — the volume factor stays within a hair of 1 across the whole envelope; **the crawler's
  contact point does not move** through a full squash cycle (the test that would have caught the
  Phase 1 floor-lift bug, in a different guise); amplitude is bounded by the configured value.

The amplitude itself is an eye call and has no headless acceptance, exactly like the `CRAWL_*` and
`DRIFT_*` constants.

#### Why it is worth building before Phase 4

`ANCHORED`'s retract/emerge (§3.4) **is** a scale animation — an eel withdrawing into the sand is
this machinery with a different trigger and a much larger amplitude. Building the
drive → pose → pivot path for `DRIFT` and `BENTHIC` first means Phase 4 is a trigger and a
constant rather than a new system. `GLIDE` could later drive a wing flex off `bank[i]` through the
same path.

---

## 4. Open questions for you

1. ~~**Cosmetic structures are not in the occupancy grid.**~~ **Answered: fold them in.** Done in
   Phase 1, at floor level — cosmetics block floor cells and crawlers walk around them. The
   remaining half is the water: swimmers still pass through cosmetics, because obstructing the
   volume needs a sub-block `DistanceField`, which is a much larger change. Open.
2. ~~**Should drifters traverse a group?**~~ **Answered: yes, drifters move.** "Jellies stay
   put" was never a design position — it is a description of the pre-simulation renderer, which
   had no way to move anything. The engine model shipped in Phase 2; letting drifters into the
   *group* engine landed in Phase 2b.
3. **How far do we go on per-species tuning?** The class defaults will carry all 13 species. A
   `locomotion` block on `fish_profile` lets any species deviate, but every knob is a knob to
   balance. Recommendation: ship the classes with no per-species overrides at all, and add the
   block only when a specific fish demonstrably needs it.

---

## 5. Suggested order

| Phase | Content | Risk | Payoff |
|---|---|---|---|
| **0** ✅ | Split locomotion from pose: `FishSpec.locomotion`, the `switch`, the default mapping, per-class gates. **Zero behaviour change** — parity suite green with no golden edits. | low | none visible; unblocks everything |
| **1** ✅ | `FlockDomain.floor()` + `BENTHIC`, with cosmetics as terrain. 8 species, and it fixes the floor-overlap bug. | medium (new field, group caching) | highest |
| **2** ✅ | `DRIFT`. 3 species, reuses the burst envelope on a new axis. Group split landed as 2b. | low | high |
| **3** ✅ | `GLIDE` + engine-driven bank on `belly_down`. 3 species, mostly tuning. | low | medium |
| **4** ✅ | `ANCHORED` retract/emerge. 2 species. | low | medium, and cheap |

Plus one cross-cutting item with no phase of its own: **squash-and-stretch on the locomotion
drive** (§3.6) — landed 2026-09-10, between Phase 3 and Phase 4 as planned. It is pose work rather
than a motion model, it applies to `DRIFT` and `BENTHIC` today, and it builds the machinery Phase
4's retract needs.

### Phase 0 — landed 2026-09-10

* `Locomotion` (`:fishsim` core) — the six classes, with `simulated()` naming the one that has a
  motion model today.
* `FishSpec.canSwim` → `FishSpec.locomotion`. The boolean is gone from the engine's vocabulary
  entirely; `FlockEngine.swimmers[]` survives as a *derived* view (`locomotion[i] == FREE_SWIM`)
  because every consumer — renderer, metrics, harness, viewer — reads it, and none of them needed
  to change.
* `FlockEngine.step` dispatches on the effective class. `FREE_SWIM` reaches the identical
  instructions; the other five yield `false` and skip the tail-beat clock, which is exactly the
  old `if (!swimmers[i]) continue;`.
* `FlockEngine.gate` — one gate per class. Only the swim gate exists (unchanged, and shared by
  `GLIDE`), and it now demotes to `STATIC` rather than to a second implicit state.
* `TankFlockAdapter.locomotionOf` — the default pose → class mapping from §2.1, kept in the
  adapter so it stays the only class that knows both worlds. `joinsGroupSwim` marks the one spot
  §3.5 has to widen.
* `LocomotionTest` — five tests pinning what the goldens can't see: unsimulated classes never move
  (bitwise, with a live swimmer alongside as the control) and never advance the animation clock;
  `swimmers[]` is exactly the derived view; the swim gate demotes to `STATIC`; ungated classes
  survive both domains.

Verification: `:fishsim:test` — 110 tests green, **no golden or parity expected-value edits**,
which was the phase's whole acceptance condition. Both loaders compile.

One deliberate hole to close in Phase 1: group-mode hover specs are handed `STATIC` rather than
their true class, because that list mixes genuine non-swimmers with free swimmers pushed out by
the group's gate or quota, and the latter must not start swimming inside their own member tank.
Phase 1 rewrites that policy anyway.

### Phase 1 — landed 2026-09-10

Shipped with §4.1 answered "fold them in", so cosmetics are terrain from the start.

* **`FloorField`** (`:fishsim` domain) — a 2D field at 3×3 cells per block, holding each cell's
  walkable surface height or NaN. The resolution is not arbitrary: it is exactly the tank's own
  cosmetic grid, so a cosmetic cell *is* a floor cell and `CosmeticGridCell.packed()` is the
  single-tank mask index, with no resampling anywhere. `FlockDomain.floor()` exposes it; a `Box`
  synthesises an open one when the caller has nothing to say, and `VoxelDomain` derives each block
  column's floor as the lowest occupied cell whose downward neighbour is empty — which is exactly
  "the tanks that have sand in them".
* **`FlockEngine.stepBenthic`** — an overdamped 2D walk: scuttle-and-pause on an integrated
  envelope, OU wander on the heading, probe-ahead obstacle steering, 2D footprint separation, and
  a hard "test the move against the floor before taking it" rule that makes *never inside an
  obstacle* structural rather than emergent. Vertical is not simulated: a crawler's Y **is** the
  floor under it, so it steps up between tanks of different heights for free and the domain's
  vertical clamp (which describes the swim volume, well above the sand) never applies.
* **Placement draws from the fish's own seed**, not the shared scatter RNG. Adding a crab to a
  tank therefore shifts nothing: every other fish still scatters to bit-identical positions, which
  `BenthicTest.addingACrawlerDoesNotDisturbTheSwimmers` pins.
* **The floor is indexed in the block's frame, not the sim's.** A single tank's local axes are
  rotated by the placement yaw it recorded from the player; its sand and cosmetics are not. The
  engine rotates local coordinates into the block frame for every floor lookup —
  `obstaclesHoldUnderTheTanksPlacementRotation` covers five rotations.
* **The benthic gate measures floor area** (`4 × length²`), not the swimmers' straight run, and
  cosmetics take area away — fill enough of a tank and a big crab is demoted to `STATIC`. A
  demoted crawler still sits *on the sand*, because placement is on the floor regardless of the
  gate; that is what lets the renderer stop pinning the pose's Y.
* **Crawlers join the group.** `rebuildGroupMode` now hands them to the anchor's engine alongside
  free swimmers, with a **separate quota counter** — swimmers and crawlers compete for different
  resources (water volume against floor area), so a tank full of one should not evict the other.
  Overflow crawlers stay home and walk their own tank's floor rather than freezing.
* **Cosmetic changes rebuild only the floor.** They move without membership moving, so they are
  watched by fingerprint (nine cells per tank) and `VoxelDomain.rebuildFloor` recomputes the 2D
  pass in place — rebuilding the whole domain would re-incur the distance-field cost §5.3a of
  fish-tank-group-scaling.md exists to avoid. A crawler standing where a cosmetic just landed
  re-places; it is the one case carry-over cannot win.
* **`FishAnimator.floorPoseLift`** — how far a floor pose sits above whatever floor height the
  engine reports: its own nudge, plus the centre-pivot compensation an upright item needs to stand
  on its base rather than its middle. It lives with the pose because every draw path needs it.
  *(Fixed after first in-game look: it was inlined in the single-tank path's `computeBaseY`, so
  when crawlers started rendering in group space the group loop translated by the engine's floor
  height alone and upright creatures — nudibranchs, crabs — sat buried to their midpoints in the
  sand, while a lone tank looked right. `FishAnimatorFloorLiftTest` pins the numbers both callers
  now share.)*
* **`UprightSit.pivotFraction`** — how far below the item's centre the art's lowest *visible*
  pixel sits, measured from the texture's alpha by `tools/fish-render-calibration.ps1` (which also
  handles the 45° roll, where the lowest point is a rotated corner rather than the bottom edge).
  *(Pre-existing bug, fixed here: the lift assumed half the item, i.e. that the art reached the
  bottom of its canvas. `trapania_scurra` fills half its canvas vertically, so it hovered by
  ~0.28 × its render scale — roughly its own visible height, and unmissable. Measured values:
  trapania 0.19, spider crab 0.34, octopus and willans 0.41. Species nobody has measured keep the
  old 0.5, so nothing else moves.)*
* **Renderer.** `computeBaseY`'s `FloorSit`/`UprightSit` cases no longer pin `COSMETIC_FLOOR_Y` —
  they contribute the baseline and the engine's Y carries the floor, which is what makes a
  group's varying floor heights work at all. `FishAnimator.applyBenthic` takes the engine's yaw so
  a crawler faces where it is walking, while its idle sway stays on game time (it is the creature
  fidgeting, not a function of travel speed). `Planted` is untouched — it is `ANCHORED`, Phase 4.

**Verification.** `:fishsim` 122 passing (goldens and parity again with **no expected-value
edits**), of which 13 are `BenthicTest`; `:common` 29 passing, including `TankFloorsTest` on the
cosmetic-grid → floor-grid mapping — worth its own test because every cheaper check passes when it
is wrong — and `FishAnimatorFloorLiftTest` on the floor lift. `BenthicProbe` renders the walk from above with each crawler's path drawn over
the floor — crawlers route around cosmetics and through the corridor left between them.

**Not done, and visible in that probe: swimmers still pass through cosmetics.** Only the *floor*
is obstructed. Blocking the water would mean giving `DistanceField` sub-block resolution, which is
a redesign of the part of the domain the group-scaling work is holding up, and it is a separate
decision from this one.

Tuning constants (`CRAWL_SPEED`, `CRAWL_DWELL_SECONDS`, and the rest) are deliberately engine
constants rather than `Tunables`, following `PLANAR_TURN_RATE` and the burst envelope rates: they
are internal to one motion model, and `DEFAULT`/`GROUP` stay untouched, so the parity lock cannot
be reached from them at all. They graduate to `Tunables` when the viewer needs sliders.

**In-game acceptance: passed** (2026-09-10, together with Phase 2, Tier 2 and the 512 cap). The
crawlers were called out as a high point. Everything above shipped headless and needed no
correction once looked at.

### Phase 2 — landed 2026-09-10 (engine only)

Split deliberately: the engine model and the single-tank path shipped together, and the group-mode
adapter edit did not. Everything below is additive — no existing motion model was touched, and the
goldens and parity suite passed again **with no expected-value edits**, this time without even
being reachable from the new code.

* **`FlockEngine.stepDrift`** — velocity commanded directly, like the crawl and unlike the
  swimmers: a jellyfish has no inertia worth integrating, and what looks like momentum is the
  water, which is already in the wander's correlation time. The two axes are deliberately unlike
  each other:
  * **Horizontal — no forward drive at all.** Two independent OU processes, one per axis, at
    θ = 0.25 (≈4 s of correlation, an order of magnitude slower than the swimmers'). Independent
    axes rather than a wandering heading, because a drifter is not pointing where it is going.
  * **Vertical — pulse-and-sink.** The burst envelope on a new axis and far more asymmetric: a
    0.8 s contraction reaching 0.10 blocks/s up, then ~3.7 s of passive sink at 0.035. The sink is
    set to the pulse's own duty-cycle mean so the two cancel over a cycle, and the residual is
    absorbed by wall avoidance rather than by tuning the pair against each other — a balance that
    a domain of a different height would break.
* **Bell separation and weak same-species cohesion**, in one pass, over drifters only. Everything
  else already sees them: the swimmers' separation term scans all fish regardless of class.
* **A drifter gets its own wall margins**, not `Tunables`'. Found by test: borrowing the
  swimmers' 0.20 left avoidance active across the *entire* width of a one-block-deep tank —
  measured horizontal speed peaked at 0.052 blocks/s against a drift speed of 0.012, i.e. the
  containment doing four fifths of the moving. At 0.08/0.10 the open water is pure drift again.
* **The drift gate measures headroom** (`0.6 × length`), per §2.4. The factor is below 1 on
  purpose, unlike the swimmers' 2.5: the three drifting species are 28–40 cm (≈0.30–0.36 blocks
  rendered) while the *legacy single-tank* model's vertical band is only `yRange` = 0.25 blocks
  tall, so any factor at or above 1 would demote every jellyfish in every single tank to `STATIC`.
  A gate that only ever fires is not a gate.
* **The tail-beat clock does not run for a drifter**, even though the engine now moves it. That
  clock exists to couple tail-beat frequency to swim speed; a jellyfish's pose is a bell bob and a
  spin on game time. `step`'s switch yields "does this pose beat" rather than "did this fish
  move" — the two stopped being the same thing here.
* **No renderer or adapter change was needed for single tanks.** The single-tank draw loop already
  translates every fish by the engine's `renderX/Y/Z`, so `locomotionOf`'s existing
  `upright_float → DRIFT` mapping carries the whole feature. `UprightFloat`'s own bob (±0.05
  blocks at 0.03 Hz, a 33 s period) rides on top of the ~5 s pulse as a slow swell; the two are
  an order of magnitude apart in period and do not fight.

**Verification.** `:fishsim` 132 (131 passing, 1 pre-existing skip), of which 8 are `DriftTest`
plus one carry case added to `RebuildCarryTest`; `:common` 29 passing; both loaders compile.
`DriftProbe` prints the cycle's measured shape — in a group: 93% of the vertical band swept, mean
Y within 1.6% of centre, a 5.1 s pulse period, 0.77 blocks of net carry in 200 s, zero backstop
engagements. In a single tank the same numbers hold at 51% of a much shallower band.

`LocomotionTest` lost `DRIFT` from two of its rows, which is the phase working rather than
breaking: its "never moves" loop now skips `DRIFT` via `simulated()`, its animation-clock test
moved to `ANCHORED`, and its ungated-classes test is down to `ANCHORED` alone.

**In-game acceptance: passed** (2026-09-10), with no correction needed.

### Phase 2b — the group-mode split, landed 2026-09-10

The half deferred above: drifters now join the anchor's engine and drift across the whole
aquarium rather than inside their own block.

* **The split rule became an object, `TankFlockAdapter.GroupSplit`.** The risk here was never the
  motion model — it was the two mirrored loops that have to agree on every slot (handoff §2.6),
  which were two hand-written copies of the same `if` and one edit away from drawing a fish twice.
  They now share one instance-per-tank rule carrying the quota counters, so agreement is
  structural. `stayingHomeAs` is the same story for the other half of the decision: what class a
  fish that did not make the cut keeps.
* **A third quota counter, and it generalised.** Counters are per class (`taken[ordinal]`), each
  against the same per-tank quota, because the three classes compete for different resources —
  water volume, floor area, and the column a jellyfish pulses through. Phase 3 and 4 add a class
  to the eligible list and get their counter for free.
* **Only free swimmers are size-gated in the adapter.** Crawlers and drifters are admitted ungated
  and let `FlockEngine.gate` demote them: a demoted one still belongs in *group* space, standing
  on the group's sand or hanging in its water, which is where the player is looking. A swimmer is
  the opposite case — the group's gate has to be checked here, because a swimmer that joined and
  was demoted inside the group engine would be drawn nowhere at all.
* **The group draw loop stopped assuming everything in it swims.** It branched on
  `locomotion == BENTHIC` and cast the rest to `HorizontalSwim` — fine while the group held only
  swimmers and crawlers, a `ClassCastException` the moment an `UprightFloat` arrived. It now
  branches on `swimmers[]` first and falls through to `FishAnimator.apply` on game time, mirroring
  the single-tank loop exactly. No other renderer change was needed: `floorPoseLift` already
  returns 0 for a drifting pose, so the group translate was right for it already.

**Verification.** `:common` 36 passing, of which 7 are `GroupSplitTest` — the quota isolation, the
gate asymmetry, and a mixed tank walked through both passes asserting they agree slot for slot.
`:fishsim` unchanged at 132 (131 passing, 1 pre-existing skip) with **no golden or parity
edits** — nothing in the engine moved. Both loaders compile.

### Phase 3 — landed 2026-09-10

Rays glide. The class is the planar swimmer model under a parameter set of its own, so the work
was where those parameters live and what the model could not already say.

* **`Tunables.GLIDE`** — a third canonical set beside `DEFAULT` and `GROUP`. Slower and wider than
  the shoal on every axis, and unschooled: alignment, speed-matching and cohesion are all zero and
  the separation radii are up around a body length. The defining number is
  `turnRateDegPerTick` 2.2 against the shoal's 7 — what makes a ray read as a ray is that it
  cannot whip around.
* **It is a fixed set, not one derived from the engine's own.** A lone tank runs `DEFAULT`, whose
  planar terms are all neutralised to hold the binary model's parity lock, so deriving from it
  would hand a ray `patrolSpeed` 0 and no wander correlation — a ray jiggling in place. A ray moves
  the same way in a lone tank as in a group; only the room it has differs.
* **`stepFishPlanar` gained a per-fish parameter view**, `Tunables p = params(i)`, and reads `p`
  where it read the engine's `t`. A free swimmer gets *the same object*, so the arithmetic is
  identical and `VoxelGoldenTrajectoryTest` never moved. This is the whole of the dispatch: there
  is no `stepGlide`.
* **The one thing a parameter set cannot express: a floor to fly over.** A glider holds a ride
  height measured from the sand *under it* — `min(0.45 blocks, 30% of the local headroom)`, plus a
  17 s swell — so it follows a group's varying terrain instead of using the water column. Both
  quantities are fractions of the actual headroom rather than absolute heights, because the same
  creature has to work in a lone tank's quarter-block slab and in a stacked group's several
  blocks, and an absolute ride height would pin it to the lid of the former.
* **`belly_down`'s bank is the engine's now.** It was an open-loop sine that rocked the wings
  whether or not the ray was turning; it is now `FlockEngine.bankFraction(i)` — the lean the fish
  has earned by turning — times the pose's own `bank_amplitude`, so the data file still says how
  far a species leans and the engine says only when. `bankFraction` is normalised in [−1, 1]
  precisely so the renderer never has to know which parameter set stepped the fish.
* **A glider runs in a `Box` domain too**, which no planar-model fish had done before: a lone
  tank's spatial index is inactive, and `grid.gather` already returns −1 there, which is the
  brute-force path the binary model always takes. What did need saying is that yaw is now
  meaningful in a Box (`continuousYaw()`, replacing the narrower `hasBenthic` test) and that the
  index, when it *is* active, must be sized from the widest parameter set in play — a glider's
  separation radius is three times the shoal's, and a grid that skipped a fish in range would
  break its "skipped contributes exactly zero" contract.
* **Gliders join the group**, gated exactly like swimmers (`GroupSplit`), with their own quota
  counter — the generalisation Phase 2b left in place cost one line here.

**In practice the gate decides where you see this.** Rendered lengths are 0.54 (`acute_iaspis`)
and 0.86 (the two mantas), against a gate of 2.5 body lengths of straight run, so all three stay
`STATIC` in a lone tank — a giant manta in a one-block tank has nowhere to glide, and freezing it
is the honest answer — and glide in an aquarium big enough to hold them. That is a design
statement, not an accident: big rays are a reason to build a big tank.

**Verification.** `:fishsim` 139 (138 passing, 1 pre-existing skip), of which 6 are `GlideTest`
plus one carry case; goldens and parity again with **no expected-value edits**. `:common` 36
passing, both loaders compile. `GlideProbe` measures the model: in a 4×2×2 group, 10.6 blocks of
path in 200 s at a mean 0.059 blocks/s, riding 0.48 above the sand (28% of headroom), mean turn
1.0°/tick against the 2.2 cap, mean bank 0.48 of full lean, zero backstop engagements. In a lone
tank the turn rate sits pinned at the cap and the bank with it — a small ray in a small box
circles continuously, which is what a continuous max-rate turn *is*; at `bank_amplitude` 10° that
is a steady 10° lean, not a roll.

`LocomotionTest` is down to `ANCHORED` alone in its "never moves" and ungated-class rows, which is
Phase 4's cue.

#### After the first in-game look: the ray buzzed

Reported as a small, fast vibration laid over a banking effect that was otherwise right. Three
things were wrong, and only the first was the one being looked at.

1. **`bank` is a one-tick signal and was being drawn raw.** It is computed from a single tick's
   yaw delta, so it carries every bit of the wander's and the separation term's noise, and unlike
   `renderYaw` and `renderPhase` it had no interpolated mirror — so it also stair-stepped at 20 Hz.
   Measured on a gliding ray: **0.102 of full lean of change per tick, reversing sign 1.7 times a
   second.** Now `bankSmooth` → `prevBankSmooth` → `renderBank`, the same three-array shape
   `tailPhase` has, and `bankFraction` reads the interpolated value. `bank` itself is untouched —
   `ParityTest` asserts it bitwise.
2. **A low-pass alone was not enough**, which is worth remembering. `bank` *saturates* at
   ±`bankMax` on any turn at all — for a glider, whose turn budget is a third of the shoal's, that
   is most turns — so it slams the full width of its range, and a fifth of a two-lean gap is still
   a 0.32 snap. The fix is a second, physical constraint: a **maximum roll rate**
   (`BANK_ROLL_RATE_DEG_PER_SECOND`, 6°/s — level to fully banked in ~1.7 s). A body has a top
   roll rate; the filter alone only says it has inertia.
3. **Half of `Tunables.GLIDE` was inert.** `advanceWander`, `advanceBurst` and `deriveTraits` read
   the engine's `t` rather than the fish's `p`, so a ray's wander correlation, its wingbeat period,
   and its trait spread were all silently the shoal's. Found by changing a glide constant and
   measuring *no difference at all* — the `nnFront` lesson in a new costume. They take the
   parameter set now.

Then the wander itself, which is what the remaining weave was: the OU's steady-state amplitude is
`sigma/√(2·theta)`, so lowering `theta` for a calmer ray while inheriting GROUP's `sigma` had it
wandering **harder** than the shoal (0.80 against 0.60). Sweeping `sigma` and `cruiseSpeed`
separated two things that look alike: they scale how *far* each swing goes (mean turn 0.84 →
0.41°/tick) and do not touch how *often* it reverses (~1/s at every value tried, solitary or
crowded). That rate is a limit cycle in the rate-limited yaw chasing its own steered velocity, not
noise, and it is what a swimming animal weaving looks like — so amplitude was the right lever and
the rate was never the target.

**Measured after, in the 4×2×2 group:** bank change per tick 0.102 → **0.012**, sign reversals
1.71 → **0.49/s**, mean turn 1.03 → **0.50°/tick**, with path length and ride height unchanged.
`GlideTest.bankFractionIsNormalisedAndEarnedAndSteady` now bounds the per-tick change and the
reversal rate rather than the amplitude — amplitude cannot tell a lean from a flutter, since the
two have the same mean.

#### After the second look: too slow, and orbiting

Two complaints, and they turned out to be one number. A ray holding a steady turn describes a
circle of radius `v/ω`; at `patrolSpeed` 0.05 and the measured mean 0.5°/tick that circle is
**0.4 blocks across** — "the mantas just loop over the same two block path", exactly. No amount of
wander tuning reaches it, because the wander sets the *amplitude of the weave* and not the *radius
of the orbit*; sweeping it changes the former and leaves the latter alone.

The probe had been printing the evidence since Phase 3 landed and it went unread: 10.6 blocks of
path against 1.4 of net displacement. Path length cannot distinguish a cruise from a tight circle
run for three minutes, so the probe and the test now measure **the ground actually visited** and
the **implied circle** instead.

`patrolSpeed` 0.05 → **0.14** (`maxSpeed` 0.26 to keep the burst peak under the ceiling). That
opens the circle to 1.6 blocks and a ray crosses a 6×2×4 aquarium corner to corner. `wallMargin`
was swept too and left at 0.25 — it buys a little radius, but at double the speed a creature this
slow to turn needs its margin *more*, not less, and containment held with zero backstop
engagements at every size including a one-block box.

One assertion was deleted rather than adjusted: `GlideTest` required a glider to average below the
shoal's cruise speed. That encoded "a ray is slow", which is not the class — **what makes a ray a
ray is that it cannot turn.** Holding it slow while its turn rate stayed capped is precisely what
produced the orbit. `aGliderCrossesTheAquariumRatherThanCirclingInIt` replaces it.

**Measured, 6×2×4 with one ray:** mean speed 0.060 → **0.138** blocks/s, turn radius 0.53 →
**1.56** blocks, ground covered 3.7×2.3 → **5.4×3.5** blocks, still zero backstop engagements.

**Accepted in game** after this change — the first part of this work that has been. `GLIDE` took
two rounds of looking to get there, both of them things no headless check would have raised on its
own: a signal drawn raw that needed a render mirror, and a speed that was wrong only in
combination with a turn rate. The remaining classes have had no such pass; assume they each owe
one.

**In-game acceptance: passed**, after the two rounds of correction in the sections above — the
only phase that needed any. Phases 1 and 2, Tier 2 and the 512 cap passed in the same session with
no changes.

### Squash-and-stretch (§3.6) — landed 2026-09-10

The cross-cutting item, shipped between Phase 3 and Phase 4 exactly where §5 put it: it is the
drive → pose → pivot path that Phase 4's retract needs, so building it first makes that phase a
trigger and a constant rather than a new system.

* **`FlockEngine.shapeDrive` / `prevShapeDrive` / `renderShape`** — a second envelope off the same
  trigger as `burstDrive`, advanced by `advanceShape` next to it in both `stepDrift` and
  `stepBenthic`, mirrored to render time the way `tailPhase` is, and carried across a rebuild.
  Separate arrays rather than a reuse of `burstDrive`, because the two want opposite decays: drift's
  drive relaxes at 0.9/s because that is the *coast*, and a bell mapped onto it would snap shut and
  take four seconds to refill. The shape shares each model's attack and relaxes at 3.0/s (drift) and
  6.0/s (crawl) — measured half-life 5 ticks against the motion drive's ~15.
* **It is in the engine although it is purely cosmetic**, on `tailPhase`'s precedent: that is where
  it can be integrated at a fixed 20 Hz, interpolated, carried, and tested headlessly. A render-side
  follower would be re-derived per frame at a variable rate and none of that would hold.
* **Only the classes whose poses are otherwise nearly still run it.** `FREE_SWIM` has a `burstDrive`
  too and deliberately gains no shape: `horizontal_swim` already has a tail wiggle and a bank driven
  by the same motion, and a third deformation on top would fight both — the burst-"hop" lesson
  again. `ShapeDriveTest.aFreeSwimmerNeverDeforms` pins it.
* **The frame is the thing to get right, and the plan had it backwards.** §3.6c says the scale goes
  "last, after the roll". Under `PoseStack`'s composition it is the other way round: calls are
  applied to the geometry in reverse, so a scale written *after* the −45° roll deforms along the
  texture's diagonal, and one written *before* it deforms along the creature. The upright poses now
  write it before the roll; `FishAnimatorSquashTest.anUprightCreatureDeformsAlongItsOwnAxes`
  measures the upright frame's axes rather than the amount of change, because the wrong placement
  produces a deformation of exactly the right magnitude pointed 45° off.
* **Each class pivots where it actually touches the world.** A drifter hangs, so its bell scales
  about the item's centre with no sandwich. A crawler scales about its measured contact point
  (`UprightSit.pivotFraction × scale`, the same product `floorPoseLift` forms) — scaling about the
  centre would sink it into the sand on the squash and float it on the stretch, which is the Phase 1
  floor-lift bug in a new guise, and is the assertion
  `aCrawlersContactPointHoldsStillThroughTheWholeSquash` exists for.
* **A flat-lying creature deforms in its own plane, not vertically.** §3.6 assumes an upright
  silhouette throughout; `floor_sit` (starfish) is face-up, and a vertical squash there deforms the
  sprite through its own zero thickness and shows *nothing*. It shortens along the body and spreads
  across it instead, area preserved, written after the `XP(-90)` so it lands in the plane the
  creature lies in — the opposite placement to the upright poses', for the same reason.
* **Volume is preserved** (`sy`, `1/√sy`, `1/√sy`), which is what makes the effect read as flexing
  rather than as the animal changing size. That matters more here than it would elsewhere, since
  `render_calibration` exists precisely to draw these species true-to-scale.
* **Authoring surface**: `pulse_stretch` on `upright_float` (0.10) and `scuttle_squash` on
  `upright_sit` / `floor_sit` (0.05), optional with non-zero defaults, so the effect ships without
  touching any of the 60 `fish_profile` files and a species that looks wrong can opt down. The names
  deviate from §3.6's `pulse_squash` because the two are not the same motion: a bell *elongates* as
  it contracts (`sy = 1 + a·d`) and a crawler *crouches* as it pushes off (`sy = 1 − a·d`), and one
  name across both would have had an author guessing the sign.

**Verification.** `:fishsim` 145 (144 passing, 1 pre-existing skip), of which 5 are `ShapeDriveTest`
— range, the decay comparison, the interpolation endpoints and midpoint, carry-over, and the
free-swimmer exclusion — with **no golden or parity edits**. `:common` 41 passing, of which 5 are
`FishAnimatorSquashTest`. Both loaders compile.

**The amplitudes are the one thing with no headless acceptance**, exactly like the `CRAWL_*` and
`DRIFT_*` constants: 0.10 and 0.05 are eye calls that have not yet been looked at in game.

### Phase 4 — landed 2026-09-10

The last class. Garden eels keep a burrow and duck into it when something big swims past.

* **`FlockEngine.stepAnchored`** — the cheapest model here by a wide margin, and the only one whose
  defining property is what it does *not* do: the footprint is chosen once at rebuild by the same
  floor scatter a crawler gets and never moves again, which `AnchoredTest.anEelsFootprintNeverMoves`
  asserts bitwise with a live swimmer alongside. All that is stepped is the retract envelope.
* **The retract is §3.6's machinery with a different trigger**, exactly as that section predicted:
  `shapeDrive` again, so there is no new state, no new carry plumbing and no new render mirror —
  only a pair of rates and a much larger amplitude. Building the squash first is what made this
  phase a constant rather than a system.
* **Down fast, up slow** — 10/s and 0.8/s, i.e. most of the way into the sand in 0.3 s and the
  better part of three seconds to come back out. §3.4 asks for a re-emergence "a couple of seconds
  later"; the asymmetric decay *is* that, with no hold timer and none of the per-fish state a timer
  would need. A threat that lingers simply keeps the target held, which is also what a real eel does.
* **Only something bigger, and only something that moves.** The threat test is relative to the
  eel's own size (0.8×) and within 4 body lengths, and it skips `ANCHORED` and `STATIC` neighbours
  outright. Without that exclusion a colony holds itself permanently retracted — every eel is a
  large object parked half a block from its neighbour — and a demoted swimmer frozen nearby would
  pin one down forever. The size factor is deliberately **below 1**: an eel's length here is its
  *height*, it is a thin creature, and a factor above 1 stops the effect firing in an ordinary tank
  at all. A reaction nobody ever sees is the same as not building it.
* **A plain scan, not a grid query**, following the crawl's and the drift's separation passes. The
  classes that scan are the ones with few members, and it keeps the anchored radius out of
  `interactionRadius`, whose contract — a fish the grid skips contributes exactly zero — would
  otherwise have had to grow to cover it (handoff §2.11).
* **`ANCHORED` is placed on the floor, gate or no gate** (`floorPlaced`, now shared with `BENTHIC`),
  because the renderer stopped pinning `Planted`'s Y in this phase. The gate measures the floor a
  burrow needs (1 × length²), which is §2.4's "a free floor footprint exists" made into a number.
  A colony also spaces itself now: `placeOnFloor`'s rejection sampling covers both floor classes, so
  eels no longer stack and no longer land inside a cosmetic. It also means `garden_eel`'s
  `xz_spread` no longer clusters them — the floor scatter uses the whole tank, which is what a
  colony looks like.
* **Eels join the group**, with their own quota counter — the generalisation Phase 2b left in place
  cost one line here and one in `stayingHomeAs`. An eel that loses the quota draw keeps its class at
  home: its model needs nothing the group provides.
* **A standing pose bug, found by hanging a large amplitude off it.** `applyPlanted`'s sway pivot was
  written `(+p, rotate, −p)`, which fixes the item's *top* and swings the base through the sand —
  the opposite of what its own comment claimed. `PoseStack` applies its calls to the geometry in
  reverse, so the leading translate has to be the negative one. At 3° of sway nobody had noticed;
  at fifty times that amplitude it would have been the whole effect. `FishAnimatorSquashTest`
  now pins the buried base across the sway *and* the withdrawal.
* **The withdrawal is a scale about that base, not a translate**, and deliberately not
  volume-preserving: an animal going into a hole gets shorter without getting fatter. A translate
  would push the sprite through the tank's own floor, where the sand is two pixels thick. It stops
  at 90% (`retract_fraction`) so a nub is left — an eel that vanished completely reads as a
  rendering glitch rather than as an animal hiding.

**Verification.** `:fishsim` 149 (148 passing, 1 pre-existing skip), of which 5 are `AnchoredTest`;
goldens and parity again with **no expected-value edits**. `:common` 44 passing, both loaders
compile. The threat trigger is tested by *parking* the threat rather than by waiting for a roaming
swimmer to happen past — a test that waits measures the scatter, not the model.

`LocomotionTest` is down to `STATIC` alone in its "never moves" row, and its ungated-classes test is
gone: every class has a gate of its own now, each asserted next to its own model. That test failing
was the phase working, as it has been for every phase.

**Not verified in game.** The retract's amplitude, its two rates and the threat radius are eye
calls with no headless acceptance, exactly like the `CRAWL_*` and `DRIFT_*` constants — and §3.6's
two amplitudes are in the same position. `GLIDE` needed two rounds of looking; assume this one owes
its own.

#### After the first in-game look: the eels never came out

Reported from a large, well-stocked tank: the colony sat permanently in the sand. The trigger was
a **state** — hold the retract target up while something big is nearby — which is right in a quiet
tank and wrong in a busy one. Past some stocking density there is always a qualifying fish inside
the radius, so the target never falls, and at `ANCHOR_EMERGE_RATE` 0.8/s the eel needs three clear
seconds to get back out. It never got one.

No radius and no rate would have fixed that. Any threshold a crowded tank sits permanently above is
the same bug at a different fish count — the number would only have moved which tank it happens in,
which is the shape of failure this document keeps rediscovering.

The reaction is **edge-triggered and habituating** now. Only an eel that is out and watching can be
startled; it then hides for a fixed 1.6 s and ignores everything for a further 6 s, whatever is
still swimming past. Both durations carry a ±35% per-fish jitter off the seed so a colony does not
duck in unison. The refractory is deliberately longer than the emerge takes, so the eel is always
fully out and visible for a while before it can be startled again — which bounds the fraction of
time it spends hidden at about a fifth (analytic worst case 36%) **however many fish are in the
tank**, rather than by choosing a number that happens to work at one stocking density.

That needed one piece of state: `anchorTimer[i]`, seconds, with the sign carrying the phase
(positive hiding and counting down, negative habituated and counting up, zero out and watching).
It is carried across a rebuild like everything else continuous in time.

**Measured**, in a 4×2×3 group with two eels and twenty swimmers over five minutes: hidden 16% and
29% of the time, both reaching fully out. `AnchoredTest.aCrowdedTankDoesNotHoldTheEelsUnderground`
is that scenario, and `aThreatThatStaysGetsOneReactionNotAPermanentOne` is the same statement
reduced to two fish, where it is about the model rather than about a stocking density.

One thing the report incidentally settled: a **big** eel in a tank of ordinary fish never reacts at
all, since the threat threshold is 0.8× its own rendered length. That is left as it is — it is the
same statement as a giant manta staying `STATIC` in a one-block tank, and the fix for wanting to
see it is a bigger fish, not a lower threshold.

`:fishsim` 151 (150 passing, 1 pre-existing skip).

Each phase ships independently and leaves the other species on their current behaviour, so there
is no half-migrated state at any point.

---

## 6. Verification

The engine's existing verification model applies unchanged, and is the reason this is safe to
attempt at all.

**Parity.** `GoldenTrajectoryTest`, `ParityTest`, `VoxelGoldenTrajectoryTest` and
`SpatialIndexEquivalenceTest` must pass with no expected-value edits at every phase. If a golden
moves, `FREE_SWIM` is not reaching identical code.

**New invariants**, one set per class, in the style of `InvariantTest`:

* Benthic: `|y − floorHeight(l,d)| < ε` every tick; never enters a NaN-floor column; pairwise 2D
  distance ≥ the sum of footprint radii; speed ≤ the class cap.
* Drift: stays inside its vertical band; vertical speed ≤ the pulse cap; horizontal displacement
  per second below the free-swim floor — a drifter that "swims" is a tuning failure the test
  should catch.
* Anchored: footprint XZ bit-identical across the whole run; retract state ∈ [0,1].
* All classes: `backstopEngagements() == 0`, and carry-over across `rebuildPreserving` preserves
  position, phase and class state (extend `RebuildCarryTest`).

**Headless first.** New `Scenarios` entries — a mixed 3×3 with a shoal, two crabs, a jelly and a
ray — exported through `HeadlessRunner` to GIF and inspected in `SimViewer`. Nothing here needs the
game running to be evaluated.

**On metrics.** Floor-coverage dispersion for crawlers and vertical-distribution histograms for
drifters are worth having as *diagnostics*. They are not targets. The pooled-speed-CV episode
(maximizing a variance metric shipped a visible "hop") and the inert `nnFront` metric are the two
standing reminders that a number moving the right way is not the same as the tank looking right —
the GIF is the acceptance test; the metric only explains it.

**Standing caveat — discharged 2026-09-10.** Tier 2 swarm realism, the 512-fish group cap and
every phase of this document have now had their in-game acceptance pass, in one session as
intended. Only `GLIDE` needed changes, and both were of a kind headless verification cannot reach:
a raw per-tick signal that needed interpolating and rate-limiting before it was drawn, and a speed
that was wrong only in combination with a turn rate. Neither was a metric moving the wrong way —
in the second case the metric that would have shown it (net displacement) was on screen and was
read past. The lesson is unchanged and now has a third instance: the picture is the acceptance
test, and a metric has to be able to *see* the failure before its silence means anything.

The one thing still open is cost rather than behaviour: the 512 cap's render measurement
(fish-tank-group-scaling.md §3.6).
