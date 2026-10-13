package grill24.fishtastic.client.ambient;

import grill24.fishsim.ambient.WaterDiscovery;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Distance is to actual cached water. Nearby occupied patches keep their display slots. */
public final class AmbientPatchSelection {
    private AmbientPatchSelection() {}
    public record Selection(List<WaterDiscovery.Patch> patches, Map<Long, Integer> distances) {}

    public static Selection select(List<WaterDiscovery.Patch> patches, List<WaterDiscovery.Column> columns,
                                   Set<Long> occupied, int x, int z, int limit) {
        Map<Long, Integer> distances = new HashMap<>();
        for (var column : columns) {
            int dx = column.x() - x, dz = column.z() - z;
            distances.merge(column.patchKey(), dx * dx + dz * dz, Math::min);
        }
        List<WaterDiscovery.Patch> ranked = new ArrayList<>(patches);
        ranked.sort(Comparator.<WaterDiscovery.Patch>comparingInt(p ->
                        occupied.contains(p.key()) && distances.getOrDefault(p.key(), Integer.MAX_VALUE) <= 100 ? 0 : 1)
                .thenComparingInt(p -> distances.getOrDefault(p.key(), Integer.MAX_VALUE))
                .thenComparing(Comparator.comparingInt(WaterDiscovery.Patch::columns).reversed())
                .thenComparingLong(WaterDiscovery.Patch::createdTick));
        if (ranked.size() > limit) ranked.subList(limit, ranked.size()).clear();
        return new Selection(ranked, distances);
    }
}
