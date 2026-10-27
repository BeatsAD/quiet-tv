# 贡献指南 / Contributing

[中文首页](README.md) · [English home](README.en.md) · [行为准则 / Code of conduct](CODE_OF_CONDUCT.md)

## 开发环境与构建 / Build

需要 Git、Python 3、JDK 17 或 21、Android SDK platform 36 与 build-tools 36.0.0。按[安装指南](docs/INSTALL.zh-CN.md)设置 `ANDROID_HOME`，或在未跟踪的 `local.properties` 中配置 `sdk.dir`。

```sh
./gradlew :app:assembleDebug :app:lintDebug --console=plain
python3 scripts/validate_profiles.py
python3 scripts/check_contribution.py --base origin/main
```

这些命令不连接电视。全新克隆只需要公开文件和标准开发环境；Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。归档及签名规则见[版本规则](docs/VERSIONING.md)。

## 公开范围与本地测试 / Public files and local tests

公开仓库包含生产源码、资源、构建配置与脚本、必要使用说明、许可证及协作文件。计划、历史过程记录、测试源码及辅助模块、设备配置、备份、独立原型、宣传工程和产物仅保留本地。用户明确要求公开的额外文件，以及新增的生产构建输入，应同步添加 `.gitignore` 例外；不能通过本机已有的未上传文件满足构建或文档链接。

