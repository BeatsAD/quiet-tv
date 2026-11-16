#version 300 es
// Quiet TV V13 native port. Light transport/noise adapted from Open Water (MIT).
// Copyright (c) 2026 Leonxlnx. See assets/licenses/open-water-MIT.txt.

precision highp float;precision highp int;precision highp sampler3D;precision highp sampler2DArray;
const float PI=3.14159265359;
float sat(float x){return clamp(x,0.,1.);}
uniform vec2 cloudLayer;
float cloudHeight(vec3 p){return sat((p.y-cloudLayer.x)/cloudLayer.y);}
vec3 cloudOffset(vec3 p,vec3 translation,vec3 shear){return translation+(cloudHeight(p)-.5)*shear;}
vec3 cloudGradient(vec3 n,vec3 shear){return normalize(vec3(n.x,n.y+dot(n.xz,shear.xz)/3.85,n.z));}
uniform vec3 sharedSun;
vec3 sunDirection(){return normalize(sharedSun);}
uniform sampler2D skyView;uniform sampler2D transmittanceLut;
uniform bool physicalAtmosphere;
vec3 skyLut(vec3 d){
 vec3 sh=normalize(vec3(sharedSun.x,0.,sharedSun.z)+vec3(1e-6,0.,0.));vec3 right=normalize(cross(vec3(0.,1.,0.),sh));
 float az=abs(atan(dot(d,right),dot(d,sh)))/PI;float el=asin(clamp(d.y,-1.,1.))/(PI*.5);
 return texture(skyView,vec2(az,.5+.5*sign(el)*sqrt(abs(el)))).rgb;
}
vec3 sunTransmission(float h){
 float Rg=6360.,Rt=6460.,r=Rg+max(h,.001),mu=sunDirection().y;
 float H=sqrt(Rt*Rt-Rg*Rg),rho=sqrt(max(r*r-Rg*Rg,0.));
 float d=max(0.,-r*mu+sqrt(max(r*r*(mu*mu-1.)+Rt*Rt,0.)));
 return texture(transmittanceLut,vec2((d-(Rt-r))/(rho+H-(Rt-r)),rho/H)).rgb;
}
uniform vec4 camera;uniform vec2 cameraHeading;
vec3 viewDirection(vec3 p){return vec3(cameraHeading.y*p.x-cameraHeading.x*p.z,p.y,cameraHeading.x*p.x+cameraHeading.y*p.z);}
vec3 ray(vec2 coord,vec2 size){vec2 q=coord*2.-1.;float tanV=camera.z/(size.x/size.y);vec3 local=vec3(q.x*camera.z,camera.x+q.y*camera.y*tanV,camera.y-q.y*camera.x*tanV);return normalize(vec3(cameraHeading.y*local.x+cameraHeading.x*local.z,local.y,-cameraHeading.x*local.x+cameraHeading.y*local.z));}
vec2 projectPoint(vec3 world,vec2 size){vec3 p=viewDirection(world);float forward=p.y*camera.x+p.z*camera.y;if(forward<=.001)return vec2(-10.);return .5+.5*vec2(p.x/(forward*camera.z),(p.y*camera.y-p.z*camera.x)/(forward*camera.z/(size.x/size.y)));}
float hg(float mu,float g){return (1.-g*g)/(4.*PI*pow(max(1.+g*g-2.*g*mu,.001),1.5));}
float lattice(ivec3 p){uvec3 u=uvec3(p);uint h=u.x*1597334677u^u.y*3812015801u^u.z*2798796415u;h=(h^(h>>16u))*2246822519u;h=(h^(h>>13u))*3266489917u;h^=h>>16u;return float(h>>8u)/16777216.;}
float noiseWorld(vec3 p){ivec3 i=ivec3(floor(p));vec3 f=fract(p);f=f*f*(3.-2.*f);return mix(mix(mix(lattice(i),lattice(i+ivec3(1,0,0)),f.x),mix(lattice(i+ivec3(0,1,0)),lattice(i+ivec3(1,1,0)),f.x),f.y),mix(mix(lattice(i+ivec3(0,0,1)),lattice(i+ivec3(1,0,1)),f.x),mix(lattice(i+ivec3(0,1,1)),lattice(i+ivec3(1,1,1)),f.x),f.y),f.z);}

