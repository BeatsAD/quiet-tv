import { useState } from 'react';
import { type PhoneClient, type Value } from './api';
import { ConfirmDialog, Group, Icon, Row } from './widgets';

type Action={id:string;title:string;enabled:boolean;reason:string;explanation:string};
type DesktopRow={id:string;title:string;value:string;action?:Action};
type GuardSetup={label:string;accessibilityAllowed:boolean;accessibilityBound:boolean;accessibilitySettings:boolean;notificationSupported:boolean;notificationAllowed:boolean;notificationSettings:boolean};
type GuardRepair={canRetry:boolean;accessibilitySettings:boolean;accessibilityBound:boolean};
type Settings={rows:DesktopRow[];recovery:string;reason:string;guardSetup?:GuardSetup;guardRepair?:GuardRepair};
const guardIndicators: Record<string,{icon:string;tone:string}> = {
  running:{icon:'CheckCircledIcon',tone:'success'},
  paused:{icon:'PauseCircledIcon',tone:'warning'},
  authorization:{icon:'ExclamationTriangleIcon',tone:'warning'},
  review:{icon:'ExclamationTriangleIcon',tone:'warning'},
  disconnected:{icon:'ExclamationTriangleIcon',tone:'error'},
};
type Open=(view:{kind:string;id:string;title:string;keys?:string[];message?:string},nested?:boolean)=>void;

export function DesktopSwitch({client,open,run,busy}:{client:PhoneClient;open:Open;run:(domain:string,field:string,value?:Value)=>Promise<boolean>;busy:boolean}){
  const [confirmation,setConfirmation]=useState<{action:Action;revision:string}|null>(null);
  const [confirmationError,setConfirmationError]=useState('');
  const [systemUnavailable,setSystemUnavailable]=useState(false);
  const state=client.frame?.domains.desktopSwitch;
  const settings=state?.settings as unknown as Settings|undefined;
  const setup=settings?.guardSetup,repair=settings?.guardRepair;
  const guard=state?.guard as {state:string}|undefined;
  const guardIndicator=guardIndicators[guard?.state||'']||{icon:'Link2Icon',tone:'neutral'};
  const localSetupBlocked=setup&&(setup.accessibilityAllowed?!setup.accessibilityBound:!setup.accessibilitySettings);
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
          const value=row.id==='guard'
            ? <span className="desktop-guard-status" data-tone={guardIndicator.tone}><Icon name={guardIndicator.icon}/>{row.value}</span>
            : row.value;
          return <Row key={row.id} title={row.title} value={value} navigate
            disabled={disabled} onClick={action?.enabled?()=>confirm(action):undefined}/>;
        })}
        {repair&&<Row title="修复守护" navigate disabled={disabled} onClick={()=>void run('guard','repair',true)}/>}
        {repair?.canRetry&&repair.accessibilitySettings&&!repair.accessibilityBound&&<Row title="打开无障碍设置" navigate disabled={disabled} onClick={()=>void run('guard','repairAccessibility',true)}/>}
        {recovery==='system'&&<Row title="设置 Quiet TV 桌面" navigate disabled={disabled} onClick={()=>void openSystem()}/>}
        {recovery==='authorize'&&<Row title="去授权" value="在电视上确认" navigate disabled={disabled} onClick={()=>void run('management','connect')}/>}
      </Group>
        {setup&&<Group title="设置桌面守护">
          <Row title="电视原桌面" value={setup.label}/>
          <Row title="无障碍" value={setup.accessibilityBound&&setup.accessibilityAllowed?'已连接':setup.accessibilityAllowed?'等待系统连接':setup.accessibilitySettings?'待授权':'入口不可用'}
            navigate={setup.accessibilitySettings} disabled={disabled} onClick={setup.accessibilitySettings?()=>void run('guard','accessibility',true):undefined}/>
          {setup.notificationSupported&&<Row title="通知使用权" value={setup.notificationAllowed?'已授权':setup.notificationSettings?'用于后台恢复':'入口不可用'}
            navigate={setup.notificationSettings} disabled={disabled} onClick={setup.notificationSettings?()=>void run('guard','notification',true):undefined}/>}
          <Row title="启用桌面守护" value="在电视上确认"/>
        </Group>}
      {!repair&&settings.reason&&<p className="section-note" role="status">{settings.reason}</p>}
        {(recovery==='computer'||localSetupBlocked)&&typeof setupGuide==='string'&&setupGuide&&<a className="text-action desktop-management-guide" href={setupGuide} target="_blank" rel="noreferrer">通过电脑设置与恢复</a>}
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
