import { createContext, useContext, useEffect, useLayoutEffect, useRef, useId, useState, type ReactNode, type CSSProperties, type ImgHTMLAttributes } from 'react';
import { cachedResourceImage, discardResourceImage, loadResourceImage } from './ResourceImageCache';
import { motionPreference, sheetEntrance, useContentMotion } from './motion';
import { Icon } from './icons';
export { Icon } from './icons';
import type { PhoneClient, Field, App, Value } from './api';
import { desktopFieldIcons, fieldPresentation } from './presentation';
export function ParentPinInput({label,value,onChange,autoComplete='off'}:{label:string;value:string;onChange:(value:string)=>void;autoComplete?:string}){
  const id=useId(),[visible,setVisible]=useState(false);
  return <div className="parent-pin-field"><label htmlFor={id}>{label}</label><div className="parent-pin-control"><input id={id} className="parent-pin" aria-label={label} type={visible?'text':'password'} inputMode="numeric" pattern="[0-9]{6}" maxLength={6} required autoComplete={autoComplete} value={value} onChange={event=>onChange(event.target.value.replace(/[^0-9]/g,''))}/><button type="button" className="icon-button parent-pin-toggle" aria-label={`${visible?'隐藏':'显示'}${label}`} aria-pressed={visible} aria-controls={id} onPointerDown={event=>event.preventDefault()} onClick={()=>setVisible(previous=>!previous)}><Icon name={visible?'EyeNoneIcon':'EyeOpenIcon'}/></button></div></div>;
}
export function Group({title,action,children}:{title?:string;action?:ReactNode;children:ReactNode}){return <section className="setting-section">{(title||action)&&<div className="section-heading"><h2>{title}</h2>{action}</div>}<div className="setting-group">{children}</div></section>;}
export function Row({title,titleAccessory,value,icon,hint,onClick,children,danger,disabled,navigate=false}:{title:string;titleAccessory?:ReactNode;value?:ReactNode;icon?:string;hint?:string;onClick?:()=>void;children?:ReactNode;danger?:boolean;disabled?:boolean;navigate?:boolean}){
  const content=<>{icon&&<Icon name={icon} className="row-icon"/>}<span className="row-title">{title}{titleAccessory}{hint&&<small className="row-note">{hint}</small>}</span>{value!==undefined&&<span className="row-value">{value}</span>}{children}{onClick&&navigate&&<Icon name="ChevronRightIcon" className="chevron"/>}</>;
  return onClick?<button className={`setting-row ${danger?'danger':''}`} disabled={disabled} onClick={onClick}>{content}</button>:<div className="setting-row">{content}</div>;
}
export function EmptyState({icon,title,description,action,alert=false}:{
  icon:string;title:string;description?:string;action?:ReactNode;alert?:boolean;
}){
  return <div className="empty-state">
    <div className="empty-state-body" role={alert?'alert':'status'}>
      <Icon name={icon}/>
      <p className="empty-state-title">{title}</p>
      {description&&<p className="empty-state-description">{description}</p>}
    </div>
    {action&&<div className="empty-state-actions">{action}</div>}
  </div>;
}
export type StatusTone = 'success'|'progress'|'warning'|'error'|'neutral';
const statusIcons:Record<StatusTone,string> = {
  success:'CheckCircledIcon',progress:'UpdateIcon',warning:'ExclamationTriangleIcon',
  error:'ExclamationTriangleIcon',neutral:'UnplugIcon',
};
export function SettingStatus({label,tone,icon=statusIcons[tone]}:{label:string;tone:StatusTone;icon?:string}){
  return <span className="setting-status" data-tone={tone}><Icon name={icon}/><span>{label}</span></span>;
}
export function Switch({title,checked,onChange,disabled}:{title:string;checked:boolean;onChange:()=>void;disabled?:boolean}){return <button className="switch" role="switch" aria-label={title} aria-checked={checked} disabled={disabled} onClick={onChange}><span/></button>;}
export function ChoiceField({title,options,value,onChange,disabled,hint,icon,hideTitle=false,multiple=false}:{title:string;icon?:string;options:{value:string|number;title:string;icon?:string}[];value:Value;onChange:(value:string|number)=>void;disabled?:boolean;hint?:string;hideTitle?:boolean;multiple?:boolean}){
  const name=useId();
  return <fieldset className={`choice-field ${multiple?'multiple':''}`} disabled={disabled}><legend className="quiet-sr-only">{title}</legend>{!hideTitle&&<span className="choice-title" aria-hidden="true">{icon&&<Icon name={icon} className="row-icon"/>}{title}</span>}<div className="choice-options">{options.map(option=><label className="inline-choice" key={String(option.value)} data-selected={multiple?Array.isArray(value)&&value.includes(option.value):value===option.value}><input className="quiet-sr-only" type={multiple?'checkbox':'radio'} name={name} aria-label={option.title} value={String(option.value)} checked={multiple?Array.isArray(value)&&value.includes(option.value):value===option.value} onChange={()=>onChange(option.value)}/><span className="choice-content" aria-hidden="true"><span>{option.icon&&<Icon name={option.icon} className="choice-option-icon"/>}{option.title}</span>{(multiple?Array.isArray(value)&&value.includes(option.value):value===option.value)&&<Icon name="CheckIcon"/>}</span></label>)}</div>{hint&&<small className="row-note">{hint}</small>}</fieldset>;
}
export function ChoiceList({title,options,value,onChange,disabled,hint}:{title:string;options:{value:string|number;title:string}[];value:Value;onChange:(value:string|number)=>void;disabled?:boolean;hint?:string}){
  const name=useId();
  return <fieldset className="choice-list" disabled={disabled}><legend className="quiet-sr-only">{title}</legend><div className="setting-group">{options.map(option=><label className="setting-row choice-list-option" key={String(option.value)}><input className="quiet-sr-only" type="radio" name={name} value={String(option.value)} checked={value===option.value} onClick={()=>{if(value===option.value)onChange(option.value);}} onChange={()=>onChange(option.value)}/><span className="row-title">{option.title}</span><Icon name="CheckIcon" className={`choice-check ${value===option.value?'selected':''}`}/></label>)}</div>{hint&&<small className="row-note">{hint}</small>}</fieldset>;
}
export function Setting({client,name,title,titleAccessory,onSelect,inline=false,onChoose,maximum}:{client:PhoneClient;name:string;title?:string;titleAccessory?:ReactNode;onSelect:(key:string)=>void;inline?:boolean;onChoose?:(key:string,value:string|number)=>void;maximum?:number}){
  const blocked=client.status!=='connected'||client.sync.receipt.state==='checking';
  const field=client.metadata?.fields[name],f=field&&title?{...field,title}:field;
  const value=client.value(name),presentation=fieldPresentation[name],icon=desktopFieldIcons[name]||presentation?.icon;if(!f||value===undefined)return <Row title={title||f?.title||presentation?.title||name} titleAccessory={titleAccessory} icon={icon} value={name==='wallSound'&&!f?'不可用':client.metadata?'读取待核对':'读取中'} hint={name==='wallSound'&&!f?'当前图片背景不播放声音':f?'尚未读取到电视当前值，请重新读取配置':client.metadata?'设置定义未读取完整，请重新读取电视配置':undefined}/>;
  const parentOff=(name==='showSeconds'||name==='clockStyle')&&!client.value('showTime')||name==='showLunarDate'&&!client.value('showDate');
  if(parentOff)return <Row title={f.title} icon={icon} disabled value={f.type==='select'?f.options?.find(option=>option.value===value)?.title:undefined}>{f.type==='toggle'&&<Switch title={f.title} checked={!!value} disabled onChange={()=>{}}/>}</Row>;
  if(name==='volume'&&f.disabledReason)return <Row title={f.title} icon="SpeakerLoudIcon" value="系统控制" hint={f.disabledReason}/>;
  if(f.type==='range'){
    const max=maximum===undefined?f.max:Math.min(f.max??maximum,maximum);
    return <Range icon={icon} disabled={blocked||max!==undefined&&max===f.min} inline={name==='dockScale'||name==='dockMagnification'} percent={name==='volume'}
      field={{...f,max}} value={Math.min(Number(value),max??Infinity)}
      onChange={value=>client.update(name,value,120)} onEnd={()=>client.flush(name)}/>;
  }
  if(inline&&name!=='boot'&&f.type==='select'&&f.options?.length&&f.options.length<=3)return <ChoiceField title={f.title} icon={icon} options={f.options} value={f.options.some(option=>option.value===value)?value:f.options.some(option=>option.value==='custom')?'custom':value} disabled={blocked||!!f.disabledReason} hint={f.disabledReason} onChange={value=>onChoose?onChoose(name,value):client.update(name,value)}/>;
  const blockedByDailyContent=name==='showHomeQuote'&&client.value('wallMode')==='daily'&&client.value('dailySource')==='movie-daily'&&client.value('showMovieInfo')
    ?'请先关闭影片信息'
    :name==='showMovieInfo'&&client.value('showHomeQuote')?'请先关闭每日格言':undefined;
  const disabledReason=f.disabledReason||blockedByDailyContent;
  return <Row title={f.title} titleAccessory={titleAccessory} icon={icon} hint={disabledReason} value={f.type==='select'?f.options?.find(option=>option.value===value)?.title||String(value):undefined} navigate={f.type==='select'} onClick={f.type==='select'?()=>onSelect(name):undefined} disabled={!!disabledReason}>{f.type==='toggle'&&<Switch title={f.title} checked={!!value} disabled={blocked||!!disabledReason} onChange={()=>client.update(name,!value)}/>}</Row>;
}
export function Range({field:f,value,onChange,onEnd,icon,percent=false,disabled=false,inline=false}:{field:Field;icon?:string;disabled?:boolean;inline?:boolean;value:number;onChange:(value:number)=>void;onEnd:()=>void;percent?:boolean}){
  const divisor=f.divisor||1,display=(number:number)=>percent?`${Math.round(100*number/Math.max(1,Number(f.max)))}%`:`${Number((number/divisor).toFixed(2))}${f.unit||''}`;return <label className={inline?'range-field range-field-inline':'range-field'}><span className="range-heading"><span>{icon&&<Icon name={icon} className="row-icon"/>}{f.title}</span><output>{display(value)}</output></span><input aria-label={f.title} type="range" min={f.min} max={f.max} step={f.step||1} disabled={disabled||!!f.disabledReason} value={value} style={{'--range-percent':`${(value-(f.min||0))/((f.max||100)-(f.min||0))*100}%`} as CSSProperties} onChange={event=>onChange(Number(event.target.value))} onPointerUp={onEnd} onKeyUp={onEnd} onBlur={onEnd}/><span className="range-endpoints"><span>{display(Number(f.min))}</span><span>{display(Number(f.max))}</span></span>{f.disabledReason&&<small>{f.disabledReason}</small>}</label>;
}
export function Sheet({title,nested,confirmation=false,closing,pageKey,direction,onBack,onClose,onExited,toolbar,footer,className='',children}:{title:string;nested:boolean;confirmation?:boolean;closing:boolean;pageKey:string;direction:number;onBack:()=>void;onClose:()=>void;onExited:()=>void;toolbar?:ReactNode;footer?:ReactNode;className?:string;children:ReactNode}){
  const titleId=useId();
  const ref=useRef<HTMLDialogElement>(null),panel=useRef<HTMLDivElement>(null),body=useRef<HTMLDivElement>(null),heading=useRef<HTMLHeadingElement>(null),interrupted=useRef<string|null>(null),exit=useRef(onExited);
  useLayoutEffect(()=>{exit.current=onExited;});
  useLayoutEffect(()=>{const previous=document.activeElement as HTMLElement|null;const dialog=ref.current!;dialog.showModal();return()=>{dialog.close();if(previous?.isConnected)previous.focus({preventScroll:true});};},[]);
  useLayoutEffect(()=>{
    const cancel=confirmation?body.current?.querySelector<HTMLButtonElement>('[data-confirm-cancel]'):null;
    (cancel||heading.current)?.focus({preventScroll:true});
  },[pageKey,confirmation]);
  useLayoutEffect(()=>{
    const node=panel.current!,preference=motionPreference();let cancelled=false;
    if(preference.matches||!node.animate){if(closing)exit.current();return;}
    const start=interrupted.current;interrupted.current=null;
    const frames=closing?[{transform:start||'translateY(0)'},{transform:'translateY(102%)'}]:start?[{transform:start},{transform:'translateY(0)'}]:sheetEntrance;
    const animation=node.animate(frames,{duration:closing?200:460,easing:closing?'cubic-bezier(.4,0,.8,.4)':'linear',fill:'both'});
    const reduce=()=>{if(preference.matches)animation.finish();};preference.addEventListener('change',reduce);
    void animation.finished.then(()=>{if(cancelled)return;if(closing)exit.current();else animation.cancel();}).catch(()=>{});
    return()=>{cancelled=true;interrupted.current=getComputedStyle(node).transform;preference.removeEventListener('change',reduce);animation.cancel();};
  },[closing]);
  useContentMotion(body,pageKey,direction);useContentMotion(heading,pageKey,direction);
  const dismiss=()=>{if(!closing)(nested?onBack:onClose)();};
  // Native dismissal can bypass a cancelled close request; clear the mounted view as well.
  return <dialog ref={ref} aria-labelledby={titleId} className={`bottom-sheet ${className} ${closing?'is-closing':''}`} onClose={event=>{if(!event.currentTarget.open){onClose();onExited();}}} onCancel={event=>{if(event.target!==event.currentTarget)return;event.preventDefault();dismiss();}} onClick={event=>{if(event.target===ref.current)dismiss();}}><div ref={panel} className="sheet-panel" inert={closing}><header className="sheet-heading">{nested&&!confirmation?<button className="icon-button sheet-back" aria-label="返回上一级" onClick={onBack}><Icon name="ChevronLeftIcon"/></button>:<span/>}<h2 ref={heading} id={titleId} tabIndex={-1}>{title}</h2>{nested||confirmation?<span/>:<button className="icon-button sheet-close" aria-label="关闭设置浮窗" onClick={onClose}><Icon name="Cross2Icon"/></button>}</header>{toolbar&&<div className="sheet-toolbar">{toolbar}</div>}<div ref={body} className="quiet-sheet-body">{children}</div>{footer&&<div className="sheet-footer">{footer}</div>}</div></dialog>;
}
export function FormDialog({title,busy=false,onClose,children,actions,className='',confirm=false}:{title:string;busy?:boolean;onClose:()=>void;children:ReactNode;actions?:ReactNode;className?:string;confirm?:boolean}){
  const ref=useRef<HTMLDialogElement>(null),titleId=useId(),descriptionId=useId();
  useLayoutEffect(()=>{
    const previous=document.activeElement as HTMLElement|null;
    const dialog=ref.current!,viewport=window.visualViewport;
    const resize=()=>{
      dialog.style.setProperty('--dialog-height',`${viewport?.height||window.innerHeight}px`);
      dialog.style.setProperty('--dialog-top',`${viewport?.offsetTop||0}px`);
    };
    resize();dialog.showModal();
    viewport?.addEventListener('resize',resize);
    viewport?.addEventListener('scroll',resize);
    return ()=>{
      dialog.close();if(previous?.isConnected)previous.focus({preventScroll:true});
      viewport?.removeEventListener('resize',resize);
      viewport?.removeEventListener('scroll',resize);
    };
  },[]);
  const dismiss=()=>{if(!busy)onClose();};
  return <dialog ref={ref} className={`confirmation-dialog ${className}`} role={confirm?'alertdialog':undefined} aria-labelledby={titleId}
    aria-describedby={confirm?descriptionId:undefined} aria-busy={busy}
    onCancel={event=>{event.preventDefault();dismiss();}} onClick={event=>{if(event.target===ref.current)dismiss();}}>
    <h2 id={titleId}>{title}</h2><div id={descriptionId}>{children}</div>{actions}
  </dialog>;
}
export function ConfirmDialog({title,busy,disabled,confirmLabel='确认删除',busyLabel='正在删除…',destructive=true,onCancel,onConfirm,children}:{title:string;busy:boolean;disabled?:boolean;confirmLabel?:string;busyLabel?:string;destructive?:boolean;onCancel:()=>void;onConfirm:()=>void;children:ReactNode}){
  return <FormDialog title={title} busy={busy} onClose={onCancel} confirm actions={
    <div className="confirmation-dialog-actions">
      <button type="button" autoFocus disabled={busy} onClick={onCancel}>取消</button>
      <button type="button" className={destructive?'danger-button':'confirm-button'} disabled={busy||disabled} onClick={onConfirm}>{busy?busyLabel:confirmLabel}</button>
    </div>
  }>{children}</FormDialog>;
}
export function fileSize(bytes?:number){if(bytes===undefined||bytes<0)return '大小未知';return bytes>=1e6?`${(bytes/1e6).toFixed(1)} MB`:bytes>=1000?`${Math.round(bytes/1000)} KB`:`${bytes} B`;}

