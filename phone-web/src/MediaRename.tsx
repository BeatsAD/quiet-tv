import { useEffect, useRef, useState } from 'react';
import { useOperationFeedback } from './OperationFeedback';
import { ApiError, type PhoneClient } from './api';
import { ResourceImage, fileSize } from './widgets';

export function MediaRename({client,id,onSaved,onCancel}:{client:PhoneClient;id:string;onSaved:()=>void;onCancel:()=>void}){
  const feedback=useOperationFeedback();
  const item=client.metadata?.wallpapers.find(item=>item.id===id);
  const [original,setOriginal]=useState(item?.name||''),[name,setName]=useState(item?.name||''),[saving,setSaving]=useState(false),[pending,setPending]=useState<string|null>(null),[error,setError]=useState(''),[review,setReview]=useState(false);
  const expected=useRef(client.frame?.revisions.catalog||''),mounted=useRef(true),input=useRef<HTMLInputElement>(null),submitting=useRef(false),finished=useRef(false);
  useEffect(()=>{mounted.current=true;input.current?.focus();return()=>{mounted.current=false;};},[]);
  function complete(){if(!mounted.current||finished.current)return;finished.current=true;setPending(null);onSaved();}
  const {connection,receipt}=client.sync;
  const pendingReceipt=receipt.state!=='idle'&&receipt.domain==='catalog'&&receipt.field==='renameMedia';
  const canConfirm=connection.state==='connected'&&!pendingReceipt;
  useEffect(()=>{if(pending&&canConfirm&&item?.name===pending)complete();},[pending,canConfirm,item?.name]);
  const locked=saving||!!pending&&(pendingReceipt||connection.state==='offline'),conflict=!!item&&item.name!==original;
  async function save(){
    if(submitting.current||locked||conflict||review||!item?.canRename)return;
    const next=name.trim();if(!next||next.length>48||/[\x00-\x1f\x7f-\x9f/\\]/.test(name)){setError('请输入 1–48 个字符的名称，不能包含斜线或控制字符');feedback({kind:'error',title:'重命名失败',detail:'请输入有效的素材名称'});return;}
    submitting.current=true;setSaving(true);setPending(next);setError('');
    try{
      const receipt=await client.command('catalog','renameMedia',{id,originalName:original,name:next},expected.current);if(receipt.operation?.renamed!==true)throw new Error('名称保存结果待核对');await client.refresh();
      complete();
    }catch(error){
      if(mounted.current){if(error instanceof ApiError&&error.status<500)setPending(null);if(error instanceof ApiError&&error.status===409)setReview(true);setError(error instanceof Error?error.message:'保存结果待核对');}
      await client.refresh();
    }finally{submitting.current=false;if(mounted.current)setSaving(false);}
  }
  if(!item?.canRename)return <><p className="form-error" role="alert">素材已移除或不支持改名，请重新选择。</p><button className="secondary-button" onClick={onCancel}>返回素材库</button></>;
  return <form className="media-rename-form" onSubmit={event=>{event.preventDefault();void save();}}><div className="media-rename-target"><ResourceImage src={client.resource(`wallpaper/${encodeURIComponent(item.id)}?v=${item.bytes}`)} alt=""/><span><strong>{original}</strong><small>{item.width} × {item.height}  {fileSize(item.bytes)}{item.video&&item.durationMs?`  ${Math.floor(item.durationMs/60000)}:${String(Math.round(item.durationMs/1000)%60).padStart(2,'0')}`:''}</small></span></div><label className="media-name">素材名称<input ref={input} aria-label="素材名称" aria-describedby="rename-count" maxLength={48} value={name} disabled={locked} onChange={event=>{setName(event.target.value);setError('');}} autoComplete="off" enterKeyHint="done"/></label><p id="rename-count" className="character-count">{name.length}/48</p>{(error||conflict)&&<p className="form-error" role="alert">{conflict?`电视当前名称为“${item.name}”，请核对后保存。`:error}</p>}{!locked&&(conflict||review)&&<button type="button" className="text-action" onClick={()=>{setOriginal(item.name);expected.current=client.frame?.revisions.catalog||'';setError('');setPending(null);setReview(false);}}>已核对，保留输入继续修改</button>}<div className="media-rename-actions"><button type="button" className="secondary-button" disabled={locked} onClick={onCancel}>取消</button><button type="submit" className="primary-button" disabled={locked||conflict||review||!name.trim()||name.trim()===original}>{locked?'正在核对保存…':'保存'}</button></div></form>;
}
