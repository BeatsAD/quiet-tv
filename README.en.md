<p align="center"><img src="docs/assets/cover.png" alt="Quiet TV project cover" width="100%"></p>

# Quiet TV

**A simple, customizable home screen for Android TVs.**

[简体中文](README.md) · [English](README.en.md)

Quiet TV is built for everyday TV use in China. Keep your favorite apps on the home screen, open them with your remote, switch inputs, choose a wallpaper, and manage installed applications.

The launcher has no ads or recommendation feed and requires no account. It uses native Android views and needs no root access or firmware changes.

[Download the test APK](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0) · [Install](docs/INSTALL.en.md) · [Usage](docs/USAGE.en.md) · [Supported devices](docs/COMPATIBILITY.md)

## Screenshots

**Your favorite apps, right on the home screen.**

A clock, date, favorite apps, and system shortcuts keep the main screen simple. A white focus outline follows the remote selection; press down to browse all apps.

![Quiet TV home screen: clock, system shortcuts, and four favorite apps](docs/assets/home.png)

**Choose a wallpaper, or keep the default theme.**

Use the ambient theme that changes color with the selected app, pick a fixed landscape, or enable a five-minute wallpaper slideshow.

![Quiet TV wallpaper settings: default theme, landscapes, and slideshow toggle](docs/assets/wallpaper-settings.png)

These original Sony KD-65X9000H captures were taken on October 19, 2026, with development build 1.1.0 / b15. The current download is the b23 optimized test build. The cover is an AI illustration. See [image provenance](docs/assets/README.md).

## Features

| Feature | What it does |
| --- | --- |
| Favorite apps | Keep up to four apps on the home screen. Add, replace, remove, and reorder them; launcher updates preserve your selection. |
| App management | Inspect app names, package names, versions, and status. Search, filter, and enable, disable, or uninstall apps individually. Protected system components stay read-only. |
| First setup | Set the default home screen, then optionally manage preinstalled apps. Choose actions before authorizing them; system packages on unknown firmware stay read-only. |
| Restore the original launcher | Switch back to or enable the original launcher using local records and validated device capabilities. Unavailable actions explain why they are blocked. |
| Inputs and settings | Choose from the TV's actual inputs, including HDMI, AV, or live TV where available. Open network and system settings directly. |
| Wallpaper | Preview and choose the default ambient theme, a fixed landscape, or an automatic slideshow. Cancel to restore your previous choice. |
| Remote navigation | Use direction keys to move, OK to open, and Back to return. The home screen remembers your last focus. |
| Local use | Settings stay on the TV. No cloud account is needed; app management connects to the TV's own ADB service on demand. |
| About and licenses | View the installed version and project/contact QR codes from the home screen. Bundled open-source licenses are available offline. |

The home screen supports Chinese and English; app management currently uses Chinese. Open “应用管理” from the all-apps screen to manage apps and edit favorites. Wallpaper settings are also available on the TV.

System package operations require local ADB authorization. Disabling keeps app data; uninstalling deletes data for the current user. Eligible preinstalled apps can be reinstalled, but deleted data cannot be restored. Preinstalled uninstall/reinstall remains unverified on hardware; see [app management details](docs/APP_MANAGEMENT.md).

## Install and try it

1. **Check your TV.** Source builds support Android 6.0 or newer; the published b23 APK still requires Android 12. Read the supported-device table below and the [compatibility details](docs/COMPATIBILITY.md).
2. **Get an APK.** Download the APK and SHA-256 file from [Releases](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0), or build from source using the [installation guide](docs/INSTALL.en.md).
3. **Install and try the launcher.** Follow the guide to connect ADB and install Quiet TV. A fresh install opens setup; choose “暂不设置，临时使用” to try it temporarily and check your remote, app launches, and system shortcuts first.
4. **Complete first setup.** Set the default home screen, then choose whether to manage preinstalled apps. Select “不处理，进入桌面” to skip package changes. Upgrades do not force setup again. Reopen it through “应用管理 → 更多 → 首次设置”; the same menu offers “恢复原桌面” to restore the original launcher. See [first setup and recovery, Chinese](docs/SETUP.md).

The current download is **v1.1.0 / b23, an ARM32 optimized test build of about 4.54 MiB**, for Android 12 or newer TVs supporting `armeabi-v7a`. Devices that only support 64-bit apps cannot use this APK. It is a prerelease signed with a test key; no stable release is available yet.

Changes in this build:

- Architecture-specific packaging with code and resource shrinking reduces APK size.
- Display-sized wallpaper decoding, on-demand thumbnails, and cover caching reduce loading work; returning home avoids unnecessary full app rescans.
- Improved native two-step setup, default-home verification, and the native About screen, with offline licenses opened on demand.

b23 was installed as an update on one Android 12 / ARM32 TV, with matching APK hashes and checks of HOME return, wallpaper, and Dock display. This does not establish hardware acceptance of every new flow. See the [release notes](docs/releases/v1.1.0.md) for checksums, updates, and validation limits. Historical v1.0.1 remains available. Default source Debug builds are universal and differ from the download; see [build rules](docs/VERSIONING.md).

