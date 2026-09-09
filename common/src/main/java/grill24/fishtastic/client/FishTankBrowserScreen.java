package grill24.fishtastic.client;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.PlacedCosmetic;
import grill24.fishtastic.fishtank.TankEntryKind;
import grill24.fishtastic.fishtank.TankGroups;
import grill24.fishtastic.item.FishTankCosmeticItem;
import grill24.fishtastic.item.FishTankStructureCosmeticItem;
import grill24.fishtastic.menu.FishTankBrowserMenu;
import grill24.fishtastic.network.RemoveTankEntryPacket;
import grill24.fishtastic.util.ItemSizeHelper;
import io.github.currenj.gelatinui.GelatinUIScreen;
import io.github.currenj.gelatinui.gui.UI;
import io.github.currenj.gelatinui.gui.components.ItemButton;
import io.github.currenj.gelatinui.gui.components.Label;
import io.github.currenj.gelatinui.gui.components.ManualContainer;
import io.github.currenj.gelatinui.gui.components.SpriteData;
import io.github.currenj.gelatinui.gui.components.SpriteRectangle;
import io.github.currenj.gelatinui.gui.components.SpriteRenderMode;
import io.github.currenj.gelatinui.gui.components.VBox;
import io.github.currenj.gelatinui.gui.minecraft.MinecraftRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lists every fish and cosmetic across the connected tank group the player opened this from, and
 * lets them remove any entry directly — replaces the old raycast-only, edit-mode-gated removal
 * (see docs/fish-tank-interaction-redesign.md). Reads the group's contents straight off the
 * already chunk-synced client-side {@link FishTankBlockEntity}s, exactly the way the swarm
 * renderer's {@link TankGroups} does.
 *
 * <p>Both sections render as one continuous, row-major icon grid (matching the density of
 * {@code ElectricFishOrganizerScreen}'s slot grid) rather than a one-entry-per-line list, since a
 * connected multi-tank group can easily hold well past the point where a line-per-fish list stops
 * being scannable. Entries are grouped by item (species for fish, cosmetic type for cosmetics) and
 * sorted by name; groups flow left-to-right across the same row and only wrap when a row actually
 * fills, with a single skipped cell marking the boundary between two groups sharing a row — a
 * group only forces its own new row when it happens to end exactly at the row boundary. No
 * per-group label eats into that density; the full name (and count, when more than one) shows as
 * a hover tooltip on each icon instead.
 */
public class FishTankBrowserScreen extends GelatinUIScreen<FishTankBrowserMenu> {

    private static final int CELL = 18;
    // Wider than the Electric Fish Organizer's 9 (which is chest-width-locked by its slot
    // texture) — this grid has no background texture tying it to a fixed width, and a big
    // multi-tank can easily hold 50+ fish, so more columns means fewer, denser rows.
    private static final int COLUMNS = 14;
    /** Extra vertical space between rows, on top of CELL's own built-in icon margin. */
    private static final int ROW_GAP = 1;
    private static final int ROW_STRIDE = CELL + ROW_GAP;

    // Reuses the same panel art as the shop/leaderboard/quest-log cards (see
    // LeaderboardScreen#ROW_BG_TEXTURE) rather than a bespoke outline asset, for visual
    // consistency with the rest of the mod's gelatin-ui screens.
    private static final Identifier OUTLINE_TEXTURE = Fishtastic.id("textures/gui/generic_item_panel.png");
    private static final int OUTLINE_SOURCE_WIDTH = 20;
    private static final int OUTLINE_SOURCE_HEIGHT = 24;
    private static final int OUTLINE_SLICE = 4;

    private record EntryRef(TankEntryKind kind, BlockPos segmentPos, int key) {}

    private record Placement(ItemStack stack, TankEntryKind kind, BlockPos segmentPos, int key) {}

    private record IconGroup(String displayName, List<Placement> placements) {}

    /** Removed here before the server confirms, so a click doesn't wait on the next block sync. */
    private final Set<EntryRef> pendingRemovals = new HashSet<>();

    private MinecraftRenderContext tempContext;
    private VBox content;
    private Label fishHeader;
    private Label cosmeticsHeader;
    private VBox fishSection;
    private VBox cosmeticsSection;

    public FishTankBrowserScreen(FishTankBrowserMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void buildUI() {
        tempContext = new MinecraftRenderContext(null, this.font);
        menu.setOnAnchorSynced(this::refresh);

        content = UI.vbox().spacing(10).padding(16).alignment(VBox.Alignment.CENTER);
        content.addChild(label(translated("screen.fishtastic.fish_tank_browser.title"), 0xFFFFFFFF));

        fishHeader = label(translated("screen.fishtastic.fish_tank_browser.fish_count", 0), 0xFF88CCFF);
        content.addChild(fishHeader);
        fishSection = UI.vbox().alignment(VBox.Alignment.CENTER);
        content.addChild(fishSection);

        cosmeticsHeader = label(translated("screen.fishtastic.fish_tank_browser.cosmetics_count", 0), 0xFF88CCFF);
        content.addChild(cosmeticsHeader);
        cosmeticsSection = UI.vbox().alignment(VBox.Alignment.CENTER);
        content.addChild(cosmeticsSection);

        uiScreen.setRoot(content);
        uiScreen.setAutoCenterRoot(false);
        uiScreen.setScrollEnabled(true);

        refresh();
    }

    @Override
    public void removed() {
        super.removed();
        menu.setOnAnchorSynced(null);
    }

    /** Rebuilds both grids from the group's current (already synced) contents. */
    private void refresh() {
        if (fishSection == null || cosmeticsSection == null) return;

        List<Placement> fishPlacements = new ArrayList<>();
        List<Placement> cosmeticPlacements = new ArrayList<>();

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(menu.getAnchorPos()) instanceof FishTankBlockEntity anchorTank) {
            TankGroups.Group group = TankGroups.of(anchorTank, mc.level, TankGroups.GAMEPLAY_MAX_GROUP_SIZE);
            for (BlockPos pos : group.members()) {
                if (!(mc.level.getBlockEntity(pos) instanceof FishTankBlockEntity member)) continue;

                for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE; slot++) {
                    ItemStack stack = member.getItem(slot);
                    if (stack.isEmpty()) continue;
                    addIfNotPending(fishPlacements, stack, TankEntryKind.FISH, pos, slot);
                }
                for (Map.Entry<CosmeticGridCell, PlacedCosmetic> entry : member.getCosmetics().entrySet()) {
                    addIfNotPending(cosmeticPlacements, cosmeticIcon(entry.getValue()), TankEntryKind.COSMETIC, pos, entry.getKey().packed());
                }
                for (Map.Entry<CosmeticGridCell, FishTankBlockEntity.PlacedStructureCosmetic> entry : member.getStructureCosmetics().entrySet()) {
                    addIfNotPending(cosmeticPlacements, structureIcon(entry.getValue()), TankEntryKind.STRUCTURE_COSMETIC, pos, entry.getKey().packed());
                }
            }
        }

        setHeaderText(fishHeader, "screen.fishtastic.fish_tank_browser.fish_count", fishPlacements.size());
        setHeaderText(cosmeticsHeader, "screen.fishtastic.fish_tank_browser.cosmetics_count", cosmeticPlacements.size());

        renderGroups(fishSection, groupAndSort(fishPlacements, true));
        renderGroups(cosmeticsSection, groupAndSort(cosmeticPlacements, false));

        recenter();
    }

    private void addIfNotPending(List<Placement> out, ItemStack icon, TankEntryKind kind, BlockPos segmentPos, int key) {
        if (icon.isEmpty()) return;
        if (pendingRemovals.contains(new EntryRef(kind, segmentPos, key))) return;
        out.add(new Placement(icon, kind, segmentPos, key));
    }

    /** Groups by item identity (species for fish, cosmetic type for cosmetics), sorted by name. */
    private static List<IconGroup> groupAndSort(List<Placement> placements, boolean sortBySizeDesc) {
        Map<Item, List<Placement>> byItem = new LinkedHashMap<>();
        for (Placement p : placements) {
            byItem.computeIfAbsent(p.stack().getItem(), k -> new ArrayList<>()).add(p);
        }
        List<IconGroup> groups = new ArrayList<>();
        for (List<Placement> group : byItem.values()) {
            if (sortBySizeDesc) {
                group.sort(Comparator.comparingDouble(FishTankBrowserScreen::sizeOf).reversed());
            }
            groups.add(new IconGroup(group.get(0).stack().getHoverName().getString(), group));
        }
        groups.sort(Comparator.comparing(IconGroup::displayName));
        return groups;
    }

    private static float sizeOf(Placement p) {
        return ItemSizeHelper.hasSize(p.stack()) ? ItemSizeHelper.getSize(p.stack()) : 0f;
    }

    /** One icon's assigned cell before row-centering is applied, plus which group it belongs to. */
    private record PlacedIcon(Placement placement, int groupIndex, int row, int col) {}

    /** One contiguous run of a single group's icons within one row — what gets an outline. */
    private record RowSegment(int row, int minCol, int maxCol) {}

    /**
     * Rebuilds one section's grid. First pass assigns every icon a (row, col) cell via the same
     * continuous row-major flow as before (a group only forces a line break when the current row
     * is actually full; a single skipped cell divides two groups sharing a row). Second pass
     * centers each row horizontally by its own used span — otherwise a row that doesn't fill all
     * {@link #COLUMNS} columns (nearly always true for the last row, and any row with only a
     * couple of small groups) reads as hugging the left edge of the fixed-width grid instead of
     * sitting centered like the rest of the panel. A thin outline is then drawn behind each
     * group's icons, one rectangle per row it occupies (almost always just one — a group only
     * spans more than one row once its own count alone exceeds {@link #COLUMNS}).
     */
    private void renderGroups(VBox section, List<IconGroup> groups) {
        section.clearChildren();
        if (groups.isEmpty()) {
            section.addChild(label(translated("screen.fishtastic.fish_tank_browser.empty"), 0xFF888888));
            return;
        }

        List<PlacedIcon> placedIcons = new ArrayList<>();
        int col = 0;
        int row = 0;
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            IconGroup group = groups.get(groupIndex);
            if (col != 0) {
                col++;
                if (col >= COLUMNS) {
                    col = 0;
                    row++;
                }
            }
            for (Placement placement : group.placements()) {
                placedIcons.add(new PlacedIcon(placement, groupIndex, row, col));
                col++;
                if (col >= COLUMNS) {
                    col = 0;
                    row++;
                }
            }
        }
        int totalRows = row + (col > 0 ? 1 : 0);

        int[] colsUsedPerRow = new int[totalRows];
        for (PlacedIcon icon : placedIcons) {
            colsUsedPerRow[icon.row()] = Math.max(colsUsedPerRow[icon.row()], icon.col() + 1);
        }
        float totalWidth = COLUMNS * CELL;
        float[] rowOffsets = new float[totalRows];
        for (int r = 0; r < totalRows; r++) {
            rowOffsets[r] = (totalWidth - colsUsedPerRow[r] * (float) CELL) / 2f;
        }

        // Group each (groupIndex, row) pair into its column span, so a group that happens to
        // wrap across rows gets one outline per row-segment rather than one box spanning gaps.
        Map<List<Integer>, RowSegment> segments = new LinkedHashMap<>();
        for (PlacedIcon icon : placedIcons) {
            List<Integer> key = List.of(icon.groupIndex(), icon.row());
            segments.merge(key, new RowSegment(icon.row(), icon.col(), icon.col()),
                    (a, b) -> new RowSegment(a.row(), Math.min(a.minCol(), b.minCol()), Math.max(a.maxCol(), b.maxCol())));
        }

        ManualContainer grid = UI.manualContainer();
        for (RowSegment segment : segments.values()) {
            addOutline(grid, rowOffsets[segment.row()], segment);
        }
        for (PlacedIcon icon : placedIcons) {
            float x = rowOffsets[icon.row()] + CELL / 2f + icon.col() * CELL;
            float y = CELL / 2f + icon.row() * ROW_STRIDE;
            addIcon(grid, icon.placement(), x, y);
        }

        float totalHeight = totalRows > 0 ? totalRows * (float) CELL + (totalRows - 1) * (float) ROW_GAP : 0f;
        grid.setSize(totalWidth, totalHeight);
        section.addChild(grid);
    }

    /** Thin sliced-sprite outline behind a group's icons in one row, added before the icons. */
    private void addOutline(ManualContainer grid, float rowOffset, RowSegment segment) {
        float left = rowOffset + segment.minCol() * CELL;
        float top = segment.row() * (float) ROW_STRIDE;
        float width = (segment.maxCol() - segment.minCol() + 1) * (float) CELL;
        float height = CELL;

        SpriteData sprite = new SpriteData(OUTLINE_TEXTURE)
                .uv(0, 0, OUTLINE_SOURCE_WIDTH, OUTLINE_SOURCE_HEIGHT)
                .textureSize(OUTLINE_SOURCE_WIDTH, OUTLINE_SOURCE_HEIGHT)
                .renderMode(SpriteRenderMode.SLICE)
                .slice(OUTLINE_SLICE, OUTLINE_SLICE, OUTLINE_SLICE, OUTLINE_SLICE);

        ManualContainer outline = UI.manualContainer().setSize(width, height).backgroundSprite(sprite);
        grid.addChildAt(outline, left + width / 2f, top + height / 2f);
    }

    private void addIcon(ManualContainer grid, Placement placement, float x, float y) {
        ItemButton button = UI.itemButton(placement.stack());
        button.onClick(e -> {
            pendingRemovals.add(new EntryRef(placement.kind(), placement.segmentPos(), placement.key()));
            sendRemove(placement.segmentPos(), placement.kind(), placement.key());
            refresh();
        });

        StringBuilder tooltipText = new StringBuilder(placement.stack().getHoverName().getString());
        if (ItemSizeHelper.hasSize(placement.stack())) {
            String sizeText = translated("tooltip.fishtastic.item_size.cm",
                    String.format("%.0f", ItemSizeHelper.getSize(placement.stack())));
            tooltipText.append(" (").append(sizeText).append(')');
        }
        // Same tooltip-panel style QuestLogScreen uses for its status-pip/cycling-icon tooltips —
        // a small backed, outlined panel rather than bare floating text.
        SpriteRectangle.SpriteRectangleImpl tooltip = UI.spriteRectangle(0, 0, 0xFF002244)
                .text(tooltipText.toString(), 0xFFFFFFFF)
                .autoSize(true)
                .padding(3, 2)
                .outline(true);
        button.tooltip(uiScreen, tooltip);

        grid.addChildAt(button, x, y);
    }

    private static ItemStack cosmeticIcon(PlacedCosmetic cosmetic) {
        Item item = FishTankCosmeticItem.forBlock(cosmetic.block());
        if (item == null) item = cosmetic.block().asItem();
        return new ItemStack(item);
    }

    private static ItemStack structureIcon(FishTankBlockEntity.PlacedStructureCosmetic placed) {
        FishTankStructureCosmeticItem item = FishTankStructureCosmeticItem.forStructure(placed.structureId());
        return item != null ? new ItemStack(item) : ItemStack.EMPTY;
    }

    private void sendRemove(BlockPos segmentPos, TankEntryKind kind, int key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.connection.send(new ServerboundCustomPayloadPacket(new RemoveTankEntryPacket(segmentPos, kind, key)));
    }

    private void setHeaderText(Label header, String key, int count) {
        header.text(translated(key, count));
        header.updateSize(tempContext);
    }

    private void recenter() {
        if (content == null) return;
        content.forceLayout();
        Vector2f size = content.getSize();
        content.setPosition(new Vector2f((this.width - size.x) / 2f, (this.height - size.y) / 2f));
        if (uiScreen != null) {
            uiScreen.setRoot(content);
        }
    }

    private static String translated(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private Label label(String text, int color) {
        return new Label(text, color).init(tempContext);
    }
}
