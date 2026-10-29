# 电脑 AI 适配规则与恢复

[安装](AGENT.md)  [诊断与验收](AGENT_ADAPT.zh-CN.md)

电脑 Agent 负责检测、选择规则、配置与读回；电视保存本机配置，独立运行 HOME、守护、ADB 保持与信号源。电脑断开后不依赖 Agent 在线。新规则可独立于 APK 更新，但 APK 必须已提供所需能力；未知协议或运行行为仍需开发和发布新 APK。

## 规则来源与匹配

[device-presets.json](../scripts/device-presets.json) 是后续维护的配置规则源，首批完整承接旧规则及参考资料。`devices` 是执行规则，`references` 仅是调查线索，不能因品牌相同转成执行资格。先读取本机品牌、型号、完整指纹、用户、相关包版本和实际能力，再选择规则。

[device_adaptation.py](../scripts/device_adaptation.py) 默认只读。安装页 `latest.json` 同时分发脚本和 JSON 的 SHA-256；独立 Agent 下载索引列出的全部工具与规则到同一私有目录，并校验实际字节。不从网页摘要、旧缓存或临时生成的命令替代规则。源码工作区使用同目录文件，不联网取得其他规则。

电视只接受当前固件的一条数据规则，不执行下载代码。接口返回 `adaptation-rules-v1`、`takeover-record-v1` 等能力；缺失时报告需要支持它们的 APK，不绕过。固件升级后本机规则标记 `stale`，重新检测；已有恢复记录不自动套到新固件。

旧 APK 内的 `setup-presets.json` 冻结为升级兼容默认值，不继续添加机型。已保存本机规则优先，旧用户未重新配置时保留原行为。专项接管记录保存当次规则快照；后续规则更新不能改变其恢复依据。信号源运行驱动及旧默认映射保留，电脑通过现有 `profile-set` 接口写入当前固件的修正。仅增加配置不能创造尚未实现的厂商服务协议。

同包名、同签名覆盖升级时，若仅主程序构建号递增而固件、HOME 形态、原桌面和助手版本均未改变，沿用已配置路线与暂停选择；旧包的实体场景验收标记清为待验证。升级完成会恢复已选择的 ADB 设置保持，不重新启用用户暂停的守护，也不默认开启原本关闭的 ADB 保持。缺少权限时不能视为恢复成功。原环境发生变化仍停止自动沿用，先重新核对。

## 安装和专项接管

以下占位变量由 Agent 从已确认目标填写，每次预览、执行使用不同的全新私有记录目录。不得自动挑选一台 ADB 设备或猜测用户、端口。

```sh
python3 scripts/device_adaptation.py --serial "$TV_SERIAL" --user "$TV_USER" --record "$NEW_RECORD_DIR"
python3 scripts/device_adaptation.py --serial "$TV_SERIAL" --user "$TV_USER" --record "$NEW_RECORD_DIR" --action rules-set
```

规则预览通过后，以新目录加 `--apply` 写入当前设备。新统一安装器在已安装 APK 声明能力时自动同步匹配规则，普通安装仍优先 `configure_boot_home.py`，不默认禁用原桌面。

已核对专项需求、具体影响和恢复路线后可选择：

| route | 适用条件与动作 | 原能力承接 |
|---|---|---|
| `verified` | 精确规则、原 HOME、版本和用户匹配；保留安装数据，禁用原 HOME 后设置 Quiet TV | Sony 已收录原桌面、小米 alita／xmen／darkknight 规则；各自证据限制保留 |
| `google` | 实际已安装、启用且声明 HOME 的 Google TV／Android TV 桌面；系统完成初始化，恢复命令可用 | Google HOME 及实际存在的 setupwraith，顺序恢复 |
| `jm` | 主用户，实际可用的系统 `HOME.JM` 组件，原属性可读 | `persist.sys.home.selectpkg` |
| `vidaa` | 主用户，实际原 HOME 是聚好看，原属性是合法包名 | `persist.vt.use_home_pkg` |

```sh
python3 scripts/device_adaptation.py --serial "$TV_SERIAL" --user "$TV_USER" --record "$NEW_RECORD_DIR" --action takeover --route verified
```

