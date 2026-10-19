# Forge support implementation progress

Dedicated progress ledger for `docs/forge-support-implementation-plan.md`. Kept separate from the
backport catch-up handoff; existing parity markers are untouched.

Overall state: **F00/F01 complete; F02 proven on 1.21.1, dev-blocked on 26.1.2.** Both lines are
pinned to Loom `1.17.493` on Gradle `9.5.0` and their existing-loader tests/builds pass. The 1.21.1
`forge` module builds, launches its dev server to "Done!", and packages a verified jar (mods.toml +
AT + mixins + transformed @ExpectPlatform). The 26.1.2 dev launch remains blocked on Loom injecting
Mixin 0.8.7 (no JAVA_25 support); its build/package path is verified. Evidence lives under
`build/forge-support/` (git-ignored transient build output).

## F00 Baseline — COMPLETE (2026-10-18)

Captured four source trees' branches, HEADs, Gradle/JDK versions, plugin pins, dependency
coordinates, and complete dirty inventories into
`build/forge-support/baseline/manifest.json`. Evidence level: LOCAL INSPECTED.

- fishtastic 26.1.2 @ `0b84e7e` (branch `26.1.2`) — Gradle 9.2.1, Java 25, loom-no-remap 1.14.
- fishtastic 1.21.1 @ `e06344a` (branch `port/1.21.1`) — Gradle 9.5.0, Java 21, Loom 1.17-SNAPSHOT.
- gelatin-ui 26.1.2 @ `dd9337e` (branch `26.1.2`) — Gradle 9.2.1, Java 25, loom-no-remap 1.14.
- gelatin-ui 1.21.1 @ `fedf9d2` (branch `mc/1.21.1`) — Gradle 8.14, Java 21, Loom 1.11-SNAPSHOT.

Local cache already contains Forge `26.1.2-64.1.0` and `1.21.1-52.1.2`, plus Loom 1.17-SNAPSHOT
marker `c819ec71`. Gradle daemon runs unsandboxed (Gradle 9.2.1 / Java 25 verified via
`.\gradlew.bat --version`); the sandbox blocks the daemon, so build commands run escalated.

## F01 Toolchain — IN PROGRESS

Goal per line: resolve an immutable Loom 1.17 artifact (record checksum + upstream source commit),
upgrade the 26.1.2 loom-no-remap pins from 1.14 and Gelatin 1.21.1 from Loom 1.11/Architectury 3.4,
then reproduce existing Fabric/NeoForge builds before any Forge module is added.

### 26.1.2 line — COMPLETE (2026-10-18)

- Pinned `dev.architectury.loom-no-remap` `1.14-SNAPSHOT` -> **`1.17.493`** in fishtastic and
  gelatin-ui. `1.17.493` is the immutable artifact the previous `1.17-SNAPSHOT` marker resolved to
  (marker POM SHA-256 `07D62DFB...EDDF08C7`), not a moving version. Artifact SHA-256
  `B88261D50A940C6785FABE83C3E70626E7ECDA8CD68EDA08312E6B6F5A12E0E3`; POM SHA-256
  `7D8F3360FE0BCA12235A059DFF30D20A1F8FE2C96290B703087A4E32E5E83780`. Upstream tag `1.17` is
  commit `5b00f1e37558b4a063f0b0c4c8c8613e15b6db68`. Exact-version plugin markers for both
  `dev.architectury.loom` and `dev.architectury.loom-no-remap` return HTTP 200 from the
  Architectury Maven. Full record: `build/forge-support/26.1.2/F01/loom-pin.json`.
- Both 26.1.2 wrappers moved Gradle `9.2.1` -> `9.5.0`. The first fishtastic attempt with the new
  pin on 9.2.1 failed while applying Loom with
  `NoSuchMethodError: Configuration.extendsFrom(Provider[])` at `build.gradle:43`; this is a
  toolchain/API mismatch (the 1.17 line is already used with Gradle 9.5.0 on `port/1.21.1`), not a
  product regression. No product code changed.
- fishtastic gate: `:common:test :fishsim:test :tools:tank-shape-gen:test :fabric:build
  :neoforge:build` exited 0, `BUILD SUCCESSFUL in 5m 53s`, 37/37 tasks executed. Common 857 tests
  (1 skipped), fishsim 563 (63 skipped), tank-shape 21,955; zero failures/errors. Final Fabric and
  NeoForge `2.1.1+26.1.2` jars were produced and contain their loader metadata, entrypoint and
  bundled fishsim classes.
- Runtime smoke: `:fabric:runGametest :neoforge:runGametest` exited 0 in 1m. Fabric completed
  **378/378** GameTests (12.13 s) and NeoForge **372/372** (5.649 s), matching the pre-upgrade
  counts. The Loom 1.17/Gradle 9.5 dev runtime still boots and discovers/executes both loaders'
  suites.
- gelatin-ui gate: `:fabric:build :neoforge:build` exited 0, `BUILD SUCCESSFUL in 20s`, 22/22 tasks
  executed; both `1.0.37+26.1.2` final jars were produced. Exact commands, test counts and artifact
  hashes: `build/forge-support/26.1.2/F01/results.json`; raw logs in `.../F01/logs/`.
- Not done here: no Forge module, no development or installed-artifact launch (F02), no publication,
  deployment, commit or push. Pre-existing dirty files were preserved.
