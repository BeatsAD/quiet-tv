# Quiet TV 电脑维护指南

从[统一入口](AGENT.md)进入。本页用于已有安装的适配与修复；不重复安装或初始化。先读[连接与任务记录](agent/connect.md)，保留原接管路线、暂停选择和恢复记录。

## 工具与授权

从同一次实时 [latest.json](https://beatsad.github.io/quiet-tv/latest.json) 的 `tools` 下载索引列出的全部工具与规则到同一私有目录，逐个核对 SHA-256；源码工作区直接使用 `scripts/`。缺项时报告站点尚未部署，旧 APK 缺接口时报告需要包含该功能的新包。维护能力由 `inspect.capabilities` 返回，不能绕过缺失的接收器改写应用私有文件。

每次明确目标 `--serial`、当前 `--user` 和实际 `--package`，记录目录的父目录须存在且目录本身必须全新。下方命令是不同操作的示例，不连续执行，不把示例变量或设备值照抄为真实参数。Windows 使用对应终端变量语法。

“检查”只读；“修复”可执行与故障有关且可恢复的配置。既有授权不重复询问。切换画面、待机和整机重启须属于用户已授权范围；默认只读报告不触发这些操作。应用禁用按用户明确选择逐个处理，不作为安装、守护或自检的默认步骤。所有原始日志和截图留在电脑私有目录，公开反馈前脱敏。

## 检查、修复、复测

1. 运行 `device_selftest.py` 生成修复前的 JSON、Markdown 和原始命令记录；记录 APK 版本、固件、用户、当前 HOME、原桌面、守护和 ADB 状态。
2. 通过任务工具 `plan --route repair --issue <领域> --note <证据>` 固定问题范围。home／adb使用对应配置修复，inputs使用本机信号源配置；设置、网络、按键和外设先从真实入口取证，不调用HOME／ADB repair代替定位。现有能力无法处理时记录LIMITED及替代办法。沿用原路线和暂停选择；明确恢复原桌面另用restore意图。
3. 使用同一自检工具和相同场景复测，以 `--compare` 指向修复前的 `report.json`。对比只读取结果，不从报告执行命令；设备、连接、固件、用户或包名不符则拒绝对比。
4. 报告分别列配置、模拟按键、实体按键、重启、网络 ADB、冷启动、待机、信号源画面；失败或未测不能写成全面通过。

按原问题复测，用 `accept --check repair --status <实际结果> --note <证据>` 登记；其余必需项通过 `plan --checks` 增加。最后finish汇总，不把命令0当成修复完成。外设按实际现场发现，不限品类，也不因未接入设备扩展成全部必测。

```sh
python3 device_selftest.py --serial "$TV_SERIAL" --user "$TV_USER" --record "$BEFORE_DIR"
python3 device_maintenance.py repair --serial "$TV_SERIAL" --user "$TV_USER" --record "$PREVIEW_DIR"
python3 device_maintenance.py repair --serial "$TV_SERIAL" --user "$TV_USER" --record "$APPLY_DIR" --apply
python3 device_selftest.py --serial "$TV_SERIAL" --user "$TV_USER" --record "$AFTER_DIR" --compare "$BEFORE_DIR/report.json"
```

默认 ADB 保持独立于守护暂停、恢复原桌面和儿童模式。安卓5及以上标准档按权限恢复调试设置；旧 TCP 端口策略仍只适用于已有小米／Redmi 路径，安卓4.4不宣称支持。Android 11+无线调试不等同于 `adb_enabled`。读回开关不等于重启后可以连接；不能仅用 USB 验证宣布网络 ADB 常开。固件实际关闭网络调试且没有已验证恢复途径时，报告目标未完成，不承诺通用绕过。

## 按需进入专项步骤

- [验收与主动场景自检](agent/acceptance.md)：仅按授权选择实体 HOME、重启、待机等场景。
- [HOME、守护和 ADB](agent/home.md)：已有路线缺配置或 ADB 保持异常。
- [信号源](agent/inputs.md)：只在信号源名称、路由或显示异常时读取。
- [规则与历史配置](DEVICE_ADAPTATION.zh-CN.md)：命中专项固件、代理或旧记录时读取。
- [恢复原桌面](agent/restore.md)：仅在明确恢复请求时进入。

所有执行通过[任务记录工具](agent/session.md)分配新记录目录。中断先 `resume`，不重放历史命令。1.3.0 已提供配套 APK，仍须先检查实际接口与能力；旧包缺接口时只执行其支持的系统检查或既有流程，不改写私有文件。

## 交付状态

本站 `maintenance.md` 由本文件生成；旧 `adapt.md` 保留同内容兼容。工具、说明和规则按同一次索引下载并校验，下载完整包不等于把全部内容读入上下文。遵守[分项验收](agent/acceptance.md)，不能以源码存在或命令成功代替电视实测。

流程参考 [Aerial 适配指南](https://github.com/pengchujin/aerial-tv/blob/main/site/adapt.md)；执行仍使用 Quiet TV 的能力校验和原始恢复记录。
