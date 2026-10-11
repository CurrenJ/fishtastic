package grill24.fishsim.viewer;

import grill24.fishsim.companion.CompanionAgent;
import grill24.fishsim.companion.CompanionTunables;
import grill24.fishsim.companion.LocalVoxels;
import grill24.fishsim.companion.OwnerState;
import grill24.fishsim.companion.Vec;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.harness.companion.CompanionRun;
import grill24.fishsim.harness.companion.OwnerScript;
import grill24.fishsim.harness.companion.VoxelScene;
import grill24.fishsim.render.CompanionRenderer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The companion playground (docs/fish-companions-sim-plan.md §4.3): walk an owner around a scene
 * with the keyboard and mouse and watch the fish decide what to do — in first person, as a player
 * would see it, beside a top-down map with its drives, goal and trail.
 *
 * <pre>
 *   ./gradlew :fishsim:runCompanionViewer
 *
 *   WASD move · Shift sprint · drag mouse or arrow keys to look · F first/third person
 *   T teleport 60 blocks ahead · Space pause · N step one tick · R reseed the fish
 * </pre>
 *
 * The "owner" dropdown switches between manual control and any headless owner script (played
 * live); the sliders write straight into the running fish's tunables.
 */
public final class CompanionViewer {

    private static final int FP_W = 960, FP_H = 540, TOP_W = 400;
    private static final int TRAIL = 160;
    private static final String MANUAL = "manual (WASD)";

    private final CompanionTunables tunables = CompanionTunables.defaults();
    private final LocalVoxels renderCache = new LocalVoxels();
    private final ArrayDeque<Vec> trail = new ArrayDeque<>();
    private final Set<Integer> keys = new HashSet<>();
    private final List<Runnable> sliderRefreshers = new ArrayList<>();

    private VoxelScene scene;
    private OwnerScript script;          // null in manual mode
    private OwnerScript.Pilot pilot;     // manual mode
    private CompanionAgent agent;
    private OwnerState owner;
    private long seed = 1;
    private Locomotion locomotion = Locomotion.FREE_SWIM;
    private float lookYaw, lookPitch;
    private boolean paused, stepOnce, teleportNext;
    private long lastNanos = System.nanoTime();
    private double accumulator;
    private int dragX, dragY;

