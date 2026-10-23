package grill24.fishtastic.client.companion;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishsim.companion.PerchBody;
import grill24.fishsim.core.Locomotion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.resources.PlayerSkin;

/**
 * Draws a perched companion fish on its owner's body (docs/fish-companions.md, §11): a benthic
 * fish on a shoulder or the head, an anchored one rising from the head (party spec D5). The same shape as
 * vanilla's {@code ParrotOnShoulderLayer} — a player render layer, not the world hook — because a
 * world-hook fish would keep its place in the world while the body it sits on turns and walks.
 *
 * <p>This layer is never drawn for your own player in first person (vanilla does not draw your own
 * player model there), which is why the sim's percher hops onto the hand when its owner hasn't
 * seen it for a while (the hand is drawn by the world pass). It <em>is</em> drawn in third person and for every other player.
 *
 * <p>Registered by {@code AvatarRendererMixin} so both loaders and both player model variants get
 * it from one place.
 */
public final class CompanionPerchLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public CompanionPerchLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource collector, int lightCoords,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || player.isInvisible()) return;
        ClientCompanions.perchLayerCalls++;
        // The layer frame after LivingEntityRenderer's setup (body yaw, then scale(-1,-1,1),
        // 0.9375 model scale, translate(0,-1.501,0)) puts y=0 at ~1.41 blocks — the shoulder
        // line. x is mirrored by the flip, z is not, and the model faces -z. (Vanilla's parrot
        // layer passes -1.5 here only because its own model pose hoists it back down again.)
        // The slot is the sim's: where the companion decided to ride (plan §3.4). A benthic group
        // rides up to three at once, one per slot (left shoulder, right shoulder, head), and every
        // companion of the party draws its own riders. The party's PartyLayer gives each percher
        // its own home perch and leases the rest (party spec §6.1), so two never share a slot.
        for (ClientCompanion companion : ClientCompanions.companionsOf(player)) {
            if (!ClientCompanions.shouldDrawOnPerch(companion)) continue;
            for (int i = 0; i < companion.memberCount(); i++) {
                if (companion.drawsOnPerch(i)) submitMember(poseStack, collector, lightCoords, player, companion, i, partialTick);
            }
        }
    }

    private void submitMember(PoseStack poseStack, MultiBufferSource collector, int lightCoords,
                              AbstractClientPlayer player, ClientCompanion companion, int i, float partialTick) {
        PerchBody.Slot slot = companion.perchSlot(i);
        // In this frame +x is the player's left arm (vanilla's ParrotOnShoulderLayer puts the left
        // parrot at +0.4), and the sim holds LEFT_SHOULDER on its owner's left (-flatRight). These
        // signs were once the other way round, so a fish landing on a shoulder popped across to
        // the other one, and its name hung over the wrong arm (found 2026-10-14).
        float side = switch (slot) {
            case RIGHT_SHOULDER -> -0.42f;
            case LEFT_SHOULDER -> 0.42f;
            case BACK -> 0.05f;
            default -> 0f;       // crown of the head
        };
        float yaw = 180f;
        boolean shoulder = slot == PerchBody.Slot.LEFT_SHOULDER || slot == PerchBody.Slot.RIGHT_SHOULDER;
        if (shoulder && companion.locomotion() == Locomotion.ANCHORED) {
            // A planted fish stands on its base (FishAnimationConfig.Planted.baseX), so the base
            // goes on the middle of the arm's top, not the 0.42 that keeps wide sprites clear of
            // the head: vanilla's arm box is 4..8 px out, 4..7 for slim arms.
            side = Math.signum(side) * (player.getSkin().model() == PlayerSkin.Model.SLIM ? 5.5f : 6f) / 16f;
            // Its art leans away from its base; turn it so the lean points away from the head
            // (owner feedback 2026-10-14: the left-shoulder eel curved into the head). Art leaning
            // right (base on the left) already leans outward on the right shoulder, as drawn here
            // (the stack's x is mirrored); on the left shoulder it turns round.
            float baseX = companion.perchBaseX(i);
            if (baseX != 0f && (slot == PerchBody.Slot.LEFT_SHOULDER) == (baseX < 0f)) yaw = 0f;
        }
        poseStack.pushPose();
        if (slot == PerchBody.Slot.HEAD) {
            // Anchor before applying the offset: the crown rotates about the head's actual
            // pivot, including pitch, yaw and the crouching/swimming model pose.
            getParentModel().head.translateAndRotate(poseStack);
            poseStack.translate(0f, -0.5f - 0.5f / 16f, 0f); // top of the hat layer
        } else if (slot == PerchBody.Slot.BACK) {
            poseStack.translate(side, -0.12f + (player.isCrouching() ? 0.2f : 0f), 0.26f);
        } else {
            getParentModel().body.translateAndRotate(poseStack);
            poseStack.translate(side, -0.25f / 16f, 0f); // shoulder surface, including sleeve
        }
        if (slot != PerchBody.Slot.BACK) {
            // Player models use down-positive Y and mirrored X. FishAnimator expects up-positive
            // Y: undo that frame flip so crabs stand upright and floor-sitters stay face-up/flat.
            poseStack.scale(-1f, -1f, 1f);
            poseStack.translate(0f, companion.perchFloorLift(i), 0f);
        }
        ClientCompanions.perchSubmits++;
        companion.submitPerched(i, poseStack, collector, lightCoords, partialTick, yaw);
        poseStack.popPose();
    }
}
