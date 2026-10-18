import { useEffect, useRef, useState } from 'react';
import { ArrowLeft, ArrowRight, ArrowCounterClockwise, Check, CaretRight, ClockCounterClockwise, GearSix, House, Info, MagnifyingGlass, Moon, Package, Play, PlugsConnected, Power, ShieldCheck, SquaresFour, Sun, Television, Trash, WifiHigh, X } from '@phosphor-icons/react';

const STORAGE = 'quiet-tv-setup-prototype-v1';
const scenarios = {
  standard: { label:'标准切换', desc:'支持系统选择默认桌面', method:'传统网络调试' },
  tcp: { label:'传统 ADB', desc:'通过本机 ADB 切换默认桌面', method:'传统网络调试' },
  pair: { label:'无线配对', desc:'通过无线配对切换默认桌面', method:'无线配对' },
  unsupported: { label:'暂未适配', desc:'当前固件暂未适配', method:'电脑辅助' },
};
const originals = [
  { id:'feiniu', name:'飞牛 TV', caption:'你的媒体库', icon:'feiniu.webp', banner:'feiniu-banner.png' },
  { id:'bili', name:'云视听小电视', caption:'视频与番剧', icon:'bili-icon.png', banner:'bili-banner.png' },
  { id:'tianguang', name:'天光云影', caption:'电视直播', icon:'tianguang.png', banner:'tianguang-banner.png' },
  { id:'thunder', name:'迅雷云盘', caption:'云盘影音', icon:'thunder-icon.png', banner:'thunder-banner.png' },
  { id:'showroom', name:'BRAVIA 演示', caption:'卖场循环展示', glyph:'tv', system:true, suggested:true, pkg:'com.sony.dtv.braviashow' },
  { id:'retail', name:'零售演示支持', caption:'零售演示配套', glyph:'tv', system:true, suggested:true, pkg:'com.sony.dtv.demosupport' },
  { id:'system-demo', name:'系统演示支持', caption:'系统演示配套', glyph:'tv', system:true, suggested:true, pkg:'com.sony.dtv.demosystemsupport' },
  { id:'multi-demo', name:'多屏演示', caption:'卖场多屏展示', glyph:'tv', system:true, suggested:true, pkg:'com.sony.dtv.multiscreendemo' },
  { id:'bugreport', name:'索尼错误报告', caption:'错误报告收集', glyph:'chart', system:true, suggested:true, pkg:'com.sony.dtv.sonybugreportsys' },
  { id:'idd', name:'IDD 诊断数据代理', caption:'诊断数据反馈', glyph:'chart', system:true, suggested:true, pkg:'com.sonyericsson.idd.agent' },
  { id:'settings', name:'系统设置', caption:'网络、画面与声音设置', glyph:'settings', system:true, protected:true },
  { id:'factory', name:'原厂桌面', caption:'系统原有桌面', glyph:'home', system:true, protected:true },
];
const recommended = originals.filter(a=>a.suggested).map(a=>a.id);
const fresh = () => ({ apps:originals.map(a=>({...a,enabled:true,installed:true})), defaultHome:false, connected:false, step:'default', scenario:'standard', journal:[] });
function readState(){
  try {
    const saved=JSON.parse(localStorage.getItem(STORAGE));
    if(saved?.apps && Array.isArray(saved.journal)){
      const step=saved.step==='done'?'home':saved.step==='welcome'?'default':saved.step==='connect'?(saved.destination==='default'?'default':'curate'):saved.step;
      return {...fresh(),...saved,step:step==='default'&&saved.defaultHome?'curate':step,
        apps:originals.map(a=>{const old=saved.apps.find(item=>item.id===a.id);return {...a,enabled:old?.enabled??true,installed:old?.installed??true}}),
        journal:saved.journal.filter(item=>originals.some(a=>a.id===item.id))};
    }
  } catch {}
  return fresh();
}
const labels={disable:'禁用',enable:'启用',uninstall:'卸载',restore:'恢复安装'};
function Icon({app,large=false}){const Glyph=app.glyph==='settings'?GearSix:app.glyph==='home'?House:app.glyph==='play'?Play:app.glyph==='tv'?Television:SquaresFour;return <span className={`q-icon ${large?'big':''} ${app.icon?'art':''}`}>{app.icon?<img src={'/assets/'+app.icon} alt=""/>:<Glyph weight="regular"/>}</span>}
function Button({children,icon:Glyph,onClick,primary=false,...props}){return <button className={`q-button ${primary?'primary':''}`} onClick={onClick} {...props}>{Glyph&&<Glyph/>}{children}</button>}
function MiniHome(){return <div className="q-mini-home"><div className="q-mini-top"><span>15:39 <small>周日，下午好</small></span><span><WifiHigh/><GearSix/></span></div><div className="q-mini-bottom"><div>{originals.slice(0,4).map(a=><img src={'/assets/'+a.banner} alt="" key={a.id}/>)}</div></div></div>}

