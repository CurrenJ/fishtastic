# Backport pass 2: implementation plan

> **Status:** Pass 2, written 2026-09-24 on `port/1.21.1`. Baseline: `26.1.2` @ `33986055` (A0.3, moved forward from `3b8427e4` after seams S5/S6 landed, then from `f096fc8d` after S6c).
> **Parent:** [`../backport-plan.md`](../backport-plan.md) (pass 1: strategy, decisions D1–D7, phase outline).
> Work-item IDs (`A2.3`, `B2.1`, …) are the pass 1 IDs. Pass 2 adds sub-steps (`A2.3.b`) where a phase needs them.

## Files

| File | What it covers |
|---|---|
| [`track-a-1.21.1.md`](track-a-1.21.1.md) | 26.1.2 → 1.21.1: A1–A4, A6, A7, and the gelatin-ui 1.21.1 catch-up (G-1.21.1). File lists, order, stubs, gates. |
| [`track-a5-rendering-1.21.1.md`](track-a5-rendering-1.21.1.md) | A5.1–A5.7: one design note per rendering item, written before any code, in the style of `docs/*-rendering.md`. Reuses the A-SPIKE results. |
| [`track-b-1.20.1.md`](track-b-1.20.1.md) | 1.21.1 → 1.20.1: B0–B6, the S1 "left in place" list, the `ComponentKey<T>` shim (B2.1), the `BufCodec` shim (B3.1), the `BundleContents` port, and G-1.20.1. |
| [`checklist.md`](checklist.md) | The tracked checklist: one line per work item with status and commit, plus the per-branch **"ported through `<26.1.2 commit>`"** marker. Update it in the same commit as the work. |

**Why a directory and not more sections in `backport-plan.md`.** Pass 1 is the strategy (why sequential, what the risks are, what was decided). It's short enough to read in one sitting and it changes rarely. Pass 2 is reference material that a porting session opens next to the code: long per-file tables for one track at a time. Keeping them apart means a track A session loads only track A, the plan stays readable, and the checklist (edited in almost every porting commit) has small, conflict-free diffs of its own. `backport-plan.md` gets a pointer here and the corrections below, nothing else.

## How pass 2 was verified

