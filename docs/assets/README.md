# 展示素材来源 / Showcase provenance

| 文件 / File | 来源 / Source | 说明 / Notes |
| --- | --- | --- |
| [quiet-tv-icon.svg](quiet-tv-icon.svg) | 按用户选定的方案 1「Q 字电视」重建的矢量图标 / Vector reconstruction of selected concept 1 | 深蓝底、雾白 Q 形屏幕、浅蓝尾部；与 Android `launcher_icon.xml` 和关于页内联 SVG 保持相同几何与配色 / Shared geometry and palette across Android and About |
| [home.png](home.png) | 本地 `evidence/public-showcase-20261019/home.png` | Sony KD-65X9000H，2026-10-19，1920×1080，原始 ADB 截图，逐字节复制 / Original ADB capture, copied unchanged |
| [wallpaper-settings.png](wallpaper-settings.png) | 本地 `evidence/public-showcase-20261019/wallpaper-settings.png` | 同机型与日期，原始 ADB 截图，逐字节复制 / Same device and date, copied unchanged |
| [cover.png](cover.png) | 内置 imagegen 工具，参考原封面重新构图 / Built-in imagegen, recomposed from the original cover | 2172×724，3:1 横幅；保留标题、副标题与深蓝客厅电视场景；AI 品牌插画，不作为设备验证证据 / 2172×724, 3:1 banner retaining the title, subtitle and dark blue living-room TV scene; AI brand illustration, not hardware evidence |
| [contact-16x9.png](contact-16x9.png) | 作者提供的联系方式图，经内置 imagegen 横版重排 / Author-provided contact card, recomposed with built-in imagegen | 1920×1080；微信、公众号、小红书二维码解析结果与原图一致，抖音码待平台内扫码确认 / Matching decoded links for WeChat, Official Account, and Xiaohongshu; Douyin code requires an in-app scan |

历史 `home.png` 与 `wallpaper-settings.png` 来自电视当时安装的 1.1.0 / b15 开发版本，不代表后续公开测试包。截图是开发过程的历史状态，不证明其他机型兼容。没有对实际界面、文字、应用卡片做合成或重绘。第三方应用图标及风景壁纸属于各自权利人，见[资源说明](../../THIRD_PARTY_NOTICES.md)。完整本地备份和证据目录没有上传。

The historical home.png and wallpaper-settings.png captures were running development build 1.1.0 / b15; these captures do not represent hardware acceptance of later public test APKs. The captures show historical development states and do not establish compatibility with another model. The captured UI, text, and app cards were not composited or redrawn. Third-party application art and wallpaper retain their own rights; see [notices](../../THIRD_PARTY_NOTICES.md). Full local backups and evidence are not uploaded.

## 1.1.2 索尼真机截图 / Sony captures

2026-10-22 直接通过电视 ADB `screencap -p` 获取，Sony / Android 12（API31），应用实际渲染尺寸 1920×1080。已读取实际安装包版本 `1.1.2 / 34`，其 SHA-256 为 `6494164778c305675c54a64101cd13619d01bc55a804398d0f374f83ca28cc37`，与已发布标准包一致；下载见 [v1.1.2](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.1.2)。

以下均为原始 PNG 逐字节复制，没有裁切、合成、重绘或修改 UI。保留电视现有应用、壁纸和显示配置；只打开页面和管理模式，未删除图片、切换壁纸、修改开关或保存排序，结束后返回原首页。自定义壁纸来自设备现有用户图片，第三方图案与应用素材权利归各自权利人。上传窗口含本地地址、连接码与二维码，其原图仅保留本地，打开的会话已关闭。

| 文件 / File | 原始 PNG SHA-256 |
| --- | --- |
| [home-1.1.2.png](home-1.1.2.png) | `5ed3c608847cfd1aec3bec87a68f4f1e75786bae02a42aac1937a2de0ac15ec1` |
| [all-apps-1.1.2.png](all-apps-1.1.2.png) | `7549279f2f6aba4070b0ba65ac94cd4d602c3fd4c7ecc1c73a5fdcc1b3172c27` |
| [wallpaper-1.1.2.png](wallpaper-1.1.2.png) | `d5cb938f8287320016b456b9355ed9ad84d71d7dc31db3bf9ca2eb06c4a87126` |
| [wallpaper-manage-1.1.2.png](wallpaper-manage-1.1.2.png) | `5a10e8a6a1d4ee54187159296d2bf67756b860804495025325ad7c1fab212bf8` |
| [desktop-settings-1.1.2.png](desktop-settings-1.1.2.png) | `b56e5669ae22b05290530cdb68efb31506fc7375ef772e70d9fb457cad87e886` |
| [sort-1.1.2.png](sort-1.1.2.png) | `8b3e4fd960a195e0cdc43518f8eafba7389ae247abb250e04174744815e177dd` |

