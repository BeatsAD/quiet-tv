export function connectionRoute(){
  const params=new URLSearchParams(location.search);
  new URLSearchParams(location.hash.slice(1)).forEach((value,key)=>{if(!params.has(key))params.set(key,value);});
  return params;
}

export function clearConnectionRoute(){
  history.replaceState(history.state,'',location.pathname);
}

export function clearLegacyConnectionKey(){
  const params=new URLSearchParams(location.hash.slice(1));
  if(!params.has('connect'))return;
  params.delete('connect');
  const route=params.toString();
  history.replaceState(history.state,'',location.pathname+location.search+(route?'#'+route:''));
}

export async function copyConnectionAddress(){
  await copyText(new URL('/',location.href).href,'请长按地址栏复制设置网址');
}

export async function copyText(text:string,failureMessage:string){
  if(navigator.clipboard)try{await navigator.clipboard.writeText(text);return;}catch{}
  const previous=document.activeElement,field=document.createElement('textarea');field.value=text;field.readOnly=true;field.style.cssText='position:fixed;top:-9999px';document.body.appendChild(field);
  let copied=false;try{field.select();field.setSelectionRange(0,text.length);copied=document.execCommand('copy');}finally{field.remove();if(previous instanceof HTMLElement)previous.focus();}
  if(!copied)throw new Error(failureMessage);
}
