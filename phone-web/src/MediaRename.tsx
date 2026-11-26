import { useFormDraft, type FormDrafts } from './formDraft';
import { useEffect, useRef, useState } from 'react';
import { ApiError, type PhoneClient } from './api';
import { EmptyState, FormDialog, ResourceImage, fileSize } from './widgets';

export function MediaRename({client,id,drafts,onSaved,onCancel}:{client:PhoneClient;id:string;drafts:FormDrafts;onSaved:()=>void;onCancel:()=>void}){
  const item=client.metadata?.wallpapers.find(item=>item.id===id);
  const draft=useFormDraft(drafts,'media-name:'+id,()=>({original:item?.name||'',name:item?.name||'',revision:client.frame?.revisions.catalog||''}));
  const {original,name,revision}=draft.value;
  const [saving,setSaving]=useState(false),[pending,setPending]=useState<string|null>(null),[error,setError]=useState(''),[review,setReview]=useState(false);
  const mounted=useRef(true),input=useRef<HTMLInputElement>(null),submitting=useRef(false),finished=useRef(false);
  useEffect(()=>{mounted.current=true;input.current?.focus();return()=>{mounted.current=false;};},[]);
  function complete(){if(!mounted.current||finished.current)return;finished.current=true;draft.clear();setPending(null);onSaved();}
  const {connection,receipt}=client.sync;
  const pendingReceipt=receipt.state!=='idle'&&receipt.domain==='catalog'&&receipt.field==='renameMedia';
  const canConfirm=connection.state==='connected'&&!pendingReceipt;
  useEffect(()=>{if(pending&&canConfirm&&item?.name===pending)complete();},[pending,canConfirm,item?.name]);
  const locked=saving||!!pending&&(pendingReceipt||connection.state==='offline'),conflict=!!item&&item.name!==original;
  async function save(){
    if(submitting.current||locked||connection.state!=='connected'||conflict||review||!item?.canRename)return;
    const next=name.trim();if(!next||next.length>48||/[\x00-\x1f\x7f-\x9f/\\]/.test(name)){setError('请输入 1–48 个字符的名称，不能包含斜线或控制字符');return;}
    submitting.current=true;setSaving(true);setPending(next);setError('');
    try{
      const receipt=await client.command('catalog','renameMedia',{id,originalName:original,name:next},revision,false);if(receipt.operation?.renamed!==true)throw new Error('名称保存结果待核对');await client.refresh();
      complete();
    }catch(error){
      if(mounted.current){if(error instanceof ApiError&&error.status<500)setPending(null);if(error instanceof ApiError&&error.status===409)setReview(true);setError(error instanceof Error?error.message:'保存结果待核对');}
      await client.refresh();
    }finally{submitting.current=false;if(mounted.current)setSaving(false);}
  }
  if(!item?.canRename)return <FormDialog title="重命名素材" onClose={onCancel}>
    <EmptyState icon="ImageIcon" title="素材已移除或不支持改名" description="请重新选择。"
      action={<button type="button" className="secondary-button" onClick={onCancel}>返回素材库</button>} alert/>
  </FormDialog>;
  return <FormDialog title="重命名素材" busy={locked} onClose={onCancel}><form className="media-rename-form" onSubmit={event=>{event.preventDefault();void save();}}><div className="media-rename-target"><ResourceImage src={client.resource(`wallpaper/${encodeURIComponent(item.id)}?v=${item.bytes}`)} alt=""/><span><strong>{original}</strong><small>{item.width} × {item.height}  {fileSize(item.bytes)}{item.video&&item.durationMs?`  ${Math.floor(item.durationMs/60000)}:${String(Math.round(item.durationMs/1000)%60).padStart(2,'0')}`:''}</small></span></div><label className="media-name">素材名称<input ref={input} aria-label="素材名称" aria-describedby="rename-count" autoFocus maxLength={48} value={name} disabled={locked} onChange={event=>{draft.update({...draft.value,name:event.target.value});setError('');}} autoComplete="off" enterKeyHint="done"/></label><p id="rename-count" className="character-count">{name.length}/48</p>{(error||conflict)&&<p className="form-error" role="alert">{conflict?`电视当前名称为“${item.name}”，请核对后保存。`:error}</p>}{!locked&&(conflict||review)&&<button type="button" className="text-action" onClick={()=>{draft.update({...draft.value,original:item.name,revision:client.frame?.revisions.catalog||''});setError('');setPending(null);setReview(false);}}>已核对，保留输入继续修改</button>}<div className="confirmation-dialog-actions"><button type="button" disabled={locked} onClick={()=>{draft.clear();onCancel();}}>取消</button><button type="submit" className="confirm-button" disabled={locked||connection.state!=='connected'||conflict||review||!name.trim()||name.trim()===original}>{locked?'正在核对保存…':'保存'}</button></div></form></FormDialog>;
}
