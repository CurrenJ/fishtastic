package grill24.fishtastic.client.encyclopedia.page;

/** The small pixel glyphs the info page draws (7x7 and smaller), taken from the approved mockups. */
public final class PageIcons {
    private PageIcons() {}

    public static final String[] SUN = {"..#.#..", "...#...", "#.###.#", ".#####.", "#.###.#", "...#...", "..#.#.."};
    public static final String[] DAWN = {".......", "...#...", ".#.#.#.", "..###..", ".#####.", "#######", "......."};
    public static final String[] DUSK = {".......", ".......", "..###..", ".#####.", "#######", ".......", "#.#.#.#"};
    public static final String[] NIGHT = {"..###..", ".##....", "##.....", "##.....", "##.....", ".##....", "..###.."};
    public static final String[] RAIN = {"..###..", ".#####.", "#######", ".......", "#.#.#..", ".#.#.#.", "#.#.#.."};
    public static final String[] SNOW = {"#..#..#", ".#.#.#.", "..###..", "#######", "..###..", ".#.#.#.", "#..#..#"};
    public static final String[] THUNDER = {"..###..", ".#####.", "#######", "...#...", "..#....", ".####..", "...#..."};
    public static final String[] LOCK = {".###.", "#...#", "#...#", "#####", "##.##", "##.##", "#####"};
    public static final String[] CHECK = {".......", "......#", ".....##", "#...##.", "##.##..", ".###...", "..#...."};
    public static final String[] HEART = {".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."};
    public static final String[] PIN = {"..#..", ".###.", "#####", ".###.", "..#.."};
    public static final String[] MARKER = {"#####", ".###.", "..#.."};

    public static final int SUN_RGB = 0xFFE14A, DAWN_RGB = 0xFFB060, DUSK_RGB = 0xFF8A50, NIGHT_RGB = 0xD8E0FF;
    public static final int RAIN_RGB = 0x7FB4FF, SNOW_RGB = 0xF2F6FF, THUNDER_RGB = 0xFFE14A;

    /** 7x7 moon, phase 0 = full ... 4 = new (vanilla's index order); '#' lit, 'o' the dark side. */
    public static String[] moon(int phase) {
        float lit = new float[]{1.0f, 0.75f, 0.5f, 0.25f, 0.0f, 0.25f, 0.5f, 0.75f}[phase];
        boolean waning = phase >= 1 && phase <= 3;
        String[] rows = new String[7];
        for (int y = 0; y < 7; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < 7; x++) {
                int dx = x - 3, dy = y - 3;
                if (dx * dx + dy * dy > 10) {
                    row.append('.');
                    continue;
                }
                float pos = (x + 0.5f) / 7.0f;
                boolean isLit = waning ? pos > (1 - lit) : pos < lit;
                row.append(isLit ? '#' : 'o');
            }
            rows[y] = row.toString();
        }
        return rows;
    }
}
