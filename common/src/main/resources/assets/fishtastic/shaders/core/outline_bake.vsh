#version 150

// Vertex shader for the outline bake pass (see FishtasticItemOutlineAtlas).
// One full-slot quad in the atlas's orthographic pixel space; nothing view-dependent.

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
}
