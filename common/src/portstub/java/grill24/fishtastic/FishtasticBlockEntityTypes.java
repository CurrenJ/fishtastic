package grill24.fishtastic;

import net.minecraft.core.Holder;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Portstub (gradle/port-excludes.gradle): temporary stand-in for the real
 * {@code FishtasticBlockEntityTypes}, which registers every block entity type (fish tank, fish
 * tank assembly, marine compost, fish pile, electric fish organizer) and so pulls in all of their
 * classes plus {@code FishtasticBlocks}' full registration graph. Only {@code FISH_TANK} is kept,
 * for {@link grill24.fishtastic.blockentity.FishTankBlockEntity}'s constructor. Real file still
 * exists (excluded) at FishtasticBlockEntityTypes.java, delete this stub once that graph is ported
 * for real.
 */
public class FishtasticBlockEntityTypes {
    public static Holder<BlockEntityType<?>> FISH_TANK;
}
