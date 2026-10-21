# Handoff: port/1.21.1 forward-port of 26.1.2 31adba43..76986ade (2026-10-01)

Autonomous forward-port pass. Uncommitted file; nothing pushed. Source of truth for the in-flight
state of this pass — keep it current.

## Status per 26.1.2 commit

| 26.1.2 | Subject | port/1.21.1 |
|---|---|---|
| faf47828 | Move deep_sea_bait recipe into the datagen recipe provider | ported `609690ec` (row missing from checklist) |
| cfddd15d | Pin JSON to LF via .gitattributes | ported `ac94d60f` (row missing) |
| 5e26c375 | Remove the GitHub workflows; CurseForge tags + -Pbeta | ported `b8d5b834` (row missing) |
| b345b33e | Bump gelatinui_version to 1.0.37 | ported `c7b37f7a` (row missing) |
| 7576a7ed | Render each group's fish from the tank they are in | ported `c74092e4` (row missing) |
| 31adba43 | Render self-test harness + per-tank group scene | ported `b8acb288` (row missing) |
| 97a26b53 | Fish Tank Assembly refit | ported `74a09123` |
| 75d961b3 | Inventory highlight + bait tutorial | ported `9178af23` |
| 17dde5de | Legendary catch reveal celebration | ported `fcb09410` |
| ef118b31 | Discovery polish, reduced effects, longer hold | ported `e954315b` |
| 01da18c1 | Fishing bar and bobber styles | ported `569e3525` |
| a86b349b | Column/hanging/spanning cosmetics in the chunk mesh | ported `125bc550` |
| 0b46b99e | Tank interior light | ported `9bcaee30`; `lighting` scene run on BOTH loaders: 26 checks, 0 FAIL, 34 shots each. Deliberate deviation: NeoForge bakes a block-light-only floor (26.1.2's NeoForge raises sky light too) - owner confirmation pending. Sodium/Iris on NeoForge untested. |
| 59df0b3d | Tall bar styles' fish target art | ported `db4fc653` (item-model swap -> GUI sprite, see Port notes); `:common:test` + hook green; on-screen look needs an owner pass (no headless scene for the minigame bar) |
| e0bb057e | Freeze tank flocks while the world is frozen | ported `1c7aeccb`; `lighting` re-run on both loaders (26 checks, 0 FAIL, 34 shots each), and the shoal holds station on both (NCC patch matching between two shots 25s apart, all at offset (0,0): fabric 0.90/0.92/0.72, neoforge 0.90/0.92/0.77; the pre-change run had the big fish 69px out at 0.55) |
| 55ae36cf | mcp-bridge: re-read .env on every bridge call | ported `eeac4ef8` (files match 26.1.2's postimage byte-for-byte, modulo CRLF). The handoff's "skip per D4" was wrong - tools/mcp-bridge exists here |
| 76986ade | Add span structure preview renders | ported `f8632c72` (PNGs hash-identical to 26.1.2's blobs: db1b17b0, c2252f8e). Deliberately not regenerated - this branch's gen.py still emits the 26.1-only oxidized_lightning_rod |

## Gates
- Pre-commit hook (stage `full`: compile + `:common:test` + `:tools:tank-shape-gen:test` +
  `:fabric:runGametest`) green per commit: `125bc550` (282/282 at that point) -> `9bcaee30` ->
  `db4fc653` -> `1c7aeccb` -> `eeac4ef8` -> `f8632c72`.
- Self-test screenshots inspected this pass: lighting on both loaders (`9bcaee30`), lighting again on
  both loaders (`e0bb057e`).
- End green bar on the tip `f8632c72` (JDK 21, `--console=plain`): `:common:cleanTest :common:test`
  = 87 tests, 0 failures (BUILD SUCCESSFUL, 14s); `:fabric:runGametest` = "All 282 required tests
  passed :)" (1m6s); `:neoforge:runGametest` = "All 282 required tests passed :)" (1m1s);
  `:fabric:runDatagen` = BUILD SUCCESSFUL (37s) and `git status --porcelain` empty afterwards
  (checked again after the next stage too); `:fabric:runServer` / `:neoforge:runServer` both printed
  `Done (...) ! For help, type "help"`.
- `:neoforge:runData` FAILS at configuration time ("Could not find Forge run template with name
  'dataServer'"; "Cannot query the value of property 'mainClass'"), identically on a rerun. It is a
  pre-existing branch condition, not this pass's: the `data` run block was last touched 2026-09-25
  (`fbda3b04`) and uses `serverData()`, a workaround written for the 26.1.2 line's Loom 1.14.476,
  while this branch is on Loom 1.17 (`29f4083a`); nothing in the pass touches run configuration.
  Datagen on this branch is Fabric-owned (`:fabric:runDatagen` -> `copyGeneratedAssetsToCommon`);
  `neoforge/src/main/generated` does not exist. Reported to the owner rather than fixed here.
- Checklist rows + marker move are in the final docs commit (marker now `76986ade`, 2026-10-01).

## Port-only names introduced (details in each commit's "Port notes")
- FishtasticHighlightEffect.render(GuiGraphics, ItemStack, cx, cy, size, z) (ShaderInstance over the outline-atlas mask); gui_item_highlight.json + GLSL150 .fsh (uniforms TexelsPerItemPx, SlotBounds); FishtasticShaders.GUI_ITEM_HIGHLIGHT_ID/guiItemHighlight; FishtasticGuiOutlineRenderer.renderOutline (private).
- FishingMinigameAnimation.CELEBRATION_Z (GUI depth lift).
- TankCosmeticMesh: Baked(List<Quad> untinted, List<Quad> tinted), Quad.tintState always set, withBakedColor(quad, argb).
- FishTankModelData.TINTED_COSMETICS_PROPERTY (NeoForge; tints coloured in getModelData(level,pos,state,data)).
- CosmeticPlacementPreview.render(PoseStack, MultiBufferSource, Vec3) (replaces tickGizmos).
- BlockEntityNbt.child / readChild.
- TankInteriorLight.level()/apply()/max(a,b)/minimumLightmap()/set() (max replaces 26.1's LightCoordsUtil.max); FishTankBakedModelFabric.emitLayers; NeoForge FishTankBakedModel.withLightEmission (baked UV2 block-light floor); IrisCompat.isShaderPackInUse (real reflection).
- FishingBarLayout#fishTargetSprite + #targetScale (replace 26.1.2's fishTargetModel item-model swap);
  IGuiGraphicsExtension#fishtastic$renderSprite; FishingMinigameAnimation.renderSprite (public, shared
  with the bar/bobber helper); FishingBarStyles.TALL_FISH_TARGET (+ textures/gui/fish_target_2.png,
  moved from 26.1.2's textures/item) and TALL_TARGET_SCALE; FishingBarLayoutTest (common/src/test).

## Open problems / owner decisions
- 0b46b99e NeoForge: implemented as a baked block-light-only floor (NeoForge 1.21.1 applyBakedLighting),
  so it matches Fabric; 26.1.2's NeoForge raises sky light too. Deliberate deviation - owner to confirm.
  Sodium/Iris on NeoForge untested.
- 59df0b3d: the rarity ring/silhouette mask is baked from the stack's real item model, so under a tall
  style the ring keeps the classic fish silhouette around the new art (26.1.2 moves the ring to the
  swapped model). Needs an owner look in-game.
- RenderSelfTest: cool-cam/mcp scenes (matte, matteclip, fourwaters, cathedral, mcpsession) not ported
  (D4); cosmeticpreview keeps checks, drops cool-cam orbits.
- Lossy ids on 1.21.1: minecraft:iron_chain -> minecraft:chain (HangingCosmetics, tank_hanging_cosmetics
  tag, self-test, gametests); drowned_pagoda's oxidized_lightning_rod -> lightning_rod (gen.py left
  unchanged, so regenerating on this branch reintroduces it). DONE this pass: both added to the
  necessary-but-lossy table in modding-guide 20 - but D:\GitHub\modding-guide is NOT a git repo
  (no .git anywhere up the tree), so nothing was committed and nothing was pushed.
- Owner in-game checks still needed: span cosmetics + tints + placement preview in a real world, chunk
  re-mesh when cosmetics change in stacked tanks, perfbench not run, tall/lava bar target art.

## Follow-up (2026-10-02): checklist resync
The coordinator's three-way drift review found no code drift against this branch (all seven avoidable
items were on `port/1.20.1`) but this branch's `docs/backport-pass2/checklist.md` predated the sibling's
finish. That file was replaced with the sibling's converged copy verbatim (`port/1.20.1` `f23c2f1a`,
blob `02dbbfd3`) and committed here - see the sync commit. Note: `docs/backport-pass2/drift-report-2026-10-01.md`
appeared untracked in this worktree during the review (not written by this pass); left untracked.

