(()=>{
const $=id=>document.getElementById(id),english=!navigator.language.toLowerCase().startsWith('zh');
const message=(zh,en)=>english?en:zh;
let items=[],xhr=null,running=false,stopped=false,limits=null;
let uploadBase=new URL('.',location.href);
const needsCode=location.pathname==='/';
$('pairing').hidden=!needsCode;$('editor').hidden=needsCode;
if(english){
  document.documentElement.lang='en';document.title='Quiet TV  Upload wallpaper';
  $('editor-title').textContent='Upload wallpaper';$('choose-label').textContent='Choose images and videos (multiple)';
  $('photo').setAttribute('aria-label','Choose wallpaper images and videos (multiple)');$('queue').setAttribute('aria-label','Wallpaper uploads');
  $('send').textContent='Upload and save all';$('cancel').textContent='Stop uploading';$('progress').setAttribute('aria-label','Upload progress');
  $('pair-title').textContent='Connect to TV';document.querySelector('label[for=code]').textContent='Connection code';$('code').placeholder='6 digits';$('connect').textContent='Connect to TV';
}
function error(text){$('error').textContent=text;$('error').hidden=false;}
async function capabilities(){
  limits=null;$('photo').disabled=true;$('send').disabled=true;
  try{const response=await fetch(new URL('info',uploadBase));if(!response.ok)throw Error();const data=await response.json();if(data.purpose!=='wallpaper')throw Error();limits=data;
    $('fileinfo').textContent=message('图片与视频可混合多选。图片最大 ','Select images and videos together. Images up to ')+(data.imageMaxBytes/1024/1024)+message(' MB；视频大小受电视可用空间限制。上传期间请保持电视上传页打开。',' MB; videos are limited by TV storage. Keep the TV upload screen open.');$('photo').disabled=false;
  }catch{error(message('会话不可用，请在电视重新打开上传页面。','Session unavailable. Reopen upload on your TV.'));}
}
if(!needsCode)void capabilities();
$('pair-form').addEventListener('submit',async event=>{
  event.preventDefault();$('pair-error').hidden=true;$('connect').disabled=true;
  try{const response=await fetch(new URL('connect',location.href),{method:'POST',headers:{'Content-Type':'text/plain'},body:$('code').value});const result=await response.json();
    if(!response.ok){$('pair-error').textContent=result.message;$('pair-error').hidden=false;return;}
    uploadBase=new URL(result.path,location.href);$('pairing').hidden=true;$('editor').hidden=false;await capabilities();
  }catch{$('pair-error').textContent=message('连接失败，请保持电视上传页面打开。','Connection failed. Keep the TV upload screen open.');$('pair-error').hidden=false;}
  finally{$('connect').disabled=false;}
});
function render(){
  $('queue').replaceChildren();
  for(const [index,item] of items.entries()){
    const row=document.createElement('li'),label=document.createElement('label'),input=document.createElement('input'),detail=document.createElement('p'),status=document.createElement('p');
    row.dataset.uploadState=item.state;label.textContent=message('素材名称 ','Media name ')+(index+1);input.type='text';input.maxLength=48;input.value=item.name;input.disabled=running||item.state!=='ready';input.setAttribute('aria-label',label.textContent);input.autocomplete='off';
    input.addEventListener('input',()=>{item.name=input.value;});label.append(input);detail.className='detail';detail.textContent=item.file.name+'  '+(item.file.size/1024/1024).toFixed(1)+' MB';status.textContent=item.message;status.setAttribute('role','status');
    row.append(label,detail,status);$('queue').append(row);item.status=status;
  }
  $('photo').disabled=running||!limits;$('send').disabled=running||!items.some(item=>item.state==='ready');$('cancel').hidden=!running;
}
function update(item,state,text){item.state=state;item.message=text;if(item.status){item.status.textContent=text;item.status.parentElement.dataset.uploadState=state;}}
$('photo').addEventListener('change',()=>{
  if(running||!limits)return;const chosen=Array.from($('photo').files);$('photo').value='';if(!chosen.length)return;
  items=chosen.map(file=>{
    const video=file.type.startsWith('video/')||/\.(mp4|mov|mkv|webm|m4v|3gp|3g2|ts|m2ts|mts|mpg|mpeg|vob|flv)$/i.test(file.name);
    const maximum=video?limits.videoMaxBytes:limits.imageMaxBytes;
    const invalid=!file.size?message('文件不能为空','Empty file'):!video&&!/\.(jpe?g|png|webp)$/i.test(file.name)&&!['image/jpeg','image/png','image/webp'].includes(file.type)?message('不支持的图片格式','Unsupported image format'):maximum>0&&file.size>maximum?message('文件超过大小上限或电视可用空间','File exceeds the size limit or available TV storage'):'';
    const name=file.name.replace(/\.[^.]+$/,'').replace(/[\u0000-\u001f\u007f-\u009f/\\]/g,' ').trim().slice(0,48).replace(/[\uD800-\uDBFF]$/,'')||message('我的壁纸','My wallpaper');
    const random=new Uint8Array(16);crypto.getRandomValues(random);const request=Array.from(random,n=>n.toString(16).padStart(2,'0')).join('');
    return {file,name,video,request,state:invalid?'failed':'ready',message:invalid||message('等待上传','Waiting to upload')};
  });$('error').hidden=true;$('status').textContent=message('已选择 ','Selected ')+items.length+message(' 项',' files');$('progress').hidden=true;render();
});
function send(item,index){return new Promise(resolve=>{
  const request=new XMLHttpRequest();xhr=request;
  const extension=item.file.name.match(/\.[a-z0-9]{2,5}$/i);
  request.open('POST',new URL('upload',uploadBase));request.setRequestHeader('X-Wallpaper-Request',item.request);request.setRequestHeader('Content-Type',item.file.type||'application/octet-stream');request.setRequestHeader('X-Wallpaper-Name',encodeURIComponent(item.name.trim()+(extension?extension[0]:'')));request.setRequestHeader('X-Wallpaper-Kind',item.video?'video':'image');request.timeout=item.video?0:90000;
  const prefix=`${index+1}/${items.length}  `;
  request.upload.onprogress=event=>{if(event.lengthComputable){const percent=Math.round(event.loaded/event.total*100);$('progress').setAttribute('aria-valuenow',String(percent));$('progress').querySelector('span').style.width=percent+'%';update(item,'uploading',percent===100?message('正在校验并保存','Validating and saving'):message('正在上传 ','Uploading ')+percent+'%');$('status').textContent=prefix+item.file.name+'  '+item.message;}};
  function finish(){xhr=null;resolve();}
  async function unknown(text){
    try{const response=await fetch(new URL('uploads/'+item.request,uploadBase));if(response.ok){const result=await response.json();if(result.saved||result.id&&!result.pending){update(item,'saved',message('已核对，已保存到素材库','Confirmed saved to library'));finish();return;}}}catch{}
    update(item,'unknown',text);stopped=true;finish();
  }
  request.onload=()=>{let result;try{result=JSON.parse(request.responseText);}catch{unknown(message('保存结果待核对，请在电视素材库确认','Save result unknown. Check the TV library.'));return;}
    if(request.status===201){update(item,result.pending?'pending':'saved',result.pending?message('已传到电视，待电视确认保存','Received by TV; confirm Save on TV'):message('已保存到素材库','Saved to library'));}
    else if(request.status>=400&&request.status<500&&![403,408,409,410].includes(request.status)){update(item,'failed',result.message||message('上传失败','Upload failed'));}
    else{unknown(result.message||message('结果待核对，后续上传已停止','Result unknown. Remaining uploads stopped.'));return;}finish();
  };
  request.onerror=()=>unknown(message('连接中断，保存结果待核对','Connection lost; check the save result on TV'));
  request.ontimeout=()=>unknown(message('响应超时，保存结果待核对','Response timed out; check the save result on TV'));
  request.onabort=()=>unknown(message('已停止传输，保存结果请在电视核对','Transfer stopped; check the save result on TV'));
  request.send(item.file);
});}
$('send').addEventListener('click',async()=>{
  if(running||!items.some(item=>item.state==='ready'))return;
  if(items.some(item=>item.state==='ready'&&(!item.name.trim()||item.name.trim().length>48||/[\u0000-\u001f\u007f-\u009f/\\]/.test(item.name)))){error(message('请填写每项素材名称，最多 48 个字符，不能包含斜线或控制字符','Enter a name for each file (up to 48 characters, no slashes or control characters)'));return;}
  running=true;stopped=false;$('error').hidden=true;$('progress').hidden=false;render();
  try{for(const [index,item] of items.entries()){
    if(stopped)break;if(item.state!=='ready')continue;
    update(item,'uploading',message('正在上传','Uploading'));$('status').textContent=`${index+1}/${items.length}  ${item.file.name}`;$('progress').setAttribute('aria-valuenow','0');$('progress').querySelector('span').style.width='0%';await send(item,index);
  }}finally{running=false;$('progress').hidden=true;render();const count=state=>items.filter(item=>item.state===state).length;
    $('status').textContent=message('已保存 ','Saved ')+count('saved')+message(' 项  失败 ','  Failed ')+count('failed')+message(' 项  待核对 ','  Check ')+count('unknown')+message(' 项','')+(count('pending')?message('  待电视保存 ','  Pending TV save ')+count('pending'):'')+(stopped?message('  后续上传已停止','  Remaining uploads stopped'):'');
  }
});
$('cancel').addEventListener('click',()=>{stopped=true;if(xhr)xhr.abort();});
window.addEventListener('pagehide',()=>{stopped=true;if(xhr)xhr.abort();});
})();
