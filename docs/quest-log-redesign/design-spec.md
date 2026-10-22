# Quest book redesign — interaction and visual specification

Status: implemented and reviewed in game on 20 October 2026. This is the interaction and visual
contract; see [the implementation review](review/README.md) for validation limits. Current code is
authoritative if it differs.

## Scope and player jobs

Players must be able to find a ready reward, choose a feasible fishing objective, inspect
conditions or collection evidence, spend tokens and understand the shared cleanup goal.
Keep the existing economy, quest unlocks, tutorial reward, discovery protection and server
validation. Track and HUD pinning are outside this revision.

## Navigation and scope

| Destination | Content | Default ordering |
|---|---|---|
| Today | Active daily rotation plus the unclaimed First Catch tutorial on first run | Ready first, then in progress, then not started, then claimed |
| Goals | Mastery / lifetime goals | Ready first, then highest proportional progress, then not started, then claimed |
| Explore | Explorer quests | Same state order, then proportional progress |
| Collect | Distinct-species and other collector quests | Same state order, then proportional progress |
| Trials | Challenge quests | Same state order, then proportional progress |
| Market | Current purchasable stock with remaining quantity | Available, then unaffordable; omit sold-out entries and pack the grid |
| Cleanup | Current server-wide trash cycle | Fixed summary and personal contribution, then contributors |

Use existing hidden/prerequisite rules before counting, sorting or rendering. Do not expose
locked names, icons, rewards or readiness through badges. Stable tie-breaks are difficulty,
reward and display name, then quest ID. Never sort by a guessed “fastest” or “actionable” score.

- All includes visible claimed quests. In progress includes unclaimed, incomplete quests,
  including zero progress. Ready includes complete but unclaimed quests in this destination.
- The Ready filter includes the count (`Ready (2)`). Zero is an enabled empty-state destination.
- The rail shows the unclaimed-ready count for each quest destination; zero counts are omitted.
  In this constrained mockup the count replaces the decorative icon. Tooltip and narration say,
  for example, “Today, 2 rewards ready.” Counts are not a global Ready filter.
- Keep independent filter, expanded quest ID and scroll anchor per destination for the current
  screen session. Reopening the book starts at Today.
- First Catch is a first-run exception in Today and does not reset. Its detail explicitly
  identifies it as Tutorial / no reset. The Today timer describes daily quests only.

## Shell and layout

The reference viewport is 480×270 logical pixels, rendered at integer GUI scale 2 (960×540).
The centred shell is 416×232: 74-pixel rail, 4-pixel gap and 338-pixel content panel. At this
reference size the origin is (32,19). The content inset is 11 pixels per side. The list starts
48 pixels below the panel top and has 180 pixels of viewport height.

The 36-pixel row is a baseline, not a fixed maximum. Five baseline rows fit that viewport.
Adjacent rows use their panel borders as separators; do not add another heavy divider.
Long translated text wraps and grows the row without shrinking the bitmap font. Use the same
layout model for title, essential objective and reward variants. Do not force five rows to fit
by truncating conditions. Scroll content within the panel, not the entire shell. Preserve the original rework scrollbar
palette: recessed mauve track #352A32 with a #72505B edge, warm gold thumb #C58A58 and a
pale gold #FFE0A0 highlight edge. The thumb remains proportional to content; omit it when the
list fits. Do not substitute the muted text color for the thumb.

The 416×232 shell can fit a 426×240 logical viewport with reduced outer margins. At smaller
sizes, use a bounded scrollable fallback and preserve native font scale. Exact small-viewport
behavior must be prototyped before implementation sign-off; the supplied PNGs only validate
480×270. Keep all text pixel aligned; do not stretch rendered text fractionally.

Header: destination title at left, token balance at a stable top-right location; category
filters beneath. The token container occupies panel-relative y=6 through y=24 (18 pixels high),
with its 16-pixel coin inset one pixel and its text inset five pixels from the container top.
The filter/reset row begins at y=28, leaving four clear logical pixels below the container.
Keep both the coin artwork and container out of that lower row; do not extend the balance
background behind the reset timer. Filter labels and reset text share the y=32 baseline. Today puts its daily reset timer beside filters. Remove explanatory
subtitles such as “Seven quests, one quick scan.” The label and content explain the screen.

## Row contract

```text
| tiered panel / state | icon | title              [Claim 15 + coin] [>]
|              Catch any fish
|              progress bar   1 / 1   [overlapping item sprites]
```

