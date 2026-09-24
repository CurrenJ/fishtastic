# Track A: 26.1.2 → 1.21.1 (pass 2)

> Parent: [`README.md`](README.md). Rendering (A5) is in [`track-a5-rendering-1.21.1.md`](track-a5-rendering-1.21.1.md).
> Baseline `26.1.2` @ `298279e1`. Branch `port/1.21.1`, worktree `D:\GitHub\fishtastic-worktrees\mc-1.21.1`.

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

---

## A2: Core and server logic

**Goal:** `:common` compiles with only the 62 files excluded, unit tests pass, and both platforms compile their server side.

### A2.0: Set up the real exclusion list and stubs
Replace A1's everything-list with the 62-file list above, create the boundary stubs, and strip the excluded mixins from `fishtastic.mixins.json` at build time (driven by the same file). **Order within A2:** A2.0 → A2.1 (a mechanical pass across everything that compiles) → A2.2 → A2.3 → A2.4 → A2.5 → A2.6 → A2.7 → A2.8 → A2.9 → A2.10. After A2.1, iterate with `gw :common:compileJava` and let the compiler order the rest.

### A2.1: Renames and registry access (mechanical)

| 26.1 API | 1.21.1 API (lands on) | Files |
|---|---|---|
| `net.minecraft.resources.Identifier` (+ `fromNamespaceAndPath`, `parse`, `withDefaultNamespace`, `tryParse`) | `ResourceLocation`, with the same four statics (MC `ResourceLocation.java:48,52,56,61`) | **106** files (list in appendix A). A word-boundary `sed`, then fix comments by hand. |
| `commands.arguments.IdentifierArgument` | `ResourceLocationArgument` | `command/FishProfileCommand`, `command/FollowFishStubCommand`, `command/TemperamentCommand` |
| `registryAccess().lookupOrThrow(K)` returning `Registry<T>` (1.21.2 rename) | `registryOrThrow(K)` returning `Registry<T>` (MC `RegistryAccess.java:26`). Note: `HolderLookup.Provider.lookupOrThrow` **still exists** on 1.21.1 but returns `HolderLookup.RegistryLookup<T>` (`HolderLookup.java:34`). A missed rename therefore fails to compile at the assignment instead of silently binding. | 35 files (appendix A) |
| `Registry#getValue(id / key)` | `Registry#get(ResourceLocation)` / `get(ResourceKey)` returning `T` (MC `Registry.java:70,73`) | 13 files (appendix A) |
| `Registry#get(ResourceKey)` returning `Optional<Holder.Reference<T>>` | `getHolder(ResourceKey)` (MC `Registry.java:143`) | found by the compiler (a type mismatch at every site) |
| `requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))` (1.21.11 permission overhaul) | `requires(src -> src.hasPermission(Commands.LEVEL_GAMEMASTERS))` (MC `CommandSourceStack.java:390`, `Commands.java:144`) | 20 command files (appendix A). With seam S6 (D9) this becomes a one-line change in a `FishtasticPermissions.gamemaster()` helper. |
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
| `useItemOn(...)` returning `InteractionResult` | returns **`ItemInteractionResult`** (MC `BlockBehaviour.java:197`). Map `InteractionResult.SUCCESS` → `ItemInteractionResult.SUCCESS`, and `PASS` → `PASS_TO_DEFAULT_BLOCK_INTERACTION` (vanilla's own mapping) | `block/{ElectricFishOrganizerBlock,FishPileBlock,FishTankAssemblyBlock,FishTankBlock,MarineCompostBlock}` (the two that override `useItemOn`) |
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
| `SavedDataType<FishCatchSavedData>(id, ctor, codec, DataFixTypes)`, `storage.computeIfAbsent(TYPE)` | `SavedData.Factory<>(FishCatchSavedData::new, FishCatchSavedData::load, null)` (MC `SavedData.java:49`), `storage.computeIfAbsent(FACTORY, id)` (MC `DimensionDataStorage.java:43`). `save(CompoundTag, Provider)` (MC `SavedData.java:19`) encodes the same `CODEC` through `NbtOps` under a `"data"` key. `load` decodes it. Keep the **same `id` string**, so the file name matches 26.1.2 (D4 makes this cosmetic, but it keeps `/fishtastic backup` paths identical). | `server/FishCatchSavedData` |
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
| **`ItemEffect` split.** 26.1 `itemeffect/ItemEffect` is a datapack record that also carries render resources (`GpuBuffer outlineParamsBuffer`, `RenderPipeline outlinePipeline`, a `Std140Builder` writer) | Split it: the record keeps only data fields and codec, identical on every branch, and becomes a small **S4 seam candidate on 26.1.2**. The render resources move to a client-side `client/renderer/ItemEffectRenderData` map keyed by the effect's id, built in A5.4. Until A5.4 the map is a stub. The spike (`d3ded9da:ItemEffect.java`) already shows the 1.21.1 field set: `outline_color`, `outline_falloff`, `outline_width`, `outline_opacity`, `outline_pinwheel`, same JSON. | `itemeffect/ItemEffect`, `itemeffect/ItemEffectManager` |

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
| **A2.8.b `MoonPhase`** (26.1 enum, serialized in `FishProfile` JSON) | A Fishtastic-owned `data/FishMoonPhase` enum with the **same eight serialized names** (`full_moon` … `waxing_gibbous`) and `fromIndex(level.getMoonPhase())` (MC `LevelTimeAccess.java:16`). **Best landed on 26.1.2 first as seam S6 (D9)**, so the 64 profile JSONs and `FishProfile` are identical everywhere. | `data/FishProfile`, `item/FishtasticFishItem`, `server/FishingMinigameManager` |
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

Paths as in the conventions. Produced by grep over `298279e1` (excluding `mcp/**` and cool-cam).

**`Identifier` → `ResourceLocation` (106):** `Fishtastic`, `FishtasticItemData`, `FishtasticItems`, `architectury/IRegistrationApi`, `architectury/RegistrationApiSided`, `blockentity/FishTankBlockEntity`, `client/CosmeticTransformLoader`, `client/ElectricFishOrganizerScreen`, `client/FishEncyclopediaClientCache`, `client/FishEncyclopediaClientHelper`, `client/FishEncyclopediaScreen`, `client/FishTankAssemblyScreen`, `client/FishTankBrowserScreen`, `client/FishtasticClientSetup`, `client/LeaderboardScreen`, `client/NotificationPriority`, `client/QuestClientCache`, `client/QuestLogScreen`, `client/QuestProgressEvent`, `client/QuestProgressNotification`, `client/QuestProgressNotificationManager`, `client/ShapeGalleryPanel`, `client/SilhouetteItemButton`, `client/ThinProgressBar`, `client/ZoneIconRectangle`, `client/compositemodel/{BlockModelPathResolver,BlockstateModelScanner,BlockstateRedirectRegistry,CompositeTextureHelper}`, `client/renderer/{CosmeticStructureItemModel,FishPileBlockItemModel,FishTankBlockEntityRenderer,FishtasticBlackOutlineEffect,FishtasticItemOutlineAtlas,FishtasticRenderPipelines,FishtasticSilhouetteEffect,FishtasticTextureOutlineEffect,ZoneIconTextures}`, `client/tooltip/{ClientFishTankMaterialsTooltip,ClientRodGearTooltip}`, `client/util/FishPileIcons`, `command/{FishProfileCommand,FollowFishStubCommand,TemperamentCommand,TestQuestNotifyCommand}`, `compat/jei/FishtasticJeiPlugin`, `data/{EncyclopediaRewardSection,ShopEntry}`, `fishtank/{CosmeticTransforms,FishTankFrameType,FishTankShape}`, `item/FishopediaItem`, `itemeffect/ItemEffect`, `itemeffect/condition/{ComponentCondition,ComponentValueCondition,ItemCondition,ItemTagCondition}`, `network/{ClaimEncyclopediaRewardPacket,CompleteQuestPacket,FishEncyclopediaSyncPacket,FishtasticPackets,LeaderboardEntry,PurchaseShopEntryPacket,QuestSyncPacket,RecentCatch,SetAssemblyShapePacket,StartFishingMinigamePacket}`, `server/{FishCatchSavedData,FishingMinigameManager,PlayerQuestState,QuestTracker}`, `tutorial/TutorialManager`, `util/{FishingMinigameAnimation,Utility}`; `fabric:{FishtasticFabricClient,datagen/FishTankGlassModelProvider,datagen/FishtasticItemTagProvider,datagen/FishtasticModelProvider,datagen/FishtasticRecipeProvider,datagen/ShopEntryFromQuestProvider,fishtank/BlockstateModelRedirectPlugin,fishtank/FishTankBlockStateModelFabric,fishtank/FishTankItemModelFabric,fishtank/FishTankModelFabric}`, `fabric/src/main/java/…/architectury/fabric/FabricRegistrationApi`; `neoforge:{FishtasticNeoForgeClient,fishtank/BlockModelPathResolver,fishtank/FishTankBlockStateModel,fishtank/FishTankItemModel,fishtank/FishTankModel,fishtank/FishTankPartBlacklistChecker}`, `neoforge/src/main/java/…/architectury/neoforge/NeoForgeRegistrationApi`, `neoforge-testmod:NeoForgeGameTestRegistration`; `test:client/FishSphereContainerTest`; `testmod:gametest/{FishCatchBackupsGameTests,FishCatchDataGameTests,FishEncyclopediaClientGameTests,ItemEffectConditionGameTests,LifetimeQuestProgressGameTests,MathUtilGameTests,PacketRoundTripGameTests,PlayerQuestStateGameTests,QuestLogVisibilityGameTests,QuestTrackerGameTests,ShopEntryGameTests,TutorialManagerGameTests}`.

**`lookupOrThrow` → `registryOrThrow` (35):** `block/FishTankBlock`, `blockentity/ElectricFishOrganizerBlockEntity`, `client/{FishEncyclopediaClientHelper,FishEncyclopediaScreen,FishTankAssemblyScreen,NotificationPriority,QuestLogScreen,QuestProgressNotification,ShapeGalleryPanel}`, `client/renderer/{CosmeticStructureItemModel,FishTankBlockEntityRenderer}`, `command/{DebugEncyclopediaCommand,DebugShapesCommand,FishProfileCommand,FishZoneCommand,QuestsCommand,TemperamentCommand}`, `data/{QuestObjective,SwarmConfig,TankCapacity}`, `fabric:datagen/FishtasticRecipeProvider`, `item/{FishopediaItem,QuestBookItem}`, `itemeffect/ItemEffectManager`, `network/{CompleteQuestPacket,PurchaseShopEntryPacket,SetAssemblyShapePacket}`, `server/{FishCatchSavedData,FishingMinigameManager,QuestTracker}`, `testmod:gametest/{CapstoneRewardGameTests,CatchCelebrationGameTests,FishEncyclopediaClientGameTests,QuestSatisfiabilityGameTests,QuestTrackerGameTests}`.

**`Registry#getValue` → `get` (13):** `FishtasticDispenseBehaviors`, `FishtasticItemData`, `block/{FishPileBlock,FishTankBlock,MarineCompostBlock}`, `blockentity/{FishTankBlockEntity,MarineCompostBlockEntity}`, `client/FishEncyclopediaScreen`, `client/renderer/FishTankBlockEntityRenderer`, `data/ShopEntry`, `server/FishingMinigameManager`, `testmod:gametest/{MarineCompostGameTests,QuestTrackerGameTests}`.

**Permissions (20):** `command/{BackupCommand,CelebrationCommand,CleanupGoalCommand,CosmeticCommand,DebugEncyclopediaCommand,DebugFishDataCommand,DebugShapesCommand,FishProfileCommand,FishZoneCommand,ForceQualityCommand,PoseDebugCommand,QuestsCommand,SetFishQualityCommand,SetItemSizeCommand,SetTankShapeCommand,SimulateFishingCommand,TemperamentCommand,TestQuestNotifyCommand,TokenBalanceCommand,TutorialCommand}`.

**`FabricPackOutput` → `FabricDataOutput` (12):** `fabric:datagen/{CosmeticStructureProvider,FishTankFrameModelProvider,FishTankGlassModelProvider,FishTankSandModelProvider,FishtasticBlockLootTableProvider,FishtasticBlockTagProvider,FishtasticItemTagProvider,FishtasticModelProvider,FishtasticRecipeProvider,ItemEffectProvider,QuestProvider,ShopEntryFromQuestProvider}`.

**Reproduce any list:** `grep -rlE '<pattern>' common/src fabric/src neoforge/src --include=*.java | grep -v '/mcp/\|coolcam'`.
