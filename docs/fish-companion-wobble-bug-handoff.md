# Fish companion wobble — bug handoff (2026-10-11)

> **RESOLVED (same day, follow-up session); in-game look pending.** The root cause was a
> self-sustaining limit cycle in `stepFollow`'s moment-profile steering. The acceleration
> ignored the velocity still to come while built-up acceleration unwinds at the jerk cap, so
> any kick past ~0.5 block locked the box into a ±0.6–1.3-block, ~18-tick cycle. Fix: a
> jerk-aware lookahead on the moment profile. Results, equal grounds: afterLegacy 1.115 → 0.127,
> g1 1.153 → 0.078, g2 1.373 → 0.157, peek 1.160 → 0.015. Full write-up in
> `docs/fish-companions.md` §21.
>
> Corrections to this doc:
> - **§4's moving-centre hypothesis is wrong.** A phantom's scene point is its `final`
>   `phantomEye`, not its swimming position.
> - **§4's "clean contexts" weren't clean.** g2 and peek wobbled at ~1.4 b all along; the
>   9-tick metric of §3 hid it. The bisect eliminations made there were blind for the same reason.
>
> The text below is kept as the record.

**Status: UNRESOLVED, reproduced deterministically. Handed off mid-diagnosis by the round-3
session.** A candidate fix exists in the working tree but has been *invalidated* as a validation
(this doc explains exactly why and what remains true). Read this whole doc before touching the
steering.

---

## 1. The symptom (owner-reported, verbatim)

> "Fish in both g1 and g2 scenes, and also sometimes when just player is idling with companions,
> they are doing huge medium-slow wobbles side-to-side or up and down. Very clearly anomalous.
> The wobble distance is 1+ blocks and is above 1 wobble oscillation a second."

Confirmed on a **bluegill** (FREE_SWIM) in a pet-to-pet scene. Still visible after the round-3
session's candidate fix — the owner watched the ten-scene self-test suite and reported it
unchanged.

## 2. The reproduction (use this; it is deterministic)

Self-test scene `companionpolish3` (opt-in marker; see `RenderSelfTest`), leg
**`wobble.afterLegacy`** — added by the round-3 session precisely because it reproduces the
original reading:

- The leg probes the DRAWN fish per tick from **186 ticks into the fixed-crossing G2 scene**
  through ~+247 (61 samples) — i.e. the scene's late phase and its second crossing traversal.
- That scene was started **~5 ticks after the dev-only `legacy` crossing leg** (the
  `devLegacyEight` seam with the 110.8°-turn whiplash path), so the scene's **phantom partner**
  is displaced when the scene begins.
- Measured on the reverted (pre-fix) tree — `fabric/run/logs/wobble-H-afterlegacy.log`:
  **fast-component residual = 1.263 blocks** (per-axis xyz = 0.287 / 0.058 / 1.263), matching the
  original round-3 reading (`wobble-instr2.log` idle window, re-measured offline: **1.238 b**).
- Signature in the trace: the box **centre lunges** — drawn speed mean 0.154, max 0.315 b/tick
  against a scene path moving ~0.05–0.07 b/tick; per-tick acceleration pinned at the moment cap
  (0.06) for consecutive ticks; tracking error (centre − target) swings ±0.6–0.9 blocks at
  **~18–36 tick period (0.6–1.1 Hz)**.

## 3. ⚠ The metric trap (this one cost days — read it)

The permanent checks (`checkWobble` → `wobble.idle/g1/g2/peek/water/afterLegacy`) measure the
fast component of the drawn path with **a centred 9-tick moving average** (per-tick rows,
half-window 4). A 9-tick MA attenuates exactly this bug's 18–36-tick period band heavily —
**on the reproducing window the check prints 0.387 (PASS) while the true amplitude is 1.26 b.**
Every "clean" number the checks reported through round 3 is therefore suspect at ±3× in this
band. **Fix the metric before trusting any wobble check.** The offline re-measurement that works:
log the trace rows (every 2nd tick is enough), then high-pass with a 9-*row* window on the
2-tick-spaced series (= 18 real ticks) — implemented ad hoc in the round-3 session; see the
analysis snippet in the round-3 log transcript or re-derive: for each axis, residual[i] =
v[i] − mean(v[i−4 .. i+4]), p2p of residuals, edges skipped. Longer windows measure this band
more; the check's window must be re-chosen with the band in mind (recommend half ≥ 8 on per-tick
rows, then re-baseline the healthy scenes: clean-scene residuals measured 0.3–0.4 with the
9-tick window — they will read somewhat higher with a longer one).

## 4. What is known about the mechanism

