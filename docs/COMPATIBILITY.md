# 兼容范围 / Compatibility

[中文首页](../README.md) · [English home](../README.en.md) · [安装指南](INSTALL.zh-CN.md)

## 源码与下载 / Source and download

| 内容 / Build | 范围 / Scope |
| --- | --- |
| 当前源码 1.1.0 / b30 | `standard`: minSdk21；`legacy19`: minSdk19（Android 4.4）。均为 targetSdk36；已发布 b30 兼容测试包，真实电视待验收。 / Two source profiles; b30 legacy prerelease published; hardware acceptance pending. |
| Android 4.4 公开 [b30 兼容测试包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b30) | API19 起，约 2.30 MiB，无原生库，传统 TCP ADB；原生功能与实机待验收。 / API19+, no native libraries; native runtime acceptance pending. |
| 标准版 [b26 通用兼容测试包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b26) | Android 5.0 / API21 起；含 ARM32、ARM64、x86、x86_64，约 6.50 MiB。已通过部分模拟器验证，尚未安装真实电视。 / Android 5.0+ universal APK; selected emulator checks passed; physical TV acceptance pending. |
| 历史 [b24 通用包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b24) | Android 6.0 / API23 起；原下载保留。 / Android 6.0+; original download retained. |
| 历史 [b23 ARM32 测试包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0) | Android 12 / API31 起，需支持 armeabi-v7a；仅支持 64 位应用的设备不可用。 / Requires Android 12 and ARM32 app support. |
| Android 4.0～4.3、Tizen、webOS 等非 Android 系统 | 不支持 / Unsupported |

Android 4.4 的 `legacy19` 档位采用 Activity 1.9.3、RecyclerView 1.3.2、旧版主题及构建工具生成的位图图标。保留核心桌面、Dock、壁纸、网络、设置、关于、离线许可证和传统 TCP ADB；不包含 Conscrypt/SPAKE2 原生库，不提供六位码配对。信号源回退到系统设置与遥控器信号源键，厂商直切未适配。未裁剪 Debug 包启用旧版 MultiDex，最终优化包需单独验证。

The `legacy19` profile targets Android 4.4 with legacy UI resources and TCP ADB only. It contains no Conscrypt/SPAKE2 native libraries and offers no pairing control. Inputs fall back to system settings; direct vendor input switching and physical TV acceptance remain unverified.

Android 5 使用旧版网络状态接口和安装时权限；频道权限或 TV Input 服务受限时给出提示，ADB 仅提供设备实际支持的传统 TCP 操作，六位码无线配对不可用。离线许可证页使用旧版页面完成回调。

源码兼容处理包括旧系统网络广播与字体回退、系统 HOME 入口检测、旧遥控器及新版系统返回导航，以及按本机实际命令能力开放的应用管理。Android 版本符合最低要求不等于厂商固件全部功能已通过验收。

Source includes legacy networking/font fallbacks, detected HOME settings, Back handling and capability-based package operations. Meeting the minimum Android version does not establish vendor-firmware compatibility.

## 已验证与待验收 / Evidence boundaries

- 公开 b30：基于合入安卓 4.4 与信号源回退的公开 main 构建；优化构建、19 项单元测试、Lint（0 错误、39 条警告）、API19 元数据、v1/v2 签名与包内容校验通过。与 b26 使用相同测试证书。b30 未完成原生模拟器功能验收，未安装真实电视；下列 b28 记录仅为历史开发证据，不能视为 b30 已通过相同功能验收。

Published b30 passed the optimized build, 19 unit tests, Lint (0 errors, 39 warnings), API19 metadata, v1/v2 signatures and package-content checks. Its test signer matches b26. Native emulator acceptance was not completed for b30 and no physical TV has received it. The b28 evidence below is historical and does not validate the new APK.

