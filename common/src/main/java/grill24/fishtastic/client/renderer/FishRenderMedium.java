package grill24.fishtastic.client.renderer;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Whether the fish being resolved right now is drawn in water. The in-world fish renderers resolve
 * their item models with no level or entity, so the model can't tell a tank fish from one in the
 * inventory; those renderers set this around the resolve and the {@code fishtastic:submerged}
 * item-model property reads it (docs/hellbender-wet-dry-texture.md). Render thread only.
 *
 * <p>Defaults to dry, so any context that doesn't set it (GUI, hand, item frame, dropped item,
 * fish pile, the fishing reveal) gets the out-of-water texture.
 */
public final class FishRenderMedium {
    private FishRenderMedium() {}

    /** Dev override from {@code /fishtasticambient fishmedium}: AUTO follows the renderers. */
    public enum Forced { AUTO, WET, DRY }

    private static boolean wet;
    private static Forced forced = Forced.AUTO;

    /** What the item-model property sees: the renderer's flag unless the dev override forces one. */
    public static boolean wet() {
        return switch (forced) {
            case WET -> true;
            case DRY -> false;
            case AUTO -> wet;
        };
    }

    /** Sets the flag and returns the previous one; restore it in a finally. */
    public static boolean set(boolean value) {
        boolean previous = wet;
        wet = value;
        return previous;
    }

    /** Resolves a fish's model as the in-world renderers do (FIXED, no level or owner), drawn wet or dry. */
    public static void resolve(ItemModelResolver resolver, ItemStackRenderState state, ItemStack stack, boolean wet) {
        boolean previous = set(wet);
        try {
            resolver.updateForTopItem(state, stack, ItemDisplayContext.FIXED, null, null, 0);
        } finally {
            set(previous);
        }
    }

    public static Forced forced() { return forced; }

    public static void setForced(Forced value) { forced = value; }

    /** {@code fishtastic:submerged}: true when the fish is drawn in water. */
    public record Submerged() implements ConditionalItemModelProperty {
        public static final MapCodec<Submerged> MAP_CODEC = MapCodec.unit(new Submerged());

        @Override
        public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed,
                           ItemDisplayContext context) {
            return wet();
        }

        @Override
        public MapCodec<Submerged> type() { return MAP_CODEC; }
    }
}
