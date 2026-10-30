# 版本与安装包规则

## 历史：Android 4.0–4.4 兼容档位（1.3.0测试版131）

历史1.3.0新增 `--compatibility legacy14`，固定 minSdk14，输出一个 `安卓4.0至4.4` 通用包；`--batch --universal-only` 使用标准通用包和此兼容包。各包继续使用相同源码与软件版本、递增构建号及真实签名，历史档位和旧文件保持原样。`legacy19` 固定 API19，仅用于历史构建与升级回归，不另加进默认交付批次。当时格式示例为 `QuietTV_<版本号>-<构建号>_安卓4.0至4.4_测试版.apk`，不预占版本或构建号。119–122 已用于本轮较早的本地兼容候选，保留记录且不复用；本地验证用标准包和统一兼容包分别使用123、124，后续标准包125也已占用，均未发布。本轮按维护者要求保持1.3.0并累加功能与修复。126用于审查回归调试候选，127保留为此前分配号；128/129因标准包混入历史原生库中间产物而未通过批次检查，保留候选且不作为本批交付。打包输出改为按构建号隔离；最终标准通用包130与安卓4.0至4.4兼容包131已构建、签名和批次校验通过；按本批审核确认上传至原1.3.0 Release并同步revision9双更新清单。未完成或失败候选的占用号仍保留。构建通过不代替 API14–19 功能、覆盖升级和厂商固件验收。


[首页](../README.md)  [安装与构建](INSTALL.zh-CN.md)  [English installation](INSTALL.en.md)

本页承接 [AGENTS.md](../AGENTS.md) 的安装包规则，适用于后续交付。历史安装包、校验记录、发布标签和下载链接保留原样。

TCL 等受限固件可另行交付主程序 relay 实验档，复用公共代码及桌面守护，不声明 HOME。`scripts/build_apk.py --home-mode relay` 显式传入并核验真实清单，记录 `home_mode=relay` 与构建参数；安卓4.4不提供此档。文件名沿用系统／架构规则、状态用“实验版”，如 `QuietTV_<版本号>_安卓5.0及以上_实验版.apk`。普通包与 relay 同版本、同源码及签名，但构建号各自占用同一递增序列，不能同名覆盖。relay 不加入默认批次，按实际授权单独构建；发布前仍须完成本批审核与适用验证。签名更新清单和安装索引明确记录 HOME 形态，不从“实验”字样推断；已装 relay 仅匹配 relay 更新。

**实施状态：主程序归档脚本已去掉 APK 文件名中的构建号，输出中文本地名，在 JSON 的 `public_apk` 记录英文公开名；安卓范围使用 `4.0-4.4`、`5.0及以上`／`5.0AndAbove`，不再使用 `plus` 或 `to`。内部构建号、真实版本、签名、同名 SHA-256 文件及已登记 worktree 的构建号检查保留；同档位同版本不同内容须增加软件版本号。通用架构或不含原生库时省略架构标记。助手及正式版流程仍需单独验证。**

**分包实施状态：当前开发源码无原生库，使用 `--batch --universal-only` 构建标准通用包和安卓4.0至4.4包。存在原生库架构差异时，使用 `--batch` 构建标准 ARM32、ARM64、通用及一个安卓4.0至4.4包。两种模式均固定源码并核对版本、签名、生产输入和递增构建号；四包模式另核对各架构原生库完整性。最终 APK 的功能、升级保留和厂商真机验收仍须单独记录，批次清单不代表设备验收。**

## 命名与签名

主程序安装包固定格式：

```text
QuietTV_<版本号>_<适用系统>[_<单架构标记>]_<发布状态>.apk
```

方括号表示可选字段，不写入实际名称。固定使用 `QuietTV` 前缀及下划线分隔，文件名不含构建号、日期、提交哈希或“最新”；APK 内部 `versionCode` 仍递增，保留在归档元数据和排障记录中。`standard`、`legacy14`、`legacy19`、`api21`、`benchmark` 等技术字段写入归档记录。历史包、附件和下载链接不追溯改名。

### 版本号与构建号