- 历史 b28 兼容开发：两个构建档位通过构建和完整 Lint（0 错误），各通过 19 项本地单元测试；仅跟踪文件的源码副本不依赖私有测试模块即可构建。API19 / Android 4.4.2 的 32 位 x86 软件模拟环境通过基础桌面、应用枚举、遥控返回、设置、管理入口、关于／离线许可证、Dock 编辑与排序、壁纸选择和重开保存检查。本轮 Debug 功能回归与最终 b28 优化包验收分别执行；最终优化包通过黑盒页面冒烟、覆盖安装数据保留和已安装 APK 哈希核对。覆盖安装保留了首次安装时间及 Dock、壁纸、ADB 密钥、设置记录的原文件哈希。
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

## 信号源兼容

源码新增原厂信号源面板回退，公开 b26 安装包不包含此功能。Android 标准 TV Input 列表为空或服务不可用，并不证明电视没有输入接口。标准路径继续读取本机输入；回退按实际安装的系统组件、启用状态、导出状态和调用权限检测，不按品牌名称套用固定 HDMI 数量，也不自动修改系统组件或权限。

| 候选固件 / Candidate | 源码行为 / Source behavior | 依据与验证边界 / Evidence |
| --- | --- | --- |
| 海信 / Vidda MT9653 | 检测 `com.hisense.tv.inputs/com.hisense.tv.InputsUIService`，通过 `com.hisense.tv.start_inputs` 打开原厂面板。标准列表正常时保留列表。 | [上游记录](https://github.com/Gathin23/cleanyourtv/blob/3e996739ce0b470b51c1c438bf5d0559d1c9e7eb/examples/vidda-mt9653/debloat.txt#L45)称该固件标准输入为空、原厂面板可以切换；Quiet TV 尚未在目标海信验证，不能视为所有海信支持。 |
| TCL ak30a5 / Android 9 | 检测 `com.tcl.settings/com.tcl.insrc.ShowWindowService`，通过 `com.tcl.insrc.SHOW_WINDOW` 打开面板。 | [上游 Issue #37](https://github.com/Gathin23/cleanyourtv/issues/37)确认面板调用，但该机型拒绝安装第三方 HOME；存在面板入口不代表 Quiet TV 可以安装，其他 TCL 固件仍需单独验证。 |
| BYINTEK U4 | 检测 `com.sdt.app.skysettings/.entry.activity.QuickSourceActivity`，优先打开面板，避免上游报告的虚假 HDMI／AV 列表。 | [上游记录](https://github.com/Gathin23/cleanyourtv/blob/3e996739ce0b470b51c1c438bf5d0559d1c9e7eb/examples/byintek-u4/README.md)；Quiet TV 真机待验收。 |

入口配置随源码保存在 [tv-input-profiles.json](../app/src/main/assets/tv-input-profiles.json)。组件名吻合仍需通过运行时权限与状态检查；多个可用面板由用户选择。标准切换发生异常时提供系统面板选项；调用成功但画面黑屏无法据此自动判断，可返回信号源菜单手动打开系统面板。未知机型没有匹配入口时提供原因和系统设置入口。

本次新增逻辑通过 9 项本地单元测试、Debug 构建、Lint 和配置校验。API31 专用模拟器回归覆盖标准服务缺失／受限／异常诊断、原生系统组件检查、Activity／Service 启动分支与失败处理、空输入提示及返回导航；系统组件启动在测试中被拦截，不代表已打开海信／TCL／BYINTEK 原厂面板。目标海信型号与固件尚未提供，实际 HDMI 切换、画面、声音及实体遥控器均待实机验收。

上游还记录 Vidda 的 `com.hisense.tv.tiletransition` 是实际切换依赖，禁用后可能出现“面板打开但 HDMI 不切换”。这是特定固件的诊断线索，Quiet TV 不会自动启用它，也不把信号源适配配置作为禁用／卸载系统包的授权。

System-panel fallback exists in source only, not in the published b26. Profiles identify exact system components and require runtime enabled/exported/permission checks. Standard inputs remain the default, with a vendor-panel preference only for BYINTEK U4's reported bogus list. These are upstream reports, not Quiet TV hardware acceptance or support for every model of a brand. Dispatch success does not verify picture, sound, physical remote behavior or actual source switching. No system package or permission is changed automatically.

## 默认桌面与恢复

设备需实际提供可用的 HOME 入口、系统设置、TV Input、遥控键和本机调试能力。默认桌面以读回的真实组件为准；命令返回 Success 不代表 HOME 已切换。保留原桌面及恢复记录，先验证再决定是否处理预装应用。流程见[首次设置](SETUP.md)和[应用管理](APP_MANAGEMENT.md)。

## 上游兼容线索与 Quiet TV 状态

以下记录来自 Aerial 的源码和用户反馈，调查日期 2026-10-22。它们不授予系统包操作权限，也不计入 Quiet TV 已验收机型。完整原始日志、设备资料和过程记录保留本地；公开反馈只保留脱敏必要信息。

| 范围 | 上游记录与方案 | Quiet TV 状态及验收重点 |
| --- | --- | --- |
| 小米 MiTV4-ANSM0 / Android 6 | [#81](https://github.com/pengchujin/aerial-tv/issues/81)：服务绑定后仍可能没有有效窗口事件；用户独立 UsageStats 辅助仅有延迟接管和有限重启实测 | 无主程序通用接管结论；目标固件需分别测试事件、实体 HOME、重启、短/长待机及暂停恢复 |
| 酷开 / Android 8 | [#69](https://github.com/pengchujin/aerial-tv/issues/69)：私有 HOME.CC 类别，识别修复不等于改写固件主页 | Quiet TV 厂商接管待验收；不能按品牌批量应用 |
| 酷开 / Android 11 | [#122](https://github.com/pengchujin/aerial-tv/issues/122)：LITE_HOME 与 HOME.CC 不同，用户修改包有有限实测 | 原主页识别、实体键、开机与恢复均待目标固件核验 |
| 海信 Android VIDAA | [#40](https://github.com/pengchujin/aerial-tv/issues/40)：私有主页属性可改变 HOME 行为，开机仍受拦截 | Quiet TV 不自动改属性；系统输入面板调用与 HOME 接管分别验证 |
| TCL MT9655 / 部分新雷鸟 | [机型记录](https://github.com/pengchujin/aerial-tv/blob/d652d5ef65267f22bd273da01f28a99718d059a8/docs/devices/tcl-mt9655-cn.md)：TLS 动态端口但无可用配对入口，能装 APK 不等于能授权接管 | 先核实调试方式；无配对/授权通道时保留普通 App 使用，不循环试连 5555 |
| 小米 MiTV-ANTL0 / Android 5.1 | [#64](https://github.com/pengchujin/aerial-tv/issues/64)：输入标签与物理口错位，切换编号不同 | 仅按已知型号、输入服务及硬件 ID 修正，Quiet TV 实际切源待真机验收 |
| 红米 MiTV-MFFU1 / Android 14 | [#62](https://github.com/pengchujin/aerial-tv/issues/62)：DP 与 USB-C 名称和路由不同，固件连接状态不可靠 | 型号限定映射，未知状态不显示已连接；需实际接线核对 |
| 康佳 KKTV U86V9 | [PR #125](https://github.com/pengchujin/aerial-tv/pull/125)：原生 SDK 枚举及直切候选，不能按品牌生成固定端口 | 本轮未加入该未合入候选；保留系统设置/遥控器路径，获得样机后验证 |
| 遥控器与播放保护 | [#115](https://github.com/pengchujin/aerial-tv/issues/115)、[#108](https://github.com/pengchujin/aerial-tv/issues/108)、[#22](https://github.com/pengchujin/aerial-tv/issues/22)：按键过滤连跳、切源误拉回、息屏自行亮起 | 本轮主程序不新增守护或按键过滤；后续助手实验必须覆盖这些回归 |

反馈请分别给出安装包版本/构建号、固件、安装、设置、信号源、实体 HOME、整机重启、冷启动、待机唤醒与恢复结果。未执行记为未执行，不能用模拟器或上游结果代替本应用真机验收。

## 本轮源码兼容改进

本轮只改变普通应用入口、设置调用、图片生成与信号源调用，不增加 HOME 守护、按键过滤、系统包操作权限或 ADB 常开功能。现有公开下载包不包含这些改动。

| 功能 | 公共实现及档位差异 | 验证边界 |
| --- | --- | --- |
| 应用入口 | 安卓4.4与标准档共用应用目录；电视入口优先，补 APPSTORE_LAUNCHER 及小米播放器/米家明确组件。厂商补项仅接受可调用系统组件，首页、排序和常用应用选择共用结果 | 原生组件与权限检查可在模拟环境验证；实际厂商入口待验收 |
| 设置与网络 | 小米优先检测本机设置/Wi-Fi 组件；标准设置不可用时尝试 TCL 系统设置。检查启用、导出、权限，拒绝直接解析到 HOME 的入口，失败显示提示 | 复用实验分支的设置路由；不包含该分支的助手、构建号或设备操作 |
| Quiet TV 应用图标 | 两档均增加普通 LAUNCHER 入口，同时保留 HOME 和电视入口 | 原厂应用中心是否展示及遥控启动待目标固件验收 |
| 卡片图片 | 优先加载 APK 中的高密度资源，复用现有卡片样式与缓存；4K 渲染且内存预算足够时提高生成尺寸，低内存设备保留原尺寸 | 不重绘第三方品牌图案；不能提高 APK 本身缺失的源图细节 |
| 新上传壁纸 | 按应用实际显示长边处理，普通设备最大 3840；低内存或内存档位低于 256 MB 时最大 1920。保留 EXIF 方向、格式与像素上限 | 只影响新上传；不重写已有文件，原图仍在上传端。应用渲染 1080p 的 4K 面板不强行生成 4K 图 |
| TCL 快捷信号源 | 只有本机声明 com.tcl.software.systemui_custom_tv_source 时，在 SystemUI 包内解析 SHOW_WINDOW 服务并传递 Source/input 参数；再次核对实际系统组件、权限和状态 | 继续保留 TCL 设置面板及其他已有面板；只确认调用，不视为切源成功 |
| 小米 MiTV-ANTL0 | 仅已注册的系统 com.droidlogic.tvinput HDMI / HW6，显示 HDMI 1 并尝试源编号 23 | 不补造第二个端口；其他型号、服务或类型不继承编号 |
| 红米 MiTV-MFFU1 | 仅系统 com.mediatek.tvinput DisplayPort / HW9、HW10 分别对应 DP/29、USB-C/30 | 不把框架报告的连接状态当可靠接线证据，不显示未经验证的“已连接” |
| 未匹配直切 | 继续使用标准 TvContract 调用，失败提供已检测的系统面板和遥控器路径 | 不按品牌硬编码端口数量，不自动授权、不修改主页属性 |

应用入口与图标依据：[Aerial Apps.kt](https://github.com/pengchujin/aerial-tv/blob/d652d5ef65267f22bd273da01f28a99718d059a8/app/src/main/java/app/orchard/tv/apps/Apps.kt)。设置/信号源依据：[SystemState.kt](https://github.com/pengchujin/aerial-tv/blob/d652d5ef65267f22bd273da01f28a99718d059a8/app/src/main/java/app/orchard/tv/system/SystemState.kt)、[TCL 记录](https://github.com/pengchujin/aerial-tv/blob/d652d5ef65267f22bd273da01f28a99718d059a8/docs/devices/tcl-mt5879-cn.md)。本轮按这些接口事实实现现有 Java 结构，未引入上游 UI、守护、视频屏保或额外依赖。

当前代码验证记录见本地任务交付；上游机型结果不计入 Quiet TV 验收。厂商设置、真实信号源画面、实体遥控器、重启、冷启动、待机唤醒均需另行真机验证。Android 4.4 仍不提供标准 TV Input 列表，信号源使用系统设置和实体遥控器替代路径。
