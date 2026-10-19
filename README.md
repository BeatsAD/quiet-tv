<p align="center"><img src="docs/assets/cover.png" alt="Quiet TV 项目封面" width="100%"></p>

# Quiet TV

**一个简洁、可自定义的 Android 电视桌面。**

[简体中文](README.md) · [English](README.en.md)

Quiet TV 面向国内 Android 电视的日常使用场景：把常用应用放在首页，用遥控器直接打开，方便地切换信号源、调整壁纸和管理已安装的应用。

桌面没有广告和推荐信息流，无需注册账号。采用 Android 原生界面，无需 root 或刷机。

[下载测试版](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0) · [安装指南](docs/INSTALL.zh-CN.md) · [使用指南](docs/USAGE.zh-CN.md) · [支持机型](docs/COMPATIBILITY.md)

## 界面

**常用应用，打开电视就能找到。**

首页保留时钟、日期、常用应用和系统快捷入口。白色焦点框标出当前选中的项目，按下方向键可继续浏览全部应用。

![Quiet TV 首页：时钟、系统入口和四个常用应用](docs/assets/home.png)

**选一张壁纸，也可以保持简单。**

可使用随应用焦点换色的默认主题，固定一张风景图，或开启每 5 分钟切换的壁纸轮播。

![Quiet TV 壁纸设置：默认主题、风景图和轮播开关](docs/assets/wallpaper-settings.png)

以上为 2026-10-19 在 Sony KD-65X9000H 上截取的原始画面，截图对应 1.1.0 / b15 开发版本；当前下载为 b23 优化测试版。顶部封面为 AI 插画。[查看素材来源](docs/assets/README.md)。

## 功能

| 功能 | 可以做什么 |
| --- | --- |
| 常用应用 | 首页最多放 4 个应用，支持添加、替换、移除和排序；更新桌面时保留配置。 |
| 应用管理 | 查看应用名称、包名、版本和状态，按名称搜索、按类型筛选，逐个启用、禁用或卸载。关键系统组件保持只读。 |
| 信号源与设置 | 从电视实际提供的输入中选择 HDMI、AV 或电视直播，直接进入网络和系统设置。 |
| 壁纸 | 默认主题光晕、固定风景图或自动轮播，支持预览和取消。 |
| 遥控器操作 | 方向键移动、确定打开、返回上一级；回到首页时恢复上次焦点。 |
| 本地使用 | 设置保存在电视上，无需云端账号；应用管理按需连接电视本机 ADB。 |

首页支持中文和英文，应用管理界面当前使用中文。应用管理入口位于“全部应用”，常用应用和壁纸可直接在电视上调整。

系统包操作需要先完成本机 ADB 授权。禁用会保留数据，卸载会删除当前用户的应用数据；符合条件的预装应用可恢复安装，但无法恢复已删除的数据。预装应用卸载／恢复仍待实机验收，详见[应用管理说明](docs/APP_MANAGEMENT.md)。

## 安装与试用

1. **确认电视是否适用**：当前最低为 Android 12，先查看下方支持范围和[兼容性说明](docs/COMPATIBILITY.md)。
2. **获取安装包**：从 [Releases](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0) 下载 APK 与 SHA-256 校验文件，或按[安装指南](docs/INSTALL.zh-CN.md)从源码构建。
3. **安装并试用**：按安装指南连接 ADB，安装后先打开 Quiet TV，检查遥控器、应用启动和系统入口。
4. **设置为默认桌面**：确认可用后，在电视设置中选择 Quiet TV，再按[使用指南](docs/USAGE.zh-CN.md)整理常用应用和壁纸。

当前下载为 **v1.1.0 / b23 ARM32 优化测试包**，最低 Android 12，仅适用于支持 `armeabi-v7a` 应用的电视，仍为测试签名预发布版本。更新、校验和实机验证范围见[发布说明](docs/releases/v1.1.0.md)；历史 v1.0.1 保留。源码默认 Debug 构建为通用架构，与发布的 ARM32 优化包区分；详见[构建规则](docs/VERSIONING.md)。

安装 Quiet TV 后可以保留原桌面，试用不要求先禁用系统应用。系统精简是单独的操作，需为目标电视备份并记录恢复方式；本仓库的索尼包清单仅用于该设备。

## 支持范围

| 设备／系统 | 状态 |
| --- | --- |
| Sony KD-65X9000H / BRAVIA VH1 · Android 12 | 已安装并验证首页、应用管理、壁纸、部分系统入口及一次整机重启；完整范围见[验收记录](VERIFICATION.md)。 |
| 其他 Android 12 及以上电视 | 待逐机适配，需确认厂商固件、默认桌面切换和 ADB 权限。 |
| Android 11 及以下，或 Tizen、webOS 等非 Android 系统 | 当前版本不支持。 |

长期运行、投屏和完整影音播放仍待验收。第三方影音应用通过各自渠道更新，Quiet TV 当前不提供第三方应用自动更新服务。

## 文档与参与

| 我想…… | 查看 |
| --- | --- |
| 安装桌面、更新 APK 或切回原桌面 | [安装指南](docs/INSTALL.zh-CN.md) |
| 调整常用应用、壁纸和遥控器操作 | [使用指南](docs/USAGE.zh-CN.md) |
| 了解机型适配与已验证功能 | [兼容性](docs/COMPATIBILITY.md) · [验收记录](VERIFICATION.md) |
| 开发、构建或贡献代码 | [贡献指南](CONTRIBUTING.md) · [版本与安装包规则](docs/VERSIONING.md) |
| 查看索尼设备实施过程 | [设备记录](docs/DEVICE_NOTES.zh-CN.md) |
| 了解安全与仓库设置 | [安全政策](SECURITY.md) · [依赖安全](docs/DEPENDENCY_SECURITY.md) · [仓库说明](docs/REPOSITORY.md) |

反馈：[Bug](https://github.com/BeatsAD/quiet-tv/issues/new?template=bug_report.yml) · [功能建议](https://github.com/BeatsAD/quiet-tv/issues/new?template=feature_request.yml) · [新机型适配](https://github.com/BeatsAD/quiet-tv/issues/new?template=device_adaptation.yml)

仓库、文档和测试包下载已公开；提交反馈需要登录 GitHub。

## 参与开发与维护

欢迎贡献 Android 原生功能、遥控器交互、文档翻译、新机型验证和测试。可以从[新人任务](https://github.com/BeatsAD/quiet-tv/issues?q=is%3Aissue%20is%3Aopen%20label%3A%22good%20first%20issue%22)或[需要帮助的任务](https://github.com/BeatsAD/quiet-tv/issues?q=is%3Aissue%20is%3Aopen%20label%3A%22help%20wanted%22)开始，先留言认领，再通过 Fork 提交 PR。

[贡献指南](CONTRIBUTING.md)提供开发环境、检查命令与评审流程；[Discussions](https://github.com/BeatsAD/quiet-tv/discussions)用于问答、方向讨论和长期维护意愿交流。参与协作请遵守[行为准则](CODE_OF_CONDUCT.md)。

## 致谢与许可

Quiet TV 基于 [Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack) 开发，感谢 **Gathin** 的原始工作。本项目独立维护，与上游及电视、应用品牌方无官方关联。

代码采用 [MIT 许可证](LICENSE)，保留原作者版权与完整许可。字体、壁纸和第三方应用素材分别遵循各自授权，详见[第三方项目与资源说明](THIRD_PARTY_NOTICES.md)。

## 保持联系

![WenBin AI LAB 联系方式：X、GitHub、网站、邮箱及微信、公众号、小红书、抖音二维码](docs/assets/contact-16x9.png)
