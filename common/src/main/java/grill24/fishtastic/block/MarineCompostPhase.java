package grill24.fishtastic.block;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum MarineCompostPhase implements StringRepresentable {
    DRY("dry"), WET("wet"), READY("ready");

    private final String name;

    MarineCompostPhase(String name) {
        this.name = name;
    }

    @Override
    public @NotNull String getSerializedName() {
        return name;
    }
}
