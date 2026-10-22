# Usage

[简体中文](USAGE.zh-CN.md) · [Home](../README.en.md)

## Remote navigation

| Key | Behavior |
| --- | --- |
| Left / Right | Move within the current row; stop at its edges |
| Up / Down | Move one row between shortcuts, dock, and grid |
| OK | Open the selected application, shortcut, or settings module |
| Back in the grid | Return to the dock |
| HOME from an application | Return to Quiet TV and restore focus |

An incomplete last row uses the nearest valid column. Focus survives launcher process restarts. One actual Sony TV reboot has been verified: Quiet TV opened automatically and Dock/wallpaper settings persisted. Long-running behavior remains unverified.

## App management and Dock

Open 应用管理 (App Management) from All Apps. Management controls in this first Sony-targeted version are in Chinese.

- Dock 管理 supports up to four shortcuts: add, replace, remove, or move left/right. OK saves reordering; Back cancels. Editing works without ADB.
- **Home order**: open App management → 首页排序. Press OK to pick up an entry, use the arrows to move it, and press OK again to drop it. Back cancels the current move; 保存顺序 saves the page. Leaving with changes offers save, discard, or continue editing. Restore default also requires saving. Wallpaper Settings, Desktop Settings and App Management can all move.
- Search by application name or package, and filter all, user, preinstalled, disabled, or restorable packages.
- The top-right ADB status opens local TCP connection settings (default port 5555). Enable network debugging on the TV and approve QuietTV's independent key. Pairing-code wireless debugging is not implemented.
- Operations affect one package for the current Android user. Disabling retains data; uninstalling removes that user's app data. Preinstalled uninstall leaves the system APK; install-existing is offered only when a system APK is retained and never restores deleted data.
- Quiet TV, critical components, and unverified system packages are read-only. Operation history records configuration states, not user-data backups. Reconnect and refresh after uncertain results; do not repeat uninstall.

Disabled Dock entries retain their order and reappear when enabled. Confirmed uninstalls remove the shortcut. An explicitly empty Dock stays empty across APK updates. See [implementation and hardware evidence](APP_MANAGEMENT.md).

Home ordering works without ADB and leaves Dock independent. In custom order, newly visible apps append at the end; updates and name changes preserve positions. Disabled or pinned apps retain their order records until shown again; uninstalls remove records. Default order puts Wallpaper Settings, Desktop Settings and App Management first, followed by apps sorted by name. Settings stay on the device across normal in-place updates; physical remote, reboot, and upgrade behavior must be accepted on each target TV.

## Language and initial configuration

Edit `app/src/main/assets/launcher.json`, then rebuild and update the APK. `dock` supplies up to four initial package names in left-to-right order. Once migrated, edit Dock in the TV interface; bundled changes do not overwrite saved preferences. `hidden` hides entries without disabling packages. Choose `zh` or `en` for `language`, and `time` or `home` for `greeting`. The current implementation does not implement the prompt's planned automatic `system` language mode.

The included application package list belongs to the Sony adaptation. Use actual packages installed on your target device; see the [Chinese configuration example](USAGE.zh-CN.md).

## Wallpapers

Open Wallpaper Settings from All Apps. Left / Right previews; OK or Use wallpaper saves the choice. Back cancels an unapplied preview and returns home. In Manage mode, Back first exits management; completed deletions are not undone.

- **Default color theme:** a black background with an app-colored glow. Always available and cannot be deleted.
- **Photos and slideshow:** applying a photo disables rotation. The slideshow cycles through available built-in and uploaded images every five minutes. Opening the picker or leaving the launcher pauses it; returning starts a new interval.
- **Manage:** remove one image at a time. Built-in images leave the list and slideshow and can be restored. Uploaded images delete only the TV copy. Deleting the fixed current photo restores the color theme; deleting the slideshow’s current photo advances to the next. Fewer than two photos disables rotation.
- **Upload:** select Upload on the TV. Scan its QR code or open its complete session address on a phone or computer on the same LAN. Upload, press Back on the TV, then select the new photo to apply it. Wi-Fi and Ethernet are supported; ADB, accounts, and cloud services are not required.

Accepts JPG, PNG and static WebP: up to 20 MB and 40 megapixels per input, 20 uploaded photos, and 50 MB total processed storage. Saved images retain their aspect ratio with a maximum long edge of 1920 pixels; orientation is corrected and display uses centered cropping. Transparent areas use black. Only the processed TV copy is saved; originals stay on the phone or computer. Convert HEIC to JPG before uploading.

The server runs only while the TV upload dialog is open. Leaving it, backgrounding Quiet TV, changing network addresses, or ten idle minutes ends the session and invalidates its address. If connection fails, check guest-network/device isolation and try a system browser. Failed uploads and storage errors leave existing wallpapers unchanged.

APK updates preserve images and removed built-ins; uninstalling or clearing application data removes uploaded photos. Existing selections and rotation settings migrate on first launch. These management/upload additions describe current source, are not included in the existing b26 download, and await real-TV acceptance. See [resource notices](../THIRD_PARTY_NOTICES.md) for provenance and licensing limits.

## Desktop settings

Open the independent Desktop Settings module after Wallpaper Settings in All Apps. Toggle Show time and date or Show right-side buttons (Inputs, Network, Settings); both default to on. Changes apply when returning home and are saved automatically across normal in-place updates. Remote navigation skips hidden buttons; Desktop Settings remains accessible in All Apps to restore them. Existing custom orders receive the new entry immediately after Wallpaper Settings; it can then be moved in Home order. This feature describes current source, is absent from existing downloads, and awaits vendor-TV acceptance.

## Inputs and settings

Inputs are read from the actual TV Input list. The adapted Sony exposes HDMI 1–4, AV, and Live TV. An empty channel database offers settings without automatically tuning channels. Network and Settings open their corresponding system screens.

New source checks known vendor input panels when the standard service is absent, restricted or empty. Only installed, enabled system components accessible to Quiet TV are offered. A single usable panel opens directly when the standard list is unavailable; multiple panels require a choice. Normal lists retain their entries and add usable system panels. BYINTEK U4's reported bogus standard list prefers its vendor panel. If a standard launch throws an error, the user can choose a system panel; a black screen without a launch error requires returning to Inputs and choosing that panel manually.

If no route is usable, a diagnostic dialog offers system settings. This does not establish that HDMI/AV ports are absent. Components and permissions are never changed automatically. See [compatibility](COMPATIBILITY.md) for the Hisense, TCL and BYINTEK candidates and hardware acceptance limits. The published b26 does not contain this fallback.

Ethernet has been checked. Wi-Fi names depend on location permission and the system location switch; switching to Wi-Fi has not been validated on hardware. Launching a media application does not verify its account, playback, sound, or third-party service.

## Updates and recovery

Follow [installation](INSTALL.en.md) to rebuild and update Quiet TV. Third-party applications use their own update channels. System recovery requires the original baseline and journal for that exact target; never apply the included Sony recovery list to another TV.
