package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import grill24.fishtastic.Fishtastic;

import java.lang.reflect.Method;

/**
 * Registers Fishtastic's custom {@link RenderPipeline}s with Iris so they survive shaderpack rendering.
 *
 * <p><b>Why this is needed.</b>  When a shaderpack is loaded, Iris mixes into
 * {@code GlDevice.getOrCompilePipeline} and swaps every pipeline for the pack's equivalent gbuffers
 * program.  Pipelines it doesn't recognise are left alone — Iris logs
 * {@code "Missing program <id> in override list"} and lets ours run unchanged.  That sounds harmless
 * but isn't: our shaders write a single {@code out vec4 fragColor} at location 0, while Iris has
 * rebound world rendering to its own multi-attachment gbuffer using the pack's DRAWBUFFERS layout.
 * The fragments land in a buffer the pack's deferred/composite passes never resolve as scene colour,
 * so the draw is silently discarded and the geometry is <em>completely invisible</em> under every
 * shaderpack.  This is what hid the fish tank water fill and the in-world quality outlines.
 *
 * <p>GUI pipelines are deliberately not registered here: GUI rendering happens after Iris releases
 * the framebuffer, so those draws are unaffected and must keep their own fragment shaders.
 *
 * <p><b>Why reflection.</b>  Iris is an optional, client-only, loader-specific dependency.  Linking
 * {@code net.irisshaders.iris.api.v0} at compile time would require per-platform Gradle wiring and
 * would hard-fail the NeoForge build and any shader-less install.  The Iris API is a stable versioned
 * surface ({@code api/v0}), so reflecting against it is cheap and safe.  Every method here is a no-op
 * when Iris is absent.
 */
public final class IrisCompat {

    private static final String API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";
    private static final String PROGRAM_CLASS = "net.irisshaders.iris.api.v0.IrisProgram";
    private static final String SHADOW_PROGRAM_CLASS = "net.irisshaders.iris.api.v0.IrisShadowProgram";

    /** Resolved lazily on first use; {@code null} once {@link #resolved} is set means "Iris absent". */
    private static Object apiInstance;
    private static Method assignPipeline;
    private static Method assignPipelineShadow;
    private static Class<?> programClass;
    private static Class<?> shadowProgramClass;
    private static boolean resolved;

    /**
     * Binds {@code pipeline} to the shaderpack program named by {@code program} (a constant of
     * Iris's {@code IrisProgram} enum, e.g. {@code "ENTITIES_TRANSLUCENT"}) for the main pass, and
     * to {@code shadowProgram} ({@code IrisShadowProgram}, e.g. {@code "SHADOW_ENTITIES"}) for the
     * shadow pass.
     *
     * <p>Registering the shadow pass matters even though these are cosmetic overlays: an unassigned
     * pipeline drawn during shadow rendering hits the same missing-override path and would scribble
     * into the shadow map with our own shader.  Pass {@code null} for {@code shadowProgram} only if
     * the pipeline can never be reached during shadow rendering.
     *
     * <p>Safe to call repeatedly (Iris stores assignments in a map) and safe to call when Iris is
     * not installed.  Must be called before the pipeline's first draw — callers register at
     * pipeline-creation time, which guarantees that.
     */
    public static void assignPipeline(RenderPipeline pipeline, String program, String shadowProgram) {
        if (!resolve()) {
            return;
        }
        try {
            assignPipeline.invoke(apiInstance, pipeline, enumValue(programClass, program));
            if (shadowProgram != null) {
                assignPipelineShadow.invoke(apiInstance, pipeline, enumValue(shadowProgramClass, shadowProgram));
            }
        } catch (ReflectiveOperationException e) {
            // Non-fatal: without the assignment the pipeline is invisible under shaders, which is
            // exactly the pre-existing behaviour. Never let this break rendering outright.
            Fishtastic.LOGGER.warn("Failed to assign Iris program for pipeline {}", pipeline.getLocation(), e);
        }
    }

    private static Object enumValue(Class<?> enumClass, String name) throws ReflectiveOperationException {
        return enumClass.getField(name).get(null);
    }

    /** @return true if the Iris API is present and its methods were resolved. */
    private static synchronized boolean resolve() {
        if (resolved) {
            return apiInstance != null;
        }
        resolved = true;
        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            programClass = Class.forName(PROGRAM_CLASS);
            shadowProgramClass = Class.forName(SHADOW_PROGRAM_CLASS);
            assignPipeline = apiClass.getMethod("assignPipeline", RenderPipeline.class, programClass);
            assignPipelineShadow = apiClass.getMethod("assignPipelineShadow", RenderPipeline.class, shadowProgramClass);
            apiInstance = apiClass.getMethod("getInstance").invoke(null);
            Fishtastic.LOGGER.info("Iris detected - registering Fishtastic render pipelines for shaderpack rendering");
        } catch (ClassNotFoundException e) {
            // Iris not installed. Expected on most installs; not worth logging above debug.
            apiInstance = null;
        } catch (ReflectiveOperationException e) {
            Fishtastic.LOGGER.warn("Iris is present but its v0 API could not be resolved - "
                    + "Fishtastic pipelines will be invisible under shaderpacks", e);
            apiInstance = null;
        }
        return apiInstance != null;
    }

    private IrisCompat() {}
}