- Show a verb, target quantity or distinct-species unit, and essential qualifying condition.
  “Catch 2 large fish” and “Discover 15 river species” are acceptable. “Large fish” and “River
  species” alone are not. Do not clip authored prose to generate an objective summary.
- Generate localized summaries from objective fields. If no safe template exists, show the
  full localized objective and expand the row height. Do not hide required conditions solely
  behind hover or disclosure. Detailed timing/location explanations may appear in expansion.
- Align the progress bar and exact current/target below the objective. A zero count has an empty
  bar, never a decorative filled pixel. Repeated catches do not imply distinct discoveries.
- Count targets and collection membership come from actual objective/tag resolution, including
  exclusions. Never hard-code illustrative species totals into the screen.
- Show the actual item reward previews in the gap after the objective/progress cluster and
  before the token/Claim column. Replace the former `+N items` text with up to three 16×16
  sprites, offset horizontally by 10 pixels (6 pixels of overlap). Draw left to right so the
  rightmost sprite is in front; retain authored reward order and do not merge component variants.
- At reference width every item stack starts 164 pixels into the row; vertically center its 16-pixel
  sprites with `stackY = headerY + floor((headerHeight - 16) / 2)`. A 36-pixel header uses a
  10-pixel top inset. Three sprites occupy 36 pixels horizontally. Reserve six pixels between its left edge and the objective column, and keep
  a gap before Claim. With items, titles and objectives wrap within 126 pixels and the progress count
  has a separate 44-pixel slot starting at x=114. The progress bar starts at x=32 and is
  76 pixels wide, shared across all rows. Grow the row for wrapped copy and recalculate the preview's
  vertical position from the header height. Keep the +N indicator vertically centred on the same sprites. Never paint
  a preview over text, the tiered frame, Claim or disclosure.
- Show a single reward as one sprite. For more than three reward entries, retain three sprites
  and place a small `+N` indicator at the right edge of the last visible sprite, with a slight
  two-pixel overlap and vertical alignment to the sprite centre. Keep the remaining item art
  visible; do not detach the indicator under the Claim/token column. Reserve overflow space in
  every row's fixed reward column, including rows without overflow. Never shift the stack based
  on item count: one item, three items and three items plus +N share the same first-sprite x.
  Wrap text or adapt the common column layout for larger content rather than shifting one row.
  This counts hidden entries, not stack quantities. Reduce the number of previews only if the available viewport
  cannot preserve readable text and controls; expose the omitted count. Do not shrink the font.
- The preview group's hover/focus tooltip lists every reward's name, quantity and relevant
  components, including omitted entries. A hit on an overlapping sprite uses the topmost sprite;
  the group summary must still expose the obscured entries. Preview inspection never claims or
  toggles the row. Keyboard users can inspect the group without precisely targeting a sliver.
- Omit quantity numerals on the overlapping collapsed sprites to avoid unreadable collisions.
  Expanded rewards retain full names and quantities. Item-only and token-only rewards must also
  work; omit empty token elements. Runtime previews render actual component-bearing ItemStacks.
  Static mockups also use actual in-game GUI ItemStack captures keyed by item ID and complete
  component data. Never use the fish tank assembly GUI texture as a fish tank item sprite.
  Captures, their component manifest and reproducible export tooling live in this design package.
- In unfinished quest headers, vertically center both the 16-pixel coin slot and 8-pixel
  reward amount on the header centre: `coinY = headerY + floor((headerHeight - 16) / 2)`
  and `amountY = headerY + floor((headerHeight - 8) / 2)`. Recalculate for wrapped headers;
  expanded content does not affect their alignment. Use the same header centre as Claim.
- Claim appears only for ready rows. Vertically center its 19-pixel button within the row
  header: `buttonY = headerY + floor((headerHeight - buttonHeight) / 2)`. A 36-pixel header
  therefore uses an 8-pixel top inset. Recalculate against the header height when copy wraps;
  opening the accordion keeps Claim centered in the header, not the complete expanded card.
  Disclosure remains separately visible for every inspectable row, including ready and claimed
  rows. Vertically center its 8-pixel glyph within the same header using
  `arrowY = headerY + floor((headerHeight - 8) / 2)`; this applies to both expand and collapse
  arrows and adapts when text wraps. Clicking Claim must not toggle expansion.
