# Track A5: rendering on 1.21.1 (pass 2 design notes)

> Parent: [`README.md`](README.md), [`track-a-1.21.1.md`](track-a-1.21.1.md). Spike: `docs/spike-1.21.1-rendering.md` on `spike/1.21.1-rendering` (`362b5255`).
> Written **before any A5 code**. Each section follows the house style of `docs/fish-tank-rendering.md` and `docs/item-effect-rendering.md`: what 26.1.2 does, the constraint on 1.21.1, the chosen approach and the rejected alternatives, the file map with verified 1.21.1 signatures, and how it's verified.
> 1.20.1 inherits all of this (track B, B5.2). Its deltas are noted inline as **[1.20.1]**.

1.21.1 is **immediate mode**. There is no extract/submit split, no `RenderPipeline`, no GPU buffer abstraction, and no GUI render state. Every hook below runs with the `ItemStack`, entity or block entity in scope and a `MultiBufferSource` in hand. Most 26.1 side channels (identity maps keyed on quads lists, thread-locals across the submit boundary, render-state fields) exist only to carry context across that split, so on 1.21.1 they **go away rather than get ported**. That's the main reason the A5 code will be smaller than 26.1.2's.

**Order within A5** (each step unblocks the next visual check):
A5.0 → A5.5 (particles, trivial) → A5.1 (BER, most visible) → A5.2 (tank model) → A5.3 (item rendering) → A5.6 (entity hooks) → A5.4 (effects, which reuse A5.0 and the spike) → A5.7.

---

## A5.0: Shared infrastructure

**Shaders.** Spike finding 1: vanilla 1.21.1 `ShaderInstance` only loads from the `minecraft` namespace. Modded core shaders go through the loader:
- Fabric: `CoreShaderRegistrationCallback.EVENT.register(ctx -> ctx.register(id, VertexFormat, Consumer<ShaderInstance>))` (FAPI `client/rendering/v1/CoreShaderRegistrationCallback`)
- NeoForge: `RegisterShadersEvent#registerShader(new ShaderInstance(provider, ResourceLocation, VertexFormat), Consumer)` (NF `client/event/RegisterShadersEvent`; NeoForge adds the `ResourceLocation` constructor)

Both call one shared seam, **`client/renderer/FishtasticShaders.registerAll(BiConsumer<ResourceLocation, VertexFormat>, …)`**. It's taken from the spike as is. The JSON program files live at `assets/fishtastic/shaders/core/<name>.json` with `"vertex": "fishtastic:<name>"`, following the spike's `outline_bake.json`.
**[1.20.1]** Same registration APIs. Forge `RegisterShadersEvent` and the Fabric callback both exist (checked in the 47.4.23 and 0.92.12 sources).

**Render types.** 1.21.1 `RenderType.create(String, VertexFormat, Mode, int, boolean, boolean, CompositeState)` (MC `RenderType.java:1114`), with `CompositeState.builder()` (`:1303`). The `RenderStateShard` constants we need (`TRANSLUCENT_TRANSPARENCY`, `COLOR_WRITE`, `NO_CULL`, `LIGHTMAP`, `OVERLAY`, `ShaderStateShard`) are `protected`, so all Fishtastic render types live in one **`client/renderer/FishtasticRenderTypes extends RenderType`** holder. That's the standard subclass trick, and it means no access-widener entries. It replaces 26.1's `FishtasticRenderPipelines` (pipelines) **and** the `rendertype.RenderType create` AW line.

**Rejected:** a mixin accessor on `RenderStateShard`. That's more moving parts for the same result.

---

## A5.1: Fish tank BER

**26.1.2:** `FishTankBlockEntityRenderer implements BlockEntityRenderer<FishTankBlockEntity, FishTankRenderState>`. `extractRenderState` snapshots the BE, then `submit(state, PoseStack, SubmitNodeCollector, CameraRenderState)` submits:
- the water-fill custom geometry (`submitCustomGeometry` with `WATER_FILL_RENDER_TYPE`)
- fish (`ItemStackRenderState.submit`, one per swimmer, driven by `TankFlockAdapter` + `FishAnimator`)
- cosmetics (`BlockModelRenderState.submit` for block cosmetics, `submitModel(ChestModel, …)` for chests)
- outlines on quality fish (`FishtasticWorldOutlineRenderer.submitOutline`)
- particles spawned from the render path (bubbles, furnace, campfire)

**1.21.1 constraint:** `BlockEntityRenderer<T>` has one method, `render(T, float partialTick, PoseStack, MultiBufferSource, int light, int overlay)` (MC `BlockEntityRenderer.java:12`), plus `shouldRenderOffScreen(T)` / `getViewDistance()` / `shouldRender(T, Vec3)` (`:14-22`). There's no render state.

**Approach: collapse extract and submit into `render`, and keep everything that computes positions.** S4 already split "what to draw" from "emit vertices" for the swarm, animation and bubble emission, so:
- `FishTankRenderState` (46 lines) stops being a render state and becomes a **per-BE client cache**, still built by the existing `extractRenderState` body, which is renamed `snapshot(BE, partialTick)` and called at the top of `render`. That keeps `resolveStructureCosmetics`, `waterFillRuns` and the chest cycle maths byte-identical to 26.1.2. That code is fiddly and was accepted in game (R4).
- `TankFlockAdapter`: `ItemStackRenderState[] itemRenderStates / groupRenderStates` go away. Per swimmer, the adapter keeps only the `ItemStack` and the pose it already computes. The BER draws each fish with `ItemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, level, seed)` (MC `ItemRenderer.java:231-244`). Squash and stretch, render calibration and the hanging roll are already pose transforms, so they carry over.
- Water fill: `submitCustomGeometry(pose, WATER_FILL_RENDER_TYPE, (pose, buffer) -> …)` becomes `buffers.getBuffer(FishtasticRenderTypes.TANK_WATER_FILL)` and the same `addWaterFillQuad` calls. `WATER_FILL_RENDER_TYPE`'s 26.1 settings (translucent, **no depth write**; the comment in the BER explains why) map onto `CompositeState`: `ENTITY_TRANSLUCENT` shader, `TRANSLUCENT_TRANSPARENCY`, `COLOR_WRITE` (no depth), `LIGHTMAP`, `OVERLAY`, cull off. **Vertex calls:** `addVertex(pose, x,y,z).setColor(..).setUv(..).setOverlay(..).setLight(..).setNormal(pose, ..)` is the 1.21 builder, the same as 26.1, so the 6 `addVertex` sites don't change on 1.21.1. **[1.20.1]** `vertex(Matrix4f,…).color().uv().overlayCoords().uv2().normal(Matrix3f,…).endVertex()` (1.20.1 `VertexConsumer.java:18-30,111,116`).
- Block cosmetics: `BlockModelResolver` + `BlockModelRenderState.submit` becomes `Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers, light, overlay)` (MC `BlockRenderDispatcher.java:109`).
- Chest cosmetics: 26.1's `ChestModel` class doesn't exist on 1.21.1. Bake `context.bakeLayer(ModelLayers.CHEST)` once, take the `bottom`/`lid`/`lock` children as vanilla's `ChestRenderer` does (MC `ChestRenderer.java:35-57`), and set `lid.xRot` from the existing `chestOpenness(..)`. Texture: `Sheets.chooseMaterial(be, ChestType.SINGLE, false)` (MC `Sheets.java:177`) → `material.buffer(buffers, RenderType::entityCutout)`.
- Quality outlines on tank fish: automatic. The A5.4 world-outline hook sits inside `ItemRenderer.render`, which `renderStatic` goes through. The 26.1 explicit `submitOutline` calls are deleted.
- Particles spawned from the renderer: unchanged (`ClientLevel.addParticle`).
- **Culling.** A tank group can be larger than one block. NeoForge 21.1: override `getRenderBoundingBox(T)` (NF `IBlockEntityRendererExtension.java:20`) with the group AABB. Fabric has no equivalent, so `shouldRenderOffScreen(be)` returns true for the group's top/anchor tank. **[1.20.1]** Forge has `getRenderBoundingBox` on the **BE** (`IForgeBlockEntity`), not the renderer.

