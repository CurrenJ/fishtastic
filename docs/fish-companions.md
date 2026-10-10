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

---

## 13. Phase 0 spike results (2026-10-10)

Dev-only spike, uncommitted: `client/companion/` (`CompanionSpike`, `CompanionPipRenderer`,
`CompanionPipRenderState`), `client/renderer/CompanionFlock`, `/fishtastic companion on|off|minigame|stopgame`,
self-test scene `companion` (opt-in). Verified headlessly on **Fabric and NeoForge**.

| Question | Result |
|---|---|
| Free-flying fish from a world-render hook, visible to its owner in first person | **Yes.** Fabric `LevelRenderEvents.COLLECT_SUBMITS` / NeoForge `SubmitCustomGeometryEvent`. Drawn in first and third person; whether it is *in view* is the visibility note below. |
| Docked fish as real 3D perspective **on top of** the HUD | **Yes.** A picture-in-picture renderer whose projection is the camera's own perspective cropped to the rect (off-axis frustum). Must be registered on both loaders. |
| Invisible world → HUD handoff | **Yes.** Same pose, position and shading on both sides of the switch; a side-by-side frame drawing both passes at once matches. |
| Stays locked to the dock through camera turns and in third person | **Yes**, after one fix below. |
| Fish sim reused in the open world | **Yes, unchanged.** Following: the group path's planar engine in an invisible 3×2×3 voxel box that follows the player. Docked: the single-tank binary engine (fish swim only left/right, so always side-on). |

**Pitfalls found, and their fixes (all in the spike code):**
- **GUI ignores the model-view stack.** Feature submits in a PiP don't apply `RenderSystem.getModelViewStack()`. Fold the view rotation into the PiP projection and keep the pose world-oriented, so normals match level lighting.
- **Domain swaps strand the fish.** On a carry-over rebuild into a smaller domain, the engine's wall constraint pins a fish that starts outside it. Clamp the fish in, and fade the moved distance out at render time.
- **Planar engine looks wrong when docked.** In a thin box it still turns toward the camera, so the docked fish reads nose-on. The single-tank model fixes it. Its swim gate needs a nominal short sim length; the drawn size is set separately.
- **True-to-scale small fish are specks.** Decided (owner, 2026-10-10): companions keep a **minimum drawn length of 0.25 blocks** while following; larger fish stay true to scale. Docked fish are drawn at a fixed screen size.

**NeoForge:** verified after the create-world deadlock fix (9b203ace). The same scene passes on both
loaders, with identical results.

**First-person visibility while following is not solved by placement alone.** A free-wandering fish
in the follow box was out of a level first-person view in most shots on both loaders. It's visible
when looking up, or when it happens to cross in front. This confirms that the peeks (§3.1, R2.1) are
a requirement for phase 3, not polish.

**Not covered:** bob-matched projection at the handoff (no visible pop in tests; untested with view bob while walking); non-swimmer classes (benthic/glide/drift/anchored), which use the tank idle pose for now.

---

## 14. Phase 2 — as built (2026-10-10)

- **Storage:** `companion/CompanionStorage` (`@ExpectPlatform` get/set), backed on NeoForge by an
  `AttachmentType<ItemStack>` (default empty, serialized only when set, `copyOnDeath`, synced to the
  owner and everyone tracking them). On Fabric it's an `AttachmentRegistry` type (persistent,
  `copyOnDeath`, `syncWith(... all())`, and an empty stack is stored as "not attached"). It is never
  `FishCatchSavedData`.
- **Server operations:** `companion/Companions`:
  - `bond` finds a fish identical in every component (count aside), in loose stacks first and then
    inside Piles of Fish, and takes exactly one. An emptied pile is removed, and a pile left holding
    one fish becomes that fish. A previous companion comes back through `RemoveTankEntryPacket.giveOrDrop`.
  - `unequip` and `equip` are the other two operations.
  - Results are `BONDED / NOT_A_FISH / LOCKED / NOT_CARRIED`.
  - `CompanionBondGate.isUnlocked` is a Phase 4 stub that allows everything.
- **Network:** `BondCompanionPacket(ItemStack)`, decoded with the untrusted stream codec, and
  `UnequipCompanionPacket`. Other clients learn companions only through the attachment sync.
- **Client:** `ClientCompanions` rebuilds one `ClientCompanion` for each player in the client level
  whose synced companion is non-empty: added, rebuilt on change, dropped on leave or unequip. Only
  the local player's companion docks. Someone else's box turns with its owner's view yaw.
  `CompanionSpike` is gone.
- **Dev:** `/fishtastic companion on|off|give <player> <species> [size]|minigame|stopgame`
  (dev-only). `on` and `off` go through the real bond and unequip.
- **Verification:**
  - **Game tests:** 9 new game tests on both loaders (bond from inventory or pile, pile collapse,
    non-matching description, non-fish, identical return, full-inventory drop, swap, kept on death).
    The death test takes a per-loader respawn hook, because NeoForge copies inside `restoreFrom`
    while Fabric copies on `AFTER_RESPAWN`. Fabric passed 292/292. NeoForge's testmod compiles,
    but its game tests weren't run (that JVM doesn't exit).
  - **Self-test:** the `companion` scene bonds and unequips through the real packets, and passed
    every CHECK on both loaders.
- **Second player:** headless runs can't have one, so the scene adds a dev phantom companion
  (`ClientCompanions.addPhantom`) to prove the manager tracks and draws two. Real remote-player sync
  is covered only by the attachment's own sync contract, not by a live two-client test.

---