- Full row space outside explicit controls toggles expansion. Reward tooltips do not claim or
  toggle. Hover/focus must visibly identify the exact target.

## Visual state grammar

| Meaning | Treatment |
|---|---|
| Active destination/filter | Gold edge/underline plus filled selection surface and readable label |
| Token currency | Existing gold coin ladder and gold amount |
| Quest identity | White title on the original warm mauve panel texture |
| Difficulty | Shipped bronze/default, silver-border and gold-border panel sprites; full tier names in tooltip and narration; no B/S/G badges |
| Ready | Narrow green state strip, full green progress and established green Claim control |
| Incomplete | Mauve progress; neutral row and white disclosure |
| Claimed | Explicit Claimed text, subdued progress; still inspectable |
| Keyboard focus / row selection | Separate high-contrast outline outside the tiered frame, independent of difficulty and readiness; never replace the tier sprite |
| Uncaught eligible species | Actual fish silhouette, matching current collection quests; generic ??? tooltip/narration until caught. Hidden unlisted species remain omitted under existing visibility rules |

Gold and silver row borders encode difficulty using the existing panel sprites. Keep titles
white; remove the B/S/G letter badges. Expanded details inherit their quest's tier-specific
panel sprite and keep the original warm mauve interior even
when claimable; do not apply an ink-colored darkening wash to quest cards or their details. Readiness is also communicated by Claim text and a full count, not color alone.
Muted copy uses #C9B8BE; shell interiors use #201A24 while card interiors retain their
original texture colors; white is the primary text color. Main
selection is #FFAA00; ready is #5DCC7A; progress is #B86E88; timer is #8CCBFF. Preserve the
existing coin and button art. Hover/focus and narration still need runtime contrast checks.

Tier sprite mapping (all 9-sliced at the existing four-pixel border):

- Bronze: `textures/gui/generic_item_panel.png`.
- Silver: `textures/gui/generic_item_panel_silver_border.png`.
- Gold: `textures/gui/generic_item_panel_gold_border.png`.

Preserve the tier frame on ready and claimed quests; Claim text, progress and the state strip
carry readiness. Do not turn the whole quest green or substitute a focus sprite for its tier.
The separate focus outline must remain visible around all three frame treatments. Hover may
highlight the disclosure or Claim target without changing the panel tier.

## Shared reward display

Use one reward presentation component across Daily, Goals, Explore, Collect, Trials and the
first-run tutorial. Category changes the objective evidence, never the reward layout.

- Collapsed and expanded row headers share the same token/Claim column and overlapping item
  preview group. Keep the same icon sizes, spacing, order, quantities policy and overflow rule.
- Every expanded quest uses the same Rewards block after its objective/evidence: white Rewards
  heading, coin icon plus gold token amount in the left column, and actual item icons with white
  names and exact stack quantities in a vertical right column. Items retain authored order.
- The reference block places its coin at x+12 / y+12, amount at x+32 / y+16, and item column at
  x+135 / y+11 with 15-pixel row pitch. Item names start at x+151. The block grows for more items
  or translated names. Heading-relative geometry is identical across quest categories.
- Keep collection evidence above Rewards. Do not replace the shared block with a prose summary
  such as “Reward: 45 tokens + tank x8,” or place navigation in its token/item columns.
- Ready/in-progress/claimed states do not change reward composition or alignment. Claim remains
  the separate header action. Component-sensitive hover/focus details remain available on items.
- Omit absent token/item elements without changing the remaining column anchors. Market prices
  and Cleanup's automatic payout explanation remain transaction information for those pages;
  they are not a category-specific substitute for a quest reward block.

## Expansion and evidence

Only one row is expanded at a time per destination. Open another row by quest ID, then close
the old one in the same layout transaction. Keep the clicked row's header anchored when it can
remain in bounds; reveal enough of the details to be useful with minimal scrolling. Preserve
the anchor as content heights change; clamp at list boundaries. Do not jump to the list top.

Expanded content answers questions that the collapsed row cannot:

- Count quest: explain qualifying species/tag and relevant environment conditions, including
  time/location/weather if present. State whether it counts lifetime catches or the daily period.
- Collection: explain distinct catches and show every eligible species inline in a wrapping
  icon grid. Caught species use their colored item artwork; uncaught species use their actual
  black silhouettes, matching the existing collection quests. Remove the “Found: Bluegill…”
  and “9 undiscovered…” summary lines. The header's exact count remains the numeric summary.
  The inline grid is the only collection detail view; there is no View species button or sub-screen.
