# 文档索引 / Documentation

[中文首页](../README.md) · [English home](../README.en.md) · [贡献指南 / Contributing](../CONTRIBUTING.md)

## 使用与构建 / Use and build

- 安装与构建：[中文](INSTALL.zh-CN.md) / [English](INSTALL.en.md)
- [电视桌面的技术实现路径：以 Codex 为例](TECHNICAL_PATH_WITH_CODEX.zh-CN.md)
- 使用：[中文](USAGE.zh-CN.md) / [English](USAGE.en.md)
- [兼容范围 / Compatibility](COMPATIBILITY.md)
- [首次设置与恢复 / Setup and recovery](SETUP.md)
- [应用管理 / App management](APP_MANAGEMENT.md)
- [版本、签名与归档 / Versioning and archives](VERSIONING.md)
- [第三方许可 / Third-party notices](../THIRD_PARTY_NOTICES.md) · [素材来源 / Image provenance](assets/README.md)

## 目录与协作 / Layout and collaboration

| 路径 / Path | 用途 / Purpose |
| --- | --- |
| [app/src/main](../app/src/main/) | Android 生产源码、资源与许可证 / Production code, resources and licenses |
| [prototypes/about](../prototypes/about/) | APK 关于资源和离线许可的构建输入 / Required About and offline-license build inputs |
| [scripts](../scripts/) | 公开构建与静态检查工具 / Public build and static checks |
| [gradle](../gradle/) | Wrapper 与依赖安全约束 / Wrapper and security constraints |
| [.github](../.github/) | CI、依赖检查、负责人及反馈模板 / CI, dependency updates, ownership and templates |

开发流程见[贡献指南](../CONTRIBUTING.md)，私密问题见[安全政策](../SECURITY.md)。

本地另保留计划、过程记录、测试与 fixture、设备配置、备份、证据、独立原型、视频工程和构建产物。这些文件不参与公开源码构建，也不作为公开文档链接目标；当前跟踪范围由根目录 `.gitignore` 明确约定。用户明确要求上传的额外内容可以加入例外。取消跟踪不删除本地原件，也不改写历史提交。

Plans, private tests, fixtures, device data, backups, standalone demos and generated files remain local. Fresh-clone builds use only public inputs. Explicit maintainer requests can add exceptions without discarding local originals or rewriting history.
