package grill24.fishsim.companion;

import java.util.*;

/** Feature memory and performance. No randomness: adding an empty index cannot perturb motion. */
final class Notice {
    private record Key(WorldFeature.Kind kind, long id) {}
    private static final class Memory { int seen, engaged, cooldown, absent; float novelty = 1; long beat = Long.MIN_VALUE; boolean splashed; }
    private final Map<Key, Memory> memories = new HashMap<>();
    WorldFeature feature;
    float utility;
    private int tick, performing;
    private long gameTick;
    private CompanionWorld world;
    private Habitat habitat;
    private final Temperament temperament;
    private final Mood mood;
    final List<Emote> events = new ArrayList<>();
    Notice(Temperament temperament, Mood mood) { this.temperament = temperament; this.mood = mood; }

    void update(CompanionWorld world, Habitat habitat, Vec self) {
        this.world = world; this.habitat = habitat; gameTick = world.gameTick(); tick++; events.clear();
        for (Memory m : memories.values()) { m.absent++; if (m.cooldown > 0) m.cooldown--; m.novelty = Math.min(1, m.novelty + 1f / 3600); }
        WorldFeature best = null; float score = 0;
        for (WorldFeature f : world.features()) {
            double distance = f.pos().distanceTo(world.owner().chest());
            if (distance > (f.kind() == WorldFeature.Kind.MUSIC ? 24 : 6)) continue;
            Memory m = memories.computeIfAbsent(new Key(f.kind(), f.id()), k -> new Memory());
            m.absent = 0; m.seen++;
            boolean note = f.kind() == WorldFeature.Kind.MUSIC && f.music().source() == WorldFeature.Source.NOTE_BLOCK;
            if (note && f.music().active(gameTick) && m.beat != f.music().lastBeatTick()) {
                m.beat = f.music().lastBeatTick(); events.add(new Emote(Emote.Kind.NOTE, self, f.music().pitch()));
            }
            float interest = switch (f.kind()) {
                case WARMTH -> 0; // Warmth uses the established species-sized inspection steering.
                case WATER_SURFACE -> world.owner().inWater() || distance > 3.6 ? 0 : habitat.ownWater(f.zone()) ? 1.05f : 0.8f;
                case LIGHT -> f.emission() >= 10 && world.ambient().darkness() > 0.45f && !mood.moonstruck()
                        ? (habitat.nightActive() ? 1.0f : 0.78f) : 0;
                case MUSIC -> f.music().active(gameTick) ? 1.0f : 0;
            };
            boolean song = f.kind() == WorldFeature.Kind.MUSIC && f.music().source() == WorldFeature.Source.JUKEBOX;
            if (song && f.music().active(gameTick) && tick % 30 == 1) events.add(new Emote(Emote.Kind.NOTE,self));
            if (m.cooldown > 0 && !note && !song && f.kind() != WorldFeature.Kind.WARMTH) continue;
            interest *= Math.min(1.2f, 0.85f + 0.15f * temperament.curiosity());
            if (!note && !song && f.kind() != WorldFeature.Kind.WARMTH) interest *= m.novelty;
            if (!note) interest *= Math.min(1, m.seen / 10f);
            // Commitment prevents a newly visible neighbour stealing the performance.
            if (feature != null && feature.kind() == f.kind() && feature.id() == f.id()) interest += 0.12f;
            if (interest > score || interest == score && best != null && f.id() < best.id()) { score = interest; best = f; }
        }
        memories.entrySet().removeIf(e -> e.getValue().absent > 3600);
        if (best == null || feature == null || best.id() != feature.id() || best.kind() != feature.kind()) {
            performing = 0;
            if (best != null) memories.get(new Key(best.kind(),best.id())).splashed = false;
        }
        feature = best; utility = score > 0.5f ? Math.min(0.88f, score) : 0;
    }

