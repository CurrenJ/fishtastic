package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import grill24.fishtastic.itemeffect.ItemEffect;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Fishtastic-owned item render atlas used for in-world outline rendering.
 *
 * <p>Vanilla's {@code GuiItemAtlas} cannot be reused for world rendering: its allocator
 * reclaims any slot not used by a GUI item <em>this frame</em>, its slots have no padding
 * (outlines would clip at the sprite edge), and it bakes mid-GUI-render via render-target
 * overrides.  This atlas solves all three: slots are padded so outlines can extend past the
 * item sprite, eviction is LRU and never touches a slot used within the last frame, and all
 * bakes happen at a single safe point (head of {@code GameRenderer.render}, before any level
 * or GUI drawing).
 *
 * <p>Each slot is {@link #SLOT_PX} texels square with the 16-px item tile rendered at
 * {@link #ITEM_RENDER_PX} texels centered inside it, leaving {@link #PAD_PX} texels of
 * margin per side for the outline.  The slot grid layout and UV conventions intentionally
 * mirror {@code GuiItemAtlas} so the outline fragment shaders share the same slot-clamping
 * math (anchored at V=1).
 *
 * <p>Frame flow:
 * <ol>
 *   <li>{@code GameRendererMixin} calls {@link #processBakeQueue} at the head of
 *       {@code GameRenderer.render} — bakes queued items while the shared
 *       {@code SubmitNodeStorage}/{@code FeatureRenderDispatcher}/{@code BufferSource}
 *       are idle.</li>
 *   <li>Entity renderer mixins call {@link #requestSlot} during render-state extraction.
 *       A baked slot returns its UV rect; an unbaked item is queued and returns
 *       {@code null} (no outline for one frame).</li>
 * </ol>
 *
 * <p>All methods must be called on the render thread.
 */
public final class FishtasticItemOutlineAtlas {

    /** Identifier the composed outline atlas is registered under in the {@code TextureManager}. */
    public static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath("fishtastic", "item_outline_atlas");
    /** Identifier the item mask atlas is registered under (sampled by the bake shaders). */
    public static final Identifier MASK_TEXTURE_ID = Identifier.fromNamespaceAndPath("fishtastic", "item_outline_mask_atlas");

    /** The 16-px item tile is rendered at this many texels (4x resolution). */
    public static final int ITEM_RENDER_PX = 64;
    /** Outline margin per slot side, in texels (4 item pixels — matches the max outline width). */
    public static final int PAD_PX = 16;
    /** Full slot size in texels. */
    public static final int SLOT_PX = ITEM_RENDER_PX + 2 * PAD_PX;
    /** Fixed atlas texture size. 1024 / 96 = a 10x10 grid (100 slots). */
    public static final int TEXTURE_SIZE = 1024;

    private static final int GRID = TEXTURE_SIZE / SLOT_PX;

    /** UV rect of a baked slot. Follows GuiItemAtlas conventions: v0 = visual top of the item. */
    public record SlotView(float u0, float v0, float u1, float v1) {}

    // ----- Slot bookkeeping (CPU side, safe to touch from any render-thread call) -----

    /**
     * Item visual identity: same item + same components = same slot. Count is ignored.
     * Must be declared before INSTANCE — the singleton's constructor builds a map with it.
     */
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
        /** True when the slot previously held a different item and must be cleared before redraw. */
        boolean needsClear;
        /**
         * The effect whose outline is composed into this slot. Held so animated slots can be
         * re-composed each frame without the caller re-supplying it.
         */
        ItemEffect effect;
        /** Guards against a freshly baked animated slot being queued for composition twice in a frame. */
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
    /** Reused per frame by {@link #processBakeQueue} so collecting slots to compose allocates nothing. */
    private final List<Slot> composeScratch = new ArrayList<>();
    private long frameCounter;
    private boolean pendingGpuClear;

    // ----- GPU resources (lazily created on first bake) -----

    /** Composed atlas: the outline ring only, transparent everywhere else. This is what is drawn. */
    private GpuTexture texture;
    private GpuTextureView textureView;
    /**
     * Item sprite atlas, same slot layout as {@link #texture}. Only ever sampled by the bake
     * shaders, which need the item's alpha to find its silhouette edge. Kept as a separate texture
     * rather than composing in place because a single pass cannot both read and write one texture —
     * and keeping it resident lets animated slots re-compose without re-rendering the item model.
     */
    private GpuTexture maskTexture;
    private GpuTextureView maskTextureView;
    private GpuTexture depthTexture;
    private GpuTextureView depthTextureView;
    private final PoseStack poseStack = new PoseStack();
    /**
     * Identity pose for the compose quad, whose vertices are already in atlas-pixel space.
     * Held as a field rather than allocated per call: {@link #recomposeAnimatedSlots} runs this
     * every frame for every animated item on screen. Never mutated.
     */
    private final PoseStack composePoseStack = new PoseStack();
    private final Projection projection = new Projection();
    private ProjectionMatrixBuffer projectionMatrixBuffer;
    /** Scratch render state, resolved and cleared per bake. */
    private final ItemStackRenderState bakeRenderState = new ItemStackRenderState();

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

    /**
     * Drops all slot assignments and queued bakes.  Called when item effects reload —
     * item models or effect definitions may have changed, so baked content is stale.
     * Safe to call from the logical thread: the GPU-side clear is deferred to the next
     * {@link #processBakeQueue} on the render thread.
     *
     * <p>The flag is only armed when the texture already exists.  Arming it before the
     * first bake ever runs would leave it pending across texture creation, and the
     * deferred clear would then wipe the first frame's bakes one frame later — leaving
     * those slots marked baked but empty (no outline for the rest of the session).
     * A freshly created texture is cleared at creation, so there is nothing to defer.
     */
    public void invalidate() {
        resetSlots();
        pendingGpuClear = texture != null;
    }

    /**
     * Returns the UV rect for {@code stack}'s baked slot, or {@code null} if the item is
     * not yet baked (in which case it has been queued and will be available next frame)
     * or the atlas is full of recently-used slots.
     */
    public @Nullable SlotView requestSlot(ItemStack stack, ItemEffect effect) {
        Slot slot = slotsByStack.get(stack);
        if (slot != null) {
            slot.lastUsedFrame = frameCounter;
            // The effect for a given stack is resolved fresh each frame and can change when the
            // effect registry reloads; keep the slot in step so re-composes use current params.
            slot.effect = effect;
            return slot.baked ? viewOf(slot) : null;
        }

        slot = allocateSlot();
        if (slot == null) {
            return null;
        }
        slot.lastUsedFrame = frameCounter;
        slot.baked = false;
        slot.effect = effect;
        // Defensive copy: the caller's stack belongs to a live entity and may mutate.
        ItemStack key = stack.copyWithCount(1);
        slotsByStack.put(key, slot);
        bakeQueue.add(new PendingBake(key, slot));
        return null;
    }

    private @Nullable Slot allocateSlot() {
        Slot free = freeSlots.poll();
        if (free != null) {
            return free;
        }
        // LRU-evict, but never a slot referenced this frame or last frame — a SlotView
        // for it may still be held by an in-flight render state.
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
        // Drop any queued bake for the evicted assignment so it cannot overwrite the new owner.
        Iterator<PendingBake> it = bakeQueue.iterator();
        while (it.hasNext()) {
            if (it.next().slot() == slot) it.remove();
        }
        slot.needsClear = true;
        return slot;
    }

    /**
     * Bakes all queued items into the atlas.  Must be called at the head of
     * {@code GameRenderer.render}, before any level/GUI submission — this borrows the
     * shared {@code SubmitNodeStorage}, {@code FeatureRenderDispatcher} and
     * {@code BufferSource} exactly like {@code GuiItemAtlas} does, which is only safe
     * while they are idle.  Also clobbers {@code RenderSystem}'s projection matrix
     * (harmless here: level and GUI rendering set their own projections afterwards).
     */
    public void processBakeQueue(Minecraft minecraft) {
        frameCounter++;

        if (pendingGpuClear) {
            // texture can only be null here if invalidate() raced texture creation;
            // consume the flag unconditionally — it must never survive past this point,
            // or it would wipe bakes performed later this frame on the next frame.
            if (texture != null) {
                RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(texture, 0, depthTexture, 1.0);
                RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(maskTexture, 0, depthTexture, 1.0);
            }
            pendingGpuClear = false;
        }

        composeScratch.clear();

        if (!bakeQueue.isEmpty()) {
            ensureInitialized();
            PendingBake pending;
            while ((pending = bakeQueue.poll()) != null) {
                minecraft.getItemModelResolver()
                        .updateForTopItem(bakeRenderState, pending.stack(), ItemDisplayContext.GUI, minecraft.level, null, 0);
                drawToSlot(minecraft, pending.slot(), bakeRenderState);
                bakeRenderState.clear();
                pending.slot().baked = true;
                pending.slot().needsClear = false;
                pending.slot().lastComposedFrame = frameCounter;
                composeScratch.add(pending.slot());
            }
        }

        collectAnimatedSlots();
        composeSlots(minecraft, composeScratch);
    }

    /**
     * Queues on-screen slots whose effect animates (the legendary pinwheel) for re-composition, so
     * their rotation advances every frame.
     *
     * <p>Only the compose pass is repeated — the item model is <em>not</em> re-rendered, because the
     * mask atlas already holds its sprite. That keeps the per-frame cost to one full-slot quad per
     * animated item on screen, which is why re-baking every frame is affordable at all.
     *
     * <p>Slots not touched within the last frame are skipped: an off-screen item's outline does not
     * need to keep spinning, and it will be re-composed on the frame it reappears. Slots already
     * queued by a fresh bake this frame are skipped too, via {@code lastComposedFrame}.
     */
    private void collectAnimatedSlots() {
        if (texture == null) {
            return;
        }
        for (Slot slot : slotsByStack.values()) {
            if (!slot.baked || slot.effect == null || !slot.effect.isOutlineAnimated()) continue;
            if (slot.lastUsedFrame < frameCounter - 1) continue;
            if (slot.lastComposedFrame == frameCounter) continue;
            slot.lastComposedFrame = frameCounter;
            composeScratch.add(slot);
        }
    }

    /**
     * Draws the outline ring for every slot in {@code slots} into the composed atlas, sampling each
     * item's silhouette from the mask atlas.
     *
     * <p><b>All slots batch into a single draw.</b> The bake shaders take no per-slot state — they
     * derive the slot's UV bounds and pinwheel centre per-fragment from {@code texCoord0} — so one
     * buffer holding many slots' quads composes each correctly. Slots whose effects differ land in
     * different render types and are flushed as separate batches by {@code endBatch}, automatically.
     *
     * <p>There is deliberately <b>no clear and no scissor</b>. The bake shaders never discard: every
     * fragment writes, transparent black where there is no ring, and blending is off — so a quad
     * covering the full slot overwrites it completely. That removes a per-slot clear (which could
     * not have batched, since animated slots are scattered across the grid) and removes the scissor,
     * which is what made batching impossible in the first place. Re-introducing a {@code discard} in
     * the bake shaders would silently break this: last frame's pinwheel blades would never be erased.
     */
    private void composeSlots(Minecraft minecraft, List<Slot> slots) {
        if (slots.isEmpty()) {
            return;
        }
        // Render-target and projection state is set once around the whole batch, not per slot —
        // the draw happens at endBatch, so the override must still be active then.
        RenderSystem.outputColorTextureOverride = this.textureView;
        RenderSystem.outputDepthTextureOverride = this.depthTextureView;
        this.projection.setupOrtho(-1000.0F, 1000.0F, TEXTURE_SIZE, TEXTURE_SIZE, true);
        RenderSystem.setProjectionMatrix(this.projectionMatrixBuffer.getBuffer(this.projection), ProjectionType.ORTHOGRAPHIC);

        // Vertices are absolute atlas-pixel coordinates in the ortho space set up above (Y down),
        // so each quad covers exactly its slot and its UVs map 1:1 onto the mask atlas slot.
        var pose = this.composePoseStack.last();
        for (Slot slot : slots) {
            ItemEffect effect = slot.effect;
            if (effect == null) continue;
            int left = slot.x * SLOT_PX;
            int top = slot.y * SLOT_PX;
            int bottom = top + SLOT_PX;
            SlotView uv = viewOf(slot);
            VertexConsumer buffer = minecraft.renderBuffers().bufferSource().getBuffer(effect.outlineBakeRenderType());
            buffer.addVertex(pose, left, top, 0.0F).setUv(uv.u0(), uv.v0()).setColor(-1);
            buffer.addVertex(pose, left, bottom, 0.0F).setUv(uv.u0(), uv.v1()).setColor(-1);
            buffer.addVertex(pose, left + SLOT_PX, bottom, 0.0F).setUv(uv.u1(), uv.v1()).setColor(-1);
            buffer.addVertex(pose, left + SLOT_PX, top, 0.0F).setUv(uv.u1(), uv.v0()).setColor(-1);
        }
        minecraft.renderBuffers().bufferSource().endBatch();

        RenderSystem.outputColorTextureOverride = null;
        RenderSystem.outputDepthTextureOverride = null;
    }

    private void ensureInitialized() {
        if (texture != null) {
            return;
        }
        GpuDevice device = RenderSystem.getDevice();
        // Usage flags 13 (color) / 9 (depth) mirror GuiItemAtlas's texture creation.
        this.texture = device.createTexture("Fishtastic outline atlas", 13, TextureFormat.RGBA8, TEXTURE_SIZE, TEXTURE_SIZE, 1, 1);
        this.textureView = device.createTextureView(this.texture);
        this.maskTexture = device.createTexture("Fishtastic outline mask atlas", 13, TextureFormat.RGBA8, TEXTURE_SIZE, TEXTURE_SIZE, 1, 1);
        this.maskTextureView = device.createTextureView(this.maskTexture);
        // Shared by both passes: the item bake needs real depth testing for 3D models, and the
        // compose pass ignores depth entirely (CompareOp.ALWAYS, no write).
        this.depthTexture = device.createTexture("Fishtastic outline atlas depth", 9, TextureFormat.DEPTH32, TEXTURE_SIZE, TEXTURE_SIZE, 1, 1);
        this.depthTextureView = device.createTextureView(this.depthTexture);
        this.projectionMatrixBuffer = new ProjectionMatrixBuffer("fishtastic_outline_atlas");
        device.createCommandEncoder().clearColorAndDepthTextures(this.texture, 0, this.depthTexture, 1.0);
        device.createCommandEncoder().clearColorAndDepthTextures(this.maskTexture, 0, this.depthTexture, 1.0);
        Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, new AtlasTexture(this.texture, this.textureView));
        Minecraft.getInstance().getTextureManager().register(MASK_TEXTURE_ID, new AtlasTexture(this.maskTexture, this.maskTextureView));
    }

    /**
     * Renders the item model into the <em>mask</em> atlas slot, centered inside the padding.
     * Mirrors {@code GuiItemAtlas.drawToSlot}. The visible outline is produced from this by
     * {@link #composeSlot}.
     */
    private void drawToSlot(Minecraft minecraft, Slot slot, ItemStackRenderState item) {
        int left = slot.x * SLOT_PX;
        int top = slot.y * SLOT_PX;
        int bottom = top + SLOT_PX;
        GpuDevice device = RenderSystem.getDevice();
        if (slot.needsClear) {
            device.createCommandEncoder()
                    .clearColorAndDepthTextures(this.maskTexture, 0, this.depthTexture, 1.0, left, TEXTURE_SIZE - bottom, SLOT_PX, SLOT_PX);
        }

        this.poseStack.pushPose();
        this.poseStack.translate(left + SLOT_PX / 2.0F, top + SLOT_PX / 2.0F, 0.0F);
        // Scale by the item render size, not the slot size — this is what creates the padding.
        this.poseStack.scale(ITEM_RENDER_PX, -ITEM_RENDER_PX, ITEM_RENDER_PX);
        RenderSystem.outputColorTextureOverride = this.maskTextureView;
        RenderSystem.outputDepthTextureOverride = this.depthTextureView;
        this.projection.setupOrtho(-1000.0F, 1000.0F, TEXTURE_SIZE, TEXTURE_SIZE, true);
        RenderSystem.setProjectionMatrix(this.projectionMatrixBuffer.getBuffer(this.projection), ProjectionType.ORTHOGRAPHIC);
        RenderSystem.enableScissorForRenderTypeDraws(left, TEXTURE_SIZE - bottom, SLOT_PX, SLOT_PX);
        Lighting.Entry lighting = item.usesBlockLight() ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT;
        minecraft.gameRenderer.getLighting().setupFor(lighting);
        item.submit(this.poseStack, minecraft.gameRenderer.getSubmitNodeStorage(), 15728880, OverlayTexture.NO_OVERLAY, 0);
        minecraft.gameRenderer.getFeatureRenderDispatcher().renderAllFeatures();
        minecraft.renderBuffers().bufferSource().endBatch();
        RenderSystem.disableScissorForRenderTypeDraws();
        RenderSystem.outputColorTextureOverride = null;
        RenderSystem.outputDepthTextureOverride = null;
        this.poseStack.popPose();
    }

    private static SlotView viewOf(Slot slot) {
        float slotUv = (float) SLOT_PX / TEXTURE_SIZE;
        float u0 = slot.x * slotUv;
        float v0 = 1.0F - slot.y * slotUv;
        return new SlotView(u0, v0, u0 + slotUv, v0 - slotUv);
    }

    /**
     * Thin {@link AbstractTexture} wrapper so the atlas can be bound by name through
     * {@code RenderSetup.withTexture(Identifier)}.  The sampler is fully NEAREST + clamp:
     * the outline shaders sample discrete texels and must not filter across slot borders.
     */
    private static final class AtlasTexture extends AbstractTexture {
        AtlasTexture(GpuTexture texture, GpuTextureView textureView) {
            this.texture = texture;
            this.textureView = textureView;
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        }
    }
}