- Next for this line: **F02** minimal Forge module probe (development client/server plus a clean
  installed final jar), after F01's 1.21.1 line is also green.

### 1.21.1 line — COMPLETE (2026-10-18)

- Pinned `dev.architectury.loom` -> **`1.17.493`** in fishtastic 1.21.1 and gelatin-ui 1.21.1.
  Gelatin wrapper moved Gradle `8.14` -> `9.5.0` (required by Loom 1.17's Gradle API).
- Gelatin `architectury-plugin` `3.4` -> **`3.5.170`**: 3.4.164 cannot load Loom 1.17
  (`NoClassDefFoundError: net/fabricmc/loom/configuration/ide/RunConfig` at `fabric/build.gradle:7`);
  3.5.170 is the immutable build the `3.5-SNAPSHOT` marker resolves to. This answers the plan's open
  question — Gelatin 1.21.1 does need the Architectury upgrade alongside Loom.
- Regression caught and fixed: Loom 1.17 disables the legacy mixin AP by default, silently dropping
  `gelatinui-refmap.json` from the 1.21.1 jars. Added `loom.mixin.useLegacyMixinAp = true` to
  Gelatin; refmap restored and mapping-identical to the published jar (173/173 entries). Fishtastic
  1.21.1 already had `useLegacyMixinAp = true`.
- fishtastic fresh gates: common 839 (1 skipped), fishsim 563 (63 skipped), tank-shape 21,955; zero
  failures. Clean Fabric+NeoForge rebuild hashes exactly to the released `2.1.1+1.21.1` jars
  (`E4A4F330…FFBB22`, `B9041150…F5ACAD`). GameTests: Fabric 372/372, NeoForge 372/372.
- gelatin-ui Fabric+NeoForge builds pass with refmap present.
- Caveat recorded: rebuilt Gelatin jars were not published to mavenLocal, so Fishtastic consumed the
  previously published `1.0.37+1.21.1` during these builds. Local exact-version publication is G03.
- Evidence: `build/forge-support/1.21.1/F01/{operation,source-and-dependencies,commands-and-exits}.{md,json}`,
  logs under `.../F01/logs/`.
- Not done here: no Forge module, no commit/push/publication. Pre-existing dirty files preserved.
- Next for both lines: **F02** minimal Forge module probe (dev client/server + clean installed final
  jar; one platform call, widened access and Mixin behavior).

Stop conditions: any existing-loader regression (Fabric or NeoForge) after a Loom upgrade stops the
line before Forge work; a reproducible Loom defect is classified (packaging / dependency / JDK /
Loom) before any fallback is considered. No broad Forge adapters are written until F02 passes.

### F08 client completion / 1.21.1 — IN PROGRESS (2026-10-19)

- `FishtasticForgeClient` now mirrors the 1.21.1 NeoForge client inventory: all S2C handlers,
  reload listeners, model loader, renderers, 11 particles, shaders, 5 keys, 2 tooltip factories,
  4 menu screens, HUD layers, world-render stages, join/leave/tag cache resets (including
  `CompanionPartyClientCache.reset()`), client commands and the rendering self-test.
- Added the missing custom `builtin/entity` item path. Forge 52 has no client-extension registry
  event, so a client-only `ItemRenderPropertiesMixin` supplies
  `FishtasticForgeItemRenderer.EXTENSIONS` for the pile of fish, structure cosmetics and treasure
  chest. `:forge:build` passes and the final jar contains the mixin, refmaps and config.
- Migrated the dev-only `RenderSelfTest` sends to `FishtasticTransport`, and corrected
  `OnDatapackSyncEvent` to broadcast quest/companion data to all players on `/reload` while keeping
  tutorial join handling per-player.
- Evidence: `build/forge-support/1.21.1/F08/operation.md`, `item-renderer-build.log`,
  `f03-f04-fix-build.log`; rebuilt installed jar SHA-256
  `340C506F8817F859CB1EB2636B24DD230C165D1E50690E932682AEA4C3F7E4E2` boots the Forge 52.1.2
  server to `Done (3.952s)!`.
- Pending: installed-client visual/interaction acceptance (tank, items, world fish/companions,
  tutorial/screens/particles/keys) and the Forge client mixin/AT runtime proof. These belong to F10.

### F09 harness integration / 1.21.1 — IN PROGRESS (2026-10-19)

Pending **F09-1211-01**: wire the shared gametest harness into `:forge` - Forge
`@GameTestHolder(namespace = "fishtastic")` + `@GameTestDontPrefix` on `FishtasticGameTests` with
compile-only stubs for the Fabric/NeoForge testmod compiles, a `testmod` source set sourcing
`common/src/testmod`, the testmod in Loom's `main` mod group, and a `runGametest` run using Forge's
`gameTestServer` template plus `gradle/gametest-world.gradle`. Then run `:forge:runGametest` in two
fresh universes, pin the discovered inventory against NeoForge/Fabric, and keep both existing
loaders green.

Forge 52 discovery facts established from the 52.1.2 bytecode: `RegisterGameTestsEvent.register`
uses `getDeclaredMethods()` only; `@GameTestHolder.namespace` qualifies the template while `value`
prefixes test/batch names; `GameTestDontPrefix` suppresses that prefix; and
`forge.enabledGameTestNamespaces` filters on the derived batch name, so the unprefixed shared
harness must not set it.

Evidence: `build/forge-support/1.21.1/F09/`. Interrupted runs are unverified; no elapsed-time kill
cap. Pre-existing edits are preserved.

## Package status

### 26.1.2 Forge client/transport checkpoint (2026-10-19)

- **F04 fixed and verified.** `ForgePacketRegistrar` now marks each payload handled after invoking
  the handler; without it Forge's `NetworkInstance#dispatch` returned false and vanilla logged the
  payload as unknown. Join sync and two-client companion scenes prove the channel both ways, with
  zero `Unknown custom packet payload` lines.
- **F07/F08 tick gap fixed.** `FishtasticForgeClient` only ticked the self-test. It now runs the full
  shared per-tick work (`ClientAmbientFish`, `ClientTickHandler`, `ClientTankFlocks`,
  `ClientCompanions`, tutorials, key binds, quest notifications, gizmos), plus
  `GelatinScreensCompat.init()`, reload listeners and the cosmetic tooltip hint.
- Evidence: `selftest-companionworld-2.log` (29/29), `selftest-ambientparity-1.log`,
  `selftest-core-2.log` (41/41), `selftest-ui-1/2.log`, `F10/{server,client-a,client-b}.log`.
- Artifact: `fishtastic-forge-2.1.1+26.1.2.jar`, SHA-256
  `6FFC00FA09147F9CD78C3A0B467E6B848C5E775ECB40CE587DA4720E692F25A5`, no dev-only entries.
- **F09 unblocked the same day.** The sibling Potions Plus mod (same MC/Forge line) already used a
  `RegistryLoadTask` mixin to inject tests while `TEST_INSTANCE` is writable; adopting it gives
  378/378 twice in fresh universes (`F09/gametest-5.log`, `gametest-6.log`).
- **Installed client verified (join + bidirectional payloads).** The Forge installer produced a
  clean client; a production-only `LocalPlayer` dist crash in `ForgePacketContext#getPlayer` was
  found and fixed (client fallback now behind `Context#isClientSide()` + a client-installed
  supplier). Evidence: `F10/operation.md`, `installed-client/client-join-3.log`.
- **Ambient and companion matrices pass.** Ambient: 111 PASS/5 FAIL from cross-scene state
  (spectator mode left by `ambientlifecycle`, cumulative expiry counter); both fixed and re-run
  30/30. Companions: `GhostConnectionImpl` was missing for Forge; added as a documented no-op and
  the 66-check companion batch now passes.
- **1.21.1 installed client verified.** Installing Forge 52.1.2 and joining a dedicated server
  exposed two production-only defects: an unguarded `RenderSelfTest` reference and the same
  `LocalPlayer` dist crash. Both fixed in the worktree; the client now joins and drives the
  ambient-context exchange on the shipped jar (`F10/installed-client.md`).
- **26.1.2 installed two-identity join verified** (`client-two-a/b.log`), and the 1.21.1 Forge
  GameTests still pass 372/372 after the production fixes (`F09/gametest-regression.log`).
- **Installed restart persistence verified.** RCON-driven smoke set a token balance to 123, saved,
  stopped, restarted, rejoined and read back 124 after granting one token
  (`F10/operation.md`, `installed-persistence-*.log`). `scripts/rcon.ps1` is the helper.
- **Installed `/reload` verified**: `Reloading!` with no reload errors, and
  `fishtastic fishprofile fishtastic:bluegill` still resolves the datapack registry afterwards.
- **Installed-layout scene matrix passes via a smoke build.** `:forge:build -PincludeSelftest` plus
  `-Dfishtastic.selftest=true` runs the real scenes inside the installed client:
  `companionworld` 29/29 and `tank/items/hud/leaderboard` 19/19. The release jar still has zero
  selftest entries; the harness is resolved reflectively.
- **1.21.1 installed-layout scenes pass too**: `tank/items/hud/leaderboard` 22/22 in the installed
  client with its own smoke build, plus `companionworld` 29/29; release jar verified to contain no
  selftest entries.
- **Wrong-Minecraft dependency now rejected cleanly.** The 1.21.1 Gelatin jar was accepted by
  Maven ordering and crashed in bootstrap; Gelatin and Fishtastic metadata now bound Minecraft
  (`[1.21.1,1.21.2)` / `[26.1.2,26.2)`), and the loader reports
  `Mod gelatinui requires minecraft 1.21.1 or above, and below 1.21.2`.
- Next operation: installed gameplay interaction (fishing, tank placement, companion menu) via a
  scripted testmod or client automation; companion-party persistence on the installed jar.

| Package / line | State | Evidence | Next operation |
|---|---|---|---|
| F00 / both | COMPLETE | `build/forge-support/baseline/manifest.json` | — |
| F01 / 26.1.2 | COMPLETE | `build/forge-support/26.1.2/F01/{loom-pin,results}.json`, `logs/` | — (F02 probe next) |
| F01 / 1.21.1 | COMPLETE | `build/forge-support/1.21.1/F01/{operation,source-and-dependencies,commands-and-exits}.{md,json}`, `logs/` | — |
| F02 / 26.1.2 | PASS | `build/forge-support/26.1.2/F02/operation.md`, `logs/forge-runserver-modfiles-3.log`, `logs/forge-runclient-modfiles.log` | Forge-only JAVA_21 mixin config; dev server + client pass all probe checks |
| G01 / 26.1.2 | PASS | `build/forge-support/26.1.2/G/operation.md` | Gelatin Forge 26.1.2 builds, loads in dev server/client, menu lifecycle fires |
| F03 / 26.1.2 | PASS | `build/forge-support/26.1.2/F03/operation.md`, `installed-server/run-f03-2.log` | Nine static + seven datapack registries, config, commands, tick; installed server Done |
| F04 / 26.1.2 | PASS | `build/forge-support/26.1.2/F04/operation.md`, `selftest-companionworld-2.log` | Inbound payloads consumed by the Forge channel (handled flag); live join sync verified |
| F05 / 26.1.2 | PARTIAL | `build/forge-support/26.1.2/F05/operation.md` | SavedData/sync/cache ported and compiled; lifecycle matrix pending F08 |
| F02 / 1.21.1 | PASS | `build/forge-support/1.21.1/F02/operation.md`, `installed-server/` | Dev + installed production jar pass all four probe checks |
| G01 / 1.21.1 | PASS | `build/forge-support/1.21.1/G01/operation.md`, `installed-server/` | Build + lifecycle + production transform + installed-server registration verified |
| G02 / 1.21.1 | PARTIAL | `build/forge-support/1.21.1/G02/operation.md` | Bootstrap fix + server mixin load verified; client UI/tooltip checks pending |
| G03 / 1.21.1 | PASS | `build/forge-support/1.21.1/G03/operation.md`, `installed-server/` | Local Gelatin 1.0.38 integration verified in installed server |
| F03 / 1.21.1 | PASS | `build/forge-support/1.21.1/F03/operation.md`, `installed-server/` | Registered 9 static + 7 datapack registries, config, commands, tick on installed server |
| F04 / 1.21.1 | PARTIAL | `build/forge-support/1.21.1/F04/operation.md` | Channel + seam + send sites migrated; live client round-trip pending |
| F05 / 1.21.1 | PARTIAL | `build/forge-support/1.21.1/F05/operation.md` | SavedData + sync + join broadcast implemented; client lifecycle matrix pending |
| F06 / 1.21.1 | PARTIAL | `build/forge-support/1.21.1/F06/operation.md` | Tank model/resources/loader implemented + server-isolated; client visual proof pending |
| F07 / 1.21.1 | PARTIAL | `build/forge-support/1.21.1/F07/operation.md` | World-render hook implemented + server-isolated; client visuals pending |
| F08 / 1.21.1 | PARTIAL | `build/forge-support/1.21.1/F08/operation.md`, `F08/*.log` | Client entrypoint + item renderer implemented; installed-client acceptance pending |
| F09 / 1.21.1 | PASS | `build/forge-support/1.21.1/F09/operation.md`, `gametest-10.log`, `gametest-11.log` | Forge testmod/runGametest: 372/372 twice; testmod excluded from final jar |
| F08 / 26.1.2 | PLANNED | — | Blocked behind F02 dev launch and 26.1.2 render prototypes |
| F10 / 1.21.1 | PARTIAL | `build/forge-support/1.21.1/F10/operation.md`, `installed-client.md`, `selftest-core-10.log`, `selftest-hud-4.log` | Dev scenes + two-client pass; installed client joins with two production-only crashes fixed |
| F06 / 26.1.2 | PARTIAL | `build/forge-support/26.1.2/F06/operation.md`, `screen/` | Placed tanks render frame/glass/sand/water + fish/cosmetics; full shape fragments and tinted cosmetics pending |
| F07 / 26.1.2 | PARTIAL | `build/forge-support/26.1.2/F07/operation.md`, `selftest-companionworld-2.log`, `selftest-ambientparity-1.log` | Tank, ambient world fish and companions render; first/third person + transition checks pending |
| F08 / 26.1.2 | PARTIAL | `build/forge-support/26.1.2/F08/operation.md`, `selftest-core-2.log`, `selftest-ui-2.log`, `selftest-companion-2.log` | 41 core + 40 UI + 66 companion checks pass; perf/visual opt-ins pending |
| F09 / 26.1.2 | PASS | `build/forge-support/26.1.2/F09/operation.md`, `gametest-5.log`, `gametest-6.log` | 378/378 twice in fresh universes via `RegistryLoadTaskMixin` + `gatherTests` |
| F10 / 26.1.2 | PARTIAL | `build/forge-support/26.1.2/F10/operation.md`, `client-a.log`, `client-b.log`, `installed-client/client-two-*.log` | Core 51/51, UI 40/40, companions 66/66, ambient matrix, two-client 8/8, two installed identities joined; gameplay interaction/persistence pending |
| F11 / 26.1.2 | PARTIAL | `build/forge-support/26.1.2/F11/operation.md` | Artifact/metadata verified; Gelatin Forge is absent from Maven Central (404), so remote resolution is blocked |
| F10-F11 / both | PLANNED | — | Remaining installed-jar matrix items and release readiness |

## Branch/commit checkpoint (2026-10-19)

Authorized operation: preserve the current Forge implementations on `codex/forge-26.1.2`
(parent `26.1.2` at `0b84e7e327e9317ebd77c6fb61d418bb33329cdf`) and
`codex/forge-1.21.1` (parent `port/1.21.1` at
`e06344aa053db4f2886f3b0dc68dfb46241bcad0`). Stage Forge modules, transport/persistence
adapters, rendering/test harness fixes, toolchain changes and Forge documentation only.
Preserve pre-existing release, ambient filming, backport and runtime edits unstaged.
Evidence destination: `build/forge-support/commit-checkpoint/`; verify staged whitespace,
file inventory, commit parents and remaining dirty state. No new runtime acceptance is claimed.
Result: all four implementation commits created locally; commit parents verified against the
original main port branches, with no staged files remaining. User explicitly included Gelatin UI.

| Repository / version | Branch | Implementation commit | Parent branch |
|---|---|---|---|
| Fishtastic / 26.1.2 | `codex/forge-26.1.2` | `643411a4fc0fc3e37786f9a9f0ec116280921b5a` | `26.1.2` |
| Fishtastic / 1.21.1 | `codex/forge-1.21.1` | `f3635876ced2c9c4eed125a89aebec1cd5bd1903` | `port/1.21.1` |
| Gelatin UI / 26.1.2 | `codex/forge-26.1.2` | `8b9d4edb4950fef6814b1f9e939395e13148e7be` | `26.1.2` |
| Gelatin UI / 1.21.1 | `codex/forge-1.21.1` | `8c3ccaf9a04213191952aaa91ce4a02d190a5a26` | `mc/1.21.1` |

Fishtastic pre-commit unit-test tasks and Fabric GameTests passed on both lines (commit exit 0).
Gelatin staged whitespace checks passed; its prior runtime acceptance remains documented above.
Tests ran against the existing working trees, including their pre-existing release edits; this
checkpoint does not claim a fresh clean-checkout build. Source changes were not edited here.
Inventory/parents/remaining dirty state: `build/forge-support/commit-checkpoint/results.json`.
26.1.2 hook output: `build/forge-support/commit-checkpoint/commit-26.1.2.log`;
1.21.1 Fabric report: its `fabric/build/junit.xml`. Both original main port branches are unchanged.
Unrelated edits and runtime output remain in place. No push or publication performed.
Next action: continue the installed gameplay/companion persistence acceptance already listed
above, then finish F10/F11; these commits are implementation checkpoints, not release approval.

## Forge visual regressions — started 2026-10-19

User reports Forge 1.21.1 ambient fish moving with camera rotation, stepped companion movement,
and discovery-celebration rays cutting through notification banners. Inspect both Forge lines.
Pending operation: reproduce with isolated Forge client self-test scenes, correct world-pass
matrix/timing and celebration depth/blend boundaries, then rebuild and inspect scene captures.
Evidence: `build/forge-support/1.21.1/render-regressions/` and corresponding 26.1.2 folder.
LOCAL INSPECTED: Forge 52.1.2 AFTER_ENTITIES dispatch passes the projection matrix twice;
its event partial tick maps to DeltaTracker.getRealtimeDeltaTicks, not interpolation alpha.
26.1.2 uses a vanilla submitEntities mixin with an identity stack and game partial tick instead.
Preserve unrelated release/ambient/backport edits. Do not change parity or acceptance markers.

### Visual-regression checkpoint result (2026-10-19)

Fixed on the 1.21.1 Forge branch:
- World rendering starts with an identity PoseStack, matching vanilla entities; the broken Forge
  AFTER_ENTITIES event projection matrix is no longer multiplied into camera-relative geometry.
- Ambient/companion rendering uses `Minecraft.getTimer().getGameTimeDeltaPartialTick(false)`;
  Forge's event frame-duration value is no longer used as the interpolation alpha.
- Discovery/hero glow and ray blits flush pending GUI geometry and disable depth writes, restoring
  depth/blend/tint state in a finally block so translucent rays cannot cut out later banners.

Validation: Forge release build succeeds and excludes self-test classes. Before/after captures
reproduce then remove the banner cutout. Ambient parity passes 14/14. The combined fixture run
reported five companion reaction failures; a fresh isolated companion-only world at 60 FPS passes
29/29 checks (including shader readiness), exit 0. Keep the failed combined result as evidence of
scene isolation sensitivity rather than counting it as a passing acceptance run.

26.1.2's existing Forge world-submit mixin already uses vanilla's identity pose and game partial
 tick. Its isolated celebration overlap capture shows an intact banner (client exit 0). No
production render fix was needed there for these identified causes. Both branches gain the opt-in
`celebrationoverlap` screenshot scene; 1.21.1 also ignores the harness runtime directories.

Evidence: `build/forge-support/1.21.1/render-regressions/{operation.md,results.json,before/,after/,
after.log,companion-fresh.log}` and `build/forge-support/26.1.2/render-regressions/`.
Source fixes and harness changes are uncommitted. The rebuilt 1.21.1 release jar is
`D:/GitHub/fishtastic-worktrees/mc-1.21.1/forge/build/libs/fishtastic-forge-2.1.1+1.21.1.jar`.
Next acceptance step: installed-jar playtest with camera rotation/translation, smooth companion
motion and discovery rays overlapping banners; broader F10/F11 work remains pending.

Deployment checkpoint: redeployed the validated fixed `fishtastic-forge-2.1.1+1.21.1.jar` to
CurseForge profile `F2 1.21.1 Forge` on 2026-10-19. Source SHA-256 matched the tested artifact in
`render-regressions/results.json`; deployed SHA-256 matches source and exactly one Fishtastic jar
is present. Previous same-filename jar backed up under
`D:/Curse Forge/Minecraft/_profile_backups/20261019-193821/F2 1.21.1 Forge/`.
Installed playtest remains the next acceptance step.

Installed playtest confirmation (2026-10-19): user reports the redeployed 1.21.1 build fixes the
reported visual problems. User also confirms the celebration/banner transparency defect on Forge
1.20.1; that narrow fix is tracked under `build/render-regressions/1.20.1/`, without changing any
backport parity markers or the new-Forge package gates.

## Verified fixes and session commits (2026-10-19)

User confirmed the installed fixes on both Forge 1.21.1 and Forge 1.20.1. The 1.20.1 fix also
passed its isolated celebration/banner screenshot run and was deployed with backup/hash checks;
evidence is in `build/render-regressions/1.20.1/`.

Authorized pending operation: commit remaining session changes only: Fishtastic 1.21.1 world
transform/interpolation and celebration depth fixes; Fishtastic 1.20.1 celebration depth fix;
celebrationoverlap scenes on all three lines, harness ignore files, this progress ledger; Gelatin
UI 1.0.38 version bumps on 26.1.2 and 1.20.1. Gelatin 1.21.1 is already committed and clean.
Preserve pre-session ambient-filming, backport, notes, runtime and other release edits unstaged. The user additionally authorized recording the already-released Fishtastic 2.1.1 version on all three lines.
Run the repository hooks and verify committed inventories and residual dirty state.
Evidence destination: `build/session-commits/`. The session commit set includes:

| Repository / line | Branch | Rendering or dependency commit |
|---|---|---|
| Fishtastic 1.21.1 | `codex/forge-1.21.1` | `7c9a20b53315cb12ed2a788c4a0588f451982881` |
| Fishtastic 1.20.1 | `port/1.20.1` | `1cff969fe5d1f6a58e76991668a33b54dce50b28` |
| Gelatin UI 26.1.2 | `codex/forge-26.1.2` | `1186e06d3abb14142e561d4f470730556f328dfc` |
| Gelatin UI 1.20.1 | `mc/1.20.1` | `b7d72f8c46527ae386a39fed5bf0817671976b8c` |

This 26.1.2 checkpoint commit adds its celebrationoverlap scene, the progress record and the
released 2.1.1 version. Additional version-only commits record 2.1.1 on both older Fishtastic lines.
The existing Gelatin 1.21.1 Forge commit already includes 1.0.38. Final branch tips, committed file
inventories, hook results and remaining dirty state are saved in `build/session-commits/results.json`.
Required hooks run for every Fishtastic commit. Installed fixes were confirmed by the user on both
older Forge lines; Gelatin's eight loader/version artifacts were built and deployed earlier in the
session. Older unrelated changes remain outside the session commits. No push is part of this request.
Next Forge implementation operation remains the broader pending F10/F11 acceptance work.

## Forge-only 2.1.1 CurseForge publication (2026-10-19)

Owner explicitly authorized publishing the two new Fishtastic Forge builds under
version 2.1.1. Pending operation: build `:fishsim:jar :forge:build` on 26.1.2 and
1.21.1, inspect the final distributable jars and required Gelatin metadata, then
submit exactly one Forge Release per line to CurseForge project 1513269. Existing
Fabric/NeoForge files must not be uploaded again. The matching Gelatin 1.0.38
Forge uploads already succeeded (26.1.2 file 9259810; 1.21.1 file 9259814).
Use a dedicated Forge-only publishing task and record configuration, artifact
hashes, returned file IDs and results under `build/forge-release-2.1.1/`.
Broader acceptance/package gate and backport parity markers are unchanged.

Publication result: **COMPLETE**. Both Forge production builds and both dedicated
Forge-only publish tasks passed. Exactly two new Release files were submitted,
with Forge, matching Minecraft, Client/Server and Java 25/21 tags. Both upload
configurations require CurseForge project `gelatin-ui`. Final jars passed CRC,
version, loader resource, required dependency, bytecode, bundled simulation and
self-test exclusion checks. Post-upload local hashes match the verified inputs.

| Minecraft | CurseForge release | SHA-256 | Source revision |
| --- | --- | --- | --- |
| 26.1.2 | [9260058](https://www.curseforge.com/minecraft/mc-mods/fishtastic/files/9260058) | `8fc5253d072d417efc66b589b9119f949bf5bb51844f9ed3db193097b9e9ef98` | `d0f9f03cb0e6bab00657735c4fe57bbe6270eef1` |
| 1.21.1 | [9260060](https://www.curseforge.com/minecraft/mc-mods/fishtastic/files/9260060) | `1367efb9fe50018ae058bc5b1f8f8dc36366068f764b076b5f3a8ed3147a1cff` | `7ab5bbf7242e8e9d765bd09924aac6190f5b28f1` |

Returned file IDs establish successful submission; moderation/public download
availability is not independently verified. No upload retries or profile deployment
were performed. Existing Fabric/NeoForge uploads were not repeated. The isolated
publisher configuration, manifests and build/publish logs are retained under
`build/forge-release-2.1.1/`.

No publication operation remains pending. The broader F10/F11 acceptance work and
existing backport parity markers remain at their previously recorded states.

## PR review fixes and cross-loader validation (2026-10-19)

Owner authorized fixing all six review findings and validating the same behavior on
all eight supported loader/version combinations. Pending operation: implement Forge
26.1.2 model-data refresh, shape/diagonal geometry, independent cosmetic meshes and
tint sources; correct 26.1.2/1.21.1 Forge observer login sync and 1.21.1 payload
handled state. Audit 1.20.1 Fabric/Forge and both newer Fabric/NeoForge attachment,
geometry, tint and invalidation implementations. Evidence and exact command/results
will be saved under `build/forge-pr-fixes/`. Run targeted regression coverage and
the complete supported build/test matrix. Treat interrupted runtime probes as
unverified; do not advance unrelated package or backport parity markers.

### Continuation checkpoint (2026-10-19, second session)

Resumed from `docs/forge-ports-2026-10-19-handoff.md`.

Blocker 1 re-read from the artifacts: the image the handoff cited
(`build/forge-pr-fixes/first-render-run/selftest-forge-forgereview-edited_vitrine.png`,
21:29 run) is the pre-fix capture. The final run of that session wrote
`docs/fish-companions-twoclient/run-forge-solo/screenshots/selftest-forge-forgereview-edited_vitrine.png`
at 21:31:47, and it shows the vitrine shape with the diamond frame and the tree cosmetic.
So the world-render acceptance already passed after the model-data refresh and mesh-cache
changes; a fresh run is still required as part of the matrix below.

Pending operation, in order:

1. 26.1.2 full matrix in `D:/GitHub/fishtastic`: `:common:test`, `:fishsim:test`,
   `:tools:tank-shape-gen:test`, `:fabric:assemble`, `:neoforge:assemble`, `:forge:build`,
   then the Forge GameTests. Evidence: `build/forge-pr-fixes/matrix/26.1.2-*.log`.
2. Fresh Forge 26.1.2 `forgereview` client run (acceptance screenshot).
3. Forge 26.1.2 two-client party login regression: dedicated Forge server on 25602,
   `partyseed` as FishB, `partyobserver` as FishA (owner offline), then `partyreturn` as
   FishB. Evidence: `build/forge-pr-fixes/party/26.1.2/`.
4. Same party regression on the 1.21.1 Forge worktree (port 25602 there too, separate
   checkout), plus the client-log check that handled Fishtastic payloads no longer log
   "Unknown custom packet payload".
5. Re-run the 1.21.1 and 1.20.1 test/build matrices only if their working trees change;
   their earlier logs in `build/forge-pr-fixes/` remain valid evidence for these edits.

Interrupted runs stay unverified. No package gate or backport parity marker is advanced.

### Continuation results (2026-10-19, second session)

EXECUTION VERIFIED on Forge 26.1.2 (`D:/GitHub/fishtastic`, branch `codex/forge-26.1.2`):

- `:common:test :fishsim:test :tools:tank-shape-gen:test :fabric:assemble
  :neoforge:assemble :forge:build` — BUILD SUCCESSFUL in 1m43s
  (`build/forge-pr-fixes/matrix/26.1.2-tests-build.log`).
- `:forge:runGametest` — **378/378** required tests passed, BUILD SUCCESSFUL in 33s
  (`build/forge-pr-fixes/matrix/26.1.2-gametests.log`). This closes the outstanding
  26.1.2 GameTest item from the handoff.
- Fresh `forgereview` client run — 15/15 checks PASS and BUILD SUCCESSFUL in 40s
  (`build/forge-pr-fixes/matrix/26.1.2-forgereview.log`). The screenshot
  `docs/fish-companions-twoclient/run-forge-solo/screenshots/selftest-forge-forgereview-edited_vitrine.png`
  (22:09:49) shows the vitrine shape, diamond frame and tree cosmetic after the server-side
  edit, so the stale-chunk-mesh blocker is closed. The earlier failing copy is retained at
  `build/forge-pr-fixes/pre-rerun/selftest-forge-forgereview-edited_vitrine_2131.png`.
- Two-client party regression on a dedicated Forge server (port 25602), evidence in
  `build/forge-pr-fixes/party/26.1.2/`:
  `partyseed.stored` PASS (FishB, bluegill in slot);
  `partyobserver.owner_initially_offline` PASS and `partyobserver.returning_party` PASS
  (`online=[FishA, FishB]`, 775 ticks) while FishB rejoined;
  `partyreturn.persisted` PASS. The server was then killed and restarted, and a fresh FishB
  session again reported `partyreturn.persisted` PASS, proving the
  `world/data/fishtastic/companion_parties.dat` round-trip rather than only in-memory state.

EXECUTION VERIFIED on Forge 1.21.1 (`D:/GitHub/fishtastic-worktrees/mc-1.21.1`,
branch `codex/forge-1.21.1`), evidence in `build/forge-pr-fixes/party/1.21.1/`:

- Two-client party regression: `partyseed.stored` PASS,
  `partyobserver.owner_initially_offline` PASS, `partyobserver.returning_party` PASS
  (815 ticks), `partyreturn.persisted` PASS.
- Payload-handled fix: the same three client logs and both run-directory `latest.log` files
  contain **0** "Unknown custom packet payload" lines, where the pre-fix F10 capture
  (`build/forge-support/1.21.1/F10/twoclient/client-a.log`) has 7 (quest_sync,
  companion_tutorial_sync, tutorial_sync, ...). The party payload itself was received, so the
  handler path was exercised.

Cross-loader runtime confirmation (same three scenes, dedicated server per loader):

| Minecraft | Fabric | NeoForge | Forge |
|---|---|---|---|
| 26.1.2 | PASS (`party/fabric-26.1.2`) | PASS (`party/neoforge-26.1.2`) | PASS (`party/26.1.2`) |
| 1.21.1 | PASS (`party/fabric-1.21.1`) | PASS (`party/neoforge-1.21.1`) | PASS (`party/1.21.1`) |
| 1.20.1 | PASS after fix (`party/fabric-1.20.1/fixed`) | n/a | PASS after fix (`party/forge-1.20.1/fixed`) |

Each PASS row means `partyobserver.owner_initially_offline`,
`partyobserver.returning_party` and `partyreturn.persisted` all passed, under
`build/forge-pr-fixes/party/`.

### New 1.20.1 defect found by the shared scene (fixed)

The first 1.20.1 runs reproduced a real, pre-existing bug that the newer branches do not have:
the returning owner's **own** client saw an empty party (`partyreturn.persisted` FAIL), while the
observer received it correctly. Instrumentation showed the cause: on 1.20.1,
`CompanionStorage.onPlayerJoin` only sends parties while iterating
`PlayerList#getPlayers()`, and the joining player is **not yet in that list** when the hook runs
(Fabric fires it from the client-ready packet; Forge from datapack sync). The server log recorded
`server join FishB self=<party> players=[]`. The loop therefore never sent the joining player
their own party, and a reconnecting player's companions stayed invisible locally.

Fix in `port/1.20.1` `common/.../companion/CompanionStorage.java`: send the joining player their
own party explicitly before the loop, and keep the observer broadcast for everyone already listed.
Re-ran seed/observer/return on both 1.20.1 loaders: all three checks PASS
(`build/forge-pr-fixes/party/fabric-1.20.1/fixed/`, `.../forge-1.20.1/fixed/`).

Post-fix 1.20.1 matrix: `:common:test :fabric:build :forge:build` BUILD SUCCESSFUL
(`matrix/1.20.1-tests-build-2.log`); `:fabric:runGametest :forge:runGametest` 372/372 each,
BUILD SUCCESSFUL (`matrix/1.20.1-gametests-2.log`).

Committed locally (not pushed):

| Repository / line | Branch | Commit |
|---|---|---|
| Fishtastic 26.1.2 | `codex/forge-26.1.2` | `a1035a66` |
| Fishtastic 1.21.1 | `codex/forge-1.21.1` | `7350eb70` |
| Fishtastic 1.20.1 | `port/1.20.1` | `e9268faf` |

All three working trees are clean afterwards. Each commit ran the repository pre-commit hook
(`common`/`fishsim`/`tank-shape-gen` tests plus Fabric GameTests) successfully. Gelatin UI was
not touched. The next external step, if wanted, is pushing these three branches so PR #7/#8 pick
the fixes up; no push or publication was performed here.

## Fishtastic 2.1.2 release (2026-10-19)

Owner authorized a patch release of the PR-review fixes. Branches `codex/forge-26.1.2`,
`codex/forge-1.21.1` and `port/1.20.1` were pushed first (`c66defca`, `7350eb70`, `e9268faf`).

Pending operation RELEASE-212: set `mod_version = 2.1.2` on all three lines, add a 2.1.2
changelog entry (and fold the stale 26.1.2 `Unreleased` party notes into 2.1.1, since
`CompanionParty` dates from 2026-10-13 and shipped in 2.1.1), build every distributable, verify
version / CRC / required Gelatin relation / self-test exclusion, then publish eight Release files
to CurseForge project 1513269: Fabric+NeoForge per newer branch via `publishCurseForge`, Forge
per newer branch via a dedicated Forge-only task, and Fabric+Forge on 1.20.1 via its root task.
Evidence: `build/release-2.1.2/` and `docs/release-2.1.2.md`. Gelatin UI stays at 1.0.38; no
dependency-floor change is part of this release. Do not touch backport parity markers.

Release result: **COMPLETE**. All three builds passed; all eight jars verified; all eight Release
files were submitted to project 1513269 with the required `gelatin-ui` relation; all ten
allow-listed CurseForge profiles now carry the matching 2.1.2 jar and Gelatin 1.0.38. Full table,
hashes, source commits and backup paths: `docs/release-2.1.2.md`; raw evidence in
`build/release-2.1.2/`. No backport parity marker was touched.

### Post-release packaging cleanup (2026-10-20)

Release jars on 1.21.1 and 1.20.1 no longer bundle the development self-test harness. The
Fabric/NeoForge shadow jars and the 1.20.1 Forge release build exclude
`grill24/fishtastic/client/selftest/**`. Client entrypoints are guarded by `DevEnvironmentCheck`
(the 1.20.1 Forge call resolves the harness reflectively), and `-PincludeSelftest` keeps the
harness for the installed-layout smoke build. Verified: all five 2.1.3 jars contain zero
self-test entries, the toggle build contains the expected eight, bytecode shows the guard around
each call, and a 1.20.1 Forge client smoke started cleanly. Both lines are bumped to 2.1.3
(unpublished). Commits: 1.21.1 `2de14ba4`, 1.20.1 `622ccab2`. 26.1.2 already excluded the
harness and stays at 2.1.2 until its next change.

Owner follow-up (2026-10-20): 2.1.3 stays as the working version for the next release, but it is
**not to be published yet** — the owner will add further changes first. The bumps on 1.21.1 and
1.20.1 remain committed. 26.1.2 stays at 2.1.2 until it next changes; align it when the 2.1.3
release is cut.
