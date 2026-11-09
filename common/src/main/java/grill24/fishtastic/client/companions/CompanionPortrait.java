package grill24.fishtastic.client.companions;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishtastic.client.renderer.CompanionFishPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * The fish on a companion card (layout-spec §3.1): the live companion model, posed by its idle swim.
 *
 * <p>Port note: 26.1.2 renders this through a picture-in-picture texture ({@code CompanionPortraitPip});
 * that API does not exist here, so the model is drawn straight into the GUI with a 3D pose, the way
 * vanilla draws an entity in the inventory. Without a level it falls back to the item sprite with a
 * slow bob, so a card is never empty.
 */
public final class CompanionPortrait {
    /** Fish fill this share of the box's width, leaving room for the swim's sway. */
    private static final float FILL = 0.78f;
    private static final int FULL_BRIGHT = 15728880;
    /** Lifts the model clear of the card panel; its own depth spans about ±size/2 around this. */
    private static final float Z = 100f;

    /** Poses are built per fish (resolving visuals isn't free); cached per slot until the fish changes. */
    private static final Map<Integer, CachedPose> POSES = new HashMap<>();

    private record CachedPose(ItemStack fish, CompanionFishPose pose) {}

    private CompanionPortrait() {}

    public static void draw(GuiGraphics g, ItemStack fish, int x, int y, int size, int slot, long nowMs) {
        if (drawModel(g, fish, x, y, size, slot)) return;
        float bob = (float) Math.sin((nowMs / 1000.0 + slot * 0.7) * Math.PI * 0.6) * 1.2f;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y + bob, 0);
        pose.scale(size / 16f, size / 16f, 1f);
        g.renderItem(fish, 0, 0);
        pose.popPose();
    }

    private static boolean drawModel(GuiGraphics g, ItemStack fish, int x, int y, int size, int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (fish.isEmpty() || mc.level == null) return false;
        CachedPose cached = POSES.get(slot);
        if (cached == null || !ItemStack.isSameItemSameTags(cached.fish(), fish)) {
            cached = new CachedPose(fish.copy(), new CompanionFishPose(fish, mc.level, 0f));
            POSES.put(slot, cached);
        }
        CompanionFishPose fishPose = cached.pose();
        float ticks = (net.minecraft.Util.getMillis() % 3_600_000L) / 50f + slot * 37f;
        float scale = size * FILL / Math.max(0.05f, fishPose.length());

        g.flush();
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x + size / 2f, y + size / 2f, Z);
        // GUI orientation is y-down, the model's is y-up.
        pose.scale(scale, -scale, scale);
        Lighting.setupForFlatItems();
        fishPose.submit(pose, g.bufferSource(), FULL_BRIGHT, ticks, 0f, 0f, 0f);
        g.flush();
        Lighting.setupFor3DItems();
        pose.popPose();
        return true;
    }
}
