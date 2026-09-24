package grill24.fishtastic.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.function.Predicate;

/**
 * Permission requirements for Fishtastic's commands, for use in {@code .requires(...)}.
 *
 * <p>The permission API changed in 1.21.11 (permission sets instead of integer op levels), so
 * this is the one place that differs per MC version. 1.21.1 and 1.20.1 return
 * {@code src -> src.hasPermission(Commands.LEVEL_GAMEMASTERS)}.
 */
public final class FishtasticPermissions {
    private FishtasticPermissions() {}

    /**
     * Op level 2 (gamemaster), the level every Fishtastic admin and debug command requires.
     *
     * <p>Returns vanilla's own {@code PermissionProviderCheck} rather than a lambda: vanilla
     * recognises that type on command nodes ({@code ArgumentUtils}), so wrapping it would change
     * behaviour.
     */
    public static Predicate<CommandSourceStack> gamemaster() {
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
    }
}
