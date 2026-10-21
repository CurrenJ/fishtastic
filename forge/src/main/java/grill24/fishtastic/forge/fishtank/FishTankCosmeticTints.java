package grill24.fishtastic.forge.fishtank;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Routes cosmetic quads to their original block's tint sources through the tank's tint list. */
public final class FishTankCosmeticTints {
    private record Key(BlockState state, int tint) {}
    private static final Map<Key, Integer> INDICES = new HashMap<>();
    // BlockColors retains this live list. Copy-on-write keeps concurrent chunk meshes/readers safe.
    private static final List<BlockTintSource> SOURCES = new CopyOnWriteArrayList<>();

    private FishTankCosmeticTints() {}

    public static List<BlockTintSource> sources() {
        return SOURCES;
    }

    public static synchronized int index(BlockState state, int tint) {
        if (tint < 0) return -1;
        return INDICES.computeIfAbsent(new Key(state, tint), key -> {
            int index = SOURCES.size();
            SOURCES.add(new BlockTintSource() {
                private BlockTintSource original() {
                    return Minecraft.getInstance().getBlockColors().getTintSource(key.state(), key.tint());
                }

                @Override
                public int color(BlockState ignored) {
                    BlockTintSource source = original();
                    return source == null ? -1 : source.color(key.state());
                }

                @Override
                public int colorInWorld(BlockState ignored, BlockAndTintGetter level, BlockPos pos) {
                    BlockTintSource source = original();
                    return source == null ? -1 : source.colorInWorld(key.state(), level, pos);
                }
            });
            return index;
        });
    }
}
