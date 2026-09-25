# Backport checklist

> The tracked ledger for the 1.21.1 and 1.20.1 backports, following the potions-plus parity ledger's pattern. **Update it in the same commit as the work.** Plan: [`../backport-plan.md`](../backport-plan.md) (pass 1), [`README.md`](README.md) (pass 2).
> Status: `[ ]` not started · `[~]` in progress · `[x]` done · `[-]` dropped (with reason). The commit column holds the commit that finished the item, on the branch in the section header.

## Ported-through markers

Every 26.1.2 commit up to and including the marker is present on the branch, either ported or deliberately skipped (listed in the forward-port log). To bring a branch up to date: `git log --oneline <marker>..26.1.2`, port each commit, then move the marker.

| Branch | Ported through (`26.1.2` commit) | Updated | State |
|---|---|---|---|
| `port/1.21.1` | **`7839f271`** | 2026-09-25 | Pass 2 written; rebased onto the S5/S6/S6c seams. A1–A5 done; **G1 passed 2026-09-25** (all 11 self-test scenes green on both loaders, Iris live on both, `:common:test` 78/78, fishsim byte-identical to `26.1.2`). **A6.1 done 2026-09-25**: one shared `FishtasticGameTests` on vanilla `@GameTest`, `port/excludes.txt` empty, **263/263 green on both loaders** (`:fabric:runGametest` and `:neoforge:runGametest`). Hook stage raised to **`full`** (its first run passed as part of that commit). A6.2's green bar is green too (see its row); **A6.3 done 2026-09-25 (owner playtest, both fixes confirmed in-game on both loaders); G2 is what remains.** Includes `26.1.2` `7839f271` (cherry-picked as `e33f5792`). |
| `port/1.20.1` | **`7839f271`** (inherited via `port/1.21.1` at G2) | 2026-09-25 | Cut from `port/1.21.1` at G2 (`4ba2099c`), worktree `D:\GitHub\fishtastic-worktrees\mc-1.20.1`. **B1 done 2026-09-25**: build scaffolding green (`neoforge/`→`forge/`, Java 17, Forge 47.4.23, FAPI 0.92.12+1.20.1, Loom **1.17-SNAPSHOT**/Gradle 9.5 — not 1.11, see track-b-1.20.1.md "B1 as built"). B2 is next. |
| gelatin-ui `mc/1.21.1` | gelatin `26.1.2` @ **`5ae6aa4`** (1.0.31) | 2026-09-24 | **ported** (34 commits, tip `20c9f68`, clean). `1.0.31+1.21.1` published to mavenLocal; Fishtastic's `gelatinui_version` points at it. |
| gelatin-ui `mc/1.20.1` | — | — | not created |

**Rule:** a marker only moves forward, and only when the branch builds and its current gate passes. Once G3 is reached, D6 (lockstep) applies: a 26.1.2 release isn't cut until both markers equal its commit.

## Forward-port log

26.1.2 commits after `3b8427e4` and what each branch did with them. Add a row per commit as it lands on 26.1.2.

| `26.1.2` commit | Summary | `port/1.21.1` | `port/1.20.1` |
|---|---|---|---|
| `cf643423` | S5: Java 21-only library calls replaced with Java 17 equivalents | included via rebase | inherits |
| `afa6d4dc` | S5 guard: `java17ApiGuard` bytecode check on common/fabric/neoforge | included via rebase | inherits |
| `616b6566` | S6: `FishMoonPhase` + `FishtasticPermissions.gamemaster()` | included via rebase | inherits |
| `f896635b` | Book recipes datagen-owned again (`runDatagen` clean) | included via rebase | inherits |
| `f096fc8d` | NeoForge gametest discovery fixed (Loom mod group named `main`; 257 tests run) | included via rebase | inherits |
| `24203a87` | S6c: resource id construction routed through `util/Ids` (135 calls, 43 files) | included via rebase | inherits |
| `33986055` | S6c guard: `idConstructionGuard`; guard script renamed to `gradle/backport-guards.gradle` | included via rebase | inherits |
| `7839f271` | Stale in-place quest banner and JEI zero-size gui properties (the two A4 findings) | `e33f5792` (cherry-pick) | inherits |

---

## Seams on `26.1.2` (track P2)

