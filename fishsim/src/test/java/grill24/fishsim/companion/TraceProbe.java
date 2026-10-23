package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import grill24.fishsim.harness.companion.MoodScenes;
import grill24.fishsim.core.Locomotion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Diagnostic: a tick-by-tick trace of one run in the owner's frame (ahead, right, up of the fish
 * relative to the eye), with goal and visibility. Select other diagnostics with -PtraceMode. -PsimStdout -Ptrace=script:seed:from:to:every
 * [-PsweepClass=GLIDE -PtweakA=field=value,...].
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class TraceProbe {

    @Test
    void trace() {
        switch (System.getProperty("traceMode", "motion")) {
            case "motion" -> traceMotion();
            case "drift" -> traceDrift();
            case "stroke" -> traceStroke();
            case "warmth" -> traceWarmth();
            case "wariness" -> traceWariness();
            default -> throw new IllegalArgumentException("traceMode must be motion, drift, stroke, warmth or wariness");
        }
    }



    private void traceMotion() {
        String[] a = System.getProperty("trace", "about_face:1:0:600:5").split(":");
        CompanionTunables tun = CompanionTunables.defaults();
        SeedSweepProbe.tweak(System.getProperty("tweakA")).accept(tun);
        var c = grill24.fishsim.core.Locomotion.valueOf(System.getProperty("sweepClass", "FREE_SWIM"));
        CompanionRun run = new CompanionRun(a[0], Long.parseLong(a[1]), c, SeedSweepProbe.temperament(), tun, true);
        int from = Integer.parseInt(a[2]), to = Integer.parseInt(a[3]), every = Integer.parseInt(a[4]);
        for (int t = 0; t < to; t++) {
            run.tick();
            if (t < from || t % every != 0) continue;
            OwnerState o = run.owner;
            Vec d = run.agent.position().sub(o.eye());
            Vec f = o.flatForward(), r = o.flatRight();
            System.out.printf("t=%4d ownerV=%5.2f yaw=%6.1f %s fish ahead=%5.2f right=%5.2f up=%5.2f  v=%4.2f %-8s %s ang=%3.0f%n",
                    t, o.horizontalSpeed(), o.yawDeg(), run.agent.backstopLastStep() ? "HIT" : "   ", d.dot(f), d.dot(r), d.y(), run.agent.velocity().length(),
                    run.agent.goal() + (run.agent.perchState() != null ? "/" + run.agent.perchState() : ""), run.agent.seen() ? "SEEN" : "    ", Visibility.angleFromCenterDeg(o, run.agent.position()));
            if (System.getProperty("tracePos") != null && run.agent.locomotion() == grill24.fishsim.core.Locomotion.DRIFT)
                System.out.printf("    drift |v|=%.2f vy=%.2f tilt=%.0f pulse=%.2f thrust=%.1f effort=%.2f%n", run.agent.velocity().length(), run.agent.velocity().y(), run.agent.pitch() + 90, run.agent.pulse(), run.agent.thrust(), (run.agent.speedFactor() - 0.6f) / 0.9f);
            if (System.getProperty("tracePos") != null) System.out.printf("    pos=%s yaw=%.1f pitch=%.1f target=%s%n", run.agent.position(), run.agent.yaw(), run.agent.pitch(), run.agent.target());
        }
    }

    // DriftTraceProbe diagnostic, selected through -PtraceMode.
    private void traceDrift() {
        String script = System.getProperty("trace", "walk");
        DriftStyle style = DriftStyle.named(System.getProperty("driftStyle", "bell"));
        long seed = Long.parseLong(System.getProperty("sweepSeeds", "1"));
        int ticks = Integer.parseInt(System.getProperty("sweepTicks", "600"));
        int every = Integer.parseInt(System.getProperty("traceEvery", "5"));
        CompanionTunables t = CompanionTunables.defaults();
        SeedSweepProbe.tweak(System.getProperty("tweakA")).accept(t);
        CompanionRun run = CompanionRun.drifter(script, seed, style, SeedSweepProbe.temperament(), t, true);
        run.run(ticks);
        System.out.println("== DRIFT trace " + style + " " + script + " seed " + seed);
        System.out.println("  tick goal       dist  along  right    up |  spd  ask  own | askF  askR  velF  velR | tilt  thr  flare seen bs impact in");
        for (int k = 0; k < run.samples.size(); k += every) {
            var s = run.samples.get(k);
            var o = s.owner();
            Vec f = o.velocity().horizontalLength() > 0.5 ? o.velocity().horizontal().normalize() : o.flatForward();
            Vec r = f.cross(Vec.UP);
            Vec rel = s.pos().sub(o.chest());
            System.out.printf("  %4d %-10s %5.2f %6.2f %6.2f %5.2f | %4.1f %4.1f %4.1f | %5.1f %5.1f %5.1f %5.1f | %4.0f %4.1f %5.2f %s %s %.2f %s%n",
                    k, s.goal(), s.ownerDist(), rel.dot(f), rel.dot(r), rel.y(),
                    s.vel().length(), s.asked().length(), o.velocity().length(),
                    s.asked().dot(f), s.asked().dot(r), s.vel().dot(f), s.vel().dot(r),
                    s.tilt(), s.thrust(), s.flare(), s.seen() ? "S" : ".", s.backstop() ? "B" : "", s.impact(),
                    CompanionRun.insideBody(o, s.pos()) ? "IN" : (s.pos().distanceTo(o.camera()) < 0.3 + run.agent.radius() ? "CLIP" : ""));
        }
    }

    // DriftTickTraceProbe diagnostic, selected through -PtraceMode.
    private void traceStroke() {
        String script = System.getProperty("trace", "idle_fixed");
        long seed = Long.parseLong(System.getProperty("sweepSeeds", "1"));
        String[] w = System.getProperty("tracePos", "940,990").split(",");
        int from = Integer.parseInt(w[0]), to = Integer.parseInt(w[1]);
        CompanionTunables t = CompanionTunables.defaults();
        SeedSweepProbe.tweak(System.getProperty("tweakA")).accept(t);
        CompanionRun run = CompanionRun.drifter(script, seed, DriftStyle.BELL, SeedSweepProbe.temperament(), t, false);
        DriftBody b = (DriftBody) DriftStrokeVisibilityProbe.bodyOf(run.agent);
        Vec lastPos = b.pos;
        System.out.println("  tick idle pulse  thr  |v|    vx    vy    vz  |step|/DT flare tilt calm goal back");
        for (int k = 0; k <= to; k++) {
            run.tick();
            boolean onlyHits = Boolean.parseBoolean(System.getProperty("sweepPerSeed", "false"));
            if (k >= from && (!onlyHits || b.backstopLastStep)) System.out.printf("  %4d %s %5.2f %5.2f %4.2f %5.2f %5.2f %5.2f %5.2f %4.2f %4.0f %4.2f %s %s%s%n", k, b.idling ? "I" : "-", b.pulse, b.lastThrust,
                    b.vel.length(), b.vel.x(), b.vel.y(), b.vel.z(), b.pos.sub(lastPos).length() * 20, b.flare, b.tilt, b.calm, run.agent.goal(),
                    b.backstopLastStep ? "B" : "", (b.strokeStart ? " STROKE" : "") + String.format(" needUp %.0f° ask %.2f y %.2f", Math.toDegrees(Math.atan2(b.need.y(), b.need.horizontalLength())), b.asked.length(), b.pos.y()));
            lastPos = b.pos;
        }
    }

    // WarmthTraceProbe diagnostic, selected through -PtraceMode.
    private void traceWarmth() {
        CompanionRun run = new CompanionRun("idle_fixed", 3, Locomotion.FREE_SWIM, CompanionTunables.defaults(), true);
        MoodScenes.apply(run, MoodPresets.named("warm"), run.owner.feet());
        Vec fire = run.script.ambient.warmth().get(0);
        for (int i = 0; i < 2400; i++) {
            run.run(1);
            if (i % 20 != 0) continue;
            CompanionAgent a = run.agent;
            Drives d = a.drivesForTest();
            System.out.printf("WT %5.1f goal=%-8s %s cur=%.2f tgt=%s fireD=%.2f rest=%.2f%n", i / 20.0, a.goal(),
                    a.moodDescription(), d.curiosity, d.target == null ? "-" : String.valueOf(d.target.kind),
                    a.position().distanceTo(fire), d.rest);
        }
    }

    // WarinessTraceProbe diagnostic, selected through -PtraceMode.
    private void traceWariness() {
        String[] a = System.getProperty("trace", "hostile_approach:1").split(":");
        CompanionRun run = new CompanionRun(a[0], Long.parseLong(a[1]), CompanionTunables.defaults(), true);
        int cover = 0;
        for (int t = 0; t < 2400; t++) {
            run.tick();
            CompanionAgent ag = run.agent;
            boolean hiding = ag.goal().equals("HIDE");
            cover = hiding ? cover + 1 : 0;
            if (cover <= 30 || ag.threat() == null) continue;
            Vec chest = run.owner.chest();
            double df = ag.position().distanceTo(ag.threat()), dc = chest.distanceTo(ag.threat());
            if (df >= dc) continue;
            Vec rel = ag.position().sub(chest);
            System.out.printf("t=%4d cover=%3d ownerV=%4.2f yaw=%6.1f threat=%s fishRel=(%5.2f,%5.2f,%5.2f) dFish=%.2f dOwner=%.2f target=%s v=%.2f%n",
                    t, cover, run.owner.horizontalSpeed(), run.owner.yawDeg(), ag.threat(), rel.x(), rel.y(), rel.z(), df, dc,
                    ag.target(), ag.velocity().length());
        }
    }
}
