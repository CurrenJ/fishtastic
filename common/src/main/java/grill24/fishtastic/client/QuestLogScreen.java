package grill24.fishtastic.client;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticItems;
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
import io.github.currenj.gelatinui.gui.UI;
import io.github.currenj.gelatinui.gui.GelatinMenu;
import io.github.currenj.gelatinui.gui.components.*;
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

import io.github.currenj.gelatinui.gui.animation.Easing;
import io.github.currenj.gelatinui.gui.animation.FloatKeyframeAnimation;
import io.github.currenj.gelatinui.gui.animation.Keyframe;
import org.joml.Vector2f;

import java.util.*;

public class QuestLogScreen extends GelatinUIScreen<GelatinMenu> {

    private MinecraftRenderContext tempContext;
    private boolean handlerInstalled = false;
    private QuestSyncPacket.ClientHandler savedHandler;
    private ResourceKey<ShopEntry> selectedShopEntryKey = null;
    private int activeTabIndex = 0;

    // Live element refs for in-place updates (populated by buildUI, cleared on rebuild)
    private Label tokenBalanceLabel;
    private final Map<Identifier, QuestRowRefs> questRowRefs = new LinkedHashMap<>();
    private final Map<ResourceKey<ShopEntry>, ShopCardRefs> shopCardRefs = new LinkedHashMap<>();
    private final Map<ResourceKey<ShopEntry>, Boolean> expandedStates = new HashMap<>();

    private record QuestRowRefs(
            Label nameLabel,
            SpriteButton claimButton,
            SpriteProgressBar progressBar,
            Label countLabel,
            int targetCount,
            String baseDisplayName,
            boolean isDailyTab,
            boolean activeToday
    ) {}

    private record ShopCardRefs(
            VBox card,
            Label nameLabel,
            HBox costRow,
            Label costLabel,
            Label soldOutLabel,
            VBox expandedSection,
            SpriteButton buyBtn,
            Label notEnoughLabel,
            ShopEntry entry,
            ResourceKey<ShopEntry> key
    ) {}

    public QuestLogScreen(GelatinMenu menu, Inventory inv) {
        super(menu, inv, Component.literal("Quest Log"));
    }

