package grill24.fishtastic.gametest;

/** Optional name filter for running one shared GameTest from a loader's dev run task. */
public final class GameTestSelection {
    public static final String FILTER_PROPERTY = "fishtastic.gametest.filter";

    private GameTestSelection() {}

    public static boolean hasFilter() {
        String filter = System.getProperty(FILTER_PROPERTY);
        return filter != null && !filter.isBlank();
    }

    public static boolean matches(String testName) {
        String filter = System.getProperty(FILTER_PROPERTY);
        return filter == null || filter.isBlank()
                || normalize(testName).contains(normalize(filter));
    }

    private static String normalize(String value) {
        return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(java.util.Locale.ROOT);
    }
}
