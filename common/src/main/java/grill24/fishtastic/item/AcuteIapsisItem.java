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
        Entity hookEntity = lootParams.contextMap().getOptional(LootContextParams.THIS_ENTITY);
        Player player = null;
        if (hookEntity instanceof net.minecraft.world.entity.projectile.FishingHook hook && hook.getOwner() instanceof Player p) {
            player = p;
        }
        if (player != null) {
            // 10 extra weight per diamond in the player's inventory
            return player.getInventory().getNonEquipmentItems().stream()
                    .filter(itemStack -> itemStack.is(Items.DIAMOND))
                    .mapToInt(ItemStack::getCount)
                    .sum();
        }

        return 0;
    }
}
