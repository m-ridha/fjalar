# GitHub release assets

The **Build Release Assets** workflow runs when a GitHub Release is **published**,
including a published pre-release. Creating a draft or pushing a tag alone does
not start it. Publish from the GitHub UI or an authenticated user/automation token;
events created by another workflow's `GITHUB_TOKEN` do not start a second workflow.

The release's **tag**, rather than its display title, must use one of these forms:

- `v0.1.0`
- `v0.1.0-debug`
- `v1.2.3-rc.1`

The general form is `vX.Y.Z` with an optional hyphen suffix containing letters,
digits, dots, or hyphens. Numeric version components cannot have leading zeroes.
Malformed tags fail validation before either build job starts.

The macOS verification job uses `macos-26` and explicitly selects Xcode 26.4
through `DEVELOPER_DIR`, matching the Kotlin/Native 2.4.20 Apple SDK baseline.
The same toolchain is selected in PR checks. Xcode 16 lacks required frameworks.

## Assets and versions

After the signed Android build and multiplatform verification both pass, the
workflow attaches these files to the existing release:

- `fjalar-vX.Y.Z[-suffix]-android.apk`: aligned, release-signed Android APK.
- `SHA256SUMS.txt`: SHA-256 checksum of the APK.
- `fjalar-vX.Y.Z[-suffix]-ios-unsigned.ipa`: unsigned physical-device iOS IPA (`iosArm64` / `Release-iphoneos`).
- `SHA256SUMS-ios.txt`: SHA-256 checksum of the unsigned iOS IPA.

The exact commit associated with the release event is checked out for validation,
building, and tests. Android uses the tag without `v` as `versionName` and this
workflow's `github.run_number` as `versionCode`. The suffix is a version label;
`-debug` still produces a **Release** build, with the persistent release key.
Ordinary local builds use the toolchain defaults unless configured otherwise. CI
writes version fields into `androidApp/module.yaml` only in its disposable
checkout. Keep this workflow's run counter when maintaining it, so future
version codes continue to increase.

The build job has read-only repository access. A separate upload job receives
`contents: write` only after the build and verification pass. No Play Store,
App Store, Firebase, or external application-data upload is performed.

## Configure Android signing once

Add these **repository Actions secrets** in Settings → Secrets and variables → Actions:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded persistent JKS/PKCS12 release keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Alias of the signing key inside the keystore |
| `ANDROID_KEY_PASSWORD` | Signing key password (same as the store password for many PKCS12 stores) |

Reuse an existing release signing key if you already distribute signed builds.
Otherwise, generate a key outside the repository using `keytool -genkeypair`,
and keep a secure backup. All future APK releases must use the same key for
users to install them as updates. A previous developer/debug build signed with a
different key cannot be updated in place with this release APK.

On macOS, the keystore can be added without placing its contents in a command argument:

```sh
base64 -i /absolute/path/to/release.jks | gh secret set ANDROID_KEYSTORE_BASE64
gh secret set ANDROID_KEYSTORE_PASSWORD
gh secret set ANDROID_KEY_ALIAS
gh secret set ANDROID_KEY_PASSWORD
```

The latter commands prompt for values. Do not commit keys or passwords. Missing
secrets fail the Android job. There is no fallback to a debug key or unsigned APK.
The decoded keystore is created in a restricted temporary directory, removed by
an exit trap, and excluded from uploaded artifacts. `zipalign` runs before
`apksigner`, then both the signature and alignment are verified.

## Publish and retry

1. Merge the workflow into `main` before making the first release. The released
   commit must contain the workflow and its helper scripts.
2. Confirm the four Android secrets are configured.
3. Create a release using a matching tag, select the intended commit, and publish
   it. A pre-release works too.
4. Check **Actions → Build Release Assets**. The release exists immediately. The
   assets appear after the workflow succeeds.
5. Download the APK/IPA and their checksum files. On Linux use `sha256sum -c SHA256SUMS.txt`
   (or `SHA256SUMS-ios.txt`); on macOS use `shasum -a 256 -c SHA256SUMS.txt` (or `SHA256SUMS-ios.txt`).

If a job fails, correct its setup and re-run the failed jobs. The same workflow
run retains its version code. Successful build artifacts are retained for seven
days. If they expire, re-run all jobs. Before uploading anything, the workflow
downloads any existing matching APK/checksum assets and compares their SHA-256
hashes with the local files. Identical assets are preserved. Only missing files
are uploaded, allowing a partial upload to finish safely. A differing asset stops
the job without replacing or deleting anything. A rebuild may produce different
bytes, so prefer retrying the upload job with its original build artifacts.

