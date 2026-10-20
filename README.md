<p align="center"><img src="docs/assets/cover.png" alt="Quiet TV 项目封面" width="100%"></p>

# Quiet TV

**一个简洁、可自定义的 Android 电视桌面。**

[简体中文](README.md) · [English](README.en.md)

Quiet TV 面向国内 Android 电视的日常使用场景：把常用应用放在首页，用遥控器直接打开，方便地切换信号源、调整壁纸和管理已安装的应用。桌面没有广告和推荐信息流，无需注册账号。采用 Android 原生界面，无需 root 或刷机。

[下载测试版](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b24) · [安装指南](docs/INSTALL.zh-CN.md) · [使用指南](docs/USAGE.zh-CN.md) · [支持机型](docs/COMPATIBILITY.md)

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

面向会使用 **Codex、TRAE 等 AI 编程工具**的用户：打开 Quiet TV 源码，用自然语言描述需求，让 AI 完成原生界面修改、构建与安装，再用真实遥控器验收。

**实现路径：获取源码 → AI 定制原生桌面 → 构建 APK → ADB 安装试用 → 设置默认 HOME → 真机调试。** AI 在电脑上工作，电视运行 APK；无需 root 或刷机，日常使用无需电脑在线。

完整的操作步骤、每步完成标志与可复制提示词见[AI 编程实施教程](docs/TECHNICAL_PATH_WITH_CODEX.zh-CN.md)。

### 准备条件

| 条件 | 要求 |
| --- | --- |
| 电视 | Android 系统，允许安装 APK 和切换第三方桌面；具体能力取决于固件。 |
| 局域网 | 电脑与电视连接同一路由器，可一台用网线、一台用 Wi-Fi；关闭阻止互访的网络隔离。 |
| 调试权限 | 开启开发者选项及可用的 ADB 调试，在电视上确认电脑授权。 |
| AI 编程工具 | Codex、TRAE 等，能够读取项目、修改文件和执行终端命令。 |
| 构建工具 | Git、Python 3、JDK 17 或 21、Android SDK Platform 36、Build Tools 36.0.0、Platform Tools。 |

当前 b24 源码与公开通用测试包均最低要求 Android 6.0；下载包包含 ARM32、ARM64、x86 和 x86_64，具体机型与功能仍待真机验证。设备适配状态见[兼容说明](docs/COMPATIBILITY.md)。

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

连接成功后，让 AI 读取系统版本、CPU 架构、应用列表和实际默认 HOME，并保存原桌面状态与恢复方法。多设备连接时明确指定目标电视。

### 核心技术

| 功能 | 实现 |
| --- | --- |
| 首页与应用列表 | Java 原生 View、RecyclerView；PackageManager 读取应用并启动。 |
| 遥控与焦点 | 处理方向键，维护行列位置；Canvas 和动画绘制焦点框、卡片缩放。 |
| 壁纸与配置 | 本地图片、渐变光晕、定时轮播；SharedPreferences 保存偏好。 |
| 默认桌面 | Activity 声明 HOME 入口，通过系统选择或固件支持的命令切换。 |
| 应用管理 | 按需授权本机 ADB，核对用户、包状态和保护规则后逐项操作。 |

电脑 ADB 用于开发和安装；Quiet TV 内置 ADB 用于电视端应用管理，两者分别授权。未知固件的系统包保持只读，详见[应用管理说明](docs/APP_MANAGEMENT.md)。

### 操作流程

先获取源码，用 AI 编程工具打开项目根目录：

```sh
git clone https://github.com/BeatsAD/quiet-tv.git
cd quiet-tv
```

已有克隆直接打开，保留现有修改。用户负责操作电视菜单、确认连接授权及反馈实际体验；AI 负责阅读代码、修改、检查环境并执行已授权的命令。

| 阶段 | 你来操作 | AI 完成 | 完成标志 |
| --- | --- | --- | --- |
| 1. 准备环境 | 打开项目，发送开始提示词。 | 阅读源码，检查 Git、JDK、SDK、Python 和 ADB。 | 环境符合项目要求，缺失项明确。 |
| 2. 识别设备 | 开启调试，确认电视端授权和目标设备。 | 只读查询系统、架构、用户和默认 HOME，保存原桌面恢复基线。 | 目标设备为 `device`，记录来自本机。 |
| 3. 定制桌面 | 提供需求或参考图；无改动可跳过。 | 复用现有功能，修改界面与遥控交互，完成对应检查。 | 代码差异符合需求，本地检查有结果。 |
| 4. 构建安装 | 授权安装，在电视上临时试用。 | 构建 APK，核对版本、架构、签名和哈希后安装。 | 实际安装版本正确，可用遥控器操作。 |
| 5. 设置 HOME | 授权切换，完成系统选择，实际按 HOME 键。 | 设置并读回默认 HOME，提供恢复步骤。 | 查询与真实遥控器均确认进入 Quiet TV。 |
| 6. 验收迭代 | 检查焦点、应用、配置，反馈截图和操作步骤。 | 结合日志修复，重新构建和覆盖更新，分别记录验收结果。 | 原问题复测通过，待验收项明确。 |

构建命令为 `./gradlew :app:assembleDebug --console=plain`，Windows 使用 `gradlew.bat`；输出位于 `app/build/outputs/apk/debug/app-debug.apk`。升级需保持签名一致，不通过卸载或清数据解决冲突。默认桌面设置不要求先禁用原桌面，重启与长期运行分别验收。

