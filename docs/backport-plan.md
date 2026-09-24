# Backport Plan: Fishtastic 2.0 → MC 1.21.1 and MC 1.20.1

> **Status:** Pass 1, the broad-strokes outline (2026-09-24). A-SPIKE passed. Decisions D1–D11 all settled (§10; D8–D11 on 2026-09-24). S3 and S1 landed on `26.1.2` (A0.3 baseline below).
> **Pass 2 is written (2026-09-24): [`backport-pass2/`](backport-pass2/README.md).** It has one file per track, the A5 rendering design notes, and the tracked [checklist](backport-pass2/checklist.md) with the per-branch "ported through" markers. Pass 2 corrects a few pass 1 statements. They're marked *(pass 2)* inline below, and the full list is in `backport-pass2/README.md` §"What pass 2 changed in pass 1".
> Work item IDs (`A1.3`, `B2.1`, …) are stable, and pass 2 uses them.
> **Baseline (A0.3, frozen 2026-09-24):** branch `26.1.2` @ `298279e1` (Fishtastic 2.0.1 + seams S3 and S1, MC 26.1.2). Pass 1 surveyed `7076aeb6`, its parent before the seams.

---

## 1. TL;DR

- **Do the two ports one after the other: 26.1.2 → 1.21.1 first, then 1.20.1 from 1.21.1.** Don't port both from 26.1.2 at the same time. Dependency and scaffolding work for 1.20.1 can overlap with the second half of the 1.21.1 work (see §5).
- Most of the cost is the **client rendering rewrite**. 26.1.2 sits six rendering-API generations past 1.21.1: 1.21.2 shaders and render types, 1.21.4 client items, 1.21.5 RenderPipeline and the model rework, 1.21.6 GUI render state, 1.21.9 feature submission and render states, and 1.21.11/26.1 more rendering changes. 1.21.1 and 1.20.1 use the same *immediate-mode* renderer (BakedModel, ShaderInstance, BEWLR, immediate GuiGraphics). A sequential port pays for the rewrite once. Porting in parallel pays for it twice.
- The 1.21.1 → 1.20.1 step is a different kind of delta, mostly outside rendering and mostly mechanical. Data components become NBT, `StreamCodec`/`CustomPacketPayload` become `FriendlyByteBuf`, NeoForge becomes Forge, Java 21 becomes 17, and the datapack folder names go back to plural. It's well understood, but it touches a lot of files.
- **We have more of a head start than it first looks.** Fishtastic *began* on 1.21.1. The first 44 commits (up to `44064cc5`) are 1.21.1 code, and `06329830` ("Update to MC version 26.1.2") is a single 151-file migration commit. Reading it backwards gives a 26.1.2 → 1.21.1 lookup table for the core classes, including the old `ItemRendererMixin` glint path. gelatin-ui's `main` branch is also still on 1.21.1 (v1.0.16).
- **The biggest what-if is the quality-effect rendering, especially GUI outlines.** It's built on APIs that don't exist in 1.21.1, and the vanilla GUI item atlas the GUI outline samples wasn't added until 1.21.6. So the plan starts with a **rendering feasibility spike (A-SPIKE, §6)** before committing to the schedule. Because 1.20.1 inherits the 1.21.1 renderer, that one spike de-risks both backports.
- **gelatin-ui is on the critical path.** Its 1.21.1 line is 34 commits (15 versions) behind the 1.0.31 API that Fishtastic uses, and it has no 1.20.1 line at all.

---

## 2. What we're starting from

### 2.1 Codebase size (26.1.2 HEAD)

| Module | Java files | Lines | Ships in jar | Version-sensitive? |
|---|---|---|---|---|
| `common` | 335 | 44.5k | yes | heavily |
| `fabric` | 38 | 4.8k | yes | heavily (tank baked models, datagen, networking, registration) |
| `neoforge` | 25 | 2.7k | yes | heavily (tank baked models, registration, gametest registration) |
| `fishsim` | 38 | 8.6k | yes (shadowed) | **no MC dependency**; Java-version sensitive only |
| `tools/*` | 37 | 6.6k | no | no MC dependency; `tank-shape-gen` feeds fabric datagen |
| Resources | ~6.4k files | | yes | 5,088 generated tank fragment block models, 180 `assets/items` client-item defs, 12 shader files, 626 data files |

Tests: 9 common unit tests, 20 fishsim unit tests, 25 shared gametest files in `common/src/testmod`, plus the platform gametest harnesses (`FishtasticFabricGameTests` is 1,416 lines, `NeoForgeGameTestRegistration` is 701).

### 2.2 How much of the codebase each version-sensitive API touches (hits / files in `common`)

| Surface | Hits / files | Why it matters |
|---|---|---|
| `Identifier` | 506 / 88 | Renamed to `ResourceLocation` below 1.21.11. Mechanical. |
| `RenderState` (BER, item, entity render states) | 186 / 25 | Doesn't exist in 1.21.1 (1.21.4 / 1.21.9). Needs a rewrite. |
| `DataComponents.*` | 139 / 32 | 1.21.1 has it. **1.20.1 doesn't**, so everything goes back to NBT. |
| `StreamCodec` | 124 / 48 | 1.21.1 has it. **1.20.1 doesn't.** |
| `CustomPacketPayload` (23 payload types) | 123 / 25 | 1.21.1 has it. **1.20.1 doesn't.** |
| `GuiGraphics` (and the GUI render state behind it) | 142 / 17 | The class exists in both versions, but the 1.21.6 render-state internals don't. |
| `RenderPipeline` | 112 / 19 | 1.21.5+. Doesn't exist in 1.21.1. Needs a rewrite. |
| `RenderType` | 101 / 12 | Moved and reworked. Needs a rewrite. |
| `SubmitNodeCollector` | 30 / 7 | 1.21.9+. Doesn't exist in 1.21.1. |
| Custom item model types (`ItemModel`, `SpecialModelRenderer`, `ItemModelResolver`) | 61 / 14 | 1.21.4+. Replace with BEWLR or baked-model overrides. |
| `BlockStateModel`, `ModelBaker` | 20 / 7 | 1.21.5 model rework. The platform tank models go back to `BakedModel`. |
| `SavedData` | 128 / 35 | Only the definition sites change (`SavedDataType` → `Factory` → `computeIfAbsent`). |
| `ValueInput`/`ValueOutput` | 33 / 5 | Back to `CompoundTag` (with `HolderLookup.Provider` on 1.21.1). |
| Brigadier commands (25 commands) | 101 / 25 | Mostly stable. The 1.21.11 permission overhaul needs undoing. |
| Datapack registries (8 custom) | 64 / 31 | Stable in concept. Registration and folder layout differ per version and loader. |
| Java 21+ library calls (`getFirst`, `getLast`, `reversed`, `removeFirst`, `Math.clamp`) | ~48 sites | Fine on 1.21.1 (Java 21). **Must be rewritten for 1.20.1 (Java 17).** |
| Pattern-matching `switch` and record patterns | 0 | Good: nothing here blocks Java 17. |

