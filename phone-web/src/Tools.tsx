import { SourceSettings } from './SourceSettings';
import { useOperationFeedback } from './OperationFeedback';
import { AdbManagement } from './AdbManagement';
import { useEffect, useState, useRef } from 'react';
import { type PhoneClient, type Value } from './api';
import { EmptyState, Group, Icon, Row, Switch, fileSize } from './widgets';
import { DiagnosticExport } from './DiagnosticExport';
export type ToolView={kind:string;title:string;keys?:string[];id?:string;message?:string};
type Props={client:PhoneClient;view:ToolView;open:(view:ToolView,nested?:boolean)=>void;run:(domain:string,field:string,value?:Value,announce?:boolean)=>Promise<boolean>;busy:boolean;pickFile:(kind:string,target?:string)=>void;controls:(keys:string[])=>React.ReactNode;back:()=>void;uploading:boolean;drafts:Record<string,string>;newMemo:()=>void};
type Entry={id:string;name:string;bytes:number;directory?:boolean;created?:number;kind?:string;version?:string;assets?:number;assetBytes?:number};
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
  const requestEpoch=useRef(0),draftKey=`${view.id||''}/${view.id==='input-test'?view.keys?.[0]||'':''}`;
  const v=client.values(),[query,setQuery]=useState(''),[places,setPlaces]=useState<{code:string;name:string;area:string}[]>(),[problem,setProblem]=useState('');
  useEffect(()=>{requestEpoch.current++;setQuery(drafts[draftKey]||'');setPlaces(undefined);setProblem('');return()=>{requestEpoch.current++;};},[draftKey]);
  const confirm=(title:string,domain:string,field:string,value:Value,message:string)=>open({kind:'tool-confirm',title,id:domain,keys:[field],message:JSON.stringify({value,message})},true);
  if(view.id==='city')return <>
    <form onSubmit={async event=>{
      event.preventDefault();
      const current=query,request=++requestEpoch.current;
      setPlaces(undefined);setProblem('');
      try{
        const result=await client.places(current);
        if(request===requestEpoch.current){
          setPlaces(result);
          if(result.length)feedback({kind:'success',title:'城市查询完成',detail:`找到 ${result.length} 个结果`});
        }
      }catch(error){
        if(request===requestEpoch.current){
          const detail=error instanceof Error?error.message:'城市查询未完成';
          setProblem(detail);feedback({kind:'error',title:'城市查询失败',detail});
        }
      }
    }}>
      <label>城市或地区<input className="text-input" aria-label="城市或地区" maxLength={60} value={query}
        onChange={event=>{requestEpoch.current++;drafts[draftKey]=event.target.value;setQuery(event.target.value);setPlaces(undefined);setProblem('');}}
        required/></label>
      <button className="primary-button">查找城市</button>
    </form>
    {problem?<EmptyState icon="ExclamationTriangleIcon" title="城市查询失败" description={problem} alert/>
      :places?.length?<Group>{places.map(place=><Row key={place.code} title={place.name} hint={place.area} disabled={busy}
        onClick={async()=>{if(await run('weather','cityCode',place.code))back();}}/>)}</Group>
      :places&&<EmptyState icon="MagnifyingGlassIcon" title="未找到匹配城市" description="请换个名称重试。"/>}
  </>;
  if(view.id==='weather-location')return <Group>{controls(['autoLocation'])}<Row title="手动选择位置" value={String(v.city||'尚未选择')} navigate onClick={()=>open({kind:'tool',id:'city',title:'手动选择位置'},true)}/></Group>;
  if(view.id==='weather-sources'){
    const options=client.metadata?.fields.provider?.options||[];
    const meteoEnabled=v.provider==='openmeteo'||(v.provider==='auto'&&v.meteoFallback)||v.weatherUsesMeteo;
    const source=String(v.weatherSource||'');
    const observed=new Date(Number(v.weatherObservedAt));
    const dateOptions: Intl.DateTimeFormatOptions={
      year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23',
    };
    let observedLabel='尚未更新';
    if(observed.getTime()>0){
      try{observedLabel=observed.toLocaleString('zh-CN',{...dateOptions,timeZone:client.metadata?.timeZone||'UTC'});}
      catch{observedLabel=observed.toLocaleString('zh-CN',{...dateOptions,timeZone:'UTC'});}
    }
    return <div className="weather-source-settings">
      <div className="weather-source-status">
        <Group>
          <Row title="当前来源" value={source?<span className="weather-source-current">
            {v.weatherPreviewAvailable!==false&&<Icon name="CheckCircledIcon" className="good"/>}{source}
          </span>:'尚未读取'}/>
          <Row title="数据时间" value={observedLabel}/>
        </Group>
      </div>
      <Group title="天气来源"><div className="weather-source-options" role="group" aria-label="天气来源">
        {[
          ['auto','自动切换'],['qweather','和风天气'],['uapi','UAPI'],
          ['openweather','OpenWeather'],['openmeteo','Open-Meteo'],
        ].map(([id,title])=>{
          const automatic=id==='auto';
          const selected=id===v.provider;
          const available=options.some(option=>option.value===id);
          return <button key={id} type="button" className="setting-row weather-source-option" data-available={available}
            aria-pressed={selected} disabled={busy||!available} onClick={()=>{if(!selected)client.update('provider',id);}}>
            {automatic?<Icon name="ReloadIcon" className="weather-mark blue"/>:<img className={'weather-mark weather-mark-'+id} src={client.resource('assets/weather-'+id+'.png')} alt=""/>}
            <span className="row-title weather-source-label">
              {title}
              {automatic&&!!v.meteoFallback&&<small>Open-Meteo 最后尝试</small>}
            </span>
            {!automatic&&!available&&<span className="row-value">未配置</span>}
            {selected&&<Icon name="CheckIcon" className="weather-source-check blue"/>}
          </button>;
        })}
      </div></Group>
      <details className="setting-group weather-service-details" open>
        <summary className="setting-row"><Icon name="InfoCircledIcon"/><span className="row-title">服务说明</span><Icon name="ChevronRightIcon" className="chevron"/></summary>
        <div className="weather-service-content">
          <p>天气请求会向所选来源发送地点或坐标。手机输入仅在当前局域网会话中接收。</p>
          {!!meteoEnabled&&<p>Open-Meteo 提供模型估算。<a href="https://open-meteo.com/en/terms" target="_blank" rel="noreferrer">免费接口</a>仅限非商业使用，有请求限额。</p>}
          {!!v.weatherUsesMeteo&&<p>天气数据由 <a href="https://open-meteo.com/" target="_blank" rel="noreferrer">Open-Meteo</a> 提供，依据 <a href="https://creativecommons.org/licenses/by/4.0/" target="_blank" rel="noreferrer">CC BY 4.0</a> 使用。</p>}
          <div className="weather-service-links">
            {!!v.weatherWebsite&&<a className="text-action" href={String(v.weatherWebsite)} target="_blank" rel="noreferrer">天气服务官网</a>}
            {!!v.weatherTerms&&<a className="text-action" href={String(v.weatherTerms)} target="_blank" rel="noreferrer">隐私与条款</a>}
          </div>
        </div>
      </details>
    </div>;
  }
  if(view.id==='weather')return <Group><WeatherSettings client={client} open={open} run={run} busy={busy} nested/></Group>;
  if(view.id==='boot')return <><Group>{controls(['boot'])}</Group><p className="section-note">保存去向后，按电视端的开机与唤醒规则执行。HDMI 需先在信号源配置中确认画面和声音。</p></>;
  if(view.id==='management')return <AdbManagement client={client} run={run} busy={busy}/>;
  if(view.id==='memos'){const notes=(v.notes||[]) as {id:string;text:string;done:boolean}[];return <>{notes.length>0&&<Group>{notes.map(note=><div className="setting-row memo-row" key={note.id}><button className="icon-button" aria-label={note.done?'标记未完成':'标记完成'} disabled={busy} onClick={()=>void run('memos','toggle',note.id)}><Icon name={note.done?'CheckCircledIcon':'ClockIcon'}/></button><button className={`row-title ${note.done?'memo-done':''}`} onClick={()=>open({kind:'memo-edit',title:'编辑备忘录',id:note.id,message:note.text},true)}>{note.text}</button><button className="icon-button danger" aria-label="删除备忘录" onClick={()=>confirm('删除备忘录？','memos','remove',note.id,note.text)}><Icon name="TrashIcon"/></button></div>)}</Group>}{notes.length?<button className="primary-button" onClick={newMemo}>新建备忘录</button>
      :<EmptyState icon="FileTextIcon" title="暂无备忘录"
        action={<button type="button" className="secondary-button" onClick={newMemo}>新建备忘录</button>}/>}</>;}
  if(view.id==='backup'||view.id?.startsWith('backup-')){
    const timeZone=String(client.metadata?.timeZone||'UTC');
    const entries=[...((v.backups||[]) as Entry[])].sort((a,b)=>(b.created||0)-(a.created||0));
    const importDrafts=(v.backupDrafts||[]) as Entry[];
    const restore=client.restoreStatus;
    const blocked=busy||uploading||!restore.canChangeBackups;
    const record=client.frame?.domains.backup?.lastRestore as {target?:string;phase?:string}|undefined;
    const review=client.restoreReview;
    const matches=!!review&&record?.target===review.target&&JSON.stringify(record)!==review.previous;
    const reviewing=!!review&&!matches;
    if(client.restoring)return null;
    const recordDate=(entry:Entry,full=false)=>backupDate(entry.created,timeZone,full);
    const confirmRestore=(entry:Entry,recent=false)=>confirm(recent?'恢复最近备份？':'恢复这份备份？','backup','restore',entry.id,
      `${recordDate(entry,true)}  ${backupKind(entry.kind)}\n将用这份备份替换当前桌面配置。\n恢复期间手机连接会结束。完成后重新连接，查看电视恢复结果。`);

    if(view.id==='backup'){
      const latest=entries[0];
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
        {!!v.backupError&&v.backupError!==restore.message&&<p className="form-error">{String(v.backupError)}</p>}
        {reviewing&&<p className="section-note" role="status">本次恢复结果待核对。<button className="text-action" onClick={()=>void client.refresh()}>刷新结果</button></p>}
        {restore.message&&<p className={restore.canRetry||restore.phase==='failed'?'form-error':'section-note'} role="status">
          {(restore.phase==='complete'||restore.phase==='rolled-back')&&(matches?'本次恢复  ':'上次恢复  ')}{restore.message}
        </p>}
        {restore.canRetry&&<button className="secondary-button" disabled={busy} onClick={()=>void run('backup','retry')}>重新读取恢复记录</button>}
        {importDrafts.length>0&&<Group title="待核对的导入">{importDrafts.map(entry=><Row key={entry.id} title={recordDate(entry,true)} navigate
          onClick={()=>open({kind:'tool',title:'核对导入备份',id:'backup-draft',keys:[entry.id]},true)}/>)}</Group>}
      </>;
    }
    if(view.id==='backup-history'){
      if(v.backupError)return <EmptyState icon="ExclamationTriangleIcon" title="无法读取备份" description={String(v.backupError)} alert/>;
      if(!entries.length)return <EmptyState icon="ArchiveIcon" title="暂无备份"/>;
      return <Group>{entries.map(entry=><Row key={entry.id} title={recordDate(entry)} value={backupKind(entry.kind)} navigate
        onClick={()=>open({kind:'tool',title:'备份详情',id:'backup-detail',keys:[entry.id]},true)}/>)}</Group>;
    }
    const draft=view.id==='backup-draft',id=view.keys?.[0];
    const selected=(draft?importDrafts:entries).find(entry=>entry.id===id);
    if(!selected){
      const imported=draft&&entries.some(entry=>entry.id===id);
      const returnButton=<button className="secondary-button" onClick={()=>imported?open({kind:'tool',title:'桌面备份与恢复',id:'backup'}):back()}>返回</button>;
      return imported?<><p className="sheet-intro">备份已保存到「全部备份」。</p>{returnButton}</>
        :<EmptyState icon="ArchiveIcon" title="这份备份已不可用" action={returnButton}/>;
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
  if(view.id==='diagnostics'||view.id==='diagnostic-inputs')return <DiagnosticExport key={view.id} page={view.id} client={client} run={run} busy={busy} uploading={uploading}/>;
  return <EmptyState icon="InfoCircledIcon" title="当前电视未提供此入口"/>;
}

export function WeatherSettings({client,open,run,busy,nested=false}:Pick<Props,'client'|'open'|'run'|'busy'>&{nested?:boolean}){
  const v=client.values();let updated='尚未更新';if(Number(v.weatherFetchedAt)>0)try{updated=new Date(Number(v.weatherFetchedAt)).toLocaleTimeString('zh-CN',{timeZone:client.metadata?.timeZone||'UTC',hour:'2-digit',minute:'2-digit',hour12:false})+' 更新';}catch{updated='时间待核对';}
  const working=client.tasks().some(task=>task.domain==='weather'&&['queued','running','ready'].includes(task.state));
  return <><Row title="天气位置" icon="GlobeIcon" value={`${v.autoLocation?'自动定位':'手动选择'}  ${String(v.autoLocation?v.locatedCity||'未定位':v.city||'尚未选择')}`} navigate onClick={()=>open({kind:'tool',id:'weather-location',title:'天气位置'},nested)}/><Row title="刷新天气" icon="ReloadIcon" value={!v.weatherEnabled?'天气已关闭':working?'正在更新…':updated} disabled={busy||working||!v.weatherEnabled} onClick={()=>void run('weather','refresh')}/><Row title="来源与服务" icon="InfoCircledIcon" value={client.metadata?.fields.provider?.options?.find(item=>item.value===v.provider)?.title||'尚未读取'} navigate onClick={()=>open({kind:'tool',id:'weather-sources',title:'来源与服务'},nested)}/></>;
}

export function WeatherSummary({client}:{client:PhoneClient}){
  const v=client.values();return <div className="weather-background-summary" role="status"><strong>{String(v.locatedCity||v.city||'当前城市')}  {String(v.weatherSceneLabel||'天气待更新')}</strong><span>{String(v.weatherBackgroundState||'天气状态待核对')}  {String(v.weatherPeriod||'昼夜状态待核对')}</span></div>;
}
