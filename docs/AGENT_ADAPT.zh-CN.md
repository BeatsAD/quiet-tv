# Quiet TV 电脑适配与维护

[安装与更新](AGENT_INSTALL.zh-CN.md)  [只读问题排查](AGENT_DIAGNOSE.zh-CN.md)  [配置与恢复](SETUP.md)

本流程让电脑端 AI 完成检测、配置、复测和报告，电视只保留日常使用入口。安装最新适配版见安装说明；已有安装先检查，不重复安装或重新初始化。以当前设备、实际 APK 能力和用户请求为准，不能把本地源码能力当成公开 APK 已支持。

本轮工具和规则上线不包含新 APK 发布；旧版仍按实际接口分流。没有电脑维护接口时先做系统只读检查并保留旧恢复记录，不运行依赖该接口的修复或专项接管，详见[当前交付范围](AGENT_INSTALL.zh-CN.md#当前交付范围)。

## 工具与授权

从同一次实时 [latest.json](https://beatsad.github.io/quiet-tv/latest.json) 的 `tools` 下载全部十一项工具与规则到同一私有目录，逐个核对 SHA-256；源码工作区直接使用 `scripts/`。缺项时报告站点尚未部署，旧 APK 缺接口时报告需要包含该功能的新包。维护能力由 `inspect.capabilities` 返回，不能绕过缺失的接收器改写应用私有文件。

每次明确目标 `--serial`、当前 `--user` 和实际 `--package`，记录目录的父目录须存在且目录本身必须全新。下方命令是不同操作的示例，不连续执行，不把示例变量或设备值照抄为真实参数。Windows 使用对应终端变量语法。

“检查”只读；“修复”可执行与故障有关且可恢复的配置。既有授权不重复询问。切换画面、待机和整机重启须属于用户已授权范围；默认只读报告不触发这些操作。应用禁用按用户明确选择逐个处理，不作为安装、守护或自检的默认步骤。所有原始日志和截图留在电脑私有目录，公开反馈前脱敏。

## 检查、修复、复测

1. 运行 `device_selftest.py` 生成修复前的 JSON、Markdown 和原始命令记录；记录 APK 版本、固件、用户、当前 HOME、原桌面、守护和 ADB 状态。
2. 按故障选择 `device_maintenance.py repair`、`restore` 或本机信号源配置，先预览，再用新记录目录加 `--apply`。沿用已选择的直接 HOME、中转或同签名 HOME 代理。主动暂停的接管只有用户要求恢复时才加 `--resume`。
3. 使用同一自检工具和相同场景复测，以 `--compare` 指向修复前的 `report.json`。对比只读取结果，不从报告执行命令；设备、连接、固件、用户或包名不符则拒绝对比。
4. 报告分别列配置、模拟按键、实体按键、重启、网络 ADB、冷启动、待机、信号源画面；失败或未测不能写成全面通过。

```sh
python3 device_selftest.py --serial "$TV_SERIAL" --user "$TV_USER" --record "$BEFORE_DIR"
python3 device_maintenance.py repair --serial "$TV_SERIAL" --user "$TV_USER" --record "$PREVIEW_DIR"
python3 device_maintenance.py repair --serial "$TV_SERIAL" --user "$TV_USER" --record "$APPLY_DIR" --apply
python3 device_selftest.py --serial "$TV_SERIAL" --user "$TV_USER" --record "$AFTER_DIR" --compare "$BEFORE_DIR/report.json"
```

默认 ADB 保持独立于守护暂停、恢复原桌面和儿童模式。安卓5及以上标准档按权限恢复调试设置；旧 TCP 端口策略仍只适用于已有小米／Redmi 路径，安卓4.4不宣称支持。Android 11+无线调试不等同于 `adb_enabled`。读回开关不等于重启后可以连接；不能仅用 USB 验证宣布网络 ADB 常开。固件实际关闭网络调试且没有已验证恢复途径时，报告目标未完成，不承诺通用绕过。

## 主动场景自检

以下参数可单独选择或组合，必须同时加 `--apply`，只执行一次有副作用的动作，不重放失败的重启或切换。

| 参数 | 自动检查 | 仍需人工确认 |
| --- | --- | --- |
| `--home-tests` | 桌面模拟 HOME、从实际设置应用模拟 HOME，观察 8 秒前台 | 实体遥控器 HOME 与实际画面 |
| `--inputs` | 枚举可见信号源，调用桌面同一打开路径，记录请求回执和截图 | 输入名称、物理接口、真实视频与返回行为 |
| `--standby` | 发送睡眠、等待 70 秒、唤醒并观察前台 | 固件实际待机深度、遥控器唤醒、HDMI／播放不被打断 |
| `--reboot` | 整机重启、最多等待 150 秒重连和开机完成，再观察 60 秒前台 | 实际电视画面和之后的冷启动 |

```sh
python3 device_selftest.py --serial "$TV_SERIAL" --user "$TV_USER" --record "$SCENE_DIR" --home-tests --reboot --apply
```

重启后先核对硬件、固件、用户、启动标识和开机完成，再读取 ADB 开关与前台时间线，之后才查询应用接口，避免查询先启动应用而掩盖开机恢复缺陷。仅向用户选择的原 IP:端口重连，不扫描、猜端口或重新配对。动态无线标识、USB、模拟器连接不作为传统网络重连证据。ADB 断连时如实记录失败；待机后会提示用户遥控器唤醒，最多等 90 秒，不反复切电源。冷启动由人工断电验收，脚本不模拟。

`PASS` 仅针对该行证据，`FAIL` 表示失败，`SKIP` 表示未测或证据不足。信号源 `dispatched` 只证明打开请求已发出，即使有截图也不会自动判画面正确。实体 HOME 和冷启动固定留待人工报告补充。

## 本机信号源配置

电脑工具可读写本机配置，电视没有技术配置页面。仅支持安卓5及以上系统实际枚举出的系统直通信号源。完整 ID 必须来自本机 `input-list`；按完整固件指纹隔离，固件改变后配置保留但停止生效。修改前将 `profile-get` 输出保存为恢复依据；修改、重置均校验 revision，状态变化则停止。

```sh
python3 device_maintenance.py input-list --serial "$TV_SERIAL" --user "$TV_USER" --record "$INPUT_DIR"
python3 device_maintenance.py profile-get --serial "$TV_SERIAL" --user "$TV_USER" --record "$PROFILE_DIR"
python3 device_maintenance.py profile-set --profile "$PROFILE_JSON" --serial "$TV_SERIAL" --user "$TV_USER" --record "$PREVIEW_DIR"
python3 device_maintenance.py profile-set --profile "$PROFILE_JSON" --serial "$TV_SERIAL" --user "$TV_USER" --record "$APPLY_DIR" --apply
```

JSON 格式（示意值不能直接用于设备）：

```json
{
  "schemaVersion": 1,
  "fingerprint": "本机完整 ro.build.fingerprint",
  "hidden": ["本机待隐藏的完整信号源ID"],
  "labels": {"本机完整信号源ID": "HDMI 1"},
  "routes": {"本机完整信号源ID": "本机另一个完整信号源ID"},
  "xiaomiSources": {}
}
```

- `hidden` 额外隐藏重复或无效入口，不恢复系统隐藏项，不禁用任何系统包。
- `labels` 覆盖名称，最多 40 字符。
- `routes` 修正入口对应的本机信号源，只允许一跳，不允许自引用、环或任意组件／命令。
- `xiaomiSources` 仅在本机存在可调用的小米系统播放器时用于 HDMI，值为整数 19～32，同一 ID 不能同时使用 `routes`。数值必须由本机画面测试确认，不能套用其他电视。

字段可省略；只含 schemaVersion 与 fingerprint 的配置不会添加覆盖。`profile-set` 替换整份配置，不合并；应用拒绝未知字段、未知 ID 或无效值后保持旧配置。修改后重新打开信号源面板并复测。恢复之前的非空配置使用原 JSON；清除本机覆盖用 `profile-reset`，仍先预览再加 `--apply`。清除只恢复内置规则，不删除其他设置。

单个画面测试使用 `input-open --source "$INPUT_ID"`，预览后在切换画面授权下加 `--apply`，再读 `profile-get` 的 `lastInput`，核对 request 和 result。请求不会持久保存为重试队列。

## 原桌面与 HOME 代理恢复

先区分恢复记录来源：应用保存的专项接管记录由下述 `device_maintenance.py restore` 处理；`configure_boot_home.py` 或统一安装产生的 `boot_plan` 保存在电脑原始 `record.jsonl`。后者先暂停接管，再按[开机桌面恢复步骤](AGENT_INSTALL.zh-CN.md#ai-实际调用的开机桌面脚本)使用 `--restore-record` 撤销本次 HOME／海信属性，最后运行维护工具核对并完成恢复状态。不能只恢复系统 HOME 就宣布海信属性也已恢复；这两种恢复均保留已选择的 ADB 保持。

`restore` 优先读取应用保存的专项接管记录，核对设备、用户、目标、原包版本和当前状态。它只导出已尝试过的原记录步骤，按“恢复属性／包状态、恢复 HOME、最后恢复需撤销的权限”的顺序执行，每步先留记录再修改、随后读回。默认启用值 0 与显式启用值 1 不混同。

当前已选择保持 ADB 时，恢复原桌面会保留相关权限、端口和 ADB 选项，在 `retainedAdbSteps` 中列出；其他变更按原记录恢复。属性以电脑 shell 读回为准，应用能读取时还会复核。未知记录、原包版本改变或第三方状态改变时停止，不猜原值，不通过新禁用操作修复。

同签名 HOME 代理的 `repair` 调用现有代理恢复协议，不改成内置中转；暂停后的代理也可按明确 `--resume` 请求恢复。`restore` 在暂停代理后将系统 HOME 恢复到原记录并读回。最终均需实体 HOME、重启和待机验收。旧记录缺失时保留现状，按安装时的原始电脑记录排查。

## 交付状态

本文件、站点 `adapt.md`、工具包和安装 skill 共用同一流程。站点构建会发布十一项工具与规则的真实 SHA-256，维护和自检脚本变更会触发 Pages。源码修改、工具上线、APK 发布和真机验收分别记录；站点上线不能让旧 APK 获得新接口。TCL relay 继续核对实际 APK 中不声明 HOME、包名、版本、架构和签名；没有已发布的匹配 relay 时不假装存在下载包。

本流程参考 [Aerial 适配指南](https://github.com/pengchujin/aerial-tv/blob/main/site/adapt.md) 的检测、修复、复测方式，执行使用 Quiet TV 自有接口和恢复记录。

机型规则已迁入电脑工具目录，专项接管、规则下发、TCL 策略、HOME 代理和旧记录恢复按[电脑 AI 适配规则与恢复](DEVICE_ADAPTATION.zh-CN.md)执行。先检查 APK 能力，再选有限路线；普通接管不默认禁用原桌面。
