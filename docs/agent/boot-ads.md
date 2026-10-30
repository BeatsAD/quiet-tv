# 按需关闭开机广告

从[统一入口](../AGENT.md)进入。只有用户明确要求“关闭开机广告”或“恢复广告修改”时才执行；检查保持只读。它独立于安装和 HOME 接管，不要求先切换或停用原桌面。用户已选择此目标后，已授权的预览、执行与读回连续完成；重启、断电及待机按用户实际授权安排。

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

`inspect` 分别列出匹配的执行规则和调查候选，不把同品牌参考提升为执行资格。当前源码没有经 Quiet TV 验收的广告执行规则，七组 GitHub 线索仅供调查。无规则、相关包版本不符、固件拒绝或信息无法确认时保持只读并记录 LIMITED，继续官方路线，不改 JSON 绕过保护。

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

在 `scripts/device-presets.json` 的精确 `devices` 中，设置／属性操作使用 `bootAds` 数组；每项须有 `id`、`package`、数字 `versionCode`、`purpose`、`impact`、HTTPS `source`、本项目 `evidence` 和 `verified: true`。独立广告包沿用 `apps` 的 `category: bootAd`，同样要求版本、来源、本项目验收及恢复证据，只允许 ENABLE／DISABLE 且不默认推荐。原来的 APK 内置规则不扩充。

先取得完整品牌、型号、固件指纹、相关包版本、冷启动和恢复证据，再评审加入执行规则。GitHub 原作者成功、单次服务状态、同包名或截图均不能代替本项目验收。参考线索位于 `references`，不能携带 `bootAds`、`fingerprint`、`apps` 或 `home` 执行字段。调查优先级为小米设置项、TCL 独立服务、创维天赐广告服务；其他路线继续取证。

上游小米 ANSP0 的“屏蔽域名＋清空原桌面数据”不是本流程；创维原桌面参与启动链也不应被当作广告包停用。海信旧固件存在广告设置无效、停用被拒绝的反例；海尔记录明确未验证开机广告效果。不能导入整份精简清单或使用通用 restore 脚本扩大恢复范围。

## 分项验收

任务分别要求 `ad_configuration`、`ad_reboot`、`ad_cold_boot`、`ad_wake`、`ad_recovery`。配置和恢复读回由工具记录，广告画面由用户实际观察，三个电源场景的 PASS 不能由 Agent 代填。官方开关／客服路线的实际结果可用 accept 登记并注明来源。保留 HDMI 影音、遥控器、设置和网络的相关回归证据；未做的项目保持待确认，不能写全面通过。原值已恢复不等于用户画面已确认。

恢复广告修改只恢复本功能原值，不恢复原桌面，不重新启用守护，也不改变既有 ADB 保持选择。最终按[验收说明](acceptance.md)分别交付配置、效果、恢复、受限项及私有记录位置。
