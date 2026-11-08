#!/usr/bin/env python3
"""Validate release tags and derive versions without requiring YAML dependencies."""

import argparse
import json
from pathlib import Path
import re


TAG_PATTERN = re.compile(
    r"v(?P<version>(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*))"
    r"(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?"
)


def release_metadata(tag: str, build_number: int) -> dict[str, str]:
    match = TAG_PATTERN.fullmatch(tag)
    if match is None:
        raise ValueError("Release tag must be vX.Y.Z or vX.Y.Z-suffix (for example v0.1.0-debug).")
    if not 1 <= build_number <= 2_100_000_000:
        raise ValueError("Build number must be between 1 and Android's maximum versionCode (2100000000).")
    return {
        "tag": tag,
        "android_version": tag[1:],
        "version_code": str(build_number),
        "ios_version": match.group("version"),
        "ios_build_number": str(build_number),
    }


def configure_android(module: Path, metadata: dict[str, str]) -> None:
    source = module.read_text()
    lines = source.splitlines(keepends=True)
    android_sections = [index for index, line in enumerate(lines) if line.rstrip() == "  android:"]
    if len(android_sections) != 1 or "settings:\n" not in source:
        raise ValueError("Expected one settings.android section in the application module.")
    start = android_sections[0] + 1
    end = start
    while end < len(lines):
        line = lines[end]
        if line.strip() and not line.startswith("    "):
            break
        end += 1
    if any(re.match(r"    (?:versionName|versionCode|signing):", line) for line in lines[start:end]):
        raise ValueError("Android version/signing settings already exist; update configure_android before overriding them.")
    # This is a disposable CI checkout; ordinary developer builds keep their existing versions.
    module.write_text(
        "".join(lines[:end]).rstrip() + "\n"
        f"    versionName: {json.dumps(metadata['android_version'])}\n"
        f"    versionCode: {metadata['version_code']}\n"
        "    signing:\n      enabled: false\n" + "".join(lines[end:])
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("tag")
    parser.add_argument("build_number", type=int)
    parser.add_argument("--android-module", type=Path)
    args = parser.parse_args()
    try:
        metadata = release_metadata(args.tag, args.build_number)
        if args.android_module:
            configure_android(args.android_module, metadata)
    except ValueError as error:
        parser.error(str(error))
    print(json.dumps(metadata))


if __name__ == "__main__":
    main()
