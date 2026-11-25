#!/usr/bin/env python3
"""
scripts/generate_splash_assets.py

Generates native vector launch-screen artwork from the approved Light/Dark SVG masters:
1. Android splash VectorDrawables (288x288 dp viewport) in drawable / drawable-night.
2. iOS launch-screen single-scale vector SVGs (200x200 pt) in LaunchIcon.imageset (light + dark).

Android 12+ (and the compat splash below it) paints only the central two thirds of the 288 dp
canvas inside a circular mask: a 192 dp circle, i.e. a 96 dp radius. All emblem curves are
scaled and re-centred around the canvas midpoint so the outermost curve point stays inside
`SAFE_RADIUS_RATIO` of that visible circle. The same ratio is used for the iOS launch vector so
the mark reads at the same relative size on both platforms, and `scripts/test_generated_splash_assets.py`
enforces the bound.
"""

import json
import math
import os
import re
import sys
import xml.etree.ElementTree as ElementTree

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

ANDROID_VIEWPORT_SIZE = 288
IOS_POINT_SIZE = 200

# Android 12+ shows the central 192 dp of the 288 dp splash canvas inside a circle (radius 96 dp).
VISIBLE_RADIUS_RATIO = 1.0 / 3.0
# Leave a 10% margin inside the mask so no emblem curve touches the clipped edge.
SAFE_RADIUS_RATIO = VISIBLE_RADIUS_RATIO * 0.9

BEZIER_SAMPLES = 64
COORD_DECIMALS = 3

LIGHT_MASTER = "assets/icons/icon_master_light.svg"
DARK_MASTER = "assets/icons/icon_master_dark.svg"

ANDROID_LIGHT_OUT = "androidApp/res/drawable/ic_splash_icon.xml"
ANDROID_DARK_OUT = "androidApp/res/drawable-night/ic_splash_icon.xml"
IOS_IMAGESET = "iosApp/src/Assets.xcassets/LaunchIcon.imageset"
IOS_COLORSET = "iosApp/src/Assets.xcassets/LaunchBackground.colorset"

STALE_RASTER_OUTPUTS = (
    "androidApp/res/drawable-nodpi/ic_splash_icon.png",
    "androidApp/res/drawable-night-nodpi/ic_splash_icon.png",
    f"{IOS_IMAGESET}/launch_icon_light.png",
    f"{IOS_IMAGESET}/launch_icon_dark.png",
)

SVG_NS = "{http://www.w3.org/2000/svg}"
SUPPORTED_COMMANDS = frozenset("MmLlCcZz")
TOKEN_RE = re.compile(
    r"([A-Za-z])"
    r"|([+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?)"
    r"|([\s,]+)"
)


def full_path(rel_path: str) -> str:
    return os.path.join(REPO_ROOT, rel_path)


def format_num(value: float, decimals: int = COORD_DECIMALS) -> str:
    text = f"{value:.{decimals}f}".rstrip("0").rstrip(".")
    return "0" if text in ("", "-0") else text


def parse_viewbox(root: ElementTree.Element) -> tuple[float, float]:
    viewbox = root.attrib.get("viewBox", "")
    parts = [float(token) for token in viewbox.replace(",", " ").split() if token]
    if len(parts) != 4 or parts[0] != 0.0 or parts[1] != 0.0:
        raise ValueError(f"Expected '0 0 width height' viewBox, got '{viewbox}'")
    return parts[2], parts[3]


def load_master_svg(master_rel_path: str) -> tuple[float, float, str, list[tuple[str, str]]]:
    """Returns (width, height, background_hex, [(fill_hex, path_d), ...]) from a master SVG."""
    root = ElementTree.parse(full_path(master_rel_path)).getroot()
    width, height = parse_viewbox(root)

    background_hex = None
    for rect in root.iter(f"{SVG_NS}rect"):
        if rect.attrib.get("id") == "background":
            background_hex = rect.attrib.get("fill", "").strip().upper()
            break
    if not background_hex:
        raise ValueError(f"{master_rel_path} is missing <rect id='background' fill='...'/>")

    emblem_group = None
    for group in root.iter(f"{SVG_NS}g"):
        if group.attrib.get("id") == "emblem":
            emblem_group = group
            break
    if emblem_group is None:
        raise ValueError(f"{master_rel_path} is missing <g id='emblem'>")

    paths: list[tuple[str, str]] = []
    for path_el in emblem_group.findall(f"{SVG_NS}path"):
        fill = path_el.attrib.get("fill", "").strip().upper()
        d_attr = path_el.attrib.get("d", "").strip()
        if fill and d_attr:
            paths.append((fill, d_attr))
    if not paths:
        raise ValueError(f"{master_rel_path} has no <path> elements inside <g id='emblem'>")

    return width, height, background_hex, paths


