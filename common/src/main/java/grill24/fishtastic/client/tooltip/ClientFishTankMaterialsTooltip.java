package grill24.fishtastic.client.tooltip;

import grill24.fishtastic.util.Ids;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Client renderer for {@link FishTankMaterialsTooltip}: a single row of three slot icons showing
 * the tank's frame / glass / sand blocks. Mirrors {@link ClientRodGearTooltip}, minus the ghost
 * icons — a fish tank always has all three materials.
 */
public class ClientFishTankMaterialsTooltip implements ClientTooltipComponent {
    private static final ResourceLocation SLOT_BACKGROUND_SPRITE =
            Ids.withDefaultNamespace("container/bundle/slot_background");
    private static final int SLOT_SIZE = 24;
    private static final int SLOT_GAP = 2;

    private final ItemStack[] materials;

    public ClientFishTankMaterialsTooltip(ItemStack frame, ItemStack glass, ItemStack sand) {
        this.materials = new ItemStack[] { frame, glass, sand };
    }

    @Override
    public int getHeight() {
        return SLOT_SIZE;
    }

    @Override
    public int getWidth(Font font) {
        return materials.length * SLOT_SIZE + (materials.length - 1) * SLOT_GAP;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        // 1.21.1's renderImage has no tooltip-width argument (26.1 passed one), so the slot row
        // starts at the tooltip's left edge the way vanilla's own ClientBundleTooltip does.
        int startX = x;
        for (int i = 0; i < materials.length; i++) {
            int slotX = startX + i * (SLOT_SIZE + SLOT_GAP);
            graphics.blitSprite(SLOT_BACKGROUND_SPRITE, slotX, y, SLOT_SIZE, SLOT_SIZE);
            ItemStack stack = materials[i];
            int iconX = slotX + 4;
            int iconY = y + 4;
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, iconX, iconY, 0);
                graphics.renderItemDecorations(font, stack, iconX, iconY);
            }
        }
    }
}
