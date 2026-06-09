# Item Effect Rendering Architecture

This document describes how Fishtastic attaches custom visual effects to quality-tier items.
The primary goal of this doc is **long-term maintainability**: the vanilla rendering pipeline changes significantly between MC versions, so every non-obvious implementation decision below is explained with its constraint and the alternatives that were rejected. Future readers adapting this to a new MC version should be able to understand *why* the code looks the way it does and what to look for when a vanilla API changes.

---

## Overview

Two rendering paths are active per quality item:

- **Glint path** — replaces the enchantment foil texture on 3D world/entity/held items.
- **GUI outline path** — draws a shader-based outline around items in screen-space GUI slots.

Both paths are entirely Mixin-based. Vanilla rendering is unchanged when no effect is active.

---

## Data Model — `ItemEffect`

`ItemEffect` is a codec-loaded record (`ItemEffectManager`, datapacks). One instance exists per rarity tier per data load. It holds all configuration for both rendering paths:

| Field | Default | Purpose |
|---|---|---|
| `texture` | — | Glint overlay texture; also used as the pipeline identity key |
| `outline_color` | `0` | ARGB. Zero = no outline. |
| `outline_falloff` | `0.0` | 0 = solid edge, 1 = full gradient fade |
| `outline_width` | `1` | Thickness in item pixels (1–4), basic shader only |
| `outline_opacity` | `1.0` | Overall alpha multiplier |
| `outline_pinwheel` | `false` | Use the animated pinwheel (legendary) shader |
| `outline_debug_uv` | `false` | Use the slot-UV visualiser shader (dev only) |
| `outline_anim_speed` | `150.0` | Pinwheel: full rotations per in-game day |
| `outline_num_blades` | `3` | Pinwheel: blade count |
| `outline_blade_fill` | `0.65` | Pinwheel: fraction of each sector that is filled |

`ItemEffect` lazily allocates two GPU resources on the render thread:
- `outlinePipeline` — a `RenderPipeline`, unique per effect instance.
- `outlineParamsBuffer` — a 48-byte `GpuBuffer` containing all UBO shader params.

**Why lazy?** `GpuBuffer` and `RenderPipeline` can only be created on the render thread. `ItemEffect` objects are created on the logical thread at data-load time. Lazy creation on first render use avoids cross-thread GPU API calls.

---

## Effect Lookup

`ItemEffectManager.getEffectForItem(ItemStack)` reads the stack's custom data component and returns the matching `ItemEffect`, or `null` if the item has no quality tier.

---

## State Propagation — `FishtasticGlintState`

The MC 26.1 rendering pipeline separates the *resolve* phase (model → `ItemStackRenderState`) from the *draw* phase (feature renderers, GuiRenderer blits). By the time a draw call executes, the `ItemStack` is no longer in scope.

Three static holders bridge this gap:

```
SUBMIT_EFFECT_MAP   IdentityHashMap<List<?>, ItemEffect>
                    Key: the layer's quads List reference
                    Populated: ItemModelResolverMixin (TAIL of updateForTopItem)
                    Consumed:  ItemFeatureRendererMixin (HEAD of renderItem)
                    Cleaned:   ItemStackRenderStateMixin (HEAD of clear)

GUI_EFFECT_MAP      IdentityHashMap<ItemStackRenderState, ItemEffect>
                    Populated: ItemModelResolverMixin (TAIL of updateForTopItem)
                    Consumed:  GuiRendererMixin (submitBlitFromItemAtlas)
                    Cleaned:   ItemStackRenderStateMixin (HEAD of clear)

ACTIVE_EFFECT       ThreadLocal<ItemEffect>
                    Set:     ItemFeatureRendererMixin (HEAD of renderItem)
                    Cleared: ItemFeatureRendererMixin (RETURN of renderItem)
                    Read:    ItemFeatureRendererMixin (getFoilRenderType)
```

**Why `IdentityHashMap` for the first two maps?** Two different `ItemStackRenderState` objects could theoretically be `.equals()` — for example, two items of the same type. We need the *object identity* of the render state or quads list, not value equality, because we are mapping per-render-state metadata. `HashMap` would cause aliasing bugs.

