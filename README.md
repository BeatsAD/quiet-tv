# Quiet TV · Sony KD-65X9000H

这份工程已在局域网中的真实索尼电视上编译、安装与验证。桌面包名 `home.quiet.tv`，使用 Java、AndroidX RecyclerView 和 Android 原生绘制，没有收费功能。

## 当前设备和选择

- ADB：`192.168.124.10:5555`，配置见 `device.json`。
- 实机：Sony BRAVIA VH1 / KD-65X9000H，Android 12 / API 31，内存约 3 GB。
- 系统实际输出 UI 为 1920×1080；物理面板为 3840×2160。保留设备原有显示尺寸与密度设置。
- 默认 HOME：`home.quiet.tv/.HomeActivity`。
- Dock 从左到右：飞牛TV、云视听小电视（哔哩哔哩）、天光云影、迅雷云盘。
- 保留两版当贝市场、固件更新、DLNA 投屏、索尼输入与镜像组件、手机遥控。
- 关闭语音搜索和京东智能服务，其他取舍保存在 `preferences.json`。
- 目前使用以太网，Wi-Fi 原本关闭，桌面显示“有线网络”。

## 使用方式

左右在同一行移动，到行末停止；上下只移动一行。Dock 上方是三个快捷按钮，下方是其余应用的四列网格。网格最后一行不足四项时，焦点落到该行最近的有效列。顶部按钮左右到头后仍停留在顶部。

从应用按 HOME 会返回最后一个焦点。网格按返回键回 Dock。焦点位置也写入本地偏好，桌面进程重新启动后会恢复。

Dock 和应用网格选中卡片时保留边框与放大效果，卡片下方不再额外显示应用名称。Dock 背景与未放大卡片之间的上下左右内边距统一为 18 个布局单位（当前输出 36 px），不再保留名称区域。

Dock 最左／最右卡片选中时向内补偿放大与边框占用的空间，以白色焦点框外缘计算左右留白，仍保持 36 px。Dock 的焦点框与封面间距为 2 个布局单位，避免补偿后触碰相邻卡片；失焦后恢复原位置。焦点框按最终缩放后的封面范围计算。

“信号源”从 Android TV Input Framework 读取电视的实际输入，列出 HDMI 1–4、AV 和电视直播，通过标准 VIEW Intent 切换。它不会依赖原桌面。

电视直播读取系统频道列表；本次为 Quiet TV 授予了 `READ_TV_LISTINGS`，不读取影音应用的账号或媒体库。当前频道库为空，所以该项显示设置提示；只有用户选择“信号源设置”后才进入系统设置，不会自动搜台。配置频道后会通过频道 URI 打开直播。

网络按钮打开电视的网络设置。以后切到 Wi-Fi 时，Android 12 读取 SSID 需要位置权限，应用会请求该权限；系统定位开关关闭或拒绝权限时显示“Wi-Fi 已连接”。当前未切换 Wi-Fi，因此 SSID 显示未作真机验收。

## 备份和撤销

原始备份：`backups/20261018-075846/`，包括完整已安装/禁用/启用/第三方包列表、APK 路径与 UID、完整 getprop、global/secure/system 设置、完整 settings 与 package dumpsys、HOME 候选、输入、内存、显示和网络状态，以及原始截图。

本次每个禁用操作均先记入 `changes.json`，再单独执行 `pm disable-user --user 0`。禁用前已存在的两个包会保持原状，不纳入恢复范围。

双击 `restore.command`，或在本目录执行：

```sh
./restore.command
```

它逐个重新启用本次改变的包，设回原索尼当贝 HOME，发送 HOME 并保存恢复后的截图。Quiet TV 会继续保留安装，便于再次使用。此次没有卸载或清空任何系统应用数据。恢复脚本做了语法和清单核对，未运行整套恢复，以保留当前完成后的状态。

只恢复某个应用，例如腾讯视频：

```sh
.venv/bin/python scripts/tv.py shell 'pm enable --user 0 com.ktcp.tvvideo'
```

完整分类见 `PACKAGE-INVENTORY.md` 与 `package-inventory.csv`。准确禁用清单以 `changes.json` 为准。

## 重新编译并安装

双击 `build-and-install.command`，或执行：

```sh
./build-and-install.command
```

脚本使用 Gradle Wrapper 8.13 构建 debug APK，安装更新，打开桌面，发送一次右键并截图至 `evidence/build-installed.png`。它不会再次禁用系统包，也不会重设 HOME。

当前 Mac 已具备 JDK 21 和 Android SDK；`local.properties` 指向本机 SDK。依赖为 AGP 8.12.2 与 RecyclerView 1.4.0，compileSdk 36，minSdk/targetSdk 31，与本机 Android 12 相匹配。APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

