package grill24.fishtastic.util;

import grill24.fishtastic.Fishtastic;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

public class Utility {
    public static Identifier ft(String id) {
        return Identifier.fromNamespaceAndPath(Fishtastic.MOD_ID, id);
    }

    public static Vector3f interpolateColor(Vector3f colorStart, Vector3f colorEnd, float t) {
        float r = MathUtil.lerp(colorStart.x(), colorEnd.x(), t);
        float g = MathUtil.lerp(colorStart.y(), colorEnd.y(), t);
        float b = MathUtil.lerp(colorStart.z(), colorEnd.z(), t);
        return new Vector3f(r, g, b);
    }

    /** Title-cases an underscore_separated path, e.g. "ocean_fish" -> "Ocean Fish". */
    public static String prettyName(String path) {
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
