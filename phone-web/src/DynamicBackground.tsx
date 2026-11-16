import { useContext, useEffect, useRef, useState } from 'react';
import type { PhoneClient, Value } from './api';
import { ResourceImage, ResourceImageRefresh } from './widgets';

/** Layout uses TV data; weather uses a sampled native background, never a CSS imitation. */
export function DynamicBackground({client,v,filter,suspended,onProblem,retry=0}:{client:PhoneClient;v:Record<string,Value>;filter:string;suspended:boolean;onProblem?:(message:string)=>void;retry?:number}){
  const root=useRef<HTMLDivElement>(null),video=useRef<HTMLVideoElement>(null);
  const refresh=useContext(ResourceImageRefresh);
  const [visible,setVisible]=useState(!document.hidden),[reduce,setReduce]=useState(()=>matchMedia('(prefers-reduced-motion: reduce)').matches),[failure,setFailure]=useState('');
  const id=String(v.wallpaper||''),key=`${id}:${v.previewKey||''}:${refresh}`,weather=v.wallMode==='weather',dynamic=!weather&&!!v.wallVideo;
  const active=visible&&!suspended&&client.status==='connected';
  useEffect(()=>{setFailure('');},[key,retry]);
  useEffect(()=>{if(!weather)onProblem?.(failure);},[failure,weather,onProblem]);
  useEffect(()=>{
    let intersecting=true;const update=()=>setVisible(!document.hidden&&intersecting),preference=matchMedia('(prefers-reduced-motion: reduce)'),changed=()=>setReduce(preference.matches);
    const observer=new IntersectionObserver(entries=>{intersecting=entries[0]?.isIntersecting??false;update();});if(root.current)observer.observe(root.current);
    document.addEventListener('visibilitychange',update);preference.addEventListener('change',changed);update();
    return()=>{observer.disconnect();document.removeEventListener('visibilitychange',update);preference.removeEventListener('change',changed);};
  },[]);
  useEffect(()=>{if(!active||reduce||!dynamic||failure)return;const node=video.current;if(!node)return;let current=true;node.muted=true;void node.play().catch(()=>{if(current)setFailure('视频预览未播放，请重试');});return()=>{current=false;node.pause();};},[active,reduce,dynamic,key,failure]);
  return <div ref={root} className="tv-background"><div className="tv-background-layer" style={{filter}}>
    {weather?<TelevisionBackground client={client} active={active} reduce={reduce} sceneKey={`${v.weatherFetchedAt}:${v.dayNight}`} retry={retry+refresh} onProblem={onProblem}/>:id&&<ResourceImage className="tv-wallpaper" src={client.resource(`api/preview-image/${encodeURIComponent(id)}?v=${encodeURIComponent(String(v.previewKey||''))}`)} alt="电视背景" onError={()=>setFailure('图片预览未更新，保留上次画面')}/>}
    {dynamic&&!reduce&&!failure&&<video ref={video} key={key} className="tv-wallpaper" src={client.resource(`api/preview-video/${encodeURIComponent(id)}?v=${encodeURIComponent(String(v.previewKey||''))}`)} muted loop playsInline preload={active?'metadata':'none'} aria-label="动态壁纸预览" onError={()=>setFailure('手机暂不支持此视频预览')}/>}
  </div></div>;
}
function TelevisionBackground({client,active,reduce,sceneKey,retry,onProblem}:{client:PhoneClient;active:boolean;reduce:boolean;sceneKey:string;retry:number;onProblem?:(message:string)=>void}){
  const [src,setSrc]=useState(''),last=useRef('');
  useEffect(()=>()=>{if(last.current)URL.revokeObjectURL(last.current);},[]);
  useEffect(()=>{
    if(!active)return;let stopped=false,timer:ReturnType<typeof setTimeout>;const controller=new AbortController();
    const read=async()=>{
      const deadline=setTimeout(()=>controller.abort(),5000);
      try{
        const response=await fetch(client.resource('wallpaper/__current-background'),{credentials:'same-origin',cache:'no-store',signal:controller.signal});if(!response.ok)throw new Error();
        const blob=await response.blob();if(!blob.type.startsWith('image/'))throw new Error();if(stopped)return;
        const next=URL.createObjectURL(blob),previous=last.current;last.current=next;setSrc(next);if(previous)URL.revokeObjectURL(previous);onProblem?.('');
      }catch{if(!stopped)onProblem?.(last.current?'电视背景暂未更新，保留上次画面':'电视背景暂不可用，请返回 QuietTV 桌面后重试');}
      finally{clearTimeout(deadline);if(!stopped&&!reduce&&!controller.signal.aborted)timer=setTimeout(()=>void read(),5000);}
    };
    void read();return()=>{stopped=true;controller.abort();clearTimeout(timer);};
  },[client,active,reduce,sceneKey,retry,onProblem]);
  return src?<img className="tv-wallpaper" src={src} alt="电视当前背景"/>:null;
}
