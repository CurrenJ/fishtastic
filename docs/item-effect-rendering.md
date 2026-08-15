# Item Effect Rendering Architecture

This document describes how Fishtastic attaches custom visual effects to quality-tier items.
The primary goal of this doc is **long-term maintainability**: the vanilla rendering pipeline changes significantly between MC versions, so every non-obvious implementation decision below is explained with its constraint and the alternatives that were rejected. Future readers adapting this to a new MC version should be able to understand *why* the code looks the way it does and what to look for when a vanilla API changes.

---

## Overview

Three rendering paths are active per quality item:

- **Glint path** — replaces the enchantment foil texture on 3D world/entity/held items.
- **GUI outline path** — draws a shader-based outline around items in screen-space GUI slots.
- **World outline path** — draws the same outline around flat (texture-based) items rendered as item entities or in item frames, via a Fishtastic-owned item atlas.

All paths are entirely Mixin-based. Vanilla rendering is unchanged when no effect is active.

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

## World Outline Path

Extends the GUI outline aesthetic to item entities (dropped items) and item frame contents. Flat (texture-based) item models only — a flat "sticker" outline behind a 3D block model would look wrong, so models with depth > `0.0625` (vanilla's `FLAT_ITEM_DEPTH_THRESHOLD`) are skipped.

### Design decision record — alternatives considered and rejected

An earlier evaluation concluded that extending the outline shaders in-world was unfeasible because the GUI blit pipeline differs too much from world rendering. That conclusion was revised: the key realization is that the GUI path's *pattern* — "2D bake of the item in a known slot grid + a live outline shader sampling it" — transfers to world rendering even though the blit mechanism itself does not. The outline shader must stay live (not baked into a texture) because the legendary pinwheel animates via `GameTime`; what gets baked is only the vanilla item render.

| Alternative | Why rejected |
|---|---|
| **Bake item + outline into a static texture, render that on a quad** | Kills the legendary pinwheel animation (`GameTime`-driven, per-frame). Animated effects would require per-frame rebakes, erasing the win. Only the *item* is baked; the outline shader runs live on the world quad. |
| **Reuse vanilla `GuiItemAtlas` for the world quad** | Three blockers, detailed below. |
| **Sample the block atlas directly (no bake at all)** | The shader needs the sprite's UV rect to clamp the neighbour scan, and the only delivery channel is bit-packing it into vertex attributes (color/overlay) — the exact approach this codebase already abandoned for the UBO design (see "Why a UBO instead of packed shader parameters?"). Also loses the padding ring. |
| **Vanilla glowing-outline post-process (`EntityRenderState.outlineColor`)** | Nearly free (two-line mixin), but solid colour only — no falloff, opacity, width, or pinwheel — and renders through walls. A different visual language than the GUI outlines. Kept in mind as a cheap fallback, not shipped. |

### Why a Fishtastic-owned atlas instead of reusing `GuiItemAtlas`

The GUI path computes outlines live by sampling the vanilla `GuiItemAtlas`. That atlas cannot serve world rendering:

1. **Slot lifecycle** — its allocator reclaims any slot not used by a GUI item *this frame* (`reclaimSpaceFor`, `endFrame`). A world item not in any open GUI has no slot, or loses it mid-use.
2. **Bake timing** — it draws slots mid-GUI-render via `RenderSystem.outputColorTextureOverride`; forcing bakes from entity rendering would interleave with in-flight submission on the shared collector/dispatcher.
3. **No padding** — slots are exactly `16 × guiScale` px, so outlines clip at the sprite edge.

`FishtasticItemOutlineAtlas` fixes all three: padded slots (`SLOT_PX = 96`: a 64-px item render + 16-px margin per side, so outlines extend *past* the sprite edge — better than the GUI path), LRU eviction that never touches a slot used within the last frame, and all bakes at one safe point.

### Frame flow

1. **Bake** — `GameRendererMixin` calls `processBakeQueue()` at HEAD of `GameRenderer.render`, before any level/GUI submission. The bake borrows the shared `SubmitNodeStorage` / `FeatureRenderDispatcher` / `BufferSource` (same trio `GuiItemAtlas` uses) — safe only at this point because they are idle. This is two steps per slot: `drawToSlot` (a near-clone of `GuiItemAtlas.drawToSlot`, item scaled to `ITEM_RENDER_PX` but centered in the larger `SLOT_PX` slot) renders the item into the **mask** atlas, then `composeSlot` writes the finished outline ring into the **outline** atlas. `processBakeQueue` then calls `recomposeAnimatedSlots` for on-screen legendary items.
2. **Capture** — `ItemEntityRendererMixin` / `ItemFrameRendererMixin` inject at TAIL of `extractRenderState` (the only point where the `ItemStack` is in scope), look up the effect, call `requestSlot(stack, effect)`, and record `(effect, slotUVs)` in `WORLD_OUTLINE_MAP` keyed by the `ItemStackRenderState` identity. An unbaked item is queued and gets no outline for one frame (invisible in practice). Slot keys are `(item, components)` via `ItemStack.hashItemAndComponents` — count-insensitive, defensively copied.
3. **Submit** — the same mixins inject in `submit` at the exact `INVOKE` where the item model is submitted (`submitMultipleFromCount` for entities — pose carries bob + spin; `ItemStackRenderState.submit` for frames — pose carries frame rotation + 0.5 scale). One double-sided quad is added via `SubmitNodeCollector.submitCustomGeometry`, co-planar with the item, expanded by `SLOT_PX / ITEM_RENDER_PX` about the model-bounding-box centre so item texels stay aligned, UV'd to the full atlas slot.

   **The `mirrorU` flag:** display-context rotations applied *inside* `state.item.submit(...)` (per-layer `ItemTransform`) happen after our injection point and therefore never reach the outline quad. `item/generated`'s FIXED transform rotates the item 180° about Y so framed items face outward — without compensation the outline renders horizontally mirrored against the item. The frame mixin passes `mirrorU = true`, which swaps the quad's U coordinates; GROUND (item entities) has no such rotation and passes `false`.
4. **Draw** — the quad flows through `BufferSource.endBatch` → `RenderType.draw`, sampling the already-composed outline atlas. It needs no params UBO. `RenderTypeMixin` still wraps `RenderSystem.bindDefaultUniforms(renderPass)` (via `@Redirect`) to bind the effect's params UBO for the **bake** draws, which take the same path — the world counterpart of `GuiRendererExecuteDrawMixin`. Identity-map miss for vanilla pipelines, so per-draw cost is negligible.

### Pipelines and shaders — the outline is baked, not drawn

The outline ring used to be synthesized per-fragment at draw time. **It is now pre-baked into the atlas** so that a shaderpack cannot destroy it. This is the single most important thing to understand about this path; see the "Shaderpack (Iris) compatibility" section below for why.

Two textures, identical 1024² slot layouts:

| Texture | `TextureManager` id | Contents | Read by |
|---|---|---|---|
| Mask atlas | `fishtastic:item_outline_mask_atlas` | the item sprite, transparent padding | the bake shaders |
| Outline atlas | `fishtastic:item_outline_atlas` | the finished outline ring only, transparent elsewhere | the world draw |

Two textures rather than one because a single pass cannot both read and write the same texture — and keeping the mask resident is what makes per-frame re-baking of animated outlines cheap.

**Bake pass.** `ItemEffect.outlineBakeRenderType()` draws one full-slot quad into the outline atlas, sampling the mask atlas, using `outline_bake.fsh` / `outline_bake_legendary.fsh` (ports of the old draw-time shaders). Blending is off and the slot is cleared first, so the ring's alpha lands verbatim. These pipelines keep their custom shaders and are deliberately **not** registered with Iris: the bake runs offscreen against our own render target, outside the world pass, where no shaderpack can reach it.

Because the bake is always 1:1 atlas texels, the old `dFdx/dFdy` minification widening is gone — the derivative-derived factor is 1 by definition at bake time.

**Draw pass.** A single shared `RenderType` (`FishtasticWorldOutlineRenderer.OUTLINE_RENDER_TYPE`) covers every quality tier, because the finished outline carries no per-effect state. It uses `FishtasticRenderPipelines.WORLD_OUTLINE`, a clone of vanilla `ENTITY_TRANSLUCENT` with depth write disabled and culling off, bound to the outline atlas with `OutputTarget.ITEM_ENTITY_TARGET`.

Vertices are `NEW_ENTITY` format — position, **white** colour, UV, overlay, full-bright light, viewer-facing normal. White is mandatory: the tint was applied at bake time and any other vertex colour would modulate the baked ring. The full attribute set is mandatory too, because a shaderpack's `gbuffers_entities` program reads all of them.

The outline is **fullbright** — intentional: it reads as a glow in dark environments, consistent with the "quality glow" concept.

**Animated (legendary) outlines** re-run *only* the compose pass each frame via `recomposeAnimatedSlots()`; the item model is not re-rendered, since the mask atlas already holds its sprite. Cost is one full-slot quad per animated item on screen. Slots not touched within the last frame are skipped — an off-screen item's outline need not keep spinning. `GameTime` may be one frame stale (the bake runs at HEAD of `GameRenderer.render`, before the `Globals` UBO refresh), which is imperceptible at this rotation speed.

### Shaderpack (Iris) compatibility

When a shaderpack is loaded, Iris mixes into `GlDevice.getOrCompilePipeline` and swaps every pipeline for the pack's equivalent gbuffers program. Pipelines it does not recognise are **left alone** — it logs `Missing program <id> in override list` and lets ours run. That is not harmless: our shaders write a single `out vec4 fragColor` at location 0, while Iris has rebound world rendering to its own multi-attachment gbuffer using the pack's DRAWBUFFERS layout. The fragments land in a buffer the pack's deferred/composite passes never resolve as scene colour, so **the draw is silently discarded and the geometry is completely invisible under every shaderpack.**

`IrisCompat` fixes this by registering our world pipelines through Iris's versioned `api/v0` surface (via reflection — Iris is an optional, client-only, loader-specific dependency, and every method no-ops when it is absent):

| Pipeline | Iris program | Shadow |
|---|---|---|
| `WORLD_OUTLINE` | `ENTITIES_TRANSLUCENT` | none — a cosmetic overlay must not write the shadow map |
| `TANK_WATER_FILL` | `ENTITIES_TRANSLUCENT` | `SHADOW_TRANSLUCENT` |

**Do not "fix" the water fill to `BLOCK_TRANSLUCENT`.** It is the intuitive choice — the fill *is* a block entity's translucent surface — and it was tried first. It routes the quad into the pack's water/block program, which computes its own water colour, normals and waves and largely discards the incoming albedo: the fill rendered as a flat dark grey sheet with no texture detail and no tint, visibly darkening the tank. `ENTITIES_TRANSLUCENT` applies ordinary translucent shading and preserves both. The choice of Iris program is about *what shading treatment the pack applies*, not about what kind of object the geometry belongs to.

**The catch that drove the bake redesign:** assigning a pipeline makes the pack's fragment shader *replace* ours. For the water fill that is a straight win (it is a plain textured translucent quad, and it now picks up the pack's own water shading). For the outline it was fatal — the outline only existed inside our fragment shader, so under a pack we would have drawn the padded atlas slot verbatim: a blurry duplicate item sprite over every item. Pre-baking the ring into the atlas is what makes the assignment safe, because the outline then exists as ordinary pixels that any shader can sample.

