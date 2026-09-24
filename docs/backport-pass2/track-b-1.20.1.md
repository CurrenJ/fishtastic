# Track B: 1.21.1 → 1.20.1 (pass 2)

> Parent: [`README.md`](README.md). Starts from `port/1.21.1` at **G2**. Branch `port/1.20.1`, worktree `D:\GitHub\fishtastic-worktrees\mc-1.20.1`.
> Conventions as in track A. "Lands on" cites `D:\GitHub\modding-guide\resources\minecraft-merged-1.20.1-sources` (MC20), `forge-1.20.1-47.4.23-patched-sources` (FG; vanilla with Forge patches + `net.minecraftforge.*`) and `fabric-api-0.92.12+1.20.1-sources` (FAPI20).
> Gradle runs on JDK 17 in this worktree: `-Dorg.gradle.java.home=<jdk-17>` (installed: `C:\Program Files\Eclipse Adoptium\jdk-17.0.6.10-hotspot` or `C:\Program Files\Java\jdk-17.0.6+10`). Written `gw17` below.

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
- **New, D11 for track B:** Loom line. The throwaway source project ran **Architectury Loom 1.11 on Gradle 8.14** against Forge 47.4.23 and Fabric API 0.92.12 successfully (genSources, 2026-09-24). Try Loom 1.17 / Gradle 9.5 first to match `port/1.21.1` (fewer build-file diffs under lockstep), and fall back to 1.11 / 8.14, which is proven.

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

**Nothing to port.** Seam S5 (`552fed58`) replaced the Java 21-only calls on `26.1.2`, so `port/1.21.1` and this branch inherit Java 17-clean sources. The guard `java17ApiGuard` (`b7e24911`, `gradle/java17-api-guard.gradle`) fails the build if any come back. It runs before `:common:test` and every `runGametest`. On 1.20.1 the Java 17 toolchain is the final check. B1.2 only has to confirm that `:common:java17ApiGuard` passes and that the tree compiles with toolchain 17.

What S5 actually found, which corrects pass 2's N7 estimate of ~45 sites in 21 files: 19 `List#getFirst()` → `get(0)`, 7 `Math.clamp` → `Mth.clamp`, and one `SequencedMap` local → `Map` (in `RenderBuffersMixin`). All 13 `reversed()` hits were `Comparator#reversed` (Java 8), and the `addFirst`/`addLast`/`removeFirst` hits were on `Deque`s or MC/Fabric API methods. `BufferSourceAccessor` still declares `SequencedMap`, because Mixin matches the field's exact descriptor. On 1.20.1 the field is `Map<RenderType, BufferBuilder>`, so the B5.2 render rewrite changes that accessor anyway. (Pattern matching for `switch` and record patterns: 0 uses. `Stream#toList` (Java 16) and `HexFormat` (Java 17) are fine.)

**Gate (G-B1):** `gw17 :fishsim:test :tools:tank-shape-gen:test` (163 + 1 skipped, 21,955). `gw17 :fabric:build :forge:build`. The A1-style probe loads under `runServer` on both loaders (Forge in the background). Refmap jar check.

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
| `ResourceLocation.fromNamespaceAndPath(ns, p)` / `withDefaultNamespace(p)` / `parse(s)` | `new ResourceLocation(ns, p)` / `new ResourceLocation(p)` / `new ResourceLocation(s)` (MC20 `ResourceLocation.java:37,45`). `tryParse` exists (`:54`). | all 106 `ResourceLocation` files. A `util/Ids.of(ns, p)` helper on **every branch** would make this a no-op (another S6-style seam, and the cheapest one). |
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
| `ResourceLocation` construction | 106 | yes (or zero with an `Ids.of` seam) |
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
