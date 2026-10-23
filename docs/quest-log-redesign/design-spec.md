# Quest book interaction contract

Implemented and reviewed in game on 20 October 2026. The Captain's Log presents
quests, Market and Cleanup without changing the economy, discovery protection,
tutorial rewards or server validation. Tracking/HUD pinning is outside this revision.
Code defines current behavior; the open checks below are not implied complete by
this contract.

## Destinations and visibility

| Destination | Contents and ordering |
|---|---|
| Today | Active dailies plus unclaimed First Catch tutorial; ready, in progress, not started, claimed |
| Goals | Lifetime/mastery; ready first, then proportional progress, not started, claimed |
| Explore, Collect, Trials | Category quests, same state/progress ordering |
| Market | Purchasable stock; available then unaffordable, sold-out omitted |
| Cleanup | Current server cycle, personal contribution and contributors |

Apply prerequisites/hidden rules before counts, sorting or rendering. Never leak
locked names, icons, rewards or readiness through badges. Stable ties use difficulty,
reward, display name and quest ID, not guessed fastest/actionable scores.
All includes claimed quests. In progress includes visible incomplete zero-progress
quests. Ready is local to the destination, with a count; zero remains selectable.
Rail counts omit zero. Preserve filter, expanded quest ID and scroll anchor per
destination for the screen session; reopening starts at Today. First Catch explicitly
says Tutorial/no reset; Today's timer describes daily quests only.

## Layout and state

Reference logical viewport: 480x270. Shell: 416x232 at (32,19), with 74-pixel rail,
four-pixel gap, 338-pixel content panel and 11-pixel content insets. List starts
48 pixels below the panel top, with 180 pixels of viewport height. Rows start at
36 pixels and grow for translated text without shrinking the bitmap font. Scroll
inside the panel; pixel-align text and retain integer scaling. At 426x240 reduce
outer margins; smaller viewports need a bounded scrollable fallback.

Keep title left and token balance top-right. Token container occupies panel y=6..24,
with a 16-pixel coin inset one pixel and text inset five pixels from its top.
Filters/reset start at y=28, baseline y=32. Do not extend the balance behind them.
Scrollbar: recessed mauve #352A32 track, #72505B edge, #C58A58 thumb and #FFE0A0
highlight; proportional thumb, omitted if content fits. No redundant subtitles.

| Meaning | Presentation |
|---|---|
| Selected destination/filter | Gold edge/underline, filled selection and label |
| Difficulty | Original bronze/default, silver or gold nine-sliced panel; white title, full tier label in tooltip/narration; no letter badges |
| Ready | Narrow green strip, full progress and green Claim; preserve tier frame |
| Incomplete | Mauve progress, neutral row, white disclosure |
| Claimed | Explicit text and subdued progress; still inspectable |
| Focus | Separate high-contrast outline, independent of readiness/tier |
| Uncaught eligible species | Actual silhouette with generic ??? label; hidden unlisted species omitted |

Keep warm mauve card interiors; no darkening wash. Muted copy #C9B8BE,
shell #201A24, selection #FFAA00, ready #5DCC7A, progress #B86E88 and timer #8CCBFF.
Use the existing coin/button art and `generic_item_panel` bronze/silver/gold sprites
with four-pixel borders. Readiness must be understandable without color.

## Row, rewards and expansion

Every collapsed row exposes a verb, quantity/distinct-species unit and essential
qualifying conditions. Use localized objective-field summaries; fall back to full
wrapped prose rather than clipping conditions. Exact progress sits below the
objective; zero means an empty bar. Resolve counts/tags/exclusions from actual data.

Reward previews use real component-bearing ItemStacks in authored order. Show up
to three 16x16 sprites at ten-pixel offsets, rightmost on top; omit quantity numerals
in collapsed art. More entries use a small +N badge overlapping the final sprite by
two pixels, counting omitted entries rather than quantities. Reserve overflow space
on every row and keep the first sprite's column fixed regardless of reward count.
The group's hover/focus details expose all names, quantities and components;
inspection neither claims nor expands. Topmost sprites own overlapping hit areas.

