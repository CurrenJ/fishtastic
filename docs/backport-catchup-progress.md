# Backport parity progress

Last checkpoint: 2026-10-22, checkpoint 131. Both port parity markers now include the scoped post-`bc73a5fc` fixes through source commit `fa103289`.

## Resume here

Profile deployment (2026-10-19): all ten allow-listed dev profiles now run the verified `2.1.1` jars, replacing `2.0.3-dev.20261018164715`. Each installed Fishtastic jar matches its branch build artifact by SHA-256, each profile has exactly one Fishtastic jar, and every profile retains Gelatin UI `1.0.37` for its MC version. Created `F2 1.21.1 Forge` from `Template Pack Forge 1.21.1` (MC 1.21.1 / Forge 52.1.0) and `F2 26.1.2 Forge` from the corrected `Template Pack Forge 26.1.2 (1)` (MC 26.1.2 / Forge 64.1.0), both in group `Fishtastic2 Testing`. No deployment work remains. The original `Template Pack Forge 26.1.2` folder is still MC 26.3 / Forge 66.0.5 and is now unused. Evidence: CurseForge instance `mods/` folders and `_profile_backups/20261019-140302`, `20261019-140457`, `20261019-140503`, `20261019-141032`.

Release 2.1.1: All six builds and required-dependency checks passed; all three publish tasks succeeded with two file IDs each. Six post-upload hashes match pre-upload artifacts. Every upload has requiredDependency gelatin-ui; each jar requires Gelatin >=1.0.37+Minecraft. Durable report with file IDs and links: docs/backport-catchup/release-2.1.1.md. Raw evidence: build/release-2.1.1/. No upload retries, commits, pushes, profile deployments or parity-marker changes. All requested release work is complete; public moderation/visibility remains unverified. Preserve unrelated edits and runtime files.

Release 2.1.1: All three branch builds passed; all six final jars pass CRC, version, required Gelatin >=1.0.37+Minecraft and bundled-class checks. Both uploads on every branch require CurseForge slug gelatin-ui. Pending RELEASE-211-PUBLISH: publish the six verified files to project 1513269 as Release with dependency-fix changelog, recording INFO-level file IDs and per-branch results under build/release-2.1.1/. Do not repeat successful uploads. Raw manifest: build/release-2.1.1/artifacts.json.

Release 2.1.1: Owner requested patch release 2.1.1 on all six builds and explicitly authorized CurseForge publication. Both backports have the same optional Gelatin metadata defect. Pending RELEASE-211-BUILD: fix all loader manifests and CurseForge requirements, set all branches to 2.1.1, build each branch with :fishsim:jar and both loader :build tasks, then inspect all six final jars and upload configuration. Evidence: build/release-2.1.1/ and docs/backport-catchup/release-2.1.1.md. Preserve unrelated source ambient edits and port runtime files. No parity marker changes.

Release 2.1.0: all six verified jars submitted successfully as CurseForge Release files with the requested one-line changelog; all three signed release commits and required hooks passed. Source 0b84e7e3, 1.21.1 e06344aa, 1.20.1 e7ff95df. No pushes. Owner approved all-version playtests. Durable evidence: `docs/backport-catchup/release-2.1.0.md`; raw logs: `build/release-2.1.0/`. Public file IDs/moderation/visibility remain unverified because read checks were unavailable; do not rerun publishing to obtain them.

1. Read the LAST 5 checkpoints first. The original pinned catch-up is COMPLETE on both targets (checkpoint 106); the branch-local parity markers now reach `fa103289`, committed as `9230042e` on 1.21.1 and `68e2c77b` on 1.20.1. The full source feature-accounting snapshot remains `48fffdfb`.
2. Feedback/retry fixes are validated, deployed and COMMITTED (119): source 6990a687, 1.21.1 3db353c4, 1.20.1 a7cb1be3. All SSH signatures verified; no pushes. Staging areas empty. Preserve unrelated source README/ideas edits, untracked catch-up docs and target runtime/scratch/harness files.
3. Retry validation passed on all six loaders: source Fabric GameTests 378/378, source NeoForge 372/372, both ports 372/372 per loader. Source Fabric and Forge 1.20.1 tutorial client retry scenes each passed 9/9 checks. All six fresh loader builds pass. Evidence: `build/tutorial-retry/` and `build/curseforge-sync/`. Earlier full feedback/visual gate results remain in checkpoints 110-111.
4. All 8 allow-listed CurseForge dev profiles now run `2.0.3-dev.20261018164715`. Installed copies match build artifacts by SHA-256; metadata/CRC/tutorial class checks pass and no duplicate Fishtastic jars remain. Replaced jars backed up. Next: owner resumes playtesting; Skip for later is retried by the next copper-rod cast, no admin reset needed. Release properties and pinned parity markers remain unchanged; current heads are listed in item 2.
5. The old baseline init script must NOT be added to current target GameTest commands: the branches already add fresh universes, and NeoForge rejects duplicate `--universe` options. Use the branches' built-in configuration.
6. Working rules: no time caps; stop on failure evidence, read logs before relaunching, never relaunch to bisect. The feedback scene harness narrows cleanup to its own loader directory. Signing failures must not be bypassed. Record exact pending operation/evidence before each run, then result and next action; interrupted runs remain unverified.
7. Original owner-only checks from checkpoint 106 remain pending except where this feedback session records fresh evidence: overall real-play feel, F04 tank-frame equivalence, real multiplayer beyond the scene, old-world `WAITING_FOR_CAST` load, and F02 create-world behavior.

This file is the durable cross-session handoff. Keep checkpoint entries append-only; update the current-state sections as work proceeds. Store detailed evidence under `docs/backport-catchup/` and raw build logs under `build/backport-catchup/`. Do not rely on chat or ignored build logs alone for conclusions.

## Scope and pinned revisions

- Source: `D:/GitHub/fishtastic`, branch `26.1.2`, pinned snapshot `48fffdfbda1687cd42d59cb9fea0540e77aec982`.
- 1.21.1 first: `D:/GitHub/fishtastic-worktrees/mc-1.21.1`, branch `port/1.21.1`, initial HEAD `d45498c1a28a99366e3b715e5ac3509d8e99b8ef`.
- 1.20.1 second: `D:/GitHub/fishtastic-worktrees/mc-1.20.1`, branch `port/1.20.1`, initial HEAD `1588b31425fe6a55c8a7a90b317a593cff7db53a`.
- The full source feature-accounting snapshot remains `48fffdfb`; the branch-local parity markers have since advanced to `fa103289` for the scoped post-snapshot fixes recorded in checkpoint 131.
- Preserve existing rendering/API adaptations, dirty legacy worktrees and handoff files.
- Retain all six A/B switches at true; owner visual decisions remain pending.
- Publication and deployment are outside this catch-up.

## Stage state

| Stage | 1.21.1 | 1.20.1 |
|---|---|---|
| 1 Baseline and feature inventory | Recorded: units/builds/GameTests/datagen; runtime log churn noted | Recorded: units/builds/GameTests/datagen (checkpoints 07-08) |
| 2 Shared foundations | Done (F01-F06, checkpoints 10-24); client tank frame comparison inconclusive, pose equivalence by unit test | Done (same) |
| 3 Companion gameplay | Done; companionbond scene 22/22 both loaders; real-input feel awaits owner | Done; companionbond 22/22 both loaders |
| 4 Companion presentation | Slices 1-2 done and scene-verified (companionsim + 8 scenes, 348/348 GameTests both loaders); twoclient, owner looks, F15 TODOs remain | Slices 1-2 done and scene-verified (checkpoints 54-59: commits through `f347a50b`; 348/348 Fabric+Forge, 8 scenes 163 checks PASS both loaders); twoclient, owner looks, F15 TODOs remain |
| 5 Ambient fish | Code done: F13 `dfec46f1`, F14 `1a0608a4`, scenes `81018cbf`, harness `1655f0c4`; open: `ambientfloor.continuity`, NeoForge soak | Code done: `f2872b53`, `57dd1af4`, `9d523839`, `d491cb3a`; scenes parity/water/population 89 PASS / 1 expected FAIL per loader |
| 6 Onboarding | Done (F15, checkpoints 78-79): server/client/scenes committed; Fabric+NeoForge GameTests 372/372, tutorial scenes 21/21 | Done (F15, checkpoints 81-83): `583839e8`, `a4c29a34`, `d82b7522`; Fabric+Forge GameTests 372/372, tutorial scenes 21/21 |
| 7 Final parity | Done (checkpoints 92-94): all gates pass on `e328ad6f` | Done (checkpoints 97-104): all gates pass on `14f02e7a` incl. Forge two-client join |

No gameplay catch-up implementation has completed. Fresh 1.21.1 unit tests, both loader assemblies,
both loader GameTests and Fabric datagen passed. Source snapshot and both target heads remain unchanged.

## Preserved pre-existing local work

Source checkout initially had modified `docs/README.md` and untracked `docs/backport-catchup-plan.md`. These are the supplied plan/index edits; preserve them.

Target initial status (all untracked; no tracked edits reported):

- 1.21.1: `.codex/`, `common/logs/2026-10-06-{5,6,7}.log.gz`, `docs/backport-pass2/drift-report-2026-10-01.md`, `docs/backport-pass2/handoff-parity-2026-10-01.md`.
- 1.20.1: `.codex/`, `.scratch/`, `docs/backport-pass2/handoff-parity-2026-10-01.md`.

Do not clean these files. Compare datagen status against this initial state rather than demanding the removal of unrelated untracked files.

## Session 1 checkpoints

### 01 — Start and source confirmation

- Read repository `AGENTS.md`, the catch-up plan, docs index, readiness audit section 9.5, and canonical modding-guide topics 01, 19 and 20.
- Confirmed source HEAD equals the plan's proposed snapshot; chose `48fffdfb` without advancing either port marker.
- Inventoried existing worktrees using `git worktree list --porcelain`; reuse the established port branches.
- Existing source readiness results belong to the source branch only. They do not prove target baseline or parity. Source audit also records an intermittent backup-fixture failure; keep it visible if it recurs.
- Next: inspect both target worktrees and existing gelatin-ui dependencies, establish the feature ledger, run baseline gates.

### 02 — Target inventory and pending unit/build baseline

- Read the original target port plan, pass-2 process/ledger and both untracked parity handoffs. Handoffs describe older October 1 work; current ledger is through October 6 (`4b0e818e`) and takes precedence.
- Both targets already depend on gelatin-ui `1.0.37+<minecraft_version>`, matching the source library version. No library catch-up is proposed without compiler evidence of a missing API.
- 1.21.1 uses Java 21, Fabric API 0.116.7 and NeoForge 21.1.209; 1.20.1 uses Java 17, Fabric API 0.92.12 and Forge 47.4.23. All mod versions remain 2.0.1.
- Target Gradle properties lack a JVM pin; 1.21.1's existing local hook file names `C:/Program Files/Java/jdk-21`. Pass that explicitly to manual Gradle runs.
- Both target GameTest configurations still reuse `build/gametest`; fresh-universe harness adaptation is pending. Do not delete historical test worlds.
- Historical baseline expectations from the October 6 ledger: 1.21.1 common 725, 1.20.1 common 733, fishsim 283, tank-shape 21,955, loader GameTests 282 per loader. These are historical, not fresh measurements.
- Read canonical GameTest topic 18. Original version differences and pending owner visual checks remain in the port ledger/handoffs.
- **Pending operation BASE-1211-UNIT**: from `D:/GitHub/fishtastic-worktrees/mc-1.21.1`, run `.\gradlew '-Dorg.gradle.java.home=C:/Program Files/Java/jdk-21' :fishsim:cleanTest :common:cleanTest :tools:tank-shape-gen:cleanTest :fishsim:test :common:test :tools:tank-shape-gen:test :fabric:assemble :neoforge:assemble --no-parallel --console=plain`. Raw output: source checkout `build/backport-catchup/session-01/1211-unit-build.log`. Explicit cleanTest tasks force fresh test execution. Requires filesystem approval because the existing target checkout is outside the writable workspace.

### 03 — Fresh unit/build baseline passed; loader gates pending

- BASE-1211-UNIT completed exit 0, `BUILD SUCCESSFUL in 1m 54s`, 18 executed / 22 up-to-date tasks. Each test task executed after cleanTest; compiled classes were allowed to remain cached.
- Read every `TEST-*.xml` result and summed suite attributes: common 725 total = **724 passed, 1 skipped** (26 suites); fishsim 283 total = **282 passed, 1 skipped** (28 suites); tank-shape 21,955 passed, no skips (1 suite). Zero failures/errors. Historical counts were totals, not all passes.
- Fabric and NeoForge assemblies passed. This does not yet establish packaging parity or runtime behavior.
- Created [feature checklist](backport-catchup/features.md) with 16 rows, including a decision to resolve on conditional codec warmup. Created [source commit ledger](backport-catchup/source-commit-ledger.json), recording all **91** commits in `4b0e818e..48fffdfb` plus changed paths. Per-commit dispositions remain unreviewed; no marker advance.
- Created `backport-catchup/baseline-gametest.init.gradle`: adds `--universe` with a newly created UUID directory to loader GameTest tasks. This is an external baseline harness, not a target implementation change. It retains all worlds and avoids the targets' historical shared worlds. Adapted from source `gradle/gametest-world.gradle`.
- **Pending operation BASE-1211-GAME-DATA**: from the 1.21.1 target run `.\gradlew '-Dorg.gradle.java.home=C:/Program Files/Java/jdk-21' -I D:/GitHub/fishtastic/docs/backport-catchup/baseline-gametest.init.gradle :fabric:runGametest :neoforge:runGametest :fabric:runDatagen --no-parallel --console=plain`. Raw output: source `build/backport-catchup/session-01/1211-gametest-datagen.log`. Require actual counts/completion, and compare post-datagen status to preserved initial untracked files. If interrupted, inspect Gradle process/log and generated universe before rerunning.
- Partial checkpoint: Fabric completed **282/282** required GameTests, completion marker `282 GAME TESTS COMPLETE IN 9.476 s`. Its retained universe is `fabric/build/backport-baseline-worlds/e6fb302c-3a17-431e-8a04-2a4945a31051`. NeoForge has begun with universe `neoforge/build/backport-baseline-worlds/e084b050-e037-4eac-a565-4dd7287179fb`; final result/datagen are still pending.
- Verified both gelatin-ui common artifacts `1.0.37+1.21.1` and `1.0.37+1.20.1` exist in local Maven. 1.21.1 unit/assembly already proved its dependency resolves.
- Added a repository AGENTS.md handoff rule so future backport sessions read and maintain this file before continuing.
- Note: `AGENTS.md` is locally ignored (`.gitignore:134`), so that instruction persists on this machine only. The docs index and this durable handoff carry the portable resume instructions.
- Partial checkpoint: NeoForge also completed **282/282** required GameTests, completion marker `282 GAME TESTS COMPLETE IN 11.17 s`. Fabric datagen has now started. BASE-1211-GAME-DATA is still pending until datagen/Gradle exits and target status is checked.

### 04 — 1.21.1 baseline recorded; session handoff

- BASE-1211-GAME-DATA completed exit 0, `BUILD SUCCESSFUL in 1m 27s`; 9 executed / 26 up-to-date tasks. Both GameTest tasks and Fabric datagen/copy tasks executed.
- Fabric **282/282** required GameTests passed; NeoForge **282/282** passed. Actual completion markers and fresh universe paths are in checkpoint 03. Fabric JUnit additionally contains 282 testcases and zero failure/error elements.
- Fabric datagen and `copyGeneratedAssetsToCommon` completed. Post-run status reports **no resource or implementation source changes**.
- Runtime-only delta on 1.21.1: tracked `common/logs/latest.log` modified; compressed untracked logs now include October 6 `-4`, `-5`, `-6` instead of the initial `-5`, `-6`, `-7`. Logging rotated those files during the baseline. No manual cleanup/restoration was performed. Current common runtime logs were copied to source `build/backport-catchup/session-01/1211-common-runtime-logs/` before any further target run. Do not claim the entire working tree is clean or that initial untracked log paths stayed unchanged.
- Other 1.21.1 untracked handoff/tooling files remain. 1.20.1 status is unchanged from checkpoint 02. No target gameplay source edits, commits, pushes, deployment or marker advancement occurred.
- Both invoked Gradle operations have exited. No baseline task is left running.
- Validate tracking files: `git diff --check` passed; JSON parses and contains 91 unique commit IDs. New tracking files are saved on disk and remain uncommitted. `docs/README.md` and the supplied plan now point to this handoff.

**Exact next steps:**

1. Recheck all heads and local status; preserve the current runtime log delta and untracked handoffs.
2. Record a pending operation for the 1.20.1 unit/build baseline. From `D:/GitHub/fishtastic-worktrees/mc-1.20.1`, run `.\gradlew '-Dorg.gradle.java.home=C:/Program Files/Java/jdk-21' :fishsim:cleanTest :common:cleanTest :tools:tank-shape-gen:cleanTest :fishsim:test :common:test :tools:tank-shape-gen:test :fabric:assemble :forge:assemble --no-parallel --console=plain`. Record output in source `build/backport-catchup/session-02/1201-unit-build.log` after creating that directory. JVM 21 runs Gradle; target Java release remains 17.
3. Sum the XML results, then run the same external init script with `:fabric:runGametest :forge:runGametest :fabric:runDatagen --no-parallel --console=plain`. Record results and status differences, including runtime log rotation. Do not invoke the historically hanging Forge datagen task; Fabric owns generated resources on this target.
4. With stage 1 recorded for both targets, begin **1.21.1 stage 2**, starting with the final Minecraft-free fishsim sources/tests from the pinned snapshot. Keep target build configuration/adaptations. Compile and run tests before claiming the shared foundation milestone. Compare deterministic reference outputs against the pinned source, then port F01/F04/F05 and adapt the test harness. Resolve F02's conditional warmup scope from actual target behavior.
5. Update feature/commit ledgers with actual implementation commits and validation as work lands. Keep both full parity markers at `4b0e818e` throughout partial work.

## Session 2 checkpoints

### 05 — Resume and pending 1.20.1 unit/build baseline

- Re-read this handoff, plan, feature ledger, source commit ledger structure and canonical topics 19/20.
- Rechecked source HEAD `48fffdfb`, 1.21.1 HEAD `d45498c1`, 1.20.1 HEAD `1588b314`: unchanged. Target status matches checkpoint 04, including 1.21.1 runtime logs. No new target source edits.
- **Pending BASE-1201-UNIT**: run from `D:/GitHub/fishtastic-worktrees/mc-1.20.1`: `.\gradlew '-Dorg.gradle.java.home=C:/Program Files/Java/jdk-21' :fishsim:cleanTest :common:cleanTest :tools:tank-shape-gen:cleanTest :fishsim:test :common:test :tools:tank-shape-gen:test :fabric:assemble :forge:assemble --no-parallel --console=plain`. Raw log: `build/backport-catchup/session-02/1201-unit-build.log` in source checkout. Requires filesystem escalation for target build outputs.

### 06 — Session 3 resume; BASE-1201-UNIT never started

- Session 3 (2026-10-17). Inspected the pending BASE-1201-UNIT: `build/backport-catchup/session-02/` is empty and no `1201-unit-build.log` exists, no Gradle process running. Conclusion: the operation was **never executed** (session 2 stopped right after recording it). Heads unchanged (source `48fffdfb`, 1.21.1 `d45498c1`, 1.20.1 `1588b314`); target statuses match checkpoint 04/05.
- Working mode: main thread guides and keeps this log; implementation/gate runs are driven by forks. Each fork must record its pending operation here before starting and its result after finishing.
- **Pending BASE-1201-UNIT (re-issued, forked)**: same command as checkpoint 05, log at `build/backport-catchup/session-02/1201-unit-build.log`.

### 06 — (see 05) BASE-1201-UNIT ran under checkpoint 05's pending entry

### 07 — 1.20.1 fresh unit/build baseline passed

- BASE-1201-UNIT completed exit 0, `BUILD SUCCESSFUL in 1m 58s`, 18 executed / 22 up-to-date. Raw log: `build/backport-catchup/session-02/1201-unit-build.log`.
- Summed `TEST-*.xml`: common 733 total = **732 passed, 1 skipped** (27 suites); fishsim 283 total = **282 passed, 1 skipped** (28 suites); tank-shape 21,955 passed (1 suite). Zero failures/errors. Fabric and Forge assemblies passed.
- **Pending BASE-1201-GAME-DATA**: from the 1.20.1 target run `.\gradlew '-Dorg.gradle.java.home=C:/Program Files/Java/jdk-21' -I D:/GitHub/fishtastic/docs/backport-catchup/baseline-gametest.init.gradle :fabric:runGametest :forge:runGametest :fabric:runDatagen --no-parallel --console=plain`. Raw log: `build/backport-catchup/session-02/1201-gametest-datagen.log`. No Forge datagen. If interrupted, inspect the process/log/universe before rerunning.

### 08 — 1.20.1 baseline recorded; stage 1 complete

- BASE-1201-GAME-DATA completed exit 0, `BUILD SUCCESSFUL in 3m 3s`, 11 executed / 24 up-to-date. Raw log: `build/backport-catchup/session-02/1201-gametest-datagen.log`.
- Fabric **282/282** required GameTests passed (`282 GAME TESTS COMPLETE`), universe `fabric/build/backport-baseline-worlds/3b7e5d86-4405-4732-b144-dc96f7853cf8`. Forge **282/282** passed, universe `forge/build/backport-baseline-worlds/e7108833-a0ac-47e2-b615-e236d912ce18`. Fabric datagen and `copyGeneratedAssetsToCommon` ran.
- Post-run status: tracked `common/logs/latest.log` modified (runtime churn, not restored; copy in `build/backport-catchup/session-02/1201-common-runtime-logs/`); untracked `.codex/`, `.scratch/`, `docs/backport-pass2/handoff-parity-2026-10-01.md` unchanged. No resource or source changes from datagen. 1.20.1 HEAD still `1588b314`.
- Stage 1 gates pass on both targets. No commits, marker changes or target source edits. **Next:** stage 2 on 1.21.1.

### 09 — Stage 2a started (1.21.1 fishsim sync)

- **Pending STAGE2A-1211-FISHSIM**: sync `fishsim/` (src/main, src/test, resources) in `D:/GitHub/fishtastic-worktrees/mc-1.21.1` to source `48fffdfb`, keeping target build.gradle; then `:fishsim:cleanTest :fishsim:test` and `:common:compileJava`. Logs in `build/backport-catchup/session-03/`.

### 10 — Stage 2a complete on 1.21.1 (fishsim sync)

- STAGE2A-1211-FISHSIM done. `fishsim/src` (main+test) copied from source `48fffdfb` into `port/1.21.1`, plus `fishsim/build.gradle` (source content, target CRLF; only additions: `runCompanionHeadless`/`runCompanionViewer` tasks and extra probe `-P` property list). No files were removed upstream. Target commit **`c8601812`** (explicit paths only; runtime log churn untouched; no attribution; marker still `4b0e818e`).
- Validation: `:fishsim:cleanTest :fishsim:test` exit 0, 2m1s: **563 tests = 500 passed, 63 skipped (simStdout probes), 0 failed/errors**, 101 suites. Per-suite tests/failures/errors/skipped tables are identical to the source checkout's fishsim results (determinism/equivalence suites included). Logs: `build/backport-catchup/session-03/1211-fishsim-test.log`.
- `:common:compileJava :common:compileTestJava` exit 0 (`1211-common-compile.log`): no call-site adaptations needed. Note: fishsim is still `--release 17`.
- Not yet done for F03: the common-side wiring that uses the new companion/ambient sim APIs (stages 3-5). Source fishsim has stray `hs_err_pid42656.log`/`replay_pid42656.log` (untracked, not copied).
- 1.20.1 dry diff (not applied): vs source, `fishsim/src` has 11 entries only in source and 2 differing files (`core/FlockEngine.java`, `domain/FloorField.java`); expected to apply cleanly as a copy. Its `build.gradle` differs like 1.21.1's did.
- **Next:** apply the same sync to 1.20.1 (needs JDK 17-release check via same test command), then continue stage 2: F01 encyclopedia tiers, F04, F05, F06 on 1.21.1.

### 11 — Pending STAGE2A-1201-FISHSIM

- **Pending STAGE2A-1201-FISHSIM**: copy `fishsim/src` and `fishsim/build.gradle` from source `48fffdfb` into `port/1.20.1`; then `:fishsim:cleanTest :fishsim:test` and `:common:compileJava :common:compileTestJava` (JDK 21 runs Gradle). Logs: `build/backport-catchup/session-03/1201-*.log`. Note: actual dry diff is larger than checkpoint 10 stated (new dirs ambient/companion/harness/domain/etc. plus ~14 differing files incl. Shelter*/Obstacle* tests, SimViewer).

### 12 — Stage 2a complete on 1.20.1 (fishsim sync)

- STAGE2A-1201-FISHSIM done (supersedes checkpoint 11 pending). `fishsim/src` + `fishsim/build.gradle` copied from source `48fffdfb` into `port/1.20.1`; build.gradle differed from the target only by the added companion tasks and probe properties (24 insertions; `--release 17` retained). Target commit **`6f2ab8be`**, explicit paths only, no attribution, marker still `4b0e818e`. Copy touched 14 pre-existing files (Shelter*, Obstacle*, FlockEngine, FloorField, SimViewer, etc.) plus new dirs; no target-only files existed.
- Validation: `:fishsim:cleanTest :fishsim:test` exit 0, 1m59s: **563 tests = 500 passed, 63 skipped, 0 failures/errors**, 101 suites, identical totals to 1.21.1. `:common:compileJava :common:compileTestJava` exit 0. Logs: `build/backport-catchup/session-03/1201-fishsim-test.log`, `1201-common-compile.log`.
- **Next:** stage 2 on 1.21.1: F01 encyclopedia tiers, F04 visuals/pose, F05 pool resolver, F06 harness; then mirror on 1.20.1.

