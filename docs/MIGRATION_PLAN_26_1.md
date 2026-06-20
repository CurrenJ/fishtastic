# Fishtastic Migration Plan: 1.21.1 → MC 26.1.2

Generated: 2026-04-11  
Reference project: `D:\GitHub\gelatin-ui` (already migrated)  
Sources: Architectury 26.1 migration gist, NeoForged primers (1.21.5, 26.1), NeoForge/Fabric API source

---

## Version Map

| Dependency          | Current           | Target              |
|---------------------|-------------------|---------------------|
| Minecraft           | 1.21.1            | 26.1.2              |
| NeoForge            | 21.1.209          | 26.1.2.4-beta       |
| Fabric Loader       | 0.17.2            | 0.19.1              |
| Fabric API          | 0.116.7+1.21.1    | 0.145.4+26.1.2      |
| Architectury API    | 13.0.8            | ~14.x (verify)      |
| Architectury Plugin | 3.4-SNAPSHOT      | 3.5-SNAPSHOT        |
| Loom Plugin         | 1.11-SNAPSHOT     | 1.14-SNAPSHOT (no-remap) |
| Shadow Plugin       | 8.3.6             | 8.3.6 (unchanged)   |
| Java                | 21                | 25                  |
| Gradle              | 8.x               | 9.x                 |
| GelatinUI           | 1.0.16            | TBD (check gelatin-ui release) |

---

## Migration Path Overview

The version jump spans many intermediate MC releases. The breaking changes accumulate across:
- **1.21.1 → 1.21.4**: (no primer, check NeoForged 1.21.4 primer)
- **1.21.4 → 1.21.5**: Major — render pipeline rework, BlockEntity removal refactor, Item API changes, WeightedList rework, tool/weapon item class removals
- **1.21.5 → 1.21.11**: (check NeoForged intermediate primers)
- **1.21.11 → 26.1**: Major — Java 25, loom-no-remap (unobfuscated), loot type unrolling, GUI/render extraction pipeline, `GuiGraphics` → `GuiGraphicsExtractor`, `BlockEntityRenderer` signature change, `ItemRenderer`/`BlockRenderDispatcher` removed

---

## Phase 1 — Build Infrastructure

### 1.1 Gradle Wrapper

**File:** `gradle/wrapper/gradle-wrapper.properties`

```diff
-distributionUrl=https\://services.gradle.org/distributions/gradle-8.x-bin.zip
+distributionUrl=https\://services.gradle.org/distributions/gradle-9.x-bin.zip
```

Use the version from `D:\GitHub\gelatin-ui\gradle\wrapper\gradle-wrapper.properties` as the exact reference.

### 1.2 `gradle.properties`

```diff
-minecraft_version=1.21.1
+minecraft_version=26.1.2

-neoforge_version=21.1.209
+neoforge_version=26.1.2.4-beta

-fabric_loader_version=0.17.2
+fabric_loader_version=0.19.1

-fabric_api_version=0.116.7+1.21.1
+fabric_api_version=0.145.4+26.1.2

-architectury_api_version=13.0.8
+architectury_api_version=<find 26.1.x-compatible version on https://maven.architectury.dev/>

-mod_neoforge_version_range=[${neoforge_version},)
+mod_neoforge_version_range=[26.1.2.4-beta,)

-mod_minecraft_version_range=[${minecraft_version},)
+mod_minecraft_version_range=[26.1.2,)

-mod_fabric_minecraft_version=~${minecraft_version}
+mod_fabric_minecraft_version=~26.1.2

-gelatinui_version=1.0.16
+gelatinui_version=<check gelatin-ui release for 26.1.2>

# Java version range in fabric.mod.json should also update (done in Phase 3)
```

Also add if not present (see gelatin-ui):
```properties
org.gradle.toolchains.foojay-resolver-convention=1.0.0
```

### 1.3 `settings.gradle`

