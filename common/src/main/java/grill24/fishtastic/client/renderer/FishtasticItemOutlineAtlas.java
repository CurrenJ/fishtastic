package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import grill24.fishtastic.util.Ids;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Fishtastic-owned outline atlas: the 1.21.1 port of the 26.1.2 class of the same name (taken from
 * the rendering spike, docs/spike-1.21.1-rendering.md, with its findings applied).
 *
 * <p>Same design (padded slots, LRU eviction, bake at the head of {@code GameRenderer.render},
 * separate mask and outline atlases, compose-only re-bake for animated outlines), re-expressed on
 * the 1.21.1 immediate-mode API:
 * <ul>
 *   <li>{@code GpuTexture} + render-target overrides → two {@link TextureTarget} framebuffers.</li>
 *   <li>{@code ItemStackRenderState.submit} + {@code FeatureRenderDispatcher} → a direct
 *       {@code ItemRenderer.render} into a {@link MultiBufferSource}.</li>
 *   <li>{@code RenderPipeline} + params UBO → a {@link ShaderInstance} with plain uniforms, drawn
 *       per slot through {@link BufferUploader} ({@link FishtasticOutlineStyle#applyUniforms}).</li>
 * </ul>
 *
 * <p>Unlike 26.1.2 — where the GUI path samples vanilla's {@code GuiItemAtlas} — this atlas also
 * feeds the GUI outline ({@link FishtasticGuiOutlineRenderer}) and the encyclopedia silhouette
 * ({@link FishtasticSilhouetteEffect}, which reads the mask), because 1.21.1 renders GUI items
 * straight to the screen and has no GUI item atlas to sample.
 *
 * <p>All methods must be called on the render thread.
 */
public final class FishtasticItemOutlineAtlas {

    /** Id the composed outline atlas is registered under, for render types that bind by id. */
    public static final ResourceLocation TEXTURE_ID = Ids.of("fishtastic", "item_outline_atlas");

    /** The 16-px item tile is rendered at this many texels (4x resolution). */
    public static final int ITEM_RENDER_PX = 64;
    /** Outline margin per slot side, in texels (4 item pixels — matches the max outline width). */
    public static final int PAD_PX = 16;
    /** Full slot size in texels. Keep in step with FISHTASTIC_ATLAS_SLOT_PX in the bake shaders. */
    public static final int SLOT_PX = ITEM_RENDER_PX + 2 * PAD_PX;
    /** Fixed atlas texture size. 1024 / 96 = a 10x10 grid (100 slots). */
    public static final int TEXTURE_SIZE = 1024;
    /** Mask texels per item pixel; keep in step with FISHTASTIC_ATLAS_RES in the bake shaders. */
    public static final int TEXELS_PER_ITEM_PX = ITEM_RENDER_PX / 16;

    private static final int GRID = TEXTURE_SIZE / SLOT_PX;

    /** UV rect of a baked slot. v0 = visual top of the item (GL textures are bottom-up). */
    public record SlotView(float u0, float v0, float u1, float v1) {}

    private static final Hash.Strategy<ItemStack> STACK_STRATEGY = new Hash.Strategy<>() {
        @Override
        public int hashCode(@Nullable ItemStack stack) {
            return ItemStack.hashItemAndComponents(stack);
        }

        @Override
        public boolean equals(@Nullable ItemStack a, @Nullable ItemStack b) {
            if (a == b) return true;
            if (a == null || b == null) return false;
            return ItemStack.isSameItemSameComponents(a, b);
        }
    };

    private static final FishtasticItemOutlineAtlas INSTANCE = new FishtasticItemOutlineAtlas();

    public static FishtasticItemOutlineAtlas getInstance() {
        return INSTANCE;
    }

    private static final class Slot {
        final int x;
        final int y;
        long lastUsedFrame;
        boolean baked;
        boolean needsClear;
        /** The ring composed into the outline atlas; null for a mask-only slot. */
        @Nullable FishtasticOutlineStyle style;
        /** The style the outline atlas currently holds for this slot. */
        @Nullable FishtasticOutlineStyle composedStyle;
        long lastComposedFrame = -1;

        Slot(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private record PendingBake(ItemStack stack, Slot slot) {}

    private final Object2ObjectOpenCustomHashMap<ItemStack, Slot> slotsByStack =
            new Object2ObjectOpenCustomHashMap<>(STACK_STRATEGY);
    private final ArrayDeque<Slot> freeSlots = new ArrayDeque<>();
    private final ArrayDeque<PendingBake> bakeQueue = new ArrayDeque<>();
    private final List<Slot> composeScratch = new ArrayList<>();
    private long frameCounter;
    private boolean pendingGpuClear;

    /** Composed atlas: the outline ring only, transparent everywhere else. This is what is drawn. */
    private TextureTarget outlineTarget;
    /** Item sprite atlas, same slot layout. Sampled by the bake shaders and the silhouette. Has depth for 3D models. */
    private TextureTarget maskTarget;
    private AtlasTexture outlineTexture;
    private final PoseStack poseStack = new PoseStack();

    private FishtasticItemOutlineAtlas() {
        resetSlots();
    }

    private void resetSlots() {
        slotsByStack.clear();
        bakeQueue.clear();
        freeSlots.clear();
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                freeSlots.add(new Slot(x, y));
            }
        }
    }

    /** Drops all slot assignments; the GPU clear is deferred to the next bake. */
    public void invalidate() {
        resetSlots();
        pendingGpuClear = outlineTarget != null;
    }

    /** GL texture id of the composed outline atlas, or -1 before the first bake. */
    public int outlineTextureId() {
        return outlineTarget == null ? -1 : outlineTarget.getColorTextureId();
    }

    /** GL texture id of the mask atlas, or -1 before the first bake. */
    public int maskTextureId() {
        return maskTarget == null ? -1 : maskTarget.getColorTextureId();
    }

    /** Samples the outline atlas LINEAR, as the GUI draw wants (the world render type sets NEAREST). */
    public void useLinearFiltering() {
        if (outlineTexture != null) outlineTexture.setFilter(true, false);
    }

    /** For the render self-test, which dumps both atlases to PNG. */
    public @Nullable RenderTarget outlineTarget() {
        return outlineTarget;
    }

    public @Nullable RenderTarget maskTarget() {
        return maskTarget;
    }

    /**
     * Returns the UV rect for {@code stack}'s baked slot with {@code style}'s ring, or {@code null}
     * if it is not baked yet (it is then queued and available next frame) or the atlas is full of
     * recently-used slots. A null {@code style} asks for the mask only.
     */
    public @Nullable SlotView requestSlot(ItemStack stack, @Nullable FishtasticOutlineStyle style) {
        Slot slot = slotsByStack.get(stack);
        if (slot != null) {
            slot.lastUsedFrame = frameCounter;
            if (style != null) slot.style = style;
            if (!slot.baked) return null;
            // A slot first requested mask-only (or with another ring) is re-composed next frame.
            if (slot.style != null && !slot.style.equals(slot.composedStyle)) return null;
            return viewOf(slot);
        }

        slot = allocateSlot();
        if (slot == null) {
            return null;
        }
        slot.lastUsedFrame = frameCounter;
        slot.baked = false;
        slot.style = style;
        slot.composedStyle = null;
        ItemStack key = stack.copyWithCount(1);
        slotsByStack.put(key, slot);
        bakeQueue.add(new PendingBake(key, slot));
        return null;
    }

    /** The item's own 16x16 area within its slot (inside the padding), for mask sampling. */
    public static SlotView innerView(SlotView slot) {
        float inset = (float) PAD_PX / TEXTURE_SIZE;
        return new SlotView(slot.u0() + inset, slot.v0() - inset, slot.u1() - inset, slot.v1() + inset);
    }

    private @Nullable Slot allocateSlot() {
        Slot free = freeSlots.poll();
        if (free != null) {
            return free;
        }
        Map.Entry<ItemStack, Slot> oldest = null;
        for (Map.Entry<ItemStack, Slot> entry : slotsByStack.object2ObjectEntrySet()) {
            if (entry.getValue().lastUsedFrame >= frameCounter - 1) continue;
            if (oldest == null || entry.getValue().lastUsedFrame < oldest.getValue().lastUsedFrame) {
                oldest = entry;
            }
        }
        if (oldest == null) {
            return null;
        }
        Slot slot = oldest.getValue();
        slotsByStack.remove(oldest.getKey());
        Iterator<PendingBake> it = bakeQueue.iterator();
        while (it.hasNext()) {
            if (it.next().slot() == slot) it.remove();
        }
        slot.needsClear = true;
        return slot;
    }

    /**
     * Bakes queued items and re-composes animated or re-styled slots. Called at the head of
     * {@code GameRenderer.render}, before any level or GUI drawing, while the shared
     * {@code BufferSource} is idle. Leaves the main render target bound and the projection and
     * model-view matrices restored.
     */
    public void processBakeQueue(Minecraft minecraft) {
        frameCounter++;

        boolean cleared = false;
        if (pendingGpuClear) {
            if (outlineTarget != null) {
                clearTarget(outlineTarget);
                clearTarget(maskTarget);
                cleared = true;
            }
            pendingGpuClear = false;
        }

        composeScratch.clear();
        boolean hasBakes = !bakeQueue.isEmpty();
        collectSlotsToRecompose();
        if (!hasBakes && composeScratch.isEmpty()) {
            if (cleared) minecraft.getMainRenderTarget().bindWrite(true);
            return;
        }

        ensureInitialized();
        RenderSystem.backupProjectionMatrix();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        // Y-down pixel space: atlas pixel row 0 is the top of the framebuffer (texture v = 1).
        RenderSystem.setProjectionMatrix(
                new Matrix4f().setOrtho(0.0F, TEXTURE_SIZE, TEXTURE_SIZE, 0.0F, -1000.0F, 1000.0F),
                VertexSorting.ORTHOGRAPHIC_Z);

        if (hasBakes) {
            maskTarget.bindWrite(true);
            PendingBake pending;
            while ((pending = bakeQueue.poll()) != null) {
                drawToSlot(minecraft, pending.slot(), pending.stack());
                pending.slot().baked = true;
                pending.slot().needsClear = false;
                pending.slot().lastComposedFrame = frameCounter;
                if (pending.slot().style != null) composeScratch.add(pending.slot());
            }
        }

        composeSlots(composeScratch);

        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
        minecraft.getMainRenderTarget().bindWrite(true);
    }

    /** Baked slots on screen last frame whose ring animates, or whose requested ring isn't composed yet. */
    private void collectSlotsToRecompose() {
        if (outlineTarget == null) return;
        for (Slot slot : slotsByStack.values()) {
            if (!slot.baked || slot.style == null) continue;
            if (slot.lastUsedFrame < frameCounter - 1) continue;
            if (slot.lastComposedFrame == frameCounter) continue;
            if (!slot.style.isAnimated() && slot.style.equals(slot.composedStyle)) continue;
            slot.lastComposedFrame = frameCounter;
            composeScratch.add(slot);
        }
    }

    /**
     * Draws each slot's outline ring into the outline atlas, sampling the mask atlas.
     *
     * <p>One draw per slot: the style parameters are plain uniforms in 1.21.1, so they must be
     * set before each draw rather than bound per batch as the 26.1.2 UBO was. The bake shaders
     * never discard and blending is off, so each full-slot quad fully overwrites its slot.
     */
    private void composeSlots(List<Slot> slots) {
        if (slots.isEmpty()) {
            return;
        }
        outlineTarget.bindWrite(true);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShaderTexture(0, maskTarget.getColorTextureId());
        Matrix4f identity = new Matrix4f();
        for (Slot slot : slots) {
            FishtasticOutlineStyle style = slot.style;
            if (style == null) continue;
            ShaderInstance shader = style.bakeShader();
            if (shader == null) continue; // shaders not loaded (resource reload in progress)
            style.applyUniforms(shader);
            RenderSystem.setShader(() -> shader);

            int left = slot.x * SLOT_PX;
            int top = slot.y * SLOT_PX;
            int bottom = top + SLOT_PX;
            SlotView uv = viewOf(slot);
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, FishtasticShaders.OUTLINE_BAKE_FORMAT);
            buffer.addVertex(identity, left, top, 0.0F).setUv(uv.u0(), uv.v0());
            buffer.addVertex(identity, left, bottom, 0.0F).setUv(uv.u0(), uv.v1());
            buffer.addVertex(identity, left + SLOT_PX, bottom, 0.0F).setUv(uv.u1(), uv.v1());
            buffer.addVertex(identity, left + SLOT_PX, top, 0.0F).setUv(uv.u1(), uv.v0());
            BufferUploader.drawWithShader(buffer.buildOrThrow());
            slot.composedStyle = style;
        }
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private void ensureInitialized() {
        if (outlineTarget != null) {
            return;
        }
        outlineTarget = new TextureTarget(TEXTURE_SIZE, TEXTURE_SIZE, false, Minecraft.ON_OSX);
        maskTarget = new TextureTarget(TEXTURE_SIZE, TEXTURE_SIZE, true, Minecraft.ON_OSX);
        // Both start NEAREST (TextureTarget's default). The mask stays that way: the bake shaders
        // scan discrete texels and must not filter across slot borders. The outline atlas is
        // switched per use rather than toggled around each draw (spike finding F3): the GUI draw
        // asks for LINEAR (useLinearFiltering — it holds 4 texels per GUI pixel, and NEAREST
        // minification at GUI scales below 4 drops the 1-texel rings), and the world render type
        // binds it NEAREST through its own texture shard.
        clearTarget(outlineTarget);
        clearTarget(maskTarget);
        outlineTexture = new AtlasTexture(outlineTarget.getColorTextureId());
        Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, outlineTexture);
    }

    private static void clearTarget(RenderTarget target) {
        target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        target.clear(Minecraft.ON_OSX);
    }

    /** Renders the item model into the mask atlas slot, centered inside the padding. */
    private void drawToSlot(Minecraft minecraft, Slot slot, ItemStack stack) {
        int left = slot.x * SLOT_PX;
        int top = slot.y * SLOT_PX;
        int bottom = top + SLOT_PX;

        // Framebuffer scissor coordinates are bottom-up.
        RenderSystem.enableScissor(left, TEXTURE_SIZE - bottom, SLOT_PX, SLOT_PX);
        if (slot.needsClear) {
            RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        }

        BakedModel model = minecraft.getItemRenderer().getModel(stack, minecraft.level, null, 0);
        boolean flat = !model.usesBlockLight();
        if (flat) {
            Lighting.setupForFlatItems();
        } else {
            Lighting.setupFor3DItems();
        }

        this.poseStack.pushPose();
        this.poseStack.translate(left + SLOT_PX / 2.0F, top + SLOT_PX / 2.0F, 0.0F);
        // Scale by the item render size, not the slot size — this is what creates the padding.
        this.poseStack.scale(ITEM_RENDER_PX, -ITEM_RENDER_PX, ITEM_RENDER_PX);
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        minecraft.getItemRenderer().render(stack, ItemDisplayContext.GUI, false, this.poseStack, bufferSource,
                15728880, OverlayTexture.NO_OVERLAY, model);
        bufferSource.endBatch();
        this.poseStack.popPose();

        RenderSystem.disableScissor();
        Lighting.setupFor3DItems();
    }

    private static SlotView viewOf(Slot slot) {
        float slotUv = (float) SLOT_PX / TEXTURE_SIZE;
        float u0 = slot.x * slotUv;
        float v0 = 1.0F - slot.y * slotUv;
        return new SlotView(u0, v0, u0 + slotUv, v0 - slotUv);
    }

    /**
     * Wraps the outline framebuffer's colour texture so render types can bind it by id. The texture
     * belongs to the {@link TextureTarget}; this wrapper never allocates or loads anything.
     */
    private static final class AtlasTexture extends AbstractTexture {
        AtlasTexture(int glId) {
            this.id = glId;
        }

        @Override
        public void load(ResourceManager resourceManager) {
        }

        @Override
        public void releaseId() {
            // Owned by the TextureTarget — never delete it from here.
        }
    }
}