You can keep the original launcher installed while trying Quiet TV. System debloating requires a backup and recovery record for the actual target TV. Real device settings, package inventories, and recovery records stay local; the repository provides only [redacted format examples](docs/examples/README.md), not debloating recommendations or recovery baselines for another TV.

## Supported devices

| Device / system | Status |
| --- | --- |
| Sony KD-65X9000H / BRAVIA VH1 · Android 12 | Installed and checked for the home screen, app management, wallpaper, selected system shortcuts, and one full reboot. See the [verification record](VERIFICATION.md) for the exact scope. |
| Other TVs running Android 6.0 or newer | Source compatibility is included; vendor firmware, HOME and ADB capabilities need hardware validation. See the [compatibility record](docs/android-compatibility.md). |
| Android 5.1 or earlier; non-Android systems such as Tizen or webOS | Unsupported by the current source. |

Long-running operation, casting, and full media playback remain unverified. Six-digit wireless pairing is still an experimental entry awaiting validation. Experimental older-Android source builds do not extend the current download to Android 11 or earlier; see [compatibility details](docs/COMPATIBILITY.md). Third-party media apps use their own update channels; Quiet TV does not currently update them automatically.

## Documentation and contributing

| I want to… | Read |
| --- | --- |
| Browse documentation and repository layout | [Documentation index](docs/README.md) |
| Install, update, or switch back to my original launcher | [Installation guide](docs/INSTALL.en.md) |
| Configure favorites, wallpaper, or remote navigation | [Usage guide](docs/USAGE.en.md) |
| Complete first setup or restore the original launcher | [First setup and recovery, Chinese](docs/SETUP.md) |
| Check device support and validated behavior | [Compatibility](docs/COMPATIBILITY.md) · [Verification record](VERIFICATION.md) |
| Build or contribute | [Contributing](CONTRIBUTING.md) · [Version and APK rules](docs/VERSIONING.md) |
| Read about the Sony device setup | [Device notes, Chinese](docs/DEVICE_NOTES.zh-CN.md) |
| Review security and repository settings | [Security policy](SECURITY.md) · [Dependency security](docs/DEPENDENCY_SECURITY.md) · [Repository notes](docs/REPOSITORY.md) |

Feedback: [Bug report](https://github.com/BeatsAD/quiet-tv/issues/new?template=bug_report.yml) · [Feature request](https://github.com/BeatsAD/quiet-tv/issues/new?template=feature_request.yml) · [Device adaptation](https://github.com/BeatsAD/quiet-tv/issues/new?template=device_adaptation.yml)

The repository, documentation, and test APK downloads are public. Sign in to GitHub to submit feedback.

## Contribute and maintain

Help with native Android features, remote navigation, documentation, translations, device validation, or tests. Start with a [good first issue](https://github.com/BeatsAD/quiet-tv/issues?q=is%3Aissue%20is%3Aopen%20label%3A%22good%20first%20issue%22) or [help wanted task](https://github.com/BeatsAD/quiet-tv/issues?q=is%3Aissue%20is%3Aopen%20label%3A%22help%20wanted%22), comment before starting, and submit a PR from your fork.

The [contribution guide](CONTRIBUTING.md) covers setup, checks, and review. Use [Discussions](https://github.com/BeatsAD/quiet-tv/discussions) for questions, larger ideas, or joining ongoing maintenance. Follow our [code of conduct](CODE_OF_CONDUCT.md).

To build locally, install Git, Python 3, JDK 17, Android SDK platform 36, and build-tools 36.0.0. Set `ANDROID_HOME` or your own `sdk.dir` in `local.properties`, then run:

```sh
git clone https://github.com/BeatsAD/quiet-tv.git
cd quiet-tv
./gradlew :app:assembleDebug :app:testDebugUnitTest --console=plain
```

The Debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Building and offline checks need neither a TV connection nor `device.json`. Configure device tools only when you need to operate your own TV, following the [example guide](docs/examples/README.md) and preserving existing files and backups. Follow [version and APK rules](docs/VERSIONING.md) for archiving, signing, and APK delivery.

Native source is in `app/`, build and device tools in `scripts/`, and `prototypes/about/` supplies actual APK bundle inputs. Device data, standalone web demos, and promotional video work are excluded from the current source tree. See the [documentation index](docs/README.md) for the complete layout.

## Credits and license

Quiet TV is based on [Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack). Thank you to **Gathin** for the original work. This project is independently maintained and is not officially affiliated with the upstream project or TV and application brands.

Code is licensed under [MIT](LICENSE), preserving the original copyright and complete license. Fonts, wallpaper, and third-party application artwork have their own terms; see [third-party notices](THIRD_PARTY_NOTICES.md).

## Let's connect

![WenBin AI LAB: X, GitHub, website, email, and QR codes for WeChat, WeChat Official Account, Xiaohongshu, and Douyin](docs/assets/contact-16x9.png)
