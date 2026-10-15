# Audit: fish companions and fish in world (pre-backport)

**Date:** 2026-10-15 · **Branch:** `26.1.2` at `4246d339`, plus the staged, uncommitted Phase 5 (moods) work.
**Scope:** everything since the last backport parity point `4b0e818e` (2026-10-06): 75 commits and 31
staged files. That is about 47,600 inserted lines outside `docs/` and generated data, and about 128,000 more
under `docs/`.
**Method:** I read the design docs, specs and handoffs. I read every client and server file of both features
in `common/`, the platform wiring, and the `:fishsim` companion/ambient public surface. I checked "dead" and
"unused" claims by searching the whole tree. **No build, test run or game launch was done for this audit**,
so nothing here is measured. Performance items are judged from the code and from numbers already recorded in
the docs. Nothing in the repo was changed apart from adding this file.

Commit split: about 66 companion commits, 2 fish-in-world commits (`d3880715`, `d275b37b`, which hold all
seven phases), and 7 others (encyclopedia thresholds `703a09b1`, the NeoForge codec warm-up `9b203ace`,
cleanup gametests, the CRLF fix for the GUI designer, `.gitignore` changes).

---

## 1. Summary

1. **Port the end state, not the history.** About 20 companion commits (`76629f06` through `b6dc6407`, the
   v1 controller with its follow box, orbit, peeks, dock and scripted scenes) were deleted wholesale by the
   sim rewrite in `2c4ac605`. The encyclopedia "Companions" tab (`802470b9`) was replaced twice. Replaying
   commits one by one would mean porting about 2,000 lines of code only to delete them.
2. **One whole parked feature is still in the code: fishing reactions** (the dock on the minigame HUD, the
   catch and escape reactions, the bobber nibble). This includes a registered picture-in-picture renderer
   that draws nothing, a packet the server sends on every catch that the client ignores, two mixin hooks, a
   `FishingMinigameAnimation` API, a persisted config key and a dev command. None of it runs. The 1.21.1 and
   1.20.1 targets don't have the PiP API at all. **Recommendation: delete it on 26.1.2 before porting**
   (§3.1), and bring it back from git history if fishing reactions return.
3. **About 30 A/B switches in `CompanionTunables` keep rejected or first-cut behaviour reachable.** Each
   "off" branch is a rejected body kept bitwise-reproducible for comparisons. Once the owner has signed off
   on a behaviour, removing its switch also removes its dead branch (§3.4).
4. **Fish in world has a real per-tick cost problem in big water.** Each tick, the runtime and the context
   manager copy the whole discovery cache (up to 4,096 columns), scan it again per shown patch, sort it, and
   hash every span. Each domain is also rebuilt every second even when nothing changed. The docs' recorded
   peaks (context manager up to 21 ms, cold tick peaks of 16–92 ms) fit this pattern (§6.1).
5. **Companions have no distance-based level of detail.** Every client fully simulates every visible
   player's party. Each unit of a party repeats its owner's entity query, biome-tag scan and 1,183-block
   warmth scan. Only solo play has been measured (§6.2).
6. **Repo hygiene:** about 45 MB of fish-in-world evidence (logs, run directories, screenshots) is committed
   under `docs/fish-in-world-phase*`. There are also 15 stale agent worktrees under `.claude/worktrees` and
   29 `hs_err_pid*.log` crash dumps in `common/` (§3.6).
7. **One latent correctness risk in the uncommitted Phase 5 work.** Shipped code (`ClientCompanions`)
   references `grill24.fishsim.harness.companion.MoodScenes`, and the shadow jar excludes
   `grill24/fishsim/harness/**`. It only works today because every path that reaches it is dev-gated (§7.1).

---

## 2. What the backport needs, and what it can skip

### 2.1 Superseded work: port the result, skip the history

| Superseded | By | What's gone |
|---|---|---|
| v1 companion controller, fish-companions.md §13–§21 (`76629f06`…`b6dc6407`) | sim rewrite Phase 3 (`2c4ac605`) | `CompanionFlock`, orbit/figure-eight/peek/trick paths, blend weights, docking, legacy steering seams, the wobble fix to the old controller (`94bc6612`), and the `companion`/`parade`/`peek`/`hand`/`fishing`/`scenes`/`polish` self-test scenes |
| Encyclopedia Companions tab and modal picker (`802470b9`) | vanilla-style `CompanionMenu`/`CompanionScreen` (`4d90e0bd`), then the 3-slot party (`b06bbb25`) | the tab and picker UI (but see the orphaned keys and packet in §3.2) |
| Automatic trios (any small FREE_SWIM fish brought two copies) | party grouping (D8) | the rule itself (see §3.3 for the leftover) |
| Scripted G1/G2 scenes (`c9756261`) | swum `CrossScene` (party §28, `fa207fe1`…`95ef958c`) | scripted geometry |
| First-cut drifter | drifter rework: bell, jet, bob (`5a9156ca`, `8fea393f`, `44453d44`) | still selectable through switches (§3.4) |
| Fish-in-world Phase 1 fixed fixture | natural runtime (Phases 2–7) | still shipped, see §3.5 |

