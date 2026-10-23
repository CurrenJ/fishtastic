# Tank fish locomotion

All six locomotion classes are implemented. `:fishsim` owns motion; the Minecraft
adapter supplies domain, species and watcher inputs. `FishAnimationConfig` owns
pose, while `FishVisuals.locomotionOf` derives motion class from that pose. There
is no per-species locomotion override; add one only for a demonstrated need.
See [simulation architecture](fish-sim-engine-plan.md) and
[companion movement](fish-companions.md) for the separate owner-follow simulation.

## Classes and pose mapping

| Animation mode | Class | Contract |
|---|---|---|
| horizontal_swim | FREE_SWIM | Flocking with species/trait tuning; retain the locked binary single-tank path |
| belly_down | GLIDE | Heading-limited smooth planar swimming, wide turns and speed-driven bank |
| upright_float | DRIFT | Pulsed vertical motion with horizontal wander; not a swimmer's alignment model |
| floor_sit, upright_sit | BENTHIC | Floor-constrained, yaw-limited crawl with stop/turn and burst/coast gait |
| planted | ANCHORED | Stable floor footprint with sway and watcher-triggered retract/emerge |
| Any failed fit gate | STATIC | Display pose without translation |

`FishSpec`, `Locomotion`, `FlockEngine`, `FloorField` and `VoxelDomain` stay
Minecraft-free. `TankFlockAdapter` and `TankGroupFlock` connect inventory, calibrated
rendered lengths and simulation. Group simulation includes all classes that fit;
non-fitting members remain local STATIC displays rather than disappearing.

## Domain, fit and continuity

Size gates are class-specific: swimmers/gliders need horizontal run, drifters need
vertical extent, and floor classes need footprint. A single swimmer's gate must
not be reused indiscriminately for every body type. The floor is represented in
domain space, including connected tanks and cosmetic occupancy. Sample floor
positions in floor space; projecting random 3D points down afterwards can overlap
creatures that passed a 3D separation test.

Rebuilds carry continuous state for matching fish: position, heading, gait, pulse
and reaction timing. Adding a fish or changing decoration should not restart the
whole display. Group splitting and rendering must agree on which slots are
simulated; see [group budgets](fish-tank-group-scaling.md).

The binary single-tank DEFAULT path is locked by `GoldenTrajectoryTest` and
`ParityTest`; the planar/group path has its own voxel golden. Keep expected values
unchanged during structural refactors. A decorated lone tank may be promoted to
the planar model for shelters or solid obstacles; see [shelters](fish-shelters.md).

## Motion and visible shape

Benthics walk on the sampled floor, keep their heading and separate by floor
footprint. Drift pulses and benthic gait provide `shapeDrive` to `FishAnimator`;
render interpolation must retain phase continuity and frame-rate independence.
Gliders bank from simulated turning rather than an unrelated clock. Avoid
high-frequency bank oscillation or constant circling introduced by tuning.

Anchored fish react to the external watcher (the local player's eye position),
not to tankmates. A crowded tank must not hold an eel underground. Reactions are
edge-triggered and habituating: a short hide is followed by a longer refractory
period, with seeded timing variation. `setWatcher` keeps the engine ignorant of
Minecraft. The watcher radius is absolute (three blocks), not body-length-scaled.
Rearming requires positive evidence that a present watcher moved beyond the
hysteresis radius (1.5 times the trigger radius). Missing input is unknown, not
proof of departure. Rebuilds or signal dropouts must not repeatedly startle eels.

## Boundaries and checks

- Keep collision, speed, acceleration, jerk, heading, floor contact and determinism
  invariants alongside golden/parity tests. Run `:fishsim:test` and relevant
  `:common:test` coverage after adapter or data changes.
- Use `:fishsim:runViewer` and `:fishsim:runHeadless` for representative sizes,
  domains, stocking and seeds. Metrics support visual judgment, not the reverse.
- Re-run `tools/fish-render-calibration.ps1` when texture, animation mode or
  diagonal-texture interpretation changes. Calibration and floor pivot errors
  can look like simulation failures.
- A low group render cap or a failed size gate may intentionally leave a fish
  static; `/fishtastic tanksim` explains its live classification.
- Obstacle geometry, shelter bob room and floor updates belong to the cosmetic
  fingerprint, not an expensive rebuild of the membership distance field.
- GPU cost at the 1,024-fish budget and off-screen simulation LOD remain separate
  performance questions. Do not raise the 512-tank cap from old CPU measurements.
