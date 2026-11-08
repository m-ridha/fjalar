#!/usr/bin/env bash
set -euo pipefail

: "${RELEASE_TAG:?Missing release tag}"
: "${BUILD_NUMBER:?Missing workflow run number}"

TARGET_PLATFORM="${IOS_TARGET_PLATFORM:-iosSimulatorArm64}"

echo "==> Deriving release metadata for $RELEASE_TAG (build $BUILD_NUMBER)"
metadata=$(python3 scripts/release_metadata.py "$RELEASE_TAG" "$BUILD_NUMBER")
ios_version=$(python3 -c "import json; print(json.loads('''$metadata''')['ios_version'])")
ios_build_number=$(python3 -c "import json; print(json.loads('''$metadata''')['ios_build_number'])")

echo "==> Building iOS Release App via Kotlin toolchain (platform: $TARGET_PLATFORM)"
./kotlin build -m iosApp -p "$TARGET_PLATFORM" -v release

# Locate the built .app directory
app_path=$(python3 - <<'PY'
from pathlib import Path

# Match built release app directories
apps = list(Path("build/tasks").glob("_iosApp_buildIosApp*/bin/Release-*/Fjalar.app"))
if not apps:
    raise SystemExit("No built Fjalar.app found in build/tasks")
# Pick the most recently modified app
latest_app = max(apps, key=lambda p: p.stat().st_mtime)
print(latest_app)
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
