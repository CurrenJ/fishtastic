package grill24.fishtastic.client;

import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.client.effects.CoinArcEffect;
import grill24.fishtastic.client.effects.DropOffEffect;
import grill24.fishtastic.client.effects.PendulumSwingEffect;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.QuestCategory;
import grill24.fishtastic.data.QuestObjective;
import grill24.fishtastic.data.QuestReward;
import grill24.fishtastic.data.ShopEntry;
import grill24.fishtastic.tutorial.TutorialStep;
import grill24.fishtastic.network.CompleteQuestPacket;
import grill24.fishtastic.network.PurchaseShopEntryPacket;
import grill24.fishtastic.network.QuestSyncPacket;
import grill24.fishtastic.network.RefreshShopPacket;
import grill24.fishtastic.server.PlayerQuestState;
import grill24.fishtastic.server.QuestTracker;
import io.github.currenj.gelatinui.GelatinUIScreen;
import io.github.currenj.gelatinui.gui.IUIElement;
import io.github.currenj.gelatinui.gui.PivotMode;
import io.github.currenj.gelatinui.gui.UI;
import io.github.currenj.gelatinui.gui.UIContainer;
import io.github.currenj.gelatinui.gui.UIElement;
import io.github.currenj.gelatinui.gui.GelatinMenu;
import io.github.currenj.gelatinui.gui.components.*;
import org.jetbrains.annotations.Nullable;
import io.github.currenj.gelatinui.gui.effects.CoinSpinEffect;
import io.github.currenj.gelatinui.gui.minecraft.MinecraftRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;

import io.github.currenj.gelatinui.gui.animation.FloatKeyframeAnimation;
import io.github.currenj.gelatinui.gui.animation.Keyframe;
import org.joml.Vector2f;

