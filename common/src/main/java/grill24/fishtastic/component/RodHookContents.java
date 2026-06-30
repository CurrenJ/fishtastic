package grill24.fishtastic.component;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public record RodHookContents(ItemStack stack) {

    public static final RodHookContents EMPTY = new RodHookContents(ItemStack.EMPTY);

    public static final Codec<RodHookContents> CODEC =
            ItemStack.OPTIONAL_CODEC.xmap(RodHookContents::new, RodHookContents::stack);

    public static final StreamCodec<ByteBuf, RodHookContents> STREAM_CODEC =
            ByteBufCodecs.fromCodec(CODEC);

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public ItemStack copyStack() {
        return stack.copy();
    }
}
