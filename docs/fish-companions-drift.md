# Fish companions: drifters rework (design spec)

Status: spec written 2026-10-12, owner answers in §11. Phases 0–1 built and merged (§12–§13); Phase 1 accepted by the owner in the viewer (§11). Phase 2 (jet) built in an isolated worktree, merged, and accepted by the owner in the viewer on 2026-10-13 (§14). Next: Phase 3 (bob). Builds on the companion sim
(docs/fish-companions-sim-plan.md; DRIFT first cut and its open problems in §9.3, parked in §9.6).
Quality bar: the same as FREE_SWIM and the benthic ground follow (docs/fish-companions-benthic-follow.md).

## 1. The idea

Three species are DRIFT (`upright_float`): glass squid (40 cm, ghost), flapjack octopus (28 cm,
ghost), Portuguese man o' war (40 cm, aggressive). Today they share the first-cut `DriftBody`, a
pulsed bell, and a swimmer's mind riding behind one shoulder. The bell is the wrong model for the
squid (a jetter) and the man o' war (no propulsion), and even as a bell it lags after stops and
turns (§9.3: strokes overshoot, it can't brake, reversing swings through upright for 1.5–2.7 s).

The rework gives each species a body that fits it, fixes the three §9.3 problems in a shared core,
and gives drifters their own desires and in-view beats, on par with FREE_SWIM and the benthics.

## 2. Drift styles **(owner: data field)**

`fish_profile` gains an optional `companion_drift`:

```json
"companion_drift": "bell"   // bell | jet | bob ; absent → bell
```

| Style | Species | One line |
|---|---|---|
| `bell` | flapjack_octopus | pulsed bell, strokes sized to arrive, web-flare brake |
| `jet` | glass_squid | fins for hover/turning, mantle jets for travel and escape |
| `bob` | portuguese_man_o_war | float bobs; each bob shoves it along the sail heading **(owner: not realistic, but alas)** |

Plumbed like `companion_group`: codec on `FishProfile` → `ClientCompanion` → `CompanionAgent`; the
headless harness and viewer pick the style directly.

## 3. Shared drift core

All three styles keep: thrust only from the body's own actions (never carried, never lerped, no
tether); the hard per-axis resolve; the slow spin; the night glow. New for all three:

- **Strokes sized to arrive.** A stroke's impulse is the Δv still needed along its direction, less
  what drag will take over the coming coast, capped by the style's thrust. No more fixed ~3 b/s
  kicks overshooting the asked speed.
- **A brake.** Each style has a way to shed speed (below), so stopping is an action, not a wait.
- **Lead the owner.** Aim at where the owner will be over a short horizon, so a turning owner costs
  a lean, not a reversal.
- **Altitude floor.** Hold a style-specific clearance above the ground (sink compensation scaled
  up near the floor) so it doesn't scrape ground on `sprint_stop` and stairs.
