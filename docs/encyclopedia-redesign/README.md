# Fishopedia info page

**Status:** revision 3 is implemented and scene-verified. This page records the accepted behavior;
the code is authoritative if it differs.

## Accepted design

- The fish page uses an integer-scaled, one-screen field-guide layout. Its portrait shows the fish,
  size records, fight difficulty, and bond state; the catch guide shows whether the species can bite
  in the current environment, plus zone, biome, time, weather, moon, and bait information.
- Uncaught fish remain silhouettes. The unlock track follows each species' actual thresholds and has
  one claim action. The disc also offers one book-wide claim action.
- Players can move between species from the page. The selected icon remains the same object during
  the disc-to-portrait transition.
- Portraits use a static zone-themed plate and capped animation. Players can pause it globally;
  clicking the plate and claiming rewards can trigger short effects. Reduced celebration effects
  suppress the claim burst.
- Condition colors explain whether a factor helps or hurts, while a separate marker shows whether
  it is true now. Labels use plain language; exact multipliers remain available in tooltips.

## Implementation and validation

The page lives in `common/src/main/java/grill24/fishtastic/client/encyclopedia/`, with the screen in
`FishEncyclopediaScreen` and the reward packet in `network/ClaimAllEncyclopediaRewardsPacket`.
Portrait behavior is in the `portrait/` package. The live self-test scenes are in
`client/selftest/RenderSelfTest.java`.

The implementation passed 41 encyclopedia checks on Fabric and NeoForge across GUI scales 4, 3, 2,
and 1, plus a 320×240 compact case. The automated scene does not close these owner reviews: sound
feedback, real-mouse hover behavior, and visual polish of the compact card. No broader player
usability study was performed.
