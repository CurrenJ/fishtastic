# Edge-diagonal frame beam fix — remaining shapes

The edge-diagonal frame beam fix (see the corresponding plan doc and `FishTankShape#hasEdgeDiagonalFragments`)
now covers 16 of 19 shapes: the 13 original taper/shell-family shapes plus **ornate, shaggy, and
creeper**, added once inspection confirmed their corner post is byte-formula-identical to
`CornerTaperProfile.STANDARD` (`floorClosed?1:0` / `ceilingClosed?15:16`, same as
`TaperedFrameGeometryGenerator`'s own support box) — their edge fragments and glass-fill fragments
simply delegate to the shared tapered generator with `CornerTaperProfile.STANDARD`, and their own
glass generators got the same cap-band split `TaperedGlassGeometryGenerator` uses (hand-duplicated,
since each has its own glass generator for bracket/inlay holes).

Three shapes remain excluded (`NO_EDGE_DIAGONAL_FRAGMENTS`), each for a distinct structural reason —
none of them is "no uniform border exists," which turned out to be true only for bramble. All three
have a corner post that seam-extends correctly (matching `CornerTaperProfile.runs()`'s cap-extension
convention), which is what makes them worth revisiting rather than writing off — the blocker in each
case is that the shared `CornerTaperProfile`/`TaperedFrameGeometryGenerator` abstraction doesn't quite
fit their geometry, not that no fix is possible.

## tooth / film — CombFrameGeometryGenerator family

Initial read of the code (without checking in-game) suggested no seam-extension existed at all for
these two. That was wrong on the seam-extension point — the user confirmed in-game that DOWN/UP-open
states render correctly, and closer reading confirms why: `CombFrameGeometryGenerator`'s "waist"
middle band (`spec.middleYFromClosed()`/`middleYToClosed()`, extended to `0`/`16` exactly like
`CornerTaperProfile.runs()`) is a plain corner post — width 1 for film
(`FilmTankSpans.SPEC.middleLow()/middleHigh()` are both 1px), width 2 for tooth (both 2px) — gated
on both adjacent faces closed, same as every other shape here.

The blocker is different: the corner post's width is **not constant across the full height** the way
STANDARD's is. Rows immediately above/below the waist (the "teeth" zone) carry their own **asymmetric**
low/high corner-adjacent pixels — e.g. tooth's row just above the waist is 1px on the low side but 2px
on the high side. `CornerTaperProfile.Run` assumes one symmetric width per row (mirrored on both
corner sides), so it cannot represent this asymmetry. A corner/edge fragment for tooth or film needs
bespoke code in `CombFrameGeometryGenerator` that reconstructs the exact per-row low/high spans (already
present in `CombTankSpans.Band.low()/high()`) for a synthetic "as if both closed" state, rather than a
`CornerTaperProfile.uniform(width)` reuse. The glass side (`CombGlassGeometryGenerator`) needs the same
cap-band split already applied to ornate/shaggy/creeper, targeted at the waist band specifically
(`spec.middleYFromClosed()`/`middleYToClosed()`), which is comparatively straightforward — the hard part
is the frame's asymmetric per-row reconstruction.

## mullion — MullionFrameGeometryGenerator

Not a symmetric "post gated on both perpendicular faces" shape at all. Each face independently draws:
- an **anchor** bar at its low edge (`x=0`/`z=0`), unconditional on the perpendicular face's state, and
- a **wall** bar at its high edge (`x=15`/`z=15`), gated on *both* this face and its high-side neighbor
  (north/south faces gate on EAST; west/east faces gate on SOUTH — never NORTH or WEST).

This asymmetry means the edge-diagonal beam is needed uniformly on all 8 `TankEdge` directions (the
"gap in the middle of an open wall" problem is universal, independent of anchor/wall), but the
glass-flush z-fight only exists on the **wall**-gated corners (`EAST_*`/`SOUTH_*` edges) — the anchor
sides never flush their glass into the corner (the anchor's own frame box unconditionally occupies
that cell, whichever perpendicular face is closed). Reusing `TaperedGlassGeometryGenerator`'s generic
restore fragment for *all* edges would incorrectly composite a phantom glass patch on top of the
anchor's always-present frame box for `NORTH_*`/`WEST_*` edges. A correct implementation needs a
mullion-specific `generateEdgeGlassFillFragment` that returns empty geometry unless
`edge.horizontal()` is EAST or SOUTH, plus a matching patch to `MullionGlassGeometryGenerator`'s
wall-gated `complement()` calls only (not the anchor-gated ones).

## lattice — LatticeFrameGeometryGenerator

Also seam-extends correctly, and unlike mullion, both corners of each closed face render
*unconditionally* (regardless of the perpendicular face's state) — meaning a full-length edge beam
would only ever overlap lattice's own frame (opaque-on-opaque, tolerated), never its glass. That part
is clean.

The blocker: lattice's cap rows (row 1 / row 14) are drawn at a **fixed 2px width regardless of
whether the adjacent cap is open** (`addEdge`'s `w = wide ? 2 : 1` never changes with `upOpen`/
`downOpen` — only the row's Y-extent changes). Every other shape's cap-adjacent row *narrows* to the
profile's steady-state width when its cap opens (`CornerTaperProfile.effectiveRowWidths`), which is
exactly what makes `TaperedFrameGeometryGenerator.generateEdgeFragment`'s single-box-at-`baseWidth()`
simplification valid. Reusing that generator for lattice would produce a beam 1px too narrow at the
cap band, leaving a sliver gap along one side. A lattice edge fragment needs its own 2px-wide box
matching its actual (non-narrowing) cap-row width.

## bramble — unchanged, permanently excluded

Confirmed no uniform corner post exists at any Y band — every band is an independently-tuned,
asymmetric thorn pattern (`BrambleTankSpans`). There's no sub-box to extract as a reusable fragment;
this one stays excluded on the original reasoning.

## Priority if picked up later

tooth/film and lattice are probably the next-easiest (bounded, well-understood asymmetry/width
mismatch, no cross-cutting gating logic to redesign). Mullion needs the most new plumbing (a
genuinely different glass-fill gating rule per edge direction, not just a wider profile).
