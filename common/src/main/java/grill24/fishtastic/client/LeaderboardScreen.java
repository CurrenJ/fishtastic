package grill24.fishtastic.client;

import com.mojang.authlib.GameProfile;
import grill24.fishtastic.network.LeaderboardEntry;
import grill24.fishtastic.network.LeaderboardResponsePacket;
import grill24.fishtastic.network.LeaderboardType;
import grill24.fishtastic.network.RequestLeaderboardPacket;
import io.github.currenj.gelatinui.GelatinUIScreen;
import io.github.currenj.gelatinui.gui.DirtyFlag;
import io.github.currenj.gelatinui.gui.UI;
import io.github.currenj.gelatinui.gui.animation.Easing;
import io.github.currenj.gelatinui.gui.animation.FloatKeyframeAnimation;
import io.github.currenj.gelatinui.gui.animation.Keyframe;
import io.github.currenj.gelatinui.gui.components.HBox;
import io.github.currenj.gelatinui.gui.components.Label;
import io.github.currenj.gelatinui.gui.components.ManualContainer;
import io.github.currenj.gelatinui.gui.components.VBox;
import io.github.currenj.gelatinui.gui.GelatinMenu;
import io.github.currenj.gelatinui.gui.minecraft.MinecraftRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import org.joml.Vector2f;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class LeaderboardScreen extends GelatinUIScreen<GelatinMenu> {

    private boolean isPersonal = true;
    private boolean isCount = true;
    private boolean isAnimating = false;

    // Root container for absolute positioning
    private ManualContainer rootContainer;

    // Settings panel (pinned to top-right)
    private VBox settingsVBox;

    // Content panel (centered on screen)
    private VBox contentVBox;

    // The animated list wrapper — holds the currently visible list
    private VBox listWrapper;

    // Settings buttons, kept for highlight updates
    private SelectableItemButton personalBtn;
    private SelectableItemButton globalBtn;
    private SelectableItemButton countBtn;
    private SelectableItemButton sizeBtn;

    // Title label, updated when personal/global changes
    private Label titleLabel;

    // Temp render context for measuring text during label construction (graphics can be null)
    private MinecraftRenderContext tempContext;

    public LeaderboardScreen(GelatinMenu menu, Inventory inv) {
        super(menu, inv, Component.translatable("screen.fishtastic.leaderboard.title"));
    }

    @Override
    protected void buildUI() {
        tempContext = new MinecraftRenderContext(null, this.font);
        LeaderboardResponsePacket.registerClientHandler(this::onLeaderboardResponse);

        // --- Settings panel (two rows of item buttons) ---
        settingsVBox = buildSettingsPanel();

        // --- Leaderboard content ---
        listWrapper = UI.vbox().spacing(3).alignment(VBox.Alignment.CENTER);
        listWrapper.addChild(label(translated("screen.fishtastic.leaderboard.loading"), 0xFF888888));

        titleLabel = new Label(titleText(), 0xFFFFFFFF).init(tempContext);
        titleLabel.scale(1.2f);

        contentVBox = UI.vbox().spacing(10).padding(16).alignment(VBox.Alignment.CENTER);
        contentVBox.addChild(titleLabel);
        contentVBox.addChild(listWrapper);

        // --- Root: ManualContainer covering the full screen ---
        rootContainer = UI.manualContainer();
        rootContainer.setSize(this.width, this.height);
        rootContainer.addChildAt(contentVBox, this.width / 2f, this.height / 2f);
        // Settings position is approximate here; updateComponentSizes pins it precisely each frame
        rootContainer.addChildAt(settingsVBox, this.width - 50f, 50f);

        uiScreen.setRoot(rootContainer);
        uiScreen.setAutoCenterRoot(false);
        uiScreen.setScrollEnabled(false);

        requestLeaderboard();
    }

    // Pins the settings panel to the top-right corner every frame
    @Override
    protected void updateComponentSizes(MinecraftRenderContext context) {
        if (settingsVBox == null || rootContainer == null) return;
        Vector2f size = settingsVBox.getSize();
        float margin = 12f;
        float cx = this.width - margin - size.x / 2f;
        float cy = margin + size.y / 2f;
        rootContainer.updateChildPosition(settingsVBox, cx, cy);
    }

    @Override
    public void removed() {
        super.removed();
        LeaderboardResponsePacket.registerClientHandler(null);
    }

    // -------------------------------------------------------------------------
    // Settings panel construction

    private VBox buildSettingsPanel() {
        ItemStack playerHead = new ItemStack(Items.PLAYER_HEAD);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            playerHead.set(DataComponents.PROFILE, ResolvableProfile.createResolved(mc.player.getGameProfile()));
        }

        // Row 1: Personal | Global
        personalBtn = new SelectableItemButton(playerHead);
        globalBtn   = new SelectableItemButton(new ItemStack(Items.GRASS_BLOCK));

        personalBtn.onClick(e -> onPersonalGlobalChanged(true));
        globalBtn.onClick(e -> onPersonalGlobalChanged(false));

        HBox personalGlobalRow = UI.hbox().spacing(4).alignment(HBox.Alignment.CENTER);
        personalGlobalRow.addChild(personalBtn);
        personalGlobalRow.addChild(globalBtn);

        // Row 2: Count | Size
        countBtn = new SelectableItemButton(new ItemStack(Items.FISHING_ROD));
        sizeBtn  = new SelectableItemButton(new ItemStack(Items.COD));

        countBtn.onClick(e -> onCountSizeChanged(true));
        sizeBtn.onClick(e -> onCountSizeChanged(false));

        HBox countSizeRow = UI.hbox().spacing(4).alignment(HBox.Alignment.CENTER);
        countSizeRow.addChild(countBtn);
        countSizeRow.addChild(sizeBtn);

        VBox settings = UI.vbox().spacing(4).padding(4);
        settings.addChild(personalGlobalRow);
        settings.addChild(countSizeRow);

        updateButtonHighlights();
        return settings;
    }

    private void onPersonalGlobalChanged(boolean personal) {
        if (isPersonal == personal || isAnimating) return;
        isPersonal = personal;
        updateButtonHighlights();
        animateSwitch();
    }

    private void onCountSizeChanged(boolean count) {
        if (isCount == count || isAnimating) return;
        isCount = count;
        updateButtonHighlights();
        animateSwitch();
    }

    private void updateButtonHighlights() {
        if (personalBtn != null) { personalBtn.itemScale(isPersonal ? 1.2f : 0.8f); personalBtn.setSelected(isPersonal); }
        if (globalBtn   != null) { globalBtn.itemScale(isPersonal   ? 0.8f : 1.2f); globalBtn.setSelected(!isPersonal); }
        if (countBtn    != null) { countBtn.itemScale(isCount  ? 1.2f : 0.8f); countBtn.setSelected(isCount); }
        if (sizeBtn     != null) { sizeBtn.itemScale(isCount   ? 0.8f : 1.2f); sizeBtn.setSelected(!isCount); }

        if (titleLabel  != null) titleLabel.text(titleText());
    }

    private String titleText() {
        return translated(isPersonal ? "screen.fishtastic.leaderboard.title_personal" : "screen.fishtastic.leaderboard.title_global");
    }

    // -------------------------------------------------------------------------
    // Animation

    private void animateSwitch() {
        if (listWrapper == null) return;
        isAnimating = true;

        List<Keyframe> outKeys = List.of(
                new Keyframe(0f, 1f),
                new Keyframe(0.12f, 0f, Easing.EASE_IN_CUBIC)
        );
        FloatKeyframeAnimation animOut = new FloatKeyframeAnimation(
                "listOut",
                outKeys,
                v -> {
                    listWrapper.setTargetScale(v, false);
                    listWrapper.markDirty(DirtyFlag.SIZE);
                },
                () -> {
                    listWrapper.clearChildren();
                    listWrapper.addChild(label(translated("screen.fishtastic.leaderboard.loading"), 0xFF888888));
                    requestLeaderboard();
                }
        );
        listWrapper.playAnimation(animOut);
    }

    private void animateIn() {
        if (listWrapper == null) return;

        List<Keyframe> inKeys = List.of(
                new Keyframe(0f, 0f),
                new Keyframe(0.15f, 1f, Easing.EASE_OUT_BACK)
        );
        FloatKeyframeAnimation animIn = new FloatKeyframeAnimation(
                "listIn",
                inKeys,
                v -> {
                    listWrapper.setTargetScale(v, false);
                    listWrapper.markDirty(DirtyFlag.SIZE);
                },
                () -> {
                    listWrapper.setTargetScale(1f, false);
                    isAnimating = false;
                }
        );
        listWrapper.playAnimation(animIn);
    }

    // -------------------------------------------------------------------------
    // Network

    private void requestLeaderboard() {
        LeaderboardType type = currentType();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Optional<UUID> target = (type == LeaderboardType.PERSONAL_BEST_SIZE || type == LeaderboardType.PERSONAL_CATCH_COUNT)
                ? Optional.of(mc.player.getUUID())
                : Optional.empty();
        mc.player.connection.send(new ServerboundCustomPayloadPacket(
                new RequestLeaderboardPacket(type, false, target)));
    }

    private LeaderboardType currentType() {
        if (isPersonal) return isCount ? LeaderboardType.PERSONAL_CATCH_COUNT : LeaderboardType.PERSONAL_BEST_SIZE;
        return isCount ? LeaderboardType.GLOBAL_CATCH_COUNT : LeaderboardType.GLOBAL_BEST_SIZE;
    }

    private void onLeaderboardResponse(LeaderboardResponsePacket packet) {
        // Ignore responses that don't match the current selection
        if (packet.leaderboardType() != currentType()) return;

        listWrapper.clearChildren();

        List<LeaderboardEntry> entries = packet.entries();
        if (entries.isEmpty()) {
            listWrapper.addChild(label(translated("screen.fishtastic.leaderboard.no_entries"), 0xFF888888));
        } else {
            for (int i = 0; i < entries.size(); i++) {
                listWrapper.addChild(buildEntryRow(i + 1, entries.get(i), packet.leaderboardType()));
            }
        }

        animateIn();
    }

    // -------------------------------------------------------------------------
    // Row builders

    private HBox buildEntryRow(int rank, LeaderboardEntry entry, LeaderboardType type) {
        HBox row = UI.hbox().spacing(6).alignment(HBox.Alignment.CENTER);

        row.addChild(label(translated("screen.fishtastic.leaderboard.rank", rank), 0xFFAAAAAA));

        if (type == LeaderboardType.GLOBAL_CATCH_COUNT) {
            entry.playerUuid().ifPresent(uuid ->
                    row.addChild(UI.itemRenderer(playerHeadStack(uuid, entry.playerName().orElse("?"))))
            );
        } else {
            entry.fishType().ifPresent(loc -> {
                Item item = BuiltInRegistries.ITEM.getOptional(loc).orElse(Items.COD);
                row.addChild(UI.itemRenderer(new ItemStack(item)));
            });
        }

        row.addChild(label(entryText(entry, type), 0xFFFFFFFF));

        if (type == LeaderboardType.GLOBAL_BEST_SIZE) {
            entry.playerUuid().ifPresent(uuid ->
                    row.addChild(UI.itemRenderer(playerHeadStack(uuid, entry.playerName().orElse("?"))))
            );
        }

        return row;
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
