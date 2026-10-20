package grill24.fishtastic.tutorial;

import grill24.fishtastic.network.CompanionTutorialSyncPacket;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.companion.CompanionStorage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * One persistent equip hint. Unlock discovery uses a brief notification; optional controls
 * explain themselves inside the party screen.
 */
public final class CompanionTutorialManager {
    private CompanionTutorialManager() {}

    /** Call on the transition to "this player has at least one unlocked bond" (any number of times). */
    public static void onBondUnlocked(ServerPlayer player) {
        if (getStep(player) == CompanionTutorialStep.NOT_STARTED) {
            ensureBook(player);
            setStep(player, CompanionTutorialStep.INTRO);
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("tutorial.fishtastic.companion.discovery"));
        }
    }

    /** Opening the menu starts the lesson; it never counts as equipping a companion. */
    public static void onCompanionsTabOpened(ServerPlayer player) {
        resume(player);
        CompanionTutorialStep step = getStep(player);
        if (step.hasOverlay()) setStep(player, CompanionStorage.party(player).isEmpty()
                ? CompanionTutorialStep.EQUIP : CompanionTutorialStep.COMPLETE);
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
        CompanionTutorialStep step = getStep(player);
        if (step.hasOverlay() && step != CompanionTutorialStep.INTRO && step != CompanionTutorialStep.EQUIP) {
            // Old tours already equipped a fish. Do not ask players to repeat or undo it.
            setStep(player, CompanionTutorialStep.COMPLETE);
            return;
        }
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
        if (getStep(player).hasOverlay()) setStep(player, CompanionTutorialStep.COMPLETE);
    }

    public static void onReturned(ServerPlayer player) {
        // Removal is explained by the slot tooltip, without another lesson.
    }

    public static void onLeadSelected(ServerPlayer player) {
        // Lead selection is optional.
    }

    public static void onRenamed(ServerPlayer player) {
        // Naming is optional.
    }

    /** Dismissal never mutates inventory; ordinary advances cannot replace a real equip. */
    public static void advanceStep(ServerPlayer player, CompanionTutorialStep from, boolean skip) {
        if (getStep(player) != from || !from.hasOverlay()) {
            CompanionTutorialSyncPacket.sendToPlayer(player, getStep(player));
            return;
        }
        if (skip) {
            dismiss(player);
            return;
        }
        // Only a real equip completes the remaining hands-on hint.
    }

    public static void dismiss(ServerPlayer player) {
        setStep(player, CompanionTutorialStep.COMPLETE);
    }

    public static CompanionTutorialStep getStep(ServerPlayer player) {
        return FishCatchSavedData.getOrCreate(player.level().getServer()).getCompanionTutorialStep(player);
    }

    /** Replay without changing earned unlocks, names, party contents or inventory. */
    public static void reset(ServerPlayer player) {
        var unlocked = FishCatchSavedData.getOrCreate(player.level().getServer()).getOrCreateQuestState(player)
                .getUnlockedCompanionSnapshot();
        if (!unlocked.isEmpty()) ensureBook(player);
        setStep(player, unlocked.isEmpty() ? CompanionTutorialStep.NOT_STARTED
                : CompanionStorage.party(player).isEmpty() ? CompanionTutorialStep.INTRO : CompanionTutorialStep.COMPLETE);
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
