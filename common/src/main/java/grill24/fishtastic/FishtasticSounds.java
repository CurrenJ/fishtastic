package grill24.fishtastic;

import grill24.fishtastic.architectury.RegistrationApiSided;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

/**
 * Holds references to custom SoundEvents registered by the mod.
 * Follows the same multiloader pattern as FishtasticItems and FishtasticBlocks.
 */
public class FishtasticSounds {
    /** Fallback notification sound for banners with no dedicated tier (out-of-bait, cleanup-goal milestone). */
    public static Holder<SoundEvent> QUEST_PROGRESS;
    public static Holder<SoundEvent> QUEST_PROGRESS_BRONZE;
    public static Holder<SoundEvent> QUEST_PROGRESS_SILVER;
    public static Holder<SoundEvent> QUEST_PROGRESS_GOLD;
    public static Holder<SoundEvent> QUEST_COMPLETE_BRONZE;
    public static Holder<SoundEvent> QUEST_COMPLETE_SILVER;
    public static Holder<SoundEvent> QUEST_COMPLETE_GOLD;
    public static Holder<SoundEvent> NEW_SPECIES_DISCOVERED;
    public static Holder<SoundEvent> CATCH_COMMON;
    public static Holder<SoundEvent> CATCH_UNCOMMON;
    public static Holder<SoundEvent> CATCH_RARE;
    public static Holder<SoundEvent> CATCH_EPIC;
    public static Holder<SoundEvent> CATCH_LEGENDARY;

    public static void registerSounds() {
        QUEST_PROGRESS = RegistrationApiSided.getInstance().registerSoundEvent("quest_progress");
        QUEST_PROGRESS_BRONZE = RegistrationApiSided.getInstance().registerSoundEvent("quest_progress_bronze");
        QUEST_PROGRESS_SILVER = RegistrationApiSided.getInstance().registerSoundEvent("quest_progress_silver");
        QUEST_PROGRESS_GOLD = RegistrationApiSided.getInstance().registerSoundEvent("quest_progress_gold");
        QUEST_COMPLETE_BRONZE = RegistrationApiSided.getInstance().registerSoundEvent("quest_complete_bronze");
        QUEST_COMPLETE_SILVER = RegistrationApiSided.getInstance().registerSoundEvent("quest_complete_silver");
        QUEST_COMPLETE_GOLD = RegistrationApiSided.getInstance().registerSoundEvent("quest_complete_gold");
        NEW_SPECIES_DISCOVERED = RegistrationApiSided.getInstance().registerSoundEvent("new_species_discovered");
        CATCH_COMMON = RegistrationApiSided.getInstance().registerSoundEvent("catch_common");
        CATCH_UNCOMMON = RegistrationApiSided.getInstance().registerSoundEvent("catch_uncommon");
        CATCH_RARE = RegistrationApiSided.getInstance().registerSoundEvent("catch_rare");
        CATCH_EPIC = RegistrationApiSided.getInstance().registerSoundEvent("catch_epic");
        CATCH_LEGENDARY = RegistrationApiSided.getInstance().registerSoundEvent("catch_legendary");
    }
}
