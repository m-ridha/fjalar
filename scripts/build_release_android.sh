#!/usr/bin/env bash
set -euo pipefail

: "${RELEASE_TAG:?Missing release tag}"
: "${BUILD_NUMBER:?Missing workflow run number}"
: "${ANDROID_KEYSTORE_BASE64:?Set the ANDROID_KEYSTORE_BASE64 repository secret}"
: "${ANDROID_KEYSTORE_PASSWORD:?Set the ANDROID_KEYSTORE_PASSWORD repository secret}"
: "${ANDROID_KEY_ALIAS:?Set the ANDROID_KEY_ALIAS repository secret}"
: "${ANDROID_KEY_PASSWORD:?Set the ANDROID_KEY_PASSWORD repository secret}"
: "${ANDROID_HOME:?Android SDK path is required}"

python3 scripts/release_metadata.py "$RELEASE_TAG" "$BUILD_NUMBER" --android-module androidApp/module.yaml
./kotlin build -m androidApp -p android -v release

umask 077
signing_dir=$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/fjalar-signing.XXXXXX")
trap 'rm -rf "$signing_dir"' EXIT
export RELEASE_KEYSTORE_PATH="$signing_dir/release.jks"
python3 - <<'PY'
import base64
import os
from pathlib import Path

encoded = "".join(os.environ["ANDROID_KEYSTORE_BASE64"].split())
Path(os.environ["RELEASE_KEYSTORE_PATH"]).write_bytes(base64.b64decode(encoded, validate=True))
PY

# Scope discovery to this module's release task, excluding debug/test/old build artifacts.
apk_path=$(python3 - <<'PY'
from pathlib import Path

apks = list(Path("build/tasks/_androidApp_buildAndroidRelease").glob("*.apk"))
if len(apks) != 1:
    raise SystemExit(f"Expected exactly one release APK, found {len(apks)}")
print(apks[0])
PY
)
build_tools="$ANDROID_HOME/build-tools/37.0.0"
mkdir -p dist
asset="dist/fjalar-${RELEASE_TAG}-android.apk"
"$build_tools/zipalign" -f -P 16 4 "$apk_path" "$signing_dir/aligned.apk"
"$build_tools/apksigner" sign \
    --ks "$RELEASE_KEYSTORE_PATH" \
    --ks-key-alias "$ANDROID_KEY_ALIAS" \
    --ks-pass env:ANDROID_KEYSTORE_PASSWORD \
    --key-pass env:ANDROID_KEY_PASSWORD \
    --out "$asset" "$signing_dir/aligned.apk"
"$build_tools/apksigner" verify --verbose "$asset"
"$build_tools/zipalign" -c -P 16 4 "$asset"
# Signing may produce an .idsig sidecar. Only the APK is a release asset.
rm -f "${asset}.idsig"
