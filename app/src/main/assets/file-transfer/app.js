'use strict';
const $=id=>document.getElementById(id);
let base=/^\/[a-f0-9]{32}\/$/.test(location.pathname)?location.pathname:null;
let current=null,running=false,queue=[];
const size=n=>n>=1048576?(n/1048576).toFixed(1)+' MB':n>=1024?(n/1024).toFixed(1)+' KB':n+' B';
function status(text){$('status').textContent=text;}
async function checked(response){const data=await response.json();if(!response.ok)throw new Error(data.message||'传输未完成');return data;}
async function refresh(){
  if(!base)return;
  try{const data=await checked(await fetch(base+'files',{cache:'no-store'}));$('downloads').replaceChildren();
    for(const file of data.files){const li=document.createElement('li'),name=document.createElement('span'),link=document.createElement('a');name.className='file-name';name.textContent=file.name+'  '+size(file.bytes);link.textContent='下载文件';link.href=base+'file/'+encodeURIComponent(file.id);link.setAttribute('download',file.name);li.append(name,link);$('downloads').append(li);}
    if(!data.files.length)$('downloads').textContent='暂时没有加入下载列表的文件';
  }catch(error){status(error.message);}
}
async function connected(){try{await checked(await fetch(base+'files',{cache:'no-store'}));$('connect').hidden=true;$('workspace').hidden=false;status('已连接电视');await refresh();}catch(error){base=null;status(error.message);}}
$('pair').addEventListener('submit',async event=>{event.preventDefault();const button=event.submitter||$('pair').querySelector('button');button.disabled=true;try{const data=await checked(await fetch('/connect',{method:'POST',body:$('code').value}));base=data.path;$('code').value='';await connected();}catch(error){status(error.message);}finally{button.disabled=false;}});
$('refresh').addEventListener('click',refresh);
$('choose').addEventListener('click',()=>$('files').click());
$('files').addEventListener('change',()=>{for(const file of $('files').files){const li=document.createElement('li'),name=document.createElement('span'),detail=document.createElement('span'),progress=document.createElement('progress');name.className='file-name';name.textContent=file.name;detail.className='detail';detail.textContent=size(file.size)+'  等待发送';progress.max=1;progress.value=0;li.append(name,detail,progress);$('queue').append(li);queue.push({file,detail,progress});}$('files').value='';sendNext();});
function sendNext(){
  if(running||!queue.length||!base)return;running=true;const job=queue.shift();
  if(job.file.size===0||job.file.size>200*1048576){job.detail.textContent='文件为空或超过 200 MB';running=false;sendNext();return;}
  const xhr=new XMLHttpRequest();current=xhr;$('cancel').hidden=false;
  xhr.open('POST',base+'upload');xhr.timeout=600000;xhr.setRequestHeader('Content-Type','application/octet-stream');xhr.setRequestHeader('X-File-Name',encodeURIComponent(job.file.name));
  xhr.upload.onprogress=event=>{job.progress.value=event.loaded/job.file.size;job.detail.textContent='正在发送  '+size(event.loaded)+' / '+size(job.file.size);};
  let result='发送失败';xhr.onload=()=>{if(xhr.status>=200&&xhr.status<300){result='已发送到电视';job.progress.value=1;}else{try{result=JSON.parse(xhr.responseText).message;}catch(error){result='传输已结束，请在电视重新开启';}}};xhr.onerror=()=>{result='网络中断，请重新连接后重试';};xhr.ontimeout=()=>{result='传输超时';};xhr.onabort=()=>{result='已取消';};
  xhr.onloadend=()=>{job.detail.textContent=result;current=null;running=false;$('cancel').hidden=true;if(result!=='已发送到电视'){const retry=document.createElement('button');retry.className='secondary';retry.textContent='重试';retry.onclick=()=>{retry.remove();queue.push(job);sendNext();};job.detail.parentNode.append(retry);}sendNext();};xhr.send(job.file);
}
$('cancel').addEventListener('click',()=>{if(current)current.abort();});
window.addEventListener('pagehide',()=>{queue=[];if(current)current.abort();});
if(base)connected();
