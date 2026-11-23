import { useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react';
import { createPortal } from 'react-dom';
import { Icon, quickGlyph } from './widgets';
import { QUICK_SETTINGS } from './quickRules';
import { QuickIcon, type QuickIcons, type QuickItem } from './QuickPreview';
type Session={id:string;pointer:number;x:number;y:number;startY:number;grab:number;initial:string[];order:string[];revision:string;active:boolean;timer:number;frame:number;time:number};
type Swipe={id:string;pointer:number;x:number;y:number;base:number;width:number;offset:number;horizontal:boolean};
export function QuickSort({order,revision,catalog,onCommit,onRun,onActions,onRemove,hiddenIds=[],icons,status,soundOn}:{order:string[];hiddenIds?:string[];status?:(id:string)=>string;soundOn?:(id:string)=>boolean|undefined;revision:string;catalog:QuickItem[];icons?:QuickIcons;onCommit:(ids:string[],revision:string)=>void;onRun:(id:string)=>void;onActions:(id:string)=>void;onRemove:(id:string)=>void}){
  const element=useRef<HTMLDivElement>(null),session=useRef<Session|null>(null),current=useRef(order);
  const [local,setLocal]=useState(order),[floating,setFloating]=useState<{id:string;left:number;top:number;width:number}|null>(null),[announcement,setAnnouncement]=useState('');current.current=order;
  const swipe=useRef<Swipe|null>(null),suppressClick=useRef(false);
  const [revealed,setRevealed]=useState(''),[swiping,setSwiping]=useState<{id:string;offset:number}|null>(null);
  useEffect(()=>{if(!session.current?.active)setLocal(order);setRevealed(id=>order.includes(id)?id:'');if(swipe.current&&!order.includes(swipe.current.id)){swipe.current=null;setSwiping(null);}},[order]);
  function title(id:string){return catalog.find(item=>item.id===id)?.title||id;}
  function finish(cancel=false){
    const drag=session.current;if(!drag)return;session.current=null;clearTimeout(drag.timer);cancelAnimationFrame(drag.frame);
    if(element.current?.hasPointerCapture(drag.pointer))element.current.releasePointerCapture(drag.pointer);setFloating(null);
    if(cancel){setLocal(current.current);return;}
    if(drag.active&&drag.initial.join()!==drag.order.join()){onCommit(drag.order,drag.revision);setAnnouncement(`${title(drag.id)}已移到第${drag.order.indexOf(drag.id)+1}位`);}
  }
  useEffect(()=>{const escape=(event:KeyboardEvent)=>{if(event.key==='Escape'&&session.current){event.preventDefault();finish(true);}};window.addEventListener('keydown',escape);return()=>{window.removeEventListener('keydown',escape);const drag=session.current;if(drag){clearTimeout(drag.timer);cancelAnimationFrame(drag.frame);session.current=null;}};},[]);
  function paint(time:number){
    const drag=session.current,list=element.current;if(!drag?.active||!list)return;
    const app=list.closest<HTMLElement>('.quiet-app')!,scroll=app.querySelector<HTMLElement>('.page-scroll')!,bounds=app.getBoundingClientRect(),viewport=scroll.getBoundingClientRect(),row=list.querySelector<HTMLElement>(`[data-quick-id="${drag.id}"]`)!,height=row.getBoundingClientRect().height;
    const elapsed=drag.time?Math.min(32,time-drag.time):16;drag.time=time;
    const edge=44,before=list.getBoundingClientRect();let speed=0;
    if(drag.y<viewport.top+edge&&before.top<viewport.top)speed=-Math.min(1,(viewport.top+edge-drag.y)/edge);
    else if(drag.y>viewport.bottom-edge&&before.bottom>viewport.bottom)speed=Math.min(1,(drag.y-viewport.bottom+edge)/edge);
    if(speed)scroll.scrollTop+=speed*.45*elapsed;
    const rect=list.getBoundingClientRect(),top=Math.max(viewport.top,Math.min(viewport.bottom-height,drag.y-drag.grab)),index=Math.max(0,Math.min(drag.order.length-1,Math.floor((top+height/2-rect.top)/height)));
    if(index!==drag.order.indexOf(drag.id)){const ids=drag.order.filter(id=>id!==drag.id);ids.splice(index,0,drag.id);drag.order=ids;setLocal(ids);}
    setFloating({id:drag.id,left:rect.left-bounds.left,top:top-bounds.top,width:rect.width});drag.frame=requestAnimationFrame(paint);
  }
  function start(event:ReactPointerEvent<HTMLButtonElement>,id:string){
    if(event.button!==0||session.current)return;event.preventDefault();event.stopPropagation();
    swipe.current=null;setSwiping(null);setRevealed('');suppressClick.current=false;
    const row=event.currentTarget.closest<HTMLElement>('[data-quick-id]')!;
    const drag:Session={id,pointer:event.pointerId,x:event.clientX,y:event.clientY,startY:event.clientY,grab:event.clientY-row.getBoundingClientRect().top,initial:[...order],order:[...order],revision,active:false,timer:0,frame:0,time:0};
    session.current=drag;element.current!.setPointerCapture(event.pointerId);drag.timer=window.setTimeout(()=>{if(session.current===drag){drag.active=true;drag.frame=requestAnimationFrame(paint);}},400);
  }
  function move(event:ReactPointerEvent<HTMLDivElement>){const drag=session.current;if(!drag||drag.pointer!==event.pointerId)return;event.preventDefault();event.stopPropagation();drag.y=event.clientY;if(!drag.active&&Math.hypot(event.clientX-drag.x,event.clientY-drag.startY)>8)finish(true);}
  function startSwipe(event:ReactPointerEvent<HTMLDivElement>,id:string){
    if(id===QUICK_SETTINGS||event.button!==0||!event.isPrimary||session.current||swipe.current)return;suppressClick.current=false;
    const width=event.currentTarget.parentElement!.querySelector<HTMLElement>('.quick-swipe-delete')!.getBoundingClientRect().width,base=revealed===id?-width:0;
    if(revealed!==id)setRevealed('');swipe.current={id,pointer:event.pointerId,x:event.clientX,y:event.clientY,base,width,offset:base,horizontal:false};
  }
  function moveSwipe(event:ReactPointerEvent<HTMLDivElement>){
    const gesture=swipe.current;if(!gesture||gesture.pointer!==event.pointerId)return;const dx=event.clientX-gesture.x,dy=event.clientY-gesture.y;
    if(!gesture.horizontal){
      if(Math.abs(dy)>8&&Math.abs(dy)>=Math.abs(dx)){swipe.current=null;setRevealed('');return;}
      if(Math.abs(dx)<=8||Math.abs(dx)<=Math.abs(dy)*1.2)return;gesture.horizontal=true;event.currentTarget.setPointerCapture(event.pointerId);
    }
    event.preventDefault();event.stopPropagation();gesture.offset=Math.max(-gesture.width,Math.min(0,gesture.base+dx));setSwiping({id:gesture.id,offset:gesture.offset});
  }
  function finishSwipe(event:ReactPointerEvent<HTMLDivElement>,cancel=false){
    const gesture=swipe.current;if(!gesture||gesture.pointer!==event.pointerId)return;swipe.current=null;setSwiping(null);
    if(!gesture.horizontal){if(cancel)setRevealed('');return;}
    event.preventDefault();event.stopPropagation();suppressClick.current=true;setRevealed(!cancel&&gesture.offset<-gesture.width/2?gesture.id:'');
    if(event.currentTarget.hasPointerCapture(event.pointerId))event.currentTarget.releasePointerCapture(event.pointerId);
  }
  const app=element.current?.closest<HTMLElement>('.quiet-app');
  return <><div ref={element} className="quick-sort-list" data-dragging={!!floating} onPointerMove={move} onPointerUp={event=>{if(session.current?.pointer===event.pointerId){event.preventDefault();event.stopPropagation();finish();}}} onPointerCancel={()=>finish(true)} onLostPointerCapture={()=>finish(true)} onKeyDown={event=>{if(event.key==='Escape')setRevealed('');}}>{local.map(id=>{
    const open=revealed===id,moving=swiping?.id===id,state=status?.(id);
    return <div key={id} data-quick-id={id} className="quick-swipe-row" data-open={open} data-swiping={moving}>
      {id!==QUICK_SETTINGS&&<button className="quick-swipe-delete" aria-label={`删除${title(id)}`} aria-hidden={!open} tabIndex={open?0:-1} disabled={!open} onClick={()=>{setRevealed('');onRemove(id);}}>删除</button>}
      <div className={`setting-row quick-item quick-swipe-content ${floating?.id===id?'is-placeholder':''}`} style={moving?{transform:`translateX(${swiping.offset}px)`}:undefined} onPointerDown={event=>startSwipe(event,id)} onPointerMove={moveSwipe} onPointerUp={event=>finishSwipe(event)} onPointerCancel={event=>finishSwipe(event,true)} onLostPointerCapture={event=>{if(event.target===event.currentTarget)finishSwipe(event,true);}} onClickCapture={event=>{if(suppressClick.current&&event.detail>0){event.preventDefault();event.stopPropagation();suppressClick.current=false;}}}>
        <QuickIcon glyph={catalog.find(item=>item.id===id)?.glyph||id} icons={icons} fallback={quickGlyph(id)} off={soundOn?.(id)===false} className="row-icon"/>
        <button className="row-title quick-name" onClick={()=>{setRevealed('');onRun(id);}}>{title(id)}{state&&<small>  {state}</small>}{hiddenIds.includes(id)&&<small>  未显示</small>}</button>
        <button className="icon-button muted quick-sort-handle" aria-label={`长按拖动${title(id)}`} onPointerDown={event=>start(event,id)} onClick={event=>event.preventDefault()} onKeyDown={event=>{if(event.key==='Enter'||event.key===' '){event.preventDefault();setRevealed('');onActions(id);}}}><Icon name="HamburgerMenuIcon"/></button>
        <button className="icon-button muted quick-menu" aria-label={`${title(id)}更多操作`} onClick={()=>{setRevealed('');onActions(id);}}><Icon name="DotsHorizontalIcon"/></button>
      </div>
    </div>;
  })}</div>{floating&&app&&createPortal(<div className="setting-row quick-item quick-drag-ghost" aria-hidden style={{left:floating.left,top:floating.top,width:floating.width}}><QuickIcon glyph={catalog.find(item=>item.id===floating.id)?.glyph||floating.id} icons={icons} fallback={quickGlyph(floating.id)} off={soundOn?.(floating.id)===false} className="row-icon"/><span className="row-title">{title(floating.id)}</span><Icon name="HamburgerMenuIcon"/></div>,app)}<span className="quiet-sr-only" aria-live="polite">{announcement}</span></>;
}
