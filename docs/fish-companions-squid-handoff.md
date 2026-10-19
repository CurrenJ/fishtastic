# Squid / DRIFT jet: Phase 2 candidate (2026-10-12)

Status: the reported turn-visibility, doorway-contact and stopping-owner regressions have fixes
and enforced tests. The candidate is **not merged or owner accepted**. Keep the accepted bell in
the main checkout until the viewer gate. The wider style comparison still has the named differences
below; do not declare the entire §9 no-regression gate passed or advance to bob yet.

Worktree: `C:/Users/curre/.codex/worktrees/companion-squid/fishtastic`, created from the main
checkout's HEAD. All implementation changes are uncommitted. The main checkout contains a pointer
in the drift spec's §14. Existing main-checkout notes, logs and the benthic shape probe were not
copied into this phase worktree.

## Implementation

`DriftBody` dispatches JET to a separate motor path behind `driftJet`; false restores the bell
bitwise. BELL and BOB keep their previous motor. No `FlockEngine` changes.

- Fins provide at most 2 blocks/s² in any direction. They balance sink and make small corrections
  without mantle pulses. They immediately counter travel on a reversal, while the mantle turns.
- Mantle-first travel uses sin² pulses: 0.35 s cycle, 0.6 duty, up to 28 blocks/s². Each stroke
  fixes its direction and planned impulse at onset; the mantle holds that direction during the
  stroke. Unrestricted spherical turns are capped at 24°/tick travelling, rising to 30°/tick slow.
  Rest hangs mantle up / tentacles down. The existing slow tumble and independent drawn facing stay.
- A stroke pays the velocity gap and drag/sink minus the fins' contribution. `jetArrive` trims
  unused thrust when the fins have already closed the gap. Drawn squeeze follows the delivered
  jet, including this trim. Counter jets and independent counter-fin flare drag provide braking;
  holding thrust pays normal drag, never the braking drag it is trying to shed.
- JET room sweeps use a 0.2 block margin and 0.1 block steps, sharing the contact-radius bisection
  from §13.12. Fins remain active near the floor. The same per-axis hard resolve applies.
- `jetRideAhead` adds 2.2 blocks to the escort station. Jet pulses are not fed back into the
  mind's asked speed as an extra surge; their physical thrust already supplies that surge.
- The headless/viewer renderer has a distinct squid silhouette: pointed mantle, paired fluttering
  fins, a visible squeeze and trailing tentacles. In-game species-specific shape work remains
  Phase 5; the existing game adapter already selects the JET motor from `companion_drift`.

## Diagnostics and verification

Evidence is in `build/squid-validation/`; walking visual exports are in
`fishsim/build/squid-preview/`. These are generated artifacts, not committed test fixtures.

- Full `:fishsim:test :common:test :fabric:build :neoforge:build` succeeded: simulation 388 tests,
  353 passed / 35 skipped / 0 failed; common 767 tests, 0 failures; both loader builds passed.
- `JetBodyTest` verifies hover without mantle strokes, mantle-first pulses and coasts at walking
  speed, immediate counter-fin braking, fade placement clearing a live jet, the disabled JET's
  bitwise bell behavior, and finite / collision-free runs across all 23 scripts × three seeds.
- Fingerprints: all 585 non-JET lines of the 654-line baseline are bitwise identical. All 69
  JET hashes changed. This includes the accepted bell, provisional bob, other classes and trio.
- A/B: every script × eight seeds × 2400 ticks, both baseline and glass squid's ghost temperament,
  `driftJet=false` against `driftJet=true`, with per-seed standard, drift, body and owner-overlap
  metrics. Every run has zero solid penetrations.
- Reorientation now reads actual propulsion, including off-axis fins; a one-second refractory
  avoids counting the same ask swing every tick when fins respond immediately. Both A/B sides
  use this corrected metric. Historical reorientation rates require remeasurement.
- JET tail-first counts only ticks with mantle thrust > 1 blocks/s². Mantle-first is forward;
  sideways or reverse fin corrections are not erroneous mantle travel. All baseline/ghost rows
  currently read 0% mantle-tail-first. Bell/bob retain their original thrust gate.