在另一台 Mac 上需要 JDK 17 或 21、Android SDK platform 36 / build-tools 36，以及 Python 3。改好 `local.properties` 和 `device.json`，然后创建客户端环境：

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
```

恢复后若要再次启用这个桌面，可单独执行：

```sh
.venv/bin/python scripts/tv.py shell 'cmd package set-home-activity --user 0 home.quiet.tv/.HomeActivity'
.venv/bin/python scripts/tv.py shell 'input keyevent KEYCODE_HOME'
```

## 修改 Dock 顺序和壁纸

编辑 `app/src/main/assets/launcher.json`，`dock` 数组中四个包名的顺序就是从左到右的顺序，然后运行构建安装脚本。四个包应保持启用状态。当前配置：

```json
"dock": [
  "com.trim.tv",
  "com.xiaodianshi.tv.yst",
  "top.yogiczy.slcs.tv",
  "com.xunlei.downloadprovider.hd"
]
```

第二屏「全部应用」第一项是「壁纸设置」，不占用顶部快捷按钮，也不改变电视系统设置入口。可选择三种状态：

- **默认壁纸**：纯黑背景＋应用主题色光晕，随着 Dock 或网格选中应用换色；选择后停止风景图轮播。
- **固定风景图**：选择任意一张风景图并确定，关闭轮播并固定显示该图。
- **风景图轮播**：上移到「壁纸轮播：开／关」，按确定切换。关闭时固定当前风景图；开启时在三张风景图之间顺序轮播。从默认壁纸开启轮播时，使用上次的风景图。

左右移动只预览，按返回取消并恢复之前的壁纸和模式。壁纸选择、轮播开关与当前风景图均保存到本地，桌面进程重启后恢复。新安装默认使用纯黑＋主题色光晕，更新安装保留已有选择。

内置图片为本次用户确认的 Mac 自带「大苏尔海岸」「大苏尔群山」「大苏尔山路」，从本机 Apple 壁纸资源清单指定的官方 CDN 获取原图，裁为 1920×1080 JPG，存放于 `app/src/main/assets/wallpapers/`。原图来源记录在 `inspection/apple-wallpapers/approved-sources.json`。电视运行时不下载在线图片；风景模式使用暗色渐变遮罩保证文字可读，不随应用焦点改变颜色。

风景图轮播每 5 分钟切换一次，使用 900 ms 淡入淡出。仅在桌面前台、非默认壁纸且未打开壁纸设置时计时；离开桌面、关闭轮播或选择默认壁纸后停止计时，回到桌面或重新开启轮播时开始新的 5 分钟计时。

`language` 为 `zh` 或 `en`；`greeting` 为 `time`（分时问候）或 `home`（欢迎回家）；`hidden` 只控制桌面可见性，不禁用任何包。

## ADB 客户端与截图

本机原生 ADB 连接正常。若 macOS 原生 ADB 报 `No route to host`，在本目录改用已经配置的 Python 客户端，不继续排查网络路由：

```sh
TV_ADB_BACKEND=python .venv/bin/python scripts/tv.py shell 'getprop ro.product.model'
TV_ADB_BACKEND=python ./build-and-install.command
TV_ADB_BACKEND=python ./restore.command
```

它复用 `~/.android/adbkey`；电视如弹出“允许 USB 调试？”，在电视上确认。Python 安装路径通过 shell 分块传送 base64，校验整个 APK 的 MD5 后才调用 `pm install -r`，避免大文件 push 卡住。原生安装超时也会切到该分块路径。每个 pm 命令都是一次独立 shell 调用。

保存电视截图：

```sh
.venv/bin/python scripts/tv.py screenshot evidence/manual.png
```

`evidence/` 保存逐包禁用和界面验证的真实截图。哔哩哔哩使用 SECURE 窗口；HDMI 视频也拒绝截图，这两者只记录了前台窗口/TV Input 会话证据，没有绕过保护或伪造截图。飞牛TV、天光云影、迅雷的截图证明已进入应用，不能代替完整播放、声音或账号验收。

这台电视的 ADB 截图还会偶发漏掉文字或图层，保存到电视再 pull 也出现过；用户已确认电视本身一直显示完整正常。因此部分早期排查截图只作为截图异常记录，最终完整主页见 `evidence/29-final-home.png`。没有为此保留强制刷新或软件渲染逻辑。

## 资源与实现

时钟使用系统无衬线常规字重和等宽数字，保留 24 小时制；时间右侧用细竖线分隔日期，快捷按钮与时间日期行居中对齐。问候语使用小字号、较粗字重和适度字间距，应用名称使用常规字重，文字统一采用柔和亮白色。中文使用系统字体；英文界面其他文字仍使用随 APK 打包的 [Outfit](https://github.com/Outfitio/Outfit-Fonts)，字体及 SIL OFL 许可位于 `app/src/main/assets/fonts/`。

顶部三个快捷按钮采用中性半透明玻璃胶囊背景、柔和高光和细亮描边，透出下方壁纸或主题光晕，不添加固定灰紫色。按钮组的右边缘与 Dock 背景右边缘对齐，共用 48 个布局单位的页面边距（当前输出 96 px）；图标、文字、按钮尺寸及间距保持原样。

桌面无 INTERNET 权限，不发网络请求；时钟监听系统时间广播，网络状态使用系统回调；没有定时网络轮询、视频或实时模糊。默认壁纸在纯黑背景上叠加原先的两层椭圆径向渐变，应用焦点以 650 ms 过渡主题色；风景模式使用本地图片，不叠加主题色光晕，切图以 900 ms 淡入淡出。只在颜色或图片过渡期间逐帧刷新，静止后不持续绘制。顶部三个按钮使用本地矢量图标，网络图标区分以太网、Wi-Fi 和断开状态；图标与文字整体居中，长网络名保留省略显示。单个焦点层使用短时回弹动画，应用横幅缩至 360×203 后保存在内存。

输入实现参考 Android 官方 [TvInputManager](https://developer.android.com/reference/android/media/tv/TvInputManager) 与 [TvContract](https://developer.android.com/reference/android/media/tv/TvContract)。真机验收记录与限制见 `VERIFICATION.md`。
