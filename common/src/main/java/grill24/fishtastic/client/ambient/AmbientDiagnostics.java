package grill24.fishtastic.client.ambient;

import grill24.fishsim.ambient.WaterDiscovery;
import grill24.fishsim.domain.WaterDomain;
import grill24.fishtastic.Fishtastic;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The self-test's read-only view of an {@link AmbientFishRuntime} (audit §5.3): every individual
 * with its clearances, per-domain reports, the shown pools and samplers, and the one-line debug
 * state the ambient commands print. Nothing in the simulation reads any of it.
 */
public final class AmbientDiagnostics {
    private final AmbientFishRuntime rt;

    AmbientDiagnostics(AmbientFishRuntime rt) {
        this.rt = rt;
    }

    /** Called only by the invariant harness, before a rebuild mutates its input snapshot. */
    void rebuilding(AmbientFishRuntime.DomainState state, long tick, int x, int y, int z) {
        if (state.builtColumns != state.patch.columns()) return;
        Fishtastic.LOGGER.info("[ambient-rebuild] tick={} key={} columns={} version={}->{} observer={},{},{}->{},{},{} margin={}->{} changedColumns={}",
                tick, state.patchKey, state.builtColumns, state.builtVersion,
                rt.discovery.patchVersion(state.patchKey), state.builtPx, state.builtPy, state.builtPz,
                x, y, z, state.builtTopMargin, state.topMargin, rt.discovery.counters().columnsChanged);
    }

