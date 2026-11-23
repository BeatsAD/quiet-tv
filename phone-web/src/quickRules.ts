export const QUICK_BUTTON_LIMIT=8;
export const QUICK_RANGE_LIMIT=2;
export const QUICK_SETTINGS='desktopSettings';
export const QUICK_RANGES=['brightness','volume','homeBlur','dockScale','dockMagnification'];
export const isQuickRange=(id:string)=>QUICK_RANGES.includes(id);
export const quickButtonCount=(ids:string[])=>ids.filter(id=>!isQuickRange(id)&&id!==QUICK_SETTINGS).length;
export function quickButtons(ids:string[]){const buttons=ids.filter(id=>!isQuickRange(id));if(!buttons.includes(QUICK_SETTINGS))buttons.push(QUICK_SETTINGS);return buttons;}
export const quickOverflow=(ids:string[])=>quickButtons(ids).filter(id=>id!==QUICK_SETTINGS).slice(QUICK_BUTTON_LIMIT);
export const canAddQuick=(ids:string[],id:string,replace='')=>id!==QUICK_SETTINGS&&replace!==QUICK_SETTINGS&&!ids.includes(id)&&(isQuickRange(id)?quickRanges(ids).length-(isQuickRange(replace)&&ids.includes(replace)?1:0)<QUICK_RANGE_LIMIT:quickButtonCount(ids)-(replace&&!isQuickRange(replace)?1:0)<QUICK_BUTTON_LIMIT);
export function quickGrid(ids:string[]){let count=0;return quickButtons(ids).filter(id=>id===QUICK_SETTINGS||count++<QUICK_BUTTON_LIMIT);}

export const quickRanges=(ids:string[])=>ids.filter(isQuickRange);
export const visibleQuickRanges=(ids:string[])=>quickRanges(ids).slice(0,QUICK_RANGE_LIMIT);
export const quickChangeAllowed=(before:string[],next:string[])=>quickButtonCount(next)<=QUICK_BUTTON_LIMIT
  &&(quickRanges(next).length<=QUICK_RANGE_LIMIT||quickRanges(next).every(id=>before.includes(id)));
export const quickOrder=(ids:string[])=>[...quickButtons(ids),...quickRanges(ids)];
export function quickRangeState(id:string,value:number,maximum:number,available=true){
  if(!available)return {label:id==='volume'?'系统控制':'未开启聚焦放大',position:0,available:false};
  if(!Number.isFinite(value)||!Number.isFinite(maximum)||maximum<=0)return {label:'读取中',position:0,available:false};
  const minimum=id==='brightness'?20:id==='dockScale'?70:id==='dockMagnification'?100:0;
  const limit=id==='dockScale'?130:id==='dockMagnification'?160:maximum;
  const percent=id==='volume'?Math.round(100*value/maximum):value;
  return {label:`${percent}%`,position:Math.max(0,Math.min(100,(value-minimum)/(limit-minimum)*100)),available:true};
}
export type QuickDraft={ids:string[];revision:string;generation:string;submitted:boolean};
export const quickDraftConflict=(draft:QuickDraft|null,revision:string,generation:string)=>!!draft&&(draft.revision!==revision||draft.generation!==generation);
