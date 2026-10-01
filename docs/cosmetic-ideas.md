# Fish Tank Cosmetic Ideas

A wishlist of tank cosmetics, written 2026-10-01 after building two promo orbit scenes in
`RenderSelfTest` (`fourwaters` and `cathedral`; see `docs/fish-tanks.md` §6 for how cosmetics work
today). Several entries are less "more props" and more fixes for limits those builds ran into; those
are called out with **Limit hit**.

Today's model, for context: single-block cosmetics come from the `fishtastic:tank_cosmetics` block
tag (coral fans, sea pickle, kelp, seagrass, dead bush) and sit on a 3×3 floor grid per tank block
(`CosmeticGridCell`); multi-part structures are datapack JSON under
`data/fishtastic/fishtastic/cosmetic_structure/`, with a footprint limited to that same 3×3 grid.

---

## Things the system can't do yet (biggest payoff)

1. **Kelp that grows through stacked tanks.** **Limit hit:** kelp caps at 3 segments
   (`FishTankBlock.MAX_KELP_HEIGHT`), one block. The cathedral faked tall strands by placing a second
   kelp in the tank above, which leaves a small gap at each seam (the upper cosmetic sits at that
   block's `FLOOR_Y`, not at the top of the strand below). If kelp could carry on through an open
   floor up to the surface, every tank two or more blocks tall becomes a real kelp forest.
2. **Hanging cosmetics**, mounted to the ceiling: chains with lanterns, hanging roots, glow lichen,
   dripstone, upside-down coral. **Limit hit:** everything sits on the floor, so tall tanks are empty
   at the top; the cathedral's upper storey had nothing of its own.
3. **Wall-mounted cosmetics**, placed against the inside of the glass: barnacles, sponges, wall coral
   fans, glow lichen, a ship's porthole ring. These would use the side walls, which nothing touches
   today.
4. **Structures that span several tanks.** **Limit hit:** a structure's footprint is limited to one
   block's 3×3 grid. A shipwreck hull, a whale skeleton or a fallen column lying across a 5-long nave
   needs a footprint across neighbouring tanks in the same group.
5. **A floor grid that fits each shape.** **Limit hit:** `CosmeticGridCell` hardcodes STANDARD's
   dimensions (already listed in `docs/fish-tanks.md` §8), so on the tapered shapes (Bastion,
   Rampart, Faceted) the outer floor cells sit partly inside the corner posts. The `fourwaters` fire
   tank only used edge and centre cells for that reason. Without this fix, some shapes are always
   half-decorated.

## Light and atmosphere

6. **Glow cosmetics that actually light the scene**: sea lanterns, glowing anemones,
   bioluminescent plankton motes. A sea pickle glows but doesn't brighten its surroundings; the deep
   end of the `fourwaters` column wanted real glow.
7. **God rays**: angled shafts of light from the tank's roof, drawn as translucent planes. They'd
   suit the glass-roofed shapes (Cupola, Skylight).
8. **A bubble stone or vent**: a pebble on the floor that sends up a steady column of bubbles. The
   bubble emitter already exists (`TankBubbleEmitter`); this would give it an anchor.
9. **A hydrothermal vent / black smoker** with a shimmering heat plume: the natural centrepiece for
   a fire or Nether tank instead of a campfire under water.

## Plants (the forest needs variety)

10. **Giant kelp with a gas bladder at the top**, taller and leafier than vanilla kelp, plus **sea
    grass in several heights**, so a meadow isn't one uniform carpet.
11. **Swaying anemones** in a few colours. Clownfish-style pairing would be a small extra.
12. **Lily pads and floating duckweed** on the surface of an open-topped tank, with roots hanging
    into the water. They'd also cover the empty top.
13. **Driftwood and mangrove roots** coming down from above and arching across the floor, for
    blackwater tanks with the river fish (tetras, barbs, shiners).

## Story pieces (the "occasional decoration" that gives a tank meaning)

14. **Shipwreck pieces**: a half-buried bow, a broken mast, a spilled treasure chest with a lid that
    opens and closes on a timer.
15. **Sunken ruins as a kit**: fallen columns, an arch fragment, a headless statue, a broken mosaic
    floor tile. Kit pieces could be combined, unlike the single fixed `castle_ruin` structure. These
    would have finished the cathedral.
16. **A diver's helmet and an anchor with chain.**
17. **Fossils and bones**: a whale-fall rib cage (a reef of its own, and a natural home for the
    deep-sea fish) and an ammonite in the sand. In a desert tank they'd read as "this used to be the
    sea".
18. **Little scenes**: a tiny lighthouse, a sunken lantern-lit shrine or torii gate, a miniature
    sunken village.

## Ground that isn't flat sand

19. **Rock formations and terraces**: stacked flat stones with crevices, so the floor has height.
    Rocks could also count as hiding spots for the skittish fish.
20. **Sand ripples and pebble patches**, plus **shell scatters** (scallops, conches, sand dollars),
    as cheap fillers between kelp.
21. **A coral garden kit**: brain, staghorn, table and pillar corals at mixed heights. Today there's
    one coral fan per cell and two fixed reef structures (`coral_reef_1`, `coral_reef_2`).

## Cosmetics that connect to the fish simulation

22. **Hiding caves and holes**: a cave mouth or hollow log that fish swim into and out of, a
    moray-style eel cave, and a burrow for the garden eels. Fish going in and out are the best moments
    on video.
23. **A cleaning station**: a rock that fish queue up at and pause beside.
24. **A feeding ring or floating feeder** that fish gather under. It would give an orbit clip a
    natural focal point.
25. **A bubble curtain**: a line of bubbles along the back wall that the schools treat as a boundary
    and swim along. It would show off the schooling behaviour.

---

## Suggested order

1. **Kelp through stacked tanks** (#1) and **hanging cosmetics** (#2). They fix the two gaps hit in
   both promo clips: the seam in tall kelp, and empty tops in tall tanks.
2. **Multi-tank structures** (#4), so large tanks get large set pieces.
3. **Hiding caves** (#22), the first cosmetic the fish would actually use, which would make every
   capture livelier.

Status: #1 (kelp through stacked tanks), #2 (hanging cosmetics) and #4 (structures spanning several
tanks: Whale Fall and Drowned Pagoda) shipped 2026-10-01; see
`docs/fish-tanks.md` §6. The rest are ideas only. Upside-down coral (#2) was left out: coral fans
are already floor cosmetics, and nothing yet distinguishes "hang this" from "plant this" for one item.
