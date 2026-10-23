# Tank rendering architecture

Tank appearance combines shape geometry with runtime frame/sand/glass materials and
cosmetics. Shape/material persistence and generation are covered in
[fish-tanks.md](fish-tanks.md); fish runtime ownership/budget in
[fish-tank-group-scaling.md](fish-tank-group-scaling.md).

## Shared data and geometry

A block entity's material and connection state is snapped into
`FishTankCompositeModelData` on the main thread, then read during chunk meshing.
Do not read mutable block-entity state directly from a background mesher. The
snapshot includes cosmetics and diagonal/edge information; model/cache keys must
include all inputs that change visible geometry, not only the six face bits.

Each shape has 64 open-face permutations of frame, sand and glass, plus applicable
corner and edge fragments. `Direction.ordinal` agrees with the standalone generator's
`TankFace`. `FishTankShape` selects the prefix; preload all shape dependencies rather
than allowing non-standard shapes to fall back silently to STANDARD.

Shared `client/compositemodel` utilities include:

- `BlockstateModelScanner` / `BlockstateRedirectRegistry`: scan blockstates on
  resource reload for actual model redirects instead of guessing `block/<id>`.
- `BlockModelPathResolver`: redirect/fallback lookup. Ignore builtin-missing
  sentinel models so a failed candidate cannot prevent later fallbacks.
- `CompositeTextureHelper`: resolve materials and apply texture overrides.
- `TankCosmeticMesh`: immutable decoration snapshot, transformed mesh and tint data.

## Loader boundaries

| Loader | Integration |
|---|---|
| Fabric | Fabric model loading/render-data hooks and its own geometry bake signature; emitter vertex colors carry cosmetic tint |
| NeoForge | Model events and model-data snapshot; extended geometry bake properties; baked quad colors carry tint |
| Forge | `IForgeBlockStateModel.collectParts` receives refreshed `ModelData`; `FishTankModelData`, `FishTankModelResources`, `FishTankBakedModel` and `FishTankItemModel` supply adapter-specific loading/baking |

Forge and NeoForge APIs are separate. Preserve per-loader model reload registration,
block data refresh, item presentation and material/tint handling. A block entity update
must invalidate model data/section geometry immediately; a successful inventory render
does not validate placed tank behavior. Forge checks must include non-standard shapes,
all connection layouts and corner/edge pieces. Cosmetic tint handling lives in
`FishTankCosmeticTints` on Forge.

## Caching traps

`ResolvedModel.bakeTopGeometry` caches by ModelState rather than supplied TextureSlots
in this Minecraft line. Calling it repeatedly for different material overrides can
return the first tank's material. Bake through `getTopGeometry().bake(...)` with the
loader's correct signature, preserving additional properties where required.
Particle-material instance caches have the same problem: use the uncached/static
resolution path with the intended slots.

Cache identity must distinguish shape, connections/diagonals, materials and cosmetic
snapshot. Resource reload invalidates both resolved models and redirect/tint inputs.
Two differently decorated tanks must never borrow each other's cached mesh.

## Static and live cosmetics

Static cosmetics are baked into chunk meshes, avoiding per-part per-frame submission.
Snapshot each block state and pose in the drawing tank's block space. A spanning
structure's parts are assigned to member tanks; its generator cull mask drops faces
against solid neighbors. Bake transformed quads in the unculled bucket with the tank's
lighting/AO convention. Resolve tint from the cosmetic block, not from the tank block.

Column kelp/hanging strands and spans are stored once but drawn in every relevant
member tank. `TankCosmeticMesh.refreshDependents` refreshes their dependent model data
and sections after updates. Dev transform changes bump `CosmeticTransforms.version`
and trigger remeshing. Otherwise parts disappear when the storing tank is culled.

Fish, water, special-rendered chests, grouped moving/glowing reaction parts and particle
emitters retain live rendering. `CosmeticStructure.liveParts()` preselects chest and
furnace work instead of scanning all parts each frame. Keep static/reaction grouping
consistent so a part is neither omitted nor drawn twice. Historical performance
methodology is in [cosmetic-render-cost.md](cosmetic-render-cost.md).

## Checks

Use the isolated [render launcher](../tools/README.md) with tank, shapes, pertank,
spanpreview, lighting and applicable Forge review scenes. Include placed, held and
inventory tanks; edit shape/material/cosmetics, reload resources, change camera
sections and compare different decorations. Waxed copper is a useful redirect test:
its blockstate points at an unwaxed model. Walls and infested blocks test other
nontrivial redirects. Check tinted leaves/ferns and animation as well as geometry.
