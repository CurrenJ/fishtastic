package grill24.fishtastic.client;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.menu.CompanionMenu;
import grill24.fishtastic.network.BondCompanionPacket;
import grill24.fishtastic.network.OpenCompanionMenuPacket;
import grill24.fishtastic.network.RenameCompanionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Vanilla slots do all item movement; the pile strip selects a real fish via the validated bond path. */
public final class CompanionScreen extends AbstractContainerScreen<CompanionMenu> {
    private static final Identifier TEXTURE = Fishtastic.id("textures/gui/companions.png");
    private EditBox name;
    private Button rename, piles, previous, next;
    private ItemStack lastCompanion = ItemStack.EMPTY;
    private boolean pileView;
    private int page;
    private String feedback = "";

    public CompanionScreen(CompanionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 202);
        inventoryLabelY = 108;
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
        CompanionTutorialClientHandler.onCompanionsTabOpened();
        name = new EditBox(font, leftPos + 8, topPos + 87, 104, 16, text("name"));
        name.setMaxLength(RenameCompanionPacket.MAX_NAME_LENGTH);
        addRenderableWidget(name);
        rename = addRenderableWidget(Button.builder(text("rename"), b -> {
            minecraft.player.connection.send(new ServerboundCustomPayloadPacket(new RenameCompanionPacket(name.getValue())));
            name.setFocused(false);
        }).bounds(leftPos + 116, topPos + 87, 52, 16).build());
        piles = addRenderableWidget(Button.builder(text("piles"), b -> {
            pileView = !pileView;
            page = 0;
            updateControls();
        }).bounds(leftPos + 120, topPos + 72, 48, 12).build());
        previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; updateControls(); })
                .bounds(leftPos + 8, topPos + 34, 22, 16).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; updateControls(); })
                .bounds(leftPos + 146, topPos + 34, 22, 16).build());
        lastCompanion = ItemStack.EMPTY;
        updateName();
        updateControls();
    }

    /** Like the anvil: a focused name box eats keys, so E/Q/1-9 type instead of closing or moving slots. */
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) return super.keyPressed(event);
        if (name.isFocused() && name.canConsumeInput()) {
            if (event.isConfirmation() && rename.active) {
                rename.onPress(event);
                return true;
            }
            name.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    private static Component text(String key) { return Component.translatable("screen.fishtastic.companion." + key); }

    @Override protected void containerTick() {
        super.containerTick();
        updateName();
        var outcome = CompanionClientState.consumeRecentFeedback();
        if (outcome != null) feedback = Component.translatable("screen.fishtastic.encyclopedia.companions.feedback."
                + outcome.name().toLowerCase(java.util.Locale.ROOT)).getString();
        updateControls();
    }

    private void updateName() {
        ItemStack current = menu.getSlot(0).getItem();
        if (!ItemStack.matches(current, lastCompanion)) {
            lastCompanion = current.copy();
            Component custom = current.get(DataComponents.CUSTOM_NAME);
            name.setValue(custom == null ? "" : custom.getString());
            feedback = "";
        }
    }

    private void updateControls() {
        boolean equipped = menu.getSlot(0).hasItem(), cursorEmpty = menu.getCarried().isEmpty();
        name.visible = rename.visible = !pileView;
        name.active = rename.active = equipped && cursorEmpty;
        piles.active = cursorEmpty && (!pileFish().isEmpty() || pileView);
        previous.visible = next.visible = pileView;
        int pages = Math.max(1, (pileFish().size() + 8) / 9);
        page = Math.max(0, Math.min(page, pages - 1));
        previous.active = cursorEmpty && page > 0;
        next.active = cursorEmpty && page < pages - 1;
    }

    private List<CarriedFish.Group> pileFish() {
        List<ItemStack> fish = new ArrayList<>();
        if (minecraft.player == null) return List.of();
        Inventory inventory = minecraft.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(FishtasticItems.PILE_OF_FISH.value())) {
                for (var template : FishtasticItemData.bundleContentsOrEmpty(stack).items()) {
                    ItemStack item = template.create();
                    if (item.is(FishtasticItemTags.FISH)) fish.add(item);
                }
            }
        }
        return CarriedFish.group(fish);
    }

    private boolean unlocked(ItemStack fish) {
        return QuestClientCache.isBondUnlocked(BuiltInRegistries.ITEM.getKey(fish.getItem()));
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        ItemStack equipped = menu.getSlot(0).getItem();
        // Header above the slot, name below, both centred; the 8x7 heart (atlas 180,0) only accompanies a bonded fish.
        Component header = text(equipped.isEmpty() ? "empty" : "your_companion");
        int headerWidth = font.width(header) + (equipped.isEmpty() ? 0 : 12);
        int headerX = leftPos + (imageWidth - headerWidth) / 2;
        if (!equipped.isEmpty()) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, headerX, topPos + 19, 180, 0, 8, 7, 256, 256);
            headerX += 12;
        }
        graphics.text(font, header, headerX, topPos + 19, 0xFF404040, false);
        if (!equipped.isEmpty()) {
            String name = font.plainSubstrByWidth(equipped.getHoverName().getString(), 160);
            graphics.text(font, name, leftPos + (imageWidth - font.width(name)) / 2, topPos + 58, 0xFF404040, false);
        }
        String status = !feedback.isEmpty() ? feedback : pileView ? text("choose_pile").getString() : "";
        graphics.text(font, font.plainSubstrByWidth(status, 108), leftPos + 8, topPos + 74, 0xFF404040, false);
        // Species highlighting is contextual; the rest of the inventory stays fully usable.
        if (menu.highlightedSpecies() != null) {
            for (int i = 1; i < menu.slots.size(); i++) {
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
                graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, topPos + 86, 7, 119, 18, 18, 256, 256);
                int index = page * 9 + col;
                if (index < fish.size()) {
                    var group = fish.get(index);
                    ItemStack stack = group.representative().copyWithCount(group.count());
                    graphics.item(stack, x + 1, topPos + 87);
                    graphics.itemDecorations(font, stack, x + 1, topPos + 87);
                    if (!unlocked(stack)) graphics.fill(x + 1, topPos + 87, x + 17, topPos + 103, 0x88404040);
                }
            }
        }
    }

    @Override protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> tooltip = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        if (stack.is(FishtasticItemTags.FISH)) tooltip.add(text(unlocked(stack) ? "eligible" : "locked"));
        if (stack.is(FishtasticItems.PILE_OF_FISH.value())) tooltip.add(text("pile_hint"));
        return tooltip;
    }

    @Override protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        int index = pileIndex(mouseX, mouseY);
        List<CarriedFish.Group> fish = pileFish();
        if (pileView && index >= 0 && index < fish.size()) {
            ItemStack stack = fish.get(index).representative();
            List<Component> lines = getTooltipFromContainerItem(stack);
            lines.add(text(unlocked(stack) ? "click_pile" : "locked"));
            graphics.setTooltipForNextFrame(font, lines, stack.getTooltipImage(), mouseX, mouseY, stack.get(DataComponents.TOOLTIP_STYLE));
        }
    }

    private int pileIndex(double x, double y) {
        if (!pileView || x < leftPos + 8 || x >= leftPos + 170 || y < topPos + 86 || y >= topPos + 104) return -1;
        return page * 9 + (int) (x - leftPos - 8) / 18;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int index = pileIndex(event.x(), event.y());
        if (index >= 0) {
            List<CarriedFish.Group> fish = pileFish();
            if (event.button() == 0 && menu.getCarried().isEmpty() && index < fish.size() && unlocked(fish.get(index).representative())) {
                minecraft.player.connection.send(new ServerboundCustomPayloadPacket(
                        new BondCompanionPacket(fish.get(index).representative().copyWithCount(1))));
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
