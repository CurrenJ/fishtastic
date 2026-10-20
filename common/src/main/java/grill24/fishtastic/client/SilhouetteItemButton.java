package grill24.fishtastic.client;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.client.renderer.FishtasticGlintState;
import io.github.currenj.gelatinui.gui.IRenderContext;
import io.github.currenj.gelatinui.gui.animation.Easing;
import io.github.currenj.gelatinui.gui.animation.FloatKeyframeAnimation;
import io.github.currenj.gelatinui.gui.animation.Keyframe;
import io.github.currenj.gelatinui.gui.components.ItemButton;
import io.github.currenj.gelatinui.gui.UIEvent;
import io.github.currenj.gelatinui.gui.minecraft.MinecraftRenderContext;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * An {@link ItemButton} that renders a solid black silhouette of its item instead of its normal
 * texture, used on the fish encyclopedia home screen for never-caught fish.
 *
 * <p>This renders through the real itemstack renderer ({@code super.renderSelf}, which calls
 * {@code GuiGraphics.item(...)}) rather than re-deriving the icon some other way. The
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
 * the render call and cleared immediately after: {@code graphics.renderItem(...)} synchronously
 * resolves the item model within that same call, and {@code ItemModelResolverMixin} reads the
 * flag at that exact moment to tag the resulting render state. This mirrors the existing
 * {@code ACTIVE_EFFECT} thread-local pattern used for the glint render path.
 */
public class SilhouetteItemButton extends ItemButton implements Narrated {
    private String narration;

    public SilhouetteItemButton setNarration(String narration) {
        this.narration = narration;
        return this;
    }

    @Override
    public String narration() {
        return narration;
    }

    // Reuses the status-pip art from spawn condition rows: unclaimed reward here vs. condition met there.
    private static final ResourceLocation REWARD_PIP_TEXTURE = Fishtastic.id("textures/gui/status_indicator_pip_green.png");
    private static final int REWARD_PIP_TEXTURE_PX = 8;
    private static final float REWARD_PIP_SIZE = 4f;
    // Nudges the pip to read as a badge poking out from the icon's top-right corner.
    private static final float REWARD_PIP_OFFSET_X = 2f;
    private static final float REWARD_PIP_OFFSET_Y = -2f;
    private static final float REWARD_PIP_POP_DURATION = 0.35f;
    // Overshoot for the pop-in bounce; bigger than Easing.EASE_OUT_BACK's default 1.70158.
    private static final Easing.Func REWARD_PIP_POP_EASING = Easing.easeOutBack(10f);
    // Bond heart: the 5x4 atlas heart at 1:1 (half-size 8x7 lands on half pixels and drops rows),
    // poking out of the bottom-right corner like the pip does top-right.
    private static final ResourceLocation BOND_HEART_TEXTURE = Fishtastic.id("textures/gui/companions.png");
    private static final float BOND_HEART_W = 5f, BOND_HEART_H = 4f;
    // Experiment: each badge holds at full opacity after it appears, then fades out.
    private static final long BADGE_HOLD_MS = 3000L, BADGE_FADE_MS = 1500L;

    private boolean silhouette = false;
    private boolean hasUnclaimedReward = false;
    private boolean bondAvailable = false;

    public SilhouetteItemButton setBondAvailable(boolean available) {
        if (available && !bondAvailable) heartShownAt = Util.getMillis();
        bondAvailable = available;
        return this;
    }
    private float pipScale = 1f;
    // When each badge appeared (Util.getMillis()); -1 = not yet (hidden or still popping in), drawn fully opaque.
    private long pipShownAt = -1L, heartShownAt = -1L;
    // When true, only the pip is drawn. Used by FishSphereContainer for a second pass so pips
    // always render above neighboring icons. See FishSphereContainer#renderChildren.
    private boolean pipOverlayOnly = false;

    public SilhouetteItemButton(ItemStack itemStack) {
        super(itemStack);
    }

    private boolean clickMustBeInside = false;

