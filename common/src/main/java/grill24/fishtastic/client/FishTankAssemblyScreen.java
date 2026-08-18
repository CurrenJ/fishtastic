package grill24.fishtastic.client;

import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.component.FishTankMaterials;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.fishtank.FishTankShape;
import grill24.fishtastic.menu.FishTankAssemblyMenu;
import grill24.fishtastic.network.SetAssemblyShapePacket;
import io.github.currenj.gelatinui.GelatinUIScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * GUI for the Fish Tank Assembly block: 3 real material slots (frame/sand/glass) and
 * a result slot, matching the generated {@code fish_tank_assembly.png} background.
 * Vanilla {@link net.minecraft.world.inventory.Slot}s on {@link FishTankAssemblyMenu}
 * handle item interaction; gelatin-ui supplies the background/decoration layer and the
 * {@link ShapeGalleryPanel} beside the panel.
 *
 * <p>Shape selection is a gallery rather than a cycle button: with the catalog past a dozen shapes,
 * stepping one-at-a-time cost a click per shape and showed only a name, which says nothing about a
 * purely visual difference. The gallery follows vanilla's recipe-book pattern — a toggle button
 * reveals a side panel and {@link #leftPos} shifts so the combined UI stays centered (compare
 * {@code RecipeBookComponent#updateScreenPosition}).
 */
public class FishTankAssemblyScreen extends GelatinUIScreen<FishTankAssemblyMenu> {
    private static final Identifier TEXTURE = Fishtastic.id("textures/gui/fish_tank_assembly.png");

    // The texture file is a 256x256 atlas (vanilla convention, e.g. crafting_table.png) —
    // the actual panel only occupies the top-left corner.
    private static final int ATLAS_SIZE = 256;

    // Gallery toggle: top-right of the panel, clear of the "Fish Tank Assembly" title (drawn at
    // x=8 by extractLabels) and of the input/result slots below.
    private static final int SHAPE_BTN_X = 120;
    private static final int SHAPE_BTN_Y = 6;
    private static final int SHAPE_BTN_WIDTH = 48;
    private static final int SHAPE_BTN_HEIGHT = 16;

    /** Horizontal gap between the assembly panel's right edge and the gallery. */
    private static final int GALLERY_GAP = 4;

    private FishTankShape lastKnownShape = FishTankShape.STANDARD;
    /** Mirrors the input slots; the gallery re-renders its previews whenever this changes. */
    private FishTankMaterials lastKnownMaterials = FishTankMaterials.defaultMaterials();

    private boolean galleryOpen = FishtasticClientConfig.isShapeGalleryOpen();
    /** Null while the gallery is hidden — it isn't built at all, so it can't take stray clicks. */
    private ShapeGalleryPanel gallery;

    public FishTankAssemblyScreen(FishTankAssemblyMenu menu, Inventory inventory, Component title) {
        // GelatinUIScreen only exposes the 3-arg ctor, which fixes imageWidth/imageHeight at
        // vanilla's default 176x166 — close enough to the panel's actual ~175x165 drawn size.
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        // GelatinUIScreen#init runs AbstractContainerScreen#init (which centers leftPos on the
        // panel alone) and then buildUI. Re-center for the gallery afterwards, then place it.
        super.init();

        leftPos = galleryOpen
                ? (this.width - this.imageWidth - GALLERY_GAP - ShapeGalleryPanel.WIDTH) / 2
                : (this.width - this.imageWidth) / 2;

        lastKnownShape = menu.getShape();
        lastKnownMaterials = materialsFromSlots();
        if (gallery != null) {
            gallery.setTopLeft(galleryLeft(), galleryTop());
            gallery.setSelected(lastKnownShape);
            gallery.setMaterials(lastKnownMaterials);
        }

        // The vanilla Button is a plain renderable widget, not a gelatin-ui element — this screen
        // is vanilla-driven for the panel chrome, so the toggle is added the vanilla way and
        // draws/click-handles on top of the panel.
        addRenderableWidget(new Button.Builder(
                Component.translatable("gui.fishtastic.fish_tank_assembly.shape_gallery"),
                button -> toggleGallery())
                .bounds(leftPos + SHAPE_BTN_X, topPos + SHAPE_BTN_Y, SHAPE_BTN_WIDTH, SHAPE_BTN_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable(galleryOpen
                        ? "gui.fishtastic.fish_tank_assembly.shape_gallery.hide"
                        : "gui.fishtastic.fish_tank_assembly.shape_gallery.show")))
                .build());
    }

    @Override
    protected void buildUI() {
        // FreeformContainer never asserts a position on its children, so the gallery keeps the
        // absolute placement init() gives it (a VBox/ManualContainer root would override it).
        FreeformContainer root = new FreeformContainer();
        root.setSize(this.width, this.height);

        if (galleryOpen) {
            gallery = new ShapeGalleryPanel(this::selectShape, FishTankAssemblyScreen::isQuestClaimed);
            root.addChild(gallery.root());
        } else {
            gallery = null;
        }

        uiScreen.setRoot(root);
    }

    private void toggleGallery() {
        galleryOpen = !galleryOpen;
        FishtasticClientConfig.setShapeGalleryOpen(galleryOpen);
        // Rebuilds the widgets and the gelatin root against the new leftPos in one pass.
        rebuildWidgets();
    }

    private int galleryLeft() {
        return leftPos + this.imageWidth + GALLERY_GAP;
    }

    private int galleryTop() {
        return topPos;
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (gallery == null) {
            return;
        }

        // The shape data slot syncs server→client on open and after each change; move the
        // selection highlight to the authoritative value once it arrives.
        FishTankShape current = menu.getShape();
        if (current != lastKnownShape) {
            lastKnownShape = current;
            gallery.setSelected(current);
        }

        // Previews follow whatever the player has staged in the input slots, so each cell is a
        // picture of the exact tank they'd craft. Both calls no-op when nothing changed.
        lastKnownMaterials = materialsFromSlots();
        gallery.setMaterials(lastKnownMaterials);
        // A quest can be claimed (quest log, notification) while this screen is open.
        gallery.refreshUnlockStates(FishTankAssemblyScreen::isQuestClaimed);
    }

    /**
     * Commits a shape picked from the gallery. Only ever called for unlocked shapes — the gallery
     * treats locked cells as inert — so there is nothing for the server to reject and nothing that
     * can desync from the server-authoritative committed shape.
     */
    private void selectShape(FishTankShape shape) {
        // Optimistic client-side preview; the server confirms and syncs the same value back.
        menu.setShapeLocal(shape);
        lastKnownShape = shape;
        if (gallery != null) {
            gallery.setSelected(shape);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.send(new ServerboundCustomPayloadPacket(new SetAssemblyShapePacket(shape)));
        }
    }

    /**
     * The material trio staged in the input slots, or {@link FishTankMaterials#defaultMaterials()}
     * while the slots are empty or partially filled — the gallery always shows a complete tank.
     */
    private FishTankMaterials materialsFromSlots() {
        Block frame = blockIn(FishTankAssemblyMenu.FRAME_SLOT);
        Block sand = blockIn(FishTankAssemblyMenu.SAND_SLOT);
        Block glass = blockIn(FishTankAssemblyMenu.GLASS_SLOT);
        return frame == null || sand == null || glass == null
                ? FishTankMaterials.defaultMaterials()
                : new FishTankMaterials(frame, sand, glass);
    }

    private Block blockIn(int slotIndex) {
        ItemStack stack = menu.slots.get(slotIndex).getItem();
        return stack.getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : null;
    }

    /**
     * The gallery sits outside the panel rect that {@code AbstractContainerScreen} treats as
     * "outside the GUI", where a click would throw the carried stack on the floor. Clicks that land
     * on it — including on the backing between cells — are inside the UI.
     */
    @Override
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
        if (galleryOpen
                && mx >= galleryLeft() && mx < galleryLeft() + ShapeGalleryPanel.WIDTH
                && my >= galleryTop() && my < galleryTop() + ShapeGalleryPanel.HEIGHT) {
            return false;
        }
        return super.hasClickedOutside(mx, my, xo, yo);
    }

    private static boolean isQuestClaimed(ResourceKey<Quest> quest) {
        return QuestClientCache.getProgress(quest.identifier()).claimed();
    }

    /**
     * The unlock quests' authored display names, joined since claiming any one of them unlocks the
     * shape — read from the synced quest registry.
     */
    private static Component questDisplayName(FishTankShape shape) {
        var quests = Minecraft.getInstance().level.registryAccess().lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
        MutableComponent joined = null;
        for (ResourceKey<Quest> key : shape.unlockQuests()) {
            Component name = quests.getOptional(key)
                    .<Component>map(quest -> Component.literal(quest.displayName()))
                    .orElse(Component.translatable("gui.fishtastic.fish_tank_assembly.shape_locked_tooltip.unknown_quest"));
            joined = joined == null ? name.copy() : joined.append(" / ").append(name);
        }
        return joined != null ? joined : Component.translatable("gui.fishtastic.fish_tank_assembly.shape_locked_tooltip.unknown_quest");
    }

    /**
     * Names the hovered gallery cell, and for a locked one its unlock condition. The hovered cell
     * is derived from the grid geometry rather than gelatin hover state, which lands a frame later
     * (gelatin dispatches {@code onMouseMove} after this pass).
     */
    @Override
    protected void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractContent(graphics, mouseX, mouseY, partialTick);
        if (gallery == null) {
            return;
        }

        FishTankShape hovered = gallery.shapeAt(mouseX, mouseY);
        if (hovered == null) {
            return;
        }

        List<Component> lines = new ArrayList<>(2);
        lines.add(hovered.getDisplayName());
        if (!hovered.isUnlockedFor(FishTankAssemblyScreen::isQuestClaimed)) {
            lines.add(Component.translatable("gui.fishtastic.fish_tank_assembly.shape_locked_tooltip",
                    questDisplayName(hovered)).withStyle(ChatFormatting.GRAY));
        }
        graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
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