**Why use the quads `List` reference as the key in `SUBMIT_EFFECT_MAP`?** `LayerRenderState.quads` is an `ArrayList` allocated once in the layer constructor and reused (cleared, never replaced) across frames. This makes it a stable, frame-persistent identity key that survives into the draw phase. The `LayerRenderState` itself is exposed through `ItemStackRenderState.prepareQuadList()` but not its internal fields — using the quads list avoids shadowing any private fields.

**Why is there a `ACTIVE_EFFECT` thread-local at all?** `ItemFeatureRenderer.getFoilRenderType` is `public static`. It has no access to the call context. The thread-local is the only way to communicate the active effect to this static method without modifying its signature (which we cannot do from a Mixin without an access widener).

**What might change:** If MC replaces `LayerRenderState.quads` with a value type or re-allocates the list each frame, the `SUBMIT_EFFECT_MAP` key becomes unstable. Watch `ItemStackRenderState.clear()` in future versions — if it re-allocates the layers array or the quads list, the identity key strategy breaks and a different key (e.g., a per-layer integer ID) must be found.

---

## Glint Path — `ItemFeatureRendererMixin`

Injects into three methods of `ItemFeatureRenderer`:

1. **HEAD of `renderItem`** — reads `SUBMIT_EFFECT_MAP.get(submit.quads())` and stores the result in `ACTIVE_EFFECT`.
2. **RETURN of `renderItem`** — clears `ACTIVE_EFFECT`.
3. **HEAD of `getFoilRenderType`** (cancellable) — if `ACTIVE_EFFECT` is non-null, returns the effect's `RenderType` instead of the vanilla glint type.

**Why hook `updateForTopItem` (in `ItemModelResolverMixin`) instead of `appendItemLayers`?** `appendItemLayers` is called recursively for composite models (e.g., a bundle containing an item that also has a glint). Hooking at that level would overwrite the top-level item's effect with the contained item's effect. `updateForTopItem` is only called once for the outermost item and is the correct place to set the final effect.

`ItemEffect` lazily creates four `RenderType` variants via its inner `RenderTypeFactory`:

| Method | When used |
|---|---|
| `qualityGlow()` | Standard world/GUI rendering, `OUTPUT_TARGET_MAIN` |
| `entityQualityGlow()` | Entity/item-frame rendering, `OUTPUT_TARGET_ITEM_ENTITY`, `ENTITY_GLINT_TEXTURING` |
| `entityQualityGlowDirect()` | Direct entity rendering |
| `qualityGlowTranslucent()` | Shader transparency mode, `OUTPUT_TARGET_ITEM_ENTITY` |

The mixin replicates the `ItemFeatureRenderer.useTransparentGlint()` logic to select between translucent and opaque variants based on `Minecraft.useShaderTransparency()` and `baseRenderType.outputTarget()`.

**What might change:** If MC renames or refactors `getFoilRenderType`, the cancellable inject target changes. Also watch `useTransparentGlint()` — if that logic moves, the selection logic in the mixin must be updated to match.

---

## GUI Outline Path

### Blit injection — `GuiRendererMixin`

Injects at **HEAD** of `GuiRenderer.submitBlitFromItemAtlas`. Looks up the `ItemStackRenderState` in `GUI_EFFECT_MAP`. If an effect is found and has `outline_color != 0`:

1. Calls `effect.getOrCreateOutlinePipeline()` to get the pipeline.
2. Calls `renderState.addBlitToCurrentLayer(new BlitRenderState(...))` with the same texture, position, and UV as the normal item blit, using the outline pipeline and `effect.outlineColor()` as the color field.

The color field in the `BlitRenderState` is technically unused by the fragment shader (all colour data comes from the UBO), but is passed anyway for GPU debugger identification.

**Why inject at `submitBlitFromItemAtlas` rather than somewhere else?** This is the exact moment when the GUI item blit is being submitted with the atlas texture view and slot UV coordinates. Those coordinates are available here and nowhere else in the call stack at this granularity.

**What might change:** If MC adds a different blit submission path (e.g., for bundle overlays or animated item slots), outlines for those items would silently not render. Monitor new `GuiRenderer` methods when upgrading.

### UBO binding — `GuiRendererExecuteDrawMixin`

