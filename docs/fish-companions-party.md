# Fish companions: the party (three slots), design spec

Status: **draft, 2026-10-13.** Owner request: "three open companion slots that can be mixed and
matched between any three species." All decisions D1–D8 are answered (§10). Phase 1 in progress.

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
Different species stay separate members, and the party layer shares out the space round the
owner. Slot 1 is the **lead**: the fishing buddy, first claim on your view, and the leader of its
group.

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
   back or head, and fades "land on the perch" (owner preference). **Mitigation: a party perch
   allocator (§6.2).**
4. **The space round the owner is shared.** Three different-species minds aim for the same
   escort station, peek path and view pass. **Mitigation: station allocation, separation and an
   attention budget (§6).** None of this exists today. Formations work only within one group.
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
| 3 / 2 × same ground BENTHIC | 3 / 2 | `BenthicGroup` line or crowd, style from the species profile |
| 2–3 × same of another class (GLIDE, DRIFT, ANCHORED, pure percher) | 2–3 | Separate party members, no group formation (D7) |
| Different species | 1 each | Separate party members (§6) |

- **The group leader is the member in the lowest slot**, so a school in slots 1–3 is led by the
  lead.
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
| Group leadership | Leads any group it's in | Follow |
| Hand hop for a first-person look (perchers) | First claim | Only if the lead isn't using a hand |
| In-view beats and peeks | Priority (§6.3) | Round-robin turns |
| Emote key (spec §7, F3) | Plays its trick | Answer with a short reaction |
| Escort station | Best station (§6.1) | The remaining ones |
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
- A small preview line under the slots says what the party makes: "Bluegill school" or "Starfish
  line + Clownfish". **This is the only place a player learns that matching species group up.
  Don't cut it.**
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

---

## 6. The party layer (sim)

New in `:fishsim`: `CompanionParty`, which owns one owner's members. It forms the groups (§3) and
arbitrates only what the owner's body and space force them to share. Each mind stays its own.

**Hard contract:** a one-member party is **bitwise identical** to today's solo, and a
three-identical-member group to today's trio and benthic group. All existing fingerprints
(`FingerprintProbe -PfpAll=1`) are unchanged. Every party rule has an on/off switch for
equal-ground A/B.

### 6.1 Stations and separation
- **Escort stations allocated per party:** the lead (or its group) takes its class's natural
  station, and the others take offsets (the other shoulder side, higher, behind). A class keeps
  its station character (GLIDE wide and high, DRIFT behind a shoulder, ground benthics behind
  the feet).
- **Mutual separation between party bodies**, as a steering term inside the existing
  heading-constrained swim (never a push or a lerp: *companions always swim*).
- **Different-species ground benthics share the foot trail** as one queue, so they don't fight
  for the spot behind your heels.

### 6.2 Perch allocation
- One allocator owns RIGHT_SHOULDER, LEFT_SHOULDER, HEAD, BACK and the hands. Each body *asks*;
  the allocator *grants* in priority order:
  1. ANCHORED: HEAD, then the shoulders (D5; the back is retired for anchored fish);
  2. pure perchers;
  3. the lead's hand hop;
  4. ground benthics climbing on.
- **Fades:** a ground benthic's fade lands on a free perch as today. If none is free, it fades
  in at its ground station. **This changes the owner's "fades land on the perch" rule when the
  body is full; flag it at the gate.**
- **D5 (owner):** several ANCHORED fish take, in priority order, the head, then the shoulders.
  Three garden eels sit on the head and both shoulders.

### 6.3 Attention budget
- At most **one in-view beat or peek at a time** across the party. The lead has priority, and
  the others take round-robin turns with a gap. A group beats as one unit, as today. Held-item
  reactions stay on for everyone, with look-at targets spread so they don't stack.

### 6.4 Shared perception
- One `LocalVoxels` window and one `CompanionWorld` per owner per tick, shared by the party.

---

## 7. Party-mates and other companions

- **Party-mates are familiar:** a lower curiosity weight and fast habituation. A new arrival
  gets one greeting inspect (it reads like an introduction), then settles. Today a `COMPANION`
  point of interest has curiosity weight 0.8 (`Drives`), which would mean constant mutual
  inspection.
- **Phase 4's social drive covers mixed parties too.** Predator/prey and class pairings (spec
  §10, G3–G4) can play **inside your own party**. A prey fish ducking behind you while your
  predator does a smug lap is the best solo payoff. Keep intra-party scenes rare (per-pair
  cooldown). G1 (same species) no longer applies inside a party, because same species is a
  group.
- **Scene scheduling becomes per member:** `BUSY_UNTIL` is keyed by (owner, slot), and a group
  counts as one member. One scene per owner pair at a time; the pairing is picked by the
  existing priority order across the cross-party pairs.

---

## 8. Metrics and gates

The party layer gets new guard-rail metrics, each a level paired with a rate:

| Metric | Why |
|---|---|
| Member-to-member min separation, contact ticks | Overlap inside the party |
| Simultaneous in-view bodies, beat overlaps | Attention crowding (§6.3) |
| Perch grant churn per minute, refused asks | Allocator thrash |
| Fades per minute by cause (incl. "perch full") | §6.2's changed fade rule |
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
| 2 | Headless `CompanionParty`: mixed-size groups, stations, separation, perch allocator, attention budget, shared perception, party-mate familiarity; metrics; party scripts in the viewer | Solo and identical-group fingerprints bitwise; owner viewer |
| 3 | In game: party wiring, per-slot overlay, self-test scene, changelog note on lost trios | Owner solo playtest |
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

## 13. Phase 1c as built (reserved: client wiring)
