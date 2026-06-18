#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform Globals {
    ivec3 CameraPosition;
    vec3  CameraPositionFract;
    vec2  ScreenSize;
    float GlintAlpha;
    float GameTime;
    int   MenuBlurRadius;
    int   UseRgss;
};

// Params for the GUI item silhouette fill — see FishtasticSilhouetteEffect.buildParamsBuffer().
layout(std140) uniform SilhouetteParams {
    vec4  color;        // fill colour (RGB; W unused)
    float opacity;      // overall alpha multiplier
    float pulseSpeed;   // breathing-alpha cycles per in-game day; 0 = no pulse
    float pulseAmount;  // 0..1, how deep the alpha dips at the bottom of the breathing cycle
    float _reserved0;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;
in vec2 modelViewPos;

out vec4 fragColor;

const float PI = 3.14159265359;

void main() {
    // Same atlas slot the normal item blit reads from — this samples the real, already-baked
    // render of the item (any model, any animation frame), so the fill traces its exact shape.
    vec4 sampled = texture(Sampler0, texCoord0);
    if (sampled.a < 0.01) {
        discard;
    }

    float alpha = opacity;
    if (pulseSpeed > 0.0) {
        float t = fract(GameTime * pulseSpeed);
        float wave = 0.5 - 0.5 * cos(t * 2.0 * PI); // smooth 0..1 breathing cycle
        alpha *= 1.0 - pulseAmount * wave;
    }

    fragColor = vec4(color.rgb, alpha * sampled.a) * ColorModulator;
}
