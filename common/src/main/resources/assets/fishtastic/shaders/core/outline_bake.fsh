#version 150

// 1.21.1 port of the 26.1.2 shader of the same name. Differences: GLSL 150, plain uniforms instead
// of the OutlineParams / Globals UBOs, and the atlas geometry baked in as #defines (26.1.2 injects
// them through the RenderPipeline). Must stay in step with FishtasticItemOutlineAtlas.
#define FISHTASTIC_ATLAS_RES 4
#define FISHTASTIC_ATLAS_SLOT_PX 96

uniform vec4  OutlineColor;
uniform float OutlineFalloff;
uniform float OutlineOpacity;
uniform float OutlineWidth;
// The pinwheel uniforms (AnimSpeed, NumBlades, BladeFill, GameTime) live only in
// outline_bake_legendary: an unread uniform is compiled out, and ShaderInstance then warns that
// the JSON's entry "could not be found" (spike finding F2).

// Bakes the basic quality outline ring into an atlas slot's padding.
//
// Ported from world_item_outline.fsh, which used to run at draw time. It was moved to a bake
// pass so the ring exists as real pixels in the atlas: under a shaderpack Iris replaces our
// draw-time fragment shader with the pack's gbuffers program, which would discard any
// procedurally generated outline. See IrisCompat for the full explanation.
//
// Differences from the old draw-time shader:
//  - Samples the MASK atlas (item sprite only) and writes into the OUTLINE atlas, so there is
//    no read/write hazard on a single texture.
//  - No minification widening: the bake is always 1:1 atlas texels, so the dFdx/dFdy-derived
//    minification factor is by definition 1. Draw-time minification is now handled by the
//    mipmap-free NEAREST sampler on the composed atlas.
//  - Writes unblended, so alpha lands verbatim rather than being composited. Draw-time blending
//    is the render type's job.
//  - Never discards: non-ring fragments write transparent black instead. Because the quad covers
//    the whole slot and blending is off, that fully overwrites the slot — which is what lets the
//    caller skip the per-slot clear entirely and batch every slot into one draw. Discarding would
//    leave last frame's pinwheel blades behind.

uniform sampler2D Sampler0;   // mask atlas: item sprite, transparent padding

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    // Texels covered by the item itself carry no outline — the item model draws those.
    if (texture(Sampler0, texCoord0).a > 0.01) {
        fragColor = vec4(0.0);
        return;
    }

    vec2 texSize = vec2(textureSize(Sampler0, 0));
    vec2 step = 1.0 / texSize;

    float falloffStrength = OutlineFalloff;

    // radius in atlas texels: width is fractional item pixels, RES converts to texels.
    int radius = clamp(int(ceil(OutlineWidth * float(FISHTASTIC_ATLAS_RES))), 1, 16);

    // Atlas-slot UV bounds to prevent neighbour bleed. Grid is anchored at V=1,
    // matching FishtasticItemOutlineAtlas slot UV conventions.
    float slotW    = float(FISHTASTIC_ATLAS_SLOT_PX) * step.x;
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

    if (maxNeighbourAlpha <= 0.5) {
        fragColor = vec4(0.0);
        return;
    }

    float t = (minDist - 1.0) / float(max(radius - 1, 1));
    fragColor = vec4(OutlineColor.rgb, OutlineOpacity * (1.0 - falloffStrength * t));
}
