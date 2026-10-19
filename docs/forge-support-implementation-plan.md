# Add Forge while preserving loader parity

**Status: PLANNED. Research reviewed 2026-10-18.** This is the primary implementation guide for adding Forge to Fishtastic and its required Gelatin UI dependency on Minecraft 1.21.1 and 26.1.2. Both ports are technically credible, but neither is yet proven by a local Forge compile or launch. The preferred route is **Architectury Loom 1.17 on both versions**, with separate Forge modules and version-specific adapters. Upstream now explicitly supports Forge 1.21.x and unobfuscated Forge 26.x; the remaining risk lies in this project's artifact transformations, registration and networking contracts, and especially 26.1.2 rendering. Prove those risks before completing the large adapter surface. Preserve Fabric and NeoForge throughout, and keep the existing 1.20.1 implementation unchanged except where later shared release tooling demonstrably requires adjustment.

## 1. Decisions and boundaries

### 1.1 Recommended implementation decisions

| Area | Recommended decision | Evidence required before commitment |
|---|---|---|
| Build family | Pin a Loom 1.17 build containing the modern Forge fixes; retain Architectury transformation | Minimal development and installed-jar client/server probes for each version |
| Version order | Probe both toolchains early; complete 1.21.1 first; investigate 26.1.2 rendering before committing to its full port | Separate prototype results; do not infer 26.1.2 success from 1.21.1 |
| Shared code | Preserve each branch's `common` and Minecraft-free `fishsim`; adapters own loader APIs | No Forge runtime imports in common; existing loaders retain behavior |
| Registration | Start from the 1.20.1 event-scoped real-holder strategy, adapted to current registries | Holder identity, lazy block items and data-component initialization pass |
| Transport | Prototype Forge `PayloadChannel` against existing typed payloads and codecs | One C2S request and one registry-bearing S2C ItemStack succeed using channel sends |
| Sending | Introduce a common transport seam and explicit Forge/Fabric/NeoForge implementations | Inventory contains no unintended direct vanilla sends of Fishtastic payloads |
| Connection policy | Initial supported configuration: same Minecraft version and Forge client/server, required Fishtastic and matching Gelatin, strict protocol match | Positive join and deliberate missing/version/direction negative checks |
| Companions | Forge-owned SavedData plus explicit owner/watcher sync behind `CompanionStorage` | Full player/world lifecycle, mutation persistence and cache-cleanup proof |
| Gelatin | Port first; split menu initialization from registry-time firing without breaking public API | Consumer registration succeeds independently of construction order |
| Resources | Keep Fabric as current canonical datagen entry; add Forge-specific model resources only where required | Generation/sync diff and final resource inventory |
| Distribution | Separate loader jars; publish validated Gelatin before Fishtastic requires it remotely | Clean dependency resolution and installed client/server proof |

These are implementation defaults, not claims of successful prototypes. In particular, **cross-loader multiplayer and automatic conversion of NeoForge attachment saves are outside the initial acceptance target**. Before declaring support, document the actual loader-switch world compatibility: shared registry IDs do not establish companion-data compatibility. Do not silently discard incompatible data or describe an untested world switch as supported.

### 1.2 Deliverables and non-goals

The new deliverables are two Fishtastic Forge artifacts and the corresponding two Gelatin Forge artifacts, plus source/build/test/documentation support. Fishtastic's supported artifact matrix grows from six to eight:

| Minecraft | Existing artifacts retained | New artifact | Java |
|---|---|---|---:|
| 26.1.2 | Fabric, NeoForge | Forge | 25 |
| 1.21.1 | Fabric, NeoForge | Forge | 21 |
| 1.20.1 | Fabric, Forge | None | Existing branch contract retained |

This project does not backport features again, replace NeoForge, merge Minecraft source lines, introduce a runtime Architectury API dependency by default, redesign the gameplay protocol, or expand Gelatin with a new packet subsystem. Existing backport parity and release work have their own ledger. Do not advance their ported-through markers during Forge work.

### 1.3 Evidence levels and limitations

Use these labels in implementation records:

- **UPSTREAM VERIFIED:** official source/documentation inspected during this review. It proves an API or upstream implementation exists, not that this mod runs on it. Moving branch links must be replaced by pinned source commits when work begins.
- **LOCAL INSPECTED:** checked-out source/configuration examined. It can include dirty edits and does not establish a successful build.
- **IMPLEMENTATION DECISION:** a recommended design derived from the evidence; validate at the stated gate.
- **EXECUTION VERIFIED:** reserved for future commands with exact inputs, logs, exit status and reviewed outputs.

**No builds, dependency-resolution experiments, client/server launches, GameTests, publishing or profile deployments were performed for this investigation.** Historical test and release logs remain historical evidence. They are not Forge acceptance results. This guide supersedes the earlier chat outline where they differ: Loom 1.17 has concrete newer-Forge support; Forge 52 uses Mojang production names; modern payload channels are the first transport candidate.

## 2. Pin the actual starting point before editing

### 2.1 Source baselines are moving and contain unrelated changes

The review observed Fishtastic source branch `26.1.2` at `0b84e7e327e9317ebd77c6fb61d418bb33329cdf`, 1.21.1 branch `port/1.21.1` at `e06344aa053db4f2886f3b0dc68dfb46241bcad0`, and 1.20.1 reference branch `port/1.20.1` at `e7ff95df387db09b63412a39a06eab4969488330`. These identify inspected snapshots, **not a reproducible clean input tree**. At review time there were release 2.1.1 property/metadata/changelog edits, ambient command/runtime edits, build-file edits, docs/README and fish-in-world edits, ideas, untracked catch-up documents, and port runtime/scratch files. Preserve all unrelated changes. Sibling Git metadata access was restricted for parts of the research; exact dirty state and Gelatin HEADs must be captured at kickoff.

Read the latest checkpoints in [backport progress](D:/GitHub/fishtastic/docs/backport-catchup-progress.md), then its linked [plan](D:/GitHub/fishtastic/docs/backport-catchup-plan.md), [feature ledger](D:/GitHub/fishtastic/docs/backport-catchup/features.md) and [commit ledger](D:/GitHub/fishtastic/docs/backport-catchup/source-commit-ledger.json). Recent checkpoints supersede stale introductory text: the ledger records parity through pinned `48fffdfb`, later onboarding changes, and six 2.1.1 submission results. That establishes context only; new Forge support requires its own baseline and ledger.

At kickoff record each repository/worktree's absolute path, branch, full HEAD, staged/unstaged/untracked inventory, relevant local patch hashes, version properties, wrapper properties and resolved dependency coordinates. Never reset or stash others' work to create a convenient baseline. Use suitable isolated worktrees when necessary, deliberately selecting the starting state and carrying only authorized prerequisites. Freeze the product feature snapshot being ported; if ongoing parity work changes it, update the manifest and explicitly account for the delta before continuing.

### 2.2 Runtime and build matrix

| Repository / Minecraft | Inspected build | Runtime / namespace | Proposed Forge route |
|---|---|---|---|
| Fishtastic 26.1.2 | `loom-no-remap` 1.14-SNAPSHOT; Architectury 3.5-SNAPSHOT; Shadow 8.3.6; Gradle 9.2.1 | Java 25; unobfuscated official names; no mappings dependency | Pinned `loom-no-remap` 1.17; Forge 64 |
| Fishtastic 1.21.1 | regular Loom 1.17-SNAPSHOT; Architectury 3.5-SNAPSHOT; Shadow 8.3.6; Gradle 9.5.0 | Java 21; official Mojang development mappings; Forge 52 Mojang runtime namespace | Pinned regular Loom 1.17; Forge 52; retain branch remap pipeline |
| Gelatin 26.1.2 | `loom-no-remap` 1.14-SNAPSHOT; Architectury 3.5-SNAPSHOT; Shadow 8.3.6 | Java 25; unobfuscated official names | Same proven Loom family as consumer; capture its own wrapper |
| Gelatin 1.21.1 | regular Loom 1.11-SNAPSHOT; Architectury 3.4-SNAPSHOT; Shadow 8.3.6 | Java 21; official Mojang development mappings | Upgrade to compatible pinned Loom 1.17 and validate Architectury pairing; capture its own wrapper |

