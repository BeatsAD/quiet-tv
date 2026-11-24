import { wallpaperWallGrid, wallpaperWallIds } from './WallpaperWall';
import { staticWallpaperAccept } from './media';
import { BootAds, bootAdsLabel } from './BootAds';
import { ChildTime, childTimeDraft, childTimeRule, type ChildTimeDraft } from './ChildTime';
import { UpdateSettings } from './UpdateSettings';
import { saverClockLayout } from './SaverClock';
import { clearConnectionRoute, clearLegacyConnectionKey, connectionRoute, copyConnectionAddress } from './connection';
import { wallpaperAccept } from './media';
import { adbLabel } from './AdbManagement';
import { useOperationFeedback } from './OperationFeedback';
import { taskNotice } from './operationResults';
import { DesktopSwitch } from './DesktopSwitch';
import { WeatherComponentRow, WeatherComponent, OpenAssistant, weatherComponent } from './Components';
import { IconLibrary } from './IconLibrary';
import { desktopIconShape, type IconCatalog } from './IconLibraryPicker';
import { MediaRename } from './MediaRename';
import { AppRename, appRenameTarget } from './AppRename';
import { mediaRenameTarget } from './media';
import { QuizSettingsForm, QuizQuestions, QuizQuestionEditor, ChildRestPicker, quizDraft as newQuizDraft, type QuizDraft } from './ChildQuiz';
import { WallpaperUpload } from './WallpaperUpload';
import { useCallback, useEffect, useLayoutEffect, useMemo, useReducer, useRef, useState } from 'react';
import { PhoneClient, ApiError, UploadError, type Value, type App as TvApp, type Media, newId } from './api';
import { Group, Icon, Row, Setting, ChoiceList, Sheet, ConfirmDialog, ResourceImageRefresh, fileSize, ResourceImage, Switch, AppImage, quickGlyph, ParentPinInput } from './widgets';
import { Preview, SaverPreview, PreviewPagePicker, previewMovie } from './preview';
import { clearResourceImages } from './ResourceImageCache';
import { CoverCrop } from './CoverCrop';
import { AppActionConfirmation, applicationActionTitle, type ApplicationConfirmation } from './AppActionConfirmation';
import { QuickSort } from './QuickSort';
import { canAddQuick, QUICK_BUTTON_LIMIT, QUICK_RANGE_LIMIT, quickChangeAllowed, QUICK_SETTINGS, quickButtonCount, quickButtons, quickOverflow, quickRanges, quickOrder, isQuickRange, quickDraftConflict, type QuickDraft } from './quickRules';
import { QuickIcon, quickSoundOn, type QuickIcons, type QuickItem } from './QuickPreview';
import { DailyWallpaperDetails } from './DailyWallpaperDetails';
import { DailyWallpaperSource } from './DailyWallpaperSource';
import { TaskResult } from './TaskResult';
import { ToolBody, WeatherSettings, WeatherSummary } from './Tools';
import { visibleTasks, taskHasAction, taskNeedsAttention, timerSummary, guardSummary, missingSettings, fieldPresentation } from './presentation';
import { useContentMotion } from './motion';
import { AboutSettings, type AboutTab } from './AboutSettings';
type View={kind:string;title:string;keys?:string[];id?:string;message?:string;mediaType?:'image'|'video';quickRange?:boolean};
const tabs=['桌面显示','快捷控制','管理应用','儿童模式','工具','通用设置','更新与帮助'];
const childTab=3,toolsTab=4,generalTab=5,helpTab=6;
const home=['homeBlurMode','homeBlur','homeBrightness','homeFade'],second=['secondBlur','secondBrightness','secondFade'];
const dockFields=['dockScale','dockMagnify','dockMagnification','dockBackground'];
const syncLabels={synced:'已同步',saving:'正在保存',checking:'结果待核对',offline:'正在重连',failed:'保存未完成',conflict:'设置有冲突',ended:'连接已结束'};
export function App(){
  const feedback=useOperationFeedback();
  const client=useMemo(()=>new PhoneClient(),[]),[,redraw]=useReducer(n=>n+1,0);
  const [pairingCode,setPairingCode]=useState(''),[pairingBusy,setPairingBusy]=useState(false),[pairingError,setPairingError]=useState('');
  const [quickDraft,setQuickDraft]=useState<QuickDraft|null>(null),[quickSaving,setQuickSaving]=useState(false),[quickError,setQuickError]=useState('');
  const [timeDraft,setTimeDraft]=useState<ChildTimeDraft|null>(null);
  const [updateLink,setUpdateLink]=useState(()=>connectionRoute().get('tool')==='update');
  const [selectedChild,setSelectedChild]=useState(''),appsRequested=useRef(false);
  const [quizDraftValue,setQuizDraftValue]=useState<QuizDraft|null>(null),[quizSelected,setQuizSelected]=useState<string[]>([]);
  const [appFilter,setAppFilter]=useState(0),[pin,setPin]=useState(''),[newPin,setNewPin]=useState(''),[confirmPin,setConfirmPin]=useState(''),[recovery,setRecovery]=useState('');
  const [removeMedia,setRemoveMedia]=useState<Media|null>(null);
  const [appConfirmation,setAppConfirmation]=useState<ApplicationConfirmation|null>(null);
  const [renameApp,setRenameApp]=useState<string|null>(null),[appRenameLink,setAppRenameLink]=useState(()=>appRenameTarget(connectionRoute()));
  const [childLink,setChildLink]=useState(()=>connectionRoute().get('tab')==='child');
  const [filesLink,setFilesLink]=useState(()=>connectionRoute().get('tool')==='files'),consumedFileOffer=useRef('');
  const [tab,setTab]=useState(childLink?childTab:0),[light,setLight]=useState(false),[page,setPage]=useState('home'),[trail,setTrail]=useState<View[]>([]),[text,setText]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false),[notice,setNotice]=useState(''),[noticePersistent,setNoticePersistent]=useState(false),[slow,setSlow]=useState(false);
  const [desktopRefreshing,setDesktopRefreshing]=useState(false),[desktopPreviewVersion,setDesktopPreviewVersion]=useState(0);
  const viewEpoch=useRef(0),memoDraftId=useRef(''),tabDirection=useRef(1),sheetDirection=useRef(1),content=useRef<HTMLFieldSetElement>(null),closeEpoch=useRef(-1);
  const [closing,setClosing]=useState(false),[rotationCustom,setRotationCustom]=useState(false),[rotationDraft,setRotationDraft]=useState<string|null>(null);
  const header=useRef<HTMLElement>(null),preview=useRef<HTMLElement>(null),root=useRef<HTMLDivElement>(null),scroll=useRef<HTMLDivElement>(null),positions=useRef<Record<number,number>>({}),drafts=useRef<Record<string,string>>({});
  const fileInput=useRef<HTMLInputElement>(null),fileIntent=useRef({kind:'wallpaper',target:''}),cancelUpload=useRef<(()=>Promise<void>)|null>(null);
  const [upload,setUpload]=useState<{name:string;percent:number;message:string;context:string}|null>(null),[file,setFile]=useState<File|null>(null),[dismissedTasks,setDismissedTasks]=useState<string[]>([]);
  const [wallpaperFiles,setWallpaperFiles]=useState<File[]>([]),[uploadSelection,setUploadSelection]=useState(0),operationTasks=useRef<Record<string,string[]>>({});
  const [renameLink,setRenameLink]=useState(()=>mediaRenameTarget(connectionRoute()));
  const [sizes,setSizes]=useState({header:0,preview:0});const view=trail.at(-1),sync=client.sync,metadata=client.metadata,v=client.values();
  const receipt=sync.receipt,checking=receipt.state==='checking',offline=sync.connection.state==='offline',ended=sync.connection.state==='ended';
  const hasConflicts=sync.drafts.some(draft=>draft.state==='conflict'),hasRejections=sync.drafts.some(draft=>draft.state==='rejected');
  const reviewSync=ended||offline||checking||hasConflicts||hasRejections;
  const syncStatus=ended?'ended':checking?'checking':offline?'offline':hasConflicts?'conflict':hasRejections?'failed':sync.saving?'saving':'synced';
  const syncMessages=ended?[sync.connection.message||syncLabels.ended]:[
    ...(offline?[sync.connection.message||syncLabels.offline]:[]),
    ...(checking?[`${metadata?.fields[receipt.field]?.title||'上次操作'}结果待核对`]:[]),
    ...(hasConflicts?['有设置已在电视上更改，手机输入已保留']:[]),
    ...(hasRejections?['部分手机输入保存未完成']:[]),
  ];
  useEffect(()=>{if(appConfirmation&&!trail.some(item=>item.kind==='app'&&item.id===appConfirmation.app.id))setAppConfirmation(null);},[appConfirmation,trail]);
  const quickServer=(client.frame?.domains.quick?.quickControls||[]) as string[],quickRevision=client.frame?.revisions.quick||'',quickGeneration=client.frame?.generation||'';
  const quickReceipt=receipt.state!=='idle'&&receipt.domain==='quick'&&receipt.field==='quickControls';
  const quickConflict=quickDraftConflict(quickDraft,quickRevision,quickGeneration),quickWaiting=quickSaving||!!quickDraft?.submitted&&(quickReceipt||offline);
  const canConfirmQuick=sync.connection.state==='connected'&&!quickReceipt;
  useEffect(()=>{if(quickDraft?.submitted&&canConfirmQuick&&quickServer.join()===quickDraft.ids.join()){setQuickDraft(null);setQuickError('');}},[quickDraft,canConfirmQuick,quickRevision,quickServer.join()]);
  const dismissTask=useCallback((id:string)=>setDismissedTasks(previous=>previous.includes(id)?previous:[...previous,id]),[]);
  useEffect(()=>{if(!notice||noticePersistent)return;const timer=setTimeout(()=>setNotice(''),3000);return()=>clearTimeout(timer);},[notice,noticePersistent]);
  useEffect(()=>{if(notice&&!noticePersistent)feedback({kind:'info',title:notice});},[notice,noticePersistent,feedback]);
  useEffect(()=>{if(noticePersistent&&view?.id==='management'&&client.frame?.domains.management?.connected)setNotice('');},[noticePersistent,view?.id,client.frame?.domains.management?.connected]);
  useContentMotion(content,tab,tabDirection.current);
  useEffect(()=>{if(tab!==2)return;const selected=root.current?.querySelector<HTMLElement>('.app-filter-tabs [aria-selected=true]');if(!selected)return;const row=selected.parentElement!,itemBounds=selected.getBoundingClientRect(),rowBounds=row.getBoundingClientRect();if(itemBounds.left<rowBounds.left)row.scrollLeft+=itemBounds.left-rowBounds.left;else if(itemBounds.right>rowBounds.right)row.scrollLeft+=itemBounds.right-rowBounds.right;},[tab,appFilter]);
  useEffect(()=>{client.onOperation=feedback;const off=client.subscribe(redraw);client.start();return()=>{client.onOperation=undefined;off();client.stop();clearResourceImages(client.base.href);};},[client,feedback]);
  useEffect(()=>{if(client.status==='ended')clearResourceImages(client.base.href);},[client,client.status]);
  useEffect(()=>{setSlow(false);if(!sync.saving)return;const timer=setTimeout(()=>setSlow(true),800);return()=>clearTimeout(timer);},[sync.saving]);
  useLayoutEffect(()=>{const measure=()=>setSizes({header:header.current?.offsetHeight||0,preview:tab===0?preview.current?.offsetHeight||0:0});const observer=new ResizeObserver(measure);if(header.current)observer.observe(header.current);if(preview.current)observer.observe(preview.current);measure();return()=>observer.disconnect();},[tab,!!metadata]);
  useLayoutEffect(()=>{if(scroll.current)scroll.current.scrollTop=positions.current[tab]||0;},[tab]);
  useLayoutEffect(()=>{const carousel=header.current?.querySelector('.main-tab-carousel'),active=carousel?.querySelector('[aria-selected=true]');if(!carousel||!active)return;const indicator=carousel.querySelector<HTMLElement>('.tab-indicator'),area=carousel.getBoundingClientRect(),button=active.getBoundingClientRect();if(indicator){indicator.style.setProperty('--tab-left',`${(active as HTMLElement).offsetLeft}px`);indicator.style.setProperty('--tab-width',`${button.width}px`);indicator.style.setProperty('--tab-ready','1');}if(button.left<area.left)carousel.scrollLeft+=button.left-area.left;else if(button.right>area.right)carousel.scrollLeft+=button.right-area.right;},[tab,!!metadata]);
  useEffect(()=>{if(tab!==2||!client.frame||v.appsLoaded||appsRequested.current)return;appsRequested.current=true;void perform('apps','refresh',true,false);},[tab,client,!!client.frame,v.appsLoaded]);
  useEffect(()=>{setPin('');setNewPin('');setConfirmPin('');if(view?.kind!=='recovery-code')setRecovery('');},[view?.kind,view?.id]);
  useEffect(()=>{
    const read=()=>{
      clearLegacyConnectionKey();
      const params=connectionRoute();
      setRenameLink(mediaRenameTarget(params));setAppRenameLink(appRenameTarget(params));
      setChildLink(params.get('tab')==='child');setFilesLink(params.get('tool')==='files');
    };
    read();window.addEventListener('hashchange',read);return()=>window.removeEventListener('hashchange',read);
  },[]);
  useEffect(()=>{if(!childLink||!metadata||view||upload||closing)return;clearConnectionRoute();changeTab(childTab);setChildLink(false);},[childLink,metadata,view,upload,closing]);
  useEffect(()=>{
    if(!metadata||client.status!=='connected'||upload||closing)return;
    const id=String(v.offeredFile||''),key=id?`${id}:${v.fileOfferVersion}`:'',entry=((v.receivedFiles||[]) as {id:string;name:string}[]).find(item=>item.id===id),offered=!!entry&&key!==consumedFileOffer.current;
    if(!filesLink&&!offered)return;
    if(view&&!['files','file-browser','file-detail'].includes(view.id||'')){setNotice('电视已准备文件，请先关闭当前页面');return;}
    clearConnectionRoute();setFilesLink(false);setTab(toolsTab);viewEpoch.current++;setError('');setNotice('');
    const files:View={kind:'tool',id:'files',title:'文件传输'};
    setTrail(offered?[files,{kind:'tool',id:'file-detail',title:entry!.name,keys:[id,'received']}]:[files]);if(offered)consumedFileOffer.current=key;
  },[filesLink,metadata,client.status,v.offeredFile,v.fileOfferVersion,v.receivedFiles,upload,closing,view]);
  useEffect(()=>{
    if(!renameLink||!metadata||client.status!=='connected'||upload||closing)return;
    if(view){setNotice('请先关闭当前浮窗，再编辑扫码指定的素材');return;}
    const saver=renameLink.source==='screensaver',item=metadata.wallpapers.find(item=>item.id===renameLink.id);
    clearConnectionRoute();setRenameLink(null);setTab(0);viewEpoch.current++;setError('');setNotice('');
    const library:View={kind:'media',title:saver?'屏保素材':'壁纸素材',id:saver?'screensaver':undefined,mediaType:item?.video||item?.kind==='video'?'video':'image'};
    const parent:View={kind:saver?'screensaver':'wallpaper',title:saver?'闲置屏保':'桌面背景'};
    setTrail(item?.canRename?[parent,library,{kind:'media-rename',title:'重命名',id:item.id}]:[parent,library]);
    if(!item?.canRename)setError('素材已移除或不支持改名，请重新选择');
  },[renameLink,metadata,client.status,upload,closing,view]);
  useEffect(()=>{
    if(!appRenameLink||!metadata||client.status!=='connected'||upload||closing||renameApp)return;
    if(!v.appsLoaded){setTab(2);return;}
    if(view){setNotice('请先关闭当前浮窗，再编辑扫码指定的应用');return;}
    const target=(metadata.apps as TvApp[]|undefined)?.find(app=>app.id===appRenameLink);
    clearConnectionRoute();setAppRenameLink(null);setTab(2);viewEpoch.current++;setError('');setNotice('');
    if(target?.canRename){setTrail([{kind:'app',title:target.name,id:target.id}]);setRenameApp(target.id);}else setError('应用已移除或不支持重命名，请重新选择');
  },[appRenameLink,metadata,client.status,upload,closing,renameApp,view,v.appsLoaded]);
  useEffect(()=>{
    if(!updateLink||!metadata||client.status!=='connected')return;
    clearConnectionRoute();setUpdateLink(false);setTab(helpTab);
    setTrail([{kind:'update',title:'软件更新'}]);
  },[updateLink,metadata,client.status]);
  function changeTab(index:number){if(closing||index===tab)return;operationTasks.current={};tabDirection.current=index>tab?1:-1;viewEpoch.current++;setError('');setNotice('');if(scroll.current)positions.current[tab]=scroll.current.scrollTop;setTab(index);}
  function open(next:View,nested=false){if(next.kind==='tool'&&(next.id==='guard'||next.id==='desktop-switch'))next={...next,title:'桌面管理'};if(next.kind==='media'){const item=metadata?.wallpapers.find(item=>item.id===client.value(next.id==='screensaver'?'saverMedia':'wallpaper'));next={...next,mediaType:item?.video||item?.kind==='video'?'video':'image'};}if(next.kind==='child-time')setTimeDraft(childTimeDraft(childTimeRule(childSchedule(next.id!))));if(next.kind==='quiz-settings')setQuizDraftValue(newQuizDraft(client,next.id));if(next.kind==='quiz-questions')setQuizSelected((client.values().selectedCustomIds||[]) as string[]);if(!nested){setRotationCustom(false);setRotationDraft(null);}operationTasks.current={};sheetDirection.current=1;setClosing(false);viewEpoch.current++;if(next.kind==='sync')void client.refresh();(document.activeElement as HTMLElement|null)?.blur();setError('');setNotice('');if(next.kind==='fields'){if(next.keys?.some(key=>key==='homeApps'||key==='appNames'))setPage('home');else if(next.keys?.some(key=>second.includes(key)))setPage('second');else if(next.keys?.some(key=>home.includes(key)||dockFields.includes(key)))setPage('home');}if(next.kind==='memo-edit')setText(drafts.current[next.id!]??next.message??'');if(next.kind==='text')setText(drafts.current[next.id!]??String(client.value(next.id!)||''));setTrail(previous=>nested&&previous.length?[...previous,next]:[next]);}
  function close(){if(closing)return;operationTasks.current={};closeEpoch.current=++viewEpoch.current;setPin('');setNewPin('');setConfirmPin('');setRecovery('');setClosing(true);}
  function finishClose(){if(closeEpoch.current!==viewEpoch.current)return;closeEpoch.current=-1;setTrail([]);setClosing(false);setText('');setError('');setNotice('');}
  function back(){if(closing)return;operationTasks.current={};if(trail.length<=1){close();return;}sheetDirection.current=-1;viewEpoch.current++;setTrail(previous=>previous.slice(0,-1));setError('');setNotice('');}
  function choose(key:string){const field=metadata?.fields[key];if(field&&['child','quiz'].includes(field.domain)&&!client.frame?.domains.parent?.authorized){open({kind:'parent',title:'家长验证',id:client.frame?.domains.parent?.configured?'verify':'setup'},true);return;}if(field)open({kind:'choice',title:field.title,id:key},true);}
  function fields(title:string,keys:string[]){open({kind:'fields',title,keys});}
  async function selectValue(key:string,value:string|number,returnAfterSave=false){
    const field=metadata?.fields[key];if(!field)return false;
    if(key==='appNames')setPage('home');
    if(['child','quiz'].includes(field.domain)&&!client.frame?.domains.parent?.authorized){choose(key);return false;}
    if(key==='wallMode'&&value==='weather'&&!weatherComponent(client).ready){open({kind:'weather-component',title:'动态天气壁纸组件'},true);return false;}
    if(key==='rotation'&&value==='custom'){setRotationCustom(true);return false;}
    if(key==='rotation'){setRotationCustom(false);setRotationDraft(null);}
    if(returnAfterSave){const epoch=viewEpoch.current;if(await perform(field.domain,key,value)&&epoch===viewEpoch.current)back();}
    else client.update(key,value);
    return true;
  }
  function controls(keys:string[],inline=false,hideIcons=false){return keys.filter(name=>name!=='clockStyle'||v.layout==='gallery').map(name=><Setting key={name} client={client} name={name} onSelect={choose} inline={inline} hideIcon={hideIcons} onChoose={selectValue} selection={name==='rotation'&&rotationCustom?'custom':undefined}/>);}
  function inlineControls(keys:string[]){return controls(keys,true);}
  function rotationEditor(){if(!rotationCustom&&metadata?.fields.rotation?.options?.some(option=>option.value===v.rotation))return null;return <form className="inline-number-form" onSubmit={async event=>{event.preventDefault();const epoch=viewEpoch.current;if(await perform(metadata!.fields.rotation.domain,'rotation',Number(rotationDraft??v.rotation))&&epoch===viewEpoch.current){setRotationCustom(false);setRotationDraft(null);if(view?.kind==='choice')back();}}}><label>轮换间隔（分钟）<input aria-label="轮换间隔" type="number" min={1} max={1440} step={1} required disabled={busy} value={rotationDraft??String(v.rotation||15)} onChange={event=>setRotationDraft(event.target.value)}/></label><button className="primary-button" disabled={busy}>应用间隔</button></form>;}
  function label(key:string){const field=metadata?.fields[key],value=client.value(key);return field?.options?.find(option=>option.value===value)?.title??String(value??'');}
  function settingText(key:string,value:unknown){const field=metadata?.fields[key];if(Array.isArray(value)){const catalog=[...((metadata?.quickCatalog||[]) as {id:string;title:string}[]),...((metadata?.homeButtonCatalog||[]) as {id:string;title:string}[])];return value.map(id=>catalog.find(item=>item.id===id)?.title||homeApps.find(app=>app.id===id)?.name||String(id)).join('、')||'空';}if(typeof value==='boolean')return value?'开启':'关闭';if(value===undefined||value===null)return '未读取';return field?.options?.find(option=>option.value===value)?.title||`${typeof value==='number'?Number((value/(field?.divisor||1)).toFixed(2)):String(value)}${field?.unit||''}`;}
  function actionExists(id:string){return !!(metadata?.actions as Record<string,unknown>|undefined)?.[id];}
  async function perform(domain:string,field:string,value:Value=true,announce=true){if(busy)return false;const epoch=viewEpoch.current,context=feedbackKey();setBusy(true);setError('');setNotice('');setNoticePersistent(false);try{const result=await client.command(domain,field,value,undefined,announce);if(epoch===viewEpoch.current&&typeof result.operation?.taskId==='string')operationTasks.current[context]=[...(operationTasks.current[context]||[]),result.operation.taskId].slice(-16);if(epoch===viewEpoch.current&&result.operation?.state==='waitingTelevision'&&typeof result.operation.message==='string'){setNotice(result.operation.message);setNoticePersistent(true);}if(domain==='memos'&&field==='remove'){delete drafts.current[String(value)];if(memoDraftId.current===value)memoDraftId.current='';}if(client.status!=='ended')await client.refresh();return true;}catch(error){if(epoch===viewEpoch.current&&(error instanceof ApiError&&error.status<500||!client.problem))setError(error instanceof Error?error.message:'操作未确认');return false;}finally{setBusy(false);}}
  async function refreshDesktop(){if(desktopRefreshing)return;setDesktopRefreshing(true);setError('');try{await client.refresh();if(client.status==='connected'){clearResourceImages(client.base.href);setDesktopPreviewVersion(value=>value+1);feedback({kind:'success',title:'桌面配置已刷新'});}else feedback({kind:'error',title:'刷新失败',detail:client.problem||'请重新连接电视后重试'});}catch(error){const detail=error instanceof Error?error.message:'刷新未完成，请重试';setError(detail);feedback({kind:'error',title:'刷新失败',detail});}finally{setDesktopRefreshing(false);}}
  function syncFeedback(){
    if(view?.kind==='sync'||!reviewSync&&(!sync.saving||!slow))return null;
    return <div className="sync-banner" role="status">
      <Icon name="ExclamationTriangleIcon"/>
      <div>{reviewSync?syncMessages.map(message=><div key={message}>{message}</div>):'正在等待电视确认…'}</div>
      {reviewSync&&<button className="text-action" onClick={()=>open({kind:'sync',title:'核对设置'},true)}>核对</button>}
    </div>;
  }
  const homeApps=(metadata?.homeApps||[]) as TvApp[],allApps=(metadata?.apps||metadata?.homeApps||[]) as TvApp[];
  const iconCatalog=metadata?.iconLibrary as IconCatalog|undefined;
  const managedCovers=new Map(iconCatalog?.targets.map(app=>[app.id,app.cover]));
  function managedAppImage(app:TvApp){
    const cover=managedCovers.get(app.id);
    return <AppImage app={cover?{...app,icon:cover}:app} client={client}/>;
  }

  function pickFile(kind:string,target='',fromFiles=false){if(upload)return;fileIntent.current={kind,target};fileInput.current!.multiple=kind==='wallpaper';fileInput.current!.accept=kind==='wallpaper'?(target==='wall'?staticWallpaperAccept:fromFiles?'*/*':wallpaperAccept):kind==='file'?'*/*':kind==='backup'?'.json,.zip,application/json,application/zip':'image/*';fileInput.current!.click();}
  async function sendFile(apply:boolean,prepared?:File){if(!file||upload)return false;const epoch=viewEpoch.current,context=feedbackKey(),selected=prepared||file,kind=fileIntent.current.kind,resultLabel=kind==='icon'?'更换图标':kind==='file'?'发送文件':'上传素材',applyNow=['file','backup'].includes(kind)?false:apply;setError('');setUpload({name:selected.name,percent:0,message:'等待电视接收',context});try{const task=await client.upload(selected,kind,applyNow,fileIntent.current.target,(percent,message)=>setUpload({name:selected.name,percent,message,context}),cancel=>{cancelUpload.current=cancel;});setFile(null);feedback(taskNotice(resultLabel,task)||{kind:'info',title:'上传结果待核对',detail:task.message});if(epoch===viewEpoch.current){if(taskHasAction(task)||taskNeedsAttention(task))operationTasks.current[context]=[task.id];else if(kind==='icon'&&trail.at(-2)?.kind==='app-icons'){sheetDirection.current=-1;viewEpoch.current++;setTrail(previous=>previous.slice(0,-2));}else back();}return true;}catch(error){const detail=error instanceof Error?error.message:'上传结果待核对';if(epoch===viewEpoch.current)setError(detail);feedback(error instanceof UploadError?taskNotice(resultLabel,error.task)||{kind:'info',title:'上传结果待核对',detail}:{kind:error instanceof ApiError&&error.status<500?'error':'info',title:error instanceof ApiError&&error.status<500?(kind==='icon'?'更换图标失败':'上传失败'):'上传结果待核对',detail});return false;}finally{setUpload(null);cancelUpload.current=null;}}
  function feedbackKey(){return view?`${view.kind}:${view.id||''}:${(view.keys||[]).join(',')}`:`tab:${tab}`;}
  function taskDomains():string[]{
    if(!view)return [];
    if(['image-upload','upload-options'].includes(view.kind))return [uploadDomain()];
    if(view.kind==='greeting')return ['greeting'];
    if(['screensaver','saver-preview'].includes(view.kind))return ['screensaver'];
    if(['wallpaper','media','daily','rotation-number'].includes(view.kind))return [view.id==='screensaver'?'screensaver':'wallpaper'];
    if((view.kind==='tool'&&(view.id==='desktop-switch'||view.id==='guard'))||(view.kind==='tool-confirm'&&view.id==='desktopSwitch'))return ['desktopSwitch'];
    if((view.kind==='tool'&&(view.id==='inputs'||view.id?.startsWith('input-')))||(view.kind==='tool-confirm'&&view.id==='inputs'))return ['inputs'];
    if(view.kind==='tool'&&view.id==='boot-ads')return ['bootAds'];
    if(view.kind==='tool-confirm'&&view.id==='backup')return ['backup'];
    if(view.kind==='tool')return view.id==='backup'||view.id?.startsWith('backup-')?['backup']:['files','file-browser','file-detail'].includes(view.id||'')?['files']:(view.id==='diagnostics'||view.id?.startsWith('diagnostic-'))?['diagnostics']:[];
    return [];
  }
  function uploadDomain(){const kind=fileIntent.current.kind;return kind==='icon'?'icons':kind==='cover'?'apps':kind==='avatar'?'greeting':kind==='backup'?'backup':kind==='file'?'files':fileIntent.current.target==='screensaver'?'screensaver':'wallpaper';}
  function taskFeedback(){return <>{client.restoring&&<div className="transfer-status" role="status"><strong>{client.restoreStatus.message}</strong><span>处理完成前仅可查看</span></div>}{upload&&upload.context===feedbackKey()&&<div className="transfer-status" role="status"><strong>{upload.name}</strong><progress max={100} value={upload.percent}/><span>{upload.message} {upload.percent}%</span><button className="text-action" onClick={()=>void cancelUpload.current?.().catch(error=>setError(error.message))}>取消上传</button></div>}{visibleTasks(client.tasks(),taskDomains(),dismissedTasks,!!upload,operationTasks.current[feedbackKey()]).filter(task=>{if(task.domain==='diagnostics'&&view?.kind==='tool'&&(view.id==='diagnostics'||view.id?.startsWith('diagnostic-')))return false;if(task.domain==='backup'&&typeof task.result?.download==='string'&&typeof task.result.backupId==='string'&&view?.kind==='tool'&&(view.id==='backup'||view.id?.startsWith('backup-')))return ['backup-download','backup-detail'].includes(view.id||'')&&task.result.backupId===view.keys?.[0];return taskHasAction(task)||task.message!==error&&task.message!==client.problem&&!(view?.id==='backup'&&task.message===v.backupError);}).map(task=><TaskResult key={task.id} task={task} client={client} uploading={!!upload} onDismiss={dismissTask} onReview={id=>open({kind:'tool',id:'backup-draft',title:'核对导入备份',keys:[id]},true)} onCancel={id=>void perform('uploads','cancel',id)}/>)}</>;}
  function mediaHint(item:Media){const parts=[];if(item.width>0&&item.height>0)parts.push(`${item.width} × ${item.height}`);if(item.bytes>0)parts.push(fileSize(item.bytes));if(item.video&&item.durationMs>0){const seconds=Math.round(item.durationMs/1000);parts.push(`${Math.floor(seconds/60)}:${String(seconds%60).padStart(2,'0')}`);}return parts.join('  ');}
  function desktop(){return <>
    <Group>{controls(['layout'])}</Group>
    <Group title="桌面背景">
      <Row title="切换方式" icon="ImageIcon" value={label('wallMode')||metadata?.wallpapers.find(item=>item.id===v.wallpaper)?.name} navigate onClick={()=>open({kind:'wallpaper',title:'桌面背景'})}/>
      {metadata?.fields.globalGlass&&controls(['globalGlass'])}
      <Row title="首页背景" icon="SunIcon" value={`${label('homeBlurMode')}  ${settingText('homeBlur',v.homeBlur)}`} navigate onClick={()=>fields('首页背景',home)}/>
    </Group>
    <Group title="首页信息与入口">
      {metadata?.fields.showTime?<>
        <Row title="问候语与头像" icon="PersonIcon" value={String(v.greeting||'')} navigate onClick={()=>open({kind:'greeting',title:'问候语与头像'})}/>
        <Row title="时间、日期与天气" icon="InfoCircledIcon" navigate onClick={()=>fields('时间、日期与天气',['showTime',...['clockStyle','showSeconds'].filter(key=>!!metadata?.fields[key]),'showDate',...['showLunarDate'].filter(key=>!!metadata?.fields[key]),'weatherEnabled'])}/>
        <Row title="首页按钮" icon="DashboardIcon" value={(v.homeButtons as string[]||[]).length?`${(v.homeButtons as string[]).length}个按钮`:'未添加'} navigate onClick={()=>open({kind:'buttons',title:'首页按钮'})}/>
        {metadata.fields.quickControlVisibility&&controls(['quickControlVisibility'])}
      </>:<Row title="问候语" value={String(v.greeting||'')} navigate onClick={()=>open({kind:'text',title:'问候语',id:'greeting'})}/>}
    </Group>
    {metadata?.fields.showHomeQuote&&<Group title="每日格言">
      {controls(['showHomeQuote','showScreensaverQuote'])}
      {!!v.showHomeQuote&&!!previewMovie(v)&&<p className="section-note">电影信息显示中</p>}
    </Group>}
    <Group title="应用与 Dock">
      {metadata?.fields.homeApps&&controls(['homeApps','appNames'])}
      {metadata?.fields.iconSize&&<Row title="图标样式" icon="DashboardIcon" value={`${label('iconSize')}  ${label('iconShape')}`} navigate onClick={()=>fields('图标样式',['iconSize','iconShape'])}/>}
      {metadata?.fields.dockScale&&<Row title="Dock 外观" icon="ReaderIcon" value={`${(v.dockApps as string[]||[]).length}个应用`} navigate onClick={()=>open({kind:'dock',title:'Dock 外观'})}/>}
    </Group>
    {metadata?.fields.secondBlur&&<Group title="第二屏">
      {controls(['secondNames'])}
      <Row title="第二屏背景" icon="LayersIcon" value={`模糊${settingText('secondBlur',v.secondBlur)}  亮度${settingText('secondBrightness',v.secondBrightness)}`} navigate onClick={()=>fields('第二屏背景',second)}/>
    </Group>}
    {metadata?.fields.idle&&<Group><Row title="闲置屏保" icon="DesktopIcon" value={label('idle')} navigate onClick={()=>open({kind:'screensaver',title:'闲置屏保'})}/></Group>}
    <Group title="操作反馈">{controls(['keySounds','keySoundTheme'])}</Group>
    </>;}
  function apps(){
    const names=['全部','用户应用','预装应用','隐藏应用','已禁用','可恢复'],jobs=client.tasks().filter(task=>task.domain==='apps'&&task.kind==='refresh'),latest=jobs.at(-1),loading=latest&&['queued','running','ready'].includes(latest.state),failed=latest&&['failed','unknown'].includes(latest.state),loaded=!!v.appsLoaded;
    const filtered=allApps.filter(app=>appFilter===0||appFilter===1&&!app.system&&app.installed!==false||appFilter===2&&app.system||appFilter===3&&app.hidden&&!app.childHidden&&app.installed!==false||appFilter===4&&app.disabled||appFilter===5&&app.recoverable&&app.installed===false);
    return <><div className="app-icon-list-actions"><button type="button" className="text-action" disabled={busy||!loaded||client.status!=='connected'||!!iconCatalog&&!iconCatalog.targets.length} onClick={()=>open({kind:'app-icon-batch',title:'批量换图标'})}><Icon name="GridCheckIcon"/>批量换图标</button></div><div className="filter-row app-filter-bar"><div className="app-filter-tabs" role="tablist" aria-label="应用筛选">{names.map((name,index)=><button key={name} id={`app-filter-${index}`} type="button" role="tab" aria-selected={appFilter===index} aria-controls="app-filter-results" tabIndex={appFilter===index?0:-1} onClick={()=>setAppFilter(index)} onKeyDown={event=>{const next=event.key==='ArrowRight'?(index+1)%names.length:event.key==='ArrowLeft'?(index+names.length-1)%names.length:event.key==='Home'?0:event.key==='End'?names.length-1:-1;if(next<0)return;event.preventDefault();setAppFilter(next);event.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>('[role=tab]')[next]?.focus();}}>{name}</button>)}</div><button className="text-action app-filter-refresh" disabled={busy||!!loading} onClick={()=>void perform('apps','refresh')}>{loading?'读取中…':'刷新'}</button></div><div id="app-filter-results" role="tabpanel" aria-labelledby={`app-filter-${appFilter}`} tabIndex={0}>{loading&&<p className="section-note" role="status">正在读取电视应用{loaded?'，当前显示上次结果':''}</p>}{!loading&&(failed||!!v.appsProblem)&&<p className="form-error" role="alert">{String(v.appsProblem||latest?.message||'应用读取未完成，请重试')}{loaded?'。当前显示上次结果。':''}</p>}{loaded&&<Group>{filtered.length?filtered.map(app=><button className="setting-row app-row" key={app.id} onClick={()=>open({kind:'app',title:app.name,id:app.id})}>{managedAppImage(app)}<span className="app-row-copy"><strong>{app.name}</strong><small>版本 {app.version||'未知'}  安装包 {fileSize(app.apkBytes)}</small><small>{app.installed===false?'可恢复':app.childHidden?'儿童模式隐藏':app.hidden?'已隐藏':app.disabled?'已停用':app.system?'预装应用':'用户应用'}{app.protected?'  受保护':''}{((v.dockApps||[]) as string[]).includes(app.id)?'  首页应用':''}</small></span><Icon name="ChevronRightIcon" className="chevron"/></button>):<div className="empty-state"><Icon name="DashboardIcon"/><p>{appFilter?'没有符合条件的应用':'电视应用列表为空'}</p>{appFilter>0&&<button className="text-action" onClick={()=>setAppFilter(0)}>查看全部应用</button>}</div>}</Group>}{!loaded&&!loading&&<div className="empty-state"><p>{failed||v.appsProblem?'尚未取得应用列表':'应用列表尚未读取'}</p><button className="text-action" disabled={busy} onClick={()=>void perform('apps','refresh')}>重新读取</button></div>}</div></>;
  }
  async function parentRequest(action:string){if(busy)return;if(['setup','change','recover'].includes(action)&&newPin!==confirmPin){setError('两次输入的新密码不一致');feedback({kind:'error',title:'密码设置失败',detail:'两次输入的新密码不一致'});return;}const epoch=viewEpoch.current;setBusy(true);setError('');try{const result=await client.parent(action,pin,newPin);setPin('');setNewPin('');setConfirmPin('');if(epoch!==viewEpoch.current)return;if(result.recovery){setRecovery(result.recovery);setTrail(previous=>[...previous.slice(0,-1),{kind:'recovery-code',title:'保存家长恢复码'}]);}else back();}catch(error){if(epoch===viewEpoch.current)setError(error instanceof Error?error.message:'验证结果待核对');}finally{setBusy(false);}}
  function requireParent(next:()=>void){if(client.frame?.domains.parent?.authorized)next();else open({kind:'parent',title:client.frame?.domains.parent?.configured?'家长验证':'设置家长密码',id:client.frame?.domains.parent?.configured?'verify':'setup'},true);}
  function childSchedule(pkg:string){return ((client.values().rules||{}) as Record<string,Record<string,Value>>)[pkg];}
  function childRules(pkg:string){const rule:Record<string,Value>=((v.rules||{}) as Record<string,Record<string,Value>>)[pkg]||{locked:false,mode:0,minutes:60,repeatDays:127},quizApps=(v.quizApps||{}) as Record<string,boolean>,usage=(client.frame?.domains.childUsage?.usage||{}) as Record<string,Record<string,number>>;const managed=typeof rule.enabled==='boolean'?rule.enabled:!!(rule.hidden||rule.locked||Number(rule.mode)>0||quizApps[pkg]);return <><Group><Row title="对此应用开启儿童模式" icon="PersonIcon"><Switch title="对此应用开启儿童模式" checked={managed} disabled={busy} onChange={()=>requireParent(()=>void childRule(pkg,'enabled',!managed))}/></Row><Row title="锁定应用" icon="LockClosedIcon"><Switch title="锁定应用" checked={!!rule.locked} disabled={busy||!managed} onChange={()=>requireParent(()=>void childRule(pkg,'locked',!rule.locked))}/></Row><Row title="使用时长" icon="ClockIcon" value={Number(rule.mode)===0?'不限制':`${Number(rule.mode)===1?'单次':Number(rule.repeatDays)===127?'循环  每天':Number(rule.repeatDays)===31?'循环  周一至周五':Number(rule.repeatDays)===96?'循环  周六和周日':'循环  自定义星期'} ${rule.minutes} 分钟`} disabled={!managed||!!rule.locked} navigate onClick={()=>requireParent(()=>open({kind:'child-time',title:'使用时长',id:pkg},!!view))}/><Row title="定时答题" icon="ReaderIcon"><Switch title="定时答题" checked={!!quizApps[pkg]} disabled={busy||!managed||!!rule.locked} onChange={()=>requireParent(()=>void perform('quiz','quizApp',{package:pkg,enabled:!quizApps[pkg]}))}/></Row><Row title="答题设置" icon="QuestionMarkCircledIcon" disabled={!managed||!!rule.locked} navigate onClick={()=>requireParent(()=>open({kind:'quiz-settings',title:'答题设置',id:pkg},!!view))}/></Group>{usage[pkg]&&<p className="section-note">今日已用 {Math.floor(Number(usage[pkg]?.dailyUsed||0)/60000)} 分钟{Number(usage[pkg]?.remaining)>=0?`  剩余 ${Math.ceil(usage[pkg].remaining/60000)} 分钟`:''}</p>}</>;}
  function children(){const apps=(metadata?.childApps||[]) as TvApp[],app=apps.find(app=>app.id===selectedChild)||apps[0],parent=client.frame?.domains.parent,usage=client.frame?.domains.childUsage,current=String(usage?.currentApp||''),watching=apps.find(item=>item.id===current),quiz=(usage?.quizStatus||{}) as Record<string,Value>;const quizMessage:Record<string,string>={accepted:'正在等待电视显示',displayed:'电视正在答题',passed:'本轮已通过',parentSkipped:'家长已解除本轮'};return <><Group><Row title="儿童模式" icon="PersonIcon" hint="统一启停所有儿童规则"><Switch title="儿童模式" checked={!!v.childEnabled} onChange={()=>requireParent(()=>client.update('childEnabled',!v.childEnabled))}/></Row></Group><Group><Row title={current?`正在观看  ${watching?.name||'当前应用'}`:'当前没有观看应用'} icon="DesktopIcon"/>{quizMessage[String(quiz.state)]&&<p className="section-note" role="status">最近答题  {quizMessage[String(quiz.state)]}</p>}<div className="quiz-now-action"><button className="primary-button" disabled={busy||!v.childEnabled||!current||usage?.timerState!=='READY'} onClick={()=>requireParent(()=>void perform('child','quizNow',{requestId:newId()}))}>立即答题</button></div></Group><div className="section-heading"><h2>应用使用规则</h2></div>{app?<><button className="child-app-picker" onClick={()=>open({kind:'child-apps',title:'选择应用'})}><AppImage app={app} client={client}/><strong>{app.name}</strong><Icon name="ChevronDownIcon"/></button>{childRules(app.id)}</>:<div className="empty-state">电视未提供可设置规则的应用</div>}<Group title="统一设置"><Row title="默认答题设置" icon="QuestionMarkCircledIcon" value={`每 ${v.quizInterval} 分钟`} navigate onClick={()=>requireParent(()=>open({kind:'quiz-settings',title:'答题设置'}))}/><Row title="休息屏保" icon="ImageIcon" value={label('restTheme')} navigate onClick={()=>requireParent(()=>open({kind:'child-rest',title:'休息屏保'}))}/><Row title="家长密码" icon="LockClosedIcon" value={parent?.configured?'已设置':'未设置'} navigate onClick={()=>open({kind:'parent-menu',title:'家长密码'})}/><Row title="计时服务" icon="ClockIcon" value={timerSummary(usage?.timerState)} navigate onClick={()=>open({kind:'timer',title:'计时服务'})}/></Group></>;}

  async function childRule(pkg:string,field:string,value:Value){return perform('child','rule',{package:pkg,field,value});}

  function moveList(key:string,id:string,direction:number){
    const source=key==='quickControls'?quickIds():((client.value(key)||[]) as string[]),range=key==='quickControls'&&isQuickRange(id),ids=key==='quickControls'?(range?quickRanges(source):quickButtons(source)):[...source],index=ids.indexOf(id),next=index+direction;
    if(index<0||next<0||next>=ids.length)return;[ids[index],ids[next]]=[ids[next],ids[index]];if(key==='quickControls')updateQuick(range?[...quickButtons(source),...ids]:[...ids,...quickRanges(source)]);else client.update(key,ids);
  }
  function runQuick(id:string){
    if(id==='saverSound'){
      const field=metadata?.fields.saverSound;
      if(!field)return;
      if(field.disabledReason)open({kind:'info',title:field.title,message:field.disabledReason});
      else client.update('saverSound',client.value('saverSound')!==true);
      return;
    }
    if(id===QUICK_SETTINGS)changeTab(0);else if(id==='keySounds')fields('按键音',['keySounds','keySoundTheme']);else if(id==='brightness')fields('背景明暗',['homeBrightness']);else if(id==='homeBlur')fields('首页背景模糊度',['homeBlur']);else if(id==='dockScale')fields('Dock 栏大小',['dockScale']);else if(id==='dockMagnification')fields('Dock 聚焦放大幅度',['dockMagnification']);else if(id==='mute'){if(metadata?.fields.wallSound)fields('壁纸声音',['wallSound']);else open({kind:'info',title:'壁纸声音',message:'当前图片背景不播放声音。选择视频壁纸后可以设置背景声音。'});}else if(id==='clock')fields('时钟',['showTime',...['clockStyle','showSeconds','showDate','showLunarDate'].filter(key=>!!metadata?.fields[key])]);else if(id==='weather')open({kind:'tool',id:'weather',title:'天气设置'});else if(id==='wallpaper')open({kind:'wallpaper',title:'桌面背景'});else if(id==='apps')changeTab(2);else if(id==='child')changeTab(childTab);else if(id==='volume')fields('音量',['volume']);else if(id==='boot')open({kind:'tool',title:'开机直达',id:'boot'});else if(id==='source'){const inputs=client.frame?.domains.inputs||{},watch=inputs.watch as Record<string,unknown>|undefined,configuration=inputs.configuration as Record<string,unknown>|undefined;if(watch?.targetPresent||configuration?.entry)void perform('quick','run','source');else open({kind:'tool',title:'信号源配置',id:'inputs'});}else if(id==='transfer')open({kind:'tool',title:'文件传输',id:'files'});else if(id==='memoShortcut')open({kind:'tool',title:'备忘录',id:'memos'});else if(actionExists('quick.run'))void perform('quick','run',id);
  }
  function quickIds(){return quickOrder(quickDraft?.ids??((v.quickControls||[]) as string[]));}
  function updateQuick(ids:string[],revision?:string){
    if(quickWaiting)return;const next=quickOrder(ids);setQuickError('');
    if(quickDraft||quickButtonCount(quickServer)>8){const draft=quickDraft?{...quickDraft,ids:next,submitted:false}:{ids:next,revision:revision||quickRevision,generation:quickGeneration,submitted:false};setQuickDraft(draft);if(quickButtonCount(next)<=8)void saveQuickDraft(draft);}
    else client.update('quickControls',next,0,revision);
  }
  async function saveQuickDraft(draft=quickDraft){
    if(!draft||quickDraftConflict(draft,quickRevision,quickGeneration)||quickWaiting||!quickChangeAllowed(quickServer,draft.ids))return;
    const submitted={...draft,submitted:true};setQuickDraft(submitted);setQuickSaving(true);setQuickError('');
    try{await client.command('quick','quickControls',submitted.ids,submitted.revision);setQuickDraft(null);}
    catch(error){setQuickError(error instanceof Error?error.message:'保存未确认，草稿已保留');if(error instanceof ApiError&&error.status<500)setQuickDraft(current=>current?{...current,submitted:false}:null);}
    finally{setQuickSaving(false);}
  }
  function openQuickPicker(range:boolean,id?:string,replace=false){
    const title=replace?(range?'替换调节项':'替换快捷按钮'):(range?'添加调节项':'添加快捷按钮');
    open({kind:replace?'quick-replace':'quick-picker',title,id,quickRange:range,keys:[quickRevision,quickGeneration]},!!id);
  }
  function quickVacancies(range:boolean,count:number){
    const title=range?'添加调节项':'添加按钮';
    return Array.from({length:Math.max(0,count)},(_,index)=>
      <button className="setting-row quick-vacancy" key={index} aria-label={`${title}，空位 ${index+1}`} onClick={()=>openQuickPicker(range)}>
        <Icon name="PlusIcon"/>{title}
      </button>
    );
  }
  function quick(){
    const ids=quickIds(),buttons=quickButtons(ids),ranges=quickRanges(ids),catalog=(metadata?.quickCatalog||[]) as QuickItem[],icons=metadata?.quickIcons as QuickIcons|undefined;
    const actions=(id:string)=>open({kind:'quick-actions',title:catalog.find(item=>item.id===id)?.title||id,id});
    const sound=metadata?.fields.saverSound;
    const status=(id:string)=>id==='saverSound'&&sound?(sound.disabledReason?'需视频屏保':v.saverSound?'已开启':'已关闭'):'';
    return <fieldset className="connection-controls quick-controls-editor" disabled={quickWaiting}>
      <Group title={`已添加（${quickButtonCount(ids)}/${QUICK_BUTTON_LIMIT}）`} action={<button className="text-action" onClick={()=>open({kind:'quick-reset',title:'恢复默认快捷控制？'})}>恢复默认</button>}>
        {quickConflict?<><p className="section-note">电视端配置已变化，手机修改已保留。</p><Row title="使用电视当前配置" onClick={()=>{setQuickDraft(null);setQuickError('');}}/><Row title="应用手机修改" disabled={!quickChangeAllowed(quickServer,ids)} onClick={()=>void saveQuickDraft({...quickDraft!,revision:quickRevision,generation:quickGeneration,submitted:false})}/></>:quickError&&<><p className="form-error" role="alert">{quickError}</p><Row title="重试保存" disabled={!quickDraft||!quickChangeAllowed(quickServer,ids)} onClick={()=>void saveQuickDraft()}/><Row title="取消未保存修改" onClick={()=>{setQuickDraft(null);setQuickError('');}}/></>}
        {buttons.length?<QuickSort order={buttons} revision={quickDraft?.revision||quickRevision} catalog={catalog} icons={icons} onCommit={(order,revision)=>updateQuick([...order,...quickRanges(ids)],revision)} hiddenIds={quickOverflow(ids)} status={status} soundOn={id=>quickSoundOn(id,v)} onRun={runQuick} onActions={actions} onRemove={id=>updateQuick(ids.filter(item=>item!==id))}/>:<p className="empty-state">尚未添加普通按钮</p>}
        {quickVacancies(false,QUICK_BUTTON_LIMIT-quickButtonCount(ids))}
        {quickDraft&&!quickConflict&&!quickError&&quickButtonCount(ids)>8&&<Row title="取消未保存修改" onClick={()=>{setQuickDraft(null);setQuickError('');}}/>}
      </Group>
      <Group title={`快捷调节（${Math.min(ranges.length,QUICK_RANGE_LIMIT)}/${QUICK_RANGE_LIMIT}）`}>
        {ranges.length>QUICK_RANGE_LIMIT&&<p className="section-note">仅前两项在电视显示。旧配置的其他项仍可排序或移除。</p>}
        <QuickSort order={ranges} revision={quickDraft?.revision||quickRevision} catalog={catalog} icons={icons} onCommit={(order,revision)=>updateQuick([...quickButtons(ids),...order],revision)} hiddenIds={ranges.slice(QUICK_RANGE_LIMIT)} onRun={runQuick} onActions={actions} onRemove={id=>updateQuick(ids.filter(item=>item!==id))}/>
        {quickVacancies(true,QUICK_RANGE_LIMIT-ranges.length)}
      </Group>
    </fieldset>;
  }
  async function pairPhone(){
    if(pairingBusy||pairingCode.length!==8)return;
    setPairingBusy(true);setPairingError('');
    try{await client.pair(pairingCode);setPairingCode('');}
    catch(error){setPairingError(error instanceof Error?error.message:'配对未完成，请重试');}
    finally{setPairingBusy(false);}
  }
  async function copyConnection(){try{await copyConnectionAddress();feedback({kind:'success',title:'设置网址已复制'});}catch(error){feedback({kind:'error',title:'复制未完成',detail:error instanceof Error?error.message:'请长按地址栏复制设置网址'});}}
  function general(){return <><Group title="手机连接"><Row title="复制连接网址" icon="Link2Icon" onClick={()=>void copyConnection()}/></Group><Group title="天气"><WeatherSettings client={client} open={open} run={perform} busy={busy}/></Group><Group title="桌面与启动">{controls(['secondHideHomeApps'])}<Row title="桌面管理" icon="HomeIcon" value={String(client.frame?.domains.desktopSwitch?.summary||guardSummary(client.frame?.domains.guard?.code))} navigate onClick={()=>open({kind:'tool',title:'桌面管理',id:'desktop-switch'})}/><Row title="桌面备份与恢复" icon="LayersIcon" value={client.restoring?client.restoreStatus.message:client.restoreStatus.canRetry||v.backupError?'读取待核对':((v.backups||[]) as unknown[]).length?`${((v.backups||[]) as unknown[]).length}份备份`:'尚未备份'} navigate onClick={()=>open({kind:'tool',title:'桌面备份与恢复',id:'backup'})}/><Row title="开机去向" icon="HomeIcon" value={String(v.bootLabel||'QuietTV 首页')} navigate onClick={()=>open({kind:'tool',title:'开机去向',id:'boot'})}/>{actionExists('bootAds.open')&&<Row title="开机广告" icon="DesktopIcon" value={bootAdsLabel(client.frame?.domains.bootAds?.status)} navigate onClick={()=>open({kind:'tool',title:'开机广告',id:'boot-ads'})}/>}</Group><Group title="连接与信号源"><Row title="ADB 管理" icon="Link2Icon" value={adbLabel(client.frame?.domains.management)} navigate onClick={()=>open({kind:'tool',title:'ADB 管理',id:'management'})}/><Row title="信号源配置" icon="DesktopIcon" value={String(v.watchTarget?v.watchLabel||'已配置':(client.frame?.domains.inputs?.configuration as Record<string,unknown>|undefined)?.entry?'原厂信号源菜单':'尚未配置')} navigate onClick={()=>open({kind:'tool',title:'信号源配置',id:'inputs'})}/></Group></>;}
  function tools(){return <Group><Row title="文件传输" icon="FileIcon" value={`${((v.receivedFiles||[]) as unknown[]).length}个文件`} navigate onClick={()=>open({kind:'tool',title:'文件传输',id:'files'})}/><Row title="备忘录" icon="FileTextIcon" value={`${((v.notes||[]) as {done:boolean}[]).filter(note=>!note.done).length}条未完成`} navigate onClick={()=>open({kind:'tool',title:'备忘录',id:'memos'})}/><Row title="Tailscale 组网" icon="Link2Icon" value="在手机助手中使用" navigate onClick={()=>open({kind:'remote-assistant',title:'Tailscale 组网'})}/></Group>;}
  function help(){return <><Group title="软件更新"><Row title="软件更新" value={metadata?.device.version} icon="DownloadIcon" navigate onClick={()=>open({kind:'update',title:'软件更新'})}/></Group><Group title="问题与反馈">{actionExists('diagnostics.export')&&<Row title="诊断日志" icon="FileTextIcon" navigate onClick={()=>open({kind:'tool',title:'诊断日志',id:'diagnostics'})}/>}<Row title="加入交流群" value="提反馈  提建议" icon="PersonIcon" navigate onClick={()=>open({kind:'community',title:'加入交流群'})}/></Group><Group title="关于"><Row title="关于 Quiet TV" value="版本与设备信息" icon="InfoCircledIcon" navigate onClick={()=>open({kind:'about',title:'关于 Quiet TV'})}/></Group></>;}
  function sheet(){if(!view)return null;switch(view.kind){
    case 'tool':case 'tool-confirm':if(view.id==='boot-ads')return <BootAds client={client} run={perform} busy={busy}/>;if(view.id==='desktop-switch'||view.id==='guard')return <DesktopSwitch client={client} open={open} run={perform} busy={busy}/>;return <ToolBody client={client} view={view} open={open} run={perform} busy={busy} drafts={drafts.current} newMemo={()=>{if(!memoDraftId.current)memoDraftId.current=newId();open({kind:'memo-edit',title:'新建备忘录',id:memoDraftId.current,message:''},true);}} pickFile={pickFile} controls={inlineControls} back={back} uploading={!!upload}/>;
    case 'memo-edit':return <form onSubmit={async event=>{event.preventDefault();const epoch=viewEpoch.current;if(await perform('memos','save',{id:view.id!,text})&&epoch===viewEpoch.current){delete drafts.current[view.id!];if(memoDraftId.current===view.id)memoDraftId.current='';back();}}}><textarea aria-label="备忘录内容" required maxLength={60} rows={4} value={text} onChange={event=>{setText(event.target.value);drafts.current[view.id!]=event.target.value;}}/><button className="primary-button" disabled={busy}>应用备忘录</button></form>;
    case 'fields':return controls(view.keys||[],true,view.title==='时间、日期与天气');
    case 'choice':{const field=metadata?.fields[view.id!];if(!field?.options)return null;const value=client.value(view.id!)??'',selected=view.id==='rotation'&&rotationCustom?'custom':field.options.some(option=>option.value===value)?value:field.options.some(option=>option.value==='custom')?'custom':value;return <><ChoiceList title={field.title} options={field.options} value={selected} disabled={busy||!!field.disabledReason} hint={field.disabledReason} onChange={value=>void selectValue(view.id!,value,true)}/>{view.id==='rotation'&&rotationEditor()}</>; }
    case 'text':{const greeting=view.id==='greeting',limit=greeting?8:metadata?.fields[view.id!]?.maxLength||80;return <form onSubmit={async event=>{event.preventDefault();if(greeting&&text.length>limit)return;const epoch=viewEpoch.current,field=metadata?.fields[view.id!];if(field&&await perform(field.domain,view.id!,text)&&epoch===viewEpoch.current){delete drafts.current[view.id!];back();}}}><textarea aria-label={view.title} aria-describedby={greeting?'greeting-count':undefined} rows={3} maxLength={limit} value={text} onChange={event=>{setText(event.target.value);drafts.current[view.id!]=event.target.value;}}/>{greeting&&<p id="greeting-count" className="character-count" role="status">最多 8 个字  {text.length}/8</p>}<button className="primary-button" type="submit" disabled={busy||greeting&&text.length>limit}>{greeting?'应用问候语':view.id==='city'?'使用此城市':'提交'}</button></form>;}
    case 'weather-component':return <WeatherComponent client={client} run={action=>perform('components',action,'weather')}/>;
    case 'remote-assistant':return <OpenAssistant/>;
    case 'wallpaper':return <><Group>{inlineControls(['wallMode'])}<WeatherComponentRow client={client} onClick={()=>open({kind:'weather-component',title:'动态天气壁纸组件'},true)}/>{['fixed','rotation'].includes(String(v.wallMode))&&<Row title="选择壁纸" value={metadata?.wallpapers.find(item=>item.id===v.wallpaper)?.name} navigate onClick={()=>open({kind:'media',title:'壁纸素材'},true)}/>}{v.wallMode==='rotation'&&<>{inlineControls(['rotation'])}{(metadata?.fields.rotation?.options?.length||0)<=3&&rotationEditor()}</>}{metadata?.fields.wallSound&&inlineControls(['wallSound'])}{v.wallMode==='daily'&&<><DailyWallpaperSource client={client} disabled={busy}/>{v.dailySource==='movie-daily'&&inlineControls(['showMovieInfo'])}<Row title="当前图片详情" value={String((v.dailyWallpaper as Record<string,unknown>|undefined)?.title||'')} navigate onClick={()=>open({kind:'daily',title:'当前图片详情'},true)}/></>}</Group>{v.wallMode==='daily'&&<p className="section-note">每天更新，断网保留当前背景。</p>}{v.wallMode==='weather'&&<Group><WeatherSummary client={client}/>{inlineControls(['dayNight'])}<Row title="天气位置" value={String(v.autoLocation?v.locatedCity||'定位待更新':v.city||'尚未选择')} navigate onClick={()=>open({kind:'tool',id:'weather-location',title:'天气位置'},true)}/><Row title="来源与服务" navigate onClick={()=>open({kind:'tool',id:'weather-sources',title:'来源与服务'},true)}/></Group>}</>;
    case 'media':{
      const saver=view.id==='screensaver',type=view.mediaType||'image',types=['image','video'] as const;
      const selectType=(mediaType:'image'|'video')=>setTrail(previous=>previous.map((entry,index)=>index===previous.length-1?{...entry,mediaType}:entry));
      const items=metadata?.wallpapers.filter(item=>item.kind!=='daily'&&item.kind!=='color'&&!(item.id==='yosemite-valley'&&(saver?v.saverBuiltinHidden:v.wallpaperBuiltinHidden))&&((item.video||item.kind==='video')?'video':'image')===type)||[];
      return <><div className="media-tabs" role="tablist" aria-label="壁纸类型">{types.map((value,index)=><button key={value} id={`media-tab-${value}`} type="button" role="tab" aria-selected={type===value} aria-controls="media-results" tabIndex={type===value?0:-1} onClick={()=>selectType(value)} onKeyDown={event=>{const next=event.key==='ArrowRight'||event.key==='ArrowLeft'?1-index:event.key==='Home'?0:event.key==='End'?1:-1;if(next<0)return;event.preventDefault();selectType(types[next]);event.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>('[role=tab]')[next]?.focus();}}>{value==='image'?'静态壁纸':'视频壁纸'}</button>)}</div><div id="media-results" role="tabpanel" aria-labelledby={`media-tab-${type}`} tabIndex={0}><Group>{items.map(item=><div className="media-option" key={item.id}><button className="media-choice" disabled={busy} onClick={async()=>{const epoch=viewEpoch.current;if(await perform(saver?'screensaver':'wallpaper',saver?'saverMedia':'wallpaper',item.id)&&epoch===viewEpoch.current)back();}}>{item.kind!=='color'&&item.bytes>0&&<ResourceImage src={client.resource(`wallpaper/${encodeURIComponent(item.id)}?v=${item.bytes}`)} alt=""/>}<span><strong>{item.name}</strong><small>{mediaHint(item)}</small></span>{(saver?v.saverMedia:v.wallpaper)===item.id&&<Icon name="CheckIcon" className="blue"/>}</button>{item.canRename&&<button className="icon-button" aria-label={`重命名${item.name}`} onClick={()=>open({kind:'media-rename',title:'重命名',id:item.id},true)}><Icon name="Pencil1Icon"/></button>}{item.canRename&&<button className="icon-button danger" disabled={busy} aria-label={`删除${item.name}`} onClick={()=>{setError('');setNotice('');setRemoveMedia(item);}}><Icon name="TrashIcon"/></button>}</div>)}{!items.length&&<p className="empty-state">{type==='image'?'暂无静态壁纸':'暂无视频壁纸'}</p>}</Group></div><button className="primary-button" disabled={!!upload} onClick={()=>pickFile('wallpaper',saver?'screensaver':'')}><Icon name="UploadIcon"/>选择图片或视频（可多选）</button><button className="secondary-button" disabled={!!upload} onClick={()=>pickFile('wallpaper',saver?'screensaver':'',true)}>从文件选择</button></>;
    }
    case 'media-rename':return <MediaRename key={view.id} client={client} id={view.id!} onSaved={back} onCancel={back}/>;
    case 'daily':return <DailyWallpaperDetails client={client} disabled={busy}/>;
    case 'upload-options':return <WallpaperUpload key={uploadSelection} files={wallpaperFiles} client={client} target={fileIntent.current.target} onProgress={progress=>setUpload(progress?{...progress,context:feedbackKey()}:null)} onCancel={cancel=>{cancelUpload.current=cancel;}}/>;
    case 'image-upload':if(!file)return null;if(fileIntent.current.kind==='icon'){
      const [id,shape]=fileIntent.current.target.split('|');
      return <CoverCrop file={file} shape={shape} currentShape={desktopIconShape(client)} busy={!!upload} appName={allApps.find(app=>app.id===id)?.name} onUpload={prepared=>sendFile(true,prepared)}/>;
    }return <><p className="sheet-intro">{file.name}</p><button className="primary-button" disabled={!!upload} onClick={()=>void sendFile(true)}>{fileIntent.current.kind==='file'?'发送到电视':fileIntent.current.kind==='backup'?'上传并校验备份':'上传并应用'}</button></>;
    case 'screensaver':{
      const clock=saverClockLayout(v);
      const wall=v.saverContent==='wall',count=wall?wallpaperWallIds(client).length:0;
      const {columns,rows}=wallpaperWallGrid(count);
      const clockOnly=clock.digital||v.saverContent==='flip-clock';
      const clockFields=['saverAnalogClock',clock.analog?'saverAnalogStyle':'saverDigitalLayout','saverCalendar'];
      if(clock.calendar||clock.largeDate)clockFields.push('saverLunarDate');
      if(!clock.largeDate)clockFields.push('saverSeconds');
      return <><Group>
        {inlineControls(['idle','saverContent'])}
        {clock.digital&&inlineControls(clockFields)}
        {v.saverContent==='flip-clock'&&inlineControls(['saverFlipSeconds','saverFlipSound'])}
        {v.saverContent==='custom'&&<Row title="屏保素材" value={v.saverMedia==='blank'?'空白背景':metadata?.wallpapers.find(item=>item.id===v.saverMedia)?.name} navigate onClick={()=>open({kind:'media',title:'屏保素材',id:'screensaver'},true)}/>}
        {wall&&<Row title="壁纸素材" value={count?`${count} 张  ${columns} × ${rows}`:'上传静态壁纸'} navigate disabled={!!upload} onClick={()=>pickFile('wallpaper','wall')}/>}
        {!clockOnly&&inlineControls(['saverClock'])}
        {v.saverContent!=='flip-clock'&&!clock.composed&&(v.saverClock||clockOnly)&&inlineControls(['saverClockPosition'])}
        {inlineControls(['saverWeather'])}
        {v.saverWeather&&inlineControls(['saverWeatherPosition'])}
        {metadata?.fields.saverSound&&!metadata.fields.saverSound.disabledReason&&inlineControls(['saverSound'])}
      </Group>
      <button className="secondary-button" onClick={()=>open({kind:'saver-preview',title:'屏保预览'},true)}>预览屏保</button>
      {actionExists('quick.run')&&<button className="primary-button" disabled={busy} onClick={()=>void perform('quick','run','screensaver')}>在电视预览屏保</button>}
      </>;
    }
    case 'saver-preview':return <SaverPreview client={client} suspended={!!upload}/>;
    case 'greeting':return <Group>{inlineControls(['showGreeting','showAvatar'])}<Row title="头像" value={v.avatar?'已设置':'未设置'} onClick={()=>pickFile('avatar')}/><Row title="问候语" value={String(v.greeting||'')} navigate onClick={()=>open({kind:'text',title:'问候语',id:'greeting'},true)}/></Group>;
    case 'dock':{const ids=(v.dockApps||[]) as string[];return <>{inlineControls(['dockLimit'])}<Group title="Dock 应用">{ids.map((id,index)=><Row key={id} title={homeApps.find(app=>app.id===id)?.name||id}><button className="icon-button" aria-label="上移" disabled={index===0} onClick={()=>moveList('dockApps',id,-1)}><Icon name="ArrowUpIcon"/></button><button className="icon-button danger" aria-label="从 Dock 移除" onClick={()=>client.update('dockApps',ids.filter(item=>item!==id))}><Icon name="Cross2Icon"/></button></Row>)}<Row title="添加 Dock 应用" disabled={ids.length>=Number(v.dockLimit)} navigate onClick={()=>open({kind:'dock-picker',title:'添加 Dock 应用'},true)}/></Group><Group><Row title="第二屏应用顺序" navigate onClick={()=>open({kind:'grid-order',title:'第二屏应用顺序'},true)}/></Group>{inlineControls(dockFields.filter(key=>key!=='dockMagnification'||v.dockMagnify))}</>;}
    case 'dock-picker':return <Group>{homeApps.filter(app=>!((v.dockApps||[]) as string[]).includes(app.id)).map(app=><Row key={app.id} title={app.name} onClick={()=>{client.update('dockApps',[...((v.dockApps||[]) as string[]),app.id]);back();}}/>)}</Group>;
    case 'grid-order':{const saved=(v.gridOrder||[]) as string[],ids=[...saved.filter(id=>homeApps.some(app=>app.id===id)),...homeApps.map(app=>app.id).filter(id=>!saved.includes(id))];return <Group>{ids.map((id,index)=><Row key={id} title={homeApps.find(app=>app.id===id)?.name||id}><button className="icon-button" aria-label="上移" disabled={index===0} onClick={()=>{const next=[...ids];[next[index-1],next[index]]=[next[index],next[index-1]];client.update('gridOrder',next);}}><Icon name="ArrowUpIcon"/></button><button className="icon-button" aria-label="下移" disabled={index===ids.length-1} onClick={()=>{const next=[...ids];[next[index],next[index+1]]=[next[index+1],next[index]];client.update('gridOrder',next);}}><Icon name="ArrowDownIcon"/></button></Row>)}</Group>;}
    case 'buttons':{const ids=(v.homeButtons||[]) as string[],catalog=(metadata?.homeButtonCatalog||[]) as {id:string;title:string}[];return <Group>{ids.map((id,index)=><Row key={id} title={catalog.find(item=>item.id===id)?.title||id}><button className="icon-button" aria-label="上移" disabled={index===0} onClick={()=>moveList('homeButtons',id,-1)}><Icon name="ArrowUpIcon"/></button><button className="icon-button" aria-label="替换" onClick={()=>open({kind:'button-picker',title:'替换首页按钮',id},true)}><Icon name="DotsHorizontalIcon"/></button><button className="icon-button danger" aria-label="移除" onClick={()=>client.update('homeButtons',ids.filter(item=>item!==id))}><Icon name="Cross2Icon"/></button></Row>)}{ids.length<3&&<Row title="添加首页按钮" navigate onClick={()=>open({kind:'button-picker',title:'添加首页按钮'},true)}/>}</Group>;}
    case 'button-picker':{const ids=(v.homeButtons||[]) as string[],catalog=(metadata?.homeButtonCatalog||[]) as {id:string;title:string}[];return <Group>{catalog.filter(item=>!ids.includes(item.id)).map(item=><Row key={item.id} title={item.title} onClick={()=>{client.update('homeButtons',view.id?ids.map(id=>id===view.id?item.id:id):[...ids,item.id]);back();}}/>)}</Group>;}
    case 'quick-actions':{
      const ids=quickIds(),id=view.id!;
      if(!ids.includes(id))return <p className="section-note">此功能已被移除，请关闭后重新选择。</p>;
      const range=isQuickRange(id),items=range?quickRanges(ids):quickButtons(ids),index=items.indexOf(id);
      const catalog=(metadata?.quickCatalog||[]) as QuickItem[];
      const hasCandidate=(replace='')=>catalog.some(item=>isQuickRange(item.id)===range&&canAddQuick(ids,item.id,replace));
      return <fieldset className="connection-controls" disabled={quickWaiting}><Group>
        <Row title="上移" disabled={index===0} onClick={()=>{moveList('quickControls',id,-1);back();}}/>
        <Row title="下移" disabled={index===items.length-1} onClick={()=>{moveList('quickControls',id,1);back();}}/>
        {hasCandidate()&&<Row title={range?'在此后添加调节项':'在此后添加按钮'} navigate onClick={()=>openQuickPicker(range,id)}/>}
        {id!==QUICK_SETTINGS&&<>
          <Row title={range?'替换调节项':'替换快捷按钮'} disabled={!hasCandidate(id)} navigate onClick={()=>openQuickPicker(range,id,true)}/>
          <Row title="移除" danger onClick={()=>{updateQuick(ids.filter(item=>item!==id));close();}}/>
        </>}
      </Group></fieldset>;
    }
    case 'quick-picker':case 'quick-replace':{
      const ids=quickIds(),anchor=view.id,replace=view.kind==='quick-replace'?anchor:'';
      const catalog=(metadata?.quickCatalog||[]) as QuickItem[],icons=metadata?.quickIcons as QuickIcons|undefined;
      if((anchor&&!ids.includes(anchor))||view.keys?.[0]!==quickRevision||view.keys?.[1]!==quickGeneration)
        return <p className="section-note">快捷控制已变化，请返回后重新选择。</p>;
      const available=catalog.filter(item=>isQuickRange(item.id)===view.quickRange&&canAddQuick(ids,item.id,replace));
      const choose=(item:QuickItem)=>{
        if(quickWaiting)return;
        const next=[...ids],index=anchor?next.indexOf(anchor):next.length-1;
        if(replace)next[index]=item.id;
        else next.splice(index+1,0,item.id);
        updateQuick(next,view.keys?.[0]);
        close();
      };
      return <fieldset className="connection-controls" disabled={quickWaiting}>
        {available.length>0?<Group>{available.map(item=><button className="setting-row" key={item.id} onClick={()=>choose(item)}>
          <QuickIcon glyph={item.glyph||item.id} icons={icons} fallback={quickGlyph(item.id)} className="row-icon"/>
          <span className="row-title">{item.title}</span>
        </button>)}</Group>:<p className="empty-state">{view.quickRange?'暂无可添加的调节项':'暂无可添加的快捷按钮'}</p>}
      </fieldset>;
    }
    case 'quick-reset':return <><p className="sheet-intro">将使用电视端的默认功能和顺序。</p><div className="confirmation-dialog-actions"><button data-confirm-cancel onClick={back}>取消</button><button className="confirm-button" disabled={busy} onClick={()=>void perform('quick','reset').then(saved=>{if(saved){setQuickDraft(null);setQuickError('');}})}>恢复默认</button></div></>;
    case 'app':{const app=allApps.find(app=>app.id===view.id);return <><div className="app-detail-summary">{app&&managedAppImage(app)}<div>{app?.renamed&&<p>原名称：{app.originalName}</p>}<p>版本 {app?.version||'未知'}  安装包 {fileSize(app?.apkBytes)}</p><p>{app?.system?'预装应用':'用户应用'}  {app?.id}</p></div></div>{app?.protection&&<p className="section-note">{app.canRename?'系统操作受保护':app.protection}</p>}<Group>{app?.actions?.filter(action=>action.id!=='restoreCover').map(action=><Row key={action.id} title={action.id==='cover'?'更换图标':applicationActionTitle(action)} danger={action.danger} value={action.reason||undefined} disabled={busy||!!action.reason} onClick={()=>{if(action.id==='rename')setRenameApp(app.id);else if(action.id==='cover')open({kind:'app-icons',title:'更换图标',id:app.id},true);else{setError('');setAppConfirmation({app,action});}}}/>)}</Group></>;}
    case 'app-icons':return <IconLibrary key={view.id} client={client} packageId={view.id} pickFile={pickFile} onSaved={back}/>;
    case 'parent-menu':return <Group><Row title={client.frame?.domains.parent?.configured?'验证家长密码':'设置家长密码'} navigate onClick={()=>open({kind:'parent',title:'家长密码',id:client.frame?.domains.parent?.configured?'verify':'setup'},true)}/>{!!client.frame?.domains.parent?.configured&&<><Row title="修改密码" navigate onClick={()=>requireParent(()=>open({kind:'parent',title:'修改家长密码',id:'change'},true))}/><Row title="查看恢复码" onClick={()=>requireParent(()=>void parentRequest('recovery'))}/><Row title="使用恢复码重设" navigate onClick={()=>open({kind:'parent',title:'恢复家长密码',id:'recover'},true)}/></>}</Group>;
    case 'parent':{const action=view.id||'verify',setting=['setup','change','recover'].includes(action);return <form key={action} autoComplete="off" aria-busy={busy} onSubmit={event=>{event.preventDefault();void parentRequest(action);}}>{['verify','recover'].includes(action)&&<ParentPinInput label={action==='recover'?'恢复码':'家长密码'} value={pin} onChange={setPin}/>} {setting&&<><ParentPinInput label="新密码" value={newPin} onChange={setNewPin} autoComplete="new-password"/><ParentPinInput label="再次输入新密码" value={confirmPin} onChange={setConfirmPin} autoComplete="new-password"/></>}<p className="section-note">{setting?'密码为 6 位数字。':'使用电视上的同一组 6 位数字密码。验证仅对本次连接短时有效。'}</p><button className="primary-button" disabled={busy||Number(client.frame?.domains.parent?.retrySeconds)>0}>{busy?setting?'正在保存…':'正在验证…':setting?'设置密码':'验证'}</button>{Number(client.frame?.domains.parent?.retrySeconds)>0&&<p className="form-error">请等待 {String(client.frame?.domains.parent?.retrySeconds)} 秒后重试</p>}</form>;}
    case 'recovery-code':return <><p className="recovery-code">{recovery}</p><p className="section-note">请由家长单独保存。忘记密码时需要输入此码。</p><button className="primary-button" onClick={()=>{setRecovery('');back();}}>已保存恢复码</button></>;
    case 'timer':return <><p className="sheet-intro">{String(client.frame?.domains.childUsage?.timerMessage||'正在读取计时状态')}</p><button className="primary-button" disabled={busy} onClick={()=>void perform('child','permission')}>在电视打开计时授权</button><button className="secondary-button" onClick={()=>void client.refreshNow()}>重新检查状态</button></>;
    case 'quiz-settings':return quizDraftValue&&<QuizSettingsForm client={client} draft={quizDraftValue} setDraft={setQuizDraftValue} onQuestions={()=>open({kind:'quiz-questions',title:'自定义题目'},true)} onSaved={back}/>;
    case 'quiz-questions':return <QuizQuestions client={client} selected={quizSelected} onSelect={setQuizSelected} onEdit={id=>open({kind:'quiz-edit',title:id?'编辑题目':'新增题目',id},true)} onDeleted={id=>setQuizSelected(previous=>previous.filter(value=>value!==id))} onSaved={back}/>;
    case 'quiz-edit':return <QuizQuestionEditor key={view.id||'new'} client={client} id={view.id||''} onSaved={back}/>;
    case 'child-rest':return <ChildRestPicker client={client} value={Number(v.restTheme)} onChange={value=>void selectValue('restTheme',value)}/>;
    case 'child-apps':return <Group>{((metadata?.childApps||[]) as TvApp[]).map(app=><button className="setting-row child-app-option" key={app.id} onClick={()=>{setSelectedChild(app.id);back();}}><AppImage app={app} client={client}/><span className="row-title">{app.name}</span>{app.id===(selectedChild||((metadata?.childApps||[]) as TvApp[])[0]?.id)&&<Icon name="CheckIcon" className="blue"/>}</button>)}</Group>;
    case 'child-rule':return childRules(view.id!);
    case 'configuration':return <><p className="section-note">以下设置尚未完整读取，请重新读取电视配置后核对。</p><Group>{missingSettings(metadata,v).map(key=><Row key={key} title={fieldPresentation[key]?.title||key} value={metadata?.fields[key]?'当前值未读取':'定义未读取'}/>)}</Group><button className="secondary-button" onClick={()=>void client.refreshNow()}>重新读取配置</button></>;
    case 'child-time':return timeDraft&&<ChildTime draft={timeDraft} change={setTimeDraft}
      current={childTimeRule(childSchedule(view.id!))} blocked={busy}
      authorized={!!client.frame?.domains.parent?.authorized} cancel={back}
      save={rule=>requireParent(async ()=>{
        const epoch=viewEpoch.current;
        if(await childRule(view.id!,'settings',rule)&&epoch===viewEpoch.current)back();
      })}/>;
    case 'child-grant':return <form onSubmit={async event=>{event.preventDefault();const epoch=viewEpoch.current,ok=await perform('child','grant',{package:view.id!,minutes:Number(text)});if(ok&&epoch===viewEpoch.current)back();}}><label>分钟<input type="number" aria-label="分钟" min={1} max={1440} step={1} required value={text} onChange={event=>setText(event.target.value)}/></label><button className="primary-button" disabled={busy}>应用时长</button></form>;
    case 'update':return <UpdateSettings client={client} device={metadata?.device} busy={busy}
      action={(field,value)=>void perform('update',field,value)}
      install={sha=>open({kind:'tool-confirm',id:'update',title:'安装更新？',keys:['install'],
        message:JSON.stringify({value:sha,message:'请在电视上完成系统确认。安装后重新连接手机，即可核对电视版本。'})},true)}/>;
    case 'about':return metadata&&<AboutSettings client={client} metadata={metadata} visible={!closing}
      tab={(view.id as AboutTab|undefined)||'device'}
      select={id=>setTrail(previous=>[...previous.slice(0,-1),{...view,id}])}
      openRecovery={()=>open({kind:'parent-menu',title:'家长密码'},true)}
      openRelease={()=>open({kind:'info',title:'版本说明',message:String(metadata.releaseNotes||'')},true)}
      openLicense={()=>open({kind:'info',title:'MIT 许可证',message:String(metadata.license||'')},true)}/>;
    case 'community':return <><ResourceImage className="community-qr" src={client.resource('assets/qq-group.webp')} alt="QuietTV 交流群二维码"/><p className="sheet-intro community-number">QQ群 1126775126</p><p className="section-note community-number">入群暗号  Quiet是最好的</p></>;
    case 'sync':return <>
      {ended&&<button className="primary-button" onClick={()=>location.reload()}>重新连接</button>}
      {syncMessages.length>0&&<div className="section-note" role="status">{syncMessages.map(message=><div key={message}>{message}</div>)}</div>}
      {sync.drafts.length>0&&<Group>{sync.drafts.map(draft=><div className="sync-difference" key={draft.key} data-draft-state={draft.state}>
        <strong>{metadata?.fields[draft.key]?.title||draft.key}</strong>
        <p>电视当前值  {settingText(draft.key,draft.televisionValue)}</p>
        <p>手机输入  {settingText(draft.key,draft.value)}</p>
        {draft.error&&<p className="form-error">{draft.error}</p>}
        {draft.state==='checking'&&<p>此项正在查找原请求的回执</p>}
      </div>)}</Group>}
      <Group>
        {sync.drafts.length>0&&<Row title="使用电视当前配置" disabled={!sync.canDiscard} onClick={()=>{void client.useTelevision();close();}}/>}
        <Row title={checking?'核对原操作':'重新读取电视配置'} disabled={!sync.canRefresh} onClick={()=>void client.refreshNow()}/>
        {sync.drafts.length>0&&<Row title="重新应用手机输入" disabled={!sync.canReapply} onClick={()=>{void client.retry();back();}}/>}
      </Group>
    </>;
    case 'info':return <p className="sheet-prose">{view.message||'当前版本未提供说明内容'}</p>;
    default:return <p className="section-note">正在读取电视提供的可用操作</p>;
  }}
  if(!metadata)return <main className="quiet-app connect-page">
    <img className="connect-launcher-icon" src={client.resource('assets/quiettv-assistant-launcher.png')} alt=""/>
    <h1>连接 QuietTV</h1>
    {client.status==='pairing'?<form className="phone-pairing" onSubmit={event=>{event.preventDefault();void pairPhone();}}>
      <label htmlFor="phone-pairing-code">输入电视上的 8 位配对码</label>
      <input id="phone-pairing-code" type="text" inputMode="numeric" pattern="[0-9]{8}" maxLength={8} autoComplete="off" required value={pairingCode} disabled={pairingBusy} onChange={event=>{setPairingCode(event.target.value.replace(/[^0-9]/g,''));setPairingError('');}}/>
      <button type="submit" className="primary-button" disabled={pairingBusy||pairingCode.length!==8}>{pairingBusy?'正在配对…':'配对'}</button>
      <p className="section-note">旧收藏打不开时，请从电视重新扫码，配对后收藏新地址。</p>
      {pairingError&&<p className="form-error" role="alert">{pairingError}</p>}
    </form>:<p role="status">{client.problem||'正在连接电视…'}</p>}
    {client.status==='ended'&&<button className="primary-button" onClick={()=>location.reload()}>重新连接</button>}
  </main>;
  return <div ref={root} className="quiet-app" onPointerDownCapture={event=>{event.currentTarget.dataset.inputModality='pointer';}} onKeyDownCapture={event=>{event.currentTarget.dataset.inputModality='keyboard';}} data-ended={ended} data-sync-connection={sync.connection.state} data-sync-receipt={sync.receipt.state} data-restore-state={client.restoreStatus.phase} data-theme={light?'light':'dark'} style={{'--header-height':`${sizes.header}px`,'--preview-height':`${tab===0?sizes.preview:0}px`} as React.CSSProperties}><header ref={header} className="app-header"><div className="device-line"><Icon name="DesktopIcon"/><span>{metadata.device.name}</span><span className="device-version">QuietTV {metadata.device.version}</span><button className={`sync-status ${syncStatus}`} aria-label={`同步状态  ${syncLabels[syncStatus]}`} aria-disabled={!reviewSync} onClick={()=>reviewSync&&open({kind:'sync',title:'核对设置'})}><Icon name={syncStatus==='synced'?'CheckCircledIcon':syncStatus==='saving'?'UpdateIcon':'ExclamationTriangleIcon'}/>{syncLabels[syncStatus]}</button><button className="icon-button theme-button" aria-label="切换外观" onClick={()=>setLight(!light)}><Icon name={light?'MoonIcon':'SunIcon'}/></button></div><div className="main-tab-carousel"><div className="tabs-track"><div role="tablist" aria-label="设置分类"><span className="tab-indicator" aria-hidden="true"/>{tabs.map((title,index)=><button key={title} role="tab" aria-selected={tab===index} aria-controls="setting-page" onClick={()=>changeTab(index)}>{title}</button>)}</div></div></div></header>{<section ref={preview} className="pinned-desktop-preview" hidden={tab!==0} inert={tab!==0}><div className="preview-label"><h2>桌面预览</h2><div className="preview-actions"><button type="button" className="text-action desktop-refresh" aria-label="刷新桌面显示" aria-busy={desktopRefreshing} disabled={desktopRefreshing||busy||sync.saving||checking||!!upload||client.status==='ended'||client.restoring} onClick={()=>void refreshDesktop()}><Icon name="ReloadIcon"/>{desktopRefreshing?'刷新中…':'刷新'}</button><PreviewPagePicker value={page} onChange={setPage}/></div></div><ResourceImageRefresh.Provider value={desktopPreviewVersion}><Preview client={client} page={page} suspended={tab!==0||!!upload}/></ResourceImageRefresh.Provider></section>}<div ref={scroll} className="page-scroll" data-testid="mobile-scroll"><main id="setting-page" className="page-content" role="tabpanel" aria-label={tabs[tab]}>{!view&&<>{syncFeedback()}{missingSettings(metadata,v).length>0&&<button className="sync-banner" onClick={()=>open({kind:'configuration',title:'设置读取不完整'})}><Icon name="ExclamationTriangleIcon"/><span>部分设置尚未完整读取</span><Icon name="ChevronRightIcon"/></button>}{taskFeedback()}{notice&&noticePersistent&&<p className="section-note" role="status">{notice}</p>}{error&&error!==client.problem&&<p className="form-error" role="alert">{error}</p>}</>}<fieldset ref={content} className="connection-controls" disabled={client.status==='ended'||client.restoring}>{tab===0?desktop():tab===1?quick():tab===2?apps():tab===childTab?children():tab===toolsTab?tools():tab===generalTab?general():help()}</fieldset></main></div><input ref={fileInput} type="file" hidden onChange={event=>{const chosen=Array.from(event.target.files||[]),selected=chosen[0];event.target.value='';if(!selected)return;setFile(selected);if(fileIntent.current.kind==='wallpaper'){setWallpaperFiles(chosen);setUploadSelection(value=>value+1);}open({kind:fileIntent.current.kind==='wallpaper'?'upload-options':'image-upload',title:fileIntent.current.kind==='avatar'?'上传头像':fileIntent.current.kind==='icon'?'上传图标':fileIntent.current.kind==='file'?'发送文件':fileIntent.current.kind==='backup'?'导入备份':'上传素材'},true);}}/>{view?.kind==='app-icon-batch'&&<IconLibrary client={client} pickFile={pickFile} batch closing={closing} onSaved={close} onClose={close} onExited={finishClose}/>}
    {view&&view.kind!=='app-icon-batch'&&<Sheet className={view.kind==='app-icons'?'icon-library-sheet':view.kind==='tool'&&view.id==='files'?'file-transfer-sheet':''} footer={view.kind==='tool'&&view.id==='files'?<button className="primary-button" disabled={busy||!!upload||client.status==='ended'||client.restoring} onClick={()=>pickFile('file')}><Icon name="UploadIcon"/>发送到电视</button>:undefined} title={view.kind==='app'?allApps.find(app=>app.id===view.id)?.name||view.title:view.title} nested={trail.length>1} confirmation={view.kind==='tool-confirm'||view.kind==='quick-reset'} closing={closing} pageKey={`${trail.length}:${view.kind}:${view.id||view.title}`} direction={sheetDirection.current} onBack={back} onClose={close} onExited={finishClose}>{syncFeedback()}<fieldset className="connection-controls" disabled={client.status==='ended'||client.restoring}>{sheet()}</fieldset>{taskFeedback()}{notice&&noticePersistent&&view.kind!=='update'&&<p className="section-note" role="status">{notice}</p>}{error&&error!==client.problem&&<p className="form-error" role="alert">{error}</p>}</Sheet>}{appConfirmation&&view?.kind==='app'&&view.id===appConfirmation.app.id&&<AppActionConfirmation request={appConfirmation} current={allApps.find(app=>app.id===appConfirmation.app.id)} busy={busy} disabled={client.status==='ended'||client.restoring||checking||offline} error={error} onCancel={()=>{setAppConfirmation(null);setError('');}} onConfirm={async()=>{const {app,action}=appConfirmation;if(['UNINSTALL','systemUninstall'].includes(action.id)&&((client.frame?.domains.apps?.uninstallParentApps||[]) as string[]).includes(app.id)&&!client.frame?.domains.parent?.authorized){requireParent(()=>{});return false;}return perform('apps',action.id,{package:app.id});}} onDone={()=>setAppConfirmation(current=>current===appConfirmation?null:current)}/>}{renameApp&&<AppRename key={renameApp} client={client} id={renameApp} onCancel={()=>setRenameApp(null)} onSaved={()=>setRenameApp(null)}/>}{removeMedia&&<ConfirmDialog title="删除素材？" busy={busy} disabled={client.status==='ended'||client.restoring} onCancel={()=>{setRemoveMedia(null);setError('');}} onConfirm={async()=>{if(busy)return;if(await perform('wallpaper','removeMedia',removeMedia.id))setRemoveMedia(null);}}><p className="confirmation-media-name">{removeMedia.name}</p><p>删除后无法恢复。若正在使用该素材，桌面或屏保会自动切换。</p>{error&&<p className="form-error" role="alert">{error}</p>}</ConfirmDialog>}</div>;
}
