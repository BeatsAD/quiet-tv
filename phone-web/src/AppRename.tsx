import { useFormDraft, type FormDrafts } from './formDraft';
import { useEffect, useId, useRef, useState } from 'react';
import { ApiError, type PhoneClient, type App } from './api';
import { AppImage, FormDialog } from './widgets';

export function appRenameTarget(params:URLSearchParams){const pkg=params.get('appRename');return pkg&&/^[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+$/.test(pkg)?pkg:null;}
export function appName(value:string){return value.trim();}
export function validAppName(value:string){const name=appName(value);return !!name&&Array.from(name).length<=20&&!/[\x00-\x1f\x7f-\x9f\u2028\u2029]/.test(value)&&!Array.from(value).some(char=>char.length===1&&/[\ud800-\udfff]/.test(char));}

export function AppRename({client,id,drafts,onSaved,onCancel}:{client:PhoneClient;id:string;drafts:FormDrafts;onSaved:()=>void;onCancel:()=>void}){
  const item=(client.metadata?.apps as App[]|undefined)?.find(app=>app.id===id),count=useId(),nameId=useId();
  const draft=useFormDraft(drafts,'app-name:'+id,()=>({name:item?.name||'',baseline:item?.customName||'',revision:client.frame?.revisions.apps||''}));
  const {name,baseline,revision}=draft.value;
  const setName=(name:string)=>draft.update({...draft.value,name});
  const [saving,setSaving]=useState(false),[pending,setPending]=useState<string|null>(null),[error,setError]=useState(''),[review,setReview]=useState(false);
  const submitting=useRef(false),composing=useRef(false),finished=useRef(false),mounted=useRef(true);
  const unavailable=client.status==='ended'||client.restoring||!item?.canRename,conflict=!!item&&(item.customName||'')!==baseline;
  const valid=validAppName(name)||name===item?.originalName;
  const {connection,receipt}=client.sync;
  const pendingReceipt=receipt.state!=='idle'&&receipt.domain==='apps'&&receipt.field==='renameApp';
  const canConfirm=connection.state==='connected'&&!pendingReceipt;
  const locked=saving||pending!==null&&(pendingReceipt||connection.state==='offline');
  function complete(){if(!mounted.current||finished.current)return;finished.current=true;draft.clear();onSaved();}
  useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;};},[]);
  useEffect(()=>{if(pending!==null&&canConfirm&&item?.name===pending)complete();},[pending,canConfirm,item?.name]);
  async function save(){
    if(submitting.current||composing.current||locked||connection.state!=='connected'||unavailable||conflict||review||!valid||appName(name)===item?.name)return;
    const next=name===item?.originalName?name:appName(name);submitting.current=true;setSaving(true);setPending(next);setError('');
    try{const receipt=await client.command('apps','renameApp',{package:id,expectedAlias:baseline,name:next},revision,false);if(!receipt.operation?.persisted)throw new Error('名称保存结果待核对');await client.refresh();complete();}
    catch(problem){if(mounted.current){if(problem instanceof ApiError&&problem.status<500)setPending(null);if(problem instanceof ApiError&&problem.status===409)setReview(true);setError(problem instanceof Error?problem.message:'名称保存结果待核对');}await client.refresh();}
    finally{submitting.current=false;if(mounted.current)setSaving(false);}
  }
  function cancel(discard=false){if(!locked){if(discard)draft.clear();onCancel();}}
  return <FormDialog title="重命名应用" className="app-rename-dialog" busy={locked} onClose={cancel}>{item&&<div className="app-rename-identity"><AppImage app={item} client={client}/><strong>{item.name}</strong></div>}<form onSubmit={event=>{event.preventDefault();void save();}}><div className="app-rename-field-heading"><label htmlFor={nameId}>应用名称</label><button type="button" className="text-action app-rename-reset" disabled={locked||unavailable||name===item?.originalName} onClick={()=>{setName(item?.originalName||'');setError('');}}>恢复原名称</button></div><input id={nameId} aria-label="应用名称" aria-describedby={count} value={name} autoFocus disabled={locked||unavailable} onCompositionStart={()=>{composing.current=true;}} onCompositionEnd={()=>{composing.current=false;}} onChange={event=>{setName(event.target.value);setError('');}} autoComplete="off" enterKeyHint="done"/><div id={count} className="app-rename-meta"><span className="app-rename-original">原名称：{item?.originalName||'应用已不可用'}</span><span className="app-rename-count">{Array.from(appName(name)).length}/20</span></div>{(!valid||error||conflict||unavailable)&&<p className="form-error" role="alert">{unavailable?'应用已不可用或连接已结束，请返回核对':conflict?`电视当前名称为“${item?.name}”，请核对后保存。`:error||'请输入 1–20 个字符，不能包含换行或控制字符'}</p>}{!locked&&(conflict||review)&&!unavailable&&<button type="button" className="text-action" onClick={()=>{draft.update({...draft.value,baseline:item?.customName||'',revision:client.frame?.revisions.apps||''});setPending(null);setReview(false);setError('');}}>已核对，保留输入继续修改</button>}<div className="confirmation-dialog-actions"><button type="button" disabled={locked} onClick={()=>cancel(true)}>取消</button><button type="submit" className="confirm-button" disabled={locked||connection.state!=='connected'||unavailable||conflict||review||!valid||appName(name)===item?.name}>{locked?'正在核对保存…':'保存'}</button></div></form></FormDialog>;
}
