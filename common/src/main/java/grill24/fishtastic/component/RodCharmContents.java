package grill24.fishtastic.component;

import com.mojang.serialization.Codec;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import net.minecraft.world.item.ItemStack;

public record RodCharmContents(ItemStack stack) {

    public static final RodCharmContents EMPTY = new RodCharmContents(ItemStack.EMPTY);

    /**
     * {@code ItemStack.CODEC} (not an "optional" codec: 1.20.1 has none). Only non-{@link #EMPTY}
     * values ever reach this codec — {@link grill24.fishtastic.component.ComponentKey} normalizes
     * the prototype default ({@code EMPTY}) away before encoding, and falls back to it on decode.
     */
    public static final Codec<RodCharmContents> CODEC =
            ItemStack.CODEC.xmap(RodCharmContents::new, RodCharmContents::stack);

    public static final BufCodec<RodCharmContents> STREAM_CODEC =
            BufCodecs.fromCodec(CODEC);

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public ItemStack copyStack() {
        return stack.copy();
    }
}