GUI outline pipelines are **not** registered and must keep their own fragment shaders: GUI rendering happens after Iris releases the framebuffer, so those draws were never affected.

### Invalidation

`ItemEffectManager.clearCache()` (world join + data reload) calls `FishtasticItemOutlineAtlas.invalidate()` — drops slot bookkeeping immediately, defers the GPU clear to the next render-thread `processBakeQueue`.

**The deferred clear must never outlive a bake.** `invalidate()` arms the GPU-clear flag only if the atlas texture already exists, and `processBakeQueue` consumes the flag unconditionally before baking. The bug this guards against (shipped and fixed): `clearCache()` fires on world join *before* the first bake, so the flag was armed with no texture; the flag-check skipped (null texture) but stayed armed, the texture was created and the first batch of items baked that same frame — and one frame later the still-armed flag wiped the whole atlas. Those slots stayed marked `baked` but empty, so the first quality items seen each session permanently lost their world outlines until a world reload re-invalidated cleanly.

### Resource cost

- **GPU memory (fixed):** 12 MiB — 1024² RGBA8 outline atlas (4 MiB) + 1024² RGBA8 mask atlas (4 MiB) + a shared DEPTH32 depth texture (4 MiB), allocated once on first bake, never resized. 100 slots.
- **Bake cost (amortized to ~zero):** one small ortho item render per unique (item + components) combo — the same cost as vanilla rendering one GUI inventory slot, paid once per combo and then reused indefinitely. Worst case is a burst frame when many distinct quality items first become visible, comparable to opening a full chest in the GUI (which vanilla re-bakes every invalidation anyway).
- **Per-frame CPU (negligible):** per visible quality item, two hash-map lookups and a 4-vertex quad submission. Effect lookup is cached (`ItemEffectManager.CACHE`). One extra draw batch per *effect tier* on screen, not per item — quads sharing a render type batch together.
- **One global hook:** `RenderTypeMixin` adds an `IdentityHashMap` miss to **every** immediate render-type draw game-wide (~nanoseconds each). Same unavoidable pattern as `GuiRendererExecuteDrawMixin` on the GUI side — there is no UBO unbind API, so the bind must be checked per draw.
- **Fragment cost:** the `(2·radius+1)²` alpha-sample loop now runs at **bake** time over one 96² slot, not per screen fragment — a fixed, distance-independent cost paid once per item combo. The world draw itself is a plain textured quad. Animated (legendary) slots pay that 96² bake every frame while on screen, which is the deliberate trade for keeping the pinwheel spinning.
- **CPU memory:** atlas slot keys hold up to 100 defensive `ItemStack` copies; `WORLD_OUTLINE_MAP` holds one small record per live item render state with an active outline.

