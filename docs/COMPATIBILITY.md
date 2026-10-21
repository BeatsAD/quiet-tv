# 兼容范围 / Compatibility

[中文首页](../README.md) · [English home](../README.en.md) · [安装指南](INSTALL.zh-CN.md)

## 源码与下载 / Source and download

| 内容 / Build | 范围 / Scope |
| --- | --- |
| 当前源码 1.1.0 / b28 | `standard`: minSdk21；`legacy19`: minSdk19（Android 4.4）。均为 targetSdk36；尚未发布，真实电视待验收。 / Two source build profiles; unpublished candidates; hardware acceptance pending. |
| 当前公开 [b26 通用兼容测试包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b26) | Android 5.0 / API21 起；含 ARM32、ARM64、x86、x86_64，约 6.50 MiB。已通过部分模拟器验证，尚未安装真实电视。 / Android 5.0+ universal APK; selected emulator checks passed; physical TV acceptance pending. |
| 历史 [b24 通用包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b24) | Android 6.0 / API23 起；原下载保留。 / Android 6.0+; original download retained. |
| 历史 [b23 ARM32 测试包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0) | Android 12 / API31 起，需支持 armeabi-v7a；仅支持 64 位应用的设备不可用。 / Requires Android 12 and ARM32 app support. |
| Android 4.0～4.3、Tizen、webOS 等非 Android 系统 | 不支持 / Unsupported |

Android 4.4 的 `legacy19` 档位采用 Activity 1.9.3、RecyclerView 1.3.2、旧版主题及构建工具生成的位图图标。保留核心桌面、Dock、壁纸、网络、设置、关于、离线许可证和传统 TCP ADB；不包含 Conscrypt/SPAKE2 原生库，不提供六位码配对。信号源回退到系统设置与遥控器信号源键，厂商直切未适配。未裁剪 Debug 包启用旧版 MultiDex，最终优化包需单独验证。

The `legacy19` profile targets Android 4.4 with legacy UI resources and TCP ADB only. It contains no Conscrypt/SPAKE2 native libraries and offers no pairing control. Inputs fall back to system settings; direct vendor input switching and physical TV acceptance remain unverified.

Android 5 使用旧版网络状态接口和安装时权限；频道权限或 TV Input 服务受限时给出提示，ADB 仅提供设备实际支持的传统 TCP 操作，六位码无线配对不可用。离线许可证页使用旧版页面完成回调。

源码兼容处理包括旧系统网络广播与字体回退、系统 HOME 入口检测、旧遥控器及新版系统返回导航，以及按本机实际命令能力开放的应用管理。Android 版本符合最低要求不等于厂商固件全部功能已通过验收。

Source includes legacy networking/font fallbacks, detected HOME settings, Back handling and capability-based package operations. Meeting the minimum Android version does not establish vendor-firmware compatibility.

## 已验证与待验收 / Evidence boundaries

- 本轮 b28 兼容开发：两个构建档位通过构建和完整 Lint（0 错误），各通过 19 项本地单元测试；仅跟踪文件的源码副本不依赖私有测试模块即可构建。API19 / Android 4.4.2 的 32 位 x86 软件模拟环境通过基础桌面、应用枚举、遥控返回、设置、管理入口、关于／离线许可证、Dock 编辑与排序、壁纸选择和重开保存检查。本轮 Debug 功能回归与最终 b28 优化包验收分别执行；最终优化包通过黑盒页面冒烟、覆盖安装数据保留和已安装 APK 哈希核对。覆盖安装保留了首次安装时间及 Dock、壁纸、ADB 密钥、设置记录的原文件哈希。
- 标准版 b28 在 API21 通过基础兼容回归，在 API33 通过真实 TLS 配对启动、取消和关闭控制器后拒绝新配对的检查。兼容版传统 ADB 经环回转发到同一模拟器，完成本机身份核对并拒绝 root 守护进程；这不代表 shell 身份的启禁操作或电视固件网络调试已通过。
- b28 尚未发布，未安装实体电视；Android 4.4 的 ARM32 真机、Wi-Fi／有线切换、HDMI 直切、真实 HOME、实体 Home 键、重启、唤醒与低内存性能仍待验收。x86 软件模拟结果不能替代这些项目。

