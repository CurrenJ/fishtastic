package grill24.fishtastic.client.renderer;

import grill24.FishtasticRegistries;
import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.ShelterUse;
import grill24.fishtastic.data.FishAnimationConfig;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.data.SwarmConfig;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Shared species appearance/descriptor mapping for tanks and ambient fish. */
public final class FishVisuals {
    private FishVisuals() {}

    public record ResolvedFishRender(FishAnimationConfig animation, float renderCalibration) {
        private static final ResolvedFishRender DEFAULT = new ResolvedFishRender(
                FishAnimationConfig.HorizontalSwim.DEFAULT, FishProfile.DEFAULT_RENDER_CALIBRATION);
    }

    public static ResolvedFishRender resolve(ItemStack stack, Level level) {
        if (stack.isEmpty()) return ResolvedFishRender.DEFAULT;

        var itemKey = BuiltInRegistries.ITEM.getResourceKey(stack.getItem());
        if (itemKey.isEmpty()) return ResolvedFishRender.DEFAULT;

        ResourceKey<FishProfile> profileKey = ResourceKey.create(
                FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, itemKey.get().location());

        return level.registryAccess()
                .registryOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY)
                .getOptional(profileKey)
                .map(profile -> new ResolvedFishRender(
                        profile.animation().orElse(FishAnimationConfig.HorizontalSwim.DEFAULT),
                        profile.renderCalibration()))
                .orElse(ResolvedFishRender.DEFAULT);
    }

    public static FishSpec descriptor(ItemStack stack, Level level, boolean mirrored) {
        ResolvedFishRender render = resolve(stack, level);
        return new FishSpec(renderedLength(stack, render.renderCalibration()), locomotionOf(render.animation()),
                mirrored, speciesId(stack), shelterUseOf(stack, level));
    }

    public static Locomotion locomotionOf(FishAnimationConfig anim) {
        // Java 17 target: instanceof chain instead of a pattern switch (sealed-switch exhaustiveness is lost).
        if (anim instanceof FishAnimationConfig.HorizontalSwim) return Locomotion.FREE_SWIM;
        if (anim instanceof FishAnimationConfig.BellyDown) return Locomotion.GLIDE;
        if (anim instanceof FishAnimationConfig.UprightFloat) return Locomotion.DRIFT;
        if (anim instanceof FishAnimationConfig.FloorSit) return Locomotion.BENTHIC;
        if (anim instanceof FishAnimationConfig.UprightSit) return Locomotion.BENTHIC;
        if (anim instanceof FishAnimationConfig.Planted) return Locomotion.ANCHORED;
        throw new IllegalStateException("Unhandled FishAnimationConfig: " + anim);
    }

    public static int speciesId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getId(stack.getItem());
    }

    public static ShelterUse shelterUseOf(ItemStack stack, Level level) {
        return SwarmConfig.resolve(stack, level).shelter().map(behaviour -> switch (behaviour) {
            case VISITOR -> ShelterUse.VISITOR;
            case SKITTISH -> ShelterUse.SKITTISH;
            case LURKER -> ShelterUse.LURKER;
        }).orElse(ShelterUse.NONE);
    }

    public static float renderedLength(ItemStack stack, float calibration) {
        // Matches the render scale exactly: unrolled fish render at a flat 0.5 (never length 0,
        // which would let an unmeasured species free-swim when it shouldn't).
        if (ItemSizeHelper.hasSize(stack)) {
            return (ItemSizeHelper.getSize(stack) / 100f) * calibration;
        }
        return 0.5f;
    }
}
