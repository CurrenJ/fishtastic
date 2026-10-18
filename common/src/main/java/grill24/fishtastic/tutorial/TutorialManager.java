package grill24.fishtastic.tutorial;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.component.ItemSize;
import grill24.fishtastic.data.PhaseRule;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.network.StartFishingMinigamePacket;
import grill24.fishtastic.network.TutorialSyncPacket;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.util.FishingTarget;
import grill24.fishtastic.util.Ids;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

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
                    Ids.of("fishtastic", "tutorial/first_catch"));

    private static final Identifier TUTORIAL_QUEST_ID = TUTORIAL_QUEST_KEY.identifier();

    /**
     * The {@code minecraft:recipe_crafted} advancement that {@code PlayerAdvancementsMixin}
     * watches for to drive {@link #onItemCrafted}. Also referenced by {@code TutorialCommand}'s
     * reset, which must revoke it — otherwise a re-crafted rod after a debug reset would never
     * re-fire the advancement criterion (already granted) and the tutorial would stay stuck.
     */
    public static final Identifier TUTORIAL_ROD_ADVANCEMENT_ID =
            Ids.of("fishtastic", "tutorial/craft_copper_fishing_rod");
    public static final String TUTORIAL_ROD_ADVANCEMENT_CRITERION = "crafted_copper_fishing_rod";

    // -------------------------------------------------------------------------
    // Hooks — called by external systems (one-line callsites)
    // -------------------------------------------------------------------------

    /** Call when server detects the player joined. Resumes tutorial state. */
    public static void onPlayerJoin(ServerPlayer player) {
        CompanionTutorialManager.onPlayerJoin(player);
        TutorialStep step = getStep(player);
        // Also supply missing access books when upgrading an unfinished tutorial.
        if (step.hasOverlay() && step != TutorialStep.FISHING_OFFER) giveBooks(player);
        if (isPracticeStep(step) || step == TutorialStep.HOOK_IN_WATER) {
            // Sessions and bobbers cannot be reconstructed from a saved tutorial step.
            setStep(player, TutorialStep.WAITING_FOR_CAST);
        } else {
            TutorialSyncPacket.sendToPlayer(player, step);
        }
    }

    /**
     * Revokes {@link #TUTORIAL_ROD_ADVANCEMENT_ID} so a player who re-crafts the starter rod
     * after a debug {@code /tutorial reset} can re-trigger {@code PlayerAdvancementsMixin} and
     * reach BAIT_LOAD again — without this, the advancement criterion stays granted forever and
     * {@link #onItemCrafted} never fires a second time.
     */
    public static void revokeRodCraftedAdvancement(ServerPlayer player) {
        AdvancementHolder rodAdvancement =
                player.level().getServer().getAdvancements().get(TUTORIAL_ROD_ADVANCEMENT_ID);
        if (rodAdvancement != null) {
            player.getAdvancements().revoke(rodAdvancement, TUTORIAL_ROD_ADVANCEMENT_CRITERION);
        }
    }

    /** Whether the crafting entry point has fired; casting never awards this advancement. */
    public static boolean hasCraftedStarterRod(ServerPlayer player) {
        AdvancementHolder rodAdvancement =
                player.level().getServer().getAdvancements().get(TUTORIAL_ROD_ADVANCEMENT_ID);
        if (rodAdvancement == null) return false;
        return player.getAdvancements().getOrStartProgress(rodAdvancement).isDone();
    }

    /**
     * Call when the player is granted the {@code fishtastic:tutorial/craft_copper_fishing_rod}
     * advancement (i.e. actually crafted the starter rod, by any take-from-result-slot path).
     */
    public static void onItemCrafted(ServerPlayer player) {
        if (getStep(player) == TutorialStep.NOT_STARTED || getStep(player) == TutorialStep.DEFERRED) {
            setStep(player, TutorialStep.FISHING_OFFER);
        }
    }

    private static void giveStarterItems(ServerPlayer player) {
        give(player, new ItemStack(FishtasticItems.WORMS.value(), 8));
        giveBooks(player);
    }

    private static void giveBooks(ServerPlayer player) {
        for (var book : List.of(FishtasticItems.QUEST_BOOK.value(), FishtasticItems.FISHOPEDIA.value(),
                FishtasticItems.LEADERBOARDS_BOOK.value())) {
            if (!player.getInventory().contains(stack -> stack.is(book))) give(player, new ItemStack(book));
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    /**
     * Call after bait component is set on the player's rod, from a code path that's already
     * running server-side (e.g. a normal survival container click). Creative's Inventory tab
     * never sends its rearrangement clicks to the server, so bait loaded there instead reaches
     * this same transition via {@link #advanceStep}'s BAIT_LOAD case, polled client-side by
     * TutorialClientHandler and sent as a TutorialAdvancePacket.
     */
    public static void onBaitLoaded(ServerPlayer player) {
        if (getStep(player) == TutorialStep.BAIT_LOAD) {
            setStep(player, TutorialStep.WAITING_FOR_CAST);
        }
    }

    /** Called with the actual used stack after a successful cast, including creative/given rods. */
    public static void onHookCast(ServerPlayer player, ItemStack rod) {
        if (!rod.is(FishtasticItems.COPPER_FISHING_ROD.value())) return;
        TutorialStep step = getStep(player);
        if (step == TutorialStep.NOT_STARTED || step == TutorialStep.DEFERRED) {
            setStep(player, TutorialStep.FISHING_OFFER);
            return;
        }
        if (step == TutorialStep.WAITING_FOR_CAST
                || step == TutorialStep.BAIT_LOAD || isPracticeStep(step)) {
            setStep(player, TutorialStep.HOOK_IN_WATER);
        }
    }

    private static boolean isPracticeStep(TutorialStep step) {
        return step == TutorialStep.MINIGAME_INTRO || step == TutorialStep.MINIGAME_CONTROL
                || step == TutorialStep.MINIGAME_CATCH;
    }

    public static void onPracticeInterrupted(ServerPlayer player) {
        if (isPracticeStep(getStep(player))) setStep(player, TutorialStep.WAITING_FOR_CAST);
    }

    public static void onQuestLogOpened(ServerPlayer player) {
        TutorialStep step = getStep(player);
        if (step == TutorialStep.CATCH_RESULT || step == TutorialStep.QUEST_INTRO) {
            setStep(player, TutorialStep.QUEST_CLAIM);
        }
    }

    public static void onEncyclopediaOpened(ServerPlayer player) {
        if (getStep(player) == TutorialStep.ENCYCLOPEDIA_OPEN) setStep(player, TutorialStep.COMPLETE);
    }

    /**
     * Returns true if the next minigame session for this player should use tutorial
     * rules (single easy target with no catch-progress drain).
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

    /** Accept an early claim too: a player may already have caught fish before starting the lesson. */
    public static void onQuestClaimed(ServerPlayer player, Identifier questId) {
        TutorialStep step = getStep(player);
        if (TUTORIAL_QUEST_ID.equals(questId) && step.hasOverlay() && step != TutorialStep.SHOP_BROWSE
                && step != TutorialStep.FISHING_OFFER && step != TutorialStep.ENCYCLOPEDIA_OFFER && step != TutorialStep.ENCYCLOPEDIA_OPEN) {
            setStep(player, TutorialStep.SHOP_BROWSE);
        }
    }

    /** Called by TutorialAdvancePacket — client-initiated step advance. */
    public static void advanceStep(ServerPlayer player, TutorialStep fromStep) {
        advanceStep(player, fromStep, false);
    }

    public static void advanceStep(ServerPlayer player, TutorialStep fromStep, boolean showEncyclopedia) {
        if (getStep(player) != fromStep) return;
        if (fromStep == TutorialStep.FISHING_OFFER) {
            if (!showEncyclopedia) {
                setStep(player, TutorialStep.DEFERRED);
                return;
            }
            // Begin only after consent; a pre-existing cast cannot become a practice session.
            if (player.fishing != null) {
                player.fishing.discard();
                player.fishing = null;
            }
            giveStarterItems(player);
            boolean baited = false;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack rod = player.getInventory().getItem(slot);
                if (rod.is(FishtasticItems.COPPER_FISHING_ROD.value())
                        && !grill24.fishtastic.item.FishtasticFishingRodItem.getBait(rod).isEmpty()) baited = true;
            }
            setStep(player, baited ? TutorialStep.WAITING_FOR_CAST : TutorialStep.BAIT_LOAD);
            return;
        }

        TutorialStep next = switch (fromStep) {
            case BAIT_LOAD         -> TutorialStep.WAITING_FOR_CAST;
            case MINIGAME_INTRO    -> TutorialStep.MINIGAME_CONTROL;
            case MINIGAME_CONTROL  -> TutorialStep.MINIGAME_CATCH;
            case CATCH_RESULT      -> TutorialStep.QUEST_INTRO;
            case QUEST_INTRO       -> TutorialStep.QUEST_CLAIM;
            case SHOP_BROWSE       -> TutorialStep.ENCYCLOPEDIA_OFFER;
            case ENCYCLOPEDIA_OFFER -> showEncyclopedia ? TutorialStep.ENCYCLOPEDIA_OPEN : TutorialStep.COMPLETE;
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
        FishtasticItemData.set(fish, FishtasticDataComponents.FISH_QUALITY, new FishQuality(FishQuality.Quality.COMMON));
        FishtasticItemData.set(fish, FishtasticDataComponents.ITEM_SIZE, new ItemSize(12.0f));

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
