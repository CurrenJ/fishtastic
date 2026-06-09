# Rod and Bait Upgrades

> **Status: Design / Not yet implemented.** See [feature-design-overview.md](feature-design-overview.md) for context.

Rod and bait upgrades are **quest-exclusive** — no crafting recipes. Each one changes how the minigame *feels*, not just numeric stats. Rod upgrades are permanent; bait upgrades are consumable (per-session).

---

## Rod Upgrades

The Copper Rod remains the only rod item. Upgrades are applied via **single-use Upgrade Scroll items** (delivered as quest rewards) that write to a new `RodUpgrades` data component on the rod in the player's main hand.

```java
record RodUpgrades(Set<RodUpgradeType> unlockedUpgrades) { }

enum RodUpgradeType {
    TEMPERAMENT_INSIGHT,
    COOLDOWN_MASTERY,
    SELECTIVE_TARGETING
}
```

Multiple upgrades can accumulate on the same rod. Upgrades are queried at session start in `FishingMinigameManager.startSession()` and passed to `StartFishingMinigamePacket`.

### Temperament Insight
**Quest source:** Mid-tier Challenge quest (e.g. "Catch 1 Legendary of any species")

**Minigame change:** When the session begins, the bobber bar shows dim vertical guide lines at each upcoming temperament phase threshold for all active targets. Skilled players can see when a fish is about to shift into a harder movement pattern and adjust their rhythm preemptively.

**Implementation:**
- Add `nextPhaseThreshold: float` to `FishingMinigameState`, computed from `Temperament.resolvedPhases()` during target generation in `FishingMinigameManager.generateTargets()`
- Render a secondary thin indicator (lower opacity, distinct color) at this position in `FishingMinigameAnimation`

### Cooldown Mastery
**Quest source:** High-tier Challenge quest

**Minigame change:** Halves the impulse cooldown between bobber pushes (faster, snappier feel), but targets drain catch progress 10% faster when the bobber isn't overlapping them. Trades reactive buffering for high-precision play. Impulse sounds play at higher pitch.

**Implementation:**
- Add `float impulseSpeedMultiplier` (default 1.0, set to 2.0) to `FishingMinigameState`
- Apply multiplier in `applyPlayerImpulse()` when upgrade is active
- Scale `CATCH_PROGRESS_LOSS` by 1.1 in `tickCatchProgress()`
- Override impulse sound pitch +0.3 client-side when active

### Selective Targeting
**Quest source:** Pinnacle Challenge quest (e.g. "The Grand Haul")

**Minigame change:** When three or more targets are active, press a keybind to lock the bobber to a specific target for 10 seconds. During the lock, the bobber auto-follows the target at 70% normal speed (reducing manual control), but catch rate on that target increases 30%. After the lock expires, all targets return to normal movement. The locked target glows with a tether line to the bobber.

**Implementation:**
- Add `Optional<Integer> lockedTargetIndex` and `int lockTicksRemaining` to `FishingMinigameState`
- New packet: `LockTargetPacket(int targetIndex)`
- In `FishingMinigameState.tick()`: move bobber toward locked target at 0.7× normal velocity; increase `CATCH_PROGRESS_GAIN` by 1.3; decrement and auto-clear `lockTicksRemaining`
- Client: render golden outline + tether line to locked target in `FishingMinigameAnimation.render()`

---

## Bait Upgrades

Quest-locked bait items delivered as direct quest rewards (not purchasable with tokens). They have pre-configured `BaitEffect` components and are consumed when loaded into the rod for a session, like normal bait.

### Radiant Bait
**Quest source:** Explorer chain completion reward

**Minigame change:** Sessions last 50% longer (9000 ticks vs 6000). Targets do not drain catch progress — you can't lose a fish, only run out of time. Treasure target rate increases to 25%. Good for players who want extended, relaxed sessions.

**BaitEffect values:**
```
luck_bonus: 1.5
treasure_chance: 0.25
mod_fish_multiplier: 2.0
quality_bias: 1.0
```

**Implementation:** Add `Optional<Integer> extendedSessionTicks` to `FishingMinigameManager.ActiveSession`. When this bait is active, skip drain logic in `FishingMinigameState.tickCatchProgress()`. Bar background shifts to warm gold hue; timer displays as a larger countdown.

### Celestial Bait
**Quest source:** High-tier Challenge reward

**Minigame change:** All targets spawn at 50% of their normal difficulty (very relaxed feel), but one Legendary item is guaranteed in the reward pool alongside normal catches. A "Legendary Treasure" label appears at session start. The bar glows with a starry overlay. All targets move noticeably slower.

**BaitEffect values:**
```
luck_bonus: 2.5
treasure_chance: 0.08
mod_fish_multiplier: 3.5
quality_bias: 2.0
exclusive_treasure: GUARANTEED_LEGENDARY
target_count_bonus: -1
```

**Implementation:** Extend `BaitEffect` with `Optional<String> exclusiveTreasure`. In `FishingMinigameManager.generateTargets()`, clamp difficulty to `difficulty * 0.5` when active. Inject one Legendary item directly into the reward pool before sending to client.

### Prismatic Bait
**Quest source:** Pinnacle mastery reward (requires completing multiple species chains)

**Minigame change:** Two independent bobber zones appear on the bar (distinct colors), falling with separate gravity. Both share one impulse input. Targets can be caught by either bobber. Sessions generate +2 targets on average. High skill cap — you must balance two falling weights simultaneously.

**BaitEffect values:**
```
luck_bonus: 0.3
treasure_chance: 0.12
target_count_bonus: 2
mod_fish_multiplier: 1.5
quality_bias: 0.2
```

**Implementation:** Add `Optional<Integer> secondaryBobberPosition` to `FishingMinigameState`. Flag dual-bobber mode in `StartFishingMinigamePacket` (`bool isDualBobber`). In `tick()`, calculate both positions and check overlap separately. Render two bobber zones in `FishingMinigameAnimation`.

---

## Summary

| Upgrade | Type | Source | Key feel change |
|---|---|---|---|
| Temperament Insight | Rod (permanent) | Challenge quest | See phase thresholds before they hit |
| Cooldown Mastery | Rod (permanent) | Challenge quest | Faster clicks, higher drain pressure |
| Selective Targeting | Rod (permanent) | Pinnacle quest | Lock and tether a specific target |
| Radiant Bait | Bait (consumable) | Explorer reward | No drain, longer session, relaxed |
| Celestial Bait | Bait (consumable) | Challenge reward | Slow fish, guaranteed Legendary |
| Prismatic Bait | Bait (consumable) | Mastery reward | Two bobbers, shared impulse |