- Ready quest: show completion evidence and all rewards, retaining the separate inline Claim.
- Tutorial: show that it does not reset, and list all component-bearing reward stacks accurately.
- Claimed quest: show Claimed and the authored reward contents; do not invent a dated payout
  history if the system does not store one.

Esc closes the book from the quest list, including when a row is expanded. There is no
collection sub-screen or intermediate Back state. Species icons retain direct encyclopedia
navigation and caught-species tooltips; uncaught silhouettes use generic ??? labels.

Collection grid contract:

- Resolve the full target tag, subtract exclusions, and apply existing hidden-unlisted rules.
  Render one icon per remaining species; do not replace the collection with a handful of samples.
- Match the current quest collection order: descending fish-profile base weight, with a stable
  species-ID tie-break. A new catch fills its existing slot instead of reordering the grid.
- At reference size, use up to ten 20-pixel icons per row, 26-pixel horizontal pitch and 24-pixel
  vertical pitch, centering each row like the existing collection list. Wrap to fewer columns
  when needed. Grow the expanded quest for the entire grid, followed by the shared Rewards block;
  keep scrolling/scissoring at the existing quest-list viewport rather than clipping the grid.
- Six of the fifteen eligible river species are caught in the sample. The header count and
  inline grid must agree. Do not show a name for an uncaught silhouette.
- Species icons retain the existing encyclopedia interaction, including uncaught silhouettes;
  the destination must keep its own discovery protection. Inspecting/clicking an icon must not
  claim the quest or toggle the accordion. Keyboard focus reaches the icons with the same
  caught/uncaught narration and supported encyclopedia action.

Animation, if added, is brief and action-triggered. Reduced motion makes height changes immediate.

## Claim transaction and list stability

1. On activation, keep the row in place and change Claim to a disabled Claiming state. Block
   duplicate submissions for that quest; keep the user oriented and expose pending narration.
2. Treat server acknowledgement as authoritative. On success update the balance from confirmed
   state, show Claimed in place, and update the rail/filter counts. Keep a Claimed receipt row
   temporarily visible even if it no longer belongs to the Ready filter.
3. Defer removal/reordering while focus or the pointer remains in the affected row, or while
   it is expanded. Once interaction leaves it, apply the new ordering with a stable quest anchor.
   Never place a different Claim button under the pointer as a result of this transaction.
4. On failure, preserve the row and balance, restore Claim when retryable and show a short inline
   reason. A stale/expired quest refreshes authoritatively and says that it expired. Do not show
   success or optimistic token gains before confirmation.
5. At daily rotation, preserve unaffected quests by ID, clear expired selections and announce
   the updated list. Restore focus to the destination/filter when its quest no longer exists.

## Keyboard, narration and empty states

Tab reaches navigation, filters and explicit controls in visual order. Enter/Space activates
focused buttons and disclosures. Row headers expose expanded/collapsed state; Claim has its own
focus target and accessible reward label. Support existing game navigation conventions rather
than trapping focus in a custom mouse-only list. Keep focus visible through list changes.

Narrate objective, exact progress, difficulty, reward entries and readiness without requiring
color or item recognition. Uncaught silhouettes use generic labels only. A disabled Buy exposes the
shortfall in visible text as well as narration. Pending/error status must be announced once.

Empty Ready: “No rewards ready in Today” (destination-localized) and a Show all action.
Empty In progress: explain that no unfinished visible quests remain and offer All.
No unlocked content: explain the applicable discovery/unlock action without spoiler text.
Loading, sync failure and genuine empty data are distinct; a sync failure offers Retry.

## Market

Keep the two-column stock layout. Available items have Buy and a visible price. If balance is
insufficient, disable Buy and show the exact shortfall. Remove sold-out entries from the shop
entirely: no panel, disabled tile, Bought label or placeholder slot. Filter entries with zero
remaining stock before assigning cells, then pack the remaining items into the two-column grid.
A purchase with stock still remaining keeps its panel; affordability never counts as sold out.

Remove a panel only after the server confirms the purchase exhausted its stock. Do not remove
pending or rejected purchases. Preserve focus by item ID; if its panel disappears, move focus
to the next remaining item (or the previous one at the end), and to Reroll stock when none remain.
Do not activate a new Buy under a stationary pointer as a consequence of reflow. Purchases,
disabled states and balance changes remain server-authoritative.

