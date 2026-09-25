package grill24.fishtastic.component;

import com.mojang.serialization.Codec;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import net.minecraft.world.item.ItemStack;

public record RodHookContents(ItemStack stack) {

    public static final RodHookContents EMPTY = new RodHookContents(ItemStack.EMPTY);

    /**
     * {@code ItemStack.CODEC} (not an "optional" codec: 1.20.1 has none). Only non-{@link #EMPTY}
     * values ever reach this codec — {@link grill24.fishtastic.component.ComponentKey} normalizes
     * the prototype default ({@code EMPTY}) away before encoding, and falls back to it on decode.
     */
    public static final Codec<RodHookContents> CODEC =
            ItemStack.CODEC.xmap(RodHookContents::new, RodHookContents::stack);

    public static final BufCodec<RodHookContents> STREAM_CODEC =
            BufCodecs.fromCodec(CODEC);

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public ItemStack copyStack() {
        return stack.copy();
    }
}
