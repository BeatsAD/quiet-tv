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
const urls = 'https://raw.githubusercontent.com/BeatsAD/quiet-tv/main/docs/AGENT_INSTALL.zh-CN.md\nhttps://github.com/BeatsAD/quiet-tv/blob/main/docs/INSTALL.zh-CN.md\nhttps://github.com/BeatsAD/quiet-tv/blob/main/docs/COMPATIBILITY.md';
const discovery = `请自动发现电脑当前局域网内的目标电视：先检查现有 ADB 连接与 ADB mDNS 服务，必要时仅在当前局域网扫描常用 ADB 连接端口，不跨网段或进行全端口扫描。
结合所选品牌及可读取的型号识别电视；发现多个候选或无法确认目标时，列出候选让我选择，不批量安装或修改设备。
未发现目标时，再指导我在电视上查看网络地址、打开调试或完成无线调试配对。配对端口与连接端口分别核对，电视上的授权或配对码由我现场提供，不绕过授权。
连接后核对设备身份，后续所有 ADB 命令显式指定同一目标。`;
function update() {
  $('#install-prompt').textContent = `请在电脑上执行命令，帮我安装 Quiet TV。
目标：先安装并试用 Quiet TV，暂不修改默认桌面。
电视品牌：${brands[brand][0]}

请先完整阅读安装与兼容说明：
${urls}

从官方 Releases（含预发布／测试版）选择适配系统的最新主程序，当前推荐 1.1.2，更新版本以发布列表为准。安装后核对实际版本与目标一致；无法确认版本或下载失败时先告诉我，不改装历史包。

${discovery}

确认目标设备、系统和当前用户，保存原桌面恢复记录。按系统档位选择已发布 APK，核对 SHA-256、包名、架构、签名和覆盖升级条件。
首次打开选择“暂不设置，临时使用”，引导我验证遥控器、应用和设置入口。
试用确认可用后，询问我是否将 Quiet TV 设为默认桌面；只有我同意后，才核对当前固件的 HOME 能力并尝试切换。切换后读回结果，等待我用实体遥控器验证；固件受限或我选择不切换时保留原默认桌面。
保留原桌面，不禁用、不卸载、不清数据，不处理无关系统应用。电视授权时提醒我，重启测试先单独确认。
分别报告安装校验、默认 HOME、实体 HOME、重启及待机唤醒结果，未验证请标明，并提供本机恢复方法。`;
  $('#restore-prompt').textContent = `请在电脑上执行命令，帮我恢复原来的电视桌面。
电视品牌：${brands[brand][0]}

${discovery}

先读取现状和安装时保存的本机恢复记录，核对目标设备、当前用户、原桌面组件和原始启用状态。
优先使用系统或 Quiet TV 的恢复入口，仅恢复有记录的变更。保留 Quiet TV 和数据，不操作无关应用。
需要电视端操作时逐步提示，完成后读回 HOME，等待我用实体遥控器验证。
记录缺失、连接或权限受限时说明原因，不猜测原值、不套用其他电视的命令。`;
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
