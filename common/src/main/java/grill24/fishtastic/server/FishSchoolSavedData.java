package grill24.fishtastic.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.ecology.VisitId;
import grill24.fishtastic.ecology.VisitWeatherLog;
import grill24.fishtastic.data.FishMoonPhase;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.item.FishtasticFishItem;
import grill24.fishtastic.school.VisitShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.UUID;

/**
 * Shared fish-school visits, and the one thing about them that is saved: the depletion ledger
 * ({@code VisitId -> caught-member bitmask}, world fish exploration E1/E14, implementation plan
 * Step 2). A visit is derived from the world seed, its site and its window, so the schools in
 * {@link #schools()} are live runtime state only - never written, and derived again when a player
 * returns - and only the members already caught need to outlive a reload. Entries are pruned once
 * their window can no longer be revealed. Saves from before the rework carried schools and region
 * schedules; those fields are ignored on load and gone with the next save (planning decision P4).
 * The bounded recent weather log is saved alongside depletion: world input needed to derive a
 * window under its start weather, rather than saved visit state (the accepted exception to E1).
 */
public final class FishSchoolSavedData extends SavedData {
    public enum SchoolState implements StringRepresentable {
        ACTIVE, DEPARTING;
        public static final Codec<SchoolState> CODEC = StringRepresentable.fromEnum(SchoolState::values);
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public enum MemberState implements StringRepresentable {
        AVAILABLE, APPROACHING, BITE_READY, IN_SESSION, RECOVERING, DEPARTING, CAUGHT, DEPARTED;
        public static final Codec<MemberState> CODEC = StringRepresentable.fromEnum(MemberState::values);
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public record Position(double x, double y, double z, float yaw) {
        public static Position at(BlockPos pos) {
            return new Position(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0f);
        }
    }

    /** Current formation context is explanatory only; later soft-weight changes do not reroll a visit. */
    public record FormationSnapshot(ResourceLocation biome, FishProfile.TimeOfDay timeOfDay,
                                    FishProfile.WeatherCondition weather, FishMoonPhase moon,
                                    List<String> zones) {
    }

    /** A hook's claim on a member. In memory only: hooks and minigame clients do not survive a restart. */
    public record Reservation(UUID playerId, UUID hookId, long startedAt, long leaseUntil,
                              int sessionId, int targetIndex) {
    }

    public static final class Member {
        private final UUID id;
        private final long rewardSeed;
        /** The member's index in its visit's derivation (its ledger bit), or -1 for a member not derived from a window. */
        private final int index;
        /** The game tick its seeded departure falls on (E11), or -1 for a member that only leaves with its visit. */
        private final long departAt;
        /** When a member sent away on its own is gone, or 0. */
        private long fadeUntil;
        private Position position;
        private long recoveryUntil;
        private MemberState state;
        private Reservation reservation;

        private Member(UUID id, long rewardSeed, int index, long departAt, Position position,
                       long recoveryUntil, MemberState state, Optional<Reservation> reservation) {
            this.id = id;
            this.rewardSeed = rewardSeed;
            this.index = index;
            this.departAt = departAt;
            this.position = position;
            this.recoveryUntil = recoveryUntil;
            this.state = state;
            this.reservation = reservation.orElse(null);
        }

        public UUID id() { return id; }
        public long rewardSeed() { return rewardSeed; }
        public int index() { return index; }
        public long departAt() { return departAt; }
        public long fadeUntil() { return fadeUntil; }
        /** This fish's quality for its whole visit: its trail shows it, and catching it gives it. */
        public FishQuality.Quality quality() { return qualityOf(rewardSeed); }
        /** Where the school's sim last had this member (frozen while it is out of the water). */
        public Position position() { return position; }
        public long recoveryUntil() { return recoveryUntil; }
        public MemberState state() { return state; }
        public Reservation reservation() { return reservation; }

        void setPosition(Position value) { position = value; }
        void setFadeUntil(long value) { fadeUntil = value; }
        void setRecoveryUntil(long value) { recoveryUntil = value; }
        void setState(MemberState value) { state = value; }
        void setReservation(Reservation value) { reservation = value; }
    }

    public static final class School {
        private final UUID id;
        /** The window this visit is the derivation of, or null for a visit that is not (debug spawns, test fixtures). */
        private final @Nullable VisitId visitId;
        private final ResourceLocation species;
        private final BlockPos anchor;
        private final long createdAt;
        private final FormationSnapshot formation;
        private final VisitShape shape;
        private final List<Member> members;
        private long activeUntil;
        private long departureUntil;
        private SchoolState state;
        /** Game time the visit may next roll a surface jump; 0 until the first roll is scheduled. */
        private long nextSurfaceJumpAt;
        /** Of a derived visit's roster: how many had left, and how many were caught, before it was revealed. */
        private int departedAtReveal, caughtAtReveal;

        private School(UUID id, @Nullable VisitId visitId, ResourceLocation species, BlockPos anchor, long createdAt,
                       long activeUntil, long departureUntil, SchoolState state, FormationSnapshot formation,
                       VisitShape shape, List<Member> members, long nextSurfaceJumpAt) {
            this.id = id;
            this.visitId = visitId;
            this.species = species;
            this.anchor = anchor;
            this.createdAt = createdAt;
            this.activeUntil = activeUntil;
            this.departureUntil = departureUntil;
            this.state = state;
            this.formation = formation;
            this.shape = shape;
            this.members = new ArrayList<>(members);
            this.nextSurfaceJumpAt = nextSurfaceJumpAt;
        }

        public UUID id() { return id; }
        public @Nullable VisitId visitId() { return visitId; }
        public ResourceLocation species() { return species; }
        public BlockPos anchor() { return anchor; }
        public long createdAt() { return createdAt; }
        public long activeUntil() { return activeUntil; }
        public long departureUntil() { return departureUntil; }
        public SchoolState state() { return state; }
        public FormationSnapshot formation() { return formation; }
        /** How the visit arrived: alone, a pair, a loose group or a school. */
        public VisitShape shape() { return shape; }
        public List<Member> members() { return members; }
        public long nextSurfaceJumpAt() { return nextSurfaceJumpAt; }
        public int departedAtReveal() { return departedAtReveal; }
        public int caughtAtReveal() { return caughtAtReveal; }

        void setState(SchoolState value) { state = value; }
        void setDepartureUntil(long value) { departureUntil = value; }
        void setNextSurfaceJumpAt(long value) { nextSurfaceJumpAt = value; }
    }

    /** Small test-fixture description; public because loader GameTests compile in a separate module. */
    public record MemberFixture(long rewardSeed, Position position, MemberState state,
                                @Nullable Reservation reservation) {}

    /** One ledger row: the members caught so far of one window of one site (bit {@code i} = member {@code i}). */
    public record DepletionEntry(int chunkX, int chunkZ, long window, long caughtMask) {
        public static final Codec<DepletionEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x").forGetter(DepletionEntry::chunkX),
                Codec.INT.fieldOf("z").forGetter(DepletionEntry::chunkZ),
                Codec.LONG.fieldOf("window").forGetter(DepletionEntry::window),
                Codec.LONG.fieldOf("caught").forGetter(DepletionEntry::caughtMask)
        ).apply(i, DepletionEntry::new));
    }

