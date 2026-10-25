# 连接、切换与恢复

## 工具与连接

先检查当前电脑能否运行 `adb version`。缺失时从 [Android 官方 Platform-Tools](https://developer.android.com/tools/releases/platform-tools) 获取适合主机的工具；不自动改全局配置，不清理用户 SDK。

在电视设置中查找“关于 → Build／编译版本号”，常见固件可连续确认约七次打开开发者选项；菜单以电视及厂商官方说明为准，不能保证所有固件提供入口。参考 [Android 开发者选项](https://developer.android.com/studio/debug/dev-options)。

- 传统网络调试：用电视显示的实际地址与端口执行 `adb connect`。
- 无线配对：仅当电视提供配对入口，按显示的配对地址与端口执行 `adb pair`，再按连接地址与端口 `adb connect`；两种端口可能不同。电脑侧 ADB 配对与 Quiet TV 内置配对入口的版本限制分别核查。
- USB：需电视支持 ADB 数据连接；“USB 调试”开关不证明网络端口开放。
- 查看 `adb devices -l`；`unauthorized` 需电视授权，`offline` 先核对连接。让用户选定电视后设置目标标识；后续每条命令显式 `-s`，禁止自动扫描整个局域网。

下方命令中的变量均须来自本次记录，不可将占位符直接发送到设备。POSIX 示例在 PowerShell 中应转换为其原生变量／引号语法；优先用进程参数数组，避免拼接远端 shell 命令。

## 基线与能力

只读助手将 `pm help`、`cmd package help`、HOME 查询及 Quiet TV 包信息记录为原始结果。脚本退出 0 仅代表记录成功；查询返回码 0 也可能包含厂商错误，必须阅读输出。

当前用户优先由 `am get-current-user` 读取；旧系统不支持时，从 `dumpsys activity activities` 中唯一的 `mResumedActivity` 用户核对。无确定结果时保持未知，不假设用户 0。多用户或前台切换后重查，不复用过期记录。

仅使用帮助中明确支持 `--user` 的 `pm` 或 `cmd package` 子命令，例如：

```sh
adb -s "$TV_SERIAL" shell cmd package resolve-activity --brief --user "$TV_USER" -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s "$TV_SERIAL" shell cmd package query-activities --brief --user "$TV_USER" -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s "$TV_SERIAL" shell dumpsys package "$ORIGINAL_PACKAGE"
```

有些固件仅在 `pm` 提供对应命令，选择帮助验证过的版本。包名／组件仅接受从该设备 HOME 列表取得的合法标识。若结果为 `android/...ResolverActivity`，这是系统选择器，不能记录成可恢复原桌面；向用户展示实际候选并核实原桌面。设置向导接管 HOME 时不要禁用设置向导。

在原桌面包输出中找到目标 `User N` 的 `installed`、`hidden`、`suspended`、`enabled` 等字段和相关组件覆盖状态，保存原始值。包级 `enabled=0` 是默认设置，`enabled=1` 是显式启用，两者不能混同。原桌面无法核实、组件被覆盖禁用或功能耦合未知时，只继续不依赖它的查询和手动试用。

## APK 与安装

按当前 Agent 安装说明，通过本机终端读取 [最新版索引](https://beatsad.github.io/quiet-tv/latest.json)，自动附加本次实际时间的 `check` 参数并禁用缓存，包含预发布／测试版。按适配与升级条件选择 APK，使用索引中的实际下载地址、大小及 SHA-256；历史包也可核对同名校验文件，不因缺少独立校验文件改装旧版。不得只用网页摘要或 `/releases/latest` 判定没有新版；安装前明确目标版本，已安装同一文件时无需重装，不自动回退历史版本。使用本机现有工具读取 APK，例如 Android SDK 的 `aapt dump badging`／`apkanalyzer` 与 `apksigner verify --print-certs`。仅凭文件名不能推断包名、minSdk 或架构。

先比对设备 API、CPU ABI 和电视特性。已安装 Quiet TV 时查询其版本和 APK 路径；将已安装 APK 拉到电脑私有目录读取签名后再决定是否覆盖，不能只比较包名。哈希是文件一致性证据，不替代来源信任。

```sh
adb -s "$TV_SERIAL" install -r --user "$TV_USER" "$APK_PATH"
adb -s "$TV_SERIAL" shell pm path --user "$TV_USER" home.quiet.tv
adb -s "$TV_SERIAL" shell am start --user "$TV_USER" -n home.quiet.tv/.HomeActivity
```

先核查本机 ADB／固件支持上述用户参数。旧系统无法明确指定用户时，仅在确认安装和启动实际作用于选定用户后采用其支持的路径；无法核实则停下，不省略参数后猜测。

安装后拉取实际 `pm path` 返回的 APK，计算 SHA-256 与选定包比较。多 APK／split 安装需逐个核对其来源，不能将单一 base.apk 哈希当成完整 split 集合证明。安装或校验异常时保留原桌面。

## HOME 切换与失败诊断

优先引导系统选择或 Quiet TV“首次设置”。使用 ADB 时必须先核对目标用户未改变、Quiet TV 的组件已安装且启用，并且是当前用户 HOME 候选。只有帮助明确支持对应语法才运行：

```sh
adb -s "$TV_SERIAL" shell cmd package set-home-activity --user "$TV_USER" home.quiet.tv/.HomeActivity
```

之后重新解析 HOME，并等待用户用真实遥控器 Home 键确认。失败后检查实际包版本、当前用户、HOME 候选和组件状态；记录固件拒绝，不反复发送同一修改。

| 结果 | 后续处理 |
| --- | --- |
| 安装成功，系统无桌面选择或拒绝设置 | 保留原桌面，说明可以手动试用，默认切换受限；若实际 APK 包含电脑守护配置入口，且用户明确授权兼容守护，可独立配置。 |
| `cannot be home on user ...` | 核对用户和 HOME 候选；不能直接归因为一个通用原因，也不能先禁用原桌面。 |
| HOME 读回 Quiet TV，真实遥控器返回原厂主页 | 分别记录查询与实测；可能是厂商按键接管，尚未完成实际切换。 |
| 重启／冷开机／唤醒后回到原厂主页 | 分别记录触发路径；不把一次 Home 键成功当成持久切换。 |
| ADB 被禁用、无可用连接 | 无法用 skill 绕过；如允许手动安装和系统选择，提供电视端步骤。 |

当前兼容问题应实时查阅 [项目兼容说明](https://github.com/BeatsAD/quiet-tv/blob/main/docs/COMPATIBILITY.md) 和 [HOME／ADB 反馈](https://github.com/BeatsAD/quiet-tv/issues/35)。运行异常按[Agent 问题排查](https://raw.githubusercontent.com/BeatsAD/quiet-tv/main/docs/AGENT_DIAGNOSE.zh-CN.md)取证。开发版兼容守护须核对实际安装包、已授权范围、原桌面记录和权限状态；不能把上游方案、服务已绑定或新源码能力当作历史 APK 和当前固件的验收结果。

## 恢复原桌面

安装与切换流程保留原桌面，不执行禁用。历史操作记录中的 `enabled=0` 是默认状态、`enabled=1` 是显式启用，恢复时不能混同。

故障恢复或用户要求切回原桌面时，先确认目标电视、当前用户与记录一致，再按实际变更恢复。若本次未禁用原桌面，不需执行启用命令。若已禁用，按保存的原值执行其中一个经固件支持的命令：

```sh
adb -s "$TV_SERIAL" shell pm default-state --user "$TV_USER" "$ORIGINAL_PACKAGE"
```

或原始为显式启用时：

```sh
adb -s "$TV_SERIAL" shell pm enable --user "$TV_USER" "$ORIGINAL_PACKAGE"
```

读回包状态后，再使用系统选择或已验证的 `set-home-activity --user` 切回记录的真实原组件，并重新查询和验证实际 Home 键。系统选择器不能作为恢复目标。保留 Quiet TV 安装及数据，卸载是独立操作。

恢复依赖 ADB 仍能连接或系统界面仍可打开，必须向用户说明。连接失效且没有已确认的恢复路径时停止远程修改，提供厂商支持的人工恢复入口，不重置电视。

需要重启验收时，先确认用户已有重启授权和恢复途径，再运行针对目标的 `adb -s "$TV_SERIAL" reboot`。等待设备上线后重读用户、HOME 和相关包状态，并让用户观察电视；连接重试只做只读查询，不自动重放安装或切换。


## 电脑首次配置兼容守护

按[首次设置说明](../../../docs/SETUP.md#通过电脑配置守护开发源码尚未发布)使用 `scripts/configure_home_guard.py`。这是独立的可选配置：电脑 ADB shell 已授权时，不要求 Quiet TV 内置 ADB 认证或系统默认 HOME 设置成功。只读排查、普通安装和仅更新不授权运行 `--apply`；明确授权守护配置后可执行，且该参数会主动启用守护。核对实际 APK 包含受系统权限保护的 `HomeGuardSetupReceiver`，历史包不自动获得此能力。

先预览，再以已核对的 `--serial`、`--user` 和新的私有 `--record` 文件执行，必要时指定实际 `--home`。只配置本应用的写安全设置、无障碍、适用的后台启动、通知及安卓5／6用量统计权限；保留原桌面和其他服务，不导入电脑密钥、不禁用系统包、不开启 ADB 常开。失败保留已完成的授权，核对实际状态后再处理，不自动回滚。权限已配置不等于服务绑定或真实接管；实体 HOME、冷启动、待机与 HDMI 分别验收。低内存固件通知恢复不可用时单独报告。
