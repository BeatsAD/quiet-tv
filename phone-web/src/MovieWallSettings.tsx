import { useEffect, useState } from 'react';
import type { PhoneClient } from './api';
import { Group, Row } from './widgets';

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
    {wall.ids.length>0&&<p className="section-note" role="status">已缓存 {wall.ids.length} 张海报</p>}
    {statusText[wall.status]&&<p className="section-note" role="status">{statusText[wall.status]}</p>}
  </>;
}

export function MovieWallSourceSettings({client,provider,disabled,onSync}:{
  client:PhoneClient;
  provider:MovieSource;
  disabled:boolean;
  onSync:(id:string)=>void;
}) {
  const wall=movieWall(client);
  const source=wall.sources?.[provider]??emptySource;
  const [editing,setEditing]=useState(false);
  const status=wall.operationProvider===provider?statusText[wall.status]:undefined;
  const locked=disabled||wall.busy;
  const smb=provider==='smb';
  if(editing||!source.address)return <MovieWallConnection client={client} provider={provider} source={source}
    disabled={locked} onConnected={()=>setEditing(false)} onCancel={source.address?()=>setEditing(false):undefined}/>;
  const savedShare=wall.sources?.smb;
  const server=provider==='plex'?wall.sources?.plex:wall.sources?.feiniu;
  return <>
    <Group>
      <Row title="连接设置" value={source.address} disabled={locked} navigate onClick={()=>setEditing(true)}/>
      {smb&&<Row title="同步海报" disabled={locked}
        onClick={()=>savedShare?.canSync?onSync('smb'):setEditing(true)}/>}
    </Group>
    {status&&<p className="section-note" role="status">{status}</p>}
    {wall.provider===provider&&wall.ids.length>0&&<p className="section-note">已缓存 {wall.ids.length} 张海报</p>}
    {!smb&&!!server?.libraries.length&&<Group title="片库">
      {server.libraries.map(item=><Row key={item.id} title={item.name} disabled={locked}
        value={wall.provider===provider&&server.library===item.id?'重新同步':'同步'} onClick={()=>onSync(item.id)}/>)}
    </Group>}
  </>;
}

function MovieWallConnection({client,provider,source,disabled,onConnected,onCancel}:{
  client:PhoneClient;
  provider:MovieSource;
  source:Source;
  disabled:boolean;
  onConnected:()=>void;
  onCancel?:()=>void;
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
    {working&&<p className="section-note" role="status">{smb?'正在同步海报…':'正在连接…'}</p>}
    {!working&&(error||changed)&&<p className="form-error" role="alert">{error||'电视上的来源配置已变化，请重新打开连接设置'}</p>}
    <div className={onCancel?'paired-actions':undefined}>
      {onCancel&&<button type="button" className="secondary-button" disabled={working} onClick={onCancel}>取消</button>}
      <button type="submit" className="primary-button" disabled={locked||changed}>{smb?'同步海报':'连接'}</button>
    </div>
  </form>;
}
