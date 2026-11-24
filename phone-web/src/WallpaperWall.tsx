import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import type { PhoneClient } from './api';
import { motionPreference } from './motion';
import { ResourceImage } from './widgets';

const FLIP_INTERVAL_MS=5000;

export function wallpaperWallIds(client:PhoneClient){
  return (client.metadata?.wallpapers||[])
    .filter(item=>item.id.startsWith('user-')&&item.kind==='image'&&!item.video)
    .map(item=>item.id);
}

export function wallpaperWallGrid(count:number){
  const columns=count<=2?1:count<=6?2:count<=12?3:4;
  const rows=count===0?0:count<=4?1:count<=9?2:count<=16?3:4;
  return {columns,rows};
}

function shuffled(values:string[]){
  const result=values.slice();
  for(let i=result.length-1;i>0;i--){
    const j=Math.floor(Math.random()*(i+1));
    [result[i],result[j]]=[result[j],result[i]];
  }
  return result;
}

export function WallpaperWallPreview({client,source,active,children}:{client:PhoneClient;source:string;active:boolean;children:ReactNode}){
  const ids=useMemo(()=>source?source.split(','):[],[source]);
  const [failed,setFailed]=useState<string[]>([]);
  const available=ids.filter(id=>!failed.includes(id));
  if(!available.length)return <div className="saver-wall-empty">{ids.length?'壁纸预览无法读取':'上传静态壁纸后即可预览'}</div>;
  return <WallGrid key={available.join(',')} ids={available} client={client} active={active}
    onError={id=>setFailed(previous=>previous.includes(id)?previous:[...previous,id])}>{children}</WallGrid>;
}

function WallGrid({ids,client,active,onError,children}:{ids:string[];client:PhoneClient;active:boolean;onError:(id:string)=>void;children:ReactNode}){
  const {columns,rows}=wallpaperWallGrid(ids.length),size=columns*rows;
  const [initial]=useState(()=>shuffled(ids));
  const [cells,setCells]=useState(()=>initial.slice(0,size));
  const unseen=useRef(initial.slice(size));
  const nextFlipAt=useRef(0);
  const [next,setNext]=useState<{cell:number;id:string;ready:boolean;flipping:boolean}|null>(null);
  const [loaded,setLoaded]=useState<string[]>([]);
  const allReady=cells.every(id=>loaded.includes(id));
  useEffect(()=>{
    if(!active){nextFlipAt.current=0;setNext(null);return;}
    if(next||!allReady||ids.length<=size)return;
    if(!nextFlipAt.current)nextFlipAt.current=performance.now()+FLIP_INTERVAL_MS;
    if(!unseen.current.length)unseen.current=shuffled(ids.filter(id=>!cells.includes(id)));
    setNext({cell:Math.floor(Math.random()*size),id:unseen.current[0],ready:false,flipping:false});
  },[active,allReady,cells,ids,next,size]);

  const finish=useCallback(()=>{
    if(!next||unseen.current[0]!==next.id)return;
    const updated=cells.slice();
    updated[next.cell]=next.id;
    unseen.current.shift();
    setCells(updated);
    setNext(null);
    setLoaded(updated);
  },[cells,next]);

  useEffect(()=>{
    if(!active||!next?.ready)return;
    const preference=motionPreference();
    if(next.flipping){
      const settle=()=>{if(preference.matches)finish();};
      preference.addEventListener('change',settle);
      settle();
      return()=>preference.removeEventListener('change',settle);
    }
    const timer=setTimeout(()=>{
      nextFlipAt.current=performance.now()+FLIP_INTERVAL_MS;
      if(preference.matches)finish();
      else setNext({...next,flipping:true});
    },Math.max(0,nextFlipAt.current-performance.now()));
    return()=>clearTimeout(timer);
  },[active,next,finish]);

  return <><div className="saver-wall-grid" style={{gridTemplateColumns:`repeat(${columns}, minmax(0, 1fr))`,gridTemplateRows:`repeat(${rows}, minmax(0, 1fr))`}}>
    {cells.map((id,index)=>{
      const replacement=next?.cell===index?next:null;
      const animated=active&&replacement?.flipping;
      const images=replacement?[id,replacement.id]:[id];
      return <div key={index} className={`saver-wall-cell${animated?' flipping':''}`} onAnimationEnd={event=>{if(animated&&event.animationName==='saver-wall-in')finish();}}>
        {images.map(image=><ResourceImage key={image} src={client.resource(`wallpaper/${encodeURIComponent(image)}`)} alt=""
          className={image===id?'':'saver-wall-next'}
          onLoad={()=>image===id?setLoaded(previous=>previous.includes(id)?previous:[...previous,id]):
            setNext(current=>current?.id===image&&!current.ready?{...current,ready:true}:current)}
          onError={()=>onError(image)}/>)}
      </div>;
    })}
  </div>{allReady&&children}</>;
}
