package grill24.fishtastic.client.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class ClientRodBaitTooltip implements ClientTooltipComponent {
    private static final Identifier SLOT_BACKGROUND_SPRITE =
            Identifier.withDefaultNamespace("container/bundle/slot_background");
    private static final int SLOT_SIZE = 24;

    private final ItemStack bait;

    public ClientRodBaitTooltip(ItemStack bait) {
        this.bait = bait;
    }

    @Override
    public int getHeight(Font font) {
        return SLOT_SIZE;
    }

    @Override
    public int getWidth(Font font) {
        return SLOT_SIZE;
    }

    @Override
    public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
        int slotX = x + (w - SLOT_SIZE) / 2;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_BACKGROUND_SPRITE, slotX, y, SLOT_SIZE, SLOT_SIZE);
        graphics.item(bait, slotX + 4, y + 4, 0);
        graphics.itemDecorations(font, bait, slotX + 4, y + 4);
    }
}
