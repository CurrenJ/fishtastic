package grill24.fishtastic.fabric.datagen;

import com.google.gson.JsonObject;
import grill24.fishtastic.fishtank.FishTankShape;
import grill24.fishtastic.shapegen.TankShapeGeometryStrategies;

import java.util.function.IntFunction;

/**
 * Maps each {@link FishTankShape} to the {@code tools/tank-shape-gen} calls that produce its
 * 64-permutation frame/sand/glass models — the single place a new shape's datagen wiring gets
 * added, so {@code FishTankFrameModelProvider}/{@code GlassModelProvider}/{@code SandModelProvider}
 * don't each need their own copy of this mapping.
 *
 * <p>Just a thin adapter over {@link TankShapeGeometryStrategies}, the shared (Minecraft-free) list
 * in {@code tools/tank-shape-gen} keyed by serialized name instead of the enum — that's the actual
 * single source of truth, shared with the geometry-safety test so a new shape can't be wired up
 * here without also picking up its safety coverage. Adding a shape still only touches one switch:
 * the entry in {@code TankShapeGeometryStrategies.ALL}.
 */
final class FishTankShapeGeometryStrategies {

    record Strategy(IntFunction<JsonObject> frame, IntFunction<JsonObject> glass, IntFunction<JsonObject> sand) {}

    private FishTankShapeGeometryStrategies() {}

    static Strategy forShape(FishTankShape shape) {
        TankShapeGeometryStrategies.Strategy strategy = TankShapeGeometryStrategies.byName(shape.getSerializedName());
        return new Strategy(strategy.frame(), strategy.glass(), strategy.sand());
    }
}
