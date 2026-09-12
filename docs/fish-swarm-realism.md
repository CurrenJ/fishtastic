# Fish Swarm Realism — Assessment and Roadmap

**Written:** 2026-09-09 · **Scope:** `:fishsim` planar (voxel-domain) flocking model — the one the
multi-tank group renderer runs. The binary 2.5D single-tank model (`Tunables.DEFAULT`,
`FlockEngine.stepFish`) is bitwise-locked by [`GoldenTrajectoryTest`] / [`ParityTest`] and is
**out of scope for every change here**.

Companion to [`fish-sim-engine-plan.md`](fish-sim-engine-plan.md) (module layout, verification
model) — that document's invariants remain binding. This one says what "more realistic" means,
in numbers, and in what order to get there.

---

## 1. Where the swarm stood before this work

`stepFishPlanar` was a competent classical Reynolds boid: topological k=6 neighbours with a
perception radius, species-scoped alignment/cohesion, distance-field wall avoidance, fixed-dt
integration plus `interpolate`, zero allocation in the hot path. The realism ceiling came from
five specific modelling choices, not from anything structural.

### 1.1 The wander was two fixed sine waves, shared by every fish

```java
wanderL(i) = sin(phaseA[i] + t*0.031)*0.7 + sin(phaseB[i] + t*0.017)*0.5
```

Only the **phase** varied per fish; the two frequencies (~203-tick and ~370-tick periods) were
global constants. Every fish in the tank rode the same clock. This was the strongest single source
of the "animated props on a shared timer" read, and the cheapest to fix.

### 1.2 Alignment was effectively off

`aliL += velL[j]`, averaged over neighbours, times `alignmentWeight = 0.15`. With speeds around
0.16 that contributes ~0.024 blocks/s to a desired velocity whose `patrolSpeed` term is 0.10 — a
~20% influence.

**Correction from measurement.** The first pass at this document predicted the model was sitting
at Φ ≈ 0.3–0.5. It was not: measured Φ was 0.74 (L-domain) to 0.95 (single tank). Wall-following
in a small domain polarizes a school almost for free, because everything is doing the same
perimeter loop. The real defect was subtler and only visible once the metric existed — alignment
gain and speed variance were *coupled*, so any attempt to firm up formation-keeping also flattened
every fish onto the same speed. That is what §2's split fixes, and it is why the shipped
`alignHeadingWeight` (0.05) is well below the 0.09 the "raise the gain" reasoning first suggested.

The `GROUP` comment (*"halved: full velocity-matching synced every wander"*) recorded the symptom
correctly. The cause was that averaging raw **velocities** couples direction and speed into one
term: matching a neighbour's heading and matching its speed are different behaviours and want
different gains.

### 1.3 Speed was nearly constant

Patrol 0.10, ceiling 0.16, no burst, no glide, no rest. Real fish do burst-and-coast: a short
train of hard tail beats, then a passive glide. Because `speedFactor = 0.6 + 0.9*(speed/maxSpeed)`
and speed barely moved, tail-beat frequency was near-constant too — the animation inherited the
flatness.

### 1.4 Steering is holonomic; only the *sprite* is rate-limited *(fixed in Tier 2)*

`PLANAR_TURN_RATE = 7°/tick` caps `yawDeg`, but the velocity vector can swing as fast as
`maxForce` allows. During wall avoidance or a separation shove a fish translates sideways relative
to where it points. Fish cannot do that.

**Measured magnitude, before assuming it is the headline problem.** Over the domain matrix, the
travel direction turned up to **20°/tick** against the 7°/tick sprite cap — but only rarely: mean
sideslip 0.3–3°, p95 up to 21° in a crowded 1×1×1, and >30° of slip on 0.3–3% of ticks. Apparent
180° reversals all turned out to be near-zero-speed artifacts where the travel direction is
numerically undefined, not darting.

So the defect is real but bounded, and concentrated exactly where the handoff predicted (crowding
and glass). That measurement is what argued for capping the existing force model's turning
component rather than rewriting the model as thrust + yaw-rate command — see §4.1.

Bank compounds it: `clamp(turn * 1.5, ±bankMax)` with `turn ≤ 7` and `bankMax = 10` saturates
whenever `|turn| ≥ 6.7°`, i.e. through any sustained turn, so bank reads as binary rather than as a
lean proportional to the turn.

### 1.5 No perception asymmetry, no individuality

Neighbour selection is isotropic nearest-k — no rear blind cone, no forward weighting, so the
whole school reacts simultaneously instead of propagating a turn. And every fish shared one
`Tunables`: identical top speed, cruise, and turn rate. Nothing broke lockstep.

