# Fish Companions

> **Status: Design.** Built from the yes/no pass in [fish-companion-ideas.md](fish-companion-ideas.md),
> the owner's follow-ups, and the two-reviewer pass in [fish-companion-review.md](fish-companion-review.md)
> (all 2026-10-10). No open decisions remain; R3.3 (docked motion amount) is tuned in game.

A bonded fish that swims in the air around the player. It's purely cosmetic. Its body plan comes
from the species' locomotion class and its personality comes from the temperament. It reacts to
fishing, to the world, to what you're holding, and to other players' companions.

---

## 1. Principles

- **Fishing buddy first** (A1). The fishing reactions (§5) get the most polish.
- **Cosmetic only** (A2). No stats, no fishing hints.
- **Data-driven from existing fields.** Locomotion class, `animation.mode`, temperament, quality,
  size, `render_calibration`, zones, biome weights and time/moon weights already describe how a
  fish should behave. Companions read them. Only the signature companions (§8.3) get per-species
  authoring.
- **Reuse the fish sim; don't reinvent it.** The tank simulation is bound to tanks, so companions
  need an open-world motion controller. That controller should reuse the existing sim's
  locomotion and steering logic, the animation inputs (`renderPhase`, `speedFactor`, `bank`,
  `renderShape`) and `FishAnimator`. It only adapts what the open world forces: no tank walls, a
  moving anchor, and an unbounded domain.
- **The owner must actually see their companion.** In first person, perched and orbiting fish
  are mostly out of view. "Peeks" (§3.1) are a requirement, not polish.

---

## 2. Count: trio or solo (A3)

Small schooling fish come as a **trio**: one leader on the display-mode path and two followers in
formation, reusing the sim's swarm offset logic. Everything else comes **solo**.

Rule: trio if the species is `FREE_SWIM` **and** the bonded fish's size is under ~20 cm.

---

## 3. Display modes (B1–B8)

| Class | Anchor | Idle | Tricks |
|---|---|---|---|
| `FREE_SWIM` | Orbits the shoulders, ~1.2 blocks | Lazy figure-eights, banks into turns | Quick lap around the player |
| `GLIDE` | Wide slow arcs overhead | Kite-like wing flaps | Low swoop that kicks up block-dust particles from the floor |
| `DRIFT` | Behind one shoulder on a loose spring | Pulse (existing squash-and-stretch) | Rises and settles; glows at night |
| `BENTHIC` | Perched on the head or a shoulder | Shuffle or tilt | Spin, hop, slide down the arm and climb back |
| `ANCHORED` | Pokes out of the back or helmet | Sway | Ducks in when a hostile mob is within ~3 blocks |

`animation.mode` refines each class (`upright_float` stays upright, `belly_down` stays flat,
`planted` never leaves its perch).

**In water (E1):** every class drops its air anchor and swims next to the player using the sim's
own locomotion.

### 3.1 Peeks and held-item reactions (R2.1)

Every so often, the companion makes sure the owner sees it in first person:

- **Swimmers** swim through the front of your view.
- **Perchers** hop off the perch, show themselves, and hop back.
- **Look down:** the companion hops onto your hand, or settles just above the hotbar, when you
  look down.

**Reactions to what you're holding** give these peeks a reason to happen:

| Held item | Reaction |
|---|---|
| Fishing rod | Excited, watches the rod tip, ready to go |
| Bait | Begs; this is the moment to feed it (§7) |
| A fish of the same species | Curious, nose to the item |
| A fish from `#predator_fish` | Hides behind your head |
| A name tag | Tilts its head |
| Food | Mild interest |

The list is extendable; each entry is a target point plus a pose.

**Acceptance (R2.2):** in first person, during normal play, the companion is visible to its owner
at regular intervals.

---

## 4. Personality (C1–C7, C9, R7.4)

Temperament changes **how** a display mode behaves, never **which** one is used.

