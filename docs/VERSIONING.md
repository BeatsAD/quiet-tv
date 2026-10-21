# 版本与安装包规则

[首页](../README.md) · [安装与构建](INSTALL.zh-CN.md) · [English installation](INSTALL.en.md)

唯一版本配置位于 `app/build.gradle`。当前源码为 `versionName 1.1.0`、`versionCode 26`，默认 minSdk21（Android 5.0）、targetSdk36。b26 尚未发布；当前公开下载为 [b24 通用兼容优化测试包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b24)，最低 Android 6.0 / API23；历史 [b23 / API31 / ARM32 包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0) 保留。

公开 b24 文件为 `quiet-tv_1.1.0_b24_benchmark_api23.apk`，约 12.30 MiB，包含 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64`，与 b23 使用相同测试签名。发布标签为 `v1.1.0-b24`，包内 `versionName` 仍为 `1.1.0`。构建、Lint、签名及回下载哈希检查通过；本次包尚未安装到电视或模拟器，不能表述为已完成机型适配。

## 命名与签名

- 文件名为 `quiet-tv_<versionName>_b<versionCode>_<buildType>[_<ABI>][_apiN].apk`；通用架构不加 ABI，API31 保留历史无 SDK 后缀命名，其他最低版本显式追加 `_apiN`。
- 每次交付不同内容的新包递增全局构建号，不回退或重用；功能版本、修订版本按改动更新。
- `debug` 用于开发；`benchmark` 使用测试签名并启用代码与资源裁剪；`release` 必须配置持久正式签名，未签名包不得作为正式发布归档。
- 版本、构建号、ABI 和最低系统均以实际 APK 元数据为准，不能通过改名冒充新版本。

## 构建与归档

先按[安装指南](INSTALL.zh-CN.md)配置 SDK，归档脚本读取 `local.properties` 的 `sdk.dir`：

```sh
python3 scripts/build_apk.py --summary "说明本次更新内容"
```

默认输出 `dist/apk/quiet-tv_1.1.0_b26_debug_api21.apk` 及 JSON。可指定 `--variant benchmark`、`--abi armeabi-v7a`、`--min-sdk 21`（默认）、`23`、`26` 或 `31`；优化包同时保存映射文件。

归档记录保存 APK 版本、签名、ABI、最低系统、时间、SHA-256、变更摘要和生产输入指纹。同名同字节可复用；同名不同内容必须递增构建号，不覆盖既有文件。构建期间生产输入改变则停止归档。

Gradle 的 `app/build/outputs/` 是可更新的中间输出；`dist/apk/` 为本地不可变交付归档，不随源码发布。现有归档、配置、密钥和恢复记录全部保留。

## 安装与发布边界

构建、归档、安装、已安装包哈希核对、遥控器与功能验收分别记录。工作区提交和脏状态描述构建环境，不能单独证明设备已安装该版本。历史校验只证明对应时间的状态。

CI 仅检查源码，不安装设备或发布 APK。设备升级与正式发布按明确授权执行；升级应保持签名及数据，并核对真实版本。源码整理不改变生产功能或自动发布新包。

## English

Source is 1.1.0 / b26 with minSdk21 (Android 5.0) and targetSdk36; b26 has not been published. The current download is the universal optimized b24 test APK, tagged `v1.1.0-b24`, with package version `1.1.0` / code `24`, Android 6.0 / API23 minimum, and ARM32, ARM64, x86, and x86_64 libraries. It is about 12.30 MiB and uses the same test signature as b23. Package checks do not establish hardware acceptance; this b24 APK has not been installed on a TV or emulator. Historical b23 remains available under `v1.1.0`.

Read versions from app/build.gradle and actual APK metadata. Increment the global build number for each different delivered build. Debug, optimized test and formally signed release builds are distinct. Archive naming includes ABI when selected and the minimum SDK suffix except for historical API31 names. Archives and receipts are immutable, remain local, and do not establish device installation or acceptance. Publishing and device operations require explicit scope.
