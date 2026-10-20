package grill24.fishtastic.client;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.client.effects.CoinArcEffect;
import grill24.fishtastic.client.encyclopedia.EncyclopediaCoins;
import grill24.fishtastic.client.encyclopedia.EnvSnapshot;
import grill24.fishtastic.client.encyclopedia.FishPageModel;
import grill24.fishtastic.client.encyclopedia.page.CompactPageLayout;
import grill24.fishtastic.client.encyclopedia.page.EncyclopediaPageLayout;
import grill24.fishtastic.client.encyclopedia.page.HitArea;
import grill24.fishtastic.client.encyclopedia.page.PageSurface;
import grill24.fishtastic.client.encyclopedia.page.ScaledTooltip;
import grill24.fishtastic.client.encyclopedia.portrait.Portrait;
import grill24.fishtastic.companion.CompanionBondUnlocks;
import grill24.fishtastic.companion.CompanionParty;
import grill24.fishtastic.companion.CompanionStorage;
import grill24.fishtastic.data.EncyclopediaRewardSection;
import grill24.fishtastic.data.EncyclopediaTiers;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.network.ClaimAllEncyclopediaRewardsPacket;
import grill24.fishtastic.network.FishEncyclopediaSyncPacket;
import grill24.fishtastic.network.LeaderboardEntry;
import io.github.currenj.gelatinui.GelatinUIScreen;
import io.github.currenj.gelatinui.gui.GelatinMenu;
import io.github.currenj.gelatinui.gui.IUIElement;
import io.github.currenj.gelatinui.gui.UI;
import io.github.currenj.gelatinui.gui.UIElement;
import io.github.currenj.gelatinui.gui.animation.FloatKeyframeAnimation;
import io.github.currenj.gelatinui.gui.animation.Easing;
import io.github.currenj.gelatinui.gui.animation.Keyframe;
import io.github.currenj.gelatinui.gui.components.ItemRenderer;
import io.github.currenj.gelatinui.gui.components.ManualContainer;
import io.github.currenj.gelatinui.gui.components.Rectangle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import grill24.fishtastic.network.NetworkApiSided;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
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
import java.util.Optional;

/**
 * Fish encyclopedia: a home screen disc of every fish (silhouettes for never-caught fish).
 * Clicking one flies that same icon into the portrait of a fixed 416x232 field-guide page
 * (docs/encyclopedia-redesign/README.md): specimen on the left, a live "How to catch" guide on the
 * right, one unlock track and one Claim button along the bottom, prev/next browsing. The page is
 * drawn on an integer-scaled canvas ({@code k = max(1, min(W/416, H/232))}) so text stays crisp;
 * a compact 300x228 card is used when the full spread cannot fit. See {@code gelatin-spike.md}
 * for the six gelatin-ui rules this follows.
 */
public class FishEncyclopediaScreen extends GelatinUIScreen<GelatinMenu> {
    private static final float DISC_RADIUS = 70f;
    private static final float DISC_SIZE = DISC_RADIUS * 2f + 32f;
    private static final float SCREEN_FADE_DURATION = 0.2f;

    /** The icon travels for this long and reaches the exact slot / scale on the final frame. */
    private static final float FLIGHT_SNAP_SECONDS = 0.45f;
    private static final float PAGE_FADE_SECONDS = 0.2f;
    private static final float BROWSE_SECONDS = 0.15f;
    private static final float BROWSE_SLIDE = 24f;
    private static final int CURTAIN_RGB = 0x101014;
    private static final float BACKDROP_ALPHA = 0.93f;

    /** Where the portrait's fish slot sits on the canvas, and the icon's local scale that fills it. */
    private static final Vector2f SLOT = new Vector2f(
            EncyclopediaPageLayout.PLATE_X + Portrait.FISH_SLOT_X, EncyclopediaPageLayout.PLATE_Y + Portrait.FISH_SLOT_Y);
    private static final float SLOT_SCALE = Portrait.FISH_SLOT_SIZE / 16f;

    // Staggers unclaimed-reward pip pop-ins across the disc instead of all popping at once.
    private static final float REWARD_PIP_POP_INITIAL_DELAY = 1.2f;
    private static final float REWARD_PIP_POP_STAGGER = 0.1f;

    private static final int LIVE_REFRESH_TICKS = 5;

    private net.minecraft.client.gui.components.Button dismissHint;
    private UIElement<?> companionEntryButton;

    private boolean closingScreen = false;
    private long closingAtNanos = -1L;

    private java.util.Set<ResourceLocation> lastSeenUnlocks = java.util.Set.of();

