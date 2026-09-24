package grill24.fishtastic;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

/**
 * The single point through which the mod reads and writes per-stack item data — both our own
 * {@link FishtasticDataComponents} and the vanilla {@link DataComponents} gameplay depends on.
 * <p>
 * Backport seam S1 (docs/backport-plan.md §8): on MC 1.20.1 item stacks carry NBT, not data
 * components, and only this class gets an NBT implementation there. Call sites keep the same
 * shape on every version — they pass the {@code FishtasticDataComponents} field and never name
 * {@code DataComponentType} or {@code ItemStack#get/set/has/remove} themselves, so the 1.20.1 port
 * swaps the key type behind those fields rather than touching every caller.
 * <p>
 * Every method here is a straight delegation to the vanilla component API; semantics (null on
 * absence, empty-stack behaviour, the return of {@code set}) match {@code ItemStack} exactly.
 * Semantic helpers such as {@link grill24.fishtastic.util.FishQualityHelper} and
 * {@link grill24.fishtastic.util.ItemSizeHelper} sit on top of this class.
 */
public final class FishtasticItemData {
    private FishtasticItemData() {}

    // ── Fishtastic components ─────────────────────────────────────────────

    @Nullable
    public static <T> T get(ItemStack stack, Holder<DataComponentType<T>> component) {
        return stack.get(component.value());
    }

    public static <T> T getOrDefault(ItemStack stack, Holder<DataComponentType<T>> component, T fallback) {
        return stack.getOrDefault(component.value(), fallback);
    }

    public static boolean has(ItemStack stack, Holder<? extends DataComponentType<?>> component) {
        return stack.has(component.value());
    }

    /** Sets the component and returns its previous value, like {@link ItemStack#set}. */
    @Nullable
    public static <T> T set(ItemStack stack, Holder<DataComponentType<T>> component, @Nullable T value) {
        return stack.set(component.value(), value);
    }

    public static void remove(ItemStack stack, Holder<? extends DataComponentType<?>> component) {
        stack.remove(component.value());
    }

    /** The registry id of a Fishtastic component — the id data-driven conditions refer to it by. */
    public static Identifier id(Holder<? extends DataComponentType<?>> component) {
        return BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.value());
    }

    // ── Any component, by registry id (data-driven item-effect conditions) ──

    /** Whether the stack carries the component registered under {@code id}; false for an unknown id. */
    public static boolean hasById(ItemStack stack, Identifier id) {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
        return type != null && stack.has(type);
    }

    /**
     * The value of the component registered under {@code id}, encoded to JSON through its codec,
     * or null if the id is unknown, the stack lacks the component, the component isn't
     * persistent, or encoding fails.
     */
    @Nullable
    public static JsonElement encodeById(ItemStack stack, Identifier id) {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
        if (type == null) return null;
        return encode(stack, type);
    }

    @Nullable
    private static <T> JsonElement encode(ItemStack stack, DataComponentType<T> type) {
        T value = stack.get(type);
        if (value == null) return null;

        Codec<T> codec = type.codec();
        if (codec == null) return null;

        try {
            return codec.encodeStart(JsonOps.INSTANCE, value).result().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Component patches (quest / shop reward definitions) ──────────────

    public static void applyPatch(ItemStack stack, DataComponentPatch patch) {
        stack.applyComponents(patch);
    }

    /**
     * The value a patch <em>explicitly</em> sets for {@code component}, or null if the patch doesn't
     * mention it (or only removes it) — deliberately not the item's default.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> T patchValue(DataComponentPatch patch, Holder<DataComponentType<T>> component) {
        DataComponentType<T> type = component.value();
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            if (entry.getKey() == type && entry.getValue().isPresent()) {
                return (T) entry.getValue().get();
            }
        }
        return null;
    }

    // ── Stack identity ────────────────────────────────────────────────────

    /** Same item and same item data — the stacking/merging test ({@link ItemStack#isSameItemSameComponents}). */
    public static boolean isSameItemSameData(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameComponents(a, b);
    }

    // ── Vanilla item data ─────────────────────────────────────────────────

    /** A pile's (bundle-style) contents, or null if the stack has none. */
    @Nullable
    public static BundleContents bundleContents(ItemStack stack) {
        return stack.get(DataComponents.BUNDLE_CONTENTS);
    }

    /** A pile's (bundle-style) contents, or {@link BundleContents#EMPTY} if the stack has none. */
    public static BundleContents bundleContentsOrEmpty(ItemStack stack) {
        return stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
    }

    public static void setBundleContents(ItemStack stack, BundleContents contents) {
        stack.set(DataComponents.BUNDLE_CONTENTS, contents);
    }

    /** Sets the profile a player-head stack renders the skin of. */
    public static void setHeadProfile(ItemStack stack, ResolvableProfile profile) {
        stack.set(DataComponents.PROFILE, profile);
    }

    /** Whether the stack's tooltip is hidden entirely (vanilla's hide-tooltip flag). */
    public static boolean isTooltipHidden(ItemStack stack) {
        TooltipDisplay tooltipDisplay = stack.get(DataComponents.TOOLTIP_DISPLAY);
        return tooltipDisplay != null && tooltipDisplay.hideTooltip();
    }

    /** The sound the item makes when it breaks, or null if it's silent. */
    @Nullable
    public static Holder<SoundEvent> breakSound(ItemStack stack) {
        return stack.get(DataComponents.BREAK_SOUND);
    }
}