## Exact next step
None - the pass is complete. All five commits are on `port/1.21.1`
(`9bcaee30` 0b46b99e, `db4fc653` 59df0b3d, `1c7aeccb` e0bb057e, `eeac4ef8` 55ae36cf, `f8632c72`
76986ade) plus the docs commit (checklist rows + marker to `76986ade`). Nothing pushed, nothing
published. Outstanding for the owner: the in-game looks listed above, a decision on the NeoForge
interior-light floor, and (separately) the broken `:neoforge:runData` run config on this branch.

## Follow-up (2026-10-02): "highlight outline is fully opaque" report - cleared on 1.21.1
Owner first reported the inventory gold highlight rendering with no falloff; the coordinator later
recalibrated to "may actually be 1.20.1". Investigated on this branch per decision rule: probe the GL
state, measure the rendered edge; change code only if the render is genuinely opaque.

Probe (temporary `[blendprobe]` in FishtasticHighlightEffect.render, Fabric runClient, `highlight`
selftest scene, 2026-10-02 01:09, window 1600x900 gui scale 3):
- post-flush  blend=false srcRGB=302 dstRGB=303 srcA=1 dstA=0
- post-enable blend=true  srcRGB=302 dstRGB=303 srcA=1 dstA=0   (enableBlend + defaultBlendFunc)
- pre-draw    blend=true  (same factors)
- post-draw   blend=true  (same factors)
- params: texelsPerItemPx=4, slot bounds normalised to u[0,0.09375] v[0.90625,1.0] (= 96/1024 texels),
  item cell 0.0625 wide (= 64 texels = 4 texels/item px), mask texture id 54, shader
  `fishtastic:gui_item_highlight`.
