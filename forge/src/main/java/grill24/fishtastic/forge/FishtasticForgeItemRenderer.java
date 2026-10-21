package grill24.fishtastic.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishtastic.client.renderer.FishtasticItemRenderers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Forge 52 hook for the 1.21.1 {@code builtin/entity} items. Forge reads
 * {@link IClientItemExtensions} from {@code Item#getRenderPropertiesInternal}; the client-only
 * mixin supplies this adapter for the items {@link FishtasticItemRenderers} draws.
 */
public final class FishtasticForgeItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static FishtasticForgeItemRenderer instance;

    public static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (instance == null) {
                instance = new FishtasticForgeItemRenderer();
            }
            return instance;
        }
    };

    private FishtasticForgeItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource buffers, int light, int overlay) {
        FishtasticItemRenderers.render(stack, displayContext, poseStack, buffers, light, overlay);
    }
}
