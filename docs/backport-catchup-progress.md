# Backport parity progress

The original pinned catch-up backlog is complete on `port/1.21.1` and `port/1.20.1`.

The free-daily-reroll and coelacanth backports are implemented on source and both port branches.
All eight client launch smokes are recorded, including the merged 1.21.1 Forge module.
The 2026-10-23 docs/tooling catch-up is complete; the
isolated solo render check (`scripts/run_render_checks.py fabric --solo`) passed 14/14 on each
port (evidence under each worktree's `build/verification/two-client/fabric/`).

## Current parity state

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
- Parity markers: source `fa103289`; 1.21.1 commit `9230042e`; 1.20.1 commit `68e2c77b`.
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
