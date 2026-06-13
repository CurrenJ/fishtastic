package grill24.fishtastic.fishtank;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-block-type render transforms for cosmetic decorations inside fish tanks.
 * Loaded from {@code assets/<namespace>/cosmetic_transforms/<name>.json} at resource reload time.
 * Dev commands (/fishtastic cosmetic nudge/rotate/scale) modify entries at runtime until next reload.
 */
public class CosmeticTransforms {

    public record Transform(float offsetX, float offsetY, float offsetZ,
                            float rotX, float rotY, float rotZ,
                            float scale) {

        public static final Transform DEFAULT = new Transform(0f, 0f, 0f, 0f, 0f, 0f, 0.25f);

        /** Codec with all fields optional (missing fields use the defaults above). */
        public static final MapCodec<Transform> MAP_CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                Codec.FLOAT.optionalFieldOf("offsetX", 0f).forGetter(Transform::offsetX),
                Codec.FLOAT.optionalFieldOf("offsetY", 0f).forGetter(Transform::offsetY),
                Codec.FLOAT.optionalFieldOf("offsetZ", 0f).forGetter(Transform::offsetZ),
                Codec.FLOAT.optionalFieldOf("rotX",    0f).forGetter(Transform::rotX),
                Codec.FLOAT.optionalFieldOf("rotY",    0f).forGetter(Transform::rotY),
                Codec.FLOAT.optionalFieldOf("rotZ",    0f).forGetter(Transform::rotZ),
                Codec.FLOAT.optionalFieldOf("scale", 0.25f).forGetter(Transform::scale)
            ).apply(instance, Transform::new)
        );

        public Transform withOffset(float dx, float dy, float dz) {
            return new Transform(offsetX + dx, offsetY + dy, offsetZ + dz, rotX, rotY, rotZ, scale);
        }

        public Transform withRotation(float rx, float ry, float rz) {
            return new Transform(offsetX, offsetY, offsetZ, rotX + rx, rotY + ry, rotZ + rz, scale);
        }

        public Transform withScale(float s) {
            return new Transform(offsetX, offsetY, offsetZ, rotX, rotY, rotZ, s);
        }

        /** Produces a copy-pasteable JSON file entry for use with /fishtastic cosmetic dump. */
        public String toFileJson(Block block) {
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
            return String.format(
                "{\n  \"block\": \"%s\",\n  \"offsetX\": %.4f,\n  \"offsetY\": %.4f,\n  \"offsetZ\": %.4f," +
                "\n  \"rotX\": %.4f,\n  \"rotY\": %.4f,\n  \"rotZ\": %.4f,\n  \"scale\": %.4f\n}",
                blockId, offsetX, offsetY, offsetZ, rotX, rotY, rotZ, scale);
        }
    }

    // Volatile so a resource-reload swap on the main thread is immediately visible to the render thread.
    private static volatile Map<Block, Transform> TRANSFORMS = new HashMap<>();

    public static Transform get(Block block) {
        return TRANSFORMS.getOrDefault(block, Transform.DEFAULT);
    }

    public static void set(Block block, Transform transform) {
        TRANSFORMS.put(block, transform);
    }

    /** Called by the resource reload listener to atomically replace all transforms. */
    public static void replaceAll(Map<Block, Transform> newTransforms) {
        TRANSFORMS = new HashMap<>(newTransforms);
    }
}