### 2.3 Mixins (22 in common)

In 1.21.1, several of the **targets don't exist**, so those mixins get re-conceived rather than ported:
`GuiRendererMixin`, `GuiRendererExecuteDrawMixin` (1.21.6), `ItemFeatureRendererMixin` (1.21.9), `ItemModelResolverMixin`, `ItemStackRenderStateMixin` (1.21.4), `RenderTypeMixin` (the class moved), and the render-state halves of `FishingHookRendererMixin`, `HumanoidModelMixin`, `ItemInHandLayerMixin`, `ItemEntityRendererMixin` and `ItemFrameRendererMixin`.
The server-side mixins (`FishingHookMixin`, `PlayerAdvancementsMixin`, `QuickCollectPileMixin`, `SizedItemClickMixin`) should port with small target and signature fixes.
The 1.21.1 ancestor's mixin set (`ItemRendererMixin`, `GuiGraphicsMixin`, `LevelRendererMixin`, `RenderBuffersMixin`, `GameRendererMixin`) shows what the immediate-mode equivalents looked like.

### 2.4 History and existing work we can reuse

| Asset | Where | Use |
|---|---|---|
| Fishtastic 1.21.1 ancestor | `44064cc5` (107 Java files) | Working 1.21.1 Architectury Gradle setup: remapping loom, mojmap, NeoForge 21.1.209, Fabric API 0.116.7+1.21.1, `JAVA_21` mixins. |
| The migration commit | `06329830` (151 files, +2209/−1907) | Read in reverse, it's a 26.1.2 → 1.21.1 lookup table for registration, BE NBT, SavedData, packets, the tank BER, glint/`ItemRendererMixin`, creative tabs and item properties. |
| gelatin-ui 1.21.1 line | `gelatin-ui` `origin/main` (v1.0.16) | Starting point for gelatin-ui 1.21.1. It's 34 commits behind the 1.0.31 API. |
| potions-plus 1.21.1 port | `D:\GitHub\potions-plus-worktrees\mc-1.21.1` | Working **1.21.1 Architectury build with Fabric + Forge + NeoForge**, JEI 19.18.10.218, and a gametest harness on all three loaders. Its `2_0_backport_plan.txt` is the ledger format this plan follows. |
| apt-ores multi-version worktrees | `D:\GitHub\apt-ores-worktrees` | The `<repo>-worktrees/mc-<version>` layout, plus `build-all.ps1` to build every version and collect the jars. |
| Modding guide primers | `modding-guide/resources/primers/1.21.2.md` … `26.1.md` | Per-version API deltas. For this port, read them **in reverse**. |
| Decompiled vanilla | `modding-guide/resources/minecraft-merged-…-26.1.2-sources` | 26.1.2 only. We'll need 1.21.1 and 1.20.1 sources too. Loom's `genSources` in each worktree covers that. |

### 2.5 External dependencies

| Dependency | 26.1.2 | 1.21.1 | 1.20.1 | Notes |
|---|---|---|---|---|
| gelatin-ui | 1.0.31 | exists at 1.0.16 (`main`) | **doesn't exist** | Critical path. See §7. |
| JEI (optional compat) | 29.33.0.87 | 19.x (potions-plus uses 19.18.10.218) | 15.x | The API has shifted between majors, but only 10 compat files use it. |
| cool-cam (optional, Fabric only) | 0.1.0+26.1.2 | none | none | **Drop the compat on backports** (D1). It's promo-video tooling only. Exclude `fabric/compat/coolcam`. |
| Iris (optional, `IrisCompat`) | yes | yes | yes | Check which API the reflection targets on each version. |
| Architectury | injectables 1.0.13 only (no runtime API) | same | same | Keep it this way. `@ExpectPlatform` via injectables worked on the 1.21.1 ancestor. |
| Fabric API | 0.155.2+26.1.2 | 0.116.x+1.21.1 | 0.92.x+1.20.1 | |
| Loader | NeoForge 26.1.2.100 | NeoForge 21.1.x | **Forge 47.x** | See decision D2. |

---

## 3. Target matrix

| | 26.1.2 (baseline) | **1.21.1** | **1.20.1** |
|---|---|---|---|
| Java | 25 | 21 | **17** |
| Mixin compat level | `JAVA_25` | `JAVA_21` | `JAVA_17` |
| Loom | `dev.architectury.loom-no-remap` 1.14 | `dev.architectury.loom` (remapping) + `loom.officialMojangMappings()` | same as 1.21.1, on an older loom line that supports 1.20.1 |
| Names | unobfuscated official | mojmap (so most class names match 26.1 apart from the 1.21.11 rename shuffle) | mojmap |
| Loaders | Fabric + NeoForge | Fabric + NeoForge (no Forge, D2) | Fabric + Forge |
| Access widener header | `official` namespace | `named` | `named` |
| Resource pack / data pack format | current | 34 / 48 | 15 / 15 |
| Data folder names | singular (`recipe`, `loot_table`, `tags/item`) | singular (**same as now**) | **plural** (`recipes`, `loot_tables`, `advancements`, `tags/items`, `tags/blocks`) |
| Item stack data | DataComponents | DataComponents (older API: no `TooltipDisplay`, no component initializers) | **NBT tags** |
| Networking | `CustomPacketPayload` + `StreamCodec` | same (the 1.20.5 API) | `FriendlyByteBuf` + per-loader channels |
| Client items | `assets/*/items/*.json` + custom `ItemModel` types | model overrides + BEWLR | model overrides + BEWLR |
| Shaders | `RenderPipeline` + UBOs | `ShaderInstance` + JSON program + uniforms | same as 1.21.1 |
| Gametests | 1.21.5+ data-driven test instances | `@GameTest` annotations (Fabric) / `RegisterGameTestsEvent` (NeoForge) | `FabricGameTest` / Forge `@GameTestHolder` |

---

## 4. Workspace layout

Created: `D:\GitHub\fishtastic-worktrees\` (empty for now). This is the same convention as `apt-ores-worktrees` and `potions-plus-worktrees`.

```
D:\GitHub\fishtastic                     ← branch 26.1.2 (primary; stays the source of truth)
D:\GitHub\fishtastic-worktrees\
    mc-1.21.1\                           ← git worktree, branch port/1.21.1
    mc-1.20.1\                           ← git worktree, branch port/1.20.1 (created later, branched off port/1.21.1)
    build-all.ps1                        ← adapted from apt-ores: build every version, collect jars into dist\
    dist\  logs\
