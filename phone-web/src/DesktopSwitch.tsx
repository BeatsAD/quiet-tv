import { useState } from 'react';
import { type PhoneClient, type Value } from './api';
import { ConfirmDialog, Group, Row } from './widgets';

type Action={id:string;title:string;enabled:boolean;reason:string;explanation:string};
type DesktopRow={id:string;title:string;value:string;action?:Action};
type Settings={rows:DesktopRow[];recovery:string;reason:string};
type Open=(view:{kind:string;id:string;title:string;keys?:string[];message?:string},nested?:boolean)=>void;

export function DesktopSwitch({client,open,run,busy}:{client:PhoneClient;open:Open;run:(domain:string,field:string,value?:Value)=>Promise<boolean>;busy:boolean}){
  const [confirmation,setConfirmation]=useState<{action:Action;revision:string}|null>(null);
  const [confirmationError,setConfirmationError]=useState('');
  const [systemUnavailable,setSystemUnavailable]=useState(false);
  const state=client.frame?.domains.desktopSwitch;
  const settings=state?.settings as unknown as Settings|undefined;
  const working=busy||!!state?.busy||client.tasks().some(task=>task.domain==='desktopSwitch'&&['queued','running'].includes(task.state));
  const offline=client.status!=='connected',disabled=working||offline;
  const revision=String(state?.revision||'');
  const recovery=settings?.recovery==='system'&&systemUnavailable?'computer':settings?.recovery;
  const setupGuide=client.metadata?.setupGuide;
  const confirm=(action:Action)=>{
    setConfirmationError('');
    setConfirmation({action,revision});
  };
  const openSystem=async()=>{
    if(!await run('guard','system'))setSystemUnavailable(true);
  };
  return <div className="desktop-management">
    {offline&&<p className="section-note" role="status">电视连接不可用，重新连接后可继续操作。</p>}
    {!state?<p className="section-note" role="status">正在读取电视桌面状态</p>:
      settings?<><Group>
        {settings.rows.map(row=>{
          const action=row.action;
          return <Row key={row.id} title={row.title} value={row.value} navigate
            disabled={disabled} onClick={action?.enabled?()=>confirm(action):undefined}/>;
        })}
        {recovery==='system'&&<Row title="设置 Quiet TV 桌面" navigate disabled={disabled} onClick={()=>void openSystem()}/>}
        {recovery==='authorize'&&<Row title="去授权" value="在电视上确认" navigate disabled={disabled} onClick={()=>void run('management','connect')}/>}
      </Group>
      {settings.reason&&<p className="section-note" role="status">{settings.reason}</p>}
      {recovery==='computer'&&typeof setupGuide==='string'&&setupGuide&&<a className="text-action desktop-management-guide" href={setupGuide} target="_blank" rel="noreferrer">通过电脑设置与恢复</a>}
      </>:
      <><p className="sheet-intro">{String(state.mode||'桌面状态待核对')}</p>
        {((state.actions||[]) as unknown as Action[]).map(action=><Row key={action.id} title={action.title}
          hint={action.enabled?undefined:action.reason} navigate disabled={disabled||!action.enabled}
          onClick={()=>open({kind:'tool-confirm',id:'desktopSwitch',title:action.title+'？',keys:['switch'],
            message:JSON.stringify({value:{action:action.id,revision},message:action.explanation})},true)}/>)}
      </>}
    {confirmation&&<ConfirmDialog title={confirmation.action.title+'？'} busy={working} disabled={offline||!!confirmationError}
      destructive={confirmation.action.id.includes('disable')} confirmLabel="确认" busyLabel="正在处理…"
      onCancel={()=>setConfirmation(null)} onConfirm={async()=>{
        if(await run('desktopSwitch','manage',{action:confirmation.action.id,revision:confirmation.revision}))setConfirmation(null);
        else setConfirmationError(client.problem||'操作未完成，请取消后重新核对电视状态。');
      }}>
      <p>{confirmation.action.explanation}</p>
      <p className="section-note">操作将在当前连接的电视上生效。</p>
      {confirmationError&&<p className="form-error" role="alert">{confirmationError}</p>}
    </ConfirmDialog>}
  </div>;
}
