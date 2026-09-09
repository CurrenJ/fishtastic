# Fish Swarm — Tier 2 Handoff

**Written:** 2026-09-09, at the end of the Tier 1 changeset. Read
[`fish-swarm-realism.md`](fish-swarm-realism.md) first — it holds the assessment, the tier list,
and the measured Tier 1 results. This document is the cold-start context for picking up Tier 2:
what is true right now, what will bite you, and how to verify.

---

## 1. State of the code

Tier 1 is **implemented, tested, and confirmed good in game by the user (2026-09-09)**. All 56
`:fishsim` tests pass; both loaders compile.

**Uncommitted at time of writing** (branch `feature/26.1.2/fish_simulation`):

```
common/.../client/renderer/FishAnimator.java        bob amplitude decoupled from speedFactor
fishsim/.../core/FlockEngine.java                   OU wander, traits, burst envelope, align split
fishsim/.../core/Tunables.java                      9 new fields + 24 with* helpers
fishsim/.../harness/Metrics.java                    polarization, milling, speed CV, NN bearing
fishsim/.../viewer/SimViewer.java                   knob table rewritten onto the with* helpers
fishsim/.../test/.../VoxelDomainTest.java           speed bound is now per-fish
fishsim/.../golden/voxel-l-domain-trajectory.txt    regenerated (deliberately)
docs/fish-swarm-realism.md                          new
docs/fish-swarm-tier2-handoff.md                    new (this file)
```

### The two models — do not confuse them

| | binary 2.5D | planar |
|---|---|---|
| entry point | `FlockEngine.stepFish` | `FlockEngine.stepFishPlanar` |
| tunables | `Tunables.DEFAULT` | `Tunables.GROUP` |
| used by | single tank (`FlockDomain.Box`) | multi-tank groups (`VoxelDomain`) |
| status | **bitwise-locked** by `GoldenTrajectoryTest` + `ParityTest` | free to change |

**Every Tier 1 term is neutral-valued in `DEFAULT` and skipped by the engine at that value.** Keep
that pattern: a new field must default to a value the engine branches away from, so the binary
model stays bitwise-identical. `patrolSpeed` is the original example of the idiom.

`FishSpec.species()` is already plumbed, and only the planar model reads it.

---

## 2. Current shipped `Tunables.GROUP`

```
maxSpeed 0.20   cruiseSpeed 0.025   steeringGain 2.0   maxForce 0.5   neighborCount 6
separationRadius 0.24   separationSpeed 0.40   alignmentWeight 0.15 (UNUSED in planar)
cohesionSpeed 0.05   wallMargin 0.20   wallAvoidSpeed 0.50
neighborRange 0.9   patrolSpeed 0.10   separationRadiusOther 0.60
-- Tier 1 --
wanderTurnSigma 0.8   wanderTurnTheta 0.9        (OU steady-state sigma = 0.8/sqrt(1.8) ~ 0.60)
alignHeadingWeight 0.05   speedMatchWeight 0.15  (alignmentWeight is dead while this is > 0)
traitJitter 0.12
burstPeriodSeconds 4.0   burstDuty 0.45   burstThrustScale 1.4   burstCoastScale 0.3
```

Engine constants (`FlockEngine`, not tunables): `PLANAR_TURN_RATE 7` (deg/tick),
`PLANAR_YAW_MIN_SPEED 0.005`, `WANDER_CLAMP 2.5`, `BURST_PERIOD_JITTER 0.25`,
`BURST_ATTACK_RATE 3.0`, `BURST_DECAY_RATE 0.8`.

---

## 3. Load-bearing couplings — do not remove these while refactoring

Three things look like tuning nits and are not. All three were found by measurement, after a
plausible-sounding change broke something.

1. **The burst fades toward plain cruise near a wall** (`stepFishPlanar`:
   `burst += (1f - burst) * avoidMag`). Without it, burst peak + separation shove out-muscles
   `wallAvoidSpeed` in a 1-block domain and the hard backstop engages.
2. **Alignment fades to zero within `wallMargin`** (`alignWeight *= (1f - avoidMag)`).
   Unit-heading alignment removed a negative feedback the velocity-averaged form had for free — a
   unit heading has magnitude 1 however nearly stopped its owner is, so a school pressed against
   glass kept commanding a full-strength push into it. Ablation showed this was the **sole** cause
   of the remaining backstop engagements. Gating on *neighbour speed* was tried and measurably did
   not work; only wall proximity did.
3. **`avoidScratch` is filled at the TOP of `stepFishPlanar`**, not just before the wall term,
   because both gates above need `avoidMag`. It depends only on position, which does not change
   until integration, so this is equivalent — but a refactor that moves it back down silently
   breaks both gates.

Also: `deriveTraits(i, seed)` is called from **both** `initFish` and `setTunables`. The second call
is what makes the viewer's trait/burst sliders live without a reseed. Anything continuous in time
(positions, velocities, `wanderState`, `noiseState`, `burstPhase`, `burstDrive`) must NOT be reset
there, and must be added to the carry-over snapshot (`captureCarry` / `seedCarried` /
`allocateCarry`) — otherwise a surviving fish jolts on every rebuild.

---

## 4. Tier 2 work items, in the order I would do them

### 4.1 Nonholonomic steering — do this first

