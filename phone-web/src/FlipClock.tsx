import { Fragment, useEffect, useLayoutEffect, useMemo, useRef, type RefObject } from 'react';

/** Keep one physical leaf mounted through its entire rotation, including its back face. */
function FlipCard({value,tick,active,duration,onLanded}:{value:string;tick:number;active:boolean;duration:number;onLanded?:()=>void}){
  const root=useRef<HTMLSpanElement>(null),upper=useRef<HTMLSpanElement>(null),lower=useRef<HTMLSpanElement>(null),front=useRef<HTMLSpanElement>(null),back=useRef<HTMLSpanElement>(null),leaf=useRef<HTMLSpanElement>(null),shadow=useRef<HTMLSpanElement>(null),frontShade=useRef<HTMLSpanElement>(null),backShade=useRef<HTMLSpanElement>(null);
  const last=useRef({value,tick,active:false}),settle=useRef<(()=>void)|null>(null);
  useEffect(()=>{const motion=matchMedia('(prefers-reduced-motion: reduce)'),changed=()=>{if(motion.matches)settle.current?.();};motion.addEventListener('change',changed);return()=>motion.removeEventListener('change',changed);},[]);
  useLayoutEffect(()=>{
    const before=last.current;last.current={value,tick,active};
    const moving=active&&before.active&&before.value!==value&&tick===before.tick+1&&!matchMedia('(prefers-reduced-motion: reduce)').matches;
    upper.current!.textContent=value;lower.current!.textContent=moving?before.value:value;
    if(!moving)return;
    front.current!.textContent=before.value;back.current!.textContent=value;leaf.current!.hidden=false;shadow.current!.hidden=false;root.current!.dataset.flipping='true';
    const options:KeyframeAnimationOptions={duration,easing:'cubic-bezier(.35,.04,.62,1)',fill:'both'},animations=[
      leaf.current!.animate([{transform:'rotateX(0deg)'},{transform:'rotateX(-180deg)'}],options),
      shadow.current!.animate([{opacity:0},{opacity:1,offset:.5},{opacity:0}],options),
      frontShade.current!.animate([{opacity:0},{opacity:.22,offset:.5},{opacity:0}],options),
      backShade.current!.animate([{opacity:0},{opacity:.22,offset:.5},{opacity:0}],options)
    ];
    let finished=false;const finish=()=>{if(finished)return;finished=true;upper.current!.textContent=value;lower.current!.textContent=value;leaf.current!.hidden=true;shadow.current!.hidden=true;root.current!.dataset.flipping='false';animations.forEach(animation=>animation.cancel());if(settle.current===finish)settle.current=null;};
    settle.current=finish;animations[0].finished.then(()=>{if(!finished){finish();onLanded?.();}},()=>{});return finish;
  },[value,tick,active,duration,onLanded]);
  const half=(glyph:RefObject<HTMLSpanElement|null>,bottom=false,face='')=><span className={`saver-flip-half${bottom?' saver-flip-bottom':''}${face?` saver-flip-${face}`:''}`}><span className="saver-flip-glyph" ref={glyph}/>{face&&<span className="saver-flip-shade" ref={face==='front'?frontShade:backShade}/>}</span>;
  return <span className="saver-flip-card" ref={root} aria-hidden="true"><span className="saver-flip-face">{half(upper)}{half(lower,true)}<span className="saver-flip-shadow" ref={shadow} hidden/><span className="saver-flip-leaf" ref={leaf} hidden>{half(front,false,'front')}{half(back,true,'back')}</span></span><span className="saver-flip-seam"/><span className="saver-flip-hinge left"/><span className="saver-flip-hinge right"/></span>;
}
export function FlipClock({time,tick,active,onLanded}:{time:string;tick:number;active:boolean;onLanded?:()=>void}){
  const last=useRef(time),duration=useMemo(()=>time.length===8&&time.slice(0,5)===last.current.slice(0,5)?260:380,[time]);
  const changed=useMemo(()=>{
    const previous=last.current.split(':');
    return time.split(':').findIndex((value,index)=>value!==previous[index]);
  },[time]);
  useLayoutEffect(()=>{last.current=time;},[time]);
  return <span className="saver-flip-clock" data-seconds={time.length===8} role="img" aria-label={time}>{time.split(':').map((value,index)=><Fragment key={index}>{index>0&&<span className="saver-flip-colon" aria-hidden="true"><i/><i/></span>}<FlipCard value={value} tick={tick} active={active} duration={duration} onLanded={index===changed?onLanded:undefined}/></Fragment>)}</span>;
}
