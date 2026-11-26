import { useEffect, useRef, useState, type PointerEvent } from 'react';
import { Icon } from './icons';

type Drag = {
  id:string; pointer:number; x:number; y:number; startY:number; grab:number;
  initial:string[]; order:string[]; revision:string; active:boolean; timer:number; frame:number; time:number;
};
type Floating = {id:string; left:number; top:number; width:number; height:number};

export function useRowSort(order:string[],revision:string,onCommit:(order:string[],revision:string)=>void,title:(id:string)=>string){
  const element=useRef<HTMLDivElement>(null),session=useRef<Drag|null>(null),current=useRef(order);
  const [local,setLocal]=useState(order),[floating,setFloating]=useState<Floating|null>(null),[announcement,setAnnouncement]=useState('');
  current.current=order;

  function finish(cancel=false){
    const drag=session.current;
    if(!drag)return;
    session.current=null;clearTimeout(drag.timer);cancelAnimationFrame(drag.frame);
    if(element.current?.hasPointerCapture(drag.pointer))element.current.releasePointerCapture(drag.pointer);
    setFloating(null);
    if(cancel){setLocal(current.current);return;}
    if(drag.active&&drag.initial.join()!==drag.order.join()){
      onCommit(drag.order,drag.revision);
      setAnnouncement(`${title(drag.id)}已移到第${drag.order.indexOf(drag.id)+1}位`);
    }
  }

  useEffect(()=>{
    const drag=session.current;
    if(drag&&(drag.revision!==revision||drag.initial.join()!==order.join()))finish(true);
    else if(!drag?.active)setLocal(order);
  },[order,revision]);
  useEffect(()=>{
    const escape=(event:KeyboardEvent)=>{if(event.key==='Escape'&&session.current){event.preventDefault();finish(true);}};
    window.addEventListener('keydown',escape);
    return ()=>{
      window.removeEventListener('keydown',escape);
      const drag=session.current;
      if(drag){clearTimeout(drag.timer);cancelAnimationFrame(drag.frame);session.current=null;}
    };
  },[]);

  function paint(time:number){
    const drag=session.current,list=element.current;
    if(!drag?.active||!list)return;
    const rows=Array.from(list.querySelectorAll<HTMLElement>(':scope > [data-sort-id]'));
    const row=rows.find(item=>item.dataset.sortId===drag.id);
    const app=list.closest<HTMLElement>('.quiet-app'),scroll=app?.querySelector<HTMLElement>('.page-scroll');
    if(!row||!app||!scroll){finish(true);return;}
    const height=row.getBoundingClientRect().height,bounds=app.getBoundingClientRect(),viewport=scroll.getBoundingClientRect();
    const elapsed=drag.time?Math.min(32,time-drag.time):16;
    drag.time=time;
    const edge=44,before=list.getBoundingClientRect();
    let speed=0;
    if(drag.y<viewport.top+edge&&before.top<viewport.top)speed=-Math.min(1,(viewport.top+edge-drag.y)/edge);
    else if(drag.y>viewport.bottom-edge&&before.bottom>viewport.bottom)speed=Math.min(1,(drag.y-viewport.bottom+edge)/edge);
    if(speed)scroll.scrollTop+=speed*.45*elapsed;
    const rect=list.getBoundingClientRect(),top=Math.max(viewport.top,Math.min(viewport.bottom-height,drag.y-drag.grab));
    // Compare row centers so multiline application rows can be sorted alongside shorter rows.
    const center=top+height/2;
    const index=rows.filter(item=>{
      if(item===row)return false;
      const target=item.getBoundingClientRect();
      return target.top+target.height/2<=center;
    }).length;
    if(index!==drag.order.indexOf(drag.id)){
      const ids=drag.order.filter(id=>id!==drag.id);
      ids.splice(index,0,drag.id);drag.order=ids;setLocal(ids);
    }
    setFloating({id:drag.id,left:rect.left-bounds.left,top:top-bounds.top,width:rect.width,height});
    drag.frame=requestAnimationFrame(paint);
  }

  function start(event:PointerEvent<HTMLButtonElement>,id:string){
    const list=element.current,row=event.currentTarget.closest<HTMLElement>('[data-sort-id]');
    if(event.button!==0||session.current||!list||!row)return;
    event.preventDefault();event.stopPropagation();
    const drag:Drag={id,pointer:event.pointerId,x:event.clientX,y:event.clientY,startY:event.clientY,grab:event.clientY-row.getBoundingClientRect().top,
      initial:[...order],order:[...order],revision,active:false,timer:0,frame:0,time:0};
    session.current=drag;list.setPointerCapture(event.pointerId);
    drag.timer=window.setTimeout(()=>{if(session.current===drag){drag.active=true;drag.frame=requestAnimationFrame(paint);}},400);
  }
  function move(event:PointerEvent<HTMLDivElement>){
    const drag=session.current;
    if(!drag||drag.pointer!==event.pointerId)return;
    event.preventDefault();event.stopPropagation();drag.y=event.clientY;
    if(!drag.active&&Math.hypot(event.clientX-drag.x,event.clientY-drag.startY)>8)finish(true);
  }
  return {order:local,floating,announcement,host:element.current?.closest<HTMLElement>('.quiet-app'),start,
    listProps:{ref:element,'data-dragging':!!floating,onPointerMove:move,
      onPointerUp:(event:PointerEvent<HTMLDivElement>)=>{if(session.current?.pointer===event.pointerId){event.preventDefault();event.stopPropagation();finish();}},
      onPointerCancel:()=>finish(true),onLostPointerCapture:()=>finish(true)}};
}

export function RowSortHandle({title,disabled,onStart,onActions,className=''}:{
  title:string;disabled?:boolean;onStart:(event:PointerEvent<HTMLButtonElement>)=>void;onActions:()=>void;className?:string;
}){
  return <button type="button" className={`icon-button muted row-sort-handle ${className}`} disabled={disabled}
    aria-label={`长按拖动${title}`} onPointerDown={onStart} onClick={event=>event.preventDefault()}
    onKeyDown={event=>{if(event.key==='Enter'||event.key===' '){event.preventDefault();onActions();}}}>
    <Icon name="HamburgerMenuIcon"/>
  </button>;
}
