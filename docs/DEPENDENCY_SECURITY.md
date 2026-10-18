# 依赖安全版本 / Dependency security floors

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
| Bouncy Castle bcprov / bcpkix / bcutil jdk15to18（APK） | 1.81 | 1.86 |
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

现有 AGP 8.12.2、Gradle 8.13、JDK 17/21、compileSdk 36、minSdk/targetSdk 31 和 RecyclerView 1.4.0 配置保留。早期基线中的 jdk18on 等依赖属于构建工具；加入本机 ADB 后，应用实际运行依赖另包含 jdk15to18 三件套。两个系列坐标不同，原来的 jdk18on 约束不能修补 APK 内的 jdk15to18。安全版本变更仍需核对实际构建和解析图，不能只检查源码或一个配置。

AGP 8.12.2, Gradle 8.13, JDK 17/21, compileSdk 36, minSdk/targetSdk 31, and RecyclerView 1.4.0 remain unchanged. The earlier jdk18on baseline belongs to build tools; local ADB introduced the separate jdk15to18 trio into the APK runtime. Constraints for one artifact family do not repair the other. Validate the actual build and resolved graph rather than source inspection or a single configuration alone.

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

## 2026-10-18：应用与网页原型修复

本次范围是检查时仍开放的 15 条 Dependabot 告警：5 条 APK Bouncy Castle 告警和 10 条网页原型工具链告警。修改仅在本地；GitHub 告警须在推送后由新的依赖图与锁文件分析结果确认，不能将本地修复写成远程告警已关闭。已发布的 `1.0.1/b3` 不会随源码修复更新，其历史说明保留。

### APK 运行依赖

`app/build.gradle` 直接引入 bcpkix，libadb 3.1.1 另引入 bcprov。现在三件套统一解析为 `jdk15to18:1.86`，并为三个坐标补充安全版本下限，保留原有 libadb 和无线配对原生依赖排除。

这批告警的最低共同安全版本是 1.85，但 Maven Central 的 jdk15to18 1.85 在 bcprov 和 bcutil 中重复打包 `IANAObjectIdentifiers`，本地 Android 构建实际失败，故使用无该重复类的 1.86；不改写第三方 JAR 或跳过重复类检查。参见[上游问题 #2356](https://github.com/bcgit/bc-java/issues/2356)与[官方下载页](https://www.bouncycastle.org/download/bouncy-castle-java/)。

实际调用为 `AdbController.loadKeys()` 中的 RSA-2048、自签证书和 SHA256withRSA，以及 libadb 的 RSA 公钥编码。静态检查未发现应用调用告警涉及的 GOST、LDAP、CompositeVerifier 或 BC PKIX 路径校验；这不等于对整个库作出不可利用保证，也不替代依赖升级。GOST 公告部分坐标的修补字段为空，但[公告正文](https://github.com/advisories/GHSA-574f-3g2m-x479)明确记录 1.84 修复，不能解释为没有安全版本。

### 网页原型锁文件

没有新增直接依赖或 overrides；在既有兼容范围内更新传递依赖及其所需的数据包。

| 依赖 | 原版本 | 修复后锁定版本 |
| --- | --- | --- |
| vite | 6.4.2 | 6.4.3 |
| postcss | 8.5.16 | 8.5.30 |
| nanoid | 3.3.15 | 3.3.20 |
| browserslist | 4.28.5 | 4.29.3 |
| baseline-browser-mapping | 2.10.42 | 2.11.29 |
| source-map-js | 1.2.1 | 1.2.2 |

网页原型和视频剪辑工程不打入 APK；`.github/dependabot.yml` 已增加这两个 npm 子项目的每月版本检查，尚待推送生效。

### 本地验证与交付边界

- 临时源码副本执行 `:app:dependencyInsight --configuration debugRuntimeClasspath --dependency org.bouncycastle`，确认三个 APK 运行依赖均为 1.86；`:app:assembleDebug :app:assembleDebugAndroidTest` 通过。测试 APK 仅构建，未执行设备仪器测试。
- 临时主机 JVM 探针在 1.81 上复现 GOST CTR 重复密钥流，1.86 对两组不同密钥／IV 均不再重复；加解密往返、自签 RSA 证书生成／验签和内存 PKCS12 保存／恢复通过。这是库与主机兼容性证据，未证明应用存在可利用路径，也不等于 Android 12 密钥与连接验收。
- 网页原型 `npm audit --audit` 为 0，`npm run build` 和现有 `npm run test:sites` 的 4 项测试通过；没有新增浏览器视觉验收。视频剪辑工程的锁文件审计也为 0，其依赖未改动。
- 未升级 AGP／Gradle 主版本，未覆盖本地历史 APK 归档，未归档或发布新包，未连接或安装到电视。源码当前版本配置仍为 1.0.1/b3；下次交付不同内容必须先递增构建号。Android 12 首次密钥生成、已有密钥恢复及实际 ADB 连接留待单独实机验收。
