package grill24.fishtastic.client.encyclopedia;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.component.CharmEffect;
import grill24.fishtastic.data.FishMoonPhase;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.item.CopperFishingRod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;

import java.util.Set;

/**
 * The client player's fishing environment, as the server's odds maths sees it: zones at the
 * player, biome, time of day (with a charm's night override), weather and moon phase. Built
 * from the live client by {@link #fromClient()}, or directly (tests, other callers).
 */
public record EnvSnapshot(Set<FishProfile.Zone> zones, Holder<Biome> biome, FishProfile.TimeOfDay time,
                          FishProfile.WeatherCondition weather, FishMoonPhase moon) {

    /**
     * The moon only matters at night under clear skies — mirrors the gate in
     * {@link FishProfile#computeEnvironmentMultiplier}.
     */
    public boolean moonVisible() {
        return time == FishProfile.TimeOfDay.NIGHT && weather == FishProfile.WeatherCondition.CLEAR;
    }

    /** Snapshot of the local player's position, or null when there is no level or player. */
    public static EnvSnapshot fromClient() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        BlockPos pos = mc.player.blockPosition();
        Holder<Biome> biome = mc.level.getBiome(pos);
        return new EnvSnapshot(
                FishProfile.Zone.resolve(biome, pos.getY(), mc.level.getSeaLevel()),
                biome,
                effectiveTimeOfDay(mc.player, mc.level.getOverworldClockTime()),
                FishProfile.WeatherCondition.fromLevel(mc.level, pos),
                FishMoonPhase.at(mc.level, pos));
    }

    /**
     * Time of day used for spawn-condition evaluation, accounting for charm overrides (e.g. luna
     * charm forcing night) the same way {@code FishingMinigameManager} does.
     */
    public static FishProfile.TimeOfDay effectiveTimeOfDay(Player player, long clockTime) {
        CharmEffect charmEffect = equippedCharmEffect(player);
        return (charmEffect != null && charmEffect.forceNightFishing())
                ? FishProfile.TimeOfDay.NIGHT
                : FishProfile.TimeOfDay.fromGameTime(clockTime);
    }

    /**
     * Charm equipped in the player's held fishing rod's charm slot, if any — mirrors the rod
     * lookup in {@code FishingMinigameClientHandler} so the page reflects the same overrides.
     */
    public static CharmEffect equippedCharmEffect(Player player) {
        if (player == null) return null;
        ItemStack rod = player.getMainHandItem();
        if (!rod.is(FishtasticItems.COPPER_FISHING_ROD)) {
            rod = player.getOffhandItem();
        }
        if (!rod.is(FishtasticItems.COPPER_FISHING_ROD)) return null;
        ItemStack charm = CopperFishingRod.getCharm(rod);
        if (charm.isEmpty()) return null;
        return FishtasticItemData.get(charm, FishtasticDataComponents.CHARM_EFFECT);
    }
}
