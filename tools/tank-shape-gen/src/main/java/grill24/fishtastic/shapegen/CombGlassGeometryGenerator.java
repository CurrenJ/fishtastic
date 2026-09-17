package grill24.fishtastic.shapegen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static grill24.fishtastic.shapegen.TankShapeGeometry.baseModel;
import static grill24.fishtastic.shapegen.TankShapeGeometry.face;
import static grill24.fishtastic.shapegen.TankShapeGeometry.vec3;

/**
 * Glass generator for the "comb" family of tanks (tooth, film), the counterpart to
 * {@link CombFrameGeometryGenerator}: a pane on each closed face, split per Y band into the
 * <em>complement</em> of that band's currently rendered spans (base, plus low if the west/north
 * neighbor is closed, plus high if the east/south neighbor is closed — same gating
 * {@link CombFrameGeometryGenerator} uses), so the translucent glass never sits behind an opaque
 * inlay.
 *
 * <p>The {@link CombTankSpans.Spec#sandRow() sand-row band} is included like any other band, even
 * though the reference images draw that row's non-tooth cells as sand color rather than glass color:
 * the standard sand ({@code SandGeometryGenerator}) only insets to the profile's floor-adjacent
 * width (the glass's inner face), so wherever this row has no frame tooth there's a real 1px gap
 * between the sand's edge and the true block boundary that only glass can fill — the reference
 * simply draws "sand seen through glass" as sand color. Excluding this band left that gap bare and
 * failed {@code TankShapeConnectivitySafetyTest}'s floor-coverage sweep on the film tank.
 *
 * <p>As in {@link BrambleGlassGeometryGenerator}, there's no separate always-on corner post
 * reserving the outer 1px, so the complement is always taken across the full {@code [0,16]} span —
 * the pane reaches the block boundary for free whenever a gated run disappears.
 *
 * <p>Translucency invariant (as in {@link TaperedGlassGeometryGenerator}): only the two
 * thickness-axis faces are defined per segment, never up/down.
 */
public final class CombGlassGeometryGenerator {
    public static final String DEFAULT_TEXTURE = "block/blue_stained_glass";

    private CombGlassGeometryGenerator() {}

    public static JsonObject generate(int permutationIndex, CombTankSpans.Spec spec) {
        return generate(permutationIndex, spec, DEFAULT_TEXTURE);
    }

    public static JsonObject generate(int permutationIndex, CombTankSpans.Spec spec, String textureId) {
        Set<TankFace> openFaces = TankFace.fromPermutationIndex(permutationIndex);
        boolean northClosed = !openFaces.contains(TankFace.NORTH);
        boolean southClosed = !openFaces.contains(TankFace.SOUTH);
        boolean westClosed = !openFaces.contains(TankFace.WEST);
        boolean eastClosed = !openFaces.contains(TankFace.EAST);
        boolean upOpen = openFaces.contains(TankFace.UP);
        boolean downOpen = openFaces.contains(TankFace.DOWN);

        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();

        List<CombTankSpans.Band> bands = new ArrayList<>();
        if (!upOpen) bands.addAll(spec.top());
        if (!downOpen) {
            bands.addAll(spec.bottom());
            if (spec.sandRow() != null) bands.add(spec.sandRow());
        }

        if (northClosed) {
            for (CombTankSpans.Band b : bands) {
                for (int[] span : complement(renderedSpans(b, westClosed, eastClosed), 0, 16)) {
                    elements.add(pane(span[0], b.yFrom(), 0, span[1], b.yTo(), 1, "north", "south"));
                }
            }
        }
        if (southClosed) {
            for (CombTankSpans.Band b : bands) {
                for (int[] span : complement(renderedSpans(b, westClosed, eastClosed), 0, 16)) {
                    elements.add(pane(span[0], b.yFrom(), 15, span[1], b.yTo(), 16, "north", "south"));
                }
            }
        }
        if (westClosed) {
            for (CombTankSpans.Band b : bands) {
                for (int[] span : complement(renderedSpans(b, northClosed, southClosed), 0, 16)) {
                    elements.add(paneZAxis(0, b.yFrom(), span[0], 1, b.yTo(), span[1], "west", "east"));
                }
            }
        }
        if (eastClosed) {
            for (CombTankSpans.Band b : bands) {
                for (int[] span : complement(renderedSpans(b, northClosed, southClosed), 0, 16)) {
                    elements.add(paneZAxis(15, b.yFrom(), span[0], 16, b.yTo(), span[1], "west", "east"));
                }
            }
        }

        addWaistGlassPanes(elements, spec, northClosed, southClosed, westClosed, eastClosed, upOpen, downOpen);

        model.add("elements", elements);
        return model;
    }

