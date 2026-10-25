# Changelog

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

## 2.1.2

Fixed fish tank rendering and companion party sync found in review.

- Changing a tank's shape or materials now redraws it immediately instead of leaving the old
  model on screen until the chunk reloads.
- Non-standard tank shapes, every connection layout and the corner and edge pieces now render on
  Forge instead of falling back to the standard tank.
- Decorated tanks no longer reuse another tank's decoration mesh, and tinted decorations such as
  leaves and ferns keep their colours.
- A reconnecting player's companion party is now announced to players already on the server.

## 2.1.1

Fixed Gelatin UI being treated as optional: all loaders now report it as a required dependency, and CurseForge installs it as a required dependency.

### Fish companions: the party

- Companions now have three slots, all open from the start. Mix species, or put two or three of
  one species in to make a school, line or crowd. Slot 1 is the lead.
- **Worlds from earlier test builds lose their automatic trios.** A small free-swimming companion
  (under 20 cm), a starfish or a sea slug used to bring copies of itself along as a group.
  Those copies are gone: a group now forms only from fish you put in the party. If you were bonded
  to one small bluegill, you'll see one fish after updating, not three. To get the school back,
  catch two more and put them in slots 2 and 3. The copies were never items, so they can't be
  handed back without duplicating fish.

## 2.1.0

Added ambient fish, companions, and interactive fish tank cosmetic decorations
