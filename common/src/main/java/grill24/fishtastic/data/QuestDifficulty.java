package grill24.fishtastic.data;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum QuestDifficulty implements StringRepresentable {
    BRONZE, SILVER, GOLD;

    public static final Codec<QuestDifficulty> CODEC = StringRepresentable.fromEnum(QuestDifficulty::values);

    @Override
    public @NotNull String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