Add the Foojay toolchain resolver if not present (required for Java 25 auto-download):

```groovy
plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
}
```

Verify `pluginManagement` repositories still include:
- `https://maven.fabricmc.net/`
- `https://maven.architectury.dev/`
- `https://maven.neoforged.net/releases/`
- `gradlePluginPortal()`

### 1.4 Root `build.gradle`

**Critical changes:**

```diff
 plugins {
-    id 'dev.architectury.loom' version '1.11-SNAPSHOT' apply false
-    id 'architectury-plugin' version '3.4-SNAPSHOT'
+    id 'dev.architectury.loom-no-remap' version '1.14-SNAPSHOT' apply false
+    id 'architectury-plugin' version '3.5-SNAPSHOT'
     id 'com.gradleup.shadow' version '8.3.6' apply false
 }
```

```diff
 subprojects {
-    apply plugin: 'dev.architectury.loom'
+    apply plugin: 'dev.architectury.loom-no-remap'
     apply plugin: 'architectury-plugin'
     apply plugin: 'maven-publish'
     ...
 }
```

```diff
-    loom {
-        silentMojangMappingsLicense()
-    }

     dependencies {
         minecraft "net.minecraft:minecraft:$rootProject.minecraft_version"
-        mappings loom.officialMojangMappings()
     }
```

```diff
     tasks.withType(JavaCompile).configureEach {
-        it.options.release = 21
+        it.options.release = 25
     }
```

```diff
     java {
-        sourceCompatibility = JavaVersion.VERSION_21
-        targetCompatibility = JavaVersion.VERSION_21
+        sourceCompatibility = JavaVersion.VERSION_25
+        targetCompatibility = JavaVersion.VERSION_25
     }
```

---

## Phase 2 — Subproject Build Scripts

### 2.1 `common/build.gradle`

Remove `mod` prefix from all dependencies (loom-no-remap drops this convention):

```diff
 dependencies {
-    modImplementation "net.fabricmc:fabric-loader:$rootProject.fabric_loader_version"
-    modImplementation "dev.architectury:architectury:$rootProject.architectury_api_version"
-    modCompileOnly("io.github.currenj.gelatinui:gelatinui-common:${gelatinui_version}")
+    implementation "net.fabricmc:fabric-loader:$rootProject.fabric_loader_version"
+    implementation "dev.architectury:architectury:$rootProject.architectury_api_version"
+    compileOnly("io.github.currenj.gelatinui:gelatinui-common:${gelatinui_version}")
 }
```

No `mappings` line needed (unobfuscated in 26.1).

### 2.2 `fabric/build.gradle`

**Remove `remapJar` — it no longer exists in loom-no-remap. `shadowJar` becomes the primary output.**

```diff
 dependencies {
-    modImplementation "net.fabricmc:fabric-loader:$rootProject.fabric_loader_version"
-    modImplementation "net.fabricmc.fabric-api:fabric-api:$rootProject.fabric_api_version"
-    modImplementation("io.github.currenj.gelatinui:gelatinui-fabric:${gelatinui_version}")
-    common(project(path: ':common', configuration: 'namedElements')) { transitive = false }
+    implementation "net.fabricmc:fabric-loader:$rootProject.fabric_loader_version"
+    implementation "net.fabricmc.fabric-api:fabric-api:$rootProject.fabric_api_version"
+    implementation("io.github.currenj.gelatinui:gelatinui-fabric:${gelatinui_version}")
+    common(project(path: ':common')) { transitive = false }
     shadowBundle project(path: ':common', configuration: 'transformProductionFabric')
 }
```

Replace the `shadowJar`/`remapJar` block:

```diff
-shadowJar {
-    configurations = [project.configurations.shadowBundle]
-    archiveClassifier = 'dev-shadow'
-}
-
-remapJar {
-    inputFile.set shadowJar.archiveFile
-}
+jar {
+    archiveClassifier = "raw"
+}
+
+shadowJar {
+    dependsOn(jar)
+    mainSpec.sourcePaths.clear()
+    from(zipTree(jar.archiveFile))
+    configurations = [project.configurations.shadowBundle]
+    archiveClassifier = null
+}
```

