# Backport parity progress

## Completed follow-up (2026-10-25): interactive-school encounter odds

- **Operation:** Carry the source defaults of 50% encounter chance every 6,000 ticks
  (five minutes) to `port/1.21.1` and `port/1.20.1`, build all supported loader jars for
  26.1.2, 1.21.1, and 1.20.1, sync the jars into the listed CurseForge profiles, then run
  release smoke checks on every profile. The exact pending operation and evidence path were
  recorded here before starting. Preserve unrelated worktree state; do not advance parity markers.
- **Starting state:** Source `ef84f32d` was version 2.1.7 with the odds change already in its
  working tree. Port baselines were 1.21.1 `341e6453` and 1.20.1 `73fe6fe6`, both at 2.1.7.
  The 1.21.1 tree was clean; 1.20.1 had only the pre-existing untracked
  `docs/fish-companions-twoclient/`, which remains untouched.
- **Result:** Source and both port worktrees now default to 6,000 ticks and 0.5 chance; timing
  notes and changelogs match, and all three versions are 2.1.8. All eight loader builds passed
  (`:fabric:build`, `:neoforge:build`, `:forge:build` on 26.1.2 and 1.21.1; `:fabric:build`,
  `:forge:build` on 1.20.1). Eight unique jars were synced into all ten allow-listed profiles.
  All ten profile smokes passed: eight `PASS`; 26.1.2 Forge and 1.21.1 Forge were
  `PASS_WITH_FINDINGS` after confirming vanilla's experimental-settings screen. The extra
  `F2ALPHA` Fabric and NeoForge profiles passed.
- **Smoke harness repair:** The first 1.20.1 Forge attempt stopped before launch because nullable
  `assetIndex` and `minecraftArguments` entries in the loader version chain were not handled.
  `tools/release-smoke/release_smoke.py` now skips null entries. The retry reached the world and
  passed; no jar issue was observed.
- **Backups:** Replaced profile jars were backed up to
  `D:\Curse Forge\Minecraft\_profile_backups\20261025-173622`,
  `D:\Curse Forge\Minecraft\_profile_backups\20261025-173820`, and
  `D:\Curse Forge\Minecraft\_profile_backups\20261025-173930`.
- **Evidence:** `build/backport-catchup/2026-10-25/school-odds/` contains all three sync/build
  logs, the first smoke transcript, the 1.20.1 Forge retry log, per-profile scratch logs and
  summaries, `run_smokes.py`, and `result-matrix.md`.
- **Commits:** Source `056bec03` (`Balance interactive fish school encounter odds`), 1.21.1
  `f30329a5` (`Backport fish-school encounter tuning to 1.21.1`), and 1.20.1 `e1788e7f`
  (`Backport fish-school encounter tuning to 1.20.1`) are separate commits. No branch was pushed,
  and no parity marker was advanced.
- **Next action:** None required for this request; the changes are committed on all three branches.

The original pinned catch-up backlog is complete on `port/1.21.1` and `port/1.20.1`.

