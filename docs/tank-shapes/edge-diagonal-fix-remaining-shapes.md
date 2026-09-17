# Edge-diagonal frame beam fix — remaining shapes

The edge-diagonal frame beam fix (see the corresponding plan doc and `FishTankShape#hasEdgeDiagonalFragments`)
now covers every shape except **bramble**: the 13 original taper/shell-family shapes,
**ornate, shaggy, and creeper** (added once inspection confirmed their corner post is byte-formula-identical
to `CornerTaperProfile.STANDARD` — `floorClosed?1:0` / `ceilingClosed?15:16`, same as
`TaperedFrameGeometryGenerator`'s own support box — so their edge fragments delegate to the shared tapered
generator directly, with the same cap-band glass split hand-duplicated into their own glass generators), and
**tooth, film, lattice, mullion, and arch**, each with bespoke geometry (see below).

Only one shape remains excluded (`NO_EDGE_DIAGONAL_FRAGMENTS`):

## bramble — unchanged, permanently excluded

Confirmed no uniform corner post exists at any Y band — every band is an independently-tuned,
asymmetric thorn pattern (`BrambleTankSpans`). There's no sub-box to extract as a reusable fragment;
this one stays excluded on the original reasoning.

## tooth / film — CombFrameGeometryGenerator family (done)

`CombFrameGeometryGenerator#generateEdgeFragment` reconstructs the near-cap `top()`/`bottom()` band
list directly on `edge.horizontal()`'s face — base+low+high all rendered unconditionally, since
eligibility already requires that face open (the ordinary per-permutation gating, which requires the
face closed before drawing anything, can't be reused as-is). The band nearest the vacated cap is
extended flush to the block boundary (mirroring `CornerTaperProfile.runs()`'s cap-extension
convention) **and flattened to a solid full-width strip** rather than kept as its own (possibly
perforated) pattern — tooth's cap-adjacent rows are already solid, but film's are a period-2
perforated comb with no neighbor-gated content to fall back to, and there is no complementary
mechanism to show glass through the holes on a face that's fully open. This widens the reconstructed
zone to exactly `[spec.middleYToClosed(), 16]` (or `[0, spec.middleYFromClosed()]` for a DOWN edge).

`CombGlassGeometryGenerator#addWaistGlassPanes` splits the waist band's glass at that same
`middleYFromClosed()`/`middleYToClosed()` boundary and, within it, excludes only a **1px sliver**
nearest the corner from the perpendicular walls' own glass — not the waist's full along-wall width.
This was the actual bug found empirically: every comb inlay (including the waist's own
`middleLow`/`middleHigh`) is a flush **1-unit-deep plate** regardless of how far its span runs along
the wall, and the beam mirrors that same 1-unit depth — excluding the full waist width left a second
cell's glass carved out with nothing to fill it (`TankShapeConnectivitySafetyTest#outerWallSkinHasNoGapsAtAnyBand`
caught this on tooth, whose waist width is 2). `generateEdgeGlassFillFragment` restores exactly that
sliver, over the widened Y-range, when the diagonal neighbor is filled instead.

## lattice — LatticeFrameGeometryGenerator (done)

`LatticeFrameGeometryGenerator#generateEdgeFragment` uses a constant-width-2 box (`CAP_EDGE_WIDTH`,
matching the cap rows' own fixed width — `addEdge`'s `w = wide ? 2 : 1` never narrows with
`upOpen`/`downOpen`) instead of borrowing `CornerTaperProfile#baseWidth()`. The matching glass-fill
fragment (`LatticeGlassGeometryGenerator#generateEdgeGlassFillFragment`) is an empty no-op:
`LatticeGlassGeometryGenerator`'s cap-row bands already exclude the `[0,width)`/`[16-width,16)`
corner columns from glass *unconditionally*, mirroring how `LatticeFrameGeometryGenerator#addEdge`
renders both of a closed face's corner columns unconditionally too — so the beam's full-width reach
never overlaps real glass at any permutation, and there is nothing to restore.

