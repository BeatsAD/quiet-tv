import type { PhoneClient, Value } from './api';
import { Group, Icon, Row } from './widgets';
import './AdbManagement.css';

export function adbLabel(state?:Record<string,Value>){
  return typeof state?.label==='string'?state.label:state?.connected?'已连接':state?.state==='CONNECTING'?'连接中…':state?'未连接':'正在读取…';
}

export function AdbManagement({client,run,busy}:{client:PhoneClient;run:(domain:string,field:string,value?:Value)=>Promise<boolean>;busy:boolean}){
  const state=client.frame?.domains.management,connected=!!state?.connected,connecting=state?.state==='CONNECTING';
  const blocked=busy||!state||client.status==='ended'||client.restoring;
  return <div className="adb-management" data-tone={String(state?.tone||(connected?'success':state?.waitingAuthorization?'warning':connecting?'progress':state?.timedOut||state?.label==='连接失败'?'error':'neutral'))}>
    <div className="adb-status" role="status" aria-live="polite" aria-atomic="true">
      <div className="adb-status-heading"><Icon name={connected?'CheckCircledIcon':connecting?'ReloadIcon':'Link2Icon'}/><strong>{adbLabel(state)}</strong></div>
      <p>{String(state?.detail||'正在读取当前电视的 ADB 连接状态。')}</p>
      {!!state?.waitingAuthorization&&<p className="adb-authorization-wait" role="timer">剩余等待 {Math.max(0,Number(state.authorizationSeconds)||0)} 秒</p>}
    </div>
    <Group><Row title="系统调试" value={String(state?.debugging||'待确认')}/><Row title="连接端口" value={state?.port?String(state.port):'待确认'}/></Group>
    {connected&&<p className="section-note">断开后暂停自动重连，需要时可再次连接。</p>}
    <button type="button" className="primary-button" disabled={blocked} onClick={()=>void run('management',connected||connecting?'disconnect':'connect')}>{connected?'断开连接':connecting?'取消连接':'连接 ADB'}</button>
    <button type="button" className="secondary-button" disabled={blocked||connecting} onClick={()=>void run('management','check')}>检查状态</button>
  </div>;
}
