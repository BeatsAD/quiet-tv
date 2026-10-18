# 安全政策 / Security policy

## 维护范围 / Supported versions

当前维护 `main` 的最新代码，尚无正式稳定版发布或历史版本长期维护承诺。安全修复优先提交到 `main`，由维护者核对受影响版本和机型。

The latest `main` is maintained. No stable release or long-term support for historical builds is currently promised. Fixes go to `main` first; affected versions and devices are assessed individually.

## 如何报告 / Reporting

当前仓库为私密仓库。GitHub 的 Private vulnerability reporting 仅支持公开仓库，此处尚无可用的“Report a vulnerability”入口。普通私密仓库 Issue 可被所有有相应访问权限的协作者阅读，不是仅报告人与维护者可见的漏洞通道。

已授权协作者可以在当前私密仓库中提交不含敏感复现细节的“安全联络请求”，由维护者 @BeatsAD 与报告人确认受限的沟通方式后再传递细节。已有可信私密联系方式时可直接联系维护者。本项目尚未指定公开安全邮箱，不应把猜测的邮箱作为报告渠道。

仓库以后公开前，需要启用 GitHub 私密漏洞报告，或明确公布维护者认可的私密安全联系方式。若页面出现有效的“Report a vulnerability”入口，优先使用该入口。不要将漏洞利用细节放到公开 Issue 或普通 PR 中。

This repository is private. GitHub Private vulnerability reporting is available for public repositories, so there is currently no active reporting form here. Issues in this private repository are visible to authorized collaborators, not only the reporter and maintainer.

Authorized collaborators may open a minimal security-contact request without exploit details. Maintainer @BeatsAD will agree a restricted communication channel with the reporter before details are shared. An existing trusted private contact may also be used. No public security email has been designated; do not assume an email address.

Before making the repository public, enable GitHub's private reporting form or publish an approved confidential contact. Prefer an active “Report a vulnerability” form when available. Never put exploit details in a public issue or ordinary PR.

## 报告内容 / Report contents

通过确认的受限渠道提供：受影响提交或版本、电视型号和固件、最小复现步骤、预期影响及可选修复建议。隐去账号、媒体内容、设备标识、SSID、局域网地址、ADB 私钥与令牌。不要提交完整设备备份。

Through the agreed restricted channel, include the affected commit or version, device and firmware, minimal reproduction, impact, and optional fix suggestions. Remove accounts, media content, device identifiers, SSIDs, local addresses, ADB private keys, and tokens. Do not send a full device backup.

## 使用与依赖 / Usage and dependencies

ADB 调试仅在可信网络启用，使用后可关闭。桌面无 INTERNET 权限，不意味着电脑上的 ADB 工具或电视系统没有安全风险。系统包变更前需保留目标设备原始备份与恢复记录。

已启用 Dependabot 漏洞提醒。Python 依赖来自 `requirements.txt`；Gradle 依赖通过受信任主分支上的依赖图工作流提交。工作流成功不表示没有漏洞，也不替代实机验收；依赖更新由人工审查，不自动合入。

Enable ADB only on a trusted network and disable it when no longer needed. The launcher's lack of INTERNET permission does not establish the security of host tools or the TV system. Preserve the target baseline and journal before package changes.

Dependabot alerts are enabled. Python dependencies come from `requirements.txt`; a trusted-main workflow submits Gradle dependencies. A successful submission does not prove an absence of vulnerabilities or hardware compatibility. Dependency updates require human review and are not automatically merged.

未承诺固定响应或修复时限；维护者会依据问题影响协调处理和披露。No fixed response or remediation SLA is promised; handling and disclosure are coordinated according to impact.
