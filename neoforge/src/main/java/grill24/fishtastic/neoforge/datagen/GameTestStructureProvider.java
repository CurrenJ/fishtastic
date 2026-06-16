package grill24.fishtastic.neoforge.datagen;

import com.google.common.hash.Hashing;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Generates minimal empty structure NBT files used by NeoForge game tests.
 * Output goes to src/main/generated/data/fishtastic/structure/.
 *
 * Run once: ./gradlew :neoforge:runData
 * Then commit the generated files alongside the test sources.
 */
public class GameTestStructureProvider implements DataProvider {

    private final PackOutput output;

    public GameTestStructureProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        // 7×7×7: enough room for fish tank adjacency tests (needs 3×3 connected area)
        futures.add(writeStructure(cachedOutput, "empty_testarea", 7, 7, 7));
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    /**
     * Writes a minimal empty structure (no blocks, no entities) of the given dimensions.
     * The GameTest framework only needs the size to determine the test bounds.
     */
    private CompletableFuture<?> writeStructure(CachedOutput output, String name, int x, int y, int z) {
        return CompletableFuture.runAsync(() -> {
            try {
                CompoundTag tag = buildEmptyStructureTag(x, y, z);
                byte[] bytes = toCompressedNbt(tag);
                Path path = this.output
                    .getOutputFolder(PackOutput.Target.DATA_PACK)
                    .resolve("fishtastic/structure/" + name + ".nbt");
                output.writeIfNeeded(path, bytes, Hashing.sha1().hashBytes(bytes));
            } catch (IOException e) {
                throw new RuntimeException("Failed to write game test structure " + name, e);
            }
        });
    }

    private static CompoundTag buildEmptyStructureTag(int sizeX, int sizeY, int sizeZ) {
        CompoundTag tag = new CompoundTag();

        // DataVersion — matches MC 26.1.2. Update if the world format version changes.
        tag.putInt("DataVersion", 4189);

        ListTag sizeList = new ListTag();
        sizeList.add(IntTag.valueOf(sizeX));
        sizeList.add(IntTag.valueOf(sizeY));
        sizeList.add(IntTag.valueOf(sizeZ));
        tag.put("size", sizeList);

        // Empty palette, blocks, entities — valid for a structure with no content.
        tag.put("palette", new ListTag());
        tag.put("blocks", new ListTag());
        tag.put("entities", new ListTag());

        return tag;
    }

    private static byte[] toCompressedNbt(CompoundTag tag) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        NbtIo.writeCompressed(tag, baos);
        return baos.toByteArray();
    }

    @Override
    public String getName() {
        return "Fishtastic Game Test Structures";
    }
}
