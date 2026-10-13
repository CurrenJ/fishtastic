package grill24.fishtastic.item;

import grill24.fishtastic.server.FishingPoolResolver;

/**
 * The one species with a hook-dependent weight: diamond-alert iapsis is more likely to bite when
 * the hook owner is carrying diamonds.
 */
public class AcuteIapsisItem extends FishtasticFishItem {
    public AcuteIapsisItem(Properties properties) {
        super(properties);
    }

    /**
     * +1 weight per diamond carried by the hook owner. The count is an explicit input since the
     * Phase 3 extraction — {@link FishingPoolResolver.PersonalEquipment#fromHookOwner} on the real
     * cast path, and whatever a preview mode honestly knows otherwise — rather than being read back
     * out of {@code LootParams.THIS_ENTITY} here.
     *
     * <p>Note for future readers: the historical comment on this code claimed "10 extra weight per
     * diamond", but the arithmetic has always added one weight per diamond item stack count. That
     * is preserved deliberately; changing it would change real catch odds.
     */
    @Override
    protected int getAdditionalWeight(int diamondBonus) {
        return diamondBonus;
    }
}
