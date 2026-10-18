# 兼容性 / Compatibility

[简体中文首页](../README.md) · [English home](../README.en.md)

## 机型与平台 / Devices and platforms

截至 2026-10-18，已实施机型如下。已适配只覆盖下方列出的验证范围。

As of October 18, 2026, the following device has been adapted. Validation is limited to the functions described below.

| 机型 / Device | 系统 / OS | 状态 / Status |
| --- | --- | --- |
| Sony KD-65X9000H / BRAVIA VH1 | Android 12 / API 31 | 已安装并验证部分功能 / Installed, partially validated |
| 其他国内销售的 Android TV / Google TV / 兼容 Android 电视 | API 31+ | 待逐机适配 / Requires device-specific adaptation |
| Android 11 及以下 / Android 11 and earlier | API <31 | 当前 APK 不支持 / Current APK unsupported |
| Tizen、webOS、HarmonyOS 原生电视及其他非 Android 系统 | 非兼容 Android / Non-compatible Android | 当前方案不支持 / Unsupported |

品牌相同、面板分辨率相同或 Android 版本更高，都不等于已兼容。需确认厂商固件、HOME 切换、系统设置 Intent、TV Input、遥控器按键与 ADB 权限。

The same brand, resolution, or a newer Android version does not establish compatibility. Check vendor firmware, HOME selection, settings intents, TV Input, remote keys, and ADB permissions.

## 已验证与待验收 / Evidence boundaries

| 功能 / Function | 当前证据 / Current evidence |
| --- | --- |
| 编译、debug APK 更新安装 / Build and update | 已有构建、实机安装和 APK MD5 核对记录 / Historical build, install, and APK consistency records |
| 首页、四列网格、遥控器、进程重启焦点 / Launcher, grid, remote, process restart | 已在索尼机型验证 / Verified on the Sony target |
| 默认壁纸、固定图、取消预览 / Default, fixed, cancel preview | 实机与偏好读回记录 / Device captures and preference readback |
| 5 分钟轮播 / Five-minute slideshow | 实测一次正式间隔；未完成全部三张循环 / One full interval checked; complete cycle unverified |
| 网络与系统设置 / Network and settings | 有线入口已验证；Wi-Fi SSID 待验收 / Ethernet checked; Wi-Fi SSID unverified |
| HDMI 2 / HDMI 2 | 输入会话确认；受保护视频无法截图 / Session verified; protected video cannot be captured |
| 频道与直播 / Channels and live TV | 空频道处理已验证；搜台和实际播放未验收 / Empty state checked; tuning and playback unverified |
| 第三方影音应用 / Third-party media apps | 已有启动记录；不等于账号、完整播放和音效通过 / Launch evidence only, not account, full playback, or audio validation |
| 恢复脚本 / Recovery script | 清单及语法核对；未执行整套恢复 / Journal and syntax checked; complete recovery not executed |
| 整机重启、长时间运行、投屏 / Full reboot, long runs, casting | 尚待验收 / Pending hardware validation |

原始细节见 [VERIFICATION.md](../VERIFICATION.md)。`evidence/` 和 `backups/` 是本地保留资料，不随 Git 克隆分发；精选截图见 [assets](assets/README.md)。

Original details are recorded in [VERIFICATION.md](../VERIFICATION.md), in Chinese. Full evidence and backups are local and excluded from Git; selected captures have [provenance records](assets/README.md).

## 工具链与脚本 / Toolchain and scripts

Java、原生 View/Canvas、RecyclerView 1.4.0；compileSdk 36、minSdk/targetSdk 31、AGP 8.12.2、Gradle 8.13。已使用 macOS + JDK 21；构建环境可准备 JDK 17 或 21。其他桌面平台尚未验收。

The project uses Java, native View/Canvas, RecyclerView 1.4.0, compileSdk 36, minSdk/targetSdk 31, AGP 8.12.2, and Gradle 8.13. The recorded host is macOS with JDK 21; use JDK 17 or 21. Other host platforms are unverified.

现有设备脚本固定用户 0 和 ADB 端口 5555，并依赖本地原始备份。已有索尼禁用／恢复记录不能复用到其他设备。新机型请提交[适配信息](https://github.com/BeatsAD/quiet-tv/issues/new?template=device_adaptation.yml)。

Device scripts currently assume user 0 and ADB TCP port 5555, with a local original backup. Do not reuse Sony disable/recovery records elsewhere. Use the [device adaptation form](https://github.com/BeatsAD/quiet-tv/issues/new?template=device_adaptation.yml) for another model.
