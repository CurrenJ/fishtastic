# Gameplay Loop Audit — 2026-07-01

Findings from a 6-perspective subagent audit (onboarding, core loop/minigame feel, progression/economy,
quests/goals, collection/retention, systems integrity) of branch `26.1.2`. Tracked here as a working plan;
check items off as they're fixed.

## Critical — bugs/exploits

- [x] **Item duplication exploit.** Fixed: `FishingMinigameManager.handleMinigameComplete` now iterates a
  `LinkedHashSet` of `caughtTargetIndices` instead of the raw list, and `FinishFishingMinigamePacket`'s
  codec caps the list at `MAX_CAUGHT_INDICES = 16` so an oversized/replayed-index payload is rejected at
  the wire level.
- [x] **Bait balance regression.** Fixed: all 4 specialist baits in `FishtasticItems.java` now use
  `FishGroupAffinity` `4.0f` and `vanillaFishMultiplier` `0.25f` (previously silently reverted to
  `2.0f`/`1.0f`).
- [x] **Minigame tunneling.** Fixed: `FishingMinigameState` now tracks a swept bobber-position envelope
  (`getSweptMinPosition`/`getSweptMaxPosition`/`resetSweptRange`) accumulated across render-rate
  `updatePhysics` calls; `FishingMinigameAnimation.tick()` checks target overlap against that envelope
  instead of a single instantaneous position, then resets it each tick.
- [x] **Tutorial promises an ungranted reward.** Fixed: `tutorial/first_catch.json` now grants
  `{"id": "fishtastic:worms", "count": 4}` alongside the 15 tokens, matching the `quest_claim.body` copy.
- [x] **Inverted mastery reward curve.** Fixed: re-balanced token rewards for the Angler chain and all 4
  species mastery chains (bluegill/gar/moorish_idol/tetra/manta) so marginal tokens-per-catch is
  non-decreasing across tiers instead of dropping at harder tiers.

## High priority — design/content debt

- [ ] Every quest reward JSON has an empty `items` field (35/35) — docs promise rod-upgrade scrolls and
  quest-exclusive cosmetics as the point of Mastery/Challenge quests; none exist in code.
- [ ] No rod/bait upgrade system exists at all (`docs/rod-and-bait-upgrades.md` — "not yet implemented").
  This is the mod's only planned long-arc currency/quest sink and gear-driven minigame-difficulty lever.
- [ ] Skip-the-minigame exploit accepted silently: sub-20-tick completion only logs a warning and still
  awards full rewards (`FishingMinigameManager.java:215-218`) — pinned as an open decision by an existing
  game test, not actually resolved.
- [ ] Docs badly out of sync with code in both directions (`quest-system.md`,
  `rod-and-bait-upgrades.md` mark implemented things as "not implemented," while separately promising
  specific rewards — e.g. "Netherite Rod Scroll" — that don't exist).
- [ ] Singleplayer UUID inconsistency: `recordCatch` uses raw `player.getUUID()`
  (`FishingMinigameManager.java:230`) instead of `resolvePlayerKey`, so personal catch stats fragment
  across sessions in singleplayer even though quest progress doesn't.

## Medium — polish/UX

- [ ] No sound cue for catch/fail in the minigame (visual-only feedback for the most-repeated moment).
- [ ] Fish Encyclopedia keybind (K) never surfaced to players in tutorial/UI text.
- [ ] No completion percentage/counter on the Encyclopedia home screen.
- [ ] Only 5 daily quest templates (4 shown at once) — fully memorized within a week.
- [ ] Mastery objectives without explicit `notification_interval` spam a HUD toast per catch, overflowing
  the 5-slot notification queue.
- [ ] Shop rotation shows only 4 of 7 entries/day — can starve cosmetics or a bait needed for an active
  quest.
- [ ] No rate limit on Encyclopedia/leaderboard/quest-log request packets.

## Low — content gaps

- [ ] Only 2 tank cosmetics exist vs. the richer vision in `docs/tank-customization-expansion.md`.
- [ ] Hooks/charms are trivial common-ingredient crafts rather than quest/shop-gated prestige items.
- [ ] No repeatable/endgame content once Mastery/Explorer/Challenge are exhausted.
- [ ] Missing an explicit "craft a rod" tutorial step — defaults to a `WAITING_FOR_CAST` overlay that
  assumes the player already owns a rod.
