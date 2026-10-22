(()=>{
const $=id=>document.getElementById(id),english=!navigator.language.toLowerCase().startsWith('zh');
let file=null,url=null,busy=false;
let uploadBase=new URL('.',location.href);
const needsCode=location.pathname==='/';
$('pairing').hidden=!needsCode;$('editor').hidden=needsCode;
const message=(zh,en)=>english?en:zh;
if(english){document.documentElement.lang='en';document.title='Quiet TV  Upload wallpaper';$('connection').textContent='Local network upload';document.querySelector('#editor h1').innerHTML='Your favorite view.<br>On your TV.';document.querySelector('#editor .intro').textContent='Choose a photo, upload it, then apply it on your TV.';$('placeholder').querySelector('span').textContent='Choose a favorite view';$('photo').setAttribute('aria-label','Choose wallpaper image');$('change').textContent='Change photo';$('fileinfo').textContent='16:9 landscape recommended  JPG / PNG / WebP  Up to 20 MB';$('send').textContent='Upload to TV';document.querySelector('#success h1').textContent='Upload complete';document.querySelector('#success .intro').textContent='Added to your TV wallpaper library. Press Back on the TV, then select the photo to apply it.';$('another').textContent='Upload another';document.querySelector('footer').textContent='Direct to your TV  No cloud';}
function error(text){$('error').textContent=text;$('error').hidden=false;}
if(english){document.querySelector('#pairing h1').textContent='Connect to TV';document.querySelector('#pairing .intro').textContent='Enter the six-digit code shown on the TV upload screen.';document.querySelector('label[for=code]').textContent='Connection code';$('connect').textContent='Connect to TV';}
$('pair-form').addEventListener('submit',async event=>{
  event.preventDefault();$('pair-error').hidden=true;$('connect').disabled=true;
  try{
    const response=await fetch(new URL('connect',location.href),{method:'POST',headers:{'Content-Type':'text/plain'},body:$('code').value});
    const result=await response.json();
    if(!response.ok){$('pair-error').textContent=result.message;$('pair-error').hidden=false;return;}
    uploadBase=new URL(result.path,location.href);$('pairing').hidden=true;$('editor').hidden=false;
  }catch{$('pair-error').textContent=message('连接失败，请检查电视上传页面是否打开。','Connection failed. Keep the TV upload screen open.');$('pair-error').hidden=false;}
  finally{$('connect').disabled=false;}
});
$('photo').addEventListener('change',()=>{
  const chosen=$('photo').files[0];if(!chosen)return;$('error').hidden=true;$('send').disabled=true;file=null;
  if(!['image/jpeg','image/png','image/webp'].includes(chosen.type)){error(message('请选择 JPG、PNG 或静态 WebP 图片；HEIC 照片请先转为 JPG。','Choose JPG, PNG or static WebP. Convert HEIC photos to JPG first.'));return;}
  if(chosen.size>20*1024*1024||chosen.size===0){error(message('图片大小需在 0～20 MB 之间。','Choose a non-empty image up to 20 MB.'));return;}
  if(url)URL.revokeObjectURL(url);url=URL.createObjectURL(chosen);
  $('preview').onload=()=>{const image=$('preview');if(image.naturalWidth*image.naturalHeight>40000000||Math.max(image.naturalWidth,image.naturalHeight)>16384){error(message('图片尺寸过大，请选择不超过 4000 万像素的图片。','Photo dimensions are too large. Choose up to 40 megapixels.'));return;}file=chosen;$('send').disabled=false;};
  $('preview').onerror=()=>{error(message('无法预览这张图片，请换一张重试。','Cannot preview this photo. Please choose another.'));};
  $('preview').src=url;$('preview').hidden=false;$('placeholder').hidden=true;$('change').hidden=false;
  $('fileinfo').textContent=chosen.name+'  '+(chosen.size/1024/1024).toFixed(1)+' MB';$('status').textContent='';
});
$('send').addEventListener('click',()=>{
  if(!file||busy)return;busy=true;$('send').disabled=true;$('photo').disabled=true;$('error').hidden=true;$('progress').hidden=false;$('progress').setAttribute('aria-valuenow','0');$('progress').querySelector('span').style.width='0%';$('status').textContent=message('正在上传…','Uploading…');
  const xhr=new XMLHttpRequest();xhr.open('POST',new URL('upload',uploadBase));xhr.setRequestHeader('Content-Type',file.type);xhr.setRequestHeader('X-Wallpaper-Name',encodeURIComponent(file.name));xhr.timeout=90000;
  xhr.upload.onprogress=e=>{if(e.lengthComputable){const n=Math.round(e.loaded/e.total*100);$('progress').setAttribute('aria-valuenow',String(n));$('progress').querySelector('span').style.width=n+'%';if(n===100)$('status').textContent=message('图片已传送，电视正在处理…','Transferred. Your TV is processing the photo…');}};
  function finish(){busy=false;$('photo').disabled=false;$('send').disabled=!file;$('progress').hidden=true;}
  xhr.onload=()=>{finish();let result;try{result=JSON.parse(xhr.responseText);}catch{error(message('电视返回异常，请重新打开上传页面。','Unexpected TV response. Reopen the upload page.'));return;}
    if(xhr.status!==201){error(result.message||message('上传失败，请重试。','Upload failed. Please retry.'));return;}
    $('editor').hidden=true;$('success').hidden=false;$('result').src=url;
  };
  xhr.onerror=()=>{finish();error(message('连接已断开，请保持电视上传页面打开，并检查是否在同一局域网。','Connection lost. Keep the TV upload screen open and check your local network.'));};
  xhr.ontimeout=()=>{finish();error(message('上传超时，请检查电视是否仍在上传页面。','Upload timed out. Check that the TV upload screen is still open.'));};
  xhr.send(file);
});
$('another').addEventListener('click',()=>{$('success').hidden=true;$('editor').hidden=false;$('status').textContent='';$('error').hidden=true;});
})();
