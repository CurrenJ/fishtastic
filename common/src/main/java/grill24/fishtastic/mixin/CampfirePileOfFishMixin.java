package grill24.fishtastic.mixin;

import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.item.PileOfFishItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Right-clicking a campfire with a Pile of Fish puts one fish from the pile on the grill, the way
 * a loose fish is placed. Vanilla only accepts items in the campfire input set, which the pile is
 * not, and would fall through to the pile's bundle use (dropping its contents). This runs on both
 * sides so the client also reports the click as consumed. A full campfire still consumes the click,
 * as vanilla does for a held raw fish.
 */
@Mixin(CampfireBlock.class)
public class CampfirePileOfFishMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void fishtastic$placeFishFromPile(
            ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack pile = player.getItemInHand(hand);
        if (!pile.is(FishtasticItems.PILE_OF_FISH.value())
                || !(level.getBlockEntity(pos) instanceof CampfireBlockEntity campfire)) {
            return;
        }
        ItemStack fish = PileOfFishItem.peekCookableFish(pile);
        if (fish.isEmpty() || !level.recipeAccess().propertySet(RecipePropertySet.CAMPFIRE_INPUT).test(fish)) {
            return;
        }

        if (level instanceof ServerLevel serverLevel && campfire.placeFood(serverLevel, player, fish)) {
            // placeFood consumes from the stack it is given unless the player has infinite materials.
            if (fish.isEmpty()) {
                player.setItemInHand(hand, PileOfFishItem.removeOneCookableFish(pile));
            }
            player.awardStat(Stats.INTERACT_WITH_CAMPFIRE);
            cir.setReturnValue(InteractionResult.SUCCESS_SERVER);
            return;
        }
        cir.setReturnValue(InteractionResult.CONSUME);
    }
}
