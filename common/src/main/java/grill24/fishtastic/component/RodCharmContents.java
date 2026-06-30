package grill24.fishtastic.component;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public record RodCharmContents(ItemStack stack) {

    public static final RodCharmContents EMPTY = new RodCharmContents(ItemStack.EMPTY);

    public static final Codec<RodCharmContents> CODEC =
            ItemStack.OPTIONAL_CODEC.xmap(RodCharmContents::new, RodCharmContents::stack);

    public static final StreamCodec<ByteBuf, RodCharmContents> STREAM_CODEC =
            ByteBufCodecs.fromCodec(CODEC);

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public ItemStack copyStack() {
        return stack.copy();
    }
}
