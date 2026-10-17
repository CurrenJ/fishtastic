# Party Phase 1c as built (for spec §13)

Fork 1c, worktree `D:/GitHub/fishtastic/.claude/worktrees/agent-a310606aaf814cdb4`, base `6db92f87`
plus the parent's fork-2a fishsim patch (`build/party-2a-fishsim.patch`, applied unchanged). This
is the client wiring that finishes Phase 1. Uncommitted. No sim behaviour was changed here.

## What changed

**One grouping rule (§3).**
- `CompanionGroups.of` now delegates to `:fishsim`'s `PartyGrouping`. It maps each slot to a
  `PartyGrouping.Member` (`members(party, registries, length, stackSeed)`, plus the `gaitOf` and
  `formationOf` helpers), then translates the result into the existing API: SOLO, PAIR, SCHOOL,
  LINE, CROWD, and SEPARATE (same-species solos gathered for the preview).
- No rule logic is duplicated in common.
- The server-side grouping passes each fish's size (cm / 100) as its length. Within one species
  that ranks members the same way as the client's rendered length, so the server preview, the
  scene leader and the client agree on D9's "smallest leads".

**ClientCompanions per party (§3, §5.2).**
- Each player's party is grouped with real lengths (`ClientCompanion.trueLength`) and stack seeds
  (`stackSeed(owner, stack)`, the old `seedOf`; `Member.seed()` mixes in the slot, and slot 0 is
  unchanged).
- There is one `ClientCompanion` per group. The whole party rebuilds when
  `CompanionParty.matches` says it changed.
- A `ClientCompanion` holds per-member arrays: stack, `CompanionRender`, perched pose and length,
  all in the group's formation order. A school is built with `group.school(...)`, a ground
  formation with `group.ground(...)`, and a solo with `CompanionAgent` and `group.seed()`.
- School followers are drawn with `followerCount()`, so pairs work.
- Pose hints for every member follow the leader's mind, as the single render did before.
- `local()` returns the unit that holds slot 0, found by slot rather than by leadership (2a: the
  lead may be a follower in its own school). `localCompanions()` and `companionsOf(player)` list
  all units.

**Automatic rules retired.**
- A single small FREE_SWIM fish is a solo, and a single starfish is one starfish.
- `companion_group.count` is now `optionalFieldOf("count", 1)`, `@Deprecated`, and ignored. It
  was removed from the three shipped profiles (starfish, trapania_scurra, willans_chromodoris),
  which keep only `formation`.
- `CompanionProfileGameTests` now asserts the formation only.

**Identity.**
- Point-of-interest ids are `-1 - (ownerEntityId * 3 + lowestSlot)`. The lowest slot is the
  unit's identity, because 2a's formation leader can change with sizes. Phantoms keep their
  below-every-id scheme.
- Party-mates are each other's points of interest (Phase 2 makes them familiar).
- The perch layer now draws every perched member of every unit the player has.

**Names (D6).**
- Added `ClientCompanion.submitNames`. While the owner is crouching and visible, every body gets
  its stack's hover name: the custom name, else the species name.
- It uses vanilla's `submitNameTag` in the discreet, not see-through style, within 32 blocks,
  0.15 blocks above the body's top (`radius + 0.15`).
- Hand-attached bodies use the frame-rate hand point. Perched bodies use the sim's position for
  them.
- It is drawn only where the world pass draws the companion, so the hide-others setting,
  hide-own-in-first-person and the owner's invisibility all apply.

**True size (D11, both rounds).**
- Finding: the world pass already drew every fish at true scale. The 0.25-block `MIN_LENGTH`
  floor only sized the sim's body (radius, spacing, stations) and the perched pose
  (`CompanionFishPose` minLength).
- The floor is gone for solos and groups alike. Only a 0.001-block guard against a degenerate
  zero length remains.
- What leaned on the floor, and how it looks now:
  - **Sim body / collision radius:** a 9 cm bluegill simulates at 0.08 blocks (was 0.25), so it
    swims closer to its owner and to walls.
  - **Perched pose and floor lift:** both scale with the true length, so a tiny percher sits
    proportionally lower on the shoulder.
  - **Name height** follows the radius, so tiny fish get labels close above them. Fine.
  - **Hand-hop placement** is fixed slot points, independent of length. Unchanged.
  - Nothing looked wrong in the shots, but no tiny percher was photographed.
- Smallest bodies:
  - neon goby, mean 5 cm × calibration 1.69: 0.084 blocks (about 0.05 two standard deviations
    down);
  - trapania_scurra, the smallest benthic: 0.13;
  - the self-test's 9 cm bluegill: 0.08.

**Dev tooling.**
- `/fishtastic companion debug slot <1-3>` selects the unit holding that slot. The overlay,
  `temperament`, `fake`, `hurt`, `drowsy`, `ground`, `climb` and `tumble` act on it.
- A temperament override sticks to every slot of the selected unit.
- `swell` and `squeeze` are global render gains, so they are unchanged.
- Overlay:
  - a party header, one line per unit (`slot 1+2+3: bluegill school of 3`), with the selected
    unit marked;
  - the selected unit's detail, including every member length (`0.08/0.14/0.21 blocks`);
  - the cost line shows units and fish (`1 companions (3 fish)`).
- `catchdemo` uses `CompanionStorage.lead`.
- The deprecated `CompanionStorage.get(player)` is removed.
- The scene packet's client hook now receives both slots, on both loaders.

**Self-tests.**
- `companionsim`: the "trio" leg is now `small_solo` (a 9 cm bluegill). Each leg also asserts
  one unit and one body.
