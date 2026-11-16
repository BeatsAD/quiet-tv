#version 300 es
// Quiet TV V13 native port. Light transport/noise adapted from Open Water (MIT).
// Copyright (c) 2026 Leonxlnx. See assets/licenses/open-water-MIT.txt.
precision highp float;precision highp int;
layout(location=0) in vec2 position;
uniform float time;uniform float wind;uniform int kind;uniform vec2 viewSize;
uniform float fallScale;uniform float nightAmount;uniform vec2 lightCenter;uniform vec3 flash;
uniform float amount;uniform float snowStreak;uniform vec2 rainBand;
out vec2 local;out float opacity;out float illumination;
float random(uint x){x=(x^(x>>16u))*2246822519u;x=(x^(x>>13u))*3266489917u;return float(x>>8u)/16777216.;}
void main(){
 uint id=uint(gl_InstanceID);float a=random(id*1597334677u+31u),b=random(id*3812015801u+73u);
 float z=mix(.45,1.4,random(id*2798796415u+17u));
 float life=(kind==1?mix(1.0,2.4,a):mix(7.,15.,a))*sqrt(z)/fallScale;
 float age=(time+b*life)/life,cycle=floor(age),u=fract(age);
 uint birth=id*2798796415u+uint(cycle)*747796405u;
 float x=mix(-1.4,1.4,random(birth+49u));
 if(snowStreak>0.)x-=wind*1.4/z;
 float sway=kind==2?sin(time*.47+float(id)*1.73)*.11/z:0.;
 vec2 center=vec2(x+wind*2.8*u/z+sway,1.4-2.8*u);
 vec2 axis=kind==1||snowStreak>0.?normalize(vec2(wind*viewSize.x/(z*viewSize.y),-1.)):vec2(0.,1.);
 vec2 side=vec2(-axis.y,axis.x);
 float radius=kind==1?.55/z:(1.25+random(birth+91u)*2.3)/z;
 float velocity=sqrt(1.+pow(wind*viewSize.x/(z*viewSize.y),2.));
 float length=kind==1?max(7.,viewSize.y*2.8/life/100.):mix(radius,max(radius,viewSize.y*2.8/life/90.*velocity),snowStreak);
 vec2 offset=side*position.x*radius+axis*position.y*length;
 gl_Position=vec4(center+offset*2./viewSize,0.,1.);local=position;
 opacity=(kind==1?.30:.68)/sqrt(z)*(1.-smoothstep(1.12,1.38,abs(center.y)));
 opacity*=amount*mix(1.,.8,snowStreak);if(rainBand.y>0.)opacity*=1.-smoothstep(rainBand.y*.4,rainBand.y,abs(center.x-rainBand.x));
 vec2 delta=(center-lightCenter)*vec2(viewSize.x/viewSize.y,1.);
 vec2 lightningDelta=(center-flash.xy)*vec2(viewSize.x/viewSize.y,1.);
 illumination=mix(1.,.45+.55*exp(-dot(delta,delta)*.42),nightAmount)+flash.z*.35*exp(-dot(lightningDelta,lightningDelta)*1.6);
}