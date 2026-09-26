package grill24.fishtastic.mixin;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Manual MixinExtras bootstrap for fishtastic.mixins.json (used by 4 mixins). Fabric Loader
 * bundles MixinExtras and self-initializes it; Forge 47 (unlike NeoForge) ships neither, so
 * without this hook the mixin transformer deadlocks the first time it tries to apply a
 * MixinExtras-based injector (see track-b-1.20.1.md "B6.1 as built"). Calling init() more than
 * once (e.g. Fabric calling its own bootstrap first) is safe — MixinExtrasBootstrap guards it
 * with an internal flag.
 */
public class FishtasticMixinExtrasPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
        MixinExtrasBootstrap.init();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
