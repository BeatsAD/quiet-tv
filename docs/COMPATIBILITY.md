# 兼容范围 / Compatibility

[中文首页](../README.md) · [English home](../README.en.md) · [安装指南](INSTALL.zh-CN.md)

## 样机验证记录

整理日期：2026-10-29。下表分别记录维护者历史验证、社区公开反馈与用户提交的本地报告；本次仅核对资料，没有新增电视操作或真机验收。结论绑定所列版本与固件，不外推到整个品牌或其他安装包。缺失项保留“未验证”，不以源码、模拟器或上游项目的结果替代。

| 样机与固件 | Quiet TV 版本 | 已有结果与来源 | 失败或待验证 |
| --- | --- | --- | --- |
| 酷开／创维 55G20（8R40_G20），Android 8 / API26，`OPR6.170623.012`，增量 `019.012.270` | 1.2.9；反馈未给构建号 | 社区 [#35 最新回复](https://github.com/BeatsAD/quiet-tv/issues/35#issuecomment-6440081087)确认配置后，实体 HOME 与重启均进入 Quiet TV。型号、固件沿用同一 Issue 的原始环境，回复未重新回读。 | 补充构建号与实际配置；断电冷启动、待机唤醒、HDMI／播放及原桌面恢复未反馈。该反馈仅确认这两个场景，不代表所有酷开通过。 |
| 海信 VIDAA_TV / Hi3751，Android 4.4.2 / API19；固件未提供 | 问题包 1.2.1（54） | 社区 [#68](https://github.com/BeatsAD/quiet-tv/issues/68)报告天气及每日壁纸 HTTPS 握手失败；[维护者回复](https://github.com/BeatsAD/quiet-tv/issues/68#issuecomment-6430967402)说明后续版本已有 TLS／证书兼容修复发布。 | 尚无该样机升级后的网络复测，不能标为真机修复通过。HOME、重启、待机等未验证。 |
| 小米盒子 2 增强版，Android 4.4.2；固件未提供 | 1.2.4；构建号未提供 | 社区 [#81](https://github.com/BeatsAD/quiet-tv/issues/81)反馈安装成功、正常使用；[后续回复](https://github.com/BeatsAD/quiet-tv/issues/81#issuecomment-6430967163)说明 1.2.6 起提供 Dock 背景开关。 | 未提供各项功能、实体 HOME、电源场景、后续升级及 Dock 开关的复测结果。 |
| Sony BRAVIA VH1，Android 12 / API31 | 历史 1.2.4（64） | 维护者历史核对：从 1.2.4（63）保留数据覆盖安装，已安装 APK 哈希与公开包一致，原 HOME 和数据目录保持，采集可见页面；见[历史记录](#历史发布记录--historical-releases)及[截图来源](assets/README.md#124-索尼真机截图--sony-captures)。 | 页面与部分配置核对不代表全部数据和完整功能；实体 HOME、冷启动、重启、待机与真实切源未全面验收，不作为 1.3.0 验收结果。 |
| 创维 8H73 E6100 / Hi3751V500，Android 4.4.2 / API19，`022.012.160` | 1.3.0（131），`trial` 试用 | 用户提交的验收报告记录安装、启动与前台运行；实体 HOME 仍回原桌面，系统桌面选择器调用失败。原报告仅保留本地，公开此脱敏摘要。 | 桌面接管未完成；重启、冷启动、待机未执行。API19 无标准守护备用路径，不将试用成功写为接管成功，也不据单次报告断言所有无 root 方案均不可行。 |
| 创维 55H6（系统型号 9S63_H6），Android 8 / API26，`OPR5.170623.014`，增量 `020.012.180` | 1.3.0（130） | 用户反馈开机先显示信号源、信号源键使用异常、菜单键无响应；所附日志的 HOME 解析指向 Quiet TV。日志仅保留本地，公开此脱敏摘要。 | 修复与该固件复测待完成。HOME 解析不能证明按键与实际切源通过；需分别检查输入画面、声音、切源后是否被守护拉回及菜单键动作。 |

### 验收口径与反馈

- 安装／升级：记录实际包名、版本与构建号、来源和签名／哈希核对，以及所测设置与数据保留范围。
- 桌面：分别操作手动启动、实体 HOME、整机重启、断电冷启动与待机唤醒；记录实际画面，不能只读 HOME 候选或前台进程。
- 信号源与按键：分别验证实际输入画面与声音、切源后停留、HOME 返回和菜单键动作。厂商可能直接处理按键，应用未收到键码不能单独证明按键失效。
- 恢复：保留原桌面，记录暂停接管与恢复结果。ADB 当前连接、重启后连接和待机后连接分别记录。

电脑安装流程默认以 `takeover` 尝试配置 HOME；显式 `trial` 仅试用，`update` 仅更新。当前 API14–19 不提供标准兼容守护，标准 HOME 失败时不能报告接管完成；`trial` 改为 `takeover` 不保证解决厂商限制。见[安装与验收流程](agent/install.md)及[验收说明](agent/acceptance.md)。

公开反馈使用[机型适配模板](https://github.com/BeatsAD/quiet-tv/issues/new?template=device_adaptation.yml)，写明固件、APK、配置方式、复现步骤和每个场景的通过／失败／未验证；仅附必要脱敏片段，不上传地址、序列号、密钥或完整报告。

## Android 4.0–4.4 统一兼容包（1.3.0测试版131）

1.3.0累加测试版提供标准130与兼容131，下载以[实时安装索引](https://beatsad.github.io/quiet-tv/latest.json)为准。`legacy14` 档位最低 API14，以一个无原生库的通用 APK 覆盖 Android 4.0–4.4。公共功能与 `standard` 共用代码；标准档仍为 API21，保留原来的 Activity、RecyclerView、ExifInterface、Media3 版本。`legacy19` 参数仅保留用于历史 API19 构建与升级回归，默认批次只生成一个 `legacy14` 旧系统包。

| 功能 | Android 4.0–4.4 / legacy14 | Android 5+ / standard |
|---|---|---|
| 首页、全部应用、排序、封面、桌面设置、问候语、儿童模式、诊断日志 | 共用公共实现，补齐旧系统界面与存储接口 | 保留原有功能和依赖 |
| 图片、每日壁纸、手机上传、离线许可 | 保留；低版本二维码使用同内容 PNG，天气 API14/15 使用连接级 BCJSSE | 保留原有路径 |
| 视频壁纸上传、预览、循环、旋转 | 系统 MediaPlayer；API14/15 使用有界 MP4/WebM 容器检查和系统播放器预检，API16+ 使用系统提取器 | 原 Media3 播放路径 |
| 默认 HOME 与海信特殊按键 | 保留标准 HOME 声明；海信补偿仍只匹配原有 API19 固件 | 保留原有守护及适配范围 |
| 标准守护、HOME 代理、标准 TV Input 列表 | 与原 4.4 档位一致，不提供平台缺少的标准接口 | 保留原有范围 |

API14–18 仅为更新安装器交接请求写外部缓存权限，声明以 maxSdkVersion18 限定，标准包不增加该权限。API14/15/16 通过 PackageManager 查询实际安装状态，避免依赖 API17 才定义的 FLAG_INSTALLED。旧系统原子文件迁移保留文件名、JSON 格式和 `.bak` 恢复能力。API14/15 的视频检查不伪造系统没有提供的帧率；可从容器确定时检查帧率，无法确定时记录未知，并由实际播放器确认是否可准备与解码。解码分辨率、格式、后台恢复及物理 HOME 仍以具体固件为准。

更新清单方面，新包支持 `legacy14` 和历史 `legacy19` 之间按最低 SDK、签名及版本选择升级；标准档不会选到旧系统包。`legacy14` 优先读取 `updates-v2.json`，未部署时在原总超时内回退读取 `updates.json`。现有标准版的清单地址和来源顺序不变。发布 API14 包时不得将新档位写进旧 `updates.json`，避免旧客户端整份解析失败；本批发布同步revision9双清单及安装索引；已安装1.3.0不会提示同版本更新，旧legacy19客户端不能自动选择legacy14，需手动下载匹配新包覆盖安装。

验证结果及限制见本次本地 `output/android4-tests/兼容验证记录.md`（不作为公开构建输入）。API14 Lint 与依赖声明只能证明静态兼容，较新模拟器运行兼容包不能证明真实 4.x 系统或厂商固件通过。

<a id="当前源码中的本地修复尚未发布"></a>

## 当前配置方式（1.3.0累加测试版130/131）

电脑 AI 负责首次安装、规则匹配、端口配置、守护授权及恢复；电视保留日常功能与“应用管理 → 更多 → 帮助与恢复”。当前开发源码已恢复应用列表的本机 TCP ADB 客户端，用于按规则与固件能力禁用、重新启用、卸载及恢复应用，不提供无线配对界面；电脑连接不等于应用已获授权。已发布117/118包尚无这些日常管理按钮。电脑断开后仍由电视运行已配置的守护和 ADB 设置保持；安卓4.0–4.4不提供标准守护或本轮 ADB 保持。规则源为 `scripts/device-presets.json`，APK 内旧规则仅保留升级兼容默认值；新固件必须核对实际能力，未知运行协议仍需修改 APK。详见[规则与恢复](DEVICE_ADAPTATION.zh-CN.md)。

本轮覆盖升级修复保留环境未变时的接管、暂停及 ADB 选择，并重新握手已有自动 HOME 代理；旧实体场景验收清为待验证。源码与模拟器回归不等于厂商真机验收，尤其不能用 `adb_enabled=1` 证明网络 ADB 重启后可连接。公开安装包仍按[实时索引](https://beatsad.github.io/quiet-tv/latest.json)选择，缺少维护接口的旧包不能套用新能力。

下列旧版本记录保留各自证据范围，不作为当前入口或当前下载推荐。

## 历史修复与证据

海信 `VIDAA_TV / Hi3751 / hi3751 / API19` 的独立 HOME 按键补偿继续保留。当前源码由电脑 AI 引导用户在系统无障碍设置授权，不再在桌面显示技术开关；历史版本曾提供“桌面设置 → 桌面显示 → 主页键修复”。短按 HOME 请求 Quiet TV 首屏，长按或双击使用系统最近任务动作。服务不读取屏幕内容、不依赖标准守护、不禁用原厂包；绑定、实体按键、底部面板抑制及重启保持仍须逐项验收。

桌面兼容开发补齐 VIDAA `com.jamdeo.tv.vod` 原主页在新安卓上的获准用量检测、酷开 AI 与松下系无 HOME 声明系统组件发现、TCL 命名自启动权限读回及 Direct Boot 等待解锁入口。无障碍授权、服务连接与实体场景验证分别显示和记录。适配以实际组件和能力为依据，未增加整品牌系统包操作规则；源码检查通过不代表目标电视已验收。电脑诊断技能及后续日志案例登记独立交付，详见[设置说明](SETUP.md)与[排查说明](AGENT_DIAGNOSE.zh-CN.md)。

当前本地源码修复动态壁纸播放、封面与比例处理：安卓4.4不再通过取消音轨实现静音；播放器输出尺寸、旋转与像素比例参与等比裁剪；API21–26的90°／270°视频使用纹理层统一旋转；缺失封面可在后台补生成。更新查询比较两个来源的已验签清单，保留较高revision，并区分无更新与安装包不匹配。当前开发源码的六个管理标签共用窗口，应用列表按需连接本机 TCP ADB，退出时断开；内容与排序继续保存。儿童管理在内容区主动解锁，切换标签与按下进入内容均不弹出验证窗口；离开后清空输入、取消未完成验证并重新锁定，重新解锁保留应用选择与列表位置。

应用内安装交接按系统版本适配：Android 4.0～6（API14～23）将已校验 APK 复制到应用专属外部缓存，复核 SHA-256 后通过 `file://` 打开系统安装器；API24+ 继续通过 `FileProvider` 的 `content://` 临时读取授权交接。两条路径均保留系统安装确认，仅 API14–18 需要外部缓存写入权限，API19+ 不增加存储权限。外部缓存不可用时保留原下载供重试；安装入口无法打开时说明失败原因，并提供电脑更新指引。旧客户端需先通过可用的手动覆盖安装方式获得修复，真机安装与升级数据保留仍需验证。

| 安装包档位 | 运行系统 | 安装交接差异 |
| --- | --- | --- |
| 安卓4.0–4.4 / legacy14 | API14–19 | 已校验 APK 复制至应用专属外部缓存，复核后使用 `file://`；API14–18 请求外部缓存写入权限。历史 legacy19 仍走同一路径。 |
| 安卓5及以上 / standard | API21～23 | 与4.x兼容包共用文件交接逻辑。 |
| 安卓5及以上 / standard | API24+ | 使用私有更新文件及 `FileProvider` 临时读取授权。 |

这些修复尚未发布。旧版客户端不会因本地源码变化自动获得修复，需在后续获批版本交付后覆盖更新。模拟器验证不代替厂商电视、用户原始视频及最终交付APK验收。

<a id="当前测试版--current-test-release"></a>

## 历史发布记录 / Historical releases

[1.2.5 测试版](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.2.5)提供安卓5+ ARM32／64／通用包（构建68–70）及安卓4.4兼容包（71），详见[选包与安装](INSTALL.zh-CN.md#下载安装与试用)。四包同源同版本同签名；旧系统不提供无线配对、桌面守护或HOME代理。

历史1.2.4验证曾在 Sony BRAVIA VH1 / Android 12 上保留数据从1.2.4／63更新到公开ARM32包64，实际安装APK哈希一致、原HOME和应用数据目录保持，并采集[当时界面](assets/README.md#124-索尼真机截图--sony-captures)。仅核对现有页面与可见配置；全部数据、完整功能、实体HOME、重启和待机唤醒未作全面验收。安卓4.4及其他厂商仍需各自真机验收。

Version 1.2.5 has standard ARM32/ARM64/universal and Android 4.4 profiles. Historical Sony build 64 installation and page captures do not establish complete feature, data-retention, physical HOME, reboot/wake or other-device acceptance. The following sections retain historical development and validation records; older artifacts are not current download recommendations.

<a id="源码与下载--source-and-download"></a>

## 历史包与兼容设计 / Historical packages and compatibility design

以下 TCP／配对及应用内配置描述对应迁移前版本；当前源码由电脑承担连接与配置，见本文首节。

| 内容 / Build | 范围 / Scope |
| --- | --- |
| 历史 [1.1.2 标准测试包，34](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.2) | Android 5.0 / API21 起，target36；9,370,356 字节，ARM32、ARM64、x86、x86_64。 / Standard universal test APK. |
| 历史 [1.1.2 安卓4.4测试包，35](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.2) | API19 起，target36；4,900,169 字节，无原生库，传统 TCP ADB，信号源走系统设置／遥控器。 / Legacy19, no pairing or standard input list. |
| 历史 [b30](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b30) / [b26](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b26) | 原包、标签、校验记录及各自历史验收范围保留，不作为当前默认下载。 / Original historical artifacts and evidence retained. |
| 历史 [b24 通用包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b24) | Android 6.0 / API23 起；原下载保留。 / Android 6.0+; original download retained. |
| 历史 [b23 ARM32 测试包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0) | Android 12 / API31 起，需支持 armeabi-v7a；仅支持 64 位应用的设备不可用。 / Requires Android 12 and ARM32 app support. |
| Android 4.0～4.3、Tizen、webOS 等非 Android 系统 | 不支持 / Unsupported |

Android 4.4 的 `legacy19` 档位采用 Activity 1.9.3、RecyclerView 1.3.2、旧版主题及构建工具生成的位图图标。保留核心桌面、Dock、壁纸、网络、设置、关于、离线许可证和传统 TCP ADB；不包含 Conscrypt/SPAKE2 原生库，不提供六位码配对。信号源回退到系统设置与遥控器信号源键，厂商直切未适配。未裁剪 Debug 包启用旧版 MultiDex，最终优化包需单独验证。

The `legacy19` profile targets Android 4.4 with legacy UI resources and TCP ADB only. It contains no Conscrypt/SPAKE2 native libraries and offers no pairing control. Inputs fall back to system settings; direct vendor input switching and physical TV acceptance remain unverified.

Android 5 使用旧版网络状态接口和安装时权限；频道权限或 TV Input 服务受限时给出提示，ADB 仅提供设备实际支持的传统 TCP 操作，六位码无线配对不可用。离线许可证页使用旧版页面完成回调。

源码兼容处理包括旧系统网络广播与字体回退、系统 HOME 入口检测、旧遥控器及新版系统返回导航，以及按本机实际命令能力开放的应用管理。Android 版本符合最低要求不等于厂商固件全部功能已通过验收。

桌面自动配置优先系统 HOME，确认不能切换时才回退原桌面中转；取消、断连和结果未知不触发回退。自动配置保留原桌面，危险固件及未验收原主页受执行层保护，暂停中转不解除保护。安卓5及以上创维／酷开可在明确配置时启用开机跳板，需本机有效原主页且无其他动作处理者或独立助手冲突；不再以精确固件规则作为试用前提，也不把候选检测标为真机通过。其他品牌不套用创维动作。

酷开主程序包名 `com.coocaa.quiettv` 可选择标准API21+或独立legacy19/API19档，既有API26构建方式保留。API19仍不支持桌面守护、跳板或HOME代理，独立酷开助手的适用固件不变。构建参数、升级边界及各档位能力见[设置说明](SETUP.md)。这不代表旧酷开安卓8安装包可以安装到低版本系统，也不表示黑屏／系统焦点冻结已解决。

构建参数 `quietHomeMode` 默认为 `standard`，维持 HOME 声明和原构建命令；API21+ 可显式选择 `relay`，最终 Manifest 不声明 HOME，保留 LAUNCHER／LEANBACK 入口并共用原桌面中转。安卓4.4拒绝该组合。本地编译与合并清单不代表 TCL p331 可以安装，也不证明实体 HOME、开机或待机已通过；本轮没有电视安装或兼容包归档交付。详见[桌面配置](SETUP.md#安装与维护)。

Source includes legacy networking/font fallbacks, detected HOME settings, Back handling and capability-based package operations. Meeting the minimum Android version does not establish vendor-firmware compatibility.

## 已验证与待验收 / Evidence boundaries

### 旧系统天气证书兼容（开发源码） / Legacy weather certificates

针对 Android 5.1 小米盒子天气刷新时的 `SSLHandshakeException`／`CertificateException`，天气客户端在 API19–25 上将随包的 ISRG Root X1 根证书加入当前连接的信任集合。和风及 UAPI 共用该处理，保留原有系统根证书、证书链、有效期及域名校验，不写入系统 CA 库，也不改变其他网络功能的 TLS 设置。根证书来源为 [ISRG Root X1](https://letsencrypt.org/certificates/)，随包文件见[公开证书](../app/src/main/assets/certificates/isrg-root-x1.pem)。

| 档位 / Profile | 共同功能及差异 / Behavior | 本轮验证 / Validation |
| --- | --- | --- |
| 标准 API21+ | API21–25 的天气连接补充根证书；API26+ 使用系统默认信任 | 编译与 Lint 通过（0 错误）；21 项天气及 TLS 单元测试通过。 |
| 安卓4.4 / legacy19 | 相同根证书补充，并沿用 TLS 1.2 设置 | 编译与 Lint 通过（0 错误）；相同 21 项测试通过；API19 运行待验收。 |
| 酷开 API26+ 独立包名 | 共用天气代码，继续使用系统默认信任 | 编译与 Lint 通过（0 错误）；酷开固件运行待验收。 |

TLS 测试通过离线握手复现缺根证书时失败、补充后成功，并检查系统原有证书信任、未知 CA、过期叶证书和错误域名。测试运行于电脑 JVM，不代表 Android 固件已验收。现有 API22 ARM64 镜像因本机模拟器程序不支持而未能启动；本次未安装实体电视、未验证覆盖升级数据保留，也未归档或发布 APK。当前开发修复不能视作已发布的 1.2.1 安装包已包含。

### 桌面布局与实时预览开发源码 / Desktop layouts and live preview

顶部标签替代原侧栏，仅“桌面显示”提供固定首页预览；天气设置和桌面守护使用完整内容区。经典布局、艺术画廊居中及问候语、时间、日期、天气、三个快捷按钮的独立显隐共用同一套原生代码。日期首次继承旧时间日期开关状态；已有问候语、天气、快捷按钮及应用封面配置继续复用。

| 档位 / Profile | 共用功能及差异 / Features | 本轮验证 / Validation |
| --- | --- | --- |
| 标准 API21+ | 三个标签、两种布局、实时预览 | 构建与 Lint 通过；API22 和 API31 模拟回归通过，API31 的 720p／1080p 验证覆盖256种组合、遥控焦点、数据迁移、问候语保存取消、页面重建、真实壁纸及常用应用封面。 |
| 安卓4.4 / legacy19 | 同一布局与预览；沿用桌面守护禁用策略，仅显示前两个标签 | 构建与 Lint 通过；API22 上运行兼容档通过相同256种组合与核心交互。本轮无可运行 API19 镜像，Android 4.4实际运行仍待验收。 |
| 酷开 API26 独立包名 | 复用标准档的布局代码 | 本轮未单独构建或进行酷开固件验收。 |

预览读取本地壁纸、常用应用封面和有效天气缓存，动态壁纸使用本地封面，Dock复用当前磨砂样式并按应用数量居中伸缩，不启动第二套天气请求或壁纸播放流程。以上为本地开发验证，尚未发布，也未安装实体电视；厂商遥控、HOME、待机唤醒、升级数据保留及低内存表现需目标设备验收。

Both layouts share the native Home header with the preview. Only Desktop shows the preview; Weather and Guard use the full content area. Standard emulator checks passed on API22 and API31; API31 covers 720p/1080p and 256 visibility combinations. The legacy19 build passed the same functional checks on API22, which does not establish Android 4.4 runtime acceptance. No physical TV or separate Coocaa package acceptance was performed, and this work is not released.

### 动态壁纸开发源码 / Video wallpaper source

默认关闭，图片和 MP4／MOV／MKV／WebM 共用上传入口。单视频 200 MB、最高 60 FPS；总容量和剩余空间检查仍保留。源码构建不代表已发布的安装包包含此功能。

| 能力 | Android 4.4 | Android 5+ |
| --- | --- | --- |
| 上传、预览、静音循环、关闭恢复、退出释放 | 共用流程 | 共用流程 |
| 播放路径 | 系统 MediaPlayer + TextureView，无 Media3 | Media3 1.8.1 + SurfaceView；API21–26的90°／270°视频采用TextureView手动旋转。12 MiB缓冲目标，禁用音频轨，可用解码器回退 |
| 输入上限 | SDR、8bit、1080p、60 FPS 内，依系统解码器 | SDR、8bit、4K、60 FPS 内，依实际编码／分辨率／帧率组合 |
| 4K 封面 | 本批不开放 4K | API27+缩放取帧；API21–26尝试系统取帧后缩小至最长640像素，失败保留占位图 |
| 厂商硬解、实际输出、长期性能、屏保与待机 | 待对应设备验收 | 待对应设备验收 |

不接收 HDR／10bit。手机本机预览与电视首帧验证分别判断；播放错误区分读取、格式、解码、超时及输出失败。上述新范围尚待厂商真机验收；SurfaceView 保留部分电视独立于 UI 分辨率的视频输出路径；不能仅凭面板或素材标签确认原生 4K。4K 不可用时需另传低规格文件，不自动在电视转码。详见[使用指南](USAGE.zh-CN.md)。

### 每日壁纸开发源码 / Daily wallpaper source

每日壁纸共用同一套 Java 实现，入口位于原有壁纸设置，选中确认即启用。固定使用必应中国，已移除每日设置按钮、弹窗及搏天请求路径；本地轮播与每日模式互斥，离线保留已缓存图片。旧搏天背景在必应缓存成功保存前保留。

| 档位 / Profile | 共用功能 / Shared features | 验证边界 / Evidence |
| --- | --- | --- |
| 安卓5及以上 / Standard API21+ | 必应每日更新、不可用图片过滤、固定缓存、旧缓存过渡 | 历史 API21 与 API31 模拟器验证包括双来源 HTTPS 和原生流程；该历史证据不代替本次单来源界面及升级验证。 |
| 安卓4.4 / legacy19 | 应用内纯 Java TLS 1.2 与必应根证书补充，无需 root 或手动安装系统证书 | 编译、32 项相关测试、Lint 与 R8 通过；API19／x86 普通应用 UID 的真实必应资料、图片下载及尺寸读取通过，ARM32 厂商固件待验收。 |
| 酷开安卓8 / Coocaa API26 | 同一功能代码，独立包名 | 历史专用配置构建通过；本次未单独构建，酷开固件真机未测。 |

旧版本曾为搏天接口补充 ISRG 根证书；当前已移除搏天网络路径，该兼容逻辑不再加载。以上为本地开发验证，不代表已发布安装包，也不保证所有地区、运营商或电视固件的网络可达；实体遥控器、待机唤醒和跨日长期使用仍需真机验收。

2026-10-25 安卓4.4 HTTPS 兼容：旧引擎即使启用 TLS 1.2，仍可能因加密套件及证书兼容性无法连接必应。当前资料、图片及 HTTPS 重定向共用连接级 BCJSSE／Bouncy Castle 1.86 TLS 1.2 实现，在系统信任根基础上补充 DigiCert Global Root G2；保留证书链、有效期和默认域名校验，不写系统 CA 库，不改变全局安全提供方。天气配置和 Android 5+ 的壁纸连接保持原样，不通过降到 TLS 1.1 解决必应连接。

API19／x86 对照验证中，原引擎连接必应被重置；保留 Java 版本兼容边界后的 R8 实现以普通 Dalvik 校验模式取得 HTTP 200。Debug 应用在普通 UID 10052 下完成真实资料请求、331,717 字节图片下载和 1920×1080 JPEG 尺寸读取。标准与安卓4.4各通过 32 项相关测试及 Lint；模拟器 DNS 配置单独修正，没有更改系统证书。上述网络验证不代表完整像素解码、桌面全流程、跨日更新、覆盖升级或 ARM32 厂商真机验收；仅生成本地验证包，未归档交付或发布。

2026-10-24 单来源调整：移除每日设置按钮、弹窗及搏天请求路径，固定必应自动更新。旧搏天背景在必应成功提交前继续使用。标准与安卓4.4开发构建、各 25 项定向单元测试及 Lint（0 错误）通过，覆盖筛选、同图复用、旧缓存过渡及配置保留。本机 emulator 为 x86_64 程序，现有 ARM64 API31 测试 AVD 启动受阻，因此本次界面、遥控焦点及实际覆盖升级未运行验收；未安装电视或发布。

此前的 2026-10-24 必应筛选与存储调整：从最近 8 张资料中跳过未开放下载的图片，只下载最近可用的一张；相同或更新的缓存直接复用，不回退旧图。继续使用每来源两个固定图片位，并在请求结束时释放下载原始临时文件。标准与安卓4.4开发构建、各 18 项定向单元测试及 Lint（0 错误）通过；真实接口资料验证已跳过不可用首图，选择最近可用图。此次未安装模拟器或电视，未验证真机跨日、断网及磁盘占用，未发布。

<a id="当前-112--current-release"></a>

### 历史 1.1.2 / Historical release

- 两档均通过优化构建、各 49 项单元测试、Lint（0 错误；标准 61 条警告，兼容 46 条），以及实际 APK 元数据、签名和 ZIP 对齐检查。归档工具 9 项检查通过；GitHub 回下载 SHA-256 与归档一致。
- 2026-10-22 在一台 Sony / Android 12 电视核对 1.1.2／34 的实际版本与已安装 APK SHA-256，匹配公开标准包；直接截取首页、五列应用、壁纸及管理模式、桌面设置、首页排序。截图保留原始 1920×1080 像素，不改配置、不执行删除或保存排序；上传会话打开后已关闭，原始连接信息不公开。详见[素材来源](assets/README.md)。
- 以上页面展示不替代完整功能、覆盖升级数据保留、真实遥控器 HOME、重启、冷启动、待机唤醒、切源画面和声音的验收。安卓4.4本批真实电视验收仍待完成。发布时尚未完成的验收范围保留在 [Release](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.2)。

Both optimized APKs passed 49 unit tests each, Lint with zero errors (warnings remain), metadata, signature and alignment checks; GitHub download hashes match the immutable archives. Sony / Android 12 captures verify the installed standard 1.1.2 / 34 hash and the displayed pages only. No settings, wallpapers or ordering were changed. Final-APK feature acceptance, in-place upgrade retention, physical Home, reboot, standby and actual input switching remain incomplete; Android 4.4 physical-TV acceptance is pending.

### 历史开发与发布证据 / Historical evidence

- 历史应用内配置的桌面守护采用自动策略，完成首次授权后自动运行；当前源码改由电脑配置既有运行机制。标准档按 HOME 身份与系统能力识别，普通原主页事件快速接管，开机/唤醒稳定期复核，通知监听提供有限恢复。安卓4.4档禁用相关组件。守护不改真实 HOME、不禁用原桌面、不读取屏幕或通知正文；首次兼容配置可在 HOME 仍属原桌面时完成，但不解锁系统包操作。通知绑定、后台启动和厂商实际效果仍有系统限制；目标固件的重启、冷启动、待机及 HDMI/播放均需单独验收，详见[桌面配置](SETUP.md#桌面守护实验)。旧独立助手保留并互斥。

- b31 的小米主页中转助手独立构建，限定 MiTV4-ANSM0 / API23，默认暂停，需手动选择本机原主页并开启无障碍。主程序与助手均未安装目标电视。构建和模拟检查不代表实体 HOME、原厂设置、开机、待机或窗口事件已适配，流程与限制见[助手说明](../xiaomi-home-helper/README.md)。

- 公开 b30：基于合入安卓 4.4 与信号源回退的公开 main 构建；优化构建、19 项单元测试、Lint（0 错误、39 条警告）、API19 元数据、v1/v2 签名与包内容校验通过。与 b26 使用相同测试证书。b30 未完成原生模拟器功能验收，未安装真实电视；下列 b28 记录仅为历史开发证据，不能视为 b30 已通过相同功能验收。

Published b30 passed the optimized build, 19 unit tests, Lint (0 errors, 39 warnings), API19 metadata, v1/v2 signatures and package-content checks. Its test signer matches b26. Native emulator acceptance was not completed for b30 and no physical TV has received it. The b28 evidence below is historical and does not validate the new APK.

- 历史 b28 兼容开发：两个构建档位通过构建和完整 Lint（0 错误），各通过 19 项本地单元测试；仅跟踪文件的源码副本不依赖私有测试模块即可构建。API19 / Android 4.4.2 的 32 位 x86 软件模拟环境通过基础桌面、应用枚举、遥控返回、设置、管理入口、关于／离线许可证、Dock 编辑与排序、壁纸选择和重开保存检查。本轮 Debug 功能回归与最终 b28 优化包验收分别执行；最终优化包通过黑盒页面冒烟、覆盖安装数据保留和已安装 APK 哈希核对。覆盖安装保留了首次安装时间及 Dock、壁纸、ADB 密钥、设置记录的原文件哈希。
- 标准版 b28 在 API21 通过基础兼容回归，在 API33 通过真实 TLS 配对启动、取消和关闭控制器后拒绝新配对的检查。兼容版传统 ADB 经环回转发到同一模拟器，完成本机身份核对并拒绝 root 守护进程；这不代表 shell 身份的启禁操作或电视固件网络调试已通过。
- b28 尚未发布，未安装实体电视；Android 4.4 的 ARM32 真机、Wi-Fi／有线切换、HDMI 直切、真实 HOME、实体 Home 键、重启、唤醒与低内存性能仍待验收。x86 软件模拟结果不能替代这些项目。

For the b28 development cycle, both profiles passed builds, full Lint (zero errors) and 19 local unit tests each. API19 / Android 4.4.2 checks use a 32-bit x86 software emulator. Debug UI regressions and final optimized-APK smoke, in-place state preservation and installed-file hash verification were performed separately. The standard b28 profile also passed API21 compatibility and API33 TLS pairing cancellation, including rejection after controller closure. Legacy ADB uses a loopback relay to the same root emulator; shell package operations and actual firmware network debugging remain unverified. b28 is unpublished and has not been installed on a physical TV.

- 公开 b26：Debug 构建与 19 项单元测试通过，Lint 为 0 错误、28 条警告；API21、22、23、31 模拟器通过基础页面、应用枚举、返回导航及离线许可证回归。API21 / 22 另外通过代码与资源裁剪后的通用包冒烟，覆盖 HOME、关于、Dock、离线管理入口与原生库加载。API31 还通过 shell 身份下的测试应用禁用／启用、日志与原状态恢复回归，以及优化包实际 ADB 连接冒烟。API21 / 22 / 31 已安装优化包的 SHA-256 与候选包一致。b26 已作为预发布测试版提供下载，尚未安装真实电视。
- Android 5 的 ADB 验证经转发环回同一模拟器，覆盖密钥读写、本机身份、命令完成标记及 root 守护进程拒绝；未验证 shell 身份下的应用启禁、真实网络调试或固件授权。Wi-Fi / 有线切换、真实输入源、默认 HOME、重启与待机唤醒仍需目标电视验收。
- 历史兼容分支曾在 API23、31、36、37 模拟环境验证部分页面和操作；通用 Android 镜像不能代替 TV 固件，历史分支结果也不能直接视作集成包全部通过。
- 已发布 b23 曾在一台 Android 12 / ARM32 电视覆盖安装、核对 APK 哈希，并检查 HOME 返回、壁纸和 Dock；范围见 [Releases](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0)。
- 历史公开 b24 通用优化包构建通过，完整 Lint 为 0 错误、27 条警告；已核对实际 APK 最低 API23、架构、签名和 GitHub 回下载哈希，签名与 b23 一致。**b24 发布时未安装到电视或模拟器**；Android 6、7 及其他厂商固件、真实 HOME 切换／恢复、长期驻留、投屏和完整播放仍需分别验收。详情见 [b24 发布说明](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b24)。
- 无线配对仍为实验入口，要求 API33+、可加载原生库及设备实际提供配对服务；六位码成功配对不能由版本号或构建通过代替。
- 未知固件可读取应用，未经匹配和核验的系统包操作保持只读；不能套用其他设备的禁用清单。

Published b26 passed Debug build, 19 unit tests and Lint (0 errors, 28 warnings). API21/22/23/31 emulator checks cover basic UI, app enumeration, Back and offline licenses; the optimized universal APK also passed API21/22 UI and native-library smoke checks. Android 5 ADB checks use a forwarded loop to the same emulator and verify identity, output markers and root rejection; shell package writes and actual TV firmware remain unverified. API31 also passed shell-authorized fixture disable/enable, operation journal, exact state restoration and optimized local ADB smoke. Installed optimized APK hashes match the candidate on API21/22/31. b26 is a prerelease and has not been installed on a physical TV.

The published b24 universal APK passed its optimized build, full Lint (0 errors, 27 warnings), package and signature checks, and GitHub download hash verification. It has not been installed on a TV or emulator. Historical emulator and b23 hardware checks do not validate b24, other vendors, real HOME changes, pairing, casting, or long-running operation. Unknown firmware retains read-only system-package behavior.

## 首页 Dock 材质与动效（开发源码）

首页 Dock 使用壁纸模糊缓存、半透明覆层、细描边和轻阴影；遥控焦点带动当前封面及相邻封面缩放、避让，确认键提供短促按压反馈。保留五项容量、横向封面、应用顺序和焦点记忆；顶部按钮及全部应用网格沿用各自样式。焦点移动复用背景缓存，壁纸变化时在后台生成缓存并同步淡入。视频壁纸下使用视频封面的磨砂缓存，不逐帧抓取或模糊视频；封面缺失时沿用视频播放器的静态背景。

| 档位 | 共同功能及差异 | 本次验证边界 |
| --- | --- | --- |
| 标准 API21+ | 相同磨砂底板和焦点动画，无额外权限或依赖 | 构建、局部单元测试及 Lint 通过；API31 的 1080p、720p 模拟检查通过 |
| 安卓4.4 / legacy19 | 共用实现，不依赖 API31 的 RenderEffect | 构建、局部单元测试及 Lint 通过；API21 运行检查通过，API19 运行与老电视性能待验收 |
| 酷开 API26+ 包名档位 | 共用首页实现，无 Dock 专用兼容分支 | 本次未单独构建或验收酷开档位 |

此处描述当前开发源码，不代表已发布 APK 包含。模拟器验证不替代厂商电视的真实遥控、升级数据保留及长时间运行验收。与动态壁纸集成后，API31 另通过视频播放期间的焦点动画、封面缓存复用、离开返回、静态／默认切换和封面缺失检查；视频布局仅在显示尺寸或源缓冲尺寸变化时更新，避免持续重复布局。

## 儿童模式源码兼容

| 功能 | 标准 API21+、安卓4.4、酷开 API26+ 包名档位 | 边界 |
| --- | --- | --- |
| 同排标签、隐藏、锁定、数字密码及恢复码 | 共用原生界面与本地存储，不使用系统停用、卸载或 ADB | 仅管理 Quiet TV 内的入口；当前源码的版本更新页恢复码按三次确认显示（旧版为关于页） |
| 单次限时、每日累计、家长追加时间 | 共用独立无障碍计时服务；API19 使用旧屏幕状态接口，API33+ 使用受限广播注册 | 依赖本机无障碍窗口事件和后台显示能力；安卓4.4不因桌面守护被禁用而裁剪本服务 |

本轮 Android TV API33 Debug 模拟验证覆盖数字恢复码、遥控三次确认、家长验证及密码重设、隐藏与锁定、预设及自定义时长、重新进入验证、家长追加时间、前台计时、切换暂停、息屏暂停／唤醒继续和到时限制页。标准、安卓4.4与酷开包名档位通过编译和 Lint（0 错误，仍有警告），标准档另通过 R8 优化检查；标准与安卓4.4各通过 8 项儿童模式规则／密码单元测试。源码构建／静态检查与模拟验证分开记录，实际厂商固件、实体遥控器、待机唤醒、长时间使用及升级后的规则保留仍需真机验收；不代表现有 Release 已包含。用法见[儿童模式](APP_MANAGEMENT.md#儿童模式)。

## 信号源兼容

当前开发源码的小米 HDMI 直切采用分层规则（尚未发布）：先用已实测的特殊映射，再从输入原始标签提取 HDMI 端口，通过 `mitv.common.ConfigurationManager.getInstance().queryTvConfigByKey("hdmiN_order")` 读取固件源编号；类或方法缺失、访问受限、查询异常或返回值不是正整数字符串时，再回退原有型号／固件及通用规则。固件读取不限定型号或固件版本，但只处理本机已注册、硬件 ID 为 `/HW数字` 的 HDMI 输入，实际启动仍要求系统输入及可调用的小米系统播放器；不补造端口，不尝试轮流切换编号。

保留 MiTV-ANTL0、MiTV-MFFU1、MiTV-MFZP0，以及 MiTV4-ANSM0／22.11.18.3386 的已实测特殊映射。其中最后一项将 `/HW7` 显示为 HDMI 1、使用源编号 23，并过滤已确认的 `/HDMI300004` 重复条目；这些特殊映射不会被固件查询覆盖。标签修正和重复条目过滤继续独立处理，读取固件编号不等于自动识别全部物理接口。AV、DP、USB-C 及电视调谐器保留各自既有规则。依据为 [Aerial 分层输入路由](https://github.com/pengchujin/aerial-tv/blob/f1c8ea979a9128cebc46c646bb8efba1644e7b26/app/src/main/java/app/orchard/tv/system/SystemState.kt#L653-L703)、[小米 Android 6 编号差异记录](https://github.com/pengchujin/aerial-tv/blob/f1c8ea979a9128cebc46c646bb8efba1644e7b26/docs/devices/xiaomi-android6.md#L13-L19)及 [CleanYourTV 端口映射记录](https://github.com/Gathin23/cleanyourtv/pull/722)。标准档 API21+ 与酷开 API26+ 包名档位共用此代码；安卓4.4档仍使用系统设置／遥控器。厂商接口可访问性、真实画面／声音及实体遥控切换仍待目标电视验收，上游实测不代表 Quiet TV 真机通过。

1.1.2 标准包已包含原厂信号源面板回退；安卓4.4兼容包仍使用系统设置／遥控器。Android 标准 TV Input 列表为空或服务不可用，并不证明电视没有输入接口。标准路径继续读取本机输入；回退按实际安装的系统组件、启用状态、导出状态和调用权限检测，不按品牌名称套用固定 HDMI 数量，也不自动修改系统组件或权限。

| 候选固件 / Candidate | 源码行为 / Source behavior | 依据与验证边界 / Evidence |
| --- | --- | --- |
| 海信 / Vidda MT9653 | 检测 `com.hisense.tv.inputs/com.hisense.tv.InputsUIService`，通过 `com.hisense.tv.start_inputs` 打开原厂面板。标准列表正常时保留列表。 | [上游记录](https://github.com/Gathin23/cleanyourtv/blob/3e996739ce0b470b51c1c438bf5d0559d1c9e7eb/examples/vidda-mt9653/debloat.txt#L45)称该固件标准输入为空、原厂面板可以切换；Quiet TV 尚未在目标海信验证，不能视为所有海信支持。 |
| TCL ak30a5 / Android 9 | 检测 `com.tcl.settings/com.tcl.insrc.ShowWindowService`，通过 `com.tcl.insrc.SHOW_WINDOW` 打开面板。 | [上游 Issue #37](https://github.com/Gathin23/cleanyourtv/issues/37)确认面板调用，但该机型拒绝安装第三方 HOME；存在面板入口不代表 Quiet TV 可以安装，其他 TCL 固件仍需单独验证。 |
| BYINTEK U4 | 检测 `com.sdt.app.skysettings/.entry.activity.QuickSourceActivity`，优先打开面板，避免上游报告的虚假 HDMI／AV 列表。 | [上游记录](https://github.com/Gathin23/cleanyourtv/blob/3e996739ce0b470b51c1c438bf5d0559d1c9e7eb/examples/byintek-u4/README.md)；Quiet TV 真机待验收。 |

入口配置随源码保存在 [tv-input-profiles.json](../app/src/main/assets/tv-input-profiles.json)。组件名吻合仍需通过运行时权限与状态检查；多个可用面板由用户选择。标准切换发生异常时提供系统面板选项；调用成功但画面黑屏无法据此自动判断，可返回信号源菜单手动打开系统面板。未知机型没有匹配入口时提供原因和系统设置入口。

本次新增逻辑通过 9 项本地单元测试、Debug 构建、Lint 和配置校验。API31 专用模拟器回归覆盖标准服务缺失／受限／异常诊断、原生系统组件检查、Activity／Service 启动分支与失败处理、空输入提示及返回导航；系统组件启动在测试中被拦截，不代表已打开海信／TCL／BYINTEK 原厂面板。目标海信型号与固件尚未提供，实际 HDMI 切换、画面、声音及实体遥控器均待实机验收。

上游还记录 Vidda 的 `com.hisense.tv.tiletransition` 是实际切换依赖，禁用后可能出现“面板打开但 HDMI 不切换”。这是特定固件的诊断线索，Quiet TV 不会自动启用它，也不把信号源适配配置作为禁用／卸载系统包的授权。

System-panel fallback is included in the published 1.1.2 standard APK; legacy19 uses settings or the physical remote input key. Profiles identify exact system components and require runtime enabled/exported/permission checks. Standard inputs remain the default, with a vendor-panel preference only for BYINTEK U4's reported bogus list. These are upstream reports, not Quiet TV hardware acceptance or support for every model of a brand. Dispatch success does not verify picture, sound, physical remote behavior or actual source switching. No system package or permission is changed automatically.

## 默认桌面与恢复

设备需实际提供可用的 HOME 入口、系统设置、TV Input、遥控键和本机调试能力。默认桌面以读回的真实组件为准；命令返回 Success 不代表 HOME 已切换。保留原桌面及恢复记录，先验证再决定是否处理预装应用。流程见[桌面配置](SETUP.md)和[应用管理](APP_MANAGEMENT.md)。

## 上游兼容线索与 Quiet TV 状态

TCL／雷鸟的安装受限方案于 2026-10-26 补充：先以品牌发现候选，结合实际安装错误判断，不要求先提供完整固件号。固件缺失不阻止显示方案，但必须记为未确认；执行前仍核对本机安装器、ADB 用户及恢复条件，按[Agent 安装说明的 TCL 分支](AGENT_INSTALL.zh-CN.md#tcl雷鸟安装受限候选方案)取得本次授权。此补充不开放应用内未知固件的系统包操作规则。

| 范围 | 上游依据与方案 | Quiet TV 状态及后续验证 |
| --- | --- | --- |
| TCL／雷鸟，无 HOME relay 安装及守护 | [Aerial TCL 记录](https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/tcl-mt5879-cn.md)：不声明 HOME 的 guard 包、多屏互动推送及当贝／电视端回退，后续配置守护 | 复用现有 relay 与守护，接入 T-Cast 安装、电视端回退、原 AI 指令的签名配置选包及工具下载；默认不停用安装器。没有已发布 relay 时明确报告，不用普通包替代。安装、服务绑定、实体 HOME、重启和待机行为仍需目标固件验收 |
| TCL／雷鸟，出现明确 LauncherStrategy 安装拒绝 | cleanyourtv 社区 [#683](https://github.com/Gathin23/cleanyourtv/issues/683) 在 `t982_ar31a8`、Android 11、`RQ3A.210705.001.2024111116` 上报告：临时停用 `com.android.packageinstaller`、安装 HOME 包、立即恢复，再设置 HOME；维护者将该办法列为官方项目范围之外 | 已实现 [TCL 安装助手](../scripts/install_tcl.py)：预览、实际错误识别、明确选择后临时处理安装器、异常恢复、独立恢复入口及安装后 APK 校验。按品牌进入检测，无固件号也可检查；不按品牌自动停用。离线模拟不计为 Quiet TV 真机通过，目标固件的安装、恢复及 HOME／电源行为仍待验证 |

上游报告使用另一个包名 `local.quiettv.launcher`，不是本项目的 `home.quiet.tv`。临时安装器方案不适用于所有校验失败；只有通用错误码或解析期证书拒绝时不能直接套用停用操作。relay 安装通道与临时停用方案分别判断，不因一条失败自动扩大系统修改。源码及工具完成不等于对应 APK 已发布或目标电视已验收。

以下记录来自 Aerial 的源码和用户反馈，调查日期 2026-10-22。它们不授予系统包操作权限，也不计入 Quiet TV 已验收机型。完整原始日志、设备资料和过程记录保留本地；公开反馈只保留脱敏必要信息。

| 范围 | 上游记录与方案 | Quiet TV 状态及验收重点 |
| --- | --- | --- |
| 小米 MiTV4-ANSM0 / Android 6 | [#81](https://github.com/pengchujin/aerial-tv/issues/81)：服务绑定后仍可能没有有效窗口事件；上游1.43起增加 UsageStats 补偿，1.46有用户延迟返回及有限重启实测 | 本修复源码加入安卓5／6精确主页补偿与电脑首次配置；未发布，目标固件的事件、实体 HOME、重启、短/长待机及暂停恢复仍待 Quiet TV 真机验收 |
| 酷开 / Android 8 | [#69](https://github.com/pengchujin/aerial-tv/issues/69)：私有 HOME.CC 类别，识别修复不等于改写固件主页 | Quiet TV 55G20 在 1.2.9 已有用户确认实体 HOME 与重启通过，详见[样机记录](#样机验证记录)；其他场景与固件仍待验收，不能按品牌批量应用 |
| 酷开 / Android 11 | [#122](https://github.com/pengchujin/aerial-tv/issues/122)：LITE_HOME 与 HOME.CC 不同，用户修改包有有限实测 | 原主页识别、实体键、开机与恢复均待目标固件核验 |
| 海信 Android VIDAA | [#40](https://github.com/pengchujin/aerial-tv/issues/40)：私有主页属性可改变 HOME 行为，开机仍受拦截 | Quiet TV 不自动改属性；系统输入面板调用与 HOME 接管分别验证 |
| TCL MT9655 / 部分新雷鸟 | [机型记录](https://github.com/pengchujin/aerial-tv/blob/d652d5ef65267f22bd273da01f28a99718d059a8/docs/devices/tcl-mt9655-cn.md)：TLS 动态端口但无可用配对入口，能装 APK 不等于能授权接管 | 先核实调试方式；无配对/授权通道时保留普通 App 使用，不循环试连 5555 |
| 小米 MiTV-ANTL0 / Android 5.1 | [#64](https://github.com/pengchujin/aerial-tv/issues/64)：输入标签与物理口错位，切换编号不同 | 仅按已知型号、输入服务及硬件 ID 修正，Quiet TV 实际切源待真机验收 |
| 红米 MiTV-MFFU1 / Android 14 | [#62](https://github.com/pengchujin/aerial-tv/issues/62)：DP 与 USB-C 名称和路由不同，固件连接状态不可靠 | 型号限定映射，未知状态不显示已连接；需实际接线核对 |
| 康佳 KKTV U86V9 | [PR #125](https://github.com/pengchujin/aerial-tv/pull/125)：原生 SDK 枚举及直切候选，不能按品牌生成固定端口 | 本轮未加入该未合入候选；保留系统设置/遥控器路径，获得样机后验证 |
| 遥控器与播放保护 | [#115](https://github.com/pengchujin/aerial-tv/issues/115)、[#108](https://github.com/pengchujin/aerial-tv/issues/108)、[#22](https://github.com/pengchujin/aerial-tv/issues/22)：按键过滤连跳、切源误拉回、息屏自行亮起 | 本轮主程序不新增守护或按键过滤；后续助手实验必须覆盖这些回归 |

反馈请分别给出安装包版本/构建号、固件、安装、设置、信号源、实体 HOME、整机重启、冷启动、待机唤醒与恢复结果。未执行记为未执行，不能用模拟器或上游结果代替本应用真机验收。

## 本轮源码兼容改进

1.1.2 的这一部分改变普通应用入口、设置调用、图片生成与信号源调用，不增加 HOME 守护、按键过滤、系统包操作权限或 ADB 常开功能。这些改动已包含在 1.1.2 下载中，实际厂商适配仍待验收。

| 功能 | 公共实现及档位差异 | 验证边界 |
| --- | --- | --- |
| 应用入口 | 安卓4.4与标准档共用应用目录；电视入口优先，补 APPSTORE_LAUNCHER 及小米播放器/米家明确组件。厂商补项仅接受可调用系统组件，首页、排序和常用应用选择共用结果 | 原生组件与权限检查可在模拟环境验证；实际厂商入口待验收 |
| 设置与网络 | 小米优先检测本机设置/Wi-Fi 组件；标准设置不可用时尝试 TCL 系统设置。检查启用、导出、权限，拒绝直接解析到 HOME 的入口，失败显示提示 | 复用实验分支的设置路由；不包含该分支的助手、构建号或设备操作 |
| Quiet TV 应用图标 | 两档均增加普通 LAUNCHER 入口，同时保留 HOME 和电视入口 | 原厂应用中心是否展示及遥控启动待目标固件验收 |
| 卡片图片 | 优先加载 APK 中的高密度资源，复用现有卡片样式与缓存；4K 渲染且内存预算足够时提高生成尺寸，低内存设备保留原尺寸 | 不重绘第三方品牌图案；不能提高 APK 本身缺失的源图细节 |
| 新上传壁纸 | 按应用实际显示长边处理，普通设备最大 3840；低内存或内存档位低于 256 MB 时最大 1920。保留 EXIF 方向、格式与像素上限 | 只影响新上传；不重写已有文件，原图仍在上传端。应用渲染 1080p 的 4K 面板不强行生成 4K 图 |
| 壁纸轮播频率 | 标准档与 legacy14／legacy19 共用顶部下拉框、1／5／15／30／60分钟、按分类轮播与旧设置迁移；视频仍分别使用 Media3 和系统 MediaPlayer，不改变各档素材限制 | 配置与顺序策略由本地单元测试覆盖；实际播放、焦点、系统暂停恢复及厂商黑闪需分别验证 |
| TCL 快捷信号源 | 只有本机声明 com.tcl.software.systemui_custom_tv_source 时，在 SystemUI 包内解析 SHOW_WINDOW 服务并传递 Source/input 参数；再次核对实际系统组件、权限和状态 | 继续保留 TCL 设置面板及其他已有面板；只确认调用，不视为切源成功 |
| 小米 MiTV-ANTL0 | 仅已注册的系统 com.droidlogic.tvinput HDMI / HW6，显示 HDMI 1 并尝试源编号 23 | 不补造第二个端口；其他型号、服务或类型不继承编号 |
| 红米 MiTV-MFFU1 | 仅系统 com.mediatek.tvinput DisplayPort / HW9、HW10 分别对应 DP/29、USB-C/30 | 不把框架报告的连接状态当可靠接线证据，不显示未经验证的“已连接” |
| 未匹配直切 | 继续使用标准 TvContract 调用，失败提供已检测的系统面板和遥控器路径 | 不按品牌硬编码端口数量，不自动授权、不修改主页属性 |

应用入口与图标依据：[Aerial Apps.kt](https://github.com/pengchujin/aerial-tv/blob/d652d5ef65267f22bd273da01f28a99718d059a8/app/src/main/java/app/orchard/tv/apps/Apps.kt)。设置/信号源依据：[SystemState.kt](https://github.com/pengchujin/aerial-tv/blob/d652d5ef65267f22bd273da01f28a99718d059a8/app/src/main/java/app/orchard/tv/system/SystemState.kt)、[TCL 记录](https://github.com/pengchujin/aerial-tv/blob/d652d5ef65267f22bd273da01f28a99718d059a8/docs/devices/tcl-mt5879-cn.md)。本轮按这些接口事实实现现有 Java 结构，未引入上游 UI、守护、视频屏保或额外依赖。

当前代码验证记录见本地任务交付；上游机型结果不计入 Quiet TV 验收。厂商设置、真实信号源画面、实体遥控器、重启、冷启动、待机唤醒均需另行真机验证。Android 4.4 仍不提供标准 TV Input 列表，信号源使用系统设置和实体遥控器替代路径。

## 本地源码新增的系统入口适配

以下实现已加入本地源码，尚未作为新安装包交付；历史 1.1.2 下载不包含本节新增内容。上游案例只说明接口依据，不能作为 Quiet TV 真机验收。

| 入口 | 实现与限制 | 适用档位 |
| --- | --- | --- |
| 网络 | 优先检测 TV 网络页，避开小米部分固件会立即关闭的 Wi-Fi 子页；有线连接先检测小米 Ethernet 入口。按明确组件顺序尝试，仅在同步调用失败时推进；快速返回或 HOME 不会触发计时重开 | 三档共用路由，以固件实际组件为准 |
| 原厂设置 | 补海尔、PPTV、微鲸、Fire OS 设置入口及极米设置服务/页面链接；过滤 CTS 空壳、HOME、禁用、未导出、无权限和非系统目标。开发者设置仍先打开其专用入口 | 安卓4.4、安卓5及以上、酷开安卓8 |
| 海信面板 | 仅配置的 InputsUIService 在 API26+ 使用前台服务启动；其他面板保持原启动类型 | API21+，不代表其他品牌通用 |
| TCL / 雷鸟 | 保留原厂面板；标准请求无法调起时，在 MT5879 / T966D5 中间件和系统输入服务匹配时提供原厂播放器候选，按实际枚举的 HDMI 1–4、DTV、AV 选择序号，不补造端口 | API21+；T966D5 序号仍待目标固件验收 |
| DroidLogic | 已注册 HDMI、系统输入服务及原厂播放器同时匹配时，将实际输入 ID 交给 DroidLogicTv | API21+，不代表任意 Amlogic 固件支持 |
| 创维 / 酷开 | 标准列表为空时识别厂商 source_list provider：旧接口使用 METHOD_GOTO_SOURCE，SDK 列表使用 SkyTVMainActivity 与 source 参数。先读取实际端口再选择路径，不连续盲试两套切换 API | API21+，不同固件分别验收 |
| 康佳 | 标准列表为空且系统播放器可用时，通过现有 KKCommonManager 读取实际端口枚举；不打包厂商 jar，不硬编码端口数量 | API21+，系统 SDK 不可访问时保留原面板路径 |
| 小米 | 保留既有直切，补旧 MiTV4-ANSM0 / MiTV4A 的 Amlogic 固件差异，过滤 MFTR0 / MFZP0 已知虚假端口 | API21+，按型号、服务和固件限制 |
| 标准输入 | 显式携带 channel MIME，绑定已解析的可调用系统播放器，避免第三方 URI 处理程序接走请求；日志只记录请求，不视为切源成功 | API21+，安卓4.4仍使用设置/实体遥控器 |

接口依据：[原项目系统设置](https://github.com/Gathin23/cleanyourtv/blob/eb0131a32fd8567086f39002f1782f7c1b078ca6/launcher/app/src/main/java/com/gathin/tvhome/SystemSettings.java)、[原项目酷开输入](https://github.com/Gathin23/cleanyourtv/blob/eb0131a32fd8567086f39002f1782f7c1b078ca6/launcher/app/src/main/java/com/gathin/tvhome/CoocaaSources.java)、[Aerial 系统入口及输入](https://github.com/pengchujin/aerial-tv/blob/bc89bc51a382899d3233f7d0b87db54a5d408348/app/src/main/java/app/orchard/tv/system/SystemState.kt)、[康佳输入](https://github.com/pengchujin/aerial-tv/blob/bc89bc51a382899d3233f7d0b87db54a5d408348/app/src/main/java/app/orchard/tv/system/KonkaSources.kt)。小米网络页秒退见 [#331](https://github.com/pengchujin/aerial-tv/issues/331)，雷鸟播放器候选及未验收边界见 [#395](https://github.com/pengchujin/aerial-tv/issues/395)。

本次验证覆盖本地构建、配置/文档检查、模拟器中的路由故障注入及系统页面返回导航。未在厂商电视验证原厂服务、真实切源画面声音、CEC、实体遥控器、重启、待机或升级保留，未发布新包。


## 旧版本用户反馈与当前处理

以下反馈的安装包版本未知，用户确认早于 b26。它们不能直接证明当前源码仍有同一故障，也不能据此宣称目标固件已经修复。

| 反馈 | 已核对的源码与处理 | 仍需复测 |
| --- | --- | --- |
| TCL 安卓 4.4，root 后可用，但设置、网络、信号源和应用设置闪退 | b24 的最低 API 为 23，旧源码直接调用新系统 API，设置回退也可能再次抛出异常。当前 `legacy19` 已隔离 API21+ 接口；本次补齐设置解析、组件状态查询及启动异常处理，开发者设置复用同一路由。root 不会补齐 Android API。 | 确认真实 API 与所装包，使用安卓 4.4 档复测这四类入口；缺少崩溃栈，尚不能确认每处闪退原因。 |
| KKTV U55J，工厂菜单开启 ADBD，无授权弹窗且无法连接 | 本次去掉握手前的额外 TCP 探测，复用一次有超时且可取消的连接；错误区分端口、握手／授权、本机身份、ADB 身份及前台用户核对。授权提示仅在实际出现弹窗时要求允许。 | 工厂 ADBD 开关不保证监听 TCP、允许本机访问或提供 shell 身份；无弹窗也不能单独判断成功／失败。需记录失败阶段，再区分固件限制与客户端问题。 |
| 海信安卓 5.1，信号源无法选择 | b26 之前的标准 TV Input 路径不覆盖原厂私有面板；当前已检测可调用的海信系统面板，保留标准列表及失败提示。 | 现有面板依据来自特定 Vidda 固件，不能外推到这台未知型号的安卓 5.1 电视；升级后仍失败时，需要该机真实面板组件及相关异常，不能套用其他机型端口表。 |
| 微鲸 55D2UA 安卓 5.0，可开 ADB，无法设置默认桌面 | 当前只使用固件帮助中确实提供且支持指定用户的命令；本次在已连接但缺少切换命令时禁用无效 ADB 操作，并按系统入口是否存在提示系统选择或临时使用。 | Android 5.0 的版本号及 ADB 可连接不等于支持 `set-home-activity`；需核对 HOME 候选、系统选择入口、命令帮助及实际报错。无可用路径时保留原桌面，不能宣称已绕过固件限制。 |

本次改动共用于标准与 `legacy19` 档。Android 4.4 仍使用系统设置／遥控器信号源键，不提供标准 TV Input 列表；两档都不会自动开启 ADBD、改变 root 状态或禁用原桌面。测试通过只证明所列软件路径；实体 HOME、HDMI 画面和声音、重启及待机唤醒仍需各电视验收。

本次本地验证：标准与 `legacy19` 均通过 Debug 构建、23 项单元测试和 Lint（0 错误，仍有警告）；API22 模拟器通过 23 项定向检查，覆盖设置解析／组件状态／启动失败回退、权限与 HOME 入口保护、单次 ADB 连接、握手取消、实际 ADB 本机校验及 root 身份拒绝，以及首页、桌面设置、应用管理和首次设置的启动。实际 ADB 使用转发连接同一模拟器，无需授权弹窗；没有验证 shell 身份下的系统写操作。安卓 4.4 本轮未完成运行验收，四台反馈电视均未实测；不将旧包反馈直接标为已解决。

## HOME 代理适用边界

可选 HOME 助手接入两路线自动选配：主程序直接 HOME 优先，明确失败后尝试同签名代理，再回退原主页识别。助手本身声明 HOME，无 HOME 主程序并不保证助手也能安装；包名允许名单、开机强制组件及后台限制仍需按固件验收，不根据小米、创维、TCL 或雷鸟品牌作成功承诺。小米／Redmi 当前优先采用主程序 `com.ts.launcher` 改名路线，不限制机型或固件入口；普通 `home.quiet.tv` 包保留历史代理，旧代理保持原包名。改名主程序与同名助手互斥，不自动迁移。适用入口覆盖所有小米／Redmi 机型不等于全机型验收；当前没有本项目该身份助手的厂商真机验收，不自动替换外部同名应用。

代理保持原桌面和必要依赖，支持独立原桌面回退、全局暂停、来源一致的恢复启用与实际 HOME 恢复。开机跳板仍默认关闭；代理前台回执不能作为重启、断电开机、待机唤醒或实体 HOME 的通过证据。安卓5及以上的标准/无 HOME 形态可使用，安卓4.4保留原有流程。构建方式和故障回退限制见 [HOME 助手说明](../home-proxy/README.md)。


| HOME 助手构建 | 选择范围 | 共同能力及差异 |
| --- | --- | --- |
| 主程序 `com.ts.launcher` | Android 5+ 小米／Redmi，显式改名构建，不限型号/固件入口 | 主程序直接参与 HOME，保留窗口、按键及开机恢复；不搭配 HOME 助手，逐机验证 |
| 历史助手 `com.ts.launcher` | 普通主程序已部署的旧代理路线 | 保留旧配置，不自动替换为改名主程序；与同名主程序互斥 |
| `home.quiet.tv.proxy` | 其他品牌，以及已配置此身份的小米旧代理 | 同上；不自动迁移已建立的系统 HOME |
| 安卓4.4 | 保留现有流程 | 现有 HOME 助手最低 API21，此档不提供代理 |

诊断导出提供固定字段的设备统计 JSON、助手包名、实际 HOME 和分场景用户结果，用于汇总设备型号、固件与兼容情况；只读本机，不自动上传。厂商界面版本不一定等于 Android 固件显示版本，缺失时保留空值并请反馈者补充，不按商品名推断已支持。

小米改名主程序的本地开发修复包含按需 HOME 短按接管、长按/双按最近任务、开机前台证据复查及多入口去重。新安卓小米前台查询需授权，持续轮询仍限安卓5／6；其他品牌与安卓4.4维持原有范围。实体键、重启、待机及固件后台限制尚未验收，详见[配置说明](SETUP.md#小米主程序-home-与重启恢复本地开发未交付)。

MiBOX3 / JurassicPark（API22）新增直接 HOME 按键补偿开发路径，允许原桌面缺失但自身已是系统默认HOME的情况。用户需明确配置并开启无障碍，旧配置不自动启用；普通主程序、系统HOME解析、过滤请求与实体遥控结果分别核对。原桌面窗口观察、其他小米机型和其他厂商范围未扩大。配置与电脑预览见[配置说明](SETUP.md#mibox3-直接-home-按键补偿本地开发未交付)。本修复尚无目标盒子真机验收。
