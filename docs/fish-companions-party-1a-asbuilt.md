# Party Phase 1a as built (for spec §11)

Fork 1a, worktree `D:/GitHub/fishtastic/.claude/worktrees/agent-acd3a5afecb020a61`, base
`43f7d6ac`. Server, data and UI half of Phase 1 (spec §5). Uncommitted. `:fishsim` untouched.
Client companion wiring (one sim per slot, groups, names) is left to 1c.

## What changed

**Storage (§5.1).**
- New `companion/CompanionParty`: an immutable record of 3 stacks (slot 0 = lead), with
  `get/with/firstEmpty/size/isEmpty/matches`.
- Codecs:
  - `CODEC` (Fabric persistent) writes `{slots:[...]}` and also reads the old bare ItemStack.
  - `MAP_CODEC` (NeoForge persistent) writes `{slots:[...]}` and also reads the old
    `{stack: ...}`.
  - `STREAM_CODEC` is a fixed list of 3 optional stacks.
- **Migration is in the codec.** Both loaders keep the same attachment id (`fishtastic:companion`),
  and an old save decodes into the lead slot on load. No join hook is needed.
- `CompanionStorage`:
  - `party/setParty` (ExpectPlatform);
  - `get(player, slot)`, `set(player, slot, stack)`, `lead`, `clear`;
  - `@Deprecated get(player)` returns the lead. Only the client wiring and the catch demo still
    call it, for 1c to migrate.
- Fabric: an empty party removes the attachment. NeoForge: the default is an empty party,
  serialized only when non-empty. Both still copy on death and sync to all trackers.

**Operations.**
- `Companions.bond(player, wanted[, slot])`: with `FIRST_EMPTY` (-1) a full party returns the new
  `BondResult.PARTY_FULL` and changes nothing. With an explicit slot, the fish already there
  returns to the inventory, with the old inventory-room logic.
- `unequip/rename/equip/setFromMenu` all take a slot.
- Duplicates are allowed. The bond gate is unchanged.

**Grouping rule (shared).**
- New `companion/CompanionGroups.of(party, registries)` returns per-species groups in slot order,
  each with a leader slot (the lowest).
- Kinds: SOLO, PAIR, SCHOOL (3× FREE_SWIM), LINE / CROWD (3× BENTHIC, formation from
  `companion_group`), SEPARATE (non-grouping duplicates: GLIDE, DRIFT, ANCHORED; D7).
- `locomotionOf` moved here from `CompanionSceneManager` (the same table as `TankFlockAdapter`).
- 1c should use this, not re-derive it.
- Every BENTHIC counts as grouping, because every benthic currently walks the ground
  (`ClientCompanion` gives all of them a gait). There is no pure percher class to exclude.

**Packets (§5.2).**
- `BondCompanionPacket(fish, slot)`: `slot` = -1 means the first empty slot; a 1-arg
  constructor keeps that default.
- `UnequipCompanionPacket(slot)`, `RenameCompanionPacket(slot, name)`.
- The server validates slots: an invalid one is ignored, or answers NOT_A_FISH / NOTHING_TO_RENAME.
- `CompanionFeedbackPacket.Outcome.PARTY_FULL` is appended, so existing ordinals are unchanged.
- `CompanionScenePacket(scene, first, firstSlot, second, secondSlot, startTime)`. The client
  handlers still read `first()/second()` and ignore the slots (scenes are parked; 1c / Phase 4).

**Scenes.**
- `CompanionSceneManager` schedules per member (owner, leader slot of a group). Pairs are only
  across different owners.
- At most one *playing* scene per owner pair. Once it ends, another pairing of the same two
  owners may start, during the first pair's cooldown. Busy keying is (owner, slot):
  `isBusy(UUID, slot, now)`.

**Menu / screen (§5.3).**
- `CompanionMenu` has 3 party slots (menu 0–2, item x 54/80/106, y 34);
  `INVENTORY_START = 3`, `INVENTORY_END = 39`.
- Kept: the PICKUP handling (SizedItemClickMixin bypass), the mirror pulled before each click,
  QUICK_MOVE run once, and pick-all skipping party slots.
- Shift-click from the inventory puts one fish in the first empty slot. A full party takes
  nothing; it never replaces. Shift-click out of a slot returns it.
- Moving a fish between slots by click or swap reorders the party.
- `CompanionScreen`:
  - The 2 extra frames are drawn by re-blitting the atlas frame (75,29,26×26), and the lead gets
    the 5×4 atlas heart in its frame corner. **The texture was not regenerated.**
  - The header reads "Your party" or "No companions".
  - The party preview line sits under the slots, e.g. "Bluegill school + Starfish".
  - A **selected slot** (the last party slot clicked; default: first filled) gets a white
    outline. The rename box and the pile strip act on it.
  - Party slot tooltips mark the lead.

**Hold-B.**
- Fills the first empty slot. A full party opens the menu with the species highlighted.
- The old "same species opens management" rule is gone.
- The tooltip hint reads "Party full: hold [B] to manage your party" when full.

