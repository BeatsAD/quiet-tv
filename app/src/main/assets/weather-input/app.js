'use strict';
const $ = id => document.getElementById(id);
const english = !navigator.language.toLowerCase().startsWith('zh');
const tr = (zh, en) => english ? en : zh;
let base = /^\/[a-f0-9]{32}\/$/.test(location.pathname) ? location.pathname : null;
let selected = null, searchVersion = 0, cityVersion = 0, districtVersion = 0, timer;
const records = new Map();
if (english) {
  document.documentElement.lang = 'en'; document.querySelector('h1').textContent = 'Weather location';
  const labels = {code: 'Six-digit code shown on your TV', province: 'Province', city: 'City', district: 'District', query: 'Search city or district'};
  Object.entries(labels).forEach(([id, text]) => {document.querySelector(`label[for="${id}"]`).textContent = text;$(id).setAttribute('aria-label', text);});
  $('connect').querySelector('button').textContent = 'Connect to TV';$('search').querySelector('button').textContent = 'Search';
  $('query').placeholder = 'For example: 武汉';$('send').textContent = 'Send to TV';document.querySelector('.hint').textContent = 'After sending, select Save on your TV.';
}
async function request(path, options, timeoutMs = 8000) {
  let response, data;
  const controller = new AbortController(), timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {response = await fetch(path, Object.assign({cache: 'no-store', signal: controller.signal}, options));data = await response.json();}
  catch (error) {throw new Error(tr('连接中断，请查看电视并重新连接。', 'Connection interrupted. Check your TV and reconnect.'));}
  finally {clearTimeout(timeout);}
  if (!response.ok) {
    if (response.status === 410) {$('editor').hidden = true;$('connect').hidden = true;}
    throw new Error(data.message || tr('请求失败，请重试。', 'Request failed. Try again.'));
  }
  return data;
}
function choose(region) {
  selected = region;$('send').disabled = !region;
  $('selection').textContent = region ? tr('已选  ', 'Selected  ') + region.name + (region.area ? '  ' + region.area : '') : '';
}
function options(select, items, placeholder) {
  select.replaceChildren(new Option(placeholder, ''));
  items.forEach(item => {records.set(item.code, item);select.add(new Option(item.name, item.code));});
  select.disabled = items.length === 0;
}
async function children(parent) {return (await request(base + 'children?parent=' + encodeURIComponent(parent))).results;}
async function syncSelectors(region) {
  const cityRequest = ++cityVersion, districtRequest = ++districtVersion;
  if (!region.provinceCode || !region.cityCode) {
    $('province').value = '';options($('city'), [], tr('选择城市', 'City'));options($('district'), [], tr('选择区县', 'District'));return;
  }
  $('province').value = region.provinceCode;
  try {
    const cities = await children(region.provinceCode);
    if (cityRequest !== cityVersion || districtRequest !== districtVersion) return;
    options($('city'), cities, tr('选择城市', 'City'));$('city').value = region.cityCode;
    const districts = await children(region.cityCode);
    if (cityRequest !== cityVersion || districtRequest !== districtVersion) return;
    options($('district'), districts, tr('全市 / 选择区县', 'City / choose district'));$('district').value = region.level === 2 ? region.code : '';
  } catch (error) {if (cityRequest === cityVersion && districtRequest === districtVersion) $('status').textContent = error.message;}
}
async function load() {
  if (!base) {$('connect').hidden = false;$('status').textContent = '';return;}
  try {options($('province'), await children(''), tr('选择省份', 'Province'));options($('city'), [], tr('选择城市', 'City'));options($('district'), [], tr('选择区县', 'District'));$('connect').hidden = true;$('editor').hidden = false;$('status').textContent = '';}
  catch (error) {$('status').textContent = error.message;}
}
$('connect').addEventListener('submit', async event => {
  event.preventDefault();const button = $('connect').querySelector('button');button.disabled = true;
  try {const data = await request('/connect', {method: 'POST', headers: {'Content-Type': 'text/plain'}, body: $('code').value});base = data.path;history.replaceState(null, '', base);await load();}
  catch (error) {$('status').textContent = error.message;}finally {button.disabled = false;}
});
$('province').addEventListener('change', async () => {
  const version = ++cityVersion;++districtVersion;choose(null);options($('city'), [], tr('选择城市', 'City'));options($('district'), [], tr('选择区县', 'District'));if (!$('province').value) return;
  try {const items = await children($('province').value);if (version === cityVersion) options($('city'), items, tr('选择城市', 'City'));}
  catch (error) {if (version === cityVersion) $('status').textContent = error.message;}
});
$('city').addEventListener('change', async () => {
  const version = ++districtVersion;choose(records.get($('city').value) || null);options($('district'), [], tr('选择区县', 'District'));if (!$('city').value) return;
  try {const items = await children($('city').value);if (version === districtVersion) options($('district'), items, tr('全市 / 选择区县', 'City / choose district'));}
  catch (error) {if (version === districtVersion) $('status').textContent = error.message;}
});
$('district').addEventListener('change', () => {++districtVersion;choose(records.get($('district').value) || records.get($('city').value) || null);});
async function search(online = false) {
  clearTimeout(timer);const version = ++searchVersion, query = $('query').value.trim();$('results').replaceChildren();if (!query) return;
  try {
    const data = await request(base + 'search?q=' + encodeURIComponent(query) + (online ? '&online=1' : ''), undefined, online ? 22000 : 8000);if (version !== searchVersion) return;
    data.results.forEach(region => {
      const button = document.createElement('button');button.type = 'button';button.textContent = region.name;button.setAttribute('aria-pressed', String(selected?.code === region.code));
      if (region.area) {const area = document.createElement('small');area.textContent = region.area;button.append(area);}
      button.addEventListener('click', () => {choose(region);syncSelectors(region);$('results').querySelectorAll('button').forEach(item => item.setAttribute('aria-pressed', String(item === button)));});$('results').append(button);
    });
    $('status').textContent = data.results.length ? '' : (online ? tr('未找到地点，请换个关键词。', 'No places found. Try another keyword.') : tr('未找到本地地点，点击搜索联网查找。', 'No local places. Tap Search to look online.'));
  } catch (error) {if (version === searchVersion) $('status').textContent = error.message;}
}
$('query').addEventListener('input', () => {++searchVersion;clearTimeout(timer);timer = setTimeout(search, 180);});
$('search').addEventListener('submit', event => {event.preventDefault();search(true);});
$('send').addEventListener('click', async () => {
  if (!selected) return;$('send').disabled = true;
  try {await request(base + 'select', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({code: selected.code})});$('editor').hidden = true;$('status').textContent = tr('地区已发送到电视\n请用遥控器选择“保存”。', 'Location sent to your TV.\nSelect Save with your remote.');}
  catch (error) {$('status').textContent = error.message;$('send').disabled = false;}
});
load();
