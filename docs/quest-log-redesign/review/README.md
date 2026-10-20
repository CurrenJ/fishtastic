# Quest log implementation review — 20 October 2026

Compared the working screen with the reviewed mockups and `design-spec.md`. The original design discussion in README is historical; this file records implementation evidence.

## Corrections

- Match the 416×232 book shell, dark navigation/content interiors, integer-aligned bitmap text, header balance, filters and reset timer. Keep five ordinary daily rows in the 180-pixel list viewport.
- Separate the quest identity icon, objective, 76-pixel progress bar, exact count, item previews, token/Claim column and disclosure. Preserve bronze/silver/gold frames and the warm card interiors.
- Show tokens even when a quest also rewards items. Anchor preview stacks at x=164, overlap sprites by six pixels, and position overflow badges against the last sprite. Use the mockups' shared coin ladder.
- Use concise tutorial/zone/collection summaries where the objective fields permit; keep full authored conditions and grow other rows. Expanded collections show all eligible species in a stable grid, six caught and nine silhouettes in the river fixture.
- Share the expanded token/item reward columns across tutorial and collections. Use real component-bearing ItemStacks, quantities and item tooltips rather than substituting mockup product names.
- Give Market a packed two-column layout, omit confirmed sold-out stock, show disabled unaffordable Buy controls and exact shortfalls, and distinguish automatic stock rotation from paid rerolls and their next price. Use the mockup's ordinary gray purchase buttons.
- Give Cleanup the milestone, remaining count, proportional automatic payout explanation, pinned personal status and independently scrolling contributor rows. Compute shares from the actual cycle total. A player absent from the capped snapshot is explicitly unavailable rather than incorrectly zero.
- Preserve destination scroll positions and quest anchors through rebuilding. Keep an interacted-with claim row in place, block duplicate pending claims, and wait for authoritative state before showing success or changing currency. Restrict species/preview click handling to their own bounds.

## Reproduction

```powershell
$env:JAVA_HOME='C:\Users\curre\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2'
./gradlew.bat -I docs/quest-log-redesign/capture.init.gradle -PquestLoader=fabric -PquestScene=questbook :fabric:runClient --offline --console=plain
./gradlew.bat :common:test --tests '*Quest*' :fabric:compileJava :neoforge:compileJava --offline --console=plain
```

The opt-in `questbook` scene uses an isolated self-test world and explicitly synthetic client-cache states for expanded rewards, collection progress, balance and cleanup contributions. It captures real Minecraft rendering, not a recreation of the UI. Market uses actual active products and prices, so its names need not match the illustrative products in the mockup.

## Evidence and limits

- Common, Fabric and NeoForge compilation passed. Resource-ID and Java API guards passed.
- Existing quest unit tests: 5 passed, 0 failures/errors.
- Fabric runtime: reward-preview clicks leave disclosure unchanged; clicking disclosure collapses the row. Destination viewport checks pass.
- Screenshots: [Today](baseline.png), [expanded tutorial](tutorial.png), [expanded collection](collection.png), [Market](market.png), [Cleanup](cleanup.png), [Cleanup at 426×240](cleanup_426x240.png).
- The native bitmap font and translated ItemStack names are authoritative. Mockup labels such as “Oak tank” are not new registered item names.
- These checks do not establish keyboard/narration completeness, successful/rejected multiplayer purchase or claim transactions, or usability improvements. Viewports below 426×240 and long translations still need separate validation. The capped cleanup snapshot cannot supply an absent player's exact contribution; the screen reports that limitation explicitly.
