# 兼容性 / Compatibility

[简体中文首页](../README.md) · [English home](../README.en.md)

当前源码集成候选为 1.1.0 / b24，最低 Android 6.0（API 23），目标 SDK 为 36；公开 b23 APK 仍最低 API31。以下索尼真机结果属于历史包；新候选的代码、模拟器与待验收范围见[版本兼容记录](android-compatibility.md)。

## 机型与平台 / Devices and platforms

截至 2026-10-18，已实施机型如下。已适配只覆盖下方列出的验证范围。

As of October 18, 2026, the following device has been adapted. Validation is limited to the functions described below.

| 机型 / Device | 系统 / OS | 状态 / Status |
| --- | --- | --- |
| Sony KD-65X9000H / BRAVIA VH1 | Android 12 / API 31 | 已安装并验证部分功能 / Installed, partially validated |
| 其他国内销售的 Android TV / Google TV / 兼容 Android 电视 | API 23+ | 版本兼容候选，待逐机验收 / Compatibility candidate; hardware validation required |
| Android 5.1 及以下 / Android 5.1 and earlier | API <23 | 当前 APK 不支持 / Current APK unsupported |
| Tizen、webOS、HarmonyOS 原生电视及其他非 Android 系统 | 非兼容 Android / Non-compatible Android | 当前方案不支持 / Unsupported |

品牌相同、面板分辨率相同或 Android 版本更高，都不等于已兼容。需确认厂商固件、HOME 切换、系统设置 Intent、TV Input、遥控器按键与 ADB 权限。

The same brand, resolution, or a newer Android version does not establish compatibility. Check vendor firmware, HOME selection, settings intents, TV Input, remote keys, and ADB permissions.

## 已验证与待验收 / Evidence boundaries

`1.1.0 / b4` 首次设置候选已加入按功能检测与无法切换默认桌面时的退出路径。不同品牌扫描及操作能力分别核对；未知固件的系统包仍只读。本次新增流程的证据与保留项见 [首次设置验证](SETUP_VERIFICATION.md)，不将下表的历史真机结果沿用为新流程通过。

| 功能 / Function | 当前证据 / Current evidence |
| --- | --- |
| 编译、debug APK 更新安装 / Build and update | 已有构建、实机安装和 APK MD5 核对记录 / Historical build, install, and APK consistency records |
| 首页、四列网格、遥控器、进程重启焦点 / Launcher, grid, remote, process restart | 已在索尼机型验证 / Verified on the Sony target |
| 电视端本机 ADB / In-app local ADB | 当前索尼固件的传统 TCP 独立密钥连接、当前用户识别及专用测试包启停通过 / TCP local connection and fixture disable/enable verified |
| 应用管理与可编辑 Dock / App management and editable Dock | 0～4 个、替换、排序保存／取消、列表筛选、单包启停／卸载及首页联动已实测；预装卸载／恢复待验收 / UI and fixture operations verified; preinstalled uninstall/restore pending |
| 默认壁纸、固定图、取消预览 / Default, fixed, cancel preview | 实机与偏好读回记录 / Device captures and preference readback |
| 5 分钟轮播 / Five-minute slideshow | 实测一次正式间隔；未完成全部三张循环 / One full interval checked; complete cycle unverified |
| 网络与系统设置 / Network and settings | 有线入口已验证；Wi-Fi SSID 待验收 / Ethernet checked; Wi-Fi SSID unverified |
| HDMI 2 / HDMI 2 | 输入会话确认；受保护视频无法截图 / Session verified; protected video cannot be captured |
| 频道与直播 / Channels and live TV | 空频道处理已验证；搜台和实际播放未验收 / Empty state checked; tuning and playback unverified |
| 第三方影音应用 / Third-party media apps | 已有启动记录；不等于账号、完整播放和音效通过 / Launch evidence only, not account, full playback, or audio validation |
| 恢复脚本 / Recovery script | 清单及语法核对；未执行整套恢复 / Journal and syntax checked; complete recovery not executed |
| 整机重启 / Full reboot | 本次实测启动完成后自动进入 Quiet TV，Dock／壁纸／禁用清单保持一致 / One actual reboot verified with state preserved |
| 长时间运行、投屏 / Long runs, casting | 尚待验收 / Pending hardware validation |

原始细节见 [VERIFICATION.md](../VERIFICATION.md)。`evidence/` 和 `backups/` 是本地保留资料，不随 Git 克隆分发；精选截图见 [assets](assets/README.md)。

Original details are recorded in [VERIFICATION.md](../VERIFICATION.md), in Chinese. Full evidence and backups are local and excluded from Git; selected captures have [provenance records](assets/README.md).

## 工具链与脚本 / Toolchain and scripts

Java、原生 View/Canvas、RecyclerView 1.4.0；compileSdk 36、minSdk/targetSdk 31、AGP 8.12.2、Gradle 8.13。已使用 macOS + JDK 21；构建环境可准备 JDK 17 或 21。其他桌面平台尚未验收。

The project uses Java, native View/Canvas, RecyclerView 1.4.0, compileSdk 36, minSdk/targetSdk 31, AGP 8.12.2, and Gradle 8.13. The recorded host is macOS with JDK 21; use JDK 17 or 21. Other host platforms are unverified.

应用内管理实时识别当前用户，TCP 端口可编辑；只在当前索尼固件验证。现有电脑脚本仍固定用户 0 和 ADB 端口 5555，并依赖本地原始备份。已有索尼禁用／恢复记录不能复用到其他设备。新机型请提交[适配信息](https://github.com/BeatsAD/quiet-tv/issues/new?template=device_adaptation.yml)。

In-app management discovers the current user and allows editing the TCP port; it is verified only on the current Sony firmware. Existing host scripts still assume user 0 and ADB TCP port 5555, with a local original backup. Do not reuse Sony disable/recovery records elsewhere. Use the [device adaptation form](https://github.com/BeatsAD/quiet-tv/issues/new?template=device_adaptation.yml) for another model.


## 优化候选与旧系统实验（2026-10-19）

默认构建继续为 API31；另提供明确带 `_api26` 后缀的实验候选。当前源码已隔离 API29 HOME 角色入口、兼容旧字体与版本号 API，并在 boot_id 读取受限时执行一次性本机文件挑战。未知固件的预装包仍只读，不按社区参考清单自动开放权限。

Android 8/API26、Android 9/API28、Android 11/API30 通用 ARM64 AOSP 镜像已验证原生首页、关于页、Dock、JNI、本机 ADB 往返和首次设置入口。旧镜像的 ADB 通过专用测试转发连接同一模拟器，不能计作该固件直接开放本机 TCP 端口。API26 的不可读挑战负向测试拒绝连接，不执行包写入。通用镜像没有电视硬件能力，不代表电视信号源或默认 HOME 接管已适配。

Android TV 12/API31 新环境已验证优化包首次密钥生成与实际本机 ADB、原生入口；Android TV 13/API33 验证已有密钥连接、HOME 冲突及失败恢复、原生设置与遥控焦点回归。相关结果分别记录，详见[优化验证说明](OPTIMIZATION.md)。

旧 ARM32 电视、其他真实固件、低于 1GB 设备以及六位码成功配对仍待实测；正常 APK 暂不下调最低系统，不扩大生产设备操作规则。
