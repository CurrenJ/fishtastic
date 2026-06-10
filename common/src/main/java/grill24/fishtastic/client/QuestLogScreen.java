package grill24.fishtastic.client;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.QuestCategory;
import grill24.fishtastic.network.CompleteQuestPacket;
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

import org.joml.Vector2f;

import java.util.*;

public class QuestLogScreen extends GelatinUIScreen<GelatinMenu> {

    private MinecraftRenderContext tempContext;
    private boolean handlerInstalled = false;
    private QuestSyncPacket.ClientHandler savedHandler;

    public QuestLogScreen(GelatinMenu menu, Inventory inv) {
        super(menu, inv, Component.literal("Quest Log"));
    }

    @Override
    protected void init() {
        if (!handlerInstalled) {
            savedHandler = QuestSyncPacket.clientHandler;
            handlerInstalled = true;
            QuestSyncPacket.registerClientHandler(packet -> {
                QuestClientCache.update(packet.questProgress(), packet.tokenBalance(), packet.triggeringItems());
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == this) {
                    this.init();
                }
            });
        }
        super.init();
    }

    @Override
    protected void buildUI() {
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

        Label titleLabel = new Label("Quest Log", 0xFFFFFFFF).init(tempContext);
        titleLabel.scale(1.3f);
        titleLabel.addBreatheEffect();
        titleLabel.onMouseEnter(e -> titleLabel.setTargetScale(1.5f, true));
        titleLabel.onMouseExit(e -> titleLabel.setTargetScale(1.3f, true));

        HBox tokenLabel = UI.hbox().spacing(4).alignment(HBox.Alignment.CENTER);
        tokenLabel.addChild(UI.itemRenderer(new ItemStack(FishtasticItems.QUEST_TOKEN.value())));
        tokenLabel.addChild(new Label(QuestClientCache.getTokenBalance() + " tokens", 0xFFFFAA00).init(tempContext));
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

        VBox content = UI.vbox().spacing(10).padding(16).alignment(VBox.Alignment.CENTER);
        content.addChild(header);
        content.addChild(tabs);

        float scaleTarget = this.width * 0.4f;
        content.scaleToWidth(scaleTarget);
        // scaleToWidth guarantees content.getSize().x == scaleTarget, so centerX is exact at build time.
        // Pre-position horizontally centered, vertically at top (y=0) so scroll starts at the top
        // rather than at a negative auto-centered y that would permanently clip the first few rows.
        content.setPosition(new Vector2f((this.width - scaleTarget) / 2f, 0f));

        uiScreen.setRoot(content);
        // autoCenterRoot stays false (GelatinUIScreen default) — horizontal centering is baked into
        // the pre-positioned baseRootPosition; vertical is left to scroll.
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
                boolean active = !isDailyTab || activeDailies.contains(entry.getKey());
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

        int nameColor = (claimed || inactive) ? 0xFF666666 : 0xFFFFFFFF;
        String nameText = quest.displayName().isEmpty() ? questId.getPath() : quest.displayName();
        if (claimed) nameText = "[Done] " + nameText;
        else if (inactive) nameText = "(inactive) " + nameText;

        VBox row = UI.vbox().spacing(3).padding(4).alignment(VBox.Alignment.CENTER);
        row.onMouseEnter(e -> row.setTargetScale(1.04f, true));
        row.onMouseExit(e -> row.setTargetScale(1.0f, true));

        HBox nameRow = UI.hbox().spacing(8).alignment(HBox.Alignment.CENTER);
        nameRow.addChild(new Label(nameText, nameColor).init(tempContext));

        if (canClaim) {
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
            nameRow.addChild(claimBtn);
        }

        row.addChild(nameRow);

        if (!quest.description().isEmpty()) {
            row.addChild(new Label(quest.description(), 0xFF888888).maxWidth(150).centered(true).init(tempContext));
        }

        HBox progressRow = UI.hbox().spacing(6).alignment(HBox.Alignment.CENTER);
        SpriteProgressBar bar = UI.progressBar();
        bar.progressImmediate(fraction);
        progressRow.addChild(bar);
        progressRow.addChild(new Label(currentCount + " / " + targetCount, 0xFFAAAAAA).init(tempContext));
        row.addChild(progressRow);

        if (quest.reward().questTokens() > 0 || !quest.reward().items().isEmpty()) {
            row.addChild(buildRewardRow(quest));
        }

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

    @Override
    public void removed() {
        super.removed();
        if (handlerInstalled) {
            QuestSyncPacket.registerClientHandler(savedHandler);
        }
    }
}
