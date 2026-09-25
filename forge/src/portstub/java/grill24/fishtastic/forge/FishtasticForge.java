// PORT STUB: deleted in B2.0
package grill24.fishtastic.forge;

import net.minecraft.SharedConstants;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** B1 probe: stands in for the real entrypoint (excluded) so the scaffold can be load-tested. */
@Mod("fishtastic")
public final class FishtasticForge {
    private static final Logger LOGGER = LoggerFactory.getLogger("fishtastic");

    public FishtasticForge() {
        LOGGER.info("Fishtastic B1 probe loaded on Forge {}", SharedConstants.getCurrentVersion().getName());
    }
}
