package grill24.fishtastic;

import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.item.TestItem;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;

public class FishtasticItems {
    public static Holder<Item> TEST_ITEM;
    public static Holder<Item> FISHING_MINIGAME_ROD_BACKGROUND;
    public static Holder<Item> FISHING_MINIGAME_BOBBER;

    public static void registerItems() {
        TEST_ITEM = RegistrationApiSided.getInstance().registerItem("test_item", loc -> new TestItem(new Item.Properties()));
        FISHING_MINIGAME_ROD_BACKGROUND = RegistrationApiSided.getInstance().registerItem("fishing_minigame_rod_background", loc -> new TestItem(new Item.Properties().stacksTo(1)));
        FISHING_MINIGAME_BOBBER = RegistrationApiSided.getInstance().registerItem("fishing_minigame_bobber", loc -> new TestItem(new Item.Properties().stacksTo(1)));
    }
}
