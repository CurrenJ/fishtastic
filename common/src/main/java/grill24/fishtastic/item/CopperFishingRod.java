package grill24.fishtastic.item;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.tutorial.TutorialManager;
import grill24.fishtastic.client.tooltip.RodBaitTooltip;
import grill24.fishtastic.component.BaitEffect;
import grill24.fishtastic.component.CharmEffect;
import grill24.fishtastic.component.HookEffect;
import grill24.fishtastic.component.RodBaitContents;
import grill24.fishtastic.component.RodCharmContents;
import grill24.fishtastic.component.RodHookContents;
import net.minecraft.ChatFormatting;
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

        // Minigame impulse input is polled every frame in FishingMinigameAnimation.render()
        // (both the dedicated keybind and vanilla's "use item" mapping), so right-click no
        // longer applies a discrete tap impulse here — that would double up with the
        // continuous hold force and behave differently from the keybind.

        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    // ----- Inventory interactions (bait, hook, charm) -----

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot,
                                             ClickAction clickAction, Player player, SlotAccess carriedItem) {
        // `self` is the rod sitting in a slot; `other` is the item held in the cursor.

        if (!other.isEmpty()) {
            if (other.is(FishtasticItemTags.FISHING_BAIT)) {
                return handleSlotInsert(self, other, slot, player, carriedItem,
                        SlotType.BAIT, clickAction);
            }
            if (other.is(FishtasticItemTags.FISHING_HOOKS)) {
                return handleSlotInsert(self, other, slot, player, carriedItem,
                        SlotType.HOOK, clickAction);
            }
            if (other.is(FishtasticItemTags.FISHING_CHARMS)) {
                return handleSlotInsert(self, other, slot, player, carriedItem,
                        SlotType.CHARM, clickAction);
            }
            return false;
        }

        // Cursor is empty — SECONDARY click extracts from the first non-empty slot.
        if (clickAction == ClickAction.SECONDARY) {
            if (!slot.allowModification(player)) return false;
            // Try bait first, then hook, then charm.
            for (SlotType type : SlotType.values()) {
                ItemStack stored = getSlotItem(self, type);
                if (!stored.isEmpty()) {
                    setSlotItem(self, type, ItemStack.EMPTY);
                    carriedItem.set(stored);
                    player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
                    broadcastContainerChanges(player);
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player) {
        // `self` is the rod held in the cursor; the item in `slot` is what we're clicking onto.
        ItemStack other = slot.getItem();

        if (!other.isEmpty()) {
            if (other.is(FishtasticItemTags.FISHING_BAIT)) {
                return handleSlotDrag(self, other, slot, player, SlotType.BAIT);
            }
            if (other.is(FishtasticItemTags.FISHING_HOOKS)) {
                return handleSlotDrag(self, other, slot, player, SlotType.HOOK);
            }
            if (other.is(FishtasticItemTags.FISHING_CHARMS)) {
                return handleSlotDrag(self, other, slot, player, SlotType.CHARM);
            }
        }

        if (other.isEmpty() && clickAction == ClickAction.SECONDARY) {
            // Deposit from the first non-empty rod slot into the empty inventory slot.
            for (SlotType type : SlotType.values()) {
                ItemStack stored = getSlotItem(self, type);
                if (!stored.isEmpty()) {
                    ItemStack remainder = slot.safeInsert(stored);
                    setSlotItem(self, type, remainder.isEmpty() ? ItemStack.EMPTY : remainder);
                    player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
                    broadcastContainerChanges(player);
                    return true;
                }
            }
        }

        return false;
    }

    private boolean handleSlotInsert(ItemStack rod, ItemStack other, Slot slot, Player player,
                                      SlotAccess carriedItem, SlotType type, ClickAction clickAction) {
        if (!slot.allowModification(player)) return false;

        ItemStack current = getSlotItem(rod, type);
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, other)) {
            // Different item already loaded — reject unless it's a hook/charm (single-item slots)
            if (type == SlotType.HOOK || type == SlotType.CHARM) {
                player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1.0F, 1.0F);
                return true;
            }
            player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1.0F, 1.0F);
            return true;
        }

        int maxForSlot = (type == SlotType.HOOK || type == SlotType.CHARM) ? 1 : other.getMaxStackSize();
        int existing = current.isEmpty() ? 0 : current.getCount();
        int spaceLeft = maxForSlot - existing;
        if (spaceLeft <= 0) {
            player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1.0F, 1.0F);
            return true;
        }

        int toInsert = Math.min(other.getCount(), spaceLeft);
        ItemStack newStack = current.isEmpty()
                ? other.copyWithCount(toInsert)
                : current.copyWithCount(existing + toInsert);
        setSlotItem(rod, type, newStack);
        other.shrink(toInsert);

        if (type == SlotType.BAIT && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            TutorialManager.onBaitLoaded(sp);
        }
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
        broadcastContainerChanges(player);
        return true;
    }

    private boolean handleSlotDrag(ItemStack rod, ItemStack other, Slot slot, Player player, SlotType type) {
        if (!slot.allowModification(player)) return false;

        ItemStack current = getSlotItem(rod, type);
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, other)) {
            player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1.0F, 1.0F);
            return true;
        }

        int maxForSlot = (type == SlotType.HOOK || type == SlotType.CHARM) ? 1 : other.getMaxStackSize();
        int existing = current.isEmpty() ? 0 : current.getCount();
        int spaceLeft = maxForSlot - existing;
        if (spaceLeft <= 0) {
            player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1.0F, 1.0F);
            return true;
        }

        int toInsert = Math.min(other.getCount(), spaceLeft);
        ItemStack newStack = current.isEmpty()
                ? other.copyWithCount(toInsert)
                : current.copyWithCount(existing + toInsert);
        setSlotItem(rod, type, newStack);
        other.shrink(toInsert);

        if (type == SlotType.BAIT && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            TutorialManager.onBaitLoaded(sp);
        }
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
        broadcastContainerChanges(player);
        return true;
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

        // Bait
        ItemStack bait = getBait(stack);
        if (bait.isEmpty()) {
            builder.accept(Component.translatable("item.fishtastic.copper_fishing_rod.no_bait")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            BaitEffect baitEffect = bait.get(FishtasticDataComponents.BAIT_EFFECT.value());
            if (baitEffect != null) {
                baitEffect.tooltipLines().forEach(builder);
            }
        }

        // Hook
        ItemStack hook = getHook(stack);
        if (hook.isEmpty()) {
            builder.accept(Component.translatable("item.fishtastic.copper_fishing_rod.no_hook")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            builder.accept(Component.literal(hook.getHoverName().getString())
                    .withStyle(ChatFormatting.YELLOW));
            HookEffect hookEffect = hook.get(FishtasticDataComponents.HOOK_EFFECT.value());
            if (hookEffect != null) {
                hookEffect.tooltipLines().forEach(builder);
            }
        }

        // Charm
        ItemStack charm = getCharm(stack);
        if (charm.isEmpty()) {
            builder.accept(Component.translatable("item.fishtastic.copper_fishing_rod.no_charm")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            builder.accept(Component.literal(charm.getHoverName().getString())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            CharmEffect charmEffect = charm.get(FishtasticDataComponents.CHARM_EFFECT.value());
            if (charmEffect != null) {
                charmEffect.tooltipLines().forEach(builder);
            }
        }
    }

    // ----- Slot helpers -----

    private enum SlotType { BAIT, HOOK, CHARM }

    private static ItemStack getSlotItem(ItemStack rod, SlotType type) {
        return switch (type) {
            case BAIT -> getBait(rod);
            case HOOK -> getHook(rod);
            case CHARM -> getCharm(rod);
        };
    }

    private static void setSlotItem(ItemStack rod, SlotType type, ItemStack item) {
        switch (type) {
            case BAIT -> setBait(rod, item);
            case HOOK -> setHook(rod, item);
            case CHARM -> setCharm(rod, item);
        }
    }

    public static ItemStack getBait(ItemStack rod) {
        return rod.getOrDefault(FishtasticDataComponents.ROD_BAIT_CONTENTS.value(), RodBaitContents.EMPTY).copyStack();
    }

    public static void setBait(ItemStack rod, ItemStack bait) {
        rod.set(FishtasticDataComponents.ROD_BAIT_CONTENTS.value(), new RodBaitContents(bait));
    }

    public static ItemStack getHook(ItemStack rod) {
        return rod.getOrDefault(FishtasticDataComponents.ROD_HOOK_CONTENTS.value(), RodHookContents.EMPTY).copyStack();
    }

    public static void setHook(ItemStack rod, ItemStack hook) {
        rod.set(FishtasticDataComponents.ROD_HOOK_CONTENTS.value(), new RodHookContents(hook));
    }

    public static ItemStack getCharm(ItemStack rod) {
        return rod.getOrDefault(FishtasticDataComponents.ROD_CHARM_CONTENTS.value(), RodCharmContents.EMPTY).copyStack();
    }

    public static void setCharm(ItemStack rod, ItemStack charm) {
        rod.set(FishtasticDataComponents.ROD_CHARM_CONTENTS.value(), new RodCharmContents(charm));
    }

    private static void broadcastContainerChanges(Player player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu != null) {
            menu.slotsChanged(player.getInventory());
        }
    }
}
