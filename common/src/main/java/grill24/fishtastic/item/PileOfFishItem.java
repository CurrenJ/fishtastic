package grill24.fishtastic.item;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.block.FishTankBlock;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import grill24.fishtastic.FishtasticItems;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class PileOfFishItem extends BundleItem {

    public PileOfFishItem(Properties properties) {
        super(properties);
    }

    /**
     * Returns true if the given stack is allowed inside a Pile of Fish.
     * Only fish-tagged items or items with the ITEM_SIZE component are accepted.
     */
    public static boolean canInsertInPile(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem().canFitInsideContainerItems()
                && (stack.is(FishtasticItemTags.FISH)
                    || stack.has(FishtasticDataComponents.ITEM_SIZE.value()));
    }

    // --- Fish tank interaction ---

    /**
     * Shift-click pulls the topmost fish out of a targeted fish tank into this pile. Vanilla
     * suppresses {@code FishTankBlock#useItemOn} while sneaking with a non-empty hand, so this
     * has to be handled here instead — see {@link FishTankBlock#tryShiftExtractFromTargetedTank}.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        InteractionResult tankResult = FishTankBlock.tryShiftExtractFromTargetedTank(level, player, hand);
        return tankResult != null ? tankResult : super.use(level, player, hand);
    }

    // --- Pile merging helpers ---

    /**
     * Merges items from {@code sourceContents} into {@code destContents}.
     * Returns the leftover {@link BundleContents} for items that didn't fit,
     * or {@code null} if everything was transferred successfully.
     */
    @Nullable
    private static BundleContents mergeContents(BundleContents sourceContents, BundleContents.Mutable destContents) {
        BundleContents.Mutable leftover = new BundleContents.Mutable(BundleContents.EMPTY);
        for (ItemStackTemplate template : sourceContents.items()) {
            ItemStack toInsert = template.create();
            int added = destContents.tryInsert(toInsert);
            // toInsert is shrunk by `added`; whatever remains goes to leftover
            if (!toInsert.isEmpty()) {
                leftover.tryInsert(toInsert);
            }
        }
        BundleContents result = leftover.toImmutable();
        return result.isEmpty() ? null : result;
    }

    // --- Quick-collect (double-click) ---

    /**
     * Returns the single fish/item species a stack represents for quick-collect matching purposes,
     * or {@code null} if the stack isn't eligible (e.g. an empty pile, or a pile mixing species).
     */
    @Nullable
    private static Item getQuickCollectSpecies(ItemStack stack) {
        if (stack.getItem() instanceof PileOfFishItem) {
            BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
            if (contents == null || contents.items().isEmpty()) {
                return null;
            }
            Item species = contents.items().getFirst().item().value();
            for (ItemStackTemplate template : contents.items()) {
                if (template.item().value() != species) {
                    return null;
                }
            }
            return species;
        }
        return canInsertInPile(stack) ? stack.getItem() : null;
    }

    /**
     * Vanilla double-click ("collect all") matches by {@code isSameItemSameComponents}, which never
     * matches two fish of the same species (each has its own random {@code ItemSize}/{@code FishQuality}),
     * and doesn't know how to fold matches into a bundle rather than just growing a count. This
     * reimplements collection for that case: double-clicking a sized fish/item stack or a single-species
     * pile gathers every matching fish/item (and single-species pile) in the menu into a pile on the cursor.
     * Mirrors the slot-scan structure of {@code AbstractContainerMenu#doClick}'s {@code PICKUP_ALL} branch.
     */
    public static boolean tryQuickCollectIntoPile(AbstractContainerMenu menu, Player player, int buttonNum) {
        ItemStack carried = menu.getCarried();
        Item species = getQuickCollectSpecies(carried);
        if (species == null) {
            return false;
        }

        boolean wasPile = carried.getItem() instanceof PileOfFishItem;
        BundleContents.Mutable acc = wasPile
                ? new BundleContents.Mutable(carried.get(DataComponents.BUNDLE_CONTENTS))
                : new BundleContents.Mutable(BundleContents.EMPTY);
        if (!wasPile) {
            acc.tryInsert(carried.copy());
        }

        boolean collectedAny = false;
        int start = buttonNum == 0 ? 0 : menu.slots.size() - 1;
        int step = buttonNum == 0 ? 1 : -1;
        for (int i = start; i >= 0 && i < menu.slots.size(); i += step) {
            Slot slot = menu.slots.get(i);
            if (!slot.hasItem() || !slot.mayPickup(player)) {
                continue;
            }
            ItemStack slotStack = slot.getItem();
            if (getQuickCollectSpecies(slotStack) != species) {
                continue;
            }

            if (slotStack.getItem() instanceof PileOfFishItem) {
                BundleContents slotContents = slotStack.get(DataComponents.BUNDLE_CONTENTS);
                if (slotContents == null) {
                    continue;
                }
                BundleContents leftover = mergeContents(slotContents, acc);
                int leftoverSize = leftover == null ? 0 : leftover.items().size();
                if (leftoverSize == slotContents.items().size()) {
                    continue; // nothing fit
                }

                if (leftover == null) {
                    slot.set(ItemStack.EMPTY);
                } else if (leftoverSize == 1) {
                    slot.set(leftover.items().getFirst().create());
                } else {
                    slotStack.set(DataComponents.BUNDLE_CONTENTS, leftover);
                }
                collectedAny = true;
            } else if (acc.tryTransfer(slot, player) > 0) {
                collectedAny = true;
            }
        }

        if (!collectedAny) {
            return false;
        }

        ItemStack pile = wasPile ? carried : new ItemStack(FishtasticItems.PILE_OF_FISH.value());
        pile.set(DataComponents.BUNDLE_CONTENTS, acc.toImmutable());
        menu.setCarried(pile);
        playInsertSound(player);
        menu.broadcastChanges();
        return true;
    }

    // --- Overrides to gate insertion through canInsertInPile ---

    @Override
    public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player) {
        BundleContents initialContents = self.get(DataComponents.BUNDLE_CONTENTS);
        if (initialContents == null) {
            return false;
        }

        ItemStack other = slot.getItem();
        BundleContents.Mutable contents = new BundleContents.Mutable(initialContents);

        if (clickAction == ClickAction.PRIMARY && !other.isEmpty()) {
            // Pile-on-pile merge: cursor pile (self) dumps into slot pile (other)
            if (other.getItem() instanceof PileOfFishItem) {
                BundleContents otherContents = other.get(DataComponents.BUNDLE_CONTENTS);
                if (otherContents == null) return false;

                BundleContents.Mutable otherMutable = new BundleContents.Mutable(otherContents);
                BundleContents leftover = mergeContents(initialContents, otherMutable);
                other.set(DataComponents.BUNDLE_CONTENTS, otherMutable.toImmutable());

                if (leftover == null) {
                    // Everything merged — destroy cursor pile
                    self.shrink(1);
                } else if (leftover.items().size() == 1) {
                    // Only 1 item left — decompose pile into that item
                    ItemStack singleItem = leftover.items().getFirst().create();
                    self.shrink(1);
                    player.containerMenu.setCarried(singleItem);
                } else {
                    // Some items didn't fit — update cursor pile with leftovers
                    self.set(DataComponents.BUNDLE_CONTENTS, leftover);
                }
                playInsertSound(player);
                broadcastChangesOnContainerMenu(player);
                return true;
            }

            // Gate: only allow fish / sized items
            if (!canInsertInPile(other)) {
                playInsertFailSound(player);
                return true; // consume click
            }
            if (contents.tryTransfer(slot, player) > 0) {
                playInsertSound(player);
            } else {
                playInsertFailSound(player);
            }

            self.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
            broadcastChangesOnContainerMenu(player);
            return true;
        } else if (clickAction == ClickAction.SECONDARY && other.isEmpty()) {
            // Extraction — no restriction needed
            ItemStack removed = contents.removeOne();
            if (removed != null) {
                ItemStack remainder = slot.safeInsert(removed);
                if (remainder.getCount() > 0) {
                    contents.tryInsert(remainder);
                } else {
                    playRemoveOneSound(player);
                }
            }

            BundleContents updatedContents = contents.toImmutable();
            if (updatedContents.isEmpty()) {
                // Pile is now empty — destruct
                self.shrink(1);
            } else if (updatedContents.items().size() == 1) {
                // Only 1 item left — decompose pile into that item
                ItemStack singleItem = updatedContents.items().getFirst().create();
                self.shrink(1);
                player.containerMenu.setCarried(singleItem);
            } else {
                self.set(DataComponents.BUNDLE_CONTENTS, updatedContents);
            }
            broadcastChangesOnContainerMenu(player);
            return true;
        }

        return false;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot,
                                             ClickAction clickAction, Player player, SlotAccess carriedItem) {
        if (clickAction == ClickAction.PRIMARY && other.isEmpty()) {
            toggleSelectedItem(self, -1);
            return false;
        }

        BundleContents initialContents = self.get(DataComponents.BUNDLE_CONTENTS);
        if (initialContents == null) {
            return false;
        }

        BundleContents.Mutable contents = new BundleContents.Mutable(initialContents);

        if (clickAction == ClickAction.PRIMARY && !other.isEmpty()) {
            // Pile-on-pile merge: cursor pile (other) dumps into slot pile (self)
            if (other.getItem() instanceof PileOfFishItem) {
                BundleContents otherContents = other.get(DataComponents.BUNDLE_CONTENTS);
                if (otherContents == null) return false;

                BundleContents leftover = mergeContents(otherContents, contents);
                self.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());

                if (leftover == null) {
                    // Everything merged — destroy cursor pile
                    other.shrink(1);
                    carriedItem.set(ItemStack.EMPTY);
                } else if (leftover.items().size() == 1) {
                    // Only 1 item left — decompose pile into that item
                    ItemStack singleItem = leftover.items().getFirst().create();
                    other.shrink(1);
                    carriedItem.set(singleItem);
                } else {
                    // Some items didn't fit — update cursor pile with leftovers
                    other.set(DataComponents.BUNDLE_CONTENTS, leftover);
                }
                playInsertSound(player);
                broadcastChangesOnContainerMenu(player);
                return true;
            }

            // Gate: only allow fish / sized items
            if (!canInsertInPile(other)) {
                playInsertFailSound(player);
                self.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
                broadcastChangesOnContainerMenu(player);
                return true; // consume click
            }
            if (slot.allowModification(player) && contents.tryInsert(other) > 0) {
                playInsertSound(player);
            } else {
                playInsertFailSound(player);
            }

            self.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
            broadcastChangesOnContainerMenu(player);
            return true;
        } else if (clickAction == ClickAction.SECONDARY && other.isEmpty()) {
            if (slot.allowModification(player)) {
                ItemStack removed = contents.removeOne();
                if (removed != null) {
                    playRemoveOneSound(player);
                    carriedItem.set(removed);
                }
            }

            BundleContents updatedContents = contents.toImmutable();
            if (updatedContents.isEmpty()) {
                // Pile is now empty — destruct
                self.shrink(1);
            } else if (updatedContents.items().size() == 1) {
                // Only 1 item left — decompose pile into that item
                ItemStack singleItem = updatedContents.items().getFirst().create();
                self.shrink(1);
                slot.setByPlayer(singleItem);
            } else {
                self.set(DataComponents.BUNDLE_CONTENTS, updatedContents);
            }
            broadcastChangesOnContainerMenu(player);
            return true;
        } else {
            toggleSelectedItem(self, -1);
            return false;
        }
    }

    // --- Safety net & helpers ---

    /**
     * Safety net: if the pile becomes empty for any reason (e.g. the inherited
     * onUseTick → dropContent path), destroy it on the next inventory tick.
     */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        BundleContents contents = stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        if (contents.isEmpty()) {
            stack.shrink(1);
            return;
        }
        if (contents.items().size() == 1) {
            // Pile has exactly 1 item — replace the pile with that item
            ItemStack singleItem = contents.items().getFirst().create();
            if (entity instanceof Player player) {
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    if (player.getInventory().getItem(i) == stack) {
                        player.getInventory().setItem(i, singleItem);
                        return;
                    }
                }
            }
            // Fallback: couldn't locate the stack — just destroy it
            stack.shrink(1);
            return;
        }
        super.inventoryTick(stack, level, entity, slot);
    }

    private static void playRemoveOneSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }

    private static void playInsertSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }

    private static void playInsertFailSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1.0F, 1.0F);
    }

    private void broadcastChangesOnContainerMenu(Player player) {
        AbstractContainerMenu containerMenu = player.containerMenu;
        if (containerMenu != null) {
            containerMenu.slotsChanged(player.getInventory());
        }
    }
}

