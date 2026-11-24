import { useOperationFeedback } from './OperationFeedback';
import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { ApiError, type PhoneClient, type Task, type Value } from './api';
import { ConfirmDialog, Group, Icon, ResourceImage, Row, Sheet } from './widgets';
import { IconBatch } from './IconBatch';
import { IconFilterOptions, IconFiltersBar, IconMaterialGrid, desktopIconShape, filteredIcons, filterTitles, iconResource, type FilterPage, type IconCatalog, type IconFilters, type IconSelection, type IconTarget } from './IconLibraryPicker';
import './icon-library.css';

type Operation={id:string;kind:string;name:string;package?:string};
type OperationStatus={kind:string;state:'pending'|'checking'|'success'|'failed'|'unknown';message:string};
export function IconLibrary({client,packageId,pickFile,onSaved,batch,closing,onClose,onExited}:{
  client:PhoneClient;packageId?:string;pickFile:(kind:string,target:string)=>void;onSaved:()=>void;
  batch?:boolean;closing?:boolean;onClose?:()=>void;onExited?:()=>void;
}){
  const notify=useOperationFeedback();
  const data=client.metadata?.iconLibrary as IconCatalog|undefined;
  const desktopShape=desktopIconShape(client);
  const [filters,setFilters]=useState<IconFilters>({style:'apple',shape:desktopShape,form:'icon'}),[selection,setSelection]=useState<IconSelection|null>(null);
  const [filterPage,setFilterPage]=useState<FilterPage>(''),[busy,setBusy]=useState(false),[checking,setChecking]=useState(false),[error,setError]=useState(''),[readySrc,setReadySrc]=useState(''),[previewNonce,setPreviewNonce]=useState(0),[restoreTarget,setRestoreTarget]=useState<IconTarget|null>(null);
  const [status,setStatus]=useState<OperationStatus|null>(null);
  const [operation,setOperation]=useState<Operation|null>(null),[uncertain,setUncertain]=useState(false),[refreshId,setRefreshId]=useState('');
  const submitting=useRef(false),reading=useRef(false),mounted=useRef(true);
  useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;};},[]);
  useEffect(()=>{if(data?.styles.length&&!data.styles.includes(filters.style))setFilters(current=>({...current,style:data.styles[0]}));},[data?.revision,filters.style]);
  const target=data?.targets.find(item=>item.id===packageId);
  const availableForms=data?(desktopShape==='rectangle'?['icon','text','transparent']:['icon','transparent']).filter(form=>filteredIcons(data,{...filters,shape:desktopShape,form}).length>0):[];
  const form=availableForms.includes(filters.form)?filters.form:availableForms[0]||'icon';
  const effectiveFilters={...filters,shape:desktopShape,form};
  useEffect(()=>{
    if(filters.shape!==desktopShape||filters.form!==form){
      setFilters(current=>({...current,shape:desktopShape,form}));
      setSelection(null);
      setReadySrc('');
      setFilterPage('');
    }
  },[desktopShape,filters.shape,filters.form,form]);
  const task=client.tasks().find(job=>job.id===operation?.id),active=client.tasks().some(job=>job.domain==='icons'&&['queued','running','ready','receiving'].includes(job.state));
  const refreshTask=client.tasks().find(job=>job.id===refreshId),refreshing=!!refreshId;
  const working=busy||checking||active||refreshing,locked=working||uncertain||!!status||client.status!=='connected'||client.restoring;
  const conflict=!!selection&&(selection.package!==target?.id||selection.coverKey!==target?.coverKey||selection.catalogVersion!==data?.revision);
  const showConflict=conflict&&!status&&!operation&&!uncertain;
  const shapeMismatch=!!selection&&selection.shape!==desktopShape;
  const candidate=selection&&data?.items.find(item=>item.style===selection.style&&item.id===selection.id),src=selection?iconResource(client,selection,selection.id,selection.catalogVersion,true):'';
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
        setError('');setSelection(null);setUncertain(false);setOperation(null);onSaved();
      }else if(own&&['failed','cancelled'].includes(own.state)){setError(own.message||'图标替换未完成');report('failed',own.message||'图标替换未完成');setUncertain(false);setOperation(null);}
      else if(!own||own.state==='unknown'){setUncertain(false);setOperation(null);setSelection(null);setError('已读取当前图标，请核对预览；本次替换结果尚未确认');report('unknown','已读取当前图标，请核对预览；本次替换结果尚未确认');}
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
  useEffect(()=>{
    if(!refreshTask||!['completed','failed','cancelled','unknown'].includes(refreshTask.state))return;
    const success=refreshTask.state==='completed';
    notify({kind:success?'success':refreshTask.state==='unknown'?'info':'error',title:success?refreshTask.message:'图标库刷新未完成',detail:success?undefined:refreshTask.message});
    if(!success)setError(refreshTask.message);setRefreshId('');
  },[refreshTask?.id,refreshTask?.state]);
  async function refreshIcons(){
    if(submitting.current||locked||client.status!=='connected')return;
    submitting.current=true;setBusy(true);setError('');
    try{
      const receipt=await client.command('icons','refresh',{});
      if(typeof receipt.operation?.taskId!=='string')throw new Error('刷新结果待核对，请重新读取图标库');
      if(!mounted.current)return;setRefreshId(receipt.operation.taskId);await client.refresh();
    }catch(error){if(mounted.current){const message=error instanceof Error?error.message:'刷新结果待核对';setError(message);notify({kind:error instanceof ApiError&&error.status<500?'error':'info',title:'图标库刷新未完成',detail:message});}}
    finally{submitting.current=false;if(mounted.current)setBusy(false);}
  }
  function changeFilters(next:IconFilters){setFilters(next);setSelection(null);setError('');setFilterPage('');}
  const feedback=!status&&<>{error&&<p className="form-error" role="alert">{error}</p>}{task&&['queued','running'].includes(task.state)&&<p className="section-note" role="status">{task.message||'正在替换图标…'}</p>}{uncertain&&<button className="text-action" disabled={busy||checking} onClick={()=>void readResult()}>重新读取当前图标</button>}</>;
  const operationDialog=status&&<IconOperationDialog status={status} task={task} checking={checking} uncertain={uncertain} onCheck={()=>void readResult()} onClose={()=>setStatus(null)}/>;
  if(batch&&onClose&&onExited){
    if(!data)return <IconBatchUnavailable closing={!!closing} onClose={onClose} onExited={onExited} refresh={()=>void client.refreshNow()}/>;
    return <><IconBatch client={client} catalog={data} filters={effectiveFilters} onFilters={changeFilters} closing={!!closing} onClose={onClose} onExited={onExited} working={locked} feedback={feedback} onSubmit={value=>submit('batch',value)}/>{operationDialog}</>;
  }
  if(!data)return <div className="empty-state"><p>图标库尚未读取</p><button className="text-action" onClick={()=>void client.refreshNow()}>重新读取</button></div>;
  if(!target)return <p className="empty-state" role="status">此应用当前不可更换图标</p>;
  return <div className="icon-library-page">
    <fieldset className="connection-controls" disabled={locked}>
      {filterPage?<><Row title={'返回'+filterTitles[filterPage]} onClick={()=>setFilterPage('')}/>
        <IconFilterOptions catalog={data} filters={effectiveFilters} page={filterPage} forms={availableForms} disabled={locked} onChange={changeFilters}/></>
      :<>
        <div className="app-icon-target"><ResourceImage src={client.resource(target.cover||target.icon||'')} alt=""/><strong>{target.name}</strong></div>
        <IconFiltersBar catalog={data} filters={effectiveFilters} disabled={locked} shapeLocked forms={availableForms} onOpen={setFilterPage}/>
         <div className="app-icon-materials"><IconMaterialGrid client={client} catalog={data} filters={effectiveFilters} selected={selection?.id||''} disabled={locked} onSelect={item=>{
          setError('');setReadySrc('');setPreviewNonce(value=>value+1);
          setSelection({...effectiveFilters,package:target.id,coverKey:target.coverKey,id:item.id,catalogVersion:data.revision});
        }}/></div>
        {candidate&&selection&&<div className="icon-replace-comparison app-icon-comparison">
          <div><span className="icon-comparison-label">当前图标</span><ResourceImage src={client.resource(target.cover||target.icon||'')} alt={target.name+'当前图标'}/></div>
          <Icon name="ArrowRightIcon"/>
          <div><span className="icon-comparison-label">新图标</span>
            <ResourceImage key={src+':'+previewNonce} src={src} fetchPriority="high" alt={candidate.name+'替换预览'} onLoad={()=>setReadySrc(src)} onError={()=>{setReadySrc('');setError('候选图标读取失败，请重新选择');}}/>
          </div>
        </div>}
        {showConflict&&<p className="form-error" role="alert">当前图标或素材已变化，请重新选择图标。</p>}
        {shapeMismatch&&<p className="form-error" role="alert">桌面图标形状已变化，请重新选择图标。</p>}
        <Group>
          <Row title="从手机上传图标" icon="UploadIcon" navigate onClick={()=>pickFile('icon',[target.id,desktopShape,target.coverKey].join('|'))}/>
          <Row title="恢复默认图标" disabled={!target.customCover} onClick={()=>{setError('');setRestoreTarget(target);}}/>
          <Row title="刷新图标库" icon="ReloadIcon" value={refreshing?'正在刷新…':''} onClick={()=>void refreshIcons()}/>
        </Group>
        <button className="primary-button" disabled={locked||!selection||conflict||shapeMismatch||readySrc!==src} onClick={()=>void submit('apply',selection!,target.name)}>确认替换</button>
      </>}
    </fieldset>
    {feedback}
    {restoreTarget&&<ConfirmDialog title="恢复默认图标？" busy={false} disabled={locked} confirmLabel="确认恢复" busyLabel="正在恢复…" destructive={false}
      onCancel={()=>setRestoreTarget(null)} onConfirm={()=>{
        const chosen=restoreTarget;
        setRestoreTarget(null);
        void submit('restore',{package:chosen.id,coverKey:chosen.coverKey},chosen.name);
      }}><p>将 {restoreTarget.name} 恢复为应用自带的图标或横幅。</p></ConfirmDialog>}
    {operationDialog}
  </div>;
}

