# Party Phase 1b as built: sim groups from explicit members (2026-10-13)

Fork 1b of docs/fish-companions-party.md (§3, groups from the party). `:fishsim` only, uncommitted,
base `8fea393f`. The parent folds this into spec §12.

## What changed

- **`CompanionGroup` (schools)** takes explicit members: `new CompanionGroup(t, seed, float[] lengths,
  temperament, world)`, 2 or 3 lengths, leader first. A pair has one follower (`followerCount()`).
  Each follower has its own length (`Follower.length()`), radius and spacing. Station offsets and
  separation use **the mean of the two members' lengths** (`pairLength`), which is exactly the
  old single length when they're equal. The old `(t, seed, length, temperament, world)`
  constructor delegates with three equal lengths. `isTrio` / `TRIO_MAX_SIZE_CM` are
  `@Deprecated`: still compiling for the game's current caller, but no longer the source of
  groups.
- **`BenthicGroup` (ground formations)** gains `new BenthicGroup(t, seed, float[] lengths,
  temperament, gait, formation, world)`, 1–3 lengths. A line spaces each member from the one ahead,
  and crowds and water stations space from the leader, each by the pair's mean length. Catch-up
  speed and the trail radius use the member's own length, and the start positions accumulate the
  pair spacings. The old `count` constructor delegates with equal lengths. `length(i)` is new.
- **Seeds:** follower randomness still derives from the **leader's** seed and the follower's
  index, exactly as before, so identical members are bitwise today's groups. Per-member seeds
  matter for solos: `PartyGrouping.memberSeed(stackSeed, slot)` returns `stackSeed` unchanged
  for slot 0 and a splitmix-mixed value for slots 1–2.
- **`PartyGrouping` (new, pure):** the helper 1c calls. Retires size and profile count as the
  source of groups (D8).
- **Harness:**
  - `CompanionRun.school(script, seed, lengths, temperament, tunables, record)`, plus
    `schoolShape()`.
  - `followerMetrics()` generalised to n followers with per-pair radii and drawn lengths
    (identical members give the old values exactly).
  - New constants `SCHOOL_MIXED {.09,.16,.24}`, `SCHOOL_PAIR {.16,.24}`, `SCHOOL_BIG {.25,.40,.60}`,
    `STARFISH_PAIR {.30,.45}` and `SLUG_CROWD_MIXED {.08,.12,.18}`, all true-to-scale blocks.
  - `GroupShapeMetrics` (new): spacing in pair lengths (mean, min) with its rate; overtakes
    (share ahead of a travelling leader, crossings per travelling minute); lag (mean and max
    distance to the leader) with its rate; longest straggle; contact share.
  - `GroupRun` (test) gets a lengths constructor and a `shape` result.
- **Viewer (`runCompanionViewer`):** new classes in the class dropdown:
  - "SCHOOL bluegill 9/16/24 cm";
  - "SCHOOL pair 16/24 cm";
  - "SCHOOL big 25/40/60 cm";
  - "BENTHIC starfish pair 30/45 cm";
  - "BENTHIC slug crowd 8/12/18 cm".

  The renderer draws each follower at its own length. `drawFollowers` lost its `length`
  parameter (callers in the viewer and the headless runner are updated).

## API for 1c

```java
// Up to three members, in any order; empty slots simply absent. Slots are 0-based (spec "slot 1" = 0).
var member = new PartyGrouping.Member(
        speciesId,                // String, e.g. "fishtastic:bluegill"; equality decides grouping
        locomotion,               // grill24.fishsim.core.Locomotion
        gait,                     // GroundGait for a BENTHIC (null = pure percher; null for other classes)
        formation,                // BenthicGroup.Formation from companion_group's style, or null (LINE)
        lengthBlocks,             // this fish's own rendered length
        stackSeed,                // today's ClientCompanion.seedOf(owner, stack)
        slot);                    // 0..2
List<PartyGrouping.Group> groups = PartyGrouping.group(members);   // ordered by lowest slot
for (PartyGrouping.Group g : groups) {
    switch (g.kind()) {
        case SOLO   -> new CompanionAgent(t, g.seed(), loco, length, temperament, gait, driftStyle, world);
        case SCHOOL -> g.school(t, temperament, world);   // CompanionGroup; leader = g.leader() (lowest slot)
        case GROUND -> g.ground(t, temperament, world);   // BenthicGroup, g.formation()
    }
}
// g.slots() maps members back to party slots: SCHOOL follower(i) is slot g.slots()[i + 1];
// GROUND member(i) is slot g.slots()[i].
```

Grouping table (tested):

