import { SourceSettings } from './SourceSettings';
import { useOperationFeedback } from './OperationFeedback';
import { AdbManagement, adbLabel } from './AdbManagement';
import { useEffect, useState, useRef } from 'react';
import { type PhoneClient, type Value } from './api';
import { Group, Icon, Row, Switch, fileSize } from './widgets';
import { DiagnosticExport } from './DiagnosticExport';
export type ToolView={kind:string;title:string;keys?:string[];id?:string;message?:string};
type Props={client:PhoneClient;view:ToolView;open:(view:ToolView,nested?:boolean)=>void;run:(domain:string,field:string,value?:Value)=>Promise<boolean>;busy:boolean;pickFile:(kind:string,target?:string)=>void;controls:(keys:string[])=>React.ReactNode;back:()=>void;uploading:boolean;drafts:Record<string,string>;newMemo:()=>void};
type Entry={id:string;name:string;bytes:number;directory?:boolean;created?:number;kind?:string;version?:string;assets?:number;assetBytes?:number};
function fileGlyph(entry:Entry){
  if(entry.directory)return 'FolderIcon';
  if(/\.apk(?:\.[0-9]+)?$/i.test(entry.name))return 'CubeIcon';
  if(/\.(jpe?g|png|webp|gif|bmp)$/i.test(entry.name))return 'ImageIcon';
  if(/\.(mp4|mov|mkv|webm|avi)$/i.test(entry.name))return 'VideoIcon';
  if(/\.zip$/i.test(entry.name))return 'ArchiveIcon';
  if(/\.(json|txt|log)$/i.test(entry.name))return 'FileTextIcon';
  return 'FileIcon';
}

function backupKind(kind?:string){
  switch(kind){
    case 'auto':return '自动备份';
    case 'before':return '恢复前备份';
    case 'upgrade':return '升级前备份';
    case 'imported':return '导入备份';
    case 'manual':return '手动备份';
    default:return '';
  }
}

function backupDate(created:number|undefined,timeZone:string,full=false){
  if(!created)return '';
  try{
    const format=new Intl.DateTimeFormat('zh-CN',{
      timeZone,year:'numeric',month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit',hourCycle:'h23',
    });
    const parts=Object.fromEntries(format.formatToParts(created).map(({type,value})=>[type,value]));
    const currentYear=format.formatToParts(Date.now()).find(part=>part.type==='year')?.value;
    return `${full||parts.year!==currentYear?parts.year+'年':''}${parts.month}月${parts.day}日  ${parts.hour}:${parts.minute}`;
  }catch{return new Date(created).toLocaleString('zh-CN',{hour12:false});}
}

