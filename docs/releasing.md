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

## Assets and versions

After the signed Android build and multiplatform verification both pass, the
workflow attaches these files to the existing release:

- `fjalar-vX.Y.Z[-suffix]-android.apk`: aligned, release-signed Android APK.
- `SHA256SUMS.txt`: SHA-256 checksum of the APK.

The exact commit associated with the release event is checked out for validation,
building, and tests. Android uses the tag without `v` as `versionName` and this
workflow's `github.run_number` as `versionCode`. The suffix is a version label;
`-debug` still produces a **Release** build, with the persistent release key.
Ordinary local builds retain the versions in `androidApp/module.yaml`; CI updates
only its disposable checkout. Keep this workflow's run counter when maintaining
it, so future version codes continue to increase.

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
secrets fail the Android job; there is no fallback to a debug key or unsigned APK.
The decoded keystore is created in a restricted temporary directory, removed by
an exit trap, and excluded from uploaded artifacts. `zipalign` runs before
`apksigner`, then both the signature and alignment are verified.

## Publish and retry

1. Merge the workflow into `main` before making the first release. The released
   commit must contain the workflow and its helper scripts.
2. Confirm the four Android secrets are configured.
3. Create a release using a matching tag, select the intended commit, and publish
   it. A pre-release works too.
4. Check **Actions → Build Release Assets**. The release exists immediately; the
   assets appear after the workflow succeeds.
5. Download the APK and checksum file. On Linux use `sha256sum -c SHA256SUMS.txt`;
   on macOS use `shasum -a 256 -c SHA256SUMS.txt`.

If a job fails, correct its setup and re-run the failed jobs. The same workflow
run retains its version code. Successful build artifacts are retained for seven
days; if they expire, re-run all jobs. Upload uses `--clobber` to replace only the
same named APK/checksum assets on retry, without changing the release notes or
other assets. If a partial upload occurs, a successful retry replaces the pair.

This published-release trigger requires editable release assets. If GitHub
immutable releases are enabled, use a future draft-build-then-publish flow instead;
GitHub locks assets after an immutable release is published.

## iOS release packaging is disabled for now

iOS apps use **IPA**, not APK. Automatic Signing still requires an Apple account,
a signing identity, and provisioning. Without that setup, an unsigned IPA can be
packaged but cannot be installed as a normally signed iOS application. This
workflow intentionally publishes **no iOS asset**, as requested, until Apple
signing is available. The macOS verification job still runs the existing
multiplatform tests and checks; it does not export or publish an IPA.

Your free Apple Account can still be used locally: open `iosApp/module.xcodeproj`
in Xcode, select your Personal Team under Signing & Capabilities, enable
Automatically manage signing, and build/run on your own connected device.
Personal Team provisioning expires after seven days, so the app needs rebuilding
and reinstalling periodically. It is intended for personal device testing, not
public release distribution. A GitHub-hosted runner is a separate machine and
does not inherit the signed-in account or keychain from your local Xcode.

To enable iOS publication later:

1. Set up Apple Developer signing and the registered application identifier. The
   current Xcode project still uses a generated placeholder bundle identifier;
   align it with `com.muhammadridha.fjalar` and the Apple registration first.
2. Configure the team and CI signing credentials securely, using Automatic Signing
   with provisioning updates rather than relying on a logged-in local Xcode account.
3. Add a device Release archive/export job using `module.xcodeproj`, not a
   simulator `.app`. Set the numeric marketing version from `X.Y.Z` and a valid,
   increasing Apple build version. Keep suffixes in the asset filename.
4. Choose the intended Apple distribution method. An IPA for registered test
   devices is not a universally installable public download.
5. Gate the upload on that job, then include its IPA and checksum in the release.

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
