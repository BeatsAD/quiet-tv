# Installation and build

[简体中文](INSTALL.zh-CN.md) · [Home](../README.en.md) · [Compatibility](COMPATIBILITY.md)

## Requirements

The [installation page](https://beatsad.github.io/quiet-tv/) and [latest release index](https://beatsad.github.io/quiet-tv/latest.json) automatically track published builds, including prereleases. Choose the latest APK compatible with the TV's system, supported ABIs and upgrade conditions. Android 4.4 / API19–20 needs the compatibility profile; API21+ needs an applicable standard build. Verify the actual APK metadata and read the [compatibility](COMPATIBILITY.md) limits. Historical downloads keep their links.

Install Git, Python 3, JDK 17 or 21, Android SDK platform 36, build-tools 36.0.0 and platform-tools. Builds need no TV, device configuration, private tests or recovery records.

## Fresh-clone build

Before archiving, set `STANDARD_BUILD_NUMBER` to an unused positive integer above all allocated delivery numbers, and `LEGACY_BUILD_NUMBER` to a separate unused number higher than it. The two new APKs must not reuse a build number. Published 1.1.2 builds 34 and 35 are occupied.

```sh
git clone https://github.com/BeatsAD/quiet-tv.git
cd quiet-tv
```

Set `ANDROID_HOME` or create local.properties with your actual SDK path:

```properties
sdk.dir=/absolute/path/to/your/android-sdk
```

```sh
./gradlew :app:assembleDebug --console=plain
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. Use gradlew.bat on Windows; host-specific Python and device workflows need their own validation. See [Contributing](../CONTRIBUTING.md) for checks.

For immutable archives, configure `sdk.dir` in local.properties and run:

```sh
python3 scripts/build_apk.py --build-number "$STANDARD_BUILD_NUMBER" --summary "Describe this build's changes"
```

A historical 1.1.2 standard archive example is `dist/apk/QuietTV_1.1.2-34_安卓5及以上_测试版.apk`; new builds use the requested number and also produce matching `.apk.sha256` and `.json` files. Choose a higher unused `--build-number` for every new delivery. Local archive names retain the actual APK versionCode; public names omit it and are recorded in `public_apk`, without creating additional copies. New names use short system labels such as `安卓5+`, explicit ARM32/ARM64 or `通用` architecture labels (omitted only for native-library-free APKs), and the `测试` status. Historical archives keep their original names. Options include `--variant benchmark --abi armeabi-v7a` and `--min-sdk 21` (default), `23`, `26` or `31`. See [versioning](VERSIONING.md). These commands never install to a TV.

## Android 4.4 / API19

Build and archive the separate compatibility profile. First set `LEGACY_BUILD_NUMBER` higher than the standard APK and every other archived delivery:

```sh
python3 scripts/build_apk.py --build-number "$LEGACY_BUILD_NUMBER" --compatibility legacy19 --variant benchmark --summary "Android 4.4 compatibility candidate"
```

A historical 1.1.2 legacy archive example is `dist/apk/QuietTV_1.1.2-35_安卓4.4_测试版.apk`. The profile defaults to API19; lowering the standard profile's minimum alone is rejected. For build and Lint, run `./gradlew :app:assembleDebug :app:lintDebug -PquietCompatibility=legacy19 --console=plain`.

The legacy profile retains the launcher, Dock, wallpaper, app list, native About and offline licenses, with traditional TCP ADB only. Six-digit pairing is absent. Inputs open system settings with a prompt to use the remote input key; vendor-specific direct switching needs separate adaptation. The APK contains no native libraries. ARM32 runtime, real HOME, network changes and reboot/wake still require target hardware validation. Debug uses legacy MultiDex startup support.

Both profiles share the application ID and test key and replace each other. Preserve the previous APK and configuration; do not uninstall to resolve a version or signature mismatch.

## Download and try

Use the [installation page](https://beatsad.github.io/quiet-tv/) or [latest release index](https://beatsad.github.io/quiet-tv/latest.json) to choose the latest compatible APK, including test builds. Each asset provides its actual `url`, size in `bytes`, and `sha256`; do not reconstruct a filename or download URL from an older document.

Calculate the downloaded file's SHA-256 and compare it with the selected asset. Set `APK_PATH` to the actual downloaded file:

```sh
shasum -a 256 "$APK_PATH"
```

Historical releases may also provide a matching `.apk.sha256`. A missing standalone checksum file is not a reason to select an older version. Verify the actual package, signer, increasing versionCode, system and data compatibility before upgrading; do not uninstall or clear data to bypass a mismatch. [Historical releases](https://github.com/BeatsAD/quiet-tv/releases) remain unchanged.

Enable supported debugging on your TV and authorize the computer. Replace the documentation-only address below. The example installs a locally built Debug APK; for a downloaded APK, substitute its actual file path:

```sh
TV_SERIAL=192.0.2.10:5555
adb connect "$TV_SERIAL"
adb -s "$TV_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$TV_SERIAL" shell am start -n home.quiet.tv/.HomeActivity
```

Updates require a matching signature and an increasing internal versionCode; filenames include the actual versionName and versionCode, and About shows versionName. Do not uninstall merely to resolve a signature mismatch: uninstalling clears private settings and recovery records. Verify the old package and backups first.

Fresh installs open [setup](SETUP.md); temporary use lets you check remote navigation and shortcuts first. The recovery entry is under 应用管理 → 更多. See [app management](APP_MANAGEMENT.md) for operation limits. Unknown firmware must not inherit another device’s debloating list.
