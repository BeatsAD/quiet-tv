# Installation and build

[简体中文](INSTALL.zh-CN.md) · [Home](../README.en.md) · [Compatibility](COMPATIBILITY.md)

## Requirements

Source is 1.1.0 / b30: the default standard profile requires Android 5.0 / API21, and `legacy19` requires Android 4.4 / API19. Both target API36. The public download remains b26 / API21; b30 is not published. The download is about 6.50 MiB and includes ARM32, ARM64, x86 and x86_64. Selected emulator checks passed; physical TV acceptance remains pending. See [compatibility](COMPATIBILITY.md). Historical b24 still requires Android 6.0 and b23 requires Android 12 with ARM32 support; both remain available. Building source does not establish hardware acceptance.

Install Git, Python 3, JDK 17 or 21, Android SDK platform 36, build-tools 36.0.0 and platform-tools. Builds need no TV, device configuration, private tests or recovery records.

## Fresh-clone build

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
python3 scripts/build_apk.py --summary "Describe this build's changes"
```

The default archive is `dist/apk/quiet-tv_1.1.0_b30_debug_api21.apk`. Options include `--variant benchmark --abi armeabi-v7a`, `--min-sdk 21` (default), `23`, `26` and `31`. See [versioning](VERSIONING.md) for naming and signatures. These commands never install to a TV.

## Android 4.4 / API19

Build and archive the separate compatibility profile:

```sh
python3 scripts/build_apk.py --compatibility legacy19 --variant benchmark --abi armeabi-v7a --summary "Android 4.4 compatibility candidate"
```

Output: `dist/apk/quiet-tv_1.1.0_b30_benchmark_legacy19_armeabi-v7a_api19.apk`. The profile defaults to API19; lowering the standard profile's minimum alone is rejected. For build and Lint, run `./gradlew :app:assembleDebug :app:lintDebug -PquietCompatibility=legacy19 --console=plain`.

The legacy profile retains the launcher, Dock, wallpaper, app list, native About and offline licenses, with traditional TCP ADB only. Six-digit pairing is absent. Inputs open system settings with a prompt to use the remote input key; vendor-specific direct switching needs separate adaptation. The APK contains no native libraries. ARM32 runtime, real HOME, network changes and reboot/wake still require target hardware validation. Debug uses legacy MultiDex startup support.

Both profiles share the application ID and test key and replace each other. Preserve the previous APK and configuration; do not uninstall to resolve a version or signature mismatch.

## Download and try

[b26 Releases](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b26) provides `quiet-tv_1.1.0_b26_benchmark_api21.apk` and its SHA-256 file. Put both in the same directory and run:

```sh
shasum -a 256 -c quiet-tv_1.1.0_b26_benchmark_api21.apk.sha256
```

The published APK uses the same test signature as b24. Historical [b23 Releases](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0) requires Android 12+ and ARM32 app support; those requirements do not apply to b26.

Enable supported debugging on your TV and authorize the computer. Replace the documentation-only address below. The example installs a locally built Debug APK; for a downloaded APK, substitute its actual file path:

```sh
TV_SERIAL=192.0.2.10:5555
adb connect "$TV_SERIAL"
adb -s "$TV_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$TV_SERIAL" shell am start -n home.quiet.tv/.HomeActivity
```

Updates require a matching signature and non-decreasing build number. Do not uninstall merely to resolve a signature mismatch: uninstalling clears private settings and recovery records. Verify the old package and backups first.

Fresh installs open [setup](SETUP.md); temporary use lets you check remote navigation and shortcuts first. The recovery entry is under 应用管理 → 更多. See [app management](APP_MANAGEMENT.md) for operation limits. Unknown firmware must not inherit another device’s debloating list.
