(()=>{
'use strict';
const $=id=>document.getElementById(id),chinese=/^zh/i.test(navigator.language||''),message=(zh,en)=>chinese?zh:en;
let base=/^\/[a-f0-9]{32}\/$/.test(location.pathname)?new URL(location.href):null;
let image=null,sourceUrl=null,revision=0,busy=false,zoom=1,x=50,y=50,drag=null,icon=false,shape="rectangle",requestId=null,uncertain=false;
const maxBytes=2*1024*1024;
function error(text){$('error').textContent=text;$('error').hidden=false;}
if(!chinese){document.documentElement.lang='en';$('pair-title').textContent='Connect to TV';document.querySelector('label[for=code]').textContent='Connection code';$('code').placeholder='Code shown on the TV';$('connect').textContent='Connect to TV';$('editor-title').textContent='Upload app cover';$('placeholder').textContent='Choose an image, then crop it';$('choose').textContent='Choose image';$('send').textContent='Upload to TV';document.querySelector('label[for=zoom]').textContent='Zoom';document.querySelector('label[for=pan-x]').textContent='Horizontal position';document.querySelector('label[for=pan-y]').textContent='Vertical position';$('fileinfo').textContent='Recommended 720 × 406 px. Minimum 360 × 203 px. JPG / PNG / static WebP, up to 2 MB.';$('ai-hint').textContent='You can use AI to create a cover and export it at the dimensions above.';document.querySelector('#success h1').textContent='Cover sent';document.querySelector('#success .intro').textContent='Confirm on the TV to apply. Your original cover stays until then.';}
async function openEditor(){
  $('pairing').hidden=true;$('editor').hidden=false;$('choose').disabled=true;
  try{const response=await fetch(new URL('info',base));const info=await response.json();if(!response.ok)throw new Error(info.message);$('target').textContent=info.label;icon=info.mode==='icon';if(icon){shape=['rectangle','circle','square'].includes(info.shape)?info.shape:'rectangle';document.body.classList.add('icon-mode');document.title=message('Quiet TV 替换应用图标','Quiet TV Replace app icon');$('editor-title').textContent=message('替换应用图标','Replace app icon');$('placeholder').textContent=message('选择图片，预览后确认替换','Choose an image, then confirm');$('icon-shape').hidden=false;$('crop').setAttribute('aria-label',message('拖动图片调整图标位置','Drag image to position icon'));document.querySelector('label[for=zoom]').textContent=message('缩放  拖动图片调整位置','Zoom  Drag image to position');$('ai-hint').hidden=true;document.querySelector('#success h1').textContent=message('替换成功','Icon replaced');document.querySelector('#success .intro').textContent=message('电视已保存图标，显示效果同步更新。','Your TV has saved the icon and is updating its appearance.');$('result').alt=message('已替换的应用图标','Applied app icon');setShape();}$('choose').disabled=false;}
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
function dimensions(){return icon?{w:shape==='rectangle'?720:512,h:shape==='rectangle'?405:512,minW:shape==='rectangle'?360:256,minH:shape==='rectangle'?203:256}:{w:720,h:406,minW:360,minH:203};}
function setShape(){
  const d=dimensions();$('crop').width=d.w;$('crop').height=d.h;requestId=null;
  const name=message({rectangle:'圆角矩形',circle:'圆形',square:'圆角正方形'}[shape],{rectangle:'Rounded rectangle',circle:'Circle',square:'Rounded square'}[shape]);
  $('icon-shape').textContent=message('桌面图标形状  '+name,'Desktop icon shape  '+name);
  $('fileinfo').textContent=message(`推荐 ${d.w} × ${d.h} px  最低 ${d.minW} × ${d.minH} px  JPG / PNG / 静态 WebP  单张不超过 2 MB`,`Recommended ${d.w} × ${d.h} px. Minimum ${d.minW} × ${d.minH} px. JPG / PNG / static WebP, up to 2 MB.`);
  $('result').style.aspectRatio=d.w+'/'+d.h;$('result').style.objectFit='contain';$('result').style.borderRadius=shape==='circle'?'50%':'1rem';
  zoom=1;x=50;y=50;$('zoom').max=4;validateImage();draw();
}
function validateImage(){const d=dimensions(),valid=!!image&&image.naturalWidth>=d.minW&&image.naturalHeight>=d.minH;$('send').disabled=!valid;$('send').textContent=icon?message('确认替换','Confirm replacement'):message('上传到电视','Upload to TV');if(image&&!valid)error(message('当前形状需要更大的图片，请重新选择。','Choose a larger image for this shape.'));else $('error').hidden=true;return valid;}
function scaleFor(d){return (icon?Math.min(d.w/image.naturalWidth,d.h/image.naturalHeight):Math.max(d.w/image.naturalWidth,d.h/image.naturalHeight))*zoom;}
function draw(){
  if(!image)return;const d=dimensions(),canvas=$('crop'),ctx=canvas.getContext('2d'),scale=scaleFor(d),w=image.naturalWidth*scale,h=image.naturalHeight*scale;
  ctx.clearRect(0,0,d.w,d.h);ctx.save();if(icon&&shape==='circle'){ctx.beginPath();ctx.arc(d.w/2,d.h/2,d.w/2,0,2*Math.PI);ctx.clip();}if(!icon){ctx.fillStyle='#505966';ctx.fillRect(0,0,d.w,d.h);}ctx.drawImage(image,(d.w-w)*x/100,(d.h-h)*y/100,w,h);ctx.restore();
  $('zoom').value=zoom;$('zoom-value').value=Math.round(zoom*100)+'%';$('pan-x').value=x;$('pan-x-value').value=Math.round(x)+'%';$('pan-y').value=y;$('pan-y-value').value=Math.round(y)+'%';
}
$('choose').addEventListener('click',()=>$('photo').click());
$('photo').addEventListener('change',async()=>{
  const chosen=$('photo').files[0];if(!chosen)return;const attempt=++revision;requestId=null;image=null;$('send').disabled=true;$('error').hidden=true;$('crop').hidden=true;$('crop-controls').hidden=true;$('placeholder').hidden=false;
  if(!['image/jpeg','image/png','image/webp'].includes(chosen.type)||chosen.size===0||chosen.size>maxBytes){error(message('请选择不超过 2 MB 的 JPG、PNG 或静态 WebP。','Choose JPG, PNG or static WebP up to 2 MB.'));return;}
  try{
    if(!staticImage(await chosen.arrayBuffer(),chosen.type))throw new Error(message('请选择静态图片。','Choose a static image.'));if(attempt!==revision)return;
    if(sourceUrl)URL.revokeObjectURL(sourceUrl);sourceUrl=URL.createObjectURL(chosen);const candidate=new Image();
    candidate.onload=()=>{if(attempt!==revision)return;const w=candidate.naturalWidth,h=candidate.naturalHeight,d=dimensions();const scale=Math.min(w/d.minW,h/d.minH);
      if(w*h>40000000||w>16384||h>16384||scale<1){error(message(`图片至少需要 ${d.minW} × ${d.minH} 像素，且不超过 4000 万像素。`,`Choose at least ${d.minW} × ${d.minH} px and no more than 40 megapixels.`));return;}
      image=candidate;zoom=1;x=50;y=50;$('zoom').max=icon?4:Math.min(4,scale);$('crop').hidden=false;$('placeholder').hidden=true;$('crop-controls').hidden=false;validateImage();draw();};
    candidate.onerror=()=>{if(attempt===revision)error(message('无法读取图片，请换一张。','Cannot read this image. Choose another.'));};candidate.src=sourceUrl;
  }catch(e){if(attempt===revision)error(e.message);}
});
for(const id of ['zoom','pan-x','pan-y'])$(id).addEventListener('input',event=>{if(busy||uncertain)return;requestId=null;const v=Number(event.target.value);if(id==='zoom')zoom=v;else if(id==='pan-x')x=v;else y=v;draw();});
$('crop').addEventListener('pointerdown',event=>{if(busy||uncertain||!image)return;requestId=null;drag={x:event.clientX,y:event.clientY,panX:x,panY:y};event.currentTarget.setPointerCapture(event.pointerId);});
$('crop').addEventListener('pointermove',event=>{if(!drag||!image)return;const d=dimensions(),scale=scaleFor(d),dx=image.naturalWidth*scale-d.w,dy=image.naturalHeight*scale-d.h,ratio=d.w/event.currentTarget.getBoundingClientRect().width;
  if(Math.abs(dx)>1)x=Math.max(0,Math.min(100,drag.panX-(event.clientX-drag.x)*ratio/dx*100));if(Math.abs(dy)>1)y=Math.max(0,Math.min(100,drag.panY-(event.clientY-drag.y)*ratio/dy*100));draw();});
$('crop').addEventListener('pointerup',()=>drag=null);$('crop').addEventListener('pointercancel',()=>drag=null);
function enabled(value){$('send').disabled=!value||!image;$('choose').disabled=!value||uncertain;for(const input of document.querySelectorAll('#crop-controls input'))input.disabled=!value||uncertain;}
function finish(){busy=false;enabled(true);$('progress').hidden=true;$('status').textContent='';}
function success(result){if(icon&&result.applied!==true)throw new Error(message('尚未确认替换结果，请检查连接。','Replacement was not confirmed. Check the connection.'));finish();uncertain=false;$('editor').hidden=true;$('success').hidden=false;$('result').src=$('crop').toDataURL('image/png');}
async function recover(){
  busy=true;uncertain=true;enabled(false);$('error').hidden=true;$('status').textContent=message('正在检查替换结果…','Checking replacement…');
  try{const response=await fetch(new URL('uploads/'+requestId,base));const result=await response.json();if(response.ok){success(result);return;}throw new Error(response.status===404?message('替换结果尚未确认，请稍后再次检查。','Replacement is not confirmed yet. Check again shortly.'):result.message);}
  catch(e){error(e.message||message('连接中断，恢复网络后检查替换结果。','Connection lost. Restore the network and check the result.'));}
  finish();$('send').textContent=message('检查替换结果','Check replacement');
}
$('send').addEventListener('click',()=>{
  if(!image||busy||!base)return;if(icon&&uncertain){recover();return;}if(!validateImage())return;busy=true;drag=null;enabled(false);$('error').hidden=true;
  $('crop').toBlob(blob=>{
    if(!blob||blob.size>maxBytes){finish();error(message('裁剪结果超过 2 MB，请换一张图片。','Cropped image exceeds 2 MB. Choose another image.'));return;}
    const xhr=new XMLHttpRequest();xhr.open('POST',new URL('upload',base));xhr.setRequestHeader('Content-Type','image/png');xhr.setRequestHeader('X-Wallpaper-Name',icon?'icon.png':'cover.png');xhr.timeout=90000;
    if(icon){if(!requestId){const random=new Uint8Array(16);crypto.getRandomValues(random);requestId=Array.from(random,v=>v.toString(16).padStart(2,'0')).join('');}xhr.setRequestHeader('X-Wallpaper-Request',requestId);xhr.setRequestHeader('X-Quiet-Icon-Shape',shape);}
    $('progress').hidden=false;$('progress').setAttribute('aria-valuenow','0');$('progress').querySelector('span').style.width='0%';$('status').textContent=icon?message('正在替换…','Replacing icon…'):message('正在上传…','Uploading…');
    xhr.upload.onprogress=event=>{if(event.lengthComputable){const n=Math.round(event.loaded/event.total*100);$('progress').setAttribute('aria-valuenow',String(n));$('progress').querySelector('span').style.width=n+'%';if(icon&&n===100)$('status').textContent=message('正在保存图标…','Saving icon…');}};
    xhr.onload=()=>{let result;try{result=JSON.parse(xhr.responseText);}catch{if(icon){recover();return;}finish();error(message('电视返回异常，请重新打开上传页面。','Unexpected response. Reopen the upload page.'));return;}if(xhr.status!==201){finish();requestId=null;error(result.message||message('上传失败，请重试。','Upload failed. Please retry.'));return;}try{success(result);}catch(e){if(icon)recover();else{finish();error(e.message);}}};
    xhr.onerror=xhr.ontimeout=()=>{if(icon)recover();else{finish();error(message('连接断开或上传超时，请检查网络与电视上传页面。','Connection lost or upload timed out. Check the network and TV upload screen.'));}};xhr.send(blob);
  },'image/png');
});
})();
