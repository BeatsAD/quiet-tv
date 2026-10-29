# 文档索引 / Documentation

[中文首页](../README.md) · [English home](../README.en.md) · [贡献指南 / Contributing](../CONTRIBUTING.md)

## 使用与构建 / Use and build

- [在线安装指引（中文） / Online installation guide (Chinese)](https://beatsad.github.io/quiet-tv/)
- 安装与构建：[中文](INSTALL.zh-CN.md) / [English](INSTALL.en.md)
- [用 AI 编程工具实现电视桌面：技术与操作流程（含分阶段提示词）](TECHNICAL_PATH_WITH_CODEX.zh-CN.md)
- 使用：[中文](USAGE.zh-CN.md) / [English](USAGE.en.md)
- [兼容范围 / Compatibility](COMPATIBILITY.md)
- [桌面配置与恢复 / Setup and recovery](SETUP.md)
- [电脑适配与维护](AGENT_ADAPT.zh-CN.md)：检测、修复、信号源配置和自检报告
- [应用管理 / App management](APP_MANAGEMENT.md)
- [版本、签名与归档 / Versioning and archives](VERSIONING.md)
- 第三方许可：[中文](../THIRD_PARTY_NOTICES.md) / [English](../THIRD_PARTY_NOTICES.en.md) · [素材来源 / Image provenance](assets/README.md)

## 目录与协作 / Layout and collaboration

| 路径 / Path | 用途 / Purpose |
| --- | --- |
| [app/src/main](../app/src/main/) | Android 生产源码、资源与许可证 / Production code, resources and licenses |
| [prototypes/about](../prototypes/about/) | APK 关于资源和离线许可的构建输入 / Required About and offline-license build inputs |
| [prototypes/install-guide](../prototypes/install-guide/) | GitHub Pages 静态安装指引 / Static installation guide published to GitHub Pages |
| [scripts](../scripts/) | 公开构建与静态检查工具 / Public build and static checks |
| [gradle](../gradle/) | Wrapper 与依赖安全约束 / Wrapper and security constraints |
| [.github](../.github/) | CI、依赖检查、负责人及反馈模板 / CI, dependency updates, ownership and templates |

开发流程见[贡献指南](../CONTRIBUTING.md)，私密问题见[安全政策](../SECURITY.md)。

本地另保留计划、过程记录、测试与 fixture、设备配置、备份、证据、独立原型、视频工程和构建产物。这些文件不参与公开源码构建，也不作为公开文档链接目标；当前跟踪范围由根目录 `.gitignore` 明确约定。用户明确要求上传的额外内容可以加入例外。取消跟踪不删除本地原件，也不改写历史提交。

Plans, private tests, fixtures, device data, backups, standalone demos and generated files remain local. Fresh-clone builds use only public inputs. Explicit maintainer requests can add exceptions without discarding local originals or rewriting history.

- [Agent 安装与恢复](AGENT.md)：面向可执行终端命令的电脑 Agent，按实际设备选包并保留原桌面。

- [Agent 问题排查](AGENT_DIAGNOSE.zh-CN.md)：电视内扫码日志不足时，电脑侧指导复现、采集系统日志与生成反馈。
