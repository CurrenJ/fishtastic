package net.neoforged.neoforge.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PORT-ONLY compile-time stub of NeoForge's annotation of the same name (A6.1,
 * docs/backport-pass2/track-a-1.21.1.md). See {@link GameTestHolder} for why the stubs exist, why
 * they are compile-only, and why they must never reach a runtime classpath.
 *
 * <p>NeoForge defaults this to <strong>true</strong> when it is absent, which prefixes the class's
 * simple name onto the raw template and turns {@code fishtastic:empty} into an id that is not even
 * valid. The shared harness sets it false.
 *
 * <p>Keep this in step with NeoForge's definition.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface PrefixGameTestTemplate {
    /**
     * Whether to prefix the game test template with the containing class' simple name.
     */
    boolean value() default true;
}
