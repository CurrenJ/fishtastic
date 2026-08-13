# Fish Tank Shape Variants — Implementation Plan

> **Status: Phases 0, 1, and 2b complete and verified — `trimmed`/`reinforced` are real, shippable
> `FishTankShape`s with datagen output, shop entries, and a passing gametest suite. Phase 2a
> (the parametric flare system) was **removed outright** on 2026-08-13: `CornerTaperProfile` +
> the image-translation skill is now the *only* shape-authoring path, and `STANDARD` itself is a
> `CornerTaperProfile` (see the "Removed outright" note in Phase 2a).** See
> **`docs/tank-shape-variants-HANDOFF.md`** for the next developer's orientation: what's done, what
> isn't, and where to start.
>
> Adds a new **shape** axis to the fish tank block — a second, orthogonal customization dimension
> alongside the existing material axis (`FishTankMaterials`: frame/sand/glass) — so future body
> geometries (flared rim, thick base, alternate sand profile, …) can be added without disturbing
> texturing or the connected-tank system. Full design writeup: see the "Tank Shape Variants" design
> doc (feasibility, architecture rationale, authoring-path comparison). This file is the
> actionable, checkable source of truth for building it.
>
> Decisions locked in over the course of this work:
> - Connection eligibility is gated by each shape's assigned **connection collection id**, not
>   shape identity directly — decoupled so multiple shapes can be curated into one connecting
>   family. The mechanism still *defaults* a new shape to its own singleton collection, but
>   **`STANDARD`/`TRIMMED`/`REINFORCED` all deliberately share one collection** (`"standard"`) per
>   an explicit later decision — all three are meant to freely interconnect with each other. Any
>   *future* shape starts isolated again unless someone deliberately opts it into that family too.
> - A **real-time parametric previewer** is required infrastructure for designing new shapes, not
>   a deferred nice-to-have — it shipped in Phase 1, before any new shape geometry was authored,
>   and later gained a second shape-design workflow (Phase 2b, pixel-image translation) alongside
>   the original parametric-slider one (Phase 2a).

Check items off as they land. Phases are ordered by dependency; later phases assume earlier ones
are done.

---

## Phase 0 — Foundation: prove the refactor is invisible

Goal: add the shape axis end to end with exactly one shape, `STANDARD`, mapped onto today's
existing geometry byte-for-byte. **No new visuals ship in this phase.**

