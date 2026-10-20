# 2026-10-20 本地全分支集成检查

## 范围与结果

检查本仓库全部 7 个工作树、15 个本地分支（含 main）。本次仅本地提交与合并，没有推送远程、发布或安装到电视。浏览器日志、依赖目录和已有设备资料保留本地，未纳入源码；没有删除本地文件。

- 当前工作区的 README 与 5 个关于页 SVG 保存为 `49db1ab`，合入 `327e9b9`。
- setup-demo-remote 的 15 个已有修改保存为 `9833ff3`。该分支由纯模拟入口恢复真实原生流程，但主线已进一步完成相同需求、固定示意图、遥控焦点与原生关于页；这些较新实现继续保留，旧模拟页不重新作为产品入口。
- 安卓 6 兼容分支合入 `65eb7ed`，源码候选递增至 `1.1.0 / b24`，默认 minSdk 23、targetSdk 36；公开下载 b23 的 API31 / ARM32 条件保持不变。
- 对内容已被吸收或替代的剩余分支，采用 `ours` 策略记录合并关系；完整原提交继续可追溯。没有以旧分支内容覆盖较新的代码或恢复已排除的独立演示、宣传与设备记录。

## 分支清单

| 分支 | 检查时分支提交 | 处理 |
| --- | --- | --- |
| `codex/android6-compatibility` | `2a49b0a` | 已合入；保留主线原生关于页、焦点和性能优化 |
| `codex/community-collaboration` | `aa58295` | 已被 Squash 合入或被后续主线替代；本次补齐历史合并关系，保留当前内容 |
| `codex/fix-build-dependency-alerts` | `150307d` | 已被 Squash 合入或被后续主线替代；本次补齐历史合并关系，保留当前内容 |
| `codex/integrate-current-source` | `d166390` | 已被 Squash 合入或被后续主线替代；本次补齐历史合并关系，保留当前内容 |
| `codex/main-before-sync-20261019` | `2a49b0a` | 已合入；保留主线原生关于页、焦点和性能优化 |
| `codex/protect-main` | `93ac54d` | 原已属于 main 历史，无需重复应用 |
| `codex/public-showcase` | `b5f5dc2` | 原已属于 main 历史，无需重复应用 |
| `codex/readme-contact-publish` | `65788a8` | 原已属于 main 历史，无需重复应用 |
| `codex/readme-current-project` | `55b82c1` | 已被 Squash 合入或被后续主线替代；本次补齐历史合并关系，保留当前内容 |
| `codex/readme-rewrite` | `49db1ab` | 未提交修改保存为 49db1ab，已合入 327e9b9 |
| `codex/remove-focus-black-outline` | `ad9fec8` | 原已属于 main 历史，无需重复应用 |
| `codex/repository-layout` | `43480f2` | 已被 Squash 合入或被后续主线替代；本次补齐历史合并关系，保留当前内容 |
| `codex/setup-demo-remote` | `9833ff3` | 未提交修改经编译与现有单元检查后保存为 9833ff3；功能已被主线后续实现替代，保留完整历史 |
| `codex/source-only-repository` | `e2c094b` | 已被 Squash 合入或被后续主线替代；本次补齐历史合并关系，保留当前内容 |

## 等价和替代依据

- `codex/readme-current-project` 与 `5ee2d11`、`codex/source-only-repository` 与 `66786b0`、`codex/integrate-current-source` 合并补丁与 `e070310` 的稳定 patch-id 分别相同。
- `codex/community-collaboration` 的三提交累计补丁与 `6f7c413` 的稳定 patch-id 相同。后续 source-only 整理移除了独立演示相关检查，继续沿用当前维护范围。
- `codex/fix-build-dependency-alerts` 的安全版本约束已在主线继续扩充；不回退应用密码库安全下限、JitPack 或辅助测试模块。
- `codex/repository-layout` 的目录职责、脱敏样例、设备记录隔离及安装包说明已在主线存在，并由 source-only 与后续首页更新继续整理；不恢复已取消跟踪的独立视频、原型和提示词。
- setup-demo-remote 中新增的连接输入框遥控焦点逻辑已逐行存在于主线；设置测试新增内容除旧预览结构断言外已存在。保留主线固定图片预览、连接等待期间的取消焦点及后续修复。

## 冲突处理

保留当前原生关于首屏与按需离线许可，合入旧系统字体回退、AndroidX 返回处理及 API23 许可导航。保留 HOME 异常运行记录和原桌面回退，同时切换与回退均使用已检测支持的命令。焦点回调原使用 API24 的 Consumer，改用等价的本地回调接口以支持 API23。构建脚本同步 API23 默认值，并保留 API26 / API31 显式选项；非 API31 归档名称包含实际最低系统后缀。

## 验证

- `:app:lintDebug`：0 错误、27 条警告。
- `:app:testDebugUnitTest`：19 项通过。
- `:app:assembleDebug`、`:app:assembleDebugAndroidTest`、`:home-fixture:assembleDebug`、`:management-fixture:assembleDebug`：通过。仪器测试仅编译。
- `:app:assembleBenchmark`：代码与资源压缩构建通过；Debug 与 Benchmark APK 签名验证通过，包含 Android 6 所需的 v1 签名。Debug APK 元数据为 1.1.0 / b24、minSdk 23、targetSdk 36。
- Python 工具离线回归：7 项通过，包括 API23 / API26 归档后缀与不可覆盖检查。
- `scripts/validate_profiles.py`：1 个可执行固件配置、30 个未验证参考记录，规则校验通过。
- `scripts/check_contribution.py`、差异空白、修改文档本地链接、JavaScript 语法及 5 个 SVG XML 语法：通过。

没有连接设备或模拟器；没有执行本轮界面、真实 HOME 切换、包管理或保留数据升级验收。历史模拟器和真机记录仅代表对应历史候选。本次没有创建新的不可变交付归档。
