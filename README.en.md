<p align="center"><img src="docs/assets/cover.png" alt="Quiet TV project cover" width="100%"></p>

<h1 align="center">Quiet TV</h1>

<p align="center"><strong>A simple, customizable launcher for Android TVs.</strong></p>

<p align="center">
  <a href="README.md"><img alt="简体中文" src="https://img.shields.io/badge/%E7%AE%80%E4%BD%93%E4%B8%AD%E6%96%87-555555?style=flat-square"></a>
  <a href="README.en.md"><img alt="English" src="https://img.shields.io/badge/English-1f6feb?style=flat-square"></a>
</p>

<p align="center">
  Favorite apps, inputs, wallpapers and app management, all from your remote.<br>
  Native Android UI. No ads, recommendation feed, account, root or firmware flashing.
</p>

<p align="center">
  <a href="https://beatsad.github.io/quiet-tv/"><img alt="Download APK" src="https://img.shields.io/badge/Download%20APK-34a853?style=for-the-badge&amp;logo=android&amp;logoColor=white"></a>
  <a href="docs/INSTALL.en.md"><img alt="Install guide" src="https://img.shields.io/badge/Install%20guide-1f6feb?style=for-the-badge"></a>
  <a href="docs/USAGE.en.md"><img alt="Usage guide" src="https://img.shields.io/badge/Usage%20guide-d97757?style=for-the-badge"></a>
</p>

<p align="center">
  <a href="https://github.com/BeatsAD/quiet-tv/releases"><img alt="Latest release" src="https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fbeatsad.github.io%2Fquiet-tv%2Flatest.json&amp;query=%24.latestVersion&amp;prefix=v&amp;label=Latest%20release&amp;style=flat-square&amp;color=2997ff"></a>
  <a href="LICENSE"><img alt="License MIT" src="https://img.shields.io/badge/License-MIT-555555?style=flat-square"></a>
  <a href="docs/COMPATIBILITY.md"><img alt="Standard Android 5.0+" src="https://img.shields.io/badge/Standard-Android%205.0%2B-3ddc84?style=flat-square&amp;logo=android&amp;logoColor=white"></a>
  <a href="docs/COMPATIBILITY.md"><img alt="Legacy Android 4.4" src="https://img.shields.io/badge/Legacy-Android%204.4-f5a623?style=flat-square&amp;logo=android&amp;logoColor=white"></a>
</p>

## Download

