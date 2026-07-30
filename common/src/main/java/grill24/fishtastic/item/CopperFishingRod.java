package grill24.fishtastic.item;

public class CopperFishingRod extends FishtasticFishingRodItem {

    public CopperFishingRod(Properties properties) {
        super(properties);
    }

    // Copper is not lava-resistant — punishing durability loss is the point: this rod
    // technically works in lava, but you're meant to switch to the obsidian rod.
    @Override
    public int getLavaDamageIntervalTicks() {
        return 20;
    }

    @Override
    public int getLavaDamagePerTick() {
        return 5;
    }
}
