# Feature Design Overview

> **Status: Design context, not an implementation tracker.** The quest data, tracking and Quest Log UI described here have since been implemented. Use the relevant system documentation and source for current behavior; the original feature checklist has been removed below.

Three feature pillars frame Fishtastic's progression: quests, upgrades and tank customization. Each reinforces the mod's core identity: **fishing is the experience, not a means to an end.** Every reward should change how the minigame feels or make the tank display more impressive.

---

## Intended loop

This is the design target, not a status report of currently shipped progression.

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
| [fish-tanks.md](fish-tanks.md) | The tank's customization axes (shape, material, cosmetics), how its geometry is generated, and how to add more |

---

## Design Principles

**Minigame-first.** New features layer on top of the reflex loop, not replace or route around it.

**Destinations reinforce fishing.** A destination strengthens the identity when it makes players want to fish more deliberately — choosing a species, targeting a quality tier, picking the right bait. It harms the identity when it makes catching fish feel like a chore.

**Upgrades change feel, not just numbers.** A rod upgrade that halves cooldown *and* increases drain rate is a tradeoff. A bait that spawns two bobbers is a new experience. Neither is simply "+10% fishing speed."

**Cosmetics as prestige, not gatekeeping.** Tank cosmetics unlocked by quests should feel like achievements. The mod is fully playable without them.

---

The original implementation checklist is omitted because it is stale and was not maintained as a
status source. The loop and principles above remain the design context for [quests](quest-system.md),
[rod and bait upgrades](rod-and-bait-upgrades.md), and [fish tanks](fish-tanks.md).
