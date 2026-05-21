# Fish Tank Rendering Architecture

The fish tank is a dynamically retextured block — its frame, glass, and sand faces all display the texture of whatever blocks the player chose during customization. This is more complex than a normal block model because the texture isn't known at bake time; it depends on per-block-entity data that only exists at runtime.

This document explains how that works on each platform, why the two implementations diverge where they do, and the specific pitfalls that were discovered and fixed along the way.

---

## The Core Challenge

A normal Minecraft block model is fully baked at resource-load time: textures, geometry, and UV mapping are all resolved once into a static `BlockStateModel`. The result is stored in the chunk section's mesh and never changes until the chunk is re-meshed.

The fish tank cannot do this because:

1. Two fish tanks in the same chunk may have different frame/sand/glass choices.
2. The player can change a tank's customization at any time, which must be reflected without a full resource reload.
3. The texture to display is a property of the **block entity**, not the block state.

The solution on both platforms is a **dynamic block state model** that is called at chunk-meshing time with world context (level + block position), reads the block entity's current configuration, and emits the correct geometry on the fly.

---

## Shared Utilities (`client/compositemodel` package)

The logic common to both platforms lives in `common/src/main/java/grill24/fishtastic/client/compositemodel/`:

| Class | Role |
|---|---|
| `BlockstateRedirectRegistry` | Shared volatile map of `standardPath → actualPath`; populated at resource reload by each platform's adapter |
| `BlockstateModelScanner` | Scans all `assets/*/blockstates/*.json` to build the redirect map; used by both platform adapters |
| `BlockModelPathResolver` | Base path resolution: redirect → standard path fallback; Fabric uses this directly |
| `CompositeTextureHelper` | `resolveBlockTexture()` and `overrideAllTexture()` — extracted from both baked model classes where they were previously duplicated |

The fish-tank-specific data record also lives in common:

| Class | Role |
|---|---|
| `fishtank/FishTankCompositeModelData` | `record(Block frame, Block sand, Block glass, Set<Direction> openFaces)` — the snapshot passed across the thread boundary on both platforms |

---

## Sub-Model System (shared)

Rather than one monolithic mesh, the fish tank is broken into three composited parts:

- **Frame** — the wooden/stone border, with a texture taken from the chosen frame block.
- **Sand** — the bottom layer, textured from the chosen sand/ground block.
- **Glass** — the transparent panels, textured from the chosen glass block.

Each part has **64 geometric permutations** (2⁶ = 64) corresponding to which of the six faces are open (for connected tanks). These are pre-baked meshes stored as named sub-models:

```
fishtastic:block/fishtankbase/fish_tank_frame_0  .. fish_tank_frame_63
fishtastic:block/fishtankbase/fish_tank_sand_0   .. fish_tank_sand_63
fishtastic:block/fishtankbase/fish_tank_glass_0  .. fish_tank_glass_63
```

The permutation index for a given tank is derived from its `openFaces` bitmask (`Direction.ordinal()` → bit position). At chunk mesh time the correct permutation geometry is selected, then retextured dynamically.

All 192 sub-models are declared as dependencies during the `resolveDependencies` phase so the model bakery loads them unconditionally.

---

## Runtime Data Flow

```
Block entity (main thread)
    │  getFrameBlock() / getSandBlock() / getGlassBlock() / getOpenFaces()
    ▼
FishTankCompositeModelData  ← immutable snapshot, safe to hand to meshing thread
    │
    ▼
Chunk meshing thread
    │  reads snapshot (via platform-specific API — see below)
    ▼
Dynamic block state model
    │  getPermutationIndex() → picks sub-model geometry [0..63]
    │  CompositeTextureHelper.resolveBlockTexture() for each of the three blocks
    │  CompositeTextureHelper.overrideAllTexture() → builds TextureSlots override
    │  getTopGeometry().bake() directly → QuadCollection  (bypasses ModelWrapper cache)
    ▼
Chunk mesh / render
```

