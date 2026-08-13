package grill24.fishtastic.shapepreviewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;

/**
 * Converts the Minecraft block-model JSON produced by the tank-shape-gen generators (0-16
 * pixel-space cuboid "elements", each with a "faces" map naming which sides are present) into a
 * single JavaFX {@link MeshView} — one quad per named face, so faces the generators
 * intentionally omit (e.g. a wall lip on a side that connects to a neighboring tank) are
 * genuinely absent in the preview too, not just hidden behind another face.
 */
final class TankGeometryMeshBuilder {

    /** Scene units per Minecraft block-model pixel (0-16 per block) — purely a display scale. */
    private static final float SCALE = 10f;

    private TankGeometryMeshBuilder() {}

    static MeshView build(JsonObject model, Color color, boolean translucent) {
        TriangleMesh mesh = new TriangleMesh();

        JsonArray elements = model.getAsJsonArray("elements");
        if (elements != null) {
            for (int i = 0; i < elements.size(); i++) {
                addElement(mesh, elements.get(i).getAsJsonObject());
            }
        }

        MeshView view = new MeshView(mesh);
        PhongMaterial material = new PhongMaterial(color);
        if (translucent) {
            material.setSpecularColor(Color.WHITE);
        }
        view.setMaterial(material);
        // Preview correctness over performance: don't rely on getting winding order exactly
        // right for every generated quad, since it can't be visually checked from here.
        view.setCullFace(CullFace.NONE);
        return view;
    }

    private static void addElement(TriangleMesh mesh, JsonObject element) {
        float[] from = vec3(element.getAsJsonArray("from"));
        float[] to = vec3(element.getAsJsonArray("to"));
        JsonObject faces = element.getAsJsonObject("faces");
        if (faces == null) return;

        float x1 = from[0], y1 = from[1], z1 = from[2];
        float x2 = to[0], y2 = to[1], z2 = to[2];

        for (String faceName : faces.keySet()) {
            switch (faceName) {
                case "down" -> addQuad(mesh, pt(x1, y1, z1), pt(x2, y1, z1), pt(x2, y1, z2), pt(x1, y1, z2));
                case "up" -> addQuad(mesh, pt(x1, y2, z2), pt(x2, y2, z2), pt(x2, y2, z1), pt(x1, y2, z1));
                case "north" -> addQuad(mesh, pt(x2, y1, z1), pt(x1, y1, z1), pt(x1, y2, z1), pt(x2, y2, z1));
                case "south" -> addQuad(mesh, pt(x1, y1, z2), pt(x2, y1, z2), pt(x2, y2, z2), pt(x1, y2, z2));
                case "west" -> addQuad(mesh, pt(x1, y1, z2), pt(x1, y1, z1), pt(x1, y2, z1), pt(x1, y2, z2));
                case "east" -> addQuad(mesh, pt(x2, y1, z1), pt(x2, y1, z2), pt(x2, y2, z2), pt(x2, y2, z1));
                default -> { /* unknown face name — ignore */ }
            }
        }
    }

    /**
     * Minecraft Y (up = larger value) -> scene Y (JavaFX's default is screen-style Y-down), so
     * the preview reads "up" the same way the game does.
     */
    private static float[] pt(float x, float y, float z) {
        return new float[]{x * SCALE, -y * SCALE, z * SCALE};
    }

    private static float[] vec3(JsonArray array) {
        return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }

    /** Adds a planar quad (two triangles) from four corner points, already in scene space. */
    private static void addQuad(TriangleMesh mesh, float[] a, float[] b, float[] c, float[] d) {
        int base = mesh.getPoints().size() / 3;
        mesh.getPoints().addAll(
                a[0], a[1], a[2],
                b[0], b[1], b[2],
                c[0], c[1], c[2],
                d[0], d[1], d[2]
        );
        mesh.getTexCoords().addAll(
                0, 0,
                1, 0,
                1, 1,
                0, 1
        );
        int t0 = base, t1 = base + 1, t2 = base + 2, t3 = base + 3;
        mesh.getFaces().addAll(
                t0, t0, t1, t1, t2, t2,
                t0, t0, t2, t2, t3, t3
        );
    }
}
