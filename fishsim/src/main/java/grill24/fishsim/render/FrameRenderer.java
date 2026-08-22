package grill24.fishsim.render;

import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.FlockDomain;
import grill24.fishsim.domain.VoxelDomain;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Draws one simulation frame to a {@link BufferedImage} — <b>Java2D only, no Swing/JavaFX
 * imports</b>, so it runs headless (docs/fish-sim-engine-handoff.md Task 7). The desktop viewer
 * blits this same renderer's output, which is what guarantees the viewer and the headless export
 * can never disagree.
 *
 * <p>Views: {@link View#SIDE} is the orthographic view a player has through the glass (lateral →
 * x, vertical → y); {@link View#TOP} looks down for depth-layer behavior (lateral → x, depth →
 * y). Fish draw as oriented capsules scaled to spec length — heading via the nose wedge, bank via
 * capsule tilt; gate-failing hover fish draw dimmed as obstacles.
 */
public final class FrameRenderer {

    public enum View { SIDE, TOP }

    /** Pixels per block. */
    private final int scale;
    private final boolean drawHeatmap;
    private final boolean drawVelocities;

    public FrameRenderer(int scale, boolean drawHeatmap, boolean drawVelocities) {
        this.scale = scale;
        this.drawHeatmap = drawHeatmap;
        this.drawVelocities = drawVelocities;
    }

    /** Pads half a block around the domain bounding box. */
    public int imageWidth(FlockDomain domain, View view) {
        float span = domain.maxLateral() - domain.minLateral();
        return (int) Math.ceil((span + 1f) * scale);
    }

    public int imageHeight(FlockDomain domain, View view) {
        float span = view == View.SIDE
                ? domain.maxVertical() - domain.minVertical()
                : domain.maxDepth() - domain.minDepth();
        return (int) Math.ceil((span + 1f) * scale);
    }

    public BufferedImage render(FlockEngine engine, Tunables tunables, View view) {
        FlockDomain domain = engine.domain();
        int w = imageWidth(domain, view);
        int h = imageHeight(domain, view);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(16, 24, 34));
            g.fillRect(0, 0, w, h);

