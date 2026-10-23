(()=>{
'use strict';
const $=id=>document.getElementById(id),chinese=/^zh/i.test(navigator.language||''),message=(zh,en)=>chinese?zh:en;
let base=/^\/[a-f0-9]{32}\/$/.test(location.pathname)?new URL(location.href):null;
let image=null,sourceUrl=null,revision=0,busy=false,zoom=1,x=50,y=50,drag=null;
const maxBytes=2*1024*1024;
function error(text){$('error').textContent=text;$('error').hidden=false;}
if(!chinese){document.documentElement.lang='en';$('pair-title').textContent='Connect to TV';document.querySelector('label[for=code]').textContent='Connection code';$('code').placeholder='6 digits';$('connect').textContent='Connect to TV';$('editor-title').textContent='Upload app cover';$('placeholder').textContent='Choose an image, then crop it';$('choose').textContent='Choose image';$('send').textContent='Upload to TV';document.querySelector('label[for=zoom]').textContent='Zoom';document.querySelector('label[for=pan-x]').textContent='Horizontal position';document.querySelector('label[for=pan-y]').textContent='Vertical position';$('fileinfo').textContent='Recommended 720 × 406 px. Minimum 360 × 203 px. JPG / PNG / static WebP, up to 2 MB.';$('ai-hint').textContent='You can use AI to create a cover and export it at the dimensions above.';document.querySelector('#success h1').textContent='Cover sent';document.querySelector('#success .intro').textContent='Confirm on the TV to apply. Your original cover stays until then.';}
async function openEditor(){
  $('pairing').hidden=true;$('editor').hidden=false;
  try{const response=await fetch(new URL('info',base));const info=await response.json();if(!response.ok)throw new Error(info.message);$('target').textContent=info.label;}
  catch{error(message('会话不可用，请在电视重新打开更换封面。','Session unavailable. Reopen Change cover on your TV.'));$('choose').disabled=true;}
}
if(base)openEditor();else $('pairing').hidden=false;
$('pair-form').addEventListener('submit',async event=>{
  event.preventDefault();$('pair-error').hidden=true;$('connect').disabled=true;
  try{const response=await fetch(new URL('connect',location.href),{method:'POST',headers:{'Content-Type':'text/plain'},body:$('code').value});const result=await response.json();if(!response.ok)throw new Error(result.message);base=new URL(result.path,location.href);await openEditor();}
  catch(e){$('pair-error').textContent=e.message||message('连接失败，请检查电视上传页面是否打开。','Connection failed. Keep the TV upload screen open.');$('pair-error').hidden=false;}
  finally{$('connect').disabled=false;}
});
function staticImage(buffer,type){
  const view=new DataView(buffer),text=(p)=>String.fromCharCode(view.getUint8(p),view.getUint8(p+1),view.getUint8(p+2),view.getUint8(p+3));
  if(type==='image/webp'||type==='image/png')for(let p=type==='image/png'?8:12;p+8<=view.byteLength;){const size=view.getUint32(type==='image/png'?p:p+4,type==='image/webp'),kind=text(type==='image/png'?p+4:p);if(['acTL','ANIM','ANMF'].includes(kind))return false;p+=8+size+(type==='image/png'?4:size%2);}
  return true;
}
function draw(){
  if(!image)return;const canvas=$('crop'),ctx=canvas.getContext('2d'),scale=Math.max(720/image.naturalWidth,406/image.naturalHeight)*zoom,w=image.naturalWidth*scale,h=image.naturalHeight*scale;
  ctx.fillStyle='#505966';ctx.fillRect(0,0,720,406);ctx.drawImage(image,(720-w)*x/100,(406-h)*y/100,w,h);
  $('zoom').value=zoom;$('zoom-value').value=Math.round(zoom*100)+'%';$('pan-x').value=x;$('pan-x-value').value=Math.round(x)+'%';$('pan-y').value=y;$('pan-y-value').value=Math.round(y)+'%';
}
$('choose').addEventListener('click',()=>$('photo').click());
$('photo').addEventListener('change',async()=>{
  const chosen=$('photo').files[0];if(!chosen)return;const attempt=++revision;image=null;$('send').disabled=true;$('error').hidden=true;$('crop').hidden=true;$('crop-controls').hidden=true;$('placeholder').hidden=false;
  if(!['image/jpeg','image/png','image/webp'].includes(chosen.type)||chosen.size===0||chosen.size>maxBytes){error(message('请选择不超过 2 MB 的 JPG、PNG 或静态 WebP。','Choose JPG, PNG or static WebP up to 2 MB.'));return;}
  try{
    if(!staticImage(await chosen.arrayBuffer(),chosen.type))throw new Error(message('请选择静态图片。','Choose a static image.'));if(attempt!==revision)return;
    if(sourceUrl)URL.revokeObjectURL(sourceUrl);sourceUrl=URL.createObjectURL(chosen);const candidate=new Image();
    candidate.onload=()=>{if(attempt!==revision)return;const w=candidate.naturalWidth,h=candidate.naturalHeight;const scale=Math.min(w/360,h/203);
      if(w*h>40000000||w>16384||h>16384||scale<1){error(message('图片至少需要 360 × 203 像素，且不超过 4000 万像素。','Choose at least 360 × 203 px and no more than 40 megapixels.'));return;}
      image=candidate;zoom=1;x=50;y=50;$('zoom').max=Math.min(4,scale);$('crop').hidden=false;$('placeholder').hidden=true;$('crop-controls').hidden=false;$('send').disabled=false;draw();};
    candidate.onerror=()=>{if(attempt===revision)error(message('无法读取图片，请换一张。','Cannot read this image. Choose another.'));};candidate.src=sourceUrl;
  }catch(e){if(attempt===revision)error(e.message);}
});
for(const id of ['zoom','pan-x','pan-y'])$(id).addEventListener('input',event=>{const v=Number(event.target.value);if(id==='zoom')zoom=v;else if(id==='pan-x')x=v;else y=v;draw();});
$('crop').addEventListener('pointerdown',event=>{if(busy||!image)return;drag={x:event.clientX,y:event.clientY,panX:x,panY:y};event.currentTarget.setPointerCapture(event.pointerId);});
$('crop').addEventListener('pointermove',event=>{if(!drag||!image)return;const scale=Math.max(720/image.naturalWidth,406/image.naturalHeight)*zoom,dx=image.naturalWidth*scale-720,dy=image.naturalHeight*scale-406,ratio=720/event.currentTarget.getBoundingClientRect().width;
  if(dx>1)x=Math.max(0,Math.min(100,drag.panX-(event.clientX-drag.x)*ratio/dx*100));if(dy>1)y=Math.max(0,Math.min(100,drag.panY-(event.clientY-drag.y)*ratio/dy*100));draw();});
$('crop').addEventListener('pointerup',()=>drag=null);$('crop').addEventListener('pointercancel',()=>drag=null);
function enabled(value){$('send').disabled=!value||!image;$('choose').disabled=!value;for(const id of ['zoom','pan-x','pan-y'])$(id).disabled=!value;}
$('send').addEventListener('click',()=>{
  if(!image||busy||!base)return;busy=true;drag=null;enabled(false);$('error').hidden=true;
  $('crop').toBlob(blob=>{
    if(!blob||blob.size>maxBytes){busy=false;enabled(true);error(message('裁剪结果超过 2 MB，请换一张图片。','Cropped cover exceeds 2 MB. Choose another image.'));return;}
    const xhr=new XMLHttpRequest();xhr.open('POST',new URL('upload',base));xhr.setRequestHeader('Content-Type','image/png');xhr.setRequestHeader('X-Wallpaper-Name','cover.png');xhr.timeout=90000;
    $('progress').hidden=false;$('progress').setAttribute('aria-valuenow','0');$('progress').querySelector('span').style.width='0%';$('status').textContent=message('正在上传…','Uploading…');
    xhr.upload.onprogress=event=>{if(event.lengthComputable){const n=Math.round(event.loaded/event.total*100);$('progress').setAttribute('aria-valuenow',String(n));$('progress').querySelector('span').style.width=n+'%';}};
    const finish=()=>{busy=false;enabled(true);$('progress').hidden=true;$('status').textContent='';};
    xhr.onload=()=>{finish();let result;try{result=JSON.parse(xhr.responseText);}catch{error(message('电视返回异常，请重新打开上传页面。','Unexpected response. Reopen the upload page.'));return;}if(xhr.status!==201){error(result.message||message('上传失败，请重试。','Upload failed. Please retry.'));return;}$('editor').hidden=true;$('success').hidden=false;$('result').src=$('crop').toDataURL('image/png');};
    xhr.onerror=()=>{finish();error(message('连接断开，请确认电视上传页面仍然打开。','Connection lost. Keep the TV upload screen open.'));};xhr.ontimeout=()=>{finish();error(message('上传超时，请检查网络和电视上传页面。','Upload timed out. Check the network and TV upload screen.'));};xhr.send(blob);
  },'image/png');
});
})();
