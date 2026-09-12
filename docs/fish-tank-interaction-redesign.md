# Fish Tank Interaction Redesign

**Status:** approved, not yet implemented. Decisions in §3 are locked; see §6 for the implementation checklist.

## 1. Problem statement

The tank currently exposes four player actions — add fish, remove fish, add cosmetic, remove
cosmetic — through an overloaded set of raycast/empty-hand interactions gated by a hidden,
per-player, unpersisted "edit mode" toggle (`FishTankEditModeManager`, `ToggleEditModePacket`).
This has three concrete problems:

1. **Discoverability/fragility.** Whether an empty-hand click extracts a fish or removes a
   cosmetic depends on invisible state (edit mode) that isn't shown anywhere in the world or HUD
   beyond a transient action-bar message, and isn't synced to anything persistent. Players lose
   track of which mode they're in and get surprised by the result.
2. **No selection.** Fish removal is LIFO on whichever tank segment you're aimed at
   (`FishTankBlockEntity.extractItem`, last-occupied-slot pop) — there's no way to see what's in
   the tank or choose *which* fish to remove. Cosmetic removal is raycast-only (aim precisely at
   the cell/structure you want gone).
3. **Segment-scoping no longer matches the mental model.** Since fish rendering moved to pooled
   swarm/flock simulation across a connected multi-tank group (`TankGroups`,
   `TankFlockAdapter`), fish visually belong to the whole connected structure, not to whichever
   single block they were dropped into. But storage, capacity, and interaction are still strictly
   per-block-entity (`FishTankBlockEntity.items`, `TankCapacity.canAdd`) — so a tank can look full
   of fish anywhere in the structure while the specific segment you're clicking is refusing inserts
   or serving up an arbitrary fish on extraction.

## 2. Goals

- Remove the edit-mode toggle entirely. No hidden per-player state should gate what a click does.
- One consistent rule for **adding**: right-click with a compatible item in hand adds it (fish,
  pile of fish, cosmetic, structure cosmetic) — this already mostly works today and should be kept,
  just without edit-mode interference.
- One consistent, discoverable mechanism for **removing** anything (fish or cosmetic): a GUI,
  opened by a click, that lists the full contents of the connected multi-tank and lets the player
  pick exactly what comes out.
- Treat a connected multi-tank as the unit of capacity and identity for fish, matching what the
  player already sees (swarm/flock pooling). A segment should stop being a meaningfully separate
  fish container from the player's point of view.
- Give cosmetic placement/removal a more robust interaction than raycast-the-exact-cell, ideally
  unified with the same GUI used for fish.

## 3. Decisions (resolved)

All three recommendations below are approved. Kept as a record of the load-bearing choices and
their rationale.

### 3a. Does fish *storage* become multi-tank-pooled, or stay per-segment with a pooled *view*?

**Decision: Option B.**

- **Option A — server-authoritative shared pool.** Introduce a real multi-tank grouping concept
  (today `TankGroups` is client-only/render-only and rebuilt every extract call). One elected
  member (or a lightweight pool object) becomes the actual inventory; `TankCapacity` becomes a
  group-level budget instead of `BASE_BUDGET` per segment. This is the "matches the mental model"
  answer but is a real storage-model migration — capacity math, save/load, and the swarm renderer's
  existing anchor-election logic all need to agree on the same group definition.
- **Option B — storage stays per-segment; GUI/capacity become group-aware.** Keep each
  `FishTankBlockEntity.items` as today's ground truth (simplest migration, no save-format change),
  but (1) the new fish-list GUI queries every connected segment and shows one merged list, (2) "add
  fish" picks *some* segment with room (e.g. the clicked segment first, else the least-full
  neighbor) rather than requiring the clicked segment specifically to have room, and (3) capacity
  checks sum across the group before rejecting an insert. This gets most of the player-facing
  benefit (you never have to think about which specific block has room, and you always see
  everything) without an inventory/save-format migration.

