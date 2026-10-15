package grill24.fishtastic.mixin;

import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.FishtasticParticleTypes;
import grill24.fishtastic.item.FishtasticFishingRodItem;
import grill24.fishtastic.server.FishingMinigameManager;
import grill24.fishtastic.tutorial.TutorialManager;
import grill24.fishtastic.util.IFishingHookExtension;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingHook.class)
public class FishingHookMixin implements IFishingHookExtension {
    @Shadow
    @Final
    private int luck;

    @Inject(method = "<init>(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;II)V",
            at = @At("TAIL"))
    private void fishtastic$onCreated(Player player, Level level, int luckBonus, int speedBonus, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            TutorialManager.onHookCast(serverPlayer);
        }
    }

    // On vanilla/Fabric, shouldStopFishing checks is(Items.FISHING_ROD) which excludes modded rods.
    // NeoForge patches this to canPerformAction(), so it already works there.
    @Inject(method = "shouldStopFishing", at = @At("HEAD"), cancellable = true)
    private void fishtastic$keepCopperRodAlive(Player owner, CallbackInfoReturnable<Boolean> cir) {
        if (!owner.canInteractWithLevel()) return;
        ItemStack mainHand = owner.getMainHandItem();
        ItemStack offHand = owner.getOffhandItem();
        if ((mainHand.is(FishtasticItemTags.FISHING_RODS) || offHand.is(FishtasticItemTags.FISHING_RODS))
                && ((FishingHook)(Object)this).distanceToSqr(owner) <= 1024.0) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "retrieve", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getServer()Lnet/minecraft/server/MinecraftServer;"), cancellable = true)
    private void retrieve(ItemStack itemStack, CallbackInfoReturnable<Integer> cir) {
        FishingHook fishingHook = (FishingHook)(Object)this;
        Player player = fishingHook.getPlayerOwner();

        if (player == null) return;

        if(itemStack.is(FishtasticItemTags.FISHING_RODS)) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                grill24.fishtastic.server.FishingMinigameManager manager =
                        grill24.fishtastic.server.FishingMinigameManager.get(serverPlayer.level());
                if (TutorialManager.isTutorialSession(serverPlayer)) {
                    manager.startTutorialSession(serverPlayer);
                } else {
                    manager.startSession(serverPlayer, 1.0f, false);
                }
                cir.setReturnValue(0); // Prevent normal loot retrieval
            }
        }
    }

    @Inject(method = "retrieve", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/FishingHook;discard()V"), cancellable = true)
    private void retrieveDiscardHook(ItemStack itemStack, CallbackInfoReturnable<Integer> cir) {
        FishingHook fishingHook = (FishingHook)(Object)this;
        Player player = fishingHook.getPlayerOwner();

        if(player instanceof ServerPlayer serverPlayer) {
            FishingMinigameManager manager = FishingMinigameManager.get(serverPlayer.level());
            boolean isFishingMinigameActive = manager.isPlayerInActiveSession(serverPlayer.getUUID());

            if (itemStack.is(FishtasticItemTags.FISHING_RODS) && isFishingMinigameActive) {
                cir.setReturnValue(0); // Prevent hook discard when minigame is active
            }
        }
    }

    @Override
    public int getLuck() {
        return this.luck;
    }

    // ----- Lava fishing (obsidian / copper rods) -----

    @Unique
    private int fishtastic$lavaDamageTickCounter = 0;

    @Unique
    private boolean fishtastic$ownerHoldsLavaCapableRod() {
        Player owner = ((FishingHook) (Object) this).getPlayerOwner();
        if (owner == null) return false;
        return owner.getMainHandItem().is(FishtasticItemTags.FISHING_RODS)
                || owner.getOffhandItem().is(FishtasticItemTags.FISHING_RODS);
    }

    @Unique
    private static ItemStack fishtastic$rodStackOf(Player owner) {
        ItemStack mainHand = owner.getMainHandItem();
        if (mainHand.is(FishtasticItemTags.FISHING_RODS)) return mainHand;
        ItemStack offHand = owner.getOffhandItem();
        if (offHand.is(FishtasticItemTags.FISHING_RODS)) return offHand;
        return ItemStack.EMPTY;
    }

    // A fishing bobber was never meant to visually catch fire — this also keeps lava-capable
    // rods' bobbers from igniting/extinguishing every tick they sit in lava.
    public boolean fireImmune() {
        return true;
    }

    // Vanilla hardcodes FluidTags.WATER for the bobber's buoyancy/"in fluid" checks
    // (liquid height calc and the fall-through-fluid gravity gate). When the owner holds a
    // Fishtastic rod, treat lava the same as water so the bobber floats instead of sinking.
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean fishtastic$treatLavaAsFishableFluid(FluidState fluidState, TagKey<Fluid> tag) {
        if (fluidState.is(tag)) return true;
        return tag == FluidTags.WATER && this.fishtastic$ownerHoldsLavaCapableRod() && fluidState.is(FluidTags.LAVA);
    }

    // Vanilla's catchingFish() only spawns the bite/nibble particle cycle when the block under
    // the simulated fish is literally Blocks.WATER; extend that to lava for Fishtastic rods so
    // the bite cycle is visible (and, combined with the fluid redirect above, actually happens).
    @Redirect(method = "catchingFish", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
    private boolean fishtastic$treatLavaAsSplashBlock(BlockState state, Object other) {
        // This call site only ever passes Blocks.WATER; a plain Block comparison replicates
        // vanilla's original check without depending on the generic is(Object) overload.
        if (other instanceof Block block) {
            if (state.is(block)) return true;
            return block == Blocks.WATER && this.fishtastic$ownerHoldsLavaCapableRod() && state.is(Blocks.LAVA);
        }
        return false;
    }

    // Copper rods drain rapidly in lava (the "wrong tool" experience); obsidian rods drain
    // much more slowly (the intended lava-fishing tool). Counter lives on the hook instance,
    // so it naturally resets every cast.
    @Inject(method = "tick", at = @At("TAIL"))
    private void fishtastic$tickLavaDamage(CallbackInfo ci) {
        FishingHook hook = (FishingHook) (Object) this;
        if (hook.level().isClientSide()) return;

        Player owner = hook.getPlayerOwner();
        if (owner == null) return;

        ItemStack rod = fishtastic$rodStackOf(owner);
        if (!(rod.getItem() instanceof FishtasticFishingRodItem fishtasticRod)) return;

        if (!hook.level().getFluidState(hook.blockPosition()).is(FluidTags.LAVA)) {
            this.fishtastic$lavaDamageTickCounter = 0;
            return;
        }

        if (++this.fishtastic$lavaDamageTickCounter < fishtasticRod.getLavaDamageIntervalTicks()) return;
        this.fishtastic$lavaDamageTickCounter = 0;

        if (owner instanceof ServerPlayer serverPlayer) {
            rod.hurtAndBreak(fishtasticRod.getLavaDamagePerTick(), serverPlayer.level(), serverPlayer, item -> {});
        }
    }

    // Reskin the bite-cycle particles (bubble trail, ripple, tease splash) to lava tones when
    // the owner is lava fishing — vanilla hardcodes ParticleTypes.BUBBLE/FISHING/SPLASH here.
    @Redirect(method = "catchingFish", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"))
    private <T extends ParticleOptions> int fishtastic$lavaTintParticles(
            ServerLevel level, T particle, double x, double y, double z, int count, double xDist, double yDist, double zDist, double speed) {
        ParticleOptions substituted = fishtastic$substituteLavaParticle(particle);
        return level.sendParticles(substituted, x, y, z, count, xDist, yDist, zDist, speed);
    }

    @Unique
    private ParticleOptions fishtastic$substituteLavaParticle(ParticleOptions original) {
        if (original != ParticleTypes.BUBBLE && original != ParticleTypes.FISHING && original != ParticleTypes.SPLASH) {
            return original;
        }

        FishingHook hook = (FishingHook) (Object) this;
        if (!this.fishtastic$ownerHoldsLavaCapableRod()) return original;
        if (!hook.level().getFluidState(hook.blockPosition()).is(FluidTags.LAVA)) return original;

        if (original == ParticleTypes.BUBBLE) return FishtasticParticleTypes.LAVA_BUBBLE.value();
        if (original == ParticleTypes.FISHING) return FishtasticParticleTypes.LAVA_WAKE.value();
        return FishtasticParticleTypes.LAVA_SPLASH.value();
    }
}
