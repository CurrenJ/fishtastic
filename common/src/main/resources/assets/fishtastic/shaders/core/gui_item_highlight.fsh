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

// "Look here" outline for GUI items — see FishtasticHighlightEffect.buildParamsBuffer().
// Layout matches FishtasticRenderPipelines.HIGHLIGHT_PARAMS_UBO_SIZE (std140, 48 bytes).
layout(std140) uniform HighlightParams {
    vec4  color;        // outline tint (RGB; W unused)
    float opacity;      // overall alpha multiplier
    float width;        // solid ring thickness in item pixels
    float glow;         // extra soft falloff beyond the ring, in item pixels
    float pulseSpeed;   // pulse cycles per in-game day (use a whole number so the wrap is seamless); 0 = no pulse
    float pulseAmount;  // 0..1, how far the glow shrinks at the bottom of the pulse (0.5 = down to half its reach)
    float _reserved0;
    float _reserved1;
    float _reserved2;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;
in vec2 modelViewPos;

out vec4 fragColor;

const float PI = 3.14159265359;

void main() {
    vec2 step = 1.0 / vec2(textureSize(Sampler0, 0));

    // dFdx(modelViewPos.x) = 1/guiScale: position advances one GUI unit per guiScale fragments.
    // Unaffected by the quad being padded past the slot.
    float dvx = abs(dFdx(modelViewPos.x));
    int guiScale = (dvx > 0.0001) ? clamp(int(round(1.0 / dvx)), 1, 8) : 1;

    // The quad is padded past the item's 16x16 slot so the glow can spill outside it, which means
    // texCoord0 can lie in a neighbouring atlas cell. The blit's vertex colour carries the item's
    // own cell (R = column, G = row from the top) — see GuiRendererMixin — and we never derive it
    // from the UV.
    float slotW    = 16.0 * float(guiScale) * step.x;
    float cellCol  = floor(vertexColor.r * 255.0 + 0.5);
    float cellRow  = floor(vertexColor.g * 255.0 + 0.5);
    float uSlotMin = cellCol * slotW;
    float uSlotMax = uSlotMin + slotW;
    float vSlotMax = 1.0 - cellRow * slotW;
    float vSlotMin = vSlotMax - slotW;

    // Solid pixels of the item itself belong to the normal item blit.
    bool insideSlot = texCoord0.x >= uSlotMin && texCoord0.x <= uSlotMax
                   && texCoord0.y >= vSlotMin && texCoord0.y <= vSlotMax;
    if (insideSlot && texture(Sampler0, texCoord0).a > 0.01) {
        discard;
    }

    float reach  = width + glow;                                   // item px
    int   radius = clamp(int(ceil(reach * float(guiScale))), 1, 20); // texels

    // Euclidean distance (texels) to the nearest opaque pixel of the item, searched only within
    // the item's own slot so neighbouring atlas cells can't bleed in.
    float minDist = float(radius) + 1.0;
    for (int dx = -radius; dx <= radius; dx++) {
        for (int dy = -radius; dy <= radius; dy++) {
            float d = length(vec2(float(dx), float(dy)));
            if (d >= minDist || d > float(radius)) continue;
            vec2 sampleUV = texCoord0 + vec2(float(dx), float(dy)) * step;
            if (sampleUV.x < uSlotMin || sampleUV.x > uSlotMax ||
                sampleUV.y < vSlotMin || sampleUV.y > vSlotMax) continue;
            if (texture(Sampler0, sampleUV).a > 0.5) {
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
    float glowNow = glow;
    if (pulseSpeed > 0.0) {
        float wave = 0.5 - 0.5 * sin(GameTime * pulseSpeed * 2.0 * PI); // 0..1
        glowNow = glow * (1.0 - pulseAmount * wave);
    }
    float reachNow = width + glowNow;

    float distItemPx = minDist / float(guiScale);
    float intensity  = 1.0 - smoothstep(width, max(reachNow, width + 0.001), distItemPx);

    fragColor = vec4(color.rgb, opacity * intensity) * ColorModulator;
}
