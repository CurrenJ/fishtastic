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
Pending: stage/review and create the two local commits, then record results here.