Update `processResources` — replace `project(":common").buildDir` reference (deprecated in Gradle 9) with `project(":common").layout.buildDirectory`:

```diff
 processResources {
     ...
-    from(project(":common").buildDir) {
-        include "resources/main/fishtastic.refmap.json"
-    }
+    from(project(":common").layout.buildDirectory) {
+        include "resources/main/fishtastic.refmap.json"
+    }
     ...
 }
```

### 2.3 `neoforge/build.gradle`

Same `mod` → no-prefix rename and `namedElements` removal as Fabric:

```diff
 dependencies {
     neoForge "net.neoforged:neoforge:$rootProject.neoforge_version"
-    modImplementation("io.github.currenj.gelatinui:gelatinui-neoforge:${gelatinui_version}")
-    common(project(path: ':common', configuration: 'namedElements')) { transitive = false }
+    implementation("io.github.currenj.gelatinui:gelatinui-neoforge:${gelatinui_version}")
+    common(project(path: ':common')) { transitive = false }
     shadowBundle project(path: ':common', configuration: 'transformProductionNeoForge')
 }
```

Replace `shadowJar`/`remapJar`:

```diff
-shadowJar {
-    configurations = [project.configurations.shadowBundle]
-    archiveClassifier = 'dev-shadow'
-}
-
-remapJar {
-    inputFile.set shadowJar.archiveFile
-}
+jar {
+    archiveClassifier = "raw"
+}
+
+shadowJar {
+    dependsOn(jar)
+    mainSpec.sourcePaths.clear()
+    from(zipTree(jar.archiveFile))
+    configurations = [project.configurations.shadowBundle]
+    archiveClassifier = null
+}
```

Fix deprecated `buildDir` in `processResources`:

```diff
-    from(project(":common").buildDir) {
-        include "resources/main/fishtastic.refmap.json"
-    }
+    from(project(":common").layout.buildDirectory) {
+        include "resources/main/fishtastic.refmap.json"
+    }
```

---

## Phase 3 — Metadata Files

### 3.1 `neoforge/src/main/resources/META-INF/neoforge.mods.toml`

```diff
-loaderVersion = "[4,)"
+loaderVersion = "[4,)"   # verify against NeoForge 26.1.x — may need "[4,)" or newer range
```

Update the version range templates so they resolve correctly at processResources time (currently uses `${mod_neoforge_version_range}` etc. — these are already parameterized so will update automatically via `gradle.properties`).

Check if NeoForge 26.1 changed the TOML schema (e.g., new required fields). Reference: `D:\GitHub\gelatin-ui\neoforge\src\main\resources\META-INF\neoforge.mods.toml`.

### 3.2 `fabric/src/main/resources/fabric.mod.json`

```diff
   "depends": {
-    "fabricloader": ">=0.17.2",
+    "fabricloader": ">=0.19.1",
-    "minecraft": "${mod_fabric_minecraft_version}",
+    "minecraft": "~26.1.2",
-    "java": ">=21",
+    "java": ">=25",
     "fabric-api": "*"
   }
```

---

## Phase 4 — Mixin Configuration Files

### 4.1 `common/src/main/resources/fishtastic.mixins.json`

```diff
-"compatibilityLevel": "JAVA_21",
+"compatibilityLevel": "JAVA_25",
```

### 4.2 `fabric/src/main/resources/fishtastic-fabric.mixins.json`

```diff
-"compatibilityLevel": "JAVA_21",
+"compatibilityLevel": "JAVA_25",
```

---

## Phase 5 — Java Source: Common Module

### 5.1 `Item#inventoryTick` signature change (1.21.5)

