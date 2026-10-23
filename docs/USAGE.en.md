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

All Apps and Home order both use five columns. An incomplete last row uses the nearest valid column. Focus survives launcher process restarts. A historical build passed one Sony TV reboot. That result does not establish reboot or upgrade-state acceptance for 1.1.2.

Development builds reduce the top gap when entering All Apps, keeping the clock, date and visible shortcuts fixed. More apps scroll continuously by row with focus, without scrollbars, an app count or footer labels. Up from the first grid row or Back from the grid restores the original home layout and the dock item selected before entering the grid. This change is not included in published 1.1.2 APKs.

This guide describes published [1.1.2 test builds](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.2). Images are captured on a Sony TV running standard build 34 and do not establish full feature or other-device acceptance.

## App management and Dock

Open 应用管理 (App Management) from All Apps. Management controls in this first Sony-targeted version are in Chinese.

- Dock 管理 supports up to five shortcuts: add, replace, remove, or move left/right. OK saves reordering; Back cancels. Editing works without ADB.
- **Home order**: open App management → 首页排序. Press OK to pick up an entry, use the arrows to move it, and press OK again to drop it. Back cancels the current move; 保存顺序 saves the page. Leaving with changes offers save, discard, or continue editing. Restore default also requires saving. Wallpaper Settings, Desktop Settings and App Management can all move.
- Search by application name or package, and filter all, user, preinstalled, disabled, or restorable packages.
- The top-right ADB status opens local TCP connection settings (default port 5555). Enable network debugging on the TV and approve QuietTV's independent key. Six-digit wireless pairing is experimental, requires API33+ and actual device support, and is absent from legacy19.
- Operations affect one package for the current Android user. Disabling retains data; uninstalling removes that user's app data. Preinstalled uninstall leaves the system APK; install-existing is offered only when a system APK is retained and never restores deleted data.
- Quiet TV, critical components, and unverified system packages are protected from system-state changes. Operation history records configuration states, not user-data backups. Reconnect and refresh after uncertain results; do not repeat uninstall.

Disabled Dock entries retain their order and reappear when enabled. Confirmed uninstalls remove the shortcut. An explicitly empty Dock stays empty across APK updates. See [implementation and hardware evidence](APP_MANAGEMENT.md).

Development source adds 应用列表 → 更换封面 (Change cover). Scan with a phone on the same network, select and crop an image, then confirm on the TV to apply it. 恢复默认 restores the app's current original artwork after confirmation. Recommended: 720 × 406 px; minimum: 360 × 203 px. JPG, PNG or static WebP, up to 2 MB per image. AI can help create a suitable image; no generation service is included. Cover changes work without ADB and retain the original app icon and package-operation protection. Filters stay above the list; ADB, More and About share the top-right row. These changes are not included in published 1.1.2 packages. See [app covers](APP_MANAGEMENT.md#自定义应用封面) for scope and hardware acceptance limits.

Home ordering works without ADB and leaves Dock independent. In custom order, newly visible apps append at the end; updates and name changes preserve positions. Disabled or pinned apps retain their order records until shown again; uninstalls remove records. Default order puts Wallpaper Settings, Desktop Settings and App Management first, followed by apps sorted by name. Settings stay on the device across normal in-place updates; physical remote, reboot, and upgrade behavior must be accepted on each target TV.

![Home ordering on the Sony TV, 1.1.2 / 34](assets/sort-1.1.2.png)

## Boot-ad management

Open 应用管理 → 更多 for boot-ad management. Detection and actions depend on matching local rules and actual service states. Restoration uses the recorded original state; unmatched devices stay read-only. No rule has passed Quiet TV hardware acceptance yet, and this does not promise removal of every vendor ad. See [app management](APP_MANAGEMENT.md).

## Language and initial configuration

Edit `app/src/main/assets/launcher.json`, then rebuild and update the APK. `dock` supplies up to five initial package names in left-to-right order. Once migrated, edit Dock in the TV interface; bundled changes do not overwrite saved preferences. `hidden` hides entries without disabling packages. Choose `zh` or `en` for `language`, and `time` or `home` for `greeting`. The current implementation does not implement the prompt's planned automatic `system` language mode.

