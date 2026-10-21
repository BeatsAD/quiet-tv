<p align="center"><img src="docs/assets/cover.png" alt="Quiet TV 项目封面" width="100%"></p>

# Quiet TV

**一个简洁、可自定义的 Android 电视桌面。**

[简体中文](README.md) · [English](README.en.md)

Quiet TV 面向国内 Android 电视的日常使用场景：把常用应用放在首页，用遥控器直接打开，方便地切换信号源、调整壁纸和管理已安装的应用。桌面没有广告和推荐信息流，无需注册账号。采用 Android 原生界面，无需 root 或刷机。

[下载测试版](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b26) · [安装指南](docs/INSTALL.zh-CN.md) · [使用指南](docs/USAGE.zh-CN.md) · [支持机型](docs/COMPATIBILITY.md) · [已知问题与样机验证](#已知兼容性问题与样机验证)

## 界面

**常用应用，打开电视就能找到。**首页保留时钟、日期、常用应用和系统快捷入口。白色焦点框标出当前选中的项目，按下方向键可继续浏览全部应用。

![Quiet TV 首页：时钟、系统入口和四个常用应用](docs/assets/home.png)

**新增了壁纸功能**：可使用随应用焦点换色的默认主题，固定一张风景图，或开启每 5 分钟切换的壁纸轮播，目前没有做自定义壁纸更换，自由度还没那么高。

![Quiet TV 壁纸设置：默认主题、风景图和轮播开关](docs/assets/wallpaper-settings.png)

以上为 Sony 电视机真实截图，保留当时的界面状态；

## 功能

| 功能 | 可以做什么 |
| --- | --- |
| 常用应用 | 首页最多放 4 个应用，支持添加、替换、移除和排序；更新桌面时保留配置。 |
| 应用管理 | 查看应用名称、包名、版本和状态，按名称搜索、按类型筛选，逐个启用、禁用或卸载。关键系统组件保持只读。 |
| 首次设置 | 两步完成默认桌面设置与可选的预装应用管理；先选择操作，再按需授权。未知固件的系统包保持只读。 |
| 恢复原桌面 | 根据本机记录和已验证的设备能力，切回或启用原桌面；未开放的操作会显示原因。 |
| 信号源与设置 | 从电视实际提供的输入中选择 HDMI、AV 或电视直播，直接进入网络和系统设置。 |
| 壁纸 | 默认主题光晕、固定风景图或自动轮播，支持预览和取消。 |
| 遥控器操作 | 方向键移动、确定打开、返回上一级；回到首页时恢复上次焦点。 |
| 本地使用 | 设置保存在电视上，无需云端账号；应用管理按需连接电视本机 ADB。 |
| 关于与许可 | 首页可查看应用版本、项目与联系二维码；开源许可随包提供，可离线阅读。 |

应用管理入口位于“全部应用”，常用应用和壁纸可直接在电视上调整。系统包操作需要先完成本机 ADB 授权。禁用会保留数据，卸载会删除当前用户的应用数据；符合条件的预装应用可恢复安装，但无法恢复已删除的数据。预装应用卸载／恢复仍待实机验收，详见[应用管理说明](docs/APP_MANAGEMENT.md)。

## 技术实现

**实现路径：Codex 编写原生桌面 → 构建 APK → 局域网 ADB 安装 → 设置默认 HOME → 真机调试。** Codex 在电脑上开发，电视运行 APK；无需 root 或刷机，日常使用无需电脑在线。

### 准备条件

| 条件 | 要求 |
| --- | --- |
| 电视 | Android 系统，允许安装 APK 和切换第三方桌面；具体能力取决于固件。 |
| 局域网 | 电脑与电视连接同一路由器，可一台用网线、一台用 Wi-Fi；关闭阻止互访的网络隔离。 |
| 调试权限 | 开启开发者选项及可用的 ADB 调试，在电视上确认电脑授权。 |
| Codex | 已登录、可联网，允许读写项目文件和执行终端命令。 |
| 构建工具 | Git、Python 3、JDK 17 或 21、Android SDK Platform 36、Build Tools 36.0.0、Platform Tools。 |

当前源码为 1.1.0 / b28，提供 `standard`（Android 5.0+ / API21）和 `legacy19`（Android 4.4 / API19）两个构建档位。安卓 4.4 版保留核心桌面与传统 TCP ADB，输入源回退到系统设置，不包含六位码配对及现代原生密码库。公开下载仍为 b26 / Android 5.0+，b28 尚未发布。构建方式见[安装指南](docs/INSTALL.zh-CN.md)，实际验证范围见[兼容说明](docs/COMPATIBILITY.md)。

### 连接电视，读取真实设备能力

**先找到开发者选项。** 常见入口：

- Google TV：设置 → 系统 → 关于 → Android TV 操作系统版本号（Android TV OS build）。
- Android TV：设置 → 设备偏好设置 → 关于 → 版本号（Build）。

选中 **Build／编译版本号**，连续按遥控器确定约 **7 次**，看到开发者模式提示后返回，在“系统”或“设备偏好设置”中进入“开发者选项”。打开总开关（如有）及 USB／ADB 调试。菜单因品牌、固件而异，找不到时按完整型号查询官方说明。参考 [Android 官方开启方法](https://developer.android.com/studio/debug/dev-options)。

**再确认网络调试方式：**

| 可用入口 | 操作 |
| --- | --- |
| 网络 ADB／TCP 调试 | 开启后记录实际 IP、端口，在同一局域网连接并授权。 |
| 无线调试／配对码 | 按页面要求在同一 Wi-Fi 网络配对；配对端口与连接端口可能不同。 |
| 只有 USB 调试 | 不保证网络 ADB 可用；按固件说明确认是否支持 USB 数据连接或其他调试方式。 |

IP 通常在“设置 → 网络 → 当前连接详情”查看。连接后运行 `adb devices -l`：`device` 表示已连接，`unauthorized` 表示需在电视授权；`offline` 或无设备时检查网络、地址、端口和调试开关。USB ADB 不要求同一局域网。

连接成功后，让 Codex 读取系统版本、CPU 架构、应用列表和实际默认 HOME，并保存原桌面状态与恢复方法。多设备连接时明确指定目标电视。

### 核心技术

| 功能 | 实现 |
| --- | --- |
| 首页与应用列表 | Java 原生 View、RecyclerView；PackageManager 读取应用并启动。 |
| 遥控与焦点 | 处理方向键，维护行列位置；Canvas 和动画绘制焦点框、卡片缩放。 |
| 壁纸与配置 | 本地图片、渐变光晕、定时轮播；SharedPreferences 保存偏好。 |
| 默认桌面 | Activity 声明 HOME 入口，通过系统选择或固件支持的命令切换。 |
| 应用管理 | 按需授权本机 ADB，核对用户、包状态和保护规则后逐项操作。 |

电脑 ADB 用于开发和安装；Quiet TV 内置 ADB 用于电视端应用管理，两者分别授权。未知固件的系统包保持只读，详见[应用管理说明](docs/APP_MANAGEMENT.md)。

### 用 Codex 实施

1. **检查设备**：确认环境、调试连接与电视能力，记录原桌面恢复基线。
2. **开发界面**：提供文字或参考图，先完成首页、四个常用应用、全部应用和遥控导航，再增加壁纸等功能。
3. **构建安装**：执行下方命令生成 APK；核对版本、架构和签名后，按授权安装。连接与安装命令见[安装指南](docs/INSTALL.zh-CN.md)。
4. **真机调整**：结合截图、日志和真实遥控器反馈修改，重复构建与安装。
5. **设置并验收**：按授权切换默认 HOME，查询实际组件并按遥控器 HOME 键确认；重启、播放和长期运行分别验证。

```sh
./gradlew :app:assembleDebug --console=plain
```

Windows 使用 `gradlew.bat`。APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。升级保持签名一致；先保留原桌面，不处理无关系统应用。构建、安装和真机验收分别记录。

### 可复制的 Codex 提示词

```text
请基于当前 Quiet TV 源码，复现并适配参考截图中的电视桌面。
先阅读项目文档和现有实现，已有功能优先复用。

一、准备与设备检查
电脑与电视处于同一可互访局域网。
指导我找到开发者选项，开启适用的 ADB 调试并完成授权。
先只读查询系统版本、CPU 架构、屏幕尺寸、当前用户和默认 HOME，
记录原桌面状态及恢复方式，不套用其他电视的禁用清单。

二、界面与遥控
以提供的真实截图为视觉基准，保持布局、字体、间距和颜色一致。
首页包含时间日期、信号源、网络、设置、关于及最多四个常用应用；
向下浏览四列全部应用。
选中卡片有白色圆角焦点框、轻微放大和移动动画。
明确处理方向键边界、滚动、确定打开、返回和焦点恢复。

三、功能
支持常用应用添加、替换、移除、排序并本地保存。
支持默认主题色光晕、固定壁纸、预览取消及每五分钟轮播。
从本机读取应用和电视信号源，调用实际可用的系统入口。
保留首次设置、默认 HOME 状态核验及恢复原桌面流程。
应用管理按需连接本机 ADB，保护关键组件和未知固件系统包，
每次修改前检查状态，修改后读回结果，异常时不自动重复执行。

四、实施与验收
先说明现有实现与目标的差异，再按模块完成最小必要修改。
构建后核对版本、架构和签名，按我的授权安装。
结合真实截图、日志和遥控器反馈修正，验证 HOME 返回与配置保持。
保留原桌面，不操作无关系统应用。
分别报告代码、构建、安装、真机验收及尚未完成的项目。
```

## 安装与试用

1. **确认电视是否适用**：当前 b26 安装包最低要求 Android 5.0，涵盖 Android 5.1 及更高版本的安装门槛；具体机型与功能仍需验证，先查看下方支持范围和[兼容性说明](docs/COMPATIBILITY.md)。
2. **获取安装包**：从 [Releases](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b26) 下载 APK 与 SHA-256 校验文件，或按[安装指南](docs/INSTALL.zh-CN.md)从源码构建。
3. **安装并试用**：按安装指南连接 ADB 并安装。新安装首次打开会进入设置，也可选择“暂不设置，临时使用”，先检查遥控器、应用启动和系统入口。
4. **完成首次设置**：按两步引导设置默认桌面，再选择是否处理预装应用；可选择“不处理，进入桌面”。升级不会强制重新引导，需要时从“应用管理 → 更多 → 首次设置”打开，同一菜单也提供“恢复原桌面”。详见[首次设置说明](docs/SETUP.md)。

当前下载为 **v1.1.0 / b26 通用兼容优化测试包，约 6.50 MiB**，文件名为 `quiet-tv_1.1.0_b26_benchmark_api21.apk`，最低 Android 5.0 / API21，包含 `armeabi-v7a`、`arm64-v8a`、`x86` 和 `x86_64`。与 b24 使用相同测试签名，支持满足签名与版本条件的覆盖安装；仍为预发布测试版本，尚无稳定版。

本次更新包括：

- 最低要求降至 Android 5.0；适配旧版网络状态读取、权限及 TV Input 服务受限场景。
- 离线许可证页增加 Android 5 WebView 回退，修正旧版矢量路径及主题属性。
- 关于页在确认键抬起时激活，避免返回首页后再次打开。
- ADB 命令完成标记改用旧系统支持的 shell 内建 `echo`，保留身份校验、超时和能力限制。

b26 构建与 19 项本地单元测试通过，Lint 为 0 错误、28 条警告；API21 / 22 / 23 / 31 页面回归、API21 / 22 优化包冒烟及 API31 shell ADB 操作回归通过。实际 APK 最低版本、架构、签名及模拟器安装哈希均已核对。**尚未安装真实电视；Android 5 的默认 HOME、实体遥控器、输入源、网络切换及重启／唤醒仍待真机验收。**校验文件、更新说明与完整边界见 [b26 发布说明](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b26)。

历史 [b24 Android 6.0+ 通用包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b24)、[b23 ARM32 包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0)（Android 12+）与 v1.0.1 保留。b23 曾在一台 Android 12 / ARM32 电视验证部分功能，该结果不代表 b26 已通过真机验收。源码默认 Debug 构建与公开优化包区分，详见[构建规则](docs/VERSIONING.md)。

安装 Quiet TV 后可以保留原桌面，试用不要求先禁用系统应用。系统精简需为目标电视备份并记录恢复方式；实际设备配置、包清单和恢复记录仅保留本地，这些资料不随公开源码分发，不能将其他电视的清单作为自己的禁用建议或恢复基线。

## 支持范围

| 设备／系统 | 状态 |
| --- | --- |
| Sony KD-65X9000H / BRAVIA VH1 · Android 12 | 历史版本曾验证首页、应用管理、壁纸、部分系统入口及一次整机重启；本次 b26 尚未安装到该电视验证。完整范围见[验收记录](docs/COMPATIBILITY.md)。 |
| 其他 Android 6.0 及以上电视 | b26 通用包可供按机型试用，仍需逐机确认固件、HOME 与 ADB 能力；见[兼容记录](docs/COMPATIBILITY.md)。 |
| Android 5.0 / 5.1 | b26 通用测试包可供试用；已通过通用 Android 镜像的电视尺寸配置测试，不能代替原生 TV 固件验收。厂商固件与真实 HOME、ADB、输入源行为待验收。 |
| Android 4.4 | 从源码构建 `legacy19` 兼容测试包；真实电视、HOME、输入源与 ADB 待验收。 |
| Android 4.0～4.3，或 Tizen、webOS 等非 Android 系统 | 不支持。 |

长期运行、投屏和完整影音播放仍待验收；六位码无线配对仍为待验收实验入口，当前要求 API 33+ 及设备实际提供配对能力，不能在 Android 5/6/7 使用。满足最低安装要求不代表固件全部功能已适配，详见[兼容性说明](docs/COMPATIBILITY.md)。第三方影音应用通过各自渠道更新，Quiet TV 当前不提供第三方应用自动更新服务。

## 已知兼容性问题与样机验证

**以下问题仍待解决或验证，需要其他品牌、型号和固件的真实样机协助；不能视为已修复。** 安装成功、手动启动正常或 HOME 查询结果正确，不代表开机、遥控器 HOME 键和重启后的行为均正常。当前反馈见 [Issue #35](https://github.com/BeatsAD/quiet-tv/issues/35)。

| 问题 | 当前表现与待验证内容 |
| --- | --- |
| 厂商主页接管 | 部分固件可能在开机时恢复原厂主页，或由 Home 键直接启动原厂 Activity，绕过标准 HOME 设置；冷启动、重启和待机唤醒需分别验证。 |
| 默认桌面设置失败 | 已有反馈出现 `cannot be home on user 0`；需核对实际安装包、当前用户、HOME 候选和厂商限制，原因及适配尚待确认。 |
| 默认桌面状态提示不充分 | 当前检测主要依据 HOME Intent 解析，可能显示“已是默认桌面”，但实际 Home 键或重启仍返回原厂主页；状态提示和持久行为核验仍需改进。 |
| ADB 被禁用或入口受限 | USB 调试灰色、无线调试缺失或无法取得配对码时，本机应用管理和依赖 ADB 的适配可能不可用；内置 ADB 客户端不能绕过固件封锁或配对认证，目前没有通用解锁方案。 |

可选开机启动、无障碍服务检测原厂主页后跳转、日志监听后拉起桌面均为候选兼容方案，**当前 Quiet TV 尚未提供这些兼容实现，也未在反馈固件上验证**。上游其他设备的成功记录不能代替 Quiet TV 验收；若要走无 ADB 路线，还需确认 APK 安装和系统无障碍入口可用。保留原桌面及本机恢复记录，不套用其他电视的系统包禁用清单。

欢迎通过 [Issue #35](https://github.com/BeatsAD/quiet-tv/issues/35) 或[新机型适配反馈](https://github.com/BeatsAD/quiet-tv/issues/new?template=device_adaptation.yml)补充样机结果：电视型号、Android／固件版本、Quiet TV 版本与安装包来源、复现步骤，以及手动启动、真实遥控器 HOME、冷启动／重启、待机唤醒、信号源和原桌面恢复的实际结果。未测试项请标为“未验证”；日志只提供相关脱敏片段，不公开 IP、序列号、密钥或完整设备转储。

## 文档与参与

| 我想…… | 查看 |
| --- | --- |
| 浏览全部文档与目录职责 | [文档索引](docs/README.md) |
| 安装桌面、更新 APK 或切回原桌面 | [安装指南](docs/INSTALL.zh-CN.md) |
| 调整常用应用、壁纸和遥控器操作 | [使用指南](docs/USAGE.zh-CN.md) |
| 完成首次设置或恢复原桌面 | [首次设置与恢复](docs/SETUP.md) |
| 了解机型适配与已验证功能 | [兼容性](docs/COMPATIBILITY.md) · [验收记录](docs/COMPATIBILITY.md) |
| 开发、构建或贡献代码 | [贡献指南](CONTRIBUTING.md) · [版本与安装包规则](docs/VERSIONING.md) |
| 了解安全与仓库设置 | [安全政策](SECURITY.md) · [贡献指南](CONTRIBUTING.md) |

反馈：[Bug](https://github.com/BeatsAD/quiet-tv/issues/new?template=bug_report.yml) · [功能建议](https://github.com/BeatsAD/quiet-tv/issues/new?template=feature_request.yml) · [新机型适配](https://github.com/BeatsAD/quiet-tv/issues/new?template=device_adaptation.yml)

仓库、文档和测试包下载已公开；提交反馈需要登录 GitHub。

## 参与开发与维护

欢迎贡献 Android 原生功能、遥控器交互、文档翻译、新机型验证和测试。可以从[新人任务](https://github.com/BeatsAD/quiet-tv/issues?q=is%3Aissue%20is%3Aopen%20label%3A%22good%20first%20issue%22)或[需要帮助的任务](https://github.com/BeatsAD/quiet-tv/issues?q=is%3Aissue%20is%3Aopen%20label%3A%22help%20wanted%22)开始，先留言认领，再通过 Fork 提交 PR。

[贡献指南](CONTRIBUTING.md)提供开发环境、检查命令与评审流程；[Discussions](https://github.com/BeatsAD/quiet-tv/discussions)用于问答、方向讨论和长期维护意愿交流。参与协作请遵守[行为准则](CODE_OF_CONDUCT.md)。

本地构建需要 Git、Python 3、JDK 17、Android SDK platform 36 和 build-tools 36.0.0。先设置 `ANDROID_HOME`，或在 `local.properties` 配置自己的 `sdk.dir`，然后运行：

```sh
git clone https://github.com/BeatsAD/quiet-tv.git
cd quiet-tv
./gradlew :app:assembleDebug --console=plain
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。构建与离线检查不需要连接电视或准备 `device.json`；安装到自己的电视时，按[安装指南](docs/INSTALL.zh-CN.md)使用 ADB，保留已有配置和备份。正式交付 APK 的归档、签名与版本要求见[版本规则](docs/VERSIONING.md)。

原生源码位于 `app/`，公开构建工具位于 `scripts/`；`prototypes/about/` 是 APK 的实际打包输入。设备数据、独立网页演示和宣传视频不随当前源码分发，完整目录说明见[文档索引](docs/README.md)。

## 致谢与许可

Quiet TV 基于 [Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack) 开发，感谢 **Gathin** 的原始工作。本项目独立维护，与上游及电视、应用品牌方无官方关联。

代码采用 [MIT 许可证](LICENSE)，保留原作者版权与完整许可。字体、壁纸和第三方应用素材分别遵循各自授权，详见[第三方项目与资源说明](THIRD_PARTY_NOTICES.md)。

## 保持联系

![WenBin AI LAB 联系方式：X、GitHub、网站、邮箱及微信、公众号、小红书、抖音二维码](docs/assets/contact-16x9.png)
