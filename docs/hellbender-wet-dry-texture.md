# Hellbender wet/dry texture — feature spec

Status: implemented on 26.1.2, committed 94bf3a3a (2026-10-28). Unit tests and datagen done; owner in-game look
pending. Ports (1.21.1 / 1.20.1) not started. See "Implementation notes" at the end.

## Goal

The hellbender shows two textures depending on where it is drawn:

| Context | Texture |
|---|---|
| In water: fish tank, wild schools / ambient fish / in-world encounters, a companion that is under water | `textures/item/fish/hellbender.png` (default, "wet") |
| Out of water: inventory and GUI, held in hand, item frame, dropped item, fish pile block, a companion on land | `textures/item/fish/hellbender_sheen.png` ("dry") |

Both textures are already staged (32×32 each).

## Design

Nothing about the item knows where it is being drawn. Every in-world fish renderer calls
`ItemModelResolver.updateForTopItem(..., ItemDisplayContext.FIXED, null, null, 0)` with no
level and no entity, so a vanilla item-model property has nothing to read. The fix: the
renderer says whether the fish is wet, and the item model picks the texture based on that.

1. **A render-scope flag.** Add a small client helper, `FishRenderMedium` (in `client/renderer`),
   that holds a render-thread `wet` flag. It defaults to **false (dry)**, so any context this spec
   doesn't list (vanilla GUI, hand, item frame, item entity, fish pile) gets the sheen without
   extra work.
2. **A conditional item-model property `fishtastic:submerged`.** Register it in the
   conditional-property ID mapper. `FishtasticClientSetup.registerItemModelTypes` already does
   this for `ItemModels.ID_MAPPER`, using reflection because NeoForge's access widener doesn't
   work there. The property returns `FishRenderMedium.wet()`.
3. **A conditional item model for the hellbender.** `items/hellbender.json` becomes a
   `minecraft:condition` on `fishtastic:submerged`: `on_true` uses `item/hellbender`, `on_false`
   uses a new `item/hellbender_sheen` model. Write it in
   `FishtasticModelProvider` (fabric datagen, line 136), not by hand, or the next datagen run
   overwrites it. Give the provider a `generateWetDryFishItemModel(item, name)` helper so any
   later species can opt in with one line. No `fish_profile` change is needed.
4. **The renderers set the flag** around their `updateForTopItem` call:
   - `FishTankBlockEntityRenderer` (both call sites): always wet.
   - `GroupFishDraw.submit` (schools and ambient fish): always wet, including mid-jump
     (decision 1).
   - `CompanionRender` / `CompanionFishPose`: wet if the fluid at the fish's drawn body centre
     is water (`FluidTags.WATER`). This is the same check `ClientCompanion` already does for the
     owner. Sample it per fish, not per party, so a fish half out of a pond flips on its own.
   - All other callers stay dry by default.

   Use a try/finally (or an `AutoCloseable` scope) so a renderer that throws can't leave the
   flag stuck on wet.

## Edge cases

- **Waterline flicker (companions).** A fish crossing the surface could switch textures every
  frame. Hold the last state for a few ticks (hysteresis) before switching, keyed per drawn fish.
- **Encyclopedia silhouette.** `SilhouetteItemButton.spriteFor` reads
  `textures/item/fish/<id>.png` directly, so it always uses the wet texture. That is harmless as
  long as both PNGs have the same alpha mask, because the silhouette is drawn as a dark tint.
  Check this. If the masks differ, add a `_sheen` lookup there.
- **Outline atlas / glint.** `FishtasticItemOutlineAtlas` and the outline effects work from the
  resolved render state, so they follow the chosen texture automatically. Check that the outline
  atlas registers the sheen sprite.

## Ports (1.21.1 / 1.20.1)

Neither version has `items/*.json` model definitions. Use the same `FishRenderMedium` flag
behind a registered `ItemProperties` predicate (`fishtastic:submerged` returns 0/1), plus an
`overrides` entry in `models/item/hellbender.json` that points to `item/hellbender_sheen` when
the predicate is 0. The renderer changes in step 4 port unchanged.

## Debug trigger

A client-side dev command, `/fishtasticambient fishmedium wet|dry|auto` (no argument prints the
current mode), forces the texture everywhere. It lives on the client command root because the
flag is client-only. This lets the owner compare both textures in a solo game without carrying a fish
in and out of water.

## Verification

- Unit test: the property reads the flag, the flag defaults to dry, and the scope restores
  the flag after an exception.
- Datagen: rerun it and confirm the `items/hellbender.json` diff is exactly the condition.
- In game, on all loaders: inventory/hand/item frame/dropped show the sheen; a tank and a wild
  school show the wet texture; a companion switches as it crosses a shoreline without flicker.

## Decisions (owner, 2026-10-28)

1. **Surface jumps: no switch.** A school hellbender keeps the wet texture while airborne, so
   `GroupFishDraw` always draws wet.
2. **Dropped item floating in water: dry.** It keeps the sheen, which is the default; no
   item-entity handling.
3. **Caught-fish reveal / fishing minigame: dry.** The hooked fish shows the sheen, which is the
   default; that path needs no change.

## Implementation notes (2026-10-28)

- `client/renderer/FishRenderMedium.java`: the flag, the `Forced` dev override, the
  `Submerged` property record, and `resolve(resolver, state, stack, wet)`. That method wraps the
  in-world renderers' `updateForTopItem(..., FIXED, null, null, 0)` call in a try/finally.
- `FishtasticClientSetup.registerConditionalProperties()` puts `fishtastic:submerged` into
  `ConditionalItemModelProperties.ID_MAPPER` by reflection. It is idempotent and called from
  `registerItemModelTypes` on all three loaders. Datagen calls it too, because encoding the
  condition looks the id up.
- Tanks (both call sites) and `GroupFishDraw` pass `wet = true`.
- `CompanionRender.wetAt(index, pos, gameTime)`: water at the fish's drawn centre (feet plus the
  pose lift for ground walkers), with a 4-tick hold. A swimming companion uses it in `submit`;
  a perched one passes `wetAt(i, member(i).position(), …)` to the new `wet` parameter of
  `CompanionFishPose.submit`.
- Encyclopedia silhouette: the two PNGs have identical alpha masks (0 differing pixels), so it
  needs no change. The outline atlas bakes from the resolved render state, so it follows the
  chosen texture.
- Datagen: `generateWetDryFishItemModel` in `FishtasticModelProvider`. The rerun also rewrote
  coelacanth's model files (lost their trailing newline) and reordered `predator_fish.json`;
  those are unrelated datagen drift and were reverted.
- Tests: `FishRenderMediumTest` (4 tests) covers default dry, set/restore, the forced override,
  and restore-on-throw.
