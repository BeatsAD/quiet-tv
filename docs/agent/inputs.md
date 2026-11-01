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

## 电脑测试并保存系统入口

首次安装在基础试用通过、配置 HOME 前分别检查 `settings`（完整设置）、`network`（网络设置）及 `inputs`（原厂信号源面板）。通过 `agent_session.py run` 调用下列维护操作，记录目录由任务工具分配；下面只展示底层参数。APK 必须声明 `system-entry-test-v1`，旧包缺接口时明确记录未完成，不直接写应用私有数据。

```sh
python3 device_maintenance.py entry-list --entry settings --serial "$TV_SERIAL" --user "$TV_USER" --record "$LIST_DIR"
python3 device_maintenance.py entry-test --entry settings --from-entry "$LIST_DIR/entry.json" --serial "$TV_SERIAL" --user "$TV_USER" --record "$TEST_DIR" --apply
python3 device_maintenance.py entry-status --entry settings --from-entry "$TEST_DIR/entry.json" --serial "$TV_SERIAL" --user "$TV_USER" --record "$STATUS_DIR"
python3 device_maintenance.py entry-confirm --entry settings --from-entry "$TEST_DIR/entry.json" --worked yes --observed "实际结果摘要" --evidence-source user --serial "$TV_SERIAL" --user "$TV_USER" --record "$CONFIRM_DIR" --apply
```

`entry-list` 只读取当前应用允许调用的内置候选，按当前有效的成功记忆排序，不打开页面。`entry-test` 默认测试优先候选，可用 `--candidate` 明确选择列表中的其他 ID；省略 `--apply` 只预览。测试会进入 Quiet TV，并复用首页的壁纸退出、生命周期取消和系统入口调用路径。只测试选定候选，同步失败也不偷偷改测另一项；请求最多保留30秒，派发后的确认十分钟内有效，进程重启不恢复未完成测试。

工具短时读取派发结果并生成 `entry.json`；`DISPATCHED_UNVERIFIED` 或窗口出现均不算功能成功。Agent 能取得实际截图／界面证据时，以 `--evidence-source agent` 记录真实结果；物理切源的画面和声音仍需用户确认。其他情况下询问用户实际页面，以 `user` 记录。失败用 `--worked no` 明确拒绝，再从新列表中选择下一候选，不能连续盲开所有系统页面。`--observed` 只写功能摘要，不含账号、SSID 或页面私人信息。

确认成功复用设置／网络的原有成功记忆，二者独立；网络回退到完整设置主页不能确认成网络成功。信号源只记住经过确认的原厂面板，首页优先采用仍可访问的已确认面板，不覆盖端口的隐藏、名称、映射及开机 HDMI 设置。原厂面板出现不等于某个 HDMI 已有画面，不将其记作开机直达端口。固件、API、协议参数或目标系统包变化后不再使用过期选择。

安装任务默认包含这三项验收。通过任务工具执行的 `entry-test` 只记待确认，`entry-confirm` 成功并读回后才归入对应功能的 PASS，失败确认记 FAIL。无原厂面板但已有标准输入可实际使用的设备，沿用 `input-list`、`input-open` 和实际画面验收，在任务中确认 `inputs`，不要求安装额外面板。没有候选时，电脑 Agent 核对实际系统包、原厂页面及诊断；仅使用现有协议或本机信号源配置解决。缺新协议或权限受限时，保存原因并记录 LIMITED／FAIL，不猜写组件、不修改原桌面来试错。

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
| 老极米 | API14～27；启用、导出且允许访问的系统 `com.xgimi.xhplayer/.InputActivity`；可读取 `GimiConfigManager.getXgimiConfig("global")` | 仅取产品 `signal` 配置中的 HDMI，不使用 MStar 通用枚举补口；传 `launcher_start_source`，新 GMUI 保留原有面板路径 |
| 海信 Jamdeo | API14+；可访问的系统 `com.jamdeo.tv.service/.SourceService` 与 `com.hisense.tv.tiletransition/.TileTransitionService`；可读取 `TvManager` 的 SourceManager | 从实际返回 ID 和原生 HDMI 名称读取；按 `SILO_SWITCH_EVENT` / `silo_input_src_id` 协议发给原厂转换服务所在包，不直接拉起 LiveTV 冒充切源 |
| 小米原生记录 | API14+；当前固件的版本 2 记录、MiTV 型号和可调用的系统播放器 | 使用记录中的 source，发送 `EXTSRC_PLAY`；原生记录路径带 `CLEAR_TOP`，避免复用旧任务丢掉端口参数 |

海信管理器只持有应用 Context，按固件及两个系统包的版本隔离；初始化投递到主线程，主线程不等待。后台最多等初始化 2 秒及首批端口就绪 1 秒，中断会结束等待，不创建无限重试任务或注册额外监听。维护读取也在后台等待；超时仍未就绪时可由用户重新检测。现有创维、康佳可返回端口时优先保留，不混合无法核对硬件身份的两套厂商列表。

