# Stage 6 scope: onboarding and guided companion lessons (F15)

Measured by applying source `465fe891` (and the companion-FTUE pieces of `802470b9`) to `port/1.21.1`
and compiling, not by grepping. Source snapshot `48fffdfb`.

## What was missing on the target

- Whole files: `CompanionTutorialStep`, `CompanionTutorialManager`, `CompanionTutorialSyncPacket`,
  `CompanionTutorialAdvancePacket`, `CompanionTutorialClientHandler`, `CompanionTutorialManagerGameTests`.
- Persistence: `FishCatchSavedData.companion_tutorial_steps` (+ get/set, `hasPlayer`, copy-all, copy-player).
  Serialized enum values come from the source `CompanionTutorialStep.CODEC` / `TutorialStep.CODEC`
  verbatim; `TutorialStep` default is now `NOT_STARTED` (was `WAITING_FOR_CAST`), as in the source.
- Edits to existing onboarding code (`465fe891`): `TutorialManager`, `TutorialStep`, `EncyclopediaTutorialManager`,
  `TutorialAdvancePacket`, `TutorialCommand`, `TutorialClientHandler`, `EncyclopediaTutorialClientHandler`,
  `ItemHighlightRules`, `GelatinOpenMenuCompat`, book/rod items, `FishingHookMixin`, `FishingMinigameManager`,
  `RequestFishEncyclopediaPacket` (drops the auto-offer call), lang, `first_catch.json`.
- Screens: guided layout in `CompanionScreen`; guide labels and tab breathing in `QuestLogScreen`
  (also needed the F08 `selectOnNextOpen(ResourceKey<Quest>)` focus + `pulseQuestRow`, absent from the target);
  `FishEncyclopediaScreen.highlightCompanionEntryButton`.
- Every `TODO(F15)`: `Companions` (equip/return/rename/lead), `CompanionMenu` (return paths),
  `OpenCompanionMenuPacket` (Companions tab), `CompanionBondUnlocks.refresh` and `/fishtastic companion unlock`
  (`onBondUnlocked`).
- Loader clients: companion sync handler, reset on disconnect, screen overlay render.
- GameTests: 3 new `TutorialManagerGameTests`, 6 `CompanionTutorialManagerGameTests`, registered through the shared
  `FishtasticGameTests` wrapper (not the per-loader registration files).
- Scenes: `tutorial`, `companiontutorial` (opt-in).

## Slices (1.21.1)

1. `0de14496` server half, persistence, packets, hooks, GameTests.
2. `bde8a310` client handler, screens, HUD and loader wiring.
3. `c28e37a5` scenes.

## 26.1 -> 1.21.1 differences hit

`GuiGraphicsExtractor` -> `GuiGraphics` (`renderOutline`, `drawString`); `Identifier`/`identifier()` ->
`ResourceLocation`/`location()`; `ContainerInput` -> `ClickType`; no `ItemStackTemplate` (bundle contents hold
stacks); `getToastManager` -> `getToasts`; `Button.onPress()` takes no argument. HUD: the target draws
tutorial/minigame/notification layers through `FishtasticHudLayers`; the companion book hint is added to
`renderTutorial`.

## 1.20.1 mirror notes (not started)

Same file set; no data components; party is save-data; `GuiGraphics` API matches 1.21.1 mostly; verify `ClickType`,
`Button.onPress`, `Inventory.selected`, toast accessor and the old `PoseStack` HUD path by compiling.
