package grill24.fishtastic.tutorial;

import grill24.fishtastic.network.CompanionTutorialSyncPacket;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.companion.CompanionStorage;
import grill24.fishtastic.menu.CompanionMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Persistent companion lessons. Inventory operations advance the hands-on steps on the server;
 * acknowledgements only advance the optional explanations. No timers or custom keys are required.
 */
public final class CompanionTutorialManager {
    private CompanionTutorialManager() {}

    /** Call on the transition to "this player has at least one unlocked bond" (any number of times). */
    public static void onBondUnlocked(ServerPlayer player) {
        if (getStep(player) == CompanionTutorialStep.NOT_STARTED) {
            ensureBook(player);
            setStep(player, CompanionTutorialStep.INTRO);
        }
    }

    /** Opening the menu starts the lesson; it never counts as equipping a companion. */
    public static void onCompanionsTabOpened(ServerPlayer player) {
        resume(player);
        CompanionTutorialStep step = getStep(player);
        boolean equipped = !CompanionStorage.party(player).isEmpty();
        if (step == CompanionTutorialStep.INTRO || step == CompanionTutorialStep.EQUIP && equipped) {
            setStep(player, equipped ? CompanionTutorialStep.RETURN : CompanionTutorialStep.EQUIP);
        } else if (step == CompanionTutorialStep.RETURN && !equipped) {
            // Closing a menu returns its cursor stack. Resume after a manual return-and-close.
            setStep(player, CompanionTutorialStep.REEQUIP);
        }
    }

    /** Refresh the discovery hint whenever the player opens the book. */
    public static void onEncyclopediaOpened(ServerPlayer player) {
        resume(player);
    }

    public static void onPlayerJoin(ServerPlayer player) {
        if (getStep(player).hasOverlay()) ensureBook(player);
        resume(player);
    }

    private static void resume(ServerPlayer player) {
        if (getStep(player) == CompanionTutorialStep.NOT_STARTED
                && !FishCatchSavedData.getOrCreate(player.level().getServer()).getOrCreateQuestState(player)
                .getUnlockedCompanionSnapshot().isEmpty()) {
            onBondUnlocked(player);
        } else {
            CompanionTutorialSyncPacket.sendToPlayer(player, getStep(player));
        }
    }

    public static void onEquipped(ServerPlayer player) {
        if (CompanionStorage.party(player).isEmpty()) return;
        switch (getStep(player)) {
            case INTRO, EQUIP -> setStep(player, CompanionTutorialStep.RETURN);
            case REEQUIP -> setStep(player, CompanionTutorialStep.LEAD);
            default -> { }
        }
    }

    public static void onReturned(ServerPlayer player) {
        if (getStep(player) == CompanionTutorialStep.RETURN) setStep(player, CompanionTutorialStep.REEQUIP);
    }

    public static void onLeadSelected(ServerPlayer player) {
        if (getStep(player) == CompanionTutorialStep.LEAD && !CompanionStorage.get(player, 0).isEmpty()) {
            setStep(player, CompanionTutorialStep.RENAME);
        }
    }

    public static void onRenamed(ServerPlayer player) {
        if (getStep(player) == CompanionTutorialStep.RENAME) setStep(player, CompanionTutorialStep.GROUPS);
    }

    /** The packet cannot skip an equipment action or advance a stale step. */
    public static void advanceStep(ServerPlayer player, CompanionTutorialStep from, boolean skip) {
        if (!(player.containerMenu instanceof CompanionMenu menu) || !menu.getCarried().isEmpty()
                || getStep(player) != from || !from.hasOverlay()) {
            CompanionTutorialSyncPacket.sendToPlayer(player, getStep(player));
            return;
        }
        if (skip) {
            setStep(player, CompanionTutorialStep.COMPLETE);
            return;
        }
        CompanionTutorialStep next = switch (from) {
            case LEAD -> CompanionTutorialStep.RENAME;
            case RENAME -> CompanionTutorialStep.GROUPS;
            case GROUPS -> CompanionTutorialStep.PILES;
            case PILES -> CompanionTutorialStep.COMPLETE;
            default -> null;
        };
        if (next != null) setStep(player, next);
    }

    public static CompanionTutorialStep getStep(ServerPlayer player) {
        return FishCatchSavedData.getOrCreate(player.level().getServer()).getCompanionTutorialStep(player);
    }

    /** Replay without changing earned unlocks, names, party contents or inventory. */
    public static void reset(ServerPlayer player) {
        var unlocked = FishCatchSavedData.getOrCreate(player.level().getServer()).getOrCreateQuestState(player)
                .getUnlockedCompanionSnapshot();
        if (!unlocked.isEmpty()) ensureBook(player);
        setStep(player, unlocked.isEmpty() ? CompanionTutorialStep.NOT_STARTED : CompanionTutorialStep.INTRO);
    }

    private static void ensureBook(ServerPlayer player) {
        if (player.getInventory().hasAnyOf(java.util.Set.of(FishtasticItems.FISHOPEDIA.value()))
                || player.getOffhandItem().is(FishtasticItems.FISHOPEDIA.value())) return;
        ItemStack book = new ItemStack(FishtasticItems.FISHOPEDIA.value());
        if (!player.getInventory().add(book)) player.drop(book, false);
    }

    private static void setStep(ServerPlayer player, CompanionTutorialStep step) {
        FishCatchSavedData.getOrCreate(player.level().getServer()).setCompanionTutorialStep(player, step);
        CompanionTutorialSyncPacket.sendToPlayer(player, step);
    }
}
