# Fish Swarm — Tier 2/3 Handoff

**Written:** 2026-09-09 at the end of Tier 1. **Revised:** 2026-09-10 at the end of Tier 2 — the
work items in §4 have been done or disproven, and §4 now records what actually happened rather than
what was planned. Read [`fish-swarm-realism.md`](fish-swarm-realism.md) first — it holds the
assessment, the tier list, and the measured Tier 1 **and Tier 2** results (its §4 is the Tier 2
result log). This document is the cold-start context for picking up the next block of work: what is
true right now, what will bite you, and how to verify.

---

## 1. State of the code

Tier 1 is implemented, tested, and confirmed good in game by the user (2026-09-09).

> **Separate open thread:** `docs/fish-tank-group-scaling.md` analyses why large multi-tank
> structures strand their fish in a corner blob (`RENDER_MAX_GROUP_SIZE = 64`) and what has to
> change to raise that cap. It is independent of the swarm model — nothing in it blocks or is
> blocked by Tier 2/3 work — but it is the reason a big build looks wrong today, so read it before
> concluding the flocking model is at fault for a distribution problem.

Tier 2 is **implemented, headlessly verified, and accepted in game** (2026-09-10 — watched in a
real multi-tank aquarium alongside the locomotion classes and called out as a high point; no
changes were needed). The paragraph below described the state before that pass and is kept for the
reasoning it carries.

Tier 2 was, at the time of writing, **implemented and headlessly verified, but had NOT had its in-game acceptance pass**
(2026-09-10). All 103 `:fishsim` tests pass; both loaders compile. Two of the four planned Tier 2
items landed, one was disproven and reverted, one is untouched:

| item | outcome |
|---|---|
| Nonholonomic steering | **landed**, as a turn-rate cap on the existing force model rather than the planned rewrite |
| Field of view / rear blind cone | **disproven and reverted** — the target metric cannot move; see realism doc §4.2 before re-attempting |
| Anticipatory separation | **landed**; `separationSpeed` came down 0.40 → 0.25 as predicted |
| Per-species behaviour profiles | not started |

### The two models — do not confuse them

| | binary 2.5D | planar |
|---|---|---|
| entry point | `FlockEngine.stepFish` | `FlockEngine.stepFishPlanar` |
| tunables | `Tunables.DEFAULT` | `Tunables.GROUP` |
| used by | single tank (`FlockDomain.Box`) | multi-tank groups (`VoxelDomain`) |
| status | **bitwise-locked** by `GoldenTrajectoryTest` + `ParityTest` | free to change |

**Every Tier 1 and Tier 2 term is neutral-valued in `DEFAULT` and skipped by the engine at that
value.** Keep that pattern: a new field must default to a value the engine branches away from, so
the binary model stays bitwise-identical. `patrolSpeed` is the original example of the idiom.

`FishSpec.species()` is already plumbed, and only the planar model reads it.

### Adding a tunable is now cheap

`Tunables`' withers used to be 24 hand-written 35-argument constructor calls. They now route
through `Tunables.Mut`, a mutable mirror of the component list, so a new tunable costs: one record
component, one `DEFAULT` value, one `GROUP` value, three lines in `Mut`, a one-line wither, and a
`SimViewer` knob. `TunablesWitherTest` proves by reflection that every wither changes exactly the
component its name promises.

---

## 2. Current shipped `Tunables.GROUP`

```
maxSpeed 0.20   cruiseSpeed 0.025   steeringGain 2.0   maxForce 0.5   neighborCount 6
separationRadius 0.24   separationSpeed 0.25   alignmentWeight 0.15 (UNUSED in planar)
cohesionSpeed 0.05   wallMargin 0.20   wallAvoidSpeed 0.50
neighborRange 0.9   patrolSpeed 0.10   separationRadiusOther 0.60
-- Tier 1 --
wanderTurnSigma 0.8   wanderTurnTheta 0.9        (OU steady-state sigma = 0.8/sqrt(1.8) ~ 0.60)
alignHeadingWeight 0.05   speedMatchWeight 0.15  (alignmentWeight is dead while this is > 0)
traitJitter 0.12
burstPeriodSeconds 4.0   burstDuty 0.45   burstThrustScale 1.4   burstCoastScale 0.3
-- Tier 2 --
turnRateDegPerTick 7.0   separationLookahead 1.0
```

