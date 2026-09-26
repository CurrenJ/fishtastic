package grill24.fishtastic.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.client.renderer.FishtasticItemRenderers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.lang.reflect.Field;

/**
 * PORT-ONLY: Forge's hook for the {@code builtin/entity} items {@link FishtasticItemRenderers}
 * draws (Fabric registers the same with {@code BuiltinItemRendererRegistry}).
 *
 * <p>B5.3: Forge 47 has no {@code RegisterClientExtensionsEvent} (that's NeoForge). The real
 * extension point is {@code Item#initializeClient(Consumer<IClientItemExtensions>)}, but it's an
 * empty default method meant to be overridden per-{@code Item}-subclass — these items
 * ({@code PILE_OF_FISH}, the treasure chest, every {@code FishTankStructureCosmeticItem}) are
 * common-module classes that can't depend on a Forge-only interface. Instead this reflects into
 * {@code Item}'s private {@code renderProperties} field directly, which is exactly what a
 * subclass's {@code initializeClient} override would have populated via its consumer — the same
 * workaround Forge's own docs point third-party/vanilla-item integrations at.
 */
public final class FishtasticItemRendererForge extends BlockEntityWithoutLevelRenderer {
    private static final Field RENDER_PROPERTIES_FIELD;

    static {
        try {
            RENDER_PROPERTIES_FIELD = Item.class.getDeclaredField("renderProperties");
            RENDER_PROPERTIES_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private static FishtasticItemRendererForge instance;

    private FishtasticItemRendererForge() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource buffers, int light, int overlay) {
        FishtasticItemRenderers.render(stack, displayContext, poseStack, buffers, light, overlay);
    }

    public static void register() {
        IClientItemExtensions extensions = new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                // Created on first use: the block entity render dispatcher doesn't exist yet when
                // this runs (FMLClientSetupEvent).
                if (instance == null) instance = new FishtasticItemRendererForge();
                return instance;
            }
        };
        for (Item item : FishtasticItemRenderers.items()) {
            try {
                RENDER_PROPERTIES_FIELD.set(item, extensions);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        Fishtastic.LOGGER.info("Fishtastic builtin/entity item renderers registered.");
    }
}
