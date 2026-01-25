package grill24.fishtastic.item;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class AcuteIapsisItem extends FishtasticFishItem {
    public AcuteIapsisItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getAdditionalWeight(LootParams lootParams) {
        Entity entityOpt = lootParams.getOptionalParameter(LootContextParams.ATTACKING_ENTITY);
        if (entityOpt instanceof Player player) {
            // 10 extra weight per diamond in the player's inventory
            return player.getInventory().items.stream()
                    .filter(itemStack -> itemStack.is(Items.DIAMOND))
                    .mapToInt(ItemStack::getCount)
                    .sum();
        }

        return 0;
    }
}