Android 4.x 的“看电视 → 电视信号源”可选择并保存厂商接口实际返回的 HDMI，或经过核对的版本 2 小米原生端口记录；不加载 API21 标准输入框架。隐藏、改名、固件和组件版本校验继续生效，不接受标准输入映射。首页仍保留原厂面板；只有面板而没有明确端口的设备不能保存指定 HDMI。选择后可立即观看，确认画面和声音后才可开启开机自动进入。此能力尚待对应固件真机验收。

各档位在“电视信号源”未发现可直达的 HDMI 时，列出本机已启用、导出且有调用权限的原厂面板。TCL 的快捷面板与设置面板可分别手动尝试；首页无输入时也提供这两个入口的选择，已有明确的面板优先规则继续沿用。面板请求只标记派发，不保存为具体 HDMI、不产生画面声音确认、不启用开机直达，失败或返回后不自动调用下一面板。没有可用面板时说明原因并提示使用遥控器信号源键。此入口修复不包含后台 HDMI 音频释放，实际面板和物理切源仍待目标固件验收。

安卓 4.x 的电脑维护 `input-list` 返回 `scope=native_hdmi`、`standardSupported=false`，可用 HDMI 位于 `vendorSources`，与“看电视”的可选目标一致。`input-open` 仅接受这些可见且身份唯一的目标；请求绑定当时的固件／组件范围，桌面派发前再次检查。配置能力声明包含 `input-profile-v2`。列表为空不代表电视没有接口，ATV／DTV／AV 和仅有原厂面板的设备不在此直接切换范围内。派发或取消回执仍不证明物理画面和声音正常。

协议依据为 Aerial 固定提交 `587a2849c58b27dc5854a18eab3b458e1d072b34` 的 [HisenseSources](https://github.com/pengchujin/aerial-tv/blob/587a2849c58b27dc5854a18eab3b458e1d072b34/app/src/main/java/app/orchard/tv/system/HisenseSources.kt)、[XgimiMstarSources](https://github.com/pengchujin/aerial-tv/blob/587a2849c58b27dc5854a18eab3b458e1d072b34/app/src/main/java/app/orchard/tv/system/XgimiMstarSources.kt) 与 [小米原生输入](https://github.com/pengchujin/aerial-tv/blob/587a2849c58b27dc5854a18eab3b458e1d072b34/app/src/main/java/app/orchard/tv/system/SystemState.kt)。运行时按组件、API 和协议能力匹配，不将整品牌视为同一固件。上游报告与本地夹具测试不替代 Quiet TV 的厂商实机验收。

## 异步调用与结果确认（第三批开发源码）

首页列表、手动切源、已保存 HDMI、维护 `input_open` 和“看电视”页复用后台准备路径。厂商枚举、频道查询和创维 Provider 切源不在主线程执行；活动、服务及广播在准备完成后由前台路径派发。派发前复查配置、隐藏项、固件、相关包版本和组件权限，不在主线程重新枚举。原厂 DTV 入口可用时先直接调用，仅在明确失败后查询标准频道。

首页的 Back、HOME、新 Intent、失焦、暂停、停止、熄屏和销毁会中断等待，并用请求代次丢弃迟到结果；“看电视”页退出及销毁同样取消。已送入厂商进程的调用不能保证撤回，中断不等于物理切换失败。维护输入／配置命令使用异步广播结果；`lastInput` 按 request 隔离，取消后的 `cancelled_unverified` 不证明切源成功或失败，`physicalVerified` 仍为 false。未完成请求不持久重放。保存 HDMI 的原有 JSON 格式不变。

长按首页网络／系统设置可进行显式入口测试，返回后再次长按同一入口确认，十分钟内有效；活动重建不会恢复未确认的设置测试。只有用户确认对应功能正常后才保存成功路径。网络请求降级到系统设置首页时只提供有限入口，不允许确认成网络页；历史上误记的此类记录保留，但不会参与网络入口的优先排序。系统接受启动为 `DISPATCHED_UNVERIFIED`；已有授权的页面观察最多提升到 `WINDOW_OBSERVED`，窗口存在不等于设置功能正常。失败、返回、短暂失焦、超时及没有观察能力均不会自动启动下一入口。

成功记录只保存内置路由的摘要与环境绑定，不保存可执行 Intent。每次使用重新解析允许调用的系统候选，再按记录排序；网络连接为以太网与其他情况分别记忆。固件、API 或目标／已知关联包版本变化会停止使用旧优先级，旧记录保留供重新确认。明确派发失败可以尝试当前候选链后续入口；派发后结果未知时只能由用户主动试下一入口。

| 档位 | 共同能力 | 差异 |
| --- | --- | --- |
| Android 5+ | 异步准备与取消、设置测试和路径记忆 | 标准输入与已匹配厂商原生端口，支持保存 HDMI |
| Android 4.x | 原厂面板、厂商 HDMI 后台发现与取消、设置测试和路径记忆 | 不加载 API21 输入框架；明确的原生 HDMI 可保存、测试并经确认用于开机进入，缺少端口接口时保留面板／遥控器路径 |

本地 JVM 测试只验证线程、生命周期、路由及状态规则。真实厂商 SDK 的耗时、窗口表现、HDMI 画面／声音及开机／待机行为仍须逐固件验收。
