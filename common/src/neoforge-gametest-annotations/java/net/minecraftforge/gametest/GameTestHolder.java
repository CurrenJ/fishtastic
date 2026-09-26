package net.minecraftforge.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PORT-ONLY compile-time stub of Forge's real annotation of the same name (B6.1,
 * docs/backport-pass2/track-b-1.20.1.md), needed only on Fabric's testmod compile classpath.
 *
 * <p>Forge 47 ships this annotation for real (unlike NeoForge 21.1, which needed a stub on
 * <em>both</em> loaders at A6.1 on port/1.21.1 — see that annotation's own PORT-ONLY stub for the
 * history), so the Forge module here compiles {@code FishtasticGameTests} straight against
 * Forge's own {@code net.minecraftforge.gametest.GameTestHolder}. Fabric has no Forge dependency
 * at all, but the one shared harness class in {@code common/src/testmod} is compiled once per
 * platform, so Fabric's compile still needs *something* resolvable at this exact fully-qualified
 * name. This stub supplies it there, compile-only, never shipped.
 *
 * <p>Keep this in step with Forge's real definition (retention, targets, element name and
 * default) — only the shape matters to compilation, since Fabric ignores the annotation outright
 * at runtime (it never loads Forge's real class, so a name collision cannot occur there the way
 * it could on Forge itself).
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
