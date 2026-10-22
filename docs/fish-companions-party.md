# Fish companions: the party (three slots)

Status: current behavior contract. The party implementation and solo playtest were accepted on
2026-10-15. The final cross-player scene visuals still need an owner look; see §11.

A player has three companion slots, each holding one bonded fish. Same-species fish group; free
swimmers of different species form a mixed shoal unless predator/prey rules separate them. Slot 1
is the lead and first claim on the owner's view. Current party design and owner decisions are below.

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

The owner chose the mixed-shoal and attention-budget behavior in D13–D14 (§10). The implementation
keeps the already working group formations and independent member decisions:
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

## 9. Current state

The three-slot party, mixed shoals, perch allocation, hand visits, party overlay, and cross-player
scenes are implemented. The owner accepted the party baseline in game on 2026-10-15. The documented
owner decisions in §10 remain the design contract; the detailed phase logs and per-seed metric tables
were removed after implementation.

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

## 11. Remaining owner review

The final cross-player scene needs a brief visual pass for the G1 ring pivot, the heart's size and
placement, and how close the G2 approach passes the other player. The automated solo and two-client
scenes passed on both loaders. The owner chose a ghost owner and divergence probe for verification;
a real two-client owner check is optional.