预览之后，以新目录加 `--apply`。执行先配置独立 ADB 保持，再重新核对方案；每次系统修改之前同时保存电脑日志和电视恢复记录。只执行有限的已实现步骤，逐步读回；失败保留记录，不自动重连、重放或换另一套方案。ADB 开关恢复不代表网络端口可达，重启后另验。

统一安装也支持 `--adaptation-route verified|google|jm|vidaa`，仅用于 `--goal takeover`。AI 应先检测并解释具体动作，不把“自动接管”解释为未知机型默认禁用。未指定专项路线则不禁用原桌面。需要先安装新 APK 才能检查接口时，先核验安装，专项条件不满足就停止专项操作。

恢复新旧专项记录均使用：

```sh
python3 scripts/device_maintenance.py restore --serial "$TV_SERIAL" --user "$TV_USER" --record "$NEW_RECORD_DIR"
```

预览后加 `--apply`。先恢复原包的默认启用值 `0` 或显式启用值 `1`，再恢复 HOME；中断记录只处理已尝试的步骤。当前已选择 ADB 保持时保留对应设置。原包版本、固件或其他操作改变状态时停止，不能覆盖未知现场。`configure_boot_home.py` 创建的电脑 `boot_plan` 仍按原 `--restore-record` 恢复厂商属性，再完成应用路线恢复。

## 厂商配置与历史恢复

[vendor_adaptation.py](../scripts/vendor_adaptation.py) 需要 `legacy-records-v1` 接口，默认 `inspect`，不会安装额外助手或修改设备。以下操作都先预览，再以新目录加 `--apply`。

| action | 行为与恢复 |
|---|---|
| `tcl-configure` | 从实际系统 provider 读取四类已识别策略，只增删 Quiet TV 自身的名单项，保留其他字段；保存原始 JSON，比较原值后更新并读回。`--module` 可限定一项。 |
| `tcl-restore` | 用 `--from-record <原 record.jsonl>` 恢复本次电脑修改；不提供时读取旧 APK 保存的 `tclPolicies`。仅接受原值或当时写入值，厂商已改过则停止。 |
| `proxy` | 配置已经安装且由应用核实同签名的 HOME 代理。先准备原桌面回退，再由电脑设置 HOME，最后以 epoch 回执启用。中断后 `device_maintenance.py --action restore` 可处理已准备但未完成的代理。 |
| `legacy-app-restore --target <一个包名>` | 从旧 `bootAds` 或 `originalState` 恢复原启用状态，核对用户、固件、版本和现状；不删除记录或应用数据。 |
| `hisense-restore` | 仅恢复旧版在精确 MSD6A828 固件上留下的 `unhide_pending` 原隐藏状态，不把正常可见的厂商包隐藏。 |

历史 Sony 等应用管理规则仍可通过 `rules-set` 下发后使用 `device_maintenance.py --action disable/restore-app` 管理单个允许的应用。默认不执行推荐精简，不批量修改系统应用。历史卸载已经删除的数据不能恢复，安装包缺失时明确报告，不声称启用操作能恢复数据。

海信无障碍刷新、TCL 自启动 app-op、旧系统权限命令分流由 `configure_home_guard.py` 承接。HOME 代理原有修复／暂停／恢复、改包名主程序以及旧信号源映射继续保留。第三方独立中转助手需先按原流程暂停，不抢占它的路线。

## APK 精简与验证边界

运行时保留 `HomeGuardService`、开机广播、独立 ADB 设置恢复、信号源驱动、配置接收器、同签名代理协议和历史记录读取。配置页面、应用内 ADB 客户端／发现／无线配对和旧 shell 配置器退出 Java 编译及 Manifest；原源码作为迁移参考留在仓库，不批量删除。`libadb`、SPAKE2、Conscrypt、仅用于旧证书生成的 bcpkix 顶层依赖移除。更新验签仍使用 bcprov，安卓 4.4 TLS 仍保留 BCJSSE。

修改清单、规则单测、脚本模拟故障、标准版／安卓 4.4／relay 编译和 Lint，只证明相应源码与构建结果。模拟器用于接口、升级记录和恢复验证，不替代厂商实体 HOME、普通重启、冷启动、唤醒、HDMI 与网络 ADB 验收。安装后分别报告这些结果。

