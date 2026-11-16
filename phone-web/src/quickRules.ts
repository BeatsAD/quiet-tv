export const QUICK_BUTTON_LIMIT=8;
export const QUICK_SETTINGS='desktopSettings';
export const isQuickRange=(id:string)=>id==='brightness'||id==='volume';
export const quickButtonCount=(ids:string[])=>ids.filter(id=>!isQuickRange(id)&&id!==QUICK_SETTINGS).length;
export function quickButtons(ids:string[]){const buttons=ids.filter(id=>!isQuickRange(id));if(!buttons.includes(QUICK_SETTINGS))buttons.push(QUICK_SETTINGS);return buttons;}
export const quickOverflow=(ids:string[])=>quickButtons(ids).filter(id=>id!==QUICK_SETTINGS).slice(QUICK_BUTTON_LIMIT);
export const canAddQuick=(ids:string[],id:string,replace='')=>id!==QUICK_SETTINGS&&replace!==QUICK_SETTINGS&&!ids.includes(id)&&(isQuickRange(id)||quickButtonCount(ids)-(replace&&!isQuickRange(replace)?1:0)<QUICK_BUTTON_LIMIT);
export function quickGrid(ids:string[]){let count=0;return quickButtons(ids).filter(id=>id===QUICK_SETTINGS||count++<QUICK_BUTTON_LIMIT);}

export const quickRanges=(ids:string[])=>ids.filter(isQuickRange);
export const quickOrder=(ids:string[])=>[...quickButtons(ids),...quickRanges(ids)];
export function quickRangeState(id:string,value:number,maximum:number,available=true){
  if(id==='volume'&&!available)return {label:'系统控制',position:0,available:false};
  if(!Number.isFinite(value)||!Number.isFinite(maximum)||maximum<=0)return {label:'读取中',position:0,available:false};
  const percent=id==='volume'?Math.round(100*value/maximum):value;
  return {label:`${percent}%`,position:Math.max(0,Math.min(100,id==='brightness'?(value-20)/80*100:percent)),available:true};
}
export type QuickDraft={ids:string[];revision:string;generation:string;submitted:boolean};
export const quickDraftConflict=(draft:QuickDraft|null,revision:string,generation:string)=>!!draft&&(draft.revision!==revision||draft.generation!==generation);
