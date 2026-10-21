# Installation and build

[简体中文](INSTALL.zh-CN.md) · [Home](../README.en.md) · [Compatibility](COMPATIBILITY.md)

## Requirements

Source is 1.1.0 / b26, with Android 5.0 / minSdk 21 and targetSdk 36; b26 has not been published. Build this source for Android 5.0 / 5.1. The public b24 APK still requires Android 6.0 / API23. The download is about 12.30 MiB and includes ARM32, ARM64, x86, and x86_64. This APK has not been installed on a TV or emulator; device and feature validation remains pending. The historical b23 ARM32 APK still requires Android 12 and remains available. Building source does not establish hardware acceptance.

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

The default archive is `dist/apk/quiet-tv_1.1.0_b26_debug_api21.apk`. Options include `--variant benchmark --abi armeabi-v7a`, `--min-sdk 21` (default), `23`, `26` and `31`. See [versioning](VERSIONING.md) for naming and signatures. These commands never install to a TV.

## Download and try

[b24 Releases](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b24) provides `quiet-tv_1.1.0_b24_benchmark_api23.apk` and its SHA-256 file. Put both in the same directory and run:

```sh
shasum -a 256 -c quiet-tv_1.1.0_b24_benchmark_api23.apk.sha256
```

The published APK uses the same test signature as b23. Historical [b23 Releases](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0) requires Android 12+ and ARM32 app support; those requirements do not apply to b24.

Enable supported debugging on your TV and authorize the computer. Replace the documentation-only address below. The example installs a locally built Debug APK; for a downloaded APK, substitute its actual file path:

```sh
TV_SERIAL=192.0.2.10:5555
adb connect "$TV_SERIAL"
adb -s "$TV_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$TV_SERIAL" shell am start -n home.quiet.tv/.HomeActivity
```

Updates require a matching signature and non-decreasing build number. Do not uninstall merely to resolve a signature mismatch: uninstalling clears private settings and recovery records. Verify the old package and backups first.

Fresh installs open [setup](SETUP.md); temporary use lets you check remote navigation and shortcuts first. The recovery entry is under 应用管理 → 更多. See [app management](APP_MANAGEMENT.md) for operation limits. Unknown firmware must not inherit another device’s debloating list.
