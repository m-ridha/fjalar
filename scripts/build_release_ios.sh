#!/usr/bin/env bash
set -euo pipefail

: "${RELEASE_TAG:?Missing release tag}"
: "${BUILD_NUMBER:?Missing workflow run number}"

TARGET_PLATFORM="${IOS_TARGET_PLATFORM:-iosArm64}"
export TARGET_PLATFORM

echo "==> Deriving release metadata for $RELEASE_TAG (build $BUILD_NUMBER)"
metadata=$(python3 scripts/release_metadata.py "$RELEASE_TAG" "$BUILD_NUMBER")
read -r ios_version ios_build_number < <(
    printf '%s\n' "$metadata" | python3 -c "import json, sys; d = json.load(sys.stdin); print(d['ios_version'], d['ios_build_number'])"
)

echo "==> Building iOS Release App via Kotlin toolchain (platform: $TARGET_PLATFORM)"
./kotlin build -m iosApp -p "$TARGET_PLATFORM" -v release

# Locate the built .app directory matching the target platform
app_path=$(python3 - <<'PY'
import os
from pathlib import Path

target = os.environ["TARGET_PLATFORM"]
if target == "iosArm64":
    pattern = "_iosApp_buildIosAppIosArm64Release/bin/Release-iphoneos/Fjalar.app"
elif target == "iosSimulatorArm64":
    pattern = "_iosApp_buildIosAppIosSimulatorArm64Release/bin/Release-iphonesimulator/Fjalar.app"
else:
    pattern = f"_iosApp_buildIosApp*{target}*/bin/Release-*/Fjalar.app"

apps = list(Path("build/tasks").glob(pattern))
if len(apps) != 1:
    raise SystemExit(f"Expected 1 built Fjalar.app for {target} matching {pattern}, found {len(apps)}")
print(apps[0])
PY
)

echo "==> Found built application at: $app_path"

mkdir -p dist
archive_dir="dist/fjalar-${RELEASE_TAG}.xcarchive"
ipa_path="dist/fjalar-${RELEASE_TAG}-ios-unsigned.ipa"
checksum_path="dist/SHA256SUMS-ios.txt"

# 1. Package .xcarchive
echo "==> Constructing .xcarchive bundle at: $archive_dir"
rm -rf "$archive_dir"
mkdir -p "$archive_dir/Products/Applications"
cp -R "$app_path" "$archive_dir/Products/Applications/Fjalar.app"

# Write Info.plist for .xcarchive
cat <<PLIST > "$archive_dir/Info.plist"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>ApplicationProperties</key>
	<dict>
		<key>ApplicationPath</key>
		<string>Applications/Fjalar.app</string>
		<key>CFBundleIdentifier</key>
		<string>com.muhammadridha.fjalar</string>
		<key>CFBundleShortVersionString</key>
		<string>${ios_version}</string>
		<key>CFBundleVersion</key>
		<string>${ios_build_number}</string>
	</dict>
	<key>ArchiveVersion</key>
	<integer>2</integer>
	<key>Name</key>
	<string>Fjalar</string>
	<key>SchemeName</key>
	<string>app</string>
</dict>
</plist>
PLIST

# 2. Package unsigned .ipa
echo "==> Packaging unsigned .ipa at: $ipa_path"
staging_dir=$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/fjalar-ipa.XXXXXX")
trap 'rm -rf "$staging_dir"' EXIT
mkdir -p "$staging_dir/Payload"
cp -R "$app_path" "$staging_dir/Payload/Fjalar.app"
(cd "$staging_dir" && zip -qr -9 "$OLDPWD/$ipa_path" Payload)

# 3. Compute SHA256 checksums
echo "==> Computing SHA256 checksums"
(cd dist && shasum -a 256 "$(basename "$ipa_path")" > "$(basename "$checksum_path")")

echo "==> iOS release artifacts generated successfully:"
ls -lh "$ipa_path" "$checksum_path" "$archive_dir"
