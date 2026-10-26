#!/usr/bin/env python3
"""
scripts/generate_app_icons.py

Automated production pipeline for Fjalar app icons.
Generates:
1. Android adaptive foreground and monochrome icons (432x432 px xxxhdpi, safe-zone centered).
2. Android legacy square and round mipmaps across mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi.
3. Android Play Store master (512x512).
4. iOS 18 universal asset catalog icons: light, dark, and tinted monochrome (1024x1024).
5. Android XML configurations (ic_launcher_background.xml, adaptive ic_launcher.xml, ic_launcher_round.xml).
6. iOS Contents.json asset catalog manifest.
7. Cleans up stale Android vector drawables (ic_launcher_foreground.xml in drawable-v24).
"""

import json
import math
import os
import shutil
import sys
from PIL import Image, ImageDraw

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def full_path(rel_path: str) -> str:
    return os.path.join(REPO_ROOT, rel_path)


def extract_emblem(master_img: Image.Image) -> Image.Image:
    """
    Extracts the emblem from icon_master_light_1024.png by removing the solid white background.
    Preserves anti-aliasing with a smooth alpha ramp and unblends RGB from white.
    """
    rgb_img = master_img.convert("RGB")
    pixels = rgb_img.load()
    w, h = rgb_img.size

    rgba_img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    rgba_pixels = rgba_img.load()

    for y in range(h):
        for x in range(w):
            r, g, b = pixels[x, y]
            delta = max(255 - r, 255 - g, 255 - b)
            if delta <= 6:
                # Pure white canvas background
                continue
            elif delta >= 50:
                # Fully opaque emblem body
                rgba_pixels[x, y] = (r, g, b, 255)
            else:
                # Smooth antialiased border transition
                a_float = (delta - 6.0) / (50.0 - 6.0)
                alpha = int(a_float * 255.0)
                r_fg = int(min(255, max(0, (r - 255.0 * (1.0 - a_float)) / a_float)))
                g_fg = int(min(255, max(0, (g - 255.0 * (1.0 - a_float)) / a_float)))
                b_fg = int(min(255, max(0, (b - 255.0 * (1.0 - a_float)) / a_float)))
                rgba_pixels[x, y] = (r_fg, g_fg, b_fg, alpha)

    return rgba_img


def create_antialiased_circle_mask(size: int) -> Image.Image:
    """Generates a high-quality antialiased circular alpha mask using 4x supersampling."""
    factor = 4
    large_size = size * factor
    mask = Image.new("L", (large_size, large_size), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, large_size - 1, large_size - 1), fill=255)
    return mask.resize((size, size), Image.Resampling.LANCZOS)


def generate_android_adaptive_foreground(emblem: Image.Image) -> tuple[Image.Image, Image.Image]:
    """
    Scales and centers the emblem onto a 432x432 (108dp xxxhdpi) canvas strictly within the
    66dp (264px) diameter safe center circle. Returns (foreground, monochrome).
    """
    bbox = emblem.getbbox()
    if not bbox:
        raise ValueError("Emblem bbox is empty")

    cropped = emblem.crop(bbox)
    cw, ch = cropped.size

    # Fit within 258px target size to guarantee all non-transparent pixels stay within radius 132
    target_size = 258
    scale = float(target_size) / float(max(cw, ch))
    nw = int(round(cw * scale))
    nh = int(round(ch * scale))

    scaled = cropped.resize((nw, nh), Image.Resampling.LANCZOS)

    canvas_fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    pos_x = (432 - nw) // 2
    pos_y = (432 - nh) // 2
    canvas_fg.alpha_composite(scaled, (pos_x, pos_y))

    # Verify safe-zone circle constraint: distance from center (216, 216) <= 132.0 px
    pixels = canvas_fg.load()
    for y in range(432):
        for x in range(432):
            if pixels[x, y][3] > 0:
                dist = math.sqrt((x - 216.0) ** 2 + (y - 216.0) ** 2)
                if dist > 132.0:
                    raise RuntimeError(f"Pixel at ({x},{y}) exceeds safe zone radius: {dist:.2f} > 132.0")

    # Generate monochrome: solid white (#FFFFFF) with preserved alpha channel
    r, g, b, a = canvas_fg.split()
    white_rgb = Image.new("RGB", (432, 432), (255, 255, 255))
    canvas_mono = Image.merge("RGBA", (*white_rgb.split(), a))

    return canvas_fg, canvas_mono


