# 本机信号源配置

[维护指南](../AGENT_ADAPT.zh-CN.md)  [统一入口](../AGENT.md)

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