**Context-dependence is total.** The *same* G2 scene, same code, probed from tick 0 through the
whole 250/300-tick scene in a **settled** context (phantom near the owner; scene not preceded by
the legacy leg) is **clean**: the local fish tracks the scene path at gap ≈ 0.01 blocks; whole-
scene residuals 0.29–0.39 even with the mis-calibrated metric, and the scene end (a ~2-block
target snap when the scene clock expires) is a single clean 8-tick chase. See
`wobble-G2-unfixed-full.log`, `wobble-F4.log`.

The reproducing context differs in two structural ways:

1. The scene starts ~5 ticks after the legacy leg (dev-only seam) whose whiplash flings the fish.
2. **The scene's ring/eight centre appears to lie ~3.4 blocks east of the owner during the
   reproducing window** (target ≈ 3.0–3.7 b from the eye, vs ≈ 1.2 b in clean runs) — consistent
   with the **scene path being centred on the midpoint of the two owners, with the phantom
   partner still travelled-out from the legacy leg and possibly returning during the window.**
   If the phantom's position feeds the ring centre, its transit translates/jitters the whole
   path — a moving-centre path the local fish chases — but **this is a hypothesis, not yet
   proven**: the phantom's per-tick position and the ring centre were never logged during the
   window. That is the first instrumentation the next session should add (a
   `devPhantomPoint()`-style column in the wobble trace; `ClientCompanions.scenePartnerPoint`
   and the phantom's own anchor are the places to look).

**Not** the cause (each eliminated by measurement, logs available):

- Double-ticking (per-companion tick counter is 1:1 with gameTime) or double registration.
- Player motion (the owner's eye is frozen throughout the window).
- Orbit over-advance (orbit angle advances exactly the designed 0.6923°/tick) or tricks
  (`trick=0` on every sampled row).
- Wall-bounce/box size (`devFollowBoxSide`), the fish-centring compensation weight
  (`devCompensation`), or the feed-forward weight (`devFeedForward`) — bisect switches changed
  nothing in the clean windows.
- The moment-profile gains themselves in *clean* contexts (see §5).

## 5. The unvalidated candidate fix (in the working tree, adjudication pending)

Round 3 produced two behavioural changes in `ClientCompanion.stepFollow`:

```java
// (a) scenes and tricks ride the fast "moment" profile:
boolean moment = docking || outAmount() > 0f || sceneBlend > 0.001f || trickTimer > 0
        || gap > FOLLOW_FAR_GAP;
// (b) full velocity feed-forward while a scene drives:
double ff = ffWeight + (1.0 - ffWeight) * sceneBlend;
Vec3 desiredVel = clampLength(err.scale(gain).add(anchorVelocity.scale(ff)), ...);
```

**Validation state, equal-grounds A/B (identical windows, both trees):**

- Tick-0 scene start, both scenes, 121-tick windows: fixed 0.346/0.392 vs unfixed 0.297/0.353 —
  **no effect** (`wobble-F4.log` vs `wobble-G-unfixed.log`).
- Full 250/300-tick scenes including both crossings and the end: unfixed g1 0.286, g2 0.383 —
  **no oscillation to fix in clean contexts at all** (`wobble-G2-unfixed-full.log`).
- The one window where the bug reproduces (`wobble.afterLegacy`): unfixed = 1.263 (§2),
  fixed = **1.177** (§9) — a ~7% difference, i.e. **no meaningful effect**.

**Verdict: the two lines are REVERTED in the working tree** (the exact replace shown above,
restoring `double ff = ffWeight;` — the seam field with its 0.6 default, value-identical to the
original `FOLLOW_FEED_FORWARD` use). Nothing indicates they *hurt*; nothing measured says they
help. The next dev starts from the round-3 baseline behaviour with the instrumentation kept.

## 6. Working-tree inventory (uncommitted — the parent commits/reviews)

Everything below is part of the round-3 session and is dev-only unless stated:

| Change | File(s) | Verdict |
|---|---|---|
| `wobble.*` regression legs + per-tick probes + trace dumps | `RenderSelfTest` | **Keep** — but recalibrate the metric window (§3) |
| `wobble.afterLegacy` reproduction leg | `RenderSelfTest` | **Keep** — the repro |
| Idle leg stops scenes first (`devStopScenes` + settle) | `RenderSelfTest` | **Keep** — the old "idle" window was mid-scene |
| Orbit-phase pin (`devSetOrbitPhaseDeg(180)` at setup) + seam | `RenderSelfTest`, `ClientCompanion`, `ClientCompanions` | **Keep, validated** — the orbit phase seeds from `stack.hashCode()` and is random per run; unpinned walk checks flipped between 0.23 and 0.57 regimes run-to-run (0.81/0.844 after pinning, both trees) |
| Dev seams: `ffWeight`, `compensationWeight`, `devLegacyFollow`, `devFollowBoxSide`, trace accessors (`devOrbitAngleDeg`, `devTrickTimer`, `devTickCount`, `devPeekBlend`, `devSceneBlend`, `devOutAmount`, `devLineWait`, `devSetOrbitPhaseDeg`) | `ClientCompanion`, `ClientCompanions`, `CompanionFlock` | Keep (bisect instrumentation; inert in production) |
| The two `stepFollow` behaviour lines (§5) | `ClientCompanion` | **REVERTED** — adjudicated on the afterLegacy window (1.177 fixed vs 1.263 unfixed, ~7%) |
| §21 "Round-3 regression" section | `docs/fish-companions.md` | **Superseded — correct it** (it states a validated fix; per §5 it is not) |

## 7. Run index (all logs under `fabric/run/logs/`)

| Log | What it shows |
|---|---|
| `wobble-instr2.log` | Original reading, unfixed tree: idle window mid-scene, offline residual **1.238 b**; also the first probe-column evidence (eye frozen, orbit exact, scene=1.0, trick=0) |
| `wobble-fix1.log`, `wobble-fix2.log` | First fix attempts and the old-metric readings (0.84–1.88) |
| `wobble-fix3.log`, `wobble-F3.log` | Fixed tree, steady windows, all `wobble.*` PASS |
| `wobble-F4.log` | Fixed tree, tick-0 windows (attack phases) — clean |
| `wobble-G-unfixed.log` | Unfixed tree, tick-0 windows — **identical to F4 → fix has no effect in clean contexts** |
| `wobble-G2-unfixed-full.log` | Unfixed tree, whole-scene windows — clean; the scene-end snap behaviour |
| `wobble-H-afterlegacy.log` | **The reproduction**: unfixed tree, `wobble.afterLegacy` = 1.263 offline |
| `wobble-I-fixed-afterlegacy.log` | Fixed tree, same window — adjudicates §5 (see §9) |
| `final-suite-fabric.log` | Full ten-scene suite on the then-current tree: 134 checks, 133 pass; one flake `polish.facesVelocity min=-0.151` (single sub-zero sample at low speed — velocity direction is ill-defined when \|v\| is small and the facing cap is 7°/tick; recommend a minimal-speed gate on that dot before calling it a real regression) |

## 8. Suggested next steps, in order

1. **Fix the wobble metric** (§3) — until then, all wobble checks are blind in the defect's band.
2. **Instrument the phantom**: per-tick `scenePartnerPoint` / phantom position and the computed
   ring centre into the wobble trace; re-run `companionpolish3`.
3. **Test the moving-centre hypothesis** cheaply: before the fixed-crossing scene, teleport the
   phantom to its home point (or freeze its anchor) — if the afterLegacy wobble vanishes, the
   partner's transit is the engine. (The real-game dev solo scenes use the same one-player +
   phantom path, so this is representative.)
4. **Own it at the source**: the scene path derives from locally-known positions (the protocol
   shares only the type + start time + both owner UUIDs). If the centre must be the owners'
   midpoint, consider capturing and freezing the centre at scene start, or smoothing the phantom's
   contribution — decide with the owner (it's their visual).
5. **Only then** re-adjudicate §5's fix with the corrected metric in the repro window.
6. Re-run both loader suites on the final tree; fix the `facesVelocity` gate; keep the phase pin.

## 9. Adjudication results (the fix-vs-baseline question)

`wobble-I-fixed-afterlegacy.log` — fixed tree, identical afterLegacy window, offline method of §3:

> **Result: fixed tree offline residual = 1.177 b** (per-axis xyz = 1.177 / 0.119 / 0.590; the
> check's mis-calibrated number was 0.356, PASS). Unfixed tree, same window: **1.263 b**.
> **~7% difference — the candidate fix does not address this wobble.** Reverted (§5 verdict).
>
> Both runs also caught the same corroborating detail: the check window's zcr reads 1.000 Hz —
> squarely in the owner's "above one a second" — while the check still PASSes because of the
> 9-tick-window attenuation (§3).
>
> Note for the next run of this leg: the log contains this dump first, then the idle and g1
> dumps; split the trace into segments on gameTime gaps > 6 ticks (the round-3 ad-hoc analysis
> did this) and apply the high-pass per segment. Mixing segments produces nonsense (one attempt
> read 2.37 b — do not repeat that mistake).
