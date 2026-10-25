# 应用内更新

Quiet TV 在首页前台显示后检查已签名的公开版本清单，成功检查间隔为 24 小时；自动失败间隔为 30 分钟。待机、关机和应用未运行时不唤醒检查。“检查更新”可主动立即检查。

有匹配更新时，管理页顶部“关于”显示红底白描边的“新版本”角标；关于页检查按钮显示红点。查看说明不清除提示，确认实际更新版本后清除。无更新或检查失败只在弹窗显示，不增加关于页常驻辅助文字。

用户选择下载后，依次尝试 ghfast.top、gh-proxy.com、gh.llkk.cc 与 GitHub 原源，上次完整下载并验真成功的源优先。连接和首字节共用 8 秒预算；断流 8 秒或连续三个 5 秒窗口低于 100 KiB/s 时切源。完成度达到 95% 或剩余不超过 512 KiB 时，只因中断或错误换源。最后一个仍在传输的源允许继续慢速下载。

只有符合 Content-Range 偏移与总大小的 206 才续传；200 从头写入。同一下载任务固定目标文件，最终检查完整 SHA-256、实际 APK 包名、版本、SDK、ABI 和签名关系。下载不使用电视 ADB；应用内安装由用户通过系统安装器确认。首次需要授权时，点击“继续”后由系统安装器展示来源授权提示，用户在系统设置中允许后返回确认安装；应用不自行改变安装权限。

更新保留当前包名、系统兼容档、HOME 声明形态和渠道。64 位处理器不等于系统支持 ARM64。构建号必须更高，软件版本必须更新；不把同版本其他架构的更高构建号当作更新。缺少匹配包、签名不兼容或电视限制安装时，不卸载、不清数据、不强制降级，按安装指引处理。

## 公开输入与首次接入

生产输入为 `updates/updates.json` 和 `app/src/main/assets/updates/public-key.der`。Pages 工作流复制原字节清单到站点根目录；Raw 地址指向仓库同一份文件。Pages 域名不通过 GitHub 代理前缀包装，Raw 和 Releases 文件才使用代理。

清单包含 keyId、Base64 payload 和 SHA256withRSA 签名。应用验签后读取 payload，拒绝低于已缓存 revision 的清单。代理没有发布签名权；APK 不包含私钥、GitHub Token 或代理凭据。没有已批准的新包时，初始化清单的 releases 为空，不发布虚构版本或旧包升级提示。

第一批包含更新能力的 APK 仍需通过已有安装方式安装一次。合入代码不等于清单已上线；Pages、Raw 和 Release 的公开部署必须分别确认。

## 本地生成清单

### 外部 Agent 与安装页的最新版入口

安装站点另提供 `https://beatsad.github.io/quiet-tv/latest.json` 与 `agent.md`。`scripts/build_install_guide.py` 在部署时实时读取 GitHub 的完整公开 Release 列表，包含测试版、排除草稿，按数字软件版本排序，只保留真实上传的主程序 APK 及其下载地址、大小和 SHA-256。历史档位保留为适配候选；全局最新软件版本不代表适配每台电视。全部撤回后索引的 `latestVersion` 为 `null`、`releases` 为空，停止提供安装请求。索引不推断 APK 的实际构建号、签名或架构，也不重签、修改电视端 `updates.json`。

Release 发布、编辑、转正式或撤回后，工作流自动请求从 `main` 重新部署安装站点；此路径兼容 Pages 仅允许 `main` 部署的环境规则。更新规范、站点脚本或签名清单合入 `main` 时同样自动部署。普通新版本不需要修改网页、提示词或固定链接。发布操作须保留原有审核授权；用仓库 `GITHUB_TOKEN` 发布 Release 不会触发普通工作流事件，发布者使用该方式时须显式执行 `gh workflow run pages.yml --ref main --repo BeatsAD/quiet-tv`。自动部署完成前站点仍为上次成功版本；失败时检查 Pages 工作流并修复后重跑，不把失败视为上线成功。

安装页打开和复制时用 `no-store` 与自动时间参数读取索引；复制内容只保留一个官方操作说明地址，不写入当前版本号、特定发布页或复制时的时间戳，保存后也不会锁定当时版本。实时查询、选包、校验与防缓存规则放在操作说明中，由 Agent 每次执行时处理，无需用户维护版本号。第三方工具不执行读取、网络不可达或站点部署失败时仍须报告失败，不能承诺任何网络与任意工具下都实时可用。

本地检查（仅生成文件，不部署）：

```sh
python3 scripts/build_install_guide.py --output output/install-guide
```

`--releases /绝对路径/releases.json` 可改用保存的 GitHub API 响应进行离线检查。生成失败不会清理或替换已上线站点；Pages 上传与部署仅发生在构建成功后。

### 电视端签名清单

从不可变 APK 归档及其 JSON 生成。版本说明文件仅保留本地，例如：

```json
{
  "versionName": "2.0.0",
  "channel": "test",
  "features": ["本版实际新增的功能"],
  "fixes": ["本版实际修复的问题"],
  "artifacts": [
    {"receipt": "../dist/apk/本地归档记录.json", "url": "https://github.com/BeatsAD/quiet-tv/releases/download/v2.0.0/已审核的实际文件名.apk"}
  ]
}
```

示例版本不预占交付版号。`receipt` 相对说明文件解析。脚本重新核对 APK 字节、版本、架构、签名和 HOME 形态，同版本要求同一源码提交。

```sh
python3 scripts/build_update_manifest.py --release /绝对路径/本地版本说明.json --revision 2 --key /仓库外/持久私钥.pem --output updates/updates.json
```

可通过 `python3 scripts/build_update_manifest.py --verify updates/updates.json` 只用公开密钥验证清单；现有 Validate 会执行此检查，不依赖私钥或本地测试模块。

撤回版本时，使用更高 revision 的 `--empty` 清单。脚本不联网查询、不上传、不创建标签。持久私钥应备份在仓库外；APK 的公开密钥必须与签名密钥匹配。新克隆可以独立构建和验签，无需私钥；发布者生成下一份清单才需要私钥。不要替换现有公钥或用临时密钥发布。

发布前先按 [版本规则](VERSIONING.md) 和 [工作区指令](../AGENTS.md) 备齐、审核真实的更新功能、修复文案及 APK 材料。附件上传并校验可下载后再发布对应签名清单。历史 Release 与 APK 不覆盖。

## 验证范围

自动规则和受控传输测试不能代替真实代理完整下载、电视网络、未知来源授权、实体遥控器及升级保留验证。实际系统安装器样式与能力由电视固件决定。应用被替换时可能退出；再次启动会读取实际版本，不以“安装器已打开”判断更新完成。
