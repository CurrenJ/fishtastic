# Medium Cosmetics and Reactions — Handoff

**Written:** 2026-10-03, at the end of the session that built the first wave of reactive cosmetics.

> **Status:** the reaction system and four pieces are built and committed. The owner checked them
> live in game on 2026-10-03 and was happy. A world-load crash (triggers dropped when shelters
> were moved into the engine frame) was found by the owner and fixed. It is covered by
> `TankReactionsTest`.

Read first: [`fish-shelters.md`](fish-shelters.md) §12.13 (the reaction system as built), §12.9 to
§12.12 (multi-shelter structures, `occupied_cells`, deboxing, tunnels as gates), and §12.2
(rarity). This file is the cold-start context: what the owner asked for, what exists, the build
ideas still to do, and the constraints that will bite.

## The brief (owner, 2026-10-03)

Expand the collection of **medium-sized cosmetics**: one tank, between the small single-cell
pieces and the large multi-tank set pieces. Give them shelters, pathways, and the first
**interactibles**. The bar the owner set: builds that "overflow with aesthetic intention and visual
appeal", showing off great Minecraft building and how beautiful a tank can be. The owner prunes
after seeing renders, so build boldly and show them.

Owner decisions:
- **Sounds:** yes, but quiet (volume 0.15 to 0.3). They are vanilla sound events played only on
  the client.
- **Changing over time:** liked. A reaction may leave a structure in a new resting state (for
  example armillary rings that stay turned). **Not built yet.** Today every reaction returns to
  rest.
- **Portcullis:** in the first wave (done).
- **Palette:** prefers cohesive ramps over high contrast (from the earlier deboxify pass). See
  memory `cosmetic-underwater-palette`: fog flattens pale blocks, terracotta and dripstone go
  mauve, saturated living colour and dark/pale value carry.

## What exists

| Piece | Structure / item | Interaction |
|---|---|---|
| Giant Clam | `giant_clam` (re-authored; same item) | Nose the front lip: lid hinges up, mantle and pearl glow |
| Sunken Strongbox | `sunken_strongbox` / `cosmetic_sunken_strongbox` | Nose the gold lock: lid hinges up, treasure glows |
| Wayside Shrine | `wayside_shrine` / `cosmetic_wayside_shrine` | Gate beneath. Nose the bell rope: the bell sways (`sway_ticks`), candles and lanterns light |
| Sunken Gatehouse | `sunken_gatehouse` / `cosmetic_sunken_gatehouse` | Locked gate: the portcullis slides up for one fish |

Where the code lives:
- **Data:** `fishtank/CosmeticReaction.java` (nose or gate trigger, motions with hinge, slide or
  sway, glows, bursts, sounds, timing, `Key`). `CosmeticStructure` adds the `reactions` list, a
  part `group`, `LiveParts.animatedParts`, `isGateDoor` and `reactionError` validation.
- **Engine:** `fishsim/domain/Shelter.java` adds `Kind.TRIGGER` and the `Trigger` record.
  `FlockEngine` adds `stepTriggers`, `sendForTrigger`, `noseSteering`, locked-gate hulls,
  `drainTriggerEvents` and the static `setTriggerRateBoost`. Engine test: `ShelterTriggerTest`.
- **Host:** `client/util/TankShelters.java` keys triggers by `(tank, anchor cell, index)` and
  must carry `trigger` through every shelter copy. `client/util/ClientCosmeticReactions.java`
  holds the timelines, sounds, bursts and candle flames. In `FishTankBlockEntityRenderer`,
  `renderReactionParts` draws grouped parts per frame. `TankCosmeticMesh` and
  `CosmeticObstacles` skip animated parts and door parts respectively.
- **Generator:** `tools/shelter-structure-gen/reactive.py`, run through `gen.py`.
  `Build.set(..., group=)`, `b.doors`, `b.reactions`; grouped parts are never culled and never
  hide neighbours.
- **Dev tools:**
  - `/fishtastic cosmetic reactionboost <x>` speeds up reaction clocks on your client for the
    session. The 30 s rest between reactions is not boosted.
  - The `cosmeticpreview` marker line `reactionOpen=1` renders every reaction held open, with
    orbits suffixed `_open`.

