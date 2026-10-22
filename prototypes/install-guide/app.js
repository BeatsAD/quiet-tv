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
const guideUrl = 'https://raw.githubusercontent.com/BeatsAD/quiet-tv/main/docs/AGENT_INSTALL.zh-CN.md';
function update() {
  $('#install-prompt').textContent = `请在我的电脑上安装或更新 Quiet TV。
电视品牌：${brands[brand][0]}

请先读取并遵循操作说明：
${guideUrl}

先发现电视并让我确认目标。已安装则保留数据更新；首次安装先试用，再让我选择默认桌面和应用管理。
需要授权或选择时提示我；失败时显示具体错误、当前状态和下一步。
无法读取说明时先告诉我，不猜测执行。`;
  $('#restore-prompt').textContent = `请在我的电脑上帮我恢复原来的电视桌面。
电视品牌：${brands[brand][0]}

请先读取并遵循操作说明：
${guideUrl}

确认目标电视后，根据本机记录恢复，保留 Quiet TV 和应用数据。
失败时显示具体错误、当前状态和下一步；记录缺失或无法读取说明时先告诉我，不猜测执行。`;
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
    if (button.dataset.copy === 'restore') previewRequest();
    const selection = window.getSelection();
    const range = document.createRange();
    range.selectNodeContents(pre);
    selection.removeAllRanges();
    selection.addRange(range);
    pre.focus();
    toast('已选中文字，请手动复制。');
  }
}));
function previewRequest() {
  if (!$('#request-dialog').open) $('#request-dialog').showModal();
}
document.querySelectorAll('[data-preview]').forEach(button => button.addEventListener('click', previewRequest));
$('#close-request').addEventListener('click', () => $('#request-dialog').close());
$('#show-faq').addEventListener('click', () => $('#faq-dialog').showModal());
$('#close-faq').addEventListener('click', () => $('#faq-dialog').close());
$('#faq-dialog').addEventListener('click', event => {
  if (event.target !== $('#faq-dialog')) return;
  const box = event.target.getBoundingClientRect();
  if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) event.target.close();
});
update();
