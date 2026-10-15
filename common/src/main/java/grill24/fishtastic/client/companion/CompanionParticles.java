package grill24.fishtastic.client.companion;

import grill24.fishsim.companion.Emote;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;

import java.util.List;

/** Sparse cosmetic beats. One limiter belongs to one fish, never to the whole party. */
public final class CompanionParticles {
    public static final double NEAR_FIELD_SQUARED = .75 * .75;
    private long nextTick = Long.MIN_VALUE;
    private ClientLevel previousLevel;
    private long spawned;
    private grill24.fishsim.companion.Vec lastSpawnPosition;

    public long spawned() { return spawned; }
    public grill24.fishsim.companion.Vec lastSpawnPosition() { return lastSpawnPosition; }

    public void emit(ClientLevel level, List<Emote> emotes) {
        Minecraft mc = Minecraft.getInstance();
        ParticleStatus setting = mc.options.particles().get();
        if (previousLevel != level) { previousLevel = level; nextTick = Long.MIN_VALUE; }
        if (setting == ParticleStatus.MINIMAL || emotes.isEmpty()) return;
        long now = level.getGameTime();
        if (now < nextTick) return;
        var camera = mc.gameRenderer.getMainCamera().position();
        for (Emote emote : emotes) {
            var p = emote.pos();
            double spawnY = p.y() + (emote.kind() == Emote.Kind.GLINT ? .65 : .12);
            if (camera.distanceToSqr(p.x(), p.y(), p.z()) < NEAR_FIELD_SQUARED
                    || camera.distanceToSqr(p.x(), spawnY, p.z()) < NEAR_FIELD_SQUARED) continue;
            var particle = switch (emote.kind()) {
                case HOMECOMING -> ParticleTypes.HEART;
                case SPLASH -> ParticleTypes.SPLASH;
                case NOTE -> ParticleTypes.NOTE;
                case SIGH -> grill24.fishtastic.FishtasticParticleTypes.TANK_BUBBLE.value();
                case SNORE -> grill24.fishtastic.FishtasticParticleTypes.TANK_BUBBLE.value();
                case STEAM -> ParticleTypes.CLOUD;
                case FROST -> ParticleTypes.SNOWFLAKE;
                case GLINT -> ParticleTypes.GLOW;
            };
            level.addParticle(particle, p.x(), spawnY, p.z(),
                    emote.kind() == Emote.Kind.NOTE ? emote.pitch() : 0,
                    emote.kind() == Emote.Kind.SNORE ? p.y() + .8
                            : emote.kind() == Emote.Kind.SIGH ? p.y() + .4 : .015, 0);
            spawned++;
            lastSpawnPosition = new grill24.fishsim.companion.Vec(p.x(), spawnY, p.z());
            nextTick = now + (setting == ParticleStatus.DECREASED ? 20 : 8);
            // A homecoming and splash have a second, small water cue at full density only.
            if (setting == ParticleStatus.ALL && (emote.kind() == Emote.Kind.HOMECOMING || emote.kind() == Emote.Kind.SPLASH)) {
                level.addParticle(grill24.fishtastic.FishtasticParticleTypes.TANK_BUBBLE.value(),
                        p.x(), p.y(), p.z(), 0, p.y() + .6, 0);
                spawned++;
            }
            break;
        }
    }
}
