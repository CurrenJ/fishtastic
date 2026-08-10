package grill24.fishtastic.mixin;

import grill24.fishtastic.tutorial.TutorialManager;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks the crafting-result-taken step of the tutorial (previously a {@code ResultSlot.onTake}
 * mixin) onto the vanilla advancement system instead. A {@code minecraft:recipe_crafted}
 * criterion is granted via {@code RecipeCraftingHolder.awardUsedRecipes}, which fires from both
 * the normal-click and shift-click take paths (unlike {@code ResultSlot.onTake}, which — on this
 * MC version — only fires for the normal click), so this covers every way of taking the crafted
 * rod out of the result slot without needing a mixin per click path.
 */
@Mixin(PlayerAdvancements.class)
public class PlayerAdvancementsMixin {

    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At("RETURN"))
    private void fishtastic$onAdvancementAwarded(
            AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && holder.id().equals(TutorialManager.TUTORIAL_ROD_ADVANCEMENT_ID)) {
            TutorialManager.onItemCrafted(this.player);
        }
    }
}