| Temperament | Behaviour |
|---|---|
| docile | Stays tighter; nuzzles you while you stand still |
| skittish | Hides behind your head when a hostile mob is near, peeks out |
| aggressive | Puffs up at nearby hostiles; "bites" the air when the owner takes damage |
| sprinter | Dashes ahead while the owner sprints |
| ambusher | Hangs still, darts at nearby dropped items |
| wily | Takes the active role in pet-to-pet scenes |
| ghost | Timing only: long, still hovers (no transparency or phasing, since C8 was rejected) |
| erratic / trembler / metronome | Idle timing only: jittery / shaky / rhythmic |

**Triggers that could stay on during normal play** need a dwell time, then habituation, then a
cooldown (R7.2). That covers pet cats nearby, item streams for ambushers, and docile nuzzling.

---

## 5. Fishing reactions (D1–D5)

| Moment | Where | Reaction |
|---|---|---|
| Cast / waiting | World | Leaves its anchor and goes to the line: swimmers hover by the bobber, benthic fish ride the rod tip |
| Nibble | World | Leans toward the bobber and twitches |
| Minigame opens | World → docked | Swims from the bobber to its dock on the minigame screen (§5.1) |
| Minigame running | Docked | Reacts to the bar state (§5.2) |
| Catch with a celebration tier | Docked | Joins the celebration, greets the hero fish on a same-species catch, then flies past toward the camera and returns. Skipping the celebration ends the fly-past (R3.5) |
| Catch without a celebration | Docked → world | Quiet happy wiggle, then swims back |
| Fish escapes | Docked → world | Droops, sinks, slowly swims back (~3 s) |

The fish docks in first **and** third person. A setting "companion waits at the bobber during the
minigame" skips docking; it defaults to **off**, so the fish docks (R3.4).

### 5.1 How the docked fish is drawn

The minigame bar is drawn in the centre of the screen and covers the bobber in the world, so while
the minigame runs the companion moves into screen space. Its motion is still driven in world space
and only its target changes, so the move from bobber to dock is a real swim.

**Rendering approach (revised after the R1.2 review):**

| Path | 3D perspective | Draw order vs. bar art | Notes |
|---|---|---|---|
| World pass | yes | under | Can be blocked by terrain |
| First-person hand / screen-effect pass (where vanilla draws the totem animation) | yes | under | Depth is cleared, so terrain can't block it; fixed 70° FOV |
| HUD GUI items (how the minigame draws its items today) | **no**: flat atlas image | any | The current hero fish fakes its Y turn with a horizontal squeeze |
| **Custom picture-in-picture renderer with a perspective projection** | **yes** | **any**, including on top of bar art | Renders offscreen, then is drawn into the HUD in order. Vanilla's default is orthographic, but a subclass can set its own perspective projection before drawing. Registration exists on both loaders (NeoForge `RegisterPictureInPictureRenderersEvent`, Fabric `PictureInPictureRendererRegistry`) |

**Plan:**
- The approach to the dock uses the hand pass, so terrain can't block it once it's close.
- The docked fish uses a **perspective picture-in-picture renderer**. Its projection matches the
  hand pass (fixed 70° FOV, R1.4), so moving from one pass to the other can be made invisible.
- The fish stays a 3D model on top of the HUD through the whole celebration, so no swap to a flat
  item is needed (this replaces R1.3).
- **Phase 0 must prove this**: a pixel-locked dock, an invisible handoff, and the cost of resizing
  the offscreen texture as the fish moves.
- **Fallback if the picture-in-picture approach fails:** keep it in the hand pass, drawn under the
  HUD, and swap to the flat GUI item under the celebration flash.

**Placement:**
- Anchors are computed in hand-pass camera space (GUI px → NDC → `tan(35°)` × depth), so FOV,
  sprint and view bob don't move them.
- The minigame publishes named anchors each frame (bar top/base, bobber sprite, fish target,
  progress meter) **plus the outer edges and current heights of the gear column (left) and zone
  icon column (right)**.
- The fish docks beyond or below those columns, above the bar, or below its base (R3.1). It never
  covers the bobber zone or the fish target.
- The "sits on the top cap" and "pokes out from behind the edge" poses are dropped (R3.2).
  Picture-in-picture drawing would make them possible again, but they stay out unless asked for.

**Motion while docked:** the fish keeps moving, never fully idle, but it slows down at times rather
than swimming constantly. How much it moves while docked is tuned in game (R3.3 is open).

