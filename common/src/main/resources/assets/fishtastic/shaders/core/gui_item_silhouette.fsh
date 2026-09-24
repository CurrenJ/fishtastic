#version 150

// 1.21.1 port of the 26.1.2 shader of the same name. Differences: GLSL 150, plain uniforms instead
// of the DynamicTransforms / Globals / SilhouetteParams blocks (GameTime is the standard
// ShaderInstance uniform), and Sampler0 is Fishtastic's outline MASK atlas (the item rendered at
// 4 texels per item pixel) rather than vanilla's GUI item atlas, which 1.21.1 doesn't have.
// FishtasticSilhouetteEffect rescales EdgeBlurTexels so the blur covers the same item-pixel
// distance as on 26.1.2.

uniform vec4  ColorModulator;
uniform float GameTime;
uniform vec4  SilhouetteColor;   // fill colour (RGB; W unused)
uniform float Opacity;           // overall alpha multiplier
uniform float PulseSpeed;        // breathing-alpha cycles per in-game day; 0 = no pulse
uniform float PulseAmount;       // 0..1, how deep the alpha dips at the bottom of the breathing cycle
uniform float EdgeBlurTexels;    // atlas-texel radius for the box blur that rounds off fins/spikes; 0 = sharp
uniform float DissolveScale;     // noise frequency in screen pixels; higher = finer grain
uniform float DissolveSpeed;     // noise drift speed (slow ambient motion); 0 = static per-icon pattern
uniform float DissolveStrength;  // 0..1, how far the dissolve can erode/extend the silhouette boundary

uniform sampler2D Sampler0;

in vec2 texCoord0;

out vec4 fragColor;

const float PI = 3.14159265359;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

void main() {
    // The real, already-rendered image of the item (any model, any animation frame). Rather than
    // tracing its exact alpha (which gives away the species via fin/tail shape), we box-blur it
    // first to round off tell-tale fine detail, then fray the boundary with noise so it reads as a
    // hazy, shrouded blob instead of a crisp silhouette.
    vec2 texel = EdgeBlurTexels / vec2(textureSize(Sampler0, 0));
    float blurredAlpha = 0.0;
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            blurredAlpha += texture(Sampler0, texCoord0 + vec2(x, y) * texel).a;
        }
    }
    blurredAlpha /= 9.0;

    if (blurredAlpha < 0.01) {
        discard;
    }

    // Static-per-icon noise pattern (seeded by screen position, since each encyclopedia icon
    // occupies a fixed screen rect) with optional slow drift for a gentle "alive" shimmer.
    vec2 noiseUv = gl_FragCoord.xy * DissolveScale + GameTime * DissolveSpeed;
    float n = valueNoise(noiseUv);

    // Only perturb near the boundary (blurredAlpha ~0.5) so the body stays a solid, readable
    // blob and just the contour gets tattered — that's where the species-revealing detail lives.
    float edgeFactor = 1.0 - abs(blurredAlpha * 2.0 - 1.0);
    float dissolvedAlpha = clamp(blurredAlpha + (n - 0.5) * DissolveStrength * edgeFactor, 0.0, 1.0);

    float alpha = Opacity * dissolvedAlpha;
    if (PulseSpeed > 0.0) {
        float t = fract(GameTime * PulseSpeed);
        float wave = 0.5 - 0.5 * cos(t * 2.0 * PI); // smooth 0..1 breathing cycle
        alpha *= 1.0 - PulseAmount * wave;
    }

    fragColor = vec4(SilhouetteColor.rgb, alpha) * ColorModulator;
}
