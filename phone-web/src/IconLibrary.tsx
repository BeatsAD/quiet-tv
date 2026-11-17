import { useOperationFeedback } from './OperationFeedback';
import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { ApiError, type App, type PhoneClient, type Task, type Value } from './api';
import { ConfirmDialog, Group, Icon, ResourceImage, Row, Sheet } from './widgets';
import { Preview } from './preview';
import { IconBatch } from './IconBatch';
import { IconFilterOptions, IconFiltersBar, IconMaterialGrid, filterTitles, iconResource, iconTitles, type FilterPage, type IconCatalog, type IconFilters, type IconSelection, type IconTarget } from './IconLibraryPicker';
import './icon-library.css';

type Operation={id:string;kind:string;name:string;package?:string};
type OperationStatus={kind:string;state:'pending'|'checking'|'success'|'failed'|'unknown';message:string};
export function IconLibrary({client,pickFile,uploading}:{client:PhoneClient;pickFile:(kind:string,target:string)=>void;uploading:boolean}){
  const notify=useOperationFeedback();
  const data=client.metadata?.iconLibrary as IconCatalog|undefined;
  const [pkg,setPkg]=useState(''),[filters,setFilters]=useState<IconFilters>({style:'apple',shape:'rectangle',form:'icon'}),[selection,setSelection]=useState<IconSelection|null>(null);
  const [view,setView]=useState(''),[filterPage,setFilterPage]=useState<FilterPage>(''),[closing,setClosing]=useState(false),[busy,setBusy]=useState(false),[checking,setChecking]=useState(false),[error,setError]=useState(''),[readySrc,setReadySrc]=useState(''),[previewNonce,setPreviewNonce]=useState(0),[restoreTarget,setRestoreTarget]=useState<IconTarget|null>(null);
  const [status,setStatus]=useState<OperationStatus|null>(null);
  const [operation,setOperation]=useState<Operation|null>(null),[uncertain,setUncertain]=useState(false);
  const submitting=useRef(false),reading=useRef(false),mounted=useRef(true);
  useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;};},[]);
  useEffect(()=>{if(data?.styles.length&&!data.styles.includes(filters.style))setFilters(current=>({...current,style:data.styles[0]}));},[data?.revision,filters.style]);
  const targets=data?.targets||[],target=targets.find(item=>item.id===pkg)||targets.find(item=>((client.values().dockApps||[]) as string[]).includes(item.id))||targets[0];
  const task=client.tasks().find(job=>job.id===operation?.id),active=client.tasks().some(job=>job.domain==='icons'&&['queued','running','ready','receiving'].includes(job.state));
  const working=busy||checking||uploading||active,locked=working||uncertain||!!status;
  const conflict=!!selection&&(selection.package!==target?.id||selection.coverKey!==target?.coverKey||selection.catalogVersion!==data?.revision);
  const showConflict=conflict&&!status&&!operation&&!uncertain;
  const candidate=selection&&data?.items.find(item=>item.style===selection.style&&item.id===selection.id),src=selection?iconResource(client,selection,selection.id,selection.catalogVersion,true):'';
  const page=((client.values().dockApps||[]) as string[]).includes(target?.id||'')?'home':'second';
  function close(){setClosing(true);}
  function exited(){setView('');setClosing(false);setFilterPage('');}
  function open(next:string){if(locked)return;setError('');setFilterPage('');setClosing(false);setView(next);}
  function report(state:OperationStatus['state'],message:string){if(state==='success'||state==='failed'){const restoring=(status?.kind||operation?.kind)==='restore';notify({kind:state==='success'?'success':'error',title:`${restoring?'恢复默认图标':'替换图标'}${state==='success'?'成功':'失败'}`,detail:message});setStatus(null);}else setStatus(previous=>({kind:previous?.kind||operation?.kind||'apply',state,message}));}
  async function readResult(){
    if(reading.current)return;reading.current=true;setChecking(true);report('checking','正在读取电视上的当前图标…');
    try{
      const previous=client.metadata;await client.refresh();if(!mounted.current)return;
      const own=client.tasks().find(job=>job.id===operation?.id);
      if(client.status!=='connected'||client.metadata===previous){setUncertain(true);setError('结果尚未读回，请重新读取当前图标');report('unknown','结果尚未读回，请重新读取当前图标');return;}
      if(own?.state==='completed'&&operation){
        if(operation.kind==='batch'?typeof own.result?.applied!=='number'||own.result.applied<0:!(client.metadata?.iconLibrary as IconCatalog|undefined)?.targets.some(item=>item.id===operation.package&&item.id===own.result?.package)||own.result?.applied!==true){
          setUncertain(true);setError('替换结果待核对，请重新读取当前图标');report('unknown','替换结果待核对，请重新读取当前图标');return;
        }
        report('success',operation.kind==='batch'?`已替换 ${Number(own.result?.applied||0)} 个图标`:operation.kind==='restore'?`已恢复 ${operation.name} 的默认图标`:`已替换 ${operation.name} 的图标`);
        setError('');setSelection(null);setUncertain(false);setOperation(null);close();
      }else if(own&&['failed','cancelled'].includes(own.state)){setError(own.message||'图标替换未完成');report('failed',own.message||'图标替换未完成');setUncertain(false);setOperation(null);}
      else if(!own||own.state==='unknown'){setUncertain(false);setOperation(null);setSelection(null);setError('已读取当前图标，请核对预览；本次替换结果尚未确认');report('unknown','已读取当前图标，请核对预览；本次替换结果尚未确认');close();}
      else {setUncertain(false);setError('');report('pending',own.message||'正在替换图标…');}
    }finally{reading.current=false;if(mounted.current)setChecking(false);}
  }
  useEffect(()=>{if(task?.state==='completed')void readResult();else if(task&&['failed','cancelled'].includes(task.state)){setError(task.message||'图标替换未完成');report('failed',task.message||'图标替换未完成');setUncertain(false);setOperation(null);}else if(task?.state==='unknown'){setUncertain(true);setError(task.message||'替换结果待核对，请重新读取当前图标');report('unknown',task.message||'替换结果待核对，请重新读取当前图标');}},[task?.id,task?.state]);
  useEffect(()=>{if(operation&&client.status!=='connected'&&!busy&&!checking){setUncertain(true);setError('连接中断，替换结果待核对');report('unknown','连接中断，替换结果待核对，请重新读取当前图标');}},[operation?.id,client.status,busy,checking]);
  async function submit(kind:string,value:Value,name=''){
    if(submitting.current||locked)return;submitting.current=true;setBusy(true);setError('');setStatus({kind,state:'pending',message:kind==='restore'?'正在恢复默认图标…':'正在提交替换请求…'});
    try{
      const receipt=await client.command('icons',kind,value);
      if(typeof receipt.operation?.taskId!=='string')throw new Error('电视未返回替换任务，请读取当前图标核对');
      if(!mounted.current)return;const submitted=value as Partial<IconSelection>;setOperation({id:receipt.operation.taskId,kind,name,package:submitted.package});await client.refresh();
      if(mounted.current&&!client.tasks().some(job=>job.id===receipt.operation!.taskId)){setUncertain(true);setError('替换任务尚未读回，请重新读取当前图标');report('unknown','替换任务尚未读回，请重新读取当前图标');}
    }catch(e){if(mounted.current){const message=e instanceof Error?e.message:'结果待核对，请重新读取',unknown=!(e instanceof ApiError&&e.status<500);setError(message);setUncertain(unknown);report(unknown?'unknown':'failed',message);}}
    finally{submitting.current=false;if(mounted.current)setBusy(false);}
  }
  function changeFilters(next:IconFilters){setFilters(next);setSelection(null);setError('');setFilterPage('');}
  const feedback=!status&&<>{error&&<p className="form-error" role="alert">{error}</p>}{task&&['queued','running'].includes(task.state)&&<p className="section-note" role="status">{task.message||'正在替换图标…'}</p>}{uncertain&&<button className="text-action" disabled={busy||checking} onClick={()=>void readResult()}>重新读取当前图标</button>}</>;
  if(!data)return <div className="empty-state"><p>图标库尚未读取</p><button className="text-action" onClick={()=>void client.refreshNow()}>重新读取</button></div>;
  return <div className="icon-library-page"><fieldset className="connection-controls" disabled={locked}>
    <Group title="替换预览" action={<button className="icon-batch-action" aria-label="一键替换" disabled={locked||!targets.length} onClick={()=>open('batch')}><Icon name="GridCheckIcon"/><span className="icon-batch-copy"><strong>一键替换</strong></span></button>}>
      {target?<><div className="icon-replace-comparison"><button aria-label={`目标应用：${target.name}`} onClick={()=>open('target')}><span className="icon-comparison-label">目标应用<Icon name="ChevronDownIcon"/></span><ResourceImage src={client.resource(target.cover||target.icon||'')} alt={`${target.name}当前图标`}/><strong>{target.name}</strong></button><Icon name="ArrowRightIcon"/><button aria-label="选择图标素材" onClick={()=>open('material')}><span className="icon-comparison-label">所选图标<Icon name="ChevronDownIcon"/></span>{candidate?<ResourceImage src={src} alt={candidate.name}/>:<ResourceImage src={client.resource(target.cover||target.icon||'')} alt="当前使用的图标"/>}<strong>{candidate?.name||'选择图标'}</strong></button></div><div className="icon-desktop-preview"><p className="section-note">{page==='home'?'首页效果':'第二屏效果'}</p><Preview client={client} page={page} suspended={uploading} coverOverride={selection&&!conflict?{id:target.id,url:src}:undefined}/>{!((client.metadata?.homeApps||[]) as App[]).some(app=>app.id===target.id)&&<p className="section-note">此应用当前未在桌面显示</p>}</div></>:<p className="empty-state">没有可替换图标的应用</p>}
    </Group>
    <Group><Row title="从手机上传图标" icon="UploadIcon" disabled={!target} navigate onClick={()=>pickFile('icon',`${target.id}|${filters.shape}|${target.coverKey}`)}/><Row title="恢复默认图标" disabled={!target?.customCover} onClick={()=>{if(!locked){setError('');setRestoreTarget(target);}}}/></Group>
  </fieldset>{!view&&feedback}
  {view==='batch'?<IconBatch client={client} catalog={data} filters={filters} onFilters={changeFilters} closing={closing} onClose={close} onExited={exited} working={locked} feedback={feedback} onSubmit={value=>submit('batch',value)}/>:view&&<Sheet className="icon-library-sheet" title={view==='target'?'选择目标应用':filterPage?filterTitles[filterPage]:'图标素材'} nested={!!filterPage} closing={closing} pageKey={`${view}:${filterPage}`} direction={filterPage?1:-1} onBack={()=>setFilterPage('')} onClose={close} onExited={exited}
    toolbar={view==='material'&&!filterPage?<><p className="icon-target-context">替换应用：{target?.name}</p><IconFiltersBar catalog={data} filters={filters} disabled={locked} onOpen={setFilterPage}/></>:undefined}
    footer={view==='material'&&!filterPage?<>{candidate&&<div className="icon-selected-preview"><ResourceImage key={`${src}:${previewNonce}`} src={src} fetchPriority="high" alt={`${candidate.name}替换预览`} onLoad={()=>setReadySrc(src)} onError={()=>{setReadySrc('');setError('候选图标读取失败，请重新选择');}}/><span>{candidate.name}<small>{iconTitles[selection!.shape]}  {iconTitles[selection!.form]}</small></span></div>}{showConflict&&<p className="form-error" role="alert">当前图标或素材已变化，请重新选择图标。</p>}{feedback}<button className="primary-button" disabled={locked||!selection||conflict||readySrc!==src} onClick={()=>void submit('apply',selection!,target?.name)}>确认替换</button></>:undefined}>
    {view==='target'?<div className="icon-target-list">{targets.map(item=><button key={item.id} disabled={locked} aria-pressed={item.id===target?.id} onClick={()=>{setPkg(item.id);setSelection(null);close();}}><ResourceImage src={client.resource(item.cover||item.icon||'')} alt=""/><span>{item.name}</span>{item.id===target?.id&&<Icon name="CheckIcon"/>}</button>)}</div>:filterPage?<IconFilterOptions catalog={data} filters={filters} page={filterPage} disabled={locked} onChange={changeFilters}/>:<IconMaterialGrid client={client} catalog={data} filters={filters} selected={selection?.id||''} disabled={locked||!target} onSelect={item=>{setError('');setReadySrc('');setPreviewNonce(value=>value+1);setSelection({...filters,package:target!.id,coverKey:target!.coverKey,id:item.id,catalogVersion:data.revision});}}/>}
  </Sheet>}{restoreTarget&&<ConfirmDialog title="恢复默认图标？" busy={false} disabled={locked||client.status==='ended'||client.restoring} confirmLabel="确认恢复" busyLabel="正在恢复…" destructive={false} onCancel={()=>setRestoreTarget(null)} onConfirm={()=>{const chosen=restoreTarget;setRestoreTarget(null);void submit('restore',{package:chosen.id,coverKey:chosen.coverKey},chosen.name);}}><p>将 {restoreTarget.name} 恢复为应用自带的图标或横幅。</p></ConfirmDialog>}{status&&<IconOperationDialog status={status} task={task} checking={checking} uncertain={uncertain} onCheck={()=>void readResult()} onClose={()=>{setStatus(null);if(uncertain)close();}}/>}</div>;
}

