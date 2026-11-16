#version 300 es
// Near density integration. Fine distant artwork is composed by present.glsl.
precision highp float;precision highp int;
precision highp sampler3D;
in vec2 uv;
layout(location=0) out vec4 color;
layout(location=1) out vec4 depth;
uniform vec4 camera;uniform vec2 cameraHeading,cloudHeading;
uniform vec2 resolution,cloudParameters,jitter,cloudLayer;
uniform vec3 translation,shear,sharedSun,solarDirection,skyZenith,skyHorizon,directLight,ambientLight;
uniform vec4 fogParameters,lightning;
uniform float lightningRadius,hazeAmount,nightAmount,sunGlow,cloudStructure;
uniform sampler3D cloudVolume;
uniform int steps,frameIndex;
uniform sampler2D blueNoise;
vec3 offsetAt(vec3 p);
vec4 volume(vec3 p){
 float height=(p.y-cloudLayer.x)/cloudLayer.y;if(height<=0.||height>=1.)return vec4(0.,1.,0.,0.);
 vec3 shifted=p+offsetAt(p);return textureLod(cloudVolume,vec3(shifted.x*.047,height,shifted.z*.047),0.);
}
float density(vec4 data,float cover){return max(0.,data.r-(.22-.17*cover))*cloudParameters.y*2.;}
vec3 offsetAt(vec3 p){float h=clamp((p.y-cloudLayer.x)/cloudLayer.y,0.,1.);return translation+(h-.5)*shear;}

void main(){
 vec2 q=(uv+jitter/resolution)*2.-1.;float tanV=camera.z*resolution.y/resolution.x;
 vec3 local=vec3(q.x*camera.z,camera.x+q.y*camera.y*tanV,camera.y-q.y*camera.x*tanV);
 vec3 rd=normalize(vec3(cameraHeading.y*local.x+cameraHeading.x*local.z,local.y,-cameraHeading.x*local.x+cameraHeading.y*local.z));
 vec3 eye=vec3(0.,camera.w,0.);
 vec3 sun=normalize(sharedSun),solar=normalize(solarDirection);
 vec3 background=mix(skyHorizon,skyZenith,pow(clamp(uv.y,0.,1.),.7));
 float light=max(dot(rd,solar),0.);background+=vec3(1.,.92,.78)*sunGlow*(1.-nightAmount)*(.18*pow(light,28.)+.75*pow(light,700.));
 float cover=clamp((cloudParameters.x+.45)/.75,0.,1.);
 float T=1.,moment=0.,weightSum=0.;vec3 value=vec3(0.);
 // Only the near blanket/rain layer integrates density. Separated banks
 // are filtered and lit at output resolution by present.glsl.
 float nearGain=smoothstep(.30,.80,cover);
 if(cloudStructure>.5&&rd.y>.001&&nearGain>.001){
  float start=max(0.,(cloudLayer.x-eye.y)/rd.y),end=min(24.,(cloudLayer.x+cloudLayer.y-eye.y)/rd.y);
  int count=steps>=100?32:steps<=32?12:20;float ds=max(0.,end-start)/float(count);
  float t=start+ds*.5;
  for(int i=0;i<32;i++){
   if(i>=count||start>=end||T<.015)break;
   vec3 p=eye+rd*t;vec4 data=volume(p);float sigma=density(data,cover)*nearGain;
   if(sigma>.001){
    float shadow=0.;
    for(int j=0;j<4;j++){if(count<=12&&j>=2)break;float length=.45+float(j)*.85;shadow+=density(volume(p+sun*length),cover)*.75;}
    float visibility=exp(-shadow),rim=pow(max(dot(rd,sun),0.),8.);
    vec3 source=ambientLight*(.07+.20*sqrt(data.g))+directLight*visibility*(.25+.38*rim);
    float flash=lightning.w*exp(-dot(p-lightning.xyz,p-lightning.xyz)/max(.1,lightningRadius*lightningRadius));source+=vec3(.50,.64,1.)*flash;
    source=mix(source,background,clamp((1.-exp(-t*.009))*hazeAmount,0.,.6));
    float alpha=1.-exp(-sigma*ds),weight=T*alpha;value+=source*weight;moment+=t*weight;weightSum+=weight;T*=1.-alpha;
   }
   t+=ds;
  }
 }
 float distance=weightSum>.001?moment/weightSum:80.;float mist=exp(-fogParameters.w*max(0.,distance-1.5));
 value=mix(fogParameters.rgb,value,mist);T*=mist;
 color=vec4(value,T);depth=vec4(distance,T,0.,1.);
}
