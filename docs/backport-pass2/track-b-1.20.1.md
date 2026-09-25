# Track B: 1.21.1 → 1.20.1 (pass 2)

> Parent: [`README.md`](README.md). Starts from `port/1.21.1` at **G2**. Branch `port/1.20.1`, worktree `D:\GitHub\fishtastic-worktrees\mc-1.20.1`.
> Conventions as in track A. "Lands on" cites `D:\GitHub\modding-guide\resources\minecraft-merged-1.20.1-sources` (MC20), `forge-1.20.1-47.4.23-patched-sources` (FG; vanilla with Forge patches + `net.minecraftforge.*`) and `fabric-api-0.92.12+1.20.1-sources` (FAPI20).
> **Correction (B1 as built):** the Gradle *daemon* needs JDK 21 here, not 17 — Loom's buildscript deps (`unpick`) require it regardless of which Loom line is pinned, matching `port/1.21.1`. `-Dorg.gradle.java.home=<jdk-21>` (`C:\Program Files\Java\jdk-21`). The project's own **toolchain still targets 17** (`build.gradle`'s `JavaLanguageVersion.of(17)` / `--release 17`), so compiled output is Java 17 bytecode either way. Written `gw17` below for continuity with the rest of this doc, but it now means "run gradle for this branch," not "run gradle under a JDK 17 daemon."

**Shape of the delta.** Rendering carries over from 1.21.1 almost unchanged (the `VertexConsumer` builder and a handful of GUI calls). The work is in five layers:
1. item data (components → NBT)
2. networking (payloads → `FriendlyByteBuf` channels)
3. the loader (NeoForge 21.1 → Forge 47)
4. Java 17
5. pre-1.20.2 data formats

S1 and S2 were designed so that layers 1 and 2 are each **one shim** under unchanged call sites.

The same exclusion-list + stub mechanism as track A (`port/excludes.txt`, `common/src/portstub/java`) carries over. The 1.20.1 exclusion seed is much smaller: every rendering file compiles after B5.2's mechanical vertex edits.

---

## B0: Decisions (settled)
- **B0.1** Loaders: Fabric + **Forge 47.4.x** (D2). NeoForge 1.20.1 (47.1) loads Forge jars.
- **B0.2** No world upgrade from 1.20.1 to 1.21.1 (D4). Consequence for B2: the NBT layout under `ComponentKey` is ours to choose, with no compatibility constraint towards 1.21.1 saves.
- **New, D11 for track B:** Loom line. The throwaway source project ran **Architectury Loom 1.11 on Gradle 8.14** against Forge 47.4.23 and Fabric API 0.92.12 successfully (genSources, 2026-09-24; a `genSources` run never remaps a mod dependency like Fabric API, so it didn't hit the bug below). **Decided at B1 (2026-09-25): Loom 1.17-SNAPSHOT / Gradle 9.5, matching `port/1.21.1`.** 1.11 hit a real `:common` SRG-merge ordering bug against Forge, and the older, genuinely-proven-for-Forge `1.7.+` line can't remap current Fabric API builds (its own version numbers trail mainline fabric-loom's). See track-b-1.20.1.md "B1 as built" for detail.

---

## B1: Build scaffolding (gate: an empty mod loads on Fabric and Forge)

| File | 1.21.1 | 1.20.1 |
|---|---|---|
| `gradle.properties` | MC 1.21.1, NeoForge 21.1.209, FAPI 0.116.7, loader 0.17.2, JEI 19.18 | `minecraft_version=1.20.1`, **`forge_version=47.4.23`** (latest 47.x on 2026-09-24; drop `neoforge_version`), `fabric_api_version=0.92.12+1.20.1`, `fabric_loader_version=0.16.14` (what the source project resolved; any ≥0.14.21 works), JEI `15.x` (latest 1.20.1 line; pick the version at B1), `gelatinui_version=1.0.31+1.20.1` (G-1.20.1), `enabled_platforms=fabric,forge` |
| `settings.gradle` | `include 'neoforge'` | `include 'forge'` (rename the module directory `neoforge/` → `forge/`, keeping history with `git mv`), and add `maven { url = 'https://maven.minecraftforge.net/' }` to `pluginManagement` |
| `build.gradle` (root) | toolchain 21, `JAVA_21` | toolchain **17**, `options.release = 17`. The `tools/*` block is unchanged (S3: already release 17). |
| `forge/gradle.properties` | — | `loom.platform=forge` |
| `forge/build.gradle` | `neoForge "net.neoforged:neoforge:…"`, `transformProductionNeoForge` | `forge "net.minecraftforge:forge:1.20.1-47.4.23"`, `architectury { forge() }`, `shadowBundle project(path: ':common', configuration: 'transformProductionForge')`, and `loom { forge { mixinConfig "fishtastic.mixins.json", "fishtastic-forge.mixins.json"; convertAccessWideners = true; extraAccessWideners.add loom.accessWidenerPath.get().asFile.name } }`. Forge has no AW, so Loom converts it to an AT. |
| `forge/src/main/resources/META-INF/mods.toml` | `neoforge.mods.toml` | `mods.toml`: `modLoader="javafml"`, `loaderVersion="[47,)"`, a dependency on `forge` `[47.4,)`, `minecraft` `[1.20.1,1.20.2)`. Mixin configs are listed through `loom.forge.mixinConfig`, not TOML. |
| mixin configs | `JAVA_21` | **`JAVA_17`** in all three. Refmap names as in A1 (`fishtastic-forge-refmap.json` for the platform one). |
| `fabric.mod.json` | `"java": ">=21"`, `"minecraft": "~1.21.1"` | `"java": ">=17"`, `"minecraft": "~1.20.1"` |
| `common/src/main/resources/fishtastic.accesswidener` | `named` | same file. Check each entry against MC20 at compile (the same caveat as track A). |
| `scripts/git-hooks/local-java-home` (untracked) | JDK 21 | JDK 17 |

### B1.2: Java 17 source audit (no-op: done by S5 on 26.1.2)

**Nothing to port.** Seam S5 (`cf643423`) replaced the Java 21-only calls on `26.1.2`, so `port/1.21.1` and this branch inherit Java 17-clean sources. The guard `java17ApiGuard` (`afa6d4dc`, now in `gradle/backport-guards.gradle` since `33986055`) fails the build if any come back. It runs before `:common:test` and every `runGametest`. On 1.20.1 the Java 17 toolchain is the final check. B1.2 only has to confirm that `:common:java17ApiGuard` passes and that the tree compiles with toolchain 17.

What S5 actually found, which corrects pass 2's N7 estimate of ~45 sites in 21 files: 19 `List#getFirst()` → `get(0)`, 7 `Math.clamp` → `Mth.clamp`, and one `SequencedMap` local → `Map` (in `RenderBuffersMixin`). All 13 `reversed()` hits were `Comparator#reversed` (Java 8), and the `addFirst`/`addLast`/`removeFirst` hits were on `Deque`s or MC/Fabric API methods. `BufferSourceAccessor` still declares `SequencedMap`, because Mixin matches the field's exact descriptor. On 1.20.1 the field is `Map<RenderType, BufferBuilder>`, so the B5.2 render rewrite changes that accessor anyway. (Pattern matching for `switch` and record patterns: 0 uses. `Stream#toList` (Java 16) and `HexFormat` (Java 17) are fine.)

**Gate (G-B1):** `gw17 :fishsim:test :tools:tank-shape-gen:test` (163 + 1 skipped, 21,955). `gw17 :fabric:build :forge:build`. The A1-style probe loads under `runServer` on both loaders (Forge in the background). Refmap jar check.

### B1 as built (2026-09-25)

**G-B1 passed**, with three corrections to what B0.3/B1 assumed:

1. **Loom line: 1.17-SNAPSHOT, not 1.11.** B0.3's throwaway-proven "1.11/Gradle 8.14" hit a real bug: `:common`'s `architectury.common(['fabric','forge'])` triggers the SRG+mojmap merge before the Forge-side provider that writes `<mcversion>/forge/mojmap.tsrg2` has run, so `:common`'s Minecraft setup fails (`Right does not exist: .../forge/mojmap.tsrg2`). Pinning `dev.architectury.loom 1.7.+` (Gradle 8.8) — the version real 1.20.1 Forge+Fabric Architectury multiloader templates use — fixed that, but then failed remapping FAPI `0.92.12+1.20.1`: `Mod was built with a newer version of Loom (1.17.20), you are using Loom (1.7.435)`. Architectury-loom's own version numbers (1.7.x, 1.11.x) trail mainline fabric-loom's numbering (FAPI's published jar was remapped by fabric-loom 1.17.20), and Loom's cross-version mod-remap guard (`ArtifactMetadata.validateLoomVersion`) compares raw major.minor numbers without knowing the two are different projects — any architectury-loom below "1.17" always rejects a FAPI jar remapped that recently. **1.17-SNAPSHOT/Gradle 9.5 — the same line as `port/1.21.1` — is what actually works**, once the daemon runs on JDK 21 (see the correction above `B1.2`).
2. **`mods.toml` schema: `mandatory = true/false`, not `type = "required"/"optional"`.** 1.20.1's Forge 47 line predates the newer TOML dependency schema `port/1.21.1`'s (NeoForge) `neoforge.mods.toml` uses. Using `type =` there throws `InvalidModFileException: Missing required field mandatory in dependency (main)` during mod discovery, before any of our own code runs. Confirmed against `Fabricators-of-Create/create-multiloader-addon-template@1.20.1`.
3. **`forge/build.gradle` needs its own `loom.accessWidenerPath`.** Unlike a single-loader project, Forge's `extraAccessWideners.add loom.accessWidenerPath.get().asFile.name` inside `loom.forge {}` throws unless `forge/build.gradle` explicitly sets `loom.accessWidenerPath = project(":common").loom.accessWidenerPath` first (common's own `accessWidenerPath` isn't visible to sibling projects automatically).

Also: `gelatinui_version` in `gradle.properties` is the eventual `1.0.31+1.20.1` (matching G-1.20.1's plan), but that artifact doesn't exist yet — G-1.20.1 hasn't run. The three `modImplementation("io.github.currenj.gelatinui:...")`/`modCompileOnly`/`modTestImplementation` lines are commented out (common, fabric, forge) with a note to restore them once G-1.20.1 publishes; B1's bare probe doesn't reference gelatin. `jei_version` is pinned to `15.20.0.117`, not the newest 1.20.1 JEI build (`15.62.0.216`, checked live): newer JEI builds require `fabricloader >= 0.19.4`, well above what `fabric_loader_version = 0.16.14` (B0's pick) provides — `15.20.0.117` is old enough to be undemanding on both loaders.

The accesswidener (`common/src/main/resources/fishtastic.accesswidener`) is now just the header: all three 1.21.1-era entries (`CreativeModeTab$Output`, `MenuScreens$ScreenConstructor`/`MenuType$MenuSupplier`/its constructor, `RenderType.create`) are already `public` on MC20 (checked against `minecraft-merged-1.20.1-sources`), so none of the widenings are needed pre-B5.

Verified: `:fishsim:test` 164 (163 + 1 skipped), `:tools:tank-shape-gen:test` 21,955, `:fabric:build` and `:forge:build` both green (including `java17ApiGuard`/`idConstructionGuard` on both platforms), both `runServer` probes reach `Done` (Fabric and Forge), both jars' `fishtastic.mixins.json`/platform mixins config have empty `mixins`/`client` lists (nothing compiled yet, as A1) with the correct `refmap` keys injected (`fishtastic-common-refmap.json` on Fabric's copy, `fishtastic-forge-refmap.json` on Forge's platform config) — no refmap files exist yet in either jar, expected until B2 compiles mixins (same as A1.5's note).

---

## B2: Item data: components → NBT

### B2.1: The `ComponentKey<T>` shim

**Goal:** the 154 facade call sites (S1) and every `FishtasticDataComponents.X` field reference compile unchanged.

```java
// common/.../FishtasticDataComponents.java on 1.20.1: same class name, same field names
public static final ComponentKey<ItemSize> ITEM_SIZE = ComponentKey.of("item_size", ItemSize.CODEC);
...
public static final ComponentKey<Unit> HAS_ALERT = ComponentKey.of("has_alert", Codec.unit(Unit.INSTANCE));
public static void registerDataComponents() {} // kept so Fishtastic#init is identical on every branch

// common/.../component/ComponentKey.java (1.20.1 only)
public final class ComponentKey<T> {
    private static final Map<ResourceLocation, ComponentKey<?>> BY_ID = new LinkedHashMap<>();
    final ResourceLocation id;   // fishtastic:<name>, the same id as the 1.21.1 component type
    final String nbtKey;         // id.toString(): "fishtastic:item_size", a root-level key in the stack tag
    final Codec<T> codec;
    ...
}
```

**Semantics that must match 1.21.1 components:**
1. **Prototype defaults** (S1 left-in-place item 2, 26 `Item.Properties.component(...)` defaults in `FishtasticItems`). `get(stack, key)` returns the stored value if the tag has `nbtKey`, **else the item's registered default**, else null. The defaults live in `component/ItemComponentDefaults` (`Map<Item, Map<ComponentKey<?>, Object>>`). `FishtasticItems` records them through a helper that replaces `.component(TYPE, V)` in the `Item.Properties` chain: `props(loc).component(ROD_BAIT_CONTENTS, RodBaitContents.EMPTY)` becomes `defaults(loc, ROD_BAIT_CONTENTS, RodBaitContents.EMPTY)`, and the entries bind to the `Item` after registration. **Rejected:** overriding `getDefaultInstance()`. It only covers creative and `new ItemStack(item)` paths that call it. `/give`, loot and crafting results would miss the defaults, while components apply to every stack.
2. **Normalization.** `set(stack, key, v)` where `v` equals the item's default **removes** the key. That's how 1.21.1's patch drops prototype-equal values, and it keeps stacking and `isSameItemSameData` behaviour identical. `set(…, null)` / `remove` delete the key. If the root tag ends up empty, `stack.setTag(null)`.
3. **Encoding:** `codec.encodeStart(NbtOps.INSTANCE, v)` / `codec.parse(NbtOps.INSTANCE, tag.get(nbtKey))`. Decode failures log once per key and return the default. That matches DFU 6's lenient `optionalFieldOf` behaviour.
4. **Sync:** the stack tag is synced whole on 1.20.1, so `networkSynchronized` has no equivalent and isn't needed.
5. **`HAS_ALERT` (a `Unit` component):** stored as `"fishtastic:has_alert": {}` (Unit's `Codec.unit` encodes to an empty compound). `has` checks key presence.

**The facade on 1.20.1** (`FishtasticItemData`): same method names. The parameter type changes from `Holder<DataComponentType<T>>` to `ComponentKey<T>`, and callers don't notice. Mapping:

| Facade method | 1.21.1 body | 1.20.1 body (lands on) |
|---|---|---|
| `get/getOrDefault/has/set/remove(stack, KEY)` | `stack.get(KEY.value())` … | `KEY.get(stack)` … (`ItemStack#getTag/getOrCreateTag`, MC20 `ItemStack.java:481,485`) |
| `id(KEY)` | `BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(..)` | `KEY.id` |
| `hasById(stack, id)` / `encodeById(stack, id)` | registry lookup of the component type | `ComponentKey.byId(id)`. Data only references `fishtastic:fish_quality` (8 item-effect conditions), so an unknown id logs and returns false/null. |
| `isSameItemSameData(a, b)` | `ItemStack.isSameItemSameComponents` | `ItemStack.isSameItemSameTags` (MC20 `ItemStack.java:429`). Correct because of normalization (2). |
| `applyPatch(stack, patch)` / `patchValue(patch, KEY)` | `DataComponentPatch` | take a `FishtasticItemPatch` (B2.5) |
| `bundleContents / bundleContentsOrEmpty / setBundleContents` | vanilla `BUNDLE_CONTENTS` | `ComponentKey<BundleContents> BUNDLE_CONTENTS = of("bundle_contents", BundleContents.CODEC)` over the ported class (B2.2) |
| `setHeadProfile(stack, profile)` | `PROFILE` ← `ResolvableProfile` | `NbtUtils.writeGameProfile(tag.getCompound("SkullOwner"), gp)` (MC20 `NbtUtils.java:103`; key `SkullBlockEntity.TAG_SKULL_OWNER`, MC20 `SkullBlockEntity.java:23`). A name-only profile is `tag.putString("SkullOwner", name)`, which vanilla resolves through `SkullBlockEntity.updateGameprofile`. The facade parameter becomes a small `HeadProfile` record (name / uuid / `GameProfile`) on **all three** branches, so `client/util/PlayerHeadItems` and `LeaderboardScreen` stop naming `ResolvableProfile`. **Seam candidate (S1b), for 26.1.2 at any time.** |
| `isTooltipHidden(stack)` | `HIDE_TOOLTIP` (1.21.1) | `(stack.getTag().getInt("HideFlags") & 0x7F) == 0x7F` (MC20 `ItemStack.java:103,595`) |
| `breakSound(stack)` | `wrapAsHolder(SoundEvents.ITEM_BREAK)` | the same |

### B2.2: `BundleContents` port

1.20.1 has no `BundleContents`. `BundleItem` keeps an `"Items"` list tag behind private statics. Fishtastic uses `BundleContents` / `.Mutable` in 11 files (`block/FishPileBlock`, `block/FishTankBlock`, `blockentity/ElectricFishOrganizerBlockEntity`, `client/renderer/FishPileBlockItemModel`, `client/renderer/PileOfFishItemModel`, `client/util/FishPileIcons`, `FishtasticItemData`, `FishtasticItems`, `item/PileOfFishItem`, `mixin/SizedItemClickMixin`, `recipe/MarineCompostRecipe`).

**Port 1.21.1's class** (MC `world/item/component/BundleContents.java`, ~190 lines) as `grill24.fishtastic.component.BundleContents`, with the same public surface the tree uses:
- immutable: `EMPTY`, `items()` (`Iterable<ItemStack>`), `itemCopyStream()`, `size()`, `weight()` (`Fraction`), `isEmpty()`, `CODEC` (`ItemStack.CODEC.listOf()`, MC20 `ItemStack.java:80`, stored with `"id"/"Count"/"tag"`)
- `Mutable`: `tryInsert(ItemStack)`, `tryTransfer(Slot, Player)`, `removeOne()`, `toImmutable()`, `clearItems()`
- constructors `new BundleContents(List<ItemStack>)`, `new BundleContents.Mutable(BundleContents)`

Usage counts for the port surface: `isEmpty` 25, `items` 21, `toImmutable` 17, `new Mutable` 16, `tryInsert` 14, `EMPTY` 9, `removeOne` 5, `size` 5, `tryTransfer` 2. The 1.21.1 weight rules (`getWeight(ItemStack)`: bee nests are 64/64, nested bundles 4/64 + contents, otherwise 64/maxStackSize) copy as is. `org.apache.commons.lang3.math.Fraction` is on the 1.20.1 classpath (`BundleItem` uses it). Only the import in the 11 files changes (`net.minecraft.world.item.component.BundleContents` → `grill24.fishtastic.component.BundleContents`). **Seam option:** keep a same-named `grill24.fishtastic.component.BundleContents` on 26.1.2 and 1.21.1 too, as a thin alias. Not recommended: it's a pure import difference, and aliasing a vanilla type adds more confusion than it saves.

### B2.3: Tooltips, equality, stack templates
- **Tooltip providers** (S1 item 4): the 1.21.1 `TooltipProvider#addToTooltip(TooltipContext, Consumer<Component>, TooltipFlag)` doesn't exist on 1.20.1. `ItemSize`, `FishQuality` and `FishTankShape` keep a method named `addToTooltip`, but with parameters `(@Nullable Level, Consumer<Component>, TooltipFlag)`, because `Item.TooltipContext` doesn't exist on 1.20.1 (it arrived in 1.20.5; MC20 `Item.java` has no `TooltipContext`). They drop `implements TooltipProvider`. `mixin/ItemStackMixin` (inject at RETURN of `getTooltipLines(Player, TooltipFlag)`, MC20 `ItemStack.java:580`) calls them. That's the one place that changes.
- `Item#appendHoverText(ItemStack, @Nullable Level, List<Component>, TooltipFlag)` (MC20 `Item.java:264`), in the 6 item files.
- Equality: covered by normalization (B2.1 point 2).

### B2.4: `ItemEffectCondition`s
`itemeffect/condition/ComponentCondition` and `ComponentValueCondition` already go through `FishtasticItemData.hasById/encodeById` (S1), so **no change**. `encodeById` → `KEY.codec.encodeStart(JsonOps.INSTANCE, value)` gives the same JSON the 1.21.1 path produces, so the 14 item-effect JSONs are unchanged.

### B2.5: Reward data format (S1 left-in-place item 5), keeping the JSON byte-identical

`data/QuestReward.RewardItem` and `data/ShopEntry.ShopReward` carry `DataComponentPatch components` encoded as `"components": {"fishtastic:fish_tank_shape": "ornate", "fishtastic:fish_tank_materials": {…}}` in **94 data files** (53 quests, 41 shop entries). Only those two ids appear.

- Introduce `component/FishtasticItemPatch`. On 1.20.1 it's `Map<ComponentKey<?>, Object>` with `CODEC = Codec.unboundedMap(ResourceLocation.CODEC, <passthrough>)`, and each value is decoded with `ComponentKey.byId(id).codec`. An unknown id is a decode **error** (the data is ours, so fail loudly at load). `"!id"` removal entries aren't supported (none are used).
- `applyPatch(stack, patch)` → `patch.forEach((k, v) -> k.set(stack, v))`. `patchValue(patch, KEY)` → `patch.get(KEY)`.
- The field type in the two records changes to `FishtasticItemPatch`, and its `CODEC` / `EMPTY` names are kept.
- **Seam S1c (recommended, D9 family):** add `FishtasticItemPatch` on 26.1.2 as a thin wrapper over `DataComponentPatch`, with the same `CODEC` / `EMPTY` / `applyTo`. Then the two records are identical on all branches, and only the wrapper's body differs.

`fabric:datagen/DailyQuestFamily` builds these patches: it goes through `FishtasticItemPatch.builder()`, so it has the same shape everywhere once S1c exists.

### B2.6: Block entity ↔ item data for the fish tank (S1 left-in-place item 3)

1.21.1 moves `FISH_TANK_MATERIALS` / `FISH_TANK_SHAPE` between the BE and the item through `collectImplicitComponents` / `applyImplicitComponents` plus the `copy_components` loot function. 1.20.1 has none of these. The three flows:

| Flow | 1.21.1 | 1.20.1 (lands on) |
|---|---|---|
| Place (item → BE) | `BlockItem` applies the stack's components to the BE | `FishTankBlock#setPlacedBy(Level, BlockPos, BlockState, LivingEntity, ItemStack)` reads the two `ComponentKey`s and calls the BE's existing `applyFromItem(materials, shape)` (the shared body that `applyImplicitComponents` calls on 1.21.1) |
| Break (BE → drop) | loot `copy_components` from `block_entity` | a **custom loot function `fishtastic:copy_tank_data`** (`LootItemConditionalFunction`) that calls the BE's `writeToItem(stack)` (the shared body behind `collectImplicitComponents`). It's registered with `Registries.LOOT_FUNCTION_TYPE`. **Rejected:** vanilla `copy_nbt` (MC20 `CopyNbtFunction`). It would tie the loot JSON to the BE's NBT key names and the `ComponentKey` NBT layout. |
| Pick block | `getCloneItemStack` + components | `FishTankBlock#getCloneItemStack(BlockGetter, BlockPos, BlockState)` calls `writeToItem` |

The tank loot table JSON differs on 1.20.1 (`fishtastic:copy_tank_data` instead of `minecraft:copy_components`). It's generated by `FishtasticBlockLootTableProvider`, so only the provider line differs (S1 item 6).

### B2.6 as built (2026-09-25)

**Data-flow methods written and verified compiling against MC20's real signatures (`javap`/mapped-sources-checked, not the doc's line citations); the two host files stay excluded.** `FishTankBlock.java` and `FishTankBlockEntity.java` are still full 1.21.1 files (689 and 1111 lines) that import the entire menu/registration/interaction graph — `FishTankBrowserMenu` (blocked on G-1.20.1, gelatin-ui), `grill24.FishtasticRegistries`, `RegistrationApiSided`, `TankGroups`/`TankCapacity`, `QuestTracker`, the full cosmetic-placement `useItemOn` body — none of which is this slice's scope (block/BE tick logic, menu, and the rest of `useItemOn` belong to other subtasks). Un-excluding either file whole was explicitly out of scope per this task's brief; confirmed by inspection that doing so would require a much bigger port than "the data-flow methods."

What changed:
- **`FishTankBlockEntity`**: `collectImplicitComponents`/`applyImplicitComponents` (1.21.1-only `BlockEntity` overrides — `DataComponentMap`/`DataComponentInput` don't exist on MC20, so these had to go, not just get a signature tweak) replaced by two plain methods carrying the same body: `writeToItem(ItemStack stack)` (calls `FishtasticItemData.set(stack, KEY, ...)` for both `FISH_TANK_MATERIALS`/`FISH_TANK_SHAPE`) and `applyFromItem(FishTankMaterials materials, FishTankShape shape)` (sets the three block fields + shape directly, defaulting left to the caller). The now-unused `DataComponentMap` import was removed.
- **`FishTankBlock`**: `getCloneItemStack` retargeted from the 1.21.1 `LevelReader` override to MC20's real `Block#getCloneItemStack(BlockGetter, BlockPos, BlockState)` (confirmed against `D:\GitHub\modding-guide\resources\minecraft-merged-1.20.1-sources\...\Block.java:389`; the `LevelReader` import was unused elsewhere in the file so it was swapped outright rather than added alongside) — body now calls `fishTank.writeToItem(stack)` instead of two direct `FishtasticItemData.set` calls. Added a new `setPlacedBy(Level, BlockPos, BlockState, @Nullable LivingEntity, ItemStack)` override (MC20 `Block.java:362`, matches the doc exactly) that reads both `ComponentKey`s off the placing stack via `FishtasticItemData.getOrDefault` (defaulting to `FishTankMaterials.defaultMaterials()`/`FishTankShape.STANDARD`, same defaults `applyImplicitComponents` used on 1.21.1) and calls `fishTank.applyFromItem(materials, shape)`.
- **New file `loot/CopyTankDataFunction.java`**: a real MC20 `LootItemConditionalFunction` + nested `Serializer` in the pre-1.20.5 Gson style (MC20 has no loot `MapCodec`s yet — confirmed against `LootItemFunctions`/`LimitCount`/`CopyNameFunction` in the mapped sources as shape references), registered as a static field via `Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE, Fishtastic.id("copy_tank_data"), new LootItemFunctionType(new Serializer()))` — this mirrors vanilla's own `LootItemFunctions.register(...)` pattern exactly; modded loot function types are plain `BuiltInRegistries` entries on both Fabric and Forge, no `DeferredRegister` needed. `run()` reads `LootContextParams.BLOCK_ENTITY` (present on MC20, confirmed in `LootContextParams.java:20`), narrows to `FishTankBlockEntity`, and calls `writeToItem`. **Added to `port/excludes.txt`**: it names the real `FishTankBlockEntity`'s `writeToItem` method, and that class's bare portstub (no members) can't satisfy the reference — so this file only compiles once the real `FishTankBlockEntity` replaces its stub. Verified with a throwaway probe: temporarily gave the portstub a no-op `writeToItem(ItemStack)` and un-excluded this file — it compiled clean except for one error that's purely a stub-shape artifact (the portstub is a bare `class FishTankBlockEntity {}`, not `extends BlockEntity`, so the `instanceof FishTankBlockEntity` narrowing on a `BlockEntity` value doesn't type-check against the stub — it will against the real class). Reverted the probe; both changes are back out and `:common:compileJava --rerun` is clean.

What's explicitly **not done**, and why:
- **The loot table JSON** (`minecraft:copy_components` → `fishtastic:copy_tank_data` in `fish_tank.json`) is generated by `FishtasticBlockLootTableProvider`, under `fabric/src/main/java/**` — entirely excluded since B1 (`port/excludes.txt:277`), so the whole Fabric module doesn't compile yet and datagen is unreachable. Not forced; this is B4.2 territory once the Fabric module comes back.
- **Registration wiring**: nothing calls `CopyTankDataFunction`'s implicit registration yet. The static field pattern (vanilla's own approach) still needs *something* to reference the class to force its `<clinit>` to run — normally a mod's init entrypoint. `Fishtastic.java` (the only currently-compiling class outside an excluded package that could plausibly host this) has no `init()`/lifecycle method at all; the real init lifecycle lives in the Fabric/Forge entrypoints, both fully excluded (`fabric/src/main/java/**`, `forge/src/main/java/**`, from B1). This is the same shape of blocker B2.7 found for other registries — B3.2/B5.3 territory (platform registrar/entrypoint wiring), not B2.6 itself.
- **Verified with `:common:compileJava`/`:common:test`, both forced `--rerun`, both green** after every real edit (and after reverting the throwaway probe). `:common:test` count is unchanged (still `ComponentKeyTest` alone) since nothing here un-excluded a test-bearing file.

