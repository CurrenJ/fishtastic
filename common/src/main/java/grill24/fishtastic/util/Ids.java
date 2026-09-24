package grill24.fishtastic.util;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The one place Fishtastic constructs resource ids. Call these instead of the static factories on
 * {@link Identifier}.
 *
 * <p>Id construction differs per MC version. 26.1 has {@code Identifier.fromNamespaceAndPath}, 1.21.1
 * has the same factories on {@code ResourceLocation}, and 1.20.1 only has
 * {@code new ResourceLocation(ns, path)}. Routing every call through here means each backport
 * changes only these method bodies, not every call site. The {@code idConstructionGuard} build
 * check (gradle/backport-guards.gradle) fails on direct calls elsewhere.
 */
public final class Ids {
    private Ids() {}

    /** {@code namespace:path}. */
    public static Identifier of(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    /** {@code minecraft:path}. */
    public static Identifier withDefaultNamespace(String path) {
        return Identifier.withDefaultNamespace(path);
    }

    /** Parses {@code namespace:path} (or a bare path, which gets the {@code minecraft} namespace). Throws on invalid input. */
    public static Identifier parse(String id) {
        return Identifier.parse(id);
    }

    /** Like {@link #parse}, but returns null on invalid input. */
    public static @Nullable Identifier tryParse(String id) {
        return Identifier.tryParse(id);
    }
}
