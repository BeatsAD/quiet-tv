# 安装与构建

[English](INSTALL.en.md)  [首页](../README.md)  [兼容范围](COMPATIBILITY.md)

## 普通用户安装

当前公开版本为 [1.2.4 测试版](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.2.4)。使用已发布 APK 不需要 JDK、Gradle 或完整 Android SDK。可阅读[Agent 安装与恢复说明](AGENT_INSTALL.zh-CN.md)，或按下方“下载、安装与试用”操作。

## 环境

安卓5+标准档最低 API21，按 ARM32、ARM64 和通用备用分包；安卓4.4兼容档最低 API19，不含原生库、不提供六位码配对和桌面守护，信号源通过系统设置或遥控器进入。各包均为 targetSdk36，包名为 `home.quiet.tv`。最低系统满足不代表厂商固件已验收。

从源码构建需要 Git、Python 3、JDK 17 或 21、Android SDK platform 36、build-tools 36.0.0 和 platform-tools。无需设备配置、私有测试、备份或电视连接。

## 全新克隆并构建

```sh
git clone https://github.com/BeatsAD/quiet-tv.git
cd quiet-tv
```

设置 `ANDROID_HOME`，或在仓库根目录创建自己的 `local.properties`：

```properties
sdk.dir=/absolute/path/to/your/android-sdk
```

```sh
./gradlew :app:assembleDebug --console=plain
```

输出为 `app/build/outputs/apk/debug/app-debug.apk`。Windows 使用 `gradlew.bat`；各主机本地脚本仍需验证。详见[贡献指南](../CONTRIBUTING.md)。

交付归档先配置 `local.properties` 中的 `sdk.dir`，将 `FIRST_BUILD_NUMBER` 设为高于全部已用号的未占用正整数；1.2.4 的 64–67 已发布。整批会依次分配四个数字构建号：

```sh
python3 scripts/build_apk.py --batch --build-number "$FIRST_BUILD_NUMBER" --variant benchmark --summary "说明本次构建的修改"
```

脚本按固定提交顺序构建 ARM32、ARM64、通用和安卓4.4包，检查版本、签名和原生库一致性，在 `dist/apk/` 生成不可变 APK、同名 SHA-256、JSON 和批次清单。本地归档名保留构建号，公开名省略构建号；脚本不安装电视或上传发布。`benchmark` 使用测试签名，不是正式包。已发布后交付修改过的内容须增加软件版本号，详见[版本规则](VERSIONING.md)。

仅构建标准单包可省略 `--batch`，指定 `--abi armeabi-v7a`、`arm64-v8a` 或 `universal`；最低 API 默认21，可按实际适配选择23、26或31。

## Android 4.4 / API19

使用独立兼容档位，不能只降低标准版最低 API。`LEGACY_BUILD_NUMBER` 同样须是高于全部已用号的未占用数字：

```sh
python3 scripts/build_apk.py --build-number "$LEGACY_BUILD_NUMBER" --compatibility legacy19 --variant benchmark --summary "Android 4.4 兼容测试版"
```

不另传 `--min-sdk` 时自动选择 API19。构建与 Lint 可用 `./gradlew :app:assembleDebug :app:lintDebug -PquietCompatibility=legacy19 --console=plain`。兼容版保留核心桌面、天气、壁纸、应用管理、儿童模式、关于和离线许可证，使用传统 TCP ADB；API19及厂商真实 HOME、遥控器、网络、重启与唤醒仍需目标电视验收。Debug 采用旧版 MultiDex 启动支持。

## 下载、安装与试用

从 [1.2.4 发布页](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.2.4)选择一个完整 APK：

| 系统 | 架构 | 内部构建号 | 下载 | 大小 |
| --- | --- | --- | --- | --- |
| 安卓5+ | ARM32 | 64 | [QuietTV_1.2.4_安卓5+_ARM32_测试.apk](https://github.com/BeatsAD/quiet-tv/releases/download/v1.2.4/QuietTV_1.2.4_Android5plus_ARM32_Test.apk) | 3.74 MiB |
| 安卓5+ | ARM64 | 65 | [QuietTV_1.2.4_安卓5+_ARM64_测试.apk](https://github.com/BeatsAD/quiet-tv/releases/download/v1.2.4/QuietTV_1.2.4_Android5plus_ARM64_Test.apk) | 3.87 MiB |
| 安卓5+ | 通用 | 66 | [QuietTV_1.2.4_安卓5+_通用_测试.apk](https://github.com/BeatsAD/quiet-tv/releases/download/v1.2.4/QuietTV_1.2.4_Android5plus_Universal_Test.apk) | 7.04 MiB |
| 安卓4.4 | 无原生库 | 67 | [QuietTV_1.2.4_安卓4.4_测试.apk](https://github.com/BeatsAD/quiet-tv/releases/download/v1.2.4/QuietTV_1.2.4_Android4.4_Test.apk) | 3.95 MiB |

以电视操作系统支持的架构为准；64位处理器可能运行32位系统。优先选择匹配的单架构包，无法确认时用安卓5+通用备用包。每台设备只安装一个，不能将构建号最大的包当作所有设备的推荐包。

GitHub 使用英文实际下载名和中文显示名。新发布仅上传 APK，SHA-256 可从 [GitHub 发布 API](https://api.github.com/repos/BeatsAD/quiet-tv/releases/tags/v1.2.4) 对应附件的 `digest` 核对；不要求下载独立校验文件。计算下载文件的值，例如：

```sh
shasum -a 256 QuietTV_1.2.4_Android5plus_ARM32_Test.apk
```

历史版本的附件、链接和校验文件保留。安装前核对实际 APK 的包名、版本、构建号、最低 API、架构及签名。更新需相同包名和签名、内部构建号更高；同批通用包切换到较低构建号单架构包不满足升级条件。没有合适更新包时保留现状，不卸载、清数据或强制降级。

电视支持时可通过文件管理器手动安装，来源授权由用户在系统设置中确认。通过电脑 ADB 安装时，先开启电视实际提供的调试入口，电脑与电视处于同一可互访局域网，并在电视上确认电脑授权。仅开启 USB 调试不保证网络 ADB 可用，详见[连接与授权](TECHNICAL_PATH_WITH_CODEX.zh-CN.md#二连接电视读取真实设备能力)。

先读取设备当前用户及命令帮助，核对 `--user` 支持；将 `TV_SERIAL`、`TV_USER` 设为实际目标地址和用户编号。下例地址为占位，用户0仅为示例：

```sh
TV_SERIAL=192.0.2.10:5555
TV_USER=0
adb connect "$TV_SERIAL"
adb -s "$TV_SERIAL" shell am get-current-user
adb -s "$TV_SERIAL" install -r --user "$TV_USER" QuietTV_1.2.4_Android5plus_ARM32_Test.apk
adb -s "$TV_SERIAL" shell am start --user "$TV_USER" -n home.quiet.tv/.HomeActivity
```

安装后读取实际版本并拉取已安装 APK 核对 SHA-256，不能仅凭 `Success` 判断更新完成。新安装先选“暂不设置，临时使用”；更新时保持原 HOME、壁纸、排序和应用状态，并实际检查。默认桌面、实体 HOME、重启及待机唤醒分别验收，恢复入口见[首次设置与恢复](SETUP.md)。

已有应用内更新入口时，可在“应用管理 → 关于 → 检查更新”下载并确认安装。1.2.4 签名清单已上线；同版本不会因构建号不同重复提示升级。没有更新入口的旧版先手动安装，详见[应用内更新](UPDATES.md)。
