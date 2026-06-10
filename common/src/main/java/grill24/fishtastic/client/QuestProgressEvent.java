package grill24.fishtastic.client;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Fired by QuestClientCache when a quest's progress increments.
 * Used by QuestProgressNotificationManager to enqueue HUD notifications.
 */
public record QuestProgressEvent(
        Identifier questId,
        int oldCount,
        int newCount,
        int targetCount,
        boolean completed,
        ItemStack triggeringItem
) {}