    // Set by another screen (e.g. QuestLogScreen's tag checklist) right before it requests the
    // encyclopedia menu reopen; consumed once by the next buildUI() to jump straight to that
    // fish's info page instead of landing on the disc. Static because the request round-trips
    // through the server (a fresh menu open), so no live screen instance exists to hand this to
    // directly at the point the request is made.
    private static ResourceLocation pendingAutoSelectFishId;

    /** See {@link #pendingAutoSelectFishId}. */
    public static void selectOnNextOpen(ResourceLocation fishId) {
        pendingAutoSelectFishId = fishId;
    }

    private boolean handlerInstalled = false;
    private FishEncyclopediaSyncPacket.ClientHandler savedHandler;

    // ----- disc
    private FreeformContainer root;
    private FishSphereContainer sphere;
    // Zero-size overlay hosting the reward coin-fly flourish; kept above the page.
    private ManualContainer rewardFlyOverlay;
    private FreeformContainer claimAllGroup;
    private final Map<ResourceKey<FishProfile>, SilhouetteItemButton> iconRefs = new LinkedHashMap<>();
    private final List<ResourceKey<FishProfile>> discOrder = new ArrayList<>();
    private boolean deepLinkOpen;

    // ----- info page: non-null / true only while it is showing
    private boolean pageOpen;
    /** True from the moment the page starts closing until the icon is back in the disc. */
    private boolean closing;
    private boolean browsing;
    private ResourceKey<FishProfile> shownKey;
    private SilhouetteItemButton shownIcon;
    private FishPageModel model;
    private FishProfile profile;
    private EncyclopediaPageLayout.Tab tab = EncyclopediaPageLayout.Tab.CATCH;
    private boolean compactPage;
    private int k = 1;
    private int canvasX, canvasY;
    private FreeformContainer canvas, contentHost, backHost, iconLayer, frontHost;
    private Rectangle curtain;
    private Portrait portrait;
    private EnvSnapshot lastEnv;
    private String lastBondSignature = "";
    private int pollTicks;
    // The icon sliding out during a browse; parked back into the disc when the slide ends.
    private SilhouetteItemButton leavingIcon;
    private ResourceKey<FishProfile> leavingKey;

