<p align="center"><img src="docs/assets/cover.png" alt="Quiet TV 项目封面" width="100%"></p>

# Quiet TV

**一个简洁、可自定义的 Android 电视桌面。**

[简体中文](README.md) · [English](README.en.md)

Quiet TV 面向国内 Android 电视的日常使用场景：把常用应用放在首页，用遥控器直接打开，方便地切换信号源、调整壁纸和管理已安装的应用。

桌面没有广告和推荐信息流，无需注册账号。采用 Android 原生界面，无需 root 或刷机。

[下载测试版](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.0.1) · [安装指南](docs/INSTALL.zh-CN.md) · [使用指南](docs/USAGE.zh-CN.md) · [支持机型](docs/COMPATIBILITY.md)

## 界面

**常用应用，打开电视就能找到。**首页保留时钟、日期、常用应用和三个系统入口。白色焦点框标出当前选中的项目，按下方向键可继续浏览全部应用。

![Quiet TV 首页：时钟、系统入口和四个常用应用](docs/assets/home.png)

**选一张壁纸，也可以保持简单。**

可使用随应用焦点换色的默认主题，固定一张风景图，或开启每 5 分钟切换的壁纸轮播。

![Quiet TV 壁纸设置：默认主题、风景图和轮播开关](docs/assets/wallpaper-settings.png)

以上为 Sony KD-65X9000H 真实截图，保留当时的界面状态；
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
2. **获取安装包**：从 [Releases](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.0.1) 下载 APK 与 SHA-256 校验文件，或按[安装指南](docs/INSTALL.zh-CN.md)从源码构建。
3. **安装并试用**：按安装指南连接 ADB，安装后先打开 Quiet TV，检查遥控器、应用启动和系统入口。
4. **设置为默认桌面**：确认可用后，在电视设置中选择 Quiet TV，再按[使用指南](docs/USAGE.zh-CN.md)整理常用应用和壁纸。

目前提供的是 **v1.0.1 / b3 Debug 测试包**，尚无稳定版。已知依赖问题和验证范围见[发布说明](docs/releases/v1.0.1.md)；源码中的后续[依赖修复](docs/DEPENDENCY_SECURITY.md)尚未重新发布为安装包。

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

仓库目前为私密仓库，下载、文档和反馈入口需要相应访问权限。

## 致谢与许可

Quiet TV 基于 [Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack) 开发，感谢 **Gathin** 的原始工作。本项目独立维护，与上游及电视、应用品牌方无官方关联。

代码采用 [MIT 许可证](LICENSE)，保留原作者版权与完整许可。字体、壁纸和第三方应用素材分别遵循各自授权，详见[第三方项目与资源说明](THIRD_PARTY_NOTICES.md)。

## 保持联系

![WenBin AI LAB 联系方式：X、GitHub、网站、邮箱及微信、公众号、小红书、抖音二维码](docs/assets/contact-16x9.png)
