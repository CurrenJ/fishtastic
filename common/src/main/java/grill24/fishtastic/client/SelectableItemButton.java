package grill24.fishtastic.client;

import grill24.fishtastic.util.IGuiGraphicsExtension;
import io.github.currenj.gelatinui.gui.DirtyFlag;
import io.github.currenj.gelatinui.gui.IRenderContext;
import io.github.currenj.gelatinui.gui.components.ItemButton;
import io.github.currenj.gelatinui.gui.minecraft.MinecraftRenderContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * An ItemButton that draws a lime stained glass pane behind itself when selected.
 */
public class SelectableItemButton extends ItemButton {
    private static final ItemStack SELECTION_STACK = new ItemStack(Items.LIME_STAINED_GLASS_PANE);

    private boolean selected = false;

    public SelectableItemButton(ItemStack itemStack) {
        super(itemStack);
    }

    public SelectableItemButton setSelected(boolean selected) {
        if (this.selected != selected) {
            this.selected = selected;
            markDirty(DirtyFlag.CONTENT);
        }
        return this;
    }

    @Override
    protected void renderSelf(IRenderContext context) {
        if (selected && context instanceof MinecraftRenderContext mcContext) {
            var graphics = mcContext.getGraphics();
            graphics.pose().pushMatrix();
            ((IGuiGraphicsExtension) graphics).fishtastic$renderItem(SELECTION_STACK, 0, 0);
            graphics.pose().popMatrix();
        }
        super.renderSelf(context);
    }

    @Override
    protected String getDefaultDebugName() {
        return "SelectableItemButton(item=" + getItemStack().getItem() + ", selected=" + selected + ")";
    }
}
