package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * A solid black edge outline on GUI sprites blitted directly from their own texture — currently
 * the fishing minigame's zone icons and the encyclopedia's zone condition rows.
 *
 * <p>Sibling of {@link FishtasticBlackOutlineEffect}, which does the same job for sprites drawn
 * through the item atlas. Separate because the two shaders measure outline width in different
 * units: item pixels in the atlas, source texels here.
 *
 * <p>26.1.2 draws it as a {@code blit} through its own {@code RenderPipeline} + params UBO; on
 * 1.21.1 it's the {@code gui_texture_outline} {@link ShaderInstance} with plain uniforms, drawn
 * over the same rect as the icon ({@link #blit}), called just before the icon's own blit.
 */
public final class FishtasticTextureOutlineEffect {
    /** Solid black, no gradient fade, full opacity, one source texel thick. */
    private static final float COLOR_R = 0.0f, COLOR_G = 0.0f, COLOR_B = 0.0f;
    private static final float FALLOFF = 0.0f;
    private static final float OPACITY = 1.0f;
    private static final float WIDTH_TEXELS = 1.0f;

    private FishtasticTextureOutlineEffect() {}

    /**
     * The outline pass for a sprite blitted at ({@code x}, {@code y}) with size {@code w}x{@code h}
     * from ({@code u}, {@code v}) of a {@code texW}x{@code texH} texture — the same arguments as the
     * matching {@code GuiGraphics.blit}.
     */
    public static void blit(GuiGraphics guiGraphics, ResourceLocation texture, float x, float y, float w, float h,
                            float u, float v, int regionW, int regionH, int texW, int texH) {
        ShaderInstance shader = FishtasticShaders.guiTextureOutline;
        if (shader == null) return; // shaders not loaded (resource reload in progress)

        guiGraphics.flush();
        shader.safeGetUniform("OutlineColor").set(COLOR_R, COLOR_G, COLOR_B, 1.0f);
        shader.safeGetUniform("OutlineFalloff").set(FALLOFF);
        shader.safeGetUniform("OutlineOpacity").set(OPACITY);
        shader.safeGetUniform("OutlineWidth").set(WIDTH_TEXELS);
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        float u0 = u / texW;
        float v0 = v / texH;
        float u1 = (u + regionW) / texW;
        float v1 = (v + regionH) / texH;
        Matrix4f pose = guiGraphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, FishtasticShaders.GUI_EFFECT_FORMAT);
        buffer.addVertex(pose, x, y, 0.0F).setUv(u0, v0);
        buffer.addVertex(pose, x, y + h, 0.0F).setUv(u0, v1);
        buffer.addVertex(pose, x + w, y + h, 0.0F).setUv(u1, v1);
        buffer.addVertex(pose, x + w, y, 0.0F).setUv(u1, v0);
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.disableBlend();
    }
}
