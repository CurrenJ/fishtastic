package grill24.fishtastic.util;

public interface IGameRendererExtension {
    void fishtastic$displayItemActivation(ItemActivationAnimation animation);
    void fishtastic$cancelCurrentAnimation();
    ItemActivationAnimation fishtastic$getActiveAnimation();
}