The included application package list belongs to the Sony adaptation. Use actual packages installed on your target device; see the [Chinese configuration example](USAGE.zh-CN.md).

## Wallpapers

Open Wallpaper Settings from All Apps. Left / Right previews; OK or Use wallpaper saves the choice. Back cancels an unapplied preview and returns home. In Manage mode, Back first exits management; completed deletions are not undone.

- **Default color theme:** a black background with an app-colored glow. Always available and cannot be deleted.
- **Photos and slideshow:** applying a photo disables rotation. The slideshow cycles through available built-in and uploaded images every five minutes. Opening the picker or leaving the launcher pauses it; returning starts a new interval.
- **Daily wallpaper:** focus the card after Default color theme to preview; press OK or click once to apply and enable daily updates. Daily settings offers Bing China and Btstu Landscape. Confirm source applies it directly; cancelling or failure keeps the previous source and wallpaper. Check for updates checks the selected draft source without enabling it. Daily mode disables local slideshow; selecting a local wallpaper leaves daily mode.
- **Daily updates and offline use:** updates run as needed when the home screen resumes, the day changes or connectivity returns. A new image is saved before replacing the previous one. Offline use keeps the cache. Btstu keeps one landscape per day. The daily card cannot be deleted and does not count towards upload limits. API and image CDN reachability depends on the network and TV firmware.
- **Manage:** long-press OK on an image, or select Manage and its delete control, then confirm deletion of that one image. Built-in images leave the list and slideshow and can be restored. Uploaded images delete only the TV copy. Deleting the fixed current photo restores the color theme; deleting the slideshow’s current photo advances to the next. Fewer than two photos disables rotation.
- **Upload:** select Upload on the TV. On the same LAN, scan its QR code to choose a photo directly, or open the short address on a computer and enter the six-digit code shown on the TV. Upload, press Back on the TV, then select the new photo to apply it. Wi-Fi and Ethernet are supported; ADB, accounts, and cloud services are not required.

Accepts JPG, PNG and static WebP: up to 20 MB and 40 megapixels per input, 20 uploaded photos, and 50 MB total processed storage. Newly saved images retain their aspect ratio and follow the actual rendering size, up to a 3840-pixel long edge; low-memory devices or memory classes below 256 MB use a 1920-pixel limit; orientation is corrected and display uses centered cropping. Transparent areas use black. Only the processed TV copy is saved; originals stay on the phone or computer. Convert HEIC to JPG before uploading.

The server runs only while the TV upload dialog is open. Leaving it, backgrounding Quiet TV, changing network addresses, or ten idle minutes ends the session and invalidates its QR code and connection code. Reconnecting refreshes both. Five incorrect codes pause verification for 30 seconds. If connection fails, check guest-network/device isolation and try a system browser. Failed uploads and storage errors leave existing wallpapers unchanged.

APK updates preserve images and removed built-ins; uninstalling or clearing application data removes uploaded photos. Existing selections and rotation settings migrate on first launch. Both 1.1.2 downloads include management and upload. Sony captures show the pages only; complete upload, deletion/recovery and upgrade-state acceptance remain pending. See [resource notices](../THIRD_PARTY_NOTICES.md) for provenance and licensing limits.

![Wallpaper management on the Sony TV, 1.1.2 / 34](assets/wallpaper-manage-1.1.2.png)

The upload dialog contains a local address, connection code and QR code, so its raw screenshot is kept private. Follow the current TV session; closing it invalidates that session.

## Desktop settings

Open the independent Desktop Settings module after Wallpaper Settings in All Apps. Time/date and weather have separate toggles. Under Quick buttons, toggle Inputs, Network and Settings individually; all default to visible. Remaining buttons keep their original order, align to the right, and remote navigation skips hidden buttons. Changes apply when returning home and are saved across normal in-place updates. Each unset individual preference inherits the previous right-side button group setting. Even when all three buttons are hidden, restore them through All Apps → Desktop Settings. Existing custom orders receive the entry after Wallpaper Settings; it can then be moved in Home order. Published 1.1.2 packages have the original group toggle; individual visibility controls are not released yet. Physical remote, restart and in-place upgrade persistence need acceptance on each target TV.

