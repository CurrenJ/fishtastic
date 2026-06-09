# Tank Customization Expansion

> **Status: Design / Not yet implemented.** See [feature-design-overview.md](feature-design-overview.md) for context.

Expands the fish tank beyond frame/sand/glass into new visual dimensions, adds fish-reactive display behavior, and introduces prestige cosmetics unlocked exclusively via quests.

---

## New Customization Layers

Three new customization modes alongside the existing Frame / Sand / Glass:

### Water Tint & Lighting
A fourth customization mode. Store a `ColorRGB` (3 bytes) + `GlowLevel` (0–15, 1 byte) in `FishTankBlockEntity`.

The player applies a colored block (concrete, dyed glass) to set the ambient water tint. Rendering: blend a translucent quad behind items in `FishTankBlockEntityRenderer.submit()` using the stored color, with alpha scaled by glow level. When `GlowLevel > 8`, small tinted bubble particles spawn periodically from tank position.

### Background Overlays
A new `BackdropType` enum stored as a single byte in BlockEntity NBT.

```java
enum BackdropType { NONE, CORAL_REEF, DEEP_OCEAN, DARK_CAVE, BIOLUMINESCENT }
```

The renderer draws a flat textured plane (~0.95× tank bounds, slightly recessed) behind items using a second pass in `LevelRendererMixin`. All non-NONE types except BIOLUMINESCENT are freely selectable; BIOLUMINESCENT is quest-locked (see below).

### Animated Decorations
A `DecorationType` enum stored as a single byte.

```java
enum DecorationType { NONE, BUBBLE_STREAM, DRIFTING_ALGAE, SPARKLES }
```

Client-side only. Particle spawning tied to tank position, driven by `gameTimeTicks % 60` phase stored in `FishTankRenderState`. Minimal sync cost. Bubble phase offsets create natural-looking variation between adjacent tanks.

---

## Quest-Locked Cosmetics

These unlocks are stored as completion flags in `PlayerQuestData` (part of `FishCatchSavedData`) and checked by the renderer on each `extractRenderState()` call. They are **not** tied to vanilla advancements.

| Cosmetic | Quest trigger | What it unlocks |
|---|---|---|
| Golden Frame | Catch 10 Legendary fish | New frame block registry entry with gold-textured composite model |
| Bioluminescent Backdrop | Collect a full base species set (all common species) | `BackdropType.BIOLUMINESCENT` — cyan glow tint, auto-enables SPARKLES decoration |
| Sparkle Overlay | Store a Legendary fish in a tank for 30 minutes | Persistent sparkle particles activate whenever the tank contains a Legendary-quality item |
| Portuguese Man o' War Electro-Shader | Catch a Legendary Portuguese Man o' War | Crackling noise-based displacement shader around Man o' War when present in tank |
| Prestige Border | Fill all 27 tank slots with Epic+ fish | Animated pulsing aura rendered around tank edges in a second renderer pass |

### Unlock persistence across worlds
Cosmetic unlock flags live in `PlayerQuestData` (server `SavedData`), which is world-specific. To ensure a tank carries its earned cosmetics when moved to a new world, **unlocked cosmetic IDs are also stored as a data component on the `FishTankBlockEntity` itself** at the time of unlocking. The renderer checks both sources — per-player flags for interactive customization, per-tank component as the persisted proof.

---

## Fish-Reactive Display

The tank responds to the quality and identity of its contents without any player action. Computed during `extractRenderState()` and cached per frame.

### Legendary fish detection
Scan items in the tank for `FishQuality.Quality.LEGENDARY`. If found, set `state.hasLegendaryFish = true`. Renderer increases overall glow intensity and spawns golden sparkle particles. If the Sparkle Overlay quest cosmetic is unlocked, these particles persist even when the player isn't nearby.

### Full species set overlay
Scanned periodically (cached with 20-tick delay to avoid per-frame expense). If all base fish species are present in the tank, set `state.showCollectionOverlay = true`. Render a subtle centered badge texture in the tank interior. Acts as a visible "complete" indicator.

### Portuguese Man o' War electricity
When `extractRenderState()` finds a Portuguese Man o' War in the tank contents, apply an animated displacement shader via `GameRendererMixin` (already in use) and trigger crackling particle spawns using existing particle infrastructure. Only active when the Electro-Shader cosmetic is unlocked.

---

## Tank Size Progression

The existing multi-block system already supports arbitrary configurations via `calculateTankDimensions()`. This extends it with a formal progression tier.

### ExpansionLevel
Add `byte expansionLevel` (0–2: Base / Expanded / Mega) to `FishTankBlockEntity`, stored via `saveAdditional` / `loadAdditional`. Defaults to 0; existing tanks load safely.

| Level | Name | Unlock | Slots |
|---|---|---|---|
| 0 | Base | Default | 27 |
| 1 | Expanded | Quest: Catch 50 fish total | 54 |
| 2 | Mega | Quest: Complete 3 Mastery chains | 81 |

`getContainerSize()` returns `27 * (1 + expansionLevel)`.

When placing a tank adjacent to an existing expanded/mega tank, both must have compatible expansion levels. The composite model path changes per level: `fishtank_base.json` → `fishtank_expanded.json` → `fishtank_mega.json`. Corner and edge pieces detect direction via existing open-face tracking.
