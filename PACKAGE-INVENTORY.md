# 设备包分类（来自真机）

所有包完整列于 package-inventory.csv。分类表示用途，不表示自动禁用授权。

## 厂商诊断与分析（6）

- `com.qterics.da.product`：DeviceAgentProduct诊断配套
- `com.sony.dtv.da.service`：远程诊断服务（SessionStateProvider）
- `com.sony.dtv.sonybugreportsys`：索尼错误报告
- `com.sony.dtv.sonyloglevelsettingsys`：索尼日志设置
- `com.sony.dtv.usercontext`：使用情境数据提供器（用途边界不明，保留）
- `com.sonyericsson.idd.agent`：IDD诊断数据代理

## 厂商内容与推广（5）

- `com.dangbei.TVHomeLauncher`：索尼当贝桌面及推荐内容（新桌面验证后禁用）
- `com.sony.dtv.braviashow`：BRAVIA演示
- `com.sony.dtv.demosupport`：零售演示支持
- `com.sony.dtv.demosystemsupport`：系统演示支持
- `com.sony.dtv.multiscreendemo`：多屏演示

## 可选后台服务（14）

- `com.android.printspooler`：打印后台
- `com.google.android.tv.remote.service`：手机遥控
- `com.jd.smartservicetwo`：京东智能服务
- `com.sony.dtv.homenetwork`：家庭网络设置
- `com.sony.dtv.iot`：索尼IoT，依赖边界不明保留
- `com.sony.dtv.otaurlprovisioning`：OTA地址配置
- `com.sony.dtv.seconddispsetting`：手机遥控相关设置，非独立Miracast包
- `com.sony.dtv.systemupdate`：系统更新
- `com.sony.dtv.update`：更新组件
- `com.sony.dtv.upgradesetup`：升级设置
- `com.sony.dtv.voicesearch`：语音搜索
- `com.sony.huey.dlna.module`：DLNA渲染模块
- `com.sony.huey.dlna.renderersettings`：DLNA设置
- `com.sonyericsson.dlna`：DLNA媒体共享

## 预装应用与工具（12）

- `com.dangbeimarket`：当贝市场普通版
- `com.sony.dangbeimarket`：当贝市场索尼版
- `com.sony.dtv.appassistant`：应用助手
- `com.sony.dtv.ecodashboard`：节能仪表盘
- `com.sony.dtv.helpguidecontents`：使用指南
- `com.sony.dtv.livingfit`：健身
- `com.sony.dtv.mysony`：My Sony
- `com.sony.dtv.notificationcenter`：通知中心
- `com.sony.dtv.osat.music`：索尼音乐
- `com.sony.dtv.smarthelp`：智能帮助
- `com.sony.dtv.smartmediaapp`：索尼媒体播放器
- `com.sony.dtv.timers`：定时器

## 影音应用（7）

- `com.ktcp.tvvideo`：腾讯视频电视版
- `com.newtv.login`：NewTV账号（内容应用依赖）
- `com.newtv.sport`：NewTV体育
- `com.trim.tv`：飞牛TV
- `com.xiaodianshi.tv.yst`：云视听小电视
- `com.xunlei.downloadprovider.hd`：迅雷
- `top.yogiczy.slcs.tv`：天光云影

## 系统组件或用途不明：保留（172）

保留全部 172 个包，详见 CSV。包括 HDMI/AV/直播输入、CEC、画质音效、遥控、蓝牙、网络、安全、设置与媒体提供器。

未发现 PatchWall、独立 Chromecast 或独立 Miracast 应用包。Miracast 可能包含于索尼系统组件内，不猜测禁用其宿主。
用户选择已执行：保留固件更新、投屏、手机遥控；关闭语音搜索与京东智能服务。影音应用保留六个包，见 preferences.json。
原已禁用：com.sony.dtv.b2b.vendorprotocol、com.sony.dtv.b2b.softap。

## 最终状态

原有 216 个包全部仍安装，新增 Quiet TV 后共 217 个。原有 2 个禁用状态保留，本次新增禁用 21 个，总计禁用 23 个。CSV 包含每个包的前后状态与对应截图。

本次禁用：

- `com.sonyericsson.idd.agent`：IDD诊断数据代理
- `com.sony.dtv.sonybugreportsys`：索尼错误报告
- `com.sony.dtv.sonyloglevelsettingsys`：索尼日志设置
- `com.sony.dtv.da.service`：远程诊断服务（SessionStateProvider）
- `com.qterics.da.product`：DeviceAgentProduct诊断配套
- `com.sony.dtv.braviashow`：BRAVIA演示
- `com.sony.dtv.demosupport`：零售演示支持
- `com.sony.dtv.demosystemsupport`：系统演示支持
- `com.sony.dtv.multiscreendemo`：多屏演示
- `com.sony.dtv.voicesearch`：语音搜索
- `com.jd.smartservicetwo`：京东智能服务
- `com.ktcp.tvvideo`：腾讯视频电视版
- `com.newtv.sport`：NewTV体育
- `com.sony.dtv.osat.music`：索尼音乐
- `com.sony.dtv.smartmediaapp`：索尼媒体播放器
- `com.sony.dtv.timers`：定时器
- `com.sony.dtv.mysony`：My Sony
- `com.sony.dtv.livingfit`：健身
- `com.sony.dtv.smarthelp`：智能帮助
- `com.sony.dtv.helpguidecontents`：使用指南
- `com.dangbei.TVHomeLauncher`：索尼当贝桌面及推荐内容（新桌面验证后禁用）
