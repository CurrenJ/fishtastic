# Fish simulation architecture

The simulation extraction is complete. This is the current architecture reference, not an
implementation plan. See [locomotion classes](fish-sim-locomotion.md) and
[tank group scaling](fish-tank-group-scaling.md) for the current behavior contracts and remaining
cost question.

## Modules and responsibilities

- `:fishsim` is a plain Java subproject with no Minecraft or loader dependencies. It owns the
  fixed-step flock engine, voxel domain, locomotion, and deterministic simulation tests.
- `common` adapts tank blocks, inventories, species data, and render state to and from the engine.
  Loader-specific setup stays in the corresponding loader modules.
- Simulation ticks are fixed-step; rendering interpolates between completed steps. Keep rendering
  read-only so camera and frame rate cannot advance simulation state.
- Tank groups use the voxel-domain model. The single-tank 2.5D model remains protected by
  `GoldenTrajectoryTest` and `ParityTest`.

## Durable constraints

- Keep the core Minecraft-free and deterministic.
- Treat metrics as diagnostics; visible behavior is judged through representative views and in-game
  review.
- Preserve tank goldens when changing shared motion or pose code. A changed golden needs an explicit
  behavior explanation, not an unexplained expected-value update.
- Group render limits are performance policy. See the group-scaling reference before changing the
  fish cap.