function IconOperationDialog({status,task,checking,uncertain,onCheck,onClose}:{status:OperationStatus;task:Task|undefined;checking:boolean;uncertain:boolean;onCheck:()=>void;onClose:()=>void}){
  const ref=useRef<HTMLDialogElement>(null),titleId=useId(),messageId=useId(),pending=['pending','checking'].includes(status.state),success=status.state==='success';
  const title=status.state==='checking'?'正在核对结果':pending?status.kind==='restore'?'正在恢复默认图标':'正在替换图标':success?status.kind==='restore'?'恢复成功':'替换成功':status.state==='unknown'?'结果尚未确认':status.kind==='restore'?'恢复未完成':'替换未完成';
  const message=status.state==='pending'?task?.message||status.message:status.message;
  useLayoutEffect(()=>{const dialog=ref.current!;dialog.showModal();return()=>dialog.close();},[]);
  return <dialog ref={ref} className="confirmation-dialog icon-operation-dialog" aria-labelledby={titleId} aria-describedby={messageId} aria-busy={pending} onCancel={event=>event.preventDefault()}><div className={`icon-operation-symbol ${pending?'is-pending':success?'good':status.state==='unknown'?'warning':'danger'}`}><Icon name={pending?'UpdateIcon':success?'CheckCircledIcon':'ExclamationTriangleIcon'}/></div><h2 id={titleId}>{title}</h2><div id={messageId} role={status.state==='failed'?'alert':'status'} aria-live="polite" aria-atomic="true"><p>{message}</p></div>{pending&&<progress max={100} value={status.state==='pending'&&task&&task.progress>=0?task.progress:undefined} aria-label={title}/ >}{!pending&&<div className="confirmation-dialog-actions">{status.state==='unknown'&&uncertain&&<button type="button" className="status-confirm" disabled={checking} onClick={onCheck}>重新读取</button>}<button type="button" autoFocus className={status.state==='unknown'&&uncertain?'':'status-confirm'} onClick={onClose}>{success?'完成':status.state==='unknown'?'查看当前图标':'返回选择'}</button></div>}</dialog>;
}

export function IconUploadPreview({file,shape,busy,onUpload}:{file:File;shape:string;busy:boolean;onUpload:()=>void}){
  const [src,setSrc]=useState('');useEffect(()=>{const type=file.type==='image/jpeg'?'image/jpeg':file.type==='image/webp'?'image/webp':'image/png';const url=URL.createObjectURL(new Blob([file],{type}));setSrc(url);return()=>URL.revokeObjectURL(url);},[file]);
  return <><p className="sheet-intro">{file.name}</p><div className={`icon-upload-preview ${shape}`}><img src={src} alt="上传图标预览"/></div><p className="section-note">{iconTitles[shape]}  等比适配  保留透明背景</p><button className="primary-button" disabled={busy} onClick={onUpload}>确认上传并替换图标</button></>;
}
