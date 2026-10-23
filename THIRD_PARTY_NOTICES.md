# 第三方项目与资源说明

## tv-vibehack

- 原项目：[Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack)
- 原作者版权：Copyright (c) 2026 Gathin
- 许可证：MIT
- 完整上游许可快照：[tv-vibehack-LICENSE.txt](app/src/main/assets/licenses/tv-vibehack-LICENSE.txt)
- 许可核对版本：[19f3c224fe68843cb2ff0df3157b81622bbe1266](https://github.com/Gathin23/tv-vibehack/tree/19f3c224fe68843cb2ff0df3157b81622bbe1266)，核对日期为 2026-10-18。此版本用于固定许可来源，不代表已经确认最初派生代码所用的提交。

Quiet TV 基于该项目开发，面向国内电视机的适配与应用升级改造，独立维护。原作者版权、MIT 授权条件与免责声明予以保留。上游许可快照放在 Android assets 中，后续构建 APK 时会随应用打包。

## Aerial 桌面守护（实验）参考

- 来源：[pengchujin/aerial-tv](https://github.com/pengchujin/aerial-tv/tree/da9cedc2c5887a46d62b1f54699567a5deac5c2a)，固定提交 `da9cedc2c5887a46d62b1f54699567a5deac5c2a`。
- Copyright (c) 2026 pengchujin，MIT；[完整许可证](app/src/main/assets/licenses/aerial-MIT.txt)随主程序打包。
- 参考原主页窗口识别及酷开私有 HOME 类别，后续恢复机制固定参考提交 [bd30a9f9fbbcbb8e8ff02963dc265714e7a67a8e](https://github.com/pengchujin/aerial-tv/tree/bd30a9f9fbbcbb8e8ff02963dc265714e7a67a8e)。在现有 Java 结构实现自动策略、普通主页快速接管、开机稳定期复核、通知监听恢复与有限重绑；未引入账号同步、全局按键过滤、后台 ADB 或调试常开。MIT 版权和许可继续随包保留。上游机型反馈不等于 Quiet TV 真机验收。

## coocaa-home-shim（可选独立助手）

- 来源：[VincentYe123/coocaa-home-shim](https://github.com/VincentYe123/coocaa-home-shim/tree/281a6a1362a9e30faaeb2169b75ba701dd0b1a03)，固定提交 `281a6a1362a9e30faaeb2169b75ba701dd0b1a03`。
- Copyright (c) 2026 VincentYe123，MIT；[完整许可证](coocaa-home-shim/LICENSE)随助手 APK 打包。
- 移植仅调整 Quiet TV 目标组件、构建配置及外部调用入口；保留上游主页中转、原桌面回退和短时开机处理。该助手不包含在普通 Quiet TV APK 内。
- [构建、安装和恢复说明](coocaa-home-shim/README.md)；Quiet TV 移植尚未经过真实酷开电视验收。

## Outfit 字体

- 来源：[Outfit Project](https://github.com/Outfitio/Outfit-Fonts)
- 版权：Copyright 2021 The Outfit Project Authors
- 字体文件：`app/src/main/assets/fonts/outfit.ttf`
- 独立许可：SIL Open Font License 1.1，完整文本见 [OFL.txt](app/src/main/assets/fonts/OFL.txt)，随字体一起打包。

上游许可快照中提到的 `launcher/app/src/main/res/font/outfit.ttf` 和 `launcher/FONT-LICENSE-OFL.txt` 为上游目录；本项目对应文件为本节所列路径。

## 大苏尔风景壁纸

当前内置的三张图片来自本机 Apple 壁纸资源清单所列的官方 CDN，裁剪为 1920×1080 JPG 后供当前设备离线使用：

| 本项目文件 | 资源名称 | 原始来源 |
| --- | --- | --- |
| `app/src/main/assets/wallpapers/big-sur-coastline.jpg` | Big Sur Coastline | [Apple CDN](https://updates.cdn-apple.com/2025/mobileassets/072-29818/74AF556A-DC02-429B-894B-23AB61E2A5F9/com_apple_MobileAsset_DesktopPicture/bec472e7c6e54ee0765abffa76c624290b9ef2c2.zip) |
| `app/src/main/assets/wallpapers/big-sur-mountains.jpg` | Big Sur Mountains | [Apple CDN](https://updates.cdn-apple.com/2025/mobileassets/072-29818/74AF556A-DC02-429B-894B-23AB61E2A5F9/com_apple_MobileAsset_DesktopPicture/7a1f6e346a253992b2df0358398caafb6d75fe4a.zip) |
| `app/src/main/assets/wallpapers/big-sur-road.jpg` | Big Sur Road | [Apple CDN](https://updates.cdn-apple.com/2025/mobileassets/072-29818/74AF556A-DC02-429B-894B-23AB61E2A5F9/com_apple_MobileAsset_DesktopPicture/643aa03a1828fb2170170ef999b18900bd6b26f6.zip) |

这些图片的权利归各自权利人所有，不属于本项目 MIT 许可的代码。上述来源记录不是再分发授权；尚未记录可供公开再分发的授权依据，公开发布源码或 APK 前需核实相应权利，或替换为获准再分发的图片。


## 金门大桥日落动态壁纸

内置视频「金门大桥日落 / Golden Gate Sunset」选自本机 macOS 已下载的 Apple 航拍壁纸，资源 ID 为 `4207734D-74FE-4F92-B5E1-6EC8DEE24A15`。本机资源清单名称为 `Golden Gate Sunset`，[原始 Apple CDN 来源](https://sylvan.apple.com/itunes-assets/Aerials116/v4/cb/5b/50/cb5b5035-6701-619f-9065-3d7d0e5fbef4/GG_A_SUNSET_MarshallsBeach_c28_v7_24comp_HFR_16Mbps.mov)。

- 视频：`app/src/main/assets/wallpapers/golden-gate-sunset.mp4`，从原视频前 24 秒制作约 23 秒循环，结尾与开头进行 1 秒淡化衔接，转为 1920×1080、30 FPS、SDR、H.264 Baseline Level 4.0，无音轨。
- 封面：`app/src/main/assets/wallpapers/golden-gate-sunset.jpg`，取内置视频首帧，供离线预览及播放前展示。
- 首次开启动态壁纸时使用该内置选项；已有当前壁纸及最近使用的视频选择保留。内置视频不占上传视频配额，可从列表移除，并通过「恢复内置壁纸」恢复。

资源权利归相应权利人所有，不属于本项目 MIT 许可的代码；来源记录不代表公开再分发授权，与上文大苏尔图片采用相同的本地使用及发布前权利核实边界。

## 电视端 ADB 与证书依赖

- [libadb-android 3.1.1](https://github.com/MuntashirAkon/libadb-android/tree/3.1.1)：采用其 Apache-2.0 许可选项，保留继承代码的 BSD-3-Clause 和 MIT 声明。许可文本随 APK 保存在 `assets/licenses/libadb-*.txt`。
- [Bouncy Castle 1.86](https://github.com/bcgit/bc-java/tree/r1rv86)：bcprov、bcpkix 及 bcutil 的 jdk15to18 版本，用于独立密钥的自签证书和库内部 RSA 公钥编码，许可见 `assets/licenses/bouncycastle-LICENSE.md`。此版本为当前源码依赖；历史发布包的依赖不会随源码维护改变。
- `1.1.0 / b4` 候选接入无线配对，包含 [spake2-android 2.2.1](https://github.com/MuntashirAkon/spake2-java/tree/2.2.1) 的未修改原生库，采用 LGPL-3.0；版权归 Muntashir Al-Islam 等上游作者。LGPL 与 GPL 文本见 `assets/licenses/spake2-*.txt`。相同版本的完整库源码及构建脚本可从该标签取得；本项目通过 Gradle 引用，保留独立共享库及源码重建／替换路径。上游 Ed25519 实现来源与 CC0 说明见其 README。
- [Conscrypt 2.7.0](https://github.com/google/conscrypt/tree/2.7.0) 提供 TLS 1.3 与公开密钥导出接口，采用 Apache-2.0；其及内含原生代码的声明随 APK 保存为 `assets/licenses/conscrypt-LICENSE.txt`、`conscrypt-NOTICE.txt`。历史 TCP 版本未包含以上无线依赖。

## AndroidX 返回导航

- [AndroidX Activity 1.10.1](https://developer.android.com/jetpack/androidx/releases/activity#1.10.1) 及其 AndroidX 生命周期依赖用于统一 Android 6 与新版系统的返回操作，采用 Apache-2.0。完整 Apache-2.0 文本随 APK 保存于 `assets/licenses/libadb-Apache-2.0.txt`；该文本同时适用于这些 AndroidX 组件。版权归 The Android Open Source Project 等贡献者。

## 每日在线壁纸

每日壁纸按用户选择直接访问必应国区或搏天风景接口及其图片 CDN，只在桌面前台按需请求，将有限缓存保存在电视本机。第三方服务可看到通常的网络请求信息；本功能不上传用户图片，不向提供方发送设备标识或位置。

- 必应保留接口返回的图片标题、版权及来源链接，来源见 [Bing 首页说明](https://support.microsoft.com/en-us/topic/explore-the-homepage-bab2022c-cdb4-4d1f-80e2-c8f5c55712c7)。接口可访问不表示所有图片获得再分发授权，在线图片不作为应用内置资源分发。
- 搏天采用 [随机壁纸接口](https://api.btstu.cn/doc/sjbz.php)的电脑端风景类别，每天缓存一张。接口未提供完整摄影署名时不编造作者，图片权利仍归原权利人。
- Android 7.1 及以下仅对 `api.btstu.cn`、`img.btstu.cn` 请求补充官方 [ISRG Root X1](https://letsencrypt.org/certificates/) 根证书，保留系统信任及正常域名校验，不修改设备证书库。证书文件为 `app/src/main/assets/certificates/isrg-root-x1.pem`，DER SHA-256 为 `96bcec06264976f37460779acf28c5a7cfe8a3c0aae11a8ffcee05c0bddf08c6`。此证书用于验证 HTTPS，不是私钥。

## 壁纸局域网上传

- [NanoHTTPD 2.3.1](https://github.com/NanoHttpd/nanohttpd/tree/nanohttpd-project-2.3.1)：用于临时 HTTP 上传服务，BSD-3-Clause，版权归 NanoHTTPD 贡献者。完整声明随 APK 保存于 `assets/licenses/nanohttpd-LICENSE.txt`。
- [ZXing Core 3.5.3](https://github.com/zxing/zxing/tree/zxing-3.5.3)：仅用于生成上传地址二维码，Apache-2.0，版权归 ZXing Authors。
- [AndroidX ExifInterface 1.4.2](https://developer.android.com/jetpack/androidx/releases/exifinterface#1.4.2)：用于读取上传照片方向，Apache-2.0，版权归 The Android Open Source Project。以上 Apache-2.0 文本随 APK 保存在 `assets/licenses/libadb-Apache-2.0.txt`。

## Android 4.4 compatibility profile

The `legacy19` profile uses AndroidX Activity 1.9.3, RecyclerView 1.3.2 and MultiDex 2.0.1 under Apache-2.0. Their complete license text is included in the offline license assets. It omits Conscrypt and SPAKE2 from its dependency graph; their historical notices remain available for the standard profile. Bouncy Castle and the existing minimum patched version constraints are retained.

The wallpaper uploader in `legacy19` uses ZXing Core 3.3.0 and AndroidX ExifInterface 1.3.7 under the same Apache-2.0 license to retain Android 4.4 compatibility. The standard profile retains ZXing Core 3.5.3 and ExifInterface 1.4.2.

## Wallpaper interface icons

The wallpaper settings and upload dialog use raster exports of Lucide 0.468.0 icons (chevron-left, upload, sliders-horizontal, trash-2, check, x, rotate-ccw). Original source: https://github.com/lucide-icons/lucide/tree/0.468.0/icons . The original ISC and Feather-derived MIT license notices are included in `app/src/main/assets/licenses/Lucide-LICENSE.txt` and the APK offline licenses.
