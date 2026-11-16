#version 300 es
// Quiet TV V13 native port. Light transport/noise adapted from Open Water (MIT).
// Copyright (c) 2026 Leonxlnx. See assets/licenses/open-water-MIT.txt.
precision highp float;
uniform sampler2D source;
uniform float maximumSigma;
uniform float maximumLevel;
uniform float brightness;
in vec2 uv;out vec4 color;
void main(){
 float distanceFromClear=clamp(((1.-uv.y)-.34)/.54,0.,1.);
 float sigma=maximumSigma*pow(distanceFromClear,3.);
 float level=sigma<1.?sigma:1.+log2(sigma);
 vec3 blurred=textureLod(source,uv,clamp(level,0.,maximumLevel)).rgb;
 // Only the subpixel onset fades in. At useful blur radii the surface is opaque,
 // so sharp wallpaper detail cannot leak through a translucent blur overlay.
 float alpha=smoothstep(0.,.75,sigma);color=vec4(blurred*brightness*alpha,alpha);
}