### 13 — Pending STAGE2B-1211 (F01 + F05)

- **Pending STAGE2B-1211**: in `port/1.21.1` port F01 (EncyclopediaTiers) and F05 (FishingPoolResolver + consumers) from source `48fffdfb`; one commit each. Validation: `:common:compileJava`, fabric/neoforge compile, fresh `:common:test`. Logs in `build/backport-catchup/session-03/1211-stage2b-*.log`. If F05 needs F04 pieces, stop at a clean boundary.

### 14 — STAGE2B-1211 complete (F01 + F05 on 1.21.1)

- STAGE2B-1211 done (supersedes checkpoint 13 pending). Target commits: **`20fad8f0`** F01, **`52dbd61b`** F05; explicit paths only, no attribution, marker still `4b0e818e`. Logs: `build/backport-catchup/session-03/1211-stage2b-*.log`.
- F01: applied source `703a09b1` (the first commit after the marker) with rejects hand-fixed: EncyclopediaTiers + test, FishEncyclopediaEntry, Screen/ClientHelper/Fishopedia/ClaimPacket, 64 entry JSONs, gametests. Adaptations: `Identifier`->`ResourceLocation`, `.identifier()`->`.location()`, `lookupOrThrow`->`registryOrThrow`; target Fabric/NeoForge gametest wrappers live in `FishtasticGameTests` (renamed two methods there). Fresh `:common:test`: 730 tests, 0 failures, 1 skipped (+5 EncyclopediaTiersTest, same 5 as source).
- F05: 3-way merged source `4b0e818e..48fffdfb` for FishingMinigameManager/FishtasticFishItem/AcuteIapsisItem, then removed unrelated hunks that belong to later features (companion bond banner sync, `TutorialManager.onPracticeInterrupted`). Added FishingPoolResolver + FishingPoolFixture + FishingPoolResolverTest. Version differences: `Inventory.items` (main inventory) for `getNonEquipmentItems()`; `getDayTime()` for `getOverworldClockTime()`; `registryOrThrow` for `lookupOrThrow`; fixture drops `setId`/`bindComponents`, uses `getHolderOrThrow`, adds BiomeSpecialEffects fog/sky colors, and binds each synthetic Item's intrusive holder reflectively (1.21.1 `ItemStack.getItemHolder()` goes through it); FishProfile ctor has no companion gait/drift fields yet (F09).
- Validation: `:common:cleanTest :common:test` 754 tests (28 suites), 0 failures/errors, 1 skipped; FishingPoolResolverTest 24/24 = source count, includes the pinned fingerprint and seeded pre-refactor draw replay. `:fabric`/`:neoforge` `compileJava` + `compileTestmodJava` pass. Commit hook also ran units green. No loader GameTests run this stage.
- Not done: FishingMinigameManagerGameTests/AmbientContextGameTests (F13/F15), Almanac preview consumers if any.
- **Next:** F04 (shared visual/pose extraction) and F06 (GameTest fresh worlds/bounded fixtures) on 1.21.1; then mirror F01/F05 (patch above applies similarly) on 1.20.1.


### 15 — Pending STAGE2C-1211 (F04, then F06 scoping)

- Fork started on 1.21.1 (HEAD `52dbd61b`): port F04 (FishVisuals, GroupFishPose, tank renderer consumers) from `48fffdfb`; then scope F06. Not committed yet. If interrupted: inspect `git status`/`git diff` in the 1.21.1 worktree before assuming anything.

### 16 — STAGE2C-1211 complete (F04 on 1.21.1); F06 scoped, not started

- Supersedes checkpoint 15 pending. Target commit **`3089ba1f`** (F04), explicit paths, no attribution, marker still `4b0e818e`. Logs: `build/backport-catchup/session-03/1211-stage2c-*.log`.
- Port: net source diff `4b0e818e..48fffdfb` for FishTankBlockEntityRenderer/TankFlockAdapter/TankGroupFlock/TankFishBudget/TankSimDebug applied with `git apply --3way` (two conflicts hand-resolved: target had no per-fish ItemStackRenderState, kept its 1.21.1 class doc; dropped old resolveFishRender). New FishVisuals + GroupFishPose copied with `identifier()`->`location()`, `lookupOrThrow`->`registryOrThrow`. Target's old group-path pose block was byte-identical to the source's pre-refactor block, so GroupFishPose is a pure extraction.
- Deliberately excluded: `TankGroupFlock.fishPose` (dev camera capture, F14); FishAnimator ripple/`onBase`/`applyPlanted` baseX changes (F09 companion).
- Validation: fresh `:common:cleanTest :common:test` 766 tests (30 suites), 0 failures/errors, 1 skipped (754 + FishVisualsTest 6 + GroupFishPoseTest 6; both suites identical to source counts). GroupFishPoseTest compares the pose and normal matrices for all six locomotion classes bit-exactly (tolerance 0) against the frozen pre-extraction sequence and checks no sim advance. Fabric/NeoForge `compileJava`+`compileTestmodJava` pass; commit hook GameTests passed (Fabric; NeoForge skipped by hook).
- **Not proven (needs client):** tank rendered-frame equivalence on 1.21.1 (RenderSelfTest tank scenes before/after). Pose math is proven; item model / renderStatic path is untouched.
- F06 scope (not started): add `gradle/gametest-world.gradle` and apply from fabric/neoforge build.gradle (baseline already uses an external equivalent init script); new `BackupTestFixture` plus edits to FishCatchBackupsGameTests/MarineCompostGameTests/FishingMinigameManagerGameTests (cleanup-goal cycle landing); loader registration changes (`FishtasticFabricGameTests`, `NeoForgeGameTestRegistration`) do NOT map 1:1 because the targets use the shared vanilla-annotation wrapper (`FishtasticGameTests`); source `scripts/git-hooks/pre-commit` tweak. BackupTestFixture depends on `FishCatchBackups.directory/resetSchedule` and `FishCatchSavedData.TYPE`, whose 1.21.1 equivalents must be checked by compiling. Needs full loader GameTest runs (NeoForge hang-prone) to validate, so do it as its own fork.
- **Next:** F06 on 1.21.1 (own fork), then mirror F01/F04/F05 (+F06) on 1.20.1; a client-scene tank comparison before the stage 2 milestone.

### 17 — Pending STAGE2D-1211 (F06)

- **Pending STAGE2D-1211**: in `port/1.21.1` (HEAD `3089ba1f`) port F06 from source `1cc713b5`, `4dc2e956`, `48fffdfb` (gametest-world.gradle, BackupTestFixture, Backup/MarineCompost/Minigame gametest edits, hook tweak). Validate fresh `:common:test`, `:fabric:runGametest`, `:neoforge:runGametest` (--no-parallel). Logs `build/backport-catchup/session-03/1211-stage2d-*.log`. If interrupted, inspect `git status`/`git diff` in the 1.21.1 worktree before assuming anything.

### 18 — STAGE2D-1211 complete (F06 on 1.21.1)

- Supersedes checkpoint 17 pending. Target commit **`115e15e5`** on `port/1.21.1` (explicit paths, no attribution, marker still `4b0e818e`). Logs: `build/backport-catchup/session-03/1211-stage2d-*.log`.
- Ported: `gradle/gametest-world.gradle` (verbatim; applied from fabric and neoforge `build.gradle`), `BackupTestFixture`, fixture-wrapped backup GameTests plus new `backupFixtureRestoresSharedStateAndFiles`, ticker-driven compost yield checks (`finishForYield` + READY assertion, timeouts dropped from the five yield tests), bounded cleanup-goal cycle landing in the trash test, pre-commit hook now also runs `:fishsim:test`. Source `FishtasticFabricGameTests`/`NeoForgeGameTestRegistration` changes were applied through the shared `FishtasticGameTests` wrapper only.
- Version adaptations: `FishCatchSavedData.FILE_ID` made public (1.21.1 has no `TYPE`; fixture swaps via `overworld().getDataStorage().set(FILE_ID, ...)`); compost fixture uses `saveWithoutMetadata/loadWithComponents(CompoundTag, registries)` instead of ValueInput. The `--report` junit arg for NeoForge was not ported (unverified on NeoForge 21.1). `FishCatchBackupsGameTests` never had the `1cc713b5` pre-landing line on this target, so only the fixture-era comment/message changes were applied.
- Validation: fresh `:common:test` 766 tests, 1 skipped, 0 failures; Fabric `runGametest` **284/284** ("284 GAME TESTS COMPLETE", fresh universe `fabric/build/gametest-worlds/e2bdc26c-...`); NeoForge `runGametest` **284/284** (universe `bf0bafc0-...`), no hang. Baseline was 282 each; the +2 are F-stage tests added since (checkpoints 14/16 context) plus the new fixture test — not itemized.
- Note: per source design, successful runs DELETE their fresh universe (failed ones are retained); the historical `build/gametest` world was left untouched. Pre-commit hook ran green (Fabric gametests; NeoForge skipped by hook). The `fishtastic_render_selftest` entries under `*/run/saves` are pre-existing worlds, not armed marker files; none exists at a run root.
- **Next:** mirror F01/F04/F05/F06 onto 1.20.1; then client-scene tank comparison; then stage 3 on 1.21.1.

### 19 — Pending STAGE2E-1201 (F01, F05, F04, F06 on 1.20.1)

- **Pending STAGE2E-1201**: in `port/1.20.1` (HEAD `6f2ab8be`) mirror F01, F05, F04, F06 in that order, one commit each, guided by 1.21.1 commits `20fad8f0`, `52dbd61b`, `3089ba1f`, `115e15e5`. Logs `build/backport-catchup/session-03/1201-stage2e-*.log`. If interrupted, inspect `git log`/`git status`/`git diff` in the 1.20.1 worktree before assuming anything.

### 20 — STAGE2E-1201 partial: F01, F05, F04 committed on 1.20.1

