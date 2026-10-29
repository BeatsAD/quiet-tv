(()=>{
const $=id=>document.getElementById(id),english=!navigator.language.toLowerCase().startsWith('zh');
const message=(zh,en)=>english?en:zh;
let file=null,url=null,video=false,xhr=null,selection=0,limits=null;
let uploadBase=new URL('.',location.href);
const needsCode=location.pathname==='/';
$('pairing').hidden=!needsCode;$('editor').hidden=needsCode;
if(english){
  document.documentElement.lang='en';document.title='Quiet TV  Upload wallpaper';
  $('editor-title').textContent='Upload wallpaper';$('placeholder').querySelector('.placeholder-text').textContent='Choose image or video';
  $('photo').setAttribute('aria-label','Choose wallpaper image or video');$('change').textContent='Change file';
  $('send').textContent='Upload to TV';$('cancel').textContent='Cancel upload';$('progress').setAttribute('aria-label','Upload progress');
  $('name-label').textContent='Wallpaper name';$('name-hint').textContent='Leave unchanged to keep the original name';
  $('success-title').textContent='Upload complete';document.querySelector('#success .intro').textContent='Press Back on the TV, then preview and apply the wallpaper.';$('another').textContent='Upload another';
  $('pair-title').textContent='Connect to TV';document.querySelector('label[for=code]').textContent='Connection code';$('code').placeholder='6 digits';$('connect').textContent='Connect to TV';
}
function error(text){$('error').textContent=text;$('error').hidden=false;}
function updateName(){
  $('name-count').textContent=$('wallpaper-name').value.length+'/48';
  $('wallpaper-name').removeAttribute('aria-invalid');$('name-error').hidden=true;
}
$('wallpaper-name').addEventListener('input',updateName);
async function capabilities(){
  limits=null;$('photo').disabled=true;$('send').disabled=true;
  try{const response=await fetch(new URL('info',uploadBase));if(!response.ok)throw Error();const data=await response.json();if(data.purpose!=='wallpaper')throw Error();limits=data;
    $('fileinfo').textContent=message('图片  JPG / PNG / 静态 WebP  最大 ','Images  JPG / PNG / static WebP  Up to ')+(data.imageMaxBytes/1024/1024)+' MB\n'+message('视频  MP4 / MOV / MKV / WebM  最大 ','Video  MP4 / MOV / MKV / WebM  Up to ')+(data.videoMaxBytes/1024/1024)+' MB  '+data.videoMaxFps+' FPS\n'+message('视频规格以电视检查结果为准','Video compatibility is checked on the TV');$('photo').disabled=false;
  }catch{error(message('会话不可用，请在电视重新打开上传页面。','Session unavailable. Reopen upload on your TV.'));}
}
if(!needsCode)capabilities();
$('pair-form').addEventListener('submit',async event=>{
  event.preventDefault();$('pair-error').hidden=true;$('connect').disabled=true;
  try{const response=await fetch(new URL('connect',location.href),{method:'POST',headers:{'Content-Type':'text/plain'},body:$('code').value});const result=await response.json();
    if(!response.ok){$('pair-error').textContent=result.message;$('pair-error').hidden=false;return;}
    uploadBase=new URL(result.path,location.href);$('pairing').hidden=true;$('editor').hidden=false;await capabilities();
  }catch{$('pair-error').textContent=message('连接失败，请保持电视上传页面打开。','Connection failed. Keep the TV upload screen open.');$('pair-error').hidden=false;}
  finally{$('connect').disabled=false;}
});
function releasePreview(){
  for(const id of ['video-preview','result-video']){const v=$(id);v.pause();v.removeAttribute('src');v.load();v.hidden=true;}
  for(const id of ['preview','result']){$(id).removeAttribute('src');$(id).hidden=true;}
  if(url)URL.revokeObjectURL(url);url=null;
}
$('photo').addEventListener('change',()=>{
  const chosen=$('photo').files[0];if(!chosen||!limits)return;const version=++selection;
  releasePreview();file=null;$('naming').hidden=true;$('send').disabled=true;$('error').hidden=true;$('status').textContent='';
  video=['video/mp4','video/quicktime','video/x-matroska','video/webm'].includes(chosen.type)||/\.(mp4|mov|mkv|webm)$/i.test(chosen.name);
  if(!video&&!['image/jpeg','image/png','image/webp'].includes(chosen.type)){error(message('请选择 JPG、PNG、静态 WebP 图片或 MP4、MOV、MKV、WebM 视频。','Choose JPG, PNG, static WebP, MP4, MOV, MKV or WebM.'));return;}
  const maximum=video?limits.videoMaxBytes:limits.imageMaxBytes;
  if(chosen.size===0||chosen.size>maximum){error(message('文件不能为空，大小不能超过 ','Choose a non-empty file up to ')+(maximum/1024/1024)+' MB');return;}
  url=URL.createObjectURL(chosen);$('placeholder').hidden=true;$('change').hidden=false;
  $('fileinfo').textContent=chosen.name+'  '+(chosen.size/1024/1024).toFixed(1)+' MB';
  const originalName=chosen.name.replace(/\.(jpe?g|png|webp|mp4|mov|mkv|webm)$/i,'').replace(/[\u0000-\u001f\u007f/\\]/g,' ').trim();
  $('wallpaper-name').value=originalName.slice(0,48).replace(/[\uD800-\uDBFF]$/,'')||message(video?'动态壁纸':'我的壁纸',video?'Video wallpaper':'My wallpaper');
  updateName();$('naming').hidden=false;
  if(video){
    const preview=$('video-preview');file=chosen;$('send').disabled=false;preview.hidden=false;
    preview.onerror=()=>{if(version===selection)$('status').textContent=message('此浏览器无法预览，可以上传后在电视确认。','This browser cannot preview the video. You can upload and check it on the TV.');};
    preview.src=url;
  }else{
    const preview=$('preview');preview.hidden=false;
    preview.onload=()=>{if(version!==selection)return;if(preview.naturalWidth*preview.naturalHeight>40000000||Math.max(preview.naturalWidth,preview.naturalHeight)>16384){error(message('图片尺寸过大，请选择不超过 4000 万像素的图片。','Photo dimensions are too large. Choose up to 40 megapixels.'));return;}file=chosen;$('send').disabled=false;};
    preview.onerror=()=>{if(version===selection)error(message('无法预览这张图片，请换一张。','Cannot preview this photo. Please choose another.'));};preview.src=url;
  }
});
$('send').addEventListener('click',()=>{
  if(!file||xhr)return;
  const name=$('wallpaper-name').value.trim();
  if(!name||name.length>48||/[\u0000-\u001f\u007f/\\]/.test(name)){
    $('name-error').textContent=message(!name?'请输入壁纸名称':name.length>48?'名称最多 48 个字符':'名称不能包含 / 或 \\',!name?'Enter a wallpaper name':name.length>48?'Use up to 48 characters':'The name cannot contain / or \\');
    $('name-error').hidden=false;$('wallpaper-name').setAttribute('aria-invalid','true');$('wallpaper-name').focus();return;
  }
  $('wallpaper-name').value=name;updateName();
  const extension=file.name.match(/\.(jpe?g|png|webp|mp4|mov|mkv|webm)$/i),request=new XMLHttpRequest();xhr=request;const sendingVideo=video;
  $('send').disabled=true;$('photo').disabled=true;$('wallpaper-name').disabled=true;$('error').hidden=true;$('cancel').hidden=false;$('progress').hidden=false;$('progress').setAttribute('aria-valuenow','0');$('progress').querySelector('span').style.width='0%';$('status').textContent=message('正在上传…','Uploading…');
  request.open('POST',new URL('upload',uploadBase));request.setRequestHeader('Content-Type',file.type||'application/octet-stream');request.setRequestHeader('X-Wallpaper-Name',encodeURIComponent(name+(extension?extension[0]:'')));request.setRequestHeader('X-Wallpaper-Kind',sendingVideo?'video':'image');request.timeout=sendingVideo?630000:90000;
  request.upload.onprogress=e=>{if(e.lengthComputable){const n=Math.round(e.loaded/e.total*100);$('progress').setAttribute('aria-valuenow',String(n));$('progress').querySelector('span').style.width=n+'%';if(n===100)$('status').textContent=sendingVideo?message('电视正在检查视频…','Your TV is checking the video…'):message('电视正在处理图片…','Your TV is processing the photo…');}};
  function finish(){xhr=null;$('status').textContent='';$('photo').disabled=false;$('wallpaper-name').disabled=false;$('send').disabled=!file;$('progress').hidden=true;$('cancel').hidden=true;}
  request.onload=()=>{finish();let result;try{result=JSON.parse(request.responseText);}catch{error(message('电视返回异常，请重新打开上传页面。','Unexpected TV response. Reopen the upload page.'));return;}
    if(request.status!==201){error(result.message||message('上传失败，请重试。','Upload failed. Please retry.'));return;}
    $('editor').hidden=true;$('success').hidden=false;$('video-preview').pause();
    if(sendingVideo){
      const resultVideo=$('result-video'),status=$('result-status');status.hidden=false;
      status.textContent=message('本机视频预览；电视播放效果请返回电视确认。','Local video preview. Check playback on your TV.');
      resultVideo.onerror=()=>{resultVideo.hidden=true;status.textContent=message('视频已上传；此浏览器无法预览，请返回电视确认播放。','Video uploaded. This browser cannot preview it; check playback on your TV.');};
      resultVideo.src=url;resultVideo.hidden=false;
    }else{$('result').src=url;$('result').hidden=false;}
  };
  request.onerror=()=>{finish();error(message('连接已断开，请保持电视上传页面打开，并检查局域网。','Connection lost. Keep the TV upload screen open and check your local network.'));};
  request.ontimeout=()=>{finish();error(message('上传超时，请检查电视上的结果后重试。','Upload timed out. Check the result on your TV before retrying.'));};
  request.onabort=()=>{finish();$('status').textContent=message('已取消传输；如电视已完成处理，请在壁纸列表查看。','Transfer cancelled. Check the wallpaper list if your TV already finished processing.');};
  request.send(file);
});
$('cancel').addEventListener('click',()=>{if(xhr)xhr.abort();});
$('another').addEventListener('click',()=>{++selection;releasePreview();file=null;$('photo').value='';$('wallpaper-name').value='';updateName();$('naming').hidden=true;$('send').disabled=true;$('placeholder').hidden=false;$('change').hidden=true;$('success').hidden=true;$('result-status').hidden=true;$('editor').hidden=false;$('status').textContent='';$('error').hidden=true;capabilities();});
window.addEventListener('pagehide',()=>{if(xhr)xhr.abort();releasePreview();});
})();
