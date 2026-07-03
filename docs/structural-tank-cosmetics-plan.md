# Structural (Multi-Block) Tank Cosmetics — Implementation Plan

> **Status: Design / Not yet implemented.** Extends the existing single-block cosmetic system
> (`PlacedCosmetic`, `CosmeticGridCell`, `CosmeticTransforms`, `FishTankBlockEntityRenderer.renderCosmetics`)
> with a second cosmetic type composed of multiple blocks arranged across a reserved multi-cell footprint
> in the tank's 3×3 floor grid (e.g. a small arch built from fence posts). Existing single-block cosmetics
> are untouched and remain fully backward compatible — this is purely additive.

Check items off as they land. Phases are ordered by dependency; later phases assume earlier ones are done.

---

## Phase 0 — Risk spike: confirm dynamic registry client sync

Structural cosmetics need their definitions on **both** server (collision/footprint validation) and
client (rendering) — unlike `CosmeticTransforms`, which is a client-only resource-reload map. This must
be confirmed before any other phase is built on top of it.

- [ ] Verify whether the existing dynamic registries in `FishtasticRegistries` (`Quest`, `ShopEntry`,
  `FishTankFrameType`) are actually synced from server to client (registry sync packet on login/reload),
  or whether they're server-only and the client currently gets equivalent data some other way.
- [ ] If none of the existing registries are client-synced, determine the smallest working NeoForge +
  Fabric (Architectury) pattern for syncing a new dynamic registry, referencing `08-networking.md` and
  `02-registration.md` from the modding guide.
- [ ] Confirm registry access pattern for use inside `FishTankBlockEntityRenderer` (client render thread)
  — almost certainly `level.registryAccess().registryOrThrow(...)`, but confirm `BlockEntityRenderer`
  has a `Level` reference available at render/`extractRenderState` time.

---

## Phase 1 — `CosmeticStructure` data model

