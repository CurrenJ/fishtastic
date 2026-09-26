package grill24.fishtastic.forge;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.InMemoryFormat;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;
import java.util.Map;

public final class FishtasticConfig {
    /**
     * The config defines blacklisted blocks for fish tank parts and model path overrides.
     */
    public static class Startup {
        public final ForgeConfigSpec.ConfigValue<List<? extends Config>> fishTankPartBlacklists;
        public final ForgeConfigSpec.ConfigValue<List<? extends Config>> blockModelPathOverrides;

        Startup(ForgeConfigSpec.Builder builder) {
            //TODO: See if/how this can be made to work with the config GUI

            fishTankPartBlacklists = builder
                    .comment("""

                            Blacklisted blocks for fish tank parts.
                            Specify the parts (frame, glass, sand) and the blocks/tags to blacklist.
                            Blocks in these lists will not be used for the corresponding fish tank parts.

                            Example entries:
                            [[fishTankPartBlacklists]]
                                parts = ["frame"]
                                blocks = ["minecraft:stone", "#minecraft:logs"]

                            [[fishTankPartBlacklists]]
                                parts = ["frame", "glass", "sand"]
                                blocks = ["minecraft:bedrock"]
                                """)
                    .translation("fishtastic.config.fishTankPartBlacklists")
                    .worldRestart()
                    .defineList("fishTankPartBlacklists", () ->
                    {
                        var frameBlacklist = Config.wrap(Map.of(
                            "parts", List.of("frame", "glass", "sand"),
                            "blocks", List.of("fishtastic:fish_tank")
                        ), InMemoryFormat.defaultInstance());

                        return List.of(frameBlacklist);
                    }, o -> o instanceof Config);

            blockModelPathOverrides = builder
                    .comment("""
                          \s
                            Model path overrides for blocks or block tags.
                            Use this when blocks have models in non-standard locations (e.g., in subdirectories).
                            Supports both individual blocks and block tags.
                               \s
                            The {name} placeholder will be replaced with the block's registry name (without namespace).
                               \s""")
                    .translation("fishtastic.config.blockModelPathOverrides")
                    .worldRestart()
                    .defineList("blockModelPathOverrides", () -> {
                        // Default overrides for Fishtastic's custom glass blocks
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
        // Forge 47 has no ModConfig.Type.STARTUP (that's a NeoForge addition) - COMMON is the
        // closest analog: loaded once at startup, not per-world, matching this config's contents.
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, startupSpec);
    }
}
