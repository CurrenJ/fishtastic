package grill24.fishtastic.client.companions;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishtastic.client.renderer.CompanionFishPose;
import io.github.currenj.gelatinui.extension.IGuiGraphicsExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws a companion card's fish as the live companion model (layout-spec §3.1): the same
 * {@link CompanionFishPose} the player's perched companion uses, in its idle swim, rendered into an
 * off-screen picture-in-picture texture the size of the card's portrait box.
 *
 * <p>Registered per loader (Fabric's {@code PictureInPictureRendererRegistry}, NeoForge's and Forge's
 * register events). A picture-in-picture renderer owns one texture and every render happens before the
 * blits, so this dispatcher keeps one delegate (one texture) per card slot, like Gelatin's
 * {@code HiResItemPipRenderer} does per model.
 */
public final class CompanionPortraitPip extends PictureInPictureRenderer<CompanionPortraitPip.State> {
    /** Fish fill this share of the box's width, leaving room for the swim's sway. */
    private static final float FILL = 0.78f;
    /**
     * The portrait fits the fish's length to the box, but the idle bob is in blocks, so a short fish
     * (a betta) bobbed several of its own heights and left the box. On a card the bob peaks at this
     * share of the fish's length; the box leaves about 0.64 lengths above and below the centre line.
     */
    static final float BOB_CAP = 0.15f;
    /** Self-test only (companionportraitbob scene): false draws the uncapped bob for an A/B. */
    public static boolean bobCapEnabled = true;

    /** The bob a card draws at {@code ticks}, blocks: the pose's own, scaled down to {@link #BOB_CAP}. */
    public static float portraitBob(CompanionFishPose fish, float ticks) {
        float amplitude = fish.idleBobAmplitude(), cap = BOB_CAP * fish.length();
        float bob = fish.idleBob(ticks);
        return bobCapEnabled && amplitude > cap ? bob * cap / amplitude : bob;
    }

    /** The per-slot clock {@link #submit} uses, so a scene can predict a card's bob. */
    public static float slotTicks(int slot, long millis) {
        return (millis % 3_600_000L) / 50f + slot * 37f;
    }

    /** Self-test: the cached pose a card is drawing, or null. */
    public static CompanionFishPose poseFor(int slot) {
        CachedPose cached = POSES.get(slot);
        return cached == null ? null : cached.pose();
    }
    private static final int FULL_BRIGHT = 15728880;

    /** The renderer the loader registered; only its close (the GUI renderer closing) frees the delegates. */
    @Nullable private static CompanionPortraitPip instance;
    private final MultiBufferSource.BufferSource bufferSource;
    /**
     * One texture per card slot, shared by every instance: Fabric hands the 2nd and 3rd portrait of a
     * frame to extra pooled renderers from our factory and closes them on any frame with fewer
     * portraits, so those extras must not own (or free) anything.
     */
    private static final Map<Integer, Delegate> DELEGATES = new HashMap<>();
    /** Poses are built per fish (resolving visuals isn't free); cached per slot until the fish changes. */
    private static final Map<Integer, CachedPose> POSES = new HashMap<>();

    private record CachedPose(ItemStack fish, CompanionFishPose pose) {}

    public CompanionPortraitPip(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
        this.bufferSource = bufferSource;
    }

    /**
     * The first call makes the registered renderer. Later calls (Fabric's per-frame pool) get a
     * separate forwarding instance: returning the registered one let the pool close it when the
     * screen shut, so every later open fell back to item sprites.
     */
    public static CompanionPortraitPip getOrCreate(MultiBufferSource.BufferSource bufferSource) {
        CompanionPortraitPip pip = new CompanionPortraitPip(bufferSource);
        if (instance == null) instance = pip;
        return pip;
    }

    private static volatile boolean registered;

    /**
     * Called from each loader's registration. NeoForge builds picture-in-picture renderers lazily,
     * on the first submitted state, so waiting for an instance would never submit one.
     */
    public static void markRegistered() { registered = true; }

    public static boolean isRegistered() { return registered || instance != null; }

    /** Self-test: the registered renderer is still open (not closed by a loader's pool). */
    public static boolean hasLiveRenderer() { return instance != null; }