    Vec destination(OwnerState owner, Vec self) {
        if (feature == null) return self;
        Vec source = feature.pos();
        Vec away = owner.chest().sub(source).horizontal().normalize();
        if (away.lengthSqr() < 1e-8) away = owner.flatRight();
        double phase = performing * 0.05;
        Vec point;
        switch (feature.kind()) {
            case WARMTH -> point = source.add(away.scale(1.6)).add(0, 0.3, 0);
            case WATER_SURFACE -> {
                // One short dip per visit, with a long above-water hover on either side.
                double dip = performing >= 25 && performing < 85 ? Math.sin((performing - 25) * Math.PI / 60) : 0;
                point = source.add(away.scale(0.35 - 1.1 * dip)).add(0, 0.45 - 1.4 * dip, 0);
            }
            case LIGHT -> {
                double radius = temperament.peekOut() ? 1.7 + 0.4 * Math.sin(phase * 2) : temperament.ambush() ? 1.8 : 0.85;
                double angle = temperament.ambush() || temperament.nuzzle() ? Math.atan2(away.z(), away.x()) : phase * (temperament.stillness() > 0.8 ? 0.45 : 0.9);
                point = source.add(Math.cos(angle) * radius, 0.35 + (temperament.ambush() ? 0 : 0.15 * Math.sin(phase)), Math.sin(angle) * radius);
            }
            case MUSIC -> {
                // Far songs and docile fish groove by the owner rather than pulling it out of band.
                Vec centre = source.distanceTo(owner.chest()) <= 8 && !temperament.nuzzle()
                        ? source.add(away.scale(temperament.peekOut() ? 2 : 1.2)).add(0, 0.8, 0)
                        : owner.chest().add(owner.flatForward().scale(1.8)).add(owner.flatRight().scale(0.9));
                double rhythm = temperament.regular() == 1 ? phase * 5 : phase * 5 + (temperament.jitter() > 1.3 ? 0.6 * Math.sin(phase * 9) : 0);
                double bob = Math.sin(rhythm) * (temperament.guard() ? 0.3 : 0.18);
                if (feature.music().source() == WorldFeature.Source.NOTE_BLOCK) {
                    double age = gameTick - feature.music().lastBeatTick();
                    bob = Math.sin(Math.max(0, Math.min(1, age / 12)) * Math.PI) * (0.15 + feature.music().pitch() * 0.5);
                }
                point = centre.add(Math.sin(phase * (temperament.stillness() > 0.8 ? 1 : 3)) * 0.35, bob, 0);
            }
            default -> point = source;
        }
        // Features can never pull a fish beyond its owner's local band.
        Vec delta = point.sub(owner.chest());
        return delta.length() > 3.8 ? owner.chest().add(delta.clampLength(3.8)) : point;
    }

    private Vec waterEdge, waterBankOwner;
    private long waterBankId;
    private int waterBankTick = -100;
    /** Pick a dry, visible bank with a ground path; do not ask a walker to linger below the view. */
    boolean groundWaterReachable(OwnerState owner, GroundBody ground, LocalVoxels blocks) {
        if (feature == null || feature.kind() != WorldFeature.Kind.WATER_SURFACE) { waterEdge = null; return true; }
        if (performing > 0 && waterEdge != null) return true;
        if (waterBankOwner != null && waterBankId == feature.id() && owner.feet().distanceTo(waterBankOwner) < 0.5 && tick-waterBankTick < 20) return waterEdge != null;
        waterBankOwner = owner.feet(); waterBankId = feature.id(); waterBankTick = tick;
        waterEdge = null;
        for (double radius=1;radius<=2.5;radius+=0.25) for(int angle=0;angle<16;angle++) {
            double a=angle*Math.PI/8;
            Vec p=feature.pos().add(Math.cos(a)*radius,0,Math.sin(a)*radius);
            double floor=blocks.floorBelow(p.x(),p.z(),owner.feet().y()+0.5,1,ground.radius);
            if(Double.isNaN(floor)||Math.abs(floor-ground.floorY)>0.5) continue;
            Vec bank=new Vec(p.x(),floor+ground.radius+LocalVoxels.GROUND_LIFT,p.z());
            if(bank.distanceTo(owner.chest())>3.8 || blocks.water(bank) || !Visibility.visible(owner,bank,blocks)) continue;
            Vec delta=bank.sub(ground.pos); int steps=Math.max(1,(int)Math.ceil(delta.horizontalLength()/0.1));
            boolean path=true;
            for(int i=1;i<=steps;i++) {
                Vec q=ground.pos.lerp(bank,i/(double)steps);
                double f=blocks.floorBelow(q.x(),q.z(),ground.floorY+0.5,1,ground.radius);
                if(Double.isNaN(f)||Math.abs(f-ground.floorY)>0.5 || blocks.water(q)) {path=false;break;}
            }
            if(path) {waterEdge=bank;return true;}
        }
        return false;
    }

