package grill24.fishtastic.client.renderer;
import net.minecraft.client.renderer.ShaderInstance;
/**
 * Holds references to custom shaders used by Fishtastic.
 */
public class FishtasticShaders {
    private static ShaderInstance qualityGlowShader;
    private static ShaderInstance entityQualityGlowShader;
    private static ShaderInstance entityQualityGlowDirectShader;
    private static ShaderInstance qualityGlowTranslucentShader;

    public static ShaderInstance getQualityGlowShader() {
        return qualityGlowShader;
    }
    public static void setQualityGlowShader(ShaderInstance shader) {
        qualityGlowShader = shader;
    }

    public static ShaderInstance getEntityQualityGlowShader() {
        return entityQualityGlowShader;
    }
    public static void setEntityQualityGlowShader(ShaderInstance shader) {
        entityQualityGlowShader = shader;
    }

    public static ShaderInstance getEntityQualityGlowDirectShader() {
        return entityQualityGlowDirectShader;
    }
    public static void setEntityQualityGlowDirectShader(ShaderInstance shader) {
        entityQualityGlowDirectShader = shader;
    }

    public static ShaderInstance getQualityGlowTranslucentShader() {
        return qualityGlowTranslucentShader;
    }
    public static void setQualityGlowTranslucentShader(ShaderInstance shader) {
        qualityGlowTranslucentShader = shader;
    }
}
