package grill24.fishtastic.client.tooltip;

import grill24.fishtastic.Fishtastic;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class ClientRodGearTooltip implements ClientTooltipComponent {
    private static final Identifier SLOT_BACKGROUND_SPRITE =
            Identifier.withDefaultNamespace("container/bundle/slot_background");
    private static final int SLOT_SIZE = 24;
    private static final int SLOT_GAP = 2;
    private static final int ICON_SIZE = 16;

    // Drawn in place of the item icon when a slot is empty, in bait/hook/charm order.
    private static final Identifier[] GHOST_TEXTURES = new Identifier[] {
            Fishtastic.id("textures/item/fish/worms_ghost.png"),
            Fishtastic.id("textures/item/fish/hook_ghost.png"),
            Fishtastic.id("textures/item/fish/charm_ghost.png")
    };

    private final ItemStack[] gear;

    public ClientRodGearTooltip(ItemStack bait, ItemStack hook, ItemStack charm) {
        this.gear = new ItemStack[] { bait, hook, charm };
    }

    @Override
    public int getHeight(Font font) {
        return SLOT_SIZE;
    }

    @Override
    public int getWidth(Font font) {
        return gear.length * SLOT_SIZE + (gear.length - 1) * SLOT_GAP;
    }

    @Override
    public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
        int startX = x + (w - getWidth(font)) / 2;
        for (int i = 0; i < gear.length; i++) {
            int slotX = startX + i * (SLOT_SIZE + SLOT_GAP);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_BACKGROUND_SPRITE, slotX, y, SLOT_SIZE, SLOT_SIZE);
            ItemStack stack = gear[i];
            int iconX = slotX + 4;
            int iconY = y + 4;
            if (!stack.isEmpty()) {
                graphics.item(stack, iconX, iconY, 0);
                graphics.itemDecorations(font, stack, iconX, iconY);
            } else {
                Identifier ghost = GHOST_TEXTURES[i];
                if (ghost != null) {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, ghost, iconX, iconY, 0, 0,
                            ICON_SIZE, ICON_SIZE, 32, 32, 32, 32);
                }
            }
        }
    }
}