    private boolean groundDipped, groundPerformance;
    private long groundBeat = Long.MIN_VALUE;
    Vec groundDestination(OwnerState owner, GroundBody ground, LocalVoxels voxels) {
        groundPerformance = true;
        Vec target = destination(owner, ground.pos);
        if (feature != null && feature.kind() == WorldFeature.Kind.MUSIC
                && feature.music().source() == WorldFeature.Source.NOTE_BLOCK && feature.music().active(gameTick)
                && feature.music().lastBeatTick() != groundBeat && ground.notePulse(feature.music().pitch())) groundBeat = feature.music().lastBeatTick();
        if (feature != null && feature.kind() == WorldFeature.Kind.MUSIC && feature.pos().distanceTo(owner.chest()) <= 8) {
            Vec beyond = feature.pos().sub(owner.feet()).horizontal().normalize();
            target = feature.pos().add(beyond.scale(1.3));
            Vec offset = target.sub(owner.feet());
            if (offset.horizontalLength() > 3.8) target = owner.feet().add(offset.horizontal().clampLength(3.8));
        }
        if (feature == null || feature.kind() != WorldFeature.Kind.WATER_SURFACE) {
            groundDipped = false;
            double floor = voxels.floorBelow(target.x(),target.z(),ground.pos.y()+0.5,3,ground.radius);
            return Double.isNaN(floor) ? ground.standing() : new Vec(target.x(),floor,target.z());
        }
        Vec away = owner.feet().sub(feature.pos()).horizontal().normalize();
        Vec edge = waterEdge == null ? feature.pos().add(away.scale(1.0)) : waterEdge;
        if (performing == 0) groundDipped = false;
        if (!groundDipped && performing >= 12 && ground.pos.sub(edge).horizontalLength() < 1.1) {
            Vec water = feature.pos().add(0, -0.2, 0);
            if (ground.dipTo(water, voxels)) groundDipped = true;
        }
        return edge;
    }

    PoseHint perform(Vec self, Vec destination, boolean watching) {
        if (feature == null) return PoseHint.NONE;
        if (!watching && self.distanceTo(destination) > 1.0) return PoseHint.WATCH;
        Memory m = memories.get(new Key(feature.kind(), feature.id()));
        performing++; m.engaged++;
        switch (feature.kind()) {
            case WATER_SURFACE -> {
                if (world.blocks().water((int)Math.floor(self.x()),(int)Math.floor(self.y()),(int)Math.floor(self.z())) && !m.splashed) {
                    m.splashed = true; events.add(new Emote(Emote.Kind.SPLASH,self));
                }
            }
            case LIGHT -> { if (performing == 1 || performing % 70 == 0) events.add(new Emote(Emote.Kind.GLINT, feature.pos())); }
            case MUSIC -> {
                if (feature.music().source() == WorldFeature.Source.NOTE_BLOCK) {
                    if (m.beat != feature.music().lastBeatTick()) { m.beat = feature.music().lastBeatTick(); events.add(new Emote(Emote.Kind.NOTE,self,feature.music().pitch())); }
                }
            }
            default -> { }
        }
        if (m.engaged >= (groundPerformance ? feature.kind() == WorldFeature.Kind.WATER_SURFACE ? 24 : 40 : feature.kind() == WorldFeature.Kind.WATER_SURFACE ? 120 : 80) && feature.kind() != WorldFeature.Kind.MUSIC && feature.kind() != WorldFeature.Kind.WARMTH) {
            m.cooldown = groundPerformance && feature.kind() == WorldFeature.Kind.WATER_SURFACE ? 1200 : 600; m.novelty *= 0.6f; m.engaged = 0; m.seen = 0;
        }
        if (watching) return PoseHint.WATCH;
        return switch (feature.kind()) { case WATER_SURFACE -> performing >= 25 && performing < 85 ? PoseHint.DIP : PoseHint.NOSE; case MUSIC -> PoseHint.DANCE; default -> PoseHint.NOSE; };
    }
}