Trait jitter (Tier 1) fixed the individuality half. The perception half was attempted in Tier 2 and
**abandoned on measurement** — it changed nothing anyone could detect, and the metric meant to
detect it turned out to be incapable of moving. See §4.2, which is worth reading before anyone
proposes a field-of-view term again.

### 1.6 Lower-order issues

- **Vertical is pure damped wander** (`verticalDamp = 1.2`) — the school never moves as a body in
  Y, only bobs individually.
- **Separation is positional-only.** Head-on approaches resolve late and hard, which is exactly
  why `GROUP` had to widen `separationRadius` to 0.24 and push `separationSpeed` to 0.40.

---

## 2. Roadmap

### Tier 1 — biggest visible win, local to `stepFishPlanar` + `Tunables` *(implemented)*

| # | Change | Replaces |
|---|--------|----------|
| 1 | **Ornstein–Uhlenbeck wander** — per-fish band-limited noise on the *turn rate*, seeded, aperiodic, one float of state per fish | §1.1 shared sine clock |
| 2 | **Unit-heading alignment** at a much higher weight, plus a separate weak speed-matching term | §1.2 velocity-averaged alignment |
| 3 | **Per-fish trait jitter** — ±% multipliers on top speed, patrol speed, and turn rate drawn from the existing per-fish seed | §1.5 identical individuals |
| 4 | **Burst-and-coast** — a per-fish thrust/glide cycle driving `patrolSpeed`, which feeds `speedFactor` and therefore the tail-beat animation for free | §1.3 constant speed |

Every term is gated so `Tunables.DEFAULT` (and hence the binary single-tank model) is untouched:
each new field is neutral-valued in `DEFAULT` and the engine skips the term entirely at that
value, exactly as `patrolSpeed` already does.

**Two containment couplings surfaced during implementation**, both found by ablation rather than
by reasoning, and both now load-bearing:

1. **The burst fades toward plain cruise near a wall.** Nothing sprints at glass — and an ungated
   burst peak plus a separation shove out-muscled `wallAvoidSpeed` in a 1-block domain, engaging
   the hard backstop that soft containment is supposed to make unreachable.
2. **Alignment fades to zero within `wallMargin`.** Unit-heading alignment quietly removed a
   negative feedback the velocity-averaged form had for free: a fish decelerating into glass used
   to contribute a shrinking average, whereas a unit heading keeps magnitude 1 however nearly
   stopped its owner is — so a school pressed against a wall kept commanding a full-strength push
   into it. Ablation identified this as the *sole* cause of the remaining backstop engagements.
   Gating on neighbour speed (the intuitive repair) measurably did **not** work; gating on wall
   proximity took every case in the matrix back to zero.

### Tier 2 — structural, higher payoff, more work

- **Nonholonomic steering** *(implemented — §4.1)*. Shipped as a turn-rate cap on the existing
  force model rather than the planned rewrite; same guarantee, far smaller blast radius.
- **Field of view with a rear blind cone** *(attempted, measured inert, reverted — §4.2)*. Do not
  re-attempt without first reading §4.2: the target metric cannot move.
- **Anticipatory separation** *(implemented — §4.3)*. Landed, and it did permit lowering
  `separationSpeed` from 0.40 to 0.25 exactly as predicted.
- **Per-species behaviour profiles** *(not started)*. A Couzin-style zone shape (repulsion /
  alignment-band width / attraction) on `FishProfile`, so a tetra shoals tight and polarized while
  an angelfish drifts loosely and a solitary species barely flocks. `FishSpec.species()` and
  `findNearestSwimmers` already carry the plumbing; this is mostly datapack surface, and the
  per-species values are an authoring decision rather than something the harness can derive.

### Tier 3 — flavour

- **Collective vertical**: cohesion acting on Y with a species depth preference, so the shoal
  rises and sinks as one body.
- **Startle response**: a player-proximity or tank-interaction impulse propagating through the
  neighbour graph — seconds of scatter, then re-cohesion.
- **Milling.** With Tier 1+2 in, a torus becomes reachable by tuning alone; worth exposing as a
  per-species mode since it is the most recognisable aquarium formation.
- **Cosmetic structures stamped into the distance field**, so fish weave around the shipwreck
  rather than through it.

---

## 3. How realism is measured

No in-game capture — the existing `Metrics` + `HeadlessRunner` + `SimViewer` loop covers it. Tier 1
adds four measurements so "more realistic" is a number, not a vibe:

| Metric | Definition | Target for a schooling species |
|---|---|---|
| **Polarization** Φ | \|mean unit velocity\| over swimmers | ~0.85–0.94; above ~0.96 reads as a rigid block, not a school |
| **Milling index** M | \|mean of (r̂ × v̂)\| about the shoal centroid | near 0 when polarized; high in a torus |
| **Speed CV** | stddev/mean of per-fish speed | ~0.3–0.4 |
| **NN bearing distribution** | histogram of nearest-neighbour bearing in the fish's own frame | front/side preference, not the uniform ring an isotropic model gives — **but see §4.2: at tank scale this metric is inert and should not be used as a target** |

### 3.1 Tier 1 result

n=12, 3 seeds, 6000 ticks, 400-tick warmup:

| Domain | Φ before → after | M before → after |
|---|---|---|
| 3×1×1 | 0.865 → **0.921** | 0.128 → 0.084 |
| L | 0.735 → **0.900** | 0.210 → 0.123 |
| 1×1×1 | 0.952 → 0.956 | 0.142 → 0.072 |

Polarization rises most in the L-domain, where the old model's weak alignment let the two arms
decouple. The single tank barely moves on Φ because it was already saturated by its own geometry.

Speed statistics are reported in §3.2 instead — the first pass quoted **pooled** speed CV
(0.18 → 0.38) as the burst's headline result, and that number was wrong for the purpose. Pooled CV
mixes within-fish variation over time with between-fish variation from trait jitter, so most of
that "doubling" was the ±12% trait spread, not burst-and-coast. See §3.2.

### 3.2 The speed-CV trap, and the "hop"

The burst shipped at `period 2.2s / thrust 2.2× / coast 0.10×`, chosen to maximise pooled speed CV.
In game it read as fish **hopping**: a small lurch up and forward every few seconds, then a
conspicuously fast slowdown. Two independent causes, both found by measurement:

**1. The metric was measuring the wrong thing.** Re-measured on one domain with within-fish CV
(the burst itself, trait spread factored out):

| config | within-fish speed CV | worst rate of speed change |
|---|---|---|
| pre-Tier 1 | 0.138 | 0.173 |
| Tier 1, burst **off** | 0.102 | 0.165 |
| Tier 1, burst on (2.2/2.2/0.10) | 0.125 | **0.306** |

The burst was *nearly doubling the abruptness while adding no speed variation at all* — pre-Tier 1
had more. Commanding a peak above the speed ceiling and a trough near zero means the fish spends
the cycle clipped at one end or the other: all the transition, none of the range. Pooled CV hid
this completely.

**2. The animator double-counted speed.** `FishAnimator` scaled bob *amplitude* by `speedFactor`
on top of `renderPhase` already advancing at `speedFactor` — so a burst raised the bob's height
*and* its rate together, and the whole body rose and fell in time with it. That is the "up" in the
report. The old justification ("per-frame amplitude deltas are sub-pixel") was true and irrelevant:
what reads is the envelope over half a second, not the per-frame step. Amplitude coupling is now
compressed to ±5%; frequency coupling, which conveys effort correctly, is untouched.

**The fix, and a counter-intuitive result.** The envelope is now *integrated* with asymmetric
rates — brisk attack, slow decay — so a glide is a glide rather than an active brake toward a
near-zero target. Retuned to `period 4.0s / thrust 1.4× / coast 0.30×`:

| | within-fish CV | worst rate of change | min pairwise (crowded 1×1×1) |
|---|---|---|---|
| pre-Tier 1 | 0.138 | 0.173 | 0.037 |
| shipped 2.2/2.2/0.10 | 0.125 | 0.306 | 0.013 |
| **shipped 4.0/1.4/0.30** | **0.171** | **0.169** | 0.033 |

A *deeper, slower, gentler* burst carries more speed variation than a violent one and is
marginally smoother than having no burst at all. It also stopped a crowded single tank from
letting two fish overlap (0.013, under the 0.02 invariant floor) — sustained forward drive during
a shallow "glide" was closing head-on approaches that positional separation resolves too late.
That failure mode is Tier 2's anticipatory-separation item showing through.

**Lesson for the rest of this roadmap:** these metrics are diagnostic, not objectives. Maximising
one is how the hop got shipped. Read them to explain what the eye reports, and to catch
regressions — not as a target function.

Nearest-neighbour bearing stays near-uniform (~0.38 forward fraction against 0.375 for a flat
ring) — expected, and precisely what Tier 2's field-of-view work exists to change. The metric is
in place to measure it when that lands.

The tuning loop is: change one term → run the harness over the seed × domain matrix → read the
table. The existing invariant tests stay as guardrails throughout:

- zero backstop engagements (soft containment stays soft),
- bounded accel and jerk (no darting, no snapping),
- no mirror-flip storms,
- and the single-tank parity/golden tests, which must remain **bitwise** green.

