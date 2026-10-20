# Prototype Instructions

Run the local server yourself and open the preview in the browser available to this environment. Do not give the user server-start instructions when you can run it.

Before making substantial visual changes, use the Product Design plugin's `get-context` skill when the visual source is unclear or no longer matches the current goal. When the user gives durable prototype-specific design feedback, preferences, or decisions, record them in `AGENTS.md`.

When implementing from a selected generated mock, treat that image as the source of truth for layout, component anatomy, density, spacing, color, typography, visible content, and hierarchy.

Build app UI in `src/`. Keep `.openai/hosting.json`, `worker/index.js`, `scripts/prepare-sites-build.mjs`, and `tests/sites-worker.test.mjs` intact so the same local prototype can be handed to Sites. Before a Sites handoff, run `npm run build` and `npm run test:sites`; the build must leave `dist/client/index.html`, `dist/server/index.js`, and `dist/.openai/hosting.json`.

## 本次确认范围

- 独立的电视应用管理交互原型；确认前不修改 Android 原生界面。
- 延续现有首页：黑灰、静态半透明材质、白色焦点框、克制动效。
- 应用管理入口图标和文字作为整体居中；使用应用网格图标。
- 演示数据仅保存在网页中，不连接电视、不执行 ADB 或真实包管理操作。

## 首次设置原型（2026-10-18）

- 用户要求先设计可交互原型，沿用当前极简桌面风格，确认前不接入 Android 功能。
- 目标是减少默认桌面切换与自定义应用管理的操作；桌面切换优先，预装软件精简可跳过，系统管理按需授权。
- 保留当前壁纸、黑灰半透明面板、留白和白色遥控焦点框；设备情境切换与模拟 HOME／重启属于原型工具，不进入产品界面。
- 原型区分重启后的配置保留与管理连接状态，不承诺跨恢复出厂或所有固件升级永久生效。
- 文案只保留必要指引、状态与操作后果；删除重复宣传、免责和说明性文字。预设禁用影响放在操作按钮旁，单应用卸载保留确认弹窗。
- 用户已确认继续压缩流程：取消欢迎／先体验入口，默认桌面设置后直接进入推荐预设；标题直接说明操作，不使用宣传句。
- 推荐预设展示已调查的具体应用名称并默认勾选，可取消；连接授权使用当前页弹窗，成功后自动继续，不另设连接成功页。
- 不设置完成页；完成或跳过精简后直接进入桌面，以短暂“设置成功”提示反馈。单应用卸载仍保留数据后果确认。
- 点击“禁用 N 项并完成”即确认所选预设，不再增加清单确认页；首次连接后自动执行用户已选择的操作，取消连接则不执行。
- 用户已指定原桌面可选择禁用或卸载；第二屏同时提供保留，默认选可逆的禁用。卸载原桌面需单独确认数据后果。
- 必须先设 Quiet TV 为默认桌面，才允许禁用或卸载原桌面。恢复中心按原桌面状态先启用／恢复安装，再切回；恢复安装不恢复数据。

## 电视模拟演示（2026-10-19）

- 需求澄清：电视产品首次设置执行真实操作，只有右侧桌面预览使用通用示意，不能展示用户真实桌面。本目录网页仍仅用于离线原型评审，不作为产品设置入口。
- 电视直接复用当前网页原型，主界面全屏展示；评审情境与重置等工具收进“演示工具”，始终显示模拟标识与退出入口。
- 遥控器方向键必须覆盖所有可用按钮、原桌面选项、推荐卡片、弹窗关闭、折叠连接设置及输入框；折叠内隐藏控件和禁用控件不进入焦点路径。确定键激活，返回键关闭弹窗或返回上层，焦点不穿透弹窗。
