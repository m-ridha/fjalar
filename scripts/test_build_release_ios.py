#!/usr/bin/env python3
"""Unit tests for iOS release packaging and archive generation script."""

import hashlib
import os
from pathlib import Path
import plistlib
import shutil
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BUILD_SCRIPT = ROOT / "scripts/build_release_ios.sh"
METADATA_SCRIPT = ROOT / "scripts/release_metadata.py"


class BuildReleaseIosTest(unittest.TestCase):
    """Tests for scripts/build_release_ios.sh behavior and output structure."""

    def test_missing_environment_variables_fail_fast(self):
        """Ensure missing RELEASE_TAG or BUILD_NUMBER causes immediate failure."""
        env = os.environ.copy()
        env.pop("RELEASE_TAG", None)
        env["BUILD_NUMBER"] = "1"
        res = subprocess.run(["bash", str(BUILD_SCRIPT)], env=env, capture_output=True, text=True)
        self.assertNotEqual(0, res.returncode)
        self.assertIn("Missing release tag", res.stderr)

        env["RELEASE_TAG"] = "v0.3.1"
        env.pop("BUILD_NUMBER", None)
        res = subprocess.run(["bash", str(BUILD_SCRIPT)], env=env, capture_output=True, text=True)
        self.assertNotEqual(0, res.returncode)
        self.assertIn("Missing workflow run number", res.stderr)

    def test_package_structure_and_checksum_verification(self):
        """Run build_release_ios.sh with a stub kotlin CLI and verify .xcarchive, .ipa, and checksum."""
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)
            scripts_dir = temp_path / "scripts"
            scripts_dir.mkdir(parents=True, exist_ok=True)
            shutil.copy2(METADATA_SCRIPT, scripts_dir / "release_metadata.py")

            # Create stub ./kotlin executable that validates arguments and produces Fjalar.app
            kotlin_stub = temp_path / "kotlin"
            kotlin_stub.write_text(
                "#!/usr/bin/env python3\n"
                "import plistlib, sys\n"
                "from pathlib import Path\n"
                "assert sys.argv[1:] == ['build', '-m', 'iosApp', '-p', 'iosArm64', '-v', 'release'], sys.argv\n"
                "app_dir = Path('build/tasks/_iosApp_buildIosAppIosArm64Release/bin/Release-iphoneos/Fjalar.app')\n"
                "app_dir.mkdir(parents=True, exist_ok=True)\n"
                "(app_dir / 'Fjalar').write_bytes(b'\\xca\\xfe\\xba\\xbe')\n"
                "with open(app_dir / 'Info.plist', 'wb') as f:\n"
                "    plistlib.dump({\n"
                "        'CFBundleIdentifier': 'com.muhammadridha.fjalar',\n"
                "        'CFBundleExecutable': 'Fjalar',\n"
                "        'CFBundleName': 'Fjalar',\n"
                "        'CFBundlePackageType': 'APPL',\n"
                "        'CFBundleShortVersionString': '1.0',\n"
                "        'CFBundleVersion': '1',\n"
                "    }, f, fmt=plistlib.FMT_BINARY)\n"
            )
            kotlin_stub.chmod(0o755)

            env = os.environ.copy()
            env["RELEASE_TAG"] = "v0.3.1"
            env["BUILD_NUMBER"] = "42"
            env.pop("IOS_TARGET_PLATFORM", None)

            res = subprocess.run(
                ["bash", str(BUILD_SCRIPT)],
                cwd=temp_path,
                env=env,
                capture_output=True,
                text=True,
                check=True,
            )
            self.assertEqual(0, res.returncode)

            dist_dir = temp_path / "dist"
            archive_dir = dist_dir / "fjalar-v0.3.1.xcarchive"
            ipa_path = dist_dir / "fjalar-v0.3.1-ios-unsigned.ipa"
            checksum_path = dist_dir / "SHA256SUMS-ios.txt"

            # Verify intermediate build/tasks Fjalar.app/Info.plist was not mutated
            source_plist = temp_path / "build/tasks/_iosApp_buildIosAppIosArm64Release/bin/Release-iphoneos/Fjalar.app/Info.plist"
            with open(source_plist, "rb") as f:
                loaded_source_plist = plistlib.load(f)
            self.assertEqual("1.0", loaded_source_plist["CFBundleShortVersionString"])
            self.assertEqual("1", loaded_source_plist["CFBundleVersion"])

            # Verify .xcarchive structure and both outer + embedded Info.plist versions
            self.assertTrue(archive_dir.is_dir())
            self.assertTrue((archive_dir / "Info.plist").is_file())
            self.assertTrue((archive_dir / "Products/Applications/Fjalar.app/Fjalar").is_file())
            with open(archive_dir / "Info.plist", "rb") as f:
                loaded_archive_plist = plistlib.load(f)
            self.assertEqual("0.3.1", loaded_archive_plist["ApplicationProperties"]["CFBundleShortVersionString"])
            self.assertEqual("42", loaded_archive_plist["ApplicationProperties"]["CFBundleVersion"])
            self.assertEqual("com.muhammadridha.fjalar", loaded_archive_plist["ApplicationProperties"]["CFBundleIdentifier"])

            with open(archive_dir / "Products/Applications/Fjalar.app/Info.plist", "rb") as f:
                loaded_embedded_archive_plist = plistlib.load(f)
            self.assertEqual("0.3.1", loaded_embedded_archive_plist["CFBundleShortVersionString"])
            self.assertEqual("42", loaded_embedded_archive_plist["CFBundleVersion"])

            # Verify .ipa zip structure and embedded Payload/Fjalar.app/Info.plist versions
            self.assertTrue(ipa_path.is_file())
            with zipfile.ZipFile(ipa_path, "r") as zf:
                names = zf.namelist()
                self.assertIn("Payload/Fjalar.app/Fjalar", names)
                self.assertIn("Payload/Fjalar.app/Info.plist", names)
                loaded_ipa_plist = plistlib.loads(zf.read("Payload/Fjalar.app/Info.plist"))
            self.assertEqual("0.3.1", loaded_ipa_plist["CFBundleShortVersionString"])
            self.assertEqual("42", loaded_ipa_plist["CFBundleVersion"])

            # Verify SHA256SUMS-ios.txt matches actual .ipa hash
            self.assertTrue(checksum_path.is_file())
            expected_sha = hashlib.sha256(ipa_path.read_bytes()).hexdigest()
            checksum_content = checksum_path.read_text().strip()
            self.assertEqual(f"{expected_sha}  fjalar-v0.3.1-ios-unsigned.ipa", checksum_content)


if __name__ == "__main__":
    unittest.main()
