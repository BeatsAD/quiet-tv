# 安装与构建

[English](INSTALL.en.md) · [首页](../README.md) · [兼容范围](COMPATIBILITY.md)

## 普通用户安装

已有 APK 的使用者优先阅读[Agent 安装与恢复说明](AGENT_INSTALL.zh-CN.md)或本页“下载、安装与试用”。下方构建环境只用于从源码构建，使用已发布 APK 不需要 JDK、Gradle 或完整 Android SDK。

## 环境

当前版本为 [1.1.2 测试版](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.2)：标准包为构建号 34，最低 Android 5.0 / API21；`legacy19` 兼容包为构建号 35，最低 Android 4.4 / API19，均为 targetSdk36。按系统选包，不按构建号大小选包。两档主要功能相同；标准包包含四种 CPU 架构，兼容包不含原生库，不提供六位码配对，信号源通过系统设置或遥控器进入。构建、下载与设备验收范围见[兼容说明](COMPATIBILITY.md)。历史包保留原链接。

需要 Git、Python 3、JDK 17 或 21、Android SDK platform 36、build-tools 36.0.0 和 platform-tools。构建无需设备配置、备份、私有测试目录或电视连接。

## 全新克隆并构建

归档前将 `STANDARD_BUILD_NUMBER` 设为高于所有已用号的未占用正整数；`LEGACY_BUILD_NUMBER` 须另设为比它更高的未占用号。两个新包不能复用同一构建号；1.1.2 的 34、35 已发布，不可用于新内容。

```sh
git clone https://github.com/BeatsAD/quiet-tv.git
cd quiet-tv
```

设置标准环境变量 `ANDROID_HOME`，或在仓库根目录创建自己的 `local.properties`：

```properties
sdk.dir=/absolute/path/to/your/android-sdk
```

```sh
./gradlew :app:assembleDebug --console=plain
```

输出为 `app/build/outputs/apk/debug/app-debug.apk`。Windows 使用 `gradlew.bat`；Python、脚本和 Windows 本地交互仍需各主机验证。协作检查命令见[贡献指南](../CONTRIBUTING.md)。

若要归档构建，先配置 `local.properties` 中的 `sdk.dir`，再运行：

```sh
python3 scripts/build_apk.py --build-number "$STANDARD_BUILD_NUMBER" --summary "说明本次构建的修改"
```

本批已发布标准版归档为 `dist/apk/QuietTV_1.1.2-34_安卓5及以上_测试版.apk`；新构建按实际构建号生成本地归档，同时输出 `.apk.sha256` 和 `.json`。每个新交付包使用更高且未占用的 `--build-number`，本地归档名中的构建号与 APK 内部 versionCode 一致；公开下载名省略构建号，由 JSON 的 `public_apk` 记录，不额外生成副本。新名称使用 `安卓5+` 等简短系统标签，按实际内容标明 `ARM32`、`ARM64` 或 `通用`，无原生库时省略架构，状态使用 `测试`。历史归档保留原样。可选 `--variant benchmark --abi armeabi-v7a` 及 `--min-sdk 21`（默认）/ `23` / `26` / `31`，详见[版本规则](VERSIONING.md)。这些命令不会安装或修改电视。

## Android 4.4 / API19

使用独立兼容档位构建和归档；先设置 `LEGACY_BUILD_NUMBER`，必须高于标准包和其他已归档包的构建号：

```sh
python3 scripts/build_apk.py --build-number "$LEGACY_BUILD_NUMBER" --compatibility legacy19 --variant benchmark --summary "Android 4.4 兼容测试版"
```

本批已发布兼容包归档为 `dist/apk/QuietTV_1.1.2-35_安卓4.4_测试版.apk`。不另传 `--min-sdk` 时自动选择 API19；标准档不能用降低数字的方式生成 API19 包。直接构建与 Lint 可执行 `./gradlew :app:assembleDebug :app:lintDebug -PquietCompatibility=legacy19 --console=plain`。

兼容版保留核心桌面、Dock、壁纸、应用列表、关于与离线许可证，使用传统 TCP ADB；不提供六位码配对。信号源按钮打开系统设置并提示使用遥控器信号源键，厂商直切需单独适配。APK 不含原生库；ARM32 运行、真实 HOME、网络切换和重启唤醒仍需目标电视验证。Debug 采用旧版 MultiDex 启动支持。

两个档位共用包名和测试签名，会覆盖安装；切换档位前保留原包与配置，不通过卸载清空设置解决版本或签名问题。

## 下载、安装与试用

从 [1.1.2 Releases](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.2) 按系统选择：

| 系统 | GitHub 实际下载文件名 | 大小 |
| --- | --- | --- |
| 安卓 4.4 | `QuietTV_1.1.2-35_Android4.4_Test.apk` | 4,900,169 字节，约 4.67 MiB |
| 安卓 5 及以上 | `QuietTV_1.1.2-34_Android5plus_Test.apk` | 9,370,356 字节，约 8.94 MiB |

GitHub 附件用英文下载名和中文标签，本地中文归档保留。选择 APK 与实际英文名同名的 `.apk.sha256`，放在同一目录；不要用标为“原中文文件名校验记录”的历史记录检查英文名文件。例如安卓 5 及以上：

```sh
shasum -a 256 -c QuietTV_1.1.2-34_Android5plus_Test.apk.sha256
```

安卓 4.4 改用 `QuietTV_1.1.2-35_Android4.4_Test.apk.sha256`。两档使用与历史 b26／b30 相同的测试签名，不能并存；跨档位覆盖仍需满足签名、递增构建号、系统与数据兼容条件。所有[历史下载](https://github.com/BeatsAD/quiet-tv/releases)及其原校验文件保留。

先在电视“设置 → 系统／设备偏好设置 → 关于”中找到 Build／编译版本号，通常连续按遥控器确定约 7 次可开启“开发者选项”，再进入该页面开启实际支持的 USB／ADB 调试。网络连接还需核对网络调试开关、地址和端口；仅开启 USB 调试不保证网络 ADB 可用。详细菜单位置、开启和授权步骤见[技术路径文档第二节](TECHNICAL_PATH_WITH_CODEX.zh-CN.md#二连接电视读取真实设备能力)。

使用网络 ADB 时，电脑与电视需处于同一可互访局域网，并在电视上确认电脑授权。下方地址是文档占位，必须替换成自己的设备地址和实际调试端口。示例安装自行构建的 Debug APK；使用下载包时，将 APK 路径替换为下载文件的实际路径：

```sh
TV_SERIAL=192.0.2.10:5555
adb connect "$TV_SERIAL"
adb -s "$TV_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$TV_SERIAL" shell am start -n home.quiet.tv/.HomeActivity
```

升级覆盖安装需要相同签名且内部 `versionCode` 递增；关于显示用户可见版本，安装包名称同时标明实际构建号。签名不匹配时不要直接卸载解决；卸载会清除应用私有设置和恢复记录，应先核对旧包来源及备份。

新安装会进入[首次设置](SETUP.md)，可选择“暂不设置，临时使用”，先检查遥控器、应用和系统入口。切回原桌面使用“应用管理 → 更多”的恢复入口；资格与限制见[应用管理](APP_MANAGEMENT.md)。未知固件不套用其他设备的禁用清单。
