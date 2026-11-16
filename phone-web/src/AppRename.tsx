import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { ApiError, type PhoneClient, type App } from './api';
import { AppImage } from './widgets';

export function appRenameTarget(hash:string){const params=new URLSearchParams(hash.replace(/^#/,'')),pkg=params.get('appRename');return pkg&&/^[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+$/.test(pkg)?pkg:null;}
export function appName(value:string){return value.trim();}
export function validAppName(value:string){const name=appName(value);return !!name&&Array.from(name).length<=20&&!/[\x00-\x1f\x7f-\x9f\u2028\u2029]/.test(value)&&!Array.from(value).some(char=>char.length===1&&/[\ud800-\udfff]/.test(char));}

export function AppRename({client,id,onSaved,onCancel}:{client:PhoneClient;id:string;onSaved:()=>void;onCancel:()=>void}){
  const item=(client.metadata?.apps as App[]|undefined)?.find(app=>app.id===id),dialog=useRef<HTMLDialogElement>(null),title=useId(),count=useId(),nameId=useId();
  const [name,setName]=useState(item?.name||''),[baseline,setBaseline]=useState(item?.customName||''),[revision,setRevision]=useState(client.frame?.revisions.apps||''),[saving,setSaving]=useState(false),[pending,setPending]=useState<string|null>(null),[error,setError]=useState(''),[review,setReview]=useState(false);
  const submitting=useRef(false),composing=useRef(false),finished=useRef(false),mounted=useRef(true);
  const unavailable=client.status==='ended'||client.restoring||!item?.canRename,conflict=!!item&&(item.customName||'')!==baseline;
  const valid=validAppName(name)||name===item?.originalName;
  const locked=saving||pending!==null&&['saving','checking','offline'].includes(client.sync);
  function complete(){if(!mounted.current||finished.current)return;finished.current=true;onSaved();}
  useLayoutEffect(()=>{const previous=document.activeElement as HTMLElement|null,node=dialog.current!;node.showModal();const resize=()=>{node.style.setProperty('--rename-height',`${window.visualViewport?.height||window.innerHeight}px`);node.style.setProperty('--rename-top',`${window.visualViewport?.offsetTop||0}px`);};resize();window.visualViewport?.addEventListener('resize',resize);window.visualViewport?.addEventListener('scroll',resize);return()=>{node.close();previous?.focus();window.visualViewport?.removeEventListener('resize',resize);window.visualViewport?.removeEventListener('scroll',resize);};},[]);
  useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;};},[]);
  useEffect(()=>{if(pending!==null&&client.sync==='synced'&&item?.name===pending)complete();},[pending,client.sync,item?.name]);
  async function save(){
    if(submitting.current||composing.current||locked||unavailable||conflict||review||!valid||appName(name)===item?.name)return;
    const next=name===item?.originalName?name:appName(name);submitting.current=true;setSaving(true);setPending(next);setError('');
    try{const receipt=await client.command('apps','renameApp',{package:id,expectedAlias:baseline,name:next},revision);if(!receipt.operation?.persisted)throw new Error('名称保存结果待核对');await client.refresh();complete();}
    catch(problem){if(mounted.current){if(problem instanceof ApiError&&problem.status<500)setPending(null);if(problem instanceof ApiError&&problem.status===409)setReview(true);setError(problem instanceof Error?problem.message:'名称保存结果待核对');}await client.refresh();}
    finally{submitting.current=false;if(mounted.current)setSaving(false);}
  }
  function cancel(){if(!locked)onCancel();}
  return <dialog ref={dialog} className="confirmation-dialog app-rename-dialog" aria-labelledby={title} aria-busy={locked} onCancel={event=>{event.preventDefault();cancel();}} onClick={event=>{if(event.target===dialog.current)cancel();}}><h2 id={title}>重命名应用</h2>{item&&<div className="app-rename-identity"><AppImage app={item} client={client}/><strong>{item.name}</strong></div>}<form onSubmit={event=>{event.preventDefault();void save();}}><div className="app-rename-field-heading"><label htmlFor={nameId}>应用名称</label><button type="button" className="text-action app-rename-reset" disabled={locked||unavailable||name===item?.originalName} onClick={()=>{setName(item?.originalName||'');setError('');}}>恢复原名称</button></div><input id={nameId} aria-label="应用名称" aria-describedby={count} value={name} disabled={locked||unavailable} onCompositionStart={()=>{composing.current=true;}} onCompositionEnd={()=>{composing.current=false;}} onChange={event=>{setName(event.target.value);setError('');}} autoComplete="off" enterKeyHint="done"/><div id={count} className="app-rename-meta"><span className="app-rename-original">原名称：{item?.originalName||'应用已不可用'}</span><span className="app-rename-count">{Array.from(appName(name)).length}/20</span></div>{(!valid||error||conflict||unavailable)&&<p className="form-error" role="alert">{unavailable?'应用已不可用或连接已结束，请返回核对':conflict?`电视当前名称为“${item?.name}”，请核对后保存。`:error||'请输入 1–20 个字符，不能包含换行或控制字符'}</p>}{!locked&&(conflict||review)&&!unavailable&&<button type="button" className="text-action" onClick={()=>{setBaseline(item?.customName||'');setRevision(client.frame?.revisions.apps||'');setPending(null);setReview(false);setError('');}}>已核对，保留输入继续修改</button>}<div className="confirmation-dialog-actions"><button type="button" disabled={locked} onClick={cancel}>取消</button><button type="submit" className="confirm-button" disabled={locked||unavailable||conflict||review||!valid||appName(name)===item?.name}>{locked?'正在核对保存…':'保存'}</button></div></form></dialog>;
}
