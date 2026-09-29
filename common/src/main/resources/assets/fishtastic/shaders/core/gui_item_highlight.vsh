#version 330

// Cannot moj_import here — these are needed verbatim.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 texCoord0;
out vec4 vertexColor;
out vec2 modelViewPos;

void main() {
    vec4 mvPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * mvPos;
    texCoord0 = UV0;
    vertexColor = Color;
    modelViewPos = mvPos.xy;
}