### 2.2 Don't port at all

- **All the parked fishing-reaction surface** (§3.1). The PiP API doesn't exist on 1.21.1 or 1.20.1, so
  porting it isn't possible anyway.
- **Pre-party save migration.** `CompanionParty.CODEC` (`withAlternative`, `CompanionParty.java:89`) and
  `MAP_CODEC` (`mapEither`, `:96`) read the old single-stack attachment. `CompanionGroupConfig.count` is
  deprecated (`FishProfile.java:109`). The CHANGELOG has a note about lost trios. Companions have never been
  in a public release (the CHANGELOG says "Unreleased" and "earlier test builds"), so this code only
  protects the owner's own test worlds. The ports have never had companions, so they have nothing to migrate.
- **Dev and verification tooling that isn't part of the mod:** `tools/mc-gui-designer/**` and its
  `.run/GUI Designer.run.xml` (only its output, `textures/gui/companions.png`, is needed), the `:fishsim`
  harness, viewer, renderer and 47 opt-in probes (they're Minecraft-free and shared source, so copy them
  only if the ports run them), the two-client scripts under `docs/fish-companions-twoclient/`, and the
  `docs/fish-in-world-phase*` evidence.
- **The NeoForge codec warm-up (`9b203ace`)** fixes a dev-only classload deadlock on 26.1.2. Port it only if
  the deadlock reproduces on the target.

### 2.3 Must port, carefully: shared refactors under both features

- **`FishingPoolResolver` extraction** (`FishingMinigameManager` changed by about 290 lines,
  `FishtasticFishItem` by about 70). It now drives real catches, the Almanac preview and the ambient
  context. It changes the core fishing path, so it's the riskiest port and needs the existing catch-pool
  tests on each target.
- **`FishVisuals` and `GroupFishPose` extraction** from the tank renderer, plus the small NaN-safety
  changes to `FlockEngine` (`placeFish`, `passesGate`, `residentFits`, finite floor heights). Tanks use these.
- **Renderer hook points:** `SubmitCustomGeometryEvent` (NeoForge) and the Fabric world-render hook,
  `AvatarRenderer` layer injection, and `LevelRenderer.blockChanged` / `ClientLevel.onChunkLoaded`. All of
  these are 26.1-specific and each needs its own per-version equivalent.
- **Player attachments for the party.** 1.20.1 Forge has no attachment API (it needs a capability), and
  Fabric's attachment API is newer than 1.20.1's Fabric API. This is the biggest per-loader design question
  in the companion port.

---

## 3. Leftovers from decisions we didn't go with

### 3.1 Parked fishing reactions: everything wired, nothing runs

| Where | What | Status |
|---|---|---|
| `client/companion/CompanionPipRenderer.java`, `CompanionPipRenderState.java` | perspective PiP dock renderer | registered on both loaders (`FishtasticFabricClient.java:326`, `FishtasticNeoForgeClient` `registerPictureInPictureRenderers`); nothing ever creates a `CompanionPipRenderState`, and `ClientCompanions.submitDockedFish` (`:667`) is empty |
| `ClientCompanions.pipRenders` (`:179`), `CompanionPipRenderer.lighting` | spike counters and hooks | always 0 / unused |
| `network/CompanionFishingEventPacket.java`, `companion/CompanionFishingOutcome.java` + `CompanionFishingOutcomeTest`, gametest round-trip | catch/escape broadcast | **the server sends it to every player within 128 blocks at every session end** (`FishingMinigameManager.java:482-487`); the client handler `ClientCompanions.onFishingEvent` (`:320`) is empty |
| `mixin/GuiGraphicsMixin.java:121` `fishtastic$addPictureInPicture` (+ `IGuiGraphicsExtension`) | PiP submit hook | no callers |
| `mixin/FishingHookMixin.java:100-123` `fishtastic$isBiting` / `fishtastic$forceBite`, `DATA_BITING` shadow (+ `IFishingHookExtension`) | nibble reaction | no callers |
| `util/FishingMinigameAnimation.java:1169-1275`: `CompanionAnchors`, `companionAnchors`, `companionDockAnchor`, `isHidingNow`, `getCompanionCelebration`, `hasFishTarget`, `caughtFish` | dock lane / reaction anchors | no callers (about 100 lines in a core minigame file) |
| `FishtasticClientConfig` `companionWaitsAtBobber` (`:279`, the key, setter and `persist`), plus its block in the default file text (`:35`) | "skip docking" | written, never read; **the shipped default config file tells players about a dock that doesn't exist** |
| `CompanionCommand` `waitatbobber`, and arguably `catchdemo`/`catch` | dev | `catchdemo` was for "celebration + greet"; with no greet it just exercises the minigame celebration |
| `ClientCompanions.renderHud(graphics, minigame, …)` | HUD hook | the `minigame` parameter is unused; `activeMinigame()` only serves `devForceCatch` |
| `CompanionSceneManager.busyFishing` rationale ("their companion belongs at the bobber") | rule comment | the rule is still reasonable; the stated reason refers to the parked feature |

