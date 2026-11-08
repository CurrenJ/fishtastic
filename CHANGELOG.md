# Changelog

## Unreleased

## 2.1.26

- A Pile of Fish can now be cooked in a furnace or smoker, one fish at a time, and right-clicking a campfire with a pile puts one of its fish on the grill.
- Fish tanks built with glass or stained glass now show it without the frame drawn into vanilla glass, so the panes read clean. The tank keeps the glass it was built with. Turn this off with `/fishtastic tank borderlessglass false`.

## 2.1.24

- Fish companions can now swim and walk through open doors and open trapdoors instead of stopping at them.

## 2.1.23

- Breaking a fish tank, Fish Tank Assembly or Electric Fish Organizer now drops what was inside instead of destroying it.
- Breaking a fish tank now drops its cosmetics. A multi-tank structure drops from the tank storing it, and a kelp or hanging strand passing through a broken tank drops the segments that no longer fit.
- The Fish Tank Assembly's tank slot now takes a whole stack of tanks and refits them one at a time. Shift-clicking the result refits as many as the materials allow.
- Quests that ask you to catch fish now only count fish: trash and treasure, including the Legendary treasure tank, no longer earn credit.

## 2.1.22

- Added an FAQ to the Fishtastic help menu, answering common questions about rods, bait, the minigame, tanks and more.

## 2.1.21

- Vanilla flowers, saplings, grasses, ferns, mushrooms and other plants can now be placed as fish tank cosmetics.

## 2.1.20

- Fish you haven't caught or spotted yet show as black silhouettes named "???" everywhere: the creative menu, JEI, inventories and the hotbar.
- Blazed Grub and Gummy Worms now sit with the other baits in the creative tab.

## 2.1.19

- Secret unlisted fish no longer appear as ambient fish in the world, even after you've caught one.

## 2.1.18

- Starting the fishing tutorial now gives the Fishopedia and Leaderboards book along with the Quest Book.
- Right-clicking a carried Pile of Fish over fish keeps dealing one fish at a time instead of swapping the whole pile into the slot.
- The quest book's Cleanup page shows contributor player heads again, and each 200-trash cycle now pays a 250-token pool (was 50).
- The Fish Tank Assembly now mines faster with an axe.
- Fish schools in deep water now climb all the way to your bobber instead of giving up partway.

## 2.1.17

- Ambient fish now swim in species groups: schools, pairs, loose groups and lone fish, following each species' nature.
- Fish swim at species-specific speeds, faster and more varied in open water; tank fish use their species' speed too.
- Ambient fish are already in place as you travel and reach out to 32 blocks, with no pop-in when bodies of water merge.
- Every fish species now declares a cruise speed tier (slow, normal or fast) in its profile.

## 2.1.16

- Fixed a startup crash on Forge when other mods also bundle MixinExtras.

## 2.1.15

- Fixed minigame gear detection for the Obsidian Fishing Rod.
- Added scheduled fish visits, seeded ambient fish, and spyglass observation.
- Added legendary breach celebrations and improved fish-school trails and bite reach.
- Refined hellbender wet/dry visuals and ground-walker behavior.

## 2.1.12

- Added the hellbender, a rare river fish.

## 2.1.11

- School visits now always succeed, and wild school fish leap out of the water every 30-60 seconds.

## 2.1.10

- Wild school fish now occasionally breach the surface with species-specific arcs, then return to swimming.

## 2.1.9

- Interactive in-world visits can now be almost any species, not only the four schooling fish.
  Each species arrives in its own way: alone, as a pair, a loose group or a school. Rare and large
  fish usually come alone, and a visit only forms in water big enough for its species.
- Crabs and other floor dwellers take a bobber from the bottom, jellyfish drift over to it, and
  garden eels take one cast over their burrow but duck if you sprint at them.
- A visit arriving near you sends a few silent ripples across the surface, and creatures that
  barely move still sparkle in their quality's colour.

## 2.1.8

- Interactive fish schools now have a 50% encounter chance per region every five minutes.

## 2.1.7

- Added interactive fish schools. Catchable members approach nearby bobbers and can be caught through
  the regular fishing minigame. Catching one removes it from the shared school.
- School fish keep their quality, shown by the animated mote trail; bite movement and effects have
  been polished.
- Added the coelacanth and one free quest-shop reroll per in-game day.
- Reworked the fish encyclopedia and quest book with improved small-window layouts, keyboard
  navigation, and narrator support. Tutorial help is optional and available from the pause menu.
- Cosmetic placement works on any grid cell with enough room and targets the tank sand so kelp
  does not obscure placement.

## 2.1.3

- Release jars no longer bundle the development self-test harness.

## 2.1.2

Fixed companion and message issues found in review.

- A reconnecting player's companion party is now announced to players already on the server.
- Forge no longer logs "Unknown custom packet payload" warnings for Fishtastic messages.
- Celebration rays no longer draw over notification banners.

## 2.1.1

Fixed Gelatin UI being treated as optional: all loaders now report it as a required dependency, and CurseForge installs it as a required dependency.

## 2.1.0

Added ambient fish, companions, and interactive fish tank cosmetic decorations

## Earlier releases

Initial release of 1.21.1 backport