Rendered edge (RGB, not PNG alpha) measured against the known slot background (139,139,139) on the
fresh `selftest-fabric-highlight-{bait_on_cursor,rod_on_cursor}.png`: 1012/784 px at alpha 1.0 plus
760/700 px of partial coverage across 11/13 distinct alpha levels (0.05..0.95). That is a real
multi-step falloff (it breathes with the glow pulse), not a hard cut.

Conclusion: 1.21.1 is correct as-is - blending is on at draw time with MC's default factors and the
shader's alpha ramp reaches the framebuffer. Nothing changed on this branch (probe code reverted; the
only tree difference is this doc). Also confirmed the owner-facing 1.21.1 Fabric jar
(`F2 1.21.1 Fabric/mods/fishtastic-fabric-2.0.1+1.21.1.jar`, built 2026-10-02 00:49) packages this exact
shader, so it is not a stale-build artefact. The 1.20.1 mirror is the profile that can differ: its
ShaderInstance parses/applies the json `blend` block, which no fishtastic shader json declares - the
sibling takes that fix. The other four programs (gui_item_silhouette, gui_texture_outline, outline_bake,
outline_bake_legendary) are unaffected here: every GUI caller sets enableBlend/defaultBlendFunc itself,
and the atlas bake deliberately disables blend (raw write into the offscreen target).
Evidence: fabric/run/logs/latest.log (`grep blendprobe`), fabric/run/screenshots/selftest-fabric-highlight-*.png.
