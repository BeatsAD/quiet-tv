import { useCallback, useEffect, useId, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import type { PhoneClient, Value } from './api';
import type { View } from './settingsNavigation';
import { EmptyState, Group, Icon, Row, fileSize } from './widgets';

export const FILE_TRANSFER_TITLE='文件传输助手';
const sources=[['received','已接收'],['local','本地存储'],['usb','U 盘']] as const;
export type FileSource=(typeof sources)[number][0];
type Entry={id:string;name:string;bytes:number;directory?:boolean};
type FileState={
  receivedFiles?:Entry[];sourceFiles?:Entry[];fileSource?:FileSource;
  fileFolder?:string;fileFolderId?:string;fileParent?:string;fileBrowseStatus?:string;
};
const activeTasks=new Set(['queued','running','ready']);

export function fileSource(view:View):FileSource {
  const source=view.keys?.[0];
  if(source==='local'||source==='usb')return source;
  return view.id==='file-browser'?'local':'received';
}

function fileType(entry:Entry){
  if(entry.directory)return {label:'文件夹',icon:'FolderIcon',kind:'folder'};
  if(/\.apk(?:\.\d+)?$/i.test(entry.name))return {label:'应用安装包',icon:'CubeIcon',kind:'apk'};
  if(/\.(jpe?g|png|webp)$/i.test(entry.name))return {label:'图片',icon:'ImageIcon',kind:'image'};
  if(/\.(mp4|mov|mkv|webm)$/i.test(entry.name))return {label:'视频',icon:'VideoIcon',kind:'video'};
  if(/\.(zip|json)$/i.test(entry.name))return {label:'文件',icon:'ArchiveIcon',kind:'other'};
  return {label:'文件',icon:'FileIcon',kind:'other'};
}

function FileMenu({name,children,menuDisabled}:{name:string;children:(close:()=>void)=>ReactNode;menuDisabled:boolean}){
  const [open,setOpen]=useState(false),[above,setAbove]=useState(false),id=useId();
  const root=useRef<HTMLDivElement>(null),trigger=useRef<HTMLButtonElement>(null),panel=useRef<HTMLDivElement>(null);
  useEffect(()=>{
    if(!open)return;
    const outside=(event:PointerEvent)=>{if(!root.current?.contains(event.target as Node))setOpen(false);};
    document.addEventListener('pointerdown',outside);
    return ()=>document.removeEventListener('pointerdown',outside);
  },[open]);
  useLayoutEffect(()=>{
    if(!open)return;
    const rect=trigger.current?.getBoundingClientRect(),height=panel.current?.offsetHeight||0;
    const footer=trigger.current?.closest('.quiet-app')?.querySelector('.file-transfer-footer')?.getBoundingClientRect().top;
    setAbove(!!rect&&rect.bottom+height>(footer??window.innerHeight)&&rect.top>height);
    panel.current?.querySelector<HTMLElement>('button,a')?.focus({preventScroll:true});
  },[open]);
  function close(){
    setOpen(false);
    trigger.current?.focus({preventScroll:true});
  }
  return <div className="file-menu-anchor" ref={root} onBlur={event=>{
    if(event.relatedTarget&&!event.currentTarget.contains(event.relatedTarget))setOpen(false);
  }} onKeyDown={event=>{
    if(open&&event.key==='Escape'){event.preventDefault();event.stopPropagation();close();}
  }}>
    <button type="button" ref={trigger} className="icon-button file-menu-trigger" aria-label={`更多操作：${name}`}
      disabled={menuDisabled} aria-expanded={open&&!menuDisabled} aria-controls={open&&!menuDisabled?id:undefined} onClick={()=>setOpen(!open)}>
      <Icon name="DotsHorizontalIcon"/>
    </button>
    {open&&!menuDisabled&&<div className="file-menu" data-above={above} id={id} ref={panel} role="group" aria-label={`${name}的操作`}>
      {children(close)}
    </div>}
  </div>;
}

type Actions={
  client:PhoneClient;
  open:(view:View,nested?:boolean)=>void;
  run:(domain:string,field:string,value?:Value,announce?:boolean)=>Promise<boolean>;
  disabled:boolean;
};

function FileActions({entry,source,client,open,run,disabled,compact=false,previewed=false,suppressPreview=false,close}:Actions&{
  entry:Entry;source:FileSource;compact?:boolean;previewed?:boolean;suppressPreview?:boolean;close?:()=>void;
}){
  const received=source==='received',key=received?entry.id:`source-${entry.id}`;
  const type=fileType(entry).kind,media=type==='image'||type==='video';
  const supportsPreview=!!(client.metadata?.actions as Record<string,unknown>|undefined)?.['files.preview'];
  const route=(id:string,title:string)=>{close?.();open({kind:'tool',id,title,keys:[entry.id,source]},true);};
  function confirm(title:string,field:string,message:string){
    close?.();open({kind:'tool-confirm',id:'files',title,keys:[field],message:JSON.stringify({value:key,message})},true);
  }
  function action(title:string,icon:string,onClick:()=>void,danger=false){
    return compact?<button type="button" className={danger?'danger':undefined} disabled={disabled} onClick={onClick}><Icon name={icon}/>{title}</button>
      :<Row title={title} icon={icon} disabled={disabled} onClick={onClick} danger={danger}/>;
  }
  const download=<a className={compact?undefined:'setting-row'} download={entry.name}
    href={disabled?undefined:client.resource(`api/downloads/file/${encodeURIComponent(key)}`)}
    aria-disabled={disabled||undefined} onClick={()=>close?.()}>
    <Icon name="DownloadIcon" className={compact?undefined:'row-icon'}/><span className={compact?undefined:'row-title'}>下载到手机</span>
  </a>;
  const operations=<>
    {media&&supportsPreview&&!suppressPreview&&action('预览','EyeOpenIcon',()=>route('file-preview','文件预览'))}
    {media&&!compact&&(previewed||!supportsPreview&&received)&&action('设为壁纸','WallpaperIcon',
      ()=>confirm('设为电视壁纸？','wallpaper','电视将校验此文件并将它应用为桌面壁纸。'))}
    {type==='apk'&&(received||supportsPreview)&&action('在电视安装','CubeIcon',
      ()=>confirm('在电视安装应用？','install',received?'请在电视上核对安装包，并完成系统确认。'
        :'电视会先将安装包保存到「已接收」。请在电视上核对安装包，并完成系统确认。'))}
    {download}
    {!received&&action('保存到已接收','FileIcon',()=>{close?.();void run('files','receive',entry.id);})}
    {/\.(json|zip)$/i.test(entry.name)&&!compact&&action('校验为桌面备份','ArchiveIcon',()=>void run('backup','prepareFromFile',key))}
    {received&&!suppressPreview&&action('删除','TrashIcon',()=>confirm('删除接收文件？','remove',`只删除「${entry.name}」的接收副本。已导入的壁纸会保留。`),true)}
  </>;
  return compact?operations:<Group>{operations}</Group>;
}

export function FileTransfer({client,view,open,run,busy,canWrite,setLocation}:Omit<Actions,'disabled'>&{
  view:View;busy:boolean;canWrite:boolean;setLocation:(source:FileSource,id?:string)=>void;
}){
  const source=fileSource(view),received=source==='received',folderId=view.keys?.[1]||'';
  const state=(client.frame?.domains.files||{}) as FileState;
  const name=sources.find(([id])=>id===source)![1];
  const generation=client.frame?.generation;
  const readMatches=state.fileSource===source&&(state.fileFolderId??'')===folderId;
  const result=readMatches?state.fileBrowseStatus||'':'';
  const [requesting,setRequesting]=useState(false),[taskId,setTaskId]=useState(''),[readError,setReadError]=useState('');
  const [authorizing,setAuthorizing]=useState(false),[authorization,setAuthorization]=useState('');
  const [authorizationId,setAuthorizationId]=useState('');
  const start=useRef(false);
  const tasks=client.tasks();
  const task=tasks.find(item=>item.id===taskId);
  const authorizationTask=tasks.find(item=>item.id===authorizationId);
  const waitingForTv=!!authorizationId&&(!authorizationTask||activeTasks.has(authorizationTask.state));
  const authorizationMessage=authorization||authorizationTask?.message||(waitingForTv?'正在打开电视授权':'');
  const working=requesting||!!taskId&&(task?activeTasks.has(task.state):!result);
  const locked=busy||working||authorizing;
  const statusId="file-source-results";
  const initialRead=useRef('');

  const refresh=useCallback(async()=>{
    if(source==='received'||!canWrite||start.current)return;
    start.current=true;setRequesting(true);setReadError('');setAuthorization('');
    try{
      const receipt=await client.command('files','browse',{source,id:folderId},undefined,false);
      setTaskId(typeof receipt.operation?.taskId==='string'?receipt.operation.taskId:'');
      await client.refresh();
    }catch(error){
      setTaskId('');
      setReadError(error instanceof Error?error.message:'请检查电视连接后重试。');
    }finally{start.current=false;setRequesting(false);}
  },[client,source,folderId,canWrite]);

  useEffect(()=>{
    if(!received&&!result&&!locked&&canWrite&&initialRead.current!==generation){
      initialRead.current=generation||'';
      void refresh();
    }
  },[received,result,locked,canWrite,generation,refresh]);

  useEffect(()=>{
    if(authorizationTask?.state!=='completed'||!canWrite)return;
    setAuthorizationId('');
    if(folderId)setLocation(source);
    else void refresh();
  },[authorizationTask?.state,canWrite,folderId,setLocation,source,refresh]);

  async function selectDirectory(){
    if(locked||waitingForTv||!canWrite)return;
    setAuthorizing(true);setAuthorization('');setAuthorizationId('');
    try{
      const receipt=await client.command('files','permission',source,undefined,false);
      if(typeof receipt.operation?.taskId==='string')setAuthorizationId(receipt.operation.taskId);
      else setAuthorization('未能确认电视授权，请重新尝试。');
      await client.refresh(false);
    }catch(error){setAuthorization(error instanceof Error?error.message:'未能打开电视授权，请重新尝试。');}
    finally{setAuthorizing(false);}
  }

  const files=received?state.receivedFiles||[]:result==='ready'||readMatches&&!result&&!working?state.sourceFiles||[]:[];
  const problem=readError||(task&&['failed','unknown','cancelled'].includes(task.state)?task.message:'');
  const directoryReady=result==='ready'&&!problem;
  const caption=readMatches&&state.fileFolder?state.fileFolder:'';
  const directoryButton=(label:string)=><button type="button" className="secondary-button"
    disabled={locked||waitingForTv||!canWrite} onClick={()=>void selectDirectory()}>{label}</button>;
  const retry=<button type="button" className="secondary-button" disabled={locked||!canWrite} onClick={()=>void refresh()}>重新读取</button>;
  const actionProps={client,open,run,disabled:!canWrite||busy};
  function openEntry(entry:Entry){
    if(entry.directory)setLocation(source,entry.id);
    else open({kind:'tool',title:entry.name,id:'file-detail',keys:[entry.id,source]},true);
  }

  return <div className="file-transfer-content">
    <div className="file-source-tabs" role="tablist" aria-label="电视文件来源">
      {sources.map(([id,label],index)=><button type="button" role="tab" key={id} id={`${statusId}-${id}`}
        aria-controls={statusId} aria-selected={source===id} tabIndex={source===id?0:-1} disabled={locked}
        onClick={()=>{if(id!==source)setLocation(id);}} onKeyDown={event=>{
          const next=event.key==='ArrowRight'?(index+1)%sources.length:event.key==='ArrowLeft'?(index+sources.length-1)%sources.length:
            event.key==='Home'?0:event.key==='End'?sources.length-1:-1;
          if(next<0||locked)return;
          event.preventDefault();
          setLocation(sources[next][0]);
          requestAnimationFrame(()=>document.getElementById(`${statusId}-${sources[next][0]}`)?.focus());
        }}>{label}</button>)}
    </div>
    <div id={statusId} role="tabpanel" aria-labelledby={`${statusId}-${source}`} aria-busy={working||undefined}>
      {received?(files.length?<>
        <p className="file-count">{files.length} 个文件</p>
        <div className="file-received-list">{files.map(entry=>{
          const type=fileType(entry);
          return <div className="file-received-row" key={entry.id}>
            <button type="button" className="file-entry" onClick={()=>openEntry(entry)}>
              <Icon name={type.icon}/><span><strong>{entry.name}</strong><small>{type.label}  {fileSize(entry.bytes)}</small></span>
            </button>
            <FileMenu name={entry.name} menuDisabled={!canWrite||busy}>{close=><FileActions {...actionProps}
              compact entry={entry} source={source} close={close}/>}</FileMenu>
          </div>;
        })}</div>
      </>:<EmptyState icon="FolderIcon" title="暂无已接收文件"/>):<>
        <div className="file-location"><span>{name}{caption&&caption!==name?` / ${caption}`:''}</span>
          <button type="button" className="text-action" disabled={locked||!canWrite} onClick={()=>void refresh()}><Icon name="ReloadIcon"/>刷新</button></div>
        {(directoryReady||!!folderId&&!working&&result!=='stale')&&<div className="file-directory-actions">
          <button type="button" className="secondary-button" disabled={locked||!canWrite||!folderId}
            onClick={()=>setLocation(source,directoryReady?state.fileParent||'':'')}><Icon name="ArrowUpIcon"/>{directoryReady?'上一级':'返回根目录'}</button>
          {directoryReady&&directoryButton('选择目录')}
        </div>}
        {working||!result&&!problem&&canWrite&&!readMatches?<EmptyState icon="ReloadIcon" title="正在读取电视文件…"/>
          :!canWrite&&(!readMatches||!result)?<EmptyState icon="UnplugIcon" title="等待重新连接电视"/>
          :problem||result==='failed'?<EmptyState icon="ExclamationTriangleIcon" title="读取目录失败"
            description={problem||'请检查存储连接或目录权限后重试。'} action={retry} alert/>
          :result==='stale'?<EmptyState icon="FolderIcon" title="目录已变化" description="请从存储根目录重新读取。"
            action={<button type="button" disabled={locked||!canWrite} className="secondary-button" onClick={()=>setLocation(source)}>返回根目录</button>}/>
          :result==='permission'?<EmptyState icon="LockClosedIcon" title={waitingForTv?'等待电视授权':'需要读取权限'}
            description={authorizationMessage||
              (source==='usb'?'请在电视允许读取 U 盘。':'请在电视允许读取本地存储。')}
            action={directoryButton('在电视选择目录')}/>
          :result==='unavailable'?<EmptyState icon={source==='usb'?'UnplugIcon':'FolderIcon'}
            title={source==='usb'?'未检测到可读的 U 盘':'没有可读取的本地存储'}
            description={authorizationMessage||
              (source==='usb'?'连接 U 盘后刷新，或在电视选择目录。':'请在电视选择允许读取的目录。')}
            action={directoryButton('在电视选择目录')}/>
          :files.length?<div className="file-grid">{files.map(entry=>{
            const type=fileType(entry);
            return <button type="button" className="file-entry" key={entry.id} disabled={locked} onClick={()=>openEntry(entry)}>
              <Icon name={type.icon}/><span><strong>{entry.name}</strong>{!entry.directory&&<small>{fileSize(entry.bytes)}</small>}</span>
            </button>;
          })}</div>:<EmptyState icon="FolderIcon" title="此目录为空"/>}
        {authorizationMessage&&directoryReady&&<p className="section-note" role="status">{authorizationMessage}</p>}
      </>}
    </div>
  </div>;
}

export function FileDetail({view,...props}:Actions&{view:View}){
  const [id,rawSource]=view.keys||[],source=rawSource==='local'||rawSource==='usb'?rawSource:'received';
  const state=(props.client.frame?.domains.files||{}) as FileState;
  const entry=(source==='received'?state.receivedFiles:state.fileSource===source?state.sourceFiles:[])?.find(item=>item.id===id&&!item.directory);
  const [previewStatus,setPreviewStatus]=useState<'loading'|'ready'|'failed'>('loading');
  if(!entry)return <EmptyState icon="FileIcon" title="文件已不可用" description="返回列表重新读取。"/>;
  const type=fileType(entry);
  if(view.id==='file-preview'){
    const url=props.client.resource(`api/preview-file/${encodeURIComponent(source==='received'?entry.id:`source-${entry.id}`)}`);
    const failed=previewStatus==='failed';
    return <>
      {failed?<EmptyState icon="ImageIcon" title="手机暂无法预览此文件" description="可以下载到手机查看。"/>:
        <div className="file-media-preview">
          {type.kind==='video'?<video src={url} controls playsInline preload="metadata" onLoadedMetadata={()=>setPreviewStatus('ready')}
            onError={()=>setPreviewStatus('failed')}/>:
            <img src={url} alt={entry.name} onLoad={()=>setPreviewStatus('ready')} onError={()=>setPreviewStatus('failed')}/>}
        </div>}
      <FileActions {...props} entry={entry} source={source} previewed={previewStatus==='ready'} suppressPreview/>
    </>;
  }
  return <><div className="file-detail-identity"><Icon name={type.icon}/><div><strong>{entry.name}</strong>
    <p>{type.label}  {fileSize(entry.bytes)}</p></div></div>
    <FileActions {...props} entry={entry} source={source}/>
  </>;
}