The snapshot step is the critical thread-safety boundary. The block entity's fields live on the main thread; the chunk mesher runs on a background thread. Both platforms have different APIs for crossing this boundary (see below).

---

## NeoForge Architecture

### Registration

| What | How |
|---|---|
| Custom block state model type | `RegisterBlockStateModels` event → `event.registerModel(ft("fish_tank"), FishTankBlockStateModel.CODEC)` |
| Custom item model loader | `ModelEvent.RegisterLoaders` event → `event.register(ft("fish_tank"), FishTankModel.Loader.INSTANCE)` |
| Block entity renderer | `EntityRenderersEvent.RegisterRenderers` |

### Block State Model (`FishTankBlockStateModel`)

Implements NeoForge's `CustomUnbakedBlockStateModel`. Referenced in `blockstates/fish_tank.json` as `"type": "fishtastic:fish_tank"`.

`resolveDependencies` marks all 192 sub-models as dependencies. `bake(ModelBaker)` retrieves all 192 `ResolvedModel`s from the baker and constructs `FishTankBakedModel`.

### Baked Model (`FishTankBakedModel`)

Implements NeoForge's `DynamicBlockStateModel`, which extends `BlockStateModel` and adds `collectParts(BlockAndTintGetter, BlockPos, …)`. This overload is called by NeoForge's chunk mesher when it detects that the model is dynamic, giving it world context.

**Data access:** NeoForge uses `ModelData` — a typed property bag. `FishTankBlockEntityNeoForge.getModelData()` constructs a `ModelData` containing a `FishTankCompositeModelData` value, stored under the `FishTankModelData.DATA_PROPERTY` key. The mesher calls `level.getModelData(pos)` to retrieve it.

Note: `FishTankModelData` (NeoForge-only) is now just the property key holder:
```java
public final class FishTankModelData {
    public static final ModelProperty<FishTankCompositeModelData> DATA_PROPERTY = new ModelProperty<>();
}
```

**Model cache:** Composite models are keyed by `CacheKey(frame, sand, glass, permutation)` in a `ConcurrentHashMap`. Cache misses generate the composite under a `synchronized` lock. Failed generations (null texture) are intentionally **not** cached so the next chunk re-mesh retries.

### Item Model (`FishTankModel`)

A separate `UnbakedModel` for the fish tank item, baked once at load time into a static default composite (permutation 0, oak planks / sand / blue glass). It also calls `resolveDependencies` for all 192 sub-models, so loading the item model is sufficient to guarantee all sub-models are resolved regardless of load order.

### Model Path Resolution (`BlockModelPathResolver` — NeoForge)

NeoForge's `BlockModelPathResolver` extends the common base with `FishtasticConfig.STARTUP.blockModelPathOverrides` — a user-configurable list of `{pattern, blocks, modelPath}` entries. Config entries support wildcard patterns and tag references. Priority order: blockstate redirect (from common `BlockstateRedirectRegistry`) → config overrides → standard path.

---

## Fabric Architecture

### Registration

| What | How |
|---|---|
| Custom block state model type | `CustomUnbakedBlockStateModel.register(ft("fish_tank"), FishTankBlockStateModelFabric.CODEC)` |
| Custom item model loader | `UnbakedModelDeserializer.register(ft("fish_tank"), FishTankModelFabric.Loader.INSTANCE)` |
| Block entity renderer | `BlockEntityRendererRegistry.register(…)` |
| Blockstate redirect map | `PreparableModelLoadingPlugin.register(BlockstateModelRedirectPlugin.LOADER, BlockstateModelRedirectPlugin.PLUGIN)` |

All four registrations happen in `FishtasticFabricClient.onInitializeClient()`.

### Block State Model (`FishTankBlockStateModelFabric`)

Implements Fabric API's `CustomUnbakedBlockStateModel`. Referenced in `blockstates/fish_tank.json` as `"fabric:type": "fishtastic:fish_tank"` (note the `fabric:` prefix — this is a Fabric API extension to the blockstate JSON format).

