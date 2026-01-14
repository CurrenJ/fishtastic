package grill24.fishtastic;

import grill24.fishtastic.architectury.RegistrationApiSided;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;

public class FishtasticItems {
    public static Holder<Item> TEST_ITEM;

    public static void registerItems() {
        TEST_ITEM = RegistrationApiSided.getInstance().registerItem("test_item", loc -> new Item(new Item.Properties()));
    }
}