## Measured gains and remaining gates

Baseline temperament, means over eight seeds (bell → candidate):

| Script | Longest far time, s | Seen, % | Contacts / 2 min | Worst impact, blocks/s |
|---|---|---|---|---|
| walk | 6.2 → 0.0 | 9 → 51 | 0 → 0 | 0 → 0 |
| sprint_stop | 6.4 → 0.3 | 23 → 35 | 0 → 0 | 0 → 0 |
| corridor | 16.9 → 8.1 | 2 → 5 | 10 → 5 | 2.91 → 2.20 |
| doorway | 4.6 → 2.6 | 15 → 15 | 1 → 7 | 2.88 → 1.44 |
| forest | 7.1 → 3.8 | 8 → 18 | 3 → 3 | 6.80 → 3.02 |
| stairwell | 12.9 → 5.8 | 7 → 3 | 37 → 14 | 6.43 → 2.45 |
| about_face | 4.9 → 3.2 | 29 → 2 | 0 → 0 | 0 → 0 |

The table above is the **original candidate**, before the regression fixes below. Its turn
visibility, stair visibility, doorway contact count and sprint-stop owner entries are superseded.
Do not use this historical table as the current candidate's measurements.

Tried and discarded: stronger 3 blocks/s² fins could sustain walking with too few jets; 32-thrust /
0.8 s jets grew overshoot and impacts; a 4-block escort lead weakened walking and stopping;
0.5 s catch-up prediction plus faster view-pass yaw did not fix about-face and increased doorway
contacts; a capsule braking prototype improved a stop-entry seed but lengthened the stairwell
far-time tail. Raw experiments are retained with the validation output.

## Regression fixes (2026-10-12)

The old motor spent about 1.5 seconds reorienting at travel speed, then waited for a long cycle
before stroking. The mind's escort station also turned at a bell's 6°/tick, and its view pass at
3.5°/tick. On repeated turns the owner passed it before it accelerated and it chased from behind.
The fixes are bounded fin turns and shorter squeeze/coast cycles, matching target turn rates
(`jetMindTurn`), a 0.7 s catch-up lead only along a clear lane (`jetFollowLead`), and a forward
escort station. Attention passes use 2.5 blocks/s relative pace (`jetViewPassSpeed`, 0 restores
the old calculation) so they can reach the view before a short stop ends.

The bell's flare rules were also wrong for squid: squeezing folded the brake, and the motor's
hold term then paid the brake's added drag with extra thrust. `jetCounterFinBrake` separates
counter fins from the mantle and removes braking drag from the hold term. `jetBrakeDelay` reserves
0.2 s of opening delay in stopping distance. `jetAlignDeg` (8°) prevents a poorly aligned mantle
from launching a sideways/upward stroke through a tight opening. The hard resolve is unchanged.

Final baseline sweep: all 23 scripts × 16 seeds × 2400 ticks, bell off/on comparison. Ghost sweep:
all 23 scripts × 8 seeds × 2400 ticks. Raw files `regression-final-baseline.txt` and
`regression-ghost.txt` in `build/squid-validation/`. Every run has zero solid penetrations.

| Script | Original squid, 8-seed mean | Repaired squid, 16-seed mean | Repaired worst |
|---|---|---|---|
| about_face, seen | 2% | 32% | 27% (minimum) |
| about_face, longest far | 3.2 s | 0.7 s | 1.9 s |
| doorway, contacts / 2 min | 7 | below 1 | 3 |
| doorway, max impact | 1.26 blocks/s | 0.02 blocks/s | 0.32 blocks/s |
| stairwell, seen | 3% | 8% | 7% (minimum) |
| sprint_stop, owner stop entries | up to 2 | 0 | 0 |
| sprint_stop, seen | 35% | 69% | 60% (minimum) |

