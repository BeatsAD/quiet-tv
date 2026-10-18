# 构建依赖安全版本 / Build dependency security floors

## 范围 / Scope

`gradle/security-constraints.gradle` 通过 `settings.gradle` 在各项目配置前注册最低版本约束，同时覆盖插件 buildscript classpath 和 Android 工具的独立配置。2026-10-18 的基线中，旧版 Netty 还来自 Unified Test Platform（UTP），旧版 Protobuf 来自多个 UTP 配置，仅修补根插件 classpath 会遗漏这些路径。

The settings-applied script registers minimum-version constraints before project evaluation, covering both buildscript classpaths and independent Android tool configurations. The October 18, 2026 baseline also contained older Netty and Protobuf in Unified Test Platform (UTP) configurations; repairing only the root plugin classpath would miss them.

约束只为已经存在的模块提供版本下限，不增加应用依赖，也不使用强制固定版本阻止更高版本。它们由所有项目配置继承；如果未来应用直接使用相同模块，也会继承相应最低版本。Netty 仅列出当前使用的 4.1 系列模块，不改动 tcnative 等采用独立版本线的模块。

Constraints provide floors only for modules already present, without adding application dependencies or forcing newer versions down. All project configurations inherit them; future application use of a listed module also inherits its floor. Only the currently used Netty 4.1 modules are listed; independently versioned modules such as tcnative are not rewritten.

## 版本 / Versions

| 依赖 / Dependency | 基线 / Baseline | 最低版本 / Floor |
| --- | --- | --- |
| Netty 当前 4.1 模块 / Current 4.1 modules | 4.1.93.Final / 4.1.110.Final | 4.1.137.Final |
| Bouncy Castle bcprov / bcpkix / bcutil jdk18on | 1.79 | 1.85 |
| Protobuf java / java-util / kotlin | 包含 / includes 3.24.4 | 3.25.5 |
| Commons Compress | 1.21 | 1.28.0 |
| jose4j | 0.9.5 | 0.9.6 |
| JDOM 2 | 2.0.6 | 2.0.6.1 |

版本依据为仓库 Dependabot 告警的受影响范围和修补版本，并从 Maven Central 核对实际发布版本。Commons Compress 使用 1.28.0，避免选择较早修补版所依赖的旧版 Commons IO / Lang。告警计数会随上游公告更新，不能将本表视作永久无漏洞保证。

Floors are based on the repository's Dependabot affected ranges and patched versions, verified against Maven Central. Commons Compress uses 1.28.0 rather than an older patch line with older Commons IO / Lang dependencies. Advisory coverage changes over time; this table is not a permanent vulnerability-free guarantee.

相关公告 / Relevant advisories:

- [Netty TLS SNI / CVE-2026-75595](https://github.com/advisories/GHSA-c4c3-7fpv-j4q5)
- [Bouncy Castle Name Constraints / CVE-2026-8763](https://github.com/advisories/GHSA-9pwp-9qqc-pr26)
- [Bouncy Castle GOST CTR / CVE-2025-14813](https://github.com/advisories/GHSA-574f-3g2m-x479)
- [Protobuf / CVE-2024-7254](https://github.com/advisories/GHSA-735f-pc8j-v9w8)
- [Commons Compress / CVE-2024-25710](https://github.com/advisories/GHSA-4g9r-vxhx-9pgx)
- [jose4j / CVE-2024-29371](https://github.com/advisories/GHSA-3677-xxcr-wjqv)
- [JDOM / CVE-2021-33813](https://github.com/advisories/GHSA-2363-cqg2-863c)

## 验证和维护 / Verification and maintenance

现有 AGP 8.12.2、Gradle 8.13、JDK 17/21、compileSdk 36、minSdk/targetSdk 31 和 RecyclerView 1.4.0 配置保留。基线中的已知漏洞版本属于构建和工具配置，当前应用运行依赖不包含这些库。安全版本变更仍需核对实际构建和完整解析图，不能只检查源码或一个配置。

AGP 8.12.2, Gradle 8.13, JDK 17/21, compileSdk 36, minSdk/targetSdk 31, and RecyclerView 1.4.0 remain unchanged. Baseline vulnerable modules belong to build/tool configurations and are absent from current application runtime dependencies. Validate the actual build and complete resolved graph rather than source inspection or a single configuration alone.

```sh
./gradlew :app:assembleDebug --console=plain
./gradlew buildEnvironment --console=plain
./gradlew :app:dependencies --configuration debugRuntimeClasspath --console=plain
./gradlew :app:dependencyInsight --configuration _internal-unified-test-platform-core --dependency netty-handler --console=plain
```

`gradle/**` 与 `settings.gradle` 的主分支变更会触发依赖图提交工作流。合入后须确认工作流成功，以及 GitHub 依赖图和告警实际更新。不要关闭告警、排除工具配置或通过手动 dismiss 替代修复。

Changes under `gradle/**` or to `settings.gradle` on main trigger graph submission. After merge, confirm both workflow success and the updated graph and alert state. Do not replace remediation with disabled alerts, omitted tool configurations, or manual dismissal.

升级 AGP 或其他构建工具时复核这些约束；当上游已全面提供相同或更高的安全版本，可删除相应约束，并再次核对插件、lint、UTP 与应用运行依赖。构建验证不等于真机安装或验收。

Review the floors when upgrading AGP or other tools. Remove a floor only after upstream supplies safe versions across all relevant paths, then recheck plugin, lint, UTP, and application runtime dependencies. Build verification does not establish installation or hardware acceptance.