    private record LedgerKey(int chunkX, int chunkZ, long window) {
        static LedgerKey of(VisitId id) {
            return new LedgerKey(id.chunkX(), id.chunkZ(), id.window());
        }
    }

    public static final Codec<FishSchoolSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
            DepletionEntry.CODEC.listOf().optionalFieldOf("depletion", List.of()).forGetter(FishSchoolSavedData::depletionEntries),
            VisitWeatherLog.Entry.CODEC.listOf().optionalFieldOf("weather", List.of()).forGetter(FishSchoolSavedData::weatherEntries)
    ).apply(i, FishSchoolSavedData::new));

    public static final String FILE_ID = "fishtastic_fish_schools";
    public static final SavedData.Factory<FishSchoolSavedData> FACTORY =
            new SavedData.Factory<>(FishSchoolSavedData::new, FishSchoolSavedData::load, null);

    private static FishSchoolSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        return CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                .resultOrPartial(error -> Fishtastic.LOGGER.error("Failed to load fish-school data: {}", error))
                .orElseGet(FishSchoolSavedData::new);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this)
                .resultOrPartial(error -> Fishtastic.LOGGER.error("Failed to save fish-school data: {}", error))
                .ifPresent(encoded -> tag.merge((CompoundTag) encoded));
        return tag;
    }

    /** Live visits: in memory only (see the class comment). */
    private final List<School> schools = new ArrayList<>();
    private final Map<LedgerKey, Long> depletion = new HashMap<>();
    private VisitWeatherLog weatherLog;
    // Runtime-only service state belongs to this world's SavedData lifetime. Keeping it here
    // avoids a process-wide weak-key cache whose values would retain their ServerLevel keys.
    private transient FishSchoolManager manager;

    public FishSchoolSavedData() {
        this(List.of(), List.of());
    }

    private FishSchoolSavedData(List<DepletionEntry> depletion, List<VisitWeatherLog.Entry> weather) {
        this.weatherLog = new VisitWeatherLog(weather);
        for (DepletionEntry entry : depletion) {
            if (entry.caughtMask() != 0L) {
                this.depletion.merge(new LedgerKey(entry.chunkX(), entry.chunkZ(), entry.window()), entry.caughtMask(),
                        (a, b) -> a | b);
            }
        }
    }

    public static FishSchoolSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    FishSchoolManager manager(ServerLevel level) {
        if (manager == null) manager = new FishSchoolManager(level);
        return manager;
    }

    List<School> schools() { return schools; }

    /** Test-only fixture seam used by the shared GameTest suite across all loaders. */
    public School addSchoolForTest(ResourceLocation species, BlockPos anchor, long createdAt, long activeUntil,
                                   FormationSnapshot formation, List<MemberFixture> fixtures) {
        List<Member> members = new ArrayList<>(fixtures.size());
        for (MemberFixture fixture : fixtures) {
            members.add(new Member(UUID.randomUUID(), fixture.rewardSeed(), -1, -1L, fixture.position(),
                    0L, fixture.state(), Optional.ofNullable(fixture.reservation())));
        }
        School school = newSchool(species, anchor, createdAt, activeUntil, formation, VisitShape.SCHOOL, members);
        addSchool(school);
        return school;
    }

    /** Test-only transition seam; production transitions remain owned by {@link FishSchoolManager}. */
    public void setMemberForTest(Member member, MemberState state, @Nullable Reservation reservation) {
        boolean owned = schools.stream().flatMap(school -> school.members().stream()).anyMatch(candidate -> candidate == member);
        if (!owned) throw new IllegalArgumentException("Member does not belong to this fish school data");
        member.setState(state);
        member.setReservation(reservation);
    }

    public List<School> schoolsForTest() { return List.copyOf(schools); }

    // Schools are runtime state (derived again on reveal), not saved, so changing them leaves the
    // file clean; only the depletion ledger and the weather log mark it dirty.
    void addSchool(School school) {
        schools.add(school);
    }

    void removeSchool(School school) {
        schools.remove(school);
    }

    /** The members of this window already caught, as a bitmask by member index (0 when none). */
    public long caughtMask(VisitId id) {
        return depletion.getOrDefault(LedgerKey.of(id), 0L);
    }

    /** Records that member {@code member} of the window was caught; saved, and the only thing about a visit that is. */
    public void markCaught(VisitId id, int member) {
        if (member < 0 || member >= Long.SIZE) return;
        depletion.merge(LedgerKey.of(id), 1L << member, (a, b) -> a | b);
        setDirty();
    }

    /** Drops the ledger rows for which {@code ended} holds (their window can no longer be revealed). */
    int pruneDepletion(Predicate<DepletionEntry> ended) {
        int before = depletion.size();
        if (depletion.entrySet().removeIf(e -> ended.test(entry(e)))) setDirty();
        return before - depletion.size();
    }

    int depletionSize() { return depletion.size(); }

    private static DepletionEntry entry(Map.Entry<LedgerKey, Long> e) {
        return new DepletionEntry(e.getKey().chunkX(), e.getKey().chunkZ(), e.getKey().window(), e.getValue());
    }

    private List<DepletionEntry> depletionEntries() {
        List<DepletionEntry> out = new ArrayList<>(depletion.size());
        for (Map.Entry<LedgerKey, Long> e : depletion.entrySet()) out.add(entry(e));
        out.sort(java.util.Comparator.comparingInt(DepletionEntry::chunkX).thenComparingInt(DepletionEntry::chunkZ)
                .thenComparingLong(DepletionEntry::window));
        return out;
    }

    public List<DepletionEntry> depletionForTest() { return depletionEntries(); }

    public List<VisitWeatherLog.Entry> weatherEntries() { return weatherLog.entries(); }

    public VisitWeatherLog.Sample weatherAt(long gameTime, VisitWeatherLog.State current) {
        return weatherLog.at(gameTime, current);
    }

    public void recordWeather(long gameTime, VisitWeatherLog.State state) {
        if (weatherLog.record(gameTime, state)) setDirty();
    }

    public int pruneWeather(long cutoff) {
        int removed = weatherLog.prune(cutoff);
        if (removed != 0) setDirty();
        return removed;
    }

    /** Test seam: restores the dimension's log after a synchronous weather fixture. */
    public void replaceWeatherForTest(List<VisitWeatherLog.Entry> entries) {
        weatherLog = new VisitWeatherLog(entries);
        setDirty();
    }

    /**
     * A school fish's quality, from its reward seed: settled when the school forms rather than when
     * the fish is caught, so the trail can show it beforehand. Derived rather than stored, so a
     * school saved before qualities existed gets them too. Unbiased: bait and hook quality bias
     * apply to ordinary casts only, or a fish's trail would not match what catching it gives.
     */
    public static FishQuality.Quality qualityOf(long rewardSeed) {
        return FishtasticFishItem.sampleRandomQuality(RandomSource.create(rewardSeed ^ QUALITY_SALT));
    }

    private static final long QUALITY_SALT = 0x51A11E7C0FFEE5EDL;

    static Member newMember(long seed, Position position) {
        return new Member(UUID.randomUUID(), seed, -1, -1L, position, 0L, MemberState.AVAILABLE, Optional.empty());
    }

    /** A member derived from a window: a fixed id and index, so the same fish is the same fish on every reveal. */
    static Member derivedMember(UUID id, int index, long seed, long departAt, Position position) {
        return new Member(id, seed, index, departAt, position, 0L, MemberState.AVAILABLE, Optional.empty());
    }

    static School newSchool(ResourceLocation species, BlockPos anchor, long createdAt, long activeUntil,
                            FormationSnapshot formation, VisitShape shape, List<Member> members) {
        return new School(UUID.randomUUID(), null, species, anchor, createdAt, activeUntil, 0L,
                SchoolState.ACTIVE, formation, shape, members, 0L);
    }

    /** A visit derived from {@code visitId}: its school id is fixed too. */
    static School derivedSchool(UUID id, VisitId visitId, ResourceLocation species, BlockPos anchor, long createdAt,
                                long activeUntil, FormationSnapshot formation, VisitShape shape, List<Member> members,
                                int departedAtReveal, int caughtAtReveal) {
        School school = new School(id, visitId, species, anchor, createdAt, activeUntil, 0L,
                SchoolState.ACTIVE, formation, shape, members, 0L);
        school.departedAtReveal = departedAtReveal;
        school.caughtAtReveal = caughtAtReveal;
        return school;
    }

    public static FormationSnapshot snapshot(FishingPoolResolver.EnvironmentContext env) {
        ResourceLocation biome = env.biome().unwrapKey().map(key -> key.location()).orElse(Fishtastic.id("unknown_biome"));
        return new FormationSnapshot(biome, env.timeOfDay(), env.weather(), env.moonPhase(),
                env.zones().stream().map(zone -> zone.getSerializedName()).sorted().toList());
    }
}
