# Three-way drift report — 2026-10-01 forward-port pass

Read-only drift analysis of the 2026-10-01 parity pass. Branches compared:

| Branch | Worktree | Tip |
|---|---|---|
| `26.1.2` (source of truth) | `D:\GitHub\fishtastic` | `05565f7b` (content-identical to `76986ade`) |
| `port/1.21.1` | `D:\GitHub\fishtastic-worktrees\mc-1.21.1` | `6ee52aae` |
| `port/1.20.1` | `D:\GitHub\fishtastic-worktrees\mc-1.20.1` | `bfe2fc55` |

Method: per mapped commit, `--stat` file-set comparison (normalised `neoforge/`↔`forge/`, `tags/block/`↔`tags/blocks/`), then cross-port blob-hash comparison of postimages, then port-vs-port content diffs, then port-vs-26.1.2 diffs. For the large #6 tree the decisive test was comparing the two ports' *patch content* (`git diff P^ P -- <file>`, +/- line sets) so that pre-existing branch divergence is not misattributed to this pass. For `port/1.20.1` commit #2/#3 the comparison point is the aligned state (`6c9d5118` is an explicit port-only align commit, newer than `5c2a74a9`/`43eb1367`, older than `b91c79df`). All hand-verified with `git show <sha>:<path>` / process-substitution diffs; no working trees were modified.

Classification buckets: **API-forced**, **Established shim / documented adaptation**, **Avoidable drift**.

---

## 0. Objective claims verified

| Claim | Result | Evidence |
|---|---|---|
| #10 (`55ae36cf`/`eeac4ef8`/`4f5d8db9`) JS byte-identical | **PASS** — all three identical | `httpClient.js` `5b6a6c12…`, `loadEnv.js` `2a109ed7…` in all three branches |
| #11 (`76986ade`/`f8632c72`/`23b8016d`) PNG blobs identical | **PASS** — hashes equal 3-way | `drowned_pagoda.png` `c2252f8e…`, `whale_fall.png` `db1b17b0…` |
| #8 (`59df0b3d`/`db4fc653`/`e69631e5`) diffstat identical between ports | **PASS** — both 8 files, +195/−14 | `--stat` lists identical; only comment/branch-name and `getComponents()`/`getTag()` diffs remain (latter API-forced) |
| #9 (`e0bb057e`/`1c7aeccb`) "applied verbatim" | **PASS** | 1.21.1 patch is line-for-line the 26.1.2 patch (4 added lines incl. comment); 1.20.1 skip recorded in its checklist row: "skipped — 1.20.1 has no `TickRateManager`/`/tick freeze` (0 occurrences in the mappings)" |
| #3 (`17dde5de`/`fcb09410`/`d4672c87`+align) ports match 26.1.2 | **PASS (mod. API renames)** | At `ef118b31` state, `CatchCelebration.java`, `CelebrationConfetti.java`, `CatchCelebrationGameTests.java` are byte-identical to 26.1.2 except `lookupOrThrow`→`registryOrThrow` |
| #4/#5/#7 shared command/packet files identical 3-way | **PASS** | `FishingBarCommand`, `ReducedEffectsCommand`, `TankInteriorLightCommand` hashes equal 3-way |
| #6 non-code artifacts | **PASS** | `gen.py`, `analyze.py`, `shop_entry/*.json`, `TankColumns`, `CosmeticGridCell`, `CosmeticStructure`, `TankCosmeticTooltip`, `RemoveTankEntryPacket` (1.21.1==26.1.2), `docs/cosmetic-ideas.md`, `docs/fish-tanks.md` identical 3-way |
| port-vs-port aligned files "byte-identical apart from BufferBuilder" (`6c9d5118` claim) | **PASS** | At HEAD, `FishtasticHighlightEffect`, `FishtasticGuiOutlineRenderer`, `FishtasticGlintState`, `FishtasticShaders`, `AbstractContainerScreenMixin`, `gui_item_highlight.fsh/.json`, `FishingMinigameAnimation` differ only by BufferBuilder/`1f`-vs-`1` literal patterns and branch-name comments |

