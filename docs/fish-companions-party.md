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

- **Party-mates are familiar:** a lower curiosity weight and fast habituation. A new arrival
  gets one greeting inspect (it reads like an introduction), then settles. Today a `COMPANION`
  point of interest has curiosity weight 0.8 (`Drives`), which would mean constant mutual
  inspection.
- **Phase 4's social drive covers mixed parties too.** Predator/prey and class pairings (spec
  §10, G3–G4) can play **inside your own party**. A prey fish ducking behind you while your
  predator does a smug lap is the best solo payoff. Keep intra-party scenes rare (per-pair
  cooldown). G1 (same species) no longer applies inside a party, because same species is a
  group.
- **Open for Phase 4 (from D13):** a free-swimming predator and its free-swimming prey in one
  party now share a shoal. Decide in Phase 4 whether such a pair schools anyway, or splits out
  of the shoal.
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
| 2 | Re-scoped (§6, review §6). **2b** (done, §16) `PartyBaselineProbe` as the metric suite, plus multi-unit parties in the viewer. **2c** mixed shoal (D13). **2d** perch homes + leases (§6.1, with D5's intended ANCHORED change). **2e** party-mate familiarity tuning (§7). **2f** measure ground benthic candidates (§6.2), then build the winner | Solo and identical-group fingerprints bitwise; owner viewer |
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
