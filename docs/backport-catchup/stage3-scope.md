# Stage 3 scope note — companion gameplay (F07/F08)

Written 2026-10-17 (session 3, fork STAGE3A-1211). Source snapshot `48fffdfb`; target 1.21.1 `port/1.21.1` at `72f19d0b`.

## Key finding: the target has NO companion gameplay code

`4b0e818e` pre-dates companions v1 (source commits `787a4097` Phase 2 … `c9756261` Phase 6) and everything after. The 1.21.1 target contains no `companion/`
package, no companion menu, packets, commands, tutorial, scene manager, client companions, mixins or tests (only the `fishsim` module, F01 `EncyclopediaTiers` and F03). So stage 3/4 are
**new-feature ports of ~18.8k added lines across ~127 files** (`git diff --shortstat 4b0e818e..48fffdfb`), not deltas on existing code. Size every later slice accordingly; the plan's "adapt the new output code to existing rendering" still holds, but there is no gameplay base to extend.
Existing 1.21.1 seams reused: `FishtasticItemData` facade (needs `matches`, custom name, food, tooltip style additions), `PlayerQuestState`, `Quest`, `QuestSyncPacket`, `FishtasticPackets`, `FishtasticMenuTypes`.

## Minimum F07 set (self-contained, this fork)

| Piece | Source | Notes |
|---|---|---|
| `PlayerQuestState` bond-unlock set + pending banner queue + codec field `unlocked_companion_species` | server/PlayerQuestState.java | pure, unit-testable (`PlayerQuestStateCompanionBondsTest`) |
| `Quest.unlocksCompanion` (`unlocks_companion`) + `companionUnlockedBy` | data/Quest.java | record gains a field: every `new Quest(` call site (datagen, tests) must be updated |
| `CompanionParty`, `CompanionStorage` (+ `@ExpectPlatform`) | companion/ | `ItemStack.OPTIONAL_CODEC` / `OPTIONAL_STREAM_CODEC` exist on 1.21.1 |
| Fabric/NeoForge `CompanionStorageImpl` | fabric/, neoforge/ | Fabric attachment API (`AttachmentRegistry`, `syncWith`, `copyOnDeath`) and NeoForge `AttachmentType` (21.1 has `serialize(MapCodec, shouldSave)`, `sync(StreamCodec)`, `copyOnDeath`). Verify by compile |
| `FishtasticItemData.matches` (+ custom name/food/tooltip facade items, only as needed) | FishtasticItemData.java | `matches` here; the rest belong to F08 |
| `CompanionBondGate`, `CompanionBondUnlocks` (+ `CompanionBondUnlocksTest`) | companion/ | uses `EncyclopediaTiers` (done), `FishCatchSavedData.getPersonalCatchCounts`/`resolvePlayerKey`/`getOrCreateQuestState` (verify exist) |
| `QuestSyncPacket` fields `unlockedCompanions`, `newlyUnlockedCompanions` + `CompanionBondUnlocks.refresh` in `sendToPlayer` | network/QuestSyncPacket.java | client consumers (`QuestClientCache`) must compile; banner UI deferred to F08 |
| Quest data JSON | resources/data | `unlocks_companion` is optional; no quest content needs to change for F07 |

## Deferred out of F07

- `CompanionTutorialManager.onBondUnlocked(player)` call inside `CompanionBondUnlocks.refresh` (first-unlock FTUE) → **F15**; the call site is omitted until then and must be re-added when the tutorial lands.
- `Companions` (server-side bond/unequip/rename writes; needs feedback packets and menu), `CompanionGroups`, `CompanionSceneType` → F08/F10/F11.
- Client banner for `newlyUnlockedCompanions`, Companions tab/`CompanionScreen`, `CompanionClientState`, `CompanionBondInput` → F08.
- Syncing the attachment to clients is wired by each loader's attachment API; the client-side readers (`ClientCompanions`) → F09.
- `CompanionBondGameTests`, `CompanionQuestUnlockGameTests` need `Companions`/menu: port with F08. `CompanionGroupConfigTest` with F10.

## Loader backing differences

- Fabric: `AttachmentRegistry.create(id, builder -> persistent(CODEC).copyOnDeath().syncWith(STREAM_CODEC, AttachmentSyncPredicate.all()))`; `setAttached`/`removeAttached` (empty party removes the attachment).
- NeoForge: `DeferredRegister<AttachmentType<?>>` (`register(modEventBus)` from mod constructor), `serialize(MAP_CODEC, !isEmpty)`, `sync(STREAM_CODEC)`, `setData` syncs itself.
- Both register the id `fishtastic:companion`; retired single-stack form is not migrated (per plan).

## Proposed commit slicing

