import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { ApiError, type Metadata, type PhoneClient } from './api';
import { EmptyState, Group, ResourceImage, Row } from './widgets';

export type AboutTab = 'device' | 'runtime' | 'source';
type Capacity = { total: number; used: number };
type DeviceReadings = { memory?: Capacity; storage?: Capacity; screenLabel?: string; screen?: string; gpu?: string };
type RuntimeReadings = { memory?: Capacity; apps: {name: string; bytes: number}[]; note: string };
type Props = {
  client: PhoneClient;
  metadata: Metadata;
  tab: AboutTab;
  visible: boolean;
  select: (tab: AboutTab) => void;
  openLicense: () => void;
  openRecovery: () => void;
  openRelease: () => void;
};
const tabs: [AboutTab,string][]=[['device','设备信息'],['runtime','运行状态'],['source','开源信息']];
const unavailable='系统未提供';
const intro='简洁、可自定义的 Android 电视桌面';
const channels: Record<string,string>={stable:'正式版',experimental:'实验版'};
const refreshMs=5000;

function capacityText(capacity:Capacity){
  const gb=capacity.total>=1e9, divisor=gb?1e9:1e6, digits=gb?1:0;
  return `${(capacity.used/divisor).toFixed(digits)} / ${(capacity.total/divisor).toFixed(digits)} ${gb?'GB':'MB'}`;
}

function appMemory(bytes:number){
  if(bytes<=0)return unavailable;
  return bytes>=1e9?`${(bytes/1e9).toFixed(1)} GB`:`${Math.round(bytes/1e6)} MB`;
}

function AboutFact({title,children}:{title:string;children:ReactNode}){
  return <div className="about-fact">
    <dt className="row-title">{title}</dt>
    <dd className="row-value">{children}</dd>
  </div>;
}

function Meter({title,capacity,fallback}:{title:string;capacity?:Capacity;fallback?:string}){
  const percentage=capacity?Math.round(capacity.used*100/capacity.total):0;
  return <AboutFact title={title}>
    {capacity?<span className="about-meter">
      <span>{capacityText(capacity)}</span>
      <progress aria-label={title} value={percentage} max={100}/>
      <span>{percentage}%</span>
    </span>:fallback||unavailable}
  </AboutFact>;
}

