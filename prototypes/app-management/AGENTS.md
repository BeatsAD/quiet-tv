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
- 文案只保留必要指引、状态与操作后果；删除重复宣传、免责和说明性文字，禁用／卸载影响集中在确认弹窗中。