**Affects:** Any item that overrides `inventoryTick`. Check `CopperFishingRod.java`, `FishtasticFishItem.java`, `AcuteIapsisItem.java`, `TestItem.java`.

```diff
-public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected)
+public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot)
```

The method is now only called on the server (`ServerLevel`). Any client-side logic previously gated on `level.isClientSide` must be moved to an event or removed.

### 5.2 BlockEntity removal refactor (1.21.5)

**Affects:** `FishTankBlockEntity.java`

`BlockBehaviour#onRemove` is now split:
- `BlockEntity#preRemoveSideEffects(ServerLevel, BlockPos, BlockState)` — drop contents, handle pre-removal side effects
- `BlockBehaviour#affectNeighborsAfterRemoval(ServerLevel, BlockPos, BlockState)` — update neighbors only

If `FishTankBlockEntity` or its block overrides `onRemove` to drop items or do side effects, those must migrate.

### 5.3 DataComponents changes (1.21.5)

**Affects:** `FishtasticDataComponents.java`, `ItemSize.java`, `FishQuality.java`, and any tooltip logic.

- `DataComponents.HIDE_ADDITIONAL_TOOLTIP` and `DataComponents.HIDE_TOOLTIP` are **removed** → replaced by `DataComponents.TOOLTIP_DISPLAY` taking a `TooltipDisplay`
- `DataComponents.UNBREAKABLE` now holds a `Unit` instead of `Unbreakable`

### 5.4 Loot API — Loot Type Unrolling (26.1)

**Affects:** `FishingLootHelper.java`, `ItemEffect.java`, `ItemEffectCondition.java`, and any `ItemEffectManager` loot integration.

All loot-related types now use `codec()` instead of `getType()`, and the `*Type` wrapper records are removed:

```diff
-LootItemFunctionType getType()
+MapCodec<? extends LootItemFunction> codec()
```

Registration now uses the `MapCodec` directly:
```diff
-Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE, id, new LootItemFunctionType(codec))
+Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE, id, mapCodec)
```

Applies to:
- `LOOT_FUNCTION_TYPE` (LootItemFunction)
- `LOOT_CONDITION_TYPE` (LootItemCondition)
- `LOOT_NUMBER_PROVIDER_TYPE` (NumberProvider)
- `LOOT_POOL_ENTRY_TYPE` (LootPoolEntryContainer)

### 5.5 WeightedList rework (1.21.5)

**Affects:** Any code using `SimpleWeightedRandomList` or `WeightedRandomList`.

```diff
-net.minecraft.util.random.SimpleWeightedRandomList
-net.minecraft.util.random.WeightedRandomList
-net.minecraft.util.random.WeightedEntry
+net.minecraft.util.random.WeightedList
+net.minecraft.util.random.Weighted
```

`WeightedBakedModel` in the NeoForge `FishTankBakedModel` now takes `WeightedList` instead of `SimpleWeightedRandomList`. See Phase 6.2.

### 5.6 Validation API (26.1)

**Affects:** If any `ItemEffect` or condition implements a loot interface. `CriterionValidator` is replaced by `ValidationContextSource` and `Validatable`.

### 5.7 GUI / Screen changes (26.1)

**Affects:** `LeaderboardScreen.java`, `SelectableItemButton.java`, anything extending `Screen`/`AbstractContainerScreen`/`AbstractWidget`.

`GuiGraphics` is **renamed** to `GuiGraphicsExtractor`. All method prefixes change:

| Old prefix | New prefix | Example |
|------------|------------|---------|
| `draw*`    | `extract*` | `drawString` → `extractText` |
| `render*`  | `extract*` | `renderOutline` → `outline` |
| `submit*`  | (removed)  | |
| `*String*` | `*Text*`   | `drawString` → `extractText` |

