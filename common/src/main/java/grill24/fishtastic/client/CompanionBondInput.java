package grill24.fishtastic.client;

import com.mojang.blaze3d.platform.InputConstants;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.companion.CompanionStorage;
import grill24.fishtastic.network.BondCompanionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** A fresh press must start over the same fish and finish without leaving it. */
public final class CompanionBondInput {
    private CompanionBondInput() {}
    private static Object screen;
    private static boolean wasDown, spent;
    private static Slot target;
    private static ItemStack fish = ItemStack.EMPTY;
    private static long started;
    private static final long HOLD_MS = 800;

    public static boolean eligible(ItemStack stack) {
        return stack.is(FishtasticItemTags.FISH)
                && QuestClientCache.isBondUnlocked(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** Poll physical input because vanilla suppresses KeyMapping state while inventory is open. */
    private static boolean down(Minecraft mc) {
        if (FishtasticKeyBinds.bondCompanion == null) return false;
        var key = InputConstants.getKey(FishtasticKeyBinds.bondCompanion.saveString());
        if (key.getValue() < 0) return false;
        return key.getType() == InputConstants.Type.MOUSE
                ? GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS
                : key.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(mc.getWindow(), key.getValue());
    }

    public static void tick(Object currentScreen, Slot hovered, boolean cursorEmpty) {
        Minecraft mc = Minecraft.getInstance();
        if (!(currentScreen instanceof InventoryScreen) || mc.player == null) return;
        boolean pressed = down(mc);
        if (screen != currentScreen) {
            screen = currentScreen;
            wasDown = pressed; // entering with the key held must never trigger a bond
            spent = pressed;
            target = null;
        }
        if (!pressed) { target = null; fish = ItemStack.EMPTY; spent = false; }
        else if (!wasDown && !spent && cursorEmpty && hovered != null && eligible(hovered.getItem())) {
            target = hovered;
            fish = hovered.getItem().copyWithCount(1);
            started = System.currentTimeMillis();
        } else if (target != null && (!cursorEmpty || hovered != target || !ItemStack.isSameItemSameComponents(fish, target.getItem()) || !eligible(target.getItem()))) {
            target = null;
            spent = true;
        }
        if (target != null && System.currentTimeMillis() - started >= HOLD_MS) {
            ItemStack equipped = CompanionStorage.get(mc.player);
            if (!equipped.isEmpty() && equipped.is(fish.getItem())) CompanionScreen.open(fish);
            else mc.player.connection.send(new ServerboundCustomPayloadPacket(new BondCompanionPacket(fish)));
            target = null;
            spent = true;
        }
        wasDown = pressed;
    }

    public static Component hint(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        String key = FishtasticKeyBinds.bondCompanion.getTranslatedKeyMessage().getString();
        if (target != null && ItemStack.isSameItemSameComponents(stack, fish)) {
            int cells = Math.min(6, (int) ((System.currentTimeMillis() - started) * 6 / HOLD_MS));
            return Component.translatable("screen.fishtastic.companion.holding", key, "■".repeat(cells) + "□".repeat(6 - cells));
        }
        boolean equipped = mc.player != null && CompanionStorage.get(mc.player).is(stack.getItem());
        return Component.translatable(equipped ? "screen.fishtastic.companion.hold_manage" : "screen.fishtastic.companion.hold", key);
    }
}
