// PORT STUB: deleted in A5.4
package grill24.fishtastic.client.renderer;

import net.minecraft.client.Minecraft;

/** Stand-in for the world-outline atlas (A5.4): no atlas, so nothing to bake or invalidate. */
public final class FishtasticItemOutlineAtlas {
    private static final FishtasticItemOutlineAtlas INSTANCE = new FishtasticItemOutlineAtlas();

    private FishtasticItemOutlineAtlas() {}

    public static FishtasticItemOutlineAtlas getInstance() {
        return INSTANCE;
    }

    public void invalidate() {}

    public void processBakeQueue(Minecraft minecraft) {}
}
