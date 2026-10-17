# Docs index

Where the current truth lives, and what is history. Updated 2026-10-16 after the pre-backport
cleanup ([audit §9.4](audit-2026-10-15-companions-and-fish-in-world.md#94-final-verification-and-acceptance-2026-10-16)).
All six cleanup steps are implemented, validated and committed; no backport
has started. Five A/B switches remain pending owner visual review; dirty legacy worktrees are retained.
When a doc and the code disagree,
the code wins; fix the doc.

## Fish companions

**Current**
- [fish-companions-sim-plan.md](fish-companions-sim-plan.md): the architecture (headless `:fishsim`
  agent sim plus a thin adapter) and the phase record.
- [fish-companions-party.md](fish-companions-party.md): the three-slot party, grouping, hand visits,
  the overlay, and cross-player scenes (§28).
- [fish-companions-world.md](fish-companions-world.md): the world around the owner (features, moods,
  habitats), W0 to W9.
- [fish-companions-drift.md](fish-companions-drift.md): drifters (bell, jet, bob).
- [fish-companions-benthic-follow.md](fish-companions-benthic-follow.md): benthics on the ground.
- [fish-companions-habitats.md](fish-companions-habitats.md): the approved per-species habitat table.
- Measurement and verification: [world-sim](fish-companions-world-sim.md),
  [world-cost](fish-companions-world-cost.md), [world-validation](fish-companions-world-validation.md),
  and the two-client harness in [fish-companions-twoclient/](fish-companions-twoclient/).

**History** (decisions and work that are done or superseded)
- [fish-companions.md](fish-companions.md): the v1 spec. Its motion, trio and display-mode sections
  describe deleted code. The deferred fishing reactions (§5) are still the reference if they return.
- Checklists: [fish-companion-ideas.md](fish-companion-ideas.md), [fish-companion-review.md](fish-companion-review.md).
- Handoffs: [handoff](fish-companions-handoff.md), [phase 3](fish-companions-phase3-handoff.md),
  [squid](fish-companions-squid-handoff.md), [benthic](fish-companions-benthic-handoff.md),
  [wobble bug](fish-companion-wobble-bug-handoff.md), [GUI redesign](companions-gui-redesign-handoff.md).
- Party as-built notes: [1a](fish-companions-party-1a-asbuilt.md), [1b](fish-companions-party-1b-asbuilt.md),
  [1c](fish-companions-party-1c-asbuilt.md), [2a](fish-companions-party-2a-asbuilt.md), and the
  [Phase 2 evidence review](fish-companions-party-phase2-review.md).

## Fish in world (ambient fish)

**Current**
- [fish-in-world.md](fish-in-world.md): the feature contract (what a player may infer from what they see).
- [fish-in-world-implementation-plan.md](fish-in-world-implementation-plan.md): the phase table and gates.
- [fish-in-world-handoff.md](fish-in-world-handoff.md): the as-built summary and where to start.

**History**
- Per-phase gate records: [0](fish-in-world-phase0.md), [1](fish-in-world-phase1.md), [2](fish-in-world-phase2.md),
  [3](fish-in-world-phase3.md), [4](fish-in-world-phase4.md), [5](fish-in-world-phase5.md),
  [6](fish-in-world-phase6.md), [7](fish-in-world-phase7.md). Their raw evidence (logs, screenshots, capture
  scripts) sits in the matching `fish-in-world-phase*/` directories.
- Playtest notes: [island playtest](fish-in-world-island-playtest.md), [early retirement](fish-in-world-early-retirement.md).

## Fish tanks and the tank simulation

- [fish-tanks.md](fish-tanks.md) (tank shapes are generated; see also [tank-shapes/](tank-shapes/)),
  [fish-tank-rendering.md](fish-tank-rendering.md), [fish-tank-group-scaling.md](fish-tank-group-scaling.md),
  [fish-tank-interaction-redesign.md](fish-tank-interaction-redesign.md), [fish-tank-bubbles.md](fish-tank-bubbles.md).
- Simulation engine: [fish-sim-engine-plan.md](fish-sim-engine-plan.md), [fish-sim-locomotion.md](fish-sim-locomotion.md),
  [fish-swarm-realism.md](fish-swarm-realism.md). Their handoffs ([engine](fish-sim-engine-handoff.md),
  [locomotion](fish-sim-locomotion-handoff.md), [swarm tier 2](fish-swarm-tier2-handoff.md),
  [simulation](fish-simulation-handoff.md)) are history.
- Shelters and cosmetics: [fish-shelters.md](fish-shelters.md) ([obstacles handoff](fish-shelters-obstacles-handoff.md)),
  [cosmetic-render-cost.md](cosmetic-render-cost.md), [cosmetic-ideas.md](cosmetic-ideas.md),
  [medium-cosmetics-handoff.md](medium-cosmetics-handoff.md).

## Other features

- [quest-system.md](quest-system.md), [rod-and-bait-upgrades.md](rod-and-bait-upgrades.md),
  [encyclopedia-tier-review.md](encyclopedia-tier-review.md), [fish-lore-checklist.md](fish-lore-checklist.md),
  [item-effect-rendering.md](item-effect-rendering.md), [feature-design-overview.md](feature-design-overview.md),
  [POSESTACK_GUIDE.md](POSESTACK_GUIDE.md).

## Reviews and audits

- [audit-2026-10-15-companions-and-fish-in-world.md](audit-2026-10-15-companions-and-fish-in-world.md): the
  pre-backport audit and the cleanup it led to (§9).
- [reviews/](reviews/).

## GameTests

Run `./gradlew :fabric:runGametest` or `./gradlew :neoforge:runGametest`. Each invocation
selects a new UUID universe under that loader's `build/gametest-worlds/`; successful runs remove
their universe, while failed runs retain it at the path printed in the log. Historical worlds
under `build/gametest/` are left alone. JUnit results are written to `<loader>/build/junit.xml`.
When invoking both loaders together, use `--no-parallel` to avoid Architectury's shared agent-jar race.

Backup GameTests run synchronously inside `BackupTestFixture`, with fresh saved data and an isolated
backup directory. The original object and files are restored even on failure. Other gameplay tests
create shared quest/tutorial records during a run; the disposable universe prevents those records
from accumulating across invocations. Do not schedule delayed assertions inside the backup fixture.
Compost yield checks load a nearly complete block entity and finish through the real ticker;
the base and aerated timing checks still cover the entire 6,000- and 4,800-tick cycles.

Backports follow the porting playbook in the modding guide (`D:\GitHub\modding-guide`, topics 19 and 20).