- [ ] Add `CosmeticStructure` record + codec in `common/src/main/java/grill24/fishtastic/fishtank/`
  (mirrors `CosmeticTransforms.Transform`'s `RecordCodecBuilder` style):
  ```java
  record CosmeticStructure(List<GridOffset> footprintCells, List<StructurePart> parts, float unitScale)
  record GridOffset(int dx, int dz)
  record StructurePart(BlockState state, float offsetX, float offsetY, float offsetZ)
  ```
- [ ] `footprintCells` must include `(0,0)` (the anchor). Validate at codec/registry-load time; reject
  (log + skip, don't crash datapack load) a structure missing the anchor cell.
- [ ] Validate at load time that `footprintCells` fits within a 3×3 bounding box in **every** rotation
  (i.e. both its width and depth are ≤ 3) — a structure that can never legally be placed is an authoring
  error, not a runtime surprise.
- [ ] Register `CosmeticStructure` as a new `ResourceKey<Registry<CosmeticStructure>>` dynamic registry in
  `common/src/main/java/grill24/FishtasticRegistries.java`, following the existing `Quest`/`ShopEntry`
  pattern confirmed in Phase 0.
- [ ] Datagen: add a `RegistryDataGenerator`-style provider (per `06-datagen.md`) so structure JSONs land
  under `data/fishtastic/cosmetic_structure/<id>.json` via the normal datagen pipeline, not hand-copied.

## Phase 2 — Rotation utilities

Build and unit-test these in isolation before wiring them into placement or rendering — rotation bugs are
much cheaper to catch here than downstream.

- [ ] Add a small helper (e.g. `CosmeticStructures.rotateOffset(Rotation, double x, double z)`) using the
  identical 2D matrix `net.minecraft.world.level.block.Rotation` uses for `BlockPos` (CW90: `x',z'=-z,x`;
  180: `-x,-z`; CCW90: `z,-x`) — for continuous float part offsets, which `BlockPos.rotate` can't handle.
- [ ] Footprint cell rotation reuses vanilla directly: `rotation.rotate(new BlockPos(dx, 0, dz))`,
  discarding Y. Do **not** hand-roll a second formula for this — reuse the vanilla call.
- [ ] Confirm both rotations pivot around the same origin (the structure's local `(0,0,0)`, i.e. the
  anchor cell) so footprint cells and part offsets never drift out of sync with each other.
- [ ] Part `BlockState` rotation reuses `state.rotate(rotation)` directly (vanilla's own
  `StructureTemplate` rotation mechanism) — handles 4-way `FACING`, `AXIS`, and 16-way `ROTATION`
  (signs/banners) uniformly. No per-property special-casing needed here.

## Phase 3 — Item association

- [ ] Add `FishTankStructureCosmeticItem` in `common/src/main/java/grill24/fishtastic/item/`, parallel to
  `FishTankCosmeticItem` (`item/FishTankCosmeticItem.java:14-34`) but holding a
  `ResourceKey<CosmeticStructure>` instead of a `Block`, with a static `BY_STRUCTURE` lookup map.
- [ ] Register at least one instance in `FishtasticItems.java` (e.g. `cosmetic_fence_arch`) for use as the
  Phase 8 test content.
- [ ] Item model/icon: decide whether the item uses a hand-authored 2D icon or a simple 3D composite
  (lowest effort: flat icon texture, same as existing cosmetic items).

## Phase 4 — Block entity storage

Extends `FishTankBlockEntity` (`blockentity/FishTankBlockEntity.java`) alongside the existing `cosmetics`
map (`:78`) — do not modify that map or its NBT format.

- [ ] Add `PlacedStructureCosmetic(ResourceKey<CosmeticStructure> structureId, Rotation rotation)` record.
- [ ] Add `Map<CosmeticGridCell, PlacedStructureCosmetic> structureCosmetics` (anchor cells only).
- [ ] Add `Map<CosmeticGridCell, CosmeticGridCell> structureCellIndex` (any occupied cell → its anchor),
  **derived, not persisted** — rebuild on load by re-rotating the loaded structure's `footprintCells`
  against its stored `rotation`.
- [ ] NBT save/load: extend `saveAdditional`/`loadAdditional` (`:269-288`, `:365-397`) with a second child
  list for `structureCosmetics` (anchor gridX/gridZ, structure id, rotation) — separate list from the
  existing single-block cosmetic list, not merged into it.
- [ ] Decide and implement missing-structure-id fallback (datapack removed/renamed after placement):
  skip rendering + log, per the earlier design discussion — do not throw during load.
- [ ] Add accessors mirroring `getCosmetics()/setCosmetic()/removeCosmetic()` (`:606-625`) for the
  structure maps.

## Phase 5 — Placement & collision

Extends `FishTankBlock` (`block/FishTankBlock.java`).

- [ ] Determine rotation at placement time from the placing player's horizontal facing (4-way), no new UI.
- [ ] On right-click with a `FishTankStructureCosmeticItem`: resolve anchor cell via the existing raycast
  (`findTargetedCell`, `:320-334`/`:360-371`), then rotate `footprintCells` (Phase 2) by the chosen
  rotation and translate by the anchor.
- [ ] Validate **every** resulting footprint cell: in-bounds (0–2, 0–2) and unoccupied in both the
  existing `cosmetics` map and the new `structureCellIndex`. Reject with an action-bar message on any
  failure — mirror how normal block placement already rejects against existing entities.
- [ ] **Explicitly rotate `footprintCells` before running the bounds/overlap check** — never validate the
  unrotated shape and render the rotated one (flagged in design as an easy off-by-rotation bug).
- [ ] On success: write one `structureCosmetics` entry at the anchor, backfill `structureCellIndex` for
  every footprint cell including the anchor itself (so removal logic, Phase 6, is uniform regardless of
  which cell was clicked).

## Phase 6 — Removal & hit-testing

- [ ] Extend `findTargetedCosmetic` (`:336-358`) to also test against the union of AABBs for a placed
  structure's rotated footprint cells, not just single-cell AABBs — so aiming anywhere within the
  structure's bounding volume hits it, matching existing single-cosmetic behavior.
- [ ] Removal (empty hand + `FishTankEditModeManager.isInEditMode`): look up the clicked cell in
  `structureCellIndex` → resolve anchor → clear the anchor entry from `structureCosmetics` and every
  `structureCellIndex` entry pointing at that anchor.
- [ ] Confirm edit-mode outline/highlight rendering (wherever the single-cosmetic outline is drawn) is
  extended to draw the full footprint outline for structures, not just the targeted cell.

## Phase 7 — Rendering

Extends `FishTankBlockEntityRenderer.renderCosmetics` (`client/renderer/FishTankBlockEntityRenderer.java:342-398`).

- [ ] Extend `extractRenderState` (`:102-146`) to also snapshot `blockEntity.getStructureCosmetics()`
  (or equivalent) into `FishTankRenderState`.
- [ ] Add a second branch in `renderCosmetics`: for each placed structure, look up its `CosmeticStructure`
  via the client-synced registry (Phase 0), translate to the anchor cell's `localX()/localZ()` + `FLOOR_Y`,
  apply the placement rotation, then loop `parts`:
  - rotate each part's offset (Phase 2 helper) and translate,
  - compute `part.state().rotate(rotation)` for the actual rendered state,
  - submit via the existing `blockModelResolver.update(...)`/`submit(...)` calls (`:391-393`), reused
    as-is — same block-model rendering path as single-block cosmetics, just looped over N parts.
- [ ] **Fix the chest special case** (`:363-370`, `:376-379`, `:410-439`): if a structure part is a chest,
  its baked `ChestModel` pose logic currently reads the *authored* `FACING` directly. It must instead
  read `FACING` off the **rotated** state (`chestState.rotate(structureRotation)`) before computing the
  pose — otherwise a chest inside a rotated structure renders facing the wrong way while the rest of the
  structure rotates correctly. This is the one call site flagged during design as needing an explicit fix.
- [ ] Confirm kelp height-stacking (`:380-390`) composes correctly with structure rotation — kelp has no
  directional blockstate property, so only its offset needs rotating, not its state; should work
  automatically via the generic path, but verify visually.
- [ ] Any future special-cased render part (beyond chest/kelp) must follow the same rule: compose
  structure rotation into its bespoke facing logic, not just its position.

## Phase 8 — Content, verification, and polish

- [ ] Author one real structure JSON as the reference example (the "little arch" — e.g. 2 fence posts +
  a fence gate or slab, footprint `[(0,0),(1,0)]`), by hand per the design decision to use hand-written
  JSON authoring for v1.
- [ ] Wire `cosmetic_fence_arch` (Phase 3 item) to a shop entry or crafting recipe, matching how existing
  cosmetics (`cosmetic_sea_lantern`, `cosmetic_treasure_chest`) are unlocked, so it's reachable in-game
  for testing.
- [ ] In-game verification (`/run` skill or manual): place the structure at all 4 rotations and confirm
  visually that (a) parts move as a rigid unit around the anchor cell, (b) directional blockstates (if the
  test structure includes one, e.g. fence gate `FACING`) rotate correctly, (c) footprint collision rejects
  overlapping placement and out-of-bounds placement at every rotation, (d) removal via edit mode clears
  the whole structure from any cell clicked, not just the anchor, (e) the structure survives a world
  reload (NBT round-trip) with rotation intact.
- [ ] Update `docs/tank-customization-expansion.md` / `wiki/Fish-Tank.md` if structural cosmetics should
  be reflected there once shipped.
