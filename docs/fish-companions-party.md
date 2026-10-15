# Fish companions: the party (three slots), design spec

Status: **draft, 2026-10-13.** Owner request: "three open companion slots that can be mixed and
matched between any three species." Phase 1 is done. Phase 2 was re-scoped by the evidence review
of 2026-10-14 ([`fish-companions-party-phase2-review.md`](fish-companions-party-phase2-review.md);
decisions D13–D14 in §10).

This replaces the single slot in [`fish-companions.md`](fish-companions.md) §8.1 and the
automatic trio and group rules in §2 and in `companion_group`. It changes how the sim plan's
Phase 4 (pet-to-pet, [`fish-companions-sim-plan.md`](fish-companions-sim-plan.md) §5) is
designed. It must land **before** Phase 4 and the remaining drift phases, because both assume
one companion per player.

---

## 1. The design in one paragraph

A player has **three companion slots**. Each holds one bonded fish item, and **each fish is one
body**. Fish of the same species in the party **form a group**: three bluegill are a school,
three starfish are a line, two nudibranchs are a pair. This is the new, and only, way to get a
trio. The old rules that turned one small fish or one starfish into three bodies are retired.
**Free swimmers of different species school together too (D13): a mixed shoal.** Other classes of
different species stay separate members, and the party layer shares out the owner's perches and
space. Slot 1 is the **lead**: the fishing buddy and first claim on your view.

---

## 2. Review: what this costs, and what it buys

**What it buys.**
- **Groups become a choice.** Today a trio appears or not depending on a size threshold the
  player never sees (FREE_SWIM under 20 cm). Now a school is something you build: catch three
  good bluegill and put them in. It's legible, collectable, and it makes a fish's quality and
  size matter in the party.
- **A reason to keep unlocking bonds.** Variety (three species) or a school (one species) is a
  real choice. All slots are open from the start, and the per-species bond gate is the
  progression.
- **Bounded clutter.** At most 3 fish per player, and 12 for four players together. The
  uncapped worst case under the old group rules would have been 9 per player. No budget is
  needed.
- **Self-contained social behaviour.** Mixed parties can react to each other (§6), so Phase 4's
  social work pays off in solo play.
- **No balance risk.** Companions are cosmetic only (spec §1, A2).

**What it costs, in order of risk.**

1. **Existing saves lose their trio.** A player bonded to one small bluegill sees one fish after
   the update, not three. We can't conjure the two missing fish. **Accept it and say so in the
   changelog.** Duplicating items to preserve the trio would be a duplication exploit.
2. **The bond gets diluted.** "Fishing buddy first" is one fish that is yours.
   **Mitigation: the lead (§4).**
3. **The owner's body is shared.** Perch slots are two shoulders, the head, the back and
   transient hands. Three bodies against four fixed perches mostly fits, but ANCHORED needs the
   back or head, and fades "land on the perch" (owner preference). **Mitigation: party-wide
   perch homes and leases (§6.1).**
4. **The space round the owner is shared.** Measured (2026-10-14): different classes already keep
   apart, but same-class units collide. **Mitigation: the mixed shoal (D13) for swimmers, and
   §6.2 for ground benthics.**
5. **Groups of mixed fish.** Group members used to be clones of one stack. Now each member has
   its own size, quality, name and seed. Formation spacing and pace must handle a 9 cm and a
   24 cm bluegill in one school. That reads natural for a real school, but it's untested in the
   sim (§3).
6. **Identity in the plumbing.** Several systems key on the owner alone and become ambiguous:
   - point-of-interest IDs (`-1 - ownerEntityId`);
   - scene `BUSY_UNTIL` and `CompanionScenePacket`;
   - `ClientCompanions` (one per player);
   - the perch layer (`ClientCompanions.get(player)`);
   - rename, unequip and the fishing-event packet.

   Each needs a (owner, slot) identity. This is mechanical but touches every packet.

**Net:** worth doing, and now. Retiring the automatic groups removes the clutter problem
outright. The real feature is still **a party layer in the sim** (§6). Don't scope it as a
storage change.

---

## 3. Groups from the party

| Party | Bodies | Formation |
|---|---|---|
| 3 × same FREE_SWIM | 3 | Trio school (today's `CompanionGroup` formation) |
| 2 × same FREE_SWIM | 2 | Pair: leader plus one follower in the same formation |
| 2–3 FREE_SWIM of **different** species | 2–3 | **Mixed shoal (D13):** the same `CompanionGroup` formation. Every free swimmer in the party joins one school |
| 3 / 2 × same ground BENTHIC | 3 / 2 | `BenthicGroup` line or crowd, style from the species profile |
| 2–3 × same of another class (GLIDE, DRIFT, ANCHORED, pure percher) | 2–3 | Separate party members, no group formation (D7) |
| Different species, not both FREE_SWIM | 1 each | Separate party members (§6) |

- **Mixed shoal (D13).**
  - Measured: three different free swimmers (25/30/40 cm) overlap 0.42% of the time as one school
    (worst 4.5%), against 6.34% (worst 44%) as independent members. The leader's seen % is
    unchanged (review §4 B).
  - The smallest fish leads (D9), whatever its species.
  - **Accepted cost:** the leader's mind decides for the whole shoal. Followers keep their own
    bodies, sizes and looks, but not their own decisions or temperament.

- **The group leader is picked by size (D9): the smallest fish leads.** Ties go to the lowest
  slot, so identical members keep today's leader. The group paces to its slowest member (D10).
- **The old count rules are retired:**
  - `CompanionGroup.isTrio` (FREE_SWIM and under 20 cm). Any size forms a group (D8).
  - `companion_group.count`. The field keeps only its style (`line` / `crowd`), and `count` is
    ignored and deprecated.
- **Mixed members:** each body takes its own size, quality and `render_calibration`. Formation
  spacing uses each pair's body lengths, not one shared length. Seeds come from hash(stack,
  slot), so three identical stacks still swim three different paths.
- **Fingerprint contract:** the harness's TRIO and benthic-group rows are rebuilt as "three
  identical members", seeded so the slot-1 seed equals today's. They must stay bitwise.
  Mixed-size groups get new rows.

---

## 4. The lead

Slot 1 is the **lead** (owner answer D4).

| Thing | Lead | Others |
|---|---|---|
| Fishing dock / reactions (deferred, spec §5) | Docks | Watch from their stations |
| Group leadership | None: the smallest fish leads (D9), so the lead may be a follower | — |
| Hand hop for a first-person look (perchers) | First claim (§6.1 leases) | Only if the lead isn't using a hand |
| Emote key (spec §7, F3) | Plays its trick | Answer with a short reaction |
| Name shown on sneak | Above itself | Above itself (D6) |

---

## 5. Data, sync and UI

### 5.1 Storage
- `CompanionStorage` becomes a fixed-size party: `get(player, slot)`, `party(player)` (3 stacks,
  `EMPTY` for an empty slot) and `set(player, slot, stack)`. It's one attachment on both loaders,
  with a codec for a 3-element list. The data stays under 1 KB per player.
- **Migration:** an old single-stack attachment reads into slot 1. Write a round-trip test for
  both the old and new formats, on both loaders.
- `Companions.bond/unequip/rename/equip` take a slot. `bond` with no slot fills the first empty
  slot. If the party is full, it **never** silently replaces anything (§5.3).
- Kept on death, never in `FishCatchSavedData` (the §8.1 backup-dupe reasoning is unchanged).
- Every slot is bond-gated per species (`CompanionBondGate`), as today.

### 5.2 Identity
- A companion is identified by **(owner UUID, slot)** everywhere: packets, scene bookkeeping,
  point-of-interest IDs, `ClientCompanions` keys and the perch layer.
- Point-of-interest IDs: `-1 - (ownerEntityId * 3 + slot)` (still disjoint from entity IDs).

### 5.3 Menu and the hold-to-bond key
- `CompanionMenu` gets three companion slots in a row, with the lead marked on slot 1. Rename and
  the pile strip act on the **selected** slot. Moving a fish between slots reorders the party.
- A small preview line under the slots says what the party makes: "Bluegill school", "Starfish
  line + Clownfish", or "Mixed shoal" (D13). **This is the only place a player learns that
  matching species, and free swimmers, group up. Don't cut it.**
- Holding **B** over an eligible fish:
  - fills the first empty slot;
  - if the party is full, it opens the menu with the fish highlighted. It never replaces a slot
    silently. Swapping out your lead by accident would be a nasty surprise.
  - The current "same species as your companion opens management" rule goes, because duplicates
    are now a valid way to build a group.
- Shift-click from the inventory goes to the first empty slot.
- Encyclopedia state "Your companion" becomes "In your party", with a lead marker for slot 1.
- Extend `CompanionMenuGameTests`:
  - three-slot equip, return and reorder;
  - full party;
  - three of the same species from one stack and from a pile;
  - migration of an old save;
  - shift-click into the first empty slot.

### 5.4 Commands
- `/fishtastic companion give <player> <species> [size] [slot]`.
- `debug slot <n>` chooses whose overlay shows; the cost line shows party totals.

### 5.5 Rename and pile picking (D12, Phase 1d)

The hidden "selected slot" mode goes. Every action names its own slot.

- **Pencil tabs.**
  - Under each party slot is a small pencil button (drawn in code or from our own atlas; no
    Mojang assets). It's disabled for an empty slot.
  - Clicking it opens the name row bound to **that** slot, with the pencil highlighted.
  - ✓ or Enter saves (`RenameCompanionPacket(slot, name)`), and Esc or the pencil again cancels.
  - If that slot's fish is moved or swapped while the row is open, the row closes without saving.
  - The focused box still eats keys (the anvil rule).
