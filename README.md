<p align="center"><img src="docs/assets/cover.png" alt="Quiet TV 项目封面" width="100%"></p>

<h1 align="center">Quiet TV</h1>

<p align="center"><strong>一个简洁、可自定义的 Android 电视桌面。</strong></p>

<p align="center">
  <a href="README.md"><img alt="简体中文" src="https://img.shields.io/badge/%E7%AE%80%E4%BD%93%E4%B8%AD%E6%96%87-1f6feb?style=flat-square"></a>
  <a href="README.en.md"><img alt="English" src="https://img.shields.io/badge/English-555555?style=flat-square"></a>
</p>

<p align="center">
  常用应用、信号源、壁纸和应用管理，用遥控器轻松操作。<br>
  Android 原生界面，无广告、无推荐信息流，无需账号、root 或刷机。
</p>

<p align="center">
  <a href="https://beatsad.github.io/quiet-tv/"><img alt="下载 APK" src="https://img.shields.io/badge/%E4%B8%8B%E8%BD%BD%20APK-34a853?style=for-the-badge&amp;logo=android&amp;logoColor=white"></a>
  <a href="#安装方式"><img alt="安装指南" src="https://img.shields.io/badge/%E5%AE%89%E8%A3%85%E6%8C%87%E5%8D%97-1f6feb?style=for-the-badge"></a>
  <a href="docs/USAGE.zh-CN.md"><img alt="使用指南" src="https://img.shields.io/badge/%E4%BD%BF%E7%94%A8%E6%8C%87%E5%8D%97-d97757?style=for-the-badge"></a>
</p>

<p align="center">
  <a href="https://github.com/BeatsAD/quiet-tv/releases"><img alt="最新版本" src="https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fbeatsad.github.io%2Fquiet-tv%2Flatest.json&amp;query=%24.latestVersion&amp;prefix=v&amp;label=%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC&amp;style=flat-square&amp;color=2997ff"></a>
  <a href="LICENSE"><img alt="许可证 MIT" src="https://img.shields.io/badge/%E8%AE%B8%E5%8F%AF%E8%AF%81-MIT-555555?style=flat-square"></a>
  <a href="docs/COMPATIBILITY.md"><img alt="标准版 Android 5.0+" src="https://img.shields.io/badge/%E6%A0%87%E5%87%86%E7%89%88-Android%205.0%2B-3ddc84?style=flat-square&amp;logo=android&amp;logoColor=white"></a>
  <a href="docs/COMPATIBILITY.md"><img alt="兼容测试版 Android 4.0–4.4" src="https://img.shields.io/badge/%E5%85%BC%E5%AE%B9%E6%B5%8B%E8%AF%95%E7%89%88-Android%204.0--4.4-f5a623?style=flat-square&amp;logo=android&amp;logoColor=white"></a>
</p>

## 界面

<table>
  <tr>
    <td width="50%" valign="top">
      <p><strong>常用应用、时间与天气，首页一目了然。</strong></p>
      <img src="docs/assets/home-1.3.0.png" alt="Quiet TV 1.3.0 首页应用与时间天气" width="100%">
    </td>
    <td width="50%" valign="top">
      <p><strong>经典布局与艺术画廊，设置实时预览。</strong></p>
      <img src="docs/assets/desktop-settings-1.3.0.png" alt="Quiet TV 1.3.0 桌面个性化与实时预览" width="100%">
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <p><strong>每日壁纸自动更新，也可上传图片和视频。</strong></p>
      <img src="docs/assets/wallpaper-1.3.0.png" alt="Quiet TV 1.3.0 每日壁纸与上传入口" width="100%">
    </td>
    <td width="50%" valign="top">
      <p><strong>自动定位或手动选城，天气来源按需选择。</strong></p>
      <img src="docs/assets/weather-1.3.0.png" alt="Quiet TV 1.3.0 城市与天气来源设置" width="100%">
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <p><strong>应用分类筛选与详情，集中查看管理。</strong></p>
      <img src="docs/assets/app-management-1.3.0.png" alt="Quiet TV 1.3.0 应用分类与详情" width="100%">
    </td>
    <td width="50%" valign="top">
      <p><strong>查看当前版本，一键检查应用更新。</strong></p>
      <img src="docs/assets/update-1.3.0.png" alt="Quiet TV 1.3.0 版本信息与检查更新" width="100%">
    </td>
  </tr>
