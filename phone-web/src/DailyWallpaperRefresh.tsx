import { useState } from 'react';
import type { PhoneClient } from './api';

export function DailyWallpaperRefresh({client,disabled}:{client:PhoneClient;disabled:boolean}){
  const [taskId,setTaskId]=useState(''),[requesting,setRequesting]=useState(false),[error,setError]=useState('');
  const tasks=client.tasks(),task=tasks.find(item=>item.id===taskId);
  const refreshing=requesting||!!client.values().dailyPending||tasks.some(item=>item.domain==='wallpaper'&&['dailyRefresh','dailySource','dailyApply'].includes(item.kind)&&['queued','running','ready'].includes(item.state));
  const problem=error||(task&&['failed','unknown','cancelled'].includes(task.state)?task.message:'');
  async function refresh(){
    if(disabled||refreshing)return;
    setRequesting(true);setTaskId('');setError('');
    try{
      const result=await client.command('wallpaper','dailyRefresh',true),id=result.operation?.taskId;
      if(typeof id!=='string')throw new Error('刷新结果未确认，请重试');
      setTaskId(id);void client.refresh();
    }catch(error){setError(error instanceof Error?error.message:'刷新未完成，请重试');}
    finally{setRequesting(false);}
  }
  return <><button className="primary-button" disabled={disabled||refreshing} aria-busy={refreshing} onClick={()=>void refresh()}>{refreshing?'刷新中…':problem?'重试':'刷新壁纸'}</button>{problem&&<p className="form-error" role="alert">{problem}</p>}</>;
}