```

- Backport branches are `port/1.21.1` and `port/1.20.1` (the primary stays `26.1.2`). Feature branches follow the existing `feature/<mc>/<name>` pattern.
- Git hooks come from the tracked `scripts/git-hooks` (`core.hooksPath`), so each branch runs its **own** pre-commit script. The existing NeoForge-hang caveat carries over.
- **Hook policy on port branches.** The port branch's `pre-commit` reads a tracked stage from `scripts/git-hooks/port-stage` and raises its checks as gates pass: `none` (A1, before anything compiles) → `compile` (A1 gate) → `unit` (A2 gate: `:common:test`, `:tools:tank-shape-gen:test`) → `full` (A6.1: plus Fabric gametests, as on the primary). Raising the stage is part of each gate's commit. No `--no-verify`.
- **JDK per worktree.** `~/.gradle/gradle.properties` pins JDK 25 for the primary. Each port worktree has an untracked, gitignored `scripts/git-hooks/local-java-home` (JDK 21 for `port/1.21.1`, JDK 17 for `port/1.20.1`) that the hook passes to Gradle as `org.gradle.java.home`. Manual Gradle runs in a port worktree need the same `-Dorg.gradle.java.home=…`.
- `.github/workflows/*` hard-code JDK 25. Each backport branch needs its own JDK and version matrix.
- The 1.21.1 and 1.20.1 worktrees each need `genSources`, which gives local decompiled vanilla to check against, like the 26.1.2 source the guide already has.

---

## 5. Strategy: sequential, not parallel

### Decision

**Sequential, in the order 26.1.2 → 1.21.1 → 1.20.1.** The 1.20.1 branch is cut **from the 1.21.1 branch** once 1.21.1 passes gate G2 (§6). Dependency and scaffolding work for 1.20.1 runs *alongside* the second half of the 1.21.1 port.

### Why

1. **The rendering rewrite is the most expensive single item, and 1.21.1 and 1.20.1 share its result.** Both use immediate-mode rendering: `BakedModel` / FRAPI / Forge `IDynamicBakedModel` + `ModelData`, JSON `ShaderInstance` programs, `BlockEntityWithoutLevelRenderer`, direct `GuiGraphics` blits, and `VertexConsumer` in the BER. Nearly all of it carries from 1.21.1 to 1.20.1. The one real change is the `VertexConsumer` builder: `addVertex`/`setColor` in 1.21 versus `vertex().color()…endVertex()` in 1.20.1. Porting in parallel from 26.1.2 means doing this rewrite, which is also the part most likely to need visual tuning, twice.
2. **The two deltas are different in kind, so running them in order breaks the work into pieces we can reason about.** 26.1.2 → 1.21.1 is "rendering and model APIs moved; data and networking stay the same." 1.21.1 → 1.20.1 is "rendering stays the same; data, networking, Java and the loader moved." Jumping straight from 26.1.2 to 1.20.1 hits both at once, across about nine version steps, with no working intermediate to check against.
3. **Most of the reference material is for 1.21.1:** the Fishtastic ancestor, the migration commit, gelatin-ui `main`, the potions-plus 1.21.1 build, and the NeoForge/Fabric 1.21.1 docs. 1.20.1 has almost none. Once the 1.21.1 port works, it becomes the 1.20.1 port's reference.
4. **Bug fixes flow one way.** Anything we find during the 1.21.1 port (logic that silently depended on 26.1 behaviour, datagen gaps, codec edge cases) is already fixed when 1.20.1 branches off. Parallel branches would each find those bugs separately.

### What we give up, and how to limit it

The 1.20.1 release lands later. To limit that, we start the long-lead 1.20.1 work early, since none of it depends on the 1.21.1 port being finished:

| Parallel track | Can start | Blocks |
|---|---|---|
| **P0:** A-SPIKE, the rendering feasibility spike (§6) | immediately, once the A1 scaffold exists | The A5 design, and decision D7 |
| **P1:** gelatin-ui 1.21.1 catch-up (§7) | immediately | Fishtastic 1.21.1 GUI (phase A4) |
| **P2:** "Seam" refactors on 26.1.2 (§8), done on the primary branch before branching | immediately (optional) | Makes A and B smaller |
| **P3:** gelatin-ui 1.20.1 port (from gelatin-ui 1.21.1) | after P1 | Fishtastic 1.20.1 GUI |
| **P4:** 1.20.1 Gradle/Forge skeleton, plus the Java 17 audit of `fishsim` and `tank-shape-gen` | after A1 | B1 |
| **P5:** 1.20.1 shims for NBT components and buffer codecs (§9.2) | after A2 | B2 and B3 |

### Alternatives considered

- **Parallel, both from 26.1.2.** Rejected: the rendering rewrite happens twice (reason 1), and neither branch has a working reference.
- **1.20.1 first, then 1.21.1 "for free."** Rejected: 1.21.1 isn't free from 1.20.1. Components and payloads would have to be re-added, which undoes work. The 1.21.1 reference material doesn't help a 1.20.1-first port either.
- **One multi-version source tree** (Stonecutter-style preprocessor comments, or a version-abstraction module). Rejected for now: the 26.1.2 client code differs *structurally* from the immediate-mode code (render states, submission, pipelines), not in a line or two. `#if` blocks would take over the rendering code. We can look at this again after both backports exist, maybe limited to the non-rendering layers (§11, open question Q5).
- **Replay history from the 1.21.1 ancestor** (`44064cc5` plus cherry-picks of all 342 later commits). Rejected: nearly every later commit is written against 26.1.2 APIs, so each pick would be a port of its own. Instead we **port the current tree once**, using the ancestor and the migration commit as reference.

---

## 6. Phase plan: 26.1.2 → 1.21.1 (track A)

Approach: create `mc-1.21.1` from `26.1.2` HEAD. Restore the 1.21.1 build scaffolding from `44064cc5` and potions-plus. Then get it compiling **in layers**: temporarily exclude the client-rendering packages from the source sets, so the server and common game logic compile, test and datagen first. Then add the client subsystems back one at a time.

### A0: Decisions and scope (before any code)
- **A0.1** ~~Settle decisions D1 to D6 (§10).~~ All settled 2026-09-24.
- **A0.2** Cut list (decided, D1): **`mcp/`** (the retired MCP bridge) and the **cool-cam compat** (`fabric/compat/coolcam`, used only to capture promo videos on 26.1.2). Everything else ships on every version: `examples/`, `TestItem`, all debug, authoring and admin commands, and the leaderboard podium (which means porting gelatin-ui's posed-player rendering).
- **A0.3** ~~Freeze the feature baseline: the `26.1.2` commit that lands S1 and S3 (D5).~~ Done 2026-09-24: **`298279e1`** on `26.1.2`. `port/1.21.1` is rebased onto it. Forward-port ranges start from this commit ("ported through `298279e1`").

### A1: Build scaffolding (gate: an empty mod loads on both loaders)
- **A1.1** Switch the root, common, fabric and neoforge Gradle files from `loom-no-remap` to the remapping Architectury Loom with mojmap. Use the Java 21 toolchain, `JAVA_21` mixin configs, and the `named` access-widener header. Reference: `44064cc5` and potions-plus `mc-1.21.1`.
- **A1.2** Change `gradle.properties` versions: MC 1.21.1, NeoForge 21.1.x, Fabric API 0.116.x, JEI 19.x, gelatin-ui 1.21.1 line. Update the mod metadata version ranges.
- **A1.3** Remove cool-cam from the build. Keep `fishsim` and `tools/*` as plain-Java modules. They compile unchanged on Java 21, but the target should come down (see §8 S3).
- **A1.4** Check `publishCurseForge` and `build-all.ps1`, and make the artifacts carry the version suffix `2.0.x+1.21.1`.

### A2: Core and server logic (gate: `:common` compiles with client rendering excluded, unit tests pass)
Mostly mechanical. The migration commit (`06329830`) covers most of the patterns:
- **A2.1** `Identifier` → `ResourceLocation` and the other 1.21.11 "rename shuffle" names (e.g. `Util` package moves). ~~`Level#isClientSide()` → the field.~~ *(pass 2: the method exists on 1.21.1 too, so there's no change.)* Undo the permission-overhaul changes in commands. Undo `Item.Properties#setId` and `BlockBehaviour.Properties#setId` (1.21.2).
- **A2.2** Registration through `IRegistrationApi` on both loaders. Datapack registries (8 of them) through `DataPackRegistryEvent` on NeoForge and `DynamicRegistries` on Fabric. The folder layout (`data/<ns>/fishtastic/<registry>`) matches 1.21.1 already.
- **A2.3** Block entities: `ValueInput`/`ValueOutput` → `load`/`saveAdditional(CompoundTag, HolderLookup.Provider)`. BE removal handling (1.21.5). Container and inventory BEs.
- **A2.4** `SavedData`: `SavedDataType` + codec → `SavedData.Factory` + codec-through-`NbtOps` (`FishCatchSavedData`, backups, quest state, leaderboards). Check the tiered-backup and restore commands.
- **A2.5** Data components: use the 1.21.1 builder API and drop the 26.1 extras (component initializers, `TooltipDisplay`, 1.21.5 getter changes). Keep the 9 custom component records.
- **A2.6** Networking: the 23 payloads keep their `StreamCodec`. Rework the registrars (`FabricPacketRegistrar`, `NeoForgePacketRegistrar`) against the 1.21.1 payload APIs.
- **A2.7** Recipes (`MarineCompostRecipe`, serializer), loot, advancements (`PlayerAdvancementsMixin`), tags. Undo the 1.21.2 recipe-registry and ingredient changes and the 26.1 "loot type unrolling" and serializer records.
- **A2.8** Fishing core: `FishingHookMixin`, the minigame managers, rod items. Check the fishing-luck and lure enchantment helper signatures and the fishing loot context.
- **A2.9** Commands (25), config (NeoForge `ModConfigSpec`, the Fabric equivalent), menus (`MenuType` access-widener, extended menu openers).
- **A2.10** Port the unit tests (`common/src/test`, `fishsim`) and get them green.

### A3: Datagen and resources (gate: datagen runs, the generated tree diffs cleanly against 26.1.2 apart from the expected format differences)
- **A3.1** Port the Fabric datagen providers (14 files) to the 1.21.1 `FabricDataGenerator` APIs. This includes `FishTank{Frame,Glass,Sand}ModelProvider`, which uses `tools:tank-shape-gen`, and the quest, shop and cosmetic providers.
- **A3.2** **Regenerate** the formats that changed (recipes, advancements, loot tables, model and blockstate JSON) rather than copying them. Copy the stable assets (textures, sounds, lang, particles, and the hand-authored datapack registry JSON) after a schema check.
- **A3.3** Delete `assets/fishtastic/items/*.json` (the 1.21.4 client-item defs, 180 files). Their replacement is in A5.
- **A3.4** Check that the 5,088 tank fragment models still match what the loader requests (see the tank-fragment-loader contract: grep the log for `Missing block model`).
- **A3.5** Set the `pack.mcmeta` formats.

### A4: GUI (gate: every screen opens and works)
- **A4.1** Needs P1 (gelatin-ui 1.21.1 at API parity).
- **A4.2** Screens: Encyclopedia (1k lines), Quest Log (1.7k), Leaderboards, Tank Browser, Assembly, Organizer, Shape Gallery, tutorial and notification overlays. Undo the 1.21.9 input-handling consolidation (`MouseButtonEvent` → the raw `(x, y, button)` signatures). Remove `KeyMapping.Category`.
- **A4.3** Tooltip components (`ClientTooltipComponent`) and the GUI side of `IGuiGraphicsExtension`.

### A-SPIKE: Rendering feasibility spike (do this first, in parallel with A1 and A2)

> **Result (2026-09-24): PASS.** Every exit criterion passed on Fabric and NeoForge, and under Iris + Complementary
> Reimagined on Fabric. The combined bake-atlas design works, and D7 is resolved: **no degraded GUI outline is needed.**
> The full report, evidence and findings are in `docs/spike-1.21.1-rendering.md` on branch `spike/1.21.1-rendering` (commit `d3ded9da`).
> Still open from the spike: Fabulous graphics, Iris on NeoForge, and the Fabric FRAPI tank model.

**This is the biggest unknown in the whole backport.** Do it before committing to the A-track schedule. It needs only the A1 scaffold, and can even run in a throwaway 1.21.1 dev environment.

Why this is the biggest unknown, and not just the biggest job: every hook the quality-effect system uses is a 26.1-era API. All 11 steps of the migration checklist in `docs/item-effect-rendering.md` point at code that **doesn't exist in 1.21.1**:

| 26.1.2 mechanism | Introduced | 1.21.1 equivalent |
|---|---|---|
| GUI outline samples vanilla **`GuiItemAtlas`** (neighbour-scan shader over the atlas slot) | 1.21.6 GUI render state | **None.** GUI items draw straight to the main framebuffer, so there's no atlas slot to sample. The approach itself has to change, not just the hook. |
| `GuiRenderer.submitBlitFromItemAtlas` / `executeDraw` hooks, `@Coerce` on `GuiRenderer.Draw` | 1.21.6 | Hook `GuiGraphics.renderItem` / `ItemRenderer.render` in GUI context |
| Effect params in a `GpuBuffer` UBO, bound through `RenderPass`; `Globals` UBO for time | 1.21.5 Blaze3D | `ShaderInstance` uniforms (`safeGetUniform(...).set`), game time passed in by hand |
| Per-effect `RenderPipeline` | 1.21.5 | Per-effect `ShaderInstance` + `RenderType` (`RenderStateShard.ShaderStateShard`) |
| Effect lookup by `ItemStackRenderState` / quads identity (`ItemModelResolverMixin`, `ItemStackRenderStateMixin`) | 1.21.4 | **Simpler:** the `ItemStack` is in scope at `ItemRenderer.render`, so the side-channel identity maps mostly go away |
| Glint swap in `ItemFeatureRenderer.getFoilRenderType` | 1.21.9 | `ItemRenderer.getFoilBuffer` / `getFoilBufferDirect` / `getCompassFoilBuffer`. **Already done once:** the 1.21.1 ancestor's `ItemRendererMixin` (`44064cc5`) did exactly this. |
| World outline: frame-head bake using `SubmitNodeStorage`/`FeatureRenderDispatcher`, `submitCustomGeometry` quad | 1.21.9 | Bake into our own `TextureTarget` with a plain `BufferSource`, then draw one quad through `MultiBufferSource` in the item-entity and item-frame `render` methods |
| Iris compatibility (pipeline assignment) | 26.1-era Iris API | Iris 1.21.1 API, which is different. Re-verify. |

**Where the risk actually sits.** The glint path is low risk because it has been done on 1.21.1 before. The world-outline path is medium risk: its design (our own mask and outline atlases, bake then compose then draw) *already* avoids depending on vanilla's atlas, and `TextureTarget` and `ShaderInstance` are enough to express it. **The GUI outline path is the real what-if**, because the vanilla feature it's built on doesn't exist.

**Recommended 1.21.1 design to prove out:** **combine the GUI and world outline paths on the Fishtastic-owned bake atlas** (`FishtasticItemOutlineAtlas`). In the GUI, blit the pre-baked outline ring from our atlas behind or around the item at `GuiGraphics.renderItem`, instead of computing the outline per fragment from vanilla's atlas. That gives one outline implementation where 26.1.2 has two, and the padded slots even improve on the 26.1 GUI path (the outline can extend past the 16-px slot). Things to measure: bake cost when an inventory full of quality items opens (LRU at 100 slots, maybe raised for the GUI), legendary pinwheel animation, which only re-runs the compose pass so should be cheap, and GUI scale handling.

**Spike exit criteria** (these become gate G1 for A5): on 1.21.1, on both loaders, (1) a custom quality glint on a held item, (2) a static and an animated legendary outline around a GUI slot item, (3) a world outline on a dropped item and on one in an item frame, (4) the same with an Iris shaderpack loaded. If (2) can't be done with an acceptable look or cost, go to the fallback: **degrade the GUI outline on backports** to a static pre-rendered overlay sprite per tier, or a tinted slot background. That's a legitimate scope cut (decision D7). It shouldn't be found out halfway through A5.

The same kind of spike applies, at lower risk, to **the fish tank dynamic model** (A5.2): a single tank rendering configurable frame, glass and sand textures through FRAPI and `IDynamicBakedModel` on 1.21.1. The ancestor had this working, so it's a confirmation, not a research question.

Because 1.20.1 inherits the 1.21.1 renderer (§5), **this one spike de-risks both backports.** The 1.20.1-specific additions are small: the `VertexConsumer` builder API, and `ShaderInstance` registration via Forge's `RegisterShadersEvent`.

### A5: Rendering (the big one; gate: every render path works in game)
Each item is a re-implementation on the immediate-mode API, not a port.
- **A5.1 Fish tank BER** (`FishTankBlockEntityRenderer`, 1k lines, plus `TankFlockAdapter`, `FishAnimator`, `TankBubbleEmitter`). The render-state extraction and submit pipeline goes back to direct `render(BE, partialTick, PoseStack, MultiBufferSource, …)`. Keep the fishsim-driven animation logic. Only the output side changes.
- **A5.2 Fish tank dynamic block models** (fabric `FishTank*Fabric` ×5, neoforge `FishTank*` ×8). `BlockStateModel` goes back to `BakedModel` + FRAPI `emitBlockQuads` on Fabric and to `IDynamicBakedModel` + `ModelData` on NeoForge. The shared `client/compositemodel` utilities should mostly survive. Re-check `docs/fish-tank-rendering.md` for pitfalls that are specific to 26.1.
- **A5.3 Custom item rendering.** `CosmeticStructureItemModel`, `FishPileBlockItemModel`, `PileOfFishItemModel` and the fish-tank item model become BEWLR (`BlockEntityWithoutLevelRenderer`) or baked-model overrides. Replace the item-size scaling hooks (`ItemStackRenderState`/`ItemModelResolver` mixins) with `ItemRenderer`-level hooks. *(pass 2: those two mixins are the glint side channel, not size scaling. Size scaling lives in the tank BER, `HumanoidModelMixin` and the held-item mixins; see A5.3/A5.6.)* The `44064cc5` `ItemRendererMixin` shows the 1.21.1 approach.
- **A5.4 Item effects: glint, GUI outline, world outline** (`docs/item-effect-rendering.md`). This is the highest-risk item. `RenderPipeline` + UBO params go back to a `ShaderInstance` per effect with uniforms. The GUI outline path goes back to hooking `GuiGraphics.renderItem`, not the `GuiRenderer` render-state pipeline. `FishtasticItemOutlineAtlas` needs a check against the 1.21.1 texture and atlas API. Port the 12 shader files to GLSL 150 core with JSON program definitions, and register them (NeoForge `RegisterShadersEvent`, Fabric `CoreShaderRegistrationCallback`).
- **A5.5 Particles** (9 classes). `SingleQuadParticle` and the 26.1 render types go back to `TextureSheetParticle` and `ParticleRenderType`.
- **A5.6 Entity and held-item hooks.** Fishing line and bobber (`FishingHookRendererMixin`), fisherman pose (`HumanoidModelMixin`), and held item (`ItemInHandLayerMixin`, `ItemInHandRendererMixin`). Move from render-state fields to direct entity access.
- **A5.7** `IrisCompat`, `RenderBuffersMixin` and `LevelRendererMixin` (the world outline pass and the custom buffers).

### A6: Gametests and verification (gate G2: 1.21.1 feature-complete)
- **A6.1** Port the shared testmod suites (25 files) and both platform harnesses from 26.1 data-driven test instances to 1.21.1 `@GameTest` registration. Carry over the structure providers.
- **A6.2** Green on: build, unit tests, Fabric gametests, NeoForge gametests (backgrounded, because of the hang caveat), and datagen producing no diff.
- **A6.3** In-game playtest on both loaders, by the owner, against a checklist taken from the 2.0 feature list: minigame, all rods, bait, hooks and charms, tanks (every shape and cosmetic, swarm behaviour, bubbles), quests, shop, encyclopedia, leaderboards, compost, organizer and backups.
- **A6.4 → G2.** Cut the `port/1.20.1` branch from here.

### A7: Release 1.21.1
- **A7.1** Changelog, CurseForge publish (the `publishCurseForge` game-version tags), and a CI workflow on JDK 21.

---

## 7. Dependency track: gelatin-ui

- **G-1.21.1:** bring gelatin-ui `main` (1.21.1, 1.0.16) up to **API parity with 1.0.31** by porting the 34 commits on `26.1.2` that aren't on `main`. The 26.1 migration is part of those 34, so identify it and skip it. What's left is feature work (tabs, animations, containers, particles, …) that needs adapting to 1.21.1's immediate-mode GUI (164 hits on render-state and pipeline APIs in gelatin-ui common). Publish it as `1.0.31+1.21.1` (or whatever D6 decides) to mavenLocal or Maven.
- **G-1.20.1:** a new port from the finished 1.21.1 line. GUI rendering is close between the two (`GuiGraphics` exists since 1.20). Menu opening and networking differ (Forge `NetworkHooks.openScreen`, Fabric `ExtendedScreenHandlerType` with a buf).
- gelatin-ui is a library other mods can use, so its backport lines also help other projects.
- Worktree convention: `D:\GitHub\gelatin-ui-worktrees\mc-1.21.1`, `mc-1.20.1`.

---

## 8. Optional seam work on 26.1.2 (track P2)

Small refactors on the **primary** branch before the backport branches split. Each one shrinks the permanent diff between branches, and so the cost of every future forward-port. Decision D5 is whether to do these.

- **S1: Item data accessor.** Route every `stack.get/set/has(FishtasticDataComponents.X)` and the vanilla `DataComponents.*` reads the mod depends on through the existing helpers (`FishQualityHelper`, `ItemSizeHelper`, …) or a thin `FishtasticItemData` facade. On 1.20.1, only that facade gets an NBT implementation.
  **Done 2026-09-24** (`a697edc8`..`298279e1`, 4 commits). `common/.../FishtasticItemData` is now the only place that calls `ItemStack#get/getOrDefault/has/set/remove`, `applyComponents` or `isSameItemSameComponents` (154 call sites in 47 files, including the shared gametests and the platform tank item models). Its shape:
  - **Fishtastic components:** generic `get/getOrDefault/has/set/remove(stack, FishtasticDataComponents.X)`. Call sites pass the field itself, not `.value()`. For B2.1, the 1.20.1 `FishtasticDataComponents` fields become `ComponentKey<T>` (codec + NBT name) with the same names, and the facade's parameter type changes. Callers don't change.
  - **Vanilla data:** `bundleContents`/`bundleContentsOrEmpty`/`setBundleContents`, `setHeadProfile`, `isTooltipHidden`, `breakSound`, `isSameItemSameData`.
  - **By id and patch:** `hasById`/`encodeById` (item-effect conditions), `id(component)`, `applyPatch`/`patchValue` (quest and shop rewards, tank-shape unlocks).
  - `FishQualityHelper` and `ItemSizeHelper` keep their API and sit on top of the facade.

  **Left in place as inherently version-specific (pass 2 must plan each):**
  1. Component type registration: `FishtasticDataComponents.registerDataComponents`, `IRegistrationApi.registerDataComponent`, the Fabric and NeoForge registration APIs, and the `DATA_COMPONENT_TYPES` DeferredRegister. Plus the `CODEC`/`STREAM_CODEC` on the 9 component records and `HAS_ALERT`'s unit codec.
  2. `Item.Properties.component(...)` defaults in `FishtasticItems`: 26 of them (rod contents, bait/hook/charm effects, and the pile's `BUNDLE_CONTENTS`). On 1.20.1 these become default-NBT (`getDefaultInstance`) or facade fallbacks.
  3. The fish tank BE's implicit-component hooks (`FishTankBlockEntity.collectImplicitComponents`/`applyImplicitComponents`, which take `DataComponentMap.Builder`/`DataComponentGetter`). On 1.20.1 this is `BlockEntityTag`.
  4. Tooltip providers: `addToTooltip(..., DataComponentGetter)` on `ItemSize`, `FishQuality` and `FishTankShape` (the 1.21.5 `TooltipProvider` signature). They're called from `ItemStackMixin`.
  5. Reward data format: `QuestReward.RewardItem` and `ShopEntry.ShopReward` carry a `DataComponentPatch` (field + codec). On 1.20.1 that's an NBT `CompoundTag`, and `applyPatch`/`patchValue` change parameter type with it.
  6. Fabric datagen: `DailyQuestFamily` (patch builder), `FishtasticBlockLootTableProvider` (`CopyComponentsFunction.include`), `FishtasticModelProvider` (`HasComponent` item-model condition).
  7. Client: `FishPileIcons` sets `DataComponents.ITEM_MODEL` (1.21.2+ client items). A5 replaces it.
  8. Facade signatures that name types missing on older versions: `BundleContents` (plus `BundleContents.Mutable`, used directly in 11 files for the pile logic; 1.20.1 needs a small `BundleContents` port), `ResolvableProfile` (`setHeadProfile`, and `PlayerHeadItems.resolvableProfile` for the podium), `TooltipDisplay` (1.21.1 `HIDE_TOOLTIP`, 1.20.1 `HideFlags`), and `BREAK_SOUND` (1.21.5+; older versions use `SoundEvents.ITEM_BREAK`).
- **S2: Packet codec locality.** Each payload keeps its codec definition in one place (it mostly does now), so the 1.20.1 buffer shim swaps one layer.
- **S3: Pin `fishsim` and `tools:tank-shape-gen` to `--release 17`** and replace the handful of Java 21 library calls there. These modules ship in the mod jar and feed datagen. Kept at 17, they are **one shared source across all three versions**: no branch-specific fishsim, and fishsim fixes merge cleanly everywhere.
  **Done 2026-09-24** (`7b8945b5`). `options.release = 17` is set on every JavaCompile task (main and test) in both modules. The toolchains are unchanged (25 and 21), and the class files are major version 61. The compiler found only 8 Java 21 calls, all `List.getFirst()/getLast()`. Behaviour is byte-identical: same test counts (fishsim 163 + 1 skipped, tank-shape-gen 21,955 including the STANDARD gate), and a headless fishsim export (L, 12 fish, seed 42, 3000 ticks) produces identical CSV, PNGs and GIF.
- **S4: Keep rendering logic apart from rendering output.** Where it's cheap (swarm, animation, bubble emission), keep the "compute what to draw" code apart from the "emit vertices" code, so the backport rewrites only the output code.

---

## 9. Phase plan: 1.21.1 → 1.20.1 (track B)

Approach: create `mc-1.20.1` from the `port/1.21.1` branch at G2. Rendering carries over nearly as-is. The work is in the data, networking and loader layers.

### B0: Decisions
- **B0.1** Loader set (D2): Fabric + **Forge 47.x**. NeoForge 1.20.1 (47.1) loads Forge mods, so a separate NeoForge 1.20.1 jar isn't needed.
- **B0.2** Whether to support world upgrades from 1.20.1 to 1.21.1 (NBT → component migration). Vanilla's datafixers won't move custom NBT into custom components. Decided: **not supported** (D4).

### B1: Build scaffolding (gate: an empty mod loads on Fabric and Forge)
- **B1.1** Java 17 toolchain, `JAVA_17` mixins, a Loom line that supports 1.20.1, and the `neoforge/` module becomes `forge/` (Architectury `forge()` platform, `mods.toml` instead of `neoforge.mods.toml`). Fabric API 0.92.x and JEI 15.x.
- **B1.2** Java 17 source audit: about 48 `getFirst`/`getLast`/`reversed`/`removeFirst`/`Math.clamp` sites in common and platform code. S3 is done, so `fishsim` and `tank-shape-gen` need nothing (they are `--release 17` on every branch).

### B2: Item data: components → NBT
- **B2.1** 1.20.1 shim (P5): a `ComponentKey<T>` that pairs a `Codec<T>` with an NBT key under the item's tag, with `get`/`set`/`has`/`remove` helpers that look like the component API. Call sites through S1 stay the same.
- **B2.2** The 9 custom components, plus the vanilla component reads the mod depends on (custom name, lore, damage, profile/player-head, …), each mapped to its 1.20.1 NBT or API equivalent.
- **B2.3** Tooltip providers, item stack equality and stacking (components versus NBT comparison), and stack templates.
- **B2.4** `ItemEffectCondition` component conditions (`ComponentCondition`, `ComponentValueCondition`) read through the shim.

### B3: Networking
- **B3.1** 1.20.1 shim (P5): a `StreamCodec`-shaped `BufCodec<T>` interface with the same combinators the mod uses (`composite`, `list`, `optional`, the primitive codecs). The 23 payload records keep their shape.
- **B3.2** Registrars: Fabric `ServerPlayNetworking`/`ClientPlayNetworking` with `ResourceLocation` channels, and Forge `SimpleChannel`, behind the existing `IPacketContext` abstraction.
- **B3.3** Datapack registry sync (8 registries). Forge `DataPackRegistryEvent.NewRegistry` with a network codec, and Fabric `DynamicRegistries.registerSynced`.

### B4: Data, datagen and resources
- **B4.1** Folder renames back to plural (`recipes`, `loot_tables`, `advancements`, `tags/items`, `tags/blocks`). **Verify** the datapack registry directory layout on each loader: vanilla 1.20.1 doesn't namespace-prefix modded registry directories, Forge adds the prefix, and Fabric API's behaviour needs checking.
- **B4.2** Regenerate recipes, advancements (the pre-1.20.2 format, where criteria triggers deserialize from JSON, not codecs) and loot tables with 1.20.1 datagen.
- **B4.3** `pack.mcmeta` format 15, and fix any lang or model keys that point at 1.21-only vanilla content.

### B5: Remaining API deltas
- **B5.1** `ResourceLocation.fromNamespaceAndPath` → `new ResourceLocation(...)`. `SavedData.Factory` → `computeIfAbsent(load, create, name)`. BE NBT without `HolderLookup.Provider`.
- **B5.2** Rendering: the `VertexConsumer` builder API (1.21 `addVertex`/`setColor` → 1.20.1 `vertex().color()…endVertex()`) in the BER, the outline renderers and the particles. Check shader and `RenderType` state names.
- **B5.3** Loader API differences: Forge events versus NeoForge events (event bus names, `IDynamicBakedModel`, `ModelData`, client setup), Forge `ForgeConfigSpec`, and Forge menu opening.
- **B5.4** ~~Fishing: 1.20.1 hardcoded enchantment helpers (`getFishingLuckBonus(stack)`) and loot context params.~~ *(pass 2: dropped. The tree never calls `EnchantmentHelper`; luck and lure come in through `FishingHook`.)*
- **B5.5** Creative tabs, `Item.Properties` (no `component(...)`), and the `FoodProperties` builder if any fish item uses it.

### B6: Gametests, verification, release (gate G3)
- **B6.1** Gametest harness: Fabric `FabricGameTest`, Forge `@GameTestHolder` + `@PrefixGameTestTemplate`.
- **B6.2** Same green bar as A6.2, the owner playtests on both loaders, and publish.

---

## 10. Decisions for the owner

| ID | Decision | Recommendation |
|---|---|---|
| **D1** | Feature scope: full 2.0.1 parity, or a trimmed backport? | **Decided: full parity on all three versions.** Cut only `mcp/` and the cool-cam compat (promo-video tooling, 26.1.2 only). Everything else, including dev and debug tooling and the podium, ships everywhere. |
| **D2** | Loaders per version | **Decided:** 1.21.1 is **Fabric + NeoForge**, with no Forge. 1.20.1 is **Fabric + Forge**. |
| **D3** | Sequential or parallel | **Decided: sequential**, 1.21.1 then 1.20.1, with the dependency and scaffolding work overlapped (§5). |
| **D4** | Save compatibility across MC versions (1.20.1 → 1.21.1 world upgrades) | **Decided: not supported.** An accepted limitation, to be stated in the release notes. Custom NBT → component migration would need a DFU fixer for our own data. |
| **D5** | Do the seam refactors (S1–S4) on 26.1.2 first? | **Decided: S1 and S3 land on `26.1.2` before porting.** That commit is the A0.3 baseline, and `port/1.21.1` rebases onto it. S2 and S4 when convenient. |
| **D7** | If A-SPIKE shows a faithful GUI quality outline is too costly on 1.21.1: build it anyway, or ship a simplified GUI outline on backports? | **Resolved by the spike: not needed.** The faithful GUI outline costs about 0.05 ms/frame on the shared bake atlas. |
| **D8** | *(pass 2)* Sunset Postcard on the backports: full parity through tickTime mixins + rate sync, or a server-only rate (the sun visibly jumps)? | **Decided 2026-09-24: full parity** (tickTime accumulator mixins + rate-sync payload, A2.8.c). |
| **D9** | *(pass 2)* Land seams S5 (Java 17 calls) and S6 (`FishMoonPhase`, permission helper) on `26.1.2` before track A? | **Decided 2026-09-24: yes.** S5 and S6 land on `26.1.2` before A1; the baseline marker advances past `298279e1`. |
| **D10** | *(pass 2)* One shared gametest harness class for all loaders? | **Decided 2026-09-24: yes** (A6.1, B6.1). |
| **D11** | *(pass 2)* Architectury Loom 1.17 + Gradle 9.5 on the port branches (as potions-plus mc-1.21.1)? | **Decided 2026-09-24: yes.** Fallback Loom 1.11 / Gradle 8.14 only if 1.17 fails. |
| **D6** | Versioning and release cadence | **Decided.** Goal set by the owner: identical gameplay on all three versions, and future features and fixes land on all three **together**. So: the same mod version everywhere (`2.x.y+<mc>`), and releases in lockstep once G3 is reached. Decided: format `2.x.y+<mc>`. |

---

## 11. Risks and open questions

| # | Risk or question | Mitigation |
|---|---|---|
| R1 | **(Mitigated by A-SPIKE, 2026-09-24.)** **The biggest what-if.** The item-effect rendering (quality outlines, pinwheel shader, outline atlas) is built on 26.1-only GPU and GUI abstractions (`RenderPipeline`, `GpuBuffer` UBOs, `GuiItemAtlas`, `GuiRenderer`, the submit pipeline). All 11 migration-checklist hook points are missing in 1.21.1, and the GUI outline's *source texture* (vanilla's GUI item atlas) doesn't exist before 1.21.6. | **A-SPIKE**, run first: combine GUI and world outlines on the Fishtastic-owned bake atlas. Fallback: D7, a degraded GUI outline on backports. |
| R2 | The **tank dynamic model** relies on 26.1 `BlockStateModel` semantics, including the fragment-loader contract and diagonal connections. | The 1.21.1 ancestor had a working `BakedModel` tank (simpler at the time). `docs/fish-tank-rendering.md` lists the per-platform pitfalls. |
| R3 | gelatin-ui catch-up is larger than expected, because the 34 commits include render-state-coupled features. | Start P1 immediately. It's the only item on the critical path from day one. |
| R4 | **Visual regressions** can't be caught headlessly. Swarm feel, render calibration, squash-and-stretch and bubbles were all accepted in game on 26.1.2. | Keep fishsim byte-identical (S3), so behaviour stays the same and only presentation can drift. Use the `:fishsim` headless export and viewer to check behaviour parity. Only presentation needs the owner's in-game check. |
| R5 | ~~Datapack registry folder layout on 1.20.1 differs by loader.~~ | **Retired by pass 2.** Every loader we ship namespace-prefixes modded registry directories (NeoForge 21.1, Forge 47, and Fabric API 0.116 and 0.92), so `data/fishtastic/fishtastic/<registry>/` works unchanged everywhere. |
| R6 | Gametests on 26.1 rely on the 1.21.5 test-instance overhaul, so the harnesses aren't a direct port. | Budget A6.1 and B6.1 as rewrites of the harness. The test *bodies* in `common/src/testmod` mostly survive. |
| R7 | Long-term maintenance: three live branches, and (per D6) every feature and fix must land on all three together. | Seams (§8), a shared fishsim, and data JSON kept version-neutral where possible. Keep a per-branch "ported through `<26.1.2 commit>`" marker so forward-port ranges are explicit. |
| Q5 | Is it worth a shared multi-version layer (for example, only for `data/`, `server/` and `network/`) after both ports exist? | **More important given D6** (lockstep updates). Pass 2 should keep the non-rendering layers as close to identical across branches as it can, so that this stays possible. Decide after G3, once the real diffs are known. |
| Q6 | JEI API drift between 29.x, 19.x and 15.x for the 2 recipe categories. | Small (10 files). Worst case, drop JEI compat on 1.20.1. |
| R8 | *(pass 2)* **Runtime tank baking is racy on 1.21.1.** 26.1 bakes tank geometry on meshing threads. 1.21.1's `ModelBakery` caches are plain `HashMap`s, and loading is lazy. | A5.2 design: resolve all 5,088 fragments in `resolveParents` during reload, and bake at runtime only through `BlockModel#bake` (FaceBakery), with a throwing `ModelBaker`. A 512-tank stress test is in the A5 gate. |
| R9 | *(pass 2)* **The Fabric FRAPI tank model is new code on 1.21.1.** The ancestor only had the NeoForge one, and the spike didn't cover it. | Budgeted as new work (A5.2f). Same data flow as NeoForge. Check under Sodium 0.6's built-in FRAPI in the Iris run. |
| R10 | *(pass 2)* **Sunset Postcard has no clock-rate API on 1.21.1 or 1.20.1.** | A2.8.c: fractional tickTime accumulator on server and client + a rate-sync payload (D8). |

---

## 12. What pass 2 must produce

> **Done 2026-09-24:** [`backport-pass2/`](backport-pass2/README.md). Track A (`track-a-1.21.1.md`, with A5 in `track-a5-rendering-1.21.1.md`), track B (`track-b-1.20.1.md`), and the tracked [`checklist.md`](backport-pass2/checklist.md). Reference sources for all three MC versions are extracted under `D:\GitHub\modding-guide\resources\` (see `README-sources.txt` there).

For each phase (A0–A7, B0–B6, G-1.21.1, G-1.20.1, S1–S4):
1. The exact file list it touches, grouped by the 26.1 API it replaces and the target-version API it lands on, **with the target-version signatures checked against that worktree's decompiled sources**, not from memory.
2. The order within the phase, and which files get temporarily excluded or stubbed to keep the build compiling.
3. The gate check: the commands, the expected test counts, and the datagen diff expectation.
4. For the rendering items (A5.x), a short design note on the target-version approach before any code, in the style of the existing `docs/*-rendering.md`.
5. A tracked checklist, like the potions-plus parity ledger, recording progress and the "ported through" commit per branch.
