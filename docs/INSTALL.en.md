# Installation and build

[简体中文](INSTALL.zh-CN.md)  [Home](../README.en.md)  [Compatibility](COMPATIBILITY.md)

## Requirements

The current public release is [1.2.4 test builds](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.2.4). Installing a published APK needs no JDK, Gradle or full Android SDK.

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

Choose one complete APK from the [1.2.4 release](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.2.4):

| System | Architecture | Internal build | Download | Size |
| --- | --- | --- | --- | --- |
| Android 5+ | ARM32 | 64 | [QuietTV_1.2.4_Android5plus_ARM32_Test.apk](https://github.com/BeatsAD/quiet-tv/releases/download/v1.2.4/QuietTV_1.2.4_Android5plus_ARM32_Test.apk) | 3.74 MiB |
| Android 5+ | ARM64 | 65 | [QuietTV_1.2.4_Android5plus_ARM64_Test.apk](https://github.com/BeatsAD/quiet-tv/releases/download/v1.2.4/QuietTV_1.2.4_Android5plus_ARM64_Test.apk) | 3.87 MiB |
| Android 5+ | Universal | 66 | [QuietTV_1.2.4_Android5plus_Universal_Test.apk](https://github.com/BeatsAD/quiet-tv/releases/download/v1.2.4/QuietTV_1.2.4_Android5plus_Universal_Test.apk) | 7.04 MiB |
| Android 4.4 | No native libraries | 67 | [QuietTV_1.2.4_Android4.4_Test.apk](https://github.com/BeatsAD/quiet-tv/releases/download/v1.2.4/QuietTV_1.2.4_Android4.4_Test.apk) | 3.95 MiB |

Choose by OS-supported ABIs; a 64-bit processor may run a 32-bit OS. Prefer a matching single-architecture APK, or the Android 5+ universal fallback when uncertain. Install one per device; the largest build number is not a universal recommendation.

GitHub uses English download filenames and Chinese labels. New releases upload APKs only. Compare the file's SHA-256 with its asset `digest` in the [GitHub release API](https://api.github.com/repos/BeatsAD/quiet-tv/releases/tags/v1.2.4); no separate checksum download is required. For example:

```sh
shasum -a 256 QuietTV_1.2.4_Android5plus_ARM32_Test.apk
```

Historical assets, links and checksum files remain unchanged. Check the actual package, version, build number, minSdk, ABI and signer before installation. Updates require the same package/signer and a higher internal versionCode. A higher-build universal APK cannot be replaced with a lower-build ARM APK. Retain the installation if no compatible upgrade exists; do not uninstall, clear data or force a downgrade.

Where supported, install through the TV's file manager and confirm source authorization in system settings. For computer ADB, use the TV's actual debugging entry and approve the computer. Network ADB requires a reachable shared LAN; enabling USB debugging alone does not establish network access. See [connection and authorization, Chinese](TECHNICAL_PATH_WITH_CODEX.zh-CN.md#二连接电视读取真实设备能力).

Read the current user and command help first; verify `--user` support and set `TV_SERIAL` and `TV_USER` to the actual target. The following address and user 0 are examples:

```sh
TV_SERIAL=192.0.2.10:5555
TV_USER=0
adb connect "$TV_SERIAL"
adb -s "$TV_SERIAL" shell am get-current-user
adb -s "$TV_SERIAL" install -r --user "$TV_USER" QuietTV_1.2.4_Android5plus_ARM32_Test.apk
adb -s "$TV_SERIAL" shell am start --user "$TV_USER" -n home.quiet.tv/.HomeActivity
```

Read back the version and pull the installed APK to compare its SHA-256. `Success` alone does not establish a verified update. Fresh installs should start with temporary use; updates should retain existing HOME, wallpaper, ordering and app states, with actual checks. Default HOME, the physical Home button, reboot and standby are separate acceptance items. See [setup and recovery](SETUP.md).

If the installed version has an update entry, open App Management → About → Check for updates, download and confirm installation. The signed 1.2.4 feed is live; a higher build of the same software version is not offered as an update. Older versions without that entry need a manual installation first. See [in-app updates](UPDATES.md).