`FishingMinigameAnimation.resolveTargetCaught` and `devCatchTarget` (the extraction) are fine to keep.

### 3.2 Old companion GUI

- **`UnequipCompanionPacket`** is registered (`FishtasticPackets.java:36`, `:136`) but no client code sends
  it any more, because the menu slots handle returns. It's still a live server endpoint. Delete it, or
  document why it's kept.
- **Orphaned lang keys** (no exact or prefix-built reference in code):
  `screen.fishtastic.encyclopedia.tab.fish`, `…tab.companions`, and under
  `screen.fishtastic.encyclopedia.companions.`: `title`, `your_companion`, `bond_available`, `bond_quest`,
  `bond_catches`, `bond`, `unequip`, `open_quest`, `name_hint`, `set_name`, `empty`, `picker.title`,
  `picker.empty`, `picker.confirm`, `picker.cancel`. Also `screen.fishtastic.companion.your_companion`,
  the pre-party single-slot label. That's 18 keys.
- The feedback keys the new screen *does* use still live under the old `encyclopedia.companions.feedback.*`
  namespace (`CompanionScreen.java:165`).
- The tutorial still talks about the tab that no longer exists: `CompanionTutorialStep` javadoc,
  `CompanionTutorialManager.onCompanionsTabOpened` (`:26`) and the client handler of the same name (called
  from `CompanionScreen` opening). `CompanionClientState`'s javadoc says "Companions tab" and "picker".
  Behaviour is fine; only the names are stale.

### 3.3 Retired grouping rules

- `CompanionGroup.TRIO_MAX_SIZE_CM` and `isTrio` (`CompanionGroup.java:21-31`) are `@Deprecated` with zero
  callers. Delete them.
- `FishProfile.CompanionGroupConfig.count` is deprecated and kept "so older datapacks load", but no released
  datapack ever had it. Delete it before the ports introduce the schema.

### 3.4 A/B switches that keep rejected bodies alive (`CompanionTunables`)

The pattern is sound for the work in progress. Each change shipped behind a switch whose "off" setting is
the previous body, bitwise, so equal-grounds comparisons were possible. But every switch the owner has
signed off on leaves a dead branch, and every switch whose default is `false` because it "measured a loss"
is rejected code that still ships.

- **Adopted, default `true`, off branch = rejected or first-cut body** (about 28): `whiskers`, `driftJet`,
  `jetArrive`, `jetCounterFinBrake`, `jetMindTurn`, `driftBob`, `bobRetime`, `bobMindTurn`, `driftRetime`,
  `driftArrive`, `driftFlare`, `driftNoClimb`, `driftRoomBrake`, `driftRoomPastContact`, `driftHoldHeight`,
  `driftIdleSteady`, `driftCalmCatch`, `anchoredHead`, `partyHomes`, `partyLeases`,
  `partyGroundSeparation`, `partySwimSeparation`, `partyPredatorScene`, `otherPlayerClearance`,
  `handClock`, `partyHandClock`, `shoulderPeek`, `moreGroundBeats`. `driftRoomBrake` and `driftJet` have 11
  branch sites each, and `handClock` has 15.
