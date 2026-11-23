import { useEffect, useState } from 'react';
import { type PhoneClient, type Value } from './api';
import { copyText } from './connection';
import { ConfirmDialog, Group, Row } from './widgets';

export function bootAdsLabel(status:unknown) {
  switch (status) {
    case 'available': return '可关闭';
    case 'applied': return '已应用';
    case 'configured': return '电视已设为屏蔽广告';
    case 'authorize': return '需要授权';
    case 'unsupported': return '暂不支持';
    default: return '状态待核对';
  }
}

export function BootAds({client,run,busy}:{
  client:PhoneClient;
  run:(domain:string,field:string,value?:Value)=>Promise<boolean>;
  busy:boolean;
}) {
  const state=client.frame?.domains.bootAds;
  const status=state?.status;
  const revision=String(state?.revision||'');
  const impact=String(state?.impact||'');
  const working=busy||client.tasks().some(task=>task.domain==='bootAds'&&(task.state==='queued'||task.state==='running'));
  const offline=client.status!=='connected';
  const [confirmation,setConfirmation]=useState<{action:'apply'|'restore';revision:string}|null>(null);
  const [guide,setGuide]=useState(false);
  const [copyResult,setCopyResult]=useState('');
  const [checked,setChecked]=useState(false);

  useEffect(()=>{
    if (!checked && state?.canCheck===true&&!offline&&!working) {
      setChecked(true);
      void run('bootAds','check','');
    }
  },[checked,state?.canCheck,offline,working,run]);

  const restoring=confirmation?.action==='restore';
  const stale=!!confirmation&&confirmation.revision!==revision;
  const request=String(state?.aiRequest||'');
  return <>
    <p className="sheet-intro" role="status">{offline?'电视连接不可用':bootAdsLabel(status)}</p>
    {status==='applied'&&<p className="section-note">下次开机时，请确认广告是否消失。</p>}
    {status==='unsupported'&&<p className="section-note">当前机型或系统版本暂不支持直接关闭开机广告。可通过电脑 AI 排查是否有适用的关闭方式。</p>}
    {typeof state?.detail==='string'&&state.detail&&<p className="section-note">{state.detail}</p>}
    <Group>
      {status==='available'&&<Row title="关闭开机广告" navigate disabled={offline||working}
        onClick={()=>setConfirmation({action:'apply',revision})}/>}
      {status==='applied'&&<Row title="恢复原设置" navigate disabled={offline||working}
        onClick={()=>setConfirmation({action:'restore',revision})}/>}
      {state?.needsAuthorization===true&&<Row title="去授权" value="在电视上确认" navigate disabled={offline||working}
        onClick={()=>{setChecked(false);void run('management','connect');}}/>}
      {state?.canCheck===true&&['check','review','configured','unsupported'].includes(String(status))&&<Row title="重新检测" disabled={offline||working}
        onClick={()=>void run('bootAds','check','')}/>}
      <Row title="电脑 AI 排查" navigate onClick={()=>setGuide(!guide)}/>
    </Group>
    {guide&&typeof state?.guide==='string'&&<div className="desktop-management">
      <a className="text-action desktop-management-guide" href={state.guide} target="_blank" rel="noreferrer">查看开机广告排查说明</a>
      <button className="secondary-button" onClick={async()=>{
        try {
          await copyText(request,'请手动复制下方的排查指令');
          setCopyResult('已复制排查指令');
        } catch {
          setCopyResult(request);
        }
      }}>复制排查指令</button>
      {copyResult&&<p className="section-note" role="status">{copyResult}</p>}
    </div>}
    {confirmation&&<ConfirmDialog title={restoring?'恢复原设置？':'关闭开机广告？'} busy={working}
      disabled={offline||stale} destructive={false} confirmLabel={restoring?'确认恢复':'确认关闭'}
      busyLabel="正在处理…" onCancel={()=>setConfirmation(null)} onConfirm={async()=>{
        if(await run('bootAds',confirmation.action,confirmation.revision))setConfirmation(null);
      }}>
      {restoring?<p>将撤销 Quiet TV 对开机广告的修改，广告可能再次出现。</p>:<>
        {state?.upstream===true?<p>此方案来自开源项目的同款固件实测。</p>:impact&&<p>{impact}</p>}
        <p>关闭效果因电视机型和系统版本而异。下次开机时，请确认广告是否消失。</p>
        <p>如果仍有广告，可通过电脑 AI 排查原因。</p>
      </>}
      {stale&&<p className="form-error" role="alert">电视状态已变化，请返回后重新确认。</p>}
    </ConfirmDialog>}
  </>;
}
