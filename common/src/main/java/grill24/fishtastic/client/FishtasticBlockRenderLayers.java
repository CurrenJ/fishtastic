package grill24.fishtastic.client;

import grill24.fishtastic.FishtasticBlocks;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;

/**
 * PORT-ONLY: the chunk render layers of Fishtastic's see-through blocks. 26.1 derives a block's
 * layer from its textures' alpha, so it registers nothing; 1.21.1 draws every block solid unless
 * told otherwise. These are the layers 26.1 derives (checked against the textures: the stained
 * glass has partial alpha, the plain clear and borderless glass only fully transparent pixels).
 * The fish tank reads the same layers for its retextured glass (see FishTankGeometry).
 *
 * <p>Each loader supplies its registration call (Fabric {@code BlockRenderLayerMap}, NeoForge
 * {@code ItemBlockRenderTypes.setRenderLayer}).
 */
public final class FishtasticBlockRenderLayers {
    @FunctionalInterface
    public interface Registrar {
        void register(Block block, RenderType layer);
    }

    public static void register(Registrar registrar) {
        for (Holder<Block> glass : FishtasticBlocks.CLEAR_STAINED_GLASS.values()) {
            registrar.register(glass.value(), RenderType.translucent());
        }
        for (Holder<Block> glass : FishtasticBlocks.BORDERLESS_STAINED_GLASS.values()) {
            registrar.register(glass.value(), RenderType.translucent());
        }
        registrar.register(FishtasticBlocks.CLEAR_GLASS.value(), RenderType.cutout());
        registrar.register(FishtasticBlocks.BORDERLESS_GLASS.value(), RenderType.cutout());
    }

    private FishtasticBlockRenderLayers() {}
}
