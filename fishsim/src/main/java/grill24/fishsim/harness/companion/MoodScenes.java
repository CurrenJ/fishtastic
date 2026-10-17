package grill24.fishsim.harness.companion;

import grill24.fishsim.companion.MoodPresets;
import grill24.fishsim.companion.Vec;

/** Applies the shipped {@link MoodPresets} to headless runs and the viewer. */
public final class MoodScenes {

    private MoodScenes() {}

    /** Sets a run's companion feeling {@code preset} from now on. */
    public static void apply(CompanionRun run, MoodPresets.Preset preset, Vec startFeet) {
        run.agent.habitat(preset.habitat());
        run.script.ambient = preset.ambientAt(startFeet);
    }
}