    /** What one individual looks like right now — the harness's window into the population. */
    public record Individual(long id, long patchKey, String species, double length, double x, double y,
                             double z, float yaw, boolean retiring, int retireReason, float fade,
                             long bornTick, int lifetimeTicks,
                             double openFaceDistance, double surfaceClearance, double bedClearance,
                             boolean inWater, boolean bodyFits) {}
    /** Per-domain summary, for the harness log. */
    public record DomainReport(long patchKey, int columns, int originX, int originY, int originZ,
                               int individuals, int retiring, long builds, double buildMillis,
                               boolean openMinL, boolean openMaxL, boolean openMinY, boolean openMaxY,
                               boolean openMinD, boolean openMaxD) {}
    /**
     * Every species any shown patch's sampler can currently draw, in first-seen order — the union
     * of the live visual populations. Before the first context arrives, or where nothing is
     * representable, this is empty: the runtime shows only what an authoritative rt.pool named.
     */
    public List<String> pool() {
        List<String> out = new ArrayList<>();
        for (AmbientFishRuntime.DomainState st : rt.domains.values()) {
            if (st.sampler == null) continue;
            for (VisualSampler.Entry entry : st.sampler.entries()) {
                if (!out.contains(entry.species())) out.add(entry.species());
            }
        }
        return List.copyOf(out);
    }
    /**
     * The species one patch's sampler may draw, in candidate order — the harness's legality window
     * ("every live individual's species must be in here"). Empty when the patch has no usable
     * context or nothing representable.
     */
    public List<String> representableSpecies(long patchKey) {
        AmbientFishRuntime.DomainState st = rt.domains.get(patchKey);
        if (st == null || st.sampler == null) return List.of();
        List<String> out = new ArrayList<>(st.sampler.size());
        for (VisualSampler.Entry entry : st.sampler.entries()) out.add(entry.species());
        return List.copyOf(out);
    }
    /** One patch's sampler entries (shares included), for the scene log; empty when unusable. */
    public List<VisualSampler.Entry> samplerFor(long patchKey) {
        AmbientFishRuntime.DomainState st = rt.domains.get(patchKey);
        return st == null || st.sampler == null ? List.of() : st.sampler.entries();
    }
    /** The fingerprint a patch's applied sampler was built from, or {@link Long#MIN_VALUE}. */
    public long appliedFingerprint(long patchKey) {
        AmbientFishRuntime.DomainState st = rt.domains.get(patchKey);
        return st == null ? Long.MIN_VALUE : st.appliedFingerprint;
    }
    /** Patch random states currently held for a quick return (bounded; diagnostics). */
    public int retainedStateCount() {
        return rt.retained.size();
    }
    /**
     * One line of internal state for the harness log: the population, and for each shown domain why
     * it is or is not taking fish. Diagnosis only — nothing in the sim reads it.
     */
    public String debugState() {
        StringBuilder sb = new StringBuilder(160);
        WaterDiscovery.Counters c = rt.discovery.counters();
        int knownNear = 0, waterNear = 0;
        BlockPos at = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.blockPosition() : BlockPos.ZERO;
        for (WaterDiscovery.Column col : rt.discovery.columns()) {
            if (Math.abs(col.x() - at.getX()) > 8 || Math.abs(col.z() - at.getZ()) > 8) continue;
            knownNear++;
            if (!col.water().isEmpty()) waterNear++;
        }
        sb.append("armed=").append(rt.armed).append(" indivs=").append(rt.stats.individuals)
                .append('/').append(rt.budget.individuals()).append(" density=").append(rt.budget.density())
                .append(" tickMs[mean=").append(rt.stats.totalTickMillis / Math.max(1, rt.stats.ticks))
                .append(" peak=").append(rt.stats.peakTickMillis).append("] submitMs[mean=")
                .append(rt.stats.totalSubmitMillis / Math.max(1, rt.stats.renderFrames))
                .append(" peak=").append(rt.stats.peakSubmitMillis).append("] species=").append(pool().size())
                .append(" retained=").append(rt.retained.size()).append('/').append(AmbientTuning.MAX_RETAINED_PATCHES)
                .append(" domains=").append(rt.domains.size())
                .append(" livePatches=").append(rt.discovery.livePatches().size())
                .append(" submits=").append(rt.worldSubmitsThisFrame)
                .append(" retired[face=").append(rt.stats.retireOpenFace).append(" invalid=").append(rt.stats.retireInvalid)
                .append(" domain=").append(rt.stats.retireDomainLost).append(" excluded=").append(rt.stats.retireExcluded)
                .append(" expired=").append(rt.stats.retireExpired).append(']')
                .append(" known=").append(rt.discovery.columns().size())
                .append(" nearKnown=").append(knownNear).append(" nearWater=").append(waterNear)
                .append(" scansThisTick=").append(c.scansThisTick)
                .append(" cellsThisTick=").append(c.cellsThisTick)
                .append(" cursor=").append(c.cursorStepsThisTick)
                .append(" spawnSkip=").append(rt.lastSpawnSkip);
        int shown = 0;
        for (AmbientFishRuntime.DomainState st : rt.domains.values()) {
            if (shown++ == 4) break;
            WaterDiscovery.Patch now = rt.discovery.patch(st.patchKey);
            int omitted = st.sampler == null ? 0 : st.sampler.omitted().size();
            int representable = st.sampler == null ? 0 : st.sampler.size();
            sb.append(" [key=").append(st.patchKey)
                    .append(" patchCols=").append(st.patch == null ? -1 : st.patch.columns())
                    .append(" mapCols=").append(now == null ? -1 : now.columns())
                    .append(" builtCols=").append(st.builtColumns)
                    .append(" water=").append(st.domain == null ? "none" : "built")
                    .append(" refilling=").append(st.refilling)
                    .append(" ctx=").append(st.contextOk ? "ok" : "none")
                    .append(" pool=").append(representable).append('/').append(representable + omitted)
                    .append(" fp=").append(st.appliedFingerprint == Long.MIN_VALUE ? "-"
                            : Long.toHexString(st.appliedFingerprint))
                    .append(" top=").append(String.format(java.util.Locale.ROOT, "%.2f", st.topMargin))
                    .append(" members=").append(st.members.size()).append('/').append(rt.targetFor(st))
                    .append(" spawn=").append(skipReason(st))
                    .append(']');
        }
        individuals().stream().sorted(Comparator.comparingDouble(p -> at.distToCenterSqr(p.x(), p.y(), p.z())))
                .limit(3).forEach(p -> sb.append(String.format(java.util.Locale.ROOT,
                        " [fish=%s xyz=%.1f,%.1f,%.1f depth=%.1f fade=%.2f reason=%d]",
                        p.species(), p.x(), p.y(), p.z(), p.surfaceClearance(), p.fade(), p.retireReason())));
        return sb.toString();
    }
    /** Why {@link #spawnOne} would skip this domain, in one word; {@code -} means eligible. */
    private String skipReason(AmbientFishRuntime.DomainState st) {
        if (st.dying) return "dying";
        if (st.domain == null) return "unbuilt";
        if (st.members.size() >= rt.targetFor(st)) return "full";
        if (!st.contextOk) return "ctxx";
        if (st.sampler == null || st.sampler.isEmpty()) return "norep";
        if (AmbientFishRuntime.recent(st.lastOmissionTick, rt.lastTick, AmbientFishRuntime.OMISSION_RETRY_TICKS)) return "retry";
        return "-";
    }
    public List<DomainReport> domainReports() {
        List<DomainReport> out = new ArrayList<>();
        for (AmbientFishRuntime.DomainState st : rt.domains.values()) {
            int retiring = 0;
            for (AmbientFishRuntime.Member m : st.members) {
                if (m.retiring()) retiring++;
            }
            WaterDomain d = st.domain;
            out.add(new DomainReport(st.patchKey, st.patch == null ? 0 : st.patch.columns(),
                    st.originX(), st.originY(), st.originZ(), st.members.size(), retiring,
                    st.builtColumns < 0 ? 0 : 1, st.buildNanos / 1_000_000.0,
                    d != null && d.openFace(WaterDomain.FACE_MIN_L), d != null && d.openFace(WaterDomain.FACE_MAX_L),
                    d != null && d.openFace(WaterDomain.FACE_MIN_Y), d != null && d.openFace(WaterDomain.FACE_MAX_Y),
                    d != null && d.openFace(WaterDomain.FACE_MIN_D), d != null && d.openFace(WaterDomain.FACE_MAX_D)));
        }
        return out;
    }
    /**
     * Live cached column count for a patch key, straight from the rt.discovery cache. Unlike
     * {@link #domainReports()}, which shows the stale count held by the last successful build,
     * this drops to zero as soon as the patch's columns are evicted — the harness's window into
     * "this water is gone / this water is back".
     */
    public int patchColumns(long patchKey) {
        return rt.discovery.columnCount(patchKey);
    }
    /** Snapshot of every individual, in world coordinates with its clearance figures. */
    public List<Individual> individuals() {
        List<Individual> out = new ArrayList<>();
        for (AmbientFishRuntime.DomainState st : rt.domains.values()) {
            for (int i = 0; i < st.members.size(); i++) out.add(snapshot(st, i));
        }
        return out;
    }
    private Individual snapshot(AmbientFishRuntime.DomainState st, int i) {
        AmbientFishRuntime.Member m = st.members.get(i);
        float l = st.engine.posL()[i], y = st.engine.posY()[i], d = st.engine.posD()[i];
        WaterDomain dom = st.domain;
        boolean inWater = dom != null && dom.contains(l, y, d);
        boolean fits = dom != null && dom.bodyFits(l, y, d, m.body);
        double open = dom == null ? Float.MAX_VALUE : dom.openFaceDistance(l, y, d);
        double surface = dom == null ? Double.MAX_VALUE : dom.surfaceAt(l, d) - y;
        double bed = dom == null ? Double.MAX_VALUE : y - dom.bedAt(l, d);
        return new Individual(m.id, st.patchKey, m.species, m.spec.length(),
                st.originX() + l, st.originY() + y, st.originZ() + d, st.engine.yawDeg[i],
                m.retiring(), m.retireReason, m.fade, m.bornTick, m.lifetimeTicks,
                open, surface, bed, inWater, fits);
    }
}
