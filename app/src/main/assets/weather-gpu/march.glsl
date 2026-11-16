#version 300 es
// Quiet TV V13 native port. Light transport/noise adapted from Open Water (MIT).
// Copyright (c) 2026 Leonxlnx. See assets/licenses/open-water-MIT.txt.

precision highp float;precision highp int;precision highp sampler3D;
const float PI=3.14159265359;
float sat(float x){return clamp(x,0.,1.);}
float cloudHeight(vec3 p){return sat((p.y-1.1)/3.85);}
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

in vec2 uv;layout(location=0) out vec4 color;layout(location=1) out vec4 depth;
uniform sampler3D shapeTex;uniform sampler3D detailTex;uniform sampler3D reliefTex;uniform sampler3D bodyTex;
uniform sampler3D curlTex;uniform bool curlEnabled;
uniform sampler2D blueNoise;uniform int lightSteps;uniform bool layeredLight;
uniform vec2 cloudParameters;uniform float skyOverride;uniform vec3 skyZenith;uniform vec3 skyHorizon;uniform vec3 directLight;uniform vec3 ambientLight;uniform float hazeAmount;
uniform float skyFalloff;uniform vec4 sunDisc;
uniform int cloudKind;
uniform vec4 fogParameters;uniform vec4 lightning;uniform float lightningRadius;
uniform vec2 resolution;uniform vec2 jitter;uniform vec3 translation;uniform vec3 shear;uniform float deformationStretch;uniform vec3 evolution;uniform vec3 origin;uniform int frameIndex;uniform int steps;
float footprint;
vec3 densityNormal;
float mergeCloud(float a,float b){float h=sat(.5+.5*(a-b)/.25);return mix(b,a,h)+.25*h*(1.-h);}
float ellipsoid(vec3 p,vec3 center,vec3 radius){vec3 d=(p-center)/radius;return 1.-dot(d,d);}
float cirrusDensity(vec3 p){
 if(p.y<5.8||p.y>7.2)return 0.;
 vec3 world=p+translation+shear*.5;world.xz+=origin.xz;
 vec2 bend=vec2(noiseWorld(world*.12+evolution*.012),noiseWorld(world*.12+evolution*.012+vec3(17.,8.,3.)))*2.-1.;
 vec2 flow=world.xz+bend*1.3+evolution.xz*.4;ivec2 cell=ivec2(floor(flow/12.));float field=0.;
 float contrast=1.-smoothstep(.035,.12,footprint);
 for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++){
  ivec2 id=cell+ivec2(x,z);float a=lattice(ivec3(id.x,731,id.y)),b=lattice(ivec3(id.x,982,id.y));
  vec2 center=(vec2(id)+vec2(.2+.6*a,.2+.6*b))*12.;
  vec2 axis=normalize(vec2(1.,.10+.65*(a-.5))),side=vec2(-axis.y,axis.x),q=flow-center;
  float along=dot(q,axis)/(4.5+4.*b);if(abs(along)>1.)continue;
  float curve=.85*sin(along*2.1+a*6.28)+.45*along*along;
  float across=dot(q,side)-curve;across+=(noiseWorld(vec3(along*5.,across*1.5,a*13.))-.5)*.35;
  float width=(.45+1.25*a)*(.23+.77*smoothstep(-1.,.6,along));
  float envelope=exp(-1.4*across*across/(width*width))*smoothstep(-1.,-.58,along)*(1.-smoothstep(.48,1.,along));
  float fine=noiseWorld(vec3(along*5.,across*6.,a*20.)+evolution*.01)*.7+noiseWorld(vec3(along*13.,across*13.,b*20.)+evolution*.017)*.3;
  float fiber=mix(.30,pow(smoothstep(.34,.74,fine),2.),contrast);
  field+=envelope*(.05+.95*fiber);
 }
 densityNormal=vec3(0.,1.,0.);
 return min(field,1.4)*.20*cloudParameters.y*(1.-smoothstep(40.,64.,length(p-vec3(0.,camera.w,0.))));
}
float density(vec3 p,bool detail){
 if(p.y<1.1||p.y>4.95)return 0.;
 vec3 world=p+cloudOffset(p,translation,shear);world.xz+=origin.xz;
 vec4 weather=textureLod(shapeTex,vec3(world.x*.038,.271,world.z*.038),2.);
 float coverage=sat((weather.r-.58)*2.2+.18+cloudParameters.x);
 float bottom=1.15+(weather.g-.7)*.32,top=2.7+coverage*2.1;
 float h=sat((p.y-bottom)/(top-bottom));
 float profile=smoothstep(0.,.10,h)*(1.-smoothstep(.45,1.,h));
 if(profile<.001)return 0.;
 vec3 warp=vec3(0.);
 if(curlEnabled)warp=(textureLod(curlTex,world*.24+evolution*.018,1.).xyz*2.-1.)*.38*smoothstep(.05,.65,h);
 vec3 q=(world+warp)/4.8+evolution*.025;
 float lod=max(0.,log2(max(footprint,1e-5)/(4.8/128.)));
 vec4 shape=textureLod(reliefTex,q,lod);
 if(detail){densityNormal=normalize(shape.gba*2.-1.+vec3(1e-5));densityNormal=normalize(densityNormal+vec3(0.,.65*smoothstep(.55,.93,h)-.8*(1.-smoothstep(.03,.20,h)),0.));}
 float field=sat((shape.r-(.91-coverage*.34))/.22);
 field*=profile;
 if(field<=0.)return 0.;
 float cameraDistance=length(p-vec3(0.,camera.w,0.));float close=1.-smoothstep(9.,22.,cameraDistance);
 if(detail){
  float dlod=max(0.,log2(max(footprint,1e-5)/(1.05/64.)));
  vec3 fine=textureLod(detailTex,(world+warp)/1.05+evolution*.055,dlod).rgb;
  float erosion=(1.-dot(fine,vec3(.625,.25,.125)))*mix(.10,.42,smoothstep(.1,.9,h));
  field=max(0.,field-erosion);
  if(field>0.&&close>.01){
   float mlod=max(0.,dlod-.19);
   vec4 body=textureLod(bodyTex,(world+warp)/1.2+evolution*.033,mlod);
   float billow=smoothstep(.58,.82,body.r);
   field=max(0.,field-.04*(1.-billow)*close);
   densityNormal=normalize(densityNormal+normalize(body.gba*2.-1.+vec3(1e-5))*.85*close*smoothstep(.02,.20,field));
   field*=mix(1.,mix(.70,1.9,billow),close);
  }
 }
 field*=mix(1.,1.25-.25*sat(field),close);
 field*=smoothstep(2.8,6.,cameraDistance)*(1.-smoothstep(48.,64.,cameraDistance));
 if(detail)densityNormal=cloudGradient(densityNormal,shear);
 return field*mix(5.4,6.6,close)*cloudParameters.y;
}
vec3 sky(vec3 rd){
 if(physicalAtmosphere){float h=pow(sat(rd.y),.55);vec3 palette=mix(skyHorizon,skyZenith,h);if(skyFalloff>0.)palette=mix(skyZenith,skyHorizon,exp(-max(rd.y,0.)*skyFalloff));vec3 horizonRay=normalize(vec3(rd.x,max(rd.y,.012),rd.z));vec3 value=mix(skyLut(horizonRay)*6.,palette,skyOverride);value=mix(value,mix(value,skyHorizon,.45),1.-smoothstep(-.12,.015,rd.y));if(sunDisc.w>0.){float mu=dot(rd,sunDirection());value+=sunDisc.rgb*(.03*exp(18.*(mu-1.))+.16*exp(160.*(mu-1.)));}return value;}
 float h=pow(sat(rd.y),.55);
 vec3 value=mix(vec3(.46,.65,.88),vec3(.022,.145,.44),h);
 float mu=max(dot(rd,sunDirection()),0.);
 value+=vec3(.20,.17,.12)*pow(mu,18.);
 vec3 sd=sunDirection();float aspect=resolution.x/resolution.y;
 vec2 center=projectPoint(sd,resolution);
 vec2 p=(uv-center)*vec2(aspect,1.);
 value+=vec3(5.7,5.4,4.7)*(1.-smoothstep(.009,.0115,length(p)));
 return value;
}
vec3 horizonClouds(vec3 rd,vec3 background,out float transmission){
 transmission=1.;if(rd.y<-.12||rd.y>.16)return background;
 vec2 horizontal=normalize(rd.xz)*72.+translation.xz+origin.xz;
 vec4 shape=textureLod(shapeTex,vec3(horizontal.x*.16,rd.y*1.8+.37,horizontal.y*.16)+evolution*.025,2.);
 float envelope=smoothstep(-.12,-.015,rd.y)*(1.-smoothstep(.035,.13,rd.y));
 float field=sat((shape.r-(.77-cloudParameters.x*.15))/.18)*envelope;
 transmission=exp(-field*cloudParameters.y*(cloudKind==1?.10:.9));
 vec3 light=mix(skyHorizon*.65+ambientLight*vec3(.09,.11,.13)+directLight*.025,background,.40);
 return mix(light,background,transmission);
}
float fogExtinction(vec3 rd){if(fogParameters.w<=0.)return 0.;vec3 point=rd*12.+translation/3.;float bank=textureLod(detailTex,point/16.+evolution*.02,3.).r;return fogParameters.w*mix(.78,1.22,smoothstep(.48,.84,bank));}
void main(){
 vec3 rd=ray(uv+jitter/resolution,resolution),sunDir=sunDirection();
 float horizonT;vec3 background=horizonClouds(rd,sky(rd),horizonT);vec3 eye=vec3(0.,camera.w,0.);float mist=fogExtinction(rd);
 if(cloudKind==1){
  // Optically thin high cloud: analytic slab, retaining subpixel fiber filtering.
  if(rd.y<=.001){color=vec4(background,horizonT);depth=vec4(80.,horizonT,0.,1.);return;}
  float distance=(6.5-camera.w)/rd.y;vec3 p=eye+rd*distance;footprint=distance*(2.*camera.z/(resolution.x/resolution.y))/resolution.y*deformationStretch;
  float opticalDepth=cirrusDensity(p)*1.4/max(rd.y,.08),transmission=exp(-opticalDepth);
  vec3 sunE=sunTransmission(6.5)*6.*directLight;
  vec3 ambient=(skyLut(normalize(vec3(.3,.8,-.2)))+skyLut(normalize(vec3(-.5,.4,.5))))*.75*ambientLight;
  float phase=mix(hg(dot(rd,sunDir),.65),hg(dot(rd,sunDir),-.16),.35);
  vec3 light=ambient+sunE*(.18+phase*.45);light=mix(light,background,(1.-exp(-distance*.018))*.35);
  color=vec4(light*(1.-transmission)+background*transmission,transmission*horizonT);depth=vec4(distance,transmission*horizonT,0.,1.);return;
 }
 float dy=abs(rd.y)<.0001?.0001:rd.y;float entry=(1.1-camera.w)/dy,exit=(4.95-camera.w)/dy,t0=max(min(entry,exit),0.),t1=min(max(entry,exit),64.);
 if(t0>=t1){float fogT=exp(-mist*78.5);color=vec4(mix(fogParameters.rgb,background,fogT),horizonT*fogT);depth=vec4(80.,horizonT*fogT,0.,1.);return;}
 float random=fract(texture(blueNoise,(gl_FragCoord.xy+.5)/64.).r+float(frameIndex)*.61803398875);
 float fine=clamp(t0*.008,.04,.22)*(128./float(steps));
 float t=t0+fine*random,T=1.,mean=0.,weight=0.;vec3 L=vec3(0.);
 float cosT=dot(rd,sunDir);
 float ph0=mix(hg(cosT,.72),hg(cosT,-.25),.4),ph1=mix(hg(cosT,.36),hg(cosT,-.12),.5);
 float ph2=mix(hg(cosT,.18),hg(cosT,-.06),.5),ph3=hg(cosT,.08);
 vec3 sunE=(physicalAtmosphere?sunTransmission(1.)*6.:vec3(4.8,4.65,4.4))*directLight;
 vec3 ambientSkyBase=physicalAtmosphere?(skyLut(normalize(vec3(.3,.8,-.2)))+skyLut(normalize(vec3(-.5,.4,.5))))*1.5:vec3(.18,.26,.36);
 for(int i=0;i<384;i++){
  if(i>=steps*3||T<.012||t>t1)break;
  footprint=t*(2.*camera.z/(resolution.x/resolution.y))/resolution.y*deformationStretch;
  vec3 p=eye+rd*t;float sigma=density(p,true);vec3 normal=densityNormal;
  float ds=max(fine,min(t*.006,.22));
  if(sigma<.0005){t+=ds*2.5;continue;}
  ds=min(ds,max(.85/max(sigma,.001),.025));
  float span=min(5.,max((4.95-p.y)/sunDir.y,.01));
  float ls=span/63.,tau=0.,nearDensity=sigma;vec3 lp=p;
  for(int j=0;j<6;j++){
   if(j>=lightSteps)break;
   if(lightSteps==4||lightSteps==2){
    float start=lightSteps==2?(j==0?0.:.16):(j==0?0.:(j==1?.04:(j==2?.12:.32)));
    float end=lightSteps==2?(j==0?.16:1.):(j==0?.04:(j==1?.12:(j==2?.32:1.)));
    lp=p+sunDir*span*(start+end)*.5;
    if(lp.y<=4.95){float extinction=density(lp,j==0);tau+=extinction*span*(end-start);if(j==0)nearDensity=extinction;}
   }else{lp+=sunDir*ls;if(lp.y>4.95)break;tau+=density(lp,j<2)*ls;ls*=2.;}
  }
  float hf=sat((p.y-1.25)/3.55);
  float msK=1.-exp(-(tau+sigma*.12));
  float nearAmount=1.-smoothstep(5.,22.,t);
  float indirect=layeredLight?mix(1.,.68,nearAmount):1.;
  float ms=ph0*exp(-tau)+indirect*msK*(.55*ph1*exp(-tau*.5)+.30*ph2*exp(-tau*.25)+.16*ph3*exp(-tau*.125));
  float powder=1.-exp(-2.*tau-sigma*.25);
  float tauUp=sigma*(4.95-p.y)*.4;
  float diffSun=1./(1.+.11*tau),diffUp=1./(1.+.11*tauUp);
  float diffuse=diffSun*mix(.07,.40,1.-smoothstep(-.6,.3,cosT))*(1.-exp(-.15*tau));
  vec3 diffuseTint=layeredLight?mix(vec3(.93,1.,1.09),vec3(.78,.91,1.12),nearAmount):vec3(.93,1.,1.09);
  vec3 diffuseColor=dot(sunE,vec3(.2126,.7152,.0722))*diffuseTint;
  vec3 sunlight=sunE*ms*mix(1.,powder,.25*(1.-max(cosT,0.)))+diffuseColor*diffuse;
  float relief=clamp((sigma-nearDensity)/(1.+sigma)*.8,-.24,.24);
  sunlight*=1.+nearAmount*relief;
  float facing=smoothstep(-.35,.7,dot(normal,sunDir));
  sunlight*=mix(1.,mix(.55,1.35,facing),nearAmount*.85);
  vec3 ambient=ambientSkyBase*diffUp*mix(layeredLight?mix(.38,.24,nearAmount):.4,1.2,hf)+vec3(.09,.10,.11)*(1.-hf)*diffUp*.6;
  ambient*=mix(1.,.70+.30*sat(normal.y*.5+.5),nearAmount);
  ambient*=ambientLight;
  vec3 source=sunlight*(layeredLight?1.10:1.)+ambient;
  if(lightning.w>.0001){vec3 delta=p-lightning.xyz;float scatter=exp(-1.7*dot(delta,delta)/(lightningRadius*lightningRadius))*(1.-exp(-sigma*.65));source+=vec3(.34,.49,.78)*lightning.w*1.8*scatter;}
  float aerial=layeredLight?(1.-exp(-max(t-3.,0.)*.065))*.70:(1.-exp(-t*.022))*.48;
  source=mix(source,background,clamp(aerial*hazeAmount,0.,.85));
  float a=exp(-sigma*ds),w=T*(1.-a);
  L+=w*source;mean+=t*w;weight+=w;T*=a;t+=ds;
 }
 float distance=weight>.001?mean/weight:80.;vec3 radiance=L+background*T;
 if(mist>0.){float cloudTransmission=exp(-mist*max(distance-1.5,0.)),skyTransmission=exp(-mist*78.5);radiance=L*cloudTransmission+background*T*skyTransmission+fogParameters.rgb*((1.-T)*(1.-cloudTransmission)+T*(1.-skyTransmission));T*=skyTransmission;}
 T*=horizonT;color=vec4(radiance,T);depth=vec4(distance,T,0.,1.);
}
