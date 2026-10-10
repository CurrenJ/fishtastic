package grill24.fishtastic.client.companion;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;

/**
 * Draws the docked companion into the HUD with a real perspective projection (Phase 0 spike,
 * docs/fish-companions.md §5.1).
 *
 * <p>Vanilla's picture-in-picture renderers are orthographic: {@code prepare} sets an ortho
 * projection and then calls {@link #renderToTexture}. Nothing stops the subclass replacing that
 * projection before it draws, so this one installs the camera's own perspective, cropped to the
 * rectangle — the fish is the same 3D model the world pass draws, it just lands in a texture that
 * the GUI then blits in its normal order, i.e. on top of the minigame bar.
 */
public final class CompanionPipRenderer extends PictureInPictureRenderer<CompanionPipRenderState> {
    /** Spike experiment hook: lighting entry the docked fish is drawn under (the self-test cycles it). */
    public static Lighting.Entry lighting = Lighting.Entry.LEVEL;

    private final ProjectionMatrixBuffer perspectiveBuffer = new ProjectionMatrixBuffer("PIP - companion perspective");

    public CompanionPipRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public Class<CompanionPipRenderState> getRenderStateClass() {
        return CompanionPipRenderState.class;
    }

    @Override
    protected void renderToTexture(CompanionPipRenderState state, PoseStack ignoredOrthoStack) {
        ClientCompanions.pipRenders++;
        RenderSystem.setProjectionMatrix(perspectiveBuffer.getBuffer(state.projection()), ProjectionType.PERSPECTIVE);
        Minecraft mc = Minecraft.getInstance();
        mc.gameRenderer.getLighting().setupFor(lighting);
        FeatureRenderDispatcher features = mc.gameRenderer.getFeatureRenderDispatcher();
        // The pose stays world-oriented, as in the world pass, so normals meet level lighting's
        // world-space light directions the same way. The view rotation the world pass keeps on the
        // model-view stack lives in the projection instead: the GUI's feature submits ignore the
        // global model-view stack (verified — a fish posed through it was drawn off-screen as soon
        // as the camera turned away from yaw 180, where the view rotation is close to identity).
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(state.fishPose());
        ClientCompanions.submitDockedFish(poseStack, features.getSubmitNodeStorage(), state.light(), state.gameTimeTicks());
        features.renderAllFeatures();
    }

    @Override
    protected String getTextureLabel() {
        return "fishtastic companion";
    }

    @Override
    public void close() {
        super.close();
        perspectiveBuffer.close();
    }
}
