# Track A: 26.1.2 → 1.21.1 (pass 2)

> Parent: [`README.md`](README.md). Rendering (A5) is in [`track-a5-rendering-1.21.1.md`](track-a5-rendering-1.21.1.md).
> Baseline `26.1.2` @ `33986055` (was `3b8427e4` before seams S5/S6, `f096fc8d` before S6c). Branch `port/1.21.1`, worktree `D:\GitHub\fishtastic-worktrees\mc-1.21.1`.

**Conventions.**
- Paths are relative to `common/src/main/java/grill24/fishtastic/` unless prefixed: `fabric:` = `fabric/src/main/java/grill24/fishtastic/fabric/`, `neoforge:` = `neoforge/src/main/java/grill24/fishtastic/neoforge/`, `testmod:` = `common/src/testmod/java/grill24/fishtastic/`, `test:` = `common/src/test/java/grill24/fishtastic/`.
- "Lands on" cites `Class.java:line` in `D:\GitHub\modding-guide\resources\minecraft-merged-1.21.1-sources` (MC), `neoforge-21.1.209-sources` (NF) or `fabric-api-0.116.7+1.21.1-sources` (FAPI).
- Every Gradle command runs with `-Dorg.gradle.java.home="C:\Program Files\Java\jdk-21"`. It's written `gw` below.
- `mcp/**` and `fabric:compat/coolcam/**` are deleted in A1 (D1) and don't appear in any later list.

---

## The compile strategy: an exclusion list plus boundary stubs

Pass 1 said to exclude the client-rendering packages until they're ported. The dependency graph rules that out. Excluding every file that uses a missing 1.21.1 rendering or GUI API, together with everything that transitively depends on those files, removes **237 of 302** common files. That's because `ItemEffect`, `FishingMinigameAnimation` and a handful of screen launchers link game logic to rendering code.

What works instead is excluding the 62 files that actually use missing APIs, and giving the ~12 of them that kept code calls into a temporary **stub** with the same members.

