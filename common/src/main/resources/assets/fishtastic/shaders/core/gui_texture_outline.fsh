#version 330

// Solid edge outline for GUI sprites blitted directly from their own texture. The gui_item_outline
// variant samples one slot of the shared item atlas; here the sampled texture is the whole sprite,
// so `width` is read straight off as source texels and the neighbour search clamps to UV [0,1].

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

// Shares the BasicOutlineParams layout with gui_item_outline.fsh — see
// FishtasticRenderPipelines.OUTLINE_PARAMS_UBO_SIZE. Only the unit of `width` differs.
layout(std140) uniform BasicOutlineParams {
    vec4  color;        // outline tint (RGB; W unused)
    float falloff;      // 0 = solid, 1 = full gradient fade at outer edge
    float opacity;      // overall opacity multiplier
    float width;        // outline thickness in SOURCE TEXELS
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

    // Opaque pixels belong to the sprite itself — the normal blit handles them.
    if (center.a > 0.01) {
        discard;
    }

    vec2 step = 1.0 / vec2(textureSize(Sampler0, 0));

    int radius = clamp(int(round(width)), 1, 8);

    float solidness       = 1.0 - falloff;
    float falloffStrength = 1.0 - solidness;

    float maxNeighbourAlpha = 0.0;
    float minDist = float(radius + 1);
    for (int dx = -radius; dx <= radius; dx++) {
        for (int dy = -radius; dy <= radius; dy++) {
            if (dx == 0 && dy == 0) continue;
            vec2 sampleUV = texCoord0 + vec2(float(dx), float(dy)) * step;
            // The whole texture is the sprite; anything outside it is not a neighbour.
            if (sampleUV.x < 0.0 || sampleUV.x > 1.0 ||
                sampleUV.y < 0.0 || sampleUV.y > 1.0) continue;
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
