package grill24.fishtastic.forge;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.InMemoryFormat;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;
import java.util.Map;

public final class FishtasticConfig {
    public static class Startup {
        public final ForgeConfigSpec.ConfigValue<List<? extends Config>> fishTankPartBlacklists;
        public final ForgeConfigSpec.ConfigValue<List<? extends Config>> blockModelPathOverrides;

        Startup(ForgeConfigSpec.Builder builder) {
            fishTankPartBlacklists = builder
                    .comment("""
                            Blacklisted blocks for fish tank parts.
                            Specify the parts (frame, glass, sand) and the blocks/tags to blacklist.
                            """)
                    .translation("fishtastic.config.fishTankPartBlacklists")
                    .worldRestart()
                    .defineList("fishTankPartBlacklists", () -> {
                        var frameBlacklist = Config.wrap(Map.of(
                            "parts", List.of("frame", "glass", "sand"),
                            "blocks", List.of("fishtastic:fish_tank")
                        ), InMemoryFormat.defaultInstance());
                        return List.of(frameBlacklist);
                    }, o -> o instanceof Config);

            blockModelPathOverrides = builder
                    .comment("""
                            Model path overrides for blocks or block tags.
                            """)
                    .translation("fishtastic.config.blockModelPathOverrides")
                    .worldRestart()
                    .defineList("blockModelPathOverrides", () -> {
                        var borderlessGlassOverride = Config.wrap(Map.of(
                            "pattern", "fishtastic:*_borderless_stained_glass",
                            "modelPath", "fishtastic:block/glass/{name}"
                        ), InMemoryFormat.defaultInstance());
                        var clearGlassOverride = Config.wrap(Map.of(
                            "pattern", "fishtastic:*_clear_stained_glass",
                            "modelPath", "fishtastic:block/glass/{name}"
                        ), InMemoryFormat.defaultInstance());
                        return List.of(borderlessGlassOverride, clearGlassOverride);
                    }, o -> o instanceof Config);
        }
    }

    public static final Startup STARTUP;
    private static final ForgeConfigSpec startupSpec;

    static {
        var startupPair = new ForgeConfigSpec.Builder().configure(Startup::new);
        STARTUP = startupPair.getLeft();
        startupSpec = startupPair.getRight();
    }

    public static void register() {
        // Forge 52 has no ModConfig.Type.STARTUP; COMMON is the closest analog (loaded once at
        // startup, not per-world).
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, startupSpec);
    }
}
