# 第三方许可与资源

Quiet TV 基于 [cleanyourtv](https://github.com/Gathin23/cleanyourtv)（原名 tv-vibehack）独立维护。项目代码采用 [MIT 许可证](LICENSE)，保留 Gathin 与 BeatsAD 的版权声明、许可条件和免责声明。

## 开源组件

| 项目或资源 | 许可与原文 |
| --- | --- |
| cleanyourtv | MIT，[上游许可](app/src/main/assets/licenses/tv-vibehack-LICENSE.txt) |
| [Aerial](https://github.com/pengchujin/aerial-tv)（部分实现参考） | MIT，Copyright (c) 2026 pengchujin，[许可原文](app/src/main/assets/licenses/aerial-MIT.txt) |
| AndroidX（含 Media3）、ExoPlayer（安卓4.1–4.4软解适配）、ZXing | Apache-2.0，[许可原文](app/src/main/assets/licenses/libadb-Apache-2.0.txt) |
| libadb-android | Apache-2.0，保留其继承的 BSD／MIT 声明，见[完整许可目录](app/src/main/assets/licenses/) |
| Bouncy Castle | MIT，[许可原文](app/src/main/assets/licenses/bouncycastle-LICENSE.md) |
| NanoHTTPD | BSD-3-Clause，[许可原文](app/src/main/assets/licenses/nanohttpd-LICENSE.txt) |
| React、React DOM（手机扫码设置） | MIT，[许可原文](app/src/main/assets/licenses/phone-react-MIT.txt) |
| Radix Icons（手机扫码设置） | MIT，[许可原文](app/src/main/assets/licenses/phone-radix-icons-MIT.txt) |
| Google Material Symbols（APK 图标） | Apache-2.0，[许可原文](app/src/main/assets/licenses/libadb-Apache-2.0.txt)，[来源与转换说明](app/src/main/assets/licenses/material-symbols-NOTICE.txt) |
| Pictogrammers Material Design Icons（图片文件图标） | Apache-2.0，[许可原文](app/src/main/assets/licenses/libadb-Apache-2.0.txt)，[来源与转换说明](app/src/main/assets/licenses/mdi-file-image-NOTICE.txt) |
| Phosphor Icons（既有资源） | MIT，Copyright (c) 2023 Phosphor Icons，[许可原文](app/src/main/assets/licenses/Phosphor-LICENSE.txt) |
| Lucide 图标（首页、快捷控制、其他页面及手机扫码网页功能图标） | ISC／MIT，[许可原文](app/src/main/assets/licenses/Lucide-LICENSE.txt)，[固定源码与图标映射](app/src/main/assets/licenses/Lucide-NOTICE.txt) |
| [Open Water](https://github.com/Leonxlnx/open-water)（天气噪声与光照计算） | MIT，Copyright (c) 2026 Leonxlnx，[许可原文](app/src/main/assets/licenses/open-water-MIT.txt) |
| Outfit 字体 | SIL OFL 1.1，[许可原文](app/src/main/assets/fonts/OFL.txt) |
| 中国行政区划数据 | WTFPL 2.0，[许可原文](app/src/main/assets/licenses/china-regions-WTFPL.txt) |
| 可选酷开独立助手 | MIT，[独立许可](coocaa-home-shim/LICENSE) |

完整版权与许可文本随 APK 离线提供。

## 在线服务与媒体

- 必应每日壁纸保留图片版权与来源链接，在线图片不作为内置资源分发。用户上传的图片和视频由用户确认使用权。
- 天气地区补充坐标与 Open-Meteo 数据使用 CC BY 4.0；[Open-Meteo 免费接口](https://open-meteo.com/en/terms)仅限非商业使用。和风、OpenWeather 等服务按各自条款使用，界面保留实际数据来源。
- 字体、图标、地区数据、天气数据及媒体资源分别遵循各自许可，不统一纳入项目 MIT 授权。

## TMDB 每日电影

电影资料通过 TMDB API 自动获取，TMDB 蓝色短版标识来自 [官方标识页面](https://www.themoviedb.org/about/logos-attribution)，标识与影片图片版权归各自权利人。电影详情保留 TMDB 来源和评分来源，不把剧情简介标记为影评或电影台词。

This product uses the TMDB API but is not endorsed or certified by TMDB.

## 可选组网组件

Tailscale核心固定为v1.104.4，使用Go 1.27.3构建。ARM64原生核心按需下载，基础APK不包含该原生库；完整依赖及Go运行时许可随应用离线提供，见`app/src/main/assets/licenses/remote-core-THIRD-PARTY.txt`，具体依赖见`remote-core/go.mod`和`remote-core/go.sum`。