    // Dark backdrop behind the page, so the HUD does not show through; eased in and out.
    private float backdropFrom, backdropTo;
    private long backdropStartNanos;
    private float backdropSeconds;

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
        dismissHint = addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                Component.translatable("tutorial.fishtastic.dismiss"), b -> EncyclopediaTutorialClientHandler.dismiss())
                .bounds(width - 112, height - 28, 104, 20).build());
        dismissHint.visible = EncyclopediaTutorialClientHandler.hasActiveLesson();
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
        EncyclopediaTutorialClientHandler.dismiss();
        closingScreen = true;
        closingAtNanos = System.nanoTime();
        if (sphere != null) sphere.startOutroAnimation();
        // Actual close is deferred to tick() so the animation can play first.
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pageOpen && !closing) {
            // Escape returns to the disc before it closes the screen; arrows browse.
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closePage();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                browse(-1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                browse(1);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderTransparentBackground(GuiGraphics graphics) {
        if (!closingScreen) {
            super.renderTransparentBackground(graphics);
            drawPageBackdrop(graphics, 1f);
            return;
        }
        float elapsed = (System.nanoTime() - closingAtNanos) / 1_000_000_000f;
        float alpha = Math.max(0f, 1f - elapsed / SCREEN_FADE_DURATION);
        int a1 = (int) (0xC0 * alpha);
        int a2 = (int) (0xD0 * alpha);
        graphics.fillGradient(0, 0, this.width, this.height,
                (a1 << 24) | 0x101010,
                (a2 << 24) | 0x101010);
        drawPageBackdrop(graphics, alpha);
        if (elapsed >= SCREEN_FADE_DURATION) {
            closingScreen = false;
            closingAtNanos = -1L;
            super.onClose();
        }
    }

    private void drawPageBackdrop(GuiGraphics graphics, float factor) {
        float a = backdropAlpha() * factor;
        if (a <= 0.003f) return;
        graphics.fill(0, 0, this.width, this.height, ((int) (a * 255) << 24) | 0x0C0C10);
    }

    private float backdropAlpha() {
        if (backdropSeconds <= 0f) return backdropTo;
        float t = (System.nanoTime() - backdropStartNanos) / 1_000_000_000f / backdropSeconds;
        if (t >= 1f) return backdropTo;
        return backdropFrom + (backdropTo - backdropFrom) * Math.max(0f, t);
    }

    private void fadeBackdropTo(float target, float seconds) {
        backdropFrom = backdropAlpha();
        backdropTo = target;
        backdropStartNanos = System.nanoTime();
        backdropSeconds = seconds;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (dismissHint != null) dismissHint.visible = EncyclopediaTutorialClientHandler.hasActiveLesson();
        if (!lastSeenUnlocks.equals(QuestClientCache.getUnlockedCompanions())) {
            lastSeenUnlocks = QuestClientCache.getUnlockedCompanions();
            refreshFromCache();
        }
        // The verdict and the "now" outlines follow time, weather and position (~4x a second).
        if (pageOpen && !closing && ++pollTicks >= LIVE_REFRESH_TICKS) {
            pollTicks = 0;
            Minecraft mc = Minecraft.getInstance();
            EncyclopediaPageLayout.Bond bond = bondInfo(mc);
            String signature = bond == null ? "" : bond.text() + "|" + bond.unlocked();
            if (!java.util.Objects.equals(EnvSnapshot.fromClient(), lastEnv) || !signature.equals(lastBondSignature)) {
                rebuildContent();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Home screen (disc)

    @Override
    protected void buildUI() {
        iconRefs.clear();
        discOrder.clear();
        pageOpen = false;
        closing = false;
        browsing = false;
        shownKey = null;
        shownIcon = null;
        model = null;
        profile = null;
        compactPage = false;
        canvas = null;
        contentHost = backHost = iconLayer = frontHost = null;
        curtain = null;
        portrait = null;
        leavingIcon = null;
        leavingKey = null;
        companionEntryButton = null;
        claimAllGroup = null;
        lastEnv = null;
        lastBondSignature = "";
        backdropFrom = backdropTo = 0f;
        backdropSeconds = 0f;
        lastSeenUnlocks = QuestClientCache.getUnlockedCompanions();

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
                Item item = BuiltInRegistries.ITEM.getOptional(fishKey.location()).orElse(Items.COD);
                boolean caught = FishEncyclopediaClientCache.getCatchCount(fishKey.location()) > 0;

                // Unlisted fish (secret one-off variants) don't even get a silhouette slot —
                // they're absent from the disc entirely until the player has caught one.
                if (!caught && item.builtInRegistryHolder().is(FishtasticItemTags.UNLISTED_FISH)) continue;

                SilhouetteItemButton icon = new SilhouetteItemButton(new ItemStack(item));
                syncIcon(fishKey, icon);
                iconItems.add(Map.entry(fishKey, icon));
                iconRefs.put(fishKey, icon);
                discOrder.add(fishKey);
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

        rebuildClaimAll();

        ResourceLocation autoSelectFishId = pendingAutoSelectFishId;
        pendingAutoSelectFishId = null;
        if (autoSelectFishId != null) {
            // Opening straight onto a page (Quest Log link): there is no click and no disc on
            // screen to fly from, so the fish simply starts in the portrait slot.
            SilhouetteItemButton icon = iconRefs.get(ResourceKey.create(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, autoSelectFishId));
            if (icon != null) {
                deepLinkOpen = true;
                sphere.selectChild(icon);
                deepLinkOpen = false;
            }
        }
    }

    /** Uses the element's live bounds, including layout/animation transforms. */
    public void highlightCompanionEntryButton(net.minecraft.client.gui.GuiGraphics graphics) {
        if (!hasCompanionEntryButton()) return;
        var bounds = companionEntryButton.getBounds();
        graphics.renderOutline((int) bounds.getX() - 2, (int) bounds.getY() - 2,
                (int) bounds.getWidth() + 4, (int) bounds.getHeight() + 4, 0xFFFFDD44);
    }

    public boolean hasCompanionEntryButton() {
        return pageOpen && companionEntryButton != null;
    }

    /** Re-reads every icon (and the info page) from the client cache, e.g. after a sync. */
    private void refreshFromCache() {
        for (Map.Entry<ResourceKey<FishProfile>, SilhouetteItemButton> entry : iconRefs.entrySet()) {
            syncIcon(entry.getKey(), entry.getValue());
        }
        rebuildClaimAll();
        if (pageOpen && shownKey != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                FishPageModel fresh = FishPageModel.build(mc.level.registryAccess(), shownKey);
                if (fresh != null) {
                    model = fresh;
                    rebuildContent();
                }
            }
        }
    }

    /**
     * Brings one disc icon in line with the cache. The icon currently on the page carries no
     * badges (a pip or heart would be drawn at 4x on the portrait).
     */
    private void syncIcon(ResourceKey<FishProfile> fishKey, SilhouetteItemButton icon) {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation fishId = fishKey.location();
        icon.setSilhouette(FishEncyclopediaClientCache.getCatchCount(fishId) <= 0);
        boolean onPage = pageOpen && icon == shownIcon;
        Item species = BuiltInRegistries.ITEM.getOptional(fishId).orElse(null);
        icon.setBondAvailable(!onPage && species != null && mc.player != null
                && QuestClientCache.isBondUnlocked(fishId) && !CarriedFish.groupsOf(mc.player, species).isEmpty());
        icon.setHasUnclaimedReward(!onPage && mc.level != null
                && FishEncyclopediaClientHelper.fishHasUnclaimedReward(mc.level.registryAccess(), fishKey, fishId));
    }

    /** Staggers reward-pip and bond-heart pop-ins across the disc, in icon order. Called once on screen open only. */
    private void scheduleRewardPipPopIns() {
        float delay = REWARD_PIP_POP_INITIAL_DELAY;
        for (SilhouetteItemButton icon : iconRefs.values()) {
            if (!icon.hasBadge()) continue;
            icon.playPipPopIn(delay);
            delay += REWARD_PIP_POP_STAGGER;
        }
    }

    // -------------------------------------------------------------------------
    // Disc: Claim all

    private static int unclaimedCoins(RegistryAccess access, ResourceKey<FishProfile> key) {
        var thresholds = EncyclopediaTiers.thresholdsFor(access, key);
        int catches = FishEncyclopediaClientCache.getCatchCount(key.location());
        int coins = 0;
        for (EncyclopediaRewardSection section : EncyclopediaRewardSection.values()) {
            if (catches >= section.threshold(thresholds) && !FishEncyclopediaClientCache.isRewardClaimed(key.location(), section)) {
                coins += section.coinReward();
            }
        }
        return coins;
    }

    /** (Re)builds the green "Claim all N" button under the disc; absent while nothing is claimable. */
    private void rebuildClaimAll() {
        if (root == null || sphere == null) return;
        if (claimAllGroup != null) {
            root.removeChild(claimAllGroup);
            claimAllGroup = null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int coins = 0, fish = 0;
        for (ResourceKey<FishProfile> key : iconRefs.keySet()) {
            int c = unclaimedCoins(mc.level.registryAccess(), key);
            if (c > 0) {
                coins += c;
                fish++;
            }
        }
        if (coins <= 0) return;

        String label = translated("screen.fishtastic.encyclopedia.claim_all", coins);
        String sub = translated("screen.fishtastic.encyclopedia.claim_all_sub", fish);
        int bw = 6 + PageSurface.width(label) + 3 + 16 + 5, bh = 20;
        int gw = Math.max(bw, PageSurface.width(sub)), gh = bh + 3 + 9;
        int bx = (gw - bw) / 2;

        PageSurface surface = new PageSurface(gw, gh);
        surface.panel(PageSurface.PANEL_GREEN, bx, 0, bw, bh);
        surface.text(bx + 6, (bh - 8) / 2, label, 0xFFFFFF);
        surface.textCentered(gw / 2, bh + 3, sub, 0x8C7F7A);

        FreeformContainer group = new FreeformContainer();
        group.setSize(gw, gh);
        group.addChild(surface);
        UIElement<?> coin = EncyclopediaCoins.icon(coins);
        coin.setPosition(new Vector2f(bx + bw - 5 - 16, (bh - 16) / 2f));
        group.addChild(coin);
        HitArea hit = new HitArea(bw, bh, true);
        hit.tip(uiScreen, ScaledTooltip.of(1, ScaledTooltip.line(translated("tutorial.fishtastic.rewards.help"), 0xFFFFFF)));
        hit.click(() -> {
            sendClaimAll(Optional.empty());
            playRewardClaimFlourish(hit);
        });
        hit.setPosition(new Vector2f(bx, 0));
        group.addChild(hit);

        group.setPosition(new Vector2f(Math.round((this.width - gw) / 2f), Math.round(sphere.getPosition().y + DISC_SIZE - 4f)));
        root.addChild(group);
        claimAllGroup = group;
        group.setVisible(!pageOpen);
        bringOverlayToFront();
    }

    private void sendClaimAll(Optional<ResourceLocation> fishId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            NetworkApiSided.sendToServer(new ClaimAllEncyclopediaRewardsPacket(fishId), ClaimAllEncyclopediaRewardsPacket.STREAM_CODEC);
        }
    }

    private void bringOverlayToFront() {
        if (rewardFlyOverlay == null || root == null) return;
        root.removeChild(rewardFlyOverlay);
        root.addChild(rewardFlyOverlay);
    }

    // -------------------------------------------------------------------------
    // Selection: the clicked icon flies into the portrait slot

    private void onFishSelected(IUIElement icon, ResourceKey<FishProfile> fishKey) {
        if (fishKey == null || !(icon instanceof SilhouetteItemButton button)) return;
        Minecraft mc = Minecraft.getInstance();
        FishPageModel built = mc.level == null ? null : FishPageModel.build(mc.level.registryAccess(), fishKey);
        if (built == null) {
            // No profile to show: put the disc back rather than leave it shrunk with no page.
            sphere.reinstateAfterClose(icon);
            return;
        }
        EncyclopediaTutorialClientHandler.onFishClicked();
        openPage(fishKey, button, built, deepLinkOpen);
    }

    private void openPage(ResourceKey<FishProfile> key, SilhouetteItemButton icon, FishPageModel built, boolean instant) {
        Minecraft mc = Minecraft.getInstance();
        shownKey = key;
        shownIcon = icon;
        model = built;
        profile = mc.level.registryAccess().registryOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY).getOptional(key).orElse(null);
        tab = EncyclopediaPageLayout.Tab.CATCH;
        pageOpen = true;
        closing = false;
        browsing = false;
        pollTicks = 0;

        // Integer scale only. A narrow screen gets the approved compact card instead of a clipped
        // two-page spread; larger screens keep the full field-guide layout.
        compactPage = this.width < EncyclopediaPageLayout.W || this.height < EncyclopediaPageLayout.H;
        int pageW = pageWidth(), pageH = pageHeight();
        k = compactPage ? 1 : Math.max(1, Math.min(this.width / pageW, this.height / pageH));
        int cw = pageW * k, ch = pageH * k;
        canvasX = Math.max(0, (this.width - cw) / 2);
        canvasY = Math.max(0, (this.height - ch) / 2);
        boolean fits = this.width >= cw && this.height >= ch;
        root.setSize(this.width, fits ? this.height : Math.max(this.height, canvasY + ch + 4));
        uiScreen.setScrollEnabled(!fits);
        uiScreen.setScrollY(0f);

        canvas = new FreeformContainer();
        canvas.setSize(pageW, pageH);
        canvas.setTargetScale(k, false);   // never scale(k): that one animates (spike rule 1)
        canvas.setPosition(new Vector2f(canvasX, canvasY));
        contentHost = host();
        backHost = host();
        iconLayer = host();
        frontHost = host();
        // Z-order: page content < portrait plate < (fade curtain) < the fish icon < front bubbles + toggle.
        canvas.addChild(contentHost);
        canvas.addChild(backHost);
        curtain = null;
        if (!instant) {
            addCurtain(PAGE_FADE_SECONDS, 1f);
        }
        canvas.addChild(iconLayer);
        canvas.addChild(frontHost);
        root.addChild(canvas);
        bringOverlayToFront();
        if (claimAllGroup != null) claimAllGroup.setVisible(false);
        if (dismissHint != null) dismissHint.visible = EncyclopediaTutorialClientHandler.hasActiveLesson();

        buildPortrait();
        rebuildContent();
        fadeBackdropTo(BACKDROP_ALPHA, instant ? 0f : 0.12f);

        icon.setHasUnclaimedReward(false);
        icon.setBondAvailable(false);
        adoptIcon(icon);
        if (instant) {
            land(icon);
        } else {
            animateIconTo(icon, slot(), slotScale(), FLIGHT_SNAP_SECONDS, "fishEncyclopediaTravel",
                    () -> uiScreen.runDeferred(() -> land(icon)));
        }
    }

    private int pageWidth() {
        return compactPage ? CompactPageLayout.W : EncyclopediaPageLayout.W;
    }

    private int pageHeight() {
        return compactPage ? CompactPageLayout.H : EncyclopediaPageLayout.H;
    }

    private Vector2f slot() {
        return compactPage
                ? new Vector2f(CompactPageLayout.FISH_X, CompactPageLayout.FISH_Y)
                : new Vector2f(SLOT);
    }

    private float slotScale() {
        return compactPage ? CompactPageLayout.FISH_SCALE : SLOT_SCALE;
    }

    private FreeformContainer host() {
        FreeformContainer host = new FreeformContainer();
        host.setSize(pageWidth(), pageHeight());
        return host;
    }

    /**
     * Moves a disc icon into the icon layer keeping its on-screen position AND size: reparenting
     * preserves position but not scale, so the local scale is rewritten from the global one before
     * (gelatin-spike rule 5).
     */
    private void adoptIcon(SilhouetteItemButton icon) {
        float globalScale = icon.getGlobalScale();
        icon.clearAllActions();
        icon.reparentTo(iconLayer, true);
        icon.setTargetScale(globalScale / iconLayer.getGlobalScale(), false);
        icon.setVisible(true);
    }

    /** Snaps the icon to the exact slot and integer scale (spike rule 6). */
    private void land(SilhouetteItemButton icon) {
        if (!pageOpen || icon != shownIcon || iconLayer == null) return;
        icon.setTargetPosition(slot(), false);
        icon.setTargetScale(slotScale(), false);
    }

    /**
     * Drives an icon's position and scale from their current values to exact final values.
     *
     * <p>{@link UIElement#setTargetPosition(Vector2f, boolean)} uses exponential smoothing, so
     * it is still a small distance from its target when a fixed-duration travel animation ends.
     * Applying the final transform from the keyframe itself keeps the endpoint continuous while
     * still preserving the exact pixel-aligned landing values.
     */
    private void animateIconTo(SilhouetteItemButton icon, Vector2f targetPosition, float targetScale,
                               float duration, String channel, Runnable onComplete) {
        Vector2f startPosition = icon.getPosition();
        float startScale = icon.getCurrentScale();
        Vector2f endPosition = new Vector2f(targetPosition);

        // Freeze the interpolation targets at the current transform. The keyframe callback below
        // owns both properties for the duration, so UIElement.animate() cannot move them first.
        icon.setTargetPosition(startPosition, false);
        icon.setTargetScale(startScale, false);
        icon.playAnimation(new FloatKeyframeAnimation(channel,
                List.of(new Keyframe(0f, 0f),
                        new Keyframe(duration, 1f, Easing.EASE_OUT_CUBIC)),
                progress -> {
                    icon.setPosition(new Vector2f(startPosition).lerp(endPosition, progress));
                    icon.setTargetScale(startScale + (targetScale - startScale) * progress, false);
                },
                onComplete));
    }

    /** A full-canvas cover in the backdrop colour that fades away; removed at the end so it never eats clicks. */
    private void addCurtain(float seconds, float startAlpha) {
        dropCurtain();
        Rectangle cover = new Rectangle(pageWidth(), pageHeight(), argb(startAlpha));
        curtain = cover;
        // Curtain sits between the plate (backHost) and the icon layer.
        int index = canvas.getChildren().indexOf(backHost) + 1;
        List<IUIElement> above = new ArrayList<>(canvas.getChildren().subList(index, canvas.getChildCount()));
        for (IUIElement element : above) canvas.removeChild(element);
        canvas.addChild(cover);
        for (IUIElement element : above) canvas.addChild(element);
        cover.playAnimation(new FloatKeyframeAnimation("fishEncyclopediaPageFade",
                List.of(new Keyframe(0f, 0f), new Keyframe(seconds, 1f)),
                v -> cover.color(argb(startAlpha * (1f - v))),
                () -> uiScreen.runDeferred(() -> {
                    if (curtain == cover) dropCurtain();
                })));
    }

    private void dropCurtain() {
        if (curtain != null && canvas != null) canvas.removeChild(curtain);
        curtain = null;
    }

    private static int argb(float alpha) {
        return ((int) (Math.max(0f, Math.min(1f, alpha)) * 255) << 24) | CURTAIN_RGB;
    }

    private void buildPortrait() {
        backHost.clearChildren();
        frontHost.clearChildren();
        if (compactPage) {
            portrait = null;
            return;
        }
        portrait = new Portrait(uiScreen, model.themeZone(), shownKey.location().hashCode(), !model.caught(), k);
        backHost.addChild(portrait.backLayer());
        frontHost.addChild(portrait.frontLayer());
        frontHost.addChild(portrait.toggle());
        portrait.placeAt(EncyclopediaPageLayout.PLATE_X, EncyclopediaPageLayout.PLATE_Y);
    }

    /** Rebuilds all page content from {@link #model} and the live environment; the icon and portrait stay. */
    private void rebuildContent() {
        if (!pageOpen || contentHost == null || model == null) return;
        Minecraft mc = Minecraft.getInstance();
        uiScreen.clearTooltip();
        contentHost.clearChildren();
        lastEnv = EnvSnapshot.fromClient();
        EncyclopediaPageLayout.Bond bond = bondInfo(mc);
        lastBondSignature = bond == null ? "" : bond.text() + "|" + bond.unlocked();

        LeaderboardEntry best = FishEncyclopediaClientCache.getPersonalBest(shownKey.location());
        int quality = 0xAAAAAA;
        if (best != null && best.quality().getColor().getColor() != null) quality = best.quality().getColor().getColor();

        var params = new EncyclopediaPageLayout.Params(
                model, profile, lastEnv, tab, Math.max(0, discOrder.indexOf(shownKey)), discOrder.size(),
                bond, quality, uiScreen, k, new PageActions());
        UIElement<?> bondButton;
        if (compactPage) {
            bondButton = CompactPageLayout.build(contentHost, params).bondButton();
        } else {
            bondButton = EncyclopediaPageLayout.build(contentHost, params).bondButton();
        }
        companionEntryButton = bondButton;
    }

    private final class PageActions implements EncyclopediaPageLayout.Actions {
        @Override
        public void close() {
            closePage();
        }

        @Override
        public void browse(int dir) {
            FishEncyclopediaScreen.this.browse(dir);
        }

        @Override
        public void selectTab(EncyclopediaPageLayout.Tab target) {
            tab = target;
            rebuildContent();
        }

        @Override
        public void claim(UIElement<?> origin) {
            if (shownKey == null) return;
            sendClaimAll(Optional.of(shownKey.location()));
            if (portrait != null) portrait.claimBurst();
            playRewardClaimFlourish(origin);
        }

        @Override
        public void openBond(ItemStack species) {
            CompanionScreen.open(species);
        }
    }

    // -------------------------------------------------------------------------
    // Browsing (prev / next)

    private void browse(int dir) {
        if (!pageOpen || closing || browsing || discOrder.size() < 2) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int index = discOrder.indexOf(shownKey);
        ResourceKey<FishProfile> nextKey = discOrder.get(Math.floorMod(index + dir, discOrder.size()));
        SilhouetteItemButton nextIcon = iconRefs.get(nextKey);
        FishPageModel nextModel = FishPageModel.build(mc.level.registryAccess(), nextKey);
        if (nextIcon == null || nextModel == null) return;

        browsing = true;
        uiScreen.clearTooltip();
        SilhouetteItemButton outgoing = shownIcon;
        ResourceKey<FishProfile> outgoingKey = shownKey;
        leavingIcon = outgoing;
        leavingKey = outgoingKey;

        // Remove the old portrait icon before adding the incoming one. Keeping both in iconLayer
        // makes their transparent sprites cross and visibly overlap during the slide.
        sphere.parkSilently(outgoing);

        // The incoming icon leaves the (hidden) disc already at 4x and slides in from the far side.
        sphere.markSelected(nextIcon);
        nextIcon.setHasUnclaimedReward(false);
        nextIcon.setBondAvailable(false);
        nextIcon.clearAllActions();
        nextIcon.reparentTo(iconLayer, false);
        nextIcon.setTargetScale(slotScale(), false);
        nextIcon.setVisible(true);
        Vector2f currentSlot = slot();
        nextIcon.setPosition(new Vector2f(currentSlot.x - dir * BROWSE_SLIDE, currentSlot.y));

        shownKey = nextKey;
        shownIcon = nextIcon;
        model = nextModel;
        profile = mc.level.registryAccess().registryOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY).getOptional(nextKey).orElse(null);
        if (tab == EncyclopediaPageLayout.Tab.NOTES && !model.loreUnlocked()) tab = EncyclopediaPageLayout.Tab.CATCH;
        buildPortrait();
        rebuildContent();
        addCurtain(BROWSE_SECONDS, 0.7f);

        animateIconTo(nextIcon, currentSlot, slotScale(), BROWSE_SECONDS, "fishEncyclopediaBrowse",
                () -> uiScreen.runDeferred(this::finishBrowse));
    }

    /** Ends a slide: the previous fish is already parked, and the incoming fish is already at its slot. */
    private void finishBrowse() {
        if (!browsing) return;
        browsing = false;
        if (leavingIcon != null) {
            sphere.parkSilently(leavingIcon);
            syncIcon(leavingKey, leavingIcon);
            leavingIcon = null;
            leavingKey = null;
        }
        if (shownIcon != null) land(shownIcon);
    }

    // -------------------------------------------------------------------------
    // Closing: the shown fish flies back to its own disc slot

    private void closePage() {
        if (!pageOpen || closing || shownIcon == null) return;
        if (browsing) {
            shownIcon.cancelAnimationChannel("fishEncyclopediaBrowse");
            finishBrowse();
        }
        closing = true;
        EncyclopediaTutorialClientHandler.onInfoPageClosed();
        uiScreen.clearTooltip();

        SilhouetteItemButton icon = shownIcon;
        icon.cancelAnimationChannel("fishEncyclopediaTravel");
        // Back to screen space, keeping position and size (spike rule 5, in reverse).
        float globalScale = icon.getGlobalScale();
        icon.reparentTo(root, true);
        icon.setTargetScale(globalScale / root.getGlobalScale(), false);

        root.removeChild(canvas);
        canvas = null;
        contentHost = backHost = iconLayer = frontHost = null;
        curtain = null;
        portrait = null;
        companionEntryButton = null;
        ResourceKey<FishProfile> key = shownKey;
        pageOpen = false;
        shownIcon = null;
        shownKey = null;
        model = null;
        profile = null;
        root.setSize(this.width, this.height);
        uiScreen.setScrollY(0f);
        uiScreen.setScrollEnabled(true);
        fadeBackdropTo(0f, 0.15f);

        float defaultScale = sphere.getDefaultItemScale();
        Vector2f restTopLeft = sphere.getRestTopLeft(icon, defaultScale);
        Vector2f target = restTopLeft != null
                ? new Vector2f(sphere.getPosition()).add(restTopLeft)
                : new Vector2f(sphere.getPosition());
        animateIconTo(icon, target, defaultScale, FLIGHT_SNAP_SECONDS, "fishEncyclopediaCloseTravel",
                () -> uiScreen.runDeferred(() -> {
                    // The keyframe already reached the exact slot; hand the icon back to the disc
                    // without changing its visible transform.
                    sphere.reinstateAfterClose(icon);
                    syncIcon(key, icon);
                    closing = false;
                    rebuildClaimAll();
                }));
    }

    // -------------------------------------------------------------------------
    // Reward flourish

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
    // Companion bond status (unchanged logic, now feeds the page's bond strip)

    private EncyclopediaPageLayout.Bond bondInfo(Minecraft mc) {
        if (shownKey == null || model == null || mc.level == null) return null;
        ResourceLocation fishId = shownKey.location();
        Item item = BuiltInRegistries.ITEM.getOptional(fishId).orElse(null);
        if (item == null) return null;
        boolean unlocked = QuestClientCache.isBondUnlocked(fishId);
        ItemStack species = new ItemStack(item);
        String status;
        int inParty = partySlotOf(mc, item);
        if (inParty >= 0) {
            status = translated(inParty == CompanionParty.LEAD
                    ? "screen.fishtastic.companion.in_party_lead" : "screen.fishtastic.companion.in_party");
        } else if (unlocked) {
            boolean carried = mc.player != null && !CarriedFish.groupsOf(mc.player, item).isEmpty();
            status = translated(carried ? "screen.fishtastic.companion.ready" : "screen.fishtastic.companion.carry", species.getHoverName().getString());
        } else if (CompanionBondUnlocks.isUnlisted(fishId)) {
            status = translated("screen.fishtastic.encyclopedia.companions.bond_hidden_quest");
        } else {
            var thresholds = model.thresholds();
            int required = java.util.Arrays.stream(EncyclopediaRewardSection.values()).mapToInt(section -> section.threshold(thresholds)).max().orElse(0);
            status = translated("screen.fishtastic.companion.remaining", Math.max(0, required - FishEncyclopediaClientCache.getCatchCount(fishId)));
        }
        return new EncyclopediaPageLayout.Bond(status, unlocked, species);
    }

    /** The first party slot holding this species, or -1 (slot 0 is the lead). */
    private static int partySlotOf(Minecraft mc, Item item) {
        if (mc.player == null) return -1;
        var party = CompanionStorage.party(mc.player);
        for (int i = 0; i < CompanionParty.SIZE; i++) {
            if (party.get(i).is(item)) return i;
        }
        return -1;
    }

    // -------------------------------------------------------------------------
    // Hooks for the render self-test and in-game debugging

    /** Selects a disc icon exactly as a click would (flight included). No-op for unknown or open pages. */
    public void selectFish(ResourceLocation fishId) {
        SilhouetteItemButton icon = iconRefs.get(ResourceKey.create(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, fishId));
        if (icon != null && !pageOpen && !closing) sphere.selectChild(icon);
    }

    public void browseFish(int dir) {
        browse(dir);
    }

    public void closeInfoPage() {
        closePage();
    }

    public void showNotesTab() {
        if (pageOpen && model != null && model.loreUnlocked()) {
            tab = EncyclopediaPageLayout.Tab.NOTES;
            rebuildContent();
        }
    }

    public boolean isInfoPageOpen() {
        return pageOpen;
    }

    /** True when the narrow-window 300x228 fallback card is active instead of the full spread. */
    public boolean isCompactPage() {
        return pageOpen && compactPage;
    }

    public boolean isClosingInfoPage() {
        return closing;
    }

    public ResourceLocation shownFishId() {
        return shownKey == null ? null : shownKey.location();
    }

    // -------------------------------------------------------------------------
    // Helpers

    private static String translated(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
