package grill24.fishtastic.client;

import com.mojang.authlib.GameProfile;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.network.LeaderboardEntry;
import grill24.fishtastic.network.LeaderboardResponsePacket;
import grill24.fishtastic.network.LeaderboardType;
import grill24.fishtastic.network.RequestLeaderboardPacket;
import io.github.currenj.gelatinui.GelatinUIScreen;
import io.github.currenj.gelatinui.gui.UI;
import io.github.currenj.gelatinui.gui.components.HBox;
import io.github.currenj.gelatinui.gui.components.ItemTabs;
import io.github.currenj.gelatinui.gui.components.Label;
import io.github.currenj.gelatinui.gui.components.SpriteData;
import io.github.currenj.gelatinui.gui.components.SpriteRenderMode;
import io.github.currenj.gelatinui.gui.components.VBox;
import io.github.currenj.gelatinui.gui.GelatinMenu;
import io.github.currenj.gelatinui.gui.minecraft.MinecraftRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
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

    // Live element refs, one per tab, so a response for a given type can update its list in place
    // without rebuilding the other three tabs.
    private final Map<LeaderboardType, VBox> listWrappers = new EnumMap<>(LeaderboardType.class);

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
            playerHead.set(DataComponents.PROFILE, ResolvableProfile.createResolved(mc.player.getGameProfile()));
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
                    inner.addChild(UI.itemRenderer(playerHeadStack(uuid, entry.playerName().orElse("?")))));
        } else {
            entry.fishType().ifPresent(loc -> {
                Item item = BuiltInRegistries.ITEM.getOptional(loc).orElse(Items.COD);
                inner.addChild(UI.itemRenderer(new ItemStack(item)));
            });
        }

        inner.addChild(label(entryText(entry, type), isSelf ? 0xFFAAFFAA : 0xFFFFFFFF));

        if (type == LeaderboardType.GLOBAL_BEST_SIZE) {
            entry.playerUuid().ifPresent(uuid ->
                    inner.addChild(UI.itemRenderer(playerHeadStack(uuid, entry.playerName().orElse("?")))));
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

    private static ItemStack playerHeadStack(UUID uuid, String name) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(new GameProfile(uuid, name)));
        return head;
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
