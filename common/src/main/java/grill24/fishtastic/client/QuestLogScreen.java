package grill24.fishtastic.client;

import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.client.effects.CoinArcEffect;
import grill24.fishtastic.client.effects.SlideFromEffect;
import grill24.fishtastic.client.encyclopedia.page.PageIcons;
import grill24.fishtastic.client.encyclopedia.page.PageSurface;
import grill24.fishtastic.client.renderer.ZoneIconTextures;
import grill24.fishtastic.client.effects.DropOffEffect;
import grill24.fishtastic.client.effects.PendulumSwingEffect;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.QuestCategory;
import grill24.fishtastic.data.QuestDifficulty;
import grill24.fishtastic.data.QuestObjective;
import grill24.fishtastic.data.QuestReward;
import grill24.fishtastic.data.ShopEntry;
import grill24.fishtastic.network.CompleteQuestPacket;
import grill24.fishtastic.network.FishEncyclopediaSyncPacket;
import grill24.fishtastic.network.PurchaseShopEntryPacket;
import grill24.fishtastic.network.QuestSyncPacket;
import grill24.fishtastic.network.RefreshShopPacket;
import grill24.fishtastic.network.RequestFishEncyclopediaPacket;
import grill24.fishtastic.server.PlayerQuestState;
import grill24.fishtastic.server.QuestTracker;
import io.github.currenj.gelatinui.GelatinUIScreen;
import io.github.currenj.gelatinui.gui.IUIElement;
import io.github.currenj.gelatinui.gui.PivotMode;
import io.github.currenj.gelatinui.gui.UI;
import io.github.currenj.gelatinui.gui.UIContainer;
import io.github.currenj.gelatinui.gui.UIEvent;
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
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import grill24.fishtastic.network.NetworkApiSided;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import grill24.fishtastic.client.util.PlayerHeadItems;
import grill24.fishtastic.network.CleanupGoalProgress;

import io.github.currenj.gelatinui.gui.animation.FloatKeyframeAnimation;
import io.github.currenj.gelatinui.gui.animation.Keyframe;
import org.joml.Vector2f;

