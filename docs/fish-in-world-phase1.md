# Fish in World - Phase 1 handoff

Session continuation checkpoint: [fish-in-world-handoff.md](fish-in-world-handoff.md).
Status: **gate closed 2026-10-12.** Final visual review of all 22 curated images passed, and the
packaged 2.0.1 jars were rebuilt and verified to contain the ambient classes. Phase 2 is next.

## Gate closure (2026-10-12, after the stop)

- Visual review: the reviewer inspected the refreshed glide/swim/drift close-up pairs and the
  shore/submerged scenes on both loaders. Sprites, calibrated scale and poses read coherently in
  world water against their tank counterparts; differences stayed in ordinary world light, fog and
  glass, as expected. No defect found; no capture rerun needed.
- Packaged build: `:fabric:build :neoforge:build` succeeded; `fishtastic-fabric-2.0.1+26.1.2.jar`
  (23:39) and `fishtastic-neoforge-2.0.1+26.1.2.jar` (23:40) both contain `ClientAmbientFish`,
  `FishVisuals` and `GroupFishPose`, and the embedded `TankFlockAdapter` references `FishVisuals`.
  Note the old `2.0.2` jars in `build/libs` are Oct-4 leftovers of a different version epoch —
  ignore them.
- The gate remains explicitly limited: fixture-domain containment only, no natural-water or
  release-performance claims. Phase 2 owns those.

## Implemented

- `FishVisuals` shares tank profile animation/calibration lookup, recorded-size conversion, locomotion/species/shelter mapping and descriptors. Tank entrypoints delegate; the BER retains a compatibility delegate so companion/debug files were not migrated.
- `GroupFishPose` shares the exact existing planar tank animation/transform sequence, retaining float operation order and deferred per-fish render states. The legacy lone-tank motion path is preserved.
- `ClientAmbientFish` is an opt-in **development fixture**, with four stable IDs in a fixed world-origin 4x2x3 loaded source-water volume, `FlockEngine(Tunables.GROUP)`, per-fish world light, conservative frustum bounds and camera-depth ordering. Ticks advance once per world tick; render only interpolates; pause/freeze, disconnect/dimension/water invalidation and resource/tag reload clean up state.
- Both loader lifecycle/tick/world-submit hooks are wired. `RenderSelfTest` has an opt-in `ambientparity` scene with paired volumes, overview/submerged views, frozen species closeups and continuity/freeze/cleanup checks. Read-only tank/fixture pose snapshots support capture cameras. Self-test deletion checks its normalized named disposable-world target first.

Fixed cast: bluegill 30 cm (0.267 blocks/swim), acute iaspis 45 cm (0.5355/glide), Portuguese man o' war 40 cm (0.356/drift), plaice 35 cm (0.385/benthic). These are authored mean sizes; activation rejects locomotion demotion instead of silently clamping. Giant manta sizes would not fit this fixture.

No core locomotion, companion, fishing, networking, equipment, natural discovery or release defaults were changed. The concurrent squid/companion work was preserved.

## Verified

| Suite | Tests | Passed | Skipped | Failures/errors |
|---|---:|---:|---:|---:|
| fishsim | 383 | 347 | 36 | 0 / 0 |
| common | 779 | 778 | 1 | 0 / 0 |

Counts come from JUnit XML. Fishsim reused Phase 0's executed baseline because its sources were unchanged. Common ran after extraction and the float-exact correction: all tank regressions and 12 new mapping/pose matrix fixtures passed. Existing platform source/ID/API guards passed; no GameTests ran. [Packaged build log](fish-in-world-phase1/build.log) passed before the later read-only camera snapshot/closeup additions. Both loader compiles subsequently passed with those additions: [latest compile log](fish-in-world-phase1/closeup-compile.log). **Packaged JARs do not yet reflect those latest additions; do not deploy them as the final Phase 1 artifact.**

Both loaders completed their final inward-camera refresh with 11 images and all 14 ambient checks PASS on each. The coordinator confirmed NeoForge self-test completion and copied its final images into the curated directory before ending the session. Final visual review remains pending; no additional client capture is required unless review finds a defect.

Curated images: [screenshots directory](fish-in-world-phase1/screenshots). All 22 files are from the latest refresh on their loader. Key Fabric comparisons: [world glide](fish-in-world-phase1/screenshots/selftest-fabric-ambientparity-world_acute_iaspis.png), [tank glide](fish-in-world-phase1/screenshots/selftest-fabric-ambientparity-tank_acute_iaspis.png). Capture logs: [Fabric](fish-in-world-phase1/capture-fabric.log), [NeoForge](fish-in-world-phase1/capture-neoforge.log).

Visual inspection established coherent species sprites, calibrated scale and poses, with expected ordinary-water fog/glass/world-light differences from the tank. Seeds, positions, warmup and some camera distances differ; **these are not pixel-identical parity images**. Coordinator review of final NeoForge images remains outstanding. Arbitrary natural-water containment/performance are Phase 2 work.

## Resume exactly here

Phase 1 is closed. Phase 2 (bounded natural-water domains) is implemented and captured on both
loaders — see [fish-in-world-phase2.md](fish-in-world-phase2.md) — with its gate awaiting an owner
look at the screenshots; phase 3 is next per the implementation plan. Keep the shared tank visuals
and concurrent companion work intact.

If a capture must be repeated, the reproducible isolated commands are:

```powershell
.\gradlew.bat :fabric:runClient -I docs/fish-in-world-phase1/capture.init.gradle -PambientLoader=fabric --console=plain --no-daemon '-Dorg.gradle.jvmargs=-Xmx768m -XX:ActiveProcessorCount=4'
.\gradlew.bat :neoforge:runClient -I docs/fish-in-world-phase1/capture.init.gradle -PambientLoader=neoforge --console=plain --no-daemon '-Dorg.gradle.jvmargs=-Xmx768m -XX:ActiveProcessorCount=4'
```

The init script arms only ignored `run-<loader>` profiles, skips accessibility onboarding, and caps capture at 960x540/6-chunk render distance/30 FPS/1.5 GB client heap/four processors. Normal dev worlds/markers are untouched. Initial absolute Windows runDir concatenation and onboarding were corrected in the script. An uncapped client crashed before the scene with NVIDIA `GL_OUT_OF_MEMORY` and native malloc failure; bounded retries succeeded without stopping the companion agent's processes. [Failure excerpt](fish-in-world-phase1/capture-fabric-memory-failure.log); full native crash report remains in ignored `run-fabric/hs_err_pid65032.log`.

To arm another dev harness, prepare exactly 4x2x3 loaded, collision-free source-water cells, call `ClientAmbientFish.startFixture(minWaterCell)` on the client thread, then `clear()` when finished. The manager never places blocks and production activation returns false.
