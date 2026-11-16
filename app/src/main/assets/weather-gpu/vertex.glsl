#version 300 es
// Quiet TV V13 native port. Light transport/noise adapted from Open Water (MIT).
// Copyright (c) 2026 Leonxlnx. See assets/licenses/open-water-MIT.txt.
in vec2 position;
out vec2 uv;
void main(){uv=position*.5+.5;gl_Position=vec4(position,0.,1.);}