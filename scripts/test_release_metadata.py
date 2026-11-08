#!/usr/bin/env python3
"""Regression coverage for release event inputs and platform version mapping."""

from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from release_metadata import configure_android, release_metadata


class ReleaseMetadataTest(unittest.TestCase):
    def test_stable_and_suffixed_versions(self):
        for tag in ["v0.1.0", "v0.1.0-debug", "v0.3.0", "v0.3.0-rc.1", "v1.2.3-rc.1", "v1.2.3-test-build"]:
            with self.subTest(tag=tag):
                values = release_metadata(tag, 42)
                self.assertEqual(values["tag"], tag)
                self.assertEqual(values["android_version"], tag[1:])
                self.assertEqual(values["version_code"], "42")
                self.assertEqual(values["ios_version"], tag[1:].split("-")[0])
                self.assertEqual(values["ios_build_number"], "42")

    def test_invalid_tags_cannot_enter_workflow_outputs_or_shell_commands(self):
        for tag in ["1.2.3", "v1.2", "v01.2.3", "v1.2.3-", "v1.2.3-rc..1",
                    "v1.2.3/other", "v1.2.3\nother=value", "v1.2.3-$(id)", "v1.2.3+build"]:
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                release_metadata(tag, 1)

    def test_build_number_boundaries_and_rollover(self):
        for number in [1, 99, 100, 9999, 10000, 2_100_000_000]:
            self.assertEqual(release_metadata("v1.0.0", number)["version_code"], str(number))
        for number in [0, -1, 2_100_000_001]:
            with self.assertRaises(ValueError):
                release_metadata("v1.0.0", number)

    def test_android_override_preserves_base_module_and_rejects_duplicate_override(self):
        with TemporaryDirectory() as directory:
            module = Path(directory) / "module.yaml"
            source = "product: android/app\nsettings:\n  android:\n    namespace: com.example.app\n"
            module.write_text(source)
            configure_android(module, release_metadata("v2.3.4-debug", 12))
            updated = module.read_text()
            self.assertTrue(updated.startswith(source))
            self.assertIn('versionName: "2.3.4-debug"', updated)
            self.assertIn("versionCode: 12", updated)
            self.assertIn("signing:\n      enabled: false", updated)
            with self.assertRaises(ValueError):
                configure_android(module, release_metadata("v2.3.4", 13))
            self.assertEqual(module.read_text(), updated)


if __name__ == "__main__":
    unittest.main()
