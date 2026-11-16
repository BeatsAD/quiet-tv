#version 300 es
// Quiet TV V13 native port. Light transport/noise adapted from Open Water (MIT).
// Copyright (c) 2026 Leonxlnx. See assets/licenses/open-water-MIT.txt.
precision highp float;
uniform sampler2D source;
uniform vec4 crop;
in vec2 uv;out vec4 color;
void main(){vec2 p=vec2(crop.x+uv.x*crop.z,1.-(crop.y+(1.-uv.y)*crop.w));color=vec4(texture(source,p).rgb,1.);}