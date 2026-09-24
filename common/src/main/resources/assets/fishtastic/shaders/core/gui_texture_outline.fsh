#version 150

// 1.21.1 port of the 26.1.2 shader of the same name. Differences: GLSL 150 and plain uniforms
// instead of the DynamicTransforms / BasicOutlineParams blocks.
//
// Edge outline for GUI sprites blitted straight from their own texture (the fishing minigame's
// zone icons, see FishtasticTextureOutlineEffect): draws the outline colour on transparent texels
// within OutlineWidth SOURCE TEXELS of an opaque one, inside the sprite's own rect.

uniform vec4  ColorModulator;
uniform vec4  OutlineColor;     // outline tint (RGB; W unused)
uniform float OutlineFalloff;   // 0 = solid, 1 = full gradient fade at outer edge
uniform float OutlineOpacity;   // overall opacity multiplier
uniform float OutlineWidth;     // outline thickness in SOURCE TEXELS

uniform sampler2D Sampler0;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 center = texture(Sampler0, texCoord0);

    if (center.a > 0.01) {
        discard;
    }

    vec2 step = 1.0 / vec2(textureSize(Sampler0, 0));

    int radius = clamp(int(round(OutlineWidth)), 1, 8);

    float solidness       = 1.0 - OutlineFalloff;
    float falloffStrength = 1.0 - solidness;

    float maxNeighbourAlpha = 0.0;
    float minDist = float(radius + 1);
    for (int dx = -radius; dx <= radius; dx++) {
        for (int dy = -radius; dy <= radius; dy++) {
            if (dx == 0 && dy == 0) continue;
            vec2 sampleUV = texCoord0 + vec2(float(dx), float(dy)) * step;
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
        float alpha = OutlineOpacity * (1.0 - falloffStrength * t);
        fragColor = vec4(OutlineColor.rgb, alpha) * ColorModulator;
    } else {
        discard;
    }
}
