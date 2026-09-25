package grill24.fishtastic.server;

import grill24.fishtastic.blockentity.FishTankBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Portstub (gradle/port-excludes.gradle): temporary stand-in for the real 582-line
 * {@code QuestTracker}, which pulls in the full quest/registry graph (grill24.FishtasticRegistries,
 * data/{Quest,QuestCategory,QuestDifficulty,QuestObjective}, tutorial/*, network/QuestSyncPacket) —
 * none of that is B2.6's scope. {@link grill24.fishtastic.block.FishTankBlock#checkTankQuests} only
 * needs {@link #onTankChanged}'s signature to resolve; real file still exists (excluded) at
 * server/QuestTracker.java, delete this stub once that graph is ported for real.
 */
public class QuestTracker {
    public static void onTankChanged(MinecraftServer server, ServerPlayer player, FishTankBlockEntity tank, ItemStack placedStack) {
    }
}
