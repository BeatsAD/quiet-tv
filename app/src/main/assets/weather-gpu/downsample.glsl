#version 300 es
// Quiet TV V13 native port. Light transport/noise adapted from Open Water (MIT).
// Copyright (c) 2026 Leonxlnx. See assets/licenses/open-water-MIT.txt.
precision highp float;
uniform sampler2D source;
uniform vec2 texel;
in vec2 uv;out vec4 color;
void main(){
 vec3 c=texture(source,uv).rgb*.25;
 c+=(texture(source,uv+vec2(texel.x,0.)).rgb+texture(source,uv-vec2(texel.x,0.)).rgb+texture(source,uv+vec2(0.,texel.y)).rgb+texture(source,uv-vec2(0.,texel.y)).rgb)*.125;
 c+=(texture(source,uv+texel).rgb+texture(source,uv-texel).rgb+texture(source,uv+vec2(texel.x,-texel.y)).rgb+texture(source,uv+vec2(-texel.x,texel.y)).rgb)*.0625;
 color=vec4(c,1.);
}