    @Override
    protected void init() {
        if (!handlerInstalled) {
            savedHandler = QuestSyncPacket.clientHandler;
            handlerInstalled = true;
            QuestSyncPacket.registerClientHandler(packet -> {
                QuestClientCache.update(packet.questProgress(), packet.tokenBalance(), packet.triggeringItems(), packet.purchaseCounts());
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
        expandedStates.clear();
        tokenBalanceLabel = null;

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

        Label titleLabel = new Label("Quest Log", 0xFFFFFFFF).init(tempContext);
        titleLabel.scale(1.3f);
        titleLabel.addBreatheEffect();
        titleLabel.onMouseEnter(e -> titleLabel.setTargetScale(1.5f, true));
        titleLabel.onMouseExit(e -> titleLabel.setTargetScale(1.3f, true));

        tokenBalanceLabel = new Label(QuestClientCache.getTokenBalance() + " tokens", 0xFFFFAA00).init(tempContext);
        HBox tokenLabel = UI.hbox().spacing(4).alignment(HBox.Alignment.CENTER);
        tokenLabel.addChild(UI.itemRenderer(new ItemStack(FishtasticItems.QUEST_TOKEN.value())));
        tokenLabel.addChild(tokenBalanceLabel);
        tokenLabel.onMouseEnter(e -> tokenLabel.setTargetScale(1.1f, true));
        tokenLabel.onMouseExit(e -> tokenLabel.setTargetScale(1.0f, true));

        VBox header = UI.vbox().spacing(4).alignment(VBox.Alignment.CENTER);
        header.addChild(titleLabel);
        header.addChild(tokenLabel);

        ItemTabs tabs = UI.itemTabs();
        tabs.addTab(new ItemStack(Items.COD),
                buildQuestList(byCategory.get(QuestCategory.DAILY), activeDailies, true));
        tabs.addTab(new ItemStack(Items.FISHING_ROD),
                buildQuestList(byCategory.get(QuestCategory.MASTERY), null, false));
        tabs.addTab(new ItemStack(Items.COMPASS),
                buildQuestList(byCategory.get(QuestCategory.EXPLORER), null, false));
        tabs.addTab(new ItemStack(Items.NETHER_STAR),
                buildQuestList(byCategory.get(QuestCategory.CHALLENGE), null, false));
        tabs.addTab(new ItemStack(Items.EMERALD),
                buildShopPanel(shopRegistry, currentDay));
        tabs.onSelectionChanged(i -> activeTabIndex = i);
        tabs.select(activeTabIndex);

        VBox content = UI.vbox().spacing(10).padding(16).alignment(VBox.Alignment.CENTER);
        content.addChild(header);
        content.addChild(tabs);

        float scaleTarget = this.width * 0.4f;
        content.scaleToWidth(scaleTarget);
        content.setPosition(new Vector2f((this.width - scaleTarget) / 2f, 0f));

        uiScreen.setRoot(content);
        uiScreen.setScrollEnabled(true);
    }

    private VBox buildQuestList(
            List<Map.Entry<ResourceKey<Quest>, Quest>> quests,
            Set<ResourceKey<Quest>> activeDailies,
            boolean isDailyTab) {
        VBox list = UI.vbox().spacing(5).padding(4).alignment(VBox.Alignment.CENTER);
        if (quests.isEmpty()) {
            list.addChild(new Label("No quests available.", 0xFF888888).init(tempContext));
        } else {
            for (int i = 0; i < quests.size(); i++) {
                var entry = quests.get(i);
                boolean active = !isDailyTab || activeDailies.contains(entry.getKey())
                        || entry.getValue().category() == QuestCategory.TUTORIAL;
                list.addChild(buildQuestRow(entry.getKey(), entry.getValue(), isDailyTab, active));
                if (i < quests.size() - 1) {
                    list.addChild(UI.rectangle(150f, 1f, 0x33FFFFFF));
                }
            }
        }
        return list;
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
        row.onMouseEnter(e -> row.setTargetScale(1.04f, true));
        row.onMouseExit(e -> row.setTargetScale(1.0f, true));

        Label nameLabel = new Label(nameText, nameColor).init(tempContext);

        SpriteButton claimBtn = UI.spriteButton(40f, 14f, 0xFF44AA44).text("Claim", 0xFFFFFFFF);
        claimBtn.onMouseEnter(e -> claimBtn.setTargetScale(1.12f, true));
        claimBtn.onMouseExit(e -> claimBtn.setTargetScale(1.0f, true));
        final Identifier fId = questId;
        claimBtn.onClick(e -> {
            claimBtn.addClickBounceEffect();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.connection.send(new ServerboundCustomPayloadPacket(new CompleteQuestPacket(fId)));
            }
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
        SpriteProgressBar bar = UI.progressBar();
        bar.progressImmediate(fraction);

        HBox progressRow = UI.hbox().spacing(6).alignment(HBox.Alignment.CENTER);
        progressRow.addChild(bar);
        progressRow.addChild(countLabel);
        row.addChild(progressRow);

        if (quest.reward().questTokens() > 0 || !quest.reward().items().isEmpty()) {
            row.addChild(buildRewardRow(quest));
        }

        questRowRefs.put(questId, new QuestRowRefs(nameLabel, claimBtn, bar, countLabel, targetCount, baseDisplayName, isDailyTab, activeToday));
        return row;
    }

    private HBox buildRewardRow(Quest quest) {
        HBox row = UI.hbox().spacing(6).alignment(HBox.Alignment.CENTER);
        if (quest.reward().questTokens() > 0) {
            row.addChild(UI.itemRenderer(new ItemStack(FishtasticItems.QUEST_TOKEN.value())));
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

    private VBox buildShopPanel(Registry<ShopEntry> shopRegistry, long currentDay) {
        VBox panel = UI.vbox().spacing(8).padding(4).alignment(VBox.Alignment.CENTER);

        if (shopRegistry == null || shopRegistry.entrySet().isEmpty()) {
            panel.addChild(new Label("No shop entries available.", 0xFF888888).init(tempContext));
            return panel;
        }

        Set<ResourceKey<ShopEntry>> activeKeys = ShopEntry.getActiveDailyShop(shopRegistry, currentDay);

        if (selectedShopEntryKey != null && !activeKeys.contains(selectedShopEntryKey)) {
            selectedShopEntryKey = null;
        }

        List<ResourceKey<ShopEntry>> activeList = new ArrayList<>(activeKeys);

        Label shopTitle = new Label("Today's Stock", 0xFFFFFFFF).init(tempContext);
        shopTitle.scale(1.1f);
        panel.addChild(shopTitle);

        int cols = 2;
        VBox grid = UI.vbox().spacing(6).alignment(VBox.Alignment.CENTER);
        for (int row = 0; row < activeList.size(); row += cols) {
            HBox rowBox = UI.hbox().spacing(8).alignment(HBox.Alignment.CENTER);
            for (int col = 0; col < cols && row + col < activeList.size(); col++) {
                ResourceKey<ShopEntry> key = activeList.get(row + col);
                ShopEntry entry = shopRegistry.getOptional(key).orElse(null);
                if (entry != null) rowBox.addChild(buildShopEntryCard(key, entry));
            }
            grid.addChild(rowBox);
        }
        panel.addChild(grid);

        return panel;
    }

    private VBox buildShopEntryCard(ResourceKey<ShopEntry> key, ShopEntry entry) {
        boolean isSelected = key.equals(selectedShopEntryKey);
        int purchaseCount = QuestClientCache.getPurchaseCount(key.identifier());
        boolean soldOut = entry.maxPurchases() > 0 && purchaseCount >= entry.maxPurchases();
        boolean canAfford = QuestClientCache.getTokenBalance() >= entry.cost();

        // Outer border box — its backgroundColor becomes the gold outline when selected
        VBox card = UI.vbox().padding(2).alignment(VBox.Alignment.CENTER);
        if (isSelected && !soldOut) card.backgroundColor(0xAAFFAA00);

        // Inner content box
        VBox inner = UI.vbox().spacing(3).padding(5).alignment(VBox.Alignment.CENTER);

        if (!entry.reward().isEmpty()) {
            ItemStack icon = entry.reward().get(0).toItemStack();
            if (!icon.isEmpty()) inner.addChild(UI.itemRenderer(icon));
        }

        int nameColor = soldOut ? 0xFF555555 : 0xFFFFFFFF;
        String nameText = entry.displayName().isEmpty() ? key.identifier().getPath() : entry.displayName();
        Label nameLabel = new Label(nameText, nameColor).init(tempContext);
        inner.addChild(nameLabel);

        // Sold out label — toggled by updateShopCardVisuals
        Label soldOutLabel = new Label("Sold Out", 0xFF555555).init(tempContext);
        soldOutLabel.setVisible(soldOut);
        inner.addChild(soldOutLabel);

        // Cost row — hidden when sold out
        int costColor = soldOut ? 0xFF555555 : (canAfford ? 0xFFFFAA00 : 0xFFFF4444);
        Label costLabel = new Label(String.valueOf(entry.cost()), costColor).init(tempContext);
        HBox costRow = UI.hbox().spacing(2).alignment(HBox.Alignment.CENTER);
        costRow.addChild(UI.itemRenderer(new ItemStack(FishtasticItems.QUEST_TOKEN.value())));
        costRow.addChild(costLabel);
        costRow.setVisible(!soldOut);
        inner.addChild(costRow);

        // Expanded section — visible when selected and not sold out
        VBox expandedSection = UI.vbox().spacing(3).alignment(VBox.Alignment.CENTER).scaleFromCenter();
        if (!entry.description().isEmpty()) {
            expandedSection.addChild(new Label(entry.description(), 0xFFCCCCCC).maxWidth(80).centered(true).init(tempContext).scaleFromCenter());
        }

        SpriteButton buyBtn = UI.spriteButton(60f, 14f, 0xFF44AA44).text("Buy", 0xFFFFFFFF).scaleFromCenter();
        buyBtn.onMouseEnter(e -> buyBtn.setTargetScale(1.12f, true));
        buyBtn.onMouseExit(e -> buyBtn.setTargetScale(1.0f, true));
        final ResourceKey<ShopEntry> fKey = key;
        buyBtn.onClick(e -> {
            buyBtn.addClickBounceEffect();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.connection.send(new ServerboundCustomPayloadPacket(new PurchaseShopEntryPacket(fKey.identifier())));
            }
        });
        buyBtn.setVisible(canAfford);
        expandedSection.addChild(buyBtn);

        Label notEnoughLabel = new Label("Not enough tokens", 0xFFFF4444).init(tempContext);
        notEnoughLabel.setVisible(!canAfford);
        expandedSection.addChild(notEnoughLabel);

        expandedSection.setVisible(isSelected && !soldOut);
        inner.addChild(expandedSection);

        card.addChild(inner);

        card.onClick(e -> {
            boolean currentSoldOut = entry.maxPurchases() > 0
                    && QuestClientCache.getPurchaseCount(key.identifier()) >= entry.maxPurchases();
            if (!currentSoldOut) {
                selectedShopEntryKey = key.equals(selectedShopEntryKey) ? null : key;
                updateShopCardVisuals();
            }
        });
        card.onMouseEnter(e -> {
            boolean currentSoldOut = entry.maxPurchases() > 0
                    && QuestClientCache.getPurchaseCount(key.identifier()) >= entry.maxPurchases();
            if (!currentSoldOut) card.setTargetScale(1.06f, true);
        });
        card.onMouseExit(e -> card.setTargetScale(1.0f, true));

        shopCardRefs.put(key, new ShopCardRefs(card, nameLabel, costRow, costLabel, soldOutLabel, expandedSection, buyBtn, notEnoughLabel, entry, key));
        expandedStates.put(key, isSelected && !soldOut);
        return card;
    }

    private void updateShopCardVisuals() {
        for (ShopCardRefs refs : shopCardRefs.values()) {
            boolean isSelected = refs.key().equals(selectedShopEntryKey);
            int purchaseCount = QuestClientCache.getPurchaseCount(refs.key().identifier());
            boolean soldOut = refs.entry().maxPurchases() > 0 && purchaseCount >= refs.entry().maxPurchases();
            boolean canAfford = QuestClientCache.getTokenBalance() >= refs.entry().cost();

            // Auto-collapse a selected card that just sold out
            if (soldOut && refs.key().equals(selectedShopEntryKey)) {
                selectedShopEntryKey = null;
                isSelected = false;
            }

            int nameColor = soldOut ? 0xFF555555 : 0xFFFFFFFF;
            refs.nameLabel().color(nameColor);
            boolean shouldShowBorder = isSelected && !soldOut;
            if (shouldShowBorder) {
                animateBorderAlpha(refs.card(), true);
            } else if (refs.card().isDrawingBackground()) {
                animateBorderAlpha(refs.card(), false);
            }
            refs.soldOutLabel().setVisible(soldOut);
            refs.costRow().setVisible(!soldOut);
            refs.costLabel().color(canAfford ? 0xFFFFAA00 : 0xFFFF4444);
            boolean wantExpanded = isSelected && !soldOut;
            if (expandedStates.getOrDefault(refs.key(), false) != wantExpanded) {
                expandedStates.put(refs.key(), wantExpanded);
                animateExpandedSection(refs.expandedSection(), wantExpanded);
            }
            refs.buyBtn().setVisible(canAfford);
            refs.notEnoughLabel().setVisible(!canAfford);
        }
    }

    private static final int BORDER_RGB = 0x00FFAA00;
    private static final int BORDER_ALPHA_MAX = 0xAA;

    private void animateExpandedSection(VBox section, boolean show) {
        section.cancelAnimationChannel("expandScale");
        if (show) {
            section.setTargetScale(0f, false);
            section.setVisible(true);
            section.playAnimation(new FloatKeyframeAnimation("expandScale",
                    List.of(new Keyframe(0f, 0f), new Keyframe(0.2f, 1f, Easing.EASE_OUT_BACK)),
                    v -> section.setTargetScale(v, false)));
        } else {
            float from = section.getCurrentScale();
            section.playAnimation(new FloatKeyframeAnimation("expandScale",
                    List.of(new Keyframe(0f, from), new Keyframe(0.15f * from, 0f, Easing.EASE_IN_CUBIC)),
                    v -> section.setTargetScale(v, false),
                    () -> section.setVisible(false)));
        }
    }

    private void animateBorderAlpha(VBox card, boolean fadeIn) {
        card.cancelAnimationChannel("borderAlpha");
        if (fadeIn) {
            card.playAnimation(new FloatKeyframeAnimation("borderAlpha",
                    List.of(new Keyframe(0f, 0f), new Keyframe(0.15f, 1f, Easing.EASE_OUT_CUBIC)),
                    v -> card.backgroundColor((int)(v * BORDER_ALPHA_MAX) << 24 | BORDER_RGB)));
        } else {
            card.playAnimation(new FloatKeyframeAnimation("borderAlpha",
                    List.of(new Keyframe(0f, 1f), new Keyframe(0.2f, 0f, Easing.EASE_IN_CUBIC)),
                    v -> card.backgroundColor((int)(v * BORDER_ALPHA_MAX) << 24 | BORDER_RGB),
                    () -> card.drawBackground(false)));
        }
    }

    private void updateInPlace() {
        if (tokenBalanceLabel != null) {
            tokenBalanceLabel.text(QuestClientCache.getTokenBalance() + " tokens");
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
        }

        updateShopCardVisuals();
    }

    @Override
    public void removed() {
        super.removed();
        if (handlerInstalled) {
            QuestSyncPacket.registerClientHandler(savedHandler);
        }
    }
}
