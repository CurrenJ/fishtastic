package grill24.fishtastic.util;

import grill24.fishtastic.server.FishSchoolManager;
import org.jetbrains.annotations.Nullable;

/**
 * The level's fish-school manager, held on the {@code ServerLevel} itself so the block-change hook
 * reaches it with one field read (performance plan step A). Not a static weak map: its values
 * would retain their level keys (see {@code FishSchoolSavedData}).
 */
public interface IServerLevelSchools {
    @Nullable FishSchoolManager fishtastic$schools();

    void fishtastic$setSchools(FishSchoolManager schools);
}
