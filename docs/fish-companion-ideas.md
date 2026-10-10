# Fish Companion — Idea Checklist

> **How to use:** put `y` or `n` inside each `[ ]` (e.g. `[y]`). Leave blank if undecided.
> Add a note after `→` if you want a tweak. Every item has an ID so you can also just
> reply in chat ("no to C4, B2 but only for jellies").

---

## A. Core direction

- [ ] **A1** — Companion is primarily a *fishing* buddy: it reacts to the fishing loop more than anything else →
- [y] **A2** — Cosmetic only, no gameplay perks (at least at first) →
- [y] **A3** — Small schooling fish come as a trio orbiting you; big fish come alone →
- [y] **A4** — Pet-to-pet multiplayer interactions are a headline feature (designed early, not an afterthought) →

## B. Display modes (driven by the existing locomotion class)

- [y] **B1** — `FREE_SWIM`: orbits your shoulders, banks into turns, lazy figure-eights →
- [y] **B2** — `GLIDE` (rays): wide slow arcs overhead like a kite; low swoop that kicks up dust →
- [y] **B3** — `DRIFT` (jellies): bobs behind a shoulder on a loose spring; pulses; glows at night →
- [y] **B4** — `BENTHIC` (starfish, crabs, flatfish): sits on your head/shoulder →
- [y] **B5** — Benthic tricks: spin, hop, slide down your arm and climb back up →
- [y] **B6** — `ANCHORED` (garden eels): pokes out of your backpack/helmet, sways →
- [y] **B7** — Anchored ducks back in when a mob comes close (mirrors the tank startle) →
- [y] **B8** — `animation.mode` refines it within a class (`upright_float` stays upright, `planted` never leaves its perch, etc.) →

## C. Personality (driven by the existing temperament)

- [y] **C1** — Temperament changes how the companion behaves (same body plan, different feel) →
- [y] **C2** — docile: stays close, nuzzles you when you stand still →
- [y] **C3** — skittish: hides behind your head near mobs, peeks out →
- [y] **C4** — aggressive: puffs up at hostile mobs, "bites" the air when you take a hit →
- [y] **C5** — sprinter: dashes ahead when you sprint →
- [y] **C6** — ambusher: hangs still, then darts out at dropped items →
- [y] **C7** — wily: steals the spotlight in pet-to-pet interactions →
- [n] **C8** — ghost: semi-transparent, phases through blocks →
- [y] **C9** — erratic / trembler / metronome: idle-timing changes only (jittery / shaky / rhythmic) →

## D. Fishing reactions

- [y] **D1** — Watches the bobber while you're casting; leans toward it on a nibble →
- [y] **D2** — Mirrors minigame tension (tenses as the bar drains, wiggles when you're in the zone) →
- [y] **D3** — Catch celebration that scales with quality (nod → … → spin + particle burst for legendary) →
- [y] **D4** — Same-species catch: special "greeting" toward the fish you just caught →
- [y] **D5** — Droops for a few seconds when a fish gets away →

## E. Ambient world reactions

- [y] **E1** — Dives into water and swims "for real" beside you when you're in water →
- [y] **E2** — Weather/biome moods: happy in rain, miserable + steam in the Nether, shivers in snow →
- [y] **E3** — Presses against the glass of a tank containing its own species →
- [y] **E4** — Fish inside a nearby tank gather toward the companion →
- [n] **E5** — Companion can set off tank cosmetic reactions (clam, shrine, etc.) when it swims close →
- [y] **E6** — Cats stalk it; it flees onto your head →
- [y] **E7** — Friendly circle around axolotls and dolphins →
- [y] **E8** — Day/night & moon behaviour from the species' time/moon weights (sleepy off-hours, special at full moon) →

## F. Player-triggered interactions

- [y] **F1** — Sneak + empty hand to pet it (hearts) →
- [y] **F2** — Feed it bait for a unique snack animation (bait consumed, purely cosmetic) →
- [y] **F3** — Emote keybind: signature trick on demand, with cooldown →
- [y] **F4** — Nameable via name tag; name shows when you sneak →

## G. Pet-to-pet interactions (two companions within ~6 blocks)

- [y] **G1** — Same species: synchronized loop + bubble heart →
- [y] **G2** — Two free swimmers: short race lap around both players → DEV NOTE: figure eight around players if possible
- [y] **G3** — Swimmer + benthic: swimmer circles the other head, starfish waves → DEV NOTE: not sure how to animate a wave - might have to be a jump
- [y] **G4** — Predator + prey: small one hides behind its owner, predator does a smug lap (comedic, no harm) →
- [y] **G5** — Wily steals a bubble/particle from the other pet and brings it back →
- [y] **G6** — Fancier rare version when both pets are legendary quality →
- [n] **G7** — "Met <species>" stamps — a light social collection for pairings you've seen →

## H. Unlocking & equipping

- [y] **H1** — Bond with a specific fish you caught; companion keeps that fish's quality and size → DEV NOTE: need a simple and clear way for player to do this. 
- [y] **H2** — Species must be (near-)complete in the encyclopedia before it can be a companion →
- [y] **H3** — A few "signature" companions from quests with extra interactions (e.g. molten moorish idol) → DEV NOTE: maybe unlisted fish companions are only unlockable via quests instead of normal catch progression?
- [n] **H4** — Companions purchasable in the shop →
- [y] **H5** — Equip via a GUI tab (e.g. in the encyclopedia), no accessories-mod dependency →
- [n] **H6** — Equip via a "Fishbowl" item in a curio/trinket slot instead →
- [n] **H7** — Retire a companion to a tank; it keeps a crown icon in the tank browser →

## I. Technical approach

- [y] **I1** — No entity: rendered as a player render layer, state client-side →
- [y] **I2** — Deterministic seeding (player UUID + game time) so pet-to-pet scenes match on every client without per-tick packets →
- [y] **I3** — Reuse the tank fish renderer (render calibration, squash-and-stretch) so every species works without extra art →
- [y] **I4** — Client settings: hide other players' companions; hide your own in first person →

---

## Open notes

→ the fishing minigame is a screen space visual elements, so interactons of pets with the fishing minigame should be conscious of that. I picture our fish living mostly in world space so we need to htink about how to handle this smoothly and so it looks good.
