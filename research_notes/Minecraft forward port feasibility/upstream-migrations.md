# Upstream migrations: Minecraft 26.2 and 26.3

Checked 2026-10-19. Research only: no builds, downloads, launches, implementation, or dependency edits. Published versions and migration instructions do not establish a working Fishtastic toolchain. Local guide topics 01, 06, 07, and 08 were read; the local migration-primer directory ends at 26.1.

## What changes from 26.1.2 to 26.2?

### Takeaway

Budget a client/rendering and GUI-library migration plus registration/datagen work. This is not a version-only change.

### Cited Findings

- Fabric recommends Loom 1.17, Gradle 9.5.1, Loader 0.19.3. Vulkan joins OpenGL; raw OpenGL users must migrate through Blaze 3D. Datagen moves to separate BlockIds/BlockItemIds/ItemIds; valueLookupBuilder disappears. Screen access moves to Minecraft.gui. These are publication-time recommendations, not a validated current tuple. — [Fabric 26.2](https://fabricmc.net/2026/06/15/262.html)
- Vanilla replaces formats with GpuFormat, combines BlendFactor, introduces dynamic vertex attributes and BindGroupLayouts, removes MultiBufferSource/OutlineBufferSource, and stages drawing through PreparedRenderType and prepared feature frames. Fonts use prepared/submitted text. PictureInPictureRenderer takes FeatureRenderDispatcher and SubmitNodeCollector. EntityType/BlockEntityType constants relocate; tags become key-oriented. ChatFormatting migrates toward Style. — [NeoForged vanilla primer 26.2](https://docs.neoforged.net/primer/docs/26.2/)
- Final pack versions: data 107.1, resources 88.0. Rendering uses a reversed depth buffer. — [Minecraft 26.2 final technical notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2)
- Architectury API 20→21 changes NeoForge package imports .forge→.neoforge; RegistrySupplier stops extending Holder and provides asHolder(); interaction results change. Apply only to actual usages. — [Architectury 26.1→26.2](https://docs.architectury.dev/api/porting/port-26.1-26.2/)

### Inferences

- Inventory every custom pipeline, framebuffer, text renderer, particle submission, and preview. Record preparation, upload, drawing, attachments, and cleanup responsibilities before changing APIs. Successful compilation is insufficient.
- Reverse-depth audit: search the mod and gelatin-ui for comparison operators, clear values, projection matrices, depth bias, clip-space calculations, and fragment depth writes. Preserve the intended visual behavior. Test overlapping glass/water/sand, labels, clipping, glint, and offscreen previews. This is a risk assessment, not proof every existing pipeline is wrong.
- Library-first gate: require a target gelatin-ui build showing widgets, text, particles, clipping, previews, and resource reload before accepting the full mod estimate.
- GUI gate: catalogue screen reads/writes, HUD calls, packet callbacks, and mixin targets. Check Escape, replacement by game events, disconnect/reconnect, resize, pause, and return from overlays.
- Datagen gate: separate stable keys from instantiated objects; generate into an isolated directory; compare IDs and counts before replacing generated assets. Preserve content identities.
- Client acceptance should capture the same tank scene on OpenGL and Vulkan, then reload resources, resize, reopen screens, and reconnect. Dedicated-server acceptance is independent.

### Gaps

- Exact Architectury Loom fork/plugin compatibility is unverified; Fabric Loom advice does not validate the fork.
- Java 25 is the current local baseline, not independently verified target launcher metadata in this workstream.
- Exact loader hooks and mixin application need pinned target source and future runtime checks.

## What additional migration does 26.3 require?

### Takeaway

Create a second backend/input/rendering and registry-lifecycle package. A passing 26.2 port does not imply 26.3 readiness.

### Cited Findings

- Fabric recommends Loom 1.17, Gradle 9.6.0, Loader 0.19.5. Recipes/advancements become reloadable registries; fuel/composting become components; brewing and block transformers become data-driven. Features/configurations merge with FEATURE_TYPE registration and worldgen/feature data. Block codecs disappear. — [Fabric 26.3](https://www.fabricmc.net/2026/09/15/263.html)
- GPU APIs move from Blaze 3D into com.mojang.renderpearl. RenderPass gains frontend/API separation and compiled pipelines; global texture overrides disappear, and prepared features execute with explicit passes. OIT reorganizes transparency. Recipe.CODEC becomes holder-based while DIRECT_CODEC preserves direct values; RecipeManager loses preparable-reload-listener inheritance; datagen becomes registry bootstrap generation. KeyEvent.key is physical and keycode semantic. InputConstants.Type.KEYSYM becomes KEYBOARD and SCANCODE disappears. GuiEventListener.capturesInput and Screen.isInputCaptured affect ownership. — [NeoForged vanilla primer 26.3](https://docs.neoforged.net/primer/docs/26.3/)
- Final pack versions: data 121.0, resources 97.1. SDL3 replaces GLFW; level.dat gains version_history; block-state Name/Properties become id/properties; terrain uses indirect multidraw where supported. — [Minecraft 26.3 final technical notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3)
- SDL text-input shutdown blocks character delivery. Custom widgets need Minecraft text-input focus coordination. Historical issue and blog examples differ in owner/signature presentation: check pinned sources before copying snippets. — [Fabric-maintained docs issue 730](https://github.com/FabricMC/fabric-docs/issues/730)
- Snapshot 5 documents ShaderC on OpenGL too, #include replacing #moj_import, explicit shader locations, and RENDERPEARL_IS_ZERO_TO_ONE. This is migration history; confirm final release shader contracts. — [Official 26.3 snapshot 5](https://www.minecraft.net/en-us/article/minecraft-26-3-snapshot-5)
- Architectury API 22 specifies minimum Loader 0.19.5, Fabric API 0.160.5, NeoForge 26.3.0.0-beta. Extended menus gain typed StreamCodec data; old overloads are deprecated. Loot lookup and registry-key APIs change; tool-transformer hooks consolidate; getFilePath becomes getFilePaths. — [Architectury 26.2→26.3](https://docs.architectury.dev/api/porting/port-26.2-26.3/)

### Inferences

- Input package: inventory GLFW imports, numeric key/button values, cursor/window access, polling, shortcuts, focus, and IME handling throughout the mod/library. Distinguish physical gameplay keys from layout-sensitive editing. Test non-US layouts, all mouse buttons, scrolling, selection, clipboard, IME, focus loss, and repeated reopen.
- Offscreen package: design explicit target attachment/pass lifetime for previews and captures; verify GPU ownership/disposal and resize/reload. Test transparency intersections, lighting, labels, overlays, and glint in both supported backend/transparency modes.
- Reload package: diagram construction and lookup for profiles, cosmetics, quests, rewards, recipes, loot, and caches. Define invalidation/rebind behavior. Test initial load, successful reload, invalid reload recovery, join-after-reload, and reconnect.
- Networking package: inventory payload/component codecs, especially registry references. Preserve stable payload IDs, document registry context and configuration/play timing, verify round trips, and reject mismatched versions. Familiar codec names do not prove binary or lifecycle compatibility. Cross-Minecraft-version multiplayer is not assumed.
- Save package: use copies containing tanks, fish components, inventories, cosmetics, quests, and unloaded chunks. Test direct 26.1.2→26.3 and staged 26.1.2→26.2→26.3, save/restart, and semantic state comparison. Downgrade support is not an implicit requirement.
- Only migrate worldgen, fuel, brewing, and transformers where actual source usage warrants it; do not expand compatibility work into adopting every new vanilla system.

### Gaps

- Exact final shader/input contracts, loader patches, pack decoding, and custom component serialization are untested.
- The library, renderer, and capture paths remain conditional feasibility risks until future authorized spikes.

## What feasibility and toolchain conclusions are justified?

### Takeaway

Both targets have upstream migration documentation. Plausible with substantial client/library work is justified; builds working is unproven.

### Cited Findings

- Fabric Maven lists jar, sources, POM, and checksums for 0.160.5+26.3 dated 2026-09-14. This proves coordinate publication only. — [Artifact index](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.160.5%2B 26.3/)
- NeoForged search listings advertise 26.3.0.90, but its project page exposed no readable version metadata and the guessed artifact directory was inaccessible. A 26.3.0.36-beta index was observed independently. Neither is a validated pin. — [Projects](https://projects.neoforged.net/); [Observed beta](https://maven.neoforged.net/releases/net/neoforged/neoforge/26.3.0.36-beta/)

### Inferences

1. Freeze independent candidate manifests: Minecraft, Java, wrapper, Loom fork, Architectury plugin/API if used, loader/API, gelatin-ui, and optional integrations. Record source, status, declared constraints, checksum.
2. Future gate: verify exact coordinates and metadata, resolve the graph, inspect conflicts/nested jars/transformed common output. Track published, resolves, compiles, launches, and feature-validated separately.
3. Future gate: validate minimal library/render behavior independently; stop estimating full parity as routine if this fails.
4. Future gate: registrations, common codecs, datagen, dedicated server, reload, and saves.
5. Future gate: screens/input/custom drawing/previews/captures/particles/shaders, with fixed visual comparisons and device/backend evidence.
6. Future gate: optional integrations individually, multiplayer, upgrade fixtures, packaged jars, metadata constraints, clean profiles.
7. Use completed 26.2 evidence to refine 26.3 estimates; retain independent release decisions.

### Gaps

- No target resolution, builds, runtimes, Forge availability, or integration compatibility were validated here.
- Give effort ranges until library/render and dependency gates pass; precise day counts from changelogs would imply unsupported certainty.
