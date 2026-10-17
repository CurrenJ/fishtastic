package grill24.fishtastic.client;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.companion.CompanionParty;
import grill24.fishtastic.menu.CompanionMenu;
import grill24.fishtastic.network.OpenCompanionMenuPacket;
import grill24.fishtastic.network.RenameCompanionPacket;
import grill24.fishtastic.network.TakePileFishPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The companion party screen (docs/fish-companions-party.md §5.3, §5.5). Vanilla slots do all item
 * movement. Every action names its own slot: the pencil tab under a party slot opens the name row
 * for that slot, and the pile strip puts the chosen fish on the cursor (the server takes it out of
 * the pile). A fish that isn't bonded yet is refused loudly: the slot it was headed for flashes
 * red, a low note sounds and the status line names the species.
 */
public final class CompanionScreen extends AbstractContainerScreen<CompanionMenu> {
    private static final ResourceLocation TEXTURE = Fishtastic.id("textures/gui/companions.png");
    /** The 26x26 slot frame baked into the atlas around the middle slot; drawn again for the others. */
    private static final int FRAME_U = 75, FRAME_V = 29, FRAME_SIZE = 26;
    /** The pencil tab hangs from the bottom of each frame. */
    private static final int TAB_W = 12, TAB_H = 8;
    private static final int PREVIEW_Y = 66, STATUS_Y = 78, ROW_Y = 89;
    private static final long FLASH_MS = 600, FEEDBACK_TICKS = 100;
    private EditBox name;
    private Button save, piles, previous, next;
    /** The party slot whose name row is open, or -1; the row closes if that slot's fish changes. */
    private int editing = -1;
    private ItemStack editingStack = ItemStack.EMPTY;
    private boolean pileView;
    private int page;
    private Component feedback = Component.empty();
    /** A second line for {@link #feedback}, shown only while the row below the status is free. */
    private Component feedbackHint = Component.empty();
    private boolean feedbackError;
    private long feedbackTicks;
    /** When each menu slot / pile strip cell was last refused (ms), for the red flash. */
    private final long[] slotFlash = new long[CompanionMenu.INVENTORY_END];
    private final long[] stripFlash = new long[9];

    public CompanionScreen(CompanionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        // 1.21.1's AbstractContainerScreen takes no size arguments; the fields are set here.
        this.imageWidth = 176;
        this.imageHeight = 202;
        this.inventoryLabelY = 108;
    }

