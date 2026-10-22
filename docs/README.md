# Documentation index

Use the feature contract or design spec for intended behavior and the code for current behavior.
Completed implementation trails and raw capture sets have been removed; Git history remains the
record of past changes.

## Current systems

- **Companions:** [simulation architecture](fish-companions-sim-plan.md), [party behavior](fish-companions-party.md),
  [world reactions](fish-companions-world.md), [drifters](fish-companions-drift.md),
  [benthic follow](fish-companions-benthic-follow.md), and [habitats](fish-companions-habitats.md).
- **Ambient fish:** [implemented feature contract and remaining owner review](fish-in-world.md).
- **Fishopedia:** [implemented info-page summary](encyclopedia-redesign/README.md).
- **Quest book:** [implemented redesign summary](quest-log-redesign/README.md),
  [interaction contract](quest-log-redesign/design-spec.md), and
  [runtime review and known limits](quest-log-redesign/review/README.md).
- **Tanks and simulation:** [tanks](fish-tanks.md), [rendering](fish-tank-rendering.md),
  [group scaling](fish-tank-group-scaling.md), [interaction](fish-tank-interaction-redesign.md),
  [bubbles](fish-tank-bubbles.md), [simulation engine](fish-sim-engine-plan.md),
  [locomotion](fish-sim-locomotion.md), and [swarm behavior](fish-swarm-realism.md).
- **Shelters and cosmetics:** [shelters](fish-shelters.md), [render cost](cosmetic-render-cost.md),
  and [future cosmetic ideas](cosmetic-ideas.md).

## Other feature references

- [Quest system](quest-system.md), [rod and bait upgrades](rod-and-bait-upgrades.md),
  [fish lore checklist](fish-lore-checklist.md), [item-effect rendering](item-effect-rendering.md),
  [feature design overview](feature-design-overview.md), and [pose stack guide](POSESTACK_GUIDE.md).
- [Tank-shape references](tank-shapes/).
- [Fish Profile Studio](../tools/fish-profile-studio.html) is retained; its update is deferred.

## Porting and support

- [Forge support plan](forge-support-implementation-plan.md) and [current status](forge-support-progress.md).
- [Backport catch-up plan](backport-catchup-plan.md), [current progress](backport-catchup-progress.md),
  [feature ledger](backport-catchup/features.md), and
  [source commit ledger](backport-catchup/source-commit-ledger.json).
- For backport work, also follow the repository guidance in `AGENTS.md` and the canonical modding
  guide's topics 19 and 20.

## GameTests

Run `./gradlew :fabric:runGametest` or `./gradlew :neoforge:runGametest`. Each invocation
selects a new UUID universe under that loader's `build/gametest-worlds/`; successful runs remove
their universe, while failed runs retain it at the path printed in the log. JUnit results are
written to `<loader>/build/junit.xml`. When invoking both loaders together, use `--no-parallel`
to avoid Architectury's shared agent-jar race.

Backup GameTests run synchronously inside `BackupTestFixture`, with fresh saved data and an
isolated backup directory. The original object and files are restored even on failure. Other
gameplay tests create shared quest/tutorial records during a run; the disposable universe prevents
those records from accumulating across invocations. Do not schedule delayed assertions inside the
backup fixture. Compost yield checks load a nearly complete block entity and finish through the
real ticker; the base and aerated timing checks still cover the entire 6,000- and 4,800-tick cycles.
