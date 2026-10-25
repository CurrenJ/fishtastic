package grill24.fishtastic.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.data.FishMoonPhase;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.item.FishtasticFishItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.saveddata.SavedData;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Persisted, shared fish-school visits and their bounded encounter schedule. */
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
        public static final Codec<Position> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("x").forGetter(Position::x),
                Codec.DOUBLE.fieldOf("y").forGetter(Position::y),
                Codec.DOUBLE.fieldOf("z").forGetter(Position::z),
                Codec.FLOAT.optionalFieldOf("yaw", 0f).forGetter(Position::yaw)
        ).apply(i, Position::new));

        public static Position at(BlockPos pos) {
            return new Position(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0f);
        }
    }

    /** Current formation context is explanatory only; later soft-weight changes do not reroll a visit. */
    public record FormationSnapshot(ResourceLocation biome, FishProfile.TimeOfDay timeOfDay,
                                    FishProfile.WeatherCondition weather, FishMoonPhase moon,
                                    List<String> zones) {
        public static final Codec<FormationSnapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("biome").forGetter(FormationSnapshot::biome),
                FishProfile.TimeOfDay.CODEC.fieldOf("time").forGetter(FormationSnapshot::timeOfDay),
                FishProfile.WeatherCondition.CODEC.fieldOf("weather").forGetter(FormationSnapshot::weather),
                FishMoonPhase.CODEC.fieldOf("moon").forGetter(FormationSnapshot::moon),
                Codec.STRING.listOf().optionalFieldOf("zones", List.of()).forGetter(FormationSnapshot::zones)
        ).apply(i, FormationSnapshot::new));
    }

    /** Reservation identity is retained in-session, but transient reservations are cleared on reload. */
    public record Reservation(UUID playerId, UUID hookId, long startedAt, long leaseUntil,
                              int sessionId, int targetIndex) {
        public static final Codec<Reservation> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("player").forGetter(Reservation::playerId),
                UUIDUtil.STRING_CODEC.fieldOf("hook").forGetter(Reservation::hookId),
                Codec.LONG.fieldOf("started").forGetter(Reservation::startedAt),
                Codec.LONG.fieldOf("lease_until").forGetter(Reservation::leaseUntil),
                Codec.INT.optionalFieldOf("session", 0).forGetter(Reservation::sessionId),
                Codec.INT.optionalFieldOf("target", -1).forGetter(Reservation::targetIndex)
        ).apply(i, Reservation::new));
    }

    public static final class Member {
        private final UUID id;
        private final long rewardSeed;
        private Position position;
        private long recoveryUntil;
        private MemberState state;
        private Reservation reservation;

        private Member(UUID id, long rewardSeed, Position position,
                       long recoveryUntil, MemberState state, Optional<Reservation> reservation) {
            this.id = id;
            this.rewardSeed = rewardSeed;
            this.position = position;
            this.recoveryUntil = recoveryUntil;
            this.state = state;
            this.reservation = reservation.orElse(null);
        }

        public UUID id() { return id; }
        public long rewardSeed() { return rewardSeed; }
        /** This fish's quality for its whole visit: its trail shows it, and catching it gives it. */
        public FishQuality.Quality quality() { return qualityOf(rewardSeed); }
        /** Where the school's sim last had this member (frozen while it is out of the water). */
        public Position position() { return position; }
        public long recoveryUntil() { return recoveryUntil; }
        public MemberState state() { return state; }
        public Reservation reservation() { return reservation; }

        void setPosition(Position value) { position = value; }
        void setRecoveryUntil(long value) { recoveryUntil = value; }
        void setState(MemberState value) { state = value; }
        void setReservation(Reservation value) { reservation = value; }
    }

    public static final class School {
        private final UUID id;
        private final ResourceLocation species;
        private final BlockPos anchor;
        private final long createdAt;
        private final FormationSnapshot formation;
        private final List<Member> members;
        private long activeUntil;
        private long departureUntil;
        private SchoolState state;

        private School(UUID id, ResourceLocation species, BlockPos anchor, long createdAt, long activeUntil,
                       long departureUntil, SchoolState state, FormationSnapshot formation, List<Member> members) {
            this.id = id;
            this.species = species;
            this.anchor = anchor;
            this.createdAt = createdAt;
            this.activeUntil = activeUntil;
            this.departureUntil = departureUntil;
            this.state = state;
            this.formation = formation;
            this.members = new ArrayList<>(members);
        }

        public UUID id() { return id; }
        public ResourceLocation species() { return species; }
        public BlockPos anchor() { return anchor; }
        public long createdAt() { return createdAt; }
        public long activeUntil() { return activeUntil; }
        public long departureUntil() { return departureUntil; }
        public SchoolState state() { return state; }
        public FormationSnapshot formation() { return formation; }
        public List<Member> members() { return members; }

        void setState(SchoolState value) { state = value; }
        void setDepartureUntil(long value) { departureUntil = value; }
    }

    public record RegionSchedule(int x, int z, long nextAttemptAt, long lastSeenAt) {
        public static final Codec<RegionSchedule> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x").forGetter(RegionSchedule::x),
                Codec.INT.fieldOf("z").forGetter(RegionSchedule::z),
                Codec.LONG.fieldOf("next_attempt").forGetter(RegionSchedule::nextAttemptAt),
                Codec.LONG.fieldOf("last_seen").forGetter(RegionSchedule::lastSeenAt)
        ).apply(i, RegionSchedule::new));
    }

    /** Small test-fixture description; public because loader GameTests compile in a separate module. */
    public record MemberFixture(long rewardSeed, Position position, MemberState state,
                                @Nullable Reservation reservation) {}

    private static final Codec<Member> MEMBER_CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(Member::id),
            Codec.LONG.fieldOf("reward_seed").forGetter(Member::rewardSeed),
            Position.CODEC.fieldOf("position").forGetter(Member::position),
            Codec.LONG.optionalFieldOf("recovery_until", 0L).forGetter(Member::recoveryUntil),
            MemberState.CODEC.optionalFieldOf("state", MemberState.AVAILABLE).forGetter(Member::state),
            Reservation.CODEC.optionalFieldOf("reservation").forGetter(m -> Optional.ofNullable(m.reservation()))
    ).apply(i, Member::new));

    private static final Codec<School> SCHOOL_CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(School::id),
            ResourceLocation.CODEC.fieldOf("species").forGetter(School::species),
            BlockPos.CODEC.fieldOf("anchor").forGetter(School::anchor),
            Codec.LONG.fieldOf("created_at").forGetter(School::createdAt),
            Codec.LONG.fieldOf("active_until").forGetter(School::activeUntil),
            Codec.LONG.optionalFieldOf("departure_until", 0L).forGetter(School::departureUntil),
            SchoolState.CODEC.optionalFieldOf("state", SchoolState.ACTIVE).forGetter(School::state),
            FormationSnapshot.CODEC.fieldOf("formation").forGetter(School::formation),
            MEMBER_CODEC.listOf().fieldOf("members").forGetter(School::members)
    ).apply(i, School::new));

    public static final Codec<FishSchoolSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
            SCHOOL_CODEC.listOf().optionalFieldOf("schools", List.of()).forGetter(d -> d.schools),
            RegionSchedule.CODEC.listOf().optionalFieldOf("regions", List.of()).forGetter(d -> d.regions)
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

    private final List<School> schools;
    private final List<RegionSchedule> regions;
    // Runtime-only service state belongs to this world's SavedData lifetime. Keeping it here
    // avoids a process-wide weak-key cache whose values would retain their ServerLevel keys.
    private transient FishSchoolManager manager;

    public FishSchoolSavedData() {
        this(List.of(), List.of());
    }

    private FishSchoolSavedData(List<School> schools, List<RegionSchedule> regions) {
        this.schools = new ArrayList<>(schools);
        this.regions = new ArrayList<>(regions);
        recoverTransientReservations();
    }

    public static FishSchoolSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    FishSchoolManager manager(ServerLevel level) {
        if (manager == null) manager = new FishSchoolManager(level);
        return manager;
    }

    List<School> schools() { return schools; }
    List<RegionSchedule> regions() { return regions; }

    /** Test-only fixture seam used by the shared GameTest suite across all loaders. */
    public School addSchoolForTest(ResourceLocation species, BlockPos anchor, long createdAt, long activeUntil,
                                   FormationSnapshot formation, List<MemberFixture> fixtures) {
        List<Member> members = new ArrayList<>(fixtures.size());
        for (MemberFixture fixture : fixtures) {
            members.add(new Member(UUID.randomUUID(), fixture.rewardSeed(), fixture.position(),
                    0L, fixture.state(), Optional.ofNullable(fixture.reservation())));
        }
        School school = newSchool(species, anchor, createdAt, activeUntil, formation, members);
        addSchool(school);
        return school;
    }

    /** Test-only transition seam; production transitions remain owned by {@link FishSchoolManager}. */
    public void setMemberForTest(Member member, MemberState state, @Nullable Reservation reservation) {
        boolean owned = schools.stream().flatMap(school -> school.members().stream()).anyMatch(candidate -> candidate == member);
        if (!owned) throw new IllegalArgumentException("Member does not belong to this fish school data");
        member.setState(state);
        member.setReservation(reservation);
        setDirty();
    }

    public List<School> schoolsForTest() { return List.copyOf(schools); }
    public List<RegionSchedule> regionsForTest() { return List.copyOf(regions); }
    public void setRegionForTest(RegionSchedule schedule) { setRegion(schedule); }

    void addSchool(School school) {
        schools.add(school);
        setDirty();
    }

    void removeSchool(School school) {
        if (schools.remove(school)) setDirty();
    }

    void setRegion(RegionSchedule schedule) {
        for (int i = 0; i < regions.size(); i++) {
            RegionSchedule old = regions.get(i);
            if (old.x() == schedule.x() && old.z() == schedule.z()) {
                regions.set(i, schedule);
                setDirty();
                return;
            }
        }
        regions.add(schedule);
        setDirty();
    }

    void markDirtyNow() { setDirty(); }

    private void recoverTransientReservations() {
        boolean changed = false;
        for (School school : schools) {
            for (Member member : school.members) {
                if (member.state == MemberState.APPROACHING || member.state == MemberState.BITE_READY
                        || member.state == MemberState.IN_SESSION) {
                    // Hooks and minigame clients are not restored across restart. A member recovers
                    // into its original visit instead of becoming duplicated or permanently locked.
                    member.state = MemberState.RECOVERING;
                    member.recoveryUntil = 0;
                    member.reservation = null;
                    changed = true;
                }
            }
        }
        if (changed) setDirty();
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
        return new Member(UUID.randomUUID(), seed, position, 0L,
                MemberState.AVAILABLE, Optional.empty());
    }

    static School newSchool(ResourceLocation species, BlockPos anchor, long createdAt, long activeUntil,
                            FormationSnapshot formation, List<Member> members) {
        return new School(UUID.randomUUID(), species, anchor, createdAt, activeUntil, 0L,
                SchoolState.ACTIVE, formation, members);
    }

    public static FormationSnapshot snapshot(FishingPoolResolver.EnvironmentContext env) {
        ResourceLocation biome = env.biome().unwrapKey().map(key -> key.location()).orElse(Fishtastic.id("unknown_biome"));
        return new FormationSnapshot(biome, env.timeOfDay(), env.weather(), env.moonPhase(),
                env.zones().stream().map(zone -> zone.getSerializedName()).sorted().toList());
    }
}