`VoxelGoldenTrajectoryTest`'s fixture is the one that legitimately changes with any planar
behaviour work — regenerate it deliberately (delete the file, run the suite twice), never to make
a red test green.

---

## 4. Tier 2 results

Measured 2026-09-10 over a 7-config matrix (1×1×1 n=6/12, 3×1×1 n=8, 3×1×3 n=8/12, 2×2 slab n=12,
L n=12), 6000–12000 ticks, 200-tick warmup. All numbers come from a throwaway probe built the way
§5 of the Tier 2 handoff prescribes; the findings that survived are now permanent tests.

### 4.1 Nonholonomic steering — implemented, as a turn-rate cap

The plan was to replace free 3D acceleration with forward thrust + a yaw-rate command. The
measurement in §1.4 argued against that: the artifact is real but bounded, and the rewrite would
have forced re-deriving both load-bearing wall couplings, re-expressing wall avoidance as a turn
command, and retuning containment from scratch — for an artifact affecting a few percent of ticks.

What shipped instead achieves the same guarantee at a fraction of the risk. After the `maxForce`
clamp, the **turning** component of the steering acceleration — the part perpendicular to the
current travel direction — is capped at what the fish's turn rate allows for its speed (`a = v·ω`).
Forward thrust and braking pass through untouched, which is precisely why the two couplings in the
handoff's §3 survive unchanged: a fish that can no longer sidestep glass still *decelerates* into
it, and the wall term still commands the turn away. Since the sprite yaw uses the same rate, sprite
and travel agree by construction rather than by coincidence.

| | before | after |
|---|---|---|
| travel-direction turn rate, worst (under way) | 13–20°/tick | **8.4–9.5°/tick** |
| travel-direction turn rate, p99 | 6.2–10.1°/tick | 6.5–7.6°/tick |
| sideslip p95 (1×1×1 n=6) | 21.1° | **8.0°** |
| sideslip p95 (3×1×1, L) | 4.6°, 3.9° | 0.05°, 0.0° |
| mean sideslip | 0.28–3.03° | 0.10–1.47° |
| backstop engagements | 0 | **0** |

The residual above 7°/tick is the two deliberate allowances: the ±12% trait jitter on turn rate,
and the low-speed floor below which the cap stops shrinking (a fish that slow is pivoting, not
turning). `VoxelDomainTest.travelDirectionNeverOutrunsTheTurnRate` locks the bound.

Polarization improved as a side effect — bounded curvature means a fish holds its course — most
visibly in the crowded single tank (Φ 0.264 → 0.827 at n=12).

**Bank** is now a genuine lean: `clamp(turn * bankMax/turnRate, ±bankMax)`, full lean at full turn
rate and proportional below. The old fixed 1.5°-per-degree constant saturated through any sustained
turn because `diff` was pinned at the limit; with the trajectory itself rate-limited the yaw error
stays small and the term spends its time in the proportional region.

### 4.2 Field of view — implemented, measured inert, reverted

A rear blind cone (`fovBlindDeg`) plus front-weighted neighbour influence (`fovFrontBias`) went in
as specified: a filter in `findNearestSwimmers` excluding neighbours astern, and weighted rather
than plain means for alignment and cohesion. Separation was deliberately left unfiltered — a fish
feels crowding through its lateral line whether or not it can see it.

It did not work, and the sweep is unambiguous. Blind arc 0→200°, bias 0→1, pooled over the matrix:

| blind | bias | nnFront | Φ | mill | worst minPair |
|---|---|---|---|---|---|
| 0 (off) | — | 0.3792 | 0.670 | 0.271 | 0.0539 |
| 60 | 0.0 | 0.3948 | 0.580 | 0.374 | 0.0318 |
| 90 | 0.6 | 0.3930 | 0.620 | 0.304 | 0.0402 |
| 110 | 0.6 | 0.3882 | 0.643 | 0.295 | 0.0425 |
| 150 | 0.3 | 0.3774 | 0.567 | 0.360 | 0.0303 |
| 200 | 0.3 | 0.3750 | 0.560 | 0.291 | 0.0223 |

`nnFront` never leaves 0.375–0.405 (0.375 is exactly the no-structure value for a flat ring).
Polarization is best with the term **off**. Worst-case pairwise spacing degrades, and one setting
even produced hard-backstop engagements.

**The control experiment is the important part.** Before concluding the term was at fault, the same
metric was ablated against every other flocking term in the model:

| variant | nnFront | Φ | meanNN |
|---|---|---|---|
| shipped | 0.3882 | 0.643 | 0.252 |
| no flocking at all | 0.3894 | 0.472 | 0.323 |
| cohesion ×4 | 0.3933 | 0.564 | 0.247 |
| alignment ×3 | 0.3810 | 0.683 | 0.249 |
| no separation | 0.3943 | 0.601 | 0.150 |
| separation radius ×2 | 0.3924 | 0.626 | 0.386 |

Shoal spacing swings **2.6×** and polarization swings 0.47 → 0.68, and `nnFront` still does not
move. It is not measuring behaviour. At tank scale — 6–12 fish in a 0.7–2.7 block box — which
neighbour is geometrically nearest is set by packing against walls, not by preference, so a
*perception* filter cannot move it. Moving it would need a positional term (station-keeping off the
shoulder of the fish ahead), which is a different feature.

Both the FOV term and its two tunables were reverted rather than left dormant at a neutral value:
a hot-loop branch and two knobs that measurably do nothing are a maintenance cost with no payoff.
The finding is preserved here instead.

### 4.3 Anticipatory separation — implemented

Distance-only repulsion is a lagging controller: it cannot distinguish a fish closing head-on at
twice cruise from one drifting past at the same range, and treats them identically. Over a horizon
(`separationLookahead`, 1.0 s) each fish now also extrapolates the neighbour's current *relative*
velocity to the predicted point of closest approach and repels from that offset, weighted linearly
by how soon it arrives. It folds into the existing separation loop — no second O(n²) pass — and
collapses to the present-position term as that time goes to zero, so it strictly adds lead rather
than changing the steady state.

Horizon sweep, worst closest approach anywhere in the matrix, at `separationSpeed` 0.25:

| lookahead | 0 (off) | 0.5 s | 1.0 s | 2.0 s |
|---|---|---|---|---|
| worst minPair | 0.0502 | 0.0284 | **0.0854** | 0.1008 |

Note the **dip at 0.5 s**: a short horizon is *worse than none*, because it fires often enough to
disturb the shoal but too late to resolve the approach. Anyone tuning this should not read the
curve as monotonic.

Strength moved the opposite way to intuition — at a 1 s horizon, worst-case spacing was 0.085 at
`separationSpeed` 0.25 against 0.042 at 0.40. A hard shove applied late scatters a crowd into fresh
conflicts; a gentle one applied early does not need to be hard. `separationSpeed` accordingly came
**down from 0.40 to 0.25**, which is what the Tier 2 plan predicted this term would permit.

On `VoxelDomainTest`'s own matrix the worst closest approach went **0.037 → 0.072**, so
`MIN_PAIRWISE_FLOOR` was *tightened* from 0.02 to 0.035.

**The cost, and the thing to look at in game.** The 1-block tanks shift from a polarized school to
a **milling torus** — 1×1×1 n=12 goes Φ 0.785 / M 0.156 to Φ 0.178 / M 0.892. Multi-block domains
barely move (3×1×3 n=12: Φ 0.636 → 0.618; 2×2 slab: 0.685 → 0.674), so this is specific to a tank
too small to sustain a straight-line school.

That may well be *more* realistic — twelve fish in a 0.7-block cube all pointing the same way reads
as a rigid block, and circulation is what real fish do in a small tank — but it is a visual
judgment a metric cannot make, and it is the single thing worth checking first in the acceptance
pass. It was verified **not** to be an artifact of the implementation: the head-on tie-break rule
(each fish swerving to its own starboard, which would impose a systematic chirality and hence a
rotation) was instrumented and fired **zero** times in 42k fish-ticks, an exact head-on being a
measure-zero event in float arithmetic. That branch was removed as unreachable; the guard remains
as a divide-by-zero check. The torus is genuinely emergent.

If it reads badly, `separationLookahead` backs it off as a single knob — the term degrades
gracefully toward the old behaviour at 0.

### 4.4 What Tier 2 cost the parameter set

Two new `GROUP` tunables (`turnRateDegPerTick` 7, `separationLookahead` 1.0), one retuned
(`separationSpeed` 0.40 → 0.25). Both new terms are neutral-valued in `DEFAULT` and skipped by the
engine at that value, so the binary single-tank model stays bitwise-locked — `GoldenTrajectoryTest`
and `ParityTest` never needed regenerating. The voxel golden fixture was regenerated twice, once
per deliberate planar change.

`Tunables`' 24 hand-written 35-argument wither methods were replaced by a single mutable mirror
(`Tunables.Mut`) plus one-line withers, because Tier 2 adds fields and each one used to cost a
24-method edit with 24 chances to transpose two floats. `TunablesWitherTest` now asserts by
reflection that every wither changes exactly the component its name promises — a property nothing
checked before.
