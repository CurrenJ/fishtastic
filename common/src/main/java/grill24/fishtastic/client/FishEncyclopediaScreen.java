package grill24.fishtastic.client;

import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.client.effects.CoinArcEffect;
import grill24.fishtastic.client.renderer.ZoneIconTextures;
import grill24.fishtastic.component.CharmEffect;
import grill24.fishtastic.data.EncyclopediaRewardSection;
import grill24.fishtastic.data.FishEncyclopediaEntry;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.item.CopperFishingRod;
import grill24.fishtastic.network.ClaimEncyclopediaRewardPacket;
import grill24.fishtastic.network.FishEncyclopediaSyncPacket;
import grill24.fishtastic.network.LeaderboardEntry;
import grill24.fishtastic.util.Utility;
import io.github.currenj.gelatinui.GelatinUIScreen;
import io.github.currenj.gelatinui.gui.GelatinMenu;
import io.github.currenj.gelatinui.gui.IRenderContext;
import io.github.currenj.gelatinui.gui.IUIElement;
import io.github.currenj.gelatinui.gui.UI;
import io.github.currenj.gelatinui.gui.UIElement;
import io.github.currenj.gelatinui.gui.animation.FloatKeyframeAnimation;
import io.github.currenj.gelatinui.gui.animation.Keyframe;
import io.github.currenj.gelatinui.gui.components.HBox;
import io.github.currenj.gelatinui.gui.components.ItemRenderer;
import io.github.currenj.gelatinui.gui.components.Label;
import io.github.currenj.gelatinui.gui.components.ManualContainer;
import io.github.currenj.gelatinui.gui.components.SpriteButton;
import io.github.currenj.gelatinui.gui.components.SpriteData;
import io.github.currenj.gelatinui.gui.components.SpriteProgressBar;
import io.github.currenj.gelatinui.gui.components.SpriteRectangle;
import io.github.currenj.gelatinui.gui.components.SpriteRenderMode;
import io.github.currenj.gelatinui.gui.components.VBox;
import io.github.currenj.gelatinui.gui.minecraft.MinecraftRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Fish encyclopedia: a home screen disc of every fish (silhouettes for never-caught fish),
 * clicking one travels the icon into an info page with progressively-unlocking sections.
 * Structured like {@link QuestLogScreen}: a single screen class with private builder methods.
 */
public class FishEncyclopediaScreen extends GelatinUIScreen<GelatinMenu> {
    private static final float DISC_RADIUS = 70f;
    private static final float DISC_SIZE = DISC_RADIUS * 2f + 32f;
    private static final float INFO_ICON_SCALE = 3.0f;
    private static final float INFO_ICON_SIZE = 16f * INFO_ICON_SCALE;
    private static final float INFO_ICON_TOP_MARGIN = 24f;
    private static final float CLOSE_TRAVEL_DURATION = 0.3f;
    private static final float SCREEN_FADE_DURATION = 0.2f;

    // Staggers unclaimed-reward pip pop-ins across the disc instead of all popping at once.
    private static final float REWARD_PIP_POP_INITIAL_DELAY = 1.2f;
    private static final float REWARD_PIP_POP_STAGGER = 0.1f;

    // Status pip shown to the left of each spawn condition row — lit green while that
    // condition currently holds for the player, dim otherwise. Mirrors QuestLogScreen's
    // per-objective status pip, but evaluated per spawn-weight row rather than per quest.
    private static final Identifier STATUS_PIP_DEFAULT_TEXTURE = Fishtastic.id("textures/gui/status_indicator_pip_default.png");
    private static final Identifier STATUS_PIP_GREEN_TEXTURE = Fishtastic.id("textures/gui/status_indicator_pip_green.png");
    private static final Identifier STATUS_PIP_RED_TEXTURE = Fishtastic.id("textures/gui/status_indicator_pip_red.png");
    private static final float STATUS_PIP_SIZE = 5f;

    // Zone icon shown atop each Zones-section column, same art (and outline shader) the fishing
    // minigame's HUD zone stack uses — rendered larger than a normal row icon since Zones is now
    // its own always-visible section rather than a line item tucked into Types.
    private static final float ZONE_ICON_SIZE = 32f;
    private static final float ZONE_ICON_HOVER_SCALE = 2.0f;

    // Reward claim button background, reused from QuestLogScreen's claimed-panel texture.
    private static final Identifier CLAIM_BUTTON_TEXTURE = Fishtastic.id("textures/gui/green_generic_item_panel_2.png");
    private static final int CLAIM_BUTTON_SOURCE_WIDTH = 20;
    private static final int CLAIM_BUTTON_SOURCE_HEIGHT = 24;
    private static final int CLAIM_BUTTON_SLICE_LEFT = 4;
    private static final int CLAIM_BUTTON_SLICE_TOP = 4;
    private static final int CLAIM_BUTTON_SLICE_RIGHT = CLAIM_BUTTON_SOURCE_WIDTH - (CLAIM_BUTTON_SLICE_LEFT + 12);
    private static final int CLAIM_BUTTON_SLICE_BOTTOM = CLAIM_BUTTON_SOURCE_HEIGHT - (CLAIM_BUTTON_SLICE_TOP + 16);
    // Coin icon blitted over the claim button in place of "+N" text; button size follows the icon.
    private static final float CLAIM_BUTTON_ICON_PADDING = 3f;
    private record CoinIcon(Identifier texture, float width, float height) {}
    private static final CoinIcon COIN_ICON_ONE = new CoinIcon(Fishtastic.id("textures/gui/one_coin.png"), 8f, 7f);
    private static final CoinIcon COIN_ICON_TWO = new CoinIcon(Fishtastic.id("textures/gui/two_coins.png"), 12f, 8f);
    private static final CoinIcon COIN_ICON_THREE = new CoinIcon(Fishtastic.id("textures/gui/three_coins.png"), 8f, 11f);

    private boolean closingScreen = false;
    private long closingAtNanos = -1L;

