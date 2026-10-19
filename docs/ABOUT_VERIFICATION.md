# 关于功能验证记录

2026-10-19。本次仅验证本地构建和独立 Android TV API 33 模拟器，不代表真实电视验收或公开发布。

## 已完成

| 项目 | 证据与结果 |
| --- | --- |
| 视觉与布局 | 关于区直接从确认原型提取；HTML 主区、运行脚本、二维码原图与原型一致。目视核对单屏、Q 字电视图标、间距、分隔线和白色焦点框。操作系统的中文字形回退可能存在差异，不宣称跨系统逐像素一致。 |
| 版本信息 | 实际安装包为 1.1.0／构建 8／Debug；名称、版本名、构建号与 PackageManager 读值一致。原型预览的构建 5 仍只是设计样例。 |
| 首页入口 | 第四个胶囊入口及 ⓘ 图标正常；确定进入关于；退出后恢复第四个入口，而非常用应用。 |
| 遥控器 | 初始仓库焦点、仓库→邮箱→MIT、MIT↔上游、左侧返回按钮及对应上方向均通过。 |
| 离线许可 | 完整 MIT、第三方声明、字体及依赖许可随 APK 打包；方向键滚动、无横向溢出、返回恢复 MIT 焦点通过。 |
| 外部链接 | 系统 Intent 分发由 instrumentation 拦截核对，没有实际打开 GitHub、提交内容或发送邮件。通过 ActivityNotFoundException 错误注入验证无可用应用时页面与原焦点保留。 |
| 离线资源 | 页面网络加载、文件访问、content 访问均关闭；不使用 JavaScript 原生桥。最终包不包含评审工具条、模拟遥控器或设置演示页。 |
| 分辨率 | 1920×1080、1280×720 下单屏不溢出，功能检查通过；测试专用模拟器已恢复原始尺寸。 |
| 二维码 | 两种尺寸的截图均解码出三个 QR：仓库地址正确，微信／公众号内容与源图一致。此软件解码结果不替代电视屏幕与手机的实际扫码距离验收。 |
| 构建与 lint | `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` 通过；lint 为 0 errors／21 warnings。 |

## 复现

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug

adb -s emulator-5564 shell am instrument -w -e scenario about \
  home.quiet.tv.test/home.quiet.tv.ManagementInstrumentation
```

测试场景强制要求模拟器硬件，不会在真实电视上执行。专用 AVD 名称为 `quiet-about-api33`；没有更改真实电视／平板的安装包、默认桌面、应用状态或数据。

本地证据在 `output/about-validation/`：`build-final.txt`、`checks-1080.txt`、`checks-720.txt`、`checks-final.txt`、`about-native.png`、`about-native-720.png`、`about-home.png`、`about-home-return.png`、`about-license.png`、`qr-1080.json`、`qr-720.json`、`installed-hash.json`。证据目录被 Git 忽略。

最终 APK 归档为 `dist/apk/quiet-tv_1.1.0_b8_debug.apk`，其不可变 JSON 收据记录 SHA-256。已对模拟器的实际安装 APK 逐字节核对；真实电视尚未安装此包。未推送或发布。

归档时发现构建 7 已被并行任务先行使用，因此本次递增为构建 8，既有安装包与收据均保留。
