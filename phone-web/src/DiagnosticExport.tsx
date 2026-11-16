import type { PhoneClient, Value } from './api';
import { Group, Icon, fileSize } from './widgets';
import './DiagnosticExport.css';

export function DiagnosticExport({client,run,busy,uploading}:{client:PhoneClient;run:(domain:string,field:string,value?:Value)=>Promise<boolean>;busy:boolean;uploading:boolean}){
  const task=client.tasks().filter(task=>task.domain==='diagnostics'&&task.kind==='export').at(-1);
  const marker=client.tasks().filter(task=>task.domain==='diagnostics'&&task.kind==='mark').at(-1);
  const generating=busy||!!task&&['queued','running','ready'].includes(task.state);
  const download=task?.result?.download;
  const ready=!generating&&task?.state==='completed'&&typeof download==='string'&&download.length>0;
  const failed=!generating&&task?.state==='failed',unknown=!generating&&task?.state==='unknown';
  const status=generating?'正在生成…':ready?'可下载':failed?'生成失败':unknown?'结果待核对':task?.state==='cancelled'?'已取消':'尚未生成';
  const bytes=task?.result?.bytes;
  const blocked=uploading||client.status==='ended'||client.restoring;

  return <div className="diagnostic-export">
    <p className="diagnostic-intro">生成电视诊断记录，用于问题排查。</p>
    <Group>
      <div className="setting-row diagnostic-file">
        <Icon name="FileTextIcon" className="diagnostic-file-icon"/>
        <div className="diagnostic-file-info">
          <strong>{ready?'QuietTV-diagnostics.zip':'诊断文件'}</strong>
          {ready&&<small>诊断包{typeof bytes==='number'?`  ${fileSize(bytes)}`:''}</small>}
        </div>
        {!ready&&<span className="row-value">ZIP</span>}
      </div>
      <div className="setting-row diagnostic-status" role="status" aria-live="polite" aria-atomic="true">
        <span className="row-title">当前状态</span>
        <span className={`diagnostic-state ${ready?'good':failed?'danger':''}`}>
          {generating&&<Icon name="ReloadIcon" className="diagnostic-spinner"/>}
          {ready&&<Icon name="CheckCircledIcon"/>}
          {status}
        </span>
      </div>
    </Group>
    {(failed||unknown)&&task.message&&<p className="form-error" role="alert">{task.message}</p>}
    {ready?<a className="primary-button" download="QuietTV-diagnostics.zip" href={blocked?undefined:client.resource(download)} aria-disabled={blocked} tabIndex={blocked?-1:undefined} onClick={event=>{if(blocked)event.preventDefault();}}><Icon name="DownloadIcon"/>下载到手机</a>:<button className="primary-button" disabled={generating} onClick={()=>unknown?void client.refresh():void run('diagnostics','export')}>{generating?'正在生成…':unknown?'重新读取':failed?'重新生成':'生成诊断文件'}</button>}
    {marker&&<p role="status">{marker.message}</p>}
    <div className="diagnostic-footer">
      <button className="text-action" disabled={busy||blocked} onClick={()=>void run('diagnostics','mark',true)}>标记刚才的问题</button>
      {ready?<button className="text-action" onClick={()=>void run('diagnostics','export')}>重新生成</button>:generating?<p>请保持手机与电视连接</p>:null}
    </div>
  </div>;
}
