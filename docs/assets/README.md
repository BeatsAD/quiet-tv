# 展示素材来源 / Showcase provenance

| 文件 / File | 来源 / Source | 说明 / Notes |
| --- | --- | --- |
| [quiet-tv-icon.svg](quiet-tv-icon.svg) | 按用户选定的方案 1「Q 字电视」重建的矢量图标 / Vector reconstruction of selected concept 1 | 深蓝底、雾白 Q 形屏幕、浅蓝尾部；与 Android `launcher_icon.xml` 和关于页内联 SVG 保持相同几何与配色 / Shared geometry and palette across Android and About |
| [home.png](home.png) | 本地 `evidence/public-showcase-20261019/home.png` | Sony KD-65X9000H，2026-10-19，1920×1080，原始 ADB 截图，逐字节复制 / Original ADB capture, copied unchanged |
| [wallpaper-settings.png](wallpaper-settings.png) | 本地 `evidence/public-showcase-20261019/wallpaper-settings.png` | 同机型与日期，原始 ADB 截图，逐字节复制 / Same device and date, copied unchanged |
| [cover.png](cover.png) | 内置 imagegen 工具 / Built-in imagegen | AI 生成的项目品牌插画；不作为设备验证证据 / AI brand illustration, not hardware evidence |
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