Injects **AFTER** the call to `RenderPass.setPipeline(pipeline)` inside `GuiRenderer.executeDraw`, capturing the local `pipeline` variable via `LocalCapture.CAPTURE_FAILHARD`.

Calls `FishtasticOutlineUboRegistry.bind(pipeline, renderPass)`, which does a fast `IdentityHashMap` lookup and calls `renderPass.setUniform(uboName, buffer.slice())` if the pipeline is registered.

**Why inject here rather than before the blit submission?** The UBO must be bound to the `RenderPass` that is actively executing the draw. `RenderPass` is created inside `executeDraw`; it does not exist at blit-submission time. The `setUniform` call must happen between `setPipeline` and the actual draw call — immediately after `setPipeline` is the correct and minimal window.

**Why `@Coerce Object draw`?** `GuiRenderer.Draw` is a private record. Java source cannot reference it, so Mixin normally rejects `Object` as a substitute, failing with a descriptor mismatch error. The `@Coerce` annotation (`org.spongepowered.asm.mixin.injection.Coerce`) tells Mixin's descriptor validator to accept the coercion and emit the appropriate bytecode cast. Without `@Coerce` the game crashes at startup with "Critical injection failure: Invalid descriptor".

**Why `CAPTURE_FAILHARD` instead of `CAPTURE_FAILSOFT`?** `FAILSOFT` suppresses the error and silently skips the injection if local capture fails. If the vanilla code changes and the `pipeline` local is no longer where we expect it, we want a hard crash with a clear message rather than a silent no-op that causes invisible rendering bugs. Use `FAILHARD` for any injection whose failure is not gracefully recoverable.

**Local variable ordering note:** Mixin captures locals in declaration order within the method. In `executeDraw`, the parameter order is `(draw, renderPass, indexBuffer, indexType)` and the first captured local after `setPipeline` is `pipeline`. The handler signature must declare them in exactly this order: `draw, renderPass, indexBuffer, indexType, CallbackInfo, pipeline`. Getting this order wrong produces a descriptor mismatch.

**What might change:** If MC refactors `executeDraw` (e.g., inlines the `setPipeline` call or changes the local variable layout), `CAPTURE_FAILHARD` will crash the game immediately, making the breakage obvious. Also watch if `GuiRenderer.Draw` becomes public — if so, `@Coerce` becomes unnecessary but harmless.

---

## Pipeline Factory — `FishtasticRenderPipelines`

Each `ItemEffect` gets its own `RenderPipeline` **instance**. Two effects using the same shader variant still get separate instances.

**Why one pipeline per effect?** The MC 26.1 rendering system uses `RenderPipeline` object identity to group and batch draws. If two effects shared a pipeline instance, `FishtasticOutlineUboRegistry` would need to unbind and re-bind the UBO for every draw — but there is no "unbind" API. Having distinct pipeline instances means each draw carries its own UBO binding unambiguously.

Three factory methods, one per shader variant:

| Factory | Shader | UBO name |
|---|---|---|
| `createOutlinePipeline(id)` | `gui_item_outline` | `BasicOutlineParams` |
| `createLegendaryOutlinePipeline(id)` | `gui_item_outline_legendary` | `LegendaryOutlineParams` |
| `createDebugUvPipeline(id)` | `gui_item_outline_debug_uv` | `DebugUvOutlineParams` |

The `Identifier` location passed to each factory is derived from the effect's texture path to guarantee uniqueness across all loaded effects:
```
fishtastic:pipeline/gui_item_outline_{namespace}_{path_slashes_as_underscores}
```

All pipelines declare `DynamicTransforms` and `Projection` (standard blit UBOs), their effect-specific params UBO, `Sampler0`, `TRANSLUCENT` blend, and `POSITION_TEX_COLOR / QUADS` vertex format. The legendary pipeline additionally declares `Globals` because its shader reads `GameTime` for animation.

---

## UBO Architecture — `FishtasticOutlineUboRegistry`

### Why a UBO instead of packed shader parameters?

Earlier iterations packed guiScale, color, and outline settings into unused bytes of the blit's ARGB color field (alpha channel = packed params). This approach failed once we needed more than 8 bits of data per param and it capped guiScale at 4 (2 bits), breaking high-DPI displays at `guiScale=5` or `guiScale=6`.