**It must still be emitted, though.** Lattice originally shipped with *no* glass-fill fragments at
all, and the client loader (`FishTankBlockStateModel#resolveDependencies` on both platforms) loads
all 16 `fish_tank_glass_fill_*` models for every shape in `FishTankShape#hasEdgeDiagonalFragments()`
regardless. The missing files resolved to vanilla's missing model and were composited in as a full
purple/black cube whenever a lattice tank had an eligible edge whose edge-diagonal cell was filled
(2026-09-17). Two guardrails now pin the contract: `TankShapeConnectivitySafetyTest#everyShapeWithEdgeFragmentsAlsoHasEdgeGlassFillFragments`
(strategy table) and `FishTankShapeModelAssetsTest` in `common` (checked-in files vs. loader flags).

## mullion — MullionFrameGeometryGenerator (done)

Mullion has no combined-face corner gate at all (anchor bars are unconditional; the far wall gates on
a single perpendicular face independently), so the "gap in the middle of an open wall" problem this
fix addresses is universal and cap-band-agnostic. `MullionFrameGeometryGenerator#generateEdgeFragment`
uses a plain 1px seal — the same shape `TaperedFrameGeometryGenerator`'s STANDARD profile would
produce, ignoring mullion's own bar positions entirely — uniformly on all 8 `TankEdge` values.

Only `edge.horizontal() == EAST`/`SOUTH` ever collides with real glass: those are the wall-gated
corners (north/south faces' wall at local `x=15` gates on EAST closed; west/east faces' wall at local
`z=15` gates on SOUTH closed), where the wall can legitimately be absent and its face's own glass
flushes into the corner. The anchor side (`NORTH`/`WEST`-horizontal edges) never needs a fix — the
anchor's own frame box unconditionally occupies that cell regardless of the perpendicular face's
state, so no glass ever flushes there in the first place.
`MullionGlassGeometryGenerator#generateEdgeGlassFillFragment` is accordingly a no-op for the other
four edges, and `generate()`'s wall-gated `complement()` calls (not the anchor-gated ones) got a
cap-band split — fixed at width 1, matching the beam — mirroring `TaperedGlassGeometryGenerator#splitRunForCapBands`.

## arch — ArchFrameGeometryGenerator / ArchGlassGeometryGenerator (done)

Arch's jamb is a plain rectangular post whose width only varies by height band (1px near the ceiling,
2px near the floor — `ArchTankSpans.NEAR_CEILING_JAMB_WIDTH`/`NEAR_FLOOR_JAMB_WIDTH`), unlike the comb
family's asymmetric per-row teeth, so `ArchFrameGeometryGenerator#generateEdgeFragment` is a single
uniform box at the near-cap row's own width — mirroring `TaperedFrameGeometryGenerator#createEdgeBeamBox`
exactly (full 0-16 perpendicular reach).

Unlike mullion, arch has no always-present anchor side — every one of its 8 edges is an ordinary
gated corner post at both the low *and* high end of the wall — so a beam at any eligible edge can
collide with the perpendicular walls' own glass, which flush-extends into the corner whenever that
wall's far jamb is removed (its own perpendicular face opened). `ArchTankSpans#glassBands` (used only
by the glass generator, not the frame generator) fixes this by forcing both jamb ends "present" within
just the narrow band nearest an open cap — `[0, NEAR_FLOOR_JAMB_WIDTH)`/`[16-NEAR_CEILING_JAMB_WIDTH, 16)`
— regardless of the real open state, splitting the near-floor row (taller than its own cap band) at
that boundary so only the affected slice is forced. `ArchGlassGeometryGenerator#generateEdgeGlassFillFragment`
restores exactly that withheld sliver — at whichever of an edge's two end corners has an actually-closed
wall — when the diagonal neighbor is filled instead (both end corners can need a restore, not just one).

## Priority if picked up later

bramble is permanently excluded; nothing else remains.
