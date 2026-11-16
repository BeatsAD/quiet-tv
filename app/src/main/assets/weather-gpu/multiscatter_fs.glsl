#version 300 es
// Quiet TV V13 native port. Light transport/noise adapted from Open Water (MIT).
// Copyright (c) 2026 Leonxlnx. See assets/licenses/open-water-MIT.txt.
precision highp float;precision highp int;precision highp sampler2D;precision highp sampler3D;

const float PI = 3.14159265359;
const float Rg = 6360.0;
const float Rt = 6460.0;
uniform vec3 uRayleigh;   // scattering at sea level (1/km)
uniform vec2 uMie;        // scattering, extinction at sea level (1/km)
uniform float uMieH;      // scale height of aerosols (km)
uniform vec3 uOzone;      // absorption (1/km)
uniform float uMieG;

struct Medium { vec3 scatR; float scatM; vec3 ext; };
Medium medium(float h) {
  h = max(h, 0.0);
  float dR = exp(-h / 8.0);
  float dM = exp(-h / uMieH);
  float dO = max(0.0, 1.0 - abs(h - 25.0) / 15.0);
  Medium m;
  m.scatR = uRayleigh * dR;
  m.scatM = uMie.x * dM;
  m.ext = m.scatR + vec3(uMie.y * dM) + uOzone * dO;
  return m;
}

// distance to sphere of radius R from ro (origin-centred), -1 if none
float raySphere(vec3 ro, vec3 rd, float R) {
  float b = dot(ro, rd);
  float c = dot(ro, ro) - R * R;
  float d = b * b - c;
  if (d < 0.0) return -1.0;
  d = sqrt(d);
  float t0 = -b - d, t1 = -b + d;
  if (t0 > 0.0) return t0;
  if (t1 > 0.0) return t1;
  return -1.0;
}

float rayleighPhase(float c) { return 3.0 / (16.0 * PI) * (1.0 + c * c); }
float hgPhase(float c, float g) { float g2 = g * g; return (1.0 - g2) / (4.0 * PI * pow(max(1.0 + g2 - 2.0 * g * c, 1e-4), 1.5)); }
float miePhase(float c, float g) {
  // maritime aerosol: a sharp forward lobe (a compact aureole) and a little backscatter
  return 0.45 * hgPhase(c, 0.95) + 0.47 * hgPhase(c, 0.65) + 0.08 * hgPhase(c, -0.3);
}

vec2 transmittanceUv(float r, float mu) {
  float H = sqrt(Rt * Rt - Rg * Rg);
  float rho = sqrt(max(0.0, r * r - Rg * Rg));
  float disc = r * r * (mu * mu - 1.0) + Rt * Rt;
  float d = max(0.0, -r * mu + sqrt(max(disc, 0.0)));
  float dmin = Rt - r, dmax = rho + H;
  return vec2((d - dmin) / (dmax - dmin), rho / H);
}

uniform sampler2D uTransmittance;
vec3 sunTransmittance(vec3 p, vec3 sun) {
  float r = length(p);
  float mu = dot(p / r, sun);
  // below the horizon of the planet: no direct sun
  float sinH = Rg / r;
  float cosH = -sqrt(max(0.0, 1.0 - sinH * sinH));
  float fade = smoothstep(cosH - 0.01, cosH + 0.01, mu);
  return texture(uTransmittance, transmittanceUv(r, mu)).rgb * fade;
}

in vec2 vUv;
out vec4 o;
uniform float uGroundAlbedo;
void main() {
  float muS = vUv.x * 2.0 - 1.0;
  float h = mix(0.0, Rt - Rg, vUv.y);
  vec3 ro = vec3(0.0, Rg + h + 0.01, 0.0);
  vec3 sun = vec3(sqrt(max(0.0, 1.0 - muS * muS)), muS, 0.0);
  const int SQ = 8;
  vec3 L2 = vec3(0.0), fms = vec3(0.0);
  for (int i = 0; i < SQ; i++) for (int j = 0; j < SQ; j++) {
    float th = PI * (float(i) + 0.5) / float(SQ);
    float ph = acos(1.0 - 2.0 * (float(j) + 0.5) / float(SQ));
    vec3 rd = vec3(cos(th) * sin(ph), cos(ph), sin(th) * sin(ph));
    float tG = raySphere(ro, rd, Rg);
    float tT = raySphere(ro, rd, Rt);
    float tmax = tG > 0.0 ? tG : tT;
    const int STEPS = 20;
    float dt = tmax / float(STEPS);
    vec3 T = vec3(1.0), L = vec3(0.0), F = vec3(0.0);
    for (int s = 0; s < STEPS; s++) {
      vec3 p = ro + rd * (float(s) + 0.5) * dt;
      Medium m = medium(length(p) - Rg);
      vec3 sT = sunTransmittance(p, sun);
      vec3 scat = m.scatR + m.scatM;
      vec3 S = scat * sT / (4.0 * PI);
      vec3 ext = max(m.ext, vec3(1e-7));
      vec3 sd = exp(-ext * dt);
      L += T * (S - S * sd) / ext;
      F += T * (scat - scat * sd) / ext;
      T *= sd;
    }
    if (tG > 0.0) {
      vec3 p = ro + rd * tG;
      vec3 n = normalize(p);
      L += T * sunTransmittance(p, sun) * max(dot(n, sun), 0.0) * uGroundAlbedo / PI;
    }
    L2 += L; fms += F;
  }
  L2 /= float(SQ * SQ);
  fms /= float(SQ * SQ);
  o = vec4(L2 / (1.0 - fms), 1.0);
}