The Gradle version used to run the daemon and Java bytecode target both matter. The 26.1.2 Fishtastic checkout has a project-local Java 25 path; do not copy that path into other machines or the Java 21 branch. Verify `--version` in each actual checkout. Do not apply one wrapper upgrade to all projects without evidence. These facts come from [Fishtastic root](D:/GitHub/fishtastic/build.gradle), [1.21.1 root](D:/GitHub/fishtastic-worktrees/mc-1.21.1/build.gradle), [Gelatin root](D:/GitHub/gelatin-ui/build.gradle), [Gelatin 1.21.1 root](D:/GitHub/gelatin-ui-worktrees/mc-1.21.1/build.gradle) and their wrapper/property files.

### 2.3 Dependency pins to replace with a resolved manifest

| Input | 26.1.2 inspected / candidate | 1.21.1 inspected / candidate | Required treatment |
|---|---|---|---|
| Forge | Latest 64.1.5; recommended 64.1.0 | Latest 52.1.20; recommended 52.1.0 | As checked 2026-10-18; start with explicit latest candidate pin, then test any advertised minimum |
| Fishtastic version | 2.1.1 | 2.1.1 | Dirty release state; choose later implementation/release version deliberately |
| Required Gelatin | `1.0.37+26.1.2` | `1.0.37+1.21.1` | Existing versions are not proof of Forge artifacts; select a new validated publication version |
| Fishtastic Fabric Loader / API | 0.19.1 / 0.155.2+26.1.2 | 0.17.2 / 0.116.7+1.21.1 | Preserve during loader work unless compatibility proof requires a separate change |
| Fishtastic NeoForge | 26.1.2.100 | 21.1.209 | Preserve existing-loader baseline |
| Gelatin Fabric Loader / API | 0.19.1 / 0.145.4+26.1.2 | 0.17.2 / 0.116.7+1.21.1 | Library differs from consumer on 26.1.2; assess independently |
| Gelatin NeoForge | 26.1.2.4-beta | 21.1.209 | Inspected, not a recommendation to downgrade consumer |
| JEI | Consumer property 29.33.0.87 | Consumer property 19.18.10.218 | Verify actual Forge Maven artifacts before adding; optional integration gate |
| CoolCam | 0.1.0+26.1.2 property | No matching property inspected | Forge artifact availability unresolved; exclude from minimal proof and verify before enabling |
| MixinExtras | Distribution strategy unresolved | Distribution strategy unresolved | Check selected Forge runtime and dependency graph before shading/bootstrap choices |

