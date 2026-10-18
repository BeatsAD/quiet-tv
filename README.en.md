<p align="center"><img src="docs/assets/cover.png" alt="Quiet TV project cover" width="100%"></p>

# Quiet TV

**TV adaptation and application upgrades for televisions used in China.**

[简体中文](README.md) · [English](README.en.md) · [Install](docs/INSTALL.en.md) · [Usage](docs/USAGE.en.md) · [Compatibility](docs/COMPATIBILITY.md) · [Contribute](CONTRIBUTING.md)

A lightweight native Android TV launcher with application configuration, launcher updates, and reversible package debloating. The repository currently remains **private**. The adapted device is **Sony KD-65X9000H running Android 12**; other models require individual validation.

## Features

- Chinese and English interfaces, an editable dock for up to four apps, application grid, and D-pad focus navigation.
- Actual TV Input sources, network settings, and system settings shortcuts.
- Ambient default wallpaper, a fixed landscape, or a five-minute landscape slideshow.
- Source-based launcher updates and device-specific backup and recovery records.
- No advertising, accounts, or recommendation feeds. INTERNET permission is used only for on-demand local ADB connections; no cloud service.

Application upgrades currently mean updating Quiet TV and adjusting application configuration. Third-party media applications use their own update channels.

## Screenshots

Original ADB captures from the Sony device during development on October 18, 2026. The cover is an AI-generated brand illustration; the images below are actual device captures.

**Default theme and application dock**

![Real device: default theme, shortcuts, and application dock](docs/assets/home.png)

**Wallpaper settings**

![Real device: default wallpaper, three landscapes, and slideshow toggle](docs/assets/wallpaper-settings.png)

See [image provenance](docs/assets/README.md). Third-party application artwork and wallpaper remain the property of their respective rights holders.

## Getting started

1. Check the [compatibility matrix](docs/COMPATIBILITY.md).
2. Follow the [installation guide](docs/INSTALL.en.md) to prepare the toolchain, build, install, and try the launcher.
3. Read the [usage guide](docs/USAGE.en.md) for remote navigation and configuration.
4. Before changing system packages, back up the actual target and verify its recovery journal. Do not reuse the included Sony package journal on another device.

There is no published stable APK release yet. The current build produces a debug APK. Successful compilation or a screenshot does not establish compatibility with another TV.

## Compatibility at a glance

| Item | Current status |
| --- | --- |
| Adapted device | Sony KD-65X9000H / BRAVIA VH1 |
| Validated system | Android 12 / API 31 |
| Minimum APK version | Android 12 / API 31; newer systems are unverified |
| UI / physical panel | 1920×1080 / 3840×2160 |
| Other Android TVs | Require model, firmware, permission, and HOME validation |
| Non-Android TV systems | Unsupported by this APK and ADB workflow |

Read the [compatibility details](docs/COMPATIBILITY.md) and [original Chinese verification record](VERIFICATION.md) for functional limits.

## Documentation and feedback

- [Install](docs/INSTALL.en.md) · [Usage](docs/USAGE.en.md)
- [Contributing](CONTRIBUTING.md) · [Security policy](SECURITY.md)
- [Bug report](https://github.com/BeatsAD/quiet-tv/issues/new?template=bug_report.yml) · [Feature request](https://github.com/BeatsAD/quiet-tv/issues/new?template=feature_request.yml) · [Device adaptation](https://github.com/BeatsAD/quiet-tv/issues/new?template=device_adaptation.yml)
- [Sony implementation notes, Chinese](docs/DEVICE_NOTES.zh-CN.md) · [Repository settings and limitations](docs/REPOSITORY.md)

Private repository links are available only to authorized accounts.

## Attribution and license

Quiet TV is based on [Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack). Thank you to **Gathin** for the original work. This independently maintained project adapts it for use in China and is not officially affiliated with the upstream project or TV and application brands.

Code is licensed under [MIT](LICENSE), retaining `Copyright (c) 2026 Gathin` and the complete upstream notice. Outfit uses SIL OFL 1.1. Bundled wallpaper is outside the project's MIT code license; see [third-party notices](THIRD_PARTY_NOTICES.md).

See [app management and Dock usage](docs/USAGE.en.md) and [hardware evidence](docs/APP_MANAGEMENT.md).
