# 遥控器逐键校准与映射

[统一入口](../AGENT.md)。首次 AI 配置在 HOME/守护路线稳定后依次校准 HOME、信号源、菜单键；已有正常配置或仅更新时保留原映射，只有用户要求调整、键失效或机型/固件改变时重新校准。不是蓝牙/红外配对，不更换遥控器连接，不修改厂商按键表。先完成[连接与任务记录](connect.md)，按[任务工具](session.md)保存各阶段记录。

## 能力与边界

先通过 `remote_calibration status` 检查已安装 APK 的 `remote-keymap-v1` 能力。旧 APK 不具备此接口时，保留原生按键并报告未校准；不以最新源码能力代替实际 APK，不临时打包或绕过授权安装。

只采集短时窗口中应用或已授权无障碍实际收到的键码、扫描码、非唯一的遥控器厂商/产品编号。不读取输入框文字、不采集遥控器序列号。厂商可能在系统层处理 HOME、信号源或菜单，未采到不等于坏键：没有键码时只能选择 `system`，让用户按键并确认实际画面，记录“由系统处理”，不能猜一个键码或注入按键充当实体证据。

自定义 HOME 的全局响应要求现有 API21+ 桌面守护已就绪且具备按键观察能力。校准工具不自动授权或启用服务，能力不足就说明限制；Android4.x可保存本桌面收到的信号源/菜单映射，HOME通常保留系统处理，不扩大厂商专用 HOME 服务支持范围。菜单和信号源映射作用于 Quiet TV 桌面，不截获其他应用的菜单。

## 依次采集、试用、确认

通过 `agent_session.py run --session "$SESSION_DIR" --tool remote_calibration -- ...` 调用下列底层参数。设备、用户、应用和每次全新记录目录由任务工具固定。源码工作区脚本在 `scripts/`；下载包脚本在 `tools/`。`$PREVIOUS_CALIBRATION` 是上一阶段真实返回的 `calibration.json`，不得使用示例路径或其他电视记录。

```sh
python3 agent_session.py run --session "$SESSION_DIR" --tool remote_calibration -- status
python3 agent_session.py run --session "$SESSION_DIR" --tool remote_calibration -- begin --apply
python3 agent_session.py run --session "$SESSION_DIR" --tool remote_calibration -- capture --from-record "$PREVIOUS_CALIBRATION" --apply
```

先把当前键名和预期动作告诉用户。采集工具打印“请按一次遥控器…”后，立即在 AI 对话转述，保持进程运行等用户操作；不要等工具结束才提示。默认20秒，允许5至30秒；每次只按提示的一个键并松开，不混按方向、确定或其他键。采集不会覆盖已有映射。超时、取消或多个不同键时读取本次结果，明确说明缺失，不自动重放采集。

收到完整事件后，向用户说明所选动作，再临时试用。动作仅可选下表之一，不执行任意 Intent、组件或 shell：

| 当前步骤 | 可选动作 |
| --- | --- |
| HOME | `home` 回到 Quiet TV；`system` 保留系统处理 |
| 信号源 | `inputs` 打开现有信号源入口；`system` 保留系统处理 |
| 菜单 | `settings` 系统设置；`desktop_settings` Quiet TV 桌面设置；`system` 保留系统处理 |

```sh
python3 agent_session.py run --session "$SESSION_DIR" --tool remote_calibration -- preview --from-record "$PREVIOUS_CALIBRATION" --mapping home --apply
```

`--mapping` 按当前键和用户选择填写，不能把同一个示例动作套用给三键。试用有效120秒，未确认或取消后恢复已有映射。HOME试用前由 AI 在已授权范围打开本机可用的系统设置页面，再请用户按实体 HOME，核对是否回 Quiet TV；信号源和菜单试用从 Quiet TV 桌面开始。检查 signal source 面板和目标输入的可见结果，不能把 `dispatched` 或收到UP事件当作实际切源成功。

`system` 试用不补造键码；请用户实际按键、观察并口头确认。其他动作须先观察到试用中的一次完整按下/松开，之后仍须用户确认画面正确。失败时不要确认，按[失败报告](acceptance.md)说明并在原任务显式取消，保留日志。

```sh
python3 agent_session.py run --session "$SESSION_DIR" --tool remote_calibration -- confirm --from-record "$PREVIOUS_CALIBRATION" --observed "$USER_OBSERVATION" --apply
```

`$USER_OBSERVATION` 必须来自用户实际反馈，如按键后显示的页面；不填“命令成功”或由 AI 编造通过。每次确认后读取返回的下一键名，继续 capture → preview → confirm。三个键按 HOME → 信号源 → 菜单全部确认后，电视一次性保存并读回当前机型、固件、Android用户的映射，电脑断开后使用本机保存结果。应用升级在相同范围内沿用；固件、机型或用户不符时不套用，历史配置保留。不同按键不能保存为同一组键码/扫描码/遥控器型号编号；电源、音量、方向、返回与确定键不可改作这些动作。

## 中断与取消

每个命令写入自己的 `calibration.json` 和 `record.jsonl`。中断后先 `agent_session resume`，核对失败步骤，再用 `status` 读取电视的同一校准任务；不重放旧命令。记录的连接、应用、机型、固件、用户、任务ID、当前步骤、试用动作或映射版本不符均停止。过期试用不能直接确认，须显式重新采集/试用；已保存结果先读回，不重复确认。

```sh
python3 agent_session.py run --session "$SESSION_DIR" --tool remote_calibration -- cancel --from-record "$PREVIOUS_CALIBRATION" --apply
```

取消只结束本次草稿和临时试用，已有映射保留。若仅当前阶段被取消，用户选择重新校准时明确新建校准任务，不把取消当作已验证。结束时分别交付三键的采集证据、所选动作、用户观察、映射读回和未测项；实体 HOME、重启、冷启动、待机以及 HDMI/AV切换仍分别验收。
