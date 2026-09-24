# Backport checklist

> The tracked ledger for the 1.21.1 and 1.20.1 backports, following the potions-plus parity ledger's pattern. **Update it in the same commit as the work.** Plan: [`../backport-plan.md`](../backport-plan.md) (pass 1), [`README.md`](README.md) (pass 2).
> Status: `[ ]` not started · `[~]` in progress · `[x]` done · `[-]` dropped (with reason). The commit column holds the commit that finished the item, on the branch in the section header.

## Ported-through markers

Every 26.1.2 commit up to and including the marker is present on the branch, either ported or deliberately skipped (listed in the forward-port log). To bring a branch up to date: `git log --oneline <marker>..26.1.2`, port each commit, then move the marker.

| Branch | Ported through (`26.1.2` commit) | Updated | State |
|---|---|---|---|
| `port/1.21.1` | **`33986055`** | 2026-09-24 | Pass 2 written; rebased onto the S5/S6/S6c seams. A1, A2, A3 and A4 done (G-A4 passed 2026-09-24); A5 in progress; hook stage `unit`. Also carries `26.1.2` `7839f271` (cherry-picked as `e33f5792`); the marker moves past it at G1. |
| `port/1.20.1` | **`33986055`** (inherited when it's cut from `port/1.21.1` at G2) | — | not created |
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
| A5.2 | Tank model: NeoForge `IDynamicBakedModel` | [ ] | | |
| A5.2f | Tank model: **Fabric FRAPI (new code, N3)** | [ ] | | |
| A5.3 | BEWLR items, item properties (`cast`, `has_alert`, `pile_size`), treasure chest | [ ] | | |
| A5.6 | Fishing line hand, pose, held-item scale hooks | [ ] | | |
| A5.4a | Glint (ancestor `ItemRendererMixin`, `RenderBuffersMixin`) | [ ] | | |
| A5.4b | Outline atlas + GUI outline + world outline (spike code; findings F2–F5) | [ ] | | |
| A5.4c | GUI shader effects (silhouette, black outline, texture outline) | [ ] | | |
| A5.7 | `IrisCompat` no-op; `LevelRendererMixin` deleted; HUD layers above vanilla toasts (owner decision 2026-09-24); cosmetic-capture gizmos | [ ] | | The last two were not in the A5 design: see its "Added during A5". |
| G1 | Spike criteria on production code, both loaders, + Fabulous, GUI scales 1/2/4, Iris on NeoForge, 512-tank stress | [ ] | | |

### A6: Gametests and verification (gate G2, then hook stage → `full`)
| ID | Item | Status | Commit |
|---|---|---|---|
| A6.1 | Shared `FishtasticGameTests` harness (D10) + `fishtastic:empty` structure | [ ] | |
| A6.2 | Green bar: 78 / 163+1 / 21,955 / 264 Fabric / 264 NeoForge / datagen clean / fishsim byte-identical | [ ] | |
| A6.3 | Owner playtest on both loaders (+ Sunset Postcard, outline tiers) | [ ] | |
| G2 | `port/excludes.txt` and `portstub/` deleted; cut `port/1.20.1` | [ ] | |

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
| B1.1 | Java 17, Forge 47.4.x module (`neoforge/` → `forge/`), FAPI 0.92.12, JEI 15 | [ ] | |
| B1.2 | Java 17 audit: **no-op**, done on 26.1.2 by S5 (`cf643423`); verify `java17ApiGuard` + `--release 17` compile | [ ] | |
| G-B1 | Gate | [ ] | |

### B2: Item data
| ID | Item | Status | Commit |
|---|---|---|---|
| B2.1 | `ComponentKey<T>` + defaults + normalization; facade bodies; `ComponentKeyTest` | [ ] | |
| B2.2 | `BundleContents` port (+ 11 import changes) | [ ] | |
| B2.3 | Tooltip providers, `appendHoverText`, `ItemStackMixin` | [ ] | |
| B2.4 | Effect conditions (expected: no change) | [ ] | |
| B2.5 | `FishtasticItemPatch` (94 data files unchanged) | [ ] | |
| B2.6 | Tank BE ↔ item: `setPlacedBy`, `fishtastic:copy_tank_data`, `getCloneItemStack` | [ ] | |
| B2.7 | S1 items 1, 6, 7, 8 cleanup | [ ] | |
| G-B2 | Gate | [ ] | |

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
