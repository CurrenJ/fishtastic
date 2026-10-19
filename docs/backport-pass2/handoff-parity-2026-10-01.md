# Handoff: 26.1.2 → port/1.20.1 parity + drift passes (2026-10-01/02) — COMPLETE

Branch `port/1.20.1`, worktree `D:\GitHub\fishtastic-worktrees\mc-1.20.1`, HEAD `ff451aba` (the 2026-10-02
GUI blend fix, on top of the drift-cleanup pass `f23c2f1a`/`e4526ac9`; the forward-port pass' content tip
was `23b8016d`).
Nothing pushed, nothing published. `26.1.2` and the sibling `mc-1.21.1` worktree were read-only sources
(the sibling ported the same 26.1.2 commits, which gave every commit here a template to mirror).
Working tree clean apart from this untracked doc and untracked `.scratch/` (build logs and helper scripts;
safe to delete).

## Commits

| # | 26.1.2 | Subject | port/1.20.1 |
|---|---|---|---|
| 1 | `97a26b53` | Fish Tank Assembly refit | **ported `6e3f0443`** |
| 2 | `75d961b3` | Inventory highlight + bait tutorial | **ported `a81487a3`** (+ reconciled in `6c9d5118`) |
| 3 | `17dde5de` | Legendary catch celebration | **ported `d4672c87`** (+ depth lift in `6c9d5118`) |
| 4 | `ef118b31` | Discovery polish, reduced effects, skippable hold | **ported `5c2a74a9`** |
| 5 | `01da18c1` | Fishing bar / bobber styles | **ported `43eb1367`** |
| 6 | `a86b349b` | Column/hanging/spanning cosmetics, chunk-mesh baking | **ported `b91c79df`** |
| 7 | `0b46b99e` | Tank interior lighting | **ported `6c3f2934`** (block-light-only floor on both loaders; 26.1.2's NeoForge path raised sky light too — owner confirmation pending) |
| 8 | `59df0b3d` | Tall bar fish-target art | **ported `e69631e5`** (GUI sprite + port-only `IGuiGraphicsExtension#fishtastic$renderSprite`; no `DataComponents.ITEM_MODEL` on 1.20.1) |
| 9 | `e0bb057e` | Freeze tank flocks while frozen | **skipped (documented)** — 1.20.1 has no `TickRateManager` / `/tick freeze` (0 occurrences in the mappings) |
| 10 | `55ae36cf` | mcp-bridge .env re-read | **ported `4f5d8db9`** (repo tooling; not packaged in the mod) |
| 11 | `76986ade` | Span structure preview PNGs | **ported `23b8016d`** (both PNGs bit-identical to 26.1.2's blobs) |

`05565f7b` (the merge that follows `76986ade` on 26.1.2) is content-identical to `76986ade`
(`git diff 76986ade 05565f7b` is empty) — nothing to port.

## Drift-cleanup pass (2026-10-02)

Three commits on top of `23b8016d`, closing the three-way drift review's fix list:

| # | Commit | What |
|---|---|---|
| 1 | `ead33322` | `drowned_pagoda.json`: last part `minecraft:oxidized_lightning_rod` → `minecraft:lightning_rod` (that id does not exist on 1.20.1 — the decode dropped the part silently, not a load failure). The file is now byte-identical to `port/1.21.1`'s copy. |
| 2 | `2647f61e` | Parity cleanups: `withBakedColor`, `TINTED_COSMETICS_PROPERTY`, `BlockEntityNbt.child`/`readChild` + the nine `[FishTankBE.loadAdditional]` log tags, `pose` param + import order, the renderer's snapshot/bench/span-share members made static, and the cosmetic mesh's packed normal zeroed. |
| 3 | `f23c2f1a` | Checklist merge (docs only). |

**Verdicts on the review's two open questions.** (e) `FishTankBlockEntityRenderer`'s `snapshot` /
`benchPieceCache` / `benchPiecesFor` / `spanShares` / `collectSpanParts` are now static: nothing on 1.20.1
needs instance state (one renderer per client, caches keyed by position alone, no instance-field reads), so
static is safe and shipped. (f) The cosmetic mesh's packed normal is now zeroed, as 1.21.1 does: 26.1.2's
`BakedQuad` carries no normal field at all; Fabric/Indigo overwrites the copied payload normal with the
face normal anyway (`populateMissingNormals`), and Forge only honours a non-zero packed normal
(`applyBakedNormals`), so the old normal-pose rotation bought nothing on either loader.

**Verification.** The `spanpreview` self-test scene re-ran clean after the changes (all 5 checks PASS,
including `spanpreview.pagoda.placed: PASS 12 of 12 tanks resolve to drowned_pagoda`); the pagoda's copper
lightning rod (offsetY 20, under the end rod) renders in the shots and no new log noise appeared. The green
bar re-ran on the result: `:common:cleanTest :common:test` 95/95; `:forge:runGametest` 282/282;
`:fabric:runGametest` 282/282 (its tank save/load round-trip covers the NBT refactor); `:fabric:runDatagen`
left `git status --porcelain` with only the uncommitted sources; `:fabric:runServer` `Done (7.940s)` and
`:forge:runServer` `Done (7.046s)`, zero ERROR/exception lines on either. Caveat, recorded rather than
glossed: no gate exercises the SpanLink *load-with-existing-data* path directly (the self-test world is
recreated fresh each run), so that path's evidence is the line-identical helpers plus the overall bar.

## GUI blend fix (2026-10-02) — `ff451aba`

Owner report: the inventory highlight "renders with no falloff, fully opaque". Cause, bytecode-confirmed:
1.20.1's `ShaderInstance` parses the json `blend` field into a `BlendMode` and always applies it in
`apply()`, which runs inside `BufferUploader.drawWithShader`; a json with **no** `blend` block yields the
disabled mode (`disableBlend()`), so it overrode every effect's own `enableBlend()`/`defaultBlendFunc()`
immediately before the draw and each fragment landed at full alpha. Fixed by declaring vanilla's
translucent blend (`func add / srcrgb srcalpha / dstrgb 1-srcalpha`, the block `rendertype_text.json`
uses — taken from the local loom jar) in the three programs whose 26.1.2 pipelines are
`ColorTargetState(BlendFunction.TRANSLUCENT)`:

| Json | fsh ramp | 26.1.2 pipeline | Verdict |
|---|---|---|---|
| `gui_item_highlight` | `Opacity * (1 - smoothstep(Width, reach, dist))` | `TRANSLUCENT` | block added |
| `gui_item_silhouette` | `Opacity * dissolvedAlpha` (dissolve + pulse) | `TRANSLUCENT` | block added |
| `gui_texture_outline` | `OutlineOpacity * (1 - falloffStrength * t)` | `TRANSLUCENT` | block added |
| `outline_bake` / `outline_bake_legendary` | alpha ramp, but never relied on blending | `ColorTargetState.DEFAULT` ("blending is off: the ring's alpha must land in the atlas verbatim") | deliberately left alone |

The callers' `enableBlend()`/`defaultBlendFunc()` calls were kept (minimal change; harmless once the json
applies the same mode). Evidence, `highlight` scene on both loaders: before, the gold blob's edge is a
hard cut — `(255,221,68,255)` meets the slot background `(139,139,139,255)` with no intermediate pixel;
after, the ring keeps a solid core and fades through ~900 blended pixels in the same small crop, the old
cut coordinates now reading `(191,176,107)`/`(225,200,86)` (Fabric) and `(162,155,125)`/`(206,187,98)`
(Forge). Crops: `.scratch/p2-hl-before-crop.png` vs `.scratch/p2-hl-after-{fabric,forge}-crop.png` (the
before/after full shots share filenames, so only the crops preserve the before). `shaders.loaded` and all
four `highlight.*` checks PASS on both loaders; the `outline` scene also re-passed on Fabric (covers the
bake passes as a no-regression control and the silhouette program, whose encyclopedia shot matches the
sibling's archived `docs/port-evidence/1.21.1/selftest-fabric-outline-encyclopedia.png` look).
`:common:cleanTest :common:test` 95/95. Gametests were not run: the change is resource-only (three json
files, 15 lines) and no Java changed — the hook's `compile` stage covered the build.

**Sibling note (do not mirror):** `port/1.21.1` must NOT get this block — its `ShaderInstance` has no
blend handling at all, so its caller-side `enableBlend()` works and a declared blend would be ignored
anyway. This is an API-forced divergence, recorded in the commit's `Port notes:`.

## Green bar (tip `23b8016d`, 2026-10-01)

- `:common:cleanTest :common:test` — **95 tests, 0 failures, 0 errors, 0 skipped**.
- `:forge:runGametest` — **282/282 required tests passed**, 0 ERROR lines.
- `:fabric:runGametest` — **282/282 required tests passed**, 0 ERROR lines.
- `:fabric:runDatagen` — BUILD SUCCESSFUL; "All providers took: 1309 ms"; HashCache `written: 0`;
  `copyGeneratedAssetsToCommon` ran; `git status --porcelain` empty afterwards (ignoring `.scratch/` and
  this doc).
- `:forge:runData` — providers ran to completion ("All providers took: 0 ms", `written: 0`; datagen on
  this branch is Fabric-owned, the Forge run has no providers) then the dev JVM hung at shutdown with no
  `main` thread — the documented environment flake. Killed; tree still clean.
- Server smoke, both loaders: `:fabric:runServer` reached `Done (6.643s)`, `:forge:runServer` reached
  `Done (6.293s)` — both killed after `Done`; no mixin or remapping errors.

## Port-only names introduced (all listed under "Port notes:" in the commit messages)

- #1–#5: see `6c9d5118` and the individual commit messages (highlight shader/renderer, celebration lift,
  `FishingMinigameAnimation.CELEBRATION_Z`, styles).
- #6 (`b91c79df`): `TankCosmeticMesh.Quad.layer`, `Baked(untinted, tinted, layers)`,
  `TankCosmeticMesh.withBakedColor` (named `withColor` until `2647f61e`), Forge `FishTankModelData.TINTED_COSMETICS_PROPERTY` (renamed likewise) +
  `FishTankBakedModel.getModelData(level,pos,state,data)`, `CosmeticPlacementPreview.render(pose, buffers,
  camera)`, private `interactionRange(Player)` in `CosmeticPlacement`.
- #7 (`6c3f2934`): `TankInteriorLight` (`level`/`apply`/`minimumLightmap`/`max`/`block`/`sky`), Forge
  `FishTankBakedModel.withLightEmission(BakedQuad)` + `litQuads`/`litLevel` cache, Fabric
  `FishTankBakedModelFabric.emitLayers` + `INTERIOR_LIGHT` quad transform, `IrisCompat.isShaderPackInUse()`,
  the `lighting` self-test scene.
- #8 (`e69631e5`): `FishingBarLayout#fishTargetSprite`/`targetScale`, `FishingMinigameAnimation.renderSprite`
  (public), `IGuiGraphicsExtension#fishtastic$renderSprite` (+ `GuiGraphicsMixin` impl),
  `FishingBarStyles.TALL_FISH_TARGET`/`TALL_TARGET_SCALE`, `FishingBarLayoutTest`.
- #10/#11: none.

## 1.20.1-specific resource ids

- `fish_target_2.png` lives at `assets/fishtastic/textures/gui/fish_target_2.png` (26.1.2 keeps it under
  `textures/item/` with a generated item model) — the blob is bit-identical, only the path differs.
- `drowned_pagoda.json` was the last lossy id in the tree: its final part named `oxidized_lightning_rod`,
  a 26.1-only block, so on both port branches the part silently never became a block. Fixed by hand in
  `ead33322` to `minecraft:lightning_rod`, byte-identical to `port/1.21.1`'s copy. `tools/span-structure-gen/gen.py`
  line 362 still emits the 26.1-only id, so regenerating the JSON would reintroduce it — the span preview
  renders stay ported verbatim instead of regenerated. `iron_chain` → `chain` in the hanging cosmetic path
  (from #6).

## Owed to the owner (in-game look)

- The tall/lava fishing bar styles' target art (no headless scene covers the minigame bar).
- The tank interior-light floor, including the block-light-only deviation vs 26.1.2 (and Sodium/Iris on
  Forge, untested).
- The spanning/column/hanging cosmetics in a real world (tinted kelp/vines, chunk layers, neighbour re-mesh).
- The GUI highlight falloff now that blending is on (`ff451aba`) — confirm the ramp reads right in game; also
  the encyclopedia silhouettes and the minigame's texture-outlined zone icons, whose programs the same fix
  touched but no headless scene renders in full (the `outline` encyclopedia shot only shows the silhouette).

## Self-test hygiene

Markers deleted after every headless run this pass; `fishtastic_render_selftest` is absent in both `run`
dirs. Nothing was left armed.
