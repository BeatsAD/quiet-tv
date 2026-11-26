import { useState } from 'react';
import type { Field } from './api';
import { Icon } from './widgets';

export function rotationLabel(seconds:number){
  return seconds>=60&&seconds%60===0?`${seconds/60} 分钟`:`${seconds} 秒`;
}

export function RotationInterval({field,current,secondsSupported,disabled,onApply}:{
  field:Field;current:number;secondsSupported:boolean;disabled:boolean;onApply:(value:number)=>void;
}){
  const options=(field.options||[]).filter(option=>typeof option.value==='number');
  const [minutes,setMinutes]=useState(!secondsSupported||current>=60&&current%60===0);
  const [custom,setCustom]=useState(()=>!options.some(option=>option.value===current));
  const [draft,setDraft]=useState<string|null>(null);
  const presets=options.filter(option=>!secondsSupported||((Number(option.value)>=60)===minutes));
  const multiplier=secondsSupported&&minutes?60:1;
  const minimum=minutes?1:field.min??3,maximum=secondsSupported?Math.floor((field.max??86400)/multiplier):1440;
  const amount=current%multiplier===0?String(current/multiplier):'';
  const input=draft??amount;
  const number=Number(input);
  const valid=/^\d+$/.test(input)&&Number.isInteger(number)&&number>=minimum&&number<=maximum;
  const unit=minutes?'分钟':'秒';
  function changeUnit(next:boolean){if(next!==minutes){setMinutes(next);setCustom(false);setDraft(null);}}
  return <div className="rotation-interval-editor">
    {secondsSupported&&<div className="rotation-units" role="group" aria-label="时间单位">
      <button type="button" aria-pressed={!minutes} disabled={disabled} onClick={()=>changeUnit(false)}>秒</button>
      <button type="button" aria-pressed={minutes} disabled={disabled} onClick={()=>changeUnit(true)}>分钟</button>
    </div>}
    <div className="rotation-presets" role="group" aria-label="预设间隔">
      {presets.map(option=>{
        const selected=!custom&&option.value===current;
        return <button key={option.value} type="button" className="secondary-button" aria-pressed={selected} disabled={disabled}
          onClick={()=>onApply(Number(option.value))}>{option.title}{selected&&<Icon name="CheckIcon"/>}</button>;
      })}
    </div>
    <button type="button" className="rotation-custom-choice" aria-expanded={custom} disabled={disabled}
      onClick={()=>setCustom(!custom)}>自定义{custom&&<Icon name="CheckIcon"/>}</button>
    {custom&&<form onSubmit={event=>{event.preventDefault();if(valid&&!disabled)onApply(number*multiplier);}}>
      <label className="rotation-number">
        <input aria-label={`自定义轮换间隔（${unit}）`} type="text" inputMode="numeric" maxLength={String(maximum).length}
          required value={input} disabled={disabled} onChange={event=>setDraft(event.target.value)}/>
        <span aria-hidden="true">{unit}</span>
      </label>
      <p className={input&&!valid?'form-error':'section-note'}>{input&&!valid?`请输入 ${minimum}–${maximum} 的整数${unit}`:`最短 ${minimum} ${unit}`}</p>
      <button type="submit" className="primary-button" disabled={disabled||!valid}>应用间隔</button>
    </form>}
  </div>;
}
