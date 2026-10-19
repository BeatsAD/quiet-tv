# 版本与安装包规则

[首页](../README.md) · [安装指南](INSTALL.zh-CN.md) · [English installation](INSTALL.en.md)

## 命名与版本

统一安装包名：`quiet-tv_<版本号>_b<构建号>_<构建类型>.apk`。

例如：`quiet-tv_1.1.0_b8_debug.apk`。文件名中的版本号、构建号取自 Gradle 输出元数据，与 APK 内的 `versionName`、`versionCode` 一致，不通过单独重命名冒充新版本。

唯一版本配置在 `app/build.gradle` 的 `defaultConfig` 中。目前候选为 `versionName '1.1.0'`、`versionCode 8`；新增按确认原型实现的关于单屏，版本与构建号读取已安装包。构建、模拟器和真实设备验收分别记录。

| 字段 | 规则 |
| --- | --- |
| 版本号 | 使用 `主版本.功能版本.修订版本` 三段式。历史版本 `1.0` 原样保留。 |
| 修订版本 | 修复问题、调整样式或文案，例如 `1.0.1 → 1.0.2`。 |
| 功能版本 | 增加完整功能，例如 `1.0.2 → 1.1.0`。 |
| 主版本 | 重大改版或不兼容变化，例如 `1.x.x → 2.0.0`。 |
| 构建号 | 使用正整数，每次交付不同内容的新包必须递增，不回退、不重用；跨版本也继续递增。 |
| 同版本测试包 | 可保留版本号、递增构建号，例如 `1.0.1_b2 → 1.1.0_b8`。 |
| 构建类型 | 当前脚本仅构建 `debug`；`release` 保留给配置正式签名后的正式构建，不能把 debug 包改名当作正式包。 |

## 构建与归档

只构建和归档，不连接电视：

```sh
python3 scripts/build_apk.py --summary "规范安装包版本、命名和归档记录"
```

脚本执行 `:app:assembleDebug` 后，将 APK 归档到 `dist/apk/`，例如：

```text
dist/apk/quiet-tv_1.1.0_b8_debug.apk
dist/apk/quiet-tv_1.1.0_b8_debug.json
```

`--summary` 为必填的本次交付变更摘要。JSON 保存版本、构建类型、构建完成时间 `built_at`、APK 文件修改时间、归档时间、字节数、SHA-256、变更摘要，以及归档时的工作区提交和脏状态。

工作区信息只说明归档时的环境；工作区有未提交修改时，不能声称 APK 完全对应某个 Git 提交。历史包直接归档时不推测构建完成时间，以 APK 文件修改时间作参考。

同名归档不覆盖。相同字节、相同 SHA-256 的重复构建可复用既有归档与记录；同名但内容不同则终止归档，需递增 `versionCode` 后重新构建。变更摘要也不会覆盖既有记录。

`app/build/outputs/apk/debug/app-debug.apk` 仍是会被后续构建更新的中间输出；对外交付、手动安装和历史核对统一使用 `dist/apk/` 下的带版本文件。

`dist/apk/` 被 Git 忽略，APK 和含设备信息的安装记录仅保存在本地，不随源码提交或自动发布。保留历史归档，不自动清理，也不建立会随构建移动的“电视当前版本”快捷文件。

每次新交付先更新 `app/build.gradle` 中的版本与构建号，同步首页、安装指南及本页的当前版本示例，再运行上述归档命令。构建脚本不自动递增版本，避免重复构建意外占用新版本号。

## 安装与核对

构建、归档、更新安装、核对哈希、启动和截图：

```sh
./build-and-install.command --summary "说明本次更新内容"
```

此命令会操作 `device.json` 指向的电视，使用与原安装相同的签名密钥。先确认设备身份及安装授权。需要 Python ADB 后端时，在命令前加 `TV_ADB_BACKEND=python`。

只读取电视已安装包并核对，不安装或修改电视：

```sh
TV_ADB_BACKEND=python .venv/bin/python scripts/verify_apk.py \
  dist/apk/quiet-tv_1.1.0_b8_debug.apk
```

核对使用 `pm path home.quiet.tv` 返回的实际已安装 APK，比较本地与电视文件的 SHA-256。每次另存带时间戳的 `*.installed-<时间>.json`，包含核对时间、设备、路径、两端哈希和 `matches`；不把安装状态写入不可变的构建归档记录。哈希不一致则返回失败并保留核对记录。

只有实际安装后且两端 SHA-256 一致，才能在该次交付记录中标记为“电视当前版本”。历史匹配记录仅证明当时状态，后续安装后应重新核对。构建通过、安装通过、哈希一致与真实界面及功能验收分别报告。

## 规则启用时的基线

- 2026-10-18 原有包按真实内部版本归档为 `quiet-tv_1.0_b1_debug.apk`，不重新构建、不改内部版本。其 SHA-256 为 `acd73dfe1d1965b4dbe7246bcee28e5e0cc8a26af7d15053a7453708776a961e`，当日 16:41（Asia/Shanghai）再次只读核对，与电视已安装包一致。
- 首个采用新配置的包为 `quiet-tv_1.0.1_b2_debug.apk`。本次规则实施只构建和归档该包，不更新电视；电视仍为上述 `1.0 / b1` 基线。

## English summary

Use `quiet-tv_<versionName>_b<versionCode>_<buildType>.apk`. Versions follow major/minor/patch; increment the global integer `versionCode` for each different delivered build, including test revisions of the same version. `app/build.gradle` is the version source. The historical `1.0/b1` APK retains its actual version; the naming rules started at `1.0.1/b2`. The current candidate build is `1.1.0/b8`; the About screen reads installed package metadata, and physical-device acceptance remains separate.

Run `python3 scripts/build_apk.py --summary "Describe the changes"` to build and archive without contacting a TV. Immutable APKs and JSON receipts live in Git-ignored `dist/apk/`. Identical APKs reuse the original receipt; different bytes under an occupied name require a new build number. Receipts include timestamps, SHA-256 and the change summary. Workspace commit/dirty fields describe the archive environment, not proof of an exact source revision. Direct Gradle builds still produce the replaceable `app-debug.apk` intermediate.

`./build-and-install.command --summary "Describe the update"` builds, archives, installs, compares SHA-256, then starts the launcher and captures a screenshot. `scripts/verify_apk.py <archived-apk>` only reads the installed package and creates a separate timestamped installation receipt. A historical match proves only the state at that check. Debug and release builds must remain clearly distinguished; the current scripts only build debug APKs.
