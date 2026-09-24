package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import grill24.fishtastic.util.Ids;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Core shader programs owned by Fishtastic. 1.21.1 has no {@code RenderPipeline}: each shader is a
 * JSON program ({@code assets/fishtastic/shaders/core/<name>.json}) loaded as a {@link ShaderInstance}.
 * Vanilla only loads core shaders from the {@code minecraft} namespace, so ours are registered
 * through the loader hook instead (Fabric {@code CoreShaderRegistrationCallback}, NeoForge
 * {@code RegisterShadersEvent}); both call {@link #registerAll}. The instances are replaced on every
 * resource reload.
 */
public final class FishtasticShaders {
    public static final ResourceLocation OUTLINE_BAKE_ID = Ids.of("fishtastic", "outline_bake");
    public static final ResourceLocation OUTLINE_BAKE_LEGENDARY_ID = Ids.of("fishtastic", "outline_bake_legendary");
    public static final VertexFormat OUTLINE_BAKE_FORMAT = DefaultVertexFormat.POSITION_TEX;
    public static final ResourceLocation GUI_ITEM_SILHOUETTE_ID = Ids.of("fishtastic", "gui_item_silhouette");
    public static final ResourceLocation GUI_TEXTURE_OUTLINE_ID = Ids.of("fishtastic", "gui_texture_outline");
    /** Vertex format of the GUI effect programs (their shared {@code gui_effect.vsh}). */
    public static final VertexFormat GUI_EFFECT_FORMAT = DefaultVertexFormat.POSITION_TEX;

    public static ShaderInstance outlineBake;
    public static ShaderInstance outlineBakeLegendary;
    /** {@link FishtasticSilhouetteEffect}: the encyclopedia's never-caught silhouette. */
    public static ShaderInstance guiItemSilhouette;
    /** {@link FishtasticTextureOutlineEffect}: the black edge on directly blitted GUI sprites. */
    public static ShaderInstance guiTextureOutline;

    /** Platform-neutral registration: the loader hook supplies {@code register}. */
    public interface Registrar {
        void register(ResourceLocation id, VertexFormat format, Consumer<ShaderInstance> onLoad) throws IOException;
    }

    public static void registerAll(Registrar registrar) throws IOException {
        registrar.register(OUTLINE_BAKE_ID, OUTLINE_BAKE_FORMAT, s -> outlineBake = s);
        registrar.register(OUTLINE_BAKE_LEGENDARY_ID, OUTLINE_BAKE_FORMAT, s -> outlineBakeLegendary = s);
        registrar.register(GUI_ITEM_SILHOUETTE_ID, GUI_EFFECT_FORMAT, s -> guiItemSilhouette = s);
        registrar.register(GUI_TEXTURE_OUTLINE_ID, GUI_EFFECT_FORMAT, s -> guiTextureOutline = s);
    }

    private FishtasticShaders() {}
}
