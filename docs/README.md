# Documentation

Current contracts explain intended behavior; code defines what this checkout does.
Completed execution histories live in Git. Start with [development](development.md)
for build/test/resource workflows and [tools](../tools/README.md) for authoring and
reproducible verification. [Product direction and backlog](backlog.md) holds unscheduled ideas.

## Systems

- [Companions](fish-companions.md) and [drifter movement](fish-companions-drift.md).
- [Ambient fish](fish-in-world.md).
- [Interactive fish schools](interactive-fish-schools-design-spec.md) (Phase A implementation and tuning contract); [school motion plan and progress](interactive-fish-schools-motion-plan.md); [school performance plan](interactive-fish-schools-performance-plan.md); [in-world fish encounters](in-world-fish-encounters-design-spec.md) (Phases 1–4 built: all-species eligibility, visit shapes, cues, eel/drifter/bottom-bite approaches).
- [Tanks and content authoring](fish-tanks.md), [tank rendering](fish-tank-rendering.md),
  [group budgets](fish-tank-group-scaling.md), and [bubbles](fish-tank-bubbles.md).
- [Simulation architecture](fish-sim-engine-plan.md), [locomotion](fish-sim-locomotion.md),
  and [swarm behavior](fish-swarm-realism.md).
- [Shelters/obstacles/reactions](fish-shelters.md) and [historical cosmetic benchmark](cosmetic-render-cost.md).
- [Quest data/server behavior](quest-system.md), [quest book contract and open checks](quest-log-redesign/design-spec.md),
  and [encyclopedia behavior](encyclopedia-redesign/README.md).
- [Item-effect rendering](item-effect-rendering.md) and [shape blueprints](tank-shapes/).

## Compatibility and retained records

- [Backport state and continuation](backport-catchup-progress.md),
  [feature accounting](backport-catchup/features.md), and
  [commit provenance](backport-catchup/source-commit-ledger.json).
- [Forge acceptance reference](forge-support-implementation-plan.md) and
  [continuation/release state](forge-support-progress.md).
- [Forward-port investigation](forward-porting.md): future scope/risks, not target acceptance.
- [Release 2.1.7](release-2.1.7.md) and [Release 2.1.2](release-2.1.2.md).
  Historical versions/hashes are evidence, not current branch version pins.
- [Recovery snapshot references](worktree-cleanup-2026-10-19.md).

Pending owner checks stay in the relevant system/compatibility page. Passing tests
or condensing documentation does not close those checks or advance parity markers.
