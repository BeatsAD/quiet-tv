import { useOperationFeedback } from './OperationFeedback';
import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { ApiError, type PhoneClient, type Task, type Value } from './api';
import { ConfirmDialog, EmptyState, FormDialog, Group, Icon, ResourceImage, Row } from './widgets';
import { AppFilterMenu } from './AppFilterMenu';
import { IconBatch } from './IconBatch';
import { IconMaterialTile, desktopIconShape, galleryIcons, galleryFilterChoices, filterTitles, iconResource, iconTitles, iconVariants, type IconCatalog, type IconFilters, type IconSelection, type IconTarget } from './IconLibraryPicker';
import './icon-library.css';

const galleryFilterLabels={style:'全部风格',shape:'全部形状',form:'全部形式'};
const galleryFilterKeys=['style','shape','form'] as const;
type Operation={id:string;kind:string;name:string;package?:string};
type OperationStatus={kind:string;state:'pending'|'checking'|'success'|'failed'|'unknown';message:string};
export function IconLibrary({client,packageId,pickFile,onSaved,batch,actionsHost}:{
  client:PhoneClient;packageId?:string;pickFile:(kind:string,target:string)=>void;onSaved:()=>void;
  batch?:boolean;actionsHost?:HTMLElement|null;
}){
  const notify=useOperationFeedback();
  const data=client.metadata?.iconLibrary as IconCatalog|undefined;
  const desktopShape=desktopIconShape(client);
  const [batchFilters,setBatchFilters]=useState<IconFilters>({style:'apple',shape:desktopShape,form:'icon'});
  const [filters,setFilters]=useState<IconFilters>({style:'',shape:'',form:''}),[query,setQuery]=useState('');
  const [selection,setSelection]=useState<IconSelection|null>(null),[previewOpen,setPreviewOpen]=useState(false),[actionsOpen,setActionsOpen]=useState(false);
  const [busy,setBusy]=useState(false),[checking,setChecking]=useState(false),[error,setError]=useState('');
  const [readySrc,setReadySrc]=useState(''),[failedSrc,setFailedSrc]=useState(''),[restoreTarget,setRestoreTarget]=useState<IconTarget|null>(null);
  const [status,setStatus]=useState<OperationStatus|null>(null);
  const [operation,setOperation]=useState<Operation|null>(null),[uncertain,setUncertain]=useState(false),[refreshId,setRefreshId]=useState('');
  const submitting=useRef(false),reading=useRef(false),mounted=useRef(true);
  useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;};},[]);
  const target=data?.targets.find(item=>item.id===packageId);
  const batchStyle=data?.styles.includes(batchFilters.style)?batchFilters.style:data?.styles[0]||batchFilters.style;
  const batchForm=desktopShape!=='rectangle'&&batchFilters.form==='text'?'icon':batchFilters.form;
  useEffect(()=>{
    if(batch&&(batchFilters.style!==batchStyle||batchFilters.shape!==desktopShape||batchFilters.form!==batchForm))
      setBatchFilters({style:batchStyle,shape:desktopShape,form:batchForm});
  },[batch,batchStyle,desktopShape,batchForm,batchFilters]);
  const task=client.tasks().find(job=>job.id===operation?.id),active=client.tasks().some(job=>job.domain==='icons'&&['queued','running','ready','receiving'].includes(job.state));
  const refreshTask=client.tasks().find(job=>job.id===refreshId),refreshing=!!refreshId;
  const working=busy||checking||active||refreshing,locked=working||uncertain||!!status||client.status!=='connected'||client.restoring;
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
  function clearSelection(){setSelection(null);setError('');}
  const feedback=!status&&<>{refreshing&&<p className="section-note" role="status">正在刷新图标库…</p>}{error&&<p className="form-error" role="alert">{error}</p>}{task&&['queued','running'].includes(task.state)&&<p className="section-note" role="status">{task.message||'正在替换图标…'}</p>}{uncertain&&<button className="text-action" disabled={busy||checking} onClick={()=>void readResult()}>重新读取当前图标</button>}</>;
  const operationResult=status&&<IconOperationResult status={status} task={task} checking={checking} uncertain={uncertain} onCheck={()=>void readResult()} onClose={()=>setStatus(null)}/>;
  const readCatalog=<button type="button" className="secondary-button" onClick={()=>void client.refreshNow()}>重新读取</button>;
  if(!data)return <EmptyState icon="LayersIcon" title="图标库尚未读取" action={readCatalog}/>;
  if(batch)return <><IconBatch client={client} catalog={data} filters={{style:batchStyle,shape:desktopShape,form:batchForm}} onFilters={setBatchFilters} working={locked} feedback={feedback} onSubmit={value=>submit('batch',value)} actionsHost={actionsHost}/>{operationResult}</>;
  if(!target)return <EmptyState icon="ImageIcon" title="此应用当前不可更换图标"/>;
  const candidate=selection&&data.items.find(item=>item.style===selection.style&&item.id===selection.id);
  const conflict=!!selection&&(selection.package!==target.id||selection.coverKey!==target.coverKey||selection.catalogVersion!==data.revision);
  const canApply=!!(selection&&candidate&&iconVariants(candidate).some(variant=>variant.shape===desktopShape&&variant.form===selection.form));
  const applySelection=selection&&canApply?{...selection,shape:desktopShape}:null;
  const src=applySelection&&!conflict?iconResource(client,applySelection,applySelection.id,applySelection.catalogVersion,true):'';
  const previewError=conflict||!candidate?'当前图标或素材已变化，请返回重新选择。':
    !canApply?'此图标没有适用于当前桌面形状的版本，请返回选择。':
    src&&failedSrc===src?'候选图标读取失败，请返回选择。':'';
  const confirmDisabled=locked||!src||readySrc!==src||!!previewError;
  const optionsByKey=galleryFilterKeys.map(key=>{
    const options=[{value:'',title:galleryFilterLabels[key]},...galleryFilterChoices(data,key)];
    return {key,options,index:Math.max(0,options.findIndex(option=>option.value===filters[key]))};
  });
  const activeFilters={...filters};
  for(const {key,options,index} of optionsByKey)activeFilters[key]=options[index].value;
  const results=galleryIcons(data,activeFilters,query,desktopShape);
  const duplicateNames=new Set<string>(),seenNames=new Set<string>();
  for(const {item} of results){
    if(seenNames.has(item.name))duplicateNames.add(item.name);
    seenNames.add(item.name);
  }
  const targetSrc=client.resource(target.cover||target.icon||'');
  function showPreview(){
    setReadySrc('');setFailedSrc('');setError('');setPreviewOpen(true);
  }
  return <div className="icon-library-page">
    <fieldset className="connection-controls" disabled={locked}>
      <Group>
        <div className="app-icon-target">
          <ResourceImage src={targetSrc} alt=""/>
          <h2>{target.name}</h2>
          <button type="button" className="icon-button app-icon-more" aria-label="更多图标操作" aria-haspopup="dialog"
            onClick={()=>{setError('');setActionsOpen(true);}}><Icon name="DotsHorizontalIcon"/></button>
        </div>
      </Group>
      {!previewOpen&&<>{feedback}{operationResult}</>}
      {!!data.styles.length&&<>
        <div className="app-toolbar-field icon-gallery-search">
          <Icon name="MagnifyingGlassIcon"/>
          <label htmlFor="icon-library-search" className="quiet-sr-only">搜索图标名称</label>
          <input id="icon-library-search" type="text" role="searchbox" inputMode="search" enterKeyHint="search" autoComplete="off"
            autoCorrect="off" spellCheck={false} placeholder="搜索图标名称" value={query}
            onChange={event=>{setQuery(event.target.value);clearSelection();}}/>
          {query&&<button type="button" className="app-search-clear" aria-label="清除搜索"
            onClick={()=>{setQuery('');clearSelection();document.getElementById('icon-library-search')?.focus({preventScroll:true});}}>
            <Icon name="Cross2Icon"/>
          </button>}
        </div>
        <div className="app-icon-filters" role="group" aria-label="图标筛选条件">
          {optionsByKey.map(({key,options,index})=><AppFilterMenu key={key} title={filterTitles[key]}
            options={options.map(option=>option.title)} value={index} disabled={locked}
            onChange={next=>{setFilters({...activeFilters,[key]:options[next].value});clearSelection();}}/>)}
        </div>
      </>}
      {results.length?<div className="icon-material-grid icon-gallery-grid">
        {results.map(({item,filters:choice})=><IconMaterialTile key={`${item.style}:${item.id}`} item={item}
          src={iconResource(client,choice,item.id,data.revision)} disabled={locked}
          selected={selection?.style===item.style&&selection.id===item.id&&selection.form===choice.form}
          styleName={duplicateNames.has(item.name)?data.styleNames?.[item.style]||iconTitles[item.style]||item.style:undefined}
          onSelect={()=>{setError('');setSelection({...choice,package:target.id,coverKey:target.coverKey,id:item.id,catalogVersion:data.revision});}}/>)}
      </div>:<EmptyState icon={data.problem?'ExclamationTriangleIcon':query.trim()?'MagnifyingGlassIcon':'ImageIcon'}
        title={data.problem||(!data.styles.length?'图标库尚未读取':query.trim()?'未找到匹配的图标':'此组合暂无图标')}
        action={!data.styles.length?readCatalog:undefined} alert={!!data.problem}/>}
    </fieldset>
    {actionsHost&&createPortal(<button type="button" className="primary-button" disabled={locked||!candidate}
      onClick={showPreview}>预览替换</button>,actionsHost)}
    {actionsOpen&&<FormDialog title="图标操作" onClose={()=>setActionsOpen(false)} actions={<div className="confirmation-dialog-actions">
      <button type="button" onClick={()=>setActionsOpen(false)}>关闭</button>
    </div>}>
      <Group>
        <Row title="从手机上传图标" icon="UploadIcon" disabled={locked} navigate onClick={()=>{
          setActionsOpen(false);pickFile('icon',[target.id,desktopShape,target.coverKey].join('|'));
        }}/>
        <Row title="恢复默认图标" icon="RotateCcwIcon" disabled={locked||!target.customCover}
          onClick={()=>{setActionsOpen(false);setRestoreTarget(target);}}/>
        <Row title="刷新图标库" icon="ReloadIcon" disabled={locked}
          onClick={()=>{setActionsOpen(false);void refreshIcons();}}/>
      </Group>
    </FormDialog>}
    {previewOpen&&<FormDialog title="替换预览" className="icon-preview-dialog" busy={working} onClose={()=>setPreviewOpen(false)}
      actions={!operationResult&&<div className="confirmation-dialog-actions">
        <button type="button" autoFocus disabled={working} onClick={()=>setPreviewOpen(false)}>返回选择</button>
        <button type="button" className="confirm-button" disabled={confirmDisabled}
          onClick={()=>{if(applySelection&&!confirmDisabled)void submit('apply',applySelection,target.name);}}>确认替换</button>
      </div>}>
      {operationResult||<>
        <div className="icon-replace-comparison app-icon-comparison">
          <div>
            <span className="icon-comparison-label">当前图标</span>
            <div className="app-icon-comparison-art"><ResourceImage src={targetSrc} alt={target.name+'当前图标'}/></div>
          </div>
          <Icon name="ArrowRightIcon"/>
          <div>
            <span className="icon-comparison-label">新图标</span>
            <div className="app-icon-comparison-art">{src&&failedSrc!==src?<ResourceImage key={src} src={src} fetchPriority="high"
              alt={(candidate?.name||'新图标')+'替换预览'} onLoad={()=>setReadySrc(src)} onError={()=>setFailedSrc(src)}/>:
              <Icon name="ImageIcon"/>}</div>
            {candidate&&<span className="app-icon-comparison-name">{candidate.name}</span>}
          </div>
        </div>
        <dl className="app-icon-apply-shape"><dt>应用形状</dt><dd>{iconTitles[desktopShape]}</dd></dl>
        {previewError&&<p className="form-error" role="alert">{previewError}</p>}
        {feedback}
      </>}
    </FormDialog>}
    {restoreTarget&&<ConfirmDialog title="恢复默认图标？" busy={false} disabled={locked} confirmLabel="确认恢复" busyLabel="正在恢复…" destructive={false}
      onCancel={()=>setRestoreTarget(null)} onConfirm={()=>{
        const chosen=restoreTarget;
        setRestoreTarget(null);
        void submit('restore',{package:chosen.id,coverKey:chosen.coverKey},chosen.name);
      }}><p>将 {restoreTarget.name} 恢复为应用自带的图标或横幅。</p></ConfirmDialog>}
  </div>;
}