Direct Sony / Android 12 ADB captures, 2026-10-22, at 1920×1080. Installed version 1.1.2 / 34 and APK hash match the published standard download. Every PNG is copied unchanged; no cropping, synthesis or UI redraw. Existing preferences are retained and the TV was returned to its home screen. The private upload-session image is not published. Captures demonstrate displayed pages, not full feature, upgrade, physical Home, reboot, standby or other-model acceptance. Existing custom wallpaper and third-party artwork retain their original rights.


## 1.2.4 索尼真机截图 / Sony captures

2026-10-25 通过指定电视的 ADB `exec-out screencap -p` 直接获取，设备为 Sony BRAVIA VH1 / Android 12（API31），原始渲染尺寸1920×1080。实际安装为已发布 [1.2.4 测试版](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.2.4)的 ARM32 构建64；拉取已安装 APK，其 SHA-256 为 `960804b87b6626f744389777a5d2fcf3d52f2c11f0ac4794bda216f50df0c48e`，与 GitHub 附件及本地归档一致。

下列 PNG 均为原始截图逐字节复制，未裁切、合成或重绘。保留电视既有壁纸、布局、常用应用、排序和儿童模式规则；采集后返回首页。仅打开页面，未输入家长密码、开启计时、移动排序、上传或删除素材、切换壁纸、设置自启或执行应用启禁/卸载。应用管理截图展示已存在的本机ADB连接，未变更系统HOME。家长验证图为空白输入；关于页只有项目及作者原有公开二维码，没有上传会话、设备地址或私人恢复码。

第三方应用图标和设备已有自定义壁纸属于各自权利人，见[资源说明](../../THIRD_PARTY_NOTICES.md)。关于页的“恢复码”为入口按钮，未打开实际恢复码。截图证明页面显示，不能代替完整功能、升级数据、实体遥控器、HOME、重启、唤醒或其他机型验收。旧版素材保留为历史记录。

| 文件 / File | 原始 PNG SHA-256 |
| --- | --- |
| [home-1.2.4.png](home-1.2.4.png) | `caac0ad39acc2ef1bd1b0ec55818cb169927c1a9b5e2bb49507fa438bec58256` |
| [all-apps-1.2.4.png](all-apps-1.2.4.png) | `b6a0e2de9644dad1a980a463533a2c1be98c16c8d09a3fd2358757055cd8f8a1` |
| [desktop-settings-1.2.4.png](desktop-settings-1.2.4.png) | `99a138302bc05995c926633cc1a8bd92264c4c1eea28e2de50091d2dfb340e61` |
| [wallpaper-1.2.4.png](wallpaper-1.2.4.png) | `0ad3a37e96a6ba1cf539f97f8cd3790a4b8f2fc5b6886d4810ff2e111a654d73` |
| [weather-1.2.4.png](weather-1.2.4.png) | `56284a02c293cfd1147632ce3521de35f36af3e31b45de0b8a876ef2fe6c9b9f` |
| [app-management-1.2.4.png](app-management-1.2.4.png) | `7e13d4114527fdaff4a7a2fede5276a224a1362b4910ddb81bcd52b305a4f42f` |
| [sort-1.2.4.png](sort-1.2.4.png) | `31c204f111e90f34ad3ba7e0f30dc64be95a11dc5d9272623e425c69cd85c549` |
| [child-auth-1.2.4.png](child-auth-1.2.4.png) | `7a3a4def3edc18d8bc645e81ebd819f8b0967d36be66db5c9bd2ea1c24b4a009` |
| [update-1.2.4.png](update-1.2.4.png) | `3fbdefae79b198d4bfd42a53a7a09e1e1efec01b2e0e02865b5347207b159f64` |

