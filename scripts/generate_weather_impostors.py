#!/usr/bin/env python3
"""Pack original cloud sprites into linear relightable RGBA data.

NumPy and Pillow are offline authoring tools. APK builds use checked-in data.
Normals and occlusion are artistic relief proxies, not measured cloud geometry.
"""
import hashlib
import json
from pathlib import Path
import numpy as np
from PIL import Image, ImageOps, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'scripts/weather-materials/cloud-source.png'
OUT = ROOT / 'app/src/main/assets/weather-gpu'
WIDTH, HEIGHT, PADDING = 512, 256, 16

def main():
    source = Image.open(SOURCE).convert('RGBA')
    atlas = np.zeros((HEIGHT*2, WIDTH*2, 4), dtype=np.uint8)
    for index in range(4):
        col, row = index % 2, index // 2
        cell = source.crop((col*source.width//2, row*source.height//2,
                            (col+1)*source.width//2, (row+1)*source.height//2))
        # Encode continuous coverage from the black radiance master; isolate mip levels.
        cell = ImageOps.contain(cell, (WIDTH-2*PADDING, HEIGHT-2*PADDING), Image.Resampling.LANCZOS)
        padded = Image.new('RGBA', (WIDTH, HEIGHT))
        padded.paste(cell, ((WIDTH-cell.width)//2, (HEIGHT-cell.height)//2))
        rgba = np.asarray(padded).astype(np.float32)/255
        radiance = rgba[:, :, :3].mean(axis=2)
        coverage = np.clip((radiance-.012)/.24, 0, 1)
        coverage = coverage*coverage*(3-2*coverage)
        shade = np.clip(np.power(radiance, 2.2)/np.maximum(coverage, .01), .12, 1)
        relief = np.asarray(Image.fromarray(np.rint(shade*255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(3))).astype(np.float32)/255
        dy, dx = np.gradient(relief)
        length = np.sqrt(1+(dx*16)**2+(dy*16)**2)
        data = np.stack([coverage, (.5-dx*8/length)*coverage, (.5+dy*8/length)*coverage, shade*coverage], axis=-1)
        data[coverage < .001] = 0
        # PNG rows run downwards; each OpenGL atlas cell starts at its bottom.
        atlas[row*HEIGHT:(row+1)*HEIGHT, col*WIDTH:(col+1)*WIDTH] = np.rint(np.clip(data[::-1], 0, 1)*255).astype(np.uint8)
    data = b"".join(atlas[(i//2)*HEIGHT:(i//2+1)*HEIGHT, (i%2)*WIDTH:(i%2+1)*WIDTH].copy().tobytes() for i in range(4))
    (OUT/'cloud-impostors.rgba').write_bytes(data)
    # Reuse the original authored 96^3 density as a smaller near-cloud field.
    volume_source = OUT/'cumulus-volume.rgba'
    density = np.frombuffer(volume_source.read_bytes(), np.uint8).reshape(96, 96, 96, 4)[:, :, :, 0].astype(np.float32)/255
    coord = (np.arange(64)+.5)*96/64-.5
    lo = np.floor(coord).astype(int); fraction = (coord-lo).astype(np.float32)
    for axis in range(3):
        shape = [1, 1, 1]; shape[axis] = 64
        density = np.take(density, lo, axis=axis)*(1-fraction.reshape(shape))+np.take(density, lo+1, axis=axis)*fraction.reshape(shape)
    base = np.clip((np.arange(64)/63-.18)/.10,0,1).astype(np.float32)
    density *= (base*base*(3-2*base))[None,:,None]
    above = np.cumsum(density[:, ::-1, :], axis=1)[:, ::-1, :]*(4.8/64)
    near = np.stack([density, np.exp(-above*.8)], axis=-1)
    near_data = np.rint(np.clip(near, 0, 1)*255).astype(np.uint8).tobytes()
    (OUT/'cloud-blanket.rg8').write_bytes(near_data)
    manifest = {'encoding':'relief-weighted', 'uvRects':[[0,0,1,1]]*4, 'aspectRatios':[WIDTH/HEIGHT]*4, 'generator': 'scripts/generate_weather_impostors.py',
                'source': 'scripts/weather-materials/cloud-source.png',
                'sourceSha256': hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
                'sourceEncoding': 'opaque black radiance reference; continuous coverage encoded offline',
                'array': [WIDTH, HEIGHT, 4], 'bankGroups':{'broken':[0,1],'blanket':[2],'wet':[3]}, 'cells': 4,
                'padding': PADDING, 'maximumMip': 9, 'cellRows': 'each array layer bottom to top',
                'channels': 'R continuous coverage from black radiance; GBA coverage-weighted relief XY and diffuse visibility',
                'license': 'CC0-1.0; original Quiet TV AI-assisted cloud artwork',
                'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest(),
                'nearVolume': {'size':64, 'components':2, 'format':'RG8', 'channels':'RG blanket density/ambient',
                               'sourceSha256':hashlib.sha256(volume_source.read_bytes()).hexdigest(),
                               'bytes':len(near_data), 'sha256':hashlib.sha256(near_data).hexdigest()}}
    (OUT/'impostors.json').write_text(json.dumps(manifest, indent=2)+'\n')
    print(json.dumps(manifest, indent=2))


if __name__ == '__main__':
    main()