- **Pile strip → cursor.**
  - Clicking a fish in the strip sends a new packet that only describes the fish, like bonding.
    The server finds a fish identical in every component inside a Pile of Fish in the inventory,
    takes it out (`takeOneMatching`'s pile half, including the one-fish-left rule), and puts it
    on the menu's cursor.
  - It only works with an empty cursor, and the server rechecks that.
  - From there it's ordinary vanilla handling: click any party slot, or put it anywhere.
  - **Known catch, accepted:** a fish you change your mind about goes back as a loose fish, not
    into its pile. Closing with it on the cursor returns it to the inventory, or drops it, as
    vanilla does.
- **Game tests:**
  - strip-to-cursor (from a pile, and a pile left with one fish);
  - cursor not empty → refused;
  - a stale description → refused;
  - rename slot 2 while slot 1 holds a fish;
  - rename row closes when its fish moves.

---

## 6. The party layer (sim)

Re-scoped 2026-10-14 by the evidence review
([`fish-companions-party-phase2-review.md`](fish-companions-party-phase2-review.md)). Each rule
below fixes a problem measured by `PartyBaselineProbe`, which runs a party as the game does with
no party layer. What the probe showed already works is kept, not rebuilt:
- different classes keep apart through their class stations (crab + drifter + glider overlap
  1.2%);
- no member is starved of view time (seen % within 1.5 points of solo);
- following quality doesn't suffer (leash fades unchanged).

New in `:fishsim`: `PartyLayer`, which owns one owner's units. The name `CompanionParty` is taken
by the common storage record. It acts on **units** (a solo, a school or a ground formation),
never inside a formation. Units read each other's state from the previous tick only, so the step
order can't change results.

**Hard contract:** a one-member party is **bitwise identical** to today's solo, and an
identical-member group to today's trio and benthic group. All existing fingerprints
(`FingerprintProbe -PfpAll=1`, `GroupFingerprintProbe`) are unchanged, except the intended
ANCHORED change (D5). Every party rule has an on/off switch for equal-ground A/B.

### 6.1 Perch homes and leases
- **Homes:** when the party is built, each percher gets its own home perch, once. This is
  `BenthicGroup`'s `groupSlot` pattern (zero shared perches in every group run), extended across
  units.
  - ANCHORED fish choose first: HEAD, then the shoulders (D5; the back is retired for anchored
    fish).
  - Formation members keep their formation's homes.
  - The rest take what's left: left shoulder, right shoulder, head, back.
  - There are 4 perches for at most 3 bodies, so homes never run out.
- **Measured** (homes only, as a test hook):
  - three anchored fish overlap 97% → 0.4%, with zero clashes;
  - ground clash ticks drop 42–77%.
- **Leases:** the remaining clashes come from transient moves: solo shoulder-wander, hand hops
  and cover. A transient move takes a free perch for its duration, or doesn't happen. The lead
  has first claim on a hand. Cover falls back to the next free perch when the head is a home.
- **Fades** still land on the fish's own home perch, which is always free. The old "perch full"
  fade question goes away.

### 6.2 Different-species ground benthics
- With homes they still overlap 4.8–12.6% on the ground. Candidates are a shared foot-trail
  queue or a mixed-species `BenthicGroup` line. **Measure both before choosing.** Mixed-size
  lines of one species already measure well (1b/2a).
- **Built (2f, §20):** neither. A third candidate won: ground walkers of different units push
  apart, as a crowd's members do, and each keeps its own mind. The queue raised leash fades by a
  third, and the mixed line hid its followers.

### Not built (evidence says no)
- **Attention budget** (formerly §6.3). All three in view happens 3–6.5% of the time, and nobody
  is starved. The owner agrees it works (D14). Watch it in the viewer; build only if crowding
  shows.
- **Station allocation and separation for swimmers** (formerly §6.1). Different classes keep
  apart by station, and same-class swimmers become a mixed shoal (D13).
- **Shared perception** (formerly §6.4). It's performance only and unprofiled, so it's deferred
  to performance work.

---

## 7. Party-mates and other companions

- **Deferred (D20):** the owner playtested parties on 2026-10-14 and wants the mutual curiosity
  kept as it is. The bullet below is the old plan, kept for reference.
- **Party-mates are familiar:** a lower curiosity weight and fast habituation. A new arrival
  gets one greeting inspect (it reads like an introduction), then settles. Today a `COMPANION`
  point of interest has curiosity weight 0.8 (`Drives`), which would mean constant mutual
  inspection.
- **Phase 4's social drive covers mixed parties too.** Predator/prey and class pairings (spec
  §10, G3–G4) can play **inside your own party**. A prey fish ducking behind you while your
  predator does a smug lap is the best solo payoff. Keep intra-party scenes rare (per-pair
  cooldown). G1 (same species) no longer applies inside a party, because same species is a
  group.
- **Predator and prey split (D21):** a free-swimming predator swims apart from its party's prey
  shoal (`PartyGrouping`, the `predator` flag on `Member`, from `#predator_fish`). Measured on
  `predator + 2 prey` (pike 50 + bluegill 20 + perch 25 cm, 240 runs), against the old shoal:
  party-wide overlap 0.42 → 4.00% (worst 4.3 → 31.5%, corridor), episodes 1.67 → 4.92/min; seen
  per slot unchanged (~25%). The overlap is the predator crossing the prey shoal: the problem a
  Phase 4 predator/prey rule has to answer. Fingerprints bitwise (930).
- **Scene scheduling becomes per member:** `BUSY_UNTIL` is keyed by (owner, slot), and a group
  counts as one member. One scene per owner pair at a time; the pairing is picked by the
  existing priority order across the cross-party pairs.

---

## 8. Metrics and gates

The party layer gets new guard-rail metrics, each a level paired with a rate:

| Metric | Why |
|---|---|
| Member-to-member min separation, contact ticks | Overlap inside the party |
| Simultaneous in-view units (watch only) | Attention crowding (no budget, D14) |
| Perch clash ticks, refused leases per minute | §6.1 homes and leases |
| Mutual INSPECT % vs solo | §7 familiarity |
| Per-member seen % vs the same species solo | Does the party starve a member? |
| Mixed-size group: spacing, overtakes, follower lag | §3's new formations |

Owner-script scenes: the existing scripts × representative parties:
- bluegill school of mixed sizes;
- school pair + percher;
- three different free swimmers;
- ground benthic + drifter + glider;
- starfish pair + crab;
- three anchored.

Settings: "Other players' companions" stays a hide toggle (a party is 3 bodies at most). "Hide
own companion in first person" applies to the whole party.

---

## 9. Phases

