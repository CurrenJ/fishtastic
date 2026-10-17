package grill24.fishtastic.gametest;

import grill24.fishtastic.server.FishCatchBackups;
import grill24.fishtastic.server.FishCatchSavedData;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Synchronous backup checks use small saved data and real files without touching other tests' records. */
public final class BackupTestFixture implements AutoCloseable {
    private final MinecraftServer server;
    private final FishCatchSavedData original;
    private final Path directory;
    private final Path parked;
    private final boolean hadDirectory;

    @FunctionalInterface
    public interface Check {
        void run() throws Exception;
    }

    private BackupTestFixture(GameTestHelper helper) throws IOException {
        server = helper.getLevel().getServer();
        original = FishCatchSavedData.getOrCreate(server);
        directory = FishCatchBackups.directory(server);
        Files.createDirectories(directory.getParent());
        parked = Files.createTempDirectory(directory.getParent(), "backup-fixture-");
        hadDirectory = Files.exists(directory);
        try {
            if (hadDirectory) Files.move(directory, parked.resolve("original"));
            Files.createDirectories(directory);
        } catch (IOException e) {
            if (hadDirectory && Files.exists(parked.resolve("original"))) {
                Files.move(parked.resolve("original"), directory);
            }
            Files.deleteIfExists(parked);
            throw e;
        }
        server.overworld().getDataStorage().set(FishCatchSavedData.FILE_ID, new FishCatchSavedData());
        FishCatchBackups.resetSchedule();
    }

    public static void run(GameTestHelper helper, Check check) throws Exception {
        // These checks complete synchronously: no other server tick observes the replacement.
        try (BackupTestFixture ignored = new BackupTestFixture(helper)) {
            check.run();
        }
    }

    /** Regression: both success and failure restore the original object and preserve unrelated files. */
    public static void restoresSharedStateAndFiles(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var original = FishCatchSavedData.getOrCreate(server);
        Path directory = FishCatchBackups.directory(server);
        Files.createDirectories(directory);
        Path sentinel = Files.createTempFile(directory, "fixture-sentinel-", ".txt");
        Files.writeString(sentinel, "preserve me");
        UUID player = UUID.randomUUID();
        try {
            for (boolean fail : new boolean[]{false, true}) {
                IOException expected = new IOException("Expected fixture failure");
                try {
                    run(helper, () -> {
                        var isolated = FishCatchSavedData.getOrCreate(server);
                        helper.assertTrue(isolated != original && !isolated.hasPlayer(player), "Fresh saved data installed");
                        helper.assertTrue(FishCatchBackups.list(server).isEmpty() && !Files.exists(sentinel), "Backup files isolated");
                        isolated.getOrCreateQuestState(player).setTokenBalance(42);
                        Files.writeString(directory.resolve("fixture-only.txt"), "discard me");
                        if (fail) throw expected;
                    });
                    helper.assertTrue(!fail, "Expected failure must propagate");
                } catch (IOException e) {
                    helper.assertTrue(fail && e == expected, "Original failure must propagate");
                }
                helper.assertTrue(FishCatchSavedData.getOrCreate(server) == original && !original.hasPlayer(player),
                        "Original saved data restored without fixture records");
                helper.assertTrue(Files.readString(sentinel).equals("preserve me")
                        && !Files.exists(directory.resolve("fixture-only.txt")), "Original files preserved and fixture files removed");
            }
        } finally {
            Files.delete(sentinel);
        }
        helper.succeed();
    }

    @Override
    public void close() throws IOException {
        server.overworld().getDataStorage().set(FishCatchSavedData.FILE_ID, original);
        FishCatchBackups.resetSchedule();
        try {
            // The fixture owns this flat directory. Do not recursively delete unexpected contents.
            try (var files = Files.newDirectoryStream(directory)) {
                for (Path file : files) Files.delete(file);
            }
            Files.delete(directory);
        } finally {
            if (hadDirectory) Files.move(parked.resolve("original"), directory);
            Files.delete(parked);
        }
    }
}
