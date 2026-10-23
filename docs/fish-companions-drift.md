# Companion drifter movement

Drifters use the [companion architecture](fish-companions.md), separate from tank
locomotion. Bell and squid motion were accepted in the viewer. The Portuguese man
o' war bob is implemented; its visual review remains open.

## Data and bodies

`fish_profile.companion_drift` selects `bell`, `jet` or `bob`; absent means `bell`.
The codec passes the style through `ClientCompanion` to `CompanionAgent`; the
viewer and headless harness can select a style directly.

| Style | Species | Movement and braking |
|---|---|---|
| bell | Flapjack octopus | Sized pulse strokes; a visible web/parachute flare sheds speed |
| jet | Glass squid | Fins hover and turn; mantle-first jets travel/escape; counter-fins and reverse jets brake |
| bob | Portuguese man o' war | Vertical bobs push along the sail heading; tentacles increase drag to brake |

All thrust comes from body actions, not a tether, carry or position lerp. Strokes
supply the velocity still needed after the expected coast, bounded by style thrust.
Owner prediction reduces reversals after turns. Style-specific floor clearance and
stronger low-speed reorientation avoid ground scrapes and slow hovering pivots.
Hard per-axis resolution, slow spin and night glow are shared.

The bob stays near upright (roughly a 20-degree tilt bound), rotates its sail to
turn, and increases bob effort while travelling. Its walking pace is intentional;
a sprint can leave it behind, followed by catch-up and the normal fade backstop.
Bob propulsion is an accepted stylization rather than a realistic man o' war model.

## Desires and rendering

| Drive | Bell | Jet | Bob |
|---|---|---|---|
| Idle | Calm rise/parachute rhythm | Head-down hover with fin motion | Lazy bob and sail turn |
| Attention | Rise into view | Jet across view | Bob across view |
| Curiosity | Approach/hover | Fin approach, tentacle reach | Turn tentacles toward target |
| Wariness/hurt | Drop, flatten or clench | Escape jet | Lower/deflate or clench |
| Rest | Settle on the ground | Motionless hang | Slow bob at ride height |

Shape follows body effort: pulse stretch, bell ear flaps and flare squash, mantle
squeeze and fin flutter, or bob float wobble. Tentacle lag is render-only. Keep
motion and orientation consistent with the item's art; sprite-facing corrections
must not reverse the simulated direction.

Calm bob idle may drift sideways/backward without facing its travel. Do not treat
that as an ordinary heading failure. It is compared with `bobCalm=false`; the
switch is `/fishtastic companion switch bobCalm off|on`. Other pending visual
switches are recorded in [backport progress](backport-catchup-progress.md).

## Verification and open review

Use `:fishsim:runCompanionViewer`, selecting a DRIFT style, and
`:fishsim:runCompanionHeadless` for scripted movement. `CompanionInvariantTest`
covers styles. Compare the same seeds and scripts, including idle, walk,
sprint-stop, about-face, stairs and doorway, before changing tunables.

Measure both levels and rates: overshoot, reorientation, clearance, speed reversals,
contacts, penetration, jerk, time far from the owner and time visible. An improved
average can hide a worse tail. Preserve other classes' fingerprints and tank
golden trajectories; companion tuning is not a reason to edit `FlockEngine`.

Owner review still needs the bob's calm idle versus travel: stop-to-walk transition,
speed swing, sail wiggle and contact after catch-up. Existing accepted bell/squid
viewer behavior does not imply approval of later bob tuning. Live bob controls
include `bobCalmDrift`, `bobCalmSailNeed`, `bobCalmSailTurnDegPerTick`,
`bobIdlePeriodSeconds`, `bobCalmBank` and `bobCalmFaceDegPerTick`.
