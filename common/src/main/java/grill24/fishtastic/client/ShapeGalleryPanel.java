package grill24.fishtastic.client;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.component.FishTankMaterials;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.fishtank.FishTankShape;
import io.github.currenj.gelatinui.gui.UI;
import io.github.currenj.gelatinui.gui.components.ManualContainer;
import io.github.currenj.gelatinui.gui.components.SpriteData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The tank shape catalog shown beside the Fish Tank Assembly GUI: one cell per
 * {@link FishTankShape}, each rendering a live preview of the tank the player would actually craft
 * with the materials currently in their input slots.
 *
 * <p>Previews cost nothing extra to produce — the per-platform fish tank item model already renders
 * (and caches) an arbitrary {@code (FishTankMaterials, FishTankShape)} pair, so a cell is just an
 * {@link ItemStack} carrying those two data components.
 *
 * <p>Locked shapes keep their cell rather than being hidden, rendered as black silhouettes through
 * {@link SilhouetteItemButton}. Cell positions therefore stay stable as shapes unlock, and the grid
 * doubles as a visible goal board.
 */
public class ShapeGalleryPanel {
    // The 20x24 cell art shared with the quest log's shop entries; the gold-bordered variant marks
    // the committed selection. Reused as-is so the gallery needs no new texture work.
    private static final Identifier CELL_TEXTURE = Fishtastic.id("textures/gui/generic_item_panel.png");
    private static final Identifier CELL_TEXTURE_SELECTED = Fishtastic.id("textures/gui/generic_item_panel_gold_border.png");

    public static final int CELL_WIDTH = 20;
    public static final int CELL_HEIGHT = 24;
    public static final int COLUMNS = 4;
    private static final int PADDING = 4;

    /**
     * Row count is derived from the catalog size rather than fixed, so adding a shape needs no
     * layout change. At {@link #COLUMNS} = 4 the panel stays shorter than the assembly GUI's 166px
     * body up to 24 shapes; past that, widen to 5 columns rather than introducing scrolling
     * (gelatin-ui's scrollbar drives {@code UIScreen}'s whole-screen scroll, not a clipped
     * sub-viewport, so a scrolling sub-panel would mean new framework plumbing).
     */
    private static final int ROWS = (FishTankShape.values().length + COLUMNS - 1) / COLUMNS;

    public static final int WIDTH = COLUMNS * CELL_WIDTH + PADDING * 2;
    public static final int HEIGHT = ROWS * CELL_HEIGHT + PADDING * 2;

    /** Where the icon sits inside a cell, matching the shop's use of the same art. */
    private static final float ICON_CENTER_X = CELL_WIDTH / 2f;
    private static final float ICON_CENTER_Y = 13f;

    // Matches the translucent backdrop the quest log uses behind its cards.
    private static final int BACKING_COLOR = 0x99222222;

    private final ManualContainer root;
    /** Cell backgrounds and icons, both indexed by {@link FishTankShape#ordinal()}. */
    private final List<ManualContainer> cells = new ArrayList<>();
    private final List<SilhouetteItemButton> icons = new ArrayList<>();

    private FishTankShape selected = FishTankShape.STANDARD;
    private FishTankMaterials materials = FishTankMaterials.defaultMaterials();

    /**
     * @param onShapePicked invoked with the clicked shape; only ever called for shapes that are
     *                      unlocked at click time, so callers needn't re-check.
     * @param questClaimed  quest-claim lookup used to decide which shapes render as silhouettes.
     */
    public ShapeGalleryPanel(Consumer<FishTankShape> onShapePicked, Predicate<ResourceKey<Quest>> questClaimed) {
        root = UI.manualContainer().setSize(WIDTH, HEIGHT).backgroundColor(BACKING_COLOR);
        root.setDebugName("shapeGallery");

        for (FishTankShape shape : FishTankShape.values()) {
            ManualContainer cell = UI.manualContainer().setSize(CELL_WIDTH, CELL_HEIGHT);
            cell.setDebugName("shapeGalleryCell:" + shape.getSerializedName());
            cell.backgroundSprite(cellSprite(shape == selected));

            SilhouetteItemButton icon = new SilhouetteItemButton(previewStack(shape, materials));
            icon.onClick(e -> {
                // A locked cell is inert: the shape is browsable but not selectable, so there is
                // nothing for the server to reject and nothing that can desync.
                if (shape.isUnlockedFor(questClaimed)) {
                    onShapePicked.accept(shape);
                }
            });
            cell.addChildAt(icon, ICON_CENTER_X, ICON_CENTER_Y);
            cell.forceLayout();

            root.addChildAt(cell, cellCenterX(shape.ordinal()), cellCenterY(shape.ordinal()));
            cells.add(cell);
            icons.add(icon);
        }

        refreshUnlockStates(questClaimed);
        root.forceLayout();
    }

