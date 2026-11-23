# 按需关闭开机广告

从[统一入口](../AGENT.md)进入。只有用户明确要求“关闭开机广告”或“恢复广告修改”时才执行；检查保持只读。它独立于安装和 HOME 接管，不要求先切换或停用原桌面。用户已选择此目标后，已授权的预览、执行与读回连续完成；重启、断电及待机按用户实际授权安排。

## 从 Quiet TV 排查

在电视或手机扫码页进入「通用设置 → 桌面与启动 → 开机广告」。有适用规则且完成本机授权时，可在这里关闭，并在下次开机时确认广告是否消失。「恢复原设置」撤销 Quiet TV 对这项广告开关的修改。

如果仍有广告或暂不支持，可使用「电脑 AI 排查」。将这段指令交给电脑上的 AI，并说明广告是断电开机、重启还是待机唤醒时出现，以及它在 Quiet TV 首页之前还是之后播放。

> 请通过我的电脑只读排查这台电视的开机广告，核对机型、固件、广告出现阶段、相关开关和服务状态，给出适用的处理办法。

## 先确认来源与官方渠道

区分整机开机视频、原桌面推荐、屏保广告和视频 App 广告。记录出现阶段、用户画面、可取得的前台组件和相关日志；原厂 LOGO、动画或进入 Quiet TV 不证明广告已经消失。无广告时记录无需处理，不为精简而修改系统。

优先使用本机实际存在的官方关闭开关；个性化广告、广告静音、本次跳过不等于永久关闭。仅发现教程中的菜单不算本机支持。没有明确开关时，优先引导用户申请厂家关闭。

