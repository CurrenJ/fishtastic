package grill24.fishtastic.shapegen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static grill24.fishtastic.shapegen.TankShapeGeometry.addSingleGroup;
import static grill24.fishtastic.shapegen.TankShapeGeometry.baseModel;
import static grill24.fishtastic.shapegen.TankShapeGeometry.face;
import static grill24.fishtastic.shapegen.TankShapeGeometry.vec3;

/**
 * Frame generator for the "comb" family of tanks (tooth, film) — see {@link CombTankSpans} for the
 * data shape and how it was read off each shape's reference image. Takes a {@link CombTankSpans.Spec}
 * rather than being subclassed per shape, the same reuse pattern {@link ShellFrameGeometryGenerator}
 * already uses across four tapered shapes via {@link CornerTaperProfile}.
 *
 * <p>Structurally close to {@link BrambleFrameGeometryGenerator}: gating is per-band low/high runs
 * with nothing added back when a run's neighbor opens, plus (the one thing bramble doesn't need) an
 * unconditional {@code base} component that renders regardless of the adjacent corner's state.
 *
 * <p>The top/bottom comb zones exist only while their cap is closed and vanish entirely — no
 * replacement pattern — when it opens, matching {@code ShaggyFrameGeometryGenerator}'s TOP/BOTTOM
 * treatment; neither reference image includes a vertical-connection reference the way bramble's
 * does, so there's no observed "cap open" pattern to reproduce. The waist corner post between them
 * extends to fill the vacated space (down to {@code y=0} / up to {@code y=16}) so two stacked tanks
 * still meet flush at the seam — the same seam-extension principle {@code CornerTaperProfile.runs()}
 * applies to a plain taper.
 */
public final class CombFrameGeometryGenerator {
    public static final String DEFAULT_TEXTURE = "block/oak_planks";

    private CombFrameGeometryGenerator() {}

    public static JsonObject generate(int permutationIndex, CombTankSpans.Spec spec) {
        return generate(permutationIndex, spec, DEFAULT_TEXTURE);
    }

    public static JsonObject generate(int permutationIndex, CombTankSpans.Spec spec, String textureId) {
        Set<TankFace> openFaces = TankFace.fromPermutationIndex(permutationIndex);
        boolean upOpen = openFaces.contains(TankFace.UP);
        boolean downOpen = openFaces.contains(TankFace.DOWN);

        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();

        if (!upOpen) {
            elements.add(createCap("ceiling", 15, 16, openFaces, true));
        }
        if (!downOpen) {
            elements.add(createCap("floor", 0, 1, openFaces, false));
        }

        if (!upOpen) {
            for (CombTankSpans.Band band : spec.top()) {
                addFaceInlays(elements, openFaces, band);
            }
        }
        if (!downOpen) {
            for (CombTankSpans.Band band : spec.bottom()) {
                addFaceInlays(elements, openFaces, band);
            }
            if (spec.sandRow() != null) {
                addFaceInlays(elements, openFaces, spec.sandRow());
            }
        }

        int midYFrom = downOpen ? 0 : spec.middleYFromClosed();
        int midYTo = upOpen ? 16 : spec.middleYToClosed();
        addFaceInlays(elements, openFaces,
                new CombTankSpans.Band(midYFrom, midYTo, new int[0][], spec.middleLow(), spec.middleHigh()));

        model.add("elements", elements);
        addSingleGroup(model, "frame_" + permutationIndex);
        return model;
    }

