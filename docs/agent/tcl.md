# TCL／雷鸟安装受限处理

[统一入口](../AGENT.md)。只在任务进入本阶段时读取；先完成[连接与任务记录](connect.md)，执行前后遵守[验收要求](acceptance.md)。下方是底层工具示例；Agent 应按[任务记录说明](session.md)通过 `agent_session.py run` 调用，由任务分配新记录路径。

## TCL／雷鸟安装受限候选方案

本分支先按 TCL／雷鸟品牌发现候选，再按本机错误和实际能力判断是否尝试；不要求用户先提供完整固件号。固件缺失记为“未确认”，不因此隐藏方案，也不能将品牌匹配写成已兼容。后续使用诊断日志补全品牌、型号、Android/API、固件显示版本、构建号和指纹，再按具体固件整理验证结果。

依据是 cleanyourtv 社区 [#683](https://github.com/Gathin23/cleanyourtv/issues/683)：`t982_ar31a8`、Android 11、固件 `RQ3A.210705.001.2024111116` 上报告了可逆的安装办法。该 Issue 的维护者将绕过安装限制列为项目范围之外，未集成到官方安装脚本；报告中的 `local.quiettv.launcher` 不是本项目的 `home.quiet.tv`，其成功记录不计入 Quiet TV 验收。

### 默认路线：relay 包与现有桌面守护

用户继续使用本页开头的原安装请求，无需更换指令或手工填写命令。Agent 识别为 TCL／雷鸟且安装受限时，优先走已发布 relay 包的多屏互动／电视端安装路线；临时停用安装器保留为用户单独选择的替代办法，不自动串联尝试。

1. **选包**：从实时 `latest.json` 的 `releases[].assets[]` 读取 `homeMode=relay`、实际 URL、SHA-256、版本、最低 API、ABI 和签名等配置。`homeMode` 来自与附件匹配的签名更新清单；`unknown` 表示历史附件尚未确认，须读取实际 APK。不能根据品牌或文件名中的“实验”推断；APK 查询声明也可能包含 HOME，必须检查 Activity 的 intent-filter。relay 复用现有主程序与守护，包名仍为 `home.quiet.tv`，不登记为系统 HOME 候选。
2. **保护更新**：首次安装可先尝试普通包，同时准备同版本同签名的 relay 候选；已装 relay 的更新只选符合升级条件的 relay 包。已有普通 HOME 包不自动覆盖为 relay，以免移除当前 HOME 入口；需要迁移时先另行说明。本次发布没有适配 relay 附件时，明确报告缺少可用包，不拿普通包冒充，也不自动重打包、改签名或退回旧版。
3. **取得工具**：从同一次 `latest.json` 的 `tools` 字段下载本节所需的 `install_tcl.py`、`tcl_cast.py`、`configure_home_guard.py`、`device_maintenance.py` 到同一私有目录，并逐文件核对所列 SHA-256。安装站点构建同时输出这些工具；缺项、下载失败或校验不符就停止依赖步骤，不让用户改用一串手工命令。源码工作区可直接使用对应 `scripts/` 文件。
4. **安装**：确认目标、电脑 ADB 授权、用户、APK 和记录位置后，先运行下面的预览；预览通过再加 `--apply`。普通包被本机 TCL 校验拒绝时，工具改用核验过的 relay；直接指定 relay 时也先尝试普通 ADB 安装。失败后使用目标电视的系统多屏互动，通过6553端口推送和仅绑定电脑 `127.0.0.1` 的 APK 服务、以及本次新建的 ADB reverse 下载；不公开电脑目录，不覆盖已有转发。电视出现提示时让用户选择“同意”。用户拒绝、已发起后的断连或结果不明时停止，不自动换渠道重装。
5. **电视端回退**：多屏互动不存在、端口不可用或目标用户不适用时，工具将 relay APK 暂存到电视 Download 的唯一新路径，尝试打开当前用户的当贝安装包管理，并等待电视端安装。让用户在当贝或文件管理器选择该文件并确认；不按固定坐标点击，不删除旧文件。超时标为未确认，保留暂存文件及记录；后续可重新核验已经安装的同一 APK，不强制重装。
6. **试用及守护**：安装后拉回实际 APK 核对版本、HOME 形态及 SHA-256，再打开 Quiet TV 试用。用户同意兼容接管后，复用[电脑守护配置](../SETUP.md#桌面守护实验)，运行 `configure_home_guard.py` 的预览及 `--apply`。该工具检测 TCL 实际支持的自启动／关联启动 AppOps 并保存原值，不硬编码操作数字。保留原系统 HOME、其他无障碍服务和用户的暂停状态；首次启用需明确同意，普通安装或只更新不自动开启已暂停守护。服务配置、绑定、实体 HOME、重启、冷启动和待机唤醒分别验收，不顺带接管系统屏保；本页默认请求已授权 ADB 常开，须同时配置并核对，首装基础检查及仅更新阶段保留原选择。

首次安装的预览示例，变量由 Agent 从本次实际数据填写；只有普通包与 relay 候选都已核验时才提供两组 APK 参数。已装 relay 的更新直接将 `--apk` 指向相应 relay，不同时提供普通包：

```sh
python3 scripts/install_tcl.py --serial "$TV_SERIAL" --user "$TV_USER" \
  --apk "$APK_PATH" --sha256 "$EXPECTED_SHA256" \
  --relay-apk "$RELAY_APK_PATH" --relay-sha256 "$RELAY_SHA256" --record "$NEW_PREVIEW_DIR"
```

执行时使用全新记录目录并加 `--apply`，无需 `--allow-temporary-installer-disable`。网络 ADB 自动沿用该目标 IP；USB ADB 由 Agent 读取电视地址并用 `--tcast-host` 指定，脚本会与目标实际网卡地址核对。电脑 ADB 不可用时，本工具不能完成安装后核验或守护配置；可说明电视允许的手动安装路径，不能将“APK 能装”说成“已完成桌面接管”。

### 识别实际失败（临时停用安装器的独立方案）

- 普通安装返回 `INSTALL_FAILED_VERIFICATION_FAILURE` 时保留输出，并采集该次安装前后的系统日志。若出现 `com.android.packageinstaller.verifier.strategy.LauncherStrategy`、`fetch signature info from :com.tcl.cyberui`、`The installing app not support tcl ui tab !` 或同类明确桌面校验信息，可向用户提供下方临时安装方案。仅有通用错误码时继续只读取证，不推定同一原因。
- Android 版本过低、架构不匹配、空间不足、签名冲突和降级仍按各自错误处理。旧固件的 `INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES`／`disable install launcher app not signature by tcl` 是另一类解析期限制，不能据此套用本分支。
- 不将 APK 内存在签名文件等同于满足 TCL 的厂商签名校验；不反复修改 `verifier_verify_adb_installs`、`package_verifier_enable` 或伪装安装来源试错。

### 临时安装与恢复

这是电脑 ADB 的可选安装操作，不是应用内系统包管理规则。只对本机确认承担该校验路径的 `com.android.packageinstaller` 开放一次临时处理，不扩展为其他系统包的禁用资格，不改动原桌面或固件预设。

实际执行入口为 [TCL 安装助手](../../scripts/install_tcl.py)，不是手工照抄上游命令。脚本默认只读预览；`--apply` 才执行一次普通安装，只有同时提供 `--allow-temporary-installer-disable` 且本次安装日志确认本包遭遇 TCL 桌面校验时，才临时处理安装器并重试一次。使用现有 Android build-tools 的 `aapt2`、`apksigner` 核对 APK；找不到时通过同名参数指定，不自动安装 SDK。普通包及无 HOME 的 relay 包沿用同一实际包名核验，不自动改包或替换包。

下列变量由 Agent 从本次已确认的设备、用户、实际 APK 和可信发布校验值填写，记录目录必须全新且位于电脑私有位置。预览、执行使用不同目录，不覆盖已有记录：

```sh
python3 scripts/install_tcl.py --serial "$TV_SERIAL" --user "$TV_USER" \
  --apk "$APK_PATH" --sha256 "$EXPECTED_SHA256" --record "$NEW_PREVIEW_DIR"
```

用户授权安装及临时处理安装器后执行：

```sh
python3 scripts/install_tcl.py --serial "$TV_SERIAL" --user "$TV_USER" \
  --apk "$APK_PATH" --sha256 "$EXPECTED_SHA256" --record "$NEW_INSTALL_DIR" \
  --apply --allow-temporary-installer-disable
```

如果记录中存在“安装器恢复待确认”，先用同一设备及记录用户执行恢复预览；核对后加 `--apply`。恢复入口只处理记录中的安装器，不重放安装，不修改 HOME：

```sh
python3 scripts/install_tcl.py --serial "$TV_SERIAL" --user "$TV_USER" \
  --restore-record "$INSTALL_RECORD_DIR/record.jsonl"
```

记录目录保留 `record.jsonl`、可取得的安装前 APK 和安装后核验 APK。日志只引用本次脚本标记之后且包含本项目包名的校验信息；历史或其他应用错误不触发停用。进程被强杀或断连无法保证即时恢复，必须保留记录并优先走恢复入口。

1. **先说明并取得本次授权**：向用户展示本机匹配的错误、所选 APK，以及“临时停用安装器 → 安装一次 → 无论成败立即恢复”的流程。告知窗口内系统安装界面及校验可能不可用，恢复前不重启、不切换 HOME。普通安装请求不自动授权停用系统安装器；已有明确授权则不重复询问。
2. **核对恢复条件**：电脑 ADB 已授权、目标及前台用户已确认；APK 已按通用规则核验。保存安装器在目标用户下的原始启用值与相关组件状态，以及原 HOME 基线，核对固件支持指定用户的停用与原状态恢复命令。原始 `enabled=0` 使用支持的 `pm default-state --user` 恢复，`enabled=1` 使用 `pm enable --user` 恢复；不能把两者混为一谈。安装器已被禁用、原状态或恢复方法不明时先报告，不将其当成本次可停用对象。
3. **仅执行一次安装窗口**：每条 ADB 命令显式指定同一设备及已核对用户。用已验证的 `pm disable-user --user` 临时停用该安装器并读回；只有停用确认成功后才对已核验的 Quiet TV APK 保留数据安装一次。无论停用读回、安装、取消或超时发生何种结果，都先检查并恢复本次涉及的安装器原状态，读回确认后再启动应用或进行其他修改；执行工具须使用 `finally` 或等效的退出清理安排恢复，不以安装成功为恢复条件。
4. **恢复失败优先处理**：立即在对话显示原始错误、已知状态、目标用户和由本机原值生成的恢复命令。连接丢失时标记“安装器恢复待确认”，连接恢复后先只读检查，再仅恢复本次已授权变更；不自动重试安装、再次停用、切换桌面或重启。没有可靠读回前，不报告恢复完成或方案成功。
5. **恢复确认后验收**：拉取已安装 APK 核对实际版本、包名及 SHA-256，再进入试用。默认 HOME 按已有接管授权继续配置，缺少该授权时再询问；查询和实体遥控器仍须验证；重启、冷启动、待机唤醒分别记录。成功安装不表示开机接管已完成，也不自动添加 `directBootAware`、更改启动属性或禁用 CyberUI。

交付时分别记录“品牌候选匹配、错误依据、固件信息完整度、安装结果、安装器原状态与恢复读回、已安装包校验、HOME 及电源验收”。具体设备验证完成前，一律标为“候选方案，Quiet TV 真机待验证”，后续固件日志作为补充证据；一次成功不升级为整个 TCL 品牌支持。

默认 relay 路线参考 [Aerial 的 TCL 安装实现](https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/tcl-mt5879-cn.md)，复用本项目 `quietHomeMode=relay` 及现有守护。工具代码、索引支持和具体 relay APK 是否已发布须分别确认；源码存在不等于用户的当前发布已提供对应包。两条路线均不适用时保留现场，说明剩余限制。
