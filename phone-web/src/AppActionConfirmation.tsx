import { useRef, useState } from 'react';
import type { App } from './api';
import { ConfirmDialog } from './widgets';

export type ApplicationAction=NonNullable<App['actions']>[number];
export type ApplicationConfirmation={app:App;action:ApplicationAction};

const descriptions:Record<string,string>={
  open:'将在电视上请求打开此应用。',
  restoreCover:'将恢复为应用自带的图标或横幅。',
  boot:'将此应用设为开机应用，不会立即打开。',
  hide:'将在 QuietTV 中隐藏此应用，可在“隐藏应用”中恢复显示。',
  show:'将恢复此应用在 QuietTV 中的显示。',
  dockAdd:'将此应用添加到首页位置。',
  dockRemove:'应用和数据会保留，仍可在“全部应用”中打开。',
  UNINSTALL:'卸载后，应用数据将删除。再次使用需要重新安装。',
  systemUninstall:'将打开电视系统的卸载确认，请在电视上继续操作。卸载可能删除应用数据。',
};
const confirmLabels:Record<string,string>={open:'打开应用',restoreCover:'确认恢复',boot:'设为开机应用',hide:'确认隐藏',show:'恢复显示',dockAdd:'添加到首页',dockRemove:'确认移除',UNINSTALL:'确认卸载',systemUninstall:'确认卸载',DISABLE:'确认停用',ENABLE:'确认启用',RESTORE:'确认恢复'};
export function applicationActionTitle(action:ApplicationAction){return action.id==='dockRemove'?'移除首页位置':action.id==='dockAdd'?'添加到首页':action.title;}

export function AppActionConfirmation({request,current,busy,disabled,error,onCancel,onConfirm,onDone}:{request:ApplicationConfirmation;current?:App;busy:boolean;disabled:boolean;error:string;onCancel:()=>void;onConfirm:()=>Promise<boolean>;onDone:()=>void}){
  const running=useRef(false),[submitting,setSubmitting]=useState(false);
  const {app,action}=request,title=applicationActionTitle(action),available=current?.actions?.find(item=>item.id===action.id);
  const changed=!current||current.version!==app.version||!available,problem=changed?'应用状态已变化，请取消后重新确认。':available?.reason||error;
  async function submit(){
    if(running.current||busy||disabled||changed||available?.reason)return;
    running.current=true;setSubmitting(true);
    try{if(await onConfirm())onDone();}finally{running.current=false;setSubmitting(false);}
  }
  return <ConfirmDialog title={`${title}？`} confirmLabel={confirmLabels[action.id]||title} busyLabel="正在处理…" destructive={!!action.danger} busy={busy||submitting} disabled={disabled||changed||!!available?.reason} onCancel={onCancel} onConfirm={()=>void submit()}>
    <p className="confirmation-media-name">{current?.name||app.name}</p>
    <p>{action.explanation||descriptions[action.id]||'确认后执行此应用操作。'}</p>
    {problem&&<p className="form-error" role="alert">{problem}</p>}
  </ConfirmDialog>;
}
