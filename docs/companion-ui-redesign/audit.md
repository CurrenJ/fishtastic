# Companions / bonding UX audit

Status: audit written 2026-11-09 from code on `26.1.2` (72aa6b5e). Not yet checked in a live game.
Style decisions are recorded in §5 as the owner answers them.

## 1. What exists today

| Piece | Where | Notes |
|---|---|---|
| Party screen | `client/CompanionScreen.java`, `menu/CompanionMenu.java`, `textures/gui/companions.png` | Vanilla `AbstractContainerScreen`, 176x202, grey vanilla-style panel, 3 party slots above the 36-slot player inventory |
| Hold-to-bond | `client/CompanionBondInput.java`, `mixin/AbstractContainerScreenMixin.java` | Hover an eligible fish in the survival inventory and hold [B] for 0.8 s; a full party opens the party screen instead |
| Keybind | `FishtasticKeyBinds.bondCompanion`, default B, "Bond / manage companions" | Pressing B with no screen open opens the party screen |
| Fishopedia | `EncyclopediaPageLayout.bondStrip` | 18 px strip at the bottom of the left page of each species; a 49x14 "Bond" button appears only once that species is unlocked |
| Pause menu | `PauseScreenMixin` → `TutorialHelpScreen` → "Companion help" | Opens the party screen and replays the tour |
| Unlock notice | `CompanionTutorialManager.onBondUnlocked` | One chat line: "Manage companions from Pause > Fishtastic help." |
| Tour panel | `CompanionScreen.extractTutorialGuide` | Navy `#101820` box with a teal `#44CC88` outline beside the screen |

## 2. Visual style findings

- **V1. The screen uses a different visual language from the rest of the mod.** The Captain's Log and the
  Fishopedia share one palette: a dark plum shell `#201A24`, warm mauve cards, gold `#FFAA00` selection,
  green `#5DCC7A` ready, muted text `#C9B8BE`, bronze/silver/gold `generic_item_panel` nine-slices, a left
  rail and white text. The companion screen is a light grey vanilla container with `#404040` text, vanilla
  `Button`s, a hand-drawn 12x8 pencil and a 5x4 heart. A player meets it as though it came from another mod.
- **V2. There is a third palette.** The tour side panel is navy and teal, which matches neither the vanilla
  grey nor the house mauve.
- **V3. The companions themselves barely show.** Three 16 px item icons float in a large grey field. The
  only information about the party is one line of plain text ("Bluegill school + Starfish"). There is no
  portrait, no name under each slot (names only appear in item tooltips) and no picture of the formation,
  even though the formation is the feature's main payoff.
- **V4. Small hit targets do important jobs.** Renaming sits behind a 12x8 pencil tab. Taking a fish out of
  a pile sits behind a 48x12 "Piles" toggle that swaps the status area for a strip with pages.
- **V5. Status text is crowded.** Feedback, its hint, the pile prompt and the name box all share rows
  78–107, so each mode pushes the others out (`extractBackground`, status block).

## 3. Discoverability findings

- **D1. The only obvious ways in are a keybind and a pause-menu help page.** Pressing B is never taught
  until a player has an unlocked bond, and Pause > Fishtastic help > Companion help is a help screen, not
  somewhere players go to manage things.
- **D2. The unlock moment is one chat line, and it points to the least natural route.** Unlocking a bond
  is the end of a long goal (a complete Fishopedia entry, i.e. the highest reward threshold), yet the
  reward is a system message that scrolls away. It doesn't mention [B] or the Fishopedia button.
- **D3. Neither main book links to it.** The Captain's Log rail has no Companions destination. The Fishopedia
  has no Companions view. Its only link is the small "Bond" button at the bottom of a single species page,
  and that button only appears once that species is unlocked.
- **D4. Nothing on the inventory screen shows it.** The "♥ Hold [B] to bond" line only appears in the
  tooltip of a fish that is already eligible. A player with no eligible fish never sees it.
- **D5. Hold-to-bond only works in the survival inventory.** `CompanionBondInput.tick` and the tooltip mixin
  both check for `InventoryScreen`. The creative inventory and chests or barrels holding fish never show
  the hint and never bond.
- **D6. Before unlocking, players aren't told the feature exists.** The only hint is the Fishopedia bond
  strip ("Catch N more to unlock bonding"), at the bottom of a page players open to read about catching.

## 4. Flow and copy findings

- **C1. One word names two different actions.** The Fishopedia "Bond" button only opens the management
  screen and bonds nothing. "Hold [B] to bond" in the inventory actually equips the fish. The key's name
  ("Bond / manage companions") admits that it does both.