[广电总局《公共场所电视服务提升实施指南》](https://www.nrta.gov.cn/module/download/downfile.jsp?classid=0&filename=9456c6359d1f4256bbb3b8fed6e02b4b.pdf)（2025 年 12 月）附录 C 提供以下渠道。指南面向公共场所，家用受理条件、信息和生效时间仍由厂家按型号确认，不保证立即或所有机型生效。

| 品牌 | 指南所列电话 | 需准备的信息 |
| --- | --- | --- |
| 小米 | 950816 | 以太网和 Wi-Fi MAC |
| TCL | 4008123456 | 设备型号和序列号；雷鸟按实际售后渠道确认 |
| 创维 | 95105555 | 型号和 MAC；酷开按实际售后渠道确认 |
| 海信 | 4006111111 | 有线 MAC；Vidda 按实际售后渠道确认 |
| 海尔 | 4006999999 | 电视 UID |
| 长虹 | 4008111666 | 型号、系统版本、设备 MAC 和无线 MAC |
| 康佳 | 4008800016 | 机身唯一标识条码，联系当地售后确认 |
| 夏普 | 4008981818 | 型号和 MAC |
| 飞利浦 | 4008800008 | 以太网 MAC |

上述信息优先通过电视设置查看，不为申请客服强行开启 ADB 或安装 Quiet TV。AI 可在电脑私有记录中整理材料和待确认状态，由用户提交；未经单独授权不代发消息，不公开 MAC、序列号、UID。乐视按指南通过“乐视客服团长”公众号提供系统版本和 MAC，先确认当前官方渠道。

三星 Tizen、LG webOS 和非 Android VIDAA 仅提供本机官方设置／客服指导，不执行 Android ADB 命令。不能按品牌预设所有型号无广告；雷鸟也有上游处理广告服务的单机记录。

## 电脑技术路线

同批工具中的 `boot_ads.py` 默认只读；不安装 APK、不启动应用、不修改 HOME、DNS、路由器、OTA、语音或其他无关服务，不删除广告文件、不清数据、不卸载、不 Root。无需 Quiet TV 的维护广播接口；仍先按[连接说明](connect.md)确认同一电视和当前用户。

按[任务记录](session.md)建立 `--intent boot-ads`，选择 `--route boot-ads`。任务固定设备、用户与工具版本，所有技术步骤由任务工具分配全新记录目录：

```sh
python3 agent_session.py start --serial "$TV_SERIAL" --user "$TV_USER" --intent boot-ads --session "$NEW_SESSION_DIR" --bundle "$BUNDLE_DIR/latest.json"
```

随后以该目录作为 SESSION_DIR；源码工作区可以省略 --bundle：

```sh
python3 agent_session.py plan --session "$SESSION_DIR" --route boot-ads --note "用户明确要求处理开机广告及本机现场依据"
python3 agent_session.py run --session "$SESSION_DIR" --tool boot_ads -- --action inspect
```

`inspect` 分别列出匹配的执行规则和调查候选。海信和小米候选另读回对应广告开关和相关包构建号、启用状态，读回失败时标为未知。候选只供调查。电脑工具的写入操作采用 Quiet TV 验收的规则。电视端另有根据上游实测启用的具体小米／Redmi 规则。无规则、相关包版本不符、固件拒绝或信息无法确认时保持只读并记录 LIMITED，继续官方路线，不改 JSON 绕过保护。

已有精确验收规则后，每次只选择一个规则 ID；先预览，以新记录目录加 `--apply` 执行。恢复使用实际修改时保存的原始 `record.jsonl`，也先预览，再以新目录执行：

```sh
python3 agent_session.py run --session "$SESSION_DIR" --tool boot_ads -- --action disable --rule "$CONFIRMED_RULE_ID"
python3 agent_session.py run --session "$SESSION_DIR" --tool boot_ads -- --action disable --rule "$CONFIRMED_RULE_ID" --apply
python3 agent_session.py run --session "$SESSION_DIR" --tool boot_ads -- --action restore --from-record "$ORIGINAL_RECORD"
```

记录保存当次规则快照、设备／固件／用户／硬件标识、包版本、原值、修改尝试及读回结果。恢复从快照重新生成固定命令，不执行日志中的命令；预览记录不能作为实际修改的恢复基线。断连或中断先 resume、检查原记录和现状，再显式恢复，不重放。未知的第三种状态不覆盖。

## 已实现操作与规则准入

| 操作 ID | 范围与恢复 |
| --- | --- |
| `xiaomi-boot-ad-switch` | `com.mitv.tvhome` 精确版本对应的 System `boot_ad_switch=0`；记录键是否存在，恢复原值或原来的不存在状态 |
| `hisense-ad-block` | `com.hisense.service.ad` 精确版本对应的 Global `com.hisense.service.ad_block=1`；仅主用户，恢复原值或不存在状态 |
| `skyworth-bootvideo-time` | `com.skyworth.tv` 精确版本对应的 `persist.sys.bootvideo.time=1`；仅主用户，原属性须存在且为可恢复数字，否则不写入 |
| `disable:<广告包名>` | 仅支持已实现的 TCL、天赐、海信及 YunOS 独立广告服务；核对系统命令，分别恢复默认启用 `0` 或显式启用 `1`，不一律 enable |

在 `scripts/device-presets.json` 的精确 `devices` 中，设置／属性操作使用 `bootAds` 数组；每项须有 `id`、`package`、数字 `versionCode`、`purpose`、`impact`、HTTPS `source`、本项目 `evidence` 和 `verified: true`。独立广告包沿用 `apps` 的 `category: bootAd`，同样要求版本、来源、本项目验收及恢复证据，只允许 ENABLE／DISABLE 且不默认推荐。APK 内置验收通过的系统开关规则，也读取 `upstreamBootAds` 中由上游实测支持的指定小米／Redmi 开关规则。属性和独立服务操作保留在电脑工具中。

上游规则只选择 `xiaomi-boot-ad-switch`，以完整固件指纹，或系统设备代号与精确固件版本匹配，并核对实测的 PatchWall 显示版本。原值保存在修改当时的固件和实际包构建号下。电视和扫码页在关闭前显示上游实测提示。

| 机型 | 固件 | PatchWall | 上游记录 |
| --- | --- | --- | --- |
| 小米 L43M7-EA（MiTV-ANTL0） | `23.2.4.2225` | `3.38.1017` | [完整重启广告消失](https://github.com/Gathin23/cleanyourtv/blob/d4ac32c344987ae4e8faf1ee32d8b9572fb57fe3/docs/devices/xiaomi-l43m7-ea-antl0.md) |
| Redmi MiTV-MFTP0 | `23.2.10.6129` | `3.49.1601` | [完整重启广告消失](https://github.com/Gathin23/cleanyourtv/blob/main/examples/redmi-mitv-mftp0/debloat.txt#L36-L39) |
| Redmi L65R6-A（MiTV-MTTP0） | `21.4.9.2585` | `3.50.1621` | [机主反馈关机再开机无广告](https://github.com/Gathin23/cleanyourtv/issues/1063) |

新增 Quiet TV 验收规则时，先取得完整品牌、型号、固件指纹、相关包版本、冷启动和恢复证据。上游规则的来源保留在规则记录和确认提示内。参考线索位于 `references`，不能携带 `bootAds`、`fingerprint`、`apps` 或 `home` 执行字段。调查优先级为小米设置项、TCL 独立服务、创维天赐广告服务；其他路线继续取证。

上游小米 ANSP0 的“屏蔽域名＋清空原桌面数据”不是本流程；创维原桌面参与启动链也不应被当作广告包停用。海信旧固件存在广告设置无效、停用被拒绝的反例；海尔记录明确未验证开机广告效果。不能导入整份精简清单或使用通用 restore 脚本扩大恢复范围。

## 分项验收

任务分别要求 `ad_configuration`、`ad_reboot`、`ad_cold_boot`、`ad_wake`、`ad_recovery`。配置和恢复读回由工具记录，广告画面由用户实际观察，三个电源场景的 PASS 不能由 Agent 代填。官方开关／客服路线的实际结果可用 accept 登记并注明来源。保留 HDMI 影音、遥控器、设置和网络的相关回归证据；未做的项目保持待确认，不能写全面通过。原值已恢复不等于用户画面已确认。

恢复广告修改只恢复本功能原值，不恢复原桌面，不重新启用守护，也不改变既有 ADB 保持选择。最终按[验收说明](acceptance.md)分别交付配置、效果、恢复、受限项及私有记录位置。
