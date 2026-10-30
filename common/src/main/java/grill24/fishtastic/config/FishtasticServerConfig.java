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
 * <p>Holds catch-data backup settings (see {@link grill24.fishtastic.server.FishCatchBackups}) and
 * the bounded opportunity/interaction tuning for authoritative fish schools.
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

            # ---- Interactive fish schools -----------------------------------------------
            # Shared, finite fish-school visits in validated water. Disabling this stops new
            # visits and lets existing free fish depart; minigames already in progress can finish.
            schools.enabled=true
            # Visit windows (docs/world-fish-exploration-design-spec.md, E1-E5). Every chunk with water
            # is a site. Each site opens a window every visits.periodTicks, at its own hashed offset,
            # and a window hosts a visit with probability visits.chance unless a higher-ranked window
            # within visits.suppressionRadiusChunks chunks overlaps it. Which windows host visits is a
            # fixed function of the world seed, chunk and time: arriving, leaving, reloading or
            # crossing chunk lines never creates or rerolls one. Only caught fish are saved.
            # Rate-tuning candidate after the owner liked the 5-minute duration: chance 0.024, with
            # T=7200, L=6000 and R=2 unchanged. Step 0 estimates about 2.7 starts/hour stationary.
            # A window stays open visits.lengthTicks (a species' world_encounter.lifetime_scale
            # multiplies it for the visit it hosts, capped at one period). Changing these changes
            # which windows host visits in an existing world.
            visits.periodTicks=7200
            visits.lengthTicks=6000
            visits.chance=0.024
            visits.suppressionRadiusChunks=2
            # Protection only (a window that would exceed them is skipped, and counted in
            # "/fishtastic visits windows"); in normal play they should not bind.
            schools.maxPerDimension=6
            schools.maxMembersPerDimension=24
            schools.maxMembersPerSchool=4
            schools.interestRadius=8
            schools.trackingRange=48
            schools.approachMinTicks=40
            schools.approachMaxTicks=600
            # How much faster than a calm visit a fish swims at a bobber it is interested in (1-4).
            schools.approachSpeedScale=2.5
            schools.biteWindowTicks=80
            schools.recoveryTicks=100
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

    public static boolean fishSchoolsEnabled() { return bool("schools.enabled", true); }
    /** Window length L, ticks: how long a hosted visit's window stays open (before the species' lifetime scale). */
    public static int visitLengthTicks() { return Math.max(600, Math.min(2_400_000, integer("visits.lengthTicks", 6000))); }
    /** Window period T, ticks; never shorter than {@link #visitLengthTicks()} (windows of one site must not overlap). */
    public static int visitPeriodTicks() {
        return Math.max(visitLengthTicks(), Math.min(2_400_000, integer("visits.periodTicks", 7200)));
    }
    public static double visitChance() { return Math.max(0.0, Math.min(1.0, decimal("visits.chance", 0.024))); }
    public static int visitSuppressionRadiusChunks() { return Math.max(0, Math.min(8, integer("visits.suppressionRadiusChunks", 2))); }

    /** The window model's numbers as configured: the schedule every site follows. */
    public static grill24.fishtastic.ecology.VisitWindows.Params visitWindowParams() {
        return new grill24.fishtastic.ecology.VisitWindows.Params(visitPeriodTicks(), visitLengthTicks(),
                visitChance(), visitSuppressionRadiusChunks());
    }
    public static int fishSchoolMaxPerDimension() { return Math.max(1, Math.min(16, integer("schools.maxPerDimension", 6))); }
    public static int fishSchoolMaxMembersPerDimension() { return Math.max(2, Math.min(64, integer("schools.maxMembersPerDimension", 24))); }
    public static int fishSchoolMaxMembersPerSchool() { return Math.max(2, Math.min(8, integer("schools.maxMembersPerSchool", 4))); }
    public static int fishSchoolInterestRadius() { return Math.max(2, Math.min(16, integer("schools.interestRadius", 8))); }
    public static int fishSchoolTrackingRange() { return Math.max(16, Math.min(96, integer("schools.trackingRange", 48))); }
    public static int fishSchoolApproachMinTicks() { return Math.max(20, Math.min(1200, integer("schools.approachMinTicks", 40))); }
    public static int fishSchoolApproachMaxTicks() { return Math.max(fishSchoolApproachMinTicks(), Math.min(2400, integer("schools.approachMaxTicks", 600))); }
    public static float fishSchoolApproachSpeedScale() {
        return (float) Math.max(1.0, Math.min(4.0, decimal("schools.approachSpeedScale", 2.5)));
    }
    public static int fishSchoolBiteWindowTicks() { return Math.max(20, Math.min(1200, integer("schools.biteWindowTicks", 80))); }
    public static int fishSchoolRecoveryTicks() { return Math.max(20, Math.min(2400, integer("schools.recoveryTicks", 100))); }

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

    private static double decimal(String key, double def) {
        String raw = props().getProperty(key);
        if (raw == null) return def;
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) ? value : def;
        } catch (NumberFormatException e) {
            Fishtastic.LOGGER.warn("Invalid decimal for {} in {}: '{}' - using default {}", key, FILE_NAME, raw, def);
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
