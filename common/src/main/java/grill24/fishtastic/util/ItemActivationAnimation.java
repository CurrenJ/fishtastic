package grill24.fishtastic.util;


import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface ItemActivationAnimation {
    boolean isActive();
    void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphics, float partialTick);
    void tick();
}