Every "lands on" signature in the track files was checked against decompiled sources of the target version, not written from memory. The sources are extracted under `D:\GitHub\modding-guide\resources\` (see `README-sources.txt` there):

| Version | Sources | How they were produced |
|---|---|---|
| 26.1.2 | `minecraft-merged-a26c9a9f3c-26.1.2-sources` | pre-existing |
| 1.21.1 | `minecraft-merged-1.21.1-sources`, `neoforge-21.1.209-sources`, `fabric-api-0.116.7+1.21.1-sources` | the spike worktree's Loom `genSources` output (Architectury Loom 1.11, mojmap), the NeoForge maven sources jar, and Fabric API's remapped sources jars |
| 1.20.1 | `minecraft-merged-1.20.1-sources`, `forge-1.20.1-47.4.23-patched-sources`, `fabric-api-0.92.12+1.20.1-sources` | a throwaway Architectury project (Loom 1.11, Forge 47.4.23, Fabric API 0.92.12) run through `genSources`, then deleted |

Two techniques did most of the work:
1. **Import resolution.** Every `net.minecraft.*`, `com.mojang.blaze3d.*`, `net.neoforged.neoforge.*` and `net.fabricmc.fabric.api.*` import in the 26.1.2 tree was resolved against the 1.21.1 sources. The 122 imports that don't resolve are the verified list of classes that are missing or moved on 1.21.1. The track A tables are built from that list, grouped by the API that replaces them.
2. **The migration commit `06329830` read backwards** for the registration, BE, SavedData and item-property patterns, and the 1.21.1 ancestor `44064cc5` plus the spike (`362b5255`) for the immediate-mode rendering hooks.

**Caveats:**
- **Access modifiers.** Loom's merged sources already have Fabric API's transitive access wideners applied, so they can't be trusted for visibility. For example, `CreativeModeTab.Output` shows as `public`. Visibility is checked by the compiler at the A1 and A2 gates instead.
- **NeoForge-patched vanilla.** The NeoForge extension interfaces are in the sources. NeoForge's patches to vanilla classes are not, because the 1.21.1 merged jar is vanilla plus Fabric interface injections. The only place this matters is the `getRenderBoundingBox` extension, and it was checked in NeoForge's own sources.

## What pass 2 changed in pass 1

These go back into `backport-plan.md` as corrections.

**Smaller than pass 1 thought:**
- **`Level#isClientSide()` is a method on 1.21.1 too** (`Level.java:162`, alongside the field). The 60 call sites in 18 files don't change. A2.1's "`isClientSide()` → the field" is dropped.
- **Datapack registry folders are already portable (R5 retired).** Vanilla 1.21.1 and 1.20.1 don't namespace-prefix modded registry directories, but every loader we ship does: NeoForge 21.1 (`CommonHooks.prefixNamespace`), Forge 47 (`ForgeHooks.prefixNamespace`), and Fabric API on both versions (`RegistryLoaderMixin`). So `data/fishtastic/fishtastic/<registry>/` loads unchanged on all five loader/version pairs. B4.1 no longer needs to check this.
- **Networking on 1.21.1 is a rename, not a rework.** The Fabric `PayloadTypeRegistry.clientboundPlay()/serverboundPlay()` calls become `playS2C()/playC2S()`. The NeoForge `PayloadRegistrar` API is the same. The 23 payload records don't change.
- **Registration is nearly unchanged.** `DeferredRegister.register(String, Function<ResourceLocation, …>)`, `DataPackRegistryEvent.NewRegistry.dataPackRegistry(key, codec, netCodec)` and Fabric `DynamicRegistries.registerSynced` all exist with the shapes we call. Only `Item.Properties#setId` (4 files) and the `BlockEntityType` construction change.
- **gelatin-ui's catch-up is mostly not rendering.** Of the 34 commits on `26.1.2` that aren't on `main`, one is the MC migration (skip) and 30 are layout, event, animation and tabs work that doesn't touch the render-state API. Only 3 are render-coupled: posed-player rendering (`d407ee6`), and the hi-res item PIP pipeline plus its fix (`36c7307`, `5ae6aa4`). **The hi-res PIP path can be a no-op on 1.21.1.** It exists because 1.21.6+ draws GUI items into a 16×guiScale atlas and then magnifies them. On 1.21.1 a scaled item is drawn straight through the pose, so it's already sharp.
- **No `pack.mcmeta` exists in the tree.** Both loaders generate one. A3.5 and B4.3's "set the pack format" become checks, not edits.
- **The DataFixerUpper version gap is harmless.** 1.20.1 ships DFU 6.0.8, and the tree uses no DFU-7-only API (`Codec.unit` and `MapCodec.unit` exist in 6.0.8, checked with `javap`). The one behaviour difference: DFU 6's `optionalFieldOf` quietly falls back to the default on a malformed value instead of erroring. That's covered by B6's datagen and gametest bar.
- **The gametest harness can become one class.** Both 1.21.1 loaders consume vanilla's `@GameTest(template, timeoutTicks)`: Fabric through its `fabric-gametest` entrypoint, NeoForge through `RegisterGameTestsEvent.register(Class)`. One annotated class in `common/src/testmod` plus a Fishtastic-owned empty structure replaces both harnesses (1,416 + 701 lines). It also removes an existing drift: the NeoForge harness registers 256 tests, the Fabric harness 263. The 6 missing names are the tank-shape connection tests and one menu test.