- 版本号与实际 APK 的 `versionName` 一致，新版本使用 `主版本.次版本.修订号`。修复问题增加修订号，新增功能增加次版本，重大不兼容变化增加主版本。
- 同版本各档位、架构共用软件版本号。同版本、系统、架构和状态只对应一份确定内容；修改后重新交付同档位包必须增加软件版本号，例如从 `1.3.1` 改为 `1.3.2`，同时增加内部构建号。历史1.3.0曾经批准同软件版本累加构建号，旧文件保留；该历史例外不适用于后续无构建号文件名。公开发布仍须完成本批材料审核。
- 构建号与实际 APK 的 `versionCode` 一致，只使用不带前导零的正整数，例如 `31`，不使用 `b31`、`Build31`、`RC31`。
- 主程序各系统档位、架构和构建类型共用递增交付序列；交付不同内容的新包时分配新号，不回退、复用或因版本变化归零。日常编译不必每次加号，同一归档原字节重发不分配新号。
- 交付前核对源码配置、历史归档和远程发布中的已用号。文件名中的软件版本须与 APK 的 `versionName` 对应，归档中的构建号须与 APK 的 `versionCode` 对应，不能通过改名冒充新版本；安装时核对 APK 内部构建号。
- 主程序版本配置在 `app/build.gradle`，当前开发版本为 `1.3.1`；后续交付使用新的递增构建号，公开发布须另行审核确认。历史1.3.0标准130与4.x兼容131保持原样，默认开发构建号仍为 `105`，不作为新包交付号；下次交付先核对所有已用号，再用 Gradle 参数 `quietBuildNumber` 分别指定递增构建号。1.1.2 已发布：标准档为 `1.1.2 / 34`，兼容档为 `1.1.2 / 35`，历史附件保持原样。standard、legacy14 与 legacy19 均为 targetSdk36；本页示例不预占构建号。助手版本独立配置在 `coocaa-home-shim/build.gradle` 和 `xiaomi-home-helper/build.gradle`。

### 安卓档位与架构

| 文件名中的适用系统 | 构建对应关系 | 选择范围 |
| --- | --- | --- |
| `安卓4.0-4.4` | `legacy14`，minSdk14，`home.quiet.tv` | 当前开发兼容包，旧系统实际运行验收延期 |
| `安卓4.4` | `legacy19`，minSdk19，`home.quiet.tv` | 历史兼容档位，保留构建与升级回归 |
| `安卓5.0及以上` | `standard`，minSdk21，`home.quiet.tv` | 安卓5.0及以上优先选择的标准包 |
| `酷开安卓8` | 专用包名 `com.coocaa.quiettv`，minSdk26 | 已确认适配条件的固件专项验证，不按品牌推定兼容 |
| `酷开安卓5.0及以上` | 专用包名 `com.coocaa.quiettv`，standard，minSdk21 | 源码允许构建，跳板与守护按本机能力配置，真机待验收 |
| `酷开安卓4.4` | 专用包名 `com.coocaa.quiettv`，legacy19，minSdk19 | 使用API19裁减功能，不包含守护、跳板或独立酷开助手 |

同一标准包可覆盖多个安卓大版本，仅在存在实际兼容差异时新增档位。其他最低 API 的标签同步变化：minSdk23 为 `安卓6.0及以上`，minSdk26 为 `安卓8.0及以上`，minSdk31 为 `安卓12.0及以上`。最低 API 满足不代表目标电视的 HOME、遥控器、输入源及重启唤醒已通过验收。

通用架构包及纯 DEX/资源包省略架构标记。单架构包在状态前加 `ARM32`（`armeabi-v7a`）、`ARM64`（`arm64-v8a`）、`x86` 或 `x86_64`，并核对实际 APK 内容。通用架构不代表所有系统及固件均已兼容。兼容档位目前不含原生库，归档中的 `packaged_abis` 为空；构建时的 `--abi` 请求不能代替实际内容或实机架构验收。

### 默认分包交付

| 档位 | 默认交付范围 |
| --- | --- |
| 标准版 `home.quiet.tv` | 有原生库时提供 ARM32、ARM64、通用备用三包；无原生库且无架构差异时只提供一个通用包。1.3.0 使用通用包 |
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

助手使用 `QuietTV助手` 前缀，版本号及纯数字构建号独立于主程序；文件名保留实际递增构建号。酷开助手的适用系统字段同时注明配套目标：`酷开安卓8配套普通桌面`（目标 `home.quiet.tv`）或 `酷开安卓8配套酷开桌面`（目标 `com.coocaa.quiettv`）。酷开助手自身包名为 `com.ccos.tv.homepage`，两种目标不能作为两个助手并存；更换目标需使用新的助手构建号。

助手归档记录注明目标包名、配套主程序文件名和 SHA-256。其 minSdk21 与 Android 8 固件适用范围分别记录，不因可安装就扩大支持范围。构建与恢复流程见[助手说明](../coocaa-home-shim/README.md)。