    /**
     * Queues the live fish into the local square ({@code x}, {@code y}, {@code size}) under the
     * current pose. Returns false (drawing nothing) when no renderer is registered.
     */
    public static boolean submit(GuiGraphicsExtractor g, ItemStack fish, int x, int y, int size, int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (!isRegistered() || fish.isEmpty() || mc.level == null) return false;
        Matrix3x2f pose = new Matrix3x2f(g.pose());
        float scaleX = (float) Math.sqrt(pose.m00 * pose.m00 + pose.m01 * pose.m01);
        if (scaleX < 1e-3f) return false;
        int tex = Math.max(1, Math.round(size * scaleX));
        Matrix3x2f blit = new Matrix3x2f(pose).translate(x, y).scale(size / (float) tex, size / (float) tex);
        CachedPose cached = POSES.get(slot);
        if (cached == null || !ItemStack.isSameItemSameComponents(cached.fish(), fish)) {
            cached = new CachedPose(fish.copy(), new CompanionFishPose(fish, mc.level, 0f));
            POSES.put(slot, cached);
        }
        float ticks = slotTicks(slot, net.minecraft.util.Util.getMillis());
        ((IGuiGraphicsExtension) g).gelatinui$submitPictureInPicture(
                new State(cached.pose(), slot, ticks, tex, blit, null));
        return true;
    }

    /** The texture rectangle is in whole GUI pixels at the origin; {@link #pose()} places it. */
    public record State(CompanionFishPose fish, int slot, float ticks, int texSize, Matrix3x2f pose,
                        @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
            implements PictureInPictureRenderState {
        State(CompanionFishPose fish, int slot, float ticks, int texSize, Matrix3x2f pose, @Nullable ScreenRectangle scissor) {
            this(fish, slot, ticks, texSize, pose, scissor, bounds(texSize, pose, scissor));
        }

        @Override public int x0() { return 0; }
        @Override public int y0() { return 0; }
        @Override public int x1() { return texSize; }
        @Override public int y1() { return texSize; }

        /** Pixels per block: the fish's drawn length spans {@link #FILL} of the box. */
        @Override public float scale() { return texSize * FILL / Math.max(0.05f, fish.length()); }

        private static ScreenRectangle bounds(int texSize, Matrix3x2f pose, @Nullable ScreenRectangle scissor) {
            ScreenRectangle b = new ScreenRectangle(0, 0, texSize, texSize).transformMaxBounds(pose);
            return scissor != null ? scissor.intersection(b) : b;
        }
    }

    @Override public Class<State> getRenderStateClass() { return State.class; }

    @Override
    public void prepare(State state, GuiRenderState guiRenderState, int guiScale) {
        DELEGATES.computeIfAbsent(state.slot(), s -> new Delegate(bufferSource)).prepare(state, guiRenderState, guiScale);
    }

    @Override protected void renderToTexture(State state, PoseStack poseStack) { draw(state, poseStack); }

    @Override protected float getTranslateY(int height, int guiScale) { return height / 2.0F; }

    @Override protected String getTextureLabel() { return "fishtastic_companion_portrait"; }

    @Override
    public void close() {
        if (instance == this) {
            DELEGATES.values().forEach(PictureInPictureRenderer::close);
            DELEGATES.clear();
            instance = null;
        }
        super.close();
    }

    /** GUI orientation is y-down, the model's is y-up: flip, then pose and submit like the perch layer. */
    private static void draw(State state, PoseStack poseStack) {
        poseStack.scale(1.0F, -1.0F, -1.0F);
        Minecraft mc = Minecraft.getInstance();
        mc.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_FLAT);
        // Cancel the part of this frame's bob above the cap; phase and timing stay the pose's own.
        float excess = state.fish().idleBob(state.ticks()) - portraitBob(state.fish(), state.ticks());
        if (excess != 0f) poseStack.translate(0f, -excess, 0f);
        FeatureRenderDispatcher dispatcher = mc.gameRenderer.getFeatureRenderDispatcher();
        state.fish().submit(poseStack, dispatcher.getSubmitNodeStorage(), FULL_BRIGHT, state.ticks(), 0f, 0f, 0f, false);
        dispatcher.renderAllFeatures();
    }

    private static final class Delegate extends PictureInPictureRenderer<State> {
        Delegate(MultiBufferSource.BufferSource bufferSource) { super(bufferSource); }
        @Override public Class<State> getRenderStateClass() { return State.class; }
        @Override protected void renderToTexture(State state, PoseStack poseStack) { draw(state, poseStack); }
        @Override protected boolean textureIsReadyToBlit(State state) { return false; }
        @Override protected float getTranslateY(int height, int guiScale) { return height / 2.0F; }
        @Override protected String getTextureLabel() { return "fishtastic_companion_portrait"; }
    }
}