**Mechanics:**
- **`port/excludes.txt`** (tracked): one path pattern per line. `common/build.gradle` and both platform build files read it into `sourceSets.main.java.exclude`, and the same file lists the mixin class names to strip from `fishtastic.mixins.json` at `processResources` time. Each phase deletes lines from it. At G2 the file must be empty and is deleted.
- **`common/src/portstub/java`** (tracked, added as an extra `srcDir` of `common`'s main source set): the stubs. A stub keeps the real class's package, name and the members listed below. It has empty bodies or trivial returns, except where noted. Every stub file starts with `// PORT STUB: deleted in <phase>`. At G2 this directory must be empty and is deleted.
- The pre-commit stage `compile` compiles with the exclusions in place. The list shrinks monotonically, and it's reviewed in each phase's gate commit.

### The 62 excluded files, by the phase that brings them back

Computed by resolving imports against 1.21.1 (see README). "gelatin" means the file uses gelatin-ui API newer than 1.0.16, the last published 1.21.1 build.

| Returns in | Files |
|---|---|
| **A4** (GUI, needs G-1.21.1) | `client/ElectricFishOrganizerScreen`, `client/EncyclopediaTutorialClientHandler`, `client/FishEncyclopediaScreen`, `client/FishTankAssemblyScreen`, `client/FishTankBrowserScreen`, `client/LeaderboardScreen`, `client/QuestLogScreen`, `client/QuestProgressNotification`, `client/QuestProgressNotificationManager`, `client/TutorialClientHandler`, `client/ZoneIconRectangle`, `client/FishSphereContainer`, `client/FreeformContainer`, `client/SelectableItemButton`, `client/ShapeGalleryPanel`, `client/SilhouetteItemButton`, `client/ThinProgressBar`, `client/effects/{CoinArcEffect,DropOffEffect,PendulumSwingEffect}`, `client/tooltip/{ClientFishTankMaterialsTooltip,ClientRodGearTooltip}`, `compat/{CompatUtil,GelatinMenus,GelatinScreens}`, `util/FishingMinigameAnimation`, `util/ItemActivationAnimation`, `mixin/GuiGraphicsMixin`, `test:client/FishSphereContainerTest` (27 main + 1 test) |
| **A2 itself** (menus compile against gelatin 1.0.16, `GelatinMenu`'s public API is unchanged since then) | `menu/FishTankAssemblyMenu`, `menu/FishTankBrowserMenu` (excluded only until A2.9) |
| **A2.5** (`ItemEffect` split, see below) | `itemeffect/ItemEffect` |
| **A5** (rendering) | `client/CosmeticCaptureClientState`, `client/FishtasticClientSetup`, `client/compositemodel/CompositeTextureHelper`, `client/renderer/{CosmeticStructureItemModel,FishPileBlockEntityRenderer,FishPileBlockItemModel,FishPileRenderState,FishTankBlockEntityRenderer,FishTankRenderState,FishtasticBlackOutlineEffect,FishtasticGlintState,FishtasticItemOutlineAtlas,FishtasticItemStackRenderState,FishtasticOutlineUboRegistry,FishtasticRenderPipelines,FishtasticSilhouetteEffect,FishtasticTextureOutlineEffect,FishtasticWorldOutlineRenderer,PileOfFishItemModel,TankFlockAdapter}`, `mixin/{GuiRendererExecuteDrawMixin,GuiRendererMixin,HumanoidModelMixin,ItemEntityRendererMixin,ItemFeatureRendererMixin,ItemFrameRendererMixin,ItemInHandLayerMixin,ItemInHandRendererMixin,ItemModelResolverMixin,ItemStackRenderStateMixin,RenderTypeMixin}` (31) |
| **A5** (platform) | `fabric:FishtasticFabricClient`'s rendering registrations and `fabric:fishtank/**` (5), `neoforge:FishtasticNeoForgeClient`'s rendering registrations and `neoforge:fishtank/**` (8). The two client entrypoints aren't excluded whole: their rendering calls sit behind `// PORT A5` comments, commented out. |

### Boundary stubs (`common/src/portstub/java`)

Only the members that kept code uses (found by grepping member references from every non-excluded file, platforms included).

| Stub | Members kept code needs | Used by | Deleted in |
|---|---|---|---|
| `client/QuestProgressNotificationManager` | `getInstance()`, `CLEANUP_GOAL_MILESTONE_ID`, `FIRST_CATCH_ID_PREFIX`, `OUT_OF_BAIT_ID`, `consumeDiscoveryFanfareClaim(..)`, `getVirtualGameTime()`, `push(QuestProgressEvent)` | `client/NotificationPriority`, `client/QuestProgressEvent`, `command/TestQuestNotifyCommand`, both client entrypoints | A4 |
| `client/QuestProgressNotification` | `MARGIN`, `STACK_GAP` | `client/NotificationPriority` | A4 |
| `client/TutorialClientHandler` | `getCurrentStep()`, `onFishClicked(..)`, `onInfoPageClosed(..)`, `onQuestLogKeyPressed()` | `client/FishtasticKeyBinds`, client entrypoints | A4 |
| `client/QuestLogScreen`, `client/LeaderboardScreen` | the static `open(..)` launchers only | `item/QuestBookItem`, `item/LeaderboardsBookItem`, `client/FishEncyclopediaClientHelper` | A4 |
| `util/FishingMinigameAnimation` | `LAYOUT`, `LAYOUT_SMALL`, `createCelebrationPreview(..)`, `render(..)` (no-op) | `client/FishingMinigameClientHandler`, `command/CelebrationCommand`, `item/TestItem`, `util/CatchCelebration` | A4 |
| `util/ItemActivationAnimation` | the type only | `util/IGameRendererExtension`, `mixin/GameRendererMixin` | A4 |
| `client/renderer/FishTankBlockEntityRenderer` | `ITEM_BASELINE_Y`, `TANK_CEILING_Y`, `computeBaseY(..)`, `isFloorAnchored(..)`, `topOfConnectedTankStack(..)`. **Copied verbatim** (pure math), so `FishAnimatorFloorLiftTest` and `FishAnimatorSquashTest` keep testing real behaviour. | `client/renderer/FishAnimator`, `client/renderer/TankBubbleEmitter`, `client/util/TankFloors` | A5.1 |
| `client/renderer/TankFlockAdapter` | `sync(..)` (no-op) | `client/util/ClientTankFlocks`, `TankBubbleEmitter` | A5.1 |
| `client/renderer/FishPileBlockEntityRenderer` | the type only | `block/FishPileBlock` | A5.3 |
| `client/renderer/FishPileBlockItemModel` | the type only (`Unbaked` reference) | `client/util/FishPileIcons` | A5.3 |
| `client/renderer/FishtasticItemOutlineAtlas` | `getInstance()`, `MASK_TEXTURE_ID`, `invalidate()` | `itemeffect/ItemEffectManager`, `mixin/GameRendererMixin` | A5.4 |
| `compat/GelatinScreens`, `compat/CompatUtil`, `compat/GelatinMenus` | static registration entry points (no-ops) | `compat/GelatinScreensCompat`, `compat/GelatinMenusCompat`, `compat/GelatinOpenMenuCompat`, `command/FishtasticCommand` | A4 |

`compat/jei/**` (4 files) is excluded along with the screens it references, and comes back in A4.

---

## A1: Build scaffolding

**Goal:** the toolchain resolves and an empty mod loads on both loaders.

### Files

| File | 26.1.2 | 1.21.1 | Reference |
|---|---|---|---|
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 9.2.1 | **9.5.0** (D11) | potions-plus `mc-1.21.1` |
| `build.gradle` (root) | `dev.architectury.loom-no-remap` 1.14, toolchain 25, no mappings | `dev.architectury.loom` **1.17-SNAPSHOT**. `mappings loom.officialMojangMappings()`. Toolchain 21, `options.release = 21`. `loom { silentMojangMappingsLicense(); mixin { useLegacyMixinAp = true; defaultRefmapName = "fishtastic-${project.name}-refmap.json" } }`. The `tools/*` configure block and `publishCurseForge` are unchanged. | potions-plus `build.gradle`: the mixin/refmap block and its comment about the legacy AP |
| `gradle.properties` | MC 26.1.2, loader 0.19.1, FAPI 0.155.2, NeoForge 26.1.2.100, gelatin 1.0.31+26.1.2, JEI 29.33.0.87, cool-cam | `minecraft_version=1.21.1`, `fabric_loader_version=0.17.2`, `fabric_api_version=0.116.7+1.21.1`, `neoforge_version=21.1.209`, `gelatinui_version=1.0.16` (becomes `1.0.31+1.21.1` when G-1.21.1 publishes), `jei_version=19.18.10.218`. Delete `coolcam_version`. `mod_version` stays `2.0.1`, and the existing `allprojects.version` format already gives `2.0.1+1.21.1` (A1.4 needs no change). | potions-plus `gradle.properties` (same versions, known good together) |
| `common/build.gradle` | `implementation` deps, no remap | `modImplementation` for fabric-loader and architectury-injectables, `modCompileOnly` gelatinui-common and `mezz.jei:jei-1.21.1-common-api`. Add `tasks.named('remapJar') { enabled = false }`. Add the `port/excludes.txt` exclude hook and the `src/portstub/java` srcDir. | potions-plus `common/build.gradle:24-70` |
| `fabric/build.gradle` | `common(project(':common'))`, `jar { archiveClassifier = 'raw' }` + shadowJar over the zipTree | `common(project(path: ':common', configuration: 'namedElements'))`. `modImplementation` loader, FAPI and gelatinui-fabric. JEI `jei-1.21.1-fabric-api`/`-fabric`. **Delete cool-cam.** shadowJar gets classifier `dev-shadow`, and **`remapJar { inputFile.set shadowJar.archiveFile }`** becomes the published jar. Keep the `mcp/**` and `fishsim/{render,harness,viewer}/**` excludes (the mcp one now matches nothing, and is harmless). | potions-plus `fabric/build.gradle:35-150`, ancestor `44064cc5:fabric/build.gradle` |
| `neoforge/build.gradle` | `neoForge` dep on 26.1.2.100 | `neoForge "net.neoforged:neoforge:21.1.209"`. The same shadow → remapJar chain. JEI `jei-1.21.1-neoforge`. | potions-plus `neoforge/build.gradle:53-165`, ancestor |
| `common/src/main/resources/fishtastic.accesswidener` | header `official`; `rendertype/RenderType create(String, RenderSetup)`; `ItemModels ID_MAPPER` | header **`named`**. Delete both 26.1-only lines (the classes don't exist on 1.21.1). Keep `CreativeModeTab$Output`, `MenuScreens$ScreenConstructor`, `MenuType$MenuSupplier`, `MenuType.<init>`. Loom's sources can't show whether these are still needed (see README caveat), so an entry the compiler proves unnecessary gets deleted at the A1 gate. A5.4 may add `RenderStateShard` fields if the render types can't be built by subclassing. | ancestor AW is `accessWidener v2 named` |
| `common/src/main/resources/fishtastic.mixins.json` | `JAVA_25`, no refmap | `JAVA_21`, `"refmap": "fishtastic-common-refmap.json"` | N8 |
| `fabric/src/main/resources/fishtastic-fabric.mixins.json` | `JAVA_25`, `"refmap": "fishtastic.refmap.json"` | `JAVA_21`, `"refmap": "fishtastic-fabric-refmap.json"` | N8 |
| `neoforge/src/main/resources/fishtastic-neoforge.mixins.json` | `JAVA_25` | `JAVA_21` (no refmap: the config is empty) | |
| `fabric/src/main/resources/fabric.mod.json` | `"java": ">=25"`, `fabricloader >=0.19.1` | `"java": ">=21"`, `"fabricloader": ">=0.17.2"`. Remove any cool-cam entrypoint or `suggests`. | |
| `fabric/src/testmod/resources/fabric.mod.json` | `fabricloader >=0.19.1` | `>=0.17.2` | |
| `neoforge/src/main/resources/META-INF/neoforge.mods.toml` | JEI `[29.2.0.0,)` | JEI `[19.18,)`. `loaderVersion = "[4,)"` is already right (FML 4.0.41 ships with 21.1.209). | |
| delete | `mcp/**` (common, 11 + 3), `fabric:mcp/**`, `neoforge:mcp/**`, `grill24/fishtastic/mcp/{fabric,neoforge}/**`, `fabric:compat/coolcam/**` (5) | Also remove their call sites: `McpLifecycleNeoForge.register()` in `neoforge:FishtasticNeoForge`, the Fabric equivalent, and the MCP command registration. `env/DevEnvironmentCheck` stays (other dev tooling uses it). | D1 |
| `scripts/git-hooks/port-stage` | `none` | `compile` in the A1 gate commit | plan §4 |
| `port/excludes.txt`, `common/src/portstub/java/` | — | created. For A1 only, the exclude list is **`**/*`** for common and both platforms except the two platform `ModInitializer`/`@Mod` classes, which are cut down to a log line (see order below). | |

### Order

1. Wrapper and root `build.gradle`, then `gradle.properties`, then the three module build files. Run `gw help` until configuration succeeds.
2. `gw :fabric:genSources` (the IDE needs it, and it proves mappings resolve).
3. `gw :fishsim:test :tools:tank-shape-gen:test`: plain-Java modules, so these must already pass.
4. AW header and mixin configs.
5. Delete `mcp/**` and cool-cam, and their call sites.
6. **A1 probe.** Set `port/excludes.txt` to everything, and add `portstub` entrypoints `grill24.fishtastic.fabric.FishtasticFabric` and `grill24.fishtastic.neoforge.FishtasticNeoForge` that only log `Fishtastic A1 probe loaded on <loader> <mc>`. Metadata and entrypoints stay as they are. A2.0 replaces the everything-list with the real list.
7. Gate, then commit with `port-stage` at `compile`.

### Gate (G-A1)

| Check | Command | Expected |
|---|---|---|
| Plain-Java modules | `gw :fishsim:test :tools:tank-shape-gen:test` | fishsim **163 passed, 1 skipped**; tank-shape-gen **21,955 passed** (including the STANDARD gate). Same as 26.1.2 (S3). |
| Both jars build | `gw :fabric:build :neoforge:build` | BUILD SUCCESSFUL. `fabric/build/libs/fishtastic-fabric-2.0.1+1.21.1.jar` and the NeoForge equivalent. |
| **A1.5 refmap check (N8)** | `unzip -l` on each jar | The Fabric jar contains **both** `fishtastic-common-refmap.json` and `fishtastic-fabric-refmap.json`. `fishtastic.mixins.json` inside the jar names the common one. (A1 has no mixins yet, so this only checks the wiring. It becomes a real check at A2.) |
| Loads on Fabric | `gw :fabric:runServer` (eula accepted in `fabric/run`), stop after `Done` | The log contains the A1 probe line and no mixin or remapping errors. |
| Loads on NeoForge | `gw :neoforge:runServer` in the **background** (NeoForge hang caveat) | Same. |
| Datagen | n/a at A1 | |

**Done 2026-09-24.** G-A1 green: fishsim 163 + 1 skipped, tank-shape-gen 21,955, `:fabric:build :neoforge:build` and `classes testClasses` succeed (Gradle 9.5.0, Architectury Loom 1.17.493, JDK 21), and both `runServer`s reach `Done` with `Fishtastic A1 probe loaded on <loader> 1.21.1` and no mixin or remapping errors. Their only errors are 26.1.2 data files (1.21.2+ string recipe ingredients, loot tables naming unregistered items), which A2/A3 fix. Where A1 differs from the table above:
- **Refmap key.** `fishtastic.mixins.json` has no `refmap` key in source. Fabric's `processResources` adds `"refmap": "fishtastic-common-refmap.json"` to its copy. In the shared source JSON the key would also reach NeoForge, whose dev runtime is named, and potions-plus `mc-1.21.1` broke NeoForge runData exactly that way. The Fabric config names `fishtastic-fabric-refmap.json` in source. Refmap names are set once, in the root `build.gradle` (`fishtastic-${project.name}-refmap.json`).
- **Exclusion mechanics** live in `gradle/port-excludes.gradle`. `port/excludes.txt` lines are repo-relative Ant patterns that start with a source directory (`common/src/main/java/...`), so the same file covers common, both platforms, `test` and `testmod`. Each module's `src/portstub/java` is a `main` srcDir; the probes live in `fabric/src/portstub` and `neoforge/src/portstub` (NeoForge's `@Mod` can't live in common). `processResources` strips mixin-config entries and `fabric.mod.json` entrypoints whose class exists only in excluded files, so no config names a class that wasn't compiled. Classes found in no source file (other mods') are left alone.
- **`publishCurseForge`** uploads `remapJar`, not `shadowJar` (now the `dev-shadow` named jar). The shadowJar → published-artifact redirect of `apiElements`/`runtimeElements` is gone; Loom publishes `remapJar`.
- **Testmod classpath (Fabric)** extends the configurations instead of `+= main.compileClasspath`, which misses remapped `mod*` dependencies (potions-plus `mc-1.21.1`). common gets `loom.createRemapConfigurations(sourceSets.test)` + `modTestImplementation` for gelatin.
- **For A2:** the refmap files themselves only appear once mixins compile (A1.5 is `[~]` until G-A2). The NeoForge jar carries the raw AW and no `META-INF/accesstransformer.cfg`, which is also true on 26.1.2, so if an A2 NeoForge compile needs an AW entry, the AW has to be converted or mirrored as an AT. JEI on Fabric is `modLocalRuntime`. The NeoForge `data` run (`serverData()`) is untested until A3.

---

## A2: Core and server logic

**Goal:** `:common` compiles with only the 62 files excluded, unit tests pass, and both platforms compile their server side.

### A2.0: Set up the real exclusion list and stubs
Replace A1's everything-list with the 62-file list above, create the boundary stubs, and strip the excluded mixins from `fishtastic.mixins.json` at build time (driven by the same file). **Order within A2:** A2.0 → A2.1 (a mechanical pass across everything that compiles) → A2.2 → A2.3 → A2.4 → A2.5 → A2.6 → A2.7 → A2.8 → A2.9 → A2.10. After A2.1, iterate with `gw :common:compileJava` and let the compiler order the rest.

### A2.1: Renames and registry access (mechanical)

| 26.1 API | 1.21.1 API (lands on) | Files |
|---|---|---|
| `net.minecraft.resources.Identifier` (+ `fromNamespaceAndPath`, `parse`, `withDefaultNamespace`, `tryParse`) | `ResourceLocation`, with the same four statics (MC `ResourceLocation.java:48,52,56,61`) | **100** files at `33986055` (appendix A lists the 106 from the pass 2 survey; S6c removed the only reference from some). A word-boundary `sed`, then fix comments by hand. **Type-only:** since S6c every construction goes through `util/Ids`, and its four bodies keep the same factory names on `ResourceLocation`, so no call site changes. |
| `commands.arguments.IdentifierArgument` | `ResourceLocationArgument` | `command/FishProfileCommand`, `command/FollowFishStubCommand`, `command/TemperamentCommand` |
| `registryAccess().lookupOrThrow(K)` returning `Registry<T>` (1.21.2 rename) | `registryOrThrow(K)` returning `Registry<T>` (MC `RegistryAccess.java:26`). Note: `HolderLookup.Provider.lookupOrThrow` **still exists** on 1.21.1 but returns `HolderLookup.RegistryLookup<T>` (`HolderLookup.java:34`). A missed rename therefore fails to compile at the assignment instead of silently binding. | 35 files (appendix A) |
| `Registry#getValue(id / key)` | `Registry#get(ResourceLocation)` / `get(ResourceKey)` returning `T` (MC `Registry.java:70,73`) | 13 files (appendix A) |
| `Registry#get(ResourceKey)` returning `Optional<Holder.Reference<T>>` | `getHolder(ResourceKey)` (MC `Registry.java:143`) | found by the compiler (a type mismatch at every site) |
| `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` (1.21.11 permission overhaul) | `src -> src.hasPermission(Commands.LEVEL_GAMEMASTERS)` (MC `CommandSourceStack.java:390`, `Commands.java:144`) | **One line:** the body of `command/FishtasticPermissions.gamemaster()`. Seam S6 (`616b6566`) routed all 21 `requires` sites through it (the appendix A list plus `mcp/McpBridgeCommand`, which A1.3 deletes). |
| `Item.Properties#setId`, `BlockBehaviour.Properties#setId` (1.21.2) | removed: 1.21.1 derives the id at registration | `FishtasticItems` (the `props(loc)` helper becomes `new Item.Properties()`), `FishtasticBlocks`, `architectury/fabric/FabricRegistrationApi`, `architectury/neoforge/NeoForgeRegistrationApi`. Migration commit `06329830`, read backwards. |
| `Level#isClientSide()` | **unchanged**: the method exists (MC `Level.java:162`) | none. Pass 1's A2.1 item is dropped. |

### A2.2: Registration

| 26.1 | 1.21.1 | Files |
|---|---|---|
| `IRegistrationApi.registerBlockEntityType(name, BiFunction<BlockPos,BlockState,BE>, Supplier<Block[]>)`, implemented with `new BlockEntityType<>(factory, blocks)` (NeoForge) or `FabricBlockEntityTypeBuilder.create(...).build()` | Keep the interface signature, so it's version-neutral. NeoForge impl: `BlockEntityType.Builder.of(factory::create, blocks).build(null)` (MC `BlockEntityType.java:330,334`). The Fabric impl is unchanged (`FabricBlockEntityTypeBuilder` exists in FAPI 0.116). | `architectury/IRegistrationApi` (none), `fabric:…/FabricRegistrationApi`, `neoforge:…/NeoForgeRegistrationApi` |
| `DeferredRegister.Items.register(name, Function<Identifier, I>)` | the same overload with `ResourceLocation` (NF `DeferredRegister.java:225,501`) | `neoforge:FishtasticRegistriesNeoForge` (rename only) |
| `DataPackRegistryEvent.NewRegistry.dataPackRegistry(key, codec, netCodec)` | same (NF `DataPackRegistryEvent.java:74`) | `neoforge:FishtasticNeoForge` (no change) |
| `DynamicRegistries.registerSynced(key, codec)` | same (FAPI `DynamicRegistries.java:122`) | `fabric:FishtasticFabric` (no change) |
| Data component registration: `DataComponentType.builder().persistent().networkSynchronized()` + `Registry.registerForHolder` / DeferredRegister | same API on 1.21.1 | `FishtasticDataComponents`, both `…RegistrationApi` (no change) |
| `@EventBusSubscriber` without `bus` (FML 26.1 infers the bus) | FML 4 needs **`bus = EventBusSubscriber.Bus.MOD`** for mod-bus events (`loader-4.0.41.jar` has `EventBusSubscriber$Bus`) | `neoforge-testmod:NeoForgeGameTestRegistration` (`RegisterGameTestsEvent` is `IModBusEvent`, NF `RegisterGameTestsEvent.java:24`). `neoforge:command/CommandRegistrationNeoForge` is game-bus and needs nothing. |

### A2.3: Blocks, block entities, items

| 26.1 API | 1.21.1 API (lands on) | Files |
|---|---|---|
| `loadAdditional(ValueInput)` / `saveAdditional(ValueOutput)` | `loadAdditional(CompoundTag, HolderLookup.Provider)` / `saveAdditional(CompoundTag, HolderLookup.Provider)` (MC `BlockEntity.java:75,90`) | `blockentity/{ElectricFishOrganizerBlockEntity,FishPileBlockEntity,FishTankAssemblyBlockEntity,FishTankBlockEntity,MarineCompostBlockEntity}`, `neoforge:blockentity/FishTankBlockEntityNeoForge` |
| `input.getIntOr(k, d)`, `getStringOr`, `getBooleanOr`, `getCompoundOrEmpty`, `getListOrEmpty` (1.21.5 Optional tags) | `tag.getInt(k)`, etc. These return the zero value when the key is missing, so write `tag.contains(k) ? tag.getInt(k) : d` wherever `d != 0` | 29 sites in `ElectricFishOrganizerBlockEntity`, `FishTankAssemblyBlockEntity`, `FishTankBlockEntity`, `MarineCompostBlockEntity` |
| `input.read(k, CODEC)` / `output.store(k, CODEC, v)` | `CODEC.parse(provider.createSerializationContext(NbtOps.INSTANCE), tag.get(k))` / `encodeStart`. Put a private `readCodec`/`writeCodec` helper in each BE (or one in `util/`). | same files |
| `getUpdateTag(HolderLookup.Provider)` | same (MC `BlockEntity.java:210`) | `FishTankBlockEntity`, `FishPileBlockEntity` |
| `applyImplicitComponents(DataComponentGetter)` | `applyImplicitComponents(BlockEntity.DataComponentInput)` (MC `BlockEntity.java:256,324`). `collectImplicitComponents(DataComponentMap.Builder)` is unchanged (`:285`). `removeComponentsFromTag(ValueOutput)` becomes `removeComponentsFromTag(CompoundTag)`. | `blockentity/FishTankBlockEntity` (S1 left-in-place item 3) |
| BE removal: `affectNeighborsAfterRemoval` / `preRemoveSideEffects` (1.21.5) | `onRemove(BlockState, Level, BlockPos, BlockState newState, boolean moved)` (MC `BlockBehaviour.java:163`). Guard with `!state.is(newState.getBlock())` as vanilla does. | `block/FishTankBlock` |
| `updateShape(BlockState, LevelReader, ScheduledTickAccess, BlockPos, Direction, BlockPos, BlockState, RandomSource)` | `updateShape(BlockState, Direction, BlockState, LevelAccessor, BlockPos, BlockPos)` (MC `BlockBehaviour.java:146`). `ScheduledTickAccess#scheduleTick` becomes `LevelAccessor#scheduleTick`. | `block/FishTankBlock` |
| `useItemOn(...)` returning `InteractionResult` | returns **`ItemInteractionResult`** (MC `BlockBehaviour.java:197`). Map `InteractionResult.SUCCESS` → `ItemInteractionResult.SUCCESS`, and `PASS` → **`SKIP_DEFAULT_BLOCK_INTERACTION`** *(corrected in A2: 26.1's plain `PASS` never falls back to `useWithoutItem`; only `TRY_WITH_EMPTY_HAND` does)* | `block/{ElectricFishOrganizerBlock,FishPileBlock,FishTankAssemblyBlock,FishTankBlock,MarineCompostBlock}` (the two that override `useItemOn`) |
| `useWithoutItem(...)` returning `InteractionResult` | same (MC `BlockBehaviour.java:193`) | none |
| `Item#use` returning `InteractionResult` | `InteractionResultHolder<ItemStack>` (MC `Item.java:142`). Wrap with `InteractionResultHolder.sidedSuccess(stack, level.isClientSide())` etc. | `item/{FishopediaItem,FishtasticFishingRodItem,FishtasticFishItem,LeaderboardsBookItem,PileOfFishItem,QuestBookItem,StormCharmItem,TestItem}` |
| `appendHoverText(ItemStack, TooltipContext, TooltipDisplay, Consumer<Component>, TooltipFlag)` | `appendHoverText(ItemStack, Item.TooltipContext, List<Component>, TooltipFlag)` (MC `Item.java:276`) | `item/{FishopediaItem,FishtasticFishingRodItem,FishtasticFishItem,LeaderboardsBookItem,QuestBookItem,StormCharmItem}` |
| `inventoryTick(ItemStack, ServerLevel, Entity, EquipmentSlot)` | `inventoryTick(ItemStack, Level, Entity, int slot, boolean selected)` (MC `Item.java:250`) | `item/{FishopediaItem,PileOfFishItem,QuestBookItem}` |
| `ItemUseAnimation` | `UseAnim` (MC `Item.java:264`) | `item/StormCharmItem`, `testmod:gametest/StormCharmGameTests` |
| `ItemStack#hurtAndBreak(int, ServerLevel, …)` variants | `hurtAndBreak(int, ServerLevel, ServerPlayer, Consumer<Item>)` / `hurtAndBreak(int, LivingEntity, EquipmentSlot)` (MC `ItemStack.java:420,445`) | `block/FishTankBlock`, `FishtasticDispenseBehaviors`, `mixin/FishingHookMixin`, `server/FishingMinigameManager` |
| `GameRules.ADVANCE_WEATHER`, `getGameRules().get(rule)` / `.set(rule, v, server)` | `GameRules.RULE_WEATHER_CYCLE`, `getBoolean(rule)` (MC `GameRules.java:99,273`). Set with `getRule(rule).set(v, server)`. | `item/StormCharmItem`, `testmod:gametest/StormCharmGameTests` |
| `ContainerInput` (26.1 rename of the click type) | `ClickType` (MC `AbstractContainerMenu.java:299`) | `menu/FishTankAssemblyMenu`, `mixin/QuickCollectPileMixin`, `testmod:gametest/TutorialManagerGameTests` |

### A2.4: SavedData

Only the definition site changes. The 245 `SavedData` hits in 43 files are callers of `FishCatchSavedData`, and their API stays the same.

| 26.1 | 1.21.1 | File |
|---|---|---|
| `SavedDataType<FishCatchSavedData>(id, ctor, codec, DataFixTypes)`, `storage.computeIfAbsent(TYPE)` | `SavedData.Factory<>(FishCatchSavedData::new, FishCatchSavedData::load, null)` (MC `SavedData.java:49`), `storage.computeIfAbsent(FACTORY, id)` (MC `DimensionDataStorage.java:43`). `save(CompoundTag, Provider)` (MC `SavedData.java:19`) encodes the same `CODEC` through `NbtOps` under a `"data"` key. `load` decodes it. Keep the **same `id` string**, so the file name matches 26.1.2 (D4 makes this cosmetic, but it keeps `/fishtastic backup` paths identical). *(A2: not possible; 1.21.1 storage takes flat names only, so the file is `fishtastic_fish_catches.dat`. Backups live in their own folder and are unaffected.)* | `server/FishCatchSavedData` |
| Backups copy the `.dat` file | unchanged: `server/FishCatchBackups` works on files. `FishCatchBackupsNamingTest` (6) and `FishCatchBackupsRetentionTest` (6) cover it, and so do the gametests in A6. | `server/FishCatchBackups` (no change expected) |

### A2.5: Data components, and the S1 leftovers that change on 1.21.1

The S1 facade (`FishtasticItemData`) absorbs most of this. Its callers don't change.

| 26.1 | 1.21.1 | Files |
|---|---|---|
| `TooltipDisplay` (1.21.5), `isTooltipHidden` → `stack.get(TOOLTIP_DISPLAY).hideTooltip()` | `stack.has(DataComponents.HIDE_TOOLTIP)` (MC `DataComponents.java:98`). The `appendHoverText` parameter goes away (A2.3). | `FishtasticItemData`, `item/{FishopediaItem,FishtasticFishItem,FishtasticFishingRodItem,LeaderboardsBookItem,QuestBookItem,StormCharmItem}` |
| `TooltipProvider#addToTooltip(TooltipContext, Consumer<Component>, TooltipFlag, DataComponentGetter)` | `addToTooltip(Item.TooltipContext, Consumer<Component>, TooltipFlag)` (MC `TooltipProvider.java:9`). Drop the getter. Nothing in the three implementors reads it (check at port time). | `component/FishQuality`, `component/ItemSize`, `fishtank/FishTankShape` (S1 left-in-place item 4). `mixin/ItemStackMixin` calls them and is unchanged. |
| `ItemStackTemplate` (26.1: `record(Holder<Item>, int, DataComponentPatch)`) inside `BundleContents#items()` | `BundleContents#items()` returns `Iterable<ItemStack>` (MC `BundleContents.java:68`), and `weight()` returns `Fraction`, not `DataResult<Fraction>` (`:80`). `ItemStackTemplate.fromNonEmptyStack(s)` becomes `s.copy()`. `new ItemParticleOption(ParticleTypes.ITEM, template)` takes an `ItemStack`. | `blockentity/ElectricFishOrganizerBlockEntity`, `item/PileOfFishItem`, `server/FishingMinigameManager`, `client/util/FishPileIcons` (plus the A5 `FishPileBlockItemModel`, `PileOfFishItemModel`) |
| `DataComponents.BREAK_SOUND` (1.21.5) | none. `FishtasticItemData.breakSound` returns `BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.ITEM_BREAK)` (MC `SoundEvents.java:766` is a plain `SoundEvent`). | `FishtasticItemData` (S1 left-in-place item 8) |
| `ResolvableProfile.createResolved(gp)` / `createUnresolved(name/uuid)` (26.1) | `new ResolvableProfile(gp)` / `new ResolvableProfile(Optional.of(name), Optional.empty(), new PropertyMap())` (MC `ResolvableProfile.java:40,44`) | `client/util/PlayerHeadItems`, `client/LeaderboardScreen` (A4) |
| `Item.Properties#component(type, value)` defaults | same API (MC `Item.java:388`) | `FishtasticItems` (no change; S1 item 2 matters only on 1.20.1) |
| `DataComponentPatch` in rewards | same type on 1.21.1 | `data/QuestReward`, `data/ShopEntry` (no change; S1 item 5 is 1.20.1-only) |
| **`ItemEffect` split.** 26.1 `itemeffect/ItemEffect` is a datapack record that also carries render resources (`GpuBuffer outlineParamsBuffer`, `RenderPipeline outlinePipeline`, a `Std140Builder` writer) | Split it: the record keeps only data fields and codec, identical on every branch, and becomes a small **S4 seam candidate on 26.1.2**. The render resources move to a client-side `client/renderer/ItemEffectRenderData` map keyed by the effect's id, built in A5.4. Until A5.4 the map is a stub. The spike (`362b5255:ItemEffect.java`) already shows the 1.21.1 field set: `outline_color`, `outline_falloff`, `outline_width`, `outline_opacity`, `outline_pinwheel`, same JSON. | `itemeffect/ItemEffect`, `itemeffect/ItemEffectManager` |

The 11 component types (`FishtasticDataComponents`: 9 records + `FISH_TANK_SHAPE` + unit `HAS_ALERT`) need no change on 1.21.1.

### A2.6: Networking

| 26.1 | 1.21.1 | Files |
|---|---|---|
| `PayloadTypeRegistry.clientboundPlay()` / `serverboundPlay()` | `playS2C()` / `playC2S()` (FAPI `PayloadTypeRegistry.java:68,61`) | `fabric:…/architectury/fabric/FabricPacketRegistrar` (3 lines) |
| `ServerPlayNetworking.registerGlobalReceiver(type, handler)`, `ClientPlayNetworking.registerGlobalReceiver` | same (FAPI `ServerPlayNetworking.java:75`, `ClientPlayNetworking.java:69`) | same file (no change) |
| `event.registrar(v).versioned(..).optional()`, `playToServer/playToClient(type, codec, handler)` | same (NF `PayloadRegistrar.java:44,52,145,158`, `RegisterPayloadHandlersEvent.java:40`) | `neoforge:…/NeoForgePacketRegistrar` (no change) |
| 23 payload records with `CustomPacketPayload.Type` + `StreamCodec` | same. Every `ByteBufCodecs.*` and `StreamCodec.*` static the tree uses exists on 1.21.1 (checked one by one). | `network/*` (Identifier rename only) |
| **new: `SetDayRatePayload`** (N1, D8) | `CustomPacketPayload` carrying one `float`, S2C | `network/SetDayRatePayload` (new), `network/FishtasticPackets` |

### A2.7: Recipes, loot, advancements

| 26.1 | 1.21.1 | Files |
|---|---|---|
| `CustomRecipe#assemble(CraftingInput)` | `assemble(CraftingInput, HolderLookup.Provider)` (MC `Recipe.java:23`). **Add** `canCraftInDimensions(int w, int h)` (abstract on 1.21.1, `Recipe.java:25`; return `w * h >= 2`). `getResultItem(Provider)` is inherited (MC `CustomRecipe.java:19`). | `recipe/MarineCompostRecipe` |
| `RecipeSerializer` as a 26.1 value (codec + stream codec) | `new SimpleCraftingRecipeSerializer<>(MarineCompostRecipe::new)`, or an anonymous `RecipeSerializer` returning `CODEC` / `STREAM_CODEC` (MC `RecipeSerializer.java:46`) | `recipe/FishtasticRecipeSerializers`, both `…RegistrationApi.registerRecipeSerializer` |
| Loot: `server.reloadableRegistries().getLootTable(ResourceKey)` | same (MC `ReloadableServerRegistries.java:137`) | `server/FishingMinigameManager` (no change) |
| `PlayerAdvancements#award(AdvancementHolder, String)` | same (MC `PlayerAdvancements.java:197`) | `mixin/PlayerAdvancementsMixin` (no change) |

### A2.8: Fishing core, clock, moon

| 26.1 | 1.21.1 | Files |
|---|---|---|
| `FishingHook` targets `<init>(Player, Level, int, int)`, `shouldStopFishing`, `retrieve`, `catchingFish`, `tick` | all present (MC `FishingHook.java:80,237,421,279,139`) | `mixin/FishingHookMixin`: descriptors only |
| `@Redirect catchingFish … BlockState;is(Ljava/lang/Object;)Z` | the call is `blockState.is(Blocks.WATER)`, i.e. **`is(Lnet/minecraft/world/level/block/Block;)Z`** (MC `FishingHook.java:309,349`) | `mixin/FishingHookMixin` |
| `@Redirect … ServerLevel;sendParticles(ParticleOptions,DDDIDDDD)I` | same descriptor on 1.21.1 | none |
| **A2.8.b `MoonPhase`** (26.1 enum, serialized in `FishProfile` JSON) | **One line.** `data/FishMoonPhase` already exists (seam S6, `616b6566`). Change the body of `FishMoonPhase.at(level, pos)` to `return fromIndex(level.getMoonPhase());` (MC `LevelTimeAccess.java:16`), and delete `fromVanilla(MoonPhase)` and its import. Nothing else references vanilla `MoonPhase`. | `data/FishMoonPhase` |
| `level.getOverworldClockTime()` (26.1 world clocks) | `level.getDayTime()`. Other dimensions share the overworld's `DerivedLevelData` day time on 1.21.1, which matches 26.1's "one shared overworld clock" semantics. | `client/FishEncyclopediaScreen`, `client/QuestLogScreen`, `server/FishingMinigameManager` (4 sites) |
| **A2.8.c `SunsetExtensionHandler`**: `server.clockManager().setRate(overworldClock, rate)` | **New mechanism (N1, D8).** Keep the handler's rate calculation unchanged. Apply the rate with a fractional accumulator:<br>• `mixin/ServerLevelTickTimeMixin`: `@WrapOperation` on the `setDayTime(getDayTime() + 1L)` call in `ServerLevel.tickTime()` (MC `ServerLevel.java:434-443`). Adds `rate` to an accumulator, advances by `floor(acc)` and keeps the remainder. **Overworld only.**<br>• `mixin/ClientLevelTickTimeMixin`: the same on `ClientLevel.tickTime()` (MC `ClientLevel.java:238-243`), fed by `SetDayRatePayload`, so client prediction matches between the 20-tick `ClientboundSetTimePacket`s.<br>• Send the payload when the rate changes (the existing `RATE_CHANGE_THRESHOLD` logic) and on player join.<br>Gametest: with rate 0.5, 200 server ticks advance day time by 100 ± 1. | `server/SunsetExtensionHandler`, 2 new mixins, `network/SetDayRatePayload` |

### A2.9: Commands, config, menus

| 26.1 | 1.21.1 | Files |
|---|---|---|
| Brigadier | unchanged apart from A2.1 (permissions, `ResourceLocationArgument`) | `command/*` (25) |
| NeoForge `ModConfigSpec` | same class (the tree imports `net.neoforged.neoforge.common.ModConfigSpec`, which resolves) | `neoforge:FishtasticConfig` (no change) |
| Fabric config (`FabricLoader` paths, hand-rolled) | unchanged | `fabric:…/config/fabric/FishtasticServerConfigImpl` |
| `MenuType` with a FeatureFlagSet, AW'd constructor | same (MC `MenuType.java:48`) | `FishtasticMenuTypes`, both `…RegistrationApi.registerMenuType` |
| `menu/FishTankAssemblyMenu`, `menu/FishTankBrowserMenu` (gelatin `GelatinMenu`) | compile against gelatin-ui 1.0.16: `GelatinMenu`'s public API has no diff between gelatin `main` and `26.1.2` | remove them from `port/excludes.txt` in A2.9 |

### A2.10: Unit tests

- `common/src/test`: **9 classes, 78 tests** on 26.1.2. `FishSphereContainerTest` (14) is excluded until A4 (it's a gelatin-ui container test). The other 8 classes (64 tests) must pass at the A2 gate. The three `client/renderer` tests and `TankFloorsTest` compile against the verbatim-copied `FishTankBlockEntityRenderer` stub members.
- `fishsim` (163 + 1 skipped) and `tank-shape-gen` (21,955) are unchanged.

### Gate (G-A2)

| Check | Command | Expected |
|---|---|---|
| Common compiles | `gw :common:compileJava` | success with only the A4/A5 rows of `port/excludes.txt` left |
| Unit tests | `gw :common:test :fishsim:test :tools:tank-shape-gen:test` | common **64 passed** (78 minus `FishSphereContainerTest`); fishsim 163 + 1 skipped; tank-shape-gen 21,955 |
| Platforms compile (server side) | `gw :fabric:compileJava :neoforge:compileJava` | success (client entrypoints have their A4/A5 calls commented `// PORT`) |
| Refmap check | as A1.5, now with real mixins | both refmaps present. The common refmap has entries for `FishingHookMixin`, `PlayerAdvancementsMixin`, `QuickCollectPileMixin`, `SizedItemClickMixin` and the two tick-time mixins. |
| Hook stage | commit | `port-stage` → `unit` |

**Done 2026-09-24.** G-A2 green: `:common:compileJava`; common tests **64 passed**, fishsim 163 + 1 skipped, tank-shape-gen 21,955; `:fabric:compileJava` and `:neoforge:compileJava`; both backport guards clean. The Fabric jar's `fishtastic-common-refmap.json` maps `FishingHookMixin`, `PlayerAdvancementsMixin`, `QuickCollectPileMixin`, `SizedItemClickMixin`, `ServerLevelTickTimeMixin`, `ClientLevelTickTimeMixin` (plus the client `GameRendererMixin` and `ItemStackMixin`), and its `fishtastic.mixins.json` lists only compiled mixins. There is no Fabric refmap because the Fabric mixin config is empty.

Server smoke run (beyond the gate): both loaders boot through mod construction, registration and the `FishingHook` mixin transform, then stop at datapack registry loading. **13 of 31 `cosmetic_structure` JSONs name blocks that don't exist on 1.21.1**: `exposed_copper_lantern` (all 11 fence arches), `leaf_litter` (`birch_tree`, `leaf_litter`) and `pale_oak_fence` (`cosmetic_fence_arch_pale_oak`). This needs an owner decision in A3 (substitute blocks, or drop those cosmetics on the port branches). The first smoke run also caught a stale `FishingHookMixin` redirect descriptor that the mixin AP didn't flag, so later phases should keep running a server smoke test alongside the compile gate.

Where A2 differs from the tables above:
- **Excludes.** The 62, plus render-only files the pass 2 import scan didn't flag, because they only fail on member types: `client/particle/**` (A5.5), `RenderBuffersHelper`, `RenderBuffersMixin` and `accessor/BufferSourceAccessor` (A5.4a), `CosmeticTransformLoader`, `client/util/FishPileIcons` (A5.3), `client/util/ClientTankFlocks` and `TankBubbleEmitter` (A5.1), `IrisCompat` and `LevelRendererMixin` (A5.7), `FishingHookRendererMixin` (A5.6: 1.21.1 inlines the hand choice in `getPlayerHandPos`), `fabric:datagen/**` (A3) and all three testmod trees (A6.1). Brought back early: both menus (A2.9), `ItemEffect` (split, A2.5), `compat/CompatUtil` (plain reflection), `util/ItemActivationAnimation` (ported: `GuiGraphics`), and `neoforge:fishtank/FishTankPartBlacklistChecker` (config/tag logic, not rendering; the other 7 NeoForge tank files stay excluded).
- **Stubs: 6, not 12.** `QuestProgressNotificationManager` (ids + no-op `enqueue`), `TutorialClientHandler`, `FishingMinigameAnimation` (keeps the real `FishingMinigameState`; 12 members, more than the table listed), `FishTankBlockEntityRenderer` (`ITEM_BASELINE_Y` only), `FishtasticItemOutlineAtlas`, and `TankFlockAdapter` (its pure `GroupSplit` copied verbatim, so `GroupSplitTest` tests real code). The client entrypoints' A4/A5 calls are commented behind `// PORT A4` / `// PORT A5.x` markers instead of being stubbed, which removed the need for the screen, launcher, BER and gelatin stubs.
- **Port-branch helpers** (`util/`): `BlockEntityNbt` (the `ValueInput`/`ValueOutput` calls as static helpers, so load/save bodies read line for line like 26.1.2's), `InteractionResults` (`useItemOn`/`use` result conversion), `StreamCodecs` (`composite` for 7–9 fields; 1.21.1 stops at 6: `LeaderboardEntry`, `StartFishingMinigamePacket`, `QuestSyncPacket`), `FishtasticCodecs.VEC2` (26.1's `Vec2.CODEC`). `compileOnly jspecify` covers 26.1's `@Nullable`.
- **Semantics worth knowing when forward-porting:** `SUCCESS_SERVER` → `sidedSuccess(isClientSide())`, or `CONSUME` in server-only branches; `useItemOn` bodies stay 26.1-shaped in private `useItemOnWithResult` methods; `saveCustomOnly` is final on 1.21.1, so the tank strips connectivity keys in `removeComponentsFromTag` (both item-copy paths call it, world saves don't); `inventoryTick` runs on both sides on 1.21.1, so the three overrides return early client-side; the bundle insert-fail sound and selected-item toggle don't exist before 1.21.2 and are silent no-ops; `Level#canHaveWeather` and `precipitationAt` are replicated from 26.1; `LevelResource`'s constructor is private, so backups resolve from `LevelResource.ROOT`.
- **Sunset Postcard (A2.8.c)** is `SetDayRatePacket` (the tree's `*Packet` naming), `ServerLevelTickTimeMixin` (overworld only) and `ClientLevelTickTimeMixin` (all dimensions, since the server always sends overworld time). The rate is sent on change and on join, and the client resets it on disconnect. Its gametest waits for A6.1.
- **`ItemEffect` split** is on this branch only. Landing the data-only `ItemEffect` on 26.1.2 as an S4 seam (as the A2.5 table suggests) would remove this diff from every branch; it needs a go-ahead.

---

## A3: Datagen and resources

**Goal:** Fabric datagen runs on 1.21.1, and the generated tree diffs cleanly against 26.1.2 apart from the listed format differences.

Datagen runs the **client** (`runDatagen` inherits `client`), so A3 needs the Fabric client entrypoint to start. It does, with its A4/A5 registrations commented out.

### Files

| 26.1 API | 1.21.1 API (lands on) | Files |
|---|---|---|
| `FabricPackOutput` | `FabricDataOutput` (FAPI `datagen/v1/FabricDataOutput`) | the 12 providers that import it (appendix A) |
| `FabricTagsProvider` / `TagAppender` (26.1) | `FabricTagProvider.ItemTagProvider` / `BlockTagProvider`, `addTags(HolderLookup.Provider)` (FAPI `FabricTagProvider.java:86`), `getOrCreateTagBuilder(tag).add(..)` | `fabric:datagen/FishtasticItemTagProvider`, `fabric:datagen/FishtasticBlockTagProvider` |
| `FabricBlockLootSubProvider` | `FabricBlockLootTableProvider#generate()` (FAPI `FabricBlockLootTableProvider.java:64`). `CopyComponentsFunction.copyComponents(BLOCK_ENTITY).include(type)` exists on 1.21.1 (1.20.5 API). | `fabric:datagen/FishtasticBlockLootTableProvider` |
| `FabricRecipeProvider` + 1.21.2 recipe registry types | `buildRecipes(RecipeOutput)` (FAPI `FabricRecipeProvider.java:65`), 1.21.1 `ShapedRecipeBuilder.shaped(RecipeCategory, item)` with `Ingredient`s (no `HolderGetter`) | `fabric:datagen/FishtasticRecipeProvider` |
| `client.datagen.v1.provider.FabricModelProvider` + `net.minecraft.client.data.models.*` (1.21.4 model gen) | `datagen.v1.provider.FabricModelProvider` (FAPI `FabricModelProvider.java:35,37`) + `net.minecraft.data.models.{BlockModelGenerators,ItemModelGenerators}` and `…models.model.{ModelTemplates,TextureMapping,ModelLocationUtils}` | `fabric:datagen/FishtasticModelProvider` (the biggest datagen change, see below) |
| `ItemModelUtils`, `HasComponent` client-item conditions, `assets/*/items/*.json` generation | Removed. Item models go to `models/item/*.json`. The 4 conditional items and the chest select become **model `overrides`** with predicates (N4): `minecraft:cast` on the two rods, `fishtastic:has_alert` on `fishopedia` and `quest_book`, and `cosmetic_treasure_chest` as `builtin/entity` + BEWLR (A5.3). | `fabric:datagen/FishtasticModelProvider` |
| Fragment model providers (`FishTank{Frame,Glass,Sand}ModelProvider`) using `tools:tank-shape-gen` | They write plain block-model JSON through `DataProvider.saveStable`, so they're API-stable. Only the `FabricPackOutput` rename and `Identifier` apply. | `fabric:datagen/FishTank{Frame,Glass,Sand}ModelProvider`, `fabric:datagen/FishTankShapeGeometryStrategies` |
| Codec-driven providers (`CosmeticStructureProvider`, `ItemEffectProvider`, `QuestProvider`, `ShopEntryFromQuestProvider`) | `FabricCodecDataProvider` (FAPI `FabricCodecDataProvider.java:91`) | rename only |
| `DailyQuestFamily` builds a `DataComponentPatch` | same API on 1.21.1 | none |
| NeoForge `GatherDataEvent.Server` | `GatherDataEvent` (single event on 21.1) | `neoforge:datagen/GameTestStructureProvider`, `neoforge:FishtasticNeoForge`. With D10, the provider moves to common testmod resources (A6.1), and this whole item may be deleted. |

### Order
1. The rename-only providers.
2. Tags, loot, recipes.
3. `FishtasticModelProvider`.
4. `gw :fabric:runDatagen`, which runs `copyGeneratedAssetsToCommon`.
5. Delete `assets/fishtastic/items/` (180 files, A3.3). **Update `scripts/copy_assets_to_common.py`'s `EXCLUDE`**: it names `assets/fishtastic/items/fish_tank.json`, which no longer exists.
6. Diff.

### Gate (G-A3): the datagen diff expectation

`gw :fabric:runDatagen`, then `git diff --stat -- common/src/main/resources` against the 26.1.2 baseline tree:

| Area | Expected diff |
|---|---|
| `assets/fishtastic/items/**` (180) | **deleted** (A3.3) |
| `assets/fishtastic/models/item/*.json` (149 plain + the new override files) | the 149 plain ones **unchanged**. `copper_fishing_rod`, `obsidian_fishing_rod`, `fishopedia`, `quest_book` gain `overrides`, plus the `_cast`/`_alert` variant models they point at. `cosmetic_treasure_chest`, `fish_tank`, `fish_pile_block`, `pile_of_fish` and the 31 cosmetic-structure items become `builtin/entity` parents. |
| `assets/fishtastic/models/block/**` (5,088 tank fragments + the rest) | **unchanged** (tank-shape-gen is byte-identical, S3) |
| `assets/fishtastic/blockstates/*.json` | tank blockstates: see A5.2 (the 26.1 custom blockstate model becomes a `"loader"` model reference on NeoForge and a model-loading-plugin redirect on Fabric). Everything else unchanged. |
| `data/fishtastic/recipe/*.json` (74) | 1.21.1 `ItemStack` result format: `"result": {"id": …, "count": …}` stays the same (1.20.5 format). The 1.21.2 `"key"` values that are plain item-id strings become `{"item": "…"}` ingredient objects. **Expect all 74 to differ in `key`/`ingredients`.** Review 5 of them by hand. |
| `data/fishtastic/advancement/**` (81) | **expect none or very few.** Criteria trigger JSON has the same shape. Item predicates: 1.21.1's `HolderSetCodec` already writes a single-item set as a bare string (MC `HolderSetCodec.java:29`), the same as 26.1. Any diff here is a real finding. |
| `data/fishtastic/loot_table/**` (39) | `copy_components` unchanged. Expect 0–few diffs. |
| `data/fishtastic/tags/**` (39) | **unchanged** (folder names are already singular) |
| `data/fishtastic/fishtastic/**` (hand-authored datapack registries, 371) | **unchanged, byte-identical.** This is the version-neutral data (R5 retired). |
| Stray `data/fishtastic/{cosmetic_structure,item_effect}/` (18 + 4, pre-namespacing leftovers) | Check on 26.1.2 whether anything reads them. If not, delete them on 26.1.2 (not a port item). |

Plus: **A3.4**. Start `runClient` once and grep `latest.log` for the 1.21.1 missing-model wording, `Unable to load model: '…' referenced from` (26.1's was `Missing block model`). Expect 0 hits. The fragments are loaded on demand in A5.2, so the check becomes real there.

**A3.5:** the tree has **no `pack.mcmeta`** (both loaders synthesize one), so there's nothing to set. Confirm the synthesized format is 34/48 in the log.

**Done 2026-09-24.** G-A3 green. `fabric:datagen/**` is off `port/excludes.txt`, all 12 providers compile on FAPI 0.116, and `:fabric:runDatagen` runs through the client entrypoint with its `// PORT A4/A5` lines still commented (nothing else in the client path blocked it). Diff of `common/src/main/resources` against `33986055`, ignoring CR/LF:
- `assets/fishtastic/items/**`: 180 deleted. `copy_assets_to_common.py`'s `EXCLUDE` is empty.
- `assets/fishtastic/models/item/**`: 37 changed or new. `copper_fishing_rod`/`obsidian_fishing_rod` gain a `minecraft:cast` override and `fishopedia`/`quest_book` a `fishtastic:has_alert` override (the `_cast`/`_alert` models were already there, unchanged). `fish_tank`, `fish_pile`, `cosmetic_treasure_chest` and the 29 structure cosmetics are `builtin/entity`. `cosmetic_lit_campfire` is a plain `minecraft:block/campfire` child. The other plain models, `pile_of_fish` included, are unchanged.
- `models/block/**`, `blockstates/**`, `advancement/**`, `loot_table/**`, `tags/**`: **0 diffs.**
- `recipe/**`: 72 generated recipes differ as expected (ingredient objects; shaped/shapeless/stonecutting results also gain `"count": 1`). Hand review: `copper_fishing_rod`, `electric_fish_organizer`, `predator_bait`, `coal`, `cooked_cod_from_fishtastic_fish_smelting`, `string`. `deep_sea_bait` is hand-authored on 26.1.2 (`07a8bc3a`, never in the provider), so it was converted by hand into the provider's format. `marine_compost` is unchanged.
- `data/fishtastic/fishtastic/**`: byte-identical apart from the cosmetics decision (below). Quests and shop entries are regenerated identically.
- Both servers reach `Done` with no errors (1,364 recipes and 1,480 advancements on each). The world loads, so `ServerLevelTickTimeMixin` is applied, and with `required: true` + `defaultRequire: 1` a failed injection would have crashed. Fabric `runClient`: 0 × `Unable to load model`. The only model warning is the `fish_tank` blockstate (26.1's custom blockstate model, A5.2).

Cosmetics decision (owner, 2026-09-24), a permanent port-branch diff:
- The 10 remaining fence arches use a hanging `minecraft:lantern` in place of `exposed_copper_lantern` (`CosmeticStructureProvider`; the generated arches are copied into `data/fishtastic/fishtastic/cosmetic_structure/`, as on 26.1.2).
- The pale oak arch is gone: `FishtasticItems.FENCE_ARCH_WOOD_TYPES` has no `pale_oak`, and its structure (both dirs), shop entry and lang key are deleted.
- The leaf litter cosmetic is gone: `COSMETIC_LEAF_LITTER` and its creative-tab and `CreativeTabGameTests` lines, the `leaf_litter` structure, its shop entry and lang key.
- `birch_tree.json` loses its one `minecraft:leaf_litter` part (11 lines).

Where A3 differs from the tables above:
- **Tags:** the FAPI 0.116 builder method is `getOrCreateTagBuilder`. The `modding-guide` FAPI sources show a `tag(TagKey)` override, which is a genSources naming artifact (the remapped jar has `getOrCreateTagBuilder` returning `FabricTagBuilder`, and `tag` returning vanilla's `TagAppender`). When the sources and the jar disagree, check with `javap` on the Loom-remapped jar.
- **R5 holds for loading, not for datagen.** Vanilla 1.21.1 `Registries.elementsDirPath` is the bare path (FAPI's `RegistryLoaderMixin` adds `<ns>/` only when loading), so `createRegistryElementsPathProvider` wrote `data/fishtastic/quest` and `shop_entry`. Port-only helper: `FishtasticDataGenerator.registryElementsPathProvider` (used by `QuestProvider` and `ShopEntryFromQuestProvider`).
- **Cooking recipes:** 1.21.1 `SimpleCookingRecipeBuilder.smelting` derives the book category from the result. It matches all three explicit 26.1 values (coal MISC, glass BLOCKS, cooked cod FOOD), so the JSON is the same.
- **Item-model predicates need client registration.** Vanilla registers `cast` only for `Items.FISHING_ROD`, and `fishtastic:has_alert` is new. Until `ItemProperties.register` calls land (A4/A5 client setup), the overrides never match (1.21.1 skips unknown properties), so the rods never show `_cast` and the books never show `_alert`.
- **`builtin/entity` items render nothing until A5's BEWLRs**, and they have no display transforms yet (A5.3 decides those). `fish_pile_block` in the table is the name of 26.1's pile icon item model (`FishPileIcons`), not an item. The block's item is `fish_pile`.
- **26.1.2 findings (report only, not changed there):** the "stray" `data/fishtastic/{cosmetic_structure,item_effect}` dirs are live datagen output, because `CosmeticStructureProvider` and `ItemEffectProvider` use a plain `createPathProvider`. Nothing reads them, so the registry copies are synced by hand, and the 4 `item_effect` copies have already drifted from the generator. Fix on 26.1.2: `createRegistryElementsPathProvider`, then delete both dirs. Also, `recipe/deep_sea_bait.json` should move into `FishtasticRecipeProvider`, so the backports stop hand-converting it.

---

## A4: GUI

**Needs G-1.21.1** (below). Order: publish gelatin-ui `1.0.31+1.21.1` to mavenLocal → bump `gelatinui_version` → remove the A4 rows from `port/excludes.txt` → fix compile errors screen by screen.

### Files and API map

| 26.1 API | 1.21.1 API (lands on) | Files |
|---|---|---|
| `GuiGraphicsExtractor` (26.1 rename of `GuiGraphics`) | `GuiGraphics` | the 14 importing files: `client/{ElectricFishOrganizerScreen,EncyclopediaTutorialClientHandler,FishEncyclopediaScreen,FishTankAssemblyScreen,QuestProgressNotification,QuestProgressNotificationManager,SilhouetteItemButton,TutorialClientHandler,ZoneIconRectangle}`, `client/tooltip/{ClientFishTankMaterialsTooltip,ClientRodGearTooltip}`, `mixin/GuiGraphicsMixin`, `util/{FishingMinigameAnimation,ItemActivationAnimation}` |
| `graphics.pose()` returning `Matrix3x2fStack` (1.21.6): `pushMatrix/popMatrix`, `translate(x,y)`, `scale(x,y)`, `rotate(rad)` | `PoseStack` (MC `GuiGraphics.java:113`): `pushPose/popPose`, `translate(x,y,0)`, `scale(x,y,1)`, `mulPose(Axis.ZP.rotation(rad))` | **62 call sites**, same 14 files. The most error-prone part of A4: 2D and 3D translate/scale have different arities, so the compiler catches every one. |
| `blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, tw, th[, color])` | `blit(ResourceLocation, x, y, u, v, w, h, tw, th)` (MC `GuiGraphics.java:410-422`). A `color` argument becomes `RenderSystem.setShaderColor(r,g,b,a)` … `blit` … `setShaderColor(1,1,1,1)`. | 11 sites |
| `blitSprite(RenderPipelines.GUI_TEXTURED, sprite, …)` | `blitSprite(ResourceLocation, x, y, w, h)` (MC `GuiGraphics.java:346`) | 2 sites |
| `text(font, …)` | `drawString(font, …)` (MC `GuiGraphics.java:267-295`) | 10 sites |
| `item(stack, x, y)` / `fakeItem` | `renderItem` / `renderFakeItem` (MC `GuiGraphics.java:523,535`) | 4 sites |
| `setTooltipForNextFrame(font, lines, x, y)` | `renderTooltip(font, lines, Optional.empty(), x, y)` (MC `GuiGraphics.java:624`), drawn immediately, so call it last | 1 site |
| `fill`, `fillGradient` | same (MC `GuiGraphics.java:188,222`) | 18 sites (no change) |
| `mixin/GuiGraphicsMixin`: the 26.1 16-px-slot rescale hack (its javadoc explains that 26.1 `item()` renders a 16×16 slot where 1.21.1 rendered in unit space) | **Delete the hack.** On 1.21.1 `renderItem` already works in model space. Keep only what the ancestor's `44064cc5:mixin/GuiGraphicsMixin` had. It moves to A5.4 anyway, because the spike's GUI-outline hook lives in the same class. | `mixin/GuiGraphicsMixin` |
| `KeyEvent` / `MouseButtonEvent` (1.21.9 input records) | `keyPressed(int keyCode, int scanCode, int modifiers)` / `mouseClicked(double, double, int)` | `client/FishEncyclopediaScreen` (the only file; gelatin-ui wraps the rest) |
| `KeyMapping.Category` (1.21.9) | a plain category string in the `KeyMapping` constructor | `client/FishtasticKeyBinds` |
| Fabric `KeyMappingHelper` | `KeyBindingHelper` | `fabric:FishtasticFabricClient` |
| Fabric `ClientTooltipComponentCallback` | `TooltipComponentCallback` | `fabric:FishtasticFabricClient` |
| Fabric `HudElementRegistry` | `HudRenderCallback` (FAPI `client/rendering/v1/HudRenderCallback`) | `fabric:FishtasticFabricClient` (notification overlay and tutorial HUD) |
| NeoForge `RegisterClientTooltipComponentFactoriesEvent`, `RenderGuiEvent` | same names on 21.1 | `neoforge:FishtasticNeoForgeClient` (no change) |
| JEI 29 API (`SlotDisplay`, recipe displays) | JEI 19.18 API: `IRecipeCategory#setRecipe(IRecipeLayoutBuilder, R, IFocusGroup)`, `RecipeType.create`. `SlotDisplay` (26.1 recipe display) → explicit `ItemStack` lists. | `compat/jei/{FishtasticJeiPlugin,MarineCompostRecipeCategory,MarineCompostRipeningRecipe,MarineCompostRipeningRecipeCategory}` |
| gelatin screens and components | the gelatin-ui 1.21.1 API is kept identical to 1.0.31 by G-1.21.1, so no Fishtastic change beyond the rows above | `client/*Screen`, `client/FishSphereContainer`, `client/FreeformContainer`, `client/*Button`, `client/ShapeGalleryPanel`, `client/ThinProgressBar`, `client/effects/*`, `compat/Gelatin*` |

The GUI shader effects (`FishtasticSilhouetteEffect`, `FishtasticBlackOutlineEffect`, `FishtasticTextureOutlineEffect`) are gelatin effects built on `RenderPipeline`s + UBOs. They're **A5.4**, not A4. Until A5.4, `SilhouetteItemButton` and the organizer render without the effect (a stubbed `apply()`).

### Gate (G-A4)
- `gw :common:test`: **78 passed** (`FishSphereContainerTest` is back).
- In game, on both loaders: open every screen (Encyclopedia, Quest Log, Leaderboards, Tank Browser, Assembly, Organizer, Shape Gallery), the tutorial overlay and the notification toasts. Use each screen's main interaction once. This can run unattended with the spike's marker-file self-test pattern, extended to open each screen by id and dump a screenshot.
- `port/excludes.txt` has only A5 rows left.

**Done 2026-09-24.** G-A4 green. `gw :common:test` is **78 passed, 0 failures** (`FishSphereContainerTest` is back in), and `port/excludes.txt` holds only the A5 rows and the A6.1 testmod rows.

**In-game, both loaders, unattended.** The rendering spike's marker-file self-test pattern (`client/spike/OutlineSpikeSelfTest`) was re-added temporarily as `client/selftest/GuiSelfTest` — it creates a flat creative world, drives every GUI through its *real* open path, and screenshots each one — and deleted before this commit. Nothing is opened with `mc.setScreen`: the gelatin screens are opened by the in-game commands that open them, and the menu screens by right-clicking a placed block with an empty hand.

| Screen | Opened by | Fabric | NeoForge |
|---|---|---|---|
| Quest Log | `/gelatin quest_log` | ✓ | ✓ |
| Fish Encyclopedia (+ its tutorial overlay) | `/gelatin fish_encyclopedia` | ✓ | ✓ |
| Leaderboards | bare `/fishtastic` | ✓ | ✓ |
| Tank Browser | empty-hand click on a placed `fishtastic:fish_tank` | ✓ | ✓ |
| Assembly + Shape Gallery | empty-hand click on `fishtastic:fish_tank_assembly` | ✓ | ✓ |
| Organizer | empty-hand click on `fishtastic:electric_fish_organizer` | ✓ | ✓ |
| World tutorial overlay | `TutorialClientHandler.PACKET_HANDLER` with `QUEST_INTRO` | ✓ | ✓ |
| Quest progress toasts | `/fishtastic testquestnotify`, then a *fresh* already-completed banner | ✓ | ✓ |

**The toasts needed vanilla's tutorial hints turned off before they could be photographed, and that is itself the finding.** On 1.21.1 vanilla renders its toasts in `GameRenderer.render` *after* `Gui.render` (`GameRenderer.java:1138-1143`), and Fabric's `HudRenderCallback` is injected at `Gui.render`'s `@At("TAIL")` (`fapi InGameHudMixin`). So **any vanilla toast is drawn over any mod HUD element**, and on a fresh world the movement-hint toast lands exactly on top of a quest notification — both are top-right. The notification was never missing: a temporary probe on its `render` showed it entering on every frame with `phase=HOLD`, `x` easing 603 → 455, `w=138 h=38` in a 534-wide GUI. With the hints off (`options.tutorialStep = NONE`, `Tutorial.stop()`, `getToasts().clear()` — `stop()` alone is not enough, the step instance is recreated from the option) the corner clears and the banners render, identically on both loaders (two banners stacked in priority order in one pass, and the `Complete!` badge in another). The banner's name and target come from the quest registry (`Bluegill Beginner`, 3/10), not from the probe command's arguments — `TestQuestNotifyCommand` only uses its name/count arguments in its chat message. See "Found on 1.21.1" below.

All seven screens render identically on the two loaders, and match 26.1.2's layout apart from the A5 gaps below. The **Shape Gallery is a panel, not a screen** — `ShapeGalleryPanel` is only ever constructed by `FishTankAssemblyScreen:113` — so it is captured in that screen's screenshot, forced open with `FishtasticClientConfig.setShapeGalleryOpen(true)` (the value is read at screen-construction time, and a previous run may have persisted `false`).

**Item properties (both loaders).** Both predicates A4 registers resolve to their override models, checked through `ItemRenderer#getModel`:

| Predicate | Registered | Value with the flag | Value without | Resolved model changes |
|---|---|---|---|---|
| `fishtastic:has_alert` on `fishopedia` | ✓ | 1.0 | 0.0 | ✓ |
| `fishtastic:has_alert` on `quest_book` | ✓ | 1.0 | 0.0 | ✓ |
| `minecraft:cast` on `copper_fishing_rod` | ✓ | 1.0 while fishing | 0.0 with a null entity | ✓ |

The rod is cast through the real use path (`mc.gameMode.useItem`), so the client's `player.fishing` is set by the bobber's add-entity packet, exactly as in play; the idle/cast pair of screenshots shows visibly different hotbar icons. **Gotcha for A5:** assert model identity through `ItemRenderer#getModel(stack, level, entity, seed)`, not `ModelManager#getModel(ModelResourceLocation.inventory(<model path>))` — an override's baked model is not registered under the override target's own path, so a map lookup compared against it is always false even when the override works.

**Expected gaps visible in the screenshots (all A5, none an A4 defect):**
- **Shape Gallery cells are blank.** `ShapeGalleryPanel.previewStack` (client/ShapeGalleryPanel.java:203) renders a `fish_tank` `ItemStack`, and the tank's item model is `builtin/entity` on 1.21.1 — the platform tank item models are A5.2/A5.3 and are still on `port/excludes.txt`. The class's own comment ("the per-platform fish tank item model already renders the preview") describes the 26.1 arrangement. The selection highlight still draws, so the panel is functional.
- **The encyclopedia does not silhouette uncaught species.** `SilhouetteItemButton` sets `FishtasticGlintState.SILHOUETTE_REQUESTED` and nothing reads it until A5.4c, so every species renders in full colour. This is the most user-visible consequence of the GUI-shader stub: the encyclopedia spoils every species until A5.4c lands.
- **The tank block/item render as the missing model.** Fabric logs 2 WARN (`Exception loading blockstate definition: 'fishtastic:fish_tank'`), NeoForge 1 ERROR (`Model loader 'fishtastic:fish_tank' not found`). This is 26.1's custom blockstate/tank model, which A5.2 replaces.

**Server smoke (both loaders, beyond the gate).** `:fabric:runServer` reaches `Done (0.700s)` and `:neoforge:runServer` `Done (0.952s)`, each with **0 ERROR lines and no mixin apply failures**. `runClient` on Fabric reaches the title screen and runs the self-test with **0 ERROR lines**. The NeoForge client's only errors are the JEI ones below and the tank model loader line.

**Found on 1.21.1, HUD layer order (report only; decided in A5.7, 2026-09-24).** FAPI 0.116.7 has only `HudRenderCallback` — there is no `HudElementRegistry` or `VanillaHudElements` anywhere in its sources, which confirms the A4 row above — and it fires at `Gui.render` TAIL, while vanilla renders toasts afterwards in `GameRenderer.render`. The consequence is that **every vanilla toast covers the quest notifications, the tutorial overlay and the minigame bar**, and since advancement and recipe-unlock toasts fire constantly in normal play this is hit routinely, not only on a fresh world. **Correction (A5.7):** this note originally opened "a real behavioural difference from 26.1.2", on the grounds that 26.1.2 registers its HUD layers against vanilla's *layers*, `minecraft:toast` among them, and so draws the notification above the toasts. That is not what 26.1.2 does: its `GameRenderer` extracts the HUD at line 508 and the toasts at 562, so the toasts cover the HUD there too and there is no difference to port. The owner was told and chose the 1.21.1-only mixin anyway — a `@Inject` after the `"toasts"` section in `GameRenderer.render` that draws the three layers while no screen is open (with a screen open they stay in the HUD pass, under it, rather than jumping over the screen). The same choice faces `port/1.20.1`. Implementation and evidence: `track-a5-rendering-1.21.1.md`, A5.7.

**Found in the toast check, pre-existing on 26.1.2 (fixed on 26.1.2 first, then cherry-picked).** `QuestProgressNotification.updateProgress(newEvent)` — the in-place path `enqueue` takes when a quest's banner is already on screen — never reassigns the stored `event`. It sets only `barTargetFraction`, resets the hold timer, and (when the new event completes) plays the completion sound and arms `completeFlashTimer`. So for an in-place update the banner's counter keeps the *first* event's numbers, and the `Complete!` badge — gated on `event.completed()` — never draws; only the bar animates, and the completion sound plays anyway. Reproduced on both loaders with `/fishtastic testquestnotify` then `/fishtastic testquestnotify complete`: the second logged `active=1` (in-place) and the banner still read `3 / 10` 25 ticks later. **Fixed:** 26.1.2 `7839f271` assigns the event and recomputes the name and target; cherry-picked as `e33f5792`, and verified on the port by the self-test's `fixes` scene (the in-place banner now reads `5 / 10` with the badge).

**Found on NeoForge, JEI 19.18 (fixed on 26.1.2 first, then cherry-picked).** Every one of the five gelatin screens logged, twice per open:

```
Received invalid gui properties for screen: class grill24.fishtastic.client.QuestLogScreen
guiXSize must be greater than 1 and less than 1000000000: 0
guiYSize must be greater than 1 and less than 1000000000: 0
screenWidth must be greater than 1 and less than 1000000000: 0
```

`FishtasticJeiPlugin.fullScreenGui` captures `screen.width`/`screen.height` when JEI asks for the properties, and JEI 19 asks before the screen has been sized, so it is handed zeros. **The handler is byte-identical on 26.1.2**, so this was a latent bug there too — JEI 29 either does not ask that early or does not validate. Impact was two ERROR lines per screen open plus JEI falling back on overlay placement; the screens themselves rendered correctly. **Fixed — and the suggestion above was wrong.** 26.1.2 `7839f271` (cherry-picked as `e33f5792`) returns `null` from the size getters until the screen has a size, which `IScreenHandler`'s `@Nullable` allows and JEI does not log. Returning the live `screen.width`/`screen.height` lazily would not have worked: JEI validates the properties as soon as `apply` returns, then compares each frame's fresh object against the cached one, so a lazy cached object tracks the screen and compares equal forever. Verified: 0 `Received invalid gui properties` on NeoForge (was 2 per open).

**Where A4 differs from the API map:**
- **`GuiGraphicsMixin` is the ancestor's body, byte for byte** — `git diff 44064cc5:<file> HEAD:<file>` is empty. The 26.1 16-px-slot rescale hack is gone, as planned: on 1.21.1 `renderItem` already works in model space. The class moves to A5.4 with the rest of the GUI-outline hook.
- **The GUI shader effects are stubbed as a whole, not just an `apply()`.** The new portstub `client/renderer/FishtasticGlintState` keeps `SILHOUETTE_REQUESTED` / `BLACK_OUTLINE_REQUESTED` so `SilhouetteItemButton` and the organizer's call sites are unchanged; the two outline blits are commented out with `// PORT A5.4c` in `ZoneIconRectangle` and `FishingMinigameAnimation#renderZoneIcon`.
- **`extractBackground` → `renderBg(GuiGraphics, float, int, int)`** — a different name *and* a different parameter order. `ElectricFishOrganizerScreen` extends `AbstractContainerScreen` rather than `GelatinUIScreen`, and 1.21.1 leaves `renderBg` abstract, so its override has no `super` call.
- **`extractImage(Font,x,y,w,h,GuiGraphics)` → `renderImage(Font,x,y,GuiGraphics)`**: 1.21.1 gives no tooltip width, so the two tooltip classes lost their horizontal centering and start at the tooltip's left edge, the way vanilla's own `ClientBundleTooltip` does. `getHeight(Font)` → `getHeight()`.
- **Input and misc renames:** `setTooltipForNextFrame(font, lines, Optional.empty(), x, y)` → `renderTooltip(Font, List, Optional, int, int)`, which draws immediately and so must be called last; `KeyEvent` → `keyPressed(int,int,int)`; `hasClickedOutside` gains a button argument; `ResolvableProfile.createResolved(gp)` → `new ResolvableProfile(gp)`; `mc.getDeltaTracker()` → `mc.getTimer()`; `Inventory#getNonEquipmentItems()` → `getContainerSize()` / `getItem(i)`; the 1.21.1 `AbstractContainerScreen` constructor takes no size arguments, so `imageWidth`/`imageHeight` are set in the constructor body.
- **`blit`.** The 11-arg 1.21.1 overload is `(loc, x, y, w, h, u, v, uW, vH, texW, texH)` — `(width, height)` **before** `(u, v)`, the opposite of 26.1's, so those sites are an argument swap, not just a dropped pipeline argument. The 9-arg overload is a drop-in.
- **Fabric.** `ClientTooltipComponentCallback` → `TooltipComponentCallback`; `HudElementRegistry.addFirst/addLast` → one `HudRenderCallback`, so the tutorial, the minigame bar and the quest notifications are drawn from a single callback in the 26.1 registration order (tutorial, bar, notifications) — the relative order those registrations existed to express. `ScreenEvents.afterExtract` → `afterRender`.
- **Neither client entrypoint imported `FishtasticClientSetup`** — the import sat inside the A5 comment block — but A4's menu-screen registrations need its menu-type accessors, so the import moved out, marked as needed by A4 and A5.
- **JEI 19.18.** There is no `AbstractRecipeCategory`, so both categories implement `IRecipeCategory` and hold their own blank drawable; `IRecipeType` → `RecipeType`; `addInputSlot`/`addOutputSlot` → `addSlot(RecipeIngredientRole.*, x, y)`; `add(SlotDisplay.Composite)` → `addIngredients(Ingredient.of(ItemTags.FISHES))` plus `addItemStack(pile)` in the same slot; `CRAFTING_STATION` → `CATALYST`.
- **The `KeyMapping.Category` row landed in A2, not A4.** `FishtasticKeyBinds.CATEGORY` was already the plain string that 26.1's `KeyMapping.Category.register` derives.

**New port-only helpers:**

| Helper | Why |
|---|---|
| `client/FishtasticItemProperties` | The two predicates the generated models test, with a per-loader `Registrar` lambda: vanilla's `ItemProperties.register` is private, and each loader widens it differently (Fabric API's transitively-applied AW makes it public taking `ClampedItemPropertyFunction`; NeoForge patches it public with `ItemPropertyFunction`). The `cast` function is vanilla's own body, copied. |
| portstub `client/FishtasticClientSetup` | The three menu-type accessors only, copied verbatim, so A4's screen registrations compile. Deleted in A5, which brings the real class (item model types) back. |
| portstub `client/renderer/FishtasticGlintState` | A5.4c's flags, so the GUI call sites stay as they are. |
| portstub `client/util/FishPileIcons` | `pileBlocks(..)` returns an empty list — the `pile_of_fish` stacks it builds are `builtin/entity` and render nothing until A5.3 anyway. |
| `@BeforeAll` in `test:client/FishSphereContainerTest` | 1.21.1's `ItemStack.<clinit>` reads `BuiltInRegistries.ITEM`, so the test bootstraps the registry first. |

**Hook stage stays `unit`.** A4 adds no automated check the hook does not already run: its automated half is `:common:test`, which `unit` covers (with tank-shape-gen). The in-game screen checks are driven temporarily and cannot be hooked, and the next stage (`full`) is A6.1's gametests, which cannot run until the testmod trees come off `port/excludes.txt`.

---

## G-1.21.1: gelatin-ui catch-up

**Worktree:** `D:\GitHub\gelatin-ui-worktrees\mc-1.21.1` (to create). Branch **`mc/1.21.1`**, cut from `origin/main` (`6ff90c8`, v1.0.16), which is a clean ancestor of `26.1.2`.

**Method:** cherry-pick the 34 commits `origin/main..26.1.2` in order, **skipping `9e93297`** (the 26.1.2 migration). Each pick either applies cleanly or gets a 1.21.1 re-expression. There are 12 version-bump or build-only commits; pick them for the version history, but reset `mod_version` to the `+1.21.1` scheme (D6) at the end.

| Commit(s) | What | 1.21.1 work |
|---|---|---|
| `b4fecf5` `f8d32a5` `914ae12` `d29128c` `766a341` `c825946` `b56394c` `c7a6c9a` `51fa00f` `36b7864` `0b649e1` `637306f` `7d89544` `0d37212` `df73122` | tests, versioning, publishing, version bumps | pick. Keep the publishing task and fix its game-version tag to 1.21.1. |
| `93787d4` `cf5fe55` `528d8da` `7359b5f` `441ba9c` `7ae476c` `c7bac0b` `4031182` `7df5fd6` `41737af` `a127539` | small fixes (1–15 lines each, none touch the render-state API) | pick. Expect trivial conflicts in the item renderer bounds and sprite blit (`cf5fe55` touches blit arguments, so check it against 1.21.1's `blit` signature). |
| `4cb61bc` text wrapping (613 lines) | 2 render-API lines | pick, then fix the 2 lines against 1.21.1 `GuiGraphics#drawString`/`Font#split` |
| `f78bc6b` scale pivot (699 lines) | 4 render-API lines (pose calls) | pick, then change `Matrix3x2fStack` calls to `PoseStack` (see A4) |
| `4f22fdc` concurrent-modification fixes and deferred tasks | 1 render-API line | pick |
| `e9f8116` culling bounds and ItemTabs alert badges | none | pick. Fishtastic uses `ItemTabs` alert badges (`HAS_ALERT`). |
| `d407ee6` posed player rendering (`PlayerModelRenderer`, `PlayerAvatarRenderer`, `PlayerPoses`, `UI.playerAvatar/playerModel`) | 21 render-API lines, **re-express** | Keep the public API (`UI.playerAvatar(w,h)`, `UI.playerModel(w,h)`, `PlayerPoses`) identical. **1.21.1 implementation:** the puppet is a `RemotePlayer` (MC `RemotePlayer.java:18`) subclass with `getSkin()` overridden (MC `AbstractClientPlayer.java:66`), because 1.21.1 has no `ClientMannequin`. Skin via `Minecraft.getSkinManager().getOrLoad(GameProfile)` (MC `SkinManager.java:82`). Draw like `InventoryScreen.renderEntityInInventory` (MC `InventoryScreen.java:133`) through `EntityRenderDispatcher.render` (`:139`). `PlayerModelRenderer` (the bare model) bakes `ModelLayers.PLAYER` / `PLAYER_SLIM` into a `PlayerModel` and renders it with the skin's `RenderType.entityTranslucent`. Fishtastic's `mixin/HumanoidModelMixin` pose hook applies because the puppet goes through the normal `HumanoidModel.setupAnim` (A5.6). |
| `36c7307` + `5ae6aa4` hi-res item PIP pipeline | 54 + 7 render-API lines, **replace with a no-op** | Keep `HiResItems.item(ctx, stack, x, y, size)` and have it **return `false`**, so callers fall back to the normal path. On 1.21.1 that path is already sharp, because `GuiGraphics.renderItem` draws through the pose instead of blitting a 16×guiScale atlas slot. Delete the `GuiRendererMixin`/`GameRendererMixin` parts and the NeoForge PIP registration. |

**Gelatin-owned mixins:** the 1.0.16 line already has its 1.21.1 `GuiGraphicsMixin`/`IGuiGraphicsExtension`. Check the pick of `36c7307`'s `GuiGraphicsMixin` hunk against it.

**Gate (G-G1):** gelatin-ui `gw build` on JDK 21. Its unit tests (from `b4fecf5` and `4f22fdc`) pass. Its `TestScreen` example opens on both loaders. `publishToMavenLocal` produces `io.github.currenj.gelatinui:gelatinui-{common,fabric,neoforge}:1.0.31+1.21.1`. Fishtastic A4 is the real acceptance test.

---

## A5: Rendering

See [`track-a5-rendering-1.21.1.md`](track-a5-rendering-1.21.1.md): per-item design notes, file lists and the gate.

---

## A6: Gametests and verification (gate G2)

### A6.1: One shared harness (D10)

| 26.1 | 1.21.1 | Files |
|---|---|---|
| Fabric: `net.fabricmc.fabric.api.gametest.v1.GameTest(structure=…, maxTicks=…)` on 263 methods that delegate to `common/src/testmod` bodies | vanilla **`net.minecraft.gametest.framework.GameTest(template = "fishtastic:empty", timeoutTicks = …)`** (MC `GameTest.java:11,23`). One class `testmod:gametest/FishtasticGameTests` (public, with a no-args constructor) is registered by Fabric's `fabric-gametest` entrypoint, which calls vanilla `GameTestRegistry.register(testClass)` (FAPI `impl/gametest/FabricGameTestModInitializer.java`; `FabricGameTest` is optional) **and** by NeoForge's `RegisterGameTestsEvent.register(Class)` (NF `RegisterGameTestsEvent.java:39`), with `@GameTestHolder("fishtastic")` + `@PrefixGameTestTemplate(false)` (NF `gametest/`). | new `testmod:gametest/FishtasticGameTests` (generated mechanically from `fabric-testmod:FishtasticFabricGameTests`), delete `fabric-testmod:FishtasticFabricGameTests` (1,416 lines) and most of `neoforge-testmod:NeoForgeGameTestRegistration` (701 lines, keeping `NeoForgeTestPlayers` if it's still needed) |
| `fabric-gametest-api-v1:empty` structure, NeoForge `GameTestStructureProvider` datagen | one `data/fishtastic/structure/empty.nbt` in common testmod resources (1.21.1 folder name `structure`, singular) | new resource. Delete `neoforge:datagen/GameTestStructureProvider`. |
| 26.1 test instances, `TestEnvironmentDefinition`, `GameTestInstance`, `TestData` | none | removed |
| Avoid `skyAccess`/`manualOnly` | 1.20.1's `@GameTest` lacks them (B6.1), so the shared class doesn't use them either | |

The 6 test names that only the Fabric harness had (`crossShapeNeighborsInSameFamilyConnect`, `giveOrDropThroughRealMenuAfterPickupWhileOpenDoesNotLoseTheFish`, `newShapesConnectToEachOther`, `newShapesConnectToStandard`, `sameShapeNeighborsConnect`, `standardAndReinforcedNeighborsConnect`) now run on NeoForge too.

The 25 shared test-body files in `common/src/testmod` port like main code (A2 renames, plus the `ContainerInput`/`GameRules`/`ItemUseAnimation` rows).

#### A6.1 as built (2026-09-25): one shared harness, green on both loaders at 263

Two of the table's premises did not hold. Both were found by running, not by reading.

**The shared bodies needed less than Appendix A implies, but more than static inspection said.**
`git diff 26.1.2 -- common/src/testmod` is only the A2 rename sweep (82 lines across 15 files; 10
files untouched), and the A2 `sed` had already done the `Identifier` work. Static inspection of the
25 files concluded "20 sites in 4 files, nothing else". The compiler, once the trees were actually
un-excluded, said **576 errors**. What it really wanted was ~30 sites in 9 files:
`Inventory#getNonEquipmentItems` -> the `items` field (14 sites),
`GameTestHelper#getBlockEntity(BlockPos, Class)` -> the one-arg form (5),
`Registry#getValue` -> `get` / `getHolderOrThrow`, `lookupOrThrow` -> `registryOrThrow`,
`ContainerInput` -> `ClickType`, the `GameRules.ADVANCE_WEATHER` family ->
`RULE_WEATHER_CYCLE` + `getBoolean`/`getRule(...).set(...)`, `ItemUseAnimation` -> `UseAnim`,
`ServerLevel#getWeatherData` (absent on 1.21.1; the level data is a `ServerLevelData`),
and `ServerPlayer#gameMode()` (not overridable on 1.21.1; `setGameMode` after construction).
Appendix A's per-file lists overstate the work: several of its hits are `BlockState#getValue`,
`Map.Entry#getValue`, or a method *name*. **576 -> 2 -> 0 errors over three compile passes.**

**One shared class again, and Fabric runs the whole suite.** `FishtasticGameTests` (262 wrappers
generated from the old Fabric harness by `build/a61-gen-shared-harness.py`, which asserts 262 in /
262 out, plus the hand-added Sunset Postcard wrapper) carries vanilla
`@GameTest(template = "fishtastic:empty")`. The table's premise is right
even though the old harness was stale: `net.fabricmc.fabric.api.gametest.v1.GameTest` **does not
exist** in fabric-gametest-api-v1 2.0.5, and that module's own javadoc points at vanilla's
annotation. `:fabric:runGametest` reports **263 tests, 0 failures** (all `classname="fishtastic:empty"`;
the older report's extra `minecraft:always_pass` entry was the Fabric API's own and no longer
appears). Fabric's `TestFunctionsMixin` uses a non-empty `template()` **verbatim** and derives the
mod id from the entrypoint, so no holder is needed there.

Supporting pieces: `FishtasticTestSupport` is the mock-player seam, installed per platform, because
the loaders cannot share vanilla's `makeMockServerPlayerInLevel` - NeoForge's mock connection skips
the configuration handshake that registers its payload channels. `NeoForgeTestPlayers` is therefore
**still needed**, answering the table's "if it's still needed". A new
`common/src/testmod/resources/data/fishtastic/structure/empty.nbt` (8x8x8, `DataVersion 3955` - the
deleted provider hard-coded 4189, "matches MC 26.1.2") carries **explicit tag types**, because
`StructureTemplate.load` asks for `getList("size", 3)` and `getList("palette"|"blocks"|"entities", 10)`
while a bare `new ListTag()` carries element type 0. Both platforms' `testmod` source sets now also
register `common/src/testmod/resources` as a resource dir; previously only `java` was wired, so the
structure would not have been on the classpath at all.

**The NeoForge half is blocked on an API fact the design did not account for.** The 26.1
registration could not be trimmed: `TestData`, `GameTestInstance` and `TestEnvironmentDefinition`
**do not exist in NeoForge 21.1.209**, and `RegisterGameTestsEvent` exposes only `register(Class)`
and `register(Method)`. It was replaced with the annotation-driven path, which then failed twice in
sequence:

1. `Enabled Gametest Namespaces: [fishtastic]` -> `IllegalArgumentException: No test functions were
   given!`, and the JVM then **hangs** rather than exiting - the very hang the hook's `full` stage
   sidesteps by never running this task. `GameTestHooks.getTemplateNamespace` falls back to
   `"minecraft"` without a `@GameTestHolder`, so a fishtastic-only filter drops every test.
2. With the filter emptied: `ResourceLocationException: Non [a-z0-9/._-] character in path of
   location: minecraft:fishtasticgametests.fishtastic:empty`. `prefixGameTestTemplate` defaults to
   **true** when the annotation is absent, so NeoForge prefixes the class simple name onto the raw
   template (`fishtasticgametests.` + `fishtastic:empty`) and the result is not a valid id.

Both are one thing: **NeoForge 21.1 requires `@GameTestHolder` and `@PrefixGameTestTemplate` on the
class that *declares* the test methods**, and a class compiled for both loaders cannot carry
`net.neoforged` annotations (`common/build.gradle` has no NeoForge dependency at all, and
`fabric/build.gradle` compiles the same file). The obvious escape - a thin NeoForge subclass
carrying them - is **ruled out**: `GameTestRegistry.register(Class)` and
`RegisterGameTestsEvent.register(Class)` both use `getDeclaredMethods()`, so a subclass registers
zero tests.

**Resolved 2026-09-25 - the owner chose (b), and two further findings followed.** The shared harness
carries the two NeoForge annotations, compiled against **PORT-ONLY stubs**:
`common/src/neoforge-gametest-annotations/java` holds verbatim-shaped copies of `GameTestHolder` and
`PrefixGameTestTemplate`, published as a jar by `common/build.gradle`'s
`neoforgeGametestAnnotationsApi`, which both testmod source sets take as `testmodCompileOnly`. At
runtime NeoForge's own annotation classes are the ones loaded, and on Fabric the annotation types are
simply absent, so the JVM omits them - which is why the stubs must never reach a runtime classpath: a
second copy of the class would shadow NeoForge's and make its own lookup miss.

That fixed registration, and **the next failure was informative rather than discouraging**: NeoForge
went from 0 tests to **263 running**, then crashed on
`ResourceLocationException: ... fishtastic:fishtastic:empty`. NeoForge builds the template id as
`getTemplateNamespace(method) + ":" + gameTest.template()` - it wants a **bare path** and takes the
namespace from the holder - while Fabric parses the template verbatim as a ResourceLocation, so the
same bare path lands in `minecraft:` there. The template is therefore now the bare `fishtastic_empty`,
and the all-air 8x8x8 ships twice: `data/fishtastic/structure/fishtastic_empty.nbt` (what NeoForge
resolves) and `data/minecraft/structure/fishtastic_empty.nbt` (what Fabric resolves). Both sit in
src/testmod/resources, which only the testmod source sets read, so neither ships and the mod-prefixed
name cannot collide with a vanilla or datapack id. `@PrefixGameTestTemplate(false)` was confirmed
working on the way: without it on a throwaway diagnostic class, NeoForge resolved
`fishtastic:neoforgemockplayerdiagnostic.fishtastic_empty`.

**The last 50 failures were a port bug of A6.1's own making, found with a temporary diagnostic.** All
50 carried one message, `Cannot invoke "ServerGamePacketListenerImpl.latency()" because
"arg.connection" is null`, and the gametest framework logs no stack with it, so a throwaway
NeoForge-only test reproduced it inside a try/catch and printed the trace:

    ClientboundPlayerInfoUpdatePacket$Entry.<init>
      <- ClientboundPlayerInfoUpdatePacket.<init>
      <- ServerPlayerGameMode.changeGameModeForPlayer
      <- ServerPlayer.setGameMode
      <- NeoForgeTestPlayers.makeMockServerPlayerInLevel

That is A6.1's own edit to `NeoForgeTestPlayers`: 26.1 overrides `ServerPlayer#gameMode()`, and
replacing it with `setGameMode(GameType.CREATIVE)` looked equivalent but is not - on 1.21.1
`setGameMode` goes through `ServerPlayerGameMode#changeGameModeForPlayer`, which broadcasts a
player-info packet whose entry constructor reads `player.connection.latency()`, and a mock player has
no connection until it has joined. `NeoForgeTestPlayers` now overrides `isSpectator()`/`isCreative()`
exactly as vanilla's own `GameTestHelper.makeMockServerPlayerInLevel` does, which is also what keeps
the two loaders behaving the same here. **For the 1.20.1 branch: `setGameMode` on a pre-join player is
a trap.**

**Verified on both loaders:** `:fabric:runGametest` and `:neoforge:runGametest` each report
**263 tests, 0 failures** (262 shared + the Sunset Postcard test). `port/excludes.txt` is empty, so
A6.1 is done; A6.2's green bar and the hook stage are what remain.

**Un-blocks with A6.1:** the six Fabric-only tests (`crossShapeNeighborsInSameFamilyConnect`,
`giveOrDropThroughRealMenuAfterPickupWhileOpenDoesNotLoseTheFish`, `newShapesConnectToEachOther`,
`newShapesConnectToStandard`, `sameShapeNeighborsConnect`, `standardAndReinforcedNeighborsConnect`)
are in the shared class, so they run on both loaders once the NeoForge half lands. The **Sunset
Postcard gametest** A2.8.c deferred is written: `dayTimeAdvancesAtTheAppliedRate` in
`StormCharmGameTests` parks day time at the dawn window start with a Sunset Postcard in a mock
player's inventory, reads the live rate from `SunsetExtensionHandler.currentRate()` rather than
hard-coding 0.5 (the shipped charm's `sunset_extension_seconds` of 60 against a 100-second window
gives 0.625), and asserts day time advanced by `rate x 200` +/- 1 for the accumulator's remainder.
It passes in the Fabric run (263 tests, 0 failures).

### A6.2: Green bar (G2)

| Check | Command | Expected |
|---|---|---|
| Build | `gw build` | success. `port/excludes.txt` and `common/src/portstub/` are **deleted**. |
| Unit | `gw :common:test :fishsim:test :tools:tank-shape-gen:test` | 78; 163 + 1 skipped; 21,955 |
| Fabric gametests | `gw :fabric:runGametest` | **263 passed** (the 26.1.2 count), plus the new sunset-rate test (A2.8.c) = **264** |
| NeoForge gametests | `gw :neoforge:runGameTestServer` **in the background** (hang caveat) | **264 passed** (same class) |
| Datagen | `gw :fabric:runDatagen && git status --porcelain common/src/main/resources` | empty |
| Refmaps | A1.5 | both present, and every mixin in `fishtastic.mixins.json` has a refmap entry |
| fishsim behaviour parity (R4) | `gw :fishsim:run…` headless export (L, 12 fish, seed 42, 3000 ticks) | byte-identical CSV/PNG/GIF to 26.1.2 |
| Hook | commit | `port-stage` → `full` |

### A6.3: In-game playtest
The owner, on both loaders, against the pass 1 checklist (minigame, all rods, bait, hooks and charms, every tank shape and cosmetic, swarm, bubbles, quests, shop, encyclopedia, leaderboards and podium, compost, organizer, backups). **Add:** the Sunset Postcard (the sun visibly slows at dawn and dusk, with no stutter) and every quality-outline tier in the GUI, on the ground and in frames.

#### A6.3 as found (2026-09-25): two defects, both fixed, both compiler-invisible

The owner's playtest passed everything except two visual defects. Neither exists on `26.1.2`, and
neither could be caught by compiling, which is why A1–A6.2 missed both.

**1. Tooltip slot backgrounds drew vanilla's missing texture.** `ClientRodGearTooltip` and
`ClientFishTankMaterialsTooltip` each blitted `minecraft:container/bundle/slot_background` — a
**24x24** sprite that exists only on `26.1.2`. 1.21.1 ships no sprite by that name at all (its
bundle tooltip draws from `container/bundle/slot` and `blocked_slot`, both **18x20**; verified
against `~/.gradle/caches/fabric-loom/1.21.1/minecraft-client.jar`), so `blitSprite` fell back to
`MissingSprite` — the purple/black square behind the ghost bait/hook/charm icons. Fixed by using
1.21.1's own `container/bundle/slot` at its native size: `SLOT_SIZE = 24` became
`SLOT_WIDTH = 18` / `SLOT_HEIGHT = 20`, and the icons centre via `(SLOT_WIDTH - ICON_SIZE) / 2`
rather than a hardcoded `+4`. The owner chose native size over stretching the art to keep the 24px
layout, accepting a 58px slot row where `26.1.2`'s is 76px. **These are the only two `blitSprite`
calls in the port**; the other 36 `withDefaultNamespace` refs are registry IDs or
`textures/gui/container/generic_54.png`, all confirmed present.

**2. The leaderboard podium ordered by depth instead of by paint order.** All three of the owner's
symptoms — the player behind the pedestal, the fish pile behind the podium block, the block stack
out of order — are one cause. gelatin's `UIContainer.renderChildren` paints in insertion order with
no per-child z on **both** branches (byte-identical), but what each child *writes* differs. On
`26.1.2` the pose is a 2D `Matrix3x2fStack` and items/players are deferred picture-in-picture, so
nothing writes depth and insertion order alone decides. On 1.21.1 the pose is a real 3D `PoseStack`
and every leaf draws immediately with its **own baked-in z**: vanilla items 150
(`GuiGraphics#renderItem`, `GuiGraphics.java:555`), gelatin's posed player 100
(`PlayerModelRenderer.Z_OFFSET`), vanilla's entity-in-inventory 50 (`InventoryScreen.java:137`).
Larger z is nearer, so depth overrode paint order and the pedestal (150) covered the player (100)
whatever order they were added in. `LeaderboardScreen` is innocent — byte-identical to `26.1.2`
apart from renames.

Fixed with a new **opt-in gelatin seam** rather than a change to the paint loop:
`IUIElement#setZOffset` has the container apply an extra pose translate around that child and its
subtree (`pushZOffset`/`popZOffset` on `IRenderContext`, implemented in `MinecraftRenderContext`),
and `LeaderboardScreen` sets `PODIUM_BLOCK_Z_STEP = 32` per block and `PODIUM_PLAYER_Z_OFFSET = 400`
on the player. A global per-child z step was rejected: it would have to dominate the baked-in
150/100/50 **and** stay inside the GUI's ±10000 ortho depth budget
(`GameRenderer.java:1057-1063`: `setOrtho(0, w, h, 0, 1000, 21000)` with a `-11000` modelview
translate) once it accumulates down a tree — a 50-row list at step 200 lands exactly on the clip
limit. Gelatin change is **`gelatinui 1.0.32+1.21.1`** (branch `mc/1.21.1`, commit `e00acee`): the
API, `mod_version` 1.0.31 → 1.0.32, and a new `UIContainerZOffsetTest` pinning push order, the
zero-skip, nesting and push/pop balance. Gelatin signs every publication unconditionally and this
machine has no usable key, so the mavenLocal publish ran with the sign tasks temporarily disabled
and `build.gradle` reverted immediately (gelatin tree clean at `e00acee`).

**Not verified in-game.** Both fixes compile and the seam is unit-tested, but the podium needs live
leaderboard data, so its ordering could not be reproduced headlessly — **A6.3 stays open pending the
owner's re-check.** One thing for that re-check: the stack now paints **top block in front**, which
is what `buildBlockColumn`'s own comment documents as intended and what `26.1.2` does. If the owner
expected the opposite, that is a separate design decision, not a bug in this fix.

**Track B implication.** 1.20.1's `GuiGraphics` is the same immediate-mode depth-writing path, so
this bug will exist there too: gelatin's 1.20.1 line needs the same `setZOffset` API before the
podium can be fixed on the Forge line.

### A6.4 → G2
Record G2 in `checklist.md`. `port/1.20.1` is cut from here.

---

## A7: Release

| Item | Change |
|---|---|
| `.github/workflows/build.yml`, `publish.yml` | JDK 25 → **21**. Gradle `-Dorg.gradle.java.home` isn't needed in CI. |
| `build.gradle` `publishCurseForge` | `addGameVersion('1.21.1')` comes from `minecraft_version` already. Loader tags `Fabric`, `NeoForge`. |
| `CHANGELOG.md` | `2.0.1+1.21.1`: parity with 2.0.1, known limitations (no world upgrade from 1.20.1, D4) |
| `D:\GitHub\fishtastic-worktrees\build-all.ps1` | create it (adapted from `apt-ores-worktrees/build-all.ps1`) so it builds `26.1.2` and `port/1.21.1` and collects jars into `dist\` |
| `checklist.md` | mark A7 and record the released commit |

---

## Appendix A: exact file lists for the mechanical buckets

Paths as in the conventions. Produced by grep over `3b8427e4` (excluding `mcp/**` and cool-cam). The permissions list is kept for reference only: since S6 those files call `FishtasticPermissions.gamemaster()` and don't change.

**`Identifier` → `ResourceLocation` (106):** `Fishtastic`, `FishtasticItemData`, `FishtasticItems`, `architectury/IRegistrationApi`, `architectury/RegistrationApiSided`, `blockentity/FishTankBlockEntity`, `client/CosmeticTransformLoader`, `client/ElectricFishOrganizerScreen`, `client/FishEncyclopediaClientCache`, `client/FishEncyclopediaClientHelper`, `client/FishEncyclopediaScreen`, `client/FishTankAssemblyScreen`, `client/FishTankBrowserScreen`, `client/FishtasticClientSetup`, `client/LeaderboardScreen`, `client/NotificationPriority`, `client/QuestClientCache`, `client/QuestLogScreen`, `client/QuestProgressEvent`, `client/QuestProgressNotification`, `client/QuestProgressNotificationManager`, `client/ShapeGalleryPanel`, `client/SilhouetteItemButton`, `client/ThinProgressBar`, `client/ZoneIconRectangle`, `client/compositemodel/{BlockModelPathResolver,BlockstateModelScanner,BlockstateRedirectRegistry,CompositeTextureHelper}`, `client/renderer/{CosmeticStructureItemModel,FishPileBlockItemModel,FishTankBlockEntityRenderer,FishtasticBlackOutlineEffect,FishtasticItemOutlineAtlas,FishtasticRenderPipelines,FishtasticSilhouetteEffect,FishtasticTextureOutlineEffect,ZoneIconTextures}`, `client/tooltip/{ClientFishTankMaterialsTooltip,ClientRodGearTooltip}`, `client/util/FishPileIcons`, `command/{FishProfileCommand,FollowFishStubCommand,TemperamentCommand,TestQuestNotifyCommand}`, `compat/jei/FishtasticJeiPlugin`, `data/{EncyclopediaRewardSection,ShopEntry}`, `fishtank/{CosmeticTransforms,FishTankFrameType,FishTankShape}`, `item/FishopediaItem`, `itemeffect/ItemEffect`, `itemeffect/condition/{ComponentCondition,ComponentValueCondition,ItemCondition,ItemTagCondition}`, `network/{ClaimEncyclopediaRewardPacket,CompleteQuestPacket,FishEncyclopediaSyncPacket,FishtasticPackets,LeaderboardEntry,PurchaseShopEntryPacket,QuestSyncPacket,RecentCatch,SetAssemblyShapePacket,StartFishingMinigamePacket}`, `server/{FishCatchSavedData,FishingMinigameManager,PlayerQuestState,QuestTracker}`, `tutorial/TutorialManager`, `util/{FishingMinigameAnimation,Utility}`; `fabric:{FishtasticFabricClient,datagen/FishTankGlassModelProvider,datagen/FishtasticItemTagProvider,datagen/FishtasticModelProvider,datagen/FishtasticRecipeProvider,datagen/ShopEntryFromQuestProvider,fishtank/BlockstateModelRedirectPlugin,fishtank/FishTankBlockStateModelFabric,fishtank/FishTankItemModelFabric,fishtank/FishTankModelFabric}`, `fabric/src/main/java/…/architectury/fabric/FabricRegistrationApi`; `neoforge:{FishtasticNeoForgeClient,fishtank/BlockModelPathResolver,fishtank/FishTankBlockStateModel,fishtank/FishTankItemModel,fishtank/FishTankModel,fishtank/FishTankPartBlacklistChecker}`, `neoforge/src/main/java/…/architectury/neoforge/NeoForgeRegistrationApi`, `neoforge-testmod:NeoForgeGameTestRegistration`; `test:client/FishSphereContainerTest`; `testmod:gametest/{FishCatchBackupsGameTests,FishCatchDataGameTests,FishEncyclopediaClientGameTests,ItemEffectConditionGameTests,LifetimeQuestProgressGameTests,MathUtilGameTests,PacketRoundTripGameTests,PlayerQuestStateGameTests,QuestLogVisibilityGameTests,QuestTrackerGameTests,ShopEntryGameTests,TutorialManagerGameTests}`.

**`lookupOrThrow` → `registryOrThrow` (35):** `block/FishTankBlock`, `blockentity/ElectricFishOrganizerBlockEntity`, `client/{FishEncyclopediaClientHelper,FishEncyclopediaScreen,FishTankAssemblyScreen,NotificationPriority,QuestLogScreen,QuestProgressNotification,ShapeGalleryPanel}`, `client/renderer/{CosmeticStructureItemModel,FishTankBlockEntityRenderer}`, `command/{DebugEncyclopediaCommand,DebugShapesCommand,FishProfileCommand,FishZoneCommand,QuestsCommand,TemperamentCommand}`, `data/{QuestObjective,SwarmConfig,TankCapacity}`, `fabric:datagen/FishtasticRecipeProvider`, `item/{FishopediaItem,QuestBookItem}`, `itemeffect/ItemEffectManager`, `network/{CompleteQuestPacket,PurchaseShopEntryPacket,SetAssemblyShapePacket}`, `server/{FishCatchSavedData,FishingMinigameManager,QuestTracker}`, `testmod:gametest/{CapstoneRewardGameTests,CatchCelebrationGameTests,FishEncyclopediaClientGameTests,QuestSatisfiabilityGameTests,QuestTrackerGameTests}`.

**`Registry#getValue` → `get` (13):** `FishtasticDispenseBehaviors`, `FishtasticItemData`, `block/{FishPileBlock,FishTankBlock,MarineCompostBlock}`, `blockentity/{FishTankBlockEntity,MarineCompostBlockEntity}`, `client/FishEncyclopediaScreen`, `client/renderer/FishTankBlockEntityRenderer`, `data/ShopEntry`, `server/FishingMinigameManager`, `testmod:gametest/{MarineCompostGameTests,QuestTrackerGameTests}`.

**Permissions (20):** `command/{BackupCommand,CelebrationCommand,CleanupGoalCommand,CosmeticCommand,DebugEncyclopediaCommand,DebugFishDataCommand,DebugShapesCommand,FishProfileCommand,FishZoneCommand,ForceQualityCommand,PoseDebugCommand,QuestsCommand,SetFishQualityCommand,SetItemSizeCommand,SetTankShapeCommand,SimulateFishingCommand,TemperamentCommand,TestQuestNotifyCommand,TokenBalanceCommand,TutorialCommand}`.

**`FabricPackOutput` → `FabricDataOutput` (12):** `fabric:datagen/{CosmeticStructureProvider,FishTankFrameModelProvider,FishTankGlassModelProvider,FishTankSandModelProvider,FishtasticBlockLootTableProvider,FishtasticBlockTagProvider,FishtasticItemTagProvider,FishtasticModelProvider,FishtasticRecipeProvider,ItemEffectProvider,QuestProvider,ShopEntryFromQuestProvider}`.

**Reproduce any list:** `grep -rlE '<pattern>' common/src fabric/src neoforge/src --include=*.java | grep -v '/mcp/\|coolcam'`.
