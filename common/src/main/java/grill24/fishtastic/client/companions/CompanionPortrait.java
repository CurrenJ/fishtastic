package grill24.fishtastic.client.companions;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * The fish on a companion card (layout-spec §3.1): the live companion model, posed by its idle swim,
 * through {@link CompanionPortraitPip} wherever the loader registered that picture-in-picture
 * renderer. Elsewhere it falls back to the item sprite with a slow bob, so a card is never empty.
 */
public final class CompanionPortrait {
    private CompanionPortrait() {}

    public static void draw(GuiGraphicsExtractor g, ItemStack fish, int x, int y, int size, int slot, long nowMs) {
        if (CompanionPortraitPip.submit(g, fish, x, y, size, slot)) return;
        float bob = (float) Math.sin((nowMs / 1000.0 + slot * 0.7) * Math.PI * 0.6) * 1.2f;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y + bob);
        pose.scale(size / 16f, size / 16f);
        g.item(fish, 0, 0);
        pose.popMatrix();
    }
}
