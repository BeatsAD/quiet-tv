# 品牌、固件与接管路线

[统一入口](../AGENT.md)。首次接管或 HOME 修复进入本页；仅检查、升级、恢复沿用原意图，不借此重新接管。用户只输入统一入口的请求，Agent 负责检测、解释并选择工具，不要求用户决定脚本参数。

## 上游方法及采用范围

核对 [cleanyourtv 的固定源码版本](https://github.com/Gathin23/cleanyourtv/tree/d4ac32c344987ae4e8faf1ee32d8b9572fb57fe3)。上游通过 [PROMPT.md](https://github.com/Gathin23/cleanyourtv/blob/d4ac32c344987ae4e8faf1ee32d8b9572fb57fe3/PROMPT.md)组织现场检测，结合 examples、设备报告及 tools/disable.sh、tools/recovery.py、launcher/build-and-install.sh 实现默认 HOME、可逆禁用和恢复。不是按品牌运行一份通用解锁脚本，亦有明确无法接管的固件。

Quiet TV 复用自己的签名 APK、权限接口和恢复记录，采用上游现场能力检查、真实 HOME 识别、Wi-Fi 依赖保护、保留启动依赖及逐场景验收方法。不运行上游批量精简、不安装上游桌面、不复制其他设备的禁用清单；上游关闭 ADB 的收尾偏好不覆盖本项目用户选择的 ADB 保持。上游改包名的观察不等于获得系统签名或系统权限。

这里的“解锁”指在用户自有电视、现有授权通道内打开调试、配置 HOME 和已实现的厂商路由，不指 root、解锁 Bootloader、刷机、恢复出厂或通用绕过认证。没有 ADB 时先按[连接分流](connect.md#adb开启与连接分流)指导现场授权；不是 Android 的 Tizen、webOS、Roku、SmartCast、Vega、Linux VIDAA 不进入 APK／ADB 路线。VIDAA 品牌本身不能判断操作系统。

## 连接前的厂商入口

先检查已有连接；只有菜单与标准方法不同，且用户确认型号和对应页面时才使用下表。仅指导用户操作明确的 ADB 开关，其他工厂参数保持原值；不通过启动工厂应用试探，不把组合键推广到同品牌其他型号。

| 上游明确报告的设备 | ADB 入口／连接方法 |
| --- | --- |
| Coocaa 14K／8S61，Android 4.4.2 | 在“本机信息”页面按上上、下下、左右左右，静候约两秒；进入“其他设置”的 Adbd Enable。只改调试项；这个型号的 adb reboot 被报告为关机。 |
| Skyworth／Coocaa 8R40_K5C，Android 8 | 设置的设备信息／更多信息页面使用上上、下下、左右左右，确认出现调试入口；保护提供该页面的依赖包。不能套到其他酷开固件。 |
| TCL MT9655_4K_CN，Android 14／AU01 | 上游为“通用设置 → 更多 → ADB”；若仅有 TLS 连接服务且无配对入口，需用户按该电视硬件支持方式建立 USB 调试并现场授权。之后可以按已授权范围临时切 TCP；不能宣称端口重启保留。 |
| Sony BRAVIA_AE2_CN 指定构建 | 读取对应设备的 mDNS TLS 连接端口，按电视提供的授权／配对方式连接；5555 在该报告中重启关闭，配对记录和连接端口是两回事。 |

固定版本的详细来源均保存在 `device-presets.json.references` 的 `source`。只有用户给出品牌、没有对应型号／固件／页面时，先取得这些信息，继续可独立完成的电脑工具准备；不能猜隐藏密码或将未连接判断为已解锁。

## AI 实际调用

在[任务工具](session.md)固定目标、用户、工具和恢复目录后，先运行默认只读检测：

```sh
python3 agent_session.py run --session "$SESSION_DIR" --tool device_adaptation --
```

工作区直接检查也可用以下底层命令；已经建立任务时使用上方包装，不另建一套记录：

```sh
python3 scripts/device_adaptation.py --serial "$TV_SERIAL" --user "$TV_USER" --record "$NEW_ROUTE_DIR"
```

工具读取固件身份、当前用户 HOME 候选、解析、设置能力、系统包、厂商属性及需要时的 Wi-Fi 创建者，生成私有 `home-routes.json`，不启动应用、不注入 HOME、不执行应用广播或写入。报告包含：

- `routes`：标准 HOME、VIDAA、HOME.JM、精确禁用、Google HOME、兼容守护的候选和缺失条件。
- `preserveOriginalReason`、`setupWifiBlocker`：保留原桌面或设置向导的实际原因。
- `knownRisks`：按报告中已知型号／API／构建或完整指纹匹配的上游风险线索，保留固定源码来源；不是本机验证。
- `unknown`：未取得的查询；不得补成默认值或当作失败证据。
- `requiredChecks`：实体 HOME、用户观察的重启／冷启动／唤醒及恢复验收。

`candidate` 只表示值得进入现有预览，不能直接执行禁用或宣布可用。实际安装包还须具备所需组件与接口。`device-presets.json.devices` 是精确执行资格，`references` 只用于调查和保留依赖；上游报告不自动授予禁用权限。旧 `disableVerified` 字段不代表实体 HOME、冷启动、唤醒及恢复全通过，必须读取 evidence 备注并验收。

统一请求中对“满足精确适配规则及恢复条件时禁用已确认原桌面”的明确选择可沿用，不反复询问同一授权；先向用户说明本机实际对象与影响，再按现有工具预览及执行。底层安装器未显式指定专项路线时仍保留原桌面；没有该选择的请求亦然。授权不扩展到其他系统应用，也不把社区参考变成执行资格。

## 路线顺序与执行

| 现场条件 | Agent 动作 | 效果边界 |
| --- | --- | --- |
| 已有有效配置 | 沿用原路线、暂停状态和恢复基线 | 更新不重新接管 |
| Quiet TV 是真实 HOME 候选，固件支持当前用户设置 | 首选 `configure_boot_home.py`，保留原桌面 | 默认值读回后仍验实体 HOME、开机 |
| 实际厂商开机指针绕过默认 HOME | 预览现有 VIDAA／`jm` 路线，核对组件、用户、原属性与写权限 | 不是所有海信／坚果都适用；不能扩大到其他属性 |
| 原桌面竞争 HOME，且精确规则／Google 条件允许 | 列明包、影响、恢复办法，按用户已有明确选择执行 `verified`／`google` 专项 | 保留数据；禁用后重设 HOME；不以禁用试错 |
| 固件保留原桌面但支持兼容守护 | `configure_home_guard.py`，核对无障碍、所需通知／用量统计／后台启动能力 | 可先显示原桌面再跳转，称“兼容接管”，不称首屏直达 |
| 厂商 HOME 类别、已安装代理或专用包 | 按[适配规则](../DEVICE_ADAPTATION.zh-CN.md)检查已适配代理／同包名路线 | 不临时构建助手、不覆盖已有独立助手 |
| API24+ 解锁前 HOME 或存储异常 | 按[HOME 专项](home.md)核对实际 APK 的 Direct Boot 与启动日志 | 属于启动兼容，不是额外权限；新增助手须独立开发与发布 |
| 以上能力缺失、明确拒绝或证据不足 | 保存实际错误和已完成项，给出手动应用入口／待适配项 | 不承诺彻底接管，不循环试写、卸载或隐藏 |

统一安装器 `--goal takeover` 也记录路线报告；默认仍走标准 HOME，再在确认没有标准路线时评估守护。专项路线通过原有 `--adaptation-route` 指定。标准 HOME 写入成功但实体按键／开机失败时，先采集真实前台、启动日志与原记录，进入 HOME 修复；不能把写入成功当结束，也不能在存在未确认变更时自动换路线。

## 已纳入的品牌差异

| 平台／报告 | 采用方法与限制 |
| --- | --- |
| Google TV | 先识别真实 launcher；setupwraith 的 RecoveryActivity、FallbackHome、系统设置、工厂测试和输入播放器不能算可用恢复桌面。禁用 setupwraith 前检查 Wi-Fi 创建者，属于它或无法读取则停止该禁用，先由用户在系统网络设置处理再复查。 |
| Sony | 中国固件可能用当贝，海外固件用 Google HOME；按实际组件及固件匹配。动态 TLS 调试端口随启动变化，不能承诺固定 5555。 |
| Xiaomi／Redmi | 精确固件规则与守护分开。`Success`、无障碍绑定、软件注入与实体 HOME 分开验证。disable-user 被拒绝不改用 uninstall／hide／block 试错；Android 4.x 没有通用守护。 |
| Hisense Android／VIDAA | 优先确认默认 HOME 及已支持的厂商属性；无障碍窗口事件缺失时核对现有用量统计补偿。NOVA72691 指定固件有停用原桌面卡启动报告，保留原 HOME；不能把上游未测属性当已验证方案。 |
| TCL／FFALCON | 安装校验、自启动策略、无障碍绑定与按键过滤分别处理。MT9655 CN 报告只有授权 USB 入口可进入调试，遇到安装拒绝转[TCL 专项](tcl.md)。G09 开启按键过滤前后实测音量长按，不清空其他服务。 |
| Skyworth／Coocaa | 8R40_K5C 与 7T861_A23 的启动依赖不同；保护各报告的原 HOME 与 ADB 设置依赖。8S61/API19 报告 disable 受限且 block 会卡启动，不换命令绕过；专用助手必须与本机及目标包匹配。 |
| CVTE／海尔等平台 | 存在 `com.cvte.*` 且无精确固件资格时保留原桌面，先验默认 HOME 与冷启动；不启动工厂／生产菜单探测用途。 |
| Acer R4_GTV 指定固件 | 上游报告设置向导冷启动被重新启用，导致恢复页面循环；保留 launcherx 与 setupwraith，评估守护。待机通过不能代替冷启动。 |

## 验收与继续

任务工具在执行接管配置时自动加入 `physical_home,user_reboot,cold_boot,user_wake,recovery` 必需项；这只要求证据，不授权重启或切电源。按用户授权测试，缺项保持 pending，能力受限记 LIMITED，失败记 FAIL，不把配置返回 0 当成“已完成”。网络 ADB 保持再加 `network_adb_after_reboot` 并实际重连。连接器或脚本缺少本页时报告尚未发布，不拿最新源码替代旧工具。

分别记录“系统默认 HOME”“开机首个可操作桌面”“最终前台”“实体 HOME”“播放／HDMI／设置及遥控器功能”“恢复可用”。原厂 LOGO、动画与广告单独判断；守护跳转成功不表示原桌面从未显示。测试前后比较 HOME／包状态，若固件重启改回，不自动重复禁用。保护原记录；无需清理历史文件或用户数据。
