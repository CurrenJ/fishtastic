// PORT STUB: deleted in A2.0
package grill24.fishtastic.neoforge;

import net.minecraft.SharedConstants;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A1 probe: stands in for the real entrypoint (excluded) so the scaffold can be load-tested. */
@Mod("fishtastic")
public final class FishtasticNeoForge {
    private static final Logger LOGGER = LoggerFactory.getLogger("fishtastic");

    public FishtasticNeoForge() {
        LOGGER.info("Fishtastic A1 probe loaded on NeoForge {}", SharedConstants.getCurrentVersion().getName());
    }
}
