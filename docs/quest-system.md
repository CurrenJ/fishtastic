# Quest System

> **Status: Server-side implementation complete. Quest Log UI and quest JSON content not yet implemented.** See [feature-design-overview.md](feature-design-overview.md) for the full status breakdown.

Quests are structured goals that reward fishing skill. They are the **only source** of rod upgrade scrolls, special bait, and tank cosmetic unlocks — no crafting recipes for these items exist. Rewards are visual and experiential, not mandatory for progression.

---

## Data Structure

Quests are defined as JSON files under `data/fishtastic/fishtastic/quest/<quest_id>.json`, loaded at server startup as a datapack registry. Pack authors can add or override quests.

### Quest

| Field | Type | Description |
|---|---|---|
| `category` | enum | `daily`, `mastery`, `explorer`, `challenge` |
| `objective` | QuestObjective | What must be done |
| `reward` | QuestReward | What is awarded on claim |
| `prerequisite_quest_id` | Identifier? | Registry key of a quest that must be claimed first |
| `hidden` | bool | If true, quest is invisible until prerequisites are met |
| `display_name` | Text component | Shown in Quest Log |
| `description` | Text component | Shown as subtitle/tooltip |

### QuestObjective

| Field | Type | Description |
|---|---|---|
| `target_species` | Identifier? | Specific item key, e.g. `fishtastic:ocean_sunfish` |
| `target_species_tag` | TagKey? | Item tag, e.g. `fishtastic:ocean_fish` |
| `target_count` | int | Number of qualifying catches required |
| `min_quality` | FishQuality? | Minimum quality tier (COMMON → LEGENDARY) |
| `biome_condition` | TagKey? | Biome tag the hook must be in |
| `time_condition` | enum? | `DAY`, `NIGHT`, or `DAWN_DUSK` |
| `weather_condition` | enum? | `CLEAR`, `RAIN`, or `THUNDER` |

### QuestReward

| Field | Type | Description |
|---|---|---|
| `quest_tokens` | int | Added to player's token balance on claim |
| `items` | ItemStack list | Items added directly to inventory on claim |

---

## Categories

### Daily Catches
Repeatable, reset at server midnight. Low stakes; keeps casual players engaged session-to-session.

| Quest | Objective | Reward |
|---|---|---|
| Tidal Catch | Catch 5 ocean-biome fish | 5 tokens |
| Night Bite | Catch 3 fish at night | 5 tokens |
| Storm Chaser | Catch 1 fish during thunder | 8 tokens |

### Species Mastery
Per-species chains (3 tiers: 10 → 25 → 50 total caught). Each tier unlocks a cosmetic tied to that species. The primary long-term completionist track.

| Chain example | Tier 1 | Tier 2 | Tier 3 |
|---|---|---|---|
| Pike Hunter | Catch 10 Longnose Gar | Catch 25 Longnose Gar | Catch 50 → "Night Predator" cosmetic |
| Tetra Tracker | Catch 10 Neon Tetra | Catch 25 Neon Tetra | Catch 50 → cosmetic rod skin |

### Environmental Explorer
Single-completion quests that reward fishing in specific conditions and teach players what affects the catch pool.

| Quest | Objective | Reward |
|---|---|---|
| Molten Nights | Catch 1 Molten Moorish Idol at night during thunder | 10 tokens + exotic cosmetic |
| Deep Wanderer | Catch 3 species in deep ocean biome | 10 tokens |
| Exotic Expedition | Catch 5 exotic-pool fish (Blazed Grub required) | 15 tokens |

### Challenge
High-difficulty, one-time completions. Primary source of rod upgrade scrolls and rarest bait items.

| Quest | Objective | Reward |
|---|---|---|
| Legendary Seeker | Catch 1 Legendary Ocean Sunfish | 50 tokens |
| Quality Run | Catch 3 Epic+ fish (any species) | 25 tokens |
| The Grand Haul | Catch 1 Legendary of 5 different species | Netherite Rod Scroll |

---

## Quest Tokens

Quest Tokens are the single currency for cosmetic purchases. They do **not** purchase rod scrolls or special bait — those are direct rewards from specific quests.

**Approximate earning rates:**

| Category | Tokens per completion |
|---|---|
| Daily | 3–8 |
| Mastery (per tier) | 15–30 |
| Explorer | 10–20 |
| Challenge | 25–100 |

Tokens are spent in a dedicated Quest Shop UI (accessed from the Quest Log, or a block — TBD). Purchasable cosmetics: rod skins (texture-only, no stats), exclusive tank frames, background overlays, leaderboard profile badges.

---

## UI

A new GelatinUI Quest Log screen, accessed via keybind or from the leaderboard. Tabbed layout mirrors the existing leaderboard screen:

- **Tabs:** Daily | Mastery | Explorer | Challenge
- Each quest shows a progress bar (current / target), objective description, and reward preview
- **Claim** button activates on completion; sends `CompleteQuestPacket` to server
- **Profile** tab shows total tokens, unlocked cosmetics, milestone history

The existing leaderboard screen gains a small badge next to player names who hold prestige cosmetic unlocks.

---

## Server-Side Architecture

### Storage (implemented)
`PlayerQuestState` (in `server/PlayerQuestState.java`) holds a `Map<ResourceKey<Quest>, QuestProgress>` and a token balance. It is embedded in `FishCatchSavedData` under the `quest_states` codec field, keyed by player UUID.

```
QuestProgress
  currentCount: int
  lastResetGameDay: long   // game day of last daily reset
  completed: bool
  claimed: bool
```

### Tracking flow (implemented)
1. `FishingMinigameManager` captures biome, time, and weather at session start
2. After each rewarded catch, `QuestTracker.onCatch()` checks all quests
3. Daily pool is deterministically seeded by current game day — `Collections.shuffle(list, new Random(day))`, first 4
4. Matching quests increment progress; `setDirty()` + sync packet sent

### Claim flow (implemented)
1. Client sends `CompleteQuestPacket(questId)`
2. Server validates `canClaim()`, awards `reward.items()`, adds `reward.questTokens()` to balance
3. Server sends `QuestSyncPacket` back; `QuestClientCache` on client updates

### Daily reset (implemented)
`ServerTickHandler` checks `gameTime / 24000` once per tick; calls `FishCatchSavedData.resetDailyQuestsIfNeeded()` when the day advances.

---

## Quest JSON Examples

Place files under `data/fishtastic/fishtastic/quest/<id>.json`.

**Daily quest (catch 5 ocean fish):**
```json
{
  "category": "daily",
  "objective": {
    "target_species_tag": "fishtastic:ocean_fish",
    "target_count": 5
  },
  "reward": { "quest_tokens": 5, "items": [] },
  "hidden": false,
  "display_name": "Tidal Catch",
  "description": "Catch 5 fish from ocean biomes."
}
```

**Mastery chain tier 1 (catch 10 bluegill):**
```json
{
  "category": "mastery",
  "objective": {
    "target_species": "fishtastic:bluegill",
    "target_count": 10
  },
  "reward": { "quest_tokens": 15, "items": [] },
  "hidden": false,
  "display_name": "Bluegill Beginner",
  "description": "Catch 10 Bluegill."
}
```

**Challenge (legendary quality):**
```json
{
  "category": "challenge",
  "objective": {
    "target_count": 1,
    "min_quality": "legendary"
  },
  "reward": { "quest_tokens": 50, "items": [] },
  "hidden": false,
  "display_name": "Legendary Seeker",
  "description": "Catch any fish at Legendary quality."
}
```