**Encyclopedia.**
- The management button reads "Your party".
- A species in the party shows "♥ In your party" or "♥ In your party (lead)" instead of the
  bond status.

**Commands.**
- `/fishtastic companion give <player> <species> [size] [slot]`:
  - slots are 1–3, and 1 is the lead;
  - no slot means the first empty one; a full party with no slot fails and changes nothing;
  - a named slot replaces its fish, which returns to the inventory.
- `off [slot]`: no slot returns the whole party.
- **Behaviour change for solo playtests:** repeated `give` now fills slots instead of swapping.
  Name slot 1 to swap the lead.
- The render self-test scenes now pass slot `1`.

**Lang (en_us).** New: `your_party`, `lead`, `in_party(_lead)`, `preview.*`, the party-full
feedback and hint. Reworded: the tutorial body, the keybind name, `click_pile` and
`nothing_to_rename`.

## Files

New: `common/.../companion/CompanionParty.java`, `common/.../companion/CompanionGroups.java`,
this file.

Modified:
- `common/.../companion/{CompanionStorage,Companions}.java`
- `common/.../network/{BondCompanionPacket,UnequipCompanionPacket,RenameCompanionPacket,CompanionFeedbackPacket,CompanionScenePacket}.java`
- `common/.../server/CompanionSceneManager.java`
- `common/.../menu/CompanionMenu.java`
- `common/.../client/{CompanionScreen,CompanionBondInput,FishEncyclopediaScreen}.java`
- `common/.../client/selftest/RenderSelfTest.java`
- `common/.../command/CompanionCommand.java`
- `lang/en_us.json`
- `fabric/.../companion/fabric/CompanionStorageImpl.java`
- `neoforge/.../companion/neoforge/CompanionStorageImpl.java`
- testmod: `CompanionGameTests`, `CompanionBondGameTests`, `CompanionMenuGameTests`,
  `CompanionSceneGameTests`, `PacketRoundTripGameTests`, plus both loaders' registrations.

**Don't stage `common/logs/latest.log`** (the test runs touched it).

## Tests

- New game tests:
  - `bondIntoAFullPartyIsRefused` (also checks the PARTY_FULL feedback mapping);
  - `bondIntoANamedSlotReturnsItsFish`;
  - `threeOfOneSpeciesFillTheParty` (two from one stack, one from a pile);
  - menu `threeSlotsEquipAndReturn`, `movingBetweenSlotsReordersTheParty`,
    `shiftClickFillsTheFirstEmptySlotOnce`;
  - scene `aGroupTakesPartThroughItsLeader`, `oneScenePerOwnerPairAtATime`;
  - `companionSlotPacketsRoundTrip`;
  - `companionPartyCodecsMigrateAndRoundTrip`: both persistent codecs round-trip a party with a
    gap, an old Fabric bare stack and an old NeoForge `{stack}` each migrate into the lead slot,
    and the sync codec round-trips.
- Reworked tests:
  - `bondingASecondFishReturnsTheFirst` → `bondingASecondFishFillsTheNextSlot`;
  - `shiftClickSameSpeciesSwapsOnce` → `shiftClickFillsTheFirstEmptySlotOnce`;
  - `shiftClickWithAFullInventoryChangesNothing` → `shiftClickWithAFullPartyOrInventoryChangesNothing`;
  - `shiftClickTakesOneFromAStack` now fills slot 1;
  - `companionIsKeptOnDeath` checks a party with a gap;
  - `clickAfterAnOutsideBondSeesTheNewCompanion` now bonds into slot 0 explicitly (the pile-strip
    path).
- Results:
  - `./gradlew :common:test :fabric:build :neoforge:build`: BUILD SUCCESSFUL. Common unit tests:
    859 run, 0 failures, 1 skipped. Log: `build/party-1a-build.txt`.
  - `./gradlew :fabric:runGametest`: **all 356 required tests passed**. Log:
    `build/party-1a-fabric-gametest.txt`.
  - `./gradlew :neoforge:runGametest` (one attempt): **unverified**. All 349 tests failed at
    tick 0 with "Failed to place test structure", including unrelated suites (aeration, catch
    data). That's an environment problem in this worktree's game-test run, not this change. Not
    retried. Log: `build/party-1a-neoforge-gametest.txt`.
- Not run: runClient, so the screen layout is unseen, and the `companionbond` self-test scene.

## Deviations and open questions

1. **No texture regeneration.** The extra frames and the lead heart are drawn in code from the
   existing atlas. If the owner wants the frames baked into the texture, regenerate via the GUI
   designer.
2. **Selection model:** the selected slot is the last party slot clicked. Clicking a filled slot
   also picks its fish up (vanilla). To rename without moving anything, the player clicks the
   slot twice (pick up, put back). A dedicated select affordance may read better; it needs an
   owner look.
3. **Commands count slots 1–3**, while packets and the API count 0–2.
4. **Back-to-back scenes:** two owners can play a second pairing right after the first scene
   ends, because cooldowns are per member. Fine while scenes are parked; revisit in Phase 4.
5. **NeoForge game tests** need a run in a known-good environment. The structure-placement
   failure should be checked against main to confirm it's environmental.
