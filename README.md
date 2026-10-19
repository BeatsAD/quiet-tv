<p align="center"><img src="docs/assets/cover.png" alt="Quiet TV 项目封面" width="100%"></p>

# Quiet TV

**面向国内电视机的适配与应用升级改造。**

[简体中文](README.md) · [English](README.en.md) · [安装](docs/INSTALL.zh-CN.md) · [使用](docs/USAGE.zh-CN.md) · [兼容性](docs/COMPATIBILITY.md) · [贡献](CONTRIBUTING.md)

轻量原生电视桌面，配合应用配置、桌面更新安装和可恢复的预装应用精简，让国内 Android 电视的日常使用更简洁。

代码采用 [MIT 许可证](LICENSE)。当前仓库已公开；当前适配机型为 **Sony KD-65X9000H / Android 12**，其他机型待逐机验证。

## 可以做什么

- **简洁首页**：中文／英文、最多四个应用的可编辑 Dock、应用网格和明确的遥控器焦点。
- **系统入口**：从实际 TV Input 列表选择信号源，进入网络与系统设置。
- **壁纸设置**：默认主题光晕、固定风景图或每 5 分钟自动轮播。
- **更新与恢复**：构建并更新 Quiet TV；按实际设备备份和记录系统包变更，保留恢复路径。
- **本地运行**：没有广告、账号系统和推荐流；INTERNET 权限仅用于按需连接本机 ADB，不接入云端服务。

“应用升级改造”包括桌面更新安装和应用配置调整；当前不提供第三方影音应用的自动更新服务。

## 界面预览

以下为 Sony KD-65X9000H 在 2026-10-19 截取的原始 ADB 截图（电视当前安装的 1.1.0 / b15 开发版本，并非 Releases 中的 1.0.1 测试包），未重绘界面。封面是 AI 生成的品牌插画，截图才是实际设备画面。

**风景壁纸与应用 Dock**

![真实电视截图：海岸壁纸、快捷入口和四应用 Dock](docs/assets/home.png)

**壁纸设置**

![真实电视截图：默认壁纸、三张风景图和轮播开关](docs/assets/wallpaper-settings.png)

[截图来源与封面制作说明](docs/assets/README.md)。第三方应用图标及壁纸的权利归各自权利人所有。

## 开始使用

1. 阅读[兼容性说明](docs/COMPATIBILITY.md)，确认 Android 版本与机型边界。
2. 按[安装指南](docs/INSTALL.zh-CN.md)准备 JDK、Android SDK，先构建，再安装和试用桌面。
3. 按[使用指南](docs/USAGE.zh-CN.md)配置 Dock、壁纸与遥控器操作。
4. 涉及系统包禁用前，单独备份目标设备并核对恢复记录。仓库里的索尼设备清单不可直接套用到其他电视。

可从 [Releases v1.0.1](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.0.1) 下载 Debug 测试安装包，中文更新内容见[发布说明](docs/releases/v1.0.1.md)。当前尚无稳定版 APK；安装与适配记录不能替代其他设备的实机验收。

安装包按 `quiet-tv_<版本号>_b<构建号>_<类型>.apk` 命名，统一归档至本地 `dist/apk/`。当前配置为 `1.0.1 / b3 / debug`；完整约定见[版本与安装包规则](docs/VERSIONING.md)。

## 兼容性摘要

| 项目 | 当前状态 |
| --- | --- |
| 已适配机型 | Sony KD-65X9000H / BRAVIA VH1 |
| 已验证系统 | Android 12 / API 31 |
| APK 最低版本 | Android 12 / API 31；更高版本待验证 |
| UI／面板分辨率 | 1920×1080 UI / 3840×2160 面板 |
| 其他 Android 电视 | 待按品牌、固件、权限和 HOME 限制适配 |
| 非 Android 电视系统 | 不支持当前 APK 和 ADB 改造方式 |

完整功能与待验收项见[兼容性说明](docs/COMPATIBILITY.md)和[原始验收记录](VERIFICATION.md)。

## 文档与反馈

- [安装指南](docs/INSTALL.zh-CN.md) / [Installation](docs/INSTALL.en.md)
- [使用指南](docs/USAGE.zh-CN.md) / [Usage](docs/USAGE.en.md)
- [贡献指南 / Contributing](CONTRIBUTING.md)
- [安全政策 / Security policy](SECURITY.md)
- [Bug 反馈](https://github.com/BeatsAD/quiet-tv/issues/new?template=bug_report.yml) · [功能建议](https://github.com/BeatsAD/quiet-tv/issues/new?template=feature_request.yml) · [新机型适配](https://github.com/BeatsAD/quiet-tv/issues/new?template=device_adaptation.yml)
- [索尼设备实施记录](docs/DEVICE_NOTES.zh-CN.md) · [通用复现提示词](项目复现提示词.md)
- [仓库设置与当前限制](docs/REPOSITORY.md)

仓库、文档和测试包下载已公开；提交反馈需要登录 GitHub。

## 参与开发与维护

欢迎贡献 Android 原生功能、遥控器交互、文档翻译、新机型验证和测试。可以从[新人任务](https://github.com/BeatsAD/quiet-tv/issues?q=is%3Aissue%20is%3Aopen%20label%3A%22good%20first%20issue%22)或[需要帮助的任务](https://github.com/BeatsAD/quiet-tv/issues?q=is%3Aissue%20is%3Aopen%20label%3A%22help%20wanted%22)开始，先留言认领，再通过 Fork 提交 PR。

[贡献指南](CONTRIBUTING.md)提供开发环境、检查命令与评审流程；[Discussions](https://github.com/BeatsAD/quiet-tv/discussions)用于问答、方向讨论和长期维护意愿交流。参与协作请遵守[行为准则](CODE_OF_CONDUCT.md)。

## 项目来源与许可证

Quiet TV 基于 [Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack) 开发，感谢原作者 **Gathin**。本项目独立维护，面向国内使用场景继续适配，与上游及电视、应用品牌方无官方关联。

代码使用 [MIT](LICENSE)，保留 `Copyright (c) 2026 Gathin` 和完整上游许可。Outfit 字体按 SIL OFL 1.1 授权；内置壁纸不适用 MIT 代码许可。详见[第三方项目与资源说明](THIRD_PARTY_NOTICES.md)。

应用管理与 Dock 编辑见[使用指南](docs/USAGE.zh-CN.md)和[管理验收记录](docs/APP_MANAGEMENT.md)。

## 保持联系

![WenBin AI LAB 联系方式：X、GitHub、网站、邮箱及微信、公众号、小红书、抖音二维码](docs/assets/contact-16x9.png)
