# 仓库设置与限制 / Repository settings and limits

核对日期 / Checked: 2026-10-19。

| 项目 / Item | 状态 / Status |
| --- | --- |
| 可见性 / Visibility | Public；按维护者授权公开 / Public, authorized by the maintainer |
| 默认分支 / Default branch | main |
| MIT 与上游署名 / License and attribution | 已保留 / Present |
| Topics | android-tv, google-tv, adb, launcher, chinese, sony, tv-customization |
| Dependabot alerts | 已启用 / Enabled |
| 依赖版本检查 / Dependency update checks | Gradle、pip 与 Actions 每月检查，人工合入 / Monthly Gradle, pip, and Actions; manual merge |
| 安全更新 PR / Security update PRs | Dependabot 自动提出修复 PR，人工评审合入 / Dependabot security updates enabled; manual review and merge |
| 密钥扫描与推送保护 / Secret scanning and push protection | 已启用 / Enabled |
| 交流 / Discussions | 已启用 / Enabled |
| 合并方式 / Merge method | 仅 Squash merge；合入后自动删除本仓库功能分支 / Squash only; auto-delete merged repository branches |
| PR 自动检查 / PR checks | Validate；按修改范围执行检查 / Validate, scoped to changed projects |
| 外部工作流 / Fork workflows | 首次贡献者需维护者批准运行；默认令牌只读 / First-time contributors require approval; default token read-only |
| 主分支保护 / Main protection | 已启用；API 读回 protected: true / Enabled; API confirms protected: true |
| 私密漏洞报告 / Private vulnerability reporting | 已启用；API 读回 enabled: true / Enabled; API confirms enabled: true |

## 主分支维护 / Maintaining main

GitHub 已启用并读回 main 分支保护：变更必须通过 PR 合入、解决全部讨论，禁止强推和删除 main，管理员同样受规则约束。个人维护不强制要求另一位审批人（必需批准数为 0）；新增提交会撤销过时的批准。必需状态检查为 `Validate`，要求分支与最新 main 保持一致后通过检查。

GitHub main-branch protection is enabled and verified through the API. Changes require a PR and resolved conversations; force pushes and deletion are blocked, including for administrators. Solo maintenance requires zero approving reviews; new commits dismiss stale approvals. The required status check is `Validate`, with the branch required to be up to date with main.

[GitHub 分支保护说明 / Branch protection](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)。

## 社区维护 / Community maintenance

开发流程、检查命令和维护参与方式见[贡献指南](../CONTRIBUTING.md)；社区沟通遵循[行为准则](../CODE_OF_CONDUCT.md)。[CODEOWNERS](../.github/CODEOWNERS) 当前仅指定 @BeatsAD，尚未邀请其他协作者。第二位稳定维护者加入后，再启用至少 1 人批准及模块负责人必需评审。

See [Contributing](../CONTRIBUTING.md) for setup and maintainer participation, and follow the [code of conduct](../CODE_OF_CONDUCT.md). [CODEOWNERS](../.github/CODEOWNERS) currently names only @BeatsAD. Require one approval and code-owner review after a second regular maintainer joins.

`Validate` 会检查差异空白、修改文档的本地文件链接和 Python 语法；Android 变更编译主应用、instrumentation 测试 APK 和两个辅助模块，并执行已有 JVM 测试。现有 JVM 测试覆盖设置策略及配对失败处理，Python 工具变更执行离线回归测试；instrumentation 测试仅编译。纯文档修改不构建 Android。CI 不连接电视、不执行系统包操作，也不自动发布 APK。

`Validate` checks whitespace, local file links in changed Markdown, and Python syntax. Affected Android changes compile the app, instrumentation APK, and fixture modules and run available JVM tests. JVM tests cover setup policy and pairing failures; Python tools run offline regression tests. Instrumentation tests are compiled only. Docs-only changes skip Android builds. CI never contacts a TV, changes system packages, or publishes APKs.

## 本地数据与目录 / Local data and directories

[文档索引](README.md)提供目录职责与文档入口。独立网页演示、宣传视频、生成提示词及旧原型截图只保留本地，不再上传；`prototypes/about` 是实际 APK 构建输入，继续跟踪。设备配置、偏好、包清单和恢复日志仅保留本地，Git 分发[脱敏样例](examples/README.md)。取消跟踪不会删除本地原件，也不改写已发布的历史提交；新克隆不含目标设备基线。

The [documentation index](README.md) maps the repository. Standalone demos, promotional video work, generation prompts, and old prototype captures stay local. `prototypes/about` remains tracked because the APK build consumes it. Device settings, preferences, inventories, and recovery journals stay local; Git distributes [redacted examples](examples/README.md). Untracking preserves local originals and does not rewrite public history. Fresh clones have no device baseline.

## 依赖提醒 / Dependency alerts

Python 清单由 GitHub 分析。Gradle 依赖需要依赖图提交，工作流只在 main 的相关构建文件变化或手动运行时解析并提交，不安装到电视。该任务使用仓库令牌的 contents: write 以调用依赖图提交 API；默认工作流权限仍为只读。Dependabot 每月提出版本更新 PR，并自动提出可用的安全修复 PR；均不自动合入。

GitHub analyzes the Python manifest. The Gradle workflow resolves and submits dependencies on relevant main-branch changes or manual dispatch, without contacting a TV. Its job needs contents: write for the submission API; the repository's default workflow permission remains read-only. Monthly version updates and available security-fix PRs are reviewed manually.

没有提醒不代表所有依赖和传递依赖都已识别或不存在漏洞。查看工作流和依赖图的实际结果。No alert does not prove complete coverage or absence of vulnerabilities; inspect workflow and graph results.

## 漏洞报告与封面 / Reporting and cover

按 [SECURITY.md](../SECURITY.md) 使用当前报告路径。已启用 [GitHub 私密漏洞报告](https://github.com/BeatsAD/quiet-tv/security/advisories/new)。

Follow [SECURITY.md](../SECURITY.md). Use the enabled [private vulnerability reporting form](https://github.com/BeatsAD/quiet-tv/security/advisories/new).

封面已经嵌入两种语言的 README。它是项目品牌插画，不是真机截图。GitHub Settings 的 Social preview 是独立的上传选项；本次交付不将 README 图片等同于已设置 Social preview。

The cover is embedded in both READMEs as a brand illustration. GitHub's Settings / Social preview is a separate upload option; embedding a README cover does not set the repository's social preview.
