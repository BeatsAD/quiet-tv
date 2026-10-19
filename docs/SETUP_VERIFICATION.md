# 首次设置候选验证记录

日期：2026-10-19（Asia/Shanghai）。初次候选：`1.1.0 / b4 / debug`，当前修复候选：`1.1.0 / b5 / debug`。本记录与旧版真机记录分开；本次已完成日用电视覆盖安装与哈希核对，功能验收仍待手动测试。

## 已执行

| 检查 | 结果与范围 |
| --- | --- |
| 应用、仪器测试包及独立 HOME 验证组件构建 | 通过；最低 API 31，保留既有 targetSdk 31 |
| JVM 单元检查 | 8 项通过，0 失败；规则匹配、未知固件保护、权限不继承、规则冲突、HOME 输出解析、取消令牌、转发连接取消与期限 |
| Android Lint | 0 错误、20 条警告；保留依赖及既有界面等警告，不将其视为安全扫描结论 |
| 官方 Android TV 13 ARM64 专用模拟镜像 | 两屏原生界面、实际应用清单、临时使用、取消操作、HOME 读回、未验收卸载拒绝、测试 HOME 禁用／恢复／切回、零选择跳过通过 |
| 全新安装分流 | 自动进入第一屏；模拟首页偏好已写入仍正确识别新安装；退出不改变默认 HOME |
| 升级分流 | 实际覆盖安装后，在缺少新设置记录的隔离目录中识别旧安装，不强制引导 |
| 原桌面启用状态恢复 | 测试 HOME 禁用后恢复原始 default enabled setting，并分别核对实际 HOME 与其他包禁用状态 |
| 无线原生库与取消 | ARM64 TLS／SPAKE2 加载通过；实际上游协议发起 TLS 握手，在受控静默服务端等待期间取消，3 秒内返回失败，无 ADB 后续连接 |
| 连接面板 | 原生 TCP／实验无线模式切换与取消通过，HOME 和应用状态保持；配对／连接端口分开，未执行真实配对 |

Lint 对广泛包查询的抑制限定在该权限声明，原因为完整桌面／应用管理清单；旧 targetSdk 提示在该配置处说明本地候选用途，没有升级 targetSdk 或声称通过 Google Play 审核。布局方向常量及定位／可选 Wi-Fi、GPS 声明按诊断修正。

## 模拟器证据边界

原生 UI 来自 Android 应用，扫描的是该镜像实际系统应用，没有套用 Sony 推荐清单。截图位于本地忽略目录 `output/`：`setup-default.png`、`setup-apps-unknown-firmware.png`、`setup-fresh-install.png`、`setup-wireless-panel.png`。

该镜像的原 Google TV HOME 优先级为 2。未经调整时，`set-home-activity` 可返回 `Success`，但实际默认组件仍为原 HOME；实现按读回结果拒绝进入精简。为测试成功分支，**仅在专用模拟器**暂时禁用其原桌面包，再使用独立 `home-fixture` 验证组件；结束后恢复原桌面精确启用设置、实际默认 HOME 及原禁用清单。没有在真实电视临时禁用任何系统组件。

测试 HOME 是普通测试 APK，不能替代预装卸载／`install-existing` 证据。注入的测试设备规则只存在于仪器测试进程，不进入生产规则资产。

无线验证是实际 TLS 握手取消，不是成功配对；该镜像无线设置只提供开关，未提供本次可验收的六位码页面。

本地日志：`output/setup-final-checks.log`、`setup-emulator-checks.txt`、`setup-fresh.txt`、`setup-upgrade.txt`、`setup-pairing-cancel.txt`、`setup-connection-ui.txt`。不记录配对码或私钥。

## 仍待验收