export function AboutSettings({client,metadata,tab,visible,select,openLicense,openRecovery,openRelease}:Props){
  const tabsId=useId();
  const tabToFocus=useRef<AboutTab|undefined>(undefined);
  const [device,setDevice]=useState<DeviceReadings>();
  const [runtime,setRuntime]=useState<RuntimeReadings>();
  const [problem,setProblem]=useState('');
  const connected=client.status==='connected';

  function selectTab(target:AboutTab){
    if(target===tab)return;
    tabToFocus.current=target;
    select(target);
  }

  useEffect(()=>{
    if(tabToFocus.current!==tab)return;
    tabToFocus.current=undefined;
    document.getElementById(tabsId+'-'+tab)?.focus({preventScroll:true});
  },[tab,tabsId]);

  useEffect(()=>{
    if(!connected||!visible||tab==='source')return;
    const kind=tab;
    const controller=new AbortController();
    let pending: Promise<void>|undefined, timer: ReturnType<typeof setTimeout>|undefined;
    setProblem('');
    const stop=()=>{if(timer!==undefined)clearTimeout(timer);timer=undefined;};
    function read(){
      if(document.hidden||controller.signal.aborted||pending||client.status!=='connected')return;
      pending=(async()=>{
        let repeat=true;
        try{
          const result=await client.about(kind,controller.signal);
          if(controller.signal.aborted||document.hidden)return;
          if(kind==='device')setDevice(result);
          else {
            if(!Array.isArray(result?.apps)){
              repeat=false;
              setRuntime(undefined);
              setProblem('当前电视暂未提供运行状态');
              return;
            }
            setRuntime(result);
          }
          setProblem('');
        }catch(error){
          if(controller.signal.aborted||document.hidden)return;
          if(error instanceof ApiError&&error.status<500){
            repeat=false;
            if(tab==='runtime')setProblem('当前电视暂未提供运行状态');
          }else if(tab==='runtime')setProblem('暂时无法读取运行状态');
        }finally{
          pending=undefined;
          if(repeat&&tab==='runtime'&&!controller.signal.aborted&&!document.hidden&&client.status==='connected')
            timer=setTimeout(read,refreshMs);
        }
      })();
    }
    const visibility=()=>{stop();read();};
    document.addEventListener('visibilitychange',visibility);
    read();
    return ()=>{controller.abort();stop();document.removeEventListener('visibilitychange',visibility);};
  },[client,connected,visible,tab]);

  const info=metadata.deviceInfo as Record<string,string>|undefined;
  const project=String(metadata.project||'https://github.com/BeatsAD/quiet-tv');
  const thanks=String(metadata.thanks||'感谢 Gathin 与开源贡献者');
  const channel=channels[metadata.device.channel]||'测试版';
  const runtimeVisible=visible&&connected&&!problem?runtime:undefined;
  const deviceFacts:[title:string,value:string|undefined][]=[
    ['电视型号',info?.model||metadata.device.name],
    ['安卓版本',info?.android],['芯片型号',info?.cpu],
    [device?.screenLabel||'电视分辨率',device?.screen],['GPU 型号',device?.gpu],
    [info?.brand?.startsWith('制造商  ')?'制造商':'品牌',info?.brand?.replace(/^(品牌|制造商)\s+/,'')],['系统架构',info?.abis],
    ['固件版本',info?.firmware],
  ];
  return <div className="about-page">
    <div className="about-brand">
      <ResourceImage src={client.resource('assets/quiettv-assistant-launcher.png')} alt=""/>
      <div><strong className="about-brand-line">Quiet TV {metadata.device.version} {channel}</strong>
        <p>{intro}</p></div>
    </div>
    <div className="about-tabs" role="tablist" aria-label="关于 Quiet TV">
      {tabs.map(([key,title],index)=><button key={key} id={tabsId+'-'+key} type="button" role="tab"
        aria-controls={tabsId+'-panel'} aria-selected={key===tab} tabIndex={key===tab?0:-1}
        onClick={()=>selectTab(key)} onKeyDown={event=>{
          const next=event.key==='ArrowRight'?(index+1)%tabs.length:event.key==='ArrowLeft'?(index+tabs.length-1)%tabs.length:
            event.key==='Home'?0:event.key==='End'?tabs.length-1:-1;
          if(next<0)return;
          event.preventDefault();
          selectTab(tabs[next][0]);
        }}>{title}</button>)}
    </div>
    <div id={tabsId+'-panel'} className="about-tab-panel" role="tabpanel" aria-labelledby={tabsId+'-'+tab}>
      {tab==='device'&&<>
        <dl className="setting-group about-facts">
          {deviceFacts.map(([title,value])=><AboutFact key={title} title={title}>{value||unavailable}</AboutFact>)}
          <Meter title="运行内存" capacity={device?.memory} fallback={device?undefined:info?.memory}/>
          <Meter title="内部存储" capacity={device?.storage} fallback={device?undefined:info?.storage}/>
        </dl>
        <Group><Row title="家长密码恢复码" value="验证后查看" navigate onClick={openRecovery}/>
          <Row title="版本说明" navigate onClick={openRelease}/></Group>
      </>}
      {tab==='runtime'&&(runtimeVisible?.apps.length?<div className="about-runtime">
        <div className="setting-group">
          <table>
            <thead><tr><th scope="col">当前应用</th><th scope="col">内存占用</th><th scope="col">占整机内存</th></tr></thead>
            <tbody>{runtimeVisible.apps.map((app,index)=>{
              const share=runtimeVisible.memory&&app.bytes>0?app.bytes*100/runtimeVisible.memory.total:undefined;
              return <tr key={`${app.name}-${index}`}><td className="row-title">{app.name}</td><td className="row-value">{appMemory(app.bytes)}</td>
                <td className="row-value">{share===undefined?'':`${share.toFixed(1)}%`}</td></tr>;
            })}</tbody>
          </table>
        </div>
        <p className="section-note">{runtimeVisible.note}</p>
      </div>:runtimeVisible?<EmptyState icon="DashboardIcon" title="暂无运行中的应用" description={runtimeVisible.note||undefined}/>
        :<EmptyState icon={connected?(problem?'InfoCircledIcon':'ReloadIcon'):'UnplugIcon'}
          title={connected?problem||'正在读取运行状态…':'等待重新连接电视'}/>)}
      {tab==='source'&&<dl className="setting-group about-facts about-source">
        <AboutFact title="开源地址"><a href={project} target="_blank" rel="noreferrer">{project.replace(/^https:\/\//,'')}</a></AboutFact>
        <AboutFact title="许可证"><button className="text-action" type="button" onClick={openLicense}>MIT 许可证</button></AboutFact>
        <AboutFact title="致谢">{thanks}</AboutFact>
      </dl>}
    </div>
  </div>;
}
