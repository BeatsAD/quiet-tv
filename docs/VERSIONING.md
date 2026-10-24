# 版本与安装包规则

[首页](../README.md)  [安装与构建](INSTALL.zh-CN.md)  [English installation](INSTALL.en.md)

本页承接 [AGENTS.md](../AGENTS.md) 的安装包规则，适用于后续交付。历史安装包、校验记录、发布标签和下载链接保留原样。

**实施状态：主程序归档脚本已适配版本号与数字构建号、中文系统标签、实际单架构标记和同名 SHA-256 文件，并核对 APK 实际版本、签名及已登记 worktree 的归档构建号。助手及正式版流程仍需单独验证。**

**分包实施状态：标准版已确定采用 ARM32、ARM64、通用备用三个包的默认交付策略。脚本新增 `--batch`，按固定提交依次构建标准三包及一个安卓4.4包，核对版本、签名、生产输入、递增构建号，以及单架构包与通用包的原生库名称和字节一致性，并生成批次清单。首批最终 APK 的功能、升级保留和厂商真机验收仍须单独记录；批次清单不代表设备验收。**

## 命名与签名

主程序固定格式：

```text
QuietTV_<版本号>-<构建号>_<适用系统>[_<单架构标记>]_<发布状态>.apk
```

方括号表示可选字段，不写入实际名称。固定使用 `QuietTV` 前缀及下划线分隔，不加日期、提交哈希或“最新”。`standard`、`legacy19`、`api21`、`benchmark` 等技术字段写入归档记录。

### 版本号与构建号

- 版本号与实际 APK 的 `versionName` 一致，新版本使用 `主版本.次版本.修订号`。修复问题增加修订号，新增功能增加次版本，重大不兼容变化增加主版本。
- 构建号与实际 APK 的 `versionCode` 一致，只使用不带前导零的正整数，例如 `31`，不使用 `b31`、`Build31`、`RC31`。
- 主程序各系统档位、架构和构建类型共用递增交付序列；交付不同内容的新包时分配新号，不回退、复用或因版本变化归零。日常编译不必每次加号，同一归档原字节重发不分配新号。
- 交付前核对源码配置、历史归档和远程发布中的已用号。文件名、APK 版本和归档记录必须一致，不能通过改名冒充新版本。
- 主程序版本配置在 `app/build.gradle`，当前开发版本为 `1.2.1`，默认开发构建号为 `51`；交付时用 Gradle 参数 `quietBuildNumber` 分别指定递增构建号。1.1.2 已发布：标准档为 `1.1.2 / 34`，兼容档为 `1.1.2 / 35`，历史附件保持原样。standard 与 legacy19 均为 targetSdk36；本页示例不预占构建号。助手版本独立配置在 `coocaa-home-shim/build.gradle` 和 `xiaomi-home-helper/build.gradle`。

### 安卓档位与架构

| 文件名中的适用系统 | 构建对应关系 | 选择范围 |
| --- | --- | --- |
| `安卓4.4` | `legacy19`，minSdk19，`home.quiet.tv` | 安卓4.4兼容包，功能有所裁减 |
| `安卓5及以上` | `standard`，minSdk21，`home.quiet.tv` | 安卓5.0及以上优先选择的标准包 |
| `酷开安卓8` | 专用包名 `com.coocaa.quiettv`，minSdk26 | 已确认适配条件的固件专项验证，不按品牌推定兼容 |

同一标准包可覆盖多个安卓大版本，仅在存在实际兼容差异时新增档位。若交付其他最低 API 的标准包，标签也须同步变化：minSdk23 为 `安卓6及以上`，minSdk26 为 `安卓8及以上`，minSdk31 为 `安卓12及以上`。最低 API 满足不代表目标电视的 HOME、遥控器、输入源及重启唤醒已通过验收。

通用架构包省略 `universal`；纯 DEX/资源包不加架构标记。单架构包在发布状态前加 `ARM32`（`armeabi-v7a`）、`ARM64`（`arm64-v8a`）、`x86` 或 `x86_64`，并核对实际 APK 内容。兼容档位目前不含原生库，归档中的 `packaged_abis` 为空；构建时的 `--abi` 请求不能代替实际内容或实机架构验收。

### 默认分包交付

