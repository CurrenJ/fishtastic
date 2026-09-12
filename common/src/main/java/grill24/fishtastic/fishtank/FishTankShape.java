package grill24.fishtastic.fishtank;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.data.Quest;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
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
 *
 * <p>Which quests unlock a given shape is <em>not</em> declared here. It's derived at runtime by
 * {@link FishTankShapeUnlocks}, which scans the {@code Quest} registry for reward items carrying an
 * explicit {@code fishtastic:fish_tank_shape} component — the quest reward JSON is the only place
 * that needs editing to add a new unlock path. (An earlier version hardcoded a per-constant list of
 * unlock quests here, which had to be hand-kept in sync with both the reward JSON and the matching
 * {@code shop_entry} JSON; the three silently drifted more than once.)
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
     */
    TRIMMED(Fishtastic.id("trimmed"), Fishtastic.id("standard"), "fishtank_trimmed"),
    /**
     * Chunkier corner brackets — deeper 5px→1px taper. See CornerTaperProfile.REINFORCED.
     * Shares STANDARD's connectionCollection — see {@link #TRIMMED}'s note.
     */
    REINFORCED(Fishtastic.id("reinforced"), Fishtastic.id("standard"), "fishtank_reinforced"),
    /**
     * REINFORCED's deeper sibling — a 6px→1px taper with no full-width rows (see
     * CornerTaperProfile.HONED). Shares STANDARD's connectionCollection — see {@link #TRIMMED}'s
     * note.
     */
    HONED(Fishtastic.id("honed"), Fishtastic.id("standard"), "fishtank_honed"),
    /**
     * STANDARD's chunky hard-edged sibling: a uniform 2px mid-body with chamfered octagonal cap
     * rings (see CornerTaperProfile.STURDY). Shares STANDARD's connectionCollection — see
     * {@link #TRIMMED}'s note.
     */
    STURDY(Fishtastic.id("sturdy"), Fishtastic.id("standard"), "fishtank_sturdy"),
    /**
     * A 16px→2px corner taper with 2px-thick chamfered octagonal cap rings and a stepped-octagon
     * sand — BASTION's thinner-stepped sibling (see CornerTaperProfile.FACETED and
     * SteppedSandGeometryGenerator). Shares STANDARD's connectionCollection — all shipped
     * shapes are meant to freely interconnect (see {@link #TRIMMED}'s note).
     */
    FACETED(Fishtastic.id("faceted"), Fishtastic.id("standard"), "fishtank_faceted"),
    /**
     * 16px→2px taper with 2px-thick caps, whose {@code 16} rows are chamfered octagonal base rings
     * and whose sand is a stepped octagon (see CornerTaperProfile.BASTION). Shares STANDARD's
     * connectionCollection like {@link #FACETED}.
     */
    BASTION(Fishtastic.id("bastion"), Fishtastic.id("standard"), "fishtank_bastion"),
    /**
     * BASTION's deeper sibling — a 16px→2px taper whose full-width cap rows taper one pixel
     * further out (7 vs BASTION's 6) for a tighter frame circle (see CornerTaperProfile.RAMPART).
     * Shares STANDARD's connectionCollection like {@link #FACETED}.
     */
    RAMPART(Fishtastic.id("rampart"), Fishtastic.id("standard"), "fishtank_rampart"),
    /**
     * Ornate tank: standard 1px frame plus decorative 1px inlay brackets on each face, with a
     * standard sand and a glass pane shaped around the brackets (see OrnateFrameGeometryGenerator /
     * OrnateGlassGeometryGenerator). Shares STANDARD's connectionCollection like {@link #FACETED}.
     */
    ORNATE(Fishtastic.id("ornate"), Fishtastic.id("standard"), "fishtank_ornate"),
    /**
     * Shaggy tank: the ornate tank's construction — standard 1px frame plus 1px decorative inlays
     * inset into the glass layer — with a shaggier, deliberately asymmetric fringe and a full-width
     * band at Y [1,2] that hides the sand from the side (see ShaggyFrameGeometryGenerator /
     * ShaggyGlassGeometryGenerator, spans in ShaggyTankSpans). Shares STANDARD's
     * connectionCollection like {@link #FACETED}.
     */
    SHAGGY(Fishtastic.id("shaggy"), Fishtastic.id("standard"), "fishtank_shaggy"),
    /**
     * Bramble tank: no plain corner post at all — every interior Y band carries its own thorny,
     * asymmetric inlay pattern read off {@code docs/tank-shapes/bramble_shape_*.png} (see
     * BrambleFrameGeometryGenerator / BrambleGlassGeometryGenerator, spans in BrambleTankSpans).
     * Shares STANDARD's connectionCollection like {@link #FACETED}.
     */
    BRAMBLE(Fishtastic.id("bramble"), Fishtastic.id("standard"), "fishtank_bramble"),
    /**
     * Tooth tank: a shark-jaw motif — doubled ceiling/floor bands, a comb-tooth fringe hanging down
     * from the ceiling and rising up from the floor, and a 2px corner post filling the waist between
     * them, read pixel-exactly off {@code docs/tank-shapes/tooth_shape_3_wide.png} (see
     * CombFrameGeometryGenerator / CombGlassGeometryGenerator, spans in ToothTankSpans). Shares
     * STANDARD's connectionCollection like {@link #FACETED}.
     */
    TOOTH(Fishtastic.id("tooth"), Fishtastic.id("standard"), "fishtank_tooth"),
    /**
     * Film tank: a filmstrip-sprocket motif — a period-2 perforated comb band under the ceiling and
     * above the sand, with a plain 1px corner post filling the waist, read pixel-exactly off
     * {@code docs/tank-shapes/film_shape_3_wide.png} (see CombFrameGeometryGenerator /
     * CombGlassGeometryGenerator, spans in FilmTankSpans). Shares STANDARD's connectionCollection
     * like {@link #FACETED}.
     */
    FILM(Fishtastic.id("film"), Fishtastic.id("standard"), "fishtank_film"),
    /**
     * Arch tank: a window-arch motif on every face — an arc curve springing from a crown blob down
     * to the corners, framed by a jamb that is 1px up high and widens to 2px below the springing
     * line (matching the sand's 2px inset). See ArchTankSpans for the pixel transcription, plus
     * ArchFrameGeometryGenerator / ArchGlassGeometryGenerator ({@code tools/tank-shape-gen}).
     * <p>The two components are gated differently, and that's what produces the arcade: the jamb is
     * an ordinary corner post that disappears when the perpendicular face at its end of the wall
     * opens, while the arc renders regardless of the perpendicular faces. Connected tanks therefore
     * lose the pillar at the seam and their two arcs meet there instead, reading as a continuous
     * row of open archways. Shares STANDARD's connectionCollection like {@link #FACETED}.
     */
    ARCH(Fishtastic.id("arch"), Fishtastic.id("standard"), "fishtank_arch"),
    /**
     * Mullion tank: a standard 1px frame plus three interior window-mullion bars per face at local
     * {@code x = 4, 8, 12}, running the full wall height including through the sand row (see
     * MullionFrameGeometryGenerator / MullionGlassGeometryGenerator). The bar at local {@code x = 0}
     * is persistent (renders regardless of the adjacent face's open state) while the far edge at
     * {@code x = 15} is an ordinary gated wall — this asymmetry is what keeps the bars exactly 3px
     * apart across a horizontal connection. Shares STANDARD's connectionCollection like
     * {@link #FACETED}.
     */
    MULLION(Fishtastic.id("mullion"), Fishtastic.id("standard"), "fishtank_mullion"),
    /**
     * Lattice tank: a 1px edge on every row (2px at the very top/bottom, matching the sand's 2px
     * inset) plus a pair of diagonal 1px points per row that walk inward from the edge, crossing at
     * the tank's vertical midpoint (see LatticeFrameGeometryGenerator / LatticeGlassGeometryGenerator).
     * Every element is face-local, following the shipped ornate-family convention (no special
     * connection behavior was requested). Shares STANDARD's connectionCollection like
     * {@link #FACETED}.
     */
    LATTICE(Fishtastic.id("lattice"), Fishtastic.id("standard"), "fishtank_lattice"),
    /**
     * Dune tank: standard 1px frame/glass (identical to STANDARD's own, reused byte-for-byte)
     * with a two-step raised sand hill in the center (see DuneSandGeometryGenerator). Each connected
     * horizontal face pushes the hill's footprint out toward that boundary, and connecting all four
     * gives an entirely flat, raised sand surface at the hill's top height. Shares STANDARD's
     * connectionCollection like {@link #FACETED}.
     */
    DUNE(Fishtastic.id("dune"), Fishtastic.id("standard"), "fishtank_dune");

    private final Identifier id;
    private final Identifier connectionCollection;
    private final String modelPathPrefix;

    FishTankShape(Identifier id, Identifier connectionCollection, String modelPathPrefix) {
        this.id = id;
        this.connectionCollection = connectionCollection;
        this.modelPathPrefix = modelPathPrefix;
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

    /**
     * The quests that unlock this shape (claiming any one is enough), derived from {@code Quest}
     * reward data — see {@link FishTankShapeUnlocks}. Empty means the shape is available from the
     * start, like STANDARD/SKYLIGHT/TRIMMED/REINFORCED/BASTION.
     */
    public List<ResourceKey<Quest>> unlockQuests(Registry<Quest> quests) {
        return FishTankShapeUnlocks.unlockQuestsFor(quests, this);
    }

    /**
     * Whether this shape is available to a player, given a lookup of whether a quest has been
     * claimed. Ungated shapes (no unlocking quests) are always available; gated shapes need only
     * one of their unlocking quests claimed. Mirrors {@link grill24.fishtastic.data.ShopEntry#isUnlockedFor}.
     */
    public boolean isUnlockedFor(Registry<Quest> quests, Predicate<ResourceKey<Quest>> questClaimed) {
        List<ResourceKey<Quest>> unlockQuests = unlockQuests(quests);
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
