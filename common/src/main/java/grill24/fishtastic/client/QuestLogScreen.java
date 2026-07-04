package grill24.fishtastic.client;

import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.client.effects.CoinArcEffect;
import grill24.fishtastic.client.effects.DropOffEffect;
import grill24.fishtastic.client.effects.PendulumSwingEffect;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.QuestCategory;
import grill24.fishtastic.data.ShopEntry;
import grill24.fishtastic.tutorial.TutorialStep;
import grill24.fishtastic.network.CompleteQuestPacket;
import grill24.fishtastic.network.PurchaseShopEntryPacket;
import grill24.fishtastic.network.QuestSyncPacket;
import grill24.fishtastic.server.PlayerQuestState;
import grill24.fishtastic.server.QuestTracker;
import io.github.currenj.gelatinui.GelatinUIScreen;
import io.github.currenj.gelatinui.gui.PivotMode;
import io.github.currenj.gelatinui.gui.UI;
import io.github.currenj.gelatinui.gui.UIElement;
import io.github.currenj.gelatinui.gui.GelatinMenu;
import io.github.currenj.gelatinui.gui.components.*;
import io.github.currenj.gelatinui.gui.effects.CoinSpinEffect;
import io.github.currenj.gelatinui.gui.minecraft.MinecraftRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
            boolean isDailyTab,
            boolean activeToday
    ) {}

    // Target scale a quest row settles at once its reward has been claimed.
    private static final float CLAIMED_QUEST_ROW_SCALE = 1f;

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
        super(menu, inv, Component.literal("Quest Log"));
    }

    @Override
    protected void init() {
        if (!handlerInstalled) {
            savedHandler = QuestSyncPacket.clientHandler;
            handlerInstalled = true;
            QuestSyncPacket.registerClientHandler(packet -> {
                QuestClientCache.update(packet.questProgress(), packet.tokenBalance(), packet.triggeringItems(),
                        packet.purchaseCounts(), packet.cleanupGoal());
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == this) {
                    updateInPlace();
                }
            });
        }
        super.init();
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

        tempContext = new MinecraftRenderContext(null, this.font);

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Registry<Quest> questRegistry;
        try {
            questRegistry = mc.level.registryAccess().lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
        } catch (Exception e) {
            Label err = new Label("Quest registry unavailable.", 0xFFFF4444).init(tempContext);
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

        Label titleLabel = new Label("Quest Log", 0xFFFFFFFF).init(tempContext);
        titleLabel.scale(1.3f);
        titleLabel.addBreatheEffect();
        titleLabel.onMouseEnter(e -> titleLabel.setTargetScale(1.5f, true));
        titleLabel.onMouseExit(e -> titleLabel.setTargetScale(1.3f, true));

        tokenBalanceLabel = new Label(QuestClientCache.getTokenBalance() + " tokens", 0xFFFFAA00).init(tempContext);
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
                scaleTabPanel(buildQuestList(byCategory.get(QuestCategory.DAILY), activeDailies, true), CONTENT_WIDTH_FRACTION));
        tabs.addTab(new ItemStack(Items.FISHING_ROD),
                scaleTabPanel(buildQuestList(byCategory.get(QuestCategory.MASTERY), null, false), CONTENT_WIDTH_FRACTION));
        tabs.addTab(new ItemStack(Items.COMPASS),
                scaleTabPanel(buildQuestList(byCategory.get(QuestCategory.EXPLORER), null, false), CONTENT_WIDTH_FRACTION));
        tabs.addTab(new ItemStack(Items.NETHER_STAR),
                scaleTabPanel(buildQuestList(byCategory.get(QuestCategory.CHALLENGE), null, false), CONTENT_WIDTH_FRACTION));
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
    private static final float CLEANUP_CONTENT_WIDTH_FRACTION = 0.5f;

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
            Set<ResourceKey<Quest>> activeDailies,
            boolean isDailyTab) {
        VBox list = UI.vbox().spacing(5).padding(4).alignment(VBox.Alignment.CENTER);
        if (quests.isEmpty()) {
            list.addChild(new Label("No quests available.", 0xFF888888).init(tempContext));
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
                    boolean active = !isDailyTab || activeDailies.contains(entry.getKey())
                            || entry.getValue().category() == QuestCategory.TUTORIAL;

                    VBox row = buildQuestRow(entry.getKey(), entry.getValue(), isDailyTab, active);
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

    private VBox buildQuestRow(ResourceKey<Quest> questKey, Quest quest, boolean isDailyTab, boolean activeToday) {
        Identifier questId = questKey.identifier();
        PlayerQuestState.QuestProgress progress = QuestClientCache.getProgress(questId);

        boolean claimed = progress.claimed();
        boolean completed = progress.completed();
        boolean canClaim = completed && !claimed;
        boolean inactive = isDailyTab && !activeToday;

        int targetCount = quest.objective().targetCount();
        int currentCount = progress.currentCount();
        float fraction = targetCount > 0 ? Math.min(1f, (float) currentCount / targetCount) : 0f;

        String baseDisplayName = quest.displayName().isEmpty() ? questId.getPath() : quest.displayName();
        int nameColor = (claimed || inactive) ? 0xFF666666 : 0xFFFFFFFF;
        String nameText = claimed ? "[Done] " + baseDisplayName : inactive ? "(inactive) " + baseDisplayName : baseDisplayName;

        VBox row = UI.vbox().spacing(3).padding(4).alignment(VBox.Alignment.CENTER);
        row.backgroundSprite(questRowBackgroundSprite(claimed));
        final Identifier hoverQuestId = questId;
        row.onMouseEnter(e -> {
            float base = QuestClientCache.getProgress(hoverQuestId).claimed() ? CLAIMED_QUEST_ROW_SCALE : 1.0f;
            row.setTargetScale(base * 1.04f, true);
        });
        row.onMouseExit(e -> {
            float base = QuestClientCache.getProgress(hoverQuestId).claimed() ? CLAIMED_QUEST_ROW_SCALE : 1.0f;
            row.setTargetScale(base, true);
        });
        if (claimed) {
            row.setTargetScale(CLAIMED_QUEST_ROW_SCALE, false);
        }

        Label nameLabel = new Label(nameText, nameColor).init(tempContext);

        SpriteButton claimBtn = new SpriteButton(40f, 14f, QUEST_CLAIM_BUTTON_TEXTURE)
                .texture(questClaimButtonSprite())
                .text("Claim", 0xFFFFFFFF);
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

        HBox nameRow = UI.hbox().spacing(8).alignment(HBox.Alignment.CENTER);
        nameRow.addChild(nameLabel);
        nameRow.addChild(claimBtn);

        row.addChild(nameRow);

        if (!quest.description().isEmpty()) {
            row.addChild(new Label(quest.description(), 0xFF888888).maxWidth(150).centered(true).init(tempContext));
        }

        Label countLabel = new Label(currentCount + " / " + targetCount, 0xFFAAAAAA).init(tempContext);
        ThinProgressBar bar = new ThinProgressBar();
        bar.progressImmediate(fraction);

        HBox progressRow = UI.hbox().spacing(6).alignment(HBox.Alignment.CENTER);
        progressRow.addChild(bar);
        progressRow.addChild(countLabel);
        row.addChild(progressRow);

        if (quest.reward().questTokens() > 0 || !quest.reward().items().isEmpty()) {
            row.addChild(buildRewardRow(quest));
        }

        questRowRefs.put(questId, new QuestRowRefs(row, nameLabel, claimBtn, bar, countLabel, targetCount, baseDisplayName, isDailyTab, activeToday));
        return row;
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
                v -> tokenBalanceLabel.text(Math.round(v) + " tokens"),
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
            row.addChild(new Label(quest.reward().questTokens() + " tokens", 0xFFFFAA00).init(tempContext));
        }
        if (!quest.reward().items().isEmpty()) {
            if (quest.reward().questTokens() > 0) {
                row.addChild(new Label("+", 0xFFAAAAAA).init(tempContext));
            }
            row.addChild(new Label(quest.reward().items().size() + " item(s)", 0xFFFFAA00).init(tempContext));
        }
        return row;
    }

    private VBox buildCleanupGoalPanel() {
        VBox panel = UI.vbox().spacing(8).padding(4).alignment(VBox.Alignment.CENTER);

        Label title = new Label("Clean Up the Waters", 0xFFFFFFFF).init(tempContext);
        title.scale(1.1f);
        panel.addChild(title);

        panel.addChild(new Label("A shared, server-wide goal — every trash catch counts toward it, by anyone.", 0xFF888888)
                .maxWidth(160).centered(true).init(tempContext));

        int total = QuestClientCache.getCleanupGoalTotal();
        int threshold = Math.max(1, QuestClientCache.getCleanupGoalThreshold());
        int intoCurrentTier = total % threshold;
        float fraction = (float) intoCurrentTier / threshold;

        cleanupGoalBar = UI.progressBar();
        cleanupGoalBar.progressImmediate(fraction);
        cleanupGoalCountLabel = new Label(intoCurrentTier + " / " + threshold, 0xFFAAAAAA).init(tempContext);

        HBox progressRow = UI.hbox().spacing(6).alignment(HBox.Alignment.CENTER);
        progressRow.addChild(cleanupGoalBar);
        progressRow.addChild(cleanupGoalCountLabel);
        panel.addChild(progressRow);

        cleanupGoalTotalLabel = new Label("Total cleaned so far: " + total, 0xFFFFAA00).init(tempContext);
        panel.addChild(cleanupGoalTotalLabel);

        panel.addChild(new Label("Tokens are split between every contributor each time a milestone is reached.", 0xFF888888)
                .maxWidth(160).centered(true).init(tempContext));

        return panel;
    }

    private VBox buildShopPanel(Registry<ShopEntry> shopRegistry, long currentDay) {
        VBox panel = UI.vbox().spacing(8).padding(4).alignment(VBox.Alignment.CENTER);

        if (shopRegistry == null || shopRegistry.entrySet().isEmpty()) {
            panel.addChild(new Label("No shop entries available.", 0xFF888888).init(tempContext));
            return panel;
        }

        Set<ResourceKey<ShopEntry>> activeKeys = ShopEntry.getActiveDailyShop(shopRegistry, currentDay);

        List<ResourceKey<ShopEntry>> activeList = new ArrayList<>(activeKeys);

        Label shopTitle = new Label("Today's Stock", 0xFFFFFFFF).init(tempContext);
        shopTitle.scale(1.1f);
        panel.addChild(shopTitle);

        HBox row = UI.hbox().spacing(8).alignment(HBox.Alignment.TOP);
        for (ResourceKey<ShopEntry> key : activeList) {
            ShopEntry entry = shopRegistry.getOptional(key).orElse(null);
            if (entry != null) row.addChild(buildShopEntryCard(key, entry));
        }
        panel.addChild(row);

        return panel;
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

        // Sold out label — toggled by updateShopCardVisuals
        Label soldOutLabel = new Label("Sold Out", 0xFF555555).init(tempContext);
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

        // Description — always visible
        if (!entry.description().isEmpty()) {
            inner.addChild(new Label(entry.description(), 0xFFCCCCCC).maxWidth(SHOP_ITEM_DESCRIPTION_MAX_WIDTH).centered(true).init(tempContext));
        }

        // Buy button — always visible
        SpriteData buyButtonSprite = new SpriteData(SHOP_BUY_BUTTON_TEXTURE)
                .uv(0, 0, SHOP_BUY_BUTTON_SOURCE_WIDTH, SHOP_BUY_BUTTON_SOURCE_HEIGHT)
                .textureSize(SHOP_BUY_BUTTON_FILE_WIDTH, SHOP_BUY_BUTTON_FILE_HEIGHT);
        SpriteButton buyBtn = new SpriteButton(SHOP_BUY_BUTTON_WIDTH, SHOP_BUY_BUTTON_HEIGHT, SHOP_BUY_BUTTON_TEXTURE)
                .texture(buyButtonSprite)
                .text("Buy", 0xFFFFFFFF)
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

        Label notEnoughLabel = new Label("Not enough tokens", 0xFFFF4444).init(tempContext);
        notEnoughLabel.setVisible(!canAfford && !soldOut);
        inner.addChild(notEnoughLabel);

        card.addChild(inner);

        card.onMouseEnter(e -> {
            if (!isEntrySoldOut(key, entry)) card.setTargetScale(1.06f, true);
        });
        card.onMouseExit(e -> card.setTargetScale(1.0f, true));

        shopCardRefs.put(key, new ShopCardRefs(card, fallingPanel, nameLabel, costRow, costLabel, soldOutLabel, buyBtn, notEnoughLabel, entry, key));
        return card;
    }

    private boolean isEntrySoldOut(ResourceKey<ShopEntry> key, ShopEntry entry) {
        int purchaseCount = QuestClientCache.getPurchaseCount(key.identifier());
        return entry.maxPurchases() > 0 && purchaseCount >= entry.maxPurchases();
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

            if (soldOut) {
                refs.fallingPanel().setVisible(false);
            }

            int nameColor = soldOut ? 0xFF555555 : 0xFFFFFFFF;
            refs.nameLabel().color(nameColor);
            refs.soldOutLabel().setVisible(soldOut);
            refs.costRow().setVisible(!soldOut);
            refs.costLabel().color(canAfford ? 0xFFFFAA00 : 0xFFFF4444);
            refs.buyBtn().setVisible(canAfford && !soldOut);
            refs.notEnoughLabel().setVisible(!canAfford && !soldOut);
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
            tokenBalanceLabel.text(QuestClientCache.getTokenBalance() + " tokens");
        }

        if (cleanupGoalTotalLabel != null) {
            int total = QuestClientCache.getCleanupGoalTotal();
            int threshold = Math.max(1, QuestClientCache.getCleanupGoalThreshold());
            int intoCurrentTier = total % threshold;
            float fraction = (float) intoCurrentTier / threshold;
            cleanupGoalTotalLabel.text("Total cleaned so far: " + total);
            cleanupGoalCountLabel.text(intoCurrentTier + " / " + threshold);
            cleanupGoalBar.progressImmediate(fraction);
        }

        for (Map.Entry<Identifier, QuestRowRefs> e : questRowRefs.entrySet()) {
            Identifier questId = e.getKey();
            QuestRowRefs refs = e.getValue();

            PlayerQuestState.QuestProgress progress = QuestClientCache.getProgress(questId);
            boolean claimed = progress.claimed();
            boolean completed = progress.completed();
            boolean canClaim = completed && !claimed;
            boolean inactive = refs.isDailyTab() && !refs.activeToday();

            int currentCount = progress.currentCount();
            float fraction = refs.targetCount() > 0 ? Math.min(1f, (float) currentCount / refs.targetCount()) : 0f;

            int nameColor = (claimed || inactive) ? 0xFF666666 : 0xFFFFFFFF;
            String nameText = claimed ? "[Done] " + refs.baseDisplayName()
                    : inactive ? "(inactive) " + refs.baseDisplayName()
                    : refs.baseDisplayName();

            refs.nameLabel().text(nameText).color(nameColor);
            refs.claimButton().setVisible(canClaim);
            refs.progressBar().progressImmediate(fraction);
            refs.countLabel().text(currentCount + " / " + refs.targetCount());
            refs.row().backgroundSprite(questRowBackgroundSprite(claimed));
            refs.row().setTargetScale(claimed ? CLAIMED_QUEST_ROW_SCALE : 1.0f, true);
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
}
