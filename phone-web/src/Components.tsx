import { type PhoneClient } from './api';
import { useState } from 'react';
import { createPortal } from 'react-dom';
import { ConfirmDialog, Group, Icon, Row, fileSize } from './widgets';

type Status={supported?:boolean;ready?:boolean;canUninstall?:boolean;uninstallSupported?:boolean;updatePending?:boolean;reason?:string;state?:string;message?:string;downloadedBytes?:number;totalBytes?:number};
export function weatherComponent(client:PhoneClient):Status{return client.frame?.domains.components?.weather as Status||{};}
export function WeatherComponentRow({client,onClick}:{client:PhoneClient;onClick:()=>void}){
  if(!client.frame?.domains.components)return null;
  const s=weatherComponent(client),busy=['checking','downloading','verifying'].includes(s.state||'');
  return <Row title="动态天气壁纸" icon="SunIcon" value={s.state==='removing'?'正在卸载':s.state==='remove_failed'?'卸载未完成':s.reason||(!s.supported?'不支持':busy?'正在准备':s.ready?'已下载':'未下载')} navigate onClick={onClick}/>;
}
const weatherResourceStates:Record<string,string>={
  checking:'正在读取',downloading:'正在下载',verifying:'正在校验',ready:'已下载',
  failed:'下载未完成',cancelled:'已取消',removing:'正在卸载',remove_failed:'卸载未完成',
};
export function WeatherComponent({client,run,disabled,actionsHost}:{
  client:PhoneClient;run:(action:string)=>Promise<boolean>;disabled:boolean;actionsHost:HTMLElement|null;
}){
  const [confirm,setConfirm]=useState(false),[submitting,setSubmitting]=useState(false);
  const s=weatherComponent(client),state=s.state||'';
  const busy=['checking','downloading','verifying'].includes(state),removing=state==='removing',removeFailed=state==='remove_failed';
  const total=Number(s.totalBytes||0),done=Number(s.downloadedBytes||0);
  const resourceStatus=removing||removeFailed?weatherResourceStates[state]:
    s.reason||(s.updatePending?'重启应用后生效':weatherResourceStates[state]||(s.ready?'已下载':'未下载'));
  const enableHint=s.ready&&!s.updatePending&&!busy&&!removing&&!removeFailed&&client.value('wallMode')!=='weather'
    ?'选择「随天气变化」启用。':undefined;
  const uninstallBlocked=disabled||!s.canUninstall||busy||removing;
  return <div className="weather-component">
    <Group>
      <Row title="下载位置" value={client.metadata?.device.name||'当前电视'}/>
      <Row title="天气资源" value={resourceStatus} hint={enableHint}/>
      <Row title="下载大小" value={total?fileSize(total):'下载前读取'}/>
      <details className="weather-component-requirements">
        <summary className="setting-row">
          <span className="row-title">设备要求</span>
          <span className="row-value">{'安卓 5.0+  OpenGL ES 3.0'}</span>
          <Icon name="ChevronDownIcon" className="chevron"/>
        </summary>
        <p className="section-note">需要安卓 5.0 及以上，并支持 OpenGL ES 3.0。动态天气会占用更多内存和图形资源，实际流畅度取决于电视性能。</p>
      </details>
    </Group>
    <p className="section-note">动态天气较耗性能，低配置电视建议使用静态壁纸。</p>
    {busy&&<div className="weather-component-progress" role="status">
      <div className="upload-progress">
        <progress aria-label="天气资源下载进度" max={total||1} value={total?done:undefined}/>
        <span>{total?`${fileSize(done)} / ${fileSize(total)}  ${Math.floor(done*100/total)}%`:'正在读取并校验组件信息'}</span>
      </div>
      <p className="section-note">关闭页面后，电视会继续下载。</p>
    </div>}
    {s.message&&<p className="section-note" role="status">{s.message}</p>}
    {actionsHost&&createPortal(<div className="paired-actions">
      <button type="button" className="secondary-button" disabled={uninstallBlocked} onClick={()=>setConfirm(true)}>卸载</button>
      <button type="button" className="primary-button" disabled={disabled||!s.supported||removing||removeFailed}
        onClick={()=>void run(busy?'cancel':'start')}>{busy?'取消下载':s.ready?'检查组件更新':'下载天气资源'}</button>
    </div>,actionsHost)}
    {confirm&&<ConfirmDialog title="卸载天气资源？" confirmLabel="卸载" busyLabel="正在提交…" busy={submitting}
      disabled={uninstallBlocked} onCancel={()=>setConfirm(false)}
      onConfirm={async()=>{setSubmitting(true);try{if(await run('uninstall'))setConfirm(false);}finally{setSubmitting(false);}}}>
      <p>移除当前电视已下载的动态天气资源。正在使用时将切回静态壁纸，天气设置保留。</p>
    </ConfirmDialog>}
  </div>;
}
export function OpenAssistant(){return <><p className="sheet-intro">组网功能请在手机助手 App 中使用。</p><a className="primary-button" href="quiettv-assistant://settings/general?component=tailscale">打开手机助手 App</a><p className="section-note">手机助手首版支持 ARM64 安卓 8 及以上系统。</p></>;}
