package grill24.fishtastic.server;

import grill24.fishtastic.server.FishCatchBackups.Entry;
import grill24.fishtastic.server.FishCatchBackups.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** File-name grammar, label sanitising and same-second collision handling in {@link FishCatchBackups}. */
class FishCatchBackupsNamingTest {

    @Test
    void parsesEveryKindToken() {
        assertKind("20260920-143012-auto.dat", Kind.INTERVAL, null);
        assertKind("20260920-143012-start.dat", Kind.START, null);
        assertKind("20260920-143012-manual.dat", Kind.MANUAL, null);
        assertKind("20260920-143012-prerestore.dat", Kind.PRE_RESTORE, null);
        assertKind("20260920-143012-pre-simulatefishing.dat", Kind.PRE_COMMAND, "simulatefishing");
        assertKind("20260920-143012-manual-before_cleanup.v2.dat", Kind.MANUAL, "before_cleanup.v2");
    }

    @Test
    void parsedTimestampIsExact() {
        Entry e = FishCatchBackups.parse(Path.of("20260920-143012-auto.dat")).orElseThrow();
        assertEquals(LocalDateTime.of(2026, 9, 20, 14, 30, 12), e.time());
        assertEquals("20260920-143012-auto.dat", e.fileName());
    }

    @Test
    void rejectsForeignAndMalformedNames() {
        assertTrue(FishCatchBackups.parse(Path.of("notes.txt")).isEmpty());
        assertTrue(FishCatchBackups.parse(Path.of("20260920-143012-auto.dat.tmp")).isEmpty(), "in-flight temp file");
        assertTrue(FishCatchBackups.parse(Path.of("20260920-143012-bogus.dat")).isEmpty(), "unknown kind");
        assertTrue(FishCatchBackups.parse(Path.of("2026-09-20-auto.dat")).isEmpty(), "wrong stamp shape");
        assertTrue(FishCatchBackups.parse(Path.of("20260920-143012-auto-bad label.dat")).isEmpty(), "space in label");
        assertTrue(FishCatchBackups.parse(Path.of("20261399-143012-auto.dat")).isEmpty(), "impossible date");
    }

    @Test
    void labelSanitising() {
        assertNull(FishCatchBackups.sanitizeLabel(null));
        assertNull(FishCatchBackups.sanitizeLabel("   "));
        assertNull(FishCatchBackups.sanitizeLabel("!!!"));
        assertEquals("before_cleanup", FishCatchBackups.sanitizeLabel("before cleanup"));
        assertEquals("a_b", FishCatchBackups.sanitizeLabel("  a / b  "));
        assertEquals("x.y", FishCatchBackups.sanitizeLabel("x.y"));
        assertEquals("evil_.._up", FishCatchBackups.sanitizeLabel("evil/../up"));
        String longLabel = "x".repeat(100);
        assertEquals(40, FishCatchBackups.sanitizeLabel(longLabel).length());
        // Whatever comes out must round-trip through the file-name grammar.
        String clean = FishCatchBackups.sanitizeLabel("we!rd label/../x");
        assertTrue(FishCatchBackups.parse(Path.of("20260920-143012-manual-" + clean + ".dat")).isPresent(), clean);
    }

    @Test
    void uniquePathBumpsSecondsOnCollision(@TempDir Path dir) throws Exception {
        LocalDateTime t = LocalDateTime.of(2026, 9, 20, 14, 30, 12);
        Path first = FishCatchBackups.uniquePath(dir, t, Kind.MANUAL, "x");
        assertEquals("20260920-143012-manual-x.dat", first.getFileName().toString());
        Files.createFile(first);

        Path second = FishCatchBackups.uniquePath(dir, t, Kind.MANUAL, "x");
        assertEquals("20260920-143013-manual-x.dat", second.getFileName().toString());
        Files.createFile(second);

        Path third = FishCatchBackups.uniquePath(dir, t, Kind.MANUAL, "x");
        assertEquals("20260920-143014-manual-x.dat", third.getFileName().toString());

        // Different kind or label in the same second doesn't collide.
        assertEquals("20260920-143012-pre-x.dat",
                FishCatchBackups.uniquePath(dir, t, Kind.PRE_COMMAND, "x").getFileName().toString());
        assertEquals("20260920-143012-manual.dat",
                FishCatchBackups.uniquePath(dir, t, Kind.MANUAL, null).getFileName().toString());
    }

    @Test
    void entryAgeFormatting() {
        LocalDateTime now = LocalDateTime.now();
        assertTrue(entry(now.minusMinutes(5)).age().matches("\\d+m"));
        assertTrue(entry(now.minusHours(3).minusMinutes(2)).age().matches("3h \\d+m"));
        assertTrue(entry(now.minusDays(2).minusHours(4)).age().matches("2d \\d+h"));
    }

    private static Entry entry(LocalDateTime t) {
        return new Entry(Path.of("x.dat"), t, Kind.INTERVAL, null);
    }

    private static void assertKind(String name, Kind kind, String label) {
        Optional<Entry> e = FishCatchBackups.parse(Path.of(name));
        assertTrue(e.isPresent(), name);
        assertEquals(kind, e.get().kind(), name);
        assertEquals(label, e.get().label(), name);
    }
}
