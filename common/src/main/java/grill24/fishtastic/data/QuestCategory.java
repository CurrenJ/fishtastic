package grill24.fishtastic.data;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum QuestCategory implements StringRepresentable {
    DAILY, MASTERY, EXPLORER, CHALLENGE;

    public static final Codec<QuestCategory> CODEC = StringRepresentable.fromEnum(QuestCategory::values);

    @Override
    public @NotNull String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
