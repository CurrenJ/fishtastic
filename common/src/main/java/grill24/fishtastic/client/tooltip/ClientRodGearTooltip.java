package grill24.fishtastic.client.tooltip;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.util.Ids;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class ClientRodGearTooltip implements ClientTooltipComponent {
    private static final ResourceLocation SLOT_BACKGROUND_SPRITE =
            Ids.withDefaultNamespace("container/bundle/slot_background");
    private static final int SLOT_SIZE = 24;
    private static final int SLOT_GAP = 2;
    private static final int ICON_SIZE = 16;

    // Drawn in place of the item icon when a slot is empty, in bait/hook/charm order.
    private static final ResourceLocation[] GHOST_TEXTURES = new ResourceLocation[] {
            Fishtastic.id("textures/item/bait/worms_ghost.png"),
            Fishtastic.id("textures/item/hook/hook_ghost.png"),
            Fishtastic.id("textures/item/charm/charm_ghost.png")
    };

    private final ItemStack[] gear;

    public ClientRodGearTooltip(ItemStack bait, ItemStack hook, ItemStack charm) {
        this.gear = new ItemStack[] { bait, hook, charm };
    }

    @Override
    public int getHeight() {
        return SLOT_SIZE;
    }

    @Override
    public int getWidth(Font font) {
        return gear.length * SLOT_SIZE + (gear.length - 1) * SLOT_GAP;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        // 1.21.1's renderImage has no tooltip-width argument (26.1 passed one), so the slot row
        // starts at the tooltip's left edge the way vanilla's own ClientBundleTooltip does.
        int startX = x;
        for (int i = 0; i < gear.length; i++) {
            int slotX = startX + i * (SLOT_SIZE + SLOT_GAP);
            graphics.blitSprite(SLOT_BACKGROUND_SPRITE, slotX, y, SLOT_SIZE, SLOT_SIZE);
            ItemStack stack = gear[i];
            int iconX = slotX + 4;
            int iconY = y + 4;
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, iconX, iconY, 0);
                graphics.renderItemDecorations(font, stack, iconX, iconY);
            } else {
                ResourceLocation ghost = GHOST_TEXTURES[i];
                if (ghost != null) {
                    graphics.blit(ghost, iconX, iconY,
                            ICON_SIZE, ICON_SIZE, 0, 0, 32, 32, 32, 32);
                }
            }
        }
    }
}
