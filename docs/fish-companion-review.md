# Fish Companion — Review Checklist

> Two reviewers looked at [fish-companions.md](fish-companions.md) on 2026-10-10: one for
> **player experience**, one for **technical feasibility**. Then each checked the other's
> findings. **Every item below is agreed by both** unless marked otherwise.
>
> Put `y` or `n` inside each `[ ]`, add notes after `→`. Items marked **(changes your earlier
> answer)** reverse or narrow something you said yes to.

---

## R1. Rendering — how the pet is drawn

- [y] **R1.1** — Draw free-flying companions from a world-render hook (not a player render layer). A player layer is never drawn for your own player in first person, and it gets culled along with the player's box, so a pet waiting at the bobber would vanish whenever its owner is off-screen. Only perched poses (head/shoulder/back) use a player layer, the way vanilla draws parrots. *(changes I1's "player render layer"; still no entity)* →
- [ ] **R1.2** — The docked fish stays in the first-person-hand pass the whole minigame and is drawn **under** the HUD. A 3D fish with perspective can't be drawn in the HUD pass, so the "invisible handoff" from §5.1 is dropped. Being under the HUD also means it can never cover the bar or targets. → DEV NOTE: a 3d perspective fish can't be drawn in the HUD layer is a false assumption. Fish are rendered as item stacks and we already render perspective item stacks in the minigame layer. The whole minigame layer is modeled after the totem of undying animation, which is absolutely 3d perspective. Let's re-assess this.
- [ ] **R1.3** — During the catch celebration, the companion swaps to a flat on-screen fish (like the existing hero fish), with the cut hidden under the celebration's white flash. → DEV NOTE: pending the prior note decision
- [y] **R1.4** — Place the docked fish using the hand pass's own fixed 70° camera, so it's unaffected by FOV or sprint, and stays put through view bob. →
- [y] **R1.5** — Reuse the fish drawing and animation code (`FishAnimator`), but build a **new open-world motion controller**. The tank flock sim is bound to tanks, so water swimming (E1) and trio formations can't reuse it directly. → DEV NOTE: yes, but we should try to utilize the existing fish sim logic as much as possible, only adjusting where needed to fit the open-world constraint. No need to reinvent our fish wheel

## R2. Seeing your own companion

- [y] **R2.1** — **Rated near-blocker by both reviewers.** In first person you'd almost never see your own pet: perchers aren't drawn at all and orbiters sit at the edge of view. Add **"peek" moments**: every so often it swims into view, a percher hops off to show itself, and it hops onto your hand or just above the hotbar when you look down. → DEV NOTE: yes, and we can take advantage of held items by the player for meaningful interactoins and contextual reactions.
- [y] **R2.2** — Make "visible to the owner in first person at regular intervals" an acceptance criterion for the display-modes phase. →
- [y] **R2.3** — Pet and feed through the emote key or the Companions tab too, so you don't have to aim at a fish on your own head. →

## R3. Minigame docking

- [y] **R3.1** — The lanes beside the bar are already taken: the gear column is pressed against the left side and the zone icons against the right, at the top. The minigame publishes those columns' outer edges and heights, and the fish docks **beyond them, below them (lower lane), above the bar, or below its base**. →
- [y] **R3.2** — Drop the "starfish sits on the bar's top cap" and "eel pokes out from behind the bar edge" poses. A fish under the HUD art reads as hidden, not as interacting. *(narrows §5.2)* →
- [ ] **R3.3** — Cap the docked fish's motion: short poses when the state changes instead of constant swimming, so it doesn't pull your eye while you're playing. →DEV NOTE: maybe. This will depend on how it actually feels in-game. I'd prefer to just slow down movement at times than have any true idle time.
- [y] **R3.4** — Add a setting "companion waits at the bobber during the minigame" (skips docking). → DEV NOTE: yes, default to false (i.e. do NOT skip docks).
- [y] **R3.5** — Do the fly-past toward the camera only on catches that get a celebration tier, end it when the celebration is skipped, and use a quiet return otherwise. *(narrows your "yes" to the swim-back)* →

## R4. Storage & safety

