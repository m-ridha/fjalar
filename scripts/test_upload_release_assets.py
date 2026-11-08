#!/usr/bin/env python3
"""Exercise retry/partial-failure behavior without mutating a real release."""

import hashlib
import json
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

from upload_release_assets import sha256, upload_assets


class UploadReleaseAssetsTest(unittest.TestCase):
    def exercise(self, existing, fail_upload=False, include_ios=False):
        with TemporaryDirectory() as temporary:
            directory = Path(temporary)
            apk = directory / "fjalar-v1.2.3-android.apk"
            apk.write_bytes(b"signed apk fixture")
            checksum = f"{sha256(apk)}  {apk.name}\n".encode()

            ipa = directory / "fjalar-v1.2.3-ios-unsigned.ipa"
            ios_checksum = b""
            if include_ios:
                ipa.write_bytes(b"unsigned ipa fixture")
                ios_checksum = f"{sha256(ipa)}  {ipa.name}\n".encode()

            remote = {}
            for name, value in existing.items():
                if value == "checksum":
                    remote[name] = checksum
                elif value == "ios_checksum":
                    remote[name] = ios_checksum
                else:
                    remote[name] = value

            calls = []
            uploaded_checksums = []

            def run(command, **kwargs):
                calls.append(command)
                self.assertNotIn("--clobber", command)
                self.assertNotIn("delete-asset", command)
                if command[2] == "download":
                    name = command[command.index("--pattern") + 1]
                    destination = Path(command[command.index("--dir") + 1])
                    (destination / name).write_bytes(remote[name])
                elif command[2] == "upload":
                    for argument in command[command.index("--") + 1:]:
                        path = Path(argument)
                        if path.name in ("SHA256SUMS.txt", "SHA256SUMS-ios.txt"):
                            uploaded_checksums.append(path.read_text())
                    if fail_upload:
                        raise subprocess.CalledProcessError(1, command)

            response = json.dumps({"assets": [{"name": name} for name in remote]})
            with patch("upload_release_assets.subprocess.check_output", return_value=response), \
                 patch("upload_release_assets.subprocess.run", side_effect=run):
                error = None
                try:
                    upload_assets("v1.2.3", directory)
                except (ValueError, subprocess.CalledProcessError) as caught:
                    error = caught
            return calls, error, uploaded_checksums

    def test_new_release_uploads_pair(self):
        calls, error, uploaded_checksums = self.exercise({})
        self.assertIsNone(error)
        self.assertEqual(len(calls), 1)
        self.assertEqual(calls[0][2], "upload")
        self.assertEqual([Path(path).name for path in calls[0][5:]],
                         ["fjalar-v1.2.3-android.apk", "SHA256SUMS.txt"])
        expected_digest = hashlib.sha256(b"signed apk fixture").hexdigest()
        self.assertEqual(uploaded_checksums,
                         [f"{expected_digest}  fjalar-v1.2.3-android.apk\n"])

    def test_identical_retry_uploads_nothing(self):
        calls, error, _ = self.exercise({"fjalar-v1.2.3-android.apk": b"signed apk fixture",
                                     "SHA256SUMS.txt": "checksum"})
        self.assertIsNone(error)
        self.assertTrue(all(call[2] == "download" for call in calls))

    def test_partial_upload_completes_only_missing_checksum(self):
        calls, error, _ = self.exercise({"fjalar-v1.2.3-android.apk": b"signed apk fixture"})
        self.assertIsNone(error)
        self.assertEqual([Path(path).name for path in calls[-1][5:]], ["SHA256SUMS.txt"])

    def test_any_mismatch_prevents_all_uploads(self):
        for remote in [{"fjalar-v1.2.3-android.apk": b"older apk"},
                       {"SHA256SUMS.txt": b"older checksum"}]:
            calls, error, _ = self.exercise(remote)
            self.assertIsInstance(error, ValueError)
            self.assertFalse(any(call[2] == "upload" for call in calls))

    def test_upload_failure_never_deletes_existing_assets(self):
        calls, error, _ = self.exercise({"fjalar-v1.2.3-android.apk": b"signed apk fixture"}, fail_upload=True)
        self.assertIsInstance(error, subprocess.CalledProcessError)
        self.assertEqual([call[2] for call in calls], ["download", "upload"])

    def test_new_release_uploads_android_and_ios_assets_when_ipa_present(self):
        """Uploads APK, SHA256SUMS.txt, unsigned IPA, and SHA256SUMS-ios.txt when IPA exists."""
        calls, error, uploaded_checksums = self.exercise({}, include_ios=True)
        self.assertIsNone(error)
        self.assertEqual(len(calls), 1)
        self.assertEqual(calls[0][2], "upload")
        self.assertEqual(
            [Path(path).name for path in calls[0][5:]],
            [
                "fjalar-v1.2.3-android.apk",
                "SHA256SUMS.txt",
                "fjalar-v1.2.3-ios-unsigned.ipa",
                "SHA256SUMS-ios.txt",
            ],
        )
        expected_apk_digest = hashlib.sha256(b"signed apk fixture").hexdigest()
        expected_ipa_digest = hashlib.sha256(b"unsigned ipa fixture").hexdigest()
        self.assertEqual(
            uploaded_checksums,
            [
                f"{expected_apk_digest}  fjalar-v1.2.3-android.apk\n",
                f"{expected_ipa_digest}  fjalar-v1.2.3-ios-unsigned.ipa\n",
            ],
        )

    def test_partial_ios_upload_completes_only_missing_ios_assets(self):
        """Completes upload of missing iOS IPA and checksum while preserving existing Android assets."""
        calls, error, _ = self.exercise(
            {
                "fjalar-v1.2.3-android.apk": b"signed apk fixture",
                "SHA256SUMS.txt": "checksum",
            },
            include_ios=True,
        )
        self.assertIsNone(error)
        self.assertEqual(
            [Path(path).name for path in calls[-1][5:]],
            ["fjalar-v1.2.3-ios-unsigned.ipa", "SHA256SUMS-ios.txt"],
        )


if __name__ == "__main__":
    unittest.main()
