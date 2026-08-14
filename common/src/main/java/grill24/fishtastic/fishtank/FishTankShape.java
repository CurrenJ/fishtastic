package grill24.fishtastic.fishtank;

import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.data.Quest;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Identifies the fish tank's body geometry — the shape/silhouette of the frame, sand, and glass
 * elements — independent of which blocks texture them ({@link grill24.fishtastic.component.FishTankMaterials})
 * and independent of which other tanks it's willing to connect to ({@link #connectionCollection()}).
 *
 * <p>A code-defined enum (matching {@link FishTankFrameType}'s minimalism) rather than a dynamic
 * registry: shapes are curated content shipped with the mod, not something datapacks need to add.
 */
public enum FishTankShape implements TooltipProvider {
    STANDARD(Fishtastic.id("standard"), Fishtastic.id("standard"), "fishtankbase"),
    /**
     * Standard body with a skylight: the solid ceiling is replaced by a frame ring and a horizontal
     * glass pane mirroring the square sand footprint (see TaperedFrameGeometryGenerator#generateSkylight /
     * TaperedGlassGeometryGenerator#generateSkylight). Shares STANDARD's connectionCollection and is
     * ungated, like STANDARD.
     */
    SKYLIGHT(Fishtastic.id("skylight"), Fishtastic.id("standard"), "fishtank_skylight"),
    /**
     * Light corner brace — modest 3px→1px taper. See CornerTaperProfile.TRIMMED (tools/tank-shape-gen).
     * Shares STANDARD's connectionCollection by deliberate curation (not the default-to-self
     * behavior) — all three shipped shapes are meant to interconnect with each other.
     * Locked until {@code mastery/angler_apprentice} or {@code mastery/gar_hunter} is claimed —
     * see {@link #unlockQuests}.
     */
    TRIMMED(Fishtastic.id("trimmed"), Fishtastic.id("standard"), "fishtank_trimmed",
            List.of(ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Fishtastic.id("mastery/angler_apprentice")),
                    ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Fishtastic.id("mastery/gar_hunter")))),
    /**
     * Chunkier corner brackets — deeper 5px→1px taper. See CornerTaperProfile.REINFORCED.
     * Shares STANDARD's connectionCollection — see {@link #TRIMMED}'s note.
     * Locked until {@code mastery/angler_journeyman} or {@code mastery/tetra_scholar} is claimed —
     * see {@link #unlockQuests}.
     */
    REINFORCED(Fishtastic.id("reinforced"), Fishtastic.id("standard"), "fishtank_reinforced",
            List.of(ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Fishtastic.id("mastery/angler_journeyman")),
                    ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Fishtastic.id("mastery/tetra_scholar")))),
    /**
     * REINFORCED's deeper sibling — a 6px→1px taper with no full-width rows (see
     * CornerTaperProfile.HONED). Shares STANDARD's connectionCollection — see {@link #TRIMMED}'s
     * note. Locked until {@code mastery/angler_master} or {@code mastery/bluegill_master} is
     * claimed — see {@link #unlockQuests}.
     */
    HONED(Fishtastic.id("honed"), Fishtastic.id("standard"), "fishtank_honed",
            List.of(ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Fishtastic.id("mastery/angler_master")),
                    ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Fishtastic.id("mastery/bluegill_master")))),
    /**
     * STANDARD's chunky hard-edged sibling: a uniform 2px mid-body with chamfered octagonal cap
     * rings (see CornerTaperProfile.STURDY). Shares STANDARD's connectionCollection — see
     * {@link #TRIMMED}'s note. Locked until {@code tutorial/first_catch} or
     * {@code mastery/bluegill_novice} is claimed — see {@link #unlockQuests}.
     */
    STURDY(Fishtastic.id("sturdy"), Fishtastic.id("standard"), "fishtank_sturdy",
            List.of(ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Fishtastic.id("tutorial/first_catch")),
                    ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Fishtastic.id("mastery/bluegill_novice")))),
    /**
     * A 16px→2px corner taper with 2px-thick chamfered octagonal cap rings and a stepped-octagon
     * sand — BASTION's thinner-stepped sibling (see CornerTaperProfile.FACETED and
     * SteppedSandGeometryGenerator). Shares STANDARD's connectionCollection — all shipped
     * shapes are meant to freely interconnect (see {@link #TRIMMED}'s note).
     * Locked until {@code challenge/sunrise_ambush} or {@code mastery/gar_veteran} is claimed —
     * see {@link #unlockQuests}.
     */
    FACETED(Fishtastic.id("faceted"), Fishtastic.id("standard"), "fishtank_faceted",
            List.of(ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Fishtastic.id("challenge/sunrise_ambush")),
                    ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Fishtastic.id("mastery/gar_veteran")))),
    /**
     * 16px→2px taper with 2px-thick caps, whose {@code 16} rows are chamfered octagonal base rings
     * and whose sand is a stepped octagon (see CornerTaperProfile.BASTION). Shares STANDARD's
     * connectionCollection like {@link #FACETED}.
     * Locked until {@code explorer/nether_collector} or {@code challenge/predator_run} is claimed —
     * see {@link #unlockQuests}.
     */
    BASTION(Fishtastic.id("bastion"), Fishtastic.id("standard"), "fishtank_bastion",
            List.of(ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Fishtastic.id("explorer/nether_collector")),
                    ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Fishtastic.id("challenge/predator_run")))),
    /**
     * BASTION's deeper sibling — a 16px→2px taper whose full-width cap rows taper one pixel
     * further out (7 vs BASTION's 6) for a tighter frame circle (see CornerTaperProfile.RAMPART).
     * Shares STANDARD's connectionCollection like {@link #FACETED}.
     * Locked until {@code mastery/angler_legend} or {@code mastery/gar_legend} is claimed —
     * see {@link #unlockQuests}.
     */
    RAMPART(Fishtastic.id("rampart"), Fishtastic.id("standard"), "fishtank_rampart",
            List.of(ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Fishtastic.id("mastery/angler_legend")),
                    ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Fishtastic.id("mastery/gar_legend")))),
    /**
     * Ornate tank: standard 1px frame plus decorative 1px inlay brackets on each face, with a
     * standard sand and a glass pane shaped around the brackets (see OrnateFrameGeometryGenerator /
     * OrnateGlassGeometryGenerator). Shares STANDARD's connectionCollection like {@link #FACETED}.
     * Locked until {@code challenge/daily_completionist} or {@code mastery/tetra_legend} is
     * claimed — see {@link #unlockQuests}.
     */
    ORNATE(Fishtastic.id("ornate"), Fishtastic.id("standard"), "fishtank_ornate",
            List.of(ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Fishtastic.id("challenge/daily_completionist")),
                    ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Fishtastic.id("mastery/tetra_legend")))),
    /**
     * Shaggy tank: the ornate tank's construction — standard 1px frame plus 1px decorative inlays
     * inset into the glass layer — with a shaggier, deliberately asymmetric fringe and a full-width
     * band at Y [1,2] that hides the sand from the side (see ShaggyFrameGeometryGenerator /
     * ShaggyGlassGeometryGenerator, spans in ShaggyTankSpans). Shares STANDARD's
     * connectionCollection like {@link #FACETED}.
     * Locked until {@code challenge/storm_prize} or {@code mastery/tetra_tracker} is claimed —
     * see {@link #unlockQuests}.
     */
    SHAGGY(Fishtastic.id("shaggy"), Fishtastic.id("standard"), "fishtank_shaggy",
            List.of(ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Fishtastic.id("challenge/storm_prize")),
                    ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Fishtastic.id("mastery/tetra_tracker")))),
    /**
     * Bramble tank: no plain corner post at all — every interior Y band carries its own thorny,
     * asymmetric inlay pattern read off {@code docs/tank-shapes/bramble_shape_*.png} (see
     * BrambleFrameGeometryGenerator / BrambleGlassGeometryGenerator, spans in BrambleTankSpans).
     * Shares STANDARD's connectionCollection like {@link #FACETED}.
     * Locked until {@code explorer/jungle_downpour} is claimed — unlike every other shape's
     * any-one-of-two gating, bramble is deliberately tied to this single quest — see
     * {@link #unlockQuests}.
     */
    BRAMBLE(Fishtastic.id("bramble"), Fishtastic.id("standard"), "fishtank_bramble",
            List.of(ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Fishtastic.id("explorer/jungle_downpour"))));

    private final Identifier id;
    private final Identifier connectionCollection;
    private final String modelPathPrefix;
    /**
     * When non-empty, this shape can't be selected in the assembly GUI or crafted until *any* one
     * of the named quests has been claimed — mirrors {@link grill24.fishtastic.data.ShopEntry#unlockQuests}.
     * Several quests rather than one so a shape isn't hard-gated behind a single grind: a player
     * can earn it via whichever path they're already pursuing. Empty (the default) means the shape
     * is available from the start, like the other five shipped shapes.
     */
    private final List<ResourceKey<Quest>> unlockQuests;

    FishTankShape(Identifier id, Identifier connectionCollection, String modelPathPrefix) {
        this(id, connectionCollection, modelPathPrefix, List.of());
    }

    FishTankShape(Identifier id, Identifier connectionCollection, String modelPathPrefix, List<ResourceKey<Quest>> unlockQuests) {
        this.id = id;
        this.connectionCollection = connectionCollection;
        this.modelPathPrefix = modelPathPrefix;
        this.unlockQuests = unlockQuests;
    }

    public Identifier id() {
        return id;
    }

    /**
     * The connection-set boundary: two tanks only open a shared face if their shapes'
     * connection collections are equal. Defaults to the shape's own id, so a new shape only
     * connects to itself until deliberately grouped with another (see the tank-shape-variants
     * design doc, §"Connection gating becomes collection-id equality").
     */
    public Identifier connectionCollection() {
        return connectionCollection;
    }

    /** Path segment under {@code models/block/} this shape's 64-permutation frame/sand/glass models live in. */
    public String modelPathPrefix() {
        return modelPathPrefix;
    }

    public List<ResourceKey<Quest>> unlockQuests() {
        return unlockQuests;
    }

    /**
     * Whether this shape is available to a player, given a lookup of whether a quest has been
     * claimed. Ungated shapes (no listed quests) are always available; gated shapes need only one
     * of their listed quests claimed. Mirrors {@link grill24.fishtastic.data.ShopEntry#isUnlockedFor}.
     */
    public boolean isUnlockedFor(Predicate<ResourceKey<Quest>> questClaimed) {
        return unlockQuests.isEmpty() || unlockQuests.stream().anyMatch(questClaimed);
    }

    public String getSerializedName() {
        return id.getPath();
    }

    /**
     * The next shape in declaration order (wrapping), for cycling through the catalog
     * (e.g. from the fish tank assembly GUI's shape button).
     */
    public FishTankShape next() {
        FishTankShape[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** The previous shape in declaration order (wrapping), the inverse of {@link #next()}. */
    public FishTankShape previous() {
        FishTankShape[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }

    /** Localized display name, e.g. "Standard" / "Trimmed" / "Reinforced". */
    public Component getDisplayName() {
        return Component.translatable("shape.fishtastic." + getSerializedName());
    }

    /**
     * Tooltip line naming this shape on the fish tank item stack. Mirrors
     * {@link grill24.fishtastic.component.FishTankMaterials#addToTooltip} — a gray label with the
     * localized shape name in white — and is wired up by {@code ItemStackMixin#modifyTooltipLines}
     * (vanilla only auto-renders a hardcoded list of components, so ours are appended manually).
     */
    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltipAdder, TooltipFlag tooltipFlag, DataComponentGetter components) {
        tooltipAdder.accept(Component.translatable("tooltip.fishtastic.fish_tank_shape",
                getDisplayName().copy().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
    }

    public static FishTankShape bySerializedName(String name) {
        for (FishTankShape shape : values()) {
            if (shape.id.getPath().equals(name)) {
                return shape;
            }
        }
        return STANDARD;
    }

    public static final Codec<FishTankShape> CODEC =
            Codec.stringResolver(FishTankShape::getSerializedName, FishTankShape::bySerializedName);

    public static final StreamCodec<ByteBuf, FishTankShape> STREAM_CODEC = ByteBufCodecs.idMapper(
            i -> FishTankShape.values()[i],
            FishTankShape::ordinal
    );
}
