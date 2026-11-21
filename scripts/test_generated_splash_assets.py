#!/usr/bin/env python3
"""
scripts/test_generated_splash_assets.py

Verifies the native launch-screen assets in Fjalar:
- Android splash icons exist at the expected size, match the generator output, use the theme's
  background colour, and keep all artwork inside the circle Android 12+ actually paints.
- iOS launch icon imageset and background colourset carry light and dark appearances.
- The platform wiring still points at those assets (theme, manifest, Info.plist).

Android 12+ draws only the central two thirds of the 288 dp splash canvas inside a circular mask
(a 96 dp radius), and the compat splash below it mirrors that geometry, so the safe radius is
`size / 3` for every launch image.
"""

import importlib.util
import json
import os
import plistlib
import unittest
import xml.etree.ElementTree as ElementTree

from PIL import Image

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

ANDROID_LIGHT_ICON = "androidApp/res/drawable-nodpi/ic_splash_icon.png"
ANDROID_DARK_ICON = "androidApp/res/drawable-night-nodpi/ic_splash_icon.png"
ANDROID_LIGHT_COLORS = "androidApp/res/values/colors.xml"
ANDROID_DARK_COLORS = "androidApp/res/values-night/colors.xml"
ANDROID_THEMES = "androidApp/res/values/themes.xml"
ANDROID_MANIFEST = "androidApp/src/AndroidManifest.xml"

IOS_IMAGESET = "iosApp/src/Assets.xcassets/LaunchIcon.imageset"
IOS_COLORSET = "iosApp/src/Assets.xcassets/LaunchBackground.colorset"
IOS_INFO_PLIST = "iosApp/src/Info.plist"

ANDROID_ICON_SIZE = (864, 864)
IOS_ICON_SIZE = (600, 600)

ANDROID_NAMESPACE = "{http://schemas.android.com/apk/res/android}"


def full_path(rel_path: str) -> str:
    return os.path.join(REPO_ROOT, rel_path)


