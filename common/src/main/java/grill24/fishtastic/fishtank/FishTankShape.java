package grill24.fishtastic.fishtank;

import grill24.fishtastic.Fishtastic;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;

/**
 * Identifies the fish tank's body geometry — the shape/silhouette of the frame, sand, and glass
 * elements — independent of which blocks texture them ({@link grill24.fishtastic.component.FishTankMaterials})
 * and independent of which other tanks it's willing to connect to ({@link #connectionCollection()}).
 *
 * <p>A code-defined enum (matching {@link FishTankFrameType}'s minimalism) rather than a dynamic
 * registry: shapes are curated content shipped with the mod, not something datapacks need to add.
 */
public enum FishTankShape {
    STANDARD(Fishtastic.id("standard"), Fishtastic.id("standard"), "fishtankbase"),
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
     * Deep 6px→1px corner taper with a stepped-octagon sand (see CornerTaperProfile.FACETED and
     * SteppedSandGeometryGenerator). Shares STANDARD's connectionCollection — all six shipped
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
    SHAGGY(Fishtastic.id("shaggy"), Fishtastic.id("standard"), "fishtank_shaggy");

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
