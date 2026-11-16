import { Icon, quickGlyph } from './widgets';
import { quickGrid, quickRanges, quickRangeState } from './quickRules';
import type { Value } from './api';
export type QuickItem={id:string;title:string;shortTitle?:string;glyph?:string};
export type QuickIconSpec={path:string;size:number;outline:boolean;solidSun?:boolean};
export type QuickIcons=Record<string,QuickIconSpec>;
export function QuickIcon({glyph,icons,fallback,className=''}:{glyph:string;icons?:QuickIcons;fallback?:string;className?:string}){
  const spec=icons?.[glyph];
  if(!spec)return <Icon name={fallback||'DashboardIcon'} className={className}/>;
  return <svg className={`glyph ${className}`} data-quick-glyph={glyph} viewBox={`0 0 ${spec.size} ${spec.size}`} aria-hidden="true" fill={spec.outline?'none':'currentColor'} stroke={spec.outline?'currentColor':'none'} strokeWidth={spec.outline?1.5:undefined} strokeLinecap="round" strokeLinejoin="round"><path d={spec.path}/>{spec.solidSun&&<circle cx="12" cy="12" r="4.4" fill="currentColor" stroke="none"/>}</svg>;
}
export function QuickPreview({ids,catalog,icons,values,maximum,volumeAvailable}:{ids:string[];catalog:QuickItem[];icons?:QuickIcons;values:Record<string,Value>;maximum:number;volumeAvailable:boolean}){
  return <div className="quick-panel-preview" aria-label="快捷控制面板预览"><strong>快捷控制</strong><div className="quick-preview-grid">{quickGrid(ids).map((id,index)=>{
    const item=catalog.find(item=>item.id===id),toggle=id==='keySounds'||id==='mute',on=toggle&&!!values[id==='keySounds'?'keySounds':'wallSound'],glyph=toggle?(on?'range-speaker-high':'speaker-off'):id==='desktopSettings'?'header-settings':item?.glyph||id;
    return <div key={id||index} data-quick-preview-id={id||undefined} className={`quick-preview-card ${on?'is-on':''} ${!id?'is-empty':''}`}>{id&&<><QuickIcon glyph={glyph} icons={icons} fallback={id==='desktopSettings'?'GearIcon':toggle&&!on?'SpeakerOffIcon':quickGlyph(id)}/><span>{id==='desktopSettings'?'桌面设置':item?.shortTitle||item?.title||id}</span></>}</div>;
  })}</div>{quickRanges(ids).map(id=>{
    const light=id==='brightness',state=quickRangeState(id,Number(values[light?'homeBrightness':'volume']),light?100:maximum,light||volumeAvailable),title=light?'背景明暗':'音量';
    return <div key={id} className="quick-preview-range" data-quick-range={id} data-disabled={!state.available}><span>{title}</span><output>{state.label}</output><div><QuickIcon glyph={light?'range-sun-low':'range-speaker-low'} icons={icons} fallback={light?'SunIcon':'SpeakerQuietIcon'}/><div className="quick-preview-track" role="progressbar" aria-label={title} aria-valuemin={0} aria-valuemax={100} aria-valuenow={state.available?state.position:undefined} aria-valuetext={state.label}><span style={{width:`${state.position}%`}}/></div><QuickIcon glyph={light?'range-sun-high':'range-speaker-high'} icons={icons} fallback={light?'SunIcon':'SpeakerLoudIcon'}/></div></div>;
  })}<span className="quick-preview-phone"><QuickIcon glyph="device-mobile" icons={icons} fallback="DesktopIcon"/>手机设置</span></div>;
}
