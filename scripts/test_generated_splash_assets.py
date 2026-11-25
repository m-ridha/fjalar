#!/usr/bin/env python3
"""
scripts/test_generated_splash_assets.py

Verifies the native vector launch-screen assets in Fjalar:
- Light and dark SVG masters exist in `assets/icons/`, match the splash background colours, and
  share identical emblem path geometry.
- Android splash VectorDrawables exist in `drawable` and `drawable-night`, match the generator
  output, declare a 288x288 dp viewport, and keep all emblem curves inside the 192 dp circle
  Android 12+ paints (radius <= 96 dp).
- iOS launch icon single-scale SVGs and background colourset carry light and dark appearances,
  preserve vector representation, and keep all emblem curves inside the equivalent `size / 3`
  circle.
- Stale raster splash PNGs are absent so they cannot shadow the vector assets.
- Platform wiring still points at those assets (theme, manifest, Info.plist).
"""

import importlib.util
import json
import os
import plistlib
import unittest
import xml.etree.ElementTree as ElementTree

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

LIGHT_MASTER_SVG = "assets/icons/icon_master_light.svg"
DARK_MASTER_SVG = "assets/icons/icon_master_dark.svg"

ANDROID_LIGHT_ICON = "androidApp/res/drawable/ic_splash_icon.xml"
ANDROID_DARK_ICON = "androidApp/res/drawable-night/ic_splash_icon.xml"
ANDROID_LIGHT_COLORS = "androidApp/res/values/colors.xml"
ANDROID_DARK_COLORS = "androidApp/res/values-night/colors.xml"
ANDROID_THEMES = "androidApp/res/values/themes.xml"
ANDROID_MANIFEST = "androidApp/src/AndroidManifest.xml"

IOS_IMAGESET = "iosApp/src/Assets.xcassets/LaunchIcon.imageset"
IOS_COLORSET = "iosApp/src/Assets.xcassets/LaunchBackground.colorset"
IOS_INFO_PLIST = "iosApp/src/Info.plist"

ANDROID_VIEWPORT_SIZE = 288
IOS_POINT_SIZE = 200

ANDROID_NAMESPACE = "{http://schemas.android.com/apk/res/android}"
SVG_NAMESPACE = "{http://www.w3.org/2000/svg}"


def full_path(rel_path: str) -> str:
    return os.path.join(REPO_ROOT, rel_path)


