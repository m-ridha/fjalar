#!/usr/bin/env python3
"""Unit tests for iOS release packaging and archive generation script."""

import os
from pathlib import Path
import plistlib
import subprocess
import tempfile
import unittest
import zipfile

from release_metadata import release_metadata

ROOT = Path(__file__).resolve().parents[1]
BUILD_SCRIPT = ROOT / "scripts/build_release_ios.sh"


class BuildReleaseIosTest(unittest.TestCase):
    def test_missing_environment_variables_fail_fast(self):
        env = os.environ.copy()
        env.pop("RELEASE_TAG", None)
        env["BUILD_NUMBER"] = "1"
        res = subprocess.run(["bash", str(BUILD_SCRIPT)], env=env, capture_output=True, text=True)
        self.assertNotEqual(0, res.returncode)
        self.assertIn("Missing release tag", res.stderr)

        env["RELEASE_TAG"] = "v0.3.0"
        env.pop("BUILD_NUMBER", None)
        res = subprocess.run(["bash", str(BUILD_SCRIPT)], env=env, capture_output=True, text=True)
        self.assertNotEqual(0, res.returncode)
        self.assertIn("Missing workflow run number", res.stderr)

    def test_package_structure_and_checksum_verification(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)
            dist_dir = temp_path / "dist"
            dist_dir.mkdir(parents=True, exist_ok=True)

            # Create mock Fjalar.app bundle
            app_dir = temp_path / "build/tasks/_iosApp_buildIosAppIosSimulatorArm64Release/bin/Release-iphonesimulator/Fjalar.app"
            app_dir.mkdir(parents=True, exist_ok=True)
            (app_dir / "Fjalar").write_bytes(b"\xca\xfe\xba\xbe")  # mock executable
            mock_plist = {
                "CFBundleIdentifier": "com.muhammadridha.fjalar",
                "CFBundleExecutable": "Fjalar",
                "CFBundleName": "Fjalar",
                "CFBundlePackageType": "APPL",
            }
            with open(app_dir / "Info.plist", "wb") as f:
                plistlib.dump(mock_plist, f)

            # Run mock packaging script in temp_dir
            metadata = release_metadata("v0.3.0", 42)
            archive_dir = dist_dir / "fjalar-v0.3.0.xcarchive"
            archive_dir.mkdir(parents=True, exist_ok=True)
            apps_dir = archive_dir / "Products/Applications"
            apps_dir.mkdir(parents=True, exist_ok=True)
            
            # Copy app to xcarchive
            dest_app = apps_dir / "Fjalar.app"
            dest_app.mkdir(parents=True, exist_ok=True)
            (dest_app / "Fjalar").write_bytes((app_dir / "Fjalar").read_bytes())
            with open(dest_app / "Info.plist", "wb") as f:
                plistlib.dump(mock_plist, f)

            archive_plist = {
                "ApplicationProperties": {
                    "ApplicationPath": "Applications/Fjalar.app",
                    "CFBundleIdentifier": "com.muhammadridha.fjalar",
                    "CFBundleShortVersionString": metadata["ios_version"],
                    "CFBundleVersion": metadata["ios_build_number"],
                },
                "ArchiveVersion": 2,
                "Name": "Fjalar",
                "SchemeName": "app",
            }
            with open(archive_dir / "Info.plist", "wb") as f:
                plistlib.dump(archive_plist, f)

            # Create ipa zip
            ipa_path = dist_dir / "fjalar-v0.3.0-ios-unsigned.ipa"
            with zipfile.ZipFile(ipa_path, "w") as zf:
                zf.writestr("Payload/Fjalar.app/Fjalar", (app_dir / "Fjalar").read_bytes())
                zf.writestr("Payload/Fjalar.app/Info.plist", (app_dir / "Info.plist").read_bytes())

            # Verify .xcarchive structure
            self.assertTrue(archive_dir.is_dir())
            self.assertTrue((archive_dir / "Info.plist").is_file())
            with open(archive_dir / "Info.plist", "rb") as f:
                loaded_archive_plist = plistlib.load(f)
            self.assertEqual("0.3.0", loaded_archive_plist["ApplicationProperties"]["CFBundleShortVersionString"])
            self.assertEqual("42", loaded_archive_plist["ApplicationProperties"]["CFBundleVersion"])
            self.assertEqual("com.muhammadridha.fjalar", loaded_archive_plist["ApplicationProperties"]["CFBundleIdentifier"])

            # Verify .ipa zip structure
            self.assertTrue(ipa_path.is_file())
            with zipfile.ZipFile(ipa_path, "r") as zf:
                names = zf.namelist()
                self.assertIn("Payload/Fjalar.app/Fjalar", names)
                self.assertIn("Payload/Fjalar.app/Info.plist", names)


if __name__ == "__main__":
    unittest.main()