- Target commits on `port/1.20.1` (explicit paths, no attribution, marker still `4b0e818e`): **`0ee43ef1`** F01, **`dadb3e8e`** F05, **`ee63ca77`** F04. F06 still pending (checkpoint 19 pending remains open for it). Logs: `build/backport-catchup/session-03/1201-stage2e-f0{1,4,5}.log`.
- Method: `git apply --3way` of the 1.21.1 commit diffs; conflicts hand-resolved.
- F01 deviations: DFU 1.20.1 `getOrThrow(false, consumer)` form in `FishEncyclopediaEntryGameTests`; `EncyclopediaTiersTest` reads `tags/items/unlisted_fish.json` (1.20.1 plural tag dir). Fresh `:common:test`: 738 tests, 1 skipped, 0 failures (733 baseline + 5 `EncyclopediaTiersTest`).
- F05 deviations: removed `getFishPool` in `FishingMinigameManager` as in source; `FishingPoolFixture` reworked for 1.20.1: no `RegistrationInfo` (`Lifecycle.stable()`), no data-component registration (item data are NBT `ComponentKey`s, `ensureDataComponents` only bootstraps), rod-slot carrier items additionally registered into `BuiltInRegistries.ITEM` because `ItemStack.CODEC` writes the item id through it (otherwise NPE in encode), `items().getHolderOrThrow(..).value()` for `getOrThrow`. `FishingPoolResolverTest` 24/24 = source/1.21.1. `:common:test` 762 tests, 1 skipped, 0 failures.
- F04 deviations: conflict-resolved `FishTankBlockEntityRenderer` (kept target's instanceof chain, `FishVisuals.resolve`) and `TankFlockAdapter` (locomotionOf/speciesId moved to `FishVisuals`); `FishVisuals.locomotionOf` uses an instanceof chain, not a pattern switch (Java 17). `FishVisualsTest` 6 + `GroupFishPoseTest` 6 (bit-exact pose matrices) pass. `:common:test` 774 tests, 1 skipped, 0 failures (1.21.1 is 766; the +8 is the 1.20.1 base delta 733 vs 725). Fabric/Forge `compileJava` + `compileTestmodJava` pass for each feature. Rendered-frame equivalence is NOT proven (needs client scene), same as 1.21.1.
- Excluded identically to 1.21.1: `TankGroupFlock.fishPose` (F14), FishAnimator changes (F09), Almanac/ambient resolver consumers (F08/F13/F15).
- **Next:** F06 on 1.20.1.

### 21 — Pending STAGE2E-1201 F06 validation

- F06 applied (uncommitted) to `port/1.20.1` HEAD `ee63ca77`; compile of common/fabric/forge main+testmod green. **Pending**: `:fabric:runGametest :forge:runGametest --no-parallel` (JDK 21), logs `build/backport-catchup/session-03/1201-stage2e-f06-gametest.log`. If interrupted, inspect `git status` in the 1.20.1 worktree; F06 files are staged/modified but uncommitted.

### 22 — STAGE2E-1201 complete (F01, F05, F04, F06 on 1.20.1)

- Supersedes pending checkpoints 19 and 21. `port/1.20.1` commits: **`0ee43ef1`** F01, **`dadb3e8e`** F05, **`ee63ca77`** F04, **`39a541a3`** F06 (explicit paths, no attribution, marker still `4b0e818e`). Only `common/logs/latest.log` runtime churn remains modified in the worktree.
- F06 on 1.20.1: `gradle/gametest-world.gradle` applied from `fabric/build.gradle` and `forge/build.gradle` (no neoforge module on this branch), `BackupTestFixture`, fixture-wrapped backup tests, ticker-driven compost yield tests, bounded cleanup-goal landing, pre-commit hook adds `:fishsim:test`; `FishCatchSavedData.FILE_ID` made public. Adaptations: `bin.saveWithoutMetadata()` / `bin.load(CompoundTag)` (no registries/`loadWithComponents`); storage swap via `getDataStorage().set(FILE_ID, ..)`. Not ported (same as 1.21.1): loader registration files other than the shared wrapper; no `--report` arg.
- Validation: `:common:test` 774, 1 skipped, 0 failures. Fabric `runGametest` **284/284** ("All 284 required tests passed", universe `fabric/build/gametest-worlds/6d4cc132-...`); Forge `runGametest` **284/284** (universe `forge/build/gametest-worlds/eabf7aa2-...`), no hang. Successful universes are deleted by the script by design; the old shared `build/gametest` world is untouched. Log `build/backport-catchup/session-03/1201-stage2e-f06-gametest.log`.
- Stage 2 status: F01/F03/F04/F05/F06 done on both targets (code + unit/GameTest gates). **Still owed for the stage 2 milestone**: F02 codec-warmup decision, client-scene tank comparison (F04 frame equivalence), source-vs-target deterministic reference comparison for fishsim.
- **Next:** decide F02, then stage 3 (companion gameplay, F07/F08) on 1.21.1.

### 23 — Pending STAGE2F (fishsim determinism comparison + F02 decision)

- **Pending STAGE2F**: (A) compare deterministic fishsim reference output source@48fffdfb vs 1.21.1 vs 1.20.1; (B) decide F02 codec warmup (source `9b203ace`: WARMED_UP on `ItemEffectCondition` and `CosmeticStructure`) from target code inspection. No dev-client repro. Logs: `build/backport-catchup/session-03/stage2f-*`. If interrupted, inspect `git log` in both targets for a F02 commit before assuming nothing landed.

### 24 — STAGE2F complete (supersedes pending checkpoint 23)

- **(A) Determinism:** ran `:fishsim:runHeadless` (`--domain L --fish 12 --seed 42 --ticks 3000`, 5 files incl. PNG/GIF/CSV) and `:fishsim:runCompanionHeadless` (`--script walk --seed 1 --ticks 1200`, 3 files) on source@48fffdfb (JDK 25), 1.21.1 and 1.20.1 (JDK 21 launching, fishsim toolchain). **Every output file is byte-identical across all three trees** (cmp). Plus the suite's own `GoldenTrajectoryTest`/`VoxelGoldenTrajectoryTest` pass in both (500 passed/63 skipped, checkpoints 10/12). Outputs: `build/backport-catchup/session-03/stage2f-{src,p1211,p1201}-{headless,companion}/`. Note `-PsimArgs` is shared by both tasks, so run them separately; source needs JDK 25 as java.home.
- **(B) F02 decision: INCLUDE.** Both targets use Architectury Loom 1.17 and have the identical lazy `CONDITION_TYPE_SUPPLIERS` pattern in `ItemEffectCondition` and the same `CosmeticStructure.validate` -> `SpanStructures` path. Patch from `9b203ace` applied cleanly (31 lines, common-only, dev-only in effect). Commits: 1.21.1 **`72f19d0b`**, 1.20.1 **`7efc3291`** (explicit paths, no attribution). Validation: 1.21.1 `:common:test` 766/1 skipped/0 failed; Fabric 284/284, NeoForge 284/284. 1.20.1 `:common:test` 774/1 skipped; Fabric 284/284, Forge 284/284. Logs `{1211,1201}-stage2f-f02.log`. **Unverified:** the actual in-game create-world deadlock (dev client deliberately not launched; source repro was intermittent).
- **Stage 2 status:** code + unit + GameTest gates done on both targets for F01-F06. **Owed:** F04 client-scene tank frame comparison (needs a client run; GroupFishPoseTest proves pose matrices bit-exact only). Markers still `4b0e818e`.
- **Next:** decide on stage 3 (F07/F08 companion gameplay) on 1.21.1; schedule the client tank comparison with the owner.

### 25 — Pending STAGE3A-1211 (F07 companion storage, bonds, unlocks on 1.21.1)

- **Finding:** the target has no companion gameplay code at all (see `backport-catchup/stage3-scope.md`): stages 3/4 are new-feature ports of ~18.8k lines, not deltas. Scope note and commit slicing written there.
- **Pending STAGE3A-1211**: in `port/1.21.1` (HEAD `72f19d0b`) implement F07 slices 1-3 (PlayerQuestState+Quest, CompanionParty/Storage+loader impls, BondGate/Unlocks+QuestSyncPacket fields). Validate fresh `:common:test`, compile fabric/neoforge main+testmod, `runGametest` both. Logs `build/backport-catchup/session-03/1211-stage3a-*.log`. If interrupted, inspect `git log`/`git status` in the 1.21.1 worktree before assuming anything.

### 26 — STAGE3A-1211 complete (F07 on 1.21.1; supersedes pending checkpoint 25)

- `port/1.21.1` commits (explicit paths, no attribution, marker still `4b0e818e`): **`08fb9667`** slice 1 (PlayerQuestState bond-unlock set/banner queue, `Quest.unlocksCompanion`, tests), **`bf27b3bc`** slice 2 (CompanionParty/CompanionStorage, Fabric + NeoForge `CompanionStorageImpl`, `FishtasticItemData.matches`, loader registration, new `CompanionStorageGameTests`), **`9386c8c3`** slice 3 (CompanionBondGate/Unlocks, `QuestSyncPacket` +`unlockedCompanions`/`newlyUnlockedCompanions`, `StreamCodecs.composite` 10/11-field overloads, tests). Logs `build/backport-catchup/session-03/1211-stage3a-*.log`.
- Validation: fresh `:common:cleanTest :common:test` 773 tests, 1 skipped, 0 failures (766 + `PlayerQuestStateCompanionBondsTest` 5 + `CompanionBondUnlocksTest` 2); Fabric `runGametest` **285/285**, NeoForge `runGametest` **285/285** (+1 = new storage round-trip test, no hang). Compile of common/fabric/neoforge main+testmod green.
- Version adaptations: `Identifier`→`ResourceLocation`, `lookupOrThrow`→`registryOrThrow`; NeoForge 21.1 attachment builder takes `serialize(Codec, shouldSave)` so it uses `CompanionParty.CODEC` (same `{slots:[...]}` JSON as `MAP_CODEC`); 1.21.1 `StreamCodec.composite` stops at 6 so `QuestSyncPacket` (now 11 fields) uses the target's `StreamCodecs` helper, extended to 10 and 11. Patch hunks from the source tests beyond F07 (ambient packet round-trip imports) were NOT taken.
- Deferred: `CompanionTutorialManager.onBondUnlocked` call in `CompanionBondUnlocks.refresh` (TODO(F15)); `CompanionBondGameTests`/`CompanionQuestUnlockGameTests` (need `Companions`; with F08). The dev-time `clearCompanionBonds` exists on PlayerQuestState. The quest sync's new fields are carried but no client UI reads them yet (F08 banner).
- Not proven: nothing in F07 is exercised by a client; the attachment's client sync (Fabric `syncWith`, NeoForge `sync`) is configured but unobserved until a client reads it (F09).
- **Next:** F08 slices per `backport-catchup/stage3-scope.md` (F08-1 `Companions` writes + packets + ItemData name/food/style facade + CompanionBondGameTests first).

### 27 — Pending STAGE3B-1211 (F08 slice 1 on 1.21.1)

- **Pending STAGE3B-1211**: in `port/1.21.1` (HEAD `9386c8c3`) implement F08-1 per `backport-catchup/stage3-scope.md`: `Companions` server writes, Bond/Rename/Feedback/OpenMenu packets, `FishtasticItemData` name/food/tooltip-style facade, `CompanionBondGameTests` (+ `CompanionQuestUnlockGameTests` if its subjects exist). First-bond tutorial call stays `TODO(F15)`. Logs `build/backport-catchup/session-03/1211-stage3b-*.log`. If interrupted, inspect `git log`/`git status` in the 1.21.1 worktree first.

### 28 — STAGE3B-1211 complete (F08 slice 1 on 1.21.1; supersedes pending checkpoint 27)

- `port/1.21.1` commits (explicit paths, no attribution, marker still `4b0e818e`): **`1d3562d3`** `FishtasticItemData` customName/setCustomName/isFood/tooltipStyle facade; **`ca728245`** `Companions` (bond/unequip/rename/equip/setFromMenu, pile take), `BondCompanionPacket`, `RenameCompanionPacket`, `CompanionFeedbackPacket` + registration in `FishtasticPackets` (both loaders go through the shared registrar), `CompanionBondGameTests` (6 tests) and `CompanionQuestUnlockGameTests` (2 tests) wired in the shared `FishtasticGameTests` wrapper. Logs `build/backport-catchup/session-03/1211-stage3b-*.log`.
- Validation: compile of common + fabric/neoforge testmod clean first try; fresh `:common:cleanTest :common:test` 773 tests, 1 skipped, 0 failures (unchanged: no new unit tests in this slice); Fabric `runGametest` **293/293**, NeoForge **293/293** (285 + 8 new, no hang). The commit hook re-ran Fabric tests green and skipped NeoForge.
- Version adaptations: `Identifier`→`ResourceLocation`; `ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC`→`OPTIONAL_STREAM_CODEC` (no untrusted variant on 1.21.1); pile taking rebuilds `BundleContents` from `List<ItemStack>` (no `ItemStackTemplate`); `tooltipStyle` always null (no tooltip-style component before 1.21.2); test players come from `FishtasticTestSupport.playerSupplier`.
- Deferred on purpose: all `CompanionTutorialManager.on*` calls in `Companions` are `TODO(F15)` comments (onLeadSelected/onEquipped/onReturned/onRenamed; also `onCompanionsTabOpened`); `OpenCompanionMenuPacket` and `TakePileFishPacket` (need `CompanionMenu`, F08-2); `UNEQUIP_COMPANION_ID` (declared in source, no packet class exists there either); the three `CarriedFish` picker GameTests (F08-3, with the picker class).
- Not proven: packets exercised only via direct `Companions` calls and codec-level; no client or real network round trip of the bond/rename/feedback packets yet, and no client handler is registered for `CompanionFeedbackPacket` (F08-3).
- **Next:** F08-2 `CompanionMenu` + menu type + `CompanionMenuGameTests`, then `OpenCompanionMenuPacket`/`TakePileFishPacket`; then F08-3 screen/client state, F08-4 command + `CompanionGameTests`.

### 29 — Pending STAGE3C-1211 (F08 slice 2 on 1.21.1)

- **Pending STAGE3C-1211**: in `port/1.21.1` implement F08-2: `CompanionMenu`, menu type registration (both loaders), `CompanionMenuGameTests`, `OpenCompanionMenuPacket`, `TakePileFishPacket`. Logs `build/backport-catchup/session-03/1211-stage3c-*.log`. If interrupted, inspect `git log`/`git status` in the 1.21.1 worktree first.

### 30 — STAGE3C-1211 complete (F08 slice 2 on 1.21.1; supersedes pending checkpoint 29)

- `port/1.21.1` commits (explicit paths, no attribution, marker still `4b0e818e`): **`8182a9df`** `CompanionMenu` + `FishtasticMenuTypes.COMPANION`; **`c349169d`** `OpenCompanionMenuPacket`, `TakePileFishPacket` (+ `TAKE_PILE_FISH_ID`, registered client→server in `FishtasticPackets`), `CompanionMenuGameTests` (18 tests) wired in the shared `FishtasticGameTests` wrapper (names mirror the source Fabric wrapper). Logs `build/backport-catchup/session-03/1211-stage3c-*.log`.
- Validation: compile of common + fabric/neoforge testmod clean (only test-file API fixes needed); fresh `:common:cleanTest :common:test` 773 tests, 1 skipped, 0 failures (unchanged, no unit tests in slice); Fabric `runGametest` **311/311**, NeoForge **311/311** (293 + 18 new, no hang). Commit hook re-ran Fabric tests green.
- Version adaptations: `ContainerInput`→`ClickType`; `Identifier`→`ResourceLocation`; `OPTIONAL_UNTRUSTED_STREAM_CODEC`→`OPTIONAL_STREAM_CODEC`; tests use `Inventory.items` (no `getNonEquipmentItems`) and `List<ItemStack>` bundle contents (no `ItemStackTemplate`). Main-code menu logic (`Slot.safeTake/safeInsert/setByPlayer`, `DataSlot`) compiled unchanged.
- Deferred: `CompanionTutorialManager.onReturned` (3 sites in `CompanionMenu`) and `onCompanionsTabOpened` (`OpenCompanionMenuPacket`) are `TODO(F15)` comments. No `CompanionScreen`/menu-screen registration yet, so the menu type has no client screen (F08-3); a client opening it would not render.
- Not proven: menu exercised only by server-side container clicks in GameTests; the packets have no client sender or network round trip yet. The `SizedItemClickMixin` interaction noted in `clickCompanion` was not separately tested on 1.21.1 (mixin exists on the target).
- **Next:** F08-3 `CompanionScreen`/`CompanionClientState`/`CompanionBondInput`, encyclopedia Companions tab, lang, banner, `CompanionFeedbackPacket` client handler, the three `CarriedFish` picker GameTests; then F08-4 command + `CompanionGameTests`.

### 31 — Pending STAGE3D-1211 (F08 slice 3 on 1.21.1)

- **Pending STAGE3D-1211**: in `port/1.21.1` (HEAD `c349169d`+) implement F08-3: CompanionScreen, client companion state, bond input, encyclopedia Companions tab, banner, lang/assets, `CompanionFeedbackPacket` client handler + client registration on both loaders, three CarriedFish picker GameTests. Logs `build/backport-catchup/session-03/1211-stage3d-*.log`. If interrupted, inspect `git log`/`git status` in the 1.21.1 worktree first.

### 32 — STAGE3D-1211 complete (F08 slice 3 on 1.21.1; supersedes pending checkpoint 31)

- `port/1.21.1` commits (explicit paths, no attribution, marker still `4b0e818e`): **`0260b02c`** CompanionScreen, CompanionClientState, CompanionBondInput, CarriedFish; QuestClientCache unlocked-companion set + bond-unlock listener; banner (`COMPANION_BOND_ID_PREFIX`, NotificationPriority, QuestProgressNotification text); QuestSyncPacket handlers (both loaders + QuestLogScreen) pass the 2 new fields; encyclopedia bond section/heart badges (`SilhouetteItemButton`, `FishEncyclopediaScreen`, `findCompanionUnlockQuest`); `bondCompanion` keybind (B); `AbstractContainerScreenMixin` hold-to-bond + tooltip; companion menu screen registration on Fabric/NeoForge; `CompanionFeedbackPacket` client handler; 46 lang keys; `companions.png`. **`a5d2f9bd`** 3 `CarriedFish` picker GameTests in the shared `FishtasticGameTests` wrapper. Logs `build/backport-catchup/session-03/1211-stage3d-*.log`.
- Validation: `:common:compileJava`, Fabric/NeoForge main + testmod compile clean; fresh `:common:cleanTest :common:test` 773 tests, 1 skipped, 0 failures (unchanged: no new unit tests); Fabric `runGametest` **314/314**, NeoForge **314/314** (311 + 3, no hang). Commit hook re-ran Fabric tests green.
- Version adaptations: AbstractContainerScreen is 3-arg on 1.21.1 (size fields set in ctor); `extractBackground`→`renderBg`; no carried-item hook, so red-flash overlays draw in `renderLabels` (pose un-translated); `extractTooltip`→`renderTooltip` (`GuiGraphics.renderTooltip(font, lines, image, x, y)`, no tooltip style); input via `keyPressed(int,int,int)`/`mouseClicked(double,double,int)`; `blit(ResourceLocation,…)` replaces `RenderPipelines.GUI_TEXTURED`; badge alpha via `GuiGraphics.setColor`; `ClickType`, `ResourceLocation`, `FastColor.ARGB32`; mixin hooks `render` HEAD and `getTooltipFromContainerItem` RETURN (signatures verified with javap against the 1.21.1 merged jar). Pile contents iterate `BundleContents.items()` as stacks.
- Deferred: guided tutorial layout/highlights in the screen (`TODO(F15)`), party preview line (`TODO(F10)`, needs CompanionGroups), banner/tutorial handlers for F15, RenderSelfTest bond scenes.
- **NOT proven:** the screen, mixin and encyclopedia changes have never run on a client (GameTests are server-side; client mixins are not applied there), so a bad mixin injection or layout problem would only show on a client launch. The owner/client check is outstanding for the CompanionScreen, the hold-to-bond input, encyclopedia bond section and banner.
- **Next:** F08-4 `/fishtastic companion` command (dev triggers) + `CompanionGameTests`; then a client launch to verify the F08 UI (owner or a headless self-test), then stage 4 (F09+).

### 33 — Pending STAGE3E-1211 (F08 slice 4 on 1.21.1)

- Fork started: port `/fishtastic companion` command and `CompanionGameTests` on 1.21.1, then F07/F08 accounting. Target HEAD before edits recorded in the completion entry. If interrupted, inspect the target worktree status/log before assuming anything.

### 34 — STAGE3E-1211 complete (F08 slice 4; supersedes pending checkpoint 33)

- Target 1.21.1 `port/1.21.1`: `a1309104` (server-side `/fishtastic companion`: on, off [slot], give, catchcount, unlock, resetbonds; dev-only registration), `818801ce` (`CompanionGameTests`, 12 tests incl. `companionIsKeptOnDeath`; new `FishtasticTestSupport.installRespawnHandoff` installed by `FishtasticFabricTestInit` and `NeoForgeGameTestRegistration`). Explicit paths, no attribution, `common/logs` untouched.
- Validation (fresh): `:common:cleanTest :common:test` 773 total, 1 skipped, 0 failures (no new unit tests); Fabric `326 GAME TESTS COMPLETE`, "All 326 required tests passed"; NeoForge 326/326 same; no NeoForge hang. 314 + 12 new = 326. Logs: `build/backport-catchup/session-03/1211-stage3e-*.log`.
- Deviations: command ported as a reduced server-side subset (see stage3-scope.md STAGE3E note); `IdentifierArgument` -> `ResourceLocationArgument`; `unlock` omits the tutorial call (`TODO(F15)`); `ItemStackTemplate` -> `ItemStack::copy` and `getNonEquipmentItems()` -> `items` in the pile test helper; respawn hand-off made a loader-installed seam instead of per-loader test registrations.
- F07/F08 accounting: no F07/F08 source class or test missing on 1.21.1. Remaining deferrals: `TODO(F15)` tutorial hooks (Companions x4, CompanionMenu x3, OpenCompanionMenuPacket, CompanionBondUnlocks, unlock command), `TODO(F10)` party preview line, guided-tutorial screen layout (F15), client dev subcommands (F09/F10), predatorscene/ghost (F10/F11), RenderSelfTest bond scenes.
- **Client-unverified (needs an owner/client run):** companion screen, hold-to-bond, B keybind, `AbstractContainerScreenMixin` injections, encyclopedia bond section/heart badges, unlock banner, `CompanionFeedbackPacket` handler, attachment sync to clients, packet round trips.
- Next action: (1) owner/client look at the stage-3 UI on 1.21.1 (or accept unverified), (2) recommendation: mirror stage 3 (F07/F08, 7 commits' worth) onto 1.20.1 now while the 1.21.1 diffs are fresh and the 1.21.1 -> 1.20.1 deltas are small (older API again), then start stage 4 (F09-F12, ~3k+ lines client companions) on 1.21.1 first. Parity markers remain `4b0e818e`.

### 35 — Pending STAGE3F-1201 (F07 + F08 slices 1,2,4 server side on 1.20.1)

- Session 3. Mirroring 1.21.1 commits 08fb9667, bf27b3bc, 9386c8c3 (F07) and 1d3562d3, ca728245, 8182a9df, c349169d, a1309104, 818801ce (F08 server) onto `port/1.20.1` (HEAD at start recorded below by the fork). Slice 3 client UI is NOT in this group. Storage backing on Fabric 0.92 / Forge 47 (no attachment API) to be chosen after inspecting the target and documented in the completion checkpoint. Logs: `build/backport-catchup/session-03/1201-stage3f-*.log`.

### 36 — STAGE3F-1201 progress: F07 committed on 1.20.1

- Commits on `port/1.20.1` (explicit paths, no attribution): `28c9ae2a` (F07-1 quest bond unlocks), `6b25a7f1` (F07-2 party + storage), `46b5dd3b` (F07-3 bond gate/unlocks + QuestSyncPacket fields). Pre-commit hook ran unit tests + Fabric GameTests green each time.
- **Storage decision (deviation):** neither Fabric API 0.92 nor Forge 47 has a player attachment API, and the repo has no per-player persistence pattern outside SavedData. `CompanionStorage` is common-only (no `@ExpectPlatform`/loader impls), backed by new `CompanionPartySavedData` (overworld file `fishtastic_companions`, `{parties: {uuid: {slots: [...]}}}`), separate from `FishCatchSavedData` (backup-duplication rule). Survives death/respawn because it never lives on the player. Client reads a static map that is always empty until `TODO(F09)` adds party sync (no tracking/owner sync exists yet = client-unverified).
- Other adaptations: `BufCodec.composite` gained 10/11-field overloads (QuestSyncPacket has 11); `PlayerQuestStateCompanionBondsTest` uses `JsonOps` (no `JavaOps` in this DFU) and `getOrThrow(false, …)`; empty-slot stack codec via `CompoundTag.CODEC` xmap (no optional ItemStack codec).

### 37 — STAGE3F-1201 F08 slice 1 ported but commit BLOCKED by signing

- F08-1 (item-data facade, `Companions`, bond/rename/feedback packets, `CompanionBondGameTests` + `CompanionQuestUnlockGameTests`) is **staged, not committed** in `mc-1.20.1`: `git commit` failed twice with `1Password: failed to fill whole buffer` / `agent returned an error` (commit signing agent, `fatal: failed to write commit object`). Not bypassed (no --no-gpg-sign). The user must unlock/restart 1Password's SSH agent; then commit as two commits (`FishtasticItemData.java` alone = F08 slice 1a; the rest = 1b). HEAD stays `46b5dd3b`.
- 1.20.1 adaptations in 1b: packets rewritten on `FishtasticPayload`/`BufCodec` (+ `.class` arg in registrar calls, `NetworkApiSided.sendToPlayer`); `BundleContents` is `grill24.fishtastic.component`; name/food facade via `hasCustomHoverName/getHoverName/setHoverName/isEdible`; test fixtures use `Lifecycle.stable()` not `RegistrationInfo`, `hasCustomHoverName()`.

### 38 — 1.20.1 F08 slice 1 committed; slices 2/4 pending

- Signing agent unblocked. 1.20.1 commits: `0cf7e73a` (F08 slice 1a, item-data facade; hook unit + Fabric GameTests passed) and `c6e0d9f1` (slice 1b: Companions writes, bond/rename/feedback packets, CompanionBondGameTests + CompanionQuestUnlockGameTests). Slice 1b's own GameTests are validated with the slice 2/4 run below.
- **Pending STAGE3G-1201**: mirror F08 slice 2 (1.21.1 `8182a9df`, `c349169d`) and slice 4 server pieces (`a1309104`, `818801ce`) onto 1.20.1, then fresh `:common:test`, `:fabric:runGametest`, `:forge:runGametest` (--no-parallel). Logs `build/backport-catchup/session-03/1201-stage3g-*.log`. Interrupted => inspect, don't assume.

### 39 — 1.20.1 stage 3 server side complete (STAGE3G-1201 done; supersedes pending in 38)

- 1.20.1 commits (explicit paths, signed; hook ran unit + Fabric GameTests each time): `0cf7e73a` (1a facade), `c6e0d9f1` (1b writes/packets/tests), `3b01df4b` (2a CompanionMenu + menu type), `ee077cb6` (2b open-menu/take-pile packets + CompanionMenuGameTests), `13f19b8f` (4a dev command), `a4bbdea0` (4b CompanionGameTests + respawn hand-off). Together with F07 `28c9ae2a`, `6b25a7f1`, `46b5dd3b`.
- Validation (fresh): `:common:test` 781 tests, 0 failures/errors, 1 skipped; Fabric **323/323**, Forge **323/323** GameTests (matches the expected 284+1+8+18+12; the 3 picker tests come with slice 3). Logs `build/backport-catchup/session-03/1201-stage3g-*.log`.
- Deviations: packets use the 1.20.1 `FishtasticPayload`/`BufCodec` idiom (already used by Bond/Rename); tests use `grill24.fishtastic.component.BundleContents`, `FishtasticItemData.customName/isSameItemSameData` instead of data components. `companionIsKeptOnDeath` gives the mock "after" player the old UUID and does not clear the shared record, because the 1.20.1 party is a UUID-keyed save file (real respawn keeps the UUID); the respawn hand-off helper is `after.restoreFrom(before, false)` on Forge, plus Fabric's AFTER_RESPAWN. Command is the same reduced port as 1.21.1 (no client-driven triggers; F15 tutorial TODO).
- Client-unverified (same list as 1.21.1, plus): client reading the party (empty until party sync, F09), real packet round trips. No GUI exists yet on 1.20.1.
- Next: F08 slice 3 client UI on 1.20.1 (mirror 1.21.1 `0260b02c`, `a5d2f9bd`; keep the 1.20.1-only GUI blend fix), then a client look at the stage 3 UI on both targets; then stage 4. Markers unchanged at `4b0e818e`.

### 40 — Pending STAGE3H-1201 (F08 slice 3 client UI on 1.20.1)

- Mirroring 1.21.1 `0260b02c` + `a5d2f9bd` onto `port/1.20.1` (HEAD `a4bbdea0` at start). Keep the 1.20.1-only GUI blend fix. Logs `build/backport-catchup/session-03/1201-stage3h-*.log`. Interrupted => inspect target status/log, don't assume.

### 41 — STAGE3H-1201 complete (F08 slice 3 on 1.20.1; supersedes pending checkpoint 40)

- `port/1.20.1` commits (signed, hook green, explicit paths, no attribution; marker still `4b0e818e`): **`8f82b701`** (screen, client state, bond input, CarriedFish, banner, QuestClientCache fields, encyclopedia bond heart/section, B keybind, `AbstractContainerScreenMixin`, Fabric + Forge client registration, feedback handler, lang, `companions.png`), **`93565bd3`** (3 CarriedFish picker GameTests).
- Method: `0260b02c` diff applied to 1.20.1 (neoforge hunks dropped; Forge client hunks applied by hand to `FishtasticForgeClient`), then compile-driven fixes.
- Validation (fresh): `:common:compileJava`, Fabric/Forge main + testmod clean; `:common:cleanTest :common:test` **781 tests, 1 skipped, 0 failures/errors** (no new unit tests); Fabric `runGametest` **326/326**, Forge **326/326** (323 + 3), no Forge death. Logs `build/backport-catchup/session-03/1201-stage3h-*.log`.
- 1.20.1 deviations: client sends use `NetworkApiSided.sendToServer(pkt, Pkt.STREAM_CODEC)` (no `ServerboundCustomPayloadPacket`); `BundleContents` is `grill24.fishtastic.component`; stack equality via `FishtasticItemData.isSameItemSameData` (no `isSameItemSameComponents`); picker test names fish through `FishtasticItemData.setCustomName`. The screen code otherwise compiled unchanged (3-arg-free `AbstractContainerScreen`, `GuiGraphics`, `renderBg`/`renderLabels` exist on 1.20.1). Mixin targets verified with javap against the 1.20.1 mapped jar: `render(GuiGraphics,int,int,float)` public, `getTooltipFromContainerItem(ItemStack)` protected, shadowed `hoveredSlot/leftPos/topPos` protected. GUI blend fix `ff451aba` concerns custom ShaderInstance json only; this slice adds no shader programs, so nothing to add — an in-game look should confirm badge/banner alpha.
- **Client-unverified (both targets):** CompanionScreen, hold-to-bond, B key, mixin injections, encyclopedia bond heart/section, unlock banner, feedback handler, party sync (empty until F09), packet round trips. No automated client scene was run.
- Overall stage 3: F07 and F08 (slices 1-4) done on both targets, server-side tests green (1.21.1 326/326 both loaders; 1.20.1 326/326 both loaders); remaining deferrals `TODO(F15)` tutorial hooks, `TODO(F10)` party preview line, client dev subcommands (F09/F10), predator/ghost (F10/F11), RenderSelfTest bond scenes. UI awaits a client look.
- Next: owner/client look at stage 3 UI (or accept unverified), then stage 4 on 1.21.1 (F09-F12).

### 42 — Pending SELFTEST-1211 (client-only verification of stages 1-3 on 1.21.1)

- Session 3, fork. **Pending SELFTEST-1211**: port the source `companionbond` RenderSelfTest scene (and any harness bits) to the 1.21.1 target and run it via the `<run dir>/fishtastic_render_selftest` marker on Fabric, then NeoForge; plus the existing `tank` scene at d45498c1 vs HEAD for F04. Evidence goes to `docs/backport-catchup/selftest/`. Markers/self-test worlds must be cleared afterwards. Interrupted run => inspect `fabric/run` and `neoforge/run` markers and any java processes before assuming anything.

### 43 — SELFTEST-1211 complete (supersedes pending checkpoint 42)

- Added the opt-in `companionbond` scene to the 1.21.1 `RenderSelfTest` (commit `1c32f60e`, only that file; hook's Fabric GameTests passed). Marker file `<run>/fishtastic_render_selftest` containing `companionbond` (or `tank`). Ran `:fabric:runClient` then `:neoforge:runClient` (JDK 21, runClient built itself, no hangs, no relaunch needed after fixing the scene itself). Logs: `build/backport-catchup/session-03/1211-selftest-{fabric-final,neoforge,tank-d45498c1,tank-head}.log`; earlier scene-debugging runs `-run1`, `-run2`.
- **Result: 22/22 `bond.*` checks PASS on both Fabric and NeoForge**, no mixin/injection errors in either `latest.log`:
  - `feedbackHandler` (BondCompanionPacket for a locked fish -> server CompanionFeedbackPacket -> `CompanionClientState` LOCKED), `unlockSynced` (unlock command -> QuestSyncPacket -> `QuestClientCache`), unlock banner screenshot ("Bluegill: Bond Unlocked!").
  - `mixinRenderHead` (CompanionBondInput tracks the survival InventoryScreen => render-head injection applies) and `mixinTooltipHint` (unlocked fish tooltip gets the extra "Hold [B] to bond" line, locked fish does not => tooltip-return injection applies).
  - Companion screen via real OpenCompanionMenuPacket (39 slots), shift-click equip (1 taken from stack), second equip, rename packet, pencil editor open/close-on-move, pile strip + pile-to-cursor (pile collapses to one bluegill), locked-fish refusal flash (own slot + party slot, red, "Catch 6 more Oscar to bond. See encyclopedia."), return to inventory, encyclopedia opens with bond heart on bluegill; selecting bluegill shows "Ready to bond" + "Choose fish" in the Companions section.
- Harness findings (not product bugs): in creative mode `InventoryScreen` redirects to the creative screen and the hook is skipped, so that check runs in survival; the right-hand panel in screenshots is JEI (dev env); the encyclopedia selection was driven by reflection (`onFishSelected`), which leaves the silhouette disc drawn over the info page - not a real-click path; hotbar HUD draws under the encyclopedia's bottom button (Choose fish is partly overlapped when scrolled to the end).
- **Not covered / still needs the owner's eyes:** the physical hold-B timing and progress glyphs (`CompanionBondInput.down()` polls GLFW key state, which the harness cannot press; the tooltip hint and tick hook were verified instead), real mouse-click path and hover feedback, screen aesthetics (the large empty band between the party slots and "Inventory" is design-as-ported, unjudged), companion-screen feedback sound, and the encyclopedia scroll/transition feel.
- **F04 tank comparison** (Fabric only): checked out `d45498c1` into a temporary worktree (removed afterwards), same options.txt, `tank` scene; compared with HEAD. Tank frames, glass, water, sand, chest/campfire/pile props identical; fish drawn in the same style; fish positions differ because the sim is live, so pixel diffs (up to 95% of pixels inside a tank) are expected and prove nothing. Side-by-sides: `docs/backport-catchup/selftest/tank/`. Equivalence of poses stays established by `GroupFishPoseTest` (bit-exact). NeoForge tank scene not run. A fixed-seed frozen-fish render would be needed for a true pixel comparison.
- Evidence screenshots (800x450, 128-colour): `docs/backport-catchup/selftest/{fabric,neoforge,tank}/`. All self-test markers cleared (`fabric/run`, `neoforge/run`); no java processes remain; `saves/fishtastic_render_selftest` worlds are the harness's own and are recreated each run. Target working tree still has the runtime-log churn (`common/logs/latest.log`, new `2026-10-17-*.log.gz` untracked) - left alone.
- **1.20.1 equivalent would need:** (a) port the scene into the 1.20.1 `RenderSelfTest` (2247 lines; add `"companionbond"` to `OPT_IN_SCENES` and the `queueScene` switch, plus `paradeSetup`, `containerClick`, `peekStatic`; the 1.20.1 file lacks them); (b) replace `ServerboundCustomPayloadPacket(...)` sends with `NetworkApiSided.sendToServer(pkt, Pkt.STREAM_CODEC)`; (c) keep `ClickType`/`handleInventoryMouseClick` (same), `BundleContents` from `grill24.fishtastic.component`, `FishtasticItemData.isSameItemSameData`, no `Identifier`; (d) check `FishtasticRegistries`/`ResourceKey` and the Gelatin `IUIElement` package names for the reflection hop into the encyclopedia; (e) run `:fabric:runClient` and `:forge:runClient` (JDK 21, Forge dev JVM can die silently - read Gradle's raw stderr), survival for the inventory-hook check, party sync is empty until F09 so skip any party-sync assertion, and verify the badge/banner alpha with the 1.20.1 blend fix in the screenshots; (f) clear `fabric/run` and `forge/run` markers afterwards.
- Next action: stage 4 (F09/F10) on 1.21.1 per checkpoint 41's recommendation, or run the 1.20.1 `companionbond` scene first.

### 44 — Pending SELFTEST-1201 (client-only verification of stages 1-3 on 1.20.1)

- Session 3. Port the `companionbond` scene (1.21.1 `1c32f60e`) into the 1.20.1 `RenderSelfTest`; run Fabric then Forge `runClient` (JDK 21, runClient builds itself); tank comparison vs pre-F04 (`1588b314`-era, commit before `ee63ca77`) with a frozen-fish option if easy; badge/banner alpha check. If this entry has no completion checkpoint, inspect `fabric/run` and `forge/run` for a leftover `fishtastic_render_selftest` marker, any java/gradle process, and a temp worktree before assuming anything.

### 45 — SELFTEST-1201 complete (supersedes pending checkpoint 44)

- Ported the `companionbond` scene to the 1.20.1 `RenderSelfTest` (commit `211d2d66`, only that file; hook unit tests passed). Adaptations vs 1.21.1 `1c32f60e`: sends via `NetworkApiSided.sendToServer(pkt, Pkt.STREAM_CODEC)`, `BundleContents` from `grill24.fishtastic.component`, `mouseScrolled(x, y, delta)`, and a `partyServer(mc, slot)` helper because the 1.20.1 party lives in server save data (a client-side `CompanionStorage` read is empty until party sync, F09). The first Fabric run failed `bond.equipped/second/renamed` for exactly that reason (the screenshot showed the fish equipped); `lockedNotBonded`, `editorClosesOnMove` and `returned` would have passed vacuously on the client read, so every party read now goes to the integrated server.
- **Result: 22/22 `bond.*` checks PASS on Fabric and on Forge**, no mixin/injection/class errors in either log (render-head injection tracks the survival `InventoryScreen`; the tooltip-return injection adds "Hold [B] to bond" for the unlocked fish only). Logs: `build/backport-catchup/session-03/1201-selftest-{fabric,fabric-r2,forge,tank-head,tank-d-prefo4,compile}.log` (`-fabric` is the failed first run, `-fabric-r2` the final one). Forge did not die; no relaunch was needed on either loader.
- Screenshots (800x450) looked at: unlock banner ("Bluegill: Bond Unlocked!") is legible on both loaders (small but readable, no alpha problem seen); companion screen, equipped party slot, red refusal flash with "Catch 6 more Oscar to bond. See encyclopedia." and the encyclopedia all render sensibly. Same harness artefacts as 1.21.1 (JEI panel; silhouette disc over the info page from the reflection-driven selection). Files: `docs/backport-catchup/selftest/{fabric1201,forge1201}/`.
- **F04 tank comparison (Fabric only):** `tank` scene at pre-F04 `dadb3e8e` (temporary worktree, removed) vs HEAD. Same tank, glass, sand, props and fish style; fish positions and chest-lid state differ. Sampled pixel diffs 9-60% are expected and prove nothing. Side-by-sides: `docs/backport-catchup/selftest/tank1201/`. **No frozen-fish option was added** (to either target): the sim is tick-driven from world-load time and bobs on game time, and screenshots land on arbitrary partial ticks, so a true freeze would mean hooking the client tick counter and partial tick, not a scene option. Pose equivalence still rests on `GroupFishPoseTest`. A real pixel comparison would need that hook; Forge tank scene was not run.
- Cleanup: markers removed from `fabric/run` and `forge/run`; temp worktree `tmp-1201-prefo4` removed (needed `\?\` rmdir, git failed on a long path) and pruned; remaining java processes are Gradle daemons/compiler workers, no game clients. Working tree: only the runtime-log churn (`common/logs/latest.log`, new `2026-10-17-*.log.gz`) and the preserved untracked files.
- **Still needs the owner's eyes (both targets):** physical hold-B timing/progress glyphs (the harness cannot press keys), real mouse hover/click feel, screen aesthetics (empty band in the companion screen), the companion-screen feedback sound, encyclopedia scroll/transition feel, and badge/banner alpha under real play on 1.20.1.
- Next action: stage 4 (F09/F10) on 1.21.1 per checkpoint 41's recommendation.

### 46 — Pending STAGE4A-1211 (stage 4 scoping + F09 slice 1 on 1.21.1)

- Session 3, fork. Step 1: write `docs/backport-catchup/stage4-scope.md` (file-by-file F09-F12 needs, dependencies, slice plan, risks). Step 2: implement F09 slice 1 (FishProfile gait/drift fields, FishAnimator ripple/onBase, party sync server->client, client companion state hooks, swimmer path) on `port/1.21.1`, validating with `:common:test`, loader GameTests, datagen and a client self-render scene. Logs `build/backport-catchup/session-03/1211-stage4a-*.log`. Interrupted => inspect target `git status/log`, `fabric/run` + `neoforge/run` markers and java processes; don't assume.

### 47 — STAGE4A-1211 interim (F09 foundation committed; client layer uncommitted)

- Committed `ba9fbf36` on `port/1.21.1`: FishProfile companion_gait/companion_drift/companion_group + FishAnimationConfig ripple/diagonalTexture/baseX + FishAnimator ripple/onBase + 9 fish_profile JSONs + FishAnimatorFloorHeadingTest + CompanionGroupConfigTest. Fresh `:common:test` 780 tests (773 + 7), 1 skipped, 0 failures.
- Uncommitted in the target working tree (compiles on common/Fabric/NeoForge main+testmod): client companion layer ported from 48fffdfb — `client/companion/*` (ClientCompanion, ClientCompanions, Dev, WorldAdapter, WorldFeatureIndex, Ambient, Music, Particles, PerchLayer, ...), `client/renderer/Companion{Render,FishPose,FeatureLean}`, `companion/CompanionGroups`, `CompanionSceneType`, `CompanionScenePacket` (+ registration), 5 mixins (AvatarRenderer->PlayerRenderer, ClientLevel, LevelEvent->LevelRenderer, NoteBlock, LevelRenderer.blockChanged), PREDATOR_FISH tag + provider + generated JSON (identical to source), FishingMinigameAnimation.devCatchTarget, loader wiring (tick, world pass in AFTER_ENTITIES, dev HUD, scene handler), `companionsim` self-test scene.
- 26.1 -> 1.21.1 render adaptations: `SubmitNodeCollector`/`ItemStackRenderState` replaced by `MultiBufferSource` + `ItemRenderer.renderStatic`; name tags drawn with `Font.drawInBatch` SEE_THROUGH billboard; perch layer is `RenderLayer<AbstractClientPlayer, PlayerModel<...>>.render(...)`; `AvatarRenderer` mixin -> `PlayerRenderer`; `LevelEventHandler` mixin -> `LevelRenderer.levelEvent` (field `level` shadowed non-final); precipitation via `Level.isRaining` + heightmap + `Biome.getPrecipitationAt`.
- Next: run `companionsim` self-test (Fabric, then NeoForge), commit client layer, validate tests/GameTests/datagen.
- **Pending SELFTEST-4A-1211**: marker `fabric/run/fishtastic_render_selftest` = `companionsim`, `:fabric:runClient`; then NeoForge. Clear markers afterwards; inspect `fabric/run` + `neoforge/run` and java processes if interrupted.

### 48 — STAGE4A-1211 complete (supersedes pending 46 and interim 47)

- `port/1.21.1` commits (signed, explicit paths, no attribution; marker still `4b0e818e`): **`ba9fbf36`** profile gait/drift/group fields + FishAnimator ripple/onBase + 9 profile JSONs + 2 tests; **`3b70479c`** `PREDATOR_FISH` tag/provider/JSON, `FishtasticClientConfig` companion settings, `FishtasticKeyBinds`, minigame `devCatchTarget`; **`a0e803fe`** the companion client layer (30 files: `client/companion/*`, `Companion{Render,FishPose,FeatureLean}`, `CompanionGroups`, `CompanionSceneType`, `CompanionScenePacket`+registration, 5 mixins, loader wiring); **`e79dbfec`** `companionsim` self-test scene. (A first attempt accidentally bundled everything into one commit; it was reset and recommitted in these slices before anything else touched the branch.)
- Scoping: see [stage4-scope.md](stage4-scope.md). Finding: the client layer is monolithic, so F09/F10/F12 and the client half of F11 landed together; remaining is server scenes, dev client commands, self-test scenes, owner looks.
- Validation (fresh): `:common:compileJava/TestJava`, Fabric + NeoForge main + testmod compile clean; `:common:test` **783 tests, 1 skipped, 0 failures**; `:fabric:runGametest` **326/326**, `:neoforge:runGametest` **326/326** (no new GameTests); `:fabric:runDatagen` changed only the new `tags/item/predator_fish.json`, byte-identical to the source's. Logs `build/backport-catchup/session-03/1211-stage4a-*.log`.
- Client scene `companionsim` (marker + `:fabric:runClient`, then `:neoforge:runClient`): **18/18 checks PASS on both** (tracked, one body, drawn for bluegill small/solo, starfish, garden eel, glass squid, giant manta ray), no mixin/class errors in either `latest.log`. Screenshots viewed (800x450) in `docs/backport-catchup/selftest/companionsim/{fabric,neoforge}/`: swimmer drawn in the world pass (bluegill, manta ray — the manta's texture is genuinely black/grey, checked against the png, not a lighting bug: forcing FULL_BRIGHT and disabling the quality outline changed nothing), glass squid as a faint blue dot, garden eel standing on the head via the player layer (both loaders). Starfish (benthic, on the ground 3 blocks behind the owner) passed the drawn check but was not seen in any frame; shoulder perch not exercised.
- Harness notes: perchers are drawn only in third person; the draw check therefore runs after the camera switch. Two temporary debug edits (FULL_BRIGHT, outline early-return) were reverted before committing. Markers `fabric/run/fishtastic_render_selftest` and `neoforge/run/fishtastic_render_selftest` removed; no game clients left (Gradle daemons only).
- Needs the owner's eyes: how the companion reads at real play distance, benthic walking/perching on shoulders, drifter bell/jet motion, name tags while sneaking, first-person hand visits, glass squid translucency ordering.
- Next: stage-4 slice 2 (F11 server half + GameTests) per stage4-scope.md, or mirror slice 1 to 1.20.1 first. Markers unchanged.

### 49 — Pending STAGE4B-1211 (stage 4 slice 2: F11 server half + F10 server/tests on 1.21.1)

- Session 3, fork. Port `server/CompanionSceneManager`, `CompanionGhost`, `GhostConnection` (+ Fabric/NeoForge impls), tick hook, scene sending, `OwnerFallTracker` if scoped, `CompanionSceneGameTests`/`CompanionGroupsGameTests`/`CompanionProfileGameTests`, and the deferred companion-command subcommands where the client bridge exists, from 48fffdfb to `port/1.21.1`. Validate with `:common:test`, both loaders' GameTests and self-test scenes. Logs `build/backport-catchup/session-03/1211-stage4b-*.log`. Interrupted => inspect target `git status/log`, `fabric/run` + `neoforge/run` markers and java processes; don't assume.

### 50 — STAGE4B-1211 interim: server half committed; self-test scenes pending

- Commits on `port/1.21.1` (signed): **`1fdf326d`** `CompanionSceneManager`, `CompanionGhost`, `GhostConnection` (+ Fabric/NeoForge impls), server tick hook, `CompanionSceneGameTests` (15), `CompanionGroupsGameTests` (5), `CompanionProfileGameTests` (2) wired into `FishtasticGameTests`; then the full `CompanionCommand` (see next line for SHA).
- Validation: `:common:test` 783 tests, 1 skipped, 0 failures (unchanged); `:fabric:runGametest` **348/348**, `:neoforge:runGametest` **348/348** (326 + 22). Logs `build/backport-catchup/session-03/1211-stage4b-*.log`.
- Deviation: on 1.21.1 `placeNewPlayer` scatters mock players over the spawn radius (26.1 stacks them), so `CompanionSceneGameTests.place` anchors offsets at the shared spawn instead of "where it stood", and `freeSwimmersTooCloseForG2` now uses an absolute 3-block offset. First run failed 7/22 for exactly this reason (players ~17 blocks apart).
- Other adaptations: `Identifier`→`ResourceLocation`, `snapTo`→`moveTo`, `gameMode()` override → `isCreative()/isSpectator()` on the ghost, `player.level()`→`serverLevel()`, `registryOrThrow`/`Registry.get`. `/fishtastic companion` is now the full source command; `unlock` keeps `TODO(F15)`.

### 51 — STAGE4B-1211 resume inspection (after interruption)

- Found: HEAD `84539ad8` (commits `1fdf326d`, `84539ad8` as in checkpoint 50). Only uncommitted source edit: `RenderSelfTest.java` (9 ported scenes: companionscenes/party/eel/lod/moods/world/particles/worldcost/twoclient + helpers `memberOf/moodOf/climateOf/poseOf/moods/anyMood/note/tickProbe`, added to OPT_IN_SCENES). A leftover `fabric/run/fishtastic_render_selftest` marker (companionworld, from the debug run) was cleared; no `neoforge/run` marker. Debug `NOTE DBG` edit was already reverted (restored from backup). That edit compiled (the debug run ran); the full compile of the scene port was re-verified by that run.
- Fabric scene run (marker with 8 scenes, log `1211-stage4b-selftest-fabric.log`): companionscenes 13/13, party 23/23, eel 7/7, lod 5/5, moods 11/11, particles 12/12, worldcost 63/63 PASS; **companionworld 24 PASS / 4 FAIL**: `light.settled_darkness`, `light.reaction`, `percher.left.watch`, `percher.right.watch` (all night-light features).
- Root cause (proved live with a debug NOTE): at `dayTime=18000` the client reports `Level.getSkyDarken()==0` and `getMaxLocalRawBrightness==15`, so `Ambient.darkness==0`. In 1.21.1 the int `skyDarken` is only maintained on the server; the client uses the float `ClientLevel.getSkyDarken(partial)` (1.0 day .. 0.2 night). Same cause affects `ClientCompanion.glowsNow()` (drifter night glow). Fix in progress: a shared client helper deriving the int from the float.

### 52 — STAGE4B-1211 complete (supersedes pending 49, interim 50 and inspection 51)

- Commits on `port/1.21.1` (signed, explicit paths, no attribution; marker still `4b0e818e`): **`1fdf326d`** scene manager/ghost/GhostConnection + tick hook + 22 GameTests; **`84539ad8`** full `/fishtastic companion` command; **`650b06bf`** client sky darkening fix; **`88021d42`** nine ported self-test scenes (companionscenes/party/eel/lod/moods/world/particles/worldcost/twoclient, opt-in).
- Fix found by the scene run (proved live with a debug NOTE, then reverted): on 1.21.1 `Level.getSkyDarken()` is server-only (0 on the client at midnight), so `Ambient.darkness` stayed 0 and night-light noticing / drifter glow never fired. `CompanionAmbient.skyDarken(level)` now rebuilds the int from `ClientLevel.getSkyDarken(1f)`. Fabric `companionworld` went 24 PASS/4 FAIL -> 28/28.
- Validation: `:common:test` 783/1 skipped/0 failed; Fabric + NeoForge GameTests **348/348** each; scenes Fabric and NeoForge: eel 7, lod 5, moods 11, particles 12, party 23, scenes 13, world 28, worldcost 63 (all PASS, 162 checks per loader), no mixin/class errors in either `latest.log`. Logs `build/backport-catchup/session-03/1211-stage4b-*.log`; screenshots (viewed) `docs/backport-catchup/selftest/stage4b/{fabric,neoforge}`: ghost-owner G1 ring with name tag, school of 3 with name tags, three eels on the head, night light scene.
- Deviation: GameTest `place()` anchors at shared spawn (checkpoint 50). Markers cleared in `fabric/run` and `neoforge/run`; no game clients left (Gradle daemon only).
- NOT verified: `twoclient` (needs dedicated server + two clients; source harness `docs/fish-companions-twoclient/*` not ported), first-person hand visits, shoulder/ground benthic feel, bell/jet motion, real-play look of moods/particles.
- Remaining stage 4 on 1.21.1: twoclient harness + run; owner looks above; F15 `TODO`s. Not stage 4: ambient scenes (F13/F14) and `companiontutorial` (F15).
- Mirroring stage 4 to 1.20.1 will need: (1) the same 4 client/server commit groups; (2) world pass: Fabric/Forge `AFTER_ENTITIES` equivalents (Forge 47 `RenderLevelStageEvent.AFTER_ENTITIES`, Fabric `WorldRenderEvents.AFTER_ENTITIES`) with the old `PoseStack`/`MultiBufferSource` path and `ItemRenderer.renderStatic(…, int seed)` signature; (3) player layer: `RenderLayer<AbstractClientPlayer, PlayerModel<…>>` already matches 1.20.1 but `render(...)` params/`PlayerRenderer.addLayer` mixin target must be rechecked with javap; (4) mixin targets `ClientLevel`, `LevelRenderer.levelEvent`/`blockChanged`, `NoteBlock` — verify descriptors; (5) no `ResourceLocation.fromNamespaceAndPath` (use constructor), no data components (NBT facade), 1.20.1 `BiomeSource`/`Biome.getPrecipitation` and `Level.getSkyDarken` quirks (same client-side zero expected — reuse `skyDarken` helper), `GuiGraphics` blend fix must not break the debug overlay; (6) GameTest `place()` scatter and per-loader mock-player factory; (7) Forge has no `GhostConnection` mock-connection helper equivalent — inspect `NetworkRegistry`/`ConnectionData` for dev ghost; (8) party is in save data on 1.20.1 so client party reads need the party sync from F09.

### 53 — Pending STAGE4C-1201 (stage 4 slice 1 mirror onto 1.20.1)

- Session 3, fork. Mirror 1.21.1 `ba9fbf36` (profile gait/drift/group + FishAnimator), `3b70479c` (predator tag/datagen, client settings, dev-catch), `a0e803fe` (companion client layer, mixins, loader wiring, party sync for save-data party), `e79dbfec` (companionsim scene), and `650b06bf` (skyDarken helper, only if the client-side zero reproduces) onto `port/1.20.1` HEAD `211d2d66`. Logs `build/backport-catchup/session-03/1201-stage4c-*.log`. Interrupted => inspect 1.20.1 `git status/log`, `fabric/run` + `forge/run` markers and java processes; don't assume.

### 54 — STAGE4C-1201 (supersedes pending 53): stage 4 slice 1 mirrored to 1.20.1; skyDarken commit BLOCKED on signing

- Commits on `port/1.20.1` (signed, explicit paths, no attribution; marker still `4b0e818e`): **`8621d98d`** profile gait/drift/group fields + FishAnimator ripple/onBase (pattern switches rewritten as instanceof chains; test uses JsonOps not JavaOps); **`523d2b76`** predator tag (datagen path `tags/items/`, datagen adds `"replace": false`), client settings, dev-catch hook; **`d02dc252`** companion client layer (30 files + mixins + both loaders) **plus 1.20.1-only party sync**; **`715d4464`** `companionsim` scene.
- **NOT COMMITTED (staged):** `ClientCompanion.java` + `CompanionAmbient.java` = the skyDarken rebuild (1.21.1 `650b06bf`). Signing failed three times: `1Password: failed to fill whole buffer` (x2), then `agent returned an error`. Hooks passed each time (unit + Fabric 326/326). To finish: unlock the 1Password agent, then in `D:/GitHub/fishtastic-worktrees/mc-1.20.1` run `git commit -m "Rebuild the client sky darkening from ClientLevel.getSkyDarken so night lights and drifter glow work on 1.20.1"` (the two files are already staged). Did not bypass signing.
- skyDarken verified empirically on 1.20.1 before applying: temporary debug line (reverted) at `time set 18000` read client `Level.getSkyDarken()==0` but `ClientLevel.getSkyDarken(1f)==0.2` (log `1201-stage4c-skydbg-fabric.log`); bytecode agrees (`ClientLevel` only calls `updateSkyBrightness` in its constructor). Same quirk as 1.21.1.
- Party sync (new on this target): `CompanionPartySyncPacket(owner, party)` (id `companion_party_sync`), `CompanionStorage.setParty` broadcasts on every server-side write, `CompanionStorage.onPlayerJoin` sends every online party to the joiner (own party even if empty, clears stale client entries) and the joiner's party to everyone; hooked on Fabric `ServerPlayConnectionEvents.JOIN` and Forge `OnDatapackSyncEvent`. Client map `CLIENT_PARTIES` fed by `applyClientParty`. Verified indirectly: companionsim reads parties through `CompanionStorage` on the client and tracks the companion.
- 1.20.1 adaptations: world pass Fabric `WorldRenderEvents.AFTER_ENTITIES` (`context.tickDelta()`), Forge `RenderLevelStageEvent.AFTER_ENTITIES` (`event.getPartialTick()`); HUD dev overlay via Fabric `HudRenderCallback` and a Forge `registerAboveAll("companion_dev")` overlay; packet written as `FishtasticPayload`/`BufCodec` (UUID, party codec), registered with the class arg; `CompanionSceneType` codec is a `BufCodec`; `ChunkStatus` lives in `world.level.chunk`; skin slimness via `getModelName()` instead of `PlayerSkin`; jukebox mixin reads `Item.byId(data) instanceof RecordItem` + `getLengthInTicks()` (no JukeboxSong); Forge client scene-packet/tick/world-pass hunks applied by hand (`neoforge/*` hunks excluded). Mixin targets verified with javap on the 1.20.1 mapped jar: `PlayerRenderer(Context, boolean)`, `ClientLevel.unload/onChunkLoaded`, `LevelRenderer.blockChanged(BlockGetter,BlockPos,BlockState,BlockState,int)`/`levelEvent(int,BlockPos,int)`, `NoteBlock.triggerEvent`, `LivingEntityRenderer.addLayer` (private `LevelRenderer.level` for the shadow).
- Validation (fresh, logs `build/backport-catchup/session-03/1201-stage4c-*.log`): `:common:test` **791 tests, 1 skipped, 0 failed** (781 + FishAnimatorFloorHeading, CompanionGroupConfig, FishtasticClientConfig, OwnerFallTracker, ...); `:fabric:runGametest` **326/326**, `:forge:runGametest` **326/326**; `:fabric:runDatagen` changed only `tags/items/predator_fish.json` (the `"replace": false` line); compile clean for common/Fabric/Forge main+testmod.
- Client scene `companionsim` (marker + `:fabric:runClient` then `:forge:runClient`): **18/18 checks PASS on both**, no mixin/class errors in either `latest.log`. Screenshots viewed (`docs/backport-catchup/selftest/companionsim/{fabric1201,forge1201}`): garden eel on the head via the player layer, glass squid as a faint dot, giant manta black/grey (texture is genuinely black, same as 1.21.1), bluegill small and far, held carried fish in hand. Markers cleared in `fabric/run` and `forge/run`; no game processes left.
- Not verified / owner's eyes (same list as checkpoint 48): starfish benthic and shoulder perches never seen in a frame, first-person hand visits, name tags while sneaking, bell/jet motion, glass squid translucency ordering, real two-player party sync (only singleplayer exercised; the join/broadcast path for a second player is untested).
- Next: unblock signing and commit the skyDarken change; then mirror stage 4 slice 2 (F11 server half + `/fishtastic companion` full command + scenes, 1.21.1 commits `1fdf326d`, `84539ad8`, `88021d42`; `650b06bf` is the skyDarken one) onto 1.20.1; Forge needs its own ghost-connection helper (checkpoint 52 item 7).

### 55 — skyDarken commit landed (resolves the block in checkpoint 54)

- 1Password unlocked; `port/1.20.1` commit **`dc9645b0`** (skyDarken rebuild, `ClientCompanion` + `CompanionAmbient`, signed, hooks green: Fabric 326/326). Stage 4 slice 1 is now fully committed on 1.20.1 (`8621d98d`, `523d2b76`, `d02dc252`, `715d4464`, `dc9645b0`). Marker still `4b0e818e`. Next: slice 2 mirror onto 1.20.1.

### 56 — Pending STAGE4D-1201 (stage 4 slice 2 mirror onto 1.20.1)

- Session 4 (2026-10-17) resume. Rechecked heads: source `48fffdfb`, 1.21.1 `88021d42`, 1.20.1 `dc9645b0`; target statuses show only runtime log churn plus the preserved untracked files. Matches checkpoints 52/55.
- Fork started: mirror 1.21.1 `1fdf326d` (scene manager/ghost/GhostConnection + 22 GameTests), `84539ad8` (full `/fishtastic companion` command), `88021d42` (nine self-test scenes) onto `port/1.20.1` HEAD `dc9645b0`; apply the adaptation list in checkpoint 52 (items 1-8, Forge needs its own ghost-connection helper). Logs `build/backport-catchup/session-03/1201-stage4d-*.log`. Interrupted => inspect 1.20.1 `git status/log`, `fabric/run` + `forge/run` markers and java processes; don't assume.

### 56 — Pending STAGE4D-1201 (stage 4 slice 2 mirror onto 1.20.1)

- Session 3, fork. Mirror 1.21.1 `1fdf326d` (scene manager/ghost/GhostConnection + 22 GameTests), `84539ad8` (full `/fishtastic companion` command), `88021d42` (nine self-test scenes) onto `port/1.20.1` HEAD `dc9645b0`, one commit each, signed, explicit paths. Validate per commit: fresh `:common:test`, compile common/fabric/forge main+testmod, `runGametest` both (expect 348), then the nine scenes on Fabric and Forge. Logs `build/backport-catchup/session-03/1201-stage4d-*.log`. Interrupted => inspect 1.20.1 `git status/log`, `fabric/run` + `forge/run` markers and java processes; don't assume.

### 57 — STAGE4D-1201 commit 1 landed (still pending 56 for commits 2-3)

- `port/1.20.1` **`7de53c3c`** (signed, explicit paths): `CompanionSceneManager`, `CompanionGhost`, `GhostConnection` + Fabric/Forge `GhostConnectionImpl` (Forge is a no-op), tick hook, `CompanionSceneGameTests`/`CompanionGroupsGameTests`/`CompanionProfileGameTests` wired into `FishtasticGameTests`. Marker `4b0e818e`.
- 1.20.1 adaptations: `ServerPlayer(server, level, GameProfile)` (no cookie), `connection.setProtocol(PLAY)` before `placeNewPlayer(connection, player)`; scene packet sent via `NetworkApiSided.sendToPlayer(.., CompanionScenePacket.STREAM_CODEC)`; the GameTest `place()` now also pins Y to the spawn's Y (Forge 47 scatters mock players several blocks vertically; first Forge run failed 3 G2 tests for exactly that, Fabric was unaffected).
- Validation: `:common:test` 791/1 skipped/0 failed; compile common/fabric/forge main+testmod green; Fabric `runGametest` **348/348**, Forge **348/348** (after the Y fix). Logs `build/backport-catchup/session-03/1201-stage4d-c1-*.log`.
- NOT verified: the ghost owner on a real Forge connection (GameTests do not use the ghost); needs the `/fishtastic companion` command + client run.

### 58 — STAGE4D-1201 commit 2 landed; commit 3 (scenes) applied uncommitted, scene runs pending

- `port/1.20.1` **`506938d9`**: full `/fishtastic companion` command (applied cleanly). Validation: `:common:test` 791/1/0, Fabric **348/348**, Forge **348/348**, compile common/fabric/forge clean.
- Commit 3 (nine scenes in `RenderSelfTest`) applied to the 1.20.1 worktree, **uncommitted**: conflicts hand-resolved (OPT_IN_SCENES keeps the 1.20.1 PORT-ONLY note, no `cosmeticpreview`); 1.20.1 fixes: jukebox event 1010 passes `Item.getId(record)` (no `JUKEBOX_SONG` registry), `player.getModelName()` for skin model. Compiles clean.
- **Pending**: scene runs, 8 scenes (`companionscenes companionparty companioneel companionlod companionmoods companionworld companionparticles companionworldcost`; `twoclient` needs a dedicated server + two clients, not run, as on 1.21.1) via `:fabric:runClient` then `:forge:runClient`, markers in `fabric/run` and `forge/run`. If interrupted: inspect `git status` in the 1.20.1 worktree (RenderSelfTest modified), the markers, java processes.

### 59 — STAGE4D-1201 complete (supersedes pending 56 and interim 58)

- `port/1.20.1` commits (signed, explicit paths, no attribution; marker still `4b0e818e`): **`7de53c3c`** scene manager/ghost + 22 GameTests (checkpoint 57), **`506938d9`** full `/fishtastic companion` command, **`f347a50b`** nine ported self-test scenes (opt-in). Stage 4 slice 2 is fully mirrored; 1.20.1 head `f347a50b`.
- Validation: `:common:test` 791/1 skipped/0 failed; Fabric and Forge `runGametest` **348/348** each. Scenes (8 of 9; `twoclient` not run) on `:fabric:runClient` then `:forge:runClient`: companionscenes 13, party 23, eel 7, lod 5, moods 11, world 28, particles 12, worldcost 63, **all PASS, 0 FAIL** on both loaders (163 PASS lines each, identical to 1.21.1's counts), no mixin/class errors in either `latest.log`. Logs `build/backport-catchup/session-03/1201-stage4d-*.log`. Screenshots (viewed, 1600x900) in `docs/backport-catchup/selftest/stage4d/{fabric1201,forge1201}`: ghost-owner G1 ring with `CompanionGhost` name tag, three eels on the head, school of three. The ghost owner ran on a real Forge connection (no-op `GhostConnectionImpl` sufficed with `setProtocol(PLAY)` + `EmbeddedChannel`).
- 1.20.1 deviations: see 57 (ServerPlayer ctor, GameTest `place()` pins Y); scenes: jukebox event 1010 uses `Item.getId(recordItem)` (no `JUKEBOX_SONG` registry), `player.getModelName()` for the eel skin log. No client-side skyDarken debugging needed (already in `dc9645b0`).
- Markers cleared (`fabric/run`, `forge/run`); no game clients left running.
- NOT verified (same as 1.21.1): `twoclient` (dedicated server + two clients; source harness `docs/fish-companions-twoclient/*` not ported), real two-player party sync / join broadcast, first-person hand visits, benthic walk/shoulder feel, bell/jet motion, jukebox/note reactions in real play, glass squid translucency ordering, F04 tank frame comparison. Source scene `cosmeticpreview` not ported (D4).
- Next: stage 4 residue (twoclient on both targets, F15 `TODO`s), then stage 5 (ambient fish F13/F14).

### 60 — Pending STAGE5A-1211 (stage 5: ambient fish F13/F14 on 1.21.1)

- Session 4. Heads: source `48fffdfb`, 1.21.1 `88021d42`, 1.20.1 `f347a50b`-era (unchanged since checkpoint 59). Fork started to scope and port F13 (server context/protocol/population) then F14 (client ambient sim/rendering/perf) onto `port/1.21.1`, writing `docs/backport-catchup/stage5-scope.md` first. Logs `build/backport-catchup/session-03/1211-stage5-*.log`. Interrupted => inspect 1.21.1 `git status/log`, `fabric/run` + `neoforge/run` markers and java processes; don't assume.

### 60 — Pending STAGE5A-1211 (stage 5 ambient fish F13/F14 on 1.21.1)

- Session 4 fork. Step 1: measure the stage 5 surface and write `backport-catchup/stage5-scope.md`. Step 2: implement slice by slice in `port/1.21.1` (HEAD `88021d42` at start), validating each. No 1.20.1 mirror. Logs `build/backport-catchup/session-03/1211-stage5a-*.log`. Interrupted => inspect 1.21.1 `git status/log`, `fabric/run` + `neoforge/run` markers and java processes; don't assume.

### 61 — STAGE5A-1211 slice 1 committed: ambient context server half (F13)

- `port/1.21.1` commit **`dfec46f1`** (signed by hook-run commit, explicit paths, no attribution; marker still `4b0e818e`): `AmbientContextRequestPacket`/`ResponsePacket`, `AmbientContextServer`, `AmbientRequestThrottle`, registration in `FishtasticPackets` (ids, client->server, server->client, codec-only list), `AmbientContextServerRulesTest`, `AmbientRequestThrottleTest`, `AmbientContextGameTests` (9) + 6 ambient packet round-trip/bounds tests in `PacketRoundTripGameTests`, wrappers appended to `FishtasticGameTests`.
- Adaptations: `Identifier`->`ResourceLocation`, `lookupOrThrow`->`registryOrThrow`, `dimension().identifier()`->`location()`, `player.level()`->`serverLevel()`, 9-field `StreamCodec.composite` -> target `StreamCodecs.composite`. Client response handler is registered via `registerClientHandler` (none yet; slice 2). Not ported: companion packet round-trip tests that were in the same source file (belong to earlier stages).
- Validation: `:common:cleanTest :common:test` 794 tests, 1 skipped, 0 failures (783 + 11); Fabric `runGametest` **363/363**, NeoForge **363/363** (348 + 15); compile fabric/neoforge main+testmod green. Logs `build/backport-catchup/session-03/1211-stage5a-s1-*.log`.
- Next: slice 2 = client ambient runtime (`client/ambient/*`, `AmbientParityFixture`, client wiring, unit tests), compile-driven.

### 62 — STAGE5A-1211 slice 2 committed; slice 3 (scenes) pending

- `port/1.21.1` commit **`1a0608a4`**: client ambient runtime (`client/ambient/*`, 13 files + new `AmbientModelBounds`), `AmbientParityFixture`, 9 ambient unit-test classes, Fabric/NeoForge client wiring (response handler, `fishtasticambient` client command, resource reload invalidation, join/leave/tags clear, tick, AFTER_ENTITIES draw with the loader's frustum). `:common:cleanTest :common:test` 839 tests, 1 skipped, 0 failures; compile fabric/neoforge main+testmod green; hook GameTests green.
- Rendering adaptation: `SubmitNodeCollector`+`ItemStackRenderState` replaced by `MultiBufferSource` + `ItemRenderer.renderStatic`; `submitWorld` gains a `Frustum` parameter (26.1 read `camera.getCullFrustum()`); the fade pivot (26.1 `ItemStackRenderState.getModelBoundingBox`) is rebuilt from the baked quads through the FIXED display transform (`AmbientModelBounds`, cached per member). Camera: `getPosition()`/`getLookVector()`; `LevelRenderer.getLightColor`; 1.21.1 `Registry.get`/`registryOrThrow`.
- **Pending STAGE5A-SCENES-1211-FABRIC**: ported the 8 ambient scenes (ambientparity/water/context/population/lifecycle/island/floor/walk, source lines 419-2641 of `RenderSelfTest`) + helpers (`worldCommand`, `noteState`, `noteCell`, `remoteServer` fixed null: dedicated-server harness not ported) + `TankGroupFlock.fishPose` (F14 dev capture) into the 1.21.1 tree, uncommitted. Marker `fabric/run/fishtastic_render_selftest` lists all 8; `:fabric:runClient`, log `build/backport-catchup/session-03/1211-stage5a-s3-selftest-fabric.log`. Interrupted => inspect `git status`, `fabric/run` marker and java processes; then NeoForge.

### 63 — Pending STAGE5A-SCENES-1211 (supersedes the pending in 62): ambient scene runs, per loader, two markers each

- Findings from the first long Fabric runs (`1211-stage5a-s3-selftest-fabric.log`, `-fabric2.log`, ~21 min each; NeoForge partial `-neoforge-a.log`): 113-118 PASS, FAILs only in `ambientcontext` phase B (held rod lane) and `ambientpopulation` lanes B-D. **Root cause proven from the log, not a port bug:** all scenes in one marker are queued into one step deque and `ambientcontext` and `ambientpopulation` interleave (both start at the same second; population's `handRod(null)` empties the hand the context scene just filled, and on NeoForge the leftover rod flipped population's baseline fingerprint to the rod lane `5b839295...`). Run alone, both pass fully on Fabric (`ambientcontext` 9/9 + traffic in `-probe.log`, `ambientpopulation` 24/24 in `-probe2.log`). Rule: **run `ambientcontext` and `ambientpopulation` in separate marker runs.**
- Why the run is slow: 70% of the time is `ambientpopulation`'s long-run soak (12000 ticks at real-time 20 tps = ~10.5 min); the other 7 scenes take ~5 min; plus ~1.5 min start per launch. The soak is now gated behind a marker line `soak=on` (default off; harness-only change in `RenderSelfTest`). The soak passed on Fabric twice (turnover/lifetimes/occupancy PASS, 12000 ticks).
- **Pending**: `/tmp/runscenes.sh` runs Fabric A (parity, water, population, lifecycle, island, floor, walk), Fabric B (context), NeoForge A, NeoForge B sequentially, logs `build/backport-catchup/session-03/1211-stage5a-s3-{fabric,neoforge}-{A,B}.log`, done-file `stage5a-s3-all.done`. Markers are removed after each run. Interrupted => inspect `fabric/run`, `neoforge/run` markers and java processes, and which logs exist.

### 64 — STAGE5A-SCENES-1211 interrupted (fork stopped by the user, session 4, 2026-10-18)

- The stage 5 fork was stopped mid-run. Pending 63 is **unresolved**: no scene result for Fabric B, NeoForge A/B is conclusive.
- `port/1.21.1` HEAD `1a0608a4`; committed: `dfec46f1` (F13 server half), `1a0608a4` (F14 client half). Uncommitted: `TankGroupFlock.java` (+8) and `RenderSelfTest.java` (ambient scenes, `soak=on` gate, ~2300 lines, mid-edit: the last report said a closing `}` was missing and being fixed, so it may not compile). Not committed on purpose.
- Observed at inspection: `neoforge/run/fishtastic_render_selftest` marker is armed (clear it before any owner playtest); a NeoForge client java (PID 44588), a Gradle wrapper (46420) and a bash script (41868, `/tmp/runscenes.sh` likely) were still alive. Whatever they write is not a verified result.
- Resume: confirm the processes are gone, clear markers, fix/compile `RenderSelfTest`, commit it as the scenes slice, rerun scenes per 63, then stage 5 mirror to 1.20.1.

### 64 — Correction to 63 and workflow stop (session 4)

- Fabric A (`ambientparity,water,population,lifecycle,island,floor,walk`, no context scene): 110 PASS / 8 FAIL. Population's rod-lane checks (`weights/charm/quality/excluded/return.applied`) failed **without** `ambientcontext` in the run, so the interleave explanation in 63 is at most half the story. What the data shows: `ambientcontext` alone 10/10 PASS (Fabric B), `ambientpopulation` alone 24/24 PASS, but any run where `ambientparity`/`ambientwater` come first leaves the later rod handoff ineffective (server answers the worms lane). **Root cause not yet proven.** Also `ambientfloor.continuity` FAIL (expiry retirements=6) in that run, not seen in the earlier combined run; unexamined.
- Process note: ~2 h of this session went to client self-test launches (each ~1.5 min start + scene time; first runs ~21 min each, of which 10.5 min is the population soak, now gated behind `soak=on`). Do not run the whole ambient pass as one launch again; run scenes in isolation (context 10/10, population 24/24 alone) and bisect only the specific leak.
- `/tmp/runscenes.sh` was still running NeoForge A/B at this checkpoint; its logs are `1211-stage5a-s3-neoforge-{A,B}.log`. Slice 3 (ported scenes, `TankGroupFlock.fishPose`, soak gate) is uncommitted in the 1.21.1 tree. Markers must be cleared: `fabric/run` and `neoforge/run` `fishtastic_render_selftest`.

### 65 — STAGE5A-1211 slice 3 committed (supersedes pending 63; extends 64)

- Script finished; markers cleared. Results, identical on both loaders: `ambientcontext` alone 10/10 PASS (Fabric, NeoForge); `ambientpopulation` alone 24/24 PASS (Fabric only; includes the 12000-tick soak). In the multi-scene run A (parity, water, population, lifecycle, island, floor, walk): Fabric 110 PASS / 8 FAIL, NeoForge 109 PASS / 9 FAIL, failures only `ambientpopulation` rod-lane checks and `ambientfloor.continuity`. Parity, water, lifecycle, island and walk fully pass. The order-dependent rod-lane failure and `ambientfloor.continuity` remain **unexplained** (see 64); the product code is not the suspect since both scenes pass alone.
- Committed as `port/1.21.1` **`81018cbf`** (hook units + Fabric GameTests green): the ported scenes, helpers, `TankGroupFlock.fishPose` and the `soak=on` gate. NOT verified: NeoForge `ambientpopulation` alone and the soak on NeoForge; screenshots from these runs were not reviewed; real-play look of ambient fish (translucency, fade pivot from `AmbientModelBounds`, frustum culling, performance) needs the owner.
- Remaining for stage 5 on 1.21.1: isolate the scene-order leak (cheap: `ambientwater` + `ambientpopulation` only), `ambientfloor.continuity`, F15 hooks. 1.20.1 mirror not started: do it in its own session, unit tests + GameTests as the gate, one scene per loader at the end.

### 66 — Pending STAGE5A-BISECT-1211

- Fabric run with marker `ambientwater`,`ambientpopulation` (soak off) to see whether `ambientwater` alone causes the population rod-lane failures. Script `/tmp/bisect.sh`, log `build/backport-catchup/session-03/1211-stage5a-bisect-water-pop.log`, done-file `stage5a-bisect.done`; marker removed by the script. Interrupted => inspect `fabric/run` marker and java processes.

### 67 — STAGE5A-BISECT-1211 result (supersedes pending 66)

- `ambientwater`+`ambientpopulation` reproduced the population rod-lane failures (6 FAIL); the creative-slot packet is not reflected in the server's main hand after `ambientwater` (cause not found). Harness workaround: `handRod` and the context scene also write the server's copy directly in singleplayer. Rerun `-bisect-water-pop3.log`: **76 PASS / 0 FAIL**. `ambientfloor.continuity` and the multi-scene run not re-run.
- **Workflow rule (owner, 2026-10-18, corrected):** self tests are wanted and should be launched without being asked; the problem is their length (4+ hours of this session). Rules: only the scenes a slice touches; long phases opt-in (`soak=on`); launch in the background and keep working instead of polling; aim under ~5 min per launch; never relaunch just to bisect a harness failure.

### 68 — Pending STAGE5B-1201 (stage 5 ambient mirror onto 1.20.1)

- Session 4, 2026-10-18. Source commits on 1.21.1: `dfec46f1`, `1a0608a4`, `81018cbf`, `1655f0c4`. `port/1.20.1` HEAD `f347a50b`. Fork mirrors them (scenes commit = code only, no client launches per checkpoint 67). Gate: fresh `:common:test`, compile common/fabric/forge main+testmod, `:fabric:runGametest` + `:forge:runGametest` (expect 363/363). Logs `build/backport-catchup/session-03/1201-stage5b-*.log`. Interrupted => inspect 1.20.1 `git status/log` and java processes; don't assume.

### 68 — Self-test budget is now enforced by a script, not a promise

- The owner pointed out the duration rules in 67 repeat promises already made and broken. `docs/backport-catchup/run-scene.sh <worktree> <loader> <name> <budget-seconds> <scene lines...>` now runs one launch under a hard `timeout` (kills Gradle/java and clears the marker on overrun, reports OVERRUN, never a pass) and writes a one-line `<name>.summary`. Start it in the background; do not poll; read only the summary. Default budget 300 s. A scene that cannot meet its budget gets its fixed waits cut or is recorded as unverified. Killing java.exe is machine-wide on this Windows box: run nothing else in Java meanwhile.

### 69 — STAGE5B-1201 interrupted (fork stopped by the user, session 4, 2026-10-18)

- Pending 68 (stage 5 mirror onto 1.20.1) is **unresolved**. `port/1.20.1` HEAD is still `f347a50b`; nothing committed.
- Worktree is mid-merge for slice 1 (`dfec46f1`): staged/added ambient server files (AmbientContextServer, AmbientRequestThrottle, request/response packets, 2 unit tests, AmbientContextGameTests, FishtasticGameTests, PacketRoundTripGameTests) and **`FishtasticPackets.java` is an unresolved conflict (`UU`)**. Some files are `AM` (staged then modified). Not compiled or tested.
- No run markers armed in `fabric/run` or `forge/run`. Note: there are two checkpoints numbered 68 above (pending mirror; run-scene.sh note). Treat the pending mirror as superseded by this entry.
- Caution: `docs/backport-catchup/run-scene.sh` kills every `java.exe` on overrun; make it kill only its own process tree before first use.
- Resume: inspect `git diff --cached` / resolve `FishtasticPackets.java`, finish slice 1, validate (units, compile, Fabric+Forge GameTests), then slices 2-4. Scenes once per loader after the code gates, per the corrected rule in 67.

### 70 — Housekeeping (session 4, 2026-10-18)

- Numbering: the script entry formerly titled 68 is now **68b**; pending 68 (mirror) is superseded by 69 and then by the resumed mirror below. Next free number is 71+ unless later entries exist.
- `run-scene.sh` no longer runs `taskkill /IM java.exe`. On overrun it stops only java processes whose command line contains the target worktree path (client and wrapper); the shared Gradle daemon and other worktrees' jobs are left alone. Not yet executed.

### 71 — Pending STAGE5B-1201-RESUME (stage 5 mirror onto 1.20.1, resumed)

- Resumes interrupted 69 from the mid-merge slice 1 state (`FishtasticPackets.java` conflict). Fork finishes slices `dfec46f1`, `1a0608a4`, `81018cbf`, `1655f0c4` onto `port/1.20.1` (HEAD `f347a50b`). Gates: fresh `:common:test`, compile common/fabric/forge main+testmod, Fabric+Forge `runGametest` (expect 363 each); then ambient scenes once per loader via `run-scene.sh` (budget 300s, soak off). Logs `build/backport-catchup/session-03/1201-stage5b-*`. Interrupted => inspect `git status/log` and java processes; don't assume.

### 71 — Pending STAGE5B-1201-RESUME (stage 5 mirror onto 1.20.1, resumed)

- Session 4, 2026-10-18. Resuming mid-merge slice 1 (`dfec46f1`) in `port/1.20.1` (HEAD `f347a50b`; `FishtasticPackets.java` was `UU`). Then `1a0608a4`, `81018cbf`, `1655f0c4`. Logs `build/backport-catchup/session-03/1201-stage5b-*.log`. Interrupted => inspect 1.20.1 `git status`/`git diff --cached` and java processes; don't assume.

### 72 — STAGE5B-1201 slice 1 validated but NOT committed: signing blocked (supersedes pending 71)

- `port/1.20.1` HEAD still `f347a50b`. Slice 1 (`dfec46f1`: F13 server half) is fully **staged**; `git commit` failed with `1Password: agent returned an error` / `failed to write commit object`. Signing was not bypassed. To finish: unlock the 1Password agent, then in `D:/GitHub/fishtastic-worktrees/mc-1.20.1` run `git commit -m "Port the ambient context server, request throttle and packets with their tests (F13 server half)"` (no re-staging needed; verify with `git status`).
- The `FishtasticPackets.java` conflict was already correctly merged (both ids, request registration with class arg, response registration with class arg, sync codec entry). Staged files = 1.21.1 `dfec46f1` adapted.
- 1.20.1 deviations: (a) `PacketRoundTripGameTests` uses `ByteBuf`/`FriendlyByteBuf` instead of `RegistryFriendlyByteBuf`; (b) **real gap fixed in `network/codec/BufCodecs.java`: `list(int maxSize)` ignored the bound** (delegated to unbounded `list()`), so the 3 oversize encode/decode GameTests failed and any packet using it accepted unbounded lists from clients. It now throws `EncoderException`/`DecoderException` like vanilla 1.21.1 and caps initial capacity. This also tightens existing users (`CompanionParty` SIZE, `FinishFishingMinigamePacket`); their GameTests still pass.
- Validation (fresh): `:common:test` **802 tests, 1 skipped, 0 failed**; compile common/fabric/forge main+testmod green; `:fabric:runGametest` **363/363**, `:forge:runGametest` **363/363**. Logs `build/backport-catchup/session-03/1201-stage5b-s1-*.log` (`-gametest.log` first run with the 3 oversize failures; `-gametest2.log` the passing run).
- Not done: slices 2-4 (`1a0608a4`, `81018cbf`, `1655f0c4`) not started, since they must follow a committed slice 1. No scenes run. No markers armed.
- Next: unlock signing, commit slice 1, then slices 2-4 and one scene launch per loader via `run-scene.sh`.

### 73 — Slice 1 committed on 1.20.1; pending STAGE5C-1201 (slices 2-4)

- `port/1.20.1` commit **`f2872b53`** (F13 server half, signed after 1Password unlock; includes the `BufCodecs.list(maxSize)` bound fix and the `PacketRoundTripGameTests` ByteBuf adaptation). Validation as in checkpoint 72 (802 tests, Fabric/Forge 363/363). Marker still `4b0e818e`.
- **Pending STAGE5C-1201**: fork mirrors `1a0608a4` (client runtime/wiring), `81018cbf` (scenes + soak gate + TankGroupFlock.fishPose), `1655f0c4` (scene rod harness fix), then one scene launch per loader via `run-scene.sh`. Interrupted => inspect `git status/log` in the 1.20.1 worktree and java processes; don't assume.

### 74 — STAGE5C-1201 slice 2 committed (F14 client half on 1.20.1)

- `port/1.20.1` commit **`57dd1af4`** (signed, explicit paths, no attribution; marker still `4b0e818e`): client ambient runtime/store/context client, fixtures, 9 unit-test classes, Fabric + Forge client wiring (reload listener, join/leave/tags clear, tick, world-pass submit, `fishtasticambient` client command, response handler). Applied 1.21.1 `1a0608a4` with `git apply --3way --exclude='neoforge/*'`; Fabric conflict hand-merged; Forge hunks written by hand.
- 1.20.1 deviations: no `tickRateManager()` (1.20.3+), so the `/tick`-freeze guard is dropped and only `mc.isPaused()` remains (AmbientContextClient, AmbientFishRuntime, AmbientParityFixture); request goes out via `NetworkApiSided.sendToServer(packet, STREAM_CODEC)` and the encoded-size probe uses `BufCodec`/`FriendlyByteBuf`; no `PoseStack.mulPose(Matrix4f)`, so the frozen retirement pose is applied as `pose().mul(m)` + `normal().mul(m.normal(Matrix3f))`; Forge client command feedback uses `sendSuccess(() -> Component, false)`; Fabric world pass passes `context.tickDelta()` and `context.frustum()`; Forge passes `event.getFrustum()` and `getPartialTick()`.
- Validation (fresh, logs `build/backport-catchup/session-03/1201-stage5c-s2-*.log`): `:common:test` **847 tests, 1 skipped, 0 failed** (802 + 45 ambient); compile common/fabric/forge main+testmod green; `:fabric:runGametest` **363/363**, `:forge:runGametest` **363/363**. Hook ran units + Fabric GameTests green.
- NOT verified: the fade pivot (rebuilt from baked quads) and retirement-pose normals on the old `PoseStack`, frustum culling and performance in a client; those need the scene run / owner look.

### 75 — STAGE5C-1201 slices 3-4 committed; pending scene launches

- `port/1.20.1`: **`9d523839`** (eight ambient scenes + `TankGroupFlock.fishPose` + `soak=on` gate; 1.21.1 `81018cbf`) and **`d491cb3a`** (rod handed to the server inventory; `1655f0c4`). Hooks green (units + Fabric GameTests). Fresh `:common:test` **847 tests, 1 skipped, 0 failed**; compile common/fabric/forge main+testmod green (`1201-stage5c-s34-test.log`).
- Deviation: 1.20.1 has no `cosmeticpreview`/span-preview scenes, so `RenderSelfTest` lacked `placeSpanBox`; only that helper was copied (OPT_IN_SCENES keeps no `cosmeticpreview`, plus the eight ambient names).
- **Pending SCENES-1201-AMBIENT**: one launch per loader via `run-scene.sh` (300s budget, soak off), scenes `ambientparity`, `ambientwater`, `ambientpopulation`; Fabric first then Forge. Summaries `build/backport-catchup/session-03/1201-stage5c-{fabric,forge}.summary`. Interrupted => inspect `fabric/run`/`forge/run` markers and java processes.

### 76 — STAGE5C-1201 complete (supersedes pending 73 and pending SCENES-1201-AMBIENT in 75)

- `port/1.20.1` stage 5: **`f2872b53`** (F13 server), **`57dd1af4`** (F14 client), **`9d523839`** (scenes), **`d491cb3a`** (rod harness). Signed, explicit paths, no attribution; marker still `4b0e818e`. Fresh `:common:test` 847 / 1 skipped / 0 failed; Fabric and Forge GameTests 363/363 (after slice 2; slices 3-4 are client self-test code only, compiled and unit-tested).
- Scenes, one launch per loader via `run-scene.sh` (300s budget, soak off; `ambientparity`, `ambientwater`, `ambientpopulation`): Fabric `exit=0 PASS=89 FAIL=1`, Forge `exit=0 PASS=89 FAIL=1`, no mixin/class errors. The single failure on both is `ambientparity.fixture.freeze: FAIL steps=241`, **expected**: it exercises `/tick freeze`, and 1.20.1 has no `tickRateManager`, so the freeze guard was dropped (checkpoint 74). Not a runtime bug, but the check should be made version-aware or removed on this branch. Logs `1201-stage5c-{fabric,forge}.log/.summary`; markers cleared; no clients left.
- Screenshots viewed (copied to `docs/backport-catchup/selftest/stage5c/`): Fabric `ambientparity` shore_pair (fish visible in the tank and in the shore water), Forge `ambientpopulation` baseline (empty pool, one speck, from distance). Not examined: fade pivot, retirement pose normals, frustum culling, performance.
- NOT run on 1.20.1: `ambientcontext`, `ambientlifecycle`, `ambientisland`, `ambientfloor`, `ambientwalk`, soak; NOT verified: real-play look (owner), two-player ambient context.
- Next: see Resume here step 3.

### 77 — Pending STAGE6A-1211 (stage 6: onboarding F15 on 1.21.1)

- Session 4, 2026-10-18. Heads: 1.21.1 `1655f0c4`, 1.20.1 `d491cb3a`. Fork scopes F15 (source `465fe891`: tutorial/, tutorial packets, client highlighting/menu consumers, companion lesson hooks, TODO(F15) sites in `CompanionBondUnlocks`/`/fishtastic companion unlock`/`FishingMinigameManager.onPracticeInterrupted`), writes `docs/backport-catchup/stage6-scope.md`, then ports to `port/1.21.1` only. Gate: units + compile + both loaders' GameTests; tutorial/companiontutorial scenes via `run-scene.sh` once per loader. Logs `build/backport-catchup/session-03/1211-stage6-*`. Interrupted => inspect 1.21.1 `git status/log` and java processes; don't assume.

### 78 — STAGE6A-1211 code slices committed (F15 on 1.21.1); scene launches pending

- `port/1.21.1` commits (signed, explicit paths, no attribution; marker still `4b0e818e`): **`0de14496`** server half (CompanionTutorialStep/Manager, Sync/Advance packets + registration, `FishCatchSavedData` `companion_tutorial_steps` persistence, TutorialManager/TutorialStep/Encyclopedia rework, hooks at every `TODO(F15)` site, book/rod/FishingHook hooks, lang + `first_catch.json`, 9 new GameTests), **`bde8a310`** client (CompanionTutorialClientHandler, guided CompanionScreen layout, QuestLogScreen guide + `pulseQuestRow`/`selectOnNextOpen` focus, TutorialClientHandler rework, ItemHighlightRules, HUD wiring in `FishtasticHudLayers.renderTutorial` and both loader clients), **`c28e37a5`** `tutorial` + `companiontutorial` scenes (opt-in).
- Method: `git apply --3way` of `465fe891` for existing files, whole-file copy of the 5 new files (from 48fffdfb) plus the 802470b9 persistence hunks; 15 conflicts hand-resolved. Adaptations: `GuiGraphicsExtractor` -> `GuiGraphics` (`renderOutline`, `drawString`), `identifier()` -> `location()`, `ContainerInput` -> `ClickType`, `ItemStackTemplate` dropped, `getToastManager` -> `getToasts`, `Button.onPress()`. HUD: companion book hint drawn inside `FishtasticHudLayers.renderTutorial` (works for the toast pass and both loaders' HUD hooks).
- Found gap closed: target `QuestLogScreen` lacked the F08 `pendingFocusQuest` / `selectOnNextOpen(ResourceKey<Quest>)` focus mechanism (source nobody calls it except the tutorial's `pulseQuestRow`); ported with the tutorial.
- Validation: fresh `:common:test` 839 / 1 skipped / 0 failed; compile common/fabric/neoforge main + testmods green; Fabric and NeoForge `runGametest` **372/372** each (363 + 3 TutorialManager + 6 CompanionTutorial). Logs `build/backport-catchup/session-03/1211-stage6-*.log`.
- Caveat: commit `0de14496` was hooked against the working tree that already contained the client files, so it is not proven to compile alone; the three commits together are.
- All `TODO(F15)` markers in the tree are now resolved.
- **Pending STAGE6A-SCENES-1211**: `run-scene.sh` Fabric then NeoForge (`tutorial`, `companiontutorial`), 300 s budget each, summaries `1211-stage6-scenes-{fabric,neoforge}.summary`. Interrupted => inspect markers and java processes.

### 79 — STAGE6A-1211 complete (supersedes pending in 78)

- Scenes via `run-scene.sh` (300 s budget, `tutorial` + `companiontutorial`): Fabric `exit=0 PASS=21 FAIL=0`, NeoForge `exit=0 PASS=21 FAIL=0`; no mixin/class errors; markers cleared, no clients left. Logs and summaries `build/backport-catchup/session-03/1211-stage6-scenes-{fabric,neoforge}.*`.
- Screenshots viewed (copied to `docs/backport-catchup/selftest/stage6/{fabric,neoforge}`): Fabric `companiontutorial-equip` (guide panel left of the party menu, "Step 1 of 7", Skip tour, highlighted slots, Piles button outlined) and NeoForge `tutorial-claim` (Quest Log with the in-layout guide title/body above the token balance, Claim button on First Catch). Scope note: `docs/backport-catchup/stage6-scope.md`.
- NOT verified: 1.21.1 real-play feel of the lessons and layout at other window sizes (owner), the full companion tour with a real two-player server, `TutorialStep` migration from an old save with `WAITING_FOR_CAST` (default changed to `NOT_STARTED` as in the source; no old-save test run), commit `0de14496` compiling alone.
- Stage 6 on 1.21.1 is done. **Next:** mirror stage 6 to 1.20.1 (`0de14496`, `bde8a310`, `c28e37a5`; notes in the scope doc), then stage 4/5 residues and stage 7.

### 80 — Pending STAGE6B-1201 (stage 6 mirror onto 1.20.1)

- Session 4, 2026-10-18. Fork mirrors 1.21.1 `0de14496`, `bde8a310`, `c28e37a5` (and the QuestLogScreen focus mechanism) onto `port/1.20.1` (HEAD `d491cb3a`). Gate: units, compile, Fabric+Forge GameTests (expect 372), tutorial/companiontutorial scenes once per loader via `run-scene.sh`. Logs `build/backport-catchup/session-03/1201-stage6-*`. Interrupted => inspect 1.20.1 `git status/log` and java processes; don't assume.

### 81 — STAGE6B-1201 slice 1 committed (F15 server half on 1.20.1)

- `port/1.20.1` commit **`583839e8`** (signed, explicit paths, no attribution; marker still `4b0e818e`). Mirrors 1.21.1 `0de14496`: companion tutorial steps/manager, sync + advance packets, `companion_tutorial_steps` in `FishCatchSavedData`, reworked `TutorialManager`/`TutorialStep` (appended NOT_STARTED/ENCYCLOPEDIA_*; default `NOT_STARTED`), `TODO(F15)` hooks, lang, 9 GameTests. Compiles on its own (client files untouched in this commit).
- Method: `git apply --3way` of the 1.21.1 diff; conflicts in `RequestFishEncyclopediaPacket`, `TutorialAdvancePacket` (kept `FishtasticPayload`/`BufCodec`, added `showEncyclopedia`), `TutorialManager` (kept `Advancement`, not `AdvancementHolder`).
- 1.20.1 adaptations: new packets use `FishtasticPayload`/`BufCodec` + `NetworkApiSided.sendToPlayer`, registered with the class argument; `CompanionTutorialStep.STREAM_CODEC` is a `BufCodec`; `Inventory.contains(predicate)` -> `hasAnyOf(Set.of(item))`; GameTest uses `FishtasticItemData.customName` and the `component.BundleContents` facade.
- Old-save check: added `TutorialStepSaveCompatTest` (3 tests): original ordinals 0-10 still decode to the same `TutorialStep` names (WAITING_FOR_CAST is ordinal 1), every step round-trips, companion steps keep ordinals 0/1/2. Note 1.20.1 default is now `NOT_STARTED`, as in the source.
- Validation: fresh `:common:test` **850 tests, 1 skipped, 0 failed**; Fabric `runGametest` **372/372**, Forge **372/372**; compile common/fabric/forge main+testmod green. Logs `build/backport-catchup/session-03/1201-stage6-a-*.log`. Commit hook ran green (units + Fabric GameTests).

### 82 — STAGE6B-1201 slices 2-3 committed; scene launches pending

- `port/1.20.1` commits (signed, explicit paths, no attribution; marker `4b0e818e`): slice 2 client = mirror of `bde8a310` (guided CompanionScreen/QuestLogScreen/lesson panel/highlights/HUD + Fabric and Forge client wiring; the NeoForge file hunk became the Forge equivalent in `FishtasticForgeClient`); slice 3 scenes = mirror of `c28e37a5` (`tutorial`, `companiontutorial`, opt-in). Hashes in the git log of the worktree (see checkpoint 83 for final SHAs).
- Adaptations: `ServerboundCustomPayloadPacket` sends -> `NetworkApiSided.sendToServer(.., STREAM_CODEC)` (TutorialClientHandler, CompanionTutorialClientHandler, RenderSelfTest); scenes use `component.BundleContents`; OPT_IN_SCENES merge kept the target's list (no cosmeticpreview) and added the two new scenes.
- Validation after the client slice: fresh `:common:test` 850/1 skipped/0 failed; Fabric `runGametest` **372/372**, Forge **372/372**. Logs `build/backport-catchup/session-03/1201-stage6-b-*.log`.
- **Pending STAGE6B-SCENES-1201**: `run-scene.sh` once per loader (Fabric then Forge), scenes `tutorial` + `companiontutorial`, 300 s budget, summaries `1201-stage6-scenes-{fabric,forge}.summary`. Interrupted => inspect `fabric/run` + `forge/run` markers and java processes.

### 83 — STAGE6B-1201 complete (F15 on 1.20.1; supersedes pending 80 and the pending scenes entry in 82)

- `port/1.20.1` commits (signed, explicit paths, no attribution; marker `4b0e818e`): **`583839e8`** server half, **`a4c29a34`** client, **`d82b7522`** scenes (opt-in `tutorial`, `companiontutorial`).
- Validation: fresh `:common:test` 850/1 skipped/0 failed (incl. `TutorialStepSaveCompatTest`, 3); Fabric `runGametest` **372/372**, Forge **372/372**; common/fabric/forge main+testmod compile. Scenes via `run-scene.sh` (300 s budget, soak n/a): Fabric `exit=0 PASS=21 FAIL=0`, Forge `exit=0 PASS=21 FAIL=0`, no mixin/class errors in either log (`1201-stage6-scenes-{fabric,forge}.{log,summary}`); markers cleared, no clients left. Screenshots viewed and copied to `docs/backport-catchup/selftest/stage6b/{fabric,forge}`: Fabric companion guide (Step 1 of 7, Skip tour, highlighted slots, Piles outlined), Forge Quest Log with the in-layout guide title/body and Claim on First Catch.
- Old saves: ordinals 0-10 of `TutorialStep` still decode to the same steps (WAITING_FOR_CAST is ordinal 1); default is now `NOT_STARTED` as in the source. Tested at the codec level only, not by loading a real old world.
- NOT verified: real-play feel of the lessons and layouts at other window sizes (owner); full companion tour with a real two-player server; an old world loaded in game; the Forge client wiring is covered only by the scene run (singleplayer); the NeoForge GameTest skip in the commit hook (n/a here).
- Next: stage 4/5 residues, then stage 7 (see 'Resume here').

### 84 — Pending RESIDUE-1211 (close stage 4/5 residue on 1.21.1; 1.20.1 follows)

- Session 4, 2026-10-18. Scope for 1.21.1: (a) re-run `ambientfloor.continuity` (and ambientcontext/lifecycle/island/walk multi-scene sanity) and fix or record; (b) port the `twoclient` harness (source `docs/fish-companions-twoclient/*`, dedicated server + two clients) and run it once per loader within budget; (c) grep remaining `TODO(F15)` and resolve; (d) NeoForge soak (`soak=on`, ambientpopulation alone) once. Sequential: 1.20.1 residue starts only after this finishes (avoid two clients at once). Logs `build/backport-catchup/session-03/1211-residue-*`. Interrupted => inspect 1.21.1 `git status/log`, markers, java processes.

### 85 — RESIDUE-1211 started (task a: ambientfloor, d: NeoForge soak, b: twoclient port; c: no TODO(F15)/backport TODO markers remain in 1.21.1 java sources)

### 86 — RESIDUE-1211 result (supersedes pending 84 and the start note 85)

- (a) `ambientfloor` alone, soak off, `run-scene.sh` 300s: Fabric 4/4 PASS, NeoForge 4/4 PASS, `ambientfloor.continuity` PASS on both (`indivs=64/64`). So the earlier multi-scene failure was an order effect, not a port bug; the full 8-scene set in one launch was not re-run (deliberately). Logs `build/backport-catchup/session-03/1211-resid-floor-{fabric,neoforge}.log`.
- (d) NeoForge soak: `ambientpopulation` alone with `soak=on`, 24/24 PASS incl. `longrun.turnover` (spawns +30 / expired +30 over 12000 ticks), `lifetimes`, `occupancy` (`1211-resid-soak-neoforge.log`).
- (b) twoclient: the checkpoint-52 "not ported" was wrong in one respect: the scene was ported but `RenderSelfTest` had `remoteServer` stubbed to `null` and never parsed `server=`, so first run (both loaders implied) had each client play in its own integrated world (7 FAIL each, "alone after 180 s"). Fix: real `remoteServer` field, `server=` parse on the title screen, `connectToServer` (1.21.1 `ConnectScreen.startConnecting(..., false, null)`), integrated-server gate and quiet-world rules skipped when joined. Harness `docs/fish-companions-twoclient/{twoclient.init.gradle,run-twoclient.sh}` copied from source (runner pinned to JDK 21 via `-Dorg.gradle.java.home`). Rerun with dedicated server + two clients: **Fabric 7/7 PASS on both clients, NeoForge 7/7 PASS on both** (peer_visible, local/remote party, scene_packet, local_on_path, remote_in_scene, scene_done). Outputs `1211-resid-twoclient-{fabric2,neoforge}.out`. Generated run dirs/logs deleted (the runner recreates them; note `run-*` glob also matches `run-twoclient.sh`, so never `rm run-*` there).
- (c) No `TODO(F15)` or backport TODO markers remain in 1.21.1 java sources (stage 6 resolved them).
- Gate after the RenderSelfTest change: `:common:cleanTest :common:test`, testmod compile, Fabric 372/372 and NeoForge 372/372 GameTests, `BUILD SUCCESSFUL` (`1211-resid-gate.log`).
- **NOT COMMITTED (staged):** `RenderSelfTest.java`, `docs/fish-companions-twoclient/twoclient.init.gradle`, `run-twoclient.sh`. Signing failed (`1Password: agent returned an error`, not bypassed). To finish: unlock 1Password, then in `D:/GitHub/fishtastic-worktrees/mc-1.21.1` run `git commit -m "Port the two-client harness: dedicated-server join in the self-test, init script and runner (F10-F12 verification)"`. Git warns LF->CRLF for `run-twoclient.sh`; check it still runs under bash after checkout (strip CR if needed).
- Still open for stage 4/5 on 1.21.1: owner looks only (real-play feel, hand visits, bell/jet, translucency, fade pivot). 1.20.1 residue untouched: remaining ambient scenes, `ambientparity.fixture.freeze` guard, soak on Forge, twoclient on Fabric+Forge.

### 87 — twoclient harness committed on 1.21.1; pending RESIDUE-1201

- `port/1.21.1` commit **`e328ad6f`** (signed after unlock; `RenderSelfTest` server join, `docs/fish-companions-twoclient/{twoclient.init.gradle,run-twoclient.sh}`). Supersedes the staged-but-blocked note in 86. 1.21.1 residue is closed except owner looks.
- **Pending RESIDUE-1201**: on `port/1.20.1` (HEAD `d82b7522`): run remaining ambient scenes (context, lifecycle, island, floor, walk) once per loader; make `ambientparity.fixture.freeze` version-aware (1.20.1 has no `/tick freeze`) or drop it; Forge soak (`ambientpopulation` alone, `soak=on`); port the twoclient harness (mirror `e328ad6f`) and run it once per loader. Logs `build/backport-catchup/session-03/1201-residue-*`. Interrupted => inspect 1.20.1 `git status/log`, markers, java processes.

### 88 — Goal set: finish and validate all stages (session 4, 2026-10-18)

- Owner set a session goal: finish and validate all stages of the catch-up plan. Remaining: RESIDUE-1201 (fork running, see 87), then **stage 7 / F16**: resource/data parity comparison, clean datagen both targets, packaging, dedicated-server startup, full test/build/GameTest gates (fresh counts), one scene pass per loader, two-client scenes, source-commit ledger accounting for all 91 commits (dispositions incl. superseded history and source-only tooling), documented version differences, then and only then advance both parity markers from `4b0e818e` to the chosen snapshot `48fffdfb`. Six A/B switches stay true; publication/deployment out of scope.
- Next after RESIDUE-1201: stage 7 scope fork per target (1.21.1 first), read-only ledger/data comparison before any client runs.

### 88 — RESIDUE-1201 started (fork; supersedes nothing yet)

- Edited (uncommitted) in `port/1.20.1` (HEAD `d82b7522`): `RenderSelfTest` (server= join + `remoteServer` field mirrored from `e328ad6f`; `ambientparity.fixture.freeze` check removed, replaced by a note because 1.20.1 has no `/tick freeze`), `docs/fish-companions-twoclient/{twoclient.init.gradle,run-twoclient.sh}` copied with CRs stripped and `neoforge`->`forge` (port 25601, run dirs `run-forge-<role>`). Next: gate (`:common:cleanTest :common:test`, compile, Fabric+Forge GameTests), commit, then scenes sequentially (never two clients at once): ambient context/lifecycle/island/floor/walk per loader, Forge soak, twoclient per loader. Logs `build/backport-catchup/session-03/1201-residue-*`. Interrupted => inspect `git status` in the 1.20.1 worktree, markers, java processes.

### 89 — RESIDUE-1201 code committed; pending scene runs

- `port/1.20.1` commit **`2a4eb235`** (signed): `RenderSelfTest` server join + `remoteServer`, `ambientparity.fixture.freeze` check removed (note logged instead: no `/tick freeze` on 1.20.1), twoclient harness files (Forge adaptation: port 25601, `run-forge-<role>`). Gate: `:common:test` fresh, compile Fabric+Forge main+testmod, Fabric **372/372**, Forge **372/372** (`1201-residue-gate1.log`).
- **Pending**: `/tmp/resid1201.sh` sequentially (one client at a time) via `run-scene.sh`: per loader ambientcontext; ambientlifecycle+ambientisland; ambientfloor+ambientwalk (300s each), then Forge soak (`ambientpopulation`, `soak=on`, 900s). Summaries `build/backport-catchup/session-03/1201-resid-*.summary`, done-file `1201-resid-scenes.done`. Then twoclient per loader. Interrupted => inspect markers in `fabric/run` and `forge/run`, java processes.

### 90 — RESIDUE-1201 scene results; pending twoclient runs

- All single-launch runs finished inside budget, 0 FAIL, markers absent after: ambientcontext 10/10 (Fabric, Forge); ambientlifecycle+island 23/23 (both); ambientfloor+walk 7/7 (both); Forge soak (`ambientpopulation`, `soak=on`) 24/24. Logs/summaries `build/backport-catchup/session-03/1201-resid-*`. Screenshots not reviewed for these.
- **Pending**: `timeout 900 bash docs/fish-companions-twoclient/run-twoclient.sh fabric` then `... forge` from `D:/GitHub/fishtastic-worktrees/mc-1.20.1`; output `/tmp` plus the harness's own logs under `docs/fish-companions-twoclient/`. Interrupted => stop the process listening on ports 25600/25601, clear `run-*-a|b/fishtastic_render_selftest` markers, check java processes.

### 89 — RESIDUE-1201 stopped by the owner; twoclient on 1.20.1 NOT verified (supersedes pending 87 and the interim notes)

- Owner stopped the work after the Fabric `twoclient` run burned its full 15-minute cap (13:03-13:18) and produced no usable result; the Forge run was also killed mid-way. Fork `a1dc2664...` and `/tmp/two1201.sh` stopped; 6 java processes scoped to the 1.20.1 worktree killed; markers absent. HEAD `port/1.20.1` = `2a4eb235` (twoclient harness + dropped `/tick freeze` parity check; 372/372 Fabric+Forge). Untracked harness logs under `docs/fish-companions-twoclient/` left in place as evidence (do not commit).
- Recorded before the stop (single-launch runs, 0 FAIL): ambientcontext 10/10, lifecycle+island 23/23, floor+walk 7/7 (both loaders), Forge soak 24/24.
- **Finding from the logs (read, not relaunched):** the Fabric dedicated server logged within ~30 s of start `Netty Server IO ERROR Error receiving packet 40 ... EncoderException: Failed to encode: Can't access registry ResourceKey[minecraft:root / minecraft:worldgen/biome]` and disconnected FishB. So the run failed in under a minute and then idled to the cap. This looks like a real 1.20.1 dedicated-server packet-encoding bug (a codec reading the biome registry on the server side, not seen in singleplayer or GameTests). **Open: identify packet 40 and fix; Forge server log not yet inspected.**
- **Process rule (owner, 2026-10-18, final):** no long runs. Every run needs a fail-fast watcher (abort on ERROR/Exception/disconnect, on server or client exit, or on no progress for 60 s), hard cap 5 minutes, and the harness must report the first error, not wait. If a run cannot be made to fit, it is recorded unverified and not retried.

### 90 — Pending FIX-1201-PACKET40 (dedicated-server biome-registry encode error on 1.20.1)

- Goal hook still active (finish and validate all stages). Fork finds the packet behind `Error receiving packet 40 ... Can't access registry ... worldgen/biome` from `docs/fish-companions-twoclient/server-{fabric,forge}.log` (log reads only), fixes it on `port/1.20.1`, and proves it with a unit/GameTest round trip. No client launches; at most one fail-fast server+client join check capped at 3 minutes. Then stage 7 starts.

### 89b — Time caps removed (owner, 2026-10-18)

- Owner: a hard time cap is the wrong approach; it only cuts off long runs/commits. `docs/backport-catchup/run-scene.sh` no longer uses `timeout`; the budget argument is ignored. A run ends when Gradle exits or on failure evidence in the log (first `CHECK.*FAIL`, `/ERROR]`, exception, encode/decode error, disconnect, mixin/class error), then only that worktree's java processes are stopped and the first error goes to `<name>.summary`/`.first-error`. Supersedes the 5-minute cap and 60 s stall rule in 89. Untested script change.
- Direction under reassessment: root cause of the slow scenes is real-time (20 tps) waits, no fail-fast, per-launch Gradle cost and one shared client queue. Plan: move logic checks (population soak, context rules) into server GameTests/headless sim; keep client scenes for rendering smoke; stage 7 gate = units, GameTests, datagen, server startup, packaging, ledger accounting, plus one short smoke scene per loader. Awaiting owner agreement on that scope before the conversion work.

### 91 — Root cause of the 1.20.1 packet-40 error found (harness config, NOT product code); pending fail-fast Fabric check

- Packet 40 is vanilla `ClientboundLoginPacket`. Cause (from the logs, no relaunch): `twoclient.init.gradle` wrote `level-type=minecraft:flat` without `generator-settings`; the 1.20.1 dedicated server fills `{}`, which fails to parse (`No key layers in MapLike[{}]` at startup), falls back to a flat generator whose biome is not in the synced registry, and the login packet cannot encode (`Can't access registry worldgen/biome`) for both loaders (Forge server log shows the same `lost connection` for FishA/FishB). Not a mod packet; nothing in fishtastic encodes biomes. 1.21.1 uses the same init script but did not hit this (its flat default parses); code unchanged there, read-only check.
- Fix (uncommitted in `port/1.20.1`): explicit valid flat `generator-settings` in `twoclient.init.gradle`; `run-twoclient.sh` rewritten fail-fast (cap arg default 180 s; aborts on first server error/`No key layers`/disconnect/client exit; kills only its own port and worktree processes; last line `VERDICT:`). The old `wait $pa $pb` with no watcher is what burned 15 minutes.
- No unit/GameTest can reproduce this (dedicated-server flat fallback + vanilla login packet), so the proof is the fail-fast join check below.
- **Pending**: `bash docs/fish-companions-twoclient/run-twoclient.sh fabric 180` from the 1.20.1 worktree, output `build/backport-catchup/session-03/1201-twoclient-failfast-fabric.txt`. One run only; if VERDICT is not DONE with fails=0, record it and stop.

### 91a — Scope agreed; pending STAGE7A-1211 (final parity gates on 1.21.1)

- Owner agreed (2026-10-18): stage 7 gate = fresh units, GameTests, datagen, assembly/packaging, dedicated-server startup, resource/data parity comparison, source-commit ledger accounting, plus one short smoke scene per loader; long client scenes and soaks are not gates (soaks already passed: Fabric, NeoForge, Forge). No time caps; fail-fast on evidence only. Soak-to-GameTest conversion is NOT needed for the goal and is dropped.
- **Pending STAGE7A-1211**: fork on `port/1.21.1` (HEAD `e328ad6f`) does all of the above read-mostly; it may fix gaps found (signed commits) but does not advance the markers. 1.20.1 stage 7 follows after the packet-40 fix fork finishes. Logs `build/backport-catchup/session-03/1211-stage7-*`.

### 92 — S7-1211-A pending: gates started (fresh units, fishsim, tank-shape, assemble)

- Fork S7-1211. Pending: `:fishsim:cleanTest :common:cleanTest :tools:tank-shape-gen:cleanTest :fishsim:test :common:test :tools:tank-shape-gen:test :fabric:assemble :neoforge:assemble --no-parallel` in `port/1.21.1` (HEAD `e328ad6f`), log `build/backport-catchup/session-03/1211-stage7-units.log`; then Fabric datagen, GameTests, servers. Ledger/data comparison done in parallel read-only. Interrupted => inspect `git status` in the worktree and java processes.

### 93 — S7-1211 partial results (supersedes the gates note in 92 for units/packaging); pending datagen + GameTests

- Ledger: all 91 commits in `4b0e818e..48fffdfb` now carry `disposition`/`targetCommits`/`reason` in `source-commit-ledger.json` (checked 2026-10-18, target `port/1.21.1`): **56 ported, 12 superseded, 23 excluded, 0 unreviewed**. Exclusions are source-only docs/handoffs (19), tooling (`b79b693b`, `6bae743b`, `97b7d087` GUI designer), the `fishspin` promo clip (`cffab7c1`) and the CHANGELOG note (`a1a6a8a0`). Evidence: every non-doc path touched by each commit was tested for existence on the target; the only missing ones are files deleted at source HEAD (spike, fishing-reaction surface, `UnequipCompanionPacket`, `BenthicVisibilityTest`), the GUI designer tool, and the Fabric gametest wrapper replaced by the shared `FishtasticGameTests`.
- Resource/data parity (common main resources, 6462 source vs 6307 target files): no unexplained difference. Only-source: 207 26.1 `items/` definitions (target uses `models/item` + overrides; all but pale-oak arch, leaf-litter, fish_target_2, fish_pile_block have a target model), 8 26.1 core shaders (target uses `.json` shader formats), pale-oak arch and leaf-litter cosmetics + lang keys (owner decision A3.c in pass-2 checklist). 124 differing shared files all explained: 73 recipes equal after 1.21/26.1 format normalisation, structure JSONs differ only by block substitutions (`iron_chain`->`chain`, copper lantern->lantern, oxidized lightning rod, leaf litter dropped), tags the same substitutions, 5 `models/item` gain 1.21.1 predicate overrides, mcmeta/accesswidener/shader format differences.
- Fresh gates in `port/1.21.1` (HEAD `e328ad6f`), `BUILD SUCCESSFUL in 3m 11s`, log `1211-stage7-units.log`: common **839 tests / 1 skipped / 0 failed** (47 suites), fishsim **563 / 63 skipped / 0 failed** (101 suites), tank-shape-gen **21,955 / 0 skipped / 0 failed**. Fabric + NeoForge assemble OK; jars `fishtastic-{fabric,neoforge}-2.0.1+1.21.1.jar`, metadata version `2.0.1+1.21.1`, 717 data files, ~5,670 asset files, 852-862 classes, mixin configs + accesswidener present. (Stale `2.0.1b1` jars in `build/libs` are old builds.)
- **Pending**: `:fabric:runDatagen :fabric:runGametest :neoforge:runGametest --no-parallel`, log `1211-stage7-gates.log`, done file `1211-stage7-gates.done`; then servers and smoke scenes. Interrupted => inspect `git status` and java processes.

### 94 — S7-1211 complete: stage 7 gates pass on 1.21.1 (supersedes pending 92 and 93)

- Target `port/1.21.1` HEAD `e328ad6f`, no code changes were needed; no commits in this stage. Logs `build/backport-catchup/session-03/1211-stage7-*`.
- Passed with counts: units common 839/1 skipped/0 failed, fishsim 563/63 skipped/0 failed, tank-shape-gen 21,955/0/0 (checkpoint 93); Fabric + NeoForge assemble, jars `2.0.1+1.21.1`; **Fabric datagen: working tree unchanged** (no resource/source diff); **GameTests Fabric 372/372 and NeoForge 372/372** (`372 GAME TESTS COMPLETE`); **dedicated servers**: Fabric `Done (0.616s)`, NeoForge `Done (0.975s)`, no ERROR/Exception (new `docs/backport-catchup/run-server-check.sh`, stops on evidence only, never on time); **smoke scene `companionsim`** one launch per loader: Fabric exit 0, PASS=19 FAIL=0; NeoForge PASS=19 FAIL=0 (the watcher reported FAILFAST only because it matched the benign `Dev lost connection: Disconnected` logged at normal scene exit; pattern narrowed to `lost connection: (Internal|Timed|Encod|Decod)` in `run-scene.sh`, untested). Selftest markers absent afterwards.
- Ledger/data results are in checkpoint 93 and `source-commit-ledger.json` (56 ported, 12 superseded, 23 excluded, 0 unreviewed).
- Not verified (owner's eyes / not a gate): real-play feel and look items from checkpoints 48/52/79, F04 tank frame equivalence (pose proven bit-exact only), real two-player party sync beyond the two-client scene, old-world load of a `WAITING_FOR_CAST` save, in-game create-world deadlock for F02.
- 1.21.1 stage 7 is done. Remaining for the goal: stage 7 on 1.20.1 (same gates, using `run-server-check.sh` with `forge`), then the parent advances both markers from `4b0e818e` to `48fffdfb` and updates the parity ledgers.

### 92 — Packet-40 bug is REAL product code (supersedes the "harness config" conclusion in 91); fix in progress on 1.20.1

- Correction to 91: fixing the flat `generator-settings` removed `No key layers` but the encode error stayed (fail-fast run aborted at 83 s). True cause: `FishProfile.BiomeWeight` used `RegistryCodecs.homogeneousList(Registries.BIOME)` (a `HolderSet<Biome>`); `FishProfile.CODEC` is a synced registry (Fabric `DynamicRegistries.registerSynced`, Forge `DataPackRegistryEvent`), and 1.20.1's `ClientboundLoginPacket` writes synced registries with plain NbtOps, so each profile with biome weights fails with "Can't access registry worldgen/biome" (81 repeats). **On 1.20.1 nobody could join a dedicated server or LAN game** (singleplayer's local connection never encodes). Introduced by `104aba82` on this branch, i.e. it predates this catch-up. 1.21.1 syncs with RegistryOps, so it is unaffected (its twoclient passed 7/7).
- Proof before: new `FishProfileNetworkCodecTest` (registry-free JSON decode, NBT encode, NBT decode of all 64 shipped profiles): **36 of 64 profiles drop their biome_weights** (`optionalFieldOf` swallows the error silently) and the selector round trip throws. After: 2/2 pass. Logs `build/backport-catchup/session-03/1201-packet40-test-{before,after}.log`.
- Fix (uncommitted): `FishProfile.BiomeSelector(Optional<TagKey<Biome>>, List<ResourceKey<Biome>>)` with the same JSON forms (`"#tag"`, `"id"`, `["id", ...]`), `contains(Holder)`, `resolve(RegistryAccess)`; `BiomeWeight.biome` is now a selector; `CompanionAmbient.habitatOf` resolves against the client level when present, name heuristics otherwise; `FishingPoolFixture` uses `ofKeys`. `:common:test` 852 tests, 1 skipped, 0 failed; Fabric/Forge compile green.
- **Pending**: Fabric+Forge `runGametest` (expect 372), then one fail-fast `run-twoclient.sh fabric 180`.

### 95 — State snapshot before usage-limit pause (parent, 2026-10-18)

- Header "Resume here" rewritten with the exact state (items 1-7). Only running fork at snapshot time: `FIX-1201-PACKET40` (agent a4b7fa97...), which appends its own checkpoints; its working-tree changes are uncommitted in `port/1.20.1`. If it is interrupted: follow Resume here item 4. No client/server processes should be left; check `tasklist` java and `fabric|forge/run/fishtastic_render_selftest` markers before any playtest.

### 93 — FIX-1201-PACKET40 complete (supersedes pending 90 and the pending in 92)

- `port/1.20.1` commit **`14f02e7a`** (signed, explicit paths, no attribution; marker still `4b0e818e`): `FishProfile.BiomeSelector` replaces the registry-needing `HolderSet<Biome>` in the synced `BiomeWeight`; `CompanionAmbient`, `FishingPoolFixture`, new `FishProfileNetworkCodecTest`; `twoclient.init.gradle` flat `generator-settings` (harmless, not the cause); `run-twoclient.sh` now fail-fast (cap arg default 180 s, aborts on first server error/disconnect/client exit, kills only its own port + worktree processes, last line `VERDICT:`).
- Validation: fresh `:common:test` **852 tests, 1 skipped, 0 failed** (850 + 2 new; before the fix the new test showed 36 of 64 profiles dropping biome_weights); Fabric + Forge `runGametest` **372/372** each; Fabric dedicated server + two clients through the fail-fast runner: **VERDICT: DONE (119 s) fails=0**, both clients 7/7 twoclient checks plus shaders, both joined the server and saw each other's party. Logs `build/backport-catchup/session-03/1201-packet40-*`, `1201-twoclient-failfast-fabric.txt`.
- **Not verified**: Forge twoclient (not run; same registry-free codec, so expected fine; the Forge server log showed the same encode failure before the fix); the cold/nether habitat inference now falls back to name heuristics when no world is loaded (in-world it resolves the live biomes as before).
- 1.21.1 and the 26.1.2 source still use `RegistryCodecs.homogeneousList(Registries.BIOME)` in the synced codec; on those versions registry sync uses registry-aware ops (1.21.1 twoclient passed 7/7 on a dedicated server, checkpoint 86), so no change there.
- Untracked harness logs/run dirs under `docs/fish-companions-twoclient/` remain on disk, not committed. Remaining stage 7 work: see 88.

### 96 — Packet-40 fix committed; pending STAGE7B-1201

- `port/1.20.1` **`14f02e7a`** (signed): `FishProfile.BiomeSelector` (registry-free), `CompanionAmbient`, `FishingPoolFixture`, `FishProfileNetworkCodecTest`, fail-fast `run-twoclient.sh`, `twoclient.init.gradle`. Proof: 36/64 profiles dropped biome weights before, 852 tests 0 failed after, Fabric/Forge 372/372, Fabric dedicated + two clients `VERDICT: DONE fails=0`. Pre-existing 1.20.1 bug (from `104aba82`): nobody could join a dedicated server or LAN game on 1.20.1 before. Forge twoclient not run (same shared fix). 1.21.1/source unaffected (registry-aware sync). Supersedes "Resume here" item 4.
- **Pending STAGE7B-1201**: fork runs the stage 7 gates of checkpoint 94 on `port/1.20.1`: ledger accounting for 1.20.1 targets, data parity, fresh units, assemble/packaging (`2.0.1+1.20.1`), Fabric datagen clean, Fabric+Forge GameTests, both dedicated servers reach Done (`run-server-check.sh`, forge), one smoke scene per loader, then the Forge two-client join once. Does not advance markers.

### 97 — S7-1201-A pending: fresh gates started on 1.20.1 (units, fishsim, tank-shape, assemble)

- Fork STAGE7B-1201 started; `port/1.20.1` HEAD `14f02e7a`. Command from the 1.20.1 worktree: `.\gradlew '-Dorg.gradle.java.home=C:/Program Files/Java/jdk-21' :fishsim:cleanTest :common:cleanTest :tools:tank-shape-gen:cleanTest :fishsim:test :common:test :tools:tank-shape-gen:test :fabric:assemble :forge:assemble --no-parallel --console=plain`; log `build/backport-catchup/session-03/1201-stage7-unit-build.log`. Interrupted => inspect log/XML and java processes; do not assume.

### 98 — S7-1201-B ledger accounting for 1.20.1 done (uncommitted in source checkout)

- `source-commit-ledger.json`: every commit now has a populated `target1201` (status Done/Superseded/Excluded, mapped 1.20.1 commits, evidence, version-difference pointer), plus `accounting1201` and `commitMap1211To1201` (34 1.21.1 catch-up commits -> 1.20.1 equivalents, same-subject match plus 10 manual pairs). **91/91: 56 ported, 12 superseded, 23 excluded, 0 unreviewed**, identical to 1.21.1. Two superseded commits (`ce429e94`, `849841ce`, fishing-reaction surface deleted at source) have no target commits on either port by design. One 1.20.1-only commit `14f02e7a` (BiomeSelector login-packet fix) recorded under `extraTargetOnlyCommits`.
- Pending: unit/assemble gate from checkpoint 97 (done-file `1201-stage7-unit-build.done`), data parity, datagen/GameTests, servers, smoke scenes, Forge twoclient.

### 99 — S7-1201-C resource/data parity vs source (1.20.1) done: no unexplained difference

- Method: file-level comparison of `common/src/main/resources` (6462 source vs 6297 target files) after path normalisation (`recipe`->`recipes`, `advancement`->`advancements`, `loot_table`->`loot_tables`, tag dirs plural) and CRLF-insensitive hashing; script `build/backport-catchup/session-03/dp.py`, raw lists `dp.txt`.
- Only-source (232): 207 26.1 `items/` definitions (1.20.1 uses `models/item` + predicates, 60 target-only model files), 8 26.1 core shader sources (target-only: 6 `.json`/`.vsh` shader files in the 1.20.1 format), pale-oak arch and leaf-litter cosmetics + shop entries + lang keys (owner decision A3.c), `fish_target_2` model/texture (target has the texture under `textures/gui`), 2 `minecraft:tags/item/enchantable/*` (tag does not exist before 1.21), and **8 recipe advancements**: 7 for charms/almanac/little fish box are **stale dead data at source** (the recipes were removed in `9b2bebd2`, shop-only; 1.21.1 carries them too; not ported), and `marine_compost` (1.21.1 datagen emits an advancement for the custom recipe; 1.20.1 vanilla datagen emits none for special recipes - version difference affecting only the recipe-book unlock, recipe file itself is present).
- Differing shared files, all explained: recipes/advancements 1.20.1 vs 1.21 format, tags (item/block, plural dirs, 1.21-only members dropped), cosmetic structures (documented block substitutions, same set as 1.21.1), `fish_tank` loot table (`copy_components` -> NBT copy), 7 `models/item` predicate models, shader formats, `fishtastic.mixins.json`/`accesswidener` (per-version), `en_us.json` (2 owner-decision keys), `quest/daily/storm_chaser_gold.json` (`tuff_bricks` -> `tuff`, tuff bricks do not exist in 1.20.1; pre-existing substitution).
- Pending: unit/assemble gate (checkpoint 97); then Fabric datagen + GameTests, servers, smoke scenes, Forge twoclient.

### 100 — S7-1201-A result (supersedes pending 97): units + packaging pass on 1.20.1; pending datagen + GameTests

- `BUILD SUCCESSFUL in 3m 6s` (log `1201-stage7-unit-build.log`, exit 0). Summed `TEST-*.xml`: common **852 tests / 1 skipped / 0 failed / 0 errors** (50 suites), fishsim **563 / 63 skipped / 0 failed** (101 suites), tank-shape-gen **21,955 / 0 skipped / 0 failed**. Fabric + Forge assemble OK: jars `fishtastic-{fabric,forge}-2.0.1+1.20.1.jar` (7,360 and 7,901 entries), metadata version `2.0.1+1.20.1` in `fabric.mod.json` and `mods.toml`.
- **Pending**: `:fabric:runDatagen :fabric:runGametest :forge:runGametest --no-parallel` from the 1.20.1 worktree, log `1201-stage7-gates.log`, done file `1201-stage7-gates.done`; then compare `git status` for datagen cleanliness (ignore `common/logs/*`, harness output). No Forge datagen (hangs historically). Interrupted => inspect log/java processes.

### 101 — S7-1201-A result (supersedes pending in 100): datagen clean, GameTests pass on 1.20.1; pending servers

- `:fabric:runDatagen :fabric:runGametest :forge:runGametest` exit 0, `BUILD SUCCESSFUL in 2m 27s` (log `1201-stage7-gates.log`). **Fabric 372/372** (`372 GAME TESTS COMPLETE`, all required passed) and **Forge 372/372**. **Datagen clean:** `git status` (excluding `common/logs/*` and harness output) identical to the pre-run status (`1201-stage7-status-before.txt`); no resource or source diff.
- **Pending**: dedicated servers, sequentially: `bash docs/backport-catchup/run-server-check.sh D:/GitHub/fishtastic-worktrees/mc-1.20.1 fabric 1201-stage7-server-fabric` then `... forge 1201-stage7-server-forge`; summaries `*.summary`, chain done-file `1201-stage7-servers.done`. Stops on evidence only. Interrupted => inspect java processes (kills only this worktree's).

### 102 — S7-1201-A result: dedicated servers start on 1.20.1; pending smoke scenes

- Fabric `Done (5.158s)!`, Forge `Done (4.858s)!` via `run-server-check.sh` (summaries `1201-stage7-server-{fabric,forge}.summary`), no ERROR/Exception before Done.
- **Pending**: `companionsim` smoke, one launch per loader through `run-scene.sh` (fabric then forge), summaries `1201-stage7-smoke-{fabric,forge}.summary`, chain done-file `1201-stage7-smoke.done`. Interrupted => check markers in `fabric/run` and `forge/run` and java processes.

### 103 — S7-1201-A result: smoke scenes pass on 1.20.1; pending Forge two-client join

- `companionsim` one launch per loader: Fabric `exit=0 PASS=19 FAIL=0`, Forge `exit=0 PASS=19 FAIL=0` (summaries `1201-stage7-smoke-{fabric,forge}.summary`). Markers absent afterwards.
- **Pending**: one fail-fast `bash docs/fish-companions-twoclient/run-twoclient.sh forge` from the 1.20.1 worktree (default cap arg per the script; stop on first error), output `build/backport-catchup/session-03/1201-stage7-twoclient-forge.txt`, done-file `1201-stage7-twoclient.done`. Interrupted => stop processes on ports 25600/25601 and this worktree's java only; record unverified, do not retry.

### 104 — S7-1201 complete: stage 7 gates pass on 1.20.1 (supersedes pending 96-103)

- `port/1.20.1` HEAD `14f02e7a`; no new target commits in this stage. Gates (checkpoints 98-103): ledger 91/91 mapped (56 ported / 12 superseded / 23 excluded, 0 unreviewed) with `target1201`, `accounting1201`, `commitMap1211To1201` in `source-commit-ledger.json`; resource/data parity with no unexplained difference (checkpoint 99); units common 852/1 skipped/0 failed, fishsim 563/63 skipped/0 failed, tank-shape 21,955/0; assemble `2.0.1+1.20.1` (Fabric 7,360 / Forge 7,901 entries); Fabric datagen clean; GameTests Fabric 372/372, Forge 372/372; servers Fabric `Done (5.158s)`, Forge `Done (4.858s)`; companionsim smoke 19/0 per loader; **Forge two-client join `VERDICT: DONE (134s) fails=0`** (run with a 3600 s cap argument so only failure evidence stops it; script default is 180 s, change the default if reused).
- features.md: F13/F14/F16 rows corrected - the 1.21.1 fork had written 1.21.1 results in the 1.20.1 column; now 1.21.1 and 1.20.1 columns hold their own results.
- Not verified (owner's eyes / not gates): real-play feel and look items (checkpoints 48/52/79), F04 tank frame equivalence, real two-player party sync beyond the two-client scene, old-world load at `WAITING_FOR_CAST` (codec-level test only), in-game create-world deadlock (F02), Fabric two-client was passed before the fix commit only in the packet-40 run (`DONE fails=0`).
- Both targets' stage 7 is done. Remaining for the goal: the parent advances both parity markers `4b0e818e` -> `48fffdfb` in the port ledgers (not done by this fork) and writes the final owner-looks list.

### 105 — Stage 7 passed on both targets; marker advance STAGED, commit blocked on signing

- Stage 7 passed on 1.21.1 (checkpoints 93-94) and 1.20.1 (97-104): 91/91 commits accounted for (56 ported, 12 superseded, 23 excluded), data parity with every difference explained, units, assembly/packaging, clean Fabric datagen, 372/372 GameTests per loader, dedicated servers Done, smoke scene 19/0 per loader, two-client joins pass (1.21.1 both loaders; 1.20.1 Fabric and Forge after fix `14f02e7a`).
- Parity marker advance (`4b0e818e` -> `48fffdfb`, updated 2026-10-18) is **staged but UNCOMMITTED** in `docs/backport-pass2/checklist.md` in both worktrees (both rows edited in each file). Commit failed twice with `1Password: failed to fill whole buffer` / `agent returned an error`; signing not bypassed. To finish, after unlocking 1Password run in each worktree: `git commit -m "Advance the parity marker to 48fffdfb after the 2026-10 catch-up (91 commits accounted for, stages 1-7 gated)" -- docs/backport-pass2/checklist.md` (`D:/GitHub/fishtastic-worktrees/mc-1.21.1` then `mc-1.20.1`).
- Remaining after that: nothing automated. Owner-only looks: real-play feel (hand visits, bell/jet, benthic, translucency, fade pivot), F04 tank frame equivalence, real two-player party sync beyond the scene, old-world tutorial step `WAITING_FOR_CAST`, F02 create-world deadlock in game. Source-checkout docs (`docs/backport-catchup*`, README, ledger) remain uncommitted by design until the owner decides. `run-twoclient.sh` still defaults to a 180 s cap; owner rejects caps, so remove it if reused.

### 106 — Parity markers advanced (supersedes the blocked entry in 105)

- 1Password signing worked again. `port/1.21.1` **`99adc3f3`** and `port/1.20.1` **`d00b52d7`** (signed, `docs/backport-pass2/checklist.md` only): both parity markers `4b0e818e` -> `48fffdfb` (2026-10-18). The catch-up plan is complete: stages 1-7 gated on both targets.
- Remaining is owner-only: real-play feel (hand visits, bell/jet, benthic, translucency, fade pivot), F04 tank frame equivalence, real two-player party sync beyond the scene, old-world tutorial step `WAITING_FOR_CAST`, F02 create-world deadlock in game. Source-checkout docs/ledger (`docs/backport-catchup*`, README) remain uncommitted. `run-twoclient.sh` default 180 s cap should be removed if reused. If 1.20.1 was released, `14f02e7a` (dedicated-server join fix) may need its own release.

### 107 — Owner playtest feedback follow-up pending (2026-10-18)

- Scope: fix podium avatar placement on 1.20.1, plain-click companion return progression on all versions, name readability on both backports; add initial fishing Play/Skip for later confirmation, consistent fishing/encyclopedia/companions debug subcommands, concise companion copy across all three version lines.
- Pending operation FEEDBACK-01: inspect and patch source plus established port worktrees, preserve local work; append new tutorial enum values only. Evidence/build logs: `build/playtest-feedback/`; deployment verification: `build/curseforge-sync/`. Run common units, loader builds and tutorial/container GameTests, rendering smoke where applicable, then sync all eight allow-listed dev profiles with backups and a fresh dev version. Interrupted runs are unverified.

### 108 — Feedback implementation in place; validation in progress

- Changes applied on source and both target worktrees. Initial fishing offer is appended enum `FISHING_OFFER`, with persistent `DEFERRED` for Skip for later; existing ordinals unchanged. Starter items/practice begin only after acceptance. Tutorial debug commands now have independent `fishing`, `encyclopedia`, `companions` status/reset branches. Plain pickup advances RETURN immediately. Companion text shortened. Both backports use opaque, depth-tested companion text. 1.20.1 podium avatar restores the missing scaled vertical offset locally in its leaderboard renderer.
- Source common units: 857 tests, 1 skipped, 0 failures/errors. Both assemblies compiled. First source Fabric GameTest run: 377/378, one failure caused by an over-broad test expectation edit in pile-equip (expected REEQUIP instead of RETURN), corrected on all branches; no product failure found. Original log preserved as `build/playtest-feedback/26.1.2-validation-previous.log`.
- Pending FEEDBACK-02: corrected source Fabric/NeoForge GameTests, then both ports' common units/assemblies/GameTests through `build/playtest-feedback/validate.py` (logs `<mc>-validation.log` and exit files). Added client scenes for fishing offer acceptance/decline, plain pickup, and a stable three-avatar podium. After gates, run client scenes and inspect images, then redeploy all eight allow-listed dev profiles. No deployment of these fixes has happened yet.

### 109 — Source and 1.21.1 gates recorded; 1.20.1 / client checks pending

- Source: common 857/1 skipped/0 failed; Fabric GameTests 378/378, NeoForge 372/372 (corrects an earlier chat statement saying 378 for both). Source initial tutorial+companiontutorial scene passed all new prompt and pickup checks, then failed at the newly added leaderboard fixture because the previous scene cleared its book. Fixture now supplies its own book. Corrected podium+party run: `2612-feedback-visual.summary` = `exit=0 PASS=25 FAIL=0`; screenshot confirms source feet contact podium blocks.
- 1.21.1: common 839/1 skipped/0 failed, both assemblies pass; Fabric and NeoForge GameTests 372/372. Test harness had erroneously added the old baseline init on top of the branch's built-in fresh-universe configuration: NeoForge rejected duplicate `--universe`, logged before stopping only PID 49500; rerun without obsolete init passed. Earlier broad scene cleanup interrupted a port assembly, so the feedback harness now matches only its exact loader directory. No product failures in these harness interruptions.
- Pending FEEDBACK-03: 1.20.1 common/assemblies/Fabric+Forge GameTests (`1.20.1-validation.log`); 1.21.1 NeoForge scenes (`1211-feedback-scenes.log`), then 1.20.1 Forge scenes with the same four scenes; inspect images, redeploy fresh builds to all eight profiles, verify hashes and no duplicate jars. No feedback deployment yet.

### 110 — All version gates pass; Forge visual fixes inspected

- Validation complete: common source 857, 1.21.1 839, 1.20.1 852 tests; each has 1 skipped, 0 failures/errors. Assemblies pass for all six loader/version builds. GameTests: source Fabric 378/378 and NeoForge 372/372; both ports 372/372 on each loader. Logs `build/playtest-feedback/*validation*.log`; source GameTest assertion correction and interrupted 1.21.1 harness runs remain explicitly recorded above, not counted as successes.
- Client evidence: source fishing/companion tutorial checks pass (first combined fixture later failed for missing book, corrected); corrected source visual run 25/0. 1.21.1 NeoForge four-scene run finished `exit=0 PASS=48 FAIL=0`. 1.20.1 Forge has passed new fishing choices and complete companion tutorial including plain pickup; podium screenshot inspected, feet contact gold/iron/copper surfaces. Crouching mixed-party screenshot inspected, full Bluegill and Starfish names visible.
- Additional 1.20.1-only name fix: vanilla uses negative X name-tag scale, unlike 1.21.1. Confirmed from local mapped Minecraft jars via javap; bytecode evidence `build/playtest-feedback/vanilla-{1201,1211}-entity-renderer.txt`. Fixed X sign only on 1.20.1. Forge client compiled and ran this final change. Both backports retain opaque depth-tested text for readability.
- Pending FEEDBACK-DEPLOY: let the already-running Forge party scene finish (`1201-feedback-scenes.summary`, no cap); then execute `python -u -B build/curseforge-sync/sync_dev_profiles.py` with build/deployment log `build/playtest-feedback/deploy.log`. This builds all three version branches with one fresh dev version override, backs up replaced jars and copies to all 8 configured profiles; verify SHA-256 equality and exactly one matching Fishtastic jar in each. Release gradle.properties remain unchanged. Feedback code remains uncommitted; parity markers remain the original pinned snapshot.

### 111 — Owner feedback follow-up complete and redeployed (2026-10-18)

- Final Forge 1.20.1 four-scene run finished `exit=0 PASS=48 FAIL=0` (`1201-feedback-scenes.summary`), matching the 1.21.1 NeoForge result. Inspected the actual Forge podium and mixed-party crouching screenshots: feet meet the podium blocks and full companion names display. New prompt Play/Skip and ordinary-click RETURN progression pass on all three representative clients; all six loaders build and pass their recorded GameTest gates (checkpoint 110).
- Ran `python -u -B build/curseforge-sync/sync_dev_profiles.py`, exit 0. Fresh `2.0.3-dev.20261018162613` builds deployed to F2ALPHA, F2ALPHA [N], F2 1.20.1 Fabric/Forge, F2 1.21.1 Fabric/NeoForge, F2 26.1.2 Fabric/NeoForge. Replaced jars backed up under `D:/Curse Forge/Minecraft/_profile_backups/`. F2F Cam remains excluded by the configured allow-list.
- Deployment evidence: `build/playtest-feedback/deploy.log`, loader build logs `build/curseforge-sync/{1.20.1,1.21.1,26.1.2}-build.log`, and `build/curseforge-sync/verified.txt`. All 8 installed copies equal their built artifacts by SHA-256; exactly one matching Fishtastic jar per profile. Additional verification (`verify_deployment.py`) passed archive CRC, exact mod-version metadata, and presence of FISHING_OFFER/DEFERRED in installed TutorialStep classes; results `build/playtest-feedback/deployment-verified.json`.
- Self-test markers absent after runs. Release gradle.properties unchanged. Feedback changes and source docs remain uncommitted; original parity markers and heads remain unchanged. Owner can resume playtesting. To replay the initial fishing prompt: `/fishtastic tutorial fishing reset`, then craft or cast a copper fishing rod. Other sections use `/fishtastic tutorial encyclopedia reset` and `/fishtastic tutorial companions reset`.
- Remaining owner acceptance is the original real-play/old-save/multiplayer list from checkpoint 106, plus any new feedback from resumed playtesting. No automated work remains for this feedback request.

### 112 — Action-triggered tutorial retry pending (2026-10-18)

- Owner requests a deferred fishing tutorial be offered again on a later copper-rod cast. Pending RETRY-01: update NOT_STARTED entry guards to also accept DEFERRED on source and both port worktrees; preserve COMPLETE and in-progress states. Check other existing tutorial entry points without treating completed lessons as deferred.
- Evidence: `build/tutorial-retry/`. Extend the registered GameTest to cover reconnect persistence without a popup, non-copper casts, actual copper-rod retry, acceptance and no repeated grants/completed replay. Change the tutorial client scene to retry through actual rod use instead of an admin reset. Run both loaders' GameTests on all three versions, representative client tutorial scenes, then redeploy all 8 dev profiles via the existing sync skill with fresh version/backups/hash verification. Record results before ending; interrupted runs remain unverified.

### 113 — Retry implementation applied; source gates pass

- All three branches now offer the fishing lesson from NOT_STARTED or DEFERRED when a copper rod is cast; the crafting entry hook accepts the same states. COMPLETE and active lesson states are unchanged. Reconnects alone keep the lesson deferred. The encyclopedia lesson already starts/resumes on opening its book; no completed lesson was changed to deferred.
- Expanded the existing actual-item-use GameTest: deferred survives join/stale accepts/non-copper rods; copper casts reoffer, repeated declines can retry again, acceptance recalls the cast and grants starters once, COMPLETE never reoffers. The tutorial client fixture now casts the actual copper rod after Skip rather than resetting state through an admin hook, and asserts the renewed confirmation.
- A direct source test-file write failed with EINVAL before truncation; the subsequent validation launch was stopped and is unverified (`26.1.2-gametests-interrupted.log`). Retried all edits successfully using atomic temporary-file replacement before restarting gates.
- Source GameTests pass: Fabric 378/378 and NeoForge 372/372, `build/tutorial-retry/26.1.2-gametests.log`. Pending: both loaders on each port through `validate.py`, plus source Fabric tutorial scene (`2612-retry-tutorial.log`); then the Forge 1.20.1 tutorial scene and fresh deployment to all 8 dev profiles. No retry builds deployed yet.

### 114 — Retry server gates pass on all versions; final client pass pending

- `build/tutorial-retry/validate.py` completed exit 0. Source Fabric 378/378, source NeoForge 372/372; 1.21.1 Fabric/NeoForge 372/372 each; 1.20.1 Fabric/Forge 372/372 each. The expanded real-item-use regression passed on every loader.
- Source Fabric tutorial client scene completed `exit=0 PASS=9 FAIL=0` (`2612-retry-tutorial.summary`), including the new `tutorial.fishing_retry_on_cast` check: declined offer, cast the copper rod through the real client action, received another confirmation, accepted and completed the lesson flow.
- Pending RETRY-02: Forge 1.20.1 tutorial-only client scene via `build/tutorial-retry/run-scene.sh`, summary/log `1201-retry-tutorial.*`; then fresh build/deployment via existing `build/curseforge-sync/sync_dev_profiles.py` with output `build/tutorial-retry/deploy.log` and supplemental installed-jar verification. No retry build deployed yet.

- RETRY-02 client result: Forge tutorial scene completed `exit=0 PASS=9 FAIL=0`, including the actual copper-cast retry check. Pending RETRY-DEPLOY now starts: `python -u -B build/curseforge-sync/sync_dev_profiles.py`, output `build/tutorial-retry/deploy.log`; then run `build/playtest-feedback/verify_deployment.py` and copy its JSON report into `build/tutorial-retry/` for this follow-up.

### 115 — Action-triggered tutorial retry complete and deployed

- Natural retry is now implemented on all three versions: after Skip for later, the next actual copper-rod cast offers Play / Skip for later again. The crafting entry hook also accepts DEFERRED. Rejoining alone and other rod types do not prompt; completed/in-progress lessons stay intact. No admin reset is required. Encyclopedia teaching already resumes on its book-opening action; no completed lessons were converted into deferred ones.
- All six GameTest runs pass (source Fabric 378, all other loader/version pairs 372); real-item-use regression covers repeated skips/retries, non-copper rods, joins, stale packets, one-time starter grants and completion guards. Source Fabric and 1.20.1 Forge client tutorial scenes both finished `exit=0 PASS=9 FAIL=0`, including a real client cast after declining and acceptance of the renewed prompt. Logs and summaries under `build/tutorial-retry/`.
- Fresh builds for all six loader/version pairs passed and deployed to all 8 configured dev profiles as `2.0.3-dev.20261018164715`. Existing jars backed up under the configured `_profile_backups` root. Deployment log `build/tutorial-retry/deploy.log`; build logs `build/curseforge-sync/*-build.log`; all installed jars equal their build source by SHA-256 and only one matching Fishtastic jar remains per profile. Additional CRC/version/tutorial-class checks passed, report `build/tutorial-retry/deployment-verified.json`.
- Both client self-test markers absent. Release properties, pinned parity markers and branch heads unchanged; code/docs remain uncommitted. No automated work remains for this retry request. Owner can resume normal playtesting.

### 116 — Signed feedback commits pending

- Owner explicitly requested commits. Pending COMMIT-FEEDBACK: review and commit only the feedback/retry production changes, translations and regression scenes/tests on source, port/1.21.1 and port/1.20.1, using explicit file paths and SSH signatures. Preserve unrelated source docs/README, ideas.txt, catch-up documentation, runtime logs, scratch state and harness output. No pushes requested.
- Evidence/results: `build/commit-feedback/`. Existing validation and installed build evidence are checkpoints 110-115; no code changes since the deployed retry build. Stop on any signing failure, leave staged, never bypass signing. Record exact resulting SHAs and remaining local state.

### 117 — Commit blocked on 1Password signing; all reviewed files staged

- Source commit pre-commit hook ran successfully: common/simulation/tank-shape unit tasks passed, Fabric GameTests passed (378 complete), then the configured SSH signer failed: `error: 1Password: failed to fill whole buffer`, `fatal: failed to write commit object`. No commit was created. Signing and hooks were not bypassed. Full evidence `build/commit-feedback/26.1.2-commit.log`.
- All reviewed files are staged: source 10, 1.21.1 11, 1.20.1 12. `diff --cached --check` passes; staged names match the explicit allow-list. Heads unchanged: source 48fffdfb, 1.21.1 99adc3f3, 1.20.1 d00b52d7. Evidence `build/commit-feedback/staged.json`. Unrelated README/docs/ideas.txt/runtime logs and scratch/harness output were left out. No pushes.
- Exact next action: owner unlocks 1Password; then rerun `python -u -B build/commit-feedback/commit.py`. It creates one signed commit per branch, checks file scope and signatures, and records SHAs in `build/commit-feedback/results.json`. Stop again on signing failure; never bypass. The deployed retry builds remain valid and available for playtesting.

### 118 — Signing resumed after owner unlocked 1Password; source and 1.21.1 committed

- Owner unlocked 1Password and authorized retry. Source `26.1.2` commit `6990a6876557c840147e016902cf5f66cc1ac2ec` (10 explicit files) and `port/1.21.1` commit `3db353c45fe3cc38743a56a1ed8af874bc052eaa` (11 explicit files) created with SSH signatures. Both commit file lists match the reviewed allow-list. Hook tests passed; no hooks/signing bypass. Commit logs under `build/commit-feedback/`; source log now contains the successful retry, superseding the first failure's log (failure message retained in 117).
- Git's default verification reports missing `gpg.ssh.allowedSignersFile`. Independently verified both signatures with OpenSSH against each worktree's configured public signing key using temporary allowed-signers files and command-local Git configuration only; no persistent config changed. Evidence `build/commit-feedback/verification.json`.
- Pending: 1.20.1 signed commit through the same running `commit.py` process; then verify all three signatures/file scopes and record remaining local state. Prior unrelated docs, ideas and runtime logs remain excluded. No pushes.

### 119 — Requested commits complete on all three branches

- Signed commits created: source `26.1.2` **`6990a6876557c840147e016902cf5f66cc1ac2ec`** (10 files), `port/1.21.1` **`3db353c45fe3cc38743a56a1ed8af874bc052eaa`** (11 files), `port/1.20.1` **`a7cb1be3bba2f774e06c97d1ed675c547771b5f7`** (12 files). Title: `Improve tutorial onboarding and retry deferred lessons`; the 1.20.1 body also records podium placement and billboard orientation fixes. Exact file scopes validated by `commit.py`.
- Repository pre-commit hooks passed on every branch (common/simulation/tank-shape unit tasks plus Fabric GameTests); no hooks or signing bypassed. All three SSH signatures independently verified with OpenSSH against their configured public signing key using temporary allowed-signers files, without changing persistent Git configuration. Evidence: `build/commit-feedback/results.json`, `verification.json`, and per-branch commit logs. The default verifier's missing-allowed-signers warning does not indicate a bad signature.
- Staging areas empty after completion. Remaining tracked local edits are source `docs/README.md`, source `ideas.txt`, and each port's `common/logs/latest.log`; existing untracked catch-up docs, runtime logs and scratch/harness files remain excluded. No pushes. Deployed dev build remains `2.0.3-dev.20261018164715` with the same committed product code; committing alone needs no rebuild or redeployment. Owner can resume playtesting.


### 120 — Owner approved all playtests; 2.1.0 release preparation pending

- Owner confirmed all versions are good to go and explicitly authorized publishing all six loader/version files as 2.1.0. Changelog: Added ambient fish, companions, and interactive fish tank cosmetic decorations
- Pending RELEASE-210: bump mod_version on source and both ports; add concise release changelogs; build :fishsim:jar followed by both loader builds per branch; inspect final jar metadata and hashes. Logs and manifest: `build/release-2.1.0/`. Then publish Release files to CurseForge project 1513269, recording returned file IDs before retries. Preserve unrelated work.
- CURSEFORGE_TOKEN and Gradle curseforge_token are absent. Browser tooling initialization failed before opening a session (missing kernel assets). Upload authorization already granted; missing credentials/tool access may need owner action after artifacts are ready.

- RELEASE-210 checkpoint: all three properties are now 2.1.0, concise release entries added while preserving prior notes. Source Fabric/NeoForge build passed; 1.21.1 build running, then 1.20.1. Publishing credentials found in ignored local.properties on every branch; no credential action needed from owner. Uploads have not started.

### 121 — Six 2.1.0 release artifacts verified; publication pending

- All three branch builds passed. Six final jars validated for CRC, 2.1.0+Minecraft metadata, tutorial and bundled fishsim classes; SHA-256 and dependency metadata saved in build/release-2.1.0/artifacts.json. Older ports use remapJar outputs; source uses shadowJar.
- Pending RELEASE-210-PUBLISH: run build/release-2.1.0/publish.py for all three branches with project 1513269, release_type=release and the exact owner changelog override. Capture per-branch publish logs and returned file IDs before any retry. Existing ignored local.properties supplies credentials; no secret is printed. Then record signed version commits and actual remote status.

### 122 — All six publishing submissions succeeded; signed commits in progress

- RELEASE-210-PUBLISH completed exit 0 on source, 1.21.1 and 1.20.1. Each task configured two verified final release jars, Minecraft/loader/Java/client/server tags, project 1513269, release_type=release, and exactly the requested changelog. Per-branch publish logs and publish-results.json are under build/release-2.1.0/. No retries or duplicate uploads were made.
- The plugin reports upload IDs at INFO level, absent from the default logs; file IDs were not captured. Public page retrieval via web returned an old cached listing; direct public/API read checks returned HTTP 403. Thus upload task success is confirmed, while individual IDs, moderation state and public visibility remain unverified. Do not rerun publish.py to obtain IDs.
- Pending RELEASE-210-COMMIT: build/release-2.1.0/commit.py, one signed commit per branch of only gradle.properties and CHANGELOG.md, with all required hooks. Source 0b84e7e327e9317ebd77c6fb61d418bb33329cdf committed and SSH signature verified; 1.21.1 running, then 1.20.1. No pushes.


### 123 — 2.1.0 release submission and signed commits complete

- Six final artifacts built and verified; all three publishCurseForge tasks succeeded with two Release files each and the exact owner changelog. No retries. Signed release commits: source `0b84e7e327e9317ebd77c6fb61d418bb33329cdf`, 1.21.1 `e06344aa053db4f2886f3b0dc68dfb46241bcad0`, 1.20.1 `e7ff95df387db09b63412a39a06eab4969488330`. Only gradle.properties and CHANGELOG.md committed per branch; all required hooks and independent SSH signature checks passed. No pushes. Staging empty, unrelated edits preserved. Final artifact hashes still match the pre-upload manifest.
- Durable report: `docs/backport-catchup/release-2.1.0.md`. Raw logs/manifests/results: `build/release-2.1.0/`. Owner approval covers all-version release playtests. No implementation/build/submission work remains. Individual file IDs, moderation state and public visibility could not be independently checked; next possible external check is the live CurseForge files page. Existing dev profiles still have the previously accepted dev jars; local profile deployment was not requested for this release.


### 124 — Six-build dependency fix and 2.1.1 release preparation

Owner requested patch release 2.1.1 on all six builds and explicitly authorized CurseForge publication. Both backports have the same optional Gelatin metadata defect. Pending RELEASE-211-BUILD: fix all loader manifests and CurseForge requirements, set all branches to 2.1.1, build each branch with :fishsim:jar and both loader :build tasks, then inspect all six final jars and upload configuration. Evidence: build/release-2.1.1/ and docs/backport-catchup/release-2.1.1.md. Preserve unrelated source ambient edits and port runtime files. No parity marker changes.


### 125 — Six 2.1.1 builds and dependency metadata verified; publishing pending

All three branch builds passed; all six final jars pass CRC, version, required Gelatin >=1.0.37+Minecraft and bundled-class checks. Both uploads on every branch require CurseForge slug gelatin-ui. Pending RELEASE-211-PUBLISH: publish the six verified files to project 1513269 as Release with dependency-fix changelog, recording INFO-level file IDs and per-branch results under build/release-2.1.1/. Do not repeat successful uploads. Raw manifest: build/release-2.1.1/artifacts.json.


### 126 — All six 2.1.1 CurseForge release submissions complete

All six builds and required-dependency checks passed; all three publish tasks succeeded with two file IDs each. Six post-upload hashes match pre-upload artifacts. Every upload has requiredDependency gelatin-ui; each jar requires Gelatin >=1.0.37+Minecraft. Durable report with file IDs and links: docs/backport-catchup/release-2.1.1.md. Raw evidence: build/release-2.1.1/. No upload retries, commits, pushes, profile deployments or parity-marker changes. All requested release work is complete; public moderation/visibility remains unverified. Preserve unrelated edits and runtime files.


### 127 — 2.1.1 deployed to nine dev profiles; Forge 26.1.2 profile pending template

Deployed the verified 2.1.1 branch artifacts with `curseforge-profile-sync` (no rebuild, no version bump). Nine profiles were targeted: `F2 1.20.1 Fabric`, `F2 1.20.1 Forge`, `F2 1.21.1 Fabric`, `F2 1.21.1 NeoForge`, `F2 1.21.1 Forge` (new), `F2ALPHA`, `F2 26.1.2 Fabric`, `F2ALPHA [N]`, `F2 26.1.2 NeoForge`. Replaced `fishtastic-*-2.0.3-dev.20261018164715+*.jar` jars were backed up under `D:\Curse Forge\Minecraft\_profile_backups\20261019-140302`, `20261019-140457`, and `20261019-140503`. Post-deploy verification: every installed Fishtastic jar SHA-256 equals its branch build artifact, every profile has exactly one Fishtastic jar, and every profile has the matching Gelatin UI `1.0.37` jar.

Created `F2 1.21.1 Forge` from `Template Pack Forge 1.21.1` (MC 1.21.1, Forge 52.1.0), filed under group `Fishtastic2 Testing` with the auto-selected `rainbow_trout` icon; it contains `fishtastic-forge-2.1.1+1.21.1.jar` and `gelatinui-forge-1.0.37+1.21.1.jar`. Added both new Forge profile folders to the skill's `profiles.json`. CurseForge was not running, so folder adoption will occur on its next launch; the skill never edits the instance registry.

Pending FORGE-2612-PROFILE: the existing `Template Pack Forge 26.1.2` folder is actually MC 26.3 / Forge 66.0.5 and cannot be cloned for a 26.1.2 instance. A genuine MC 26.1.2 + Forge 64.1.0 template must be created once through the CurseForge UI. Then clone it as `F2 26.1.2 Forge` in group `Fishtastic2 Testing`, copy `gelatinui-forge-1.0.37+26.1.2.jar` from the `gelatin-ui` forge build, and run `sync_jars.py --mc 26.1.2 --repo fishtastic --skip-build`. Expected jar: `fishtastic-forge-2.1.1+26.1.2.jar` (SHA-256 `112f5b8abd3e65ae70b0a5224142e60316b52b812a6b2304857f2ca2e704f09e`). No parity markers changed.


### 128 — Forge 26.1.2 profile created; full ten-profile matrix deployed

Owner created the corrected template `Template Pack Forge 26.1.2 (1)` (verified MC 26.1.2, `forge-64.1.0`). Cloned it as `F2 26.1.2 Forge` (guid `f0cada5f-a5ee-4691-b4ba-fcc1750e31bc`, group `Fishtastic2 Testing`, auto icon `oscar`) and deployed `fishtastic-forge-2.1.1+26.1.2.jar` (SHA-256 `112f5b8abd3e65ae70b0a5224142e60316b52b812a6b2304857f2ca2e704f09e`) alongside `gelatinui-forge-1.0.37+26.1.2.jar`. Post-deploy check: the installed Fishtastic jar hash equals the build artifact, the profile has exactly those two jars, and the manifest reports 26.1.2 / forge-64.1.0 with the expected 42-key schema.

All ten allow-listed profiles are now on 2.1.1. CurseForge was not running during the work, so both new profile folders will be adopted and their addon lists populated on the next app launch. The older `Template Pack Forge 26.1.2` folder remains a 26.3 / Forge 66.0.5 instance and is unused. No parity markers changed.

### 129 — Quest log empty-icon fallback (2026-10-21)

Pending QUEST-ICON-SOURCE: replace the explicit anglerfish fallback in QuestLogScreen.objectiveConditionIcon with its empty 16x16 PageSurface; preserve species/weather/time/zone icons. Verify :common:compileJava on 26.1.2, then apply the same change and compile on port/1.21.1 (D:/GitHub/fishtastic-worktrees/port-1.21.1, HEAD 54c34836) and port/1.20.1 (D:/GitHub/fishtastic-worktrees/mc-1.20.1, HEAD b05a3352), sequentially. All three working trees were clean. Evidence: build/backport-catchup/quest-icon/{source,1211,1201}-compile.log and docs/backport-catchup/quest-icon.md. Do not advance overall parity markers for this scoped fix.

QUEST-ICON-SOURCE: Windows Gradle launcher compiled :common:compileJava successfully (source-bat-compile.log). The first sandboxed launcher stalled at java -version and was stopped; its empty log is unverified. Pending QUEST-ICON-1211: apply identical two-line change, then run gradlew.bat with JDK 21 :common:compileJava; evidence build/backport-catchup/quest-icon/1211-compile.log.

QUEST-ICON-1211 passed: :common:compileJava executed, BUILD SUCCESSFUL in 20s. Pending QUEST-ICON-1201: identical fallback/comment change and JDK 17 :common:compileJava on port/1.20.1; evidence build/backport-catchup/quest-icon/1201-compile.log. Then check all three diffs and save durable result.

QUEST-ICON-1201 initial launcher failed before compilation: historical C:/Program Files/Java/jdk-17 path does not exist. Pending corrected run using installed C:/Program Files/Java/jdk-17.0.6+10; evidence 1201-compile-jdk17.log. No source adjustment required.

QUEST-ICON-1201 second launcher also rejected the Program Files Java 17 installation before compilation. Pending run with Gradle-provisioned C:/Users/curre/.gradle/jdks/jdk-17.0.2+8; evidence 1201-compile-provisioned.log.

QUEST-ICON-1201 provisioned Java 17 ran, but current Architectury Loom 1.17.493 requires JVM 21. Pending corrected run: Gradle JDK 21 with branch's existing Java 17 compiler toolchain and --release 17; evidence 1201-compile-jdk21.log.

QUEST-ICON complete: all three :common:compileJava tasks executed and passed (source 16s, 1.21.1 20s, 1.20.1 16s). Source and both ports have identical fallback/comment changes; source inspection confirms empty PageSurface renders no commands. Port checklists updated. Durable report: docs/backport-catchup/quest-icon.md. No in-game visual test, commits, pushes, version changes or overall marker advancement. No remaining operation for this scoped request.

Pending QUEST-ICON-COMMIT (owner requested commit all): commit both port source/checklist changes, then source code/report/progress with required hooks and signing enabled. Run hooks with JAVA_HOME=C:/Program Files/Java/jdk-21 (source Gradle retains its project Java 25 pin). Evidence: build/backport-catchup/quest-icon/{1211,1201,source}-commit.log. Record exact commit IDs and signature/working-tree checks; no pushes.

QUEST-ICON-COMMIT checkpoint: 1.21.1 hook unit checks passed and Fabric GameTests passed 372/372; signing failed with 1Password 'failed to fill whole buffer', exit 128, no commit created. Source/checklist remain staged on that target. User asked to unlock signer; signing will not be bypassed. Pending: run remaining commits with hooks, then retry 1.21.1 once signer is available. Evidence 1211-commit.log.

QUEST-ICON-COMMIT blocked on signing: all three required pre-commit unit checks and Fabric GameTests passed (source 378/378; both ports 372/372). All three signed commit attempts exited 128 before creating a commit: ports '1Password: failed to fill whole buffer'; source '1Password: agent returned an error'. No hooks or signing bypassed. User has been asked to unlock 1Password; next operation is retry all three signed commits with hooks once signer is ready. Logs: build/backport-catchup/quest-icon/{source,1211,1201}-commit.log. Changes remain staged; heads unchanged. No pushes.

Pending QUEST-ICON-COMMIT-RETRY (owner requested retry): retry signed commits on both port branches then source with existing hooks enabled and JAVA_HOME JDK 21. Evidence build/backport-catchup/quest-icon/{1211,1201,source}-commit-retry.log. Preserve prior successful test and failed-signing evidence; verify resulting signatures and worktree status. No pushes.

QUEST-ICON-COMMIT-RETRY: both port signed commits succeeded with required hooks; Fabric GameTests 372/372 each. 1.21.1 aa60aaa9cb22fea4553dd20c907824f4c95f168b; 1.20.1 3bd39c5347c396100c702ca2784a45244af397d4. Both working trees clean. Pending source signed commit with final report/tracker staged; evidence source-commit-retry.log. Independent verification uses per-command allowedSignersFile under build/backport-catchup/quest-icon (no global config change).

QUEST-ICON-COMMIT-RETRY complete: source signed commit succeeded; required hook unit checks and fresh Fabric GameTests 378/378 passed. Both ports passed 372/372 and their independent SSH verification passed. Source signature also independently verified. Final source commit is the commit containing this checkpoint (amended to record completion); both port IDs above remain final. Pending final operation: signed amendment with hooks, then verify signature and clean status across all three trees; evidence source-commit-final.log. No implementation work remains and no pushes performed.

### 130 — Quest log small-window resize backport pending (2026-10-22)

Pending BACKPORT-QUEST-RESIZE: apply source commit `fa103289` (`Fix quest log overflow in small windows`) to `port/1.21.1` at `D:/GitHub/fishtastic-worktrees/port-1.21.1` and `port/1.20.1` at `D:/GitHub/fishtastic-worktrees/mc-1.20.1`, preserving the existing quest-icon commits and unrelated worktree state. Verify each target with `:common:compileJava`, then run the target quest-focused tests/loader compile gate as practical. Evidence paths: `build/backport-catchup/quest-log-resize/{1211,1201}-compile.log` and durable result `docs/backport-catchup/quest-log-resize.md`. Do not advance overall parity markers or alter release/version metadata. If interrupted, inspect both target statuses and commit heads before resuming; treat any incomplete operation as unverified.

BACKPORT-QUEST-RESIZE complete: clean cherry-picks created `port/1.21.1` commit `3104a8a6888779f2c8e3478c73d20aedac6a0350` and `port/1.20.1` commit `0f780628dcee6d109f7336cb8fc557ee9bb719cc`. Both `:common:compileJava` runs passed; both quest-focused common-test plus Fabric/NeoForge-or-Forge compile gates passed. Worktrees are clean. Evidence: `build/backport-catchup/quest-log-resize/{1211,1201}-{compile,gate}.log`; report: `docs/backport-catchup/quest-log-resize.md`. Parity markers and version/release metadata unchanged; no pushes. Next action: none for this scoped backport.

### 131 — Port parity markers advanced through quest-log resize (2026-10-22)

Both branch-local parity ledgers now advance from `bc73a5fc` to source commit `fa103289`. The applicable post-marker source changes are accounted for on both ports: `a00a1464` (Gelatin/version bump), `b3bab723` (empty quest-icon fallback), and `fa103289` (small-window quest-log resize). The corresponding target commits are 1.21.1 `54c34836`, `aa60aaa9`, `3104a8a6` and 1.20.1 `b05a3352`, `3bd39c53`, `0f780628`.

Source commit `453352a1` is a 26.1.2 Forge-only test-wrapper rename and is not applicable to either port; source commit `276f3498` is docs-only and is excluded from the marker. The marker updates were committed as signed commits `9230042e` (1.21.1) and `68e2c77b` (1.20.1). Their hooks passed unit tests and Fabric GameTests 372/372; the 1.21.1 NeoForge and 1.20.1 Forge game-test stages were skipped by the repository hook because that loader stage can hang. Quest-focused compile/gate evidence remains under `build/backport-catchup/quest-log-resize/`. No source ledger snapshot or release/version metadata changed; no pushes.
