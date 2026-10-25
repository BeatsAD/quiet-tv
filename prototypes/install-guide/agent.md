# Quiet TV Agent 安装入口

公开完整说明由构建脚本根据操作规范与实际 Release 自动生成：
https://beatsad.github.io/quiet-tv/agent.md

最新版与真实下载地址：
https://beatsad.github.io/quiet-tv/latest.json

每次通过本机终端读取时自动追加本次实际时间的 `check` 参数并禁用缓存，不需要用户指定版本。按电视系统、架构及覆盖升级条件选择最新适配主程序，包含测试版；读取失败时报告具体原因，不使用历史缓存改装旧版。

完整操作规范的唯一源码为 [Agent 安装与恢复说明](../../docs/AGENT_INSTALL.zh-CN.md)。发布构建会将该规范与实时发布信息合成公开 `agent.md`，本文件仅保留稳定入口，不重复维护操作规则或版本号。
