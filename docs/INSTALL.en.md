# Installation and build

[简体中文](INSTALL.zh-CN.md) · [Home](../README.en.md) · [Compatibility](COMPATIBILITY.md)

## Requirements

Source is 1.1.0 / b24, minSdk 23 and targetSdk 36. The published b23 ARM32 test APK still requires Android 12 / API31 and `armeabi-v7a` support. Building source does not establish hardware acceptance.

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

The default archive is `dist/apk/quiet-tv_1.1.0_b24_debug_api23.apk`. Options include `--variant benchmark --abi armeabi-v7a`, `--min-sdk 26` and `--min-sdk 31`. See [versioning](VERSIONING.md) for naming and signatures. These commands never install to a TV.

## Download and try

[Releases](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0) provides the published APK and checksum. Use the downloaded filename and check its Android and ABI requirements.

Enable supported debugging on your TV and authorize the computer. Replace the documentation-only address below:

```sh
TV_SERIAL=192.0.2.10:5555
adb connect "$TV_SERIAL"
adb -s "$TV_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$TV_SERIAL" shell am start -n home.quiet.tv/.HomeActivity
```

Updates require a matching signature and non-decreasing build number. Do not uninstall merely to resolve a signature mismatch: uninstalling clears private settings and recovery records. Verify the old package and backups first.

Fresh installs open [setup](SETUP.md); temporary use lets you check remote navigation and shortcuts first. The recovery entry is under 应用管理 → 更多. See [app management](APP_MANAGEMENT.md) for operation limits. Unknown firmware must not inherit another device’s debloating list.
