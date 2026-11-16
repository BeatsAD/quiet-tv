#version 300 es
// Quiet TV V13 native port. Light transport/noise adapted from Open Water (MIT).
// Copyright (c) 2026 Leonxlnx. See assets/licenses/open-water-MIT.txt.
precision highp float;precision highp int;
in vec2 local;in float opacity;in float illumination;out vec4 color;uniform int kind;uniform vec3 tint;
void main(){
 float mask=kind==1?exp(-local.x*local.x*4.)*(1.-smoothstep(.25,1.,abs(local.y))):exp(-dot(local,local)*3.2)*(1.-smoothstep(.7,1.,length(local)));
 float a=mask*opacity;color=vec4(tint*illumination*a,a);
}