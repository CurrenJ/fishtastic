package grill24.fishtastic.shapepreviewer;

import com.google.gson.JsonObject;
import grill24.fishtastic.shapegen.TankFace;
import grill24.fishtastic.shapegen.TankShapeGeometryStrategies;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;

import java.util.EnumMap;
import java.util.Map;

/**
 * Live parametric previewer for fish tank shape geometry — Phase 1/2 of
 * docs/fish-tanks.md. Renders the exact same geometry the real datagen produces,
 * via the shared {@code tools/tank-shape-gen} library (not a port), so a new shape can be
 * designed and checked here before ever touching the mod's build.
 *
 * <p>One parameter group: which of the 6 faces are "open" (connected to a neighboring tank —
 * the permutation every existing tank already depends on). The shape list is read straight off
 * {@link TankShapeGeometryStrategies#ALL} — the same list datagen and the geometry-safety test
 * consume — so a shape added there shows up here with no previewer edit at all. (It used to be a
 * hand-maintained dropdown paired with three parallel index switches, which is exactly why four
 * shipped shapes were missing from it.) A shape isn't a set of tunable sliders: its geometry comes
 * from a reference image, see the {@code tank-shape-image-to-datagen} skill.
 *
 * <p><b>Known simplification:</b> textures aren't wired up yet — the default frame/glass/sand
 * textures are vanilla Minecraft assets that don't exist anywhere in this repo (they ship inside
 * the Minecraft client jar), so parts are shown in flat representative colors instead.
 */
public class TankShapePreviewerApp extends Application {

    private final Group tankGroup = new Group();
    private final Map<TankFace, CheckBox> faceCheckboxes = new EnumMap<>(TankFace.class);

    private ChoiceBox<String> shapeChoice;

    private double anchorX, anchorY;
    private double anchorAngleX;
    private double anchorAngleY;
    private final Rotate rotateX = new Rotate(-20, Rotate.X_AXIS);
    private final Rotate rotateY = new Rotate(-30, Rotate.Y_AXIS);

    @Override
    public void start(Stage stage) {
        // Center the tank (roughly 160 scene units across) on the rotation pivot instead of
        // orbiting around a corner.
        tankGroup.setTranslateX(-80);
        tankGroup.setTranslateY(80);
        tankGroup.setTranslateZ(-80);

        // Hand-built meshes carry no vertex normals, so shape shading from a directional/point
        // light would be undefined — ambient light illuminates every face uniformly regardless,
        // which is all a flat "does this geometry look right" preview needs.
        Group world = new Group(tankGroup, new AmbientLight(Color.WHITE));
        world.getTransforms().addAll(rotateY, rotateX);

        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(3000);
        camera.setTranslateZ(-400);

        SubScene subScene = new SubScene(world, 800, 600, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.rgb(30, 34, 38));
        subScene.setCamera(camera);

        subScene.setOnMousePressed(e -> {
            anchorX = e.getSceneX();
            anchorY = e.getSceneY();
            anchorAngleX = rotateX.getAngle();
            anchorAngleY = rotateY.getAngle();
        });
        subScene.setOnMouseDragged(e -> {
            rotateY.setAngle(anchorAngleY + (e.getSceneX() - anchorX) * 0.4);
            rotateX.setAngle(anchorAngleX - (e.getSceneY() - anchorY) * 0.4);
        });
        subScene.setOnScroll(e -> camera.setTranslateZ(Math.min(-40, camera.getTranslateZ() + e.getDeltaY() * 0.8)));

        VBox controls = buildControls();

        BorderPane root = new BorderPane();
        root.setLeft(controls);
        root.setCenter(subScene);
        subScene.widthProperty().bind(root.widthProperty().subtract(controls.widthProperty()));
        subScene.heightProperty().bind(root.heightProperty());

        Scene scene = new Scene(root, 1150, 760);
        stage.setTitle("Fishtastic — Tank Shape Previewer");
        stage.setScene(scene);
        stage.show();

        regenerate();
    }

