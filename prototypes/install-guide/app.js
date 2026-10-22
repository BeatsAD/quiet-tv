'use strict';
const $ = selector => document.querySelector(selector);
const brands = {
  sony: ['索尼', '设置 → 设备偏好设置 → 关于 → 内部版本号', '连续按确定约 7 次，在开发者选项中打开 USB / ADB 调试。', '通用入口参考；网络调试需按具体固件确认。'],
  xiaomi: ['小米 / Redmi', '设置 → 关于 → 产品型号', '连续按确定进入开发者模式，再到「账号与安全」查看 ADB 调试。', '部分小米固件路径；其他型号需单独核对。'],
  tcl: ['TCL / 雷鸟', '设置 → 关于 → 本机信息', '查看开发者选项、ADB 或无线调试入口，由 Agent 按型号核对。', '部分固件限制安装或配对；不能按品牌推定兼容。'],
  coocaa: ['创维 / 酷开', '设置 → 关于 → 系统信息', '查看系统版本与调试入口，找不到时由 Agent 按型号核对。', '部分固件接管 HOME；默认桌面及重启行为待验证。'],
  other: ['其他 Android 电视', '设置 → 系统 / 设备偏好设置 → 关于 → 内部版本号', '连续按内部版本号约 7 次，在开发者选项中开启可用的调试方式。', '通用 Android TV 路径；非 Android 系统不适用。']
};
let brand = 'sony';
let toastTimer;
const urls = 'https://github.com/BeatsAD/quiet-tv/blob/main/docs/AGENT_INSTALL.zh-CN.md\n原始文本备用地址：\nhttps://raw.githubusercontent.com/BeatsAD/quiet-tv/main/docs/AGENT_INSTALL.zh-CN.md';
const discovery = `请自动发现电脑当前局域网内的目标电视：先检查现有 ADB 连接与 ADB mDNS 服务，必要时仅在当前局域网扫描常用 ADB 连接端口，不跨网段或进行全端口扫描。
结合所选品牌及可读取的型号识别电视；无论发现一台还是多台，都列出候选让我确认目标，确认后再安装或修改设备，不批量操作。
未发现目标时，再指导我在电视上查看网络地址、打开调试或完成无线调试配对。配对端口与连接端口分别核对，电视上的授权或配对码由我现场提供，不绕过授权。
连接后核对设备身份，后续所有 ADB 命令显式指定同一目标。`;
const failure = `任何步骤失败时，立即在当前 AI 对话显示失败步骤、相关脱敏命令、本机退出码及可取得的远端退出状态、原始错误输出、已确认原因与待确认项、当前修改状态和下一步；未取得的状态写“未知”，不只说“失败”或只写入日志。
命令成功但核对不符时报告“验证失败”；超时、断连或证据不足时报告“结果未确认”。停止依赖失败结果的后续修改，继续独立的只读检查，不自动重复修改或通过卸载、清数据解决。`;
function update() {
  $('#install-prompt').textContent = `请在电脑上执行命令，帮我安装或更新 Quiet TV。
目标：先判断是否已安装；已有安装只更新，首次安装先试用，再由我选择后续设置。
电视品牌：${brands[brand][0]}

请先完整阅读并遵循 Agent 安装说明，以及其中链接的安装和兼容文档：
${urls}

${discovery}

读取品牌、完整型号、Android/API、架构、固件、当前用户、安装状态和 HOME，分别检查 ADB、桌面切换、禁用及恢复能力，不只按品牌或版本推定支持。保存本机恢复记录，保留已有原桌面记录。
实时查询 https://github.com/BeatsAD/quiet-tv/releases ，按系统档位选择适用的已发布 APK，不按最大构建号选包。旧文档与下载信息冲突时，以对应 Release 正文及实际附件为准，并核对真实 APK 的版本、包名、最低系统、架构、签名和 SHA-256；使用实际 APK 下载文件名对应的 .apk.sha256，不自动安装实验助手。
已有安装：仅在同包名、签名匹配且构建号递增时保留数据覆盖升级，保持默认桌面、应用启禁状态和设置，不重新精简；相同发布文件无需重装。签名冲突、降级、同构建号不同文件或特殊安装状态时说明差异，不自动修复。安装后拉回已安装 APK 核对版本与哈希，请我确认原配置仍在。
首次安装：先试用并验证遥控器、应用、设置和信号源，再让我选择是否设为默认桌面。切换后读回 HOME，等待我用实体遥控器确认；固件受限时保留原桌面并说明试用路径。随后让我选择是否处理原桌面和预装应用；默认保留，只有符合对应发布包的固件规则、身份与恢复条件，且经我明确选择的项目才逐项禁用并读回，保留安装和数据。执行前核实界面预选项，未知系统包只读，不禁用原桌面试错，不卸载原桌面，不处理无关系统应用。
${failure}
需要电视授权时提示我；重启和电源操作先确认。分别报告安装校验、系统 HOME、实体 HOME、重启、冷启动、待机唤醒、可选应用管理和恢复方法，未执行请注明。`;
  $('#restore-prompt').textContent = `请在电脑上执行命令，帮我恢复原来的电视桌面。
电视品牌：${brands[brand][0]}

请先完整阅读并遵循：
${urls}

${discovery}

先读取现状和安装时保存的本机恢复记录，核对目标设备、当前用户、原桌面组件和原始启用状态。
优先使用系统或 Quiet TV 的恢复入口，仅恢复有记录的变更；原桌面未禁用就不执行启用。保留 Quiet TV 和数据，不操作无关应用。
需要电视端操作时逐步提示，完成后读回 HOME，等待我用实体遥控器验证。
记录缺失、连接或权限受限时说明原因，不猜测原值、不套用其他电视的命令。
${failure}
分别报告系统 HOME、实体 HOME 和未验证的重启、冷启动及待机结果；电源操作先确认。`;
}
function wireTabs(selector, activate) {
  const tabs = [...document.querySelectorAll(selector)];
  tabs.forEach((tab, index) => {
    tab.addEventListener('click', () => {
      tabs.forEach(item => { item.setAttribute('aria-selected', String(item === tab)); item.tabIndex = item === tab ? 0 : -1; });
      activate(tab);
    });
    tab.addEventListener('keydown', event => {
      let next;
      if (event.key === 'ArrowRight') next = (index + 1) % tabs.length;
      if (event.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length;
      if (event.key === 'Home') next = 0;
      if (event.key === 'End') next = tabs.length - 1;
      if (next !== undefined) { event.preventDefault(); tabs[next].focus(); tabs[next].click(); }
    });
  });
}
wireTabs('[data-brand]', tab => {
  brand = tab.dataset.brand;
  const [, route, detail, note] = brands[brand];
  $('#brand-route').textContent = route;
  $('#brand-detail').textContent = detail;
  $('#brand-note').textContent = note;
  $('#brand-panel').setAttribute('aria-labelledby', tab.id);
  update();
});
wireTabs('[data-method]', tab => {
  ['agent', 'manual', 'restore'].forEach(method => $('#' + method + '-panel').hidden = tab.dataset.method !== method);
});
function toast(message) {
  clearTimeout(toastTimer);
  $('#toast').textContent = message;
  $('#toast').hidden = false;
  toastTimer = setTimeout(() => $('#toast').hidden = true, 3500);
}
document.querySelectorAll('[data-copy]').forEach(button => button.addEventListener('click', async () => {
  const pre = button.dataset.copy === 'install' ? $('#install-prompt') : $('#restore-prompt');
  try {
    await navigator.clipboard.writeText(pre.textContent);
    toast('已复制，发送给电脑上的 Agent 即可。');
  } catch {
    previewRequest(button.dataset.copy);
    const selection = window.getSelection();
    const range = document.createRange();
    range.selectNodeContents(pre);
    selection.removeAllRanges();
    selection.addRange(range);
    pre.focus();
    toast('已选中文字，请手动复制。');
  }
}));
function previewRequest(mode) {
  const restore = mode === 'restore';
  $('#request-dialog-title').textContent = restore ? '恢复请求' : '安装请求';
  $('#install-prompt').hidden = restore;
  $('#restore-prompt').hidden = !restore;
  $('#preview-copy').dataset.copy = restore ? 'restore' : 'install';
  if (!$('#request-dialog').open) $('#request-dialog').showModal();
}
document.querySelectorAll('[data-preview]').forEach(button => button.addEventListener('click', () => previewRequest(button.dataset.preview)));
$('#close-request').addEventListener('click', () => $('#request-dialog').close());
$('#show-faq').addEventListener('click', () => $('#faq-dialog').showModal());
$('#close-faq').addEventListener('click', () => $('#faq-dialog').close());
$('#faq-dialog').addEventListener('click', event => {
  if (event.target !== $('#faq-dialog')) return;
  const box = event.target.getBoundingClientRect();
  if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) event.target.close();
});
update();
