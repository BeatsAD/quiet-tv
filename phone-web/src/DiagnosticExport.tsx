import { useEffect, useRef, useState } from 'react';
import type { PhoneClient, Value } from './api';
import { Group, Icon, Row, Switch } from './widgets';
import './DiagnosticExport.css';

type Kind = 'export' | 'exportBluetooth' | 'exportInputs';
type Props = {client:PhoneClient;run:(domain:string,field:string,value?:Value)=>Promise<boolean>;busy:boolean;uploading:boolean};

function Download({client,run,busy,uploading,kind,disabled=false}:{kind:Kind;disabled?:boolean}&Props){
  const [request,setRequest]=useState<{previous?:string}|null>(null);
  const started=useRef(false);
  const link=useRef<HTMLAnchorElement>(null);
  const task=client.tasks().filter(task=>task.domain==='diagnostics'&&task.kind===kind).at(-1);
  const result=request&&task?.id!==request.previous?task:undefined;
  const download=result?.state==='completed'&&result.result?.download;
  const path=typeof download==='string'?download:'';
  const blocked=uploading||client.status==='ended'||client.restoring;
  const failed=!!result&&['failed','cancelled','unknown'].includes(result.state);
  const generating=!!request&&!path&&!failed;

  useEffect(()=>{
    if(path&&link.current&&!blocked&&!started.current){
      started.current=true;
      link.current.click();
    }
  },[path,blocked]);

  const label=kind==='exportInputs'?'下载信号源诊断包':kind==='exportBluetooth'?'下载蓝牙诊断':'下载日志';
  async function start(){
    started.current=false;
    setRequest({previous:task?.id});
    if(!await run('diagnostics',kind,true))setRequest(null);
  }

  return <>
    {path?<a ref={link} className="primary-button" download href={blocked?undefined:client.resource(path)} aria-disabled={blocked} tabIndex={blocked?-1:undefined}>
      <Icon name="DownloadIcon"/>{label}
    </a>:<button className="primary-button" disabled={busy||blocked||disabled||generating} onClick={()=>void start()}>
      <Icon name={generating?'ReloadIcon':'DownloadIcon'} className={generating?'diagnostic-spinner':undefined}/>{generating?'正在生成…':label}
    </button>}
    {failed&&<p className="form-error" role="alert">{result.message||'日志生成未完成，请重试'}</p>}
  </>;
}

export function DiagnosticExport({page,...props}:Props&{page:'diagnostics'|'diagnostic-specialty'|'diagnostic-inputs'}){
  const {client,run,busy,uploading}=props;
  const state=client.frame?.domains.diagnostics;
  const enabled=state?.recording===true,active=state?.recordingActive===true;
  const blocked=busy||uploading||client.status==='ended'||client.restoring;
  const mark=client.tasks().filter(task=>task.domain==='diagnostics'&&task.kind==='mark').at(-1);
  const starting=!!mark&&['queued','running','ready'].includes(mark.state);
  const [markRequested,setMarkRequested]=useState(false);
  if(page==='diagnostic-specialty')return <div className="diagnostic-export">
    <Group>
      <Row title="短时录制" value={active?'正在录制':undefined} hint={!enabled?'请先在“诊断日志”中开启记录。':active?'请复现问题。录制结束后从“诊断日志”下载。':'开始录制后，在 30 秒内复现问题。完成后从“诊断日志”下载。'}/>
      <Row title={starting?'正在启动…':'录制 30 秒'} disabled={blocked||!enabled||active||starting} onClick={()=>{setMarkRequested(true);void run('diagnostics','mark',true);}}/>
    </Group>
    {markRequested&&mark?.state==='failed'&&<p className="form-error" role="alert">{mark.message}</p>}
    {state?.bluetoothPermissionRequired===true&&<p className="section-note">排查蓝牙遥控器或配对问题时，请在电视的“专项排查”中选择“允许读取蓝牙状态”。</p>}
    <Download {...props} kind="exportBluetooth" disabled={!enabled}/>
  </div>;
  if(page==='diagnostic-inputs')return <div className="diagnostic-export">
    {state?.inputEvidence!==true&&<p className="section-note">暂无信号源配对或测试记录。</p>}
    {state?.inputRecording===true&&<p className="section-note">请在信号源记录结束后下载。</p>}
    <Download {...props} kind="exportInputs" disabled={state?.inputEvidence!==true||state.inputRecording===true}/>
  </div>;
  return <div className="diagnostic-export">
    <Group><Row title="记录诊断日志"><Switch title="记录诊断日志" checked={enabled} disabled={blocked||typeof state?.recording!=='boolean'} onChange={()=>void run('diagnostics','recording',!enabled)}/></Row></Group>
    <Download {...props} kind="export"/>
  </div>;
}