---

## 1. `97a26b53` — Fish Tank Assembly refit (`74a09123` / `6e3f0443`)

File sets: identical between the two ports; 26.1.2 also carries `FishtasticFabricGameTests`+`NeoForgeGameTestRegistration` where ports have the combined `FishtasticGameTests` (documented D10 architecture, both ports identical).

Port-vs-port differences (all classified):
- **API-forced (4):** `HolderLookup.Provider saveAdditional/loadAdditional` (1.21.1) vs `load(CompoundTag)` (1.20.1); `BlockEntityNbt` registry-aware overloads; `DataComponents.CUSTOM_NAME` vs `setHoverName` in the refit gametests; NeoForge vs Forge `@GameTestHolder` imports/doc text.
- **Established shim (2):** `NetworkApiSided.sendToServer(pkt, STREAM_CODEC)` on 1.20.1 (pre-existing B3.2 shim) vs direct `ServerboundCustomPayloadPacket` on 1.21.1 (its established pattern, used consistently); port-only testmod docs.
- **Avoidable drift (0).**
- vs 26.1.2: `ContainerInput`→`ClickType` in `FishTankAssemblyMenu` (API-forced rename); otherwise semantics match.

Counts: API-forced 4+ (sampled), established 2, avoidable 0.

## 2. `75d961b3` — Inventory highlight + bait tutorial (`9178af23` / `a81487a3` + align `6c9d5118`)

