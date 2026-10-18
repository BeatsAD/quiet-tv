# 电视端应用管理与 Dock 编辑

## A 阶段：本机 ADB

2026-10-18，Sony KD-65X9000H / BRAVIA VH1、Android 12，应用进程内实测：

- 应用私有目录生成独立 RSA 密钥，经电视弹窗授权后连接 `127.0.0.1:5555`。
- 通过当前启动标识、shell UID 和前台用户核对本机连接，当前用户为 0；实现不固定用户 0。
- 读取当前用户 219 个包（包括 Quiet TV、测试程序及专用测试应用）。
- 对 `home.quiet.tv.fixture` 单独禁用再启用，实际包状态核对通过。
- 去除未使用的无线配对原生依赖后再次通过。未复用电脑密钥，未由电脑代执行包变更。

固定使用 libadb-android 3.1.1，首版只连接本机传统 TCP ADB；库支持的配对码及远程连接接口不对用户开放。密钥文件不参与备份或 Git，命令超时关闭传输，不自动重试修改命令。

本地原始证据：`evidence/app-management/phase-a-adb.txt`（忽略入库）。测试 APK 无账号及业务数据。测试程序只作用于该专用包，不对其他应用执行验证性卸载。

本机复验（先安装桌面和专用测试 APK，并在电视确认首次授权）：

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :management-fixture:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r management-fixture/build/outputs/apk/debug/management-fixture-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w home.quiet.tv.test/home.quiet.tv.ManagementInstrumentation
```

## B 阶段：四位置 Dock

- 本地偏好首次从 `launcher.json` 导入，明确保存的空数组不会再次导入默认值。
- 添加、移除、满位替换与左右排序均用原生遥控控件；排序按确定保存、返回取消。
- 首页保持卡片尺寸与四位置布局；没有可见常用应用时提供“添加常用应用”。禁用不清除配置，明确卸载后移除配置；更新替换广播不按卸载处理。
- 电视原生界面验收通过：空 Dock 添加、移除、1～4 个持久化、满位替换、左右排序保存及取消。已恢复原四个常用应用。
- 本地证据：`evidence/app-management/phase-b-dock.txt`、`dock-empty.png`、`dock-one.png`、`dock-four-sorted.png`。

单独运行 Dock 验收（专用测试 APK 须已安装）：

```sh
adb shell am instrument -w -e scenario dock home.quiet.tv.test/home.quiet.tv.ManagementInstrumentation
```


## C、D 阶段：逐应用管理

入口：全部应用 → 应用管理。管理界面使用 Java、原生 View 与 RecyclerView，当前面向索尼机型，新增管理文案为中文。

应用列表展示包名、名称、图标、版本、预装／用户类型与当前状态；支持按名称／包名搜索及全部／用户／预装／禁用／可恢复筛选。未连接时使用本地包信息，系统操作要求本机 ADB 已连接。连接页可修改 TCP 端口，离开管理功能后释放连接，无常驻服务。

启用、禁用、当前用户卸载与符合条件的系统 install-existing 均针对单个包执行：重新读取状态 → 对照用户确认的版本和状态 → 先写记录 → 执行一次 → 再查询实际状态。用户切换、保护规则、包名和操作资格在执行前重新核对。超时或断连不自动重复执行；无法核对时记录结果不明，要求重新连接刷新。

`files/app-operations.jsonl` 保存包名、用户、操作、操作前后状态及结果；不保存账号、输入内容、密钥或数据备份。界面“操作记录”可查看记录。

Quiet TV、管理验证组件、未知或关键系统组件只读。可管理预装清单来自本项目既有调查，仅对已核实的 Sony 固件指纹开放；没有将电脑 `changes.json` 导入为新用户基线。

### 验收与保留项

- C 阶段真实界面已验证：完整包清单与同用户 ADB 查询一致；取消禁用无副作用；确认禁用／启用与实际状态一致；Dock 隐藏／原位恢复；关键组件只读；筛选、搜索、断连提示与操作记录。
- D 阶段真实界面已验证：取消卸载不改变安装状态；确认卸载后专用测试 APK 实际移除，Dock 配置随之移除；操作记录核对通过。
- 测试仅修改 `home.quiet.tv.fixture` 及临时 Dock 配置，收尾同步保存原四个快捷应用，防止测试进程退出早于异步偏好落盘。
- 预装应用卸载／恢复尚未进行破坏性实机验收；用户明确选择保留待验收，不使用 BRAVIA 演示测试。恢复入口的资格检查和实现不能等同于该流程已通过真机验证。
- 本次不覆盖其他固件、配对码调试、root、固件升级、正式签名发布和在线更新。

分别运行（卸载场景会真正卸载专用测试 APK，需要先安装该包）：

```sh
adb shell am instrument -w -e scenario apps home.quiet.tv.test/home.quiet.tv.ManagementInstrumentation
adb shell am instrument -w -e scenario uninstall home.quiet.tv.test/home.quiet.tv.ManagementInstrumentation
```

本地证据：`evidence/app-management/phase-c-apps.txt`、`phase-d-uninstall.txt` 以及对应管理页／确认弹窗／首页联动截图。截图仅记录管理功能与专用测试应用。


## E 阶段：联合回归与整机重启

2026-10-18 真机验证：壁纸预览返回后偏好未改变；信号源弹出真实输入菜单；网络打开 `NetworkActivity`；系统设置打开 `MainSettings`；飞牛 TV 启动到其 `MainActivity`（不包含账号、播放和音效验收）。

应用列表刷新保留顶部按钮焦点，避免从设置返回时误跳到 Dock。回归通过系统 HOME 入口进行，避免普通启动任务与系统 HOME 任务生成两个实例造成测试干扰。

整机 `reboot` 后系统启动完成，启动标识变化，自动回到 Quiet TV；默认 HOME、原四个常用应用、壁纸及 23 个禁用包状态均保持一致。它是当前这次重启的验收，不代表长时间运行、投屏或全部播放功能已通过。

重启后使用同一应用私有密钥再次通过本机 ADB 连接，读取到 218 个包（专用测试应用已卸载，验收程序保留安装），见 `post-reboot-adb.txt`。

该阶段最终 Debug APK 与电视已安装 APK SHA-256 一致：`a17b65d783691d58b386933f52aa70d0ca3f339ad1405ac0eb5709e206a9c3fd`。当次安装包包含任务开始前已有的首页样式修改，当次 Git 提交仅包含管理功能实现。后续改版、安装与发布状态见 [验收摘要](../VERIFICATION.md#当前记录摘要2026-10-18)，本段不代表当前工作区仍有未提交修改。

本地证据：`regression.json`、`reboot.json`、`home-after-reboot.png`、`final-build.txt`；均位于 `evidence/app-management/`。预装卸载／恢复按用户要求保留待验收，不操作 BRAVIA 演示，不以普通测试 APK 的成功替代该项证据。
