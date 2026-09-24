package grill24.fishtastic.util;

import com.mojang.serialization.Codec;
import grill24.fishtastic.Fishtastic;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Block entity NBT helpers for MC versions before 1.21.6's {@code ValueInput}/{@code ValueOutput}.
 * Each method stands in for the {@code ValueInput}/{@code ValueOutput} call of the same name, so the
 * ported {@code loadAdditional}/{@code saveAdditional} bodies read line for line like 26.1.2's.
 */
public final class BlockEntityNbt {
    private BlockEntityNbt() {}

    /** {@code ValueOutput#store}: encodes {@code value} with {@code codec} under {@code key}. */
    public static <T> void store(CompoundTag tag, String key, Codec<T> codec, T value, HolderLookup.Provider registries) {
        codec.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), value)
                .resultOrPartial(error -> Fishtastic.LOGGER.error("Failed to save '{}': {}", key, error))
                .ifPresent(encoded -> tag.put(key, encoded));
    }

    /** {@code ValueInput#read}: decodes {@code key} with {@code codec}, or empty if it's missing or invalid. */
    public static <T> Optional<T> read(CompoundTag tag, String key, Codec<T> codec, HolderLookup.Provider registries) {
        Tag encoded = tag.get(key);
        if (encoded == null) return Optional.empty();
        return codec.parse(registries.createSerializationContext(NbtOps.INSTANCE), encoded)
                .resultOrPartial(error -> Fishtastic.LOGGER.error("Failed to load '{}': {}", key, error));
    }

    /** {@code ValueOutput#childrenList}: a new list of compounds stored under {@code key}. */
    public static ListTag childrenList(CompoundTag tag, String key) {
        ListTag list = new ListTag();
        tag.put(key, list);
        return list;
    }

    /** {@code ValueOutput.ValueOutputList#addChild}: appends and returns a new compound. */
    public static CompoundTag addChild(ListTag list) {
        CompoundTag child = new CompoundTag();
        list.add(child);
        return child;
    }

    /** {@code ValueInput#childrenListOrEmpty}: the compounds in list {@code key}, or none. */
    public static List<CompoundTag> childrenListOrEmpty(CompoundTag tag, String key) {
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        List<CompoundTag> children = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            children.add(list.getCompound(i));
        }
        return children;
    }

    public static int getIntOr(CompoundTag tag, String key, int fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getInt(key) : fallback;
    }

    public static long getLongOr(CompoundTag tag, String key, long fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getLong(key) : fallback;
    }

    public static float getFloatOr(CompoundTag tag, String key, float fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getFloat(key) : fallback;
    }

    public static boolean getBooleanOr(CompoundTag tag, String key, boolean fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getBoolean(key) : fallback;
    }

    public static String getStringOr(CompoundTag tag, String key, String fallback) {
        return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : fallback;
    }
}
