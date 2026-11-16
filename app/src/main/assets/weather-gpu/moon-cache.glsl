#version 300 es
// Original Quiet TV phase-lit artistic lunar surface, cached in a 128px tile.
precision highp float;
in vec2 uv;out vec4 color;
uniform sampler2D albedo;uniform vec3 phaseLight;uniform float illuminated;
void main(){
 vec2 q=(uv*2.-1.)*1.6;float d=length(q),aa=3.2/128.;
 vec3 value=vec3(.003,.005,.009)*illuminated*exp(-d*d*1.3)*(1.-smoothstep(1.35,1.58,d));
 if(d<1.+aa){
  vec3 n=vec3(q,sqrt(max(1.-dot(q,q),0.)));
  float surface=texture(albedo,clamp(q*.5+.5,0.,1.)).r;
  float sunlight=max(dot(n,phaseLight),0.);
  float limb=1.-smoothstep(1.-aa,1.+aa,d);
  value+=vec3(1.15,1.22,1.32)*surface*(sunlight+.006)*limb;
 }
 color=vec4(value,1.);
}