import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class QuestLogScreen extends GelatinUIScreen<GelatinMenu> {

    private MinecraftRenderContext tempContext;
    private boolean handlerInstalled = false;
    private QuestSyncPacket.ClientHandler savedHandler;
    private int activeTabIndex = 0;

    // Live element refs for in-place updates (populated by buildUI, cleared on rebuild)
    private Label tokenBalanceLabel;
    private HBox tokenIconRow;
    private ManualContainer coinFlyOverlay;
    private int coinSpawnSeq = 0; // unique per-coin channel suffix for staggered spawn timers
    private Label cleanupGoalTotalLabel;
    private Label cleanupGoalCountLabel;
    private SpriteProgressBar cleanupGoalBar;
    private Label dailyResetLabel;
    private Label cleanupGoalResetLabel;
    private Label shopResetLabel;
    private SpriteButton shopRefreshBtn;
    private Label shopRefreshCostLabel;
    private Label shopRefreshNotEnoughLabel;
    private final Map<Identifier, QuestRowRefs> questRowRefs = new LinkedHashMap<>();
    private final Map<ResourceKey<ShopEntry>, ShopCardRefs> shopCardRefs = new LinkedHashMap<>();
    private ItemTabs questTabs;
    private final Map<QuestCategory, List<ResourceKey<Quest>>> questKeysByCategory = new EnumMap<>(QuestCategory.class);
    private static final Map<QuestCategory, Integer> QUEST_CATEGORY_TAB_INDEX = Map.of(
            QuestCategory.DAILY, 0,
            QuestCategory.MASTERY, 1,
            QuestCategory.EXPLORER, 2,
            QuestCategory.CHALLENGE, 3
    );

    private record QuestRowRefs(
            VBox row,
            Label nameLabel,
            SpriteButton claimButton,
            ThinProgressBar progressBar,
            Label countLabel,
            int targetCount,
            String baseDisplayName,
            QuestObjective objective,
            SpriteRectangle.SpriteRectangleImpl statusPip
    ) {}

    // Target scale a quest row settles at once its reward has been claimed.
    private static final float CLAIMED_QUEST_ROW_SCALE = 1f;

    // Very slight per-leaf hover scale-up applied to each element within a quest row.
    private static final float QUEST_ROW_LEAF_HOVER_SCALE = 1.04f;

    // Number of quest entries laid out side-by-side before wrapping to a new row.
    private static final int QUESTS_PER_ROW = 3;

    private record ShopCardRefs(
            VBox card,
            ManualContainer fallingPanel,
            Label nameLabel,
            HBox costRow,
            Label costLabel,
            Label soldOutLabel,
            SpriteButton buyBtn,
            Label notEnoughLabel,
            @Nullable Label descriptionLabel,
            ShopEntry entry,
            ResourceKey<ShopEntry> key
    ) {}

    // Shop item panel — displays a purchasable item pinned to the board; falls away with
    // accelerating (gravity-like) motion on purchase, then pops back in shortly after if the
    // entry is still available.
    private static final Identifier SHOP_ITEM_PANEL_TEXTURE = Fishtastic.id("textures/gui/generic_item_panel.png");
    private static final Identifier SHOP_ITEM_PANEL_PIN_TEXTURE = Fishtastic.id("textures/gui/generic_item_panel_pin_only.png");
    // Source texture files are 20x24; rendered at SHOP_ITEM_PANEL_SCALE for a bigger on-screen panel.
    private static final int SHOP_ITEM_PANEL_SOURCE_WIDTH = 20;
    private static final int SHOP_ITEM_PANEL_SOURCE_HEIGHT = 24;
    private static final float SHOP_ITEM_PANEL_SCALE = 4f;
    private static final int SHOP_ITEM_PANEL_WIDTH = Math.round(SHOP_ITEM_PANEL_SOURCE_WIDTH * SHOP_ITEM_PANEL_SCALE);
    private static final int SHOP_ITEM_PANEL_HEIGHT = Math.round(SHOP_ITEM_PANEL_SOURCE_HEIGHT * SHOP_ITEM_PANEL_SCALE);
    // Centers a 16x16 icon (scaled up to match) so its top-left lands at (2,5) in source-texture space
    private static final Vector2f SHOP_ITEM_ICON_CENTER = new Vector2f(10f * SHOP_ITEM_PANEL_SCALE, 13f * SHOP_ITEM_PANEL_SCALE);
    // Leaves room in the name+price row for the cost icon and number alongside the name
    private static final float SHOP_ITEM_NAME_MAX_WIDTH = 90f;
    private static final float SHOP_ITEM_DESCRIPTION_MAX_WIDTH = 140f;

    private static final Identifier SHOP_BUY_BUTTON_TEXTURE = Fishtastic.id("textures/gui/buy_button_2.png");
    private static final int SHOP_BUY_BUTTON_FILE_WIDTH = 18;
    private static final int SHOP_BUY_BUTTON_FILE_HEIGHT = 14;
    // This file's opaque art fills the entire 18x14 canvas — no cropping needed.
    private static final int SHOP_BUY_BUTTON_SOURCE_WIDTH = 18;
    private static final int SHOP_BUY_BUTTON_SOURCE_HEIGHT = 14;
    // Scaled down from SHOP_ITEM_PANEL_SCALE so the button hugs the "Buy" label rather than
    // matching the bigger item panel's scale.
    private static final float SHOP_BUY_BUTTON_SCALE = 3f;
    private static final int SHOP_BUY_BUTTON_WIDTH = Math.round(SHOP_BUY_BUTTON_SOURCE_WIDTH * SHOP_BUY_BUTTON_SCALE);
    private static final int SHOP_BUY_BUTTON_HEIGHT = Math.round(SHOP_BUY_BUTTON_SOURCE_HEIGHT * SHOP_BUY_BUTTON_SCALE);

    // Alert badge shown on a tab icon when that category has a completed-but-unclaimed quest.
    private static final Identifier TAB_ALERT_TEXTURE = Fishtastic.id("textures/gui/alert_2.png");
    private static final float TAB_ALERT_WIDTH = 2.5f;
    private static final float TAB_ALERT_HEIGHT = 7.5f;

    // Quest row background — reuses the shop item panel texture, 9-sliced so the border art
    // stays crisp while the middle stretches to fit each row's actual (varying) size.
    private static final Identifier QUEST_ROW_BG_TEXTURE = Fishtastic.id("textures/gui/generic_item_panel.png");
    // Claimed quests use a green-tinted variant of the same panel to signal completion.
    private static final Identifier QUEST_ROW_BG_TEXTURE_CLAIMED = Fishtastic.id("textures/gui/green_generic_item_panel_2.png");
    private static final int QUEST_ROW_BG_SOURCE_WIDTH = 20;
    private static final int QUEST_ROW_BG_SOURCE_HEIGHT = 24;
    // Requested center/stretch region: origin (4,4), size (12,16) within the 20x24 source.
    private static final int QUEST_ROW_BG_SLICE_LEFT = 4;
    private static final int QUEST_ROW_BG_SLICE_TOP = 4;
    private static final int QUEST_ROW_BG_SLICE_RIGHT = QUEST_ROW_BG_SOURCE_WIDTH - (QUEST_ROW_BG_SLICE_LEFT + 12);
    private static final int QUEST_ROW_BG_SLICE_BOTTOM = QUEST_ROW_BG_SOURCE_HEIGHT - (QUEST_ROW_BG_SLICE_TOP + 16);

    // Claim button — reuses the same green claimed-panel texture as the row background,
    // 9-sliced so its border art stays crisp at the button's small on-screen size.
    private static final Identifier QUEST_CLAIM_BUTTON_TEXTURE = Fishtastic.id("textures/gui/green_generic_item_panel_2.png");

    // Status pip shown to the left of a quest's title when its objective has a spatial
    // (biome) or temporal (time of day / weather) condition — lit green while that
    // condition currently holds for the player, dim otherwise.
    private static final Identifier STATUS_PIP_DEFAULT_TEXTURE = Fishtastic.id("textures/gui/status_indicator_pip_default.png");
    private static final Identifier STATUS_PIP_GREEN_TEXTURE = Fishtastic.id("textures/gui/status_indicator_pip_green.png");
    private static final float STATUS_PIP_SIZE = 5f;
    private static final float STATUS_PIP_TOOLTIP_SCALE = 0.7f;

    private static final float SHOP_ITEM_FALL_DURATION = 0.65f;
    private static final float SHOP_ITEM_RESPAWN_DELAY = 0.5f;
    private static final float SHOP_ITEM_FALL_DISTANCE = 400f;
    private static final float SHOP_ITEM_FALL_ROTATION = 25f;
    private static final float SHOP_ITEM_SWING_START_ANGLE = 30f;
    private static final float SHOP_ITEM_SWING_FREQUENCY = 2.5f;
    private static final float SHOP_ITEM_SWING_DAMPING = 3.0f;
    private static final float SHOP_ITEM_SWING_DURATION = 2.0f;

    // Quest claim reward feedback — mini coins that fly from the claimed quest row up to the
    // token balance display, arcing with some randomness, while the balance counts up.
    private static final int QUEST_CLAIM_MIN_COINS = 6;
    private static final int QUEST_CLAIM_MAX_COINS = 14;
    private static final float COIN_FLIGHT_DURATION_MIN = 0.9f;
    private static final float COIN_FLIGHT_DURATION_MAX = 1.3f;
    private static final float COIN_SPAWN_STAGGER = 0.05f; // base delay per coin index
    private static final float COIN_SPAWN_STAGGER_JITTER = 0.05f; // added random jitter so the burst isn't metronomic
    private static final float COIN_ARC_HEIGHT_MIN = 22f;
    private static final float COIN_ARC_HEIGHT_MAX = 48f;
    private static final float COIN_SCATTER_START = 10f;
    private static final float COIN_SCATTER_TARGET = 18f;
    private static final float COIN_ITEM_SCALE_MIN = 0.45f;
    private static final float COIN_ITEM_SCALE_MAX = 0.7f;
    private static final float COIN_SPIN_SPEED_MIN = 480f;
    private static final float COIN_SPIN_SPEED_MAX = 900f;

    public QuestLogScreen(GelatinMenu menu, Inventory inv) {
        super(menu, inv, Component.translatable("screen.fishtastic.quest_log.title"));
    }

    @Override
    protected void init() {
        if (!handlerInstalled) {
            savedHandler = QuestSyncPacket.clientHandler;
            handlerInstalled = true;
            QuestSyncPacket.registerClientHandler(packet -> {
                int previousShopRefreshCount = QuestClientCache.getShopRefreshCount();
                QuestClientCache.update(packet.questProgress(), packet.tokenBalance(), packet.triggeringItems(),
                        packet.purchaseCounts(), packet.cleanupGoal(), packet.serverGameTime(),
                        packet.baitDepletedItem(), packet.firstCatchItems(), packet.shopRefreshCount());
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == this) {
                    // A shop refresh changes which entries are active, not just their visuals -
                    // a full rebuild is needed rather than the usual in-place widget update.
                    if (packet.shopRefreshCount() != previousShopRefreshCount) {
                        buildUI();
                    } else {
                        updateInPlace();
                    }
                }
            });
        }
        super.init();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // Ticked locally every frame rather than only on quest-sync packets, so the countdown
        // text counts down smoothly instead of jumping only when the player catches something.
        if (dailyResetLabel != null) {
            dailyResetLabel.text(formatResetCountdown("screen.fishtastic.quest_log.dailies_reset", QuestClientCache.getTicksUntilDailyReset()));
        }
        if (cleanupGoalResetLabel != null) {
            cleanupGoalResetLabel.text(formatResetCountdown("screen.fishtastic.quest_log.cleanup.resets", QuestClientCache.getTicksUntilCleanupGoalReset()));
        }
        if (shopResetLabel != null) {
            shopResetLabel.text(formatResetCountdown("screen.fishtastic.quest_log.shop.resets", QuestClientCache.getTicksUntilDailyReset()));
        }
        // Ticked locally (rather than only on quest-sync) so a conditioned quest's pip reacts
        // live to weather changing, day/night passing, or the player walking between biomes.
        for (Map.Entry<Identifier, QuestRowRefs> e : questRowRefs.entrySet()) {
            QuestRowRefs refs = e.getValue();
            if (refs.statusPip() != null) {
                updateStatusPip(refs, QuestClientCache.getProgress(e.getKey()).claimed());
            }
        }
    }

    /** Formats a tick countdown as "Xd HH:MM:SS" / "H:MM:SS" / "MM:SS", shrinking to whichever units are non-zero, and substitutes it into {@code translationKey} (e.g. "Dailies reset in %s"). */
    private static String formatResetCountdown(String translationKey, long ticksRemaining) {
        String countdown;
        if (ticksRemaining < 0) {
            countdown = "--:--";
        } else {
            long totalSeconds = ticksRemaining / 20L;
            long days = totalSeconds / 86400L;
            long hours = (totalSeconds % 86400L) / 3600L;
            long minutes = (totalSeconds % 3600L) / 60L;
            long seconds = totalSeconds % 60L;
            if (days > 0) countdown = String.format("%dd %02d:%02d:%02d", days, hours, minutes, seconds);
            else if (hours > 0) countdown = String.format("%d:%02d:%02d", hours, minutes, seconds);
            else countdown = String.format("%02d:%02d", minutes, seconds);
        }
        return translated(translationKey, countdown);
    }

    @Override
    protected void buildUI() {
        questRowRefs.clear();
        shopCardRefs.clear();
        questTabs = null;
        tokenBalanceLabel = null;
        tokenIconRow = null;
        coinFlyOverlay = null;
        cleanupGoalTotalLabel = null;
        cleanupGoalCountLabel = null;
        cleanupGoalBar = null;
        dailyResetLabel = null;
        cleanupGoalResetLabel = null;
        shopResetLabel = null;
        shopRefreshBtn = null;
        shopRefreshCostLabel = null;
        shopRefreshNotEnoughLabel = null;

        tempContext = new MinecraftRenderContext(null, this.font);

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Registry<Quest> questRegistry;
        try {
            questRegistry = mc.level.registryAccess().lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
        } catch (Exception e) {
            Label err = new Label(translated("screen.fishtastic.quest_log.registry_unavailable"), 0xFFFF4444).init(tempContext);
            VBox errorRoot = UI.vbox().alignment(VBox.Alignment.CENTER);
            errorRoot.addChild(err);
            uiScreen.setRoot(errorRoot);
            uiScreen.setAutoCenterRoot(true);
            return;
        }

        Registry<ShopEntry> shopRegistry = null;
        try {
            shopRegistry = mc.level.registryAccess().lookupOrThrow(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY);
        } catch (Exception ignored) {
        }

        long currentDay = mc.level.getGameTime() / 24000L;
        Set<ResourceKey<Quest>> activeDailies = QuestTracker.getActiveDailies(questRegistry, currentDay);

        Map<QuestCategory, List<Map.Entry<ResourceKey<Quest>, Quest>>> byCategory = new EnumMap<>(QuestCategory.class);
        for (QuestCategory cat : QuestCategory.values()) byCategory.put(cat, new ArrayList<>());
        for (Map.Entry<ResourceKey<Quest>, Quest> entry : questRegistry.entrySet()) {
            byCategory.get(entry.getValue().category()).add(entry);
        }
        for (var list : byCategory.values()) {
            list.sort(Comparator.comparing(e -> e.getValue().displayName()));
        }

        // Only today's rotated-in dailies are worth showing — the rest of the daily pool
        // stays hidden rather than listed as "inactive", so players discover it as it rotates in.
        byCategory.get(QuestCategory.DAILY).removeIf(entry -> !activeDailies.contains(entry.getKey()));

        // Secret quests (e.g. unlisted-fish reveals, completionist capstones) stay out of the log
        // entirely until their objective is met — progress still tracks silently in the background
        // (QuestTracker doesn't check `hidden`), so they surface already complete, ready to claim.
        for (var list : byCategory.values()) {
            list.removeIf(entry -> entry.getValue().hidden()
                    && !QuestClientCache.getProgress(entry.getKey().identifier()).completed());
        }

        // During the tutorial, pin tutorial quests to the top of the Daily tab.
        // They are hidden once the tutorial is complete or hasn't started yet.
        TutorialStep tutorialStep = TutorialClientHandler.getCurrentStep();
        boolean tutorialActive = tutorialStep != TutorialStep.COMPLETE
                && tutorialStep != TutorialStep.WAITING_FOR_CAST;
        if (tutorialActive) {
            List<Map.Entry<ResourceKey<Quest>, Quest>> tutorialQuests = byCategory.get(QuestCategory.TUTORIAL);
            if (!tutorialQuests.isEmpty()) {
                byCategory.get(QuestCategory.DAILY).addAll(0, tutorialQuests);
            }
        }

        questKeysByCategory.clear();
        for (QuestCategory cat : QUEST_CATEGORY_TAB_INDEX.keySet()) {
            List<ResourceKey<Quest>> keys = new ArrayList<>();
            for (Map.Entry<ResourceKey<Quest>, Quest> entry : byCategory.get(cat)) keys.add(entry.getKey());
            questKeysByCategory.put(cat, keys);
        }

        Label titleLabel = new Label(translated("screen.fishtastic.quest_log.title"), 0xFFFFFFFF).init(tempContext);
        titleLabel.scale(1.3f);
        titleLabel.addBreatheEffect();
        titleLabel.onMouseEnter(e -> titleLabel.setTargetScale(1.5f, true));
        titleLabel.onMouseExit(e -> titleLabel.setTargetScale(1.3f, true));

        tokenBalanceLabel = new Label(translated("screen.fishtastic.quest_log.tokens", QuestClientCache.getTokenBalance()), 0xFFFFAA00).init(tempContext);
        HBox tokenLabel = UI.hbox().spacing(4).alignment(HBox.Alignment.CENTER);
        tokenLabel.addChild(UI.itemRenderer(new ItemStack(FishtasticItems.PILE_OF_COINS.value())));
        tokenLabel.addChild(tokenBalanceLabel);
        tokenLabel.onMouseEnter(e -> tokenLabel.setTargetScale(1.1f, true));
        tokenLabel.onMouseExit(e -> tokenLabel.setTargetScale(1.0f, true));
        tokenIconRow = tokenLabel;

        VBox header = UI.vbox().spacing(4).alignment(VBox.Alignment.CENTER);
        header.addChild(titleLabel);
        header.addChild(tokenLabel);

        ItemTabs tabs = UI.itemTabs();
        tabs.alertIcon(TAB_ALERT_TEXTURE, TAB_ALERT_WIDTH, TAB_ALERT_HEIGHT);
        tabs.addTab(new ItemStack(Items.COD),
                scaleTabPanel(buildQuestList(byCategory.get(QuestCategory.DAILY), true), CONTENT_WIDTH_FRACTION));
        tabs.addTab(new ItemStack(Items.FISHING_ROD),
                scaleTabPanel(buildQuestList(byCategory.get(QuestCategory.MASTERY), false), CONTENT_WIDTH_FRACTION));
        tabs.addTab(new ItemStack(Items.COMPASS),
                scaleTabPanel(buildQuestList(byCategory.get(QuestCategory.EXPLORER), false), CONTENT_WIDTH_FRACTION));
        tabs.addTab(new ItemStack(Items.NETHER_STAR),
                scaleTabPanel(buildQuestList(byCategory.get(QuestCategory.CHALLENGE), false), CONTENT_WIDTH_FRACTION));
        tabs.addTab(new ItemStack(Items.EMERALD),
                scaleTabPanel(buildShopPanel(shopRegistry, currentDay), SHOP_CONTENT_WIDTH_FRACTION));
        tabs.addTab(new ItemStack(FishtasticItems.SEA_GLASS.value()),
                scaleTabPanel(buildCleanupGoalPanel(), CLEANUP_CONTENT_WIDTH_FRACTION));

        questTabs = tabs;
        refreshTabAlerts();

        VBox content = UI.vbox().spacing(10).padding(16).alignment(VBox.Alignment.CENTER);
        content.addChild(header);
        content.addChild(tabs);

        tabs.onSelectionChanged(i -> {
            activeTabIndex = i;
            recenterContent(content);
        });
        tabs.select(activeTabIndex);
        recenterContent(content);

        // Overlay hosting the quest-claim coin-fly animation. Nested inside `content` (rather
        // than rendered as an independent screen-space layer) so it scrolls in lockstep with
        // both the claimed quest row and the token balance display it flies coins between —
        // both of which live in this same scrolling tree.
        coinFlyOverlay = UI.manualContainer().setSize(0, 0);
        coinFlyOverlay.setDebugName("questClaimCoinOverlay");
        content.addChild(coinFlyOverlay);

        uiScreen.setRoot(content);
        uiScreen.setScrollEnabled(true);
    }

    // Each tab body is scaled individually (rather than scaling the shared header+tabs block as a
    // whole) so the title and tab bar stay a constant absolute size no matter which tab — narrow
    // quest lists or the wide, short shop row — is active.
    private static final float CONTENT_WIDTH_FRACTION = 0.7f;
    private static final float SHOP_CONTENT_WIDTH_FRACTION = 0.7f;
    private static final float CLEANUP_CONTENT_WIDTH_FRACTION = 0.7f;

    private VBox scaleTabPanel(VBox panel, float widthFraction) {
        panel.scaleToWidth(this.width * widthFraction);
        return panel;
    }

    private void recenterContent(VBox content) {
        content.forceLayout();
        Vector2f size = content.getSize();
        content.setPosition(new Vector2f((this.width - size.x) / 2f, 0f));
        // UIScreen re-applies its own cached base position over content's every frame (for
        // scrolling); re-registering the root re-syncs that cache to the position we just set,
        // otherwise our new offset gets overwritten back to whatever was current on the last
        // setRoot() call as soon as the next frame's scroll pass runs.
        if (uiScreen != null) {
            uiScreen.setRoot(content);
        }
    }

    private VBox buildQuestList(
            List<Map.Entry<ResourceKey<Quest>, Quest>> quests,
            boolean isDailyTab) {
        VBox list = UI.vbox().spacing(5).padding(4).alignment(VBox.Alignment.CENTER);
        if (isDailyTab) {
            dailyResetLabel = new Label(formatResetCountdown("screen.fishtastic.quest_log.dailies_reset", QuestClientCache.getTicksUntilDailyReset()), 0xFF88CCFF)
                    .init(tempContext);
            list.addChild(dailyResetLabel);
        }
        if (quests.isEmpty()) {
            list.addChild(new Label(translated("screen.fishtastic.quest_log.no_quests"), 0xFF888888).init(tempContext));
        } else {
            for (int i = 0; i < quests.size(); i += QUESTS_PER_ROW) {
                int rowEnd = Math.min(i + QUESTS_PER_ROW, quests.size());

                // Wrapped rather than added directly: `list` has scaleToWidth applied (via
                // scaleTabPanel), which forces its *direct* children to a uniform fit-to-width
                // scale every layout pass. Nesting each quest row one level deeper means that
                // forced uniform scale lands on the wrapper, not the row itself, so the row keeps
                // whatever scale it's individually given (hover zoom, claimed-shrink). The
                // wrapper auto-sizes to the row's live (scaled) size, so a shrunk row also
                // shrinks its reserved slot and later rows reflow up to close the gap — this
                // only stops working if literally every row in the list shrinks at once, since
                // then there's no other full-size row left to anchor the list's fit-to-width
                // math (an acceptable edge case: a tab with every quest already claimed).
                // Deliberately left at the default top-left pivot: VBox's centering math
                // assumes a child's visual box spans [position, position + scaledSize], which
                // only holds for a top-left pivot. A center pivot shifts the rendered content by
                // half the (unscaled) size relative to where the parent thinks it placed the row.
                HBox rowGroup = UI.hbox().spacing(5).alignment(HBox.Alignment.CENTER);
                for (int j = i; j < rowEnd; j++) {
                    var entry = quests.get(j);

                    VBox row = buildQuestRow(entry.getKey(), entry.getValue());
                    VBox rowWrapper = UI.vbox().alignment(VBox.Alignment.CENTER);
                    rowWrapper.addChild(row);
                    rowGroup.addChild(rowWrapper);
                }
                list.addChild(rowGroup);
                if (rowEnd < quests.size()) {
                    list.addChild(UI.rectangle(150f, 1f, 0x33FFFFFF));
                }
            }
        }
        return list;
    }

    private SpriteData questRowBackgroundSprite(boolean claimed) {
        Identifier texture = claimed ? QUEST_ROW_BG_TEXTURE_CLAIMED : QUEST_ROW_BG_TEXTURE;
        return new SpriteData(texture)
                .uv(0, 0, QUEST_ROW_BG_SOURCE_WIDTH, QUEST_ROW_BG_SOURCE_HEIGHT)
                .textureSize(QUEST_ROW_BG_SOURCE_WIDTH, QUEST_ROW_BG_SOURCE_HEIGHT)
                .renderMode(SpriteRenderMode.SLICE)
                .slice(QUEST_ROW_BG_SLICE_LEFT, QUEST_ROW_BG_SLICE_RIGHT, QUEST_ROW_BG_SLICE_TOP, QUEST_ROW_BG_SLICE_BOTTOM);
    }

    private SpriteData questClaimButtonSprite() {
        return new SpriteData(QUEST_CLAIM_BUTTON_TEXTURE)
                .uv(0, 0, QUEST_ROW_BG_SOURCE_WIDTH, QUEST_ROW_BG_SOURCE_HEIGHT)
                .textureSize(QUEST_ROW_BG_SOURCE_WIDTH, QUEST_ROW_BG_SOURCE_HEIGHT)
                .renderMode(SpriteRenderMode.SLICE)
                .slice(QUEST_ROW_BG_SLICE_LEFT, QUEST_ROW_BG_SLICE_RIGHT, QUEST_ROW_BG_SLICE_TOP, QUEST_ROW_BG_SLICE_BOTTOM);
    }

    private static boolean hasEnvironmentCondition(QuestObjective obj) {
        return obj.biomeCondition().isPresent() || obj.timeCondition().isPresent() || obj.weatherCondition().isPresent()
                || obj.zoneCondition().isPresent();
    }

    /**
     * Re-evaluates a quest's biome/time/weather condition(s) against the client player's
     * current world state — mirrors the same condition checks {@link QuestTracker#matchesObjective}
     * applies server-side when a fish is actually caught.
     */
    private static boolean isEnvironmentConditionMet(QuestObjective obj) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;

        if (obj.biomeCondition().isPresent()) {
            Holder<Biome> biome = mc.level.getBiome(mc.player.blockPosition());
            if (!biome.is(obj.biomeCondition().get())) return false;
        }

        if (obj.timeCondition().isPresent()) {
            FishProfile.TimeOfDay timeOfDay = FishProfile.TimeOfDay.fromGameTime(mc.level.getOverworldClockTime());
            if (timeOfDay != obj.timeCondition().get()) return false;
        }

        if (obj.weatherCondition().isPresent()) {
            FishProfile.WeatherCondition weather = FishProfile.WeatherCondition.fromLevel(mc.level, mc.player.blockPosition());
            if (weather != obj.weatherCondition().get()) return false;
        }

        if (obj.zoneCondition().isPresent()) {
            BlockPos pos = mc.player.blockPosition();
            Set<FishProfile.Zone> currentZones = FishProfile.Zone.resolve(mc.level.getBiome(pos), pos.getY(), mc.level.getSeaLevel());
            if (!currentZones.contains(obj.zoneCondition().get())) return false;
        }

        return true;
    }

    /** Builds the pip's hover tooltip text, e.g. "Requires: Night, Thunder". */
    private static String buildConditionTooltipText(QuestObjective obj) {
        List<String> parts = new ArrayList<>();
        obj.biomeCondition().ifPresent(tag -> parts.add(formatConditionWord(tag.location().getPath())));
        obj.timeCondition().ifPresent(t -> parts.add(formatConditionWord(t.getSerializedName())));
        obj.weatherCondition().ifPresent(w -> parts.add(formatConditionWord(w.getSerializedName())));
        obj.zoneCondition().ifPresent(z -> parts.add(formatConditionWord(z.getSerializedName())));
        return translated("screen.fishtastic.quest_log.condition_requires", String.join(", ", parts));
    }

    private static String formatConditionWord(String raw) {
        StringBuilder sb = new StringBuilder();
        for (String word : raw.replace('/', ' ').replace('_', ' ').split(" ")) {
            if (word.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    /** Swaps the pip's sprite between lit/dim and hides it once the quest is claimed. */
    private void updateStatusPip(QuestRowRefs refs, boolean claimed) {
        SpriteRectangle.SpriteRectangleImpl pip = refs.statusPip();
        if (pip == null) return;
        pip.setVisible(!claimed);
        if (!claimed) {
            boolean met = isEnvironmentConditionMet(refs.objective());
            pip.texture(new SpriteData(met ? STATUS_PIP_GREEN_TEXTURE : STATUS_PIP_DEFAULT_TEXTURE));
        }
    }

    private VBox buildQuestRow(ResourceKey<Quest> questKey, Quest quest) {
        Identifier questId = questKey.identifier();
        PlayerQuestState.QuestProgress progress = QuestClientCache.getProgress(questId);

        boolean claimed = progress.claimed();
        boolean completed = progress.completed();
        boolean canClaim = completed && !claimed;

        int targetCount = quest.objective().effectiveTargetCount(Minecraft.getInstance().level.registryAccess());
        int currentCount = progress.currentCount();
        float fraction = targetCount > 0 ? Math.min(1f, (float) currentCount / targetCount) : 0f;

        String baseDisplayName = quest.displayName().isEmpty() ? questId.getPath() : quest.displayName();
        int nameColor = claimed ? 0xFFAAAAAA : 0xFFFFFFFF;
        String nameText = claimed ? translated("screen.fishtastic.quest_log.quest_done", baseDisplayName) : baseDisplayName;

        VBox row = UI.vbox().spacing(3).padding(4).alignment(VBox.Alignment.CENTER);
        row.backgroundSprite(questRowBackgroundSprite(claimed));
        if (claimed) {
            row.setTargetScale(CLAIMED_QUEST_ROW_SCALE, false);
        }

        Label nameLabel = new Label(nameText, nameColor).init(tempContext);

        SpriteButton claimBtn = new SpriteButton(40f, 14f, QUEST_CLAIM_BUTTON_TEXTURE)
                .texture(questClaimButtonSprite())
                .text(translated("screen.fishtastic.quest_log.claim"), 0xFFFFFFFF);
        claimBtn.onMouseEnter(e -> claimBtn.setTargetScale(1.12f, true));
        claimBtn.onMouseExit(e -> claimBtn.setTargetScale(1.0f, true));
        final Identifier fId = questId;
        claimBtn.onClick(e -> {
            claimBtn.addClickBounceEffect();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.connection.send(new ServerboundCustomPayloadPacket(new CompleteQuestPacket(fId)));
            }
            triggerQuestClaimReward(row, quest);
        });
        claimBtn.setVisible(canClaim);

        SpriteRectangle.SpriteRectangleImpl statusPip = null;
        if (hasEnvironmentCondition(quest.objective())) {
            boolean met = isEnvironmentConditionMet(quest.objective());
            statusPip = UI.spriteRectangle(STATUS_PIP_SIZE, STATUS_PIP_SIZE, STATUS_PIP_DEFAULT_TEXTURE)
                    .texture(new SpriteData(met ? STATUS_PIP_GREEN_TEXTURE : STATUS_PIP_DEFAULT_TEXTURE));
            statusPip.setVisible(!claimed);
            SpriteRectangle.SpriteRectangleImpl pipTooltip = UI.spriteRectangle(0, 0, 0xFF002244)
                    .text(buildConditionTooltipText(quest.objective()), 0xFFFFFFFF)
                    .autoSize(true).padding(3, 2).outline(true);
            pipTooltip.scale(STATUS_PIP_TOOLTIP_SCALE);
            statusPip.tooltip(uiScreen, pipTooltip);
        }

        // Pip sits tight against the title (its own low-spacing group); the wider spacing(8)
        // stays between the title group and the claim button.
        HBox titleGroup = UI.hbox().spacing(2).alignment(HBox.Alignment.CENTER);
        if (statusPip != null) titleGroup.addChild(statusPip);
        titleGroup.addChild(nameLabel);

        HBox nameRow = UI.hbox().spacing(8).alignment(HBox.Alignment.CENTER);
        nameRow.addChild(titleGroup);
        nameRow.addChild(claimBtn);

        row.addChild(nameRow);

        if (!quest.description().isEmpty()) {
            row.addChild(new Label(quest.description(), 0xFF888888).maxWidth(150).centered(true).init(tempContext));
        }

        Label countLabel = new Label(translated("screen.fishtastic.quest_log.progress_count", currentCount, targetCount), 0xFFAAAAAA).init(tempContext);
        ThinProgressBar bar = new ThinProgressBar();
        bar.progressImmediate(fraction);

        HBox progressRow = UI.hbox().spacing(6).alignment(HBox.Alignment.CENTER);
        progressRow.addChild(bar);
        progressRow.addChild(countLabel);
        row.addChild(progressRow);

        if (quest.reward().questTokens() > 0 || !quest.reward().items().isEmpty()) {
            row.addChild(buildRewardRow(quest));
        }

        // Each leaf gets its own hover scale (rather than the whole row scaling as a unit)
        // because hover events only ever reach the single deepest leaf element under the cursor
        // (see UIScreen#findElementAt) — a container like this VBox row can never itself be the
        // hit target. claimBtn is excluded since it already drives its own, larger hover scale.
        attachLeafHoverScale(row, QUEST_ROW_LEAF_HOVER_SCALE, Set.of(claimBtn));

        questRowRefs.put(questId, new QuestRowRefs(row, nameLabel, claimBtn, bar, countLabel, targetCount, baseDisplayName, quest.objective(), statusPip));
        return row;
    }

    /**
     * Registers a self-contained hover scale-up on every leaf descendant of {@code element}
     * (skipping containers themselves, since their hover callbacks can never fire, and skipping
     * anything in {@code exclude}). Each leaf scales itself independently rather than the whole
     * subtree scaling as one — e.g. hovering a single reward icon only bumps that icon.
     */
    private static void attachLeafHoverScale(IUIElement element, float hoverScale, Set<IUIElement> exclude) {
        if (element instanceof UIContainer<?> container) {
            for (IUIElement child : container.getChildren()) {
                attachLeafHoverScale(child, hoverScale, exclude);
            }
            return;
        }
        if (exclude.contains(element)) return;
        if (element instanceof UIElement<?> uiElement) {
            uiElement.onMouseEnter(e -> uiElement.setTargetScale(hoverScale, true));
            uiElement.onMouseExit(e -> uiElement.setTargetScale(1.0f, true));
        }
    }

    /**
     * Plays the quest-claim reward feedback: a bunch of mini coins fly from the claimed
     * quest row up to the token balance display while the balance counts up to its new total.
     * Fired optimistically on click, like the shop's purchase-fall feedback, rather than
     * waiting on the server's confirming {@link QuestSyncPacket}.
     */
    private void triggerQuestClaimReward(VBox row, Quest quest) {
        int reward = quest.reward().questTokens();
        if (reward <= 0 || tokenIconRow == null || tokenBalanceLabel == null) return;

        int oldBalance = QuestClientCache.getTokenBalance();
        int newBalance = oldBalance + reward;

        float totalDuration = spawnQuestClaimCoins(row, reward);

        tokenIconRow.addClickBounceEffect();
        tokenBalanceLabel.cancelAnimationChannel("token-balance-count");
        tokenBalanceLabel.playAnimation(new FloatKeyframeAnimation(
                "token-balance-count",
                List.of(new Keyframe(0f, (float) oldBalance), new Keyframe(totalDuration, (float) newBalance)),
                v -> tokenBalanceLabel.text(translated("screen.fishtastic.quest_log.tokens", Math.round(v))),
                () -> tokenIconRow.addClickBounceEffect()));
    }

    /**
     * Spawns a scatter of mini, spinning coin icons that arc from {@code origin}'s on-screen
     * position to the token balance display, each on its own randomized flight — a bit of
     * randomness per coin (start/end scatter, duration, arc height, spin speed) so the burst
     * reads as organic rather than mechanical. Coins are staggered in, one after another,
     * rather than all launching in the same frame.
     *
     * @return the total time (seconds) until the last coin is expected to land, for syncing
     *         the token balance count-up to the actual visual arrival of the coins.
     */
    private float spawnQuestClaimCoins(UIElement<?> origin, int tokenReward) {
        if (coinFlyOverlay == null || tokenIconRow == null) return 0f;

        Rectangle2D originBounds = origin.getBounds();
        Rectangle2D targetBounds = tokenIconRow.getBounds();
        float originCx = (float) (originBounds.getX() + originBounds.getWidth() / 2);
        float originCy = (float) (originBounds.getY() + originBounds.getHeight() / 2);
        float targetCx = (float) (targetBounds.getX() + targetBounds.getWidth() / 2);
        float targetCy = (float) (targetBounds.getY() + targetBounds.getHeight() / 2);

        Vector2f overlayGlobalPos = coinFlyOverlay.getGlobalPosition();
        float overlayScale = Math.max(0.0001f, coinFlyOverlay.getGlobalScale());
        float startX = (originCx - overlayGlobalPos.x) / overlayScale;
        float startY = (originCy - overlayGlobalPos.y) / overlayScale;
        float targetX = (targetCx - overlayGlobalPos.x) / overlayScale;
        float targetY = (targetCy - overlayGlobalPos.y) / overlayScale;

        int coinCount = Math.max(QUEST_CLAIM_MIN_COINS, Math.min(QUEST_CLAIM_MAX_COINS, tokenReward));
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (int i = 0; i < coinCount; i++) {
            float spawnDelay = i * COIN_SPAWN_STAGGER + rng.nextFloat(0f, COIN_SPAWN_STAGGER_JITTER);
            String delayChannel = "coin-spawn-delay-" + (coinSpawnSeq++);
            coinFlyOverlay.playAnimation(new FloatKeyframeAnimation(
                    delayChannel,
                    List.of(new Keyframe(0f, 0f), new Keyframe(Math.max(0.001f, spawnDelay), 1f)),
                    v -> {},
                    () -> spawnSingleCoin(startX, startY, targetX, targetY)));
        }

        return (coinCount - 1) * (COIN_SPAWN_STAGGER + COIN_SPAWN_STAGGER_JITTER) + COIN_FLIGHT_DURATION_MAX;
    }

    /** Spawns and animates a single flying coin; see {@link #spawnQuestClaimCoins}. */
    private void spawnSingleCoin(float startX, float startY, float targetX, float targetY) {
        if (coinFlyOverlay == null) return;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        float sx = startX + rng.nextFloat(-COIN_SCATTER_START, COIN_SCATTER_START);
        float sy = startY + rng.nextFloat(-COIN_SCATTER_START, COIN_SCATTER_START);
        float tx = targetX + rng.nextFloat(-COIN_SCATTER_TARGET, COIN_SCATTER_TARGET);
        float ty = targetY + rng.nextFloat(-COIN_SCATTER_TARGET, COIN_SCATTER_TARGET);
        float duration = rng.nextFloat(COIN_FLIGHT_DURATION_MIN, COIN_FLIGHT_DURATION_MAX);

        ItemRenderer.ItemRendererImpl coin = UI.itemRenderer(new ItemStack(FishtasticItems.QUEST_TOKEN.value()));
        coin.itemScale(rng.nextFloat(COIN_ITEM_SCALE_MIN, COIN_ITEM_SCALE_MAX));
        coin.showCount(false);
        // Center pivot so the coin-spin's horizontal squash (a real 3D Y-axis flip isn't
        // renderable in 2D GUI space) narrows symmetrically around the coin's middle
        // instead of its top-left corner.
        coin.scaleFromCenter();

        coinFlyOverlay.addChildAt(coin, sx, sy);

        coin.addEffect(new CoinArcEffect("coin-arc", 0, duration)
                .setDisplacement(tx - sx, ty - sy)
                .setArcHeight(rng.nextFloat(COIN_ARC_HEIGHT_MIN, COIN_ARC_HEIGHT_MAX))
                .setWobble(rng.nextFloat(3f, 8f), rng.nextFloat(1.5f, 3f), rng.nextFloat(0f, (float) (Math.PI * 2)))
                .setShrink(0.7f, 0.15f));
        // Duration matches the coin's flight exactly, so the spin completes its whole
        // rotation count over the same span without needing to loop (which would snap
        // back to 0° mid-flight unless rotationSpeed happened to be a multiple of 360).
        coin.addEffect(new CoinSpinEffect("coin-spin", 1, duration)
                .setRotationSpeed(rng.nextFloat(COIN_SPIN_SPEED_MIN, COIN_SPIN_SPEED_MAX)));

        coin.playAnimation(new FloatKeyframeAnimation(
                "coin-lifetime",
                List.of(new Keyframe(0f, 0f), new Keyframe(duration, 1f)),
                v -> {},
                () -> coinFlyOverlay.removeChild(coin)));
    }

    private HBox buildRewardRow(Quest quest) {
        HBox row = UI.hbox().spacing(6).alignment(HBox.Alignment.CENTER);
        if (quest.reward().questTokens() > 0) {
            row.addChild(UI.itemRenderer(new ItemStack(FishtasticItems.PILE_OF_COINS.value())));
            row.addChild(new Label(translated("screen.fishtastic.quest_log.tokens", quest.reward().questTokens()), 0xFFFFAA00).init(tempContext));
        }
        if (!quest.reward().items().isEmpty()) {
            if (quest.reward().questTokens() > 0) {
                row.addChild(new Label("+", 0xFFAAAAAA).init(tempContext));
            }
            for (QuestReward.RewardItem rewardItem : quest.reward().items()) {
                row.addChild(UI.itemRenderer(rewardItem.toStack()));
            }
        }
        return row;
    }

    // Wide enough that, once scaled up to match the other tabs' width, the panel's natural
    // (unscaled) aspect ratio stays short enough to fit the screen's height without scrolling —
    // narrower content (like the old 160px description wrap) forces a much larger scale factor
    // to reach the same target width, which blows the height out past the viewport.
    private static final float CLEANUP_GOAL_DESCRIPTION_MAX_WIDTH = 280f;

    private VBox buildCleanupGoalPanel() {
        VBox panel = UI.vbox().spacing(6).padding(4).alignment(VBox.Alignment.CENTER);

        Label title = new Label(translated("screen.fishtastic.quest_log.cleanup.title"), 0xFFFFFFFF).init(tempContext);
        title.scale(1.1f);
        panel.addChild(title);

        // Combined into a single label (rather than two separate paragraphs) to cut a whole
        // block's worth of height off the panel's natural size.
        panel.addChild(new Label(translated("screen.fishtastic.quest_log.cleanup.description"), 0xFF888888)
                .maxWidth(CLEANUP_GOAL_DESCRIPTION_MAX_WIDTH).centered(true).init(tempContext));

        int total = QuestClientCache.getCleanupGoalTotal();
        int threshold = Math.max(1, QuestClientCache.getCleanupGoalThreshold());
        int intoCurrentTier = total % threshold;
        float fraction = (float) intoCurrentTier / threshold;

        cleanupGoalBar = UI.progressBar();
        cleanupGoalBar.progressImmediate(fraction);
        cleanupGoalCountLabel = new Label(translated("screen.fishtastic.quest_log.progress_count", intoCurrentTier, threshold), 0xFFAAAAAA).init(tempContext);

        HBox progressRow = UI.hbox().spacing(6).alignment(HBox.Alignment.CENTER);
        progressRow.addChild(cleanupGoalBar);
        progressRow.addChild(cleanupGoalCountLabel);
        panel.addChild(progressRow);

        cleanupGoalTotalLabel = new Label(translated("screen.fishtastic.quest_log.cleanup.total", total), 0xFFFFAA00).init(tempContext);
        panel.addChild(cleanupGoalTotalLabel);

        cleanupGoalResetLabel = new Label(formatResetCountdown("screen.fishtastic.quest_log.cleanup.resets", QuestClientCache.getTicksUntilCleanupGoalReset()), 0xFF88CCFF)
                .init(tempContext);
        panel.addChild(cleanupGoalResetLabel);

        return panel;
    }

    private VBox buildShopPanel(Registry<ShopEntry> shopRegistry, long currentDay) {
        VBox panel = UI.vbox().spacing(8).padding(4).alignment(VBox.Alignment.CENTER);

        if (shopRegistry == null || shopRegistry.entrySet().isEmpty()) {
            panel.addChild(new Label(translated("screen.fishtastic.quest_log.shop.no_entries"), 0xFF888888).init(tempContext));
            return panel;
        }

        Set<ResourceKey<ShopEntry>> activeKeys = ShopEntry.getActiveDailyShop(shopRegistry, currentDay, QuestClientCache.getShopRefreshCount());

        List<ResourceKey<ShopEntry>> activeList = new ArrayList<>(activeKeys);

        Label shopTitle = new Label(translated("screen.fishtastic.quest_log.shop.title"), 0xFFFFFFFF).init(tempContext);
        shopTitle.scale(1.1f);
        panel.addChild(shopTitle);

        shopResetLabel = new Label(formatResetCountdown("screen.fishtastic.quest_log.shop.resets", QuestClientCache.getTicksUntilDailyReset()), 0xFF88CCFF)
                .init(tempContext);
        panel.addChild(shopResetLabel);

        panel.addChild(buildShopRefreshRow());

        HBox row = UI.hbox().spacing(8).alignment(HBox.Alignment.TOP);
        for (ResourceKey<ShopEntry> key : activeList) {
            ShopEntry entry = shopRegistry.getOptional(key).orElse(null);
            if (entry != null) row.addChild(buildShopEntryCard(key, entry));
        }
        panel.addChild(row);

        return panel;
    }

    /** Header row letting the player spend a fixed token cost to reroll today's active shop entries. */
    private HBox buildShopRefreshRow() {
        boolean canAfford = QuestClientCache.getTokenBalance() >= ShopEntry.SHOP_REFRESH_COST;

        HBox refreshRow = UI.hbox().spacing(4).alignment(HBox.Alignment.CENTER);

        SpriteData refreshButtonSprite = new SpriteData(SHOP_BUY_BUTTON_TEXTURE)
                .uv(0, 0, SHOP_BUY_BUTTON_SOURCE_WIDTH, SHOP_BUY_BUTTON_SOURCE_HEIGHT)
                .textureSize(SHOP_BUY_BUTTON_FILE_WIDTH, SHOP_BUY_BUTTON_FILE_HEIGHT);
        SpriteButton refreshBtn = new SpriteButton(SHOP_BUY_BUTTON_WIDTH, SHOP_BUY_BUTTON_HEIGHT, SHOP_BUY_BUTTON_TEXTURE)
                .texture(refreshButtonSprite)
                .text(translated("screen.fishtastic.quest_log.shop.refresh"), 0xFFFFFFFF)
                .scaleFromCenter();
        refreshBtn.onMouseEnter(e -> refreshBtn.setTargetScale(1.12f, true));
        refreshBtn.onMouseExit(e -> refreshBtn.setTargetScale(1.0f, true));
        refreshBtn.onClick(e -> {
            refreshBtn.addClickBounceEffect();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.connection.send(new ServerboundCustomPayloadPacket(new RefreshShopPacket()));
            }
        });
        refreshBtn.setVisible(canAfford);
        shopRefreshBtn = refreshBtn;

        Label costLabel = new Label(String.valueOf(ShopEntry.SHOP_REFRESH_COST), canAfford ? 0xFFFFAA00 : 0xFFFF4444).init(tempContext);
        shopRefreshCostLabel = costLabel;

        Label notEnoughLabel = new Label(translated("screen.fishtastic.quest_log.shop.not_enough_tokens"), 0xFFFF4444).init(tempContext);
        notEnoughLabel.setVisible(!canAfford);
        shopRefreshNotEnoughLabel = notEnoughLabel;

        refreshRow.addChild(refreshBtn);
        refreshRow.addChild(UI.itemRenderer(new ItemStack(FishtasticItems.PILE_OF_COINS.value())));
        refreshRow.addChild(costLabel);
        refreshRow.addChild(notEnoughLabel);

        return refreshRow;
    }

    private VBox buildShopEntryCard(ResourceKey<ShopEntry> key, ShopEntry entry) {
        boolean soldOut = isEntrySoldOut(key, entry);
        boolean canAfford = QuestClientCache.getTokenBalance() >= entry.cost();

        VBox card = UI.vbox().padding(2).alignment(VBox.Alignment.CENTER);

        // Inner content box
        VBox inner = UI.vbox().spacing(3).alignment(VBox.Alignment.CENTER);

        // Item slot: the item's panel pinned to a static pin backdrop. The panel (background +
        // item icon) is what falls away on purchase; the pin is drawn on top of it (like a pin
        // head poking through the tag it's holding), so it's added last/rendered in front.
        ManualContainer slot = UI.manualContainer().setSize(SHOP_ITEM_PANEL_WIDTH, SHOP_ITEM_PANEL_HEIGHT);
        slot.setDebugName("shopSlot:" + key.identifier());

        ManualContainer fallingPanel = UI.manualContainer().setSize(SHOP_ITEM_PANEL_WIDTH, SHOP_ITEM_PANEL_HEIGHT);
        fallingPanel.setDebugName("shopFallingPanel:" + key.identifier());
        // Scale and rotation both pivot from the top-center — where the pin actually is —
        // so the respawn swing reads as the panel hanging and settling on the pin.
        fallingPanel.setPivotMode(PivotMode.TOP_CENTER);
        SpriteData panelSprite = new SpriteData(SHOP_ITEM_PANEL_TEXTURE)
                .uv(0, 0, SHOP_ITEM_PANEL_SOURCE_WIDTH, SHOP_ITEM_PANEL_SOURCE_HEIGHT)
                .textureSize(SHOP_ITEM_PANEL_SOURCE_WIDTH, SHOP_ITEM_PANEL_SOURCE_HEIGHT);
        fallingPanel.backgroundSprite(panelSprite);
        if (!entry.reward().isEmpty()) {
            ItemStack icon = entry.reward().get(0).toItemStack();
            if (!icon.isEmpty()) fallingPanel.addChildAt(UI.itemRenderer(icon).itemScale(SHOP_ITEM_PANEL_SCALE), SHOP_ITEM_ICON_CENTER.x, SHOP_ITEM_ICON_CENTER.y);
        }
        // ManualContainer positions its children lazily via the dirty-flag system, but VBox/HBox
        // ancestors only eagerly force-layout VBox/HBox descendants before measuring themselves —
        // a plain ManualContainer like this one can end up rendered (and viewport-culled against
        // stale/default bounds) before it's ever had a layout pass, which is why entries would
        // randomly show no icon/background until something else (e.g. the purchase animation)
        // happened to mark them dirty again. Force it here so positions are correct from frame one.
        fallingPanel.forceLayout();
        // Already sold out when this card is (re)built (e.g. reopening the shop) - show it gone
        // immediately with no animation; the fall animation itself only plays on the live purchase.
        if (soldOut) fallingPanel.setVisible(false);
        slot.addChildAt(fallingPanel, SHOP_ITEM_PANEL_WIDTH / 2f, SHOP_ITEM_PANEL_HEIGHT / 2f);

        SpriteData pinSprite = new SpriteData(SHOP_ITEM_PANEL_PIN_TEXTURE)
                .uv(0, 0, SHOP_ITEM_PANEL_SOURCE_WIDTH, SHOP_ITEM_PANEL_SOURCE_HEIGHT)
                .textureSize(SHOP_ITEM_PANEL_SOURCE_WIDTH, SHOP_ITEM_PANEL_SOURCE_HEIGHT);
        SpriteRectangle.SpriteRectangleImpl pinLayer = UI.spriteRectangle(SHOP_ITEM_PANEL_WIDTH, SHOP_ITEM_PANEL_HEIGHT, SHOP_ITEM_PANEL_PIN_TEXTURE)
                .texture(pinSprite);
        slot.addChildAt(pinLayer, SHOP_ITEM_PANEL_WIDTH / 2f, SHOP_ITEM_PANEL_HEIGHT / 2f);
        slot.forceLayout();

        inner.addChild(slot);

        int nameColor = soldOut ? 0xFF555555 : 0xFFFFFFFF;
        String nameText = entry.displayName().isEmpty() ? key.identifier().getPath() : entry.displayName();
        Label nameLabel = new Label(nameText, nameColor).maxWidth(SHOP_ITEM_NAME_MAX_WIDTH).init(tempContext);
        // Name and description are no longer relevant once sold out - toggled by updateShopCardVisuals
        nameLabel.setVisible(!soldOut);

        // Sold out label — toggled by updateShopCardVisuals
        Label soldOutLabel = new Label(translated("screen.fishtastic.quest_log.shop.sold_out"), 0xFF555555).init(tempContext);
        soldOutLabel.setVisible(soldOut);

        // Cost row — hidden when sold out
        int costColor = soldOut ? 0xFF555555 : (canAfford ? 0xFFFFAA00 : 0xFFFF4444);
        Label costLabel = new Label(String.valueOf(entry.cost()), costColor).init(tempContext);
        HBox costRow = UI.hbox().spacing(2).alignment(HBox.Alignment.CENTER);
        costRow.addChild(UI.itemRenderer(new ItemStack(FishtasticItems.PILE_OF_COINS.value())));
        costRow.addChild(costLabel);
        costRow.setVisible(!soldOut);

        // Name and price sit in-line; sold-out entries show the stamp label instead of the price
        HBox nameRow = UI.hbox().spacing(4).alignment(HBox.Alignment.CENTER);
        nameRow.addChild(nameLabel);
        nameRow.addChild(costRow);
        nameRow.addChild(soldOutLabel);
        inner.addChild(nameRow);

        // Description — hidden once sold out, since it's no longer relevant
        Label descriptionLabel = null;
        if (!entry.description().isEmpty()) {
            descriptionLabel = new Label(entry.description(), 0xFFCCCCCC).maxWidth(SHOP_ITEM_DESCRIPTION_MAX_WIDTH).centered(true).init(tempContext);
            descriptionLabel.setVisible(!soldOut);
            inner.addChild(descriptionLabel);
        }

        // Buy button — always visible
        SpriteData buyButtonSprite = new SpriteData(SHOP_BUY_BUTTON_TEXTURE)
                .uv(0, 0, SHOP_BUY_BUTTON_SOURCE_WIDTH, SHOP_BUY_BUTTON_SOURCE_HEIGHT)
                .textureSize(SHOP_BUY_BUTTON_FILE_WIDTH, SHOP_BUY_BUTTON_FILE_HEIGHT);
        SpriteButton buyBtn = new SpriteButton(SHOP_BUY_BUTTON_WIDTH, SHOP_BUY_BUTTON_HEIGHT, SHOP_BUY_BUTTON_TEXTURE)
                .texture(buyButtonSprite)
                .text(translated("screen.fishtastic.quest_log.shop.buy"), 0xFFFFFFFF)
                .scaleFromCenter();
        buyBtn.onMouseEnter(e -> buyBtn.setTargetScale(1.12f, true));
        buyBtn.onMouseExit(e -> buyBtn.setTargetScale(1.0f, true));
        final ResourceKey<ShopEntry> fKey = key;
        buyBtn.onClick(e -> {
            buyBtn.addClickBounceEffect();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.connection.send(new ServerboundCustomPayloadPacket(new PurchaseShopEntryPacket(fKey.identifier())));
            }
            triggerPurchaseFall(fallingPanel, fKey, entry);
        });
        buyBtn.setVisible(canAfford && !soldOut);
        inner.addChild(buyBtn);

        Label notEnoughLabel = new Label(translated("screen.fishtastic.quest_log.shop.not_enough_tokens"), 0xFFFF4444).init(tempContext);
        notEnoughLabel.setVisible(!canAfford && !soldOut);
        inner.addChild(notEnoughLabel);

        card.addChild(inner);

        card.onMouseEnter(e -> {
            if (!isEntrySoldOut(key, entry)) card.setTargetScale(1.06f, true);
        });
        card.onMouseExit(e -> card.setTargetScale(1.0f, true));

        shopCardRefs.put(key, new ShopCardRefs(card, fallingPanel, nameLabel, costRow, costLabel, soldOutLabel, buyBtn, notEnoughLabel, descriptionLabel, entry, key));
        return card;
    }

    private boolean isEntrySoldOut(ResourceKey<ShopEntry> key, ShopEntry entry) {
        int purchaseCount = QuestClientCache.getPurchaseCount(key.identifier());
        return entry.dailyMaxPurchases() > 0 && purchaseCount >= entry.dailyMaxPurchases();
    }

    /**
     * Plays the purchase feedback: the item's panel falls away from its pin with
     * accelerating (gravity-like) motion, then — if the entry is still available —
     * pops back into place a moment later.
     */
    private void triggerPurchaseFall(ManualContainer fallingPanel, ResourceKey<ShopEntry> key, ShopEntry entry) {
        fallingPanel.cancelAnimationChannel("shopItemFall");
        fallingPanel.cancelAnimationChannel("shopItemRespawn");
        fallingPanel.addEffectExclusive(new DropOffEffect("shopItemDrop", 0, SHOP_ITEM_FALL_DURATION)
                .setDropDistance(SHOP_ITEM_FALL_DISTANCE)
                .setRotation(SHOP_ITEM_FALL_ROTATION));
        fallingPanel.playAnimation(new FloatKeyframeAnimation(
                "shopItemFall",
                List.of(new Keyframe(0f, 0f), new Keyframe(SHOP_ITEM_FALL_DURATION, 1f)),
                v -> {},
                () -> {
                    fallingPanel.setVisible(false);
                    fallingPanel.clearEffects();
                    fallingPanel.playAnimation(new FloatKeyframeAnimation(
                            "shopItemRespawn",
                            List.of(new Keyframe(0f, 0f), new Keyframe(SHOP_ITEM_RESPAWN_DELAY, 1f)),
                            v -> {},
                            () -> {
                                if (!isEntrySoldOut(key, entry)) {
                                    fallingPanel.setVisible(true);
                                    fallingPanel.addEffectExclusive(new PendulumSwingEffect("shopItemSwing", 0, SHOP_ITEM_SWING_DURATION)
                                            .setStartAngle(SHOP_ITEM_SWING_START_ANGLE)
                                            .setFrequency(SHOP_ITEM_SWING_FREQUENCY)
                                            .setDamping(SHOP_ITEM_SWING_DAMPING));
                                }
                            }));
                }));
    }

    private void updateShopCardVisuals() {
        for (ShopCardRefs refs : shopCardRefs.values()) {
            boolean soldOut = isEntrySoldOut(refs.key(), refs.entry());
            boolean canAfford = QuestClientCache.getTokenBalance() >= refs.entry().cost();

            // Deliberately not touching fallingPanel visibility here: the only way an entry becomes
            // sold out is the local player's own purchase, which already drives the fall animation
            // via triggerPurchaseFall. Forcing it invisible here would race that animation and cut
            // it short, since this runs on every sync (including the one right after a purchase).

            int nameColor = soldOut ? 0xFF555555 : 0xFFFFFFFF;
            refs.nameLabel().color(nameColor);
            // Name and description are irrelevant once sold out
            refs.nameLabel().setVisible(!soldOut);
            if (refs.descriptionLabel() != null) refs.descriptionLabel().setVisible(!soldOut);
            refs.soldOutLabel().setVisible(soldOut);
            refs.costRow().setVisible(!soldOut);
            refs.costLabel().color(canAfford ? 0xFFFFAA00 : 0xFFFF4444);
            refs.buyBtn().setVisible(canAfford && !soldOut);
            refs.notEnoughLabel().setVisible(!canAfford && !soldOut);
        }

        if (shopRefreshBtn != null) {
            boolean canAffordRefresh = QuestClientCache.getTokenBalance() >= ShopEntry.SHOP_REFRESH_COST;
            shopRefreshBtn.setVisible(canAffordRefresh);
            shopRefreshCostLabel.color(canAffordRefresh ? 0xFFFFAA00 : 0xFFFF4444);
            shopRefreshNotEnoughLabel.setVisible(!canAffordRefresh);
        }
    }

    private void refreshTabAlerts() {
        if (questTabs == null) return;
        for (Map.Entry<QuestCategory, Integer> e : QUEST_CATEGORY_TAB_INDEX.entrySet()) {
            List<ResourceKey<Quest>> keys = questKeysByCategory.getOrDefault(e.getKey(), List.of());
            boolean hasUnclaimed = keys.stream().anyMatch(key -> {
                PlayerQuestState.QuestProgress progress = QuestClientCache.getProgress(key.identifier());
                return progress.completed() && !progress.claimed();
            });
            questTabs.setTabAlert(e.getValue(), hasUnclaimed);
        }
    }

    private void updateInPlace() {
        if (tokenBalanceLabel != null) {
            tokenBalanceLabel.text(translated("screen.fishtastic.quest_log.tokens", QuestClientCache.getTokenBalance()));
        }

        if (cleanupGoalTotalLabel != null) {
            int total = QuestClientCache.getCleanupGoalTotal();
            int threshold = Math.max(1, QuestClientCache.getCleanupGoalThreshold());
            int intoCurrentTier = total % threshold;
            float fraction = (float) intoCurrentTier / threshold;
            cleanupGoalTotalLabel.text(translated("screen.fishtastic.quest_log.cleanup.total", total));
            cleanupGoalCountLabel.text(translated("screen.fishtastic.quest_log.progress_count", intoCurrentTier, threshold));
            cleanupGoalBar.progressImmediate(fraction);
        }

        for (Map.Entry<Identifier, QuestRowRefs> e : questRowRefs.entrySet()) {
            Identifier questId = e.getKey();
            QuestRowRefs refs = e.getValue();

            PlayerQuestState.QuestProgress progress = QuestClientCache.getProgress(questId);
            boolean claimed = progress.claimed();
            boolean completed = progress.completed();
            boolean canClaim = completed && !claimed;

            int currentCount = progress.currentCount();
            float fraction = refs.targetCount() > 0 ? Math.min(1f, (float) currentCount / refs.targetCount()) : 0f;

            int nameColor = claimed ? 0xFFAAAAAA : 0xFFFFFFFF;
            String nameText = claimed ? translated("screen.fishtastic.quest_log.quest_done", refs.baseDisplayName()) : refs.baseDisplayName();

            refs.nameLabel().text(nameText).color(nameColor);
            refs.claimButton().setVisible(canClaim);
            refs.progressBar().progressImmediate(fraction);
            refs.countLabel().text(translated("screen.fishtastic.quest_log.progress_count", currentCount, refs.targetCount()));
            refs.row().backgroundSprite(questRowBackgroundSprite(claimed));
            refs.row().setTargetScale(claimed ? CLAIMED_QUEST_ROW_SCALE : 1.0f, true);
            updateStatusPip(refs, claimed);
        }

        updateShopCardVisuals();
        refreshTabAlerts();
    }

    @Override
    public void removed() {
        super.removed();
        if (handlerInstalled) {
            QuestSyncPacket.registerClientHandler(savedHandler);
        }
    }

    private static String translated(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