The free-daily-reroll and coelacanth backports are implemented on source and both port branches.
All eight client launch smokes are recorded, including the merged 1.21.1 Forge module.
The 2026-10-23 docs/tooling catch-up is complete; the
isolated solo render check (`scripts/run_render_checks.py fabric --solo`) passed 14/14 on each
port (evidence under each worktree's `build/verification/two-client/fabric/`).

## Current parity state

- **Status (2026-10-27):** every 26.1.2 source commit through `8794a541` (`Add hellbender`) is on
  both port branches, ported or excluded as source-only docs/tooling. The commit-by-commit
  crosswalk past the markers is in
  [forward-port-log.md](backport-catchup/forward-port-log.md#post-marker-follow-ups-e21bc4a38794a541).
  Tips: 26.1.2 `8794a541`, `port/1.21.1` `eba88004`, `port/1.20.1` `1e7802ad`; all three are
  version 2.1.11 and all three worktrees are clean. The ported-through markers remain at
  `e21bc4a3` (each scoped follow-up chose not to move them); they can advance to `8794a541`
  without further porting. Nothing is pushed: before this docs commit 26.1.2 was 16 commits ahead of
  `origin`, and each port was 14 ahead.

- **Completed follow-up (2026-10-27): hellbender.** Source `8794a541` (`Add hellbender`, a new rare
  river species) was cherry-picked to 1.21.1 as `eba88004` and carried to 1.20.1 as `1e7802ad`.
  The 26.1-only client item definition `assets/fishtastic/items/hellbender.json` was dropped on
  both ports (no ported fish has one). The item model is the Fabric datagen output (no trailing
  newline, like the other ported models). On 1.20.1 the tag edits land in `tags/items/`.
  Validation, per port: all loaders' `compileJava` (1.21.1 Fabric/NeoForge/Forge, 1.20.1
  Fabric/Forge), `WorldEncounterTest` (solitary count 21 → 22), and Fabric datagen with no change
  beyond the new model (1.21.1 copied 1, 1.20.1 copied 0; 101 authored entries preserved, none
  pruned). Each commit hook's unit tests and Fabric GameTests passed. NeoForge/Forge GameTests and
  an in-game look were not run on the ports. The source passed 395/395 Fabric GameTests before its
  commit. No push.

- **Release and tuning follow-ups (2026-10-27):** `Prepare Fishtastic 2.1.9 release` (source
  `99325a8f`) maps to 1.21.1 `783dec27` and 1.20.1 `9fbc8479`. `Bump Fishtastic to 2.1.10`
  (`cdad5be3`) maps to `6b9dbfb2` / `6735c5f1`. `Make school visits always succeed and surface
  jumps come every 30-60s` (`091e2c58`) was cherry-picked as `88a6ce2f` / `82c3da88`. `Bump
  Fishtastic to 2.1.11` (`21eadae1`) maps to `7bb45c3b` / `eafc4d16`.

- **Completed follow-up (2026-10-27): wild-fish surface jumps.** Backport source commit
  `603ade5a92c7ddee91fe01d5462515f7bdb2e570` (`Let wild school fish leap out of the water now and
  then`) sequentially: apply and adapt on clean `port/1.21.1` at `783dec27`, validate and commit,
  then carry that result to clean `port/1.20.1` at `9fbc8479`, adapt, validate and commit. Preserve
  version-specific networking, saved-data, rendering, simulation and loader APIs. Validate common,
  fishsim and tank-shape-gen tests; all supported loader builds, testmod compiles and GameTests;
  Fabric datagen with an unchanged authored-resource/status diff; and the `fishschooljump` client
  scene on each target. Record command logs, captures, snapshots and a result matrix under
  `build/backport-catchup/2026-10-27/wild-fish-surface-jumps/`. Starting source is clean at
  `603ade5a`; target branches are clean at the baselines above, each nine commits ahead of its
  remote. The source commit is not in the pinned source-commit ledger; this scoped follow-up does
  not advance historical parity markers or push either branch. **Checkpoint (2026-10-27):** the
  `--no-commit` apply stopped on conflicts in `FishSchoolClientRuntime.java`, `GroupFishDraw.java`,
  and `RenderSelfTest.java`, plus source `docs/README.md` modified vs port-deleted. Apply transcript:
  `build/backport-catchup/2026-10-27/wild-fish-surface-jumps/1.21.1-apply.log`. **Next action:**
  conflicts are resolved with the port's immediate-mode renderer and old camera API, preserving its
  render self-test scenes and adding `fishschooljump`; the port-only README remains deleted. The
  design spec and all gameplay/simulation/test changes are staged. Forced `:common:compileJava`
  passed (only existing deprecation/unchecked warnings); log:
  `build/backport-catchup/2026-10-27/wild-fish-surface-jumps/1.21.1-common-compile.log`.
  Forced tests passed: common 914 tests (0 failures, 1 skipped), fishsim 516 passed/59 skipped,
  and tank-shape-gen 21,955 passed. Log:
  `build/backport-catchup/2026-10-27/wild-fish-surface-jumps/1.21.1-unit-tests.log`.
  Forced Fabric, NeoForge and Forge `build` tasks and all three `compileTestmodJava` tasks passed;
  log: `build/backport-catchup/2026-10-27/wild-fish-surface-jumps/1.21.1-build-testmod.log`.
  GameTests passed 389/389 on Fabric, NeoForge and Forge; logs:
  `1.21.1-{fabric,neoforge,forge}-gametests.log` in the same evidence directory. **Next action:**
  Fabric datagen passed: copied 0, preserved 101 authored entries, 5,513 unchanged, none pruned;
  pre/post Git status snapshots match. Log and snapshots are in
  `build/backport-catchup/2026-10-27/wild-fish-surface-jumps/`. **Next action:** run the Fabric
  `fishschooljump` client scene, then review and commit the 1.21.1 port. **Checkpoint (2026-10-27):**
  the first scene attempt failed 2 of 10 checks: the school copy disappeared before the surface
  goal was visible, leaving the apex offset at zero; the other 8 checks passed. The target log and
  all 24 captures are under `build/verification/two-client/fabric/c237f0198e0d42179990283f15ffbbfc/`
  in the 1.21.1 worktree. Diagnosis found `queueJumpPool` omitted the river-biome fill already used
  by the neighboring Bluegill scene fixture. Added the matching river-biome setup to the target
  `queueJumpPool` self-test fixture; the retry passed all 10/10 `fishschooljump` checks. Runner log
  `1.21.1-fishschooljump-retry.log`, with 24 arc captures under
  `D:\GitHub\fishtastic-worktrees\port-1.21.1\build\verification\two-client\fabric\7bc54818740a472e8fce3c39db8370e6\solo\screenshots\`.
  **Checkpoint (2026-10-27):** the forced final Fabric, NeoForge and Forge build/testmod matrix
  passed (48 tasks); log `1.21.1-build-testmod-final.log`. Final review found only the backport and
  the jump-scene fixture update. `git diff --check HEAD` initially caught one trailing blank line
  at the design spec EOF; it was removed and the check now passes. The validated changes were
  committed as 1.21.1 `785ba2c8c9ec7de6c1e3b56dd42c1c4eb8fcb359`; its worktree is clean. The
  repository commit hook's common tests and Fabric GameTests passed (NeoForge GameTests were skipped
  by that hook; the explicit 389/389 NeoForge run above passed). Carried
  `785ba2c8c9ec7de6c1e3b56dd42c1c4eb8fcb359` to `port/1.20.1` from clean baseline `9fbc8479`.
  Apply/build/test/game-test/datagen/scene logs and snapshots are under
  `build/backport-catchup/2026-10-27/wild-fish-surface-jumps/` with the `1.20.1-` prefix. The
  apply is recorded in `1.20.1-apply.log`; it stopped with conflicts in
  `GroupFishDraw.java`, `RenderSelfTest.java`, `FishSchoolSim.java`, and `FishSchoolWire.java`.
  Those conflicts are now resolved: the renderer retains 1.20.1's matrix/normal handling, the
  scene registry retains its port-only entries and adds `fishschooljump`, simulation dispatch uses
  Java 17-compatible `instanceof`, and packet encoding/decoding uses the existing `BufCodec` API.
  `git diff --check HEAD` passes. Forced `:common:compileJava --rerun-tasks` passed with only
  existing deprecation/unchecked warnings; log `1.20.1-common-compile.log`. Forced
  `:common:test :fishsim:test :tools:tank-shape-gen:test --rerun-tasks` exposed a test compile error:
  the new wire test uses 1.21's unavailable `RegistryFriendlyByteBuf`, while this port's `BufCodec`
  takes a Netty `ByteBuf`. That command completed with `:fishsim:test` passing 516 tests (59
  skipped) and `:tools:tank-shape-gen:test` passing 21,955; common tests did not compile. Adapted
  the wire test to encode/decode directly on `Unpooled.buffer()`. The failed first attempt remains
  in `1.20.1-unit-tests.log`; the successful retry is `1.20.1-unit-tests-retry.log`.
  **Checkpoint (2026-10-27):** retry
  passed common 927 tests (0 failures/errors, 1 skipped), fishsim 516 passed/59 skipped, and
  tank-shape-gen 21,955 passed. JUnit XML confirms the counts; retry transcript is
  `1.20.1-unit-tests-retry.log`. Forced Fabric and Forge builds plus both testmod compiles passed
  (35 tasks); log `1.20.1-build-testmod.log`. Fabric GameTests passed 389/389 and Forge passed
  390/390; logs `1.20.1-fabric-gametests.log` and `1.20.1-forge-gametests.log`. Fabric datagen
  passed: copied 0, preserved 101 authored entries, 5,511 unchanged, none pruned; pre/post status
  snapshots match (`1.20.1-pre-datagen-status.txt`, `1.20.1-post-datagen-status.txt`; log
  `1.20.1-datagen.log`). **Next action:** run
  `python scripts/run_render_checks.py fabric --solo --scenes fishschooljump --timeout 600` on the
  1.20.1 worktree, logging output to `1.20.1-fishschooljump-runner.log` and retaining its captures.
  **Checkpoint (2026-10-27):** the 1.20.1 scene passed 10/10 checks; runner log above and 24
  screenshots under
  `D:\GitHub\fishtastic-worktrees\mc-1.20.1\build\verification\two-client\fabric\a5ebfd5a59c745fe831123663efabce3\solo\screenshots\`.
  Final review also found the copied `GroupFishDraw` Javadoc named the 1.21.1 renderer, and the new
  packet roundtrip covered only `SurfaceGoal`. Updated the comment and added direct `SurfaceJump`
  roundtrip coverage; final forced `:common:test --rerun-tasks` passed (927 tests, 0 failures/errors,
  1 skipped), log `1.20.1-common-test-final.log`. Final `git diff --check HEAD` passes, no conflict
  markers remain, all 24 scene captures exist, and the reviewed diff contains only the backport.
  Committed as 1.20.1 `7e19cfa499e435c5b29b44d1683afa7e642157eb`; both target worktrees are clean
  and each branch is 10 commits ahead of its remote. Final matrix:
  `build/backport-catchup/2026-10-27/wild-fish-surface-jumps/result-matrix.md`. No push was made and
  historical parity markers remain unchanged. No pending work remains for this follow-up.

- **Completed follow-up (2026-10-26): in-world fish visit shapes and locomotion.** Port source
  commit `ed3dbfd61e84eb2deffbc9996911a5c8cb1cd12f` (`Open interactive in-world visits to most
  species with authored shapes`) in order `26.1.2 → port/1.21.1 → port/1.20.1`. Carry the full
  gameplay, saved-data and packet changes; profile schema and authored encounter data; tags and
  datagen; client cues/config/commands; fishsim; unit and GameTest coverage; and the encounter
  design documentation, adapting target APIs and loader registration. Baselines are clean
  1.21.1 `f30329a5` and 1.20.1 `e1788e7f`; both branches are eight commits ahead of their remotes after these local commits.
  Keep the historical source/port parity markers at `e21bc4a3` / `0c35f75c` / `861c251f`.
  Validate both targets with uncached common, fishsim and tank-shape-gen tests; every supported
  loader's main/testmod compile and GameTests; Fabric datagen with a clean authored-resource diff;
  and the applicable in-world encounter client scenes. Record logs, captures and a result matrix
  under `build/backport-catchup/2026-10-26/in-world-fish-visits/`. **Starting state:** source
  `ed3dbfd6` is clean; both target worktrees are clean at the baselines above. No push or marker
  advancement is included in this operation. **Checkpoint (2026-10-26):** the source change is
  applied to 1.21.1 with `git cherry-pick --no-commit`; 9 paths need manual resolution:
  `RenderSelfTest.java`, `FishtasticCommand.java`, `FishSchoolManager.java`,
  `FishSchoolSavedData.java`, `fish_school_enabled.json`, `FishtasticItemTagProvider.java`,
  `NeoForgeGameTestRegistration.java`, and the two source paths deleted by the port
  (`docs/README.md`, `ForgeFishtasticGameTests.java`). The target-specific portions are recorded
  in `build/backport-catchup/2026-10-26/in-world-fish-visits/1.21.1-apply.log`.
  All 9 conflicts are resolved: the command/manager/data classes use `ResourceLocation`, Fabric
  datagen uses `getOrCreateTagBuilder`, the derived-eligibility override is empty, the port's
  shared NeoForge test registration is retained, and deleted source-only docs/Forge harness files
  remain deleted. New GameTest wrappers are present in the shared harness. The first compile found
  a malformed shape-command chain; after matching the source builder nesting, the forced rerun
  reached two target API errors: `FishSchoolManager.readyByProfile` used `RegistryLookup` where the
  target API requires `Registry`, and `FishingMinigameManager.createSchoolTarget` passed a generic
  `Level` where its manager already holds the `ServerLevel`. The independent fishsim suite passed
  516 tests with 59 skipped. Log:
  `build/backport-catchup/2026-10-26/in-world-fish-visits/1.21.1-common-compile.log`.
  The profile lookup now uses the target `Registry` API, and the minigame reuses its existing
  `ServerLevel`. The clean `:common:compileJava` rerun passed. In the full sequential run,
  fishsim passed 516 tests (59 skipped) and tank-shape-gen passed 21,955; `:common:test` completed
  without a failure. Fabric main compilation passed, but `:fabric:compileTestmodJava` found six new
  wrappers using the 26.1 `@GameTest(structure, maxTicks)` attributes, which 1.21.1 lacks. Full
  log: `build/backport-catchup/2026-10-26/in-world-fish-visits/1.21.1-build-tests.log`.
  The wrappers now use `template="fishtastic_empty"` and `timeoutTicks`. The clean sequential
  rerun passed: `:common:test` 900 tests (0 failures, 1 skipped); `:fishsim:test` 516 passed/59
  skipped; `:tools:tank-shape-gen:test` 21,955 passed; Fabric, NeoForge and Forge builds and all
  three testmod compiles passed. Detailed log:
  `build/backport-catchup/2026-10-26/in-world-fish-visits/1.21.1-build-tests-final.log`; the
  pre-datagen status snapshot is `1.21.1-pre-datagen-status.txt`. The initial annotation failure
  remains recorded in `1.21.1-build-tests.log` and is superseded by the successful rerun.
  Fabric datagen passed: 0 files copied, 101 authored entries preserved, 5,513 unchanged, no files
  pruned; it wrote 2 files to generated output. The target `git status` is byte-for-byte unchanged
  from the pre-datagen snapshot, and the encounter tags match the empty allow override plus shrimp
  and fried-shrimp deny list. Log and snapshots:
  `build/backport-catchup/2026-10-26/in-world-fish-visits/1.21.1-datagen.log` and
  `1.21.1-{pre,post}-datagen-status.txt`. Fabric GameTests passed 389/389. NeoForge ran all 389 but
  failed the three new player-driven approach cases because their wrappers used vanilla's mock
  player supplier; its connection had not registered `fishtastic:quest_sync` yet. The failure was
  consistent across eel, drifter and crawler and not a behavior assertion. Log:
  `build/backport-catchup/2026-10-26/in-world-fish-visits/1.21.1-gametests.log`. The three wrappers
  now use `FishtasticTestSupport.playerSupplier(helper)`, which installs each loader's supported
  mock-player factory before test execution. The forced retry passed all three suites: Fabric,
  NeoForge and Forge each completed 389/389. Full retry log:
  `build/backport-catchup/2026-10-26/in-world-fish-visits/1.21.1-gametests-final.log`. The initial
  NeoForge failures remain documented and are superseded by this corrected run. **Next action:** run
  the three 1.21.1 fishschool render scenes, then port to 1.20.1. A combined shapes/classes/crab
  launch passed every shapes and classes assertion but failed the crab approach/bite probes because
  the shared pool retained the earlier class-scene crab. The encounter spec explicitly requires
  `fishschoolcrab` to run in its own launch. Reran it alone; all 4 checks passed. Combined diagnostic
  log: `1.21.1-render-scenes.log`; isolated crab log:
  `1.21.1-render-fishschoolcrab.log`. Captures are under target
  `build/verification/two-client/fabric/1711422abd9f4665ac6ea2302ee02271/solo/screenshots/`.
  The shapes and classes scene assertions and captures are in target
  `build/verification/two-client/fabric/427fb046dd3a417894ade499ec875ee6/solo/`. **Next action:**
  inspect and commit the validated 1.21.1 diff, then port it to 1.20.1. Review found only the
  intended target adaptations and a one-line command-builder correction; `git diff --check` passed.
  Commit hooks passed their uncached unit-test gate and Fabric GameTests 389/389 (the hook's
  documented NeoForge skip is covered by the separate manual Fabric/NeoForge/Forge 389/389 run).
  Commit: `5ffca717` (`Backport in-world fish visits to 1.21.1`). No other changes were committed.
  **1.20.1 operation (recorded before start):** verify `port/1.20.1` is clean at baseline
  `e1788e7f`, then apply validated port commit `5ffca717` with `git cherry-pick --no-commit` and
  adapt/test Minecraft 1.20.1 APIs. Evidence path:
  `build/backport-catchup/2026-10-26/in-world-fish-visits/`. Baseline was clean. The apply stopped
  on 8 target seams: `RenderSelfTest.java`, `FishSchoolSetupPacket.java`, `FishSchoolSim.java`,
  `FishSchoolWire.java`, `FishSchoolManager.java`, the `fish_encounter_disabled` tag path, the
  existing `fish_school_enabled` tag, and `FishSchoolGameTests.java`. Git correctly reports that
  the singular 1.21.1 tag directory must be moved to 1.20.1's plural `tags/items/`. Apply transcript:
  `1.20.1-apply.log`. Resolutions use the 1.20.1 BufCodec/FishtasticPayload network layer, Java 17-
  compatible `instanceof` dispatch and plural item-tag location. The first forced common compile
  found two target API differences: `Codec.validate` is unavailable and the raw `ByteBuf` has no
  `writeVarInt`. Member-range checks now use 1.20.1 `Codec.flatXmap`, and shape ordinals use
  `BufCodecs.VAR_INT`. The forced `:common:compileJava` rerun passed (two existing JEI deprecation
  warnings only); logs: `1.20.1-common-compile.log` and `1.20.1-common-compile-final.log`.
  The first broad run also passed `:common:test`, `:fishsim:test` (516 passed/59 skipped) and
  `:tools:tank-shape-gen:test` (21,955 passed), then found four no-argument `DataResult.getOrThrow`
  calls in new GameTest coverage. They now use the 1.20.1 `(allowPartial, errorConsumer)` API. Both
  `:fabric:compileTestmodJava` and `:forge:compileTestmodJava` now pass; log:
  `1.20.1-testmod-compile-final.log`. Earlier logs retain the schema and compile diagnostics.
  The final forced matrix passed: common 913 tests (0 failures, 1 skipped), fishsim 516 passed/59
  skipped, tank-shape-gen 21,955 passed, Fabric and Forge builds and both testmod compiles. Log:
  `1.20.1-build-tests-final2.log`; the first broad failure remains in `1.20.1-build-tests-final.log`.
  Fabric GameTests passed 389/389. Forge completed 390 tests with one failure in
  `liveSchoolApproachesAndTriggersOwnedBite`: the fixture's two members were `DEPARTED` by the
  300-tick assertion while 128 mock players were active in the shared GameTest batch. Failure log:
  `1.20.1-gametests.log`. **Next action:** isolate the four player-driven fish approach/bite tests in
  their own GameTest batch, then rerun both loader suites to determine whether this is batch pressure
  or a behavior regression. Fabric again passed 389/389. Forge's dedicated batch did not reduce the
  fixture population because completed tests retain their mock players, and Forge exposed a second
  failure: `schoolStaysInItsWaterAndStepsOncePerTick` had `water=1`; the live-approach test again
  departed. Both use synthetic bluegill ponds but did not bypass habitat eligibility, so the test
  world biome can reduce the scan to the anchor alone. With habitat bypassed in the two synthetic
  fixtures and the four approach/bite cases grouped in `in_world_fish_visits`, both loaders passed:
  Fabric 389/389 and Forge 390/390. Final log:
  `1.20.1-gametests-habitat-fixed.log`; earlier diagnostics are retained. Fabric datagen passed:
  copied 2, preserved 101 authored entries, 5,509 unchanged, pruned none. The pre/post status
  snapshots differ only in two expected generated tags: `fish_encounter_disabled` gained
  `replace: false`, and `fish_school_enabled` is empty because eligibility is now rule-derived.
  Evidence: `1.20.1-datagen.log`, `1.20.1-pre-datagen-status.txt`,
  `1.20.1-post-datagen-status.txt`. The combined `fishschoolshapes` and `fishschoolclasses` render
  run passed all 14 checks. Isolated `fishschoolcrab` passed 4/4. Logs: `1.20.1-render-scenes.log`,
  `1.20.1-render-fishschoolcrab.log`; captures are under target
  `build/verification/two-client/fabric/072153d6026b448bb0d46a76a8da682d/` and
  `build/verification/two-client/fabric/dc610ad76def4587a751a3c081210eef/`. Final diff review and
  `git diff --check` passed. The 1.20.1 commit hook passed uncached unit tests and Fabric GameTests
  389/389; NeoForge is skipped by hook policy, and Forge passed 390/390 in the separate run.
  Commit: `bbfe1fed573033f8ec149814b718f5ebbb590be3` (`Backport in-world fish visits to 1.20.1`).
  Final heads are 1.21.1 `5ffca717c3c448fb80fcab47312e5082520f209a` and 1.20.1 `bbfe1fed...`;
  both worktrees are clean, eight commits ahead of their remotes. Neither branch was pushed; historical
  parity markers remain `e21bc4a3` / `0c35f75c` / `861c251f`. **Next action:** none for this operation;
  both ports, validation suites and evidence records are complete.

- **Completed follow-up (2026-10-25): fish-school bite/trail polish.** Source commit `e21bc4a3`
  (`Polish fish school bites; colour trails by quality`) is backported in order
  `26.1.2 → port/1.21.1 → port/1.20.1`, with version-specific packet/rendering APIs preserved.
  The 1.21.1 port commit is `d8a09e1e`; the 1.20.1 port commit is `aaab94d3`. Source commit
  `c311019d` is release-smoke-only tooling and is excluded by design; `1e4945bf` is a source-only
  progress-ledger update and is excluded. Baselines were 1.21.1 `62b2b838` and 1.20.1 `77c0f5fd`.
  1.21.1 validation passed: common 878 tests (0 failures, 1 skipped), fishsim 516 passed/59
  skipped, all three loaders' main/testmod compiles, and Fabric/NeoForge/Forge GameTests 383/383.
  1.20.1 validation passed: common 891 tests (0 failures, 1 skipped), fishsim 516 passed/59
  skipped, Fabric/Forge main/testmod compiles, and Fabric/Forge GameTests 383/383 and 384/384.
  The mixed-quality 1.20.1 `fishschoolgif` capture passed all scene checks with zero replica
  mismatches and 72 real-renderer frames across three views. Both commits' normal hooks passed;
  hooks skip NeoForge GameTests by policy, covered by the separate 1.21.1 run. Preserve the
  existing 1.20.1 untracked `docs/fish-companions-twoclient/`. After auditing the complete
  post-marker range, both ported-through markers now point through source `e21bc4a3`; the feature
  commits are `d8a09e1e` and `aaab94d3`, and the marker-recording commits are `0c35f75c` and
  `861c251f`. No push was made. Evidence and the result matrix:
  `build/backport-catchup/2026-10-25/fish-school-polish/`.
- **Completed follow-up (2026-10-25): final catch-up audit.** Audited every source commit in
  `fa103289..e21bc4a3`: 13 ported to both branches and 8 excluded as source-only progress/tooling.
  Verified all 91 pinned-ledger mappings against the target histories and corrected the stale
  1.21.1 statuses. Both ported-through markers are now `e21bc4a3`, recorded by commits
  `0c35f75c` (1.21.1) and `861c251f` (1.20.1); the source feature commits are `d8a09e1e` and
  `aaab94d3`. The full crosswalk is
  `docs/backport-catchup/forward-port-log.md`; current-head validation evidence is in
  `build/backport-catchup/2026-10-25/fish-school-polish/result-matrix.md`. The existing untracked
  1.20.1 companion-doc directory is preserved; manual owner checks remain open and no push was
  made.
- **Completed follow-up (2026-10-25): interactive fish schools.** Backport source commit
  `a31a9298` (`Add interactive fish schools`) in order `26.1.2 → port/1.21.1 → port/1.20.1`,
  adapting APIs and loader wiring per target. Include gameplay/networking, client simulation and
  rendering, fishsim changes, resources/datagen, tests, and relevant feature docs. Validate each
  target with focused compile/unit/GameTest gates and client/loader checks where practical. Keep
  the historical pinned-backlog markers unchanged. Record logs and a result matrix under
  `build/backport-catchup/2026-10-25/interactive-fish-schools/`; treat any interrupted run as
  unverified. Baselines inspected: 1.21.1 `ba12b1a3` has an existing modified
  `docs/backport-plan.md`; 1.20.1 `e529cd35` has the same existing modification plus untracked
  `docs/fish-companions-twoclient/`. Preserve all three. **Checkpoint (2026-10-25):** the shared
  1.21.1 source delta is applied. Source-only docs and platform test-registration files were
  excluded for manual target adaptation. The port now uses 1.21.1 immediate-mode fish rendering,
  has all three school payloads registered, preserves both ServerLevel mixins, and discovers the
  five school GameTests through the target shared harness. The first compile surfaced expected
  version differences (`ResourceLocation`, `SavedData.Factory`, and the 1.21.1 block-change hook);
  those are adapted. **Pending operation:** rerun `:common:compileJava` and `:fishsim:test`; log at
  `build/backport-catchup/2026-10-25/interactive-fish-schools/1.21.1-common-compile.log`.
  The initial compile is unverified. The second attempt passed `:common:compileJava` and
  `:fishsim:test` (`BUILD SUCCESSFUL`); the intervening attempt found RegistryAccess and
  camera-vector differences, which are now adapted. The loader compile found one source-version
  inventory API mismatch in the new school GameTest helper; changed it to the target's
  `Inventory.items` list. The clean rerun passed `:common:test` (78 passed) and `:fishsim:test`
  (516 passed, 59 skipped) but failed Fabric main compilation because this target uses
  `getOrCreateTagBuilder`, not the source's `valueLookupBuilder`. Adapted the opt-in school tag.
  The final forced rerun passed all three loader main compiles, all three testmod compiles,
  `:common:test` (78 passed), and `:fishsim:test` (516 passed, 59 skipped); see
  `build/backport-catchup/2026-10-25/interactive-fish-schools/1.21.1-loader-compile.log`.
  Fabric datagen passed and wrote one generated file, the intended
  `tags/item/fish_school_enabled.json`; sync preserved 101 authored entries and left 5,511
  generated entries unchanged. Log: `build/backport-catchup/2026-10-25/interactive-fish-schools/1.21.1-datagen.log`.
  Fabric GameTests passed: 383 total, 0 failures, including all five school cases; report is
  `port-1.21.1/fabric/build/junit.xml`, full log at
  `build/backport-catchup/2026-10-25/interactive-fish-schools/1.21.1-fabric-gametest.log`.
  `:neoforge:runData` could not configure because Loom reported no `dataServer` Forge run template;
  the shared GameTest structure files are already present under `common/src/testmod/resources`.
  First `:neoforge:runGametest` attempt ran 383 tests and had one transient school approach failure
  (mock-player hook link disappeared around tick 28). The full-suite retry passed all 383 required
  tests, including all five school cases; logs are `1.21.1-neoforge-gametest.log` (first attempt)
  and `1.21.1-neoforge-school-only.log` (successful full-suite retry; the filter was not forwarded).
  `:forge:runGametest` passed all 383 required tests. Log:
  `build/backport-catchup/2026-10-25/interactive-fish-schools/1.21.1-forge-gametest.log`.
  The corrected Fabric render scene passed every school check: 4 members spawned; the server and
  client copies remained present in broadside, reverse, and surface views; the close-up followed a
  fish for 60 frames; and the final client replica was caught up with zero mismatches. Captures and
  logs are in `build/backport-catchup/2026-10-25/interactive-fish-schools/1.21.1-fabric-render/`.
  The temporary stage assertions remain in the self-test harness, which now assigns synthetic
  school pools a river biome so Bluegill remains habitat-valid after its forced debug spawn.
  Created a feature-only patch from the adapted 1.21.1 base, excluding its pre-existing plan edit.
  Three existing-file hunks needed manual adaptation (`AmbientFishRuntime.java`, `RenderSelfTest.java`,
  `FishtasticPackets.java`); all other files applied. The port uses the 1.20 `FishtasticPayload` /
  `BufCodec` channel shim, older `SavedData` API, Java 17 syntax, and plural `tags/items` directory.
  The source ambient renderer shares the extracted draw helper with 1.20 pose/normal-matrix handling.
  `:common:test`, fishsim, and forced Fabric/Forge main plus testmod compilation now pass.
  Fabric datagen passed (`BUILD SUCCESSFUL`, copied 1, preserved 101 authored entries, 5,509
  unchanged, no files pruned). Fabric GameTests ran 383 tests and failed the new
  `liveSchoolApproachesAndTriggersOwnedBite` scenario on its first run. The diagnostic showed the
  sim advanced 300 steps with both members available, `inputs=0`, and `hookReport=Your bobber: no
  line out.` Restoring `player.fishing = hook` on each callback kept the mock bobber available; the
  rerun passed all 383 Fabric GameTests. Forge GameTests passed all 384 tests. The 1.20.1
  `fishschoolgif` client smoke passed on retry in an isolated run directory. The first attempt was
  held at Minecraft's first-run accessibility prompt; bringing the window forward and continuing
  let the scene run. All 13 checks passed: four Bluegill rendered in broadside, reverse and surface
  views; the server and client remained present; the client replica stayed caught up with zero
  mismatches; and the close-up followed a fish for 60 frames. The run produced 72 view frames plus
  60 close-up frames. Gradle/client logs and representative captures are under
  `build/backport-catchup/2026-10-25/interactive-fish-schools/1.20.1-fabric-render-retry/`. The
  original `fabric/run` world/logs were untouched, and the temporary run configuration and marker
  were restored/removed. All requested functional and visual validation gates pass on both targets.
  Historical port markers remain unchanged; no repository operation remains pending.
- **Completed operation (2026-10-24): coelacanth backport.** Source `c20dac1a` (`Add coelacanth`)
  is committed on both ports: 1.21.1 `c0f8415e` and 1.20.1 `8ba9199d` (`Backport coelacanth to
  <mc>`), 15 feature files each: item registration, creative tab entry, English name, profile and
  encyclopedia lore, item model and texture, six item tags, and Fabric datagen output. 1.21.1 uses
  the legacy item model format without the newer client-item definition; 1.20.1 keeps its plural
  `tags/items` paths and `replace: false` fields.
  - Validation: Fabric datagen passed on both ports (1.21.1: 1 copied, 5,510 unchanged; 1.20.1:
    1 copied, 5,508 unchanged; none pruned). Aggregate builds and uncached unit gates passed
    (1.21.1 common 863 passed/1 skipped; 1.20.1 common 876 passed/1 skipped; fishsim 500/59
    skipped and tank-shape-gen 21,955 on both). GameTests: 1.21.1 Fabric, NeoForge and Forge
    378 each; 1.20.1 Fabric 378, Forge 379. Each commit's pre-commit hook passed.
  - Release smoke (2026-10-24): the 2.1.6 release jars of both ports, which contain the
    coelacanth, booted in their CurseForge profiles and loaded a tank world via
    `tools/release-smoke/run_matrix.py`: 1.21.1 Fabric/NeoForge and 1.20.1 Fabric/Forge PASS;
    1.21.1 Forge PASS_WITH_FINDINGS (vanilla's experimental-settings screen, which source
    26.1.2 Forge shows too; not a port defect).
  - Datagen caveats, unchanged and not blocking: 1.21.1 NeoForge `runData` fails at
    configuration (Loom finds no `dataServer` run template); 1.20.1 Forge `runData` initialised the
    registries, reported zero providers and stalled, so it was interrupted and is unverified.
  - Evidence: `build/backport-catchup/2026-10-23/coelacanth/` (validation logs and commit logs
    under `validation/`).
- **Release follow-ups (2026-10-24):** `Bump Fishtastic to 2.1.6` (source `3cde87e4`) maps to
  1.21.1 `801b1004` and 1.20.1 `704dbfee`. `Publish Forge jars from publishCurseForge` (source
  `3834ead3`) maps to 1.21.1 `ba12b1a3` and 1.20.1 `e529cd35`. 1.20.1 already uploaded Forge, so
  its port adds only the `-Pcurseforge_loaders=` subset option (fabric, forge; `remapJar` jars).
  Dry runs (`publishCurseForge -m`) confirmed default = both jars, `fabric` / `forge` = that jar
  alone, and `neoforge` rejected as unknown; its pre-commit hook passed 378 GameTests.
  Source-only tooling `71214244` (release smoke test) drives all three branches from the source
  checkout and is not ported.
- Neither ported-through marker changed; no push was made. Preserve 1.21.1's and 1.20.1's
  unrelated `docs/backport-plan.md` edits and 1.20.1's untracked `docs/fish-companions-twoclient/`.

- **Completed operation (2026-10-23):** Extracted the authoritative reroll action into a shared
  callable operation and added shop_reroll_interaction GameTest coverage for free use, denied
  second use without tokens, escalating paid costs, changed draws, and the next in-game day's reset.
  The interaction check is registered across all eight builds. Each final run completed
  successfully:
  - 26.1.2 Fabric: selected reroll check, 1/1 passed.
  - 26.1.2 NeoForge: reroll check plus loader base test, 2/2 passed.
  - 26.1.2 Forge: reroll check plus loader base test, 2/2 passed.
  - 1.21.1 Fabric: full suite, 378/378 passed; the optional test filter is unsupported by this
    older Fabric registration path.
  - 1.21.1 NeoForge: full suite, 378/378 passed; registration auto-discovered the suite.
  - 1.21.1 Forge: selected reroll check, 1/1 passed.
  - 1.20.1 Fabric: full suite, 378/378 passed; the optional test filter is unsupported by this
    older Fabric registration path.
  - 1.20.1 Forge: full suite, 379/379 passed; registration auto-discovered the suite.
  Evidence, including raw GameTest logs, available JUnit reports, and the result matrix:
  build/backport-catchup/2026-10-23/free-daily-shop-reroll/shop-interaction/. An initial Forge
  run on source and 1.21.1 exposed a missing runGametest dependency on forgeCommonJar; the
  dependency was added and both final Forge reruns rebuilt the shared jar and passed. Earlier
  unsupported/incomplete selection attempts and pre-fix stale-jar runs are unverified and are not
  counted. Do not advance parity markers.
- Pinned source backlog: complete through `48fffdfbda1687cd42d59cb9fea0540e77aec982`.
- Current parity markers: source `e21bc4a3`; 1.21.1 marker commit `0c35f75c`; 1.20.1 marker
  commit `861c251f`.
- The scoped cosmetic-placement follow-up is complete without advancing those markers. Source
  commits `83fd745d` and `933214c5` map to 1.21.1 commits `9481dc9b` and `f9097e39`, and 1.20.1
  commits `edf20f2c` and `d44d7ee0`.
- The completed follow-up passed focused compilation and Fabric GameTests. No push was made.
- Docs/tooling catch-up (2026-10-23), also without advancing the markers: source `cd43f8cf`,
  `2b1ed674` and `ea4ec7da` map to 1.21.1 `8d721dbb`, `7b61f058` and `1120f02a`, and 1.20.1
  `dc24388f`, `10f82391` and `81044ce2`. Docs that never existed on a port stay absent; each port
  keeps its own `fish-tank-rendering.md` and `quest-system.md`. The `RenderSelfTest` split was
  re-applied to each port's adapted members by per-member three-way merge (the source split is
  purely mechanical; port-only scene bodies are unchanged). `scripts/twoclient.init.gradle` keeps
  1.20.1's flat `generator-settings`. Every commit passed the port pre-commit hook (unit tests and
  Fabric GameTests). Evidence and the split tooling: `build/backport-catchup/2026-10-23/`.
- **Completed follow-up (2026-10-23):** Ported the source working-tree change “one free quest shop
  reroll per in-game day” to `port/1.21.1` at `1120f02a`, then carry that result to
  `port/1.20.1` at `81044ce2`. Keep paid reroll escalation separate from the free-use flag and
  advance neither parity marker. Evidence paths:
  `build/backport-catchup/2026-10-23/free-daily-shop-reroll/1.21.1/` and
  `build/backport-catchup/2026-10-23/free-daily-shop-reroll/1.20.1/`. 1.21.1 target edits began
  after the plan and topic 19/20 review.
- **Checkpoint:** The 1.21.1 state, sync, shop UI, packet callers, platform sync handlers, strings,
  documentation, and nested `StreamCodec` packet record are adapted. The first 26.1.2 Fabric
  compile caught a packet-composite arity error; the nested record fix is applied on source and
  1.21.1. Source 26.1.2 Forge and NeoForge, all three loaders on merged `port/1.21.1`, and both
  loaders on `port/1.20.1` launched after their fixes. The merged 1.21.1 Forge handler now forwards
  the daily reroll nonce and availability to the client cache. An initial attempt used a separate
  Forge-support worktree by mistake; the correct port checkout was then compiled and launched.
  Evidence:
  `build/backport-catchup/2026-10-23/free-daily-shop-reroll/client-smoke/matrix.md`.
- **Completed operation (2026-10-23):** Adapted the free daily quest-shop reroll from the completed
  1.21.1 working tree to `port/1.20.1` at `81044ce2`, including saved state/legacy codec fallback,
  server sync, shop draw nonce, UI labels/costs, packet constructors, Fabric/Forge handlers, and
  port-local quest-system docs. Preserve the existing untracked
  `docs/fish-companions-twoclient/`. Evidence path:
  `build/backport-catchup/2026-10-23/free-daily-shop-reroll/1.20.1/`. Do not run builds/tests or
  advance parity markers in this operation.
- **Checkpoint:** 1.20.1 implementation is adapted, including its nested shop-reroll packet codec
  for the fixed-arity `BufCodec` API. The unrelated untracked directory is preserved. Both Fabric
  and Forge clients launched and loaded Fishtastic screens/network/model resources. Evidence note:
  `build/backport-catchup/2026-10-23/free-daily-shop-reroll/1.20.1/operation.md`. Run the normal
  port validation gates before treating either backport as fully verified; leave both parity
  markers unchanged.
- **Completed smoke correction (2026-10-23):** The Forge support docs were current: Forge 1.21.1
  had already merged into `port/1.21.1` (PR #7). Corrected the matrix to use that merged checkout.
  Its first Forge compile found a stale `QuestClientCache.update` call; updated
  `forge/src/main/java/grill24/fishtastic/forge/FishtasticForgeClient.java` to forward
  `packet.shopRefreshNonce()` and `packet.freeShopRerollAvailable()`, then reran
  `:forge:runClient` with Java 21. Compilation succeeded and Fishtastic initialized screens/network
  and loaded model resources/texture atlases before Ctrl+C. Updated evidence:
  `build/backport-catchup/2026-10-23/free-daily-shop-reroll/client-smoke/matrix.md` and
  `build/forge-support/2026-10-23/free-daily-reroll-client-smoke/README.md`. The separate
  `codex/forge-1.21.1` startup run was extra and is not counted. Keep parity markers unchanged;
  next run normal port validation gates if needed.
- **Completed documentation correction (2026-10-23):** Both port copies of `docs/backport-plan.md`
  had the stale September loader matrix. Updated each current matrix to include the merged Forge
  modules and added a current-support note citing Forge support in 2.1.2, PR #7 (1.21.1) and PR #8
  (26.1.2), while retaining D2 as a historical planning decision. No parity markers advanced.
- The six A/B switches remain enabled at their current defaults pending owner visual review.

## Remaining owner checks

These are the outstanding manual parity checks recorded at the last checkpoint:

- Real-play feel.
- F04 tank-frame equivalence.
- Real multiplayer beyond the scripted scene.
- Loading an old world in `WAITING_FOR_CAST`.
- F02 create-world behavior.

The later cosmetic-placement work does not close these checks.

## Resuming parity work

Read the complete
[feature ledger](backport-catchup/features.md), and the
[source commit ledger](backport-catchup/source-commit-ledger.json). Follow the backport-specific
instructions in `AGENTS.md` and use the target branches' existing porting plans and the canonical
modding guide's topics 19 and 20.

Before a new operation, record its exact action and evidence path here. Record its result and next
action at each checkpoint and before ending the session. An interrupted operation is unverified.
Do not advance a ported-through marker until the complete pinned backlog is accounted for. Preserve
unrelated edits and target runtime files.

## Scope and safeguards

Carry final source behavior while preserving Minecraft/loader adaptations; account
for superseded intermediate changes rather than replaying them. Target branch
plans remain under `docs/backport-plan.md` and `docs/backport-pass2/` on those
branches. Canonical guide topics 19 and 20 cover backport APIs and workflow.
The visual switches `driftBob`, `bobRetime`, `bobMindTurn`, `bobCalm`,
`partySwimSeparation` and `otherPlayerClearance` retain their current defaults
until owner review. Publication/profile deployment are separate operations.
