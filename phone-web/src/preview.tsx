import { wallpaperWallIds, WallpaperWallPreview } from './WallpaperWall';
import flipSoundUrl from '../../app/src/main/res/raw/flip_clock.wav?no-inline';
import { FlipClock } from './FlipClock';
import { SaverClock, saverClockLayout, type SaverClockTime } from './SaverClock';
import { useCallback, useEffect, useLayoutEffect, useMemo, useId, useRef, useState, type CSSProperties } from 'react';
import type { PhoneClient, Value, App } from './api';
import { DynamicBackground } from './DynamicBackground';
import { AnalogClock } from './AnalogClock';
import { Icon, quickGlyph, ResourceImage } from './widgets';
import { QuickIcon, type QuickIcons, type QuickItem } from './QuickPreview';
export function PreviewPagePicker({value,onChange}:{value:string;onChange:(value:string)=>void}){
  const [open,setOpen]=useState(false),root=useRef<HTMLDivElement>(null),trigger=useRef<HTMLButtonElement>(null),menu=useRef<HTMLDivElement>(null),id=useId();
  useEffect(()=>{if(!open)return;const outside=(event:PointerEvent)=>{if(!root.current?.contains(event.target as Node))setOpen(false);},escape=(event:KeyboardEvent)=>{if(event.key==='Escape'){event.preventDefault();setOpen(false);trigger.current?.focus({preventScroll:true});}};document.addEventListener('pointerdown',outside);document.addEventListener('keydown',escape);return()=>{document.removeEventListener('pointerdown',outside);document.removeEventListener('keydown',escape);};},[open]);
  useLayoutEffect(()=>{if(open)menu.current?.querySelector<HTMLButtonElement>('[aria-checked=true]')?.focus({preventScroll:true});},[open]);
  return <div ref={root} className="preview-selector"><button ref={trigger} aria-label={`预览页面：${value==='home'?'首页':'第二屏'}`} aria-haspopup="menu" aria-expanded={open} aria-controls={open?id:undefined} onClick={()=>setOpen(!open)} onKeyDown={event=>{if(event.key==='ArrowDown'||event.key==='ArrowUp'){event.preventDefault();setOpen(true);}}}>{value==='home'?'首页':'第二屏'}<Icon name="ChevronDownIcon"/></button>{open&&<div ref={menu} id={id} role="menu" aria-label="预览页面" className="preview-menu" onKeyDown={event=>{if(event.key==='Tab'){setOpen(false);return;}const buttons=Array.from(menu.current!.querySelectorAll<HTMLButtonElement>('button')),index=buttons.indexOf(document.activeElement as HTMLButtonElement);let next;if(event.key==='ArrowDown')next=(index+1)%buttons.length;else if(event.key==='ArrowUp')next=(index+buttons.length-1)%buttons.length;else if(event.key==='Home')next=0;else if(event.key==='End')next=buttons.length-1;if(next!==undefined){event.preventDefault();buttons[next].focus();}}}>{[{id:'home',title:'首页'},{id:'second',title:'第二屏'}].map(item=><button key={item.id} role="menuitemradio" aria-checked={value===item.id} tabIndex={-1} onClick={()=>{onChange(item.id);setOpen(false);trigger.current?.focus({preventScroll:true});}}><span>{item.title}</span>{value===item.id&&<Icon name="CheckIcon"/>}</button>)}</div>}</div>;
}
export function dockGeometry(count:number,v:Record<string,Value>){
  const square=v.iconShape!=='rectangle',aspect=square?1:16/9,size=String(v.iconSize),scale=Number(v.dockScale)/100;
  if(!count){const width=151.2*(size==='small'?5.6/8.2:size==='large'?10.4/8.2:1)*scale,height=width*203/360;const top=404+151.2*203/360-height;return{left:(960-width)/2,top,focusedTop:top,width,height,cardWidth:width,cardHeight:height,gap:0,padding:0};}
  const capacity=(864-2*16.75-8*Math.max(0,count-1))/count;
  const base=(size==='small'?112:size==='large'?168:144)*Math.min(1,capacity/144);
  const heightLimit=(540-32.4-(v.appNames==='top-left'?344:280)-2*16.75-(v.appNames==='below'?25.6:0))*aspect;
  const maximum=Math.min(130,5*Math.floor((Math.min(capacity,heightLimit)/base*100+.001)/5));
  const cardWidth=base*Math.min(Number(v.dockScale),maximum)/100,cardHeight=cardWidth/aspect,gap=count<2?0:Math.max(8,Math.min(14,(864-2*16.75-cardWidth*count)/(count-1)));
  const width=cardWidth*count+gap*(count-1)+2*16.75,height=cardHeight+(v.appNames==='below'?25.6:0)+2*16.75;
  const top=540-32.4-height,zoom=v.dockMagnify?Math.min(Number(v.dockMagnification||100)/100,heightLimit/cardWidth):1;
  const focusedTop=top-cardHeight*(zoom-1);
  return{left:(960-width)/2,top,focusedTop,width,height,cardWidth,cardHeight,gap,padding:16.75};
}
function previewMovie(values:Record<string,Value>){
  const daily=values.dailyWallpaper as {cached?:boolean;sourceId?:string;movie?:{title?:string;summary?:string}}|undefined;
  const movie=daily?.movie;
  return values.showMovieInfo&&values.wallMode==='daily'&&values.wallpaper==='daily'&&values.dailySource==='movie-daily'
    &&daily?.cached&&daily.sourceId==='movie-daily'&&(movie?.title||movie?.summary)?movie:undefined;
}
function quoteFor(values:Record<string,Value>,date:string){
  const quote=values.dailyQuote as {date?:string;text?:string;credit?:string}|undefined;
  return quote?.date===date&&quote.text&&quote.credit?quote:undefined;
}
function Quote({quote}:{quote:NonNullable<ReturnType<typeof quoteFor>>}){
  return <div className="tv-quote"><span>{quote.text}</span><small>{quote.credit}</small></div>;
}
export function Preview({client,page,suspended=false,coverOverride}:{client:PhoneClient;page:string;suspended?:boolean;coverOverride?:{id:string;url:string}}){
  const [problem,setProblem]=useState(''),[retry,setRetry]=useState(0);const [,tick]=useState(0);
  const v=client.values(),glass=Boolean(v.globalGlass??v.dockGlass??true),second=page==='second',gallery=v.layout==='gallery',blur=Number(v[second?'secondBlur':'homeBlur']||0),brightness=Number(v[second?'secondBrightness':'homeBrightness']??100)/100;
  const analog=gallery&&(v.clockStyle==='analog-light'||v.clockStyle==='analog-dark'),showSeconds=!!v.showSeconds;
  useEffect(()=>{
    let timer:ReturnType<typeof setTimeout>|undefined;
    const refresh=()=>{if(timer!==undefined)clearTimeout(timer);if(document.hidden||suspended||second||(!v.showTime&&!v.showHomeQuote))return;tick(n=>n+1);const period=showSeconds?1000:60000,offset=Number(client.metadata?.clockOffsetMs||0);timer=setTimeout(refresh,period-((Date.now()+offset)%period));};
    refresh();document.addEventListener('visibilitychange',refresh);return()=>{if(timer!==undefined)clearTimeout(timer);document.removeEventListener('visibilitychange',refresh);};
  },[suspended,second,v.showTime,v.showHomeQuote,showSeconds,client.metadata?.clockOffsetMs]);
  const apps=(client.metadata?.homeApps||[]) as App[],dock=(v.dockApps||[]) as string[],order=(v.gridOrder||[]) as string[];
  const visible=apps.filter(app=>!app.hidden&&!app.disabled&&!app.removed),grid=order.map(id=>visible.find(app=>app.id===id)).filter((app):app is App=>!!app);
  for(const app of visible)if(!grid.some(item=>item.id===app.id))grid.push(app);
  const pinned=dock.map(id=>visible.find(app=>app.id===id)).filter((app):app is App=>!!app).slice(0,Number(v.dockLimit));
  const selected=second?(v.homeApps&&v.secondHideHomeApps?grid.filter(app=>!pinned.some(item=>item.id===app.id)):grid):pinned;
  const geometry=dockGeometry(selected.length,v),columns=v.iconShape==='rectangle'?(v.iconSize==='small'?6:v.iconSize==='large'?4:5):7,unit=(n:number)=>`${n/9.6}cqw`,x=(n:number)=>`${n/9.6}%`,y=(n:number)=>`${n/5.4}%`;
  const progressive=!second&&v.homeBlurMode==='progressive';
  const movie=second?undefined:previewMovie(v);
  // Match DockLayout.featuredTitleTop; the browser preview draws the unfocused row.
  const titleTop=(top:number)=>Math.min(top+geometry.padding-14,v.dockBackground?top-5:Infinity)-56;
  const applicationTop=!v.homeApps?540:v.appNames==='top-left'&&selected.length?titleTop(geometry.focusedTop):geometry.top;
  const galleryOffset=Math.max(0,Math.min(56,applicationTop-280));
  const now=new Date(Date.now()+Number(client.metadata?.clockOffsetMs||0)),zone=client.metadata?.timeZone||'UTC';
  let clockParts:{hour:number;minute:number;second:number}|undefined;
  let time='',date='',lunarKey='';try{time=now.toLocaleTimeString('zh-CN',{timeZone:zone,hour:'2-digit',minute:'2-digit',...(v.showSeconds?{second:'2-digit' as const}:{}),hour12:false});date=now.toLocaleDateString('zh-CN',{timeZone:zone,month:'long',day:'numeric',weekday:'long'});const parts=new Intl.DateTimeFormat('en',{timeZone:zone,year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23'}).formatToParts(now);lunarKey=['year','month','day'].map(type=>parts.find(part=>part.type===type)?.value||'').join('-');clockParts={hour:Number(parts.find(p=>p.type==='hour')?.value),minute:Number(parts.find(p=>p.type==='minute')?.value),second:Number(parts.find(p=>p.type==='second')?.value)};}catch{time='时间暂不可用';}
  const lunar=v.showLunarDate&&v.lunarDateFor===lunarKey?String(v.lunarDate||''):'';
  const galleryLabels=(v.showAvatar?36:v.showGreeting?30:0)+(v.showDate?(lunar?50:30):0)+(v.weatherEnabled&&v.weatherText?34:0);
  const quote=!second&&!movie&&v.showHomeQuote?quoteFor(v,lunarKey):undefined;
  const galleryDial=movie?80:Math.max(80,Math.min(136,Math.floor(applicationTop-36-galleryLabels-(quote?66:0))));
  const quoteTop=gallery&&quote?y(Math.min(v.showTime?(analog?24:75):97,Math.max(8,applicationTop-258))):undefined;
  function art(app:App,height:number){return <div className={`tv-app ${v.iconShape==='circle'?'round-icon':''}`} style={{'--preview-card-radius':v.iconShape==='circle'?'50%':unit(height*.22)} as CSSProperties}><div className={`tv-cover has-custom-cover ${app.id===coverOverride?.id||app.coverFit==='contain'?'library-cover':''}`}>{app.cover&&<ResourceImage src={app.id===coverOverride?.id?coverOverride.url:client.resource(app.cover)} alt={app.name}/>}</div>{(second?v.secondNames:v.appNames==='below')&&<span className="tv-app-name">{app.name}</span>}</div>;}
  const buttonIds=[...((v.homeButtons||[]) as string[]).filter(id=>id!=='settings'&&id!=='control'),...(v.quickControlVisibility==='focus'?[]:['control'])];
  const quickCatalog=(client.metadata?.quickCatalog||[]) as QuickItem[],quickIcons=client.metadata?.quickIcons as QuickIcons|undefined;
  return <><div className={`tv-preview ${second?'second-preview':''} ${gallery?'gallery-layout':''} ${analog&&v.showTime?'analog-layout':''} ${movie?'has-movie-info':''}`} style={{'--movie-offset':unit(galleryOffset),'--gallery-dial':unit(galleryDial)} as CSSProperties} data-glass={glass} data-testid="desktop-preview" data-scene={page}>
    <DynamicBackground client={client} v={v} suspended={suspended} onProblem={setProblem} retry={retry} filter={`brightness(${brightness}) blur(${unit(progressive?0:blur*.48)})`}/>
    {progressive&&blur>0&&<div className="tv-progressive-blur" style={{backdropFilter:`blur(${unit(blur*.54)})`}}/>}
    {movie&&<div className="tv-movie-info" role="group" aria-label="影片信息"><strong className="tv-movie-title">{movie.title}</strong><p className="tv-movie-summary">{movie.summary}</p></div>}
    {!second&&<><div className="tv-information" style={quoteTop?{top:quoteTop}:undefined}><div className="tv-intro">{(v.showGreeting||v.showAvatar)&&<span className="tv-greeting">{!!v.showAvatar&&!!v.avatar&&<ResourceImage src={client.resource(String(v.avatar))} alt="头像"/>}{!!v.showGreeting&&<span>{String(v.greeting||v.automaticGreeting||'')}</span>}</span>}{v.weatherEnabled&&v.weatherText&&<span className="tv-weather">{String(v.weatherText)}</span>}</div><div className="tv-time-row">{v.showTime&&(analog&&clockParts?<AnalogClock style={String(v.clockStyle)} {...clockParts} seconds={showSeconds} label={time}/>:<strong>{time}</strong>)}{v.showTime&&v.showDate&&<span className="tv-time-divider"/>}{v.showDate&&<span className="tv-calendar"><span className="tv-date">{date}</span>{lunar&&<span className="tv-lunar-date">{lunar}</span>}</span>}</div>{quote&&<Quote quote={quote}/>}</div><div className="tv-entries">{buttonIds.map(id=><span key={id} className="tv-entry"><QuickIcon glyph={id==='control'?'header-control':quickCatalog.find(item=>item.id===id)?.glyph||id} icons={quickIcons} fallback={quickGlyph(id)}/></span>)}</div></>}
    {!second&&v.homeApps&&v.appNames==='top-left'&&selected.length>0&&<div className="tv-app-title" style={{top:y(titleTop(geometry.top))}}>{selected[0].name}</div>}
    {second?<div className="tv-app-grid" style={{gridTemplateColumns:`repeat(${columns},minmax(0,1fr))`}}>{selected.map(app=><div className="tv-grid-cell" key={app.id} style={{aspectRatio:v.iconShape==='rectangle'?'16/9':'1'}}>{art(app,80)}</div>)}</div>:v.homeApps&&selected.length>0&&<div data-glass={glass} className={`tv-dock ${v.dockBackground?'with-dock-background':''}`} style={{left:x(geometry.left),top:y(geometry.top),width:x(geometry.width),height:y(geometry.height),backdropFilter:v.dockBackground&&glass?`blur(${unit(20)})`:'none'}}>{selected.map((app,i)=><div className="tv-dock-cell" key={app.id} style={{left:`${(geometry.padding+i*(geometry.cardWidth+geometry.gap))/geometry.width*100}%`,top:`${geometry.padding/geometry.height*100}%`,width:`${geometry.cardWidth/geometry.width*100}%`,height:`${geometry.cardHeight/geometry.height*100}%`}}>{art(app,geometry.cardHeight)}</div>)}</div>}{!second&&!!v.homeApps&&!selected.length&&<div className="tv-empty-card" style={{left:x(geometry.left),top:y(geometry.top),width:x(geometry.width),height:y(geometry.height),gap:unit(24*geometry.width/360)}}><span className="tv-empty-symbol" style={{width:unit(76*geometry.width/360),height:unit(76*geometry.width/360)}}><Icon name="PlusIcon"/></span><span className="tv-empty-title" style={{fontSize:unit(33*geometry.width/360)}}>添加常用应用</span></div>}
  </div>{problem&&<p className="preview-status" role="status">{problem}<button className="text-action" onClick={()=>setRetry(value=>value+1)}>重试预览</button></p>}</>;
}

export function SaverPreview({client,suspended=false}:{client:PhoneClient;suspended?:boolean}){
  const [problem,setProblem]=useState(''),[retry,setRetry]=useState(0),[,tick]=useState(0);
  const [visible,setVisible]=useState(!document.hidden);
  const v=client.values(), layout=saverClockLayout(v);
  const flipping=v.saverContent==='flip-clock', clockOnly=layout.digital||flipping;
  const offset=Number(client.metadata?.clockOffsetMs||0), zone=client.metadata?.timeZone||'UTC';
  const active=!suspended&&visible;
  const soundAvailable=flipping&&!!v.saverFlipSound;
  const audio=useRef<HTMLAudioElement>(null);
  const [soundEnabled,setSoundEnabled]=useState(false),[soundError,setSoundError]=useState(false);
  const playSound=useCallback(()=>{
    const cue=audio.current;
    if(!cue||document.hidden)return;
    cue.currentTime=0;
    void cue.play().catch(error=>{
      if(error.name==='AbortError'||audio.current!==cue||document.hidden)return;
      setSoundError(true);
      setSoundEnabled(false);
    });
  },[]);
  useEffect(()=>{
    if(!soundAvailable)setSoundEnabled(false);
    if(!active||!soundAvailable||!soundEnabled)audio.current?.pause();
  },[active,soundAvailable,soundEnabled]);
  const seconds=flipping?!!v.saverFlipSeconds:layout.seconds,period=seconds?1000:60000;
  useEffect(()=>{
    const visibility=()=>setVisible(!document.hidden);
    document.addEventListener('visibilitychange',visibility);
    return()=>document.removeEventListener('visibilitychange',visibility);
  },[]);
  useEffect(()=>{
    if(!active)return;
    let timer:ReturnType<typeof setTimeout>;
    const schedule=()=>{
      const remainder=((Date.now()+offset)%period+period)%period;
      timer=setTimeout(()=>{tick(n=>n+1);schedule();},period-remainder);
    };
    schedule();
    return()=>clearTimeout(timer);
  },[active,offset,period]);
  const formatter=useMemo(()=>{
    try{
      return new Intl.DateTimeFormat('en-GB',{
        timeZone:zone,calendar:'gregory',year:'numeric',month:'2-digit',day:'2-digit',
        hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23',
      });
    }catch{return null;}
  },[zone]);
  const now=Date.now()+offset;
  const parts=formatter?.formatToParts(now);
  const part=(name:Intl.DateTimeFormatPartTypes)=>parts?.find(p=>p.type===name)?.value||'';
  const dateKey=parts?`${part('year')}-${part('month')}-${part('day')}`:'';
  const quote=!layout.digital&&v.showScreensaverQuote?quoteFor(v,dateKey):undefined;
  const timeLabel=parts?`${part('hour')}:${part('minute')}${seconds?`:${part('second')}`:''}`:'时间暂不可用';
  const time:SaverClockTime={year:Number(part('year')),month:Number(part('month')),day:Number(part('day')),
    hour:Number(part('hour')),minute:Number(part('minute')),second:Number(part('second'))};
  useEffect(()=>{if(active)client.refreshClockMetadata();},[active,client,dateKey]);
  const lunarData=layout.lunar?client.metadata?.saverLunar:undefined;
  const lunar=lunarData?.date===dateKey?lunarData.text:'';
  const monthStarts=lunarData?.date.slice(0,7)===dateKey.slice(0,7)?lunarData.monthStarts:[];
  const wall=v.saverContent==='wall', wallSource=wall?wallpaperWallIds(client).join(','):'';
  const follow=v.saverContent==='follow', id=String(follow?v.wallpaper:v.saverMedia);
  const media=client.metadata?.wallpapers.find(item=>item.id===id);
  const scene=follow?v:{...v,wallpaper:id,wallMode:'fixed',wallVideo:!!media?.video,previewKey:`${media?.bytes||0}`};
  const information=<div className="saver-overlays">
    <div className="saver-overlay-main">
      {flipping&&parts&&<FlipClock time={timeLabel} tick={Math.floor(now/period)} active={active} onLanded={soundAvailable&&soundEnabled?playSound:undefined}/>}
      {layout.composed&&parts&&<SaverClock time={time} timeLabel={timeLabel} layout={layout} lunar={lunar} monthStarts={monthStarts}/>}
      {['top-left','top-right','center','bottom-left','bottom-right'].map(position=><div
        className={`saver-information saver-${position}${layout.composed&&position==='center'?' saver-weather-under-clock':''}`} key={position}>
        {!flipping&&(!layout.composed||!parts)&&(!!v.saverClock||clockOnly)&&(v.saverClockPosition||'center')===position&&
          <strong className={layout.seconds?'saver-time-seconds':undefined}>{timeLabel}</strong>}
        {!!v.saverWeather&&!!v.weatherText&&(v.saverWeatherPosition||'bottom-left')===position&&
          <span className="saver-weather">{String(v.weatherText)}</span>}
      </div>)}
    </div>
    {quote&&<div className="tv-saver-quote"><Quote quote={quote}/></div>}
  </div>;
  return <>
    <div className={`tv-preview saver-preview${flipping?' saver-mechanical':''}${flipping&&seconds?' saver-with-seconds':''}`} aria-label="手机屏保预览">
      {!wall&&!clockOnly&&(follow||id!=='blank')&&<DynamicBackground client={client} v={scene} filter="none" suspended={!active} onProblem={setProblem} retry={retry}/>}
      {wall?<WallpaperWallPreview key={wallSource} client={client} source={wallSource} active={active}>{information}</WallpaperWallPreview>:information}
    </div>
    {soundAvailable&&<>
      <audio ref={audio} src={flipSoundUrl} preload="none"/>
      <button className="secondary-button" type="button" disabled={!active} aria-pressed={soundEnabled} onClick={()=>{
        if(soundEnabled){setSoundEnabled(false);return;}
        setSoundError(false);setSoundEnabled(true);playSound();
      }}>{soundEnabled?'关闭预览声音':'试听翻页声音'}</button>
      {soundError&&<p className="section-note" role="status">暂时无法播放，请重新试听。</p>}
    </>}
    {!wall&&!clockOnly&&problem&&<p className="preview-status" role="status">{problem}<button className="text-action" onClick={()=>setRetry(value=>value+1)}>重试预览</button></p>}
  </>;
}
