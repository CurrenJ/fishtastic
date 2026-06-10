package grill24.fishtastic;

import grill24.fishtastic.architectury.RegistrationApiSided;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

/**
 * Holds references to custom SoundEvents registered by the mod.
 * Follows the same multiloader pattern as FishtasticItems and FishtasticBlocks.
 */
public class FishtasticSounds {
    public static Holder<SoundEvent> QUEST_PROGRESS;
    public static Holder<SoundEvent> QUEST_COMPLETE;

    public static void registerSounds() {
        QUEST_PROGRESS = RegistrationApiSided.getInstance().registerSoundEvent("quest_progress");
        QUEST_COMPLETE = RegistrationApiSided.getInstance().registerSoundEvent("quest_complete");
    }
}
