package grill24.fishtastic.item;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.tutorial.TutorialManager;
import grill24.fishtastic.client.tooltip.RodGearTooltip;
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
import net.minecraft.world.InteractionResultHolder;
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
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Shared behavior for all Fishtastic fishing rods (bait/hook/charm slots, tooltips).
 * Per-rod tuning (e.g. lava fishing damage) is exposed via abstract methods for subclasses.
 */
public abstract class FishtasticFishingRodItem extends FishingRodItem {

    public FishtasticFishingRodItem(Properties properties) {
        super(properties);
    }

    /** How many ticks the bobber must sit in lava before this rod takes lava-fishing damage. */
    public abstract int getLavaDamageIntervalTicks();

    /** Durability damage dealt to this rod each time the lava-fishing interval elapses. */
    public abstract int getLavaDamagePerTick();

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        super.use(level, player, hand);

        // Minigame impulse input is polled every frame in FishingMinigameAnimation.render()
        // (both the dedicated keybind and vanilla's "use item" mapping), so right-click no
        // longer applies a discrete tap impulse here — that would double up with the
        // continuous hold force and behave differently from the keybind.

        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
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
        if (!current.isEmpty() && !FishtasticItemData.isSameItemSameData(current, other)) {
            // Different item already loaded — reject unless it's a hook/charm (single-item slots)
            if (type == SlotType.HOOK || type == SlotType.CHARM) {
                playInsertFailSound(player);
                return true;
            }
            playInsertFailSound(player);
            return true;
        }

        int maxForSlot = (type == SlotType.HOOK || type == SlotType.CHARM) ? 1 : other.getMaxStackSize();
        int existing = current.isEmpty() ? 0 : current.getCount();
        int spaceLeft = maxForSlot - existing;
        if (spaceLeft <= 0) {
            playInsertFailSound(player);
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

    /** Silent: the bundle insert-fail sound is new in 1.21.2 (26.1.2 plays BUNDLE_INSERT_FAIL). */
    private static void playInsertFailSound(Player player) {
    }

    private boolean handleSlotDrag(ItemStack rod, ItemStack other, Slot slot, Player player, SlotType type) {
        if (!slot.allowModification(player)) return false;

        ItemStack current = getSlotItem(rod, type);
        if (!current.isEmpty() && !FishtasticItemData.isSameItemSameData(current, other)) {
            playInsertFailSound(player);
            return true;
        }

        int maxForSlot = (type == SlotType.HOOK || type == SlotType.CHARM) ? 1 : other.getMaxStackSize();
        int existing = current.isEmpty() ? 0 : current.getCount();
        int spaceLeft = maxForSlot - existing;
        if (spaceLeft <= 0) {
            playInsertFailSound(player);
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
        return Optional.of(new RodGearTooltip(getBait(stack), getHook(stack), getCharm(stack)));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        Consumer<Component> builder = tooltip::add;

        // Bait — empty slot is communicated by the ghost icon in the tooltip image, not text.
        ItemStack bait = getBait(stack);
        if (!bait.isEmpty()) {
            builder.accept(Component.literal(bait.getHoverName().getString())
                    .withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE));
            BaitEffect baitEffect = BaitEffect.fromStack(bait);
            if (baitEffect != null) {
                baitEffect.tooltipLines().forEach(builder);
            }
        }

        // Hook
        ItemStack hook = getHook(stack);
        if (!hook.isEmpty()) {
            builder.accept(Component.literal(hook.getHoverName().getString())
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE));
            HookEffect hookEffect = FishtasticItemData.get(hook, FishtasticDataComponents.HOOK_EFFECT);
            if (hookEffect != null) {
                hookEffect.tooltipLines().forEach(builder);
            }
        }

        // Charm
        ItemStack charm = getCharm(stack);
        if (!charm.isEmpty()) {
            builder.accept(Component.literal(charm.getHoverName().getString())
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.UNDERLINE));
            CharmEffect charmEffect = FishtasticItemData.get(charm, FishtasticDataComponents.CHARM_EFFECT);
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
        return FishtasticItemData.getOrDefault(rod, FishtasticDataComponents.ROD_BAIT_CONTENTS, RodBaitContents.EMPTY).copyStack();
    }

    public static void setBait(ItemStack rod, ItemStack bait) {
        FishtasticItemData.set(rod, FishtasticDataComponents.ROD_BAIT_CONTENTS, new RodBaitContents(bait));
    }

    public static ItemStack getHook(ItemStack rod) {
        return FishtasticItemData.getOrDefault(rod, FishtasticDataComponents.ROD_HOOK_CONTENTS, RodHookContents.EMPTY).copyStack();
    }

    public static void setHook(ItemStack rod, ItemStack hook) {
        FishtasticItemData.set(rod, FishtasticDataComponents.ROD_HOOK_CONTENTS, new RodHookContents(hook));
    }

    public static ItemStack getCharm(ItemStack rod) {
        return FishtasticItemData.getOrDefault(rod, FishtasticDataComponents.ROD_CHARM_CONTENTS, RodCharmContents.EMPTY).copyStack();
    }

    public static void setCharm(ItemStack rod, ItemStack charm) {
        FishtasticItemData.set(rod, FishtasticDataComponents.ROD_CHARM_CONTENTS, new RodCharmContents(charm));
    }

    private static void broadcastContainerChanges(Player player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu != null) {
            menu.slotsChanged(player.getInventory());
        }
    }
}
