# Feature Design Overview

> **Status: In progress.** Core data model, server-side tracking, worm bin, and targeted fishing are implemented. Quest Log UI and quest JSON content remain.

Three planned feature pillars for Fishtastic's next development phase. Each system reinforces the mod's core identity: **fishing is the experience, not a means to an end.** Every reward either changes how the minigame feels or makes the tank display more impressive.

---

## The Loop

```
Fish well
  → complete quests
  → earn quest tokens + upgrade scrolls + special bait
  → unlock rod upgrades (permanent) + bait upgrades (consumable)
  → deeper / different minigame feel
  → harder quests become achievable
  → unlock prestige tank cosmetics
```

Nothing in this loop routes the player away from fishing. No crafting chains that consume fish. No mandatory gates. Quests reward fishing skill; rewards deepen the experience or enhance the display.

---

## The Three Pillars

| Document | What it covers |
|---|---|
| [quest-system.md](quest-system.md) | Structured goals that reward fishing skill; the source of all meaningful progression |
| [rod-and-bait-upgrades.md](rod-and-bait-upgrades.md) | Quest-exclusive unlocks that change how the minigame feels |
| [tank-customization-expansion.md](tank-customization-expansion.md) | New visual layers, fish-reactive effects, and prestige cosmetics for tanks |

---

## Design Principles

**Minigame-first.** New features layer on top of the reflex loop, not replace or route around it.

**Destinations reinforce fishing.** A destination strengthens the identity when it makes players want to fish more deliberately — choosing a species, targeting a quality tier, picking the right bait. It harms the identity when it makes catching fish feel like a chore.

**Upgrades change feel, not just numbers.** A rod upgrade that halves cooldown *and* increases drain rate is a tradeoff. A bait that spawns two bobbers is a new experience. Neither is simply "+10% fishing speed."

**Cosmetics as prestige, not gatekeeping.** Tank cosmetics unlocked by quests should feel like achievements. The mod is fully playable without them.

---

## Implementation Status

### Done
- **Quest data model** — `Quest`, `QuestObjective`, `QuestReward`, `QuestCategory` records with codecs; datapack registry (`FishtasticRegistries.QUEST_REGISTRY_KEY`) wired on both NeoForge and Fabric
- **Server storage** — `PlayerQuestState` (progress map + token balance) embedded in `FishCatchSavedData`; daily reset in `ServerTickHandler`
- **Quest tracking** — `QuestTracker.onCatch()` checks species, tag, quality, biome, time, weather; called after each rewarded catch in `FishingMinigameManager`
- **Network layer** — `CompleteQuestPacket` (client→server claim), `QuestSyncPacket` (server→client state sync); `QuestClientCache` on client; login sync on both platforms
- **Quest Token item** — `QUEST_TOKEN` registered in `FishtasticItems`
- **Worm Bin** — `WormBinBlock` + `WormBinBlockEntity` fully implemented (deposit fish → CONVERTING → aerate → READY → harvest worms); blockstate JSON, block models, item model all stubbed
- **Targeted fishing** — `BaitEffect.FishGroupAffinity` field added; `FRESHWATER_BAIT`, `OCEAN_BAIT`, `PREDATOR_BAIT` registered with 2.5× group multiplier; fish group tags (`freshwater_fish`, `ocean_fish`, `deep_sea_fish`, `predator_fish`) populated

### Remaining
1. **Quest Log UI** (`QuestLogScreen`) — GelatinUI screen with DAILY/MASTERY/EXPLORER/CHALLENGE/PROFILE tabs, progress bars, Claim button; keybind (default K); register in `GelatinScreens`
2. **Quest JSON content** — actual datapack JSON under `data/fishtastic/fishtastic/quest/`; see [quest-system.md](quest-system.md) for the full content spec
3. **Textures** — new items (freshwater_bait, ocean_bait, predator_bait, quest_token) and worm_bin block currently use worms/dirt placeholder textures
4. **Crafting recipes** — bait crafting recipes (e.g. worms + lily pad → freshwater bait)