**Net effect**: B2.6's three data-flow methods are written, correct against verified MC20 signatures, and ready — but, like B2.3's five non-`StormCharmItem` files, nothing actually left `port/excludes.txt` this pass. The real unblock for `FishTankBlock.java`/`FishTankBlockEntity.java` is porting their full menu/registration/interaction bodies (a separate, sizable task), and the loot function additionally needs B4.2 (Fabric datagen) and B3.2/B5.3 (registration wiring) before it does anything at runtime.

### B2.7: Other S1 "left in place" items
- **Item 1, registration:** `FishtasticDataComponents.registerDataComponents()` becomes a no-op. `IRegistrationApi.registerDataComponent` and both platform impls are **deleted** on 1.20.1. `DATA_COMPONENT_TYPES` DeferredRegister: deleted. The `CODEC`s on the 9 records are kept (ComponentKey uses them). The `STREAM_CODEC`s are kept as `BufCodec`s (B3.1). `HAS_ALERT`'s unit codec is `Codec.unit(Unit.INSTANCE)`.
- **Item 6, datagen:**
  - `DailyQuestFamily` (B2.5)
  - `FishtasticBlockLootTableProvider` (B2.6)
  - `FishtasticModelProvider`'s `HasComponent` became item-property overrides in A3, and those carry over. The `fishtastic:has_alert` property function calls `FishtasticItemData.has`.
