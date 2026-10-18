# IMG_9608 口播精剪预览

原片约 74.29 秒，初剪约 34.87 秒，1080 × 1920、60 fps。保留原声、原速和画面顺序。

- `Edited`：11 个可独立调整的剪辑片段。
- `Original`：原片对照。
- 删除重复的桌面替换介绍、重复的底层修改说明、壁纸介绍中的反复起句，以及多处长停顿。
- 保留设置、壁纸、应用隐藏与删除、飞牛展示、适用人群及征求建议的收尾。
- 剪切时画面和声音同步，不替换配音。
- 已加入 17 句中文字幕，使用白字和半透明深色底，位于画面下方。字幕与视频绑定，调整片段会一起移动。
- `edit/subtitles.srt` 为当前精剪版的独立字幕文件；后续在 Studio 修改字幕后，需要重新同步该文件。
- 字幕组件来自 [Remotion Basic Captions](https://www.remotion.dev/elements/captions/basic-captions)。

## 打开预览

```sh
npm ci
npx remotion studio --no-open
```

打开终端打印的本地地址并选择 `Edited`。点击播放试听；在时间线上选择蓝色片段，可以调整起点、长度和排序。`Original` 可用于对照。

本机素材已复制到 `public/source.mov`，源文件为 `/Users/changwenbin/Downloads/IMG_9608.MOV`。素材不进入 Git；其他电脑运行时需要自行放入同一录制文件。

`src/Composition.tsx` 为可编辑时间线；`edit/timeline.json` 记录本次初剪的源片区间、输出位置和剪辑原因。Studio 后续手动调整以 TSX 为准。

## 本次检查

- ESLint 与 TypeScript 检查通过。
- 使用本地 Whisper 转写辅助剪辑，并复核剪辑音轨，确认保留的主要语句和中段口头修正的衔接。
- 在 Remotion Studio 检查竖屏方向、时间线和实际播放；预览未显示错误。
- 自动转写不能代替对细微字音、节奏的人工试听，预览供最终审看。

按用户选择仅提供可编辑预览，未导出 MP4。
