type ImageEntry={source:string;promise:Promise<Blob>;resolve:(blob:Blob)=>void;reject:(error:unknown)=>void;blob?:Blob;controller?:AbortController};
const images=new Map<string,ImageEntry>();
const MAX_BYTES=32*1024*1024,MAX_IMAGES=64;
let bytes=0,active:ImageEntry|null=null,queue:ImageEntry[]=[];

function remove(entry:ImageEntry){if(images.get(entry.source)!==entry)return;images.delete(entry.source);bytes-=entry.blob?.size||0;}
function trim(){while(bytes>MAX_BYTES||images.size>MAX_IMAGES){const oldest=[...images.values()].find(entry=>!!entry.blob);if(!oldest)break;remove(oldest);}}
export function cachedResourceImage(source:string){const entry=images.get(source);if(entry?.blob){images.delete(source);images.set(source,entry);}return entry?.blob;}
export function discardResourceImage(source:string){const entry=images.get(source);if(entry)remove(entry);}
export function clearResourceImages(prefix:string){
  for(const entry of [...images.values()])if(entry.source.startsWith(prefix)){remove(entry);entry.controller?.abort();}
  queue=queue.filter(entry=>{if(!entry.source.startsWith(prefix))return true;entry.reject(new DOMException('Image request cancelled','AbortError'));return false;});
}
// Keep HTTP slots available for settings and uploads on the TV's four-client server.
async function pump(){
  if(active)return;const entry=queue.shift();if(!entry)return;active=entry;
  const controller=new AbortController();entry.controller=controller;const deadline=setTimeout(()=>controller.abort(),10000);
  try{
    const response=await fetch(entry.source,{credentials:'same-origin',cache:'no-store',signal:controller.signal});
    if(!response.ok)throw new Error('图片读取失败');const blob=await response.blob();
    if(!blob.type.startsWith('image/')||!blob.size)throw new Error('图片内容不可用');
    if(images.get(entry.source)===entry){entry.blob=blob;bytes+=blob.size;trim();}entry.resolve(blob);
  }catch(error){remove(entry);entry.reject(error);}finally{clearTimeout(deadline);active=null;void pump();}
}
export function loadResourceImage(source:string,priority=false):Promise<Blob>{
  const previous=images.get(source);
  if(previous){if(priority){const index=queue.indexOf(previous);if(index>0){queue.splice(index,1);queue.unshift(previous);}}cachedResourceImage(source);return previous.promise;}
  let resolve!:(blob:Blob)=>void,reject!:(error:unknown)=>void;
  const promise=new Promise<Blob>((yes,no)=>{resolve=yes;reject=no;}),entry={source,promise,resolve,reject};
  images.set(source,entry);if(priority)queue.unshift(entry);else queue.push(entry);void pump();return promise;
}
