package grill24.fishtastic.client;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.client.renderer.FishtasticGlintState;
import io.github.currenj.gelatinui.gui.IRenderContext;
import io.github.currenj.gelatinui.gui.animation.Easing;
import io.github.currenj.gelatinui.gui.animation.FloatKeyframeAnimation;
import io.github.currenj.gelatinui.gui.animation.Keyframe;
import io.github.currenj.gelatinui.gui.components.ItemButton;
import io.github.currenj.gelatinui.gui.minecraft.MinecraftRenderContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * An {@link ItemButton} that renders a solid black silhouette of its item instead of its normal
 * texture, used on the fish encyclopedia home screen for never-caught fish.
 *
 * <p>This renders through the real itemstack renderer ({@code super.renderSelf}, which calls
 * {@code GuiGraphicsExtractor.item(...)}) rather than re-deriving the icon some other way. The
 * silhouette swap happens via a Mixin pipeline ({@code gui_item_silhouette.fsh}, wired through
 * {@code GuiRendererMixin}/{@code ItemModelResolverMixin}/{@code FishtasticGlintState}) that
 * intercepts the GUI item blit and replaces it with a shader sampling the same already-baked
 * atlas slot — i.e. the actual rendered appearance of this exact item, whatever its model,
 * with any animation frame correctly resolved, since the bake is vanilla's own. See
 * {@code docs/item-effect-rendering.md} for the full architecture (this reuses the same
 * GUI-outline plumbing already built for quality-tier item glows, just with a fill shader
 * instead of an edge-detect one).
 *
 * <p>{@link FishtasticGlintState#SILHOUETTE_REQUESTED} is a thread-local set immediately before
 * the render call and cleared immediately after: {@code graphics.item(...)} synchronously
 * resolves the item model within that same call, and {@code ItemModelResolverMixin} reads the
 * flag at that exact moment to tag the resulting render state. This mirrors the existing
 * {@code ACTIVE_EFFECT} thread-local pattern used for the glint render path.
 */
public class SilhouetteItemButton extends ItemButton {
    // Reuses the status-pip art from spawn condition rows: unclaimed reward here vs. condition met there.
    private static final Identifier REWARD_PIP_TEXTURE = Fishtastic.id("textures/gui/status_indicator_pip_green.png");
    private static final int REWARD_PIP_TEXTURE_PX = 8;
    private static final float REWARD_PIP_SIZE = 4f;
    // Nudges the pip to read as a badge poking out from the icon's top-right corner.
    private static final float REWARD_PIP_OFFSET_X = 2f;
    private static final float REWARD_PIP_OFFSET_Y = -2f;
    private static final float REWARD_PIP_POP_DURATION = 0.35f;
    // Overshoot for the pop-in bounce; bigger than Easing.EASE_OUT_BACK's default 1.70158.
    private static final Easing.Func REWARD_PIP_POP_EASING = Easing.easeOutBack(10f);

    private boolean silhouette = false;
    private boolean hasUnclaimedReward = false;
    private float pipScale = 1f;
    // When true, only the pip is drawn. Used by FishSphereContainer for a second pass so pips
    // always render above neighboring icons. See FishSphereContainer#renderChildren.
    private boolean pipOverlayOnly = false;

    public SilhouetteItemButton(ItemStack itemStack) {
        super(itemStack);
    }

    public SilhouetteItemButton setSilhouette(boolean silhouette) {
        this.silhouette = silhouette;
        return this;
    }

    public boolean isSilhouette() {
        return silhouette;
    }

    public boolean hasUnclaimedReward() {
        return hasUnclaimedReward;
    }

    /** See {@link #pipOverlayOnly}. */
    public void setPipOverlayOnly(boolean pipOverlayOnly) {
        this.pipOverlayOnly = pipOverlayOnly;
    }

    /** Toggles the reward pip, shown at full scale; use {@link #playPipPopIn} to animate its entrance. */
    public SilhouetteItemButton setHasUnclaimedReward(boolean hasUnclaimedReward) {
        this.hasUnclaimedReward = hasUnclaimedReward;
        this.pipScale = 1f;
        return this;
    }

    /** Pops the pip in with an overshooting scale after {@code delaySeconds}. No-op without an unclaimed reward. */
    public void playPipPopIn(float delaySeconds) {
        if (!hasUnclaimedReward) return;
        pipScale = 0f;
        playAnimation(new FloatKeyframeAnimation(
                "reward-pip-pop-delay",
                List.of(new Keyframe(0f, 0f), new Keyframe(Math.max(0.001f, delaySeconds), 1f)),
                v -> {},
                () -> playAnimation(new FloatKeyframeAnimation(
                        "reward-pip-pop",
                        List.of(new Keyframe(0f, 0f), new Keyframe(REWARD_PIP_POP_DURATION, 1f, REWARD_PIP_POP_EASING)),
                        v -> pipScale = v))));
    }

    @Override
    protected void renderSelf(IRenderContext context) {
        if (!pipOverlayOnly) {
            if (!silhouette) {
                super.renderSelf(context);
            } else {
                FishtasticGlintState.SILHOUETTE_REQUESTED.set(Boolean.TRUE);
                try {
                    super.renderSelf(context);
                } finally {
                    FishtasticGlintState.SILHOUETTE_REQUESTED.remove();
                }
            }
        }

        if (hasUnclaimedReward && pipScale > 0f && context instanceof MinecraftRenderContext mcContext) {
            // GPU pose scale (like ItemButton's hover-zoom) instead of resizing the blit, which stepped visibly.
            float centerX = size.x - REWARD_PIP_SIZE + REWARD_PIP_OFFSET_X + REWARD_PIP_SIZE / 2f;
            float centerY = REWARD_PIP_OFFSET_Y + REWARD_PIP_SIZE / 2f;
            var pose = mcContext.getGraphics().pose();
            pose.pushMatrix();
            pose.translate(centerX, centerY);
            pose.scale(pipScale, pipScale);
            context.drawTexture(REWARD_PIP_TEXTURE,
                    -REWARD_PIP_SIZE / 2f, -REWARD_PIP_SIZE / 2f,
                    REWARD_PIP_SIZE, REWARD_PIP_SIZE,
                    0f, 0f, REWARD_PIP_TEXTURE_PX, REWARD_PIP_TEXTURE_PX,
                    REWARD_PIP_TEXTURE_PX, REWARD_PIP_TEXTURE_PX);
            pose.popMatrix();
        }
    }

    @Override
    protected String getDefaultDebugName() {
        return "SilhouetteItemButton(item=" + getItemStack().getItem() + ", silhouette=" + silhouette + ")";
    }
}
