# 贡献指南 / Contributing

[简体中文首页](README.md) · [English home](README.en.md) · [行为准则 / Code of conduct](CODE_OF_CONDUCT.md)

## 开始之前

仓库已公开，欢迎通过 Fork 和 PR 参与，无需先申请仓库写权限。请先查看 [Issues](https://github.com/BeatsAD/quiet-tv/issues)、[新人任务](https://github.com/BeatsAD/quiet-tv/issues?q=is%3Aissue%20is%3Aopen%20label%3A%22good%20first%20issue%22)和[兼容性说明](docs/COMPATIBILITY.md)。

Bug、功能建议和新机型适配使用对应 Issue 模板；开发任务可说明目标、涉及文件和验收方式。准备认领任务时先在对应 Issue 留言，避免重复开发。较大的功能先在 [Discussions](https://github.com/BeatsAD/quiet-tv/discussions) 讨论范围。安全问题使用 [SECURITY.md](SECURITY.md) 中的私密报告入口。

## 从 Fork 到第一个 PR

1. 在 GitHub 点击 Fork，将自己的 Fork 克隆到本地。下面用 `YOUR_GITHUB_NAME` 代表你的账号。
2. 添加上游远程，基于最新 `upstream/main` 创建功能分支。
3. 完成最小必要修改，执行与改动匹配的检查，按明确路径提交并推送到自己的 Fork。
4. 向 `BeatsAD/quiet-tv:main` 提交 PR，关联任务、说明改动与实际验证结果，并响应评审。

```sh
git clone https://github.com/YOUR_GITHUB_NAME/quiet-tv.git
cd quiet-tv
git remote add upstream https://github.com/BeatsAD/quiet-tv.git
git fetch upstream
git switch -c feat/short-name upstream/main
# 修改并检查后，替换为你实际修改的明确路径：
git add path/to/changed-file
git commit -m "说明具体修改及目的"
git push -u origin feat/short-name
```

[文档索引](docs/README.md)说明各目录职责；设备配置与恢复记录只保留本地，格式见[脱敏样例](docs/examples/README.md)。

## 开发环境与本地检查

Android 开发使用 JDK 17、Android SDK platform 36 和 build-tools 36.0.0。按[安装指南](docs/INSTALL.zh-CN.md)设置 `local.properties` 中的 `sdk.dir`，或配置 `ANDROID_HOME`。构建和检查无需连接电视。GitHub CI 使用 Ubuntu；macOS 已有开发记录，Windows 本地流程仍待验证。

```sh
# 检查相对 upstream/main 的差异、修改文档的本地链接和 Python 语法：
python3 scripts/check_contribution.py --base upstream/main

# 修改 Android 代码、资源或 Gradle 配置时：
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :management-fixture:assembleDebug :home-fixture:assembleDebug :app:testDebugUnitTest --console=plain
```

JVM 测试覆盖设置策略和配对失败处理；Python 离线测试命令为 `python3 -m unittest discover -s scripts/tests -p 'test_*.py'`。CI 编译 instrumentation 测试 APK 与两个辅助模块，不执行设备上的测试；构建成功不能替代真机验收。

仅修改文档时，无需完整构建 Android。Python 语法检查不会执行脚本或联系电视；若修改脚本行为，还需验证实际受影响路径，使用不连接真实设备的测试或样例。

修改应用管理网页原型时，在 `prototypes/app-management` 下运行：

```sh
npm ci
npm run build
npm run test:sites
```

修改视频项目源码时，在 `tools/video-preview` 下运行 `npm ci` 和 `npm run lint`。CI 不渲染视频，也不需要私有录制素材；本地播放要求见[视频项目说明](tools/video-preview/README.md)。

## 修改与验证

- 保持原生、轻量、遥控器可操作，不引入广告或无关后台服务。
- 不覆盖他人的未提交修改，不批量或递归删除文件，不提交设备密钥、账号资料、完整私密日志或其他用户的备份。
- 行为变化分别记录源码／构建、机型与固件、安装、遥控器操作、截图、真实播放、恢复及长期运行结果；未执行项明确标记。
- 新机型适配使用目标设备自己的记录，不套用其他机型的禁用名单。系统包变更前先备份并记录原状态与恢复方式。
- 交付新 APK 前遵循[版本与安装包规则](docs/VERSIONING.md)，递增版本／构建号并归档，不覆盖历史包。PR 检查生成的 APK 仅用于构建验证，不是正式发布。
- 保留上游代码、字体和第三方素材的许可；按变更同步文档与兼容性记录。

## 评审、合入与维护者

`main` 已受保护：变更须通过 PR，`Validate` 检查成功并解决全部讨论后才能合入；禁止强推和删除，管理员同样受约束。统一使用 Squash merge，合入后自动删除本仓库中的功能分支；外部 Fork 分支由贡献者自行管理。

首次贡献者的 Actions 工作流需要维护者批准运行。这是运行检查的批准，不代表代码已被接受。PR 工作流仅有只读仓库权限，不读取发布密钥，也不操作电视。

[CODEOWNERS](.github/CODEOWNERS) 目前由 @BeatsAD 负责。长期参与者可在 Discussions 表达维护意愿，明确愿意负责的模块和时间。经双方确认后再邀请为协作者并更新负责人；本仓库当前属于个人账号，协作者拥有写权限，需谨慎授予。

目前必需批准数为 0，由维护者评审合入。有第二位稳定维护者后，再启用至少 1 人批准和按模块负责人评审，避免个人维护期间出现无人能批准自己 PR 的情况。发布由维护者手动执行，不因 PR 合入自动发布。

## English

This repository is public. Fork it, clone your fork, add `https://github.com/BeatsAD/quiet-tv.git` as `upstream`, and create a descriptive branch from `upstream/main`. Find an issue, comment before starting, and send a PR with its issue link, changes, and reproducible validation. Discuss larger work in [Discussions](https://github.com/BeatsAD/quiet-tv/discussions). Follow the [code of conduct](CODE_OF_CONDUCT.md) and report vulnerabilities privately through [SECURITY.md](SECURITY.md).

Use JDK 17 and Android SDK platform 36 / build-tools 36.0.0. Configure `sdk.dir` in `local.properties` or set `ANDROID_HOME`. Run `python3 scripts/check_contribution.py --base upstream/main` for changed-document links, whitespace, and Python syntax. For Android changes, also run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :management-fixture:assembleDebug :home-fixture:assembleDebug :app:testDebugUnitTest --console=plain`. JVM tests cover setup policy and pairing failures. Run offline Python tests with `python3 -m unittest discover -s scripts/tests -p 'test_*.py'`. CI compiles instrumentation APKs and both fixture modules without running device tests. Documentation-only changes do not require an Android build.

For the app-management prototype, run `npm ci`, `npm run build`, and `npm run test:sites` in `prototypes/app-management`. For video source changes, run `npm ci` and `npm run lint` in `tools/video-preview`; CI does not render video or require private media.

Keep changes scoped, stage explicit paths, and preserve unrelated work and device data. Never use bulk or recursive file deletion or upload keys, accounts, private logs, or backups. Distinguish build, installation, screenshots, playback, recovery, and long-run evidence; mark unperformed checks. Follow [version and APK rules](docs/VERSIONING.md) before delivering an APK, preserve existing archives, and retain upstream and third-party licenses.

Protected `main` requires a PR, a successful `Validate` check, and resolved conversations, including for administrators. Squash merge is the only merge method; merged repository branches are deleted automatically. First-time contributors need maintainer approval to run Actions. PR checks have read-only repository permissions, no publishing secrets, and no TV access.

@BeatsAD currently owns all modules in [CODEOWNERS](.github/CODEOWNERS). Prospective maintainers can discuss responsibilities before being invited as collaborators. Required approving reviews remain zero until a second regular maintainer is available; then require one approval and module-owner review. Releases remain manual.
