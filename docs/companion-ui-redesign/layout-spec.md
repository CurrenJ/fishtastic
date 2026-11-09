# Companions screen: layout spec

Status: built on 26.1.2 (uncommitted, 2026-11-09); see §14 for build status. Owner in-game look pending.
Inputs: the audit and the owner's style decisions S1–S8 in [audit.md](audit.md).
Mockups: [mockups/](mockups/). They are drawn at true logical GUI pixels (the 480x270 reference viewport
the Captain's Log uses) with the vanilla font sheet and the mod's own textures, then upscaled 3x.
Regenerate with
`python -I tools/companion-mockups/render.py <minecraft-client-only.jar> docs/companion-ui-redesign/mockups`.

| Mockup | Shows |
|---|---|
| `01_equipped.png` | Main view: 2 of 3 equipped, one species open, hover states |
| `02_replace.png` | All three cards full: choosing a card to replace; search active |
| `03_rename_and_refusal.png` | Renaming on a card; a locked species refused |
| `04_unlocks.png` | Unlocks view: every species' progress |
| `05_nothing_unlocked.png` | Empty state before the first unlock |
| `06_inventory_tab.png` | Inventory heart tab, unlocked and locked |
| `07_creative_inventory.png` | Heart button in the creative Inventory tab, unlocked and locked |

## 1. Architecture

- The screen becomes a `GelatinUIScreen<GelatinMenu>` registered as Gelatin menu `companions`, like
  `quest_log` and `fish_encyclopedia`. Gelatin UI is already a required dependency.
  `OpenCompanionMenuPacket` keeps its name and payload (the optional species to focus) and now opens that
  menu.
- `CompanionMenu`, its vanilla slots, the carried-cursor flow and `companions.png` are deleted. There is
  no item dragging any more: every action is a packet that describes a fish, and the server finds an
  identical fish itself. This is how bonding and pile takes already work, so no new trust surface is added.

  | Action | Packet | Server |
  |---|---|---|
  | Equip | `BondCompanionPacket(fish, slot)`, renamed in code to `EquipCompanionPacket` (wire id kept) | `Companions.bond` already searches the inventory **and** piles and supports an explicit slot with replacement |
  | Unequip | new `UnequipCompanionPacket(slot)` | `Companions.unequip` (exists) |
  | Make lead | new `MoveCompanionPacket(from, to)` (swap) | new `Companions.swap`, server-validated |
  | Rename | `RenameCompanionPacket` (exists) | `Companions.rename` |

  `TakePileFishPacket` and `CompanionMenu.takeFromPile` go away (equip takes from piles directly).
  Results come back through `CompanionFeedbackPacket` as they do today.
- The client builds the fish list from the player's inventory plus every Pile of Fish's contents. The
  current screen's `pileFish()` already does this. Identical fish are grouped with `CarriedFish.group`.
- Keyboard navigation and narration reuse `UiKeyboardNav` / `Narrated` like the Captain's Log.

## 2. Shell (shared with the Captain's Log)

Reference viewport 480x270. Shell 416x232 at (32,19): a 74 px rail, a 4 px gap, a 338 px content panel,
11 px content insets (content width 316). Colours come from `QuestLogScreen`: shell `#201A24`, rail `#2A202B`,
edge `#72505B`, muted `#C9B8BE`, selection `#FFAA00`, ready `#5DCC7A`, progress `#B86E88`, rail selection
`#714452`, filter selection `#704450`, scrollbar `#352A32` / `#C58A58` / `#FFE0A0`.
If possible, factor the shell into a shared helper instead of copying it (`QuestLogScreen` builds it inline
today).

**Rail.** It shows "FISH" in `#EBDAD1` and "COMPANIONS" in gold at (10,7) and (10,17). Destinations are
20 px tall from y=34 with a 24 px pitch: **Equipped** (heart) and **Unlocks** (star). The selected
destination gets a filled `#714452` background and a 2 px gold bar on the left. A divider sits at
SH-46, followed by "[B] / opens this" (it shows the bound key) and "Esc  close".

**Count box.** It sits top-right at panel y=6..24, like the token box (`#3B2A29`, at least 87 px wide,
growing to fit the text). It holds a heart and gold text: "2 / 3 equipped" on Equipped, "3 of 62 unlocked"
on Unlocks.

## 3. Equipped view (`01`)

Panel-relative y coordinates:

| y | Element |
|---|---|
| 9 | Title "Your companions" (white) |
| 26–86 | Three cards, 100x60 each, 8 px gaps (3x100 + 2x8 = 316) |
| 92 | Formation line |
| 102 | 1 px divider (edge colour) |
| 106–120 | Toolbar |
| 124–212 | Fish list viewport (scrolls; 2 px scrollbar inside the right edge) |
| 215 | 1 px divider |
| 219 | Footer line |

### 3.1 Cards

- Each card is a `generic_item_panel` nine-slice (4 px border). Card 1 uses the **gold-border** variant
  plus a crown and "LEAD" in gold at (5,4). Gold means the lead and nothing else here, so the shared
  meaning of bronze/silver/gold for difficulty isn't stretched.
- The fish is the **live animated companion model** (owner decision 2026-11-09), drawn into a 32x32 box
  at (34,7) with the companion's idle swim. It is clipped to the card and never changes the card's size.
  Cost: one model render per filled card per frame, three at most; the owner judged it minimal, so it is
  not measured.
- The name sits centred at y=40 with a pencil button right after it (S6). Clicking the name **or** the
  pencil opens an inline field on that card (`03`): 88x12, a gold edge, the old name preselected.
  Enter saves, Esc cancels and only closes the field, and clicking outside saves. A changed fish closes
  the field without saving, as today. The max length is `RenameCompanionPacket.MAX_NAME_LENGTH`.
- Line 2 at y=50 shows the size in muted text and the quality word in its quality colour, lightened for
  contrast on mauve (Rare `#8888FF` instead of `#5555FF`).
- On hover, two 10x10 buttons appear: **×** at the top right unequips; on cards 2 and 3, a **crown** at
  the top left makes that fish the lead (it swaps with card 1). Both have tooltips ("Unequip",
  "Make lead companion"). Unequip with a full inventory is refused through the existing `INVENTORY_FULL`
  feedback.
- An empty card shows a dashed edge-colour frame, a large "+" and "Equip fish / pick one below". It isn't
  a button, because the list below is where fish are picked. An empty card 1 still shows "LEAD".
- The tooltip on a filled card shows the item tooltip (size, quality, caught date and so on).

### 3.2 Formation line

A heart followed by the current group summary from `CompanionGroups` in muted text, for example
"Bluegill pair + Starfish". The trailing "?" opens the groups tooltip ("Fish group together by species and
swimming style."). No "equip N more for a school" hint (owner decision 2026-11-09: not needed).

### 3.3 Toolbar

- Search box at 112x14: `#15101A` fill, edge outline, a lens icon, placeholder "Search fish". It matches
  species display names, case-insensitive, live.
- Filter pills in the quest-log style: **All** / **Unlocked** (selected = `#704450` fill, a 2 px gold
  underline and gold text). The filter is remembered for the session.
- Right-aligned summary in muted text: "1,284 fish" (eligible fish carried, inventory and piles
  combined), or "2 species" while a search is active.

### 3.4 Fish list (S3: species first, built for huge piles)

**Species row (18 px).** A chevron, the 16 px icon (greyed if locked), the name, "x412" in muted text,
and a state on the right:

- unlocked: a heart and "Unlocked" in green.
- catch-gated: "Catch N more" in progress mauve, over a 2 px bar showing progress to the threshold.
- quest-gated: a lock and "Unlocks via a quest" in muted text.

Order: unlocked species alphabetically, then locked species closest-first.
**Only one species is open at a time** (accordion), so the list stays short however many species a
player carries. The `species` in `OpenCompanionMenuPacket` (from the Fishopedia or hold-B with all cards
full) opens that species' row and scrolls to it.

**Fish row (15 px, indented 13 px with a tree line).** A quality star and word in the quality colour, the
size, and where it is ("Pile" with an 8 px pile icon, or "Inventory"). Identical fish (every component
the same) collapse into one row with "x3". On the right, an **Equip** button (36x13, `#714452`, hover
`#7D4E5B`), or a heart and "Equipped" for a fish that is already equipped. Rows are sorted by quality,
then size, highest first. The first 8 rows show, then a gold "Show N more" row reveals 16 at a time. A
species with 400 fish in piles costs one row until it is opened, and at most 9 rows after opening.

**Locked species** can still be opened. Their Equip buttons are dimmed (`#3A2B37`, muted text) but still
answer a click with a refusal (§3.6), so the player learns what's missing.

**Equip flow.**
1. With an empty card, the fish goes to the **first empty card**. The new card plays a brief gold
   outline pulse and the click sound, and the row changes to "Equipped".
2. With all three full, the screen enters **replace mode** (`02`). All cards get a gold outline and a
   dim wash; the hovered card shows a gold "Replace" plate; the chosen fish row stays outlined in gold;
   the footer says "All 3 cards are full: choose one to replace." with "Esc  cancel". Clicking a card
   sends `EquipCompanionPacket(fish, slot)`, and the old fish goes back to the inventory, or drops if the
   inventory is full (existing server rule). Esc, clicking the list or clicking the same row again
   cancels.

### 3.5 Footer

One line for hints and feedback, muted by default. A refusal shows it in `#FF6B6B` for 5 s (100 ticks, as
today) with an optional right-aligned action link. In replace mode it shows the prompt in gold. Idle hint:
"Click Equip to add a fish to the first empty card."

### 3.6 Refusals (GUI failure feedback rule)

A refused equip flashes the fish row **and** the card it was aimed at red, using two pulses over 600 ms
(today's `flash()` curve and `#E02424`). It also plays the bass note, a player action so sound is allowed,
and the footer names the next step with a link:

| Case | Footer | Link |
|---|---|---|
| Catch-gated | "Catch 12 more Arctic Char to unlock it." | "See Unlocks" |
| Quest-gated | "Electric Eel unlocks through its quest." | "Open quests" |
| Fish gone (dropped while open) | "You aren't carrying that fish anymore." | — |
| Inventory full on unequip/replace | "Make room in your inventory first." | — |

## 4. Unlocks view (`04`)

- Title "Unlocks", count box "3 of 62 unlocked". Muted copy: "Complete a species' Fishopedia entry to
  unlock it." / "Closest first."
- An 8-column grid of 34x34 tiles with 5 px gaps and an 18 px row pitch below each tile for the bar. It
  scrolls if needed.
  - Unlocked: a gold-border panel, the full-colour fish and a heart in the corner.
  - In progress: a bronze panel, a desaturated fish and a 2 px progress bar under the tile.
  - Uncaught: a pure silhouette, with "???" in its tooltip so no names leak (same rule as the quest log).
  - Quest-gated: a lock under the tile.
- Order: unlocked, then by progress fraction from highest, then uncaught.
- Tooltip: the name (or ???), "Catch N more to unlock as a companion." and "Click: open in Fishopedia".
  Clicking a discovered species opens its Fishopedia page.

## 5. Nothing unlocked (`05`)

Three empty cards, then a large dim heart below the divider, "No companions unlocked yet", and
"Complete a species' Fishopedia entry / to bring that fish along with you." A **See Unlocks** button
(100x16) follows, then "Closest: Betta, 4 more catches". The fish list is hidden until something is
unlocked.

## 6. Inventory heart tab (`06`, S7–S8)

- 22x17 at (leftPos + 150, topPos − 14). It overlaps the panel's top edge by 3 px, and its lower 3 px use
  the panel's `#C6C6C6` so it reads as attached. Body `#201A24` (hover `#2E2430`), edge `#72505B` on the
  top and sides, and a 7x6 heart centred: pink when unlocked, `#6E5E66` with a lock mark when locked.
  It follows `leftPos`, so it moves with the recipe book.
- Always shown in the survival inventory. Tooltip "Companions  [B]" plus "2 / 3 equipped"; when locked,
  "Complete a Fishopedia entry / to unlock your first." and "Closest: Betta, 4 more". Clicking opens the
  screen: on Equipped normally, on the empty state (§5) when locked. Click sound.
- **Creative inventory (`07`).** The creative top and bottom edges belong to the creative tabs (seven per
  row, plus page arrows when other mods add tabs), so nothing goes there. Instead, while the **Inventory**
  tab is selected, an 18x18 button sits inside the panel at (leftPos + 150, topPos + 19). That is level
  with the offhand slot (x=34) and mirrors it on the far side of the armour column, in the empty area of
  the panel. It looks like the survival tab: a `#201A24` fill and `#72505B` edge (gold edge and `#2E2430`
  on hover), with a pink heart, or a grey heart and lock when locked. The plum fill keeps it from reading
  as an item slot. It shows the same tooltip and does the same thing on click. It isn't shown on the
  other creative tabs. Both placements share the `showInventoryTab` config.
- Risk: other mods (inventory-tab mods, some accessory mods) also use the top edge. Add client config
  `showInventoryTab` (default on) and test beside the common ones in the release smoke matrix.

## 7. Keys and hold-to-equip

- The key is renamed to "Companions" (default B). It opens the screen from no screen, and pressing it
  while the screen is open closes it.
- Hold [B] over a fish to equip it (0.8 s, the existing `CompanionBondInput`). The tooltip line becomes
  "♥ Hold [B] to equip as companion"; when all cards are full, "♥ Hold [B] to choose which companion to
  replace". That opens the screen focused on the species, ready for replace mode with that fish chosen.
- Fix D5: allow hold-to-equip over **player-inventory slots** in any container screen, including the
  creative inventory's survival tab, not just `InventoryScreen`. Fish in a chest stay ineligible, because
  the server only equips carried fish.

## 8. Tour

The navy/teal side panel is removed. Tour steps become callouts in the house style: a `#201A24` box with
a 1 px gold edge and a 4 px pointer, attached to the element they explain and placed inside the shell so
nothing goes off screen. **Next** and **Skip tour** buttons sit in the callout. Steps: equip (points at
the open species' Equip) → unequip (card ×) → lead (crown) → rename (pencil) → groups (formation line).
The pile step goes away because piles are just part of the list.

## 9. Copy (S5 glossary applied)

New and changed keys, all under `screen.fishtastic.companion.*` unless noted. Feedback keys move out of
`screen.fishtastic.encyclopedia.companions.feedback.*` into `screen.fishtastic.companion.feedback.*`.

| Key | Text |
|---|---|
| `key.fishtastic.companions` (was `bond_companion`, same default) | Companions |
| `title` | Fish Companions |
| `rail.equipped` / `rail.unlocks` | Equipped / Unlocks |
| `your_companions` | Your companions |
| `count.equipped` | %s / 3 equipped |
| `count.unlocked` | %s of %s unlocked |
| `lead` | LEAD |
| `make_lead` | Make lead companion |
| `unequip` | Unequip |
| `equip` / `equipped` | Equip / Equipped |
| `empty_card` / `empty_card_hint` | Equip fish / pick one below |
| `search` | Search fish |
| `filter.all` / `filter.unlocked` | All / Unlocked |
| `state.unlocked` | Unlocked |
| `state.catch_more` | Catch %s more |
| `state.quest` | Unlocks via a quest |
| `show_more` | Show %s more |
| `hint.idle` | Click Equip to add a fish to the first empty card. |
| `hint.replace` | All 3 cards are full: choose one to replace. |
| `feedback.catch_more` | Catch %s more %s to unlock it. |
| `feedback.quest` | %s unlocks through its quest. |
| `link.unlocks` / `link.quests` | See Unlocks / Open quests |
| `hold` | ♥ Hold [%s] to equip as companion |
| `hold_full` | ♥ Hold [%s] to choose which companion to replace |
| `none.title` / `none.body` | No companions unlocked yet / Complete a species' Fishopedia entry to bring that fish along with you. |
| `closest` | Closest: %s, %s more catches |
| `tab.locked` | Complete a Fishopedia entry to unlock your first. |
| Fishopedia strip button (`screen.fishtastic.encyclopedia.bond`) | Companions |
| Fishopedia strip states | "Companion unlocked" / "♥ Your companion" / "♥ Your lead companion" / "Catch %s more to unlock as a companion" |
| FAQ `tutorial.fishtastic.faq.companion.*` | Rewritten with equip/unequip wording, mentioning the inventory tab |
| Unlock chat line `tutorial.fishtastic.companion.discovery` | "New companion unlocked: %s. Open Companions with [%s] or the ♥ tab in your inventory." |

Delete these keys: orphaned `intro.*`, `open_book`, `open_party`, `choose_fish`, `companion_intro.*`,
`choose`, `manage`, `piles`, `pile_hint`, `click_pile`, `choose_pile`, `rename_empty`, `not_bonded*`.

## 10. Small viewports

At 426x240, reduce the outer margins as the Captain's Log does. Below that, use the Log's bounded
scrolling fallback. Cards may shrink to 92 px wide; the fish render stays 32 px, and the quality word
moves into the tooltip if the line overflows. Never scale the bitmap font. The render self-test should
capture `01` and `03` at 480x270, 426x240 and 320x180, like `questbook-cleanup_*`.

## 11. Tests and self-test hooks

- Server GameTests: equip from a pile into an explicit slot with replacement, swap, unequip with a full
  inventory, and a locked species refused. Most of this exists for `Companions.bond`; add swap.
- Replace the hooks `CompanionScenes` uses (`openEditor`, `editingSlot`, `showPiles`, `clickPile`,
  `isFlashing`, `clickSlot`) with `openSpecies`, `clickEquip(row)`, `chooseCard(i)`, `startRename(i)`,
  `isFlashing(target)`. Add a `companionui` scene that captures every mockup state.
- An in-game debug trigger (solo playtest rule): `/fishtastic debug companion_ui <state>` opens the screen
  in a given state (`equipped`, `replace`, `none`, `unlocks`).

## 12. Owner decisions on the open questions (2026-11-09)

1. Cards use the **live animated companion model** (§3.1).
2. **No** formation hint (§3.2).
3. Creative inventory: **yes**, as an in-panel button on the Inventory tab (§6, `07`).

## 13. Build order

1. Server: `Companions.swap`, `UnequipCompanionPacket`, `MoveCompanionPacket`, equip-packet rename,
   GameTests.
2. Shell helper, then the Equipped view without the list (cards, rename, lead, unequip).
3. Fish list (species rows, accordion, fish rows, search and filter, replace mode, refusals).
4. Unlocks view, empty state.
5. Inventory tab, creative Inventory-tab button and config, hold-to-equip in more screens, key rename.
6. Copy pass (§9) across lang, FAQ and the wiki; tour callouts.
7. Self-test scene plus small viewports, then the owner's in-game look.
8. Port to 1.21.1 / 1.20.1 (the GUI render API differs per branch).

## 14. Build status (2026-11-09, branch 26.1.2, uncommitted)

Steps 1–6 are built and step 7 is partly done. Verified on all three loaders: GameTests Fabric 420/420,
NeoForge 414/414, Forge 411/411, plus the `companionbond` (12 checks) and `companiontutorial` (6 checks)
self-test scenes, which photograph every state. The owner's in-game look is still pending.

Code map:

- `client/companions/CompanionsScreen`: the Gelatin screen. One immediate-mode canvas in the spec's
  logical pixels; clicks and tooltips resolve against the rectangles drawn that frame.
- `client/companions/CompanionFishCatalog`: species-first grouping, sorting and unlock state.
- `client/companions/CompanionPortraitPip` + `CompanionPortrait`: the live card fish through a
  picture-in-picture renderer (Fabric `PictureInPictureRendererRegistry`, NeoForge and Forge register
  events), with a bobbing-item fallback.
- `client/companions/CompanionInventoryButton`: the survival tab and the creative Inventory-tab button.
  `showInventoryTab` lives in the client config.
- Server: `Companions.swap` / `unequipToInventory` / `syncInventory`; `UnequipCompanionPacket` (reuses the
  old, never-registered `unequip_companion` id); `MoveCompanionPacket`; `EquipCompanionPacket` (wire id
  `bond_companion`).
- Removed: `CompanionMenu`, the old `CompanionScreen`, `TakePileFishPacket`, the `companion` menu type,
  `companions.png`, `CompanionMenuGameTests` (18 tests), and 52 dead lang keys.

Decisions made during the build, beyond the spec:

- **Key id kept:** `key.fishtastic.bond_companion`; only its label became "Companions", so players keep
  their custom bindings.
- **Fishopedia button label:** "Equip", not "Companions" (§9). The button is 49 px and "Companions" doesn't
  fit; the button opens the screen with that species' row open.
- **Tour:** the lesson had already shrunk to one step (EQUIP → COMPLETE), so §8 is one callout. It is
  house-styled, sits over the empty cards 2–3 (the lesson only runs with nothing equipped), points down
  at the list and has Skip tour.
- **Inventory sync:** the Gelatin menu has no slots, and the server only broadcasts the open menu, so
  equip/unequip call `inventoryMenu.broadcastChanges()`. Without it the list offered a fish that had
  already been equipped.
- **JEI:** the screen is registered as a full-screen GUI so JEI's item list doesn't draw over it.

Pre-existing bugs found and fixed along the way:

- **Hold-to-bond in the survival inventory has never run on 26.1.2.** `InventoryScreen` (a recipe-book
  screen) never calls `AbstractContainerScreen.extractRenderState`, where the hook was. The hooks now sit
  in `extractContents`, and hold-to-equip works over the player's own slots in any container screen (§7).
- **Forge had no Pause › Fishtastic help button.** `PauseScreenMixin` was missing from
  `fishtastic-common-forge.mixins.json` (left out by f5b20a82).
- **The `companiontutorial` scene left the Companions key unbound in every dev run's options.txt.** It
  now restores the key, and the three options files were repaired.

Still open: small-viewport checks (§10, not captured yet), the wiki copy, the `/fishtastic debug
companion_ui` trigger (§11; `companionbond` drives the same states unattended), keyboard navigation and
narration, the owner's look, and the 1.21.1 / 1.20.1 ports (§13.8).
