import { type PhoneClient } from './api';
import { useState } from 'react';
import { ConfirmDialog, Group, Row, fileSize } from './widgets';

type Status={supported?:boolean;ready?:boolean;canUninstall?:boolean;uninstallSupported?:boolean;reason?:string;state?:string;message?:string;downloadedBytes?:number;totalBytes?:number};
export function weatherComponent(client:PhoneClient):Status{return client.frame?.domains.components?.weather as Status||{};}
export function WeatherComponentNotice(){return <p className="section-note">实时天气会占用更多内存和图形性能。<br/>仅建议高性能电视开启，老旧或低配置电视建议使用静态壁纸。<br/>最低要求：安卓 5.0 及以上，支持 OpenGL ES 3.0。系统版本达标不代表运行流畅。</p>;}
export function WeatherComponentRow({client,onClick}:{client:PhoneClient;onClick:()=>void}){
  if(!client.frame?.domains.components)return null;
  const s=weatherComponent(client),busy=['checking','downloading','verifying'].includes(s.state||'');
  return <Row title="动态天气壁纸" value={s.state==='removing'?'正在卸载':s.state==='remove_failed'?'卸载未完成':s.reason||(!s.supported?'不支持':busy?'正在准备':s.ready?'已下载':'未下载')} navigate onClick={onClick}/>;
}
export function WeatherComponent({client,run}:{client:PhoneClient;run:(action:string)=>Promise<boolean>}){
  const [confirm,setConfirm]=useState(false),[submitting,setSubmitting]=useState(false);
  const s=weatherComponent(client),busy=['checking','downloading','verifying'].includes(s.state||''),total=Number(s.totalBytes||0),done=Number(s.downloadedBytes||0),removing=s.state==='removing',removeFailed=s.state==='remove_failed',offline=client.status!=='connected';
  return <><Group><Row title="下载位置" value={client.metadata?.device.name||'当前电视'}/><Row title="天气资源" value={(removing?'正在卸载':removeFailed?'卸载未完成':s.reason)||({checking:'正在读取',downloading:'正在下载',verifying:'正在校验',ready:'已下载',failed:'下载未完成',cancelled:'已取消'}[s.state||'']||'未下载')}/><Row title="下载大小" value={total?fileSize(total):'下载前读取'}/></Group><WeatherComponentNotice/>{busy&&<div className="upload-progress" role="status"><progress max={total||1} value={total?done:undefined}/><span>{total?`${fileSize(done)} / ${fileSize(total)}  ${Math.floor(done*100/total)}%`:'正在读取并校验组件信息'}</span></div>}{s.message&&<p className="section-note" role="status">{s.message}</p>}<button className="primary-button" disabled={offline||!s.supported||removing||removeFailed} onClick={()=>run(busy?'cancel':'start')}>{busy?'取消下载':s.ready?'检查组件更新':'下载天气资源'}</button><p className="section-note">下载完成后，选择“随天气变化”。关闭页面后，电视继续下载。</p><button className="secondary-button" disabled={offline||!s.canUninstall||busy||removing} onClick={()=>setConfirm(true)}>卸载</button>{confirm&&<ConfirmDialog title="卸载天气资源？" confirmLabel="卸载" busyLabel="正在提交…" busy={submitting} disabled={offline||!s.canUninstall||busy||removing} onCancel={()=>setConfirm(false)} onConfirm={async()=>{setSubmitting(true);try{if(await run('uninstall'))setConfirm(false);}finally{setSubmitting(false);}}}><p>移除当前电视已下载的动态天气资源。正在使用时将切回静态壁纸，天气设置保留。</p></ConfirmDialog>}</>;
}
export function OpenAssistant(){return <><p className="sheet-intro">组网功能请在手机助手 App 中使用。</p><a className="primary-button" href="quiettv-assistant://settings/general?component=tailscale">打开手机助手 App</a><p className="section-note">手机助手首版支持 ARM64 安卓 8 及以上系统。</p></>;}