    /**
     * The waist band's glass, split at the {@code spec.middleYFromClosed()}/{@code
     * middleYToClosed()} boundary the same way {@code TaperedGlassGeometryGenerator#splitRunForCapBands}/
     * {@code OrnateGlassGeometryGenerator} split a run — within the zone the waist's own Y-range
     * extends into once a cap opens (see {@link CombFrameGeometryGenerator}'s {@code midYFrom}/{@code
     * midYTo}), a corner that would otherwise flush fully to the boundary (its perpendicular face
     * open, so no corner post excludes that span) instead yields to that face's own edge-diagonal
     * beam — see {@link CombFrameGeometryGenerator#generateEdgeFragment}, which reconstructs the
     * comb's asymmetric near-cap teeth over exactly this same widened Y-range.
     *
     * <p>Unlike {@code TaperedGlassGeometryGenerator}'s corner posts (a solid box whose reach-in
     * <em>is</em> its glass-exclusion width), every comb inlay — including the waist's own
     * {@code middleLow}/{@code middleHigh} — is a flush 1-unit-deep plate regardless of how far its
     * span runs along the wall; the beam mirrors that same 1-unit depth. So within the capBand zone,
     * only a single 1px sliver nearest the corner is excluded (matching the beam's actual footprint),
     * not the waist's full along-wall width — excluding the full width here would carve out glass the
     * beam never fills, reopening the exact gap this fix exists to close (confirmed by the tooth
     * shape's capWidth=2 failing {@code TankShapeConnectivitySafetyTest#outerWallSkinHasNoGapsAtAnyBand}
     * at the second cell in from the corner when this used the full width instead).
     */
    private static void addWaistGlassPanes(JsonArray elements, CombTankSpans.Spec spec,
            boolean northClosed, boolean southClosed, boolean westClosed, boolean eastClosed,
            boolean upOpen, boolean downOpen) {
        int midYFrom = downOpen ? 0 : spec.middleYFromClosed();
        int midYTo = upOpen ? 16 : spec.middleYToClosed();

        for (double[] seg : splitForCapBands(midYFrom, midYTo, spec.middleYFromClosed(), spec.middleYToClosed(), upOpen, downOpen)) {
            int yFrom = (int) seg[0], yTo = (int) seg[1];
            if (yFrom >= yTo) continue;
            boolean capBand = seg[2] != 0;

            if (northClosed) {
                List<int[]> excluded = new ArrayList<>();
                excluded.addAll(waistExclusion(spec.middleLow(), true, westClosed, capBand));
                excluded.addAll(waistExclusion(spec.middleHigh(), false, eastClosed, capBand));
                excluded.sort((a, b) -> Integer.compare(a[0], b[0]));
                for (int[] span : complement(excluded, 0, 16)) {
                    elements.add(pane(span[0], yFrom, 0, span[1], yTo, 1, "north", "south"));
                }
            }
            if (southClosed) {
                List<int[]> excluded = new ArrayList<>();
                excluded.addAll(waistExclusion(spec.middleLow(), true, westClosed, capBand));
                excluded.addAll(waistExclusion(spec.middleHigh(), false, eastClosed, capBand));
                excluded.sort((a, b) -> Integer.compare(a[0], b[0]));
                for (int[] span : complement(excluded, 0, 16)) {
                    elements.add(pane(span[0], yFrom, 15, span[1], yTo, 16, "north", "south"));
                }
            }
            if (westClosed) {
                List<int[]> excluded = new ArrayList<>();
                excluded.addAll(waistExclusion(spec.middleLow(), true, northClosed, capBand));
                excluded.addAll(waistExclusion(spec.middleHigh(), false, southClosed, capBand));
                excluded.sort((a, b) -> Integer.compare(a[0], b[0]));
                for (int[] span : complement(excluded, 0, 16)) {
                    elements.add(paneZAxis(0, yFrom, span[0], 1, yTo, span[1], "west", "east"));
                }
            }
            if (eastClosed) {
                List<int[]> excluded = new ArrayList<>();
                excluded.addAll(waistExclusion(spec.middleLow(), true, northClosed, capBand));
                excluded.addAll(waistExclusion(spec.middleHigh(), false, southClosed, capBand));
                excluded.sort((a, b) -> Integer.compare(a[0], b[0]));
                for (int[] span : complement(excluded, 0, 16)) {
                    elements.add(paneZAxis(15, yFrom, span[0], 16, yTo, span[1], "west", "east"));
                }
            }
        }
    }

