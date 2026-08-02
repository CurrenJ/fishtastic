package grill24.fishtastic.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.FishtasticBlocks;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Consumer;

/**
 * The frame, sand (floor), and glass blocks a fish tank was assembled from.
 * Carried on both the item stack (so distinct combos are distinct, non-stacking
 * items) and the placed block entity, which is seeded from this component on
 * placement (see {@code BlockItem#place} -> {@code BlockEntity#applyComponentsFromItemStack}).
 */
public record FishTankMaterials(Block frame, Block sand, Block glass) implements TooltipProvider {

    /**
     * Not a static field: {@code FishtasticBlocks.CLEAR_STAINED_GLASS} isn't populated until
     * {@code FishtasticBlocks.registerBlocks()} runs, which happens after data components are
     * registered — eagerly evaluating this at class-load time would NPE during mod init.
     */
    public static FishTankMaterials defaultMaterials() {
        return new FishTankMaterials(
                Blocks.OAK_PLANKS, Blocks.SAND,
                FishtasticBlocks.CLEAR_STAINED_GLASS.get(DyeColor.BLUE).value());
    }

    private static final StreamCodec<ByteBuf, Block> BLOCK_STREAM_CODEC = ByteBufCodecs.idMapper(
            BuiltInRegistries.BLOCK::byId,
            BuiltInRegistries.BLOCK::getId
    );

    public static final Codec<FishTankMaterials> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("frame").forGetter(FishTankMaterials::frame),
            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("sand").forGetter(FishTankMaterials::sand),
            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("glass").forGetter(FishTankMaterials::glass)
    ).apply(instance, FishTankMaterials::new));

    public static final StreamCodec<ByteBuf, FishTankMaterials> STREAM_CODEC = StreamCodec.composite(
            BLOCK_STREAM_CODEC, FishTankMaterials::frame,
            BLOCK_STREAM_CODEC, FishTankMaterials::sand,
            BLOCK_STREAM_CODEC, FishTankMaterials::glass,
            FishTankMaterials::new
    );

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltipAdder, TooltipFlag tooltipFlag, DataComponentGetter components) {
        tooltipAdder.accept(Component.translatable("tooltip.fishtastic.fish_tank_materials.frame",
                frame.getName().copy().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
        tooltipAdder.accept(Component.translatable("tooltip.fishtastic.fish_tank_materials.sand",
                sand.getName().copy().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
        tooltipAdder.accept(Component.translatable("tooltip.fishtastic.fish_tank_materials.glass",
                glass.getName().copy().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
    }
}
