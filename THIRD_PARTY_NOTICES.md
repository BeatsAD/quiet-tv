# 第三方项目与资源说明

## tv-vibehack

- 原项目：[Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack)
- 原作者版权：Copyright (c) 2026 Gathin
- 许可证：MIT
- 完整上游许可快照：[tv-vibehack-LICENSE.txt](app/src/main/assets/licenses/tv-vibehack-LICENSE.txt)
- 许可核对版本：[19f3c224fe68843cb2ff0df3157b81622bbe1266](https://github.com/Gathin23/tv-vibehack/tree/19f3c224fe68843cb2ff0df3157b81622bbe1266)，核对日期为 2026-10-18。此版本用于固定许可来源，不代表已经确认最初派生代码所用的提交。

Quiet TV 基于该项目开发，面向国内电视机的适配与应用升级改造，独立维护。原作者版权、MIT 授权条件与免责声明予以保留。上游许可快照放在 Android assets 中，后续构建 APK 时会随应用打包。

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


## 电视端 ADB 与证书依赖

- [libadb-android 3.1.1](https://github.com/MuntashirAkon/libadb-android/tree/3.1.1)：采用其 Apache-2.0 许可选项，保留继承代码的 BSD-3-Clause 和 MIT 声明。许可文本随 APK 保存在 `assets/licenses/libadb-*.txt`。
- [Bouncy Castle 1.86](https://github.com/bcgit/bc-java/tree/r1rv86)：bcprov、bcpkix 及 bcutil 的 jdk15to18 版本，用于独立密钥的自签证书和库内部 RSA 公钥编码，许可见 `assets/licenses/bouncycastle-LICENSE.md`。此版本为当前源码依赖；历史发布包的依赖不会随源码维护改变。
- 首版仅使用传统 TCP，显式排除未使用的 `spake2-android` 原生无线配对依赖。
