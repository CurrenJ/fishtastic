package grill24.fishtastic.forge;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticCreativeTabs;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.FishtasticMenuTypes;
import grill24.fishtastic.FishtasticParticleTypes;
import grill24.fishtastic.FishtasticSounds;
import grill24.fishtastic.architectury.forge.ForgePacketRegistrar;
import grill24.fishtastic.architectury.forge.ForgeRegistrationApi;
import grill24.fishtastic.compat.GelatinMenusCompat;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.ShopEntry;
import grill24.fishtastic.itemeffect.ItemEffect;
import grill24.fishtastic.network.FishtasticPackets;
import grill24.fishtastic.network.QuestSyncPacket;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.ServerTickHandler;

import grill24.fishtastic.Fishtastic;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DataPackRegistryEvent;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

/**
 * Forge 47 entrypoint (B5.3: NeoForge's constructor-injected {@code IEventBus modEventBus,
 * ModContainer container} becomes {@code FMLJavaModLoadingContext.get().getModEventBus()} inside a
 * no-arg constructor; NeoForge's own event bus is {@code MinecraftForge.EVENT_BUS} here).
 */
@Mod(Fishtastic.MOD_ID)
public final class FishtasticForge {
    public FishtasticForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Try to register GelatinUI menus, if GelatinUI is present.
        GelatinMenusCompat.init();

        // __BEGIN:item_registration:init_forge
        FishtasticDataComponents.registerDataComponents();

        // Forge 47 unfreezes each registry only while its own RegisterEvent runs, so every common
        // register*() is called from inside the event for its registry and gets that registry's
        // real holders back (see ForgeRegistrationApi). Blocks fire before items, so the BlockItems
        // registerBlocks() queues are flushed in the ITEM event.
        ForgeRegistrationApi api = ForgeRegistrationApi.INSTANCE;
        modEventBus.addListener((RegisterEvent event) -> {
            ResourceKey<? extends Registry<?>> key = event.getRegistryKey();
            if (key.equals(Registries.SOUND_EVENT)) {
                api.registerDuring(event, FishtasticSounds::registerSounds);
            } else if (key.equals(Registries.BLOCK)) {
                api.registerDuring(event, FishtasticBlocks::registerBlocks);
            } else if (key.equals(Registries.ITEM)) {
                api.registerDuring(event, () -> {
                    FishtasticItems.registerItems();
                    api.registerPendingBlockItems();
                });
            } else if (key.equals(Registries.PARTICLE_TYPE)) {
                api.registerDuring(event, FishtasticParticleTypes::registerParticleTypes);
            } else if (key.equals(Registries.BLOCK_ENTITY_TYPE)) {
                api.registerDuring(event, FishtasticBlockEntityTypes::registerBlockEntityTypes);
            } else if (key.equals(Registries.MENU)) {
                api.registerDuring(event, FishtasticMenuTypes::registerMenuTypes);
            } else if (key.equals(Registries.RECIPE_SERIALIZER)) {
                api.registerDuring(event, grill24.fishtastic.recipe.FishtasticRecipeSerializers::registerRecipeSerializers);
            } else if (key.equals(Registries.LOOT_FUNCTION_TYPE)) {
                api.registerDuring(event, grill24.fishtastic.loot.CopyTankDataFunction::registerLootFunctions);
            } else if (key.equals(Registries.CREATIVE_MODE_TAB)) {
                api.registerDuring(event, FishtasticCreativeTabs::registerCreativeTabs);
            }
        });

        // Deferred to common setup, not called inline: registerDispenseBehaviors() dereferences
        // FishtasticBlocks.MARINE_COMPOST.value(), and blocks aren't registered until their
        // RegisterEvent, after the mod constructor has run.
        // enqueueWork because DispenserBlock's behavior map is plain mutable global state and
        // common setup runs in parallel across mods. Fabric calls it inline in onInitialize and is
        // fine — its registries are populated eagerly.
        modEventBus.addListener((FMLCommonSetupEvent event) ->
                event.enqueueWork(grill24.fishtastic.FishtasticDispenseBehaviors::registerDispenseBehaviors));

        // Register datapack registries
        modEventBus.addListener((DataPackRegistryEvent.NewRegistry event) -> {
            Fishtastic.LOGGER.info("Registering ItemEffect datapack registry");
            event.dataPackRegistry(FishtasticRegistries.ITEM_EFFECT_REGISTRY_KEY, ItemEffect.CODEC, ItemEffect.CODEC);
            Fishtastic.LOGGER.info("Registering FishProfile datapack registry");
            event.dataPackRegistry(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, grill24.fishtastic.data.FishProfile.CODEC, grill24.fishtastic.data.FishProfile.CODEC);
            Fishtastic.LOGGER.info("Registering Temperament datapack registry");
            event.dataPackRegistry(FishtasticRegistries.TEMPERAMENT_REGISTRY_KEY, grill24.fishtastic.data.Temperament.CODEC, grill24.fishtastic.data.Temperament.CODEC);
            Fishtastic.LOGGER.info("Registering Quest datapack registry");
            event.dataPackRegistry(FishtasticRegistries.QUEST_REGISTRY_KEY, Quest.CODEC, Quest.CODEC);
            Fishtastic.LOGGER.info("Registering ShopEntry datapack registry");
            event.dataPackRegistry(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY, ShopEntry.CODEC, ShopEntry.CODEC);
            Fishtastic.LOGGER.info("Registering FishEncyclopediaEntry datapack registry");
            event.dataPackRegistry(FishtasticRegistries.FISH_ENCYCLOPEDIA_ENTRY_REGISTRY_KEY, grill24.fishtastic.data.FishEncyclopediaEntry.CODEC, grill24.fishtastic.data.FishEncyclopediaEntry.CODEC);
            Fishtastic.LOGGER.info("Registering CosmeticStructure datapack registry");
            event.dataPackRegistry(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY, grill24.fishtastic.fishtank.CosmeticStructure.CODEC, grill24.fishtastic.fishtank.CosmeticStructure.CODEC);
        });

        // Send quest and tutorial state to player on world join
        MinecraftForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> {
            if (event.getPlayer() == null) return;
            QuestSyncPacket.sendToPlayer(event.getPlayer(), FishCatchSavedData.getOrCreate(((ServerLevel) event.getPlayer().level()).getServer()));
            grill24.fishtastic.tutorial.TutorialManager.onPlayerJoin(event.getPlayer());
            grill24.fishtastic.server.SunsetExtensionHandler.onPlayerJoin(event.getPlayer());
        });

        // Register network packets
        ForgePacketRegistrar.register();
        FishtasticPackets.init();

        // Register server tick handler
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                ServerTickHandler.onServerTick(event.getServer());
            }
        });

        // Register config
        FishtasticConfig.register();

        // Forge 47's @Mod has no dist filter (that's NeoForge). A plain dist check, not
        // DistExecutor.safeRunWhenOn: that validates its referent on every dist, which links
        // FishtasticForgeClient::new and loads client-only classes on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            new FishtasticForgeClient();
        }
    }
}