### Limitations

1. **Flat items only** (by design) — models thicker than vanilla's flatness threshold are skipped silently.
2. **One-frame delay** before a newly-seen (item + components) combo's outline appears: the bake is queued at extract and processed at the next frame head. Imperceptible in practice.
3. **100-slot capacity** for distinct (item + components) combos visible in-world at once, with LRU eviction beyond that. Important nuance: slots are keyed by *components*, so items carrying per-instance unique data (e.g., per-catch fish sizes) each consume a slot. A ground littered with 100+ unique quality items starts dropping outlines (oldest-seen first). See future improvements for the fix.
4. **Animated item models bake once** — vanilla redraws animated GUI slots every frame; this atlas does not. An animated quality item model would show a stale silhouette. None exist today.
5. **Fullbright** — the outline ignores the lightmap (intentional, see above), a divergence from how the item itself renders.
6. **Stacked clusters** (`count > 1`) get one outline at the primary copy's position, not one per rendered copy.
7. **Coverage:** item entities and item frames only — not held items, armor stands, or display entities.
8. **Residual aliasing at distance:** the atlas has no mipmaps, so far-away outlines shimmer slightly. The minification-adaptive radius prevents disappearance but not all aliasing.
9. **GUI-context bake vs. GROUND/FIXED-context model:** the bake renders with `ItemDisplayContext.GUI`; the world quad is aligned to the world-context bounding box, and in-submit display rotations never reach the quad. The known case — FIXED's 180° Y rotation in item frames — is compensated by the `mirrorU` flag, which assumes the standard `item/generated` display transforms. A custom quality-item model with *other* GUI/GROUND/FIXED rotations would bake a silhouette that mismatches its world orientation.

