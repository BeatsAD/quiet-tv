const $ = (selector) => document.querySelector(selector);
const paths = {
  back: '<path d="m14 6-6 6 6 6M8 12h13"/>',
  close: '<path d="m6 6 12 12M6 18 18 6"/>',
  settings: '<path d="m9 3 1-2h4l1 2 3 2 3 1v4l-2 2v3l2 2-2 4-3-1-3 1-2 2-4-2v-3l-2-2-3-1v-4l3-1 2-2z" transform="translate(1 0) scale(.9)"/><circle cx="12" cy="12" r="3"/>',
  network: '<rect x="9" y="2" width="6" height="6"/><path d="M12 8v5M5 13h14M5 13v3M19 13v3"/><rect x="2" y="16" width="6" height="6"/><rect x="16" y="16" width="6" height="6"/>',
  input: '<path d="M6 3h12v8l-4 4v6h-4v-6l-4-4zM10 6v4M14 6v4"/>'
};
function icons(root = document) {
  root.querySelectorAll('[data-icon]').forEach(el => {
    el.innerHTML = `<svg viewBox="0 0 24 24" aria-hidden="true">${paths[el.dataset.icon]}</svg>`;
  });
}
icons();

let currentPage = 'about';
let lastFocus = $('#source-link');
let toastTimer;
function resize() {
  const full = document.fullscreenElement === $('#viewport');
  const available = full ? innerWidth : Math.min(innerWidth - (innerWidth < 900 ? 24 : 64), 1600);
  const scale = full ? Math.min(innerWidth / 1920, innerHeight / 1080) : available / 1920;
  $('#viewport').style.width = `${1920 * scale}px`;
  $('#viewport').style.height = `${1080 * scale}px`;
  $('#tv').style.transform = `scale(${scale})`;
}
addEventListener('resize', resize);
document.addEventListener('fullscreenchange', resize);
resize();
function showPage(page) {
  currentPage = page;
  $('#home').hidden = page !== 'home';
  $('#about').hidden = page !== 'about';
  document.querySelectorAll('[data-preview]').forEach(el => el.classList.toggle('selected', el.dataset.preview === page));
  (page === 'home' ? $('#entry-about') : $('#source-link')).focus();
}
document.querySelectorAll('[data-preview]').forEach(el => el.onclick = () => showPage(el.dataset.preview));
$('#entry-about').onclick = () => showPage('about');
$('#back-home').onclick = () => showPage('home');
$('#fullscreen').onclick = async () => {
  try {
    if (document.fullscreenElement) await document.exitFullscreen();
    else await $('#viewport').requestFullscreen();
    lastFocus.focus();
  } catch { toast('当前浏览器暂不支持全屏，请放大预览窗口'); }
};
function toast(message) {
  clearTimeout(toastTimer);
  $('#toast').textContent = message;
  $('#toast').hidden = false;
  toastTimer = setTimeout(() => $('#toast').hidden = true, 2600);
}
document.querySelectorAll('[data-demo]').forEach(el => el.onclick = () => toast(`演示入口：${el.dataset.demo}。本次可体验「关于」功能`));
function focusables() {
  return [...$(`#${currentPage}`).querySelectorAll('button,a[href]')].filter(el => el.getClientRects().length);
}
$('#tv').addEventListener('focusin', event => { if (event.target !== $('#tv')) lastFocus = event.target; });
function navigate(key) {
  const items = focusables();
  if (key === 'Escape' || key === 'Backspace') {
    if (currentPage === 'about') showPage('home');
    else toast('已在桌面首页');
    return;
  }
  if (!items.includes(document.activeElement)) (items.includes(lastFocus) ? lastFocus : items[0])?.focus();
  const active = document.activeElement;
  if (key === 'Enter') { active.click(); return; }
  if (currentPage === 'about') {
    const routes = {
      'back-home': {ArrowRight: 'source-link', ArrowDown: 'source-link'},
      'source-link': {ArrowUp: 'back-home', ArrowLeft: 'back-home', ArrowDown: 'website-link'},
      'website-link': {ArrowUp: 'source-link', ArrowLeft: 'back-home', ArrowDown: 'email-link'},
      'email-link': {ArrowUp: 'website-link', ArrowLeft: 'back-home', ArrowDown: 'license-link'},
      'license-link': {ArrowUp: 'email-link', ArrowLeft: 'back-home', ArrowRight: 'upstream-link'},
      'upstream-link': {ArrowUp: 'email-link', ArrowLeft: 'license-link'}
    };
    const next = routes[active.id]?.[key];
    if (next) $(`#${next}`).focus();
    return;
  }
  const vertical = key === 'ArrowUp' || key === 'ArrowDown';
  const direction = key === 'ArrowLeft' || key === 'ArrowUp' ? -1 : 1;
  const rect = active.getBoundingClientRect();
  const x = rect.x + rect.width / 2, y = rect.y + rect.height / 2;
  const candidates = items.filter(el => el !== active).map(el => {
    const next = el.getBoundingClientRect();
    const dx = next.x + next.width / 2 - x, dy = next.y + next.height / 2 - y;
    const primary = vertical ? dy : dx, secondary = vertical ? dx : dy;
    return {el, primary: primary * direction, score: Math.abs(primary) + Math.abs(secondary) * 2.5};
  }).filter(item => item.primary > 5).sort((a, b) => a.score - b.score);
  candidates[0]?.el.focus();
}
document.addEventListener('keydown', event => {
  if (!['ArrowUp','ArrowDown','ArrowLeft','ArrowRight','Enter','Escape','Backspace'].includes(event.key)) return;
  if (!$('#tv').contains(document.activeElement) && !document.activeElement.closest('.remote')) return;
  event.preventDefault();
  navigate(event.key);
});
document.querySelectorAll('[data-key]').forEach(el => {
  el.onmousedown = event => event.preventDefault();
  el.onclick = () => navigate(el.dataset.key);
});
$('#source-link').focus();