**Bigger than pass 1 thought, or new:**
- **N1. The Sunset Postcard charm has no clock-rate API to land on.** `SunsetExtensionHandler` slows the overworld clock with 26.1's `ServerClockManager.setRate`. 1.21.1 and 1.20.1 only have `ServerLevel.tickTime()` (`+1` per tick) and a client that ticks its own day time between `ClientboundSetTimePacket`s. Parity needs a fractional-rate accumulator mixin on **both** `ServerLevel.tickTime` and `ClientLevel.tickTime`, plus a small rate-sync payload so the sun doesn't stutter. Design in track A, A2.8.c. It's small, but pass 1 didn't list it.
- **N2. `MoonPhase` is a 26.1 enum, and `FishProfile` JSON serializes it** (`"phase": "full_moon"`). 1.21.1 and 1.20.1 only have `getMoonPhase()` returning an `int`. We need a Fishtastic-owned `FishMoonPhase` enum with the same serialized names, so the 64 fish-profile JSONs stay byte-identical. Best done as a seam on 26.1.2 first (S6, below).
- **N3. Fabric's tank model on 1.21.1 is new code, not a restore.** The 1.21.1 ancestor only had the NeoForge `IDynamicBakedModel` tank. The Fabric FRAPI tank was never written on 1.21.1 (the spike also left it unverified). A5.2 is budgeted as new work on Fabric.
- **N4. The 26.1 client items that aren't plain models need 1.21.1 replacements beyond BEWLRs:** the `fishing_rod/cast` condition (2 rods), the `has_component fishtastic:has_alert` condition (2 books), and a `local_time` select plus a `special` chest model (`cosmetic_treasure_chest`) become item-property overrides (`ItemProperties.register`) plus a BEWLR. The other 149 client items are plain `minecraft:model` wrappers whose `models/item/*.json` already exist, so they just go away.
- **N5. There are 11 component types, not 9:** the 9 records plus `FISH_TANK_SHAPE` and the unit-typed `HAS_ALERT`. B2 counts are updated to match.
- **N6. Reward data encodes components in JSON.** 94 data files (53 quests, 41 shop entries) carry a `"components": {…}` patch. Only `fishtastic:fish_tank_shape` and `fishtastic:fish_tank_materials` appear. The B2.5 design keeps that JSON byte-identical on 1.20.1 (a `FishtasticItemPatch` codec that routes through the `ComponentKey` codecs) instead of forking the data.
- **N7. The Java 17 audit (B1.2) is ~45 sites in 21 files** *(overcounted: S5 found 27 real sites; see `backport-plan.md` §8 S5)* in common and the platforms: `getFirst()` ×20, `reversed()` ×13, `Math.clamp` ×7, `removeFirst` ×1, and a few `addFirst`/`addLast` calls on `List`. As estimated, but they can't be pre-empted by compiler flag on 26.1.2 (see S5).
- **N8. Refmap trap on the remapping Loom.** The Fabric mixin config names `fishtastic.refmap.json` and the common one has no refmap key. potions-plus hit exactly this on 1.21.1: one refmap silently overwrote the other during shading, and every mixin was dropped in production but not in dev. A1 has to name the refmaps per subproject and keep `useLegacyMixinAp = true`. A1.5 adds a jar-level check to the gate.

**Estimates.** Track A: unchanged overall. A2 is smaller than pass 1 thought (registration, networking, `isClientSide`). A5.2 is bigger (Fabric FRAPI tank is new work, N3). N1 adds about a day. G-1.21.1 is smaller: 3 render-coupled commits, not 164 render-API hits spread across the history. Track B: unchanged, with B4.1 smaller (R5 retired) and B2 slightly bigger (N6).

## New decisions (all four accepted by the owner, 2026-09-24)

| ID | Question | Decision (was the recommendation) |
|---|---|---|
| **D8** | Sunset Postcard on 1.21.1/1.20.1 (N1): implement the tickTime accumulator + rate sync for full parity, or accept a server-only rate (the sun jumps back up to once a second while a charm is active)? | **Full parity** (accumulator on both sides + a `SetDayRatePayload`). About 60 lines plus two small mixins, and D1 says full parity. |
| **D9** | Seams S5/S6 on `26.1.2` before track A starts in earnest? **S5**: replace the ~45 Java-21-only calls in common and platform code with Java 17 equivalents on 26.1.2. **S6**: add `FishMoonPhase` (N2) and a `gamemaster()` permission helper for the 20 `Commands.hasPermission(LEVEL_GAMEMASTERS)` sites on 26.1.2. | **Yes to both.** Each is under an hour on 26.1.2 and removes a permanent per-branch diff under lockstep. Neither changes behaviour. The baseline marker then advances past `3b8427e4` by those commits. |
| **D10** | Gametests: replace both 1.21.1 platform harnesses with one shared annotated class in `common/src/testmod` (A6.1)? | **Yes.** It removes about 2,100 lines of duplicated registration and fixes the 256/263 drift. It also makes B6.1 nearly free, because Forge 47 consumes the same vanilla `@GameTest` annotations (`@GameTestHolder` + `RegisterGameTestsEvent`). |
| **D11** | Loom and Gradle line for `port/1.21.1`: Architectury Loom **1.17** on Gradle **9.5** (what potions-plus mc-1.21.1 runs, and it clears the Iris 1.8.14 Loom ≥ 1.16 requirement from spike finding 6), or Loom 1.11 on Gradle 8.14 (what the spike ran)? | **Loom 1.17 + Gradle 9.5.** It's the configuration already proven on this machine for 1.21.1 with remapping, refmaps and shadowed common. Fall back to 1.11 / 8.14 only if 1.17 misbehaves on the Fabric FRAPI setup. |
