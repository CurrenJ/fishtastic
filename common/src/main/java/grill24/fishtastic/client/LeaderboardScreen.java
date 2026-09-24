package grill24.fishtastic.client;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.blockentity.FishPileBlockEntity;
import grill24.fishtastic.client.util.FishPileIcons;
import grill24.fishtastic.client.util.PlayerHeadItems;
import grill24.fishtastic.network.LeaderboardEntry;
import grill24.fishtastic.network.LeaderboardResponsePacket;
import grill24.fishtastic.network.LeaderboardType;
import grill24.fishtastic.network.RequestLeaderboardPacket;
import grill24.fishtastic.util.FishQualityHelper;
import grill24.fishtastic.util.ItemSizeHelper;
import io.github.currenj.gelatinui.GelatinUIScreen;
import io.github.currenj.gelatinui.gui.UI;
import io.github.currenj.gelatinui.gui.components.HBox;
import io.github.currenj.gelatinui.gui.components.ItemTabs;
import io.github.currenj.gelatinui.gui.components.Label;
import io.github.currenj.gelatinui.gui.components.ManualContainer;
import io.github.currenj.gelatinui.gui.components.PlayerAvatarRenderer;
import io.github.currenj.gelatinui.gui.components.PlayerModelRenderer;
import io.github.currenj.gelatinui.gui.components.PlayerPoses;
import io.github.currenj.gelatinui.gui.components.SpriteData;
import io.github.currenj.gelatinui.gui.components.SpriteRenderMode;
import io.github.currenj.gelatinui.gui.components.VBox;
import io.github.currenj.gelatinui.gui.GelatinMenu;
import io.github.currenj.gelatinui.gui.minecraft.MinecraftRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class LeaderboardScreen extends GelatinUIScreen<GelatinMenu> {

    // Order of tabs left-to-right; index into this array is the ItemTabs selection index.
    private static final LeaderboardType[] TAB_TYPES = {
            LeaderboardType.PERSONAL_CATCH_COUNT,
            LeaderboardType.PERSONAL_BEST_SIZE,
            LeaderboardType.GLOBAL_CATCH_COUNT,
            LeaderboardType.GLOBAL_BEST_SIZE
    };

    private static final Map<LeaderboardType, String> TAB_SUBTITLE_KEYS = Map.of(
            LeaderboardType.PERSONAL_CATCH_COUNT, "screen.fishtastic.leaderboard.tab.personal_count",
            LeaderboardType.PERSONAL_BEST_SIZE, "screen.fishtastic.leaderboard.tab.personal_size",
            LeaderboardType.GLOBAL_CATCH_COUNT, "screen.fishtastic.leaderboard.tab.global_count",
            LeaderboardType.GLOBAL_BEST_SIZE, "screen.fishtastic.leaderboard.tab.global_size"
    );

    // Row background — reuses the same 9-sliced panel art as quest log rows/shop cards, so
    // leaderboard rows read as the same kind of "thing" as quests do elsewhere in the mod.
    private static final Identifier ROW_BG_TEXTURE = Fishtastic.id("textures/gui/generic_item_panel.png");
    private static final Identifier ROW_BG_TEXTURE_SILVER = Fishtastic.id("textures/gui/generic_item_panel_silver_border.png");
    private static final Identifier ROW_BG_TEXTURE_GOLD = Fishtastic.id("textures/gui/generic_item_panel_gold_border.png");
    // The viewing player's own row on a global board, regardless of rank/tier — same green tint
    // quest log uses to mark a claimed quest, so players can spot themselves at a glance.
    private static final Identifier ROW_BG_TEXTURE_SELF = Fishtastic.id("textures/gui/green_generic_item_panel_2.png");
    private static final int ROW_BG_SOURCE_WIDTH = 20;
    private static final int ROW_BG_SOURCE_HEIGHT = 24;
    private static final int ROW_BG_SLICE_LEFT = 4;
    private static final int ROW_BG_SLICE_TOP = 4;
    private static final int ROW_BG_SLICE_RIGHT = ROW_BG_SOURCE_WIDTH - (ROW_BG_SLICE_LEFT + 12);
    private static final int ROW_BG_SLICE_BOTTOM = ROW_BG_SOURCE_HEIGHT - (ROW_BG_SLICE_TOP + 16);

    private static final float CONTENT_WIDTH_FRACTION = 0.34f;

    private static final int RANK_COLOR_GOLD = 0xFFFFD700;
    private static final int RANK_COLOR_SILVER = 0xFFC0C0C0;
    private static final int RANK_COLOR_BRONZE = 0xFFCD7F32;
    private static final int RANK_COLOR_DEFAULT = 0xFFAAAAAA;

    // Podium — top 3 of a global leaderboard rendered as posed player models standing on
    // rank-scaled pedestals, left-to-right in classic 2nd/1st/3rd order. Purely decorative: the
    // scrollable row list below still lists every rank, including the top 3.
    private static final int PODIUM_PLAYER_WIDTH_FIRST = 44;
    private static final int PODIUM_PLAYER_HEIGHT_FIRST = 64;
    private static final int PODIUM_PLAYER_WIDTH_OTHER = 36;
    private static final int PODIUM_PLAYER_HEIGHT_OTHER = 54;
    // Pedestals are built from stacked block item renders — ItemRenderer already draws block
    // items as isometric 3D-perspective cubes (the same baked block model used in inventories),
    // so a column of them reads as a rank-scaled pillar of real blocks rather than a flat sprite.
    private static final float PODIUM_BLOCK_SCALE = 2.5f;
    // With spacing(0), two block icons already stack perfectly edge-to-edge *as sprites* — no
    // padding to close there. The gap instead comes from perspective: vanilla's block GUI display
    // transform (models/block/block.json, verified against the 26.1.2 client jar: rotate 30°
    // about X, scale 0.625) is linear, so it applies to the *offset between two stacked cubes'
    // centers* the same way it applies to the cubes themselves. Two real cubes stacked in-world
    // have centers exactly one cube-height apart; running that (0, 1, 0) offset through the same
    // scale+rotation gives the correct on-screen center distance as a fraction of one block's
    // rendered height: scale * cos(rotationX) (the Y-rotation component drops out — a rotation
    // about Y never changes a vector's Y coordinate). Edge-to-edge (spacing 0) stacking instead
    // puts centers a full block-box apart, so the overlap needed to correct that is 1 minus the
    // true fraction. This lands at ~0.4587, matching (and explaining) the ~0.46 found by eye.
    private static final float PODIUM_BLOCK_GUI_SCALE = 0.625f;
    private static final float PODIUM_BLOCK_GUI_ROTATION_X_DEGREES = 30f;
    private static final float PODIUM_BLOCK_VERTICAL_OVERLAP_FRACTION =
            1f - PODIUM_BLOCK_GUI_SCALE * (float) Math.cos(Math.toRadians(PODIUM_BLOCK_GUI_ROTATION_X_DEGREES));
    private static final float PODIUM_TOP_BLOCK_SIZE = 16f * PODIUM_BLOCK_SCALE;
    // The same perspective compression that shrinks the *distance between* two stacked block
    // centers to PODIUM_BLOCK_VERTICAL_OVERLAP_FRACTION also shrinks each block's own rendered
    // cube within its (uncompressed) nominal bounding box, centred in it — so the topmost
    // block's real top surface sits below the naive bounding-box top edge by half that same
    // fraction of the block's own size (half, because the compression eats equally from both the
    // top and bottom of the box around its centre).
    private static final float PODIUM_TOP_BLOCK_SURFACE_DROP =
            PODIUM_TOP_BLOCK_SIZE * PODIUM_BLOCK_VERTICAL_OVERLAP_FRACTION / 2f;
    private static final Map<Integer, Item> PODIUM_BLOCK_ITEM = Map.of(
            1, Items.GOLD_BLOCK,
            2, Items.IRON_BLOCK,
            3, Items.COPPER_BLOCK
    );
    /** How tall a Top Angler's pile of recent catches may get, in Fish Pile blocks. */
    private static final int PODIUM_PILE_MAX_BLOCKS = 3;
    /**
     * Fish shown in the rank-1 Top Angler's pile — the full column. Ranks 2 and 3 show a fraction
     * of this in proportion to their catch count against rank 1's, so the three piles read as a
     * to-scale comparison of the podium's hauls rather than three identical full columns (which is
     * what they became once every podium player's recorded history hit its own cap).
     */
    private static final int PODIUM_PILE_MAX_FISH = PODIUM_PILE_MAX_BLOCKS * FishPileBlockEntity.MAX_FISH;
    private static final Map<Integer, Integer> PODIUM_BLOCK_COUNT = Map.of(
            1, 3,
            2, 2,
            3, 1
    );
    // The player render's own bounding box leaves empty space below the feet (it's fit/centred
    // within its render rect rather than flush to the bottom edge), so plain VBox spacing(0)
    // between the player and the pedestal still shows a visible gap — the -6f is that part, tuned
    // by eye. On top of that, the pedestal's own top-block surface sits PODIUM_TOP_BLOCK_SURFACE_DROP
    // below its naive bounding-box top edge (see that constant's doc) — without subtracting it too,
    // the player ends up standing on the block's bounding-box edge instead of its rendered surface.
    private static final float PODIUM_PLAYER_PEDESTAL_OVERLAP = -6f - PODIUM_TOP_BLOCK_SURFACE_DROP;

    // Live element refs, one per tab, so a response for a given type can update its list in place
    // without rebuilding the other three tabs.
    private final Map<LeaderboardType, VBox> listWrappers = new EnumMap<>(LeaderboardType.class);
    // Only populated for the two global tabs — personal tabs have no podium.
    private final Map<LeaderboardType, HBox> podiumWrappers = new EnumMap<>(LeaderboardType.class);

    private ItemTabs tabs;
    private int activeTabIndex = 0;

    // Temp render context for measuring text during label construction (graphics can be null)
    private MinecraftRenderContext tempContext;

    public LeaderboardScreen(GelatinMenu menu, Inventory inv) {
        super(menu, inv, Component.translatable("screen.fishtastic.leaderboard.title"));
    }

    @Override
    protected void buildUI() {
        listWrappers.clear();
        podiumWrappers.clear();
        tabs = null;

        tempContext = new MinecraftRenderContext(null, this.font);
        LeaderboardResponsePacket.registerClientHandler(this::onLeaderboardResponse);

        Label titleLabel = new Label(translated("screen.fishtastic.leaderboard.title"), 0xFFFFFFFF).init(tempContext);
        titleLabel.scale(1.3f);
        titleLabel.addBreatheEffect();
        titleLabel.onMouseEnter(e -> titleLabel.setTargetScale(1.5f, true));
        titleLabel.onMouseExit(e -> titleLabel.setTargetScale(1.3f, true));

        VBox header = UI.vbox().spacing(4).alignment(VBox.Alignment.CENTER);
        header.addChild(titleLabel);

        ItemStack playerHead = new ItemStack(Items.PLAYER_HEAD);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            FishtasticItemData.setHeadProfile(playerHead, ResolvableProfile.createResolved(mc.player.getGameProfile()));
        }

        ItemTabs itemTabs = UI.itemTabs();
        itemTabs.addTab(new ItemStack(Items.FISHING_ROD), scaleTabPanel(buildLeaderboardTab(LeaderboardType.PERSONAL_CATCH_COUNT)));
        itemTabs.addTab(new ItemStack(Items.PUFFERFISH), scaleTabPanel(buildLeaderboardTab(LeaderboardType.PERSONAL_BEST_SIZE)));
        itemTabs.addTab(playerHead, scaleTabPanel(buildLeaderboardTab(LeaderboardType.GLOBAL_CATCH_COUNT)));
        itemTabs.addTab(new ItemStack(Items.COD), scaleTabPanel(buildLeaderboardTab(LeaderboardType.GLOBAL_BEST_SIZE)));

        tabs = itemTabs;

        VBox content = UI.vbox().spacing(10).padding(16).alignment(VBox.Alignment.CENTER);
        content.addChild(header);
        content.addChild(tabs);

        tabs.onSelectionChanged(i -> {
            activeTabIndex = i;
            requestLeaderboard(TAB_TYPES[i]);
            recenterContent(content);
        });
        tabs.select(activeTabIndex);
        recenterContent(content);

        uiScreen.setRoot(content);
        uiScreen.setAutoCenterRoot(false);
        uiScreen.setScrollEnabled(true);

        requestLeaderboard(TAB_TYPES[activeTabIndex]);
    }

    private VBox scaleTabPanel(VBox panel) {
        panel.scaleToWidth(this.width * CONTENT_WIDTH_FRACTION);
        return panel;
    }

    private void recenterContent(VBox content) {
        content.forceLayout();
        Vector2f size = content.getSize();
        content.setPosition(new Vector2f((this.width - size.x) / 2f, 0f));
        // UIScreen re-applies its own cached base position over content's every frame (for
        // scrolling); re-registering the root re-syncs that cache to the position we just set.
        if (uiScreen != null) {
            uiScreen.setRoot(content);
        }
    }

    @Override
    public void removed() {
        super.removed();
        LeaderboardResponsePacket.registerClientHandler(null);
    }

    // -------------------------------------------------------------------------
    // Tab construction

    private VBox buildLeaderboardTab(LeaderboardType type) {
        VBox wrapper = UI.vbox().spacing(6).padding(4).alignment(VBox.Alignment.CENTER);
        wrapper.addChild(label(translated(TAB_SUBTITLE_KEYS.get(type)), 0xFF88CCFF));

        if (isGlobal(type)) {
            HBox podium = UI.hbox().spacing(4).alignment(HBox.Alignment.BOTTOM);
            podiumWrappers.put(type, podium);
            wrapper.addChild(podium);
        }

        VBox listWrapper = UI.vbox().spacing(4).alignment(VBox.Alignment.CENTER);
        listWrapper.addChild(label(translated("screen.fishtastic.leaderboard.loading"), 0xFF888888));
        wrapper.addChild(listWrapper);

        listWrappers.put(type, listWrapper);
        return wrapper;
    }

    // -------------------------------------------------------------------------
    // Network

    private void requestLeaderboard(LeaderboardType type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Optional<UUID> target = (type == LeaderboardType.PERSONAL_BEST_SIZE || type == LeaderboardType.PERSONAL_CATCH_COUNT)
                ? Optional.of(mc.player.getUUID())
                : Optional.empty();
        mc.player.connection.send(new ServerboundCustomPayloadPacket(
                new RequestLeaderboardPacket(type, false, target)));
    }

    private void onLeaderboardResponse(LeaderboardResponsePacket packet) {
        VBox listWrapper = listWrappers.get(packet.leaderboardType());
        if (listWrapper == null) return;

        listWrapper.clearChildren();

        List<LeaderboardEntry> entries = packet.entries();
        if (entries.isEmpty()) {
            listWrapper.addChild(label(translated("screen.fishtastic.leaderboard.no_entries"), 0xFF888888));
        } else {
            Minecraft mc = Minecraft.getInstance();
            UUID selfUuid = mc.player != null ? mc.player.getUUID() : null;
            for (int i = 0; i < entries.size(); i++) {
                listWrapper.addChild(buildEntryRow(i + 1, entries.get(i), packet.leaderboardType(), selfUuid));
            }
        }

        HBox podium = podiumWrappers.get(packet.leaderboardType());
        if (podium != null) {
            rebuildPodium(podium, entries, packet.leaderboardType());
        }
    }

    // -------------------------------------------------------------------------
    // Podium

    private static boolean isGlobal(LeaderboardType type) {
        return type == LeaderboardType.GLOBAL_CATCH_COUNT || type == LeaderboardType.GLOBAL_BEST_SIZE;
    }

    private void rebuildPodium(HBox podium, List<LeaderboardEntry> entries, LeaderboardType type) {
        podium.clearChildren();
        // Rank 1's catch count is what the other two piles are measured against, so it's read once
        // here (entries arrive sorted best-first) and handed to every slot.
        int topCatchCount = entries.isEmpty() ? 0 : entries.get(0).catchCount();
        // Classic podium order: 2nd, 1st, 3rd, left to right.
        int[] order = {1, 0, 2};
        for (int rank : order) {
            if (rank < entries.size()) {
                podium.addChild(buildPodiumSlot(rank + 1, entries.get(rank), type, topCatchCount));
            }
        }
    }

    private VBox buildPodiumSlot(int rank, LeaderboardEntry entry, LeaderboardType type, int topCatchCount) {
        boolean isFirst = rank == 1;
        int playerWidth = isFirst ? PODIUM_PLAYER_WIDTH_FIRST : PODIUM_PLAYER_WIDTH_OTHER;
        int playerHeight = isFirst ? PODIUM_PLAYER_HEIGHT_FIRST : PODIUM_PLAYER_HEIGHT_OTHER;

        VBox slot = UI.vbox().spacing(2).alignment(VBox.Alignment.CENTER);

        UUID uuid = entry.playerUuid().orElse(null);
        String name = entry.playerName().orElse(null);
        slot.addChild(label(name != null ? name : translated("screen.fishtastic.leaderboard.unknown_player"), rankColor(rank)));

        // Player standing on the pedestal, positioned manually rather than via VBox: VBox couples
        // layout order with paint order (same reason buildPedestal's own block stack needs a
        // ManualContainer), and here the player must be positioned ABOVE the pedestal but PAINTED
        // in front of it — the two blocks paint back-to-front by list order regardless of position,
        // so the pedestal is added first (behind) and the player second (in front).
        if (type == LeaderboardType.GLOBAL_CATCH_COUNT) {
            // Top Anglers shows no player at all: an angler is represented by their haul — the
            // Fish Pile block, stacked with the actual fish they most recently landed, sitting on
            // the pedestal as one continuous column of blocks.
            slot.addChild(buildBlockColumn(topAnglerColumn(entry, topCatchCount)));
            return slot;
        }

        ManualContainer playerOnPedestal = UI.manualContainer();
        ManualContainer pedestal = buildPedestal(rank, type);
        Vector2f pedestalSize = pedestal.getSize();

        float groupWidth = Math.max(playerWidth, pedestalSize.x);
        float pedestalTop = playerHeight + PODIUM_PLAYER_PEDESTAL_OVERLAP;
        float groupHeight = pedestalTop + pedestalSize.y;
        playerOnPedestal.setSize(groupWidth, groupHeight);

        playerOnPedestal.addChildAt(pedestal, groupWidth / 2f, pedestalTop + pedestalSize.y / 2f);
        if (uuid != null) {
            if (type == LeaderboardType.GLOBAL_BEST_SIZE) {
                PlayerAvatarRenderer avatar = UI.playerAvatar(playerWidth, playerHeight)
                        .profile(PlayerHeadItems.resolvableProfile(uuid, name))
                        .heldItem(catchStack(entry));
                playerOnPedestal.addChildAt(avatar, groupWidth / 2f, playerHeight / 2f);
            } else {
                PlayerModelRenderer model = UI.playerModel(playerWidth, playerHeight)
                        .profile(PlayerHeadItems.resolvableProfile(uuid, name))
                        .pose(isFirst ? PlayerPoses.VICTORY : PlayerPoses.ARMS_CROSSED);
                playerOnPedestal.addChildAt(model, groupWidth / 2f, playerHeight / 2f);
            }
        }
        slot.addChild(playerOnPedestal);

        return slot;
    }

    /** The fish this podium entry's held-item render shows off — their recorded biggest catch. */
    private static ItemStack catchStack(LeaderboardEntry entry) {
        return entry.fishType().map(loc -> {
            Item item = BuiltInRegistries.ITEM.getOptional(loc).orElse(Items.COD);
            ItemStack stack = new ItemStack(item);
            ItemSizeHelper.setSize(stack, entry.size());
            FishQualityHelper.setQuality(stack, entry.quality());
            return stack;
        }).orElse(ItemStack.EMPTY);
    }

    /**
     * The Top Anglers column, bottom block first: the flat single-gold-block pedestal every rank
     * gets, topped by this player's recent catches piled up as Fish Pile blocks, as many of them as
     * {@link #pileFishBudget} allows this rank. An entry with no recorded catch history (a world
     * from before that history was kept) is just the pedestal.
     */
    private static List<ItemStack> topAnglerColumn(LeaderboardEntry entry, int topCatchCount) {
        List<ItemStack> column = new ArrayList<>();
        column.add(new ItemStack(Items.GOLD_BLOCK));
        column.addAll(FishPileIcons.pileBlocks(entry.recentCatches(),
                pileFishBudget(entry.catchCount(), topCatchCount)));
        return column;
    }

    /**
     * How many fish this podium entry's pile may show: its catch count as a fraction of rank 1's,
     * scaled onto {@link #PODIUM_PILE_MAX_FISH}. Any player with at least one catch keeps at least
     * one fish, so a trailing third place still reads as a pile rather than a bare pedestal.
     *
     * <p>The pile can still come up short of its budget — it only ever draws fish the server
     * actually recorded, and that per-player history is itself capped at
     * {@link grill24.fishtastic.server.FishCatchSavedData#MAX_RECENT_CATCHES}.
     */
    private static int pileFishBudget(int catchCount, int topCatchCount) {
        if (catchCount <= 0) return 0;
        if (topCatchCount <= 0) return PODIUM_PILE_MAX_FISH;
        int budget = Math.round(PODIUM_PILE_MAX_FISH * (float) catchCount / (float) topCatchCount);
        return Math.clamp(budget, 1, PODIUM_PILE_MAX_FISH);
    }

    private ManualContainer buildPedestal(int rank, LeaderboardType type) {
        // The Top Anglers (catch count) podium keeps a flat single-gold-block pedestal for every
        // rank — only Best Size's rank-tiered gold/iron/copper stack varies in height.
        Item blockItem = type == LeaderboardType.GLOBAL_CATCH_COUNT
                ? Items.GOLD_BLOCK
                : PODIUM_BLOCK_ITEM.getOrDefault(rank, Items.COPPER_BLOCK);
        int blockCount = type == LeaderboardType.GLOBAL_CATCH_COUNT
                ? 1
                : PODIUM_BLOCK_COUNT.getOrDefault(rank, 1);

        List<ItemStack> column = new ArrayList<>(blockCount);
        for (int i = 0; i < blockCount; i++) {
            column.add(new ItemStack(blockItem));
        }
        return buildBlockColumn(column);
    }

    /** Renders {@code column} (bottom block first) as one flush-stacked pillar of block icons. */
    private ManualContainer buildBlockColumn(List<ItemStack> column) {
        int blockCount = Math.max(column.size(), 1);

        float blockSize = PODIUM_TOP_BLOCK_SIZE;
        float spacing = -(blockSize * PODIUM_BLOCK_VERTICAL_OVERLAP_FRACTION);
        float step = blockSize + spacing;
        float totalHeight = blockSize + (blockCount - 1) * step;

        ManualContainer pedestal = UI.manualContainer().maxChildren(blockCount);
        pedestal.setSize(blockSize, totalHeight);

        // VBox lays out AND paints in the same list order (first child = top position = painted
        // first = behind), so the overlap from the negative spacing above showed the bottom block
        // drawn in front of the one above it. A ManualContainer decouples position from paint
        // order: positions are set explicitly below (top block at y=0, same as before), while
        // children are added bottom-most first so the stack paints back-to-front, top in front.
        // Iterated bottom block first (index 0) to keep that back-to-front paint order, while the
        // positions run the other way: y grows downward, so the bottom block gets the largest y.
        for (int i = 0; i < column.size(); i++) {
            float centerY = (column.size() - 1 - i) * step + blockSize / 2f;
            pedestal.addChildAt(UI.itemRenderer(column.get(i)).itemScale(PODIUM_BLOCK_SCALE), blockSize / 2f, centerY);
        }
        return pedestal;
    }

    // -------------------------------------------------------------------------
    // Row builders

    private VBox buildEntryRow(int rank, LeaderboardEntry entry, LeaderboardType type, UUID selfUuid) {
        boolean isSelf = (type == LeaderboardType.GLOBAL_CATCH_COUNT || type == LeaderboardType.GLOBAL_BEST_SIZE)
                && selfUuid != null && entry.playerUuid().map(selfUuid::equals).orElse(false);

        HBox inner = UI.hbox().spacing(6).alignment(HBox.Alignment.CENTER);
        inner.addChild(label(translated("screen.fishtastic.leaderboard.rank", rank), rankColor(rank)));

        if (type == LeaderboardType.GLOBAL_CATCH_COUNT) {
            entry.playerUuid().ifPresent(uuid ->
                    inner.addChild(UI.itemRenderer(PlayerHeadItems.headStack(uuid, entry.playerName().orElse(null)))));
        } else {
            entry.fishType().ifPresent(loc -> {
                Item item = BuiltInRegistries.ITEM.getOptional(loc).orElse(Items.COD);
                inner.addChild(UI.itemRenderer(new ItemStack(item)));
            });
        }

        inner.addChild(label(entryText(entry, type), isSelf ? 0xFFAAFFAA : 0xFFFFFFFF));

        if (type == LeaderboardType.GLOBAL_BEST_SIZE) {
            entry.playerUuid().ifPresent(uuid ->
                    inner.addChild(UI.itemRenderer(PlayerHeadItems.headStack(uuid, entry.playerName().orElse(null)))));
        }

        VBox row = UI.vbox().padding(3, 3, 6, 6).alignment(VBox.Alignment.CENTER);
        row.backgroundSprite(rowBackgroundSprite(rank, isSelf));
        row.onMouseEnter(e -> row.setTargetScale(1.03f, true));
        row.onMouseExit(e -> row.setTargetScale(1.0f, true));
        row.addChild(inner);
        return row;
    }

    private static int rankColor(int rank) {
        return switch (rank) {
            case 1 -> RANK_COLOR_GOLD;
            case 2 -> RANK_COLOR_SILVER;
            case 3 -> RANK_COLOR_BRONZE;
            default -> RANK_COLOR_DEFAULT;
        };
    }

    private SpriteData rowBackgroundSprite(int rank, boolean isSelf) {
        Identifier texture;
        if (isSelf) {
            texture = ROW_BG_TEXTURE_SELF;
        } else {
            texture = switch (rank) {
                case 1 -> ROW_BG_TEXTURE_GOLD;
                case 2 -> ROW_BG_TEXTURE_SILVER;
                default -> ROW_BG_TEXTURE;
            };
        }
        return new SpriteData(texture)
                .uv(0, 0, ROW_BG_SOURCE_WIDTH, ROW_BG_SOURCE_HEIGHT)
                .textureSize(ROW_BG_SOURCE_WIDTH, ROW_BG_SOURCE_HEIGHT)
                .renderMode(SpriteRenderMode.SLICE)
                .slice(ROW_BG_SLICE_LEFT, ROW_BG_SLICE_RIGHT, ROW_BG_SLICE_TOP, ROW_BG_SLICE_BOTTOM);
    }

    private static String entryText(LeaderboardEntry entry, LeaderboardType type) {
        return switch (type) {
            case PERSONAL_BEST_SIZE, GLOBAL_BEST_SIZE -> Component.translatable(
                    "screen.fishtastic.leaderboard.entry_size",
                    fishDisplayName(entry),
                    Component.translatable("tooltip.fishtastic.item_size.cm", String.format("%.0f", entry.size()))
            ).getString();
            case PERSONAL_CATCH_COUNT -> Component.translatable(
                    "screen.fishtastic.leaderboard.entry_count",
                    fishDisplayName(entry), entry.catchCount()
            ).getString();
            case GLOBAL_CATCH_COUNT -> Component.translatable(
                    "screen.fishtastic.leaderboard.entry_count",
                    entry.playerName().<Component>map(Component::literal)
                            .orElseGet(() -> Component.translatable("screen.fishtastic.leaderboard.unknown_player")),
                    entry.catchCount()
            ).getString();
        };
    }

    private static Component fishDisplayName(LeaderboardEntry entry) {
        return entry.fishType()
                .<Component>map(loc -> BuiltInRegistries.ITEM.getOptional(loc)
                        .<Component>map(item -> Component.translatable(item.getDescriptionId()))
                        .orElseGet(() -> Component.literal(prettyName(loc.getPath()))))
                .orElseGet(() -> Component.translatable("screen.fishtastic.leaderboard.unknown_fish"));
    }

    private static String prettyName(String path) {
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static String translated(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private Label label(String text, int color) {
        return new Label(text, color).init(tempContext);
    }
}
