#version 150

// 1.21.1 port of the 26.1.2 shader of the same name. Differences: GLSL 150, plain uniforms instead
// of the DynamicTransforms / Globals / HighlightParams blocks (GameTime is the standard
// ShaderInstance uniform), and Sampler0 is Fishtastic's outline MASK atlas (the item rendered at
// TexelsPerItemPx texels per item pixel) rather than vanilla's GUI item atlas, which 1.21.1 doesn't
// have. So distances are converted with TexelsPerItemPx instead of the GUI scale, and the item's
// cell comes from the SlotBounds uniform instead of the vertex colour.

uniform vec4  ColorModulator;
uniform float GameTime;
uniform vec4  HighlightColor;   // outline tint (RGB; W unused)
uniform float Opacity;          // overall alpha multiplier
uniform float Width;            // solid ring thickness in item pixels
uniform float Glow;             // extra soft falloff beyond the ring, in item pixels
uniform float PulseSpeed;       // pulse cycles per in-game day (use a whole number so the wrap is seamless); 0 = no pulse
uniform float PulseAmount;      // 0..1, how far the glow shrinks at the bottom of the pulse
uniform float TexelsPerItemPx;  // mask texels per item pixel
uniform vec4  SlotBounds;       // the item's mask slot: (uMin, vMin, uMax, vMax)

uniform sampler2D Sampler0;

in vec2 texCoord0;

out vec4 fragColor;

const float PI = 3.14159265359;

bool inSlot(vec2 uv) {
    return uv.x >= SlotBounds.x && uv.x <= SlotBounds.z && uv.y >= SlotBounds.y && uv.y <= SlotBounds.w;
}

void main() {
    vec2 step = 1.0 / vec2(textureSize(Sampler0, 0));

    // Solid pixels of the item itself belong to the normal item draw.
    if (inSlot(texCoord0) && texture(Sampler0, texCoord0).a > 0.01) {
        discard;
    }

    float reach  = Width + Glow;                                          // item px
    int   radius = clamp(int(ceil(reach * TexelsPerItemPx)), 1, 20);     // texels

    // Euclidean distance (texels) to the nearest opaque pixel of the item, searched only within
    // the item's own slot so neighbouring atlas slots can't bleed in.
    float minDist = float(radius) + 1.0;
    for (int dx = -radius; dx <= radius; dx++) {
        for (int dy = -radius; dy <= radius; dy++) {
            float d = length(vec2(float(dx), float(dy)));
            if (d >= minDist || d > float(radius)) continue;
            vec2 sampleUV = texCoord0 + vec2(float(dx), float(dy)) * step;
            if (!inSlot(sampleUV)) continue;
            if (texture(Sampler0, sampleUV).a > 0.5) {
                minDist = d;
            }
        }
    }

    if (minDist > float(radius)) {
        discard;
    }

    // The pulse breathes the glow's falloff, not the overall alpha: the solid ring stays at full
    // strength while the soft halo around it shrinks and swells.
    float glowNow = Glow;
    if (PulseSpeed > 0.0) {
        float wave = 0.5 - 0.5 * sin(GameTime * PulseSpeed * 2.0 * PI); // 0..1
        glowNow = Glow * (1.0 - PulseAmount * wave);
    }
    float reachNow = Width + glowNow;

    float distItemPx = minDist / TexelsPerItemPx;
    float intensity  = 1.0 - smoothstep(Width, max(reachNow, Width + 0.001), distItemPx);

    fragColor = vec4(HighlightColor.rgb, Opacity * intensity) * ColorModulator;
}
