package grill24.fishtastic.block;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum WormBinPhase implements StringRepresentable {
    EMPTY("empty"), CONVERTING("converting"), READY("ready");

    private final String name;

    WormBinPhase(String name) {
        this.name = name;
    }

    @Override
    public @NotNull String getSerializedName() {
        return name;
    }
}