| Party | Result |
|---|---|
| 2–3 × same FREE_SWIM species | SCHOOL |
| 2–3 × same BENTHIC species with a gait | GROUND (line/crowd from the leader's `formation`, LINE if null) |
| duplicates of GLIDE, DRIFT, ANCHORED, BENTHIC without a gait | SOLO each (D7) |
| different species | SOLO each |

Notes for 1c:
- In game **every BENTHIC currently gets a gait** (`ClientCompanion` defaults to CRAWL), so in game
  all benthic duplicates group. The "pure percher" (null gait) exists only in the harness.
- `ClientCompanion` loops `follower(i)` for `i < 2`; a pair needs `followerCount()`.
- **Length floor:** `ClientCompanion.MIN_LENGTH = 0.25` floors every rendered length, so a
  9/16/24 cm bluegill school is 0.25/0.25/0.25 in game. Mixed spacing then only shows above
  25 cm. Whether to keep the floor for school members is the owner's call; the sim doesn't need
  it.
- `companion_group.count` (common `FishProfile`) is now unused by the sim. Deprecating or
  removing the field is 1c's (it's in common).
- The member seed of a group led from slot 1 or 2 is `memberSeed(stack, slot)`, not the stack
  seed, so a school moved from slots 1–3 to 0–2 swims a different path. That's expected.

## Verification

- `:fishsim:test`: **467 tests, 428 passed, 0 failed, 39 skipped** (opt-in probes).
- **Fingerprints bitwise:** `FingerprintProbe -PfpAll=1` gives 654 lines, identical to the
  pre-change baseline, TRIO included. The new `GroupFingerprintProbe` covers benthic line and
  crowd × 2 and 3 members × 23 scripts × 3 seeds (276 lines, previously unhashed), baselined on
  the untouched code before the refactor and identical after. That's **930/930 unchanged**.
- One defect found and fixed while proving it: a new field `toLeader` was shadowed by a local of
  the same name in `Follower.step`, which changed every TRIO line. A side-by-side copy of the
  original class located the first diverging tick; renamed to `pairToLeader`.
- No Minecraft imports; `--release 17`; `FlockEngine` untouched.

New tests:
- `PartyGroupingTest` (11):
  - the D7 table;
  - slot-order leader and group order;
  - empty slots;
  - rejects duplicate slots, out-of-range slots and more than 3 members;
  - `memberSeed` slot 0 equality and distinct seeds for slots 1–2;
  - an identical 3-member party builds positions **equal to today's** trio and starfish line over
    400 ticks;
  - pair construction.
- `MixedSchoolInvariantTest`: 4 mixed schools × 23 scripts × 3 seeds. Every run must have:
  - no follower in a block, and finite poses;
  - turn, accel, jerk ≤ 160 and tail-first ≤ 0.5%;
  - drawn-body overlap ≤ 1.5% (outside the corridor);
  - straggle ≤ 5 s in the open and ≤ 18 s in obstacle scenes;
  - no leash fades in the open.

  Obstacle impacts are held to ≤ 7.0 b/s and contacts to ≤ 21/min. Those bounds come from the
  identical-member reference's 8-seed envelope (see below), not `TrioInvariantTest`'s 3-seed
  3–5 b/s and 8/min.
- `MixedGroundInvariantTest`: 4 mixed formations × 7 ground scripts × 3 seeds, held to
  `BenthicGroupTest`'s floor:
  - no terrain penetration, finite, no shared perch;
  - stacked ≤ 72, underfoot ≤ 36;
  - line order ≤ 8% (≤ 18% jostled);
  - no fades except the named sprint gap.

## Measured (MixedGroupProbe, 8 equal seeds × 2 min, baseline temperament)

Schools, 23 owner scripts × 8 seeds = 184 runs each (mean | worst):

| School | Runs outside the trio floor | Body overlap % | Straggle s | Spacing L (min) | Spacing rate b/s | Ahead % | Overtakes /min | Lag b | Lag rate b/s |
|---|---|---|---|---|---|---|---|---|---|
| trio 18×3 (ref) | 5 | 0.33 \| 3.44 | 2.20 \| 11.7 | 3.99 (0.051) | 0.55 | 12.3 | 21.3 | 0.92 | 0.66 |
| mixed 9/16/24 | 3 | 0.27 \| 3.69 | 2.06 \| 7.5 | 3.90 (0.052) | 0.52 | 15.1 | 23.0 | 0.84 | 0.63 |
| mixed 24/16/9 | 4 | 0.33 \| 3.40 | 2.14 \| 11.2 | 4.75 (0.029) | 0.55 | 12.1 | 21.1 | 0.91 | 0.65 |
| pair 16/24 | 5 | 0.19 \| 1.60 | 1.87 \| 11.3 | 4.74 (0.004) | 0.66 | 10.8 | 9.9 | 0.95 | 0.66 |
| big 40×3 (ref) | 7 | 0.59 \| 5.41 | 2.79 \| 13.8 | 3.08 (0.043) | 0.74 | 9.4 | 18.5 | 1.46 | 0.79 |
| big 25/40/60 | 10 | 0.53 \| 3.63 | 2.94 \| 17.1 | 2.96 (0.020) | 0.74 | 9.9 | 18.3 | 1.44 | 0.79 |

