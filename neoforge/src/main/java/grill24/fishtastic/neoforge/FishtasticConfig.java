package grill24.fishtastic.neoforge;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.InMemoryFormat;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Map;

public final class FishtasticConfig {
    /**
     * The config defines blacklisted blocks for fish tank parts and model path overrides.
     */
    public static class Startup {
        public final ModConfigSpec.ConfigValue<List<? extends Config>> fishTankPartBlacklists;
        public final ModConfigSpec.ConfigValue<List<? extends Config>> blockModelPathOverrides;

        Startup(ModConfigSpec.Builder builder) {
            //TODO: See if/how this can be made to work with the config GUI

            var emptyConfig = Config.wrap(Map.of(), InMemoryFormat.defaultInstance());
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
                    .gameRestart()
                    .defineList("fishTankPartBlacklists", () ->
                    {
                        var frameBlacklist = Config.wrap(Map.of(
                            "parts", List.of("frame", "glass", "sand"),
                            "blocks", List.of("fishtastic:fish_tank")
                        ), InMemoryFormat.defaultInstance());

                        return List.of(frameBlacklist);
                    }, null, o -> o instanceof Config);

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
                    .gameRestart()
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
                    }, null, o -> o instanceof Config);
        }
    }

    public static final Startup STARTUP;
    private static final ModConfigSpec startupSpec;

    static {
        var startupPair = new ModConfigSpec.Builder().configure(Startup::new);
        STARTUP = startupPair.getLeft();
        startupSpec = startupPair.getRight();
    }

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.STARTUP, startupSpec);
    }
}