For changed content, publish a new version/tag whenever possible. If an existing
asset must be replaced, an administrator must explicitly back up the old APK and
checksum, upload and verify the candidate pair under distinct names first, and
then deliberately replace the canonical pair. Keep the backup/candidate available
until the replacement is verified. The automated workflow never performs this
destructive replacement procedure and never uses `--clobber`.

This published-release trigger requires editable release assets. If GitHub
immutable releases are enabled, use a future draft-build-then-publish flow instead;
GitHub locks assets after an immutable release is published.

## iOS release packaging

An automated packaging pipeline is provided via `scripts/build_release_ios.sh`:

```sh
RELEASE_TAG="v0.3.0" BUILD_NUMBER=1 bash scripts/build_release_ios.sh
```

This builds the physical device release app (`iosArm64` / `Release-iphoneos` by
default) via the Kotlin Multiplatform toolchain, constructs an `.xcarchive`
bundle (`dist/fjalar-${RELEASE_TAG}.xcarchive`) with metadata matching the tag
and build number, packages an unsigned `.ipa`
(`dist/fjalar-${RELEASE_TAG}-ios-unsigned.ipa`), and outputs the SHA-256 checksum
(`dist/SHA256SUMS-ios.txt`).

In GitHub Actions, the `verify` job on `macos-26` runs this packaging pipeline
and uploads the resulting unsigned archive artifacts (`release-ios-unsigned`),
after which the `publish` job attaches `fjalar-${RELEASE_TAG}-ios-unsigned.ipa`
and `SHA256SUMS-ios.txt` directly to the published GitHub Release alongside the
signed Android APK.

### Installing and Signing on iOS

Without an active Apple Developer certificate and provisioning profile, an
unsigned IPA cannot be installed on un-jailbroken iOS devices as a signed release.
However, it can be signed and deployed using standard methods:

1. **Local Xcode Deployment (Free Apple Account)**: Open `iosApp/module.xcodeproj` in
   Xcode, select your Personal Team under Signing & Capabilities, enable
   *Automatically manage signing*, and run on your connected device.
2. **Ad-Hoc / Enterprise Resigning**: Because `scripts/build_release_ios.sh`
   targets `iosArm64` (`Release-iphoneos`) by default, the generated unsigned `.ipa`
   contains a physical device binary that can be resigned with enterprise or ad-hoc
   provisioning profiles via `codesign` or `fastlane sigh resign`.
3. **App Store Distribution**: CI signing and App Store / TestFlight upload steps
   are not yet wired into `.github/workflows/release-assets.yml`. When Apple Developer
   credentials and distribution provisioning are available, a dedicated signing and
   submission step (`xcodebuild -exportArchive` / `xcrun altool` or TestFlight) must
   be added to the workflow alongside the corresponding repository secrets.

## References and fit for this repository

- [Mifos Pay reference workflow](https://github.com/openMF/mifos-pay/blob/dev/.github/workflows/multi-platform-build-and-publish.yml): delegates to a reusable Gradle/Fastlane pipeline with multiple distribution services. Fjalar uses the Kotlin Toolchain and only needs GitHub Release assets.
- [Kotlin Toolchain 0.13 Android application guide](https://kotlin-toolchain.org/0.13/user-guide/product-types/android-app/): `build` produces an APK; `package` produces an AAB. This workflow uses the repo's existing `./kotlin build` command.
- [Kotlin Toolchain 0.13 iOS application guide](https://kotlin-toolchain.org/0.13/user-guide/product-types/ios-app/): its generated Xcode project supports the standard archive/export process when signing is available.
- [GitHub release events](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#release): `published` covers both stable and pre-release publication.
- [Android apksigner](https://developer.android.com/tools/apksigner): signing and verifying the APK after alignment.
- [GitHub CLI release upload](https://cli.github.com/manual/gh_release_upload): attaches assets to the existing release.
- [Apple signing on GitHub runners](https://docs.github.com/en/actions/how-tos/deploy/deploy-to-third-party-platforms/sign-xcode-applications): signing credentials and provisioning needed for future iOS automation.
- [Apple account and Personal Team limits](https://developer.apple.com/help/account/basics/about-your-developer-account): free on-device testing, seven-day provisioning, and developer program distribution access.
- [GitHub immutable releases](https://docs.github.com/en/code-security/concepts/supply-chain-security/immutable-releases): assets cannot be appended after publication when immutability is enabled.
