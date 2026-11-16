#!/usr/bin/env python3
"""Bake original periodic cloud data and an artistic moon albedo. Python standard library only."""
import hashlib
import json
import math
from pathlib import Path
import random

SIZE = 256
SEED = 74719
OUT = Path(__file__).resolve().parents[1] / 'app/src/main/assets/weather-gpu'


def periodic(delta):
    return (delta + .5) % 1 - .5


def bake_cloud(kind):
    rng = random.Random(SEED + kind)
    puffs = []
    for _ in range(12):
        x, z, r = rng.random(), rng.random(), rng.uniform(.07, .14)
        for _ in range(7):
            puffs.append(((x+rng.uniform(-r, r)) % 1, (z+rng.uniform(-r, r)) % 1,
                          r*rng.uniform(.40, .90), r*rng.uniform(.40, .90), rng.uniform(.45, 1)))
    harmonics = [(rng.randint(1, 10), rng.randint(1, 10), rng.random()*math.tau, 1/(i+1)**1.35) for i in range(12)]
    fine = [(rng.randint(12, 31), rng.randint(12, 31), rng.random()*math.tau, 1/(i+1)**1.2) for i in range(8)]
    height = []
    for y in range(SIZE):
        for x in range(SIZE):
            u, v = x/SIZE, y/SIZE
            if kind == 0:
                h = 0
                for px, pz, rx, rz, amp in puffs:
                    d = 1-(periodic(u-px)/rx)**2-(periodic(v-pz)/rz)**2
                    if d > 0:
                        dome = amp*math.sqrt(d)
                        # Smooth union leaves small lobes on a coherent cumulus body.
                        k = max(.10-abs(dome-h), 0)/.10
                        h = max(dome, h)+k*k*.025
            else:
                h = .62 + .18*sum(math.sin(math.tau*(a*u+b*v)+p)*w for a,b,p,w in harmonics)
            detail = sum(math.sin(math.tau*(a*u+b*v)+p)*w for a,b,p,w in fine)
            h += (.035 if kind == 0 else .060)*detail
            height.append(max(0, min(1, h)))
    data = bytearray()
    for y in range(SIZE):
        for x in range(SIZE):
            h = height[y*SIZE+x]
            dx = (height[y*SIZE+(x+1) % SIZE]-height[y*SIZE+(x-1) % SIZE])*SIZE/16
            dz = (height[((y+1) % SIZE)*SIZE+x]-height[((y-1) % SIZE)*SIZE+x])*SIZE/16
            n = math.sqrt(dx*dx+dz*dz+1)
            data.extend((round(h*255), round((.5-dx/n*.5)*255), round((.5-dz/n*.5)*255), round(h*255)))
    return bytes(data)


def bake_moon():
    rng = random.Random(SEED+991)
    craters = [(rng.uniform(-1, 1), rng.uniform(-1, 1), rng.uniform(.015, .13)) for _ in range(90)]
    maria = [(-.28, .32, .40, .28), (.16, .42, .28, .31), (-.48, -.10, .24, .35), (.36, -.05, .27, .20)]
    data = bytearray()
    for y in range(SIZE):
        for x in range(SIZE):
            u, v = x/(SIZE-1)*2-1, y/(SIZE-1)*2-1
            albedo = .82+.035*math.sin(u*191+math.cos(v*133))+.025*math.cos(v*79+u*41)
            for cx,cy,rx,ry in maria:
                albedo -= .16*math.exp(-(((u-cx)/rx)**2+((v-cy)/ry)**2)*1.8)
            for cx,cy,r in craters:
                d = math.hypot(u-cx, v-cy)/r
                if d < 1.3:
                    albedo += .08*math.exp(-((d-1)/.10)**2)-.07*math.exp(-d*d*3)
            n = round(max(0, min(1, albedo))*255)
            data.extend((n,n,n,255))
    return bytes(data)


def bake_volume():
    """A small original cumulus density/normal volume, periodic only horizontally."""
    side = 96
    rng = random.Random(SEED+1729)
    puffs = []
    for _ in range(10):
        cx, cz, radius = rng.random(), rng.random(), rng.uniform(.07, .14)
        for _ in range(7):
            puffs.append(((cx+rng.uniform(-radius, radius)) % 1, rng.uniform(.34,.63),
                          (cz+rng.uniform(-radius, radius)) % 1,
                          radius*rng.uniform(.45,1), rng.uniform(.12,.28), radius*rng.uniform(.45,1)))
    harmonics = [(rng.randint(3, 13), rng.randint(2, 9), rng.randint(3, 13), rng.random()*math.tau, 1/(i+1)**1.2) for i in range(12)]
    density = []
    for z in range(side):
        for y in range(side):
            for x in range(side):
                u,v,w = x/side,y/(side-1),z/side
                h = 0
                for px,py,pz,rx,ry,rz in puffs:
                    d = 1-(periodic(u-px)/rx)**2-((v-py)/ry)**2-(periodic(w-pz)/rz)**2
                    if d > 0:
                        k = max(.16-abs(d-h),0)/.16
                        h = max(d,h)+k*k*.04
                # Bake 3D erosion before differentiating normals. No extra runtime volume sample.
                if h > 0:
                    detail = sum(math.sin(math.tau*(a*u+b*v+c*w)+p)*weight for a,b,c,p,weight in harmonics)
                    h -= .12*(1+detail*.65)
                density.append(max(0,min(1,h)))
    def at(x,y,z):
        return density[((z % side)*side+max(0,min(side-1,y)))*side+(x % side)]
    data = bytearray()
    for z in range(side):
        for y in range(side):
            for x in range(side):
                dx=(at(x+1,y,z)-at(x-1,y,z))*side/20
                dy=(at(x,y+1,z)-at(x,y-1,z))*side/4.8
                dz=(at(x,y,z+1)-at(x,y,z-1))*side/20
                length=math.sqrt(dx*dx+dy*dy+dz*dz)
                normal=(0,1,0) if length<1e-8 else (-dx/length,-dy/length,-dz/length)
                data.extend((round(at(x,y,z)*255), *(round((n*.5+.5)*255) for n in normal)))
    return bytes(data)


def main():
    manifest = {'generator': 'scripts/generate_weather_materials.py', 'seed': SEED, 'size': SIZE,
                'license': 'CC0-1.0; original Quiet TV mathematical data, no third-party images',
                'cloud_channels': 'linear R thickness, G normal X, B normal Z, A relief; periodic in both axes',
                'moon': 'original artistic albedo, not a geographic lunar surface map', 'assets': {}}
    manifest['volume_channels'] = '96 cubed; linear R density, GBA surface normal XYZ; original ellipsoid unions with periodic 3D erosion'
    for name, data in [('cumulus-material.rgba', bake_cloud(0)), ('stratus-material.rgba', bake_cloud(1)), ('cumulus-volume.rgba', bake_volume()), ('moon-albedo.rgba', bake_moon())]:
        (OUT/name).write_bytes(data)
        manifest['assets'][name] = {'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
    (OUT/'materials.json').write_text(json.dumps(manifest, indent=2)+'\n')
    print(json.dumps(manifest, indent=2))


if __name__ == '__main__':
    main()