Screen lifecycle methods:
```diff
-Renderable#render(GuiGraphics, int, int, float)
+Renderable#extractRenderState(...)
-AbstractContainerScreen#renderBg
+Screen#extractBackground
-AbstractWidget#renderWidget
+AbstractWidget#extractWidgetRenderState
```

`IGuiGraphicsExtension.java` will need to be updated to extend/mixin `GuiGraphicsExtractor`.

---

## Phase 6 — Java Source: NeoForge Module

### 6.1 ServerTickEvent (1.21.x → 26.1)

**Affects:** `NeoForgePacketRegistrar.java`, `FishtasticNeoForge` (entry point, locate actual file).

`net.neoforged.neoforge.event.tick.ServerTickEvent` — verify the inner class names (`Pre`/`Post`) haven't changed. Reference `D:\GitHub\NeoForge\src\main\java\net\neoforged\neoforge\event\tick\ServerTickEvent.java`.

### 6.2 `FishTankBakedModel.java` — BakedModel and WeightedBakedModel

**Affects:** `neoforge/src/main/java/grill24/fishtastic/neoforge/fishtank/FishTankBakedModel.java`

From 1.21.5:
- `WeightedBakedModel` now takes `WeightedList<BakedModel>` instead of `SimpleWeightedRandomList<BakedModel>`
- `net.minecraft.client.renderer.block.model.BlockModel` → **renamed** `CuboidModel`
- `BlockModelWrapper` → `CuboidItemModelWrapper`
- `BlockModelPart` → `BlockStateModelPart`
- `BlockModelDefinition` → `BlockStateModelDispatcher`

From 26.1:
- `BlockRenderDispatcher` is **completely removed** — the rendering pipeline now uses `BlockModelResolver`
- Any use of `BlockRenderDispatcher` in `FishTankBakedModel` must be replaced with the new `BlockModel`/`BlockModelResolver` pattern

The `FishTankBakedModel` and `FishTankModel` system may need significant redesign to use the new `BlockModel` interface instead of `BakedModel`. Consider whether a `SpecialBlockModelWrapper` approach (vanilla-provided for custom block models) fits the fish tank's needs.

### 6.3 `FishTankBlockEntityNeoForge.java` — BlockEntity rendering

From 26.1:
- `BlockEntityRendererProvider.Context` no longer provides `ItemRenderer` or `BlockRenderDispatcher`
- It now provides `BlockModelResolver` instead of `BlockRenderDispatcher`

If `FishTankBlockEntityNeoForge` or its renderer caches `ItemRenderer` from context, that must change:
```diff
-this.itemRenderer = context.getItemRenderer();
// ItemRenderer no longer exists; use the new item model pipeline
```

### 6.4 `requestModelDataUpdate` in NeoForgeRegistrationApi

**Affects:** `NeoForgeRegistrationApi#requestModelDataUpdate`

Verify `blockEntity.requestModelDataUpdate()` still exists in NeoForge 26.1.2. The `ModelData` API may have evolved. Cross-reference `D:\GitHub\NeoForge\src\main\java\net\neoforged\neoforge\client\model\data\`.

### 6.5 `OnDatapackSyncEvent` and `DataPackRegistryEvent`

**Affects:** `FishtasticRegistriesNeoForge.java` (locate actual file path), anywhere `DataPackRegistryEvent` is subscribed.

From the NeoForge source, verify:
- `DataPackRegistryEvent.NewRegistry` still exists and has the same API
- `OnDatapackSyncEvent` signature is unchanged

### 6.6 NeoForge Packet API

**Affects:** `NeoForgePacketRegistrar.java`

`PayloadRegistrar.playToServer` / `playToClient` and `IPayloadContext` — verify these are unchanged in NeoForge 26.1.2. The networking API is generally stable but check `D:\GitHub\NeoForge\src\main\java\net\neoforged\neoforge\network\registration\PayloadRegistrar.java` to confirm method signatures.

### 6.7 FishTankBlockEntityRenderer (NeoForge rendering)

`FishTankBlockEntityRenderer.java` will need the most significant work:

**New BlockEntityRenderer signature (26.1):**
```java
// Old:
public class FishTankBlockEntityRenderer implements BlockEntityRenderer<FishTankBlockEntity> {
    public void render(FishTankBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int combinedLight, int combinedOverlay) { ... }
}

