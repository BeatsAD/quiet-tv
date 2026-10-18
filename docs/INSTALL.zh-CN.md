# 安装指南

[English](INSTALL.en.md) · [返回首页](../README.md) · [兼容性](COMPATIBILITY.md)

## 1. 确认设备与环境

当前 APK 的 minSdk/targetSdk 均为 31，只在 Sony KD-65X9000H / Android 12 做过实机适配。其他 Android 12 及以上电视需要单独试用；Android 11 及以下不能直接安装当前 APK。非 Android 系统不适用。

已使用的开发主机为 macOS。需要 Git、JDK 17 或 21、Android SDK platform 36 / build-tools 36.0.0 / platform-tools，以及 Python 3（仅使用辅助脚本时需要）。Windows 和 Linux 的构建、脚本与设备交互尚未验收，`.command` 为 macOS 入口。

电视与电脑在可信局域网内，开启设备实际支持的 ADB 调试，并在电视上确认授权。现有 Python 和安装／恢复脚本固定使用 TCP 5555；新式配对码调试、自定义端口没有适配。

## 2. 获取源码并构建

私密仓库需要你的 GitHub 账号已有访问权限。首次克隆：

```sh
git clone https://github.com/BeatsAD/quiet-tv.git
cd quiet-tv
```

通过 Android SDK Manager 安装 platform 36、build-tools 36.0.0 和 platform-tools。在仓库根目录新建 `local.properties`，写入你自己的 SDK 绝对路径，例如：

```properties
sdk.dir=/absolute/path/to/your/android-sdk
```

在根目录运行：

```sh
python3 scripts/build_apk.py --summary "说明本次交付的变更"
```

归档输出：`dist/apk/quiet-tv_1.0.1_b2_debug.apk`，同目录 JSON 保存版本、时间、SHA-256 和变更摘要。这一步只构建和归档，不连接或修改电视。包名为 `home.quiet.tv`，当前配置为版本 1.0.1、构建号 2、类型 debug；尚无已发布的稳定版 APK。版本递增、历史包和安装核对约定见[版本与安装包规则](VERSIONING.md)。

## 3. 连接并试用桌面

以下示例中的 `192.0.2.10` 是文档占位地址，必须改成目标电视的实际 IP。命令中的 `adb` 需来自你自己的 platform-tools 并可在 PATH 中找到。

```sh
TV_SERIAL=192.0.2.10:5555
adb connect "$TV_SERIAL"
adb -s "$TV_SERIAL" shell getprop ro.product.model
adb -s "$TV_SERIAL" shell getprop ro.build.version.sdk
adb -s "$TV_SERIAL" shell pm list users
adb -s "$TV_SERIAL" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

确认目标身份，保存最后一条命令返回的原 HOME 组件，并保留原桌面。安装并启动：

```sh
adb -s "$TV_SERIAL" install -r dist/apk/quiet-tv_1.0.1_b2_debug.apk
adb -s "$TV_SERIAL" shell am start -n home.quiet.tv/.HomeActivity
```

先验证遥控器、信号源、设置和应用启动。确认可用后，可在电视的默认应用／桌面设置中选择 Quiet TV。设备支持标准 HOME 切换时，也可执行：

```sh
adb -s "$TV_SERIAL" shell cmd package set-home-activity --user 0 home.quiet.tv/.HomeActivity
adb -s "$TV_SERIAL" shell input keyevent KEYCODE_HOME
```

上述 `--user 0` 只适用于已确认目标用户为 0 的设备。其他用户或厂商限制需要单独适配；这一步无需禁用原桌面。

更新安装需要原安装与新 APK 的签名一致。不同电脑的 debug 密钥可能不同；遇到签名不匹配时，使用原签名密钥，不为更新直接卸载并丢失已有设置。

## 4. 使用本项目辅助脚本

`scripts/tv.py` 从根目录 `device.json` 读取配置。可参考 [device.example.json](examples/device.example.json) 的字段，手动填写你自己的 IP、ADB 和 SDK 路径；不要覆盖仍在使用的设备记录。

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python scripts/tv.py shell 'getprop ro.product.model'
```

`./build-and-install.command --summary "说明本次更新内容"` 会构建和归档带版本号的包、安装更新、核对两端 SHA-256、启动桌面、发送一次右键并截图。它会操作 `device.json` 指向的设备，运行前必须核对目标。安装核对结果单独保存在带时间戳的 JSON 中，命名见[版本规则](VERSIONING.md)。

macOS 原生 ADB 出现 `No route to host` 且已确认网络和端口可达时，可使用 Python 后端：

```sh
TV_ADB_BACKEND=python .venv/bin/python scripts/tv.py shell 'getprop ro.product.model'
TV_ADB_BACKEND=python ./build-and-install.command --summary "说明本次更新内容"
```

该后端复用本机 `~/.android/adbkey` 和公钥；不要上传私钥。

## 5. 系统精简与恢复边界

安装桌面不要求先精简系统。`changes.json`、`backup-current.json`、包清单和 `preferences.json` 是已有索尼设备的实施记录，不是通用预设。

`backups/`、`evidence/`、`inspection/` 被 Git 忽略，新克隆不包含原始设备备份和完整证据。`restore.command` 依赖 `changes.json` 指向的原始备份；新克隆或其他设备不能直接运行它。`scripts/backup.py` 在已有 journal 时会保留原基线，不会自动创建其他设备的新恢复计划。

需要禁用系统包时，为目标设备另建备份与变更记录，逐项确认用户取舍、原状态和恢复方式后再操作。不要复用其他设备的包名单。当前脚本中的用户 0、5555 端口及索尼恢复记录都需要逐机核对。

只试用了桌面而未禁用系统包时，可通过系统设置切回原桌面；若使用 ADB，应使用事先记录的原 HOME 组件和正确用户 ID。已有索尼设备的细节见 [设备实施记录](DEVICE_NOTES.zh-CN.md)。
