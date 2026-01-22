package grill24.fishtastic.util;

import java.util.function.Supplier;

public interface IGameRendererExtension {
    void fishtastic$displayItemActivation(ItemActivationAnimation animation);
    void fishtastic$displayItemActivation(Supplier<? extends ItemActivationAnimation> animationSupplier);
    void fishtastic$cancelCurrentAnimation();
    ItemActivationAnimation fishtastic$getActiveAnimation();
}
