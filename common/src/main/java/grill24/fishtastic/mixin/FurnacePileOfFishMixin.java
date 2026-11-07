package grill24.fishtastic.mixin;

import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.item.PileOfFishItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a furnace or smoker cook a Pile of Fish one fish at a time. The
 * {@code cooked_cod_from_pile_of_fish_*} recipes match the pile item, and vanilla would then
 * consume the whole pile for a single cooked cod. Instead, when a tick cooks (and so consumes)
 * the pile, the input slot gets the pile back minus one fish, so cook time and XP per fish match
 * the loose-fish recipes; a pile that drops to one entry unpacks into that stack. A pile holding
 * no {@code fishtastic:fish} (only trash or vanilla catches) is hidden from the tick, so the
 * furnace treats it like any item it can't smelt.
 *
 * <p>Hooks {@code serverTick} rather than {@code canBurn}/{@code burn}: Forge patches those two
 * into instance methods while vanilla and NeoForge keep them static, and a common mixin can't
 * target both. {@code serverTick} is static on every loader and runs on the level's thread
 * without re-entering itself, so the pile is carried from HEAD to RETURN in thread-locals.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnacePileOfFishMixin {

    @Shadow
    protected NonNullList<ItemStack> items;

    /** A copy of a cookable pile in the input slot at HEAD. */
    @Unique
    private static final ThreadLocal<ItemStack> fishtastic$cookingPile = new ThreadLocal<>();

    /** A fishless pile taken out of the input slot for the duration of the tick. */
    @Unique
    private static final ThreadLocal<ItemStack> fishtastic$hiddenPile = new ThreadLocal<>();

    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void fishtastic$beforeTick(
            ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity entity, CallbackInfo ci) {
        fishtastic$cookingPile.remove();
        fishtastic$hiddenPile.remove();
        NonNullList<ItemStack> items = ((FurnacePileOfFishMixin) (Object) entity).items;
        ItemStack input = items.get(0);
        if (!input.is(FishtasticItems.PILE_OF_FISH.value())) {
            return;
        }
        if (PileOfFishItem.hasCookableFish(input)) {
            fishtastic$cookingPile.set(input.copy());
        } else {
            items.set(0, ItemStack.EMPTY);
            fishtastic$hiddenPile.set(input);
        }
    }

    @Inject(method = "serverTick", at = @At("RETURN"))
    private static void fishtastic$afterTick(
            ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity entity, CallbackInfo ci) {
        ItemStack cooking = fishtastic$cookingPile.get();
        ItemStack hidden = fishtastic$hiddenPile.get();
        if (cooking == null && hidden == null) {
            return;
        }
        fishtastic$cookingPile.remove();
        fishtastic$hiddenPile.remove();
        NonNullList<ItemStack> items = ((FurnacePileOfFishMixin) (Object) entity).items;
        if (hidden != null) {
            items.set(0, hidden);
        } else if (items.get(0).isEmpty()) {
            // The tick cooked the pile and shrank it away: hand back the rest of the pile.
            items.set(0, PileOfFishItem.removeOneCookableFish(cooking));
        }
    }
}
