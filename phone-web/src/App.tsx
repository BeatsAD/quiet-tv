import { isPage, lastPage, readSettingsHistory, viewKey, writeSettingsHistory, type View } from './settingsNavigation';
import type { FormDrafts } from './formDraft';
import { MovieWallSettings, MovieWallSourceSettings, movieSourceTitles, movieWall } from './MovieWallSettings';
import { wallpaperWallGrid, wallpaperWallIds } from './WallpaperWall';
import { staticWallpaperAccept } from './media';
import { BootAds, bootAdsLabel } from './BootAds';
import { ChildTime, childTimeDraft, childTimeRule, type ChildTimeDraft } from './ChildTime';
import { UpdateSettings } from './UpdateSettings';
import { saverClockLayout } from './SaverClock';
import { clearConnectionRoute, clearLegacyConnectionKey, connectionRoute, copyConnectionAddress } from './connection';
import { wallpaperAccept } from './media';
import { AdbStatus } from './AdbManagement';
import { useOperationFeedback } from './OperationFeedback';
import { taskNotice } from './operationResults';
import { DesktopSwitch, DesktopManagementStatus } from './DesktopSwitch';
import { TopShelfSupport } from './TopShelfSupport';
import { WeatherComponentRow, WeatherComponent, OpenAssistant, weatherComponent } from './Components';
import { IconLibrary } from './IconLibrary';
import { desktopIconShape, type IconCatalog } from './IconLibraryPicker';
import { MediaRename } from './MediaRename';
import { RotationInterval, rotationLabel } from './RotationInterval';
import { AppRename, appRenameTarget } from './AppRename';
import { mediaRenameTarget } from './media';
import { QuizSettingsForm, QuizQuestions, QuizQuestionEditor, ChildRestPicker, quizDraft as newQuizDraft, type QuizDraft } from './ChildQuiz';
import { WallpaperUpload } from './WallpaperUpload';
import { useCallback, useEffect, useLayoutEffect, useMemo, useReducer, useRef, useState } from 'react';
import { PhoneClient, ApiError, UploadError, type Value, type App as TvApp, type Media, newId } from './api';
import { EmptyState, Group, Icon, Row, Setting, ChoiceList, Sheet, ConfirmDialog, FormDialog, ResourceImageRefresh, fileSize, ResourceImage, Switch, AppImage, quickGlyph, ParentPinInput } from './widgets';
import { Preview, SaverPreview, PreviewPagePicker, dockEntries, dockGeometry } from './preview';
import { clearResourceImages } from './ResourceImageCache';
import { CoverCrop } from './CoverCrop';
import { AppActionConfirmation, applicationActionTitle, type ApplicationConfirmation } from './AppActionConfirmation';
import { QuickSort } from './QuickSort';
import { ManagedAppList } from './ManagedAppList';
import { ManagedAppDetail } from './ManagedAppDetail';
import { AppFilterMenu } from './AppFilterMenu';
import { canAddQuick, QUICK_BUTTON_LIMIT, QUICK_RANGE_LIMIT, quickChangeAllowed, QUICK_SETTINGS, quickButtonCount, quickButtons, quickOverflow, quickRanges, quickOrder, isQuickRange, quickDraftConflict, type QuickDraft } from './quickRules';
import { QuickIcon, quickSoundOn, type QuickIcons, type QuickItem } from './QuickPreview';
import { DailyWallpaperDetails } from './DailyWallpaperDetails';
import { DailyWallpaperSource } from './DailyWallpaperSource';
import { TaskResult } from './TaskResult';
import { ToolBody, WeatherSettings, WeatherSummary } from './Tools';
import { FILE_TRANSFER_TITLE, FileDetail, FileTransfer, fileSource, type FileSource } from './FileTransfer';
import { visibleTasks, taskHasAction, taskNeedsAttention, timerSummary, missingSettings, fieldPresentation, desktopFieldIcons } from './presentation';
import { useContentMotion, usePageMotion, type PageDirection } from './motion';
import { AboutSettings, type AboutTab } from './AboutSettings';
const tabs=['桌面显示','快捷控制','管理应用','儿童模式','工具','通用设置','更新与帮助'];
const childTab=3,toolsTab=4,generalTab=5,helpTab=6;
const desktopCategories=[
  ['home','首屏'],['wallpaper','壁纸'],['apps','菜单栏'],
  ['second','第二屏'],['saver','屏保'],['feedback','操作反馈'],
] as const;
type DesktopCategory=typeof desktopCategories[number][0];
const defaultDesktopCategory=desktopCategories[0][0];

