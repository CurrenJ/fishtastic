package grill24.fishtastic.client;

import grill24.fishtastic.network.RequestFishEncyclopediaPacket;
import grill24.fishtastic.network.RequestQuestLogPacket;
import grill24.fishtastic.network.TutorialActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/** Always available from the pause menu, including when custom shortcuts are unbound. */
public final class TutorialHelpScreen extends Screen {
    private final Screen parent;
    private java.util.List<net.minecraft.util.FormattedCharSequence> description;

    public TutorialHelpScreen(Screen parent) {
        super(Component.translatable("tutorial.fishtastic.help.title"));
        this.parent = parent;
    }

    public static void send(TutorialActionPacket.Action action) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) mc.player.connection.send(new ServerboundCustomPayloadPacket(new TutorialActionPacket(action)));
    }

    @Override protected void init() {
        int x = width / 2 - 105;
        int top = Math.max(40, (height - 180) / 2);
        description = font.split(Component.translatable("tutorial.fishtastic.help.body"), Math.min(330, width - 32));
        Button practice = addRenderableWidget(Button.builder(Component.translatable("tutorial.fishtastic.help.fishing"), b -> {
            send(TutorialActionPacket.Action.REPLAY_FISHING);
            minecraft.setScreen(null);
        }).bounds(x, top + 30, 210, 20).build());
        practice.active = !FishingMinigameClientHandler.hasActiveSession();
        if (!practice.active) practice.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("tutorial.fishtastic.finish_catch")));
        addRenderableWidget(Button.builder(Component.translatable("tutorial.fishtastic.help.encyclopedia"), b -> {
            send(TutorialActionPacket.Action.REPLAY_ENCYCLOPEDIA);
            minecraft.player.connection.send(new ServerboundCustomPayloadPacket(new RequestFishEncyclopediaPacket(true)));
        }).bounds(x, top + 54, 210, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("tutorial.fishtastic.help.companions"), b -> {
            send(TutorialActionPacket.Action.REPLAY_COMPANIONS);
            CompanionScreen.open(net.minecraft.world.item.ItemStack.EMPTY);
        }).bounds(x, top + 78, 210, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("tutorial.fishtastic.help.quests"), b ->
                minecraft.player.connection.send(new ServerboundCustomPayloadPacket(new RequestQuestLogPacket())))
                .bounds(x, top + 102, 210, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("tutorial.fishtastic.dismiss_all"), b -> {
            send(TutorialActionPacket.Action.DISMISS_ALL);
            onClose();
        }).bounds(x, top + 126, 210, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(x, top + 150, 210, 20).build());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int top = Math.max(40, (height - 180) / 2);
        graphics.drawCenteredString(font, title, width / 2, top - 25, 0xFFFFFFFF);
        int y = top - 8;
        for (var line : description) {
            graphics.drawString(font, line, (width - font.width(line)) / 2, y, 0xFFCCCCCC);
            y += 11;
        }
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