The UBO approach has no parameter size limit, allows full float/int precision, and puts all shader inputs in one named, documented struct.

### Buffer layout

All three shader variants use the same 48-byte std140 layout (computed at class load time via `Std140SizeCalculator`, stored in `FishtasticRenderPipelines.OUTLINE_PARAMS_UBO_SIZE`):

```
std140 offset  field         type    notes
  0            color         vec4    RGB outline tint; W unused
 16            falloff       float   0=solid, 1=gradient fade
 20            opacity       float   overall alpha multiplier
 24            width         float   outline thickness in item pixels (basic only)
 28            animSpeed     float   pinwheel rotations/day (legendary only)
 32            numBlades     int     pinwheel blade count  (legendary only)
 36            bladeFill     float   pinwheel sector fill  (legendary only)
 40            _reserved0    float   padding for future use
 44            _reserved1    float   padding for future use
```

**Why the reserved fields?** Extending a std140 struct changes its size. `GpuBuffer` is sized at creation. Two reserved floats leave room for one more float param without requiring buffer reallocation or `OUTLINE_PARAMS_UBO_SIZE` changes.

`ItemEffect.buildOutlineParamsBuffer()` allocates the buffer with `USAGE_UNIFORM | USAGE_COPY_DST` and writes values via `Std140Builder.onStack`.

### Registry

`FishtasticOutlineUboRegistry` maps `RenderPipeline → Entry(uboName, GpuBuffer)` via `IdentityHashMap`. The `Entry` record holds both the name and the buffer so that `bind()` can set the uniform by name in one call.

`bind(pipeline, renderPass)` is called for every `executeDraw` invocation. For vanilla pipelines (the majority) this is a fast hash-miss no-op.

**What might change:** If `RenderPass.setUniform` signature changes (e.g., accepts a binding index instead of a name string), the `bind()` method must be updated. Also watch `GpuBuffer.USAGE_UNIFORM` — if the usage flag value or constant name changes, buffer creation will fail.

---

## Shaders

### Vertex shaders

All three outline shaders share the same vertex shader structure (the basic and debug-UV shaders literally share `gui_item_outline.vsh`; the legendary variant has its own copy with an identical body). The key output is `modelViewPos = (ModelViewMat * vec4(Position, 1.0)).xy`, which is passed to the fragment shader to derive guiScale.

### guiScale derivation

All fragment shaders derive guiScale from a screen-space derivative:

```glsl
float dvx = abs(dFdx(modelViewPos.x));
int guiScale = (dvx > 0.0001) ? clamp(int(round(1.0 / dvx)), 1, 8) : 1;
```

`dFdx(modelViewPos.x)` equals `1/guiScale` because the blit spans 16 GUI units across `16 * guiScale` fragments. This approach correctly handles any guiScale value without parameters.

**Why not pass guiScale as a UBO field?** We would need to update the buffer each frame (or at resolution change) and we would need to know the current guiScale on the Java side at outline-setup time. Deriving it inside the shader from `dFdx` is self-contained and always correct, even in mixed-DPI or split-screen scenarios.

**What might change:** `dFdx` is only valid in a fragment shader executing in a full quad. If MC ever batches blit draws such that adjacent blits from different guiScale contexts appear in the same draw call, the derivative would be incorrect at the join boundary. This is extremely unlikely in a blit pipeline but worth noting.

### Atlas slot boundary clamping

The neighbour-sample loop must not read texels from adjacent item slots. Slot bounds are computed from guiScale:

```glsl
float slotW    = 16.0 * float(guiScale) * step.x;
float uSlotMin = floor(texCoord0.x / slotW) * slotW;
float uSlotMax = uSlotMin + slotW;
float vSlotMax = 1.0 - floor((1.0 - texCoord0.y) / slotW) * slotW;
float vSlotMin = vSlotMax - slotW;
```

**Why `1.0 - floor((1.0 - texCoord0.y) / slotW) * slotW` for V?** `GuiItemAtlas` packs items from the top-left. V increases downward in UV space. The floor-based snapping must be done relative to the top of the slot (`1.0 - V`) so that it snaps to slot row boundaries correctly. Without the inversion, the slot row detection is off by a fraction and samples may bleed into adjacent rows.