function IconOperationResult({status,task,checking,uncertain,onCheck,onClose}:{status:OperationStatus;task:Task|undefined;checking:boolean;uncertain:boolean;onCheck:()=>void;onClose:()=>void}){
  const pending=['pending','checking'].includes(status.state),success=status.state==='success';
  const title=status.state==='checking'?'正在核对结果':pending?status.kind==='restore'?'正在恢复默认图标':'正在替换图标':success?status.kind==='restore'?'恢复成功':'替换成功':status.state==='unknown'?'结果尚未确认':status.kind==='restore'?'恢复未完成':'替换未完成';
  const message=status.state==='pending'?task?.message||status.message:status.message;
  return <section className="transfer-status icon-operation-result" aria-busy={pending}><div className={`icon-operation-symbol ${pending?'is-pending':success?'good':status.state==='unknown'?'warning':'danger'}`}><Icon name={pending?'UpdateIcon':success?'CheckCircledIcon':'ExclamationTriangleIcon'}/></div><strong>{title}</strong><div role={status.state==='failed'?'alert':'status'} aria-live="polite" aria-atomic="true"><p>{message}</p></div>{pending&&<progress max={100} value={status.state==='pending'&&task&&task.progress>=0?task.progress:undefined} aria-label={title}/ >}{!pending&&<div className="confirmation-dialog-actions">{status.state==='unknown'&&uncertain&&<button type="button" className="status-confirm" disabled={checking} onClick={onCheck}>重新读取</button>}<button type="button" className={status.state==='unknown'&&uncertain?'':'status-confirm'} onClick={onClose}>{success?'完成':status.state==='unknown'?'查看当前图标':'返回选择'}</button></div>}</section>;
}
