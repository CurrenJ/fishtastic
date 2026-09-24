package grill24.fishtastic.client.renderer;

/**
 * A solid black GUI item edge outline, used by the fishing minigame's gear readout (equipped
 * hook/charm icons) — see {@code FishingMinigameAnimation} — and requested through
 * {@link FishtasticGlintState#BLACK_OUTLINE_REQUESTED}.
 *
 * <p>Reuses the exact same shader shape as the per-quality-tier outlines, since that shader is
 * already fully parameterized (color/falloff/opacity/width) and a solid black edge is just one more
 * parameter combination — no new GLSL needed. But it isn't data-driven or keyed by an
 * {@code ItemStack}'s rarity component: every caller wants the same fixed look, driven by UI
 * context rather than item data. 26.1.2 builds a pipeline + params UBO for it; on 1.21.1 it's just
 * this style, baked into {@link FishtasticItemOutlineAtlas} and drawn by
 * {@link FishtasticGuiOutlineRenderer} like a quality ring.
 */
public final class FishtasticBlackOutlineEffect {
    /** Solid black, no gradient fade, full opacity, half an item pixel thick. */
    public static final FishtasticOutlineStyle STYLE = FishtasticOutlineStyle.BLACK;

    private FishtasticBlackOutlineEffect() {}
}
