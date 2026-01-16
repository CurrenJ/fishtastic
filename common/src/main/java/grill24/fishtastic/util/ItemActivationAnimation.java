package grill24.fishtastic.util;


import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public interface ItemActivationAnimation {
    boolean isActive();
    void render(Minecraft minecraft, GuiGraphics guiGraphics, float partialTick);
    void tick();
}