            drawDomain(g, domain, view, w, h);
            drawFish(g, engine, tunables, domain, view);
        } finally {
            g.dispose();
        }
        return image;
    }

    // ── Coordinate mapping ──────────────────────────────────────────────────

    public float mapX(FlockDomain domain, float lateral) {
        return (lateral - domain.minLateral() + 0.5f) * scale;
    }

    /** Screen y for the view's second axis (vertical flips so +y is up in SIDE view). */
    public float mapY(FlockDomain domain, View view, float vertical, float depth) {
        if (view == View.SIDE) {
            return (domain.maxVertical() - vertical + 0.5f) * scale;
        }
        return (depth - domain.minDepth() + 0.5f) * scale;
    }

    // ── Layers ──────────────────────────────────────────────────────────────

    private void drawDomain(Graphics2D g, FlockDomain domain, View view, int w, int h) {
        if (drawHeatmap && domain instanceof VoxelDomain voxel) {
            drawHeatmap(g, voxel, view, w, h);
        }

        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(90, 140, 170));
        if (domain instanceof VoxelDomain voxel && view == View.TOP) {
            drawOccupancyOutline(g, voxel, true);
        } else if (domain instanceof VoxelDomain voxel) {
            drawOccupancyOutline(g, voxel, false);
        } else {
            float x0 = mapX(domain, domain.minLateral());
            float x1 = mapX(domain, domain.maxLateral());
            float y0 = mapY(domain, view, domain.maxVertical(), domain.minDepth());
            float y1 = mapY(domain, view, domain.minVertical(), domain.maxDepth());
            g.draw(new java.awt.geom.Rectangle2D.Float(x0, Math.min(y0, y1), x1 - x0, Math.abs(y1 - y0)));
        }
    }

    /** Interior boundary of the voxel union in the view plane (per-cell edges without occupied neighbours). */
    private void drawOccupancyOutline(Graphics2D g, VoxelDomain voxel, boolean topView) {
        boolean[][][] occ = voxel.occupancy();
        int sx = occ.length, sy = occ[0].length, sz = occ[0][0].length;
        float inset = voxel.inset();
        float minGL = -sx / 2f, minGY = -sy / 2f, minGD = -sz / 2f;

        // Collapse the axis not shown by this view: a cell column is "present" if any cell along
        // the collapsed axis is occupied.
        int cols = sx;
        int rows = topView ? sz : sy;
        boolean[][] present = new boolean[cols][rows];
        for (int ix = 0; ix < sx; ix++) {
            for (int iy = 0; iy < sy; iy++) {
                for (int iz = 0; iz < sz; iz++) {
                    if (!occ[ix][iy][iz]) continue;
                    present[ix][topView ? iz : iy] = true;
                }
            }
        }

        FlockDomain domain = voxel;
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                if (!present[c][r]) continue;
                // Interior extents of this cell's swim space in the view plane: shrink by the
                // inset only on edges that face a missing neighbour.
                float l0 = minGL + c + (c == 0 || !present[c - 1][r] ? inset : 0f);
                float l1 = minGL + c + 1 - (c == cols - 1 || !present[c + 1][r] ? inset : 0f);
                float a0, a1; // second axis, in local units
                if (topView) {
                    a0 = minGD + r + (r == 0 || !present[c][r - 1] ? inset : 0f);
                    a1 = minGD + r + 1 - (r == rows - 1 || !present[c][r + 1] ? inset : 0f);
                } else {
                    a0 = minGY + r + (r == 0 || !present[c][r - 1] ? inset : 0f);
                    a1 = minGY + r + 1 - (r == rows - 1 || !present[c][r + 1] ? inset : 0f);
                }

                float x0 = mapX(domain, l0), x1 = mapX(domain, l1);
                float yA = topView ? mapY(domain, View.TOP, 0, a0) : mapY(domain, View.SIDE, a1, 0);
                float yB = topView ? mapY(domain, View.TOP, 0, a1) : mapY(domain, View.SIDE, a0, 0);
                // Wall segments only where a neighbour is missing, so shared faces read as open water.
                if (c == 0 || !present[c - 1][r]) g.draw(new Line2D.Float(x0, yA, x0, yB));
                if (c == cols - 1 || !present[c + 1][r]) g.draw(new Line2D.Float(x1, yA, x1, yB));
                if (r == 0 || !present[c][r - 1]) g.draw(new Line2D.Float(x0, topView ? yA : yB, x1, topView ? yA : yB));
                if (r == rows - 1 || !present[c][r + 1]) g.draw(new Line2D.Float(x0, topView ? yB : yA, x1, topView ? yB : yA));
            }
        }
    }

    private void drawHeatmap(Graphics2D g, VoxelDomain voxel, View view, int w, int h) {
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                float l = x / (float) scale - 0.5f + voxel.minLateral();
                float dist;
                if (view == View.SIDE) {
                    float v = voxel.maxVertical() + 0.5f - y / (float) scale;
                    dist = voxel.field().distance(l, v, (voxel.minDepth() + voxel.maxDepth()) * 0.5f);
                } else {
                    float d = y / (float) scale - 0.5f + voxel.minDepth();
                    dist = voxel.field().distance(l, (voxel.minVertical() + voxel.maxVertical()) * 0.5f, d);
                }
                if (dist <= 0) continue;
                float t = Math.min(dist / 0.5f, 1f);
                g.setColor(new Color(0.10f + 0.06f * t, 0.18f + 0.18f * t, 0.26f + 0.28f * t));
                g.fillRect(x, y, 1, 1);
            }
        }
    }

    private void drawFish(Graphics2D g, FlockEngine engine, Tunables tunables, FlockDomain domain, View view) {
        int n = engine.count();
        float[] posL = engine.posL(), posY = engine.posY(), posD = engine.posD();
        float[] velL = engine.velL(), velY = engine.velY(), velD = engine.velD();

        for (int i = 0; i < n; i++) {
            float x = mapX(domain, posL[i]);
            float y = mapY(domain, view, posY[i], posD[i]);
            float bodyLen = Math.max(engine.lengths[i] * scale, 4f);
            float bodyH = bodyLen * 0.38f;
            boolean swims = engine.swimmers[i];
            boolean facingNeg = swims ? engine.heading[i] < 0f : engine.hoverMirrored[i];

            AffineTransform old = g.getTransform();
            g.translate(x, y);
            if (engine.planar() && swims) {
                // Continuous-yaw model: rotate to the actual travel direction in the top view;
                // foreshorten in the side view (a fish swimming in depth presents edge-on).
                float hsp = (float) Math.sqrt(velL[i] * velL[i] + velD[i] * velD[i]);
                float dl = hsp > 1e-4f ? velL[i] / hsp : 1f;
                float dd = hsp > 1e-4f ? velD[i] / hsp : 0f;
                if (view == View.TOP) {
                    g.rotate(Math.atan2(dd, dl));
                } else {
                    if (dl < 0f) g.scale(-1, 1);
                    g.scale(Math.max(Math.abs(dl), 0.3f), 1f);
                }
            } else {
                if (view == View.SIDE && swims) {
                    // Bank tilts the capsule; positive bank rolls the nose down-screen slightly.
                    g.rotate(Math.toRadians(engine.bank[i]) * (facingNeg ? 1 : -1) * 0.5);
                }
                if (facingNeg) g.scale(-1, 1);
            }

            // Swimmers are colored by species (so the species-aware separation/schooling is
            // legible in the viewer and plots); hover fish stay dim gray obstacles.
            Color body = swims
                    ? Color.getHSBColor((engine.species[i] % 6) / 6f * 0.85f + 0.05f, 0.62f, 0.95f)
                    : new Color(110, 116, 124);
            Color fin = swims ? body.brighter() : new Color(140, 146, 152);
            g.setColor(body);
            g.fill(new RoundRectangle2D.Float(-bodyLen / 2f, -bodyH / 2f, bodyLen, bodyH, bodyH, bodyH));
            // Nose wedge marks the heading.
            Path2D nose = new Path2D.Float();
            nose.moveTo(bodyLen / 2f, 0);
            nose.lineTo(bodyLen / 2f - bodyH * 0.6f, -bodyH * 0.5f);
            nose.lineTo(bodyLen / 2f - bodyH * 0.6f, bodyH * 0.5f);
            nose.closePath();
            g.setColor(fin);
            g.fill(nose);
            // Tail notch.
            Path2D tail = new Path2D.Float();
            tail.moveTo(-bodyLen / 2f, 0);
            tail.lineTo(-bodyLen / 2f - bodyH * 0.7f, -bodyH * 0.6f);
            tail.lineTo(-bodyLen / 2f - bodyH * 0.7f, bodyH * 0.6f);
            tail.closePath();
            g.fill(tail);
            g.setTransform(old);

            if (drawVelocities && swims) {
                g.setColor(new Color(120, 220, 160, 180));
                g.setStroke(new BasicStroke(1.5f));
                float vx = velL[i] / tunables.maxSpeed() * scale * 0.6f;
                float vy = (view == View.SIDE ? -velY[i] : velD[i]) / tunables.maxSpeed() * scale * 0.6f;
                g.draw(new Line2D.Float(x, y, x + vx, y + vy));
            }
        }
    }
}