def generate_ios_tinted_mask(emblem: Image.Image) -> Image.Image:
    """Generates the iOS 18 1024x1024 tinted monochrome alpha mask from the extracted emblem."""
    r, g, b, a = emblem.split()
    white_rgb = Image.new("RGB", emblem.size, (255, 255, 255))
    return Image.merge("RGBA", (*white_rgb.split(), a))


def update_android_xml_configs():
    """Updates background and adaptive icon XML definitions and cleans stale vectors."""
    # 1. Clean ic_launcher_background.xml
    bg_xml_path = full_path("androidApp/res/drawable/ic_launcher_background.xml")
    os.makedirs(os.path.dirname(bg_xml_path), exist_ok=True)
    clean_bg_content = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M0,0h108v108h-108z" />
</vector>
"""
    with open(bg_xml_path, "w", encoding="utf-8") as f:
        f.write(clean_bg_content)
    print(f"Updated {bg_xml_path}")

    # 2. Update mipmap-anydpi-v26/ic_launcher.xml
    launcher_xml_path = full_path("androidApp/res/mipmap-anydpi-v26/ic_launcher.xml")
    os.makedirs(os.path.dirname(launcher_xml_path), exist_ok=True)
    adaptive_content = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""
    with open(launcher_xml_path, "w", encoding="utf-8") as f:
        f.write(adaptive_content)
    print(f"Updated {launcher_xml_path}")

    # 3. Update mipmap-anydpi-v26/ic_launcher_round.xml
    round_xml_path = full_path("androidApp/res/mipmap-anydpi-v26/ic_launcher_round.xml")
    with open(round_xml_path, "w", encoding="utf-8") as f:
        f.write(adaptive_content)
    print(f"Updated {round_xml_path}")

    # 4. Remove stale vector ic_launcher_foreground.xml in drawable-v24
    stale_vector_path = full_path("androidApp/res/drawable-v24/ic_launcher_foreground.xml")
    if os.path.exists(stale_vector_path):
        os.remove(stale_vector_path)
        print(f"Removed stale vector {stale_vector_path}")


def update_ios_contents_json():
    """Updates iOS AppIcon.appiconset/Contents.json with light, dark, and tinted entries."""
    contents_path = full_path("iosApp/src/Assets.xcassets/AppIcon.appiconset/Contents.json")
    os.makedirs(os.path.dirname(contents_path), exist_ok=True)
    manifest = {
        "images": [
            {
                "filename": "app-icon-1024.png",
                "idiom": "universal",
                "platform": "ios",
                "size": "1024x1024",
            },
            {
                "appearances": [
                    {
                        "appearance": "luminosity",
                        "value": "dark",
                    }
                ],
                "filename": "app-icon-dark-1024.png",
                "idiom": "universal",
                "platform": "ios",
                "size": "1024x1024",
            },
            {
                "appearances": [
                    {
                        "appearance": "luminosity",
                        "value": "tinted",
                    }
                ],
                "filename": "app-icon-tinted-1024.png",
                "idiom": "universal",
                "platform": "ios",
                "size": "1024x1024",
            },
        ],
        "info": {
            "author": "xcode",
            "version": 1,
        },
    }
    with open(contents_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)
        f.write("\n")
    print(f"Updated {contents_path}")


def main():
    print("=== Fjalar App Icon Generator Pipeline ===")

    master_light_path = full_path("assets/icons/icon_master_light_1024.png")
    master_dark_path = full_path("assets/icons/icon_master_dark_1024.png")

    if not os.path.isfile(master_light_path):
        print(f"Error: Missing master light icon: {master_light_path}", file=sys.stderr)
        sys.exit(1)
    if not os.path.isfile(master_dark_path):
        print(f"Error: Missing master dark icon: {master_dark_path}", file=sys.stderr)
        sys.exit(1)

    with Image.open(master_light_path) as master_light, Image.open(master_dark_path) as master_dark:
        print("1. Extracting emblem with anti-aliased transparency...")
        emblem = extract_emblem(master_light)

        # 2. Android Adaptive Foreground & Monochrome
        print("2. Generating Android adaptive foreground and monochrome icons (432x432 xxxhdpi)...")
        fg, mono = generate_android_adaptive_foreground(emblem)
        fg_path = full_path("androidApp/res/drawable-nodpi/ic_launcher_foreground.png")
        mono_path = full_path("androidApp/res/drawable-nodpi/ic_launcher_monochrome.png")
        os.makedirs(os.path.dirname(fg_path), exist_ok=True)
        fg.save(fg_path, "PNG")
        mono.save(mono_path, "PNG")
        print(f"   Saved {fg_path}")
        print(f"   Saved {mono_path}")

        # 3. Android Play Store Master (512x512)
        print("3. Generating Play Store master icon (512x512)...")
        playstore_path = full_path("androidApp/ic_launcher-playstore.png")
        playstore_img = master_light.resize((512, 512), Image.Resampling.LANCZOS)
        playstore_img.save(playstore_path, "PNG")
        print(f"   Saved {playstore_path}")

        # 4. Android Legacy Density Mipmaps
        print("4. Generating legacy density mipmaps (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)...")
        densities = {
            "mdpi": 48,
            "hdpi": 72,
            "xhdpi": 96,
            "xxhdpi": 144,
            "xxxhdpi": 192,
        }
        for density, size in densities.items():
            dir_path = full_path(f"androidApp/res/mipmap-{density}")
            os.makedirs(dir_path, exist_ok=True)

            # Square icon
            square_icon = master_light.resize((size, size), Image.Resampling.LANCZOS)
            square_path = os.path.join(dir_path, "ic_launcher.png")
            square_icon.save(square_path, "PNG")

            # Round icon with circular mask
            circle_mask = create_antialiased_circle_mask(size)
            round_icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
            round_icon.paste(square_icon.convert("RGBA"), (0, 0), circle_mask)
            round_path = os.path.join(dir_path, "ic_launcher_round.png")
            round_icon.save(round_path, "PNG")

            print(f"   Saved {square_path} and {round_path} ({size}x{size})")

        # 5. iOS Asset Catalog Icons
        print("5. Generating iOS 18 asset catalog icons (1024x1024)...")
        ios_dir = full_path("iosApp/src/Assets.xcassets/AppIcon.appiconset")
        os.makedirs(ios_dir, exist_ok=True)

        # Light mode
        ios_light_path = os.path.join(ios_dir, "app-icon-1024.png")
        master_light.save(ios_light_path, "PNG")
        print(f"   Saved {ios_light_path}")

        # Dark mode
        ios_dark_path = os.path.join(ios_dir, "app-icon-dark-1024.png")
        master_dark.save(ios_dark_path, "PNG")
        print(f"   Saved {ios_dark_path}")

        # Tinted mode
        ios_tinted_path = os.path.join(ios_dir, "app-icon-tinted-1024.png")
        tinted_mask = generate_ios_tinted_mask(emblem)
        tinted_mask.save(ios_tinted_path, "PNG")
        print(f"   Saved {ios_tinted_path}")

    # 6. Update XML configs and clean stale vector drawables
    print("6. Updating Android XML configurations and cleaning stale drawables...")
    update_android_xml_configs()

    # 7. Update Contents.json
    print("7. Updating iOS Contents.json...")
    update_ios_contents_json()

    print("=== Pipeline Complete! ===")


if __name__ == "__main__":
    main()