本地原有 `app/src/test`、`app/src/androidTest`、`scripts/tests` 与两个 fixture 目录保留原位置。Gradle 在对应 `build.gradle` 存在时加载本地 fixture 模块。维护者拥有这些本地测试资料时仍可运行：

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebugAndroidTest :home-fixture:assembleDebug :management-fixture:assembleDebug --console=plain
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
```

全新克隆不包含这些私有测试文件；上述本地测试命令不作为公开 CI 的前置条件。PR 应说明实际执行的测试及未执行项，需要共享额外测试文件时按维护者明确要求纳入。设备操作脚本及其配置、恢复记录继续保留在维护者本地，不由 CI 执行。

### 已有克隆的迁移 / Existing-clone migration

忽略规则不会阻止 Git 更新移除以前跟踪的文件。其他已有克隆切换到精简版本后，运行下面的必要迁移工具，从最近完整主线历史恢复缺失的本地测试、fixture、设备辅助工具与历史文档：

```sh
python3 scripts/restore_local_development.py
```

工具只恢复当前未跟踪且被忽略的文件，只补缺失路径，不覆盖现有内容、不删除文件，也不自动获取远程历史。更新前仍应保存本地修改。浅克隆缺少历史时，按工具提示先获取指定提交再重试。恢复后可继续运行上面的本地测试命令；测试仍不随当前公开源码分发。新克隆的核心构建不需要执行迁移。

Ignore rules do not preserve paths removed by a Git update. After updating an existing clone, run the migration command to restore missing local-only development files from the last complete public commit. Existing files and local edits are never overwritten. Shallow clones may need the explicit history fetch printed by the tool. Migration is optional for a fresh core-source build.

## 提交与评审 / Pull requests

维护者日常开发先在本地完成；“合并到 main”仅指本地主线合并。用户明确要求“推送到 GitHub”或授权远程同步后，才按授权范围推送、建 PR 并合并远程主线；仅授权推送或建 PR 时不扩大范围。以下 PR 流程适用于远程同步，不自动授权 APK 上传或 Release 发布，具体审核要求见 [AGENTS.md](AGENTS.md)。

1. 从最新 `main` 创建分支；外部贡献者通过 Fork 提交 PR。
2. 保持修改范围明确，按具体文件路径暂存，保护现有修改与本地资料；不批量删除文件，不提交密钥、账号或私密日志。
3. 修改生产源码、资源或构建配置时执行构建、Lint 和配置校验；仅文档修改执行链接与差异检查。
4. 在 PR 中说明问题、行为变化、范围及可复现的验证结果。构建、安装、哈希核对和真机功能验收分别报告。

远程 `main` 要求 PR、成功的 `Validate` 检查和解决全部讨论，统一 Squash merge；不绕过分支保护或强推。CI 检查提交文件中的文档链接、Python 语法、固件配置，并按修改范围构建和 Lint 主应用；不依赖本地测试目录，不连接电视或自动发布 APK。必需检查名称保持 `Validate`。

首次贡献者的 Actions 可能需要维护者批准运行。按 [CODEOWNERS](.github/CODEOWNERS) 评审，保留 [Issue 模板](.github/ISSUE_TEMPLATE/) 与 [PR 模板](.github/PULL_REQUEST_TEMPLATE.md)。较大功能先在 [Discussions](https://github.com/BeatsAD/quiet-tv/discussions) 讨论；任务认领与反馈通过 [Issues](https://github.com/BeatsAD/quiet-tv/issues)。安全问题按 [SECURITY.md](SECURITY.md) 私密报告。

## 依赖与交付 / Dependencies and delivery

依赖安全下限在 `gradle/security-constraints.gradle` 中维护，修改时核对实际解析与构建。保留上游、字体、壁纸及其他资源的许可证。源码整理不授权设备操作或 APK 发布；交付不同内容的新包时递增构建号，保留不可变归档。

## English

Use Python 3, JDK 17 or 21, and Android SDK platform 36 / build-tools 36.0.0. Set `ANDROID_HOME` or `sdk.dir` in local.properties. A fresh clone builds with `./gradlew :app:assembleDebug :app:lintDebug --console=plain`; run the profile validator and contribution checker above as appropriate.

Only core source, required resources, build inputs, essential guides, licenses and collaboration files are public. Private tests, fixtures, plans, device records and generated files remain local. Existing local tests keep their paths and commands; fixture modules load when their build files exist. CI does not require those private files. Explicit maintainer requests and new production build inputs update the tracking exceptions.

Maintainer development stays local by default; “merge into main” means local integration. Only explicit GitHub synchronization authorization starts the push/PR/remote-merge workflow; a request limited to pushing or opening a PR does not authorize merging or APK publication. For remote contributions, start from current main and submit a scoped PR, normally through a Fork. Preserve user data, stage explicit paths, and report actual checks separately from hardware acceptance. Protected remote main requires the `Validate` check, resolved discussions and Squash merge. Keep CODEOWNERS, templates, Discussions and confidential security reporting. CI never operates a TV or publishes an APK.


### 可选天气源配置 / Optional weather configuration

全新克隆不需要天气密钥即可构建，缺少配置时使用 UAPI。要以和风为主源，在仓库外保存仅当前用户可读的 UTF-8 JSON 文件，可选包含 `qweatherHost`（控制台提供的纯域名）及 `qweatherApiKey`（API KEY），两者须同时填写；也可独立配置 `openWeatherApiKey`（OpenWeather API KEY）。未配置来源会在设置中标记“未配置”，不进入自动切换链。构建时通过环境变量 `QUIETTV_WEATHER_CONFIG` 或 Gradle 属性 `-PquietWeatherConfig=/绝对路径/weather.local.json` 传入文件路径。禁止在命令行、源码、日志或提交中填写密钥值。

`bundleWeatherConfiguration` 仅将配置写入忽略的构建 assets；没有配置时写入空配置，防止复用先前构建的凭据。私有配置不会进入手机选城接口。APK 内置 Key 可以被提取，公开分发前应配置 Android 包名/签名和 API 权限限制，并按开发者账号的合计免费额度控制用量；本地限流不能代替服务端账号配额。

地区库和手机网页是已提交的生产输入，不需要在线下载。测试仅使用合成凭据；真实接口联调单独记录结果，不记录认证头。

Open-Meteo uses its public HTTPS endpoint without credentials and is disabled until explicitly enabled for non-commercial use. Its current-condition values come from weather models. Each provider keeps separate 429 cooldowns. Manual provider selection never changes the weather provider on failure. Online place search uses UAPI independently of weather source selection; no QWeather GeoAPI response is stored as a place index. Mainland selection, supplemental aliases, online search, source selection and failover share the same code across standard, legacy19 and Coocaa profiles. TV and operator-network acceptance remains separate from JVM tests and compilation.