def parse_path_commands(d_str: str) -> list[tuple[str, list[float]]]:
    """Tokenizes and validates an SVG/VectorDrawable path into `(command, values)` pairs."""
    commands: list[tuple[str, list[float]]] = []
    current_command: str | None = None
    values: list[float] = []
    pos = 0

    def flush() -> None:
        if current_command is None:
            return
        upper = current_command.upper()
        if upper == "Z":
            if values:
                raise ValueError(f"Command '{current_command}' does not accept coordinates")
        elif upper in ("M", "L"):
            if not values or len(values) % 2 != 0:
                raise ValueError(f"Command '{current_command}' requires coordinate pairs")
        elif upper == "C":
            if not values or len(values) % 6 != 0:
                raise ValueError(f"Command '{current_command}' requires 6-value cubic Bezier tuples")
        commands.append((current_command, list(values)))

    for match in TOKEN_RE.finditer(d_str):
        if match.start() != pos:
            snippet = d_str[pos:match.start()]
            raise ValueError(f"Unexpected path token near '{snippet}'")
        pos = match.end()
        cmd_token, num_token, sep_token = match.groups()
        if sep_token:
            continue
        if cmd_token:
            if cmd_token not in SUPPORTED_COMMANDS:
                raise ValueError(f"Unsupported path command '{cmd_token}'")
            flush()
            current_command = cmd_token
            values = []
        elif num_token:
            if current_command is None:
                raise ValueError(f"Coordinate '{num_token}' appeared before any path command")
            values.append(float(num_token))

    if pos != len(d_str):
        snippet = d_str[pos:]
        raise ValueError(f"Unexpected path token near '{snippet}'")
    flush()
    return commands


def sample_path_points(d_str: str, samples: int = BEZIER_SAMPLES) -> list[tuple[float, float]]:
    """Samples vertices and cubic Bezier points along an SVG/VectorDrawable path string."""
    points: list[tuple[float, float]] = []
    current = (0.0, 0.0)
    subpath_start = (0.0, 0.0)

    for command, values in parse_path_commands(d_str):
        upper = command.upper()
        is_rel = command != upper
        if upper == "Z":
            current = subpath_start
            continue
        if upper in ("M", "L"):
            for index in range(0, len(values), 2):
                nx = values[index] + (current[0] if is_rel else 0.0)
                ny = values[index + 1] + (current[1] if is_rel else 0.0)
                current = (nx, ny)
                if upper == "M" and index == 0:
                    subpath_start = current
                points.append(current)
        elif upper == "C":
            for index in range(0, len(values), 6):
                ox = current[0] if is_rel else 0.0
                oy = current[1] if is_rel else 0.0
                p0 = current
                p1 = (values[index] + ox, values[index + 1] + oy)
                p2 = (values[index + 2] + ox, values[index + 3] + oy)
                p3 = (values[index + 4] + ox, values[index + 5] + oy)
                for step in range(1, samples + 1):
                    t = step / float(samples)
                    mt = 1.0 - t
                    bx = (
                        (mt ** 3) * p0[0]
                        + 3.0 * (mt ** 2) * t * p1[0]
                        + 3.0 * mt * (t ** 2) * p2[0]
                        + (t ** 3) * p3[0]
                    )
                    by = (
                        (mt ** 3) * p0[1]
                        + 3.0 * (mt ** 2) * t * p1[1]
                        + 3.0 * mt * (t ** 2) * p2[1]
                        + (t ** 3) * p3[1]
                    )
                    points.append((bx, by))
                current = p3
    return points


def max_radius_from_center(paths: list[tuple[str, str]], width: float, height: float) -> float:
    """Largest radial distance from (width / 2, height / 2) across all sampled emblem curves."""
    centre_x = width / 2.0
    centre_y = height / 2.0
    largest_sq = 0.0
    for _, d_str in paths:
        for px, py in sample_path_points(d_str):
            dx = px - centre_x
            dy = py - centre_y
            dist_sq = dx * dx + dy * dy
            if dist_sq > largest_sq:
                largest_sq = dist_sq
    return math.sqrt(largest_sq)