import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class QuestLogScreen extends GelatinUIScreen<GelatinMenu> {

    private MinecraftRenderContext tempContext;
    public UiKeyboardNav keyboardNav() {
        return keyboardNav;
    }

    private final UiKeyboardNav keyboardNav = new UiKeyboardNav(this::revealInViewport)
            .glyphNames(Map.of(">", Component.translatable("narration.fishtastic.expand").getString(),
                    "^", Component.translatable("narration.fishtastic.collapse").getString()));
    private boolean handlerInstalled = false;
    private QuestSyncPacket.ClientHandler savedHandler;
    private boolean encyclopediaHandlerInstalled = false;
    private FishEncyclopediaSyncPacket.ClientHandler savedEncyclopediaHandler;
    private int activeTabIndex = 0;

    // Set just before a screen requests the log reopen, consumed once by the next buildUI:
    // switches to the quest's category tab and pulses its row, so a "unlocks via quest" link lands
    // on the quest itself, not just the quest log in general.
    private static ResourceKey<Quest> pendingFocusQuest;

    /** The filters belong to each destination, not to the book as a whole. */
    private enum QuestFilter {
        ALL, IN_PROGRESS, READY
    }

    private final Map<Integer, QuestFilter> questFilters = new HashMap<>();
    private final Map<Integer, ResourceLocation> expandedQuestByDestination = new HashMap<>();
    private QuestViewport questViewport;
    private final Map<Integer, Float> destinationScroll = new HashMap<>();
    private int renderedDestination;
    private final Set<ResourceLocation> pendingClaims = new HashSet<>();
    private final Map<ResourceLocation, Long> pendingClaimTimes = new HashMap<>();
    private final Map<Integer, List<ResourceLocation>> displayedOrder = new HashMap<>();
    private ResourceLocation heldQuest;
    // Wall-clock time the in-flight claim coins land. A UI rebuild before then would discard the coin overlay.
    private long claimAnimEndMs;
    private boolean rebuildAfterClaimAnim;
    private boolean fadingHeldQuest;
    private boolean keepScrollOffset;
    private ResourceLocation scrollAnchor;
    private float scrollAnchorY;

    /** See {@link #pendingFocusQuest}. */
    public static void selectOnNextOpen(ResourceKey<Quest> quest) {
        pendingFocusQuest = quest;
    }

    // Live element refs for in-place updates (populated by buildUI, cleared on rebuild)
    private Label tokenBalanceLabel;
    private HBox tokenIconRow;
    private ManualContainer coinFlyOverlay;
    private int coinSpawnSeq = 0; // unique per-coin channel suffix for staggered spawn timers
    private Label cleanupGoalTotalLabel;
    private Label cleanupGoalCountLabel;
    private SpriteProgressBar cleanupGoalBar;
    private Label dailyResetLabel;
    private HBox cleanupGoalContributorsRow;
    private Label shopResetLabel;
    private SpriteButton shopRefreshBtn;
    private Label shopRefreshCostLabel;
    private Label shopRefreshNotEnoughLabel;
    private final Map<ResourceLocation, QuestRowRefs> questRowRefs = new LinkedHashMap<>();
    private final Map<ResourceLocation, CyclingIconState> cyclingIconRefs = new LinkedHashMap<>();
    private final Map<ResourceKey<ShopEntry>, ShopCardRefs> shopCardRefs = new LinkedHashMap<>();
    private ItemTabs questTabs;
    private VBox questHeader;
    private VBox questContent;
    private final Map<QuestCategory, List<ResourceKey<Quest>>> questKeysByCategory = new EnumMap<>(QuestCategory.class);
    private static final Map<QuestCategory, Integer> QUEST_CATEGORY_TAB_INDEX = Map.of(
            QuestCategory.DAILY, 0,
            QuestCategory.MASTERY, 1,
            QuestCategory.EXPLORER, 2,
            QuestCategory.COLLECTOR, 3,
            QuestCategory.CHALLENGE, 4
    );

    private static final int SHELL_WIDTH = 416;
    private static final int SHELL_HEIGHT = 232;
    private static final int RAIL_WIDTH = 74;
    private static final int CONTENT_WIDTH = 338;
    private static final int CONTENT_INSET = 11;
    private static final int QUEST_VIEWPORT_WIDTH = CONTENT_WIDTH - CONTENT_INSET * 2;
    private static final int QUEST_VIEWPORT_HEIGHT = 180;
    private static final int QUEST_SCROLL_GUTTER = 8;
    private static final int QUEST_PAGE_WIDTH = QUEST_VIEWPORT_WIDTH - QUEST_SCROLL_GUTTER;
    private static final int QUEST_ROW_WIDTH = QUEST_PAGE_WIDTH;
    private static final int QUEST_HEADER_HEIGHT = 36;
    private static final int COLOR_SHELL = 0xFF201A24;
    private static final int COLOR_RAIL = 0xFF2A202B;
    private static final int COLOR_PANEL_EDGE = 0xFF72505B;
    private static final int COLOR_MUTED = 0xFFC9B8BE;
    private static final int COLOR_SELECTION = 0xFFFFAA00;
    private static final int COLOR_READY = 0xFF5DCC7A;
    private static final int COLOR_PROGRESS = 0xFFB86E88;
    private static final int COLOR_TIMER = 0xFF8CCBFF;

    /**
     * A clipped, independently scrolling list. UIScreen's built-in scroll bar moves its root,
     * which is correct for most gelatin screens but would make the Captain's Log rail slide away.
     */
    private static final class QuestViewport extends ManualContainer {
        private IUIElement content;
        private float scrollOffset;
        private float dragGrab;
        /** Starts a screen-level drag on this viewport, so DRAG events keep arriving off the track. */
        private java.util.function.BiConsumer<IUIElement, Float> dragStarter;

        QuestViewport(float width, float height) {
            setSize(width, height);
            setDebugName("questBookViewport");
        }

        void setContent(IUIElement content) {
            clearChildren();
            this.content = content;
            if (content == null) return;
            if (content instanceof UIContainer<?> container) container.forceLayout();
            addChildAt(content, content.getSize().x / 2f, content.getSize().y / 2f);
            repositionContent();
            forceLayout();
        }

        private void repositionContent() {
            if (content == null) return;
            if (content instanceof UIContainer<?> container) container.forceLayout();
            updateChildPosition(content, content.getSize().x / 2f,
                    content.getSize().y / 2f - scrollOffset);
        }

        private float maxScroll() {
            return content == null ? 0f : Math.max(0f, content.getSize().y - getSize().y);
        }

        private void setScrollOffset(float offset) {
            scrollOffset = Math.max(0f, Math.min(maxScroll(), offset));
            repositionContent();
            markDirty();
        }

        @Override
        public boolean handleEvent(UIEvent event) {
            boolean dragging = event.getType() == UIEvent.Type.DRAG || event.getType() == UIEvent.Type.DRAG_END;
            if (!dragging && !getBounds().contains(event.getMouseX(), event.getMouseY())) return false;
            return super.handleEvent(event);
        }

        private float localY(UIEvent event) {
            return (event.getMouseY() - getGlobalPosition().y) / getGlobalScale();
        }

        private float localX(UIEvent event) {
            return (event.getMouseX() - getGlobalPosition().x) / getGlobalScale();
        }

        private float thumbHeight() {
            float trackH = getSize().y;
            return Math.max(18f, trackH * trackH / content.getSize().y);
        }

        @Override
        protected boolean onEvent(UIEvent event) {
            float max = maxScroll();
            if (max > 0f) {
                float travel = Math.max(1f, getSize().y - thumbHeight());
                switch (event.getType()) {
                    case CLICK -> {
                        // Rows stop short of the gutter, so this only fires for clicks on the bar itself.
                        if (localX(event) < getSize().x - QUEST_SCROLL_GUTTER) break;
                        float thumbY = travel * scrollOffset / max;
                        float y = localY(event);
                        dragGrab = (y >= thumbY && y <= thumbY + thumbHeight()) ? y - thumbY : thumbHeight() / 2f;
                        setScrollOffset((y - dragGrab) / travel * max);
                        if (dragStarter != null) dragStarter.accept(this, event.getMouseY() * 1f);
                        return true;
                    }
                    case DRAG -> {
                        setScrollOffset((localY(event) - dragGrab) / travel * max);
                        return true;
                    }
                    case DRAG_END -> {
                        return true;
                    }
                    default -> { }
                }
            }
            if (event.getType() == UIEvent.Type.SCROLL && maxScroll() > 0f) {
                setScrollOffset(scrollOffset - event.getScrollDelta() * 18f);
                return true;
            }
            return super.onEvent(event);
        }

        @Override
        protected void renderChildren(io.github.currenj.gelatinui.gui.IRenderContext context, Rectangle2D viewport) {
            if (context instanceof MinecraftRenderContext mc) {
                // UIElement.render already has this viewport's parent pose on the stack.
                // MinecraftRenderContext transforms scissor coordinates through that pose, so
                // passing global coordinates here would apply the shell offset twice.
                mc.pushScissor(0, 0, Math.round(getSize().x), Math.round(getSize().y));
                super.renderChildren(context, viewport);
                mc.popScissor();
            } else {
                super.renderChildren(context, viewport);
            }

            float max = maxScroll();
            if (max <= 0f) return;
            int trackX = Math.round(getSize().x - 4);
            int trackH = Math.round(getSize().y);
            int thumbH = Math.max(18, Math.round(trackH * getSize().y / content.getSize().y));
            int thumbY = Math.round((trackH - thumbH) * scrollOffset / max);
            context.fill(trackX - 1, 0, trackX + 3, trackH, COLOR_PANEL_EDGE);
            context.fill(trackX, 0, trackX + 2, trackH, 0xFF352A32);
            context.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFFC58A58);
            context.fill(trackX, thumbY, trackX + 1, thumbY + thumbH, 0xFFFFE0A0);
        }
    }

    private record QuestRowRefs(
            UIElement<?> row,
            Label nameLabel,
            SpriteButton claimButton,
            ThinProgressBar progressBar,
            Label countLabel,
            int targetCount,
            String baseDisplayName,
            QuestObjective objective,
            QuestDifficulty difficulty,
            SpriteRectangle.SpriteRectangleImpl statusPip
    ) {}

    /**
     * Live state for a non-{@code distinct_species} tag quest's progress-bar icon: cycles through
     * the tag's members over time (see {@link #CYCLE_INTERVAL_TICKS}) rather than picking one, since
     * no single species is "the" target. {@link #lastIndex} avoids re-touching the icon/tooltip
     * every tick when the cycled-to species hasn't changed since the last one.
     */
    private static final class CyclingIconState {
        final SilhouetteItemButton icon;
        final SpriteRectangle.SpriteRectangleImpl tooltip;
        final List<ResourceKey<Item>> species;
        int lastIndex = -1;
        // Freezes the cycle on whatever species is currently shown while the cursor is over the
        // icon, so a player can't click a species other than the one they're actually looking at.
        boolean hovered = false;

        CyclingIconState(SilhouetteItemButton icon, SpriteRectangle.SpriteRectangleImpl tooltip, List<ResourceKey<Item>> species) {
            this.icon = icon;
            this.tooltip = tooltip;
            this.species = species;
        }
    }

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
    private static final ResourceLocation SHOP_ITEM_PANEL_TEXTURE = Fishtastic.id("textures/gui/generic_item_panel.png");
    private static final ResourceLocation SHOP_ITEM_PANEL_PIN_TEXTURE = Fishtastic.id("textures/gui/generic_item_panel_pin_only.png");
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

    private static final ResourceLocation SHOP_BUY_BUTTON_TEXTURE = Fishtastic.id("textures/gui/buy_button_2.png");
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
    private static final ResourceLocation TAB_ALERT_TEXTURE = Fishtastic.id("textures/gui/alert_2.png");
    private static final float TAB_ALERT_WIDTH = 2.5f;
    private static final float TAB_ALERT_HEIGHT = 7.5f;

    // Quest row background — reuses the shop item panel texture, 9-sliced so the border art
    // stays crisp while the middle stretches to fit each row's actual (varying) size.
    private static final ResourceLocation QUEST_ROW_BG_TEXTURE = Fishtastic.id("textures/gui/generic_item_panel.png");
    // Silver/gold difficulty tiers reuse the same 20x24 canvas and slice metrics, just with a
    // different border color, so no separate slice constants are needed for them.
    private static final ResourceLocation QUEST_ROW_BG_TEXTURE_SILVER = Fishtastic.id("textures/gui/generic_item_panel_silver_border.png");
    private static final ResourceLocation QUEST_ROW_BG_TEXTURE_GOLD = Fishtastic.id("textures/gui/generic_item_panel_gold_border.png");
    // Claimed quests use a green-tinted variant of the same panel to signal completion,
    // regardless of difficulty tier — there's no claimed+silver/gold art, and "claimed" is
    // the more important signal to show once it applies.
    private static final ResourceLocation QUEST_ROW_BG_TEXTURE_CLAIMED = Fishtastic.id("textures/gui/green_generic_item_panel_2.png");
    private static final int QUEST_ROW_BG_SOURCE_WIDTH = 20;
    private static final int QUEST_ROW_BG_SOURCE_HEIGHT = 24;
    // Requested center/stretch region: origin (4,4), size (12,16) within the 20x24 source.
    private static final int QUEST_ROW_BG_SLICE_LEFT = 4;
    private static final int QUEST_ROW_BG_SLICE_TOP = 4;
    private static final int QUEST_ROW_BG_SLICE_RIGHT = QUEST_ROW_BG_SOURCE_WIDTH - (QUEST_ROW_BG_SLICE_LEFT + 12);
    private static final int QUEST_ROW_BG_SLICE_BOTTOM = QUEST_ROW_BG_SOURCE_HEIGHT - (QUEST_ROW_BG_SLICE_TOP + 16);

    // Claim button — reuses the same green claimed-panel texture as the row background,
    // 9-sliced so its border art stays crisp at the button's small on-screen size.
    private static final ResourceLocation QUEST_CLAIM_BUTTON_TEXTURE = Fishtastic.id("textures/gui/green_generic_item_panel_2.png");

    // Status pip shown to the left of a quest's title when its objective has a spatial
    // (biome) or temporal (time of day / weather) condition — lit green while that
    // condition currently holds for the player, dim otherwise.
    private static final ResourceLocation STATUS_PIP_DEFAULT_TEXTURE = Fishtastic.id("textures/gui/status_indicator_pip_default.png");
    private static final ResourceLocation STATUS_PIP_GREEN_TEXTURE = Fishtastic.id("textures/gui/status_indicator_pip_green.png");
    private static final float STATUS_PIP_SIZE = 5f;

    /** One look for every quest-log tooltip: same panel colour, padding, outline and text scale. */
    private static SpriteRectangle.SpriteRectangleImpl tooltipPanel(String text, int textColor) {
        return UI.spriteRectangle(0, 0, COLOR_SHELL).text(text, textColor)
                .autoSize(true).padding(4, 3).outline(true);
    }

    // Scale (relative to ItemRenderer's 16x16 base) for the single-species icon shown to the
    // left of a quest's progress bar, and for each member icon in a tag-quest's discovered-fish
    // list. The list icons are smaller since a whole row of them sits together.
    private static final float QUEST_SPECIES_ICON_SCALE = 0.7f;
    private static final float QUEST_TAG_LIST_ICON_SCALE = 1.25f;
    // Number of tag-list fish icons laid out side-by-side before wrapping to a new row.
    private static final int QUEST_TAG_LIST_ICONS_PER_ROW = 10;
    // How long (in game ticks) a non-distinct tag quest's progress-bar icon shows one species
    // before cycling to the next.
    private static final int CYCLE_INTERVAL_TICKS = 30;

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
    private static final float QUEST_CLAIM_FADE_SECONDS = 0.25f;
    private static final float QUEST_REFLOW_SECONDS = 0.3f;
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
                QuestClientCache.update(packet.questProgress(), packet.tokenBalance(), packet.triggeringItems(),
                        packet.purchaseCounts(), packet.cleanupGoal(), packet.serverGameTime(),
                        packet.baitDepletedItem(), packet.firstCatchItems(), packet.shopRefreshCount(),
                        packet.unlockedCompanions(), packet.newlyUnlockedCompanions());
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == this) {
                    // The redesign derives destination counts, filters and ordering from the
                    // authoritative snapshot. Rebuild the clipped content atomically so a
                    // claim/rotation never leaves a stale Ready count or a mismatched row order.
                    // The selected destination/filter and expanded quest IDs are retained by the
                    // screen, so a sync does not throw the player back to the top of the book.
                    pendingClaims.clear();
                    if (System.currentTimeMillis() < claimAnimEndMs) {
                        // Keep the coin overlay alive; show the claimed state in place and rebuild once they land.
                        updateInPlace();
                        rebuildAfterClaimAnim = true;
                    } else {
                        buildUI();
                    }
                }
            });
        }
        if (!encyclopediaHandlerInstalled) {
            savedEncyclopediaHandler = FishEncyclopediaSyncPacket.clientHandler;
            encyclopediaHandlerInstalled = true;
            FishEncyclopediaSyncPacket.registerClientHandler(packet -> {
                FishEncyclopediaClientCache.update(packet.personalCatchCounts(), packet.personalBestSizes(),
                        packet.globalBestSizes(), packet.claimedRewardKeys());
                Minecraft mc = Minecraft.getInstance();
                // Discovered/silhouette state and spoiler-guarded text are baked into the icons and
                // quest descriptions at build time, so a plain in-place update isn't enough here.
                if (mc.screen == this) {
                    buildUI();
                }
            });
        }
        // Species silhouettes/discovered state depend on FishEncyclopediaClientCache, which is
        // otherwise only refreshed by opening the encyclopedia itself - request a fresh sync (without
        // opening that menu) so a species caught since the last encyclopedia visit shows correctly here.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            NetworkApiSided.sendToServer(new RequestFishEncyclopediaPacket(false), RequestFishEncyclopediaPacket.STREAM_CODEC);
        }
        super.init();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        long now = System.currentTimeMillis();
        if (pendingClaimTimes.values().stream().anyMatch(start -> now - start > 5000)) {
            pendingClaims.clear();
            pendingClaimTimes.clear();
            buildUI();
        }
        if (rebuildAfterClaimAnim && now >= claimAnimEndMs) {
            rebuildAfterClaimAnim = false;
            buildUI();
        }
        if (heldQuest != null && !fadingHeldQuest && now >= claimAnimEndMs && !pendingClaims.contains(heldQuest)
                && !heldQuest.equals(expandedQuestByDestination.get(activeTabIndex))) {
            QuestRowRefs held = questRowRefs.get(heldQuest);
            Minecraft client = Minecraft.getInstance();
            double mx = client.mouseHandler.xpos() * width / client.getWindow().getScreenWidth();
            double my = client.mouseHandler.ypos() * height / client.getWindow().getScreenHeight();
            if (held == null || !held.row().getBounds().contains(mx, my)) {
                if (held != null && held.row() instanceof ManualContainer row) {
                    fadeOutHeldQuest(row);
                } else {
                    heldQuest = null;
                    buildUI();
                }
            }
        }
        // Ticked locally every frame rather than only on quest-sync packets, so the countdown
        // text counts down smoothly instead of jumping only when the player catches something.
        if (dailyResetLabel != null) {
            dailyResetLabel.text(formatResetCountdown("screen.fishtastic.quest_log.dailies_reset", QuestClientCache.getTicksUntilDailyReset()));
        }
        if (shopResetLabel != null) {
            shopResetLabel.text(formatResetCountdown("screen.fishtastic.quest_log.shop.resets", QuestClientCache.getTicksUntilDailyReset()));
        }
        // Ticked locally (rather than only on quest-sync) so a conditioned quest's pip reacts
        // live to weather changing, day/night passing, or the player walking between biomes.
        for (Map.Entry<ResourceLocation, QuestRowRefs> e : questRowRefs.entrySet()) {
            QuestRowRefs refs = e.getValue();
            if (refs.statusPip() != null) {
                updateStatusPip(refs, QuestClientCache.getProgress(e.getKey()).claimed());
            }
        }
        // All cycling icons advance in lockstep off the world's game time (rather than each
        // tracking its own elapsed-time timer) so the swap cadence stays exact even across
        // rebuilds, and every non-distinct tag quest's icon visibly cycles in sync.
        if (!cyclingIconRefs.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                long gameTime = mc.level.getGameTime();
                for (CyclingIconState state : cyclingIconRefs.values()) {
                    if (state.species.isEmpty() || state.hovered) continue;
                    int index = (int) ((gameTime / CYCLE_INTERVAL_TICKS) % state.species.size());
                    updateCyclingIcon(state, index);
                }
            }
        }
    }

    /**
     * Fades the claimed card out before it is dropped from the list. Elements have no opacity,
     * so a cover in the page colour is faded in over the card, then the list is rebuilt without it.
     */
    private void fadeOutHeldQuest(ManualContainer row) {
        fadingHeldQuest = true;
        float w = row.getSize().x;
        float h = row.getSize().y;
        int rgb = COLOR_RAIL & 0x00FFFFFF;
        var cover = UI.rectangle(w, h, rgb);
        row.addChildAt(cover, w / 2f, h / 2f);
        row.playAnimation(new FloatKeyframeAnimation(
                "quest-claim-fade",
                List.of(new Keyframe(0f, 0f), new Keyframe(QUEST_CLAIM_FADE_SECONDS, 1f)),
                v -> cover.color((Math.round(Math.min(1f, v) * 255) << 24) | rgb),
                () -> {
                    // Screen-space rows (list position minus scroll), since the rebuild may also re-anchor the scroll.
                    float oldScroll = questViewport == null ? 0f : questViewport.scrollOffset;
                    Map<ResourceLocation, Float> oldY = new HashMap<>();
                    for (Map.Entry<ResourceLocation, QuestRowRefs> e : questRowRefs.entrySet()) {
                        oldY.put(e.getKey(), e.getValue().row().getPosition().y - oldScroll);
                    }
                    fadingHeldQuest = false;
                    heldQuest = null;
                    // Same scroll offset, not an anchored one: the cards below close the gap, the view stays put.
                    keepScrollOffset = true;
                    buildUI();
                    // Remaining cards glide from where they were to their new slot.
                    float newScroll = questViewport == null ? 0f : questViewport.scrollOffset;
                    for (Map.Entry<ResourceLocation, QuestRowRefs> e : questRowRefs.entrySet()) {
                        Float before = oldY.get(e.getKey());
                        if (before == null) continue;
                        UIElement<?> card = e.getValue().row();
                        float dy = before - (card.getPosition().y - newScroll);
                        if (Math.abs(dy) > 0.5f) {
                            card.addEffect(new SlideFromEffect("quest-reflow", 2, QUEST_REFLOW_SECONDS, 0f, dy));
                        }
                    }
                }));
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
        if (questViewport != null) {
            destinationScroll.put(renderedDestination, questViewport.scrollOffset);
            if (scrollAnchor == null && !keepScrollOffset && renderedDestination == activeTabIndex) {
                // Anchor on the topmost visible row, never the held (just-claimed) one: it is about
                // to be re-sorted to the end of the list and would drag the scroll down with it.
                float anchorTop = Float.MAX_VALUE;
                for (var entry : questRowRefs.entrySet()) {
                    UIElement<?> row = entry.getValue().row();
                    float top = row.getPosition().y;
                    if (!entry.getKey().equals(heldQuest) && top + row.getSize().y > questViewport.scrollOffset
                            && top < anchorTop) {
                        anchorTop = top;
                        scrollAnchor = entry.getKey();
                        scrollAnchorY = top - questViewport.scrollOffset;
                    }
                }
            }
        }
        keepScrollOffset = false;
        keyboardNav.resetLabels();
        renderedDestination = activeTabIndex;
        questRowRefs.clear();
        cyclingIconRefs.clear();
        shopCardRefs.clear();
        questTabs = null;
        questViewport = null;
        tokenBalanceLabel = null;
        tokenIconRow = null;
        coinFlyOverlay = null;
        fadingHeldQuest = false;
        rebuildAfterClaimAnim = false;
        cleanupGoalTotalLabel = null;
        cleanupGoalCountLabel = null;
        cleanupGoalBar = null;
        dailyResetLabel = null;
        cleanupGoalContributorsRow = null;
        shopResetLabel = null;
        shopRefreshBtn = null;
        shopRefreshCostLabel = null;
        shopRefreshNotEnoughLabel = null;

        tempContext = new MinecraftRenderContext(null, this.font);

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Registry<Quest> questRegistry;
        try {
            questRegistry = mc.level.registryAccess().registryOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
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
            shopRegistry = mc.level.registryAccess().registryOrThrow(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY);
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
            list.sort(Comparator
                    .<Map.Entry<ResourceKey<Quest>, Quest>, QuestDifficulty>comparing(e -> e.getValue().difficulty())
                    .thenComparingInt(e -> e.getValue().reward().questTokens())
                    .thenComparing(e -> e.getValue().displayName()));
        }

        ResourceKey<Quest> focusQuest = pendingFocusQuest;
        pendingFocusQuest = null;
        if (focusQuest != null) {
            Quest focused = questRegistry.getOptional(focusQuest).orElse(null);
            if (focused == null) {
                focusQuest = null;
            } else {
                activeTabIndex = QUEST_CATEGORY_TAB_INDEX.getOrDefault(focused.category(), activeTabIndex);
            }
        }

        // Only today's rotated-in dailies are worth showing — the rest of the daily pool
        // stays hidden rather than listed as "inactive", so players discover it as it rotates in.
        byCategory.get(QuestCategory.DAILY).removeIf(entry -> !activeDailies.contains(entry.getKey()));

        // Secrets stay out of the log until completed; chain rungs appear once their prerequisite
        // is claimed. See Quest#isHiddenFromLog for why the two cases differ.
        for (var list : byCategory.values()) {
            list.removeIf(entry -> {
                Quest quest = entry.getValue();
                boolean completed = QuestClientCache.getProgress(entry.getKey().location()).completed();
                boolean prerequisiteClaimed = quest.prerequisiteQuestId()
                        .map(prereq -> QuestClientCache.getProgress(prereq.location()).claimed())
                        .orElse(false);
                return quest.isHiddenFromLog(completed, prerequisiteClaimed);
            });
        }

        // First Catch rewards belong to fishing, regardless of whether help was used.
        byCategory.get(QuestCategory.DAILY).addAll(0, byCategory.get(QuestCategory.TUTORIAL).stream()
                .filter(entry -> !QuestClientCache.getProgress(entry.getKey().location()).claimed()
                        || entry.getKey().location().equals(heldQuest)).toList());

        questKeysByCategory.clear();
        for (QuestCategory cat : QUEST_CATEGORY_TAB_INDEX.keySet()) {
            List<ResourceKey<Quest>> keys = new ArrayList<>();
            for (Map.Entry<ResourceKey<Quest>, Quest> entry : byCategory.get(cat)) keys.add(entry.getKey());
            questKeysByCategory.put(cat, keys);
        }

        // The book is deliberately a fixed logical-size shell. The rail and header stay put while
        // only the clipped quest viewport scrolls, matching the reviewed Captain's Log mockups.
        ManualContainer shell = UI.manualContainer().setSize(SHELL_WIDTH, SHELL_HEIGHT)
                .backgroundColor(0xFF15151B);
        shell.setDebugName("questBookShell");

        ManualContainer rail = UI.manualContainer().setSize(RAIL_WIDTH, SHELL_HEIGHT)
                .backgroundColor(COLOR_RAIL);
        rail.backgroundSprite(questRowBackgroundSprite(false, QuestDifficulty.BRONZE));
        rail.addChildAt(UI.rectangle(RAIL_WIDTH - 6, SHELL_HEIGHT - 6, COLOR_RAIL), RAIL_WIDTH / 2f, SHELL_HEIGHT / 2f);
        place(rail, new Label(translated("screen.fishtastic.quest_log.rail.title"), 0xFFEBDAD1).init(tempContext), 10, 7);
        Label railSubtitle = new Label(translated("screen.fishtastic.quest_log.rail.subtitle"), COLOR_SELECTION).init(tempContext);
        place(rail, railSubtitle, 10, 16);
        place(rail, UI.rectangle(58, 1, COLOR_PANEL_EDGE), 11, 154);

        String[] destinationKeys = {
                "today", "goals", "explore", "collect", "trials", "market", "cleanup"
        };
        for (int i = 0; i < destinationKeys.length; i++) {
            int ready = i < 5 ? readyCount(destinationEntries(byCategory, i)) : 0;
            addDestinationButton(rail, i, destinationKeys[i], ready, i == activeTabIndex);
        }
        Label closeHint = new Label(translated("screen.fishtastic.quest_log.rail.close"), 0xFF8E7E87).init(tempContext);
        rail.addChildAt(closeHint, RAIL_WIDTH / 2f, SHELL_HEIGHT - 14f);

        ManualContainer panel = UI.manualContainer().setSize(CONTENT_WIDTH, SHELL_HEIGHT)
                .backgroundColor(COLOR_SHELL);
        panel.backgroundSprite(questRowBackgroundSprite(false, QuestDifficulty.BRONZE));
        panel.addChildAt(UI.rectangle(CONTENT_WIDTH - 6, SHELL_HEIGHT - 6, COLOR_RAIL),
                CONTENT_WIDTH / 2f, SHELL_HEIGHT / 2f);

        String titleKey = "screen.fishtastic.quest_log.heading." + activeTabIndex;
        Label titleLabel = new Label(translated(titleKey), 0xFFFFFFFF).init(tempContext);
        place(panel, titleLabel, CONTENT_INSET, 8);

        tokenBalanceLabel = new Label(translated("screen.fishtastic.quest_log.tokens", QuestClientCache.getTokenBalance()), COLOR_SELECTION).init(tempContext);
        HBox tokenLabel = UI.hbox().spacing(2).alignment(HBox.Alignment.CENTER);
        tokenLabel.addChild(UI.itemRenderer(new ItemStack(FishtasticItems.PILE_OF_COINS.value())).showCount(false));
        tokenLabel.addChild(tokenBalanceLabel);
        tokenLabel.tooltip(uiScreen, new Label(translated("screen.fishtastic.quest_log.tokens", QuestClientCache.getTokenBalance()), COLOR_SELECTION).init(tempContext));
        tokenIconRow = tokenLabel;
        ManualContainer tokenBox = UI.manualContainer().setSize(87, 18).backgroundColor(0xFF3B2A29);
        tokenLabel.forceLayout();
        place(tokenBox, tokenLabel, 8, 1);
        place(panel, tokenBox, CONTENT_WIDTH - 98, 6);

        place(panel, buildFilterBar(byCategory, activeTabIndex), CONTENT_INSET, 28);
        if (activeTabIndex < 5) {
            QuestFilter filter = questFilters.getOrDefault(activeTabIndex, QuestFilter.ALL);
            place(panel, buildQuestListRedesigned(destinationEntries(byCategory, activeTabIndex),
                    categoryForDestination(activeTabIndex), activeTabIndex, filter), CONTENT_INSET, 48);
        } else {
            IUIElement page = activeTabIndex == 5 ? buildShopPanel(shopRegistry, currentDay) : buildCleanupGoalPanel();
            if (page instanceof UIContainer<?> container) container.forceLayout();
            int pageTop = activeTabIndex == 5 ? 48 : 32;
            questViewport = new QuestViewport(QUEST_VIEWPORT_WIDTH, SHELL_HEIGHT - pageTop - 7);
            questViewport.dragStarter = (el, y) -> uiScreen.startDrag(el, y, 0f);
            questViewport.setContent(page);
            questViewport.setScrollOffset(destinationScroll.getOrDefault(activeTabIndex, 0f));
            place(panel, questViewport, CONTENT_INSET, pageTop);
        }

        shell.addChildAt(rail, RAIL_WIDTH / 2f, SHELL_HEIGHT / 2f);
        shell.addChildAt(panel, RAIL_WIDTH + 4f + CONTENT_WIDTH / 2f, SHELL_HEIGHT / 2f);

        coinFlyOverlay = UI.manualContainer().setSize(SHELL_WIDTH, SHELL_HEIGHT);
        coinFlyOverlay.setDebugName("questClaimCoinOverlay");
        shell.addChild(coinFlyOverlay);
        questContent = null;

        shell.forceLayout();
        uiScreen.setAutoCenterRoot(true);
        uiScreen.setScrollEnabled(false);
        uiScreen.setRoot(shell);
        keyboardNav.refresh(shell);

        if (focusQuest != null) {
            ResourceKey<Quest> focused = focusQuest;
            uiScreen.runDeferred(() -> pulseQuestRow(focused));
        }
    }

    @Override
    protected void renderContent(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xC010141A);
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        keyboardNav.renderFocus(graphics);
        if (keyboardNav.consumeNarrationRequest()) triggerImmediateNarration(false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (uiScreen != null && keyboardNav.keyPressed(keyCode, modifiers, uiScreen, uiScreen.getRoot())) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        keyboardNav.mouseUsed();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void updateNarratedWidget(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        keyboardNav.narrate(output);
        output.add(net.minecraft.client.gui.narration.NarratedElementType.USAGE,
                Component.translatable("narration.fishtastic.quest_log.usage"));
    }

    /** Scrolls the quest list so a keyboard-focused element is inside the clipped viewport. */
    private void revealInViewport(IUIElement element) {
        if (questViewport == null) return;
        for (IUIElement p = element.getParent(); p != null; p = p.getParent()) {
            if (p != questViewport) continue;
            Rectangle2D vp = questViewport.getBounds();
            Rectangle2D b = element.getBounds();
            float scale = Math.max(0.01f, questViewport.getGlobalScale());
            if (b.getMinY() < vp.getMinY()) {
                questViewport.setScrollOffset(questViewport.scrollOffset - (float) (vp.getMinY() - b.getMinY()) / scale - 4f);
            } else if (b.getMaxY() > vp.getMaxY()) {
                questViewport.setScrollOffset(questViewport.scrollOffset + (float) (b.getMaxY() - vp.getMaxY()) / scale + 4f);
            }
            return;
        }
    }

    /** Place a measured element by its top left, keeping font and item pixels on integer coordinates. */
    private static void place(ManualContainer parent, IUIElement child, float x, float y) {
        if (child instanceof UIContainer<?> container) container.forceLayout();
        parent.addChildAt(child, x + child.getSize().x / 2f, y + child.getSize().y / 2f);
    }

    private static void addPanelBorder(ManualContainer panel, int width, int height) {
        panel.addChildAt(UI.rectangle(width, 2, COLOR_PANEL_EDGE), width / 2f, 1f);
        panel.addChildAt(UI.rectangle(width, 2, COLOR_PANEL_EDGE), width / 2f, height - 1f);
        panel.addChildAt(UI.rectangle(2, height, COLOR_PANEL_EDGE), 1f, height / 2f);
        panel.addChildAt(UI.rectangle(2, height, COLOR_PANEL_EDGE), width - 1f, height / 2f);
    }

    private static QuestCategory categoryForDestination(int destination) {
        return switch (destination) {
            case 0 -> QuestCategory.DAILY;
            case 1 -> QuestCategory.MASTERY;
            case 2 -> QuestCategory.EXPLORER;
            case 3 -> QuestCategory.COLLECTOR;
            default -> QuestCategory.CHALLENGE;
        };
    }

    private static String titleKeyForDestination(int destination) {
        return switch (destination) {
            case 0 -> "screen.fishtastic.quest_log.destination.today";
            case 1 -> "screen.fishtastic.quest_log.destination.goals";
            case 2 -> "screen.fishtastic.quest_log.destination.explore";
            case 3 -> "screen.fishtastic.quest_log.destination.collect";
            case 4 -> "screen.fishtastic.quest_log.destination.trials";
            case 5 -> "screen.fishtastic.quest_log.destination.market";
            default -> "screen.fishtastic.quest_log.destination.cleanup";
        };
    }

    private static List<Map.Entry<ResourceKey<Quest>, Quest>> destinationEntries(
            Map<QuestCategory, List<Map.Entry<ResourceKey<Quest>, Quest>>> byCategory, int destination) {
        if (destination >= 5) return List.of();
        return byCategory.getOrDefault(categoryForDestination(destination), List.of());
    }

    private static int readyCount(List<Map.Entry<ResourceKey<Quest>, Quest>> entries) {
        int count = 0;
        for (Map.Entry<ResourceKey<Quest>, Quest> entry : entries) {
            PlayerQuestState.QuestProgress progress = QuestClientCache.getProgress(entry.getKey().location());
            if (progress.completed() && !progress.claimed()) count++;
        }
        return count;
    }

    private void addDestinationButton(ManualContainer rail, int destination, String key, int ready, boolean active) {
        String label = translated("screen.fishtastic.quest_log.destination." + key);
        String text = label;
        int width = 62;
        ManualContainer slot = UI.manualContainer().setSize(width, 20);
        SpriteButton button = new SpriteButton(width, 20, active ? 0xFF714452 : 0x002A202B)
                .text(text, active ? 0xFFFFFFFF : COLOR_MUTED);
        button.onMouseEnter(e -> button.color(active ? 0xFF7D4E5B : 0xFF3A2B37));
        button.onMouseExit(e -> button.color(active ? 0xFF714452 : 0x002A202B));
        button.onClick(e -> {
            if (activeTabIndex == destination) return;
            heldQuest = null;
            scrollAnchor = null;
            activeTabIndex = destination;
            uiScreen.runDeferred(this::buildUI);
        });
        button.text("", 0xFFFFFFFF);
        keyboardNav.label(button, label
                + (ready > 0 ? ", " + translated("narration.fishtastic.quest_log.ready", ready) : "")
                + (active ? ", " + translated("narration.fishtastic.selected") : ""));
        slot.addChildAt(button, width / 2f, 10f);
        String[] icons = {"*", "✓", "♦", "▥", "✦", "•", ">"};
        place(slot, new Label(ready > 0 ? String.valueOf(ready) : icons[destination], ready > 0 ? COLOR_READY : COLOR_MUTED).init(tempContext), 9, 5);
        place(slot, new Label(text, active ? 0xFFFFFFFF : COLOR_MUTED).init(tempContext), 20, 5);
        if (active) slot.addChildAt(UI.rectangle(2, 20, COLOR_SELECTION), 1f, 10f);
        rail.addChildAt(slot, RAIL_WIDTH / 2f, 43f + destination * 25.5f);
    }

    private ManualContainer buildFilterBar(Map<QuestCategory, List<Map.Entry<ResourceKey<Quest>, Quest>>> byCategory,
            int destination) {
        ManualContainer bar = UI.manualContainer().setSize(QUEST_VIEWPORT_WIDTH, 18);
        if (destination >= 5) {
            if (destination == 5) {
                shopResetLabel = new Label(formatResetCountdown("screen.fishtastic.quest_log.shop.resets",
                        QuestClientCache.getTicksUntilDailyReset()), COLOR_TIMER).init(tempContext);
                place(bar, shopResetLabel, 0, 3);
            }
            return bar;
        }

        QuestFilter selected = questFilters.getOrDefault(destination, QuestFilter.ALL);
        List<Map.Entry<ResourceKey<Quest>, Quest>> entries = destinationEntries(byCategory, destination);
        int ready = readyCount(entries);
        float x = 0f;
        x = addFilterButton(bar, x, translated("screen.fishtastic.quest_log.filter.all"), QuestFilter.ALL, selected);
        x = addFilterButton(bar, x, translated("screen.fishtastic.quest_log.filter.in_progress"), QuestFilter.IN_PROGRESS, selected);
        addFilterButton(bar, x, translated("screen.fishtastic.quest_log.filter.ready", ready), QuestFilter.READY, selected);

        if (destination == 0) {
            dailyResetLabel = new Label(formatResetCountdown("screen.fishtastic.quest_log.dailies_reset",
                    QuestClientCache.getTicksUntilDailyReset()), COLOR_TIMER).init(tempContext);
            place(bar, dailyResetLabel, QUEST_VIEWPORT_WIDTH - dailyResetLabel.getSize().x - 28, 3);
        } else if (destination == 5) {
            shopResetLabel = new Label(formatResetCountdown("screen.fishtastic.quest_log.shop.resets",
                    QuestClientCache.getTicksUntilDailyReset()), COLOR_TIMER).init(tempContext);
            place(bar, shopResetLabel, 0, 3);
        }
        return bar;
    }

    private float addFilterButton(ManualContainer bar, float x, String text, QuestFilter filter, QuestFilter selected) {
        int width = this.font.width(text) + 12;
        ManualContainer slot = UI.manualContainer().setSize(width, 15);
        SpriteButton button = new SpriteButton(width, 15, filter == selected ? 0xFF704450 : 0x00352A32)
                .text(text, filter == selected ? COLOR_SELECTION : COLOR_MUTED);
        button.onMouseEnter(e -> button.color(filter == selected ? 0xFF845462 : 0xFF4A3540));
        button.onMouseExit(e -> button.color(filter == selected ? 0xFF704450 : 0x00352A32));
        button.onClick(e -> {
            heldQuest = null;
            questFilters.put(activeTabIndex, filter);
            uiScreen.runDeferred(this::buildUI);
        });
        keyboardNav.label(button, text + (filter == selected ? ", " + translated("narration.fishtastic.selected") : ""));
        slot.addChildAt(button, width / 2f, 7.5f);
        if (filter == selected) slot.addChildAt(UI.rectangle(width, 2, COLOR_SELECTION), width / 2f, 14f);
        bar.addChildAt(slot, x + width / 2f, 7.5f);
        return x + width + 3f;
    }

    private QuestViewport buildQuestListRedesigned(List<Map.Entry<ResourceKey<Quest>, Quest>> entries,
            QuestCategory category, int destination, QuestFilter filter) {
        List<Map.Entry<ResourceKey<Quest>, Quest>> visible = new ArrayList<>(entries);
        visible.removeIf(entry -> !matchesFilter(entry.getValue(), entry.getKey().location(), filter)
                && !entry.getKey().location().equals(heldQuest));
        List<ResourceLocation> previous = displayedOrder.getOrDefault(destination, List.of());
        if (heldQuest != null && previous.contains(heldQuest)) {
            visible.sort(Comparator.comparingInt(entry -> {
                int index = previous.indexOf(entry.getKey().location());
                return index < 0 ? Integer.MAX_VALUE : index;
            }));
        } else visible.sort(this::compareQuestEntries);
        displayedOrder.put(destination, visible.stream().map(entry -> entry.getKey().location()).toList());

        VBox list = UI.vbox().spacing(0).alignment(VBox.Alignment.CENTER);
        if (visible.isEmpty()) {
            String destinationName = translated("screen.fishtastic.quest_log.destination." +
                    switch (destination) {
                        case 0 -> "today";
                        case 1 -> "goals";
                        case 2 -> "explore";
                        case 3 -> "collect";
                        default -> "trials";
                    });
            String message = filter == QuestFilter.READY
                    ? translated("screen.fishtastic.quest_log.empty.ready", destinationName)
                    : translated(filter == QuestFilter.ALL ? "screen.fishtastic.quest_log.no_quests" : "screen.fishtastic.quest_log.empty.in_progress");
            list.addChild(new Label(message, COLOR_MUTED).maxWidth(240).centered(true).init(tempContext));
            if (filter != QuestFilter.ALL) {
                SpriteButton showAll = new SpriteButton(70, 18, 0xFF4D3440)
                        .text(translated("screen.fishtastic.quest_log.filter.all"), 0xFFFFFFFF);
                showAll.onClick(e -> {
                    questFilters.put(destination, QuestFilter.ALL);
                    uiScreen.runDeferred(this::buildUI);
                });
                list.addChild(showAll);
            }
        } else {
            for (Map.Entry<ResourceKey<Quest>, Quest> entry : visible) {
                list.addChild(buildQuestRowRedesigned(entry.getKey(), entry.getValue(), destination));
            }
        }

        list.forceLayout();
        questViewport = new QuestViewport(QUEST_VIEWPORT_WIDTH, QUEST_VIEWPORT_HEIGHT);
        questViewport.dragStarter = (el, y) -> uiScreen.startDrag(el, y, 0f);
        questViewport.setContent(list);
        QuestRowRefs anchor = scrollAnchor == null ? null : questRowRefs.get(scrollAnchor);
        questViewport.setScrollOffset(anchor == null ? destinationScroll.getOrDefault(destination, 0f)
                : anchor.row().getPosition().y - scrollAnchorY);
        scrollAnchor = null;
        return questViewport;
    }

    private boolean matchesFilter(Quest quest, ResourceLocation id, QuestFilter filter) {
        PlayerQuestState.QuestProgress progress = QuestClientCache.getProgress(id);
        return switch (filter) {
            case ALL -> true;
            case READY -> progress.completed() && !progress.claimed();
            case IN_PROGRESS -> !progress.claimed() && !progress.completed();
        };
    }

    private int compareQuestEntries(Map.Entry<ResourceKey<Quest>, Quest> left,
            Map.Entry<ResourceKey<Quest>, Quest> right) {
        int state = Integer.compare(questStateRank(right), questStateRank(left));
        if (state != 0) return state;
        int progress = Float.compare(progressFraction(right), progressFraction(left));
        if (progress != 0) return progress;
        int difficulty = left.getValue().difficulty().compareTo(right.getValue().difficulty());
        if (difficulty != 0) return difficulty;
        if (left.getValue().category() == QuestCategory.TUTORIAL) return -1;
        if (right.getValue().category() == QuestCategory.TUTORIAL) return 1;
        int reward = Integer.compare(left.getValue().reward().questTokens(), right.getValue().reward().questTokens());
        if (reward != 0) return reward;
        int name = left.getValue().displayName().compareToIgnoreCase(right.getValue().displayName());
        return name != 0 ? name : left.getKey().location().compareTo(right.getKey().location());
    }

    private int questStateRank(Map.Entry<ResourceKey<Quest>, Quest> entry) {
        PlayerQuestState.QuestProgress progress = QuestClientCache.getProgress(entry.getKey().location());
        if (progress.completed() && !progress.claimed()) return 4;
        if (!progress.claimed() && progress.currentCount() > 0) return 3;
        if (!progress.claimed()) return 2;
        return 1;
    }

    private float progressFraction(Map.Entry<ResourceKey<Quest>, Quest> entry) {
        int target = entry.getValue().objective().effectiveTargetCount(Minecraft.getInstance().level.registryAccess());
        return target <= 0 ? 0f : Math.min(1f,
                (float) QuestClientCache.getProgress(entry.getKey().location()).displayCount(target) / target);
    }

    private ManualContainer buildQuestRowRedesigned(ResourceKey<Quest> questKey, Quest quest, int destination) {
        ResourceLocation questId = questKey.location();
        PlayerQuestState.QuestProgress progress = QuestClientCache.getProgress(questId);
        boolean claimed = progress.claimed();
        boolean completed = progress.completed();
        boolean canClaim = completed && !claimed;

        int targetCount = quest.objective().effectiveTargetCount(Minecraft.getInstance().level.registryAccess());
        int currentCount = progress.displayCount(targetCount);
        float fraction = targetCount > 0 ? Math.min(1f, (float) currentCount / targetCount) : 0f;
        String baseDisplayName = quest.displayName().isEmpty() ? questId.getPath() : quest.displayName();

        boolean hasItems = !quest.reward().items().isEmpty();
        Label nameLabel = new Label(baseDisplayName, 0xFFFFFFFF).maxWidth(hasItems ? 126 : 172).init(tempContext);
        Label objectiveLabel = new Label(objectiveSummary(quest, targetCount), COLOR_MUTED)
                .maxWidth(hasItems ? 126 : 192).init(tempContext);
        Label countLabel = new Label(translated("screen.fishtastic.quest_log.progress_count", currentCount, targetCount),
                0xFFFFFFFF).init(tempContext);
        ThinProgressBar bar = new ThinProgressBar(76, 5).progressImmediate(fraction)
                .flatColor(canClaim ? COLOR_READY : claimed ? 0xFF777077 : COLOR_PROGRESS);
        SilhouetteItemButton speciesIcon = buildQuestHeaderIcon(quest, questId);
        int extra = Math.round(nameLabel.getSize().y + objectiveLabel.getSize().y - 18);
        int headerHeight = QUEST_HEADER_HEIGHT + extra;

        boolean expandable = hasExpandableContent(quest, targetCount);
        boolean expanded = expandable && questId.equals(expandedQuestByDestination.get(destination));
        VBox details = expanded ? buildExpandedQuestDetails(quest, progress) : null;
        int detailHeight = 0;
        if (details != null) {
            details.forceLayout();
            detailHeight = Math.max(82, Math.round(details.getSize().y + 12f));
        }

        ManualContainer row = UI.manualContainer().setSize(QUEST_ROW_WIDTH, headerHeight + detailHeight);
        row.setDebugName("questRow:" + questId);
        row.backgroundSprite(questRowBackgroundSprite(false, quest.difficulty()));

        // Full-header hit target gives the accordion a generous target without stealing clicks
        // from Claim or species/reward inspection controls.
        SpriteButton toggleHit = new SpriteButton(QUEST_ROW_WIDTH, headerHeight, 0x00000000);
        if (expandable) toggleHit.onClick(e -> toggleExpanded(destination, questId));
        row.addChildAt(toggleHit, QUEST_ROW_WIDTH / 2f, headerHeight / 2f);

        int stateColor = canClaim ? COLOR_READY : claimed ? 0xFF8E7E87 : COLOR_PROGRESS;
        place(row, UI.rectangle(2, headerHeight - 8, stateColor), 4, 4);
        place(row, speciesIcon != null ? speciesIcon : objectiveConditionIcon(quest.objective()), 10, 7);
        place(row, nameLabel, 32, 3);
        place(row, objectiveLabel, 32, 13 + nameLabel.getSize().y - 9);
        place(row, bar, 32, 24 + extra);
        place(row, countLabel, 114, 22 + extra);

        addCollapsedRewardPreview(row, quest, headerHeight, !canClaim && !claimed);

        SpriteButton claimButton = new SpriteButton(64, 19, QUEST_CLAIM_BUTTON_TEXTURE)
                .texture(questClaimButtonSprite())
                .text(translated("screen.fishtastic.quest_log.claim") +
                        (quest.reward().questTokens() > 0 ? " " + quest.reward().questTokens() : ""), 0xFFFFFFFF);

        final ResourceLocation finalQuestId = questId;
        claimButton.onClick(e -> {
            if (!pendingClaims.add(finalQuestId)) return;
            pendingClaimTimes.put(finalQuestId, System.currentTimeMillis());
            heldQuest = finalQuestId;
            claimButton.text(translated("screen.fishtastic.quest_log.claiming"), COLOR_MUTED);
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                NetworkApiSided.sendToServer(new CompleteQuestPacket(finalQuestId), CompleteQuestPacket.STREAM_CODEC);
            }
            triggerQuestClaimReward(row, quest);
        });
        claimButton.setVisible(canClaim);
        place(row, claimButton, QUEST_ROW_WIDTH - 83, (headerHeight - 19) / 2);
        if (canClaim && !pendingClaims.contains(questId)) {
            claimButton.text("", 0xFFFFFFFF);
            HBox claimContent = UI.hbox().spacing(3).alignment(HBox.Alignment.CENTER);
            claimContent.addChild(new Label(translated("screen.fishtastic.quest_log.claim") +
                    (quest.reward().questTokens() > 0 ? " " + quest.reward().questTokens() : ""), 0xFFFFFFFF).init(tempContext));
            if (quest.reward().questTokens() > 0) claimContent.addChild(rewardCoinIconFor(quest.reward().questTokens()));
            claimContent.forceLayout();
            row.addChildAt(claimContent, QUEST_ROW_WIDTH - 51f, headerHeight / 2f);
        }
        if (pendingClaims.contains(questId)) claimButton.text(translated("screen.fishtastic.quest_log.claiming"), COLOR_MUTED);

        if (!canClaim && claimed) {
            row.addChildAt(new Label(translated("screen.fishtastic.quest_log.claimed"), COLOR_MUTED).init(tempContext),
                    264f, headerHeight / 2f);
        }

        String arrowText = expanded ? "^" : ">";
        if (expandable) {
            SpriteButton arrow = new SpriteButton(13, 16, 0x00000000).text(arrowText, 0xFFFFFFFF);
            arrow.onClick(e -> toggleExpanded(destination, questId));
            row.addChildAt(arrow, QUEST_ROW_WIDTH - 10f, headerHeight / 2f);
        }

        if (details != null) {
            place(row, details, 12, headerHeight + 6);
        }

        ManualContainer focusOutline = UI.manualContainer().setSize(QUEST_ROW_WIDTH, headerHeight);
        focusOutline.addChildAt(UI.rectangle(QUEST_ROW_WIDTH, 1, COLOR_SELECTION), QUEST_ROW_WIDTH / 2f, 0.5f);
        focusOutline.addChildAt(UI.rectangle(QUEST_ROW_WIDTH, 1, COLOR_SELECTION), QUEST_ROW_WIDTH / 2f, headerHeight - 0.5f);
        focusOutline.addChildAt(UI.rectangle(1, headerHeight, COLOR_SELECTION), 0.5f, headerHeight / 2f);
        focusOutline.addChildAt(UI.rectangle(1, headerHeight, COLOR_SELECTION), QUEST_ROW_WIDTH - 0.5f, headerHeight / 2f);
        focusOutline.setVisible(false);
        row.addChildAt(focusOutline, QUEST_ROW_WIDTH / 2f, headerHeight / 2f);
        row.onMouseEnter(e -> focusOutline.setVisible(true));
        row.onMouseExit(e -> focusOutline.setVisible(false));


        SpriteRectangle.SpriteRectangleImpl statusPip = null;
        questRowRefs.put(questId, new QuestRowRefs(row, nameLabel, claimButton, bar, countLabel, targetCount,
                baseDisplayName, quest.objective(), quest.difficulty(), statusPip));
        return row;
    }

    /**
     * Whether expanding the row would reveal anything the header doesn't already show: a species or
     * bait checklist, or a description the header's objective summary doesn't already spell out
     * (the summary falls back to the description itself for any conditioned objective).
     */
    private boolean hasExpandableContent(Quest quest, int target) {
        QuestObjective objective = quest.objective();
        if (objective.distinctSpecies() && objective.targetSpeciesTag().isPresent()) return true;
        if (objective.distinctBaitTag().isPresent()) return true;
        String description = describeWithSpoilerGuard(quest).trim().replaceFirst("[.]$", "");
        return !description.isEmpty() && !description.equals(objectiveSummary(quest, target));
    }

    private String objectiveSummary(Quest quest, int target) {
        QuestObjective objective = quest.objective();
        if (objective.distinctSpecies() && objective.targetSpeciesTag().isPresent()
                && objective.targetSpeciesTag().get().location().getPath().startsWith("zone_")
                && !hasEnvironmentCondition(objective) && objective.minQuality().isEmpty() && objective.catchStat().isEmpty()) {
            return translated("screen.fishtastic.quest_log.objective.discover", target,
                    objective.targetSpeciesTag().get().location().getPath().substring(5).replace('_', ' '));
        }
        if (quest.category() == QuestCategory.TUTORIAL) return translated("screen.fishtastic.quest_log.objective.any");
        if (objective.zoneCondition().isPresent() && objective.targetSpeciesTag().isPresent()
                && objective.targetSpeciesTag().get().location().getPath().equals("zone_" + objective.zoneCondition().get().getSerializedName())
                && objective.timeConditions().isEmpty() && objective.weatherCondition().isEmpty()
                && objective.biomeCondition().isEmpty() && objective.minQuality().isEmpty()
                && objective.catchStat().isEmpty() && !objective.distinctSpecies()) {
            return translated("screen.fishtastic.quest_log.objective.zone", target,
                    objective.zoneCondition().get().getSerializedName());
        }
        // Unknown objective combinations keep all authored conditions and grow the row.
        return describeWithSpoilerGuard(quest).trim().replaceFirst("[.]$", "");
    }

    @Nullable
    private SilhouetteItemButton buildQuestHeaderIcon(Quest quest, ResourceLocation questId) {
        QuestObjective objective = quest.objective();
        if (objective.targetSpecies().isPresent()) {
            ResourceKey<Item> key = objective.targetSpecies().get();
            SilhouetteItemButton icon = buildSpeciesIcon(key, 1f);
            if (icon != null) wireEncyclopediaNavigation(icon, key);
            return icon;
        }
        // Tag quests cycle through the tag's members (distinct "catch them all" ones too); treasure has no species to show.
        if (objective.targetSpeciesTag().isPresent()
                && !objective.targetSpeciesTag().get().equals(FishtasticItemTags.TREASURE)) {
            return buildCyclingSpeciesIcon(questId, objective.targetSpeciesTag().get(), objective.excludeSpeciesTag(),
                    Minecraft.getInstance().level.registryAccess(), 1f);
        }
        return null;
    }

    /**
     * Icon for quests that name no species: the encyclopedia's own weather / time-of-day glyphs and
     * zone art, falling back to the angler fish only when the objective has no such condition.
     */
    private UIElement<?> objectiveConditionIcon(QuestObjective objective) {
        PageSurface surface = new PageSurface(16, 16);
        if (objective.weatherCondition().isPresent()) {
            switch (objective.weatherCondition().get()) {
                case CLEAR -> surface.icon(PageIcons.SUN, 1, 1, PageIcons.SUN_RGB, -1, 2);
                case RAIN -> surface.icon(PageIcons.RAIN, 1, 1, PageIcons.RAIN_RGB, -1, 2);
                case SNOW -> surface.icon(PageIcons.SNOW, 1, 1, PageIcons.SNOW_RGB, -1, 2);
                case THUNDER -> surface.icon(PageIcons.THUNDER, 1, 1, PageIcons.THUNDER_RGB, -1, 2);
            }
            return surface;
        }
        if (!objective.timeConditions().isEmpty()) {
            switch (objective.timeConditions().get(0)) {
                case DAWN -> surface.icon(PageIcons.DAWN, 1, 1, PageIcons.DAWN_RGB, -1, 2);
                case DAY -> surface.icon(PageIcons.SUN, 1, 1, PageIcons.SUN_RGB, -1, 2);
                case DUSK -> surface.icon(PageIcons.DUSK, 1, 1, PageIcons.DUSK_RGB, -1, 2);
                case NIGHT -> surface.icon(PageIcons.NIGHT, 1, 1, PageIcons.NIGHT_RGB, -1, 2);
            }
            return surface;
        }
        if (objective.zoneCondition().isPresent()) {
            ResourceLocation zoneIcon = ZoneIconTextures.get(objective.zoneCondition().get());
            if (zoneIcon != null) {
                surface.texture(zoneIcon, 0, 0, 16, 16, ZoneIconTextures.TEXTURE_PX, ZoneIconTextures.TEXTURE_PX);
                return surface;
            }
        }
        return UI.itemRenderer(new ItemStack(FishtasticItems.ANGLER_FISH.value())).showCount(false);
    }

    private void addCollapsedRewardPreview(ManualContainer row, Quest quest, int headerHeight, boolean showTokens) {
        List<QuestReward.RewardItem> rewards = quest.reward().items();
        int previewCount = Math.min(3, rewards.size());
        for (int i = 0; i < previewCount; i++) {
            ItemStack stack = rewards.get(i).toStack();
            UIElement<?> icon = inspectionItem(stack);
            place(row, icon, 164 + i * 10, (headerHeight - 16) / 2);
        }
        if (rewards.size() > 3) {
            Label overflow = new Label("+" + (rewards.size() - 3), 0xFFFFFFFF).init(tempContext);
            ManualContainer badge = UI.manualContainer().setSize(overflow.getSize().x + 4, 10).backgroundColor(COLOR_SHELL);
            place(badge, overflow, 2, 0);
            place(row, badge, 198, (headerHeight - 16) / 2 + 3);
        }
        if (showTokens && quest.reward().questTokens() > 0) {
            place(row, rewardCoinIconFor(quest.reward().questTokens()), QUEST_ROW_WIDTH - 81, (headerHeight - 16) / 2);
            row.addChildAt(new Label(String.valueOf(quest.reward().questTokens()), COLOR_SELECTION).init(tempContext),
                    QUEST_ROW_WIDTH - 48f, headerHeight / 2f);
        }
    }

    private ItemButton inspectionItem(ItemStack stack) {
        ItemButton icon = new ItemButton(stack) {
            @Override
            public boolean handleEvent(UIEvent event) {
                if (event.getType() == UIEvent.Type.CLICK && !getBounds().contains(event.getMouseX(), event.getMouseY())) return false;
                return super.handleEvent(event);
            }
        };
        icon.showCount(false).clearOnMouseEnter().clearOnMouseExit();
        String tooltip = stack.getHoverName().getString() + " x" + stack.getCount();
        icon.tooltip(uiScreen, tooltipPanel(tooltip, 0xFFFFFFFF));
        return icon;
    }

    private VBox buildExpandedQuestDetails(Quest quest, PlayerQuestState.QuestProgress progress) {
        VBox details = UI.vbox().spacing(6).alignment(VBox.Alignment.LEFT);
        String description = quest.category() == QuestCategory.TUTORIAL
                ? translated("screen.fishtastic.quest_log.tutorial_evidence", progress.displayCount(1))
                : describeWithSpoilerGuard(quest);
        boolean headerRepeatsDescription = quest.category() != QuestCategory.TUTORIAL
                && description.trim().replaceFirst("[.]$", "").equals(objectiveSummary(quest, quest.objective()
                        .effectiveTargetCount(Minecraft.getInstance().level.registryAccess())));
        if (!description.isEmpty() && !headerRepeatsDescription) details.addChild(new Label(description, 0xFFFFFFFF).maxWidth(286).init(tempContext));
        if (!headerRepeatsDescription && hasEnvironmentCondition(quest.objective())) {
            details.addChild(new Label(buildConditionTooltipText(quest.objective()), COLOR_MUTED).maxWidth(286).init(tempContext));
        }

        QuestObjective objective = quest.objective();
        if (objective.distinctSpecies() && objective.targetSpeciesTag().isPresent()) {
            details.addChild(copy("collection.once", COLOR_MUTED));
            details.addChild(buildTagFishList(objective.targetSpeciesTag().get(), objective.excludeSpeciesTag(),
                    Minecraft.getInstance().level.registryAccess(), key -> progress.caughtSpecies().contains(key.location())));
        } else if (objective.distinctBaitTag().isPresent()) {
            List<ResourceLocation> usedBaits = progress.caughtSpecies();
            details.addChild(buildTagFishList(objective.distinctBaitTag().get(), Optional.empty(),
                    Minecraft.getInstance().level.registryAccess(), key -> usedBaits.contains(key.location())));
        }

        details.addChild(new Label(translated("screen.fishtastic.quest_log.rewards"), 0xFFFFFFFF).init(tempContext));
        details.addChild(buildExpandedRewards(quest));
        return details;
    }

    private ManualContainer buildExpandedRewards(Quest quest) {
        ManualContainer rewards = UI.manualContainer().setSize(290, 22);
        if (quest.reward().questTokens() > 0) {
            HBox tokens = UI.hbox().spacing(3).alignment(HBox.Alignment.CENTER);
            tokens.addChild(rewardCoinIconFor(quest.reward().questTokens()));
            tokens.addChild(new Label(translated("screen.fishtastic.quest_log.tokens", quest.reward().questTokens()), COLOR_SELECTION).init(tempContext));
            place(rewards, tokens, 0, 0);
        }
        if (!quest.reward().items().isEmpty()) {
            VBox items = UI.vbox().spacing(-1).alignment(VBox.Alignment.LEFT);
            for (QuestReward.RewardItem rewardItem : quest.reward().items()) {
                ItemStack stack = rewardItem.toStack();
                HBox item = UI.hbox().spacing(3).alignment(HBox.Alignment.CENTER);
                item.addChild(inspectionItem(stack));
                item.addChild(new Label(stack.getHoverName().getString() + " x" + stack.getCount(), 0xFFFFFFFF)
                        .maxWidth(142).init(tempContext));
                items.addChild(item);
            }
            items.forceLayout();
            rewards.setSize(290, Math.max(22, items.getSize().y));
            place(rewards, items, 123, 0);
        }
        return rewards;
    }

    private void toggleExpanded(int destination, ResourceLocation questId) {
        QuestRowRefs clicked = questRowRefs.get(questId);
        if (clicked != null && questViewport != null) {
            scrollAnchor = questId;
            scrollAnchorY = clicked.row().getPosition().y - questViewport.scrollOffset;
        }
        heldQuest = questId;
        ResourceLocation current = expandedQuestByDestination.get(destination);
        if (questId.equals(current)) expandedQuestByDestination.remove(destination);
        else expandedQuestByDestination.put(destination, questId);
        uiScreen.runDeferred(this::buildUI);
    }

    /** Scrolls {@code questKey}'s row roughly into view and gives it a one-off "here it is" pulse. */
    private void pulseQuestRow(ResourceKey<Quest> questKey) {
        QuestRowRefs refs = questRowRefs.get(questKey.location());
        if (refs == null) return;
        if (questViewport != null) questViewport.setScrollOffset(Math.max(0f,
                refs.row().getPosition().y - 36f));
        refs.row().playAnimation(new FloatKeyframeAnimation(
                "companionBondQuestFocus",
                List.of(new Keyframe(0f, 1f), new Keyframe(0.35f, 1.12f), new Keyframe(1.1f, 1f)),
                v -> refs.row().setTargetScale(v, false),
                () -> refs.row().setTargetScale(1f, false)));
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

    private SpriteData questRowBackgroundSprite(boolean claimed, QuestDifficulty difficulty) {
        ResourceLocation texture;
        if (claimed) {
            texture = QUEST_ROW_BG_TEXTURE_CLAIMED;
        } else {
            texture = switch (difficulty) {
                case GOLD -> QUEST_ROW_BG_TEXTURE_GOLD;
                case SILVER -> QUEST_ROW_BG_TEXTURE_SILVER;
                case BRONZE -> QUEST_ROW_BG_TEXTURE;
            };
        }
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
        return obj.biomeCondition().isPresent() || !obj.timeConditions().isEmpty() || obj.weatherCondition().isPresent()
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

        if (!obj.timeConditions().isEmpty()) {
            FishProfile.TimeOfDay timeOfDay = FishProfile.TimeOfDay.fromGameTime(mc.level.getDayTime());
            if (!obj.timeConditions().contains(timeOfDay)) return false;
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
        if (!obj.timeConditions().isEmpty()) {
            StringBuilder times = new StringBuilder();
            for (FishProfile.TimeOfDay t : obj.timeConditions()) {
                if (!times.isEmpty()) times.append("/");
                times.append(formatConditionWord(t.getSerializedName()));
            }
            parts.add(times.toString());
        }
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

    private static boolean isFishSpecies(ResourceKey<Item> speciesKey) {
        return BuiltInRegistries.ITEM.getOptional(speciesKey.location())
                .map(item -> item.builtInRegistryHolder().is(FishtasticItemTags.FISH))
                .orElse(false);
    }

    /**
     * Non-fish quest targets (trash, treasure) are never tracked in the encyclopedia, so there's
     * no spoiler to guard - they're always treated as "discovered" and shown with their real
     * sprite/name instead of a silhouette.
     */
    private static boolean isDiscovered(ResourceKey<Item> speciesKey) {
        return !isFishSpecies(speciesKey) || FishEncyclopediaClientCache.getCatchCount(speciesKey.location()) > 0;
    }

    /**
     * Unlisted fish (secret one-off variants) don't get a silhouette placeholder anywhere in the
     * quest log - they're left out entirely until actually discovered, mirroring the
     * encyclopedia's own home-screen filtering.
     */
    private static boolean isHiddenUnlistedSpecies(ResourceKey<Item> speciesKey) {
        if (isDiscovered(speciesKey)) return false;
        return BuiltInRegistries.ITEM.getOptional(speciesKey.location())
                .map(item -> item.builtInRegistryHolder().is(FishtasticItemTags.UNLISTED_FISH))
                .orElse(false);
    }

    private static String itemDisplayName(Item item) {
        return Component.translatable(item.getDescriptionId()).getString();
    }

    /**
     * If this quest's objective names a single, not-yet-discovered species, blanks out that
     * species' display name wherever it appears in the free-text description (e.g. "Catch 3
     * Moorish Idol in total" becomes "Catch 3 ??? in total"). Best-effort substring match against
     * hand-authored prose: a description that doesn't literally contain the item's display name
     * (different phrasing, pluralization, etc.) is left untouched rather than guessed at.
     */
    private static String describeWithSpoilerGuard(Quest quest) {
        String description = quest.description();
        Optional<ResourceKey<Item>> targetSpecies = quest.objective().targetSpecies();
        if (targetSpecies.isEmpty() || isDiscovered(targetSpecies.get())) return description;

        Item item = BuiltInRegistries.ITEM.getOptional(targetSpecies.get().location()).orElse(null);
        if (item == null) return description;

        String name = itemDisplayName(item);
        if (name.isBlank() || !description.contains(name)) return description;
        return description.replace(name, "???");
    }

    /**
     * Small icon for a species: its real sprite once discovered, a black silhouette until then -
     * or {@code null} if this is an undiscovered unlisted fish, which gets no slot at all (see
     * {@link #isHiddenUnlistedSpecies}).
     */
    @Nullable
    private SilhouetteItemButton buildSpeciesIcon(ResourceKey<Item> speciesKey, float scale) {
        return buildSpeciesIcon(speciesKey, scale, isDiscovered(speciesKey));
    }

    /**
     * Same as {@link #buildSpeciesIcon(ResourceKey, float)}, but with the discovered/silhouette
     * state passed in explicitly rather than derived from the fish encyclopedia — used by a bait
     * checklist, where "discovered" means "used on this quest already" instead.
     */
    @Nullable
    private SilhouetteItemButton buildSpeciesIcon(ResourceKey<Item> speciesKey, float scale, boolean discovered) {
        if (isHiddenUnlistedSpecies(speciesKey)) return null;

        Item item = BuiltInRegistries.ITEM.getOptional(speciesKey.location()).orElse(Items.COD);

        SilhouetteItemButton icon = new SilhouetteItemButton(new ItemStack(item)) {
            @Override
            public boolean handleEvent(UIEvent event) {
                if (event.getType() == UIEvent.Type.CLICK && !getBounds().contains(event.getMouseX(), event.getMouseY())) return false;
                return super.handleEvent(event);
            }
        };
        icon.itemScale(scale);
        icon.setSilhouette(!discovered);

        String tooltipText = discovered ? itemDisplayName(item) : "???";
        icon.tooltip(uiScreen, tooltipPanel(tooltipText, discovered ? 0xFFFFFFFF : COLOR_MUTED));

        return icon;
    }

    /**
     * Resolves every species in {@code tag} (minus {@code excludeTag} members, which can never
     * satisfy the quest), sorted descending by {@link FishProfile#baseWeight()} so
     * rarer/heavier-weighted fish lead. Shared by the "collect one of each" checklist and the
     * non-distinct quest's cycling icon.
     */
    private static List<ResourceKey<Item>> resolveSortedTagMembers(TagKey<Item> tag, Optional<TagKey<Item>> excludeTag, RegistryAccess registryAccess) {
        Registry<Item> items = registryAccess.registryOrThrow(Registries.ITEM);
        Registry<FishProfile> profiles = registryAccess.registryOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);

        List<ResourceKey<Item>> members = new ArrayList<>();
        for (Holder<Item> holder : items.getTagOrEmpty(tag)) {
            if (excludeTag.isPresent() && holder.is(excludeTag.get())) continue;
            holder.unwrapKey().filter(key -> !isHiddenUnlistedSpecies(key)).ifPresent(members::add);
        }
        members.sort(Comparator.comparingInt((ResourceKey<Item> key) -> {
            ResourceKey<FishProfile> profileKey = ResourceKey.create(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, key.location());
            return profiles.getOptional(profileKey).map(FishProfile::baseWeight).orElse(0);
        }).reversed().thenComparing(key -> key.location().toString()));
        return members;
    }

    /**
     * Builds the horizontal, wrapping list of every species in {@code tag} (minus
     * {@code excludeTag} members). Discovered species show their real sprite; undiscovered ones
     * render as silhouettes, mirroring the single-species icon's spoiler guard.
     */
    private VBox buildTagFishList(TagKey<Item> tag, Optional<TagKey<Item>> excludeTag, RegistryAccess registryAccess) {
        return buildTagFishList(tag, excludeTag, registryAccess, QuestLogScreen::isDiscovered);
    }

    /**
     * Same as {@link #buildTagFishList(TagKey, Optional, RegistryAccess)}, but with the
     * discovered/silhouette state for each icon supplied by {@code discoveredFn} instead of the
     * fish-encyclopedia default — used by a bait checklist, where "discovered" means "used on this
     * quest already" rather than "ever caught". Encyclopedia click-navigation is skipped for
     * non-fish members either way, since they have no encyclopedia entry to jump to.
     */
    private VBox buildTagFishList(TagKey<Item> tag, Optional<TagKey<Item>> excludeTag, RegistryAccess registryAccess,
            java.util.function.Predicate<ResourceKey<Item>> discoveredFn) {
        List<ResourceKey<Item>> members = resolveSortedTagMembers(tag, excludeTag, registryAccess);

        VBox wrap = UI.vbox().spacing(4).alignment(VBox.Alignment.CENTER);
        for (int i = 0; i < members.size(); i += QUEST_TAG_LIST_ICONS_PER_ROW) {
            int rowEnd = Math.min(i + QUEST_TAG_LIST_ICONS_PER_ROW, members.size());
            HBox iconRow = UI.hbox().spacing(6).alignment(HBox.Alignment.CENTER);
            for (int j = i; j < rowEnd; j++) {
                ResourceKey<Item> speciesKey = members.get(j);
                SilhouetteItemButton icon = buildSpeciesIcon(speciesKey, QUEST_TAG_LIST_ICON_SCALE, discoveredFn.test(speciesKey));
                // Non-fish tag members (e.g. a bait checklist) have no encyclopedia entry to jump to.
                if (icon == null) continue;
                if (isFishSpecies(speciesKey)) wireEncyclopediaNavigation(icon, speciesKey);
                iconRow.addChild(icon);
            }
            wrap.addChild(iconRow);
        }
        return wrap;
    }

    /**
     * Builds the progress-bar icon for a non-{@code distinct_species} tag quest: since no single
     * species is "the" target, the icon cycles through every tag member instead (see
     * {@link #CYCLE_INTERVAL_TICKS}, driven from {@link #containerTick}). Registered into
     * {@link #cyclingIconRefs} under {@code questId} so the tick loop can find it.
     */
    @Nullable
    private SilhouetteItemButton buildCyclingSpeciesIcon(ResourceLocation questId, TagKey<Item> tag, Optional<TagKey<Item>> excludeTag,
            RegistryAccess registryAccess, float scale) {
        List<ResourceKey<Item>> members = resolveSortedTagMembers(tag, excludeTag, registryAccess);
        if (members.isEmpty()) return null;

        SilhouetteItemButton icon = new SilhouetteItemButton(ItemStack.EMPTY).requireClickInside();
        icon.itemScale(scale);
        SpriteRectangle.SpriteRectangleImpl tooltip = tooltipPanel("", 0xFFFFFFFF);
        icon.tooltip(uiScreen, tooltip);

        CyclingIconState state = new CyclingIconState(icon, tooltip, members);
        updateCyclingIcon(state, 0);
        cyclingIconRefs.put(questId, state);

        // Pausing on hover (rather than always reading the live index) means the fish clicked is
        // always the one the player was actually looking at, not whatever it cycled to mid-click.
        icon.onMouseEnter(e -> state.hovered = true);
        icon.onMouseExit(e -> state.hovered = false);
        icon.onClick(e -> navigateToEncyclopedia(state.species.get(state.lastIndex)));

        return icon;
    }

    /**
     * Wires an icon (single-species progress-bar icon or checklist icon) so clicking it closes
     * the quest log and jumps straight to {@code speciesKey}'s encyclopedia info page, whether
     * that species is discovered or still a silhouette.
     */
    private static void wireEncyclopediaNavigation(SilhouetteItemButton icon, ResourceKey<Item> speciesKey) {
        icon.onClick(e -> navigateToEncyclopedia(speciesKey));
    }

    /**
     * Opening the encyclopedia is a server round-trip (it's a separate menu), so the target
     * species is stashed on {@link FishEncyclopediaScreen} for the next screen it builds to pick up.
     */
    private static void navigateToEncyclopedia(ResourceKey<Item> speciesKey) {
        FishEncyclopediaScreen.selectOnNextOpen(speciesKey.location());
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            NetworkApiSided.sendToServer(new RequestFishEncyclopediaPacket(true), RequestFishEncyclopediaPacket.STREAM_CODEC);
        }
    }

    /** Swaps a cycling icon's sprite/silhouette/tooltip to {@code index}'s species, skipping the work if it's unchanged since the last call. */
    private static void updateCyclingIcon(CyclingIconState state, int index) {
        if (state.species.isEmpty() || index == state.lastIndex) return;
        state.lastIndex = index;

        ResourceKey<Item> speciesKey = state.species.get(index);
        Item item = BuiltInRegistries.ITEM.getOptional(speciesKey.location()).orElse(Items.COD);
        boolean discovered = isDiscovered(speciesKey);

        state.icon.itemStack(new ItemStack(item));
        state.icon.setSilhouette(!discovered);
        state.tooltip.text(discovered ? itemDisplayName(item) : "???", 0xFFFFFFFF);
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
        ResourceLocation questId = questKey.location();
        PlayerQuestState.QuestProgress progress = QuestClientCache.getProgress(questId);

        boolean claimed = progress.claimed();
        boolean completed = progress.completed();
        boolean canClaim = completed && !claimed;

        int targetCount = quest.objective().effectiveTargetCount(Minecraft.getInstance().level.registryAccess());
        int currentCount = progress.displayCount(targetCount);
        float fraction = targetCount > 0 ? Math.min(1f, (float) currentCount / targetCount) : 0f;

        String baseDisplayName = quest.displayName().isEmpty() ? questId.getPath() : quest.displayName();
        int nameColor = claimed ? 0xFFAAAAAA : 0xFFFFFFFF;
        String nameText = claimed ? translated("screen.fishtastic.quest_log.quest_done", baseDisplayName) : baseDisplayName;

        VBox row = UI.vbox().spacing(3).padding(4).alignment(VBox.Alignment.CENTER);
        row.backgroundSprite(questRowBackgroundSprite(claimed, quest.difficulty()));
        if (claimed) {
            row.setTargetScale(CLAIMED_QUEST_ROW_SCALE, false);
        }

        Label nameLabel = new Label(nameText, nameColor).init(tempContext);

        SpriteButton claimBtn = new SpriteButton(40f, 14f, QUEST_CLAIM_BUTTON_TEXTURE)
                .texture(questClaimButtonSprite())
                .text(translated("screen.fishtastic.quest_log.claim"), 0xFFFFFFFF);
        claimBtn.onMouseEnter(e -> claimBtn.setTargetScale(1.12f, true));
        claimBtn.onMouseExit(e -> claimBtn.setTargetScale(1.0f, true));
        final ResourceLocation fId = questId;
        claimBtn.onClick(e -> {
            claimBtn.addClickBounceEffect();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                NetworkApiSided.sendToServer(new CompleteQuestPacket(fId), CompleteQuestPacket.STREAM_CODEC);
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
            String state = translated(met ? "screen.fishtastic.quest_log.condition_met" : "screen.fishtastic.quest_log.condition_unmet");
            statusPip.tooltip(uiScreen, tooltipPanel(buildConditionTooltipText(quest.objective()) + "\n" + state,
                    met ? 0xFF8CE68C : COLOR_MUTED));
        }

        // Pip sits tight against the title (its own low-spacing group); the wider spacing(8)
        // stays between the title group and the claim button.
        HBox titleGroup = UI.hbox().spacing(2).alignment(HBox.Alignment.CENTER);
        if (statusPip != null) titleGroup.addChild(statusPip);
        titleGroup.addChild(nameLabel);
        // Bronze is every other category's implicit default (no badge anywhere else in the log),
        // so only Daily needs a badge at all — and only once it actually rolls above Bronze.
        if (quest.category() == QuestCategory.DAILY && quest.difficulty() != QuestDifficulty.BRONZE) {
            String tierKey = "screen.fishtastic.quest_log.difficulty." + quest.difficulty().getSerializedName();
            int tierColor = quest.difficulty() == QuestDifficulty.GOLD ? 0xFFFFD700 : 0xFFC0C0C0;
            titleGroup.addChild(new Label(translated(tierKey), tierColor).init(tempContext));
        }

        HBox nameRow = UI.hbox().spacing(8).alignment(HBox.Alignment.CENTER);
        nameRow.addChild(titleGroup);
        nameRow.addChild(claimBtn);

        row.addChild(nameRow);

        if (!quest.description().isEmpty()) {
            row.addChild(new Label(describeWithSpoilerGuard(quest), 0xFF888888).maxWidth(150).centered(true).init(tempContext));
        }

        // "Collect one of each" tag quests get a discovered-fish checklist between the
        // description and the progress bar; a plain species-count quest doesn't (see part 2 below
        // for its own, single-icon treatment instead).
        Set<IUIElement> tagListIcons = new HashSet<>();
        if (quest.objective().distinctSpecies() && quest.objective().targetSpeciesTag().isPresent()) {
            VBox tagList = buildTagFishList(quest.objective().targetSpeciesTag().get(), quest.objective().excludeSpeciesTag(),
                    Minecraft.getInstance().level.registryAccess());
            collectLeaves(tagList, tagListIcons);
            row.addChild(tagList);
        } else if (quest.objective().distinctBaitTag().isPresent()) {
            // "Discovered" here means "already used on this quest" (progress.caughtSpecies() is
            // fed bait ids for a distinctBaitTag objective — see QuestTracker#onCatch), not the
            // fish-encyclopedia default buildTagFishList otherwise falls back to.
            List<ResourceLocation> usedBaitIds = progress.caughtSpecies();
            VBox tagList = buildTagFishList(quest.objective().distinctBaitTag().get(), Optional.empty(),
                    Minecraft.getInstance().level.registryAccess(),
                    speciesKey -> usedBaitIds.contains(speciesKey.location()));
            collectLeaves(tagList, tagListIcons);
            row.addChild(tagList);
        }

        Label countLabel = new Label(translated("screen.fishtastic.quest_log.progress_count", currentCount, targetCount), 0xFFAAAAAA).init(tempContext);
        ThinProgressBar bar = new ThinProgressBar();
        bar.progressImmediate(fraction);

        HBox progressRow = UI.hbox().spacing(6).alignment(HBox.Alignment.CENTER);
        SilhouetteItemButton speciesIcon = null;
        if (quest.objective().targetSpecies().isPresent()) {
            ResourceKey<Item> speciesKey = quest.objective().targetSpecies().get();
            speciesIcon = buildSpeciesIcon(speciesKey, QUEST_SPECIES_ICON_SCALE);
            if (speciesIcon != null) {
                wireEncyclopediaNavigation(speciesIcon, speciesKey);
                progressRow.addChild(speciesIcon);
            }
        } else if (!quest.objective().distinctSpecies() && quest.objective().targetSpeciesTag().isPresent()
                && !quest.objective().targetSpeciesTag().get().equals(FishtasticItemTags.TREASURE)) {
            // No single species is "the" target here, unlike a plain target_species quest — cycle
            // through the tag's members instead of picking one arbitrarily. Treasure isn't fish
            // species to identify, so its catch-count quests skip the cycling icon entirely.
            speciesIcon = buildCyclingSpeciesIcon(questId, quest.objective().targetSpeciesTag().get(),
                    quest.objective().excludeSpeciesTag(), Minecraft.getInstance().level.registryAccess(), QUEST_SPECIES_ICON_SCALE);
            if (speciesIcon != null) progressRow.addChild(speciesIcon);
        }
        progressRow.addChild(bar);
        progressRow.addChild(countLabel);
        row.addChild(progressRow);

        if (quest.reward().questTokens() > 0 || !quest.reward().items().isEmpty()) {
            row.addChild(buildRewardRow(quest));
        }

        // Each leaf gets its own hover scale (rather than the whole row scaling as a unit)
        // because hover events only ever reach the single deepest leaf element under the cursor
        // (see UIScreen#findElementAt) — a container like this VBox row can never itself be the
        // hit target. claimBtn and the species icons are excluded since they're ItemButtons that
        // already drive their own, larger hover scale.
        Set<IUIElement> hoverExclusions = new HashSet<>(tagListIcons);
        hoverExclusions.add(claimBtn);
        if (speciesIcon != null) hoverExclusions.add(speciesIcon);
        attachLeafHoverScale(row, QUEST_ROW_LEAF_HOVER_SCALE, hoverExclusions);

        questRowRefs.put(questId, new QuestRowRefs(row, nameLabel, claimBtn, bar, countLabel, targetCount, baseDisplayName, quest.objective(), quest.difficulty(), statusPip));
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

    /** Collects every leaf descendant of {@code element} (see {@link #attachLeafHoverScale}) into {@code out}. */
    private static void collectLeaves(IUIElement element, Set<IUIElement> out) {
        if (element instanceof UIContainer<?> container) {
            for (IUIElement child : container.getChildren()) {
                collectLeaves(child, out);
            }
            return;
        }
        out.add(element);
    }

    /**
     * Plays the quest-claim reward feedback: a bunch of mini coins fly from the claimed
     * quest row up to the token balance display while the balance counts up to its new total.
     * Fired optimistically on click, like the shop's purchase-fall feedback, rather than
     * waiting on the server's confirming {@link QuestSyncPacket}.
     */
    private void triggerQuestClaimReward(UIElement<?> row, Quest quest) {
        int reward = quest.reward().questTokens();
        if (reward <= 0 || tokenIconRow == null || tokenBalanceLabel == null) return;

        int oldBalance = QuestClientCache.getTokenBalance();
        int newBalance = oldBalance + reward;

        float totalDuration = spawnQuestClaimCoins(row, reward);
        claimAnimEndMs = System.currentTimeMillis() + (long) (totalDuration * 1000f) + 100L;

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

    // Reward-row coin icon scales with the token amount so bigger quests visibly promise a
    // bigger payout at a glance, before the player even reads the number. Boundaries are tuned
    // from the actual quest_tokens distribution across every quest (daily/explorer/collector/
    // challenge/mastery), which falls into six natural clusters with clean gaps between them:
    //   4-7 (daily bronze), 9-13 (daily silver), 14-21 (daily gold + small explorer/mastery),
    //   25-45, 50-60 (mid explorer/collector/challenge/mastery), 75-206 (top mastery/challenge,
    //   with nothing between 61 and 74).
    private static final int REWARD_ICON_TWO_COINS_THRESHOLD = 9;
    private static final int REWARD_ICON_THREE_COINS_THRESHOLD = 14;
    private static final int REWARD_ICON_SMALL_PILE_THRESHOLD = 25;
    private static final int REWARD_ICON_MEDIUM_PILE_THRESHOLD = 50;
    private static final int REWARD_ICON_LARGE_PILE_THRESHOLD = 75;

    // Shop price-tag coin icon — a separate six-tier ladder from the reward-row one above, tuned
    // to shop_entry "cost" instead of quest "quest_tokens". The two economies have very different
    // shapes (shop costs cluster hard at 18 (fence arches) and 100 (every tank shape), rather than
    // the reward side's smooth daily->explorer->mastery climb), so they need their own boundaries
    // even though they share the same six textures. Clusters from the actual shop_entry data:
    //   5-8 (worms/starter baits), 10-16 (common baits/cosmetics), 18 (fence arches, exactly),
    //   20-25, 35-50 (charms/cosmetics), 55-100 (top charms + every tank shape, with nothing
    //   between 70 and 85).
    private static final int SHOP_ICON_TWO_COINS_THRESHOLD = 10;
    private static final int SHOP_ICON_THREE_COINS_THRESHOLD = 18;
    private static final int SHOP_ICON_SMALL_PILE_THRESHOLD = 20;
    private static final int SHOP_ICON_MEDIUM_PILE_THRESHOLD = 35;
    private static final int SHOP_ICON_LARGE_PILE_THRESHOLD = 55;

    private static final ResourceLocation COIN_ICON_ONE_TEXTURE = Fishtastic.id("textures/gui/one_coin.png");
    private static final ResourceLocation COIN_ICON_TWO_TEXTURE = Fishtastic.id("textures/gui/two_coins.png");
    private static final ResourceLocation COIN_ICON_THREE_TEXTURE = Fishtastic.id("textures/gui/three_coins.png");

    /** Builds the reward-row icon for a quest's token payout: a loose-coins sprite for small amounts, scaling up through coin-pile item icons for larger ones. */
    private static UIElement<?> rewardCoinIconFor(int questTokens) {
        return coinLadderIcon(questTokens, 2, 3, 3, 8, 20);
    }

    /** Builds the shop price-tag icon for a token cost, on its own threshold ladder — see {@link #SHOP_ICON_TWO_COINS_THRESHOLD}. */
    private static UIElement<?> shopCoinIconFor(int cost) {
        return coinLadderIcon(cost, 2, 3, 3, 8, 20);
    }

    private static UIElement<?> coinLadderIcon(int amount, int twoCoinsThreshold, int threeCoinsThreshold,
            int smallPileThreshold, int mediumPileThreshold, int largePileThreshold) {
        if (amount >= largePileThreshold) {
            return UI.itemRenderer(new ItemStack(FishtasticItems.PILE_OF_COINS.value()));
        }
        if (amount >= mediumPileThreshold) {
            return UI.itemRenderer(new ItemStack(FishtasticItems.MEDIUM_PILE_OF_COINS.value()));
        }
        if (amount >= smallPileThreshold) {
            return UI.itemRenderer(new ItemStack(FishtasticItems.SMALL_PILE_OF_COINS.value()));
        }
        if (amount >= threeCoinsThreshold) {
            return coinSprite(COIN_ICON_THREE_TEXTURE, 8, 11);
        }
        if (amount >= twoCoinsThreshold) {
            return coinSprite(COIN_ICON_TWO_TEXTURE, 12, 8);
        }
        return coinSprite(COIN_ICON_ONE_TEXTURE, 8, 7);
    }

    private static ManualContainer coinSprite(ResourceLocation texture, int width, int height) {
        ManualContainer slot = UI.manualContainer().setSize(16, 16);
        place(slot, UI.spriteRectangle(width, height, texture)
                .texture(new SpriteData(texture).uv(0, 0, width, height).textureSize(width, height)),
                (16 - width) / 2, (16 - height) / 2);
        return slot;
    }

    private HBox buildRewardRow(Quest quest) {
        HBox row = UI.hbox().spacing(6).alignment(HBox.Alignment.CENTER);
        if (quest.reward().questTokens() > 0) {
            row.addChild(rewardCoinIconFor(quest.reward().questTokens()));
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

    private ManualContainer insetPanel(int width, int height) {
        return UI.manualContainer().setSize(width, height)
                .backgroundSprite(questRowBackgroundSprite(false, QuestDifficulty.BRONZE));
    }

    private Label copy(String key, int color, Object... args) {
        return new Label(translated("screen.fishtastic.quest_log." + key, args), color).init(tempContext);
    }

    private ManualContainer buildCleanupGoalPanel() {
        ManualContainer page = UI.manualContainer().setSize(QUEST_PAGE_WIDTH, 193);
        int total = QuestClientCache.getCleanupGoalTotal();
        int threshold = QuestClientCache.getCleanupGoalThreshold();
        if (threshold <= 0) {
            place(page, copy("cleanup.loading", COLOR_MUTED), 8, 8);
            return page;
        }
        int cycle = total % threshold;
        ManualContainer summary = insetPanel(QUEST_PAGE_WIDTH, 60);
        place(summary, copy("cleanup.milestone", 0xFFFFFFFF, threshold), 10, 6);
        place(summary, copy("cleanup.removed", 0xFFFFFFFF, cycle, threshold), 10, 19);
        place(summary, copy("cleanup.left", COLOR_MUTED, threshold - cycle), 248, 19);
        place(summary, new ThinProgressBar(296, 5).flatColor(COLOR_PROGRESS)
                .progressImmediate((float) cycle / threshold), 10, 37);
        place(summary, copy("cleanup.pool", COLOR_SELECTION), 10, 47);
        place(page, summary, 0, 0);
        place(page, copy("cleanup.eligibility", 0xFFFFFFFF), 0, 67);
        place(page, copy("cleanup.proportional", COLOR_MUTED), 0, 79);

        ManualContainer personal = insetPanel(QUEST_PAGE_WIDTH, 33);
        List<CleanupGoalProgress.Contributor> contributors = QuestClientCache.getCleanupGoalContributors();
        Minecraft mc = Minecraft.getInstance();
        var own = contributors.stream().filter(c -> mc.player != null && c.playerUuid().equals(mc.player.getUUID())).findFirst();
        // The snapshot is capped: absence from its top contributors cannot establish zero.
        String ownText = own.map(c -> translated("screen.fishtastic.quest_log.cleanup.personal", c.amount(),
                cycle > 0 ? Math.round(c.amount() * 100f / cycle) : 0))
                .orElse(translated("screen.fishtastic.quest_log.cleanup.personal_unavailable"));
        place(personal, new Label(ownText, 0xFFFFFFFF).init(tempContext), 10, 6);
        place(personal, copy("cleanup.rollover", COLOR_MUTED, threshold), 10, 20);
        place(page, personal, 0, 96);
        place(page, copy("cleanup.contributors", 0xFFFFFFFF), 0, 137);
        VBox rows = UI.vbox().spacing(3).alignment(VBox.Alignment.LEFT);
        for (var contributor : contributors) {
            if (own.isPresent() && contributor.playerUuid().equals(own.get().playerUuid())) continue;
            ManualContainer row = UI.manualContainer().setSize(QUEST_PAGE_WIDTH - QUEST_SCROLL_GUTTER, 10);
            place(row, new Label(contributor.playerName(), COLOR_MUTED).maxWidth(65).init(tempContext), 4, 0);
            int percent = cycle > 0 ? Math.round(contributor.amount() * 100f / cycle) : 0;
            place(row, new ThinProgressBar(151, 5).flatColor(COLOR_PROGRESS)
                    .progressImmediate(cycle > 0 ? (float) contributor.amount() / cycle : 0), 77, 3);
            place(row, new Label(contributor.amount() + " (" + percent + "%)", COLOR_MUTED).init(tempContext), 239, 0);
            rows.addChild(row);
        }
        QuestViewport contributorViewport = new QuestViewport(QUEST_PAGE_WIDTH, 39);
        contributorViewport.setContent(rows);
        place(page, contributorViewport, 0, 152);
        return page;
    }

    private void rebuildCleanupGoalContributorsRow() {
        // The redesigned Cleanup page rebuilds from the authoritative snapshot.
    }

    private VBox buildShopPanel(Registry<ShopEntry> shopRegistry, long currentDay) {
        VBox page = UI.vbox().spacing(6).alignment(VBox.Alignment.LEFT);
        List<ResourceKey<ShopEntry>> stock = shopRegistry == null ? List.of() :
                ShopEntry.getActiveDailyShop(shopRegistry, currentDay, QuestClientCache.getShopRefreshCount(),
                        key -> QuestClientCache.getProgress(key.location()).claimed()).stream()
                        .filter(key -> shopRegistry.getOptional(key).map(entry -> !isEntrySoldOut(key, entry)).orElse(false))
                        .sorted(Comparator.<ResourceKey<ShopEntry>, Boolean>comparing(key ->
                                shopRegistry.getOptional(key).orElseThrow().cost() > QuestClientCache.getTokenBalance())
                                .thenComparing(key -> key.location().toString())).toList();
        if (stock.isEmpty()) page.addChild(copy("shop.empty", COLOR_MUTED).maxWidth(300));
        for (int i = 0; i < stock.size(); i += 2) {
            HBox row = UI.hbox().spacing(8).alignment(HBox.Alignment.TOP);
            for (int j = i; j < Math.min(stock.size(), i + 2); j++) {
                ResourceKey<ShopEntry> key = stock.get(j);
                row.addChild(buildMarketCard(key, shopRegistry.getOptional(key).orElseThrow()));
            }
            page.addChild(row);
        }
        int price = ShopEntry.refreshCost(QuestClientCache.getShopRefreshCount());
        ManualContainer reroll = insetPanel(QUEST_PAGE_WIDTH, 42);
        place(reroll, copy("shop.replace", 0xFFFFFFFF), 8, 6);
        place(reroll, copy("shop.next", COLOR_MUTED, ShopEntry.refreshCost(QuestClientCache.getShopRefreshCount() + 1)), 8, 23);
        place(reroll, shopCoinIconFor(price), 184, 9);
        place(reroll, new Label(String.valueOf(price), COLOR_SELECTION).init(tempContext), 204, 12);
        boolean affordable = QuestClientCache.getTokenBalance() >= price;
        SpriteButton button = marketButton(73, "shop.reroll", affordable);
        if (affordable) button.onClick(e -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) NetworkApiSided.sendToServer(new RefreshShopPacket(), RefreshShopPacket.STREAM_CODEC);
        });
        place(reroll, button, 234, 10);
        if (!affordable) {
            reroll.setSize(QUEST_PAGE_WIDTH, 55);
            place(reroll, copy("shop.need", COLOR_MUTED, price - QuestClientCache.getTokenBalance()), 8, 41);
        }
        if (!affordable) reroll.tooltip(uiScreen, tooltipPanel(
                translated("screen.fishtastic.quest_log.shop.need", price - QuestClientCache.getTokenBalance()), COLOR_MUTED));
        page.addChild(reroll);
        return page;
    }

    private SpriteButton marketButton(int width, String key, boolean enabled) {
        SpriteButton button = new SpriteButton(width, 16, enabled ? 0xFF777777 : 0xFF4A3540)
                .text(translated("screen.fishtastic.quest_log." + key), enabled ? 0xFFFFFFFF : COLOR_MUTED);
        if (enabled) button.texture(new SpriteData(grill24.fishtastic.util.Ids.withDefaultNamespace("textures/gui/widgets.png"))
                .uv(0, 66, 200, 20).textureSize(256, 256).renderMode(SpriteRenderMode.SLICE).slice(3, 3, 3, 3));
        return button;
    }

    private ManualContainer buildMarketCard(ResourceKey<ShopEntry> key, ShopEntry entry) {
        int cardWidth = (QUEST_PAGE_WIDTH - 8) / 2;
        boolean affordable = QuestClientCache.getTokenBalance() >= entry.cost();
        ManualContainer card = insetPanel(cardWidth, 62);
        int y = 6;
        for (var reward : entry.reward()) {
            place(card, UI.itemRenderer(reward.toItemStack()).showCount(false), 8, y);
            y += 17;
        }
        place(card, new Label(entry.displayName(), 0xFFFFFFFF).maxWidth(109).init(tempContext), 35, 6);
        place(card, shopCoinIconFor(entry.cost()), 35, 25);
        place(card, new Label(String.valueOf(entry.cost()), COLOR_SELECTION).init(tempContext), 52, 26);
        if (!affordable) place(card, copy("shop.need", COLOR_MUTED, entry.cost() - QuestClientCache.getTokenBalance()), 8, 44);
        SpriteButton buy = marketButton(43, "shop.buy", affordable);
        if (affordable) buy.onClick(e -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) NetworkApiSided.sendToServer(new PurchaseShopEntryPacket(key.location()), PurchaseShopEntryPacket.STREAM_CODEC);
        });
        place(card, buy, cardWidth - 50, 43);
        return card;
    }

    /** Header row letting the player spend a fixed token cost to reroll today's active shop entries. */
    private HBox buildShopRefreshRow() {
        int refreshCost = ShopEntry.refreshCost(QuestClientCache.getShopRefreshCount());
        boolean canAfford = QuestClientCache.getTokenBalance() >= refreshCost;

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
                NetworkApiSided.sendToServer(new RefreshShopPacket(), RefreshShopPacket.STREAM_CODEC);
            }
        });
        refreshBtn.setVisible(canAfford);
        shopRefreshBtn = refreshBtn;

        Label costLabel = new Label(String.valueOf(refreshCost), canAfford ? 0xFFFFAA00 : 0xFFFF4444).init(tempContext);
        shopRefreshCostLabel = costLabel;

        Label notEnoughLabel = new Label(translated("screen.fishtastic.quest_log.shop.not_enough_tokens"), 0xFFFF4444).init(tempContext);
        notEnoughLabel.setVisible(!canAfford);
        shopRefreshNotEnoughLabel = notEnoughLabel;

        refreshRow.addChild(refreshBtn);
        refreshRow.addChild(shopCoinIconFor(refreshCost));
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
        slot.setDebugName("shopSlot:" + key.location());

        ManualContainer fallingPanel = UI.manualContainer().setSize(SHOP_ITEM_PANEL_WIDTH, SHOP_ITEM_PANEL_HEIGHT);
        fallingPanel.setDebugName("shopFallingPanel:" + key.location());
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
        String nameText = entry.displayName().isEmpty() ? key.location().getPath() : entry.displayName();
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
        costRow.addChild(shopCoinIconFor(entry.cost()));
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
                NetworkApiSided.sendToServer(new PurchaseShopEntryPacket(fKey.location()), PurchaseShopEntryPacket.STREAM_CODEC);
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
        int purchaseCount = QuestClientCache.getPurchaseCount(key.location());
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
            boolean canAffordRefresh = QuestClientCache.getTokenBalance()
                    >= ShopEntry.refreshCost(QuestClientCache.getShopRefreshCount());
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
                PlayerQuestState.QuestProgress progress = QuestClientCache.getProgress(key.location());
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
            rebuildCleanupGoalContributorsRow();
        }

        for (Map.Entry<ResourceLocation, QuestRowRefs> e : questRowRefs.entrySet()) {
            ResourceLocation questId = e.getKey();
            QuestRowRefs refs = e.getValue();

            PlayerQuestState.QuestProgress progress = QuestClientCache.getProgress(questId);
            boolean claimed = progress.claimed();
            boolean completed = progress.completed();
            boolean canClaim = completed && !claimed;

            int currentCount = progress.displayCount(refs.targetCount());
            float fraction = refs.targetCount() > 0 ? Math.min(1f, (float) currentCount / refs.targetCount()) : 0f;

            int nameColor = claimed ? 0xFFAAAAAA : 0xFFFFFFFF;
            String nameText = claimed ? translated("screen.fishtastic.quest_log.quest_done", refs.baseDisplayName()) : refs.baseDisplayName();

            refs.nameLabel().text(nameText).color(nameColor);
            refs.claimButton().setVisible(canClaim);
            refs.progressBar().progressImmediate(fraction);
            refs.countLabel().text(translated("screen.fishtastic.quest_log.progress_count", currentCount, refs.targetCount()));
            if (refs.row() instanceof ManualContainer row) {
                row.backgroundSprite(questRowBackgroundSprite(false, refs.difficulty()));
            }
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
        if (encyclopediaHandlerInstalled) {
            FishEncyclopediaSyncPacket.registerClientHandler(savedEncyclopediaHandler);
        }
    }

    private static String translated(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