- **Item 7, `ITEM_MODEL`:** already replaced by the `fishtastic:pile_size` item property in A5.3.
- **Item 8, missing vanilla types:**
  - `BundleContents` (B2.2)
  - `ResolvableProfile` → `HeadProfile` (B2.1)
  - `TooltipDisplay` → `HideFlags` (B2.1)
  - `BREAK_SOUND` → `SoundEvents.ITEM_BREAK` (1.21.1 already)

**Gate (G-B2):** `gw17 :common:compileJava` with B3–B5 rows still excluded, then `gw17 :common:test`. Expect **78**, plus the new `ComponentKeyTest`, which is a unit test of normalization, defaults, round-trip and `HAS_ALERT`.

### B2.1 as built (2026-09-25)

**G-B2 not reached yet — this is a first slice.** `:common:compileJava` is green for a much narrower file set than "everything except B3-B5", and `:common:test` runs green for `ComponentKeyTest` alone (8 cases); the other 9 pre-existing unit tests stay excluded (their production code — `client/`, most of `fishtank/`, `server/` — isn't ported). Three corrections to what B2 assumed:

1. **The `BufCodec`/`BufCodecs` shim (B3.1) had to land now, not at B3.** The 9 item-data component records each declare their `STREAM_CODEC` in the same file as the persistent `CODEC` B2.1 needs, and `net.minecraft.network.codec.StreamCodec`/`ByteBufCodecs` don't exist on 1.20.1 MC jars — so the file won't compile at all until something replaces those types, B3 or not. Landed a scoped `common/.../network/codec/{BufCodec,BufCodecs}.java` with only what those 9 files (+ `BundleContents`, B2.2) use: `composite` (1/2/3-field), `of`, `unit`, `fromCodec` (NBT round-trip through the buffer, wrapped in a throwaway `CompoundTag` key since not every component's codec encodes to a compound), `FLOAT`, `idMapper`. B3 does the full 49-file sed and the registrars; this shim's surface just needs to grow, not change shape.
2. **`util/Ids` (B5.1) had to land now too.** `ComponentKey.of` needs to construct a `fishtastic:<name>` id, and `Ids.of`'s body was still `ResourceLocation.fromNamespaceAndPath` (1.21.1-only). Pulled forward B5.1's one-file fix: `new ResourceLocation(ns, path)` / `new ResourceLocation("minecraft", path)` / `new ResourceLocation(id)`; `tryParse` was already MC20-native.
3. **`FishtasticItems.java` doesn't compile yet, on purpose.** Its `.component(KEY.value(), V)` calls were converted to a `defaults(item, KEY, V)` helper (registers into `ItemComponentDefaults` after the item registers, since properties can't carry component defaults pre-1.20.5) — but the file stays on `port/excludes.txt`. It pulls in the *entire* registration graph (`IRegistrationApi`/`RegistrationApiSided`, every `block/`, most of `item/`, `menu/` (blocked on G-1.20.1's gelatin-ui), `recipe/`), which is B2.6/B2.7/B3.2/B5.3 territory, not B2.1's. It'll come off the exclude list once those land.

**What actually compiles now** (`port/excludes.txt`'s inverse): `FishtasticDataComponents`, `FishtasticItemData` (with a package-private `BUNDLE_CONTENTS` key covering vanilla's missing bundle component, and a `HeadProfile` record replacing `ResolvableProfile`, S1b — 1.20.1-only for now, not propagated to the other branches), the whole `component/` package including the new `ComponentKey`, `ItemComponentDefaults`, `BundleContents` (B2.2, class only — the 11 files that import it are still excluded), `FishtasticItemPatch` (B2.5, class only — `QuestReward` was updated to use it since it's tiny, `ShopEntry` and the 94 JSON files weren't touched yet), `FishTankShape`, `QuestCategory`/`QuestDifficulty`/`QuestReward`, `FishTankShapeUnlocks`, `Fishtastic`/`FishtasticItemTags`/`util.{Ids,MathUtil,Utility}`.

Two portstubs, both citing what they're standing in for and why:
- `common/src/portstub/java/grill24/fishtastic/data/Quest.java` — a 2-field `record Quest(QuestCategory category, QuestReward reward)`, standing in for the real 8-field record. The real one needs `QuestObjective` → `FishProfile` → `FishtasticRegistries` → most of the data-registry graph, well beyond item-data scope; `FishTankShapeUnlocks` only calls `.category()`/`.reward()`.
- `common/src/portstub/java/grill24/fishtastic/FishtasticBlocks.java` — just `CLEAR_STAINED_GLASS` (an `EnumMap` of vanilla `Blocks.GLASS` holders), standing in for the full block registry. `FishTankMaterials#defaultMaterials()` is the only caller in scope.

Also found live: two DFU API deltas that show up as soon as anything calls `DataResult` methods — `DataResult#isError()` doesn't exist on this DFU version (use `result().isEmpty()`), and `Codec.stringResolver` doesn't exist either (`FishTankShape.CODEC` uses `Codec.STRING.xmap(...)` instead, same behavior).

**Next:** either keep widening the compiled set toward the real G-B2 (`ShopEntry`/`DailyQuestFamily` for the rest of B2.5, `ItemStackMixin`/the 6 tooltip files for B2.3, then B2.6/B2.7 and `FishtasticItems`), or move to B3 if a full G-B2 pass is deferred — the doc's ordering assumed B2 would be self-contained, but in practice item-data and "the rest of common" are intertwined enough that they may need to grow together.

### B2.2 as built (2026-09-25)

**Import-site edits done, files still excluded.** Changed `import net.minecraft.world.item.component.BundleContents;` → `import grill24.fishtastic.component.BundleContents;` (and the one fully-qualified `new net.minecraft.world.item.component.BundleContents(...)` construction in the dev-only `client/selftest/RenderSelfTest`) in all 9 call sites: `FishtasticItems`, `blockentity/ElectricFishOrganizerBlockEntity`, `block/FishTankBlock`, `block/FishPileBlock`, `recipe/MarineCompostRecipe`, `item/PileOfFishItem`, `mixin/SizedItemClickMixin`, `client/util/FishPileIcons`, `client/selftest/RenderSelfTest`. (The doc's original count of 11 was off by two: `client/renderer/{FishPileBlockItemModel,PileOfFishItemModel}` only call `FishtasticItemData.bundleContentsOrEmpty(stack).items()` inline and never name the `BundleContents` type, so they need no edit.)

**Compiled-by-inspection, not verified live**: tried un-excluding `client/util/FishPileIcons` alone (the shallowest of the 9) as a probe — it still transitively needs `FishtasticItems` (for `PILE_OF_FISH`) and `blockentity/FishPileBlockEntity` (for `MAX_FISH`), both excluded pending the registration graph. Every one of the 9 files has the same shape: none is compilable standalone, because `BundleContents` was never the blocker — the surrounding block/item/BE/registration classes are. **B2.2 doesn't unblock on its own; it completes only as a side effect of B2.6/B2.7 (and, for `FishtasticItems`, the registration cleanup) un-excluding those files.** Left the import fix in place (harmless while excluded, correct once un-excluded) rather than reverting it.

### B2.7 as built, registration-cleanup slice (2026-09-25)

Followed the handoff's path 1: tried the registration graph first, out of doc order, to see how much of B2.2/B2.3/B2.5's excluded files it would unblock.

**Done:** deleted `registerDataComponent`/`dataComponentTypes` (and the now-unused `DataComponentType`/`UnaryOperator` imports) from `common/.../architectury/IRegistrationApi.java` — the only part of item 1 that lives in `common` (the NeoForge-only deletions on `FishtasticRegistriesNeoForge`/`NeoForgeRegistrationApi`/`FabricRegistrationApi` are real, but those three files are still under the blanket `fabric/src/main/java/**` / `forge/src/main/java/**` exclusion from B1, i.e. B5.3 territory, not reachable or necessary yet). `IRegistrationApi.java` and `RegistrationApiSided.java` (which only depends on it) now compile and are off `port/excludes.txt`.

That surfaced two more symbols `IRegistrationApi` names as types: `FishTankFrameType` (`fishtank/`, 30 lines, self-contained — un-excluded for real, no changes needed) and `FishTankBlockEntity` (`blockentity/`, 1111 lines, `extends BlockEntity implements Container, MenuProvider`, pulls in the tank/menu/item registration graph — real B2.6 territory). Added a one-line portstub (`common/src/portstub/java/grill24/fishtastic/blockentity/FishTankBlockEntity.java`, bare class, no members) since nothing in the currently-compiled set calls anything on it beyond naming the type as `createFishTankBlockEntity`'s return type. **The real file stays on `port/excludes.txt`** (portstub + un-excluding the real file would collide as duplicate classes on the same sourceSet).

`:common:compileJava` and `:common:test` both green with this slice.

**Probed further, reverted:** un-excluded `FishtasticItems.java` next (the doc's predicted payoff file) to see what actually unblocks now that the registration graph compiles. It named 14 missing symbols: `grill24.FishtasticRegistries` (25 lines), `fishtank/CosmeticTransforms` (76 lines), and the entire `item/` package (16 files, 2070 lines total) — `AcuteIapsisItem`, `CopperFishingRod`, `CosmeticCaptureWandItem`, `FishTankBlockItem`, `FishTankCosmeticItem`, `FishTankStructureCosmeticItem`, `FishopediaItem` (153), `FishtasticFishItem` (370), `FishtasticFishingRodItem` (310), `LeaderboardsBookItem`, `ObsidianFishingRod`, `PileOfFishItem` (500), `QuestBookItem`, `StormCharmItem` (219), `TestItem`. Several of those (`FishopediaItem`, `FishtasticFishItem`, `FishtasticFishingRodItem`, `PileOfFishItem`, `QuestBookItem`, `StormCharmItem`) are exactly B2.3's `appendHoverText`/`@Nullable Level` tooltip rewrites and B2.6's tank BE↔item flow — real semantic porting per file, not a mechanical sweep. Re-excluded `FishtasticItems.java` (confirmed the original exclude-list line is byte-identical to before; `:common:compileJava`/`:common:test` back to green) rather than push through item-by-item in this slice.

**Conclusion on path 1 vs path 2:** the registration graph itself (IRegistrationApi/RegistrationApiSided) was cheap and is now real and done. But it doesn't cascade into B2.2/B2.3/B2.5 unblocking "for free" the way the handoff hoped — `FishtasticItems.java` is gated on the `item/` package compiling, and the `item/` package is gated on doing B2.3 and B2.6 as real semantic ports first. **Path 2 is now the correct next step**: work through the 16 `item/` files (and `FishtasticRegistries`, `CosmeticTransforms`) as real ports, smallest/least-coupled first (`TestItem`, `ObsidianFishingRod`, `CopperFishingRod`, `AcuteIapsisItem`, `FishTankBlockItem`, `FishTankCosmeticItem`, `FishTankStructureCosmeticItem`, `CosmeticCaptureWandItem`, `LeaderboardsBookItem` look mechanical or near-mechanical by size; `FishopediaItem`/`QuestBookItem`/`StormCharmItem`/`FishtasticFishingRodItem`/`FishtasticFishItem`/`PileOfFishItem` need the B2.3 tooltip signature change and, for the tank-adjacent ones, B2.6's BE↔item flow), verifying each by compiling with `FishtasticItems.java` still excluded until the graph is ready, same discipline as B2.1/B2.2.

**Path 2, first pass — own-file size is a false proxy for shallowness; check transitive imports first.** `TestItem.java` is 44 lines but pulls in `util/FishingMinigameAnimation` (1302 lines) and `server/FishingMinigameManager` (1249 lines) transitively — not remotely shallow. Checked internal (`grill24.fishtastic.*`) imports before touching a file, not just line count, from then on.

That surfaced a genuinely shallow, vanilla-API-only cluster, un-excluded and verified together (`:common:compileJava`/`:common:test` green, **zero code changes needed** on any of them):
- `fishtank/{CosmeticGridCell,CosmeticStructures,CosmeticTransforms,CosmeticStructure}` (a self-contained sub-graph: grid math, rotation helpers, per-block render transforms, and the multi-block structure record built on them — all vanilla `Codec`/`BlockState`/`Rotation`/`StructureTemplate` API, none of it touched by any 1.20.1 delta this doc tracks)
- `item/{FishTankCosmeticItem,FishTankStructureCosmeticItem}` (thin `Item` subclasses over the above)
- `item/FishTankBlockItem` + `client/tooltip/FishTankMaterialsTooltip` (a second small pair: `Item#getTooltipImage`/`TooltipComponent` are unchanged on MC20, and both already only needed `FishtasticDataComponents`/`FishtasticItemData`/`component/FishTankMaterials`, which B2.1 already compiles)

`CosmeticCaptureWandItem` and `LeaderboardsBookItem` (next by size) stay excluded: the former needs `network/CosmeticCaptureSyncPacket` (a `StreamCodec` payload — B3.1's `BufCodec` shim, not built yet), the latter needs `compat/GelatinOpenMenuCompat` (blocked on G-1.20.1, gelatin-ui's 1.20.1 port, not started). Both are real blockers, not scope creep to chase now.

### B2.3 as built (2026-09-25)

Verified the two MC20 signatures directly against the mapped `minecraft-merged` jar with `javap` rather than trusting the doc's line-number citations blind: `ItemStack#getTooltipLines(Player, TooltipFlag)` and `Item#appendHoverText(ItemStack, Level, List<Component>, TooltipFlag)` (no `@Nullable` in the bytecode signature itself, since annotations on parameters aren't always retained — kept the doc's `@Nullable Level` convention in the port's own overrides since a `null` `Level`/`Player` is a real, exercised case, e.g. JEI's tooltip rendering).

**`mixin/ItemStackMixin`**: rewrote `modifyTooltipLines` to the new `getTooltipLines(Player, TooltipFlag)` inject target and changed all three `addToTooltip` call sites from `tooltipContext` to `level` (`player == null ? null : player.level()`) — this is the wiring the doc's B2.3 section calls out as "the one place that changes," and it now matches `ItemSize`/`FishQuality`/`FishTankShape`'s already-ported `addToTooltip(@Nullable Level, …)` signature (those three were done in an earlier B2.1-era pass but the mixin calling them was never updated to match, so it was silently out of sync — passing a `TooltipContext` where a `Level` was expected — the whole time it sat on `port/excludes.txt`). **Not un-excluded**: `hasFoil`'s `ItemEffectManager.shouldShowEffect` call is unrelated to B2.3 and needs `grill24.FishtasticRegistries` plus three `client/renderer/*` classes, none of which compile yet (B4/B5 rendering territory). Compiled-by-inspection only.

**The 6 item files, checked individually rather than swept as a batch** (the doc's list, carried into the last handoff, turned out to be an approximation):
- **`StormCharmItem`** — genuinely self-contained (zero `grill24.fishtastic` imports at all). The `appendHoverText` signature fix wasn't the only thing wrong: the pre-commit hook's clean build (not the ad-hoc `compileJava` run right after un-excluding it, which came back green only because Gradle's incremental compiler treated the excludes-file edit as a no-op and reused a stale `UP-TO-DATE` result — **a cached compile result is not a gate any more than a cached test count is**) caught two real leftover-from-1.21.1 API calls: `getUseDuration(ItemStack, LivingEntity)` (MC20's `Item#getUseDuration` takes only `ItemStack`) and `ItemStack#consume(int, LivingEntity)` (doesn't exist on MC20; replaced with the `shrink(1)` + `!player.getAbilities().instabuild` check `consume` wraps on 1.21+). Both confirmed against the mapped 1.20.1 jar via `javap` before and after the fix. **Un-excluded and verified for real** on a forced `--rerun` this time: `:common:compileJava` and `:common:test` (78 + `ComponentKeyTest`) both green.
- **`QuestBookItem`**, **`FishopediaItem`** — signature fixed (`FishopediaItem` also needed its tooltip body rewritten off `TooltipContext#registries()`, which doesn't exist on MC20: now `level.registryAccess().registryOrThrow(...)`, with a `level == null` guard replacing the old `registries == null` one, and the iteration switched from `HolderLookup.RegistryLookup#listElementIds()` to plain `Registry#registryKeySet()`, mirroring `QuestBookItem`'s own loop). **Still excluded** — both need `compat/GelatinOpenMenuCompat` (G-1.20.1, gelatin-ui's 1.20.1 port, not started — the same real blocker `LeaderboardsBookItem` already hit), plus network sync packets (B3.1) and `server/FishCatchSavedData`/`PlayerQuestState` (the quest/catch data layer, itself blocked on the full registry graph). **Correction to the last handoff:** these two are not "blocked on B2.3 specifically" — B2.3 was never their binding constraint, G-1.20.1 and the server data layer are. Compiled-by-inspection only.
- **`FishtasticFishItem`**, **`FishtasticFishingRodItem`** — signature fixed, mechanical (no body changes needed beyond the parameter). Both stay excluded: `FishtasticFishItem` still needs `block/{FishTankBlock,FishPileBlock}` (real B2.6 territory — its `use()` override calls their static helpers) plus `grill24.FishtasticRegistries`/`data/{FishMoonPhase,FishProfile}`/`util/{FishQualityHelper,ItemSizeHelper}`; `FishtasticFishingRodItem` needs `tutorial/TutorialManager` (itself blocked on `FishtasticItems` and two network packet classes — B3.1). Compiled-by-inspection only.
- **`PileOfFishItem`** — **doesn't override `appendHoverText` at all**; the doc's file list was wrong on this one (`BundleItem`'s own MC20 tooltip is unaffected by the signature change, so there was nothing to port here). No change made. Stays excluded solely on `block/{FishTankBlock,FishPileBlock}`, same as `FishtasticFishItem` — B2.6, not B2.3.

**Net effect**: `StormCharmItem` is the only one of the six that actually left `port/excludes.txt` this pass. The other five had real signature/body work landed (so they're ready the moment their actual blockers — B2.6, B3.1, G-1.20.1 — clear) but none of those blockers are B2.3 itself. `AcuteIapsisItem` (extends `FishtasticFishItem`) and `CopperFishingRod`/`ObsidianFishingRod` (extend `FishtasticFishingRodItem`) still ride on their parents and remain excluded for the same reason.

**B2.3 is functionally done** as a unit of work (the tooltip-provider signature, the mixin wiring, and every item file that actually needed the `appendHoverText` change all landed) — what's left before those files compile for real is B2.6 (tank BE↔item, for `FishtasticFishItem`/`PileOfFishItem`), B3.1 (networking, for the tutorial/quest/encyclopedia sync path), and G-1.20.1 (gelatin-ui, for the two book items' menu-opening calls).

---

## B3: Networking

### B3.1: The `BufCodec<T>` shim (S2 payoff)

1.20.1 has **no `StreamCodec`, `ByteBufCodecs`, `CustomPacketPayload` or `RegistryFriendlyByteBuf`**: none of them are in MC20. 49 files use `StreamCodec`. The shim reproduces exactly the surface the tree uses (inventoried with grep):

```java
// common/.../network/codec/BufCodec.java (1.20.1 only)
public interface BufCodec<T> {
    T decode(FriendlyByteBuf buf);
    void encode(FriendlyByteBuf buf, T value);
    // instance combinators used by the tree
    <R> BufCodec<R> map(Function<T, R> to, Function<R, T> from);
    <R> BufCodec<R> apply(Function<BufCodec<T>, BufCodec<R>> op); // .apply(BufCodecs.list())
    static <T> BufCodec<T> of(BiConsumer<FriendlyByteBuf, T> enc, Function<FriendlyByteBuf, T> dec); // StreamCodec.of ×2
    static <T> BufCodec<T> unit(T value);                                                              // StreamCodec.unit ×4
    static <T, A, …> BufCodec<T> composite(…);  // StreamCodec.composite ×29: overloads for 1..8 fields (check the widest record at B3; 1.21.1 has up to 6)
}
// common/.../network/codec/BufCodecs.java: mirrors ByteBufCodecs
VAR_INT (22), BOOL (10), FLOAT (5), VAR_LONG (2), INT (2), STRING_UTF8 (1), stringUtf8(max) (2),
list() / list(max) (14), optional(c) (7), map(factory, k, v) (4), collection(factory, c) (1),
idMapper(IntFunction, ToIntFunction) (4), fromCodec(Codec) (8; NBT round trip through buf.writeNbt/readNbt, as 1.21.1 does),
RESOURCE_LOCATION (was Identifier.STREAM_CODEC ×11), ITEM_STACK (ItemStack.STREAM_CODEC ×4; buf.writeItem/readItem),
BLOCK_POS (×4), UUID (UUIDUtil.STREAM_CODEC ×3)
```

- **Call sites:** the S2 locality means every payload and record declares its codec once (`public static final StreamCodec<RegistryFriendlyByteBuf, X> STREAM_CODEC`). On 1.20.1 that line becomes `public static final BufCodec<X> STREAM_CODEC`, the **field name is kept**, and the rest of the line is identical apart from the `ByteBufCodecs` → `BufCodecs` class name. That's a mechanical `sed` over the 49 files (`StreamCodec<RegistryFriendlyByteBuf, ` / `StreamCodec<ByteBuf, ` → `BufCodec<`, `ByteBufCodecs.` → `BufCodecs.`, `StreamCodec.` → `BufCodec.`, `Identifier.STREAM_CODEC`/`ResourceLocation.STREAM_CODEC` → `BufCodecs.RESOURCE_LOCATION`, etc.).
- **Payloads:** `CustomPacketPayload` + `Type<T>` → `network/FishtasticPayload` (1.20.1 only): `interface FishtasticPayload { PayloadType<?> type(); }` with `record PayloadType<T>(ResourceLocation id)`. The 23 payload records keep `TYPE` and `type()` under the same names, so only the imported interface changes.
- **Seam note (Q5):** with the shim in place, the payload records and codecs differ from 1.21.1 only in imports and codec class names. A later shared layer could make them identical.

### B3.2: Registrars

| 1.21.1 | 1.20.1 (lands on) | Files |
|---|---|---|
| FAPI `PayloadTypeRegistry.playS2C/playC2S().register(type, codec)` + `registerGlobalReceiver(type, handler)` | FAPI20 `PacketType.create(id, buf -> codec.decode(buf))` (FAPI20 `PacketType.java:46`) around an adapter record `FabricPayloadPacket<T>(T payload) implements FabricPacket` (`write(buf)` → `codec.encode`, `getType()`; FAPI20 `FabricPacket.java:62-77`). `ServerPlayNetworking.registerGlobalReceiver(PacketType, handler)` / the client equivalent (FAPI20 `ServerPlayNetworking.java:99`, `ClientPlayNetworking.java:90`). `send(player, packet)`. | `fabric:…/architectury/fabric/FabricPacketRegistrar` |
| NF `PayloadRegistrar.playToClient/playToServer` + `IPayloadContext` | Forge `NetworkRegistry.newSimpleChannel(id("main"), () -> "1", "1"::equals, "1"::equals)` (FG `NetworkRegistry.java:102`). Per payload: `channel.messageBuilder(Class<T>, index, NetworkDirection.PLAY_TO_CLIENT/SERVER)` (FG `SimpleChannel.java:149`) `.encoder(codec::encode).decoder(codec::decode).consumerMainThread((msg, ctxSupplier) -> handler.handle(msg, new ForgePacketContext(ctxSupplier.get())))`. Send: `channel.send(PacketDistributor.PLAYER.with(() -> player), msg)` / `sendToServer(msg)` (FG `SimpleChannel.java:85,106`). Payload indices are assigned in `FishtasticPackets` registration order (the order is already fixed there). | `forge:…/architectury/forge/ForgePacketRegistrar` (renamed from NeoForge) |
| `IPacketContext` (Fishtastic abstraction) | unchanged interface. New Forge impl over `NetworkEvent.Context` (`getSender()`, `enqueueWork`). | both platform registrars |

### B3.3: Datapack registry sync (8 registries)
- Forge: `DataPackRegistryEvent.NewRegistry#dataPackRegistry(key, codec, networkCodec)` (FG `DataPackRegistryEvent.java:79`), the same call shape as NeoForge 21.1, so `forge:FishtasticForge` keeps its 7 lines.
- Fabric: `DynamicRegistries.registerSynced(key, codec)` (FAPI20 `DynamicRegistries.java:118`), unchanged.
- **Folder layout: verified, no change** (README, R5 retired). Forge 47 prefixes modded registry directories with the namespace (FG `ForgeHooks.prefixNamespace`), and Fabric 0.92 does the same in `RegistryLoaderMixin.getPath` for registries registered through `DynamicRegistries`. So `data/fishtastic/fishtastic/<registry>/` is correct.

**Gate (G-B3):** `testmod:gametest/PacketRoundTripGameTests` passes on both loaders. It already round-trips every payload through its codec, and on 1.20.1 it exercises `BufCodec`.

---

## B4: Data, datagen and resources

### B4.1: Folder names (plural) and data formats

| 1.21.1 path | 1.20.1 path | How |
|---|---|---|
| `data/fishtastic/recipe/` (74) | `recipes/` | regenerated (B4.2) |
| `data/fishtastic/loot_table/` (39) | `loot_tables/` | regenerated |
| `data/fishtastic/advancement/` (81) | `advancements/` | regenerated |
| `data/fishtastic/tags/item/`, `tags/block/` | `tags/items/`, `tags/blocks/` | regenerated |
| `data/minecraft/tags/item/…` (10 files under `data/minecraft`) | `tags/items/…` | regenerated or moved |
| `data/fishtastic/structure/empty.nbt` (gametest, A6.1) | **`structures/`** | moved |
| `data/fishtastic/fishtastic/<registry>/` (371) | **unchanged** | verified (B3.3) |
| `assets/**` | unchanged, except where B4.3 finds 1.20.2+ references | |

### B4.2: Datagen (Fabric 0.92)

| 1.21.1 | 1.20.1 (lands on) | Files |
|---|---|---|
| `FabricRecipeProvider#buildRecipes(RecipeOutput)` | `buildRecipes(Consumer<FinishedRecipe>)` (FAPI20 `FabricRecipeProvider.java:57`). The builders' `save(exporter)` shape is the same. | `fabric:datagen/FishtasticRecipeProvider` |
| Recipe JSON (1.20.5 format: `"result": {"id","count"}`) | pre-1.20.5: `"result": {"item": …, "count": …}` (the builders write it) | output only |
| Advancement JSON (codec-based, `AdvancementHolder`) | pre-1.20.2: `Advancement.Builder … .save(consumer, id)`. Criteria triggers deserialize from JSON through `createInstance(JsonObject, ContextAwarePredicate, DeserializationContext)`. **The tree has no custom criterion triggers** (grep: 0 `CriterionTrigger` implementations), so only vanilla triggers are involved. | `fabric:datagen/*` that build advancements (via `QuestProvider`, if it emits them; check at B4) |
| `FabricBlockLootTableProvider#generate()` | same (FAPI20 `FabricBlockLootTableProvider.java:60`). `copy_components` → `fishtastic:copy_tank_data` (B2.6). | `fabric:datagen/FishtasticBlockLootTableProvider` |
| `FabricTagProvider#addTags(HolderLookup.Provider)` | same (FAPI20 `FabricTagProvider.java:89`) | tag providers |
| `FabricModelProvider`, `FabricCodecDataProvider`, `FabricDataOutput` | all present in FAPI20 (`datagen/v1/provider/`) | the rest: rename-free |
| `MarineCompostRecipe` serializer (`MapCodec` + `StreamCodec`) | **1.20.1 serializers are JSON/buf based**: `fromJson(ResourceLocation, JsonObject)`, `fromNetwork(ResourceLocation, FriendlyByteBuf)`, `toNetwork(FriendlyByteBuf, T)` (MC20 `RecipeSerializer.java:46-50`). Use `SimpleCraftingRecipeSerializer<>(MarineCompostRecipe::new)`. The recipe takes `(ResourceLocation id, CraftingBookCategory)` (MC20 `CustomRecipe.java:11`), and `assemble(CraftingContainer, RegistryAccess)` / `getResultItem(RegistryAccess)` (MC20 `Recipe.java:15,19`). | `recipe/MarineCompostRecipe`, `recipe/FishtasticRecipeSerializers` |

### B4.3: Resources
- `pack.mcmeta`: none in the tree (loaders synthesize it). Nothing to set.
- **GUI sprites.** 1.20.1 has no GUI sprite atlas (`blitSprite` is 1.20.2+; MC20 `GuiGraphics` has none). Every `blitSprite` (2 in Fishtastic, plus gelatin-ui's) becomes a `blit` of a texture file. Fishtastic's sprites under `textures/gui/sprites/**` move to plain textures with explicit sizes.
- Lang and models: grep for vanilla ids that are new in 1.20.2+ (trial chambers, crafter, `minecraft:item/…` models added after 1.20.1) used as cosmetic or tank materials in datagen and data JSON. Replace or drop them per case, and record each one in the checklist.

**Gate (G-B4):** `gw17 :fabric:runDatagen`, then diff. Expected: the plural renames, and all recipes/advancements/loot tables differ in format. `data/fishtastic/fishtastic/**` and `assets/**/models/block/**` are **byte-identical**.

---

## B5: Remaining API deltas

### B5.1: Core
| 1.21.1 | 1.20.1 (lands on) | Scope |
|---|---|---|
| `util/Ids` bodies (S6c): `ResourceLocation.fromNamespaceAndPath(ns, p)` / `withDefaultNamespace(p)` / `parse(s)` / `tryParse(s)` | Change the 4 bodies in `util/Ids` to the `new ResourceLocation(...)` forms: `new ResourceLocation(ns, p)` / `new ResourceLocation("minecraft", p)` / `new ResourceLocation(s)` (MC20 `ResourceLocation.java:37,45`). `tryParse` exists (`:54`). | `util/Ids` only (1 file). `idConstructionGuard` keeps every other site on `Ids`. |
| `loadAdditional/saveAdditional(CompoundTag, Provider)`, `getUpdateTag(Provider)` | `load(CompoundTag)`, `saveAdditional(CompoundTag)`, `getUpdateTag()` (MC20 `BlockEntity.java:53,56,157`). Codec reads use `NbtOps.INSTANCE` directly (no registry context). | the 6 BE files |
| `SavedData.Factory` + `computeIfAbsent(factory, name)`, `save(tag, provider)` | `computeIfAbsent(FishCatchSavedData::load, FishCatchSavedData::new, name)` (MC20 `DimensionDataStorage.java:38`), `save(CompoundTag)` (MC20 `SavedData.java:15`) | `server/FishCatchSavedData` |
| `Item#use` → `InteractionResultHolder` | same (MC20 `Item.java:135`) | none |
| `useItemOn` + `useWithoutItem` (1.20.5 split) | one `use(BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult)` returning `InteractionResult` (MC20 `BlockBehaviour.java:156`). Merge the two bodies: held-item branch first, then the empty-hand branch. | the 5 block files |
| `getUseDuration(ItemStack, LivingEntity)` | `getUseDuration(ItemStack)` (MC20 `Item.java:253`) | `item/StormCharmItem` |
| `hurtAndBreak(int, ServerLevel, ServerPlayer, Consumer<Item>)` / `(int, LivingEntity, EquipmentSlot)` | `hurtAndBreak(int, T, Consumer<T>)` (MC20 `ItemStack.java:334`) | 4 files |
| `PlayerAdvancements#award(AdvancementHolder, String)` | `award(Advancement, String)` (MC20 `PlayerAdvancements.java:198`) | `mixin/PlayerAdvancementsMixin` |
| `server.reloadableRegistries().getLootTable(ResourceKey)` | `server.getLootData().getLootTable(ResourceLocation)` (MC20 `MinecraftServer.java:1504`, `LootDataResolver.java:25`) | `server/FishingMinigameManager` |
| Data-driven enchantments | **no change needed**: the tree has no `EnchantmentHelper` calls. Luck and lure reach Fishtastic through `FishingHook` fields. (Pass 1's B5.4 was precautionary and is dropped.) | none |
| `CreativeModeTab.builder()` | `CreativeModeTab.builder(Row, int)` (MC20 `CreativeModeTab.java:51`), or Forge/Fabric builders | `FishtasticCreativeTabs` |
| `Item.Properties.component(...)` | removed (B2.1 defaults) | `FishtasticItems` |
| `FishingHookMixin` targets | `FishingHook(Player, Level, int, int)`, `shouldStopFishing`, `catchingFish`, `retrieve` all present (MC20 `FishingHook.java:79,236,278,419`) | descriptors only |
| `ItemStackMixin#getTooltipLines` | `(Player, TooltipFlag)` (MC20 `ItemStack.java:580`) | `mixin/ItemStackMixin` |
| `ServerLevel.tickTime` / `ClientLevel.tickTime` (sunset mixins, A2.8.c) | re-check the descriptors on MC20 | the 2 mixins |

### B5.2: Rendering (small)
- **Vertex builder:** 1.21 `addVertex(pose, x,y,z).setColor(..).setUv(..).setOverlay(..).setLight(..).setNormal(pose, ..)` → 1.20.1 `vertex(pose.pose(), x,y,z).color(..).uv(..).overlayCoords(..).uv2(..).normal(pose.normal(), ..).endVertex()` (MC20 `VertexConsumer.java:18-30,111,116`). **6 sites** in 3 files: `client/renderer/{FishTankBlockEntityRenderer,FishtasticItemOutlineAtlas,FishtasticWorldOutlineRenderer}`, plus `FishtasticGuiOutlineRenderer` (spike).
- `blitSprite` → `blit` (B4.3).
- Shaders: the same JSON programs. Forge `RegisterShadersEvent#registerShader` (FG `RegisterShadersEvent.java:63`) with `new ShaderInstance(provider, ResourceLocation, VertexFormat)` (FG `ShaderInstance.java:107`). Fabric `CoreShaderRegistrationCallback` exists in FAPI20. **Check `#version 150` features** (both 1.20.1 and 1.21.1 require GL 3.2 core).
- Render types, BER, particles: same as 1.21.1. BER culling moves to the BE (`IForgeBlockEntity#getRenderBoundingBox` on Forge; see A5.1).
- `GuiGraphics#renderItem(LivingEntity, Level, ItemStack, int, int, int, int)` for the GUI outline hook: present on MC20 (the public 5-argument overload is at `GuiGraphics.java:469`). Check the private overload's descriptor.

### B5.3: Loader: NeoForge 21.1 → Forge 47

| NeoForge 21.1 | Forge 47 (lands on) | Files |
|---|---|---|
| `DeferredRegister` returning **`DeferredHolder` (a `Holder`)** | `DeferredRegister.register` returns **`RegistryObject<T>`, not a `Holder`**. `getHolder()` returns an `Optional` that's empty until registration (FG `RegistryObject.java:24,479`). `IRegistrationApi` hands out `Holder<T>`, and so do the `FishtasticItems`/`Blocks`/… fields on every branch. **Add `forge:…/RegistryObjectHolder<T> implements Holder<T>`**, delegating every `Holder` method (MC20 `Holder.java:15-35`) to `ro.getHolder().orElseThrow()` lazily. Same public API, no call-site change. | `forge:…/architectury/forge/ForgeRegistrationApi`, `forge:FishtasticRegistriesForge` |
| mod bus via constructor injection `FishtasticNeoForge(IEventBus, ModContainer)` | `FMLJavaModLoadingContext.get().getModEventBus()`. Game bus `MinecraftForge.EVENT_BUS`. `@Mod.EventBusSubscriber(bus = Bus.MOD)`. | `forge:FishtasticForge`, `forge:FishtasticForgeClient`, `forge:command/CommandRegistrationForge` |
| `ModConfigSpec` + `container.registerConfig` | `ForgeConfigSpec` (FG `common/ForgeConfigSpec`) + `ModLoadingContext.get().registerConfig(ModConfig.Type, spec)` (fmlcore; not in the extracted sources, so check at compile) | `forge:FishtasticConfig` |
| `IDynamicBakedModel`, `ModelData`, `ModelProperty` (`neoforge.client.model[.data]`), `IUnbakedGeometry`, `IGeometryLoader`, `ModelEvent.RegisterGeometryLoaders` | the same names under `net.minecraftforge.client.model[.data|.geometry]`, and `ModelEvent.RegisterGeometryLoaders` (all present in FG) | `forge:fishtank/*` (a package rename, plus checking signatures at compile) |
| `IClientItemExtensions` + `RegisterClientExtensionsEvent` | `IClientItemExtensions` via **`Item#initializeClient(Consumer<IClientItemExtensions>)`** (FG `Item.java:410`; there's no registration event on 47), overridden in the item classes | the BEWLR items (A5.3). The override has to live in common item classes, so it goes through an `@ExpectPlatform` hook or a Forge-only subclass. Decide at B5 (the pattern the ancestor used for platform item hooks). |
| `IBlockEntityRendererExtension#getRenderBoundingBox(T)` (on the renderer) | `IForgeBlockEntity#getRenderBoundingBox()` (on the **BE**, FG `IForgeBlockEntity.java:105`) | `forge:blockentity/FishTankBlockEntityForge` |
| `RegisterShadersEvent`, `RegisterParticleProvidersEvent`, `RegisterKeyMappingsEvent`, `RegisterClientTooltipComponentFactoriesEvent`, `EntityRenderersEvent`, `RegisterMenuScreensEvent` | the first five exist in FG (checked). **`RegisterMenuScreensEvent` doesn't exist on Forge 47**: use `MenuScreens.register` in `FMLClientSetupEvent#enqueueWork`. | `forge:FishtasticForgeClient` |
| `RenderGuiEvent` / `RegisterGuiLayersEvent` (HUD) | `RegisterGuiOverlaysEvent` (FG) | `forge:FishtasticForgeClient` |
| `RegisterClientReloadListenersEvent` | same (FG) | |
| Menus with extra data: `IMenuTypeExtension.create`, `player.openMenu(provider, buf -> …)` | `IForgeMenuType.create(IContainerFactory)` (FG `IForgeMenuType.java:17`), `NetworkHooks.openScreen(ServerPlayer, MenuProvider, Consumer<FriendlyByteBuf>)` (FG `NetworkHooks.java:192`). This lives in gelatin-ui's menu registration (G-1.20.1), so Fishtastic's `menu/*` don't change. | gelatin-ui |
| Fabric `ExtendedScreenHandlerType` (codec-based on 1.21.1) | buf-based `ExtendedScreenHandlerType(ExtendedFactory)` (FAPI20 `ExtendedScreenHandlerType.java:71`) | gelatin-ui |
| Gametest registration | B6.1 | |

### B5.4: Fishing
Dropped (see B5.1: no enchantment helper use). `FishingHookMixin` descriptors are covered in B5.1.

### B5.5: Creative tabs and item properties
Covered in B5.1 and B2.1. No `FoodProperties` builder is used by any fish item (grep: no `.food(` in `FishtasticItems`).

---

## B6: Gametests, verification, release (gate G3)

### B6.1: Harness
With D10, the shared `testmod:gametest/FishtasticGameTests` from A6.1 carries over:
- Forge 47: `@GameTestHolder("fishtastic")` + `@PrefixGameTestTemplate(false)` (FG `gametest/`), with `RegisterGameTestsEvent#register(Class)` (FG `RegisterGameTestsEvent.java:42`) on the mod bus. Enable the namespace with `-Dforge.enabledGameTestNamespaces=fishtastic` (FG `ForgeGameTestHooks.java:74`) in the `forge/build.gradle` gametest run.
- Fabric 0.92: the `fabric-gametest` entrypoint (`FabricGameTest` is optional, as on 1.21.1).
- Vanilla `@GameTest` on 1.20.1 has `timeoutTicks`, `template`, `batch`, `rotationSteps`, `required`, `setupTicks`, `attempts`, `requiredSuccesses` (MC20 `GameTest.java`), but **not** `skyAccess`/`manualOnly`, so the shared class must not use them (noted in A6.1).
- Template `fishtastic:empty` → `data/fishtastic/structures/empty.nbt` (plural, B4.1). Check the structure NBT's `DataVersion`: 1.20.1 is 3465. A structure saved on 1.21.1 (3955) must be re-saved or down-versioned. Keep a 1.20.1 copy.

### B6.2: Green bar (G3)
Same shape as G2 (track A, A6.2), with `gw17`, `:forge:runGameTestServer` (backgrounded) and the Forge jar. Expected counts: unit **78 + `ComponentKeyTest`**, fishsim 163 + 1 skipped, tank-shape-gen 21,955, gametests **264 on each loader**. Datagen shows no diff after regeneration. The owner playtests on both loaders against the A6.3 list. Release `2.0.1+1.20.1`, lockstep with the other two (D6).

---

## G-1.20.1: gelatin-ui 1.20.1

Branch `mc/1.20.1` in `D:\GitHub\gelatin-ui-worktrees\mc-1.20.1`, cut from gelatin `mc/1.21.1` once G-G1 passes. Deltas are the same classes as Fishtastic's track B, at gelatin's scale:
- Java 17
- the Forge platform module (replacing NeoForge), with menu registration through `IForgeMenuType` + `NetworkHooks.openScreen`
- Fabric `ExtendedScreenHandlerType` with a buf
- `blitSprite` → `blit` in `SpriteRenderMode`/nine-slice paths (B4.3)
- `PoseStack`-based GUI (same as 1.21.1)
- posed player: `RemotePlayer` exists on MC20. 1.20.1's `SkinManager` has no `PlayerSkin` record (added in 1.20.2). It has `getInsecureSkinLocation(GameProfile)` → `ResourceLocation` and `registerSkins(GameProfile, SkinTextureCallback, boolean)` (MC20 `SkinManager.java:96,134`). Override `AbstractClientPlayer#getSkinTextureLocation()`/`getModelName()` on the puppet instead of `getSkin()`.

Publish `1.0.31+1.20.1`. **Gate:** gelatin builds on JDK 17, its tests pass, and `TestScreen` opens on Fabric and Forge.

---

## Appendix: track B size by bucket

| Bucket | Files | Mechanical? |
|---|---|---|
| `ResourceLocation` construction | 1 (`util/Ids`) | no-op at the call sites: S6c landed on 26.1.2 |
| `StreamCodec` → `BufCodec` | 49 | yes (a sed after the shim exists) |
| Payload interface | 23 payloads + 2 registrars | the payloads are mechanical, the registrars are rewrites |
| `FishtasticDataComponents` + facade + `ComponentKey` | 3 (+1 test) | new code, small |
| `BundleContents` port | 1 new + 11 import changes | mostly mechanical |
| Reward patch (`FishtasticItemPatch`) | 1 new + 2 records + 1 datagen | zero on the records if S1c lands on 26.1.2 |
| Tank BE ↔ item (B2.6) | `FishTankBlock`, `FishTankBlockEntity`, 1 new loot function, the loot provider | new code, small |
| Java 17 | 21 | yes (zero if S5 lands on 26.1.2) |
| BE / SavedData / blocks / items / recipe | ~20 | mostly mechanical |
| Forge platform module | ~25 (the renamed `neoforge/` tree) | rewrites of the event wiring, a package rename for models |
| Datagen | ~6 providers | small |
| Rendering | 4 files (vertex builder) + `blitSprite` sites | mechanical |