    /**
     * The span to exclude from a waist glass pane for one side (low/high) of one Y-segment: the
     * waist's real full-width plate when its neighbor is genuinely closed, a 1px sliver nearest the
     * corner (matching the edge-diagonal beam's fixed plate depth) when the neighbor is open but this
     * segment falls in the beam's capBand zone, or nothing otherwise.
     */
    private static List<int[]> waistExclusion(int[][] fullSpan, boolean anchoredAtZero, boolean neighborClosed, boolean capBand) {
        if (neighborClosed) return List.of(fullSpan);
        if (capBand) return List.of(anchoredAtZero ? new int[]{0, 1} : new int[]{15, 16});
        return List.of();
    }

    /**
     * Splits {@code [yFrom,yTo]} at the cap-band boundary(ies) — mirrors {@code
     * TaperedGlassGeometryGenerator#splitRunForCapBands}, generalized off a plain Y-range plus the
     * waist's own closed boundaries instead of a {@code CornerTaperProfile.Run}. Returned as
     * {@code {yFrom, yTo, capBand(0/1)}} triples.
     */
    private static List<double[]> splitForCapBands(int yFrom, int yTo, int middleYFromClosed, int middleYToClosed,
            boolean upOpen, boolean downOpen) {
        double downBandTo = downOpen ? middleYFromClosed : -1;
        double upBandFrom = upOpen ? middleYToClosed : 17;

        java.util.TreeSet<Double> cuts = new java.util.TreeSet<>();
        cuts.add((double) yFrom);
        cuts.add((double) yTo);
        if (downBandTo > yFrom && downBandTo < yTo) cuts.add(downBandTo);
        if (upBandFrom > yFrom && upBandFrom < yTo) cuts.add(upBandFrom);

        List<Double> sorted = new ArrayList<>(cuts);
        List<double[]> result = new ArrayList<>();
        for (int i = 0; i < sorted.size() - 1; i++) {
            double lo = sorted.get(i), hi = sorted.get(i + 1);
            double mid = (lo + hi) / 2;
            boolean capBand = mid < downBandTo || mid > upBandFrom;
            result.add(new double[]{lo, hi, capBand ? 1 : 0});
        }
        return result;
    }

    /**
     * A standalone glass-restore fragment for one end of an eligible edge-diagonal beam — the comb
     * counterpart to {@code TaperedGlassGeometryGenerator#generateEdgeGlassFillFragment}, restoring
     * exactly the 1px sliver {@link #addWaistGlassPanes}'s cap-band split removed from {@code
     * corner}'s "wall" side when the edge-diagonal neighbor cell IS filled (so the beam itself does
     * not render there). Spans the full widened capBand Y-range (from {@code
     * spec.middleYToClosed()}/{@code middleYFromClosed()} to the block boundary — matching {@code
     * addWaistGlassPanes}'s split, not just the beam's own 1px vertical thickness at the true cap),
     * since that's the whole zone the inset actually removed.
     */
    public static JsonObject generateEdgeGlassFillFragment(TankEdge edge, TankCorner corner, CombTankSpans.Spec spec) {
        return generateEdgeGlassFillFragment(edge, corner, spec, DEFAULT_TEXTURE);
    }

