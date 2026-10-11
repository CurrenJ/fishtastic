package grill24.fishsim.render;

import grill24.fishsim.companion.BlockQuery;
import grill24.fishsim.companion.CompanionAgent;
import grill24.fishsim.companion.CompanionTunables;
import grill24.fishsim.companion.CompanionWorld;
import grill24.fishsim.companion.LocalVoxels;
import grill24.fishsim.companion.OwnerState;
import grill24.fishsim.companion.PointOfInterest;
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
    private static final Color CRAB = new Color(0xD0442C), EEL = new Color(0xE8DCA0);
    private static final Color WATER = new Color(40, 110, 200, 110);
    private static final Color UNDERWATER = new Color(20, 70, 160, 90);

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
        return firstPerson(w, h, o, blocks, a, overlay, null);
    }

    /** As above, with the world's points of interest and held item drawn too ({@code world} may be null). */
    public static BufferedImage firstPerson(int w, int h, OwnerState o, BlockQuery blocks, CompanionAgent a,
                                            boolean overlay, CompanionWorld world) {
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

        if (!o.firstPerson()) {
            // The owner, so a third-person view shows where a ground companion is from their feet.
            Vec mid = o.feet().add(0, OwnerState.BODY_HEIGHT / 2, 0);
            drawBox(g, cam, mid, new Vec(OwnerState.BODY_RADIUS, OwnerState.BODY_HEIGHT / 2, OwnerState.BODY_RADIUS),
                    new Color(0x3D5A80));
        }
        Vec fish = a.renderPos();
        LocalVoxels voxels = a.voxels();
        double fishDepth = cam.toCam(fish).z();
        // Points of interest behind the fish first, the rest after it.
        if (world != null) drawPoints(g, cam, world, voxels, d -> d >= fishDepth);
        boolean occluded = !voxels.segmentClear(cam.pos(), fish, 0.0);
        boolean camWet = blocks.water(floor(cam.pos().x()), floor(cam.pos().y()), floor(cam.pos().z()));
        if (!occluded && a.renderAppear() > 0.01f) {
            drawShadow(g, cam, fish, blocks, a);
            drawFish(g, cam, a);
            boolean fishWet = blocks.water(floor(fish.x()), floor(fish.y()), floor(fish.z()));
            if (fishWet && !camWet) {
                // Seen through the surface: wash the fish with the water's colour.
                Vec c = cam.toCam(fish);
                if (c.z() > NEAR) {
                    double rpx = a.length() * 0.6 / c.z() * cam.f();
                    g.setColor(WATER);
                    g.fill(new java.awt.geom.Ellipse2D.Double(cam.sx(c) - rpx, cam.sy(c) - rpx, rpx * 2, rpx * 2));
                }
            }
        }
        if (world != null) drawPoints(g, cam, world, voxels, d -> d < fishDepth);
        if (camWet) {
            g.setColor(UNDERWATER);
            g.fillRect(0, 0, w, h);
        }

        if (overlay) drawOverlay(g, w, h, a, occluded);
        if (world != null && world.held() != CompanionWorld.Held.NONE) {
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
            g.setColor(new Color(0, 0, 0, 140));
            g.fillRect(w - 150, h - 44, 146, 18);
            g.setColor(Color.WHITE);
            g.drawString("holding " + world.held().name().toLowerCase(java.util.Locale.ROOT), w - 146, h - 31);
        }
        // Crosshair.
        g.setColor(new Color(255, 255, 255, 160));
        g.drawLine(w / 2 - 5, h / 2, w / 2 + 5, h / 2);
        g.drawLine(w / 2, h / 2 - 5, w / 2, h / 2 + 5);
        g.dispose();
        return img;
    }

    private static final Color HOSTILE = new Color(0x3B7A3B), CAT = new Color(0x8E7CC3),
            ITEM = new Color(0xF1C232), OTHER_COMPANION = new Color(0x45B5C4), HELD = new Color(0xFFFFFF);

    private static Color pointColor(PointOfInterest.Kind k) {
        return switch (k) {
            case HOSTILE -> HOSTILE;
            case CAT -> CAT;
            case DROPPED_ITEM -> ITEM;
            case COMPANION -> OTHER_COMPANION;
        };
    }

    /** Half extents of a point of interest's box: a mob stands tall, an item is a speck. */
    private static Vec pointExtents(PointOfInterest p) {
        return p.kind() == PointOfInterest.Kind.HOSTILE ? new Vec(p.radius(), 0.9, p.radius())
                : new Vec(p.radius(), p.radius(), p.radius());
    }

    /** The world's points of interest as boxes, and the held item's anchor as a small marker. */
    private static void drawPoints(Graphics2D g, Cam cam, CompanionWorld world, LocalVoxels voxels,
                                   java.util.function.DoublePredicate depth) {
        for (PointOfInterest p : world.points()) {
            if (!depth.test(cam.toCam(p.pos()).z())) continue;
            if (!voxels.segmentClear(cam.pos(), p.pos(), 0.0)) continue;
            drawBox(g, cam, p.pos(), pointExtents(p), pointColor(p.kind()));
        }
        Vec hp = world.heldPoint();
        if (hp != null && depth.test(cam.toCam(hp).z())) drawBox(g, cam, hp, new Vec(0.04, 0.04, 0.04), HELD);
    }

    private static void drawBox(Graphics2D g, Cam cam, Vec c, Vec e, Color color) {
        double x0 = c.x() - e.x(), x1 = c.x() + e.x(), y0 = c.y() - e.y(), y1 = c.y() + e.y();
        double z0 = c.z() - e.z(), z1 = c.z() + e.z();
        Vec[][] faces = {
                {new Vec(x0, y0, z0), new Vec(x1, y0, z0), new Vec(x1, y1, z0), new Vec(x0, y1, z0)},
                {new Vec(x0, y0, z1), new Vec(x1, y0, z1), new Vec(x1, y1, z1), new Vec(x0, y1, z1)},
                {new Vec(x0, y0, z0), new Vec(x0, y0, z1), new Vec(x0, y1, z1), new Vec(x0, y1, z0)},
                {new Vec(x1, y0, z0), new Vec(x1, y0, z1), new Vec(x1, y1, z1), new Vec(x1, y1, z0)},
                {new Vec(x0, y1, z0), new Vec(x1, y1, z0), new Vec(x1, y1, z1), new Vec(x0, y1, z1)},
                {new Vec(x0, y0, z0), new Vec(x1, y0, z0), new Vec(x1, y0, z1), new Vec(x0, y0, z1)}};
        double[] shade = {0.8, 0.8, 0.7, 0.7, 1.0, 0.5};
        Integer[] order = {0, 1, 2, 3, 4, 5};
        java.util.Arrays.sort(order, (p, q) -> Double.compare(cam.toCam(centroid(faces[q])).z(),
                cam.toCam(centroid(faces[p])).z()));
        for (int i : order) fill(g, cam, faces[i], shadeColor(color, shade[i]), true);
    }

    private static void collectFaces(Cam cam, BlockQuery blocks, List<Face> out) {
        int cx = (int) Math.floor(cam.pos().x()), cy = (int) Math.floor(cam.pos().y()), cz = (int) Math.floor(cam.pos().z());
        int r = (int) VIEW_RADIUS;
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int x = cx - r; x <= cx + r; x++) {
            for (int y = cy - r; y <= cy + r; y++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    if (blocks.water(x, y, z) && !blocks.water(x, y + 1, z) && !blocks.solid(x, y + 1, z)) {
                        Vec c = new Vec(x + 0.5, y + 1, z + 0.5);
                        if (c.distanceTo(cam.pos()) <= VIEW_RADIUS) {
                            out.add(new Face(quad(x, y, z, new int[]{0, 1, 0}), WATER, c.distanceTo(cam.pos())));
                        }
                    }
                    if (!blocks.solid(x, y, z)) continue;
                    double dx = x + 0.5 - cam.pos().x(), dy = y + 0.5 - cam.pos().y(), dz = z + 0.5 - cam.pos().z();
                    if (dx * dx + dy * dy + dz * dz > VIEW_RADIUS * VIEW_RADIUS) continue;
                    // Partial blocks (slabs, carpets) are drawn as tall as they are.
                    double top = blocks.top(x, y, z);
                    for (int[] d : dirs) {
                        int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                        if (blocks.solid(nx, ny, nz)) {
                            // A side hidden by a neighbour at least as tall; a top hidden only if full.
                            if (d[1] == 0 && blocks.top(nx, ny, nz) >= top) continue;
                            if (d[1] == 1 && top >= 1) continue;
                            if (d[1] == -1) continue;
                        }
                        // Face centre and back-face test.
                        Vec n = new Vec(d[0], d[1], d[2]);
                        Vec c = new Vec(x + 0.5 + d[0] * 0.5, y + 0.5 + d[1] * 0.5, z + 0.5 + d[2] * 0.5);
                        if (c.sub(cam.pos()).dot(n) >= 0) continue;
                        Vec[] q = quad(x, y, z, d, top);
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
        return quad(x, y, z, d, 1);
    }

    private static Vec[] quad(int x, int y, int z, int[] d, double top) {
        double x0 = x, y0 = y, z0 = z, x1 = x + 1, y1 = y + top, z1 = z + 1;
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
        if (a.locomotion() == grill24.fishsim.core.Locomotion.GLIDE) {
            drawRay(g, cam, a);
            return;
        }
        if (a.locomotion() == grill24.fishsim.core.Locomotion.DRIFT) {
            drawJelly(g, cam, a);
            return;
        }
        Color body = a.locomotion() == grill24.fishsim.core.Locomotion.BENTHIC ? CRAB
                : a.locomotion() == grill24.fishsim.core.Locomotion.ANCHORED ? EEL : FISH;
        drawFishAt(g, cam, a.length() * a.renderAppear(), a.renderPos(), a.renderYaw(), a.renderPitch(),
                a.renderBank(), a.renderTailPhase(), body);
    }

    /** A trio's followers, drawn like the leader where the owner can see them. */
    public static void drawFollowers(BufferedImage img, OwnerState o, LocalVoxels voxels,
                                     grill24.fishsim.companion.CompanionGroup group, float length) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Cam cam = Cam.of(o, img.getWidth(), img.getHeight());
        for (int i = 0; i < 2; i++) {
            var f = group.follower(i);
            if (f.renderAppear() < 0.01f || !voxels.segmentClear(cam.pos(), f.renderPos(), 0.0)) continue;
            drawFishAt(g, cam, length * f.renderAppear(), f.renderPos(), f.renderYaw(), f.renderPitch(),
                    f.renderBank(), f.renderTailPhase(), FISH);
        }
        g.dispose();
    }

    private static void drawFishAt(Graphics2D g, Cam cam, double len, Vec c, float yaw, float pitch, float bankDeg,
                                   float tailPhase, Color body) {
        Vec[] fr = frame(yaw, pitch, bankDeg);
        Vec fwd = fr[0], right = fr[1], up = fr[2];
        double wig = Math.sin(tailPhase) * 0.12 * len;
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
            fill(g, cam, t, shadeColor(t[0] == tail && t[1] == finT ? FISH_FIN : body, shade), false);
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

    /**
     * A jellyfish: a bell along its axis that narrows and lengthens as it squeezes, and tentacles
     * trailing the other way.
     */
    private static void drawJelly(Graphics2D g, Cam cam, CompanionAgent a) {
        double len = a.length() * a.renderAppear();
        Vec axis = Vec.fromYawPitch(a.renderYaw(), a.renderPitch());
        Vec side = axis.cross(Vec.UP);
        if (side.lengthSqr() < 1e-6) side = Vec.fromYawPitch(a.spin(), 0).cross(Vec.UP);
        side = side.normalize();
        Vec other = axis.cross(side).normalize();
        double squeeze = a.pulse();
        double rim = len * 0.5 * (1 - 0.3 * squeeze);
        double dome = len * 0.45 * (1 + 0.25 * squeeze);
        Vec c = a.renderPos();
        Vec top = c.add(axis.scale(dome));
        int n = 10;
        Vec[] ring = new Vec[n];
        double spinRad = Math.toRadians(a.spin());
        for (int i = 0; i < n; i++) {
            double ang = spinRad + i * Math.PI * 2 / n;
            ring[i] = c.add(side.scale(Math.cos(ang) * rim)).add(other.scale(Math.sin(ang) * rim));
        }
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < n; i += 2) {
            Vec tip = ring[i].add(axis.scale(-len * (0.7 + 0.15 * Math.sin(a.renderTailPhase() + i))));
            Vec rc = cam.toCam(ring[i]), tc = cam.toCam(tip);
            if (rc.z() > NEAR && tc.z() > NEAR) {
                g.setColor(new Color(230, 150, 200, 150));
                g.draw(new Line2D.Double(cam.sx(rc), cam.sy(rc), cam.sx(tc), cam.sy(tc)));
            }
        }
        List<Vec[]> tris = new ArrayList<>();
        for (int i = 0; i < n; i++) tris.add(new Vec[]{top, ring[i], ring[(i + 1) % n]});
        tris.sort((p, q) -> Double.compare(centroid(q).distanceTo(cam.pos()), centroid(p).distanceTo(cam.pos())));
        for (Vec[] t : tris) {
            Vec nn = t[1].sub(t[0]).cross(t[2].sub(t[0])).normalize();
            double shade = 0.6 + 0.4 * Math.abs(nn.dot(LIGHT));
            fill(g, cam, t, shadeColor(new Color(240, 160, 210, 190), shade), false);
        }
    }

    /** A ray: a flat diamond whose wingtips rise and fall with the beat, and a whip of a tail. */
    private static void drawRay(Graphics2D g, Cam cam, CompanionAgent a) {
        double len = a.length() * a.renderAppear();
        Vec[] fr = frame(a.renderYaw(), a.renderPitch(), a.renderBank());
        Vec fwd = fr[0], right = fr[1], up = fr[2];
        Vec c = a.renderPos();
        double flap = Math.sin(a.renderTailPhase()) * 0.14 * len;
        java.util.function.Function<double[], Vec> P = v -> c.add(fwd.scale(v[0] * len)).add(up.scale(v[1] * len))
                .add(right.scale(v[2] * len));
        Vec nose = P.apply(new double[]{0.38, 0, 0});
        Vec mid = P.apply(new double[]{0.0, 0.05, 0});
        Vec back = P.apply(new double[]{-0.3, 0, 0});
        Vec wl = P.apply(new double[]{-0.02, 0, -0.55}).add(up.scale(flap));
        Vec wr = P.apply(new double[]{-0.02, 0, 0.55}).add(up.scale(flap));
        Vec tail = P.apply(new double[]{-0.75, 0, 0}).add(right.scale(flap * 0.3));
        Color body = new Color(0x7A6A58), belly = new Color(0xD8CFC0);
        Vec[][] tris = {{nose, wl, mid}, {nose, mid, wr}, {back, mid, wl}, {back, wr, mid}};
        List<Vec[]> order = new ArrayList<>(List.of(tris));
        order.sort((p, q) -> Double.compare(centroid(q).distanceTo(cam.pos()), centroid(p).distanceTo(cam.pos())));
        for (Vec[] t : order) {
            Vec n = t[1].sub(t[0]).cross(t[2].sub(t[0])).normalize();
            boolean fromBelow = cam.pos().sub(c).dot(up) < 0;
            double shade = 0.55 + 0.45 * Math.abs(n.dot(LIGHT));
            fill(g, cam, t, shadeColor(fromBelow ? belly : body, shade), false);
        }
        Vec bc = cam.toCam(back), tc = cam.toCam(tail);
        if (bc.z() > NEAR && tc.z() > NEAR) {
            g.setColor(new Color(0x4A3F34));
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new Line2D.Double(cam.sx(bc), cam.sy(bc), cam.sx(tc), cam.sy(tc)));
            g.setStroke(new BasicStroke(1f));
        }
    }

    private static void drawShadow(Graphics2D g, Cam cam, Vec fish, BlockQuery blocks, CompanionAgent a) {
        int x = (int) Math.floor(fish.x()), z = (int) Math.floor(fish.z());
        int y = (int) Math.floor(fish.y());
        for (int k = 0; k < 8; k++, y--) {
            if (blocks.solid(x, y, z)) {
                double top = y + blocks.top(x, y, z) + 0.002;
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
        g.fillRect(4, 4, 196, 152);
        g.setColor(Color.WHITE);
        String state = a.perchState() != null && a.goal().equals("PERCH") ? a.perchState() : a.goal();
        g.drawString(state + "  " + (a.seen() ? "seen" : occluded ? "hidden" : "unseen"), x, y);
        y += 16;
        String pose = a.poseHint() == grill24.fishsim.companion.PoseHint.NONE ? "" : "pose " + a.poseHint().name().toLowerCase(java.util.Locale.ROOT);
        g.drawString(a.temperament().name() + "  " + pose, x, y);
        y += 16;
        y = bar(g, x, y, "proximity", a.proximity(), new Color(0xE06666));
        y = bar(g, x, y, "attention", a.attention(), new Color(0xF6B26B));
        y = bar(g, x, y, "curiosity", a.curiosity(), new Color(0xF1C232));
        y = bar(g, x, y, "wariness", a.wariness(), new Color(0x8E7CC3));
        y = bar(g, x, y, "rest", a.rest(), new Color(0x93C47D));
        y = bar(g, x, y, "burst", Math.min(1f, a.burstDrive() / 1.5f), new Color(0x6FA8DC));
        g.drawString(String.format("%.1f b/s  %.1f blk", a.velocity().length(), a.ownerDistance()), x, y + 4);
        if (a.onGround()) {
            g.setColor(new Color(0, 0, 0, 120));
            g.fillRect(4, 158, 196, 34);
            g.setColor(Color.WHITE);
            g.drawString(String.format("ground %s  lag %.1f%s", a.groundPhase().toLowerCase(java.util.Locale.ROOT),
                    a.groundLag(), a.groundReachable() ? "" : " stranded"), x, 172);
            g.drawString(String.format("fades %d  gap hops %d", a.groundFades(), a.gapHops()), x, 186);
        }
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
        return topView(w, h, o, blocks, a, t, trail, pxPerBlock, null);
    }

    /** As above, with the world's points of interest marked ({@code world} may be null). */
    public static BufferedImage topView(int w, int h, OwnerState o, BlockQuery blocks, CompanionAgent a,
                                        CompanionTunables t, Deque<Vec> trail, double pxPerBlock,
                                        CompanionWorld world) {
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
                if (!body && !over) {
                    if (blocks.water(x, feetY, z) || blocks.water(x, feetY - 1, z)) {
                        g.setColor(new Color(0x9CC3E6));
                        g.fillRect((int) SX.applyAsDouble((double) x), (int) SY.applyAsDouble((double) z),
                                (int) Math.ceil(pxPerBlock), (int) Math.ceil(pxPerBlock));
                    }
                    continue;
                }
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

        // Points of interest.
        if (world != null) {
            for (PointOfInterest pt : world.points()) {
                double px = SX.applyAsDouble(pt.pos().x()), py = SY.applyAsDouble(pt.pos().z());
                double pr = Math.max(3, pt.radius() * pxPerBlock);
                g.setColor(pointColor(pt.kind()));
                g.fill(new java.awt.geom.Ellipse2D.Double(px - pr, py - pr, pr * 2, pr * 2));
                g.setColor(Color.DARK_GRAY);
                g.draw(new java.awt.geom.Ellipse2D.Double(px - pr, py - pr, pr * 2, pr * 2));
            }
        }

        // The owner's trail at their feet, which a benthic on the ground follows.
        g.setColor(new Color(120, 90, 40, 140));
        for (Vec c : a.footTrail()) {
            double tx = SX.applyAsDouble(c.x()), ty = SY.applyAsDouble(c.z());
            g.fill(new java.awt.geom.Ellipse2D.Double(tx - 1.5, ty - 1.5, 3, 3));
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
            case "INSPECT" -> new Color(0xBF9000);
            case "HIDE" -> new Color(0x674EA7);
            case "SETTLE" -> new Color(0x6AA84F);
            case "GUARD" -> new Color(0x990000);
            case "NUZZLE" -> new Color(0xC27BA0);
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

    private static int floor(double v) {
        return (int) Math.floor(v);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