- New opt-in `companionparty`, in two parts:
  - a school of 9/16/24 cm bluegill in slots 1–3;
  - a mixed party: bluegill 12, starfish 40, garden eel 40.

  Each part asserts the unit and body counts and the kinds, and takes first-person,
  third-person and third-person-sneaking (names) shots.
- Fixed a latent harness bug: `/fishtastic companion off` needs a player source, and the harness
  runs commands as the server, so it had always silently done nothing. The single-slot scene
  hid this because `give … 1` replaced the lead. Scenes now clear parties directly
  (`clearParties`).

## Files (excluding fishsim)

- **Client companion code:**
  - `common/.../client/companion/ClientCompanion.java` (rewritten);
  - `ClientCompanions.java` (rewritten);
  - `CompanionPerchLayer.java`.
- **Shared companion code:**
  - `common/.../companion/CompanionGroups.java` (delegates to `PartyGrouping`);
  - `CompanionStorage.java` (deprecated accessor removed).
- **Commands and data:**
  - `common/.../command/CompanionCommand.java` (`debug slot`);
  - `common/.../data/FishProfile.java` (`count` optional and deprecated);
  - the `starfish`, `trapania_scurra` and `willans_chromodoris` fish profiles (`count`
    removed).
- **Self-tests:** `common/.../client/selftest/RenderSelfTest.java`.
- **Game tests:**
  - `common/src/testmod/.../CompanionGroupsGameTests.java` (new, 4 tests);
  - `CompanionProfileGameTests.java`.
- **Loader wiring:**
  - `fabric/.../FishtasticFabricClient.java` and `neoforge/.../FishtasticNeoForgeClient.java`
    (scene hook slots);
  - both loaders' game-test registration.

## Results

- `:fishsim:test` (with 2a): 470 tests, 431 passed, 0 failed, 39 skipped.
- `FingerprintProbe -PfpAll=1`: 654/654 lines identical to the 6db92f87 baseline
  (`D:/GitHub/fishtastic/build/p1b-fp.txt`).
- `:common:test`: 859 tests, 0 failures, 1 skipped. `:fabric:build` and `:neoforge:build` pass.
- `:fabric:runGametest`: **all 360 required tests passed**. That includes the 4 new
  `CompanionGroupsGameTests`, one of which checks D9: 24/9/16 cm leads from slot 2.
- `:neoforge:runGametest` (one run): all 353 tests failed at tick 0 with "Failed to place test
  structure". This is the known worktree-environment failure; the same base passed 350/350 on
  the main tree. **NeoForge game tests are unverified here.**
- Render self-tests (`companionparty`, `companionsim`), 21/21 checks on both loaders:
  - **Fabric:** final-tree run.
  - **NeoForge:** the first launch stalled. The fresh run dir had `onboardAccessibility:true`,
    so vanilla's accessibility onboarding screen blocked the harness, which waits for the title
    screen. jstack showed the render thread healthy and no server thread, so it was not the
    create-world deadlock. I set the option to false and the one retry passed.
  - Markers removed from both worktree run dirs afterwards (the main tree's run dirs had none).

## Screenshots

`fabric/run/screenshots/` and `neoforge/run/screenshots/` in the worktree, named
`selftest-<loader>-<scene>-<shot>.png`.

- **`companionparty-school_tp` (Fabric):** three bluegill at true relative scale beside the
  player (overlay: 0.08/0.14/0.21 blocks). The 9 cm fish is a small but visible speck.
- **`companionparty-school_fp`:** the school is out of view at that instant (`seen no (3 s)`).
- **`companionparty-school_tp_names` (both loaders):** names show while sneaking, one per fish.
  **In a tight school the three "Bluegill" labels overlap into an unreadable stack.** This is
  open question 1.
- **`companionparty-mixed_tp` / `_tp_names` (both loaders):** the bluegill (0.11) and the starfish
  on the ground, each labelled. "Garden Eel" sits above the player's head (the eel rides the
  back, and its label comes from the sim's perch point). The eel itself isn't readable from
  behind: the known old back pose, which Phase 2 moves to the head.
- **`companionparty-mixed_fp`:** the bluegill is in INSPECT with `points 2`. Party-mates are
  points of interest, and the lead inspects its own starfish and eel, as spec §7 predicted.
  Phase 2's familiarity rule is needed.
- **`companionsim-small_solo_fp` (Fabric):** **the 9 cm solo is visible in first person** as a
  small dark speck on the ground ahead.
- **`companionsim-small_solo_tp`:** **visible in third person on NeoForge** (a tiny speck right of
  the player). On Fabric it was hidden in front of the body at that instant.
- **`companionsim-benthic_tp` / `anchored_tp`:** the fish shape at the player's right hip is the
  *held* item: `give` returns the replaced lead to the hotbar. It is not a perched companion.
- No overlapping perches showed up in these parties. The mixed party has one percher (the eel,
  BACK) and one ground walker (the starfish, which perches on climb or fade). Two
  different-species perchers on one slot would overlap, as expected without the Phase 2
  allocator; that wasn't photographed.

## Open questions

1. **Names in a school:** three identical labels stack illegibly. Options: one label per group
   ("Bluegill ×3" over the leader), only custom-named fish labelled, or a vertical stagger by
   formation index. Owner's call.
2. **Tiny fish in first person:** a 9 cm solo is a 1–2 pixel speck at 1.5 blocks. True to size,
   but does it still read as a companion? A minimum *drawn* scale for peeks only is an option,
   if wanted.
3. **Party-mates as points of interest** pull the lead into INSPECT on equip. That is Phase 2's
   familiarity rule; until then, expect some mutual inspection in game.
4. **Self-test `give`** leaves the replaced fish in the player's hand, so held-item reactions can
   fire mid-scene. It's harmless for these checks; clear the inventory if later scenes care.
