package grill24.fishtastic.client.companion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import grill24.fishsim.core.Locomotion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Draws a perched companion fish on its owner's body (docs/fish-companions.md §3, §11): a benthic
 * fish on a shoulder or the head, an anchored one poking out of the back. The same shape as
 * vanilla's {@code ParrotOnShoulderLayer} — a player render layer, not the world hook — because a
 * world-hook fish would keep its place in the world while the body it sits on turns and walks.
 *
 * <p>This layer is never drawn for your own player in first person (vanilla does not draw your own
 * player model there), which is exactly why the peeks of §3.1 exist: the perched fish hops off to
 * show itself. It <em>is</em> drawn in third person and for every other player.
 *
 * <p>Registered by {@code AvatarRendererMixin} so both loaders and both player model variants get
 * it from one place.
 */
public final class CompanionPerchLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public CompanionPerchLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
                       AvatarRenderState state, float yRot, float xRot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || state.isInvisible) return;
        Entity entity = mc.level.getEntity(state.id);
        if (!(entity instanceof Player player)) return; // mannequins and anything else: no companion
        ClientCompanion companion = ClientCompanions.get(player);
        if (companion == null || !ClientCompanions.shouldDrawOnPerch(companion)) return;

        // The layer frame after LivingEntityRenderer's setup (body yaw, then scale(-1,-1,1),
        // 0.9375 model scale, translate(0,-1.501,0)) puts y=0 at ~1.41 blocks — the shoulder
        // line. x is mirrored by the flip, z is not, and the model faces -z. (Vanilla's parrot
        // layer passes -1.5 here only because its own model pose hoists it back down again.)
        int slot = companion.perchSlot();
        float side = switch (slot) {
            case 0 -> 0.42f;   // right shoulder
            case 1 -> -0.42f;  // left shoulder
            default -> 0f;     // crown of the head
        };
        float height = switch (slot) {
            case 0, 1 -> 0f;       // shoulder line
            default -> -0.5f;      // head top
        };
        if (companion.locomotion() == Locomotion.ANCHORED) {
            // Out of the upper back: the model's back face is +z.
            side = 0.05f;
            height = -0.12f;
        }
        float z = companion.locomotion() == Locomotion.ANCHORED ? 0.26f : 0f;
        float crouchDrop = state.isCrouching ? 0.2f : 0f;

        poseStack.pushPose();
        poseStack.translate(side, height + crouchDrop, z);
        // A flat pose (a starfish's floor sit) is stood up on a shoulder so the sprite reads from
        // the front, like a fish pinned to the outside of the arm; on the crown it stays flat.
        boolean standUp = slot != 2 && companion.perchStandsUp();
        if (standUp) {
            poseStack.mulPose(Axis.XP.rotationDegrees(90f));
        }
        if (slot == 2) {
            poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
        }
        companion.submitPoseOnly(poseStack, collector, lightCoords,
                ClientCompanion.gameTime(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)),
                180f, 0f);
        poseStack.popPose();
    }
}
