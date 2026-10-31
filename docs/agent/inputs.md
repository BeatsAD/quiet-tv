# 本机信号源配置

[维护指南](../AGENT_ADAPT.zh-CN.md)  [统一入口](../AGENT.md)

## 本机信号源配置

电脑工具可读写本机配置，电视没有技术配置页面。安卓5及以上支持标准系统直通信号源；声明 `input-profile-v2` 能力的版本还支持下述原生端口与厂商对应关系。标准完整 ID 必须来自本机 `input-list`；按完整固件指纹隔离，固件改变后配置保留但停止生效。修改前将 `profile-get` 输出保存为恢复依据；修改、重置均校验 revision，状态变化则停止。

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

## 原生端口与对应关系（版本 2）

当前开发源码保留版本 1 的读取与写入格式。版本 2 在上述字段基础上增加 `nativeSources`、`vendorAliases`、`packageVersions`，不接受任意组件、Intent、广播或 shell 命令。

- `input-list.sources` 是标准输入，`vendorReported` 是厂商接口原始发现的端口（含被合并或隐藏的项），`vendorSources` 是合并后可选的厂商端口。新增协议均不把“请求发出”标记为画面或声音验证成功。
- `vendorAliases` 用厂商端口 ID 作键、同类型标准物理端口 ID 作值，要求一一对应；CEC 子输入不能代替物理端口，也不能与 `routes` 重映射或显式小米编号冲突。对应关系必须有本机端口证据，不能凭显示名称填写。同类型中每个已登记的标准端口都能对应到实际枚举的厂商端口后，才补出其余厂商端口。对应不全、厂商 ID 有歧义或配置过期时保留保守去重规则。
- 版本 2 的 `hidden` 和 `labels` 也接受实际枚举出的厂商 ID。隐藏标准端口不会通过它的厂商别名恢复；隐藏已对应的厂商端口，也隐藏标准端口及 CEC 子输入。`routes` 和 `xiaomiSources` 仍只接受标准输入 ID。
- `nativeSources` 目前仅支持小米电视已核对的原生 HDMI 记录：每项只能含 `protocol: "xiaomi"`、整数 `port`（物理口 1～4）、整数 `source`（播放器协议参数 19～32）。物理口与 source 均不得重复。这些范围是参数检查，不代表设备实际具有对应接口，也不代表两种编号相同。必须匹配当前固件、MiTV 型号和可调用的原厂系统播放器；盒子和仅装有同名第三方包的设备不会获得输入端口。没有记录就不生成端口，不把 `hdmiN_order` 当作物理端口列表。
- `packageVersions` 是应用保存时生成的相关系统包版本与更新时间绑定。首次提交可省略，电脑工具只允许这一字段在读回时由应用补充；端口记录及其他字段必须完全一致。导出的字段重新提交时必须与本机一致。固件或绑定的包版本变化后，配置显示 `stale`，原记录和 revision 保留；重新核对端口后才重建绑定，不自动复用旧确认。

以下仅展示结构，示例值不是任何设备的已验证映射，不能直接应用：

```json
{
  "schemaVersion": 2,
  "fingerprint": "本机完整 ro.build.fingerprint",
  "nativeSources": [{"protocol": "xiaomi", "port": 1, "source": 23}],
  "vendorAliases": {"xiaomi:23": "本机标准物理端口完整ID"}
}
```

没有标准输入的电视可以省略 `vendorAliases`，但仍须提供已经核对的 `nativeSources`。维护工具的 `input-open` 也支持可见的厂商 ID，调用前及桌面接收请求后会重新检查；已隐藏或已消失的输入不再执行。旧 APK 不声明版本 2 能力时，电脑工具拒绝写入版本 2。

## 已实现的厂商读取协议

| 路径 | 运行时启用条件 | 端口和调用来源 |
| --- | --- | --- |
| 老极米 | API21～27；启用、导出且允许访问的系统 `com.xgimi.xhplayer/.InputActivity`；可读取 `GimiConfigManager.getXgimiConfig("global")` | 仅取产品 `signal` 配置中的 HDMI，不使用 MStar 通用枚举补口；传 `launcher_start_source`，新 GMUI 保留原有面板路径 |
| 海信 Jamdeo | API21+；可访问的系统 `com.jamdeo.tv.service/.SourceService` 与 `com.hisense.tv.tiletransition/.TileTransitionService`；可读取 `TvManager` 的 SourceManager | 从实际返回 ID 和原生 HDMI 名称读取；按 `SILO_SWITCH_EVENT` / `silo_input_src_id` 协议发给原厂转换服务所在包，不直接拉起 LiveTV 冒充切源 |
| 小米原生记录 | API21+；当前固件的版本 2 记录、MiTV 型号和可调用的系统播放器 | 使用记录中的 source，发送 `EXTSRC_PLAY`；原生记录路径带 `CLEAR_TOP`，避免复用旧任务丢掉端口参数 |

海信管理器只持有应用 Context，按固件及两个系统包的版本隔离；初始化投递到主线程，主线程不等待。后台最多等初始化 2 秒及首批端口就绪 1 秒，中断会结束等待，不创建无限重试任务或注册额外监听。首次维护读取可能尚未就绪，可重新检测。现有创维、康佳可返回端口时优先保留，不混合无法核对硬件身份的两套厂商列表。

Android 4.x 档位继续使用原有原厂信号源面板，不加载 API21 输入框架和本批原生端口配置；界面及现有面板共用主线代码。此档位的直接保存 HDMI 能力未在本批扩展。

协议依据为 Aerial 固定提交 `587a2849c58b27dc5854a18eab3b458e1d072b34` 的 [HisenseSources](https://github.com/pengchujin/aerial-tv/blob/587a2849c58b27dc5854a18eab3b458e1d072b34/app/src/main/java/app/orchard/tv/system/HisenseSources.kt)、[XgimiMstarSources](https://github.com/pengchujin/aerial-tv/blob/587a2849c58b27dc5854a18eab3b458e1d072b34/app/src/main/java/app/orchard/tv/system/XgimiMstarSources.kt) 与 [小米原生输入](https://github.com/pengchujin/aerial-tv/blob/587a2849c58b27dc5854a18eab3b458e1d072b34/app/src/main/java/app/orchard/tv/system/SystemState.kt)。运行时按组件、API 和协议能力匹配，不将整品牌视为同一固件。上游报告与本地夹具测试不替代 Quiet TV 的厂商实机验收。
