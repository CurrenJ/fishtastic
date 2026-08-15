#version 330

// Bakes the animated legendary (pinwheel) outline ring into an atlas slot's padding.
// Ported from world_item_outline_legendary.fsh — see outline_bake.fsh for why the outline
// generation moved from draw time to bake time.
//
// Because the pinwheel animates, slots using this shader are re-baked every frame while the
// item is on screen (FishtasticItemOutlineAtlas.collectAnimatedSlots). GameTime may be one
// frame stale — the bake runs at the head of GameRenderer.render, before the Globals UBO is
// refreshed — which is imperceptible at this rotation speed.
//
// Never discards: non-ring and between-blade fragments write transparent black. Because the quad
// covers the whole slot and blending is off, that fully overwrites it, which is what lets the
// caller skip the per-slot clear and batch every slot into one draw. This matters most here —
// discarding would leave the previous frame's blades painted in the slot forever.

layout(std140) uniform Globals {
    ivec3 CameraPosition;
    vec3  CameraPositionFract;
    vec2  ScreenSize;
    float GlintAlpha;
    float GameTime;
    int   MenuBlurRadius;
    int   UseRgss;
};

layout(std140) uniform LegendaryOutlineParams {
    vec4  color;        // outline tint (RGB; W unused)
    float falloff;      // 0 = solid, 1 = full gradient fade at outer edge
    float opacity;      // overall opacity multiplier
    float width;        // outline thickness in item pixels; fractional values allowed
    float animSpeed;    // full rotations per in-game day (default 150)
    int   numBlades;    // pinwheel blade count (default 3)
    float bladeFill;    // fraction of each sector that is filled (default 0.65)
    float _reserved0;
    float _reserved1;
};

uniform sampler2D Sampler0;   // mask atlas: item sprite, transparent padding

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

const float PI = 3.14159265359;

vec3 hsvToRgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    if (texture(Sampler0, texCoord0).a > 0.01) {
        fragColor = vec4(0.0);
        return;
    }

    vec2 texSize = vec2(textureSize(Sampler0, 0));
    vec2 step = 1.0 / texSize;

    float falloffStrength = falloff;

    int radius = clamp(int(ceil(width * float(FISHTASTIC_ATLAS_RES))), 1, 16);

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

    // ---- Pinwheel effect ----

    // GameTime: 0->1 per in-game day (24 000 ticks ~ 1200 real seconds).
    float rotation = mod(GameTime * animSpeed, 1.0) * 2.0 * PI;

    float uCenter = (uSlotMin + uSlotMax) * 0.5;
    float vCenter = (vSlotMin + vSlotMax) * 0.5;
    vec2 dir = texCoord0 - vec2(uCenter, vCenter);
    float angle = atan(dir.y, dir.x) + PI;          // 0..2*PI

    // Counter-clockwise spin: subtract the rotation offset.
    float rotatedAngle = mod(angle - rotation + 4.0 * PI, 2.0 * PI);

    float sectorSize  = 2.0 * PI / float(numBlades);
    float posInSector = mod(rotatedAngle, sectorSize) / sectorSize;  // 0..1

    if (posInSector >= bladeFill) {
        fragColor = vec4(0.0);
        return;
    }

    // Sweep brightness: full at the leading edge, 50 % at the trailing edge.
    float sweepT     = posInSector / bladeFill;
    float brightness = 1.0 - 0.5 * sweepT * sweepT;

    // Subtle iridescent shimmer: mostly preserves the item-defined colour,
    // with a small rainbow tint that drifts slowly over time.
    float hue         = fract(angle / (2.0 * PI) + mod(GameTime * 15.0, 1.0));
    vec3  rainbow     = hsvToRgb(vec3(hue, 0.7, 1.0));
    vec3  blendedColor = mix(color.rgb, rainbow, 0.2) * brightness;

    float t = (minDist - 1.0) / float(max(radius - 1, 1));
    fragColor = vec4(blendedColor, opacity * (1.0 - falloffStrength * t));
}
