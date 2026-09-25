// PORT STUB: deleted in B2.0
package grill24.fishtastic.fabric;

import net.fabricmc.api.ModInitializer;
import net.minecraft.SharedConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** B1 probe: stands in for the real entrypoint (excluded) so the scaffold can be load-tested. */
public final class FishtasticFabric implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("fishtastic");

    @Override
    public void onInitialize() {
        LOGGER.info("Fishtastic B1 probe loaded on Fabric {}", SharedConstants.getCurrentVersion().getName());
    }
}