小米助手包名为 `home.quiet.tv.xiaomihelper`，当前实验范围仅为 MiTV4-ANSM0 / Android 6（API23），配套 `home.quiet.tv`。当前模块版本为 `0.1 / 1`，尚未适配三段版本号和统一归档规则，下次交付前须同步适配；构建、暂停与验收范围见[小米助手说明](../xiaomi-home-helper/README.md)。

HOME 中转助手有默认身份 `home.quiet.tv.proxy` 和小米／Redmi 兼容身份 `com.ts.launcher`，通过 `quietProxyApplicationId` 选择，共用源码，最低 API21；普通主程序保留原包名；小米当前优先路线另以 `quietApplicationId=com.ts.launcher` 构建主程序，与同名助手互斥。两条路线不自动迁移配置。两个身份各自核对真实包名、签名、版本和配套目标，安装前检查同名外部应用占用。此助手与上文旧无障碍小米助手是不同模块；构建及范围见 [HOME 助手说明](../home-proxy/README.md)。

小米改名主程序属于独立包名，最低 API21，仅提供标准 HOME 形态，共用主线代码。已有 `com.ts.launcher` 主程序可在同签名、递增构建号时验证覆盖升级；普通主程序或同名助手不属于可直接覆盖的数据基线。现有主程序归档脚本与公开更新清单只覆盖原有身份，交付此身份前须另核对归档、版本和更新配套；本轮不打包交付。

### 命名示例

后续主程序使用以下格式。本节示例不预占新版本、不表示重新归档或修改已发布附件：

```text
QuietTV_1.3.1_安卓5.0及以上_测试版.apk
QuietTV_1.3.1_安卓4.0-4.4_测试版.apk
QuietTV_1.3.1_安卓5.0及以上_ARM32_测试版.apk
```

英文公开名记录在 `public_apk`，本地不额外复制英文 APK，不自动上传：

| 本地系统标签 | 公开系统标签 |
| --- | --- |
| `安卓4.0-4.4` | `Android4.0-4.4` |
| `安卓4.4` | `Android4.4` |
| `安卓5.0及以上` | `Android5.0AndAbove` |
| `安卓6.0及以上` | `Android6.0AndAbove` |
| `安卓8.0及以上` | `Android8.0AndAbove` |
| `安卓12.0及以上` | `Android12.0AndAbove` |

状态映射为 `测试版` → `Test`、`实验版` → `Experimental`、`正式版` → `Release`；主程序脚本目前仅实现测试版及 relay 实验版。公开名示例：

```text
QuietTV_1.3.1_Android5.0AndAbove_Test.apk
QuietTV_1.3.1_Android4.0-4.4_Test.apk
QuietTV_1.3.1_Android5.0AndAbove_ARM32_Test.apk
```

安装索引、电脑选包及应用更新按实际 URL、签名元数据和 APK 内部版本识别，不从文件名猜测构建号或兼容范围；历史名继续可用。现有 GitHub 附件和下载链接保持原样。

下方 1.2.9 为历史批次命名，其他旧名称仅作历史格式示例；中转助手仍沿用独立命名，尚未接入主程序归档脚本：

```text
QuietTV_1.2.9-105_安卓5及以上_ARM32_测试版.apk
QuietTV_1.2.9-106_安卓5及以上_ARM64_测试版.apk
QuietTV_1.2.9-107_安卓5及以上_测试版.apk
QuietTV_1.2.9-108_安卓4.4_测试版.apk
QuietTV_1.1.1-33_酷开安卓8_实验版.apk
QuietTV_1.1.1-35_安卓5及以上_正式版.apk
QuietTV助手_2.0.1-12_酷开安卓8配套酷开桌面_实验版.apk
```

历史 1.2.9 批次按 ARM32、ARM64、通用和安卓4.4顺序分配 105–108，包含天气地点、多源切换及首页启动优化；1.2.8 的90–93已发布，94和不含上述修复的95–98已用于本地候选，99–100已用于天气验证包，101–104已为审查修复前的候选批次分配（104构建未完成），均保留记录且不复用。该历史批次的英文名使用 `Android5plus` 等旧映射并保留构建号，不追溯改名；后续主程序使用上方无构建号新格式。上传前核对实际下载名及 APK 字节；本地校验文件中的文件名须与其对应的本地 APK 一致。

## 构建与归档