in vec2 uv;out vec4 color;uniform sampler2D image;uniform sampler2D celestialImage;uniform bool celestialOnly;uniform float nightAmount;uniform vec2 viewSize;uniform vec4 sunDisc;uniform float sunSoftness;
uniform sampler2D cloudDepth;uniform bool reprojectCloud;uniform vec2 cloudSize;uniform vec3 displayTranslation,displayShear;
vec4 cloudImage(){
 vec4 current=texture(image,uv);
 if(!reprojectCloud)return current;
 vec2 guide=texture(cloudDepth,uv).rg;float opacity=1.-clamp(guide.y,0.,1.);
 if(opacity<.01||guide.x<=0.)return current;
 vec3 point=vec3(0.,camera.w,0.)+ray(uv,cloudSize)*guide.x;
 vec3 offset=cloudOffset(point,displayTranslation,displayShear);
 vec2 old=projectPoint(point-vec3(0.,camera.w,0.)+offset,cloudSize);
 if(any(lessThan(old,vec2(0.)))||any(greaterThan(old,vec2(1.))))return current;
 vec2 oldGuide=texture(cloudDepth,old).rg;
 if(abs(oldGuide.x-guide.x)>max(.5,guide.x*.12))return current;
 // Keep clear sky fixed; use the same warped transmission to occlude stars and moon.
 return mix(current,texture(image,old),opacity);
}
uniform vec3 solarDirection;
vec3 solar(){
 vec3 sd=normalize(solarDirection);float aspect=viewSize.x/viewSize.y;
 vec2 center=projectPoint(sd,viewSize);
 vec2 q=(uv-center)*vec2(aspect,1.)/sunDisc.w;float d=length(q),aa=max(1./(viewSize.y*sunDisc.w),sunSoftness);
 float limb=.75+.25*sqrt(max(1.-d*d,0.));
 return sunDisc.rgb*limb*(1.-smoothstep(1.-aa,1.+aa,d));
}
float starHash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
vec3 celestial(){
 vec3 direction=ray(uv,viewSize);vec2 worldUv=vec2(atan(direction.x,direction.z)/(2.*PI)+.5,asin(clamp(direction.y,-1.,1.))/PI+.5);
 vec2 grid=worldUv*vec2(320.,160.),id=floor(grid);float seed=starHash(id);
 vec2 center=vec2(starHash(id+3.7),starHash(id+9.2));
 float star=(1.-smoothstep(.015,.065,length(fract(grid)-center)))*step(.94,seed);
 vec3 value=vec3(.12,.15,.20)*star*smoothstep(-.01,.10,direction.y);
 return value;
}
uniform sampler2D moonImage;uniform bool showMoon;uniform vec3 moonDirection;uniform float moonGain,moonAngularRadius,moonScale;
vec3 lunar(){
 if(!showMoon)return vec3(0.);
 // True centre/visibility; disk enlarged for TV legibility.
 if(ray(uv,viewSize).y<0.)return vec3(0.);
 float forward=dot(viewDirection(moonDirection),vec3(0.,camera.x,camera.y));
 if(forward<=.001)return vec3(0.);
 vec2 center=projectPoint(moonDirection,viewSize);
 float radius=.5*tan(moonAngularRadius*moonScale)/(max(forward,.001)*camera.z/(viewSize.x/viewSize.y));
 vec2 q=(uv-center)*vec2(viewSize.x/viewSize.y,1.)/(radius*1.6);
 if(any(greaterThan(abs(q),vec2(1.))))return vec3(0.);
 float elevation=smoothstep(0.,.18,moonDirection.y);
 return texture(moonImage,q*.5+.5).rgb*moonGain*mix(vec3(.52,.30,.16),vec3(1.),elevation);
}
// Fine distant cloud detail is sampled at the output resolution. Near density
// stays in the low-resolution pass and history; no extra full-screen target.
uniform bool cloudDetailEnabled,sixWayBanks;uniform sampler2DArray cloudArt,cloudNegative;
uniform int bankCount;uniform vec4 bankUv[8];uniform float bankAspect[8];uniform vec3 bankAxes;uniform vec2 bankGains;
uniform vec2 detailHeading,detailParameters;
uniform vec3 detailTranslation,detailShear,detailEvolution,detailZenith,detailHorizon,detailDirect,detailAmbient;
uniform float detailHaze,detailSunGlow,detailStructure,detailSkyMix;
uniform vec3 detailSolar,detailLunar,detailSunColor,detailMoonColor;
float sixWay(vec3 p,vec3 n,vec3 direction){vec3 a=max(direction,vec3(0.)),b=max(-direction,vec3(0.));return dot(p,a*a)+dot(n,b*b);}
uniform int cloudCount;uniform vec4 cloudPosition[12],cloudMaterial[12],cloudBounds[12];
vec4 distantClouds(){
 vec3 rd=ray(uv,viewSize),eye=vec3(0.,camera.w,0.);vec3 rayDx=dFdx(rd),rayDy=dFdy(rd);
 vec3 forward=vec3(detailHeading.x,0.,detailHeading.y),right=vec3(detailHeading.y,0.,-detailHeading.x);
 vec3 palette=mix(detailHorizon,detailZenith,pow(max(rd.y,0.),.55));
 vec3 horizon=normalize(vec3(rd.x,max(.012,rd.y),rd.z));
 vec3 background=physicalAtmosphere?mix(skyLut(horizon)*6.,palette,detailSkyMix):palette;float glow=max(dot(rd,normalize(solarDirection)),0.);
 background+=vec3(1.,.92,.78)*detailSunGlow*(1.-nightAmount)*(.18*pow(glow,28.)+.75*pow(glow,700.));
 float T=1.,f=dot(rd,forward);vec3 value=vec3(0.);bool blanket=detailStructure>.5;
 vec3 sd=normalize(detailSolar),md=normalize(detailLunar);
 vec3 solar=bankAxes*vec3(dot(sd,right),sd.y,dot(sd,forward)),lunar=bankAxes*vec3(dot(md,right),md.y,dot(md,forward));
 if(f>.02&&rd.y>.005){
  for(int i=0;i<12;i++){
   if(i>=cloudCount)break;
   vec4 shape=cloudMaterial[i],box=cloudBounds[i];if(shape.w<.0001||uv.x<box.x||uv.y<box.y||uv.x>box.z||uv.y>box.w)continue;
   vec4 card=cloudPosition[i];float t=card.z/f;vec3 p=eye+rd*t;
   vec2 coord=vec2((dot(p,right)-card.x)/card.w,(p.y-card.y)/shape.y);
   if(any(greaterThan(abs(coord),vec2(1.))))continue;
   vec3 pDx=t*(rayDx-rd*dot(rayDx,forward)/f),pDy=t*(rayDy-rd*dot(rayDy,forward)/f);
   vec2 texDx=vec2(dot(pDx,right)/card.w,pDx.y/shape.y)*.5,texDy=vec2(dot(pDy,right)/card.w,pDy.y/shape.y)*.5;
   coord.x*=shape.z;texDx.x*=shape.z;texDy.x*=shape.z;
   int tile=int(shape.x);vec4 rect=bankUv[tile];vec3 bankCoord=vec3(rect.xy+(coord*.5+.5)*rect.zw,float(tile));vec2 dx=texDx*rect.zw,dy=texDy*rect.zw;
   vec4 data=textureGrad(cloudArt,bankCoord,dx,dy);float coverage=sixWayBanks?data.a:data.r;
   vec2 footprint=max(abs(texDx)+abs(texDy),1./vec2(textureSize(cloudArt,0).xy));
   vec2 border=1.-smoothstep(vec2(1.)-clamp(footprint*3.,vec2(.008),vec2(.25)),vec2(1.),abs(coord));
   float alpha=(1.-pow(max(1.-coverage,.0001),detailParameters.y))*shape.w*smoothstep(.005,.060,rd.y)*border.x*border.y;
   if(blanket)alpha*=(1.-smoothstep(.75,.98,abs(coord.x)))*(1.-smoothstep(.82,1.,abs(coord.y)));
   if(alpha<.0001)continue;
   vec3 contribution;
   if(sixWayBanks){
    vec3 positive=data.rgb,negative=textureGrad(cloudNegative,bankCoord,dx,dy).rgb;
    vec3 sl=solar,ml=lunar;sl.x*=shape.z;ml.x*=shape.z;
    float sky=.7*(bankAxes.y<0.?negative.g:positive.g)+.06*(positive.r+negative.r+positive.b+negative.b+(bankAxes.y<0.?positive.g:negative.g));
    // Keep data linear and premultiplied until its final opacity scale.
    contribution=(detailAmbient*sky*bankGains.x+(detailSunColor*sixWay(positive,negative,sl)+detailMoonColor*sixWay(positive,negative,ml))*bankGains.y)*(alpha/max(coverage,.001));
   }else{
    vec3 material=data.gba/max(coverage,.001);vec2 relief=(material.xy*2.-1.)*smoothstep(.06,.25,coverage);relief.x*=shape.z;
    vec3 normal=normalize(vec3(relief,sqrt(max(.1,1.-dot(relief,relief)))));
    vec3 sl=vec3(dot(sd,right),sd.y,-dot(sd,forward)),ml=vec3(dot(md,right),md.y,-dot(md,forward));
    float day=.75+.25*clamp(dot(normal,sl)*.5+.5,0.,1.),night=.75+.25*clamp(dot(normal,ml)*.5+.5,0.,1.);
    contribution=(detailAmbient*.55+detailSunColor*.75*day+detailMoonColor*.75*night)*(.10+1.80*clamp(material.z,0.,1.))*alpha;
   }
   // Black-plate relief has no measured extinction. Thin coverage transmits
   // the sky; only dense interiors may retain a modest shadow against it.
   if(!sixWayBanks){
    vec3 transmitted=background*(1.-.15*smoothstep(.45,.95,coverage));
    contribution=max(contribution,transmitted*alpha);
   }
   contribution=mix(contribution,background*alpha,clamp((1.-exp(-t*.009))*detailHaze,0.,.7));value+=contribution*T;T*=1.-alpha;
  }
 }
 return vec4(value+background*T,T);
}
void main(){if(celestialOnly){vec3 light=vec3(0.);if(nightAmount>0.)light+=celestial()*nightAmount;if(sunDisc.w>0.)light+=solar();color=vec4(light,1.);return;}vec4 sampleColor=cloudDetailEnabled&&sixWayBanks?vec4(0.,0.,0.,1.):cloudImage();if(cloudDetailEnabled){vec4 farCloud=distantClouds();sampleColor.rgb+=farCloud.rgb*sampleColor.a;sampleColor.a*=farCloud.a;}vec3 x=max(sampleColor.rgb,vec3(0.));vec3 light=texture(celestialImage,uv).rgb;float occlusion=sunDisc.w>0.?(sunSoftness>.3?smoothstep(.0008,.01,sampleColor.a):smoothstep(.015,.12,sampleColor.a)):1.;x+=(light*occlusion+lunar())*sampleColor.a;x=clamp((x*(2.51*x+.03))/(x*(2.43*x+.59)+.14),0.,1.);// Stable sub-LSB dither breaks 8 bit dark-sky bands without another pass.
vec3 display=pow(x,vec3(1./2.2));float dither=(lattice(ivec3(ivec2(gl_FragCoord.xy),74719))-.5)/255.;color=vec4(clamp(display+dither,0.,1.),1.);}