| ID | Item | Status | Commit | Notes |
|---|---|---|---|---|
| S1 | `FishtasticItemData` facade | [x] | `a285e76f`..`3b8427e4` | |
| S1b | `HeadProfile` record for `setHeadProfile` | [ ] | | optional (track B, B2.1) |
| S1c | `FishtasticItemPatch` wrapper for reward patches | [ ] | | recommended (B2.5), D9 family |
| S2 | Packet codec locality | [x] | (already true) | confirmed by the B3.1 inventory: one `STREAM_CODEC` field per type |
| S3 | `fishsim` + `tank-shape-gen` at `--release 17` | [x] | `2b181f3c` | |
| S4 | Rendering logic apart from output | [~] | | the swarm, animator and bubbles are already split. Remaining: the `ItemEffect` data/render split (A2.5). |
| S5 | Java 17 library calls replaced | [x] | `cf643423`, guard `afa6d4dc` | 19 `getFirst` + 7 `Math.clamp` + 1 `SequencedMap` local (the other N7 hits were Java 8 `Comparator#reversed` / `Deque` calls). `java17ApiGuard` keeps it that way. |
| S6 | `FishMoonPhase` enum + `FishtasticPermissions.gamemaster()` | [x] | `616b6566` | 21 `requires` sites (20 commands + `mcp/McpBridgeCommand`). |
| S6c | `util/Ids` for resource id construction | [x] | `24203a87`, guard `33986055` | 135 calls in 43 files (`of` 77, `withDefaultNamespace` 45, `parse` 8, `tryParse` 5). `idConstructionGuard` keeps it that way. Backports change only the 4 `Ids` bodies; A2.1's `Identifier` type rename (100 files) still happens. |

## Decisions

| ID | Status |
|---|---|
| D1–D7 | settled (pass 1) |
| D8 Sunset Postcard parity mechanism | **decided 2026-09-24: full parity** |
| D9 Seams S5/S6 on 26.1.2 first | **decided 2026-09-24: yes** |
| D10 Shared gametest harness | **decided 2026-09-24: yes** |
| D11 Loom 1.17 / Gradle 9.5 on the port branches | **decided 2026-09-24: yes** |

---

## Track A: `port/1.21.1`

### A1: Scaffolding (gate G-A1, then hook stage → `compile`)
| ID | Item | Status | Commit | Notes |
|---|---|---|---|---|
| A1.1 | Gradle 9.5, Loom 1.17 remap, mojmap, toolchain 21, refmap names | [x] | A1 commit | Loom 1.17.493. Refmaps `fishtastic-<module>-refmap.json`; the common config's `refmap` key is injected Fabric-side only (see track A, A1 "Done"). |
| A1.2 | `gradle.properties` versions, mod metadata ranges | [x] | A1 commit | gelatin 1.0.16 until G-1.21.1. |
| A1.3 | Delete cool-cam and `mcp/**` and their call sites | [x] | A1 commit | 23 files; call sites in both entrypoints, both client entrypoints, `FishtasticCommand`. |
| A1.4 | Artifact names `2.0.1+1.21.1` (already the format; verify) | [x] | A1 commit | Verified. `publishCurseForge` now uploads `remapJar` (shadowJar is the named dev jar on the remapping Loom). |
| A1.5 | Jar-level refmap check (N8) | [~] | A1 commit | Wiring verified: the Fabric jar's `fishtastic.mixins.json` names `fishtastic-common-refmap.json`. No refmap files yet (no mixins compile at A1); re-check both files are in the Fabric jar at G-A2. |
| G-A1 | Gate: fishsim 163+1, tank-shape-gen 21,955, both jars build, the probe loads on both loaders | [x] | A1 commit | All green 2026-09-24. Both servers reach `Done` with the probe line; the only errors are 26.1.2 data (string ingredients, unregistered items), which A2/A3 fix. |

