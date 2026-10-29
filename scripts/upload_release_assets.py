#!/usr/bin/env python3
"""Upload missing assets, preserving every existing release asset on retries."""

import argparse
import hashlib
import json
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory

from release_metadata import release_metadata


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def upload_assets(tag: str, directory: Path) -> None:
    release_metadata(tag, 1)  # Validate the tag before constructing paths or CLI arguments.
    apk = directory / f"fjalar-{tag}-android.apk"
    checksum = directory / "SHA256SUMS.txt"
    checksum.write_text(f"{sha256(apk)}  {apk.name}\n")
    files = [apk, checksum]
    result = subprocess.check_output(
        ["gh", "release", "view", tag, "--json", "assets"], text=True
    )
    existing = {asset["name"] for asset in json.loads(result)["assets"]}
    missing = []
    # Preflight the entire pair before uploading anything. A partial previous upload
    # can be completed, but a rebuilt/different APK must never replace a working one.
    with TemporaryDirectory(prefix="fjalar-release-compare-") as temporary:
        for path in files:
            if path.name not in existing:
                missing.append(path)
                continue
            subprocess.run(
                ["gh", "release", "download", tag, "--pattern", path.name, "--dir", temporary],
                check=True,
            )
            if sha256(Path(temporary) / path.name) != sha256(path):
                raise ValueError(
                    f"Existing release asset differs: {path.name}. Nothing was replaced. "
                    "Publish a new version or follow the explicit replacement procedure in docs/releasing.md."
                )
            print(f"Preserving identical existing asset: {path.name}")
        if missing:
            subprocess.run(
                ["gh", "release", "upload", tag, "--", *map(str, missing)], check=True
            )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("tag")
    parser.add_argument("--directory", type=Path, default=Path("dist"))
    args = parser.parse_args()
    try:
        upload_assets(args.tag, args.directory)
    except ValueError as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
