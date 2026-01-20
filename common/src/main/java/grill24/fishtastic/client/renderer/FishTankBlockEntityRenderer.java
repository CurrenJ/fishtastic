package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.util.ClientTickHandler;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.Random;

public class FishTankBlockEntityRenderer implements BlockEntityRenderer<FishTankBlockEntity> {

    public FishTankBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        // Context can be used to access various rendering resources if needed
    }

    private static final Vector3f SAND_BASE_Y_OFFSET = new Vector3f(0f, 1f / 32f, 0f);
    private static final Vector3f ITEM_POSITION_OFFSET = new Vector3f(0.5f,  8f / 16f, 0.5f);
    @Override
    public void render(FishTankBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                      MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // Get the first item to render
        ItemStack itemToRender = blockEntity.getFirstItem();

        if (itemToRender.isEmpty()) {
            return; // Nothing to render
        }

        Level level = blockEntity.getLevel();
        if (level == null) {
            return; // Level is required for animation timing
        }

        BlockPos blockPos = blockEntity.getBlockPos();

        poseStack.pushPose();

        // -- TRANSFORMATIONS --
        // Fish tank sand = 1/16 blocks height tall
        // So center of tank vertically is at 8.5/16 blocks height
        // Start by translating to the center of the tank
        float yOffset = ITEM_POSITION_OFFSET.y();
        if (!blockEntity.getOpenFaces().contains(Direction.DOWN)) {
            yOffset += SAND_BASE_Y_OFFSET.y();
        }
        poseStack.translate(ITEM_POSITION_OFFSET.x(), yOffset, ITEM_POSITION_OFFSET.z());

        float t = ClientTickHandler.total() + partialTick;

        // Apply bobbing animation
        Random random = new Random(blockPos.hashCode());
        float amplitude = 0.125f;
        float hertz = 0.08f + (random.nextFloat() * 0.04f); // Randomize hertz slightly per item
        float yBobHeight = getBobbingHeight(random, t, amplitude, hertz);
        poseStack.translate(0f, yBobHeight, 0f);

        // Apply the stored rotation to face the direction the player was when they placed it
        float storedRotation = blockEntity.getFirstItemRotation();
        poseStack.mulPose(Axis.YP.rotationDegrees(storedRotation));

        // Calculate the tangent angle to make the item "surf" the sine wave
        float surfFactor = 0.12f; // Reduce the surfing angle effect
        float surfAngle = getSurfingAngle(random, t, amplitude, hertz) * surfFactor;


        // Add organic y-axis wiggle using layered waveforms
        float wiggleFactor = 0.5f; // Full effect for wiggle
        float yWiggle = getOrganicWiggle(random, t) * wiggleFactor;
        poseStack.mulPose(Axis.YP.rotationDegrees(yWiggle));

        poseStack.mulPose(Axis.ZP.rotationDegrees(surfAngle + 45f));


        // Scale the item slightly smaller
        float scale = 1f;
        if(ItemSizeHelper.hasSize(itemToRender))
        {
            float size = ItemSizeHelper.getSize(itemToRender);
            // Assume size is cm, where 100 cm = 1 block
            scale = 0.01f + (size / 100f) * 0.8f; // Clamp scale between 0.01 and 0.6
        } else {
            scale *= 0.5f; // Default scale for items without size data
        }
        poseStack.scale(scale, scale, scale);

        // -- RENDER ITEM --
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        itemRenderer.renderStatic(
            itemToRender,
            ItemDisplayContext.FIXED,
            packedLight,
            OverlayTexture.NO_OVERLAY,
            poseStack,
            bufferSource,
            blockEntity.getLevel(),
            0
        );

        poseStack.popPose();
    }

    private static float getBobbingHeight(Random random, float gameTimeTicks, float amplitude, float hertz) {
        // 20 ticks = 1 second
        // 1 hz = 1 cycle per second = 20 ticks per cycle
        float time = gameTimeTicks / (20f / hertz);
        time += (float) (random.nextFloat() * 2 * Math.PI);
        return (float) (Math.sin(time * 2 * Math.PI) * amplitude);
    }

    private static float getSurfingAngle(Random random, float gameTimeTicks, float amplitude, float hertz) {
        // Calculate the derivative of the sine wave to get the tangent angle
        // y = sin(t) * amplitude
        // dy/dt = cos(t) * amplitude * (2π)
        float time = gameTimeTicks / (20f / hertz);
        time += (float) (random.nextFloat() * 2 * Math.PI);

        // The derivative gives us the slope
        float derivative = (float) (Math.cos(time * 2 * Math.PI) * amplitude * 2 * Math.PI);

        // Convert slope to angle in degrees
        // atan gives us the angle of the tangent line
        return (float) Math.toDegrees(Math.atan(derivative));
    }

    private static float getOrganicWiggle(Random random, float gameTimeTicks) {
        // Layer multiple sine waves with different frequencies and amplitudes
        // to create an organic, natural-feeling wiggle motion
        float randomOffset = (float) (random.nextFloat() * 2 * Math.PI);

        // Base slow wave - creates the primary ebb and flow
        float slowWave = (float) Math.sin((gameTimeTicks / 60f + randomOffset) * 2 * Math.PI) * 15f;

        // Medium frequency wave - adds complexity
        float mediumWave = (float) Math.sin((gameTimeTicks / 35f + randomOffset * 1.3f) * 2 * Math.PI) * 8f;

        // Fast subtle wave - adds liveliness
        float fastWave = (float) Math.sin((gameTimeTicks / 18f + randomOffset * 0.7f) * 2 * Math.PI) * 4f;

        // Very slow drift - creates a long period variation
        float drift = (float) Math.sin((gameTimeTicks / 120f + randomOffset * 0.5f) * 2 * Math.PI) * 10f;

        // Combine all waves for organic motion
        return slowWave + mediumWave + fastWave + drift;
    }


}
