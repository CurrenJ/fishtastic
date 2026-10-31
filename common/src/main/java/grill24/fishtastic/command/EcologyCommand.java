package grill24.fishtastic.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import grill24.fishtastic.config.FishtasticServerConfig;
import grill24.fishtastic.ecology.EcologyHash;
import grill24.fishtastic.ecology.EcologySeed;
import grill24.fishtastic.ecology.NaturalPool;
import grill24.fishtastic.ecology.VisitWindows;
import grill24.fishtastic.server.EcologySeedSync;
import grill24.fishtastic.server.FishingPoolResolver;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

/**
 * Debug view of the world fish ecology core (docs/world-fish-exploration-implementation-plan.md,
 * Step 1): this dimension's seed fingerprint (never the world seed), the current window, the
 * activity hash, rank, active and suppressed flags for the invoker's chunk and its neighbours,
 * and the natural pool at the invoker's position.
 *
 * Usage: /fishtastic ecology
 */
public class EcologyCommand {

    private static final int TOP_SPECIES = 8;

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("ecology")
                .requires(FishtasticPermissions.gamemaster())
                .executes(EcologyCommand::execute);
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        ChunkPos chunk = new ChunkPos(pos);

        long seed = EcologySeedSync.seedFor(level);
        VisitWindows.Params params = FishtasticServerConfig.visitWindowParams();
        long now = level.getGameTime();
        long phase = VisitWindows.phase(seed, chunk.x, chunk.z, params);
        long window = VisitWindows.windowIndex(seed, chunk.x, chunk.z, now, params);
        long activity = VisitWindows.activityHash(seed, chunk.x, chunk.z, window);
        boolean active = VisitWindows.isActive(seed, chunk.x, chunk.z, window, params);
        boolean suppressed = VisitWindows.isSuppressed(seed, chunk.x, chunk.z, window, params);
        int activeNearby = 0;
        for (int dx = -params.suppressionRadiusChunks(); dx <= params.suppressionRadiusChunks(); dx++) {
            for (int dz = -params.suppressionRadiusChunks(); dz <= params.suppressionRadiusChunks(); dz++) {
                int nx = chunk.x + dx;
                int nz = chunk.z + dz;
                if (VisitWindows.isActive(seed, nx, nz, VisitWindows.windowIndex(seed, nx, nz, now, params), params)) {
                    activeNearby++;
                }
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("Ecology ").append(level.dimension().location())
                .append(" seed#").append(EcologySeed.fingerprint(seed))
                .append(" (T=").append(params.periodTicks()).append(" L=").append(params.visitTicks())
                .append(" chance=").append(params.visitChance()).append(" R=").append(params.suppressionRadiusChunks()).append(')')
                .append("\nWindow ").append(window).append(" phase=").append(phase).append(" [")
                .append(VisitWindows.windowStart(seed, chunk.x, chunk.z, window, params))
                .append(", ").append(VisitWindows.windowEnd(seed, chunk.x, chunk.z, window, params)).append(") open=")
                .append(VisitWindows.isOpenAt(seed, chunk.x, chunk.z, now, window, params))
                .append("\nChunk ").append(chunk.x).append(',').append(chunk.z)
                .append(" activity=").append(String.format("%016x", activity))
                .append(" (").append(String.format("%.5f", EcologyHash.toUnitDouble(activity))).append(')')
                .append(" active=").append(active)
                .append(" rank=").append(String.format("%016x", VisitWindows.rank(seed, chunk.x, chunk.z, window)))
                .append(" suppressed=").append(suppressed)
                .append(" hostsVisit=").append(active && !suppressed)
                .append(" activeInRadiusNow=").append(activeNearby);

        FishingPoolResolver.SpeciesContext pool = NaturalPool.resolveAt(level, pos);
        FishingPoolResolver.EnvironmentContext env = pool.environment();
        int total = pool.candidates().stream().mapToInt(FishingPoolResolver.WeightedFish::weight).sum();
        out.append("\nNatural pool at ").append(pos.toShortString()).append(": ")
                .append(pool.candidates().size()).append(" species, zones=").append(env.zones())
                .append(" time=").append(env.timeOfDay()).append(" weather=").append(env.weather())
                .append(" moon=").append(env.moonPhase()).append(" fingerprint=").append(Long.toHexString(pool.fingerprint()));
        List<FishingPoolResolver.WeightedFish> top = FishingPoolResolver.topWeighted(pool, TOP_SPECIES);
        for (FishingPoolResolver.WeightedFish fish : top) {
            out.append("\n  ").append(fish.item().unwrapKey().map(key -> key.location().toString()).orElse("?"))
                    .append(" w=").append(fish.weight())
                    .append(String.format(" (%.1f%%)", total == 0 ? 0.0 : 100.0 * fish.weight() / total));
        }

        source.sendSuccess(() -> Component.literal(out.toString()), false);
        return 1;
    }
}