- **C2. Some tour copy describes UI that no longer exists.** `tutorial.fishtastic.companion.intro.body`,
  `.open_party` and `.choose_fish` tell players to "select Choose fish", but the button now says "Bond".
  These keys, along with `.open_book`, `companion_intro.*`, `screen.fishtastic.companion.choose` and
  `.manage`, have no Java reference left (checked with grep; a dynamically built key could still use one).
- **C3. The book has two names.** The tour says "Fishopedia" but the refusal hint says "See encyclopedia."
  and the locked tooltip says "Complete this species entry".
- **C4. There are two ways to equip and they work differently.** Hold-B fills the first empty slot from the
  inventory. The screen uses vanilla drag and shift-click, plus a separate path for piles. Each path explains
  itself only, through tooltips (the GUI-feedback rule already notes that players miss tooltips).
- **C5. Fish in piles are second class.** They are greyed out, sit behind a toggle and have pages. Yet the
  Pile of Fish is where much of a player's catch actually ends up.
- **C6. Some lang keys sit in an old namespace.** Feedback lives under
  `screen.fishtastic.encyclopedia.companions.feedback.*`. This is harmless but worth folding in during a
  rewrite.

## 5. Style decisions (owner)

Round 1 (2026-11-09):

- **S1. Home:** a separate Companions screen in the Captain's Log shell (plum frame, left rail, mauve cards).
  The vanilla container goes away.
- **S2. Party slots:** portrait cards. Each card shows the fish large and animated, its name, and a lead
  marker on card 1. An empty card shows "+ Equip fish" (was "+ Add fish" before S5 was revised).
- **S3. Fish picker:** a filtered list of eligible fish from the inventory and every pile, merged, with
  locked species sorted to the end. The owner noted that piles can hold huge numbers of fish, so the list
  must stay easy to navigate at that scale. The owner suggested species-first: one row per species, which
  expands on click to show the individual fish (they left the final design to Claude). The Piles toggle goes.
- **S4. Entry points:** the B key plus a button on the survival inventory screen. The Fishopedia link, a
  Captain's Log link and an unlock toast were not chosen.

Round 2 (2026-11-09):

- **S5. Wording:** the player-facing noun is "companion" alone, and the verbs are equip / unequip.
  "Bond" and "party" disappear from player-facing text. (Revised 2026-11-09: the owner swapped
  "Add as companion" / "Return to inventory" for equip / unequip because those read clearer.)

  | Concept | Text |
  |---|---|
  | Species qualifies | "Companion unlocked" |
  | Not yet | "Catch N more to unlock as a companion" |
  | Put a fish in a slot | "Equip" / "Hold [B] to equip as companion"; empty card "+ Equip fish" |
  | Take it out | "Unequip" (the fish goes back to the inventory) |
  | The three slots | "Your companions" (n / 3) |
  | Slot 1 | "Lead companion" |
  | Key name | "Companions" |
  | Fishopedia strip button | "Companions" (or "♥ Your companion" when that species is one) |

  Formation words (school, pair, shoal, line, crowd) stay. This also fixes C1–C3 and removes the orphaned
  keys.
- **S6. Rename:** each card has a pencil button, and clicking the name also starts editing. Enter saves,
  Esc cancels.
- **S7. Inventory button:** a heart tab on the top edge of the inventory panel.
- **S8. Button visibility:** always shown. Before the first unlock it shows a locked state: the tooltip
  explains how to unlock (complete a Fishopedia entry) and clicking opens the screen in a locked or
  progress state.

Defaults chosen by Claude (owner can override):

- The fish list is species-first. Each row shows icon, name, count and a "Companion unlocked" or
  "Catch N more" state, with locked species last. Clicking a row expands its individual fish, sorted by
  quality then size, each labelled with quality and cm. Clicking one adds it to the first empty card; with
  all cards full, the player picks a card to replace. A search box and an "unlocked only" filter sit above
  the list.
- The tour panel and refusal feedback move to the house palette; the navy/teal panel goes.

## 6. Constraints any redesign keeps

- Server-authoritative moves: `CompanionMenu` slot rules, `CompanionBondGate`, and pile takes described by
  the client but resolved by the server.
- Refusals flash, play a sound and name the next step (GUI failure feedback rule). Sound only on
  player actions.
- Self-test hooks used by `CompanionScenes` / `RenderSelfTest`: `openEditor`, `editingSlot`, `showPiles`,
  `clickPile`, `isFlashing`, `clickSlot`.
- Ship an in-game debug trigger, and port to 1.21.1 / 1.20.1 afterwards (the render API differs per branch).
