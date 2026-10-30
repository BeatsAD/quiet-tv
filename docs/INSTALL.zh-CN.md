# 安装与构建

[English](INSTALL.en.md)  [首页](../README.md)  [兼容范围](COMPATIBILITY.md)

## 普通用户安装

当前公开版本与下载地址由[最新版索引](https://beatsad.github.io/quiet-tv/latest.json)自动维护，包含测试版；也可直接使用[安装页](https://beatsad.github.io/quiet-tv/)。使用已发布 APK 不需要 JDK、Gradle 或完整 Android SDK。可阅读[Agent 安装与恢复说明](AGENT.md)，或按下方“下载、安装与试用”操作。

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

交付归档先配置 `local.properties` 中的 `sdk.dir`，将 `FIRST_BUILD_NUMBER` 设为高于全部已用号的未占用正整数；已发布及本地候选用号均保留。当前无原生库批次依次分配两个数字构建号：

```sh
python3 scripts/build_apk.py --batch --universal-only --build-number "$FIRST_BUILD_NUMBER" --variant benchmark --summary "说明本次构建的修改"
```

脚本固定源码提交，依次构建标准通用包和安卓4.0至4.4通用包，检查版本、签名及两包均无原生库；存在原生库架构差异时，省略 `--universal-only` 构建标准 ARM32、ARM64、通用及一个 legacy14 包。在 `dist/apk/` 生成不可变 APK、同名 SHA-256、JSON 和批次清单。后续主程序名称包含软件版本、适用系统和发布状态，不含构建号；构建号继续写入 APK 内部及 JSON，`public_apk` 记录英文公开名，历史名称保留；脚本不安装电视或上传发布。`benchmark` 使用测试签名，不是正式包。同版本、系统、架构和状态已归档不同内容时，须同时增加软件版本号和内部构建号，详见[版本规则](VERSIONING.md)。

仅构建标准单包可省略 `--batch`，指定 `--abi armeabi-v7a`、`arm64-v8a` 或 `universal`；最低 API 默认21，可按实际适配选择23、26或31。

<a id="android-44--api19"></a>

## Android 4.0–4.4 / API14–19

使用独立兼容档位，不能只降低标准版最低 API。`LEGACY_BUILD_NUMBER` 同样须是高于全部已用号的未占用数字：

```sh
python3 scripts/build_apk.py --build-number "$LEGACY_BUILD_NUMBER" --compatibility legacy14 --variant benchmark --summary "Android 4.0–4.4 兼容测试版"
```

最低 API 固定14，一个 APK 覆盖安卓4.0–4.4。构建与 Lint 可用 `./gradlew :app:assembleDebug :app:lintDebug -PquietCompatibility=legacy14 --console=plain`。兼容版与标准版共用核心桌面、天气、壁纸、应用管理、儿童模式、关于和离线许可证；当前源码保留本机 TCP ADB 日常管理，首次端口配置及授权由电脑完成，不提供应用内无线配对。`legacy19` 固定 API19，仅保留历史构建和升级回归。API14–19实际运行、覆盖升级、实体 HOME、遥控器、网络、重启与唤醒验收仍延期。Debug 采用旧版 MultiDex 启动支持，详见[兼容范围](COMPATIBILITY.md)。

## 下载、安装与试用

从[安装页](https://beatsad.github.io/quiet-tv/)或[最新版索引](https://beatsad.github.io/quiet-tv/latest.json)按系统、架构及升级条件选择最新适配 APK，包含测试版。索引中的 `url` 是真实附件地址，`bytes` 为大小，`sha256` 为校验值；不要根据旧文档或文件命名猜测当前包名和下载链接。

下载后计算文件 SHA-256，与所选附件记录逐字核对，例如将变量设为本次实际下载的文件路径：

```sh
shasum -a 256 "$APK_PATH"
```

历史附件有同名 `.apk.sha256` 时也可使用；缺少独立校验文件不代表没有新版。跨档位覆盖仍需核对实际包名、签名、递增构建号、系统及数据兼容条件，不卸载或清数据解决不匹配。所有[历史下载](https://github.com/BeatsAD/quiet-tv/releases)及原校验文件保留。

电视支持时可通过文件管理器手动安装，来源授权由用户在系统设置中确认。通过电脑 ADB 安装时，先开启电视实际提供的调试入口，电脑与电视处于同一可互访局域网，并在电视上确认电脑授权。仅开启 USB 调试不保证网络 ADB 可用，详见[连接与授权](TECHNICAL_PATH_WITH_CODEX.zh-CN.md#二连接电视读取真实设备能力)。

先读取设备当前用户及命令帮助，核对 `--user` 支持；将 `TV_SERIAL`、`TV_USER` 设为实际目标地址和用户编号。将 `APK_PATH` 设为本次实际下载且已校验的 APK 路径。下例地址为占位，用户0仅为示例：

```sh
TV_SERIAL=192.0.2.10:5555
TV_USER=0
adb connect "$TV_SERIAL"
adb -s "$TV_SERIAL" shell am get-current-user
adb -s "$TV_SERIAL" install -r --user "$TV_USER" "$APK_PATH"
adb -s "$TV_SERIAL" shell am start --user "$TV_USER" -n home.quiet.tv/.HomeActivity
```

安装后读取实际版本并拉取已安装 APK 核对 SHA-256，不能仅凭 `Success` 判断更新完成。当前源码首次打开直接进入桌面，不再强制设置向导；AI 已完成的桌面配置直接沿用，未配置时可先试用，再按授权配置。实际旧版 APK 若仍有引导，可选“暂不设置，临时使用”或跳过应用管理。更新时保持原 HOME、壁纸、排序和应用状态，并实际检查。预装应用维护交给电脑 AI，电视端保留“帮助与恢复”入口，见[桌面配置与恢复](SETUP.md)。默认桌面、实体 HOME、重启及待机唤醒分别验收。

已有应用内更新入口时，可在“应用管理 → 版本更新 → 检查更新”（旧版为“关于”）下载并确认安装。1.2.4 签名清单已上线；同版本不会因构建号不同重复提示升级。没有更新入口的旧版先手动安装，详见[应用内更新](UPDATES.md)。