## Known issues to address first

1. ~~**Watch it live.**~~ Done 2026-10-03: the owner checked it in game and was happy. Headless
   runs still have no fish in reactive tanks; that is how the world-load crash slipped through.
2. ~~**Art pass on the shrine and gatehouse.**~~ Done 2026-10-03, uncommitted pending the owner's
   look: [`fish-shelters.md`](fish-shelters.md) §12.13 "Art pass". The shrine now claims 3 grid
   cells, not 1, because of its podium.
3. ~~**Clam palette.**~~ Done in the same pass.
4. **Giant Clam footprint changed** from a north–south column of 3 cells to a 3×3 extent (it only
   blocks the centre cells). Clams already placed in a world may overlap neighbours. Tell the
   owner if it matters.
5. **Optional:** a flag to skip the 30 s rest while testing.
6. ~~**Commit when the owner is happy.**~~ Done 2026-10-03. The sticker sheet changes went in as a
   separate commit.

## Build ideas still to do (from the brainstorm the owner approved)

Each line gives the look, the fish affordance, and the reaction or primitive it would use. The
brainstorm's first wave is done; these were the rest.

**Story pieces**
- **Diver's Helmet** *(hollow)*. It lies on its side with the neck ring as a horizontal mouth. The
  faceplate is a copper grate, so the fish shows *inside* the helmet. The copper grades from
  bright on top to full verdigris underneath, with chain trailing into the sand. Reaction:
  bubbles from the valve when a fish leaves. That needs a new trigger, "on EXIT of a shelter",
  or a short-mean nose anchor on the valve. The brainstorm called this the most charming piece.
- **Ship's Bell retrofit** of the existing `drowned_bell` *(nose)*. A fish bumps it: a hinge swing
  with `sway_ticks`, a silt burst, and a muffled `block.bell.use` at low volume.

**Natural formations**
- **Ammonite Fossil** *(hollow den, lurker)*. A spiral shell half buried, calcite and dripstone
  bands (watch the mauve), and a packed-mud chamber mouth.
- **Black Smoker Vent** *(gate at the base, burst)*. A basalt and blackstone chimney with magma
  glow at the foot. Tube worms are bone or white stems with red fire-coral fan tops. Reaction: a
  plume of bubbles, either as a nose trigger at the vent or as a clock-only reaction. A
  "structure fires with no fish" mode doesn't exist yet and would need adding.
- **Coral Bommie** *(open shelter under the overhang, gate fissure)*. A mushroom-shaped coral
  head: brain-coral cap, horn and tube coral down the sides, fans in the cracks.
- **Anemone Rock** *(nose, uncommon)*. Bubble-coral fans, pink petals and sea pickles on a rock.
  A fish nestles in, the tentacles slide-retract, and the pickles glow. Gobies make it read as
  clownfish-and-anemone.
- **Cleaning Station.** A flat-topped boulder with the neon goby as cleaner. It needs the
  **perch** verb (§12.6), which isn't built. Design the rock now; the behaviour comes later.

**Little architecture**
- **Armillary Sphere** *(pass trigger, spin)*. Nested copper rings with a gold equator on a stone
  pedestal. A fish swims through, and the rings turn a quarter step and **stay** turned: the
  owner's "changing over time". It needs:
  - a **spin** motion that accumulates instead of easing back,
  - per-structure client state for the current angle (not stored or synced; it may reset on
    reload, so ask the owner),
  - an **unlocked** gate that still raises events: today only locked gates trigger. Add an
    "event on pass" flag to the gate reaction that leaves it open to everyone.
- **Aqueduct Fragment** *(2×1×1 or wide single tank, several gates)*. Two Roman arches and one
  collapsed, a channel on top as a raised tunnel, moss toward the waterline, glow lichen under
  the arches.
- **Shroom Grotto** *(open shelter, no reaction)*. One oversized mushroom with shroomlight gills:
  warm amber light under the blue fog.