def load_generator():
    """Imports generate_splash_assets.py so the test can rebuild the expected vector outputs."""
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

    def test_master_svgs_match_theme_backgrounds_and_share_geometry(self):
        light_w, light_h, light_bg, light_paths = self.generator.load_master_svg(LIGHT_MASTER_SVG)
        dark_w, dark_h, dark_bg, dark_paths = self.generator.load_master_svg(DARK_MASTER_SVG)

        self.assertEqual((light_w, light_h), (1024.0, 1024.0))
        self.assertEqual((dark_w, dark_h), (1024.0, 1024.0))

        light_theme_bg = read_color(ANDROID_LIGHT_COLORS, "fjalar_splash_background")
        dark_theme_bg = read_color(ANDROID_DARK_COLORS, "fjalar_splash_background")
        self.assertEqual(light_theme_bg, "#FFFFFF")
        self.assertEqual(dark_theme_bg, "#000000")
        self.assertEqual(light_bg, light_theme_bg)
        self.assertEqual(dark_bg, dark_theme_bg)

        self.assertEqual(
            [d_str for _, d_str in light_paths],
            [d_str for _, d_str in dark_paths],
            "Light and dark master SVGs must share identical emblem path geometry",
        )
        self.assertNotEqual(
            [fill for fill, _ in light_paths],
            [fill for fill, _ in dark_paths],
            "Light and dark master SVGs should use distinct light/dark palettes",
        )

    def assert_android_vector_safe_circle(self, rel_path: str) -> list[str]:
        root = ElementTree.parse(full_path(rel_path)).getroot()
        self.assertEqual(root.tag, "vector")
        self.assertEqual(root.get(f"{ANDROID_NAMESPACE}width"), f"{ANDROID_VIEWPORT_SIZE}dp")
        self.assertEqual(root.get(f"{ANDROID_NAMESPACE}height"), f"{ANDROID_VIEWPORT_SIZE}dp")
        viewport_w = float(root.get(f"{ANDROID_NAMESPACE}viewportWidth", "0"))
        viewport_h = float(root.get(f"{ANDROID_NAMESPACE}viewportHeight", "0"))
        self.assertEqual((viewport_w, viewport_h), (float(ANDROID_VIEWPORT_SIZE), float(ANDROID_VIEWPORT_SIZE)))

        paths: list[tuple[str, str]] = []
        for path_el in root.findall("path"):
            fill = (path_el.get(f"{ANDROID_NAMESPACE}fillColor") or "").strip()
            path_data = (path_el.get(f"{ANDROID_NAMESPACE}pathData") or "").strip()
            self.assertTrue(fill, f"{rel_path} has a <path> missing android:fillColor")
            self.assertTrue(path_data, f"{rel_path} has a <path> missing android:pathData")
            paths.append((fill, path_data))
        self.assertGreater(len(paths), 0, f"{rel_path} has no <path> elements")

        radius = self.generator.max_radius_from_center(paths, viewport_w, viewport_h)
        safe_radius = viewport_w / 3.0
        self.assertGreater(radius, 0.0)
        self.assertLessEqual(
            radius,
            safe_radius,
            f"{rel_path} emblem radius {radius:.2f} exceeds masked circle ({safe_radius:.2f})",
        )
        return [d_str for _, d_str in paths]

    def test_android_splash_vectors_keep_artwork_inside_the_masked_circle(self):
        light_geometry = self.assert_android_vector_safe_circle(ANDROID_LIGHT_ICON)
        dark_geometry = self.assert_android_vector_safe_circle(ANDROID_DARK_ICON)
        self.assertEqual(light_geometry, dark_geometry)

    def assert_ios_svg_safe_circle(self, rel_path: str) -> list[str]:
        root = ElementTree.parse(full_path(rel_path)).getroot()
        self.assertEqual(root.tag, f"{SVG_NAMESPACE}svg")
        self.assertEqual(root.get("width"), str(IOS_POINT_SIZE))
        self.assertEqual(root.get("height"), str(IOS_POINT_SIZE))
        width, height = self.generator.parse_viewbox(root)
        self.assertEqual((width, height), (float(IOS_POINT_SIZE), float(IOS_POINT_SIZE)))

        paths: list[tuple[str, str]] = []
        for path_el in root.findall(f"{SVG_NAMESPACE}path"):
            fill = (path_el.get("fill") or "").strip()
            d_str = (path_el.get("d") or "").strip()
            self.assertTrue(fill, f"{rel_path} has a <path> missing fill")
            self.assertTrue(d_str, f"{rel_path} has a <path> missing d")
            paths.append((fill, d_str))
        self.assertGreater(len(paths), 0, f"{rel_path} has no <path> elements")

        radius = self.generator.max_radius_from_center(paths, width, height)
        safe_radius = width / 3.0
        self.assertGreater(radius, 0.0)
        self.assertLessEqual(
            radius,
            safe_radius,
            f"{rel_path} emblem radius {radius:.2f} exceeds safe circle ({safe_radius:.2f})",
        )
        return [d_str for _, d_str in paths]

    def test_ios_launch_vectors_keep_artwork_inside_the_same_circle(self):
        light_geometry = self.assert_ios_svg_safe_circle(f"{IOS_IMAGESET}/launch_icon_light.svg")
        dark_geometry = self.assert_ios_svg_safe_circle(f"{IOS_IMAGESET}/launch_icon_dark.svg")
        self.assertEqual(light_geometry, dark_geometry)

    def test_stale_raster_splash_assets_are_absent(self):
        for rel_path in self.generator.STALE_RASTER_OUTPUTS:
            self.assertFalse(
                os.path.exists(full_path(rel_path)),
                f"Stale raster splash asset still exists at {rel_path}",
            )

    def test_splash_assets_match_generator_output(self):
        expected = [
            (ANDROID_LIGHT_ICON, self.generator.render_android_vector_xml(LIGHT_MASTER_SVG)),
            (ANDROID_DARK_ICON, self.generator.render_android_vector_xml(DARK_MASTER_SVG)),
            (f"{IOS_IMAGESET}/launch_icon_light.svg", self.generator.render_ios_launch_svg(LIGHT_MASTER_SVG)),
            (f"{IOS_IMAGESET}/launch_icon_dark.svg", self.generator.render_ios_launch_svg(DARK_MASTER_SVG)),
        ]
        for out_path, rendered in expected:
            with open(full_path(out_path), encoding="utf-8") as handle:
                stored = handle.read()
            self.assertEqual(
                stored,
                rendered,
                f"{out_path} is stale; run python3 scripts/generate_splash_assets.py",
            )

    def test_ios_launch_imageset_declares_light_and_dark_vectors(self):
        with open(full_path(f"{IOS_IMAGESET}/Contents.json"), encoding="utf-8") as handle:
            contents = json.load(handle)
        self.assertTrue(
            contents.get("properties", {}).get("preserves-vector-representation"),
            "LaunchIcon.imageset must enable preserves-vector-representation",
        )
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
        self.assertEqual([entry.get("filename") for entry in light], ["launch_icon_light.svg"])
        self.assertEqual([entry.get("filename") for entry in dark], ["launch_icon_dark.svg"])
        for entry in light + dark:
            self.assertEqual(entry.get("idiom"), "universal")
            self.assertNotIn("scale", entry)
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
