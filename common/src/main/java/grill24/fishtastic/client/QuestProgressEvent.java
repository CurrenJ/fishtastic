package grill24.fishtastic.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Fired by QuestClientCache when a quest's progress increments.
 * Used by QuestProgressNotificationManager to enqueue HUD notifications.
 */
public record QuestProgressEvent(
        ResourceLocation questId,
        int oldCount,
        int newCount,
        int targetCount,
        boolean completed,
        ItemStack triggeringItem
) {}