Given the effort/risk gap, **I'd lean Option B** — it's additive on top of the existing
`TankGroups` flood-fill (which already computes group membership; it would just need to run
server-side too, or be replaced by a small server-side equivalent) and defers a full storage
migration until/unless it's actually needed. Worth confirming before I plan implementation.

### 3b. How is the removal GUI opened?

**Decision: plain empty-hand right-click.**

Candidates:
- Empty-hand right-click opens the GUI directly (replaces today's "empty hand extracts LIFO fish"
  behavior entirely). Simplest, most discoverable, no new input to teach — right-click always does
  "the obvious thing for what's in your hand," including empty.
- Sneak + empty-hand right-click, leaving plain empty-hand click for something else. Adds a
  modifier for players to remember and doesn't have an obvious use for the plain-empty-hand slot
  once LIFO-extract is gone.

**Recommendation: plain empty-hand right-click opens the GUI.** It directly replaces the
blind-LIFO-extract behavior with a strictly better version of the same gesture ("I have nothing in
hand, I'm interacting with what's already in the tank"), and needs no new modifier key.

### 3c. Does the GUI also handle cosmetics, or do cosmetics keep a separate world-click removal path?

**Decision: same GUI, second section.**

Given goal 5 ("more robust way to place/remove the cosmetic across the tank"), I'd fold cosmetics
into the same GUI as a second tab/section rather than maintaining two different removal
mechanisms — see §4.3. Flagging as a decision point since it's a bigger scope than "just fix fish."

## 4. Proposed interaction model

### 4.1 Adding (right-click with item in hand) — unchanged in spirit, edit-mode dependency removed

Right-click with a compatible item in hand adds it to the tank, exactly like today's
`FishTankBlock.useItemOn` add-path (lines 212–270, 343–416 for the various item-type branches),
with edit-mode checks deleted. No behavior change here beyond removing the toggle's interference —
this path never depended on edit mode being on or off to begin with (per the current-state survey,
placement already runs identically in both modes today).

If §3a resolves to Option B, "add fish" additionally needs a segment-picking step when the clicked
segment's own `TankCapacity.canAdd` fails: check sibling segments in the same connected group
(reusing/adapting `TankGroups`' adjacency walk) and insert into the first with room, rather than
bouncing the interaction. If every segment in the group is full, the interaction fails as it does
today.

### 4.2 Removing fish — new GUI, no more edit mode or LIFO-only extraction

- **Trigger:** plain right-click with an empty main hand on any tank segment in the connected
  group (§3b).
- **Contents:** the GUI enumerates every fish/display item across every segment in the connected
  multi-tank (not just the clicked segment), shown as a single scrollable slot grid — closely
  modeled on the existing `ElectricFishOrganizerMenu`/`ElectricFishOrganizerScreen` pattern (plain
  `AbstractContainerMenu` over a `Container`, with a restrictive slot subclass and `DataSlot`s for
  any sort/filter state), which already does almost exactly this for a different block.
- **Removal:** clicking/shift-clicking a slot pulls that specific fish out into the player's
  inventory (or drops it if the inventory is full — matching existing pickup behavior elsewhere in
  the mod). No more LIFO-only; the player picks exactly which fish leaves.
- **Multi-segment plumbing:** the menu's slots need to address `(segment BlockPos, slot index)`
  pairs instead of a single container's flat slot list, since the "container" being displayed is
  really N block entities' worth of items merged. This is new plumbing regardless of §3a's outcome
  — even in Option B (storage stays per-segment), the *menu* needs to know how to read/write across
  multiple `FishTankBlockEntity`s network-synced together.
- Reuse `ElectricFishOrganizerScreen`'s existing sort modes (mentioned in project memory) as a
  starting point for organizing a potentially large merged list, rather than inventing a new
  sort/filter UI from scratch.

### 4.3 Cosmetics — same GUI, second section; placement stays right-click, removal moves off raycard-only

- **Placement:** unchanged — right-click with a `FishTankCosmeticItem`/`FishTankStructureCosmeticItem`
  in hand, targeting a floor cell, exactly like today's `placeStructureCosmetic`/single-cosmetic
  path. This already works and isn't part of the reported pain point.
- **Removal:** add a cosmetics section/tab to the same GUI from §4.2, listing every placed
  cosmetic and structure cosmetic across the connected group (single cosmetics keyed by grid cell,
  structures keyed by their anchor — both already have stable identities in
  `FishTankBlockEntity.cosmetics`/`structureCosmetics`). Selecting an entry removes it and returns
  the item, exactly like today's `removeStructureCosmetic`/single-cell removal logic, just
  triggered from a GUI row instead of a raycast hit test against edit-mode state.
- This removes the last real dependency on raycast-precision for removal (today you must aim at
  the exact cell or exact structure-occupied cell) and the last use of edit mode anywhere in the
  codebase, so `FishTankEditModeManager` and `ToggleEditModePacket` can be deleted outright once
  this ships.
- Structure cosmetics remain all-or-nothing to remove (matches today; partial-structure removal is
  out of scope here unless you want to raise it separately).

### 4.4 Sneak+item shift-extract-to-pile path

`PileOfFishItem`/`FishtasticFishItem`'s sneak+right-click "combine into a pile" behavior
(`tryShiftExtractFromTargetedTank`) doesn't depend on edit mode today and isn't part of the
reported problem — proposal is to leave it as-is. Worth a sanity check once the GUI exists: does
"scoop the topmost fish into my held pile without opening a menu" still pull a value worth keeping
as a fast path, or does it become redundant with the GUI's shift-click-to-take? Recommend keeping
it; it's a meaningfully faster gesture for the common case of "grab one more of what's already in
my hand," and removing it isn't necessary to hit any stated goal.

## 5. What gets deleted

- `FishTankEditModeManager` (`common/src/main/java/grill24/fishtastic/block/FishTankEditModeManager.java`)
- `ToggleEditModePacket` and its registration/handler, and whatever client-side keybind/trigger
  currently sends it (not yet located — needs a search before removal).
- The edit-mode branches in `FishTankBlock.useItemOn`/`useWithoutItem` (today's lines ~272–330),
  replaced by the GUI-open call.
- The action-bar "Fish Tank Edit Mode: ON/OFF" messaging.

## 6. Net new work

1. Server-side connected-group resolution (either reused/ported from `TankGroups`' client-only
   flood-fill, or the real Option-A grouping object if that's the direction) — needed by both the
   GUI (§4.2/4.3) and, if Option B, by capacity-aware insertion (§4.1).
2. A new `AbstractContainerMenu` (or `GelatinMenu`, per house style — see `FishTankAssemblyMenu`)
   and `Screen` pair for the fish/cosmetics browser, with slots/rows addressing
   `(segment pos, local index)` rather than a flat single-container slot list.
3. Menu-driven removal paths for fish, single cosmetics, and structure cosmetics, reusing the
   existing extraction/removal methods on `FishTankBlockEntity` (`extractItem`, `removeCosmetic`,
   `removeStructureCosmetic`) rather than rewriting them — only the *trigger* changes, not the
   underlying mutation.
4. Deletion work from §5.
5. If Option A (§3a): capacity/save-format migration for a real shared pool. Deferred unless you
   pick Option A.

## 7. Out of scope (unless you want to fold it in)

- Partial structure-cosmetic removal (remove one block of a multi-block structure rather than the
  whole thing).
- Any change to *placement* mechanics beyond removing edit-mode interference — right-click-to-add
  stays as-is.
- Cross-multi-tank (i.e. across two *disconnected* tank structures) fish management — the GUI scope
  is one connected group, opened from a block in that group.