**Rejected:**
- Keep `FishTankRenderState` as a 26.1-shaped render state and write an extract→submit shim. That adds a layer 1.21.1 doesn't need.
- Move the fish into the chunk model. They animate every frame.

**File map**

| 26.1.2 | 1.21.1 | Files |
|---|---|---|
| `BlockEntityRenderer<BE, State>`, `createRenderState`, `extractRenderState`, `submit(State, PoseStack, SubmitNodeCollector, CameraRenderState)` | `BlockEntityRenderer<BE>#render(BE, float, PoseStack, MultiBufferSource, int, int)` | `client/renderer/FishTankBlockEntityRenderer`, `client/renderer/FishTankRenderState` (cache), `client/renderer/FishPileBlockEntityRenderer`, `client/renderer/FishPileRenderState` (cache) |
| `ItemStackRenderState` per fish, `ItemModelResolver.updateForTopItem` | `ItemStack` + `ItemRenderer.renderStatic` | `client/renderer/TankFlockAdapter`, `client/renderer/FishAnimator` (types only) |
| `ModelFeatureRenderer.CrumblingOverlay` | none (crumbling is applied by `LevelRenderer` to the BE's buffers) | `FishTankBlockEntityRenderer` |
| `SpriteGetter` / `SpriteId` / `Sheets.chooseSprite` | `Material` + `Sheets.chooseMaterial` | `FishTankBlockEntityRenderer` |
| `BlockEntityRendererRegistry` (FAPI) / `EntityRenderersEvent.RegisterRenderers` (NF) | same on 1.21.1 | `fabric:FishtasticFabricClient`, `neoforge:FishtasticNeoForgeClient` |

**Verify:** the unit tests `FishAnimatorFloorLiftTest` (8), `FishAnimatorSquashTest` (7), `GroupSplitTest` (7) and `TankFloorsTest` (3) now run against the real renderer (the stub is deleted). Then a self-test scene (spike pattern): a 3-tank group with 12 fish, one legendary fish, a chest and a campfire cosmetic, and water fill. Screenshot and compare side by side with a 26.1.2 capture of the same scene.

---

## A5.2: Fish tank dynamic block model

**26.1.2:** NeoForge `FishTankBlockStateModel implements CustomUnbakedBlockStateModel` (blockstate-level custom model, `"type": "fishtastic:fish_tank"`), baked into `FishTankBakedModel implements DynamicBlockStateModel`. Fabric has the same shape (`"fabric:type"`, `FabricBlockStateModel`). `resolveDependencies` marks all **5,088 fragment models**. At meshing time `collectParts` / `emitQuads` read the BE snapshot (`FishTankCompositeModelData`), pick the permutation, and bake the fragment geometry with texture slots overridden from the chosen frame, sand and glass blocks, cached per `CacheKey`. The `bakeTopGeometry` cache bug and the blockstate-redirect scan are the known pitfalls (`docs/fish-tank-rendering.md`).

**1.21.1 constraints:**
1. There's no blockstate-level custom model type. Custom models hang off a **model JSON** (NeoForge `"loader"`) or a **model-loading plugin** (Fabric).
2. Models are `BakedModel` (`getQuads`) with NeoForge `IDynamicBakedModel#getQuads(state, side, rand, ModelData, RenderType)` (NF `IDynamicBakedModel.java:30`) or Fabric `FabricBakedModel#emitBlockQuads(BlockAndTintGetter, BlockState, BlockPos, Supplier<RandomSource>, RenderContext)` (FAPI `FabricBakedModel.java:94`).
3. **Thread safety.** `ModelBakery.getModel` lazily loads model JSON into a plain `HashMap` (MC `ModelBakery.java:83,141-166`), and `ModelBaker.bake` writes a `HashMap` baked cache (`:84`). Both are safe only during the reload's bake phase. The 26.1 design bakes fragments **at meshing time, on meshing threads**, so a straight port would race.

**Approach:**
- **Load everything during reload, bake geometry at runtime without the baker.** In `resolveParents` (NeoForge: `IUnbakedGeometry#resolveParents(Function<ResourceLocation, UnbakedModel>, IGeometryBakingContext)`, NF `IUnbakedGeometry.java:36`; Fabric: `UnbakedModel#resolveParents`, MC `UnbakedModel.java:15`), call the model getter for **every** fragment id, using the enumeration that 26.1's `FishTankBlockStateModel.resolveDependencies` already has (copied, not rewritten). Keep the resolved `BlockModel`s in an immutable map. At meshing time, build the retextured model the way the 1.21.1 ancestor did (`44064cc5:neoforge/…/FishTankBakedModel.java:191-203`): `new BlockModel(parent, List.of(), overriddenTextureMap, …)`, resolve its parents from the immutable map, then `BlockModel#bake(ModelBaker, Function<Material, TextureAtlasSprite>, ModelState)` (MC `BlockModel.java:196`; constructor `:89`). With no item overrides, that path only runs `FaceBakery` over the model's elements and doesn't touch `ModelBakery`'s shared caches. Pass a small `ModelBaker` wrapper whose `getModel` reads the immutable map and whose `bake` throws, so a regression shows up immediately instead of racing. The retained `spriteGetter` is the block atlas lookup, which is read-only after stitching. Results go into the same `ConcurrentHashMap<CacheKey, …>` as today.
- **The `bakeTopGeometry` cache bug doesn't exist on 1.21.1.** There's no `ResolvedModel`/`ModelWrapper` cache. Baking a fresh `BlockModel` with its own texture map always produces fresh quads. `docs/fish-tank-rendering.md` §"The bakeTopGeometry Cache Bug" gets a 1.21.1 note instead of a port.
- **Blockstate redirects:** `client/compositemodel/{BlockstateModelScanner, BlockstateRedirectRegistry, BlockModelPathResolver}` are plain resource scanning and port unchanged (Identifier rename). The Fabric trigger stays: `PreparableModelLoadingPlugin` (FAPI `client/model/loading/v1/PreparableModelLoadingPlugin`). **NeoForge changes trigger:** 26.1 orders a reload listener before the model manager through `AddClientReloadListenersEvent` + `VanillaClientListeners`. 21.1's `RegisterClientReloadListenersEvent` has no ordering against vanilla listeners. So run the scan at the **start of `FishTankModel.resolveParents`** with `Minecraft.getInstance().getResourceManager()`. That runs on every model reload, before any tank geometry bakes. `neoforge:fishtank/BlockstateModelReloadListener` is deleted. (`ModelEvent.RegisterAdditional` was also considered, but its caller is a NeoForge vanilla patch that isn't in the extracted sources, so whether it fires per reload is unverified.)
- **Block entity data to the mesher:** NeoForge `BlockEntity#getModelData()` → `ModelData` with a `ModelProperty<FishTankCompositeModelData>` (NF `client/model/data/ModelData`, `ModelProperty`; 26.1 had them under `neoforge.model.data`), and `requestModelDataUpdate()` on change. Fabric: `RenderDataBlockEntity#getRenderData()` + `FabricBlockView#getBlockEntityRenderData(pos)` (FAPI `blockview/v2/FabricBlockView.java:67`; 26.1's `blockgetter.v2.FabricBlockGetter`).
- **NeoForge:** `neoforge:fishtank/FishTankModel` becomes an `IUnbakedGeometry<FishTankModel>` plus `IGeometryLoader`, registered in `ModelEvent.RegisterGeometryLoaders` (NF `ModelEvent.java:167,181`), and referenced from `models/block/fish_tank.json` as `"loader": "fishtastic:fish_tank"`. `FishTankBakedModel implements IDynamicBakedModel`. It also overrides `getRenderTypes(state, rand, ModelData)` → `ChunkRenderTypeSet.of(RenderType.cutout(), RenderType.translucent())`, because glass is translucent (the ancestor imports `ChunkRenderTypeSet`) and 26.1 carried this in `materialFlags`. The blockstate JSON goes back to the vanilla `variants` form pointing at the loader model.
- **Fabric (new code, N3):** `fabric:fishtank/FishTankModelFabric implements UnbakedModel` (`getDependencies`, `resolveParents`, `bake`; MC `UnbakedModel.java:13-18`), returning `FishTankBakedModelFabric implements BakedModel, FabricBakedModel` with `isVanillaAdapter() = false` and `emitBlockQuads(...)` emitting through `context.getEmitter()` (FAPI `QuadEmitter`, `fromVanilla(BakedQuad, RenderMaterial, Direction)`). Materials: `RendererAccess.INSTANCE.getRenderer().materialFinder().blendMode(BlendMode.TRANSLUCENT)` for glass and `CUTOUT` for frame and sand. Installed by the existing `ModelLoadingPlugin`: `pluginContext.registerBlockStateResolver(FISH_TANK, ctx -> ctx.setModel(state, unbaked))` (FAPI `ModelLoadingPlugin.java:70`). Blockstate JSON on Fabric: vanilla form (the resolver overrides it).
  **Renderer availability:** FRAPI needs an active renderer. Fabric API ships Indigo. Under Sodium 0.6, FRAPI support is built in (Indium was merged into Sodium 0.6). Add that to the A6.3 Iris run.
- **Items:** the tank item's per-stack materials. NeoForge: `ItemOverrides#resolve(model, stack, level, entity, seed)` returns the composite for the stack's `FISH_TANK_MATERIALS`/`FISH_TANK_SHAPE` (the ancestor built an `ItemOverrides` subclass; MC `ItemOverrides`). Fabric: `FabricBakedModel#emitItemQuads(stack, randomSupplier, context)` (FAPI `FabricBakedModel.java:125`). This replaces 26.1's `FishTankItemModel`/`FishTankItemModelFabric` (`ItemModel` + `ItemModels.ID_MAPPER`) and the `fishtastic:fish_tank_composite` client item.

**Rejected:**
- Bake all combinations at reload. Frame × sand × glass is open-ended (any block).
- A BER for the tank body. It loses AO and smooth lighting and costs every frame (`docs/fish-tank-rendering.md` §"Why FabricBlockStateModel").
- Calling `ModelBaker.bake(id, state)` from the mesher. It races (constraint 3).

**File map**

| 26.1.2 | 1.21.1 | Files |
|---|---|---|
| `CustomUnbakedBlockStateModel` + codec (NF / FAPI) | NF `IUnbakedGeometry` + `IGeometryLoader`; FAPI `UnbakedModel` + `ModelLoadingPlugin.registerBlockStateResolver` | `neoforge:fishtank/{FishTankBlockStateModel → folded into FishTankModel}`, `fabric:fishtank/{FishTankBlockStateModelFabric → folded into FishTankModelFabric}` |
| `DynamicBlockStateModel#collectParts` / `FabricBlockStateModel#emitQuads` | `IDynamicBakedModel#getQuads(…, ModelData, RenderType)` / `FabricBakedModel#emitBlockQuads` | `neoforge:fishtank/FishTankBakedModel`, `fabric:fishtank/FishTankBakedModelFabric` |
| `ResolvedModel`, `QuadCollection`, `UnbakedGeometry.bake`, `TextureSlots`, `Material.Baked`, `BlockModelRotation` (`block.dispatch`), `ModelState` (`block.dispatch`), `ModelDebugName`, `SimpleModelWrapper` | `BlockModel`, `List<BakedQuad>`, `BlockModel#bake`, `BlockModel.textureMap` (`Either<Material, String>`), `Material`, `BlockModelRotation` (`net.minecraft.client.resources.model`), `ModelState` (same package) | all of `neoforge:fishtank/*`, `fabric:fishtank/*`, `client/compositemodel/CompositeTextureHelper` |
| `ItemModel` tank (`FishTankItemModel*`) + `ItemModels` registration | `ItemOverrides` (NF) / `emitItemQuads` (FAPI) | `neoforge:fishtank/FishTankItemModel`, `fabric:fishtank/FishTankItemModelFabric` |
| `neoforge.model.data.{ModelData,ModelProperty}` | `neoforge.client.model.data.{ModelData,ModelProperty}` | `neoforge:fishtank/FishTankModelData`, `neoforge:blockentity/FishTankBlockEntityNeoForge` |
| `blockgetter.v2.FabricBlockGetter` | `blockview.v2.FabricBlockView` + `RenderDataBlockEntity` | `fabric:fishtank/FishTankBakedModelFabric`, `fabric:blockentity/*` |
| `RegisterBlockStateModels`, `AddClientReloadListenersEvent`, `VanillaClientListeners` | `ModelEvent.RegisterGeometryLoaders`, `RegisterClientReloadListenersEvent` | `neoforge:FishtasticNeoForgeClient`, `neoforge:fishtank/BlockstateModelReloadListener` |
| `assets/fishtastic/blockstates/fish_tank.json` (per platform, `type` / `fabric:type`) | vanilla `variants` → `fishtastic:block/fish_tank` (NeoForge: that model JSON has `"loader"`) | per-platform resource copies |

**Verify:**
- `latest.log` has 0 `Unable to load model` lines (MC `ModelBakery.java:159`; the 1.21.1 wording of the tank-fragment-loader contract).
- Self-test scene: every shape × 3 material sets, a diagonal group, the waxed-copper redirect case (`docs/fish-tank-rendering.md` §Testing Blockstate Redirects), and a material change applied live (re-mesh).
- **Stress:** a 512-tank group (the cap) placed with fresh materials, which forces concurrent first-time bakes. Watch for `ConcurrentModificationException` or missing-texture quads.

---

## A5.3: Custom item rendering

**26.1.2:** custom `ItemModel` types registered in `ItemModels.ID_MAPPER` (AW): `fishtastic:cosmetic_structure` (31 items), `fish_pile_block`, `pile_of_fish_layers`, `fish_tank_composite` (A5.2). `FishPileIcons` sets `DataComponents.ITEM_MODEL` to pick an icon per pile size (S1 left-in-place item 7). Client-item conditions for the rods, books and treasure chest.

**1.21.1:**
- **BEWLR** for the structure and pile items. Fabric `BuiltinItemRendererRegistry.INSTANCE.register(ItemLike, DynamicItemRenderer)` (FAPI `BuiltinItemRendererRegistry.java:76`). NeoForge: `IClientItemExtensions#getCustomRenderer()` (NF `IClientItemExtensions.java:178`), registered in `RegisterClientExtensionsEvent#registerItem` (NF `RegisterClientExtensionsEvent.java:56`). Each item's `models/item/*.json` gets `"parent": "builtin/entity"` plus the display transforms the 26.1 `ItemModel` hard-codes (`ItemTransforms` in `CosmeticStructureItemModel`/`FishPileBlockItemModel`), moved into JSON so datagen emits them.
- The three renderers become `DynamicItemRenderer`s sharing one common body: `render(ItemStack, ItemDisplayContext, PoseStack, MultiBufferSource, light, overlay)`.
  - `CosmeticStructureItemModel`: draws the structure's blocks with `renderSingleBlock`, the same as the BER cosmetics (A5.1). Its `BlockTintSource` use becomes `BlockColors` through `renderSingleBlock`, which tints automatically.
  - `FishPileBlockItemModel`, `PileOfFishItemModel`: draw the contained fish with `renderStatic` (the ancestor's approach), reading `FishtasticItemData.bundleContents` (now `Iterable<ItemStack>`).
- **`FishPileIcons` / `ITEM_MODEL`:** 1.21.1 has no `ITEM_MODEL` component. Pick the icon with an item property `fishtastic:pile_size` (`ItemProperties.register(item, id, ClampedItemPropertyFunction)`, MC `ItemProperties.java:54`) and `overrides` in `pile_of_fish.json`. `FishPileIcons` becomes the property function.
- **Client-item conditions (N4):** register `ItemProperties` on our items: `minecraft:cast` (vanilla registers it for `Items.FISHING_ROD` only, MC `ItemProperties.java:208`; copy that lambda for `copper_fishing_rod`/`obsidian_fishing_rod`) and `fishtastic:has_alert` (`FishtasticItemData.has(stack, HAS_ALERT) ? 1 : 0`). `cosmetic_treasure_chest`'s `local_time` Christmas select plus `special` chest becomes a BEWLR that picks `Sheets.CHEST_XMAS_LOCATION` vs the normal chest material, the same way vanilla's chest BEWLR does.
- **Item size scaling:** not a render hook on 26.1.2 either (pass 1 was wrong). Size lives in `FishTankBlockEntityRenderer.getHeldItemRenderScale`, `TankFlockAdapter`, `HumanoidModelMixin`, and the tank browser and leaderboard screens. The held-item part is A5.6.

**Rejected:** baked-model `ItemOverrides` for the structure items. Structures are arbitrary block sets, so there's nothing to pre-bake.

| 26.1.2 | 1.21.1 | Files |
|---|---|---|
| `ItemModel`, `ItemModel.Unbaked`, `ItemModels.ID_MAPPER`, `ItemStackRenderState`, `ItemModelResolver`, `ItemOwner`, `ItemTransform(s)` (`resources.model.cuboid`), `ResolvableModel`, `ResolvedModel` | `DynamicItemRenderer` / `BlockEntityWithoutLevelRenderer`, `ItemTransforms` in JSON, `LivingEntity` | `client/renderer/{CosmeticStructureItemModel,FishPileBlockItemModel,PileOfFishItemModel}`, `client/FishtasticClientSetup` (registration) |
| `DataComponents.ITEM_MODEL` | `ItemProperties` + model `overrides` | `client/util/FishPileIcons` |
| `assets/fishtastic/items/*.json` (180) | deleted (A3.3); `models/item` overrides generated in A3 | `fabric:datagen/FishtasticModelProvider` |

---

## A5.4: Item effects: glint, GUI outline, world outline, GUI shader effects

**Spike result: PASS** on every criterion, on both loaders and under Iris + Complementary on Fabric. This section is the spike's design carried into production code, with its seven findings applied. `docs/item-effect-rendering.md` gets a "1.21.1 / 1.20.1" section pointing here.

**Glint.** 26.1: `ItemModelResolverMixin` + `ItemStackRenderStateMixin` + `FishtasticGlintState` (quads-identity map and thread-local) + `ItemFeatureRendererMixin#getFoilRenderType`. **1.21.1:** the ancestor's `mixin/ItemRendererMixin` (`44064cc5`), which the spike confirmed unchanged. `@Inject` at HEAD/RETURN of `ItemRenderer.render` captures the stack in a thread-local, and HEAD cancellable injects on `getFoilBuffer`/`getFoilBufferDirect`/`getCompassFoilBuffer` (MC `ItemRenderer.java:176,182`) return the effect's glint buffer. `RenderBuffersMixin` registers the custom glint render types as fixed buffers (the ancestor version: `put` at HEAD, keyed on `RenderType.glint()`).
- **Deleted, not ported:** `FishtasticGlintState`, `FishtasticItemStackRenderState`, `ItemModelResolverMixin`, `ItemStackRenderStateMixin`, `ItemFeatureRendererMixin`, `RenderTypeMixin` (its UBO binding is replaced by plain uniforms), `LevelRendererMixin` (already a placeholder on 26.1.2).

**Outline atlas.** `FishtasticItemOutlineAtlas` from the spike: two `TextureTarget`s (the mask with depth, the outline without). Each slot is baked with `ItemRenderer.render(..., GUI, ...)` into the shared `BufferSource` + `endBatch()`, then composed per slot with the bake shaders (`ShaderInstance` + uniforms set before each slot's draw). The bake point is HEAD of `GameRenderer.render(DeltaTracker, boolean)` (MC `GameRenderer.java:1023`, `mixin/GameRendererMixin`), which restores the projection and model-view matrices and rebinds the main target. Same slot and LRU bookkeeping as 26.1.2.
- Findings to apply:
  - **F2:** split the shader JSON uniforms per program (no "could not find uniform" spam).
  - **F3:** leave the atlas texture LINEAR. The world render type forces NEAREST through its own texture state shard, so nothing toggles filters per draw.
  - **F5:** optionally bake the mask without foil (harmless either way).
  - Remove the spike's CPU timing and `client/spike/**`.

**GUI outline.** `FishtasticGuiOutlineRenderer` (spike): a HEAD inject on `GuiGraphics.renderItem(LivingEntity, Level, ItemStack, int, int, int, int)` (MC `GuiGraphics.java:543` and its private overload) flushes the GUI buffer and draws a 24×24 `position_tex` quad, offset by 4, from the baked slot (blend on, depth write off). It replaces `GuiRendererMixin`, `GuiRendererExecuteDrawMixin`, `ItemModelResolverMixin` and `ItemStackRenderStateMixin`. The live hook lives in `mixin/GuiGraphicsMixin` (A4 moved it here).

**World outline.** `FishtasticWorldOutlineRenderer` (spike): an inject in `ItemRenderer.render` right after `translate(-0.5,-0.5,-0.5)`, where a flat item's sprite is exactly `[0,1]²` at `z = 0.5`, draws one expanded quad through `FishtasticRenderTypes.ITEM_OUTLINE` (atlas texture, NEAREST, translucent, **no cull**). **Finding F4, Fabulous:** 26.1 draws into the item-entity target, but the spike used `entityTranslucent` (main target). For Fabulous, define `ITEM_OUTLINE` with `RenderStateShard.ITEM_ENTITY_TARGET` as the output state (what `itemEntityTranslucentCull` uses, minus the cull). Verify in Fabulous mode (spike gap). It replaces `ItemEntityRendererMixin`, `ItemFrameRendererMixin` and `ItemFeatureRendererMixin`, and it covers tank fish (A5.1) and held items automatically.

**GUI shader effects** (`FishtasticSilhouetteEffect`, `FishtasticBlackOutlineEffect`, `FishtasticTextureOutlineEffect`; gelatin `AbstractEffect`s used by `SilhouetteItemButton`, the organizer and the minigame zones): 26.1 builds each on a `RenderPipeline` + `GpuBuffer` UBO (`FishtasticOutlineUboRegistry`). **1.21.1:** one `ShaderInstance` each (`gui_item_silhouette`, `gui_texture_outline`, and the basic outline program reused for black), with uniforms set before the draw. The effect draws its quad with `RenderSystem.setShader(() -> shader)` + `BufferUploader.drawWithShader`, inside the gelatin effect's `apply(IRenderContext)`. Port the 4 fragment shaders to GLSL 150 with JSON programs (the spike's `outline_bake.fsh` shows the mechanical changes: `#version 150`, uniforms instead of blocks, geometry as `#define`s). `FishtasticOutlineUboRegistry` is deleted.

**Shader file map (12 files on 26.1.2):**

| 26.1.2 | 1.21.1 |
|---|---|
| `outline_bake.{vsh,fsh}`, `outline_bake_legendary.fsh` | spike versions + `outline_bake.json`, `outline_bake_legendary.json` (F2: separate uniform lists) |
| `gui_item_outline.{vsh,fsh}`, `gui_item_outline_legendary.{vsh,fsh}` | **deleted.** GUI outlines now come from the baked atlas (spike design). |
| `gui_item_outline_debug_uv.{vsh,fsh}` | port as a dev-only program (`outline_debug_uv.json`), or drop it. Recommendation: port it. It's small and `outline_debug_uv` is a live `ItemEffect` field. |
| `gui_item_silhouette.{vsh,fsh}`, `gui_texture_outline.fsh` | port + JSON programs (GUI shader effects above) |

**`ItemEffect` render data** (split in A2.5): `client/renderer/ItemEffectRenderData` holds the per-effect glint `RenderType` and nothing GPU-side: uniforms are set per draw, so no buffers are needed. It's keyed by effect id, rebuilt on `ItemEffectManager` reload, and invalidates `FishtasticItemOutlineAtlas`.

**Verify (G1 = the spike criteria, now on production code):**
1. quality glint on a held item
2. static and animated legendary GUI outline in hotbar and container
3. world outline on dropped items and in item frames
4. under Iris + Complementary on **both** loaders (the spike ran only Fabric)
5. **new:** Fabulous graphics
6. **new:** GUI scale 1, 2 and 4 (the spike covered 3 only)
7. GUI shader effects on the silhouette buttons and organizer

Evidence goes into `docs/spike-evidence`-style PNGs through the self-test harness.

---

## A5.5: Particles

**26.1.2:** 6 classes `extends SingleQuadParticle` with `getLayer() → SingleQuadParticle.Layer.OPAQUE`, plus `RisingParticle`, `BaseAshSmokeParticle` and `WaterDropParticle` subclasses. Providers are registered through FAPI `ParticleProviderRegistry` / NF `RegisterParticleProvidersEvent`.

**1.21.1:** `extends TextureSheetParticle` (MC `TextureSheetParticle.java:12,16`) with `getRenderType() → ParticleRenderType.PARTICLE_SHEET_OPAQUE` (MC `Particle.java:137`, `ParticleRenderType.java:31`). Sprites come from `pickSprite(SpriteSet)` / `setSpriteFromAge(SpriteSet)` (`TextureSheetParticle.java:44,48`) instead of the 26.1 constructor argument. The three vanilla-subclass particles check their super constructors. Registration: FAPI `ParticleFactoryRegistry.getInstance().register(type, PendingParticleFactory)` (`ParticleFactoryRegistry.java:50`), NF `RegisterParticleProvidersEvent#registerSpriteSet` (`:82`). The `assets/fishtastic/particles/*.json` (11) are unchanged.

Files: `client/particle/{LavaBubbleParticle,LavaSplashParticle,LavaWakeParticle,MiniCampfireSmokeParticle,MiniFlameParticle,MiniSmokeParticle,TankBubbleParticle,TankBubblePopParticle,TankMicroBubbleParticle}`, `util/SparkleParticle`, `fabric:FishtasticFabricClient`, `neoforge:FishtasticNeoForgeClient`. **[1.20.1]** Same API.

---

## A5.6: Entity and held-item hooks

| Hook | 26.1.2 | 1.21.1 approach (lands on) |
|---|---|---|
| Fishing line comes from the right hand for Fishtastic rods | `FishingHookRendererMixin`: `@Inject HEAD getHoldingArm` (1.21.9+ static helper) | 1.21.1 has no `getHoldingArm`. The hand is chosen inline in `FishingHookRenderer.getPlayerHandPos` with `itemStack.is(Items.FISHING_ROD)` (MC `FishingHookRenderer.java:64-68`). **`@WrapOperation` on that `ItemStack.is(Item)` call** returns true for `FishtasticItemTags.FISHING_RODS`. **[1.20.1]** The same code exists in `render` (1.20.1 `FishingHookRenderer.java:52`), so it's the same wrap on a different enclosing method. |
| Fisherman pose, and the podium puppet pose | `HumanoidModelMixin`: `@Inject TAIL setupAnim(HumanoidRenderState)` reading render-state fields that `ItemInHandLayerMixin` filled in | `@Inject TAIL setupAnim(T entity, float, float, float, float, float)` (MC `HumanoidModel.java:112`). Read the entity directly: main hand, `isFishing`, item size through `FishtasticItemData`. The render-state plumbing is deleted. gelatin's 1.21.1 puppet is a real `RemotePlayer` (G-1.21.1), so the podium pose goes through this same hook. |
| Held fish scale and hanging roll (third person) | `ItemInHandLayerMixin`: inject around `submitArmWithItem` → `ItemStackRenderState.submit` | Inject before and after the `renderItem` call inside `ItemInHandLayer.renderArmWithItem(LivingEntity, ItemStack, ItemDisplayContext, HumanoidArm, PoseStack, MultiBufferSource, int)` (MC `ItemInHandLayer.java:44`): push, apply `getHeldItemRenderScale`/`getHeldItemHangingRollDegrees`, pop. |
| Held fish scale (first person) | `ItemInHandRendererMixin`: at `ItemModelResolver.updateForTopItem` inside `renderItem` | Inject at HEAD and RETURN of `ItemInHandRenderer.renderItem(LivingEntity, ItemStack, ItemDisplayContext, boolean, PoseStack, MultiBufferSource, int)` (MC `ItemInHandRenderer.java:125`), with the same pose push, scale and pop. |
| `GameRendererMixin` (`tick`, `render` HEAD bake point, `displayItemActivation`) | same targets | present (MC `GameRenderer.java:778,1023,1289`). Descriptor check only. |
| `ItemStackMixin` (`getTooltipLines`, `hasFoil`) | `getTooltipLines(TooltipContext, Player, TooltipFlag)` | same (MC `ItemStack.java:713,852`). **[1.20.1]** `getTooltipLines(Player, TooltipFlag)` (1.20.1 `ItemStack.java:580`). |

Files: `mixin/{FishingHookRendererMixin,HumanoidModelMixin,ItemInHandLayerMixin,ItemInHandRendererMixin,GameRendererMixin,ItemStackMixin}`. The render-state imports `ArmedEntityRenderState`, `HumanoidRenderState`, `ItemEntityRenderState` and `ItemFrameRenderState` all disappear.

---

## A5.7: Iris, RenderBuffers, LevelRenderer

- **`IrisCompat`**: 26.1 registers each `RenderPipeline` with Iris through `IrisApi.assignPipeline` (reflection), so Iris doesn't drop our world-outline pipeline. **1.21.1:** Iris 1.8 has no pipeline API and works at the shader-program level. The spike showed the outline render type drawing correctly under Complementary with no registration. Keep `IrisCompat` as a **no-op with the same public methods**, so call sites don't differ between branches. Iris detection (`isShaderPackInUse`) stays, if anything uses it. **[1.20.1]** The same no-op.
- **`RenderBuffersMixin`**: the ancestor version (A5.4 glint).
- **`LevelRendererMixin`**: already an empty placeholder on 26.1.2. **Delete it** on 1.21.1 (and consider deleting it on 26.1.2 too).
- **Dev setup for Iris checks:** spike finding 6. On Loom 1.17 (D11), Iris 1.8.14 + Sodium 0.6.x should work directly. If they don't, reuse the spike's `-Pspike_iris` `localRuntime` block (renamed `-Piris`) for Iris 1.8.8 + Sodium 0.6.13.

---

## As built

Written as each checkpoint lands. Where this section and the design above disagree, this section is what the code does.

**Owner decisions taken at the start of A5 (2026-09-24):**
- **HUD layers go above vanilla's toasts.** One `@Inject` in `GameRenderer.render` after its `"toasts"` section draws the three Fishtastic HUD layers (A4's finding: FAPI 0.116's `HudRenderCallback` fires at `Gui.render` TAIL and vanilla's toasts cover it). Lands with A5.7; 1.20.1 gets the same.
- **Tooltip slot rows stay left-aligned** (1.21.1's `renderImage` gets no tooltip width; vanilla's bundle tooltip does the same).
- **gelatin's true X/Y rotation stays.** FlipEffect X flips and SpinEffect Y spins differ in kind from 26.1's cosine squash; that's accepted, to be looked at in A6.3.
- **The two 26.1.2 bugs from A4 were fixed on 26.1.2 first** (`7839f271`) and cherry-picked (`e33f5792`). The A4 note's suggested JEI fix (read the size lazily in the getters) would not have worked: JEI validates the properties as soon as `apply` returns and then compares each frame's fresh object against the cached one, so a lazy cached object would track the screen and compare equal forever. The fix returns `null` (allowed by `IScreenHandler`'s `@Nullable`, not logged) until the screen has a size.

**Added during A5 (not in the design):**
- `client/CosmeticCaptureClientState` draws its wand-selection boxes with 26.1's `Gizmos`, which 1.21.1 doesn't have. It needs a world-render line-box hook. Scheduled with A5.7.
- **The self-test harness is committed** as `client/selftest/RenderSelfTest` (A4's was deleted and can't be recovered). It's inert unless `<run dir>/fishtastic_render_selftest` exists; each line of that file names a scene. It logs `[selftest] CHECK <name>: PASS|FAIL` for what it can assert itself and writes `run/screenshots/selftest-<loader>-<scene>-<shot>.png`. It's dev tooling: delete it at A7 if it shouldn't ship.

**A5.0 (checkpoint 1).**
- `FishtasticRenderTypes extends RenderType` holds `TANK_WATER_FILL`. **Correction:** the shards are `protected` and reachable by subclassing, but vanilla's `RenderType.create` is `private` (the 7-arg overload) and package-private (the 5-arg one) in the 1.21.1 jar (javap). The reference sources show it public because they carry FAPI's access wideners. So `fishtastic.accesswidener` gets one line for the 7-arg `create`. At runtime NeoForge's own AT and FAPI's transitive AW already make it public; the line is for common's compile.
- `FishtasticShaders` is the spike's seam, registering `outline_bake` and `outline_bake_legendary`. The shader sources are the spike's, with **F2 applied**: the basic program neither declares nor lists the four pinwheel-only uniforms, and no "could not find uniform" warning is logged on either loader. The self-test checks both programs load (`shaders.loaded`). `FishtasticRenderPipelines` is deleted; the A5.4 files still reference it and are rewritten there.

**A5.5 (checkpoint 1).** Nine particle classes (`util/SparkleParticle` already compiled in A2). `SingleQuadParticle` → `TextureSheetParticle` (or the 1.21.1 `RisingParticle`/`WaterDropParticle`/`BaseAshSmokeParticle`), and the sprite goes through `setSprite` after `super` because 1.21.1's constructors don't take one. `getLayer() → OPAQUE` becomes `getRenderType() → PARTICLE_SHEET_OPAQUE`. 1.21.1's `ParticleProvider.createParticle` has no `RandomSource`, so sprites are picked with `level.getRandom()`. `MiniFlameParticle.getLightCoords` becomes `getLightColor` with vanilla `FlameParticle`'s body (26.1's `LightCoordsUtil.addSmoothBlockEmission`). Fabric's registry is `ParticleFactoryRegistry` on 0.116.

**A5.1 (checkpoint 1).**
- `render` calls a static `snapshot(be, partialTick, light)`, which is 26.1.2's `extractRenderState` body, and then runs 26.1.2's `submit` body against a `MultiBufferSource`. **A fresh snapshot per frame**, not a per-BE cache: 26.1.2's `BlockEntityRenderDispatcher` calls `createRenderState()` every frame (`BlockEntityRenderDispatcher.java:87`), so allocating per frame keeps every frame-to-frame behaviour identical. `FishTankRenderState`/`FishPileRenderState` lose their vanilla base class and gain a `lightCoords` field.
- Fish: `ItemRenderer.renderStatic(stack, FIXED, light, NO_OVERLAY, pose, buffers, level, 0)`. The explicit outline calls are gone (A5.4's hook sits in `ItemRenderer.render`). `TankFlockAdapter` loses its `ItemStackRenderState[]` arrays.
- Chest cosmetic: `bakeLayer(ModelLayers.CHEST)` children `bottom`/`lid`/`lock`, posed like `ChestRenderer` (`lid.xRot = -(openness·π/2)`; 26.1's `ChestModel.setupAnim` is the same formula), textured from `Sheets.CHEST_LOCATION` with `entityCutout`. Block cosmetics: `BlockRenderDispatcher.renderSingleBlock`.
- **Culling: no override.** The design proposed a group AABB on NeoForge, but 26.1.2 has no culling hook either, and its BER javadoc accepts the artifact ("fish vanish when the anchor is frustum-culled"). Both 1.21.1 loaders default to the unit cube (NeoForge `IBlockEntityRendererExtension.getRenderBoundingBox`), which is the same behaviour. Changing it would be a presentation difference from 26.1.2, so it's left for 26.1.2 to decide first.
- `ClientTankFlocks`, `TankBubbleEmitter` and `CosmeticTransformLoader` came off the exclude list with it. The loader's `reload` takes 1.21.1's six-argument signature. On Fabric it's registered through `ResourceManagerHelper` wrapped in an `IdentifiableResourceReloadListener`; on NeoForge through `RegisterClientReloadListenersEvent`.
- **NeoForge dev runtime needed `:fishsim` in the Loom `main` mod group** (`sourceSet("main", project(':fishsim'))`). Without it, the first tank render died with `NoClassDefFoundError: grill24/fishsim/domain/FlockDomain`: FML 4 loads the mod's dev classes into their own module, which can't see a plain project jar. The shipped jar already shadows fishsim in. Fabric was unaffected.
- **Verified** with the `tank` scene on both loaders: 12 fish added to a 3-tank group whose middle tank reports open faces `[west, east]`, a chest, a lit campfire, the `dynamic_duo` lit-furnace structure, a lone tank with starfish/garden eel/plaice, and a fish pile. The tank body is still the missing model (A5.2) and hides the contents from outside, so the evidence is taken from inside the tanks, where the missing-model cube's back faces are culled: fish, water fill, bubbles and particles all draw, with no exceptions on either loader. The outside comparison with 26.1.2 comes once A5.2 lands.
- **26.1.2 finding (report only):** `FishTankRenderState.chestLastBubbleSpawnTick` is documented as persisting across frames to stop a chest's bubble being released twice in one game tick. But the state is new every frame (see above), so the check never fires, and on a stream tick every rendered frame releases a bubble. The port keeps that behaviour for parity. A fix belongs on 26.1.2 (keep the map on the block entity, as the furnace and campfire throttles already do).

**A5.2 + A5.2f (checkpoint 2).**
- **One shared loader-neutral core, `client/compositemodel/FishTankGeometry` (port-only).** Both loaders' tank models call `FishTankGeometry.bake(baker, spriteGetter, modelLocations)` from their `bake`, which runs in the reload's single-threaded bake phase. It does everything that touches the `ModelBakery` there and then: it runs the blockstate-redirect scan (`BlockstateModelScanner` → `BlockstateRedirectRegistry`), loads and parent-resolves every fragment model into an immutable map, and resolves **every registered block's** texture into an immutable `Block → Material` map (26.1.2's `CompositeTextureHelper` logic). That's **5,048 fragments** (the enumeration is 26.1.2's `resolveDependencies`, copied, so the design's 5,088 was an estimate) and 1,095 block textures, in 120–185 ms. Candidate model paths are checked against the resource manager *before* asking the bakery, because a miss makes `ModelBakery.getModel` log `Unable to load model` (the G1 count stays 0). At meshing time, `composite(data)` only reads those maps, builds a fresh `BlockModel` per fragment (parent = the fragment, `all`/`particle` = the material's texture) and calls vanilla `BlockModel#bake` with a `ModelBaker` that throws if it's ever used. With no item overrides that's just `FaceBakery`, so it's safe on meshing threads. The composition (permutation, corner and edge fragments, glass fills) is 26.1.2's `generateCompositeModel` unchanged. The result is cached per 26.1.2 `CacheKey` in a `ConcurrentHashMap` (`putIfAbsent`, so concurrent first bakes of one key are harmless). One `FishTankGeometry` per reload serves the block and item models (keyed on that bakery's missing-model instance). The sprite getter kept for runtime is the bake phase's, whose sprites are the ones the new atlas holds.
- **The redirect scan moved into the bake on both loaders.** So NeoForge's `BlockstateModelReloadListener` and Fabric's `BlockstateModelRedirectPlugin` are deleted, and `CompositeTextureHelper` is deleted (26.1-only API; its logic is in `FishTankGeometry`).
- **Chunk layers per material.** Each composite has three layers (frame, sand, glass), and each renders in its *material block's* own layer: NeoForge asks that block's model for its render types, Fabric maps `ItemBlockRenderTypes.getChunkRenderType` to a `BlendMode`. 26.1.2 gets the same from its quads' material flags. AO is off on both (26.1.2's `SimpleModelWrapper(..., false, ...)`).
- **NeoForge:** `FishTankModel implements IUnbakedGeometry` + `IGeometryLoader`, registered in `ModelEvent.RegisterGeometryLoaders`. `models/block/fish_tank.json` is `{"parent": "minecraft:block/block", "loader": "fishtastic:fish_tank"}`, and the blockstate is the vanilla `variants` form. `FishTankBakedModel implements IDynamicBakedModel` reads `FishTankModelData.DATA_PROPERTY` (the BE's `getModelData` is back). The item model's parent is the block model, so it bakes through the same geometry. `FishTankItemModel` is now an `ItemOverrides` that resolves the stack's `FISH_TANK_SHAPE`/`FISH_TANK_MATERIALS` to the closed composite, as 26.1.2's item model does, and returns **one render pass per layer**. NF 21.1's `ItemRenderer` draws every quad of a pass for each of its render types, so passes are the only way to give the glass its own translucent item render type.
- **Fabric (new code):** `FishTankModelFabric implements UnbakedModel`, and one `ModelLoadingPlugin` resolves **both** `fishtastic:block/fish_tank` and `fishtastic:item/fish_tank` to it. The item can't just name the block model as its parent: vanilla `BlockModel.resolveParents` throws `"BlockModel parent has to be a block model."` for any other parent. `FishTankBakedModelFabric` has `isVanillaAdapter() = false`. `emitBlockQuads` reads `FabricBlockView#getBlockEntityRenderData` (the existing `FishTankBlockEntityFabric.getRenderData`), `emitItemQuads` uses the stack's materials, and `getQuads` is a vanilla fallback that draws the default tank. The blockstate is the vanilla form, and Fabric's `models/block/fish_tank.json` is deleted (the plugin supplies that id).
- **Datagen:** `FishtasticModelProvider` now emits `item/fish_tank.json` as `{"parent": "fishtastic:block/fish_tank"}` (was `builtin/entity`). Re-running `:fabric:runDatagen` changes exactly that one file.
- **Found: the see-through blocks had no chunk layer (A5.2x).** 26.1 derives a block's layer from its textures' alpha, so it registers nothing. 1.21.1 draws every block solid unless told otherwise, and the ancestor had registered the stained glass as translucent, but that registration never came across. So **all 32 clear/borderless stained glass blocks rendered opaque in the world on both loaders from A2 onward**, and the tank glass went opaque too, because it takes the glass block's layer. `client/FishtasticBlockRenderLayers` (port-only, one `Registrar` lambda per loader like `FishtasticItemProperties`) registers the stained glass as translucent and the plain `clear_glass`/`borderless_glass` as cutout. Those are the layers 26.1 derives: the alpha of every Fishtastic block texture was checked, and only these two families aren't opaque (the fish pile's only non-opaque texture is its particle).
- **Verified on both loaders** (`tank`, `shapes`, `stress512` scenes): the 3-tank group renders as one seamless tank with its fish, sand, cosmetics and water tint; all 20 shapes render in three material sets, including the waxed-copper blockstate-redirect case; an L group and a vertical L render with their corner and edge fragments; a live `setMaterials` re-meshes; the hotbar tank items show their stack's shape and materials; and an 8×8×8 group (the 512 cap, every tank 6-way connected) cycling through 64 fresh frame/glass combinations meshes with no exception, no `Could not generate` warning and no missing texture. `Unable to load model`: **0** on both loaders.

**A5.3 (checkpoint 3).**
- **One item renderer for all of 26.1's custom item model types:** `client/renderer/FishtasticItemRenderers` (port-only), registered through Fabric's `BuiltinItemRendererRegistry` and NeoForge's `IClientItemExtensions#getCustomRenderer` (`neoforge:FishtasticItemRendererNeoForge`, a `BlockEntityWithoutLevelRenderer`, created on first use because the block-entity render dispatcher doesn't exist yet when client extensions are registered). The three 26.1 classes keep their names and their geometry constants and transform order, rewritten as immediate-mode draws:
  - `PileOfFishItemModel`: up to 3 fish, each drawn through its own model with `ItemRenderer.render(fish, NONE, …)`. That context's transform is the identity, and the `-0.5` that `render` applies is undone first, so each fish gets 26.1.2's `PILE_SCALE_TRANSFORM`, then the `-0.5` centring, then its offset, exactly as 26.1.2's layers do. In the GUI the fish are flat-lit (the batch is flushed with `Lighting.setupForFlatItems` and restored), as their own models ask; the item's `builtin/entity` model asks for block lighting.
  - `FishPileBlockItemModel`: the pile block look, with `block/block`'s display transform read off a plain block item (`Items.STONE`'s model), since the Pile of Fish's model has none of its own. `leftHand` is derived from the context.
  - `CosmeticStructureItemModel`: 26.1.2's fit and placement matrices, each part drawn with `BlockRenderDispatcher.renderSingleBlock`. That applies block colours and draws the chest through its own item renderer, so 26.1.2's tint extraction and its special-model fallback aren't needed. The part cache is dropped when the world's registries change.
  - The treasure chest is a vanilla `ChestBlockEntity` drawn by the dispatcher, with vanilla's `item/chest` display. Vanilla's chest renderer evaluates the Christmas dates when it's built (on resource reload), where 26.1.2's `local_time` select evaluates them per frame.
- **The leaderboard's pile-block stacks** (`client/util/FishPileIcons`, back from the stub): 26.1.2 sets `minecraft:item_model` to its `fish_pile_block` model per stack. 1.21.1 has no such component, so `pileBlocks` marks the stacks with `CUSTOM_MODEL_DATA` (`FishPileIcons.PILE_BLOCK_MARKER`) and the renderer picks the pile-block look from that. The stacks are client-only, so a vanilla component is enough. **The design's `fishtastic:pile_size` item property is not needed.** It was meant to pick the icon, but 26.1.2 has no size-dependent icon, only this per-stack swap, and a property with overrides can't express "draw it differently" for a `builtin/entity` item. So `client/FishtasticItemProperties` keeps just `cast` and `has_alert`.
- **Datagen:** `pile_of_fish` is now `builtin/entity`, replacing the hand-written 26.1 flat model in common, which 26.1.2's client item doesn't use. The 31 structure items parent a generated `fishtastic:item/template_cosmetic_structure` (`builtin/entity` plus `block/block`'s `gui_light` and display, the transforms 26.1.2's `ItemModel` applies), and the treasure chest parents `minecraft:item/chest`. The `fish_pile` block item stays `builtin/entity` with no renderer: 26.1.2 has no client item for it either, and it isn't obtainable.
- `FishtasticClientSetup` is back as the real class. Its 26.1-only `registerItemModelTypes` is gone, which leaves exactly the three menu-type accessors the A4 stub held.
- **Verified on both loaders** (`items` scene): all item types in the hotbar, the creative inventory, item frames, on the ground and in hand; the **Shape Gallery now shows every shape** (A4's blank-cell gap), opened by an empty-hand click on a placed assembly. `Unable to load model`: 0.
- **Parity observation (26.1.2 behaviour, kept):** the flat Pile of Fish is drawn at the same 0.7 scale in every context (26.1.2's layers replace the display transform outright), so on the ground and in first person it's far larger than a normal item. Worth a look on 26.1.2.

**A5.6 (checkpoint 4).**
- **Fishing line hand.** `FishingHookRendererMixin` is a `@WrapOperation` on `ItemStack.is(Item)` inside `FishingHookRenderer.getPlayerHandPos` (MC `FishingHookRenderer.java:64-68`), answering true for `FishtasticItemTags.FISHING_RODS`. On NeoForge that line is patched to `canPerformAction(FISHING_ROD_CAST)`, which already covers modded rods (26.1.2's comment says the same about its `getHoldingArm` hook), so the wrap has `require = 0` and quietly doesn't apply there. The design's "WrapOperation on is(Item)" row, confirmed.
- **Fisherman pose.** `HumanoidModelMixin` injects at the `TAIL` of `setupAnim(LivingEntity, float×5)` and reads the entity directly: main hand, main arm, `ItemSizeHelper`. On 1.21.1 `PlayerModel` copies the arms onto the sleeves after `super.setupAnim`, so the sleeves follow. **The podium puppet:** 26.1.2 matches `EntityType.MANNEQUIN`, which its puppet always is. 1.21.1 has no mannequins, and gelatin-ui's 1.21.1 puppet is a `RemotePlayer`, which `FishermanPoseDebug.shouldPose(EntityType)` (A2) doesn't match. So a port-only `shouldPose(LivingEntity)` also matches the puppet, by the class name `PlayerAvatarRenderer$Puppet`, because gelatin-ui is an optional dependency and a class reference would break without it. The self-test checks that name still resolves (`held.gelatinPuppetClass`).
- **Held fish size (third person).** `ItemInHandLayerMixin` brackets `ItemInHandRenderer.renderItem(…)` inside `ItemInHandLayer.renderArmWithItem` with the same push/scale/roll/pop as 26.1.2's bracket around `ItemStackRenderState.submit`. The 0.55 third-person compensation is unchanged: 1.21.1's `item/generated` hand transform is the same 0.55.
- **Correction to the design:** 26.1.2's `ItemInHandRendererMixin` doesn't scale first-person fish. It draws the held item's **quality outline** (between `updateForTopItem` and `submit`). On 1.21.1 that outline comes from A5.4's world-outline hook in `ItemRenderer.render`, which `ItemInHandRenderer.renderItem` goes through, so the mixin is **deleted** (and removed from `fishtastic.mixins.json`) rather than ported. First-person fish aren't size-scaled on either version.
- `GameRendererMixin` and `ItemStackMixin` have compiled and applied since A2. The `render` HEAD bake point is used by A5.4.
- **Verified on both loaders** (`held` scene): a cast Fishtastic rod's line leaves the rod hand in first and third person; a 300 cm manta ray is held at its true size and a 12 cm bluegill shrinks; with `FishermanPoseDebug.enabledInWorld` (the puppet's code path), the arm goes out and the fish hangs tail-in-hand.

---

## A5 gate (G1 + render parity)

| Check | How | Expected |
|---|---|---|
| Everything compiles, and no rendering stubs are left | `gw build` | `port/excludes.txt` has no A5 rows; `portstub/` has no renderer stubs |
| Unit tests | `gw :common:test` | 78 |
| Missing models | grep `latest.log` for `Unable to load model` | 0 |
| Spike criteria 1–4 on production code, both loaders | self-test harness (marker file `run/fishtastic_render_selftest`: stage scenes, dump framebuffers with alpha, quit) | pass. Evidence PNGs committed under `docs/port-evidence/1.21.1/` |
| New checks: Fabulous, GUI scales 1/2/4, Iris on NeoForge, the 512-tank stress case | same harness | pass |
| Behaviour parity | fishsim headless export | byte-identical to 26.1.2 (R4). Presentation differences are for the owner's A6.3 look. |
