package grill24.fishtastic;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import grill24.fishtastic.component.BundleContents;
import grill24.fishtastic.component.ComponentKey;
import grill24.fishtastic.component.FishtasticItemPatch;
import grill24.fishtastic.component.HeadProfile;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The single point through which the mod reads and writes per-stack item data — both our own
 * {@link FishtasticDataComponents} and vanilla item data gameplay depends on.
 * <p>
 * Backport seam S1 (docs/backport-plan.md §8): on MC 1.20.1 item stacks carry NBT, not data
 * components, and only this class gets an NBT implementation here (docs/backport-pass2/track-b-1.20.1.md
 * B2.1). Call sites keep the same shape on every version — they pass the
 * {@code FishtasticDataComponents} field and never name a component type or
 * {@code ItemStack#get/set/has/remove} themselves, so this port swaps the key type
 * ({@link ComponentKey} instead of {@code Holder<DataComponentType<T>>}) behind those fields
 * rather than touching every caller.
 */
public final class FishtasticItemData {
    private FishtasticItemData() {}

    /**
     * 1.20.1 has no vanilla bundle-contents component (added 1.21) — stored under our own key,
     * same shape as any other {@link ComponentKey} (B2.1's mapping table).
     */
    static final ComponentKey<BundleContents> BUNDLE_CONTENTS =
            ComponentKey.of("bundle_contents", BundleContents.CODEC);

    // ── Fishtastic components ─────────────────────────────────────────────

    @Nullable
    public static <T> T get(ItemStack stack, ComponentKey<T> component) {
        return component.get(stack);
    }

    public static <T> T getOrDefault(ItemStack stack, ComponentKey<T> component, T fallback) {
        return component.getOrDefault(stack, fallback);
    }

    public static boolean has(ItemStack stack, ComponentKey<?> component) {
        return component.has(stack);
    }

    /** Sets the component and returns its previous value, like {@code ItemStack#set}. */
    @Nullable
    public static <T> T set(ItemStack stack, ComponentKey<T> component, @Nullable T value) {
        return component.set(stack, value);
    }

    public static void remove(ItemStack stack, ComponentKey<?> component) {
        component.remove(stack);
    }

    /** The registry id of a Fishtastic component — the id data-driven conditions refer to it by. */
    public static ResourceLocation id(ComponentKey<?> component) {
        return component.id;
    }

    // ── Any component, by registry id (data-driven item-effect conditions) ──

    /** Whether the stack carries the component registered under {@code id}; false for an unknown id. */
    public static boolean hasById(ItemStack stack, ResourceLocation id) {
        ComponentKey<?> key = ComponentKey.byId(id);
        return key != null && key.has(stack);
    }

    /**
     * The value of the component registered under {@code id}, encoded to JSON through its codec,
     * or null if the id is unknown, the stack lacks the component, or encoding fails.
     */
    @Nullable
    public static JsonElement encodeById(ItemStack stack, ResourceLocation id) {
        ComponentKey<?> key = ComponentKey.byId(id);
        if (key == null) return null;
        return encode(stack, key);
    }

    @Nullable
    private static <T> JsonElement encode(ItemStack stack, ComponentKey<T> key) {
        T value = key.get(stack);
        if (value == null) return null;
        try {
            return key.codec.encodeStart(JsonOps.INSTANCE, value).result().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Component patches (quest / shop reward definitions) ──────────────

    public static void applyPatch(ItemStack stack, FishtasticItemPatch patch) {
        patch.applyTo(stack);
    }

    /**
     * The value a patch <em>explicitly</em> sets for {@code component}, or null if the patch doesn't
     * mention it — deliberately not the item's default.
     */
    @Nullable
    public static <T> T patchValue(FishtasticItemPatch patch, ComponentKey<T> component) {
        return patch.get(component);
    }

    // ── Stack identity ────────────────────────────────────────────────────

    /** Same item and same item data — the stacking/merging test. */
    public static boolean isSameItemSameData(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameTags(a, b);
    }

    // ── Vanilla item data ─────────────────────────────────────────────────

    /** A pile's (bundle-style) contents, or null if the stack has none. */
    @Nullable
    public static BundleContents bundleContents(ItemStack stack) {
        return BUNDLE_CONTENTS.get(stack);
    }

    /** A pile's (bundle-style) contents, or {@link BundleContents#EMPTY} if the stack has none. */
    public static BundleContents bundleContentsOrEmpty(ItemStack stack) {
        return BUNDLE_CONTENTS.getOrDefault(stack, BundleContents.EMPTY);
    }

    public static void setBundleContents(ItemStack stack, BundleContents contents) {
        BUNDLE_CONTENTS.set(stack, contents);
    }

    /** Sets the profile a player-head stack renders the skin of. */
    public static void setHeadProfile(ItemStack stack, HeadProfile profile) {
        if (profile.gameProfile() != null) {
            CompoundTag ownerTag = new CompoundTag();
            net.minecraft.nbt.NbtUtils.writeGameProfile(ownerTag, profile.gameProfile());
            stack.getOrCreateTag().put("SkullOwner", ownerTag);
        } else if (profile.name() != null) {
            stack.getOrCreateTag().putString("SkullOwner", profile.name());
        }
    }

    /** Whether the stack's tooltip is hidden entirely (vanilla's hide-tooltip flag). */
    public static boolean isTooltipHidden(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && (tag.getInt("HideFlags") & 0x7F) == 0x7F;
    }

    /**
     * The sound the item makes when it breaks, or null if it's silent. There's no per-stack break
     * sound before 1.21.5, so every item uses vanilla's item-break sound.
     */
    @Nullable
    public static Holder<SoundEvent> breakSound(ItemStack stack) {
        return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.ITEM_BREAK);
    }
}
