# Stage 4 scope — companion presentation (F09-F12)

Written 2026-10-17 (session 3) while starting stage 4 on 1.21.1 (`port/1.21.1`). Source snapshot `48fffdfb`.
Companion code is brand new on both targets (nothing before `4b0e818e`), so stage 4 is a feature port, not a delta.

## Key finding: the client layer is monolithic

`ClientCompanion` (775 lines) and `ClientCompanions` (574) own all locomotion classes, party grouping, scene playback,
moods, hand visits and perching; they sit on the already-ported Minecraft-free `fishsim` engine. The only 26.1-specific
surface is rendering: three `submit(...)` points (`CompanionRender`, `CompanionFishPose`, the perch layer), the name tag,
the player-layer registration and four mixin targets. So F09, F10, F12 and the client half of F11 were ported together as
one compile-driven slice instead of as separate stages; what remains is server work, dev tooling, tests and scenes.

## What slice 1 ported (1.21.1)

| Area | Files (relative to `common/src/main/java/grill24/fishtastic/`) | 1.21.1 adaptation |
|---|---|---|
| Profile data | `data/FishProfile` (+gait/drift/group), `data/FishAnimationConfig`, `client/renderer/FishAnimator`, 9 `fish_profile` JSONs | none; datagen identical |
| Client sim layer | `client/companion/*` (13 files) | `GuiGraphicsExtractor` -> `GuiGraphics`; `Cat/Ocelot` package; `identifier()` -> `location()`; `Level.precipitationAt` -> `isRaining` + heightmap + `Biome.getPrecipitationAt`; `getOverworldClockTime` -> `getDayTime`; chunk/section accessors |
| Rendering | `client/renderer/Companion{Render,FishPose,FeatureLean}` | `SubmitNodeCollector` + `ItemStackRenderState` -> `MultiBufferSource` + `ItemRenderer.renderStatic`; name tag = `Font.drawInBatch` SEE_THROUGH billboard |
| Player layer | `CompanionPerchLayer`, `mixin/AvatarRendererMixin` (targets `PlayerRenderer`), `LivingEntityRendererInvoker` | `RenderLayer<AbstractClientPlayer, PlayerModel<...>>.render(...)`; no mannequins |
| Mixins | `ClientLevelCompanionMixin`, `LevelEventCompanionMixin` (-> `LevelRenderer.levelEvent`), `NoteBlockCompanionMixin`, new `LevelRendererMixin.blockChanged` | verified against the 1.21.1 sources jar |
| Wiring | Fabric `AFTER_ENTITIES`/`END_CLIENT_TICK`/HUD; NeoForge `RenderLevelStageEvent.AFTER_ENTITIES`/tick/GUI | replaces `COLLECT_SUBMITS` / `SubmitCustomGeometryEvent` |
| Packets / tags | `CompanionScenePacket` (+registration), `PREDATOR_FISH` tag + provider + JSON | none |
| Settings | `FishtasticClientConfig` (+153 lines), `FishtasticKeyBinds` | none |
| Dev | `FishingMinigameAnimation.devCatchTarget`, `RenderSelfTest` `companionsim` scene (+ clean overlay-free shots) | |

## Remaining stage-4 work (ordered slices)

1. **F11 server half** (~700 lines): `server/CompanionSceneManager`, `CompanionGhost`, `GhostConnection` (+ Fabric/NeoForge `GhostConnectionImpl`), `CompanionScenePacket` sending, tick hook, `CompanionSceneGameTests` (322), `CompanionGroupsGameTests` (118), `CompanionProfileGameTests` (66). Packet already registered.
2. **Dev client commands** (~520 lines `command/CompanionCommand` client-driven triggers): switch, mood, phantom, predator scene, ground/climb/tumble, catchdemo. Needs a client-command bridge (`ClientCompanionsDev` already ported). Unlocks most self-test scenes.
3. **Self-test scenes** (from `RenderSelfTest`, ~2500 lines): `companionparty`, `companioneel`, `companionscenes`, `companionlod`, `companionmoods`, `companionworld`, `companionparticles`, `companionworldcost`, `twoclient`. Port in groups; each is the evidence for F10/F11/F12.
4. **Owner-facing visual verification**: benthic on shoulder/ground, bob calm idle, jet/bell, the six A/B switches (all kept true). Hand visits in first person.
5. **Tutorial hooks** (F15): `CompanionTutorialManager` calls still `TODO(F15)`.

## Risks / unknowns

- Perched companions draw only in third person, by design; first-person hand path is untested on 1.21.1.
- The 1.21.1 `AFTER_ENTITIES` world pass uses the shared buffer source; translucent fish (glass squid) ordering vs other translucents is unverified.
- `LevelEventCompanionMixin` shadows `LevelRenderer.level` (non-final) — jukebox/note-block reactions unverified in-game.
- Performance of the sim step was not measured on 1.21.1 (`companionworldcost` scene not ported).
