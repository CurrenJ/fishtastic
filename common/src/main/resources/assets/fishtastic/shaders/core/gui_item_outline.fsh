#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
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

    // Decode alpha byte:
    //   bits 7-6 = outlineWidth-1 (0-3 → 1-4 item pixels)
    //   bits 5-3 = solidness       (0-7 → 0.0–1.0, where 7 = solid / no falloff)
    //   bits 2-0 = opacity         (0-7 → 0.0–1.0)
    int packedAlpha = int(round(vertexColor.a * 255.0));
    int outlineWidth     = (packedAlpha >> 6) + 1;
    float solidness      = float((packedAlpha >> 3) & 7) / 7.0;
    float opacity        = float(packedAlpha & 7) / 7.0;
    float falloffStrength = 1.0 - solidness;

    // Infer GUI scale: the blit spans 16 GUI units across 16*guiScale physical pixels,
    // so dFdx(modelViewPos.x) = 1/guiScale.
    // Clamp to [1,4] to guard against near-zero derivatives (-> inf -> GPU crash).
    float dvx = abs(dFdx(modelViewPos.x));
    int guiScale = (dvx > 0.0001) ? clamp(int(round(1.0 / dvx)), 1, 8) : 1;

    // radius = outlineWidth item pixels, each item pixel = guiScale atlas texels.
    // Hard cap at 16 to bound loop size (worst case: 4px * guiScale 4 = 16 → 33x33).
    int radius = clamp(outlineWidth * guiScale, 1, 16);

    // Compute the atlas-slot UV bounds so neighbour samples don't bleed into
    // adjacent slots (which hold different items).
    float slotW = 16.0 * float(guiScale) * step.x;
    float uSlotMin = floor(texCoord0.x / slotW) * slotW;
    float uSlotMax = uSlotMin + slotW;
    // v is inverted in the atlas (v0 > v1), so work in (1 - v) space.
    float vSlotMax = 1.0 - floor((1.0 - texCoord0.y) / slotW) * slotW;
    float vSlotMin = vSlotMax - slotW;

    float maxNeighbourAlpha = 0.0;
    float minDist = float(radius + 1); // Chebyshev distance to nearest opaque neighbour
    for (int dx = -radius; dx <= radius; dx++) {
        for (int dy = -radius; dy <= radius; dy++) {
            if (dx == 0 && dy == 0) continue;
            vec2 sampleUV = texCoord0 + vec2(float(dx), float(dy)) * step;
            // Skip samples that land in a neighbouring slot — they belong to a
            // different item and would cause outline bleed across atlas boundaries.
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
        // t = 0 at the innermost ring (adjacent to item), 1 at the outermost ring.
        float t = (minDist - 1.0) / float(max(radius - 1, 1));
        float alpha = opacity * (1.0 - falloffStrength * t);
        fragColor = vec4(vertexColor.rgb, alpha) * ColorModulator;
    } else {
        discard;
    }
}
