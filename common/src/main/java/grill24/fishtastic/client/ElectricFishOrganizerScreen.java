package grill24.fishtastic.client;

import grill24.fishtastic.blockentity.OrganizerSortMode;
import grill24.fishtastic.menu.ElectricFishOrganizerMenu;
import grill24.fishtastic.network.SetOrganizerSortPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Plain double-chest-style GUI for the Electric Fish Organizer — reuses vanilla's 54-slot
 * container texture (same one the large chest and shulker box use) rather than authoring new
 * background art, since the organizer's slots behave like an ordinary chest grid.
 *
 * <p>Two small, textless vanilla {@link Button}s sit in the header row to the right of the title:
 * a single-letter glyph that cycles the pile grouping ({@link OrganizerSortMode}), and a
 * single-arrow glyph that flips ascending/descending — the current state is spelled out in each
 * button's tooltip rather than its (deliberately tiny) label. Both apply an optimistic local
 * update via {@link ElectricFishOrganizerMenu#setSortStateLocal} and send
 * {@link SetOrganizerSortPacket} to the server, which re-sorts the block entity and syncs the
 * same state back.
 */
public class ElectricFishOrganizerScreen extends AbstractContainerScreen<ElectricFishOrganizerMenu> {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int ROWS = 6;

    private static final int SORT_BUTTON_SIZE = 12;
    private static final int SORT_BUTTON_GAP = 2;
    /** Right-aligned in the header row, clear of the slot grid that starts at y=18. */
    private static final int SORT_BUTTON_Y = 5;
    private static final int SORT_BUTTON_RIGHT_MARGIN = 6;

    private Button sortModeButton;
    private Button sortOrderButton;
    private OrganizerSortMode lastKnownSortMode = OrganizerSortMode.SPECIES;
    private boolean lastKnownSortAscending = true;

    public ElectricFishOrganizerScreen(ElectricFishOrganizerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 114 + ROWS * 18);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();

        lastKnownSortMode = menu.getSortMode();
        lastKnownSortAscending = menu.isSortAscending();

        int orderX = leftPos + imageWidth - SORT_BUTTON_RIGHT_MARGIN - SORT_BUTTON_SIZE;
        int modeX = orderX - SORT_BUTTON_GAP - SORT_BUTTON_SIZE;
        int y = topPos + SORT_BUTTON_Y;

        sortModeButton = addRenderableWidget(new Button.Builder(modeGlyph(lastKnownSortMode), button -> cycleSortMode())
                .bounds(modeX, y, SORT_BUTTON_SIZE, SORT_BUTTON_SIZE)
                .tooltip(Tooltip.create(sortModeTooltip(lastKnownSortMode)))
                .build());
        sortOrderButton = addRenderableWidget(new Button.Builder(orderGlyph(lastKnownSortAscending), button -> toggleSortOrder())
                .bounds(orderX, y, SORT_BUTTON_SIZE, SORT_BUTTON_SIZE)
                .tooltip(Tooltip.create(sortOrderTooltip(lastKnownSortAscending)))
                .build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // The sort state can change from a source other than these buttons (e.g. loading into a
        // world where it was already set) — pick up the menu's synced value rather than only ever
        // trusting our own optimistic writes.
        OrganizerSortMode mode = menu.getSortMode();
        boolean ascending = menu.isSortAscending();
        if (mode != lastKnownSortMode) {
            lastKnownSortMode = mode;
            refreshModeButton(mode);
        }
        if (ascending != lastKnownSortAscending) {
            lastKnownSortAscending = ascending;
            refreshOrderButton(ascending);
        }
    }

    private void cycleSortMode() {
        applySortState(lastKnownSortMode.next(), lastKnownSortAscending);
    }

    private void toggleSortOrder() {
        applySortState(lastKnownSortMode, !lastKnownSortAscending);
    }

    private void applySortState(OrganizerSortMode mode, boolean ascending) {
        // Optimistic client-side update so the buttons and pile order react instantly; the server
        // remains authoritative and syncs the confirmed state back via the menu's data slots.
        menu.setSortStateLocal(mode, ascending);
        lastKnownSortMode = mode;
        lastKnownSortAscending = ascending;
        refreshModeButton(mode);
        refreshOrderButton(ascending);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.send(new ServerboundCustomPayloadPacket(new SetOrganizerSortPacket(mode, ascending)));
        }
    }

    private void refreshModeButton(OrganizerSortMode mode) {
        sortModeButton.setMessage(modeGlyph(mode));
        sortModeButton.setTooltip(Tooltip.create(sortModeTooltip(mode)));
    }

    private void refreshOrderButton(boolean ascending) {
        sortOrderButton.setMessage(orderGlyph(ascending));
        sortOrderButton.setTooltip(Tooltip.create(sortOrderTooltip(ascending)));
    }

    /** Single-letter glyph standing in for a full label — the mode name lives in the tooltip instead. */
    private static Component modeGlyph(OrganizerSortMode mode) {
        return Component.literal(switch (mode) {
            case SPECIES -> "S";
            case QUALITY -> "Q";
            case SIZE -> "L";
            case ZONE -> "Z";
        });
    }

    private static Component orderGlyph(boolean ascending) {
        return Component.literal(ascending ? "^" : "v");
    }

    private static Component sortModeTooltip(OrganizerSortMode mode) {
        return Component.translatable("gui.fishtastic.electric_fish_organizer.sort_mode.tooltip", mode.getDisplayName());
    }

    private static Component sortOrderTooltip(boolean ascending) {
        return Component.translatable("gui.fishtastic.electric_fish_organizer.sort_order.tooltip",
                Component.translatable(ascending
                        ? "gui.fishtastic.electric_fish_organizer.sort_order.ascending"
                        : "gui.fishtastic.electric_fish_organizer.sort_order.descending"));
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