    /**
     * A standalone edge-diagonal frame-beam fragment for {@code edge} — see the edge-diagonal frame
     * beam fix design doc and {@code docs/tank-shapes/edge-diagonal-fix-remaining-shapes.md}'s
     * tooth/film writeup. Unlike {@link TaperedFrameGeometryGenerator#generateEdgeFragment}'s single
     * uniform-width box, the comb family's near-cap "teeth" bands carry their own asymmetric
     * low/high pixels per row (e.g. tooth's row just above the waist is 1px on the low side but 2px
     * on the high side) that {@link CornerTaperProfile.Run} can't represent — so this reconstructs
     * the actual {@code top()}/{@code bottom()} band list directly on {@code edge.horizontal()}'s
     * face, base+low+high all rendered unconditionally (eligibility already requires that face open,
     * so the ordinary per-permutation gating, which requires the face closed before drawing anything,
     * can't be reused as-is).
     *
     * <p>The band nearest the vacated cap is extended flush to the block boundary and rendered as a
     * plain solid strip (not its own actual pattern) — mirroring {@link CornerTaperProfile#runs}'s
     * cap-extension convention, so two stacked tanks meet with no gap at the seam. This widens the
     * reconstructed zone to exactly {@code [spec.middleYToClosed(), 16]} (or {@code [0,
     * spec.middleYFromClosed()]} for a DOWN edge) — the same boundary {@link
     * CombGlassGeometryGenerator}'s cap-band split targets on the perpendicular walls' own glass, so
     * the two fixes cover exactly the same volume. Solidifying rather than reconstructing this one
     * row's real pattern is deliberate: film's near-cap rows are period-2 perforated combs with no
     * neighbor-gated content at all, so there is no "as if closed" version of them to fall back to —
     * their holes are meant to show glass, but the open face this beam substitutes for has no glass
     * of its own to show through (the face itself is open), so a solid strip is the only option that
     * doesn't leave a real void at the hole positions.
     */
    public static JsonObject generateEdgeFragment(TankEdge edge, CombTankSpans.Spec spec) {
        return generateEdgeFragment(edge, spec, DEFAULT_TEXTURE);
    }

    public static JsonObject generateEdgeFragment(TankEdge edge, CombTankSpans.Spec spec, String textureId) {
        JsonObject model = baseModel(textureId);
        JsonArray elements = new JsonArray();

        List<CombTankSpans.Band> bands = new ArrayList<>();
        if (edge.vertical() == TankFace.UP) {
            bands.addAll(spec.top());
        } else {
            bands.addAll(spec.bottom());
            if (spec.sandRow() != null) bands.add(spec.sandRow());
        }

        if (!bands.isEmpty()) {
            int i = edge.vertical() == TankFace.UP ? 0 : bands.size() - 1;
            CombTankSpans.Band b = bands.get(i);
            bands.set(i, edge.vertical() == TankFace.UP
                    ? new CombTankSpans.Band(b.yFrom(), 16, new int[][]{{0, 16}})
                    : new CombTankSpans.Band(0, b.yTo(), new int[][]{{0, 16}}));
        }

        for (CombTankSpans.Band band : bands) {
            addClosedFaceInlaySpans(elements, edge.horizontal(), band.yFrom(), band.yTo(), band.base());
            addClosedFaceInlaySpans(elements, edge.horizontal(), band.yFrom(), band.yTo(), band.low());
            addClosedFaceInlaySpans(elements, edge.horizontal(), band.yFrom(), band.yTo(), band.high());
        }

        model.add("elements", elements);
        addSingleGroup(model, "frame_edge_" + edge.name().toLowerCase());
        return model;
    }

    /** One band's spans drawn unconditionally on a single named face — see {@link #generateEdgeFragment}. */
    private static void addClosedFaceInlaySpans(JsonArray elements, TankFace face, int y1, int y2, int[][] spans) {
        for (int[] span : spans) {
            switch (face) {
                case NORTH -> elements.add(createBox("edge_n_" + y1 + "_" + span[0], span[0], y1, 0, span[1], y2, 1));
                case SOUTH -> elements.add(createBox("edge_s_" + y1 + "_" + span[0], span[0], y1, 15, span[1], y2, 16));
                case WEST -> elements.add(createBox("edge_w_" + y1 + "_" + span[0], 0, y1, span[0], 1, y2, span[1]));
                case EAST -> elements.add(createBox("edge_e_" + y1 + "_" + span[0], 15, y1, span[0], 16, y2, span[1]));
                default -> throw new IllegalArgumentException("Not a horizontal face: " + face);
            }
        }
    }

