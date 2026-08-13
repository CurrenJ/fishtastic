package grill24.fishtastic.shapegen;

import java.util.EnumSet;
import java.util.Set;

/**
 * Plain (Minecraft-independent) mirror of {@code net.minecraft.core.Direction}'s ordinal
 * ordering — DOWN, UP, NORTH, SOUTH, WEST, EAST — so permutation bitmasks (bit = ordinal) match
 * exactly between this library and the mod's {@code Direction}-based code
 * ({@code FishTankCompositeModelData#getPermutationIndex}).
 */
public enum TankFace {
    DOWN, UP, NORTH, SOUTH, WEST, EAST;

    public static Set<TankFace> fromPermutationIndex(int index) {
        Set<TankFace> faces = EnumSet.noneOf(TankFace.class);
        for (TankFace face : values()) {
            if ((index & (1 << face.ordinal())) != 0) {
                faces.add(face);
            }
        }
        return faces;
    }
}
