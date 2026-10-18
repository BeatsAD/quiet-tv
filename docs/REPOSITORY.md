# 仓库设置与限制 / Repository settings and limits

核对日期 / Checked: 2026-10-18。

| 项目 / Item | 状态 / Status |
| --- | --- |
| 可见性 / Visibility | Private；未经明确授权不改为公开 / Remains private |
| 默认分支 / Default branch | main |
| MIT 与上游署名 / License and attribution | 已保留 / Present |
| Topics | android-tv, google-tv, adb, launcher, chinese, sony, tv-customization |
| Dependabot alerts | 已启用 / Enabled |
| 依赖版本检查 / Dependency update checks | Gradle、pip 和 Actions 每月检查，人工合入 / Monthly Gradle, pip, and Actions checks; manual merge |
| 主分支保护 / Main protection | 未启用：API 返回 403，当前私密仓库需升级 Pro 或公开才能使用 / Unavailable: API 403, requires Pro or public visibility |
| 私密漏洞报告 / Private vulnerability reporting | 未启用：仅公开仓库支持，当前私密仓库 API 返回 404 / Public repositories only; private repository API returns 404 |

## 主分支维护 / Maintaining main

当前使用功能分支和 PR 约定维护。目标保护规则为：通过 PR 合入、解决讨论、禁止强推与删除 main；个人维护时不强制要求另一位审批人。套餐条件满足后需实际配置并读回规则，在此之前不要把本文件当作服务器保护。

Use feature branches and PRs by convention. Intended rules require PRs and resolved conversations, and prevent force pushes and deletion of main. Solo maintenance does not require another reviewer. Enable and read back rules when the plan permits; this document does not enforce them.

[GitHub 分支保护说明 / Branch protection](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)。

## 依赖提醒 / Dependency alerts

Python 清单由 GitHub 分析。Gradle 依赖需要依赖图提交，工作流只在 main 的相关构建文件变化或手动运行时解析并提交，不安装到电视。该任务使用仓库令牌的 contents: write 以调用依赖图提交 API；默认工作流权限仍为只读。Dependabot 每月提出版本更新 PR，不自动合入。

GitHub analyzes the Python manifest. The Gradle workflow resolves and submits dependencies on relevant main-branch changes or manual dispatch, without contacting a TV. Its job needs contents: write for the submission API; the repository's default workflow permission remains read-only. Monthly version-update PRs are reviewed manually.

没有提醒不代表所有依赖和传递依赖都已识别或不存在漏洞。查看工作流和依赖图的实际结果。No alert does not prove complete coverage or absence of vulnerabilities; inspect workflow and graph results.

## 漏洞报告与封面 / Reporting and cover

按 [SECURITY.md](../SECURITY.md) 使用当前报告路径。公开仓库前应启用正式私密报告入口，或补充认可的私密联系方式。

Follow [SECURITY.md](../SECURITY.md). Enable a confidential reporting form or publish an approved private contact before making the repository public.

封面已经嵌入两种语言的 README。它是项目品牌插画，不是真机截图。GitHub Settings 的 Social preview 是独立的上传选项；本次交付不将 README 图片等同于已设置 Social preview。

The cover is embedded in both READMEs as a brand illustration. GitHub's Settings / Social preview is a separate upload option; embedding a README cover does not set the repository's social preview.
