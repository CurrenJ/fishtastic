package grill24.fishtastic.architectury.forge;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.tags.ITag;

import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Adapts a Forge {@link RegistryObject} (returned by {@code DeferredRegister.register}) to the
 * {@link Holder} interface the common code's {@code IRegistrationApi} expects (B5.3: NeoForge's
 * {@code DeferredRegister.register} returns a {@code DeferredHolder}, which already is a
 * {@code Holder}; Forge 47's {@code RegistryObject} is not). Every method delegates lazily to
 * {@code ro.getHolder().orElseThrow()} — the same object every call resolves to once the registry
 * event fires, so call sites never need to change.
 */
public final class RegistryObjectHolder<T> implements Holder<T> {
    private final RegistryObject<T> ro;

    public RegistryObjectHolder(RegistryObject<T> ro) {
        this.ro = ro;
    }

    private Holder<T> resolved() {
        return ro.getHolder().orElseThrow(() ->
                new IllegalStateException("Not registered yet: " + ro.getId()));
    }

    @Override
    public T value() {
        return resolved().value();
    }

    @Override
    public boolean isBound() {
        return ro.getHolder().map(Holder::isBound).orElse(false);
    }

    @Override
    public boolean is(ResourceLocation location) {
        return resolved().is(location);
    }

    @Override
    public boolean is(ResourceKey<T> resourceKey) {
        return resolved().is(resourceKey);
    }

    @Override
    public boolean is(Predicate<ResourceKey<T>> predicate) {
        return resolved().is(predicate);
    }

    @Override
    public boolean is(net.minecraft.tags.TagKey<T> tagKey) {
        return resolved().is(tagKey);
    }

    @Override
    public Stream<net.minecraft.tags.TagKey<T>> tags() {
        return resolved().tags();
    }

    @Override
    public com.mojang.datafixers.util.Either<ResourceKey<T>, T> unwrap() {
        return resolved().unwrap();
    }

    @Override
    public java.util.Optional<ResourceKey<T>> unwrapKey() {
        return resolved().unwrapKey();
    }

    @Override
    public Kind kind() {
        return resolved().kind();
    }

    @Override
    public boolean canSerializeIn(HolderOwner<T> owner) {
        return resolved().canSerializeIn(owner);
    }

    @Override
    public Stream<net.minecraft.tags.TagKey<T>> getTagKeys() {
        return resolved().getTagKeys();
    }

    @Override
    public boolean containsTag(net.minecraft.tags.TagKey<T> tagKey) {
        return resolved().containsTag(tagKey);
    }

    @Override
    public boolean containsTag(ITag<T> tag) {
        return resolved().containsTag(tag);
    }
}