export function ToolBody({client,view,open,run,busy,pickFile,controls,back,uploading,drafts,newMemo}:Props){
  const feedback=useOperationFeedback();
  const fileDirectory=useRef('');
  const requestEpoch=useRef(0),draftKey=`${view.id||''}/${view.id==='input-test'?view.keys?.[0]||'':''}`;
  const v=client.values(),[query,setQuery]=useState(''),[places,setPlaces]=useState<{code:string;name:string;area:string}[]>([]),[problem,setProblem]=useState('');
  useEffect(()=>{requestEpoch.current++;setQuery(drafts[draftKey]||'');setPlaces([]);setProblem('');return()=>{requestEpoch.current++;};},[draftKey]);
  const confirm=(title:string,domain:string,field:string,value:Value,message:string)=>open({kind:'tool-confirm',title,id:domain,keys:[field],message:JSON.stringify({value,message})},true);
  const link=(path:string,title='下载到手机')=><a className="text-action download-action" download href={client.resource(path)} onClick={event=>{if(uploading)event.preventDefault();}} aria-disabled={uploading}>{title}<Icon name="DownloadIcon"/></a>;
  const date=(time?:number)=>{if(!time)return '';try{return new Date(time).toLocaleString('zh-CN',{timeZone:String(client.metadata?.timeZone||'UTC'),hour12:false});}catch{return new Date(time).toISOString();}};
  if(view.kind==='tool-confirm'){
    const data=JSON.parse(view.message||'{}'),field=view.keys?.[0];
    const restoring=view.id==='backup'&&field==='restore';
    if(view.id==='backup'&&(restoring||field==='remove')&&Array.isArray(v.backups)&&!(v.backups as Entry[]).some(entry=>entry.id===data.value)){
      return <><p className="sheet-intro">这份备份已不可用。</p><button className="secondary-button" onClick={back}>返回</button></>;
    }
    return <><p className={view.id==='backup'?'sheet-intro backup-confirm-copy':'sheet-intro'}>{data.message}</p><div className="confirmation-dialog-actions">
      <button data-confirm-cancel onClick={back}>取消</button>
      <button className="confirm-button" disabled={busy||restoring&&client.restoring} onClick={async()=>{
        const epoch=requestEpoch.current;
        if(await run(view.id!,field!,data.value)&&epoch===requestEpoch.current&&['inputs','update'].includes(view.id!))back();
      }}>{restoring?'确认恢复':view.id==='backup'&&field==='remove'?'确认删除':'确认'+view.title.replace(/[？?]/g,'')}</button>
    </div></>;
  }
  if(view.id==='city')return <><form onSubmit={async event=>{event.preventDefault();const current=query,request=++requestEpoch.current;setProblem('');try{const result=await client.places(current);if(request===requestEpoch.current){setPlaces(result);feedback({kind:result.length?'success':'info',title:result.length?'城市查询完成':'未找到匹配城市',detail:result.length?`找到 ${result.length} 个结果`:'请换个名称重试'});}}catch(error){if(request===requestEpoch.current){const detail=error instanceof Error?error.message:'城市查询未完成';setProblem(detail);feedback({kind:'error',title:'城市查询失败',detail});}}}}><label>城市或地区<input className="text-input" aria-label="城市或地区" maxLength={60} value={query} onChange={event=>{requestEpoch.current++;drafts[draftKey]=event.target.value;setQuery(event.target.value);}} required/></label><button className="primary-button">查找城市</button></form><Group>{places.map(place=><Row key={place.code} title={place.name} hint={place.area} disabled={busy} onClick={async()=>{if(await run('weather','cityCode',place.code))back();}}/>)}</Group>{problem&&<p className="form-error">{problem}</p>}</>;
  if(view.id==='weather-location')return <Group>{controls(['autoLocation'])}<Row title="手动选择位置" value={String(v.autoLocation?v.locatedCity||'未定位':v.city||'尚未选择')} navigate onClick={()=>open({kind:'tool',id:'city',title:'手动选择位置'},true)}/></Group>;
  if(view.id==='weather-sources'){
    const options=client.metadata?.fields.provider?.options||[];
    const meteoEnabled=v.provider==='openmeteo'||(v.provider==='auto'&&v.meteoFallback)||v.weatherUsesMeteo;
    return <div className="weather-source-settings">
      <div className="weather-source-options" role="group" aria-label="天气来源">
        {[
          ['auto','自动切换'],['qweather','和风天气'],['uapi','UAPI'],
          ['openweather','OpenWeather'],['openmeteo','Open-Meteo'],
        ].map(([id,title])=>{
          const automatic=id==='auto';
          const selected=id===v.provider;
          const available=options.some(option=>option.value===id);
          return <button key={id} type="button" className={automatic?'weather-source-card weather-source-auto':'weather-source-card'}
            aria-pressed={selected} disabled={busy||!available} onClick={()=>{if(!selected)client.update('provider',id);}}>
            {automatic?<Icon name="ReloadIcon"/>:<img className={'weather-mark weather-mark-'+id} src={client.resource('assets/weather-'+id+'.png')} alt=""/>}
            <span className="weather-source-label">
              <strong>{title}</strong>
              {automatic&&!!v.meteoFallback&&<small>Open-Meteo 最后尝试</small>}
              {!automatic&&!available&&<small>未配置</small>}
            </span>
            {selected&&<span className="weather-source-check"><Icon name="CheckIcon"/></span>}
          </button>;
        })}
      </div>
      <details className="weather-service-details"><summary>服务说明</summary>
        {!!v.weatherSource&&<p>当前来源：{String(v.weatherSource)}</p>}
        {Number(v.weatherObservedAt)>0&&<p>数据时间：{date(Number(v.weatherObservedAt))}</p>}
        <p>天气请求会向所选来源发送地点或坐标。手机输入仅在当前局域网会话中接收。</p>
        {!!meteoEnabled&&<p>Open-Meteo 提供模型估算。<a href="https://open-meteo.com/en/terms" target="_blank" rel="noreferrer">免费接口</a>仅限非商业使用，有请求限额。</p>}
        {!!v.weatherUsesMeteo&&<p>天气数据由 <a href="https://open-meteo.com/" target="_blank" rel="noreferrer">Open-Meteo</a> 提供，依据 <a href="https://creativecommons.org/licenses/by/4.0/" target="_blank" rel="noreferrer">CC BY 4.0</a> 使用。</p>}
        <div className="weather-service-links">{!!v.weatherWebsite&&<a className="text-action" href={String(v.weatherWebsite)} target="_blank" rel="noreferrer">天气服务官网</a>}{!!v.weatherTerms&&<a className="text-action" href={String(v.weatherTerms)} target="_blank" rel="noreferrer">隐私与条款</a>}</div>
      </details>
    </div>;
  }
  if(view.id==='weather')return <Group><WeatherSettings client={client} open={open} run={run} busy={busy} controls={controls} nested/></Group>;
  if(view.id==='boot')return <><Group>{controls(['boot'])}</Group><p className="section-note">保存去向后，按电视端的开机与唤醒规则执行。HDMI 需先在信号源配置中确认画面和声音。</p><Group><Row title="配置信号源" navigate onClick={()=>open({kind:'tool',id:'inputs',title:'信号源配置'},true)}/></Group></>;
  if(view.id==='management')return <AdbManagement client={client} run={run} busy={busy}/>;
  if(view.id==='memos'){const notes=(v.notes||[]) as {id:string;text:string;done:boolean}[];return <><Group>{notes.map(note=><div className="setting-row memo-row" key={note.id}><button className="icon-button" aria-label={note.done?'标记未完成':'标记完成'} disabled={busy} onClick={()=>void run('memos','toggle',note.id)}><Icon name={note.done?'CheckCircledIcon':'ClockIcon'}/></button><button className={`row-title ${note.done?'memo-done':''}`} onClick={()=>open({kind:'memo-edit',title:'编辑备忘录',id:note.id,message:note.text},true)}>{note.text}</button><button className="icon-button danger" aria-label="删除备忘录" onClick={()=>confirm('删除备忘录？','memos','remove',note.id,note.text)}><Icon name="TrashIcon"/></button></div>)}</Group>{!notes.length&&<p className="empty-state">暂无备忘录</p>}<button className="primary-button" onClick={newMemo}>新建备忘录</button></>;}
  if(view.id==='files'){
    const received=(v.receivedFiles||[]) as Entry[];
    return <div className="file-transfer-content"><Group title="电视已接收"><div className="file-list">{received.length?received.map(entry=><Row key={entry.id} title={entry.name} hint={fileSize(entry.bytes)} icon={fileGlyph(entry)} navigate onClick={()=>open({kind:'tool',title:entry.name,id:'file-detail',keys:[entry.id,'received']},true)}/>):<p className="empty-state">暂无已接收文件</p>}</div></Group><Group><div className="file-list"><Row title="浏览电视文件" icon="FolderIcon" navigate disabled={busy} onClick={()=>{fileDirectory.current='';open({kind:'tool',title:'浏览电视文件',id:'file-browser'},true);void run('files','browse',{source:v.fileSource==='usb'?'usb':'local',id:''});}}/></div></Group></div>;
  }
  if(view.id==='file-browser'){
    const source=v.fileSource==='usb'?'usb':'local',files=(v.fileSource===source?v.sourceFiles||[]:[]) as Entry[];
    const browse=(nextSource:string,id:string)=>{fileDirectory.current=id;void run('files','browse',{source:nextSource,id});};
    const reading=busy||client.tasks().some(task=>task.domain==='files'&&task.kind==='browse'&&['queued','running','ready'].includes(task.state));
    return <><div className="file-source-tabs" role="group" aria-label="电视文件来源">{[['local','电视存储'],['usb','U 盘']].map(([id,title])=><button key={id} aria-pressed={source===id} disabled={reading} onClick={()=>browse(id,'')}>{title}</button>)}</div><div className="filter-row file-folder-heading"><span>{String(v.fileFolder|| (source==='usb'?'U 盘':'电视存储'))}</span><div className="file-folder-actions">{!!v.fileFolder&&<button className="text-action" disabled={reading} onClick={()=>browse(source,String(v.fileParent||''))}>上一级</button>}<button className="text-action" disabled={reading} onClick={()=>browse(source,fileDirectory.current)}>刷新</button></div></div>{reading&&<p className="section-note" role="status">正在读取电视文件…</p>}{files.length?<Group><div className="file-list">{files.map(entry=><Row key={entry.id} title={entry.name} hint={entry.directory?'文件夹':fileSize(entry.bytes)} icon={fileGlyph(entry)} navigate disabled={reading} onClick={()=>entry.directory?browse(source,entry.id):open({kind:'tool',title:entry.name,id:'file-detail',keys:[entry.id,source]},true)}/>)}</div></Group>:!reading&&<p className="empty-state">暂无可浏览文件</p>}<Group><Row title="在电视授权或选择目录" icon="DesktopIcon" navigate disabled={reading} onClick={()=>void run('files','permission')}/></Group></>;
  }
  if(view.id==='file-detail'){const [id,source]=view.keys!,entries=(source==='received'?v.receivedFiles:v.sourceFiles) as Entry[]||[],entry=entries.find(entry=>entry.id===id);return <><p className="sheet-intro">{entry?.name}</p><p className="section-note">{fileSize(entry?.bytes)}</p><Group><div className="setting-row">{link(`api/downloads/file/${source==='received'?'':'source-'}${id}`)}</div>{source!=='received'?<Row title="保存到已接收" disabled={busy} onClick={()=>void run('files','receive',id)}/>:<><Row title="删除接收文件" danger onClick={()=>confirm('删除接收文件？','files','remove',id,'只删除此接收文件。已导入的壁纸不受影响。')}/>{entry&&/\.(jpe?g|png|webp|mp4|mov|mkv|webm)$/i.test(entry.name)&&<Row title="应用为桌面壁纸" onClick={()=>confirm('应用为桌面壁纸？','files','wallpaper',id,'电视将校验并导入此文件，然后应用为桌面壁纸。')}/ >}{entry&&/\.apk(?:\.[0-9]+)?$/i.test(entry.name)&&<Row title="在电视安装应用" onClick={()=>confirm('安装此应用？','files','install',id,'将按电视现有安装包校验与系统确认流程安装，请核对应用来源及更新信息。')}/>}</>}{entry&&/\.(json|zip)$/i.test(entry.name)&&<Row title="校验为桌面备份" disabled={busy} onClick={()=>void run('backup','prepareFromFile',`${source==='received'?'':'source-'}${id}`)}/>}</Group></>;}
  if(view.id==='backup'||view.id?.startsWith('backup-')){
    const timeZone=String(client.metadata?.timeZone||'UTC');
    const entries=[...((v.backups||[]) as Entry[])].sort((a,b)=>(b.created||0)-(a.created||0));
    const importDrafts=(v.backupDrafts||[]) as Entry[];
    const blocked=busy||uploading||client.restoring||!!v.backupRecoveryError;
    const recordDate=(entry:Entry,full=false)=>backupDate(entry.created,timeZone,full);
    const confirmRestore=(entry:Entry,recent=false)=>confirm(recent?'恢复最近备份？':'恢复这份备份？','backup','restore',entry.id,
      `${recordDate(entry,true)}  ${backupKind(entry.kind)}\n将用这份备份替换当前桌面配置。\n恢复后需要重新扫码连接手机设置。`);

    if(view.id==='backup'){
      const latest=entries[0];
      const restore=v.lastRestore as Record<string,string>|undefined;
      return <>
        <Group>
          <Row title="立即备份" value="保存到电视" disabled={blocked} onClick={()=>void run('backup','create')}/>
          <Row title="恢复最近备份" value={latest?recordDate(latest):'暂无备份'} navigate disabled={blocked||!latest}
            onClick={()=>confirmRestore(latest,true)}/>
          <Row title="全部备份" value={`${entries.length} 份`} navigate onClick={()=>open({kind:'tool',title:'全部备份',id:'backup-history'},true)}/>
          <Row title="导入备份文件" navigate disabled={blocked} onClick={()=>pickFile('backup')}/>
        </Group>
        <Group><Row title="自动备份"><Switch title="自动备份" checked={!!v.backupAutomatic} disabled={blocked}
          onChange={()=>void run('backup','automatic',!v.backupAutomatic)}/></Row></Group>
        {!!v.backupError&&<p className="form-error">{String(v.backupError)}</p>}
        {!!v.backupRecoveryError&&<button className="secondary-button" disabled={busy} onClick={()=>void run('backup','retry')}>重新读取恢复记录</button>}
        {restore?.phase&&<p className="section-note">上次恢复：{({complete:'已完成','rolled-back':'已回滚到恢复前配置',applying:'尚未完成，需要核对',unknown:'结果待核对'} as Record<string,string>)[restore.phase]||restore.phase}</p>}
        {importDrafts.length>0&&<Group title="待核对的导入">{importDrafts.map(entry=><Row key={entry.id} title={recordDate(entry,true)} navigate
          onClick={()=>open({kind:'tool',title:'核对导入备份',id:'backup-draft',keys:[entry.id]},true)}/>)}</Group>}
      </>;
    }
    if(view.id==='backup-history'){
      if(v.backupError)return <p className="form-error">{String(v.backupError)}</p>;
      if(!entries.length)return <p className="empty-state">暂无备份</p>;
      return <Group>{entries.map(entry=><Row key={entry.id} title={recordDate(entry)} value={backupKind(entry.kind)} navigate
        onClick={()=>open({kind:'tool',title:'备份详情',id:'backup-detail',keys:[entry.id]},true)}/>)}</Group>;
    }
    const draft=view.id==='backup-draft',id=view.keys?.[0];
    const selected=(draft?importDrafts:entries).find(entry=>entry.id===id);
    if(!selected){
      const imported=draft&&entries.some(entry=>entry.id===id);
      return <><p className="sheet-intro">{imported?'备份已保存到「全部备份」。':'这份备份已不可用。'}</p>
        <button className="secondary-button" onClick={()=>imported?open({kind:'tool',title:'桌面备份与恢复',id:'backup'}):back()}>返回</button></>;
    }
    if(draft){
      const saving=client.tasks().some(task=>task.domain==='backup'&&task.kind==='admit'&&['queued','running'].includes(task.state));
      return <><p className="sheet-intro">{recordDate(selected,true)}</p>
        <p className="section-note">此文件将保存到「全部备份」。</p>
        <button className="primary-button" disabled={blocked||saving} onClick={()=>void run('backup','admit',selected.id)}>保存备份</button></>;
    }
    if(view.id==='backup-download'){
      return <Group>
        <Row title="仅配置" value="JSON" disabled={blocked} onClick={()=>void run('backup','export',{id:selected.id,media:false})}/>
        <Row title="配置及素材" value="ZIP" disabled={blocked} onClick={()=>void run('backup','export',{id:selected.id,media:true})}/>
      </Group>;
    }
    return <>
      <p className="sheet-intro">{recordDate(selected,true)}</p>
      <p className="section-note">{backupKind(selected.kind)}</p>
      <Group>
        <Row title="恢复到电视" navigate disabled={blocked} onClick={()=>confirmRestore(selected)}/>
        <Row title="下载备份" navigate disabled={blocked} onClick={()=>open({kind:'tool',title:'下载备份',id:'backup-download',keys:[selected.id]},true)}/>
        <Row title="删除备份" danger disabled={blocked} onClick={()=>confirm('删除这份备份？','backup','remove',selected.id,
          `${recordDate(selected,true)}\n删除后无法恢复这份备份。`)}/>
      </Group>
    </>;
  }
  if(view.id==='inputs'||view.id?.startsWith('input-'))return <><SourceSettings client={client} view={view} open={open} run={run} busy={busy} drafts={drafts} controls={controls}/>{!!client.frame?.domains.inputs?.error&&<p className="form-error" role="alert">{String(client.frame.domains.inputs.error)}</p>}</>;
  if(view.id==='diagnostics'||view.id==='diagnostic-specialty'||view.id==='diagnostic-inputs')return <DiagnosticExport key={view.id} page={view.id} client={client} run={run} busy={busy} uploading={uploading}/>;
  return <p className="section-note">当前电视未提供此入口</p>;
}