归档前为本批每个新包分配高于所有已用号且未占用的正整数构建号，标准三包按 ARM32、ARM64、通用的顺序递增。`STANDARD_BUILD_NUMBER` 表示下方单包示例的构建号；若随后交付兼容包，`LEGACY_BUILD_NUMBER` 须高于本批全部已分配号。不同新包不能复用同一构建号；1.1.2 的 34、35 已发布，不可用于新内容。

按[安装指南](INSTALL.zh-CN.md)配置 SDK，现有脚本读取 `local.properties` 的 `sdk.dir`：

以下命令只构建一个通用包；整批模式见后文的 `--batch` 和 `--universal-only`。单包和整批构建均不代替功能及设备验收。

```sh
python3 scripts/build_apk.py --build-number "$STANDARD_BUILD_NUMBER" --variant benchmark --summary "说明本次更新内容"
```

脚本按实际版本及传入构建号输出本地归档 APK、同名 `.apk.sha256`、归档 JSON 及优化映射文件，并在 JSON 的 `public_apk` 记录公开名；不会额外生成公开名副本或自动上传。当前兼容包增加 `--compatibility legacy14`；历史 API19 回归可用 `legacy19`。已发布的 1.1.2 本地归档为 `QuietTV_1.1.2-34_安卓5及以上_测试版.apk` 与 `QuietTV_1.1.2-35_安卓4.4_测试版.apk`，仅原字节重发可复用这两个编号。

现有参数包括 `--variant benchmark`、`--abi armeabi-v7a`、`--min-sdk 21`（默认）、`23`、`26`、`31`。`--compatibility legacy14` 固定 API14，历史 `legacy19` 固定 API19；标准档不接受 API14或19。酷开专用桌面及各中转助手暂按各自说明构建，尚未接入统一归档；交付前须完成对应命名及元数据核验，不将当前脚本描述为已支持所有类别。

后续交付必须满足：

- APK 放入本地不可变归档 `dist/apk/`，提供 `<完整APK文件名>.sha256` 及相同主文件名的 `.json`；优化包同时保留映射文件。
- 归档记录包含本地归档名、对应公开名、真实包名、版本号、构建号、兼容档位、最低/目标 API、构建类型、实际架构、签名指纹、源码提交、生产输入指纹、时间、字节数、SHA-256、变更摘要和验收范围。助手额外记录配套关系。
- 同名同字节且校验记录一致可复用；同名不同内容禁止覆盖，必须增加软件版本号和内部构建号。脚本也按真实版本、系统、实际架构和状态检查已登记 worktree 的历史归档，防止换目录或旧文件名绕过；同一本地归档的原字节及配套记录一致时可复用。无构建号文件名缺少有效 JSON 构建号元数据时停止归档，不从文件名推测或将用号归零。构建期间生产输入改变则停止归档。
- Gradle 的构建输出目录是可更新的中间产物；归档、密钥和恢复记录保留本地，不随源码发布。

整批构建前提交改动，固定源码提交；起始号高于所有已用号，命令如下（示例不预占号）：

```sh
python3 scripts/build_apk.py --batch --build-number "$FIRST_BUILD_NUMBER" --variant benchmark --summary "说明本次更新内容"
```

1.3.0 已移除应用内 ADB 原生依赖，标准版无原生库，因此本批使用两包模式：

```sh
python3 scripts/build_apk.py --batch --universal-only --build-number "$FIRST_BUILD_NUMBER" --variant benchmark --summary "说明本次更新内容"
```

当前两包模式依次构建标准通用包、安卓4.0至4.4包，分配两个递增构建号，并强制检查两个 APK 均无原生库。若重新引入原生库，改用原四包模式。113–116 已用于本地构建核对并保留记录，1.3.0 测试版两包已使用 117–118 发布；获批暂缓的真机验收项仍未完成，范围以归档材料为准。

当前四包模式依次为标准 ARM32、ARM64、通用及安卓4.0至4.4包分配构建号；除每包归档外生成 `QuietTV_<版本号>-<起始号>-<末尾号>_batch.json`，记录共同源码、签名、各包元数据及原生库 SHA-256。构建失败时保留已归档包及占用号，不自动清理、覆盖或复用。

主程序脚本核对实际包名、版本、最低及目标 API、签名、架构、生产输入指纹与不可覆盖归档，检查所有已登记 worktree 的归档构建号；不自动联网查询远程 Release 或扫描未登记目录，交付者仍须补齐这两项核对。助手配套归档和正式签名流程另行验证。

## 安装与发布边界

