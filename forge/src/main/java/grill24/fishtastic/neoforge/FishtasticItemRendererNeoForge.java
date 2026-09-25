package grill24.fishtastic.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishtastic.client.renderer.FishtasticItemRenderers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * PORT-ONLY: NeoForge's hook for the {@code builtin/entity} items {@link FishtasticItemRenderers}
 * draws (Fabric registers the same with {@code BuiltinItemRendererRegistry}).
 */
public final class FishtasticItemRendererNeoForge extends BlockEntityWithoutLevelRenderer {
    private static FishtasticItemRendererNeoForge instance;

    private FishtasticItemRendererNeoForge() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource buffers, int light, int overlay) {
        FishtasticItemRenderers.render(stack, displayContext, poseStack, buffers, light, overlay);
    }

    public static void register(RegisterClientExtensionsEvent event) {
        IClientItemExtensions extensions = new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                // Created on first use: the block entity render dispatcher doesn't exist yet when
                // client extensions are registered.
                if (instance == null) instance = new FishtasticItemRendererNeoForge();
                return instance;
            }
        };
        event.registerItem(extensions, FishtasticItemRenderers.items().toArray(net.minecraft.world.item.Item[]::new));
    }
}