The highest-value item and the one the user will see. Today `PLANAR_TURN_RATE` caps only the
**sprite** yaw (`yawDeg`); the velocity vector still swings as fast as `maxForce` allows, so during
wall avoidance or a separation shove a fish visibly translates sideways or slightly backwards
relative to where it points.

Replace free 3D acceleration with **forward thrust + a yaw-rate command**, deriving velocity from
the committed heading. Consequences worth planning for:

- Sprite and travel direction agree by construction; `PLANAR_TURN_RATE` stops being a special case.
- `bank` becomes a genuine continuous function of yaw rate. **Fix the bank saturation while you are
  there**: `clamp(turn * 1.5, ±bankMax)` with `turn ≤ 7` and `bankMax = 10` saturates on
  essentially every turn, so bank currently reads as binary.
- Wall avoidance must be re-expressed as a *turn command*, not a lateral push — a nonholonomic fish
  cannot sidestep glass. Expect to retune `wallAvoidSpeed` / `wallMargin`, and expect the backstop
  invariant to be the thing that fails first.
- The two gates in §3 will need re-deriving in the new formulation. They guard a real failure, not
  a quirk — keep the guarantee, not necessarily the expression.

### 4.2 Field of view with a rear blind cone

Filter in `findNearestSwimmers`: ~90–120° blind astern, plus front-weighted influence. Cheap.
This is what should move `nnBearingFrontFraction` off ~0.38 (a flat ring is 0.375) — the metric is
already in place and currently reports no structure at all, which is expected and correct.

### 4.3 Anticipatory separation

Weight repulsion by time-to-closest-approach rather than raw distance. **This has a known failure
to fix, not just an improvement to add:** during the Tier 1 retune an intermediate config let two
fish reach 0.0139 apart in a crowded 1×1×1 (invariant floor 0.02). The shipped config clears it at
0.033, but the underlying cause — positional-only separation resolving head-on approaches too late
— is unfixed and will recur under crowding. Landing this should let `separationSpeed` come back
down from 0.40 toward something natural.

### 4.4 Per-species behaviour profiles

Couzin-style zone shape (repulsion / alignment-band width / attraction) on `FishProfile`, so a
tetra shoals tight and polarized while an angelfish drifts loosely. Mostly datapack surface; the
engine plumbing (`FishSpec.species()`, species-scoped neighbour search) already exists.

---

## 5. How to verify — the loop that works

**Never propose in-game capture for verification** (the MCP bridge is retired). Verify headlessly,
then hand the user a build for an acceptance pass. That last step is not optional: Tier 1's metrics
said the first burst tuning was good, and it was visibly wrong in game.

```bash
./gradlew :fishsim:test                  # 56 tests, seconds
./gradlew :fishsim:runViewer             # live sliders, all Tier 1 knobs included
./gradlew :common:compileJava :fabric:compileJava :neoforge:compileJava
```

### Ablation probes beat reasoning

Both §3 findings came from a throwaway JUnit class in `fishsim/src/test/java/grill24/fishsim/`
that turns one term off at a time and prints a table (delete it before finishing). Much faster than
arguing from the code, and it repeatedly contradicted confident predictions — including "the
steering is saturating at `maxForce`", which measured at exactly 0%.

### Metrics are diagnostics, NOT objectives

The single most important lesson from Tier 1. Tuning to maximise pooled speed CV shipped a visible
artifact (fish "hopping"). Three specific traps:

- **Pooled vs within-subject.** `Metrics.speedCv()` pools all fish and all ticks, so per-fish trait
  jitter inflates it. When measuring a per-fish behaviour, compute a within-fish statistic.
- **Variance cannot distinguish a swell from a lurch.** Always pair it with a rate-of-change
  measure (`max |Δspeed| / dt`). That is what exposed the hop: same variance, nearly double the
  rate. Reference numbers, 3×1×3 with 8 fish — pre-Tier-1 within-fish CV 0.138 at maxRate 0.173;
  the bad config 0.125 at 0.306; shipped 0.171 at 0.169.
- **Check for clipping before raising an amplitude.** The violent burst had *less* range than the
  gentle one because it spent the cycle pinned at the speed ceiling or the coast floor.

### Invariant thresholds you will be arguing with

`VoxelDomainTest`: `NN_MEAN_MIN 0.08`, `NN_MEAN_MAX 0.55`, `MIN_PAIRWISE_FLOOR 0.02`,
`MIN_WINDOW_DISPLACEMENT 0.05`, backstop engagements must be **0**, and the speed bound is
`maxSpeed * (1 + traitJitter)`.

A backstop engagement is a containment failure, not a tuning nit — soft avoidance is supposed to
make the hard clamp unreachable. Do not raise the bound to make it pass.

### The golden fixture

`VoxelGoldenTrajectoryTest` locks planar trajectories bitwise and **will** fail on any deliberate
planar change. Protocol: delete
`fishsim/src/test/resources/golden/voxel-l-domain-trajectory.txt`, then run the suite twice (the
first run writes it and fails, the second passes). Only ever for a change you intended — never to
turn a red test green.

`GoldenTrajectoryTest` and `ParityTest` (the single-tank pair) must **never** need regenerating. If
they go red, the change leaked into the binary model.
