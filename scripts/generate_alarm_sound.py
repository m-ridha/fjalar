#!/usr/bin/env python3
"""
Generate the dedicated bundled wake-up alarm sound for Fjalar (Wake Me at).

Specifications (PRD 7.4 & Invariants):
- Format: 44.1 kHz, 16-bit PCM WAV (stereo).
- Duration: 3.6 seconds.
- Acoustic Design:
  - Transit chime aesthetic: Harmonic bell chords with gentle crescendo.
  - Frequency progression: F#5 (740 Hz) -> A#5 (932 Hz) -> C#6 (1109 Hz) -> F#6 (1480 Hz).
  - Loop-friendly: Zero DC offset, smooth exponential decay to zero amplitude.
- Destinations:
  - assets/audio/alarm.wav (master repository asset)
  - iosApp/src/alarm.wav (bundled into iOS NSBundle.mainBundle)
  - androidApp/res/raw/alarm.wav (bundled into Android resources)
"""

import math
import os
import struct
import wave

SAMPLE_RATE = 44100
DURATION = 3.6  # seconds
NUM_SAMPLES = int(SAMPLE_RATE * DURATION)


def generate_chime_pcm() -> bytes:
    # Harmonic bell notes: F#5 (739.99 Hz), A#5 (932.33 Hz), C#6 (1108.73 Hz), F#6 (1479.98 Hz)
    notes = [
        # (start_time_sec, base_freq_hz, amplitude, pan_left, pan_right)
        (0.0, 739.99, 0.50, 0.70, 0.40),  # F#5: Gentle wake-up start
        (0.4, 932.33, 0.65, 0.60, 0.60),  # A#5: Expanding harmonic
        (0.8, 1108.73, 0.80, 0.40, 0.70),  # C#6: Bright approach
        (1.2, 1479.98, 0.95, 0.50, 0.80),  # F#6: Clear arrival apex
    ]

    left_channel = [0.0] * NUM_SAMPLES
    right_channel = [0.0] * NUM_SAMPLES

    # Bell partials (fundamental, octave, minor third/tierce, strike transient)
    partials = [
        (1.00, 1.00, 1.2),  # Fundamental
        (2.00, 0.45, 0.9),  # 2nd harmonic
        (2.76, 0.25, 0.6),  # Inharmonic bell partial
        (4.07, 0.15, 0.4),  # Strike transient
    ]

    for start_t, base_freq, amp, pan_l, pan_r in notes:
        start_idx = int(start_t * SAMPLE_RATE)
        for i in range(start_idx, NUM_SAMPLES):
            t = (i - start_idx) / SAMPLE_RATE
            # Attack envelope (15 ms smooth ramp to prevent speaker pop)
            attack = min(1.0, t / 0.015)

            sample_val = 0.0
            for mult, p_amp, decay_rate in partials:
                freq = base_freq * mult
                decay = math.exp(-t * decay_rate)
                sample_val += math.sin(2.0 * math.pi * freq * t) * p_amp * decay

            val = sample_val * amp * attack
            left_channel[i] += val * pan_l
            right_channel[i] += val * pan_r

    # Clean exponential fade-out over last 150ms for seamless looping
    fade_len = int(0.15 * SAMPLE_RATE)
    for i in range(NUM_SAMPLES - fade_len, NUM_SAMPLES):
        fade = (NUM_SAMPLES - 1 - i) / fade_len
        left_channel[i] *= fade
        right_channel[i] *= fade

    # Normalize to -1.5 dB peak (approx 0.84 max amplitude) to eliminate digital clipping
    max_peak = max(max(abs(x) for x in left_channel), max(abs(x) for x in right_channel))
    scale = (32767.0 * 0.84) / max_peak

    pcm_data = bytearray()
    for l, r in zip(left_channel, right_channel):
        l_int = int(max(-32768, min(32767, l * scale)))
        r_int = int(max(-32768, min(32767, r * scale)))
        pcm_data.extend(struct.pack('<hh', l_int, r_int))

    return bytes(pcm_data)


def write_wav(output_path: str, pcm_data: bytes):
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with wave.open(output_path, 'wb') as wav_file:
        wav_file.setnchannels(2)
        wav_file.setsampwidth(2)  # 16-bit
        wav_file.setframerate(SAMPLE_RATE)
        wav_file.writeframes(pcm_data)
    print(f'Wrote {output_path} ({len(pcm_data)} bytes, {DURATION}s)')


def main():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    pcm_data = generate_chime_pcm()

    destinations = [
        os.path.join(repo_root, 'assets', 'audio', 'alarm.wav'),
        os.path.join(repo_root, 'iosApp', 'src', 'alarm.wav'),
        os.path.join(repo_root, 'androidApp', 'res', 'raw', 'alarm.wav'),
    ]

    for dest in destinations:
        write_wav(dest, pcm_data)


if __name__ == '__main__':
    main()