Captured directly from Sony BRAVIA VH1 / Android 12 running the published 1.2.4 / 64 ARM32 APK, verified against its installed-file SHA-256. Original 1920×1080 PNGs are copied unchanged. Existing customization and child rules were retained, and the TV was returned home. Parent verification is blank; the About image contains only already-public project/author QR codes and a recovery-entry button, not a private recovery code. Captures show pages, not full runtime, upgrade-state, physical Home, reboot/wake or other-model acceptance. Older assets remain historical evidence.

## 1.3.0 模拟器截图 / Emulator captures

2026-10-28，通过已运行的 Android TV / Android 13（API33）ARM64 模拟器采集，原始画面均为 1920×1080。安装版本为已发布的 [1.3.0 测试版](https://github.com/BeatsAD/quiet-tv/releases/tag/v1.3.0)标准通用包（构建117，包名 `home.quiet.tv`）；读取已安装 APK，其 SHA-256 为 `6cd523fae03e632eaaf4fa98a5dd8d4034260150afbf9e8069015d57c7751dea`，与公开下载包逐字节一致。

六张图片均通过 ADB `exec-out screencap -p` 直接获取，保留原始 PNG，没有裁切、合成、重绘或修改界面。只浏览页面和筛选应用列表，未安装应用，未修改桌面、壁纸、天气、儿童模式、自启或应用启禁设置；结束后返回原应用管理页面。模拟器已启用硬件键盘和多点触摸，并连接 AndroidWifi，核对了 wlan0 联网。版本页仅含项目已有公开联系信息及入群二维码，没有打开恢复码或上传会话。

| 文件 / File | 对应功能 / Feature | 原始 PNG SHA-256 |
| --- | --- | --- |
| [home-1.3.0.png](home-1.3.0.png) | 常用应用、时间与天气 / Home apps, time and weather | `16ab5665b54fb7ef57a72dfd91f886a56ad9de05d7b2d6a35025358b0aa333c3` |
| [desktop-settings-1.3.0.png](desktop-settings-1.3.0.png) | 布局与实时预览 / Layout and live preview | `5f107114bc208dbecd1ad28f8dec68269cb1d562dfa269a8a530b7d183a5c89b` |
| [wallpaper-1.3.0.png](wallpaper-1.3.0.png) | 每日壁纸与上传入口 / Daily wallpaper and upload entry | `ed6e6eb4b844da28f7b0fc6456e0e49d506a74a69810278acbc2404771551a6c` |
| [weather-1.3.0.png](weather-1.3.0.png) | 城市与天气来源 / City and weather sources | `381a7aac4bfadd9dc70a7b35f3e82d498b00c382fba2ed364917aff76a9df5b5` |
| [app-management-1.3.0.png](app-management-1.3.0.png) | 应用分类和详情 / App categories and details | `a0779f1b55ece43646b23f5a65fdafa40ab860ae3f015f8b44b43fd2d60a3a7f` |
| [update-1.3.0.png](update-1.3.0.png) | 当前版本与检查更新 / Current version and update check | `520db3e9492d55e825af7f78d5fb7374423278ccfa373d6f4cf8593d42be19d9` |

These original 1920×1080 captures show the published 1.3.0 / 117 universal standard APK running on an Android TV / Android 13 ARM64 emulator. The installed APK matches the public download byte for byte. Pages and app-category filters were viewed without changing app settings or installed packages; the original app-management page was restored. Hardware keyboard, multi-touch, AndroidWifi and wlan0 connectivity were checked. The version page contains only existing public contact information and a group QR code; no private recovery code or upload session was opened.

模拟器截图只展示界面，不代表厂商电视、实体 HOME、冷启动、待机、视频壁纸或完整升级流程已经验收。旧版本截图保留原样；图中第三方应用图标及每日壁纸权利归各自权利人，见[资源说明](../../THIRD_PARTY_NOTICES.md)。

Emulator captures demonstrate displayed interfaces, not vendor-TV, physical Home-button, cold-boot, standby, video-wallpaper or full-upgrade acceptance. Earlier captures remain unchanged. Third-party app artwork and daily wallpaper retain their respective rights; see [third-party notices](../../THIRD_PARTY_NOTICES.md).