- **Rejected, default off, shipping anyway:** `idleInView` ("measured a loss", §19), `groupPaceCap` ("not a
  clear win"), `driftSlowPivot = 0` with `driftPivotSwingOnly = false`, the `driftLeanIntoBrake = true`
  branch, and the "0 off" numerics on `driftOwnerAside*` and `driftIdlePulseSeconds`.
- **Diagnostic statics in shipped code:** `GroundMind.formationDropGuard`, `alongsideRecoveryEnabled`,
  `beatsEnabled`, `skipRepeatedBeatFootprints`, `countBeatFootprints` (`GroundMind.java:69-113, 575-577`).
  These are mutable statics shared by every agent in the JVM.
- **Recommendation:** for each switch whose behaviour has passed the owner's look, inline the adopted branch
  and delete the field. Keep the in-flight ones (Phase 4 and 5 items still pending a look) and the dev
  command's live list (`DEV_SWITCHES`), trimmed to match. Doing this before porting removes branches the
  ports would otherwise carry. The fingerprint baselines will move once, which is expected because the off
  paths are what they pin.
- GLIDE is **not** a leftover. It's parked with known gaps, but rays and mantas run on it in game.

### 3.5 Fish-in-world leftovers

- **Phase 1 fixture mode** (`ClientAmbientFish.startFixture` `:56`, `Fixture` `:226`, `fixturePose`,
  `SPECIES`/`SIZE_CM`): dev-gated but shipped. It's kept only so the `ambientparity` scene has a fixed
  reference, and it carries a second copy of the runtime's depth sort, frustum cull and submit loop.
  Recommendation: move it next to the self-test (or drop it, and run the parity scene on a
  natural-runtime pond with a fixed seed). Don't port it.
- **`AmbientFishRuntime.TARGET_PER_DOMAIN = 8`** (`:81`) has no users; `AmbientBudget.target(volume)`
  replaced it. `MIN_PATCH_COLUMNS` and `SPAWN_SEPARATION` are used, but their javadoc still describes the
  Phase 2 numbers.
- **Compatibility seams from the Phase 3 extraction:** `FishTankBlockEntityRenderer.ResolvedFishRender` and
  `resolveFishRender` (`:517`), "for existing companion/debug callers", plus the `TankFlockAdapter` static
  wrappers (`locomotionOf`, `speciesId`, `renderedLength`, `shelterUseOf`) that only delegate to
  `FishVisuals`. Point `CompanionRender`, `CompanionFishPose` and `TankGroupFlock` at `FishVisuals` and
  delete the seam. On the ports, port only `FishVisuals`.
- `TankGroupFlock.FishPose` / `fishPose()` (`:321`) is a "development camera capture" API on a production
  class.

### 3.6 Repo artifacts

- **About 45 MB of committed evidence** under `docs/fish-in-world-phase{0,1,2,4,5,7}/`: 72 `.log` files
  (several over 5 MB of dedicated-server logs), PNG screenshots, CSV traces, `init.gradle` scripts, a
  `FixtureCapture.java`, and hidden `.gametest-*.log`/`.junit-*.log`/`.build-final.log` files. The docs
  refer to them as evidence. Consider keeping only the curated screenshots and the gate tables and moving
  the raw logs out of git, or at least not copying them into the port branches.
- `docs/benthic-come-alongside-experiment.patch` holds a rejected experiment ("`git apply` it to resume").
  `docs/fish-companions-party-two-owner-fp.txt` is a fingerprint dump. Both are fine as history but are
  clutter for the ports.
- `docs/fish-companions-twoclient/` contains about 66 MB of untracked run directories next to its tracked
  script. Its `.gitignore` covers them, so this is a local-disk issue, not a repo issue.
- **15 stale agent worktrees** under `.claude/worktrees/agent-*` (the oldest at `07920354`, all with live
  `worktree-agent-*` branches), plus `C:/Users/curre/.codex/worktrees/companion-squid`. Every grep or IDE
  index of the repo also finds their old code: searching `isTrio` returns 15 stale copies.
  Remove them with `git worktree remove`.
- 29 untracked `common/hs_err_pid*.log` JVM crash dumps and 53 rotated `common/logs/*.log.gz` files. The
  tracked `common/logs/latest.log` (already in memory as a staging hazard) is modified again.

---

## 4. Approach changes: work the current version no longer needs

These overlap with §3. They're listed by decision so it's clear the code exists to serve an abandoned plan.

| Decision changed | Code that only served the old plan |
|---|---|
| Fishing reactions deferred (sim plan §5 "Deferred") | §3.1 in full |
| One companion → 3-slot party; size no longer decides grouping (D8) | `isTrio`, `CompanionGroupConfig.count`, pre-party codecs, CHANGELOG trio note, `screen.fishtastic.companion.your_companion` |
| Catalogue tab → vanilla menu | §3.2 |
| Old controller → sim; "Every old-controller dev hook… deleted" (plan §11.3) | `CompanionFishPose`'s javadoc still describes "a trick spin" as a caller input; "trick" survives only in comments |
| Phase 1 fixture → natural runtime | §3.5 fixture |
| Population caps 8 → 16 → 32/64; lifetimes 300–700 → 1800–3000 ticks | stale docs and comments (§5.1) |

---

## 5. Clarity and concision

### 5.1 Stale comments and docs that now say the wrong thing

- `ClientCompanions` class javadoc (`:56-58`) says pet-to-pet scenes are deferred and their packets ignored.
  Scenes have been live since party §28.
- The default `fishtastic-client.properties` text describes docking and "waits at the bobber" to players
  (§3.1).
- `AmbientFishRuntime`: `MAX_INDIVIDUALS` javadoc says "the Phase 0 headroom (2–4 domains × ~8 fish)",
  but the cap is 64. `start(ClientLevel)` (`:334`) says "the caller checks the development-environment
  gate", but the runtime is production now.
- `fabric/build.gradle:121` and `neoforge/build.gradle` say "only core/ and domain/ are reachable from the
  mod at runtime". `ambient/` and `companion/` ship and run too.
- `docs/fish-in-world-handoff.md:56` says lifetimes are 300–700 ticks; the code (`AmbientTuning`) is
  1800–3000. `docs/fish-in-world-island-playtest.md:17` says the 50% default is "at most sixteen"; it's 32.
  Both docs fix this further down, so the first statement a reader meets is wrong.
- `docs/fish-companions.md` is still presented as *the* spec, but §2–§3 (trio/solo, display modes) and
  §13–§21 (about 700 lines of v1 as-built notes) describe deleted code. Only §10 has a supersession banner.
  Add a banner at the top pointing to the sim plan and the party spec, or move §13–§21 to a history file.
- Companion docs now total about 8,000 lines across 17 files, including six handoffs whose work is done
  (`fish-companions-handoff`, `-phase3-handoff`, `-squid-handoff`, `-benthic-handoff`,
  `fish-companion-wobble-bug-handoff`, and the four party as-built files). A one-page index ("current
  truth lives in X, Y; the rest is history") would help the port more than the documents themselves.
- `Habitat`'s `@param homes` says "its zones and biome_weights"; the adapter deliberately uses only the
  Nether zone.

### 5.2 Duplication

- **The pose → locomotion table exists three times:** `FishVisuals.locomotionOf`, the `TankFlockAdapter`
  wrapper, and `CompanionGroups.locomotionOf`. The last carries a comment saying "the two copies must be
  changed together". **Rendered length exists three ways:** `FishVisuals.renderedLength`,
  `CompanionGroups.renderedLength`, and the BER's `getRenderScale`. `FishVisuals` has no client-only
  imports, so the pure parts could move to `data/` or `companion/`, and the server could use them directly.
- **Profile lookup** by item key is written out in `FishVisuals.resolve`, `CompanionGroups.profileOf` and
  `AmbientFishRuntime.ambientSizeCm`.
- **Per member, a companion resolves its fish render up to three times:** `CompanionRender`,
  `CompanionFishPose`, and `trueLength` during grouping. `CompanionRender` and `CompanionFishPose` hold the
  same stack, animation, length and locomotion; one object could do both.
- **Attention/unseen logic is in both `Mind` and `PerchBody`**, and `drawTicks` exists three times (`Mind`,
  `PerchBody`, `PartyLayer`).
- **Depth sort + frustum cull + submit** is written twice in `ClientAmbientFish` (fixture) and
  `AmbientFishRuntime`.
- Trivial aliases in `ClientCompanion`: `memberCount()` = `agentCount()`, `fishCount()` = `bodyCount()`,
  and `CompanionRender.tick(CompanionAgent)` alongside `tick(PoseHint)`.
- `CompanionCommand` hard-codes the mood list instead of using `ClientCompanions.devMoodNames()`, and the
  fake-point kinds are listed twice in the same builder.

### 5.3 Oversized classes that mix product and test code

- **`RenderSelfTest.java`: 6,256 lines in `common/src/main`, shipped in the jar.** This range added about
  3,000 of them (eight `ambient*` and five `companion*` scenes). Split it by feature, and consider excluding
  `client/selftest/**` from the jar the way `mcp/**` already is (§7.1 has the caveat).
- **`AmbientFishRuntime` (1,539 lines):** about a quarter is harness surface (`Individual`, `DomainReport`,
  the 40-field `Stats`, `debugState`, `pool`, `representableSpecies`, `samplerFor`, `patchColumns`,
  `retainedStateCount`, `waterSource`), used only by `RenderSelfTest`. A `AmbientDiagnostics` view would
  separate the two.
- **`ClientCompanions` (1,035 lines):** about half is dev tooling (phantoms, overlay, switches through
  reflection, mood presets, the predator-scene trigger, the catch demo). The same reasoning applies.
- **`Mind` (1,433), `PerchBody` (1,140), `GroundBody` (999), `GroundMind` (816).** Not wrong, but most of
  their branch count is the switches in §3.4.

### 5.4 Coupling from production code to dev and test code

- `ClientAmbientFish.tick` decides whether ambient fish run in normal play by asking
  `RenderSelfTest.isArmed()` (`:129`).
- `ClientCompanion.world` calls `ClientCompanions.devMood()` every tick and, when one is set, reads a
  harness `MoodScenes.Preset` (§7.1).
- Test seams in production classes: `CompanionSceneManager.packetSinkForTest` / `resetForTest`,
  `AmbientContextServer.resetForTest`, `FishtasticClientConfig.TEST_OVERRIDES`. The `Stats` invariants in
  §6.1 are also computed in production.
- Style: lots of inline fully-qualified names (`grill24.fishtastic.Fishtastic.LOGGER`,
  `grill24.fishsim.companion.CompanionAgent`, `com.mojang.brigadier.arguments.IntegerArgumentType…`) in
  files that already import neighbouring types. `CompanionCommand` is the worst case. The surrounding
  codebase uses imports.

### 5.5 Logging

- `ClientCompanions` logs every scene start, drop and refusal at INFO (`:349`, `:354`, `:368`). Commit
  `a887aab9` added the game-time diagnostic for a specific investigation. These should be DEBUG now that
  the cause is confirmed.
- `AmbientContextServer.handle` logs the first 10 responses and every 100th at INFO, with player names and
  fingerprints. That's acceptable on a dev server and noisy on a real one.

---

## 6. Performance

### 6.1 Fish in world: high

**Full-cache copies and scans every tick.**
- `syncDomains` → `AmbientPatchSelection.select(live, discovery.columns(), …)` (`AmbientFishRuntime.java:897`)
  copies **every cached column** (up to 4,096 `Column` records) each tick. It then folds them into a boxed
  `HashMap<Long,Integer>` with `merge` for each column.
- `AmbientContextClient.tick` → `runtime.patchSamples()` → `sampleOf` (`:597-619`), **per shown patch, per
  tick**:
  - `discovery.columnsOf(key)`: a full scan of all cached columns, allocating a record for each one in the
    patch;
  - a sort;
  - `level.hasChunkAt(new BlockPos(…))` for every column;
  - `geometrySignature` over every column's spans.

  The class comment says the per-tick compare "is a map lookup per patch". The lookup is, but computing
  the signature it looks up isn't.
- With 4 patches in a large ocean at 100% density, that's roughly 5 × 4,096 record allocations plus 4 sorts
  every tick.
- The recorded numbers fit this pattern: context-manager peak 21.1 ms on NeoForge and 9.9 ms dedicated
  (Phase 6); session tick peaks of 16–92 ms (Phase 6, island playtest, early-retirement record).
- **Fix direction:** have `WaterDiscovery` keep a per-patch column index and a per-patch signature that's
  updated incrementally when a column changes (it already counts `columnsChanged`), plus the nearest-water
  distance per patch. Then nothing needs a full-cache snapshot each tick.

**Rebuilds on a timer.**
- `syncDomains` treats a domain as stale whenever `!recent(builtTick, time, 20)` (`:946-948`). So every
  domain is rebuilt every second even when its columns and top margin are unchanged. Each rebuild is a
  `columnsOf`, a sort, a signature, and `engine.rebuildPreserving` for the whole population.
- Discovery's revalidation already catches geometry edits. Rebuilding only on a changed column count,
  margin or signature would remove most rebuilds.
- `reseed` (a full `rebuildPreserving`) also runs on every spawn and every retirement batch. That's bounded
  at 2 per tick, but it's still a rebuild of the whole engine each time.

**Diagnostic invariants computed in production.** `recordStats` (`:1395`) runs `contains`, `bodyFits`,
`surfaceAt`, `bedAt` and `openFaceDistance` for every active fish every tick, only to fill harness
counters. Gate this behind the harness or the debug command.

**Smaller items.** `submitWorld` allocates an `AABB` and `BlockPos` per fish per frame, and calls
`hasChunkAt` per fish per frame. That's minor at ≤64 fish. `spawnOne` builds a stream per spawn attempt.

### 6.2 Companions: medium, mostly in multiplayer

**No level of detail.** `ClientCompanions.tick` builds and steps a full sim for every player in
`level.players()` who has a party, at any distance the client tracks. The recorded cost is 20–90 µs per
companion per tick, and up to 430 µs on a first tick (sim plan §11.5). That was measured solo. With 10
nearby players and 3 units each, that's about 30 units per tick, about 1–3 ms per tick before any of the
items below. Options: freeze or skip remote parties beyond a radius or outside the frustum, and step them
at a lower rate.

**Work repeated per unit for the same owner.** Each `ClientCompanion` in a party calls `world()`
independently, so a 3-unit party does three of each of these per tick:
- `CompanionWorldAdapter.points`: a `level.getEntities` AABB query over 24×16×24;
- `CompanionAmbient.Sampler.sample`: a biome-tag scan that builds a `HashSet` of `"#" + tag` strings,
  copied again by `Ambient`'s `Set.copyOf`;
- the warmth scan: 13×7×13 = 1,183 `getBlockState` calls.

All of these depend only on the owner. Build them once per party per tick.

**Warmth-scan cadence** (uncommitted Phase 5). It rescans when 20 ticks have passed *or* the owner has
moved more than 3 blocks (Manhattan). Walking means about 1.4 scans per second per unit. Sprinting or
flying with an elytra means a scan almost every tick: up to about 70,000 block reads per second for one
3-unit party, and more for every visible remote party. Use a time cadence with a larger move threshold,
and cache the biome place tokens per `Holder<Biome>`.

**Small O(n²) cost with allocations.** `othersNear` (`:266`) calls `count()` and `all()` (both allocate)
for every companion every tick. `submitWorld` calls `count()` → `parties()` (allocates) every frame. This
only matters at high companion counts.

### 6.3 Server: low

- The `CompanionFishingEventPacket` broadcast is useless traffic (§3.1).
- `CompanionSceneManager.tickLevel` is O(holders²) per level per tick. It's range-gated before any grouping
  work, so it's fine at realistic player counts. `CompanionGroups.of(party b)` is recomputed for each group
  of party a, which is trivial.
- `AmbientContextServer`: at most 4 origins per request and 1 request per second per player (token bucket).
  Fine.

### 6.4 Not measured anywhere

GPU and frame-time percentiles for both features. Multiple players with parties. Tank + companion + ambient
together in dense terrain. A large ocean at 100% density across a session longer than the gate scenes.

---

## 7. Correctness risks I noticed in passing

1. **A harness class is reachable from shipped code** (uncommitted Phase 5).
   - `ClientCompanions.DevMood` holds a `grill24.fishsim.harness.companion.MoodScenes.Preset`.
     `devMood(String)` and `devMoodNames()` call into `MoodScenes`, and `ClientCompanion.world()` reads
     `dev.preset()` every tick whenever a dev mood is set.
   - The shadow jar excludes `grill24/fishsim/harness/**` (`fabric/build.gradle:123`,
     `neoforge/build.gradle:114`).
   - Today every path that reaches it starts from the dev-only command or the self-test. The JVM resolves
     classes lazily, so a production jar *probably* never touches it. That's fragile: one non-dev caller
     gives a `NoClassDefFoundError` on the client tick.
   - Move the presets into `fishsim/companion`, or into common dev code that ships. **Not verified against
     a packaged jar.**
2. `FishtasticClientConfig.persist()` (`:310`) writes each setting through its getter, and the getters
   return `TEST_OVERRIDES` when one is set. If a real setter runs while a self-test override is active, the
   override gets written to the owner's file. The class comment specifically promises this can't happen.
3. `CompanionFishPose` seeds its idle randomness from `fish.hashCode()` (`:47`). That's an identity hash,
   which differs per JVM, so perched idles differ between clients. Sim plan §11.1 already fixed this for the
   sim seed. It's cosmetic only.
4. Server statics that outlive a world in single-player: `CompanionSceneManager.BUSY_UNTIL`/`PARTNER`,
   `AmbientContextServer.THROTTLES`, and `CompanionGhost.ghost`/`owner`. None is cleared on server stop.
   The ghost is a real `ServerPlayer` added with `placeNewPlayer`, so if a world is saved or closed while
   it exists, its playerdata (including its conjured party) is probably written into the world. Dev-only;
   **not verified**.

---

## 8. Suggested order before porting

All on 26.1.2, each followed by the existing suites (fishsim, common JUnit, both loaders' gametests) and
the companion/ambient self-test scenes:

1. Delete the fishing-reaction surface (§3.1), `UnequipCompanionPacket`, the orphaned lang keys,
   `isTrio`, `CompanionGroupConfig.count`, the pre-party codecs, the extraction compatibility seams, and
   `TARGET_PER_DOMAIN`.
2. Collapse the owner-accepted A/B switches and delete the rejected ones (§3.4). Rebaseline the
   fingerprints once.
3. Fix the harness dependency (§7.1) and the `persist` override leak (§7.2).
4. Fix the fish-in-world per-tick snapshots and timed rebuilds (§6.1). Then re-measure with the existing
   `ambientlifecycle`/`ambientisland` scenes and compare against the recorded peaks.
5. Share the owner-level world queries across a party, and add remote-party LOD (§6.2).
6. Move dev tooling out of the product classes (§5.3, §5.4), decide what to do with the evidence blobs
   (§3.6), prune the worktrees, and add the doc index (§5.1).

Then port the resulting tree per feature, not per commit (§2).

## 9. Progress

**Step 1 done 2026-10-16 (uncommitted).** Deleted: the PiP dock renderer and state, `CompanionFishingEventPacket`
(and the per-catch broadcast in `FishingMinigameManager`), `CompanionFishingOutcome` and its test, the
`GuiGraphicsMixin` PiP hook, the `FishingHookMixin` `isBiting`/`forceBite` seam, the `FishingMinigameAnimation`
companion anchor/dock API, the `companionWaitsAtBobber` setting and `waitatbobber` command (`persist()`
now drops the key from older files), `UnequipCompanionPacket`, the 18 orphaned lang keys, `isTrio`,
`CompanionGroupConfig.count`, the pre-party save codecs, the `ResolvedFishRender`/`resolveFishRender` seam
and the four `TankFlockAdapter` wrappers (callers use `FishVisuals`), and `TARGET_PER_DOMAIN`.
Kept on purpose: `catchdemo`/`catch` and `devCatchTarget` (they still exercise the real minigame
celebration). Results: fishsim 556 (59 opt-in skipped), common 856, Fabric game tests 368/368, NeoForge
362/362 (one round-trip test fewer each), solo self-tests `companionbond, companionsim, companionparty,
companioneel, companionscenes, companionworld, ambientparity` 113/113 on both loaders.
Note: a save still holding a pre-party (single-stack) companion no longer loads it.

**Step 2 done 2026-10-16.** Owner chose to collapse only the signed-off switches and to delete the
rejected ones. Collapsed (adopted branch kept, off branch deleted): `whiskers`; the bell drift set
(`driftRetime`, `driftArrive`, `driftFlare`, `driftNoClimb`, `driftRoomBrake`, `driftRoomPastContact`,
`driftHoldHeight`, `driftIdleSteady`, `driftCalmCatch`); the jet set (`driftJet`, `jetArrive`,
`jetCounterFinBrake`, `jetMindTurn`); the party set (`anchoredHead`, `partyHomes`, `partyLeases`,
`partyGroundSeparation`, `handClock`, `partyHandClock`, `shoulderPeek`, `moreGroundBeats`,
`partyPredatorScene`). Deleted with their code: `idleInView` (and `GroundMind`'s show-spot path),
`groupPaceCap` (and the ground pace cap), `driftSlowPivot`/`driftPivotSwingOnly`, the
`driftLeanIntoBrake` branch, and the zero-means-off guards on `driftOwnerAside` and
`driftIdlePulseSeconds`. With no build-time switch left, the dev command's rebuild path went too.
Still switches, pending the owner's look: `driftBob`, `bobRetime`, `bobMindTurn`,
`partySwimSeparation`, `otherPlayerClearance`.
Tests that only compared a removed switch's two branches went (`disablingJetRestoresTheBellBitwise`,
`groundLeaderIsPacedToItsSlowestMember`, `BenthicVisibilityTest`, whose assertion was clock-off only);
the `companioneel` self-test now flips `otherPlayerClearance` instead of `partyHomes`.
Fingerprints: all 1,038 (930 single-owner, 108 two-owner) bitwise identical before and after, so no
rebaseline was needed. Left for now: the `GroundMind` diagnostic statics, which regression tests flip
to prove their fixes.