    /** Adds one band's base/low/high spans as 1px plates on each closed face. */
    private static void addFaceInlays(JsonArray elements, Set<TankFace> openFaces, CombTankSpans.Band band) {
        boolean northClosed = !openFaces.contains(TankFace.NORTH);
        boolean southClosed = !openFaces.contains(TankFace.SOUTH);
        boolean westClosed = !openFaces.contains(TankFace.WEST);
        boolean eastClosed = !openFaces.contains(TankFace.EAST);

        int y1 = band.yFrom();
        int y2 = band.yTo();

        // North/south inlays run along X: base is unconditional, low is additionally west-gated,
        // high additionally east-gated.
        if (northClosed || southClosed) {
            for (int[] span : band.base()) {
                if (northClosed) elements.add(createBox("inlay_n_" + y1 + "_" + span[0], span[0], y1, 0, span[1], y2, 1));
                if (southClosed) elements.add(createBox("inlay_s_" + y1 + "_" + span[0], span[0], y1, 15, span[1], y2, 16));
            }
            if (westClosed) {
                for (int[] span : band.low()) {
                    if (northClosed) elements.add(createBox("inlay_n_" + y1 + "_" + span[0], span[0], y1, 0, span[1], y2, 1));
                    if (southClosed) elements.add(createBox("inlay_s_" + y1 + "_" + span[0], span[0], y1, 15, span[1], y2, 16));
                }
            }
            if (eastClosed) {
                for (int[] span : band.high()) {
                    if (northClosed) elements.add(createBox("inlay_n_" + y1 + "_" + span[0], span[0], y1, 0, span[1], y2, 1));
                    if (southClosed) elements.add(createBox("inlay_s_" + y1 + "_" + span[0], span[0], y1, 15, span[1], y2, 16));
                }
            }
        }
        // West/east inlays run along Z: base is unconditional, low is additionally north-gated,
        // high additionally south-gated.
        if (westClosed || eastClosed) {
            for (int[] span : band.base()) {
                if (westClosed) elements.add(createBox("inlay_w_" + y1 + "_" + span[0], 0, y1, span[0], 1, y2, span[1]));
                if (eastClosed) elements.add(createBox("inlay_e_" + y1 + "_" + span[0], 15, y1, span[0], 16, y2, span[1]));
            }
            if (northClosed) {
                for (int[] span : band.low()) {
                    if (westClosed) elements.add(createBox("inlay_w_" + y1 + "_" + span[0], 0, y1, span[0], 1, y2, span[1]));
                    if (eastClosed) elements.add(createBox("inlay_e_" + y1 + "_" + span[0], 15, y1, span[0], 16, y2, span[1]));
                }
            }
            if (southClosed) {
                for (int[] span : band.high()) {
                    if (westClosed) elements.add(createBox("inlay_w_" + y1 + "_" + span[0], 0, y1, span[0], 1, y2, span[1]));
                    if (eastClosed) elements.add(createBox("inlay_e_" + y1 + "_" + span[0], 15, y1, span[0], 16, y2, span[1]));
                }
            }
        }
    }

    private static JsonObject createCap(String name, int y1, int y2, Set<TankFace> openFaces, boolean ceiling) {
        JsonObject element = new JsonObject();
        element.addProperty("name", name);
        element.add("from", vec3(0, y1, 0));
        element.add("to", vec3(16, y2, 16));

        JsonObject faces = new JsonObject();
        if (!openFaces.contains(TankFace.NORTH)) faces.add("north", face(0, ceiling ? 0 : 15, 16, ceiling ? 1 : 16, "#all"));
        if (!openFaces.contains(TankFace.EAST)) faces.add("east", face(0, ceiling ? 0 : 15, 16, ceiling ? 1 : 16, "#all"));
        if (!openFaces.contains(TankFace.SOUTH)) faces.add("south", face(0, ceiling ? 0 : 15, 16, ceiling ? 1 : 16, "#all"));
        if (!openFaces.contains(TankFace.WEST)) faces.add("west", face(0, ceiling ? 0 : 15, 16, ceiling ? 1 : 16, "#all"));
        faces.add("up", face(0, 0, 16, 16, "#all"));
        faces.add("down", face(0, 0, 16, 16, "#all"));
        element.add("faces", faces);
        return element;
    }

    private static JsonObject createBox(String name, int x1, int y1, int z1, int x2, int y2, int z2) {
        JsonObject element = new JsonObject();
        element.addProperty("name", name);
        element.add("from", vec3(x1, y1, z1));
        element.add("to", vec3(x2, y2, z2));

        JsonObject faces = new JsonObject();
        faces.add("north", face(16 - x2, 16 - y2, 16 - x1, 16 - y1, "#all"));
        faces.add("south", face(x1, 16 - y2, x2, 16 - y1, "#all"));
        faces.add("west", face(z1, 16 - y2, z2, 16 - y1, "#all"));
        faces.add("east", face(16 - z2, 16 - y2, 16 - z1, 16 - y1, "#all"));
        faces.add("up", face(x1, z1, x2, z2, "#all"));
        faces.add("down", face(x1, 16 - z2, x2, 16 - z1, "#all"));
        element.add("faces", faces);

        return element;
    }
}
