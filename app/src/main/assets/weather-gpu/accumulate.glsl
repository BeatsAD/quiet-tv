#version 300 es
// Quiet TV V13 native port. Light transport/noise adapted from Open Water (MIT).
// Copyright (c) 2026 Leonxlnx. See assets/licenses/open-water-MIT.txt.

precision highp float;precision highp int;precision highp sampler3D;
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

in vec2 uv;layout(location=0) out vec4 color;layout(location=1) out vec4 depth;
uniform sampler2D currentColor;uniform sampler2D currentDepth;uniform sampler2D previousColor;uniform sampler2D previousDepth;
uniform vec2 resolution;uniform vec2 jitter;uniform vec2 previousJitter;uniform vec3 deltaTranslation;uniform vec3 deltaShear;uniform bool valid;uniform bool edgeReconstruction;uniform float reactive;uniform bool compactKernel;
// Separable Catmull-Rom: combine the two positive inner weights with bilinear
// filtering, preserving the cubic footprint in nine reads instead of sixteen.
vec4 reconstruct(vec2 pos){
 if(!edgeReconstruction)return texture(currentColor,pos);
 vec2 size=vec2(textureSize(currentColor,0)),p=pos*size-.5,base=floor(p),f=fract(p);
 vec2 f2=f*f,f3=f2*f;
 vec2 w0=-.5*f+f2-.5*f3,w1=1.-2.5*f2+1.5*f3,w2=.5*f+2.*f2-1.5*f3,w3=-.5*f2+.5*f3;
 vec2 w12=w1+w2,inner=base+w2/w12;
 vec3 px=vec3(base.x-1.,inner.x,base.x+2.)+.5,py=vec3(base.y-1.,inner.y,base.y+2.)+.5;
 vec3 wx=vec3(w0.x,w12.x,w3.x),wy=vec3(w0.y,w12.y,w3.y);
 vec4 sum=vec4(0.),low=vec4(1e6),high=vec4(-1e6);
 for(int x=0;x<3;x++)for(int y=0;y<3;y++){
  vec4 n=texture(currentColor,vec2(px[x],py[y])/size);sum+=n*wx[x]*wy[y];low=min(low,n);high=max(high,n);
 }
 return clamp(sum,max(low,vec4(0.)),high);
}
uniform vec4 previousCamera;uniform vec2 previousHeading;
vec2 projectPrevious(vec3 world,vec2 size){vec3 p=vec3(previousHeading.y*world.x-previousHeading.x*world.z,world.y,previousHeading.x*world.x+previousHeading.y*world.z);float f=p.y*previousCamera.x+p.z*previousCamera.y;if(f<=.001)return vec2(-10.);return .5+.5*vec2(p.x/(f*previousCamera.z),(p.y*previousCamera.y-p.z*previousCamera.x)/(f*previousCamera.z/(size.x/size.y)));}
// Sky has a sentinel distance, not a surface behind the translucent cloud.
// Weight depth by coverage so upsampling cannot invent a cloud/sky midpoint.
vec2 depthGuide(sampler2D source,vec2 pos,float transmission){
 if(transmission>.999)return vec2(80.,transmission);
 ivec2 size=textureSize(source,0);vec2 p=pos*vec2(size)-.5,f=fract(p);ivec2 base=ivec2(floor(p));
 float sum=0.,weight=0.;
 for(int x=0;x<2;x++)for(int y=0;y<2;y++){
  vec2 tap=texelFetch(source,clamp(base+ivec2(x,y),ivec2(0),size-1),0).rg;
  float w=(x==0?1.-f.x:f.x)*(y==0?1.-f.y:f.y)*max(0.,1.-tap.y);
  sum+=tap.x*w;weight+=w;
 }
 return vec2(weight>.0001?sum/weight:80.,transmission);
}
void main(){
 vec2 inputSize=vec2(textureSize(currentColor,0));vec2 sampleUv=uv-jitter/inputSize;
 vec4 current=reconstruct(sampleUv);vec2 d=depthGuide(currentDepth,sampleUv,current.a);depth=vec4(d,0.,1.);
 if(!valid){color=current;return;}
 vec3 eye=vec3(0.,camera.w,0.),p=eye+ray(uv,resolution)*d.x;
 p+=cloudOffset(p,deltaTranslation,deltaShear);
 vec3 previousEye=vec3(0.,previousCamera.w,0.);vec2 old=d.y>.999?uv:projectPrevious(p-previousEye,resolution);
 if(any(lessThan(old,vec2(0.)))||any(greaterThan(old,vec2(1.)))){color=current;return;}
 vec4 past=texture(previousColor,old);vec2 pd=depthGuide(previousDepth,old,past.a);
 vec3 low=current.rgb,high=current.rgb;vec3 sum=current.rgb,sq=current.rgb*current.rgb;float taps=1.;
 float lowT=current.a,highT=current.a;
 for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++){if(compactKernel&&abs(x)+abs(y)>1)continue;taps+=1.;vec4 n=texture(currentColor,sampleUv+vec2(x,y)/inputSize);low=min(low,n.rgb);high=max(high,n.rgb);sum+=n.rgb;sq+=n.rgb*n.rgb;lowT=min(lowT,n.a);highT=max(highT,n.a);}
 vec3 mean=sum/taps,deviation=sqrt(max(sq/taps-mean*mean,vec3(0.)));past.rgb=clamp(past.rgb,max(low-.01,mean-deviation*1.6),min(high+.01,mean+deviation*1.6));
 past.a=clamp(past.a,lowT,highT);
 float depthError=abs(pd.x-length(p-previousEye))/max(.18,d.x*.035);
 float agreement=(1.-smoothstep(.75,2.,depthError))*(1.-smoothstep(.025,.18,abs(pd.y-d.y)));
 float confidence=mix(.12,.92,agreement);
 float movement=length(deltaTranslation)+length(deltaShear)*.5;
 float moving=smoothstep(.0001,.006,movement);
 confidence*=mix(1.,mix(.82,1.,smoothstep(6.,24.,d.x)),moving);
 confidence*=1.-smoothstep(.05,.3,movement);
 confidence*=1.-reactive;
 color=mix(current,past,confidence);
}
