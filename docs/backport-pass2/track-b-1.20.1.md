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

### B2.4: `ItemEffectCondition`s (done, B2.4 commit)
`itemeffect/condition/ComponentCondition` and `ComponentValueCondition` already go through `FishtasticItemData.hasById/encodeById` (S1), so no *semantic* change. `encodeById` → `KEY.codec.encodeStart(JsonOps.INSTANCE, value)` gives the same JSON the 1.21.1 path produces, so the 14 item-effect JSONs are unchanged.

One real compile-time delta the "expected: no change" note missed: `ItemEffectCondition.Codecs.DISPATCH_CODEC` used `Codec.STRING.dispatch("type", getter, Codecs::getConditionCodec)`, where the third argument returns `MapCodec<? extends ItemEffectCondition>`. 1.20.1 pins `com.mojang:datafixerupper:6.0.8` (verified via `javap` on the jar in `~/.gradle/caches`), whose only 3-arg `dispatch(String, Function, Function)` overload requires the codec-function to return `Codec<? extends E>`, not `MapCodec` — the `Function<A, ? extends MapCodec<? extends E>>` overload used on 1.21.1's newer DFU doesn't exist here. Fix: `type -> getConditionCodec(type).codec()` (`MapCodec#codec()` is the same on both DFU versions). All 7 files (`ItemEffect`, `ItemEffectCondition`, and the 5 `itemeffect/condition/*` classes) un-excluded as-is otherwise — `ItemEffectManager` stays excluded (needs `FishtasticRegistries` + the client renderer graph, B2.7/B5.3 territory).

### B2.5: Reward data format (S1 left-in-place item 5), keeping the JSON byte-identical

`data/QuestReward.RewardItem` and `data/ShopEntry.ShopReward` carry `DataComponentPatch components` encoded as `"components": {"fishtastic:fish_tank_shape": "ornate", "fishtastic:fish_tank_materials": {…}}` in **94 data files** (53 quests, 41 shop entries). Only those two ids appear.

- Introduce `component/FishtasticItemPatch`. On 1.20.1 it's `Map<ComponentKey<?>, Object>` with `CODEC = Codec.unboundedMap(ResourceLocation.CODEC, <passthrough>)`, and each value is decoded with `ComponentKey.byId(id).codec`. An unknown id is a decode **error** (the data is ours, so fail loudly at load). `"!id"` removal entries aren't supported (none are used).
- `applyPatch(stack, patch)` → `patch.forEach((k, v) -> k.set(stack, v))`. `patchValue(patch, KEY)` → `patch.get(KEY)`.
- The field type in the two records changes to `FishtasticItemPatch`, and its `CODEC` / `EMPTY` names are kept.
- **Seam S1c (recommended, D9 family):** add `FishtasticItemPatch` on 26.1.2 as a thin wrapper over `DataComponentPatch`, with the same `CODEC` / `EMPTY` / `applyTo`. Then the two records are identical on all branches, and only the wrapper's body differs.

`fabric:datagen/DailyQuestFamily` builds these patches: it goes through `FishtasticItemPatch.builder()`, so it has the same shape everywhere once S1c exists. It lives under `fabric/src/main/java/**`, which is wholesale excluded pending B1's loader wiring — not touchable yet regardless of B2.5's own state.

**`ShopEntry` (2026-09-25):** `ShopReward`'s `components` field switched from `DataComponentPatch` to `FishtasticItemPatch`, same as `QuestReward.RewardItem` — mechanical, matches the design above. Still excluded, though: `ShopEntry` needs `FishtasticRegistries` (for `QUEST_REGISTRY_KEY`), which in turn needs `Quest`, `Temperament`, `FishProfile`, and `FishEncyclopediaEntry` all compiling. `Temperament`'s `MovementParams.STREAM_CODEC` imports `net.minecraft.network.codec.StreamCodec`, which doesn't exist pre-1.20.5 — this is real B3.1 territory (the `BufCodec`/`BufCodecs` shim under `network/codec/`, not `util/StreamCodecs`, is what 1.20.1 code should target instead). So `ShopEntry`'s exclusion doesn't lift until either B3.1 lands or `MovementParams`/`Temperament`/`FishProfile`'s network codecs get a one-off shim swap ahead of the full B3.1 sed pass. Left as a documented blocker rather than reaching into B3.1 mid-B2.5.

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

### B2.6 as built, real port (2026-09-25)

Did the "separate, sizable task" the note above flagged: ported `FishTankBlockEntity.java` (1116 lines) and `FishTankBlock.java` (705 lines) for real against verified MC20 signatures, rather than leaving them on `port/excludes.txt` behind the bare-class BE portstub.

**`util/BlockEntityNbt.java`**: dropped the `HolderLookup.Provider` param from `store`/`read` — MC20's `saveAdditional`/`load` take no provider at all (added 1.20.5), and none of this file's callers' codecs (`ItemStack.CODEC`, `Rotation.CODEC`) need registry context to encode/decode, so both call straight through to `NbtOps.INSTANCE`. All 5 of its callers were already excluded, so this was a free signature change (no other call site to fix).

**`FishTankBlockEntity`**:
- `saveAdditional(CompoundTag, HolderLookup.Provider)`/`loadAdditional(CompoundTag, HolderLookup.Provider)`/`getUpdateTag(HolderLookup.Provider)` → MC20's `saveAdditional(CompoundTag)`/`load(CompoundTag)`/`getUpdateTag()` (confirmed against `BlockEntity.java:53,56,157`, no provider at all pre-1.20.5).
- **Dropped `removeComponentsFromTag`/`saveCustomOnly` entirely** — no MC20 equivalent (that split hook doesn't exist before 1.21.6, so there's no way to distinguish "saving for world persistence" from "saving for item copy" from inside the BE). Verified this is harmless rather than a silent behavior loss: `FishTankBlock#onPlace` unconditionally calls `updateConnections` on every freshly placed tank regardless of what connectivity bits the copied item's `load()` seeded, so a stale ctrl-pick copy's connectivity never survives placement. Documented in place of the deleted override.
- **Deferred `MenuProvider`/`createMenu`/`getDisplayName`**: `FishTankBrowserMenu` extends gelatin-ui's `GelatinMenu`, and G-1.20.1 (gelatin-ui's 1.20.1 port) hasn't reached the menu system yet (G2.1–G2.3 covered scaffolding/sprites/skins only). Rather than portstub a menu class with real slot machinery to fake this, dropped the interface and both overrides — `FishTankBlock`'s empty-hand click (the only caller) becomes a no-op consumed click, documented as pending G-1.20.1.
- **Structure-cosmetic registry resolution moved out of `load()`**: 1.21.1's `loadAdditional(CompoundTag, HolderLookup.Provider)` could resolve each placed structure's `CosmeticStructure` (for its footprint, to build `structureCellIndex`) inline, because it had a `Provider` handed to it. MC20's `load(CompoundTag)` has no such thing, and — confirmed against `LevelChunk.java:483` vs `:384/:553` — `load()` runs inside the static `BlockEntity.loadStatic` factory *before* `setLevel` is ever called, so `this.level` is null there too; there is no registry access available at load time at all. Split the work: `load()` now only parses each entry's raw id + rotation into `structureCosmetics` (needs no registry, just `ResourceKey.create`); a new `setLevel(Level)` override calls a new `rebuildStructureCellIndex()` (uses `level.registryAccess()`, the same `RegistryAccess extends HolderLookup.Provider` pattern `FishTankBlock#placeStructureCosmetic` already used) to resolve every entry and rebuild `structureCellIndex` from scratch once a level is actually known.
- All `BlockEntityNbt.store`/`read` call sites (items, structure-cosmetic rotation) dropped their trailing `registries` argument to match the shim's new signature.

