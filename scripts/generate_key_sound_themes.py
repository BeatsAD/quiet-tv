#!/usr/bin/env python3
"""Generate original electronic and mechanical key sounds without recordings.

44.1 kHz mono PCM16 WAV. Existing wooden cues are deliberately left untouched.
The bell/drop resource filenames stay stable across the sound redesign.
"""
import math
from pathlib import Path
import random
import struct
import wave

RATE = 44100
ROOT = Path(__file__).resolve().parents[1] / 'app/src/main/res/raw'


def generate(theme, action, duration, target_rms):
    count = round(RATE * duration)
    samples = []
    noise = random.Random('quiet-key-' + action)
    low = high = 0
    for i in range(count):
        t = i / RATE
        if theme == 'bell':
            # A clearly pitched digital cue; confirmation rises and back falls.
            frequencies = {'move': (960,), 'confirm': (880, 1320), 'back': (880, 550)}[action]
            step = duration / len(frequencies)
            note = min(len(frequencies) - 1, int(t / step))
            local = t - note * step
            active = step - .008
            envelope = max(0, min(1, local / .003, (active - local) / .006))
            phase = 2 * math.pi * frequencies[note] * local
            # Rounded square-wave harmonics make this unlike a resonant bell.
            value = envelope * sum(math.sin(h * phase) / h ** 1.5 for h in (1, 3, 5, 7))
        else:
            # Two broadband transients model the key press and spring return.
            raw = noise.uniform(-1, 1)
            low += .5 * (raw - low)
            high += .045 * (low - high)
            band = low - high
            value = 0
            release = {'move': .022, 'confirm': .034, 'back': .028}[action]
            for start, strength, decay in ((.001, 1, .0035), (release, .72, .0026)):
                local = t - start
                if local >= 0:
                    attack = 1 - math.exp(-local / .00015)
                    click = 1.9 * band + .14 * math.sin(2 * math.pi * 2700 * local)
                    body = .28 * math.sin(2 * math.pi * 240 * local) * math.exp(-local / .004)
                    value += strength * attack * (click * math.exp(-local / decay) + body)
            value *= min(1, (duration - t) / .006) ** 2
        samples.append(value)
    rms = math.sqrt(sum(value * value for value in samples) / count)
    gain = min(target_rms / rms, .60 / max(abs(value) for value in samples))
    data = struct.pack('<' + 'h' * count, *(round(value * gain * 32767) for value in samples))
    path = ROOT / f'key_{theme}_{action}.wav'
    with wave.open(str(path), 'wb') as output:
        output.setparams((1, 2, RATE, 0, 'NONE', 'not compressed'))
        output.writeframes(data)
    print(f'{path.name}: {len(data) + 44} bytes, {count / RATE * 1000:.0f} ms')


if __name__ == '__main__':
    for theme, durations in (('bell', (.064, .104, .104)), ('drop', (.054, .078, .066))):
        for action, duration, rms in zip(('move', 'confirm', 'back'), durations, (.071, .096, .086)):
            generate(theme, action, duration, rms)
