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

// All outline params — uploaded once per effect type via ItemEffect.buildOutlineParamsBuffer().
layout(std140) uniform LegendaryOutlineParams {
    vec4  color;        // outline tint (RGB; W unused)
    float falloff;      // 0 = solid, 1 = full gradient fade at outer edge
    float opacity;      // overall opacity multiplier
    float width;        // outline thickness in item pixels; fractional values allowed (e.g. 0.5 = half item pixel)
    float animSpeed;    // full rotations per in-game day (default 150)
    int   numBlades;    // pinwheel blade count (default 3)
    float bladeFill;    // fraction of each sector that is filled (default 0.65)
    float _reserved0;
    float _reserved1;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;
in vec2 modelViewPos;

out vec4 fragColor;

const float PI = 3.14159265359;

vec3 hsvToRgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    vec4 center = texture(Sampler0, texCoord0);

    // Opaque pixels belong to the item itself — discard them.
    if (center.a > 0.01) {
        discard;
    }

    vec2 step = 1.0 / vec2(textureSize(Sampler0, 0));

    // Derive guiScale from screen-space derivative — no packing limit.
    // dFdx(modelViewPos.x) = 1/guiScale since the blit spans 16 GUI units over 16*guiScale fragments.
    float dvx = abs(dFdx(modelViewPos.x));
    int guiScale = (dvx > 0.0001) ? clamp(int(round(1.0 / dvx)), 1, 8) : 1;

    float solidness       = 1.0 - falloff;
    float falloffStrength = 1.0 - solidness;

    // radius in screen pixels: width is fractional item pixels, guiScale converts to texels (1 texel = 1 px).
    int radius = clamp(int(round(width * float(guiScale))), 1, 16);

    // Atlas slot bounds — prevents bleed across adjacent items.
    float slotW    = 16.0 * float(guiScale) * step.x;
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
        discard;
    }

    // ---- Pinwheel effect ----

    // GameTime: 0→1 per in-game day (24 000 ticks ≈ 1200 real seconds).
    float rotation = mod(GameTime * animSpeed, 1.0) * 2.0 * PI;

    // Angle from the slot centre to this outline fragment.
    float uCenter = (uSlotMin + uSlotMax) * 0.5;
    float vCenter = (vSlotMin + vSlotMax) * 0.5;
    vec2 dir = texCoord0 - vec2(uCenter, vCenter);
    float angle = atan(dir.y, dir.x) + PI;          // 0..2*PI

    // Counter-clockwise spin: subtract the rotation offset.
    float rotatedAngle = mod(angle - rotation + 4.0 * PI, 2.0 * PI);

    float sectorSize  = 2.0 * PI / float(numBlades);
    float posInSector = mod(rotatedAngle, sectorSize) / sectorSize;  // 0..1

    if (posInSector >= bladeFill) {
        discard;
    }

    // Sweep brightness: full at the leading edge, 50 % at the trailing edge.
    float sweepT     = posInSector / bladeFill;
    float brightness = 1.0 - 0.5 * sweepT * sweepT;

    // Subtle iridescent shimmer: mostly preserves the item-defined colour,
    // with a small rainbow tint that drifts slowly over time.
    float hue      = fract(angle / (2.0 * PI) + mod(GameTime * 15.0, 1.0));
    vec3 rainbow   = hsvToRgb(vec3(hue, 0.7, 1.0));
    vec3 blendedColor = mix(color.rgb, rainbow, 0.2) * brightness;

    // Outline alpha with falloff.
    float t     = (minDist - 1.0) / float(max(radius - 1, 1));
    float alpha = opacity * (1.0 - falloffStrength * t);

    fragColor = vec4(blendedColor, alpha) * ColorModulator;
}