    public static void open(ItemStack species) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.send(new ServerboundCustomPayloadPacket(new OpenCompanionMenuPacket(species.copyWithCount(1))));
        }
    }

    @Override protected void init() {
        super.init();
        CompanionClientState.consumeRecentFeedback();
        // TODO(F15): guided companion tutorial layout (side panel) arrives with the tutorial port.
        // Fresh catch counts, so a refusal can say how many more catches a bond needs.
        minecraft.player.connection.send(new ServerboundCustomPayloadPacket(new grill24.fishtastic.network.RequestFishEncyclopediaPacket(false)));
        name = new EditBox(font, leftPos + 8, topPos + ROW_Y + 1, 140, 16, text("name"));
        name.setMaxLength(RenameCompanionPacket.MAX_NAME_LENGTH);
        addRenderableWidget(name);
        save = addRenderableWidget(Button.builder(Component.literal("✓"), b -> saveName())
                .bounds(leftPos + 152, topPos + ROW_Y + 1, 16, 16)
                .tooltip(Tooltip.create(text("save_name"))).build());
        piles = addRenderableWidget(Button.builder(text("piles"), b -> {
            pileView = !pileView;
            page = 0;
            clearFeedback();
            closeEditor();
        }).bounds(leftPos + 120, topPos + 4, 48, 12).build());
        previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; updateControls(); })
                .bounds(leftPos + 8, topPos + 34, 22, 16).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; updateControls(); })
                .bounds(leftPos + 146, topPos + 34, 22, 16).build());
        editing = -1;
        updateControls();
    }

    private static Component text(String key) { return Component.translatable("screen.fishtastic.companion." + key); }

    // ── Name row ────────────────────────────────────────────────────────────────

    /** Opens the name row bound to party slot {@code slot}; an empty slot has nothing to name. */
    public void openEditor(int slot) {
        ItemStack fish = menu.getSlot(slot).getItem();
        if (fish.isEmpty()) return;
        editing = slot;
        editingStack = fish.copy();
        pileView = false;
        clearFeedback();
        Component custom = FishtasticItemData.customName(fish);
        name.setValue(custom == null ? "" : custom.getString());
        setFocused(name);
        name.setFocused(true);
        updateControls();
    }

    /** Closes the name row without saving. */
    private void closeEditor() {
        editing = -1;
        editingStack = ItemStack.EMPTY;
        name.setFocused(false);
        if (getFocused() == name) setFocused(null);
        updateControls();
    }

    private void saveName() {
        if (editing < 0) return;
        minecraft.player.connection.send(new ServerboundCustomPayloadPacket(new RenameCompanionPacket(editing, name.getValue())));
        closeEditor();
    }

    /** The party slot whose name row is open, or -1 (self-test hook). */
    public int editingSlot() { return editing; }

    /** Like the anvil: a focused name box eats keys, so E/Q/1-9 type instead of closing or moving slots. */
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean escape = keyCode == GLFW.GLFW_KEY_ESCAPE;
        if (escape && editing >= 0) {
            closeEditor();
            return true;
        }
        if (escape) return super.keyPressed(keyCode, scanCode, modifiers);
        if (name.isFocused() && name.canConsumeInput()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                saveName();
                return true;
            }
            name.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override protected void containerTick() {
        super.containerTick();
        if (editing >= 0 && !FishtasticItemData.matches(menu.getSlot(editing).getItem(), editingStack)) closeEditor();
        var outcome = CompanionClientState.consumeRecentFeedback();
        if (outcome != null) {
            setFeedback(Component.translatable("screen.fishtastic.encyclopedia.companions.feedback."
                    + outcome.name().toLowerCase(java.util.Locale.ROOT)), outcome != grill24.fishtastic.network.CompanionFeedbackPacket.Outcome.BONDED
                    && outcome != grill24.fishtastic.network.CompanionFeedbackPacket.Outcome.RENAMED);
        }
        if (feedbackTicks > 0 && --feedbackTicks == 0) clearFeedback();
        updateControls();
    }

    private void setFeedback(Component message, boolean error) {
        feedback = message;
        feedbackHint = Component.empty();
        feedbackError = error;
        feedbackTicks = FEEDBACK_TICKS;
    }

    private void clearFeedback() {
        feedback = feedbackHint = Component.empty();
        feedbackTicks = 0;
    }

    private void updateControls() {
        boolean cursorEmpty = menu.getCarried().isEmpty();
        name.visible = save.visible = editing >= 0;
        piles.active = cursorEmpty && (!pileFish().isEmpty() || pileView);
        previous.visible = next.visible = pileView;
        int pages = Math.max(1, (pileFish().size() + 8) / 9);
        page = Math.max(0, Math.min(page, pages - 1));
        previous.active = cursorEmpty && page > 0;
        next.active = cursorEmpty && page < pages - 1;
    }

    // ── Refusing a fish that isn't bonded ───────────────────────────────────────

    private boolean unlocked(ItemStack fish) {
        return QuestClientCache.isBondUnlocked(BuiltInRegistries.ITEM.getKey(fish.getItem()));
    }

    /**
     * Every input that would put a fish into a party slot passes through here first. The server
     * refuses an unbonded fish anyway; this only makes the refusal visible instead of silent.
     */
    @Override protected void slotClicked(Slot slot, int slotId, int button, ClickType input) {
        if (slot != null) refuseUnbonded(slot, button, input);
        super.slotClicked(slot, slotId, button, input);
    }

    private void refuseUnbonded(Slot slot, int button, ClickType input) {
        ItemStack fish = ItemStack.EMPTY;
        int target = -1, source = -1;
        if (CompanionMenu.isPartySlot(slot.index)) {
            if (input == ClickType.PICKUP) fish = menu.getCarried();
            else if (input == ClickType.SWAP) fish = minecraft.player.getInventory().getItem(button);
            target = slot.index;
        } else if (input == ClickType.QUICK_MOVE && slot.hasItem()) {
            // Shift-click fills the first empty party slot; a full party refuses everything anyway.
            for (int i = 0; i < CompanionMenu.PARTY_SLOTS && target < 0; i++) if (!menu.getSlot(i).hasItem()) target = i;
            fish = slot.getItem();
            source = slot.index;
        }
        if (target < 0 || !fish.is(FishtasticItemTags.FISH) || unlocked(fish)) return;
        long now = net.minecraft.Util.getMillis();
        slotFlash[target] = now;
        if (source >= 0) slotFlash[source] = now;
        refused(fish);
    }

    /** Sounds the refusal and says what the bond still needs: N more catches, or the species' quest. */
    private void refused(ItemStack fish) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, 0.6f));
        ResourceLocation species = BuiltInRegistries.ITEM.getKey(fish.getItem());
        Component speciesName = fish.getItem().getName(fish);
        Component message;
        if (grill24.fishtastic.companion.CompanionBondUnlocks.isUnlisted(species)) {
            message = Component.translatable("screen.fishtastic.companion.not_bonded_quest", speciesName);
        } else {
            // The same sum the encyclopedia's companion section shows (FishEncyclopediaScreen).
            var thresholds = grill24.fishtastic.data.EncyclopediaTiers.thresholdsFor(minecraft.level.registryAccess(), species);
            int required = java.util.Arrays.stream(grill24.fishtastic.data.EncyclopediaRewardSection.values())
                    .mapToInt(section -> section.threshold(thresholds)).max().orElse(0);
            int remaining = required - FishEncyclopediaClientCache.getCatchCount(species);
            message = remaining > 0 ? Component.translatable("screen.fishtastic.companion.not_bonded_catch", remaining, speciesName)
                    : text("not_bonded");
        }
        setFeedback(message, true);
        feedbackHint = text("not_bonded_how");
    }

    /** 0..1: two red pulses fading over {@link #FLASH_MS} after {@code start}. */
    private static float flash(long start, long now) {
        if (start == 0) return 0;
        float t = (now - start) / (float) FLASH_MS;
        if (t < 0 || t >= 1) return 0;
        float local = t * 2 - (float) Math.floor(t * 2);
        return (float) Math.sin(Math.PI * local) * (1 - 0.35f * t);
    }

    /** Whether the menu slot is mid-flash (self-test hook). */
    public boolean isFlashing(int menuSlot) {
        return slotFlash[menuSlot] != 0 && net.minecraft.Util.getMillis() - slotFlash[menuSlot] < FLASH_MS;
    }

    private static int red(float strength) {
        return FastColor.ARGB32.color(Math.round(net.minecraft.util.Mth.clamp(strength, 0f, 1f) * 0xB0), 0xE0, 0x24, 0x24);
    }

    private void redSlot(GuiGraphics graphics, Slot slot, float strength) {
        int x = leftPos + slot.x, y = topPos + slot.y;
        graphics.fill(x, y, x + 16, y + 16, red(strength * 0.8f));
        if (CompanionMenu.isPartySlot(slot.index)) {
            int frameX = x - 5, frameY = y - 5;
            graphics.renderOutline(frameX - 1, frameY - 1, FRAME_SIZE + 2, FRAME_SIZE + 2, red(strength * 1.4f));
            graphics.renderOutline(frameX, frameY, FRAME_SIZE, FRAME_SIZE, red(strength * 1.4f));
        }
    }

    /**
     * Drawn after the slots and their highlight, under the carried fish. 1.21.1 has no carried-item
     * hook, but {@code render} calls {@code renderLabels} between the slot pass and the floating
     * item, with the pose translated to the panel origin; the overlays below use absolute
     * coordinates, so undo that translation around them.
     */
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.pose().pushPose();
        graphics.pose().translate(-leftPos, -topPos, 0);
        // Holding a fish that isn't bonded over a party slot: say no before the click.
        ItemStack carried = menu.getCarried();
        if (hoveredSlot != null && CompanionMenu.isPartySlot(hoveredSlot.index) && carried.is(FishtasticItemTags.FISH) && !unlocked(carried)) {
            redSlot(graphics, hoveredSlot, 0.5f);
        }
        long now = net.minecraft.Util.getMillis();
        for (int i = 0; i < slotFlash.length; i++) {
            float strength = flash(slotFlash[i], now);
            if (strength > 0) redSlot(graphics, menu.getSlot(i), strength);
        }
        if (pileView) {
            for (int col = 0; col < stripFlash.length; col++) {
                float strength = flash(stripFlash[col], now);
                int x = leftPos + 8 + col * 18, y = topPos + ROW_Y + 1;
                if (strength > 0) graphics.fill(x, y, x + 16, y + 16, red(strength));
            }
        }
        graphics.pose().popPose();
    }

    // ── Pile strip ──────────────────────────────────────────────────────────────

    private List<CarriedFish.Group> pileFish() {
        List<ItemStack> fish = new ArrayList<>();
        if (minecraft.player == null) return List.of();
        Inventory inventory = minecraft.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(FishtasticItems.PILE_OF_FISH.value())) {
                for (ItemStack item : FishtasticItemData.bundleContentsOrEmpty(stack).items()) {
                    if (item.is(FishtasticItemTags.FISH)) fish.add(item);
                }
            }
        }
        return CarriedFish.group(fish);
    }

    private int pileIndex(double x, double y) {
        if (!pileView || x < leftPos + 8 || x >= leftPos + 170 || y < topPos + ROW_Y || y >= topPos + ROW_Y + 18) return -1;
        return page * 9 + (int) (x - leftPos - 8) / 18;
    }

    /** Puts the strip's fish at {@code index} on the cursor; an unbonded one is refused. */
    public void clickPile(int index) {
        List<CarriedFish.Group> fish = pileFish();
        if (!menu.getCarried().isEmpty() || index < 0 || index >= fish.size()) return;
        ItemStack chosen = fish.get(index).representative();
        if (!unlocked(chosen)) {
            stripFlash[index % 9] = net.minecraft.Util.getMillis();
            refused(chosen);
            return;
        }
        minecraft.player.connection.send(new ServerboundCustomPayloadPacket(new TakePileFishPacket(chosen.copyWithCount(1))));
    }

    // ── Pencil tabs ─────────────────────────────────────────────────────────────

    private int tabX(int slot) { return leftPos + CompanionMenu.PARTY_SLOT_X[slot] + 2; }

    private int tabY() { return topPos + CompanionMenu.PARTY_SLOT_Y + 21; }

    private int tabAt(double x, double y) {
        for (int i = 0; i < CompanionMenu.PARTY_SLOTS; i++) {
            if (x >= tabX(i) && x < tabX(i) + TAB_W && y >= tabY() && y < tabY() + TAB_H) return i;
        }
        return -1;
    }

    /** A 12x8 tab hanging from the frame with a 6x6 pencil, drawn in code (no Mojang assets). */
    private void renderPencilTab(GuiGraphics graphics, int slot, int mouseX, int mouseY) {
        int x = tabX(slot), y = tabY();
        boolean enabled = menu.getSlot(slot).hasItem(), open = editing == slot, hovered = tabAt(mouseX, mouseY) == slot;
        int body = open ? 0xFFFFFFFF : !enabled ? 0xFF9A9A9A : hovered ? 0xFFE2E2E2 : 0xFFC6C6C6;
        graphics.fill(x, y, x + TAB_W, y + TAB_H, 0xFF373737);
        graphics.fill(x + 1, y, x + TAB_W - 1, y + TAB_H - 1, body);
        int ix = x + 3, iy = y + 1;
        int tip = enabled ? 0xFF2B2B2B : 0xFF6A6A6A, wood = enabled ? 0xFFE3B778 : 0xFF8A8A8A,
                barrel = enabled ? 0xFFF4C430 : 0xFF8A8A8A, shade = enabled ? 0xFFC9971C : 0xFF7A7A7A,
                eraser = enabled ? 0xFFEE8899 : 0xFF8A8A8A, eraserShade = enabled ? 0xFFC96478 : 0xFF7A7A7A;
        int[][] pixels = {{0, 5, tip}, {1, 4, wood}, {2, 3, barrel}, {3, 2, barrel}, {4, 1, barrel},
                {2, 4, shade}, {3, 3, shade}, {4, 2, shade}, {5, 0, eraser}, {5, 1, eraserShade}};
        for (int[] p : pixels) graphics.fill(ix + p[0], iy + p[1], ix + p[0] + 1, iy + p[1] + 1, p[2]);
    }

    // ── Drawing ─────────────────────────────────────────────────────────────────

    /** The party as the menu shows it right now (the mirror, so it follows clicks immediately). */
    private CompanionParty shownParty() {
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < CompanionMenu.PARTY_SLOTS; i++) slots.add(menu.getSlot(i).getItem());
        return new CompanionParty(slots);
    }

    /**
     * What the party makes, e.g. "Bluegill school + Starfish" (party spec §5.3). The grouping rule
     * lives in {@code CompanionGroups}, which arrives with F10; until then the line stays empty.
     */
    private String preview(CompanionParty party) {
        // TODO(F10): describe the groups once CompanionGroups is ported.
        return "";
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        CompanionParty party = shownParty();
        for (int i = 0; i < CompanionMenu.PARTY_SLOTS; i++) {
            int frameX = leftPos + CompanionMenu.PARTY_SLOT_X[i] - 5, frameY = topPos + CompanionMenu.PARTY_SLOT_Y - 5;
            if (frameX - leftPos != FRAME_U) {
                graphics.blit(TEXTURE, frameX, frameY, FRAME_U, FRAME_V, FRAME_SIZE, FRAME_SIZE, 256, 256);
            }
            if (i == editing) graphics.renderOutline(frameX - 1, frameY - 1, FRAME_SIZE + 2, FRAME_SIZE + 2, 0xFFFFFFFF);
            renderPencilTab(graphics, i, mouseX, mouseY);
        }
        // The lead: the 5x4 heart (atlas 190,0) in slot 0's frame corner, drawn 1:1 (half sizes drop rows).
        graphics.blit(TEXTURE, leftPos + CompanionMenu.PARTY_SLOT_X[0] - 4,
                topPos + CompanionMenu.PARTY_SLOT_Y - 4, 190, 0, 5, 4, 256, 256);
        // Header above the slots, party preview below, both centred; the 8x7 heart (atlas 180,0) only accompanies a party.
        Component header = text(party.isEmpty() ? "empty" : "your_party");
        int headerWidth = font.width(header) + (party.isEmpty() ? 0 : 12);
        int headerX = leftPos + (imageWidth - headerWidth) / 2;
        if (!party.isEmpty()) {
            graphics.blit(TEXTURE, headerX, topPos + 19, 180, 0, 8, 7, 256, 256);
            headerX += 12;
        }
        graphics.drawString(font, header, headerX, topPos + 19, 0xFF404040, false);
        String line = font.plainSubstrByWidth(preview(party), 160);
        if (!line.isEmpty()) graphics.drawString(font, line, leftPos + (imageWidth - font.width(line)) / 2, topPos + PREVIEW_Y, 0xFF404040, false);
        // The status line spans the panel; its hint takes the row below while that row is free.
        boolean feedbackShown = !feedback.getString().isEmpty();
        Component status = feedbackShown ? feedback : pileView ? text("choose_pile") : Component.empty();
        int statusColor = feedbackError && feedbackShown ? 0xFFB02020 : 0xFF404040;
        if (editing < 0 && !pileView) {
            // The row below is free: the message wraps onto a second line and the hint follows (3 lines at most).
            List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>(font.split(status, 160));
            if (feedbackShown && !feedbackHint.getString().isEmpty()) lines.addAll(font.split(feedbackHint, 160));
            for (int i = 0; i < Math.min(3, lines.size()); i++) {
                graphics.drawString(font, lines.get(i), leftPos + 8, topPos + STATUS_Y + i * 10, statusColor, false);
            }
        } else {
            graphics.drawString(font, font.plainSubstrByWidth(status.getString(), 160), leftPos + 8, topPos + STATUS_Y, statusColor, false);
        }
        // Species highlighting is contextual; the rest of the inventory stays fully usable.
        if (menu.highlightedSpecies() != null) {
            for (int i = CompanionMenu.INVENTORY_START; i < menu.slots.size(); i++) {
                var slot = menu.getSlot(i);
                if (slot.getItem().is(menu.highlightedSpecies())) {
                    graphics.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0x446ACC81);
                }
            }
        }
        if (pileView) {
            List<CarriedFish.Group> fish = pileFish();
            for (int col = 0; col < 9; col++) {
                int x = leftPos + 7 + col * 18;
                graphics.blit(TEXTURE, x, topPos + ROW_Y, 7, 119, 18, 18, 256, 256);
                int index = page * 9 + col;
                if (index < fish.size()) {
                    var group = fish.get(index);
                    ItemStack stack = group.representative().copyWithCount(group.count());
                    graphics.renderItem(stack, x + 1, topPos + ROW_Y + 1);
                    graphics.renderItemDecorations(font, stack, x + 1, topPos + ROW_Y + 1);
                    if (!unlocked(stack)) graphics.fill(x + 1, topPos + ROW_Y + 1, x + 17, topPos + ROW_Y + 17, 0x88404040);
                }
            }
        }
    }

    @Override protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> tooltip = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        if (hoveredSlot != null && CompanionMenu.isPartySlot(hoveredSlot.index)) {
            if (hoveredSlot.index == CompanionParty.LEAD) tooltip.add(text("lead"));
            return tooltip;
        }
        if (stack.is(FishtasticItemTags.FISH)) tooltip.add(text(unlocked(stack) ? "eligible" : "locked"));
        if (stack.is(FishtasticItems.PILE_OF_FISH.value())) tooltip.add(text("pile_hint"));
        return tooltip;
    }

    @Override protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        int tab = tabAt(mouseX, mouseY);
        if (tab >= 0 && menu.getCarried().isEmpty()) {
            graphics.renderTooltip(font, text(menu.getSlot(tab).hasItem() ? "rename" : "rename_empty"), mouseX, mouseY);
        }
        int index = pileIndex(mouseX, mouseY);
        List<CarriedFish.Group> fish = pileFish();
        if (pileView && index >= 0 && index < fish.size()) {
            ItemStack stack = fish.get(index).representative();
            List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(stack));
            lines.add(text(unlocked(stack) ? "click_pile" : "locked"));
            graphics.renderTooltip(font, lines, stack.getTooltipImage(), mouseX, mouseY);
        }
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int tab = tabAt(mouseX, mouseY);
        if (tab >= 0) {
            if (button == 0 && menu.getSlot(tab).hasItem()) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                if (editing == tab) closeEditor();
                else openEditor(tab);
            }
            return true;
        }
        int index = pileIndex(mouseX, mouseY);
        if (index >= 0) {
            if (button == 0) clickPile(index);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Self-test hook: what the Piles button toggles. */
    public void showPiles(boolean show) {
        pileView = show;
        if (show) closeEditor();
        updateControls();
    }

    /** Self-test hook: the same path a real click on menu slot {@code index} takes. */
    public void clickSlot(int index, int button, ClickType input) {
        slotClicked(menu.getSlot(index), index, button, input);
    }
}