// New (26.1 two-generic pattern):
public class FishTankBlockEntityRenderer
        implements BlockEntityRenderer<FishTankBlockEntity, FishTankRenderState> {

    // Extract data from the BE into a render state object
    public void extractRenderState(FishTankBlockEntity blockEntity, FishTankRenderState state,
                                   float partialTick, Vec3 cameraPosition,
                                   ModelFeatureRenderer.CrumblingOverlay breakProgress) { ... }

    // Submit rendering elements using only the render state
    public void submit(FishTankRenderState state, PoseStack pose,
                       SubmitNodeCollector collector, CameraRenderState camera) { ... }
}
```

A `FishTankRenderState` class extending `BlockEntityRenderState` will need to be created to hold all display data extracted from the BE.

The `RenderBuffersHelper` and `MultiBufferSource` usage will change — `SubmitNodeCollector` replaces direct buffer writes in many cases.

---

## Phase 7 — Java Source: Fabric Module

### 7.1 `FabricRegistrationApi.java` — DynamicRegistries

**Affects:** `fishTankFrameTypes()` (currently throws `OperationNotSupportedException`).

`net.fabricmc.fabric.api.event.registry.DynamicRegistries` — the API for registering datapack registries may have changed between 0.116.7 and 0.145.4. Verify the registration pattern in the Fabric API source at `D:\GitHub\fabric-api\`.

### 7.2 ServerTickEvents

**Affects:** `FabricPacketRegistrar.java` (uses `ServerTickEvents`).

`net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents` — verify `END_SERVER_TICK` / `START_SERVER_TICK` callback signatures haven't changed.

### 7.3 ClientPlayNetworking

The Fabric networking API may have updated. `ClientPlayNetworking.registerGlobalReceiver` should still work but check for deprecations in `fabric-networking-api-v1 v6.3.0`.

---

## Phase 8 — Mixin Targets Audit

Every mixin will need its target method signatures verified because class/method renames may have occurred across the many intermediate versions.

### High-Risk Mixins

| Mixin File | Target Class | Risk | Reason |
|---|---|---|---|
| `ItemRendererMixin` | `ItemRenderer` | **CRITICAL** | `ItemRenderer` is **completely removed** in 26.1. This mixin cannot exist as-is. The functionality must be moved to the new item rendering pipeline. |
| `GameRendererMixin` | `GameRenderer` | HIGH | `GameRenderer#renderLevel` and camera extraction changed significantly in 26.1. |
| `GuiGraphicsMixin` | `GuiGraphics` → `GuiGraphicsExtractor` | HIGH | Class renamed; all method names changed. Update mixin target class and `@Inject`/`@Overwrite` targets. |
| `LevelRendererMixin` | `LevelRenderer` | HIGH | Feature rendering split into solid/translucent passes; method signatures changed. |
| `RenderBuffersMixin` | `RenderBuffers` | MEDIUM | `RenderBuffers` may still exist but internal structure changed for the uber buffer system. Verify in 26.1. |
| `FishingHookMixin` | `FishingHook` | LOW-MEDIUM | Fishing hook internals unlikely to have changed but verify method names. |
| `ItemStackMixin` | `ItemStack` | LOW-MEDIUM | `ItemStack` is stable but minor method additions/renames possible. |
| `MouseHandlerMixin` (Fabric) | `MouseHandler` | LOW | Mouse input handling unlikely to have changed significantly. |

### Action for `ItemRendererMixin`

