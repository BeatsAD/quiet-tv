import { useOperationFeedback } from './OperationFeedback';
import { useEffect, useRef, useState } from 'react';
import { ApiError, PhoneClient, UploadError } from './api';
import { mediaLabel, staticWallpaperAccept, wallpaperAccept, wallpaperProblem } from './media';
import { fileSize } from './widgets';

type Item={invalid:boolean;file:File;name:string;state:'ready'|'uploading'|'saved'|'failed'|'cancelled'|'unknown';percent:number;message:string};
type Progress={name:string;percent:number;message:string};
export function WallpaperUpload({files,client,target,onProgress,onCancel}:{files:File[];client:PhoneClient;target:string;onProgress:(value:Progress|null)=>void;onCancel:(cancel:(()=>Promise<void>)|null)=>void}){
  const wall=target==='wall',accept=wall?staticWallpaperAccept:wallpaperAccept;
  const feedback=useOperationFeedback(),picker=useRef<HTMLInputElement>(null);
  function item(file:File):Item{const problem=wallpaperProblem(file,client.metadata?.uploadLimits as Record<string,number>,wall);return {file,invalid:!!problem,name:mediaLabel(file.name),state:problem?'failed':'ready',percent:0,message:problem||'等待上传'};}
  const [items,setItems]=useState<Item[]>(()=>files.map(item));
  const [selected,setSelected]=useState(0),[running,setRunning]=useState(false),[summary,setSummary]=useState('');
  const stopped=useRef(false),mounted=useRef(true),cancel=useRef<(()=>Promise<void>)|null>(null),inFlight=useRef(false);
  useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;stopped.current=true;void cancel.current?.().catch(()=>{});};},[]);
  function update(index:number,value:Partial<Item>){if(mounted.current)setItems(previous=>previous.map((item,i)=>i===index?{...item,...value}:item));}
  function addFiles(files:File[]){if(inFlight.current||!files.length)return;setItems(previous=>[...previous,...files.map(item)]);setSummary('');}
  function choose(fromFiles=false){if(!picker.current||inFlight.current)return;picker.current.accept=fromFiles&&!wall?'*/*':accept;picker.current.click();}
  const retryable=(item:Item)=>!item.invalid&&['ready','failed','cancelled'].includes(item.state);
  async function run(apply:boolean){
    if(inFlight.current)return;
    if(items.some(item=>retryable(item)&&(!item.name.trim()||item.name.trim().length>48||/[\x00-\x1f\x7f-\x9f/\\]/.test(item.name)))){const detail='请填写每项素材名称，最多 48 个字符，不能包含斜线或控制字符';setSummary(detail);feedback({kind:'error',title:'上传失败',detail});return;}
    inFlight.current=true;stopped.current=false;setRunning(true);setSummary('');
    let review=0,cancelled=0,saved=items.filter(item=>item.state==='saved').length,failed=items.filter(item=>item.invalid).length,unknown=items.filter(item=>item.state==='unknown').length;
    onCancel(async()=>{stopped.current=true;await cancel.current?.();});
    try{
      for(let index=0;index<items.length;index++){
        const item=items[index];if(stopped.current)break;if(!retryable(item))continue;
        const name=`${index+1}/${items.length}  ${item.file.name}`;
        update(index,{state:'uploading',percent:0,message:'正在上传'});onProgress({name,percent:0,message:'正在上传'});
        try{
          const task=await client.upload(item.file,'wallpaper',!wall&&apply&&index===selected,wall?'':target,(percent,message)=>{update(index,{percent,message});onProgress({name,percent,message});},value=>{cancel.current=value;},item.name.trim());
          saved++;if(task.result?.requiresReview)review++;update(index,{state:'saved',percent:100,message:task.result?.requiresReview?'已保存，应用结果待核对':task.result?.applied?'已保存并使用':'已保存到素材库'});
        }catch(error){
          const known=error instanceof UploadError&&['failed','cancelled'].includes(error.task.state)||error instanceof ApiError&&error.status>=400&&error.status<500&&![403,408,409,410].includes(error.status);
          const message=error instanceof Error?error.message:'上传结果待核对';
          if(error instanceof UploadError&&error.task.result?.saved){saved++;review++;update(index,{state:'saved',percent:100,message:'已保存，应用结果待核对'});stopped.current=true;}
          else if(error instanceof UploadError&&error.task.state==='cancelled'){cancelled++;update(index,{state:'cancelled',message});}
          else if(known){failed++;update(index,{state:'failed',message});}
          else{unknown++;update(index,{state:'unknown',message:`结果待核对：${message}`});stopped.current=true;}
        }finally{cancel.current=null;}
      }
    }finally{
      inFlight.current=false;onProgress(null);onCancel(null);
      if(mounted.current){const detail=`已保存 ${saved} 项${failed?`  失败 ${failed} 项`:''}${unknown+review?`  待核对 ${unknown+review} 项`:''}${cancelled?`  已取消 ${cancelled} 项`:''}${stopped.current?'  后续上传已停止':''}`;setRunning(false);setSummary(detail);feedback({kind:unknown||review||stopped.current?'info':failed?'error':'success',title:unknown?'上传结果待核对':review?'素材已保存，应用待核对':failed?'部分上传失败':cancelled?'上传已取消':'上传完成',detail});}
    }
  }
  const ready=items.some(item=>retryable(item)),retrying=items.some(item=>!item.invalid&&(item.state==='failed'||item.state==='cancelled')),uncertain=items.some(item=>item.state==='unknown');
  return <><div className="media-upload-pickers"><button className="secondary-button" disabled={running} onClick={()=>choose()}>{wall?'继续添加静态壁纸':'继续添加图片或视频'}</button><button className="secondary-button" disabled={running} onClick={()=>choose(true)}>从文件选择</button><input ref={picker} type="file" hidden multiple accept={accept} onChange={event=>{addFiles(Array.from(event.target.files||[]));event.target.value='';}}/></div><p className="section-note">已选择 {items.length} 项</p><ul className="wallpaper-upload-list" aria-label="壁纸上传列表">{items.map((item,index)=><li key={index} data-upload-state={item.state}><label className="wallpaper-upload-choice">{!wall&&<input type="radio" name="apply-wallpaper" checked={selected===index} disabled={running||!retryable(item)} onChange={()=>setSelected(index)}/>}<span>{item.file.name}<small>{fileSize(item.file.size)}</small></span></label><label className="media-name">素材名称<input aria-label={`素材名称 ${index+1}`} value={item.name} maxLength={48} disabled={running||!retryable(item)} onChange={event=>update(index,{name:event.target.value})}/></label>{item.state==='uploading'&&<progress max={100} value={item.percent}/>}<p role="status">{item.message}{item.state==='uploading'?` ${item.percent}%`:''}</p></li>)}</ul>{summary&&<p className="section-note" role="status">{summary}</p>}<button className="primary-button" disabled={running||!ready||uncertain} onClick={()=>void run(false)}>{retrying?'重试未完成上传':'上传并保存全部'}</button>{!wall&&<button className="secondary-button" disabled={running||!ready||uncertain||!items[selected]||!retryable(items[selected])} onClick={()=>void run(true)}>上传全部并使用所选</button>}</>;
}
