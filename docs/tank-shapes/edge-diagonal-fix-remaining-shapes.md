# Edge-diagonal frame beam fix — remaining shapes

The edge-diagonal frame beam fix (see the corresponding plan doc and `FishTankShape#hasEdgeDiagonalFragments`)
now covers all shapes except **bramble** and **arch**: the 13 original taper/shell-family shapes,
**ornate, shaggy, and creeper** (added once inspection confirmed their corner post is byte-formula-identical
to `CornerTaperProfile.STANDARD` — `floorClosed?1:0` / `ceilingClosed?15:16`, same as
`TaperedFrameGeometryGenerator`'s own support box — so their edge fragments delegate to the shared tapered
generator directly, with the same cap-band glass split hand-duplicated into their own glass generators), and
now **tooth, film, lattice, and mullion**, each with bespoke geometry (see below).

Two shapes remain excluded (`NO_EDGE_DIAGONAL_FRAGMENTS`):

## bramble — unchanged, permanently excluded

Confirmed no uniform corner post exists at any Y band — every band is an independently-tuned,
asymmetric thorn pattern (`BrambleTankSpans`). There's no sub-box to extract as a reusable fragment;
this one stays excluded on the original reasoning.

## arch — not yet attempted

A capped-height jamb breaks the shared profile-based abstraction; no bespoke implementation attempted yet.

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
`upOpen`/`downOpen`) instead of borrowing `CornerTaperProfile#baseWidth()`. No matching glass-fill
fragment is needed: `LatticeGlassGeometryGenerator`'s cap-row bands already exclude the
`[0,width)`/`[16-width,16)` corner columns from glass *unconditionally*, mirroring how
`LatticeFrameGeometryGenerator#addEdge` renders both of a closed face's corner columns unconditionally
too — so the beam's full-width reach never overlaps real glass at any permutation.

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

## Priority if picked up later

arch is the only shape left; bramble stays permanently excluded.