At reference width: first preview x=164, objective width 126 when previews exist,
progress bar x=32/width=76, count x=114/width=44. Vertically center preview/coin
(16 pixels), amount/disclosure (eight pixels) and Claim (19 pixels) in the header,
not the whole expanded card. Claim appears only when ready; disclosure remains
separate on every inspectable row. Recompute positions when text wraps and never
paint over text, controls or tier frame. Item-only/token-only rewards omit absent
pieces without moving the other anchors.

Use one expanded Rewards block across all categories and tutorial: white heading,
coin/gold tokens left, actual item icons with white names and exact quantities right.
Reference offsets: coin (12,12), amount (32,16), items (135,11), names x=151,
15-pixel row pitch. Grow for content. Collection evidence goes above Rewards;
never replace the block with a prose reward summary.

One quest is expanded per destination. Switching rows is one layout transaction:
keep the clicked header anchored where possible, minimally reveal details and clamp
at list ends. Do not jump to the top. Details explain qualifiers and daily/lifetime
scope; claimed rows show authored rewards without invented payout history.

Collections show every eligible species inline, resolving tags/exclusions and hidden
rules. Order by descending profile base weight then species ID; a catch fills its
existing slot. Caught art is colored, uncaught art silhouetted without names.
Reference grid: up to ten 20-pixel icons, pitch 26 horizontally/24 vertically;
wrap fewer columns and grow inside the existing list viewport. Header count and
grid agree. Icons link directly to the protected encyclopedia without claiming or
toggling expansion. No collection sub-screen; Esc closes the book. Reduced motion
makes height changes immediate.

## Authoritative transactions

Claim disables as Claiming, prevents duplicate requests and keeps the row anchored.
Only server confirmation changes balance/state. Success updates counts and shows
a Claimed receipt even in Ready; defer removal/reordering while the pointer/focus
remains or the row is expanded. Never move another Claim under a stationary pointer.
Failure preserves balance, explains the reason and restores retry when appropriate;
expired quests refresh authoritatively. Rotation preserves surviving IDs and restores
focus to the destination/filter when a selected quest expires.

Market uses a packed two-column grid. Disabled Buy states show the exact shortfall.
Only confirmed exhausted stock removes a panel; pending/rejected or partially sold
stock stays. Preserve focus by ID, advancing to a neighbor or Reroll when empty;
reflow must not activate another Buy. Automatic rotation is labelled New stock in;
paid Reroll stock explains replacement and current/next cost (25, 50, 100, 200 cap).
Keep timers/reroll in the all-sold-out state. Data refreshes remain authoritative.

Cleanup awards automatically every 200 trash from a nominal 50-token base pool.
Awards are proportional, rounded and at least one per contributor, so totals may
differ from 50. Offline contributors remain eligible; excess carries into the next
cycle. There is no manual Claim. Pin the player's exact contribution/share above
contributors, using the authoritative total rather than a top-N subtotal. If the
snapshot cannot supply the player's value, show unavailable/loading rather than zero.
Do not promise a fixed payout estimate; handle empty totals without division by zero.

## Accessibility and remaining verification

Tab follows visual navigation/control order. Enter/Space activates controls;
expanded state, objectives, exact progress, tier, rewards and readiness have narration.
Claim and species icons have separate focus targets. Disabled purchases explain
shortfall; pending/error status is announced once. Preserve focus across updates.
Empty Ready/In progress offer Show all/All; locked content gives a spoiler-safe
unlock explanation. Loading, empty data and sync failure differ; failure offers Retry.

The recorded live review and focused Fabric scene covered reward hit bounds,
disclosure and destination viewport behavior; compilation and existing quest unit
checks passed. This was not a full accessibility or multiplayer transaction audit.
Remaining checks:

- Keyboard navigation/narration, including Claim, disclosure and species links.
- Successful, rejected, duplicate and expired multiplayer claims/purchases, with
  stationary-pointer receipts/reflow and authoritative balances.
- Viewports below 426x240, long translations, large numbers and one/three/five-entry
  rewards; no condition truncation, overlap or spoiler leaks.
- Cleanup contribution outside the capped snapshot, rollover and offline payout.
- Sold-out/partial stock, reroll, empty market, focus retention and rotation.

Use current quest-book self-test scenes for visual checks. Historical PNGs and
fixtures are not runtime proof and are recovered through Git only when needed.