## 15. Phase 3 — as built (2026-10-10)

Display modes, peeks, held-item reactions, trios, water and the three client settings (§2, §3,
§3.1, §11). Self-test scenes `companionparade` (every class, tricks, the trio, reactions, the hand
hop, water), `companionpeek` (the visibility acceptance and the settings) and `companionhand` (a
one-minute hand-hop iteration scene). Existing scenes (`companion`) unchanged and still green.

**Display modes.** The class is `TankFlockAdapter.locomotionOf(animation)`, as planned. Each class
gets its own anchor path, driven per tick in `ClientCompanion.anchorTarget`:

| Class | Path |
|---|---|
| `FREE_SWIM` | Orbits the eye at 1.2 blocks, one lap ≈ 26 s (seeded start angle so companions don't sync). Trick: a quick lap (3× orbit speed). |
| `GLIDE` | Wide arcs at 2.2 blocks, 1.55 blocks overhead, ≈ 44 s a lap. Trick: a low swoop toward the floor that spawns cloud-puff dust (respects the particle setting). |
| `DRIFT` | Behind one shoulder (0.5 back, 0.5 to the side, 0.3 down) on a **velocity spring** rather than an ease, so it lags and settles. Trick: rises and settles. Glows at night (full-bright submit when `getSkyDarken() >= 4`). |
| `BENTHIC` | Perches on the body via a player render layer — head or a shoulder, chosen per species (`seed % 3`). Trick: a full spin on the perch. |
| `ANCHORED` | Pokes out of the upper back through the same layer; ducks in (the planted pose's retract envelope) while a hostile is within 3 blocks. |

`animation.mode` still refines each class through `FishAnimator` (upright poses stay upright, belly
-down stays flat, planted stays put) — the companion feeds the same animator entry points the tank
group renderer uses, with the engine's own heading/bank/bell/retract when it simulates.

**The follow box is sized for the fish** (`2.5 · length + 1` cells, 3–8, +2 for gliders, +1 in
water) so the engine's size gate passes anything whose swimming is meant to show. A fish too big
even for that (the giant mantas at true scale) is demoted by the engine to `STATIC` and rides the
moving box instead — for an animal that size it is indistinguishable.

**Trios.** A `FREE_SWIM` bonded fish under 20 cm is rebuilt as three same-species specs and the
engine's own swarm offsets spread them (§2). The trio swims as one flock on the leader's path; in
the dock it is drawn as the leader alone.

**Water.** When the owner's eyes are in water every class drops its air anchor: the invisible box
moves beside the owner and the planar engine swims. `BENTHIC` and `ANCHORED` — the two classes with
no swim of their own (a starfish can only crawl) — are swapped to `FREE_SWIM` locomotion for the
swim, which is the one place the sim is not run "as itself".

**Perches (player render layer).** `CompanionPerchLayer` is added to every `AvatarRenderer` by a
common mixin (`AvatarRendererMixin` + a `LivingEntityRendererInvoker` for the protected
`addLayer`; Fabric has no layer-registration API in this version). It looks the rendered entity up
by `AvatarRenderState.id`, so mannequin renderers get the layer but never a fish. The layer frame —
after `scale(-1,-1,1)` and `translate(0,-1.501,0)` — puts y=0 at ~1.41 blocks, i.e. the shoulder
line (vanilla's parrot layer passes -1.5 there only because its own model pose hoists it back
down); x is mirrored, z is not, and the model faces -z. Flat poses (a starfish's floor sit) are
stood up on a shoulder so the sprite reads from the front; on the crown of the head it stays flat.

**Peeks (§3.1, R2.1).** A phase machine per companion: `OUT (0.4 s) → HOLD (3.5 s) → BACK (0.5 s)`,
then a seeded cooldown of 15–45 s (the first waits 7.5–37.5 s). A local companion also peeks when
its owner has not had it on screen for 10 s, with a 10 s minimum gap — the schedule *is* the
visibility acceptance, so the self-test's frame count measures the mechanism directly. Peeks pause
while the owner is in the minigame (the bar owns the middle of the screen). Swimmers
swim to the point (their box is aimed so the **fish**, not the empty box centre, lands in view —
full compensation vertically, 70% horizontally so some swim survives); perchers hop off and back
along an arc from the perch.

**Held-item reactions (§3.1).** A table of `(item test, view-space point, pose tilt)`: rod (vanilla
or mod, via `fishtastic:fishing_rods`), bait, a fish of the companion's own species, a
`#fishtastic:predator_fish` (new tag), name tag (tilt only), food (fallback). Reactions take over
the anchor, suppress peeks while active, and pull perchers off the body like a peek. Main hand only.

**Look-down hand hop (deviation from the phase-0 plan).** The companion hops to a point just above
the hotbar when the owner looks down (pitch > 52°), but it is drawn by the **world pass**, not the
first-person hand pass. Reason, established empirically: the hand pass renders companion item
geometry as a shadow-only silhouette under the dev client's Iris shaderpack
(ComplementaryReimagined — the fish itself is not drawn, only its cast shadow appears, blurred);
without shaders the same draw is pixel-correct. The world pass draws correctly under both, and at
0.9 blocks the point cannot be occluded by terrain the owner is standing on. Screen-anchored (an
unprojected GUI point at 0.9 blocks, the dock's own technique) rather than a view-space offset,
because looking down rotates "forward" almost straight down.

**Settings (§11).** `FishtasticClientConfig` grows `hideOtherCompanions`,
`hideOwnCompanionFirstPerson` (ignored while docked) and `companionWaitsAtBobber` (skips docking;
until Phase 5 it simply keeps following), all default off, cached like the other settings. A
dev-only override map lets the self-tests flip them without rewriting the owner's properties file.

**Verification.** Fabric, four scenes (`companion`, `companionparade`, `companionpeek`,
`companionhand`), all checks green (the three edits made after that run — a minigame peek-pause in
common code, a water screenshot added to the parade scene, and docs — were re-run on NeoForge
only); NeoForge, the same four scenes, all checks green in one run on the final code. The acceptance window measured the companion on screen for **37.7–52.2% of
first-person ticks** on Fabric and **42.0%** on NeoForge, each over 1500 ticks looking straight
ahead with 3–4 peeks — the spread is the seeded schedule and camera timing, and all are far above
the 5% floor the check enforces. Screenshots reviewed by eye on both loaders for every class, the
perch (benthic on a shoulder, anchored at the back), the hop-off peek, the trio, the water swim,
the rod reaction and the look-down hop.

**Pitfalls found and fixed during the phase** (all cost a run each):
- A freshly bonded companion peeked into the owner's face immediately — `lastSeenTick` starts at
  zero, so the unseen-for-10-s rule fired at once. A new companion starts "just seen", and the
  first peek waits 7.5–37.5 s like every other.
- Peeks aimed the *box* at the view, so a fish wandering its 3-block box landed off-screen: the
  anchor now subtracts the fish's own offset (full vertical — a crawler rides its box floor).
- The visibility check projected the fish's local offset without the box's yaw, so
  "on screen" disagreed with the debug readout by a full turn.
- `viewPoint`'s right vector was inverted (`look × up` already points right), which put the rod
  reaction on the wrong side of the player.
- The look-down hop's view-space offset collapsed when looking straight down (forward IS down):
  the point is now a fixed GUI position unprojected at 0.9 blocks, the dock's own technique.
- Flat poses (a starfish) on the head slot were a sliver from every angle but above; they are
  re-slotted to a shoulder, stood up so the sprite reads from the front.

---

## 16. Phase 4 — as built (2026-10-10)

The bonding UI (§8.2–§8.4): a **Companions tab** in the encyclopedia, a carried-fish picker, the
real server-side gate, unlock persistence, rename, the first-unlock FTUE and the unlock banner.

**Gate and persistence.** `CompanionBondUnlocks.refresh(player)` derives and stores newly earned
bonds: a normal species unlocks when its entry meets **every** derived threshold (R6.5 — claiming
rewards is not required), and an unlisted species unlocks only via a quest whose data carries the
new `unlocks_companion` field, once that quest is completed or claimed. Unlocks are stored per
player in `PlayerQuestState` (`unlocked_companion_species`, `optionalFieldOf`, so old saves load)
and **never re-lock** (R6.4). `CompanionBondGate` runs `refresh` then reads the stored set, so the
gate stays server-authoritative and a save predating the phase is grandfathered on first check.

**Where unlocks are detected.** `QuestSyncPacket.sendToPlayer` calls `refresh` on every sync — one
choke point already covered by join, catches, quest claims and the shop flows — and the packet
carries the full unlocked set plus a `newlyUnlockedCompanions` delta that drives the banner. The
catch path forces its lightweight sync when a bond unlocked but no quest moved, so the banner can
never wait for the next unrelated sync. The banner rides the existing notification pipeline under a
per-species synthetic id (`companion_bond/<species>`) at the new-species tier.

**FTUE (R7.7).** The first unlock ever starts `CompanionTutorialStep.INTRO` (stored beside the
other tutorial state, with backup-restore coverage); the encyclopedia shows a side panel
introducing the tab, and opening the tab advances the step to COMPLETE. Later unlocks get the
banner only.

**The tab (§8.2, §8.4).** A Fish/Companions switch on the encyclopedia lists every **discovered**
species with its status line: "Bond: N/M catches" (M is the lore threshold — always the furthest,
R7.6), "Bond unlocks via quest: <name>" with an Open-quest button (selects the quest's category tab
in the log and pulses its row), "Bond available" + Bond, or "Your companion" + Unequip + the name
field. The picker lists only fish carried right now — loose stacks plus Pile of Fish contents —
collapsed per full-component identity with ×N, mirroring the server's own find exactly so the
picker can never offer a row the server then refuses; Confirm sends the description (never a slot),
and a small feedback packet answers with the outcome so the tab can explain a LOCKED or
NOT_CARRIED result. The panel and picker anchor their width so `scaleToWidth` never upscales a
short list to billboard size, and the tab watches the synced unlock set, so a bond that unlocks
while the screen is open appears without reopening it.

**Test seams.** `CarriedFish` (client) mirrors `Companions.takeOneMatching`; dev-only
`/fishtastic companion catchcount|unlock|resetbonds` keeps the self-test deterministic without real
catches; the screen's dev accessors drive the same handlers its buttons call.

**Verification.** Fabric gametests, unit tests and `./gradlew build` green, including 11 new game
tests: the gate refuses an incomplete species, unlocks (and stores) a complete one, survives the
counts being rolled back (never re-locks), keeps unlisted species quest-only, queues exactly one
banner per unlock, refuses a faked description, sets/clears/clamps a rename, groups picker fish by
full identity, and the quest-hook scan (done/claimed/undone/already-unlocked). The self-test scene
`companionbond` runs green on **both loaders** (16 CHECKs): the screen opens through the real
packet, the locked row reads "Bond: 29/30 catches" (30 = bluegill's derived lore threshold), the
unlock flips it to "Bond available" and starts the FTUE (advanced by re-opening the tab), the
picker shows the four identical bluegills as one ×4 row beside a ×1 row, Confirm bonds through the
screen, rename lands ("Bubbles"), an unlisted species reads "Bond unlocks via a hidden quest" until
the dev unlock flips it, and unequip empties the slot. Screenshots reviewed on both loaders for
every state. **Not exercised end-to-end:** the quest link itself drives only when a quest authors
`unlocks_companion` — none does yet (the six signature quests stay in the later signature phase),
so its rule is covered by the game tests, and the "Open quest" button/row pulse is unexercised.

**Pitfalls found and fixed during the phase** (all cost a run each):
- `EntityArgument.player` rejects a multi-entity selector: every `@a` in the scene's dev commands
  failed with "Only one player is allowed", silently leaving the world locked. Use `@p`.
- `VBox#scaleToWidth` scales children **up** as well as down, so a one-row list rendered as a
  billboard; a page-width 1-px spacer anchors the natural width so the scale caps at 1.
- The unlock set rides the quest sync, which the encyclopedia screen does not handle, so the tab
  kept showing a stale "Bond: 30/30" after an unlock that had already landed; the screen now
  watches `QuestClientCache`'s set and rebuilds once on change.
- `Quest`'s codec initializes registry-backed codecs, so constructing one in a bare unit test
  poisons `BuiltInRegistries` for the whole test JVM (order-dependent failures); quest-hook tests
  live in the testmod instead.

---

## 17. Phase 5 — as built (2026-10-10)

The fishing loop (§5, §5.1, §5.2): the companion goes to the line on a cast, twitches on the
nibble, swims from the bobber to its dock when the minigame opens, reacts to the bar per class,
joins a tiered catch's celebration (greeting the hero on a same-species catch, flying past toward
the camera until the skip ends it), droops and sinks home when a fish escapes, and holds the line
through the minigame with "waits at the bobber" on. Self-test scene `companionfishing` (opt-in).

**The line.** Every client derives it from vanilla state: a player with `player.fishing` set is
fishing, so their companion leaves its display-mode anchor and hovers at the bobber — swimmers
class-flavoured beside it, benthic/anchored fish up to the rod tip (§5: "benthic fish ride the rod
tip"). The nibble is vanilla's synced `DATA_BITING`, exposed through a new
`IFishingHookExtension#fishtastic$isBiting` — so every client sees the same lean-and-twitch, not
just the owner. The line anchor freezes while docking, so the swim to the dock starts from the
bobber even after the hook is gone (the hook survives a session; it dies on the next reel).

**The dock (§5.1, R3.1).** `FishingMinigameAnimation` now publishes `CompanionAnchors` each
access: the bar's content rect, the gear column's (left) and zone-icon column's (right) outer
edges and current heights, the bobber sprite's screen y, and the best active fish target's screen
y and catch progress. The dock lane moved to sit **beyond the gear column's outer edge and below
its lowest icon** (it used to be mid-left of the bar): never over the bar, its bobber zone or a
fish target by construction, and the scene asserts the lane's right edge stays left of the bar and
its top below the gear column.

**Bar-state reactions (§5.2).** The published bobber position, target progress and its trend feed
per-class reactions, all through existing channels (whole-fish offsets, the dock's box scale, the
render tilt, and a dock-only animation clock): FREE_SWIM bobs in step with the bobber sprite,
cheers (a bounce) in the zone and darts nervously while progress drains; GLIDE idles on a lazy
clock and speeds up as it drains (the dock-only clock, so a rate change never jumps the phase);
DRIFT pulses with progress and shrinks as it drains; BENTHIC bounces in the zone and grips with a
tilt while draining; ANCHORED leans out in the zone and sinks back while draining. A negative
catch-progress delta is peak-held with a decay (~1 s), so a drain burst reads as draining for the
duration of a nervous dart instead of one tick. Amplitudes are deliberately small (R3.3) and easy
to tune.

**Catch and escape (§5, D3–D5).** A tiered catch's celebration is joined from the dock: a
same-species hero earns a ~1.2 s greeting pause nosed toward it, then the fly-past — the dock
point lerps toward a nearer camera depth, toward the screen's middle and ×2.2 zoom. The flight
ends when the celebration ends *or is skipped* (R3.5) — the skip accelerates the celebration to
its settle, which drops the reference, which decays the flight. A catch without a celebration
gets the quiet wiggle; an escape droops (tilt, sink, slow ease home for ~3 s). The outcome is
`CompanionFishingOutcome.of(hadFishTarget, caughtFish)` — pure, unit-tested, shared by both
derivations: the owner's client reads it off its own animation the moment the bar starts hiding;
everyone else is told by the server.

**Others' view (§11).** `FishingMinigameManager.handleMinigameComplete` resolves CATCH/ESCAPED
from the session's own targets and sends `CompanionFishingEventPacket` to players within 128
blocks (the owner derives its own; the packet never goes to them). The receiving companion plays
the same wiggle or droop at the line.

**"Waits at the bobber."** The setting now does what it says: with it on, the companion holds the
line anchor through the minigame instead of docking (previously it just kept following).

**Calming the baseline wiggle (owner feedback, 2026-10-10).** Watching a run live, the owner
called the companion "frantic when it shouldn't" be. The fastest oscillations were the sim's
speed-integrated tail phase — which also drives the swim bob, shared clock — presented at its
tank-authored rate (~3.1 Hz tail beat beside the camera), and four reaction oscillators of mine
(up to ~10 Hz for the nervous jitter). The fix is companion-only presentation clocks, no tank-sim
change: `CompanionFlock.SWIM_CLOCK_RATE = 0.55` scales the swim phase (tail beat and bob together,
continuously — a rate change can never jump the phase) and `IDLE_CLOCK_RATE = 0.6` scales the
idle-pose clocks on the follow, perch, hand and docked paths; the reaction rates are named
constants now (nervous 2.86 Hz, cheer 0.80 Hz, drift pulse 1.11 Hz, benthic bounce 0.95 Hz —
down from ~10/2.9/2.2/4.4). Measured in the scene: the raw tail beat reads 3.14 Hz at the line
and 3.71 Hz docked, presented at 1.72 Hz and 2.04 Hz. Reaction moments (peeks, tricks, fishing
excitement) keep their livelier timing on purpose; both clock rates and all four reaction rates
are single constants, deliberately left for the owner to tune in game.

**Verification.** Fabric and NeoForge: the scene's 17 checks all green (18 including the standard
`shaders.loaded` check) — the cast is a real rod-cast through the item use path; the nibble is
forced through the new dev-only `fishtastic$forceBite`; the catch and the escape are driven
through the real resolution path via `devCatchTarget`/`startFailAnimation`. NeoForge ran once
(run 1 PASS under the hang-guard script). Screenshots reviewed: the docked
fish legible and side-on in its lane; the discovery celebration with the fish beside the
silhouetted hero at the greeting blend; the fly-past big and near-camera with the celebration
banner; the waits-at-bobber lane empty while the bar runs. The at-the-line and escape-droop legs
are state-verified; at cast distance a true-to-scale companion beside the bobber is a few pixels
even in third person, so those shots only corroborate rather than show.

**Pitfalls found and fixed during the phase** (all cost a run each):
- The bare `FishingMinigameAnimation()` constructor stages three example TREASURE targets
  (stone/diamond/gold); a demo that appends its own target ends up driving index 0, the stone —
  clear the list first, exactly as the start-packet handler does.
- `ClientCompanions`' session-end detection initially committed the new (null) animation over the
  remembered one in the same tick it disappeared, so `isHidingNow()` was never observed and the
  outcome never fired; the ended animation is now held until the hide is seen or it vanishes.
- `updateDockReactions` reset `fishProgress` to NaN at the top and then computed the delta against
  that same just-reset field, pinning `progressDelta` at 0 forever — the drain reaction and its
  scene check could never fire.
- The dock-swim metric: `lineDistance` reads the simulated centre, which deliberately holds at
  the bobber while the render-time blend swims to the dock; measuring "has it left the line"
  needs the rendered frame position (`devFrameDistanceToHook`).

---

## 18. Phase 6 — as built (2026-10-10)

Pet-to-pet scenes, v1: G1 and G2 (§10). This completes **v1 = phases 0–5 plus G1/G2** (R8.1).

**The scheduler (server, `CompanionSceneManager`).** Ticked from `ServerTickHandler`. Each tick,
per level: the players with a companion are collected and the best eligible pair starts at most one
scene. The rules, all of them: within 6 blocks; neither companion busy (in a scene, or in the
per-companion cooldown that follows one); neither owner mid-minigame (the fishing minigame
manager) or with a line out (`player.fishing` — their companion belongs at the bobber, §5); and a
fixed priority order (R5.6) read straight off the scene enum — same species (G1) beats two free
swimmers (G2), with later families slotting into `choose()` below them. Starting a scene sends
**one** `CompanionScenePacket` — scene, both owner ids, absolute start time — to players within 128
blocks of either participant, and holds both companions busy for the scene's duration + a 60 s
cooldown. That's the entire memory of the system: two numbers per player, in memory only, nothing
saved, no per-pair history (R5.2–R5.4).

**Playback (client).** `ClientCompanions.onScenePacket` hands each tracked participant its role;
`ClientCompanion` derives its position every tick from the packet's start time and the two owners'
current positions — no per-frame randomness, so both owners' clients compute the identical dance:

| Scene | Path |
|---|---|
| `G1_SAME_SPECIES` | Both companions fly a vertical ring at 1.1 blocks around the pair's midpoint, in the plane perpendicular to the owners' axis (a horizontal ring's near edge would sweep their faces), the second always antipodal to the first; at 2 s in, the bubble heart pops — bubbles traced along the classic heart curve in each viewer's camera plane, with one heart through its middle. |
| `G2_FREE_SWIM_EIGHT` | A figure eight: two circles tangent at the midpoint, one lobe centred on each owner; each fish runs the whole path half a period behind the other, swapping lobes at every crossing. |

A scene overrides the display-mode anchor for its seconds (peeks and tricks pause; perchers hop off
their perch and join, through the same out-of-body path a peek uses), then it simply expires and
normal behaviour resumes. In water, the scene still overrides — it is brief and the alternative
(ignoring it) would desync the pair.

**Verification.** Ten new game tests run the real scheduler with mock players and an explicit
clock — same-species pair → G1 to both owners; cooldown-gated repeats; minigame and line-out owners
block scenes; two different free swimmers → G2; a percher pair plays nothing; range and
one-scene-per-companion; same species beating free swimmers among three candidates; and packet
targeting to nearby observers. With the scene packet's round-trip test the suite is 315/315 on
Fabric. The visual self-test scene is `companionscenes` (opt-in): one real player, one dev phantom
partner, the scene injected through the same landing method the packet handler calls. It checks
both companions enter and leave each scene, and that the G1 ring actually travels — the sampled
arc swept over 60 ticks is ~10.5 rad for each fish (threshold 4) and the pair is antipodal in
12/12 samples. Screenshots are taken from behind the player (first person framed neither fish at most instants): the heart
moment reads clearly, the loop catches the pair at various ring positions — one fish occluded by
the player model in some frames — and the G2 figure eight puts one fish on each side of the owner.
The at-heart bubble trace is subtle at this scale; `HEART_SCALE` is the one knob.

**Pitfalls found and fixed during the phase** (each cost a run):
- The first G1 ring circled the pair's midpoint **horizontally** — with the midpoint only ~1 block
  ahead of each owner, the ring's near edge swept their faces (one frame had a fish filling half
  the screen). The ring is now vertical, in the plane perpendicular to the owners' axis, so every
  point of it stays at least the separation distance away.
- The scene had no companion to move: the bond gate is real since phase 4 and the scene never
  seeded the species' catch count, so `bondFish` came back LOCKED. Seed `catchcount` first. The
  same latent bug had been sitting in the phase-2 `companion` scene since phase 4 introduced the
  gate — it only ever passed when another seeding scene ran first in the same world, and fails on
  a fresh world; it now seeds too.
- Each participant built the ring basis from `partner − mine`, which flips sign between them: the
  second fish's π offset became a mirror, not an antipode (the pair measured "opposite" only
  half the time). The axis is canonicalised to the +x half-space before the perpendicular.
- Three vanilla hearts 0.12 apart at 1.5 blocks merge into one giant red blob; one heart plus the
  bubble trace reads as a heart. Related: the scene checks first sampled the box centre, which
  carries the 0.7 horizontal de-wander bias, so antipodal fish measured ~50% opposite; they sample
  the fish point instead.
- Mock players persist in the gametest player list for the JVM's lifetime, so an "exactly these
  recipients" assertion silently breaks once an earlier test's players are still near spawn; the
  targeting test asserts against the set computed from the same distance rule, strays included.
- `Level#getSharedSpawnPos` no longer exists in 26.1.2; the tests offset mock players from wherever
  they were placed instead of looking up a spawn point.

---

## 19. Post-v1 polish (2026-10-10)

The owner's first live playtest (after bonding their first fish in-game), folded back in the same
day: two motion fixes and a solo-test command surface. Self-test scene `companionpolish` (opt-in)
measures both fixes with numbers and drives every new command.

**The companion's own orientation frame (§A).** Owner: "Rotating player view causes companion to
rapidly interpolate to a new position — as if it is trying to keep same relative position to
player... the fish in practice is a separate entity from the player and should be decoupled from
moving strictly linked to player orientation changes." The baseline paths (the free-swim orbit,
the glide arcs, the drift shoulder, the water follow, the at-the-line hover) were built on
`flatLook()` — the instantaneous camera — so a flick re-based the whole path and the fish chased
it. They now hang off a per-companion yaw (`ClientCompanion.frameOrientationYaw`) that eases toward
the owner's **body** yaw, rate-capped: `FRAME_TURN_STILL = 0.6` / `FRAME_TURN_MOVING = 4.0`
degrees per tick (≈12°/s standing, ≈80°/s walking; vanilla's own walk threshold decides which).
Turning in place no longer relocates the fish — it drifts to its new spot over seconds, like it
chose to move — and the box's own yaw (which rotates the in-box wander) rides the same frame, so
nothing about the follow reads as pinned to the view. The intentional view-coupled moments keep
tracking the camera: peeks, held-item reactions, the rod tip, the hand hop and the dock (where the
box blends back to camera-exact for its side-on pose). The perch/back attach points now use the
body yaw directly — they sit on the body. `behindness()` (dev metric) measures against the frame.
**Measured:** a 90° camera flick while standing still moved the fish 1.465 blocks over the next 12
ticks before, 0.544–0.587 after — the residual is the slow re-anchor plus the orbit's own drift
(~0.17 baseline), i.e. a visible drift instead of a whip.

**Facing follows the world velocity (§B).** Owner, watching runs: "companions moving in a
direction that they are not facing... often seeing fish literally facing the opposite direction as
they are moving. Like the fish sim in the tanks, this should not happen." Root cause: a tank fish
faces its box-local heading and the tank never moves — but a companion's invisible box travels
(orbit, peeks, scenes, retargets), so the fish's true world velocity is box travel + local swim
while the drawn yaw followed only the local part; when they opposed, the fish swam backwards.
`CompanionFlock.submit` now computes the fish's world velocity (box velocity + rotated local
velocity) and, where the pose has a continuous yaw (planar swimmer, crawler, glider), aims the
drawn nose along it, fading in over `FACE_FADE_LOW = 0.005` → `FACE_FADE_HIGH = 0.05` blocks/tick
so near-stationary hovers keep the sim's heading instead of flickering. The binary tank model
(the dock) is exempt: its box is camera-locked, so local is world there. The pose's own organic
yaw wiggle rides on top of the aligned base — that is the fish swimming, not mis-facing.
**Measured** over a G1 scene (box motion dominating): the drawn nose's dot with the true world
velocity went from mean 0.363 before to mean 0.714 with no sampled frame below 0 (min 0.135)
after — previously the dot scattered through backwards frames.

**Solo-playtest commands.** The dev tree grows the full trigger set, client-driven like
`minigame`/`stopgame` (single-player only): `trick`, `peek`, `outcome caught|escaped`,
`catchdemo` + `catch` (a demo bar with one undiscovered fish of your companion's species, so
landing it is a full celebration + same-species greet), `scene g1|g2|stop` (auto-parks a dev
phantom partner when solo and injects through the same method the scene packet handler calls),
`phantom <species> [size]|off` (a parked ownerless second companion), and
`hideothers|hideown|waitatobber <bool>` (the three client settings, persisted like the other
config commands). Every companion animation, reaction and scene is now reachable in a live solo
game without a second player or a headless run.

**Verification.** Fabric and NeoForge, one full suite run each: all eight companion scenes
(`companionbond`, `companion`, `companionparade`, `companionpeek`, `companionhand`,
`companionfishing`, `companionscenes`, `companionpolish`) — **104 checks, 0 failures on both
loaders**. The `companionpolish` scene measures the two fixes with numbers and drives every new
dev command through the real dispatcher (`polish.trickCommand` … `polish.settingRestored`).

- **View decoupling**, measured as an in-run pair: the same 90° flick while standing still, once
  with the pre-polish camera-locked basis (a dev seam that restores exactly what the fix replaced)
  and once with the companion's own frame — peeks and tricks paused so only baseline motion moves
  the fish, with per-window diagnostics confirming `trick=false peeking=false basis=…`. Across the
  passing runs: **camera-locked basis 1.5–3.2 blocks vs own frame 0.1–0.3 blocks** over the 12-tick
  window (the orbit's own drift is ~0.17). The pre-polish basis lunges a full chord; the own frame
  barely drifts.
- **Facing**: the drawn nose's dot with the true world velocity over a scene-driven box motion
  (20 samples per run) went from **mean 0.363 before the fix** to **mean 0.69–0.77, min 0.13–0.38**
  after — the residual spread is the pose's own organic yaw wiggle, and no sampled frame ever faces
  backwards, where the before-run scattered through them.

**Harness findings from the regression passes** (all scene tooling, all fixed; the feature code was
never at fault):
- The bond scene left its encyclopedia screen open, and in the headless harness the client's
  inventory does not resync from behind a custom menu — later scenes read a stale main hand and
  inventory (every held-item reaction read the same stale fish; the unequip-return check lost its
  race). The scene now closes its screen, the harness mirrors server inventory edits onto the
  client view, and the unequip check asserts on the server, the source of truth.
- A natural quick-lap trick landing inside a measurement window tripled the orbit rate and
  masqueraded as view-following; the measurement windows now pause peeks *and* tricks.
- Several scenes only worked as the first scene of a fresh world — the parade found the phase-4
  bond gate refused its unseeded species, and the bond scene's locked-state checks folded once an
  earlier scene had unlocked the species. `bondFish` now seeds its own species' catch count, and
  the suite runs the bond scene first.
- The dev commands' first draft put their client logic in helper methods that used `mc.player`
  directly. Any real use of `Minecraft.player` (a method call, an argument) forces `LocalPlayer`
  to resolve when the command class is verified, and Fabric refuses to load vanilla client
  classes in a server environment — `:fabric:runGametest` died at command registration with
  "Cannot load class net.minecraft.client.player.LocalPlayer". Every dev action now lives in
  `ClientCompanions` (loaded only when a command actually runs in a client), with the rule
  recorded in `CompanionCommand`'s class note. Found by strip-bisecting the command tree, one
  construct per run.

Screenshots reviewed: after the flick the fish is *not* parked in the new view — it stays where it
was, which is the fix's point; during a scene the fish reads side-on along the ring. Open for the
owner, as ever: the exact frame rates and the fade band are named constants to tune in game.


---

## 20. Playtest round 2 (2026-10-10/11)

The owner's second in-game session: (1) close-range jitter, (2) fish still frequently facing away
from their motion, (3) the Companions tab lists too much, (4) the bond picker renders off-screen
when the tab is scrolled. Scene `companionpolish2` (opt-in) measures the motion fixes with numbers,
drives every new check, and screenshots the tab and picker states.

**Facing is now tank-faithful (item 2, supersedes §19's blend).** §19 blended between the sim's
heading and the velocity heading over a speed band, so the drawn nose sat ~44° off its motion on
average (dot 0.71) and the blend weight could change tick-to-tick — the owner read both as
"moving in a direction it is not facing". `CompanionFlock` now measures each fish's **world
velocity per frame** (from consecutive frames' drawn positions: box travel + rotated local swim —
frame resolution, not tick velocities, whose direction flips discretely) and draws the heading
that points along it, turned at a cap of **140°/s — the sim's own yaw rate by construction**
(`PLANAR_TURN_RATE` 7°/tick), with enter/exit hysteresis (`FACE_MOVE_ENTER/EXIT`
0.004/0.002 blocks/tick) so a near-stationary fish holds its facing instead of flickering. The
camera-locked dock stays exempt (local IS world there). **Measured** over a calm-follow window:
mean angular error between the drawn nose and the true world velocity **0.0°** (dot 0.999–1.000,
n≈300) where §19 measured ~44° mean; the fastest calm turn was 51–115°/s. First attempt capped at
2160°/s; the owner watching a live run flagged "large, near instantaneous rotation changes"
during the pet-to-pet scenes — the cap was 15× the sim's own turn rate and the scene paths
demanded more than a tank fish could ever produce. Both were corrected together (below).

**The pet-to-pet scenes move slowly now.** Same review, second message: "G1 is just much too
fast in general. So is the figure eight... we can take time and let fish move slower." The G1 ring
ran a lap in 2 s (180°/s of nose rotation — beyond the sim's own turn rate) and G2's eight in 5 s.
Slowed to a 7 s lap (G1, ~51°/s, ~1 block/s along the ring) and a 10 s eight (G2, ~72°/s per lobe),
with the heart moment moved to 3.5 s — mid-first-lap — and both durations extended to match (240 /
320 ticks). Scene start times and the cooldown are unaffected; the scenes' shared-timing contract
in `CompanionSceneType` is where the numbers live.

**Close-range jitter (item 1).** Two positional suspects were real and are fixed: the box's frame
yaw was drawn **un-interpolated** (its rotation stepped at 20 Hz while everything else interpolated
per frame), and the tick-side `fishCentred` compensation read the engine's **frame-interpolated**
scratch (a render→tick feedback path). The yaw now interpolates like the centre
(`prevFrameOrientationYaw`), and tick-side compensation reads the engine's tick-exact position via
`FlockEngine#toRender` (`CompanionFlock#fishOffsetTick`). An in-run legacy-vs-fixed pair (a dev
seam restoring both old behaviours) measures the drawn position's period-2 reverberation: legacy
flicker 0.005–0.026 vs fixed 0.006–0.019 across four runs (short-window ratio; 0 = smooth
travel), mean per-frame step ~0.01 blocks. The larger perceptual component was the heading: §19's
blend could wobble the nose tick-to-tick at ~10° amplitude, which on a thin sprite at close range
reads as rapid jumping — the velocity-tracked heading removes it (mean error 0.0°, above).

**Companions tab lists only what you can act on (item 3).** A species row now appears **iff** it is
the equipped companion (so Unequip and the name field stay reachable) **or** its bond is unlocked
**and** the player is carrying one of its fish right now (loose inventory or inside a Pile of Fish,
via `CarriedFish`). The "Bond: N/M catches" progress lines and the unlisted species' "unlocks via
quest" line are consequently unreachable from this tab (the Fish tab still carries species
progress; the quest pointer can move to the species page later if wanted). The tab also follows the
inventory live: a cheap carried-fingerprint in `tickCompanionsView` rebuilds the rows when the
carried set moves under the open screen. Scene-checked in all four states (`carriedShown`,
`carriedLockedAbsent`, `unlockedNotCarriedAbsent`, `equippedOnly`) with screenshots, and the phase-4
`companionbond` scene was updated to the new semantics.

**Bond picker pinned under scroll (item 4).** The picker used to be added to the scrolling root at
screen-space positions, so scrolling the tab pushed it off-screen. The dim and the panel are now
pinned to the viewport (local position = screen position + `uiScreen.getScrollY()`, re-applied
every tick by `pinBondPicker`, so no scroll path can displace them) and the screen swallows mouse
scroll while the picker is open (it is modal). Scene-verified at scroll ≈ 50: panel bounds
(210, 110, 220, 72.6) fully inside the 640×360 screen, dim covering the viewport, selection and
Confirm still working scrolled, and the pin holding after a further programmatic scroll — with
screenshots of each.

**Harness findings from this round** (all scene tooling, all fixed):
- The phase-2 `companion` scene's "the fish left the inventory" check read the CLIENT container,
  which the harness does not reliably resync from behind custom menus; a fish mirrored by the
  reworked bond scene's unlisted leg sat in the client's slot 3 and the check failed in the suite
  though it passes alone. The check now asserts on the server (the source of truth) and the scene
  mirrors its own slot seeding; the bond and polish2 scenes mirror their cleanups too, so no scene
  inherits another's carried state.
- One Fabric suite run lost its failure reason to `| tail` (the rule exists for a reason); the
  following run then hit a stale Gradle daemon (`:common:jar` → `ClosedFileSystemException`) fixed
  by `./gradlew --stop`. Both are process notes, not code.

**Verification** — every claim maps to a literal run; the logs are archived in the run dirs
(`fabric/run/logs/suite-run1..5.log`, `targeted-polish.log`; the NeoForge run’s
`neoforge/run/logs/latest.log`):

- **Fabric, run 4** — full 9-scene suite on the final FEATURE code (before the sampling-window
  fix): **119 checks, 1 failure**. The failure was `polish.facesVelocity` (min −0.20): the check
  still sampled the scene’s blend-in entrance — a real >90° direction reversal that the tank-rate
  cap now lags through on purpose. Everything else green.
- **Sampling-window fix** (test-only): the check now starts after the blend. **Targeted
  `companionpolish` re-run: 15 checks, 0 failures** (min 0.17).
- **NeoForge** — full 9-scene suite, one guard-script run on the final feature+test code:
  **119 checks, 0 failures** (`polish.facesVelocity` min 0.51; G1 spin 6.12/6.25 rad, antipodal
  24/24; calm-follow facing mean 0.0° at 43°/s max).
- **Fabric, run 5** — full 9-scene suite on the exact final tree (feature + test), the run the
  commit stands on: **119 checks, 0 failures** (`polish.facesVelocity` min 0.46, mean 0.83; G1
  spin 5.97/6.20 rad, antipodal 24/24; calm-follow facing mean 0.0°, dot 0.999, 35°/s max; jitter
  flicker 0.005 at ~0.011 blocks per frame).

Development history behind those numbers, per run: jitter legacy-vs-fixed flicker pairs across
runs 1–5 = 0.026/0.019, 0.009/0.006, 0.005/0.006, 0.006/0.006, 0.006/0.005. The facing check’s
earlier failures are what pointed at the scene speed and are superseded: run 2 (1080°/s cap)
min −0.09; run 3 (2160°/s cap, old 2 s ring) PASS 0.20; run 4 (140°/s cap, stale window)
min −0.20. Open for the owner: the turn-rate cap, the hysteresis band and both scene timings are
named constants, tunable in game like the rest.
