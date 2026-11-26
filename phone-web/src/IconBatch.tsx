import { useEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { type PhoneClient } from './api';
import { EmptyState, Group, Icon, ResourceImage, Row, Switch } from './widgets';
import { AppFilterMenu } from './AppFilterMenu';
import { desktopIconShape, IconMaterialGrid, filteredIcons, filterTitles, iconFilterChoices, iconResource, iconTitles, type IconCatalog, type IconFilters } from './IconLibraryPicker';

type MatchRow={package:string;name:string;coverKey:string;status:string;id:string;candidates:string[];custom:boolean;automatic:boolean};
type Plan=IconFilters&{planId:string;catalogVersion:number;rows:MatchRow[]};
type BatchRequest={planId:string;requestId:string;selected:{package:string;id:string}[];coverCustom:boolean};
const notes:Record<string,string>={pending:'核对候选后选择',custom:'已有自定义图标',current:'已是当前图标',missing:'此组合暂无图标',unmatched:'未匹配，可从图库选图'};
const group=(row:MatchRow)=>row.status==='ready'?'ready':row.status==='pending'?'pending':'kept';
const matchTabs=[
  {id:'ready',title:'可替换',empty:'暂无可替换的应用'},
  {id:'pending',title:'待确认',empty:'暂无待确认的应用'},
  {id:'kept',title:'已保留',empty:'暂无已保留的应用'},
] as const;

export function IconBatch({client,catalog,filters,onFilters,working,feedback,onSubmit,actionsHost}:{client:PhoneClient;catalog:IconCatalog;filters:IconFilters;onFilters:(filters:IconFilters)=>void;working:boolean;feedback:ReactNode;onSubmit:(value:BatchRequest)=>Promise<void>;actionsHost?:HTMLElement|null}){
  const shape=desktopIconShape(client);
  const batchFilters={...filters,shape,form:shape!=='rectangle'&&filters.form==='text'?'icon':filters.form};
  const [plan,setPlan]=useState<Plan|null>(null),[selected,setSelected]=useState<Record<string,string>>({}),[tab,setTab]=useState<(typeof matchTabs)[number]['id']>('ready'),[coverCustom,setCoverCustom]=useState(false),[error,setError]=useState(''),[matching,setMatching]=useState(false),[candidate,setCandidate]=useState<MatchRow|null>(null),[retry,setRetry]=useState(0);
  const request=useRef<BatchRequest|null>(null),generation=useRef(0),filterKey=JSON.stringify(batchFilters);
  const currentPlan=plan&&JSON.stringify({style:plan.style,shape:plan.shape,form:plan.form})===filterKey&&plan.catalogVersion===catalog.revision?plan:null;
  useEffect(()=>{
    const token=++generation.current;let disposed=false;
    setPlan(null);setSelected({});setCoverCustom(false);setCandidate(null);setMatching(true);setError('');request.current=null;
    void client.command('icons','match',batchFilters).then(receipt=>{
      if(disposed||token!==generation.current)return;
      const result=receipt.operation as unknown as Plan;
      if(!result?.planId||!Array.isArray(result.rows))throw new Error('匹配结果不完整');
      setPlan(result);setSelected(Object.fromEntries(result.rows.filter(row=>row.status==='ready').map(row=>[row.package,row.id])));
    }).catch(e=>{if(!disposed)setError(e instanceof Error?e.message:'无法读取匹配结果');}).finally(()=>{if(!disposed)setMatching(false);});
    return()=>{disposed=true;};
  },[filterKey,catalog.revision,retry]);
  const disabled=working||matching,count=Object.keys(selected).length;
  const manualIds=filteredIcons(catalog,batchFilters).map(item=>item.id);
  function choices(row:MatchRow){return row.status==='unmatched'||row.custom&&!row.candidates.length?manualIds:row.candidates;}
  function note(row:MatchRow){const text=row.status==='unmatched'?(selected[row.package]?'已手动选图':manualIds.length?notes.unmatched:'未匹配  当前组合暂无图标'):notes[row.status];return row.custom&&!coverCustom&&row.status!=='current'?`${text}  需开启覆盖已有自定义图标`:text;}
  function change(next:Record<string,string>){request.current=null;setSelected(next);}
  function toggle(row:MatchRow){if(selected[row.package]){const next={...selected};delete next[row.package];change(next);}else if(row.status!=='unmatched'&&row.candidates.length===1)change({...selected,[row.package]:row.candidates[0]});else setCandidate(row);}
  function override(){const next={...selected};for(const row of currentPlan!.rows)if(row.custom){if(!coverCustom&&row.automatic&&row.candidates.length===1&&row.status!=='current')next[row.package]=row.id;else delete next[row.package];}change(next);setCoverCustom(!coverCustom);}
  function apply(){
    if(!currentPlan||disabled||!count)return;
    if(!request.current)request.current={planId:currentPlan.planId,requestId:Array.from(crypto.getRandomValues(new Uint8Array(16)),n=>n.toString(16).padStart(2,'0')).join(''),selected:Object.entries(selected).map(([pkg,id])=>({package:pkg,id})),coverCustom};
    void onSubmit(request.current);
  }
  const canApply=!!currentPlan&&count>0;
  const visibleRows=currentPlan?.rows.filter(row=>group(row)===tab)||[];
  return <div className="icon-batch-page">
    {candidate?<button className="text-action" onClick={()=>setCandidate(null)}><Icon name="ChevronLeftIcon"/>返回匹配结果</button>:
      <div className="icon-batch-toolbar">
        <div className="icon-batch-tabs" role="tablist" aria-label="匹配结果分组">
          {matchTabs.map(({id,title})=><button key={id} type="button" role="tab" aria-selected={tab===id}
            aria-controls="icon-batch-results" id={`icon-batch-${id}`} disabled={working} onClick={()=>setTab(id)}>
            {title}{' '}<span>{currentPlan?.rows.filter(row=>group(row)===id).length||0}</span>
          </button>)}
        </div>
        <div className="icon-batch-filters" aria-label="图标筛选条件">
          {(['style','form'] as const).map(key=>{
            const choices=iconFilterChoices(catalog,batchFilters,key),current=batchFilters[key];
            if(!choices.some(option=>option.value===current))
              choices.unshift({value:current,title:iconTitles[current]||current});
            return <AppFilterMenu key={key} title={filterTitles[key]} options={choices.map(option=>option.title)}
              value={choices.findIndex(option=>option.value===current)} disabled={disabled}
              onChange={index=>onFilters({...filters,[key]:choices[index].value})}/>;
          })}
        </div>
      </div>}
    {candidate?<IconMaterialGrid client={client} catalog={catalog} filters={batchFilters} selected={selected[candidate.package]||''} ids={choices(candidate)} disabled={working} onSelect={item=>{change({...selected,[candidate.package]:item.id});setCandidate(null);}}/>:<><div id="icon-batch-results" role="tabpanel" aria-labelledby={`icon-batch-${tab}`}>
      {!currentPlan?<EmptyState title={error||(matching?'正在匹配图标…':'匹配结果尚未读取')}
        icon={error?'ExclamationTriangleIcon':matching?'ReloadIcon':'ImageIcon'} alert={!!error}/>:
        visibleRows.length?<Group>{visibleRows.map(row=>{
          const ids=choices(row),id=selected[row.package]||(row.status==='unmatched'?'':row.id);
          const enabled=!!ids.length&&row.status!=='current'&&(!row.custom||coverCustom);
          const current=catalog.targets.find(target=>target.id===row.package);
          return <div className="icon-batch-row" key={row.package}>
            <button type="button" role="checkbox" aria-checked={!!selected[row.package]} aria-label={`选择 ${row.name}`}
              className="icon-batch-check" disabled={disabled||!enabled} onClick={()=>toggle(row)}>
              <Icon name={selected[row.package]?'CheckCircledIcon':'CircleIcon'}/>
            </button>
            {current&&<ResourceImage src={client.resource(current.cover||current.icon||'')} alt={`${row.name}当前图标`}/>}
            <div className="icon-batch-label"><strong>{row.name}</strong>{row.status!=='ready'&&<small>{note(row)}</small>}</div>
            {id&&ids.includes(id)?<><Icon name="ArrowRightIcon"/>
              <button type="button" className="icon-batch-candidate" aria-label={`为 ${row.name} 选择候选图标`}
                disabled={disabled||!enabled||ids.length<2} onClick={()=>setCandidate(row)}>
                <ResourceImage src={iconResource(client,currentPlan,id,currentPlan.catalogVersion)} alt="候选图标"/>
              </button>
            </>:enabled?<button type="button" className="text-action icon-batch-pick" disabled={disabled} onClick={()=>setCandidate(row)}>从图库选择</button>:null}
          </div>;
        })}</Group>:<EmptyState icon="ImageIcon" title={matchTabs.find(item=>item.id===tab)!.empty}/>}
    </div>
      {currentPlan&&currentPlan.rows.length>0&&<Group><Row title="覆盖已有自定义图标" icon="LayersIcon">
        <Switch title="覆盖已有自定义图标" checked={coverCustom} onChange={override} disabled={disabled}/>
      </Row></Group>}
      {feedback}
      {actionsHost&&createPortal(<div className="paired-actions icon-batch-actions">
        <button type="button" className={canApply?'secondary-button':'primary-button'} disabled={disabled}
          onClick={()=>setRetry(value=>value+1)}>重新匹配</button>
        <button type="button" className="primary-button" disabled={disabled||!canApply}
          onClick={apply}>{working?'正在替换…':`替换已选 ${count} 个图标`}</button>
      </div>,actionsHost)}
    </>}
  </div>;
}