### 5.2 Bar-state reactions

| Class | While the fish is in the zone | As progress drains |
|---|---|---|
| `FREE_SWIM` | Moves in step with the bobber sprite, cheering | Darts nervously, then slows |
| `GLIDE` | Glides lazily | Flaps faster |
| `DRIFT` | Pulses in time with progress | Shrinks |
| `BENTHIC` | Bounces in place | Grips and tilts |
| `ANCHORED` | Leans out | Retracts |

---

## 6. Ambient world reactions (E1, E2, E6–E8)

- **Moods respect the species' home** (R7.1). Comfort comes from the species' `zones`,
  `biome_weights` and SNOW condition: Nether fish are happy in the Nether, cold-water fish are
  fine in snow. Anything else in an unfamiliar biome gets the miserable or shivering reaction.
  Rain makes most fish happy.
- **Mobs:** cats make it flee onto your head (with dwell and habituation, §4). Axolotls and
  dolphins get a friendly circle.
- **Time and moon:** species with a night or day time weight are sleepy during their off-hours.
  Species with a full-moon moon weight do a special idle under a full moon.
- **Deferred to "planned improvements"** (R7.3): tank interactions. That covers pressing against
  the glass of a tank with its own species (E3) and tank fish gathering toward the companion (E4).
- **Rejected:** triggering tank cosmetic reactions (E5).

---

## 7. Player-triggered interactions (F1–F4, R2.3)

- **Pet:** sneak and right-click while looking at it, **or** use the emote key, **or** use the
  Companions tab. Petting is a client raycast plus a packet; the server only checks distance.
- **Feed:** right-click with bait to play a snack animation and use one bait, or feed from the
  Companions tab.
- **Emote keybind:** plays the signature trick on a ~10 s cooldown, synced to other players.
- **Name:** the companion takes the fish item's custom name, which can also be set in the
  Companions tab. The name shows when the owner sneaks.
- **Particles:** all of them respect vanilla's particle setting (R7.5).

---

## 8. Unlocking and equipping (H1, H2, H3, H5)

### 8.1 One companion slot that holds a fish

- Bonding **moves the fish item out of your inventory and into a companion slot**.
- Unequipping **gives that exact item back**. If your inventory is full, it drops at your feet.
- Switching companions is a swap.

The slot is a **player attachment**: a per-player data slot that both loaders provide (R4.1). It
is saved in the player's own file, **kept on death** (it never drops, R4.2), and synced to other
players automatically. It must not go in the global `FishCatchSavedData`. A backup restore rewinds
that independently of inventories, which lets a player duplicate a fish: bond → backup → unequip →
restore.

While the fish is equipped, the server never scans your inventory. It is the server's own data
until you unequip it.

### 8.2 Bonding flow

1. The **Companions tab** in the encyclopedia lists the discovered species, each with its bond
   status (§8.4).
2. For a species whose bond is unlocked, **Bond** opens a picker. It lists only the fish of that
   species you're carrying right now: loose stacks and fish inside any Pile of Fish in your
   inventory. Fish that are identical in **every** component collapse into one row with ×N, so
   two fish that differ only in, say, their custom name show as separate rows.
3. If you carry none, the picker says so: "Carry a Bluegill to bond with it."
4. **Confirm:** the client sends the server a description of the chosen fish, not a slot number.
   The server finds a matching fish in your inventory or piles, removes it, and puts it in the
   slot. This happens **once, at the moment of bonding**. If nothing matches (for example, you
   dropped the fish while the screen was open), bonding fails cleanly. (R4.3)
5. Any previously equipped companion comes back to your inventory in the same step.

### 8.3 Signature companions (H3)

The `#fishtastic:unlisted_fish` species are **quest-only companions**. They skip the encyclopedia
gate, each gets one extra signature interaction, and each bond quest appears in the quest log once
the species has been caught.

### 8.4 Bond status and discovery

| State | Text |
|---|---|
| Normal species, locked | **Bond: 4/15 catches** (R7.6) |
| Signature species, locked | **Bond unlocks via quest:** *Quest Name*, with a link to the quest |
| Unlocked | **Bond available**, plus the Bond button |
| Equipped | **Your companion**, plus the Unequip button |

