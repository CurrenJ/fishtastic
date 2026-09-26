package grill24.fishtastic.recipe;

import grill24.fishtastic.architectury.RegistrationApiSided;
import net.minecraft.core.Holder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

public class FishtasticRecipeSerializers {
    public static Holder<RecipeSerializer<?>> MARINE_COMPOST;

    public static void registerRecipeSerializers() {
        MARINE_COMPOST = RegistrationApiSided.getInstance().registerRecipeSerializer(
                "marine_compost",
                () -> new SimpleCraftingRecipeSerializer<>(MarineCompostRecipe::new)
        );
    }
}
