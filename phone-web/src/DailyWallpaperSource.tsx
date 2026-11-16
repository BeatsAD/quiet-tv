import { useEffect, useState } from 'react';
import type { PhoneClient } from './api';
import { ChoiceField } from './widgets';

export function DailyWallpaperSource({client,disabled}:{client:PhoneClient;disabled:boolean}){
  const [requesting,setRequesting]=useState(false),[error,setError]=useState(''),[taskId,setTaskId]=useState('');
  const values=client.values(),task=client.tasks().find(item=>item.id===taskId);
  const busy=requesting||!!values.dailyPending||client.tasks().some(item=>item.domain==='wallpaper'&&['dailyApply','dailyRefresh','dailySource'].includes(item.kind)&&['queued','running','ready'].includes(item.state));
  useEffect(()=>{setError('');setTaskId('');},[values.dailySource]);
  const field=client.metadata?.fields.dailySource;
  if(!field?.options)return null;
  const problem=error||(task&&['failed','cancelled','unknown'].includes(task.state)?task.message:'');
  async function select(source:string|number){
    if(disabled||busy||source===values.dailySource)return;
    setRequesting(true);setError('');setTaskId('');
    try{const receipt=await client.command('wallpaper','dailySource',source),id=receipt.operation?.taskId;if(typeof id!=='string')throw new Error('来源切换结果未确认，请重新读取电视设置');setTaskId(id);void client.refresh();}
    catch(error){setError(error instanceof Error?error.message:'来源切换未完成');}
    finally{setRequesting(false);}
  }
  return <><ChoiceField title="图片来源" options={field.options} value={values.dailySource??'bing-cn'} disabled={disabled||busy} onChange={value=>void select(value)}/>{busy&&<p className="daily-source-status" role="status">正在获取壁纸…</p>}{problem&&<p className="form-error" role="alert">{problem}</p>}</>;
}