- 下载说明按系统档位及架构展示适配包，并提供通用备用入口，不以最大构建号作为所有电视的统一推荐。测试包和实验包不标为稳定正式版。
- 覆盖升级核对相同包名、匹配签名、严格递增的构建号，以及系统和架构要求。标准包与兼容包共用 `home.quiet.tv`，不能同时安装；跨档位切换额外核对功能和数据兼容。
- 酷开专用包是独立应用，不覆盖普通包、不自动迁移数据。签名不同的正式包交付前先制定迁移办法，不承诺直接无损覆盖，不自动卸载旧包或清除数据。
- 构建、归档、安装、已安装包哈希核对、遥控器及功能验收分别记录。源码提交、工作区状态和历史验证不能证明当前设备已安装或已通过验收。
- CI 检查源码，不安装设备或发布 APK；设备升级及发布按明确授权执行。

## GitHub Release 内容与确认

- 正文仅保留“新增功能”“问题修复”两个模块，分别只写相对上一发布版本的新增或改进、实际修复，不重复全部已有功能。不添加选包表、适用范围、兼容差异、技术说明、验证情况等其他内容。
- 详细构建、签名、兼容差异、升级限制和验证记录保留本地，发布正文不附加这些说明。
- 后续发布附件只上传安装包，不再上传独立的 `.apk.sha256`，也不要求用户配套下载校验文件。本地构建与不可变归档仍须生成、保留同名 SHA-256 文件和元数据；发布后核对 GitHub 提供的 APK 校验值及重新下载文件与本地归档一致。[GitHub 附件校验说明](https://github.blog/changelog/2025-06-03-releases-now-expose-digests-for-release-assets/)。GitHub 自动附带的源码下载链接仍会显示。
- 每次新建或更新 Release，必须先向用户提供本批完整正文预览，并逐项确认“新增功能”“问题修复”的具体文案，同时备齐标题、附件清单、版本、构建号、大小、SHA-256、源码基线、签名和验证结果。用户明确同意本批文案及材料可上传更新后，才允许创建发布标签、Release（含草稿）、上传附件、发布或修改线上正文；此前版本的确认、一般发布意向及本规则修改均不算本批确认。确认后文案或附件发生实质变更须重新送审，未获确认时继续完成本地准备。
- 历史 Release、附件、标签、链接和本地校验记录保持原样，不追溯删除已有校验附件。完整发布边界以 [AGENTS.md](../AGENTS.md#github-release-审核与发布) 为准。

## 1.1.2 发布与 GitHub 文件名例外

[Quiet TV 1.1.2 测试版](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.2) 已发布，同一 Release 集中标准档 34 与安卓 4.4 档 35。两档共用生产输入，源码基线见发布标签；均为 benchmark 优化测试签名包。

GitHub 清理中文附件名，本批经明确批准只对 GitHub 下载名采用以下映射；中文显示标签及本地原归档保持原样：

| 本地归档 | GitHub 下载 |
| --- | --- |
| `QuietTV_1.1.2-34_安卓5及以上_测试版.apk` | `QuietTV_1.1.2-34_Android5plus_Test.apk` |
| `QuietTV_1.1.2-35_安卓4.4_测试版.apk` | `QuietTV_1.1.2-35_Android4.4_Test.apk` |

APK 字节、版本与签名均未变化。该历史批次的实际英文名 `.apk.sha256` 与下载配套，另保留原中文文件名校验记录；本节只描述已发布历史附件，不追溯按新规则改名。后续公开命名、校验及上传范围按前文执行。

## 历史发布

下列文件名和标签保持原样，不按新规则追溯改名。历史构建及验收只适用于对应产物：

[b30 安卓 4.4 兼容优化测试包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b30)为 `quiet-tv_1.1.0_b30_benchmark_legacy19_api19.apk`，2,412,105 字节（约 2.30 MiB），最低 API19，无原生库，使用与 b26 相同的测试证书。包内版本为 1.1.0 / 30，发布标签为 `v1.1.0-b30`。基于公开 main 新构建并保留不可变归档，生产源码提交为 `a2c35cb`；历史 b28 归档未覆盖。构建与验收边界见[兼容说明](COMPATIBILITY.md)。

[b26 标准版通用测试包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b26)继续保留：最低 API21、6,819,366 字节，包含四种 CPU 架构。历史 [b24 / API23 通用包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b24) 与 [b23 / API31 / ARM32 包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0) 均保留。b30 与 b26 是不同兼容档位，不代表相同功能范围。


## English

Published 1.1.2 provides standard build 34 (minSdk21) and legacy19 build 35 (minSdk19), both targeting API36. GitHub uses the approved English filenames listed in the installation guide; local Chinese archives are unchanged. The matching checksum must name the actual download. Historical b30 legacy19 and b26 standard downloads keep their original names and tags. At b30 publication, native emulator and physical-TV acceptance were still pending; historical results for other builds do not validate this APK. See [compatibility](COMPATIBILITY.md) for the recorded scope.

Future main-app downloads and immutable archives use `QuietTV_<version>_<system>[_<single ABI>]_<status>.apk`, without a build number. Local names use Chinese labels; `public_apk` records the English download name. The script does not upload APKs. Version and internal build must match the actual APK. Build numbers are positive integers without prefixes or leading zeros and increase across all main-app delivery variants. A different payload for the same version/system/ABI/status requires a higher software version as well as a higher internal build number. Historical cumulative 1.3.0 builds keep their original names; that exception does not apply to future deliveries. Never replace different bytes under the same public filename. Re-sending identical archived bytes does not allocate a new number. The helper uses `QuietTV助手`, its own version/build sequence, and an explicit target launcher; its filename retains the actual build number.

System labels include `安卓4.0-4.4` (legacy14, API14, local development with device checks deferred), historical `安卓4.4` (legacy19, API19), `安卓5.0及以上` (standard, API21), and `酷开安卓8` (experimental vendor package, API26). Other minimum APIs require matching labels. Omit the architecture marker for multi-ABI and native-library-free APKs. Use ARM32, ARM64, x86 or x86_64 only for actual single-ABI builds. Universal architecture does not imply compatibility with every OS or TV. Status is `测试版` (test), `实验版` (experimental), or `正式版` (formally signed release with agreed acceptance completed). Public labels use Android4.0-4.4, Android4.4, Android5.0AndAbove (or the actual higher minimum), and Test/Experimental/Release. Do not add build numbers, plus or to to new public names; preserve historical assets and URLs. Verify names and bytes before upload.

When native libraries require ABI-specific packages, standard deliveries provide three standalone APKs: ARM32, ARM64 and a universal fallback containing all four native-library ABIs. Each device installs one APK. All three share the source commit, production inputs, software version, feature configuration and signing certificate; assign increasing build numbers in ARM32, ARM64, universal order. Keep one legacy14 APK while it has no native-library or actual ABI differences; determine the Coocaa package set per delivery. Select by the ABIs supported by the operating system, and use the universal fallback when uncertain. In-place switches still require a higher build number; do not uninstall, clear data or force a downgrade to switch to a lower-numbered package.

Current development profiles have no native libraries: use `--batch --universal-only` for one standard universal APK and one legacy14 APK. Published 1.3.0 builds 117/118 retain their original standard/legacy19 profiles; the new legacy14 package is not published. This mode rejects any native libraries. The script also supports a fixed-source four-APK batch with `--batch`, including complete native-library checks and batch receipts. Without `--batch`, each invocation builds one standalone APK. Functional, upgrade-data and physical-device acceptance remain separate requirements.

**The main-app build script implements the naming above, matching checksums, actual APK metadata validation and build-number checks across registered local worktree archives.** Remote releases, unregistered archives, helpers and formal-release acceptance require separate checks. Preserve historical files, checksums, tags and links. Archives require matching checksum and metadata records, including the actual package, SDKs, ABI, signing identity, source and acceptance scope. Same-name/different-byte replacement is forbidden.

Recommend downloads per system profile. In-place upgrades require matching package/signature, a strictly higher delivery build number and compatible system/ABI. Check data compatibility when switching profiles; different package IDs or signing identities need an explicit migration plan. CI, source checks and archive metadata do not establish device installation or acceptance. Device operations and publication require authorization.

Future GitHub Release bodies use only “新增功能” (new or improved features since the previous release) and “问题修复” (fixes in this release). Keep download mappings, build metadata, compatibility and validation records in the local review materials, outside the public Release body. Upload APKs only, retaining checksum files and metadata locally; compare GitHub digests and re-downloaded APKs with the archive after publication. Preserve historical assets. Before every Release creation or update, show the full body and obtain the user's explicit approval of that batch's feature and fix wording and release materials. Until approval, prepare locally only; do not create tags or Releases (including drafts), upload assets, publish or edit the live body. Prior approvals or this policy change do not approve a new batch; substantive wording or asset changes require renewed approval.