`resolveDependencies` and `bake` are structurally identical to the NeoForge version. The result is `FishTankBakedModelFabric`.

### Baked Model (`FishTankBakedModelFabric`)

Implements both vanilla `BlockStateModel` and Fabric API's `FabricBlockStateModel`.

`FabricBlockStateModel` adds `emitQuads(QuadEmitter, BlockAndTintGetter, BlockPos, …)`, which is the Fabric equivalent of NeoForge's `DynamicBlockStateModel.collectParts`. When this interface is present, Fabric's chunk mesher calls it with world context instead of the vanilla `collectParts(RandomSource, List)`.

**Data access:** Fabric uses `RenderDataBlockEntity` — an interface injected by Fabric API. `FishTankBlockEntityFabric.getRenderData()` returns a `FishTankCompositeModelData` snapshot. During meshing, `((FabricBlockGetter) level).getBlockEntityRenderData(pos)` retrieves it. The snapshot is captured on the main thread before the meshing job is dispatched, providing thread safety without any explicit synchronisation.

**Model cache:** Identical structure to NeoForge (`ConcurrentHashMap<CacheKey, CachedModel>`, synchronized lazy generation, failed generations not cached).

### Model Path Resolution (Fabric)

Fabric uses the common `BlockModelPathResolver` directly (redirect → standard path). There is no config override system — the automatic redirect map from `BlockstateModelScanner` covers all cases.

### Why `FabricBlockStateModel` Instead of a Block Entity Renderer

A `BlockEntityRenderer` runs every frame (or every render tick) and draws directly with `PoseStack` + `MultiBufferSource`. It works, but it bypasses the chunk mesh system entirely — the block renders outside the static geometry pass, which means no ambient occlusion, no smooth lighting, and no batching with adjacent blocks.

`FabricBlockStateModel` participates in the normal chunk meshing pipeline, producing the same `BlockStateModelPart`/`QuadCollection` output as any static block. This gives correct lighting and batching at the cost of needing to re-mesh the chunk section when the tank's customization changes.

---

## Key Divergence Points

### 1. World-Context API

| NeoForge | Fabric |
|---|---|
| `DynamicBlockStateModel.collectParts(BlockAndTintGetter, BlockPos, …)` | `FabricBlockStateModel.emitQuads(QuadEmitter, BlockAndTintGetter, BlockPos, …)` |
| `level.getModelData(pos)` → `ModelData` | `((FabricBlockGetter) level).getBlockEntityRenderData(pos)` |
| `FishTankBlockEntityNeoForge.getModelData()` | `FishTankBlockEntityFabric.getRenderData()` |

### 2. Blockstate JSON Syntax

The `fish_tank.json` blockstate file is different per platform because the custom block state model extension point uses different JSON keys:

- **NeoForge:** `"type": "fishtastic:fish_tank"` (standard MC extension key)
- **Fabric:** `"fabric:type": "fishtastic:fish_tank"` (Fabric API namespace prefix)

Each platform ships its own copy of this file.

### 3. Item Model Loader

- **NeoForge:** `UnbakedModelLoader` registered via `ModelEvent.RegisterLoaders`; referenced in JSON as `"loader": "fishtastic:fish_tank"`
- **Fabric:** `UnbakedModelDeserializer` registered via `UnbakedModelDeserializer.register`; referenced in JSON as `"fabric:type": "fishtastic:fish_tank"`

### 4. Blockstate Redirect Resolution

When `CompositeTextureHelper.resolveBlockTexture()` looks up a block's texture, it calls `baker.getModel(path)` where `path` is the model location. The expected path is `namespace:block/<blockname>`, but many blocks (311 in vanilla alone, and all of fishtastic's glass blocks) have their blockstate JSON point to a model at a different path. In this case `baker.getModel` returns the missing model placeholder.