If everything is sold out, show one empty-state message: “All stock sold out. Wait for new stock
or reroll below.” Keep the automatic rotation timer and paid reroll controls available. Refresh
the filtered grid from authoritative data at automatic rotation or paid reroll.

Use “New stock in [time]” for automatic rotation. Label the separate paid action Reroll stock,
with visible current cost, “Replace today's stock” explanation and next reroll price. Existing
costs are 25, 50, 100, then 200 capped. The mockup shows 25 now / 50 next. Do not invent a new
currency or change purchase limits. Preserve existing purchases when applying server reroll data.

Stock item names and prices in the image are illustrative layout content. They do not authorize
adding those products or changing the real economy. Test actual longest names and reward stacks.

## Cleanup

Represent the existing cycle accurately: every 200 trash, use a 50-token base pool for automatic
proportional awards. Each contributor's award is rounded with a minimum of one token, so the
sum can differ from 50. Contributors are eligible regardless of being online at the crossing;
there is no manual Claim action. A completed cycle resets; excess contribution carries forward.

Pin the exact player's contribution and share above the scrollable contributors. Compute shares
against the authoritative cycle total, never a displayed top-N subtotal. A player outside the
synced contributor list must not be represented as zero: implementation must supply an exact
personal count or show “Your contribution unavailable” while loading/recovering. Use the same
honesty for zero-total/loading cases; avoid division by zero. Do not promise a fixed token
estimate before the cycle closes. The fixture is 125 total: Mira 55, Jo 39, Ari 21, You 10.

Implementation dependency: the current CleanupGoalProgress snapshot contains total, threshold,
notification milestone and contributors, but no explicit reward-pool or guaranteed personal-share
field. Before shipping this layout, verify complete contributor availability and add authoritative
fields if needed. The mockup is not proof that all required data is currently synchronized.

## Acceptance and playtest gate

Static verification for this revision:

- Render all images and comparison sheets without declared text-width overflows.
- Visually inspect title/objective/count/reward spacing, panel clipping and scrollbar placement.
- Check bronze/default, silver and gold frames on collapsed, expanded, ready and claimed quests;
  confirm focus remains distinct from the tier artwork and no letter badges remain.
- Compare identical live/proposed quest IDs, targets, tiers, rewards, progress, balance and timer.
- Compare expanded tutorial and collection reward blocks for identical icon/text/column
  treatment; only objective evidence, reward contents and resulting height may differ.
- Confirm default Today shows two ready rows and Goals one; collection and tutorial rewards
  include the authored item entries; Tetra Scholar is shown only in an unlocked fixture.

Required prototype/implementation checks, not completed by static PNGs:

- Find a claimable reward, select a feasible fishing action and identify missing collection
  progress. Compare time, incorrect opens and comprehension using identical A/B content.
- Keyboard-only Claim, disclosure and species icons on the same row; verify direct encyclopedia
  navigation and that Esc closes the book without an intermediate collection screen.
- Expand near the viewport bottom, switch expanded rows, and scroll a long inline collection grid.
- Claim several adjacent rewards, including Ready-filter receipts, with the pointer stationary.
- Exercise pending, rejected, duplicate, expired and successful claims with authoritative balance.
- Check one-, three- and five-entry reward previews, overlapping hit regions, full group tooltips,
  component variants and omitted-entry badges. Keep sprites clear of title/count/Claim/disclosure.
- Check translations with long titles, long objectives, large numbers, item-only rewards and
  wrapped conditions at logical 480×270 and 426×240, plus relevant GUI scales.
- Verify the inline grid contains all eligible species, colored caught icons and actual uncaught
  silhouettes, with stable ordering and no hidden-unlisted or excluded species. Compare its
  caught count with the header; check protected names/tooltips and encyclopedia links.
- Check hidden/locked quests and undiscovered species for leaks in rows, badges and tooltips.
- Check personal contribution outside displayed top contributors, zero contribution, zero cycle,
  milestone rollover and offline payout behavior.
- Check unaffordable Buy/reroll, sold-out panel removal and grid packing, partial stock remaining,
  pending/rejected purchases, focus after removal, the all-sold-out state, automatic rotation
  and reroll price cap.

Do not describe efficiency improvements or runtime accessibility as validated until these checks
have evidence. Further UI decoration is secondary to resolving any failures in these tasks.