def transform_path_data(d_str: str, scale: float, tx: float, ty: float) -> str:
    """Applies uniform scale + translation `(x * scale + tx, y * scale + ty)` to path data."""
    parts: list[str] = []
    for cmd_index, (command, values) in enumerate(parse_path_commands(d_str)):
        upper = command.upper()
        if upper == "Z":
            parts.append("Z")
            continue
        is_rel = command != upper
        is_initial_rel_moveto = command == "m" and cmd_index == 0
        out_coords: list[str] = []
        for index, raw in enumerate(values):
            # In SVG, an initial relative `m` starts at (0, 0) (absolute), while later
            # pairs inside that `m` are implicit relative `l` offsets.
            should_translate = (not is_rel) or (is_initial_rel_moveto and index < 2)
            transformed = raw * scale + ((tx if index % 2 == 0 else ty) if should_translate else 0.0)
            out_coords.append(format_num(transformed))
        parts.append(f"{command} {' '.join(out_coords)}")
    return " ".join(parts)


def scale_emblem_paths(master_rel_path: str, target_size: float) -> list[tuple[str, str]]:
    """Scales and centers a master SVG's emblem paths inside `target_size * SAFE_RADIUS_RATIO`."""
    width, height, _, paths = load_master_svg(master_rel_path)
    measured_radius = max_radius_from_center(paths, width, height)
    if measured_radius <= 0.0:
        raise SystemExit(f"{master_rel_path} has no visible emblem geometry")

    target_radius = target_size * SAFE_RADIUS_RATIO
    scale = target_radius / measured_radius
    tx = (target_size / 2.0) - (width / 2.0) * scale
    ty = (target_size / 2.0) - (height / 2.0) * scale
    return [(fill, transform_path_data(d_str, scale, tx, ty)) for fill, d_str in paths]


def render_android_vector_xml(master_rel_path: str, viewport_size: int = ANDROID_VIEWPORT_SIZE) -> str:
    """Renders an Android VectorDrawable XML string for `Theme.SplashScreen`."""
    scaled_paths = scale_emblem_paths(master_rel_path, float(viewport_size))
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{viewport_size}dp"',
        f'    android:height="{viewport_size}dp"',
        f'    android:viewportWidth="{viewport_size}"',
        f'    android:viewportHeight="{viewport_size}">',
    ]
    for fill, path_data in scaled_paths:
        lines.extend(
            [
                "    <path",
                f'        android:fillColor="{fill}"',
                f'        android:pathData="{path_data}" />',
            ]
        )
    lines.append("</vector>\n")
    return "\n".join(lines)


def render_ios_launch_svg(master_rel_path: str, point_size: int = IOS_POINT_SIZE) -> str:
    """Renders a transparent single-scale SVG for iOS `LaunchIcon.imageset`."""
    scaled_paths = scale_emblem_paths(master_rel_path, float(point_size))
    lines = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{point_size}" height="{point_size}" '
        f'viewBox="0 0 {point_size} {point_size}">'
    ]
    for fill, path_data in scaled_paths:
        lines.append(f'  <path fill="{fill}" d="{path_data}"/>')
    lines.append("</svg>\n")
    return "\n".join(lines)


def write_text(content: str, rel_path: str) -> None:
    out_path = full_path(rel_path)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as handle:
        handle.write(content)


def remove_stale_rasters() -> None:
    for rel_path in STALE_RASTER_OUTPUTS:
        target = full_path(rel_path)
        if os.path.exists(target):
            os.remove(target)
        parent = os.path.dirname(target)
        if os.path.isdir(parent) and not os.listdir(parent):
            os.rmdir(parent)


def write_ios_metadata() -> None:
    contents = {
        "images": [
            {"filename": "launch_icon_light.svg", "idiom": "universal"},
            {
                "appearances": [{"appearance": "luminosity", "value": "dark"}],
                "filename": "launch_icon_dark.svg",
                "idiom": "universal",
            },
        ],
        "info": {"author": "xcode", "version": 1},
        "properties": {"preserves-vector-representation": True},
    }
    write_text(json.dumps(contents, indent=2) + "\n", f"{IOS_IMAGESET}/Contents.json")

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
    write_text(json.dumps(colorset, indent=2) + "\n", f"{IOS_COLORSET}/Contents.json")


def main() -> int:
    remove_stale_rasters()
    write_text(render_android_vector_xml(LIGHT_MASTER), ANDROID_LIGHT_OUT)
    write_text(render_android_vector_xml(DARK_MASTER), ANDROID_DARK_OUT)
    write_text(render_ios_launch_svg(LIGHT_MASTER), f"{IOS_IMAGESET}/launch_icon_light.svg")
    write_text(render_ios_launch_svg(DARK_MASTER), f"{IOS_IMAGESET}/launch_icon_dark.svg")
    write_ios_metadata()
    return 0


if __name__ == "__main__":
    sys.exit(main())
