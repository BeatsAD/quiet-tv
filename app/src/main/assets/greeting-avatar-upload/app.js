'use strict';
const $ = id => document.getElementById(id);
const english = !navigator.language.toLowerCase().startsWith('zh');
const tr = (zh, en) => english ? en : zh;
if (english) {
  document.documentElement.lang = 'en';
  document.querySelector('h1').textContent = 'Custom avatar';
  document.querySelector('label[for=code]').textContent = 'Six-digit code shown on your TV';
  $('connect').querySelector('button').textContent = 'Connect to TV';
  $('empty').textContent = 'Choose a photo';
  $('choose').textContent = 'Choose photo'; $('send').textContent = 'Send to TV';
  document.querySelector('label[for=zoom]').textContent = 'Zoom';
  $('requirements').textContent = 'Drag or pinch to adjust  JPG / PNG / static WebP  Up to 10 MB';
  $('save-hint').textContent = 'After sending, select Save on your TV.';
}
let base = /^\/[a-f0-9]{32}\/$/.test(location.pathname) ? location.pathname : null;
let photo = null, objectUrl = null, selection = 0, zoom = 1, x = 0, y = 0, sending = false, ended = false, gesture = null;
const pointers = new Map(), canvas = $('canvas'), ctx = canvas.getContext('2d');
async function request(path, options) {
  let response, data;
  try {response = await fetch(path, Object.assign({cache: 'no-store'}, options)); data = await response.json();}
  catch (error) {throw new Error(tr('连接已中断，请查看电视；尚未收到头像时重新连接。', 'Connection interrupted. Check the TV; reconnect if the avatar has not arrived.'));}
  if (!response.ok) {
    if (response.status === 410) {ended = true; $('editor').hidden = true; $('connect').hidden = true;}
    throw new Error(data.message || tr('发送失败，请重试。', 'Unable to send. Try again.'));
  }
  return data;
}
async function load() {
  if (!base) {$('connect').hidden = false; return;}
  try {await request(base + 'info'); $('connect').hidden = true; $('editor').hidden = false; $('status').textContent = '';}
  catch (error) {$('status').textContent = error.message;}
}
$('connect').addEventListener('submit', async event => {
  event.preventDefault(); const button = $('connect').querySelector('button'); button.disabled = true;
  try {const data = await request('/connect', {method: 'POST', headers: {'Content-Type': 'text/plain'}, body: $('code').value});base = data.path;history.replaceState(null, '', base);await load();}
  catch (error) {$('status').textContent = error.message;}
  finally {button.disabled = false;}
});
function draw() {
  if (!photo) return;
  const scale = Math.max(512 / photo.naturalWidth, 512 / photo.naturalHeight) * zoom;
  const width = photo.naturalWidth * scale, height = photo.naturalHeight * scale;
  x = Math.max(-(width - 512) / 2, Math.min((width - 512) / 2, x));
  y = Math.max(-(height - 512) / 2, Math.min((height - 512) / 2, y));
  ctx.clearRect(0, 0, 512, 512);ctx.drawImage(photo, (512 - width) / 2 + x, (512 - height) / 2 + y, width, height);
  $('zoom').value = String(zoom);$('zoom-value').textContent = Math.round(zoom * 100) + '%';
}
function checkStatic(buffer, mime) {
  if (mime !== 'image/png' && mime !== 'image/webp') return;
  const view = new DataView(buffer), bytes = new Uint8Array(buffer);
  let at = mime === 'image/png' ? 8 : 12;
  while (at + 8 <= bytes.length) {
    const png = mime === 'image/png', length = view.getUint32(at + (png ? 0 : 4), !png);
    const tag = String.fromCharCode(...bytes.subarray(at + (png ? 4 : 0), at + (png ? 8 : 4)));
    if (['acTL', 'ANIM', 'ANMF'].includes(tag)) throw new Error(tr('请选择静态图片。', 'Choose a static image.'));
    const next = at + 8 + length + (png ? 4 : length % 2);
    if (next <= at || next > bytes.length) throw new Error(tr('图片文件不完整。', 'Incomplete image file.'));
    at = next;
  }
}
$('choose').addEventListener('click', () => $('file').click());
$('file').addEventListener('change', async event => {
  const file = event.target.files[0];event.target.value = '';if (!file || sending || ended) return;
  const attempt = ++selection;$('send').disabled = true;$('zoom').disabled = true;pointers.clear();gesture = null;
  try {
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) throw new Error(tr('请选择 JPG、PNG 或静态 WebP 图片。', 'Choose JPG, PNG or static WebP.'));
    if (file.size > 10 * 1024 * 1024) throw new Error(tr('原图不能超过 10 MB。', 'Original photo must be under 10 MB.'));
    checkStatic(await file.arrayBuffer(), file.type);if (attempt !== selection) return;
    if (objectUrl) URL.revokeObjectURL(objectUrl);objectUrl = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => {
      if (attempt !== selection) return;
      if (!image.naturalWidth || !image.naturalHeight || image.naturalWidth * image.naturalHeight > 40000000) {$('status').textContent = tr('图片尺寸过大，请选择较小照片。', 'Choose a smaller photo.');return;}
      photo = image;zoom = 1;x = y = 0;draw();$('empty').hidden = true;$('send').disabled = false;$('zoom').disabled = false;$('status').textContent = '';
    };
    image.onerror = () => {if (attempt === selection) $('status').textContent = tr('无法读取图片，请重新选择。', 'Cannot read the photo. Choose another.');};
    image.src = objectUrl;
  } catch (error) {if (attempt === selection) $('status').textContent = error.message;}
});
$('zoom').addEventListener('input', () => {if (!sending) {zoom = Number($('zoom').value);draw();}});
function beginGesture() {
  const points = [...pointers.values()];
  gesture = points.length >= 2 ? {distance: Math.hypot(points[0].x - points[1].x, points[0].y - points[1].y), zoom} : points.length ? {point: points[0], x, y} : null;
}
$('crop').addEventListener('pointerdown', event => {
  if (!photo || sending || ended) return;
  pointers.set(event.pointerId, {x: event.clientX, y: event.clientY});$('crop').setPointerCapture(event.pointerId);beginGesture();
});
$('crop').addEventListener('pointermove', event => {
  if (!pointers.has(event.pointerId) || !gesture || sending) return;
  pointers.set(event.pointerId, {x: event.clientX, y: event.clientY});const points = [...pointers.values()];
  if (points.length >= 2 && gesture.distance > 0) zoom = Math.max(1, Math.min(3, gesture.zoom * Math.hypot(points[0].x - points[1].x, points[0].y - points[1].y) / gesture.distance));
  else if (points.length === 1 && gesture.point) {const factor = 512 / $('crop').getBoundingClientRect().width;x = gesture.x + (points[0].x - gesture.point.x) * factor;y = gesture.y + (points[0].y - gesture.point.y) * factor;}
  draw();
});
for (const name of ['pointerup', 'pointercancel']) $('crop').addEventListener(name, event => {pointers.delete(event.pointerId);beginGesture();});
$('send').addEventListener('click', async () => {
  if (!photo || sending || ended) return;
  sending = true;$('send').disabled = true;$('choose').disabled = true;$('zoom').disabled = true;$('status').textContent = tr('正在发送…', 'Sending…');
  try {
    const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/png'));
    if (!blob || blob.size > 2 * 1024 * 1024) throw new Error(tr('图片处理失败，请重新选择。', 'Cannot prepare the photo. Choose another.'));
    await request(base + 'upload', {method: 'POST', headers: {'Content-Type': 'image/png', 'X-Wallpaper-Name': 'avatar.png'}, body: blob});
    ended = true;$('editor').hidden = true;$('status').textContent = tr('头像已发送，请在电视上保存。', 'Avatar sent. Select Save on your TV.');
  } catch (error) {$('status').textContent = error.message;if (!ended) {$('send').disabled = false;$('choose').disabled = false;$('zoom').disabled = false;}}
  finally {sending = false;}
});
window.addEventListener('pagehide', () => {++selection;if (objectUrl) URL.revokeObjectURL(objectUrl);});
load();