Forge versions are from [official 26.1.2 downloads](https://files.minecraftforge.net/net/minecraftforge/forge/index_26.1.2.html) and [official 1.21.1 downloads](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.21.1.html). Library/consumer values are local inspection of their `gradle.properties`. Do not infer Forge minimums from recommended downloads or copy unbounded Minecraft ranges into new metadata. Advertise only the tested Minecraft line, Java floor, Forge floor and required Gelatin range.

## 3. Prove one build family before implementing adapters

### 3.1 Loom 1.17 is the preferred route

The [Loom 1.17 release](https://github.com/architectury/architectury-loom/releases/tag/1.17) explicitly lists Forge 1.21.x/26.x repairs. [PR 343](https://github.com/architectury/architectury-loom/pull/343) fixes newer Forge development bootstrap/classpath issues; [PR 349](https://github.com/architectury/architectury-loom/pull/349) completes unobfuscated Forge merge/manifest handling and adds Forge261/262 integration coverage. The actual [patch provider](https://github.com/architectury/architectury-loom/blob/dev/1.17/src/main/java/dev/architectury/loom/forge/minecraft/MinecraftPatchedProvider.java#L234) and [compile configuration](https://github.com/architectury/architectury-loom/blob/dev/1.17/src/main/java/net/fabricmc/loom/configuration/CompileConfiguration.java#L209) implement this path. This is stronger evidence than an enum or generic supported-loader claim, but does not verify Shadow, the local wrappers or Fishtastic's jar pipeline.

Select a reproducible resolved 1.17 artifact and record its checksum and upstream source commit containing the fixes. Cached 1.17.491/1.17.493 artifacts are leads, not validated pins. Upgrade Gelatin and Fishtastic consistently where needed. First reproduce existing Fabric/NeoForge configuration, build and smoke behavior after the upgrade; keep failures attributable to a small change.

**Do not import Forge 47 namespace assumptions.** Loom's [ForgeProvider](https://github.com/architectury/architectury-loom/blob/dev/1.17/src/main/java/dev/architectury/loom/forge/dependency/ForgeProvider.java#L54) selects Mojang production names for Forge majors at least 50. Forge 52 therefore differs from the 1.20.1 Forge 47 reference. Retain 1.21.1's regular Loom/remap pipeline and verify its final namespace; do not copy old SRG refmap injection, manual MixinExtras bootstrap or JEI filtering without reproducing the need.

### 3.2 Build and artifact file inventory

Apply the following to both target branches and both repositories where applicable. Existing source examples are [Fishtastic NeoForge build](D:/GitHub/fishtastic/neoforge/build.gradle), [1.21.1 NeoForge build](D:/GitHub/fishtastic-worktrees/mc-1.21.1/neoforge/build.gradle), [1.20.1 Forge build](D:/GitHub/fishtastic-worktrees/mc-1.20.1/forge/build.gradle) and [Gelatin Forge reference](D:/GitHub/gelatin-ui-worktrees/mc-1.20.1/forge/build.gradle).

| Files / groups | Implementation change |
|---|---|
| Root `settings.gradle`, `gradle.properties`, `build.gradle` | Add `forge`, enabled platform, pinned Forge coordinate/ranges, proven Loom pin, actual final outgoing artifact and build/publication dependencies |
| New `forge/build.gradle`, optional module `gradle.properties` | `architectury.forge()`, development/transformed common inputs, fishsim where applicable, resources, runs and dedicated testmod roots |
| New Forge `META-INF/mods.toml` | Correct Forge dependency syntax, expanded version/Java/Minecraft/Gelatin fields, sided requirements; inspect expansion in final jar |
| Forge mixin JSON and manifest | Register common plus loader mixins in dev and production; correct Java 21/25 compatibility and unique refmaps where used |
| Common access widener / Forge `META-INF/accesstransformer.cfg` | Convert/package on the correct final task; retain explicit library AT entries; test widened accesses |
| Platform outgoing `apiElements` / `runtimeElements`, Maven publication | Redirect to distributable; no raw or dev-shadow artifacts presented as runtime releases |
| Forge `src/testmod` / run resources and root GameTest helper | Loader discovery wrappers and fresh-world runs, main/testmod/fishsim class-root association |
| Root CurseForge/Central aggregation | Include Forge and correct dependency/loader/Java tags, source/javadoc/signing artifacts as applicable; no publication during implementation probes |

**26.1.2:** follow the current processed-raw-jar base plus transformed-common/fishsim shadow packaging. The distributable is the processed `shadowJar` output. Loom's [AW conversion API](https://github.com/architectury/architectury-loom/blob/dev/1.17/src/main/java/net/fabricmc/loom/api/ForgeExtensionAPI.java#L82) accepts an explicit jar task and jar-relative AW paths. Target final `shadowJar` after common's AW is present, and prove ordering with the actual task graph and jar contents; a legacy switch converting the earlier raw jar can miss the bundled AW.

**1.21.1:** use named common development input, `transformProductionForge` for the bundled common artifact, dev-shadow intermediate and `remapJar` final distributable. On obfuscated versions, explicit AW conversion must target the remap task. Preserve the current disabled `common:remapJar` guard: the branch documents a Container/RecipeInput named-to-intermediary bridge-method conflict. Keep legacy Mixin annotation processing and unique module refmaps until a separately proven replacement exists. A regular remap task does not imply an SRG runtime namespace.

Architectury's [production transforms](https://docs.architectury.dev/plugin/introduction/) handle `@ExpectPlatform` and Fabric environment annotations independently of the runtime Architectury API. Inspect final bytecode for transformed dispatch and side annotations. Compiling common and shading it untransformed is insufficient.

### 3.3 Minimal proof and bounded fallback

The probe must load one transformed platform call, exercise a widened Minecraft access, activate a common Mixin, and run on a dedicated server and client. Use a minimal probe mod or a deliberately limited skeleton, rather than requiring the unfinished full Fishtastic entrypoint to initialize. Inspect and install its final jar in a clean Forge runtime as well as launching in development. Omit optional JEI/CoolCam. This catches transformation and metadata failures before implementing all services; the full Fishtastic artifact repeats the gate after its adapters exist.

If the probe fails, classify the failure first: project packaging/metadata, dependency mismatch, environment/JDK, or reproducible Loom defect. Verify the selected Loom build actually contains the cited fixes. Allow a bounded corrected pin/prototype before changing toolchains. Retain the minimal reproduction and logs.

Only a demonstrated Loom blocker justifies a ForgeGradle fallback. For 26.1.2, the [official MDK](https://github.com/MinecraftForge/MinecraftForge/blob/26.1.2/mdk/build.gradle) uses ForgeGradle `[7.0.17,8)`, Java 25, the new dependency DSL and eventbus-validator processing. First run its own wrapper/JDK as an environment discriminator. If MDK also fails, diagnose that environment rather than restructuring the project. If MDK works and Loom fails reproducibly, prefer an isolated included/standalone build over mixing ForgeGradle and Loom in the same project; the root currently applies Loom to mod subprojects. For a 1.21.1 fallback, use that version's actual MDK rather than transplanting the 26.1.2 template.

A fallback must explicitly replace common transformation, environment stripping/rewriting, mapped inputs, access conversion, mixin activation, fishsim bundling, outgoing variants and testmod class roots. An exported common jar or controlled shared sources are options, each with API-leak/duplicate-compilation risk. Do not consume 1.21.1's disabled common remapJar or a Fabric intermediary artifact as a Forge runtime input. **Stop broad port implementation until a build route passes both development and installed-artifact gates.**

## 4. Port Gelatin UI as a required dependency

### 4.1 Preserve the public API and fix registration timing

Both target Gelatin branches contain common/Fabric/NeoForge only and use version 1.0.37. Add a Forge module without renaming common packages or changing consumers' menu imports. The platform seam is [SidedRegistrationHelper](D:/GitHub/gelatin-ui/common/src/main/java/io/github/currenj/gelatinui/registration/menu/SidedRegistrationHelper.java): supply the exact `registration/menu/forge/SidedRegistrationHelperImpl` target and a Forge `MenuRegistrationHandler` implementation following the actual package/signature conventions in the 1.20.1 tree.

The important defect to avoid is lifecycle timing. Current target [GelatinUi.init](D:/GitHub/gelatin-ui/common/src/main/java/io/github/currenj/gelatinui/GelatinUi.java) registers examples and immediately fires the menu event. The [1.20.1 Forge entrypoint](D:/GitHub/gelatin-ui-worktrees/mc-1.20.1/forge/src/main/java/io/github/currenj/gelatinui/forge/GelatinUiModForge.java) deliberately separates initialization from registry-time firing so dependent mod constructors can add listeners before DeferredRegister processes menus. Copy that lifecycle concept, not old bus syntax. If the selected Forge version cannot guarantee callback ordering, perform registration through one controlled registry callback. Preserve existing public `init()` callers using an additive lifecycle API or migrate every loader entrypoint together with regression proof.

The first acceptance fixture must include Fishtastic or an external consumer. Assert non-null, registered menu types exactly once for Fishtastic's `fishtastic`, `leaderboard`, `quest_log` and `fish_encyclopedia` menu listener IDs before screen factories resolve them. Test consumer-before-library and consumer-after-library construction ordering in an isolated fixture. A library-only title screen proves neither case.

### 4.2 Screens, tooltips, codecs and PiP

Register Forge screens in an appropriate client setup callback and enqueue `MenuScreens.register`, consistent with [Forge's screen guidance](https://docs.minecraftforge.net/en/1.21.x/gui/screens/) and the old Forge adapter. Do not assume NeoForge's `RegisterMenuScreensEvent` exists unchanged. Keep client classes out of dedicated-server entrypoint resolution.

Gelatin uses vanilla `ServerPlayer.openMenu(SimpleMenuProvider)` without extra opening data; no custom library channel was found. Retain that model, supported by [Forge menu documentation](https://docs.minecraftforge.net/en/1.21.x/gui/menus/). Fishtastic still owns its request/response synchronization. Preserve tooltip factories, hover/chat codecs and the explicit AT entries exposing `HoverEvent$Action.UNSAFE_CODEC` and `CODEC`. Verify codec behavior on the dedicated server as well as tooltips on client. Port the actual per-version mixins: 1.21.1 includes client GuiGraphics and common HoverEventAction behavior; 26.1.2 includes GuiGraphics/GuiRenderer behavior.

| GUI path | Preserve / implement | Proof |
|---|---|---|
| 1.21.1 all loaders | Existing immediate GUI rendering; no new PiP subsystem | Items, depth/z ordering, clipping, inputs, scroll and screen transitions |
| 26.1.2 Fabric | Existing renderer-instance list injection in GameRenderer mixin | Remains active once; no regression |
| 26.1.2 NeoForge | Existing PiP renderer factory event | Never add raw instance injection too |
| 26.1.2 Forge | Inspect pinned GameRenderer/GuiRenderer patches; choose matching factory hook if present, otherwise prove a Forge-specific vanilla-list injection | Renderer reports registered; visibly high-resolution enlarged items, several different items/frame, clipping, reload and disposal |

Forge 26.1.2's exact PiP integration is unresolved. NeoForge changes the renderer-list contract, so blindly using Fabric's raw instance can cause a class-cast failure; blindly using NeoForge's event assumes an API not established on Forge. The [HiResItems guard](D:/GitHub/gelatin-ui/common/src/main/java/io/github/currenj/gelatinui/gui/minecraft/hires/HiResItems.java) can decline rendering, so ordinary item visibility is insufficient evidence. Record the selected Forge hook and its pinned source before implementing it.

### 4.3 Library release prerequisite

Build transformed common and Forge artifacts under a new exact Gelatin version, then use local test publication to integrate Fishtastic. Validate the library alone and with the consumer on both physical sides. Include correct final Forge runtime artifact, sources/javadocs/signatures as applicable, Maven component metadata and Central aggregation. Do not let source-javadoc or signing configuration inherited from another machine obscure whether the runtime jar works. Remote publication is a later authorized release action; this planning task publishes nothing.

## 5. Implement Fishtastic's server and shared seams

### 5.1 Inventory every platform target and lifecycle hook

The canonical modding guide is the first reference for implementation: [setup](D:/GitHub/modding-guide/01-project-setup.md), [registration](D:/GitHub/modding-guide/02-registration.md), [blocks/entities](D:/GitHub/modding-guide/03-blocks-and-block-entities.md), [items/components](D:/GitHub/modding-guide/04-items-and-data-components.md), [recipes](D:/GitHub/modding-guide/05-recipes.md), [datagen](D:/GitHub/modding-guide/06-datagen.md), [mixins](D:/GitHub/modding-guide/07-mixins.md), [networking](D:/GitHub/modding-guide/08-networking.md), and [GameTests](D:/GitHub/modding-guide/18-game-tests.md). Follow checked-in signatures where illustrative guide examples differ. For example, RegistrationApiSided uses an ExpectPlatform getter, not an injected setter.

All Java paths below are relative to each branch's `src/main/java/grill24/fishtastic` inside the named module. New paths are proposed, not existing implementations.

| Existing common seam / source | Forge implementation or work group |
|---|---|
| `architectury/RegistrationApiSided.java`, `IRegistrationApi` and registration callers | `forge/.../architectury/forge/RegistrationApiSidedImpl.java`, `ForgeRegistrationApi.java`; central Forge registry/entrypoint classes |
| `companion/CompanionStorage.java`, `Companions.java`, party codec | `forge/.../companion/forge/CompanionStorageImpl.java`; SavedData, explicit sync payload/client cache |
| `config/FishtasticServerConfig.java` | `forge/.../config/forge/FishtasticServerConfigImpl.java` |
| `client/FishtasticClientConfig.java` | `forge/.../client/forge/FishtasticClientConfigImpl.java` |
| `env/DevEnvironmentCheck.java` | `forge/.../env/forge/DevEnvironmentCheckImpl.java` |
| `server/GhostConnection.java` | `forge/.../server/forge/GhostConnectionImpl.java` |
| `mcp/McpConfig.java` where present | Forge development target only; preserve production exclusion |
| `network/FishtasticPackets.java` registrar/context | `forge/.../architectury/forge/ForgePacketRegistrar.java`; channel/context wrapper |
| Direct common custom-payload sends | New shared transport facade plus exact `.fabric`, `.neoforge`, `.forge` targets |
| NeoForge `FishtasticNeoForge`, commands and config | New Forge entrypoint, version-correct bus wiring, server tick, datapack sync, commands, config |

For Forge 26.1.2, [MinecraftForge.EVENT_BUS](https://github.com/MinecraftForge/MinecraftForge/blob/26.1.2/src/main/java/net/minecraftforge/common/MinecraftForge.java#L27) is a migration helper around the newer BusGroup system; mod events use the mod group and some events expose static BUS instances. Inspect pinned APIs and eventbus-validator setup. A global replacement of NeoForge IEventBus imports is not a lifecycle implementation.

Port post-server-tick handling, commands, startup/common setup and dispenser behavior, join/datapack synchronization, leave/stop cleanup and configuration directory resolution. Preserve three config responsibilities separately: loader tank-part blacklists/model-path overrides, common server properties/backups, and common client preferences/defaults. Resolve the actual Forge equivalent of NeoForge STARTUP config instead of substituting an assumed enum. Prove GhostConnection behavior in ghost/scene tests; choose a no-op only with evidence it satisfies the contract.

### 5.2 Registration must return real holders

Cover nine static registries: **items, blocks, block entity types, data component types, creative tabs, sound events, particle types, menus and recipe serializers**. Data components must exist before items use defaults; block items and block entity valid-block arrays must remain lazy until registrations are available. On 26.1.2 retain explicit `Properties.setId(ResourceKey...)` requirements. Enqueue dispenser/global state mutations in common setup after registration.

Forge [RegistryObject](https://github.com/MinecraftForge/MinecraftForge/blob/1.21.1/src/main/java/net/minecraftforge/registries/RegistryObject.java#L23) is a Supplier, not a vanilla Holder. Do not cast it or demand its optional holder during mod construction. The [1.20.1 Forge adapter](D:/GitHub/fishtastic-worktrees/mc-1.20.1/forge/src/main/java/grill24/fishtastic/architectury/forge/ForgeRegistrationApi.java) registers common groups during their matching RegisterEvent, returns the registry's real Holder.Reference and queues block items until the item event. Adapt this proven behavioral strategy first. Fail clearly on mismatched event keys rather than silently skipping content.

Register all seven datapack registries with disk **and network** codecs: ITEM_EFFECT, FISH_PROFILE, TEMPERAMENT, QUEST, SHOP_ENTRY, FISH_ENCYCLOPEDIA_ENTRY and COSMETIC_STRUCTURE. Preserve cross-registry codec initialization order. The exact Forge 26.1.2 datapack registration signature remains a source-and-compile gate; NeoForge's signature is a reference, not established Forge compatibility. Prove custom datapack values reach a joining client and remain coherent after `/reload`; the existing one-player sync path does not automatically handle a null/all-player reload event.

### 5.3 Receive registration and send encoding are separate contracts

The inspected [FishtasticPackets](D:/GitHub/fishtastic/common/src/main/java/grill24/fishtastic/network/FishtasticPackets.java) registers **20 C2S and 16 S2C payloads** using vanilla `CustomPacketPayload.Type<T>`, `StreamCodec<? super RegistryFriendlyByteBuf,T>`, handlers and direction. Recount on the pinned branch, and update the separate Fabric codec-only S2C list whenever packets are added. There is no active generated NetworkApiSided implementation in the reviewed newer-version source.

Forge provides payload-oriented channels on [1.21.1](https://github.com/MinecraftForge/MinecraftForge/blob/1.21.1/src/main/java/net/minecraftforge/network/PayloadChannel.java#L106) and [26.1.2](https://github.com/MinecraftForge/MinecraftForge/blob/26.1.2/src/main/java/net/minecraftforge/network/PayloadChannel.java#L106). `ChannelBuilder.payloadChannel()` accepts typed payloads/codecs. PayloadChannel is package-private; retain the built public `Channel<CustomPacketPayload>` type rather than importing its implementation. Resolve codec variance with a bounded adapter, not broad raw casts. Use connection-associated registry buffers after registry synchronization; an empty/global registry lookup is not a substitute for ItemStack decoding context.

Choose one handler threading contract and apply it consistently. Forge `flow.add` invokes on the network thread; the wrapper can delegate common `enqueueWork` to the Forge context and mark handled after dispatch. Alternatively, `addMain` selects main-thread dispatch and wrapper `enqueueWork` must avoid another scheduling delay. Existing handlers generally enqueue their own work. Assert the actual game thread, correct sender and handling completion in the prototype. See [PayloadFlow](https://github.com/MinecraftForge/MinecraftForge/blob/26.1.2/src/main/java/net/minecraftforge/network/payload/PayloadFlow.java).

Forge [Channel.send](https://github.com/MinecraftForge/MinecraftForge/blob/26.1.2/src/main/java/net/minecraftforge/network/Channel.java#L53) performs channel encoding and protocol/direction checks. Directly sending `ClientboundCustomPayloadPacket(record)` bypasses that encoder. Add a common send-to-player/send-to-server seam, plus broadcast only where needed. Fabric/NeoForge adapters can retain their current vanilla sending behavior; Forge must use channel sends. Search all common main/testmod sources for `ClientboundCustomPayloadPacket`, `ServerboundCustomPayloadPacket`, payload sends and broadcast helpers, then classify each occurrence.

The direct-send checklist includes Quest/Tutorial/Encyclopedia/CosmeticCapture/config-toggle helpers, Bond/Rename/TakePile/RequestLeaderboard responses, FishingMinigameManager, AmbientContextServer, CompanionSceneManager, client screens/tutorials, CompanionBondInput, FishtasticKeyBinds, FishingMinigameClientHandler, AmbientContextClient and RenderSelfTest. Update development scene code too, or its apparent packet failures will obscure the production result. Leave unrelated vanilla protocol sends alone.

Start with strict matching protocol and required peer policy. Existing NeoForge `optional()` registration does not establish that vanilla or missing-mod Forge peers are safe with custom content and synced registries. Cross-loader wire compatibility requires separate proof and is not promised. If the pinned payload API fails the bounded prototype, evaluate SimpleChannel with explicit per-payload classes or a typed envelope. Do not use `CustomPacketPayload.class` for every class-dispatched message. The old 1.20.1 BufCodec/envelope stack solves older APIs and should not be the default here.

### 5.4 Persist companion parties and synchronize lifecycle explicitly

NeoForge's serializable copy-on-death attachment supplies persistence and owner/tracker synchronization that Forge must implement deliberately. Preserve the three-slot party schema, item components, mutation API, bonds/names/unlocks and empty-party omission semantics. 26.1.2 uses MAP_CODEC while 1.21.1 uses CODEC; adapt version-specific SavedData APIs. Do not revive the retired `{stack:...}` format accidentally.

Use [1.20.1 SavedData](D:/GitHub/fishtastic-worktrees/mc-1.20.1/common/src/main/java/grill24/fishtastic/companion/CompanionPartySavedData.java) and [party sync](D:/GitHub/fishtastic-worktrees/mc-1.20.1/common/src/main/java/grill24/fishtastic/network/CompanionPartySyncPacket.java) as a behavioral reference: server-owned UUID-to-party map, mutation marks dirty, explicit sync, and client UUID cache. Choose one server-wide save owner, preferably the same world scope as that reference, to avoid dimension-local duplicate parties. Server persistence survives player object replacement; client visibility still needs join/late-join/re-track/dimension handling and empty-party removal messages. Owner/tracking sync should meet existing visibility behavior; a simple all-online broadcast is an acceptable first correctness prototype if its cost and final visibility policy are recorded.

Clear any new party cache on disconnect/world replacement alongside quests, tutorials, encyclopedia, cosmetic capture, tank flocks and ambient state. Joining clears effect/tank/ambient caches; synchronized tag changes invalidate dependent effects/cosmetic obstacles/ambient caches. Test two worlds with the same player identity to expose cache leakage. A capability-based alternative is possible, but must prove clone/death/tracking/invalidation behavior and does not automatically migrate NeoForge attachment storage.

Before release choose and document one world-switch policy: supported explicit migration with tested backup/round-trip behavior, or unsupported loader-switch companion persistence with clear documentation. Same-loader existing shared world data must still load/save safely. Neither policy permits silent loss in an advertised compatible world.

## 6. Treat client rendering as two distinct ports

### 6.1 Tank models require version-specific adapters

| Area | 1.21.1 approach | 26.1.2 approach |
|---|---|---|
| Geometry | Reuse this branch's loader-neutral FishTankGeometry/TankCosmeticMesh | Inspect current material/geometry/cache structures; retain branch behavior |
| Loader wrapper | Adapt old Forge baked-model wrapper to current 1.21.1 APIs and model-data updates | Design against actual Forge geometry-loader/baking/item hooks; no assumed NeoForge class substitution |
| Render layers | Preserve solid/translucent routing, interior-light-adjusted quads, item overrides | Prove material flags, glass/water ordering, arbitrary frame/glass and item model path |
| World fish | Adapt RenderLevelStageEvent integration and branch immediate rendering | Prototype frame-pass or a narrowly scoped Forge-specific vanilla submit/extract mixin |

The 26.1.2 reference inventory includes `FishTankModel`, `FishTankBlockStateModel`, `FishTankBakedModel`, `FishTankItemModel`, `FishTankModelData`, `BlockstateModelReloadListener`, `BlockModelPathResolver`, `FishTankPartBlacklistChecker` and `FishTankBlockEntityNeoForge`. Inventory Forge equivalents under `forge/.../fishtank` plus its tank block entity; preserve shared simulation logic. See [current tank model](D:/GitHub/fishtastic/neoforge/src/main/java/grill24/fishtastic/neoforge/fishtank/FishTankBakedModel.java) and [1.21.1 wrapper](D:/GitHub/fishtastic-worktrees/mc-1.21.1/neoforge/src/main/java/grill24/fishtastic/neoforge/fishtank/FishTankBakedModel.java).

NeoForge's DynamicBlockStateModel, RegisterBlockStateModels and SubmitCustomGeometryEvent do not have an established direct Forge equivalent. Reviewed [Forge ModelEvent](https://github.com/MinecraftForge/MinecraftForge/blob/26.1.2/src/main/java/net/minecraftforge/client/event/ModelEvent.java#L99) exposes geometry loaders/baking-result hooks. Select matching Forge resource JSON; copying NeoForge blockstates unchanged can select a nonexistent loader. Prove loader-specific model data availability, update invalidation, item-model codecs and reload dependency ordering before implementing every tank variant.

The first scene must contain an arbitrary solid frame, translucent glass, sand, water and one cosmetic. It must work as a placed tank, GUI item and held item; update after server packets, neighboring tank changes, break/place, chunk reload and F3+T. Then expand to all shapes/permutations, connected tanks/open faces, diagonal fill, multi-cell/animated cosmetics, light/water toggles, particles and frustum boundaries. Screenshots must be inspected; absence of crashes does not prove glass or lighting parity.

### 6.2 World fish need extraction and submission proof on 26.1.2

Current NeoForge submits both ambient fish and companions through SubmitNodeCollector. Forge's [AddFramePassEvent](https://github.com/MinecraftForge/MinecraftForge/blob/26.1.2/src/main/java/net/minecraftforge/client/event/AddFramePassEvent.java) and [FramePassManager](https://github.com/MinecraftForge/MinecraftForge/blob/26.1.2/src/main/java/net/minecraftforge/client/FramePassManager.java#L38) provide a candidate route, not a demonstrated collector replacement. Extract immutable render state in the extraction phase; consume it in execute; bind an appropriate target with main-target fallback where required. Verify frame ordering and resources in each applicable graphics mode. If this cannot preserve common renderer behavior, prototype a narrow Forge-specific mixin into the correct vanilla extraction/submission point. Record why the chosen seam is stable against pinned Forge patches.

Pass only when ambient fish and all companion locomotion classes render in and out of water, first/third person, near other players, after dimensions/reconnect/disable toggles, without duplicate submissions or stale state. Preserve current client tick ordering: ambient updates outside the level/pause guard; companion/flock/tutorial/input updates within it where the branch currently does so.

### 6.3 Complete the remaining client inventory

Use the entire [26.1.2 NeoForge client entrypoint](D:/GitHub/fishtastic/neoforge/src/main/java/grill24/fishtastic/neoforge/FishtasticNeoForgeClient.java) and the matching [1.21.1 entrypoint](D:/GitHub/fishtastic-worktrees/mc-1.21.1/neoforge/src/main/java/grill24/fishtastic/neoforge/FishtasticNeoForgeClient.java) as the checklist. Cover four direct menu screens (companions, tank assembly, electric organizer, tank browser), Gelatin menus/screens, two tooltip component factories, eleven particle providers and five key mappings as counted in the inspected source. Recount after pinning. Include client commands, shader/item property or item-model registration as appropriate, quality glints/outlines in GUI/hand/dropped/frame contexts, item activation animation, pre/post GUI tutorial order, screen tutorials, quest banners, cosmetic capture/placement gizmos, and cache invalidation.

Audit every common and loader Mixin against Forge-patched methods. 26.1.2 currently has 26 client and five shared common entries, JAVA_25 and `defaultRequire=1`; GuiRendererExecuteDrawMixin uses CAPTURE_FAILHARD local capture. Keep failures visible. Do not lower injection requirements or suppress failures simply to reach a title screen. Dedicated-server boot must load shared logic without resolving client-only classes; common Fabric environment annotations also require production transformation.

## 7. Execute small work packages with explicit gates

Each package is performed per version unless the row states otherwise. Record start/result/next operation before moving on. A package closes only when its exit evidence is reviewed, not when code is written.

| ID | Depends on | Files / work | Tasks and exit gate |
|---|---|---|---|
| F00 Baseline | None | This guide/progress ledger; all four source trees; canonical guide topics | Pin source/dirty/dependency/runtime manifests; inventory existing tests/scenes; reserve isolated evidence/run directories. Stop if the intended feature baseline cannot be identified without overwriting other work. |
| F01 Toolchain | F00 | Both repos' root plugin/build/properties/wrappers as needed | Pin compatible Loom 1.17; reproduce Fabric/NeoForge builds/smokes after any upgrade. Record resolved plugin/source hashes. Stop on regression before mixing in large Forge adapters. |
| F02 Forge artifact probe | F01 | Minimal Forge modules, metadata, transforms, AW/AT, mixins and runs | Both versions: development client/server and clean installed final jar; one platform call, widened access and Mixin behavior. Diagnose bounded failure/fallback as section 3. |
| G01 Gelatin registration | F02 | Gelatin Forge entrypoints/helper/handler; common lifecycle; existing loader entrypoints | Construction-order fixture and consumer menu IDs resolve exactly once; server-opened screen works; no API break or Fabric/NeoForge lifecycle regression. |
| G02 Gelatin rendering | G01 | Forge screens/tooltips/mixins/AT; 26.1.2 PiP | Tooltip/chat codec checks; UI inputs/clipping/items; pinned Forge PiP decision and enlarged-item proof; dedicated-server class isolation. |
| G03 Local dependency | G02 | Gelatin publications/outgoing variants; Fishtastic dependency configuration | Local exact-version final common/Forge artifacts integrate; jar/metadata checks pass; reproducible library+minimal-consumer launches. Full Fishtastic launch follows F03/F04; no public publish. |
| F03 Registration/server | G03 | Forge registration API, central registries, entrypoint, configs, commands, GhostConnection and platform targets | Nine static/seven synced registries, correct holders/components, config defaults/paths, command/tick/dispenser behavior, join/reload registry proof, dedicated server. |
| F04 Transport | F03 | Common transport facade; three loader targets; Forge registrar/context; all send sites | Typed C2S + registry-bearing S2C prototype first; then complete payload/send inventory. Thread/sender/direction/protocol/invalid-input tests; Fabric/NeoForge transport regressions. |
| F05 Companion persistence | F04 | Forge storage/SavedData/cache/sync and lifecycle hooks | Three-slot party mutation, owner/nonowner visibility, empty removal, tracking/dimension/death/rejoin/restart/world-switch cache checks; explicit migration policy. |
| F06 Tank render prototype | F03 and G03 | Forge tank model/BE/resources; item rendering | One placed/held/inventory tank complete with material layers/cosmetic/light/update/reload proof. Run early on 26.1.2; stop broad client port if model seam cannot be established. |
| F07 World render prototype | F04 and F05 | Forge client world hooks; shared renderer adapters only if necessary | 1.21.1 event path and 26.1.2 extract/submit decision separately proven; ambient and companion visuals, lifecycle and graphics modes. |
| F08 Client completion | F06 and F07 | Forge client entrypoint, screens/particles/keys/effects/tutorials/mixins | Full section 6 inventory; tank variant scenes; menu/quality/tutorial/encyclopedia/shop/leaderboard acceptance. |
| F09 Harness integration | F03; run final suites after F08 | Forge testmod/run config, world helper, validation/packaging/two-client scripts | Correct loader test discovery, isolated worlds/ports and Forge-aware scripts; compile testmod without NeoForge stub leakage. Can be developed incrementally after F03. |
| F10 Production parity | F04-F09 | Final packaged artifacts, evidence and regression suites | Complete validation matrix below; clean installation and two-client persistence tests; minimum/latest Forge policy tested; all existing target-loader regressions green. |
| F11 Release readiness | F10 | Gelatin/Fishtastic metadata/publications/changelog/support docs | Verify final jars/hashes, dependency-negative checks and exact tested ranges; stage release instructions. Gelatin remotely available and cleanly resolvable before Fishtastic publication, only when separately authorized. |

Recommended order: complete F00-F02 for both lines, then G01-G03/F03-F10 on 1.21.1 while scheduling 26.1.2 G02/F06/F07 prototypes early. Advance the 26.1.2 full port only once its rendering seams are established. Keep packages small enough that each shared change can be regressed on both existing loaders. Do not run compiles/tests while clients hold shared development outputs.

### 7.1 Risks and de-risk milestones

| Risk | Relative size / uncertainty | Milestone that reduces it |
|---|---|---|
| Loom/Shadow/wrapper/final transformation | Medium implementation, high impact | F02 installed artifact on both physical sides |
| Gelatin lifecycle | Small surface, high correctness impact | G01 external consumer under both construction orders |
| Gelatin 26.1.2 PiP | Medium, API uncertain | G02 active high-resolution renderer proof |
| Registry/holder/component ordering | Medium, immediate startup failures likely | F03 real-holder/component/datapack tests |
| Payload send + receive/main thread | Medium; broad send-site inventory | F04 dedicated registry-bearing round trip and complete routing audit |
| Companion save/sync | Medium; high lifecycle breadth | F05 two clients plus server restart and changed world |
| 1.21.1 tank/client adapters | Large inventory, useful older reference | F06/F08 visual coverage |
| 26.1.2 tank/world rendering | Largest uncertainty | F06 and F07 before full implementation |
| Existing-loader regressions | Cross-cutting | F01 and repeated focused shared-surface checks, F10 full matrix |
| Published dependency correctness | Small code surface, release-blocking | G03 local exact artifact then F11 clean remote resolution |

Do not give calendar commitments from source review alone. Estimate the remaining work after F02, G02 and 26.1.2 F06/F07 using actual prototype results. A failed prototype is useful evidence if it isolates a contract; it is not grounds to silently remove the feature from the promised Forge scope.

## 8. Validation must prove features and actual shipped jars

### 8.1 Test/tooling changes are part of the port

The existing harnesses explicitly support only Fabric/NeoForge. Extend [run-pre-backport-validation.ps1](D:/GitHub/fishtastic/docs/run-pre-backport-validation.ps1), [twoclient.init.gradle](D:/GitHub/fishtastic/docs/fish-companions-twoclient/twoclient.init.gradle), [check-cleanup-packaging.ps1](D:/GitHub/fishtastic/docs/check-cleanup-packaging.ps1) and any calling scripts with Forge loader acceptance, actual task names, separate run directories and unique port allocation. Current two-client ports 25600/25601 must not collide with Forge sessions. Preserve checks for process exit, expected scene prefixes, PASS/FAIL labels and exactly one completion; extend scene expectations deliberately.

Reuse [gametest-world.gradle](D:/GitHub/fishtastic/gradle/gametest-world.gradle): fresh UUID worlds each run, failed worlds retained, success cleanup only after validated in-root path resolution. Do not add duplicate `--universe` arguments or delete historical worlds. Forge registration/discovery must expose loader-neutral common test methods through genuine Forge wrappers. In 1.21.1, compile-only NeoForge annotation stubs are not Forge test annotations; split loader-neutral logic from annotations where necessary. Associate main/testmod and fishsim runtime class roots with the recognized loader mod group.

Generate a pinned expected test-ID/count inventory, then compare discovery, execution and required assertions. A successful Gradle exit with one vanilla/default test fails acceptance. Run Forge GameTests twice in fresh universes to detect leaked state. Follow the existing owner instruction to apply no elapsed-time whole-process kill cap: end on normal completion or concrete failure evidence; investigate apparent stalls using logs and thread dumps, and retain failed logs/worlds/diagnostics. Legitimate per-fixture GameTest tick timeouts remain assertion contracts and are distinct from an external process time cap. Historical counts such as 858 common passes/1 skip, 497 fishsim passes/59 skips and 115 client checks describe earlier snapshots only; they are neither fresh results nor the required new totals. The pre-commit hook skips NeoForge GameTests because of historical hangs; passing it alone is insufficient.

Datagen remains driven by [run_datagen.bat](D:/GitHub/fishtastic/scripts/run_datagen.bat) and its common sync. Audit `scripts/copy_assets.py`, `copy_assets_to_common.py` and `copy_frame_models.bat`, which explicitly name existing loader paths; extend only where Forge actually needs resources. Avoid introducing competing committed copies of shared generated data.

### 8.2 Feature acceptance matrix

Run the new Forge columns for each requested Minecraft version. Regress Fabric/NeoForge on both target versions after shared or build changes. Preserve existing 1.20.1 behavior; if release aggregation/tooling touches it, run its established Fabric/Forge checks too.

| Feature / contract | Required scenario and reviewed evidence |
|---|---|
| Build and side loading | Development client/server, final installed client/server, no client-class resolution on server, no untransformed platform placeholders |
| Static registries/components | All IDs present; correct Holder identity, block-item laziness, item defaults and synchronized components, block entity/menu/recipe construction, creative tabs/dispenser behavior |
| Datapack registries | Custom values in all relevant client consumers after join and reload; cross-registry references; invalid references fail clearly |
| Network | Every pinned payload registered/routed, correct active registry context, correct game thread/sender, strict protocol/direction handling, no packet-helper bypass |
| Fishing/security | Cast/bite/minigame/reward flow; stale/duplicate/oversized results; invalid menu IDs, slots/species/names and unloaded tank requests rejected through real shared validation |
| Companions | Three slots, move/remove/bond/rename/unlock; owner and other viewer; late join/re-track/dimensions/death/reconnect/restart; empty removal and changed-world cache reset; no duplicates |
| Tank appearance | Every shape, arbitrary frame/glass, sand/water, connections/open faces, interior lighting, translucent sorting, inventory/held/block, item model/resource reload/chunk invalidation |
| Cosmetics | Placement/capture, multi-cell and animated meshes, obstruction state, break/reload/save round trip |
| Ambient/world fish | Populations/context changes, submerged/above-water visuals, pause/toggle/dimension/reconnect transitions, no duplicate submissions, all companion locomotion classes |
| UI and quality | Four direct screens plus Gelatin menus, encyclopedia/quests/shop/leaderboard, tooltips/hover codecs, key rebind/hold, tutorial Play/Skip/retry, particles/banners/activation/gizmos, quality effects in all item contexts |
| Library rendering | Inputs/scroll/clipping/z order; 26.1.2 high-resolution renderer active with several items/frame; reload/disposal |
| Persistent gameplay | Existing same-loader saves, progression/tanks/parties after restart; migration decision explicit; no silent data loss in any advertised compatible scenario |
| Optional integrations | JEI absent; verified Forge JEI present if available; core support does not require an unavailable optional build |
| Dependencies | Missing/wrong-Minecraft/wrong-version Gelatin rejected clearly by metadata; actual remote runtime dependency resolves without MavenLocal after publication |
| Test discovery | Expected pinned namespaces/IDs/counts, no missing required suite, reports and two fresh-world runs; interrupted runs unverified |

### 8.3 Production artifact checklist

For each final jar record SHA-256, task/output path, zip listing/CRC, metadata expansion, manifest, mixin configs/refmaps and AT contents. Prove:

- Transformed common and required fishsim runtime classes are present; platform/config/storage dispatch resolves to Forge implementations.
- No unresolved Fabric/NeoForge runtime dependency leaks into Forge common code or side stripping. Correct Forge entrypoint and all required assets/data exist.
- Common AW access requirements are converted into the final Forge AT; Gelatin explicit hover-codec AT entries remain effective.
- Correct Java/Minecraft/Forge/Gelatin floors are expanded; dependency requirements agree between jar and distribution metadata.
- MCP bridge, testmod, self-test, simulation render/harness/viewer tooling and other current development exclusions do not ship. Required simulation runtime classes, including current MoodPresets checks, still ship; no dangling references to excluded classes remain.
- Final runtime variants/publications reference shadowJar on 26.1.2 and remapJar on 1.21.1, as proven by inspected output rather than assumed task names.

Launch clean installed Forge client and dedicated server with only production Fishtastic, matching Gelatin and explicitly required runtime libraries. Use two distinct client identities. Perform join, server-opened menu, fishing, tank placement/render/update, companion changes, reload and save/restart. Development scene classes are intentionally excluded; run those tests through a separate testmod or a written production smoke procedure. Development success cannot substitute for this installed-jar check.

### 8.4 Command discipline

The following are **proposed implementation commands, not commands executed or verified by this review**. Existing task examples must be checked against the pinned branch; `:forge:*` tasks are a proposed naming contract and do not exist yet in the inspected target builds. Run from the indicated checkout using its wrapper, with Java 21 for 1.21.1 and Java 25 for 26.1.2. Verify actual daemon/toolchain selection before interpreting failures.

```powershell
# In each repository/version checkout: record the wrapper/JVM baseline.
.\gradlew.bat --version

# Existing Fishtastic tasks, after confirming branch task inventory.
.\gradlew.bat :common:test :fishsim:test :tools:tank-shape-gen:test --no-parallel
.\gradlew.bat :fishsim:jar :fabric:build :neoforge:build --no-parallel
.\gradlew.bat :fabric:runGametest :neoforge:runGametest --no-parallel

# Proposed Fishtastic Forge tasks AFTER their configuration is implemented.
.\gradlew.bat :fishsim:jar :forge:build --no-parallel
.\gradlew.bat :forge:runGametest --no-parallel
.\gradlew.bat :forge:runServer --no-parallel
.\gradlew.bat :forge:runClient --no-parallel

# Proposed local Gelatin dependency publication in its matching version checkout.
# Verify publication names/version and use only a deliberately selected local repository.
.\gradlew.bat :common:publishToMavenLocal :forge:publishToMavenLocal --no-parallel
```

Client/server tasks are separate controlled sessions; do not run the examples as an unattended sequence that leaves processes holding build outputs. Use `--no-parallel` when invoking loaders together because Architectury shared agent-jar races are already documented. Save commands and complete logs with exits, named test discoveries and actual output paths. Do not hardcode an old test cap or call every source set compiled merely because a root task completed. No public upload command belongs in the initial proof stages.

## 9. Release only after dependency and regression gates

Gelatin's Maven/Central and CurseForge definitions currently enumerate existing loader components; Fishtastic's uploads enumerate existing loaders and require Gelatin. Add `gelatinui-forge` and `fishtastic-forge` artifacts with correct final outgoing variants, loader/Minecraft/Java tags and required dependency. Keep Java 21/25 distinct. Stale comments describing Gelatin integration as optional do not override current required release metadata.

Release order is: validate new Gelatin version locally with all affected loaders; validate Fishtastic against that exact version; stage Gelatin publication; when authorized, publish and verify clean remote resolution; then build/stage/publish Fishtastic with the exact dependency requirement and verified hashes. A MavenLocal success is not proof that the public dependency exists. Check source/javadoc/signing/Central bundle membership and CurseForge dependency declaration. Never upload raw/dev-shadow jars or retry successful publication just to recover missing output IDs.

The release checklist is complete only when both new Forge targets pass F10, existing Fabric/NeoForge target builds pass their focused and full regressions, installed jars pass, the tested minimum/latest policy matches metadata, and unresolved deviations are either fixed or explicitly scoped before support is advertised. There is no existing checked-in GitHub workflow to expand; add CI after local commands/reports are reproducible. Do not create a green CI badge around unproven discovery or development-only launch checks.

## 10. Resolve these questions at the named gates

| Question | Default / required decision | Gate and acceptance evidence |
|---|---|---|
| Which immutable Loom artifact contains the fixes and works with wrappers/Shadow? | Resolve and pin; no permanent moving snapshot assumption | F01/F02 resolved artifact/source hashes, task graph and installed launches |
| Does Gelatin 1.21.1 need an Architectury plugin upgrade alongside Loom? | Test the library's older 3.4 baseline; select a compatible explicit pairing | F01 existing-loader and Forge transformation results |
| What are exact Forge 26.1.2 datapack/config/bus APIs? | Inspect pinned artifact source; preserve behavior, not NeoForge signatures | F03 compilation, synced custom data, configs/ticks/setup proof |
| Which Forge 26.1.2 PiP hook works? | Factory only if confirmed, otherwise verified loader-specific injection | G02 pinned patch/source decision and high-resolution visual proof |
| Which Forge 26.1.2 tank/item/model-data APIs preserve dynamic behavior? | Prototype before whole model port | F06 placed/item/update/material/reload proof |
| How will Forge 26.1.2 render common world fish? | Frame pass candidate versus narrow submit/extract mixin | F07 extraction/order/graphics-mode/lifecycle proof |
| How are Forge packet handler queues mapped? | One explicit contract; no accidental double queue | F04 actual game-thread assertions and sender/handled tests |
| Are loader-switch worlds supported? | Initial acceptance is same-loader; decide/document migration separately | F05/F10 storage schema, compatibility contract and any migration fixtures |
| Are absent peers or cross-loader connections supported? | Strict same-version Forge peers initially | F04/F10 protocol negative tests; separate work needed to expand scope |
| Which Forge version floor is supported? | Test candidate latest first; advertise lower floor only after testing | F10 exact minimum/latest runs and metadata |
| Is Forge JEI available; how is MixinExtras supplied? | Confirm primary artifacts/runtime; omit optional integration from core probe | F02 dependency manifest, F10 present/absent tests |
| Does GhostConnection need Forge setup? | Inspect actual mock connection behavior | F03/F09 ghost/scene execution, no unsupported no-op assumption |
| What are the exact test counts on the final feature snapshot? | Generate named inventory after source pin | F00/F09/F10 expected/discovered/executed manifests |

## 11. Implementation progress

**Overall state: PLANNED. No implementation package has started.** The completed work is source/upstream research and this guide only. Do not interpret an empty evidence directory as a completed baseline.

| Package / version | State | Result / evidence | Exact next operation |
|---|---|---|---|
| F00 / both | PLANNED | Research notes linked below; no execution manifest yet | Capture both mods' target worktree HEADs, complete dirty inventories and source/property/wrapper hashes into `build/forge-support/baseline/manifest.json`, without changing files or parity markers |
| F01-F11 and G01-G03 / both | PLANNED | Not compiled, launched, tested or published | Await F00 baseline; then resolve/prove pinned Loom and existing-loader regressions in `build/forge-support/<minecraft>/F01/` |

Before beginning the next operation, append its exact command/action, expected output, source/dependency pins, package/version, evidence destination and stop condition to this section or a linked dedicated Forge progress ledger. After each checkpoint record result, exit status, reviewed logs/artifacts, deviations and the next exact action. Mark interrupted work **UNVERIFIED** and rerun the necessary gate; do not infer success from partial logs. Keep a concise durable summary in docs and raw evidence under `build/forge-support/`; do not commit secrets or large transient runtime worlds.

Suggested evidence layout:

```text
build/forge-support/
  baseline/manifest.json
  <minecraft>/<package>/
    operation.md
    source-and-dependencies.json
    commands-and-exits.json
    logs/
    tests/expected.json
    tests/discovered.json
    tests/reports/
    scenes/summary.json
    scenes/screenshots/
    artifacts/manifest-and-sha256.json
    installed-client-server/
    next-action.md
```

An implementation session must end with a saved next action even when a gate fails. Keep this tracker separate from the backport catch-up handoff and leave existing parity markers unchanged.

## 12. Source appendix and navigation

### Local investigation records

- [Build and toolchain review](<D:/GitHub/fishtastic/research_notes/Forge support implementation plan/build-toolchain.md>) — upstream build support, packaging, namespace and fallback evidence.
- [Runtime adapter review](<D:/GitHub/fishtastic/research_notes/Forge support implementation plan/runtime-adapters.md>) — exact common seams, registries, payloads, persistence, rendering and hook inventory.
- [Gelatin and validation review](<D:/GitHub/fishtastic/research_notes/Forge support implementation plan/gelatin-validation.md>) — dependency lifecycle, PiP, harness gaps and release ordering.

These notes contain per-claim links and explicit gaps; this guide consolidates their implementation consequences. File paths identify inspected working trees; implementation source manifests must record immutable revisions and relevant dirty patch hashes.

### Primary upstream references

| Reference | What it establishes |
|---|---|
| [Forge 1.21.1 downloads](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.21.1.html), [26.1.2 downloads](https://files.minecraftforge.net/net/minecraftforge/forge/index_26.1.2.html) | Candidate builds checked on research date |
| [Loom 1.17 release](https://github.com/architectury/architectury-loom/releases/tag/1.17), [PR 343](https://github.com/architectury/architectury-loom/pull/343), [PR 349](https://github.com/architectury/architectury-loom/pull/349) | Modern Forge development/unobfuscated build support, not local mod execution |
| [ForgeProvider runtime namespace](https://github.com/architectury/architectury-loom/blob/dev/1.17/src/main/java/dev/architectury/loom/forge/dependency/ForgeProvider.java#L54) | Forge >=50 Mojang production namespace |
| [Loom access conversion API](https://github.com/architectury/architectury-loom/blob/dev/1.17/src/main/java/net/fabricmc/loom/api/ForgeExtensionAPI.java#L82) | Correct final-task targeting matters for bundled AWs |
| [Architectury transformations](https://docs.architectury.dev/plugin/introduction/) | ExpectPlatform/environment rewriting versus runtime API |
| [Forge 26.1.2 MDK](https://github.com/MinecraftForge/MinecraftForge/blob/26.1.2/mdk/build.gradle) | Bounded ForgeGradle fallback baseline |
| [Forge channel builder](https://github.com/MinecraftForge/MinecraftForge/blob/26.1.2/src/main/java/net/minecraftforge/network/ChannelBuilder.java), [1.21.1 payload channel](https://github.com/MinecraftForge/MinecraftForge/blob/1.21.1/src/main/java/net/minecraftforge/network/PayloadChannel.java), [26.1.2 payload channel](https://github.com/MinecraftForge/MinecraftForge/blob/26.1.2/src/main/java/net/minecraftforge/network/PayloadChannel.java) | Typed modern payload channel candidate on both targets |
| [PayloadConnection](https://github.com/MinecraftForge/MinecraftForge/blob/1.21.1/src/main/java/net/minecraftforge/network/payload/PayloadConnection.java), [PayloadFlow](https://github.com/MinecraftForge/MinecraftForge/blob/26.1.2/src/main/java/net/minecraftforge/network/payload/PayloadFlow.java), [Channel](https://github.com/MinecraftForge/MinecraftForge/blob/26.1.2/src/main/java/net/minecraftforge/network/Channel.java) | Registry buffer, handler threading and send-encoding contracts |
| [Forge 26.1.2 model events](https://github.com/MinecraftForge/MinecraftForge/blob/26.1.2/src/main/java/net/minecraftforge/client/event/ModelEvent.java), [frame pass event](https://github.com/MinecraftForge/MinecraftForge/blob/26.1.2/src/main/java/net/minecraftforge/client/event/AddFramePassEvent.java), [frame pass manager](https://github.com/MinecraftForge/MinecraftForge/blob/26.1.2/src/main/java/net/minecraftforge/client/FramePassManager.java) | Rendering candidate seams; no proof of NeoForge API equivalence |
| [Forge menus](https://docs.minecraftforge.net/en/1.21.x/gui/menus/), [Forge screens](https://docs.minecraftforge.net/en/1.21.x/gui/screens/) | Menu open and client screen-registration conventions |

### Local implementation starting points

Use the target branch's own code first, and older Forge as a behavioral reference:

- [26.1.2 Fishtastic](D:/GitHub/fishtastic/settings.gradle), [1.21.1 Fishtastic](D:/GitHub/fishtastic-worktrees/mc-1.21.1/settings.gradle), [1.20.1 Forge module](D:/GitHub/fishtastic-worktrees/mc-1.20.1/forge/build.gradle).
- [26.1.2 Gelatin](D:/GitHub/gelatin-ui/settings.gradle), [1.21.1 Gelatin](D:/GitHub/gelatin-ui-worktrees/mc-1.21.1/settings.gradle), [1.20.1 Forge library](D:/GitHub/gelatin-ui-worktrees/mc-1.20.1/forge/build.gradle).
- [Fishtastic packet registrar contract](D:/GitHub/fishtastic/common/src/main/java/grill24/fishtastic/network/FishtasticPackets.java), [NeoForge registry adapter](D:/GitHub/fishtastic/neoforge/src/main/java/grill24/fishtastic/architectury/neoforge/NeoForgeRegistrationApi.java), [NeoForge companion storage](D:/GitHub/fishtastic/neoforge/src/main/java/grill24/fishtastic/companion/neoforge/CompanionStorageImpl.java).
- [Gelatin common lifecycle](D:/GitHub/gelatin-ui/common/src/main/java/io/github/currenj/gelatinui/GelatinUi.java), [menu bridge](D:/GitHub/gelatin-ui/common/src/main/java/io/github/currenj/gelatinui/registration/menu/SidedRegistrationHelper.java), [Fishtastic Gelatin menu listeners](D:/GitHub/fishtastic/common/src/main/java/grill24/fishtastic/compat/GelatinMenus.java).
- [GameTest guidance](D:/GitHub/modding-guide/18-game-tests.md), [current fresh-world helper](D:/GitHub/fishtastic/gradle/gametest-world.gradle), [world validation history](D:/GitHub/fishtastic/docs/fish-companions-world-validation.md), [backport progress](D:/GitHub/fishtastic/docs/backport-catchup-progress.md).

The first implementation milestone is evidence that the chosen build can transform, package and load this code on both physical sides. The next is proof of the 26.1.2 graphics contracts. Completing those milestones turns the remaining work into a bounded adapter/parity exercise and gives a sound basis for scheduling the full port.
