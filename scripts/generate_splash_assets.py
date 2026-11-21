#!/usr/bin/env python3
"""
scripts/generate_splash_assets.py

Generates launch-screen artwork from the approved icon masters (no new artwork):
1. Android splash icons (864x864 px = 288 dp at 3x) in drawable-nodpi / drawable-night-nodpi.
2. iOS launch-screen icon imageset (light + dark appearances).

The masters have exact #FFFFFF (light) and #000000 (dark) canvases, which the native splash
background colors match, so the square canvas blends into the background seamlessly.

Android 12+ (and the compat splash below it) paints only the central two thirds of the 288 dp
canvas inside a circular mask: a 192 dp circle, i.e. a 96 dp radius. The artwork is therefore
scaled down and re-centred so its outermost mark stays inside [SPLASH_SAFE_RADIUS_RATIO] of that
visible circle. The same ratio is used for the iOS launch image so the mark reads the same size
on both platforms. `scripts/test_generated_splash_assets.py` enforces the bound.
"""

import io
import json
import math
import os
import sys
from PIL import Image

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

ANDROID_SIZE = 864
IOS_SIZE = 600

# Android 12+ shows the central 192 dp of the 288 dp splash canvas inside a circle (radius 96 dp).
VISIBLE_RADIUS_RATIO = 1.0 / 3.0
# Leave a margin inside the mask so no mark pixel can touch the clipped edge.
SAFE_RADIUS_RATIO = VISIBLE_RADIUS_RATIO * 0.9

# Per-channel difference from the canvas colour above which a pixel counts as artwork.
CONTENT_THRESHOLD = 8

LIGHT_MASTER = "assets/icons/icon_master_light_1024.png"
DARK_MASTER = "assets/icons/icon_master_dark_1024.png"

ANDROID_LIGHT_OUT = "androidApp/res/drawable-nodpi/ic_splash_icon.png"
ANDROID_DARK_OUT = "androidApp/res/drawable-night-nodpi/ic_splash_icon.png"
IOS_IMAGESET = "iosApp/src/Assets.xcassets/LaunchIcon.imageset"
IOS_COLORSET = "iosApp/src/Assets.xcassets/LaunchBackground.colorset"


def full_path(rel_path: str) -> str:
    return os.path.join(REPO_ROOT, rel_path)


def canvas_background(image: Image.Image) -> tuple:
    """The master canvas colour, read from a corner that is always outside the artwork."""
    return image.getpixel((0, 0))


def content_radius_px(image: Image.Image) -> float:
    """Largest distance from the centre to any pixel that differs from the canvas colour."""
    rgb = image.convert("RGB")
    width, height = rgb.size
    background = canvas_background(rgb)
    pixels = rgb.load()
    centre_x = (width - 1) / 2.0
    centre_y = (height - 1) / 2.0
    largest_squared = 0.0
    for y in range(height):
        for x in range(width):
            pixel = pixels[x, y]
            if (
                abs(pixel[0] - background[0]) > CONTENT_THRESHOLD
                or abs(pixel[1] - background[1]) > CONTENT_THRESHOLD
                or abs(pixel[2] - background[2]) > CONTENT_THRESHOLD
            ):
                dx = x - centre_x
                dy = y - centre_y
                squared = dx * dx + dy * dy
                if squared > largest_squared:
                    largest_squared = squared
    return math.sqrt(largest_squared)


def render_splash_icon(master_rel_path: str, size: int) -> Image.Image:
    """Scales the master so its artwork fits inside the safe circle of a `size` canvas."""
    master = Image.open(full_path(master_rel_path)).convert("RGB")
    background = canvas_background(master)
    measured_radius = content_radius_px(master)
    if measured_radius <= 0:
        raise SystemExit(f"{master_rel_path} has no visible artwork")

    target_radius = size * SAFE_RADIUS_RATIO
    scale = target_radius / measured_radius
    inner_size = max(1, round(master.width * scale))

    artwork = master.resize((inner_size, inner_size), Image.LANCZOS)
    canvas = Image.new("RGB", (size, size), background)
    offset = (size - inner_size) // 2
    canvas.paste(artwork, (offset, offset))
    return canvas


def png_bytes(image: Image.Image) -> bytes:
    buffer = io.BytesIO()
    image.save(buffer, format="PNG", optimize=True)
    return buffer.getvalue()


def write_png(image: Image.Image, rel_path: str) -> None:
    out_path = full_path(rel_path)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "wb") as handle:
        handle.write(png_bytes(image))


def write_ios_metadata() -> None:
    contents = {
        "images": [
            {"idiom": "universal", "scale": "3x", "filename": "launch_icon_light.png"},
            {
                "idiom": "universal",
                "scale": "3x",
                "filename": "launch_icon_dark.png",
                "appearances": [{"appearance": "luminosity", "value": "dark"}],
            },
        ],
        "info": {"author": "xcode", "version": 1},
    }
    os.makedirs(full_path(IOS_IMAGESET), exist_ok=True)
    with open(full_path(f"{IOS_IMAGESET}/Contents.json"), "w") as handle:
        json.dump(contents, handle, indent=2)
        handle.write("\n")

    def color(value: float) -> dict:
        component = f"{value:.3f}"
        return {
            "color-space": "srgb",
            "components": {"red": component, "green": component, "blue": component, "alpha": "1.000"},
        }

    colorset = {
        "colors": [
            {"idiom": "universal", "color": color(1.0)},
            {
                "idiom": "universal",
                "appearances": [{"appearance": "luminosity", "value": "dark"}],
                "color": color(0.0),
            },
        ],
        "info": {"author": "xcode", "version": 1},
    }
    os.makedirs(full_path(IOS_COLORSET), exist_ok=True)
    with open(full_path(f"{IOS_COLORSET}/Contents.json"), "w") as handle:
        json.dump(colorset, handle, indent=2)
        handle.write("\n")


def main() -> int:
    write_png(render_splash_icon(LIGHT_MASTER, ANDROID_SIZE), ANDROID_LIGHT_OUT)
    write_png(render_splash_icon(DARK_MASTER, ANDROID_SIZE), ANDROID_DARK_OUT)
    write_png(render_splash_icon(LIGHT_MASTER, IOS_SIZE), f"{IOS_IMAGESET}/launch_icon_light.png")
    write_png(render_splash_icon(DARK_MASTER, IOS_SIZE), f"{IOS_IMAGESET}/launch_icon_dark.png")
    write_ios_metadata()
    return 0


if __name__ == "__main__":
    sys.exit(main())