    private VBox buildControls() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(16));
        box.setPrefWidth(260);
        box.setStyle("-fx-background-color: #20242a;");

        box.getChildren().add(sectionTitle("Shape"));
        shapeChoice = new ChoiceBox<>();
        for (TankShapeGeometryStrategies.Strategy strategy : TankShapeGeometryStrategies.ALL) {
            shapeChoice.getItems().add(strategy.name().toUpperCase());
        }
        // Optional initial-shape override via a "shape:<name>" raw arg (e.g. `shape:faceted`),
        // used by the screenshot workflow to render a specific shape without UI interaction. An
        // unknown name falls back to the first shape rather than failing the launch.
        int initialIndex = 0;
        for (String arg : getParameters().getRaw()) {
            if (arg.startsWith("shape:")) {
                String name = arg.substring("shape:".length()).toLowerCase();
                int found = shapeChoice.getItems().indexOf(name.toUpperCase());
                initialIndex = Math.max(found, 0);
            }
        }
        shapeChoice.getSelectionModel().select(initialIndex);
        // Listener added after the initial selection so it doesn't fire regenerate() before the
        // rest of buildControls() (checkboxes) has finished constructing.
        shapeChoice.getSelectionModel().selectedIndexProperty().addListener((obs, was, is) -> regenerate());
        box.getChildren().add(shapeChoice);
        box.getChildren().add(hintLabel("Every shape in TankShapeGeometryStrategies.ALL — the same list "
                + "datagen builds from. Each one's geometry is read off a reference image in "
                + "docs/tank-shapes/."));
        box.getChildren().add(new Separator());

        Label openFacesTitle = sectionTitle("Open faces");
        box.getChildren().add(openFacesTitle);

        Label hint = hintLabel("Toggles which faces connect to a neighboring tank — the same "
                + "64-permutation geometry the real mod generates.");
        box.getChildren().add(hint);

        Label permLabel = new Label();
        permLabel.setStyle("-fx-text-fill: #6fd3ae; -fx-font-family: monospace;");

        for (TankFace face : TankFace.values()) {
            CheckBox cb = new CheckBox(face.name());
            cb.setStyle("-fx-text-fill: white;");
            cb.selectedProperty().addListener((obs, was, is) -> {
                regenerate();
                permLabel.setText("permutation = " + permutationIndex());
            });
            faceCheckboxes.put(face, cb);
            box.getChildren().add(cb);
        }

        permLabel.setText("permutation = " + permutationIndex());
        box.getChildren().add(permLabel);

        return box;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        return label;
    }

    private Label hintLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #a0a8b0; -fx-font-size: 11px;");
        label.setWrapText(true);
        return label;
    }

    private int permutationIndex() {
        int index = 0;
        for (TankFace face : TankFace.values()) {
            if (faceCheckboxes.get(face).isSelected()) {
                index |= 1 << face.ordinal();
            }
        }
        return index;
    }

    private void regenerate() {
        int perm = permutationIndex();
        int idx = Math.max(shapeChoice.getSelectionModel().getSelectedIndex(), 0);
        TankShapeGeometryStrategies.Strategy strategy = TankShapeGeometryStrategies.ALL.get(idx);

        JsonObject frame = strategy.frame().apply(perm);
        JsonObject sand = strategy.sand().apply(perm);
        JsonObject glass = strategy.glass().apply(perm);

        tankGroup.getChildren().setAll(
                TankGeometryMeshBuilder.build(frame, Color.rgb(158, 118, 74), false),
                TankGeometryMeshBuilder.build(sand, Color.rgb(219, 201, 145), false),
                TankGeometryMeshBuilder.build(glass, Color.rgb(120, 190, 220, 0.55), true)
        );
    }

    public static void main(String[] args) {
        launch(args);
    }
}
