# Fish Tanks

The fish tank is a single block whose appearance is assembled at render time from three
independent axes, plus a decoration layer:

| Axis | What it controls | Stored as | Where it's chosen |
|---|---|---|---|
| **Shape** | The body geometry — silhouette of frame, glass, and sand | `fishtastic:fish_tank_shape` data component → `FishTankShape` enum | Shape-cycle button in the Fish Tank Assembly GUI; also baked into shop-entry rewards |
| **Material** | Which blocks texture the frame / sand / glass | `fishtastic:fish_tank_materials` → `FishTankMaterials` | Fish Tank Assembly GUI (place blocks in slots); shop-entry rewards |
| **Connections** | Which of the 6 faces are open to a neighbor | Derived at runtime, not stored | Automatic, from world adjacency |
| **Cosmetics** | Decorations placed on the tank floor | `PlacedCosmetic` list on the block entity | Right-click with a cosmetic item |

The three axes are orthogonal by design: a new shape needs no texture work, and a new material
needs no geometry work. This document covers the shape and material pipeline end to end, points at
where rendering and cosmetics live, and records the current limitations.

---

## 1. The permutation model

Every shape ships **192 pre-generated block models**: 64 permutations × 3 parts (frame, sand,
glass). The permutation index is a 6-bit mask of *open* faces, one bit per
`net.minecraft.core.Direction` **ordinal**:

| Bit | 0 | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|---|
| Face | DOWN | UP | NORTH | SOUTH | WEST | EAST |

An open face means "there is a connected tank on that side," so that face's glass, wall, and corner
posts are omitted and the two tanks read as one continuous volume.

Two places encode this mask and they must agree:

- `FishTankCompositeModelData.getPermutationIndex()` (common, at runtime) — iterates
  `Direction.values()` and shifts by `Direction#ordinal`.
- `TankFace` (`tools/tank-shape-gen`, at datagen time) — a plain-Java enum that deliberately
  mirrors `Direction`'s ordinal order so the standalone geometry library needs no Minecraft
  dependency. If vanilla ever reorders `Direction`, `TankFace` must move with it.

Models live at `assets/fishtastic/models/block/<modelPathPrefix>/fish_tank_{frame,sand,glass}_<0-63>.json`,
where `modelPathPrefix` comes from the `FishTankShape` enum entry.

## 2. The shape axis

`common/src/main/java/grill24/fishtastic/fishtank/FishTankShape.java` is a code-defined enum —
shapes are curated content shipped with the mod, not something datapacks add. Each entry carries
three things:

```java
STANDARD(Fishtastic.id("standard"), Fishtastic.id("standard"), "fishtankbase")
//       ^ id                       ^ connectionCollection     ^ modelPathPrefix
```

**Eight shapes ship today**, all sharing the `standard` connection collection so every shape connects
to every other:

| Shape | Model prefix | Geometry summary |
|---|---|---|
| `STANDARD` | `fishtankbase` | Uniform 1px corner posts. The original tank; `CornerTaperProfile.uniform(1)`. |
| `STURDY` | `fishtank_sturdy` | Uniform 2px frame (no taper) with chamfered octagonal cap rings; square sand inset 2. |
| `TRIMMED` | `fishtank_trimmed` | Light 3→1 corner brace. |
| `REINFORCED` | `fishtank_reinforced` | Chunkier 5→1 corner brackets. |
| `FACETED` | `fishtank_faceted` | 16→2 taper with 2px-thick chamfered octagonal cap rings and flat-plate frame; stepped-octagon sand. |
| `BASTION` | `fishtank_bastion` | 16→2 taper with full-width caps rendered as chamfered octagonal base rings. |
| `ORNATE` | `fishtank_ornate` | Standard 1px frame plus decorative inlay brackets, with glass holes behind them. |
| `SHAGGY` | `fishtank_shaggy` | ORNATE's construction with a shaggier, deliberately asymmetric fringe; a full-width band at Y `[1,2]` hides the sand from the side. |