export function WeatherSettings({client,open,controls,run,busy,nested=false}:Pick<Props,'client'|'open'|'controls'|'run'|'busy'>&{nested?:boolean}){
  const v=client.values();let updated='尚未更新';if(Number(v.weatherFetchedAt)>0)try{updated=new Date(Number(v.weatherFetchedAt)).toLocaleTimeString('zh-CN',{timeZone:client.metadata?.timeZone||'UTC',hour:'2-digit',minute:'2-digit',hour12:false})+' 更新';}catch{updated='时间待核对';}
  const working=client.tasks().some(task=>task.domain==='weather'&&['queued','running','ready'].includes(task.state));
  return <><Row title="天气位置" icon="GlobeIcon" value={`${v.autoLocation?'自动定位':'手动选择'}  ${String(v.autoLocation?v.locatedCity||'未定位':v.city||'尚未选择')}`} navigate onClick={()=>open({kind:'tool',id:'weather-location',title:'天气位置'},nested)}/><Row title="刷新天气" icon="ReloadIcon" value={!v.weatherEnabled?'天气已关闭':working?'正在更新…':updated} disabled={busy||working||!v.weatherEnabled} onClick={()=>void run('weather','refresh')}/><Row title="来源与服务" icon="InfoCircledIcon" value={client.metadata?.fields.provider?.options?.find(item=>item.value===v.provider)?.title||'尚未读取'} navigate onClick={()=>open({kind:'tool',id:'weather-sources',title:'来源与服务'},nested)}/></>;
}

export function WeatherSummary({client}:{client:PhoneClient}){
  const v=client.values();return <div className="weather-background-summary" role="status"><strong>{String(v.locatedCity||v.city||'当前城市')}  {String(v.weatherSceneLabel||'天气待更新')}</strong><span>{String(v.weatherBackgroundState||'天气状态待核对')}  {String(v.weatherPeriod||'昼夜状态待核对')}</span></div>;
}
