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
     * The config defines which block tags to use for pre-generating fish tank models.
     * Any block can be used as a frame at runtime, but blocks in these tags will have
     * their models pre-generated during resource loading for better performance.
     */
    public static class Startup {
        public final ModConfigSpec.ConfigValue<List<? extends Config>> customFishTankFrameTypes;
        public final ModConfigSpec.ConfigValue<List<? extends Config>> blockModelPathOverrides;

        Startup(ModConfigSpec.Builder builder) {
            //TODO: See if/how this can be made to work with the config GUI

            //The default value will be a list containing an empty table, rather than an empty list, to make the TOML syntax for lists of tables clearer to users.
            //The default TOML file will then contain "[[customFishTankFrameTypes]]" rather than "customFishTankFrameTypes = []".
            var emptyConfig = Config.wrap(Map.of(), InMemoryFormat.defaultInstance());
            customFishTankFrameTypes = builder
                    .comment("""
                           
                            Block tags for pre-generating fish tank models.
                            Blocks in these tags will have models generated at startup for better performance.
                            Any block can be used as a frame, but non-configured blocks generate models on-demand.
                            
                            Example entry:
                            [[customFishTankFrameTypes]]
                                id = "my_mod:crimson"
                                blocks = "#minecraft:crimson_stems"
                                """)
                    .translation("fishtastic.config.customFishTankFrameTypes")
                    .gameRestart()
                    .defineList("customFishTankFrameTypes", () -> List.of(emptyConfig), null, o -> o instanceof Config);

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
