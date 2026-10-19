package grill24.fishtastic.forge;

import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticCreativeTabs;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.FishtasticMenuTypes;
import grill24.fishtastic.FishtasticParticleTypes;
import grill24.fishtastic.FishtasticSounds;
import grill24.fishtastic.architectury.forge.ForgeRegistrationApi;
import grill24.fishtastic.compat.GelatinMenusCompat;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.ShopEntry;
import grill24.fishtastic.itemeffect.ItemEffect;
import grill24.fishtastic.network.QuestSyncPacket;
import grill24.fishtastic.server.FishCatchSavedData;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DataPackRegistryEvent;
import net.minecraftforge.registries.RegisterEvent;

@Mod(Fishtastic.MOD_ID)
public final class FishtasticForge {
    public FishtasticForge() {
        BusGroup modBusGroup = FMLJavaModLoadingContext.get().getModBusGroup();

        GelatinMenusCompat.init();

        ForgeRegistrationApi api = ForgeRegistrationApi.INSTANCE;
        RegisterEvent.getBus(modBusGroup).addListener(event -> {
            ResourceKey<? extends Registry<?>> key = event.getRegistryKey();
            if (key.equals(Registries.DATA_COMPONENT_TYPE)) {
                api.registerDuring(event, FishtasticDataComponents::registerDataComponents);
            } else if (key.equals(Registries.SOUND_EVENT)) {
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
            } else if (key.equals(Registries.CREATIVE_MODE_TAB)) {
                api.registerDuring(event, FishtasticCreativeTabs::registerCreativeTabs);
            }
        });

        FMLCommonSetupEvent.getBus(modBusGroup).addListener(event ->
                event.enqueueWork(grill24.fishtastic.FishtasticDispenseBehaviors::registerDispenseBehaviors));

        DataPackRegistryEvent.NewRegistry.BUS.addListener(event -> {
            event.dataPackRegistry(FishtasticRegistries.ITEM_EFFECT_REGISTRY_KEY, ItemEffect.CODEC, ItemEffect.CODEC);
            event.dataPackRegistry(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, grill24.fishtastic.data.FishProfile.CODEC, grill24.fishtastic.data.FishProfile.CODEC);
            event.dataPackRegistry(FishtasticRegistries.TEMPERAMENT_REGISTRY_KEY, grill24.fishtastic.data.Temperament.CODEC, grill24.fishtastic.data.Temperament.CODEC);
            event.dataPackRegistry(FishtasticRegistries.QUEST_REGISTRY_KEY, Quest.CODEC, Quest.CODEC);
            event.dataPackRegistry(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY, ShopEntry.CODEC, ShopEntry.CODEC);
            event.dataPackRegistry(FishtasticRegistries.FISH_ENCYCLOPEDIA_ENTRY_REGISTRY_KEY,
                    grill24.fishtastic.data.FishEncyclopediaEntry.CODEC,
                    grill24.fishtastic.data.FishEncyclopediaEntry.CODEC);
            event.dataPackRegistry(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY,
                    grill24.fishtastic.fishtank.CosmeticStructure.CODEC,
                    grill24.fishtastic.fishtank.CosmeticStructure.CODEC);
        });

        OnDatapackSyncEvent.BUS.addListener(event -> {
            if (event.getPlayer() != null) return;
            var data = FishCatchSavedData.getOrCreate(event.getPlayerList().getServer());
            for (var player : event.getPlayers()) {
                QuestSyncPacket.sendToPlayer(player, data);
            }
        });

        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                QuestSyncPacket.sendToPlayer(player,
                        FishCatchSavedData.getOrCreate(player.level().getServer()));
                grill24.fishtastic.tutorial.TutorialManager.onPlayerJoin(player);
                grill24.fishtastic.network.CompanionPartySyncPacket.syncAllTo(player);
            }
        });

        net.minecraftforge.event.TickEvent.ServerTickEvent.Post.BUS.addListener(event ->
                grill24.fishtastic.server.ServerTickHandler.onServerTick(event.server()));

        RegisterCommandsEvent.BUS.addListener(event ->
                grill24.fishtastic.command.FishtasticCommand.register(event.getDispatcher()));

        grill24.fishtastic.architectury.forge.ForgePacketRegistrar.register();
        grill24.fishtastic.network.FishtasticPackets.init();
        FishtasticConfig.register();

        if (net.minecraftforge.fml.loading.FMLEnvironment.dist
                == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            FishtasticForgeClient.init();
        }
    }
}