    public static JsonObject generateEdgeGlassFillFragment(TankEdge edge, TankCorner corner, CombTankSpans.Spec spec, String textureId) {
        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();

        int y1 = edge.vertical() == TankFace.DOWN ? 0 : spec.middleYToClosed();
        int y2 = edge.vertical() == TankFace.DOWN ? spec.middleYFromClosed() : 16;

        TankFace wall = corner.faceA() == edge.horizontal() ? corner.faceB() : corner.faceA();

        switch (wall) {
            case NORTH -> {
                int x1 = corner.xEdge() == 1 ? 15 : 0;
                int x2 = corner.xEdge() == 1 ? 16 : 1;
                elements.add(pane(x1, y1, 0, x2, y2, 1, "north", "south"));
            }
            case SOUTH -> {
                int x1 = corner.xEdge() == 1 ? 15 : 0;
                int x2 = corner.xEdge() == 1 ? 16 : 1;
                elements.add(pane(x1, y1, 15, x2, y2, 16, "north", "south"));
            }
            case WEST -> {
                int z1 = corner.zEdge() == 1 ? 15 : 0;
                int z2 = corner.zEdge() == 1 ? 16 : 1;
                elements.add(paneZAxis(0, y1, z1, 1, y2, z2, "west", "east"));
            }
            case EAST -> {
                int z1 = corner.zEdge() == 1 ? 15 : 0;
                int z2 = corner.zEdge() == 1 ? 16 : 1;
                elements.add(paneZAxis(15, y1, z1, 16, y2, z2, "west", "east"));
            }
            default -> throw new IllegalArgumentException("Not a wall face: " + wall);
        }

        model.add("elements", elements);
        return model;
    }

    /** The spans that actually render for this band given whether the low/high neighbor is closed. */
    private static List<int[]> renderedSpans(CombTankSpans.Band band, boolean lowClosed, boolean highClosed) {
        List<int[]> result = new ArrayList<>();
        for (int[] span : band.base()) result.add(span);
        if (lowClosed) {
            for (int[] span : band.low()) result.add(span);
        }
        if (highClosed) {
            for (int[] span : band.high()) result.add(span);
        }
        result.sort((a, b) -> Integer.compare(a[0], b[0]));
        return result;
    }

    /** The complement of the given spans within [min,max]. Spans must be sorted and non-overlapping. */
    private static List<int[]> complement(List<int[]> spans, int min, int max) {
        List<int[]> result = new ArrayList<>();
        int cursor = min;
        for (int[] span : spans) {
            if (span[0] > cursor) {
                result.add(new int[]{cursor, span[0]});
            }
            cursor = Math.max(cursor, span[1]);
        }
        if (cursor < max) {
            result.add(new int[]{cursor, max});
        }
        return result;
    }

    /** North/south-facing pane segment (thickness along Z). */
    private static JsonObject pane(int x1, int y1, int z1, int x2, int y2, int z2, String faceA, String faceB) {
        JsonObject element = new JsonObject();
        element.add("from", vec3(x1, y1, z1));
        element.add("to", vec3(x2, y2, z2));

        JsonObject faces = new JsonObject();
        faces.add(faceA, face(x1, y1, x2, y2, "#all"));
        faces.add(faceB, face(x1, y1, x2, y2, "#all"));
        element.add("faces", faces);
        return element;
    }

    /** West/east-facing pane segment (thickness along X). */
    private static JsonObject paneZAxis(int x1, int y1, int z1, int x2, int y2, int z2, String faceA, String faceB) {
        JsonObject element = new JsonObject();
        element.add("from", vec3(x1, y1, z1));
        element.add("to", vec3(x2, y2, z2));

        JsonObject faces = new JsonObject();
        faces.add(faceA, face(z1, y1, z2, y2, "#all"));
        faces.add(faceB, face(z1, y1, z2, y2, "#all"));
        element.add("faces", faces);
        return element;
    }
}