Engine constants (`FlockEngine`, not tunables): `PLANAR_TURN_RATE 7` (deg/tick — now only the
fallback for when `turnRateDegPerTick` is 0), `PLANAR_YAW_MIN_SPEED 0.005`, `WANDER_CLAMP 2.5`,
`TURN_CAP_MIN_SPEED_FRACTION 0.25`, `BURST_PERIOD_JITTER 0.25`, `BURST_ATTACK_RATE 3.0`,
`BURST_DECAY_RATE 0.8`.

---

## 3. Load-bearing couplings — do not remove these while refactoring

Four things look like tuning nits and are not. All were found by measurement, after a
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
4. **The nonholonomic turn cap only limits the TURNING component of the acceleration**, never the
   forward/braking component, and is applied *after* the `maxForce` clamp. Both matter. Capping
   total acceleration would strip wall-avoidance authority and break containment; applying it
   before `maxForce` would let the force clamp re-introduce an uncapped turn. It only ever reduces
   `|a|`, which is why the acceleration invariant did not need retuning.

Also: `deriveTraits(i, seed)` is called from **both** `initFish` and `setTunables`. The second call
is what makes the viewer's trait/burst sliders live without a reseed. Anything continuous in time
(positions, velocities, `wanderState`, `noiseState`, `burstPhase`, `burstDrive`) must NOT be reset
there, and must be added to the carry-over snapshot (`captureCarry` / `seedCarried` /
`allocateCarry`) — otherwise a surviving fish jolts on every rebuild.

---

## 4. Where the work stands

### 4.1 Nonholonomic steering — DONE (differently than planned)

Shipped as `Tunables.turnRateDegPerTick`, a cap on the turning component of the steering
acceleration (`a = v·ω`), not as the planned thrust + yaw-rate rewrite. Realism doc §4.1 has the
reasoning and the before/after table. Worst-case travel-direction turn rate went 20°/tick → 8.9,
sideslip p95 21° → 8°, backstop engagements still 0.

The planned rewrite is still *available* if a future need arises, but nothing currently motivates
it: the measurement that was supposed to justify it (fish visibly translating sideways or
backwards) came in at mean sideslip under 3° and backwards motion at 0.01–0.1% of ticks. Read
realism doc §1.4 before spending time on it.

Bank saturation is fixed as a side effect — see realism doc §4.1.

### 4.2 Field of view — DISPROVEN, DO NOT RE-ATTEMPT BLIND

Implemented as specified, swept across its whole parameter space, measured inert, reverted. The
headline: `nnBearingFrontFraction` **cannot be moved by anything in the model** — a control ablation
swung shoal spacing 2.6× and polarization 0.47→0.68 without shifting it out of 0.375–0.405. At tank
scale, which neighbour is nearest is set by packing against walls, not by preference.

Full evidence in realism doc §4.2. If you want front/side structure you need a *positional*
station-keeping term, not a perception filter, and you need a metric that has been shown to respond
to something first.

### 4.3 Anticipatory separation — DONE

Shipped as `Tunables.separationLookahead` (1.0 s), folded into the existing separation loop. Let
`separationSpeed` drop 0.40 → 0.25 while worst-case closest approach on the invariant matrix went
0.037 → 0.072 (so `MIN_PAIRWISE_FLOOR` was tightened 0.02 → 0.035).

Two things to know before touching it: the horizon curve is **not monotonic** (0.5 s is worse than
off), and it turns the 1-block tanks into a milling torus. The torus was verified emergent rather
than an artifact of the head-on tie-break. Realism doc §4.3.

### 4.4 Per-species behaviour profiles — NOT STARTED