    private final JPanel canvas = new JPanel() {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            paintView((Graphics2D) g);
        }
    };

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new CompanionViewer().open());
    }

    private void open() {
        reset("open", MANUAL);

        JFrame frame = new JFrame("Fish companion — playground");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        canvas.setPreferredSize(new Dimension(FP_W + TOP_W, FP_H));
        canvas.setFocusable(true);
        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                keys.add(e.getKeyCode());
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_SPACE -> paused = !paused;
                    case KeyEvent.VK_N -> stepOnce = true;
                    case KeyEvent.VK_F -> pilotOrScript().firstPerson = !pilotOrScript().firstPerson;
                    case KeyEvent.VK_T -> teleportNext = true;
                    case KeyEvent.VK_R -> {
                        seed++;
                        agent = newAgent();
                        trail.clear();
                    }
                    default -> { }
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                keys.remove(e.getKeyCode());
            }
        });
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragX = e.getX();
                dragY = e.getY();
                canvas.requestFocusInWindow();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                lookYaw += (e.getX() - dragX) * 0.3f;
                lookPitch = Math.max(-90f, Math.min(90f, lookPitch + (e.getY() - dragY) * 0.3f));
                dragX = e.getX();
                dragY = e.getY();
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);
        frame.add(canvas, BorderLayout.CENTER);
        frame.add(controls(), BorderLayout.EAST);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        canvas.requestFocusInWindow();

        new Timer(15, e -> {
            advance();
            canvas.repaint();
        }).start();
    }

    private OwnerScript.Pilot pilotOrScript() {
        return script != null ? script.pilot() : pilot;
    }

    private void reset(String sceneName, String ownerMode) {
        if (ownerMode.equals(MANUAL)) {
            script = null;
            scene = VoxelScene.named(sceneName);
            Vec start = switch (sceneName) {
                case "corridor" -> new Vec(0.5, scene.feetY(), -6.5);
                case "forest" -> new Vec(4, scene.feetY(), 4);
                case "stairwell" -> new Vec(1.5, scene.feetY(), -4.5);
                default -> new Vec(0.5, scene.feetY(), 0.5);
            };
            pilot = new OwnerScript.Pilot(start, 0f);
            pilot.world = scene;
            pilot.terrain = true;
            lookYaw = 0f;
            lookPitch = 0f;
            pilot.beginTick();
            owner = pilot.endTick();
        } else {
            script = OwnerScript.named(ownerMode, seed);
            scene = script.scene;
            owner = script.next();
        }
        agent = newAgent();
        trail.clear();
        accumulator = 0;
    }

    private CompanionAgent newAgent() {
        return new CompanionAgent(tunables, seed, locomotion, CompanionRun.lengthOf(locomotion), owner, scene);
    }

    /** Runs whole 20 Hz ticks for the real time that has passed, then interpolates the remainder. */
    private void advance() {
        long now = System.nanoTime();
        double elapsed = Math.min(0.25, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (paused && !stepOnce) {
            agent.interpolate((float) (accumulator / CompanionAgent.DT));
            return;
        }
        accumulator += paused ? CompanionAgent.DT : elapsed;
        stepOnce = false;
        while (accumulator >= CompanionAgent.DT) {
            accumulator -= CompanionAgent.DT;
            tick();
        }
        agent.interpolate((float) (accumulator / CompanionAgent.DT));
    }

    private void tick() {
        if (script != null) {
            owner = script.next();
        } else {
            float yawStep = (keys.contains(KeyEvent.VK_LEFT) ? -5f : 0f) + (keys.contains(KeyEvent.VK_RIGHT) ? 5f : 0f);
            float pitchStep = (keys.contains(KeyEvent.VK_UP) ? -4f : 0f) + (keys.contains(KeyEvent.VK_DOWN) ? 4f : 0f);
            lookYaw += yawStep;
            lookPitch = Math.max(-90f, Math.min(90f, lookPitch + pitchStep));

            pilot.beginTick();
            pilot.look(lookYaw, lookPitch);
            Vec fwd = Vec.fromYawPitch(lookYaw, 0), right = fwd.cross(Vec.UP);
            double f = (keys.contains(KeyEvent.VK_W) ? 1 : 0) - (keys.contains(KeyEvent.VK_S) ? 1 : 0);
            double r = (keys.contains(KeyEvent.VK_D) ? 1 : 0) - (keys.contains(KeyEvent.VK_A) ? 1 : 0);
            Vec move = fwd.scale(f).add(right.scale(r)).normalize();
            boolean sprint = keys.contains(KeyEvent.VK_SHIFT) && f > 0;
            pilot.move(move.scale(sprint ? OwnerScript.SPRINT : OwnerScript.WALK));
            if (teleportNext) {
                pilot.teleport(pilot.feet.add(fwd.scale(60)));
                teleportNext = false;
            }
            owner = pilot.endTick();
        }
        agent.step(owner, scene);
        trail.addLast(agent.position());
        if (trail.size() > TRAIL) trail.removeFirst();
    }

    private void paintView(Graphics2D g) {
        renderCache.update(scene, owner.eye());
        BufferedImage fp = CompanionRenderer.firstPerson(FP_W, FP_H, owner, renderCache, agent, true);
        BufferedImage top = CompanionRenderer.topView(TOP_W, FP_H, owner, renderCache, agent, tunables, trail, 30);
        g.drawImage(fp, 0, 0, null);
        g.drawImage(top, FP_W, 0, null);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRect(0, FP_H - 22, FP_W, 22);
        g.setColor(Color.WHITE);
        g.drawString("WASD move · Shift sprint · drag/arrows look · F view · T teleport · Space pause · N step · R reseed"
                + (paused ? "   [PAUSED]" : ""), 8, FP_H - 7);
    }

    private JPanel controls() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JComboBox<String> sceneBox = new JComboBox<>(VoxelScene.NAMES.toArray(new String[0]));
        List<String> modes = new ArrayList<>();
        modes.add(MANUAL);
        modes.addAll(OwnerScript.NAMES);
        JComboBox<String> ownerBox = new JComboBox<>(modes.toArray(new String[0]));
        Runnable apply = () -> {
            reset((String) sceneBox.getSelectedItem(), (String) ownerBox.getSelectedItem());
            canvas.requestFocusInWindow();
        };
        JComboBox<String> classBox = new JComboBox<>(new String[]{"FREE_SWIM", "GLIDE", "DRIFT"});
        classBox.addActionListener(e -> {
            locomotion = Locomotion.valueOf((String) classBox.getSelectedItem());
            apply.run();
        });
        sceneBox.addActionListener(e -> apply.run());
        ownerBox.addActionListener(e -> apply.run());
        panel.add(row(new JLabel("class"), classBox));
        panel.add(row(new JLabel("scene"), sceneBox));
        panel.add(row(new JLabel("owner"), ownerBox));

        JButton defaults = new JButton("defaults");
        defaults.addActionListener(e -> {
            copyInto(CompanionTunables.defaults(), tunables);
            sliderRefreshers.forEach(Runnable::run);
            canvas.requestFocusInWindow();
        });
        panel.add(row(defaults));
        panel.add(Box.createVerticalStrut(6));

        CompanionTunables base = CompanionTunables.defaults();
        for (Field f : CompanionTunables.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try {
                if (f.getType() == boolean.class) {
                    JCheckBox box = new JCheckBox(f.getName(), f.getBoolean(tunables));
                    box.addActionListener(e -> {
                        set(f, box.isSelected());
                        canvas.requestFocusInWindow();
                    });
                    sliderRefreshers.add(() -> box.setSelected(get(f, Boolean.class)));
                    panel.add(row(box));
                } else if (f.getType() == float.class) {
                    float def = f.getFloat(base);
                    float max = def > 0 ? def * 3 : 1f;
                    JLabel label = new JLabel();
                    label.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
                    JSlider slider = new JSlider(0, 1000, Math.round(f.getFloat(tunables) / max * 1000));
                    slider.setPreferredSize(new Dimension(150, 20));
                    Runnable relabel = () -> label.setText(String.format("%-22s %7.3f", f.getName(), get(f, Float.class)));
                    slider.addChangeListener(e -> {
                        set(f, slider.getValue() / 1000f * max);
                        relabel.run();
                    });
                    slider.setFocusable(false);
                    sliderRefreshers.add(() -> {
                        slider.setValue(Math.round(get(f, Float.class) / max * 1000));
                        relabel.run();
                    });
                    relabel.run();
                    JPanel cell = new JPanel(new BorderLayout());
                    cell.add(label, BorderLayout.NORTH);
                    cell.add(slider, BorderLayout.CENTER);
                    panel.add(cell);
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setPreferredSize(new Dimension(260, FP_H));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(scroll, BorderLayout.CENTER);
        return wrap;
    }

    private static JPanel row(java.awt.Component... parts) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        for (java.awt.Component c : parts) p.add(c);
        return p;
    }

    private void set(Field f, Object value) {
        try {
            f.set(tunables, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T get(Field f, Class<T> type) {
        try {
            return type.cast(f.get(tunables));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void copyInto(CompanionTunables from, CompanionTunables to) {
        for (Field f : CompanionTunables.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.set(to, f.get(from));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
