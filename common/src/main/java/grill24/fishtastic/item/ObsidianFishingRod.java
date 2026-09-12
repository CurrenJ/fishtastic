package grill24.fishtastic.item;

public class ObsidianFishingRod extends FishtasticFishingRodItem {

    public ObsidianFishingRod(Properties properties) {
        super(properties);
    }

    // The intended tool for lava fishing — obsidian is fully immune to lava heat.
    @Override
    public int getLavaDamageIntervalTicks() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int getLavaDamagePerTick() {
        return 0;
    }
}
