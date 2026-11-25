#!/usr/bin/env python3
"""
Unit tests for the bundled alarm audio assets in Fjalar.
Verifies WAV file integrity, 22.05 kHz 16-bit PCM mono format, non-clipping peak, and duration.
"""

import os
import unittest
import wave


class GeneratedAlarmAudioTest(unittest.TestCase):

    def setUp(self):
        self.repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        self.audio_paths = [
            os.path.join(self.repo_root, "assets", "audio", "alarm.wav"),
            os.path.join(self.repo_root, "iosApp", "src", "alarm.wav"),
            os.path.join(self.repo_root, "androidApp", "res", "raw", "alarm.wav"),
        ]

    def test_audio_files_exist_and_match(self):
        master_path = self.audio_paths[0]
        self.assertTrue(os.path.exists(master_path), f"Master audio asset missing: {master_path}")

        with open(master_path, "rb") as f:
            master_bytes = f.read()

        for path in self.audio_paths[1:]:
            self.assertTrue(os.path.exists(path), f"Bundled audio asset missing: {path}")
            with open(path, "rb") as f:
                dest_bytes = f.read()
            self.assertEqual(
                master_bytes,
                dest_bytes,
                f"Audio asset at {path} does not match master {master_path}",
            )

    def test_wav_audio_format_and_duration(self):
        for path in self.audio_paths:
            with wave.open(path, "rb") as w:
                channels = w.getnchannels()
                sampwidth = w.getsampwidth()
                framerate = w.getframerate()
                nframes = w.getnframes()
                duration = nframes / framerate

                self.assertEqual(channels, 1, f"Expected mono (1 channel) for {path}")
                self.assertEqual(sampwidth, 2, f"Expected 16-bit PCM (2 bytes/sample) for {path}")
                self.assertEqual(framerate, 22050, f"Expected 22.05 kHz sample rate for {path}")
                self.assertGreaterEqual(duration, 3.0, f"Audio too short (<3.0s): {duration}s")
                self.assertLessEqual(duration, 5.0, f"Audio too long (>5.0s): {duration}s")

    def test_android_keep_xml_retains_alarm_raw(self) -> None:
        from pathlib import Path
        import xml.etree.ElementTree as ET

        keep_path = Path(__file__).resolve().parent.parent / "androidApp" / "res" / "raw" / "keep.xml"
        self.assertTrue(keep_path.is_file(), f"Missing keep.xml: {keep_path}")
        root = ET.parse(keep_path).getroot()
        keep_attr = root.attrib.get("{http://schemas.android.com/tools}keep", "")
        keep_entries = {item.strip() for item in keep_attr.split(",") if item.strip()}
        self.assertIn("@raw/alarm", keep_entries)


if __name__ == "__main__":
    unittest.main()
