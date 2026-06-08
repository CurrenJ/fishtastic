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
    vec2 step = 1.0 / vec2(textureSize(Sampler0, 0));

    // Derive guiScale from screen-space derivative — no packing limit.
    float dvx = abs(dFdx(modelViewPos.x));
    int guiScale = (dvx > 0.0001) ? clamp(int(round(1.0 / dvx)), 1, 8) : 1;

    // Compute slot bounds — mirrors the legendary shader's atlas-slot logic exactly.
    float slotW    = 16.0 * float(guiScale) * step.x;
    float uSlotMin = floor(texCoord0.x / slotW) * slotW;
    float uSlotMax = uSlotMin + slotW;
    float vSlotMax = 1.0 - floor((1.0 - texCoord0.y) / slotW) * slotW;
    float vSlotMin = vSlotMax - slotW;

    // Slot-relative UV (0..1 within this item's 16-px atlas slot).
    // Red  = U: 0 (left) → 1 (right)
    // Green = V: 0 (top)  → 1 (bottom)  — matches atlas V convention
    float u = (texCoord0.x - uSlotMin) / (uSlotMax - uSlotMin);
    float v = (texCoord0.y - vSlotMin) / (vSlotMax - vSlotMin);

    fragColor = vec4(u, v, 0.0, 1.0) * ColorModulator;
}