Couzin-style zone shape (repulsion / alignment-band width / attraction) on `FishProfile`, so a
tetra shoals tight and polarized while an angelfish drifts loosely. `FishSpec.species()` and the
species-scoped neighbour search already exist; the engine would need per-fish multiplier arrays
derived from the spec, and `FishProfile` a new optional codec block with defaults reproducing
today's behaviour exactly.

The reason it was not simply done: the mechanism is straightforward, but its *content* — which of
the 60 shipped species shoal tightly and which drift — is an authoring decision the harness cannot
derive, and building the mechanism without it produces 60 files of guesses. Get that direction
before starting.

---

## 5. How to verify — the loop that works

**Never propose in-game capture for verification** (the MCP bridge is retired). Verify headlessly,
then hand the user a build for an acceptance pass. That last step is not optional: Tier 1's metrics
said the first burst tuning was good, and it was visibly wrong in game. **Tier 2 has not had this
pass yet** — the thing to look at first is whether the 1-block milling torus reads well.

```bash
./gradlew :fishsim:test                  # 103 tests, seconds
./gradlew :fishsim:test -PsimStdout      # ...with probe/diagnostic stdout shown
./gradlew :fishsim:runViewer             # live sliders, all Tier 1 + Tier 2 knobs included
./gradlew :common:compileJava :fabric:compileJava :neoforge:compileJava
```

### Ablation probes beat reasoning

Every §3 finding, and both Tier 2 negative results, came from a throwaway JUnit class in
`fishsim/src/test/java/grill24/fishsim/` that turns one term off at a time and prints a table
(delete it before finishing; `-PsimStdout` is what makes its output visible). Much faster than
arguing from the code, and it repeatedly contradicted confident predictions — including "the
steering is saturating at `maxForce`", which measured at exactly 0%, and both of Tier 2's own
premises, which measured far weaker than the plan assumed.

**Two habits Tier 2 added to this:**

- **Measure the premise before building the fix.** Item 4.1's justification was overstated by
  roughly an order of magnitude, which changed the right implementation. Item 4.2's justification
  was false outright.
- **Run a control on the metric itself.** Before concluding a term does not work, check the metric
  can be moved by *anything*. That control is what turned "the FOV term is badly tuned" into "this
  metric is structurally incapable of measuring what we want", which is a much more useful finding.

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

And now a fourth: **a metric that never moves is not a passing grade, it is a broken instrument.**
`nnBearingFrontFraction` sat at ~0.38 through all of Tier 1 and was read as "no structure yet". It
is in fact unable to report structure at this scale. It is kept in `Metrics` (the CSV header would
churn otherwise) but must not be used as a target.

### Invariant thresholds you will be arguing with

`VoxelDomainTest`: `NN_MEAN_MIN 0.08`, `NN_MEAN_MAX 0.55`, `MIN_PAIRWISE_FLOOR 0.035`,
`MIN_WINDOW_DISPLACEMENT 0.05`, backstop engagements must be **0**, the speed bound is
`maxSpeed * (1 + traitJitter)`, and the travel-direction turn rate is bounded by
`turnRateDegPerTick * (1 + traitJitter) * 1.35`.

A backstop engagement is a containment failure, not a tuning nit — soft avoidance is supposed to
make the hard clamp unreachable. Do not raise the bound to make it pass. (Tightening one because
behaviour genuinely improved, as `MIN_PAIRWISE_FLOOR` was in Tier 2, is the opposite move and is
fine — record the measurement in the comment.)

### The golden fixture

`VoxelGoldenTrajectoryTest` locks planar trajectories bitwise and **will** fail on any deliberate
planar change. Protocol: delete
`fishsim/src/test/resources/golden/voxel-l-domain-trajectory.txt`, then run the suite twice (the
first run writes it and fails, the second passes). Only ever for a change you intended — never to
turn a red test green.

`GoldenTrajectoryTest` and `ParityTest` (the single-tank pair) must **never** need regenerating. If
they go red, the change leaked into the binary model.