**What might change:** If MC changes the `GuiItemAtlas` packing order or slot size (currently `16 * guiScale` texels), the slot-bound computation breaks. The slot size is also assumed to be square; if non-square items are ever added, slotW and slotH would need to be computed independently.

### Basic outline shader (`gui_item_outline.fsh`)

Samples all neighbours within a Chebyshev radius of `outlineWidth * guiScale` texels. If any neighbour is opaque (alpha > 0.5), the fragment is coloured. The falloff gradient uses `t = (minDist - 1) / (radius - 1)` where `minDist` is the minimum Chebyshev distance to the nearest opaque pixel.

### Legendary / pinwheel shader (`gui_item_outline_legendary.fsh`)

Same neighbour-scan as basic (radius fixed at `guiScale`). After determining that a fragment is in the outline zone, applies a rotating pinwheel mask:

1. `GameTime * animSpeed mod 1.0` → current rotation angle in turns.
2. Angle from slot-centre to fragment → `atan(dir.y, dir.x) + PI` (0..2π).
3. Subtract rotation, divide into `numBlades` equal sectors.
4. Discard if `posInSector >= bladeFill`.
5. Inside a blade: quadratic sweep brightness (`1 - 0.5 * sweepT²`) and 20% iridescent rainbow blend.

`GameTime` comes from the `Globals` UBO — this is why the legendary pipeline declares `Globals`. The other two shaders do not animate and therefore do not need `Globals`.

**What might change:** `Globals` is a vanilla-managed UBO. If MC renames it or changes its layout, the `layout(std140) uniform Globals { ... }` block in the legendary shader must be updated to match. The layout is defined by vanilla, not by us.

### Debug UV shader (`gui_item_outline_debug_uv.fsh`)

Renders `fragColor = vec4(U, V, 0.0, 1.0)` where U and V are slot-relative (0..1 within the item's 16-px atlas slot). Red = U, Green = V. Used to verify that atlas-slot detection and guiScale derivation are correct. Enable via `outline_debug_uv: true` in the effect JSON. Not used in production.

---

## Migration checklist for future MC versions

When upgrading MC, check each of these in order:

1. **`GuiRenderer.executeDraw` signature** — if the method is renamed, the `@Inject method =` target in `GuiRendererExecuteDrawMixin` must change. If the `Draw` inner type is moved or publicised, `@Coerce` may become unnecessary. If the local variable order changes, `CAPTURE_FAILHARD` will catch it immediately.

2. **`GuiRenderer.submitBlitFromItemAtlas`** — if this method is renamed or split, `GuiRendererMixin` loses its injection point. Check whether new blit submission paths exist that also need to be intercepted.

3. **`ItemFeatureRenderer.getFoilRenderType`** — if the method signature or `useTransparentGlint` logic changes, `ItemFeatureRendererMixin` must be updated to replicate the new selection logic.

4. **`ItemModelResolver.updateForTopItem`** — if the resolve pipeline is restructured (e.g., recursive composite models are resolved differently), verify that hooking at `updateForTopItem` TAIL still sees the final, top-level item's effect and not an intermediate one.

5. **`ItemStackRenderState.clear` / `LayerRenderState.quads`** — if `quads` is replaced or reallocated each frame, `SUBMIT_EFFECT_MAP` key stability is broken.

6. **`GuiItemAtlas` slot size** — if the slot size changes from `16 * guiScale` texels, update the `slotW` computation in all three fragment shaders.

7. **`Globals` UBO layout** — if MC adds, removes, or reorders fields in the `Globals` uniform block, update the `layout(std140) uniform Globals { ... }` block in `gui_item_outline_legendary.fsh` and `gui_item_outline_debug_uv.fsh` to match.

8. **`RenderPass.setUniform` API** — if the UBO-binding API changes (e.g., binding-index based rather than name-based), update `FishtasticOutlineUboRegistry.bind`.

9. **`GpuBuffer` usage flags** — verify `USAGE_UNIFORM` and `USAGE_COPY_DST` constants are unchanged in new Blaze3D versions.