function positionTabIndicator(carousel:HTMLElement|null|undefined){
  const active=carousel?.querySelector<HTMLElement>('[aria-selected=true]');
  const indicator=carousel?.querySelector<HTMLElement>('.tab-indicator');
  if(!carousel||!active||!indicator)return;
  const area=carousel.getBoundingClientRect(),button=active.getBoundingClientRect();
  const start=area.left+carousel.clientLeft,end=start+carousel.clientWidth;
  indicator.style.setProperty('--tab-left',`${active.offsetLeft}px`);
  indicator.style.setProperty('--tab-width',`${button.width}px`);
  indicator.style.setProperty('--tab-ready','1');
  if(button.left<start)carousel.scrollLeft+=button.left-start;
  else if(button.right>end)carousel.scrollLeft+=button.right-end;
}
const home=['homeBlurMode','homeBlur','homeBrightness','homeFade'],second=['secondBlur','secondBrightness','secondFade'];
const dockFields=['dockScale','dockMagnify','dockMagnification','dockBackground'];
const BUILTIN_WALLPAPER='yosemite-valley';
type Theme='system'|'light'|'dark';
const themeLabels:Record<Theme,string>={system:'跟随系统',light:'浅色',dark:'深色'};
const shortForms=new Set(['text']);
const confirmViews=new Set(['quick-reset','tool-confirm','parent-confirm','app-confirm','remove-media']);
const syncLabels={synced:'已同步',saving:'正在保存',checking:'结果待核对',offline:'正在重连',failed:'保存未完成',conflict:'设置有冲突',ended:'连接已结束'};
export function App(){
  const feedback=useOperationFeedback();
  const [theme,setTheme]=useState<Theme>(()=>{
    try{const value=localStorage.getItem('quiettv-phone-theme');if(value==='light'||value==='dark')return value;}catch{}
    return 'system';
  }),[systemLight,setSystemLight]=useState(()=>matchMedia('(prefers-color-scheme: light)').matches);
  useEffect(()=>{
    const preference=matchMedia('(prefers-color-scheme: light)');
    const change=()=>setSystemLight(preference.matches);
    preference.addEventListener('change',change);
    return()=>preference.removeEventListener('change',change);
  },[]);
  const light=theme==='light'||theme==='system'&&systemLight;
  function chooseTheme(value:Theme){
    setTheme(value);
    try{localStorage.setItem('quiettv-phone-theme',value);}catch{}
    back();
  }
  const client=useMemo(()=>new PhoneClient(),[]),[,redraw]=useReducer(n=>n+1,0);
  const [pairingCode,setPairingCode]=useState(''),[pairingBusy,setPairingBusy]=useState(false),[pairingError,setPairingError]=useState('');
  const [quickDraft,setQuickDraft]=useState<QuickDraft|null>(null),[quickSaving,setQuickSaving]=useState(false),[quickError,setQuickError]=useState('');
  const [timeDraft,setTimeDraft]=useState<ChildTimeDraft|null>(null);
  const [updateLink,setUpdateLink]=useState(()=>connectionRoute().get('tool')==='update');
  const [selectedChild,setSelectedChild]=useState(''),appsRequested=useRef(false);
  const [quizDraftValue,setQuizDraftValue]=useState<QuizDraft|null>(null),[quizSelected,setQuizSelected]=useState<string[]>([]);
  const [appFilter,setAppFilter]=useState(0),[appQuery,setAppQuery]=useState(''),[pin,setPin]=useState(''),[newPin,setNewPin]=useState(''),[confirmPin,setConfirmPin]=useState(''),[recovery,setRecovery]=useState('');
  const [removeMedia,setRemoveMedia]=useState<Media|null>(null);
  const [appConfirmation,setAppConfirmation]=useState<ApplicationConfirmation|null>(null);
  const [appRenameLink,setAppRenameLink]=useState(()=>appRenameTarget(connectionRoute()));
  const [childLink,setChildLink]=useState(()=>connectionRoute().get('tab')==='child');
  const [filesLink,setFilesLink]=useState(()=>connectionRoute().get('tool')==='files'),consumedFileOffer=useRef('');
  const [initialHistory]=useState(()=>connectionRoute().size?undefined:readSettingsHistory());
  const [tab,setTab]=useState(childLink?childTab:initialHistory?.tab??0),[page,setPage]=useState('home'),[trail,setTrail]=useState<View[]>(initialHistory?.pages??[]),[text,setText]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false),[notice,setNotice]=useState(''),[noticePersistent,setNoticePersistent]=useState(false),[slow,setSlow]=useState(false);
  const [desktopRefreshing,setDesktopRefreshing]=useState(false),[desktopPreviewVersion,setDesktopPreviewVersion]=useState(0);
  const viewEpoch=useRef(0),memoDraftId=useRef(''),tabDirection=useRef(1),sheetDirection=useRef(1),content=useRef<HTMLFieldSetElement>(null),closeEpoch=useRef(-1);
  const [closing,setClosing]=useState(false);
  const header=useRef<HTMLElement>(null),preview=useRef<HTMLElement>(null),footer=useRef<HTMLElement>(null),root=useRef<HTMLDivElement>(null),scroll=useRef<HTMLDivElement>(null),drafts=useRef<Record<string,string>>({});
  const forms=useRef<FormDrafts>(new Map()), textBaseline=useRef(new Map<string,string>()), pageHeading=useRef<HTMLHeadingElement>(null);
  const nav=useRef({tab,trail,entry:initialHistory}),navId=useRef(initialHistory?.id??0);
  const routes=useRef(new Map<number,{tab:number;trail:View[]}>());
  const locations=useRef(new Map<string,{top:number;focus?:{label:string;tag:string}}>());
  const renderedLocation=useRef(''),restoreLocation=useRef(false), replaceParentRoute=useRef(false);
  const parentContinuation=useRef<{run:()=>void|Promise<boolean>;generation:string|undefined;title?:string;valid?:()=>boolean}|null>(null);
  const [parentConfirmation,setParentConfirmation]=useState<typeof parentContinuation.current>(null);
  const fileInput=useRef<HTMLInputElement>(null),fileIntent=useRef({kind:'wallpaper',target:''}),cancelUpload=useRef<(()=>Promise<void>)|null>(null);
  const [upload,setUpload]=useState<{name:string;percent:number;message:string;context:string}|null>(null),[file,setFile]=useState<File|null>(null),[dismissedTasks,setDismissedTasks]=useState<string[]>([]);
  const [uploadActionsHost,setUploadActionsHost]=useState<HTMLDivElement|null>(null);
  const [pageActionsHost,setPageActionsHost]=useState<HTMLDivElement|null>(null);
  const [movieWallFooterHost,setMovieWallFooterHost]=useState<HTMLDivElement|null>(null);
  const [desktopSelection,setDesktopSelection]=useState<DesktopCategory>(defaultDesktopCategory);
  const [wallpaperFiles,setWallpaperFiles]=useState<File[]>([]),[uploadSelection,setUploadSelection]=useState(0), wallpaperTarget=useRef(''),operationTasks=useRef<Record<string,string[]>>({});
  const [renameLink,setRenameLink]=useState(()=>mediaRenameTarget(connectionRoute()));
  const view=trail.at(-1),sync=client.sync,metadata=client.metadata,v=client.values();
  const desktopTabs=desktopCategories.filter(([id])=>id==='second'?!!metadata?.fields.secondBlur:
    id==='saver'?!!metadata?.fields.idle:id==='feedback'?!!(metadata?.fields.keySounds||metadata?.fields.keySoundTheme):true);
  const desktopCategory=desktopTabs.some(([id])=>id===desktopSelection)?desktopSelection:defaultDesktopCategory;
  const receipt=sync.receipt,checking=receipt.state==='checking',offline=sync.connection.state==='offline',ended=sync.connection.state==='ended';
  const reconnecting=client.reconnecting,canWrite=sync.connection.state==='connected'&&!checking&&!client.restoring;
  const hasConflicts=sync.drafts.some(draft=>draft.state==='conflict'),hasRejections=sync.drafts.some(draft=>draft.state==='rejected');
  const reviewSync=ended||offline||checking||hasConflicts||hasRejections||!!client.endedOperation;
  const syncStatus=reconnecting?'offline':ended?'ended':checking?'checking':offline?'offline':hasConflicts?'conflict':hasRejections||client.endedOperation?'failed':sync.saving?'saving':'synced';
  const syncMessages=reconnecting?['正在连接电视…']:ended?[sync.connection.message||syncLabels.ended]:[
    ...(offline?[sync.connection.message||syncLabels.offline]:[]),
    ...(checking?[`${metadata?.fields[receipt.field]?.title||'上次操作'}结果待核对`]:[]),
    ...(hasConflicts?['有设置已在电视上更改，手机输入已保留']:[]),
    ...(hasRejections?['部分手机输入保存未完成']:[]),
    ...(client.endedOperation?['上次操作结果待核对。请查看电视当前状态。']:[]),
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

  useEffect(()=>{client.onOperation=feedback;const off=client.subscribe(redraw);client.start();return()=>{client.onOperation=undefined;off();client.stop();clearResourceImages(client.base.href);};},[client,feedback]);
  useEffect(()=>{if(client.status==='ended')clearResourceImages(client.base.href);},[client,client.status]);
  useEffect(()=>{setSlow(false);if(!sync.saving)return;const timer=setTimeout(()=>setSlow(true),800);return()=>clearTimeout(timer);},[sync.saving]);
  const pageView=lastPage(trail);
  const showDesktopTabs=tab===0&&!pageView;
  const previewOnPage=tab===0&&(!pageView||['wallpaper','screensaver','desktop-fields'].includes(pageView.kind));
  const saverPreviewOnPage=pageView?pageView.kind==='screensaver':desktopCategory==='saver';
  const pageLocation=`${tab}:${viewKey(pageView)}`;
  useLayoutEffect(()=>{
    const node=root.current;
    if(!node)return;
    const measure=()=>{
      node.style.setProperty('--header-height',`${header.current?.offsetHeight||0}px`);
      node.style.setProperty('--preview-height',`${preview.current?.offsetHeight||0}px`);
      node.style.setProperty('--footer-height',`${footer.current?.offsetHeight||0}px`);
      positionTabIndicator(preview.current?.querySelector<HTMLElement>('.desktop-category-tabs'));
    };
    const observer=new ResizeObserver(measure);
    if(header.current)observer.observe(header.current);
    if(preview.current)observer.observe(preview.current);
    if(footer.current)observer.observe(footer.current);
    measure();
    return()=>observer.disconnect();
  },[pageLocation,!!metadata,uploadActionsHost,pageActionsHost]);
  useLayoutEffect(()=>{
    const node=scroll.current;if(!node)return;
    renderedLocation.current=pageLocation;
    const location=locations.current.get(pageLocation);
    node.scrollTop=location?.top||0;
    if(restoreLocation.current&&location?.focus){
      const {tag,label}=location.focus;
      const candidates=[...node.querySelectorAll<HTMLElement>(tag),...(footer.current?.querySelectorAll<HTMLElement>(tag)??[])];
      const trigger=candidates.find(item=>(item.getAttribute('aria-label')||item.textContent?.trim())===label);
      (trigger||pageHeading.current)?.focus({preventScroll:true});
    }else if(pageView&&view===pageView)pageHeading.current?.focus({preventScroll:true});
    restoreLocation.current=false;
  },[pageLocation,viewKey(view),!!metadata]);
  const animatePage=usePageMotion(root,pageLocation);
  useLayoutEffect(()=>{
    positionTabIndicator(header.current?.querySelector<HTMLElement>('.main-tab-carousel'));
  },[tab,!!metadata,!!pageView]);
  useLayoutEffect(()=>{
    positionTabIndicator(preview.current?.querySelector<HTMLElement>('.desktop-category-tabs'));
  },[showDesktopTabs,desktopCategory,desktopTabs.length,!!metadata]);
  useEffect(()=>{if(tab!==2||!canWrite||!client.frame||v.appsLoaded||appsRequested.current)return;appsRequested.current=true;void perform('apps','refresh',true,false);},[tab,client,!!client.frame,v.appsLoaded,canWrite]);
  const hydrated=useRef(false);
  useEffect(()=>{
    if(!metadata||hydrated.current)return;
    hydrated.current=true;
    if(initialHistory?.pages.length)showRoute(initialHistory.tab,initialHistory.pages);
  },[!!metadata]);
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
  useEffect(()=>{if(!childLink||!metadata||view||upload||closing)return;directRoute(childTab,[]);setChildLink(false);},[childLink,metadata,view,upload,closing]);
  useEffect(()=>{
    if(!metadata||client.status!=='connected'||upload||closing)return;
    const id=String(v.offeredFile||''),key=id?`${id}:${v.fileOfferVersion}`:'',entry=((v.receivedFiles||[]) as {id:string;name:string}[]).find(item=>item.id===id),offered=!!entry&&key!==consumedFileOffer.current;
    if(!filesLink&&!offered)return;
    if(view&&!['files','file-browser','file-detail','file-preview'].includes(view.id||'')){setNotice('电视已准备文件，请先关闭当前页面');return;}
    setFilesLink(false);
    const files:View={kind:'tool',id:'files',title:FILE_TRANSFER_TITLE};
    directRoute(toolsTab,offered?[files,{kind:'tool',id:'file-detail',title:entry!.name,keys:[id,'received']}]:[files]);if(offered)consumedFileOffer.current=key;
  },[filesLink,metadata,client.status,v.offeredFile,v.fileOfferVersion,v.receivedFiles,upload,closing,view]);
  useEffect(()=>{
    if(!renameLink||!metadata||client.status!=='connected'||upload||closing)return;
    if(view){setNotice('请先关闭当前浮窗，再编辑扫码指定的素材');return;}
    const saver=renameLink.source==='screensaver',item=metadata.wallpapers.find(item=>item.id===renameLink.id);
    setRenameLink(null);
    const library:View={kind:'media',title:saver?'屏保素材':'壁纸素材',id:saver?'screensaver':undefined,mediaType:item?.video||item?.kind==='video'?'video':'image'};
    const parent:View={kind:saver?'screensaver':'wallpaper',title:saver?'闲置屏保':'桌面背景'};
    directRoute(0,item?.canRename?[parent,library,{kind:'media-rename',title:'重命名',id:item.id}]:[parent,library]);
    if(!item?.canRename)setError('素材已移除或不支持改名，请重新选择');
  },[renameLink,metadata,client.status,upload,closing,view]);
  useEffect(()=>{
    if(!appRenameLink||!metadata||client.status!=='connected'||upload||closing)return;
    if(!v.appsLoaded){if(nav.current.tab!==2)navigate(2,[],true);return;}
    if(view){setNotice('请先关闭当前浮窗，再编辑扫码指定的应用');return;}
    const target=(metadata.apps as TvApp[]|undefined)?.find(app=>app.id===appRenameLink);
    setAppRenameLink(null);
    directRoute(2,target?.canRename?[{kind:'app',title:target.name,id:target.id},{kind:'app-rename',title:'重命名应用',id:target.id}]:[]);if(!target?.canRename)setError('应用已移除或不支持重命名，请重新选择');
  },[appRenameLink,metadata,client.status,upload,closing,view,v.appsLoaded]);
  useEffect(()=>{
    if(!updateLink||!metadata||client.status!=='connected'||view)return;
    setUpdateLink(false);directRoute(helpTab,[{kind:'update',title:'软件更新'}]);
  },[updateLink,metadata,client.status,view]);
  function televisionText(item:View){
    if(item.kind==='memo-edit'){
      return ((client.values().notes||[]) as {id:string;text:string}[]).find(note=>note.id===item.id)?.text||'';
    }
    return String(client.value(item.id!)||'');
  }
  function clearText(item:View){
    const id=item.id!;
    delete drafts.current[id];
    textBaseline.current.delete(id);
    if(memoDraftId.current===id)memoDraftId.current='';
  }
  function textEditor(item:View){
    const memo=item.kind==='memo-edit',greeting=item.id==='greeting',id=item.id!;
    const limit=memo?60:greeting?8:metadata?.fields[id]?.maxLength||80;
    const original=textBaseline.current.get(id),current=televisionText(item);
    const changed=original!==undefined&&original!==current,field=metadata?.fields[id];
    const label=memo?'备忘录内容':item.title;
    return <form onSubmit={async event=>{
      event.preventDefault();
      if(!canWrite||changed||text.length>limit)return;
      const epoch=viewEpoch.current;
      const saved=memo?await perform('memos','save',{id,text}):field&&await perform(field.domain,id,text);
      if(saved){clearText(item);if(epoch===viewEpoch.current)back();}
    }}>
      <label className="quiet-sr-only" htmlFor="edit-text">{label}</label>
      <textarea id="edit-text" aria-describedby={greeting?'greeting-count':undefined} rows={memo?4:3} required={memo}
        maxLength={limit} value={text} onChange={event=>{setText(event.target.value);drafts.current[id]=event.target.value;}}/>
      {greeting&&<p id="greeting-count" className="character-count">{text.length}/{limit}</p>}
      {changed&&<div className="form-error" role="status">
        <p>电视当前内容  {current||'空'}</p>
        <button type="button" className="text-action" onClick={()=>{textBaseline.current.set(id,current);redraw();}}>已核对，保留手机输入</button>
      </div>}
      <div className="confirmation-dialog-actions">
        <button type="button" disabled={busy} onClick={()=>{clearText(item);back();}}>取消</button>
        <button className="confirm-button" type="submit" disabled={busy||!canWrite||changed||text.length>limit}>保存</button>
      </div>
    </form>;
  }
  function rememberLocation(){
    const {tab,trail}=nav.current,node=scroll.current;
    const key=`${tab}:${viewKey(lastPage(trail))}`;
    if(!node||renderedLocation.current!==key)return;
    const active=document.activeElement;
    if(active instanceof HTMLElement&&(node.contains(active)||footer.current?.contains(active))){
      locations.current.set(key,{top:node.scrollTop,focus:{tag:active.tagName,label:active.getAttribute('aria-label')||active.textContent?.trim()||''}});
    }else locations.current.set(key,{...locations.current.get(key),top:node.scrollTop});
  }
  function showRoute(index:number,next:View[],direction:PageDirection|null=null){
    const previousPage=lastPage(nav.current.trail),nextPage=lastPage(next);
    const pageChanged=nav.current.tab===index&&viewKey(previousPage)!==viewKey(nextPage)&&
      !(previousPage?.kind==='about'&&nextPage?.kind==='about');
    tabDirection.current=index>=nav.current.tab?1:-1;
    nav.current={...nav.current,tab:index,trail:next};
    const epoch=++viewEpoch.current;
    animatePage(pageChanged?direction:null,()=>{
      if(epoch!==viewEpoch.current)return;
      setClosing(false);setError('');setNotice('');
      const target=next.at(-1);
      if(target?.kind==='child-time')setTimeDraft((forms.current.get('time:'+target.id) as ChildTimeDraft|undefined)??childTimeDraft(childTimeRule(childSchedule(target.id!))));
      if(target?.kind==='quiz-settings')setQuizDraftValue((forms.current.get('quiz:'+String(target.id||'')) as QuizDraft|undefined)??newQuizDraft(client,target.id));
      if(target?.kind==='quiz-questions')setQuizSelected((forms.current.get('quiz-selection') as string[]|undefined)??((client.values().selectedCustomIds||[]) as string[]));
      if(target?.kind==='memo-edit'||target?.kind==='text'){
        const baseline=televisionText(target),id=target.id!;
        if(drafts.current[id]===undefined)textBaseline.current.set(id,baseline);
        setText(drafts.current[id]??baseline);
      }
      setTab(index);setTrail(next);
    });
  }
  function navigate(index:number,next:View[],replace=false,direction:PageDirection|null='push'){
    rememberLocation();
    const current=nav.current.entry;
    const id=replace&&current?current.id:++navId.current;
    nav.current.entry=writeSettingsHistory(id,replace?current?.parent:current?.id,index,next,replace||!current);
    routes.current.set(id,{tab:index,trail:next});
    showRoute(index,next,direction);
  }
  function directRoute(index:number,next:View[]){
    clearConnectionRoute();
    navigate(index,[],true,null);
    for(let i=1;i<=next.length;i++)navigate(index,next.slice(0,i),false,null);
  }
  useEffect(()=>{
    if(!nav.current.entry){
      nav.current.entry=writeSettingsHistory(++navId.current,undefined,nav.current.tab,nav.current.trail,true);
      routes.current.set(nav.current.entry.id,{tab:nav.current.tab,trail:nav.current.trail});
    }
    navId.current=Math.max(navId.current,Date.now());
    function restoreRoute(event:PopStateEvent){
      const entry=readSettingsHistory(event.state);if(!entry)return;
      rememberLocation();
      const old=nav.current.entry;
      if(old){
        const route=routes.current.get(old.id);
        if(route&&route.trail.some(item=>!isPage(item)))routes.current.delete(old.id);
      }
      const route=routes.current.get(entry.id), next=route?.trail??entry.pages;
      parentContinuation.current=null;setParentConfirmation(null);
      if(next.at(-1)?.kind!=='app-confirm')setAppConfirmation(null);
      if(next.at(-1)?.kind!=='remove-media')setRemoveMedia(null);
      setPin('');setNewPin('');setConfirmPin('');setRecovery('');
      nav.current.entry=entry;
      navId.current=Math.max(navId.current,entry.id);
      restoreLocation.current=true;sheetDirection.current=-1;
      const direction=event.hasUAVisualTransition||!old||entry.id===old.id?null:entry.id<old.id?'pop':'push';
      showRoute(entry.tab,next,direction);
    }
    window.addEventListener('popstate',restoreRoute);
    return()=>window.removeEventListener('popstate',restoreRoute);
  },[]);
  function changeTab(index:number){
    if(closing||index===nav.current.tab&&!nav.current.trail.length)return;
    navigate(index,[]);
  }
  function selectDesktopCategory(category:DesktopCategory){
    if(category===desktopCategory)return;
    setDesktopSelection(category);
    setPage(category==='second'?'second':'home');
    if(scroll.current)scroll.current.scrollTop=0;
  }
  function open(next:View,nested=false){
    if(next.kind==='tool'&&(next.id==='guard'||next.id==='desktop-switch'))next={...next,title:'桌面管理'};
    if(next.kind==='media'){const item=metadata?.wallpapers.find(item=>item.id===client.value(next.id==='screensaver'?'saverMedia':'wallpaper'));next={...next,mediaType:item?.video||item?.kind==='video'?'video':'image'};}
    if(next.kind==='sync')void client.refresh();
    if(next.kind==='fields'||next.kind==='desktop-fields'){if(next.keys?.some(key=>second.includes(key)))setPage('second');else if(next.keys?.some(key=>key==='homeApps'||key==='appNames'||home.includes(key)||dockFields.includes(key)))setPage('home');}
    const replaceParent=replaceParentRoute.current;
    const current=replaceParent?nav.current.trail.slice(0,-1):nav.current.trail;
    sheetDirection.current=1;
    navigate(nav.current.tab,nested?[...current,next]:[next],replaceParent||!nested&&current.length>0);
  }
  function back(){
    const {entry,tab,trail}=nav.current;
    if(entry?.parent!==undefined){history.back();return;}
    restoreLocation.current=true;sheetDirection.current=-1;
    navigate(tab,trail.slice(0,-1),true,'pop');
  }
  function close(){
    if(closing)return;
    closeEpoch.current=++viewEpoch.current;setClosing(true);
  }
  function finishClose(){if(closeEpoch.current!==viewEpoch.current)return;closeEpoch.current=-1;setClosing(false);back();}
  function choose(key:string){
    const field=metadata?.fields[key];if(!field)return;
    const show=()=>open({kind:'choice',title:field.title,id:key},true);
    if(['child','quiz'].includes(field.domain))requireParent(show);else show();
  }
  function fields(title:string,keys:string[]){open({kind:title==='首页背景'||title==='第二屏背景'?'desktop-fields':'fields',title,keys},true);}
  async function selectValue(key:string,value:string|number,returnAfterSave=false){
    const field=metadata?.fields[key];if(!field)return false;
    if(key==='appNames')setPage('home');
    if(['child','quiz'].includes(field.domain)&&!client.frame?.domains.parent?.authorized){choose(key);return false;}
    if(key==='wallMode'){
      if(returnAfterSave&&client.value(key)===value){close();return true;}
      if(value==='weather'&&!weatherComponent(client).ready){
        const component:View={kind:'weather-component',title:'动态天气壁纸组件'};
        const current=nav.current;
        if(returnAfterSave)navigate(current.tab,[...current.trail.slice(0,-1),component],true);
        else open(component,true);
        return false;
      }
    }
    if(returnAfterSave){
      const epoch=viewEpoch.current;
      if(await perform(field.domain,key,value)&&epoch===viewEpoch.current){
        if(key==='wallMode')close();
        else back();
      }
    }else client.update(key,value);
    return true;
  }
  function controls(keys:string[],inline=false,titles?:Record<string,string>){
    return keys.filter(name=>name!=='clockStyle'||v.layout==='gallery').map(name=>{
      const maximum=name==='dockScale'?dockGeometry(dockEntries((metadata?.homeApps||[]) as TvApp[],v).length,v).maximum:undefined;
      return <Setting key={name} client={client} name={name} title={titles?.[name]} onSelect={choose} inline={inline}
        onChoose={selectValue} maximum={maximum}/>;
    });
  }
  function inlineControls(keys:string[]){return controls(keys,true);}
  const rotationKey=metadata?.fields.rotationSeconds?'rotationSeconds':'rotation';
  const rotationField=metadata?.fields[rotationKey],rotationValue=v[rotationKey];
  async function applyRotation(value:number){
    if(!rotationField||typeof rotationValue!=='number')return;
    const change=rotationKey==='rotationSeconds'?{seconds:value,expectedSeconds:rotationValue,video:!!v.wallVideo}:value;
    const epoch=viewEpoch.current;
    if(await perform(rotationField.domain,rotationKey,change)&&epoch===viewEpoch.current)back();
  }
  function label(key:string){const field=metadata?.fields[key],value=client.value(key);return field?.options?.find(option=>option.value===value)?.title??String(value??'');}
  function settingText(key:string,value:unknown){const field=metadata?.fields[key];if(Array.isArray(value)){const catalog=[...((metadata?.quickCatalog||[]) as {id:string;title:string}[]),...((metadata?.homeButtonCatalog||[]) as {id:string;title:string}[])];return value.map(id=>catalog.find(item=>item.id===id)?.title||homeApps.find(app=>app.id===id)?.name||String(id)).join('、')||'空';}if(typeof value==='boolean')return value?'开启':'关闭';if(value===undefined||value===null)return '未读取';return field?.options?.find(option=>option.value===value)?.title||`${typeof value==='number'?Number((value/(field?.divisor||1)).toFixed(2)):String(value)}${field?.unit||''}`;}
  function actionExists(id:string){return !!(metadata?.actions as Record<string,unknown>|undefined)?.[id];}
  const performing=useRef(false);
  async function perform(domain:string,field:string,value:Value=true,announce=true){if(performing.current||!canWrite)return false;performing.current=true;const epoch=viewEpoch.current;
    const wallpaperPicker=view?.kind==='choice'&&view.id==='wallMode';
    const context=feedbackKey(view&&(confirmViews.has(view.kind)||wallpaperPicker)?pageView:view);setBusy(true);setError('');setNotice('');setNoticePersistent(false);try{const result=await client.command(domain,field,value,undefined,announce);if(typeof result.operation?.taskId==='string')operationTasks.current[context]=[...(operationTasks.current[context]||[]),result.operation.taskId].slice(-16);if(epoch===viewEpoch.current&&result.operation?.state==='waitingTelevision'&&typeof result.operation.message==='string'){setNotice(result.operation.message);setNoticePersistent(true);}if(domain==='memos'&&field==='remove'){delete drafts.current[String(value)];if(memoDraftId.current===value)memoDraftId.current='';}if(client.status!=='ended')await client.refresh();return true;}catch(error){if(epoch===viewEpoch.current&&(error instanceof ApiError&&error.status<500||!client.problem))setError(error instanceof Error?error.message:'操作未确认');return false;}finally{performing.current=false;setBusy(false);}}
  async function refreshDesktop(){if(desktopRefreshing)return;setDesktopRefreshing(true);setError('');try{await client.refresh();if(client.status==='connected'){clearResourceImages(client.base.href);setDesktopPreviewVersion(value=>value+1);feedback({kind:'success',title:'桌面配置已刷新'});}else feedback({kind:'error',title:'刷新失败',detail:client.problem||'请重新连接电视后重试'});}catch(error){const detail=error instanceof Error?error.message:'刷新未完成，请重试';setError(detail);feedback({kind:'error',title:'刷新失败',detail});}finally{setDesktopRefreshing(false);}}
  function syncFeedback(){
    if(view?.kind==='sync'||!reviewSync&&!reconnecting&&(!sync.saving||!slow))return null;
    return <div className="sync-banner" role="status">
      <Icon name="ExclamationTriangleIcon"/>
      <div>{reviewSync||reconnecting?syncMessages.map(message=><div key={message}>{message}</div>):'正在等待电视确认…'}</div>
      {ended?<button className="text-action" onClick={()=>void client.reconnect()}>{client.restoreReview?'重新连接并查看结果':'重新连接'}</button>:
        reviewSync&&!reconnecting&&<button className="text-action" onClick={()=>open({kind:'sync',title:'核对设置'},true)}>核对</button>}
    </div>;
  }
  const homeApps=(metadata?.homeApps||[]) as TvApp[],allApps=(metadata?.apps||metadata?.homeApps||[]) as TvApp[];
  const iconCatalog=metadata?.iconLibrary as IconCatalog|undefined;
  const managedCovers=new Map(iconCatalog?.targets.map(app=>[app.id,app.cover]));
  function managedAppImage(app:TvApp){
    const cover=managedCovers.get(app.id);
    return <AppImage app={cover?{...app,icon:cover}:app} client={client}/>;
  }

  function pickFile(kind:string,target='',fromFiles=false){if(upload||!canWrite)return;fileIntent.current={kind,target};fileInput.current!.multiple=kind==='wallpaper';fileInput.current!.accept=kind==='wallpaper'?(target==='wall'?staticWallpaperAccept:fromFiles?'*/*':wallpaperAccept):kind==='file'?'*/*':kind==='backup'?'.json,.zip,application/json,application/zip':'image/*';fileInput.current!.click();}
  async function sendFile(apply:boolean,prepared?:File){if(!file||upload)return false;const epoch=viewEpoch.current,context=feedbackKey(view),selected=prepared||file,kind=fileIntent.current.kind,resultLabel=kind==='icon'?'更换图标':kind==='file'?'发送文件':'上传素材',applyNow=['file','backup'].includes(kind)?false:apply;setError('');setUpload({name:selected.name,percent:0,message:'等待电视接收',context});try{const task=await client.upload(selected,kind,applyNow,fileIntent.current.target,(percent,message)=>setUpload({name:selected.name,percent,message,context}),cancel=>{cancelUpload.current=cancel;});setFile(null);feedback(taskNotice(resultLabel,task)||{kind:'info',title:'上传结果待核对',detail:task.message});if(epoch===viewEpoch.current){if(taskHasAction(task)||taskNeedsAttention(task))operationTasks.current[context]=[task.id];else if(kind==='icon'&&trail.at(-2)?.kind==='app-icons'){history.go(-2);}else back();}return true;}catch(error){const detail=error instanceof Error?error.message:'上传结果待核对';if(epoch===viewEpoch.current)setError(detail);feedback(error instanceof UploadError?taskNotice(resultLabel,error.task)||{kind:'info',title:'上传结果待核对',detail}:{kind:error instanceof ApiError&&error.status<500?'error':'info',title:error instanceof ApiError&&error.status<500?(kind==='icon'?'更换图标失败':'上传失败'):'上传结果待核对',detail});return false;}finally{setUpload(null);cancelUpload.current=null;}}
  function feedbackKey(view?:View){return view?`${view.kind}:${view.id||''}:${(view.keys||[]).join(',')}`:`tab:${tab}`;}
  function taskDomains(view:View|undefined):string[]{
    if(view?.kind==='update')return ['update'];
    if(!view)return [];
    if(['image-upload','upload-options'].includes(view.kind))return [uploadDomain()];
    if(view.kind==='greeting')return ['greeting'];
    if(view.kind==='screensaver')return ['screensaver'];
    if(['wallpaper','media','daily','rotation-number'].includes(view.kind))return [view.id==='screensaver'?'screensaver':'wallpaper'];
    if((view.kind==='tool'&&(view.id==='desktop-switch'||view.id==='guard'))||(view.kind==='tool-confirm'&&view.id==='desktopSwitch'))return ['desktopSwitch'];
    if((view.kind==='tool'&&(view.id==='inputs'||view.id?.startsWith('input-')))||(view.kind==='tool-confirm'&&view.id==='inputs'))return ['inputs'];
    if(view.kind==='tool'&&view.id==='boot-ads')return ['bootAds'];
    if(view.kind==='tool-confirm'&&view.id==='backup')return ['backup'];
    if(view.kind==='tool')return view.id==='backup'||view.id?.startsWith('backup-')?['backup']:['files','file-browser','file-detail','file-preview'].includes(view.id||'')?['files']:(view.id==='diagnostics'||view.id?.startsWith('diagnostic-'))?['diagnostics']:[];
    return [];
  }
  function uploadDomain(){const kind=fileIntent.current.kind;return kind==='icon'?'icons':kind==='cover'?'apps':kind==='avatar'?'greeting':kind==='backup'?'backup':kind==='file'?'files':fileIntent.current.target==='screensaver'?'screensaver':'wallpaper';}
  function taskFeedback(view:View|undefined){return <>{client.restoring&&<div className="transfer-status" role="status"><strong>{client.restoreStatus.message}</strong><span>处理完成前仅可查看</span></div>}{upload&&(upload.context===feedbackKey(view)||view?.kind==='tool'&&view.id==='files'&&fileIntent.current.kind==='file')&&<div className="transfer-status" role="status"><strong>{upload.name}</strong><progress max={100} value={upload.percent}/><span>{upload.message} {upload.percent}%</span><button className="text-action" onClick={()=>void cancelUpload.current?.().catch(error=>setError(error.message))}>取消上传</button></div>}{visibleTasks(client.tasks(),taskDomains(view),dismissedTasks,!!upload,operationTasks.current[feedbackKey(view)]).filter(task=>{if(task.domain==='files'&&['browse','permission'].includes(task.kind))return false;if(task.domain==='diagnostics'&&view?.kind==='tool'&&(view.id==='diagnostics'||view.id?.startsWith('diagnostic-')))return false;if(task.domain==='backup'&&typeof task.result?.download==='string'&&typeof task.result.backupId==='string'&&view?.kind==='tool'&&(view.id==='backup'||view.id?.startsWith('backup-')))return ['backup-download','backup-detail'].includes(view.id||'')&&task.result.backupId===view.keys?.[0];return taskHasAction(task)||task.message!==error&&task.message!==client.problem&&!(view?.id==='backup'&&task.message===v.backupError);}).map(task=><TaskResult key={task.id} task={task} client={client} uploading={!!upload} onDismiss={dismissTask} onReview={id=>open({kind:'tool',id:'backup-draft',title:'核对导入备份',keys:[id]},true)} onCancel={id=>void perform('uploads','cancel',id)}/>)}</>;}
  function mediaHint(item:Media){const parts=[];if(item.width>0&&item.height>0)parts.push(`${item.width} × ${item.height}`);if(item.bytes>0)parts.push(fileSize(item.bytes));if(item.video&&item.durationMs>0){const seconds=Math.round(item.durationMs/1000);parts.push(`${Math.floor(seconds/60)}:${String(seconds%60).padStart(2,'0')}`);}return parts.join('  ');}
  function screensaverSettings(){
    const clock=saverClockLayout(v);
    const wall=v.saverContent==='wall',count=wall?wallpaperWallIds(client).length:0;
    const movies=movieWall(client);
    const {columns,rows}=wallpaperWallGrid(count);
    const clockOnly=clock.digital||v.saverContent==='flip-clock';
    const clockFields=['saverAnalogClock',clock.analog?'saverAnalogStyle':'saverDigitalLayout','saverCalendar'];
    if(clock.calendar||clock.largeDate)clockFields.push('saverLunarDate');
    if(!clock.largeDate)clockFields.push('saverSeconds');
    return <><Group>
      {inlineControls(['idle','saverContent'])}
      {clock.digital&&inlineControls(clockFields)}
      {v.saverContent==='flip-clock'&&inlineControls(['saverFlipSeconds','saverFlipSound'])}
      {v.saverContent==='custom'&&<Row title="屏保素材" icon="ImageIcon" value={v.saverMedia==='blank'?'空白背景':metadata?.wallpapers.find(item=>item.id===v.saverMedia)?.name} navigate onClick={()=>open({kind:'media',title:'屏保素材',id:'screensaver'},true)}/>}
      {v.saverContent==='movie-wall'&&<Row title="电影海报来源" icon="ImageIcon"
        value={movies.busy?'正在同步':movies.ids.length?`已缓存 ${movies.ids.length} 张`:movies.connected?'选择媒体库':'选择来源'}
        navigate onClick={()=>open({kind:'movie-wall-source',title:'电影海报来源'},true)}/>}
      {wall&&<Row title="壁纸素材" icon="ImageIcon" value={count?`${count} 张  ${columns} × ${rows}`:'上传静态壁纸'} navigate disabled={!!upload} onClick={()=>pickFile('wallpaper','wall')}/>}
      {!clockOnly&&inlineControls(['saverClock'])}
      {v.saverContent!=='flip-clock'&&!clock.composed&&(v.saverClock||clockOnly)&&inlineControls(['saverClockPosition'])}
      {inlineControls(['saverWeather'])}
      {v.saverWeather&&inlineControls(['saverWeatherPosition'])}
      {metadata?.fields.saverSound&&!metadata.fields.saverSound.disabledReason&&inlineControls(['saverSound'])}
      {!clock.digital&&metadata?.fields.showScreensaverQuote&&inlineControls(['showScreensaverQuote'])}
    </Group>
    {actionExists('quick.run')&&<button type="button" className="primary-button saver-tv-preview-action" disabled={busy||!canWrite}
      onClick={()=>void perform('quick','run','screensaver')}>电视预览</button>}
    </>;
  }
  function desktop(){
    const topShelf=metadata?.fields.topShelf;
    const dockApps=(v.dockApps||[]) as string[];
    const homeButtons=(v.homeButtons||[]) as string[];
    switch(desktopCategory){
    case 'wallpaper':return <Group>
        <Row title="切换方式" icon={desktopFieldIcons.wallMode} value={label('wallMode')||metadata?.wallpapers.find(item=>item.id===v.wallpaper)?.name}
          navigate onClick={()=>open({kind:'wallpaper',title:'桌面背景'})}/>
        {topShelf&&<Setting client={client} name="topShelf" onSelect={choose}
          titleAccessory={topShelf.supportedApps?.length ? <TopShelfSupport apps={topShelf.supportedApps}/> : undefined}/>}
        {metadata?.fields.globalGlass&&controls(['globalGlass'])}
        <Row title="首页背景" icon="WallpaperIcon" value={`${label('homeBlurMode')}  ${settingText('homeBlur',v.homeBlur)}`}
          navigate onClick={()=>fields('首页背景',home)}/>
      </Group>;
    case 'home':return <Group>
        {controls(['layout'])}
        {metadata?.fields.showGreeting&&controls(['showGreeting'])}
        {metadata?.fields.showTime?<>
          <Row title="问候语与头像" icon="PersonIcon" value={String(v.greeting||'')}
            navigate onClick={()=>open({kind:'greeting',title:'问候语与头像'})}/>
          {controls(['showTime',...['clockStyle','showSeconds'].filter(key=>!!metadata.fields[key]),
            'showDate',...['showLunarDate'].filter(key=>!!metadata.fields[key]),'weatherEnabled'])}
          <Row title="首页按钮" icon="SquareMousePointerIcon" value={homeButtons.length?`${homeButtons.length} 个按钮`:'未添加'}
            navigate onClick={()=>open({kind:'buttons',title:'首页按钮'})}/>
          {metadata.fields.quickControlVisibility&&controls(['quickControlVisibility'])}
        </>:<Row title="问候语" icon={desktopFieldIcons.showGreeting} value={String(v.greeting||'')}
          navigate onClick={()=>open({kind:'text',title:'问候语',id:'greeting'})}/>}
      </Group>;
    case 'apps':return <Group>
        {metadata?.fields.homeApps&&controls(['homeApps','appNames'])}
        {metadata?.fields.iconSize&&controls(['iconSize','iconShape'])}
        {metadata?.fields.dockLimit&&controls(['dockLimit'])}
        {metadata?.fields.dockScale&&<>
          <Row title="Dock 应用" icon="PanelBottomIcon" value={`${dockApps.length} 个应用`}
            navigate onClick={()=>open({kind:'dock',title:'Dock 应用'})}/>
          {controls(dockFields.filter(key=>key!=='dockMagnification'||v.dockMagnify))}
        </>}
      </Group>;
    case 'second':return <Group>
        {controls(['secondNames'])}
        <Row title="第二屏背景" icon="PanelRightIcon" value={`模糊 ${settingText('secondBlur',v.secondBlur)}  亮度 ${settingText('secondBrightness',v.secondBrightness)}`}
          navigate onClick={()=>fields('第二屏背景',second)}/>
      </Group>;
    case 'saver':return screensaverSettings();
    case 'feedback':return <Group>{controls(['keySounds','keySoundTheme'])}</Group>;
    }
  }
  function appOrderContext(){
    const query=appQuery.trim().toLocaleLowerCase();
    const filtered=allApps.filter(app=>(!query||app.name.toLocaleLowerCase().includes(query))&&
      (appFilter===0||appFilter===1&&!app.system&&app.installed!==false||appFilter===2&&app.system||
       appFilter===3&&app.hidden&&!app.childHidden&&app.installed!==false||appFilter===4&&app.disabled||
       appFilter===5&&app.recoverable&&app.installed===false));
    const available=homeApps.map(app=>app.id),availableIds=new Set(available);
    const saved=((v.gridOrder||[]) as string[]).filter(id=>availableIds.has(id)),savedIds=new Set(saved);
    const fullOrder=[...saved,...available.filter(id=>!savedIds.has(id))];
    const visibleIds=new Set(filtered.map(app=>app.id));
    const order=metadata?.fields.gridOrder?fullOrder.filter(id=>visibleIds.has(id)):[];
    return {filtered,fullOrder,order};
  }
  function saveAppOrder(fullOrder:string[],visibleOrder:string[],revision:string){
    if(!canWrite)return;
    const visible=new Set(visibleOrder);
    let index=0;
    // Preserve the positions of applications outside the search or filter.
    client.update('gridOrder',fullOrder.map(id=>visible.has(id)?visibleOrder[index++]:id),0,revision);
  }
  function openApp(app:TvApp){open({kind:'app',title:'应用详情',id:app.id});}
  function apps(){
    const names=['全部','用户应用','预装应用','隐藏应用','已禁用','可恢复'];
    const latest=client.tasks().filter(task=>task.domain==='apps'&&task.kind==='refresh').at(-1);
    const loading=latest&&['queued','running','ready'].includes(latest.state);
    const failed=latest&&['failed','unknown'].includes(latest.state),loaded=!!v.appsLoaded;
    const {filtered,fullOrder,order}=appOrderContext(),revision=client.frame?.revisions.dock||'';
    return <>
      <div className="app-search-tools">
        <button type="button" className="app-icon-batch-action"
          disabled={busy||!loaded||client.status!=='connected'||!!iconCatalog&&!iconCatalog.targets.length}
          onClick={()=>open({kind:'app-icon-batch',title:'一键换图标'})}>一键换图标</button>
        <div className="app-toolbar-field app-toolbar-search">
          <label className="quiet-sr-only" htmlFor="app-search">搜索应用</label>
          <input id="app-search" type="text" role="searchbox" inputMode="search" enterKeyHint="search" autoComplete="off"
            autoCorrect="off" spellCheck={false} placeholder="搜索应用" value={appQuery} onChange={event=>setAppQuery(event.target.value)}/>
          {appQuery&&<button type="button" className="app-search-clear" aria-label="清除搜索"
            onClick={()=>{setAppQuery('');document.getElementById('app-search')?.focus({preventScroll:true});}}>
            <Icon name="Cross2Icon"/>
          </button>}
        </div>
        <AppFilterMenu options={names} value={appFilter} onChange={setAppFilter}/>
        <button type="button" className="text-action app-filter-refresh" disabled={busy||!!loading||!canWrite}
          aria-busy={!!loading} onClick={()=>void perform('apps','refresh')}><Icon name="ReloadIcon"/>刷新</button>
      </div>
      <div id="app-filter-results">
        {loading&&loaded&&<p className="section-note" role="status">正在读取电视应用，当前显示上次结果</p>}
        {loaded&&!loading&&(failed||!!v.appsProblem)&&<p className="form-error" role="alert">
          {String(v.appsProblem||latest?.message||'应用读取未完成，请重试')}。当前显示上次结果。
        </p>}
        {loaded?(filtered.length?<Group><ManagedAppList apps={filtered} order={order} revision={revision}
          canSort={!!revision&&canWrite&&!busy&&!loading} image={managedAppImage}
          onOpen={openApp} onActions={app=>open({kind:'app-actions',title:app.name,id:app.id})}
          onCommit={(next,expected)=>saveAppOrder(fullOrder,next,expected)}/></Group>:
          <EmptyState icon={appFilter||appQuery?'MagnifyingGlassIcon':'DashboardIcon'}
            title={appFilter||appQuery?'没有符合条件的应用':'电视应用列表为空'}
            action={(appFilter>0||appQuery)&&<button className="secondary-button" onClick={()=>{setAppFilter(0);setAppQuery('');}}>查看全部应用</button>}/>):
          loading?<EmptyState icon="ReloadIcon" title="正在读取电视应用…"/>:
          <EmptyState icon="DashboardIcon" title={failed||v.appsProblem?'尚未取得应用列表':'应用列表尚未读取'}
            description={failed||v.appsProblem?String(v.appsProblem||latest?.message||'应用读取未完成，请重试'):undefined}
            alert={!!(failed||v.appsProblem)}
            action={<button className="secondary-button" disabled={busy||!canWrite} onClick={()=>void perform('apps','refresh')}>重新读取</button>}/>}
      </div>
    </>;
  }
  function continueParent(){
    const continuation=parentContinuation.current;
    parentContinuation.current=null;
    if(!continuation||continuation.generation!==client.frame?.generation||!client.frame?.domains.parent?.authorized){back();return;}
    if(continuation.valid&&!continuation.valid()){back();return;}
    if(continuation.title){
      setParentConfirmation(continuation);
      navigate(nav.current.tab,[...nav.current.trail.slice(0,-1),{kind:'parent-confirm',title:continuation.title}],true);
      return;
    }
    const epoch=viewEpoch.current;
    replaceParentRoute.current=true;
    try{continuation.run();}finally{replaceParentRoute.current=false;}
    if(epoch===viewEpoch.current)back();
  }
  async function parentRequest(action:string){
    if(busy)return;
    if(['setup','change','recover'].includes(action)&&newPin!==confirmPin){setError('两次输入的新密码不一致');return;}
    const epoch=viewEpoch.current;setBusy(true);setError('');
    try{
      const result=await client.parent(action,pin,newPin);
      setPin('');setNewPin('');setConfirmPin('');
      if(epoch!==viewEpoch.current)return;
      if(result.recovery){
        setRecovery(result.recovery);
        navigate(nav.current.tab,[...nav.current.trail.slice(0,-1),{kind:'recovery-code',title:'保存家长恢复码'}],true);
      }else if(parentContinuation.current)continueParent();
      else back();
    }catch(problem){if(epoch===viewEpoch.current)setError(problem instanceof Error?problem.message:'验证结果待核对');}
    finally{setBusy(false);}
  }
  function requireParent(next:()=>void|Promise<boolean>,title?:string,valid?:()=>boolean){
    if(client.frame?.domains.parent?.authorized){next();return;}
    parentContinuation.current={run:next,title,valid,generation:client.frame?.generation};
    const configured=client.frame?.domains.parent?.configured;
    open({kind:'parent',title:configured?'家长验证':'设置家长密码',id:configured?'verify':'setup'},true);
  }
  useEffect(()=>{
    if(!ended)return;
    parentContinuation.current=null;setParentConfirmation(null);setPin('');setNewPin('');setConfirmPin('');setRecovery('');
  },[ended]);
  function childSchedule(pkg:string){return ((client.values().rules||{}) as Record<string,Record<string,Value>>)[pkg];}
  function childRules(pkg:string){const rule:Record<string,Value>=((v.rules||{}) as Record<string,Record<string,Value>>)[pkg]||{locked:false,mode:0,minutes:60,repeatDays:127},quizApps=(v.quizApps||{}) as Record<string,boolean>,usage=(client.frame?.domains.childUsage?.usage||{}) as Record<string,Record<string,number>>;const managed=typeof rule.enabled==='boolean'?rule.enabled:!!(rule.hidden||rule.locked||Number(rule.mode)>0||quizApps[pkg]);return <><Group><Row title="对此应用开启儿童模式" icon="PersonIcon"><Switch title="对此应用开启儿童模式" checked={managed} disabled={busy} onChange={()=>requireParent(()=>childRule(pkg,'enabled',!managed),managed?'关闭此应用的儿童模式？':'开启此应用的儿童模式？',()=>((client.metadata?.childApps||[]) as TvApp[]).some(item=>item.id===pkg))}/></Row><Row title="锁定应用" icon="LockClosedIcon"><Switch title="锁定应用" checked={!!rule.locked} disabled={busy||!managed} onChange={()=>requireParent(()=>childRule(pkg,'locked',!rule.locked),rule.locked?'解锁此应用？':'锁定此应用？',()=>{const current=childSchedule(pkg);return !!current&&!!current.locked===!!rule.locked;})}/></Row><Row title="使用时长" icon="ClockIcon" hint={rule.locked?'解除应用锁定后可调整':undefined} value={Number(rule.mode)===0?'不限制':`${Number(rule.mode)===1?'单次':Number(rule.repeatDays)===127?'循环  每天':Number(rule.repeatDays)===31?'循环  周一至周五':Number(rule.repeatDays)===96?'循环  周六和周日':'循环  自定义星期'} ${rule.minutes} 分钟`} disabled={!managed||!!rule.locked} navigate onClick={()=>requireParent(()=>open({kind:'child-time',title:'使用时长',id:pkg},!!view))}/><Row title="定时答题" icon="ReaderIcon"><Switch title="定时答题" checked={!!quizApps[pkg]} disabled={busy||!managed||!!rule.locked} onChange={()=>requireParent(()=>perform('quiz','quizApp',{package:pkg,enabled:!quizApps[pkg]}),'更改定时答题？',()=>!!childSchedule(pkg))}/></Row><Row title="答题设置" icon="QuestionMarkCircledIcon" disabled={!managed||!!rule.locked} navigate onClick={()=>requireParent(()=>open({kind:'quiz-settings',title:'答题设置',id:pkg},!!view))}/></Group>{usage[pkg]&&<p className="section-note">今日已用 {Math.floor(Number(usage[pkg]?.dailyUsed||0)/60000)} 分钟{Number(usage[pkg]?.remaining)>=0?`  剩余 ${Math.ceil(usage[pkg].remaining/60000)} 分钟`:''}</p>}</>;}
  function children(){const apps=(metadata?.childApps||[]) as TvApp[],app=apps.find(app=>app.id===selectedChild)||apps[0],parent=client.frame?.domains.parent,usage=client.frame?.domains.childUsage,current=String(usage?.currentApp||''),watching=apps.find(item=>item.id===current),quiz=(usage?.quizStatus||{}) as Record<string,Value>;const quizMessage:Record<string,string>={accepted:'正在等待电视显示',displayed:'电视正在答题',passed:'本轮已通过',parentSkipped:'家长已解除本轮'};return <><Group><Row title="儿童模式" icon="PersonIcon" hint="统一启停所有儿童规则"><Switch title="儿童模式" checked={!!v.childEnabled} onChange={()=>requireParent(()=>client.update('childEnabled',!v.childEnabled),v.childEnabled?'关闭儿童模式？':'开启儿童模式？',()=>client.value('childEnabled')===v.childEnabled)}/></Row></Group><Group>{current?<Row title={`正在观看  ${watching?.name||'当前应用'}`} icon="DesktopIcon"/>:<EmptyState icon="DesktopIcon" title="当前没有观看应用"/>}{quizMessage[String(quiz.state)]&&<p className="section-note" role="status">最近答题  {quizMessage[String(quiz.state)]}</p>}<div className="quiz-now-action"><button className="primary-button" disabled={!canWrite||busy||!v.childEnabled||!current||usage?.timerState!=='READY'} onClick={()=>requireParent(()=>perform('child','quizNow',{requestId:newId()}),'立即在电视上答题？',()=>{const latest=client.frame?.domains.childUsage;return !!client.value('childEnabled')&&latest?.currentApp===current&&latest?.timerState==='READY';})}>立即答题</button>{!!current&&usage?.timerState!=='READY'&&<p className="section-note">请先在电视授权计时服务。</p>}</div></Group>
      <section className="setting-section" aria-labelledby="child-app-rules-heading">
        <div className="section-heading"><h2 id="child-app-rules-heading">应用使用规则</h2></div>
        {app?<><button className="child-app-picker" onClick={()=>open({kind:'child-apps',title:'选择应用'})}>
          <AppImage app={app} client={client}/><strong>{app.name}</strong><Icon name="ChevronDownIcon"/>
        </button>{childRules(app.id)}</>:<EmptyState icon="PersonIcon" title="电视未提供可设置规则的应用"/>}
      </section>
      <Group title="统一设置"><Row title="默认答题设置" icon="QuestionMarkCircledIcon" value={`每 ${v.quizInterval} 分钟`} navigate onClick={()=>requireParent(()=>open({kind:'quiz-settings',title:'答题设置'}))}/><Row title="休息屏保" icon="ImageIcon" value={label('restTheme')} navigate onClick={()=>requireParent(()=>open({kind:'child-rest',title:'休息屏保'}))}/><Row title="家长密码" icon="LockClosedIcon" value={parent?.configured?'已设置':'未设置'} navigate onClick={()=>open({kind:'parent-menu',title:'家长密码'})}/><Row title="计时服务" icon="ClockIcon" value={timerSummary(usage?.timerState)} navigate onClick={()=>open({kind:'timer',title:'计时服务'})}/></Group></>;}

  async function childRule(pkg:string,field:string,value:Value){return perform('child','rule',{package:pkg,field,value});}

  function moveList(key:string,id:string,direction:number){
    const source=key==='quickControls'?quickIds():((client.value(key)||[]) as string[]),range=key==='quickControls'&&isQuickRange(id),ids=key==='quickControls'?(range?quickRanges(source):quickButtons(source)):[...source],index=ids.indexOf(id),next=index+direction;
    if(index<0||next<0||next>=ids.length)return;[ids[index],ids[next]]=[ids[next],ids[index]];if(key==='quickControls')updateQuick(range?[...quickButtons(source),...ids]:[...ids,...quickRanges(source)]);else client.update(key,ids);
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
  function quick(){
    const ids=quickIds(),buttons=quickButtons(ids),ranges=quickRanges(ids),catalog=(metadata?.quickCatalog||[]) as QuickItem[],icons=metadata?.quickIcons as QuickIcons|undefined;
    const buttonCount=quickButtonCount(ids),remainingButtons=QUICK_BUTTON_LIMIT-buttonCount,remainingRanges=QUICK_RANGE_LIMIT-ranges.length;
    const actions=(id:string)=>open({kind:'quick-actions',title:catalog.find(item=>item.id===id)?.title||id,id});
    const sound=metadata?.fields.saverSound;
    const status=(id:string)=>id==='saverSound'&&sound?(sound.disabledReason?'需视频屏保':v.saverSound?'已开启':'已关闭'):'';
    return <fieldset className="connection-controls quick-controls-editor" disabled={quickWaiting}>
      <Group title={`已添加（${buttonCount}/${QUICK_BUTTON_LIMIT}）`} action={<button className="text-action" onClick={()=>open({kind:'quick-reset',title:'恢复默认快捷控制？'})}>恢复默认</button>}>
        {quickConflict?<><p className="section-note">电视端配置已变化，手机修改已保留。</p><Row title="使用电视当前配置" onClick={()=>{setQuickDraft(null);setQuickError('');}}/><Row title="应用手机修改" disabled={!quickChangeAllowed(quickServer,ids)} onClick={()=>void saveQuickDraft({...quickDraft!,revision:quickRevision,generation:quickGeneration,submitted:false})}/></>:quickError&&<><p className="form-error" role="alert">{quickError}</p><Row title="重试保存" disabled={!quickDraft||!quickChangeAllowed(quickServer,ids)} onClick={()=>void saveQuickDraft()}/><Row title="取消未保存修改" onClick={()=>{setQuickDraft(null);setQuickError('');}}/></>}
        {buttons.length?<QuickSort order={buttons} revision={quickDraft?.revision||quickRevision} catalog={catalog} icons={icons} onCommit={(order,revision)=>updateQuick([...order,...quickRanges(ids)],revision)} hiddenIds={quickOverflow(ids)} status={status} soundOn={id=>quickSoundOn(id,v)} onActions={actions} />:<EmptyState icon="DashboardIcon" title="尚未添加普通按钮"/>}
        {remainingButtons>0&&<div className="quick-vacancies">
          {Array.from({length:remainingButtons},(_,index)=><button key={index} type="button" className="quick-vacancy"
            aria-label={`添加快捷按钮，第 ${index+1} 个空位`} onClick={()=>openQuickPicker(false)}>
            <Icon name="PlusIcon"/>
          </button>)}
        </div>}
        {quickDraft&&!quickConflict&&!quickError&&remainingButtons<0&&<Row title="取消未保存修改" onClick={()=>{setQuickDraft(null);setQuickError('');}}/>}
      </Group>
      <Group title={`快捷调节（${Math.min(ranges.length,QUICK_RANGE_LIMIT)}/${QUICK_RANGE_LIMIT}）`}>
        {ranges.length>QUICK_RANGE_LIMIT&&<p className="section-note">仅前两项在电视显示。旧配置的其他项仍可排序或移除。</p>}
        <QuickSort order={ranges} revision={quickDraft?.revision||quickRevision} catalog={catalog} icons={icons} onCommit={(order,revision)=>updateQuick([...quickButtons(ids),...order],revision)} hiddenIds={ranges.slice(QUICK_RANGE_LIMIT)} onActions={actions} />
        {remainingRanges>0&&<Row title="添加调节项" icon="PlusIcon" value={`还有 ${remainingRanges} 个空位`} onClick={()=>openQuickPicker(true)}/>}
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
  function general(){return <><Group title="手机连接"><Row title="复制连接网址" icon="Link2Icon" onClick={()=>void copyConnection()}/></Group><Group title="天气"><WeatherSettings client={client} open={open} run={perform} busy={busy}/></Group><Group title="桌面与启动">{controls(['secondHideHomeApps'])}<Row title="桌面管理" icon="HomeIcon" value={<DesktopManagementStatus state={client.frame?.domains.desktopSwitch} legacyCode={client.frame?.domains.guard?.code}/>} navigate onClick={()=>open({kind:'tool',title:'桌面管理',id:'desktop-switch'})}/><Row title="桌面备份与恢复" icon="LayersIcon" value={client.restoring?client.restoreStatus.message:client.restoreStatus.canRetry||v.backupError?'读取待核对':((v.backups||[]) as unknown[]).length?`${((v.backups||[]) as unknown[]).length}份备份`:'尚未备份'} navigate onClick={()=>open({kind:'tool',title:'桌面备份与恢复',id:'backup'})}/><Row title="开机去向" icon="HomeIcon" value={String(v.bootLabel||'QuietTV 首页')} navigate onClick={()=>open({kind:'tool',title:'开机去向',id:'boot'})}/>{actionExists('bootAds.open')&&<Row title="开机广告" icon="DesktopIcon" value={bootAdsLabel(client.frame?.domains.bootAds?.status)} navigate onClick={()=>open({kind:'tool',title:'开机广告',id:'boot-ads'})}/>}</Group><Group title="连接与信号源"><Row title="ADB 管理" icon="Link2Icon" value={<AdbStatus state={client.frame?.domains.management}/>} navigate onClick={()=>open({kind:'tool',title:'ADB 管理',id:'management'})}/><Row title="信号源配置" icon="DesktopIcon" value={String(v.watchTarget?v.watchLabel||'已配置':(client.frame?.domains.inputs?.configuration as Record<string,unknown>|undefined)?.entry?'原厂信号源菜单':'尚未配置')} navigate onClick={()=>open({kind:'tool',title:'信号源配置',id:'inputs'})}/>
      {client.frame?.domains.systemSettings&&<Row title="原设置" icon="GearIcon" navigate
        onClick={()=>open({kind:'tool',title:'原设置',id:'system-settings'})}/>}
    </Group></>;}
  function tools(){return <Group><Row title={FILE_TRANSFER_TITLE} icon="FileIcon" value={`${((v.receivedFiles||[]) as unknown[]).length}个文件`} navigate onClick={()=>open({kind:'tool',title:FILE_TRANSFER_TITLE,id:'files'})}/><Row title="备忘录" icon="FileTextIcon" value={`${((v.notes||[]) as {done:boolean}[]).filter(note=>!note.done).length}条未完成`} navigate onClick={()=>open({kind:'tool',title:'备忘录',id:'memos'})}/><Row title="Tailscale 组网" icon="Link2Icon" value="在手机助手中使用" navigate onClick={()=>open({kind:'remote-assistant',title:'Tailscale 组网'})}/></Group>;}
  function help(){return <><Group><Row title="软件更新" value={metadata?.device.version} icon="DownloadIcon" navigate onClick={()=>open({kind:'update',title:'软件更新'})}/></Group><Group title="问题与反馈">{actionExists('diagnostics.export')&&<Row title="诊断日志" icon="FileTextIcon" navigate onClick={()=>open({kind:'tool',title:'诊断日志',id:'diagnostics'})}/>}<Row title="加入交流群" value="提反馈  提建议" icon="PersonIcon" navigate onClick={()=>open({kind:'community',title:'加入交流群'})}/></Group><Group title="关于"><Row title="关于 Quiet TV" value="版本与设备信息" icon="InfoCircledIcon" navigate onClick={()=>open({kind:'about',title:'关于 Quiet TV'})}/></Group></>;}
  function sheet(view:View){switch(view.kind){
    case 'tool':
      if(view.id==='files'||view.id==='file-browser')return <FileTransfer key={viewKey(view)} client={client} view={view}
        open={open} run={perform} busy={busy||!!upload} canWrite={canWrite} setLocation={(source:FileSource,id='')=>{
          const next:View={kind:'tool',id:'files',title:FILE_TRANSFER_TITLE,keys:source==='received'?undefined:[source,id]};
          navigate(tab,[...nav.current.trail.slice(0,-1),next],true,null);
        }}/>;
      if(view.id==='file-detail'||view.id==='file-preview')return <FileDetail key={viewKey(view)} client={client} view={view}
        open={open} run={perform} disabled={busy||!canWrite||!!upload}/>;
      if(view.id==='system-settings'){
        const state=client.frame?.domains.systemSettings;
        const confirmation=typeof state?.confirmation==='string'?state.confirmation:'';
        const disabled=busy||!canWrite||!state;
        return <Group>
          <Row title="打开电视原设置" disabled={disabled} onClick={()=>void perform('systemSettings','open')}/>
          <Row title="尝试其他打开方式" disabled={disabled} onClick={()=>void perform('systemSettings','next')}/>
          {confirmation&&<Row title="已正常打开，记住此方式" disabled={disabled}
            onClick={()=>void perform('systemSettings','remember',confirmation)}/>}
          {state?.hasTried&&<Row title="重新检测入口" disabled={disabled} onClick={()=>void perform('systemSettings','detect')}/>}
        </Group>;
      }
      if(view.id==='boot-ads')return <BootAds client={client} run={perform} busy={busy}/>;if(view.id==='desktop-switch'||view.id==='guard')return <DesktopSwitch client={client} open={open} run={perform} busy={busy}/>;return <ToolBody client={client} view={view} open={open} run={perform} busy={busy} drafts={drafts.current} newMemo={()=>{if(!memoDraftId.current)memoDraftId.current=newId();open({kind:'memo-edit',title:'新建备忘录',id:memoDraftId.current,message:''},true);}} pickFile={pickFile} controls={inlineControls} back={back} uploading={!!upload}/>;
    case 'memo-edit':
    case 'text':return textEditor(view);
    case 'desktop-fields':return <div className="desktop-background-controls">
      {controls(view.keys||[],true,{homeBlurMode:'模糊方式'})}
    </div>;
    case 'fields':return controls(view.keys||[],true);
    case 'choice':{
      if(view.id==='rotation'||view.id==='rotationSeconds'){
        return rotationField&&typeof rotationValue==='number'?<RotationInterval key={rotationKey+rotationValue} field={rotationField}
          current={rotationValue} secondsSupported={rotationKey==='rotationSeconds'}
          disabled={busy||!canWrite||!!rotationField.disabledReason||v.wallMode!=='rotation'} onApply={value=>void applyRotation(value)}/>:null;
      }
      const field=metadata?.fields[view.id!];if(!field?.options)return null;
      const value=client.value(view.id!)??'';
      return <ChoiceList title={field.title} options={field.options} value={value}
        disabled={busy||!!field.disabledReason||view.id==='wallMode'&&!canWrite} hint={field.disabledReason}
        onChange={value=>void selectValue(view.id!,value,true)}/>;
    }
    case 'weather-component':return <WeatherComponent client={client} disabled={busy||!canWrite}
      actionsHost={pageActionsHost} run={action=>perform('components',action,'weather')}/>;
    case 'remote-assistant':return <OpenAssistant/>;
    case 'wallpaper':return <>
      <Group>
        {metadata?.fields.wallMode?.options&&controls(['wallMode'])}
        <WeatherComponentRow client={client} onClick={()=>open({kind:'weather-component',title:'动态天气壁纸组件'},true)}/>
        {['fixed','rotation'].includes(String(v.wallMode))&&<Row title="选择壁纸" icon="ImageIcon"
          value={metadata?.wallpapers.find(item=>item.id===v.wallpaper)?.name} navigate
          onClick={()=>open({kind:'media',title:'壁纸素材'},true)}/>}
        {v.wallMode==='rotation'&&rotationField&&<Row title="轮换间隔" icon="ReloadIcon"
          value={typeof rotationValue==='number'?rotationLabel(rotationValue*(rotationKey==='rotationSeconds'?1:60)):'正在读取…'}
          navigate disabled={typeof rotationValue!=='number'} onClick={()=>choose(rotationKey)}/>}
        {metadata?.fields.wallSound&&inlineControls(['wallSound'])}
        {v.wallMode==='daily'&&<>
          <DailyWallpaperSource client={client} disabled={busy}/>
          {v.dailySource==='movie-daily'&&inlineControls(['showMovieInfo'])}
          <Row title="当前图片详情" icon="ImageIcon"
            value={String((v.dailyWallpaper as Record<string,unknown>|undefined)?.title||'')} navigate
            onClick={()=>open({kind:'daily',title:'当前图片详情'},true)}/>
        </>}
        {metadata?.fields.showHomeQuote&&inlineControls(['showHomeQuote'])}
      </Group>
      {v.wallMode==='daily'&&<p className="section-note">每天更新，断网保留当前背景。</p>}
      {v.wallMode==='weather'&&<Group>
        <WeatherSummary client={client}/>
        {inlineControls(['dayNight'])}
        <Row title="天气位置" icon="GlobeIcon" value={String(v.autoLocation?v.locatedCity||'定位待更新':v.city||'尚未选择')}
          navigate onClick={()=>open({kind:'tool',id:'weather-location',title:'天气位置'},true)}/>
        <Row title="来源与服务" icon="LayersIcon" navigate onClick={()=>open({kind:'tool',id:'weather-sources',title:'来源与服务'},true)}/>
      </Group>}
    </>;
    case 'media':{
      const saver=view.id==='screensaver',uploadTarget=saver?'screensaver':'',type=view.mediaType||'image',types=['image','video'] as const;
      const selectType=(mediaType:'image'|'video')=>navigate(tab,[...nav.current.trail.slice(0,-1),{...view,mediaType}],true);
      const domain=saver?'screensaver':'wallpaper';
      const builtinHidden=saver?v.saverBuiltinHidden:v.wallpaperBuiltinHidden;
      const items=metadata?.wallpapers.filter(item=>item.kind!=='daily'&&item.kind!=='color'
        &&!(item.id===BUILTIN_WALLPAPER&&builtinHidden)&&((item.video||item.kind==='video')?'video':'image')===type)||[];
      const canRestoreBuiltin=type==='image'&&builtinHidden&&metadata?.wallpapers.some(item=>item.id===BUILTIN_WALLPAPER&&item.canDelete);
      return <><div className="media-tabs" role="tablist" aria-label="壁纸类型">{types.map((value,index)=><button key={value} id={`media-tab-${value}`} type="button" role="tab" aria-selected={type===value} aria-controls="media-results" tabIndex={type===value?0:-1} onClick={()=>selectType(value)} onKeyDown={event=>{const next=event.key==='ArrowRight'||event.key==='ArrowLeft'?1-index:event.key==='Home'?0:event.key==='End'?1:-1;if(next<0)return;event.preventDefault();selectType(types[next]);event.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>('[role=tab]')[next]?.focus();}}>{value==='image'?'静态壁纸':'视频壁纸'}</button>)}</div>
        <div id="media-results" role="tabpanel" aria-labelledby={`media-tab-${type}`} tabIndex={0}>
          {canRestoreBuiltin&&<Group><Row title="恢复默认素材" icon="ReloadIcon" disabled={busy||!canWrite}
            onClick={()=>void perform(domain,'builtinMediaVisible',true)}/></Group>}
          {items.length?<Group>{items.map(item=><div className="media-option" key={item.id}>
            <button className="media-choice" disabled={busy} onClick={async()=>{
              const epoch=viewEpoch.current;
              if(await perform(domain,saver?'saverMedia':'wallpaper',item.id)&&epoch===viewEpoch.current)back();
            }}>
              {item.bytes>0&&<ResourceImage src={client.resource(`wallpaper/${encodeURIComponent(item.id)}?v=${item.bytes}`)} alt=""/>}
              <span><strong>{item.name}</strong><small>{mediaHint(item)}</small></span>
              {(saver?v.saverMedia:v.wallpaper)===item.id&&<Icon name="CheckIcon" className="blue"/>}
            </button>
            {item.canRename&&<button className="icon-button" aria-label={`重命名${item.name}`}
              onClick={()=>open({kind:'media-rename',title:'重命名',id:item.id},true)}><Icon name="Pencil1Icon"/></button>}
            {(item.canDelete??item.canRename)&&<button className="icon-button danger" disabled={busy||!canWrite}
              aria-label={`删除${item.name}`} onClick={()=>{setRemoveMedia(item);open({kind:'remove-media',title:'删除素材？'},true);}}>
              <Icon name="TrashIcon"/>
            </button>}
          </div>)}</Group>:<EmptyState icon={type==='image'?'ImageIcon':'VideoIcon'}
            title={type==='image'?'暂无静态壁纸':'暂无视频壁纸'}/>}
        </div>{wallpaperFiles.length>0&&wallpaperTarget.current===uploadTarget&&
        <button className="secondary-button" disabled={!!upload} onClick={()=>open({kind:'upload-options',title:'上传素材'},true)}>查看上次上传</button>}</>;
    }
    case 'daily':return <DailyWallpaperDetails client={client} disabled={busy}/>;
    case 'upload-options':return <WallpaperUpload key={uploadSelection} files={wallpaperFiles} client={client} target={wallpaperTarget.current} drafts={forms.current} actionsHost={uploadActionsHost} onProgress={progress=>setUpload(progress?{...progress,context:feedbackKey(view)}:null)} onCancel={cancel=>{cancelUpload.current=cancel;}}/>;
    case 'image-upload':if(!file)return null;if(fileIntent.current.kind==='icon'){
      const [id,shape]=fileIntent.current.target.split('|');
      return <CoverCrop file={file} shape={shape} currentShape={desktopIconShape(client)} busy={!!upload} appName={allApps.find(app=>app.id===id)?.name} onUpload={prepared=>sendFile(true,prepared)}/>;
    }return <><p className="sheet-intro">{file.name}</p><button className="primary-button" disabled={!!upload} onClick={()=>void sendFile(true)}>{fileIntent.current.kind==='file'?'发送到电视':fileIntent.current.kind==='backup'?'上传并校验备份':'上传并应用'}</button></>;
    case 'movie-wall-source':
      return <MovieWallSettings client={client} onOpen={provider=>open({kind:'movie-wall-provider',title:movieSourceTitles[provider],id:provider},true)}/>;
    case 'movie-wall-provider':{
      const provider=view.id==='smb'||view.id==='plex'?view.id:'feiniu';
      return <MovieWallSourceSettings key={provider} client={client} provider={provider} disabled={busy||!canWrite}
        footerHost={movieWallFooterHost}/>;
    }
    case 'screensaver':return screensaverSettings();
    case 'greeting':return <Group>{inlineControls(['showGreeting','showAvatar'])}<Row title="头像" icon="ImageIcon" value={v.avatar?'已设置':'未设置'} onClick={()=>pickFile('avatar')}/><Row title="问候语内容" icon="MessageSquareTextIcon" value={String(v.greeting||'')} navigate onClick={()=>open({kind:'text',title:'问候语',id:'greeting'},true)}/></Group>;
    case 'dock':{const ids=(v.dockApps||[]) as string[];return <Group>{ids.map((id,index)=><Row key={id} title={homeApps.find(app=>app.id===id)?.name||id} icon="DashboardIcon"><button className="icon-button" aria-label="上移" disabled={index===0} onClick={()=>moveList('dockApps',id,-1)}><Icon name="ArrowUpIcon"/></button><button className="icon-button danger" aria-label="从 Dock 移除" onClick={()=>client.update('dockApps',ids.filter(item=>item!==id))}><Icon name="Cross2Icon"/></button></Row>)}<Row title="添加 Dock 应用" icon="PlusIcon" disabled={ids.length>=Number(v.dockLimit)} navigate onClick={()=>open({kind:'dock-picker',title:'添加 Dock 应用'},true)}/></Group>;}
    case 'dock-picker':{
      const ids=(v.dockApps||[]) as string[],available=homeApps.filter(app=>!ids.includes(app.id));
      return available.length?<Group>{available.map(app=><Row key={app.id} title={app.name} icon="DashboardIcon"
        onClick={()=>{client.update('dockApps',[...ids,app.id]);back();}}/>)}</Group>
        :<EmptyState icon="PanelBottomIcon" title="暂无可添加的 Dock 应用"/>;
    }
    case 'buttons':{const ids=(v.homeButtons||[]) as string[],catalog=(metadata?.homeButtonCatalog||[]) as {id:string;title:string}[];return <Group>{ids.map((id,index)=><Row key={id} title={catalog.find(item=>item.id===id)?.title||id} icon={quickGlyph(id)}><button className="icon-button" aria-label="上移" disabled={index===0} onClick={()=>moveList('homeButtons',id,-1)}><Icon name="ArrowUpIcon"/></button><button className="icon-button" aria-label="替换" onClick={()=>open({kind:'button-picker',title:'替换首页按钮',id},true)}><Icon name="DotsHorizontalIcon"/></button><button className="icon-button danger" aria-label="移除" onClick={()=>client.update('homeButtons',ids.filter(item=>item!==id))}><Icon name="Cross2Icon"/></button></Row>)}{ids.length<3&&<Row title="添加首页按钮" icon="PlusIcon" navigate onClick={()=>open({kind:'button-picker',title:'添加首页按钮'},true)}/>}</Group>;}
    case 'button-picker':{
      const ids=(v.homeButtons||[]) as string[],catalog=(metadata?.homeButtonCatalog||[]) as {id:string;title:string}[];
      const available=catalog.filter(item=>!ids.includes(item.id));
      return available.length?<Group>{available.map(item=><Row key={item.id} title={item.title} icon={quickGlyph(item.id)}
        onClick={()=>{client.update('homeButtons',view.id?ids.map(id=>id===view.id?item.id:id):[...ids,item.id]);back();}}/>)}</Group>
        :<EmptyState icon="SquareMousePointerIcon" title={view.id?'暂无可替换的首页按钮':'暂无可添加的首页按钮'}/>;
    }
    case 'quick-actions':{
      const ids=quickIds(),id=view.id!;
      if(!ids.includes(id))return <EmptyState icon="DashboardIcon" title="此功能已被移除" description="请关闭后重新选择。"/>;
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
        return <EmptyState icon="DashboardIcon" title="快捷控制已变化" description="请返回后重新选择。"/>;
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
        </button>)}</Group>:<EmptyState icon={view.quickRange?'MixerHorizontalIcon':'DashboardIcon'}
          title={view.quickRange?'暂无可添加的调节项':'暂无可添加的快捷按钮'}/>}
      </fieldset>;
    }
    case 'app-actions':{
      const app=allApps.find(item=>item.id===view.id);
      if(!app)return <EmptyState icon="DashboardIcon" title="应用已不可用" description="请重新读取列表。"/>;
      const {fullOrder,order}=appOrderContext(),index=order.indexOf(app.id),revision=client.frame?.revisions.dock||'';
      const disabled=!revision||!canWrite||busy;
      function move(direction:number){
        const next=[...order],target=index+direction;
        [next[index],next[target]]=[next[target],next[index]];
        saveAppOrder(fullOrder,next,revision);
        back();
      }
      return <Group>
        {index>=0&&<><Row title="上移" disabled={disabled||index===0} onClick={()=>move(-1)}/>
          <Row title="下移" disabled={disabled||index===order.length-1} onClick={()=>move(1)}/></>}
        <Row title="应用详情" navigate onClick={()=>openApp(app)}/>
      </Group>;
    }
    case 'app':{
      const app=allApps.find(app=>app.id===view.id);
      if(!app)return <EmptyState icon={v.appsLoaded?'DashboardIcon':'ReloadIcon'}
        title={v.appsLoaded?'应用已不可用':'正在读取电视应用…'}
        description={v.appsLoaded?'请返回列表。':undefined}/>;
      return <ManagedAppDetail app={app} image={managedAppImage(app)} busy={busy} onAction={action=>{
        if(action.id==='rename')open({kind:'app-rename',title:'重命名应用',id:app.id},true);
        else if(action.id==='cover')open({kind:'app-icons',title:'更换图标',id:app.id},true);
        else{
          setAppConfirmation({app,action});
          open({kind:'app-confirm',title:applicationActionTitle(action)},true);
        }
      }}/>;
    }
    case 'app-icon-batch':return <IconLibrary client={client} pickFile={pickFile} batch onSaved={back} actionsHost={pageActionsHost}/>;
    case 'app-icons':return <IconLibrary key={view.id} client={client} packageId={view.id} pickFile={pickFile} onSaved={back} actionsHost={pageActionsHost}/>;
    case 'parent-menu':return <Group><Row title={client.frame?.domains.parent?.configured?'验证家长密码':'设置家长密码'} navigate onClick={()=>open({kind:'parent',title:'家长密码',id:client.frame?.domains.parent?.configured?'verify':'setup'},true)}/>{!!client.frame?.domains.parent?.configured&&<><Row title="修改密码" navigate onClick={()=>requireParent(()=>open({kind:'parent',title:'修改家长密码',id:'change'},true))}/><Row title="查看恢复码" onClick={()=>requireParent(()=>void parentRequest('recovery'))}/><Row title="使用恢复码重设" navigate onClick={()=>open({kind:'parent',title:'恢复家长密码',id:'recover'},true)}/></>}</Group>;
    case 'parent':{const action=view.id||'verify',setting=['setup','change','recover'].includes(action);return <form key={action} autoComplete="off" aria-busy={busy} onSubmit={event=>{event.preventDefault();void parentRequest(action);}}>{['verify','recover'].includes(action)&&<ParentPinInput label={action==='recover'?'恢复码':'家长密码'} value={pin} onChange={setPin}/>} {setting&&<><ParentPinInput label="新密码" value={newPin} onChange={setNewPin} autoComplete="new-password"/><ParentPinInput label="再次输入新密码" value={confirmPin} onChange={setConfirmPin} autoComplete="new-password"/></>}<div className="confirmation-dialog-actions"><button type="button" disabled={busy} onClick={back}>取消</button><button className="confirm-button" disabled={busy||!canWrite||Number(client.frame?.domains.parent?.retrySeconds)>0}>{busy?setting?'正在保存…':'正在验证…':setting?'设置密码':'验证'}</button></div>{Number(client.frame?.domains.parent?.retrySeconds)>0&&<p className="form-error">请等待 {String(client.frame?.domains.parent?.retrySeconds)} 秒后重试</p>}</form>;}
    case 'recovery-code':return <><p className="recovery-code">{recovery}</p><p className="section-note">请由家长单独保存。忘记密码时需要输入此码。</p><button className="primary-button" onClick={()=>{setRecovery('');if(parentContinuation.current)continueParent();else back();}}>已保存恢复码</button></>;
    case 'timer':return <><p className="sheet-intro">{String(client.frame?.domains.childUsage?.timerMessage||'正在读取计时状态')}</p><div className="paired-actions">
      <button type="button" className="primary-button" disabled={busy||!canWrite} onClick={()=>void perform('child','permission')}>在电视授权</button>
      <button type="button" className="secondary-button" onClick={()=>void client.refreshNow()}>检查状态</button>
    </div></>;
    case 'quiz-settings':return quizDraftValue&&<QuizSettingsForm client={client} draft={quizDraftValue} setDraft={draft=>{forms.current.set('quiz:'+String(view.id||''),draft);setQuizDraftValue(draft);}} onCancel={()=>{forms.current.delete('quiz:'+String(view.id||''));back();}} onQuestions={()=>open({kind:'quiz-questions',title:'自定义题目'},true)} onSaved={()=>{forms.current.delete('quiz:'+String(view.id||''));back();}}/>;
    case 'quiz-questions':return <QuizQuestions client={client} selected={quizSelected} onSelect={ids=>{forms.current.set('quiz-selection',ids);setQuizSelected(ids);}} onEdit={id=>open({kind:'quiz-edit',title:id?'编辑题目':'新增题目',id},true)} onDeleted={id=>setQuizSelected(previous=>{const ids=previous.filter(value=>value!==id);forms.current.set('quiz-selection',ids);return ids;})} onSaved={()=>{forms.current.delete('quiz-selection');back();}}/>;
    case 'quiz-edit':return <QuizQuestionEditor key={view.id||'new'} client={client} id={view.id||''} drafts={forms.current} onSaved={back} onCancel={back}/>;
    case 'child-rest':return <ChildRestPicker client={client} value={Number(v.restTheme)} onChange={value=>void selectValue('restTheme',value)}/>;
    case 'child-apps':{
      const apps=(metadata?.childApps||[]) as TvApp[];
      return apps.length?<Group>{apps.map(app=><button className="setting-row child-app-option" key={app.id} onClick={()=>{setSelectedChild(app.id);back();}}><AppImage app={app} client={client}/><span className="row-title">{app.name}</span>{app.id===(selectedChild||apps[0]?.id)&&<Icon name="CheckIcon" className="blue"/>}</button>)}</Group>
        :<EmptyState icon="PersonIcon" title="暂无可设置规则的应用"/>;
    }
    case 'child-rule':return childRules(view.id!);
    case 'configuration':return <><p className="section-note">以下设置尚未完整读取，请重新读取电视配置后核对。</p><Group>{missingSettings(metadata,v).map(key=><Row key={key} title={fieldPresentation[key]?.title||key} value={metadata?.fields[key]?'当前值未读取':'定义未读取'}/>)}</Group><button className="secondary-button" onClick={()=>void client.refreshNow()}>重新读取配置</button></>;
    case 'child-time':return timeDraft&&<ChildTime draft={timeDraft} change={draft=>{forms.current.set('time:'+view.id,draft);setTimeDraft(draft);}}
      current={childTimeRule(childSchedule(view.id!))} blocked={busy}
      authorized={!!client.frame?.domains.parent?.authorized} cancel={()=>{forms.current.delete('time:'+view.id);back();}}
      save={async rule=>{
        if(!client.frame?.domains.parent?.authorized){requireParent(()=>{});return;}
        const epoch=viewEpoch.current;
        if(await childRule(view.id!,'settings',rule)&&epoch===viewEpoch.current){forms.current.delete('time:'+view.id);back();}
      }}/>;
    case 'child-grant':return <form onSubmit={async event=>{event.preventDefault();const epoch=viewEpoch.current,ok=await perform('child','grant',{package:view.id!,minutes:Number(text)});if(ok&&epoch===viewEpoch.current)back();}}><label>分钟<input type="number" aria-label="分钟" min={1} max={1440} step={1} required value={text} onChange={event=>setText(event.target.value)}/></label><button className="primary-button" disabled={busy}>应用时长</button></form>;
    case 'update':return <UpdateSettings client={client} device={metadata?.device} busy={busy}
      action={(field,value)=>void perform('update',field,value)}
      install={sha=>open({kind:'tool-confirm',id:'update',title:'安装更新？',keys:['install'],
        message:JSON.stringify({value:sha,message:'请按电视提示完成安装授权，并在系统安装器确认。安装后重新连接手机，即可核对电视版本。'})},true)}/>;
    case 'about':return metadata&&<AboutSettings client={client} metadata={metadata} visible={!closing}
      tab={(view.id as AboutTab|undefined)||'device'}
      select={id=>navigate(tab,[...nav.current.trail.slice(0,-1),{...view,id}],true)}
      openRecovery={()=>open({kind:'parent-menu',title:'家长密码'},true)}
      openRelease={()=>open({kind:'about-release',title:'版本说明'},true)}
      openLicense={()=>open({kind:'about-license',title:'MIT 许可证'},true)}/>;
    case 'about-release':return metadata?.releaseNotes?<p className="sheet-prose">{String(metadata.releaseNotes)}</p>
      :<EmptyState icon="FileTextIcon" title="暂无版本说明"/>;
    case 'about-license':return metadata?.license?<p className="sheet-prose">{String(metadata.license)}</p>
      :<EmptyState icon="FileTextIcon" title="许可证尚未读取"/>;
    case 'community':return <><ResourceImage className="community-qr" src={client.resource('assets/qq-group.webp')} alt="QuietTV 交流群二维码"/><p className="sheet-intro community-number">QQ群 1126775126</p><p className="section-note community-number">入群暗号  Quiet是最好的</p></>;
    case 'theme':return <ChoiceList title="外观" options={Object.entries(themeLabels).map(([value,title])=>({value,title}))} value={theme} onChange={value=>chooseTheme(value as Theme)}/>;
    case 'sync':return <>
      {syncMessages.length>0&&<p className="section-note" role="status">{syncMessages.join('。')}</p>}
      {sync.drafts.length>0&&<Group>{sync.drafts.map(draft=><div className="sync-difference" key={draft.key} data-draft-state={draft.state}>
        <strong>{metadata?.fields[draft.key]?.title||draft.key}</strong>
        <p>电视当前值  {settingText(draft.key,draft.televisionValue)}</p>
        <p>手机输入  {settingText(draft.key,draft.value)}</p>
        {draft.error&&<p className="form-error">{draft.error}</p>}
        {draft.state==='checking'&&<p>正在核对这项操作的回执</p>}
        {['conflict','rejected'].includes(draft.state)&&<div className="sync-draft-actions">
          <button className="text-action" disabled={!sync.canDiscard} onClick={()=>client.useTelevision(draft.key)}>采用电视值</button>
          <button className="text-action" disabled={!sync.canReapply} onClick={()=>void client.retry(draft.key,client.frame?.revisions[draft.domain])}>应用此项手机修改</button>
        </div>}
      </div>)}</Group>}
      {client.endedOperation&&<Group>
        <Row title="上次操作" value={client.endedOperation}/>
        <Row title="已在电视核对" onClick={()=>client.clearEndedOperation()}/>
      </Group>}
      {ended?<button className="primary-button" onClick={()=>void client.reconnect()}>{client.restoreReview?'重新连接并查看结果':'重新连接'}</button>:
        <button className="secondary-button" disabled={!sync.canRefresh||reconnecting} onClick={()=>void client.refresh()}>{checking?'核对原操作':'重新读取电视配置'}</button>}
    </>;
    case 'info':return view.message?<p className="sheet-prose">{view.message}</p>
      :<EmptyState icon="InfoCircledIcon" title="当前版本未提供说明内容"/>;
    default:return <p className="section-note">正在读取电视提供的可用操作</p>;
  }}
  function viewTitle(item:View){
    if(item.kind==='tool'&&(item.id==='files'||item.id==='file-browser'))return FILE_TRANSFER_TITLE;
    if(item.kind==='tool'&&item.id==='file-detail'){
      const entries=(item.keys?.[1]==='received'?v.receivedFiles:v.sourceFiles) as {id:string;name:string}[]|undefined;
      return entries?.find(file=>file.id===item.keys?.[0])?.name||item.title;
    }
    return item.title;
  }
  function confirmation(item:View){
    const epoch=viewEpoch.current;
    const returnToPage=()=>{if(viewEpoch.current===epoch)back();};
    const blocked=!canWrite||!!upload;
    const detail=error&&<p className="form-error" role="alert">{error}</p>;
    if(item.kind==='app-confirm'&&appConfirmation){
      const {app,action}=appConfirmation;
      return <AppActionConfirmation request={appConfirmation} current={allApps.find(target=>target.id===app.id)}
        busy={busy} disabled={blocked} error={error} onCancel={back} onDone={returnToPage} onConfirm={async()=>{
          if(['UNINSTALL','systemUninstall'].includes(action.id)&&((client.frame?.domains.apps?.uninstallParentApps||[]) as string[]).includes(app.id)&&!client.frame?.domains.parent?.authorized){
            requireParent(()=>{});return false;
          }
          return perform('apps',action.id,{package:app.id},false);
        }}/>;
    }
    if(item.kind==='remove-media'&&removeMedia){
      const builtin=removeMedia.id===BUILTIN_WALLPAPER;
      const domain=pageView?.id==='screensaver'?'screensaver':'wallpaper';
      return <ConfirmDialog title="删除素材？" busy={busy} disabled={blocked} onCancel={back}
        onConfirm={async()=>{
          const removed=builtin?await perform(domain,'builtinMediaVisible',false,false)
            :await perform('wallpaper','removeMedia',removeMedia.id,false);
          if(removed)returnToPage();
        }}>
        <p className="confirmation-media-name">{removeMedia.name}</p>
        <p>{builtin?'只从当前素材库移除，可恢复。正在使用时会改用其他图片；没有图片时使用备用背景。'
          :'删除后无法恢复。若正在使用该素材，桌面或屏保会自动切换。'}</p>{detail}
      </ConfirmDialog>;
    }
    if(item.kind==='parent-confirm'&&parentConfirmation){
      const authorized=!!client.frame?.domains.parent?.authorized;
      const valid=parentConfirmation.valid?.()??true;
      return <ConfirmDialog title={item.title} busy={busy} disabled={blocked||!authorized||!valid} destructive={false} confirmLabel="确认" busyLabel="正在提交…" onCancel={back}
        onConfirm={async()=>{if(canWrite&&authorized&&(parentConfirmation.valid?.()??true)&&await parentConfirmation.run()!==false)returnToPage();}}>
        {(!valid||!authorized)&&<p>电视状态或家长授权已变化，请返回核对。</p>}{detail}
      </ConfirmDialog>;
    }
    if(item.kind==='quick-reset')return <ConfirmDialog title={item.title} busy={busy} disabled={blocked} destructive={false}
      confirmLabel="恢复默认" busyLabel="正在恢复…" onCancel={back}
      onConfirm={async()=>{if(await perform('quick','reset',true,false)){setQuickDraft(null);setQuickError('');returnToPage();}}}>
      <p>将使用电视端的默认功能和顺序。</p>{detail}
    </ConfirmDialog>;
    if(item.kind==='tool-confirm'){
      const {value,message}=JSON.parse(item.message||'{}') as {value:Value;message:string};
      const id=item.id!,field=item.keys![0];
      const exists=id==='backup'&&['restore','remove'].includes(field)?
        ((v.backups||[]) as {id:string}[]).some(entry=>entry.id===value):
        id==='files'?typeof value==='string'&&(value.startsWith('source-')&&['install','wallpaper'].includes(field)
          ?((v.sourceFiles||[]) as {id:string}[]).some(entry=>entry.id===value.slice(7))
          :((v.receivedFiles||[]) as {id:string}[]).some(entry=>entry.id===value)):true;
      const destructive=field==='remove'||field==='delete';
      const label=id==='files'&&field==='wallpaper'?'应用到电视':field==='restore'?'确认恢复':field==='install'?'确认安装':destructive?'确认删除':item.title.replace(/[？?]$/,'');
      return <ConfirmDialog title={item.title} busy={busy} disabled={blocked||!exists} destructive={destructive}
        confirmLabel={label} busyLabel="正在提交…" onCancel={back}
        onConfirm={async()=>{
          if(!await perform(id,field,value,false)||viewEpoch.current!==epoch)return;
          const parent=nav.current.trail.at(-2);
          if(field==='remove'&&['file-detail','backup-detail'].includes(parent?.id||''))history.go(-2);
          else back();
        }}>
        {exists?<p className="backup-confirm-copy">{message}</p>:<p>此项目已不可用。请返回列表核对。</p>}{detail}
      </ConfirmDialog>;
    }
  }
  function viewFeedback(item:View){
    return <>{taskFeedback(item)}{notice&&noticePersistent&&item.kind!=='update'&&<p className="section-note" role="status">{notice}</p>}
      {error&&view===item&&<p className="form-error" role="alert">{error}</p>}</>;
  }
  if(!metadata)return <main className="quiet-app connect-page" data-theme={light?'light':'dark'}>
    <img className="connect-launcher-icon" src={client.resource('assets/quiettv-assistant-launcher.png')} alt=""/>
    <h1>连接 Quiet TV</h1>
    {client.status==='pairing'?<form className="phone-pairing" onSubmit={event=>{event.preventDefault();void pairPhone();}}>
      <label htmlFor="phone-pairing-code">电视上的 8 位配对码</label>
      <input id="phone-pairing-code" type="text" inputMode="numeric" pattern="[0-9]{8}" maxLength={8} autoComplete="off"
        aria-describedby="pair-help" required value={pairingCode} disabled={pairingBusy} onChange={event=>{setPairingCode(event.target.value.replace(/[^0-9]/g,''));setPairingError('');}}/>
      <button type="submit" className="primary-button" disabled={pairingBusy||pairingCode.length!==8}>{pairingBusy?'正在配对…':'配对'}</button>
      <p id="pair-help" className="section-note">在电视打开「手机扫码设置」查看配对码。配对码过期时，请使用电视当前显示的码。</p>
      {pairingError&&<p className="form-error" role="alert">{pairingError}</p>}
    </form>:<p role="status">{client.problem||'正在连接电视…'}</p>}
    {['ended','offline'].includes(client.status)&&<button className="primary-button" disabled={reconnecting} onClick={()=>void client.reconnect()}>重新连接</button>}
  </main>;
  const overlay=view&&view!==pageView?view:undefined;
  const filePage=pageView?.kind==='tool'&&pageView.id==='files'&&fileSource(pageView)==='received';
  const mediaPage=pageView?.kind==='media';
  const uploadPage=pageView?.kind==='upload-options';
  const actionPage=pageView?.kind==='app-icon-batch'||pageView?.kind==='app-icons'||pageView?.kind==='weather-component';
  const mediaTarget=pageView?.id==='screensaver'?'screensaver':'';
  const mediaPickerDisabled=!!upload||!canWrite;
  const pageTitle=pageView?viewTitle(pageView):'';
  const syncControl=<button type="button" className={`sync-status ${syncStatus}`} aria-label={`同步状态  ${syncLabels[syncStatus]}`} aria-disabled={!reviewSync}
    onClick={()=>reviewSync&&open({kind:'sync',title:'核对设置'},true)}>
    <Icon name={syncStatus==='synced'?'CheckCircledIcon':syncStatus==='saving'?'UpdateIcon':'ExclamationTriangleIcon'}/>{syncLabels[syncStatus]}
  </button>;
  return <div ref={root} className="quiet-app" onPointerDownCapture={event=>{event.currentTarget.dataset.inputModality='pointer';}}
    onKeyDownCapture={event=>{event.currentTarget.dataset.inputModality='keyboard';}} data-ended={ended} data-sync-connection={sync.connection.state}
    data-sync-receipt={sync.receipt.state} data-restore-state={client.restoreStatus.phase} data-preview-page={previewOnPage} data-theme={light?'light':'dark'}>
    <header ref={header} className={pageView?'app-header subpage-header':'app-header'}>
      {pageView?<div className="subpage-heading">
        <button type="button" className="icon-button" aria-label="返回上一级" onClick={back}><Icon name="ChevronLeftIcon"/></button>
        <h1 ref={pageHeading} tabIndex={-1} title={pageTitle}>{pageTitle}</h1>
        {syncControl}
      </div>:<>
        <div className="device-line">
          <Icon name="DesktopIcon"/><span>{metadata.device.name}</span><span className="device-version">Quiet TV {metadata.device.version}</span>
          {syncControl}
          <button className="icon-button theme-button" aria-label="切换外观" onClick={()=>open({kind:'theme',title:'外观'},true)}><Icon name={light?'MoonIcon':'SunIcon'}/></button>
        </div>
        <div className="main-tab-carousel"><div className="tabs-track"><div role="tablist" aria-label="设置分类">
          <span className="tab-indicator" aria-hidden="true"/>
          {tabs.map((title,index)=><button key={title} role="tab" id={`setting-tab-${index}`} tabIndex={tab===index?0:-1} aria-selected={tab===index} aria-controls="setting-page"
            onClick={()=>changeTab(index)} onKeyDown={event=>{
              const next=event.key==='ArrowRight'?(index+1)%tabs.length:event.key==='ArrowLeft'?(index+tabs.length-1)%tabs.length:event.key==='Home'?0:event.key==='End'?tabs.length-1:-1;
              if(next<0)return;event.preventDefault();changeTab(next);document.getElementById(`setting-tab-${next}`)?.focus();
            }}>{title}</button>)}
        </div></div></div>
      </>}
    </header>
    <section ref={preview} className="pinned-desktop-preview" hidden={!previewOnPage} inert={!previewOnPage}>
      <div className="preview-label">
        <h2>{saverPreviewOnPage?'手机屏保预览':'手机桌面预览'}</h2>
        {previewOnPage&&!saverPreviewOnPage&&<div className="preview-actions">
          <button type="button" className="text-action desktop-refresh" aria-label="刷新桌面显示" aria-busy={desktopRefreshing}
            disabled={desktopRefreshing||busy||sync.saving||!canWrite||!!upload} onClick={()=>void refreshDesktop()}><Icon name="ReloadIcon"/>{desktopRefreshing?'刷新中…':'刷新'}</button>
          <PreviewPagePicker value={page} onChange={setPage}/>
        </div>}
      </div>
      {previewOnPage&&<ResourceImageRefresh.Provider value={desktopPreviewVersion}>
        {saverPreviewOnPage?<SaverPreview client={client} suspended={!!upload}/>:<Preview client={client} page={page} suspended={!!upload}/>}
      </ResourceImageRefresh.Provider>}
      {showDesktopTabs&&<div className="desktop-category-tabs">
        <div className="desktop-category-track" role="tablist" aria-label="桌面显示分组">
          <span className="tab-indicator" aria-hidden="true"/>
          {desktopTabs.map(([id,title],index)=><button type="button" key={id} role="tab" id={`desktop-category-${id}`}
            aria-controls="desktop-category-content" aria-selected={desktopCategory===id} tabIndex={desktopCategory===id?0:-1}
            onClick={()=>selectDesktopCategory(id)} onKeyDown={event=>{
              const next=event.key==='ArrowRight'?Math.min(index+1,desktopTabs.length-1):event.key==='ArrowLeft'?Math.max(index-1,0):
                event.key==='Home'?0:event.key==='End'?desktopTabs.length-1:-1;
              if(next<0)return;
              event.preventDefault();
              const category=desktopTabs[next][0];
              selectDesktopCategory(category);
              const target=document.getElementById(`desktop-category-${category}`);
              target?.focus({preventScroll:true});
            }}>{title}</button>)}
        </div>
      </div>}
    </section>
    <div ref={scroll} className="page-scroll" data-testid="mobile-scroll">
      <main id="setting-page" className={`page-content${pageView?' subpage-content':''}`} role={pageView?undefined:'tabpanel'} aria-labelledby={pageView?undefined:`setting-tab-${tab}`}>
        {(!overlay||confirmViews.has(overlay.kind)||overlay.kind.endsWith('-rename'))&&syncFeedback()}
        {!pageView&&missingSettings(metadata,v).length>0&&<button className="sync-banner" onClick={()=>open({kind:'configuration',title:'设置读取不完整'})}>
          <Icon name="ExclamationTriangleIcon"/><span>部分设置尚未完整读取</span><Icon name="ChevronRightIcon"/>
        </button>}
        {pageView?<>
          <fieldset className="connection-controls" disabled={ended||client.restoring}>{sheet(pageView)}</fieldset>
          {viewFeedback(pageView)}
        </>:<>
          {taskFeedback(undefined)}{!view&&notice&&noticePersistent&&<p className="section-note" role="status">{notice}</p>}
          {!view&&error&&<p className="form-error" role="alert">{error}</p>}
          <fieldset ref={content} className="connection-controls" disabled={ended||client.restoring}>
            {tab===0?<div id="desktop-category-content" role="tabpanel" aria-labelledby={`desktop-category-${desktopCategory}`}>{desktop()}</div>:tab===1?quick():tab===2?apps():tab===childTab?children():tab===toolsTab?tools():tab===generalTab?general():help()}
          </fieldset>
        </>}
      </main>
    </div>
    {(filePage||mediaPage||uploadPage)&&<footer ref={footer} className={mediaPage?'page-footer paired-actions':filePage?'page-footer file-transfer-footer':'page-footer'}>
      {uploadPage?<div ref={setUploadActionsHost}/>:mediaPage?<>
        <button type="button" className="primary-button" disabled={mediaPickerDisabled}
          onClick={()=>pickFile('wallpaper',mediaTarget)}>从相册选</button>
        <button type="button" className="secondary-button" disabled={mediaPickerDisabled}
          onClick={()=>pickFile('wallpaper',mediaTarget,true)}>从文件选择</button>
      </>:<button type="button" className="primary-button" disabled={busy||!!upload||!canWrite}
        onClick={()=>pickFile('file')}>发送到电视</button>}
    </footer>}
    {actionPage&&<footer ref={footer} className="page-footer action-page-footer"><div ref={setPageActionsHost}/></footer>}
    <input ref={fileInput} type="file" hidden onChange={event=>{
      const chosen=Array.from(event.target.files||[]),selected=chosen[0];event.target.value='';if(!selected)return;
      setFile(selected);
      if(fileIntent.current.kind==='wallpaper'){
        forms.current.delete('wallpaper-upload');
        wallpaperTarget.current=fileIntent.current.target;
        setWallpaperFiles(chosen);setUploadSelection(value=>value+1);
      }
      open({kind:fileIntent.current.kind==='wallpaper'?'upload-options':'image-upload',
        title:fileIntent.current.kind==='avatar'?'上传头像':fileIntent.current.kind==='icon'?'上传图标':fileIntent.current.kind==='file'?'发送文件':fileIntent.current.kind==='backup'?'导入备份':'上传素材'},true);
    }}/>
    {overlay?.kind==='media-rename'&&<MediaRename key={overlay.id} client={client} id={overlay.id!} drafts={forms.current} onCancel={back} onSaved={back}/>}
    {overlay?.kind==='app-rename'&&<AppRename key={overlay.id} client={client} id={overlay.id!} drafts={forms.current} onCancel={back} onSaved={back}/>}
    {overlay&&confirmViews.has(overlay.kind)&&confirmation(overlay)}
    {overlay&&(shortForms.has(overlay.kind)||overlay.kind==='parent'&&overlay.id==='verify')&&
      <FormDialog key={viewKey(overlay)} title={overlay.title} onClose={back} busy={busy}>
        <fieldset className="connection-controls" disabled={ended||client.restoring}>{sheet(overlay)}</fieldset>{viewFeedback(overlay)}
      </FormDialog>}
    {overlay&&overlay.kind!=='app-rename'&&overlay.kind!=='media-rename'&&!confirmViews.has(overlay.kind)&&!shortForms.has(overlay.kind)&&!(overlay.kind==='parent'&&overlay.id==='verify')&&
      <Sheet title={viewTitle(overlay)} nested={trail.length>1} closing={closing} pageKey={viewKey(overlay)} direction={sheetDirection.current}
        onBack={back} onClose={close} onExited={finishClose}
        className={overlay.kind==='movie-wall-provider'?'movie-wall-provider-sheet':undefined}
        footer={overlay.kind==='movie-wall-provider'?<div ref={setMovieWallFooterHost}/>:undefined}>
        {overlay.kind!=='sync'&&syncFeedback()}
        <fieldset className="connection-controls" disabled={overlay.kind!=='sync'&&(ended||client.restoring)}>{sheet(overlay)}</fieldset>
        {viewFeedback(overlay)}
      </Sheet>}
  </div>;
}
