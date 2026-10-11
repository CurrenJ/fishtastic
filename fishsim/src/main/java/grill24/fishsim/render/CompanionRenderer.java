package grill24.fishsim.render;

import grill24.fishsim.companion.BlockQuery;
import grill24.fishsim.companion.CompanionAgent;
import grill24.fishsim.companion.CompanionTunables;
import grill24.fishsim.companion.LocalVoxels;
import grill24.fishsim.companion.OwnerState;
import grill24.fishsim.companion.Vec;
import grill24.fishsim.companion.Visibility;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Draws a companion run two ways (docs/fish-companions-sim-plan.md §4.3): what the owner would see
 * through their own camera, and a top-down map. The headless export and the viewer share this
 * class, so they can never disagree.
 *
 * <p>The first-person view is a small software renderer: block faces near the camera sorted back to
 * front, then the fish (a faceted body with a beating tail, banked and pitched like the sim says)
 * drawn on top when nothing blocks the line of sight to it.
 */
public final class CompanionRenderer {

    private static final double VIEW_RADIUS = 12;
    private static final double NEAR = 0.05;
    private static final Vec LIGHT = new Vec(0.4, 0.85, 0.3).normalize();
    private static final Color SKY_TOP = new Color(0x6FA8DC), SKY_BOTTOM = new Color(0xBFD9EE);
    private static final Color GRASS = new Color(0x6FA34C), GRASS_ALT = new Color(0x689B47);
    private static final Color STONE = new Color(0x8C8C8C);
    private static final Color FISH = new Color(0xF28C28), FISH_FIN = new Color(0xE0602A);

    private CompanionRenderer() {}

    // ── First person ───────────────────────────────────────────────────────────────────────────

    private record Cam(Vec pos, Vec look, Vec right, Vec up, double f, int w, int h) {
        static Cam of(OwnerState o, int w, int h) {
            Vec look = o.look();
            Vec right = look.cross(Vec.UP).normalize();
            if (right.lengthSqr() < 1e-9) right = o.flatRight();
            Vec up = right.cross(look);
            double f = (h / 2.0) / Math.tan(Math.toRadians(o.fovDeg()) / 2);
            return new Cam(o.camera(), look, right, up, f, w, h);
        }

        Vec toCam(Vec p) {
            Vec d = p.sub(pos);
            return new Vec(d.dot(right), d.dot(up), d.dot(look));
        }

        double sx(Vec c) { return w / 2.0 + c.x() / c.z() * f; }

        double sy(Vec c) { return h / 2.0 - c.y() / c.z() * f; }
    }

    private record Face(Vec[] pts, Color color, double depth) {}

    public static BufferedImage firstPerson(int w, int h, OwnerState o, BlockQuery blocks, CompanionAgent a,
                                            boolean overlay) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Cam cam = Cam.of(o, w, h);

        g.setPaint(new GradientPaint(0, 0, SKY_TOP, 0, h, SKY_BOTTOM));
        g.fillRect(0, 0, w, h);

        List<Face> faces = new ArrayList<>();
        collectFaces(cam, blocks, faces);
        faces.sort((p, q) -> Double.compare(q.depth(), p.depth()));
        for (Face face : faces) fill(g, cam, face.pts(), face.color(), true);

        Vec fish = a.renderPos();
        LocalVoxels voxels = a.voxels();
        boolean occluded = !voxels.segmentClear(cam.pos(), fish, 0.0);
        if (!occluded && a.renderAppear() > 0.01f) {
            drawShadow(g, cam, fish, blocks, a);
            drawFish(g, cam, a);
        }