- **Gate (H2, R6.5):** the server checks that the species' catch count meets every derived
  threshold. Claiming the encyclopedia rewards is not required.
- **Unlocked bonds are stored** per player and never re-lock (R6.4).
- **Discovery (R7.7):** a first-unlock tutorial (FTUE) introduces companions and the Companions
  tab. After that, a banner notification announces each new bond unlock.

---

## 9. Encyclopedia thresholds derived from rarity

### 9.1 Today

All 64 `fish_encyclopedia_entry` files have a hand-written `thresholds` block using three presets:

| Preset | name / stats / types / spawn / lore |
|---|---|
| rare | 1 / 1 / 2 / 1 / 6 |
| uncommon | 1 / 2 / 5 / 2 / 15 |
| common | 2 / 3 / 10 / 4 / 30 |

They are applied inconsistently. The worst case is `blazed_grub`: it has weight 4 out of the
Nether's 110, so it is rare there, yet it uses the common preset. That takes about **825 Nether
catches** to complete, the worst grind in the mod (R6.1).

### 9.2 Design

- **Delete every `thresholds` block.** There is no per-fish override; derivation is the only path.
  A fish that comes out wrong is fixed by changing its weight.
- **Rarity is measured against the pool a location actually draws from**: deep ocean = ocean + deep
  ocean, mountain river = river + high altitude, and so on (R6.2). Use the species' best share
  across the location sets it can appear in. `#unlisted_fish` is left out of the totals.
- **Thresholds count catches of that one species only** (decided 2026-10-10). Nothing is totalled
  across species, and that stays so. Pool share is used only to *pick* a species' tier when the
  data loads; it is never part of a player's progress.
- **Hard cap: full completion never needs more than 50 catches of the species** (the lore
  threshold, today 6–30). Tiers are tuned under that cap so rarer fish need fewer catches. Total
  fishing effort for the rarest fish is checked on the review list, and if one is still too slow,
  the fix is lowering its tier's counts or nudging its weight.
- **Grandfathering (R6.4):** a section whose reward the player has claimed stays open whatever the
  thresholds say. Claimed rewards were already stored per player, so no new save data was needed.
  Unlocked bonds (a later phase) are stored and never re-lock.
- Before shipping: a review list of every species with its derived tier, its expected effort, and
  any change from today: [encyclopedia-tier-review.md](encyclopedia-tier-review.md).

### 9.3 As built (phase 1, 2026-10-10)

`grill24.fishtastic.data.EncyclopediaTiers` derives the thresholds, and every consumer reads them
from it. It is a pure function of the fish profile registry, cached per `RegistryAccess`; the
unlisted set is rechecked on each lookup so `/reload` of the tag takes effect.

**Location pools**, the zone sets `Zone.resolve` hands back for real casts: nether · ocean ·
ocean+deep_ocean · river · cave · river+cave · river+high_altitude. A species is in a pool when its
zones overlap it. Its share = weight ÷ the pool's listed total (plus its own weight if it's
unlisted), and the best share across its pools picks the tier.

| Tier | Best pool share | name / stats / types / spawn / lore |
|---|---|---|
| very rare | < 1.5% | 1 / 1 / 1 / 1 / 3 |
| rare | 1.5% – 4% | 1 / 1 / 2 / 2 / 6 |
| uncommon | 4% – 12% | 1 / 2 / 4 / 3 / 15 |
| common | 12% – 30% | 2 / 3 / 8 / 4 / 30 |
| abundant | ≥ 30% | 2 / 4 / 12 / 6 / 50 |

The tier boundaries step lore by roughly the same factor as share, so the expected casts to
complete a listed species fall between ~106 (sulphur molly) and ~363 (sawfish, paddlefish,
piranha, clown loach), against ~18× spread before. `EncyclopediaTiersTest` fails if a listed
species passes 500 expected casts or any lore threshold passes 50.

---

## 10. Pet-to-pet interactions (G1–G6)

**The server schedules the scenes** and sends one tiny packet per scene to the players tracking
them (R5.1). Clients don't schedule anything; they just play the scene they're told to.