</table>

## 功能

以下为 1.3.0 测试版的核心功能。下载以[实时发布索引](https://beatsad.github.io/quiet-tv/latest.json)为准，按电视系统与实际安装包选择适配版本。

| 功能 | 可以做什么 |
| --- | --- |
| 常用应用与排序 | 首页最多 5 个常用应用；全部应用五列显示，移动排序自动保存。 |
| 桌面个性化 | 经典／艺术画廊布局，自定义问候语和头像，各项信息与快捷按钮独立显隐。 |
| 天气 | 自动定位、手动选城和手机扫码选城，首页显示当地天气。 |
| 壁纸 | 默认颜色主题、必应每日壁纸、自定义图片和视频；同局域网扫码上传，长按确定删除。 |
| 应用管理 | 分类筛选、应用详情、自定义封面；本机 TCP ADB 连接后，按规则和固件能力提供禁用、重新启用、卸载与恢复安装。 |
| 儿童模式 | 隐藏或锁定应用、设置单次或每日使用时长，家长验证与到时休息提醒。 |
| 应用自启 | 开机或待机唤醒进入 Quiet TV 后启动指定应用，按 HOME 返回不重复启动。 |
| 信号源与桌面配置 | 按本机能力打开输入和系统设置；电脑 AI 配置默认桌面或兼容接管，保留原桌面恢复路径。 |
| 应用内更新 | 检查版本、多源下载、完整性与签名校验，由用户确认安装。 |
| 遥控器与本地使用 | 方向键、确定、返回操作；无需云端账号，个人设置保存在电视。 |

从“全部应用 → 应用管理”进入“应用列表／首页应用／应用排序／儿童模式／版本更新／诊断日志”。日常桌面、排序、壁纸、天气和封面设置无需 ADB。首次连接配置、授权、机型适配与开机广告处理由电脑 AI 按需执行；电视端保留已配置的本机 TCP ADB 日常管理，不提供无线配对界面，电脑已连接不代表应用已获授权。详见[应用管理](docs/APP_MANAGEMENT.md)。

本批 1.3.0 累加修复了覆盖升级后的接管恢复、部分动态壁纸兼容及切换问题，增加启动背景缓存并恢复静态壁纸高清加载，改善卡片边缘、首页焦点、设置间距与管理按钮排列。完整内容见 [1.3.0 Release](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.3.0)。

应用内更新清单已上线，首页前台每 24 小时自动检查，1.3.0 可在“应用管理 → 版本更新 → 检查更新”主动查询（旧版入口为“关于”）。旧版没有更新入口时先手动安装；同版本不会因构建号不同重复提示升级。详见[应用内更新](docs/UPDATES.md)。

## 安装方式

### 1. 使用 Skill

安装仓库中的[电视桌面切换助手](skills/quiet-tv-switch/SKILL.md)，在支持 Skill 和终端操作的 AI 工具中输入：

```text
请用 $quiet-tv-switch 帮我安装 Quiet TV 并切换电视桌面，保留原桌面和恢复方法。
```

### 2. 使用安装指令

打开[安装指引](https://beatsad.github.io/quiet-tv/)，复制唯一 AI 指令，交给电脑上的 AI 工具执行。安装、升级、修复共用[统一入口](docs/AGENT.md)，恢复时补充“恢复原桌面”；中断后提供原任务目录继续。页面会自动读取[最新版索引](https://beatsad.github.io/quiet-tv/latest.json)，无需填写版本号或随新版本修改指令。

### 3. 手动安装 APK

从 [GitHub Releases](https://github.com/BeatsAD/quiet-tv/releases) 下载适合电视系统和架构的 APK，按[安装指南](docs/INSTALL.zh-CN.md)完成安装和桌面设置。

1.3.0 累加测试版提供安卓5及以上通用包（130，最低 API21）和安卓4.0至4.4统一兼容包（131，最低 API14），均无原生库，无需区分 ARM32／ARM64；按电视系统选包。兼容包不提供标准桌面守护、HOME 代理或标准 TV Input 列表，信号源使用系统设置或实体遥控器。

已安装 1.3.0 的用户需手动下载匹配的新包覆盖安装；同软件版本不会因构建号增加提示更新。先核对包名、签名和递增构建号，不卸载、不清数据。

两包构建、签名及校验通过，标准与兼容档各 12 项定向测试通过，Lint 零错误但仍有警告；API26 模拟器已逐包检查覆盖安装、HOME 与管理标签，以及所选设置和壁纸索引保留。真实 API14–19 运行与覆盖升级、各厂商电视及长期播放仍待验收，较新模拟器运行兼容包不代表真实4.x系统通过。

安装时保留原桌面；不同厂商的桌面切换能力可能有所不同。

## 已知兼容性问题与样机验证

**以下问题仍待解决或验证，需要其他品牌、型号和固件的真实样机协助；不能视为已修复。** 安装成功、手动启动正常或 HOME 查询结果正确，不代表开机、遥控器 HOME 键和重启后的行为均正常。当前反馈见 [Issue #35](https://github.com/BeatsAD/quiet-tv/issues/35)。

| 问题 | 当前表现与待验证内容 |
| --- | --- |
| 厂商主页接管 | 部分固件可能在开机时恢复原厂主页，或由 Home 键直接启动原厂 Activity，绕过标准 HOME 设置；冷启动、重启和待机唤醒需分别验证。 |
| 默认桌面设置失败 | 已有反馈出现 `cannot be home on user 0`；需核对实际安装包、当前用户、HOME 候选和厂商限制，原因及适配尚待确认。 |
| 默认桌面与实际接管不一致 | 默认 HOME、兼容守护与代理状态需分别核对；状态提示不能代替实体 Home 键、重启和待机后的实际验证。 |
| ADB 被禁用或入口受限 | USB 调试灰色、无线调试缺失或无法取得配对码时，电脑维护和依赖 ADB 的适配可能不可用；电脑工具不能绕过固件封锁或配对认证，目前没有通用解锁方案。 |

1.3.0 将桌面配置交给电脑 AI，电视端通过“应用管理 → 更多 → 帮助与恢复”暂停接管或恢复原桌面；历史 1.2.4 标准版仍有“桌面配置”入口。兼容守护和可选 HOME 代理属于实验路径，依赖固件、授权与配套组件。安卓4.0至4.4不提供标准桌面守护。实体 HOME、重启、冷启动、待机和 HDMI／播放仍需在目标电视分别验收，详见[配置与暂停](docs/SETUP.md#桌面守护实验)。 电脑端检测、修复、信号源适配及修复前后自检见[适配指南](docs/AGENT_ADAPT.zh-CN.md)。

原有[小米主页中转助手](xiaomi-home-helper/README.md)保留为独立实验方案；启用内置守护前先停用它的无障碍服务，不自动迁移或卸载。

针对已确认使用厂商主页属性的酷开 Android 8 固件，仓库另提供可选独立的[主页中转助手](coocaa-home-shim/README.md)，复用上游实现并指向 Quiet TV。**该移植尚未经过真实电视验收，不随通用包启用，也不能解锁 ADB。** 日志监听暂未实现。上游其他设备的成功记录不能代替 Quiet TV 验收；保留原桌面及本机恢复记录，不套用其他电视的系统包禁用清单。

欢迎通过 [Issue #35](https://github.com/BeatsAD/quiet-tv/issues/35) 或[新机型适配反馈](https://github.com/BeatsAD/quiet-tv/issues/new?template=device_adaptation.yml)补充样机结果：电视型号、Android／固件版本、Quiet TV 版本与安装包来源、复现步骤，以及手动启动、真实遥控器 HOME、冷启动／重启、待机唤醒、信号源和原桌面恢复的实际结果。未测试项请标为“未验证”；日志只提供相关脱敏片段，不公开 IP、序列号、密钥或完整设备转储。

## 文档与参与

| 我想…… | 查看 |
| --- | --- |
| 浏览全部文档与目录职责 | [文档索引](docs/README.md) |
| 安装桌面、更新 APK 或切回原桌面 | [安装指南](docs/INSTALL.zh-CN.md) |
| 调整常用应用、壁纸和遥控器操作 | [使用指南](docs/USAGE.zh-CN.md) |
| 按需配置桌面或恢复原桌面 | [桌面配置与恢复](docs/SETUP.md) |
| 了解机型适配与已验证功能 | [兼容性](docs/COMPATIBILITY.md) · [验收记录](docs/COMPATIBILITY.md) |
| 开发、构建或贡献代码 | [贡献指南](CONTRIBUTING.md) · [版本与安装包规则](docs/VERSIONING.md) |
| 了解安全与仓库设置 | [安全政策](SECURITY.md) · [贡献指南](CONTRIBUTING.md) |

反馈：[Bug](https://github.com/BeatsAD/quiet-tv/issues/new?template=bug_report.yml) · [功能建议](https://github.com/BeatsAD/quiet-tv/issues/new?template=feature_request.yml) · [新机型适配](https://github.com/BeatsAD/quiet-tv/issues/new?template=device_adaptation.yml)

仓库、文档和测试包下载已公开；提交反馈需要登录 GitHub。

## 参与开发与维护

欢迎贡献 Android 原生功能、遥控器交互、文档翻译、新机型验证和测试。可以从[新人任务](https://github.com/BeatsAD/quiet-tv/issues?q=is%3Aissue%20is%3Aopen%20label%3A%22good%20first%20issue%22)或[需要帮助的任务](https://github.com/BeatsAD/quiet-tv/issues?q=is%3Aissue%20is%3Aopen%20label%3A%22help%20wanted%22)开始，先留言认领，再通过 Fork 提交 PR。

[贡献指南](CONTRIBUTING.md)提供开发环境、检查命令与评审流程；[Discussions](https://github.com/BeatsAD/quiet-tv/discussions)用于问答、方向讨论和长期维护意愿交流。参与协作请遵守[行为准则](CODE_OF_CONDUCT.md)。

本地构建需要 Git、Python 3、JDK 17、Android SDK platform 36 和 build-tools 36.0.0。先设置 `ANDROID_HOME`，或在 `local.properties` 配置自己的 `sdk.dir`，然后运行：

```sh
git clone https://github.com/BeatsAD/quiet-tv.git
cd quiet-tv
./gradlew :app:assembleDebug --console=plain
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。构建与离线检查不需要连接电视或准备 `device.json`；安装到自己的电视时，按[安装指南](docs/INSTALL.zh-CN.md)使用 ADB，保留已有配置和备份。正式交付 APK 的归档、签名与版本要求见[版本规则](docs/VERSIONING.md)。

原生源码位于 `app/`，公开构建工具位于 `scripts/`；`prototypes/about/` 是 APK 的实际打包输入。设备数据、独立网页演示和宣传视频不随当前源码分发，完整目录说明见[文档索引](docs/README.md)。

## 致谢与许可

Quiet TV 基于 [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) 开发，感谢 **Gathin** 的原始工作。本项目独立维护，与上游及电视、应用品牌方无官方关联。

代码采用 [MIT 许可证](LICENSE)，保留原作者版权与完整许可。字体、壁纸和第三方应用素材分别遵循各自授权，详见[第三方项目与资源说明](THIRD_PARTY_NOTICES.md)。

## 保持联系

![WenBin AI LAB 联系方式：X、GitHub、网站、邮箱及微信、公众号、小红书、抖音二维码](docs/assets/contact-16x9.png)