| 档位 | 默认交付范围 |
| --- | --- |
| 标准版 `home.quiet.tv` | ARM32、ARM64、通用备用三个完整 APK；通用包保留 ARM32、ARM64、x86、x86_64 四种原生库架构 |
| 安卓4.4兼容版 | 无原生库或无实际架构差异时保留一个包，不生成内容相同的架构副本 |
| 酷开专用版 | 按实际交付需求确定架构范围，保留专用包名与兼容条件，不默认扩展整套包 |

- 每台设备只安装其中一个完整 APK。同批标准三包共用源码提交、生产输入、软件版本、功能配置和签名证书，按架构保留对应原生库；不为各架构维护独立功能分支。
- 按 ARM32、ARM64、通用包的顺序分配全局递增构建号，通用包使用本批标准三包中的最高号。每包分别核验实际架构、所需原生库完整性、签名、版本和升级兼容性，并记录大小、SHA-256 及验收结果；未完成项由用户决定延期或排除，不宣称整批完成。
- 依据系统实际支持的架构选包；系统支持 ARM64 时优先选 ARM64，否则按支持情况选择 ARM32，架构不明确时提供通用备用包。64 位处理器可能运行 32 位系统，不能只按处理器名称或品牌推断。x86 设备可使用匹配的通用包，不默认新增两个公开下载项。
- 覆盖安装仍要求目标包构建号高于已安装包。同批高号通用包切换到低号 ARM 包不满足升级条件，应提供后续更高构建号的匹配包，不通过卸载、清数据或强制降级解决。保留用户配置及原有签名关系，分别验证升级后的数据保留。

### 发布状态与签名

| 发布状态 | 条件 |
| --- | --- |
| `测试版` | 普通 Debug 开发包及 benchmark 优化测试包 |
| `实验版` | 酷开专用适配、助手等实验包 |
| `正式版` | 持久正式签名、release 构建，并完成该档位约定验收 |

`benchmark` 目前启用代码及资源裁剪，使用 Debug 测试签名；`release` 尚未配置持久正式签名。未签名或仅使用测试签名的包不得当作正式版，单纯改名不能改变签名或验收状态。旧系统归档必须通过 v1 签名验证。

### 中转助手

助手使用 `QuietTV助手` 前缀，版本号及纯数字构建号独立于主程序。酷开助手的适用系统字段同时注明配套目标：`酷开安卓8配套普通桌面`（目标 `home.quiet.tv`）或 `酷开安卓8配套酷开桌面`（目标 `com.coocaa.quiettv`）。酷开助手自身包名为 `com.ccos.tv.homepage`，两种目标不能作为两个助手并存；更换目标需使用新的助手构建号。

助手归档记录注明目标包名、配套主程序文件名和 SHA-256。其 minSdk21 与 Android 8 固件适用范围分别记录，不因可安装就扩大支持范围。构建与恢复流程见[助手说明](../coocaa-home-shim/README.md)。

小米助手包名为 `home.quiet.tv.xiaomihelper`，当前实验范围仅为 MiTV4-ANSM0 / Android 6（API23），配套 `home.quiet.tv`。当前模块版本为 `0.1 / 1`，尚未适配三段版本号和统一归档规则，下次交付前须同步适配；构建、暂停与验收范围见[小米助手说明](../xiaomi-home-helper/README.md)。

### 命名示例

以下仅展示格式，不代表已分配构建号、构建、发布或完成设备验收：

```text
QuietTV_1.1.1-31_安卓5及以上_测试版.apk
QuietTV_1.1.1-32_安卓4.4_测试版.apk
QuietTV_1.1.1-33_酷开安卓8_实验版.apk
QuietTV_1.1.1-34_安卓5及以上_ARM32_测试版.apk
QuietTV_1.1.1-35_安卓5及以上_正式版.apk
QuietTV助手_2.0.1-12_酷开安卓8配套酷开桌面_实验版.apk
```

## 构建与归档

归档前为本批每个新包分配高于所有已用号且未占用的正整数构建号，标准三包按 ARM32、ARM64、通用的顺序递增。`STANDARD_BUILD_NUMBER` 表示下方单包示例的构建号；若随后交付兼容包，`LEGACY_BUILD_NUMBER` 须高于本批全部已分配号。不同新包不能复用同一构建号；1.1.2 的 34、35 已发布，不可用于新内容。

