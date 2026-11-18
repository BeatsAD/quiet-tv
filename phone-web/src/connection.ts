/** A copied connection URL carries an invitation; each browser still receives its own cookie. */
export function rememberConnection(link:string,base:URL){
  const incoming=new URL(link,base),key=new URLSearchParams(incoming.hash.slice(1)).get('connect');
  if(incoming.origin!==base.origin||!key||!key.match(/^[a-f0-9]{64}$/))throw new Error('电视返回的连接网址无效');
  if(base.origin!==location.origin)return;
  const current=new URL(location.href),params=new URLSearchParams(current.hash.slice(1));params.set('connect',key);current.hash=params.toString();
  history.replaceState(history.state,'',current.href);
}
export function clearConnectionRoute(){
  const key=new URLSearchParams(location.hash.slice(1)).get('connect');
  history.replaceState(history.state,'',location.pathname+location.search+(key?'#connect='+encodeURIComponent(key):''));
}
export async function copyConnectionAddress(){
  const current=new URL(location.href),key=new URLSearchParams(current.hash.slice(1)).get('connect');
  if(!key||!key.match(/^[a-f0-9]{64}$/))throw new Error('请先连接电视，再复制连接网址');
  current.hash='connect='+key;const text=current.href;
  if(navigator.clipboard)try{await navigator.clipboard.writeText(text);return;}catch{}
  const previous=document.activeElement,field=document.createElement('textarea');field.value=text;field.readOnly=true;field.style.cssText='position:fixed;top:-9999px';document.body.appendChild(field);
  let copied=false;try{field.select();field.setSelectionRange(0,text.length);copied=document.execCommand('copy');}finally{field.remove();if(previous instanceof HTMLElement)previous.focus();}
  if(!copied)throw new Error('请长按地址栏复制完整连接网址');
}
