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

in vec2 vUv;
out vec4 o;
void main() {
  float H = sqrt(Rt * Rt - Rg * Rg);
  float rho = H * vUv.y;
  float r = sqrt(rho * rho + Rg * Rg);
  float dmin = Rt - r, dmax = rho + H;
  float d = dmin + vUv.x * (dmax - dmin);
  float mu = d == 0.0 ? 1.0 : clamp((H * H - rho * rho - d * d) / (2.0 * r * d), -1.0, 1.0);
  vec3 ro = vec3(0.0, r, 0.0);
  vec3 rd = vec3(sqrt(1.0 - mu * mu), mu, 0.0);
  float tmax = raySphere(ro, rd, Rt);
  const int STEPS = 48;
  vec3 tau = vec3(0.0);
  float dt = tmax / float(STEPS);
  for (int i = 0; i < STEPS; i++) {
    vec3 p = ro + rd * (float(i) + 0.5) * dt;
    tau += medium(length(p) - Rg).ext * dt;
  }
  o = vec4(exp(-tau), 1.0);
}