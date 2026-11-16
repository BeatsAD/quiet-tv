import { useLayoutEffect, useRef, type RefObject } from 'react';

export const motionEase = 'cubic-bezier(.22,1,.36,1)';
export const motionPreference = () => matchMedia('(prefers-reduced-motion: reduce)');

// A damped spring settles in 460ms, with a small overshoot rather than a bounce.
export const sheetEntrance: Keyframe[] = Array.from({length:37},(_,index)=>{
  const offset=index/36,time=offset*.46,decay=18,frequency=Math.sqrt(420-decay*decay);
  const remaining=Math.exp(-decay*time)*(Math.cos(frequency*time)+decay/frequency*Math.sin(frequency*time));
  return {offset,transform:`translateY(${index===36?0:remaining*100}%)`};
});

/** Only route changes animate; polling, sliders and text entry keep their current view. */
export function useContentMotion(ref:RefObject<HTMLElement|null>,route:string|number,direction:number){
  const previous=useRef(route);
  useLayoutEffect(()=>{
    if(previous.current===route)return;previous.current=route;
    const node=ref.current,preference=motionPreference();
    if(!node?.animate||preference.matches)return;
    const animation=node.animate([{opacity:.35,transform:`translateX(${direction*18}px)`},{opacity:1,transform:'translateX(0)'}],{duration:240,easing:motionEase});
    const reduce=()=>{if(preference.matches)animation.finish();};
    preference.addEventListener('change',reduce);
    return()=>{preference.removeEventListener('change',reduce);animation.cancel();};
  },[route]);
}
