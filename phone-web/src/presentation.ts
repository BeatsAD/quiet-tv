import type { Metadata, Task, Value } from './api';

// Names remain useful when a required field is absent, rather than silently losing its row.
export const fieldPresentation:Record<string,{title:string;icon?:string}>={
  childEnabled:{title:'儿童模式'},homeButtons:{title:'首页按钮'},quickControls:{title:'快捷控制'},dockApps:{title:'Dock 应用'},gridOrder:{title:'第二屏应用顺序'},wallpaper:{title:'桌面背景'},saverMedia:{title:'屏保素材'},greeting:{title:'问候语'},cityCode:{title:'天气城市'},
  homeBlurMode:{title:'首页模糊方式'},homeBlur:{title:'模糊度'},homeBrightness:{title:'亮度'},homeFade:{title:'淡入时间'},
  secondBlur:{title:'模糊度'},secondBrightness:{title:'亮度'},secondFade:{title:'淡入时间'},secondNames:{title:'第二屏应用名称'},secondHideHomeApps:{title:'第二屏隐藏首屏应用'},
  layout:{title:'桌面布局',icon:'DashboardIcon'},iconSize:{title:'图标大小'},iconShape:{title:'图标外形'},
  dockScale:{title:'Dock 栏大小'},dockMagnification:{title:'放大幅度'},dockLimit:{title:'显示应用上限'},dockMagnify:{title:'聚焦放大'},globalGlass:{title:'全局玻璃效果'},dockBackground:{title:'Dock 背景'},
  showGreeting:{title:'问候语'},showAvatar:{title:'显示头像'},showTime:{title:'时间'},clockStyle:{title:'时钟样式'},showDate:{title:'日期'},showSeconds:{title:'显示秒数'},showLunarDate:{title:'显示农历'},homeApps:{title:'首页应用区域'},appNames:{title:'首页应用名称'},
  weatherEnabled:{title:'天气',icon:'SunIcon'},autoLocation:{title:'自动定位',icon:'TargetIcon'},provider:{title:'天气来源',icon:'LayersIcon'},
  keySounds:{title:'按键音',icon:'SpeakerLoudIcon'},keySoundTheme:{title:'按键音色'},wallSound:{title:'背景声音'},wallMode:{title:'切换方式'},rotation:{title:'轮换间隔'},dayNight:{title:'随昼夜变化'},
  idle:{title:'闲置等待时间'},saverContent:{title:'屏保内容'},saverSound:{title:'屏保声音'},quickControlVisibility:{title:'快捷控制按钮'},saverClock:{title:'显示时钟'},saverWeather:{title:'显示天气'},
  quizInterval:{title:'答题间隔'},quizGrade:{title:'年级'},restTheme:{title:'休息屏保'},boot:{title:'开机去向'},volume:{title:'音量'},
};

export const desktopFieldIcons: Readonly<Partial<Record<string,string>>> = {
  layout:'DashboardIcon',topShelf:'LayoutTemplateIcon',globalGlass:'LayersIcon',homeBlurMode:'BlendIcon',
  homeBlur:'FocusIcon',homeBrightness:'SunIcon',homeFade:'HourglassIcon',
  secondBlur:'FocusIcon',secondBrightness:'SunIcon',secondFade:'HourglassIcon',
  showGreeting:'MessageCircleIcon',showAvatar:'PersonIcon',showTime:'ClockIcon',
  clockStyle:'WatchIcon',showSeconds:'TimerIcon',showDate:'NotebookIcon',showLunarDate:'MoonIcon',
  quickControlVisibility:'MixerHorizontalIcon',showHomeQuote:'QuoteIcon',showScreensaverQuote:'QuoteIcon',
  homeApps:'PanelsTopLeftIcon',appNames:'ReaderIcon',iconSize:'ScalingIcon',iconShape:'CircleIcon',
  dockLimit:'GridCheckIcon',dockScale:'MoveHorizontalIcon',dockMagnify:'PlusIcon',
  dockMagnification:'MaximizeIcon',dockBackground:'RectangleHorizontalIcon',
  secondNames:'CaptionsIcon',secondHideHomeApps:'DashboardIcon',
  idle:'AlarmClockIcon',saverContent:'MonitorPlayIcon',saverSound:'SpeakerLoudIcon',
  saverClock:'ClockIcon',saverClockPosition:'TargetIcon',saverWeather:'SunIcon',saverWeatherPosition:'MapPinIcon',
  saverFlipSeconds:'TimerIcon',saverFlipSound:'WallpaperSoundIcon',saverSeconds:'TimerIcon',
  saverAnalogClock:'WatchIcon',saverAnalogStyle:'CircleIcon',saverDigitalLayout:'DashboardIcon',
  saverCalendar:'NotebookIcon',saverLunarDate:'MoonIcon',
  keySoundTheme:'WallpaperSoundIcon',wallSound:'SpeakerLoudIcon',wallMode:'Repeat2Icon',
  rotation:'ReloadIcon',dayNight:'MoonIcon',showMovieInfo:'MonitorPlayIcon',
};

export function timerSummary(state:unknown){return ({OFF:'未开启',CONNECTING:'连接中',READY:'已连接',FAULT:'服务异常',UNKNOWN:'状态待核对'} as Record<string,string>)[String(state)]||'状态待核对';}
export function guardSummary(code:unknown){return ({incomplete:'尚未完成',paused:'已暂停',pending:'待验证',unconfigured:'尚未配置',repair:'需要修复',limited:'能力受限',verified:'已验证可用'} as Record<string,string>)[String(code)]||'状态待核对';}

export function taskHasAction(task:Task){return typeof task.result?.download==='string'||typeof task.result?.draftId==='string';}
export function taskNeedsAttention(task:Task){return ['failed','unknown'].includes(task.state)||task.result?.requiresReview===true||task.result?.waitingTelevision===true;}
export function taskIsTransient(task:Task){return task.state==='completed'&&!taskHasAction(task)&&!taskNeedsAttention(task);}

export function visibleTasks(tasks:Task[],domains:string[],dismissed:string[],uploading=false,originating:string[]=[]){
  const latest=new Map<string,Task>();
  for(const task of tasks){
    // Replace the previous outcome before filtering: a later success clears an old failure.
    latest.set(`${task.domain}/${task.kind}`,task);
  }
  return [...latest.values()].filter(task=>{
    if(dismissed.includes(task.id)||task.state==='cancelled'||uploading&&task.kind==='upload')return false;
    // These controls already show their own progress and outcome.
    if(task.domain==='wallpaper'&&task.kind==='dailyRefresh'||task.domain==='apps'&&task.kind==='refresh')return false;
    const applyingDaily=task.domain==='wallpaper'&&task.kind==='dailyApply'&&['queued','running'].includes(task.state);
    const retained=taskHasAction(task)||task.result?.requiresReview===true||task.result?.waitingTelevision===true||applyingDaily;
    if(!originating.includes(task.id)&&!(retained&&domains.includes(task.domain)))return false;
    if(task.domain==='weather'&&task.kind==='refresh')return ['failed','unknown'].includes(task.state);
    return !(task.state==='completed'&&task.domain==='files'&&task.kind==='browse');
  });
}

export function missingSettings(metadata:Metadata|null,values:Record<string,Value>){
  if(!metadata)return [];
  return Object.keys(fieldPresentation).filter(key=>key!=='wallSound'&&(!metadata.fields[key]||values[key]===undefined));
}