        if (overlay) drawOverlay(g, w, h, a, occluded);
        // Crosshair.
        g.setColor(new Color(255, 255, 255, 160));
        g.drawLine(w / 2 - 5, h / 2, w / 2 + 5, h / 2);
        g.drawLine(w / 2, h / 2 - 5, w / 2, h / 2 + 5);
        g.dispose();
        return img;
    }

    private static void collectFaces(Cam cam, BlockQuery blocks, List<Face> out) {
        int cx = (int) Math.floor(cam.pos().x()), cy = (int) Math.floor(cam.pos().y()), cz = (int) Math.floor(cam.pos().z());
        int r = (int) VIEW_RADIUS;
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int x = cx - r; x <= cx + r; x++) {
            for (int y = cy - r; y <= cy + r; y++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    if (!blocks.solid(x, y, z)) continue;
                    double dx = x + 0.5 - cam.pos().x(), dy = y + 0.5 - cam.pos().y(), dz = z + 0.5 - cam.pos().z();
                    if (dx * dx + dy * dy + dz * dz > VIEW_RADIUS * VIEW_RADIUS) continue;
                    for (int[] d : dirs) {
                        if (blocks.solid(x + d[0], y + d[1], z + d[2])) continue;
                        // Face centre and back-face test.
                        Vec n = new Vec(d[0], d[1], d[2]);
                        Vec c = new Vec(x + 0.5 + d[0] * 0.5, y + 0.5 + d[1] * 0.5, z + 0.5 + d[2] * 0.5);
                        if (c.sub(cam.pos()).dot(n) >= 0) continue;
                        Vec[] q = quad(x, y, z, d);
                        Color base = d[1] == 1 && !blocks.solid(x, y + 1, z) && y <= cy - 1
                                ? (((x + z) & 1) == 0 ? GRASS : GRASS_ALT) : STONE;
                        double shade = 0.55 + 0.45 * Math.max(0, n.dot(LIGHT));
                        out.add(new Face(q, shadeColor(base, shade), c.distanceTo(cam.pos())));
                    }
                }
            }
        }
    }

    private static Vec[] quad(int x, int y, int z, int[] d) {
        double x0 = x, y0 = y, z0 = z, x1 = x + 1, y1 = y + 1, z1 = z + 1;
        if (d[0] != 0) {
            double fx = d[0] > 0 ? x1 : x0;
            return new Vec[]{new Vec(fx, y0, z0), new Vec(fx, y1, z0), new Vec(fx, y1, z1), new Vec(fx, y0, z1)};
        }
        if (d[1] != 0) {
            double fy = d[1] > 0 ? y1 : y0;
            return new Vec[]{new Vec(x0, fy, z0), new Vec(x1, fy, z0), new Vec(x1, fy, z1), new Vec(x0, fy, z1)};
        }
        double fz = d[2] > 0 ? z1 : z0;
        return new Vec[]{new Vec(x0, y0, fz), new Vec(x1, y0, fz), new Vec(x1, y1, fz), new Vec(x0, y1, fz)};
    }

    /** Fills a world-space polygon, clipped against the near plane. */
    private static void fill(Graphics2D g, Cam cam, Vec[] world, Color color, boolean outline) {
        List<Vec> pts = new ArrayList<>(world.length + 2);
        for (Vec p : world) pts.add(cam.toCam(p));
        pts = clipNear(pts);
        if (pts.size() < 3) return;
        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i < pts.size(); i++) {
            Vec c = pts.get(i);
            if (i == 0) path.moveTo(cam.sx(c), cam.sy(c)); else path.lineTo(cam.sx(c), cam.sy(c));
        }
        path.closePath();
        g.setColor(color);
        g.fill(path);
        if (outline) {
            g.setColor(shadeColor(color, 0.85));
            g.setStroke(new BasicStroke(1f));
            g.draw(path);
        }
    }

    private static List<Vec> clipNear(List<Vec> in) {
        List<Vec> out = new ArrayList<>(in.size() + 2);
        for (int i = 0; i < in.size(); i++) {
            Vec a = in.get(i), b = in.get((i + 1) % in.size());
            boolean ain = a.z() >= NEAR, bin = b.z() >= NEAR;
            if (ain) out.add(a);
            if (ain != bin) {
                double t = (NEAR - a.z()) / (b.z() - a.z());
                out.add(a.lerp(b, t));
            }
        }
        return out;
    }

    /** The fish's body frame: forward, right, up, after pitch and bank. */
    private static Vec[] frame(float yaw, float pitch, float bank) {
        Vec fwd = Vec.fromYawPitch(yaw, pitch);
        Vec right0 = fwd.cross(Vec.UP).normalize();
        if (right0.lengthSqr() < 1e-9) right0 = Vec.fromYawPitch(yaw, 0).cross(Vec.UP);
        Vec up0 = right0.cross(fwd);
        double b = Math.toRadians(bank);
        Vec up = up0.scale(Math.cos(b)).add(right0.scale(Math.sin(b)));
        Vec right = right0.scale(Math.cos(b)).sub(up0.scale(Math.sin(b)));
        return new Vec[]{fwd, right, up};
    }

    private static void drawFish(Graphics2D g, Cam cam, CompanionAgent a) {
        double len = a.length() * a.renderAppear();
        Vec[] fr = frame(a.renderYaw(), a.renderPitch(), a.renderBank());
        Vec fwd = fr[0], right = fr[1], up = fr[2];
        Vec c = a.renderPos();
        double wig = Math.sin(a.renderTailPhase()) * 0.12 * len;
        java.util.function.Function<double[], Vec> P = v -> c.add(fwd.scale(v[0] * len)).add(up.scale(v[1] * len))
                .add(right.scale(v[2] * len));
        Vec nose = P.apply(new double[]{0.5, 0, 0});
        Vec top = P.apply(new double[]{0.05, 0.2, 0});
        Vec bot = P.apply(new double[]{0.05, -0.16, 0});
        Vec lft = P.apply(new double[]{0.05, 0, -0.09});
        Vec rgt = P.apply(new double[]{0.05, 0, 0.09});
        Vec tail = P.apply(new double[]{-0.3, 0, 0}).add(right.scale(wig * 0.4));
        Vec finT = P.apply(new double[]{-0.52, 0.17, 0}).add(right.scale(wig));
        Vec finB = P.apply(new double[]{-0.52, -0.17, 0}).add(right.scale(wig));
        Vec[][] tris = {
                {nose, top, rgt}, {nose, rgt, bot}, {nose, bot, lft}, {nose, lft, top},
                {tail, rgt, top}, {tail, bot, rgt}, {tail, lft, bot}, {tail, top, lft},
                {tail, finT, finB},
        };
        // Back to front within the fish.
        List<Vec[]> order = new ArrayList<>(List.of(tris));
        order.sort((p, q) -> Double.compare(centroid(q).distanceTo(cam.pos()), centroid(p).distanceTo(cam.pos())));
        for (Vec[] t : order) {
            Vec n = t[1].sub(t[0]).cross(t[2].sub(t[0])).normalize();
            double shade = 0.55 + 0.45 * Math.abs(n.dot(LIGHT));
            fill(g, cam, t, shadeColor(t[0] == tail && t[1] == finT ? FISH_FIN : FISH, shade), false);
        }
        // Eyes: a dark dot on each side near the nose.
        for (int s = -1; s <= 1; s += 2) {
            Vec eye = P.apply(new double[]{0.3, 0.04, 0.06 * s});
            Vec ec = cam.toCam(eye);
            if (ec.z() < NEAR) continue;
            double rpx = Math.max(1.0, 0.025 * len / ec.z() * cam.f());
            g.setColor(new Color(20, 20, 30));
            g.fill(new java.awt.geom.Ellipse2D.Double(cam.sx(ec) - rpx, cam.sy(ec) - rpx, rpx * 2, rpx * 2));
        }
    }

    private static void drawShadow(Graphics2D g, Cam cam, Vec fish, BlockQuery blocks, CompanionAgent a) {
        int x = (int) Math.floor(fish.x()), z = (int) Math.floor(fish.z());
        int y = (int) Math.floor(fish.y());
        for (int k = 0; k < 8; k++, y--) {
            if (blocks.solid(x, y, z)) {
                double top = y + 1.002;
                double height = fish.y() - top;
                double rad = a.length() * 0.35 * a.renderAppear();
                Vec[] ring = new Vec[10];
                for (int i = 0; i < ring.length; i++) {
                    double ang = i * Math.PI * 2 / ring.length;
                    ring[i] = new Vec(fish.x() + Math.cos(ang) * rad, top, fish.z() + Math.sin(ang) * rad);
                }
                int alpha = (int) Math.max(0, 90 - height * 20);
                if (alpha > 0) fill(g, cam, ring, new Color(0, 0, 0, alpha), false);
                return;
            }
        }
    }

    private static void drawOverlay(Graphics2D g, int w, int h, CompanionAgent a, boolean occluded) {
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        int x = 8, y = 16;
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(4, 4, 196, 92);
        g.setColor(Color.WHITE);
        g.drawString(a.goal() + "  " + (a.seen() ? "seen" : occluded ? "hidden" : "unseen"), x, y);
        y += 16;
        y = bar(g, x, y, "proximity", a.proximity(), new Color(0xE06666));
        y = bar(g, x, y, "attention", a.attention(), new Color(0xF6B26B));
        y = bar(g, x, y, "burst", Math.min(1f, a.burstDrive() / 1.5f), new Color(0x6FA8DC));
        g.drawString(String.format("%.1f b/s  %.1f blk", a.velocity().length(), a.ownerDistance()), x, y + 4);
    }

    private static int bar(Graphics2D g, int x, int y, String label, float v, Color c) {
        g.setColor(Color.WHITE);
        g.drawString(label, x, y);
        g.setColor(new Color(255, 255, 255, 60));
        g.fillRect(x + 80, y - 9, 100, 9);
        g.setColor(c);
        g.fillRect(x + 80, y - 9, (int) (100 * Math.max(0, Math.min(1, v))), 9);
        return y + 15;
    }

    // ── Top view ───────────────────────────────────────────────────────────────────────────────

    /**
     * North-up map centred on the owner: blocks at body height, the owner and their view cone, the
     * comfort band, the fish with its recent trail, and where its current goal is heading.
     */
    public static BufferedImage topView(int w, int h, OwnerState o, BlockQuery blocks, CompanionAgent a,
                                        CompanionTunables t, Deque<Vec> trail, double pxPerBlock) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xE8EFE3));
        g.fillRect(0, 0, w, h);
        double ox = o.eye().x(), oz = o.eye().z();
        double cx = w / 2.0, cy = h / 2.0;
        java.util.function.ToDoubleFunction<Double> SX = x -> cx + (x - ox) * pxPerBlock;
        java.util.function.ToDoubleFunction<Double> SY = z -> cy + (z - oz) * pxPerBlock;

        int feetY = (int) Math.floor(o.feet().y() + 0.01);
        int rx = (int) Math.ceil(w / 2.0 / pxPerBlock) + 1, rz = (int) Math.ceil(h / 2.0 / pxPerBlock) + 1;
        for (int x = (int) Math.floor(ox) - rx; x <= (int) Math.floor(ox) + rx; x++) {
            for (int z = (int) Math.floor(oz) - rz; z <= (int) Math.floor(oz) + rz; z++) {
                boolean body = blocks.solid(x, feetY, z) || blocks.solid(x, feetY + 1, z);
                boolean over = blocks.solid(x, feetY + 2, z) || blocks.solid(x, feetY + 3, z);
                if (!body && !over) continue;
                g.setColor(body ? new Color(0x5A5A5A) : new Color(0xB9C4B0));
                g.fillRect((int) SX.applyAsDouble((double) x), (int) SY.applyAsDouble((double) z),
                        (int) Math.ceil(pxPerBlock), (int) Math.ceil(pxPerBlock));
            }
        }
        // Grid.
        g.setColor(new Color(0, 0, 0, 18));
        for (int x = (int) Math.floor(ox) - rx; x <= (int) Math.floor(ox) + rx; x++) {
            double sx = SX.applyAsDouble((double) x);
            g.draw(new Line2D.Double(sx, 0, sx, h));
        }
        for (int z = (int) Math.floor(oz) - rz; z <= (int) Math.floor(oz) + rz; z++) {
            double sy = SY.applyAsDouble((double) z);
            g.draw(new Line2D.Double(0, sy, w, sy));
        }

        // Comfort band.
        g.setColor(new Color(60, 120, 200, 60));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{4f, 4f}, 0f));
        for (double rad : new double[]{t.bandInner, t.bandOuter, t.bandOuterIdle}) {
            double rp = rad * pxPerBlock;
            g.draw(new java.awt.geom.Ellipse2D.Double(cx - rp, cy - rp, rp * 2, rp * 2));
        }
        g.setStroke(new BasicStroke(1f));

        // View cone.
        double halfH = Math.atan(Math.tan(Math.toRadians(o.fovDeg()) / 2) * Visibility.ASPECT);
        Vec f = o.flatForward();
        for (int s = -1; s <= 1; s += 2) {
            double ang = s * halfH;
            Vec d = new Vec(f.x() * Math.cos(ang) - f.z() * Math.sin(ang), 0, f.x() * Math.sin(ang) + f.z() * Math.cos(ang));
            g.setColor(new Color(0, 0, 0, 50));
            g.draw(new Line2D.Double(cx, cy, cx + d.x() * 8 * pxPerBlock, cy + d.z() * 8 * pxPerBlock));
        }

        // Trail.
        Vec prev = null;
        int i = 0, n = trail.size();
        for (Vec p : trail) {
            if (prev != null) {
                int alpha = (int) (40 + 160.0 * i / Math.max(1, n));
                g.setColor(new Color(242, 140, 40, alpha));
                g.draw(new Line2D.Double(SX.applyAsDouble(prev.x()), SY.applyAsDouble(prev.z()),
                        SX.applyAsDouble(p.x()), SY.applyAsDouble(p.z())));
            }
            prev = p;
            i++;
        }

        // Owner.
        double or = OwnerState.BODY_RADIUS * pxPerBlock;
        g.setColor(new Color(0x3D5A80));
        g.fill(new java.awt.geom.Ellipse2D.Double(cx - or, cy - or, or * 2, or * 2));
        g.setColor(Color.WHITE);
        g.draw(new Line2D.Double(cx, cy, cx + f.x() * or * 1.6, cy + f.z() * or * 1.6));

        // Goal target.
        Vec target = a.target();
        if (target != null) {
            double tx = SX.applyAsDouble(target.x()), ty = SY.applyAsDouble(target.z());
            g.setColor(goalColor(a.goal()));
            g.draw(new Line2D.Double(tx - 4, ty - 4, tx + 4, ty + 4));
            g.draw(new Line2D.Double(tx - 4, ty + 4, tx + 4, ty - 4));
        }

        // Fish.
        Vec p = a.renderPos();
        double fx = SX.applyAsDouble(p.x()), fy = SY.applyAsDouble(p.z());
        Vec dir = Vec.fromYawPitch(a.renderYaw(), 0);
        Vec side = dir.cross(Vec.UP);
        double L = Math.max(6, a.length() * pxPerBlock);
        Polygon tri = new Polygon();
        tri.addPoint((int) (fx + dir.x() * L * 0.6), (int) (fy + dir.z() * L * 0.6));
        tri.addPoint((int) (fx - dir.x() * L * 0.4 + side.x() * L * 0.3), (int) (fy - dir.z() * L * 0.4 + side.z() * L * 0.3));
        tri.addPoint((int) (fx - dir.x() * L * 0.4 - side.x() * L * 0.3), (int) (fy - dir.z() * L * 0.4 - side.z() * L * 0.3));
        g.setColor(a.seen() ? FISH : shadeColor(FISH, 0.6));
        g.fillPolygon(tri);
        g.setColor(goalColor(a.goal()));
        g.drawPolygon(tri);

        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        g.setColor(Color.DARK_GRAY);
        g.drawString(String.format("fish y %+.2f vs eye", p.y() - o.eye().y()), 6, h - 8);
        g.dispose();
        return img;
    }

    private static Color goalColor(String goal) {
        return switch (goal) {
            case "LOITER" -> new Color(0x38761D);
            case "ESCORT" -> new Color(0x1155CC);
            case "CATCH_UP" -> new Color(0xCC0000);
            case "SHOW_OFF" -> new Color(0xB45F06);
            default -> Color.BLACK;
        };
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────────────

    /** Side-by-side: first person on the left, top view on the right. */
    public static BufferedImage composite(BufferedImage left, BufferedImage right) {
        BufferedImage out = new BufferedImage(left.getWidth() + right.getWidth(),
                Math.max(left.getHeight(), right.getHeight()), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(left, 0, 0, null);
        g.drawImage(right, left.getWidth(), 0, null);
        g.dispose();
        return out;
    }

    private static Vec centroid(Vec[] t) {
        return t[0].add(t[1]).add(t[2]).scale(1 / 3.0);
    }

    private static Color shadeColor(Color c, double k) {
        return new Color(clamp((int) (c.getRed() * k)), clamp((int) (c.getGreen() * k)), clamp((int) (c.getBlue() * k)),
                c.getAlpha());
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
