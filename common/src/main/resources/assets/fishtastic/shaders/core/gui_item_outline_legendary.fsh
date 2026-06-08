#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

// Globals UBO — layout matches GlobalSettingsUniform.java (std140):
//   ivec3  CameraPosition      (16 bytes: 12 data + 4 implicit pad)
//   vec3   CameraPositionFract (16 bytes: 12 data + 4 implicit pad)
//   vec2   ScreenSize          (8 bytes)
//   float  GlintAlpha          (4 bytes, offset 40)
//   float  GameTime            (4 bytes, offset 44) — 0..1 over one in-game day (1200 s)
//   int    MenuBlurRadius      (4 bytes)
//   int    UseRgss             (4 bytes)
layout(std140) uniform Globals {
    ivec3 CameraPosition;
    vec3  CameraPositionFract;
    vec2  ScreenSize;
    float GlintAlpha;
    float GameTime;
    int   MenuBlurRadius;
    int   UseRgss;
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

    // Derive guiScale from screen-space derivative (same approach as gui_item_outline.fsh).
    // dFdx(modelViewPos.x) = 1/guiScale since the blit spans 16 GUI units over 16*guiScale fragments.
    float dvx = abs(dFdx(modelViewPos.x));
    int guiScale = (dvx > 0.0001) ? clamp(int(round(1.0 / dvx)), 1, 8) : 1;

    // Decode packed alpha byte (legendary variant):
    //   bits 5-3 = solidness  (0-7 → 0.0–1.0)
    //   bits 2-0 = opacity    (0-7 → 0.0–1.0)
    // Outline width is hardcoded to 1; guiScale is no longer packed here.
    int packedAlpha = int(round(vertexColor.a * 255.0));
    float solidness       = float((packedAlpha >> 3) & 7) / 7.0;
    float opacity         = float(packedAlpha & 7) / 7.0;
    float falloffStrength = 1.0 - solidness;

    int radius = clamp(guiScale, 1, 16);

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
    // 150 rotations/day → 1 full rotation every 8 real seconds.
    float rotation = mod(GameTime * 150.0, 1.0) * 2.0 * PI;

    // Angle from the slot centre to this outline fragment.
    float uCenter = (uSlotMin + uSlotMax) * 0.5;
    float vCenter = (vSlotMin + vSlotMax) * 0.5;
    vec2 dir = texCoord0 - vec2(uCenter, vCenter);
    float angle = atan(dir.y, dir.x) + PI;          // 0..2*PI

    // Counter-clockwise spin: subtract the rotation offset.
    float rotatedAngle = mod(angle - rotation + 4.0 * PI, 2.0 * PI);

    // 3-blade pinwheel — each blade fills 65 % of its 120° sector; 35 % is the gap.
    // Three large blades read clearly as one rotating shape vs. 5 thin arcs.
    const int   NUM_BLADES = 3;
    const float BLADE_FILL = 0.65;
    float sectorSize  = 2.0 * PI / float(NUM_BLADES);
    float posInSector = mod(rotatedAngle, sectorSize) / sectorSize;  // 0..1

    if (posInSector >= BLADE_FILL) {
        discard;
    }

    // Sweep brightness: full at the leading edge, 50 % at the trailing edge.
    float sweepT    = posInSector / BLADE_FILL;
    float brightness = 1.0 - 0.5 * sweepT * sweepT;

    // Subtle iridescent shimmer: mostly preserves the item-defined colour,
    // with a small rainbow tint that drifts slowly over time.
    float hue      = fract(angle / (2.0 * PI) + mod(GameTime * 15.0, 1.0));
    vec3 rainbow   = hsvToRgb(vec3(hue, 0.7, 1.0));
    // 20 % rainbow shimmer + 80 % item-defined colour, modulated by sweep brightness.
    vec3 blendedColor = mix(vertexColor.rgb, rainbow, 0.2) * brightness;

    // Outline alpha with falloff (identical to basic outline).
    float t     = (minDist - 1.0) / float(max(radius - 1, 1));
    float alpha = opacity * (1.0 - falloffStrength * t);

    fragColor = vec4(blendedColor, alpha) * ColorModulator;
}
