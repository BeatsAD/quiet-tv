import { flushSync } from 'react-dom';
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

export type PageDirection = 'push' | 'pop';

export function usePageMotion(ref:RefObject<HTMLElement|null>,route:string) {
  const active=useRef<ViewTransition|null>(null);
  const slideDirection=useRef<PageDirection|null>(null);

  useLayoutEffect(()=>{
    const direction=slideDirection.current;
    slideDirection.current=null;
    const node=ref.current,preference=motionPreference();
    if(!direction||!node?.animate||preference.matches)return;
    const offset=direction==='push'?36:-36;
    const animation=node.animate([
      {transform:`translateX(${offset}px)`,opacity:.8},
      {transform:'translateX(0)',opacity:1},
    ],{duration:240,easing:motionEase});
    const reduce=()=>{if(preference.matches)animation.finish();};
    preference.addEventListener('change',reduce);
    return()=>{preference.removeEventListener('change',reduce);animation.cancel();};
  },[route]);

  return (direction:PageDirection|null,update:()=>void)=>{
    active.current?.skipTransition();
    slideDirection.current=null;
    const preference=motionPreference();
    if(!direction||!ref.current||preference.matches||document.hidden){
      update();
      return;
    }
    // Return without waiting for the browser to capture both pages.
    if(direction==='pop'||!document.startViewTransition){
      slideDirection.current=direction;
      update();
      return;
    }

    const html=document.documentElement;
    html.dataset.settingsNavigation=direction;
    const transition=document.startViewTransition(()=>flushSync(update));
    active.current=transition;
    const reduce=()=>{if(preference.matches)transition.skipTransition();};
    preference.addEventListener('change',reduce);
    const clear=()=>{
      preference.removeEventListener('change',reduce);
      if(active.current!==transition)return;
      active.current=null;
      delete html.dataset.settingsNavigation;
    };
    void transition.ready.catch(()=>{});
    void transition.finished.then(clear,clear);
  };
}
