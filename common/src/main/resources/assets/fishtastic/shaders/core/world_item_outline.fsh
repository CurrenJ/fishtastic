#version 330

// In-world port of gui_item_outline.fsh, sampling the Fishtastic item outline atlas
// (FishtasticItemOutlineAtlas) instead of the vanilla GuiItemAtlas.
//
// Differences from the GUI shader:
//  - No guiScale derivation: the atlas has fixed slot geometry, injected as defines by
//    FishtasticRenderPipelines (FISHTASTIC_ATLAS_SLOT_PX = slot size in texels,
//    FISHTASTIC_ATLAS_RES = texels per item pixel).
//  - Slots are padded, so the outline can extend past the item sprite's edge.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

// Same std140 layout as the GUI shaders — the GpuBuffer is shared per effect.
layout(std140) uniform BasicOutlineParams {
    vec4  color;        // outline tint (RGB; W unused)
    float falloff;      // 0 = solid, 1 = full gradient fade at outer edge
    float opacity;      // overall opacity multiplier
    float width;        // outline thickness in item pixels; fractional values allowed
    float animSpeed;    // unused for basic outline
    int   numBlades;    // unused for basic outline
    float bladeFill;    // unused for basic outline
    float _reserved0;
    float _reserved1;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 center = texture(Sampler0, texCoord0);

    // Opaque pixels belong to the item itself — the item model renders them.
    if (center.a > 0.01) {
        discard;
    }

    vec2 texSize = vec2(textureSize(Sampler0, 0));
    vec2 step = 1.0 / texSize;

    float solidness       = 1.0 - falloff;
    float falloffStrength = 1.0 - solidness;

    // Minification factor: atlas texels stepped per screen pixel. When the item is far
    // away this exceeds 1 and a thin outline ring would fall between fragment samples
    // (NEAREST, no mips) and vanish — so the ring is widened to stay >= ~1 screen pixel.
    // Capped at 8: beyond that the item is only a few pixels tall and a wider scan is
    // wasted work. (The GUI shader needs no such logic — GUI blits never minify.)
    vec2 ddx = dFdx(texCoord0) * texSize;
    vec2 ddy = dFdy(texCoord0) * texSize;
    float minification = clamp(max(length(ddx), length(ddy)), 1.0, 8.0);

    // radius in atlas texels: width is fractional item pixels, RES converts to texels.
    int radius = clamp(int(ceil(max(width * float(FISHTASTIC_ATLAS_RES), minification))), 1, 16);

    // Atlas-slot UV bounds to prevent neighbour bleed. Grid is anchored at V=1,
    // matching GuiItemAtlas/FishtasticItemOutlineAtlas slot UV conventions.
    float slotW = float(FISHTASTIC_ATLAS_SLOT_PX) * step.x;
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
