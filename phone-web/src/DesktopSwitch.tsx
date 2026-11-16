import { useState } from 'react';
import { type PhoneClient, type Value } from './api';
import { ConfirmDialog, Group, Row } from './widgets';
import { useOperationFeedback } from './OperationFeedback';

type Action={id:string;title:string;enabled:boolean;reason:string;explanation:string;scope?:string};
type Desktop={id:string;label:string;detail:string};
type Open=(view:{kind:string;id:string;title:string;keys?:string[];message?:string},nested?:boolean)=>void;
const scenes=[['home','实体 HOME'],['reboot','重启'],['coldBoot','断电开机'],['wake','待机唤醒']] as const;

export function DesktopSwitch({client,open,run,busy}:{client:PhoneClient;open:Open;run:(domain:string,field:string,value?:Value)=>Promise<boolean>;busy:boolean}){
  const feedback=useOperationFeedback();
  const [confirmationError,setConfirmationError]=useState('');
  const [page,setPage]=useState('home'),[confirmation,setConfirmation]=useState<{action:Action;revision:string}|null>(null);
  const state=client.frame?.domains.desktopSwitch||{},desktop=(state.mainDesktop||{}) as Record<string,Value>,guard=(state.guard||{}) as Record<string,Value>;
  const choices=((state.managementActions||[]) as unknown as Action[]),entries=((state.desktops||[]) as unknown as Desktop[]);
  const working=busy||!!state.busy||client.tasks().some(task=>task.domain==='desktopSwitch'&&['queued','running'].includes(task.state));
  const offline=client.status!=='connected',disabled=working||offline;
  const primary=choices.find(action=>action.id===state.primaryAction),secondary=choices.find(action=>action.scope==='guard');
  const confirm=(action:Action)=>{setConfirmationError('');setConfirmation({action,revision:String(state.revision||'')});};
  const choose=(action:Action,primary=false)=><button key={action.id} className={primary?'primary-button':'secondary-button'} disabled={disabled||!action.enabled} onClick={()=>confirm(action)}>{action.title}</button>;
  const details=(state.verification||{}) as Record<string,Value>;
  const header=<div className="desktop-management-status"><span className="desktop-management-caption">当前主桌面</span><div className="desktop-management-hero"><strong>{String(desktop.label||'待核对')}</strong><span className={guard.state==='running'?'good':'muted'}>{String(guard.label||'状态待核对')}</span></div><p>{String(desktop.hint||'正在读取电视桌面状态')}</p></div>;
  const back=<button className="text-action desktop-management-back" onClick={()=>setPage('home')}>返回桌面管理</button>;
  const legacy=Number(state.presentationVersion||0)<1;
  return <div className="desktop-management">
    {offline&&<p className="section-note" role="status">当前连接不可用，以下为最后已知状态。重新连接后可继续操作。</p>}
    {legacy?<><p className="sheet-intro">{String(state.mode||'桌面状态待核对')}</p><p className="section-note">当前电视提供旧版管理能力，可重新扫码获取匹配页面。</p>{((state.actions||[]) as unknown as Action[]).map(action=><div key={action.id}><button className="primary-button" disabled={disabled||!action.enabled} onClick={()=>open({kind:'tool-confirm',id:'desktopSwitch',title:action.title+'？',keys:['switch'],message:JSON.stringify({value:{action:action.id,revision:state.revision},message:action.explanation})},true)}>{action.title}</button>{!action.enabled&&<p className="section-note">{action.reason}</p>}</div>)}</>:
    page==='home'?<>{header}<Group title="可用桌面">{entries.map(entry=><Row key={entry.id} title={entry.label} value={entry.detail}/>)}</Group><Group><Row title="更多管理" value="启用／禁用" navigate onClick={()=>setPage('more')}/></Group><div className="desktop-management-actions">{primary?choose(primary,true):state.systemSelection?<button className="primary-button" disabled={disabled} onClick={()=>void run('guard','system')}>设置 Quiet TV 桌面</button>:<button className="primary-button" onClick={()=>setPage('more')}>查看处理方式</button>}{primary&&!primary.enabled&&<p className="section-note">{primary.reason}</p>}{secondary&&secondary.id!==primary?.id&&choose(secondary)}</div><p className="section-note desktop-management-note">{desktop.id==='original'?'切回时恢复切换前的守护状态':'操作将在当前连接的电视上生效'}</p></>:
    page==='more'?<><Group title="原桌面管理">{choices.filter(action=>action.scope==='original'||action.scope==='return'&&action.id==='quiet-disable').map(action=><Row key={action.id} title={action.title} hint={action.enabled?action.explanation:action.reason} navigate disabled={disabled||!action.enabled} onClick={()=>confirm(action)}/>)}</Group><Group><Row title="检查运行状态" value="只读取当前配置" navigate disabled={disabled} onClick={async()=>{await client.refreshNow();feedback({kind:client.status==='connected'?'success':'error',title:client.status==='connected'?'已重新读取桌面状态':'桌面状态读取未完成',detail:client.problem||undefined});}}/><Row title="使用验证" value="HOME  开机  唤醒" navigate onClick={()=>setPage('verify')}/><Row title="ADB 管理" navigate onClick={()=>open({kind:'tool',id:'management',title:'ADB 管理'},true)}/>{!!state.systemSelection&&<Row title="系统默认桌面选择" navigate disabled={disabled} onClick={()=>void run('guard','system')}/>}<Row title="诊断详情" navigate onClick={()=>open({kind:'tool',id:'diagnostics',title:'诊断详情'},true)}/></Group>{!!state.problem&&<p className="section-note" role="status">{String(state.problem)}</p>}<a className="text-action desktop-management-guide" href={String(client.metadata?.setupGuide||'')} target="_blank" rel="noreferrer">通过电脑设置与恢复</a>{back}</>:
    page==='verify'?<><p className="sheet-intro">按电视实际表现分别确认；服务连接不会自动记为通过。</p><Group>{scenes.map(([id,title])=><Row key={id} title={title} value={String(details[id]||'待验证')} navigate onClick={()=>setPage('scene:'+id)}/>)}</Group>{back}</>:
    <><p className="sheet-intro">{scenes.find(([id])=>page==='scene:'+id)?.[1]}</p><p className="section-note">请完成对应的实体操作后，再记录电视实际表现。</p>{state.canVerify?['通过','失败','待验证'].map(result=><button key={result} className="secondary-button" disabled={disabled} onClick={async()=>{if(await run('desktopSwitch','verify',{scene:page.slice(6),result,revision:String(state.revision||'')}))setPage('verify');}}>{result}</button>):<p className="section-note">当前路线尚不支持记录验证结果，请通过电脑核对。</p>}<button className="text-action desktop-management-back" onClick={()=>setPage('verify')}>返回使用验证</button></>}
    {confirmation&&<ConfirmDialog title={confirmation.action.title+'？'} busy={working} disabled={offline||!!confirmationError} destructive={confirmation.action.id.includes('disable')} confirmLabel="确认" busyLabel="正在处理…" onCancel={()=>setConfirmation(null)} onConfirm={async()=>{if(await run('desktopSwitch','manage',{action:confirmation.action.id,revision:confirmation.revision})){setConfirmation(null);setPage('home');}else setConfirmationError(client.problem||'操作未完成，请取消后重新核对电视状态。');}}><p>{confirmation.action.explanation}</p><p className="section-note">操作将在当前连接的电视上生效。</p>{confirmationError&&<p className="form-error" role="alert">{confirmationError}</p>}</ConfirmDialog>}
  </div>;
}
