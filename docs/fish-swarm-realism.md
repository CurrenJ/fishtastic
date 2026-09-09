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

### 1.4 Steering is holonomic; only the *sprite* is rate-limited

`PLANAR_TURN_RATE = 7°/tick` caps `yawDeg`, but the velocity vector can swing as fast as
`maxForce` allows. During wall avoidance or a separation shove a fish visibly translates sideways
or slightly backwards relative to where it points. Fish cannot do that.

Bank compounds it: `clamp(turn * 1.5, ±bankMax)` with `turn ≤ 7` and `bankMax = 10` saturates on
essentially every turn, so bank reads as binary rather than as a lean proportional to the turn.

### 1.5 No perception asymmetry, no individuality

Neighbour selection is isotropic nearest-k — no rear blind cone, no forward weighting, so the
whole school reacts simultaneously instead of propagating a turn. And every fish shared one
`Tunables`: identical top speed, cruise, and turn rate. Nothing broke lockstep.

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

- **Nonholonomic steering.** Forward thrust + a yaw-rate command; velocity derives from the
  committed heading. Fish stop sliding sideways, sprite and travel direction agree by
  construction, bank becomes a genuine continuous function of yaw rate, and `PLANAR_TURN_RATE`
  disappears as a special case.
- **Field of view with a rear blind cone** (~90–120° astern) plus front-weighted neighbour
  influence. A cheap filter in `findNearestSwimmers`; produces leader-follower chains and
  travelling turn waves instead of simultaneous reaction.
- **Anticipatory separation.** Weight repulsion by time-to-closest-approach rather than raw
  distance, so head-on pairs deflect early and gently — which then permits lowering
  `separationSpeed` back toward a natural value.
- **Per-species behaviour profiles.** A Couzin-style zone shape (repulsion / alignment-band width
  / attraction) on `FishProfile`, so a tetra shoals tight and polarized while an angelfish drifts
  loosely and a solitary species barely flocks. `FishSpec.species()` and `findNearestSwimmers`
  already carry the plumbing; this is mostly datapack surface.

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
| **NN bearing distribution** | histogram of nearest-neighbour bearing in the fish's own frame | front/side preference, not the uniform ring an isotropic model gives |

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