- [y] **R4.1** — Store the companion as a **player attachment** (a per-player data slot both loaders provide, saved in the player's own file, kept on death, auto-synced to other players). Storing it in the existing global player data would let someone duplicate fish: bond → backup → unequip → `/fishtastic backup restore` gives two fish. →
- [y] **R4.2** — The companion never drops on death (follows from R4.1); write it into the doc. →
- [ ] **R4.3** — The server finds the chosen fish itself by matching every component of the item, in the inventory or inside a pile; it never trusts a slot number from the client. The ×N rows only group fish that are identical in **every** component, so "you get the exact fish back" holds. → DEV NOTE: hmm I am a bit confusefd by this. Server shouldn't be checking slots regularly once fish is equipped. The equipped fish should exit inv until they are unequipped. Server-authoratative. Can you clarify what this means?

## R5. Pet-to-pet scheduling

- [y] **R5.1** — **The server schedules the scenes**, sending one tiny packet per scene. The client-only version can't stop one pet being in two scenes at once with 3+ players, doesn't know whether the other player is mid-minigame, and can disagree at the edge of the 6-block range. Scenes run roughly once a minute per pair, so traffic is negligible. *(changes I2)* →
- [y] **R5.2** — Each companion is in at most one scene at a time. →
- [y] **R5.3** — A **guaranteed greeting** the first time two companions come near each other (otherwise it could take minutes). → DEV NOTE: yes, but not just the first time ever. If we have a cooldown, then we can just trigger the  greeting when two players approach each other. Cooldown should naturally limit it, and then when you walk up to a player you haven't seen in  abit, the scene is pretty much guaranteed.
- [n] **R5.4** — Gaps between repeat scenes grow for the same pair, so two people fishing together for hours don't see the same scene every ~80 s. → DEV NOTE: I don't want to start introducing saved history between specific pairs of fish. Complicates things. This scene fatigue should be handled through balancing and tuning.
- [y] **R5.5** — No scene starts while either owner is in the minigame. →
- [y] **R5.6** — A fixed priority order when several pairings apply (e.g. same species beats wily). →

## R6. Encyclopedia thresholds

- [y] **R6.1** — My blazed grub example in §9 was **wrong**: it's 4 of the Nether's 110 total weight, 6th of 9 species, so it's rare there, not common. At its current "common" thresholds it takes about **825 Nether catches** to complete, the worst grind in the mod. → DEV NOTE: no action has been attached to this note, but I'm assuming you mean that we should fix the thresholds. Yes. 
- [y] **R6.2** — Measure rarity against what can actually be caught at a location (deep ocean pulls from ocean **and** deep ocean; mountain rivers pull from river **and** high altitude), not per single zone. Leave quest-only unlisted fish out of the totals. →
- [y] **R6.3** — Set tier cut-offs so the expected catches to fully complete land in a **target band (≈60–300)**, rather than copying today's presets. Under realistic pools some deep-ocean and Nether rares sit at ~450–825, so the rare tier's lore count may need to drop below 6, or a few weights may need nudging. → DEV NOTE: yes, but that range is still WAY too high. 300 catches for one fish is ridiculous. Cap at maybe 50 or 100? Leaning toward 50 as absolute max completion threshold.
- [y] **R6.4** — **Grandfathering:** a reveal the player has already seen or claimed never re-locks, and a bond once unlocked stays unlocked. It's cheap: claimed rewards are already stored per player. →
- [y] **R6.5** — "Complete" means catch counts meet every derived threshold (as the server checks today), not that every reward has been claimed. →

## R7. Behaviour polish

- [y] **R7.1** — Biome moods respect the species' home: Nether fish are happy in the Nether, and cold-water fish are fine in snow. →
- [y] **R7.2** — Triggers that could stay on during normal play need a dwell time, then habituation and a cooldown: pet cats nearby, standing next to your own tank, item streams for ambushers, docile nuzzling. →
- [y] **R7.3** — The tank "fish gather toward the companion" effect (E4) needs a second attractor slot. The current one is your own eye position, so the companion would replace you rather than add to you. → DEV NOTE: tank interactions are a nice to have, but somewhat complex so I think we should defer it as a planned improvements and not keep in first draft of companions.
- [y] **R7.4** — Ghost-temperament fish (8 species, incl. giant manta ray and ocean sunfish) get a timing-only tweak (long, still hovers), with no transparency or phasing. *(player-experience reviewer only; optional)* →
- [y] **R7.5** — Respect vanilla's particle setting (trios, glows, embers and sparkles add up per player). →
- [y] **R7.6** — The bond status line shows "Bond: N/M catches" (the lore threshold is always the furthest one, so naming the tier adds nothing). →
- [y] **R7.7** — A toast or tutorial step the first time any bond becomes available, so the feature is discoverable. → DEV NOTE: absolutely. Let's have a first unlock FTUE, and then banner notificcations for new bond unlocks.

## R8. Scope & plan

- [y] **R8.1** — **v1 = phases 0–5 plus pet-to-pet scenes G1 and G2**; the other scenes and the personality/world/player phase come later. Both reviewers note how the combinations multiply: 5 classes × animation modes × 9 temperaments × trio/solo × water. →
- [y] **R8.2** — The phase 0 spike covers: own-pet visibility in first person, hand-pass-only docking with column-edge anchors, the flash cut, and the open-world motion controller feeding `FishAnimator`. Drop the "render layer cost" spike: performance is low-risk next to tanks that already draw up to 512 fish. →
- [y] **R8.3** — Pet-to-pet (phase 6) depends on the server knowing who is fishing, so it's built after the fishing-reactions phase. →

---

## Things both reviewers said the design gets right

- The fish item *is* the companion: one stack, exact item back, nothing to lose.
- Moving in world space and only changing the target, with docking on the owner's screen only.
- Everything derived from existing profile fields, with a "companion parade" to verify it.
- Encyclopedia unification can ship on its own, and deriving thresholds only (no override) is fine technically.

## Open notes

→