The [installation page (Chinese)](https://beatsad.github.io/quiet-tv/), [Agent guide](https://beatsad.github.io/quiet-tv/agent.md), and [latest release index](https://beatsad.github.io/quiet-tv/latest.json) update automatically from published releases, including test builds. Users do not need to maintain a version number or edit the installation request for each release.

**Choose the latest build compatible with the TV's Android version, supported ABIs, package, signer and upgrade conditions.** Use an Android 4.4 compatibility APK for API19–20 or an applicable standard APK for API21+. Inspect the actual APK metadata; a larger build number or a 64-bit processor alone does not establish compatibility.

The 1.3.0 test release provides two APKs: a universal Android 5.0+ build (117) and an Android 4.4 compatibility build (118). Neither contains native libraries, so separate ARM32 and ARM64 downloads are unnecessary; choose by Android version.

Use the index's actual download URL and SHA-256, or a matching checksum attachment for historical builds. Read the selected release's validation limits. [Historical releases](https://github.com/BeatsAD/quiet-tv/releases) remain unchanged.

## Screenshots

<table>
  <tr>
    <td width="50%" valign="top">
      <p><strong>Favorite apps, time and weather at a glance.</strong></p>
      <img src="docs/assets/home-1.3.0.png" alt="Quiet TV 1.3.0 home apps, time and weather" width="100%">
    </td>
    <td width="50%" valign="top">
      <p><strong>Classic and Art Gallery layouts with a live preview.</strong></p>
      <img src="docs/assets/desktop-settings-1.3.0.png" alt="Quiet TV 1.3.0 desktop customization and live preview" width="100%">
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <p><strong>Daily wallpaper, with uploads for your photos and videos.</strong></p>
      <img src="docs/assets/wallpaper-1.3.0.png" alt="Quiet TV 1.3.0 daily wallpaper and upload entry" width="100%">
    </td>
    <td width="50%" valign="top">
      <p><strong>Automatic location, city selection and weather sources.</strong></p>
      <img src="docs/assets/weather-1.3.0.png" alt="Quiet TV 1.3.0 city and weather-source settings" width="100%">
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <p><strong>Filter apps by category and inspect their details.</strong></p>
      <img src="docs/assets/app-management-1.3.0.png" alt="Quiet TV 1.3.0 app categories and details" width="100%">
    </td>
    <td width="50%" valign="top">
      <p><strong>View the current version and check for app updates.</strong></p>
      <img src="docs/assets/update-1.3.0.png" alt="Quiet TV 1.3.0 version information and update check" width="100%">
    </td>
  </tr>
</table>

## Features

These core features describe the 1.3.0 test release. Use the live release index and choose a build compatible with the TV's system and installed package.

| Feature | What it does |
| --- | --- |
| Favorites and order | Up to five favorite apps, five-column All Apps and automatically saved ordering. |
| Personalization | Classic or Art Gallery, custom greetings and avatars, independent information and shortcut visibility. |
| Weather | Automatic location, manual city selection and phone-assisted city entry. |
| Wallpapers | Color theme, Bing daily wallpaper, uploaded photos and videos, LAN uploads and long-OK deletion. |
| App management | Category filters, package details and custom artwork; computer AI tools handle authorized system changes and recovery. |
| Child mode | Hide or lock apps, set per-session or daily limits, and use parent verification and rest reminders. |
| App autostart | Open a selected app after boot or standby wake enters Quiet TV, without repeating on ordinary HOME returns. |
| Inputs and home setup | Open supported input/settings routes; computer AI configures HOME or compatibility paths and retains stock-home recovery. |
| In-app updates | Check versions, download from multiple sources, verify integrity and signing, then confirm installation. |
| Remote and local use | Arrows, OK and Back; no cloud account, with preferences stored on the TV. |

Open All Apps → 应用管理 for App list, Favorites, Order, Child mode, Version updates and Diagnostic logs. Everyday customization needs no ADB. In 1.3.0, ADB connections and system-disable controls move to computer AI tools, which handle explicitly requested system maintenance using matching rules and recovery records. See [app management](docs/APP_MANAGEMENT.md).

The signed update feed is live. Automatic checks run every 24 hours while Home is in the foreground; 1.3.0 provides manual checks under App Management → Version updates → Check for updates (About in older APKs). Older versions without this feature need a manual installation first. A higher build of the same software version is not offered as an update. See [in-app updates](docs/UPDATES.md).

## Install and try it

1. Confirm Android support, choose the matching APK above, and [verify its checksum](docs/INSTALL.en.md#download-and-try).
2. Follow [installation](docs/INSTALL.en.md), or use the [computer Agent instructions, Chinese](docs/AGENT.md).
3. Version 1.3.0 opens Home without an automatic setup wizard. Check navigation, apps and system entries; keep any desktop configuration already completed by the AI tool.
4. Use the computer AI workflow to configure HOME when ready. Read back the component, then separately verify the physical Home button, reboot and standby wake. Recovery in 1.3.0 is under 应用管理 → 更多 → 帮助与恢复 (Help and recovery); see [setup](docs/SETUP.md).

Updates need matching package/signature and an increasing build number. Do not uninstall to resolve signing mismatches. Keep backups and recovery records locally, and never reuse another TV's debloating list.

## Supported devices and validation

| System or device | Scope |
| --- | --- |
| Android 5.0+ TVs | 1.3.0 universal standard test APK (117), minimum API21. |
| Android 4.4 | 1.3.0 compatibility test APK (118), minimum API19. Physical Android 4.4 acceptance remains pending. |
| Android 4.0–4.3, Tizen, webOS and other non-Android systems | Unsupported. |

The two 1.3.0 APKs share one source baseline, software version and test signer. Emulator checks do not establish vendor-TV compatibility. Physical HOME, cold boot, reboot, standby, playback and input switching need separate target-TV checks. ADB connection and pairing use computer tools and the TV’s actual system authorization; 1.3.0 has no in-app ADB connection or pairing interface.

See [compatibility and historical evidence](docs/COMPATIBILITY.md). Third-party media apps update through their own channels; Quiet TV does not automatically update them.

## Known issues and hardware validation

**These issues remain unresolved or unverified. More physical TVs across brands, models, and firmware versions are needed; they must not be treated as fixed.** Successful installation, manual launch, or HOME resolution does not establish correct boot, physical Home-button, or post-reboot behavior. See the current report in [Issue #35](https://github.com/BeatsAD/quiet-tv/issues/35).

| Issue | Current behavior and validation needed |
| --- | --- |
| Vendor home takeover | Firmware may restore its own launcher at boot or have the Home button start a vendor Activity directly, bypassing standard HOME preferences. Cold boot, reboot, and wake from standby need separate checks. |
| Default-home command fails | A report includes `cannot be home on user 0`. The installed APK, current user, HOME candidates, and vendor restrictions need investigation; the cause and adaptation remain unconfirmed. |
| Default-home status is insufficient | Check default HOME, compatibility guard and proxy status separately. Status text does not establish physical Home-button, reboot or standby behavior. |
| ADB disabled or inaccessible | Greyed-out USB debugging, missing wireless debugging, or an unavailable pairing code may prevent computer maintenance and ADB-dependent adaptation. Computer tools cannot bypass firmware restrictions or pairing authentication; there is no universal unlock method. |

Version 1.3.0 moves technical configuration to computer AI and keeps Help and recovery on the TV. Historical 1.2.4 APKs retain Desktop Settings → 桌面配置 (Home setup). Compatibility guard and optional HOME proxies depend on firmware, permissions and companion components; Android 4.4 has no standard guard. Physical HOME, reboot, cold boot, standby and HDMI/playback need separate target-TV checks. See [setup and recovery](docs/SETUP.md) and [computer adaptation](docs/AGENT_ADAPT.zh-CN.md).

The standalone [Xiaomi home helper](xiaomi-home-helper/README.md) remains available as an alternative experiment; disable its accessibility service before enabling the built-in guard. Existing helper settings are not migrated automatically.

An optional standalone [Coocaa home shim](coocaa-home-shim/README.md) adapts the upstream helper for Android 8 firmware confirmed to use vendor homepage properties. **This port has not been validated on a physical TV, is not enabled in the universal APK, and cannot unlock ADB.** Log-based monitoring remains unimplemented. Upstream device results do not validate this port. Keep the stock launcher and local recovery records; do not copy another TV's system-package disable list.

Share results in [Issue #35](https://github.com/BeatsAD/quiet-tv/issues/35) or a [device adaptation report](https://github.com/BeatsAD/quiet-tv/issues/new?template=device_adaptation.yml): model, Android/firmware version, Quiet TV version and APK source, reproduction steps, and actual results for manual launch, the physical Home button, cold boot/reboot, standby wake, inputs, and stock-launcher recovery. Mark untested items as unverified. Share only relevant redacted log excerpts; omit IP addresses, serial numbers, keys, and full device dumps.

## Technical approach

Native Android UI → build an APK → install with ADB → configure HOME → refine on real hardware. Daily use does not require an online computer.

Use the [unified computer AI guide, Chinese](docs/AGENT.md) for installation, upgrades, adaptation and recovery. The [Codex implementation and adaptation guide, Chinese](docs/TECHNICAL_PATH_WITH_CODEX.zh-CN.md) covers development tools and debugging authorization. The computer obtains the TV's actual ADB authorization; no in-app ADB client is required.

## Documentation and contributing

| I want to… | Read |
| --- | --- |
| Browse documentation and repository layout | [Documentation index](docs/README.md) |
| Install, update, or switch back to my original launcher | [Installation guide](docs/INSTALL.en.md) |
| Configure favorites, wallpaper, or remote navigation | [Usage guide](docs/USAGE.en.md) |
| Configure HOME on request or restore the original launcher | [Home setup and recovery, Chinese](docs/SETUP.md) |
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

Quiet TV is based on [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv). Thank you to **Gathin** for the original work. This project is independently maintained and is not officially affiliated with the upstream project or TV and application brands.

Code is licensed under [MIT](LICENSE), preserving the original copyright and complete license. Fonts, wallpaper, and third-party application artwork have their own terms; see [third-party notices](THIRD_PARTY_NOTICES.md).

## Let's connect

![WenBin AI LAB: X, GitHub, website, email, and QR codes for WeChat, WeChat Official Account, Xiaohongshu, and Douyin](docs/assets/contact-16x9.png)
