# 版本与安装包规则

[首页](../README.md) · [安装与构建](INSTALL.zh-CN.md) · [English installation](INSTALL.en.md)

唯一版本配置位于 `app/build.gradle`。当前源码为 `versionName 1.1.0`、`versionCode 29`，默认 `standard` / minSdk21（Android 5.0）；`legacy19` 固定 minSdk19（Android 4.4）。两档保持 targetSdk36、同一包名及测试签名，不能同时安装；覆盖升级还需匹配签名且构建号不回退。

[b29 安卓 4.4 兼容优化测试包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b29)为 `quiet-tv_1.1.0_b29_benchmark_legacy19_api19.apk`，2,347,533 字节（约 2.24 MiB），最低 API19，无原生库，使用与 b26 相同的测试证书。包内版本为 1.1.0 / 29，发布标签为 `v1.1.0-b29`。基于公开 main 新构建并保留不可变归档，生产源码提交为 `f15674a`；历史 b28 归档未覆盖。构建与验收边界见[兼容说明](COMPATIBILITY.md)。

[b26 标准版通用测试包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b26)继续保留：最低 API21、6,819,366 字节，包含四种 CPU 架构。历史 [b24 / API23 通用包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b24) 与 [b23 / API31 / ARM32 包](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0) 均保留。b29 与 b26 是不同兼容档位，不代表相同功能范围。

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

默认输出 `dist/apk/quiet-tv_1.1.0_b29_debug_api21.apk` 及 JSON。可指定 `--variant benchmark`、`--abi armeabi-v7a`、`--min-sdk 21`（默认）、`23`、`26` 或 `31`；优化包同时保存映射文件。`--compatibility legacy19` 使用 API19（不接受其他最低版本），包名增加 `_legacy19` 标记；标准档不接受 API19。旧系统归档必须通过 v1 签名验证。兼容包移除了原生库，`packaged_abis` 为空表示纯 DEX/资源 APK，`--abi` 是构建请求而非实机架构验收。

归档记录保存 APK 版本、签名、ABI、最低系统、时间、SHA-256、变更摘要和生产输入指纹。同名同字节可复用；同名不同内容必须递增构建号，不覆盖既有文件。构建期间生产输入改变则停止归档。

Gradle 的 `app/build/outputs/` 是可更新的中间输出；`dist/apk/` 为本地不可变交付归档，不随源码发布。现有归档、配置、密钥和恢复记录全部保留。

## 安装与发布边界

构建、归档、安装、已安装包哈希核对、遥控器与功能验收分别记录。工作区提交和脏状态描述构建环境，不能单独证明设备已安装该版本。历史校验只证明对应时间的状态。

CI 仅检查源码，不安装设备或发布 APK。设备升级与正式发布按明确授权执行；升级应保持签名及数据，并核对真实版本。源码整理不改变生产功能或自动发布新包。

## English

Source is 1.1.0 / b29 with `standard` (minSdk21) and `legacy19` (minSdk19) profiles, both targeting API36. [b29 compatibility](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.0-b29) is `quiet-tv_1.1.0_b29_benchmark_legacy19_api19.apk`, 2,347,533 bytes (about 2.24 MiB), with no native libraries and v1/v2 test signatures matching b26. Its production-source commit is `f15674a`; the archive is immutable and historical b28 files remain unchanged. The tag is `v1.1.0-b29`, while the in-app version remains 1.1.0 / 29. Native emulator and physical-TV acceptance of b29 remain pending. b26 standard, b24 and b23 downloads remain available; the profiles have different feature scope.

Read versions from app/build.gradle and actual APK metadata. Increment the global build number for each different delivered build. Debug, optimized test and formally signed release builds are distinct. Archive naming includes ABI when selected and the minimum SDK suffix except for historical API31 names. Archives and receipts are immutable, remain local, and do not establish device installation or acceptance. Publishing and device operations require explicit scope.
