// PORT STUB: deleted in A4
package grill24.fishtastic.util;

import grill24.fishtastic.component.CharmEffect;
import grill24.fishtastic.data.FishProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Stand-in for the fishing minigame HUD (A4, GUI). Keeps the real {@link FishingMinigameState}, so
 * the client handler still sets up targets, but draws nothing and never finishes on its own.
 */
public class FishingMinigameAnimation implements ItemActivationAnimation {
    /** Placeholder for the bar/bobber geometry record; only its bobber size matters without a HUD. */
    public record FishingBarLayout(float bobberSize) {}

    // 9px of a 28px travel zone, and 7px for the small bobber (the real LAYOUT / LAYOUT_SMALL).
    public static final FishingBarLayout LAYOUT = new FishingBarLayout(9f / 28f);
    public static final FishingBarLayout LAYOUT_SMALL = new FishingBarLayout(7f / 28f);

    private final FishingMinigameState minigameState;

    public FishingMinigameAnimation() {
        this(LAYOUT);
    }

    public FishingMinigameAnimation(FishingBarLayout layout) {
        this.minigameState = new FishingMinigameState(layout.bobberSize(), (1.0f - layout.bobberSize()) / 2f);
    }

    public static FishingMinigameAnimation createCelebrationPreview(CatchCelebration.Tier tier, ItemStack hero) {
        return new FishingMinigameAnimation();
    }

    public void setTutorial(boolean tutorial) {}

    public void setInputForceMultiplier(float multiplier) {}

    public void setEquippedCharmEffect(@Nullable CharmEffect charmEffect) {}

    public void setTopWeightedFishPreviews(@Nullable List<ItemStack> stacks) {}

    public void setUndiscoveredSpecies(@Nullable Set<ResourceLocation> species) {}

    public void setEquippedGearStacks(@Nullable ItemStack bait, @Nullable ItemStack hook, @Nullable ItemStack charm) {}

    public void setBaitWillBeSaved(boolean baitWillBeSaved) {}

    public void setCurrentZones(@Nullable Set<FishProfile.Zone> zones) {}

    public void applyPlayerImpulse() {}

    public List<Integer> getCaughtTargetIndices() {
        return List.of();
    }

    public FishingMinigameState getMinigameState() {
        return minigameState;
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphics guiGraphics, float partialTick) {}

    @Override
    public void tick() {}
}