    /**
     * ItemButton claims every CLICK that reaches it, and containers forward events to children
     * without a bounds check, so a click anywhere in the parent (a panel background, a scroll bar)
     * would fire this button's handler. Opt in where the button shares a parent with other
     * clickable things.
     */
    public SilhouetteItemButton requireClickInside() {
        this.clickMustBeInside = true;
        return this;
    }

    @Override
    public boolean handleEvent(UIEvent event) {
        if (clickMustBeInside && event.getType() == UIEvent.Type.CLICK
                && !getBounds().contains(event.getMouseX(), event.getMouseY())) {
            return false;
        }
        return super.handleEvent(event);
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

    /** Whether any corner badge (reward pip or bond heart) is shown. */
    public boolean hasBadge() {
        return hasUnclaimedReward || bondAvailable;
    }

    /** See {@link #pipOverlayOnly}. */
    public void setPipOverlayOnly(boolean pipOverlayOnly) {
        this.pipOverlayOnly = pipOverlayOnly;
    }

    /** Toggles the reward pip, shown at full scale; use {@link #playPipPopIn} to animate its entrance. */
    public SilhouetteItemButton setHasUnclaimedReward(boolean hasUnclaimedReward) {
        if (hasUnclaimedReward && !this.hasUnclaimedReward) pipShownAt = Util.getMillis();
        this.hasUnclaimedReward = hasUnclaimedReward;
        this.pipScale = 1f;
        return this;
    }

    /** Pops the badges in with an overshooting scale after {@code delaySeconds}. No-op without a badge. */
    public void playPipPopIn(float delaySeconds) {
        if (!hasBadge()) return;
        pipScale = 0f;
        pipShownAt = heartShownAt = -1L;
        playAnimation(new FloatKeyframeAnimation(
                "reward-pip-pop-delay",
                List.of(new Keyframe(0f, 0f), new Keyframe(Math.max(0.001f, delaySeconds), 1f)),
                v -> {},
                () -> playAnimation(new FloatKeyframeAnimation(
                        "reward-pip-pop",
                        List.of(new Keyframe(0f, 0f), new Keyframe(REWARD_PIP_POP_DURATION, 1f, REWARD_PIP_POP_EASING)),
                        v -> pipScale = v,
                        () -> pipShownAt = heartShownAt = Util.getMillis()))));
    }

    @Override
    protected void renderSelf(IRenderContext context) {
        if (!pipOverlayOnly) {
            if (!silhouette) {
                super.renderSelf(context);
            } else if (context instanceof MinecraftRenderContext silhouetteContext) {
                // The parent's renderSelf hands anything drawn above 16 screen px to gelatin's
                // HiResItems, which bypasses the item pipeline the silhouette shader hooks, so a
                // magnified never-caught fish (the info page's 4x portrait) would turn full colour.
                // Magnified: black-tint the fish's own sprite (crisp at any size). Disc size: the
                // atlas silhouette, drawn straight through graphics.item.
                ResourceLocation sprite = getGlobalScale() > MAGNIFIED_SCALE ? spriteFor(getItemStack()) : null;
                if (sprite != null) {
                    drawTintedSprite(silhouetteContext.getGraphics(), sprite);
                } else {
                    FishtasticGlintState.SILHOUETTE_REQUESTED.set(Boolean.TRUE);
                    try {
                        silhouetteContext.getGraphics().renderItem(getItemStack(), 0, 0);
                    } finally {
                        FishtasticGlintState.SILHOUETTE_REQUESTED.remove();
                    }
                }
            } else {
                FishtasticGlintState.SILHOUETTE_REQUESTED.set(Boolean.TRUE);
                try {
                    super.renderSelf(context);
                } finally {
                    FishtasticGlintState.SILHOUETTE_REQUESTED.remove();
                }
            }
        }

        if (pipScale <= 0f || !(context instanceof MinecraftRenderContext mcContext)) return;
        float heartAlpha = badgeAlpha(heartShownAt), pipAlpha = badgeAlpha(pipShownAt);
        if (bondAvailable && heartAlpha > 0f) {
            drawPopped(mcContext, heartAlpha,
                    size.x - BOND_HEART_W + REWARD_PIP_OFFSET_X + BOND_HEART_W / 2f,
                    size.y - BOND_HEART_H - REWARD_PIP_OFFSET_Y + BOND_HEART_H / 2f,
                    BOND_HEART_TEXTURE, (int) BOND_HEART_W, (int) BOND_HEART_H, 190f, 0f, 5, 4, 256, 256);
        }
        if (hasUnclaimedReward && pipAlpha > 0f) {
            drawPopped(mcContext, pipAlpha,
                    size.x - REWARD_PIP_SIZE + REWARD_PIP_OFFSET_X + REWARD_PIP_SIZE / 2f,
                    REWARD_PIP_OFFSET_Y + REWARD_PIP_SIZE / 2f,
                    REWARD_PIP_TEXTURE, (int) REWARD_PIP_SIZE, (int) REWARD_PIP_SIZE, 0f, 0f,
                    REWARD_PIP_TEXTURE_PX, REWARD_PIP_TEXTURE_PX, REWARD_PIP_TEXTURE_PX, REWARD_PIP_TEXTURE_PX);
        }
    }

    /** Above this on-screen scale the atlas silhouette is too coarse and the sprite is tinted instead. */
    private static final float MAGNIFIED_SCALE = 2.0f;
    private static final java.util.Map<ResourceLocation, int[]> SPRITE_SIZES = new java.util.HashMap<>();
    private static final java.util.Map<ResourceLocation, java.util.Optional<ResourceLocation>> SPRITES = new java.util.HashMap<>();

    /** {@code textures/item/fish/<id>.png}, or null when the item has no such sprite. */
    private static ResourceLocation spriteFor(ItemStack stack) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return SPRITES.computeIfAbsent(id, key -> {
            ResourceLocation texture = grill24.fishtastic.util.Ids.of(key.getNamespace(), "textures/item/fish/" + key.getPath() + ".png");
            return net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(texture).isPresent()
                    ? java.util.Optional.of(texture) : java.util.Optional.empty();
        }).orElse(null);
    }

