# 真机验收记录

设备：Sony KD-65X9000H / BRAVIA VH1，Android 12 / API 31。局域网 ADB：192.168.124.10:5555。日期：2026-10-18，Asia/Shanghai。

## 当前记录摘要（2026-10-18）

以下区分已安装、已发布与本次源码维护，不以历史记录证明电视此刻的状态；本次仓库维护未连接电视。

| 阶段 | 版本与证据 | 边界 |
| --- | --- | --- |
| 最近一次已记录的安装核对 | `1.0 / b1 / debug`，16:44 本地与电视 SHA-256 均为 `acd73dfe1d1965b4dbe7246bcee28e5e0cc8a26af7d15053a7453708776a961e`；本地 `dist/apk/quiet-tv_1.0_b1_debug.installed-20261018T164422557110+0800.json` | 原生管理改版已安装，见 [改版记录](docs/MANAGEMENT_REDESIGN.md)；仅证明该次核对时的状态 |
| 已发布测试包 | `1.0.1 / b3 / debug`，SHA-256 `b5ee799f1fd05e20359270720169fb0be3f52dd55f11c1ab23ed1c74a5bcd442`；[发布说明](docs/releases/v1.0.1.md) | 已上传预发布包，本地归档一致；无该包安装及实机验收记录 |
| 应用管理联合回归与重启 | [管理验收 E 阶段](docs/APP_MANAGEMENT.md#e-阶段联合回归与整机重启)，本地 `evidence/app-management/reboot.json` | 已实际整机重启，默认 HOME、Dock、壁纸及 23 个禁用包保持；该阶段读取到 218 个包，包含验收程序 |
| 本次依赖修复源码 | Bouncy Castle、网页原型依赖及维护文档；见 [依赖安全说明](docs/DEPENDENCY_SECURITY.md) | 尚未归档、发布或安装新包；构建验证不代替 Android 12 首次密钥生成、已有密钥连接与实机验收。下次交付新内容须递增构建号 |

## 历史状态：精简与首页样式阶段

下列数量、APK 哈希和日志均属于各自当次操作，早于后续应用管理与发布阶段。

- 原有 216 个包全部仍安装；新增 Quiet TV 后共 217 个。
- 本次禁用 21 个包；加上原先禁用的 2 个，当前禁用共 23 个，与恢复清单完全一致。
- 用户要求保留的 6 个应用包全部启用；更新、DLNA、镜像宿主、手机遥控仍保留。
- 默认 HOME 为 `home.quiet.tv/.HomeActivity`；原桌面 `com.dangbei.TVHomeLauncher` 为 disabled-user。
- 上一版风景壁纸桌面瞬时快照 TOTAL PSS 为 75023 KB，约 73.3 MiB；TOTAL RSS 为 171116 KB。两者口径不同，不表示整台电视节省的 RAM；原先黑底光晕版 PSS 约 34.1 MiB，图片解码与预览会增加内存使用。
- 当前焦点间距统一版 APK：SHA-256 `78600a7b0ee843a65a4b8f77c6563d61a1a58311e305e30a8fa55b4bde9346b9`，本地与电视已安装 APK 一致，核对见 `evidence/focus-gap-install.json`。
- 当前桌面进程日志未发现 FATAL EXCEPTION。Java 构建成功，Python 与两个 command 脚本通过语法检查。

## 验证证据

表内早期主题光晕截图保留作历史记录；当前版本同时支持默认的黑底主题光晕和用户确认的三张风景壁纸，入口更名为「壁纸设置」，记录见下方新增项。

| 项目 | 结果与证据 |
| --- | --- |
| Dock 与网格选中框间距统一 | 原因是 Dock 外扩为 2 布局单位、网格外扩为 5；现所有 `Tile` 统一外扩 2，保留白框粗细、放大比例与顶部按钮参数。Debug 构建、Sony BRAVIA VH1 更新安装及 APK SHA-256 核对通过。网格修复前后见 `evidence/focus-gap-grid-before.png`、`evidence/focus-gap-grid-after.png`，Dock 见 `evidence/focus-gap-dock-after.png`；网格截图 y=850 的左侧白框由 x=92–97 收紧为 x=98–103，卡片位置未变。本次启动附近 AndroidRuntime 错误日志为空。 |
| Dock 背景统一玻璃材质 | 将黑色托盘背景替换为右上角按钮共用的 `pillSurface()`：白色半透明纵向渐变、细白描边与原有 22 布局单位圆角，布局和卡片未改动。Debug 构建、Sony BRAVIA VH1 更新安装及本地／已安装 APK SHA-256 核对通过；`evidence/dock-glass-final.png` 确认山景壁纸透出、玻璃外框与白色焦点框正常。本次启动附近 AndroidRuntime 错误日志为空。本轮只验证当前山景壁纸下的静态显示。 |
| 移除选中框黑色衬边 | 移除共享焦点框绘制中的 5 布局单位黑色衬边，保留 3 布局单位白框、玻璃填充与放大动画。Debug 构建、Sony BRAVIA VH1 更新安装及本地／已安装 APK SHA-256 核对通过；实机飞牛TV选中截图见 `evidence/focus-no-black-outline-feiniu.png`，白色封面选中截图见 `evidence/focus-no-black-outline.png`。本次启动附近 AndroidRuntime 错误日志为空；顶部按钮与应用网格使用同一绘制实现，本轮未逐项截图。 |
| Dock 选中态左右留白一致 | 两端卡片聚焦时向内补偿，保留 1.035 倍放大，并修正焦点框混用缩放前后坐标的问题；Dock 框外扩改为 2 个布局单位以避免碰到邻卡。`evidence/dock-focus-spacing-left.png`、`evidence/dock-focus-spacing-right.png`、`evidence/dock-focus-spacing-unfocused.png` 为三个实机状态。PNG 在 y=850 直边处量测：背景 x=96–1824，可见外缘 x=132–1788，三种状态左右均为 36 px；选中侧以白色框外缘为准，结果见 `evidence/dock-focus-spacing-measurements.json`，XML 为另时刻的结构参考。构建与真机更新安装通过，APK MD5 一致，当前进程未见 FATAL EXCEPTION，见 `evidence/dock-focus-spacing-install.json` 和 `evidence/dock-focus-spacing-process.log`。 |
| 壁纸轮播按钮尺寸收紧 | 按钮由 240×48 缩至 144×40 布局单位（当前输出 288×80 px），字号由 16 改为 14，右边界仍为 x=912 布局单位，与页面右边距对齐；垂直中心保持一致。`evidence/wallpaper-button-compact-picker.png` 和 `evidence/wallpaper-button-compact-picker-focused.png` 确认普通及聚焦状态文字完整、焦点框贴合。构建更新安装通过，本地与已安装 APK MD5 一致，见 `evidence/wallpaper-button-compact-install.json`。 |
| 删除提示后的间距收拢 | 状态行从 top=106 上移至原提示所在的 top=73 布局单位，接在标题下方，不再保留空白提示行。构建与更新安装通过，本地和已安装 APK MD5 一致；实机截图见 `evidence/wallpaper-settings-spacing-updated.png`，安装核对见 `evidence/wallpaper-settings-spacing-install.json`。 |
| 壁纸设置文案简化 | 移除标题下方的左右预览／确定使用／返回取消提示，轮播状态文案改为「当前：自动轮播【每 5 分钟】」。构建与更新安装通过，本地及已安装 APK MD5 一致；实机截图见 `evidence/wallpaper-settings-copy-updated.png`，核对见 `evidence/wallpaper-settings-copy-install.json`。仅修改提示与状态文字，原模式选择和轮播配置保留。 |
| 壁纸设置、更改模式与恢复默认 | 第二屏入口、卡片名称和选择页标题统一为「壁纸设置」。选择页四张卡片为默认壁纸及三张已确认风景图，右上角显示「壁纸轮播：开／关」。关闭轮播后固定当前风景图；选择默认壁纸后保存 default=true、rotating=false，恢复原版两层主题色光晕。蓝色／粉色应用焦点截图见 `evidence/wallpaper-settings-default-blue.png`、`evidence/wallpaper-settings-default-pink.png`，默认选中与轮播关闭状态见 `evidence/wallpaper-settings-default-checked.png`。默认模式重启桌面进程后恢复、取消风景预览保留默认模式均通过偏好读回断言，见 `evidence/wallpaper-settings-checks.json`。构建安装通过，已安装 APK MD5 一致；本次进程日志未见 FATAL EXCEPTION。5 分钟计时沿用上次正式间隔实测，本轮未重复等待完整周期。交付前已恢复此前的山路轮播选择，见 `evidence/wallpaper-settings-final.png`；本轮 7 项功能读回检查全部通过。 |
| 风景壁纸与第二屏入口 | `evidence/wallpaper-entry.png`、`evidence/wallpaper-picker-coastline.png`、`evidence/wallpaper-preview-mountains.png`、`evidence/wallpaper-preview-road.png`；三张为用户逐项确认的 Mac 自带大苏尔海岸、群山与山路。第二屏首张卡片为壁纸入口，顶部仍为信号源、网络、设置三个按钮。左右全屏预览、确定固定、返回取消恢复原图已实测；固定山路后重启桌面进程，图片、模式、焦点仍恢复，见 `evidence/wallpaper-fixed-road-restarted.png` 与 `evidence/wallpaper-fixed-checkmark.png`。本地偏好读回断言见 `evidence/wallpaper-functional-checks.json`。构建、更新安装及已安装 APK MD5 核对通过，本次进程日志未见 AndroidRuntime 异常。 |
| 壁纸自动轮播计时 | 正式版本以实际 5 分钟间隔运行，09:12:49 左右开启轮播，09:17:49 日志记录自动显示群山（以电视本地时间为准）。`evidence/wallpaper-slideshow-start.png` 与 `evidence/wallpaper-slideshow-after-five-minutes.png` 显示海岸到群山的真实切换，偏好读回由 index=0 变为 index=1、rotating=true；`evidence/wallpaper-current-process.log` 与 `evidence/wallpaper-functional-checks.json` 保存结果。900 ms 淡入淡出为代码实现，静态截图不证明动画时长；没有缩短正式计时参数，未等待完整 15 分钟三张循环或重启整机。最终保持自动轮播、停留首页飞牛TV焦点。 |
| 原始设备与完整备份 | `backups/20261018-075846/00-original.png`、该目录完整属性/设置/包清单 |
| 逐包禁用 | 21 份 `evidence/disabled-*.png`，每次单独 pm 调用后发送右键，再截图检查 |
| 最终主页 | `evidence/29-final-home.png`，用户同时确认电视本身显示完整正常 |
| 顶部玻璃按钮及右侧对齐 | `evidence/glass-pills-blue.png`、`evidence/glass-pills-pink.png`、`evidence/glass-pills-final.png`；中性半透明胶囊、柔和高光及细描边，蓝色和粉色背景自然透出，保留原图标与文字。`evidence/glass-pills-ui.xml` 记录三个按钮均为 216×88 px、间距 24 px；设置按钮与 Dock 背景的右边界均为 x=1824，距屏幕右侧均为 96 px。信号源和设置焦点截图见 `evidence/glass-pills-input-focus.png`、`evidence/glass-pills-settings-focus.png`。构建、真机更新安装、APK MD5 核对通过，当前进程日志未见 AndroidRuntime 异常；最终停留在飞牛TV选中状态 |
| 参考图文字排版 | `evidence/47-final-reference-typography-pink.png`，问候语改为 14/600、系统时钟 50/400、日期 14/500、应用名称 40/400（字号为布局单位）；亮白文字，时间日期同行并加细竖线，右侧按钮与该行居中对齐，应用名称统一左边距并靠近 Dock。保留中文、24 小时制、光晕、图标、紧凑按钮及加粗焦点框；构建、更新安装、APK MD5 核对通过，当前进程日志未见 AndroidRuntime 异常。46 号截图漏掉问候语和应用名称，以完整的 47 号图为准 |
| 移除选中卡片底部名称 | `evidence/42-no-selected-label-dock.png`、`evidence/43-no-selected-label-grid.png`、`evidence/44-no-selected-label-final.png`；Dock 与应用网格选中后不再额外绘制底部应用名称，焦点边框、放大动画和主题换色逻辑保留；构建、更新安装、本地与已安装 APK MD5 核对通过，本次进程日志未见 AndroidRuntime 异常 |
| Dock 四周内边距一致 | `evidence/dock-equal-padding-unfocused.png`、`evidence/dock-equal-padding-left.png`、`evidence/dock-equal-padding-right.png`、`evidence/dock-equal-padding-final.png`；背景与未放大卡片的四周内边距统一为 18 个布局单位（当前输出 36 px），卡片视图高度贴合封面，去掉名称预留空间。截图确认普通状态与左右两端卡片选中效果；构建、更新安装及已安装 APK MD5 核对通过，安装核对见 `evidence/dock-equal-padding-install.json` |
| 网络按钮宽度调整 | `evidence/30-network-button-compact.png`，三个顶部按钮统一为 108×44 布局单位（当前输出 216×88 px），间距 24 px；构建、更新安装及 APK MD5 核对通过 |
| 网络按钮焦点 | `evidence/31-network-button-focused.png`，边框随新宽度正确包围按钮；该次 ADB 截图漏掉其他部分图层，完整普通状态以 30 号截图为准 |
| 焦点边框增强 | `evidence/32-strong-focus-dock.png`、`evidence/33-strong-focus-white-card.png`、`evidence/34-strong-focus-top.png`、`evidence/35-strong-focus-grid.png`；白色描边由 0.75 增为 3 布局单位（当前输出 6 px），不透明白色并增加深色衬边；Dock、白色封面、顶部按钮及应用网格焦点截图确认，构建、更新安装及 APK MD5 核对通过。34 号截图有此前记录的其他图层缺失 |
| 柔和光晕与快捷图标 | `evidence/39-final-soft-glow-blue-icons.png`、`evidence/40-final-soft-glow-pink-icons.png`、`evidence/41-final-icons-focus.png`；蓝色/粉色主题及有线网络、信号源、设置图标的完整实机截图确认；保持按钮原尺寸和加粗焦点框。网格随应用换色见 `evidence/38-soft-glow-grid.png`。650 ms 颜色过渡有代码实现，截图只验证静态结果；未实机切换 Wi-Fi/断网来验收另外两种网络图标。构建、更新安装、当前进程日志及 APK MD5 核对通过 |
| 四列网格 | `evidence/03-grid-clipping-fixed.png`、`evidence/25-pulled-grid-first.png` |
| 顶部左右边界 | `evidence/10-top-left-boundary.png`、`evidence/13-top-right-boundary.png`，日志保持 top=0 或 top=2 |
| 飞牛TV启动与 HOME 返回 | `evidence/05-launch-fntv.png`、`evidence/06-home-return-focus.png`，返回原焦点；应用弹出自己的更新提示，未执行更新 |
| 天光云影启动 | `evidence/08-launch-tiangung.png`，已进入加载直播源界面，未做完整播放验收 |
| 迅雷启动 | `evidence/09-xunlei-ready.png`，已进入迅雷界面，未更改账号或内容 |
| 哔哩哔哩启动 | 前台 Activity 与 `inspection/window-bili.txt` 确认已启动，主窗口 SECURE，无法截图验收 |
| HDMI/AV/直播菜单 | `evidence/26-final-input-menu.png`，由系统实际输入列表生成 |
| HDMI 2 切换 | `evidence/tv-input-after-hdmi2.txt` 确认 main session 为 ExternalTvInputService/HW3，设备映射为 HDMI 2；视频画面拒绝截图 |
| 直播无频道处理 | `evidence/28-live-tv-empty-state.png`，系统频道库为空，提供配置入口，没有搜台 |
| 设置与网络入口 | `evidence/14-settings-open.png`、`evidence/15-network-settings.png` |
| 进程重启焦点恢复 | `evidence/focus-log.txt` 与排查截图，重启后保持网格中的相同应用；整机重启未执行 |
| 恢复脚本 | 逐项清单与脚本语法核对；未实际运行全量恢复，保持交付状态 |

## 截图边界

### 09:31 底部提示文字裁切修复

- 原因：RecyclerView 开启 `clipToPadding`，底部 28 个布局单位的 padding 使可见区域截止于全屏 y=512；首页提示区域位于 y=504–524，下半部分被裁切（坐标为布局单位，实际输出乘以 2）。
- 修复：底部 padding 改为 8，可见区域延伸到 y=532，保留提示原位置、字号与裁剪开关。
- `:app:assembleDebug` 成功并通过 `pm install -r` 更新到真实电视。`evidence/hint-clipping-fixed-home.png` 确认“按下 ↓ 浏览全部应用”完整显示；`evidence/hint-clipping-grid-last-row.png` 确认末行卡片及选中边框可见，检查后返回首页。
- 本次 APK 为 8218516 字节，本地与已安装 APK MD5 均为 `357e260e8a8bc48ef1d0f1ec1112fbb2`，核对见 `evidence/hint-clipping-install.json`；当前进程日志 `evidence/hint-clipping-process.log` 未见 FATAL EXCEPTION。本条安装信息晚于上方历史记录。

ADB 在这台电视上偶发漏掉部分文字或图层，电视端存图再 pull 也出现过。用户明确反馈电视上一直完整正常。17–25 等排查图不能被当作电视实际缺失元素的证明。尝试过的额外刷新、整层缓存与软件渲染均未保留。最终主页有完整真实截图。

SECURE/HDMI 保护画面没有绕过、截图拼接或替换。对应项目分别记为窗口或输入会话验证，不记为截图通过。Wi-Fi SSID、真实投屏、固件下载、天线搜台、完整影音播放与音效均未执行；保留服务状态与实际功能验收分开记录。

机器可读核对结果：`evidence/final-summary.json`。精确修改清单：`changes.json`。
