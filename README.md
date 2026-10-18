<p align="center"><img src="docs/assets/cover.png" alt="Quiet TV 项目封面" width="100%"></p>

# Quiet TV

**面向国内电视机的适配与应用升级改造。**

[简体中文](README.md) · [English](README.en.md) · [安装](docs/INSTALL.zh-CN.md) · [使用](docs/USAGE.zh-CN.md) · [兼容性](docs/COMPATIBILITY.md) · [贡献](CONTRIBUTING.md)

轻量原生电视桌面，配合应用配置、桌面更新安装和可恢复的预装应用精简，让国内 Android 电视的日常使用更简洁。

代码采用 [MIT 许可证](LICENSE)。当前仓库为私密开发仓库；当前适配机型为 **Sony KD-65X9000H / Android 12**，其他机型待逐机验证。

## 可以做什么

- **简洁首页**：中文／英文、四应用 Dock、应用网格和明确的遥控器焦点。
- **系统入口**：从实际 TV Input 列表选择信号源，进入网络与系统设置。
- **壁纸设置**：默认主题光晕、固定风景图或每 5 分钟自动轮播。
- **更新与恢复**：构建并更新 Quiet TV；按实际设备备份和记录系统包变更，保留恢复路径。
- **本地运行**：桌面不申请 INTERNET 权限，没有广告、账号系统和推荐流。

“应用升级改造”包括桌面更新安装和应用配置调整；当前不提供第三方影音应用的自动更新服务。

## 界面预览

以下为 Sony KD-65X9000H 在 2026-10-18 开发过程中的原始 ADB 截图，未重绘界面。封面是 AI 生成的品牌插画，截图才是实际设备画面。

**默认主题与应用 Dock**

![真实电视截图：默认主题、快捷入口和四应用 Dock](docs/assets/home.png)

**壁纸设置**

![真实电视截图：默认壁纸、三张风景图和轮播开关](docs/assets/wallpaper-settings.png)

[截图来源与封面制作说明](docs/assets/README.md)。第三方应用图标及壁纸的权利归各自权利人所有。

## 开始使用

1. 阅读[兼容性说明](docs/COMPATIBILITY.md)，确认 Android 版本与机型边界。
2. 按[安装指南](docs/INSTALL.zh-CN.md)准备 JDK、Android SDK，先构建，再安装和试用桌面。
3. 按[使用指南](docs/USAGE.zh-CN.md)配置 Dock、壁纸与遥控器操作。
4. 涉及系统包禁用前，单独备份目标设备并核对恢复记录。仓库里的索尼设备清单不可直接套用到其他电视。

当前交付为源码工程，没有已发布的稳定版 APK。构建脚本产出 debug APK，安装与适配记录不能替代其他设备的实机验收。

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

私密仓库的文档、下载和反馈入口仅对已获授权的账号开放。

## 项目来源与许可证

Quiet TV 基于 [Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack) 开发，感谢原作者 **Gathin**。本项目独立维护，面向国内使用场景继续适配，与上游及电视、应用品牌方无官方关联。

代码使用 [MIT](LICENSE)，保留 `Copyright (c) 2026 Gathin` 和完整上游许可。Outfit 字体按 SIL OFL 1.1 授权；内置壁纸不适用 MIT 代码许可。详见[第三方项目与资源说明](THIRD_PARTY_NOTICES.md)。
