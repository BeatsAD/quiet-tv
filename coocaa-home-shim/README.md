# 酷开主页中转助手（实验）

[Quiet TV](../README.md) · [上游源码快照](https://github.com/VincentYe123/coocaa-home-shim/tree/281a6a1362a9e30faaeb2169b75ba701dd0b1a03) · [上游适配 PR](https://github.com/Gathin23/cleanyourtv/pull/537) · [MIT 许可证](LICENSE)

仅用于已确认以下机制的国产创维／酷开 Android 8 固件：`com.tianci.system` 通过 `persist.service.homepage.pkg` / `.cls` 启动主页，并在发现 `com.ccos.tv.homepage` 时将它设为开机主页。上游记录来自创维 7S60_A5；**当前 Quiet TV 移植尚未经过真实电视验收，不能按品牌推定兼容。** 上游 PR 尚未合入，其开机测试使用过额外服务，不能直接作为本移植的验收结果。

本模块独立安装，普通 Quiet TV 构建不包含或启用它。安装前必须已有可用 ADB；它不能解锁固件封锁的调试功能。

## 实现与来源

固定复用 VincentYe123/coocaa-home-shim 提交 `281a6a1362a9e30faaeb2169b75ba701dd0b1a03`，保留主页中转、原桌面回退及上游短时开机处理：最多 120 秒、每 2 秒读取 `sky.current.apk`；原桌面在前台时调用 `com.tianci.provider` 返回主页，Quiet TV 或其他应用进入前台后停止并退出进程。

移植改动：目标类改为 `home.quiet.tv.HomeActivity`，目标包名与构建参数一致；删除发往上游桌面 BootReceiver 的广播（Quiet TV 没有该接收器）；助手服务与接收器不对普通外部应用开放。原桌面组件仍为上游记录的 `com.tianci.movieplatform/com.coocaa.homepage.vast.HomePageActivity`，目标固件不符时先停止安装并调查。

助手保持上游 `minSdk 21 / targetSdk 25`，用于其 Android 8 普通后台服务方案；最低安装版本不代表其他固件适配通过。**Quiet TV 主程序仍为 targetSdk 36。** 不绕过新系统的旧目标 SDK 安装限制。许可证随助手 APK 打包在 `assets/licenses/`。

本地 Google TV 12 / API 31 模拟器曾因旧目标 SDK 被 Play Protect 拦截安装；未关闭保护或绕过拦截。这一助手应按 Android 8 专项验证，不能作为通用安装包推广。

## 构建

与主项目共用 JDK、Android SDK 和 Gradle Wrapper。按主 README 准备环境，在仓库根目录执行：

```bash
./gradlew :app:assembleDebug :coocaa-home-shim:assembleDebug -PquietCoocaaShim=true
```

产物：`app/build/outputs/apk/debug/app-debug.apk`、`coocaa-home-shim/build/outputs/apk/debug/coocaa-home-shim-debug.apk`。Debug 签名仅供本地测试，未配置持久签名的 Release 不作为交付包。

先使用普通 `home.quiet.tv`。仅当目标固件确实清理普通包名、豁免 `com.coocaa.*` 时，再构建配套专用包：

```bash
./gradlew :app:assembleDebug :coocaa-home-shim:assembleDebug -PquietCoocaaShim=true -PquietApplicationId=com.coocaa.quiettv -PquietMinSdk=26 -PquietBuildRoot="$PWD/output/coocaa-candidate"
```

两份 APK 均从 `output/coocaa-candidate/<模块名>/outputs/apk/debug/` 取用。专用包是独立应用，不覆盖普通包、不自动迁移数据；助手与桌面必须使用同一构建参数。专用包暂不使用标准 `scripts/build_apk.py` 归档，以免与通用包归档名称混淆。

## 安装与验证

以下步骤需逐项确认后手动执行，不是批量适配脚本。

1. 用 `adb devices -l` 确认目标，把 `TV_SERIAL` 设置为自己的电视标识。核对 Android／固件版本、原桌面组件，保存主页属性原值（包括空值）、当前用户和原桌面 enabled 状态作为本地恢复记录。不要公开设备标识或完整转储。

   ```bash
   adb -s "$TV_SERIAL" shell getprop persist.service.homepage.pkg
   adb -s "$TV_SERIAL" shell getprop persist.service.homepage.cls
   adb -s "$TV_SERIAL" shell dumpsys package com.tianci.movieplatform
   adb -s "$TV_SERIAL" shell pm path com.ccos.tv.homepage
   ```

   **最后一项若返回包路径，停止安装，不能覆盖或卸载已存在的同名应用。** 先安装并手动打开配套 Quiet TV，确认可用，保留原桌面。

2. 安装助手（普通构建示例）：

   ```bash
   adb -s "$TV_SERIAL" install coocaa-home-shim/build/outputs/apk/debug/coocaa-home-shim-debug.apk
   adb -s "$TV_SERIAL" shell am start -W -n com.ccos.tv.homepage/.LaunchActivity
   ```

   确认中转打开 Quiet TV，再设置主页：

   ```bash
   adb -s "$TV_SERIAL" shell setprop persist.service.homepage.pkg com.ccos.tv.homepage
   adb -s "$TV_SERIAL" shell setprop persist.service.homepage.cls com.ccos.tv.homepage.LaunchActivity
   ```

3. 按实体遥控器 HOME，从设置和常用应用返回；分别验证重启、断电冷启动、待机唤醒和输入源。属性查询、手动启动和 ADB 注入按键不代替实体遥控器验收。
4. 只有确认原桌面仍抢占、且恢复路径可用时，才单独验证禁用原桌面。不套用上游整套精简清单。
5. 上游仍记录开机竞争可能把主页属性写成 `home[0]` / `home[1]`。若复现，先按保存值恢复原桌面，将该固件标为未通过，不增加无限重试。

## 恢复

先恢复原桌面的安装和启用状态，并通过原组件启动它。若本次从默认启用改为禁用，用 `pm default-state --user <原用户> <原包名>`；原先显式启用则用 `pm enable --user <原用户> <原包名>`。

然后卸载**本次新装的助手**，再恢复保存的主页属性，避免助手仍安装时下次开机又被指定为主页。下面占位值必须替换为本地原值，原值为空时传空字符串：

```bash
adb -s "$TV_SERIAL" uninstall com.ccos.tv.homepage
adb -s "$TV_SERIAL" shell setprop persist.service.homepage.pkg '<原包属性值>'
adb -s "$TV_SERIAL" shell setprop persist.service.homepage.cls '<原组件属性值>'
```

确认 HOME、重启、输入源恢复后，专用 Quiet TV 可以留在电视上，无需清理其他应用或数据。ADB 断连时先恢复连接，不把尚未执行的恢复报告为完成。
