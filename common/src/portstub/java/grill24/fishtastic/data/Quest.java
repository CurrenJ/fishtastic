package grill24.fishtastic.data;

/**
 * Portstub (gradle/port-excludes.gradle): temporary stand-in for the real {@code Quest} record,
 * which pulls in {@code QuestObjective} -> {@code FishProfile} -> {@code FishtasticRegistries} ->
 * most of the data-registry graph — well beyond B2.1's item-data scope. Only the two accessors
 * {@link grill24.fishtastic.fishtank.FishTankShapeUnlocks} actually calls are kept. Real file still
 * exists (excluded) at data/Quest.java; delete this stub once that graph is ported for real.
 */
public record Quest(QuestCategory category, QuestReward reward) {}
