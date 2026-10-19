# 本地配置与记录样例 / Local examples

这些文件是脱敏的结构示例，不包含设备授权、禁用建议或可用的恢复基线。
These redacted examples describe file structure, not authorization, debloating advice, or a usable recovery baseline.

| 样例 / Example | 用途 / Purpose |
| --- | --- |
| [device.example.json](device.example.json) | 本地 ADB 与 SDK 配置 / Local ADB and SDK settings |
| [preferences.example.json](preferences.example.json) | 自己选择的应用与偏好 / User-selected apps and preferences |
| [changes.example.json](changes.example.json) | 变更日志字段 / Recovery journal fields |
| [backup-current.example.json](backup-current.example.json) | 基线引用字段 / Backup-reference fields |
| [package-inventory.example.csv](package-inventory.example.csv) | 包清单列与占位记录 / Inventory columns and a placeholder row |
| [PACKAGE-INVENTORY.example.md](PACKAGE-INVENTORY.example.md) | 分类说明样式 / Classification format |

## 配置自己的电视 / Configure your TV

仅在没有 `device.json` 时复制设备样例，随后填写自己的目标与 SDK 路径：
Copy the device example only when no local configuration exists, then fill in your own target and SDK paths:

```sh
cp -n docs/examples/device.example.json device.json
```

样例中的 `192.0.2.10` 是文档占位地址。执行设备操作前核对实际型号、连接目标与用户 ID。`device.json` 被 Git 忽略；构建和离线测试不需要配置或连接电视。

The example address is documentation-only. Verify the actual model, target, and user ID before device operations. `device.json` is ignored by Git; builds and offline tests do not require it or a TV connection.

已有设备的配置、偏好和恢复记录留在本地原位，不能用这些样例覆盖。不要直接复制日志样例执行恢复；真实日志须对应目标设备的原始备份。历史公开提交可能仍包含旧记录，本次整理不改写 Git 历史。

Preserve existing local settings, preferences, and journals. Do not copy example journals to perform a restore: real journals must reference that target's original backup. Historical public commits may still contain earlier records; this organization change does not rewrite history.