    /** The gelatin element to parent into the screen's UI root. */
    public ManualContainer root() {
        return root;
    }

    /**
     * Places the panel's top-left corner at the given screen coordinates. {@code UIElement}
     * positions are top-left (unlike {@code ManualContainer} child positions, which are centers).
     */
    public void setTopLeft(float x, float y) {
        root.setPosition(new Vector2f(x, y));
        root.forceLayout();
    }

    /** Moves the gold border onto {@code shape}'s cell. No-op if it is already there. */
    public void setSelected(FishTankShape shape) {
        if (selected == shape) {
            return;
        }
        cells.get(selected.ordinal()).backgroundSprite(cellSprite(false));
        selected = shape;
        cells.get(selected.ordinal()).backgroundSprite(cellSprite(true));
    }

    /**
     * Re-renders every preview against a new material set. No-op when the materials are unchanged,
     * so the common idle case costs a single record comparison.
     */
    public void setMaterials(FishTankMaterials newMaterials) {
        if (materials.equals(newMaterials)) {
            return;
        }
        materials = newMaterials;
        for (FishTankShape shape : FishTankShape.values()) {
            icons.get(shape.ordinal()).itemStack(previewStack(shape, materials));
        }
    }

    /**
     * Re-evaluates which cells render as silhouettes. Called every tick because a quest can be
     * claimed (via the quest log, or a notification) while this screen is open.
     */
    public void refreshUnlockStates(Predicate<ResourceKey<Quest>> questClaimed) {
        for (FishTankShape shape : FishTankShape.values()) {
            icons.get(shape.ordinal()).setSilhouette(!shape.isUnlockedFor(questClaimed));
        }
    }

    /**
     * The shape whose cell contains the given screen coordinates, or null. Computed straight from
     * the grid geometry rather than from gelatin hover events, so it is correct on the same frame
     * the mouse moves (gelatin dispatches {@code onMouseMove} after the screen's content pass).
     */
    public FishTankShape shapeAt(double mouseX, double mouseY) {
        Vector2f topLeft = root.getPosition();
        float localX = (float) mouseX - topLeft.x - PADDING;
        float localY = (float) mouseY - topLeft.y - PADDING;
        if (localX < 0 || localY < 0) {
            return null;
        }

        int column = (int) (localX / CELL_WIDTH);
        int row = (int) (localY / CELL_HEIGHT);
        if (column >= COLUMNS || row >= ROWS) {
            return null;
        }

        int index = row * COLUMNS + column;
        FishTankShape[] shapes = FishTankShape.values();
        return index < shapes.length ? shapes[index] : null;
    }

    private static float cellCenterX(int index) {
        return PADDING + (index % COLUMNS) * CELL_WIDTH + CELL_WIDTH / 2f;
    }

    private static float cellCenterY(int index) {
        return PADDING + (index / COLUMNS) * CELL_HEIGHT + CELL_HEIGHT / 2f;
    }

    private static SpriteData cellSprite(boolean selected) {
        return new SpriteData(selected ? CELL_TEXTURE_SELECTED : CELL_TEXTURE)
                .uv(0, 0, CELL_WIDTH, CELL_HEIGHT)
                .textureSize(CELL_WIDTH, CELL_HEIGHT);
    }

    /** A fish tank stack carrying the two components the item model renders from. */
    private static ItemStack previewStack(FishTankShape shape, FishTankMaterials materials) {
        ItemStack stack = new ItemStack(FishtasticBlocks.FISH_TANK.value());
        stack.set(FishtasticDataComponents.FISH_TANK_MATERIALS.value(), materials);
        stack.set(FishtasticDataComponents.FISH_TANK_SHAPE.value(), shape);
        return stack;
    }
}