**`FishTankBlock`**:
- Merged `useItemOn`(`ItemStack`, ..., returning `ItemInteractionResult`) + `useWithoutItem` into MC20's single `use(BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult): InteractionResult` (confirmed against `BlockBehaviour.java:156` — no split, no `ItemInteractionResult` type at all before 1.20.5). The held-item body already had its own empty-hand fallback branch (26.1.2's comment: "useItemOn fires even with an empty hand"), so no result-type translation layer (`InteractionResults.forUseItemOn`) was needed — deleted that utility's only call site.
- `onPlace`/`onRemove`/`updateShape` modifiers changed `protected` → `public` (`BlockBehaviour.java:128,145,149` declares all three `public` on MC20; the 1.21.1 tree's `protected` override would reduce visibility and fail to compile).
- `itemStack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand))` → `itemStack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand))`, matching MC20's single `hurtAndBreak(int, T extends LivingEntity, Consumer<T>)` overload (confirmed against `ItemStack.java:334` and the idiom vanilla's own `AxeItem`/`FlintAndSteelItem` use for wax-off/flint-strike, not a guess).
- `player.blockInteractionRange()` (a 1.20.5+ attribute-backed method — no reach exposed on `Player` at all pre-1.20.5) → a small `interactionRange(Player)` helper mirroring vanilla's own pre-attribute hardcoded distances (creative 5.0, survival 4.5).
- **Deferred the three Pile-of-Fish-dependent methods** (`popPileTopIntoTank`, `tryShiftExtractFromTargetedTank`, `extractTopFishIntoHand`) and the `useItemOnWithResult` branch that called the first: `PileOfFishItem` needs `FishtasticItems` (the full item registration graph, B2.7 proper) for `new ItemStack(FishtasticItems.PILE_OF_FISH.value())` inside its own real logic — not a type-only reference a bare portstub could satisfy, so faking it would mean duplicating real gameplay logic into a stub that drifts. Cut cleanly instead: nothing currently compiling called these three methods anyway (their only callers were `PileOfFishItem`/`FishtasticFishItem`'s own `Item#use()` overrides, both still excluded), so removing them lost no reachable behavior. The generic "insert held item as display content" fallback still handles a held Pile of Fish like any other item until B2.7 proper lands.

**Portstub cascade** (all in `common/src/portstub/java`, all documenting what they stand in for and why):
- Deleted `blockentity/FishTankBlockEntity.java`'s old bare-class stub — the real file replaces it now.
- `server/QuestTracker.java`: a no-op `onTankChanged(MinecraftServer, ServerPlayer, FishTankBlockEntity, ItemStack)` — the real 582-line class pulls in the whole quest/registry graph; `FishTankBlock#checkTankQuests` only needs the signature to resolve, and a quest re-check firing zero times is a silent no-op, not a behavior change anyone can observe yet (nothing registers real quests either).
- `FishtasticBlockEntityTypes.java`: only `FISH_TANK` kept (a `Holder<BlockEntityType<?>>` field, uninitialized) — the real file registers all 5 BE types and pulls in their classes plus `FishtasticBlocks`' full graph; `FishTankBlockEntity`'s constructor only names this one field.
- `data/{FishProfile,ShopEntry,Temperament}.java`: bare classes so `grill24.FishtasticRegistries`'s registry-key generics (`ResourceKey<Registry<FishProfile>>` etc.) resolve — except `FishProfile`, which also keeps `DEFAULT_MEAN_SIZE` and the `size()`/`SizeParams` shape (copied verbatim from the real record) for `TankCapacity`'s registry-lookup fallback. That fallback is dead in practice regardless of stub fidelity: nothing registers a real `FishProfile` yet, so the lookup it guards is always empty.

**Un-excluded for real this pass** (all verified together, `:common:compileJava :forge:compileJava :fabric:compileJava :common:test --rerun-tasks`, green): `util/BlockEntityNbt`, `grill24/FishtasticRegistries`, `FishtasticBlockTags`, `data/TankCapacity`, `fishtank/TankGroups`, `util/ItemSizeHelper`, `blockentity/FishTankBlockEntity`, `block/FishTankBlock`, `loot/CopyTankDataFunction` (the last came free once the real BE existed — it only ever needed `FishTankBlockEntity#writeToItem`, and the bare-class portstub's `instanceof FishTankBlockEntity` narrowing against a `BlockEntity` value was the actual blocker the last pass's probe found, not anything loot-specific).

**Still excluded, checked and confirmed not reachable this pass**: `data/{SwarmConfig,Temperament,QuestObjective}` (need the real `FishProfile`); `menu/FishTankBrowserMenu` (needs gelatin-ui, G-1.20.1); `item/PileOfFishItem`/`item/FishtasticFishItem` (need `FishtasticItems`, B2.7 proper); the loot table JSON and registration wiring for `copy_tank_data` (B4.2/B3.2/B5.3, per the previous pass's notes — unchanged).

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

### B2.7 as built, data-cluster leaf slice (2026-09-25)

Re-checked `CosmeticCaptureWandItem`/`CosmeticCaptureSyncPacket` first, on the theory that B3.1 (landed since the last B2.7 pass) might have cleared their blocker. It didn't: `CosmeticCaptureSyncPacket.sendToPlayer`/`sendClear` still construct `new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(...)` directly — the class doesn't exist on 1.20.1 vanilla at all, confirmed as a B3.1 finding already folded into B3.2's scope, not this item's. Both files stay excluded; real unblock is B3.2 (registrar `send`/`sendToServer`), not B2.7.

Went looking for other shallow leaves instead, checking internal (`grill24.*`) imports before touching anything (per the false-proxy lesson above — own-file size undercounts). Found a `data`/`fishtank`/`util` cluster with zero or already-satisfied internal dependencies and **un-excluded 9 files for real** (verified `:common:compileJava :forge:compileJava :fabric:compileJava` and `:common:test`, all green, `--rerun-tasks`):
- `data/EncyclopediaRewardSection`, `data/FishMoonPhase`, `data/FishEncyclopediaEntry` — zero `grill24` imports.
- `util/FishtasticCodecs` — zero `grill24` imports (just `Codec`/`Util`/`Vec2`).
- `data/FishAnimationConfig` — only needs `util/FishtasticCodecs` (above).
- `fishtank/PlacedCosmetic`, `fishtank/TankDiagonal`, `fishtank/TankEdgeDiagonal` — zero `grill24` imports.
- `fishtank/FishTankCompositeModelData` — only needs `FishtasticBlocks.CLEAR_STAINED_GLASS`, already satisfied by the portstub B2.7's registration-cleanup slice added.

**Found and fixed a real DFU 6.0.8 delta**, same shape as B2.4's: `FishAnimationConfig.CODEC` used `Codec.STRING.dispatch("mode", ..., mode -> switch(mode) { case "..." -> Foo.MAP_CODEC; ... })`, but this DFU version's `dispatch` overload needs the switch to return `Codec<? extends E>`, not `MapCodec` — appended `.codec()` to each of the six `MAP_CODEC` arms.

**Checked and confirmed still blocked** (not touched): `data/QuestObjective` needs `FishProfile` (excluded, large); `data/SwarmConfig`/`data/TankCapacity`/`fishtank/TankGroups` all need the real `FishTankBlockEntity` — `TankCapacity` for `CONTAINER_SIZE`, `TankGroups` for `instanceof FishTankBlockEntity` narrowing against a `BlockEntity` value, which doesn't type-check against the bare-class portstub (same issue the registration-cleanup slice already flagged for `CopyTankDataFunction`). `data/Temperament` needs `data/PhaseRule`, itself blocked on `util/FishingTarget` → `FishtasticItems` (the registration graph).

**Net effect**: 9 more files off `port/excludes.txt`, all leaves with no further cascade — none of them was gating anything else in `item/`, `FishtasticItems`, or the `data/` cluster's harder members (`FishProfile`, `Quest`, `ShopEntry`, `Temperament`). The remaining `data/` cluster and `item/` package both still bottom out on the same two blockers path 1 already found: the real `FishTankBlockEntity` (B2.6) and `FishtasticItems`/the full `item/` port (B2.7 path 2 proper).

**Path 2, first pass — own-file size is a false proxy for shallowness; check transitive imports first.** `TestItem.java` is 44 lines but pulls in `util/FishingMinigameAnimation` (1302 lines) and `server/FishingMinigameManager` (1249 lines) transitively — not remotely shallow. Checked internal (`grill24.fishtastic.*`) imports before touching a file, not just line count, from then on.

That surfaced a genuinely shallow, vanilla-API-only cluster, un-excluded and verified together (`:common:compileJava`/`:common:test` green, **zero code changes needed** on any of them):
- `fishtank/{CosmeticGridCell,CosmeticStructures,CosmeticTransforms,CosmeticStructure}` (a self-contained sub-graph: grid math, rotation helpers, per-block render transforms, and the multi-block structure record built on them — all vanilla `Codec`/`BlockState`/`Rotation`/`StructureTemplate` API, none of it touched by any 1.20.1 delta this doc tracks)
- `item/{FishTankCosmeticItem,FishTankStructureCosmeticItem}` (thin `Item` subclasses over the above)
- `item/FishTankBlockItem` + `client/tooltip/FishTankMaterialsTooltip` (a second small pair: `Item#getTooltipImage`/`TooltipComponent` are unchanged on MC20, and both already only needed `FishtasticDataComponents`/`FishtasticItemData`/`component/FishTankMaterials`, which B2.1 already compiles)

`CosmeticCaptureWandItem` and `LeaderboardsBookItem` (next by size) stay excluded: the former needs `network/CosmeticCaptureSyncPacket` (a `StreamCodec` payload — B3.1's `BufCodec` shim, not built yet), the latter needs `compat/GelatinOpenMenuCompat` (blocked on G-1.20.1, gelatin-ui's 1.20.1 port, not started). Both are real blockers, not scope creep to chase now.

### B2.7 re-probe: `FishtasticItems.java` itself (2026-09-25)

Per the last handoff's suggestion, re-probed `FishtasticItems.java` directly (temporarily un-excluded it alone) now that `FishTankBlockEntity`/`FishTankBlock`/`TankGroups`/`TankCapacity` are real. Its own missing-symbol count shrank from the last pass's 14 to **9**: it only needs `item/{CopperFishingRod, ObsidianFishingRod, CosmeticCaptureWandItem, FishopediaItem, FishtasticFishItem, PileOfFishItem, LeaderboardsBookItem, QuestBookItem, TestItem}` — `AcuteIapsisItem` and `FishtasticFishingRodItem` (the other two files on `port/excludes.txt`'s `item/` block) aren't referenced by `FishtasticItems.java` directly (they're superclasses/siblings pulled in transitively once the 9 are un-excluded).

Un-excluded all 9 (plus `FishtasticItems.java` itself) together to size the real fan-out: **31 errors**, all `cannot find symbol`/`package does not exist`, resolving to 17 distinct still-excluded files: `util/{InteractionResults,FishQualityHelper,IGameRendererExtension,FishingMinigameAnimation}`, `block/FishPileBlock`, `data/FishProfile`'s `TimeOfDay`/`WeatherCondition`/`Zone` nested types (the narrow portstub doesn't carry them) and its `Registry<FishProfile>`/`Holder<Item>` use, `tutorial/{TutorialManager,EncyclopediaTutorialManager}`, `client/tooltip/RodGearTooltip`, `network/{CosmeticCaptureSyncPacket,FishEncyclopediaSyncPacket,QuestSyncPacket}`, `client/{FishEncyclopediaClientCache,FishtasticKeyBinds}`, `compat/GelatinOpenMenuCompat`, `server/{FishCatchSavedData,PlayerQuestState}`. Confirms the handoff's own read: still the full registration/gameplay graph, not a shrunk surface — most of these are large (`FishingMinigameAnimation` 1302 lines, `FishCatchSavedData` 699 lines, `PlayerQuestState` 283 lines) or need the real `FishProfile` (big record, still a narrow portstub), not further shallow leaves.

**Found 5 genuinely shallow leaves inside that fan-out and un-excluded them for real** (verified `:common:compileJava :forge:compileJava :fabric:compileJava :common:test`, all green, `--rerun-tasks`): `util/{InteractionResults,IGameRendererExtension,ItemActivationAnimation,FishQualityHelper}`, `compat/{CompatUtil,GelatinOpenMenuCompat}` (6 files — `GelatinOpenMenuCompat` needed its sibling `CompatUtil` un-excluded too, both reflection-only, no gelatin-ui compile-time dependency at all, matching the class's own doc comment). `IGameRendererExtension` only needed its sibling `ItemActivationAnimation` (11 lines, zero `grill24` imports).

**One real delta found and fixed**: `InteractionResults.forUseItemOn`/its `ItemInteractionResult` import referenced a type that doesn't exist pre-1.20.5 at all — no `Block#useItemOn`/`useWithoutItem` split before 1.20.5 (`FishTankBlock#use` already found and documented this exact delta in B2.6; this file just hadn't been touched yet). Deleted the method and the import; kept `forItemUse` (the `InteractionResultHolder<ItemStack>` wrapper), which is still correct pre-1.21.2.

`FishtasticItems.java` and the 9 item files stay excluded — none of the 5 leaves was gating them alone; the rest of the 17-file fan-out (`FishPileBlock`, `FishProfile`'s missing nested types, the tutorial/network/client/server classes) is genuinely the full registration/gameplay graph, B2.7 path 2/3's real remaining scope, not further probing.

### B2.7 whole-graph probe (2026-09-26)

Continuing from the last handoff's "B2.7 vs B1 entrypoint" fork: checked the fabric/forge entrypoint path first (`FishtasticFabric#onInitialize`/the equivalent `FishtasticNeoForge` method, both ~70-120 lines) and it's a dead end for finding a smaller slice — it directly calls `FishtasticItems.registerItems()`/`FishtasticBlocks.registerBlocks()`/`FishtasticBlockEntityTypes.registerBlockEntityTypes()`/`Quest.CODEC`/`ShopEntry.CODEC`/`FishCatchSavedData.getOrCreate()` etc. in one method body, so it's strictly downstream of the exact same wall B2.7 already knows about, not an alternate route around it.

Went back to `FishtasticItems`/`FishtasticBlocks` with two temporary probes (`port/excludes.txt` edits during the probe, reverted after each — no probe commits):

1. **Un-excluded `data/{FishProfile,PhaseRule,Quest,QuestObjective,ShopEntry,SwarmConfig,Temperament}` + `server/PlayerQuestState` together** (the quest/shop/fish-profile data cluster `PlayerQuestState` sits in). Result: `FishProfile` only still needs `FishtasticBiomeTags` (zero grill24 imports of its own — a pure `TagKey<Biome>` holder class, genuinely shallow, just never un-excluded); `PhaseRule`/`Temperament` both resolve to `util/FishingTarget` (`PhaseRule` references `FishingTarget.MovementPattern`, a *nested* enum, so javac needs the whole outer class, not just the constant); `network/RecentCatch` (unrelated to this cluster but caught in the same pass) turned out to have zero remaining blockers either. **`FishtasticBiomeTags` and `RecentCatch` un-excluded and landed for real** this session (verified `:common:compileJava :forge:compileJava :fabric:compileJava :common:test`, `--rerun-tasks`, green) — the rest of the cluster (`FishProfile`, `PhaseRule`, `Quest`, `QuestObjective`, `ShopEntry`, `SwarmConfig`, `Temperament`, `PlayerQuestState`) stays excluded, gated on `util/FishingTarget`.

2. **Un-excluded `FishtasticItems`/`FishtasticBlocks` together** (not just `FishtasticItems` alone, this time) to get an exact, current symbol list rather than reuse the last probe's counts, which predate this session's other un-excludes and could be stale. Real, narrower numbers:
   - `FishtasticItems.java` itself: **5** missing item classes, not 9 — `item/{CopperFishingRod, ObsidianFishingRod, CosmeticCaptureWandItem, FishtasticFishItem, TestItem}`. (`FishopediaItem`/`LeaderboardsBookItem`/`PileOfFishItem`/`QuestBookItem` are needed by *other* call sites already un-excluded elsewhere, not by `FishtasticItems.java`'s own registration calls — the last probe's 9-file count conflated "referenced somewhere in the un-excluded batch" with "referenced by this file.")
   - `FishtasticBlocks.java` itself: **4** missing block classes — `block/{ElectricFishOrganizerBlock, FishPileBlock, FishTankAssemblyBlock, MarineCompostBlock}` — plus one real MC20 API delta: `Block.Properties.ofFullCopy(Block)` (used for glass/stained-glass/`FishTankBlock` properties) doesn't exist on 1.20.1's `BlockBehaviour.Properties` — that's a 1.20.2+ addition. Not fixed this session (needs the 1.20.1 replacement verified against decompiled sources — likely `Properties.copy(Block)`, unconfirmed); tracked as new B5-territory scope, not B2.7's.

   **Traced one more layer to check whether any of those 9 item + 4 block classes is individually shallower than the whole graph — none are.** `CopperFishingRod`/`ObsidianFishingRod` have zero `grill24` imports of their own, but both extend `FishtasticFishingRodItem`, which imports `tutorial/TutorialManager`, which imports `FishtasticItems` right back — so the SCC (strongly-connected component) includes even the "shallow-looking" leaves. Every other one of the 9+4 pulls in `FishCatchSavedData`, `PlayerQuestState`, `FishingTarget`, `FishingMinigameAnimation` (1302 lines), `tutorial/*`, several `network/*`/`client/*` files, or `blockentity/{ElectricFishOrganizerBlockEntity,FishTankAssemblyBlockEntity,MarineCompostBlockEntity,FishPileBlockEntity}` — all of which loop back to `FishtasticItems`/`FishtasticBlocks` themselves through at least one path (e.g. `blockentity/ElectricFishOrganizerBlockEntity` imports `item/FishtasticFishItem` and `item/PileOfFishItem` directly).

**Conclusion, replacing the last handoff's "17 distinct files" estimate**: there is no sub-slice of the remaining B2.7 gameplay graph smaller than *all of it* — `FishtasticItems` + `FishtasticBlocks` + item×9 + block×4 + `blockentity/{ElectricFishOrganizerBlockEntity,FishTankAssemblyBlockEntity,MarineCompostBlockEntity,FishPileBlockEntity}` + `menu/{ElectricFishOrganizerMenu,FishTankAssemblyMenu}` + `data/{FishProfile,PhaseRule,Quest,QuestObjective,ShopEntry,SwarmConfig,Temperament}` + `util/FishingTarget` + `util/FishingMinigameAnimation` + `server/{FishCatchSavedData,PlayerQuestState}` + `tutorial/{TutorialManager,EncyclopediaTutorialManager}` + several `network/*` (`FishEncyclopediaSyncPacket`, `QuestSyncPacket`, `StartFishingMinigamePacket`, `RequestFishEncyclopediaPacket`, `RequestLeaderboardScreenPacket`, `RequestQuestLogPacket`, `LeaderboardEntry`) + `client/{FishtasticKeyBinds,FishEncyclopediaClientCache,TutorialClientHandler,tooltip/RodGearTooltip}` — roughly 30 files, several in the 300-1300+ line range — has to compile together in one pass before any of it can leave `port/excludes.txt` for real. Not attempted this session: landing it responsibly (each file's MC20/Forge47 API deltas verified against decompiled sources or `javap`, not assumed from the 1.21.1 design doc) is a dedicated session's worth of work, not a chip-away increment. `compat/GelatinOpenMenuCompat`, `command/CosmeticCaptureSession`, and `component/BundleContents` are **not** blockers in this graph — all three already compile for real (verified in `port/excludes.txt`: the first two are `#`-commented, the third was never listed).

### B2.7 whole-graph landed for real (2026-09-26)

Landed the graph the probe above identified, by iteratively un-excluding real leaf files and following the compiler's actual errors (never grep/inspection) until `:common:compileJava` was clean, then verifying `:forge:compileJava :fabric:compileJava :common:test` — all green, `--rerun-tasks`. The graph grew to roughly 50 files as it was actually compiled, not the ~30 the last probe estimated — every previously-invisible fan-out (`PhysicsSimulation`, `MarineCompostPhase`, `client/renderer/*` shader files, `client/effects/*`, `client/util/*`, `FishtasticPackets` and 7 more payload classes, `FishingMinigameManager`, `QuestTracker`, `CleanupGoalTracker`, `FishingXpAward`, `FishEncyclopediaScreen`, `QuestLogScreen`, `LeaderboardScreen`) turned out to be real, load-bearing members of the same SCC.

**Real MC20 API deltas found and fixed, beyond the `ofFullCopy`/`ItemInteractionResult` ones prior sessions already knew about:**
- `Block#playerWillDestroy` is `void` on MC20, not the 1.21.1 `BlockState`-returning override (`FishPileBlock`, `MarineCompostBlock`); `BlockBehaviour#spawnAfterBreak` is `public`, not `protected` — narrowing it is illegal. Both are silently *absorbed* by javac's default 100-error cap when compiling a large batch with other, earlier errors present — a repeat of B2.3's "a cached compile result is not a gate" lesson, this time for error-count truncation rather than incremental caching: always re-check a shrinking error count is real by getting to zero, not by trusting an early low number.
- `BlockEntity#load`/`saveAdditional`/`getUpdateTag`/`saveWithoutMetadata` take no `HolderLookup.Provider` on MC20 (added 1.20.5); `BlockEntityNbt.store`/`read` already had no-registries overloads (from B2.6) but 4 blockentity files were still calling the old registries-taking ones.
- `SavedData` has no nested `Factory` record pre-1.20.5; `DimensionDataStorage#computeIfAbsent(Function<CompoundTag,T>, Supplier<T>, String)` is the real 3-arg overload, and `SavedData#save`/`load` take no registries param either.
- `AdvancementHolder` doesn't exist pre-1.20.3; `ServerAdvancementManager#get` is `getAdvancement`, returns `Advancement` directly (not wrapped), and `PlayerAdvancements` lives in `net.minecraft.server`, not `net.minecraft.advancements`.
- No `ItemInteractionResult`/`useItemOn`+`useWithoutItem` split on MC20 (same finding B2.6/B2.7 already made for `FishTankBlock`/`FishPileBlock`) — also hit in `ElectricFishOrganizerBlock`, `FishTankAssemblyBlock`, `MarineCompostBlock`, all of which only overrode `useWithoutItem`; merged each into a single `use()`.
- `Player#calculateViewVector`/`#blockInteractionRange()` don't exist pre-1.20.5 (same finding as `FishTankBlock#interactionRange`, B2.6) — hit again in `FishPileBlock` and in `FishTankBlock#tryShiftExtractFromTargetedTank` (ported fresh this session from the `port/1.21.1` branch, since it was still a stub/deferred no-op here).
- `net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket`/`ServerboundCustomPayloadPacket` don't exist on MC20 vanilla (B3.1/B3.2's finding) — 10 more client-side send call sites hit this (`FishtasticKeyBinds`, `TutorialClientHandler`, `QuestLogScreen` ×5, `FishEncyclopediaScreen`, `EncyclopediaTutorialClientHandler`, `FishingMinigameClientHandler`), all routed through `NetworkApiSided.sendToServer`.
- `Minecraft#getTimer()`/`Timer#getGameTimeDeltaPartialTick` don't exist pre-1.21 (the `DeltaTracker` split); `TutorialClientHandler#renderScreenOverlay` was working around a 1.21.1-only quirk that doesn't exist on MC20 — its own `partialTick` param is already the 0-1 interpolation fraction, so the workaround was deleted outright.
- `item/component/ResolvableProfile` doesn't exist pre-1.20.5; `PlayerHeadItems` rebuilt around the existing `component/HeadProfile` shim (B2.1) for stack building, and a plain `GameProfile` for gelatin's posed-player `.profile(GameProfile)` call (matching G2.3's "`.profile(ResolvableProfile)` → `.profile(GameProfile)`" scope cut) — `resolvableProfile()` renamed `gameProfile()`, call sites in `LeaderboardScreen` updated too.
- `com.mojang.serialization.JavaOps` doesn't exist on DFU 6.0.8 (`FishCatchSavedData`'s `CleanupGoalState` deep-copy helper) — round-trips through `NbtOps.INSTANCE` instead, matching the rest of the file's own pattern; `DataResult#getOrThrow` is the 2-arg `(boolean, Consumer<String>)` overload on this DFU version, not the 1-arg `Function<String,RuntimeException>` one — switched to `resultOrPartial().orElseThrow()`.
- `MinecraftServer#reloadableRegistries()` and `Registries.LOOT_TABLE`-keyed lookup don't exist pre-1.20.5; MC20's loot data access is `server.getLootData().getElement(new LootDataId<>(LootDataType.TABLE, id))`. `ServerPlayer#registryAccess()` doesn't exist either — `player.level().registryAccess()` instead (same delta `FishingMinigameManager` already used correctly for its other 5 call sites). `ItemStack#hurtAndBreak` is the 3-arg `(int, T, Consumer<T>)` overload, not 1.21.1's 4-arg one with an extra `ServerLevel`.
- `ItemStack.OPTIONAL_STREAM_CODEC` doesn't exist on MC20 — `QuestSyncPacket`'s `baitDepletedItem` field just uses the plain `BufCodecs.ITEM_STACK` codec instead, since `FriendlyByteBuf#writeItem`/`readItem` already round-trip `ItemStack.EMPTY` correctly (no separate "absent" encoding needed).

**One file didn't exist at all and had to be ported fresh, not just fixed:** `client/FreeformContainer.java` (a small, engine-agnostic gelatin-ui container subclass with zero MC-version-specific code) — copied verbatim from `port/1.21.1`, confirmed its `UIContainer`/`IRenderContext`/`UIEvent` deps are all present in the published `gelatinui-common-1.0.33+1.20.1` jar.

**Also fixed, purely mechanical:** two payload files (`CompleteQuestPacket`, `PurchaseShopEntryPacket`) were missing a `BufCodecs` import their `STREAM_CODEC` needed (pre-existing bug in already-written-but-excluded code, not a new port issue).

**Net effect:** `port/excludes.txt` gained ~50 more real (non-`#`) files; deleted 6 of the 7 portstub files this graph made obsolete (`FishtasticBlockEntityTypes`, `FishtasticBlocks`, `data/{FishProfile,Quest,ShopEntry,Temperament}`, `server/QuestTracker`) — none stayed necessary, since the whole graph they stood in for is now real. `common/src/test/java/grill24/fishtastic/{client,fishtank,server}/**` and `fabric/`/`forge`/`testmod/**` remain excluded — B1's platform entrypoints and B6.1's gametest harness wiring, both untouched by this session, are the next real blockers for those.

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

### B3.1 as built (2026-09-25)

The shim landed wider than the pulled-forward B2 subset: `BufCodec.composite` now goes to 9 fields (absorbing `util/StreamCodecs`' 7-9 field helper, which is deleted — its 2 call sites use `BufCodec.composite` directly), plus `apply` (for `FOO.apply(BufCodecs.list())`), and `BufCodecs` gained `BOOL`, `INT`, `VAR_LONG`, `STRING_UTF8`/`stringUtf8(max)`, `RESOURCE_LOCATION`, `ITEM_STACK`, `BLOCK_POS`, `UUID`, `optional`, `list()`/`list(max)` (max isn't enforced — nothing in the tree needs the cap check), `collection`, and a 2-arg `map` (plus the existing `idMapper`/`fromCodec`). `FishtasticPayload`/`PayloadType<T>` landed per the design above.

All 41 files with `StreamCodec`/`ByteBufCodecs`/`CustomPacketPayload`/`RegistryFriendlyByteBuf` in common were converted by a scripted sed (not hand-edited one by one), covering: the field/type renames, `CustomPacketPayload.Type<X>` → `FishtasticPayload.PayloadType<X>`, `implements CustomPacketPayload` → `implements FishtasticPayload`, and the `type()` override's return type. Two files needed hand fixes after the sed: `data/PhaseRule.java`'s hand-written `encode`/`decode` took `FriendlyByteBuf` (a `StreamCodec<FriendlyByteBuf, …>` in the 1.21.1 source), which doesn't satisfy `BufCodec.of`'s `BiConsumer<ByteBuf, T>`/`Function<ByteBuf, T>` shape (`FriendlyByteBuf` is a subtype, not equal — method references need an exact match); rewrapped to take `ByteBuf` and construct a local `FriendlyByteBuf` internally, matching `BufCodecs`' own style. `recipe/MarineCompostRecipe.java`'s `STREAM_CODEC` used `CraftingBookCategory.STREAM_CODEC.<RegistryFriendlyByteBuf>cast()`, which has no 1.20.1 equivalent; replaced with `BufCodecs.idMapper` over `CraftingBookCategory.values()` — a stopgap, since B4.2 says this whole recipe moves to a JSON/buf `RecipeSerializer` (`fromNetwork`/`toNetwork`) where `STREAM_CODEC` won't exist at all, so this field is disposable, not final.

**4 files came off `port/excludes.txt` for real** (verified `:common:compileJava`/`:forge:compileJava`/`:fabric:compileJava`/`:common:test` all green, `--rerun-tasks`): `blockentity/OrganizerSortMode`, `fishtank/TankEntryKind`, `command/CosmeticCaptureSession`, `data/MovementParams` — all had only vanilla-API dependencies once their `STREAM_CODEC` used the shim. `data/PhaseRule` stays excluded: it needs `util/FishingTarget`, which needs `FishtasticItems` (the full registration graph, B2.7's territory) — confirms the handoff's read that the `data/` cluster's *codec* blocker was B3.1, but several of its files have a second, deeper blocker that B3.1 alone doesn't clear.

**Sanity check on the other ~34 converted-but-still-excluded files:** temporarily un-excluded the whole batch (network/tutorial/server/recipe files) and ran `:common:compileJava` to confirm the sed didn't introduce any codec-shaped errors of its own. It didn't — all 71 resulting errors are pre-existing blockers: the `FishtasticRegistries`/`data/*`/`server/*`/`FishtasticItems` graph (expected, B2.7's territory), `recipe/MarineCompostRecipe` importing `net.minecraft.world.item.crafting.CraftingInput` (doesn't exist on MC20 vanilla, which uses `CraftingContainer` — real B4.2 scope, not caught by the original inventory), and **a new finding**: 10 files (`CosmeticCaptureSyncPacket`, `EncyclopediaTutorialSyncPacket`, `FishEncyclopediaSyncPacket`, `NotificationVolumeSyncPacket`, `QuestSyncPacket`, `RequestLeaderboardPacket`, `SetDayRatePacket`, `TankWaterFillSyncPacket`, `TutorialSyncPacket`, `server/FishingMinigameManager`) construct `new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(packet)` directly to send server→client payloads — that whole package doesn't exist on 1.20.1 vanilla, so every one of those sends has to go through the B3.2 registrar's `send` method instead. Folded into B3.2's scope; reverted the temporary un-exclusion (these files stay on `port/excludes.txt`).

### B3.2: Registrars

| 1.21.1 | 1.20.1 (lands on) | Files |
|---|---|---|
| FAPI `PayloadTypeRegistry.playS2C/playC2S().register(type, codec)` + `registerGlobalReceiver(type, handler)` | FAPI20 `PacketType.create(id, buf -> codec.decode(buf))` (FAPI20 `PacketType.java:46`) around an adapter record `FabricPayloadPacket<T>(T payload) implements FabricPacket` (`write(buf)` → `codec.encode`, `getType()`; FAPI20 `FabricPacket.java:62-77`). `ServerPlayNetworking.registerGlobalReceiver(PacketType, handler)` / the client equivalent (FAPI20 `ServerPlayNetworking.java:99`, `ClientPlayNetworking.java:90`). `send(player, packet)`. | `fabric:…/architectury/fabric/FabricPacketRegistrar` |
| NF `PayloadRegistrar.playToClient/playToServer` + `IPayloadContext` | Forge `NetworkRegistry.newSimpleChannel(id("main"), () -> "1", "1"::equals, "1"::equals)` (FG `NetworkRegistry.java:102`). Per payload: `channel.messageBuilder(Class<T>, index, NetworkDirection.PLAY_TO_CLIENT/SERVER)` (FG `SimpleChannel.java:149`) `.encoder(codec::encode).decoder(codec::decode).consumerMainThread((msg, ctxSupplier) -> handler.handle(msg, new ForgePacketContext(ctxSupplier.get())))`. Send: `channel.send(PacketDistributor.PLAYER.with(() -> player), msg)` / `sendToServer(msg)` (FG `SimpleChannel.java:85,106`). Payload indices are assigned in `FishtasticPackets` registration order (the order is already fixed there). | `forge:…/architectury/forge/ForgePacketRegistrar` (renamed from NeoForge) |
| `IPacketContext` (Fishtastic abstraction) | unchanged interface. New Forge impl over `NetworkEvent.Context` (`getSender()`, `enqueueWork`). | both platform registrars |

### B3.2 as built (2026-09-25)

**The send-side gap first.** B3.1 found 10 files sending server↔client by constructing `new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(payload)` directly — that whole package doesn't exist on 1.20.1 vanilla, so every one of those sends needed a real per-platform send call. Added `network/NetworkApiSided` (common): a `@ExpectPlatform` `sendToPlayer`/`sendToServer`/`broadcast`, taking a raw `BufCodec` parameter rather than a generic one — same reasoning `FishtasticPackets#registerServerToClientCodecs` already documents (a lambda/`@ExpectPlatform`-injected call site can't bind a type parameter the way a normal generic call can). Fixed all 10 call sites (`SetDayRatePacket`, `TankWaterFillSyncPacket`, `NotificationVolumeSyncPacket`, `CosmeticCaptureSyncPacket` ×2 methods, `EncyclopediaTutorialSyncPacket`, `TutorialSyncPacket`, `FishEncyclopediaSyncPacket`, `QuestSyncPacket`, `RequestLeaderboardPacket`, plus `server/FishingMinigameManager`'s private `sendToPlayer` helper) to call `NetworkApiSided` with their own `STREAM_CODEC` instead.

**A second real blocker, found while trying to un-exclude the now-fixed files: `FishtasticPackets.IPacketContext`/`IPacketHandler`/`IPacketRegistrar` lived as nested interfaces on `FishtasticPackets` itself**, which registers all 23 payload types and so needs the full registration graph before it compiles — meaning every payload's `handleServerToClient(T, FishtasticPackets.IPacketContext)` signature was silently pinned to the same blocker regardless of the payload's own dependencies. Split the three interfaces out into a new `network/FishtasticPacketHandling` (no payload-class references at all), and mechanically retargeted all 24 payload files' `FishtasticPackets.IPacketContext`/`IPacketHandler`/`IPacketRegistrar` references to `FishtasticPacketHandling.*` via a scripted sed. This is a real, if small, structural change — not a rename for its own sake: it's what actually lets a shallow payload compile independently of the registration graph.

**Threaded `Class<T> payloadClass` through `IPacketRegistrar`'s two methods.** Forge 47's `SimpleChannel` (unlike Fabric's id-keyed `PacketType` or NeoForge's newer payload-interface-based `PayloadRegistrar`) dispatches outgoing messages by the send-side object's own runtime `Class` (`IndexedMessageCodec` keys its encoder/decoder table by `Class<MSG>`, confirmed reading `SimpleChannel.java`/`NetworkRegistry.java` in `forge-1.20.1-47.4.23-patched-sources`), so each payload's concrete record class has to be registered explicitly — it can't be recovered from the erased `T` at registration time the way Fabric's id-based lookup can. Rather than touch all 23 payload files (which don't have a natural place to declare their own class), added the parameter to `FishtasticPackets`' two `register*Packets` methods instead — the only place that already writes each payload's literal class name (`FinishFishingMinigamePacket.class`, etc.) — a one-file, ~23-site mechanical edit.

**Verified against real decompiled/`javap`'d 1.20.1 sources, not memory or the 1.21.1/NeoForge design table alone:**
- Fabric: `javap`'d the real `fabric-networking-api-v1-1.3.15+0caff8d077` jar (the version FAPI `0.92.12+1.20.1`'s POM actually pins). Confirmed: no `PayloadTypeRegistry` at all (1.20.5+ only) — registration is `ServerPlayNetworking.registerGlobalReceiver(PacketType<T>, PlayPacketHandler<T>)`/the client equivalent, where `PlayPacketHandler.receive(T, ServerPlayer/LocalPlayer, PacketSender)` runs **on the network thread** (no `IPayloadContext`-style object to enqueue through) — `FabricServerPacketContext`/`FabricClientPacketContext#enqueueWork` jump to the main thread themselves (`player.server.execute`/`Minecraft.getInstance().execute`).
- Forge: read `SimpleChannel.java`/`NetworkRegistry.java`/`PacketDistributor.java`/`NetworkEvent.java` directly from `forge-1.20.1-47.4.23-patched-sources`. Confirmed: no `PayloadRegistrar`/`IPayloadContext` at all (those are NeoForge, a different fork's API, not this Forge line) — the real shape is `NetworkRegistry.newSimpleChannel(id, versionSupplier, clientPredicate, serverPredicate)` then `channel.messageBuilder(Class<MSG>, index, NetworkDirection).encoder(...).decoder(...).consumerMainThread(...).add()`. `consumerMainThread` (unlike `consumerNetworkThread`) already enqueues the whole consumer onto the main thread and calls `setPacketHandled(true)` before it runs — so `ForgePacketContext#enqueueWork` is a no-op `runnable.run()`, not a real enqueue. `context.getSender()` is `@Nullable ServerPlayer`, server-side only; the client path falls back to `Minecraft.getInstance().player`. **Deleted the old `forge/…/architectury/neoforge/NeoForgePacketRegistrar.java`** (used NeoForge's `RegisterPayloadHandlersEvent`/`PayloadRegistrar`/`IPayloadContext`, none of which exist on this Forge line at all — confirmed dead code for this port, not just unported) and created `forge/…/architectury/forge/ForgePacketRegistrar` fresh, per the design table's already-planned rename. Left `architectury/neoforge/{NeoForgeRegistrationApi,RegistrationApiSidedImpl}` (the *registration* API, unrelated to packets) where they are — renaming those is B5.3/registration-cleanup territory, not B3.2's.

**A generics trap, caught only by test-compiling, not by reasoning about it:** the natural design for Fabric's `PacketType` cache (`Map<ResourceLocation, PacketType<FabricPayloadPacket<?>>>`, populated via `computeIfAbsent`) doesn't compile — `computeIfAbsent`'s lambda target-types its return against the map's *value* type (`FabricPayloadPacket<?>`), which fights `PacketType.create`'s own inference of `P` from the constructor call inside the lambda (`FabricPayloadPacket<T>`, a concrete captured `T`, not `?`) — a wildcard-vs-type-variable mismatch. Switched the map and the packet-wrapping calls to raw types throughout (`Map<ResourceLocation, PacketType>`, raw `new FabricPayloadPacket(...)`), the same trade `FishtasticPackets#registerServerToClientCodecs` already makes for the same underlying reason (a generic method's injected/lambda call sites can't always bind a type parameter the normal way).

**Verification, since `fabric/src/main/java/**`/`forge/src/main/java/**` are still wholly excluded (B1) and can't be verified by a real `:fabric:`/`:forge:compileJava`:** temporarily lifted each module's wildcard exclude and individually excluded every *other* file in that module (so only the new registrar + its `NetworkApiSidedImpl` would attempt to compile), and temporarily stripped the two `FishtasticPackets.register*Packets(registrar)` calls from each `register()`/`registerServerReceiver()` method (the only remaining reference to the still-fully-excluded `FishtasticPackets`, unrelated to what needed checking). Both isolated probes came back `BUILD SUCCESSFUL` against the real FAPI/Forge jars — confirms every signature above and the raw-type fix compile for real, not just by inspection. Reverted both probes (excludes.txt and the two registrar files) back to the real, non-stripped versions before committing; neither probe state was ever committed.

**Net effect**: `network/NetworkApiSided`, `network/FishtasticPacketHandling`, and both platform registrar + `NetworkApiSidedImpl` pairs exist and are real, compile-checked code (fabric/forge via the isolated-probe method above, not a full module build — that still needs B1's entrypoint work, B5.3 territory). 6 payload files (`SetDayRatePacket`, `TankWaterFillSyncPacket`, `NotificationVolumeSyncPacket`, `CosmeticCaptureSyncPacket`, `EncyclopediaTutorialSyncPacket`, `TutorialSyncPacket`) and 2 supporting enums (`TutorialStep`, `EncyclopediaTutorialStep`) came off `port/excludes.txt` for real. `FishtasticPackets.java` itself, `FishEncyclopediaSyncPacket`, `QuestSyncPacket`, and `RequestLeaderboardPacket` stay excluded — the first needs all 23 payload classes real, the latter three need `server/{FishCatchSavedData,PlayerQuestState}` (B2.7's registration/gameplay-graph territory, unrelated to B3.2). `FabricPacketRegistrar`/`ForgePacketRegistrar` themselves stay excluded regardless of payload progress — real unblock is B1's platform entrypoint scaffolding (currently a 100%-excluded wildcard on both `fabric/src/main/java/**`/`forge/src/main/java/**`), B5.3's stated territory.

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

### B5.1/B5.2/B4.3 as built (2026-09-26)

All three rows' scope lives entirely in `common/src/main/java`, which came fully off `port/excludes.txt` this session (the last 9 files: `FishTankGeometry`, `CosmeticStructureItemModel`, `FishtasticItemRenderers`, `RenderSelfTest`, `CosmeticCommand`, `FishtasticCommand`, `FishtasticJeiPlugin` + its 2 `RecipeCategory` dependents). Each row's planned deltas above checked out against the real jars, plus two the plan didn't anticipate:

- `BlockModel#bake` needed a 4th `ResourceLocation` arg on MC20 (`FishTankGeometry`) — the plan's B5.2 vertex-builder/shader entries didn't call this one out, since it's a model-baking signature, not a vertex or shader one.
- `PoseStack#mulPose(Quaternionf)` vs `#mulPoseMatrix(Matrix4f)`: `CosmeticStructureItemModel` was calling the quaternion overload with a `Matrix4f` (`part.localTransform()`); switched to `mulPoseMatrix`.
- `WorldOpenFlows#createFreshLevel` is 4-arg on MC20 (no trailing `Screen`); `RenderSelfTest`'s self-test world creation dropped the extra `new TitleScreen()` argument.
- `SystemToast.SystemToastId` is `SystemToast.SystemToastIds` (plural) on MC20; `RenderSelfTest`'s two toast-checking call sites fixed.
- The pile-block `CUSTOM_MODEL_DATA` marker in `RenderSelfTest`'s self-test scene was still writing the 1.21.1 component (`net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA`); switched to the same pre-1.20.5 NBT-tag marker `client/util/FishPileIcons` already uses (`getOrCreateTag().putInt("CustomModelData", …)`).
- `CosmeticCommand`: `File#resolve` doesn't exist (`getServerDirectory().toPath().resolve(...)`), `Inventory#contains` has no predicate overload (replaced with a manual `getContainerSize()`/`getItem(i)` loop), and `Player#blockInteractionRange()` doesn't exist pre-1.20.5 — added a local `interactionRange(ServerPlayer)` helper mirroring `FishTankBlock`'s own (creative 5.0 / survival 4.5), same finding B2.6/B2.7 already made twice.
- `FishtasticJeiPlugin`: JEI 15.20.0.117's `IGuiProperties` uses `getScreenClass()`/`getGuiLeft()`/etc. (the `get`-prefixed names), not 1.21.1's unprefixed `screenClass()`/`guiLeft()`/etc. `MarineCompostRecipe`'s constructor needs a `ResourceLocation id` first arg (`Fishtastic.id("jei_marine_compost")`, since JEI's representative instance isn't looked up from `RecipeManager`).

Verified `:common:compileJava :forge:compileJava :fabric:compileJava :common:test`, `--rerun-tasks`, all green. `common/src/main/java` is now empty in `port/excludes.txt`; everything remaining there is `common/src/test/java/**`, `fabric/src/main/java/**`/`forge/src/main/java/**`, and the 3 `testmod/**` trees (B1's platform entrypoints, B3.3, B4.1/B4.2, B5.3, B6.1).

### B1 fabric real port (2026-09-26)

Picked up the handoff's suggested next step: `fabric/src/main/java/**` was still a blanket
`port/excludes.txt` wildcard even though the files under it (28 of them) already existed,
ported forward from `port/1.21.1`'s Fabric module and never touched for MC20/FAPI 0.92 deltas.
Un-excluded the line, ran a real `:fabric:compileJava --rerun-tasks`, and fixed every error against
the real jars (never by inspection) until it went green:

- **`FabricRegistrationApi`** (`architectury/fabric/`): `registerDataComponent`/`dataComponentTypes`
  deleted to match B2.7's `IRegistrationApi` cleanup (MC20 has no `DataComponentType` at all) — the
  Fabric implementation had never been updated to match.
- **`DailyQuestFamily`** (fabric datagen): `net.minecraft.core.component.DataComponentPatch` doesn't
  exist on MC20 — switched to B2.5's `FishtasticItemPatch` facade (`.value()` → the bare
  `ComponentKey` field, since it isn't a `Holder`).
- **`FishtasticBlockLootTableProvider`**: `FabricBlockLootTableProvider`'s ctor is 1-arg
  (`FabricDataOutput`) on FAPI 0.92, not `(output, registriesFuture)`. `CopyComponentsFunction`
  doesn't exist pre-1.20.5 — routed through B2.6's `CopyTankDataFunction` instead (added a
  `copyTankData()` builder factory wrapping the protected `simpleBuilder`, the datagen entry point
  that function never needed until now).
- **`FishtasticItemTagProvider`**: `ItemTags.FISHING_ENCHANTABLE`/`DURABILITY_ENCHANTABLE` don't
  exist pre-1.20.5 (the enchantment-overhaul tags) — dropped, nothing to add them to. DFU 6.0.8's
  `DataResult#getOrThrow` is `(boolean, Consumer<String>)`, not the later 1-arg
  `Function<String,RuntimeException>` — matched the pattern `FishCatchBackups` already uses.
- **`FishtasticRecipeProvider`**: MC20 has no `RecipeOutput` at all — `buildRecipes` and every
  private helper take `Consumer<FinishedRecipe>` (`RecipeBuilder`'s `save(Consumer)`/
  `save(Consumer, String)` defaults cover every `.save(output)`/`.save(output, name)` call
  unchanged). `FabricRecipeProvider`'s ctor is also 1-arg. The Marine Compost special recipe used
  1.21.1's `RecipeOutput#accept(id, recipe, advancement)`; rebuilt as
  `SpecialRecipeBuilder.special(serializer).save(output, "fishtastic:marine_compost")`, casting the
  `Holder<RecipeSerializer<?>>` to `RecipeSerializer<? extends CraftingRecipe>` (unchecked, same
  shape as the JEI plugin's cast last session). `Holder#getRegisteredName()` doesn't exist — the
  `_plus_glass_to_clear` file-name suffix now reads `BuiltInRegistries.BLOCK.getKey(block).getPath()`.
  `ItemTags.MEAT` doesn't exist pre-1.21 — Frenzy Bait's raw-meat ingredient is now an explicit
  `Ingredient.of(Items.BEEF, PORKCHOP, CHICKEN, MUTTON, RABBIT)`.
- **`QuestProvider`**: `Blocks.TUFF_BRICKS` doesn't exist pre-1.21 (the Tuff building set) — the
  Storm Chaser gold-tier tank frame uses plain `Blocks.TUFF` instead.
- **`FishTankModelFabric`**: MC20's `UnbakedModel#bake` takes a trailing `ResourceLocation` (the
  same delta `BlockModel#bake` already had, B4/B5) — added the unused parameter to match.
- **`FishtasticFabricClient`**: FAPI 0.92's `ItemTooltipCallback` is `(ItemStack, TooltipFlag,
  List<Component>)` — no `TooltipContext`/`type` split (that's 1.20.5+); FAPI 0.92's
  `HudRenderCallback` passes a plain `float` partial tick, not a `DeltaTracker` (1.20.2+ only).
- **Portstubs deleted**: `fabric/src/portstub/java/…/FishtasticFabric.java` and
  `forge/src/portstub/java/…/FishtasticForge.java` were both already stale (their own header said
  "deleted in B2.0") and collided with the real entrypoint classes once `fabric/src/main/java/**`
  stopped being excluded — removed both files and the now-empty `portstub` directories.
- **Found and fixed two real gelatin-ui bugs this uncovered**: `runDatagen` boots the full Fabric
  mod loader, which compiling never does. (1) gelatinui's `fabric.mod.json` hardcoded `"java":
  ">=21"` / `"fabricloader": ">=0.17.2"` (copied from a newer branch) — G2.2 had flagged this as
  "cosmetic, doesn't block the build," but it actually hard-crashes any real loader boot, datagen
  included. Fixed in `gelatin-ui` `mc/1.20.1` (`9697dcf`): both bounds now come from
  `fabric/build.gradle`'s `processResources.expand` (`mod_fabric_loader_version` from
  `rootProject.fabric_loader_version`, `mod_java_version` hardcoded `"17"` to match the project's
  own `JavaVersion.VERSION_17` toolchain target) instead of being hand-typed in the template. (2)
  Past that, mixin bootstrap crashed too: `gelatinui-fabric.mixins.json`'s `compatibilityLevel` was
  also still `"JAVA_21"`, which Mixin refuses to set under a Java 17 JRE. Fixed (`7afa572`):
  `"JAVA_17"`. Republished twice (repo convention: never reuse a version string; signing
  temporarily disabled for each mavenLocal publish, same as G-G2, then restored) — `1.0.34+1.20.1`
  for fix 1, `1.0.35+1.20.1` for fix 2. Fishtastic's `gradle.properties` `gelatinui_version` bumped
  to match each time.

Verified `:common:compileJava :fabric:compileJava :common:test :fabric:test`, `--rerun-tasks`, all
green. `fabric/src/main/java/**` is off `port/excludes.txt` — this closes the Fabric half of B1,
plus lets B3.2's Fabric registrar and B2.5's `DailyQuestFamily` be verified by a real module compile
instead of the isolated-probe method those sessions had to use.

**`:fabric:runDatagen` boots and runs against the fixed gelatin-ui, but fails** (B4.2, not a B1
compile issue): `FishtasticItemTagProvider.loadFishProfiles` throws `IllegalStateException: Missing
tag TagKey[minecraft:worldgen/biome / fishtastic:is_snowy_peaks]` while decoding
`fish_profile/arctic_char.json` (and 5 other cold-water fish) through `FishProfile.CODEC`. The tag
file itself exists on disk and copies through to `common/build/resources/main/...`.

**Ruled out**: hypothesized this was because `FishtasticDataGenerator`'s pack never registers a
`FabricTagProvider.BiomeTagProvider`, so the sibling tag providers' `HolderLookup` chain never
picks up the two `FishtasticBiomeTags`. Wrote one (writing `IS_CAVE_BIOME`/`IS_SNOWY_PEAKS` to
match the hand-authored JSON exactly) and added it to the pack before `FishtasticItemTagProvider` —
**identical crash, same stack trace**. Reverted (file deleted, `FishtasticDataGenerator` back to its
original provider list) since it didn't help and would otherwise be a needless divergence from
`port/1.21.1`'s provider list. Also confirmed directly: `port/1.21.1`'s own real
`:fabric:runDatagen --rerun-tasks` (run this session, not just recalled from A3.2's report) succeeds
clean end-to-end, hitting the exact same `FishtasticItemTagProvider.loadFishProfiles` code path with
the exact same `FishProfile`/`FishtasticBiomeTags` source, and it does **not** crash — so this isn't
a code bug in the shared logic, it's a real difference somewhere between FAPI 0.92.12 (1.20.1) and
FAPI 0.116.7 (1.21.1)'s `FabricDataGenerator`/`TagsProvider` internals in how the `HolderLookup`
handed to `addTags` gets built (both log identical `[Render thread/INFO]` provider-boot lines up to
this point, so the boot path itself looks the same). **Not yet root-caused**; next session's first
job before B4.2/G-B4 can close. Worth comparing the two FAPI versions' `FabricTagProvider`/
`AbstractRegistryDependentDataProvider` sources directly (decompile both jars) rather than guessing
again from behavior.

`forge/src/main/java/**` (still a literal, un-renamed copy of the `port/1.21.1` NeoForge module —
~1,283 lines across 20 files, every one of them needing the NeoForge 21.1 → Forge 47 API rewrite
the B5.3 table below describes) is untouched this session and is the natural next target once
B4.2's datagen crash is resolved.

### B4.2 `is_snowy_peaks` root cause (2026-09-26)

Root-caused and fixed the handoff's first suggested task. Decompiled (vineflower) both FAPI
versions' `fabric-data-generation-api-v1` jars looking for a `FabricTagProvider`/mixin difference —
found nothing relevant (`TagProviderMixin` only patches JSON `"replace"`/`"fabric:remove"` output,
unrelated to resolution). The real lead came from instrumenting `FishtasticItemTagProvider.addTags`
directly (writing a debug file from inside the booted datagen process, since `System.out.println`
inside the forked Loom/`TransformerRuntime` process doesn't reach Gradle's captured stdout — use
`Files.writeString` to a file under the process's cwd, `fabric/build/datagen/`, instead) and calling
`provider.lookupOrThrow(Registries.BIOME).listTags()`:

- On `port/1.20.1` (FAPI 0.92.12): the call returns an **empty stream** — zero tags bound at all,
  not even vanilla ones.
- On `port/1.21.1` (FAPI 0.116.7, embedded `fabric-data-generation-api-v1-0.116.7`), the identical
  call **throws `UnsupportedOperationException: Tags are not available in datagen`** — i.e. tags
  aren't just unbound there, `listTags()` is flatly unimplemented.

So datagen's `HolderLookup.Provider` never carries real tag bindings on *either* MC version — this
is expected vanilla behavior, not an FAPI regression. The actual divergence is in
`HolderGetter#getOrThrow(TagKey)` (the per-key lookup `HolderSetCodec` calls during
`FishProfile.CODEC` decode, not the enumerable `listTags()`): MC 1.20.1's default implementation
throws immediately (`IllegalStateException: Missing tag …`) the instant a `TagKey` has no bound
`HolderSet`, while MC 1.21.1's `getOrThrow` is lazy/permissive about forward-referenced tags and
returns something usable regardless. Confirmed by testing the identical instrumentation against
`port/1.21.1` and observing the `UnsupportedOperationException` there too — proving both versions'
providers are equally tag-free, only the per-key getter's strictness differs.

Fix (`FishtasticItemTagProvider.java`, fabric datagen only): wrap the `HolderLookup.Provider` passed
into `addTags` with a `lenientForDatagen(...)` decorator before building the `RegistryOps` used for
`loadFishProfiles`. The decorator delegates every `HolderLookup.RegistryLookup<T>` method unchanged
except `get(TagKey<T>)`, which falls back to `HolderSet.emptyNamed(this, tagKey)` (a real public
vanilla factory for exactly this case) instead of `Optional.empty()` when the underlying lookup has
no binding. This is safe because `FishtasticItemTagProvider` never reads the *resolved membership*
of a fish's biome tag — item tag generation only consumes `zones()` (a plain enum list) and other
non-tag fields; the biome-tag-driven environment-multiplier math is runtime-only code, never
exercised during datagen. Verified: `:fabric:runDatagen --rerun-tasks` now `BUILD SUCCESSFUL`, and
the real generated `data/fishtastic/tags/item/zone_high_altitude.json` correctly lists all 6
`is_snowy_peaks`-referencing fish (`arctic_char`/`bull_trout`/`european_grayling`/`golden_mahseer`/
`golden_trout`/`rainbow_trout`) plus the two other `high_altitude`-zoned fish — so the leniency
doesn't silently corrupt real output, it just stops the irrelevant tag-membership check from
crashing the run. `:common:compileJava :fabric:compileJava :common:test :fabric:test` still green.

**New finding, not yet resolved**: this same successful run's `copyGeneratedAssetsToCommon` also
produced a pile of untracked, almost-certainly-stale generated resources under
`common/src/main/resources` — a duplicate **plural**-path tag tree
(`data/fishtastic/tags/items/**`, `data/minecraft/tags/items/**`, `data/minecraft/tags/blocks/**`)
sitting alongside the real, correct, tracked **singular** `tags/item/`/`tags/block/` trees, plus
long-dead vanilla tags (`data/minecraft/tags/item/enchantable/{durability,fishing}.json`) that
`FishtasticItemTagProvider` explicitly stopped generating back in B1 (they don't exist pre-1.20.5).
This looks like leftover output from an old hash-cache/output directory (possibly under `fabric/run`
or a Loom-internal datagen cache) that's never been cleaned across sessions and gets blindly
re-merged in by `copyGeneratedAssetsToCommon` regardless of which providers are currently registered.
Discarded as untracked cruft this session (`rm -rf` on the 6 new directories) rather than committed
blind — **don't commit generated resources for real until this is tracked down and the stale paths
stop reappearing**, or a genuinely-stale duplicate could get committed as if it were fresh output.

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