`STANDARD` and `SKYLIGHT` are always available. Every other shape is quest-gated: `unlockQuests` on
the enum entry (mirrored by `unlock_quests` on its shop entry) lists a small set of quests, and
claiming *any one* of them unlocks the shape. Every quest in that list grants a matching tank of that
shape as its own reward, so a player pursuing a different chain from the shape's "primary" quest
still walks away with a tank in hand, not just the unlock flag — but each path's tank is themed with
its own frame/sand/glass materials rather than cloning the primary's, so a different grind feels like
a distinct reward rather than a recolor-free duplicate. Keep all three in sync by hand when adding a
path: the enum's `unlockQuests`, the shop entry's `unlock_quests`, and that quest's own `fish_tank`
reward item (shape must match; materials are free to differ) — nothing enforces this structurally.

| Shape | Unlocked by (any one) |
|---|---|
| `STURDY` | `tutorial/first_catch` (catch any fish) *or* `mastery/bluegill_novice` |
| `TRIMMED` | `mastery/angler_apprentice` (catch 50 fish) *or* `mastery/gar_hunter` |
| `REINFORCED` | `mastery/angler_journeyman` (catch 100 fish) *or* `mastery/tetra_scholar` |
| `HONED` | `mastery/angler_master` (catch 250 fish) *or* `mastery/bluegill_master` |
| `RAMPART` | `mastery/angler_legend` (catch 500 fish) *or* `mastery/gar_legend` |
| `FACETED` | `challenge/sunrise_ambush` (5 frenzied @ Rare+ at dawn) *or* `mastery/gar_veteran` |
| `BASTION` | `collector/nether_collector` (every Nether species) *or* `challenge/predator_run` |
| `ORNATE` | `challenge/daily_completionist` (clear every daily in a day) *or* `mastery/tetra_legend` |
| `SHAGGY` | `challenge/storm_prize` (Epic+ in a thunderstorm) *or* `mastery/tetra_tracker` |

### Connection gating

`FishTankBlockEntity.updateConnections` opens a face only when the neighbor is a
`FishTankBlockEntity` *and* its shape's `connectionCollection()` equals this one's. Collection id is
deliberately decoupled from shape identity so a curated family of shapes can interconnect without
being the same shape.

The mechanism *defaults* a new shape to its own singleton collection (its own id), meaning a newly
added shape connects only to itself until someone opts it into a family. All eight shipped shapes were
deliberately grouped into `standard`.

### Persistence and sync

The shape rides the same rails as materials, so mirror `FISH_TANK_MATERIALS` whenever you touch it:

- `FishtasticDataComponents.FISH_TANK_SHAPE` — `Codec` + `StreamCodec` on `FishTankShape`
  (`stringResolver` by serialized name for disk, `idMapper` by ordinal for network).
- `FishTankBlockEntity` — `shape` field, persisted via `saveAdditional`/`loadAdditional`, threaded
  through `collectImplicitComponents`/`applyImplicitComponents`, and copied by
  `FishTankBlock.getCloneItemStack`.
- `FishTankCompositeModelData` — carries `shape` alongside the three blocks; produced by
  `FishTankBlockEntityFabric.getRenderData()` and `FishTankBlockEntityNeoForge.getModelData()`.
- `SetAssemblyShapePacket` + `FishTankAssemblyMenu`'s `shapeSlot` — the GUI's cycle button changes
  the shape client-side optimistically, then the server authoritatively mirrors it back through the
  menu data slot.

### Model loading

Both platforms load *every* shape's 192 models up front:

- `FishTankBlockStateModelFabric` / `FishTankBlockStateModel` (NeoForge) loop over
  `FishTankShape.values()` when declaring dependencies and baking, building
  `Map<FishTankShape, ResolvedModel[]>` per part rather than three flat arrays.
- `FishTankBakedModelFabric` / `FishTankBakedModel` key their composite cache on
  `(shape, frame, sand, glass, permutation)`.

