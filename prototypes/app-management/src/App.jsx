import { useEffect, useRef, useState } from 'react';
import { SquaresFour, MagnifyingGlass, SlidersHorizontal, DotsThree, ArrowClockwise, ClockCounterClockwise, Plus, CaretLeft, CaretRight, Check, X, Power, Trash, WifiHigh, GearSix, HardDrives, Play, Television, CloudArrowDown, ShieldCheck, ArrowLeft, House, ImageSquare, PlugsConnected, Moon, Sun, ArrowRight, Keyboard } from '@phosphor-icons/react';

const assets='/assets/';
const initialApps=[
 {id:'feiniu',name:'飞牛 TV',pkg:'com.trim.tv',version:'1.4.1',icon:'feiniu.webp',banner:'feiniu-banner.png',enabled:true},
 {id:'bili',name:'云视听小电视',pkg:'com.xiaodianshi.tv.yst',version:'1.7.9',icon:'bili-icon.png',banner:'bili-banner.png',enabled:true},
 {id:'tianguang',name:'天光云影',pkg:'top.yogiczy.slcs.tv',version:'3.3.8',icon:'tianguang.png',banner:'tianguang-banner.png',enabled:true},
 {id:'thunder',name:'迅雷云盘',pkg:'com.xunlei.downloadprovider.hd',version:'2.0.6',icon:'thunder-icon.png',banner:'thunder-banner.png',enabled:true},
 {id:'files',name:'文件管理',pkg:'com.example.filemanager',version:'2.4.0',glyph:HardDrives,enabled:true},
 {id:'market',name:'当贝市场',pkg:'com.dangbeimarket',version:'5.0.6',glyph:SquaresFour,enabled:true,system:true,protected:true},
 {id:'tencent',name:'腾讯视频',pkg:'com.ktcp.tvvideo',version:'12.8.0',glyph:Play,system:true,enabled:false},
 {id:'sony-media',name:'索尼媒体播放器',pkg:'com.sony.dtv.smartmediaapp',version:'2.1.0',glyph:Television,system:true,enabled:false},
 {id:'settings',name:'系统设置',pkg:'com.android.tv.settings',version:'12',glyph:GearSix,enabled:true,system:true,protected:true},
 {id:'quiet',name:'Quiet TV',pkg:'home.quiet.tv',version:'1.0',glyph:SquaresFour,enabled:true,protected:true},
 {id:'demo',name:'BRAVIA 演示',pkg:'com.sony.dtv.braviashow',version:'1.0.0',glyph:Television,system:true,enabled:false,installed:false,recoverable:true},
 {id:'assistant',name:'应用助手',pkg:'com.sony.dtv.appassistant',version:'3.2.0',glyph:SquaresFour,system:true,enabled:true,protected:true},
];
const initialDock=['feiniu','bili','tianguang','thunder'];
const filters=['全部应用','用户应用','预装应用','已禁用','可恢复'];
function AppIcon({app,large=false}){const Icon=app?.glyph||SquaresFour;return <span className={`app-icon ${large?'large':''} ${app?.icon?'has-image':''}`}>{app?.icon?<img src={assets+app.icon} alt=""/>:<Icon weight="regular"/>}</span>}
function Action({icon:Icon,children,onClick,className='',...props}){return <button className={`pill ${className}`} onClick={onClick} {...props}>{Icon&&<Icon/>}{children}</button>}

