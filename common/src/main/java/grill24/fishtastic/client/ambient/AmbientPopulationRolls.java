package grill24.fishtastic.client.ambient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Bounded per-slot choices keep brief re-entry and equipment toggles on the natural turnover clock. */
public final class AmbientPopulationRolls {
    public static final int MAX_SLOTS = AmbientBudget.MAX_INDIVIDUALS;
    private static final int MAX_CONTEXTS = 4;
    public record Choice(String species, long expiresTick) {}
    private final Map<Long, Choice[]> contexts = new LinkedHashMap<>();

    public Choice choose(int slot, long fingerprint, long tick, Predicate<String> eligible,
                         Supplier<Choice> draw) {
        Choice[] current = contexts.get(fingerprint);
        if (current != null && usable(current[slot], tick, eligible)) return current[slot];
        // Weight changes reuse an existing eligible choice through its original lifetime.
        for (Choice[] slots : contexts.values()) {
            if (usable(slots[slot], tick, eligible)) {
                return remember(slot, fingerprint, slots[slot]);
            }
        }
        return remember(slot, fingerprint, draw.get());
    }

    private static boolean usable(Choice choice, long tick, Predicate<String> eligible) {
        return choice != null && tick < choice.expiresTick() && eligible.test(choice.species());
    }

    private Choice remember(int slot, long fingerprint, Choice choice) {
        Choice[] slots = contexts.get(fingerprint);
        if (slots == null) {
            if (contexts.size() >= MAX_CONTEXTS) contexts.remove(contexts.keySet().iterator().next());
            slots = new Choice[MAX_SLOTS];
            contexts.put(fingerprint, slots);
        }
        slots[slot] = choice;
        return choice;
    }
}