按[安装指南](INSTALL.zh-CN.md)配置 SDK，现有脚本读取 `local.properties` 的 `sdk.dir`：

以下命令只构建一个通用包，当前不会自动生成默认三包或完成整批验收。

```sh
python3 scripts/build_apk.py --build-number "$STANDARD_BUILD_NUMBER" --variant benchmark --summary "说明本次更新内容"
```

脚本按实际版本及传入构建号输出 APK、同名 `.apk.sha256`、归档 JSON 及优化映射文件；兼容包增加 `--compatibility legacy19`。已发布的 1.1.2 本地归档为 `QuietTV_1.1.2-34_安卓5及以上_测试版.apk` 与 `QuietTV_1.1.2-35_安卓4.4_测试版.apk`，仅原字节重发可复用这两个编号。

现有参数包括 `--variant benchmark`、`--abi armeabi-v7a`、`--min-sdk 21`（默认）、`23`、`26`、`31`。`--compatibility legacy19` 固定使用 API19，标准档不接受 API19。酷开专用桌面及各中转助手暂按各自说明构建，尚未接入统一归档；交付前须完成对应命名及元数据核验，不将当前脚本描述为已支持所有类别。

后续交付必须满足：

- APK 放入本地不可变归档 `dist/apk/`，提供 `<完整APK文件名>.sha256` 及相同主文件名的 `.json`；优化包同时保留映射文件。
- 归档记录包含真实包名、版本号、构建号、兼容档位、最低/目标 API、构建类型、实际架构、签名指纹、源码提交、生产输入指纹、时间、字节数、SHA-256、变更摘要和验收范围。助手额外记录配套关系。
- 同名同字节且校验记录一致可复用；同名不同内容禁止覆盖，必须使用新构建号。构建期间生产输入改变则停止归档。
- Gradle 的构建输出目录是可更新的中间产物；归档、密钥和恢复记录保留本地，不随源码发布。

整批构建前提交改动，固定源码提交；起始号高于所有已用号，命令如下（示例不预占号）：

```sh
python3 scripts/build_apk.py --batch --build-number "$FIRST_BUILD_NUMBER" --variant benchmark --summary "说明本次更新内容"
```

整批依次分配 ARM32、ARM64、通用及安卓4.4四个构建号；除每包归档外生成 `QuietTV_<版本号>-<起始号>-<末尾号>_batch.json`，记录共同源码、签名、各包元数据及原生库 SHA-256。构建失败时保留已归档包及占用号，不自动清理、覆盖或复用。

主程序脚本核对实际包名、版本、最低及目标 API、签名、架构、生产输入指纹与不可覆盖归档，检查所有已登记 worktree 的归档构建号；不自动联网查询远程 Release 或扫描未登记目录，交付者仍须补齐这两项核对。助手配套归档和正式签名流程另行验证。

## 安装与发布边界

- 下载说明按系统档位及架构展示适配包，并提供通用备用入口，不以最大构建号作为所有电视的统一推荐。测试包和实验包不标为稳定正式版。
- 覆盖升级核对相同包名、匹配签名、严格递增的构建号，以及系统和架构要求。标准包与兼容包共用 `home.quiet.tv`，不能同时安装；跨档位切换额外核对功能和数据兼容。
- 酷开专用包是独立应用，不覆盖普通包、不自动迁移数据。签名不同的正式包交付前先制定迁移办法，不承诺直接无损覆盖，不自动卸载旧包或清除数据。
- 构建、归档、安装、已安装包哈希核对、遥控器及功能验收分别记录。源码提交、工作区状态和历史验证不能证明当前设备已安装或已通过验收。
- CI 检查源码，不安装设备或发布 APK；设备升级及发布按明确授权执行。

## 1.1.2 发布与 GitHub 文件名例外

[Quiet TV 1.1.2 测试版](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.2) 已发布，同一 Release 集中标准档 34 与安卓 4.4 档 35。两档共用生产输入，源码基线见发布标签；均为 benchmark 优化测试签名包。

GitHub 清理中文附件名，本批经明确批准只对 GitHub 下载名采用以下映射；中文显示标签及本地原归档保持原样：