- 当前候选在其他真实机型上的安装哈希，以及 Sony 和其他机型的系统确认入口、遥控焦点／滚动、取消授权、断连／进程终止及重启保持。
- Sony 六项推荐的实际选择与执行、已有 Dock／壁纸升级回归；历史单包记录不自动计作新流程通过。
- 原厂桌面及预装应用卸载、系统保留包恢复与数据删除确认；原桌面卸载／恢复安装规则保持未开放。
- 六位码成功配对、TLS ADB 建连、NSD 实际端口匹配、系统页面切换后码的有效性及非 ARM64 运行；实验入口保留手动端口与 TCP／取消路径。

## 复验与候选包

本地检查不接触电视：

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :home-fixture:assembleDebug
```

专用模拟器安装桌面、测试包和 `home-fixture` 后，以明确的模拟器序列号执行；以下场景会改变并恢复模拟器 HOME，不要在真实电视运行：

```sh
adb -s <专用模拟器序列号> shell am instrument -w -e scenario setup home.quiet.tv.test/home.quiet.tv.ManagementInstrumentation
adb -s <专用模拟器序列号> shell am instrument -w -e scenario pairing-cancel home.quiet.tv.test/home.quiet.tv.ManagementInstrumentation
```

`setup-fresh` 仅用于全新安装；`setup-upgrade` 要求完成实际覆盖安装。入口核验模拟器与安装状态，不自动删除或重装应用。首次 TCP 连接需在模拟器确认应用独立密钥授权。

候选 APK 与不可变元数据：`dist/apk/quiet-tv_1.1.0_b4_debug.apk` 及同名 JSON；哈希与安装核对保存在独立本地记录。构建、模拟器安装和真实电视安装分别报告。未推送或发布。

2026-10-19 00:25（Asia/Shanghai）核对：归档 APK 与模拟器已安装 APK 的 SHA-256 均为 `cfa6a68deffffc4f72ae5b051de58ddf175938b0fc4c90008ce1b3a89f876810`，记录为 `output/setup-installed-receipt.json`。该时间点尚未核对或改变日用电视的已安装 APK。

## 日用电视安装核对

2026-10-19 00:30（Asia/Shanghai），按用户测试授权，将原 `1.0 / b1` 覆盖更新为 `1.1.0 / b4 / debug`，未卸载或清除数据。安装前保存旧 APK、实际 HOME、禁用包清单及桌面／Dock／壁纸偏好。

电视安装 APK 的 SHA-256 与上述候选一致。覆盖后实际 HOME 与禁用包集合保持，桌面／Dock／壁纸偏好逐字节一致，首次安装时间保留；首页已启动并保存真实电视截图。本次没有执行精简、原桌面切换、卸载或重启，未计作首次设置功能验收通过。

本地忽略目录中的证据：`evidence/setup-install-20261019-003013/`（旧 APK、安装输出、前后状态与首页截图）；独立安装核对回执：`dist/apk/quiet-tv_1.1.0_b4_debug.installed-20261019T003048642875+0800.json`。升级不会强制弹出引导，手动测试入口为“应用管理 → 更多 → 首次设置”。

## 原型还原修复（b5）

2026-10-19，候选 `1.1.0 / b5 / debug`。以已确认两屏原型为布局与交互基线，恢复默认桌面左右排版与实际应用预览、横向原桌面选项、双列推荐卡片、当前壁纸与步骤标识；完整只读清单移至次级入口。连接面板恢复两条指引、折叠连接设置、主要按钮及返回连接说明。

允许在本机扫描后先选择意向，点击执行冻结选择，再按需授权；成功连接后自动继续。执行前先核验整组冻结选择，任一状态变化则拒绝写入；保留实际 HOME、本机／当前用户、规则与单包执行前后保护。缺失包不制造，已禁用包不计数，未知规则不推荐，未验收卸载仍不开放。

- 构建、8 项单元检查通过；Lint 0 错误、20 条警告。
- 专用 Android TV 13 ARM64 模拟器 `setup-prototype`：左右预览与非通栏主按钮、横向三选一、六项三行双列及不遮挡主按钮、未连接可选、方向键相邻移动与切换后焦点保留、关闭授权不改变包、设置说明返回连接、状态变化拒绝执行、实际 TCP 授权后自动完成专用测试应用禁用均通过；测试后恢复原 HOME 和包状态。
- `setup` 与 `connection-ui` 回归通过：临时使用、HOME 读回、取消、未验收卸载拒绝、测试 HOME 精确启用状态恢复、跳过，以及 TCP／无线模式切换与取消。
- 本地截图：`output/setup-prototype-default.png`、`setup-prototype-presets.png`、`setup-prototype-connection.png`；已人工对照原型的布局、控件形态、背景与主次层级。模拟器的六项布局使用进程内临时规则和实际包元数据，只执行专用测试应用的禁用；临时规则不进入生产 APK，不计作真实系统包安全验收。
- 本地日志：`output/setup-prototype-final.txt`、`setup-restore-regression.txt`、`setup-restore-connection.txt`、`setup-restore-final-build.log`。无线成功配对及其他真实机型的边界不变。

2026-10-19 16:45（Asia/Shanghai）日用电视覆盖更新到 b5，并核对 SHA-256：`9f9f8b5d5d9b6d7af454418ee32d505fe3738b4b703003a3b62eb74d9c35cf45`，与归档一致。安装前的 b4 APK 已备份；默认 HOME、禁用包集合以及首页／Dock／壁纸偏好在安装后保持一致。

通过正常首页和应用管理菜单打开设置，已在真实电视核对两屏布局及返回／进入推荐页，停留在预装应用页供用户测试。真实图标、应用名称和状态来自当前电视：五项已禁用，一项可恢复安装，因此当前选择为 0；未重新启用、禁用或卸载任何包。未执行 HOME 切换、重启或真实精简操作。

真机证据：`evidence/setup-prototype-install-20261019-164439/` 下的 `setup-default-tv.png`、`setup-presets-tv.png`、前后状态与旧包；安装回执：`dist/apk/quiet-tv_1.1.0_b5_debug.installed-20261019T164516107911+0800.json`。真实授权及实际精简功能仍需用户按自身测试范围验收。

## 纯模拟交互与遥控修复（b7）

2026-10-19，按用户明确选择，首次设置改为直接打包原型的离线全屏模拟演示；新版入口及旧组件别名均进入 `SetupDemoActivity`，不执行真实 HOME 或包管理操作。界面显示固定模拟标识；场景／重置收进演示工具，原型中的演示数据可完整操作。

遥控修复包含：所有可用按钮、链接、折叠标题和输入框进入方向导航；折叠隐藏项和禁用项排除；弹窗焦点隔离；关闭后恢复来源焦点；焦点与按键处理在布局阶段同步更新，避免旧页面处理迟到按键。输入框只在确定后进入编辑，返回或移出时结束编辑及收起输入法。

`setup-demo` 专用 Android TV 13 ARM64 模拟器检查通过：逐屏／弹窗计算焦点图可达性并用实际遥控键走到目标，覆盖默认确认、三选一、六项卡片、连接折叠与端口移出、取消、设置说明返回、模拟授权后继续、未选应用保留、恢复、六位演示码的实际输入、模拟卸载／恢复安装和退出。前后实际 HOME、禁用包集合及原生设置 JSON 保持不变。没有执行真实 ADB 连接或系统包写入。

WebView 101 的模拟器和浏览器预览均能显示原型；TV 全屏构建将 cqw 等价转换为 vw，支持该旧版渲染器。JavaScript 无原生操作桥接，网络／文件／内容访问受限，连接由 CSP 及本地拦截共同阻断。

为避免与同时开发的关于页混入构建，候选在独立工作副本基于 `54c6c34` 加本次修复构建，使用 b7，与另一任务的候选构建号分开。构建、安装、截图和实际遥控测试分别记录。
