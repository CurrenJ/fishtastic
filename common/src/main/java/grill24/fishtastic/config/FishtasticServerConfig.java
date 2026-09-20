package grill24.fishtastic.config;

import dev.architectury.injectables.annotations.ExpectPlatform;
import grill24.fishtastic.Fishtastic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Server-side settings, stored as a plain properties file in the config directory - same
 * convention as {@link grill24.fishtastic.client.FishtasticClientConfig}. Read once on first
 * access and cached; edit the file and restart (or run {@code /fishtastic backup reload}) to
 * apply changes.
 *
 * <p>Currently holds the catch-data backup schedule and tiered retention (see
 * {@link grill24.fishtastic.server.FishCatchBackups}).
 */
public final class FishtasticServerConfig {
    private static final String FILE_NAME = "fishtastic-server.properties";
    private static final String DEFAULT_FILE_CONTENTS = """
            # Fishtastic server-side settings.
            # Edit and restart the server (or run "/fishtastic backup reload") to apply changes.

            # ---- Catch-data backups -------------------------------------------------------
            # Snapshots of the fish catch / quest saved data (<world>/data/fishtastic_fish_catches.dat)
            # are written to <world>/data/fishtastic_backups/. Manage them in-game with
            # "/fishtastic backup ...".

            # Master switch for automatic (interval, server-start, pre-command) backups.
            # Manual "/fishtastic backup create" always works.
            backups.enabled=true

            # Minutes of real time between automatic interval backups. A backup whose contents are
            # byte-identical to the newest existing one is skipped, so an idle server doesn't pile
            # up duplicates. 0 disables interval backups.
            backups.intervalMinutes=30

            # Tiered retention for interval backups - recent history is kept dense, older history
            # is thinned so a fixed number of files still reaches months back:
            #   younger than keepAllHours .......... every snapshot is kept
            #   up to keepSixHourlyDays days ....... one snapshot per 6 hours
            #   up to keepDailyDays days ........... one snapshot per day
            #   up to keepWeeklyDays days .......... one snapshot per week
            #   older than keepWeeklyDays .......... deleted
            # Manual backups and pre-restore safety backups are never pruned.
            backups.keepAllHours=24
            backups.keepSixHourlyDays=7
            backups.keepDailyDays=30
            backups.keepWeeklyDays=180

            # How many "pre-command" backups to keep (taken automatically right before destructive
            # admin commands such as simulatefishing or quests reset). Oldest are deleted first.
            backups.preCommandKeep=30
            """;

    private static volatile Properties cached;

    private FishtasticServerConfig() {}

    @ExpectPlatform
    public static Path getConfigDirectory() {
        throw new AssertionError();
    }

    public static boolean backupsEnabled() {
        return bool("backups.enabled", true);
    }

    public static int backupIntervalMinutes() {
        return Math.max(0, integer("backups.intervalMinutes", 30));
    }

    public static int backupKeepAllHours() {
        return Math.max(0, integer("backups.keepAllHours", 24));
    }

    public static int backupKeepSixHourlyDays() {
        return Math.max(0, integer("backups.keepSixHourlyDays", 7));
    }

    public static int backupKeepDailyDays() {
        return Math.max(0, integer("backups.keepDailyDays", 30));
    }

    public static int backupKeepWeeklyDays() {
        return Math.max(0, integer("backups.keepWeeklyDays", 180));
    }

    public static int backupPreCommandKeep() {
        return Math.max(0, integer("backups.preCommandKeep", 30));
    }

    /** Drops the cached values so the next read re-parses the file. */
    public static void reload() {
        cached = null;
    }

    private static boolean bool(String key, boolean def) {
        return Boolean.parseBoolean(props().getProperty(key, String.valueOf(def)));
    }

    private static int integer(String key, int def) {
        String raw = props().getProperty(key);
        if (raw == null) return def;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            Fishtastic.LOGGER.warn("Invalid integer for {} in {}: '{}' - using default {}", key, FILE_NAME, raw, def);
            return def;
        }
    }

    private static Properties props() {
        Properties p = cached;
        if (p == null) {
            p = load();
            cached = p;
        }
        return p;
    }

    private static Properties load() {
        Path file = getConfigDirectory().resolve(FILE_NAME);
        if (!Files.exists(file)) {
            try {
                Files.writeString(file, DEFAULT_FILE_CONTENTS);
            } catch (IOException ignored) {
                // Best-effort - if the config directory isn't writable, defaults apply.
            }
        }
        Properties props = new Properties();
        try (var in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            Fishtastic.LOGGER.warn("Could not read {} - using defaults", FILE_NAME, e);
        }
        return props;
    }
}
