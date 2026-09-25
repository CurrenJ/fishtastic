package grill24.fishtastic.util;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The one place Fishtastic constructs resource ids. Call these instead of the static factories on
 * {@link ResourceLocation}.
 *
 * <p>Id construction differs per MC version. 26.1 has {@code ResourceLocation.fromNamespaceAndPath}, 1.21.1
 * has the same factories on {@code ResourceLocation}, and 1.20.1 only has
 * {@code new ResourceLocation(ns, path)}. Routing every call through here means each backport
 * changes only these method bodies, not every call site. The {@code idConstructionGuard} build
 * check (gradle/backport-guards.gradle) fails on direct calls elsewhere.
 */
public final class Ids {
    private Ids() {}

    /** {@code namespace:path}. */
    public static ResourceLocation of(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    /** {@code minecraft:path}. */
    public static ResourceLocation withDefaultNamespace(String path) {
        return new ResourceLocation("minecraft", path);
    }

    /** Parses {@code namespace:path} (or a bare path, which gets the {@code minecraft} namespace). Throws on invalid input. */
    public static ResourceLocation parse(String id) {
        return new ResourceLocation(id);
    }

    /** Like {@link #parse}, but returns null on invalid input. */
    public static @Nullable ResourceLocation tryParse(String id) {
        return ResourceLocation.tryParse(id);
    }
}