Both platforms now share `BlockstateRedirectRegistry` and `BlockstateModelScanner` from the `compositemodel` package — the scanner reads every `assets/*/blockstates/*.json` at resource-reload time to build the redirect map. The platform difference is only in how the scan is triggered:

- **Fabric:** `BlockstateModelRedirectPlugin` (a `PreparableModelLoadingPlugin`) triggers the scan off-thread during resource reload, before baking begins.
- **NeoForge:** `BlockstateModelReloadListener` (a `PreparableReloadListener` registered before `MODELS`) triggers the same scan.

After the scan, both platforms call `BlockstateRedirectRegistry.update(map)` to store the result, and both use it through their respective `BlockModelPathResolver` implementations.

**NeoForge only:** additionally reads `FishtasticConfig.STARTUP.blockModelPathOverrides` as a manual escape hatch for edge cases not caught by the scanner.

`CompositeTextureHelper.resolveBlockTexture()` skips any `ResolvedModel` whose `debugName()` equals `"minecraft:builtin/missing"` (the sentinel for `MissingCuboidModel`) before trying the next candidate path — preventing a missing-texture material from short-circuiting the fallback chain.

### 5. Geometry Bake Call

The one line that cannot be shared between platforms:

- **Fabric:** `model.getTopGeometry().bake(slots, baker, BlockModelRotation.IDENTITY, model)`
- **NeoForge:** `model.getTopGeometry().bake(slots, baker, BlockModelRotation.IDENTITY, model, ResolvedModelExtension.findTopAdditionalProperties(model))`

NeoForge extends the `UnbakedGeometry.bake` signature with an additional properties argument. This is why `bakeGeometry()` stays as a private method in each platform's baked model class rather than moving to `CompositeTextureHelper`.

---

## The `bakeTopGeometry` Cache Bug

`ResolvedModel.bakeTopGeometry(TextureSlots, ModelBaker, ModelState)` is the natural API for baking a model's geometry. However, the concrete implementation (`ModelDiscovery.ModelWrapper`) maintains two internal caches keyed by `ModelState` **only**, completely ignoring `TextureSlots`:

- For `BlockModelRotation.IDENTITY` it stores the result in a fixed atomic slot on first call and returns it forever after.
- For other `ModelState`s it uses `ConcurrentHashMap.computeIfAbsent(state, …)` keyed by object identity.

This means if we baked the default model (oak planks) first through `bakeTopGeometry`, that result would be returned for every subsequent call with the same `ModelState`, regardless of the `TextureSlots` we pass — completely bypassing our texture override.

**Fix:** Both `FishTankBakedModel` (NeoForge) and `FishTankBakedModelFabric` (Fabric) call `model.getTopGeometry().bake(slots, baker, state, model, …)` directly, bypassing `ResolvedModel` entirely and going straight to the `UnbakedGeometry` implementation. This always produces fresh quads using the `TextureSlots` we actually want.

The particle material has the same problem — `ResolvedModel.resolveParticleMaterial` also uses a cached slot. The fix is to call the static `ResolvedModel.resolveParticleMaterial(slots, baker, model)` overload (NeoForge adds this as `ResolvedModelExtension`) or the equivalent direct call on Fabric, which bypasses the instance cache.

---

## Testing Blockstate Redirects

To verify the redirect map is working, use any **waxed copper block** as a fish tank frame material. Waxed copper variants (`waxed_copper_block`, `waxed_exposed_copper`, `waxed_weathered_copper`, `waxed_oxidized_copper`) have no model at `minecraft:block/waxed_*` — their blockstate JSON redirects entirely to the unwaxed counterpart's model (e.g., `minecraft:block/copper_block`). Without the redirect map these blocks would silently fall back to the default oak texture.

Other vanilla examples with the same property: all `*_wall` blocks (redirect to `*_wall_post`), all `infested_*` blocks (redirect to the un-infested block's model), and all `waxed_*` copper variants.