    private MinecraftRenderContext tempContext;
    private boolean handlerInstalled = false;
    private FishEncyclopediaSyncPacket.ClientHandler savedHandler;

    private FreeformContainer root;
    private FishSphereContainer sphere;
    // Zero-size overlay hosting the reward coin-fly flourish; screen-scoped like QuestLogScreen's coinFlyOverlay.
    private ManualContainer rewardFlyOverlay;
    private final Map<ResourceKey<FishProfile>, SilhouetteItemButton> iconRefs = new LinkedHashMap<>();

    // Info page state — non-null only while the info page is showing.
    private ResourceKey<FishProfile> selectedFishKey;
    private FishProfile selectedProfile;
    private FishEncyclopediaEntry selectedEntry;
    private IUIElement selectedIcon;
    private VBox infoPageRoot;
    private SpriteButton backButton;
    private boolean closing = false;
    private IUIElement closingIcon;
    private Label nameLabel;
    private SpriteButton nameClaimButton;
    private Label caughtCountLabel;
    private VBox recordsContainer;
    private VBox zonesContainer;
    private VBox statsContainer;
    private VBox typesContainer;
    private VBox spawnContainer;
    private VBox loreContainer;
    // Re-evaluated every tick so a spawn condition's pip reacts live to the player walking
    // between biomes, day/night passing, or weather changing — see containerTick().
    private final List<Runnable> spawnConditionPipUpdaters = new ArrayList<>();
    // Separate list for the always-visible Zones section's pips — kept apart from
    // spawnConditionPipUpdaters because that list is cleared inside buildSpawnConditionsContent,
    // which (being gated) doesn't always run on a given refresh; Zones always runs, so it owns
    // its own clear/populate lifecycle.
    private final List<Runnable> zonePipUpdaters = new ArrayList<>();

    public FishEncyclopediaScreen(GelatinMenu menu, Inventory inv) {
        super(menu, inv, Component.translatable("screen.fishtastic.encyclopedia.title"));
    }

