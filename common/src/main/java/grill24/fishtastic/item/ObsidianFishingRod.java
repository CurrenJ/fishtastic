package grill24.fishtastic.item;

public class ObsidianFishingRod extends FishtasticFishingRodItem {

    public ObsidianFishingRod(Properties properties) {
        super(properties);
    }

    // The intended tool for lava fishing — obsidian shrugs off the heat far better than copper.
    @Override
    public int getLavaDamageIntervalTicks() {
        return 100;
    }

    @Override
    public int getLavaDamagePerTick() {
        return 1;
    }
}
