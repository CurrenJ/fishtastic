package grill24.fishtastic.client.companion;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

/**
 * One frame's docked-companion draw (Phase 0 spike, docs/fish-companions.md §5.1).
 *
 * @param projection the camera's own perspective cropped to this rectangle (an off-axis frustum),
 *                   times its view rotation, so the fish lands in the texture exactly where the
 *                   world pass would have put it
 * @param fishPose   camera-relative, world-oriented transform of the companion's box centre,
 *                   including the dock's miniature scale and turn
 */
public record CompanionPipRenderState(Matrix4f projection, Matrix4f fishPose, int light, float gameTimeTicks,
                                      int x0, int y0, int x1, int y1, Matrix3x2f pose,
                                      @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
        implements PictureInPictureRenderState {

    public CompanionPipRenderState(Matrix4f projection, Matrix4f fishPose, int light, float gameTimeTicks,
                                   int x0, int y0, int x1, int y1, Matrix3x2f pose) {
        this(projection, fishPose, light, gameTimeTicks, x0, y0, x1, y1, pose, null,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, null));
    }

    @Override
    public float scale() {
        return 1.0f;
    }
}