For an exact equal-seed comparison, use the first eight per-seed lines or the switches below;
the 16-seed column deliberately adds outliers, rather than claiming identical sampling. The bell
on those same 16 seeds has about_face seen 27% / minimum 22%, doorway worst 3 contacts / 3.97
blocks/s, stairwell seen 7% / minimum 5%, and sprint_stop seen 24% / minimum 17%. All 16 repaired
baseline sprint-stop runs and all eight ghost runs have **zero inside-owner ticks**, stop entries
and idle overlap. Ghost about_face seen is 32% / minimum 25%; doorway has no contacts; stairs
seen is 9% / minimum 8%.

Three additional `JetBodyTest` tests enforce the regressions across both temperaments × eight
seeds: about_face mean seen ≥29%, per seed ≥24%, far ≤2 s; doorway ≤3 contacts, impact ≤0.5,
far ≤4 s and stair seen ≥6%; sprint_stop zero owner entries/overlap. The earlier candidate fails
these gates. Existing bell bounds remain unchanged.

Final verification: simulation 391 tests (356 passed, 35 skipped, 0 failed); common 767 tests
(766 passed, 1 skipped, 0 failed); Fabric and NeoForge builds succeeded. A combined verification
run hit a native-memory allocation failure in the common test JVM; rerunning common tests and
the loader builds serially with `--max-workers=1 --no-parallel --no-daemon
-Dorg.gradle.jvmargs=-Xmx512m` passed. The full simulation run had already succeeded. Reports:
`regression-verification.txt`, `regression-common-tests.txt`, `regression-builds.txt`.

All fixes have ablation settings. This exact list restores the previous candidate's 654
fingerprints bitwise (including all 69 JET lines), checked with `FingerprintProbe -PfpAll=1`:

```text
jetTurnDegPerTick=6,jetSlowTurnDegPerTick=12,jetPulseSeconds=0.65,jetPulseDuty=0.3,
jetThrust=24,jetRideAhead=1.4,jetFollowLead=0,jetAlignDeg=25,jetBrakeDelay=0,
jetCounterFinBrake=false,jetMindTurn=false,jetViewPassSpeed=0
```

`FingerprintProbe` now accepts `-PtweakA=...` before constructing each run. Its default behavior
is unchanged. Final defaults also preserve all 585 non-JET fingerprints bitwise; result is in
`regression-fp-on.txt`, the full old-candidate ablation is `regression-fp-off.txt`. The draw-facing
cap stays separate from the mantle turn cap.

Named differences for the owner gate: **JET_DOORWAY_VIEW** — doorway mean seen remains 10% versus
the bell's 16%, although contacts, impacts and far time are substantially lower. **JET_PULSE_JERK**
— stronger/shorter travel jets have stroke jerk up to roughly 740 blocks/s³ versus about 570 for
the old squid and 430 for the bell. **JET_COUNTER_TRAVEL** — deliberate braking jets briefly
point opposite travel, so tail-first and velocity-reversal rates must be read as braking as well
as travel errors. These are explicit differences, not silently relaxed bell gates. Final species
render, beats and the full invariant table remain later planned phases.

Rejected after measuring: a narrow-lane leading station, lowering escort height, smaller room
margin, per-axis room clipping and lateral-first fin allocation. They lengthened at least one
visibility, far-time or collision tail. Faster attention passes at 3.5 blocks/s also grew idle
jerk/overshoot without a useful view gain; retained pace is 2.5.

## Viewer check

Updated turn and sprint-stop GIFs/contact sheets are in
`fishsim/build/squid-regression-preview/` (seed 1, 600 ticks each); rendered and visually checked.
The original walking preview remains in `fishsim/build/squid-preview/` for comparison.

From this worktree run `./gradlew :fishsim:runCompanionViewer`, choose **DRIFT jet (squid)**.
Inspect head-down idle with fin flutter, mantle-first walking and sprint travel, immediate fin
braking before a counter jet, a full about-turn without the bell's upright reversal, and stops
beside the owner. Repeat in the doorway, corridor and stairs. Set `driftJet=false` to compare the
previous bell in the same style slot; tune `jetFinThrust`, `jetThrust`, `jetPulseSeconds`,
`jetArrive`, `jetRideAhead` and `jetRoomMargin` independently. The viewer silhouette is a proxy;
final in-game sprite deformation is not part of this phase's verification.