Rules:
- A companion is in at most **one scene at a time**, with a per-companion cooldown (R5.2).
- **Greeting on approach (R5.3):** when two companions come into range of each other and neither
  is on cooldown, a greeting plays. The cooldown alone stops repeats, so walking up to someone you
  haven't seen in a while almost always produces a greeting.
- **No history per pair (R5.4 rejected).** Tiring of repeated scenes is handled by tuning the
  cooldowns and scene chances. The only state is per companion (cooldown) and the current set of
  companions in range, held in memory and never saved.
- **No scene starts while either owner is in the minigame** (R5.5). The server knows who is
  fishing.
- **Fixed priority order** when several pairings apply (R5.6): same species → predator/prey →
  wily → class pairing.

| Pairing | Scene | v1? |
|---|---|---|
| Same species (G1) | Synchronized loop plus a bubble heart | **v1** |
| Two free swimmers (G2) | Figure-eight around both players, one at the centre of each loop | **v1** |
| Swimmer + benthic (G3) | Swimmer circles the other player's head; the benthic fish answers with a hop and a side-to-side rock | later |
| Predator + prey (G4) | Prey hides behind its owner, predator does a smug lap | later |
| Wily + any (G5) | Steals a bubble from the other pet and carries it home | later |
| Both legendary (G6) | A fancier version of the chosen scene | later |

---

## 11. Technical architecture

- **Storage:** a player attachment holding one `ItemStack`, kept on death, synced to tracking
  clients (§8.1). Unlocked bonds are stored as a set per player, next to the encyclopedia rewards
  the player has claimed.
- **Event packets:** catch outcome, fish escaped, pet/feed/emote, and server-scheduled scenes.
  Everything else runs on the client.
- **Rendering (R1.1):**
  - Free-flying companions are submitted from a world-render hook (NeoForge
    `SubmitCustomGeometryEvent`, Fabric `LevelRenderEvents.COLLECT_SUBMITS`), each with its own
    culling box.
  - Perched poses use a player render layer, the same way vanilla draws parrots on shoulders.
  - Docked fish follow §5.1.
  - Leaderboard mannequins are filtered out, and invisible players don't show companions.
  - Every companion fish needs its own `ItemStackRenderState`.
- **Motion:** the open-world controller described in §1 (R1.5). The target comes from the
  display mode, a peek, a held item, the dock, or a scene. Temperament supplies modifiers.
- **Settings:**
  - hide other players' companions (default off)
  - hide own companion in first person (default off; ignored while docked)
  - companion waits at the bobber during the minigame (default off)
- **Performance:** low risk next to tanks that already draw up to 512 fish. Particles are the real
  cost, so they respect the particle setting.

---

## 12. Phased plan

**v1 = phases 0–5 plus scenes G1 and G2 (R8.1).**

| Phase | Scope | Done when |
|---|---|---|
| **0. Spike** | Free-flying fish from the world hook; own-companion visibility in first person; the perspective picture-in-picture dock with column-edge anchors and the handoff from the hand pass (fallback: hand pass plus flash cut); open-world controller feeding `FishAnimator` | Visible in first person; dock pixel-locked through view bob, FOV change and resize; handoff can't be seen (or the fallback is chosen) |
| **1. Encyclopedia unification** | §9 | Every fish's tier listed and approved; no species needs more than 50 catches of itself |
| **2. Data and sync** | Player attachment, bond/unequip/swap, sync, debug command | A second client sees your companion; unequip returns the exact item; survives death |
| **3. Display modes** | §3 including peeks and held-item reactions, trios, water | Companion parade looks right; visible to the owner at regular intervals |
| **4. Bonding UI** | Companions tab, picker, status lines, first-unlock FTUE, unlock banners | Bond → equip → unequip with no commands |
| **5. Fishing reactions** | §5 | The whole fishing loop reads well in first and third person |
| **6. Pet-to-pet (v1: G1, G2)** | §10 server scheduler | Two clients see the same scene; one scene per companion; greeting on approach |
| *Later* | Personality/world/player (§4, §6, §7), scenes G3–G6, signature companions (§8.3) | |
| *Planned improvements* | Tank interactions (E3, E4) | |
