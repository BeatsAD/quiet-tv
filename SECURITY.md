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

ADB 调试仅在可信网络启用，使用后可关闭。桌面的 `INTERNET` 权限用于按需连接本机 ADB、局域网文件传输、天气查询、每日壁纸及更新清单和安装包下载；ADB 私钥保存在应用私有目录。局域网文件传输也应在可信网络使用。系统包变更前需保留目标设备原始备份与恢复记录。

已启用 Dependabot 漏洞提醒，公开检查覆盖 Gradle 与 GitHub Actions；Gradle 依赖通过受信任主分支上的依赖图工作流提交。公开 Python 构建、检查与迁移脚本仅使用标准库。本地设备工具的 `requirements.txt` 不随公开源码分发，也不在公开仓库的 pip 监控范围内，由使用者另行维护。工作流成功不表示没有漏洞，也不替代实机验收；依赖更新由人工审查，不自动合入。

Enable ADB only on a trusted network and disable it when no longer needed. The launcher uses `INTERNET` for on-demand local ADB, LAN file transfers, weather queries, daily wallpapers, and update manifest and APK downloads. Its ADB private key stays in app-private storage. Use LAN file transfers only on a trusted network as well. Preserve the target baseline and journal before package changes.

Dependabot covers public Gradle and GitHub Actions dependencies; a trusted-main workflow submits the Gradle graph. Public Python build, check and migration scripts use only the standard library. The requirements.txt for local device tools is not distributed or monitored by public pip updates; users maintain those local dependencies separately. A successful submission does not prove an absence of vulnerabilities or hardware compatibility. Dependency updates require human review and are not automatically merged.

[CodeQL 工作流](.github/workflows/codeql.yml)配置 Java/Kotlin、JavaScript/TypeScript、Python 和 GitHub Actions 安全扫描，在 PR、主分支推送、每周计划或手动触发时运行。Java 扫描分别编译下列档位，标准档位同时覆盖 HOME 代理、酷开空壳和小米助手；扫描不改变应用最低系统要求。

| 档位 / Profile | 最低 API / Minimum API | 应用 Guava / App Guava |
| --- | --- | --- |
| `standard` | 21 | Media3 间接引入 Android 变体，最低修复版本 `33.7.2-android` |
| `legacy14` | 14 | 使用平台播放器；安全约束不额外引入 Guava |
| `legacy19` | 19 | 使用平台播放器；安全约束不额外引入 Guava |

The [CodeQL workflow](.github/workflows/codeql.yml) configures Java/Kotlin, JavaScript/TypeScript, Python and GitHub Actions security scanning on pull requests, pushes to `main`, a weekly schedule and manual dispatch. Java analysis compiles each profile above; the standard job also covers the HOME proxy, Coocaa shim and Xiaomi helper. Standard builds use the patched Android Guava flavor through Media3; legacy builds keep the platform player without adding Guava. Build tools select the JRE flavor through Guava module metadata. These checks do not change minimum Android API levels.

工作流须同步到 GitHub 并成功运行后才有线上结果；告警为空或扫描成功不表示没有漏洞，也不替代各安卓档位和厂商真机验收。The workflow must reach GitHub and run successfully before online results exist. Empty alerts or successful scans do not prove an absence of vulnerabilities or replace Android-profile and vendor-device acceptance.

未承诺固定响应或修复时限；维护者会依据问题影响协调处理和披露。No fixed response or remediation SLA is promised; handling and disclosure are coordinated according to impact.
