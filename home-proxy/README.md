# QuietTV助手 HOME 中转

可选独立应用，属于主程序的“保留原桌面中转”路线。默认主程序构建不生成或安装助手。需要 Android 5 及以上，不支持 Android 4.4；主程序标准形态与无 HOME 兼容形态共用协议。

```sh
./gradlew :app:assembleDebug :home-proxy:assembleDebug -PquietHomeProxy=true
# 小米／Redmi 兼容身份，主程序仍保留 home.quiet.tv：
./gradlew :app:assembleDebug :home-proxy:assembleDebug -PquietHomeProxy=true -PquietProxyApplicationId=com.ts.launcher
# 主程序不声明 HOME，但助手仍声明 HOME：
./gradlew :app:assembleDebug :home-proxy:assembleDebug -PquietHomeProxy=true -PquietHomeMode=relay
```

`quietApplicationId=com.coocaa.quiettv` 时两者一起按该目标构建，主程序需同时传 `quietMinSdk=26`。助手默认包名 `home.quiet.tv.proxy`，小米兼容构建显式传 `quietProxyApplicationId=com.ts.launcher`；两者共用实现，独立版本 `1.0.0`、构建号 `1`。正式使用需给助手和目标主程序配置同一个持久签名证书；Debug 两者仅供本地验证，不能代替正式签名、交付归档或厂商验收。构建不授权设备安装。助手不提供普通应用图标，不包含服务、联网、开机接收器或系统包操作。

小米／Redmi 的新代理配置按制造商或品牌选择 `com.ts.launcher`，不限制产品型号和固件版本；其他品牌使用默认助手。已选定的代理包名持久保存，旧版未记录包名的代理继续使用 `home.quiet.tv.proxy`，不在升级时切换系统 HOME。需要改用新身份时，先恢复系统原桌面，再重新配置。两个包名不代表两份助手需要同时安装。

`com.ts.launcher` 也是外部应用使用的身份：安装前确认没有其他来源的同名应用，不覆盖或卸载它；本项目只接受与主程序签名、协议和目标匹配的助手，包名一致本身不足以接管。主程序包名、配置与升级关系保持原样。此范围是所有小米／Redmi 机型的兼容尝试，受 Android 5+ 最低版本约束，不代表全机型或任意固件已验收。反馈依据为用户提供的小米 S85 Mini LED / HyperOS 3.0.106.0 改包名案例，尚无本助手在该机的验收记录。

自动配置先尝试主程序直接 HOME，明确失败后检查已安装助手的签名、控制提供者、协议及目标。准备原桌面回退后才能设置代理为 HOME，设置后读回真实组件；不把命令成功当成实际切换。助手缺失或已确认安全回退的拒绝才进入原主页识别中转，连接丢失/取消/状态未知停止。不会安装助手、试探多个厂商包名、覆盖其他签名应用或禁用原桌面。HOME 入口设计参考 [homekey-standin](https://github.com/fandejin/homekey-standin)，本模块独立实现签名控制、暂停和故障回退，不采用其直接启动目标/吞掉异常的方式。

主程序通过显式、签名保护的 ContentProvider 控制助手，递增序号阻止旧的启用指令覆盖暂停。助手独立保存原桌面，在未配置、暂停、目标丢失、版本/固件变化或未确认启动后回原桌面。正常 HOME 运行按本地状态转发，不重新做自动选配，不连接 ADB 或网络，也不受开机跳板的三分钟/单次窗口限制。前台回执在主程序获得焦点后发送，仅证明软件生命周期回调。

启动未确认时，5 秒内重复 HOME 去重；之后下一次 HOME 停止重试并回原桌面。主程序异步崩溃后不能承诺立即自动跳回。原桌面不可用时显示恢复页，提供系统设置入口，不发送会再次进入自己的通用 HOME 意图。暂停先写主程序 OFF，再等待助手 ORIGINAL 回执；回执失败保留待确认状态和主程序第二道暂停检查。恢复默认原桌面先暂停，再设置并读回，之后才可在系统中处理助手。卸载主程序前应优先恢复原桌面；助手仍可独立回退，但不能把卸载当成完整恢复流程。

固件可能拒绝此助手的安装或 HOME 设置。无 HOME 主程序并不保证绕过限制，实体 HOME、重启、断电开机和待机唤醒必须分别在目标电视验证。厂商开机跳板继续默认关闭。本模块当前没有厂商真机验收记录。
