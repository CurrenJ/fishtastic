# Fish in World — continuation handoff

Updated 2026-10-12 at the user's request to conserve remaining usage. This chat coordinates one phase implementation agent at a time. Finish documentation and already-running capture cleanup; do not start another phase in this session.

## Start here

1. [Feature contract](fish-in-world.md)
2. [Implementation phases](fish-in-world-implementation-plan.md)
3. [Phase 0 integration map and baseline](fish-in-world-phase0.md)
4. [Phase 1 implementation, activation and evidence](fish-in-world-phase1.md)

## Current milestone

**Phase 0 complete. Phase 1 runtime prototype implemented and regression-tested; final coordinator visual review and latest packaged JAR build remain pending. Phases 2–7 have not started.** Implementation agent stopped after writing its checkpoint. No new work is running on behalf of this chat.

Phase 1 extracts `FishVisuals` and `GroupFishPose`, preserving tank profile/size/locomotion mapping and planar pose arithmetic. Tank callers delegate to those helpers; a BER compatibility delegate preserves other existing callers. `ClientAmbientFish` provides an explicitly armed development fixture with a fixed world origin, four stable individuals, the existing group engine/domain, one render state per fish, world light, camera-depth ordering, culling, fixed tick stepping and lifecycle cleanup. Both loaders have tick/world-submit/reload/cleanup integration.

Normal play does not activate the fixture. There is no live pool selection, natural discovery, networking, equipment transition, production setting, or saved entity behavior yet. Do not describe this as the finished feature.

## Validation already observed

- Phase 0: fishsim 347 passed / 36 skipped; common 766 passed / 1 skipped; no failures. Four deterministic motion fixtures recorded, not Minecraft render proofs.
- Phase 1: common 778 passed / 1 skipped, including 12 new mapping and pose/normal regression tests; no failures. Fishsim remained up-to-date at its Phase 0 counts because its sources were unchanged.
- Both packaged builds passed before the later read-only camera snapshot and close-up harness additions. Latest helpers/harness compiled on both loaders. At checkpoint, final incremental packaging of those additions was still to be confirmed; consult the Phase 1 report/logs rather than assuming the older jars contain them.
- Real Fabric and NeoForge shore/underwater/tank-interior scenes completed with ten checks passing each: four tank inserts, activation, count, submission, off-camera continuity, freeze, cleanup.
- Fabric's species close-up run passed fourteen checks, adding calibrated lengths and declared tank locomotion checks. The coordinator inspected all four Fabric species comparisons; appearance/poses were coherent, with expected world/tank lighting and fog differences. One gliding tank shot was obscured by a frame, so an inward-camera refresh was added.
- Closeout confirmed: refreshed Fabric and NeoForge captures both finished, with fourteen scene checks passing on each. All 22 screenshots were curated. The latest NeoForge log records self-test completion and clean shutdown; the coordinator has not yet reviewed every refreshed image. Do not launch more clients just to repeat established checks.

Evidence lives under `fish-in-world-phase1/screenshots/`; disposable runs live under ignored `fish-in-world-phase1/run-fabric/` and `run-neoforge/`. A curated screenshot is in-game evidence, but these controlled volumes do not establish arbitrary-water containment or release performance.

## Workspace and concurrent work

Checkout: `D:/GitHub/fishtastic`, branch `26.1.2`. No commits or pushes were made for this feature. New feature files and documentation are currently untracked; do not accidentally omit them when later staging changes.

Another agent is working on fish-companion squid movement in this repository. Preserve its changes and other pre-existing docs/logs/assets. The ambient phase did not change fishsim core locomotion or companion movement sources. Check fresh status/diffs before editing shared files; shared rendering, loader entrypoints and the render self-test were changed by this phase.

Follow repository AGENTS.md and read the relevant canonical guide topic before changing a system. Do not reset the working tree or broadly clean untracked files. Existing self-test cleanup must stay confined to the checked, specifically named disposable world in its isolated run directory.

## Important design decisions to retain

- World fish should look nearly identical to tank fish; continue sharing the engine, descriptors and poses rather than introducing independently tuned motion.
- No held rod uses the registered standard-worms pool with local environment and neutral personal equipment. A held supported rod uses its actual setup, including no bait. Ambient previews consume nothing and never trigger cast effects.
- Future equipment transitions resolve context immediately, keep compatible identities/motion, retire excluded fish promptly and adjust weight-only abundance gradually. Repeated toggling/re-entry must not reroll rare sightings.
- Proposed visual mixture is 75% actual species probability plus 25% square-root-flattened probability. This is visual-only and remains tunable.
- Populations are client-local representations. Catch rewards and progression remain independent of visible individuals.

## Known integration risks for later phases

- `rebuildPreserving` carries local coordinates; moving origins need explicit world-preserving rebasing. It is not sufficient by itself for rolling water windows.
- `VoxelDomain` has synchronous distance-field construction and full-block union assumptions. Natural water needs bounded loaded-only discovery, fractional fluid/collision handling, full animated body clearance, disconnected-region handling and a boundary strategy that avoids an invisible player-centered tank.
- Existing fishing chooses a main-hand rod first, while actual offhand casting has an explicit hand. `ActiveSession` lacks complete species weights/origin/moon/casting-rod identity. Resolve those seams with tests in Phase 3 rather than guessing in the client.
- Vanilla water and tank interiors have different light/fog/surroundings. Shared matrix regressions and visual inspection establish the limited Phase 1 parity claim; screenshots are not expected to be pixel-identical.
- An initial uncapped isolated client crashed during world loading under host memory pressure. Bounded launches succeeded without stopping other work. Keep the capture init script's documented heap/resolution/render-distance settings; run one dev client at a time. Ordinary run directories and other agent processes were untouched.

## Resume sequence

1. Read Phase 1 report and inspect the 22 curated images, especially the refreshed gliding close-ups. Both capture runs finished; no new capture is needed unless review finds a real defect.
2. If final packaged artifacts still predate the read-only snapshot/harness additions, run one incremental `:fabric:build :neoforge:build` and record the result. No forced rerun of unchanged tests is needed.
3. Coordinator closes the limited Phase 1 prototype gate with the available evidence, keeping release/natural-water limits explicit.
4. On resumption, assign **Phase 2 only** to the next implementation agent: bounded loaded-water discovery and terrain/domain containment with a fixed pool. Preserve shared tank visuals and concurrent companion work. No fishing/networking changes belong in that phase.

No future phase or recurring automation has been started.
