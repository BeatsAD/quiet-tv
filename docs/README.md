# 文档索引 / Documentation

[中文首页](../README.md) · [English home](../README.en.md) · [贡献指南 / Contributing](../CONTRIBUTING.md)

## 安装与使用 / Install and use

- 安装：[中文](INSTALL.zh-CN.md) / [English](INSTALL.en.md)
- 使用：[中文](USAGE.zh-CN.md) / [English](USAGE.en.md)
- [兼容性与待验收范围 / Compatibility](COMPATIBILITY.md)
- [首次设置 / First setup](SETUP.md) · [应用管理 / App management](APP_MANAGEMENT.md)
- [AI 安装提示词 / AI-assisted installation](INSTALL_WITH_AI.zh-CN.md)
- 发布记录：[v1.1.0](releases/v1.1.0.md) · [v1.0.1](releases/v1.0.1.md)

## 开发与维护 / Development and maintenance

- [贡献流程与检查命令 / Contribution workflow](../CONTRIBUTING.md)
- [版本与安装包 / Versioning and APKs](VERSIONING.md)
- [仓库设置 / Repository settings](REPOSITORY.md) · [安全政策 / Security](../SECURITY.md)
- [依赖安全 / Dependency security](DEPENDENCY_SECURITY.md) · [优化记录 / Optimization](OPTIMIZATION.md)
- [配置与设备记录样例 / Local configuration examples](examples/README.md)
- [第三方许可 / Third-party notices](../THIRD_PARTY_NOTICES.md)

## 设计方案与验收记录 / Designs and evidence

这些文档记录不同开发阶段；以当前源码、明确版本的发布说明及对应证据为准，不将方案或历史截图视作全部功能已验收。
These documents describe different development stages. Plans and historical screenshots do not establish acceptance of every feature.

- 首次设置：[开发方案](SETUP_DEVELOPMENT_PLAN.md) · [焦点修复方案](SETUP_FOCUS_REPAIR_PLAN.md) · [验证记录](SETUP_VERIFICATION.md)
- 关于页：[开发方案](ABOUT_DEVELOPMENT_PLAN.md) · [验证记录](ABOUT_VERIFICATION.md)
- [应用管理设计](MANAGEMENT_REDESIGN.md) · [历史整机验收](../VERIFICATION.md)
- [索尼实施记录](DEVICE_NOTES.zh-CN.md) · [截图与展示素材来源](assets/README.md)

## 目录职责 / Repository layout

| 目录 / Directory | 用途 / Purpose |
| --- | --- |
| [app](../app/) | Android 原生应用与测试 / Native app and tests |
| [scripts](../scripts/) | 构建、离线检查及显式设备操作工具 / Build, offline checks, and explicit device tools |
| [prototypes](../prototypes/) | 交互原型及关于页打包输入 / Interaction prototypes and About bundle inputs |
| [tools/video-preview](../tools/video-preview/) | 视频剪辑源码与本地预览 / Video edit source and local preview |
| [management-fixture](../management-fixture/) / [home-fixture](../home-fixture/) | 设备测试辅助模块 / Device-test fixture modules |
| [gradle](../gradle/) / [.github](../.github/) | 构建工具、安全约束及协作自动化 / Build tooling and collaboration automation |

`device.json`、偏好、包清单、恢复记录以及 `backups/`、`evidence/`、`inspection/`、`output/` 和构建产物仅在本地保存。新克隆不包含实际设备基线；样例不能作为目标设备的恢复记录。

Device configuration, preferences, inventories, recovery journals, backups, evidence, inspection output, and build artifacts stay local. A fresh clone has no real device baseline. Examples are not recovery records for a target TV.
