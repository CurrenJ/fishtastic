package grill24.fishtastic.client;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.fishtank.FishTankShape;
import grill24.fishtastic.menu.FishTankAssemblyMenu;
import grill24.fishtastic.network.SetAssemblyShapePacket;
import io.github.currenj.gelatinui.GelatinUIScreen;
import io.github.currenj.gelatinui.gui.UI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.util.function.Consumer;

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

    // Shape-cycle button: top-right of the panel, clear of the "Fish Tank Assembly" title
    // (drawn at x=8 by extractLabels) and of the input/result slots below.
    private static final int SHAPE_BTN_X = 104;
    private static final int SHAPE_BTN_Y = 6;
    private static final int SHAPE_BTN_WIDTH = 64;
    private static final int SHAPE_BTN_HEIGHT = 16;

    private Button shapeButton;
    private FishTankShape lastKnownShape = FishTankShape.STANDARD;

    public FishTankAssemblyScreen(FishTankAssemblyMenu menu, Inventory inventory, Component title) {
        // GelatinUIScreen only exposes the 3-arg ctor, which fixes imageWidth/imageHeight at
        // vanilla's default 176x166 — close enough to the panel's actual ~175x165 drawn size.
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        // The vanilla Button is a plain renderable widget, not a gelatin-ui element — this screen
        // is vanilla-driven (gelatin only supplies the empty UI root), so the button is added the
        // vanilla way and draws/click-handles on top of the panel.
        lastKnownShape = menu.getShape();
        shapeButton = new ShapeCycleButton(leftPos + SHAPE_BTN_X, topPos + SHAPE_BTN_Y, SHAPE_BTN_WIDTH, SHAPE_BTN_HEIGHT,
                shapeLabel(lastKnownShape), this::cycleShape);
        shapeButton.setTooltip(Tooltip.create(Component.translatable("gui.fishtastic.fish_tank_assembly.shape_cycle_tooltip")));
        addRenderableWidget(shapeButton);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        // The shape data slot syncs server→client on open and after each change; refresh the
        // button label to the authoritative value once it arrives.
        FishTankShape current = menu.getShape();
        if (current != lastKnownShape) {
            lastKnownShape = current;
            shapeButton.setMessage(shapeLabel(current));
        }
    }

    /** Cycle the shape; {@code forward} (left-click) goes to {@link FishTankShape#next()}, else backwards. */
    private void cycleShape(boolean forward) {
        FishTankShape next = forward ? menu.getShape().next() : menu.getShape().previous();
        // Optimistic client-side preview; the server confirms and syncs the same value back.
        menu.setShapeLocal(next);
        shapeButton.setMessage(shapeLabel(next));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.send(new ServerboundCustomPayloadPacket(new SetAssemblyShapePacket(next)));
        }
    }

    /**
     * A shape-cycle button that also reacts to right-click: left cycles forward, right cycles
     * backward. Vanilla's {@link Button} only routes left-clicks through {@code onPress}, so this
     * widens the accepted buttons and dispatches on the actual mouse button.
     */
    private static final class ShapeCycleButton extends Button.Plain {
        private final Consumer<Boolean> onCycle;

        ShapeCycleButton(int x, int y, int width, int height, Component message, Consumer<Boolean> onCycle) {
            super(x, y, width, height, message, button -> {}, Button.DEFAULT_NARRATION);
            this.onCycle = onCycle;
        }

        @Override
        protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
            return buttonInfo.button() == 0 || buttonInfo.button() == 1; // left or right
        }

        @Override
        public void onPress(InputWithModifiers input) {
            // Mouse left-click (button 0) and keyboard activation (Enter/Space) cycle forward;
            // mouse right-click (button 1) cycles backward.
            this.onCycle.accept(input.input() != 1);
        }
    }

    private static Component shapeLabel(FishTankShape shape) {
        return shape.getDisplayName();
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
