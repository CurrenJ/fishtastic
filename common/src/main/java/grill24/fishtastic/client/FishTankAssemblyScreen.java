package grill24.fishtastic.client;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.menu.FishTankAssemblyMenu;
import io.github.currenj.gelatinui.GelatinUIScreen;
import io.github.currenj.gelatinui.gui.UI;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * GUI for the Fish Tank Assembly block: 3 real material slots (frame/sand/glass) and
 * a result slot, matching the generated {@code fish_tank_assembly.png} background.
 * Vanilla {@link net.minecraft.world.inventory.Slot}s on {@link FishTankAssemblyMenu}
 * handle item interaction; gelatin-ui only supplies the background/decoration layer.
 */
public class FishTankAssemblyScreen extends GelatinUIScreen<FishTankAssemblyMenu> {
    private static final Identifier TEXTURE = Fishtastic.id("textures/gui/fish_tank_assembly.png");

    // The texture file is a 256x256 atlas (vanilla convention, e.g. crafting_table.png) —
    // the actual panel only occupies the top-left corner.
    private static final int ATLAS_SIZE = 256;

    public FishTankAssemblyScreen(FishTankAssemblyMenu menu, Inventory inventory, Component title) {
        // GelatinUIScreen only exposes the 3-arg ctor, which fixes imageWidth/imageHeight at
        // vanilla's default 176x166 — close enough to the panel's actual ~175x165 drawn size.
        super(menu, inventory, title);
    }

    @Override
    protected void buildUI() {
        uiScreen.setRoot(UI.vbox());
    }

    /**
     * GelatinUIScreen overrides {@code extractLabels} to a no-op — each screen is expected to
     * draw its own title. Restore vanilla's exact behavior (same position/color as every other
     * container screen) since this menu doesn't need custom label styling.
     */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, -12566464, false);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, -12566464, false);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, ATLAS_SIZE, ATLAS_SIZE);
    }
}
