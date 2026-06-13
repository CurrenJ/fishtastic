package grill24.fishtastic.component;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public record RodBaitContents(ItemStack stack) {

    public static final RodBaitContents EMPTY = new RodBaitContents(ItemStack.EMPTY);

    public static final Codec<RodBaitContents> CODEC =
            ItemStack.OPTIONAL_CODEC.xmap(RodBaitContents::new, RodBaitContents::stack);

    public static final StreamCodec<ByteBuf, RodBaitContents> STREAM_CODEC =
            ByteBufCodecs.fromCodec(CODEC);

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public ItemStack copyStack() {
        return stack.copy();
    }
}