For the b28 development cycle, both profiles passed builds, full Lint (zero errors) and 19 local unit tests each. API19 / Android 4.4.2 checks use a 32-bit x86 software emulator. Debug UI regressions and final optimized-APK smoke, in-place state preservation and installed-file hash verification were performed separately. The standard b28 profile also passed API21 compatibility and API33 TLS pairing cancellation, including rejection after controller closure. Legacy ADB uses a loopback relay to the same root emulator; shell package operations and actual firmware network debugging remain unverified. b28 is unpublished and has not been installed on a physical TV.

- 公开 b26：Debug 构建与 19 项单元测试通过，Lint 为 0 错误、28 条警告；API21、22、23、31 模拟器通过基础页面、应用枚举、返回导航及离线许可证回归。API21 / 22 另外通过代码与资源裁剪后的通用包冒烟，覆盖 HOME、关于、Dock、离线管理入口与原生库加载。API31 还通过 shell 身份下的测试应用禁用／启用、日志与原状态恢复回归，以及优化包实际 ADB 连接冒烟。API21 / 22 / 31 已安装优化包的 SHA-256 与候选包一致。b26 已作为预发布测试版提供下载，尚未安装真实电视。
- Android 5 的 ADB 验证经转发环回同一模拟器，覆盖密钥读写、本机身份、命令完成标记及 root 守护进程拒绝；未验证 shell 身份下的应用启禁、真实网络调试或固件授权。Wi-Fi / 有线切换、真实输入源、默认 HOME、重启与待机唤醒仍需目标电视验收。
- 历史兼容分支曾在 API23、31、36、37 模拟环境验证部分页面和操作；通用 Android 镜像不能代替 TV 固件，历史分支结果也不能直接视作集成包全部通过。
- 已发布 b23 曾在一台 Android 12 / ARM32 电视覆盖安装、核对 APK 哈希，并检查 HOME 返回、壁纸和 Dock；范围见 [Releases](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0)。
- 历史公开 b24 通用优化包构建通过，完整 Lint 为 0 错误、27 条警告；已核对实际 APK 最低 API23、架构、签名和 GitHub 回下载哈希，签名与 b23 一致。**b24 发布时未安装到电视或模拟器**；Android 6、7 及其他厂商固件、真实 HOME 切换／恢复、长期驻留、投屏和完整播放仍需分别验收。详情见 [b24 发布说明](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b24)。
- 无线配对仍为实验入口，要求 API33+、可加载原生库及设备实际提供配对服务；六位码成功配对不能由版本号或构建通过代替。
- 未知固件可读取应用，未经匹配和核验的系统包操作保持只读；不能套用其他设备的禁用清单。

Published b26 passed Debug build, 19 unit tests and Lint (0 errors, 28 warnings). API21/22/23/31 emulator checks cover basic UI, app enumeration, Back and offline licenses; the optimized universal APK also passed API21/22 UI and native-library smoke checks. Android 5 ADB checks use a forwarded loop to the same emulator and verify identity, output markers and root rejection; shell package writes and actual TV firmware remain unverified. API31 also passed shell-authorized fixture disable/enable, operation journal, exact state restoration and optimized local ADB smoke. Installed optimized APK hashes match the candidate on API21/22/31. b26 is a prerelease and has not been installed on a physical TV.

The published b24 universal APK passed its optimized build, full Lint (0 errors, 27 warnings), package and signature checks, and GitHub download hash verification. It has not been installed on a TV or emulator. Historical emulator and b23 hardware checks do not validate b24, other vendors, real HOME changes, pairing, casting, or long-running operation. Unknown firmware retains read-only system-package behavior.

## 默认桌面与恢复

设备需实际提供可用的 HOME 入口、系统设置、TV Input、遥控键和本机调试能力。默认桌面以读回的真实组件为准；命令返回 Success 不代表 HOME 已切换。保留原桌面及恢复记录，先验证再决定是否处理预装应用。流程见[首次设置](SETUP.md)和[应用管理](APP_MANAGEMENT.md)。
