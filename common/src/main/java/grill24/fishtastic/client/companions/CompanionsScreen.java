package grill24.fishtastic.client.companions;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.client.FishEncyclopediaClientCache;
import grill24.fishtastic.client.FishEncyclopediaClientHelper;
import grill24.fishtastic.client.FishEncyclopediaScreen;
import grill24.fishtastic.client.FishtasticKeyBinds;
import grill24.fishtastic.client.CompanionClientState;
import grill24.fishtastic.client.CompanionTutorialClientHandler;
import grill24.fishtastic.companion.CompanionGroups;
import grill24.fishtastic.companion.CompanionParty;
import grill24.fishtastic.companion.CompanionStorage;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.network.CompanionFeedbackPacket;
import grill24.fishtastic.network.EquipCompanionPacket;
import grill24.fishtastic.network.FishtasticTransport;
import grill24.fishtastic.network.MoveCompanionPacket;
import grill24.fishtastic.network.RenameCompanionPacket;
import grill24.fishtastic.network.RequestFishEncyclopediaPacket;
import grill24.fishtastic.network.RequestQuestLogPacket;
import grill24.fishtastic.network.UnequipCompanionPacket;
import io.github.currenj.gelatinui.GelatinUIScreen;
import io.github.currenj.gelatinui.gui.IRenderContext;
import io.github.currenj.gelatinui.gui.UIElement;
import io.github.currenj.gelatinui.gui.UIEvent;
import io.github.currenj.gelatinui.gui.minecraft.MinecraftRenderContext;
import io.github.currenj.gelatinui.gui.components.SpriteData;
import io.github.currenj.gelatinui.gui.components.SpriteRenderMode;
import io.github.currenj.gelatinui.gui.GelatinMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The companions screen (docs/companion-ui-redesign/layout-spec.md): the Captain's Log shell with
 * an Equipped view (three portrait cards over a species-first fish list) and an Unlocks view.
 *
 * <p>Everything inside the shell is one immediate-mode canvas, laid out in the spec's logical
 * pixels (the mockup renderer's coordinates). Each frame records the clickable rectangles it drew,
 * and clicks and tooltips resolve against that list, so what is drawn and what is clickable can't
 * drift apart. Every action is a packet that only describes a fish; the server finds it.
 */
public class CompanionsScreen extends GelatinUIScreen<GelatinMenu> {
    // ---- shell (shared numbers with QuestLogScreen) ----
    static final int SHELL_W = 416, SHELL_H = 232, RAIL_W = 74, PANEL_X = RAIL_W + 4, PANEL_W = 338, INSET = 11;
    static final int CX = PANEL_X + INSET, CW = PANEL_W - INSET * 2;
    // ---- house palette ----
    static final int SHELL = 0xFF201A24, RAIL = 0xFF2A202B, EDGE = 0xFF72505B, MUTED = 0xFFC9B8BE, HINT = 0xFF8E7E87,
            TITLE = 0xFFEBDAD1, GOLD = 0xFFFFAA00, READY = 0xFF5DCC7A, PROGRESS = 0xFFB86E88, WHITE = 0xFFFFFFFF,
            RAIL_SEL = 0xFF714452, RAIL_SEL_HOT = 0xFF7D4E5B, HOVER = 0xFF3A2B37, FILTER_SEL = 0xFF704450,
            TRACK = 0xFF352A32, THUMB = 0xFFC58A58, THUMB_HI = 0xFFFFE0A0, TOKEN_BOX = 0xFF3B2A29, FIELD = 0xFF15101A,
            ROW_HOT = 0xFF2E2430, PINK = 0xFFF27A9A, RED = 0xFFE02424, ERROR = 0xFFFF6B6B, EMPTY_CARD = 0xFF261E29,
            BUTTON_DIM = 0xFF3A2B37, CHIP = 0xFF4A3540;
    // ---- equipped view ----
    static final int CARD_Y = 26, CARD_W = 100, CARD_H = 60, CARD_GAP = 8;
    static final int FORMATION_Y = CARD_Y + CARD_H + 6, TOOL_Y = CARD_Y + CARD_H + 20, LIST_Y = TOOL_Y + 18,
            LIST_BOTTOM = SHELL_H - 20, ROW_W = CW - 8, SPECIES_H = 18, FISH_H = 15, MORE_H = 13, PAGE = 8, MORE_STEP = 16;
    // ---- unlocks view ----
    static final int TILE = 34, TILE_GAP = 5, TILE_COLS = 8, TILE_PITCH = TILE + 18, GRID_Y = 52, GRID_BOTTOM = SHELL_H - 8;
    /** Keyboard focus ring, the same colour as UiKeyboardNav's on the Captain's Log and Fishopedia. */
    static final int RING = 0xFFFFFF55;
    static final long FLASH_MS = 600, PULSE_MS = 500;
    static final int FEEDBACK_TICKS = 100;

    static final ResourceLocation PANEL = Fishtastic.id("textures/gui/generic_item_panel.png");
    static final ResourceLocation PANEL_GOLD = Fishtastic.id("textures/gui/generic_item_panel_gold_border.png");

    static final String[] HEART = {".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."};
    static final String[] STAR = {"..#..", ".###.", "#####", ".###.", ".#.#."};
    static final String[] CROWN = {"#.#.#", "#####", "#####"};
    static final String[] CHEV_R = {"#..", "##.", "###", "##.", "#.."};
    static final String[] CHEV_D = {"#####", ".###.", "..#.."};
    static final String[] CROSS = {"#...#", ".#.#.", "..#..", ".#.#.", "#...#"};
    static final String[] LENS = {".##..", "#..#.", "#..#.", ".##..", "...##"};
    static final String[] LOCK = {".###.", "#...#", "#####", "##.##", "#####"};
    static final String[] PLUS = {"..#..", "..#..", "#####", "..#..", "..#.."};
    static final String[] PILE = {".##.", "####", "####"};

    public enum View { EQUIPPED, UNLOCKS }
    enum Field { NONE, SEARCH, RENAME }

    /** Set before the menu opens (Fishopedia, hold-to-equip with full cards): that species' row opens. */
    private static ResourceLocation pendingFocus;
    private static ItemStack pendingReplace = ItemStack.EMPTY;
    private static View pendingView;

    public static void focusOnNextOpen(ItemStack species, boolean replace) {
        pendingFocus = species.isEmpty() ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(species.getItem());
        pendingReplace = replace ? species.copyWithCount(1) : ItemStack.EMPTY;
    }

    public static void openViewOnNextOpen(View view) { pendingView = view; }

    /** Opens the screen (the server opens the Gelatin menu); {@code species} opens its row, {@code replace} picks it. */
    public static void open(ItemStack species, boolean replace) {
        if (Minecraft.getInstance().player == null) return;
        focusOnNextOpen(species, replace);
        FishtasticTransport.sendToServer(new grill24.fishtastic.network.OpenCompanionMenuPacket(species.copyWithCount(1)));
    }

    private View view = View.EQUIPPED;
    private Field field = Field.NONE;
    private String query = "";
    private boolean unlockedOnly;
    private ResourceLocation expanded;
    private final Map<ResourceLocation, Integer> shown = new HashMap<>();
    private float listScroll, gridScroll;
    private ItemStack replaceFish = ItemStack.EMPTY;
    private int renaming = -1;
    private ItemStack renamingStack = ItemStack.EMPTY;
    private String renameText = "";
    private Component feedback = Component.empty();
    private boolean feedbackError;
    private Runnable feedbackLink;
    private Component feedbackLinkText = Component.empty();
    private int feedbackTicks;
    private final long[] cardFlash = new long[CompanionParty.SIZE];
    private final long[] cardPulse = new long[CompanionParty.SIZE];
    private ItemStack flashFish = ItemStack.EMPTY;
    private long flashFishAt;
    private List<CompanionFishCatalog.Species> catalog = List.of();
    private int catalogAge;
    private List<ItemStack> lastParty = List.of();

    // ---- keyboard focus: stops are rebuilt every frame, so focus follows a stable key ----
    private boolean keyboardActive;
    private String focusKey;
    private int focusIndex = -1;
    private boolean narrateFocus;

    private Canvas canvas;
    private int mouseX, mouseY;
    private float shellScale = 1f;
    private float shellX, shellY;

    public CompanionsScreen(GelatinMenu menu, Inventory inv) {
        super(menu, inv, Component.translatable("screen.fishtastic.companion.title"));
    }

    static net.minecraft.network.chat.MutableComponent tr(String key, Object... args) {
        return Component.translatable("screen.fishtastic.companion." + key, args);
    }

    // =====================================================================================
    // Lifecycle

    @Override
    protected void buildUI() {
        Minecraft mc = Minecraft.getInstance();
        if (pendingView != null) {
            view = pendingView;
            pendingView = null;
        }
        if (pendingFocus != null) {
            view = View.EQUIPPED;
            expanded = pendingFocus;
            pendingFocus = null;
        }
        if (!pendingReplace.isEmpty()) {
            replaceFish = pendingReplace;
            pendingReplace = ItemStack.EMPTY;
            setFeedback(tr("hint.replace"), false, GOLD);
        }
        CompanionClientState.consumeRecentFeedback();
        // Fresh catch counts, so locked species can say how many catches they still need.
        FishtasticTransport.sendToServer(new RequestFishEncyclopediaPacket(false));
        refreshCatalog();
        if (expanded != null) scrollToSpecies(expanded);
        canvas = new Canvas();
        shellScale = Math.max(0.01f, Math.min(1f, Math.min(width / (float) SHELL_W, height / (float) SHELL_H)));
        canvas.setTargetScale(shellScale, false);
        shellX = (width - SHELL_W * shellScale) / 2f;
        shellY = (height - SHELL_H * shellScale) / 2f;
        canvas.setPosition(new Vector2f(shellX, shellY));
        uiScreen.setAutoCenterRoot(false);
        uiScreen.setScrollEnabled(false);
        uiScreen.setRoot(canvas);
        if (mc.player != null) lastParty = partySnapshot();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xC010141A);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        super.render(graphics, mouseX, mouseY, partialTick);
        if (canvas != null) canvas.tooltip(graphics, mouseX, mouseY);
        if (narrateFocus) {
            narrateFocus = false;
            triggerImmediateNarration(false);
        }
    }

    @Override
    protected void updateNarratedWidget(NarrationElementOutput output) {
        Stop stop = focusedStop();
        if (stop != null) {
            if (canvas.stops.size() > 1) {
                output.add(NarratedElementType.POSITION,
                        Component.translatable("narrator.position.screen", focusIndex + 1, canvas.stops.size()));
            }
            output.add(NarratedElementType.TITLE, Component.literal(stop.speech().get()));
            output.add(NarratedElementType.USAGE, Component.translatable("narration.fishtastic.nav_usage"));
        } else {
            output.add(NarratedElementType.USAGE, Component.translatable("narration.fishtastic.companion.usage"));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (++catalogAge >= 5) refreshCatalog();
        List<ItemStack> party = partySnapshot();
        for (int i = 0; i < party.size(); i++) {
            ItemStack before = i < lastParty.size() ? lastParty.get(i) : ItemStack.EMPTY;
            if (!party.get(i).isEmpty() && !FishtasticItemData.matches(before, party.get(i))) cardPulse[i] = now();
        }
        if (!party.equals(lastParty)) refreshCatalog();
        lastParty = party;
        if (renaming >= 0 && !FishtasticItemData.matches(slotFish(renaming), renamingStack)) closeRename(false);
        var outcome = CompanionClientState.consumeRecentFeedback();
        if (outcome != null) onServerFeedback(outcome);
        if (feedbackTicks > 0 && --feedbackTicks == 0 && replaceFish.isEmpty()) clearFeedback();
    }

    /** Ticks (and shell drawing) live in tick(); GelatinUIScreen is an AbstractContainerScreen. */
    private static long now() { return net.minecraft.Util.getMillis(); }

    private List<ItemStack> partySnapshot() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < CompanionParty.SIZE; i++) out.add(slotFish(i).copy());
        return out;
    }

    private ItemStack slotFish(int slot) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? ItemStack.EMPTY : CompanionStorage.get(mc.player, slot);
    }

    private void refreshCatalog() {
        catalogAge = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        catalog = CompanionFishCatalog.build(mc.player.getInventory(), mc.level.registryAccess());
    }

    // =====================================================================================
    // Feedback

    private void setFeedback(Component message, boolean error, int color) {
        feedback = message;
        feedbackError = error;
        feedbackColor = color;
        feedbackTicks = FEEDBACK_TICKS;
        feedbackLink = null;
        feedbackLinkText = Component.empty();
        // The footer is easy to miss and invisible to a narrator; say refusals and hints aloud too.
        if (!message.getString().isEmpty()) Minecraft.getInstance().getNarrator().sayNow(message);
    }

    private int feedbackColor = HINT;

    private void clearFeedback() {
        feedback = Component.empty();
        feedbackLink = null;
        feedbackLinkText = Component.empty();
        feedbackTicks = 0;
    }

    private void onServerFeedback(CompanionFeedbackPacket.Outcome outcome) {
        switch (outcome) {
            case BONDED, RENAMED, UNEQUIPPED, MOVED -> {
                clearFeedback();
                refreshCatalog();
            }
            case INVENTORY_FULL -> refusal(tr("feedback.inventory_full"), null, null);
            case NOT_CARRIED -> refusal(tr("feedback.not_carried"), null, null);
            case PARTY_FULL -> refusal(tr("feedback.party_full"), null, null);
            case LOCKED -> refusal(tr("feedback.locked"), null, null);
            default -> { }
        }
    }

    private void refusal(Component message, Component linkText, Runnable link) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, 0.6f));
        setFeedback(message, true, ERROR);
        feedbackLink = link;
        feedbackLinkText = linkText == null ? Component.empty() : linkText;
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    // =====================================================================================
    // Actions

    void selectView(View next) {
        if (view == next) return;
        click();
        view = next;
        cancelReplace();
        closeRename(false);
        field = Field.NONE;
    }

    void toggleSpecies(ResourceLocation id) {
        click();
        expanded = id.equals(expanded) ? null : id;
        cancelReplace();
    }

    void equip(CompanionFishCatalog.Species species, CompanionFishCatalog.Individual fish) {
        closeRename(true);
        if (species.unlock().gate() != CompanionFishCatalog.Gate.UNLOCKED) {
            flashFish = fish.fish();
            flashFishAt = now();
            int target = firstEmpty();
            if (target >= 0) cardFlash[target] = now();
            refuseLocked(species);
            return;
        }
        click();
        int empty = firstEmpty();
        if (empty >= 0) {
            FishtasticTransport.sendToServer(new EquipCompanionPacket(fish.fish().copyWithCount(1), empty));
            clearFeedback();
        } else if (FishtasticItemData.isSameItemSameData(replaceFish, fish.fish())) {
            cancelReplace();
        } else {
            replaceFish = fish.fish().copyWithCount(1);
            setFeedback(tr("hint.replace"), false, GOLD);
        }
    }

    private void refuseLocked(CompanionFishCatalog.Species species) {
        Component name = Component.literal(species.name());
        if (species.unlock().gate() == CompanionFishCatalog.Gate.QUEST) {
            refusal(tr("feedback.quest", name), tr("link.quests"),
                    () -> FishtasticTransport.sendToServer(new RequestQuestLogPacket()));
        } else {
            refusal(tr("feedback.catch_more", species.unlock().remaining(), name), tr("link.unlocks"),
                    () -> selectView(View.UNLOCKS));
        }
    }

    void chooseCard(int slot) {
        if (replaceFish.isEmpty()) return;
        click();
        FishtasticTransport.sendToServer(new EquipCompanionPacket(replaceFish.copy(), slot));
        cancelReplace();
    }

    void cancelReplace() {
        if (replaceFish.isEmpty()) return;
        replaceFish = ItemStack.EMPTY;
        clearFeedback();
    }

    void unequip(int slot) {
        click();
        closeRename(false);
        FishtasticTransport.sendToServer(new UnequipCompanionPacket(slot));
    }

    void makeLead(int slot) {
        click();
        closeRename(false);
        FishtasticTransport.sendToServer(new MoveCompanionPacket(slot, CompanionParty.LEAD));
    }

    void startRename(int slot) {
        ItemStack fish = slotFish(slot);
        if (fish.isEmpty()) return;
        click();
        cancelReplace();
        renaming = slot;
        renamingStack = fish.copy();
        Component custom = FishtasticItemData.customName(fish);
        renameText = custom == null ? "" : custom.getString();
        field = Field.RENAME;
    }

    /** {@code save}: Enter or clicking elsewhere saves; Esc and a changed fish don't. */
    void closeRename(boolean save) {
        if (renaming < 0) return;
        if (save) FishtasticTransport.sendToServer(new RenameCompanionPacket(renaming, renameText));
        renaming = -1;
        renamingStack = ItemStack.EMPTY;
        if (field == Field.RENAME) field = Field.NONE;
    }

    void showMore(ResourceLocation id) {
        click();
        shown.merge(id, MORE_STEP, Integer::sum);
    }

    void openInFishopedia(ResourceLocation id) {
        click();
        FishEncyclopediaScreen.selectOnNextOpen(id);
        FishtasticTransport.sendToServer(new RequestFishEncyclopediaPacket(true));
    }

    private int firstEmpty() {
        for (int i = 0; i < CompanionParty.SIZE; i++) if (slotFish(i).isEmpty()) return i;
        return -1;
    }

    private void scrollToSpecies(ResourceLocation id) {
        float y = 0;
        for (var species : visibleSpecies()) {
            if (species.id().equals(id)) {
                listScroll = y;
                return;
            }
            y += SPECIES_H;
        }
    }

    List<CompanionFishCatalog.Species> visibleSpecies() {
        List<CompanionFishCatalog.Species> out = new ArrayList<>();
        for (var species : catalog) {
            if (unlockedOnly && species.unlock().gate() != CompanionFishCatalog.Gate.UNLOCKED) continue;
            if (!CompanionFishCatalog.matches(species, query)) continue;
            out.add(species);
        }
        return out;
    }

    // =====================================================================================
    // Keyboard

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (field == Field.NONE) return super.charTyped(c, modifiers);
        if (!StringUtil.isAllowedChatCharacter(c)) return true;
        if (field == Field.SEARCH && query.length() < 32) {
            query += c;
            listScroll = 0;
        } else if (field == Field.RENAME && renameText.length() < RenameCompanionPacket.MAX_NAME_LENGTH) {
            renameText += c;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (field != Field.NONE && key == GLFW.GLFW_KEY_TAB) {
            // Tab leaves a text field the way a click elsewhere does: a name being typed is saved.
            if (field == Field.RENAME) closeRename(true);
            field = Field.NONE;
            return moveFocus(!shift);
        }
        if (field != Field.NONE) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (field == Field.RENAME) closeRename(false);
                field = Field.NONE;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (field == Field.RENAME) closeRename(true);
                field = Field.NONE;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (field == Field.SEARCH && !query.isEmpty()) query = query.substring(0, query.length() - 1);
                if (field == Field.RENAME && !renameText.isEmpty()) renameText = renameText.substring(0, renameText.length() - 1);
                return true;
            }
            // Swallow everything else so typing never toggles Gelatin's debug keys or closes the screen.
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && !replaceFish.isEmpty()) {
            cancelReplace();
            return true;
        }
        if (FishtasticKeyBinds.bondCompanion != null && FishtasticKeyBinds.bondCompanion.matches(key, scanCode)) {
            onClose();
            return true;
        }
        boolean forward = key == GLFW.GLFW_KEY_TAB && !shift || key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_RIGHT;
        boolean back = key == GLFW.GLFW_KEY_TAB && shift || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_LEFT;
        if (forward || back) return moveFocus(forward);
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            activateFocus();
            return true;
        }
        if (key != GLFW.GLFW_KEY_ESCAPE && !minecraft.options.keyInventory.matches(key, scanCode)) {
            return true; // no Gelatin debug toggles here
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (renaming >= 0) closeRename(true);
        if (CompanionTutorialClientHandler.isActive()) CompanionTutorialClientHandler.advance(true);
        super.onClose();
    }

    // =====================================================================================
    // Keyboard focus and narration
    //
    // The canvas is immediate-mode, so UiKeyboardNav (which walks Gelatin elements) can't see its
    // controls. Instead every frame records a Stop beside each mouse Hit: a stable key, the spoken
    // text and the action. Rows and tiles scrolled out of view are laid out without drawing, so
    // their stops exist too, and focusing one scrolls it into view. Same keys as the Captain's
    // Log: Tab / Shift+Tab / arrows move, Enter / Space activate, a mouse click drops the ring.

    /** Which scrolled viewport a stop lives in; it is revealed there when focused. */
    enum Region { NONE, LIST, GRID }

    /** A keyboard focus stop recorded this frame, in canvas pixels (unclipped, may be off-view). */
    record Stop(String key, int x, int y, int w, int h, Region region, Supplier<String> speech, Runnable action) {}

    private Stop focusedStop() {
        if (!keyboardActive || canvas == null || focusIndex < 0 || focusIndex >= canvas.stops.size()) return null;
        return canvas.stops.get(focusIndex);
    }

    private boolean moveFocus(boolean forward) {
        if (canvas == null || canvas.stops.isEmpty()) return false;
        List<Stop> stops = canvas.stops;
        int n = stops.size(), current = canvas.indexOf(focusKey);
        int next;
        if (!keyboardActive) next = forward ? 0 : n - 1;
        else if (current < 0) next = Math.max(0, Math.min(focusIndex, n - 1));
        else next = Math.floorMod(current + (forward ? 1 : -1), n);
        keyboardActive = true;
        focusIndex = next;
        focusKey = stops.get(next).key();
        reveal(stops.get(next));
        narrateFocus = true;
        return true;
    }

    private void activateFocus() {
        Stop stop = focusedStop();
        if (stop == null || stop.action() == null) return;
        stop.action().run();
        narrateFocus = true;
    }

    /** Scrolls the list or grid so a stop is inside its viewport. */
    private void reveal(Stop stop) {
        int top, bottom;
        if (stop.region() == Region.LIST) { top = LIST_Y; bottom = LIST_BOTTOM; }
        else if (stop.region() == Region.GRID) { top = GRID_Y; bottom = GRID_BOTTOM; }
        else return;
        float delta = stop.y() < top ? stop.y() - top - 2 : stop.y() + stop.h() > bottom ? stop.y() + stop.h() - bottom + 2 : 0;
        if (stop.region() == Region.LIST) listScroll = Math.max(0, listScroll + delta);
        else gridScroll = Math.max(0, gridScroll + delta);
    }

    private void mouseUsed() {
        keyboardActive = false;
        focusKey = null;
        focusIndex = -1;
    }

    // =====================================================================================
    // Self-test hooks (CompanionScenes)

    public void testKey(int key, int modifiers) { keyPressed(key, 0, modifiers); }
    public int stopCount() { return canvas == null ? 0 : canvas.stops.size(); }
    public String focusedSpeech() { Stop s = focusedStop(); return s == null ? null : s.speech().get(); }
    public String focusedKey() { Stop s = focusedStop(); return s == null ? null : s.key(); }
    /** True when the focused stop lies fully inside its viewport (always true outside the list/grid). */
    public boolean focusedInView() {
        Stop s = focusedStop();
        if (s == null) return false;
        if (s.region() == Region.LIST) return s.y() >= LIST_Y && s.y() + s.h() <= LIST_BOTTOM;
        if (s.region() == Region.GRID) return s.y() >= GRID_Y && s.y() + s.h() <= GRID_BOTTOM;
        return true;
    }
    public boolean isExpanded(ResourceLocation id) { return id.equals(expanded); }
    public View currentView() { return view; }
    public void testDropFocus() { mouseUsed(); }

    public void testOpenSpecies(ResourceLocation id) { expanded = id; scrollToSpecies(id); }
    public void testShowView(View v) { view = v; }
    public void testStartRename(int slot, String name) { startRename(slot); renameText = name; }
    public void testEquipFirst(ResourceLocation species) {
        for (var s : catalog) if (s.id().equals(species) && !s.fish().isEmpty()) { equip(s, s.fish().get(0)); return; }
    }
    public void testChooseCard(int slot) { chooseCard(slot); }
    public void testSaveRename() { closeRename(true); field = Field.NONE; }
    public void testMakeLead(int slot) { makeLead(slot); }
    public void testUnequip(int slot) { unequip(slot); }
    public void testSearch(String q) { query = q; listScroll = 0; }
    /**
     * Presses the visible scrollbar's thumb and drags it past the bottom of its track through the
     * screen's real mouse handlers (the vanilla entry points, so Gelatin's dispatch is included).
     * Returns the scroll reached as a fraction of its maximum (1 = the end), or -1 when no
     * scrollbar was drawn last frame.
     */
    public float testDragScrollbarToEnd() {
        Bar b = canvas == null ? null : canvas.bar;
        if (b == null) return -1f;
        float before = b.list() ? listScroll : gridScroll;
        double sx = shellX + (CX + CW - 1) * shellScale;
        double y0 = shellY + (b.thumbY(before) + b.thumbH() / 2f) * shellScale;
        double y1 = shellY + (b.top() + b.trackH() + 20) * shellScale;
        int button = GLFW.GLFW_MOUSE_BUTTON_LEFT;
        mouseMoved(sx, y0);
        mouseClicked(sx, y0, button);
        mouseDragged(sx, (y0 + y1) / 2, button, 0, (y1 - y0) / 2);
        mouseDragged(sx, y1, button, 0, (y1 - y0) / 2);
        mouseReleased(sx, y1, button);
        return (b.list() ? listScroll : gridScroll) / b.max();
    }
    public boolean isReplacing() { return !replaceFish.isEmpty(); }
    public boolean isCardFlashing(int slot) { return now() - cardFlash[slot] < FLASH_MS; }
    public int renamingSlot() { return renaming; }
    public void testSkipTour() { CompanionTutorialClientHandler.advance(true); }

    // =====================================================================================
    // The canvas

    /** A clickable rectangle drawn this frame, in canvas pixels. */
    private record Hit(int x, int y, int w, int h, Runnable action, Supplier<List<Component>> tooltip, ItemStack item) {
        boolean contains(float px, float py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }

    /**
     * A scrollbar drawn this frame, in canvas pixels. The grab zone is the whole gutter right of
     * the rows (rows stop {@code ROW_W = CW - 8} short), not just the 2 px track.
     */
    private record Bar(boolean list, int top, int trackH, int thumbH, float max) {
        static final int GRAB_W = 7;
        boolean contains(float px, float py) { return px >= CX + CW - GRAB_W && px < CX + CW + 2 && py >= top && py < top + trackH; }
        int thumbY(float scroll) { return top + Math.round((trackH - thumbH) * scroll / max); }
        float scrollFor(float thumbTop) { return Math.max(0, Math.min(max, (thumbTop - top) / Math.max(1f, trackH - thumbH) * max)); }
    }

    final class Canvas extends UIElement<Canvas> {
        private final List<Hit> hits = new ArrayList<>();
        /** Keyboard stops recorded this frame, in Tab order (draw order). */
        final List<Stop> stops = new ArrayList<>();
        /** Lay out without drawing: rows and tiles scrolled out of view still record their stops. */
        private boolean ghost;
        private GuiGraphics g;
        private IRenderContext ctx;
        private float mx = -1, my = -1;
        /** The scrollbar on screen this frame (null when the content fits), and the one being dragged. */
        private Bar bar, dragging;
        private float dragGrab;

        Canvas() {
            this.size.set(SHELL_W, SHELL_H);
        }

        @Override protected Canvas self() { return this; }
        @Override protected void onUpdate(float deltaTime) { }

        private float localX(float screenX) { return (screenX - shellX) / shellScale; }
        private float localY(float screenY) { return (screenY - shellY) / shellScale; }

        int indexOf(String key) {
            if (key == null) return -1;
            for (int i = 0; i < stops.size(); i++) if (stops.get(i).key().equals(key)) return i;
            return -1;
        }

        private Hit hitAt(float x, float y) {
            for (int i = hits.size() - 1; i >= 0; i--) if (hits.get(i).contains(x, y)) return hits.get(i);
            return null;
        }

        @Override
        protected boolean onEvent(UIEvent event) {
            float x = localX(event.getMouseX()), y = localY(event.getMouseY());
            if (event.getType() == UIEvent.Type.CLICK) mouseUsed();
            if (event.getType() == UIEvent.Type.CLICK && bar != null && bar.contains(x, y)) {
                // Grab the thumb where it was pressed; a press on the bare track centres the thumb there.
                float scroll = bar.list() ? listScroll : gridScroll;
                int thumbY = bar.thumbY(scroll);
                dragGrab = y >= thumbY && y < thumbY + bar.thumbH() ? y - thumbY : bar.thumbH() / 2f;
                dragging = bar;
                setScroll(dragging, dragging.scrollFor(y - dragGrab));
                uiScreen.startDrag(this, event.getMouseY(), 0f);
                return true;
            }
            if (event.getType() == UIEvent.Type.DRAG) {
                if (dragging != null) setScroll(dragging, dragging.scrollFor(y - dragGrab));
                return true;
            }
            if (event.getType() == UIEvent.Type.DRAG_END) {
                dragging = null;
                return true;
            }
            if (event.getType() == UIEvent.Type.CLICK) {
                Hit hit = hitAt(x, y);
                Field before = field;
                if (hit != null && hit.action() != null) {
                    hit.action().run();
                } else {
                    // A click on nothing: saves an open name, drops search focus, cancels replace.
                    if (renaming >= 0) closeRename(true);
                    cancelReplace();
                }
                if (field == before && (hit == null || hit.action() == null)) field = Field.NONE;
                return true;
            }
            if (event.getType() == UIEvent.Type.SCROLL) {
                float d = -event.getScrollDelta() * 18f;
                if (view == View.EQUIPPED && y >= LIST_Y) listScroll = Math.max(0, listScroll + d);
                else if (view == View.UNLOCKS) gridScroll = Math.max(0, gridScroll + d);
                return true;
            }
            return super.onEvent(event);
        }

        private void setScroll(Bar target, float scroll) {
            if (target.list()) listScroll = scroll;
            else gridScroll = scroll;
        }

        /** Draws a scrollbar and records it so presses in its gutter grab it. */
        private void scrollbar(boolean list, int top, int trackH, int contentH, float max, float scroll) {
            int thumbH = Math.max(8, Math.round(trackH * trackH / (float) contentH));
            bar = new Bar(list, top, trackH, thumbH, max);
            int trackX = CX + CW - 2, thumbY = bar.thumbY(scroll);
            boolean hot = dragging != null && dragging.list() == list || bar.contains(mx, my);
            fill(trackX, top, 2, trackH, TRACK);
            fill(trackX, thumbY, 2, thumbH, hot ? THUMB_HI : THUMB);
            fill(trackX, thumbY, 1, thumbH, THUMB_HI);
        }

        void tooltip(GuiGraphics graphics, int screenX, int screenY) {
            Hit hit = hitAt(localX(screenX), localY(screenY));
            if (hit == null) return;
            if (!hit.item().isEmpty()) {
                List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, hit.item()));
                if (hit.tooltip() != null) lines.addAll(hit.tooltip().get());
                graphics.renderTooltip(font, lines, hit.item().getTooltipImage(), screenX, screenY);
            } else if (hit.tooltip() != null) {
                List<Component> lines = hit.tooltip().get();
                if (!lines.isEmpty()) graphics.renderTooltip(font, lines, Optional.empty(), screenX, screenY);
            }
        }

        // ---- primitives (mirror tools/companion-mockups/render.py) ----

        private void fill(int x, int y, int w, int h, int c) { if (!ghost && w > 0 && h > 0) g.fill(x, y, x + w, y + h, c); }

        private void outline(int x, int y, int w, int h, int c) { if (!ghost) g.renderOutline(x, y, w, h, c); }

        private int text(int x, int y, String s, int c) { if (!ghost) g.drawString(font, s, x, y, c, true); return x + font.width(s); }

        private int text(int x, int y, Component s, int c) { return text(x, y, s.getString(), c); }

        private void textC(int cx, int y, String s, int c) { text(cx - font.width(s) / 2, y, s, c); }

        private int textR(int rx, int y, String s, int c) { return text(rx - font.width(s), y, s, c); }

        private void icon(String[] rows, int x, int y, int c) { icon(rows, x, y, c, 1); }

        private void icon(String[] rows, int x, int y, int c, int px) {
            if (ghost) return;
            for (int j = 0; j < rows.length; j++) for (int i = 0; i < rows[j].length(); i++) {
                if (rows[j].charAt(i) == '#') g.fill(x + i * px, y + j * px, x + (i + 1) * px, y + (j + 1) * px, c);
            }
        }

        private void panel(ResourceLocation tex, int x, int y, int w, int h) {
            if (ghost) return;
            SpriteData sprite = new SpriteData(tex).uv(0, 0, 20, 24).textureSize(20, 24)
                    .renderMode(SpriteRenderMode.SLICE).slice(4, 4, 4, 4);
            ctx.drawSprite(sprite, x, y, w, h);
        }

        private void item(ItemStack stack, int x, int y, int size) {
            if (ghost) return;
            if (size == 16) {
                g.renderItem(stack, x, y);
                return;
            }
            var pose = g.pose();
            pose.pushPose();
            pose.translate(x, y, 0);
            pose.scale(size / 16f, size / 16f, 1f);
            g.renderItem(stack, 0, 0);
            pose.popPose();
        }

        private boolean hovered(int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

        private void hit(int x, int y, int w, int h, Runnable action, Supplier<List<Component>> tooltip) {
            hits.add(new Hit(x, y, w, h, action, tooltip, ItemStack.EMPTY));
        }

        private void itemHit(int x, int y, int w, int h, Runnable action, ItemStack item, Supplier<List<Component>> extra) {
            hits.add(new Hit(x, y, w, h, action, extra, item));
        }

        private static Supplier<List<Component>> tip(Component... lines) { return () -> List.of(lines); }

        private void stop(String key, int x, int y, int w, int h, Region region, Supplier<String> speech, Runnable action) {
            stops.add(new Stop(key, x, y, w, h, region, speech, action));
        }

        /** Runs a draw call with drawing suppressed when it is out of view, keeping its stops. */
        private void layout(boolean visible, Runnable draw) {
            boolean was = ghost;
            ghost = was || !visible;
            try { draw.run(); } finally { ghost = was; }
        }

        private static String speak(String key, Object... args) {
            return Component.translatable("narration.fishtastic.companion." + key, args).getString();
        }

        private static String selected(String label, boolean selected) {
            return selected ? label + ", " + Component.translatable("narration.fishtastic.selected").getString() : label;
        }

        /** Resolves the focused stop after a rebuild and draws the ring around it. */
        private void focusRing() {
            if (!keyboardActive || stops.isEmpty()) return;
            int i = indexOf(focusKey);
            if (i < 0) {
                // The focused control went away (equipped, unequipped, view changed): focus its successor.
                i = Math.max(0, Math.min(focusIndex, stops.size() - 1));
                focusKey = stops.get(i).key();
                narrateFocus = true;
            }
            focusIndex = i;
            Stop f = stops.get(i);
            int top = 0, bottom = SHELL_H;
            if (f.region() == Region.LIST) { top = LIST_Y; bottom = LIST_BOTTOM; }
            else if (f.region() == Region.GRID) { top = GRID_Y - 2; bottom = GRID_BOTTOM; }
            ctx.pushScissor(0, top, SHELL_W, bottom - top);
            g.renderOutline(f.x() - 1, f.y() - 1, f.w() + 2, f.h() + 2, RING);
            ctx.popScissor();
        }

        // ---- frame ----

        @Override
        protected void renderSelf(IRenderContext context) {
            if (!(context instanceof MinecraftRenderContext mcContext)) return;
            ctx = context;
            g = mcContext.getGraphics();
            hits.clear();
            stops.clear();
            bar = null;
            mx = localX(mouseX);
            my = localY(mouseY);
            context.enableBlend();
            shell();
            if (view == View.EQUIPPED) equippedView();
            else unlocksView();
            focusRing();
        }

        private void shell() {
            fill(0, 0, RAIL_W, SHELL_H, RAIL);
            outline(0, 0, RAIL_W, SHELL_H, EDGE);
            fill(PANEL_X, 0, PANEL_W, SHELL_H, SHELL);
            outline(PANEL_X, 0, PANEL_W, SHELL_H, EDGE);
            text(10, 7, tr("rail.title_top"), TITLE);
            text(10, 17, tr("rail.title"), GOLD);
            railEntry(34, View.EQUIPPED, tr("rail.equipped"), HEART, PINK);
            railEntry(58, View.UNLOCKS, tr("rail.unlocks"), STAR, GOLD);
            fill(8, SHELL_H - 46, RAIL_W - 16, 1, EDGE);
            if (FishtasticKeyBinds.bondCompanion != null && !FishtasticKeyBinds.bondCompanion.isUnbound()) {
                text(10, SHELL_H - 38, "[" + FishtasticKeyBinds.bondCompanion.getTranslatedKeyMessage().getString() + "]", MUTED);
                text(10, SHELL_H - 28, tr("rail.opens"), HINT);
            }
            text(10, SHELL_H - 14, tr("rail.close"), HINT);
        }

        private void railEntry(int y, View target, Component label, String[] glyph, int glyphColor) {
            boolean active = view == target, hot = hovered(4, y, RAIL_W - 8, 20);
            if (active || hot) fill(4, y, RAIL_W - 8, 20, active ? (hot ? RAIL_SEL_HOT : RAIL_SEL) : HOVER);
            if (active) fill(4, y, 2, 20, GOLD);
            icon(glyph, 10, y + 7, active || glyph == HEART ? glyphColor : MUTED);
            text(20, y + 6, label, active ? WHITE : MUTED);
            hit(4, y, RAIL_W - 8, 20, () -> selectView(target), null);
            stop("rail:" + target, 4, y, RAIL_W - 8, 20, Region.NONE, () -> selected(label.getString(), view == target), () -> selectView(target));
        }

        private void countBox(Component label) {
            int w = Math.max(87, font.width(label) + 23), x = PANEL_X + PANEL_W - INSET - w;
            fill(x, 6, w, 18, TOKEN_BOX);
            icon(HEART, x + 6, 12, PINK);
            text(x + 17, 11, label, GOLD);
        }

        // ---- equipped view ----

        private void equippedView() {
            text(CX, 9, tr("your_companions"), WHITE);
            int equipped = 0;
            for (int i = 0; i < CompanionParty.SIZE; i++) if (!slotFish(i).isEmpty()) equipped++;
            countBox(tr("count.equipped", equipped, CompanionParty.SIZE));
            for (int i = 0; i < CompanionParty.SIZE; i++) card(i);
            formation();
            boolean anyUnlocked = !grill24.fishtastic.client.QuestClientCache.getUnlockedCompanions().isEmpty();
            if (!anyUnlocked && equipped == 0) {
                noneUnlocked();
                return;
            }
            toolbar();
            list();
            footer();
            tourCallout();
        }

        /**
         * The companion lesson's one step (equip a fish), as a house-style callout in the list's
         * corner: gold edge, a pointer at the list, and Skip tour. Replaces the old navy side panel.
         */
        private void tourCallout() {
            if (!CompanionTutorialClientHandler.isActive()) return;
            // The lesson only runs with no companion equipped, so it may cover the empty cards 2-3.
            int w = CARD_W * 2 + CARD_GAP, x = cardX(1);
            List<String> body = wrap(CompanionTutorialClientHandler.body().getString(), w - 12);
            int h = Math.min(CARD_H, 6 + 10 + 4 + body.size() * 10 + 4 + 14 + 4), y = CARD_Y;
            fill(x, y, w, h, SHELL);
            outline(x, y, w, h, GOLD);
            for (int i = 0; i < 4; i++) fill(x + 14 - i, y + h + i, 1 + 2 * i, 1, GOLD); // pointer, down at the list
            text(x + 6, y + 6, truncate(CompanionTutorialClientHandler.title().getString(), w - 12), WHITE);
            int ty = y + 20;
            for (String line : body) { text(x + 6, ty, line, MUTED); ty += 10; }
            int bw = 60, bx = x + w - bw - 6, by = y + h - 20;
            boolean hot = hovered(bx, by, bw, 14);
            fill(bx, by, bw, 14, hot ? RAIL_SEL_HOT : RAIL_SEL);
            textC(bx + bw / 2, by + 3, tr("tour.skip").getString(), WHITE);
            hit(x, y, w, h, null, null);
            hit(bx, by, bw, 14, () -> { click(); CompanionTutorialClientHandler.advance(true); }, null);
            String tour = CompanionTutorialClientHandler.title().getString() + ". " + CompanionTutorialClientHandler.body().getString();
            stop("tour", x, y, w, h, Region.NONE, () -> tour, null);
            stop("tour:skip", bx, by, bw, 14, Region.NONE, () -> tr("tour.skip").getString(),
                    () -> { click(); CompanionTutorialClientHandler.advance(true); });
        }

        private int cardX(int i) { return CX + i * (CARD_W + CARD_GAP); }

        private void card(int i) {
            int x = cardX(i), y = CARD_Y;
            ItemStack fish = slotFish(i);
            boolean hot = hovered(x, y, CARD_W, CARD_H), replacing = !replaceFish.isEmpty();
            long t = now();
            if (fish.isEmpty()) {
                fill(x, y, CARD_W, CARD_H, EMPTY_CARD);
                for (int d = 0; d < CARD_W; d += 4) { fill(x + d, y, 2, 1, EDGE); fill(x + d, y + CARD_H - 1, 2, 1, EDGE); }
                for (int d = 0; d < CARD_H; d += 4) { fill(x, y + d, 1, 2, EDGE); fill(x + CARD_W - 1, y + d, 1, 2, EDGE); }
                icon(PLUS, x + CARD_W / 2 - 5, y + 16, MUTED, 2);
                textC(x + CARD_W / 2, y + 34, tr("empty_card").getString(), MUTED);
                textC(x + CARD_W / 2, y + 45, tr("empty_card_hint").getString(), HINT);
                if (i == CompanionParty.LEAD) text(x + 5, y + 4, tr("lead"), GOLD);
                if (!replacing) stop("card:" + i, x, y, CARD_W, CARD_H, Region.NONE, () -> speak("card_empty", i + 1), null);
            } else {
                panel(i == CompanionParty.LEAD ? PANEL_GOLD : PANEL, x, y, CARD_W, CARD_H);
                if (hot && !replacing) fill(x + 3, y + 3, CARD_W - 6, CARD_H - 6, 0x18FFFFFF);
                if (i == CompanionParty.LEAD) {
                    icon(CROWN, x + 5, y + 5, GOLD);
                    text(x + 12, y + 4, tr("lead"), GOLD);
                }
                CompanionPortrait.draw(g, fish, x + CARD_W / 2 - 16, y + 7, 32, i, t);
                itemHit(x + 20, y + 4, CARD_W - 40, 34, null, fish,
                        i == CompanionParty.LEAD ? tip(tr("lead_tooltip")) : null);
                String name = fish.getHoverName().getString();
                if (!replacing) {
                    String details = (sizeText(fish) + " " + qualityOf(fish).getTranslatableName().getString()).strip();
                    stop("card:" + i, x, y, CARD_W, CARD_H, Region.NONE,
                            () -> speak(i == CompanionParty.LEAD ? "card_lead" : "card", i + 1, name, details), null);
                }
                if (renaming == i) renameField(x, y);
                else nameLine(i, x, y, fish);
                // Keyboard users get the hover-only buttons on every card, so Tab can reach them.
                if ((hot || keyboardActive) && !replacing && renaming != i) {
                    int bx = x + CARD_W - 13;
                    fill(bx, y + 3, 10, 10, hovered(bx, y + 3, 10, 10) ? HOVER : CHIP);
                    icon(CROSS, bx + 3, y + 6 - 1, WHITE);
                    hit(bx, y + 3, 10, 10, () -> unequip(i), tip(tr("unequip")));
                    stop("card:" + i + ":unequip", bx, y + 3, 10, 10, Region.NONE, () -> speak("unequip", name), () -> unequip(i));
                    if (i != CompanionParty.LEAD) {
                        fill(x + 3, y + 3, 10, 10, hovered(x + 3, y + 3, 10, 10) ? HOVER : CHIP);
                        icon(CROWN, x + 6, y + 6, GOLD);
                        hit(x + 3, y + 3, 10, 10, () -> makeLead(i), tip(tr("make_lead")));
                        stop("card:" + i + ":lead", x + 3, y + 3, 10, 10, Region.NONE, () -> speak("make_lead", name), () -> makeLead(i));
                    }
                }
            }
            float pulse = 1f - (t - cardPulse[i]) / (float) PULSE_MS;
            if (cardPulse[i] != 0 && pulse > 0) outline(x - 1, y - 1, CARD_W + 2, CARD_H + 2, withAlpha(GOLD, pulse));
            if (replacing) {
                fill(x, y, CARD_W, CARD_H, 0x66201A24);
                outline(x - 1, y - 1, CARD_W + 2, CARD_H + 2, GOLD);
                if (hot) {
                    outline(x - 2, y - 2, CARD_W + 4, CARD_H + 4, GOLD);
                    fill(x + 20, y + 22, CARD_W - 40, 14, GOLD);
                    String label = tr("replace").getString();
                    g.drawString(font, label, x + CARD_W / 2 - font.width(label) / 2, y + 25, SHELL, false);
                }
                hit(x, y, CARD_W, CARD_H, () -> chooseCard(i), null);
                String occupant = fish.isEmpty() ? "" : fish.getHoverName().getString();
                stop("card:" + i + ":replace", x, y, CARD_W, CARD_H, Region.NONE,
                        () -> speak("replace", i + 1, occupant), () -> chooseCard(i));
            }
            float flash = flash(cardFlash[i], t);
            if (flash > 0) {
                fill(x, y, CARD_W, CARD_H, withAlpha(RED, flash * 0.45f));
                outline(x - 1, y - 1, CARD_W + 2, CARD_H + 2, withAlpha(RED, flash));
            }
        }

        private void nameLine(int i, int x, int y, ItemStack fish) {
            String name = fish.getHoverName().getString();
            name = truncate(name, CARD_W - 20);
            int nw = font.width(name), nx = x + (CARD_W - nw - 9) / 2;
            text(nx, y + 40, name, WHITE);
            pencil(nx + nw + 3, y + 41);
            hit(nx - 2, y + 39, nw + 13, 11, () -> startRename(i), tip(tr("rename")));
            String full = fish.getHoverName().getString();
            stop("card:" + i + ":name", nx - 2, y + 39, nw + 13, 11, Region.NONE, () -> speak("rename", full), () -> startRename(i));
            FishQuality.Quality quality = qualityOf(fish);
            String size = sizeText(fish), q = quality.getTranslatableName().getString();
            int total = font.width(size + "  " + q), sx = x + (CARD_W - total) / 2;
            int end = text(sx, y + 50, size + "  ", MUTED);
            text(end, y + 50, q, qualityColor(quality));
        }

        private void renameField(int x, int y) {
            int fx = x + 6, fy = y + 39, fw = CARD_W - 12;
            fill(fx, fy, fw, 12, FIELD);
            outline(fx, fy, fw, 12, GOLD);
            String shown = renameText;
            while (font.width(shown) > fw - 8 && !shown.isEmpty()) shown = shown.substring(1);
            int end = text(fx + 3, fy + 2, shown, WHITE);
            if ((now() / 500) % 2 == 0) fill(end + 1, fy + 2, 1, 8, WHITE);
            hit(fx, fy, fw, 12, () -> field = Field.RENAME, tip(tr("save_name")));
            stop("card:" + renaming + ":name", fx, fy, fw, 12, Region.NONE, () -> speak("renaming", renameText),
                    () -> field = Field.RENAME);
        }

        private void pencil(int x, int y) {
            int[][] px = {{0, 5, 0xFF2B2B2B}, {1, 4, 0xFFE3B778}, {2, 3, 0xFFF4C430}, {3, 2, 0xFFF4C430}, {4, 1, 0xFFF4C430},
                    {2, 4, 0xFFC9971C}, {3, 3, 0xFFC9971C}, {4, 2, 0xFFC9971C}, {5, 0, 0xFFEE8899}, {5, 1, 0xFFC96478}};
            for (int[] p : px) fill(x + p[0], y + p[1], 1, 1, p[2]);
        }

        private void formation() {
            CompanionParty party = new CompanionParty(partySnapshot());
            if (party.isEmpty() || minecraft.level == null) return;
            List<String> parts = new ArrayList<>();
            for (CompanionGroups.Group group : CompanionGroups.of(party, minecraft.level.registryAccess())) {
                String species = party.get(group.leader()).getItem().getName(party.get(group.leader())).getString();
                if (group.kind() == CompanionGroups.Kind.SHOAL) {
                    java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
                    for (int slot : group.slots()) names.add(party.get(slot).getItem().getName(party.get(slot)).getString());
                    species = String.join(", ", names);
                }
                String key = "preview." + group.kind().name().toLowerCase(java.util.Locale.ROOT);
                parts.add(tr(key, species, group.count()).getString());
            }
            String line = truncate(String.join(tr("preview.join").getString(), parts), CW - 20);
            icon(HEART, CX, FORMATION_Y + 1, PINK);
            text(CX + 10, FORMATION_Y, line, MUTED);
            textR(CX + CW, FORMATION_Y, "?", HINT);
            hit(CX, FORMATION_Y - 1, CW, 10, null, tip(tr("groups_hint")));
            String groups = String.join(tr("preview.join").getString(), parts);
            stop("formation", CX, FORMATION_Y - 1, CW, 10, Region.NONE,
                    () -> speak("formation", groups, tr("groups_hint").getString()), null);
        }

        private void toolbar() {
            fill(CX, TOOL_Y - 4, CW, 1, EDGE);
            boolean focused = field == Field.SEARCH;
            fill(CX, TOOL_Y, 112, 14, FIELD);
            outline(CX, TOOL_Y, 112, 14, focused ? GOLD : EDGE);
            icon(LENS, CX + 4, TOOL_Y + 4, MUTED);
            if (query.isEmpty() && !focused) {
                g.drawString(font, tr("search").getString(), CX + 13, TOOL_Y + 3, HINT, false);
            } else {
                String shownQuery = query;
                while (font.width(shownQuery) > 94 && !shownQuery.isEmpty()) shownQuery = shownQuery.substring(1);
                int end = text(CX + 13, TOOL_Y + 3, shownQuery, WHITE);
                if (focused && (now() / 500) % 2 == 0) fill(end + 1, TOOL_Y + 3, 1, 8, WHITE);
            }
            hit(CX, TOOL_Y, 112, 14, () -> { field = Field.SEARCH; cancelReplace(); }, null);
            stop("search", CX, TOOL_Y, 112, 14, Region.NONE,
                    () -> query.isEmpty() ? speak("search_empty") : speak("search", query),
                    () -> { field = Field.SEARCH; cancelReplace(); });
            int x = CX + 120;
            x = filterPill(x, tr("filter.all"), !unlockedOnly, () -> { unlockedOnly = false; listScroll = 0; });
            filterPill(x, tr("filter.unlocked"), unlockedOnly, () -> { unlockedOnly = true; listScroll = 0; });
            Component summary;
            if (!query.isBlank()) {
                summary = tr("summary.species", visibleSpecies().size());
            } else {
                int total = catalog.stream().mapToInt(CompanionFishCatalog.Species::total).sum();
                summary = tr("summary.fish", String.format(java.util.Locale.ROOT, "%,d", total));
            }
            textR(CX + CW, TOOL_Y + 3, summary.getString(), HINT);
        }

        private int filterPill(int x, Component label, boolean selected, Runnable action) {
            int w = font.width(label) + 10;
            boolean hot = hovered(x, TOOL_Y, w, 14);
            if (selected || hot) fill(x, TOOL_Y, w, 14, selected ? FILTER_SEL : CHIP);
            if (selected) fill(x, TOOL_Y + 12, w, 2, GOLD);
            text(x + 5, TOOL_Y + 3, label, selected ? GOLD : MUTED);
            hit(x, TOOL_Y, w, 14, () -> { if (!selected) click(); action.run(); }, null);
            stop("filter:" + label.getString(), x, TOOL_Y, w, 14, Region.NONE, () -> selected(label.getString(), selected),
                    () -> { if (!selected) click(); action.run(); });
            return x + w + 4;
        }

        private void list() {
            List<CompanionFishCatalog.Species> species = visibleSpecies();
            int contentH = 0;
            for (var s : species) contentH += rowsHeight(s);
            float max = Math.max(0, contentH - (LIST_BOTTOM - LIST_Y));
            listScroll = Math.min(listScroll, max);
            int top = LIST_Y, bottom = LIST_BOTTOM;
            ctx.pushScissor(CX, top, CW, bottom - top);
            int y = top - Math.round(listScroll);
            if (species.isEmpty()) {
                textC(CX + CW / 2, top + 20, (query.isBlank() ? tr("list.empty") : tr("list.no_match")).getString(), HINT);
            }
            for (var s : species) {
                int h = rowsHeight(s);
                int rowY = y;
                layout(y + h >= top && y < bottom, () -> drawSpecies(s, rowY, top, bottom));
                y += h;
            }
            ctx.popScissor();
            if (max > 0) scrollbar(true, top, bottom - top, contentH, max, listScroll);
        }

        private int rowsHeight(CompanionFishCatalog.Species s) {
            if (!s.id().equals(expanded)) return SPECIES_H;
            int limit = shown.getOrDefault(s.id(), PAGE);
            int rows = Math.min(limit, s.fish().size());
            return SPECIES_H + rows * FISH_H + (s.fish().size() > limit ? MORE_H : 0);
        }

        private boolean visible(int y, int h, int top, int bottom) { return y + h > top && y < bottom; }

        /** Only hits inside the list viewport count; rows scrolled under the toolbar aren't clickable. */
        private void listHit(int x, int y, int w, int h, int top, int bottom, Runnable action, Supplier<List<Component>> tip) {
            int y0 = Math.max(y, top), y1 = Math.min(y + h, bottom);
            if (y1 > y0) hit(x, y0, w, y1 - y0, action, tip);
        }

        private void drawSpecies(CompanionFishCatalog.Species s, int y, int top, int bottom) {
            boolean open = s.id().equals(expanded);
            boolean locked = s.unlock().gate() != CompanionFishCatalog.Gate.UNLOCKED;
            boolean hot = hovered(CX, y, ROW_W, SPECIES_H) && my >= top && my < bottom;
            if (open || hot) fill(CX, y, ROW_W, SPECIES_H, ROW_HOT);
            if (open) icon(CHEV_D, CX + 3, y + 8, WHITE);
            else icon(CHEV_R, CX + 4, y + 7, locked ? MUTED : WHITE);
            ItemStack icon = new ItemStack(s.item());
            item(icon, CX + 13, y + 1, 16);
            if (locked) fill(CX + 13, y + 1, 16, 16, 0x88201A24);
            int end = text(CX + 33, y + 5, truncate(s.name(), 130), locked ? MUTED : WHITE);
            text(end + 5, y + 5, "x" + s.total(), HINT);
            int rx = CX + ROW_W - 6;
            switch (s.unlock().gate()) {
                case UNLOCKED -> {
                    String label = tr("state.unlocked").getString();
                    textR(rx, y + 5, label, READY);
                    icon(HEART, rx - font.width(label) - 10, y + 6, PINK);
                }
                case QUEST -> {
                    String label = tr("state.quest").getString();
                    textR(rx, y + 5, label, MUTED);
                    icon(LOCK, rx - font.width(label) - 9, y + 6, MUTED);
                }
                case CATCHES -> {
                    String label = tr("state.catch_more", s.unlock().remaining()).getString();
                    int lx = rx - font.width(label);
                    text(lx, y + 3, label, PROGRESS);
                    int bw = font.width(label);
                    fill(lx, y + 13, bw, 2, TRACK);
                    fill(lx, y + 13, Math.round(bw * s.unlock().progress()), 2, PROGRESS);
                }
            }
            listHit(CX, y, ROW_W, SPECIES_H, top, bottom, () -> toggleSpecies(s.id()), null);
            String state = switch (s.unlock().gate()) {
                case UNLOCKED -> tr("state.unlocked").getString();
                case QUEST -> tr("state.quest").getString();
                case CATCHES -> tr("state.catch_more", s.unlock().remaining()).getString();
            };
            stop("species:" + s.id(), CX, y, ROW_W, SPECIES_H, Region.LIST,
                    () -> speak("species", s.name(), s.total(), state, speak(open ? "expanded" : "collapsed")),
                    () -> toggleSpecies(s.id()));
            if (!open) return;
            int fy = y + SPECIES_H;
            int limit = shown.getOrDefault(s.id(), PAGE);
            for (int i = 0; i < Math.min(limit, s.fish().size()); i++) {
                int rowY = fy, index = i;
                layout(visible(fy, FISH_H, top, bottom), () -> drawFish(s, s.fish().get(index), index, rowY, top, bottom, locked));
                fy += FISH_H;
            }
            if (s.fish().size() > limit) {
                fill(CX + 7, fy, 1, 12, EDGE);
                int more = s.fish().size() - limit;
                int end2 = text(CX + 26, fy + 2, tr("show_more", Math.min(more, MORE_STEP)).getString(), GOLD);
                text(end2 + 4, fy + 2, tr("show_more_of", more).getString(), HINT);
                listHit(CX + 13, fy, ROW_W - 13, MORE_H, top, bottom, () -> showMore(s.id()), null);
                String moreText = tr("show_more", Math.min(more, MORE_STEP)).getString() + " " + tr("show_more_of", more).getString();
                stop("more:" + s.id(), CX + 13, fy, ROW_W - 13, MORE_H, Region.LIST, () -> moreText, () -> showMore(s.id()));
            }
        }

        private void drawFish(CompanionFishCatalog.Species s, CompanionFishCatalog.Individual f, int index, int y, int top, int bottom, boolean locked) {
            int x = CX + 13;
            boolean equippedNow = isEquipped(f.fish());
            boolean hot = hovered(x, y, CX + ROW_W - x, FISH_H) && my >= top && my < bottom;
            boolean chosen = !replaceFish.isEmpty() && FishtasticItemData.isSameItemSameData(replaceFish, f.fish());
            if (hot) fill(x, y, CX + ROW_W - x, FISH_H, HOVER);
            float flash = FishtasticItemData.isSameItemSameData(flashFish, f.fish()) ? flash(flashFishAt, now()) : 0f;
            if (flash > 0) fill(x, y, CX + ROW_W - x, FISH_H, withAlpha(RED, flash * 0.5f));
            fill(CX + 7, y, 1, FISH_H, EDGE);
            FishQuality.Quality quality = f.quality();
            icon(STAR, x + 4, y + 5, qualityColor(quality));
            text(x + 13, y + 4, quality.getTranslatableName().getString(), qualityColor(quality));
            text(x + 70, y + 4, sizeText(f.fish()), WHITE);
            int sx = x + 110;
            if (f.source() == CompanionFishCatalog.Source.PILE) {
                icon(PILE, sx + 1, y + 6, MUTED);
                text(sx + 8, y + 4, tr("source.pile"), MUTED);
            } else {
                text(sx, y + 4, tr("source.inventory"), MUTED);
            }
            if (f.count() > 1) text(sx + 60, y + 4, "x" + f.count(), HINT);
            int bx = CX + ROW_W - 44;
            int y0 = Math.max(y, top), y1 = Math.min(y + FISH_H, bottom);
            if (y1 > y0) itemHit(x, y0, CX + ROW_W - x - 44, y1 - y0, null, f.fish(), null);
            String source = (f.source() == CompanionFishCatalog.Source.PILE ? tr("source.pile") : tr("source.inventory")).getString();
            String fishText = speak("fish", f.fish().getHoverName().getString(), quality.getTranslatableName().getString(),
                    sizeText(f.fish()), source);
            String fishKey = "fish:" + s.id() + ":" + index;
            if (equippedNow) {
                String label = tr("equipped").getString();
                textR(bx + 40, y + 4, label, MUTED);
                icon(HEART, bx + 40 - font.width(label) - 10, y + 5, PINK);
                stop(fishKey, x, y, CX + ROW_W - x, FISH_H, Region.LIST, () -> speak("fish_equipped", fishText), null);
            } else {
                boolean btnHot = hovered(bx + 4, y + 1, 36, 13);
                fill(bx + 4, y + 1, 36, 13, locked ? BUTTON_DIM : btnHot ? RAIL_SEL_HOT : RAIL_SEL);
                textC(bx + 22, y + 4, tr("equip").getString(), locked ? HINT : WHITE);
                listHit(bx + 4, y + 1, 36, 13, top, bottom, () -> equip(s, f), null);
                stop(fishKey, x, y, CX + ROW_W - x, FISH_H, Region.LIST,
                        () -> speak(locked ? "equip_locked" : "equip", fishText), () -> equip(s, f));
            }
            if (chosen) outline(x, y, CX + ROW_W - x, FISH_H, GOLD);
        }

        private boolean isEquipped(ItemStack fish) {
            for (int i = 0; i < CompanionParty.SIZE; i++) if (FishtasticItemData.isSameItemSameData(slotFish(i), fish)) return true;
            return false;
        }

        private void footer() {
            fill(CX, LIST_BOTTOM + 3, CW, 1, EDGE);
            Component message = feedback.getString().isEmpty()
                    ? (!replaceFish.isEmpty() ? tr("hint.replace") : tr("hint.idle")) : feedback;
            int color = feedback.getString().isEmpty() ? (!replaceFish.isEmpty() ? GOLD : HINT) : feedbackColor;
            int linkW = 0;
            if (!replaceFish.isEmpty()) {
                String cancel = tr("cancel").getString();
                linkW = font.width(cancel);
                textR(CX + CW, LIST_BOTTOM + 7, cancel, HINT);
                hit(CX + CW - linkW, LIST_BOTTOM + 5, linkW, 11, CompanionsScreen.this::cancelReplace, null);
                stop("footer:cancel", CX + CW - linkW, LIST_BOTTOM + 5, linkW, 11, Region.NONE,
                        () -> speak("cancel_replace"), CompanionsScreen.this::cancelReplace);
            } else if (feedbackLink != null) {
                String link = feedbackLinkText.getString();
                linkW = font.width(link);
                boolean hot = hovered(CX + CW - linkW, LIST_BOTTOM + 5, linkW, 11);
                textR(CX + CW, LIST_BOTTOM + 7, link, hot ? GOLD : MUTED);
                if (hot) fill(CX + CW - linkW, LIST_BOTTOM + 16, linkW, 1, GOLD);
                Runnable action = feedbackLink;
                hit(CX + CW - linkW, LIST_BOTTOM + 5, linkW, 11, () -> { click(); action.run(); }, null);
                String said = message.getString();
                stop("footer:link", CX + CW - linkW, LIST_BOTTOM + 5, linkW, 11, Region.NONE,
                        () -> said + " " + link, () -> { click(); action.run(); });
            }
            text(CX, LIST_BOTTOM + 7, truncate(message.getString(), CW - linkW - 8), color);
        }

        private void noneUnlocked() {
            fill(CX, TOOL_Y - 4, CW, 1, EDGE);
            int cx = CX + CW / 2;
            icon(HEART, cx - 14, TOOL_Y + 12, 0xFF5A4650, 4);
            textC(cx, TOOL_Y + 42, tr("none.title").getString(), WHITE);
            List<String> lines = wrap(tr("none.body").getString(), CW - 40);
            int y = TOOL_Y + 54;
            for (String line : lines) { textC(cx, y, line, MUTED); y += 10; }
            int bx = cx - 50, by = y + 4;
            boolean hot = hovered(bx, by, 100, 16);
            fill(bx, by, 100, 16, hot ? RAIL_SEL_HOT : RAIL_SEL);
            textC(cx, by + 4, tr("link.unlocks").getString(), WHITE);
            hit(bx, by, 100, 16, () -> selectView(View.UNLOCKS), null);
            Component closest = closestText();
            String none = tr("none.title").getString() + ". " + tr("none.body").getString()
                    + (closest == null ? "" : " " + closest.getString());
            stop("none", CX, TOOL_Y, CW, by - TOOL_Y, Region.NONE, () -> none, null);
            stop("none:unlocks", bx, by, 100, 16, Region.NONE, () -> tr("link.unlocks").getString(), () -> selectView(View.UNLOCKS));
            if (closest != null) textC(cx, by + 20, closest.getString(), HINT);
        }

        private Component closestText() {
            if (minecraft.level == null) return null;
            CompanionFishCatalog.Species best = null;
            UnlockTile bestTile = null;
            for (UnlockTile tile : unlockTiles()) {
                if (tile.unlock.gate() == CompanionFishCatalog.Gate.CATCHES && tile.discovered && tile.unlock.caught() > 0) {
                    if (bestTile == null || tile.unlock.remaining() < bestTile.unlock.remaining()) bestTile = tile;
                }
            }
            if (bestTile == null) return null;
            return tr("closest", new ItemStack(bestTile.item).getHoverName().getString(), bestTile.unlock.remaining());
        }

        // ---- unlocks view ----

        private record UnlockTile(Item item, ResourceLocation id, boolean discovered, CompanionFishCatalog.Unlock unlock) {}

        private List<UnlockTile> unlockTiles() {
            List<UnlockTile> tiles = new ArrayList<>();
            if (minecraft.level == null) return tiles;
            var access = minecraft.level.registryAccess();
            for (var entry : FishEncyclopediaClientHelper.getAllFishProfilesSorted(access)) {
                ResourceLocation id = entry.getKey().location();
                Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                if (item == null) continue;
                boolean discovered = FishEncyclopediaClientCache.isDiscovered(id);
                if (!discovered && item.builtInRegistryHolder().is(FishtasticItemTags.UNLISTED_FISH)) continue;
                tiles.add(new UnlockTile(item, id, discovered, CompanionFishCatalog.unlockOf(access, id)));
            }
            tiles.sort(java.util.Comparator
                    .<UnlockTile>comparingInt(t -> t.unlock.gate() == CompanionFishCatalog.Gate.UNLOCKED ? 0 : t.discovered ? 1 : 2)
                    .thenComparingInt(t -> t.unlock.gate() == CompanionFishCatalog.Gate.QUEST ? 1 : 0)
                    .thenComparing(t -> t.unlock.gate() == CompanionFishCatalog.Gate.CATCHES ? -t.unlock.progress() : 0f)
                    .thenComparing(t -> t.id.toString()));
            return tiles;
        }

        private void unlocksView() {
            text(CX, 9, tr("unlocks.title"), WHITE);
            List<UnlockTile> tiles = unlockTiles();
            long unlocked = tiles.stream().filter(t -> t.unlock.gate() == CompanionFishCatalog.Gate.UNLOCKED).count();
            countBox(tr("count.unlocked", unlocked, tiles.size()));
            text(CX, 26, tr("unlocks.body"), MUTED);
            text(CX, 36, tr("unlocks.order"), HINT);
            int rows = (tiles.size() + TILE_COLS - 1) / TILE_COLS;
            int viewH = SHELL_H - 8 - GRID_Y;
            float max = Math.max(0, rows * TILE_PITCH - viewH);
            gridScroll = Math.min(gridScroll, max);
            ctx.pushScissor(CX, GRID_Y - 2, CW, viewH + 2);
            for (int i = 0; i < tiles.size(); i++) {
                UnlockTile t = tiles.get(i);
                int x = CX + (i % TILE_COLS) * (TILE + TILE_GAP) + 2;
                int y = GRID_Y + (i / TILE_COLS) * TILE_PITCH - Math.round(gridScroll);
                layout(!(y + TILE_PITCH < GRID_Y || y > GRID_Y + viewH), () -> tile(t, x, y, GRID_Y, GRID_Y + viewH));
            }
            ctx.popScissor();
            if (max > 0) scrollbar(false, GRID_Y, viewH, rows * TILE_PITCH, max, gridScroll);
        }

        private void tile(UnlockTile t, int x, int y, int top, int bottom) {
            boolean unlocked = t.unlock.gate() == CompanionFishCatalog.Gate.UNLOCKED;
            panel(unlocked ? PANEL_GOLD : PANEL, x, y, TILE, TILE);
            ItemStack stack = new ItemStack(t.item);
            if (hovered(x, y, TILE, TILE) && my >= top && my < bottom) fill(x + 3, y + 3, TILE - 6, TILE - 6, 0x18FFFFFF);
            item(stack, x + 1, y + 1, 32);
            // Uncaught fish already draw as silhouettes (the mod's undiscovered item render), so no species leaks.
            if (t.discovered && !unlocked) {
                fill(x + 3, y + 3, TILE - 6, TILE - 6, 0x80201A24);
            }
            if (unlocked) icon(HEART, x + TILE - 9, y + 3, PINK);
            if (t.unlock.gate() == CompanionFishCatalog.Gate.CATCHES && t.discovered) {
                fill(x, y + TILE + 3, TILE, 2, TRACK);
                fill(x, y + TILE + 3, Math.round(TILE * t.unlock.progress()), 2, PROGRESS);
            } else if (t.unlock.gate() == CompanionFishCatalog.Gate.QUEST) {
                icon(LOCK, x + TILE / 2 - 2, y + TILE + 3, MUTED);
            }
            Supplier<List<Component>> tooltip = () -> {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(t.discovered ? stack.getHoverName().getString() : "???"));
                switch (t.unlock.gate()) {
                    case UNLOCKED -> lines.add(tr("state.companion_unlocked").withColor(READY));
                    case QUEST -> lines.add(tr("state.quest").withColor(MUTED));
                    case CATCHES -> lines.add(tr("unlocks.catch_more", t.unlock.remaining()).withColor(PROGRESS));
                }
                if (t.discovered) lines.add(tr("unlocks.open_fishopedia").withColor(HINT));
                return lines;
            };
            int y0 = Math.max(y, top), y1 = Math.min(y + TILE, bottom);
            if (y1 > y0) hit(x, y0, TILE, y1 - y0, t.discovered ? () -> openInFishopedia(t.id) : null, tooltip);
            String tileName = t.discovered ? stack.getHoverName().getString()
                    : Component.translatable("narration.fishtastic.encyclopedia.undiscovered").getString();
            String tileState = switch (t.unlock.gate()) {
                case UNLOCKED -> tr("state.companion_unlocked").getString();
                case QUEST -> tr("state.quest").getString();
                case CATCHES -> tr("unlocks.catch_more", t.unlock.remaining()).getString();
            };
            stop("tile:" + t.id, x, y, TILE, TILE, Region.GRID,
                    () -> speak(t.discovered ? "tile_open" : "tile", tileName, tileState),
                    t.discovered ? () -> openInFishopedia(t.id) : null);
        }

        // ---- helpers ----

        private String truncate(String s, int maxW) {
            if (font.width(s) <= maxW) return s;
            while (!s.isEmpty() && font.width(s + "...") > maxW) s = s.substring(0, s.length() - 1);
            return s + "...";
        }

        private List<String> wrap(String s, int maxW) {
            List<String> lines = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            for (String word : s.split(" ")) {
                String trial = cur.isEmpty() ? word : cur + " " + word;
                if (font.width(trial) <= maxW || cur.isEmpty()) {
                    cur.setLength(0);
                    cur.append(trial);
                } else {
                    lines.add(cur.toString());
                    cur.setLength(0);
                    cur.append(word);
                }
            }
            if (!cur.isEmpty()) lines.add(cur.toString());
            return lines;
        }
    }

    // =====================================================================================
    // Shared helpers

    static FishQuality.Quality qualityOf(ItemStack fish) {
        FishQuality.Quality q = grill24.fishtastic.util.FishQualityHelper.getQuality(fish);
        return q == null ? FishQuality.Quality.COMMON : q;
    }

    /** Vanilla chat colours, with Rare lightened for contrast on mauve (spec §3.1). */
    static int qualityColor(FishQuality.Quality quality) {
        return switch (quality) {
            case COMMON -> 0xFFAAAAAA;
            case UNCOMMON -> 0xFF55FF55;
            case RARE -> 0xFF8888FF;
            case EPIC -> 0xFFFF55FF;
            case LEGENDARY -> 0xFFFFAA00;
        };
    }

    static String sizeText(ItemStack fish) {
        float size = grill24.fishtastic.util.ItemSizeHelper.getSize(fish);
        return size <= 0 ? "" : Math.round(size) + " cm";
    }

    /** 0..1: two red pulses fading over {@link #FLASH_MS} (the old screen's curve). */
    static float flash(long start, long now) {
        if (start == 0) return 0;
        float t = (now - start) / (float) FLASH_MS;
        if (t < 0 || t >= 1) return 0;
        float local = t * 2 - (float) Math.floor(t * 2);
        return (float) Math.sin(Math.PI * local) * (1 - 0.35f * t);
    }

    static int withAlpha(int color, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255);
        return (a << 24) | (color & 0xFFFFFF);
    }
}