| 本地归档 | GitHub 下载 |
| --- | --- |
| `QuietTV_1.1.2-34_安卓5及以上_测试版.apk` | `QuietTV_1.1.2-34_Android5plus_Test.apk` |
| `QuietTV_1.1.2-35_安卓4.4_测试版.apk` | `QuietTV_1.1.2-35_Android4.4_Test.apk` |

APK 字节、版本与签名均未变化。实际英文名的 `.apk.sha256` 与下载配套，另保留原中文文件名校验记录。后续平台改名必须同步校验文件中的文件名，不能只重命名附件。此例外不改变本地命名规则。

## 历史发布

下列文件名和标签保持原样，不按新规则追溯改名。历史构建及验收只适用于对应产物：

[b30 安卓 4.4 兼容优化测试包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b30)为 `quiet-tv_1.1.0_b30_benchmark_legacy19_api19.apk`，2,412,105 字节（约 2.30 MiB），最低 API19，无原生库，使用与 b26 相同的测试证书。包内版本为 1.1.0 / 30，发布标签为 `v1.1.0-b30`。基于公开 main 新构建并保留不可变归档，生产源码提交为 `a2c35cb`；历史 b28 归档未覆盖。构建与验收边界见[兼容说明](COMPATIBILITY.md)。

[b26 标准版通用测试包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b26)继续保留：最低 API21、6,819,366 字节，包含四种 CPU 架构。历史 [b24 / API23 通用包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b24) 与 [b23 / API31 / ARM32 包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0) 均保留。b30 与 b26 是不同兼容档位，不代表相同功能范围。


## English

Published 1.1.2 provides standard build 34 (minSdk21) and legacy19 build 35 (minSdk19), both targeting API36. GitHub uses the approved English filenames listed in the installation guide; local Chinese archives are unchanged. The matching checksum must name the actual download. Historical b30 legacy19 and b26 standard downloads keep their original names and tags. At b30 publication, native emulator and physical-TV acceptance were still pending; historical results for other builds do not validate this APK. See [compatibility](COMPATIBILITY.md) for the recorded scope.

Future deliveries use `QuietTV_<version>-<numeric build>_<system>[_<single ABI>]_<status>.apk`. Version and build must match the actual APK. Build numbers are positive integers without prefixes or leading zeros and increase across all main-app delivery variants. Re-sending identical archived bytes does not allocate a new number. The helper uses `QuietTV助手`, its own version/build sequence, and an explicit target launcher.

System labels are `安卓4.4` (legacy19, API19), `安卓5及以上` (standard, API21), and `酷开安卓8` (experimental vendor package, API26). Other minimum APIs require matching labels. Omit the ABI for universal or native-library-free APKs; use ARM32, ARM64, x86 or x86_64 only for actual single-ABI builds. Status is `测试版` (test), `实验版` (experimental), or `正式版` (formally signed release with agreed acceptance completed).

Future standard deliveries default to three standalone APKs: ARM32, ARM64 and a universal fallback containing all four native-library ABIs. Each device installs one APK. All three share the source commit, production inputs, software version, feature configuration and signing certificate; assign increasing build numbers in ARM32, ARM64, universal order. Keep one legacy19 APK while it has no native-library or actual ABI differences; determine the Coocaa package set per delivery. Select by the ABIs supported by the operating system, and use the universal fallback when uncertain. In-place switches still require a higher build number; do not uninstall, clear data or force a downgrade to switch to a lower-numbered package.

The default package policy is recorded; batch-wide source checks, complete native-library checks, batch receipts and first-batch acceptance remain pending. The current script builds one APK per invocation with `--abi`. Complete and record implementation and acceptance before treating the three-package delivery as ready.

**The main-app build script implements the naming above, matching checksums, actual APK metadata validation and build-number checks across registered local worktree archives.** Remote releases, unregistered archives, helpers and formal-release acceptance require separate checks. Preserve historical files, checksums, tags and links. Archives require matching checksum and metadata records, including the actual package, SDKs, ABI, signing identity, source and acceptance scope. Same-name/different-byte replacement is forbidden.

Recommend downloads per system profile. In-place upgrades require matching package/signature, a strictly higher delivery build number and compatible system/ABI. Check data compatibility when switching profiles; different package IDs or signing identities need an explicit migration plan. CI, source checks and archive metadata do not establish device installation or acceptance. Device operations and publication require authorization.
