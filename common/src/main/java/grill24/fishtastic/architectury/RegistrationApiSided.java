package grill24.fishtastic.architectury;

import dev.architectury.injectables.annotations.ExpectPlatform;
import grill24.fishtastic.fishtank.FishTankFrameType;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Function;

public class RegistrationApiSided {
    @ExpectPlatform
    public static IRegistrationApi getInstance() {
        throw new AssertionError();
    }
}
