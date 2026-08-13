package grill24.fishtastic.client.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Client renderer for {@link FishTankMaterialsTooltip}: a single row of three slot icons showing
 * the tank's frame / glass / sand blocks. Mirrors {@link ClientRodGearTooltip}, minus the ghost
 * icons — a fish tank always has all three materials.
 */
public class ClientFishTankMaterialsTooltip implements ClientTooltipComponent {
    private static final Identifier SLOT_BACKGROUND_SPRITE =
            Identifier.withDefaultNamespace("container/bundle/slot_background");
    private static final int SLOT_SIZE = 24;
    private static final int SLOT_GAP = 2;

    private final ItemStack[] materials;

    public ClientFishTankMaterialsTooltip(ItemStack frame, ItemStack glass, ItemStack sand) {
        this.materials = new ItemStack[] { frame, glass, sand };
    }

    @Override
    public int getHeight(Font font) {
        return SLOT_SIZE;
    }

    @Override
    public int getWidth(Font font) {
        return materials.length * SLOT_SIZE + (materials.length - 1) * SLOT_GAP;
    }

    @Override
    public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
        int startX = x + (w - getWidth(font)) / 2;
        for (int i = 0; i < materials.length; i++) {
            int slotX = startX + i * (SLOT_SIZE + SLOT_GAP);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_BACKGROUND_SPRITE, slotX, y, SLOT_SIZE, SLOT_SIZE);
            ItemStack stack = materials[i];
            int iconX = slotX + 4;
            int iconY = y + 4;
            if (!stack.isEmpty()) {
                graphics.item(stack, iconX, iconY, 0);
                graphics.itemDecorations(font, stack, iconX, iconY);
            }
        }
    }
}
