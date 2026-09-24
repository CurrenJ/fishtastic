#version 150

// Vertex shader shared by the GUI effect programs (gui_item_silhouette, gui_texture_outline).
// 1.21.1 port of the 26.1.2 GUI vertex shaders: plain uniforms instead of the DynamicTransforms /
// Projection blocks, and POSITION_TEX (no per-vertex colour; ColorModulator carries the tint).

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
}
