package net.neoforged.neoforge.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PORT-ONLY compile-time stub of NeoForge's annotation of the same name (A6.1,
 * docs/backport-pass2/track-a-1.21.1.md).
 *
 * <p>NeoForge 21.1 requires this on the class that <em>declares</em> the game tests, and the one
 * shared harness in {@code common/src/testmod} is compiled for both loaders, so the annotation has
 * to be resolvable at compile time on the Fabric side too. This stub supplies it there.
 *
 * <p>It is <strong>compile-only and never shipped</strong>: at runtime the annotation must be
 * NeoForge's own class, because {@code GameTestHooks.getTemplateNamespace} looks the annotation up
 * by {@code Class} - a second copy of the class on the classpath would shadow NeoForge's and make
 * its own lookup miss, quietly disabling the holder.
 *
 * <p>Keep this in step with NeoForge's definition (retention, targets, element name and default):
 * only the shape matters to compilation, but a drift in retention would silently drop the
 * annotation from the class file. Delete it when the port stops needing it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GameTestHolder {
    /**
     * Used as the default template namespace for any game tests in the class that do not specify
     * one.
     */
    String value() default "minecraft";
}
