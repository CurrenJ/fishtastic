package grill24.fishtastic.neoforge;

import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.fishtank.FishTankFrameType;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

import static grill24.fishtastic.util.Utility.ft;

public class FishtasticRegistriesNeoForge {
    public static DeferredRegister<Item> ITEMS = DeferredRegister.createItems(Fishtastic.MOD_ID);
    public static DeferredRegister<Block> BLOCKS = DeferredRegister.createBlocks(Fishtastic.MOD_ID);
    public static DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Fishtastic.MOD_ID);
    public static DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Fishtastic.MOD_ID);
    public static DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Fishtastic.MOD_ID);
    public static DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, Fishtastic.MOD_ID);
    public static DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, Fishtastic.MOD_ID);
    public static DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, Fishtastic.MOD_ID);
    public static DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Fishtastic.MOD_ID);

    public static final DefaultedRegistry<FishTankFrameType> FISH_TANK_FRAME_TYPE_REGISTRY = (DefaultedRegistry<FishTankFrameType>) new RegistryBuilder<>(FishtasticRegistries.FISH_TANK_FRAME_TYPE_REGISTRY_KEY).defaultKey(ft("oak")).sync(false).create();

    public static void registerRegistries(NewRegistryEvent event) {
        event.register(FISH_TANK_FRAME_TYPE_REGISTRY);
    }

}