### Concerns (watch list)

- **The frame-head bake borrows shared renderer state** (`SubmitNodeStorage` / `FeatureRenderDispatcher` / `BufferSource`) on the assumption they are idle at HEAD of `GameRenderer.render`. True today — extraction fills entity render states only, and all submission happens later inside `render` — and it is the same assumption `GuiItemAtlas` makes mid-GUI-render. A mod (or vanilla change) submitting geometry outside the normal frame flow could interleave.
- **The bake clobbers the global projection matrix without restoring it** (exactly as `GuiItemAtlas.drawToSlot` does). Benign at frame head because level and GUI rendering set their own projections afterwards — do not move the bake point without rechecking this.
- **`WORLD_OUTLINE_MAP` lifetime** rides on `ItemStackRenderState.clear()` being called, the same contract `GUI_EFFECT_MAP` already relies on. If a future MC version stops pooling/clearing entity render states, entries linger until the states are unreachable (bounded, but recheck during version migrations).
- **Static-init ordering in `FishtasticItemOutlineAtlas`:** `STACK_STRATEGY` must be declared before `INSTANCE` — the singleton constructor builds a map with it. This already caused one NPE crash during development; the field order is load-bearing and commented.

### Future improvements (rough value order)

1. **Key slots by model identity** (resolve with `TrackingItemStackRenderState` at capture time) instead of (item + components) — collapses per-instance-unique items into one slot per visual appearance, eliminating limitation #3. Medium effort.
2. **Distance fade** — fade outline alpha past ~20 blocks; hides residual aliasing (#8) and cheapens far fragments.
3. **Optional lightmap modulation** (`outline_fullbright: false` per effect) if outlines should ever respect darkness.
4. **Extend coverage** to armor stands / display entities — the capture/submit mixin pattern transfers directly, one mixin per renderer.
5. **Per-frame rebake for animated models** — only needed if an animated quality item ever exists (#4).

**What might change:**
- `GuiItemAtlas.drawToSlot` is the template for our bake; if vanilla changes how it sets up ortho projection / lighting / output overrides, mirror those changes in `FishtasticItemOutlineAtlas.drawToSlot`.
- `ItemEntityRenderer.submitMultipleFromCount` and the `state.item.submit(...)` call in `ItemFrameRenderer.submit` are `INVOKE` injection targets; signature changes break them loudly at startup (`defaultRequire: 1`).
- `RenderType.draw` is the world UBO-bind site. If the `bindDefaultUniforms` call moves or the draw path splits (e.g., instanced item rendering), `RenderTypeMixin` loses coverage.

---

## Pipeline Factory — `FishtasticRenderPipelines`

Each `ItemEffect` gets its own `RenderPipeline` **instance**. Two effects using the same shader variant still get separate instances.

**Why one pipeline per effect?** The MC 26.1 rendering system uses `RenderPipeline` object identity to group and batch draws. If two effects shared a pipeline instance, `FishtasticOutlineUboRegistry` would need to unbind and re-bind the UBO for every draw — but there is no "unbind" API. Having distinct pipeline instances means each draw carries its own UBO binding unambiguously.

Five factory methods, one per shader variant:

| Factory | Shader | UBO name | Path |
|---|---|---|---|
| `createOutlinePipeline(id)` | `gui_item_outline` | `BasicOutlineParams` | GUI |
| `createLegendaryOutlinePipeline(id)` | `gui_item_outline_legendary` | `LegendaryOutlineParams` | GUI |
| `createDebugUvPipeline(id)` | `gui_item_outline_debug_uv` | `DebugUvOutlineParams` | GUI |
| `createOutlineBakePipeline(id)` | `outline_bake` | `BasicOutlineParams` | Bake |
| `createOutlineBakeLegendaryPipeline(id)` | `outline_bake_legendary` | `LegendaryOutlineParams` | Bake |

The `Identifier` location passed to each factory is derived from the effect's texture path to guarantee uniqueness across all loaded effects:
```
fishtastic:pipeline/gui_item_outline_{namespace}_{path_slashes_as_underscores}
fishtastic:pipeline/outline_bake_{namespace}_{path_slashes_as_underscores}
```

The per-effect rule does **not** apply to the world draw: `WORLD_OUTLINE` is a single shared pipeline with no params UBO, because the outline is fully baked by the time it is drawn.

All pipelines declare `DynamicTransforms` and `Projection` (standard blit UBOs), their effect-specific params UBO, `Sampler0`, and `POSITION_TEX_COLOR / QUADS` vertex format. The legendary variants additionally declare `Globals` because their shaders read `GameTime` for animation. GUI pipelines use `TRANSLUCENT` blend; the bake pipelines disable blending (`ColorTargetState.DEFAULT`) and depth (`ALWAYS_PASS`, no write) so the ring's alpha is written verbatim into a freshly cleared slot, and inject the atlas slot geometry as shader defines (`FISHTASTIC_ATLAS_SLOT_PX`, `FISHTASTIC_ATLAS_RES`). The debug-UV variant has no world equivalent; world rendering falls back to the basic shader for effects with `outline_debug_uv: true`.

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

7. **`Globals` UBO layout** — if MC adds, removes, or reorders fields in the `Globals` uniform block, update the `layout(std140) uniform Globals { ... }` block in `gui_item_outline_legendary.fsh`, `gui_item_outline_debug_uv.fsh`, and `outline_bake_legendary.fsh` to match.

8. **`RenderPass.setUniform` API** — if the UBO-binding API changes (e.g., binding-index based rather than name-based), update `FishtasticOutlineUboRegistry.bind`.

9. **`GpuBuffer` usage flags** — verify `USAGE_UNIFORM` and `USAGE_COPY_DST` constants are unchanged in new Blaze3D versions.

10. **`RenderType.draw` / `RenderSystem.bindDefaultUniforms`** — `RenderTypeMixin` (world UBO binding) `@Redirect`s the `bindDefaultUniforms(renderPass)` call inside `draw`. If the call is renamed, moved, or the immediate draw path splits, the redirect fails loudly at startup; re-locate the window between `setPipeline` and the draw call.

11. **`ItemEntityRenderer.submit` / `submitMultipleFromCount`** — `ItemEntityRendererMixin` injects at the `INVOKE` of the 6-arg static `submitMultipleFromCount` overload (the point where the pose carries bob + spin). Signature or overload changes break the `INVOKE` target.

12. **`ItemFrameRenderer.submit`** — `ItemFrameRendererMixin` injects at the `INVOKE` of `ItemStackRenderState.submit(PoseStack, SubmitNodeCollector, int, int, int)` on the non-map item branch. If the frame renderer restructures (e.g., merges the map/item branches), verify the injection still fires only for items.

13. **`GuiItemAtlas.drawToSlot`** — the template for `FishtasticItemOutlineAtlas.drawToSlot` (ortho projection setup, lighting entry selection, output-texture overrides, scissor). Diff the vanilla method against ours on every upgrade and mirror changes.

14. **`GameRenderer.render(DeltaTracker, boolean)`** — the bake-queue hook (`GameRendererMixin`) targets this signature at HEAD. The bake must stay *before* all level/GUI extraction-submission so the shared `SubmitNodeStorage` / `FeatureRenderDispatcher` / `BufferSource` are idle when borrowed.

15. **`EntityRenderState` pooling** — `WORLD_OUTLINE_MAP` (like `GUI_EFFECT_MAP`) relies on `ItemStackRenderState.clear()` being invoked when render states are recycled. If entity render states stop being pooled/cleared, switch the map to weak keys or per-frame clearing.
