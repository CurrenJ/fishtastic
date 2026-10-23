# Forward-port investigation: 26.2 and 26.3

This is a condensed future-work brief from the 19 October 2026 audit of source
`0b84e7e327e9317ebd77c6fb61d418bb33329cdf` plus its then-dirty tree. It records
risks, not implemented ports, tested target combinations or a delivery commitment.
Refresh exact target sources and metadata at kickoff. Old dependency availability,
line numbers and test counts are not acceptance evidence.

## Scope and initial proofs

Preserve gameplay parity, required separately distributed Gelatin UI, the
Minecraft-free fishsim boundary and loader adapters. Stabilize 26.2 before deriving
26.3 from its reviewed baseline. Fabric/NeoForge are four independent proposed
release cells (two versions each). Forge is a separately chosen/gated addition;
existing support does not establish target compatibility.

CoolCam remains development-only: do not port it, bundle it, require it to compile
target release builds or make it a release dependency. Isolate/exclude its optional
bridge before target work depends on it. Preserve release packaging exclusions for
MCP, self-test and fishsim viewer/harness/render packages.

Before broad migration, prove these small slices:

1. Pin immutable Minecraft/JDK/Gradle/Loom/Architectury/loader coordinates and matching
   dependencies; compile/transform minimal common and platform code, access rules
   and mixins; launch an installed artifact. A published tool family is not a
   validated combination. Choose native-build fallback only as an explicit redesign.
2. Port one Gelatin screen with text, item rendering, tooltip, resizing, focus and
   server menu lifecycle. Verify dedicated-server class loading separately.
3. Render one quality item's atlas, shader/UBO and consumer correctly through
   resize/reload, with a nonblank actual target capture. Prove 26.3 text input too.

## Migration exposure

The audit identified 26.2 staged/prepared rendering, buffer submission and screen
routing changes. Inspect exact target depth conventions; do not fix reverse depth
by trial-and-error constants. Review `BufferSourceAccessor`, `RenderBuffersMixin`,
`ItemFeatureRendererMixin`, `FishtasticItemOutlineAtlas`, world outlines, tank and
companion rendering, ambient integration, HUD/screens and Gelatin extraction.
Datagen tag builders and model/recipe/loot APIs require their own semantic audit.

The 26.3 investigation additionally identified Renderpearl GPU APIs, explicit
render-pass attachments replacing global output-target overrides, changed shader
layouts, SDL3 replacing GLFW, native key/mouse changes, text-focus integration and
reloadable recipe/advancement registries. Prove replacement atlas ownership and
completion before consumers read it; document temporary GPU resource lifetimes.
Verify shader/UBO alignment, depth, resize, reload, animated items and every item
presentation (inventory, held, entity/frame), not just a working tank screenshot.

Historical upstream pointers to refresh against pinned sources:

- [NeoForge 26.3 primer](https://docs.neoforged.net/primer/docs/26.3/).
- [Fabric 26.3 migration](https://www.fabricmc.net/2026/09/15/263.html).
- [Architectury Loom 1.17](https://github.com/architectury/architectury-loom/releases/tag/1.17).

## Integration and acceptance

Freeze clean Fishtastic/Gelatin revisions and explicitly dispose of dirty changes.
Use separate branches, run worlds and output per target/loader. Keep the current
26.1.2 baseline as a regression reference; do not mutate normal playtest worlds.
Read relevant canonical modding-guide topics and exact upstream sources first.

After compile/hooks and the vertical slices, validate server/data and rendering/UI:

- Static/datapack registries, components, recipes/loot/tags and clean resource
  generation; compare semantic IDs/content after reload, reconnect and restart.
- Codec registry context, packet direction/thread/sender validation and invalid,
  duplicate or stale actions; conserve items, currency and progression.
- Catching, minigame completion, shops, quests, tutorials, encyclopedia and compost.
- Companion equip/rename/removal, owner/observer sync, tracking range, death,
  dimensions, reconnect and server restart; clear disconnected client caches.
- Copies of old saves: 26.1.2 -> 26.2, 26.2 -> 26.3, and direct 26.1.2 -> 26.3
  only if advertised. Compare tanks/materials/cosmetics, fish/components, catches,
  quests/unlocks and parties after two restarts.
- Stock renderer visual matrix plus specifically advertised optimization/shader
  combinations, GUI scales, text input/rebinding, reload and window resize.
- Nonzero expected test discovery, with named tests compared to a baseline;
  a green task that finds no tests is a failure. Port visual scene assumptions
  before trusting PASS lines. Use actual two-client sessions for advertised sync.
- Clean installed client/integrated/dedicated server launches for each advertised
  cell. A development launch and one loader's result cannot accept another cell.

Keep concise current progress separate from backport/Forge trackers, with exact
source/dependency pins, pending operation, command, exit status, artifacts/hashes,
results and next action. Interrupted runs remain unverified. Store raw evidence
under `build/forward-ports/<minecraft>/<loader>/`; do not commit per-run captures.

## Distribution gate

Resolve matching Gelatin remotely without `mavenLocal`, then inspect transformed
consumer JARs, bundled fishsim, development exclusions, resources and dependencies.
Validate game/loader/Java tags and exact release-cell selection; retain hashes.
Rehearse clean profiles and missing/wrong-dependency failures. Dependency publication
and actual availability precede consumer release. Publication requires its own
instruction; investigation is not publication authorization. Cross-loader or mixed
mod/patch-version compatibility are additional claims needing independent tests.
