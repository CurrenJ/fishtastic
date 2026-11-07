package grill24.fishtastic.mixin;

import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.item.PileOfFishItem;
import net.minecraft.core.BlockPos;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Right-clicking a campfire with a Pile of Fish puts one fish from the pile on the grill, the way
 * a loose fish is placed. Vanilla has no campfire recipe for the pile and would fall through to
 * the pile's bundle use (dropping its contents). This runs on both sides so the client also
 * reports the click as consumed. A full campfire still consumes the click, so a held pile never
 * spills onto it.
 */
@Mixin(CampfireBlock.class)
public class CampfirePileOfFishMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void fishtastic$placeFishFromPile(
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack pile = player.getItemInHand(hand);
        if (!pile.is(FishtasticItems.PILE_OF_FISH.value())
                || !(level.getBlockEntity(pos) instanceof CampfireBlockEntity campfire)) {
            return;
        }
        ItemStack fish = PileOfFishItem.peekCookableFish(pile);
        if (fish.isEmpty()) {
            return;
        }
        // Not CampfireBlockEntity#getCookableRecipe: that also answers empty for a full campfire,
        // which must still consume the click.
        Optional<CampfireCookingRecipe> recipe = level.getRecipeManager()
                .getRecipeFor(RecipeType.CAMPFIRE_COOKING, new SimpleContainer(fish), level);
        if (recipe.isEmpty()) {
            return;
        }

        // placeFood always splits off the stack it is given (fish is already a copy); like
        // vanilla, a creative player keeps the fish.
        if (!level.isClientSide && campfire.placeFood(player, fish, recipe.get().getCookingTime())) {
            if (!player.getAbilities().instabuild) {
                player.setItemInHand(hand, PileOfFishItem.removeOneCookableFish(pile));
            }
            player.awardStat(Stats.INTERACT_WITH_CAMPFIRE);
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        cir.setReturnValue(InteractionResult.CONSUME);
    }
}