![Desktop display settings on the Sony TV, 1.1.2 / 34](assets/desktop-settings-1.1.2.png)

## Home weather (development source)

A static icon, temperature, conditions and city appear beside the greeting on the same line; clock/date positions stay unchanged. Toggle weather under All Apps → Desktop Settings → Desktop. The Weather category provides automatic location, city selection, refresh and service information. Turning off automatic location pins the resolved city; if no city is available, choose one first, or cancel to keep automatic location. Choosing a city turns automatic location off. City preferences remain editable while weather is hidden, without location or weather refresh requests. No ADB is needed and the system Settings button is unchanged. This feature is not included in the published 1.1.2 packages above.

The client calls the [free UAPI weather service](https://uapis.cn/docs/api-reference/get-misc-weather) directly over HTTPS, with no account, API key or self-hosted server. With existing approximate-location permission, a bounded system network-location request is tried first; coordinates are rounded to two decimal places for region lookup. Otherwise the service estimates a city from the network egress IP. A manually pinned city always takes priority. Automatic location in Weather Settings can request approximate permission; denial still permits IP estimation or manual selection. IP estimates can be inaccurate behind a proxy or ISP gateway. The service receives the request IP and queried region; see its [privacy and terms](https://uapis.cn/docs/getting-started/privacy-policy).

Weather refreshes about every 30 minutes while Home is visible. Returning Home uses cached data first; backgrounding cancels location and scheduled requests. Home shows only the weather icon, temperature, conditions and city. Updating or failed refreshes keep usable cached weather without appending “Not updated” or other status messages. Weather is hidden when no usable data exists, the cache is over six hours old, or weather display is off. Data older than one hour, refresh failures and update times remain visible in Weather settings. If the upstream observation time cannot be parsed, cache retention uses fetch time and settings explicitly report that observation time is unavailable. Missing data never becomes 0°C or a default city's weather. Free access has dynamic rate limits and no perpetual availability guarantee; a 429 pauses refresh for at least 30 minutes and respects a longer service retry delay.

Android 4.4 enables TLS 1.2 for this connection only, retaining system trust and hostname verification with no plaintext fallback. Old firmware trust stores and OEM location behavior still need hardware acceptance. Icons use local native drawing, without video, WebView or continuous animation.

## Inputs and settings

Inputs are read from the actual TV Input list. The adapted Sony exposes HDMI 1–4, AV, and Live TV. An empty channel database offers settings without automatically tuning channels. Network and Settings open their corresponding system screens.

The 1.1.2 standard profile checks known vendor input panels when the standard service is absent, restricted or empty. Only installed, enabled system components accessible to Quiet TV are offered. A single usable panel opens directly when the standard list is unavailable; multiple panels require a choice. Normal lists retain their entries and add usable system panels. BYINTEK U4's reported bogus standard list prefers its vendor panel. If a standard launch throws an error, the user can choose a system panel; a black screen without a launch error requires returning to Inputs and choosing that panel manually.

If no route is usable, a diagnostic dialog offers system settings. This does not establish that HDMI/AV ports are absent. Components and permissions are never changed automatically. See [compatibility](COMPATIBILITY.md) for the Hisense, TCL and BYINTEK candidates and hardware acceptance limits. The 1.1.2 standard APK includes this fallback; legacy19 uses settings or the physical input key.

Ethernet has been checked. Wi-Fi names depend on location permission and the system location switch; switching to Wi-Fi has not been validated on hardware. Launching a media application does not verify its account, playback, sound, or third-party service.

## Updates and recovery

Follow [installation](INSTALL.en.md) to rebuild and update Quiet TV. Third-party applications use their own update channels. System recovery requires the original baseline and journal for that exact target; never apply the included Sony recovery list to another TV.

Version 1.1.2 shares a callable app catalog across Home, sorting and favorites, adds detected OEM settings/input routes, and retains newly uploaded images up to the actual rendering size (3840px maximum, 1920px on low-memory devices). Existing photos are unchanged. Both current APKs include the shared app/image changes; vendor input routing applies to standard, while legacy19 uses system settings. See [compatibility scope](COMPATIBILITY.md#本轮源码兼容改进).
