package grill24.fishtastic.client;

import grill24.fishtastic.menu.ElectricFishOrganizerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Plain double-chest-style GUI for the Electric Fish Organizer — reuses vanilla's 54-slot
 * container texture (same one the large chest and shulker box use) rather than authoring new
 * background art, since the organizer's slots behave like an ordinary chest grid.
 */
public class ElectricFishOrganizerScreen extends AbstractContainerScreen<ElectricFishOrganizerMenu> {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int ROWS = 6;

    public ElectricFishOrganizerScreen(ElectricFishOrganizerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 114 + ROWS * 18);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int xo = (this.width - this.imageWidth) / 2;
        int yo = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, xo, yo, 0.0F, 0.0F, this.imageWidth, ROWS * 18 + 17, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, xo, yo + ROWS * 18 + 17, 0.0F, 126.0F, this.imageWidth, 96, 256, 256);
    }
}