    private static void drawTintedSprite(GuiGraphics graphics, ResourceLocation texture) {
        int[] size = SPRITE_SIZES.computeIfAbsent(texture, t -> {
            try (var in = net.minecraft.client.Minecraft.getInstance().getResourceManager().getResourceOrThrow(t).open();
                 var image = com.mojang.blaze3d.platform.NativeImage.read(in)) {
                return new int[]{image.getWidth(), image.getHeight()};
            } catch (java.io.IOException e) {
                return new int[]{16, 16};
            }
        });
        graphics.setColor(0x08 / 255f, 0x08 / 255f, 0x0A / 255f, 1f);
        // An animated fish texture is a vertical strip of square frames; blit only the first one.
        graphics.blit(texture, 0, 0, 16, 16, 0f, 0f, size[0], Math.min(size[1], size[0]), size[0], size[1]);
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    private static float badgeAlpha(long shownAt) {
        if (shownAt < 0L) return 1f;
        long fading = Util.getMillis() - shownAt - BADGE_HOLD_MS;
        return fading <= 0L ? 1f : Math.max(0f, 1f - (float) fading / BADGE_FADE_MS);
    }

    // GPU pose scale (like ItemButton's hover-zoom) instead of resizing the blit, which stepped visibly.
    // Vanilla's blit takes int coords, so the float centring lives in the pose; alpha is the shader colour.
    private void drawPopped(MinecraftRenderContext mcContext, float alpha, float centerX, float centerY,
                            ResourceLocation texture, int w, int h, float u, float v, int uw, int vh, int texW, int texH) {
        GuiGraphics graphics = mcContext.getGraphics();
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0);
        pose.scale(pipScale, pipScale, pipScale);
        pose.translate(-w / 2f, -h / 2f, 0);
        graphics.setColor(1f, 1f, 1f, alpha);
        graphics.blit(texture, 0, 0, w, h, u, v, uw, vh, texW, texH);
        graphics.setColor(1f, 1f, 1f, 1f);
        pose.popPose();
    }

    @Override
    protected String getDefaultDebugName() {
        return "SilhouetteItemButton(item=" + getItemStack().getItem() + ", silhouette=" + silhouette + ")";
    }
}
