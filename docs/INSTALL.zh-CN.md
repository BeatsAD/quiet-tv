# 安装与构建

[English](INSTALL.en.md) · [首页](../README.md) · [兼容范围](COMPATIBILITY.md)

## 环境

源码与当前公开 b24 通用兼容测试包均为 1.1.0 / b24，最低 Android 6.0 / API23、targetSdk36。下载包约 12.30 MiB，包含 ARM32、ARM64、x86 和 x86_64；本次包未安装到电视或模拟器，具体机型与功能仍待验证。历史 b23 ARM32 包最低 Android 12，保留原下载。源码构建、公开下载和真机验收分别记录。

需要 Git、Python 3、JDK 17 或 21、Android SDK platform 36、build-tools 36.0.0 和 platform-tools。构建无需设备配置、备份、私有测试目录或电视连接。

## 全新克隆并构建

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
python3 scripts/build_apk.py --summary "说明本次构建的修改"
```

默认归档为 `dist/apk/quiet-tv_1.1.0_b24_debug_api23.apk`；脚本支持 `--variant benchmark --abi armeabi-v7a` 及 `--min-sdk 26` / `--min-sdk 31`。签名、版本与不可覆盖约定见[版本规则](VERSIONING.md)。这些命令不会安装或修改电视。

## 下载、安装与试用

也可从 [b24 Releases](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b24) 下载 `quiet-tv_1.1.0_b24_benchmark_api23.apk` 与同名 SHA-256 校验文件，放在同一目录后执行：

```sh
shasum -a 256 -c quiet-tv_1.1.0_b24_benchmark_api23.apk.sha256
```

发布包与 b23 使用相同测试签名。历史 [b23 Releases](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0) 仅供 Android 12+ 且支持 ARM32 的设备使用；不要将其最低要求套用到 b24。

先在电视“设置 → 系统／设备偏好设置 → 关于”中找到 Build／编译版本号，通常连续按遥控器确定约 7 次可开启“开发者选项”，再进入该页面开启实际支持的 USB／ADB 调试。网络连接还需核对网络调试开关、地址和端口；仅开启 USB 调试不保证网络 ADB 可用。详细菜单位置、开启和授权步骤见[技术路径文档第二节](TECHNICAL_PATH_WITH_CODEX.zh-CN.md#二连接电视读取真实设备能力)。

使用网络 ADB 时，电脑与电视需处于同一可互访局域网，并在电视上确认电脑授权。下方地址是文档占位，必须替换成自己的设备地址和实际调试端口。示例安装自行构建的 Debug APK；使用下载包时，将 APK 路径替换为下载文件的实际路径：

```sh
TV_SERIAL=192.0.2.10:5555
adb connect "$TV_SERIAL"
adb -s "$TV_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$TV_SERIAL" shell am start -n home.quiet.tv/.HomeActivity
```

升级覆盖安装需要相同签名且构建号不回退。签名不匹配时不要直接卸载解决；卸载会清除应用私有设置和恢复记录，应先核对旧包来源及备份。

新安装会进入[首次设置](SETUP.md)，可选择“暂不设置，临时使用”，先检查遥控器、应用和系统入口。切回原桌面使用“应用管理 → 更多”的恢复入口；资格与限制见[应用管理](APP_MANAGEMENT.md)。未知固件不套用其他设备的禁用清单。
