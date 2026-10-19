# 安全政策 / Security policy

## 维护范围 / Supported versions

当前维护 `main` 的最新代码，尚无正式稳定版发布或历史版本长期维护承诺。安全修复优先提交到 `main`，由维护者核对受影响版本和机型。

The latest `main` is maintained. No stable release or long-term support for historical builds is currently promised. Fixes go to `main` first; affected versions and devices are assessed individually.

## 如何报告 / Reporting

仓库已公开，GitHub 私密漏洞报告已启用。请通过 [Report a vulnerability](https://github.com/BeatsAD/quiet-tv/security/advisories/new) 向维护者提交安全问题，报告不会作为公开 Issue 发布。

不要将漏洞利用细节放到公开 Issue 或普通 PR 中。本项目尚未指定公开安全邮箱，不应把猜测的邮箱作为报告渠道。

The repository is public and GitHub Private vulnerability reporting is enabled. Submit security issues through [Report a vulnerability](https://github.com/BeatsAD/quiet-tv/security/advisories/new); reports are not published as public issues.

Never put exploit details in a public issue or ordinary PR. No public security email has been designated; do not assume an email address.

## 报告内容 / Report contents

通过确认的受限渠道提供：受影响提交或版本、电视型号和固件、最小复现步骤、预期影响及可选修复建议。隐去账号、媒体内容、设备标识、SSID、局域网地址、ADB 私钥与令牌。不要提交完整设备备份。

Through the agreed restricted channel, include the affected commit or version, device and firmware, minimal reproduction, impact, and optional fix suggestions. Remove accounts, media content, device identifiers, SSIDs, local addresses, ADB private keys, and tokens. Do not send a full device backup.

## 使用与依赖 / Usage and dependencies

ADB 调试仅在可信网络启用，使用后可关闭。桌面申请 INTERNET 权限，仅用于按需连接本机 ADB；密钥保存在应用私有目录。系统包变更前需保留目标设备原始备份与恢复记录。

已启用 Dependabot 漏洞提醒。Python 依赖来自 `requirements.txt`；Gradle 依赖通过受信任主分支上的依赖图工作流提交。工作流成功不表示没有漏洞，也不替代实机验收；依赖更新由人工审查，不自动合入。

Enable ADB only on a trusted network and disable it when no longer needed. The launcher requests INTERNET permission only for on-demand local ADB; its independent key stays in app-private storage. Preserve the target baseline and journal before package changes.

Dependabot alerts are enabled. Python dependencies come from `requirements.txt`; a trusted-main workflow submits Gradle dependencies. A successful submission does not prove an absence of vulnerabilities or hardware compatibility. Dependency updates require human review and are not automatically merged.

未承诺固定响应或修复时限；维护者会依据问题影响协调处理和披露。No fixed response or remediation SLA is promised; handling and disclosure are coordinated according to impact.
