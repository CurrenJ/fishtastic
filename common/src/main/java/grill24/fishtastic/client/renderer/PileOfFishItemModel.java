package grill24.fishtastic.client.renderer;

import com.mojang.serialization.MapCodec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;


import java.util.List;

@Environment(EnvType.CLIENT)
public class PileOfFishItemModel implements ItemModel {
    private static final int MAX_LAYERS = 3;

    // Each item is shrunk to this fraction of its normal size
    private static final float SCALE = 0.7f;

    // Per-layer XY offset so back items peek out from behind front ones.
    // Layer 0 (front / newest) sits at the bottom-left; successive layers
    // step toward the top-right.
    private static final float STEP_X = 0.12f;
    private static final float STEP_Y = 0.10f;

    // Small Z nudge per layer to keep depth ordering correct
    private static final float STEP_Z = 0.02f;

    // Offset to re-center the item after scaling around the ItemTransform's
    // fixed (-0.5, -0.5, -0.5) pivot.
    private static final float RE_CENTER = 0.5f * (1f - SCALE) - 0.2f;

    // Scale via ItemTransform so it flows through Pose.scale(), which correctly
    // leaves the normal matrix untouched for uniform scaling. Using
    // setLocalTransform with a scale matrix would corrupt normals and darken items.
    private static final ItemTransform PILE_SCALE_TRANSFORM = new ItemTransform(
            new Vector3f(0, 0, 0),      // rotation
            new Vector3f(0, 0, 0),      // translation
            new Vector3f(SCALE, SCALE, 1f)  // scale
    );

    @Override
    public void update(ItemStackRenderState output, ItemStack item,
                       ItemModelResolver resolver, ItemDisplayContext displayContext,
                       @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        output.appendModelIdentityElement(this);
        BundleContents contents = item.getOrDefault(
                DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        List<ItemStackTemplate> templates = contents.items();
        int limit = Math.min(templates.size(), MAX_LAYERS);

        FishtasticItemStackRenderState access = (FishtasticItemStackRenderState) output;

        // Render from back (oldest = index limit-1) to front (newest = index 0)
        for (int i = limit - 1; i >= 0; i--) {
            ItemStack layerStack = templates.get(i).create();
            int before = access.fishtastic$getActiveLayerCount();
            resolver.appendItemLayers(output, layerStack, displayContext, level, owner, seed + i);
            int after = access.fishtastic$getActiveLayerCount();

            // Apply scale via ItemTransform (uses Pose.scale → normals stay correct)
            // and a pure-translation localTransform for the per-layer offset + re-center.
            float d = (float) i;
            Matrix4f offset = new Matrix4f()
                    .translate(RE_CENTER + d * STEP_X,
                               RE_CENTER + d * STEP_Y,
                               -d * STEP_Z);

            for (int j = before; j < after; j++) {
                ItemStackRenderState.LayerRenderState layer = access.fishtastic$getLayer(j);
                layer.setItemTransform(PILE_SCALE_TRANSFORM);
                layer.setLocalTransform(offset);
            }
        }
    }

    @Environment(EnvType.CLIENT)
    public record Unbaked() implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
            return new PileOfFishItemModel();
        }
    }
}

