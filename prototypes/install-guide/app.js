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
  const takeover = $('[name="install-goal"]:checked').value === 'takeover';
  const keep = $('#keep-adb');
  keep.disabled = !takeover;
  if (!takeover) keep.checked = false;
  const target = $('#tv-address').value.trim() || '未填写，请先让我确认目标电视';
  $('#install-prompt').textContent = releaseIndex ? `请通过我的电脑安装或更新 Quiet TV 最新适配版（含测试版）。
电视地址或 ADB 标识：${target}
安装目标：${takeover ? '接管桌面，系统 HOME 不适用时允许配置兼容守护；保留原桌面。' : '仅安装试用，保留当前桌面和守护选择。'}
保持 ADB：${takeover && keep.checked ? '要，开启适用设备的调试设置恢复。' : '不新增开启，保留已有选择。'}

请先完整读取官方说明，按统一安装入口执行：
${guideUrl}
亲自在电脑上运行命令并保存恢复记录；需要电视授权或实体按键时提醒我。已明确选择接管的必要配置直接完成，不重复询问；仅更新不重新开启已暂停守护。
最后原样贴出系统 HOME、无障碍配置，以及实体主页键后的焦点输出；失败时显示卡住的步骤、命令和原始错误。重启测试另行征得我同意。` : releaseMessage;
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
document.querySelectorAll('[name="install-goal"], #keep-adb, #tv-address').forEach(input => {
  input.addEventListener('input', update);
  input.addEventListener('change', update);
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