### A2: Core and server (gate G-A2, then hook stage → `unit`)
| ID | Item | Status | Commit | Notes |
|---|---|---|---|---|
| A2.0 | `port/excludes.txt` (62 files) + `portstub` (12 stubs) | [x] | A2 commit | The 62, plus render-only files the import scan missed (particles, glint render-type registration, `FishPileIcons`, `ClientTankFlocks`, `TankBubbleEmitter`, `IrisCompat`, `CosmeticTransformLoader`, `FishingHookRendererMixin`, `LevelRendererMixin`), Fabric datagen (A3) and the testmods (A6.1). 6 stubs, not 12 (see track A, A2 "Done"). |
| A2.1 | `Identifier` type rename (100 files; `Ids` bodies keep their factory names), registry access (35 + 13), permissions (1 line in `FishtasticPermissions`, S6), `setId` (4) | [x] | A2 commit | Plus `ResourceKey#identifier()` → `location()`. |
| A2.2 | Registration: BE types, `@EventBusSubscriber` bus | [~] | A2 commit | NeoForge `BlockEntityType.Builder`. The `bus = MOD` change is in the testmod, which waits for A6.1. |
| A2.3 | BEs (6), blocks (5), items (8): 1.21.1 signatures | [x] | A2 commit | `util/BlockEntityNbt`, `util/InteractionResults`. Plan correction: `PASS` → `SKIP_DEFAULT_BLOCK_INTERACTION`. |
| A2.4 | `FishCatchSavedData` → `SavedData.Factory` | [x] | A2 commit | File is `data/fishtastic_fish_catches.dat` (1.21.1 storage takes flat names). |
| A2.5 | Components: `TooltipDisplay`, `TooltipProvider`, `ItemStackTemplate`, `BREAK_SOUND`, `ResolvableProfile`; `ItemEffect` split | [x] | A2 commit | `ItemEffect` is data-only here; its render half is A5.4's `ItemEffectRenderData`. |
| A2.6 | Networking: Fabric `playS2C/playC2S`; `SetDayRatePayload` | [x] | A2 commit | Named `SetDayRatePacket` (tree convention). `util/StreamCodecs` for the 3 packets over 6 fields. |
| A2.7 | `MarineCompostRecipe` + serializer | [x] | A2 commit | |
| A2.8 | `FishingHookMixin` descriptors; `FishMoonPhase.at` (1 line, S6); `getDayTime` | [x] | A2 commit | Lava redirect now targets `BlockState#is(Block)`; verified at runtime on Fabric (the AP doesn't flag the old descriptor). |
| A2.8.c | Sunset Postcard: tickTime accumulator mixins + rate sync (D8) | [~] | A2 commit | Mechanism in place (both mixins resolve in the refmap). The 0.5-rate gametest comes with A6.1. |
| A2.9 | Commands, config, menus back in | [x] | A2 commit | |
| A2.10 | Unit tests: 64 of 78 (FishSphereContainerTest waits for A4) | [x] | A2 commit | 64 passed. |
| G-A2 | Gate | [x] | A2 commit | common compiles, 64 + 163/1 + 21,955 tests, both platforms compile, common refmap has all 7 server/client mixins. Server smoke run: both loaders boot through registration, then stop on 13 cosmetic structures that use post-1.21.1 blocks (A3, needs an owner decision). |

### A3: Datagen and resources (gate G-A3)
| ID | Item | Status | Commit | Notes |
|---|---|---|---|---|
| A3.1 | Fabric datagen providers on the FAPI 0.116 APIs | [x] | A3 commit | Tags use `getOrCreateTagBuilder` (the reference sources show `tag`, a genSources naming artifact; the jar has `getOrCreateTagBuilder`). Quest/shop providers need `FishtasticDataGenerator.registryElementsPathProvider`: 1.21.1 vanilla datagen writes registry elements without the `<ns>/` directory that FAPI/NeoForge load from. Rod and alert models carry `overrides`; the `minecraft:cast` and `fishtastic:has_alert` item properties still need client registration (A4/A5). |
| A3.2 | Regenerate recipes/advancements/loot/models, and review the diff against the expectation table | [x] | A3 commit | Advancements, loot, tags, blockstates, `models/block`: 0 diffs. Recipes: 72 generated + hand-authored `deep_sea_bait` converted by hand (73 of 74; `marine_compost` unchanged). 5 reviewed by hand. `data/fishtastic/fishtastic/**` differs only by the cosmetics decision. |
| A3.3 | Delete `assets/fishtastic/items/` (180); fix `copy_assets_to_common.py` `EXCLUDE` | [x] | A3 commit | `EXCLUDE` is now empty. |
| A3.4 | 0 × `Unable to load model` in the log | [x] | A3 commit | 0 on Fabric `runClient`. The one model warning is the `fish_tank` blockstate (A5.2). |
| A3.5 | Check the synthesized pack formats (34/48) | [x] | A3 commit | Not logged; both loaders synthesize from `getPackVersion` (FAPI `ModResourcePackUtil`, NeoForge `ResourcePackLoader`), which is 34/48 on 1.21.1. |
| A3.c | Cosmetics decision (owner, 2026-09-24): regular lantern on the fence arches; drop the pale oak arch and the leaf litter cosmetic; drop leaf litter from `birch_tree` | [x] | A3 commit | Port-branch diff: `FENCE_ARCH_WOOD_TYPES` without `pale_oak`, no `COSMETIC_LEAF_LITTER`, 4 JSONs + 2 lang keys removed, 1 part out of `birch_tree`. |
| — | Stray `data/fishtastic/{cosmetic_structure,item_effect}` checked on 26.1.2 | [x] | report only | Not leftovers: `CosmeticStructureProvider` and `ItemEffectProvider` write there (plain `createPathProvider`). Nothing reads them; the registry copies are hand-synced and the 4 item_effect copies have drifted. 26.1.2 fix: `createRegistryElementsPathProvider`, then delete the dirs. Not changed here. |
| G-A3 | Gate | [x] | A3 commit | Datagen diff matches the table (see track A, A3 "Done"). Both servers reach `Done` with no errors; `ServerLevelTickTimeMixin` applies (`required`, `defaultRequire: 1`). |

### G-1.21.1: gelatin-ui (branch `mc/1.21.1`)
| ID | Item | Status | Commit (gelatin) |
|---|---|---|---|
| G1.1 | Worktree + branch from `origin/main` (`6ff90c8`) | [x] | worktree `D:\GitHub\gelatin-ui-worktrees\mc-1.21.1` |
| G1.2 | Cherry-pick the 27 non-render commits (skip `9e93297`) | [x] | `f5f7415`..`77982a5` (27 picks, one per `26.1.2` commit) |
| G1.3 | `4cb61bc`, `f78bc6b`, `4f22fdc` render-line fixes | [x] | `f50fa3e`, `058d57c`, `52b2856` |
| G1.4 | Posed player on `RemotePlayer` (`d407ee6`) | [x] | `4b6f482` |
| G1.5 | `HiResItems` as a no-op (`36c7307`, `5ae6aa4`) | [x] | `f99d8ae`, `024b771` |
| G-G1 | Gate + `publishToMavenLocal 1.0.31+1.21.1` | [x] | `20c9f68` (tip; 34 commits) |

### A4: GUI (gate G-A4)
| ID | Item | Status | Commit | Notes |
|---|---|---|---|---|
| A4.1 | Switch to gelatin `1.0.31+1.21.1` | [x] | A4 commit | `gelatinui_version`, resolving from mavenLocal. |
| A4.2 | `GuiGraphicsExtractor` → `GuiGraphics`, 62 `pose()` sites, blit/text/item calls (14 files) | [x] | A4 commit | Plus `extractBackground` → `renderBg` and `extractImage`/`getHeight` → `renderImage`/`getHeight()` — different names *and* parameter orders from 26.1. The 11-arg `blit` also swaps (w,h) before (u,v). |
| A4.3 | Input records → raw signatures; `KeyMapping.Category`; Fabric HUD/tooltip/keybinding API names | [x] | A4 commit | `KeyMapping.Category` landed in A2 instead: `FishtasticKeyBinds.CATEGORY` is already the plain string `KeyMapping.Category.register` derives on 26.1. FAPI 0.116.7 has only `HudRenderCallback` (confirmed: no `HudElementRegistry`/`VanillaHudElements` in the sources), so the three HUD layers share one callback in the 26.1 registration order — and vanilla's toasts, rendered after `Gui.render`, now cover them. |
| A4.4 | JEI 19.18 compat (4 files) | [x] | A4 commit | No `AbstractRecipeCategory`, so both categories implement `IRecipeCategory` with their own blank drawable. |
| G-A4 | Gate: 78 unit tests; every screen opens on both loaders | [x] | A4 commit | 78/78; every screen opened through its real path on both loaders, and both item predicates verified against their override models (see track A, A4 "Done"). Server smoke clean on both loaders too. |

### A5: Rendering (design notes: `track-a5-rendering-1.21.1.md`)
| ID | Item | Status | Commit | Notes |
|---|---|---|---|---|
| — | `26.1.2` `7839f271`: in-place quest banner update, JEI zero-size gui properties (the two A4 findings, fixed on 26.1.2 first) | [x] | `e33f5792` | Verified on the port by the self-test's `fixes` scene: the in-place banner reads `5 / 10` with `Complete!`; JEI logs 0 `Received invalid gui properties` on NeoForge (was 2 per open). |
| A5.0 | `FishtasticShaders` seam, `FishtasticRenderTypes` | [x] | A5 checkpoint 1 | `RenderType.create` is `private` in vanilla, not reachable by subclassing: one AW line (NeoForge's AT and FAPI's AW already widen it at runtime). The seam ships the two bake programs with F2's per-program uniform lists. |
| A5.5 | Particles (10 classes) | [x] | A5 checkpoint 1 | 9 files (`util/SparkleParticle` already compiled). `TextureSheetParticle` + `setSprite`; providers pick from `level.getRandom()` (1.21.1 passes no `RandomSource`). |
| A5.1 | Tank BER + pile BER, `TankFlockAdapter` | [x] | A5 checkpoint 1 | Also `ClientTankFlocks`, `TankBubbleEmitter`, `CosmeticTransformLoader`. A fresh snapshot per frame, as 26.1.2. No culling override (26.1.2 has none). NeoForge dev needed `:fishsim` in the Loom `main` mod group. |
| A5.2 | Tank model: NeoForge `IDynamicBakedModel` | [x] | A5 checkpoint 2 | Shared port-only `client/compositemodel/FishTankGeometry` loads the 5,048 fragments and every block's texture once per reload (the bakery is only safe in the bake phase); meshing threads only run `FaceBakery`. `"loader"` model + vanilla blockstate; item via `ItemOverrides`, one render pass per layer. |
| A5.2f | Tank model: **Fabric FRAPI (new code, N3)** | [x] | A5 checkpoint 2 | `UnbakedModel` resolved for both `block/fish_tank` and `item/fish_tank` by a `ModelLoadingPlugin` (a `BlockModel` can't have a custom parent); `emitBlockQuads`/`emitItemQuads` with per-layer blend modes. |
| A5.2x | See-through blocks' chunk layers (found by A5.2) | [x] | A5 checkpoint 2 | 26.1 derives layers from texture alpha; 1.21.1 draws everything solid unless told. `client/FishtasticBlockRenderLayers`: the 32 stained glass blocks translucent, `clear_glass`/`borderless_glass` cutout (checked against the textures). They were opaque in the world on both loaders since A2. |
| A5.3 | BEWLR items, item properties (`cast`, `has_alert`, `pile_size`), treasure chest | [x] | A5 checkpoint 3 | One port-only dispatcher, `client/renderer/FishtasticItemRenderers`, behind Fabric `BuiltinItemRendererRegistry` and NeoForge `IClientItemExtensions`: Pile of Fish (flat fan, or pile block), 31 structure cosmetics, treasure chest. **No `pile_size` property**: 26.1.2 has no size-based icon, only a per-stack model swap, now a vanilla `CUSTOM_MODEL_DATA` marker the renderer reads. `cast`/`has_alert` were already done in A4. |
| A5.6 | Fishing line hand, pose, held-item scale hooks | [x] | A5 checkpoint 4 | `FishingHookRendererMixin` wraps `is(Items.FISHING_ROD)` in `getPlayerHandPos` (`require = 0`: NeoForge's `canPerformAction` patch already covers modded rods); `HumanoidModelMixin` reads the entity in `setupAnim(LivingEntity, …)`; `ItemInHandLayerMixin` brackets `ItemInHandRenderer.renderItem` in `renderArmWithItem`. 26.1.2's `ItemInHandRendererMixin` is the held item's **outline**, not a scale: deleted, A5.4's `ItemRenderer.render` hook covers it. The podium puppet is matched by gelatin's class name. |
| A5.4a | Glint (ancestor `ItemRendererMixin`, `RenderBuffersMixin`) | [x] | A5 checkpoint 5 | The ancestor's two mixins back with their shapes unchanged (`3b8b2c31`). `RenderBuffersMixin`'s fixed-buffer local must be declared `Map`: naming `SequencedMap` emits a class constant `java17ApiGuard` bans. `FishtasticGlintState` shrinks to two thread-locals; 26.1's render-state mixins are deleted. |
| A5.4b | Outline atlas + GUI outline + world outline (spike code; findings F2–F5) | [x] | A5 checkpoint 5 | Two `TextureTarget`s, LRU slots, bake at `GameRenderer.render` HEAD; the GUI ring is blitted from that atlas through `GuiGraphicsMixin` (1.21.1 has no vanilla GUI item atlas to sample), and the world outline draws after `ItemRenderer.render`'s `-0.5` translate, over `ITEM_ENTITY_TARGET` (F4, Fabulous). |
| A5.4c | GUI shader effects (silhouette, black outline, texture outline) | [x] | A5 checkpoint 5 | Two `ShaderInstance` programs (`gui_item_silhouette`, `gui_texture_outline`) with plain uniforms per draw. The black gear ring needs no program of its own: `FishtasticOutlineStyle.BLACK`, baked into the same atlas. **Deviation:** `outline_debug_uv` is not ported — its codec field parses, nothing reads it, no shipped effect sets it. |
| A5.7 | `IrisCompat` no-op; `LevelRendererMixin` deleted; HUD layers above vanilla toasts (owner decision 2026-09-24); cosmetic-capture gizmos | [x] | A5 checkpoints 5–6 | All three port-only items back and the three `port/excludes.txt` lines dropped. The gizmos are a world-render pass on both loaders; the HUD layers draw after vanilla's toasts through a `GameRendererMixin` inject, only while no screen is open. **A4's premise for that decision was wrong** — 26.1.2's toasts cover its HUD too (see track A5, A5.7). |
| G1 | Spike criteria on production code, both loaders, + Fabulous, GUI scales 1/2/4, Iris on NeoForge, 512-tank stress | [x] | G1 commit | **Passed 2026-09-25.** Base half (all 11 scenes, fixed 1600x900 window): NeoForge 21.1.209 34 checks / Fabric API 0.116.7 34 checks, 0 FAIL on each, 0 `Unable to load model`, 0 exceptions; 46 shots per loader. Iris half (`tank`+`outline` under Iris 1.8.8 + Sodium 0.6.13 + Complementary Reimagined 5.9.3): **both** loaders 15 checks, 0 FAIL, 0 `Unable to load model`, 0 exceptions, 14 shots each; the pack proved live by differencing each committed shot against its non-Iris base (100.00% / 98.79% / 97.28% of pixels differing, mean abs delta 38.89 / 53.50 / 6.87). `:common:test` 78/78 on a forced `--rerun`. fishsim `runHeadless` byte-identical to a `26.1.2` (`7839f271`) worktree across all 5 artefacts. G1's curated evidence is 26 base + 3 Iris shots per loader; `docs/port-evidence/1.21.1/` also carries the 7 per-checkpoint A5 shots — 65 files, 41 MB (weight flagged in track A5). |

### A6: Gametests and verification (gate G2, then hook stage → `full`)
| ID | Item | Status | Commit |
|---|---|---|---|
| A6.1 | Shared `FishtasticGameTests` harness (D10) + `fishtastic_empty` structure | [x] | A6.1 commit | **Done 2026-09-25, green on both loaders.** One shared class of 263 wrappers (262 generated by `build/a61-gen-shared-harness.py`, which reproduces it byte for byte, plus the Sunset Postcard test) on vanilla `@GameTest(template = "fishtastic_empty")`; `FishtasticTestSupport` is the per-platform mock-player seam. NeoForge's `@GameTestHolder("fishtastic")` + `@PrefixGameTestTemplate(false)` ride on that one class via PORT-ONLY compile-only stubs in `common/src/neoforge-gametest-annotations` (`neoforgeGametestAnnotationsApi`, taken as `testmodCompileOnly` by both platforms), and the all-air 8x8x8 ships twice - `data/fishtastic/structure/` and `data/minecraft/structure/` - because NeoForge prepends the holder namespace while Fabric parses the bare template; both are in src/testmod/resources, so neither ships. Deletes the 1,416-line Fabric harness, the 702-line 26.1-only NeoForge registration (NF 21.1 has no `TestData`/`GameTestInstance`/`TestEnvironmentDefinition`) and the 91-line `GameTestStructureProvider`; `port/excludes.txt` is empty. **Verified: `:fabric:runGametest` 263/263 and `:neoforge:runGametest` 263/263, 0 failures on each**, and the six Fabric-only tests now run on both. Also lands A2.8.c's Sunset Postcard gametest. See track A, "A6.1 as built". |
| A6.2 | Green bar: 78 / 163+1 / 21,955 / 263 Fabric / 263 NeoForge / datagen clean / fishsim byte-identical | [x] | A6.2 commit | **All measured 2026-09-25, each on a forced re-run** (`--rerun`, or `cleanTest test` where `--rerun` was ignored - a cached count is not a gate): `:common:test` **78** (0 failures, 0 skipped); `:fishsim:test` **164 = 163 + 1 skipped**; `:tools:tank-shape-gen:test` **21,955** (all passed); `:fabric:runGametest` **263/263** and `:neoforge:runGametest` **263/263** (262 shared + the Sunset Postcard test); `:fabric:runDatagen` executes and leaves `git status --porcelain` **empty**, which is what proves the `GameTestStructureProvider` deletion is complete; fishsim byte-identical to `26.1.2` (G1). **Counts corrected:** the 26.1.2 harness has **262** `@GameTest` methods, not 263 (that count included a javadoc mention), and the older Fabric report's 263 also carried the Fabric API's own `minecraft:always_pass`, which the shared harness no longer picks up. The NeoForge task is **`:neoforge:runGametest`**, not `:neoforge:runGameTestServer`. **A1.5 refmaps:** `fishtastic.mixins.json` is the only config with mixins, and the Fabric jar injects `refmap = fishtastic-common-refmap.json` into it (`fabric/build.gradle:110`); that file is present in the built jar. `fishtastic-fabric.mixins.json` also names `fishtastic-fabric-refmap.json`, which is **not** emitted - inert, because that config's `client`/`mixins` lists are empty (the Fabric module has no mixins), and it is the source of the dev-time warning "Reference map 'fishtastic-fabric-refmap.json' ... could not be read". |
| A6.3 | Owner playtest on both loaders (+ Sunset Postcard, outline tiers) | [x] | A6.3-fixes commit | **Playtest run 2026-09-25: two defects found, both fixed, both re-checked and confirmed in-game 2026-09-25.** (1) Both tooltips blitted `minecraft:container/bundle/slot_background`, a 24x24 sprite only `26.1.2` ships, so 1.21.1 drew `MissingSprite` behind the ghost bait/hook/charm icons — now 1.21.1's own 18x20 `container/bundle/slot`, at native size (owner's choice), so the slot row is 58px where `26.1.2`'s is 76px. (2) The leaderboard podium ordered by **depth, not paint order** — all three reported symptoms (player behind pedestal, fish pile behind podium block, block stack out of order) are one cause: 1.21.1's GUI pose is a real 3D `PoseStack` and every leaf bakes in its own z (vanilla items 150, gelatin's posed player 100, vanilla entity-in-inventory 50), while `26.1.2`'s 2D pose writes no depth at all. Fixed with a new opt-in gelatin seam, `IUIElement#setZOffset` — **bumps the dependency to `gelatinui 1.0.32+1.21.1`** (gelatin `mc/1.21.1` `e00acee`), with `PODIUM_BLOCK_Z_STEP = 32` / `PODIUM_PLAYER_Z_OFFSET = 400`. See track A, "A6.3 as found". **A6.3 done; G2 is next.** |
| G2 | `port/excludes.txt` and `portstub/` deleted; cut `port/1.20.1` | [x] | G2 commit | **Scaffolding removed 2026-09-25**: `port/excludes.txt` (empty at G2) and `gradle/port-excludes.gradle` deleted, the `build.gradle` apply removed. No `src/portstub/java` directories existed at G2 (A5 took the last stub). `common/src/neoforge-gametest-annotations/` is port-only but load-bearing (A6.1) and was left in place. `gw build -x test` succeeds and `:fabric:runDatagen` leaves `git status --porcelain` empty. `port/1.20.1` cut from this tip; see track B. |

### A7: Release
| ID | Item | Status | Commit |
|---|---|---|---|
| A7.1 | CI on JDK 21, changelog, CurseForge `2.0.1+1.21.1` | [ ] | |
| A7.2 | `fishtastic-worktrees/build-all.ps1` | [ ] | |

---

## Track B: `port/1.20.1`

### B1: Scaffolding
| ID | Item | Status | Commit |
|---|---|---|---|
| B1.1 | Java 17, Forge 47.4.x module (`neoforge/` → `forge/`), FAPI 0.92.12, JEI 15 | [x] | B1 commit |
| B1.2 | Java 17 audit: **no-op**, done on 26.1.2 by S5 (`cf643423`); verify `java17ApiGuard` + `--release 17` compile | [x] | B1 commit |
| G-B1 | Gate | [x] | B1 commit |

### B2: Item data
| ID | Item | Status | Commit |
|---|---|---|---|
| B2.1 | `ComponentKey<T>` + defaults + normalization; facade bodies; `ComponentKeyTest` | [x] | B2.1 commit |
| B2.2 | `BundleContents` class ported | [~] | B2.1 commit (class only; 11 call-site import changes still pending — those files stay excluded) |
| B2.3 | Tooltip providers, `appendHoverText`, `ItemStackMixin` | [~] | B2.1 commit (`TooltipProvider` dropped from the 3 component classes; `ItemStackMixin` wiring + the 6 item files' `appendHoverText` signature not done) |
| B2.4 | Effect conditions (expected: no change) | [ ] | |
| B2.5 | `FishtasticItemPatch` | [~] | B2.1 commit (class + `QuestReward` done; `ShopEntry`, the 94 JSON files' shape check, and `DailyQuestFamily` not done) |
| B2.6 | Tank BE ↔ item: `setPlacedBy`, `fishtastic:copy_tank_data`, `getCloneItemStack` | [ ] | |
| B2.7 | S1 items 1, 6, 7, 8 cleanup | [ ] | |
| G-B2 | Gate | [ ] | `:common:compileJava` green on the file set the B2.1 commit un-excludes; full `:common:test` (78 + ComponentKeyTest) still blocked on B2.2-B2.7 and the rest of common |

### B3: Networking
| ID | Item | Status | Commit |
|---|---|---|---|
| B3.1 | `BufCodec` / `BufCodecs` shim + `FishtasticPayload`; sed over 49 files | [ ] | |
| B3.2 | Fabric `PacketType`/`FabricPacket` registrar; Forge `SimpleChannel` registrar | [ ] | |
| B3.3 | Datapack registry sync (expected: no change) | [ ] | |
| G-B3 | `PacketRoundTripGameTests` green on both loaders | [ ] | |

### B4: Data and resources
| ID | Item | Status | Commit |
|---|---|---|---|
| B4.1 | Plural folders; gametest structure → `structures/` | [ ] | |
| B4.2 | 1.20.1 datagen (recipes, advancements, loot); JSON-based compost serializer | [ ] | |
| B4.3 | `blitSprite` → `blit`; 1.20.2+ vanilla ids audit | [ ] | |
| G-B4 | Datagen diff as expected | [ ] | |

### G-1.20.1: gelatin-ui
| ID | Item | Status | Commit (gelatin) |
|---|---|---|---|
| G2.1 | Branch `mc/1.20.1` from `mc/1.21.1` at G-G1 | [ ] | |
| G2.2 | Java 17, Forge module, menus (`IForgeMenuType`, `NetworkHooks`) | [ ] | |
| G2.3 | Sprites → textures; posed-player skins on the 1.20.1 `SkinManager` | [ ] | |
| G-G2 | Gate + `publishToMavenLocal 1.0.31+1.20.1` | [ ] | |

### B5: Remaining API deltas
| ID | Item | Status | Commit |
|---|---|---|---|
| B5.1 | `ResourceLocation` construction (the 4 `util/Ids` bodies only, S6c), BE/SavedData NBT, block `use` merge, `hurtAndBreak`, advancements, loot data | [ ] | |
| B5.2 | Vertex builder (6 sites), shaders, BER bounds on the BE | [ ] | |
| B5.3 | Forge wiring: `RegistryObjectHolder`, event buses, config, models, `initializeClient`, overlays, menu screens | [ ] | |
| B5.4 | `[-]` Fishing enchantment helpers: dropped, the tree doesn't call `EnchantmentHelper` | [-] | |
| B5.5 | Creative tabs, `Item.Properties` defaults | [ ] | |

### B6: Gametests and release (gate G3)
| ID | Item | Status | Commit |
|---|---|---|---|
| B6.1 | Shared harness on Forge 47 (`@GameTestHolder`, enabled namespaces) + Fabric 0.92 | [ ] | |
| B6.2 | Green bar: 78+ / 163+1 / 21,955 / 264 × 2 / datagen clean; owner playtest; release `2.0.1+1.20.1` | [ ] | |
| G3 | Lockstep begins (D6) | [ ] | |
