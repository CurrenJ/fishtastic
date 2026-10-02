package grill24.fishtastic.client.util;

import grill24.fishsim.domain.Shelter;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.ShippedStructures;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Not a test: writes every shipped structure's obstacle boxes, placed as
 * {@link ObstacleInvariantTest} places it, for the fishsim harness's {@code --obstacles} (which
 * cannot read a structure itself). Floor structures land in the middle tank of a 3×1×1, as
 * {@code <name>-3x1x1.txt}; spanning ones fill their own box, as {@code <name>-<box>.txt} — the
 * Whale Fall's matches {@code --domain 4x2x2+skull}. Runs only when asked:
 *
 * <pre>./gradlew :common:test --tests '*ObstacleExport*' -PexportObstacles=build/obstacles
 * ./gradlew :fishsim:runHeadless -PsimArgs="--domain 4x2x2+skull --obstacles ../build/obstacles/whale_fall-4x2x2.txt --cast loaches"</pre>
 */
@EnabledIfSystemProperty(named = "exportObstacles", matches = ".+")
class ObstacleExport {

    @Test
    void export() throws IOException {
        Path dir = Path.of(System.getProperty("exportObstacles"));
        Files.createDirectories(dir);
        for (Map.Entry<String, CosmeticStructure> e : ShippedStructures.all().entrySet()) {
            CosmeticStructure structure = e.getValue();
            String domain = structure.span().map(s -> s.x() + "x" + s.y() + "x" + s.z()).orElse("3x1x1");
            List<String> lines = new ArrayList<>();
            lines.add("# " + e.getKey() + " in " + domain + ", unturned: minL minY minD maxL maxY maxD");
            for (Shelter.OrientedBox b : ObstacleInvariantTest.placeObstacles(structure, domain, Rotation.NONE)) {
                lines.add(String.format(Locale.ROOT, "%.5f %.5f %.5f %.5f %.5f %.5f",
                        b.centerL() - b.halfL(), b.centerY() - b.halfY(), b.centerD() - b.halfD(),
                        b.centerL() + b.halfL(), b.centerY() + b.halfY(), b.centerD() + b.halfD()));
            }
            Files.write(dir.resolve(e.getKey() + "-" + domain + ".txt"), lines);
        }
    }
}
