'use strict';
const $ = selector => document.querySelector(selector);
const brands = {
  sony: ['索尼', '设置 → 设备偏好设置 → 关于 → 内部版本号', '连续按确定约 7 次，在开发者选项中打开 USB / ADB 调试。', '通用入口参考；网络调试需按具体固件确认。'],
  xiaomi: ['小米 / Redmi', '设置 → 关于 → 产品型号', '连续按确定进入开发者模式，再到「账号与安全」查看 ADB 调试。', '部分小米固件路径；其他型号需单独核对。'],
  tcl: ['TCL / 雷鸟', '设置 → 关于 → 本机信息', '查看开发者选项、ADB 或无线调试入口；安装被拒时由 Agent 按错误核对兼容方案。', '暂无固件号也可排查；兼容方案需本机验证。'],
  coocaa: ['创维 / 酷开', '设置 → 关于 → 系统信息', '查看系统版本与调试入口，找不到时由 Agent 按型号核对。', '部分固件接管 HOME；默认桌面及重启行为待验证。'],
  other: ['其他 Android 电视', '设置 → 系统 / 设备偏好设置 → 关于 → 内部版本号', '连续按内部版本号约 7 次，在开发者选项中开启可用的调试方式。', '通用 Android TV 路径；非 Android 系统不适用。']
};
let brand = 'sony';
let toastTimer;
let releaseIndex;
let releaseMessage = '正在获取最新安装信息…';
const guideUrl = 'https://raw.githubusercontent.com/BeatsAD/quiet-tv/main/docs/AGENT_INSTALL.zh-CN.md';
function update() {
  $('#install-prompt').textContent = releaseIndex ? `请通过我的电脑，按照官方说明安装或更新 Quiet TV 最新适配版（含测试版）：
${guideUrl}

先确认目标电视，保留已有数据。首次安装先试用，设置默认桌面前征求我同意；遇到问题直接在对话中说明。` : releaseMessage;
  $('#restore-prompt').textContent = `请通过我的电脑，帮我恢复电视原来的桌面。

请先完整读取并遵循操作说明：
${guideUrl}

确认目标电视后，根据本机记录恢复，保留 Quiet TV 和应用数据。
失败时显示具体错误、当前状态和下一步；记录缺失或无法读取说明时先告诉我，不猜测执行。`;
}
async function refreshRelease() {
  const button = $('[data-copy="install"]');
  button.disabled = true;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10000);
  try {
    releaseIndex = null;
    releaseMessage = '正在获取最新安装信息…';
    update();
    const stamp = Date.now();
    const response = await fetch('latest.json?check=' + stamp, { cache: 'no-store', signal: controller.signal });
    if (!response.ok) throw new Error('版本索引 HTTP ' + response.status);
    const data = await response.json();
    if (data.schemaVersion !== 1 || !/^\d+\.\d+\.\d+$/.test(data.latestVersion)
        || !Array.isArray(data.releases) || data.releases[0]?.versionName !== data.latestVersion
        || !data.releases[0].url.startsWith('https://github.com/BeatsAD/quiet-tv/releases/tag/')) {
      throw new Error('版本索引无效');
    }
    releaseIndex = data;
    update();
    $('#release-link').href = data.releases[0].url;
  } catch (error) {
    releaseIndex = null;
    releaseMessage = '暂时无法获取最新安装信息。点击“复制安装请求”重试。';
    update();
    $('#release-link').href = 'https://github.com/BeatsAD/quiet-tv/releases';
    throw error;
  } finally {
    clearTimeout(timeout);
    button.disabled = false;
  }
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
  const refresh = button.dataset.copy === 'install' ? refreshRelease() : null;
  try {
    if (refresh && typeof ClipboardItem !== 'undefined' && navigator.clipboard?.write) {
      // Start the write during the click; WebKit permits the data to arrive later.
      const content = refresh.then(() => new Blob([pre.textContent], { type: 'text/plain' }));
      content.catch(() => {}); // The clipboard may reject before consuming its data.
      await navigator.clipboard.write([new ClipboardItem({ 'text/plain': content })]);
    } else {
      if (refresh) await refresh;
      await navigator.clipboard.writeText(pre.textContent);
    }
    toast('已复制，发送给电脑上的 Agent 即可。');
  } catch {
    if (refresh) {
      try {
        await refresh;
      } catch {
        toast('暂时无法获取最新版，请稍后重试。');
        return;
      }
    }
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
refreshRelease().catch(() => {});