This means **adding a shape multiplies the baked-model dependency count by 192**. It's cheap today
at seven shapes; it is the thing to watch if the catalog grows large.

## 3. Geometry generation (`tools/tank-shape-gen`)

Shape geometry is *not* hand-authored JSON. It's generated by a plain-Java library with no
Minecraft or Loom dependency (Gson only), so the same code runs in two places:

```
tools/tank-shape-gen         ← the geometry library (single source of truth)
  ├── used by → fabric/…/datagen/FishTank{Frame,Glass,Sand}ModelProvider   (real datagen)
  └── used by → tools/tank-shape-previewer                                  (JavaFX live preview)
```

The previewer renders the *same generator output* converted to a JavaFX `TriangleMesh` — one quad
per named face, so a face the generator omits is genuinely absent rather than hidden. That's what
makes it a trustworthy check on geometry rather than a separate implementation that can drift.

### The profile: `CornerTaperProfile`

Most shapes are described by one 14-entry integer array: the corner post's **reach-in from the true
corner at each height**, read off a pixel-mapped reference image. Index 0 is the row just under the
ceiling, index 13 the row just above the floor. Image rows 0 and 15 are always the fixed 1px
ceiling/floor caps and are not part of the profile.

```java
STURDY     = {16, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 16}
TRIMMED    = {3, 2, 1,1,1,1,1,1,1,1,1,1, 2, 3}
REINFORCED = {5, 3, 2, 2, 1,1,1,1,1,1, 2, 2, 3, 5}
FACETED    = {16, 4, 3, 2, 2, 2, 2, 2, 2, 2, 2, 3, 4, 16}
BASTION    = {16, 6, 4, 3, 3, 2,2,2,2, 3, 3, 4, 6, 16}
```

The four tapered shapes are two **light/chunky pairs** that share one curvature pattern.
`TRIMMED`→`REINFORCED` and `FACETED`→`BASTION` deepen the corner taper by the *same* per-row delta —
`+2` on the outermost tapered row, `+1` on the three rows inside it, `0` at the waist — so the
chunky member reads as a heavier brace on the light member's silhouette, never a different shape.
The pairs differ only in scale: `TRIMMED`/`REINFORCED` hold a 1px waist with no full-width rows,
while `FACETED`/`BASTION` inset the same profile one pixel deeper (a 2px waist) and frame it with
full-width `16` cap rows — the chamfered octagonal rings — which just shift that `+2/+1` delta one
row inward.

`STANDARD` and `STURDY` are the hard-edged column (uniform, no taper); `STURDY` is `STANDARD`'s
2px sibling, carrying the same full-width `16` cap rings as `FACETED`/`BASTION`.

Three behaviors are baked into the profile rather than into each generator:

1. **Run merging.** `runs()` collapses consecutive equal-width rows into one box each and converts
   image row `r` to Minecraft Y-band `[15-r, 16-r]`. A 14-row profile with four distinct widths
   produces four boxes per corner, not fourteen.
