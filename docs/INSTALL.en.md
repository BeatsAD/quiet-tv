# Installation and build

[简体中文](INSTALL.zh-CN.md)  [Home](../README.en.md)  [Compatibility](COMPATIBILITY.md)

## Requirements

The [installation page](https://beatsad.github.io/quiet-tv/) and [latest release index](https://beatsad.github.io/quiet-tv/latest.json) automatically track published builds, including prereleases. Choose the latest APK compatible with the TV's system, supported ABIs and upgrade conditions. Android 4.4 / API19–20 needs the compatibility profile; API21+ needs an applicable standard build. Verify the actual APK metadata and read the [compatibility](COMPATIBILITY.md) limits. Historical downloads keep their links. Installing a published APK needs no JDK, Gradle or full Android SDK.

Standard requires Android 5 / API21+ and provides ARM32, ARM64 and universal fallback APKs. Android 4.4 / API19 uses a separate compatibility APK with no native libraries, traditional TCP ADB, no six-digit pairing or desktop guard, and system settings/the remote for inputs. All use targetSdk36 and package `home.quiet.tv`. Minimum API support does not establish firmware compatibility.

Source builds need Git, Python 3, JDK 17 or 21, Android SDK platform 36, build-tools 36.0.0 and platform-tools. They need no TV connection, device configuration, private tests or recovery records.

## Fresh-clone build

```sh
git clone https://github.com/BeatsAD/quiet-tv.git
cd quiet-tv
```

Set `ANDROID_HOME` or create `local.properties` with your actual SDK path:

```properties
sdk.dir=/absolute/path/to/your/android-sdk
```

```sh
./gradlew :app:assembleDebug --console=plain
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. Use `gradlew.bat` on Windows; host-specific scripts need validation. See [Contributing](../CONTRIBUTING.md).

For immutable delivery archives, configure `sdk.dir` in `local.properties`. Set `FIRST_BUILD_NUMBER` to an unused positive integer above every allocated number; 64–67 belong to published 1.2.4. A batch allocates four consecutive numbers:

```sh
python3 scripts/build_apk.py --batch --build-number "$FIRST_BUILD_NUMBER" --variant benchmark --summary "Describe this build's changes"
```

The script pins a commit and builds standard ARM32, ARM64, universal and Android 4.4 in that order, checking version, signing and native libraries. `dist/apk/` retains the immutable APKs, SHA-256 files, JSON and batch manifest. Local archive names include the build number; public names omit it. The script neither installs nor publishes. `benchmark` uses a test signer, not a stable release key. Modified content delivered after a public release needs a new software version; see [versioning](VERSIONING.md).

For one standard APK, omit `--batch` and choose `--abi armeabi-v7a`, `arm64-v8a` or `universal`. The default minimum API is 21; 23, 26 and 31 are available for actual compatibility requirements.

## Android 4.4 / API19

Use the separate profile; lowering the standard minimum alone is rejected. `LEGACY_BUILD_NUMBER` must also be unused and above every allocated number:

```sh
python3 scripts/build_apk.py --build-number "$LEGACY_BUILD_NUMBER" --compatibility legacy19 --variant benchmark --summary "Android 4.4 compatibility candidate"
```

The minimum defaults to API19. Build and Lint with `./gradlew :app:assembleDebug :app:lintDebug -PquietCompatibility=legacy19 --console=plain`. The profile retains the core launcher, weather, wallpapers, app management, child mode, About and offline licenses, with TCP ADB. Actual API19 runtime, remote navigation, HOME, networking, reboot and wake need target-TV checks. Debug uses legacy MultiDex startup support.

## Download and try

Use the [installation page](https://beatsad.github.io/quiet-tv/) or [latest release index](https://beatsad.github.io/quiet-tv/latest.json) to choose the latest compatible APK, including test builds. Each asset provides its actual `url`, size in `bytes`, and `sha256`; do not reconstruct a filename or download URL from an older document.

Calculate the downloaded file's SHA-256 and compare it with the selected asset. Set `APK_PATH` to the actual downloaded file:

```sh
shasum -a 256 "$APK_PATH"
```

Historical releases may also provide a matching `.apk.sha256`. A missing standalone checksum file is not a reason to select an older version. Verify the actual package, signer, increasing versionCode, system and data compatibility before upgrading; do not uninstall or clear data to bypass a mismatch. [Historical releases](https://github.com/BeatsAD/quiet-tv/releases) remain unchanged.

Where supported, install through the TV's file manager and confirm source authorization in system settings. For computer ADB, use the TV's actual debugging entry and approve the computer. Network ADB requires a reachable shared LAN; enabling USB debugging alone does not establish network access. See [connection and authorization, Chinese](TECHNICAL_PATH_WITH_CODEX.zh-CN.md#二连接电视读取真实设备能力).

Read the current user and command help first; verify `--user` support and set `TV_SERIAL` and `TV_USER` to the actual target. Set `APK_PATH` to the actual downloaded and verified APK. The following address and user 0 are examples:

```sh
TV_SERIAL=192.0.2.10:5555
TV_USER=0
adb connect "$TV_SERIAL"
adb -s "$TV_SERIAL" shell am get-current-user
adb -s "$TV_SERIAL" install -r --user "$TV_USER" "$APK_PATH"
adb -s "$TV_SERIAL" shell am start --user "$TV_USER" -n home.quiet.tv/.HomeActivity
```

Read back the version and pull the installed APK to compare its SHA-256. `Success` alone does not establish a verified update. Current source opens Home directly on fresh installs and updates, without an automatic setup wizard. Keep desktop configuration already completed by the AI tool; otherwise try the app before configuring HOME with authorization. Older APKs may still show a wizard; choose temporary use or skip app management. Updates should retain existing HOME, wallpaper, ordering and app states, with actual checks. App management and stock-home recovery remain available on request. Default HOME, the physical Home button, reboot and standby are separate acceptance items. See [setup and recovery](SETUP.md).

If the installed version has an update entry, open App Management → About → Check for updates, download and confirm installation. The signed 1.2.4 feed is live; a higher build of the same software version is not offered as an update. Older versions without that entry need a manual installation first. See [in-app updates](UPDATES.md).
