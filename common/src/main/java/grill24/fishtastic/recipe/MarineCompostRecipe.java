package grill24.fishtastic.recipe;

import com.mojang.serialization.MapCodec;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.item.PileOfFishItem;
import grill24.fishtastic.util.FishQualityHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import grill24.fishtastic.component.BundleContents;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Shapeless 1 dirt + 1 fish (any item tagged {@code minecraft:fishes}, or a Pile of Fish whose
 * top item is one) -> 1 marine compost, copying the fish's {@link FishQuality} onto the result so
 * conversion yield still scales with quality. Crafting from a pile pops just the top fish off,
 * leaving the remainder of the pile behind via {@link #getRemainingItems}.
 */
public class MarineCompostRecipe extends CustomRecipe {
    public static final MapCodec<MarineCompostRecipe> CODEC = CraftingBookCategory.CODEC
            .optionalFieldOf("category", CraftingBookCategory.MISC)
            .xmap(MarineCompostRecipe::new, MarineCompostRecipe::category);

    public static final StreamCodec<RegistryFriendlyByteBuf, MarineCompostRecipe> STREAM_CODEC =
            CraftingBookCategory.STREAM_CODEC.<RegistryFriendlyByteBuf>cast()
                    .map(MarineCompostRecipe::new, MarineCompostRecipe::category);

    private final CraftingBookCategory category;

    public MarineCompostRecipe(CraftingBookCategory category) {
        super(category);
        this.category = category;
    }

    /** Abstract before 1.21.2: the recipe needs a grid with room for the dirt and the fish. */
    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public CraftingBookCategory category() {
        return category;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        boolean hasDirt = false;
        boolean hasFish = false;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.is(Items.DIRT) && !hasDirt) {
                hasDirt = true;
            } else if (!hasFish && (stack.is(ItemTags.FISHES) || pileTopFish(stack) != null)) {
                hasFish = true;
            } else {
                return false;
            }
        }

        return hasDirt && hasFish;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack result = new ItemStack(FishtasticBlocks.MARINE_COMPOST.value().asItem());

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            ItemStack fish = stack.is(ItemTags.FISHES) ? stack : pileTopFish(stack);
            if (fish != null) {
                FishQuality.Quality quality = FishQualityHelper.getQuality(fish);
                if (quality != null) {
                    FishQualityHelper.setQuality(result, quality);
                }
                break;
            }
        }

        return result;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> result = super.getRemainingItems(input);

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (pileTopFish(stack) == null) continue;

            BundleContents.Mutable contents = new BundleContents.Mutable(FishtasticItemData.bundleContents(stack));
            contents.removeOne();
            BundleContents remaining = contents.toImmutable();

            if (remaining.isEmpty()) {
                result.set(i, ItemStack.EMPTY);
            } else if (remaining.size() == 1) {
                result.set(i, remaining.getItemUnsafe(0).copy());
            } else {
                ItemStack newPile = new ItemStack(FishtasticItems.PILE_OF_FISH.value());
                FishtasticItemData.setBundleContents(newPile, remaining);
                result.set(i, newPile);
            }
            break;
        }

        return result;
    }

    /**
     * Returns the fish-tagged item on top of the given Pile of Fish stack (the one
     * {@link BundleContents.Mutable#removeOne} would pop), or {@code null} if {@code stack} isn't a
     * non-empty pile, or its top item isn't fish.
     */
    @Nullable
    private static ItemStack pileTopFish(ItemStack stack) {
        if (!(stack.getItem() instanceof PileOfFishItem)) return null;

        BundleContents contents = FishtasticItemData.bundleContents(stack);
        if (contents == null || contents.isEmpty()) return null;

        ItemStack top = contents.getItemUnsafe(0).copy();
        return top.is(ItemTags.FISHES) ? top : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RecipeSerializer<MarineCompostRecipe> getSerializer() {
        return (RecipeSerializer<MarineCompostRecipe>) (RecipeSerializer<?>) FishtasticRecipeSerializers.MARINE_COMPOST.value();
    }
}