Mechanism: 26.1.2 uses `RenderPipeline` + params UBO + `GuiRendererMixin`/`ItemModelResolverMixin`/`ItemStackRenderStateMixin` over the GUI item atlas. Neither API exists pre-1.21.2, so both ports implement a **port-only equivalent**: `FishtasticItemOutlineAtlas` mask + `gui_item_highlight` shader with plain uniforms, drawn via `FishtasticGuiOutlineRenderer`/`GuiGraphicsMixin` — API-forced, documented.
- Port-vs-port pre-align had drifted on quad maths, `SlotBounds` and draw order; **`6c9d5118` explicitly aligned 1.20.1 to 1.21.1**, and at HEAD the two ports are identical apart from BufferBuilder patterns and `fishtastic.mixins.json` (`JAVA_21` vs `JAVA_17` + the 1.20.1-only `FishtasticMixinExtrasPlugin` entry) — both API-forced.
- Remaining port-vs-port wording differences are comments only.
- `TutorialClientHandler` partial-tick handling differs (1.21.1 pulls `deltaTracker.getGameTimeDeltaPartialTick(false)`; 1.20.1's `Screen.render` partialTick is already the interpolation fraction) — API-forced, with explanatory comments on both sides.

Counts: API-forced 5, established 3, avoidable 0 at HEAD (2 pre-align drift items were repaired by the align commit and re-verified).

## 3. `17dde5de` — Legendary catch celebration (`fcb09410` / `d4672c87` + align `6c9d5118`)

- `CatchCelebration`, `CelebrationConfetti`, `CatchCelebrationGameTests` are byte-identical between ports and match 26.1.2 modulo `lookupOrThrow`→`registryOrThrow` (#4 state) — API-forced.
- `FishingMinigameAnimation`: 1.21.1's `fcb09410` shows a full-file 1506/1302 rewrite; **this is whitespace/indentation churn, not content** (`git diff -w` reduces it to 207/3). Same for `e954315b` (35/29 with `-w`). History noise only; the port notes' "cherry picked" prose is otherwise accurate.
- 1.20.1's `d4672c87` was a clean 198-line port; after `6c9d5118` it carries the identical `CELEBRATION_Z`/`heroPx+1` depth lifts as 1.21.1, which are **documented port-only adaptations** to depth-testing GUIs (26.1.2's 2D pose has no depth). At HEAD the port-vs-port file diff is comments + BufferBuilder only.
- Gametest registration uses the combined `FishtasticGameTests` on both ports (D10).

Counts: API-forced 3, established/deliberate 3, avoidable 0.

## 4. `ef118b31` — Discovery polish (`e954315b` / `5c2a74a9`)

- Content files identical to 26.1.2 apart from the `registryOrThrow` rename.
- `ReducedEffectsSyncPacket`/`FishtasticPackets`: 1.21.1 uses vanilla `CustomPacketPayload`/`StreamCodec`; 1.20.1 uses the pre-existing `FishtasticPayload`/`BufCodec(s)`/`NetworkApiSided` framework and `FishtasticPacketHandling.IPacketRegistrar` (its registrar signature takes the payload class as an extra arg — hence the 6-vs-7 line stat delta). Across the whole file these are **pre-existing branch framework differences** (introduced in B3.1/B3.2), used consistently; API-forced/established.
- Loader client hooks (`FishtasticFabricClient`/`FishtasticNeoForgeClient` vs `FishtasticForgeClient`) and gametest registration are loader/API-forced.

Counts: API-forced 3, established 3, avoidable 0.

## 5. `01da18c1` — Fishing bar/bobber styles (`569e3525` / `43eb1367`)

File sets and diffstats identical 3-way (8 files, +152/−15). At the commit state the only port-vs-port diffs were: `FishingMinigameClientHandler` sending via `NetworkApiSided` (established 1.20.1 shim) vs direct `ServerboundCustomPayloadPacket` (established 1.21.1 pattern), and the `CELEBRATION_Z` lift order afterwards fixed by align. At HEAD, `FishingMinigameAnimation` differs only in comments/BufferBuilder; `FishingBarStyles` differs only in the branch name inside a comment.

Counts: API-forced 1, established 2, avoidable 0.

## 6. `a86b349b` — Column/hanging/span cosmetics (`125bc550` / `b91c79df`)

Largest commit; comparisons done per file with patch-content diffs. Summary:

**API-forced / version-lossy (both ports, consistent):** `Blocks.IRON_CHAIN`→`CHAIN`; `isSolidRender()`→`isSolidRender(getter,pos)`; `lookupOrThrow`→`registryOrThrow`; `ITEMS_MODEL`-style `assets/fishtastic/items/*.json` dropped → `models/item/cosmetic_*.json` (no client item definitions pre-26.1); `tags/block`→`tags/blocks`; `saveWithoutMetadata(registryAccess)`/`loadWithComponents` vs `saveWithoutMetadata()`/`load`; `Player#blockInteractionRange()` vs port-only `interactionRange(Player)`; BufferBuilder patterns; `@NotNull` (Forge); `Direction.getNearest` face re-derivation.

**Documented deliberate:** `TankCosmeticMesh.Quad` keeps `tintState` because neither port has `QuadCollection`/quad-material info (port notes); `Baked` shape; `FishTankBlockEntityRenderer` static vs instance helper caches (1.21.1 note: static because its `snapshot()` is static); `MinecraftFrameBenchMixin` targets `runTick`; D4 scene set; `RenderSelfTest` scene/helper layout (1.21.1 ports `cosmeticpreview` minus cool-cam orbits; 1.20.1 omits it with the D4 note).

**Avoidable drift found — see table.** Key items: unapplied lossy block id in 1.20.1's `drowned_pagoda.json` (functional risk), property/helper naming (`TINTED_COSMETICS_PROPERTY`/`withBakedColor` vs `COSMETIC_TINTED_PROPERTY`/`withColor`), 1.20.1 spanning-NBT hand-rolled instead of the `BlockEntityNbt` child helper its own commit message claims, static-vs-instance renderer caches, `pose` vs `poseStack` parameter naming, and an unexplained normal-handling divergence in `TankCosmeticMesh` (1.21.1 zeroes the packed normal "let the renderer derive it"; 1.20.1 rotates normals with a `normalPose`). The `Quad` carrying an explicit `layer` on 1.20.1 vs a `tintState`-derived layer on 1.21.1 is defensible (different chunk-layer APIs) but the naming of the surrounding helpers is not.

Also: `en_us.json` missing `cosmetic_leaf_litter`/`cosmetic_fence_arch_pale_oak` on both ports is **not** a finding — those items do not exist on either port (grep: zero hits). `docs/fish-tank-rendering.md` extra "1.21.1: nothing to port" section appears identically on both ports (deliberate port documentation).

Counts (difference items examined): API-forced ~10, established/deliberate ~6, avoidable 7.

## 7. `0b46b99e` — Tank interior light (`9bcaee30` / `6c3f2934`)

Known suspects resolved:
- **`TankInteriorLight` `block(int)`/`sky(int)` (+11 lines, 1.20.1 only): API-forced, not avoidable.** 1.20.1's `LightTexture` exposes only `pack`; 1.21.1 has `LightTexture.block/sky`. The extra methods are the unpacking helpers 1.21.1 gets from vanilla (`(coords >> 4) & 0xF`, `(coords >> 20) & 0xF`), and both ports implement the identical `max(...)` semantics.
- **`litQuads`/`litLevel` cache: same mechanism on both ports** (`IdentityHashMap` + `synchronizedMap`, 65536 guard, `withLightEmission(q, level)`; 1.21.1 lines 44–46/141–145, 1.20.1 44–46/140–144). The earlier suspicion of a 1.20.1-only cache was a reporting gap. Only difference: `new BakedQuad(..., isShade, hasAmbientOcclusion)` vs `new BakedQuad(..., isShade)` — BakedQuad constructor signature, API-forced.
- **Stat deltas:** `IrisCompat` 42 vs 45 = comments/line-wrap only. `RenderSelfTest` 216 vs 210 = `/tick freeze` framing dropped (no such command; documented), `hasRenderedAllSections`→`hasRenderedAllChunks` (API rename), one extra comment; **nothing else in the lighting scene diverged** (verified hunk-by-hunk). `TankInteriorLightSyncPacket` 53 vs 50 and `FishtasticPackets` 6 vs 7 = established 1.20.1 networking framework/signature. `FishTankBakedModel` 50 vs 52 = `@NotNull` + fallback line, API-forced.
- `FishTankBlockEntityRenderer` #7 patch: both ports add the single `state.lightCoords = TankInteriorLight.apply(lightCoords)` line.
- Both ports' documented deviation stands: they bake a **block-light-only** floor on both loaders, where 26.1.2's quad-emission/uniform-lightmap raises sky light too (owner confirmation pending on both checklists).

Counts: API-forced 5, established/deliberate 4, avoidable 0.

## 8. `59df0b3d` — Tall bar styles' target art (`db4fc653` / `e69631e5`)

- Mechanism names confirmed identical across ports, all matching the branch reports: `FishingBarStyles.TALL_FISH_TARGET` (`GuiTextureItem`, `textures/gui/fish_target_2.png`), `FishingBarLayout#fishTargetSprite/targetScale`, `IGuiGraphicsExtension#fishtastic$renderSprite`, `GuiGraphicsMixin#fishtastic$renderSprite` → `FishingMinigameAnimation.renderSprite`. 1.20.1's "GUI sprite blitted in the item's place" is the same mechanism, same names.
- Assets: `fish_target_2.png` `4f77a2ec…` identical ports and **identical bytes to 26.1.2's `textures/item/fish_target_2.png`** (path differs: `gui/` vs `item/` — API-forced by the ITEM_MODEL absence). `fishing_bobber_2c.png` `33c1f50f…`, `tools/fishing_bar.ase` `760c730d…` identical 3-way.
- `FishingBarLayoutTest` is port-only (no 26.1.2 counterpart), identical on both ports — deliberate extra coverage.
- Port-vs-port diffs: branch-name comments + `GuiGraphicsMixin` crash-detail `getComponents()` vs `getTag()` (API-forced).

Counts: API-forced 3, established 2, avoidable 0.

## 9. `e0bb057e` — Freeze tank flocks (`1c7aeccb` / SKIPPED)

1.21.1 patch byte-for-byte the 26.1.2 change (comment + `tickRateManager().runsNormally()` guard, 4 lines). 1.20.1 skip is recorded in `checklist.md` with the reason (no `TickRateManager`, 0 mapping occurrences) and in the marker row. Verified.

## 10. `55ae36cf` — mcp-bridge `.env` re-read (`eeac4ef8` / `4f5d8db9`)

Both JS files byte-identical across all three branches (blob hashes above). No drift.

## 11. `76986ade` — Span preview renders (`f8632c72` / `23b8016d`)

Both PNGs byte-identical across all three branches (blob hashes above). No drift.

---

## Avoidable drift

| # | File | Branch(es) | Evidence | Recommended fix | Blast radius |
|---|---|---|---|---|---|
| 1 | `common/src/main/resources/data/fishtastic/fishtastic/cosmetic_structure/drowned_pagoda.json` | `port/1.20.1` | Line 11202 is `"minecraft:oxidized_lightning_rod"`; hash `13a52c5d` == 26.1.2, while 1.21.1 (`4b27da20`) applied `lightning_rod`. **Both** port commit messages state: "drowned_pagoda's one oxidized_lightning_rod (1.21.9+) is minecraft:lightning_rod" — 1.20.1 didn't apply its own documented lossy mapping. `oxidized_lightning_rod` does not exist on 1.20.1, and `CosmeticStructure.StructurePart` decodes `BlockState.CODEC`, which fails on unknown ids (per-element decode error), so the structure may fail to load entirely, not just lose one block. | Apply the documented mapping to 1.20.1's copy (replace with `minecraft:lightning_rod`), then confirm the datapack element actually registers. | #6 data/content gate: drowned_pagoda shop entry + placement, `spanpreview` self-test scene (verify the marker set includes it), datapack load log on 1.20.1 |
| 2 | `FishTankModelData` + `FishTankBakedModel` (loader) | both | 1.21.1: `TINTED_COSMETICS_PROPERTY`, `TankCosmeticMesh.withBakedColor(quad, argb)`; 1.20.1: `COSMETIC_TINTED_PROPERTY`, `withColor(...)` — same concept, different names (modifier `static` on `addCosmetics` also differed at the `#6`/`#7` boundary and converged at HEAD) | Pick one naming (record it) and rename one branch; no behaviour change | Rename-only; both ports recompile; no runtime gates beyond compile |
| 3 | `FishTankBlockEntity` (SpanLink NBT) | `port/1.20.1` | 1.20.1 writes `CompoundTag link = new CompoundTag()` + `output.put("SpanLink", link)` and reads with raw `input.contains/getCompound`; 1.21.1 uses `BlockEntityNbt.child/readChild`. 1.20.1's own message says "Tank NBT … goes through BlockEntityNbt child/readChild", but its `BlockEntityNbt` lacks those two methods (present on 1.21.1). Also log prefix `[FishTankBE.load]` vs `[FishTankBE.loadAdditional]`. | Either add `child`/`readChild` to 1.20.1's shim and use them, or amend the message; align the log prefix. | #6 NBT round-trip check (`FishTankGameTests` save/load) on 1.20.1 |
| 4 | `FishTankBlockEntityRenderer` | both | 1.21.1: `private static` `benchPiecesFor`/`collectSpanParts`/`snapshot` + static caches; 1.20.1: instance methods/fields (`benchPieceCache`, `spanShares`). 1.21.1 documents static as forced by its static `snapshot()`; 1.20.1's instance choice is unexplained | Document 1.20.1's necessity or align to static | Compile both; perfbench selftest scene re-check (cheap) |
| 5 | `CosmeticPlacementPreview` | both | `render(PoseStack pose, ...)` vs `render(PoseStack poseStack, ...)`, plus near-identical comments worded differently | Align parameter name/doc wording | None (cosmetic) |
| 6 | `FishTankRenderState` | `port/1.20.1` | Import order only (diff = one moved import line) | Trivial reorder if touching the file | None |
| 7 | `TankCosmeticMesh` | both | Normal handling differs with no note: 1.21.1 sets the packed normal aside ("no longer matches the rotated face; let the renderer derive it"), 1.20.1 rotates normals (`normalPose.transform(v).normalize()`) and writes them back; also `add(piece, quad, …, scratch)` vs `add(piece, layer, quad, …, layers)` signatures | Investigate whether the two produce identical chunk lighting; if yes, align/comment — if no, this is a behavioural difference, not just structure | #6 render verification (perfbench/spanpreview scenes, in-world tank), both ports |

Minor/uncertain, listed for completeness: `RenderSelfTest` helper reshuffle (`school`/`nave` extraction, helper order) on 1.20.1 is test-only and largely explained by D4; the `Quad` layer-field difference is defensible (documented API shape) but means the two `TankCosmeticMesh` files cannot be diffed for drift as one would like.

---

## Residuals / owner decisions (record, no action expected)

1. **#7 block-light-only interior light floor — both ports, both loaders.** 26.1.2 raises sky light too (quad emission / uniform lightmap); ports bake a block-light floor. Documented and owner confirmation pending on both checklists. Sodium/Iris untested.
2. **#9 skipped on 1.20.1** — no `TickRateManager`/`/tick freeze`; recorded with reason; 1.21.1 applied the change verbatim.
3. **#8's GUI-sprite mechanism on both ports** — no `DataComponents.ITEM_MODEL` (also absent on 1.21.1), so the tall styles draw a GUI sprite in the icon's place; item model JSONs live under `models/item/` rather than 26.1.2's `items/`; texture under `gui/` rather than `item/`. Port-only `FishingBarLayoutTest` retained.
4. **1.20.1 networking shim family** (`FishtasticPayload`, `BufCodec(s)`, `NetworkApiSided`, `FishtasticPacketHandling.IPacketRegistrar`) vs 1.21.1's vanilla payload API — pre-existing (B3.1/B3.2), consistently used; explains the 6-vs-7/53-vs-50 stat deltas.
5. **BufferBuilder/vertex-consumer and Java-level deltas** — `getBuilder/begin/vertex/uv/endVertex/end` vs `begin/addVertex/setUv/buildOrThrow`, `JAVA_17` vs `JAVA_21` mixin level, 1.20.1 `FishtasticMixinExtrasPlugin`, BufferBuilder/`1f` literals. Version-forced; all aligned-port files verified equal modulo these.
6. **Other API-forced renames carried by both ports**: `lookupOrThrow`→`registryOrThrow`, `IRON_CHAIN`→`CHAIN`, `isSolidRender(getter,pos)`, `ClickType`→`ContainerInput`, `hasRenderedAllSections`→`hasRenderedAllChunks`, `LightTexture.block/sky` absence, `TimerQuery` guard style (isRecording absent / F3-query gate), partial-tick delta-tracker split, `saveWithoutMetadata` registry argument, `@NotNull`, `BakedQuad` AO flag, `BlockState`-based `Placement`/`interactionRange`.
7. **D4 (owner decision)** — mcp/cool-cam scenes not ported to either branch; 1.20.1 additionally omits the `cosmeticpreview` scene (1.21.1 keeps it with one plain shot). Consistent, documented.
8. **D10 gametest registration architecture** — ports use the combined `FishtasticGameTests` + port-only stubs instead of 26.1.2's dual `FishtasticFabricGameTests`/`NeoForgeGameTestRegistration`. Both ports identical.
9. **1.21.1 `FishingMinigameAnimation` whole-file rewrites** (`fcb09410`, `e954315b`) are whitespace/indentation churn (`-w`: 207/3 and 35/29). No action; noted so future drift diffs don't misread the history.
10. `en_us.json` missing the two 26.1.2 keys for items that do not exist on either port — expected, not drift. `docs/fish-tank-rendering.md` port-only section is deliberate.
11. 1.21.1 checklist's `:neoforge:runData` "Cannot find Forge run template 'dataServer'" note and 1.20.1's `:forge:runData` shutdown hang note are environment/build findings, recorded by the agents; not drift.

## Skipped / could not determine

- **Secondary sweep of the six earlier rows** was name-level only: `faf47828`/`cfddd15d`/`5e26c375`/`b345b33e`/`7576a7ed`/`31adba43`. No port-vs-port file-set asymmetry found. Two asymmetries vs 26.1.2 left uninvestigated (out of primary scope): `7576a7ed` touched `FishTankRenderState` on 1.21.1 only (HEAD diffs between ports show import order only), and `31adba43` touched `FishtasticFabricClient`/`FishtasticNeoForgeClient` on 26.1.2 but on neither port (both ports symmetric — likely loader-hook redesign, not verified).
- Content of #6's two generated structure JSONs was verified by hash + a one-line diff, not full review (11k+ lines each). The `whale_fall.json` is identical 3-way; `drowned_pagoda.json` differs as finding #1.
- The `#2`/`#3` GuiGraphics rewrites were sampled at the diff level; no line-by-line audit of `FishingMinigameAnimation`'s ~1,500 lines was performed beyond the port-vs-port and vs-26.1.2 filtered diffs.
- Exact runtime effect of finding #1 (whole-structure decode failure vs single skipped part) was not executed; the recommendation includes confirming it in-game/headless.

---

## Checklist reconciliation

`git -C mc-1.21.1 show HEAD:docs/backport-pass2/checklist.md` vs the same on `mc-1.20.1` differ in exactly three places:

1. **Marker rows (lines 12–13).** Each copy has *its own* branch row current and its sibling's row stale:
   - 1.21.1 copy: `port/1.21.1` = `76986ade` (with its green-bar text: `:common:test` 87/87, Fabric/NeoForge gametests 282/282, runDatagen clean, both server smokes Done, `:neoforge:runData` template failure); `port/1.20.1` = `c656ea44` (stale).
   - 1.20.1 copy: `port/1.21.1` = `c656ea44` (stale); `port/1.20.1` = `76986ade` (with its green bar: `:common:cleanTest :common:test` 95/95, forge/fabric gametests 282/282, runDatagen clean, both server smokes Done, `:forge:runData` hang note; skip note for `e0bb057e`).
2. **Commit table — last six rows (lines 50–55).** 1.21.1 copy has `— (sibling pass)` in the 1.20.1 column; 1.20.1 copy has the values. Reconciled values: `a86b349b`→`b91c79df`; `0b46b99e`→`6c3f2934` (note: both loaders block-light-only floor; lighting scene's `/tick freeze` framing dropped); `59df0b3d`→`e69631e5` (GUI sprite via port-only `IGuiGraphicsExtension#fishtastic$renderSprite`); `e0bb057e`→*skipped* (no `TickRateManager`//`/tick freeze`, 0 mapping occurrences); `55ae36cf`→`4f5d8db9`; `76986ade`→`23b8016d` (PNGs bit-identical). The earlier rows already agree in both copies.
3. **Closing footnote (line 57).** 1.21.1's copy has the now-stale "snapshot taken during the 2026-10-01 sibling pass (that branch was at `43eb1367`)" note; 1.20.1's copy instead has the useful "`05565f7b` … content-identical to `76986ade`" note.

**Converged content both copies should carry:** both marker rows as given above (merging either branch's owner-look-owed list: tall/lava bar target art; tank cosmetics in a real world; tank interior-light floor incl. block-light-only deviation); the six commit rows with the 1.20.1 column filled as above; drop the "sibling pass snapshot" footnote and keep the `05565f7b` content-identity note. No other differences exist. (Not edited here, per read-only instruction — the report is the only file written.)

---

*Report produced by the drift-analyst pass, 2026-10-01. Untracked by design; do not commit.*