export function SetupExperience(){
  const [state,setState]=useState(readState),[screen,setScreen]=useState(()=>readState().step);
  const [scenario,setScenario]=useState(()=>readState().scenario),[scenic,setScenic]=useState(true);
  const [dialog,setDialog]=useState(null),[notice,setNotice]=useState(''),[query,setQuery]=useState(''),[filter,setFilter]=useState('全部');
  const [selected,setSelected]=useState('showroom'),[choices,setChoices]=useState(recommended);
  const [connectStep,setConnectStep]=useState('guide'),[code,setCode]=useState(''),[port,setPort]=useState('5555'),[connectError,setConnectError]=useState('');
  const [destination,setDestination]=useState('apply'),[busy,setBusy]=useState(false);
  const frame=useRef(null),lastFocus=useRef(null),timer=useRef(null),toastTimer=useRef(null);
  const capability=scenarios[scenario],adapted=scenario!=='unsupported';
  const toast=text=>{setNotice(text);clearTimeout(toastTimer.current);toastTimer.current=setTimeout(()=>setNotice(''),3600)};
  const go=next=>{clearTimeout(timer.current);setBusy(false);setScreen(next);setDialog(null);setState(s=>({...s,step:next}))};
  const open=value=>{lastFocus.current=document.activeElement;setDialog(value)};
  const close=()=>{clearTimeout(timer.current);setBusy(false);if(dialog?.returnConnection){setDialog({type:'connection'});return}setDialog(null);requestAnimationFrame(()=>lastFocus.current?.isConnected&&lastFocus.current.focus())};
  const finish=()=>{go('home');toast(state.defaultHome?'设置成功':'已进入桌面，默认桌面未设置')};
  const requireConnection=next=>{setDestination(next);setConnectStep('guide');setConnectError('');setCode('');open({type:adapted?'connection':'unsupported'})};
  const changeScenario=value=>{clearTimeout(timer.current);clearTimeout(toastTimer.current);setNotice('');setBusy(false);setScenario(value);setState({...fresh(),scenario:value});setScreen('default');setDialog(null);setConnectStep('guide');setCode('');setConnectError('');setChoices(recommended);setQuery('');setFilter('全部')};
  useEffect(()=>{localStorage.setItem(STORAGE,JSON.stringify(state))},[state]);
  useEffect(()=>()=>{clearTimeout(timer.current);clearTimeout(toastTimer.current)},[]);
  useEffect(()=>{requestAnimationFrame(()=>{const scope=dialog?frame.current?.querySelector('[role="dialog"]'):frame.current;(scope?.querySelector('[data-first]')||scope?.querySelector('button:not(:disabled),input'))?.focus({preventScroll:true})})},[screen,dialog,connectStep]);
  const back=()=>{if(dialog){close();return}go(screen==='curate'?'default':'home')};
  useEffect(()=>{
    const handle=e=>{
      if(e.key==='Escape'){e.preventDefault();if(screen==='manage'&&!dialog&&document.activeElement.closest('.q-detail')){frame.current.querySelector('.q-manager-list>button.active')?.focus();return}back();return}
      if(e.key==='Home'&&!['INPUT','TEXTAREA'].includes(e.target.tagName)){e.preventDefault();go('home');return}
      const scope=dialog?frame.current?.querySelector('[role="dialog"]'):frame.current;
      if(!scope||!scope.contains(document.activeElement))return;
      const controls=[...scope.querySelectorAll('button:not(:disabled),input:not(:disabled),select:not(:disabled),a[href]')].filter(n=>n.getClientRects().length);
      if(e.key==='Tab'&&dialog){const index=controls.indexOf(document.activeElement);if(e.shiftKey&&index===0){e.preventDefault();controls.at(-1)?.focus()}else if(!e.shiftKey&&index===controls.length-1){e.preventDefault();controls[0]?.focus()}return}
      if(['INPUT','SELECT','TEXTAREA'].includes(e.target.tagName)||!e.key.startsWith('Arrow'))return;
      e.preventDefault();const r=document.activeElement.getBoundingClientRect(),x=r.x+r.width/2,y=r.y+r.height/2;
      const horizontal=['ArrowLeft','ArrowRight'].includes(e.key),positive=['ArrowRight','ArrowDown'].includes(e.key);
      const target=controls.filter(n=>n!==document.activeElement).map(n=>{const b=n.getBoundingClientRect(),dx=b.x+b.width/2-x,dy=b.y+b.height/2-y,p=horizontal?dx:dy,c=horizontal?dy:dx;return {n,p,score:Math.abs(p)+Math.abs(c)*3}}).filter(t=>positive?t.p>6:t.p< -6).sort((a,b)=>a.score-b.score)[0];
      target?.n.focus({preventScroll:true});target?.n.scrollIntoView({block:'nearest',inline:'nearest'});
    };document.addEventListener('keydown',handle);return()=>document.removeEventListener('keydown',handle);
  });
  const setDefault=()=>{setState(s=>({...s,defaultHome:true}));go('curate')};
  const selectedApps=state.apps.filter(a=>a.suggested&&choices.includes(a.id)&&a.enabled&&a.installed&&!a.protected);
  const toggleChoice=id=>setChoices(s=>s.includes(id)?s.filter(x=>x!==id):[...s,id]);
  const applyChoices=()=>{
    setState(s=>({...s,apps:s.apps.map(a=>selectedApps.some(t=>t.id===a.id)?{...a,enabled:false}:a),journal:[...selectedApps.map(a=>({id:a.id,name:a.name,action:'disable',before:{enabled:a.enabled,installed:a.installed},time:new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'})})),...s.journal]}));
    finish();
  };
  const connected=()=>{setState(s=>({...s,connected:true}));setBusy(false);setDialog(null);if(destination==='default')setDefault();else if(destination==='apply')applyChoices();else{go(destination);toast('已连接电视')}};
  const connect=()=>{
    if(scenario==='pair'&&!/^\d{6}$/.test(code)){setConnectError('请输入 6 位配对码');return}
    if(scenario!=='pair'&&(!/^\d+$/.test(port)||+port<1||+port>65535)){setConnectError('端口需为 1–65535');return}
    setConnectError('');setBusy(true);timer.current=setTimeout(()=>{if(scenario==='pair')connected();else{setBusy(false);setConnectStep('authorize')}},700);
  };
  const requestDefault=()=>!adapted?open({type:'unsupported'}):scenario==='standard'?open({type:'default'}):state.connected?setDefault():requireConnection('default');
  const operate=(a,action)=>{if(a.protected||!adapted&&a.system){toast('此应用保持只读');return}if(!state.connected&&!(action==='uninstall'&&!a.system)){requireConnection('manage');return}open({type:'operation',app:a,action})};
  const perform=()=>{
    const {app,action}=dialog;
    const patch=action==='disable'?{enabled:false}:action==='enable'?{enabled:true}:action==='uninstall'?{enabled:false,installed:false}:{enabled:true,installed:true};
    setState(s=>({...s,apps:s.apps.map(a=>a.id===app.id?{...a,...patch}:a),journal:[{id:app.id,name:app.name,action,before:{enabled:app.enabled,installed:app.installed},time:new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'})},...s.journal]}));close();toast(`${app.name} · 已${labels[action]}`);
  };
  const restorable=state.apps.filter(a=>a.installed&&!a.enabled&&state.journal.some(j=>j.id===a.id&&j.action==='disable'&&j.before.enabled));
  const restoreDisabled=()=>{setState(s=>({...s,apps:s.apps.map(a=>restorable.some(r=>r.id===a.id)?{...a,enabled:true}:a),journal:[...restorable.map(a=>({id:a.id,name:a.name,action:'enable',before:{enabled:false,installed:true},time:new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'})})),...s.journal]}));close();toast(`已重新启用 ${restorable.length} 个应用`)};
  const available=state.apps.filter(a=>`${a.name} ${a.pkg||'demo.quiet.'+a.id}`.toLowerCase().includes(query.toLowerCase())&&(filter==='全部'||filter==='预装'&&a.system||filter==='已禁用'&&a.installed&&!a.enabled||filter==='已卸载'&&!a.installed));
  const current=available.find(a=>a.id===selected)||available[0];
  const onboarding=['default','curate'].includes(screen);
  const reset=()=>{clearTimeout(timer.current);setBusy(false);setState(fresh());setScreen('default');setScenario('standard');setDialog(null);setChoices(recommended);setQuery('');setFilter('全部');toast('演示已重置')};

  return <main className="q-preview">
    <div className="q-preview-bar"><span className="q-preview-brand"><SquaresFour/> QUIET TV <span>交互原型</span></span><div className="q-preview-actions"><button aria-label={scenic?'深色背景':'风景背景'} onClick={()=>setScenic(s=>!s)}>{scenic?<Moon/>:<Sun/>}<span>{scenic?'深色背景':'风景背景'}</span></button><button aria-label="重置演示" onClick={()=>open({type:'reset'})}><ArrowCounterClockwise/><span>重置演示</span></button></div></div>
    <div className="q-review-strip"><nav aria-label="原型页面">{[['default','首次设置'],['home','极简桌面'],['manage','应用管理'],['recovery','恢复中心']].map(([id,label])=><button key={id} onClick={()=>go(id==='default'&&state.defaultHome?'curate':id)} aria-current={screen===id||id==='default'&&onboarding?'page':undefined}>{label}</button>)}</nav><label>设备情境<select aria-label="设备情境" value={scenario} onChange={e=>changeScenario(e.target.value)}>{Object.entries(scenarios).map(([key,s])=><option value={key} key={key}>{s.label}</option>)}</select></label></div>
    <section className={`q-tv ${scenic?'scenic':''} q-screen-${screen}`} ref={frame} aria-label="Quiet TV 设置与应用管理原型">
      <div className="q-wallpaper"/>
      {screen!=='home'&&<header className="q-header"><div className="q-wordmark"><SquaresFour weight="regular"/><span>Quiet TV</span></div></header>}
      {screen==='default'&&<div className="q-body q-split"><div className="q-step-copy"><p className="q-eyebrow">01 / 默认桌面</p><h1>{state.defaultHome?'默认桌面已设置':'设为默认桌面'}</h1><p className="q-lead">按 HOME 键返回 Quiet TV。</p><div className="q-hero-actions"><Button primary icon={House} data-first onClick={()=>state.defaultHome?go('curate'):requestDefault()}>{state.defaultHome?'管理预装应用':'设为默认桌面'}</Button></div></div><div className="q-default-preview"><MiniHome/><div className="q-detection glass"><span className="q-detection-icon">{adapted?<ShieldCheck/>:<Info/>}</span><div><strong>{adapted?'原厂桌面保留':'当前固件未适配'}</strong></div></div></div></div>}
      {screen==='curate'&&<div className="q-body q-curate q-preset-page"><div className="q-section-heading"><div><p className="q-eyebrow">02 / 预装应用</p><h1>禁用预装应用</h1></div><span className="q-preset-label">{adapted?`索尼预设 · 已选 ${selectedApps.length} 项`:'暂无可用预设'}</span></div>
        {adapted?<div className="q-curate-list q-preset-grid">{state.apps.filter(a=>a.suggested).map(a=><button key={a.id} className={`q-curate-row ${choices.includes(a.id)&&a.enabled&&a.installed?'chosen':''}`} aria-pressed={choices.includes(a.id)&&a.enabled&&a.installed} disabled={!a.enabled||!a.installed} onClick={()=>toggleChoice(a.id)}><Icon app={a}/><span className="q-curate-copy"><strong>{a.name}</strong>{(!a.enabled||!a.installed)&&<small>{a.installed?'已禁用':'已卸载'}</small>}</span><span className="q-checkbox">{choices.includes(a.id)&&a.enabled&&a.installed&&<Check/>}</span></button>)}</div>:<div className="q-empty"><ShieldCheck/><p>当前固件未适配，预装应用只读。</p></div>}
        <div className="q-curate-bottom"><span><ShieldCheck/>禁用保留数据，可恢复。</span><div>{adapted&&selectedApps.length>0&&<button className="q-text-button" onClick={finish}>不禁用，进入桌面</button>}<Button primary icon={ArrowRight} data-first onClick={()=>!adapted||!selectedApps.length?finish():state.connected?applyChoices():requireConnection('apply')}>{adapted&&selectedApps.length?`禁用 ${selectedApps.length} 项并完成`:'完成设置'}</Button></div></div>
      </div>}
      {screen==='home'&&<div className="q-home"><header className="q-home-header"><div><p>下午好</p><div className="q-clock">15:39 <span>10月18日 &nbsp; 星期日</span></div></div><div><Button icon={Television} onClick={()=>toast('信号源入口')}>信号源</Button><Button icon={WifiHigh} onClick={()=>toast('网络设置入口')}>有线网络</Button><Button icon={GearSix} onClick={()=>open({type:'homeSettings'})}>设置</Button></div></header><div className="q-home-dock-area"><div className="q-home-dock glass">{state.apps.filter(a=>a.banner&&a.enabled&&a.installed).map(a=><button key={a.id} aria-label={`打开${a.name}`} onClick={()=>toast(`${a.name} · 启动演示`)}><img src={'/assets/'+a.banner} alt={a.name}/></button>)}{!state.apps.some(a=>a.banner&&a.enabled&&a.installed)&&<Button onClick={()=>go('manage')}>前往管理应用</Button>}</div></div><div className="q-home-bottom"><button data-first onClick={()=>go('manage')}><SquaresFour/>应用管理<CaretRight/></button><span>{state.defaultHome?<><Check/> 默认桌面已就绪</>:<button onClick={()=>go('default')}>设为默认桌面<CaretRight/></button>}</span></div></div>}
      {screen==='manage'&&<div className="q-body q-manager"><div className="q-manager-title"><div><button className="q-back" aria-label="返回桌面" onClick={()=>go('home')}><ArrowLeft/></button><h1>应用管理</h1></div><div><button className="q-text-button" onClick={()=>go('recovery')}><ClockCounterClockwise/>恢复中心</button><Button icon={PlugsConnected} onClick={()=>state.connected?open({type:'disconnect'}):requireConnection('manage')}>{state.connected?'已连接':'连接电视'}</Button></div></div><div className="q-manager-toolbar"><nav aria-label="应用筛选">{['全部','预装','已禁用','已卸载'].map(f=><button key={f} onClick={()=>setFilter(f)} aria-pressed={filter===f}>{f}</button>)}</nav><label className="q-search"><MagnifyingGlass/><input aria-label="搜索应用" placeholder="搜索应用" value={query} onChange={e=>setQuery(e.target.value)}/>{query&&<button aria-label="清除搜索" onClick={()=>setQuery('')}><X/></button>}</label></div><div className="q-manager-grid"><div className="q-manager-list" aria-label="应用列表">{available.map(a=><button key={a.id} onFocus={()=>setSelected(a.id)} onClick={()=>{setSelected(a.id);requestAnimationFrame(()=>frame.current.querySelector('.q-detail-actions button')?.focus())}} className={current?.id===a.id?'active':''}><Icon app={a}/><span><strong>{a.name}</strong><small>{a.system?'预装应用':'用户应用'} · {!a.installed?'已卸载':a.enabled?'已启用':'已禁用'}</small></span>{a.protected&&<ShieldCheck/>}</button>)}{!available.length&&<div className="q-empty"><MagnifyingGlass/><p>没有找到相关应用</p><button className="q-text-button" onClick={()=>{setQuery('');setFilter('全部');requestAnimationFrame(()=>frame.current.querySelector('.q-manager-list>button')?.focus())}}>显示全部应用</button></div>}</div><section className="q-detail glass" aria-label="应用详情">{current?<><Icon app={current} large/><h2>{current.name}</h2><span className="q-detail-state"><span className="status-dot"/>{!current.installed?'已卸载':current.enabled?'已启用':'已禁用'} · {current.system?'预装应用':'用户应用'}</span><p>{originals.find(item=>item.id===current.id)?.caption}</p><small className="q-package">{current.pkg||`demo.quiet.${current.id}`}</small><div className="q-detail-actions">{current.protected||!adapted&&current.system?<div className="q-readonly"><ShieldCheck/><strong>{current.protected?'系统保护应用':'当前固件未适配'}</strong></div>:current.installed?<><Button icon={Power} onClick={()=>operate(current,current.enabled?'disable':'enable')}>{current.enabled?'禁用应用':'重新启用'}</Button><Button icon={Trash} onClick={()=>operate(current,'uninstall')}>卸载应用</Button></>:current.system?<Button icon={ArrowCounterClockwise} onClick={()=>operate(current,'restore')}>恢复安装</Button>:<div className="q-readonly"><Package/><strong>需要重新安装</strong></div>}</div></>:<div className="q-empty"><SquaresFour/><p>选择一个应用查看详情</p></div>}</section></div></div>}
      {screen==='recovery'&&<div className="q-body q-recovery"><div className="q-section-heading"><div><p className="q-eyebrow">恢复中心</p><h1>恢复桌面和应用</h1></div><Button icon={ArrowLeft} onClick={()=>go('home')}>返回桌面</Button></div><div className="q-recovery-cards"><section className="glass"><House/><h2>切回原厂桌面</h2><span>{state.defaultHome?'当前：Quiet TV':'当前：原厂桌面 / 未指定'}</span><Button onClick={()=>open({type:'switchBack'})}>选择默认桌面<ArrowRight/></Button></section><section className="glass"><ArrowCounterClockwise/><h2>恢复本次禁用</h2><span>{restorable.length?restorable.map(a=>a.name).join('、'):'暂无可恢复的禁用项'}</span><Button disabled={!restorable.length} onClick={()=>state.connected?open({type:'restoreDisabled'}):requireConnection('recovery')}>查看 {restorable.length} 个可恢复项<ArrowRight/></Button></section></div><div className="q-history-heading"><h2>最近操作</h2></div><div className="q-recent-history">{state.journal.length?state.journal.slice(0,4).map((j,i)=><div key={i}><span>{j.name}</span><span>{labels[j.action]}</span><time>{j.time}</time></div>):<p>暂无操作记录</p>}</div></div>}
      {screen!=='home'&&<footer className="q-footer"><button onClick={back}><ArrowLeft/>返回</button>{onboarding?<div className="q-stepper">{['默认桌面','预装应用'].map((name,index)=><span key={name} className={(screen==='default'?0:1)===index?'active':state.defaultHome&&index===0?'finished':''}><i>{state.defaultHome&&index===0&&screen!=='default'?<Check/>:index+1}</i>{name}</span>)}</div>:<span>方向键 · 确定 · 返回</span>}</footer>}
      {notice&&<div className="q-toast" role="status"><Check/>{notice}</div>}
      {dialog&&<div className="q-modal-overlay" onClick={e=>e.currentTarget===e.target&&close()}><section className={`q-modal glass ${dialog.type==='connection'?'q-connection-modal':''}`} role="dialog" aria-modal="true" aria-label={dialog.type==='operation'?'确认应用操作':dialog.type==='connection'?'连接电视':'设置确认'}><button className="q-modal-close" aria-label="关闭弹窗" onClick={close}><X/></button>
        {dialog.type==='default'&&<><House className="q-panel-symbol"/><h2>选择默认桌面</h2><div className="q-home-choice"><SquaresFour/><strong>Quiet TV</strong><Check/></div><div className="q-inline-actions"><Button data-first onClick={close}>取消</Button><Button primary onClick={setDefault}>始终使用 Quiet TV</Button></div></>}
        {dialog.type==='connection'&&<>
          <h2>{connectStep==='authorize'?'允许调试连接':'连接电视'}</h2>
          {connectStep==='authorize'?<><div className="q-auth-preview"><strong>允许 Quiet TV 连接这台电视？</strong><span><ShieldCheck/>本机连接</span></div><div className="q-inline-actions"><Button data-first onClick={()=>{setConnectStep('guide');setConnectError('授权已取消')}}>取消</Button><Button primary onClick={connected}>允许连接并继续</Button></div></>:<>
            <ol className="q-guide-list"><li><span>1</span><div><strong>开启开发者选项</strong><p>设置 → 关于 → 连点版本号</p></div></li><li><span>2</span><div><strong>{scenario==='pair'?'开启无线调试':'开启网络 / ADB 调试'}</strong><p>{scenario==='pair'?'选择「使用配对码配对设备」':'仅开启 USB 调试可能无法连接'}</p></div></li></ol>
            {scenario==='pair'?<label className="q-field">配对码<input aria-label="6 位配对码" inputMode="numeric" maxLength={6} placeholder="输入 6 位数字" value={code} onChange={e=>setCode(e.target.value.replace(/\D/g,''))}/></label>:<details className="q-advanced"><summary>连接设置<CaretRight/></summary><label className="q-field">调试端口<input aria-label="调试端口" inputMode="numeric" value={port} onChange={e=>setPort(e.target.value)}/></label></details>}
            {connectError&&<p role="alert" className="q-error">{connectError}</p>}
            <div className="q-inline-actions"><Button disabled={busy} onClick={()=>setDialog({type:'settingsGuide',returnConnection:true})}>打开系统设置</Button><Button primary data-first disabled={busy} onClick={connect}>{busy?'正在连接…':destination==='apply'?'连接并禁用所选应用':scenario==='pair'?'配对并继续':'连接并继续'}</Button></div>
          </>}
        </>}
        {dialog.type==='unsupported'&&<><Info className="q-panel-symbol"/><h2>当前固件未适配</h2><p>默认桌面无法确认切换，预装应用保持只读。</p><div className="q-inline-actions"><Button data-first onClick={close}>返回设置</Button><Button onClick={()=>go('home')}>进入桌面</Button></div></>}
        {dialog.type==='operation'&&<><Icon app={dialog.app}/><h2>{labels[dialog.action]}{dialog.app.name}？</h2>{dialog.action!=='enable'&&<p>{dialog.action==='disable'?'保留安装和数据，可重新启用。':dialog.action==='uninstall'?dialog.app.system?'仅为当前用户卸载，应用数据将删除。恢复安装不恢复数据。':'删除应用及数据；重新安装不恢复数据。':'仅恢复安装，不恢复已删除的数据。'}</p>}<div className="q-inline-actions"><Button data-first onClick={close}>取消</Button><Button primary onClick={perform}>确认{labels[dialog.action]}</Button></div></>}
        {dialog.type==='homeSettings'&&<><GearSix className="q-panel-symbol"/><h2>桌面设置</h2><div className="q-setting-options"><button onClick={()=>go('default')}><House/>默认桌面<span>{state.defaultHome?'已设置':'去设置'}</span><CaretRight/></button><button onClick={()=>go('manage')}><SquaresFour/>应用管理<CaretRight/></button><button onClick={()=>go('recovery')}><ClockCounterClockwise/>恢复中心<CaretRight/></button><button onClick={()=>{setScenic(s=>!s);close()}}><Sun/>切换预览背景<CaretRight/></button></div></>}
        {dialog.type==='restoreDisabled'&&<><ArrowCounterClockwise className="q-panel-symbol"/><h2>重新启用这些应用？</h2><ul className="q-confirm-list">{restorable.map(a=><li key={a.id}>{a.name}<span>重新启用</span></li>)}</ul><div className="q-inline-actions"><Button data-first onClick={close}>取消</Button><Button primary onClick={restoreDisabled}>确认恢复</Button></div></>}
        {dialog.type==='switchBack'&&<><House className="q-panel-symbol"/><h2>选择默认桌面</h2><p>保留 Quiet TV 和应用禁用状态。</p><div className="q-inline-actions"><Button data-first onClick={close}>取消</Button><Button primary onClick={()=>{setState(s=>({...s,defaultHome:false}));close();toast('已切回原厂桌面')}}>切回原厂桌面</Button></div></>}
        {dialog.type==='disconnect'&&<><PlugsConnected className="q-panel-symbol"/><h2>已连接这台电视</h2><p>设置保留，下次管理需重新连接。</p><div className="q-inline-actions"><Button data-first onClick={close}>保持连接</Button><Button onClick={()=>{setState(s=>({...s,connected:false}));close();toast('已断开，桌面与应用配置保留')}}>断开连接</Button></div></>}
        {dialog.type==='settingsGuide'&&<><GearSix className="q-panel-symbol"/><h2>在电视系统设置中操作</h2><p>设置 → 关于 → 连点版本号，开启开发者选项和网络调试。</p><Button primary data-first onClick={close}>返回连接</Button></>}
        {dialog.type==='reset'&&<><ArrowCounterClockwise className="q-panel-symbol"/><h2>重新体验设置？</h2><p>仅重置演示数据。</p><div className="q-inline-actions"><Button data-first onClick={close}>取消</Button><Button primary onClick={reset}>重置演示</Button></div></>}
      </section></div>}
    </section>
    <div className="q-preview-footer"><span><span className="q-demo-dot"/>交互演示 · 不操作电视</span><div><button onClick={()=>{go('home');toast(state.defaultHome?'已返回 Quiet TV':'尚未设置默认桌面')}}><House/>模拟 HOME</button><button onClick={()=>{setState(s=>({...s,connected:false}));go('home');toast(state.defaultHome?'配置已保留，管理连接已断开':'应用配置已保留，尚未设置默认桌面')}}><Power/>模拟重启</button><a href="?prototype=management">原有管理原型<ArrowRight/></a></div></div>
  </main>;
}