2. **Open-cap fallback.** A taper is anchored to a physical cap. When that cap is open there's
   nothing to taper into, so `effectiveRowWidths` replaces the leading/trailing differing run with
   `baseWidth()` (the middle row's value) — the post just runs straight at base width to the
   boundary. This assumes a monotonic taper converging to one steady middle value.
3. **Seam extension.** The run bordering an open cap is extended to the block boundary (y=16 / y=0)
   instead of stopping at y=15 / y=1, so stacked tanks meet flush. Without this each tank falls 1px
   short and a real connection shows a 2px gap.

### The generators

| Generator | Used by | What it does |
|---|---|---|
| `TaperedFrameGeometryGenerator` | STANDARD | Fixed 1px caps + one solid `w×w` box per run at each of the 4 corners. |
| `ShellFrameGeometryGenerator` | TRIMMED, REINFORCED, FACETED, BASTION | **1px-thick flat plates** instead of solid posts, plus chamfered octagonal rings for `16`-width rows and floor chamfers bridging glass to sand. |
| `OrnateFrameGeometryGenerator` | ORNATE | Standard 1px frame + hardcoded decorative bracket spans per Y band. |
| `ShaggyFrameGeometryGenerator` | SHAGGY | Same as the ornate frame, reading its spans from the shared `ShaggyTankSpans` table. |
| `TaperedGlassGeometryGenerator` | all but ORNATE/SHAGGY | Pane split into one stacked segment per run; only the along-wall trim varies. |
| `OrnateGlassGeometryGenerator` | ORNATE | Pane split into the *complement* of the bracket spans, so glass doesn't z-fight the inlays. |
| `ShaggyGlassGeometryGenerator` | SHAGGY | Same complement approach, sharing `ShaggyTankSpans` with the frame so holes and inlays can't drift. |
| `SandGeometryGenerator` | STANDARD, ORNATE, SHAGGY | Square sand inset by the profile's floor-adjacent row width, plus edge/corner fills. |
| `SteppedSandGeometryGenerator` | TRIMMED, REINFORCED, FACETED, BASTION | Applies the profile *horizontally* along Z, so the footprint is a stepped octagon. |

`TankShapeGeometryStrategies` (`tools/tank-shape-gen`, keyed by serialized name) is the single
mapping from shape → these three generator calls. Fabric datagen's `FishTankShapeGeometryStrategies`
is a thin adapter over it (keyed by the `FishTankShape` enum instead), and it's the only file the
three model providers consult — so adding a shape still only touches one switch rather than three
loops. Keeping the mapping in `tools/tank-shape-gen` rather than Fabric-only means the geometry
safety test (`TankShapeConnectivitySafetyTest`, see §4) sweeps every shape automatically too, with no
separate list to keep in sync.

### Invariants worth not rediscovering

**Flush-anchored stacking needs no bridging.** A corner post that changes size per row but stays
anchored at the same true corner (footprint always `[0,w]` or `[16-w,16]`) has zero gaps between
bands — the wider band's own face *is* the shelf the narrower one sits on. This only holds because
the post's position never moves. A design that *insets* the post before widening it back out needs
an explicit bridging element; that's a materially harder problem and none of the shipped shapes do
it.

**Glass segments must never define `up`/`down` faces.** Segments stack along Y, so `up`/`down` is
exactly the plane where two touch. Defining it on both sides renders two coincident translucent
quads there, and alpha compounds into a visibly darker seam line. Only ever define the two faces
perpendicular to the pane's thickness axis. This is safe on the *opaque* frame — redundant
coincident faces there are invisible — which is why the frame helpers correctly render all six.

**Tapered corner posts on stepped shapes must be thin plates, not solid blocks.** A solid `w×w`
post at `w=6` occludes the stepped-octagon sand and overlaps the 1px glass, which reads in the
previewer as z-fighting and in-game as "the frame is enormous." `ShellFrameGeometryGenerator`
decomposes each face into two 1px plates reaching `w` inward, with the north/south plates owning the
corner cells and the west/east plates starting 1px in so they never overlap.

**UVs follow `FaceBakery.defaultFaceUV`.** Each sub-box samples the texture region a full block of
that material would show at those world coordinates — that's what makes the frame read as a placed
block with the glass punched out, rather than every face sampling the texture's top-left corner.

## 4. Regenerating models

```bash
./gradlew :fabric:runDatagen        # writes fabric/src/main/generated/, then auto-copies to common/
```

`runDatagen` is `finalizedBy copyGeneratedAssetsToCommon`, which runs
`scripts/copy_assets_to_common.py` to merge output into `common/src/main/resources`. That script
**merges rather than wipes**, because `common/` interleaves generated subtrees with hand-authored
files, and it maintains an `EXCLUDE` set for paths where datagen output is known to be worse than
what's committed (currently `assets/fishtastic/items/fish_tank.json`).

The copy step needs `python3` on PATH. If it isn't available, the datagen output still lands in
`fabric/src/main/generated/` and can be copied by hand — but check for stale files, since a hand copy
won't delete models a shape no longer emits.

**Regression gate:** `STANDARD` must stay byte-identical to the checked-in `fishtankbase` models.
Diff the regenerated output against them after any change to the shared geometry library:

```bash
diff -rq fabric/src/main/generated/assets/fishtastic/models/block/fishtankbase \
         common/src/main/resources/assets/fishtastic/models/block/fishtankbase
```

Zero differences means the change was additive. This has caught real regressions — including a
purely cosmetic element-`name` change that would otherwise have silently rewritten 16 of the 64 sand
models.

**Automated connectivity safety sweep:**

```bash
./gradlew :tools:tank-shape-gen:test
```

`TankShapeConnectivitySafetyTest` sweeps every shape in `TankShapeGeometryStrategies.ALL` across all
64 connection permutations and asserts zero frame/glass/sand volume overlap plus no bare gaps in the
floor or outer wall skin wherever the owning face is closed — the exact defect class the
2026-08-13/14 FACETED/BASTION connection bugs were (a piece that correctly vanished on an open face
without another piece extending to cover its territory). Runs on every commit via
`scripts/git-hooks/pre-commit`. A new shape gets this coverage automatically the moment it's added to
`TankShapeGeometryStrategies.ALL` — no separate wiring.

### The previewer

```bash
./gradlew :tools:tank-shape-previewer:run
```

Orbit camera (drag to rotate, scroll to zoom), six open-face checkboxes, and a shape selector.
Pass a raw `shape:<name>` argument (e.g. `shape:faceted`) to open directly on one shape without UI
interaction — used by the screenshot workflow.

Known simplification: textures aren't wired up. The default frame/glass/sand textures are vanilla
assets not present in this repo, so parts render in flat representative colors (brown / translucent
blue / tan). It verifies **geometry**, not texturing. Lighting is a flat `AmbientLight`, because the
hand-built meshes carry no vertex normals and a directional light renders them black.

## 5. The material axis

`FishTankMaterials` stores three `Block`s (frame, sand, glass) as a data component, carried on both
the item stack (so distinct combos are distinct, non-stacking items) and the placed block entity,
which is seeded from the stack on placement. The composite baked model retextures the shape's
geometry models with those blocks' sprites at bake time, keyed by the same cache key as the shape.
Materials require no new models — that's the point of the split.

`FishTankFrameType` is a separate dynamic registry; each entry is just a `TagKey<Block>` naming the
family of blocks that count as one frame material choice. Shop entries hand out preconfigured tanks
by setting the components directly on the reward stack:

```json
{ "id": "fishtastic:fish_tank", "count": 8,
  "components": { "fishtastic:fish_tank_shape": "bastion" } }
```

Material tanks are typically quest-gated (`unlock_quests` + `daily_max_purchases`), while the nine
non-standard shape tanks are gated by `unlock_quests` alone (no purchase cap) — each is granted once
by its first-claimed unlock quest and then stays purchasable, so the shape reads as earned rather
than bought. See the table in §2 for the full any-one-of-these quest lists.

Each shape's `ShopEntry` also sets `"is_tank_shape": true`, pulling it out of the shop's main
weighted draw entirely (the same isolation `"is_charm": true` gets — see `ShopEntry.CODEC`). Shapes
only surface via a separate `ANY_TANK_REPLACE_CHANCE` roll (`ShopEntry.getActiveDailyShop`) that
swaps one already-drawn slot for a weighted pick among *unlocked* shapes. That keeps a shape tank's
odds of showing up in a given day's shop constant regardless of how many shapes a player has
unlocked so far — without it, each newly-unlocked shape would join the main pool and crowd out a
slightly larger share of every other item's chances as the catalog grows.

## 6. Cosmetics

Distinct from both axes: cosmetics are decorations *placed inside* the tank, not part of its body.

- `CosmeticGridCell` — the 3×3 placement grid on the tank floor.
- `PlacedCosmetic` — an occupied cell with its item and rotation.
- `CosmeticStructure` / `CosmeticStructures` — multi-block cosmetics spanning several grid cells,
  defined as datapack JSON under `data/fishtastic/fishtastic/cosmetic_structure/`.
- `CosmeticTransforms` — client-side per-item render transform overrides.

Two skills cover authoring these: `fishtastic-mcp-builder` (build one live in-world via the MCP
bridge and capture it) and `cosmetic-structure-item` (wire an existing structure JSON up as a
purchasable item).

## 7. Rendering

Geometry generation ends where model *baking* begins. For how the composite model is assembled,
cached, and drawn — and how NeoForge and Fabric diverge on it — see
[fish-tank-rendering.md](fish-tank-rendering.md) and the accompanying
[architecture diagram](fish-tank-rendering-architecture.html).

## 8. Known limitations

**`CosmeticGridCell` hardcodes STANDARD's dimensions.** It assumes `WALL_PIXELS = 1`,
`FRAME_FLOOR_PIXELS = 1`, `SAND_LAYER_PIXELS = 1`, and derives cell width and floor Y from them.
Tapered shapes have a wider floor-adjacent corner (t = 3, 5, 6), so on those shapes the outer grid
cells sit partly inside the corner posts. Sand height happens to be safe — every shape keeps the
1px floor slab and 1px sand layer — but interior *width* is not. Making cosmetics correct on tapered
shapes means turning these constants into a per-shape geometry profile, which
`FishTankBlockEntityRenderer`'s floor-Y math would need to consult as well.

**No per-shape `VoxelShape`.** All shapes use the full-cube collision fallback. Fine while every
shape stays within the block volume.

**In-game visual verification is partial.** All seven shapes are datagen-verified and previewer-
verified, and the gametest suite proves the connection logic. Shapes have been confirmed rendering
in-game; the tapered/stepped shapes have had less scrutiny under mixed open-face permutations and
unusual material combinations than `STANDARD` has.

**Shapes are code-defined, not datapack-defined.** Deliberate: the enum is the pragmatic default
until third-party shapes become a real goal. Going data-driven would mean a dynamic registry plus
runtime geometry generation or shipped per-datapack models.

## 9. Adding a new shape

1. **Author the geometry.** If you have a pixel-mapped reference image, use the
   `tank-shape-image-to-datagen` skill — it covers reading the image exactly, extracting the
   profile, choosing generators, and the verification gates. Add the profile to
   `CornerTaperProfile` (or write a custom generator for geometry the profile can't express).
2. **Verify in the previewer** — closed on all sides against the reference, plus at least one
   cardinal face open and one cap open to check both seam directions.
3. **Add the enum entry** in `FishTankShape` with an id, a `modelPathPrefix`, and a deliberate
   `connectionCollection` (its own id to isolate it, `standard` to join the shipped family).
4. **Map it** in `TankShapeGeometryStrategies.ALL` (`tools/tank-shape-gen`) — Fabric's
   `FishTankShapeGeometryStrategies` picks it up automatically by serialized name, no separate edit
   needed there.
5. **Run the automated safety sweep** (`./gradlew :tools:tank-shape-gen:test`) — this is also what
   the previous step enables. Sweeps all 64 connection permutations for frame/glass/sand volume
   overlap and floor/wall-skin gaps, broader and cheaper than the previewer's three hand-picked
   states, so it's worth running before spending more time there.
6. **Run datagen**, confirm `STANDARD` still diffs clean, and confirm the new prefix directory got
   all 192 files.
7. **Add lang** (`shape.fishtastic.<id>`) and a shop entry if it should be purchasable.
8. **Add a gametest** to `FishTankGameTests` covering its connection behavior against the shapes it
   is and isn't supposed to connect to.
9. **Look at it in-game.** Datagen and the previewer verify geometry; neither verifies texturing,
   lighting, or how it reads next to a neighbor.
