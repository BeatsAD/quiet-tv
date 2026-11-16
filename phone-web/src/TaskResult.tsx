import { useEffect } from 'react';
import type { PhoneClient, Task } from './api';
import { taskHasAction, taskIsTransient, taskNeedsAttention } from './presentation';
import { Icon } from './widgets';

export function TaskResult({task,client,uploading,onDismiss,onReview,onCancel}:{task:Task;client:PhoneClient;uploading:boolean;onDismiss:(id:string)=>void;onReview:(id:string)=>void;onCancel:(id:string)=>void}){
  const transient=taskIsTransient(task),actionable=taskHasAction(task),attention=taskNeedsAttention(task);
  useEffect(()=>{
    if(!transient)return;
    const timer=setTimeout(()=>onDismiss(task.id),3000);
    return()=>clearTimeout(timer);
  },[task.id,transient,onDismiss]);
  if(transient)return null;
  return <div className="transfer-status" data-task-domain={task.domain} role={attention?'alert':'status'}><strong>{task.title}</strong><span>{task.message}</span>{typeof task.result?.download==='string'&&<a className="text-action download-action" download href={client.resource(task.result.download)} aria-disabled={uploading} onClick={event=>{if(uploading)event.preventDefault();}}>下载到手机<Icon name="DownloadIcon"/></a>}{typeof task.result?.draftId==='string'&&<button className="text-action" onClick={()=>{onDismiss(task.id);onReview(String(task.result!.draftId));}}>核对备份内容</button>}{task.progress>=0&&!['completed','failed','unknown'].includes(task.state)&&<progress max={100} value={task.progress}/ >}{attention&&!actionable&&<button className="text-action" onClick={()=>onDismiss(task.id)}>收起提示</button>}{actionable&&<button className="text-action" onClick={()=>onDismiss(task.id)}>收起结果</button>}{task.cancellable!==false&&['queued','running','ready'].includes(task.state)&&<button className="text-action" onClick={()=>onCancel(task.id)}>取消</button>}</div>;
}
