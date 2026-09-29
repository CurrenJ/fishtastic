#version 150

// 1.20.1 port of the 26.1.2 shader of the same name ("look here" outline for GUI items, see
// FishtasticHighlightEffect). Differences: GLSL 150, plain uniforms instead of the
// DynamicTransforms / Globals / HighlightParams blocks (GameTime is the standard ShaderInstance
// uniform), and Sampler0 is Fishtastic's outline MASK atlas (the item rendered at TexelsPerItemPx
// texels per item pixel) rather than vanilla's GUI item atlas, which 1.20.1 doesn't have. The mask
// slot is padded past the item by more than the quad is, so the search can't reach a neighbouring
// slot and the item's cell needs no bounds (26.1.2 passes them in the vertex colour).

uniform vec4  ColorModulator;
uniform float GameTime;
uniform vec4  HighlightColor;   // outline tint (RGB; W unused)
uniform float Opacity;          // overall alpha multiplier
uniform float Width;            // solid ring thickness in item pixels
uniform float Glow;             // extra soft falloff beyond the ring, in item pixels
uniform float PulseSpeed;       // pulse cycles per in-game day (use a whole number so the wrap is seamless); 0 = no pulse
uniform float PulseAmount;      // 0..1, how far the glow shrinks at the bottom of the pulse (0.5 = down to half its reach)
uniform float TexelsPerItemPx;  // mask atlas texels per item pixel

uniform sampler2D Sampler0;

in vec2 texCoord0;

out vec4 fragColor;

const float PI = 3.14159265359;

void main() {
    vec2 step = 1.0 / vec2(textureSize(Sampler0, 0));

    // Solid pixels of the item itself belong to the normal item draw.
    if (texture(Sampler0, texCoord0).a > 0.01) {
        discard;
    }

    float reach  = Width + Glow;                                          // item px
    int   radius = clamp(int(ceil(reach * TexelsPerItemPx)), 1, 20);    // texels

    // Euclidean distance (texels) to the nearest opaque pixel of the item.
    float minDist = float(radius) + 1.0;
    for (int dx = -radius; dx <= radius; dx++) {
        for (int dy = -radius; dy <= radius; dy++) {
            float d = length(vec2(float(dx), float(dy)));
            if (d >= minDist || d > float(radius)) continue;
            if (texture(Sampler0, texCoord0 + vec2(float(dx), float(dy)) * step).a > 0.5) {
                minDist = d;
            }
        }
    }

    if (minDist > float(radius)) {
        discard;
    }

    // The pulse breathes the glow's falloff, not the overall alpha: the solid ring stays at full
    // strength while the soft halo around it shrinks and swells. `radius` above is sized for the
    // fully swollen glow, so the search always covers the widest reach.
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
