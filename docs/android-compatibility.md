# Android 版本兼容与验收

## 配置与范围

兼容分支原始候选包为 1.1.0 / b14（当前 b24 集成说明见文末）：minSdk 23（Android 6.0）、compileSdk 36、targetSdk 36，不设置 maxSdkVersion。使用同一个 APK；Android 17 的运行验证与目标 SDK 分别记录，不把编译版本当作最高运行版本。

Android 16 TV 测试中，目标 SDK 31 的旧候选被 Play Protect 提示缺少最新隐私保护；迁移到目标 SDK 36 后正常安装。没有修改现有电视的安装保护。这个结果不代表所有厂商的安装策略。

## 实现

- Android 6 使用动态网络变化广播，Android 7 起保留默认网络回调；注册与注销成对处理。
- Android 9 以下回退字体字重和版本号接口；Android 8 以下的原生英文字体也回退到系统字体，避免依赖 Outfit 的可变字体表。自有代码用普通循环和 Collections.sort 替换较新集合 API，不引入整套核心库脱糖。
- 较新系统优先使用 HOME 角色入口，否则检测系统桌面设置入口。系统设置返回后重新读取实际 HOME，不把 RESULT_CANCELED 直接当作设置失败。
- AndroidX Activity 1.10.1 统一旧遥控器和新版返回回调，保留首页回 Dock、设置返回、Dock 排序取消、应用详情回列表和许可返回。
- 关于页使用 ES5、旧 DOM API、Flex 回退和 rgba 颜色。不支持可变字体的旧 WebView 使用系统字体，保留现有内容、只读仓库/邮箱及焦点路径。
- Android 6 使用兼容矢量轮廓和纯色图标底色；Android 7 起保留原渐变及图标。
- ADB 本机校验改用文件流。Android 6 没有 am get-current-user，读取前台 resumed Activity 的唯一用户并与应用 UID 对照；无结果或歧义时拒绝写操作。保留本机地址、启动标识、shell 身份检查和取消后禁止续办。
- 连接后只读解析 pm/cmd package 帮助，只采用明确支持 --user 的命令。没有能力的操作不开放；禁用要求能重新启用；无法精确恢复默认启用状态时，不禁用原桌面。原有机型和固件规则继续生效。

## 验证方法

```sh
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :home-fixture:assembleDebug :management-fixture:assembleDebug --console=plain
```

在独立模拟器安装主包、androidTest 包及两个 fixture。SERIAL 必须指向该模拟器，不要对真实电视运行管理用例：

```sh
adb -s SERIAL shell am instrument -w -e scenario compatibility home.quiet.tv.test/home.quiet.tv.ManagementInstrumentation
adb -s SERIAL shell am instrument -w -e scenario about home.quiet.tv.test/home.quiet.tv.ManagementInstrumentation
adb -s SERIAL shell am instrument -w -e scenario dock home.quiet.tv.test/home.quiet.tv.ManagementInstrumentation
adb -s SERIAL shell am instrument -w -e scenario setup-upgrade home.quiet.tv.test/home.quiet.tv.ManagementInstrumentation
```

compatibility 检查元数据、系统 HOME 入口、矢量孔洞、首页/设置/管理页返回、配对能力和取消。Android 6 额外检查真实前台用户读取及 ADB 密钥创建/保存。关于页等待 WebView 绘制，并检查标题像素，避免仅 DOM 正确而画面空白。Android 6 跳过 API 26 才有的 ActivityMonitor 故障注入；链接分发和许可返回仍须通过。

commands-compatibility 用例验证真实生产操作，需先开放模拟器网络调试并允许独立密钥。旧的 setup、visual、apps 等场景需要 ADB 网络端口、授权和特定系统命令。不要将环境未授权或旧测试的版本假设当成兼容通过。

## 验证记录

2026-10-19，本地构建、lint（0 错误、24 警告）、16 项 JVM 测试、ES5 语法检查通过。APK 的 v1/v2 签名校验及 16 KB ZIP 对齐检查通过；8 个原生共享库的 PT_LOAD 对齐均为 16384。后者不替代 16 KB 设备运行验收。

