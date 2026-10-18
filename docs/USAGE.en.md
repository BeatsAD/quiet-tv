# Usage

[简体中文](USAGE.zh-CN.md) · [Home](../README.en.md)

## Remote navigation

| Key | Behavior |
| --- | --- |
| Left / Right | Move within the current row; stop at its edges |
| Up / Down | Move one row between shortcuts, dock, and grid |
| OK | Open the selected application, shortcut, or wallpaper settings |
| Back in the grid | Return to the dock |
| HOME from an application | Return to Quiet TV and restore focus |

An incomplete last row uses the nearest valid column. Focus survives launcher process restarts. Full TV reboot behavior remains unverified.

## Applications and language

Edit `app/src/main/assets/launcher.json`, then rebuild and update the APK. `dock` contains four installed application package names in left-to-right order. `hidden` hides entries without disabling packages. Choose `zh` or `en` for `language`, and `time` or `home` for `greeting`. The current implementation does not implement the prompt's planned automatic `system` language mode.

The included application package list belongs to the Sony adaptation. Use actual packages installed on your target device; see the [Chinese configuration example](USAGE.zh-CN.md).

## Wallpapers

Open Wallpaper Settings from All Apps. Left / Right previews, OK applies, and Back cancels and restores the previous state.

- Default: a black background with an application-colored ambient glow.
- Fixed: select and confirm one landscape with rotation disabled.
- Slideshow: move up to the rotation toggle and enable it; images change every five minutes while the launcher is in the foreground.

Leaving the launcher or opening the picker pauses rotation. Returning starts a new five-minute interval. Selection and mode are stored locally. See [resource notices](../THIRD_PARTY_NOTICES.md) for wallpaper provenance and licensing limits.

## Inputs and settings

Inputs are read from the actual TV Input list. The adapted Sony exposes HDMI 1–4, AV, and Live TV. An empty channel database offers settings without automatically tuning channels. Network and Settings open their corresponding system screens.

Ethernet has been checked. Wi-Fi names depend on location permission and the system location switch; switching to Wi-Fi has not been validated on hardware. Launching a media application does not verify its account, playback, sound, or third-party service.

## Updates and recovery

Follow [installation](INSTALL.en.md) to rebuild and update Quiet TV. Third-party applications use their own update channels. System recovery requires the original baseline and journal for that exact target; never apply the included Sony recovery list to another TV.
