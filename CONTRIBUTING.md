# 贡献指南 / Contributing

[简体中文首页](README.md) · [English home](README.en.md)

## 开始之前

请先查看 [Issues](https://github.com/BeatsAD/quiet-tv/issues) 和[兼容性说明](docs/COMPATIBILITY.md)。Bug、功能建议和新机型适配分别使用对应模板。安全问题遵循 [SECURITY.md](SECURITY.md)。当前仓库私密，参与者需先获得访问权限。

功能和适配工作在 `codex/简短名称` 或其他清晰命名的功能分支开展，通过 PR 说明问题、最终变化和验证结果。当前套餐没有启用远程主分支保护；分支与 PR 是维护约定，不是已生效的服务器门禁。

## 修改与验证

- 保持原生、轻量、遥控器可操作，不引入广告或无关后台服务。
- 不覆盖他人的未提交修改，不批量或递归删除文件，不提交设备密钥、账号资料、完整私密日志或其他用户的备份。
- 文档和模板变更检查链接、格式、事实及 `git diff --check`；不需要为文档构建和操作电视。
- Java、资源或构建配置变更运行 `./gradlew :app:assembleDebug --console=plain`。Python 改动检查语法及实际受影响路径；不要仅为检查而执行有设备副作用的脚本。
- 行为变化需记录相关机型、系统、安装类型和实际验证步骤。构建、截图、安装、真实播放、恢复与长时间运行分别报告；未执行的项目明确标记。
- 调整系统包前必须备份实际目标并记录原状态。保留既有系统数据与必要电视能力，不套用其他机型的禁用名单。

提交 PR 时使用仓库模板。新机型补充兼容性记录及必要的脱敏证据；上游代码与字体许可须继续保留。

## English

Check [issues](https://github.com/BeatsAD/quiet-tv/issues) and [compatibility](docs/COMPATIBILITY.md) first. Use the bug, feature, or device form; report security problems according to [SECURITY.md](SECURITY.md). This private repository requires access authorization.

Work on a descriptive feature branch, such as `codex/short-name`, and submit a PR explaining the problem, final behavior, and validation. Server-side main protection is currently unavailable on this private repository's plan; the PR workflow is a maintainer convention.

Keep the launcher native, lightweight, and remote-friendly. Preserve unrelated edits and device data. Do not use bulk or recursive file deletion. Never commit ADB keys, credentials, private logs, or other users' backups.

For docs and templates, verify links, formatting, factual claims, and `git diff --check`. For Android code or resource changes, run `./gradlew :app:assembleDebug --console=plain`. Check affected Python syntax without running device-mutating scripts just to validate them. Match further checks to the behavior changed.

Report build, installation, screenshots, playback, recovery, and long-run results separately, including the device, firmware, and build type. Mark unperformed checks. Back up the actual target before changing packages, preserve original state, and never reuse another model's disable list. Retain upstream and font licenses. Use the PR template and update compatibility records for new devices.
