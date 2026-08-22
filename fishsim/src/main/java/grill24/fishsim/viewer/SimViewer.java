package grill24.fishsim.viewer;

import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishsim.harness.Scenarios;
import grill24.fishsim.render.FrameRenderer;

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
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The interactive tuning shell (docs/fish-sim-engine-plan.md §3.3): a plain-Swing desktop window
 * (no plugins, never shipped) blitting the exact same {@link FrameRenderer} the headless export
 * uses — the shared renderer is what guarantees the two can never disagree. Side view on top
 * (the player's view through the glass), optional top view below for depth-layer behavior.
 *
 * <p>Run with {@code ./gradlew :fishsim:runViewer}. Pause / single-step / speed, reseed, domain
 * and fish-count switches, overlay toggles, and live {@link Tunables} sliders (applied to the
 * running engine without re-scattering).
 */
public final class SimViewer {

    /** One adjustable tunable: label, slider range, and how to write it back into the record. */
    private record Knob(String label, float min, float max,
                        Function<Tunables, Float> get, KnobApply apply) {}

    private interface KnobApply {
        Tunables with(Tunables t, float v);
    }

    private static final List<Knob> KNOBS = List.of(
            new Knob("maxSpeed", 0.02f, 0.4f, Tunables::maxSpeed,
                    (t, v) -> new Tunables(t.dt(), t.gateFactor(), v, t.cruiseSpeed(), t.steeringGain(), t.maxForce(), t.neighborCount(), t.separationRadius(), t.separationSpeed(), t.alignmentWeight(), t.cohesionSpeed(), t.depthRestore(), t.depthDamp(), t.verticalDamp(), t.wallMargin(), t.wallMarginVertical(), t.wallAvoidSpeed(), t.bankGain(), t.bankMax(), t.headingDeadzone(), t.tankHalfExtent(), t.swarmMinSep(), t.layerZ(), t.neighborRange(), t.patrolSpeed(), t.separationRadiusOther())),
            new Knob("cruiseSpeed", 0.01f, 0.3f, Tunables::cruiseSpeed,
                    (t, v) -> new Tunables(t.dt(), t.gateFactor(), t.maxSpeed(), v, t.steeringGain(), t.maxForce(), t.neighborCount(), t.separationRadius(), t.separationSpeed(), t.alignmentWeight(), t.cohesionSpeed(), t.depthRestore(), t.depthDamp(), t.verticalDamp(), t.wallMargin(), t.wallMarginVertical(), t.wallAvoidSpeed(), t.bankGain(), t.bankMax(), t.headingDeadzone(), t.tankHalfExtent(), t.swarmMinSep(), t.layerZ(), t.neighborRange(), t.patrolSpeed(), t.separationRadiusOther())),
            new Knob("steeringGain", 0.2f, 8f, Tunables::steeringGain,
                    (t, v) -> new Tunables(t.dt(), t.gateFactor(), t.maxSpeed(), t.cruiseSpeed(), v, t.maxForce(), t.neighborCount(), t.separationRadius(), t.separationSpeed(), t.alignmentWeight(), t.cohesionSpeed(), t.depthRestore(), t.depthDamp(), t.verticalDamp(), t.wallMargin(), t.wallMarginVertical(), t.wallAvoidSpeed(), t.bankGain(), t.bankMax(), t.headingDeadzone(), t.tankHalfExtent(), t.swarmMinSep(), t.layerZ(), t.neighborRange(), t.patrolSpeed(), t.separationRadiusOther())),
            new Knob("maxForce", 0.05f, 2f, Tunables::maxForce,
                    (t, v) -> new Tunables(t.dt(), t.gateFactor(), t.maxSpeed(), t.cruiseSpeed(), t.steeringGain(), v, t.neighborCount(), t.separationRadius(), t.separationSpeed(), t.alignmentWeight(), t.cohesionSpeed(), t.depthRestore(), t.depthDamp(), t.verticalDamp(), t.wallMargin(), t.wallMarginVertical(), t.wallAvoidSpeed(), t.bankGain(), t.bankMax(), t.headingDeadzone(), t.tankHalfExtent(), t.swarmMinSep(), t.layerZ(), t.neighborRange(), t.patrolSpeed(), t.separationRadiusOther())),
            new Knob("separationRadius", 0.05f, 0.6f, Tunables::separationRadius,
                    (t, v) -> new Tunables(t.dt(), t.gateFactor(), t.maxSpeed(), t.cruiseSpeed(), t.steeringGain(), t.maxForce(), t.neighborCount(), v, t.separationSpeed(), t.alignmentWeight(), t.cohesionSpeed(), t.depthRestore(), t.depthDamp(), t.verticalDamp(), t.wallMargin(), t.wallMarginVertical(), t.wallAvoidSpeed(), t.bankGain(), t.bankMax(), t.headingDeadzone(), t.tankHalfExtent(), t.swarmMinSep(), t.layerZ(), t.neighborRange(), t.patrolSpeed(), t.separationRadiusOther())),
            new Knob("separationSpeed", 0.02f, 0.8f, Tunables::separationSpeed,
                    (t, v) -> new Tunables(t.dt(), t.gateFactor(), t.maxSpeed(), t.cruiseSpeed(), t.steeringGain(), t.maxForce(), t.neighborCount(), t.separationRadius(), v, t.alignmentWeight(), t.cohesionSpeed(), t.depthRestore(), t.depthDamp(), t.verticalDamp(), t.wallMargin(), t.wallMarginVertical(), t.wallAvoidSpeed(), t.bankGain(), t.bankMax(), t.headingDeadzone(), t.tankHalfExtent(), t.swarmMinSep(), t.layerZ(), t.neighborRange(), t.patrolSpeed(), t.separationRadiusOther())),
            new Knob("alignmentWeight", 0f, 1.5f, Tunables::alignmentWeight,
                    (t, v) -> new Tunables(t.dt(), t.gateFactor(), t.maxSpeed(), t.cruiseSpeed(), t.steeringGain(), t.maxForce(), t.neighborCount(), t.separationRadius(), t.separationSpeed(), v, t.cohesionSpeed(), t.depthRestore(), t.depthDamp(), t.verticalDamp(), t.wallMargin(), t.wallMarginVertical(), t.wallAvoidSpeed(), t.bankGain(), t.bankMax(), t.headingDeadzone(), t.tankHalfExtent(), t.swarmMinSep(), t.layerZ(), t.neighborRange(), t.patrolSpeed(), t.separationRadiusOther())),
            new Knob("cohesionSpeed", 0f, 0.5f, Tunables::cohesionSpeed,
                    (t, v) -> new Tunables(t.dt(), t.gateFactor(), t.maxSpeed(), t.cruiseSpeed(), t.steeringGain(), t.maxForce(), t.neighborCount(), t.separationRadius(), t.separationSpeed(), t.alignmentWeight(), v, t.depthRestore(), t.depthDamp(), t.verticalDamp(), t.wallMargin(), t.wallMarginVertical(), t.wallAvoidSpeed(), t.bankGain(), t.bankMax(), t.headingDeadzone(), t.tankHalfExtent(), t.swarmMinSep(), t.layerZ(), t.neighborRange(), t.patrolSpeed(), t.separationRadiusOther())),
            new Knob("depthRestore", 0.1f, 5f, Tunables::depthRestore,
                    (t, v) -> new Tunables(t.dt(), t.gateFactor(), t.maxSpeed(), t.cruiseSpeed(), t.steeringGain(), t.maxForce(), t.neighborCount(), t.separationRadius(), t.separationSpeed(), t.alignmentWeight(), t.cohesionSpeed(), v, t.depthDamp(), t.verticalDamp(), t.wallMargin(), t.wallMarginVertical(), t.wallAvoidSpeed(), t.bankGain(), t.bankMax(), t.headingDeadzone(), t.tankHalfExtent(), t.swarmMinSep(), t.layerZ(), t.neighborRange(), t.patrolSpeed(), t.separationRadiusOther())),
            new Knob("wallMargin", 0.04f, 0.5f, Tunables::wallMargin,
                    (t, v) -> new Tunables(t.dt(), t.gateFactor(), t.maxSpeed(), t.cruiseSpeed(), t.steeringGain(), t.maxForce(), t.neighborCount(), t.separationRadius(), t.separationSpeed(), t.alignmentWeight(), t.cohesionSpeed(), t.depthRestore(), t.depthDamp(), t.verticalDamp(), v, t.wallMarginVertical(), t.wallAvoidSpeed(), t.bankGain(), t.bankMax(), t.headingDeadzone(), t.tankHalfExtent(), t.swarmMinSep(), t.layerZ(), t.neighborRange(), t.patrolSpeed(), t.separationRadiusOther())),
            new Knob("wallAvoidSpeed", 0.05f, 1f, Tunables::wallAvoidSpeed,
                    (t, v) -> new Tunables(t.dt(), t.gateFactor(), t.maxSpeed(), t.cruiseSpeed(), t.steeringGain(), t.maxForce(), t.neighborCount(), t.separationRadius(), t.separationSpeed(), t.alignmentWeight(), t.cohesionSpeed(), t.depthRestore(), t.depthDamp(), t.verticalDamp(), t.wallMargin(), t.wallMarginVertical(), v, t.bankGain(), t.bankMax(), t.headingDeadzone(), t.tankHalfExtent(), t.swarmMinSep(), t.layerZ(), t.neighborRange(), t.patrolSpeed(), t.separationRadiusOther())),
            new Knob("headingDeadzone", 0f, 0.08f, Tunables::headingDeadzone,
                    (t, v) -> new Tunables(t.dt(), t.gateFactor(), t.maxSpeed(), t.cruiseSpeed(), t.steeringGain(), t.maxForce(), t.neighborCount(), t.separationRadius(), t.separationSpeed(), t.alignmentWeight(), t.cohesionSpeed(), t.depthRestore(), t.depthDamp(), t.verticalDamp(), t.wallMargin(), t.wallMarginVertical(), t.wallAvoidSpeed(), t.bankGain(), t.bankMax(), v, t.tankHalfExtent(), t.swarmMinSep(), t.layerZ(), t.neighborRange(), t.patrolSpeed(), t.separationRadiusOther())),
            new Knob("neighborRange", 0.2f, 4f, t -> Math.min(t.neighborRange(), 4f),
                    (t, v) -> new Tunables(t.dt(), t.gateFactor(), t.maxSpeed(), t.cruiseSpeed(), t.steeringGain(), t.maxForce(), t.neighborCount(), t.separationRadius(), t.separationSpeed(), t.alignmentWeight(), t.cohesionSpeed(), t.depthRestore(), t.depthDamp(), t.verticalDamp(), t.wallMargin(), t.wallMarginVertical(), t.wallAvoidSpeed(), t.bankGain(), t.bankMax(), t.headingDeadzone(), t.tankHalfExtent(), t.swarmMinSep(), t.layerZ(), v, t.patrolSpeed(), t.separationRadiusOther())),
            new Knob("patrolSpeed", 0f, 0.2f, Tunables::patrolSpeed,
                    (t, v) -> new Tunables(t.dt(), t.gateFactor(), t.maxSpeed(), t.cruiseSpeed(), t.steeringGain(), t.maxForce(), t.neighborCount(), t.separationRadius(), t.separationSpeed(), t.alignmentWeight(), t.cohesionSpeed(), t.depthRestore(), t.depthDamp(), t.verticalDamp(), t.wallMargin(), t.wallMarginVertical(), t.wallAvoidSpeed(), t.bankGain(), t.bankMax(), t.headingDeadzone(), t.tankHalfExtent(), t.swarmMinSep(), t.layerZ(), t.neighborRange(), v, t.separationRadiusOther())),
            new Knob("sepRadiusOther", 0.05f, 1.2f, Tunables::separationRadiusOther,
                    (t, v) -> new Tunables(t.dt(), t.gateFactor(), t.maxSpeed(), t.cruiseSpeed(), t.steeringGain(), t.maxForce(), t.neighborCount(), t.separationRadius(), t.separationSpeed(), t.alignmentWeight(), t.cohesionSpeed(), t.depthRestore(), t.depthDamp(), t.verticalDamp(), t.wallMargin(), t.wallMarginVertical(), t.wallAvoidSpeed(), t.bankGain(), t.bankMax(), t.headingDeadzone(), t.tankHalfExtent(), t.swarmMinSep(), t.layerZ(), t.neighborRange(), t.patrolSpeed(), v))
    );

    private FlockEngine engine;
    private Tunables tunables = Tunables.GROUP; // initial domain is a voxel group
    private FrameRenderer renderer = new FrameRenderer(120, true, false);
    private final List<Runnable> knobSyncs = new ArrayList<>();

    private String domainName = "L";
    private int fishCount = 12;
    private long seed = 12345L;
    private boolean playing = true;
    private int stepsPerTick = 1;
    private boolean showTop = true;
    private boolean showHeatmap = true;
    private boolean showVelocities = false;

    private JPanel canvas;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SimViewer().start());
    }

    private void start() {
        rebuildEngine();

        JFrame frame = new JFrame("fishsim viewer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        canvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                BufferedImage side = renderer.render(engine, tunables, FrameRenderer.View.SIDE);
                g.drawImage(side, 10, 10, null);
                if (showTop) {
                    BufferedImage top = renderer.render(engine, tunables, FrameRenderer.View.TOP);
                    g.drawImage(top, 10, side.getHeight() + 20, null);
                }
            }
        };
        canvas.setPreferredSize(new Dimension(560, 700));
        frame.add(canvas, BorderLayout.CENTER);
        frame.add(buildControls(), BorderLayout.EAST);

        // Fixed 20 Hz sim clock — the viewer never advances time faster than stepsPerTick per
        // 50 ms tick; pause keeps rendering (so slider effects on the renderer stay visible).
        new Timer(50, e -> {
            if (playing) {
                for (int s = 0; s < stepsPerTick; s++) engine.step();
            }
            canvas.repaint();
        }).start();

        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    /** The mod's canonical parameter set for the current domain kind. */
    private Tunables canonicalTunables() {
        return domainName.equals("box") ? Tunables.DEFAULT : Tunables.GROUP;
    }

    private void rebuildEngine() {
        engine = new FlockEngine(tunables);
        if (domainName.equals("box")) {
            engine.rebuild(Scenarios.specs(fishCount, seed), seed, 0f, 3, 0.35f, 0.3f, 20f);
        } else {
            engine.rebuild(Scenarios.specs(fishCount, seed), seed, 0f, 20f,
                    new VoxelDomain(Scenarios.occupancy(domainName)));
        }
    }

    private JScrollPane buildControls() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // ── Run controls ──
        JPanel run = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton pause = new JButton("Pause");
        pause.addActionListener(e -> {
            playing = !playing;
            pause.setText(playing ? "Pause" : "Play");
        });
        JButton step = new JButton("Step");
        step.addActionListener(e -> {
            playing = false;
            pause.setText("Play");
            engine.step();
            canvas.repaint();
        });
        JButton reseed = new JButton("Reseed");
        reseed.addActionListener(e -> {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            rebuildEngine();
        });
        run.add(pause);
        run.add(step);
        run.add(reseed);
        panel.add(run);

        // ── Speed ──
        JPanel speedRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        speedRow.add(new JLabel("speed ×"));
        JComboBox<Integer> speedBox = new JComboBox<>(new Integer[]{1, 2, 4, 8});
        speedBox.addActionListener(e -> stepsPerTick = (Integer) speedBox.getSelectedItem());
        speedRow.add(speedBox);
        panel.add(speedRow);

        // ── Scenario ──
        JPanel scenario = new JPanel(new FlowLayout(FlowLayout.LEFT));
        scenario.add(new JLabel("domain"));
        JComboBox<String> domainBox = new JComboBox<>(new String[]{"box", "1x1x1", "3x1x1", "L", "2x2slab", "4x1x2"});
        domainBox.setSelectedItem(domainName);
        domainBox.addActionListener(e -> {
            domainName = (String) domainBox.getSelectedItem();
            tunables = canonicalTunables(); // box and voxel domains each start from their canonical set
            rebuildEngine();
            knobSyncs.forEach(Runnable::run);
        });
        scenario.add(domainBox);
        scenario.add(new JLabel("fish"));
        JSpinner fishSpinner = new JSpinner(new SpinnerNumberModel(fishCount, 1, 40, 1));
        fishSpinner.addChangeListener(e -> {
            fishCount = (Integer) fishSpinner.getValue();
            rebuildEngine();
        });
        scenario.add(fishSpinner);
        panel.add(scenario);

        // ── Overlays ──
        JCheckBox topBox = new JCheckBox("top view", showTop);
        topBox.addActionListener(e -> showTop = topBox.isSelected());
        JCheckBox heatBox = new JCheckBox("distance-field heatmap", showHeatmap);
        heatBox.addActionListener(e -> {
            showHeatmap = heatBox.isSelected();
            renderer = new FrameRenderer(120, showHeatmap, showVelocities);
        });
        JCheckBox velBox = new JCheckBox("velocity vectors", showVelocities);
        velBox.addActionListener(e -> {
            showVelocities = velBox.isSelected();
            renderer = new FrameRenderer(120, showHeatmap, showVelocities);
        });
        panel.add(topBox);
        panel.add(heatBox);
        panel.add(velBox);
        panel.add(Box.createVerticalStrut(10));

        // ── Live tunables ──
        List<Runnable> resetters = knobSyncs;
        for (Knob knob : KNOBS) {
            JLabel label = new JLabel();
            JSlider slider = new JSlider(0, 1000);
            Runnable sync = () -> {
                float v = knob.get().apply(tunables);
                slider.setValue(Math.round((v - knob.min()) / (knob.max() - knob.min()) * 1000f));
                label.setText(knob.label() + " = " + String.format("%.3f", v));
            };
            sync.run();
            resetters.add(sync);
            slider.addChangeListener(e -> {
                float v = knob.min() + slider.getValue() / 1000f * (knob.max() - knob.min());
                tunables = knob.apply().with(tunables, v);
                engine.setTunables(tunables);
                label.setText(knob.label() + " = " + String.format("%.3f", v));
            });
            panel.add(label);
            panel.add(slider);
        }
        JButton reset = new JButton("Reset tunables");
        reset.addActionListener(e -> {
            tunables = canonicalTunables();
            engine.setTunables(tunables);
            resetters.forEach(Runnable::run);
        });
        panel.add(Box.createVerticalStrut(6));
        panel.add(reset);

        JScrollPane scroll = new JScrollPane(panel);
        scroll.setPreferredSize(new Dimension(300, 700));
        return scroll;
    }
}