function IconBatchUnavailable({closing,onClose,onExited,refresh}:{closing:boolean;onClose:()=>void;onExited:()=>void;refresh:()=>void}){
  return <Sheet title="批量换图标" nested={false} closing={closing} pageKey="icon-batch-unavailable" direction={1} onBack={onClose} onClose={onClose} onExited={onExited}>
    <div className="empty-state"><p>图标库尚未读取</p><button className="text-action" onClick={refresh}>重新读取</button></div>
  </Sheet>;
}

function IconOperationDialog({status,task,checking,uncertain,onCheck,onClose}:{status:OperationStatus;task:Task|undefined;checking:boolean;uncertain:boolean;onCheck:()=>void;onClose:()=>void}){
  const ref=useRef<HTMLDialogElement>(null),titleId=useId(),messageId=useId(),pending=['pending','checking'].includes(status.state),success=status.state==='success';
  const title=status.state==='checking'?'正在核对结果':pending?status.kind==='restore'?'正在恢复默认图标':'正在替换图标':success?status.kind==='restore'?'恢复成功':'替换成功':status.state==='unknown'?'结果尚未确认':status.kind==='restore'?'恢复未完成':'替换未完成';
  const message=status.state==='pending'?task?.message||status.message:status.message;
  useLayoutEffect(()=>{const dialog=ref.current!;dialog.showModal();return()=>dialog.close();},[]);
  return <dialog ref={ref} className="confirmation-dialog icon-operation-dialog" aria-labelledby={titleId} aria-describedby={messageId} aria-busy={pending} onCancel={event=>event.preventDefault()}><div className={`icon-operation-symbol ${pending?'is-pending':success?'good':status.state==='unknown'?'warning':'danger'}`}><Icon name={pending?'UpdateIcon':success?'CheckCircledIcon':'ExclamationTriangleIcon'}/></div><h2 id={titleId}>{title}</h2><div id={messageId} role={status.state==='failed'?'alert':'status'} aria-live="polite" aria-atomic="true"><p>{message}</p></div>{pending&&<progress max={100} value={status.state==='pending'&&task&&task.progress>=0?task.progress:undefined} aria-label={title}/ >}{!pending&&<div className="confirmation-dialog-actions">{status.state==='unknown'&&uncertain&&<button type="button" className="status-confirm" disabled={checking} onClick={onCheck}>重新读取</button>}<button type="button" autoFocus className={status.state==='unknown'&&uncertain?'':'status-confirm'} onClick={onClose}>{success?'完成':status.state==='unknown'?'查看当前图标':'返回选择'}</button></div>}</dialog>;
}
