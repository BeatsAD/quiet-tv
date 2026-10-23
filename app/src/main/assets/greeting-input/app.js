'use strict';
const connect = document.getElementById('connect');
const editor = document.getElementById('editor');
const status = document.getElementById('status');
const text = document.getElementById('text');
const send = document.getElementById('send');
const english = !navigator.language.toLowerCase().startsWith('zh');
const tr = (zh, en) => english ? en : zh;
if (english) {
  document.documentElement.lang = 'en';
  document.querySelector('h1').textContent = 'Home greeting';
  document.querySelector('label[for="code"]').textContent = 'Six-digit code shown on your TV';
  connect.querySelector('button').textContent = 'Connect to TV';
  document.querySelector('label[for="text"]').textContent = 'Text to show on Home';
  text.placeholder = 'Leave empty for a greeting based on the time of day';
  document.querySelector('.hint').textContent = 'After sending, select Save on your TV.';
  send.textContent = 'Send to TV';
}
let base = /^\/[a-f0-9]{32}\/$/.test(location.pathname) ? location.pathname : null;
async function request(path, options) {
  let response, data;
  try {
    response = await fetch(path, Object.assign({cache: 'no-store'}, options));
    data = await response.json();
  } catch (error) {
    throw new Error(tr('连接已中断，请查看电视；未收到文字时请重新连接。', 'Connection interrupted. Check the TV and reconnect if the text has not arrived.'));
  }
  if (!response.ok) {
    if (response.status === 410 || response.status === 409) {
      editor.hidden = true; connect.hidden = true;
      throw new Error(tr('会话已结束，请在电视重新打开手机输入。', 'Session ended. Open Phone input again on your TV.'));
    }
    throw new Error(data.message || tr('未能发送，请重试。', 'Unable to send. Try again.'));
  }
  return data;
}
async function load() {
  if (!base) {connect.hidden = false;status.textContent = '';return;}
  try {
    const data = await request(base + 'draft');
    text.value = data.text; editor.hidden = false; connect.hidden = true; status.textContent = '';
  } catch (error) {status.textContent = error.message || tr('无法连接电视，请检查是否在同一局域网。', 'Cannot reach the TV. Check your local network.');}
}
connect.addEventListener('submit', async event => {
  event.preventDefault(); const button = connect.querySelector('button');button.disabled = true;
  try {
    const data = await request('/connect', {method: 'POST', headers: {'Content-Type': 'text/plain'}, body: document.getElementById('code').value});
    base = data.path;history.replaceState(null, '', base);await load();
  } catch (error) {status.textContent = error.message || tr('无法连接电视，请检查网络后重试。', 'Cannot reach the TV. Check the network and retry.');}
  finally {button.disabled = false;}
});
editor.addEventListener('submit', async event => {
  event.preventDefault();send.disabled = true;
  const value = text.value.replace(/[\r\n\t]/g, ' ').trim();
  const body = JSON.stringify({text: value});
  if (new Blob([body]).size > 8192) {status.textContent = tr('文字过长，请精简后重试。', 'Text is too long. Please shorten it.');send.disabled = false;return;}
  try {
    await request(base + 'text', {method: 'POST', headers: {'Content-Type': 'application/json'}, body});
    editor.hidden = true;status.textContent = tr('文字已发送到电视\n请用遥控器选择“保存”。', 'Text sent to your TV.\nSelect Save with your remote.');
  } catch (error) {status.textContent = error.message || tr('未确认接收，请查看电视；尚未收到时请重新连接。', 'Receipt not confirmed. Check the TV; reconnect if the text has not arrived.');send.disabled = false;}
});
load();