- [x] Add `FishTankShape` (`common/src/main/java/grill24/fishtastic/fishtank/FishTankShape.java`):
  a code-defined enum (mirrors `FishTankFrameType`'s simplicity), one member `STANDARD`. Each
  entry carries: its own `id`, a `connectionCollection` id (defaults to the shape's own id), and
  a `modelPathPrefix` (the `fishtankbase`-style subfolder under `models/block/` its 64-permutation
  frame/sand/glass models live in — `STANDARD` keeps today's literal `fishtankbase` so no model
  files need to move).
- [x] Add persistent + network `Codec`/`StreamCodec` for `FishTankShape` and register a new
  `fishtastic:fish_tank_shape` data component in `FishtasticDataComponents`, mirroring
  `FISH_TANK_MATERIALS`'s registration shape exactly.
- [x] `FishTankCompositeModelData` (`common/src/main/java/grill24/fishtastic/fishtank/`): add a
  `FishTankShape shape` field to the record (alongside `frameBlock`/`sandBlock`/`glassBlock`/
  `openFaces`); update `DEFAULT` to `STANDARD`.
- [x] `FishTankBlockEntity`: add a `shape` field (default `STANDARD`), getter/setter following the
  existing `frameBlock`/`sandBlock`/`glassBlock` setter pattern (`setChanged` +
  `sendBlockUpdated` + `requestModelDataUpdate`); persist via `saveAdditional`/`loadAdditional`;
  thread through `collectImplicitComponents`/`applyImplicitComponents` alongside
  `FISH_TANK_MATERIALS`.
- [x] `FishTankBlockEntityFabric.getRenderData()` / `FishTankBlockEntityNeoForge.getModelData()`:
  pass `getShape()` into the `FishTankCompositeModelData` constructor.
- [x] **Connection gate** — `FishTankBlockEntity.updateConnections`: change the neighbor check from
  bare `instanceof FishTankBlockEntity` to also require
  `other.getShape().connectionCollection().equals(this.shape.connectionCollection())`. Symmetric,
  no propagation/ordering changes needed beyond what already exists.
- [x] **Per-shape model loading** (the mechanical-but-nontrivial part, duplicated on both
  platforms):
  - `FishTankBlockStateModelFabric` / `FishTankBlockStateModel` (NeoForge): loop over
    `FishTankShape.values()` (not just `0..63`) when declaring dependencies and when baking,
    building `Map<FishTankShape, ResolvedModel[]>` for frame/sand/glass instead of three flat
    arrays. Model path becomes `block/<shape.modelPathPrefix()>/fish_tank_<part>_<0-63>`.
  - `FishTankBakedModelFabric` / `FishTankBakedModel`: expand `CacheKey` from
    `(frame, sand, glass, permutation)` to `(shape, frame, sand, glass, permutation)`; look up the
    right per-shape model array before indexing by permutation.
- [x] `FishTankBlock.getCloneItemStack`: also copy `FISH_TANK_SHAPE` onto the cloned stack,
  mirroring the existing `FISH_TANK_MATERIALS` copy, for parity.
- [x] **Regression check**: `:common:compileJava`, `:fabric:compileJava`, and
  `:neoforge:compileJava` all build clean. `STANDARD` is the only shape and its
  `connectionCollection` equals its own id, and its `modelPathPrefix` is unchanged (`fishtankbase`,
  same 192 checked-in model files, no file moves) — so at the data level, behavior is
  indistinguishable from today's unconditional `instanceof` check. **Confirmed in-game on both
  Fabric and NeoForge clients** — rendering and connection behavior look identical to before.

## Phase 1 — Shared library + live previewer

Build the design tool *before* any new shape geometry is authored — real-time parametric preview
is required for how new shapes get designed, not an optional convenience.

- [x] Extract the reusable element-generation logic (ceiling/floor/corner-support style pieces)
  out of `FishTankFrameModelProvider` / `FishTankGlassModelProvider` / `FishTankSandModelProvider`
  into a new plain-Java module, `tools/tank-shape-gen` (zero Minecraft/Loom dependency — only
  Gson), as `FrameGeometryGenerator` / `GlassGeometryGenerator` / `SandGeometryGenerator` +
  shared `TankShapeGeometry`/`TankFace` helpers. The three Fabric datagen providers are now thin
  wrappers that just loop 0-63, call the library, and save. `settings.gradle`/root `build.gradle`
  updated so `tools:*` subprojects skip Loom/Architectury and get a plain `java-library` block
  instead.
- [x] Build a standalone previewer (`tools/tank-shape-previewer`, new Gradle module, JavaFX via
  `org.openjfx.javafxplugin`). Renders frame/sand/glass by converting each generator's element
  JSON into a hand-built `TriangleMesh` (one quad per named face, so faces the generator omits are
  genuinely absent, not just hidden) — six checkboxes toggle open faces and regenerate the
  composite mesh live. Orbit camera (drag to rotate, scroll to zoom).
  **Known simplification**: textures aren't wired up — the default frame/glass/sand textures are
  vanilla Minecraft assets that don't exist in this repo, so parts render in flat representative
  colors (brown/tan/translucent blue) for now. Real texture loading is follow-up work, not blocking.
- [x] Validation gate: ran `:fabric:runDatagen` and diffed the regenerated
  `fish_tank_frame/sand/glass_0-63.json` against the checked-in copies under
  `common/src/main/resources/assets/fishtastic/models/block/fishtankbase/` —
  **`diff -rq` reports zero differences across all 192 files.**
- [x] Launched the previewer and visually confirmed it (screenshots + a live checkbox toggle):
  initial render was solid black in unlit areas (no light source + hand-built mesh has no vertex
  normals) — fixed by adding an `AmbientLight`, which lights every face uniformly regardless of
  normals. After the fix, frame/glass/sand all render correctly; toggling "NORTH" live-updated the
  permutation (0 → 4), removed that glass wall, and revealed the sand layer — confirming the
  regenerate-on-change loop actually works end to end, not just the initial static render.

## Phase 2 — First new shape, designed live

- [x] Picked an **exterior-only** shape family: a flared rim, plus a `frameThickness` parameter
  (user-requested addition) scaling floor/ceiling/corner-support thickness — the latter turned out
  to also raise sand/floor height, which is technically "interior-altering" per the Phase 3 split,
  but is safe to explore now since the previewer has no real fish/cosmetic placement to break.
- [x] Added `TankShapeParams(frameThickness, bodyInset, rimHeight, stepCount)` to
  `tools/tank-shape-gen`, threaded through all three generators. `frameThickness` is a plain
  int scale factor; `bodyInset`/`rimHeight` together define the flare (both must be nonzero —
  moving just one is a no-op by design, there's nothing to flare from or no room to flare in);
  `stepCount` picks a single shelf (1) vs. a staircase taper (2-3), replacing the earlier
  step-vs-bevel discussion — bevel mode was dropped (Minecraft's element rotation only allows
  discrete ±22.5°/±45° angles, which would've decoupled `bodyInset` from being an independent
  slider).
- [x] Added a "smart" number serializer (`TankShapeGeometry.smartNumber`/`smartLabel`) so
  fractional (flare) and whole-number (unflared/default) geometry share one code path — whole
  values still serialize as plain integers (`1`, not `1.0`), which is what keeps
  `TankShapeParams.STANDARD` byte-identical to the checked-in models despite everything now being
  computed in doubles.
- [x] Wired `frameThickness`/`bodyInset`/`rimHeight`/`stepCount` sliders into the previewer
  alongside the open-face checkboxes.
- [x] **Bugs found via the previewer and fixed** (exactly the workflow Phase 1 was built for):
  - Corner-support posts flaring in isolation left a genuine gap — a corner post is inset in both
    X and Z at once, so stepping its footprint is a diagonal jump, not a simple outward slide; two
    boxes with different footprints stacked at the same height don't touch. Fixed by adding an
    explicit `createShelfCap` bridging element (the union of both footprints) at every step
    transition.
  - Glass wasn't moving with `bodyInset` at all — only the corner supports were wired up, so glass
    stayed flush at the block edge while the posts pulled inward around it. Fixed by shifting each
    glass pane's thickness-direction coordinate inward by `bodyInset` whenever a flare is active.
  - The `frameThickness`/flare refactor initially broke byte-identical output for 16 of the 64
    sand permutations (every one involving a NE/SW/SE corner extension) — `createCornerExtension`'s
    `name` field switched from the original literal pixel coordinates (`0`/`15`) to the new
    `cornerX`/`cornerZ` sign values (`0`/`1`), a purely cosmetic label change that still broke the
    diff. Fixed by routing the name through `smartLabel` on the actual coordinates, like the frame
    generator already did.
- [x] Re-ran the Phase 1 validation gate after all three fixes: `diff -rq` between regenerated and
  checked-in `fish_tank_frame/sand/glass_0-63.json` — zero differences, `STANDARD` still
  byte-identical.
- [ ] **Known open item**: a faint jagged/noisy silhouette right at the flare-to-ceiling
  transition in the previewer at some parameter combinations — not yet confirmed whether this is
  a real remaining geometry seam or just a shading artifact from thin overlapping shelf/support
  geometry under flat ambient-only lighting with no vertex normals. Worth a closer look before
  finalizing exact shipped parameter values.
- [x] **Removed outright (2026-08-13)**: the entire parametric system — `TankShapeParams`
  (`frameThickness`/`bodyInset`/`rimHeight`/`stepCount`), the flare logic inside
  `FrameGeometryGenerator`/`GlassGeometryGenerator`, and the four previewer sliders — was
  **deleted**, not just paused. The user's authoring direction settled on image-derived
  `CornerTaperProfile`s (the `tank-shape-image-to-datagen` skill) as the *only* path for new
  shapes, so the slider-tuning workflow was dead weight. Consequences:
  - `STANDARD` is now `CornerTaperProfile.STANDARD` (uniform width 1) routed through the same
    `Tapered*GeometryGenerator`s as every other shape; `FishTankShapeGeometryStrategies` is a
    single shape→profile→generator mapping with no non-tapered code path.
  - `SandGeometryGenerator` takes a `CornerTaperProfile` directly (inset = its floor-adjacent row
    width; the floor slab stays a fixed 1px) instead of a params record.
  - Previewer's four flare sliders are gone; the shape selector drives all three profiles.
  - **Regeneration verified**: `STANDARD` geometry is identical to the shipped models — the only
    diff is the cosmetic support element-name suffix (`support_0_0` → `support_0_0_1`, where the
    new suffix is the band's min-Y). `TRIMMED`/`REINFORCED` closed-permutation and sand output are
    byte-identical; only their open-ceiling/open-floor permutations changed, via the seam fix below.

### Phase 2b — Image-driven shapes (pixel-mapped reference, not sliders)

The user switched from tuning parametric sliders to directly authoring a pixel-mapped reference
image (`new_tank_shapes.png`, 4×16×16, tank 0 = current `STANDARD` as a known-answer check, tanks
1-3 = new designs — tank 3 was left unfinished/a duplicate and skipped) and having it translated
straight into datagen code. Process fully documented as a reusable skill:
**`.claude/skills/tank-shape-image-to-datagen/SKILL.md`**.

- [x] Read the reference image pixel-exact (PowerShell + `System.Drawing`, not by eye — the image
  is only 64×16px). Confirmed 3 solid colors (frame/glass/sand, no transparency) and validated the
  coordinate mapping against tank 0, which reproduced today's shipped shape exactly.
- [x] Discovered what the pixel data actually encodes: **the corner post's width at each height**
  (not glass position) — read as the leading/trailing run of frame-colored pixels per row. Sand's
  inset at the floor-adjacent row is just the natural continuation of the same taper, not a
  special case.
- [x] Added `CornerTaperProfile` (`tools/tank-shape-gen/.../CornerTaperProfile.java`) — a 14-entry
  per-row corner width, with `TRIMMED` (`3,2,1×10,2,3`, tank 1 — a light corner brace) and
  `REINFORCED` (`5,3,2,2,1×6,2,2,3,5`, tank 2 — chunkier corner brackets) transcribed from the image.
- [x] Key insight that made this simpler than the Phase 2a flare: because every run stays **flush
  at the same true corner** (only its reach changes, never its position), stacked bands of
  different widths touch with zero gap automatically — no shelf-cap bridging element needed here,
  unlike the flare's `bodyInset` (which moved the post's position, not just its size).
- [x] Added `TaperedFrameGeometryGenerator` and `TaperedGlassGeometryGenerator`; sand reuses the
  existing `SandGeometryGenerator`, which reads the profile's floor-adjacent row width as its inset.
- [x] Verified visually: wired a shape selector into the previewer, screenshotted both profiles
  closed (matched the reference image's stepped taper) and with one face open (confirmed corner
  posts on that side disappear and sand bridges to the neighbor correctly, same as today's
  untapered behavior).
- [x] Shape names decided: `trimmed` (light) and `reinforced` (heavy) — `CornerTaperProfile.TRIMMED`/
  `REINFORCED` renamed accordingly (were provisionally `TAPERED_LIGHT`/`TAPERED_HEAVY`).
- [x] **Translucency correctness verified**: confirmed (and documented as an explicit invariant,
  both in `TaperedGlassGeometryGenerator`'s javadoc and the skill) that stacked glass segments only
  ever define the 2 faces perpendicular to the pane's thickness axis, never `up`/`down` — so the
  Y-boundary where two segments touch never gets a coincident double-rendered translucent face.
- [x] **Mod integration complete**:
  - `FishTankShape.TRIMMED`/`REINFORCED` added, each with its own `modelPathPrefix`
    (`fishtank_trimmed`/`fishtank_reinforced`) and — per the connection-collection decision above —
    `connectionCollection` set to `STANDARD`'s (`"standard"`), not defaulting to their own id.
  - `FishTankShapeGeometryStrategies` (new, `fabric/.../datagen/`) centralizes the
    shape→generator mapping so `FishTankFrameModelProvider`/`GlassModelProvider`/`SandModelProvider`
    each just loop over `FishTankShape.values()` instead of hardcoding `STANDARD`.
  - Ran the real datagen: `STANDARD` re-verified byte-identical (`diff -rq` clean); `trimmed`/
    `reinforced` each produced their full 192-model set, copied into
    `common/src/main/resources/assets/fishtastic/models/block/` (the repo's own
    `copyGeneratedAssetsToCommon` python script isn't available in this dev environment, so this
    step was done by hand — re-run datagen and re-copy if these shapes' geometry changes again).
  - Shop entries added (`trimmed_tank.json`/`reinforced_tank.json`) — no `unlock_quest`, unlike
    every material-variant tank: every existing "collector" quest is already claimed by a material
    tank, and a shape variant doesn't have a natural survey/achievement tie-in, so these are plain
    cost-100 purchases with no daily cap. Reconsider if that undersells them relative to the
    quest-gated material tanks.
  - New gametest coverage in `FishTankGameTests`/`FishtasticFabricGameTests`:
    `sameShapeNeighborsConnect`, `crossShapeNeighborsInSameFamilyConnect`,
    `standardAndReinforcedNeighborsConnect` — all exercise the *real* world-adjacency
    `updateConnections` path (not just face-state bookkeeping), and all pass, confirming
    `STANDARD`/`TRIMMED`/`REINFORCED` connect to each other in every combination. Full suite: 211/211
    passing, no regressions.
- [x] **Seam fix (2026-08-13, landed with the parametric removal)**: `CornerTaperProfile.runs()`
  now extends the run bordering an open ceiling/floor cap to the block boundary (y=16 / y=0), so
  stacked tanks' corner posts and glass reach the shared seam instead of stopping 1px short of it.
  This matched legacy `STANDARD`'s long-standing flush-seam behavior and fixed a pre-existing
  artifact in `trimmed`/`reinforced` when stacked (their open-cap permutations previously left a
  small notch at the seam). Only open-cap permutations' frame/glass changed; everything else is
  byte-identical.
- [x] **Sand-gap fix (2026-08-13)**: the tapered shapes' sand was inset to the floor-adjacent
  corner-post width (t=3/5) to clear those posts, but the glass wall is only 1px thick, so every
  closed side left a t-1 gap between the base sand and the glass (and mixed open/closed corners
  left a corner notch). `SandGeometryGenerator` now bridges the base sand to the glass inner face
  (1/15) on each closed side, and `addCorner` fills each corner toward whichever adjacent face is
  open (block edge on an open face, glass inner face on a closed one). Both additions only fire for
  `t > 1` (zero-thickness otherwise), so `STANDARD` stays byte-identical — re-verified with
  `diff -rq` against the checked-in `fishtankbase` models. Regenerated `trimmed`/`reinforced` sand
  models copied into `common/` (128 files).
- [ ] **Not yet done** (see the handoff doc): real in-game visual verification — placing tanks via
  the actual shop UI and eyeballing the connected result — wasn't possible this session (no live
  MCP-connected game client was running, and the MCP bridge's `run_command` only permits the
  cosmetic-capture command, not a generic give/data command to conjure a specific shape for
  testing). The gametest suite proves the *logic* is correct; nobody has yet looked at it rendered.

## Phase 3 — Second shape, collection curation, interior path

- [ ] Add a second shape that deliberately alters the **interior** (e.g. thick base / raised
  floor) to exercise the harder path.
- [ ] Generalize `CosmeticGridCell` (`common/src/main/java/grill24/fishtastic/fishtank/`) from
  hardcoded static pixel constants into a per-shape geometry profile — both the sand model and
  `FishTankBlockEntityRenderer`'s floor-Y math currently assume a single global floor height.
- [ ] Exercise deliberate collection curation: assign two related shapes (e.g. two flare variants)
  to the same `connectionCollection`, verifying the seam in the previewer *before* committing, then
  confirming in-game that the cross-shape, same-collection pairing connects and seams correctly.

## Phase 4 — Polish (optional, only if the shape catalog keeps growing)

- [ ] Per-shape `VoxelShape` collision, if a shape's silhouette makes the full-cube fallback feel
  wrong in play (not needed for shapes that stay within the standard block volume).
- [ ] Data-driven (datapack) shape registry, only if third-party/config-defined shapes become an
  actual goal — the code-enum approach is intentionally the pragmatic default until then.