| 环境 | 证据范围 |
| --- | --- |
| Android 6 / API 23，ARM64 通用镜像 | 基础页面、真实前台用户读取、密钥创建/保存、配对入口能力及取消、关于页、Dock、升级分类；WebView 为 Chromium 44 |
| Android 12 TV / API 31，ARM64 | 基础页面、关于页、Dock、升级分类；实际本机 ADB、生产操作的测试包禁用/启用、日志及状态恢复 |
| Android 16 TV / API 36，ARM64 | 安装、基础页面、关于页、Dock、升级分类；新版返回路径验证 |
| Android 17 / API 37，ARM64 通用镜像 | 安装、基础页面、关于页、Dock、升级分类；不冒充 Android 17 TV 设备验收 |

截图复核发现旧 WebView 的可变字体空白，修复后文字及三个二维码正常显示。Android 17 初始焦点曾落到 Dock 返回按钮，现于第一次窗口焦点恢复后显式选择第一个位置，弹窗关闭不重复重置。

命令用例只操作独立测试包。缺少 default-state 的镜像先将测试包准备为显式启用，以准备后的状态为恢复基线；不会因此操作原厂桌面。当前电视的 pm 帮助只读核对确认提供 default-state、install-existing 和 set-home-activity，但本轮没有安装新包或改变电视配置。

本地详细日志、截图和静态检查记录保留在 output/compatibility（不随 Git 分发）。Android 6、16、17 的最终安装包 SHA-256 已与本地候选包核对一致；Android 12 TV 记录的是安装及运行用例，未另外记录其安装包哈希。

## 待真机验收与限制

- Android 6 及其他厂商电视：默认桌面、HOME、重启/待机、调试连接、禁用及恢复。通用 Android 镜像不代替 TV 固件验收。
- 现有 Android 12 电视本轮未覆盖安装；需集成正在进行的焦点修改后最终回归。
- 未逐一运行的中间系统、32 位真机、16 KB 页设备实际原生库加载。
- 低内存设备的冷启动、长期驻留、壁纸和应用切换性能。安装兼容不等于流畅度保证。
- 商店审核、签名发布及未来系统更新不属于本地构建通过的证明范围。

## 开发隔离

分支 codex/android6-compatibility 从 e0ad207 建立，没有混入原工作区已有的首次设置焦点修改。集成时保留其 SetupActivity、ManagementInstrumentation 改动，重新回归焦点用例。

公开的 Activity.dispatchKeyEvent 经由 AndroidX 库内桥接类继承，四个原有遥控器入口仅对 RestrictedApi 检查标注了原因明确的例外；没有压制 NewApi、返回导航或权限错误。

测试下载的 SDK 镜像与 AVD 占用本地磁盘。本轮不批量删除目录，清理不作为交付条件。

## 官方资料

- [min/target/max 的区别](https://developer.android.com/guide/topics/manifest/uses-sdk-element)
- [Android 16 返回和目标行为](https://developer.android.com/about/versions/16/behavior-changes-16)
- [Android 17 兼容指导](https://developer.android.com/about/versions/17)
- [Android TV 16](https://developer.android.com/tv/release/16)
- [16 KB 页检查](https://developer.android.com/guide/practices/page-sizes)

## 2026-10-20 全分支集成

兼容分支已与主线的原生关于页、首次设置焦点、壁纸缓存和 HOME 异常恢复合并，源码候选递增至 1.1.0 / b24，默认 minSdk 23、targetSdk 36。保留 b14 的历史验证范围，不将其视为本次集成包已通过真机验收。关于页首屏继续使用原生 View，旧系统采用字体回退；离线许可仍按需进入 WebView。HOME 切换与回退沿用已检测支持的命令。公开下载仍为 b23 / API31 / ARM32，本次仅本地集成与验证，不发布或安装。
