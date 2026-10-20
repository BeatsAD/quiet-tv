# 兼容范围 / Compatibility

[中文首页](../README.md) · [English home](../README.en.md) · [安装指南](INSTALL.zh-CN.md)

## 源码与下载 / Source and download

| 内容 / Build | 范围 / Scope |
| --- | --- |
| 当前源码 1.1.0 / b24 | minSdk23（Android 6.0），targetSdk36；默认通用 Debug。新构建仍需目标固件验收。 / Source compatibility candidate; hardware validation required. |
| 已发布 b23 ARM32 测试包 | Android 12 / API31 起，需支持 armeabi-v7a；仅支持 64 位应用的设备不可用。 / Requires Android 12 and ARM32 app support. |
| Android 5.1 及以下、Tizen、webOS 等非 Android 系统 | 不支持 / Unsupported |

源码兼容处理包括旧系统网络广播与字体回退、系统 HOME 入口检测、旧遥控器及新版系统返回导航，以及按本机实际命令能力开放的应用管理。Android 版本符合最低要求不等于厂商固件全部功能已通过验收。

Source includes legacy networking/font fallbacks, detected HOME settings, Back handling and capability-based package operations. Meeting the minimum Android version does not establish vendor-firmware compatibility.

## 已验证与待验收 / Evidence boundaries

- 历史兼容分支曾在 API23、31、36、37 模拟环境验证部分页面和操作；通用 Android 镜像不能代替 TV 固件，历史分支结果也不能直接视作集成包全部通过。
- 已发布 b23 曾在一台 Android 12 / ARM32 电视覆盖安装、核对 APK 哈希，并检查 HOME 返回、壁纸和 Dock；范围见 [Releases](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0)。
- 当前 b24 的构建、Lint 和本地已有测试有记录；其他厂商、旧系统真机、真实 HOME 切换／恢复、长期驻留、投屏和完整播放仍需分别验收。
- 无线配对仍为实验入口，要求 API33+、可加载原生库及设备实际提供配对服务；六位码成功配对不能由版本号或构建通过代替。
- 未知固件可读取应用，未经匹配和核验的系统包操作保持只读；不能套用其他设备的禁用清单。

Historical emulator checks and published b23 hardware checks are version-specific. They do not establish full acceptance of b24, other vendors, real HOME changes, pairing, casting or long-running operation. Unknown firmware retains read-only system-package behavior.

## 默认桌面与恢复

设备需实际提供可用的 HOME 入口、系统设置、TV Input、遥控键和本机调试能力。默认桌面以读回的真实组件为准；命令返回 Success 不代表 HOME 已切换。保留原桌面及恢复记录，先验证再决定是否处理预装应用。流程见[首次设置](SETUP.md)和[应用管理](APP_MANAGEMENT.md)。
