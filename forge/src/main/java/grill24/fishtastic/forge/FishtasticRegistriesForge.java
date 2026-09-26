package grill24.fishtastic.forge;

import grill24.fishtastic.Fishtastic;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;

public class FishtasticRegistriesForge {
    public static DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Fishtastic.MOD_ID);
    public static DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, Fishtastic.MOD_ID);
    public static DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Fishtastic.MOD_ID);
    public static DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Fishtastic.MOD_ID);
    public static DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, Fishtastic.MOD_ID);
    public static DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, Fishtastic.MOD_ID);
    public static DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, Fishtastic.MOD_ID);
    public static DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Fishtastic.MOD_ID);
}
