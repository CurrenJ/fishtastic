package grill24.fishtastic.loot;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;

import java.util.Set;

/**
 * B2.6 (1.20.1 only): copies a broken fish tank's materials/shape from its block entity onto the
 * dropped stack. Replaces 1.21.1's vanilla {@code minecraft:copy_components} loot function, which
 * has no 1.20.1 equivalent (no implicit-component mechanism exists pre-1.20.5). Rejected: vanilla
 * {@code copy_nbt} — it would tie the loot JSON to the BE's raw NBT key names and the
 * {@code ComponentKey} NBT layout instead of going through {@link FishTankBlockEntity#writeToItem}.
 */
public class CopyTankDataFunction extends LootItemConditionalFunction {
    public static final LootItemFunctionType TYPE = register();

    protected CopyTankDataFunction(LootItemCondition[] predicates) {
        super(predicates);
    }

    private static LootItemFunctionType register() {
        return Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE, Fishtastic.id("copy_tank_data"), new LootItemFunctionType(new Serializer()));
    }

    @Override
    public LootItemFunctionType getType() {
        return TYPE;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.BLOCK_ENTITY);
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext lootContext) {
        BlockEntity blockEntity = lootContext.getParamOrNull(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof FishTankBlockEntity fishTank) {
            fishTank.writeToItem(stack);
        }
        return stack;
    }

    public static class Serializer extends LootItemConditionalFunction.Serializer<CopyTankDataFunction> {
        @Override
        public void serialize(JsonObject json, CopyTankDataFunction function, JsonSerializationContext context) {
            super.serialize(json, function, context);
        }

        @Override
        public CopyTankDataFunction deserialize(JsonObject json, JsonDeserializationContext context, LootItemCondition[] predicates) {
            return new CopyTankDataFunction(predicates);
        }
    }
}
