package grill24.fishtastic.tutorial;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.component.ItemSize;
import grill24.fishtastic.data.PhaseRule;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.network.StartFishingMinigamePacket;
import grill24.fishtastic.network.TutorialSyncPacket;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.util.FishingTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.projectile.FishingHook;

import java.util.List;
import java.util.Optional;

/**
 * All server-side tutorial (FTUE) logic lives here.
 * External classes call into this via the public static hook methods;
 * no tutorial logic is scattered elsewhere.
 */
public class TutorialManager {

    public static final ResourceKey<Quest> TUTORIAL_QUEST_KEY =
            ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY,
                    Identifier.fromNamespaceAndPath("fishtastic", "tutorial/first_catch"));

    private static final Identifier TUTORIAL_QUEST_ID = TUTORIAL_QUEST_KEY.identifier();

    // -------------------------------------------------------------------------
    // Hooks — called by external systems (one-line callsites)
    // -------------------------------------------------------------------------

    /** Call when server detects the player joined. Resumes tutorial state. */
    public static void onPlayerJoin(ServerPlayer player) {
        TutorialStep step = getStep(player);
        if (step != TutorialStep.COMPLETE) {
            TutorialSyncPacket.sendToPlayer(player, step);
        }
    }

    /** Call when the player takes an item from a crafting result slot. */
    public static void onItemCrafted(ServerPlayer player, ItemStack stack) {
        if (!stack.is(FishtasticItems.COPPER_FISHING_ROD)) return;
        TutorialStep current = getStep(player);
        // Only start if tutorial hasn't been started yet (WAITING_FOR_CAST is default/pre-tutorial)
        if (current == TutorialStep.WAITING_FOR_CAST) {
            ItemStack worms = new ItemStack(FishtasticItems.WORMS.value(), 8);
            if (!player.getInventory().add(worms)) {
                player.drop(worms, false);
            }
            setStep(player, TutorialStep.BAIT_LOAD);
        }
    }

    /** Call after bait component is set on the player's rod. */
    public static void onBaitLoaded(ServerPlayer player) {
        if (getStep(player) == TutorialStep.BAIT_LOAD) {
            setStep(player, TutorialStep.WAITING_FOR_CAST);
        }
    }

    /** Call when a FishingHook entity spawns for this player (rod cast). */
    public static void onHookCast(ServerPlayer player) {
        TutorialStep step = getStep(player);
        if (step == TutorialStep.WAITING_FOR_CAST || step == TutorialStep.BAIT_LOAD) {
            setStep(player, TutorialStep.HOOK_IN_WATER);
        }
    }

    /**
     * Returns true if the next minigame session for this player should use tutorial
     * rules (single easy target, no bait consumed).
     */
    public static boolean isTutorialSession(ServerPlayer player) {
        return getStep(player) == TutorialStep.HOOK_IN_WATER;
    }

    /** Call when FishingMinigameManager sends StartFishingMinigamePacket. */
    public static void onMinigameStarted(ServerPlayer player) {
        if (getStep(player) == TutorialStep.HOOK_IN_WATER) {
            setStep(player, TutorialStep.MINIGAME_INTRO);
        }
    }

    /** Call when FishingMinigameManager.handleMinigameComplete fires. */
    public static void onMinigameComplete(ServerPlayer player) {
        if (getStep(player) == TutorialStep.MINIGAME_CATCH) {
            setStep(player, TutorialStep.CATCH_RESULT);
        }
    }

    /**
     * Call when CompleteQuestPacket handler grants quest rewards. CompleteQuestPacket itself has
     * no tutorial-step gate — a player can reach and click the claim button via a path that never
     * sent the QUEST_INTRO->QUEST_CLAIM advance packet (e.g. opening the quest log some way other
     * than the tracked keybind before that packet lands), claiming the quest while still on
     * CATCH_RESULT or QUEST_INTRO. Since the quest is then permanently marked claimed, an exact
     * QUEST_CLAIM match here would never fire again and softlock the tutorial. The claim is
     * strictly stronger evidence of progress than either step it's meant to satisfy, so accept it
     * at any point before SHOP_BROWSE rather than requiring the exact expected step.
     */
    public static void onQuestClaimed(ServerPlayer player, Identifier questId) {
        if (TUTORIAL_QUEST_ID.equals(questId) && getStep(player).ordinal() < TutorialStep.SHOP_BROWSE.ordinal()) {
            setStep(player, TutorialStep.SHOP_BROWSE);
        }
    }

    /** Called by TutorialAdvancePacket — client-initiated step advance. */
    public static void advanceStep(ServerPlayer player, TutorialStep fromStep) {
        if (getStep(player) != fromStep) return;
        TutorialStep next = switch (fromStep) {
            case MINIGAME_INTRO    -> TutorialStep.MINIGAME_CONTROL;
            case MINIGAME_CONTROL  -> TutorialStep.MINIGAME_CATCH;
            case CATCH_RESULT      -> TutorialStep.QUEST_INTRO;
            case QUEST_INTRO       -> TutorialStep.QUEST_CLAIM;
            case SHOP_BROWSE       -> TutorialStep.COMPLETE;
            default -> null;
        };
        if (next != null) setStep(player, next);
    }

    // -------------------------------------------------------------------------
    // Tutorial fishing session target generation
    // -------------------------------------------------------------------------

    /**
     * Generates the single easy target used in the tutorial minigame session.
     * Called by FishingMinigameManager when isTutorialSession() is true.
     */
    public static StartFishingMinigamePacket.TargetData buildTutorialTarget(ServerPlayer player) {
        ItemStack fish = new ItemStack(FishtasticItems.BLUEGILL.value());
        fish.set(FishtasticDataComponents.FISH_QUALITY.value(), new FishQuality(FishQuality.Quality.COMMON));
        fish.set(FishtasticDataComponents.ITEM_SIZE.value(), new ItemSize(12.0f));

        List<PhaseRule> phases = List.of(new PhaseRule(
                0f,
                List.of(FishingTarget.MovementPattern.DRIFT),
                Optional.empty(),
                Optional.of(60f),
                Optional.of(120f),
                Optional.empty()
        ));

        return new StartFishingMinigamePacket.TargetData(
                List.of(fish),
                FishingTarget.TargetCategory.FISH,
                0.5f,
                0.15f,
                phases
        );
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    public static TutorialStep getStep(ServerPlayer player) {
        return FishCatchSavedData.getOrCreate(player.level().getServer()).getTutorialStep(player);
    }

    private static void setStep(ServerPlayer player, TutorialStep step) {
        FishCatchSavedData data = FishCatchSavedData.getOrCreate(player.level().getServer());
        data.setTutorialStep(player, step);
        TutorialSyncPacket.sendToPlayer(player, step);
    }
}