- "Outside the trio floor" applies `TrioInvariantTest`'s bounds to all 8 seeds. The identical
  reference itself fails 5 of 184 (forest and doorway impact up to 6.4 b/s, forest contacts up to
  13.8/min, stairwell, one 1.54% walk overlap) on seeds 4–8. Mixed small schools are at or below
  it. The big mixed school is 10 against 7 for its identical reference: stairwell straggle
  17.1 s (seed 7), and doorway and forest contacts and impacts.
- Every school run had zero penetrations, zero tail-first, and jerk ≤ 134.
- Worst instantaneous spacing reaches 0.004 L (the pair, `about_face#2`) and 0.05 L for the
  reference. Momentary crossings are as old as the trio, so spacing is reported, not asserted.

Ground formations, 7 scripts × 8 seeds = 56 runs each: terrain penetration 0 and shared slots 0
everywhere.

| Formation | Deep ticks | Underfoot | Fades | Line order % | Spacing L (min) | Overtakes /min | Lag b | Contact % |
|---|---|---|---|---|---|---|---|---|
| line 40×3 (ref) | 11.9 \| 30 | 6.4 \| 22 | 0.57 \| 6 | 4.1 \| 21.9 | 2.50 (0.018) | 12.8 | 1.81 | 4.4 |
| line 30/40/50 | 9.8 \| 28 | 5.7 \| 22 | 0.46 \| 7 | 4.0 \| 20.2 | 2.35 (0.068) | 12.4 | 1.70 | 4.3 |
| line 50/40/30 | 16.9 \| 53 | 9.3 \| 43 | 0.29 \| 4 | 5.7 \| 45.4 | 2.89 (0.067) | 13.9 | 2.00 | 4.8 |
| line pair 40×2 (ref) | 4.0 \| 14 | 4.3 \| 22 | 0.34 \| 4 | — | 3.38 (0.027) | 5.9 | 1.35 | 2.6 |
| starfish pair 30/45 | 2.3 \| 14 | 3.9 \| 23 | 0.23 \| 3 | — | 3.43 (0.120) | 5.8 | 1.29 | 2.2 |
| crowd 12×3 (ref) | 15.4 \| 51 | 4.1 \| 13 | 3.0 \| 21 | — | 6.64 (0.022) | 11.0 | 1.65 | 4.2 |
| crowd 8/12/18 | 16.6 \| 67 | 3.0 \| 15 | 3.0 \| 21 | — | 5.66 (0.022) | 10.6 | 1.30 | 4.4 |

- **The weak spot is a big-leader line (50/40/30):** more stacking (worst 53), more underfoot
  (worst 43, over the 36 bound, on `walk_backward#8`), line order up to 45% on stairs, and sprint
  fades. On seeds 1–3 it stays inside the floor except the sprint fades below. A small-leader
  line (30/40/50) is as good as or better than the reference.
- **Named gap MIXED_SPRINT_FADE:** on `sprint_away#1` the 50/40/30 line fades its two smaller
  followers (4 fades). Identical lines fade on that script too: 0.3×3 2 fades (seed 7), 0.5×3 2
  fades (seed 1). Only the 0.4 line `BenthicGroupTest` is tuned on never does. The cause: the
  leader sets the pace, and a follower's catch-up speed scales with its own body. Allowed up to 4
  a run on `sprint_away`; not tuned (a behaviour change needs the owner).

## Open questions

1. **Big leader, small followers:** should a line or crowd pace itself to its slowest member, or
   should followers' catch-up use the leader's length, so a big lead doesn't strand the small
   fish? Either keeps identical groups bitwise. Not done without an equal-seed A/B and the
   owner's look.
2. **Leader order inside a group:** the leader is the lowest slot (per spec). For lines, the
   measurements favour small-in-front (30/40/50 beats 50/40/30). Keep slot order, or sort a
   group's members by size?
3. **`MIN_LENGTH` 0.25 in game** makes small mixed schools uniform (see notes for 1c).
4. **Large schools (D8):** the 25/40/60 school is a little rougher in obstacle scenes than
   identical 0.40s (10 against 7 of 184 outside the trio floor; stairwell straggle 17 s).
   Acceptable for a first look in the viewer?
