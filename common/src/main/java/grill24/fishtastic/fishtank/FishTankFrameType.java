package grill24.fishtastic.fishtank;

import grill24.fishtastic.architectury.RegistrationApiSided;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.Objects;

public record FishTankFrameType(TagKey<Block> blockTag) {
    public static final FishTankFrameType DEFAULT = new FishTankFrameType(TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("oak")));

    public FishTankFrameType {
        Objects.requireNonNull(blockTag);
    }

    /**
     * Returns the fish tank frame type's registry name.
     */
    public Identifier getRegistryName() {
        return RegistrationApiSided.getInstance().fishTankFrameTypes().getKey(this);
    }

    @Override
    public String toString() {
        return getRegistryName().toString();
    }
}