### 可复制的开始提示词

在项目会话中发送下面这段，再按[完整教程](docs/TECHNICAL_PATH_WITH_CODEX.zh-CN.md#四操作流程与分阶段提示词)逐步发送设备检查、界面定制、安装和 HOME 设置提示词。将方括号替换为自己的目标。

```text
我会使用 AI 编程工具，希望基于当前 Quiet TV 源码定制自己的 Android 电视桌面。
请阅读 AGENTS.md、README.md 和 docs/TECHNICAL_PATH_WITH_CODEX.zh-CN.md，
遵循其中“操作流程与分阶段提示词”推进，复用现有原生功能。
我的目标是 [描述界面或功能；也可填写“先构建并试用现有桌面”]。
先检查环境与代码，告诉我需要在电视上完成的准备和授权。
随后按设备只读检查、界面定制、构建安装、默认 HOME、真机验收逐步完成。
当前只授权本地准备与开发，安装和 HOME 切换在我发送对应阶段提示词后执行。
不要 root、刷机、清除数据或擅自禁用系统应用。
从实际设备读取型号、地址、应用和恢复信息，不套用其他人的设备资料。
常规已授权步骤连续完成；需要我在电视上授权或提供关键缺失信息时再询问。
无法执行命令或观察真实电视时如实说明，让我执行并回传结果。
代码、构建、安装和真机验收分别记录，设备资料不进入公开提交。
```

## 安装与试用

1. **确认电视是否适用**：当前 b24 安装包最低要求 Android 6.0，涵盖 Android 7 及更高版本的安装门槛；具体机型与功能仍需验证，先查看下方支持范围和[兼容性说明](docs/COMPATIBILITY.md)。
2. **获取安装包**：从 [Releases](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b24) 下载 APK 与 SHA-256 校验文件，或按[安装指南](docs/INSTALL.zh-CN.md)从源码构建。
3. **安装并试用**：按安装指南连接 ADB 并安装。新安装首次打开会进入设置，也可选择“暂不设置，临时使用”，先检查遥控器、应用启动和系统入口。
4. **完成首次设置**：按两步引导设置默认桌面，再选择是否处理预装应用；可选择“不处理，进入桌面”。升级不会强制重新引导，需要时从“应用管理 → 更多 → 首次设置”打开，同一菜单也提供“恢复原桌面”。详见[首次设置说明](docs/SETUP.md)。

当前下载为 **v1.1.0 / b24 通用兼容优化测试包，约 12.30 MiB**，文件名为 `quiet-tv_1.1.0_b24_benchmark_api23.apk`，最低 Android 6.0 / API 23，包含 `armeabi-v7a`、`arm64-v8a`、`x86` 和 `x86_64`。本包与 b23 使用相同测试签名，支持满足升级条件的覆盖安装。目前仍为预发布测试版本，尚无稳定版。

本版主要变化：

- 降低最低系统要求至 Android 6.0，提供通用架构包并启用代码和资源裁剪；通用包比原 ARM32 单架构包更大。
- 增加旧系统网络监听与字体回退、遥控器返回导航及 Android 6 用户状态读取等兼容处理。
- 按显示尺寸加载壁纸、按需加载缩略图并复用封面缓存；普通返回首页不再重复扫描全部应用。
- 改进原生两步设置、默认桌面状态核验及关于页首屏，离线许可按需打开。

b24 优化构建通过，完整 Lint 为 0 错误、27 条警告；实际 APK 的最低系统、架构、签名和 GitHub 回下载哈希均已核对。**本次 b24 未安装到电视或模拟器，Android 6、7 及其他机型的功能仍待真机验收。**校验、更新与完整验证范围见[b24 发布说明](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b24)。

历史 [b23 ARM32 测试包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0)（Android 12+，约 4.54 MiB）与 v1.0.1 保留。b23 曾在一台 Android 12 / ARM32 电视覆盖安装、核对哈希，并检查 HOME 返回、壁纸和 Dock；该结果不代表 b24 已通过真机验收。源码默认 Debug 构建与公开优化包区分，详见[构建规则](docs/VERSIONING.md)。

安装 Quiet TV 后可以保留原桌面，试用不要求先禁用系统应用。系统精简需为目标电视备份并记录恢复方式；实际设备配置、包清单和恢复记录仅保留本地，这些资料不随公开源码分发，不能将其他电视的清单作为自己的禁用建议或恢复基线。

## 支持范围

| 设备／系统 | 状态 |
| --- | --- |
| Sony KD-65X9000H / BRAVIA VH1 · Android 12 | 历史版本曾验证首页、应用管理、壁纸、部分系统入口及一次整机重启；本次 b24 尚未安装验证。完整范围见[验收记录](docs/COMPATIBILITY.md)。 |
| 其他 Android 6.0 及以上电视 | b24 通用包可供按机型试用，仍需逐机确认固件、HOME 与 ADB 能力；见[兼容记录](docs/COMPATIBILITY.md)。 |
| Android 5.1 及以下，或 Tizen、webOS 等非 Android 系统 | 当前源码不支持。 |

长期运行、投屏和完整影音播放仍待验收；六位码无线配对仍为待验收实验入口，当前要求 API 33+ 及设备实际提供配对能力，不能在 Android 6/7 使用。满足最低安装要求不代表固件全部功能已适配，详见[兼容性说明](docs/COMPATIBILITY.md)。第三方影音应用通过各自渠道更新，Quiet TV 当前不提供第三方应用自动更新服务。

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