    @Override
    protected void init() {
        if (!handlerInstalled) {
            savedHandler = FishEncyclopediaSyncPacket.clientHandler;
            handlerInstalled = true;
            FishEncyclopediaSyncPacket.registerClientHandler(packet -> {
                FishEncyclopediaClientCache.update(packet.personalCatchCounts(), packet.personalBestSizes(), packet.globalBestSizes(), packet.claimedRewardKeys());
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == this) {
                    refreshFromCache();
                }
            });
        }
        super.init();
    }

    @Override
    public void removed() {
        super.removed();
        if (handlerInstalled) {
            FishEncyclopediaSyncPacket.registerClientHandler(savedHandler);
        }
    }

    @Override
    public void onClose() {
        if (closingScreen) return;
        if (selectedFishKey != null) EncyclopediaTutorialClientHandler.onInfoPageClosed();
        closingScreen = true;
        closingAtNanos = System.nanoTime();
        if (sphere != null) sphere.startOutroAnimation();
        // Actual close is deferred to tick() so the animation can play first.
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Escape deselects the active fish (back to disc) before closing the screen.
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && selectedFishKey != null && !closing) {
            goBackToDisc();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractTransparentBackground(GuiGraphicsExtractor graphics) {
        if (!closingScreen) {
            super.extractTransparentBackground(graphics);
            return;
        }
        float elapsed = (System.nanoTime() - closingAtNanos) / 1_000_000_000f;
        float alpha = Math.max(0f, 1f - elapsed / SCREEN_FADE_DURATION);
        int a1 = (int) (0xC0 * alpha);
        int a2 = (int) (0xD0 * alpha);
        graphics.fillGradient(0, 0, this.width, this.height,
                (a1 << 24) | 0x101010,
                (a2 << 24) | 0x101010);
        if (elapsed >= SCREEN_FADE_DURATION) {
            closingScreen = false;
            closingAtNanos = -1L;
            super.onClose();
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        for (Runnable updater : spawnConditionPipUpdaters) {
            updater.run();
        }
        for (Runnable updater : zonePipUpdaters) {
            updater.run();
        }
    }

    // -------------------------------------------------------------------------
    // Home screen (disc)

    @Override
    protected void buildUI() {
        tempContext = new MinecraftRenderContext(null, this.font);
        iconRefs.clear();
        selectedFishKey = null;
        selectedProfile = null;
        selectedEntry = null;
        selectedIcon = null;
        infoPageRoot = null;
        backButton = null;
        closing = false;
        closingIcon = null;
        nameLabel = null;
        nameClaimButton = null;
        caughtCountLabel = null;
        recordsContainer = null;
        zonesContainer = null;
        statsContainer = null;
        typesContainer = null;
        spawnContainer = null;
        loreContainer = null;
        spawnConditionPipUpdaters.clear();
        zonePipUpdaters.clear();

        Minecraft mc = Minecraft.getInstance();

        root = new FreeformContainer();
        root.setSize(this.width, this.height);

        sphere = new FishSphereContainer()
                .discRadius(DISC_RADIUS)
                .onFishSelected(this::onFishSelected);
        sphere.setSize(DISC_SIZE, DISC_SIZE);
        sphere.setPosition(new Vector2f((this.width - DISC_SIZE) / 2f, (this.height - DISC_SIZE) / 2f));

        if (mc.level != null) {
            List<Map.Entry<ResourceKey<FishProfile>, FishProfile>> fishList =
                    FishEncyclopediaClientHelper.getAllFishProfilesSorted(mc.level.registryAccess());

            List<Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton>> iconItems = new ArrayList<>();
            for (Map.Entry<ResourceKey<FishProfile>, FishProfile> entry : fishList) {
                ResourceKey<FishProfile> fishKey = entry.getKey();
                Item item = BuiltInRegistries.ITEM.getOptional(fishKey.identifier()).orElse(Items.COD);
                boolean caught = FishEncyclopediaClientCache.getCatchCount(fishKey.identifier()) > 0;

                // Unlisted fish (secret one-off variants) don't even get a silhouette slot —
                // they're absent from the disc entirely until the player has caught one.
                if (!caught && item.builtInRegistryHolder().is(FishtasticItemTags.UNLISTED_FISH)) continue;

                SilhouetteItemButton icon = new SilhouetteItemButton(new ItemStack(item));
                icon.setSilhouette(!caught);
                icon.setHasUnclaimedReward(FishEncyclopediaClientHelper.fishHasUnclaimedReward(
                        mc.level.registryAccess(), fishKey, fishKey.identifier()));
                iconItems.add(Map.entry(fishKey, icon));
                iconRefs.put(fishKey, icon);
            }
            sphere.setFishItems(iconItems);
        }

        root.addChild(sphere);

        scheduleRewardPipPopIns();

        rewardFlyOverlay = UI.manualContainer().setSize(0, 0);
        rewardFlyOverlay.setDebugName("encyclopediaRewardCoinOverlay");
        root.addChild(rewardFlyOverlay);

        uiScreen.setRoot(root);
        uiScreen.setAutoCenterRoot(false);
        uiScreen.setScrollEnabled(true);
        updateRootContentSize();
    }

    /**
     * {@code root} is a {@link FreeformContainer} sized to the screen — necessary so the disc
     * and the traveling icon can be positioned absolutely — but {@code UIScreen}'s scroll system
     * computes how far there is to scroll from {@code root.getSize()} vs. the viewport, not from
     * an actual bounding box of children. Left at a fixed screen-sized {@code root}, content
     * that runs past the bottom of the screen (a fish with a long Spawn Conditions list and/or
     * lore text) is simply unreachable — the framework thinks there's nothing below to scroll to.
     * Call this whenever the displayed content's extent could have changed (info page built or
     * refreshed, or back to just the disc) so {@code root}'s reported height tracks reality.
     */
    private void updateRootContentSize() {
        if (root == null) return;
        float contentBottom = this.height;
        if (infoPageRoot != null) {
            infoPageRoot.forceLayout();
            contentBottom = Math.max(contentBottom, infoPageRoot.getPosition().y + infoPageRoot.getSize().y + 16f);
        } else if (sphere != null) {
            contentBottom = Math.max(contentBottom, sphere.getPosition().y + DISC_SIZE + 16f);
        }
        root.setSize(this.width, contentBottom);
    }

    private void refreshFromCache() {
        Minecraft mc = Minecraft.getInstance();
        for (Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton> entry : iconRefs.entrySet()) {
            ResourceKey<FishProfile> fishKey = entry.getKey();
            Identifier fishId = fishKey.identifier();
            entry.getValue().setSilhouette(FishEncyclopediaClientCache.getCatchCount(fishId) <= 0);
            if (mc.level != null) {
                entry.getValue().setHasUnclaimedReward(
                        FishEncyclopediaClientHelper.fishHasUnclaimedReward(mc.level.registryAccess(), fishKey, fishId));
            }
        }
        if (selectedFishKey != null) {
            refreshInfoPage();
            updateRootContentSize();
        }
    }

    /** Staggers unclaimed-reward pip pop-ins across the disc, in icon order. Called once on screen open only. */
    private void scheduleRewardPipPopIns() {
        float delay = REWARD_PIP_POP_INITIAL_DELAY;
        for (SilhouetteItemButton icon : iconRefs.values()) {
            if (!icon.hasUnclaimedReward()) continue;
            icon.playPipPopIn(delay);
            delay += REWARD_PIP_POP_STAGGER;
        }
    }

    // -------------------------------------------------------------------------
    // Phase 5: selection transition (click -> travel to info page)

    private void onFishSelected(IUIElement icon, ResourceKey<FishProfile> fishKey) {
        if (fishKey == null || !(icon instanceof UIElement<?> uiIcon)) return;

        EncyclopediaTutorialClientHandler.onFishClicked();

        uiIcon.reparentTo(root, true);
        uiScreen.setScrollY(0f);

        // ItemButton.init() registers its own onMouseEnter/onMouseExit handlers that drive the
        // hover-zoom (scale 1.5 / scale 1.0) seen on the disc. Once this icon leaves the disc and
        // travels to its fixed info-page slot, the cursor naturally ends up off of it within a
        // few frames, firing HOVER_EXIT — which would clobber our INFO_ICON_SCALE target right
        // back down to 1.0 (and the position math below assumes the icon renders at
        // INFO_ICON_SCALE, so a silent scale reset also looks mispositioned: the rendered box
        // shrinks but its top-left anchor point doesn't move). The icon is no longer interactive
        // on the info page, so clear all of its action handlers before asserting our own scale.
        uiIcon.clearAllActions();

        float iconTargetX = this.width / 2f - INFO_ICON_SIZE / 2f;
        float iconTargetY = INFO_ICON_TOP_MARGIN;
        uiIcon.setTargetPosition(new Vector2f(iconTargetX, iconTargetY), true);
        uiIcon.setTargetScale(INFO_ICON_SCALE, true);

        selectedIcon = icon;
        selectedFishKey = fishKey;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        selectedProfile = mc.level.registryAccess().lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY)
                .getOptional(fishKey).orElse(null);
        selectedEntry = FishEncyclopediaClientHelper.getEncyclopediaEntry(mc.level.registryAccess(), fishKey);
        if (selectedProfile == null) return;

        buildInfoPage(iconTargetY + INFO_ICON_SIZE + 8f);
    }

    /**
     * Click handler only. {@code root}/{@code sphere} tree mutations (reparenting,
     * {@code removeChild}/{@code addChild}) used to be unsafe to run synchronously here — this
     * fires from inside {@code UIElement.onEvent}'s {@code onClickActions} dispatch loop, and a
     * structural change to a list some ancestor was mid-iterating (directly, or a frame later
     * when the keyframe-animation completion callback below fired during
     * {@code UIContainer.update}'s own child loop) used to throw
     * {@code ConcurrentModificationException}. Gelatin UI's container/event-dispatch loops are
     * now defensive-copied, so this is no longer strictly required for correctness — but
     * {@code uiScreen.runDeferred(...)} is still used here to run the actual tree work at a
     * clean, predictable point (start of the next {@code UIScreen.update()}) rather than mid-event,
     * which is good practice regardless of what the framework happens to tolerate today.
     */
    private void goBackToDisc() {
        if (closing || selectedIcon == null) {
            return;
        }
        closing = true;
        uiScreen.runDeferred(this::startCloseTravel);
    }

    /**
     * Mirrors {@link #onFishSelected} in reverse: eases the info-page icon back to its disc rest
     * position/scale (the same continuous {@code setTargetPosition}/{@code setTargetScale}
     * technique the opening travel animation uses, so closing feels like the same motion played
     * backwards) and removes the info page immediately. The icon isn't reparented back into
     * {@link #sphere} — and the rest of the disc isn't shown again — until the travel-back
     * finishes; the other icons were only ever hidden (never moved) while the info page was
     * open, so they grow back from scale 0 at the exact rest position they shrank to, rather
     * than flying in from somewhere else.
     */
    private void startCloseTravel() {
        if (!(selectedIcon instanceof UIElement<?> uiIcon)) {
            closing = false;
            return;
        }

        EncyclopediaTutorialClientHandler.onInfoPageClosed();

        if (backButton != null) {
            root.removeChild(backButton);
            backButton = null;
        }
        if (infoPageRoot != null) {
            root.removeChild(infoPageRoot);
            infoPageRoot = null;
        }
        updateRootContentSize();
        uiScreen.setScrollY(0f);

        float defaultScale = sphere.getDefaultItemScale();
        Vector2f restTopLeft = sphere.getRestTopLeft(selectedIcon, defaultScale);
        Vector2f targetPos = restTopLeft != null
                ? new Vector2f(sphere.getPosition()).add(restTopLeft)
                : new Vector2f(sphere.getPosition());
        uiIcon.setTargetPosition(targetPos, true);
        uiIcon.setTargetScale(defaultScale, true);

        closingIcon = selectedIcon;
        selectedIcon = null;
        selectedFishKey = null;
        selectedProfile = null;
        selectedEntry = null;
        nameLabel = null;
        caughtCountLabel = null;
        recordsContainer = null;
        zonesContainer = null;
        statsContainer = null;
        typesContainer = null;
        spawnContainer = null;
        loreContainer = null;
        spawnConditionPipUpdaters.clear();
        zonePipUpdaters.clear();

        // Piggybacks the keyframe-animation system purely for its completion callback — the
        // actual motion above is driven by the exponential setTargetPosition/setTargetScale
        // smoothing, matching how the open animation works, not by this keyframe's value.
        // onComplete fires from inside UIContainer.update()'s loop over root's children
        // (closingIcon is one of them at this point); runDeferred runs the reparenting at the
        // start of the next update() pass instead, same reasoning as goBackToDisc() above.
        uiIcon.playAnimation(new FloatKeyframeAnimation(
                "fishEncyclopediaCloseTravel",
                List.of(new Keyframe(0f, 0f), new Keyframe(CLOSE_TRAVEL_DURATION, 1f)),
                v -> {},
                () -> uiScreen.runDeferred(() -> {
                    // reinstateAfterClose detaches closingIcon from its current parent (root)
                    // and reattaches it to sphere itself, via UIElement.reparentTo — don't
                    // remove it from root here first, or reparentTo loses the parent reference
                    // it needs to read the icon's true on-screen position before moving it.
                    sphere.reinstateAfterClose(closingIcon);
                    closingIcon = null;
                    closing = false;
                })
        ));
    }

    // -------------------------------------------------------------------------
    // Phase 6: info page content & unlock gating

    private void buildInfoPage(float startY) {
        SpriteButton backBtn = UI.spriteButton(40f, 14f, 0xFF666666).text(translated("screen.fishtastic.encyclopedia.back"), 0xFFFFFFFF);
        backBtn.onMouseEnter(e -> backBtn.setTargetScale(1.1f, true));
        backBtn.onMouseExit(e -> backBtn.setTargetScale(1.0f, true));
        backBtn.onClick(e -> goBackToDisc());
        backBtn.setPosition(new Vector2f(12f, 12f));
        root.addChild(backBtn);
        backButton = backBtn;

        float pageWidth = this.width * 0.4f;

        VBox page = UI.vbox().spacing(8).padding(6).alignment(VBox.Alignment.CENTER);

        HBox nameRow = UI.hbox().spacing(4).alignment(HBox.Alignment.CENTER);
        nameLabel = new Label("", 0xFFFFD700).init(tempContext);
        nameLabel.scale(1.2f);
        nameRow.addChild(nameLabel);
        nameClaimButton = buildClaimButton(selectedFishKey.identifier(), EncyclopediaRewardSection.NAME_REVEAL);
        nameRow.addChild(nameClaimButton);
        page.addChild(nameRow);

        caughtCountLabel = new Label("", 0xFFAAAAAA).init(tempContext);
        page.addChild(caughtCountLabel);

        recordsContainer = UI.vbox().spacing(3).padding(4).alignment(VBox.Alignment.CENTER);
        page.addChild(recordsContainer);

        zonesContainer = UI.vbox().spacing(3).padding(4).alignment(VBox.Alignment.CENTER);
        page.addChild(zonesContainer);

        statsContainer = UI.vbox().spacing(3).padding(4).alignment(VBox.Alignment.CENTER);
        page.addChild(statsContainer);

        typesContainer = UI.vbox().spacing(3).padding(4).alignment(VBox.Alignment.CENTER);
        page.addChild(typesContainer);

        spawnContainer = UI.vbox().spacing(3).padding(4).alignment(VBox.Alignment.CENTER);
        page.addChild(spawnContainer);

        loreContainer = UI.vbox().spacing(3).padding(4).alignment(VBox.Alignment.CENTER);
        page.addChild(loreContainer);

        page.scaleToWidth(pageWidth);
        page.setPosition(new Vector2f((this.width - pageWidth) / 2f, startY));
        root.addChild(page);
        infoPageRoot = page;

        refreshInfoPage();
        updateRootContentSize();
    }

    private void refreshInfoPage() {
        if (selectedFishKey == null || selectedProfile == null) return;
        Identifier fishId = selectedFishKey.identifier();
        int catchCount = FishEncyclopediaClientCache.getCatchCount(fishId);
        FishEncyclopediaEntry.UnlockThresholds thresholds = selectedEntry.thresholds();

        boolean nameRevealed = catchCount >= thresholds.nameRevealCatches();
        String displayName = nameRevealed
                ? BuiltInRegistries.ITEM.getOptional(fishId).map(item -> item.getName(new ItemStack(item))).map(Component::getString)
                        .orElseGet(() -> Utility.prettyName(fishId.getPath()))
                : translated("screen.fishtastic.encyclopedia.locked_placeholder");
        nameLabel.text(displayName).color(nameRevealed ? 0xFFFFFFFF : 0xFF888888);
        nameClaimButton.setVisible(nameRevealed && !FishEncyclopediaClientCache.isRewardClaimed(fishId, EncyclopediaRewardSection.NAME_REVEAL));
        caughtCountLabel.text(nameRevealed ? translated("screen.fishtastic.encyclopedia.caught_count", catchCount) : "");
        caughtCountLabel.setVisible(nameRevealed);

        populateRecordsSection(fishId, catchCount);
        zonesContainer.clearChildren();
        zonesContainer.addChild(buildUngatedSection(translated("screen.fishtastic.encyclopedia.section.zones"), () -> buildZonesContent(selectedProfile)));
        statsContainer.clearChildren();
        statsContainer.addChild(buildGatedSection(translated("screen.fishtastic.encyclopedia.section.stats"), catchCount, thresholds.statsCatches(),
                fishId, EncyclopediaRewardSection.STATS, () -> buildStatsContent(selectedProfile)));
        typesContainer.clearChildren();
        typesContainer.addChild(buildGatedSection(translated("screen.fishtastic.encyclopedia.section.types"), catchCount, thresholds.typesCatches(),
                fishId, EncyclopediaRewardSection.TYPES, () -> buildTypesContent(fishId)));
        spawnContainer.clearChildren();
        spawnContainer.addChild(buildGatedSection(translated("screen.fishtastic.encyclopedia.section.spawn_conditions"), catchCount, thresholds.spawnConditionsCatches(),
                fishId, EncyclopediaRewardSection.SPAWN_CONDITIONS, () -> buildSpawnConditionsContent(selectedProfile)));
        loreContainer.clearChildren();
        loreContainer.addChild(buildGatedSection(translated("screen.fishtastic.encyclopedia.section.lore"), catchCount, thresholds.loreCatches(),
                fishId, EncyclopediaRewardSection.LORE, () -> buildLoreContent(selectedEntry)));
    }

    private void populateRecordsSection(Identifier fishId, int catchCount) {
        recordsContainer.clearChildren();
        if (catchCount < 1) return;

        VBox card = UI.vbox().spacing(3).padding(6).alignment(VBox.Alignment.CENTER).backgroundColor(0x55222222);

        LeaderboardEntry personalBest = FishEncyclopediaClientCache.getPersonalBest(fishId);
        if (personalBest != null) {
            card.addChild(label(translated("screen.fishtastic.encyclopedia.your_best",
                    Component.translatable("tooltip.fishtastic.item_size.cm", String.format("%.0f", personalBest.size())),
                    personalBest.quality().getTranslatableName()), 0xFFFFFFFF));
        }
        card.addChild(label(translated("screen.fishtastic.encyclopedia.your_catches", catchCount), 0xFFFFFFFF));

        LeaderboardEntry globalBest = FishEncyclopediaClientCache.getGlobalBest(fishId);
        if (globalBest != null) {
            Component holder = globalBest.playerName().<Component>map(Component::literal)
                    .orElseGet(() -> Component.translatable("screen.fishtastic.encyclopedia.unknown_player"));
            card.addChild(label(translated("screen.fishtastic.encyclopedia.server_best",
                    holder, Component.translatable("tooltip.fishtastic.item_size.cm", String.format("%.0f", globalBest.size()))), 0xFFFFAA00));
        }

        recordsContainer.addChild(card);
    }

    private VBox buildStatsContent(FishProfile profile) {
        VBox content = UI.vbox().spacing(2).alignment(VBox.Alignment.CENTER);
        content.addChild(label(translated("screen.fishtastic.encyclopedia.base_weight", profile.baseWeight()), 0xFFFFFFFF));
        content.addChild(label(translated("screen.fishtastic.encyclopedia.average_size",
                String.format("%.0f", profile.size().mean()), String.format("%.0f", profile.size().stdDev())), 0xFFFFFFFF));
        return content;
    }

    private VBox buildTypesContent(Identifier fishId) {
        VBox content = UI.vbox().spacing(2).alignment(VBox.Alignment.CENTER);
        Item item = BuiltInRegistries.ITEM.getOptional(fishId).orElse(Items.COD);
        List<TagKey<Item>> groups = FishtasticItemTags.FISH_GROUPS.stream()
                .filter(item.builtInRegistryHolder()::is)
                .toList();
        if (groups.isEmpty()) {
            content.addChild(label(translated("screen.fishtastic.encyclopedia.no_bait_affinities"), 0xFF888888));
        } else {
            for (TagKey<Item> group : groups) {
                content.addChild(label(Utility.prettyName(group.location().getPath()), 0xFFCCCCCC));
            }
        }
        return content;
    }

    /**
     * Builds the Zones section content: one column per zone the fish can be caught in, each a
     * largeish {@link ZoneIconRectangle} (same art and outline shader the fishing minigame's HUD
     * zone stack uses) with a status pip + name beneath it — the pip lit green while the player
     * currently stands in that zone, red otherwise, live-updated via {@link #zonePipUpdaters}
     * (see {@link #containerTick}). Ungated (unlike the other info sections below it): a fish's
     * zones are a hard gate on catchability, so the player needs this before their first catch,
     * not as a reward for one.
     */
    private VBox buildZonesContent(FishProfile profile) {
        zonePipUpdaters.clear();
        VBox content = UI.vbox().spacing(2).alignment(VBox.Alignment.CENTER);
        if (profile.zones().isEmpty()) {
            content.addChild(label(translated("screen.fishtastic.encyclopedia.no_zone_restrictions"), 0xFF888888));
            return content;
        }
        HBox row = UI.hbox().spacing(8).alignment(HBox.Alignment.CENTER);
        for (FishProfile.Zone zone : profile.zones()) {
            row.addChild(buildZoneColumn(zone, zonePipUpdaters));
        }
        content.addChild(row);
        return content;
    }

    /** One icon+name column within {@link #buildZonesContent}; skips the icon if {@code zone} has no registered texture. */
    private VBox buildZoneColumn(FishProfile.Zone zone, List<Runnable> pipUpdaters) {
        VBox column = UI.vbox().spacing(2).alignment(VBox.Alignment.CENTER);
        Identifier icon = ZoneIconTextures.get(zone);
        if (icon != null) {
            ZoneIconRectangle iconElement = new ZoneIconRectangle(ZONE_ICON_SIZE, icon, ZoneIconTextures.TEXTURE_PX);
            iconElement.onMouseEnter(e -> iconElement.setTargetScale(ZONE_ICON_HOVER_SCALE, true));
            iconElement.onMouseExit(e -> iconElement.setTargetScale(1.0f, true));
            column.addChild(iconElement);
        }
        ConditionSegment segment = new ConditionSegment(Utility.prettyName(zone.name()), () -> isZoneMet(zone));
        HBox statusRow = UI.hbox().spacing(2).alignment(HBox.Alignment.CENTER);
        SpriteRectangle.SpriteRectangleImpl pip = UI.spriteRectangle(STATUS_PIP_SIZE, STATUS_PIP_SIZE, STATUS_PIP_DEFAULT_TEXTURE)
                .texture(new SpriteData(segment.metCheck().getAsBoolean() ? STATUS_PIP_GREEN_TEXTURE : STATUS_PIP_RED_TEXTURE));
        pipUpdaters.add(() ->
                pip.texture(new SpriteData(segment.metCheck().getAsBoolean() ? STATUS_PIP_GREEN_TEXTURE : STATUS_PIP_RED_TEXTURE)));
        statusRow.addChild(pip);
        statusRow.addChild(label(segment.text(), 0xFF88CCFF));
        column.addChild(statusRow);
        return column;
    }

    private VBox buildSpawnConditionsContent(FishProfile profile) {
        spawnConditionPipUpdaters.clear();
        VBox content = UI.vbox().spacing(2).alignment(VBox.Alignment.CENTER);
        boolean any = false;
        any |= addAxisRows(content, profile.biomeWeights(), FishProfile.BiomeWeight::tier, FishProfile.BiomeWeight::multiplier,
                bw -> Utility.prettyName(bw.pathDisplay()), FishEncyclopediaScreen::isBiomeWeightMet);
        any |= addAxisRows(content, profile.timeWeights(), FishProfile.TimeWeight::tier, FishProfile.TimeWeight::multiplier,
                tw -> Utility.prettyName(tw.time().name()), FishEncyclopediaScreen::isTimeWeightMet);
        any |= addAxisRows(content, profile.weatherWeights(), FishProfile.WeatherWeight::tier, FishProfile.WeatherWeight::multiplier,
                ww -> Utility.prettyName(ww.weather().name()), FishEncyclopediaScreen::isWeatherWeightMet);
        any |= addAxisRows(content, profile.moonWeights(), FishProfile.MoonWeight::tier, FishProfile.MoonWeight::multiplier,
                mw -> Utility.prettyName(mw.phase().name()), FishEncyclopediaScreen::isMoonWeightMet);
        if (!any) {
            content.addChild(label(translated("screen.fishtastic.encyclopedia.no_special_conditions"), 0xFF888888));
        }
        return content;
    }

    /**
     * Adds one row per tier present in {@code weights} for a single axis (biome/time/weather/
     * moon), merging same-tier entries onto a single row — e.g. two PRIMARY biome
     * entries ("Jungle x2.0", "River x1.5") share one row instead of two. Each entry still gets
     * its own status pip within that row (rather than one shared pip for the whole row), since
     * with multiple mutually-exclusive options only one can be active at a time and a single
     * pip can't say which. PRIMARY entries are labelled with their multiplier ("xN.N"),
     * SECONDARY entries with their flat bonus ("+N%"). Returns whether any row was added (used
     * to detect an entirely condition-free profile).
     */
    private <T> boolean addAxisRows(VBox content, List<T> weights, Function<T, FishProfile.ConditionTier> tierGetter,
                                     Function<T, Float> multiplierGetter, Function<T, String> nameGetter, Predicate<T> metCheck) {
        if (weights.isEmpty()) return false;
        for (FishProfile.ConditionTier tier : FishProfile.ConditionTier.values()) {
            List<T> group = weights.stream().filter(w -> tierGetter.apply(w) == tier).toList();
            if (group.isEmpty()) continue;
            List<ConditionSegment> segments = group.stream()
                    .map(w -> new ConditionSegment(
                            nameGetter.apply(w) + " (" + formatMultiplier(tier, multiplierGetter.apply(w)) + ")",
                            () -> metCheck.test(w)))
                    .toList();
            content.addChild(buildSpawnConditionRow(segments, spawnConditionPipUpdaters, 0xFFCCCCCC, STATUS_PIP_DEFAULT_TEXTURE));
        }
        return true;
    }

    private static String formatMultiplier(FishProfile.ConditionTier tier, float multiplier) {
        return tier == FishProfile.ConditionTier.SECONDARY
                ? String.format("%+.0f%%", (multiplier - 1.0f) * 100.0f)
                : "x" + multiplier;
    }

    private record ConditionSegment(String text, BooleanSupplier metCheck) {}

    /**
     * Builds a single condition row from one or more segments, each rendered as its own
     * status pip (lit green while that segment's condition currently holds for the client
     * player, {@code unmetTexture} otherwise) followed by its label text. Registers a live-update
     * callback per pip into {@code pipUpdaters} so it tracks its segment every tick — see
     * {@link #containerTick}. Callers pick which list (spawn-condition rows vs. the always-
     * visible Types zone rows use separate lists with separate clear lifecycles), a text color so
     * different row categories read as visually distinct at a glance, and an unmet-state texture —
     * red for Types zone rows, since a fish's zones are a hard gate on catchability (see
     * {@link FishProfile#zones}) rather than the soft multipliers the other axes represent.
     */
    private HBox buildSpawnConditionRow(List<ConditionSegment> segments, List<Runnable> pipUpdaters, int textColor, Identifier unmetTexture) {
        HBox row = UI.hbox().spacing(2).alignment(HBox.Alignment.CENTER);
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) row.addChild(label(",", textColor));
            ConditionSegment segment = segments.get(i);
            SpriteRectangle.SpriteRectangleImpl pip = UI.spriteRectangle(STATUS_PIP_SIZE, STATUS_PIP_SIZE, STATUS_PIP_DEFAULT_TEXTURE)
                    .texture(new SpriteData(segment.metCheck().getAsBoolean() ? STATUS_PIP_GREEN_TEXTURE : unmetTexture));
            pipUpdaters.add(() ->
                    pip.texture(new SpriteData(segment.metCheck().getAsBoolean() ? STATUS_PIP_GREEN_TEXTURE : unmetTexture)));
            row.addChild(pip);
            row.addChild(label(segment.text(), textColor));
        }
        return row;
    }

    private static boolean isBiomeWeightMet(FishProfile.BiomeWeight bw) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;
        return bw.biome().contains(mc.level.getBiome(mc.player.blockPosition()));
    }

    private static boolean isTimeWeightMet(FishProfile.TimeWeight tw) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        return getEffectiveTimeOfDay(mc) == tw.time();
    }

    /**
     * Time of day used for spawn-condition evaluation, accounting for charm overrides
     * (e.g. luna charm forcing night) the same way {@link FishingMinigameManager} does.
     */
    private static FishProfile.TimeOfDay getEffectiveTimeOfDay(Minecraft mc) {
        CharmEffect charmEffect = getEquippedCharmEffect();
        return (charmEffect != null && charmEffect.forceNightFishing())
                ? FishProfile.TimeOfDay.NIGHT
                : FishProfile.TimeOfDay.fromGameTime(mc.level.getOverworldClockTime());
    }

    /**
     * Charm equipped in the player's held fishing rod's charm slot, if any — mirrors the
     * rod lookup in {@link FishingMinigameClientHandler} so pips reflect the same charm
     * overrides (e.g. luna charm forcing night) that the server minigame applies.
     */
    private static CharmEffect getEquippedCharmEffect() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        ItemStack rod = mc.player.getMainHandItem();
        if (!rod.is(FishtasticItems.COPPER_FISHING_ROD)) {
            rod = mc.player.getOffhandItem();
        }
        if (!rod.is(FishtasticItems.COPPER_FISHING_ROD)) return null;
        ItemStack charm = CopperFishingRod.getCharm(rod);
        if (charm.isEmpty()) return null;
        return charm.get(FishtasticDataComponents.CHARM_EFFECT.value());
    }

    private static boolean isWeatherWeightMet(FishProfile.WeatherWeight ww) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;
        return FishProfile.WeatherCondition.fromLevel(mc.level, mc.player.blockPosition()) == ww.weather();
    }

    private static boolean isMoonWeightMet(FishProfile.MoonWeight mw) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;
        // Moon is only visible at night under clear skies — mirrors the same gate applied
        // in FishProfile.computeEnvironmentMultiplier.
        boolean isNight = getEffectiveTimeOfDay(mc) == FishProfile.TimeOfDay.NIGHT;
        boolean isClear = FishProfile.WeatherCondition.fromLevel(mc.level, mc.player.blockPosition()) == FishProfile.WeatherCondition.CLEAR;
        if (!isNight || !isClear) return false;
        return mc.level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.MOON_PHASE, mc.player.blockPosition()) == mw.phase();
    }

    private static boolean isZoneMet(FishProfile.Zone zone) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;
        BlockPos pos = mc.player.blockPosition();
        return FishProfile.Zone.resolve(mc.level.getBiome(pos), pos.getY(), mc.level.getSeaLevel()).contains(zone);
    }

    private VBox buildLoreContent(FishEncyclopediaEntry entry) {
        VBox content = UI.vbox().spacing(2).padding(4).alignment(VBox.Alignment.CENTER).backgroundColor(0x44222222);
        String lore = entry.lore().orElseGet(() -> translated("screen.fishtastic.encyclopedia.no_lore"));
        content.addChild(new Label(lore, 0xFFDDDDDD).maxWidth(150).centered(true).init(tempContext));
        return content;
    }

    /**
     * Renders {@code contentBuilder}'s content under a title, with no catch-count gate.
     * Used for sections that tell the player how to catch a fish (e.g. Types) — that
     * information must be available before the grind it would shorten, not after.
     */
    private VBox buildUngatedSection(String title, Supplier<VBox> contentBuilder) {
        VBox section = UI.vbox().spacing(3).alignment(VBox.Alignment.CENTER);
        section.addChild(new Label(title, 0xFFFFD700).init(tempContext));
        section.addChild(contentBuilder.get());
        return section;
    }

    /**
     * Renders {@code unlockedContentBuilder}'s content if unlocked, else a ghost row with progress bar.
     * Adds a claim button next to the title while unlocked but unclaimed.
     */
    private VBox buildGatedSection(String title, int currentCatches, int targetCatches, Identifier fishId,
                                    EncyclopediaRewardSection rewardSection, Supplier<VBox> unlockedContentBuilder) {
        VBox section = UI.vbox().spacing(3).alignment(VBox.Alignment.CENTER);
        boolean unlocked = currentCatches >= targetCatches;

        if (unlocked && !FishEncyclopediaClientCache.isRewardClaimed(fishId, rewardSection)) {
            HBox titleRow = UI.hbox().spacing(4).alignment(HBox.Alignment.CENTER);
            titleRow.addChild(new Label(title, 0xFFFFD700).init(tempContext));
            titleRow.addChild(buildClaimButton(fishId, rewardSection));
            section.addChild(titleRow);
        } else {
            section.addChild(new Label(title, 0xFFFFD700).init(tempContext));
        }

        if (unlocked) {
            section.addChild(unlockedContentBuilder.get());
        } else {
            section.addChild(label(translated("screen.fishtastic.encyclopedia.locked_placeholder"), 0xFF666666));
            SpriteProgressBar bar = UI.progressBar();
            float fraction = targetCatches > 0 ? Math.min(1f, (float) currentCatches / targetCatches) : 0f;
            bar.progressImmediate(fraction);
            section.addChild(bar);
            section.addChild(label(translated("screen.fishtastic.encyclopedia.unlock_progress",
                    Math.max(0, targetCatches - currentCatches), title, currentCatches, targetCatches), 0xFF888888));
        }
        return section;
    }

    /** Claim button for one fish's reward slot; disappearance is driven by the next server sync, not optimistically. */
    private SpriteButton buildClaimButton(Identifier fishId, EncyclopediaRewardSection rewardSection) {
        CoinIcon icon = coinIconFor(rewardSection.coinReward());
        float btnWidth = icon.width() + CLAIM_BUTTON_ICON_PADDING * 2f;
        float btnHeight = icon.height() + CLAIM_BUTTON_ICON_PADDING * 2f;
        CoinClaimButton btn = new CoinClaimButton(btnWidth, btnHeight, icon);
        btn.texture(claimButtonSprite());
        btn.onMouseEnter(e -> btn.setTargetScale(1.15f, true));
        btn.onMouseExit(e -> btn.setTargetScale(1.0f, true));
        btn.onClick(e -> {
            btn.addClickBounceEffect();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.connection.send(new ServerboundCustomPayloadPacket(new ClaimEncyclopediaRewardPacket(fishId, rewardSection)));
            }
            playRewardClaimFlourish(btn);
        });
        return btn;
    }

    private SpriteData claimButtonSprite() {
        return new SpriteData(CLAIM_BUTTON_TEXTURE)
                .uv(0, 0, CLAIM_BUTTON_SOURCE_WIDTH, CLAIM_BUTTON_SOURCE_HEIGHT)
                .textureSize(CLAIM_BUTTON_SOURCE_WIDTH, CLAIM_BUTTON_SOURCE_HEIGHT)
                .renderMode(SpriteRenderMode.SLICE)
                .slice(CLAIM_BUTTON_SLICE_LEFT, CLAIM_BUTTON_SLICE_RIGHT, CLAIM_BUTTON_SLICE_TOP, CLAIM_BUTTON_SLICE_BOTTOM);
    }

    private static CoinIcon coinIconFor(int coinReward) {
        return switch (coinReward) {
            case 1 -> COIN_ICON_ONE;
            case 2 -> COIN_ICON_TWO;
            default -> COIN_ICON_THREE;
        };
    }

    /** A {@link SpriteButton} that blits a coin-count icon over its background instead of "+N" text. */
    private static class CoinClaimButton extends SpriteButton {
        private final CoinIcon icon;

        CoinClaimButton(float width, float height, CoinIcon icon) {
            super(width, height, CLAIM_BUTTON_TEXTURE);
            this.icon = icon;
        }

        @Override
        protected void renderSelf(IRenderContext context) {
            super.renderSelf(context);
            context.drawSprite(new SpriteData(icon.texture()),
                    CLAIM_BUTTON_ICON_PADDING, CLAIM_BUTTON_ICON_PADDING,
                    (int) icon.width(), (int) icon.height());
        }
    }

    /** A single mini coin arcing up and fading away from {@code origin}; a smaller {@link QuestLogScreen} claim burst. */
    private void playRewardClaimFlourish(UIElement<?> origin) {
        if (rewardFlyOverlay == null) return;

        Rectangle2D originBounds = origin.getBounds();
        float originCx = (float) (originBounds.getX() + originBounds.getWidth() / 2);
        float originCy = (float) (originBounds.getY() + originBounds.getHeight() / 2);

        Vector2f overlayGlobalPos = rewardFlyOverlay.getGlobalPosition();
        float overlayScale = Math.max(0.0001f, rewardFlyOverlay.getGlobalScale());
        float startX = (originCx - overlayGlobalPos.x) / overlayScale;
        float startY = (originCy - overlayGlobalPos.y) / overlayScale;

        ItemRenderer.ItemRendererImpl coin = UI.itemRenderer(new ItemStack(FishtasticItems.QUEST_TOKEN.value()));
        coin.showCount(false);
        rewardFlyOverlay.addChildAt(coin, startX, startY);

        float duration = 0.6f;
        coin.addEffect(new CoinArcEffect("encyclopedia-reward-coin", 0, duration)
                .setDisplacement(0f, -24f)
                .setArcHeight(8f)
                .setShrink(0.5f, 0.1f));
        coin.playAnimation(new FloatKeyframeAnimation(
                "encyclopedia-reward-coin-life",
                List.of(new Keyframe(0f, 0f), new Keyframe(duration, 1f)),
                v -> {},
                () -> uiScreen.runDeferred(() -> rewardFlyOverlay.removeChild(coin))));
    }

    // -------------------------------------------------------------------------
    // Helpers

    private Label label(String text, int color) {
        return new Label(text, color).init(tempContext);
    }

    private static String translated(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

}