export function App(){
 const [apps,setApps]=useState(()=>initialApps.map(a=>({...a,installed:a.installed!==false})));
 const [dock,setDock]=useState(initialDock),[view,setView]=useState('apps'),[selected,setSelected]=useState('feiniu');
 const [connected,setConnected]=useState(true),[connection,setConnection]=useState('idle'),[port,setPort]=useState('5555');
 const [filter,setFilter]=useState(0),[query,setQuery]=useState(''),[dialog,setDialog]=useState(null),[notice,setNotice]=useState('');
 const [scenic,setScenic]=useState(true),[slot,setSlot]=useState(0),[moving,setMoving]=useState(null),[history,setHistory]=useState([]);
 const [homeSelection,setHomeSelection]=useState('management');
 const sceneRef=useRef(null),returnFocus=useRef(null),firstRender=useRef(true),toastTimer=useRef(null),connectionTimer=useRef(null);
 const app=apps.find(a=>a.id===selected)||apps[0];
 const visible=apps.filter(a=>(filter===0?(a.installed||a.recoverable):filter===1?!a.system&&a.installed:filter===2?a.system:filter===3?a.installed&&!a.enabled:a.recoverable&&!a.installed)&&`${a.name} ${a.pkg}`.toLowerCase().includes(query.toLowerCase()));
 const toast=text=>{setNotice(text);clearTimeout(toastTimer.current);toastTimer.current=setTimeout(()=>setNotice(''),3000)};
 const close=()=>{if(connection==='connecting')return;setDialog(null);requestAnimationFrame(()=>returnFocus.current?.isConnected&&returnFocus.current.focus())};
 const open=d=>{returnFocus.current=document.activeElement;setDialog(d)};
 useEffect(()=>{if(dialog)requestAnimationFrame(()=>(document.querySelector('.modal [data-autofocus]')||document.querySelector('.modal input')||document.querySelector('.modal .menu-options button')||document.querySelector('.modal .modal-actions button')||document.querySelector('.modal button'))?.focus());},[dialog]);
 useEffect(()=>{if(!visible.some(a=>a.id===selected)&&visible[0])setSelected(visible[0].id)},[filter,query,apps]);
 useEffect(()=>{if(firstRender.current){firstRender.current=false;requestAnimationFrame(()=>document.querySelector('[data-app="feiniu"]')?.focus())}},[]);
 useEffect(()=>()=>{clearTimeout(connectionTimer.current);clearTimeout(toastTimer.current)},[]);
 const showView=next=>{setView(next);setMoving(null);setDialog(null);requestAnimationFrame(()=>document.querySelector(next==='apps'?'[data-app]':next==='dock'?'[data-slot]':'[data-home="management"]')?.focus())};
 const update=(id,patch)=>setApps(old=>old.map(a=>a.id===id?{...a,...patch}:a));
 const perform=(a,type)=>{
  const name=type==='disable'?'禁用':type==='enable'?'启用':type==='uninstall'?'卸载':'恢复安装';
  if(type==='uninstall'){update(a.id,{installed:false,enabled:false,recoverable:!!a.system});setDock(d=>d.filter(id=>id!==a.id));}
  else update(a.id,{enabled:type!=='disable',installed:true,recoverable:false});
  setHistory(h=>[{name:a.name,action:name,time:new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'})},...h]);close();toast(`${a.name}已${name}（演示）`);
 };
 const confirm=(a,type)=>{if(!connected){open({type:'connection'});return}open({type:'confirm',app:a,action:type})};
 const startConnect=()=>{
  if(!/^\d+$/.test(port)||Number(port)<1||Number(port)>65535){toast('请输入 1–65535 之间的端口');return}
  setConnection('connecting');connectionTimer.current=setTimeout(()=>{setConnected(true);setConnection('idle');setDialog(null);toast('已连接本机 · 当前为演示状态');requestAnimationFrame(()=>returnFocus.current?.focus())},1300);
 };
 const addChoice=(choice,index)=>{setDock(d=>{const next=[...d];if(index>=next.length)next.push(choice);else next[index]=choice;return next});setDialog(null);toast('常用应用已更新');requestAnimationFrame(()=>document.querySelector(`[data-slot="${index}"]`)?.focus())};
 const saveOrder=()=>{setDock(moving.items);setSlot(moving.index);setMoving(null);toast('排列顺序已保存')};
 const shift=direction=>setMoving(current=>{const index=Math.max(0,Math.min(current.items.length-1,current.index+direction));const items=[...current.items];[items[index],items[current.index]]=[items[current.index],items[index]];return {items,index}});
 useEffect(()=>{
  const handle=e=>{
   if(e.key==='Escape'||e.key==='Backspace'&&!['INPUT','TEXTAREA'].includes(e.target.tagName)){
    e.preventDefault();if(dialog){close();return}if(moving){setMoving(null);toast('已取消调整，顺序未改变');return}
    if(view!=='home'){showView('home');return}return;
   }
   if(dialog&&e.key==='Tab'){
    const controls=[...document.querySelectorAll('.modal button:not(:disabled),.modal input:not(:disabled)')].filter(el=>el.offsetParent!==null);
    const first=controls[0],last=controls.at(-1);
    if(e.shiftKey&&document.activeElement===first){e.preventDefault();last?.focus()}
    else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first?.focus()}return;
   }
   if(['INPUT','TEXTAREA'].includes(e.target.tagName))return;
   if(view==='dock'&&moving&&!dialog){if(e.key==='ArrowLeft'||e.key==='ArrowRight'){e.preventDefault();shift(e.key==='ArrowLeft'?-1:1)}if(e.key==='Enter'){e.preventDefault();saveOrder()}return}
   if(e.key==='Home'&&!dialog){e.preventDefault();showView('home');return}
   if(!e.key.startsWith('Arrow'))return;
   e.preventDefault();const scope=document.querySelector('.modal')||sceneRef.current;
   const current=document.activeElement,rect=current?.getBoundingClientRect();if(!rect)return;
   const cx=rect.x+rect.width/2,cy=rect.y+rect.height/2;
   const candidates=[...scope.querySelectorAll('button:not(:disabled),input:not(:disabled)')].filter(el=>el!==current&&el.offsetParent!==null);
   const horizontal=e.key==='ArrowLeft'||e.key==='ArrowRight',positive=e.key==='ArrowDown'||e.key==='ArrowRight';
   const target=candidates.map(el=>{const r=el.getBoundingClientRect(),dx=r.x+r.width/2-cx,dy=r.y+r.height/2-cy,primary=horizontal?dx:dy,cross=horizontal?dy:dx;return {el,primary,cross,score:Math.abs(primary)+Math.abs(cross)*2.8}}).filter(t=>positive?t.primary>8:t.primary< -8).sort((a,b)=>a.score-b.score)[0];
   target?.el.focus({preventScroll:true});target?.el.scrollIntoView({block:'nearest',inline:'nearest'});
  };document.addEventListener('keydown',handle);return()=>document.removeEventListener('keydown',handle);
 });
 const connectionLabel=connection==='connecting'?'正在连接':connected?'ADB 已连接':'ADB 未连接';
 const shownDock=moving?.items||dock;
 return <main className="preview-shell">
  <div className="preview-top"><span className="preview-brand"><SquaresFour/> QUIET TV <span>交互预览</span></span><div><button onClick={()=>showView(view==='home'?'apps':'home')}><House/>{view==='home'?'应用管理':'首页入口'}</button><button onClick={()=>setScenic(s=>!s)}>{scenic?<Moon/>:<Sun/>}{scenic?'深色背景':'风景背景'}</button></div></div>
  <section ref={sceneRef} className={`tv-frame ${view==='home'?'home-view':''} ${scenic?'scenic':''}`} aria-label="Quiet TV 电视交互原型">
   <div className="wallpaper"/>
   {view==='home'?<div className="home-content">
    <header className="home-header"><div><p className="greeting">下午好</p><div className="home-time">15:39 <span>10月18日&nbsp; 星期日</span></div></div><div className="home-tools"><Action icon={Television} onClick={()=>toast('此处保留电视信号源入口')}>信号源</Action><Action icon={WifiHigh} onClick={()=>toast('此处保留网络设置入口')}>有线网络</Action><Action icon={GearSix} onClick={()=>toast('此处保留系统设置入口')}>设置</Action></div></header>
    <div className="home-hero"><h2>{homeSelection==='management'?'应用管理':apps.find(a=>a.id===homeSelection)?.name||'常用应用'}</h2><div className="home-dock glass">{dock.filter(id=>apps.find(a=>a.id===id)?.enabled).map(id=>{const a=apps.find(a=>a.id===id);return <button key={id} className="home-tile" onFocus={()=>setHomeSelection(id)} onClick={()=>toast(`${a.name} · 应用启动演示`)}><img src={assets+a.banner} alt={a.name}/></button>})}{!dock.length&&<button className="home-tile entry-tile" onClick={()=>showView('dock')}><Plus/><span>添加常用应用</span></button>}</div></div>
    <div className="home-all"><p>全部应用</p><div className="home-apps"><button className="home-tile wallpaper-entry" onClick={()=>open({type:'wallpaper'})}><ImageSquare/><span>壁纸设置</span></button><button data-home="management" className="home-tile entry-tile" onFocus={()=>setHomeSelection('management')} onClick={()=>showView('apps')}><SquaresFour weight="regular"/><span>应用管理</span></button><button className="home-tile entry-tile secondary-entry" onClick={()=>toast('系统设置入口演示')}><GearSix/><span>系统设置</span></button></div></div>
   </div>:<div className="management-content">
    <header className="titlebar"><div className="title-group"><button className="back-button" aria-label="返回首页" onClick={()=>showView('home')}><ArrowLeft/></button><h1>应用管理</h1></div><Action className={`connection ${connected?'online':''}`} onClick={()=>open({type:'connection'})}><span className="status-dot"/>{connectionLabel}</Action></header>
    <nav className="toolbar"><div className="tabs" aria-label="管理页面"><button className={view==='apps'?'active':''} aria-current={view==='apps'?'page':undefined} onClick={()=>showView('apps')}>应用列表</button><button className={view==='dock'?'active':''} aria-current={view==='dock'?'page':undefined} onClick={()=>showView('dock')}>常用应用</button></div>{view==='apps'?<div className="tool-actions"><Action icon={MagnifyingGlass} onClick={()=>open({type:'search'})}>搜索</Action><Action icon={SlidersHorizontal} onClick={()=>open({type:'filter'})}>{filter===0?'筛选':filters[filter]}</Action><Action icon={DotsThree} aria-label="更多操作" className="icon-pill" onClick={()=>open({type:'more'})}/></div>:<span className="dock-counter">{dock.length} / 4 个位置</span>}</nav>
    {view==='apps'?<div className="apps-layout"><section className="list-pane"><div className="list-caption"><span>{query?`找到 ${visible.length} 个应用`:`${filters[filter]} · ${visible.length} 个`}</span>{query&&<button className="query-chip" onClick={()=>setQuery('')} aria-label="清除搜索">{query}<X/></button>}</div><div className="app-list" role="list" aria-label="应用列表">{visible.map(a=><button key={a.id} data-app={a.id} className={`app-row ${selected===a.id?'selected':''}`} onFocus={()=>setSelected(a.id)} onClick={()=>{setSelected(a.id);requestAnimationFrame(()=>document.querySelector('.detail-actions button')?.focus())}}><AppIcon app={a}/><span className="app-row-copy"><strong>{a.name}</strong><small>{a.system?'预装应用':'用户应用'}<span>·</span>{!a.installed?'可恢复安装':a.enabled?'已启用':'已禁用'}</small></span>{dock.includes(a.id)&&<span className="favorite-marker" aria-label="已在常用应用"><SquaresFour/></span>}</button>)}{!visible.length&&<div className="empty-list"><MagnifyingGlass/><p>没有找到相关应用</p><small>试试其他名称或包名</small><Action onClick={()=>{setQuery('');setFilter(0)}}>显示全部应用</Action></div>}</div></section>
    <section className="detail-pane glass" aria-label="应用详情">{visible.length>0?<><div className="detail-top"><AppIcon app={app} large/><h2>{app.name}</h2><div className={`state-label ${app.enabled?'enabled':''}`}><span className="status-dot"/>{!app.installed?'可恢复安装':app.enabled?'已启用':'已禁用'}<span className="state-divider">·</span>{app.system?'预装应用':'用户应用'}</div></div><div className="detail-meta"><p>{app.pkg}</p><span>版本 {app.version}</span></div><div className="detail-bottom">{app.protected?<div className="protected-note"><ShieldCheck/><p>系统保护应用</p><small>此应用与桌面或系统正常运行有关，保持只读。</small></div>:<><div className="detail-actions">{!connected?<Action icon={PlugsConnected} onClick={()=>open({type:'connection'})}>连接后管理</Action>:app.installed?<><Action icon={Power} onClick={()=>confirm(app,app.enabled?'disable':'enable')}>{app.enabled?'禁用应用':'启用应用'}</Action><Action icon={Trash} className="danger" onClick={()=>confirm(app,'uninstall')}>卸载应用</Action></>:<Action icon={ArrowClockwise} onClick={()=>confirm(app,'restore')}>恢复安装</Action>}</div><p className="action-hint">{!app.installed?'恢复系统保留的安装包，不恢复已删除的数据。':app.enabled?'禁用可暂时隐藏应用，保留安装与数据。':'重新启用后，原有常用应用位置会自动恢复。'}</p></> }</div></>:<div className="no-detail"><SquaresFour/><p>选择一个应用查看详情</p></div>}</section></div>:<section className="dock-editor"><div className="dock-intro"><h2>{moving?'调整排列顺序':'常用应用，随手可达'}</h2><p>{moving?'左右移动选中的卡片，确定保存，返回取消。':'保留首页四个位置，选择卡片即可替换、移除或排序。'}</p></div><div className={`dock-cards ${moving?'moving':''}`}>{Array.from({length:4},(_,i)=>{const a=apps.find(a=>a.id===shownDock[i]);return <button key={i} data-slot={i} className={`dock-card ${moving?.index===i?'move-selected':''}`} onFocus={()=>!moving&&setSlot(i)} onClick={()=>moving?saveOrder():a?open({type:'slot',index:i,app:a}):open({type:'choose',index:dock.length})}><span className="slot-number">0{i+1}</span>{a?<><div className={`dock-art ${!a.enabled?'disabled-art':''}`}>{a.banner?<img src={assets+a.banner} alt=""/>:<AppIcon app={a} large/>}</div><span className="dock-name">{a.name}</span>{!a.enabled&&<small>已禁用 · 保留位置</small>}</>:<><div className="dock-art empty-slot"><Plus/></div><span className="dock-name muted">添加应用</span></>}</button>})}</div>{moving?<div className="moving-actions"><Action icon={CaretLeft} onClick={()=>shift(-1)}>向左</Action><Action icon={CaretRight} onClick={()=>shift(1)}>向右</Action><Action onClick={()=>{setMoving(null);toast('已取消调整')}}>取消</Action><Action icon={Check} onClick={saveOrder}>保存顺序</Action></div>:<p className="dock-footnote">移除只改变快捷入口，不会禁用或卸载应用。</p>}</section>}
    <footer className="tv-footer"><span><Keyboard/> 方向键移动<span className="footer-dot">·</span>确定选择<span className="footer-dot">·</span>返回上一级</span><span>{view==='dock'?'本地配置 · 无需 ADB':'Quiet TV'}</span></footer>
   </div>}
   {notice&&<div className="toast" role="status"><Check/>{notice}</div>}
   {dialog&&<div className="modal-backdrop" onClick={e=>e.target===e.currentTarget&&close()}><section className={`modal glass ${dialog.type==='choose'?'choice-modal':''}`} role="dialog" aria-modal="true" aria-label={dialog.type==='confirm'?'确认应用操作':'管理设置'}>
    <button className="modal-close" aria-label="关闭弹窗" onClick={close} disabled={connection==='connecting'}><X/></button>
    {dialog.type==='search'&&<><MagnifyingGlass className="modal-emblem"/><h2>搜索应用</h2><p>输入应用名称或包名</p><form onSubmit={e=>{e.preventDefault();close()}}><input aria-label="搜索应用名称或包名" data-autofocus autoComplete="off" value={query} onChange={e=>setQuery(e.target.value)} placeholder="例如：飞牛、com.trim.tv"/><div className="modal-actions"><Action onClick={()=>{setQuery('');close()}}>清除搜索</Action><button className="pill" type="submit">查看结果<ArrowRight/></button></div></form></>}
    {dialog.type==='filter'&&<><SlidersHorizontal className="modal-emblem"/><h2>筛选应用</h2><div className="menu-options">{filters.map((f,i)=><button key={f} data-autofocus={i===filter||undefined} onClick={()=>{setFilter(i);close()}}>{f}{i===filter&&<Check/>}</button>)}</div></>}
    {dialog.type==='more'&&<><h2>更多操作</h2><div className="menu-options"><button onClick={()=>{close();toast('应用列表已刷新')}}><ArrowClockwise/>刷新应用列表</button><button onClick={()=>setDialog({type:'history'})}><ClockCounterClockwise/>操作记录</button><button onClick={()=>{setApps(initialApps.map(a=>({...a,installed:a.installed!==false})));setDock(initialDock);setFilter(0);setQuery('');setSelected('feiniu');setHistory([]);setConnected(true);close();toast('演示状态已恢复')}}><ArrowClockwise/>重置演示状态</button></div></>}
    {dialog.type==='connection'&&<><PlugsConnected className="modal-emblem"/><h2>{connection==='connecting'?'正在连接电视':'本机 ADB 连接'}</h2><p>{connection==='connecting'?'正在等待电视授权，请稍候…':'开启电视网络调试后，在电视上允许 Quiet TV 连接。'}</p><div className="connection-info"><span>连接方式</span><strong>本机 TCP</strong><span>端口</span><input aria-label="ADB 端口" value={port} onChange={e=>setPort(e.target.value)} inputMode="numeric" disabled={connection==='connecting'}/><span>当前用户</span><strong>用户 0</strong></div><small className="prototype-note">交互演示，仅模拟连接状态。</small><div className="modal-actions"><Action onClick={close} disabled={connection==='connecting'}>取消</Action>{connected?<Action onClick={()=>{setConnected(false);close();toast('已断开 ADB，常用应用仍可编辑')}}>断开连接</Action>:<Action icon={PlugsConnected} onClick={startConnect} disabled={connection==='connecting'}>{connection==='connecting'?'正在连接…':'连接电视'}</Action>}</div></>}
    {dialog.type==='confirm'&&<><AppIcon app={dialog.app}/><h2>{dialog.action==='disable'?'禁用':dialog.action==='enable'?'启用':dialog.action==='uninstall'?'卸载':'恢复安装'}{dialog.app.name}？</h2><p>{dialog.action==='disable'?'应用将从首页暂时隐藏，安装与数据都会保留，之后可重新启用。':dialog.action==='enable'?'应用将重新出现在首页，并恢复原来的常用应用位置。':dialog.action==='uninstall'?(dialog.app.system?'为当前用户卸载此应用，并删除应用数据。系统保留的安装包可恢复，数据无法恢复。':'卸载此应用并删除应用数据，同时移除常用应用中的快捷入口。'):'重新安装系统保留的应用，不会恢复此前删除的数据。'}</p><span className="confirm-package">{dialog.app.pkg}</span><div className="modal-actions"><Action data-autofocus onClick={close}>取消</Action><Action className={dialog.action==='uninstall'?'danger':''} onClick={()=>perform(dialog.app,dialog.action)}>确认{dialog.action==='disable'?'禁用':dialog.action==='enable'?'启用':dialog.action==='uninstall'?'卸载':'恢复'}</Action></div></>}
    {dialog.type==='slot'&&<><AppIcon app={dialog.app}/><h2>{dialog.app.name}</h2><p>第 {dialog.index+1} 个常用应用</p><div className="menu-options"><button onClick={()=>setDialog({type:'choose',index:dialog.index})}><ArrowClockwise/>替换应用</button><button onClick={()=>{setDock(d=>d.filter((_,i)=>i!==dialog.index));close();toast('已移除快捷入口，应用仍然保留')}}><X/>移除快捷入口</button><button onClick={()=>{setMoving({items:[...dock],index:dialog.index});setDialog(null);requestAnimationFrame(()=>document.querySelector(`[data-slot="${dialog.index}"]`)?.focus())}}><CaretLeft/>调整排列顺序<CaretRight/></button></div></>}
    {dialog.type==='choose'&&<><h2>{dialog.index>=dock.length?'添加常用应用':'替换常用应用'}</h2><p>选择已启用的应用，最多保留四个。</p><div className="choose-apps">{apps.filter(a=>a.installed&&a.enabled&&!a.protected&&!dock.includes(a.id)).map(a=><button key={a.id} onClick={()=>addChoice(a.id,dialog.index)}><AppIcon app={a}/><span>{a.name}</span><Plus/></button>)}{!apps.some(a=>a.installed&&a.enabled&&!a.protected&&!dock.includes(a.id))&&<p>没有可添加的应用。可先启用其他应用。</p>}</div></>}
    {dialog.type==='history'&&<><ClockCounterClockwise className="modal-emblem"/><h2>操作记录</h2><p>记录应用状态变化，不是应用数据备份。</p><div className="history-list">{history.length?history.map((h,i)=><div key={i}><span><strong>{h.name}</strong><small>{h.action}成功 · 演示</small></span><time>{h.time}</time></div>):<div className="history-empty">暂无操作记录</div>}</div><div className="modal-actions"><Action onClick={close}>关闭</Action></div></>}
    {dialog.type==='wallpaper'&&<><ImageSquare className="modal-emblem"/><h2>背景预览</h2><p>对比纯深色与当前风景壁纸下的界面效果。</p><div className="modal-actions"><Action icon={Moon} onClick={()=>{setScenic(false);close()}}>深色背景</Action><Action icon={Sun} onClick={()=>{setScenic(true);close()}}>风景背景</Action></div></>}
   </section></div>}
  </section>
  <div className="preview-bottom"><span>网页交互原型 · 演示数据</span><span>鼠标点击，或使用方向键 / Enter / Esc 体验遥控操作</span><span>确认后再更新原生界面</span></div>
 </main>
}
