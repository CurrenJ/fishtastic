package grill24.fishtastic.recipe;

import com.mojang.serialization.MapCodec;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.util.FishQualityHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Shapeless 1 dirt + 1 fish (any item tagged {@code minecraft:fishes}) -> 1 marine compost,
 * copying the fish's {@link FishQuality} onto the result so conversion yield still scales with quality.
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
        this.category = category;
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
            } else if (stack.is(ItemTags.FISHES) && !hasFish) {
                hasFish = true;
            } else {
                return false;
            }
        }

        return hasDirt && hasFish;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack result = new ItemStack(FishtasticBlocks.MARINE_COMPOST.value().asItem());

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.is(ItemTags.FISHES)) {
                FishQuality.Quality quality = FishQualityHelper.getQuality(stack);
                if (quality != null) {
                    FishQualityHelper.setQuality(result, quality);
                }
                break;
            }
        }

        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RecipeSerializer<MarineCompostRecipe> getSerializer() {
        return (RecipeSerializer<MarineCompostRecipe>) (RecipeSerializer<?>) FishtasticRecipeSerializers.MARINE_COMPOST.value();
    }
}
