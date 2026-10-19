# Backport catch-up outline — 2026-10-17

Status: started 2026-10-17; stage 1 baseline inspection is in progress. Source snapshot
confirmed as `48fffdfb`. See [the durable progress log](backport-catchup-progress.md)
for the current checkpoint, evidence and exact next action.

## Baseline and approach

Both existing ports (`port/1.21.1` and `port/1.20.1`) are recorded through source commit
`4b0e818e`. The readiness review covered `465fe891`, 90 commits beyond that marker.
Preparation commit `48fffdfb` then closed the identified item-data facade gaps, strengthened
validation, and refreshed the baseline notes. Use `48fffdfb` as the proposed source snapshot;
confirm or deliberately update that snapshot when implementation starts.

Port the final features in dependency order. Do not replay all intermediate implementations:
the backlog includes companion motion, grouping, UI and scene designs that were subsequently
replaced or deleted. Keep the existing target branches and their version adaptations.

Complete the 1.21.1 catch-up first (Fabric + NeoForge), then follow the same sequence for
1.20.1 (Fabric + Forge). Preserve the existing full-parity scope and documented necessary
version differences. Do not redo the original rendering or dependency backports wholesale.

## Implementation stages

Each stage includes its tests and ends at a working milestone before the next stage begins.
Stages may be divided further when compiler results reveal a larger dependency boundary.

| Stage | Scope | Completion evidence |
|---|---|---|
| 1. Establish the baseline | Confirm the source snapshot, inspect the existing target worktrees, run their baseline gates, and establish a feature checklist. Preserve untracked handoff files and local work. Check existing gelatin-ui dependencies before proposing any library catch-up. | Recorded target heads, baseline results, and explicit feature scope. |
| 2. Shared foundations | Carry over the final Java-17-compatible simulation and tests, encyclopedia tiers, codec warmups, shared fish visual/pose refactors, and fishing-pool resolver. Adapt the test harness, including fresh GameTest worlds and bounded fixtures, early. | Simulation/common tests pass; relevant behavior and reference comparisons agree. |
| 3. Companion gameplay | Party storage, bonding, unlocks, inventory/menu operations, naming, packets, and persistence. Use each loader's appropriate backing behind CompanionStorage. | Inventory, rename, party, persistence, synchronization and tutorial-state tests pass. |
| 4. Companion presentation | Final movement adapters, rendering, perching, moods, world perception and cross-player scenes. Reuse existing target rendering infrastructure and adapt the new output code to it. | Relevant client scenes and two-client checks pass; visual comparison covers every locomotion class and the retained switches. |
| 5. Ambient fish | Server context/protocol, population and water logic, client simulation/rendering, lifecycle, and performance checks. Shared foundations must precede this stage. | Ambient parity, water, context, population, floor, walking, island and lifecycle checks pass. |
| 6. Onboarding | Latest fishing, encyclopedia and guided companion tutorials, including saved state, menus, highlighting and packet changes. Preserve serialized enum values. | Server-side tutorial tests and both tutorial client scenes pass. |
| 7. Final parity | Both loaders' full test/build gates, clean datagen, packaging, dedicated-server startup, client scenes, two-client scenes and visual review. | Evidence attached to each feature and target; documented version differences; no unaccounted feature gap. |

## Tracking and version boundaries

- Use one checklist row per feature, with source files/commits, target implementation commits,
  validation, deliberate exclusions and necessary version differences. Source commits identify
  provenance; they are not mandatory individual port jobs.
- Keep each overall ported-through marker at `4b0e818e` while the backlog is only partially
  complete. Advance it to the chosen source snapshot only after every feature and remaining
  source change is accounted for, including superseded history and source-only tooling.
- Retain the existing target-specific rendering, resource layout, data-component/NBT and
  network-codec adaptations. Extend the seams instead of scattering new version differences
  through callers. Measure unresolved API work by compiling against the actual target.
- Keep the Minecraft-free simulation source identical across versions wherever possible;
  it already compiles with `--release 17`. Verify deterministic behavior against the chosen
  source snapshot rather than relying on test counts alone.
- Compare version-neutral data after accounting for required path/format changes. Preserve
  documented substitutions for vanilla content missing on older versions.
- Carry the six retained A/B switches with their current defaults (all true): `driftBob`,
  `bobRetime`, `bobMindTurn`, `bobCalm`, `partySwimSeparation`, `otherPlayerClearance`.
  Their pending owner visual decisions remain pending; this plan does not approve or collapse them.
- Keep the existing release policy: gameplay parity and mod-version bumps across all three
  version lines. Publication and deployment are separate from this implementation outline.

## Validation reference

The source preparation evidence and intermittent backup-fixture failure are recorded in
[audit §9.5](audit-2026-10-15-companions-and-fish-in-world.md#95-backport-readiness-follow-up-2026-10-17).
The source readiness driver is [run-pre-backport-validation.ps1](run-pre-backport-validation.ps1);
adapt its commands and client harness to each target instead of assuming it runs unchanged there.
Run combined loader GameTests with `--no-parallel`, distinguish cached results from fresh runs,
and require actual test counts, assertions and completion markers rather than Gradle exit 0 alone.

The original port plans and parity ledger remain on the port branches under
`docs/backport-plan.md` and `docs/backport-pass2/`. Read those before implementation, together
with the canonical process and API references in `D:\GitHub\modding-guide` topics 19 and 20.
