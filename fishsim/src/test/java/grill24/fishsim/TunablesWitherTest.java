package grill24.fishsim;

import grill24.fishsim.core.Tunables;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the one property {@code Tunables}' single-field copies exist to have: a {@code withFoo}
 * must change {@code foo} and nothing else.
 *
 * <p>These used to be 24 hand-written 35-argument constructor calls, where a transposed pair would
 * compile, pass every behavioural test that did not happen to touch both fields, and silently
 * mistune the viewer's sliders. Reflection makes the check exhaustive and free — a wither added
 * later is covered without anyone remembering to cover it.
 */
class TunablesWitherTest {

    @TestFactory
    List<DynamicTest> everyWitherChangesExactlyTheComponentItNames() throws Exception {
        RecordComponent[] components = Tunables.class.getRecordComponents();
        List<DynamicTest> tests = new ArrayList<>();
        int found = 0;
        for (Method m : Tunables.class.getDeclaredMethods()) {
            if (!m.getName().startsWith("with") || m.getParameterCount() != 1
                    || m.getParameterTypes()[0] != float.class) {
                continue;
            }
            found++;
            String target = Character.toLowerCase(m.getName().charAt(4)) + m.getName().substring(5);
            tests.add(DynamicTest.dynamicTest(m.getName(), () -> {
                // A value no shipped tunable holds, so "changed" is unambiguous for every field.
                Tunables edited = (Tunables) m.invoke(Tunables.GROUP, -12345.75f);
                assertEquals(-12345.75f, componentValue(edited, target), 0f,
                        m.getName() + " did not write " + target);
                for (RecordComponent rc : components) {
                    if (rc.getName().equals(target)) continue;
                    Object before = rc.getAccessor().invoke(Tunables.GROUP);
                    Object after = rc.getAccessor().invoke(edited);
                    assertEquals(before, after, m.getName() + " also changed " + rc.getName());
                }
            }));
        }
        assertTrue(found >= 24, "expected the full wither table, found " + found);
        return tests;
    }

    private static float componentValue(Tunables t, String name) throws Exception {
        for (RecordComponent rc : Tunables.class.getRecordComponents()) {
            if (rc.getName().equals(name)) return (Float) rc.getAccessor().invoke(t);
        }
        throw new AssertionError("no record component named " + name);
    }
}
