package grill24.fishtastic.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/** Self-test overrides change what the game reads, never what a real setter writes to disk (audit §7.2). */
class FishtasticClientConfigTest {

    @TempDir
    Path dir;

    @BeforeEach
    void useTempDirectory() {
        FishtasticClientConfig.testConfigDirectory = dir;
        FishtasticClientConfig.resetCacheForTest();
    }

    @AfterEach
    void restore() {
        FishtasticClientConfig.resetCacheForTest();
        FishtasticClientConfig.testConfigDirectory = null;
    }

    @Test
    void overridesApplyInMemoryButNeverReachTheFile() throws Exception {
        FishtasticClientConfig.setTestOverride(FishtasticClientConfig.AMBIENT_FISH_ENABLED_KEY, false);
        FishtasticClientConfig.setTestOverride(FishtasticClientConfig.HIDE_OTHER_COMPANIONS_KEY, true);
        FishtasticClientConfig.setTestOverride(FishtasticClientConfig.HIDE_OWN_COMPANION_FIRST_PERSON_KEY, true);
        FishtasticClientConfig.setAmbientDensityTestOverride(5);
        assertFalse(FishtasticClientConfig.isAmbientFishEnabled());
        assertTrue(FishtasticClientConfig.hideOtherCompanions());
        assertTrue(FishtasticClientConfig.hideOwnCompanionFirstPerson());
        assertEquals(5, FishtasticClientConfig.getAmbientFishDensity());

        // A real setter for an unrelated setting rewrites the whole file.
        FishtasticClientConfig.setTankInteriorLight(7);

        Properties onDisk = new Properties();
        try (var in = Files.newInputStream(dir.resolve("fishtastic-client.properties"))) {
            onDisk.load(in);
        }
        assertEquals("7", onDisk.getProperty("tankInteriorLight"));
        assertEquals("true", onDisk.getProperty(FishtasticClientConfig.AMBIENT_FISH_ENABLED_KEY));
        assertEquals("50", onDisk.getProperty("ambientFishDensity"));
        assertEquals("false", onDisk.getProperty(FishtasticClientConfig.HIDE_OTHER_COMPANIONS_KEY));
        assertEquals("false", onDisk.getProperty(FishtasticClientConfig.HIDE_OWN_COMPANION_FIRST_PERSON_KEY));

        // Reloaded with the overrides cleared, the pre-override values come back.
        FishtasticClientConfig.resetCacheForTest();
        assertTrue(FishtasticClientConfig.isAmbientFishEnabled());
        assertEquals(50, FishtasticClientConfig.getAmbientFishDensity());
        assertFalse(FishtasticClientConfig.hideOtherCompanions());
        assertFalse(FishtasticClientConfig.hideOwnCompanionFirstPerson());
        assertEquals(7, FishtasticClientConfig.getTankInteriorLight());
    }
}