| Slice | Content | Est. size |
|---|---|---|
| F07-1 | PlayerQuestState + Quest field + unit tests | ~150 lines |
| F07-2 | CompanionParty/Storage + both impls + `matches` + registration + a round-trip test | ~300 lines |
| F07-3 | BondGate/Unlocks + QuestSyncPacket fields + tests | ~250 lines |
| F08-1 | Companions (writes), Bond/Rename/Feedback/OpenMenu packets, `FishtasticItemData` name/food/style facade, CompanionBondGameTests | ~900 lines |
| F08-2 | CompanionMenu + menu type + CompanionMenuGameTests (457 lines of test) | ~700 lines |
| F08-3 | CompanionScreen/CompanionClientState/CompanionBondInput + Companions tab in encyclopedia + lang + banner | ~1000 lines + assets |
| F08-4 | `/fishtastic companion` command (dev triggers) + CompanionGameTests | ~800 lines |
Then F09+ (client companions ~3k lines, renderers, mixins). Each of F08-2/3 likely needs its own fork; F08-3 needs gelatin-ui or vanilla screen API checks on 1.21.1.

## Status update (STAGE3B-1211)

F08-1 done on 1.21.1 (`1d3562d3`, `ca728245`). `OpenCompanionMenuPacket` and `TakePileFishPacket` moved to F08-2 because they reference `CompanionMenu`. The three `CarriedFish` picker GameTests move to F08-3. All `CompanionTutorialManager` hooks are `TODO(F15)` comments in `Companions`; grep `TODO(F15)` when the tutorial lands.

## Status update (STAGE3C-1211)

F08-2 done on 1.21.1 (`8182a9df`, `c349169d`): CompanionMenu, menu type, OpenCompanionMenuPacket, TakePileFishPacket, 18 menu GameTests (311/311 both loaders). Remaining: F08-3 (screen/client state/bond input/tab/banner/feedback handler/CarriedFish picker tests), F08-4 (command + CompanionGameTests).

## Status update (STAGE3D-1211)

F08-3 done on 1.21.1 (`0260b02c`, `a5d2f9bd`): CompanionScreen, CompanionClientState, CompanionBondInput, CarriedFish, bond-unlock banner, encyclopedia bond heart/Companions section, SilhouetteItemButton badges, lang, companions.png, AbstractContainerScreenMixin hold-to-bond, loader screen/key/feedback registration (314/314 both loaders). Deferred: guided tutorial layout (`TODO(F15)`), party preview line (`TODO(F10)`: needs CompanionGroups). Remaining: F08-4 `/fishtastic companion` command + `CompanionGameTests`. RenderSelfTest bond scenes (`bond.*`, source lines ~5066-5250, 6221+) not ported.

## Status update (STAGE3E-1211)

F08-4 done on 1.21.1 (`a1309104`, `818801ce`): reduced server-side `/fishtastic companion` (on, off [slot], give, catchcount, unlock, resetbonds; dev-only registration like the source) and `CompanionGameTests` (12: 11 bond/unequip/party tests + `companionIsKeptOnDeath`). The death test needs each loader's respawn hand-off, so `FishtasticTestSupport` gained `installRespawnHandoff` (Fabric: `restoreFrom` + `AFTER_RESPAWN`; NeoForge: `restoreFrom`, which fires Clone). Fabric/NeoForge 326/326; `:common:test` 773 total/1 skipped.
Not ported from `CompanionCommand` (need later features): all client-driven dev triggers (debug, switch, temperament, fake, mood, ground/climb/tumble, swell, squeeze, catchdemo/catch, phantom, minigame/stopgame, hideothers/hideown, feature, hurt, drowsy) -> F09/F10; predatorscene + ghost -> F10/F11; `unlock` omits `CompanionTutorialManager.onBondUnlocked` -> `TODO(F15)`.
Stage 3 accounting (source files changed `4b0e818e..48fffdfb` with companion/bond in the name, absent on target): only F09-F15 items (client companion/render/world/perch/mixins, groups, scenes, ghost, tutorial classes, OwnerFallTracker, WorldFeatureIndex, group/scene/profile/tutorial tests) plus docs and dev tooling (`tools/mc-gui-designer/companions.json`, twoclient/render scripts). No F07/F08 source class or test is missing.

## 1.20.1 status (STAGE3G-1201)

Server side mirrored: F07 (`28c9ae2a`, `6b25a7f1`, `46b5dd3b`), F08 slice 1 (`0cf7e73a`, `c6e0d9f1`), slice 2 (`3b01df4b`, `ee077cb6`), slice 4 (`13f19b8f`, `a4bbdea0`). Party storage is a UUID-keyed save file (no attachment API on Forge 47 / Fabric 0.92). Remaining: slice 3 client UI (+3 picker GameTests).

## 1.20.1 status (STAGE3H-1201)

Slice 3 mirrored (`8f82b701`, `93565bd3`); 326/326 on Fabric and Forge, `:common:test` 781/1 skipped. Stage 3 complete on both targets modulo client verification and the F10/F15 deferrals.
