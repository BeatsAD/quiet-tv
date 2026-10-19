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
| Inputs and settings | Choose from the TV's actual inputs, including HDMI, AV, or live TV where available. Open network and system settings directly. |
| Wallpaper | Preview and choose the default ambient theme, a fixed landscape, or an automatic slideshow. Cancel to restore your previous choice. |
| Remote navigation | Use direction keys to move, OK to open, and Back to return. The home screen remembers your last focus. |
| Local use | Settings stay on the TV. No cloud account is needed; app management connects to the TV's own ADB service on demand. |

The home screen supports Chinese and English; app management currently uses Chinese. Open “应用管理” from the all-apps screen to manage apps and edit favorites. Wallpaper settings are also available on the TV.

System package operations require local ADB authorization. Disabling keeps app data; uninstalling deletes data for the current user. Eligible preinstalled apps can be reinstalled, but deleted data cannot be restored. Preinstalled uninstall/reinstall remains unverified on hardware; see [app management details](docs/APP_MANAGEMENT.md).

## Install and try it

1. **Check your TV.** Android 12 is the minimum. Read the supported-device table below and the [compatibility details](docs/COMPATIBILITY.md).
2. **Get an APK.** Download the APK and SHA-256 file from [Releases](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0), or build from source using the [installation guide](docs/INSTALL.en.md).
3. **Install and try the launcher.** Follow the guide to connect ADB and install Quiet TV. Check your remote, app launches, and system shortcuts first.
4. **Set it as your home screen.** Once it works on your TV, select Quiet TV in the TV's default-home settings. Follow the [usage guide](docs/USAGE.en.md) to arrange favorites and choose a wallpaper.

The current download is **v1.1.0 / b23, an ARM32 optimized test build** for Android 12 or newer TVs supporting `armeabi-v7a`. It is a prerelease signed with a test key. Read its [release notes](docs/releases/v1.1.0.md) for changes, checksums, and hardware-validation limits. The historical v1.0.1 release is retained. Default source Debug builds are universal and differ from the ARM32 optimized download; see [build rules](docs/VERSIONING.md).

You can keep the original launcher installed while trying Quiet TV. System debloating is a separate step and requires a backup and recovery record for the actual target TV. The included Sony package list is specific to that device.

## Supported devices

| Device / system | Status |
| --- | --- |
| Sony KD-65X9000H / BRAVIA VH1 · Android 12 | Installed and checked for the home screen, app management, wallpaper, selected system shortcuts, and one full reboot. See the [verification record](VERIFICATION.md) for the exact scope. |
| Other TVs running Android 12 or newer | Require device-specific adaptation, including firmware, default-home selection, and ADB permissions. |
| Android 11 or earlier; non-Android systems such as Tizen or webOS | Not supported by the current version. |

Long-running operation, casting, and full media playback remain unverified. Third-party media apps use their own update channels; Quiet TV does not currently update them automatically.

## Documentation and contributing

| I want to… | Read |
| --- | --- |
| Browse documentation and repository layout | [Documentation index](docs/README.md) |
| Install, update, or switch back to my original launcher | [Installation guide](docs/INSTALL.en.md) |
| Configure favorites, wallpaper, or remote navigation | [Usage guide](docs/USAGE.en.md) |
| Check device support and validated behavior | [Compatibility](docs/COMPATIBILITY.md) · [Verification record](VERIFICATION.md) |
| Build or contribute | [Contributing](CONTRIBUTING.md) · [Version and APK rules](docs/VERSIONING.md) |
| Read about the Sony device setup | [Device notes, Chinese](docs/DEVICE_NOTES.zh-CN.md) |
| Review security and repository settings | [Security policy](SECURITY.md) · [Dependency security](docs/DEPENDENCY_SECURITY.md) · [Repository notes](docs/REPOSITORY.md) |

Feedback: [Bug report](https://github.com/BeatsAD/quiet-tv/issues/new?template=bug_report.yml) · [Feature request](https://github.com/BeatsAD/quiet-tv/issues/new?template=feature_request.yml) · [Device adaptation](https://github.com/BeatsAD/quiet-tv/issues/new?template=device_adaptation.yml)

The repository, documentation, and test APK downloads are public. Sign in to GitHub to submit feedback.

## Contribute and maintain

Help with native Android features, remote navigation, documentation, translations, device validation, or tests. Start with a [good first issue](https://github.com/BeatsAD/quiet-tv/issues?q=is%3Aissue%20is%3Aopen%20label%3A%22good%20first%20issue%22) or [help wanted task](https://github.com/BeatsAD/quiet-tv/issues?q=is%3Aissue%20is%3Aopen%20label%3A%22help%20wanted%22), comment before starting, and submit a PR from your fork.

The [contribution guide](CONTRIBUTING.md) covers setup, checks, and review. Use [Discussions](https://github.com/BeatsAD/quiet-tv/discussions) for questions, larger ideas, or joining ongoing maintenance. Follow our [code of conduct](CODE_OF_CONDUCT.md).

## Credits and license

Quiet TV is based on [Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack). Thank you to **Gathin** for the original work. This project is independently maintained and is not officially affiliated with the upstream project or TV and application brands.

Code is licensed under [MIT](LICENSE), preserving the original copyright and complete license. Fonts, wallpaper, and third-party application artwork have their own terms; see [third-party notices](THIRD_PARTY_NOTICES.md).

## Let's connect

![WenBin AI LAB: X, GitHub, website, email, and QR codes for WeChat, WeChat Official Account, Xiaohongshu, and Douyin](docs/assets/contact-16x9.png)