def load_generator():
    """Imports generate_splash_assets.py so the test can rebuild the expected pixels."""
    path = full_path("scripts/generate_splash_assets.py")
    spec = importlib.util.spec_from_file_location("generate_splash_assets", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def read_color(rel_path: str, name: str) -> str:
    root = ElementTree.parse(full_path(rel_path)).getroot()
    for color in root.findall("color"):
        if color.get("name") == name:
            return color.text.strip().upper()
    raise AssertionError(f"{rel_path} does not define color '{name}'")


class GeneratedSplashAssetsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.generator = load_generator()

    def assert_safe_circle(self, rel_path: str, size: tuple) -> float:
        width, height = size
        with Image.open(full_path(rel_path)) as image:
            self.assertEqual(image.size, size, f"{rel_path} has unexpected size {image.size}")
            self.assertIn(image.mode, ("RGB", "RGBA"), f"{rel_path} has unexpected mode {image.mode}")
            radius = self.generator.content_radius_px(image)
        safe_radius = width / 3.0
        self.assertGreater(radius, 0, f"{rel_path} has no visible artwork")
        self.assertLessEqual(
            radius,
            safe_radius,
            f"{rel_path} artwork radius {radius:.1f} px exceeds the masked circle "
            f"({safe_radius:.1f} px = 2/3 of a {width} px canvas)",
        )
        return radius

    def test_android_splash_icons_keep_artwork_inside_the_masked_circle(self):
        light_radius = self.assert_safe_circle(ANDROID_LIGHT_ICON, ANDROID_ICON_SIZE)
        dark_radius = self.assert_safe_circle(ANDROID_DARK_ICON, ANDROID_ICON_SIZE)
        self.assertAlmostEqual(
            light_radius,
            dark_radius,
            delta=1.0,
            msg="Light and dark splash artwork should be the same shape",
        )

    def test_ios_launch_icons_keep_artwork_inside_the_same_circle(self):
        self.assert_safe_circle(f"{IOS_IMAGESET}/launch_icon_light.png", IOS_ICON_SIZE)
        self.assert_safe_circle(f"{IOS_IMAGESET}/launch_icon_dark.png", IOS_ICON_SIZE)

    def test_android_splash_backgrounds_match_their_theme_colors(self):
        cases = [
            (ANDROID_LIGHT_ICON, ANDROID_LIGHT_COLORS, "#FFFFFF"),
            (ANDROID_DARK_ICON, ANDROID_DARK_COLORS, "#000000"),
        ]
        for icon_path, colors_path, expected in cases:
            declared = read_color(colors_path, "fjalar_splash_background")
            self.assertEqual(declared, expected, f"{colors_path} changed the splash background")
            with Image.open(full_path(icon_path)) as image:
                corner = image.convert("RGB").getpixel((0, 0))
            self.assertEqual(
                "#%02X%02X%02X" % corner,
                declared,
                f"{icon_path} corner does not blend with {declared}",
            )

    def test_splash_assets_match_generator_output(self):
        expected = [
            (ANDROID_LIGHT_ICON, self.generator.LIGHT_MASTER, self.generator.ANDROID_SIZE),
            (ANDROID_DARK_ICON, self.generator.DARK_MASTER, self.generator.ANDROID_SIZE),
            (f"{IOS_IMAGESET}/launch_icon_light.png", self.generator.LIGHT_MASTER, self.generator.IOS_SIZE),
            (f"{IOS_IMAGESET}/launch_icon_dark.png", self.generator.DARK_MASTER, self.generator.IOS_SIZE),
        ]
        for out_path, master, size in expected:
            rendered = self.generator.png_bytes(self.generator.render_splash_icon(master, size))
            with open(full_path(out_path), "rb") as handle:
                stored = handle.read()
            self.assertEqual(
                stored,
                rendered,
                f"{out_path} is stale; run python3 scripts/generate_splash_assets.py",
            )

    def test_ios_launch_imageset_declares_light_and_dark(self):
        with open(full_path(f"{IOS_IMAGESET}/Contents.json"), encoding="utf-8") as handle:
            contents = json.load(handle)
        images = contents.get("images", [])
        light = [image for image in images if "appearances" not in image]
        dark = [
            image
            for image in images
            if any(
                appearance.get("appearance") == "luminosity" and appearance.get("value") == "dark"
                for appearance in image.get("appearances", [])
            )
        ]
        self.assertEqual([entry.get("filename") for entry in light], ["launch_icon_light.png"])
        self.assertEqual([entry.get("filename") for entry in dark], ["launch_icon_dark.png"])
        for entry in light + dark:
            self.assertEqual(entry.get("scale"), "3x")
            self.assertTrue(os.path.isfile(full_path(f"{IOS_IMAGESET}/{entry['filename']}")))

    def test_ios_launch_background_declares_light_and_dark(self):
        with open(full_path(f"{IOS_COLORSET}/Contents.json"), encoding="utf-8") as handle:
            contents = json.load(handle)
        colors = contents.get("colors", [])
        self.assertEqual(len(colors), 2, "LaunchBackground needs exactly a light and a dark colour")
        for index, expected in ((0, ("1.000", "1.000", "1.000")), (1, ("0.000", "0.000", "0.000"))):
            components = colors[index]["color"]["components"]
            actual = (components["red"], components["green"], components["blue"])
            self.assertEqual(actual, expected)
        self.assertEqual(colors[1].get("appearances", [{}])[0].get("value"), "dark")

    def test_ios_launch_screen_uses_the_launch_assets(self):
        with open(full_path(IOS_INFO_PLIST), "rb") as handle:
            info = plistlib.load(handle)
        launch_screen = info.get("UILaunchScreen")
        self.assertIsInstance(launch_screen, dict, "UILaunchScreen must describe the launch assets")
        self.assertEqual(launch_screen.get("UIColorName"), "LaunchBackground")
        self.assertEqual(launch_screen.get("UIImageName"), "LaunchIcon")

    def test_android_theme_and_manifest_use_the_splash_theme(self):
        themes = ElementTree.parse(full_path(ANDROID_THEMES)).getroot()
        starting_theme = next(
            (style for style in themes.findall("style") if style.get("name") == "Theme.Fjalar.Starting"),
            None,
        )
        self.assertIsNotNone(starting_theme, "Missing Theme.Fjalar.Starting")
        self.assertEqual(starting_theme.get("parent"), "Theme.SplashScreen")

        items = {item.get("name"): (item.text or "").strip() for item in starting_theme.findall("item")}
        self.assertEqual(items.get("windowSplashScreenBackground"), "@color/fjalar_splash_background")
        self.assertEqual(items.get("windowSplashScreenAnimatedIcon"), "@drawable/ic_splash_icon")
        self.assertEqual(items.get("postSplashScreenTheme"), "@style/Theme.Fjalar")

        manifest = ElementTree.parse(full_path(ANDROID_MANIFEST)).getroot()
        activity = manifest.find("application/activity")
        self.assertIsNotNone(activity, "Missing MainActivity in the manifest")
        self.assertEqual(activity.get(f"{ANDROID_NAMESPACE}theme"), "@style/Theme.Fjalar.Starting")


if __name__ == "__main__":
    unittest.main()
