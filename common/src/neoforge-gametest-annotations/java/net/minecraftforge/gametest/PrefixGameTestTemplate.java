package net.minecraftforge.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PORT-ONLY compile-time stub of Forge's real annotation of the same name (B6.1). See
 * {@link GameTestHolder} for why the stub exists and why it is compile-only, Fabric-only.
 *
 * <p>Forge defaults this to <strong>true</strong> when absent, which prefixes the class's simple
 * name onto the raw template and turns {@code fishtastic:empty} into an id that is not even
 * valid. The shared harness sets it false.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface PrefixGameTestTemplate {
    /**
     * Whether to prefix the game test template with the containing class' simple name.
     */
    boolean value() default true;
}