**Suggested order:**
1. The art pass above.
2. Pieces needing no new engine work: Diver's Helmet as a plain hollow, Ammonite, Coral Bommie,
   Shroom Grotto, Ship's Bell retrofit, Anemone Rock.
3. The engine additions with their pilots: spin with persistent state and the unlocked event
   gate (Armillary); fish-free clock reactions (Black Smoker); on-exit triggers (Helmet valve).
4. Cleaning Station with perch.

## Constraints that will bite

These were all measured this session.

- **A gate opening must clear 0.4 blocks above the sand.** At 0.25 to 0.31, `closeGaps` probes
  0.20 below the lintel box, finds no water, and grows the lintel down to the sand. The doorway
  is sealed and gets 0 entries.
- **Tall pieces stay about 3 voxels deep (at scale 0.05) through the roaming band,** roughly 0.1
  to 0.63 blocks above the sand.
  - In a 1-deep row of tanks, a deeper piece leaves no lane in front or behind. Fish crossing to
    its doorway from the side stall at its corners (there is no pathfinding).
  - Put depth on the sand (plinths, slabs) or up high (crowns, eaves).
  - Short pieces (about 0.5 tall or less) are fine deep: fish go over them.
  - `ObstacleShelterAccessTest` (3x1x1) catches this. Use the probe pattern described in
    "Verifying" when it fails.
- **Glass limits for floor builds:** `|x|, |z| * scale + scale/2 <= 0.4375` and
  `(y + 1) * scale <= 0.8125`. `export_floor` asserts both. At 0.05 that is |x| ≤ 8 and y ≤ 15;
  at 0.0625, 6 and 12; at 0.07, 5 and 10.
- **Gate shelters need exactly 2 sideways mouths** (`ShelterKindTest`): close the sides with
  parts. Soft parts may sit inside a gateway; a locked gate's door may too (`isGateDoor`).
- **Nose anchors:**
  - The cell in front of the anchor (on the `facing` side) must be water: validation rejects a
    covered face.
  - Fish touch from about `0.45 × length` out.
  - Keep plants out of the approach corridor.
- **Reactions are floor structures only** (no spans). Grouped parts are drawn every frame, so keep
  groups to what moves or glows.
- **Every shelter copy must carry `trigger`** (`TankShelters.toEngine`, `withMinLength`).
  Forgetting it crashes world load, because `Shelter` throws on a `TRIGGER` without one.
- **The bash heredoc breaks on apostrophes** in this environment. Write patch scripts to
  `build/tmp/*.py` with the Write tool.
- **Item ids:** check `FishtasticItems` before naming. `cosmetic_treasure_chest` was already the
  single chest cosmetic.

## Verifying

- **Engine:** `./gradlew :fishsim:test` (283 tests; `ShelterTriggerTest` for reactions).
- **Data, shelters, obstacles:** `./gradlew :common:test`. The tests that matter here:
  - `ShelterKindTest`
  - `ObstacleShelterAccessTest`
  - `ObstacleInvariantTest`: lone tanks at every rotation; it caught jerk and backstop failures.
  - `TankReactionsTest`
  - `OccupiedCells*`
- **In game, headless:** the `cosmeticpreview` scene. It runs every check (loaded, fits ×4,
  item, lang, shop, placement) plus orbits. Run it twice, closed and with `reactionOpen=1`.
  Steps are in `.claude/skills/cosmetic-from-description/SKILL.md` §7.
  - Make contact sheets with ffmpeg and look at every tile.
  - Clear `fabric/run/fishtastic_render_selftest` afterwards: it hijacks the owner's next launch.
- **Item wiring:** the `cosmetic-structure-item` skill (items, creative tab, the
  `CreativeTabGameTests` exact set, lang, item model, shop entry).
- **When a gate gets no entries,** write a temporary test in `common/src/test/.../client/util`.
  Have it build the domain the way `ObstacleShelterAccessTest.run` does, and log each failed
  approach's closest distance to the staging point and where the fish ended. That is how the
  depth problem was found.
- **Still missing:** a headless scene with fish in a reactive tank and `reactionboost` on, to
  catch runtime faults before the owner does. Worth adding before the next wave.
