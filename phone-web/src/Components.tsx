import { type PhoneClient } from './api';
import { Group, Row, fileSize } from './widgets';

type Status={supported?:boolean;ready?:boolean;reason?:string;state?:string;message?:string;downloadedBytes?:number;totalBytes?:number};
export function weatherComponent(client:PhoneClient):Status{return client.frame?.domains.components?.weather as Status||{};}
export function WeatherComponentNotice(){return <p className="section-note">实时天气会占用更多内存和图形性能。<br/>仅建议高性能电视开启，老旧或低配置电视建议使用静态壁纸。<br/>最低要求：安卓 5.0 及以上，支持 OpenGL ES 3.0。系统版本达标不代表运行流畅。</p>;}
export function WeatherComponentRow({client,onClick}:{client:PhoneClient;onClick:()=>void}){
  if(!client.frame?.domains.components)return null;
  const s=weatherComponent(client),busy=['checking','downloading','verifying'].includes(s.state||'');
  return <Row title="动态天气壁纸" value={s.reason||(!s.supported?'不支持':busy?'正在准备':s.ready?'已下载':'未下载')} navigate onClick={onClick}/>;
}
export function WeatherComponent({client,run,onBack}:{client:PhoneClient;run:(action:string)=>void;onBack:()=>void}){
  const s=weatherComponent(client),busy=['checking','downloading','verifying'].includes(s.state||''),total=Number(s.totalBytes||0),done=Number(s.downloadedBytes||0);
  return <><Group><Row title="下载位置" value={client.metadata?.device.name||'当前电视'}/><Row title="天气资源" value={s.reason||({checking:'正在读取',downloading:'正在下载',verifying:'正在校验',ready:'已下载',failed:'下载未完成',cancelled:'已取消'}[s.state||'']||'未下载')}/><Row title="下载大小" value={total?fileSize(total):'下载前读取'}/></Group><WeatherComponentNotice/>{busy&&<div className="upload-progress" role="status"><progress max={total||1} value={total?done:undefined}/><span>{total?`${fileSize(done)} / ${fileSize(total)}  ${Math.floor(done*100/total)}%`:'正在读取并校验组件信息'}</span></div>}{s.message&&<p className="section-note" role="status">{s.message}</p>}<button className="primary-button" disabled={!s.supported} onClick={()=>run(busy?'cancel':'start')}>{busy?'取消下载':s.ready?'检查组件更新':'下载天气资源'}</button><p className="section-note">下载完成后，选择“随天气变化”。关闭页面后，电视继续下载。</p><button className="secondary-button" onClick={onBack}>返回壁纸设置</button></>;
}
export function OpenAssistant(){return <><p className="sheet-intro">组网功能请在手机助手 App 中使用。</p><a className="primary-button" href="quiettv-assistant://settings/general?component=tailscale">打开手机助手 App</a><p className="section-note">手机助手首版支持 ARM64 安卓 8 及以上系统。</p></>;}
