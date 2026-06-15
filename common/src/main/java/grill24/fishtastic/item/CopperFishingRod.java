package grill24.fishtastic.item;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.client.TutorialClientHandler;
import grill24.fishtastic.tutorial.TutorialManager;
import grill24.fishtastic.client.tooltip.RodBaitTooltip;
import grill24.fishtastic.component.RodBaitContents;
import grill24.fishtastic.util.FishingMinigameAnimation;
import grill24.fishtastic.util.IGameRendererExtension;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.function.Consumer;

public class CopperFishingRod extends FishingRodItem {

    public CopperFishingRod(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        super.use(level, player, hand);

        if (level.isClientSide()) {
            Minecraft minecraft = Minecraft.getInstance();
            IGameRendererExtension gameRendererExt = (IGameRendererExtension) minecraft.gameRenderer;
            var activeAnimation = gameRendererExt.fishtastic$getActiveAnimation();
            if (activeAnimation instanceof FishingMinigameAnimation animation) {
                animation.applyPlayerImpulse();
                TutorialClientHandler.onMinigameImpulse();
            }
        }

        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    // ----- Bait inventory interactions -----

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot,
                                             ClickAction clickAction, Player player, SlotAccess carriedItem) {
        // `self` is the rod sitting in a slot; `other` is the item held in the cursor.

        if (!other.isEmpty()) {
            if (!other.is(FishtasticItemTags.FISHING_BAIT)) {
                return false;
            }
            if (!slot.allowModification(player)) return false;

            ItemStack currentBait = getBait(self);
            if (!currentBait.isEmpty() && !ItemStack.isSameItemSameComponents(currentBait, other)) {
                player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1.0F, 1.0F);
                return true;
            }

            int existing = currentBait.isEmpty() ? 0 : currentBait.getCount();
            int maxCount = currentBait.isEmpty() ? other.getMaxStackSize() : currentBait.getMaxStackSize();
            int spaceLeft = maxCount - existing;
            if (spaceLeft <= 0) {
                player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1.0F, 1.0F);
                return true;
            }

            int toInsert = Math.min(other.getCount(), spaceLeft);
            ItemStack newBait = currentBait.isEmpty()
                    ? other.copyWithCount(toInsert)
                    : currentBait.copyWithCount(existing + toInsert);
            setBait(self, newBait);
            other.shrink(toInsert);

            if (player instanceof net.minecraft.server.level.ServerPlayer sp) TutorialManager.onBaitLoaded(sp);
            player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
            broadcastContainerChanges(player);
            return true;
        }

        // Cursor is empty — SECONDARY click extracts bait into cursor.
        if (clickAction == ClickAction.SECONDARY) {
            if (!slot.allowModification(player)) return false;
            ItemStack bait = getBait(self);
            if (!bait.isEmpty()) {
                setBait(self, ItemStack.EMPTY);
                carriedItem.set(bait);
                player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
                broadcastContainerChanges(player);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player) {
        // `self` is the rod held in the cursor; the item in `slot` is what we're clicking onto.
        ItemStack other = slot.getItem();

        if (!other.isEmpty() && other.is(FishtasticItemTags.FISHING_BAIT)) {
            if (!slot.allowModification(player)) return false;

            ItemStack currentBait = getBait(self);
            if (!currentBait.isEmpty() && !ItemStack.isSameItemSameComponents(currentBait, other)) {
                player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1.0F, 1.0F);
                return true;
            }

            int existing = currentBait.isEmpty() ? 0 : currentBait.getCount();
            int maxCount = currentBait.isEmpty() ? other.getMaxStackSize() : currentBait.getMaxStackSize();
            int spaceLeft = maxCount - existing;
            if (spaceLeft <= 0) {
                player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1.0F, 1.0F);
                return true;
            }

            int toInsert = Math.min(other.getCount(), spaceLeft);
            ItemStack newBait = currentBait.isEmpty()
                    ? other.copyWithCount(toInsert)
                    : currentBait.copyWithCount(existing + toInsert);
            setBait(self, newBait);
            other.shrink(toInsert);
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) TutorialManager.onBaitLoaded(sp);

            player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
            broadcastContainerChanges(player);
            return true;
        }

        if (other.isEmpty() && clickAction == ClickAction.SECONDARY) {
            // Deposit all bait from rod into the empty slot.
            ItemStack bait = getBait(self);
            if (!bait.isEmpty()) {
                ItemStack remainder = slot.safeInsert(bait);
                setBait(self, remainder.isEmpty() ? ItemStack.EMPTY : remainder);
                player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
                broadcastContainerChanges(player);
                return true;
            }
        }

        return false;
    }

    // ----- Tooltip -----

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        ItemStack bait = getBait(stack);
        if (!bait.isEmpty()) {
            return Optional.of(new RodBaitTooltip(bait));
        }
        return Optional.empty();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                 Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        if (getBait(stack).isEmpty()) {
            builder.accept(Component.translatable("item.fishtastic.copper_fishing_rod.no_bait")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    // ----- Bait helpers -----

    public static ItemStack getBait(ItemStack rod) {
        return rod.getOrDefault(FishtasticDataComponents.ROD_BAIT_CONTENTS.value(), RodBaitContents.EMPTY).copyStack();
    }

    public static void setBait(ItemStack rod, ItemStack bait) {
        rod.set(FishtasticDataComponents.ROD_BAIT_CONTENTS.value(), new RodBaitContents(bait));
    }

    private static void broadcastContainerChanges(Player player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu != null) {
            menu.slotsChanged(player.getInventory());
        }
    }
}
