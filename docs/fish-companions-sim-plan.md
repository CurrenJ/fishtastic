# Companion simulation architecture

The companion motion rewrite is implemented. This page keeps the architecture and its durable
constraints; it no longer tracks completed implementation phases. The current feature specs are
[party behavior](fish-companions-party.md), [world reactions](fish-companions-world.md),
[drifters](fish-companions-drift.md), and [benthic follow](fish-companions-benthic-follow.md).

## Architecture

- `:fishsim` is Minecraft-free. It owns deterministic companion agents, their goals, locomotion,
  group behavior, and pure simulation tests.
- The simulation reads a snapshot through `CompanionWorld`; it does not query Minecraft directly.
  `CompanionWorldAdapter` gathers nearby terrain, owner state, and points of interest.
- `ClientCompanions` owns client-side agents, lifecycle, and render integration. `FishAnimator` and
  the existing per-class pose/render path turn simulation output into fish visuals.
- Each client simulates visible companions from synchronized owner state. Cosmetic scenes share
  their start event and clock; client trajectories need to remain self-consistent, not bit-identical.
- Motion comes from goals and bodies, not scripted orbit points or a companion-follow box. A scale
  fade handles fast travel the fish cannot swim through; ordinary walking and sprinting should not
  trigger it.

## Durable constraints

- Keep simulation and rendering separate. Tests guard collision, bounded acceleration/turning,
  heading, first-person visibility, goal commitment, repeatability, and frame-rate independence.
- Metrics explain what a fish is doing; they are proxies. Use the viewer and in-game scenes to judge
  whether a behavior reads clearly.
- Preserve the Minecraft-free boundary when adding world input. Share tank behavior where it is
  proven safe; tank golden trajectories guard tank changes.
- World reactions belong to each fish, not the party lead. Species habitat and nearby features can
  affect each fish independently.

## Current status and deferrals

The party, mixed shoals, perches, hand visits, ambient world moods, drifter behavior, and scene
integration have been built. Their current contracts and remaining owner checks live in the feature
specs linked above. The world-reaction habitat and W5–W8 readability gates were approved in game;
tank interactions remain deferred under W9.

Fishing reactions, mob reactions, tank interactions, and cross-player scenes for locomotion classes
beyond free swimmers remain deferred. The drifter bob and benthic behavior still have owner visual
checks recorded in their respective specs. Do not infer release or gameplay approval from passing
automated checks alone.