- **Faster reorientation when slow.** The tilt rate scales up as speed falls (a hovering bell or
  squid can turn quickly; a fast one can't).

## 4. `bell` — flapjack octopus

The first cut's pulsed bell with §3 applied. Its brake is the **parachute**: it spreads its web,
drag rises sharply and it sinks a little, as real flapjacks do. The flare is visible (§8).

## 5. `jet` — glass squid

Two propulsion systems:
- **Fins:** weak thrust in any direction and fast yaw/pitch, for hovering, small corrections and
  reorientation. This is the answer to "reversing swings through upright".
- **Mantle jet:** strong pulses mantle-first, sized to arrive, for catch-up and escape. Mantle-first
  is correct travel for a squid: `tailFirstFraction` gets a per-style definition.
Brake: a short counter-fin flare plus a jet the other way when fast.

## 6. `bob` — Portuguese man o' war **(owner: bob propulsion)**

The float stays near upright (tilt ≤ ~20°). It bobs on a vertical cycle; each downstroke turns into
a horizontal shove along the sail's heading, like a buoy skipping across waves. Shove sized to
arrive (§3); bob rate and height rise with effort. Turning rotates the sail; the float rocks into
turns. Brake: tentacles let down (drag up). Its own top speed must hold walking pace. If a sprint
leaves it behind, it lags and then catches up, with the fade as the backstop, as for the benthics.

## 7. Desires and beats

On the existing `Mind` drives, varied per style:

| Drive | bell (flapjack) | jet (squid) | bob (man o' war) |
|---|---|---|---|
| Idle | slow rise-and-parachute rhythm | hangs head-down, fins fluttering | lazy bobbing, sail turning |
| Attention | rises into view from below, ear-fins flapping | jet pass across the view | bobs across the view |
| Curiosity | approach and hover | fins in, tentacles reaching (pose hint) | tentacles turn toward it |
| Wariness | parachutes down, flattens | escape jet | deflates, drops lower |
| Rest | **settles on the ground** (owner), the pancake | motionless hang | slow bob at ride height |
| Hurt | bell clench | escape jet | clench |

## 8. Shape and render

Liveliness through visible shape (the starfish swell is the bar), with a calm idle. Pulse stretch
visibly larger; squid mantle squeeze plus fin flutter; flapjack ear flaps, and a wide squash on the flare;
man o' war float wobble on each bob. Render-only tentacle lag along the sim velocity for all three.

## 9. Verification

- Headless first; an owner viewer gate per phase; an in-game solo playtest at the end.
- Equal-ground A/B per seed (8 seeds × 2 min × all scripts), with an on/off flag per change.
  Reject changes that shorten one tail and lengthen another.
- Metrics pair level with rate: overshoot ratio, reorientation time, ground clearance, speed
  reversals, plus the standard farthest / seen / contacts / impacts / penetrations / jerk.
- DRIFT rows join `CompanionInvariantTest` (per style); gaps that remain are named.
- Fingerprints: FREE_SWIM, GLIDE, BENTHIC, ANCHORED, PERCHER, TRIO stay bitwise. DRIFT changes.
- Don't touch `FlockEngine` (tank goldens).
- In-game debug trigger: `/fishtastic companion drift <beat>`.

## 10. Phases

| Phase | Builds | Gate |
|---|---|---|
| 0 | baseline of the first cut at HEAD; new metrics; `companion_drift` field plumbed; viewer/headless style picker; `DriftSeedSweep`-style probe | baseline file + metrics, fingerprints unchanged |
| 1 | shared core (§3) + `bell` | owner viewer |
| 2 | `jet` | owner viewer |
| 3 | `bob` | owner viewer |
| 4 | desires and beats (§7) | owner viewer |
| 5 | shape/render (§8), game integration, debug trigger, self-test scene | owner solo playtest |
| 6 | invariant table rows, named gaps, handoff doc | — |

Implementation runs in forks (owner, 2026-10-12): one worktree per phase, merged one at a time,
full suite rerun after each merge. As-built sections are pre-assigned: Phase N → §12+N.

## 11. Owner answers (2026-10-12)

1. Man o' war: bob propulsion. "Not realistic, but alas."
2. Style: a data field.
3. Flapjack rest: settles on the ground.
4. Order: bell first.

Phase 1 gate (2026-10-12, owner in the viewer):
- The surge (§13.6 item 3): "Surge looks good." The higher overshoot rate is accepted.
- Owner overlap: "it ends up inside me often when I walk or sprint ahead and stop, the drifter's
  momentum carries it through into my body." To be fixed (§13.8).
- Slow pivot left off: "This is fine for now."
- Idle: "Flapjack is too hyperactive when player is idling": fewer, slower idle moves (§13.7).
- After §13.7 and §13.8 (2026-10-12): "All fixes feel good on default viewer settings." Phase 1 accepted with
  its defaults.

## 12. Phase 0 as built (2026-10-12, uncommitted)

### 12.1 What's built

- **`companion_drift`** on `fish_profile`: `FishProfile.CompanionDrift` (`bell | jet | bob`, absent →
  `bell`), a nested `StringRepresentable` enum like `CompanionGait`. It's built eagerly in
  `FishProfile.CODEC`, so it needs no `WARMED_UP` entry. glass_squid `jet`, flapjack_octopus `bell`,
  portuguese_man_o_war `bob`. Test: `CompanionGroupConfigTest.parsesDriftStylesAndRejectsUnknownOnes`.
- **Sim side**: `DriftStyle` (`BELL | JET | BOB`). It is carried as `ClientCompanion` (DRIFT only)
  → a new public `CompanionAgent(..., gait, driftStyle, world)` → `DriftBody(t, style, ...)`. The
  body stores the style and doesn't read it: every style is still the first-cut bell.
  `CompanionAgent.driftStyle()`, `driftAsked()` (the mind's ask before wall avoidance) and
  `driftStrokeStart()` expose it for the harness.
- **Harness**: `CompanionRun.drifter(script, seed, style, temperament, tunables, record)`.
  `Sample` gained `asked` and `strokeStart`. `CompanionRun.DriftMetrics` / `driftMetrics()`
  (§12.2). Tail-first goes through `CompanionRun.tailFirst(sample, class, style)`; the JET
  branch is a stub on the bell's axis until Phase 2.
- **Tools**: `runCompanionHeadless -PsimArgs="--drift-style bell|jet|bob"` (implies DRIFT, files
  prefixed `drift_<style>_`). The viewer's class dropdown has "DRIFT bell (flapjack)", "DRIFT jet
  (squid)" and "DRIFT bob (man o' war)". `DriftSweepProbe` (`-PdriftStyle`, `-Psweep`,
  `-PtweakA/B`, `-PsweepTemperament`, `-PsweepSeeds`, `-PsweepTicks`, `-PsweepPerSeed=1`).
  `FingerprintProbe -PfpAll` adds `DRIFT_JET` / `DRIFT_BOB` lines.

### 12.2 New metrics (each a level paired with a rate)

| Metric | Definition |
|---|---|
| overshoot | per pulse cycle (settled and contact-free throughout, mean ask ≥ 1 b/s): peak speed ÷ mean asked speed; mean, worst, and cycles over 1.15 per minute |
| reorientation | a swing is the ask turning > 90° within 1 s; time from the swing until thrust (> 1 b/s²) points within 45° of the new ask (horizontal for a mostly horizontal ask); mean, worst, swings per minute |
| ground clearance | underside to the floor under it (`columnFloor`, 4 deep), settled ticks; min, share under 0.15, dips under 0.15 per minute |
| reversals | velocity turning > 135° within 1 s, both ends > 1 b/s (1 s refractory); per minute, and the mean Δv across one |

### 12.3 Gates

- `FingerprintProbe -PfpAll=1`: all 516 lines bitwise identical to the HEAD baseline
  (`build/fp-base-drift.txt`, untracked, main tree), DRIFT included. The 138 new `DRIFT_JET` /
  `DRIFT_BOB` lines hash identical to the plain DRIFT lines, which proves the style is plumbed and ignored.
- Fork: `:fishsim:test` (364, 0 failed), `:common:test`, `:fabric:build`, `:neoforge:build` green.
  After merging into the main tree: `:fishsim:test :common:test` 365 tests, 0 failed.

### 12.4 First-cut baseline (8 seeds × 2 min, baseline temperament)

Per-seed lines: `build/drift-baseline.raw.txt` (untracked, main tree). Selected columns, mean | worst
(worst is the minimum for seen% and clearance). Penetrations are 0 on every script.

| script | farthest s | seen % | contacts | max impact b/s | tail-first % | overshoot | reorient s | min clearance |
|---|---|---|---|---|---|---|---|---|
| walk | 9.8 \| 10.5 | 3 \| 2 | 0 | 0 | 2.8 \| 3.8 | 0.98 \| 1.02 | 1.49 \| 1.68 | 0.35 |
| walk_turns | 15.5 \| 21.5 | 6 \| 3 | 0 | 0 | 5.0 \| 6.0 | 0.87 \| 0.96 | 1.83 \| 2.12 | 0.31 \| 0.10 |
| sprint_stop | 8.1 \| 12.2 | 18 \| 11 | 0 | 0 | 4.0 \| 5.2 | 0.99 \| 1.04 | 0.88 \| 1.20 | 0.33 \| 0.20 |
| about_face | 26.5 \| 43.7 | 14 \| 10 | 0 | 0 | 5.6 \| 9.0 | 0.53 \| 0.58 | 1.75 \| 2.45 | 0.35 |
| idle_fixed | 2.3 \| 3.9 | 13 \| 7 | 1 \| 4 | 0.16 \| 1.25 | 13.8 \| 16.2 | 1.03 \| 1.30 | 3.39 \| 6.92 | 0.26 \| 0.00 |
| idle_looking | 2.5 \| 7.5 | 17 \| 5 | 4 \| 12 | 0.54 \| 1.25 | 12.2 \| 15.4 | 1.03 \| 1.31 | 4.07 \| 6.26 | 0.14 \| 0.00 |
| camera_sweep | 1.7 \| 3.4 | 7 \| 4 | 2 \| 15 | 0.41 \| 1.11 | 13.3 \| 15.5 | 1.22 \| 1.46 | 2.76 \| 4.11 | 0.19 \| 0.00 |
| corridor | 17.9 \| 32.7 | 5 \| 3 | 35 \| 61 | 4.90 \| 5.94 | 2.9 \| 5.5 | 0.81 \| 0.90 | 0.88 \| 1.63 | 0.01 \| 0.00 |
| doorway | 15.5 \| 23.7 | 4 \| 2 | 12 \| 22 | 5.88 \| 7.09 | 4.6 \| 6.1 | 0.70 \| 0.76 | 1.27 \| 1.70 | 0.05 \| 0.00 |
| forest | 41.7 \| 66.2 | 5 \| 2 | 48 \| 80 | 5.83 \| 7.12 | 4.9 \| 7.4 | 0.60 \| 0.68 | 1.32 \| 1.83 | 0.00 |
| stairwell | 40.2 \| 66.4 | 9 \| 4 | 56 \| 84 | 6.02 \| 7.53 | 5.0 \| 10.3 | 0.36 \| 0.38 | 1.35 \| 2.37 | 0.15 \| 0.00 |
| low_ceiling | 5.5 \| 12.0 | 17 \| 7 | 73 \| 84 | 5.19 \| 5.99 | 5.9 \| 8.3 | 1.13 \| 1.24 | 1.09 \| 1.46 | 0.12 \| 0.00 |
| pond_dive | 12.4 \| 15.9 | 4 \| 2 | 4 \| 5 | 1.97 \| 3.07 | 7.3 \| 8.8 | 0.68 \| 0.71 | 1.71 \| 1.99 | 0.28 \| 0.00 |
| held_items | 7.4 \| 13.2 | 13 \| 10 | 3 \| 13 | 0.70 \| 1.30 | 11.3 \| 16.6 | 1.09 \| 1.15 | 2.22 \| 2.67 | 0.14 \| 0.00 |
| companion_passes | 7.4 \| 13.5 | 10 \| 7 | 1 \| 5 | 0.08 \| 0.68 | 10.8 \| 12.7 | 1.02 \| 1.12 | 2.31 \| 4.19 | 0.31 \| 0.00 |

Jerk is ≈ 320–360 b/s³ on every script (item 6 below). The other scripts and the rate columns are in
the raw file.

### 12.5 What the baseline says for Phase 1

1. **Visibility is the biggest gap.** Seen only 2–6% on walking scripts (FREE_SWIM 16–30%): riding
   behind the shoulder (`alongOffset` −0.7) plus slow catch-up keeps it out of view. Where it rides
   is a Phase 1 (or Phase 4) question, not only stroke physics.
2. **Overshoot is a tail, not the mean.** The mean peak÷ask is ≈ 1.0 in the open, but ~25% of strokes
   overshoot (~18/min on `walk`, single strokes to 4.4×); obstacle and reversing scripts fall
   *short* (forest 0.60, about_face 0.53, stairwell 0.36). Strokes sized to arrive must fix both ends.
3. **Turning is the dominant lag.** Swing recovery is 1.3–2.4 s on average and up to 4.2–6.9 s in the
   worst case; about_face has the most reversals (~10/min). Supports the faster pivot when slow (§3).
4. **Ground scraping is limited to tight scenes and loitering**; open clearance holds 0.31–0.37. The
   altitude floor is a smaller job than §9.3 implied.
5. **Two invariants are blind for drifters**: `maxFacingErrorDeg` reads ~180° (yaw holds the tilt
   direction while upright) and `maxTurnDeg` is 0. DRIFT rows need a tilt-rate measure and a
   drift-specific facing measure.
6. **Jerk ≈ 350 b/s³ is the stroke itself** (the sin² rise of 32 b/s² over 0.28 s). The bound must
   come from the stroke envelope, or be measured outside stroke onsets.
7. **Tail-first is highest while idle** (12–14%): it coasts past its loiter spot while the bell
   swings round, which is a braking problem. The web-flare brake should address it.
8. **teleport / creative_flight show 23–30 swings/min**, mostly from fades and re-placing: gate on
   settled-since-fade before trusting reorientation there.

Out of scope, noted: `tools/fish-profile-studio.html` doesn't know `companion_drift`.

## 13. Phase 1 as built (2026-10-12, uncommitted)

The shared drift core (§3) and the `bell` style (§4). JET and BOB still run this body: their own
bodies are Phases 2 and 3. Everything below is a switch on `CompanionTunables` (all on = the adopted
body; all off = the first cut, bitwise: the Phase 0 sweep reproduces exactly). Measured with
`DriftSweepProbe`, 8 seeds × 2 min × 23 owner scripts, per seed against the stack before it.

### 13.1 What's built

- **Retiming** (`driftRetime`). Once a squeeze is over, a cycle ends early when the cycle it would
  pick now is already over (it has started hurrying), or the stroke it wants is much bigger than the
  last (≥ 1.5× and +1 b/s, not within 0.3 s). The first cut waited out a 2.8 s idle cycle with its
  owner walking off: that was most of its start-up lag.
- **Strokes sized to arrive** (`driftArrive`, `driftArriveGain` 0.9). A stroke is a velocity change:
  the cycle's drag-and-sink loss plus 0.9 of the gap to the ask, along the bell. The first cut
  multiplied the continuous correction by the whole cycle (4× the gap at an idle cycle). A stroke
  more than 25° off the line of what's needed is faded out (gone by 60°), so a bell still leaning
  round doesn't throw itself up and sideways.
- **The web flare** (`driftFlare`; drag +2.5/s, sink +0.8 b/s² at full flare, full at 2 b/s of
  excess). It flares by how much faster it is going than it wants to go that way (0.3 b/s dead
  band), opens at 4/s, folds at 5/s, and can't flare mid-squeeze. `DriftBody.flare` /
  `CompanionAgent.flare()` expose it (0..1) for the animator; the viewer's bell spreads and flattens
  with it.
- **Braking by room** (`driftRoomBrake`). No stroke carries it faster along the bell than its flare
  could stop in the open space ahead (k = full-flare drag × free distance, looked at to 3 blocks),
  and it flares when it is already faster than that along its travel.
- **No climbing it didn't ask for** (`driftNoClimb`). A stroke is cut so it leaves the bell rising no
  faster than the ask + half a cycle's sink + 0.3 b/s. A bell can't push down, so unwanted lift took
  seconds of sinking to undo: catch-up strokes fired at a 50–60° lean put it 3+ blocks above the
  owner's head, out of view.
- **Height hold** (`driftHoldHeight`). Near upright (axis ≥ 0.5 vertical) a stroke may always be as
  big as holding height takes (0.5 s of sink plus the asked vertical speed), even when where it
  wants to go is outside the arrive gate. Without it a reversing bell sank to the ground on its way
  through upright.
- **Not adopted, left switchable for the viewer:** `driftSlowPivot` (0) / `driftSlowPivotSpeed` /
  `driftPivotSwingOnly` — a faster tilt when slow, optionally only while reversing (§13.2).
- **Harness:** `CompanionRun.DriftBodyMetrics` / `driftBodyMetrics()` (the most the axis turned in a
  tick, coast jerk, stroke jerk, acceleration). `Sample` gained `flare`, `tilt`, `tiltRate`.
  `DriftSweepProbe` per-seed lines add the body metrics. New `DriftTraceProbe` (`-Ptrace`,
  `-PsweepSeeds` = the seed, `-PsweepTicks`, `-PtweakA`, `-PtraceEvery`): goal, place in the owner's
  frame, speed vs ask, tilt, thrust, flare, seen, contact, impact, inside the owner / near clip.

### 13.2 Every change, A/B per seed (seeds better / worse; noise floors per metric)

| Change | On | Evidence | Verdict |
|---|---|---|---|
| arrive | first cut | far +133/−12, seen +149/−16, tail-first +121/−28; but contacts +14/−115, impact +13/−128, clearance 0/−120 — it sat on the ground | adopted with retime |
| retime alone | first cut | far +60/−93 | not alone |
| retime | + arrive | contacts +122/−12, impact +137/−23, clearance +134/−0, overshoot +151/−17, reorient +155/−16; tail-first +40/−110 (strokes into its own wake on reversals) | adopted (the flare fixes the tail) |
| flare | + arrive + retime | tail-first +164/−0, reversals +149/−1, far +87/−27; seen +59/−78, corridor contacts 8 seeds worse | adopted |
| flare without its sink | + flare | far +34/−39, contacts +20/−17 | rejected |
| floor (0.2–0.3 clearance ask) | various | +2/−2 at most: never fires | rejected, removed |
| lead the owner 0.4 s | + flare | walk farthest 7.7 → 57.8 s, reversals +2/−39 | rejected, removed |
| slow pivot 0.75 / 1.5 (all speeds) | + flare | far +99/−16, reorient +109/−44; overshoot rate +3/−134, doorway impact 4.0 → 8.2 | rejected |
| hold share 0.5 / 0.75 (pay part of the drag loss) | several stacks | overshoot rate +102/−13; seen +39/−91, tail-first −, clearance − | rejected, removed |
| braking by room | + flare | impact +35/−4 (doorway 4.0 → 2.2, low_ceiling 3.4 → 1.1, stairwell 6.2 → 3.7), contacts +29/−10; stairwell grazes 47 → 67 | adopted: harder hits for gentler touches (named) |
| pivot only while reversing, 1.5 | + room | far +95/−14, catch-up +107/−12, reorient +116/−34; overshoot rate +14/−126, doorway impact 2.2 → 3.9 | rejected (switchable) |
| ride position: along 0; along 0 + 0.85 out; along +0.4 + 0.85 out | + room | all within noise (far +26/−32 …): on a walk it is catching up 76% of the time and rarely reaches the station | rejected, removed |
| no-climb | + room | unseen +91/−64, seen +81/−62 (walk 2 → 9%), far +58/−46 (forest 14 → 8 s), catch-up +71/−26; clearance +38/−101 (sags to the ground) | adopted with height hold |
| height hold, full cycle's sink | + no-climb | clearance +151/−9; catch-up +25/−68 (floats up) | revised |
| height hold, 0.5 s of sink | + no-climb | far +66/−33, unseen +83/−50, seen +80/−51, contacts +48/−5, impact +57/−21, clearance +126/−21; overshoot rate +19/−63 | adopted |
| owner braking (share 1.0, 0.4) | final | inside-owner +47/−55 and +40/−63 (jumping 28 → 85 ticks) | rejected, removed |
| swing pivot 1.5 | final | far +82/−23, reorient +119/−28, catch-up +79/−12; doorway impact 1.8 → 3.3, about_face seen 30 → 22, overshoot rate −78 | rejected (switchable) |

**The one call against the rule.** No-climb + height hold, as a package against the room stack,
win on every outcome (far +68/−33, unseen +106/−48, seen +95/−55, contacts +41/−6, impact +31/−23,
tail-first +37/−6, clearance +111/−43, catch-up +76/−20) but lengthen the overshoot-rate tail
(+30/−90; walk 11 → 38 strokes/min over 1.15× the ask) and reorientation is even (+70/−80). Every
change that lets strokes land does this, and paying less of the loss up front (hold share < 1)
cost visibility on every stack: the surge above the ask is what carries it alongside and into
view. The harmful kinds of overshoot — reversals (now ≈ 0/min) and overshooting past the owner —
are gone. Adopted, and flagged for the owner's look (§13.4 item 3).

### 13.3 Before and after (8 seeds × 2 min, baseline temperament; mean | worst)

| script | farthest s | seen % | contacts | impact b/s | tail-first % | overshoot | reorient s | clearance |
|---|---|---|---|---|---|---|---|---|
| walk | 9.8\|10.5 → 6.7\|8.0 | 3\|2 → 10\|8 | 0 → 0 | 0 → 0 | 2.8\|3.8 → 0.0\|0.1 | 0.98\|1.02 → 0.96\|0.99 | 1.5\|1.7 → 1.5\|2.1 | 0.35 → 0.72\|0.42 |
| walk_turns | 15.5\|21.5 → 7.2\|7.7 | 6\|3 → 8\|2 | 0 → 0 | 0 → 0 | 5.0\|6.0 → 0.2\|0.5 | 0.87\|0.96 → 0.85\|0.90 | 1.8\|2.1 → 1.0\|1.5 | 0.31\|0.10 → 0.60\|0.45 |
| sprint_stop | 8.1\|12.2 → 6.8\|7.9 | 18\|11 → 14\|7 | 0 → 0 | 0 → 0 | 4.0\|5.2 → 0.1\|0.5 | 0.99\|1.04 → 1.02\|1.05 | 0.9\|1.2 → 0.9\|1.2 | 0.33\|0.20 → 0.44\|0.39 |
| about_face | 26.5\|43.7 → 4.8\|5.3 | 14\|10 → 30\|27 | 0 → 0 | 0 → 0 | 5.6\|9.0 → 0.0\|0.0 | 0.53\|0.58 → 0.40\|0.45 | 1.8\|2.5 → 2.2\|2.6 | 0.35 → 0.82\|0.76 |
| idle_fixed | 2.4\|3.9 → 0 | 13\|7 → 47\|41 | 0\|4 → 0 | 0.2\|1.2 → 0 | 13.8\|16.2 → 0 | 1.03\|1.30 → 0.77\|1.15 | 3.4\|6.9 → 1.0\|1.8 | 0.26\|0.00 → 0.55\|0.47 |
| idle_looking | 2.6\|7.5 → 0 | 16\|5 → 36\|17 | 4\|12 → 0 | 0.5\|1.2 → 0 | 12.2\|15.4 → 0 | 1.02\|1.31 → 0.90\|1.14 | 4.1\|6.3 → 1.0\|2.3 | 0.14\|0.00 → 0.40\|0.28 |
| camera_sweep | 1.7\|3.4 → 0 | 7\|4 → 21\|14 | 2\|15 → 0 | 0.4\|1.1 → 0 | 13.3\|15.5 → 0 | 1.22\|1.46 → 0.99\|1.30 | 2.8\|4.1 → 0.9\|1.5 | 0.19\|0.00 → 0.42\|0.31 |
| corridor | 17.9\|32.7 → 17.7\|18.6 | 5\|3 → 3\|1 | 35\|61 → 16\|25 | 4.9\|5.9 → 2.3\|3.2 | 2.9\|5.5 → 0.1\|0.3 | 0.81\|0.90 → 0.58\|0.63 | 0.9\|1.6 → 0.8\|1.3 | 0.01 → 0.04\|0.00 |
| doorway | 15.5\|23.7 → 5.5\|6.1 | 4\|2 → 11\|5 | 12\|22 → 1\|2 | 5.9\|7.1 → 1.8\|3.4 | 4.6\|6.1 → 0.1\|0.5 | 0.70\|0.76 → 0.86\|0.95 | 1.3\|1.7 → 0.9\|1.1 | 0.05\|0.00 → 0.48\|0.25 |
| forest | 41.7\|66.2 → 6.6\|11.2 | 5\|2 → 8\|3 | 48\|80 → 3\|7 | 5.8\|7.1 → 3.7\|6.8 | 4.9\|7.4 → 0.0\|0.2 | 0.60\|0.68 → 0.83\|0.91 | 1.3\|1.8 → 0.7\|1.1 | 0.00 → 0.57\|0.23 |
| stairwell | 40.2\|66.4 → 13.6\|15.5 | 9\|4 → 7\|5 | 56\|84 → 58\|76 | 6.0\|7.5 → 4.6\|6.5 | 5.0\|10.3 → 0.2\|0.7 | 0.36\|0.38 → 0.36\|0.40 | 1.4\|2.4 → 1.5\|2.1 | 0.15\|0.00 → 0.00 |
| low_ceiling | 5.5\|12.0 → 5.7\|10.8 | 17\|7 → 11\|1 | 73\|84 → 6\|10 | 5.2\|6.0 → 1.5\|5.6 | 5.9\|8.3 → 0.5\|1.4 | 1.13\|1.24 → 0.76\|0.87 | 1.1\|1.5 → 0.9\|1.3 | 0.12\|0.00 → 0.37\|0.00 |
| pond_dive | 12.4\|15.9 → 4.3\|6.8 | 4\|2 → 4\|1 | 4\|5 → 3\|9 | 2.0\|3.1 → 2.2\|5.9 | 7.3\|8.8 → 0.4\|1.4 | 0.68\|0.71 → 0.67\|0.73 | 1.7\|2.0 → 0.8\|1.0 | 0.28\|0.00 → 0.18\|0.00 |
| held_items | 7.4\|13.2 → 3.1\|4.3 | 13\|10 → 49\|39 | 3\|13 → 0 | 0.7\|1.3 → 0 | 11.3\|16.6 → 0.2\|1.0 | 1.09\|1.15 → 0.85\|1.00 | 2.2\|2.7 → 1.2\|1.7 | 0.14\|0.00 → 0.45\|0.15 |
| companion_passes | 7.4\|13.5 → 3.6\|5.0 | 10\|7 → 34\|19 | 1\|5 → 0 | 0.1\|0.7 → 0 | 10.8\|12.7 → 0 | 1.02\|1.12 → 0.90\|1.00 | 2.3\|4.2 → 1.3\|2.0 | 0.31\|0.00 → 0.43\|0.19 |

Penetrations 0 everywhere, before and after. Over all 23 scripts (seeds better / worse): farthest
+150/−12, longest unseen +121/−45, seen +135/−29, contacts +57/−7, impact +67/−11, tail-first
+171/−0, reversals +183/−0, reorientation +142/−30, clearance +153/−14, catch-up share +175/−1;
overshoot rate +84/−84; **inside the owner +21/−113 and near the camera +24/−96** (§13.4). The
ghost and aggressive temperaments (the three drifters' own) show the same shape on equal seeds.

### 13.4 Invariants and named gaps

DRIFT BELL rows are in `CompanionInvariantTest` (every owner script × seeds 1–3), with drift-aware
body checks. They aren't the swimmer's turn/facing/jerk checks, which are blind for a bell:
`faceErr` reads ~180° because yaw holds the lean direction, and turn is 0.
- Axis turn per tick ≤ hypot(tilt rate, 2 × tilt rate) = 6.76° (measured ≤ 6.48).
- Acceleration ≤ thrust + sink + 2 = 34.6 (≤ 31.1).
- Stroke jerk ≤ π·thrust / (duty·fastest cycle) + 100 = 459, the sin² envelope's own (≤ 435).
- Coast jerk ≤ 200 (≤ 160).
- Tail-first ≤ 2% (≤ 1.6%; the first cut 3–17%).

Blocks, owner, visibility, keep-up and commitment are the swimmer's checks, with these named gaps
(8-seed worsts):
- `DRIFT_IMPACT_GAPS` forest 6.9, stairwell 6.5, pond_dive 5.9, low_ceiling 5.6, doorway 3.4 b/s
  (bound 7.5; the first cut reached 7.1–7.5).
- `DRIFT_CONTACT_GAPS` stairwell 76 gentle grazes / 2 min (bound 45/min); the tunnel 15/min.
- **Owner overlap, class-wide:** a bell can't dodge (no sideways or downward push), and the new body
  actually reaches its owner. An owner stopping in front of it, jumping up through it or turning
  into it overlaps it: up to 7.0% of ticks inside, 5.0% near the camera (sprint_stop), centre dwell
  52 ticks. Bounds 8.5% / 6.5% / 60. Owner braking measured worse; this belongs to the mind (spots
  and paths that don't run through the owner).
- `DRIFT_VISIBILITY_GAPS` walk_turns, sprint_stop, forest, teleport, low_ceiling, pond_dive, wading,
  creative_flight, doorway, elytra (+ about_face, stairwell): longest unseen 31–71 s or seen < 5%.
- `DRIFT_KEEP_UP_GAPS` stairwell 15.5, forest 11.2, low_ceiling 10.8, walk 8.0 s, the tunnel 18.6 s
  with up to 4 fades (it ends up on the tunnel's roof): bound 20 s, 5 fades in the tunnel.

Fingerprints: all 447 non-DRIFT lines bitwise identical to `build/fp-base-drift.txt`; all 69 DRIFT
lines changed (and the JET/BOB lines, same body). Gates: `:fishsim:test :common:test` (373, 0
failed, 29 skipped — the probes), `:fabric:build`, `:neoforge:build`.

### 13.5 What the work found for later phases

1. **Catch-up puts a trailing drifter behind the owner.** On walking scripts it is in CATCH_UP
   53–78% of the time (first cut 84–96%), and the catch-up destination is a point a block from the
   owner on the fish's own side: directly behind them. Where it rides (escort station) made no
   difference. A side-or-ahead catch-up destination for slow classes is probably the biggest
   visibility lever for all three styles (Phase 4).
2. **Reversals are the remaining lag.** Swinging back through upright is still ~2 s; the reversal
   pivot is the strongest lever measured but costs doorway impacts and overshoot rate. The squid's
   fins (Phase 2) are this pivot by design, so expect to need braking by room on fin turns too.
3. **No-climb and height hold are style-independent.** Any style whose thrust can point off its
   want needs them; the bob's downstroke-to-shove (Phase 3) must not turn into lift.
4. **Owner overlap** needs a mind-side answer before the bob (which can't dodge either); the jet's
   fins can.
5. The tunnel roof: in the 1-wide corridor the bell can end up on top of the tunnel block chasing an
   owner below (crumb routing), in the first cut too.

### 13.6 For the owner's viewer look (`./gradlew :fishsim:runCompanionViewer`, class "DRIFT bell (flapjack)")

1. **Starting off and stopping.** Walk off from standing: it should pulse away within a beat (no
   long wait), lean in, and keep up in strokes. Stop: it should flare (the bell spreads wide and
   flat, tentacles splay) and settle, not sail past you or pulse tentacles-first.
2. **Reversing.** Walk, turn round, walk back: it swings back through upright (~2 s) holding its
   height, then strokes after you. Toggle `driftSlowPivot` = 1.5 with `driftPivotSwingOnly` on to see
   the faster reversal that was measured better on lag but worse on doorway bumps and overshoot.
3. **The surge.** On a steady walk each stroke carries it a little past your pace, then it coasts
   back: is that the pulse-and-coast you want, or does it read as a hop? (This is what the
   overshoot-rate metric counts; §13.2.)
4. **Where it is.** It still often trails behind your shoulder while catching up (§13.5 item 1), and
   it can drift into you when you stop in front of it or jump (§13.4).
5. **Tight spaces.** Doorway and pillars: it should brake before walls rather than bonk them.

Headless GIFs: `./gradlew :fishsim:runCompanionHeadless -PsimArgs="--drift-style bell --script walk_turns"`
(also `walk`, `sprint_stop`, `about_face`, `doorway`; output in `fishsim/build/companion-export`,
files prefixed `drift_bell_`). Sweeps:
`./gradlew :fishsim:test --tests grill24.fishsim.companion.DriftSweepProbe -PsimStdout -PdriftStyle=bell -PsweepPerSeed=1 [-PtweakA=driftNoClimb=false]`.
Trace: `./gradlew :fishsim:test --tests grill24.fishsim.companion.DriftTraceProbe -PsimStdout -Ptrace=walk -PsweepSeeds=1 -PsweepTicks=600`.

### 13.7 Owner feedback: calmer idle (2026-10-12, uncommitted)

**The ask.** "Flapjack is too hyperactive when player is idling, can we reduce the frequency and
velocity of motion?"

**What made it busy (traced, `DriftIdleProbe` + `DriftTraceProbe`, idle_fixed).** Not the mind:
it only picked a new spot every ~8.5 s, a little less often than a swimmer. The body was the cause.
Every idle move asked for the full cruise (0.7 b/s), and the need to close that gap laid the bell
over to ~60°, then righted it once it got there. That rocked the bell through 60° on every move
(2,360°/min of tilt). On top of that it hung against a 0.6 b/s² sink. Each stroke paid the sink of
the whole ~2.3 s cycle up front (≈ 1.5 b/s per stroke), so it bobbed up and sank back down. The
result was a stroke every ~2 s (30/min) at 0.56 b/s mean speed, even while "dwelling".

**What's built.** Every change has its own switch on `CompanionTunables` and acts only on a
drifter. All but the lean act only while it idles by a still owner (LOITER, SETTLE). The lean acts
only while it idles and is asked to go slowly. Defaults are the adopted values. Pace, dwell and sink
at 1, steady false, calm tilt 85, idle pulse 0 and `driftCalmCatch` false together reproduce the
Phase 1 idle exactly (the probe's numbers match to the digit).

| Tunable (default) | What it does |
|---|---|
| `driftIdlePace` (0.5) | Idle moves between spots at half the cruise. The mind's half eases in over 1 s of the owner standing still. Leaving the middle of the view, it goes at full pace. |
| `driftIdleDwell` (2) | Lingers twice as long at each idle spot. |
| `driftIdleSteady` (on) | The idle ask no longer rises and falls with its own pulse (the swimmer's burst surge, fed back). |
| `driftCalmTiltDeg` (15), `driftCalmTiltSpeed` (1.5) | Idling and asked to go slowly, the bell leans at most 15°, rising to the full 85° by 1.5 b/s of ask, and scaled by how calm it is. It hangs nearly upright and lets its strokes carry it. Travelling, it leans as before. |
| `driftIdleSink` (0.2) | At rest a bell is near neutrally buoyant: its sink is a fifth of `driftSink`, so small, seldom strokes hold it where it is. Eased in over `driftIdleCalmSeconds` (2 s), and out four times faster. |
| `driftIdlePulseSeconds` (4.5) | Its slowest pulse cycle at rest (the travelling idle is 2.8 s). |
| `driftCalmCatch` (on) | A calm bell, or one still in a long idle cycle, that is falling ≥ 0.5 b/s faster than it wants strokes again at once instead of waiting out the cycle. Found by trace: out of a low swoop it fell 1.6 blocks onto the floor without a stroke. |

The calm pauses while the bell, or its spot, is in the middle of the owner's view (`centreTicks`).
From there it leaves at its travelling self. Without that it lingered on screen centre up to 117
ticks (bound 60).

**Results (8 seeds × 2 min; idle = the owner has stood still ≥ 1 s; "base" = LOITER/SETTLE ticks
only, i.e. not counting the reactions).**

| | Phase 1 | calm | FREE_SWIM |
|---|---|---|---|
| base speed, mean / p90 (b/s) | 0.56 / 0.91 | 0.29 / 0.52 | 0.49 / 0.99 |
| base strokes/min | 29.7 | 18.7 | — |
| base tilt swing (°/min) | 2,363 | 911 | — |
| idle moves/min | 7.0 | 4.3 | 8.2 |
| idle path (blocks/min) | 37.3 | 23.4 | 32.1 |
| velocity change per stroke (b/s) | 1.55 | 0.93 | — |
| seen while idle | 40% | 45% | 36% |
| ghost (the flapjack's own): base speed / strokes/min / moves/min | 0.49 / 28.3 / 4.8 | 0.27 / 18.2 / 3.7 | 0.24 / — / 5.2 |

On idle_fixed alone (baseline temperament) a stroke comes every ~3.6 s, at a calm 0.2–0.3 b/s,
leaning 20–35°. The attention drive still brings it through view (SHOW_OFF every ~20 s), and the
reactions (INSPECT, HIDE, SHOW_OFF) keep their Phase 1 liveliness.

**Equal-ground A/B, per seed.** Seeds better / worse; 56 idle runs (7 idle scripts × 8 seeds) and
56 travel runs (walk, walk_turns, sprint_stop, about_face, doorway, forest, stairwell). Each row
turns one switch back off on top of the adopted stack. "Worse" means turning it off made things worse.

| Off | idle (worse without it) | travel |
|---|---|---|
| all (= Phase 1) | speed, p90, strokes, tilt, path 56/0; moves 55/1; seen 36/16; inside owner 9/21 (better without) | far 24/19, unseen 25/17, impact 14/8, contacts 9/7: neutral to better |
| sink | speed 56/0, strokes 52/2, tilt 55/1, path 54/0 | impact 14/5, unseen 27/16 |
| idle pulse | strokes 56/0; but speed 9/36 (a longer cycle means bigger strokes) | contacts 12/5 |
| pace | speed 53/1, tilt 47/8, path 47/6 | impact 13/6 |
| calm tilt | tilt 56/0, speed 55/0, path 49/5 | far 22/10, impact 15/4 |
| dwell | moves 37/10 | identical |
| steady | tilt 53/2, speed 31/9 | impact 12/6 |
| calm catch | tilt 29/17, inside owner 16/11 | contacts 7/2 |

**Tried and dropped (each A/B'd).**
- A wider arrival radius for idle spots (0.6–0.7): more moves, not fewer.
- Shorter, flatter idle hops (hop × 0.6–0.7, height spread × 0.5): no gain.
- Waiting 1–3 s of stillness before calming (meant to spare the jumping script's 0.7–2 s pauses):
  worse on idle tilt and on travel unseen and impact.
- Judging strokes against the calm-capped direction: mixed. It was scoped away when the lean became
  idle-only.

**Named gaps and bounds moved (`CompanionInvariantTest`).**
- **Inside the owner while idle: ~0.45% → ~0.9% of idle ticks.** Most of it comes from the sink. A
  slow, near-buoyant bell drifting back to a spot can hang in the owner's chest for a few seconds.
  `driftIdleSink` 0.5 brings it back to Phase 1 (9.5 ticks vs 21 per run) at the cost of a third of
  the speed gain. This belongs with the class-wide owner-overlap work (another fork). Bounds unchanged.
- **jumping added to `DRIFT_VISIBILITY_GAPS`.** Longest unseen 25.6–29.1 s before, 18.7–44.9 s
  after (3 of 8 seeds over 40 s). The scripted sprints are catch-up parked behind the owner (§13.5
  item 1), and a second of calm in the short pauses changes which catch-up it starts from. No single
  switch accounts for it.
- **forest:** one fade in 2 min on 2 of 8 seeds (none before). New `DRIFT_LEASH_GAPS` allows 1.
- **corridor (1-wide tunnel):** one seed in 8 at 25 contacts/min (the rest 4.5–9). Tunnel contact
  bound 15 → 30/min.
- **Open scenes:** a single gentle floor graze (≤ 1 b/s) is allowed. A SHOW_OFF or INSPECT swoop
  that carries the bell low can't be pulled out of, since it can't push down. item_drops 0.76–0.90 b/s
  before and after; camera_sweep 0.93 b/s after.
- **Tail-first** 2% → 2.5%: one idle seed at 2.0% (cat_lounging).
- The reorientation metric reads long while idle (idle_fixed mean 0.97 → 4.9 s). That is by design:
  a calm bell doesn't lean to a slow ask.

**For the owner's viewer look** (`./gradlew :fishsim:runCompanionViewer`, class "DRIFT bell
(flapjack)"; stand still for 30 s+, then look away and back).
1. **Is it calm enough, or now too still?** It should hang nearly upright, pulse slowly (one every
   ~3.5–4.5 s) and drift between spots at walking-pace-in-water. Live sliders:
   - `driftIdlePace` lower = slower moves.
   - `driftIdleDwell` higher = lingers longer.
   - `driftIdlePulseSeconds` higher = fewer pulses, but each one bigger.
   - `driftIdleSink` lower = less bob.
   - `driftCalmTiltDeg` lower = more upright.
2. **Does it still feel alive?** Look for the swoops through your view (SHOW_OFF) and its reaction
   to a held item. Those are unchanged by design.
3. **Setting off:** walk away from a calm bell. It should wake within half a second and stroke after
   you as in Phase 1.
4. **Up close:** watch whether it drifts into your chest while you stand still (the gap above). If it
   does and bothers you, try `driftIdleSink` 0.35–0.5.

Probes:
- `./gradlew :fishsim:test --tests grill24.fishsim.companion.DriftIdleProbe -PsimStdout [-PsweepTemperament=ghost] [-PtweakB=...] [-PtweakList=a/b/...]`
- `./gradlew :fishsim:test --tests grill24.fishsim.companion.DriftCalmABProbe -PsimStdout -PabList=driftIdleSink=1/... [-PabTravel=jumping,...]`

### 13.8 Owner feedback: no coasting into the owner (2026-10-12, uncommitted)

Owner (Phase 1 viewer): "it ends up inside me often when I walk or sprint ahead and stop, the
drifter's momentum carries it through into my body."

**Mechanism (traced, sprint_stop seed 1).** Catch-up arrives on the owner's trail line, a block
behind them. When they stopped, the flare shed 8.6 → 2.7 b/s. Then height-holding strokes, fired
while the bell still leaned 34–52° toward them, pushed it forward again and folded the web. The
mind's detour round them (sideways ask up to 1.1 b/s) couldn't be flown, because a bell can't
sidestep. It passed through their head.

**Fix (mind, DRIFT only).** A drifter catching up heads abreast of its owner, 1.2 blocks to its own
side (`driftOwnerAside`), so momentum carried past them goes past their side, in view. The spot is
used only where there's room:
- clearance 0.4 beyond its radius at the spot, and on the ways to it from the owner and from the
  bell (`driftOwnerAsideRoom`);
- the whole lane abreast of where the owner will be in 1 s is also clear (`driftOwnerAsideAhead`);
  a spot checked only 1 s ahead lands past a one-block wall;
- the room has held for 1 s (`driftOwnerAsideSettle`); between trunks and at doors it flickers,
  and swinging out and back grazed them.

Elsewhere it keeps to the trail. `driftOwnerAside=0` is the previous body, bitwise. It is gated on
DRIFT locomotion, not style, so JET and BOB inherit it.

**Rejected:** a body-side owner brake (relative closing → flare, strokes withheld). Every form cut
overlap but stopped the bell behind the owner, out of view: seen 41/95 level, 45/81 path-based,
39/62 on top of the abreast aim. Settle 0 cuts overlap most but costs tight-scene impacts (11/22).

**Results (merged state, `driftOwnerAside=0` vs defaults, 8 seeds × 2 min).**
- Inside-owner ticks 6515 → 2056 (118 seeds better / 21 worse): sprint_stop 1001 → 0,
  stop_and_go (new script, outside `NAMES`) 881 → 23, walk_turns 305 → 37, companion_passes
  645 → 38, cat_lounging 329 → 40. Near-camera ticks 4011 → 1298.
- Entries while the owner stops 335 → 138; on sprint_stop + stop_and_go 91 → 1.
- Idle inside-owner (the §13.7 chest-hang): idle scripts 0.93% → 0.13%, below the pre-idle level,
  without touching `driftIdleSink`.
- Travel scripts: seen 743 → 824, longest unseen 1234 → 1025, far / contacts / impacts even,
  fades 2 → 0. The surge is kept (overshoot rate within 2%).
- Residuals (named `DRIFT_OWNER_TIGHT_GAPS`, 6%): doorway (the room rarely holds for 1 s, inside
  576 → 539), low_ceiling (idle spots under the ceiling, up to 5.3%), forest (contacts 20 → 31 on 8
  seeds; 111 → 119 on 32, within its 109–136 spread). walk 40 → 50 inside ticks, from the owner
  turning back into it.
- Bounds: DRIFT owner bounds tightened 8.5% / 6.5% / 60 → 2.5% / 3% / 50. New test
  `driftDoesNotCoastIntoAStoppingOwner` (sprint_stop, stop_and_go: ≤ 1 stop entry, ≤ 1% inside).
  New metrics `CompanionRun.ownerOverlap()`.
- Gates after merging into the main tree: 377 tests, 0 failed; the 447 non-DRIFT fingerprints
  are bitwise identical.

**For the owner's viewer look** ("DRIFT bell (flapjack)", open ground):
1. Walk ahead of it and stop, then sprint ahead and stop: it should drift up beside you on its
   own side and coast past your shoulder, never through your back or head.
2. Stop at an angle to your walk, and just after a turn, so it arrives from behind-left,
   behind-right and the side.
3. Stop, then turn back toward it (the remaining walk case).
4. Forest and doorway: it keeps to your trail there, so an occasional overlap is the named gap.
5. Idle after a walk: it settles near you without hanging in your chest.
6. Live tunables: `driftOwnerAside` (0 = off), `driftOwnerAsideSettle` (0 = strongest fix, more
   bumps in tight scenes).

**For Phase 2:** the jet's fins can sidestep, so test `driftOwnerAside=0` for JET in tight scenes;
re-measure room and settle once the jet and bob bodies exist.

### 13.9 Owner feedback: the art flips 180° (2026-10-12, uncommitted)

The flapjack and the man o' war flipped about their vertical axis in one tick. Cause, measured
(`DriftYawSnapProbe`, 23 scripts × 4 seeds × 1200 ticks): `steerAxis` reverses by swinging the
bell back through upright, then at `tilt < 1°` sets `tiltYaw = wantYaw` at once. `yaw = tiltYaw`
was also the drawn facing, so the art snapped. That was 824 single-tick steps over 20°, nearly all
of them about 180°, roughly one every six seconds even when idle. A round bell doesn't care; flat
art does.

- Fix: drawn facing is split from the lean. `DriftBody.face` turns toward `tiltYaw`, eased and
  capped at `driftFaceRateDegPerTick` (4°/tick = 80°/s, so a full about-turn takes about 2.3 s or
  more). The cap is scaled by `min(1, tilt/15°)`, so a bell hanging upright holds its facing.
  `CompanionRender` tilts toward the lean and then faces the art its own way
  (`Y(-lean)·X(tilt)·Y(lean-face)`). Upright, that is just the facing, so the lean's swap at 1°
  tilt can't be seen.
- Physics and mind still read `yaw` (the lean). Every fingerprint, DRIFT included, is bitwise
  unchanged. The viewer draws the round bell from the axis and is unchanged too.
- After: largest facing step 4.0°/tick, 0 steps over 20°. New test `driftFacingNeverSnaps`.

### 13.10 Owner feedback: idle pushes with no visible squeeze (2026-10-12, uncommitted)

When idling beside a still owner, the flapjack was plainly pushed with no squeeze to be seen.
Measured (`DriftStrokeVisibilityProbe`, idle_fixed and idle_looking, 8 seeds): the squeeze does
play on every stroke, but it can't be seen:
- An idle stroke squeezes over about 24 ticks (duty 0.35 of a cycle up to 4.5 s), and the game
  drew it at the tank's `pulse_stretch`, 10%. The viewer's jelly draws a 30% rim squeeze, which is
  why it read in the viewer and not in game.
- A stroke that threw it from 0.16 to 0.66 b/s drew the same 10% as one that gave nothing. Speed
  peaks as the squeeze ends, then coasts for seconds.

Fix (drawn only, so every fingerprint is bitwise unchanged):
- `DriftBody.squeeze` = pulse × the stroke's strength. Strength is its planned Δv over 0.5 b/s,
  clamped to [0.2, 1] and fixed when the stroke starts. `CompanionAgent.renderSqueeze()`
  interpolates it per frame.
- `CompanionRender.driftSqueeze` (default 3) multiplies it in game, so a full stroke stretches
  30%. Set it live with `/fishtastic companion squeeze <gain>`. The viewer's jelly now draws the
  same squeeze.
- After: idle pushes ≥ 0.25 b/s with a drawn stretch ≥ 15% went 0/82 → 80/82 (idle_fixed) and
  0/79 → 77/79 (idle_looking), mean 29%. Strokes that gain under 0.05 b/s still show about 23%.
  Most of those are pushing against the sink to hold height.
- Not changed: the squeeze's length. An idle stroke is still a ~1.2 s swell. If it should read as
  a snappier push, the next lever is a shorter idle duty, which is a physics change behind a switch.

### 13.11 Owner feedback: it lays over to horizontal by a still owner (2026-10-12, uncommitted)

"Tilt somewhat over in the horizontal direction to propel but not that far." Measured with
`DriftTiltProbe` (8 seeds × 2400 ticks):
- True idle (LOITER/SETTLE) was already upright-ish: the calm lean (§13.7) kept it past 45° on
  only 1% of ticks.
- By a still owner, the moves that aren't idle (mostly SHOW_OFF, asking about 0.9 b/s) drop
  calm, so the cap went back to 85°. That was 41% of those ticks past 45° and 17–19% past 60°.
  In 30–40% of the ticks past 45°, the need pointed level or down: it was braking or correcting a
  fall, where a bell can't push.
- Travel really does need the lean. Steady tilt is about `atan(drag·v / sink)`: 34° at 1 b/s and
  79° at a walking owner's 5 b/s. A global cap of 55° was measured and rejected: forest far-time
  went 7.6 → 43 s, walk seen 10 → 1%, and stroke delivery 0.96 → 0.23.

Built, each with its own switch (`driftAskTiltDeg=85` and `driftLeanIntoBrake=true` together give
the body before, bitwise: all 654 fingerprints):
1. `driftAskTiltDeg` 30 / `driftAskTiltSpeed` 3: the lean is capped by the asked speed for any goal,
   rising from 30° to the full 85° by 3 b/s. Travel asks for 6–7 b/s, so it is unaffected.
2. `driftLeanIntoBrake` false: with a need below 15° elevation, it leans no further than holding
   its asked velocity takes, plus `driftBrakeTiltMarginDeg` (10°).

After:
- By a still owner, ticks past 60° went 17% → 0% (idle_fixed) and 19% → 0% (idle_looking).
  SHOW_OFF tops out around 50°. Travel histograms are unchanged.
- Keep-up and visibility over all 23 scripts are within noise. 16-seed checks: camera_sweep
  longest-unseen median 20.0 → 19.9 s, with 1/16 seeds over 30 s in both; corridor contacts
  median about 10–11 in both, and each body has one floor-stuck outlier seed (old #8: 38, new #3: 68).
- New test: `driftLeansOnlySomewhatByAStillOwner`. The old body fails it on 7 of 9 runs.
- Non-DRIFT fingerprints are bitwise unchanged. 195 of 207 DRIFT fingerprints changed.
- Tried and dropped: exempting the brake cap near walls made no measurable difference.

### 13.12 The tunnel floor pit, and the gates re-set (2026-10-12, uncommitted)

After §13.11, three of the 3-seed DRIFT gates flipped on seed divergence. The owner chose to fix the
corridor pit and re-set the bounds from 16-seed data.

The pit, measured on corridor seed 3: pressed to the 1-wide tunnel's side wall or sitting on its
floor, `roomSpeed` swept a sphere of radius + margin that already touched that block. It read no
room in any direction, so `roomAxis` was 0 and the room brake cut every stroke to just cancelling
its current speed. Falling, it wanted 1.9–2.6 b/s and got 0.5–0.67, so it landed. On the floor it
wanted about 2.1 b/s up and away and got 0. It sat there, scraping, until the leash. The old body
had the same pit (seed 8: 38 contacts).

- Fix, `driftRoomPastContact` (false is the body before, bitwise): already touching, it sweeps the
  largest sphere that is clear where it is (9 bisections), so only blocks it comes closer to count.
  A first cut that shrank the radius in 15% steps undershot the clearance and saw blocks ahead
  late (stairwell worst impact 8.09 b/s, over its 7.5 bound), so it was replaced by the bisection.
- After (16 seeds; old body, then now):
  - corridor contacts 12|38 → 11|20 (mean|worst), impact 2.44|4.67 → 2.54|4.04.
  - stairwell contacts 49|74 → 40|60, impact 4.62|6.48 → 5.38|7.07 (bound 7.5).
  - forest worst impact 5.12 → 6.80 (bound 7.5).
- Gates: the corridor gates pass again on their own bounds, which are left as they were.
  camera_sweep's longest-unseen bound went 30 → 36 s (`DRIFT_CAMERA_SWEEP_UNSEEN`): one seed in
  16 goes over 30 s both before (34.1 s) and after (34.6 s), median 20 s.
- Full suite: 383 tests, 0 failed. With all three of §13.11–12's switches off, the 654
  fingerprints are identical. With them on, every non-DRIFT fingerprint is unchanged.
- Lean, rechecked with the room fix in: ticks past 60° by a still owner are still 0%.

## 14. Phase 2 as built

Phase 2 squid (`jet`) was built on 2026-10-12 in a separate worktree and merged into the main
tree on 2026-10-13. Implementation, verification, equal-seed measurements and ablation switches
are in [`fish-companions-squid-handoff.md`](fish-companions-squid-handoff.md). It has fins and
mantle jets, bounded full-sphere turns, arrival trimming and a squid viewer silhouette. The
turn-visibility, doorway-contact and stopping-owner regressions have fixes and enforced tests
(`JetBodyTest`): 16-seed baseline about_face seen 32% (minimum 27%), doorway at most 3 light
contacts (0.32 b/s), zero owner overlap on sprint stops.

**Owner gate (2026-10-13, viewer):** "Squid looks good, we can merge." Accepted with its
defaults. The named differences JET_DOORWAY_VIEW, JET_PULSE_JERK and JET_COUNTER_TRAVEL
(handoff) are accepted with it. In-game squid sprite deformation stays in Phase 5.