export function quickGlyph(id:string){const names:Record<string,string>={control:'MixerHorizontalIcon',settings:'GearIcon',systemSettings:'GearIcon',network:'WifiIcon',bluetooth:'BluetoothIcon',source:'HdmiIcon',keySounds:'KeyboardIcon',mute:'WallpaperSoundIcon',saverSound:'SpeakerLoudIcon',volume:'SpeakerLoudIcon',brightness:'SunIcon',homeBlur:'MixerHorizontalIcon',dockScale:'DashboardIcon',dockMagnification:'PlusIcon',wallpaper:'ImageIcon',original:'HomeIcon',transfer:'FolderSyncIcon',memoShortcut:'NotebookIcon',screensaver:'MonitorPlayIcon',boot:'PowerIcon',child:'ChildIcon',apps:'DashboardIcon',clock:'ClockIcon',weather:'SunIcon'};return names[id]||'DashboardIcon';}

export const ResourceImageRefresh=createContext(0);
type ResourceImageProps=Omit<ImgHTMLAttributes<HTMLImageElement>,'onLoad'|'onError'>&{onLoad?:()=>void;onError?:()=>void};
export function ResourceImage({src,onLoad,onError,...props}:ResourceImageProps){
  const image=useRef<HTMLImageElement>(null),refresh=useContext(ResourceImageRefresh),urls=useRef(new Set<string>()),displayed=useRef(''),callbacks=useRef({onLoad,onError});
  const [ready,setReady]=useState<{source:string;refresh:number;url:string}|null>(null);callbacks.current={onLoad,onError};
  const release=(url:string)=>{URL.revokeObjectURL(url);urls.current.delete(url);};
  useLayoutEffect(()=>{if(displayed.current&&displayed.current!==ready?.url)release(displayed.current);displayed.current=ready?.url||'';},[ready?.url]);
  useLayoutEffect(()=>()=>{for(const url of urls.current)URL.revokeObjectURL(url);urls.current.clear();},[]);
  useLayoutEffect(()=>{
    if(!src||!image.current)return;let current=true,queued=false,pending='',probe:HTMLImageElement|undefined,observer:IntersectionObserver|undefined;
    const accept=(blob:Blob)=>{if(!current)return;pending=URL.createObjectURL(blob);urls.current.add(pending);probe=new Image();probe.onload=()=>{if(!current)return;const url=pending;pending='';setReady({source:src,refresh,url});};probe.onerror=()=>{if(!current)return;release(pending);pending='';discardResourceImage(src);callbacks.current.onError?.();};probe.src=pending;};
    const read=()=>{if(queued)return;queued=true;observer?.disconnect();void loadResourceImage(src,props.fetchPriority==='high').then(accept).catch(()=>{if(current)callbacks.current.onError?.();});};
    const cached=cachedResourceImage(src);
    if(cached)accept(cached);else{observer=new IntersectionObserver(entries=>{if(entries.some(entry=>entry.isIntersecting))read();},{rootMargin:'80px'});observer.observe(image.current);}
    return()=>{current=false;observer?.disconnect();if(probe){probe.onload=null;probe.onerror=null;}if(pending)release(pending);};
  },[src,props.fetchPriority,refresh]);
  const current=!!ready&&ready.source===src&&ready.refresh===refresh;
  return <img {...props} ref={image} src={src?ready?.url:undefined} onLoad={()=>{if(current)callbacks.current.onLoad?.();}} onError={()=>{if(current)callbacks.current.onError?.();}}/>;
}

export function AppImage({app,client}:{app:App;client:PhoneClient}){
  const [failed,setFailed]=useState(false);useEffect(()=>setFailed(false),[app.icon]);
  return app.icon&&!failed?<ResourceImage src={client.resource(app.icon)} alt={`${app.name}图标`} onError={()=>setFailed(true)}/>:<span className="system-app-icon" role="img" aria-label={`${app.name}图标不可用`}><Icon name={app.protected?'GearIcon':'DashboardIcon'}/></span>;
}
