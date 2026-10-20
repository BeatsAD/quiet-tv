# Installation

[简体中文](INSTALL.zh-CN.md) · [Home](../README.en.md) · [Compatibility](COMPATIBILITY.md)

## Requirements

Current source is 1.1.0 / b24, minSdk 23 and targetSdk 36. Android 6.0 or newer firmware requires hardware validation; see the [compatibility record](android-compatibility.md). The published b23 APK still requires Android 12 / API31 and ARM32 support. Historical Sony hardware checks do not validate b24. Non-Android TV platforms are unsupported.

The verified development host is macOS. Install Git, JDK 17 or 21, Android SDK platform 36, build-tools 36.0.0, and platform-tools. Python 3 is required only for the helper scripts. Windows and Linux host workflows are unverified; `.command` launchers are macOS-specific.

Enable the TV's supported ADB debugging mode on a trusted local network and authorize your computer on the TV. Existing helper scripts use TCP port 5555; pairing-code debugging and custom ports are unadapted.

## Build from source

The repository is public and can be cloned directly. Read the [contribution guide](../CONTRIBUTING.md) before making changes.

```sh
git clone https://github.com/BeatsAD/quiet-tv.git
cd quiet-tv
```

Create `local.properties` in the repository root using your SDK's actual absolute path:

```properties
sdk.dir=/absolute/path/to/your/android-sdk
```

Then build without connecting to a TV:

```sh
python3 scripts/build_apk.py --summary "Describe this build's changes"
```

Default source builds are archived as `dist/apk/quiet-tv_1.1.0_b24_debug_api23.apk`, with a JSON receipt containing version, timestamps, SHA-256, and the summary. This does not contact a TV. Package: `home.quiet.tv`; source configuration: version 1.1.0, build 24, universal Debug with minSdk 23 by default. Use `--min-sdk 26` or `--min-sdk 31` for higher minimums. This integration only checks builds; no delivery archive was created and no TV was updated.

[Releases v1.1.0](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0) provides the separate optimized test APK `quiet-tv_1.1.0_b23_benchmark_armeabi-v7a.apk` and SHA-256 checksum. It requires Android 12 or newer and support for ARM32 apps. Its filename differs from the universal source Debug build; install a downloaded APK using its actual path. No stable APK has been published. See [release notes](releases/v1.1.0.md) for validation limits and [version rules](VERSIONING.md) for immutable archives and installation checks.

## Install and try

Replace the documentation-only address `192.0.2.10` with your TV's IP. Make your own platform-tools `adb` available in PATH.

```sh
TV_SERIAL=192.0.2.10:5555
adb connect "$TV_SERIAL"
adb -s "$TV_SERIAL" shell getprop ro.product.model
adb -s "$TV_SERIAL" shell getprop ro.build.version.sdk
adb -s "$TV_SERIAL" shell getprop ro.product.cpu.abilist
adb -s "$TV_SERIAL" shell pm list users
adb -s "$TV_SERIAL" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

Confirm the target identity and record the original HOME component before changing the default launcher. Keep the original launcher installed and enabled.

```sh
adb -s "$TV_SERIAL" install -r dist/apk/quiet-tv_1.1.0_b24_debug_api23.apk
adb -s "$TV_SERIAL" shell am start -n home.quiet.tv/.HomeActivity
```

Try navigation, applications, TV inputs, and settings first. Then select Quiet TV in the TV's default application settings. If the TV supports the standard command and its target user is confirmed to be user 0:

```sh
adb -s "$TV_SERIAL" shell cmd package set-home-activity --user 0 home.quiet.tv/.HomeActivity
adb -s "$TV_SERIAL" shell input keyevent KEYCODE_HOME
```

Other users and vendor restrictions need separate adaptation. Disabling the stock launcher is not required. Updates need the same signing key as the installed APK; debug keys can differ between computers. Preserve the original key rather than uninstalling and losing settings to resolve a signature mismatch.

## Helper scripts

On a fresh clone, follow the [example instructions](examples/README.md) to create an ignored local `device.json` from [the device example](examples/device.example.json), then fill in your own target and ADB / SDK paths. Preserve existing local configuration and journals; do not overwrite them with examples.

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python scripts/tv.py shell 'getprop ro.product.model'
```

`./build-and-install.command --summary "Describe this update"` builds and archives a versioned APK, installs it, compares local/installed SHA-256, opens the launcher, sends one Right key, and captures a screenshot on the configured device. Installation checks receive separate timestamped JSON receipts. Confirm the target before running it.

If native ADB on macOS reports `No route to host` after network reachability has been checked:

```sh
TV_ADB_BACKEND=python .venv/bin/python scripts/tv.py shell 'getprop ro.product.model'
TV_ADB_BACKEND=python ./build-and-install.command --summary "Describe this update"
```

The Python backend uses your local `~/.android/adbkey` and its public key. Never upload the private key.

## Debloating and recovery

Launcher installation does not require system debloating. Preferences, package inventories, `changes.json`, and `backup-current.json` are local-only device records, absent from a fresh clone. Public [redacted examples](examples/README.md) describe structure and cannot serve as a recovery baseline.

Git excludes `backups/`, `evidence/`, and `inspection/`. A fresh clone does not contain the original recovery baseline. Do not run `restore.command` on a new clone or another device: it depends on the original backup referenced in `changes.json`. With an existing journal, `scripts/backup.py` preserves the original baseline rather than initializing recovery for a different TV.

For system changes, create a target-specific baseline and journal, confirm each package and its original state, and define recovery before disabling anything. Check hardcoded user 0 and port 5555 assumptions. If you only tried the launcher, switch back through system settings or use the original HOME component you recorded, with the correct user ID. See the [Sony implementation notes, Chinese](DEVICE_NOTES.zh-CN.md) for the historical device workflow.
