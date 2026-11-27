import { useEffect, useLayoutEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import type { PhoneClient } from './api';
import { Group, Icon, Row } from './widgets';

export type MovieSource = 'feiniu' | 'plex' | 'smb';
export const movieSourceTitles:Record<MovieSource,string> = {feiniu:'媒体服务器',plex:'Plex',smb:'NAS 共享文件夹（SMB）'};

type Source = {address:string;username:string};
type Library = {id:string;name:string};
type MediaSource = Source & {library:string;libraries:Library[]};

type MovieWall = {
  connected: boolean;
  busy: boolean;
  status: string;
  provider?: MovieSource;
  operation?: number;
  operationProvider?: MovieSource;
  syncProgress?: {library:string;processed:number;total:number}|null;
  sources?: {
    feiniu: MediaSource;
    plex?: MediaSource;
    smb: Source & {canSync:boolean};
  };
  ids: string[];
  revision: string;
};

const emptyWall: MovieWall = {connected:false,busy:false,status:'',ids:[],revision:''};
const emptySource = {address:'',username:''};

export function movieWall(client: PhoneClient) {
  return (client.frame?.domains.movieWall?.movieWall as MovieWall | undefined) ?? emptyWall;
}

const statusText: Record<string,string> = {
  working: '正在连接或同步',
  unauthorized: '认证失败，请检查账号和密码后重新连接',
  'plex-auth': 'Plex 授权失败，请检查访问令牌',
  'smb-auth': '无法读取共享文件夹，请检查账号和权限',
  'smb-folder': '请检查 SMB 共享地址',
  'smb-empty': '共享目录里没有可用海报，请检查 poster.jpg 等图片',
  'smb-failed': '读取共享失败，请检查网络与 SMB 服务',
  'empty-libraries': '该账号暂无可用媒体库',
  'empty-movies': '此媒体库暂无电影',
  'empty-posters': '此媒体库暂无可用海报',
  failed: '同步失败，请检查服务器地址和网络',
};

export function MovieWallSettings({client,onOpen}:{
  client:PhoneClient;
  onOpen:(provider:MovieSource)=>void;
}) {
  const wall=movieWall(client);
  return <>
    <Group>
      {(['feiniu','plex','smb'] as const).map(provider=><Row key={provider} title={movieSourceTitles[provider]}
        value={wall.connected&&wall.provider===provider?'使用中':wall.sources?.[provider]?.address?'已连接':undefined}
        navigate onClick={()=>onOpen(provider)}/>)}
    </Group>
    {!wall.busy&&wall.ids.length>0&&<p className="section-note" role="status">已缓存 {wall.ids.length} 张海报</p>}
    {statusText[wall.status]&&<p className="section-note" role="status">{statusText[wall.status]}</p>}
  </>;
}

export function MovieWallSourceSettings({client,provider,disabled,footerHost}:{
  client:PhoneClient;
  provider:MovieSource;
  disabled:boolean;
  footerHost:HTMLDivElement|null;
}) {
  const wall=movieWall(client);
  const source=wall.sources?.[provider]??emptySource;
  const server=provider==='plex'?wall.sources?.plex:wall.sources?.feiniu;
  const [editing,setEditing]=useState(false);
  const [pending,setPending]=useState('');
  const [requestError,setRequestError]=useState<{operation?:number;message:string}>();
  const smb=provider==='smb';
  const form=editing||!source.address;
  const ownOperation=wall.operationProvider===provider;
  const status=ownOperation?wall.status:'';
  const progress=ownOperation?wall.syncProgress:undefined;
  const working=ownOperation&&wall.busy;
  const syncing=!!pending||working&&(smb||!!progress||!form&&wall.syncProgress===undefined);
  const activeLibrary=syncing?(working&&progress?.library||pending):'';
  const reading=smb?'正在扫描共享文件夹':'正在读取片库';
  const locked=disabled||wall.busy||!!pending;
  const localFailure=requestError?.operation===wall.operation?requestError?.message:undefined;
  const finished=!form&&!syncing&&!wall.busy&&!localFailure&&status==='synced'&&wall.provider===provider;
  const failure=!form&&!syncing&&!wall.busy&&status!=='working'
    ? localFailure||statusText[status]
    : undefined;

  useLayoutEffect(()=>{
    if(pending)footerHost?.closest('.sheet-panel')?.querySelector('.movie-wall-syncing')
      ?.closest('.setting-row')?.scrollIntoView({block:'nearest'});
  },[pending,footerHost]);

  async function sync(id:string) {
    if(locked)return;
    setPending(id);
    setRequestError(undefined);
    try{
      await client.command('movieWall',smb?'syncSmb':'syncLibrary',id,undefined,false);
    }catch(error){
      setRequestError({operation:wall.operation,message:error instanceof Error?error.message:'未能确认同步，请检查电视连接'});
    }finally{
      setPending('');
    }
  }

  let feedback;
  if(syncing){
    const connected=client.status==='connected';
    const caching=connected&&working&&progress&&progress.total>0?progress:undefined;
    const title=!connected?'等待电视连接':caching?'正在缓存海报':!activeLibrary&&!smb?'正在连接或同步':reading;
    const library=server?.libraries.find(item=>item.id===activeLibrary)?.name;
    const detail=caching?`已处理 ${caching.processed} 张，共 ${caching.total} 张`:library;
    feedback=<div className="movie-wall-sync-feedback" data-phase={caching?'caching':'reading'}>
      {!caching&&<Icon name="UpdateIcon"/>}
      <div><strong role="status">{title}</strong>{detail&&<span>{detail}</span>}</div>
      {caching&&<progress aria-label="海报缓存进度" value={caching.processed} max={caching.total}/>}
    </div>;
  }else if(finished||failure){
    feedback=<div className="movie-wall-sync-feedback" data-phase={finished?'complete':'error'} role={finished?'status':'alert'}>
      <Icon name={finished?'CheckCircledIcon':'ExclamationTriangleIcon'}/>
      <div><strong>{finished?'同步完成':'同步未完成'}</strong>
        <span>{finished?`已缓存 ${wall.ids.length} 张海报`:failure}</span></div>
    </div>;
  }
  const fixedFeedback=footerHost&&feedback?createPortal(feedback,footerHost):null;
  if(form)return <>
    <MovieWallConnection client={client} provider={provider} source={source} disabled={disabled||wall.busy}
      onSyncPending={setPending} onConnected={()=>setEditing(false)} onCancel={source.address?()=>setEditing(false):undefined}/>
    {fixedFeedback}
  </>;
  return <>
    <Group>
      <Row title="连接设置" value={source.address} disabled={locked} navigate onClick={()=>{setRequestError(undefined);setEditing(true);}}/>
      {smb&&<Row title="同步海报" disabled={locked} value={syncing?<span className="movie-wall-syncing">同步中</span>:undefined}
        onClick={()=>wall.sources?.smb.canSync?void sync('smb'):setEditing(true)}/>}
    </Group>
    {!syncing&&!finished&&wall.provider===provider&&wall.ids.length>0&&<p className="section-note">已缓存 {wall.ids.length} 张海报</p>}
    {!smb&&!!server?.libraries.length&&<Group title="片库">
      {server.libraries.map(item=><Row key={item.id} title={item.name} disabled={locked}
        value={activeLibrary===item.id?<span className="movie-wall-syncing">同步中</span>:
          wall.provider===provider&&server.library===item.id?'重新同步':'同步'} onClick={()=>void sync(item.id)}/>)}
    </Group>}
    {fixedFeedback}
  </>;
}

function MovieWallConnection({client,provider,source,disabled,onConnected,onCancel,onSyncPending}:{
  client:PhoneClient;
  provider:MovieSource;
  source:Source;
  disabled:boolean;
  onConnected:()=>void;
  onCancel?:()=>void;
  onSyncPending:(id:string)=>void;
}) {
  const wall=movieWall(client);
  const smb=provider==='smb';
  const plex=provider==='plex';
  const [initial]=useState(source);
  const [address,setAddress]=useState(source.address);
  const [username,setUsername]=useState(source.username);
  const [password,setPassword]=useState('');
  const [submitting,setSubmitting]=useState(false);
  const [operation,setOperation]=useState<number>();
  const [error,setError]=useState('');
  const changed=source.address!==initial.address||source.username!==initial.username;
  const working=submitting||operation!==undefined;
  const locked=disabled||working;
  useEffect(()=>{
    if(!smb||!working)return;
    onSyncPending('smb');
    return ()=>onSyncPending('');
  },[smb,working,onSyncPending]);
  useEffect(()=>{
    if(operation===undefined||(wall.operation??0)<operation||wall.busy)return;
    setOperation(undefined);
    if(wall.operation!==operation)return;
    if(wall.status==='synced'||wall.status==='connected'||wall.status==='empty-libraries')onConnected();
    else setError(statusText[wall.status]||'连接未完成，请重试');
  },[operation,wall.operation,wall.busy,wall.status,onConnected]);
  return <form autoComplete="off" onSubmit={async event=>{
    event.preventDefault();
    if(locked||changed)return;
    setError('');setSubmitting(true);setPassword('');
    try{
      setOperation(await client.connectMovieWall(provider,address.trim(),username.trim(),password));
    }catch(failure){
      setError(failure instanceof Error?failure.message:'连接未完成，请重试');
    }finally{setSubmitting(false);}
  }}>
    <fieldset className="connection-controls" disabled={locked}>
      <label>{smb?'共享地址':'服务器地址'}<input className="text-input" inputMode="url" autoCapitalize="none"
        spellCheck={false} maxLength={1024} required value={address} placeholder={smb?'smb://NAS/Movies':undefined}
        onChange={event=>setAddress(event.target.value)}/></label>
      {!plex&&<label>{smb?'账号（访客留空）':'账号'}<input className="text-input" autoCapitalize="none"
        maxLength={256} required={!smb} value={username} onChange={event=>setUsername(event.target.value)}/></label>}
      <label>{plex?'访问令牌':'密码'}<input className="text-input" type="password" autoComplete="new-password"
        maxLength={plex?2048:512} required={plex} placeholder={provider==='feiniu'?'未设置时留空':undefined} value={password}
        onChange={event=>setPassword(event.target.value)}/></label>
    </fieldset>
    {working&&!smb&&<p className="section-note" role="status">正在连接…</p>}
    {!working&&(error||changed)&&<p className="form-error" role="alert">{error||'电视上的来源配置已变化，请重新打开连接设置'}</p>}
    <div className={onCancel?'paired-actions':undefined}>
      {onCancel&&<button type="button" className="secondary-button" disabled={working} onClick={onCancel}>取消</button>}
      <button type="submit" className="primary-button" disabled={locked||changed}>{smb?'同步海报':'连接'}</button>
    </div>
  </form>;
}
