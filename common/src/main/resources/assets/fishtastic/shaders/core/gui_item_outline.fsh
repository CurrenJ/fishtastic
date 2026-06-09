#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

// All outline params — uploaded once per effect type via ItemEffect.buildOutlineParamsBuffer().
// Layout matches FishtasticRenderPipelines.OUTLINE_PARAMS_UBO_SIZE (std140, 48 bytes).
layout(std140) uniform BasicOutlineParams {
    vec4  color;        // outline tint (RGB; W unused)
    float falloff;      // 0 = solid, 1 = full gradient fade at outer edge
    float opacity;      // overall opacity multiplier
    float width;        // outline thickness in item pixels; fractional values allowed (e.g. 0.5 = half item pixel)
    float animSpeed;    // unused for basic outline
    int   numBlades;    // unused for basic outline
    float bladeFill;    // unused for basic outline
    float _reserved0;
    float _reserved1;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;
in vec2 modelViewPos;

out vec4 fragColor;

void main() {
    vec4 center = texture(Sampler0, texCoord0);

    // Opaque pixels belong to the item itself — the normal item blit handles them.
    if (center.a > 0.01) {
        discard;
    }

    vec2 step = 1.0 / vec2(textureSize(Sampler0, 0));

    // Infer GUI scale from screen-space derivative.
    // dFdx(modelViewPos.x) = 1/guiScale since the blit spans 16 GUI units over 16*guiScale fragments.
    float dvx = abs(dFdx(modelViewPos.x));
    int guiScale = (dvx > 0.0001) ? clamp(int(round(1.0 / dvx)), 1, 8) : 1;

    float solidness     = 1.0 - falloff;
    float falloffStrength = 1.0 - solidness;

    // radius in screen pixels: width is fractional item pixels, guiScale converts to texels (1 texel = 1 px).
    int radius = clamp(int(round(width * float(guiScale))), 1, 16);

    // Atlas-slot UV bounds to prevent neighbour bleed.
    float slotW = 16.0 * float(guiScale) * step.x;
    float uSlotMin = floor(texCoord0.x / slotW) * slotW;
    float uSlotMax = uSlotMin + slotW;
    float vSlotMax = 1.0 - floor((1.0 - texCoord0.y) / slotW) * slotW;
    float vSlotMin = vSlotMax - slotW;

    float maxNeighbourAlpha = 0.0;
    float minDist = float(radius + 1);
    for (int dx = -radius; dx <= radius; dx++) {
        for (int dy = -radius; dy <= radius; dy++) {
            if (dx == 0 && dy == 0) continue;
            vec2 sampleUV = texCoord0 + vec2(float(dx), float(dy)) * step;
            if (sampleUV.x < uSlotMin || sampleUV.x > uSlotMax ||
                sampleUV.y < vSlotMin || sampleUV.y > vSlotMax) continue;
            float a = texture(Sampler0, sampleUV).a;
            if (a > 0.5) {
                maxNeighbourAlpha = max(maxNeighbourAlpha, a);
                minDist = min(minDist, float(max(abs(dx), abs(dy))));
            }
        }
    }

    if (maxNeighbourAlpha > 0.5) {
        float t = (minDist - 1.0) / float(max(radius - 1, 1));
        float alpha = opacity * (1.0 - falloffStrength * t);
        fragColor = vec4(color.rgb, alpha) * ColorModulator;
    } else {
        discard;
    }
}
