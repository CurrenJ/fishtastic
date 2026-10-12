package grill24.fishsim.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Per-tick group trace: -Ptrace=script -PtracePos=seed -PsweepClass=LINE|CROWD and either
 * -PsweepTicks=from (80 ticks from there) or no ticks (ticks where perched members share a slot,
 * and ticks where grounded members overlap deeply).
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class GroupTraceProbe {
    @Test
    void trace() {
        BenthicGroup.Formation f = BenthicGroup.Formation.valueOf(System.getProperty("sweepClass", "LINE"));
        float length = f == BenthicGroup.Formation.LINE ? 0.4f : 0.12f;
        int from = Integer.parseInt(System.getProperty("sweepTicks", "-1"));
        String script = System.getProperty("trace", "long_idle_then_walk");
        long seed = Long.parseLong(System.getProperty("tracePos", "1"));
        int[] printed = {0};
        new GroupRun(script, seed, length, Temperament.BASELINE, f).run((tick, world, g) -> {
            String why = "";
            if ("order".equals(System.getProperty("sweep")) && world.owner().horizontalSpeed() > 1) {
                Vec travel = world.owner().velocity().horizontal().normalize();
                for (int i = 1; i < g.count(); i++) {
                    CompanionAgent a = g.member(i), ahead = g.member(i - 1);
                    if (a.onGround() && ahead.onGround() && a.feetDown() && ahead.feetDown()
                            && a.position().sub(ahead.position()).horizontal().dot(travel) > 0.2)
                        why += " ORDER" + (i - 1) + i;
                }
            }
            for (int i = 0; i < g.count(); i++) {
                for (int j = i + 1; j < g.count(); j++) {
                    CompanionAgent a = g.member(i), b = g.member(j);
                    if (a.riding() && b.riding() && a.perchSlot() == b.perchSlot()) why += " SHARED" + i + j;
                    if (a.onGround() && b.onGround()) {
                        double sum = a.radius() + b.radius();
                        double depth = (sum - a.position().sub(b.position()).horizontalLength()) / sum;
                        if (depth > GroupRun.OVERLAP_DEPTH && Math.abs(a.position().y() - b.position().y()) < sum)
                            why += String.format(" OVERLAP%d%d=%.2f", i, j, depth);
                    }
                }
            }
            boolean window = from >= 0 ? tick >= from && tick < from + 80 : !why.isEmpty() && printed[0]++ < 60;
            if (!window) return;
            StringBuilder sb = new StringBuilder(String.format("t=%4d own %6.2f %6.2f %6.2f v%4.1f |", tick,
                    world.owner().feet().x(), world.owner().feet().y(), world.owner().feet().z(), world.owner().horizontalSpeed()));
            for (int i = 0; i < g.count(); i++) {
                CompanionAgent a = g.member(i);
                sb.append(String.format(" [%d %s %s %-13s %6.2f %6.2f %6.2f c%d d%d f%d]", i, a.perchSlot(),
                        a.riding() ? "R" : "-", a.landMode(), a.position().x(), a.position().y(), a.position().z(),
                        a.climbs(), a.dismounts(), a.groundFades()));
            }
            System.out.println(sb + why);
        });
    }
}
