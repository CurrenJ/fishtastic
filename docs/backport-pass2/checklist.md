# Backport checklist

> The tracked ledger for the 1.21.1 and 1.20.1 backports, following the potions-plus parity ledger's pattern. **Update it in the same commit as the work.** Plan: [`../backport-plan.md`](../backport-plan.md) (pass 1), [`README.md`](README.md) (pass 2).
> Status: `[ ]` not started · `[~]` in progress · `[x]` done · `[-]` dropped (with reason). The commit column holds the commit that finished the item, on the branch in the section header.

## Ported-through markers

Every 26.1.2 commit up to and including the marker is present on the branch, either ported or deliberately skipped (listed in the forward-port log). To bring a branch up to date: `git log --oneline <marker>..26.1.2`, port each commit, then move the marker.

| Branch | Ported through (`26.1.2` commit) | Updated | State |
|---|---|---|---|
| `port/1.21.1` | **`51a8d367`** | 2026-09-24 | Pass 2 written; rebased onto the S5/S6/S6c seams. A1 not started. |
| `port/1.20.1` | **`51a8d367`** (inherited when it's cut from `port/1.21.1` at G2) | — | not created |
| gelatin-ui `mc/1.21.1` | gelatin `26.1.2` @ **`5ae6aa4`** (1.0.31) | — | not created |
| gelatin-ui `mc/1.20.1` | — | — | not created |

**Rule:** a marker only moves forward, and only when the branch builds and its current gate passes. Once G3 is reached, D6 (lockstep) applies: a 26.1.2 release isn't cut until both markers equal its commit.

## Forward-port log

26.1.2 commits after `298279e1` and what each branch did with them. Add a row per commit as it lands on 26.1.2.

| `26.1.2` commit | Summary | `port/1.21.1` | `port/1.20.1` |
|---|---|---|---|
| `552fed58` | S5: Java 21-only library calls replaced with Java 17 equivalents | included via rebase | inherits |
| `b7e24911` | S5 guard: `java17ApiGuard` bytecode check on common/fabric/neoforge | included via rebase | inherits |
| `c321593a` | S6: `FishMoonPhase` + `FishtasticPermissions.gamemaster()` | included via rebase | inherits |
| `f2085c7b` | Book recipes datagen-owned again (`runDatagen` clean) | included via rebase | inherits |
| `63c8716a` | NeoForge gametest discovery fixed (Loom mod group named `main`; 257 tests run) | included via rebase | inherits |
| `c8129cf3` | S6c: resource id construction routed through `util/Ids` (135 calls, 43 files) | included via rebase | inherits |
| `51a8d367` | S6c guard: `idConstructionGuard`; guard script renamed to `gradle/backport-guards.gradle` | included via rebase | inherits |

---

## Seams on `26.1.2` (track P2)

| ID | Item | Status | Commit | Notes |
|---|---|---|---|---|
| S1 | `FishtasticItemData` facade | [x] | `a697edc8`..`298279e1` | |
| S1b | `HeadProfile` record for `setHeadProfile` | [ ] | | optional (track B, B2.1) |
| S1c | `FishtasticItemPatch` wrapper for reward patches | [ ] | | recommended (B2.5), D9 family |
| S2 | Packet codec locality | [x] | (already true) | confirmed by the B3.1 inventory: one `STREAM_CODEC` field per type |
| S3 | `fishsim` + `tank-shape-gen` at `--release 17` | [x] | `7b8945b5` | |
| S4 | Rendering logic apart from output | [~] | | the swarm, animator and bubbles are already split. Remaining: the `ItemEffect` data/render split (A2.5). |
| S5 | Java 17 library calls replaced | [x] | `552fed58`, guard `b7e24911` | 19 `getFirst` + 7 `Math.clamp` + 1 `SequencedMap` local (the other N7 hits were Java 8 `Comparator#reversed` / `Deque` calls). `java17ApiGuard` keeps it that way. |
| S6 | `FishMoonPhase` enum + `FishtasticPermissions.gamemaster()` | [x] | `c321593a` | 21 `requires` sites (20 commands + `mcp/McpBridgeCommand`). |
| S6c | `util/Ids` for resource id construction | [x] | `c8129cf3`, guard `51a8d367` | 135 calls in 43 files (`of` 77, `withDefaultNamespace` 45, `parse` 8, `tryParse` 5). `idConstructionGuard` keeps it that way. Backports change only the 4 `Ids` bodies; A2.1's `Identifier` type rename (100 files) still happens. |

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
| ID | Item | Status | Commit |
|---|---|---|---|
| A1.1 | Gradle 9.5, Loom 1.17 remap, mojmap, toolchain 21, refmap names | [ ] | |
| A1.2 | `gradle.properties` versions, mod metadata ranges | [ ] | |
| A1.3 | Delete cool-cam and `mcp/**` and their call sites | [ ] | |
| A1.4 | Artifact names `2.0.1+1.21.1` (already the format; verify) | [ ] | |
| A1.5 | Jar-level refmap check (N8) | [ ] | |
| G-A1 | Gate: fishsim 163+1, tank-shape-gen 21,955, both jars build, the probe loads on both loaders | [ ] | |

### A2: Core and server (gate G-A2, then hook stage → `unit`)
| ID | Item | Status | Commit |
|---|---|---|---|
| A2.0 | `port/excludes.txt` (62 files) + `portstub` (12 stubs) | [ ] | |
| A2.1 | `Identifier` type rename (100 files; `Ids` bodies keep their factory names), registry access (35 + 13), permissions (1 line in `FishtasticPermissions`, S6), `setId` (4) | [ ] | |
| A2.2 | Registration: BE types, `@EventBusSubscriber` bus | [ ] | |
| A2.3 | BEs (6), blocks (5), items (8): 1.21.1 signatures | [ ] | |
| A2.4 | `FishCatchSavedData` → `SavedData.Factory` | [ ] | |
| A2.5 | Components: `TooltipDisplay`, `TooltipProvider`, `ItemStackTemplate`, `BREAK_SOUND`, `ResolvableProfile`; `ItemEffect` split | [ ] | |
| A2.6 | Networking: Fabric `playS2C/playC2S`; `SetDayRatePayload` | [ ] | |
| A2.7 | `MarineCompostRecipe` + serializer | [ ] | |
| A2.8 | `FishingHookMixin` descriptors; `FishMoonPhase.at` (1 line, S6); `getDayTime` | [ ] | |
| A2.8.c | Sunset Postcard: tickTime accumulator mixins + rate sync (D8) | [ ] | |
| A2.9 | Commands, config, menus back in | [ ] | |
| A2.10 | Unit tests: 64 of 78 (FishSphereContainerTest waits for A4) | [ ] | |
| G-A2 | Gate | [ ] | |

### A3: Datagen and resources (gate G-A3)
| ID | Item | Status | Commit |
|---|---|---|---|
| A3.1 | Fabric datagen providers on the FAPI 0.116 APIs | [ ] | |
| A3.2 | Regenerate recipes/advancements/loot/models, and review the diff against the expectation table | [ ] | |
| A3.3 | Delete `assets/fishtastic/items/` (180); fix `copy_assets_to_common.py` `EXCLUDE` | [ ] | |
| A3.4 | 0 × `Unable to load model` in the log | [ ] | |
| A3.5 | Check the synthesized pack formats (34/48) | [ ] | |
| — | Stray `data/fishtastic/{cosmetic_structure,item_effect}` checked on 26.1.2 | [ ] | |

### G-1.21.1: gelatin-ui (branch `mc/1.21.1`)
| ID | Item | Status | Commit (gelatin) |
|---|---|---|---|
| G1.1 | Worktree + branch from `origin/main` (`6ff90c8`) | [ ] | |
| G1.2 | Cherry-pick the 30 non-render commits (skip `9e93297`) | [ ] | |
| G1.3 | `4cb61bc`, `f78bc6b`, `4f22fdc` render-line fixes | [ ] | |
| G1.4 | Posed player on `RemotePlayer` (`d407ee6`) | [ ] | |
| G1.5 | `HiResItems` as a no-op (`36c7307`, `5ae6aa4`) | [ ] | |
| G-G1 | Gate + `publishToMavenLocal 1.0.31+1.21.1` | [ ] | |

### A4: GUI (gate G-A4)
| ID | Item | Status | Commit |
|---|---|---|---|
| A4.1 | Switch to gelatin `1.0.31+1.21.1` | [ ] | |
| A4.2 | `GuiGraphicsExtractor` → `GuiGraphics`, 62 `pose()` sites, blit/text/item calls (14 files) | [ ] | |
| A4.3 | Input records → raw signatures; `KeyMapping.Category`; Fabric HUD/tooltip/keybinding API names | [ ] | |
| A4.4 | JEI 19.18 compat (4 files) | [ ] | |
| G-A4 | Gate: 78 unit tests; every screen opens on both loaders | [ ] | |

### A5: Rendering (design notes: `track-a5-rendering-1.21.1.md`)
| ID | Item | Status | Commit |
|---|---|---|---|
| A5.0 | `FishtasticShaders` seam, `FishtasticRenderTypes` | [ ] | |
| A5.5 | Particles (10 classes) | [ ] | |
| A5.1 | Tank BER + pile BER, `TankFlockAdapter` | [ ] | |
| A5.2 | Tank model: NeoForge `IDynamicBakedModel` | [ ] | |
| A5.2f | Tank model: **Fabric FRAPI (new code, N3)** | [ ] | |
| A5.3 | BEWLR items, item properties (`cast`, `has_alert`, `pile_size`), treasure chest | [ ] | |
| A5.6 | Fishing line hand, pose, held-item scale hooks | [ ] | |
| A5.4a | Glint (ancestor `ItemRendererMixin`, `RenderBuffersMixin`) | [ ] | |
| A5.4b | Outline atlas + GUI outline + world outline (spike code; findings F2–F5) | [ ] | |
| A5.4c | GUI shader effects (silhouette, black outline, texture outline) | [ ] | |
| A5.7 | `IrisCompat` no-op; `LevelRendererMixin` deleted | [ ] | |
| G1 | Spike criteria on production code, both loaders, + Fabulous, GUI scales 1/2/4, Iris on NeoForge, 512-tank stress | [ ] | |

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
| B1.2 | Java 17 audit: **no-op**, done on 26.1.2 by S5 (`552fed58`); verify `java17ApiGuard` + `--release 17` compile | [ ] | |
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
