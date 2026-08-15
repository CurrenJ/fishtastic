#version 330

// Vertex shader for the outline bake pass (see FishtasticItemOutlineAtlas).
//
// The bake draws one full-slot quad in the atlas's orthographic pixel space, sampling the
// mask atlas (item sprite) and writing the synthesized outline ring into the outline atlas.
// Nothing here is view-dependent: the transform is the ortho projection set up by the atlas.

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

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
}
