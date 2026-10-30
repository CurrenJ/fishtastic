package grill24.fishtastic.ecology;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The recent dimension weather, so revealing a visit later still uses the weather at its window's
 * start. This records world input, never visit state (the bounded exception to exploration E1).
 * The caller prunes to a period plus the longest visit lifetime, retaining the cutoff's anchor.
 */
public final class VisitWeatherLog {
    public enum State implements StringRepresentable {
        CLEAR, RAIN, THUNDER;

        public static final Codec<State> CODEC = StringRepresentable.fromEnum(State::values);

        public static State fromFlags(boolean raining, boolean thundering) {
            return thundering ? THUNDER : raining ? RAIN : CLEAR;
        }

        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public record Entry(long gameTime, State state) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("tick").forGetter(Entry::gameTime),
                State.CODEC.fieldOf("state").forGetter(Entry::state)
        ).apply(i, Entry::new));

        public Entry { Objects.requireNonNull(state); }
    }

    public record Sample(State state, boolean fallback) {}

    private final List<Entry> entries = new ArrayList<>();

    public VisitWeatherLog() {}

    public VisitWeatherLog(List<Entry> saved) {
        saved.stream().sorted(Comparator.comparingLong(Entry::gameTime))
                .forEach(entry -> record(entry.gameTime(), entry.state()));
    }

    /**
     * Appends a changed state; two samples in one tick keep its final state. If game time went back
     * (an older level.dat restored next to a newer log), the entries after it describe a future that
     * no longer happens and are dropped, so the tick carries on instead of failing every time.
     */
    public boolean record(long gameTime, State state) {
        Objects.requireNonNull(state);
        boolean replaced = false;
        while (!entries.isEmpty() && entries.get(entries.size() - 1).gameTime() > gameTime) {
            entries.remove(entries.size() - 1);
            replaced = true;
        }
        if (!entries.isEmpty()) {
            Entry last = entries.get(entries.size() - 1);
            if (last.state() == state) return replaced;
            if (gameTime == last.gameTime()) {
                entries.remove(entries.size() - 1);
                replaced = true;
            }
        }
        if (!entries.isEmpty() && entries.get(entries.size() - 1).state() == state) return replaced;
        entries.add(new Entry(gameTime, state));
        return true;
    }

    /** Last recorded state at or before the tick, or the explicitly marked current-weather fallback. */
    public Sample at(long gameTime, State current) {
        int anchor = anchor(gameTime);
        return anchor < 0 ? new Sample(current, true) : new Sample(entries.get(anchor).state(), false);
    }

    /** Removes obsolete entries, keeping the newest entry at or before the cutoff for lookups. */
    public int prune(long cutoff) {
        int anchor = anchor(cutoff);
        if (anchor <= 0) return 0;
        entries.subList(0, anchor).clear();
        return anchor;
    }

    public List<Entry> entries() { return List.copyOf(entries); }

    private int anchor(long gameTime) {
        int low = 0, high = entries.size() - 1, found = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (entries.get(mid).gameTime() <= gameTime) {
                found = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return found;
    }
}
