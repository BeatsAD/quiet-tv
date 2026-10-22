<p align="center"><img src="docs/assets/cover.png" alt="Quiet TV project cover" width="100%"></p>

# Quiet TV

**A simple, customizable launcher for Android TVs.**

[简体中文](README.md)  [English](README.en.md)

Keep favorite apps on the home screen, open them with a remote, choose inputs, personalize wallpapers and manage apps. Quiet TV has no ads, recommendation feed or account requirement. Built with native Android views; no root or firmware flashing is required.

[Open the installation guide (Chinese)](https://beatsad.github.io/quiet-tv/) to choose your TV brand, copy an Agent installation request, or find manual installation and recovery instructions.

[Download 1.1.2 test builds](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.2)  [Install](docs/INSTALL.en.md)  [Usage](docs/USAGE.en.md)  [Compatibility](docs/COMPATIBILITY.md)

## Download

| TV system | Version 1.1.2 APK | Checksum |
| --- | --- | --- |
| Android 4.4 | [Compatibility test build 35, about 4.67 MiB](https://github.com/BeatsAD/quiet-tv/releases/download/v1.1.2/QuietTV_1.1.2-35_Android4.4_Test.apk) | [SHA-256](https://github.com/BeatsAD/quiet-tv/releases/download/v1.1.2/QuietTV_1.1.2-35_Android4.4_Test.apk.sha256) |
| Android 5.0 and later | [Standard test build 34, about 8.94 MiB](https://github.com/BeatsAD/quiet-tv/releases/download/v1.1.2/QuietTV_1.1.2-34_Android5plus_Test.apk) | [SHA-256](https://github.com/BeatsAD/quiet-tv/releases/download/v1.1.2/QuietTV_1.1.2-34_Android5plus_Test.apk.sha256) |

**Choose by Android version, rather than the highest build number.** Both share the main features, application ID and historical test signer, and replace each other. Android 4.4 uses TCP ADB and system settings or the remote for inputs, without six-digit pairing. The standard APK includes ARM32, ARM64, x86 and x86_64; the compatibility APK has no native libraries.

GitHub downloads use English filenames with Chinese display labels; local Chinese archives are retained. Choose the `.apk.sha256` matching the downloaded APK. See [release notes and validation limits](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.2). Both are optimized test builds; no stable release is available. [Historical releases](https://github.com/BeatsAD/quiet-tv/releases) remain unchanged.

## Screenshots

**Five favorite slots.** Time/date and right-side system shortcuts can be hidden in Desktop Settings.

![Quiet TV 1.1.2 home with five favorites and system shortcuts](docs/assets/home-1.1.2.png)

**Five-column All Apps.** Save a custom order, cancel changes or restore the default; favorites have their own order.

![Quiet TV 1.1.2 five-column All Apps](docs/assets/all-apps-1.1.2.png)

**Bring your own wallpaper.** Preview, rotate, delete with a long OK press, restore built-ins, or scan the TV upload QR code with a phone on the same LAN.

![Quiet TV 1.1.2 wallpapers with built-in and uploaded images and the upload entry](docs/assets/wallpaper-1.1.2.png)

These original 1920×1080 captures come directly from a Sony TV running published 1.1.2 / 34, verified against the APK SHA-256. Existing configuration is preserved; UI is neither composited nor redrawn. See [provenance](docs/assets/README.md) and [more UI examples](docs/USAGE.en.md). Screenshots do not establish full hardware acceptance or support for other models.

## Features

| Feature | What it does |
| --- | --- |
| Favorites and order | Up to five favorite slots; five-column All Apps with save, cancel and restore-default ordering. |
| Desktop settings | Independently show or hide time/date and right-side Inputs, Network and Settings buttons. |
| Wallpapers | Color theme, built-ins, uploaded images, preview and five-minute rotation; long-OK deletion, built-in recovery and LAN upload. |
| App management | Search/filter, inspect versions and states, and enable, disable or uninstall individual apps where supported; critical components stay read-only. |
| Boot-ad management | Detect adapted services, disable and restore according to rules. No boot-ad rule has passed Quiet TV hardware acceptance yet; unmatched devices are view-only. |
| Inputs and settings | Read actual inputs, detect vendor-panel fallbacks, and add selected OEM app, settings and network routes. Actual switching needs target-TV checks. |
| Setup and recovery | Try temporarily, set HOME or recover the original launcher. Unsupported firmware commands are disabled with guidance. |
| Remote and focus | Arrows, OK and Back, spring scrolling and focus tracking; image quality follows actual rendering size and memory. |
| Local use | No cloud account. Settings remain on the TV; package operations request separate local ADB authorization. About includes version and offline licenses. |

Find management and sorting under All Apps → 应用管理. Favorites, ordering, desktop settings and wallpapers need no ADB. System operations require separate authorization. Disabling keeps data; uninstalling removes current-user data, which restoring a preinstalled package cannot recover. See [app management](docs/APP_MANAGEMENT.md).

## Install and try it

1. Confirm Android support, choose the matching APK above, and [verify its checksum](docs/INSTALL.en.md#download-and-try).
2. Follow [installation](docs/INSTALL.en.md), or use the [computer Agent instructions, Chinese](docs/AGENT_INSTALL.zh-CN.md).
3. On first launch choose temporary use to check navigation, apps and system entries while keeping the stock launcher.
4. Set HOME only when ready. Read back the component, then separately verify the physical Home button, reboot and standby wake. Recovery is under 应用管理 → 更多; see [setup](docs/SETUP.md).

Updates need matching package/signature and an increasing build number. Do not uninstall to resolve signing mismatches. Keep backups and recovery records locally, and never reuse another TV's debloating list.

## Supported devices and validation

| System or device | Scope |
| --- | --- |
| Sony / Android 12 | Published 1.1.2 / 34 APK hash verified; home, All Apps, wallpaper, desktop settings and sorting pages captured. Full functionality, reboot and standby acceptance were not performed. |
| Android 5.0+ TVs | Standard test APK; minimum API support does not establish vendor HOME, ADB, settings or input compatibility. |
| Android 4.4 | Separate compatibility test APK; physical-TV runtime acceptance for this batch remains pending. |
| Android 4.0–4.3, Tizen, webOS and other non-Android systems | Unsupported. |

Both profiles passed optimized builds, 49 unit tests each, Lint (zero errors, warnings remain), signature and package checks. Sony captures add page evidence only; final-APK feature acceptance and in-place upgrade data retention remain incomplete. Casting, long runs, full media playback, actual input switching and vendor-specific behavior need separate checks. Six-digit wireless pairing remains experimental, requires API33+ and actual device support.

See [compatibility and historical evidence](docs/COMPATIBILITY.md). Third-party media apps update through their own channels; Quiet TV does not automatically update them.

## Known issues and hardware validation

**These issues remain unresolved or unverified. More physical TVs across brands, models, and firmware versions are needed; they must not be treated as fixed.** Successful installation, manual launch, or HOME resolution does not establish correct boot, physical Home-button, or post-reboot behavior. See the current report in [Issue #35](https://github.com/BeatsAD/quiet-tv/issues/35).

| Issue | Current behavior and validation needed |
| --- | --- |
| Vendor home takeover | Firmware may restore its own launcher at boot or have the Home button start a vendor Activity directly, bypassing standard HOME preferences. Cold boot, reboot, and wake from standby need separate checks. |
| Default-home command fails | A report includes `cannot be home on user 0`. The installed APK, current user, HOME candidates, and vendor restrictions need investigation; the cause and adaptation remain unconfirmed. |
| Default-home status is insufficient | Detection mainly uses HOME Intent resolution. Quiet TV may report that it is the default while the physical Home button or a reboot still opens the stock launcher. Status reporting and persistence verification need improvement. |
| ADB disabled or inaccessible | Greyed-out USB debugging, missing wireless debugging, or an unavailable pairing code may prevent local app management and ADB-dependent adaptation. The built-in ADB client cannot bypass firmware restrictions or pairing authentication; there is no universal unlock method. |

The 1.1.2 main app adds a regular launcher entry and Xiaomi Settings/Wi-Fi routing. The optional standalone [Xiaomi home helper](xiaomi-home-helper/README.md) targets MiTV4-ANSM0 / Android 6. It requires manually enabled accessibility, matches the selected stock homepage exactly, and provides a boot attempt and pause control. **Unpublished and untested on the target TV; it does not change the system HOME preference. Physical Home, reboot and standby behavior remain unverified.**

An optional standalone [Coocaa home shim](coocaa-home-shim/README.md) adapts the upstream helper for Android 8 firmware confirmed to use vendor homepage properties. **This port has not been validated on a physical TV, is not enabled in the universal APK, and cannot unlock ADB.** Log-based monitoring remains unimplemented. Upstream device results do not validate this port. Keep the stock launcher and local recovery records; do not copy another TV's system-package disable list.

Share results in [Issue #35](https://github.com/BeatsAD/quiet-tv/issues/35) or a [device adaptation report](https://github.com/BeatsAD/quiet-tv/issues/new?template=device_adaptation.yml): model, Android/firmware version, Quiet TV version and APK source, reproduction steps, and actual results for manual launch, the physical Home button, cold boot/reboot, standby wake, inputs, and stock-launcher recovery. Mark untested items as unverified. Share only relevant redacted log excerpts; omit IP addresses, serial numbers, keys, and full device dumps.

## Technical approach

Native Android UI → build an APK → install with ADB → configure HOME → refine on real hardware. Daily use does not require an online computer.

See the [Codex implementation and adaptation guide, Chinese](docs/TECHNICAL_PATH_WITH_CODEX.zh-CN.md) for tools, debugging authorization and a reusable prompt. Computer ADB installation and Quiet TV's local management client request separate authorization.

## Documentation and contributing

| I want to… | Read |
| --- | --- |
| Browse documentation and repository layout | [Documentation index](docs/README.md) |
| Install, update, or switch back to my original launcher | [Installation guide](docs/INSTALL.en.md) |
| Configure favorites, wallpaper, or remote navigation | [Usage guide](docs/USAGE.en.md) |
| Complete first setup or restore the original launcher | [First setup and recovery, Chinese](docs/SETUP.md) |
| Check device support and validated behavior | [Compatibility](docs/COMPATIBILITY.md) · [Verification record](docs/COMPATIBILITY.md) |
| Build or contribute | [Contributing](CONTRIBUTING.md) · [Version and APK rules](docs/VERSIONING.md) |
| Review security and repository settings | [Security policy](SECURITY.md) · [Contributing](CONTRIBUTING.md) |

Feedback: [Bug report](https://github.com/BeatsAD/quiet-tv/issues/new?template=bug_report.yml) · [Feature request](https://github.com/BeatsAD/quiet-tv/issues/new?template=feature_request.yml) · [Device adaptation](https://github.com/BeatsAD/quiet-tv/issues/new?template=device_adaptation.yml)

The repository, documentation, and test APK downloads are public. Sign in to GitHub to submit feedback.

## Contribute and maintain

Help with native Android features, remote navigation, documentation, translations, device validation, or tests. Start with a [good first issue](https://github.com/BeatsAD/quiet-tv/issues?q=is%3Aissue%20is%3Aopen%20label%3A%22good%20first%20issue%22) or [help wanted task](https://github.com/BeatsAD/quiet-tv/issues?q=is%3Aissue%20is%3Aopen%20label%3A%22help%20wanted%22), comment before starting, and submit a PR from your fork.

The [contribution guide](CONTRIBUTING.md) covers setup, checks, and review. Use [Discussions](https://github.com/BeatsAD/quiet-tv/discussions) for questions, larger ideas, or joining ongoing maintenance. Follow our [code of conduct](CODE_OF_CONDUCT.md).

To build locally, install Git, Python 3, JDK 17, Android SDK platform 36, and build-tools 36.0.0. Set `ANDROID_HOME` or your own `sdk.dir` in `local.properties`, then run:

```sh
git clone https://github.com/BeatsAD/quiet-tv.git
cd quiet-tv
./gradlew :app:assembleDebug --console=plain
```

The Debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Building and offline checks need neither a TV connection nor `device.json`. Use ADB as described in the [installation guide](docs/INSTALL.en.md) when installing on your own TV; preserve existing files and backups. Follow [version and APK rules](docs/VERSIONING.md) for archiving, signing, and APK delivery.

Native source is in `app/`, build and device tools in `scripts/`, and `prototypes/about/` supplies actual APK bundle inputs. Device data, standalone web demos, and promotional video work are excluded from the current source tree. See the [documentation index](docs/README.md) for the complete layout.

## Credits and license

Quiet TV is based on [Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack). Thank you to **Gathin** for the original work. This project is independently maintained and is not officially affiliated with the upstream project or TV and application brands.

Code is licensed under [MIT](LICENSE), preserving the original copyright and complete license. Fonts, wallpaper, and third-party application artwork have their own terms; see [third-party notices](THIRD_PARTY_NOTICES.md).

## Let's connect

![WenBin AI LAB: X, GitHub, website, email, and QR codes for WeChat, WeChat Official Account, Xiaohongshu, and Douyin](docs/assets/contact-16x9.png)
