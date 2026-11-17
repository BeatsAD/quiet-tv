# 按需组件

QuietTV 2.0 基础 APK 保留天气渲染代码及必要的组件入口，不包含天气大体积纹理或 Tailscale 原生核心。下载不会切换壁纸、开启节点、登录或绑定。

| 入口 | 动态天气壁纸组件 | Tailscale 组网 |
| --- | --- | --- |
| 电视设置 | 桌面显示 → 桌面背景 → 动态天气壁纸，下载到当前电视 | 工具 → Tailscale 组网，下载电视核心后手动开启节点 |
| 手机助手工具 → 通用设置 | 通过已核对的 ADB 连接下载到目标电视 | 手机与电视核心分别下载 |
| 扫码设置网页通用设置 | 通过当前有效会话下载到电视 | 只提示打开手机助手，不提供组网下载、登录或绑定接口 |

桌面显示选择“随天气变化”而资源缺失时，在当前页打开下载管理；关闭后返回壁纸切换项，保留当前选择与背景。下载后仍需主动选择天气。旧版本已选天气但资源不存在时保留偏好并显示静态背景，下载后按原偏好恢复。天气沿用 API21、GLES3 和内存条件；组网要求电视和手机均为 ARM64 应用进程、Android 8/API26 及以上。

## 构建

基础 APK 沿用现有构建命令，无需 Go/NDK。手机仓库独立构建和签名。以下命令在电视仓库执行，SDK 需包含 NDK 30.0.16248370，Go 固定 1.27.3，依赖由 `remote-core/go.mod` 和 `go.sum` 固定。

```sh
ANDROID_SDK_ROOT=<SDK路径> sh scripts/build_remote_core.sh
python3 scripts/build_components.py pack --kind weather --target tv --version 1.0.0 --release-tag weather-v1.0.0 --output <本地输出目录>
python3 scripts/build_components.py pack --kind tailscale --target tv --version <组件版本> --core output/components-core/arm64-v8a/libquietremote.so --output <本地输出目录>
python3 scripts/build_components.py index --artifact <电视天气.json> --revision <天气递增修订号> --key <外部签名私钥路径> --output <天气输出目录>/QuietTV_components.json
python3 scripts/build_components.py index --artifact <电视核心.json> --artifact <手机仓库生成的手机核心.json> --revision <组网递增修订号> --key <外部签名私钥路径> --output <组网输出目录>/QuietTV_components.json
```

`pack` 独占创建 ZIP、逐文件清单和 SHA-256，保留历史产物。手机核心由手机仓库自身脚本生成，这里只把公开清单汇入目录，不建立源码或构建依赖。组件版本独立于 APK 软件版本。

天气目录地址在电视端 `ComponentRepository.WEATHER_INDEX`，目标为独立仓库 [quiet-tv-components](https://github.com/BeatsAD/quiet-tv-components) 的 `weather-v1.0.0` Release 中的 `QuietTV_components.json`，仅含电视天气资源。主程序仓库继续管理 APK；组网仍使用 `ComponentRepository.INDEX` 指定的原仓库 `v2.0.0` 地址，两者分别记录清单修订号。

首次发布必须先完成 Release 审核与授权，工具不会自行上传。未发布时界面提示尚未发布。迁移后的电视测试包读取新仓库；原仓库的历史天气 Release 保留，旧包继续使用原地址。客户端同时允许两个明确仓库地址，恢复旧签名回执和已有资源；沿用原信任根，天气清单修订号递增为 5。手机通过电视 Provider 发起天气下载，无需改动手机端组网地址。后续组件内容升级使用新的组件版本和标签，并同步调整电视下载入口。

## 运行约定

- 清单以 SHA256withRSA 签名，APK 内置公开信任根，私钥在仓库外。只接受明确的组件、目标、hostApi1、版本、ABI、系统、来源、大小及逐文件 SHA-256；目录修订拒绝回退。
- 完整下载后校验归档，再按签名清单有限解压，拒绝越界路径、重复或额外文件、损坏和展开超限。写入独立只读版本目录，最后原子更新回执；失败或取消不替换可用版本，不删除旧资料。
- 电视天气组件弹窗左侧提供“卸载”，扫码网页和手机助手也可发起。未下载时禁用；下载或校验中先取消并等待停止。确认卸载后，正在使用的天气壁纸切回可用静态背景，位置、来源、昼夜设置及其他组件保留。
- 卸载先阻止新资源引用，等待桌面和预览渲染器释放，再移除天气安装凭据、资源版本和下载缓存。中断或部分失败保持不可用并允许重试；重新下载沿用签名及修订防回退。只有有明确天气归属的文件会移除，历史无归属 `pending-UUID` 目录保留。
- 页面关闭不取消下载；进程被系统终止后下次提示重新下载，不承诺后台常驻或断点续传。用户可取消和重新下载。
- 天气渲染持有同一资源版本，避免更新期间混用文件。原生核心在首次明确操作前从已验证私有路径加载；加载中的核心更新后提示重启生效。
- 手机通过已核对包签名、UID、版本、设备和用户的 ADB 会话控制电视组件。Provider 只允许 shell、root 或应用自身调用，不接收任意下载 URL、文件路径或桥接端口。网页组件写接口只接受天气的开始、取消和卸载。
- 节点只由用户开启，前台通知可暂停，不自动开机入网。节点状态、绑定凭据和证书私钥采用 Keystore 包装密钥及认证加密保护，不写入诊断日志。
- 短时绑定二维码要求手机签名证明和电视明确批准。撤销绑定会关闭相应活动通道。通道只转发经协议识别的本机 adbd，手机仍独立完成 ADB 授权。

许可随 APK 离线提供，见[第三方声明](../THIRD_PARTY_NOTICES.md)。宿主所需资源发生变化时，须同步生成和验证新的组件版本。

通用设置中的天气分组保留“天气位置、刷新天气、来源与服务”三项。自动定位与手动选择归入天气位置；备用来源与隐私说明归入来源与服务。桌面天气与天气壁纸继续共用位置和来源配置。
