package grill24.fishtastic.fabric.datagen;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.CosmeticTransforms;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.data.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Data generator for {@link CosmeticStructure} datapack entries (multi-block fish tank cosmetics).
 */
public class CosmeticStructureProvider implements DataProvider {
    private final PackOutput.PathProvider pathProvider;

    public CosmeticStructureProvider(FabricPackOutput output) {
        this.pathProvider = output.createPathProvider(PackOutput.Target.DATA_PACK, "cosmetic_structure");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (String wood : FishtasticItems.FENCE_ARCH_WOOD_TYPES) {
            futures.add(generate(cache, "cosmetic_fence_arch_" + wood, fenceArch(woodFamily(wood))));
        }
        for (String variant : FishtasticItems.LAMP_VARIANTS) {
            futures.add(generate(cache, "cosmetic_lamp_" + variant, simpleLamp(lampBase(variant))));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    /** Maps a {@link FishtasticItems#FENCE_ARCH_WOOD_TYPES} entry to its vanilla plank {@link BlockFamily}. */
    private static BlockFamily woodFamily(String wood) {
        return switch (wood) {
            case "oak" -> BlockFamilies.OAK_PLANKS;
            case "spruce" -> BlockFamilies.SPRUCE_PLANKS;
            case "birch" -> BlockFamilies.BIRCH_PLANKS;
            case "jungle" -> BlockFamilies.JUNGLE_PLANKS;
            case "acacia" -> BlockFamilies.ACACIA_PLANKS;
            case "dark_oak" -> BlockFamilies.DARK_OAK_PLANKS;
            case "pale_oak" -> BlockFamilies.PALE_OAK_PLANKS;
            case "mangrove" -> BlockFamilies.MANGROVE_PLANKS;
            case "cherry" -> BlockFamilies.CHERRY_PLANKS;
            case "crimson" -> BlockFamilies.CRIMSON_PLANKS;
            case "warped" -> BlockFamilies.WARPED_PLANKS;
            default -> throw new IllegalArgumentException("No BlockFamily mapping for wood type: " + wood);
        };
    }

    /** A tiny fence arch, built from the given wood family's fence + an exposed copper lantern, spanning two grid cells. */
    private static CosmeticStructure fenceArch(BlockFamily blockFamily) {
        Block fence = blockFamily.get(BlockFamily.Variant.FENCE);
        return new CosmeticStructure(
                List.of(
                        new CosmeticStructure.GridOffset(0, 0),
                        new CosmeticStructure.GridOffset(1, 0),
                        new CosmeticStructure.GridOffset(-1, 0)
                ),
                List.of(
                        new CosmeticStructure.StructurePart(
                                Blocks.COPPER_LANTERN.exposed().defaultBlockState().setValue(LanternBlock.HANGING, true),
                                0f, 1f, 0f),
                        new CosmeticStructure.StructurePart(fence.defaultBlockState(), 1f, 0f, 0f),
                        new CosmeticStructure.StructurePart(fence.defaultBlockState(), 1f, 1f, 0f),
                        new CosmeticStructure.StructurePart(fence.defaultBlockState().setValue(CrossCollisionBlock.WEST, true), 1f, 2f, 0f),
                        new CosmeticStructure.StructurePart(fence.defaultBlockState(), -1f, 0f, 0f),
                        new CosmeticStructure.StructurePart(fence.defaultBlockState(), -1f, 1f, 0f),
                        new CosmeticStructure.StructurePart(fence.defaultBlockState().setValue(CrossCollisionBlock.EAST, true), -1f, 2f, 0f),
                        new CosmeticStructure.StructurePart(fence.defaultBlockState().setValue(CrossCollisionBlock.EAST, true).setValue(CrossCollisionBlock.WEST, true), 0f, 2f, 0f)
                ),
                (float) CosmeticGridCell.CELL_WIDTH,
                new CosmeticTransforms.Transform(0.15f, 0.2f, 0f, 0f, -25f, 0f, 1.25f)
        );
    }

    /** Maps a {@link FishtasticItems#LAMP_VARIANTS} entry to its base block's default state. */
    private static BlockState lampBase(String variant) {
        return switch (variant) {
            case "chiseled_quartz" -> Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
            case "glazed_terracotta_light_blue" -> Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA.defaultBlockState();
            case "glazed_terracotta_cyan" -> Blocks.CYAN_GLAZED_TERRACOTTA.defaultBlockState();
            case "glazed_terracotta_magenta" -> Blocks.MAGENTA_GLAZED_TERRACOTTA.defaultBlockState();
            case "amethyst" -> Blocks.AMETHYST_BLOCK.defaultBlockState();
            case "prismarine_bricks" -> Blocks.PRISMARINE_BRICKS.defaultBlockState();
            case "crying_obsidian" -> Blocks.CRYING_OBSIDIAN.defaultBlockState();
            default -> throw new IllegalArgumentException("No block mapping for lamp variant: " + variant);
        };
    }

    private static CosmeticStructure simpleLamp(BlockState base) {
        return new CosmeticStructure(
               List.of(new CosmeticStructure.GridOffset(0, 0)),
                List.of(
                        new CosmeticStructure.StructurePart(base, 0f, 0f, 0f),
                        new CosmeticStructure.StructurePart(Blocks.SHROOMLIGHT.defaultBlockState(), 0f, 1f, 0f)
                ),
                (float) CosmeticGridCell.CELL_WIDTH,
                new CosmeticTransforms.Transform(0, 0, 0, 0, 0, 0, 1f)
        );
    }

    private CompletableFuture<?> generate(CachedOutput cache, String name, CosmeticStructure structure) {
        JsonElement json = CosmeticStructure.CODEC.encodeStart(JsonOps.INSTANCE, structure)
                .result()
                .orElseThrow(() -> new IllegalStateException("Failed to encode cosmetic structure '" + name + "'"));
        return DataProvider.saveStable(cache, json, pathProvider.json(Fishtastic.id(name)));
    }

    @Override
    public String getName() {
        return "Fishtastic Cosmetic Structures";
    }
}