Since `ItemRenderer` no longer exists, you must determine what `ItemRendererMixin` was doing and find the new equivalent:
1. Read the mixin source to understand what it was overriding (likely `renderBakedItemModel` or `renderItem`)
2. In 26.1, item rendering goes through `ItemModel` and the feature submission pipeline
3. The equivalent hook point will be in the new `ItemModel` interface or `SpecialModelRenderer`

### Action for `GuiGraphicsMixin`

Update the mixin target:
```diff
-@Mixin(GuiGraphics.class)
+@Mixin(GuiGraphicsExtractor.class)
```
Then update every `@Inject`/`@Redirect` method descriptor to use renamed methods.

### Action for `IGuiGraphicsExtension`

The Mixin interface `IGuiGraphicsExtension` must target `GuiGraphicsExtractor` instead of `GuiGraphics`.

---

## Phase 9 — Resource / Datagen Updates

### 9.1 Model JSON Changes

From 1.21.5, block models no longer specify `RenderType`. Remove any `"render_type"` field from custom model JSON files if present. The render type is now inferred from texture transparency.

### 9.2 `ItemBlockRenderTypes` removed (1.21.5)

If any code calls `ItemBlockRenderTypes.setRenderLayer(...)` (e.g., in FishtasticNeoForge or FishtasticFabric client init for the fish tank glass), this must be **removed**. Render layers are now auto-detected from textures.

### 9.3 Datagen

Update datagen providers to the new APIs:
- `RecipeProvider` — `trimSmithing` method changed, but fishtastic may not use this
- Any item model providers that referenced `BlockModel` directly must use `CuboidModel`

---

## Phase 10 — Verification Checklist

After all changes:

- [ ] `./gradlew :common:build` compiles without errors
- [ ] `./gradlew :fabric:build` compiles without errors
- [ ] `./gradlew :neoforge:build` compiles without errors
- [ ] Run Fabric client — mod loads, fishing minigame functional
- [ ] Run NeoForge client — mod loads, fish tank renders correctly
- [ ] Verify fish tank glass transparency renders without explicit render type registration
- [ ] Verify networking (start/finish minigame packets, leaderboard request/response)
- [ ] Verify datapack registry for `FishTankFrameType` loads (NeoForge)
- [ ] Verify item effects system loads from datapack
- [ ] Verify leaderboard screen renders correctly (GuiGraphics rename impact)
- [ ] Run datagen on both platforms and verify output is valid

---

## Reference Files

- Architectury 26.1 guide: https://gist.githubusercontent.com/shedaniel/18d4eebf940e9c3296e87d994ebb2838/raw/.../26.1.md
- NeoForge 1.21.5 primer: https://raw.githubusercontent.com/neoforged/.github/main/primers/1.21.5/index.md
- NeoForge 26.1 primer: https://raw.githubusercontent.com/neoforged/.github/main/primers/26.1/index.md
- Migrated reference project: `D:\GitHub\gelatin-ui`
- NeoForge source: `D:\GitHub\NeoForge`
- Fabric API source: `D:\GitHub\fabric-api`

---

## Recommended Order of Work

1. **Build system first** (Phases 1–3) — get a clean compile baseline even if features are broken
2. **Fix compile errors** — most will be API removals/renames; address in order of dependency (common → neoforge/fabric)
3. **Mixin audit** (Phase 8) — mixins that target removed classes will fail at class-load, so fix these before running
4. **Rendering overhaul** (Phase 6.2–6.7) — largest chunk of work; the fish tank renderer and item renderer mixin are the highest effort
5. **GUI screens** (Phase 5.7) — LeaderboardScreen and SelectableItemButton after the common compile is clean
6. **Test and verify** (Phase 10)

> **Note on `ItemRendererMixin`:** This is the highest-risk item. `ItemRenderer` being removed entirely means the mixin is dead code in 26.1. Before implementing a replacement, read the mixin source carefully and determine what behavior it was injecting — it may be achievable through a NeoForge event or Fabric callback in the new pipeline without a mixin at all.
