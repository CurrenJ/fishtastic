package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.client.util.FishPileIcons;
import grill24.fishtastic.item.FishTankStructureCosmeticItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * PORT-ONLY: the items 26.1 draws with custom {@code ItemModel} types, drawn on 1.21.1 by a
 * {@code builtin/entity} item renderer instead (Fabric {@code BuiltinItemRendererRegistry},
 * NeoForge {@code IClientItemExtensions#getCustomRenderer}); both call {@link #render}.
 * <ul>
 *   <li>Pile of Fish: {@link PileOfFishItemModel} (26.1 {@code fishtastic:pile_of_fish_layers}),
 *       or {@link FishPileBlockItemModel} for the leaderboard's pile-block stacks (26.1
 *       {@code fishtastic:fish_pile_block} through {@code minecraft:item_model}).</li>
 *   <li>Structure cosmetics: {@link CosmeticStructureItemModel} (26.1 {@code fishtastic:cosmetic_structure}).</li>
 *   <li>Treasure chest: a vanilla chest (26.1's {@code minecraft:chest} special model and its
 *       Christmas {@code local_time} select). Vanilla's chest renderer picks the Christmas texture
 *       on the same dates, evaluated when it's built rather than per frame.</li>
 * </ul>
 * The item models are generated as {@code builtin/entity} (the structures and chest with the block
 * and chest display transforms) by {@code FishtasticModelProvider}.
 */
public final class FishtasticItemRenderers {
    private static ChestBlockEntity treasureChest;

    /** Every item drawn by {@link #render}, for the loaders' registration. */
    public static List<Item> items() {
        List<Item> items = new ArrayList<>();
        items.add(FishtasticItems.PILE_OF_FISH.value());
        items.add(FishtasticItems.COSMETIC_TREASURE_CHEST.value());
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof FishTankStructureCosmeticItem) items.add(item);
        }
        return items;
    }

    /** Draws {@code stack}; the pose is in its model space (display transform applied, then {@code -0.5}). */
    public static void render(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                              MultiBufferSource buffers, int light, int overlay) {
        if (stack.is(FishtasticItems.PILE_OF_FISH.value())) {
            if (FishPileIcons.isPileBlock(stack)) {
                FishPileBlockItemModel.render(stack, displayContext, poseStack, buffers, light, overlay);
            } else {
                PileOfFishItemModel.render(stack, displayContext, poseStack, buffers, light, overlay);
            }
        } else if (stack.getItem() instanceof FishTankStructureCosmeticItem) {
            CosmeticStructureItemModel.render(stack, displayContext, poseStack, buffers, light, overlay);
        } else if (stack.is(FishtasticItems.COSMETIC_TREASURE_CHEST.value())) {
            if (treasureChest == null) treasureChest = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
            Minecraft.getInstance().getBlockEntityRenderDispatcher().renderItem(treasureChest, poseStack, buffers, light, overlay);
        }
    }

    private FishtasticItemRenderers() {}
}
