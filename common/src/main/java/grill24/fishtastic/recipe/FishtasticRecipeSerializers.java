package grill24.fishtastic.recipe;

import grill24.fishtastic.architectury.RegistrationApiSided;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;

public class FishtasticRecipeSerializers {
    public static Holder<RecipeSerializer<?>> MARINE_COMPOST;

    public static void registerRecipeSerializers() {
        MARINE_COMPOST = RegistrationApiSided.getInstance().registerRecipeSerializer(
                "marine_compost",
                () -> new RecipeSerializer<MarineCompostRecipe>() {
                    @Override
                    public MapCodec<MarineCompostRecipe> codec() {
                        return MarineCompostRecipe.CODEC;
                    }

                    @Override
                    public StreamCodec<RegistryFriendlyByteBuf, MarineCompostRecipe> streamCodec() {
                        return MarineCompostRecipe.STREAM_CODEC;
                    }
                }
        );
    }
}
