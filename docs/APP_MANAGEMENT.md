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
