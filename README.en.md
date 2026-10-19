<p align="center"><img src="docs/assets/cover.png" alt="Quiet TV project cover" width="100%"></p>

# Quiet TV

**A simple, customizable home screen for Android TVs.**

[简体中文](README.md) · [English](README.en.md)

Quiet TV is built for everyday TV use in China. Keep your favorite apps on the home screen, open them with your remote, switch inputs, choose a wallpaper, and manage installed applications.

The launcher has no ads or recommendation feed and requires no account. It uses native Android views and needs no root access or firmware changes.

[Download the test APK](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.0.1) · [Install](docs/INSTALL.en.md) · [Usage](docs/USAGE.en.md) · [Supported devices](docs/COMPATIBILITY.md)

## Screenshots

**Your favorite apps, right on the home screen.**

A clock, date, favorite apps, and three system shortcuts keep the main screen simple. A white focus outline follows the remote selection; press down to browse all apps.

![Quiet TV home screen: clock, system shortcuts, and four favorite apps](docs/assets/home.png)

**Choose a wallpaper, or keep the default theme.**

Use the ambient theme that changes color with the selected app, pick a fixed landscape, or enable a five-minute wallpaper slideshow.

![Quiet TV wallpaper settings: default theme, landscapes, and slideshow toggle](docs/assets/wallpaper-settings.png)

These are original Sony KD-65X9000H captures from development on October 18, 2026, showing the interface at that stage. The cover is an AI illustration. See [image provenance](docs/assets/README.md).

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

1. **Check your TV.** The compatibility candidate requires Android 6.0 or newer. Read the supported-device table below and the [compatibility details](docs/COMPATIBILITY.md).
2. **Get an APK.** Download the APK and SHA-256 file from [Releases](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.0.1), or build from source using the [installation guide](docs/INSTALL.en.md).
3. **Install and try the launcher.** Follow the guide to connect ADB and install Quiet TV. Check your remote, app launches, and system shortcuts first.
4. **Set it as your home screen.** Once it works on your TV, select Quiet TV in the TV's default-home settings. Follow the [usage guide](docs/USAGE.en.md) to arrange favorites and choose a wallpaper.

The available download is **v1.0.1 / b3, a Debug test build**. There is no stable release yet. Read its [release notes](docs/releases/v1.0.1.md) for known dependency issues and validation scope. Subsequent [dependency fixes in source](docs/DEPENDENCY_SECURITY.md) have not been published in a new APK.

You can keep the original launcher installed while trying Quiet TV. System debloating is a separate step and requires a backup and recovery record for the actual target TV. The included Sony package list is specific to that device.

## Supported devices

| Device / system | Status |
| --- | --- |
| Sony KD-65X9000H / BRAVIA VH1 · Android 12 | Installed and checked for the home screen, app management, wallpaper, selected system shortcuts, and one full reboot. See the [verification record](VERIFICATION.md) for the exact scope. |
| Other TVs running Android 6.0 or newer | Version compatibility code is included. See the [version validation record](docs/android-compatibility.md); vendor firmware, HOME selection and ADB capabilities still require hardware checks. |
| Android 5.1 or earlier; non-Android systems such as Tizen or webOS | Not supported by the current version. |

Long-running operation, casting, and full media playback remain unverified. Third-party media apps use their own update channels; Quiet TV does not currently update them automatically.

## Documentation and contributing

| I want to… | Read |
| --- | --- |
| Install, update, or switch back to my original launcher | [Installation guide](docs/INSTALL.en.md) |
| Configure favorites, wallpaper, or remote navigation | [Usage guide](docs/USAGE.en.md) |
| Check device support and validated behavior | [Compatibility](docs/COMPATIBILITY.md) · [Verification record](VERIFICATION.md) |
| Build or contribute | [Contributing](CONTRIBUTING.md) · [Version and APK rules](docs/VERSIONING.md) |
| Read about the Sony device setup | [Device notes, Chinese](docs/DEVICE_NOTES.zh-CN.md) |
| Review security and repository settings | [Security policy](SECURITY.md) · [Dependency security](docs/DEPENDENCY_SECURITY.md) · [Repository notes](docs/REPOSITORY.md) |

Feedback: [Bug report](https://github.com/BeatsAD/quiet-tv/issues/new?template=bug_report.yml) · [Feature request](https://github.com/BeatsAD/quiet-tv/issues/new?template=feature_request.yml) · [Device adaptation](https://github.com/BeatsAD/quiet-tv/issues/new?template=device_adaptation.yml)

This repository is currently private. Downloads, documentation, and feedback links require repository access.

## Credits and license

Quiet TV is based on [Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack). Thank you to **Gathin** for the original work. This project is independently maintained and is not officially affiliated with the upstream project or TV and application brands.

Code is licensed under [MIT](LICENSE), preserving the original copyright and complete license. Fonts, wallpaper, and third-party application artwork have their own terms; see [third-party notices](THIRD_PARTY_NOTICES.md).

## Let's connect

![WenBin AI LAB: X, GitHub, website, email, and QR codes for WeChat, WeChat Official Account, Xiaohongshu, and Douyin](docs/assets/contact-16x9.png)
