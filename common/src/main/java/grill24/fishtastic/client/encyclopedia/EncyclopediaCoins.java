package grill24.fishtastic.client.encyclopedia;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticItems;
import io.github.currenj.gelatinui.gui.UI;
import io.github.currenj.gelatinui.gui.UIElement;
import io.github.currenj.gelatinui.gui.components.ManualContainer;
import io.github.currenj.gelatinui.gui.components.SpriteData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * The coin ladder for a reward amount, drawn in a fixed 16x16 slot so every rung lines up on the
 * track and in the claim button. The three-coin stack is deliberately skipped: at 16px it reads
 * as more gold than the small pile. The rung thresholds are pure ({@link #forAmount}).
 */
public final class EncyclopediaCoins {
    private EncyclopediaCoins() {}

    /** Side of the square slot every icon is centred in. */
    public static final float SLOT_SIZE = 16f;

    private static final Identifier ONE_COIN_TEXTURE = Fishtastic.id("textures/gui/one_coin.png");
    private static final Identifier TWO_COINS_TEXTURE = Fishtastic.id("textures/gui/two_coins.png");

    /** The picture used for an amount. */
    public enum Rung { ONE_COIN, TWO_COINS, SMALL_PILE, MEDIUM_PILE, LARGE_PILE }

    /** 1 coin, 2 coins, 3-7 small pile, 8-19 medium pile, 20 and up large pile. */
    public static Rung forAmount(int amount) {
        if (amount >= 20) return Rung.LARGE_PILE;
        if (amount >= 8) return Rung.MEDIUM_PILE;
        if (amount >= 3) return Rung.SMALL_PILE;
        if (amount == 2) return Rung.TWO_COINS;
        return Rung.ONE_COIN;
    }

    /** The icon for {@code amount}, in a 16px square slot. */
    public static UIElement<?> icon(int amount) {
        ManualContainer slot = UI.manualContainer().setSize(SLOT_SIZE, SLOT_SIZE);
        switch (forAmount(amount)) {
            case ONE_COIN -> addSprite(slot, ONE_COIN_TEXTURE, 8f, 7f);
            case TWO_COINS -> addSprite(slot, TWO_COINS_TEXTURE, 12f, 8f);
            case SMALL_PILE -> slot.addChildAt(UI.itemRenderer(new ItemStack(FishtasticItems.SMALL_PILE_OF_COINS.value())), SLOT_SIZE / 2f, SLOT_SIZE / 2f);
            case MEDIUM_PILE -> slot.addChildAt(UI.itemRenderer(new ItemStack(FishtasticItems.MEDIUM_PILE_OF_COINS.value())), SLOT_SIZE / 2f, SLOT_SIZE / 2f);
            case LARGE_PILE -> slot.addChildAt(UI.itemRenderer(new ItemStack(FishtasticItems.PILE_OF_COINS.value())), SLOT_SIZE / 2f, SLOT_SIZE / 2f);
        }
        return slot;
    }

    private static void addSprite(ManualContainer slot, Identifier texture, float width, float height) {
        slot.addChildAt(UI.spriteRectangle(width, height, texture).texture(new SpriteData(texture)),
                (int) ((SLOT_SIZE - width) / 2f) + width / 2f, (int) ((SLOT_SIZE - height) / 2f) + height / 2f);   // ManualContainer centres children on the given point; keep the top-left on whole pixels
    }
}
