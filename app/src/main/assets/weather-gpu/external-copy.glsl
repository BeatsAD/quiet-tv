#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;
uniform samplerExternalOES source;
uniform mat4 textureTransform;
uniform mat3 imageTransform;
in vec2 uv;
out vec4 color;
void main(){
 vec3 imagePoint=imageTransform*vec3(uv.x,1.-uv.y,1.);
 vec2 p=imagePoint.xy/imagePoint.z;
 color=vec4(texture(source,(textureTransform*vec4(p.x,1.-p.y,0.,1.)).xy).rgb,1.);
}