本地源码合并不等于工具、规则、站点或 APK 已公开可用；必须完成后续明确授权的发布，新用户才会获得对应版本。

## 电脑维护：检查、修复与恢复

新源码将守护、ADB 和系统应用禁用移出电视日常界面；旧 APK 不随文档更新获得接口。电视端“应用管理 → 更多 → 帮助与恢复”仅提供帮助、暂停接管及系统原桌面恢复入口；布局、天气、应用封面、排序和儿童模式继续在电视操作。恢复基线在安装接管前保存；历史配置缺记录时沿用电脑原记录，不能猜原值。

安装仍使用 `install_quiet_tv.py`。维护使用同一工具目录中的 `device_maintenance.py`，不重新安装、清数据或自动重启。所有命令指定已确认的电视和前台用户，`NEW_RECORD_DIR` 每次取全新的电脑私有目录。

```sh
python3 device_maintenance.py inspect --serial "$TV_SERIAL" --user "$TV_USER" --record "$NEW_RECORD_DIR"
python3 device_maintenance.py repair --serial "$TV_SERIAL" --user "$TV_USER" --record "$NEW_RECORD_DIR"
python3 device_maintenance.py restore --serial "$TV_SERIAL" --user "$TV_USER" --record "$NEW_RECORD_DIR"
```

上述三行是不同操作示例，不连续执行；除 inspect 外默认预览。已有明确修复／恢复授权时，在检查计划后用新目录加 `--apply` 执行，不反复询问同一授权。repair 复用已配置的守护并按默认维护目标开启 ADB 保持；主动暂停的接管仅在用户要求恢复后加 `--resume`。独立代理路线不自动改成内置守护。restore 自动读取并核对历史专项接管记录，按原顺序恢复对应包状态、属性和 HOME，保留已选择的 ADB 保持。记录缺失、版本或固件／用户变化时停止，不猜测原值。

单个应用禁用先预览：

```sh
python3 device_maintenance.py disable --target "$APP_PACKAGE" --serial "$TV_SERIAL" --user "$TV_USER" --record "$NEW_RECORD_DIR"
```

AI 列出返回的名称、用途、影响和恢复方法。只有用户明确选择该应用后才加 `--apply`，每次只处理一个包；失败停止后续修改。工具读取已安装 APK 内的固件规则和保护条件，拒绝原桌面、接管依赖和未确认系统包；无精确恢复命令也不禁用。此入口不卸载、不删除应用数据，不替代专用原桌面接管方案。

恢复该次禁用：

```sh
python3 device_maintenance.py restore-app --target "$APP_PACKAGE" --from-record "$DISABLE_RECORD/record.jsonl" --serial "$TV_SERIAL" --user "$TV_USER" --record "$NEW_RECORD_DIR"
```

核对后用新目录加 `--apply`。只恢复记录中的原始启用值，不能把默认启用值 0 一律改成 1。预览记录不算已执行，设备、用户、固件或版本变化时停止；中断后先读现状，不重放。所有记录留在电脑私有目录，反馈前脱敏。

维护完成后按本页验收要求分别验证实体 HOME、重启、冷启动、待机唤醒及播放／HDMI 不被打断。配置返回成功不等于这些场景通过。采用 Aerial 的 [安装](https://aerial.qust.me/agent.md)／[适配](https://aerial.qust.me/adapt.md)流程组织方式，执行仍使用 Quiet TV 的包校验、权限及固件规则；不运行 Aerial 安装脚本替换本项目配置。


统一自检、本机信号源配置、同签名 HOME 代理修复和专项恢复详见[电脑适配与维护](AGENT_ADAPT.zh-CN.md)。修复前后使用 `device_selftest.py` 的同机报告对比；默认只读，主动 HOME、信号源、待机和重启场景必须明确选择并加 `--apply`。网络 ADB 必须记录重启后的实际重连结果，不能把调试开关值当作完成。

## 遥控器按键映射

首次AI配置按[逐键校准](agent/remote.md)依次采集、试用和确认HOME、信号源、菜单键。新APK的 `remote-keymap-v1` 在本机按机型、完整固件指纹和Android用户保存；三键全部确认后才替换原映射。旧APK不具备该接口时保留原生按键，不能靠更新电脑规则创造运行能力。
