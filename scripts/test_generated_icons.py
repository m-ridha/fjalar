#!/usr/bin/env python3
"""
scripts/test_generated_icons.py

Automated verification test suite for Fjalar app icon pipeline.
Validates existence, dimensions, bit depths, channels, safe-zone bounds,
and platform XML/JSON configurations.
"""

import json
import math
import os
import sys
import unittest
from PIL import Image

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


class TestGeneratedIcons(unittest.TestCase):
    def full_path(self, rel_path: str) -> str:
        return os.path.join(REPO_ROOT, rel_path)

    def test_playstore_icon(self):
        """Play Store master: 512x512 flat square icon."""
        path = self.full_path("androidApp/ic_launcher-playstore.png")
        self.assertTrue(os.path.isfile(path), f"Missing {path}")
        with Image.open(path) as img:
            self.assertEqual(img.size, (512, 512), f"Expected 512x512, got {img.size}")
            self.assertIn(img.mode, ("RGB", "RGBA"), f"Unexpected mode {img.mode}")

    def test_ios_icons_and_contents(self):
        """iOS 18 asset catalog icons (light, dark, tinted) and Contents.json."""
        catalog_dir = self.full_path("iosApp/src/Assets.xcassets/AppIcon.appiconset")
        self.assertTrue(os.path.isdir(catalog_dir), f"Missing catalog dir {catalog_dir}")

        # Check image files
        expected_ios_files = {
            "app-icon-1024.png": "light",
            "app-icon-dark-1024.png": "dark",
            "app-icon-tinted-1024.png": "tinted",
        }
        for filename, variant in expected_ios_files.items():
            file_path = os.path.join(catalog_dir, filename)
            self.assertTrue(os.path.isfile(file_path), f"Missing {variant} icon: {file_path}")
            with Image.open(file_path) as img:
                self.assertEqual(img.size, (1024, 1024), f"Expected 1024x1024 for {filename}, got {img.size}")
                if variant == "tinted":
                    self.assertEqual(img.mode, "RGBA", f"Expected RGBA for tinted icon, got {img.mode}")
                    # Tinted icon must have monochrome pixels (all non-transparent pixels should have R==G==B)
                    pixels = img.load()
                    non_transparent = 0
                    for y in range(0, 1024, 4):
                        for x in range(0, 1024, 4):
                            r, g, b, a = pixels[x, y]
                            if a > 0:
                                non_transparent += 1
                                self.assertEqual(r, g, f"Tinted pixel at ({x},{y}) not monochrome: {r}!={g}")
                                self.assertEqual(g, b, f"Tinted pixel at ({x},{y}) not monochrome: {g}!={b}")
                    self.assertGreater(non_transparent, 0, "Tinted icon has no non-transparent pixels")

        # Check Contents.json
        contents_path = os.path.join(catalog_dir, "Contents.json")
        self.assertTrue(os.path.isfile(contents_path), f"Missing {contents_path}")
        with open(contents_path, "r", encoding="utf-8") as f:
            data = json.load(f)

        self.assertIn("images", data, "Missing images array in Contents.json")
        images = data["images"]
        self.assertEqual(len(images), 3, f"Expected 3 images entries in Contents.json, found {len(images)}")

        light_entry = next((img for img in images if "appearances" not in img), None)
        self.assertIsNotNone(light_entry, "Missing light universal entry in Contents.json")
        self.assertEqual(light_entry.get("filename"), "app-icon-1024.png")
        self.assertEqual(light_entry.get("size"), "1024x1024")

        dark_entry = next(
            (
                img
                for img in images
                if any(
                    app.get("appearance") == "luminosity" and app.get("value") == "dark"
                    for app in img.get("appearances", [])
                )
            ),
            None,
        )
        self.assertIsNotNone(dark_entry, "Missing dark appearance entry in Contents.json")
        self.assertEqual(dark_entry.get("filename"), "app-icon-dark-1024.png")
        self.assertEqual(dark_entry.get("size"), "1024x1024")

        tinted_entry = next(
            (
                img
                for img in images
                if any(
                    app.get("appearance") == "luminosity" and app.get("value") == "tinted"
                    for app in img.get("appearances", [])
                )
            ),
            None,
        )
        self.assertIsNotNone(tinted_entry, "Missing tinted appearance entry in Contents.json")
        self.assertEqual(tinted_entry.get("filename"), "app-icon-tinted-1024.png")
        self.assertEqual(tinted_entry.get("size"), "1024x1024")

    def test_adaptive_icon_foreground_safe_zone(self):
        """Android adaptive foreground (432x432 px xxxhdpi) contained strictly within 66dp (264px) safe circle."""
        path = self.full_path("androidApp/res/drawable-xxxhdpi/ic_launcher_foreground.png")
        self.assertTrue(os.path.isfile(path), f"Missing {path}")

        with Image.open(path) as img:
            self.assertEqual(img.size, (432, 432), f"Expected 432x432 for adaptive foreground, got {img.size}")
            self.assertEqual(img.mode, "RGBA", f"Expected RGBA mode, got {img.mode}")

            bbox = img.getbbox()
            self.assertIsNotNone(bbox, "Foreground image is empty")

            # 432x432 canvas with 66dp (264px) diameter safe center circle.
            # Canvas center is (216, 216).
            # Max safe radius = 132 px.
            # Safe bounding box bounds: [216-132, 216-132, 216+132, 216+132] = [84, 84, 348, 348].
            min_x, min_y, max_x, max_y = bbox
            self.assertGreaterEqual(min_x, 84, f"Foreground left ({min_x}) extends beyond safe zone (84)")
            self.assertGreaterEqual(min_y, 84, f"Foreground top ({min_y}) extends beyond safe zone (84)")
            self.assertLessEqual(max_x, 348, f"Foreground right ({max_x}) extends beyond safe zone (348)")
            self.assertLessEqual(max_y, 348, f"Foreground bottom ({max_y}) extends beyond safe zone (348)")

            # Check that every non-transparent pixel is strictly within the 132px radius circle
            pixels = img.load()
            for y in range(min_y, max_y):
                for x in range(min_x, max_x):
                    alpha = pixels[x, y][3]
                    if alpha > 0:
                        dist = math.sqrt((x - 216.0) ** 2 + (y - 216.0) ** 2)
                        self.assertLessEqual(
                            dist,
                            132.0,
                            f"Pixel at ({x}, {y}) is outside 66dp safe circle (distance {dist:.2f} > 132.0)",
                        )

    def test_adaptive_icon_monochrome(self):
        """Android Material You monochrome mask (432x432 px xxxhdpi solid white with alpha)."""
        path = self.full_path("androidApp/res/drawable-xxxhdpi/ic_launcher_monochrome.png")
        self.assertTrue(os.path.isfile(path), f"Missing {path}")

        with Image.open(path) as img:
            self.assertEqual(img.size, (432, 432), f"Expected 432x432 for monochrome icon, got {img.size}")
            self.assertEqual(img.mode, "RGBA", f"Expected RGBA mode, got {img.mode}")

            bbox = img.getbbox()
            self.assertIsNotNone(bbox, "Monochrome image is empty")

            min_x, min_y, max_x, max_y = bbox
            self.assertGreaterEqual(min_x, 84, f"Monochrome left ({min_x}) extends beyond safe zone (84)")
            self.assertGreaterEqual(min_y, 84, f"Monochrome top ({min_y}) extends beyond safe zone (84)")
            self.assertLessEqual(max_x, 348, f"Monochrome right ({max_x}) extends beyond safe zone (348)")
            self.assertLessEqual(max_y, 348, f"Monochrome bottom ({max_y}) extends beyond safe zone (348)")

            pixels = img.load()
            for y in range(min_y, max_y):
                for x in range(min_x, max_x):
                    r, g, b, a = pixels[x, y]
                    if a > 0:
                        self.assertEqual(r, 255, f"Monochrome pixel at ({x},{y}) R is {r}, expected 255")
                        self.assertEqual(g, 255, f"Monochrome pixel at ({x},{y}) G is {g}, expected 255")
                        self.assertEqual(b, 255, f"Monochrome pixel at ({x},{y}) B is {b}, expected 255")
                        dist = math.sqrt((x - 216.0) ** 2 + (y - 216.0) ** 2)
                        self.assertLessEqual(
                            dist,
                            132.0,
                            f"Monochrome pixel at ({x}, {y}) outside safe circle (dist {dist:.2f} > 132.0)",
                        )

    def test_legacy_mipmaps(self):
        """Legacy mipmaps across densities (mdpi 48, hdpi 72, xhdpi 96, xxhdpi 144, xxxhdpi 192)."""
        densities = {
            "mdpi": 48,
            "hdpi": 72,
            "xhdpi": 96,
            "xxhdpi": 144,
            "xxxhdpi": 192,
        }
        for density, expected_size in densities.items():
            dir_path = self.full_path(f"androidApp/res/mipmap-{density}")
            self.assertTrue(os.path.isdir(dir_path), f"Missing mipmap directory: {dir_path}")

            # Square legacy icon
            square_path = os.path.join(dir_path, "ic_launcher.png")
            self.assertTrue(os.path.isfile(square_path), f"Missing {square_path}")
            with Image.open(square_path) as img:
                self.assertEqual(
                    img.size,
                    (expected_size, expected_size),
                    f"Expected ({expected_size}, {expected_size}) for {density} ic_launcher, got {img.size}",
                )

            # Round legacy icon
            round_path = os.path.join(dir_path, "ic_launcher_round.png")
            self.assertTrue(os.path.isfile(round_path), f"Missing {round_path}")
            with Image.open(round_path) as img:
                self.assertEqual(
                    img.size,
                    (expected_size, expected_size),
                    f"Expected ({expected_size}, {expected_size}) for {density} ic_launcher_round, got {img.size}",
                )
                self.assertEqual(img.mode, "RGBA", f"Expected RGBA mode for round icon, got {img.mode}")
                # Corner pixels must be transparent due to circular mask
                pixels = img.load()
                self.assertEqual(pixels[0, 0][3], 0, f"Corner (0, 0) in {density} round icon is not transparent")
                self.assertEqual(
                    pixels[expected_size - 1, 0][3],
                    0,
                    f"Corner ({expected_size-1}, 0) in {density} round icon is not transparent",
                )
                self.assertEqual(
                    pixels[0, expected_size - 1][3],
                    0,
                    f"Corner (0, {expected_size-1}) in {density} round icon is not transparent",
                )
                self.assertEqual(
                    pixels[expected_size - 1, expected_size - 1][3],
                    0,
                    f"Corner ({expected_size-1}, {expected_size-1}) in {density} round icon is not transparent",
                )

    def test_android_xml_configurations(self):
        """Android XML resource configuration checks."""
        # 1. Stale vector ic_launcher_foreground.xml in drawable-v24 must be removed
        stale_vector = self.full_path("androidApp/res/drawable-v24/ic_launcher_foreground.xml")
        self.assertFalse(
            os.path.exists(stale_vector),
            f"Stale vector {stale_vector} must be deleted to avoid shadowing PNG foreground",
        )

        # 1b. Unqualified adaptive bitmaps must not exist in androidApp/res/drawable/ (must be in drawable-xxxhdpi)
        for unqualified_name in ("ic_launcher_foreground.png", "ic_launcher_monochrome.png"):
            unqualified_path = self.full_path(f"androidApp/res/drawable/{unqualified_name}")
            self.assertFalse(
                os.path.exists(unqualified_path),
                f"Unqualified bitmap {unqualified_path} must not exist in drawable/ (should be in drawable-xxxhdpi/)",
            )

        # 2. ic_launcher_background.xml must be clean solid #FFFFFF
        bg_xml_path = self.full_path("androidApp/res/drawable/ic_launcher_background.xml")
        self.assertTrue(os.path.isfile(bg_xml_path), f"Missing {bg_xml_path}")
        with open(bg_xml_path, "r", encoding="utf-8") as f:
            bg_content = f.read()
        self.assertIn("#FFFFFF", bg_content, "ic_launcher_background.xml missing solid #FFFFFF fillColor")
        self.assertNotIn("#3DDC84", bg_content, "ic_launcher_background.xml still contains old green #3DDC84")

        # 3. mipmap-anydpi-v26/ic_launcher.xml includes monochrome tag
        launcher_xml_path = self.full_path("androidApp/res/mipmap-anydpi-v26/ic_launcher.xml")
        self.assertTrue(os.path.isfile(launcher_xml_path), f"Missing {launcher_xml_path}")
        with open(launcher_xml_path, "r", encoding="utf-8") as f:
            launcher_content = f.read()
        self.assertIn("@drawable/ic_launcher_monochrome", launcher_content, "ic_launcher.xml missing monochrome element")
        self.assertIn("@drawable/ic_launcher_foreground", launcher_content, "ic_launcher.xml missing foreground element")
        self.assertIn("@drawable/ic_launcher_background", launcher_content, "ic_launcher.xml missing background element")

        # 4. mipmap-anydpi-v26/ic_launcher_round.xml includes monochrome tag
        round_xml_path = self.full_path("androidApp/res/mipmap-anydpi-v26/ic_launcher_round.xml")
        self.assertTrue(os.path.isfile(round_xml_path), f"Missing {round_xml_path}")
        with open(round_xml_path, "r", encoding="utf-8") as f:
            round_content = f.read()
        self.assertIn("@drawable/ic_launcher_monochrome", round_content, "ic_launcher_round.xml missing monochrome element")
        self.assertIn("@drawable/ic_launcher_foreground", round_content, "ic_launcher_round.xml missing foreground element")
        self.assertIn("@drawable/ic_launcher_background", round_content, "ic_launcher_round.xml missing background element")


if __name__ == "__main__":
    unittest.main()