| Phase | Builds | Gate |
|---|---|---|
| 1 | Storage + migration, (owner, slot) identity, packets, menu with 3 slots and the party preview, hold-B rules, commands, game tests. In game: same-species slots form a group (§3), and different species run **independently** (no party layer yet; overlap expected). Retire the size and count group rules | Game tests on both loaders; trio and group fingerprints bitwise; owner tries the menu and a school |
| 2 | Re-scoped (§6, review §6). **2b** (done, §16) `PartyBaselineProbe` as the metric suite, plus multi-unit parties in the viewer. **2c** (done, §17) mixed shoal (D13). **2d** (done, §18) perch homes + leases (§6.1, with D5's intended ANCHORED change). **Hand visits** (done, §19) the hand clock, round-robin turns, the shoulder peek and more ground beats (D15–D19). **2e** party-mate familiarity tuning (§7): deferred (D20). **2f** (built, §20) ground separation between different-species ground walkers (§6.2) | Solo and identical-group fingerprints bitwise; owner viewer |
| 3 | In game: **3a** (done, §21) party wiring. **3b** (done, §22) per-slot overlay. **3c** (done, §23) dev switch command. **3d** (done, §24) self-test scene. **3e** (done, §25) changelog note on lost trios | Owner solo playtest: **passed 2026-10-15** |
| 4 | Folded into sim Phase 4: social drive across and within parties | Owner viewer, then in game |

Phase 1 is mechanical and low risk. Phase 2 is the real work. Don't let Phase 1's "it works with
three" in game stand in for Phase 2's gate.

---

## 10. Decisions

Answered by the owner, 2026-10-13:

| # | Question | Answer |
|---|---|---|
| D1 | Duplicates allowed? | **Yes. Three of one species is the new way to make a trio** (§3) |
| D2 | Slots open progressively? | **No, all three open at once.** The per-species bond gate is the progression |
| D3 | Body budget? | **No cap.** With one body per fish, a party is at most 3 bodies |
| D4 | A lead? | **Yes, slot 1** (§4) |

Answered by the owner, 2026-10-13 (second round):

| # | Question | Answer |
|---|---|---|
| D5 | Where do several ANCHORED fish go? | **In priority order: head, shoulder, shoulder** (e.g. three garden eels) |
| D6 | Names on sneak? | **Each fish shows its own name above itself** |
| D7 | Non-schooling duplicates (GLIDE, DRIFT, ANCHORED, pure percher)? | **Separate members, no group formation** |
| D8 | Retire the 20 cm school limit? | **Yes: any size schools, spacing scales by body length** |

Answered 2026-10-13: **a lone anchored fish also moves to the head.** The back perch is no
longer used for ANCHORED. This is an **intended** fingerprint change for the ANCHORED rows,
made in Phase 2 with the perch allocator, behind a switch, and reported as such. Phase 1 keeps
every fingerprint bitwise.

Answered 2026-10-13 (after 1b's measurements):

| # | Question | Answer |
|---|---|---|
| D9 | Who leads a group? | **Chosen automatically by size, not by the player.** The smallest fish leads (1b measured lines better that way); the direction is A/B'd on schools in Phase 2a |
| D10 | Does a group pace itself to its slowest member? | **Yes** |
| D11 | Keep the 0.25-block render floor? | **No: every companion, solo or grouped, draws true to size** |
| D12 | How do rename and the pile strip pick a slot without a pick-up click? | **No selected slot.** A pencil tab under each slot renames that fish; the pile strip puts the chosen fish on the cursor (§5.5) |

The party lead (slot 1) is unchanged: it is the party's buddy (§4). Inside a group, the size
rule picks the formation leader.

Answered 2026-10-14 (after the Phase 2 evidence review):

| # | Question | Answer |
|---|---|---|
| D13 | Different-species free swimmers: a mixed shoal or separate members with separation steering? | **Mixed shoal** (§3): 15× less overlap, built on the accepted trio formation |
| D14 | Attention budget? | **Not now.** View time is shared fairly already; watch it, change nothing |

Answered 2026-10-14 (hand visits, after measuring them, §19):

| # | Question | Answer |
|---|---|---|
| D15 | How often may a percher come to the owner's hand? | **About once a minute, solo or party.** It was every ~20 s each, so 8 a minute for three ground fish |
| D16 | Who goes when a party's hand comes free? | **Round-robin:** the fish longest since its own last visit, seen lately or not |
| D17 | A ground walker left behind in the corridor's about-face (it used to ride the hand back)? | **Accept the lag**, and widen the test |
| D18 | How do fish get seen without the hand? | **The shoulder peek and more ground beats.** "Settle in view when the owner stops" was tried at three distances and lost every time, so it's off |
| D19 | Slow ground walkers (crab, slug) are seen less while the owner walks | **Accept the cost:** a 3% seen floor for them under the clock |

Answered 2026-10-14 (after the owner's party playtest):

| # | Question | Answer |
|---|---|---|
| D20 | Make party-mates familiar (2e, §7)? | **Deferred.** Mutual curiosity is intentional and balanced as is (INSPECT 5–9% vs ~1.3% solo). Don't tune it unless a playtest shows a problem |
| D21 | A free-swimming predator and its prey in one party (open from D13, §7) | **Split out of the shoal** (owner, 2026-10-15). A predator (`#predator_fish`) never shoals with a non-predator; predators of different species still shoal together, and a predator's duplicates school |

Answered 2026-10-15 (Phase 4 step 3 design review, §28; the owner accepted every recommendation):

| # | Question | Answer |
|---|---|---|
| D22 | G1 ring shape (the old vertical ring is impossible under the 40° pitch cap) | **Horizontal**, about 0.6 above head height, kept clear of both owners' bodies |
| D23 | Two fish meeting at G2's crossing point | **Pass over and under** (±0.35 block at the crossing, eased out over the lobe) |
| D24 | Scene speed | **Fixed path speed (~1.3× cruise), lap period derived**; `pathTicks` is nominal. G2 needs the owners 2.5–6 blocks apart |
| D25 | Who takes part in cross-player scenes | **FREE_SWIM only** (server filter). Same-species percher, ground, DRIFT and GLIDE pairs play nothing (later G3) |
| D26 | Group followers in a scene | **Keep formation behind the leader**; enlarge the ring if contact is high |
| D27 | GREET | **The opening 1–1.5 s of every scene**, no separate drive; D20 untouched |
| D28 | Priority | **A cross scene beats LAP** and suspends the party's predator scene; only emergencies (catch-up, alarm) refuse it |
| D29 | Two clients' views differ | **Accepted** (sim-plan §3.6); each view must be self-consistent |
| D30 | Cancelling | **Client-side only** (owner leaves, partner gone, party rebuilt); no cancel packet. A line cast mid-scene is ignored while fishing reactions are deferred |
| D31 | Testing alone | **Ghost owner + headless divergence probe**; the owner's real two-client check is optional |

---

## 11. Phase 1a as built (server, data, menu)

Full record: [`fish-companions-party-1a-asbuilt.md`](fish-companions-party-1a-asbuilt.md).
- **Storage:** `CompanionParty`, an immutable 3-slot record (slot 0 = lead).
- **Migration in the codec:** the attachment id is unchanged, and old Fabric bare-stack and
  NeoForge `{stack:…}` saves load into slot 0. Tested at codec level, not with a real old save.
- **Bonding:** `Companions.bond` fills the first empty slot, or returns `PARTY_FULL`. An explicit
  slot swaps out the fish already there.
- **Packets:** carry slot indices (0–2); commands count slots 1–3.
- **Scene bookkeeping** is keyed by (owner, slot).
- **Menu:** three slots, with the lead heart drawn in code (no texture regeneration).
  Hold-B fills the first empty slot, or opens the menu when the party is full.
- **Verified on the merged tree:** `:common:test` and both loader builds pass; Fabric game tests
  356/356.
- Open: rename and the pile strip act on the last clicked slot, which also picks up the fish
  (needs an owner look). The screen hasn't been seen in game.

## 12. Phase 1b as built (sim groups from explicit members)

Full record: [`fish-companions-party-1b-asbuilt.md`](fish-companions-party-1b-asbuilt.md).
- **`PartyGrouping`** (`:fishsim`) groups up to 3 members into SOLO / SCHOOL / GROUND.
  - The leader is the lowest slot, and the slot-0 seed equals today's.
  - `CompanionGroup` and `BenthicGroup` take per-member lengths, with spacing set by each pair's
    mean length.
- **Verified on the merged tree:** all 654 fingerprints identical; 276 new benthic group rows
  are bitwise against the old code; `:fishsim:test` 467 tests, 0 failed.
- **Measured over 8 seeds:**
  - small mixed schools are within the identical trio's envelope;
  - a big mixed school (25/40/60) is slightly rougher;
  - a line with the biggest fish in front (50/40/30) is the weak spot (underfoot 43 against a
    bound of 36, line order up to 45% on stairs);
  - named gap MIXED_SPRINT_FADE: up to 4 follower fades on `sprint_away`.
- **Two grouping rules exist** (this one and 1a's `CompanionGroups`). 1c makes common delegate to
  `PartyGrouping`, so there's one source of truth.

## 13. Phase 1c as built (client wiring)

Full record: [`fish-companions-party-1c-asbuilt.md`](fish-companions-party-1c-asbuilt.md).
- **Groups:** `CompanionGroups` delegates to `PartyGrouping`, so there's one grouping rule.
  `ClientCompanions` keeps one companion per group, keyed by (owner, lowest slot), and rebuilds
  it when the party changes.
- **Lead:** found by which group holds slot 0; it may be a follower in its own school (D9).
- **Retired:** the automatic trio and `companion_group.count`. The count is optional and ignored,
  and it's removed from the three shipped profiles.
- **True size (D11):** every companion. The 0.25 floor only ever affected the sim body and the
  perched pose. A 9 cm bluegill is 0.08 blocks, a tiny speck that's still visible.
- **Names (D6):** each fish's name sits above it while its owner sneaks.
- **Dev:** `debug slot <1-3>` picks the slot for the overlay and the dev triggers; `swell` and
  `squeeze` stay global. The self-test scene `companionparty` is added.
- **Verified on the merged tree:**
  - `:fishsim:test` 470 tests, 0 failed; `:common:test`; both builds;
  - Fabric game tests 360/360; NeoForge 354/354;
  - self-tests 21/21 on both loaders (in the fork).
- **Open for the owner:**
  - Identical labels in a tight school stack into a blob.
  - A 9 cm solo is a 1–2 pixel speck in first person.
  - Party-mates inspect each other until Phase 2's familiarity rule.

## 14. Phase 2a as built (group leader by size, pacing)

Full record: [`fish-companions-party-2a-asbuilt.md`](fish-companions-party-2a-asbuilt.md).
- **D9:** `PartyGrouping` puts each group in formation order, smallest first, with ties to the
  lower slot.
- **Measured over 8 equal seeds:**
  - lines are clearly better smallest-first: worst underfoot 22 against 43, line order on stairs
    20% against 45%, sprint fades 4 → 0;
  - schools are equal or slightly better;
  - crowds are a trade-off: less underfoot and lag, but more member overlap (worst 67 against 37).
- **D10 comes free with D9.** A ground body's pace grows with its size, so the smallest leader is
  always the slowest; for swimmers, pace doesn't depend on size. The explicit cap
  (`groupPaceCap`, default off) acts only on big-leader orders, which D9 never produces, and it
  was a trade-off there.
- **MIXED_SPRINT_FADE closed:** lines allow no fades on any script.
- **Verified on the merged tree:**
  - all 654 fingerprints and 276 group fingerprints are bitwise against `b06bbb25`;
  - `:fishsim:test`: 470 tests, 0 failed.
- **Open:** crowd overlap (keep smallest-first, or try biggest-in-the-middle); benthics under
  10 cm all walk at one pace.
- **Owner, 2026-10-13:** crowds try **biggest in the middle** (fork 2a′, an A/B against
  smallest-first; adopted only on a clear win). Benthics under 10 cm sharing one pace is fine
  as is.
- **2a′ result (not merged):** biggest in the middle fixes crowd overlap (slugs, worst 45 vs 67)
  but makes follower lag worse on 8/8 seeds and under-your-feet time on 6–7/8. Not a clear win,
  so smallest-first stays. The switch `crowdBiggestMiddle` (default off) and the record are in
  worktree `agent-a47b5047676c4a41b` (`build/2a2/party-2a2-fishsim.patch`,
  `docs/fish-companions-party-2a2-asbuilt.md`). Untried idea: keep smallest-first and widen the
  crowd's side spacing when sizes are mixed.

## 15. Phase 1d as built (pencil tabs, pile strip to cursor, refusals)

- **Pencil tabs (D12):** a 12x8 tab, drawn in code, hangs under each party frame and is greyed out
  for an empty slot. Clicking it opens the name row for that slot and outlines its frame. ✓ or
  Enter saves (`RenameCompanionPacket(slot, name)`); Esc or the same pencil cancels. If the slot's
  fish changes while the row is open, the row closes without saving. The hidden "selected slot"
  is gone.
- **Pile strip → cursor:** `TakePileFishPacket(fish)` describes the fish only. The server
  (`CompanionMenu.takeFromPile`) needs an empty cursor and takes the fish from a pile only, never
  an identical loose fish (`Companions.takeOneFromPiles`, the pile half of `takeOneMatching`,
  including the one-fish-left rule). A stale description answers `NOT_CARRIED`.
- **Refusals (owner feedback, 2026-10-13):** the tooltip alone was easy to miss.
  - Holding a fish that isn't bonded over a party slot tints that slot red before the click.
  - A refused click, number-key swap, shift-click or pile-strip click flashes the target slot red
    twice over 0.6 s, along with the fish's own slot or strip cell when it has one. A low note
    plays.
  - The status line names what's missing: "Catch N more <species> to bond." (the same sum the
    encyclopedia shows; the screen requests fresh catch counts on open), or "<species> bonds
    through its quest." for unlisted species, then "See encyclopedia."
  - All of this is client-side. The server's gate is unchanged.
- **Layout:** Piles moved to the title row, so the status line has the full width; the preview
  moved down under the tabs.
- **Verified:**
  - Fabric game tests 365/365, NeoForge 359/359 (5 new `CompanionMenuGameTests`: pile to cursor,
    pile left with one fish, busy cursor, stale description, rename slot 2 with slot 1 filled);
  - `:common:test`;
  - self-test `companionbond` 15/15 on both loaders. It adds editor open/close-on-move, pile to
    cursor and the refusal flash, with shots `pencil_open`, `pile_strip`, `pile_cursor`,
    `locked_flash`, `locked_flash_quest`.

## 16. Phase 2b as built (metric suite, parties in the viewer)

- **`PartyLayer`** (`:fishsim` companion package) owns one owner's units (solo, school, ground
  formation) and steps them together, with no party rules yet: it is the measured baseline that
  2c–2f add switches to. Each unit sees the other leaders as `COMPANION` points within 16 blocks,
  read from the end of the previous tick.
  - A mate's point id comes from its unit's lowest slot, as `ClientCompanions.pointId` keys it,
    not from its list position. Curiosity remembers points by id, so this matters: it moves 3 of
    the review's rows by at most 0.1 point (e.g. starfish pair + crab overlap 12.34 → 12.40).
    These are the new baseline figures for A/Bs.
  - `PartyLayerTest` (always on) pins the contract: a one-unit party is bitwise a bare solo (one
    per class) and a bare school, reversing the step order changes nothing, and a party-mate does
    change what the lead does. The order test was mutation-checked: index-keyed ids fail it.
- **`PartyRun`** (harness) is the §8 metric suite: the representative `PARTIES`, the script list,
  and `measure(...)`, each unit against itself alone on the same script and seed, with a test hook
  on the fresh layer. New rates pair the levels: drawn-overlap and perch-clash episodes per minute.
  `PartyBaselineProbe` now only prints it (`-PtweakB=homes` is still the reflection test hook).
  - Before the point-id change, the refactor reproduced every line of the review's tables byte
    for byte (baseline and homes, 4 seeds).
  - New party: "school pair + percher" (a non-walking benthic). Overlap 0.03%, no clashes.
  - Rates (baseline → homes): crab + octopus + slug clash episodes 2.52 → 1.91/min, starfish pair
    + crab 1.17 → 0.31, three anchored 0.63 → 0; three free swimmers overlap 6.0 episodes/min.
- **Viewer:** a `PARTY <name>` class per suite party. The overlay and dev keys follow the lead's
  unit. The other units are drawn in first person, every body but the followed one is a labelled
  teal mark on the map, and a status line per unit shows slots, kind, goal, seen and perch.
  `./gradlew :fishsim:runCompanionViewer -PsimArgs="--party anchored --owner walk"` opens
  straight into a party. "3 anchored" shows the review's defect directly: all three on `BACK`.
- **Verified:** `:fishsim:test` 476 tests, 0 failed; all 930 `FingerprintProbe -PfpAll=1` +
  `GroupFingerprintProbe` lines bitwise against `09e026c7`.
- **Next:** 2c, the mixed shoal (D13).

## 17. Phase 2c as built (mixed shoal, D13)

- **Rule:** `PartyGrouping` puts every FREE_SWIM member into one school, whatever its species.
  Other classes are unchanged: different-species benthics, gliders, drifters and anchored fish
  stay separate. The smallest fish leads (D9), and the shoal runs on the leader's mind and
  temperament (the accepted cost, §3). Switch: `PartyGrouping.group(party, false)` is the pre-D13
  rule; the suite takes `-PtweakA=noShoal`, and the viewer has a "mixed shoal (PARTY)" checkbox.
- **Measured** (equal grounds: 30 scripts × 4 seeds, switch on vs off). Switch off reproduces the
  2b baseline line for line.
  - The suite gained two measures that compare across groupings: party-wide overlap over every
    pair of bodies (inside a school too), and seen % per slot.

  | Party | Party-wide overlap % mean / worst | Overlap episodes /min | Seen % by slot (1/2/3) |
  |---|---|---|---|
  | 3 free swimmers 40/30/25, separate | 6.34 / 44.4 | 6.00 | 22.8 / 25.1 / 24.2 |
  | 3 free swimmers, **shoal** | **0.47 / 3.3** | **1.87** | 22.2 / 23.6 / 25.6 |
  | school pair + free swimmer, separate | 3.04 / 36.1 | 4.18 | 24.0 / 23.0 / 25.6 |
  | school pair + free swimmer, **shoal** | **0.44 / 5.2** | **1.85** | 24.0 / 23.6 / 22.0 |

  - Every other party is unchanged.
  - **Watch in the viewer:** the 40 cm swimmer that joins the bluegill pair as a follower drops
    from 25.6 to 22.0 seen %. Followers trail their leader, as trio followers already do.
- **Game side** (`PartyGrouping` is shared, so the game shoals too):
  - `CompanionGroups.Kind.SHOAL` for a school of more than one species, two or three fish. The
    menu preview reads e.g. "Clownfish, Bluegill shoal" (each species once, formation order; lang
    `screen.fishtastic.companion.preview.shoal`).
  - `CompanionGroups.of` now ranks leaders by true rendered length (size × the species' render
    calibration, `CompanionGroups.renderedLength`), the same sum the client uses. Raw size only
    ranked fish alike within one species. The preview and the client agree on a shoal's leader.
  - Scenes: two shoals never play G1 (same species), even when their leaders match; they take G2.
  - The client needed no change: each body already renders its own stack.
- **Verified:** `:fishsim:test` 477, 0 failed; `:common:test`; Fabric game tests 366/366, with
  the new `companion_groups_mixed_free_swimmers_shoal` (an 8 cm bridle shiner draws bigger than
  a 10 cm bluegill, so the bluegill leads); NeoForge testmod compiles (its game-test JVM never
  exits, so it wasn't run). All 930 fingerprint lines bitwise against `b5b67c95`.
- **Gate:** owner viewer (`-PsimArgs="--party free --owner walk_turns"`, toggle the checkbox).
  **Passed 2026-10-14:** "the mixed shoals look good". Then 2d, perch homes and leases.

## 18. Phase 2d as built (perch homes and leases, D5)

Owner decisions before building (2026-10-14):
- Fix the head render for D5 now, not in Phase 3.
- A percher keeps the home it would have alone when that perch is free.
- No "lead first on the hands" rule unless the numbers show hand fights. The lead still wins a
  same-tick tie (below).

- **Homes** (`PartyLayer`, switch `partyHomes`):
  - Built once, for a party of more than one unit with at least two perchers.
  - A ground formation keeps its members' homes.
  - Then anchored fish choose: head, right shoulder, left shoulder.
  - Then the others: their own seeded home if free, else left shoulder, right shoulder, head,
    back.
  - Within each group the lower party slot chooses first, whatever order the units are listed in.
- **Leases** (`PerchLeases`, switch `partyLeases`):
  - Each tick, before anyone steps, the layer snapshots every percher's home, the perch it sits on
    and the perch it is heading for.
  - A transient move (shoulder-wander, a hand, cover, a climb from the ground) may go only to a
    perch no mate calls home, sits on or heads for; otherwise it doesn't happen. A fish's own home
    is always allowed, so fades and returns always land.
  - Cover falls back to the head, then the back, then its own home.
  - Two fish that crouch for one free perch on the same tick both see it the next tick, still
    crouching (every crouch lasts at least 2 ticks). A fish sitting there keeps it; otherwise the
    lower party slot goes and the other stays.
  - A hop already in the air can't be recalled. It can't conflict, because it only started from a
    free perch.
- **D5** (switch `anchoredHead`, default on): an anchored fish's home is the head, solo too. This
  is the one intended fingerprint change: with it on, exactly the 69 ANCHORED rows change. With it
  off, all 930 `FingerprintProbe -PfpAll=1` + `GroupFingerprintProbe` lines are bitwise against
  `e6cc5e8a`.
- **Harness:**
  - `CompanionRun`'s centre dwell no longer counts ticks riding a shoulder, the head or the back.
    That's drawn on the player model, which first person never draws, as `seen` already knew. An
    eel on the head under a looking-up script had counted as "parked on screen centre" (325 ticks).
  - `PartyRun` reports refused leases per minute.
  - `PartyBaselineProbe -PtweakB=off|homes|noAnchoredHead` replaces the reflection hook.
  - In the viewer the three switches are checkboxes that rebuild on the same seed.
- **Measured** (equal grounds: 30 scripts × 4 seeds; `off` reproduces the 2c line for line):

  | Party | Overlap % mean / worst | Clash ticks | Refused /min | Seen % by slot | Leash fades (party vs solo) |
  |---|---|---|---|---|---|
  | 3 anchored, off | 96.69 / 100 | 830,569 | — | 0.8 / 0.7 / 0.7 | 0 / 0 |
  | 3 anchored, homes (+ leases, identical) | **0.38 / 7.8** | **0** | 0 | 0.8 / 1.4 / 1.4 | 0 / 0 |
  | starfish pair + crab, off | 12.40 / 68.6 | 26,641 | — | 22.1 / 3.9 / 23.0 | 172 / 171 |
  | starfish pair + crab, homes | 4.83 / 33.4 | 6,125 | — | 22.1 / 3.9 / 23.1 | 171 / 171 |
  | starfish pair + crab, **homes + leases** | **2.61 / 16.1** | **0** | 0.27 | 22.1 / 3.9 / 23.0 | 175 / 171 |
  | crab + octopus + slug, off | 18.39 / 64.2 | 43,840 | — | 22.7 / 25.5 / 19.5 | 316 / 310 |
  | crab + octopus + slug, homes | 14.25 / 57.6 | 28,928 | — | 22.7 / 25.5 / 19.5 | 317 / 310 |
  | crab + octopus + slug, **homes + leases** | **4.06 / 25.1** | **0** | 5.29 | 22.0 / 24.1 / **16.5** | 330 / 310 |

  - Every other party is unchanged.
  - Homes alone on crab + octopus + slug measure worse than the review's strict-order hook (14.25
    vs 12.57), because seeded homes are kept. With leases the shipped figure is 4.06.
  - **The cost (owner look):** with leases, the slug (slot 3) in crab + octopus + slug drops from
    19.5 to 16.5 seen %, and leash fades rise 316 → 330. Homes alone don't do this, so it comes
    from refused hand visits: the right shoulder, the head and the back all share the right hand.
  - Two fixes were A/B'd and **rejected**, since neither was a clear win:
    - from the ground, use the other hand when yours is taken: 17.0 seen %, 319 fades;
    - keep walking up alongside while refused: 16.4.
  - Refusals counted here include wander that would land on a mate's home, which is harmless.
- **Game side:**
  - D5 applies in game now (party homes and leases wait for Phase 3's wiring).
  - Self-test `companionsim` on Fabric, 13/13: the lone garden eel rides the head and stands
    upright out of the crown (shot `anchored_tp`). The 2c run of the same shot shows it on the
    back.
  - First person doesn't draw it (`anchored_fp`).
  - No render change was needed: the head path's up-flip and floor lift suit the planted pose.
  - A side-on view wasn't captured: third person turns the camera with the player.
- **Verified:**
  - `:fishsim:test` 481 tests, 0 failed. Four new `PartyLayerTest` contracts: anchored head then
    shoulders; lone anchored on the head unless switched off; seeded home kept when free;
    distinct homes and zero clashes over 3 parties × 4 scripts × 2 seeds (mutation-checked:
    leases off gives 6 clash ticks).
  - `:common:test`; Fabric game tests 366/366; NeoForge compiles (its game-test JVM never exits,
    so not run).
- **Gate:** owner viewer, e.g. `-PsimArgs="--party anchored --owner walk"` and `--party octopus
  --owner walk_turns`, toggling `partyHomes` / `partyLeases` / `anchoredHead`. Then 2e
  (familiarity).

## 19. Hand visits as built (the hand clock, D15–D19)

Owner feedback 2026-10-14, before 2e: the hand visit came too often, and it would get annoying.

- **Measured first** (`HandVisitProbe`: 30 scripts × 4 seeds, about 4 simulated hours per row):
  - a solo percher came to the hand about 2.7 times a minute (median gap 19 s, about 3.5 s each);
  - 93–97% of visits came from the unseen timer (7–20 s unseen; 0.6× that on the ground), not
    from the owner looking down or holding something;
  - each fish had its own timer, so crab + octopus + slug made 7.9 visits a minute, with a fish
    on the hand 40% of the time and 10% of gaps under a second;
  - the hand was 70–89% of a percher's on-screen time.
- **Hand clock** (`HandClock`, switches `handClock`, `partyHandClock`):
  - after any landing on a hand, the unseen timer can't call a fish there for 45–75 s
    (`handGapMinSeconds`/`MaxSeconds`); looking down or a held item still brings it at once;
  - a party shares one clock; nobody is called while a fish is on a hand or on its way there
    (else a turn could pass mid-crouch and two would go);
  - turns go round (D16): when the clock frees, the percher longest since its own last visit
    comes, seen lately or not, the lower party slot on a tie; it is picked before anyone steps, so
    step order can't change results.
- **Shoulder peek** (switch `shoulderPeek`, D18): a fish riding a shoulder or the head, due to show
  itself while the clock blocks the hand, leans into the bottom corner of the view for 1.5–2.5 s
  (eased in and out, then 7–20 s before the next). The corner is 0.5 to the side and 0.34 down at
  0.6 ahead, about 40° off centre (the hand is about 27°). Ground fish peek too: idle, they rest on
  the shoulder about 65% of the time. Group members don't (it broke formation tests). In game, a
  peek is drawn like the hand in first person, attached to the view at frame rate
  (`CompanionAgent.viewAttached()`/`viewPoint()`, used by `ClientCompanion`).
- **More ground beats** (switch `moreGroundBeats`, `groundBeatSooner` 2.0, D18): overtake and
  roadside beats come twice as soon after going unseen, and the wait after one is halved. Not for
  group members (it broke a mixed-formation test). It helps only fish that can get ahead of a
  walking owner within 5 s: the crab and slug can't, so their beats barely change.
- **Settle in view** (switch `idleInView`, **off**, D18): a ground fish due to show itself takes an
  in-view idle spot when the owner stops. Measured at three distances (before the beat boost), it
  never beat leaving it off:

  | Spot | Crab seen % | `long_idle_then_walk` seen % | Leash |
  |---|---|---|---|
  | off | **14.2** | **27.1** | none |
  | 3–4 blocks ahead | 14.6 | about the same | `doorway`, `forest` |
  | 1–2 blocks ahead | 13.3 | 21.5 | `forest` |
  | 2.3–2.8 blocks ahead | 13.9 | 23.7 | `forest` |
  | 1–2 blocks, 30–45° to the side (left in the code) | 13.4 | 21.3 | — |

  A level 70° view meets the floor about 2.3 blocks ahead, whatever the side angle, so a closer
  spot is off screen; a farther one is left behind when the owner walks off the other way.
- **The corridor** (D17): the old run's crab hopped onto the hand while the owner waited at the
  corridor's end, then rode it back. With the clock it stays down and lags (13 s far, up to 3
  fades). Tried and reverted, as each broke other ground tests: walking up alongside when the hand
  is blocked, a hitch onto the hand or shoulder as the owner walks back past, and a shoulder climb
  when the owner stops.
- **Measured** (equal grounds, all switches on vs `handClock` off):

  | | Visits/min | Seen % | Peeks/min |
  |---|---|---|---|
  | crab solo | 2.7 → 1.1 | 22.2 → 14.3 | 0.4 |
  | starfish solo | 2.7 → 1.1 | 24.5 → 18.0 | 0.4 |
  | seahorse solo | 2.7 → 1.1 | 16.1 → 10.9 | 1.7 |
  | crab + octopus + slug | 7.9 → 1.3 | 22 / 24 / 17 → 11.0 / 12.6 / 9.1 | 2.0 |
  | school pair + percher | 2.8 → 1.1 | percher 18.5 → 11.8 | 1.8 |

  - Ordinary scripts run at 1.0 a minute everywhere; a party goes above that only when the owner
    looks down or holds bait (two fish can come, one per hand).
  - Starfish while walking (`walk_flat`): 17.7 → 28.8% with more beats.
- **Tests changed** (owner accepted the cost, D17 and D19):
  - the percher unseen bound is `handGapMaxSeconds` + 15 = 90 s (was 30);
  - the seen floor is 3% for ground fish and perchers under the clock (measured low 4%), 5% for
    the rest;
  - a ground walker in `corridor` may need the leash 3 times and be 15 s far;
  - one fade is allowed on `stairs_up_down` under the clock (the spider);
  - `BenthicVisibilityTest` asserts ground-vs-perched with the clock off only and prints clock on;
  - new contract `PartyLayerTest.partySharesOneHandClock`: no unseen call within the gap of any
    landing, and every member reaches the hand on `walk_turns`.
- **Verified:**
  - `handClock` off: all 930 `FingerprintProbe -PfpAll=1` + `GroupFingerprintProbe` lines bitwise
    against 2d; on, only the BENTHIC, PERCHER and ground-group rows change;
  - `:fishsim:test` 484 tests, 0 failed; Fabric game tests 366/366; common and NeoForge compile.
- **Gate:** the owner's in-game look at the peek (first person, a seahorse or a resting crab).

---

## 20. Phase 2f as built (ground separation, §6.2)

**Where the overlap was** (`GroundOverlapProbe`, 2d + hand clock, 8 seeds × every script): about
half while the owner walks (both fish in GROUND_FOLLOW or CATCH_UP, escorting or showing off),
and half while they stand (both GROUND_IDLE: loiter, show off, hide). A queue that acts only
while following can fix half of it at most.

**Three candidates, measured** (`PartyBaselineProbe`, 240 runs a party, same seeds and scripts):

| starfish pair + crab / crab + octopus + slug | inter-unit overlap % (worst) | leash fades | seen % by slot |
|---|---|---|---|
| 2d baseline | 4.59 (29.5) / 9.25 (32.1) | 357 / 685 | 9.9·5.0·11.5 / 11.4·12.9·9.3 |
| **separation (built)** | **1.07 (8.5) / 2.16 (12.3)** | 359 / 691 | 10.0·5.1·11.7 / 11.6·12.8·9.3 |
| shared trail queue | 4.20 (19.6) / 5.20 (26.0) | **526 / 909** | ≈ baseline |
| separation + queue | 2.62 (14.1) / 2.06 (12.0) | **662 / 931** | ≈ baseline |
| mixed-species line | party-wide 11.52 / 8.10 (vs 9.52 / 9.25) | 192 / 355 (one unit) | followers **4.5–6** |

- **The queue** sets the following fish's station on the trail of the unit ahead. It barely
  cuts overlap and adds a third more leash fades: a slow crawler queued behind another loses
  ground. It was rejected and removed.
- **The mixed line** runs one mind on the smallest fish. Overlap hardly moves, and the followers'
  seen % halves, because only a formation's leader shows itself. It was rejected and removed.
- **Separation** (`CompanionTunables.partyGroundSeparation`, on): in `PartyLayer`, each ground
  walker on the ground is pushed away from other units' ground walkers. The range is
  max(0.6 × summed lengths, summed radii + 0.04) at the same height (±0.5 b), with strength
  4 × (1 − d/range), as a `BenthicGroup` crowd's members are. It works from last tick's positions,
  and fish exactly on top of each other split by party slot, so step order can't change results.
  `GroundMind` adds the push on top of its own (`partyPush`), except while dodging feet or dazed.
  Every fish keeps its own mind, beats and hand turns.
- What stays: a starfish pair's own overlap inside its line (party-wide 6.51 vs inter-unit 1.07)
  is the same-species formation, untouched. The worst runs are hostile_approach (8.5–12.3%).
- **Scope:** the sim only. The game adopts `PartyLayer` in Phase 3.
- **Verified:** with no second ground unit nothing runs, so all 928 `FingerprintProbe -PfpAll=1`
  + `GroupFingerprintProbe` lines are bitwise against `fd329779`. The 7 other parties print
  identically on and off. `:fishsim:test` 441 passed, 0 failed (`stepOrderDoesNotChangeResults`
  caught an index tie-break, now keyed by slot).
- **Gate:** the owner's viewer look (`-PsimArgs="--party octopus"`, toggle partyGroundSeparation).

## 21. Phase 3a as built (the game runs each party through `PartyLayer`)

- **Wiring:** `ClientCompanions` keeps one `OwnerParty` per player: its synced party, one
  `ClientCompanion` per group, and a `PartyLayer` built on the party's first tick (dev phantoms
  are parties of one). Each tick every companion builds its own world (`ClientCompanion.world`),
  and the layer steps all units together. So the game now has perch homes and leases (§6.1, D5),
  one shared hand clock with round-robin turns (§19), and ground separation (§20). The mixed
  shoal (§17) was already in game through `PartyGrouping`.
- **Sim changes, for the game's sake:** `PartyLayer.step(Function<Unit, CompanionWorld>)` gives
  each unit its own base world, because what the owner holds reads per species (`SAME_SPECIES`)
  and dev triggers act on one unit. A new constructor takes the mate point id from the caller, so
  in game it is keyed by owner and slot as before (§5.2). `step(base)` delegates with the same
  world for every unit.
- **Party-mates as points:** `othersNear` now lists only other parties' companions, and the layer
  appends the party-mates. The overlay's `points` count no longer includes party-mates.
- **Unchanged:** a one-unit party steps exactly as before (the layer passes its world straight
  through). The 0-length guard moved into `ClientCompanion.trueLength`, so the layer's solos get
  the same clamp.
- **Verified:**
  - `FingerprintProbe` + `GroupFingerprintProbe` (`-PfpAll=1`): all 930 lines identical to
    `5b89c24d`;
  - `:fishsim:test` 485 tests, 441 passed, 0 failed;
  - render self-tests `companionparty` + `companionsim`: 23/23 on Fabric and on NeoForge. The mixed party gained
    two checks: `eel_home_head` (the garden eel's home is HEAD and it rides there) and
    `no_shared_perch`.
- **Garden eel fixes from the owner's look (2026-10-14):**
  - *Squashed on equip.* The owner held a lionfish (`predator_fish`), so the eel ducked for the
    whole predator fear, about 14 s (`EelDuckProbe`). Now a perched anchored fish ducks for only
    `CompanionTunables.anchoredPredatorDuckSeconds` (2 s), then comes up and watches the fish
    (`WATCH`). Hostiles and cats are unchanged: a hostile within about 4 blocks still keeps it
    ducked until it leaves. Setting it to 0 restores the old duck. Fingerprints are unchanged
    (930/930): no script holds a predator near a perched anchored fish.
  - *Floating on the shoulder.* The eel art's base is a third of the sprite's width left of its
    centre, so a shoulder eel's base hung past the arm. The planted config gains `base_x`, measured
    per species (−0.3125 for both garden eels), and the companion perch pose puts that point on the
    perch, with the sway pivoting about it. Tanks don't use it.
  - *Debugging:* the overlay shows `threat`, `wariness` and `retract`. The `companioneel`
    self-test checks three eels on head, right shoulder and left shoulder, and the brief predator
    duck. Its shots are zoomed, with a dark backdrop. 15/15 pass on Fabric and on NeoForge.
  - *Shoulder eels face out, centred (owner look, same day; accepted: "looks great").* A planted
    fish on a shoulder stands its base on the arm box's centre: 6 px for classic arms, 5.5 for
    slim (`PlayerModelType` from the render state). Its art turns, from the sign of `base_x`, so
    the lean points away from the head; the other shoulder's eel had curved into it. The sim's
    `LEFT_SHOULDER` is the player's right after the layer's frame flip. Other perchers keep 0.42
    and their facing. 15/15 on both loaders.

## 22. Phase 3b as built (the party overlay)

- **Header** (`/fishtastic companion debug on`): one line per unit, the debug slot marked with
  `>`. Each line shows its home perch (every member's, for a formation), where each member is now
  (on a perch, on the ground, swimming, or hopping to a perch), and `[hand turn]` when the shared
  clock has picked it. Then one line for the party: the hand clock (shared and how long until the
  next call, or each percher's own, or none) and the party's lease refusals, as a count and per
  minute.
- **Selected slot**, two more lines: `party:` home, where it is now, and its own lease refusals
  per minute; `hand:` time since its last hand visit (or since the party was built), whether the
  clock lets it come now, and the ground push from other species (`-` when nothing pushes). The
  formation members line shows each member's home.
- **Sim:** read-only getters on `CompanionAgent` (`handClockWait`, `handClockShared`,
  `handClockReady`, `sinceHandTicks`, `partyPushStrength`). All 930 fingerprints identical.
- **Fixed on the way: shoulders were drawn on the wrong arm.** The perch layer drew
  `RIGHT_SHOULDER` at +0.42 in a frame where +x is the player's left arm (vanilla's parrot sits at
  +0.4 for the left shoulder), while the sim holds it on the owner's right. So a fish landing on a
  shoulder popped across to the other one, and its sneak name hung over the wrong arm. The signs are
  swapped, and the eel facing rule with them; the `companioneel` scene logs each shoulder eel's
  sim side, and a two-eel shot shows `RIGHT_SHOULDER` on the player's right.
- **Self-test:** `companionparty` gains a third party, crab + octopus + slug, and checks that the
  overlay shows the shared clock and the homes. 33/33 on Fabric and on NeoForge.

## 23. Phase 3c as built (the party switch command)

- **Command** (dev builds only: the whole `companion` tree sits behind `DevEnvironmentCheck`):
  - `/fishtastic companion switch` lists the switches (ten at 3c; twelve with `partySwimSeparation`, §26, and `partyPredatorScene`, §27), with changed ones and their defaults
    marked, and `[rebuilds]` on those read only when a party is built;
  - `/fishtastic companion switch <name> on|off` (or seconds, for `anchoredPredatorDuckSeconds`);
  - `/fishtastic companion switch reset`.
- **Switches** (`CompanionTunables.DEV_SWITCHES`): `partyHomes`, `partyLeases`, `partyHandClock`,
  `partyGroundSeparation`, `handClock`, `shoulderPeek`, `moreGroundBeats`, `idleInView`,
  `anchoredHead`, `anchoredPredatorDuckSeconds`. Five of them (`BUILD_TIME_SWITCHES`: homes,
  leases, both clocks, anchoredHead) rebuild every party's sim, which then fades in beside its
  owner. The rest act at once.
- They are one setting on the client, so they apply to every party it runs, and they aren't saved.
  The overlay adds `switches: …` whenever one differs from its default.
- **Verified:** fingerprints identical (930). `companioneel` turns `partyHomes` off through the
  command (all three eels go home to the head, stacked: the pre-2d behaviour), checks the overlay
  line, then resets (head + both shoulders again). 24/24 on Fabric and on NeoForge.

## 24. Phase 3d as built (the self-test scene)

- **Shared hand clock, live** (`companionparty`, after its three parties): the crab + octopus +
  slug party stays equipped. The owner stands still, looking level, with an empty hand, so only
  the clock calls a fish to the hand. For the test the gap after a landing is cut to 4–6 s
  (`ClientCompanions.devHandGap`; it is put back after, and it isn't a switch). Over 60 s:
  - `clock.shared`: the overlay shows `hand clock: shared`;
  - `clock.turns_go_round`: every unit lands on a hand (measured 4 / 5 / 4 landings);
  - `clock.one_at_a_time`: never two on a hand at once (measured: at most 1).
  A `clock_30s` shot shows the overlay mid-rotation.
- **Three eels** (`companioneel`): already head + both shoulders (§21, §23). Added
  `three.no_shared_clock`: eels never visit a hand, so an all-eel party keeps each percher's own
  clock and nothing is ever called.
- **Verified:** `companionparty` + `companioneel`, 28/28 on Fabric and on NeoForge.

## 25. Phase 3e (the changelog note on lost trios)

- `CHANGELOG.md` (the file the publish task posts) gains an Unreleased entry: the three slots, and
  the cost from §2, risk 1. Small free swimmers, starfish and sea slugs no longer bring copies of
  themselves; worlds from test builds see one fish where they saw a group, and the copies can't be
  handed back. Companions came after the 2.0.1 release, so only test-build worlds are affected.

## 26. Phase 4, step 1: predator and prey apart (D21)

- **Grouping** (`PartyGrouping`): a free-swimming predator (`#predator_fish`, the `predator` flag on
  `Member`, set by `CompanionGroups.members`) never shoals with a non-predator. Predators of
  different species still shoal together, and a predator's duplicates school. With no predator in
  the party nothing changes.
- **Swim separation** (`PartyLayer.separateSwim`, switch `partySwimSeparation`, on, live): the
  bodies of different free-swimming units push apart in the water, as ground walkers do (§20) and
  as a school's own members do: range 1.2 mean body lengths plus both radii, 2 blocks/s at contact,
  from last tick's positions. A school's leader and its followers are each pushed, so the predator
  can't swim through the back of the shoal. Only a party with two swimming units is affected.
- **Measured** on `predator + 2 prey` (pike 50 + bluegill 20 + perch 25 cm, 240 runs):

  | | One shoal (pre-D21) | Split, no separation | Split + separation |
  |---|---|---|---|
  | Party-wide overlap % (worst) | 0.42 (4.3) | 4.00 (31.5) | **0.55 (4.1)** |
  | Overlap episodes /min | 1.67 | 4.92 | **1.80** |
  | Seen % per slot | 21–25 | 24–26 | **24–26** |
  | Leash fades, party vs the units alone | 227 vs 227 | 455 vs 455 | **460 vs 455** |

  Every other party's metrics are unchanged; fingerprints identical (930); fishsim 442/0.
- **Viewer:** `./gradlew :fishsim:runCompanionViewer -PsimArgs="--party predator --owner corridor"`.
- **Next:** step 2, the predator scene (§27).

## 27. Phase 4, step 2: the predator scene (D21, G4)

- **What it is:** now and then, with the owner standing still, the party's free-swimming predator
  does one smug lap round them (new mind goal `LAP`: chest height, 1.1 blocks past the owner's body,
  1.3× cruise, the way it was already going round), while its prey shoal fears it as it would a
  predator fish in the owner's hand: the existing `HIDE` goal takes it behind the owner's head, on
  the far side from the predator as it goes round. The prey stays hidden 1.5 s after the lap.
- **When** (`PartyLayer.predatorScene`, switch `partyPredatorScene`, on, live): the first 60–150 s
  after the party is built, then 4–8 min apart (`predatorScene*Seconds` tunables), and only when
  both are free (no catch-up, hiding or fade, no real threat), the owner has stood still 5 s, and
  the lap circle is open water all round. The lap ends after a full turn, at a wall, after 12 s,
  or when the owner walks off (a lap round someone leaving reads as a chase).
- **Measured** (`PredatorSceneProbe`, `predator + 2 prey`, 8 seeds per script):
  - real timing: 0.20 scenes/min on the five-minute idle scripts (one per ~5 min);
  - timing cut short (first 5–10 s, then 20–30 s apart): 397 scenes, 347 laps begun, 224 full
    turns, prey hid in 352. Per lap tick: prey on the owner's far side 82%, prey hiding 98%,
    predator seen 24%, 9 contact ticks in all. Mean lap 6.4 s. In open scripts (idle, camera
    sweep, held items, item drops) most scenes are a full turn with the prey 81–89% on the far side;
  - tried first: a 3 s stand and no open-water check. Scenes then started in doorways, stairwells
    and short pauses on walks, and fizzled (laps of 0.2–1.5 s, no full turns). The 5 s stand and
    the open check removed them; the rest (teleport, elytra, sprint away) are the owner leaving
    mid-lap, which can't be foreseen;
  - `PartyBaselineProbe` for the party is within noise of §26 (scenes are rare); fingerprints
    identical (930); fishsim 442/0.
- **Debug:** the overlay's party header adds `predator scene: …` (on / next in N s / due, with
  counts). `/fishtastic companion predatorscene` starts it now, skipping the wait and the stand.
- **Self-test:** `companionparty` adds pike + bluegill + discus: the pike swims apart from the
  shoal, and after the command it laps (`LAP`) while the shoal hides (`HIDE`), with the overlay
  line. 24/24 on Fabric and on NeoForge.
- **Not yet:** a predator perched or on the ground, and prey that perch or walk, take no part; and
  cross-party predator/prey (two players' companions) is still the server scene's job (§7).

## 28. Phase 4, step 3: cross-player scenes (design, D22–D31)

Agreed 2026-10-15 after a critical design review. Nothing below is built yet. This section
supersedes the spec's §18 geometry (vertical ring, half-period eight at a fixed `pathTicks`) and
its "both clients compute the identical dance".

### 28.1 What exists

- **Server:** `CompanionSceneManager` pairs companions of different owners within `RANGE` 6
  (owner to owner), keyed by (owner, slot), a group through its leader slot; one scene per owner
  pair; per-companion cooldown (no pair history, R5.4); no start while either owner fishes. It sends
  one `CompanionScenePacket` (type, both members, start tick) to players within 128 blocks.
- **Client:** `ClientCompanions.onScenePacket` ignores the packet. Every client already runs a sim
  for every visible party, remote owners included (`ClientCompanions.tick`); the remote owner's
  input is the interpolated entity pose, camera assumed first person at 70°.
- **Already there for the greeting:** another owner's companion is a `COMPANION` point of interest
  (curiosity 0.8), so approach-and-inspect across owners happens today.

### 28.2 The design

- **Who (D25):** `choose()` gives G1 and G2 only to FREE_SWIM pairs. G2 also needs the owners
  2.5–6 blocks apart (D24). Game tests: a same-species percher pair, a jellyfish pair and owners
  under 2.5 blocks play nothing.
- **How the scene reaches the sim:** `PartyLayer.crossScene(type, ticksSinceStart, role,
  partnerPointId, partnerOwnerPointId)`, mirroring `requestLap`. With no request the code path does
  not run, so the 930 fingerprints stay bitwise.
- **Targets come from the clock (D29):** each fish steers toward the point the shared phase gives
  (`gameTime − startTime`, role 0 or 1 → the second fish half a period on), computed from the two
  owners' live poses, never from the partner's live position (that would chase itself and depend on
  step order). The partner's position is used only for contact separation. Both clients then aim at
  the same targets; only lag differs.
- **Geometry:** G1 (D22) a horizontal ring of radius 1.1 round the pair's midpoint, about head
  height + 0.6, grown until it clears each owner by body radius + fish radius + 0.4. G2 (D23) the
  tangent-continuous eight, lobes round each owner (radius floored at the same clearance), one fish
  passing 0.35 above and the other 0.35 below at the crossing. Path speed fixed at ~1.3× cruise
  (D24); the period follows from the path length.
- **The goal:** one held mind goal per scene type (utility ~0.9, above LAP's 0.85; loses only to
  emergency catch-up and alarm HIDE, D28). It opens with the greeting (D27): 1–1.5 s swimming to
  the partner and holding nose to nose, which is also the join. Then pure pursuit of the clock point
  with a lead of ~0.6 rad, up to ~1.6× cruise while behind, ~0.7× while ahead. No lerp, no snap.
  When `durationTicks` runs out the goal releases to ordinary selection (G1 lingers 1 s facing the
  partner first). If the open-water check fails, the fish does the greeting only.
- **Groups (D26):** the unit holding the named slot plays; a school's followers keep formation.
  Of a server `SEPARATE` group (non-schooling duplicates), only the named unit plays.
- **Predator scene (D28):** while a cross scene is on, `PartyLayer` neither starts nor continues
  its own predator scene; an arriving cross scene ends a lap in progress.
- **Cancelling (D30), all on the client:** an owner moves for 1.5 s or more than 9 blocks from the
  scene centre; the partner unit is missing (logout, dimension change, untracked, rebuilt); our own
  party is rebuilt. A packet older than `durationTicks` is dropped, a younger one joins mid-path,
  and one that arrives before the party is built waits up to 20 ticks.
- **Bubble heart:** rebuilt as client particles (one heart plus a bubble trace, the old
  `HEART_SCALE` lesson) for G1.

### 28.3 Verification

- **Headless:** `TwoOwnerRun` (two `OwnerScript`s in one scene, two `PartyLayer`s stepped in a fixed
  order, each seeing the other's fish as `COMPANION` points and owner as a `PLAYER` point, same
  ids as the game), a scene injector, two-owner scripts (approach and stand; one walks off
  mid-scene; owners 1.5 and 6 blocks apart; beside a wall), and a second owner in the viewer. A
  two-owner fingerprint set is recorded before the goals land and must stay bitwise with no scene.
- **Metrics,** each a level with a rate: join time, path error after join, G1 antipodal error,
  contact ticks (fish to fish, fish to owner, all bodies), seen % during scenes, peak turn rate
  against the cap, scenes ended early by reason.
- **Divergence probe (D31):** the same scene run as two client views (each with the other owner
  2–4 ticks late and smoothed); reports cross-view fish distance and scenes played on one view
  only. Gate: each view self-consistent.
- **Solo in game (D31):** a dev-only ghost owner, a real `ServerPlayer` on the integrated server
  with a party, walked by script, so the real scheduler and packet reach the owner's client:
  `/fishtastic companion ghost add <species…>`, `ghost walk approach|stand|leave`,
  `ghost scene g1|g2 [--delay N]`, `ghost remove`; an overlay scene line; a self-test on both
  loaders.
- **What only two real clients show:** real latency and packet order, a second clock, the other
  player's own view. Covered by the dedicated-server two-client self-test (§28.5) where it runs.

### 28.4 Steps

- **3a-0** two-owner harness, scripts, viewer, baseline fingerprints (no behaviour change).
- **3a-1** server filter and G2 spacing, game tests.
- **3a-2** the scene goals; probes; owner looks in the viewer. Also owner clearance (owner,
  2026-10-15): free swimmers keep out of another player's body (§28.6 baseline: 6–21% inside at
  1.5 blocks), since G2's lobes go round both owners.
- **3a-3** the divergence probe.
- **3b-1** packet → `crossScene`, cancellation, overlay line, predator-scene interplay, bubble heart.
- **3b-2** ghost owner and self-test; owner solo playtest.
- **3c** optional real two-client check.

### 28.5 Two-client self-test spike (2026-10-15)

Two real clients on one dedicated server, unattended, on one machine: it works, and step 3 can be
checked with two real views, not only the ghost owner.

- **How:** `docs/fish-companions-twoclient/twoclient.init.gradle` (isolated run directories, nothing
  touches the normal dev run). Start the server, then client A, then client B:
  `.\gradlew.bat :fabric:runServer|runClient -I docs/fish-companions-twoclient/twoclient.init.gradle -PtwoRole=server|a|b`;
  for NeoForge use `:neoforge:runServer|runClient` and add `-PtwoLoader=neoforge` (port 25601, its
  own run directories). The server is offline-mode flat on port 25600 with FishA and FishB opped (offline UUIDs in
  `ops.json`); each client's marker runs the opt-in `twoclient` self-test scene (`server=`, `role=`).
- **The scene:** wait for the peer, stand 3 blocks apart face to face, `companion give` one free
  swimmer each (bluegill, discus), then check the peer is visible, the local party is built, the
  peer's party is built and simulated on this client, and a scene packet arrives; screenshots in
  first and third person; linger 20 s so the peer's shots include it.
- **Result:** all checks pass on both clients, on Fabric and on NeoForge. B joined 22 s after A; both
  received the same `G2_FREE_SWIM_EIGHT` packet (same members, same start tick: 1395 on Fabric, 1409
  on NeoForge) within a second of the parties existing. About 90 s wall time for the whole run. `ClientCompanions` gained
  `devScenePackets` / `devLastScene` for the check.
- **Gaps:** the server JVM outlives a stopped Gradle task: stop it by its port. Chat covers the lower left of the
  shots: clear or hide it in the real self-test.

### 28.6 Step 3a-0 as built: the two-owner harness

- **`TwoOwnerRun`** (`harness/companion`): `Sim` steps two owners and their parties the way
  `ClientCompanions.tick` does. Both owners move, then party A steps against B as it stood, then B
  against A as it now stands (the game steps parties one after another; here A is always first).
  Each unit's world adds the other party's unit leaders as `COMPANION` points within 16 blocks of
  its own leader (id `-1 - (ownerId * 3 + lowestSlot)`, owners A = 1, B = 2) and the other owner's
  feet as a `PLAYER` point within 4 blocks of its own owner, in the adapter's order. Mate ids are
  the game's owner-keyed ones. Party B's seed is the run's xor a constant, so the same species
  gets different stacks. `PartyLayer.Unit.asPoint` became public; nothing else in the sim changed.
- **`PAIRS`:** two free swimmers (G2), same species (G1), school vs solo (D26), predator party vs
  swimmer (D28), crab vs crab (D25: plays nothing), mixed vs mixed.
- **Scripts** (`OwnerScript.PAIR_NAMES`, `OwnerScript.pair`): `pair_approach_stand`,
  `pair_walk_off`, `pair_side_by_side`, `pair_close` (1.5 blocks), `pair_far` (6 blocks),
  `pair_corridor` (3 blocks apart in a one-wide corridor). New segment `faceTo`.
- **Viewer:** "PAIR ..." classes on the `pair_*` owner scripts; the view is owner A's, owner B is
  the blue box, B's fish are drawn and marked B1–B3 on the map.
  `./gradlew :fishsim:runCompanionViewer -PsimArgs="--pair mixed --owner pair_approach_stand"`.
- **Tests:** `TwoOwnerRunTest` (ids match the game and never collide; close owners see each
  other's fish and feet; at 6 blocks fish but not feet; a party never sees its own fish as the
  other's; deterministic; every script brings the owners within 6 blocks). fishsim 448/0.
- **Fingerprints:** the 930 are identical. `TwoOwnerFingerprintProbe` records 108 `FP2` lines
  (6 pairs × 6 scripts × 3 seeds, 2400 ticks), kept in `docs/fish-companions-party-two-owner-fp.txt`;
  3a-2 must leave them identical when no scene is requested.
- **Baseline** (`TwoOwnerBaselineProbe`, 8 seeds, 2 min), the table 3a-2 is judged against.
  Cross-party body overlap is low everywhere (≤1.0%, ≤2.25 episodes/min). Two things stood out,
  both from before any scene:
  - **Fish inside the other owner's body.** A fish's centre is inside the other player's box
    6.2% of the time for two solos 1.5 blocks apart (4.9 episodes/min), 14–15% with a school or
    the predator party, 21% mixed vs mixed (14.9/min), and 17% mixed vs mixed in the corridor. The
    other owner is a `PLAYER` point, which only the ground walkers avoid, so swimmers pass through
    a nearby player. Not a 3a goal; it bears on G2, whose lobes go round both owners.
  - **The corridor:** owner A's fish are never seen (0%) facing owner B 3 blocks away in a
    one-wide corridor (B's 9%): the room in front is taken by the other party.

### 28.7 Step 3a-1 as built: who the server schedules

- `CompanionSceneManager.choose`: both companions must be `FREE_SWIM` (D25), or nothing plays;
  then same species (not a mixed shoal) is G1; otherwise G2 only with the owners at least
  `G2_MIN_OWNER_DISTANCE` = 2.5 blocks apart (D24). Same-species perchers, ground walkers,
  drifters and gliders used to get G1; they now get nothing until G3.
- **Game tests:** new `sameSpeciesPercherPairPlaysNothing` (two starfish),
  `sameSpeciesDrifterPairPlaysNothing` (two man o' war), `freeSwimmersTooCloseForG2` (nothing at
  2 blocks, G2 at 3). `differentFreeSwimmersPlayG2` and `oneScenePerOwnerPairAtATime` now stand
  their owners 3 blocks apart (the latter's bluegill + betta parties are mixed shoals, so they play
  G2). Fabric 369/369, NeoForge 363/363.

### 28.8 Step 3a-2 as built: the scene goals, and owner clearance

- **Owner clearance** (`otherPlayerClearance`, on, a dev switch): swimmers, and a school's
  followers, are pushed off other players' bodies (`PLAYER` points) with the soft room and gain they
  give their own owner (`Mind.playerAvoidance`). Fish inside the other player at 1.5 blocks: two
  solos 6.2 → 0.25%, school vs solo 14 → 1.3%, predator party 15 → 1.8%, mixed vs mixed 21 → 8.2%
  (what's left there is the mixed party's crab, a ground walker, at the other player's feet while it
  inspects, ~2.3%, and the one-wide corridor). Seen % and cross-party overlap unchanged. The 930
  fingerprints are identical (no single-owner script has a `PLAYER` point a swimmer meets); 63 of
  the 108 two-owner fingerprints moved, and `docs/fish-companions-party-two-owner-fp.txt` now holds
  the new ones.
- **`CrossScene`** (one per side): kind (`RING` G1 240 ticks, `EIGHT` G2 320), role (0 = the
  packet's first), the shared clock, both owners' feet in packet order. All geometry comes from
  those (D29), never from the partner fish:
  - **Greeting** (D27), the first 2.5 s: both fish go to the meeting point (the ring's centre, the
    eight's crossing) and wait on the side their path sets out from, so they meet facing each other
    and leave forwards. The path's clock starts as it ends.
  - **G1** (D22): a horizontal ring about head height + 0.6 round the owners' midpoint, radius 1.1,
    moved inside (≥ 0.9) or outside so it never passes within 0.5 of an owner's axis; roles half a
    lap apart; the last second back to the centre.
  - **G2** (D23, D24): tangent lobes round each owner at eye height − 0.25, radius half the gap
    (floor 0.85); both leave the crossing together, one round each owner, one 0.35 above and one
    below, eased out over a quarter lobe. It runs one whole eight (up to 30 s), its length set from
    where the owners stand when the greeting ends (the server starts scenes as a player walks in).
  - **Path speed** 1.43 blocks/s for both roles; a fish aims 0.4 s ahead of its clock point, 0.7× to
    1.6× cruise.
  - **Ends** (D30): time up, an owner walking 1.5 s or more than 9 blocks from where it began, or
    the caller cancels.
- **`Mind` goal `SCENE`** (utility 0.9, above `LAP`; D28): only an emergency catch-up or alarm
  beats it. Within 12 blocks of its owner (`SCENE_REACH`) the catch-up drive is off, since G2's far
  lobe takes a fish 3–9 blocks from its owner; the first run let catch-up win there (path error 1.1,
  joins of 11 s). No open water along the path → the fish sits the scene out ("no room"; greeting
  in a corridor pushed through the owners); a wall across the aim → it leaves ("wall").
- **`PartyLayer.crossScene(slot, scene)`**: the unit holding the slot plays it (D26), only a free
  swimmer (D25); a predator scene on now ends, and none starts while it runs (D28).
- **Harness:** `TwoOwnerRun.Sim.autoScenes` schedules as the server does (range 6, G2 ≥ 2.5 apart,
  scene + 60 s cooldown); `startScene()` starts one now. `measureScenes` and `CrossSceneProbe`
  (8 seeds × 2 min per pair and script):

  | | G1 ring | G2 eight |
  |---|---|---|
  | Join after the greeting | 0.9–2.2 s | 0.2–1.1 s |
  | Path error once joined | 0.13–0.21 | 0.12–0.23 |
  | G1 roles off opposite | 2.6–4.5° | – |
  | Cross-party contact | ≤ 0.4% | ≤ 0.6% |
  | Scene fish inside a player | ≤ 0.55% | ≤ 2% (mixed shoal followers) |
  | Scene leader seen by its owner | 35% (1.5 apart) – 92% | 63–65% |
  | Ticks at the turn cap | 9–12% | 1–3.4% |

  Seen before scenes was 45–51%. The ring's turn-cap ticks are a pivot of about 1–1.5 s as each
  fish turns out of the meeting point, plus the approach and the closing linger; on the ring
  itself it stays under the cap. Corridor: every scene sat out. Side by side, walking: G1 starts and
  ends at once ("owner walked off"); G2 never (2 blocks apart).
- **Tests:** `CrossSceneTest` 10/10 (ring radius rule, roles opposite, the eight smooth and split
  at the crossing and clear of the owners, end rules, both scenes headless, corridor, D25, D28,
  owner clearance). fishsim 458/0. Fingerprints: 930 identical; with no scene requested the two-owner
  ones are identical to clearance alone.
- **Seeing it:** `CrossSceneRenderProbe` writes frames (owner A's view plus a map with both fish's
  tracks) to `fishsim/build/scene-render/`. The viewer's PAIR classes run scenes as the server would,
  Q starts one now, and the bottom line shows each side's scene.

### 28.9 Step 3a-3 as built: two clients' views (D29)

- **`TwoOwnerRun.Sim.lag(lagA, lagB)`** makes one client's view: its own player exact, the other's
  owner arriving a few ticks late (an interpolated remote player), for everything that player's
  owner feeds (its party, its feet as a point, the scene's geometry). **`sceneAt(ticks)`** starts
  scenes at the server's ticks, the same in every view.
- **`CrossSceneDivergenceProbe`**: per pair and script, 8 seeds, the server's schedule from a true
  run, then view A (B late) and view B (A late), lag 2 and 4 ticks:
  - the same fish in the two views, on its path: mean 0.05–0.14 blocks, 95th percentile ≤ 0.95,
    worst 1.4–2.7. The worst moments are while a player is still walking in (the path follows the
    owners, and a late owner is somewhere else); with both standing (`pair_far`) the views are
    identical;
  - no scene played in one view only;
  - each view on its own: path error 0.10–0.15, G1 roles 2.5–5.8° off opposite. Gate passed.
- **Test:** `CrossSceneTest.twoClientsSeeTheRingAlike` (lag 3: mean gap under 0.3). 11/11.

### 28.10 Step 3b as built: in game, the ghost owner, and the self-tests

- **Packet → sim** (`ClientCompanions`): a scene packet waits (up to 40 client ticks, counted on
  the client, logged if dropped) until both named parties have sims, then each plays its side through `PartyLayer.crossScene` (role 0 for the packet's
  first). Each tick before the parties step, `feedScenes` gives every scene the shared clock
  (`level.getGameTime() − start`), both players' feet in packet order (a remote player's
  interpolated position) and the partner unit's leader from this client's own sim of it. A partner
  whose party is gone (logged out, out of view, another dimension) cancels it ("partner gone"); a
  party rebuild drops it. A packet older than the scene ends at once; a younger one joins mid-path.
- **G1's bubble heart**: once per scene per client (the first participant's side), 4.5 s in (mid
  first lap), traced in the camera plane between the two players, 0.2 above their eyes, with one
  vanilla heart through it; never with minimal particles. It uses the mod's `medium_bubble`, which
  lives out of water (vanilla's `bubble` removes itself on its first tick in air, so the Phase 6
  heart was invisible apart from the vanilla heart). The first in-game shot had it above the view
  (it floated over the ring); it now sits between the players. Checked in the self-test (owner
  question, 2026-10-15): all 32 bubbles are in open air and all alive 3 ticks on; they pop when
  they have risen `HEART_POP_RISE` = 1 block (at 0.5, only 13–20 of 32 were left after 1 s; at 1,
  32 of 32), so the heart drifts up and fades over about 2–4 s.
- **Overlay**: the party header adds `scene: EIGHT role 0, 6/16 s, on the path` (greeting,
  joining, on the path, or left: why).
- **Ghost owner** (D31; `CompanionGhost`, dev runs and single-player worlds only): a real
  `ServerPlayer` named CompanionGhost on a dead connection (`GhostConnection`, which on NeoForge
  configures the mock connection as the game tests do), added to the player list with a party.
  The server walks it: it appears 12 blocks ahead, walks up to 3 (or `[blocks]`), stands facing
  you, or walks off 16 and logs out. Commands:
  `/fishtastic companion ghost add <species> [species] [species]`,
  `ghost walk approach [blocks]|stand|leave`, `ghost scene g1|g2 [late ticks]` (forces one now:
  `CompanionSceneManager.devForceScene`, skipping range, cooldowns and class rules; late = the
  packet's start that many ticks in the past), `ghost remove`, and `ghost` alone to describe it.
  It joins and leaves with a chat line and is in the tab list; its player file is written on removal.
  `ghost add` and `ghost scene` refuse to run as the ghost: the first self-test sent them as
  `execute as @p`, which landed on the ghost (the nearest player to world spawn), so the second
  ghost was its own owner and never walked up. The self-test now names its player.
- **Self-tests** (both loaders, isolated run directories via
  `docs/fish-companions-twoclient/twoclient.init.gradle`):
  - `companionscenes` (solo, `-PtwoRole=solo`): the ghost with a discus walks up and the real
    scheduler starts G2; the bluegill is on the path after the greeting; the eight ends "done";
    a bluegill ghost and a forced G1 with the heart (its bubbles in air and still alive); a packet 120 ticks late joins at t ≥ 120; the
    ghost walking off ends a scene ("owner walked off"). Screenshots `g2_fp`, `g2_tp`, `g1_heart`,
    `g1_ring`.
  - `twoclient` (dedicated server + two clients, `run-twoclient.sh fabric|neoforge`): beyond §28.5,
    each client's own fish is on the eight's path, it sees the other player's fish in `SCENE` too,
    and the eight ends "done" on both at the same moment. All checks pass on Fabric and NeoForge.
  - One NeoForge two-client run failed on client B only: it got the packet but never started its
    side, while A played the scene. B was the loaded one (started 45 s late). The pending wait was
    measured on the synced game clock (drop after 20 game ticks). Confirmed by logging the clock
    against client ticks for every waiting scene: on the joining client B the game time jumps
    13–17 ticks while the scene waits a single client tick (3 runs; client A: 0), leaving 3–7 ticks
    of margin, so a slightly larger jump under load drops the scene silently, as seen. The wait now
    counts client ticks (40), and every start logs both numbers and every drop is logged. Since the
    fix: NeoForge two-client 5/5 runs pass on both clients, Fabric 2/2.
  - Solo runs of every companion self-test (`companionbond, companionsim, companionparty,
    companioneel, companionscenes`): 71/71 on Fabric and on NeoForge. Game tests: Fabric 369/369,
    NeoForge 363/363.
- **For the owner's look:** the ring's pivot out of the meeting point (§28.8), the heart's size
  and place, and how close the ghost's discus passes on G2 (third-person shot).
