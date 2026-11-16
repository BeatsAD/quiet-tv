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

uniform sampler2D uMultiScatter;
vec3 msLookup(float h, float muS) {
  return texture(uMultiScatter, vec2(muS * 0.5 + 0.5, clamp(h / (Rt - Rg), 0.0, 1.0))).rgb;
}
// integrate in-scattered radiance along ro + rd * [0, tmax]; returns radiance and transmittance
void integrate(vec3 ro, vec3 rd, float tmax, vec3 sun, int steps, out vec3 L, out vec3 T) {
  float c = dot(rd, sun);
  float pR = rayleighPhase(c), pM = miePhase(c, uMieG);
  L = vec3(0.0); T = vec3(1.0);
  float prev = 0.0;
  for (int s = 0; s < 64; s++) {
    if (s >= steps) break;
    // quadratic step distribution: dense near the camera
    float f = (float(s) + 0.5) / float(steps);
    float t = tmax * f * f;
    float tn = tmax * pow((float(s) + 1.0) / float(steps), 2.0);
    float dt = tn - prev;
    prev = tn;
    vec3 p = ro + rd * t;
    float h = length(p) - Rg;
    Medium m = medium(h);
    vec3 sT = sunTransmittance(p, sun);
    vec3 ms = msLookup(h, dot(normalize(p), sun));
    vec3 S = (m.scatR * pR + m.scatM * pM) * sT + (m.scatR + m.scatM) * ms;
    vec3 ext = max(m.ext, vec3(1e-7));
    vec3 sd = exp(-ext * dt);
    L += T * (S - S * sd) / ext;
    T *= sd;
  }
}

in vec2 vUv;
out vec4 o;
uniform vec3 uSun;
uniform float uCamH; // km
// direction encoding shared with lookup: x = azimuth relative to sun (0..pi), y = elevation (non-linear)
void main() {
  float az = vUv.x * PI;
  float v = vUv.y * 2.0 - 1.0;
  float el = sign(v) * v * v * PI * 0.5;
  vec3 sunH = normalize(vec3(uSun.x, 0.0, uSun.z) + vec3(1e-6, 0.0, 0.0));
  vec3 sunR = normalize(cross(vec3(0.0, 1.0, 0.0), sunH));
  vec3 hdir = sunH * cos(az) + sunR * sin(az);
  vec3 rd = hdir * cos(el) + vec3(0.0, sin(el), 0.0);
  vec3 ro = vec3(0.0, Rg + max(uCamH, 0.001), 0.0);
  float tG = raySphere(ro, rd, Rg);
  float tT = raySphere(ro, rd, Rt);
  float tmax = tG > 0.0 ? tG : tT;
  vec3 L, T;
  integrate(ro, rd, tmax, uSun, 40, L, T);
  o = vec4(L, 1.0);
}