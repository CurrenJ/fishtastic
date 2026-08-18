package grill24.fishtastic.shapegen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Set;

import static grill24.fishtastic.shapegen.TankShapeGeometry.addSingleGroup;
import static grill24.fishtastic.shapegen.TankShapeGeometry.face;
import static grill24.fishtastic.shapegen.TankShapeGeometry.vec3;

/**
 * Sand generator for the dune tank (image {@code proposed_shapes_6_wide_modified.png} tank slot 5,
 * 0-indexed): standard 1px frame/glass ({@link CornerTaperProfile#STANDARD}, reused byte-for-byte —
 * dune's only novelty is the sand), with a two-step raised "hill" stacked on top of the ordinary
 * base sand layer.
 *
 * <p>Row transcription: base sand is row 14 (as STANDARD's), with two extra steps —
 * row 13 ({@code x=[4,12)}, MC Y-band [2,3]) and row 12 ({@code x=[6,10)}, MC Y-band [3,4]). The
 * image only gives one side profile, so the hill's Z-extent is <b>assumed</b> to mirror its X-extent
 * (a symmetric dome, not a ridge) — this is not verifiable from the reference and is called out in
 * the shipping report.
 *
 * <p><b>Connection behavior:</b> each hill step is a single box per permutation whose X/Z bounds
 * extend to the block boundary on whichever cardinal faces are open (west/east open widens X,
 * north/south open widens Z), independently per axis. Unlike the base sand layer's inset/extension
 * decomposition (many small boxes bridging a taper), this needs none of that: the hill step never
 * touches the true corner or needs bridging, it's a single rectangular volume whose bounds are
 * computed directly from which faces are open — so with all four horizontal faces open, both hill
 * steps' bounds become the full {@code [0,16)} footprint, i.e. an entirely flat, raised sand surface
 * at the hill's top height, per the spec.
 *
 * <p><b>Known interaction not resolved here</b> (flagged per the task, not silently fixed): the
 * fish-tank render/cosmetic pipeline (see {@code CosmeticGridCell}'s {@code SAND_LAYER_PIXELS} /
 * {@code FishTankBlockEntityRenderer}'s {@code SAND_BASE_Y_OFFSET}) assumes a single flat sand
 * surface at Y=2/16 everywhere, used to float floor-anchored fish and cosmetics just above it. Dune's
 * hill raises the surface to Y=3/16 or Y=4/16 in the raised region (and, when connected on all four
 * sides, everywhere). Floor-anchored fish and floor-placed cosmetics over the hill will render
 * partially submerged in it rather than resting on its actual top. This is a rendering-accuracy gap,
 * not a structural safety issue (the sweep test only checks frame/glass/sand volumes and floor/wall
 * skin coverage, which this generator satisfies) — left for the user to decide whether to accept,
 * scope the hill down, or make the render pipeline sand-height-aware.
 */
public final class DuneSandGeometryGenerator {
    public static final String DEFAULT_TEXTURE = "block/sand";

    private DuneSandGeometryGenerator() {}

    public static JsonObject generate(int permutationIndex) {
        return generate(permutationIndex, DEFAULT_TEXTURE);
    }

    public static JsonObject generate(int permutationIndex, String textureId) {
        JsonObject model = SandGeometryGenerator.generate(permutationIndex, textureId, CornerTaperProfile.STANDARD);

        Set<TankFace> openFaces = TankFace.fromPermutationIndex(permutationIndex);
        if (!openFaces.contains(TankFace.DOWN)) {
            boolean west = openFaces.contains(TankFace.WEST);
            boolean east = openFaces.contains(TankFace.EAST);
            boolean north = openFaces.contains(TankFace.NORTH);
            boolean south = openFaces.contains(TankFace.SOUTH);

            JsonArray elements = model.getAsJsonArray("elements");
            elements.add(hillStep("hill_step1", 2, 3,
                    west ? 0 : 4, east ? 16 : 12, north ? 0 : 4, south ? 16 : 12));
            elements.add(hillStep("hill_step2", 3, 4,
                    west ? 0 : 6, east ? 16 : 10, north ? 0 : 6, south ? 16 : 10));
        }

        addSingleGroup(model, "sand_" + permutationIndex);
        return model;
    }

    private static JsonObject hillStep(String name, int y1, int y2, int x1, int x2, int z1, int z2) {
        JsonObject element = new JsonObject();
        element.addProperty("name", name);
        element.add("from", vec3(x1, y1, z1));
        element.add("to", vec3(x2, y2, z2));

        JsonObject faces = new JsonObject();
        faces.add("north", face(x1, 14, x2, 15, "#all"));
        faces.add("south", face(x1, 14, x2, 15, "#all"));
        faces.add("west", face(z1, 14, z2, 15, "#all"));
        faces.add("east", face(z1, 14, z2, 15, "#all"));
        faces.add("up", face(x1, z1, x2, z2, "#all"));
        faces.add("down", face(x1, z1, x2, z2, "#all"));
        element.add("faces", faces);
        return element;
    }
}
