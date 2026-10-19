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
import grill24.fishtastic.server.ServerTickHandler;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DataPackRegistryEvent;
import net.minecraftforge.registries.RegisterEvent;

@Mod(Fishtastic.MOD_ID)
public final class FishtasticForge {
    public FishtasticForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        GelatinMenusCompat.init();

        ForgeRegistrationApi api = ForgeRegistrationApi.INSTANCE;
        modEventBus.addListener((RegisterEvent event) -> {
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

        modEventBus.addListener((FMLCommonSetupEvent event) ->
                event.enqueueWork(grill24.fishtastic.FishtasticDispenseBehaviors::registerDispenseBehaviors));

        modEventBus.addListener((DataPackRegistryEvent.NewRegistry event) -> {
            event.dataPackRegistry(FishtasticRegistries.ITEM_EFFECT_REGISTRY_KEY, ItemEffect.CODEC, ItemEffect.CODEC);
            event.dataPackRegistry(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, grill24.fishtastic.data.FishProfile.CODEC, grill24.fishtastic.data.FishProfile.CODEC);
            event.dataPackRegistry(FishtasticRegistries.TEMPERAMENT_REGISTRY_KEY, grill24.fishtastic.data.Temperament.CODEC, grill24.fishtastic.data.Temperament.CODEC);
            event.dataPackRegistry(FishtasticRegistries.QUEST_REGISTRY_KEY, Quest.CODEC, Quest.CODEC);
            event.dataPackRegistry(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY, ShopEntry.CODEC, ShopEntry.CODEC);
            event.dataPackRegistry(FishtasticRegistries.FISH_ENCYCLOPEDIA_ENTRY_REGISTRY_KEY, grill24.fishtastic.data.FishEncyclopediaEntry.CODEC, grill24.fishtastic.data.FishEncyclopediaEntry.CODEC);
            event.dataPackRegistry(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY, grill24.fishtastic.fishtank.CosmeticStructure.CODEC, grill24.fishtastic.fishtank.CosmeticStructure.CODEC);
        });

        MinecraftForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> {
            // A player-specific sync fires before the player's registry synchronization; the
            // ItemStack-bearing quest packet is sent from PlayerLoggedInEvent instead.
            if (event.getPlayer() != null) return;
            var server = event.getPlayerList().getServer();
            var data = FishCatchSavedData.getOrCreate(server);
            for (var player : event.getPlayers()) {
                QuestSyncPacket.sendToPlayer(player, data);
                grill24.fishtastic.network.CompanionPartySyncPacket.syncAllTo(player);
            }
        });

        // Quest/party sync carries ItemStacks, so it must run after the player's registry
        // synchronization. OnDatapackSyncEvent fires before tags/recipes are sent.
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                QuestSyncPacket.sendToPlayer(player, FishCatchSavedData.getOrCreate(player.getServer()));
                grill24.fishtastic.tutorial.TutorialManager.onPlayerJoin(player);
                grill24.fishtastic.network.CompanionPartySyncPacket.syncAllTo(player);
            }
        });

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                ServerTickHandler.onServerTick(event.getServer());
            }
        });

        grill24.fishtastic.architectury.forge.ForgePacketRegistrar.register();
        grill24.fishtastic.network.FishtasticPackets.init();

        FishtasticConfig.register();

        // The testmod source set is only on the runGametest classpath. Keep production free of it
        // while still letting Forge's RegisterGameTestsEvent see the genuine Forge registration.
        // The listener lives here so its event type is the mod module's, not the testmod module's.
        if (!net.minecraftforge.fml.loading.FMLEnvironment.production) {
            modEventBus.addListener((net.minecraftforge.event.RegisterGameTestsEvent event) -> {
                try {
                    Class<?> registration = Class.forName(
                            "grill24.fishtastic.forge.gametest.ForgeGameTestRegistration");
                    registration.getMethod("install").invoke(null);
                    event.register((Class<?>) registration.getMethod("generatorClass").invoke(null));
                } catch (ClassNotFoundException ignored) {
                    // Normal development client/server runs do not include the testmod.
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("Failed to register Forge GameTests", e);
                }
            });
        }

        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            new grill24.fishtastic.forge.FishtasticForgeClient();
        }
    }
}
