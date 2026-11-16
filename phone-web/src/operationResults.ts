import type { Metadata, Task, Value } from './api';

export type OperationNotice={kind:'success'|'error'|'info';title:string;detail?:string};
export const resultFields=new Set(['wallpaper','saverMedia','greeting','cityCode','homeButtons','dockApps','gridOrder','boot']);
const labels:Record<string,Record<string,string>>={
  catalog:{renameMedia:'重命名'},apps:{renameApp:'重命名',refresh:'刷新应用列表',open:'打开应用',restoreCover:'恢复默认封面',boot:'设置开机应用',hide:'隐藏应用',show:'恢复应用显示',dockAdd:'添加到首页',dockRemove:'移除首页位置',UNINSTALL:'卸载应用',systemUninstall:'卸载应用',DISABLE:'停用应用',ENABLE:'启用应用',RESTORE:'恢复应用'},
  wallpaper:{removeMedia:'删除素材',dailyRefresh:'刷新壁纸',dailySource:'切换壁纸来源',wallpaper:'应用壁纸'},screensaver:{saverMedia:'应用屏保素材'},greeting:{greeting:'保存问候语'},
  quick:{quickControls:'保存快捷控制',reset:'恢复默认快捷控制',run:'发送电视操作'},buttons:{homeButtons:'保存首页按钮'},dock:{dockApps:'保存 Dock 应用',gridOrder:'保存应用顺序'},
  child:{rule:'保存儿童设置',grant:'追加使用时间',permission:'打开计时授权',quizNow:'发送答题请求'},quiz:{quizSettings:'保存答题设置',quizAppSettings:'保存答题设置',selectedCustomIds:'保存所选题目',question:'保存题目',deleteQuestion:'删除题目'},
  memos:{save:'保存备忘录',remove:'删除备忘录',toggle:'更新备忘录'},files:{receive:'保存文件',remove:'删除文件',wallpaper:'应用壁纸',install:'安装应用',permission:'打开目录授权'},
  backup:{create:'创建备份',export:'导出备份',prepareFromFile:'校验备份',admit:'导入备份',restore:'恢复备份',retry:'核对恢复记录'},
  weather:{refresh:'刷新天气',cityCode:'保存天气城市'},diagnostics:{export:'生成诊断文件'},management:{connect:'连接 ADB',disconnect:'断开 ADB',check:'检查 ADB 状态'},
  guard:{pause:'暂停桌面接管',restore:'恢复原桌面',system:'打开默认桌面设置'},desktopSwitch:{switch:'切换桌面',manage:'桌面管理',verify:'记录使用验证'},
  inputs:{search:'搜索信号源',test:'发送信号源测试',confirm:'保存配对结果',restore:'恢复配对配置',defaults:'恢复配对默认设置',observe:'记录信号源',cancel:'结束信号源任务'},
  update:{check:'检查更新',download:'下载安装包',install:'准备安装更新',cancelCheck:'取消更新检查',cancelDownload:'取消安装包下载'},components:{start:'下载天气资源',cancel:'取消组件下载'}
};
export function operationLabel(domain:string,field:string,metadata:Metadata|null){return labels[domain]?.[field]||metadata?.fields[field]?.title||'保存设置';}
export function quietResult(domain:string,field:string,value:Value){return domain==='icons'||domain==='uploads'||domain==='files'&&field==='browse'||domain==='memos'&&field==='toggle'||domain==='backup'&&field==='automatic'||domain==='quiz'&&field==='quizApp'||domain==='child'&&field==='rule'&&['enabled','locked'].includes(String((value as Record<string,unknown>)?.field));}
export function requiresTask(domain:string,field:string){return domain==='desktopSwitch'||domain==='guard'&&['pause','restore'].includes(field)||domain==='apps'&&field!=='renameApp'||domain==='wallpaper'&&['dailyRefresh','dailySource'].includes(field)||domain==='weather'&&field==='refresh'||domain==='backup'&&['create','export','prepareFromFile','admit'].includes(field)||domain==='diagnostics'&&field==='export'||domain==='files'&&['receive','wallpaper','install'].includes(field);}
export function completedNotice(label:string,operation?:Record<string,unknown>,domain='',field=''):OperationNotice{
  const detail=typeof operation?.message==='string'?operation.message:undefined;
  if(domain==='apps'&&field==='renameApp'&&operation?.persisted!==true||domain==='catalog'&&field==='renameMedia'&&operation?.renamed!==true)return {kind:'info',title:'名称保存结果待核对'};
  if(operation?.endsConnection)return {kind:'info',title:'恢复请求已受理',detail:'请重新扫码核对恢复或回滚结果'};
  if(operation?.state==='waitingTelevision'||operation?.waitingTelevision||domain==='child'&&field==='permission')return {kind:'info',title:'请在电视继续操作',detail};
  if(domain==='apps'&&field==='open')return {kind:'info',title:'打开请求已发送',detail};
  if(domain==='components'||domain==='update'&&['check','download','install'].includes(field)||domain==='management'&&field==='connect'||domain==='inputs'&&['search','test','observe'].includes(field))return {kind:'info',title:domain==='update'&&field==='install'?'请在电视继续安装':'请求已受理',detail};
  return {kind:'success',title:`${label}成功`,detail};
}
export function taskNotice(label:string,task:Task):OperationNotice|null{
  if(task.state==='unknown'||task.result?.requiresReview)return {kind:'info',title:'结果待核对',detail:task.message};
  if(task.result?.waitingTelevision)return {kind:'info',title:'请在电视继续操作',detail:task.message};
  if(task.state==='failed')return {kind:'error',title:`${label}失败`,detail:task.message};
  if(task.state==='cancelled')return {kind:'info',title:'操作已取消',detail:task.message};
  if(task.state!=='completed')return null;
  if(task.domain==='apps'&&task.kind==='open')return {kind:'info',title:'打开请求已发送',detail:task.message};
  if(task.result?.download)return {kind:'success',title:'文件已生成',detail:'可下载到手机'};
  if(task.result?.draftId)return {kind:'success',title:'备份校验完成',detail:'请核对内容后确认导入'};
  if(task.result?.offline)return {kind:'info',title:'正在使用缓存',detail:task.message};
  if(task.result?.unchanged)return {kind:'info',title:'暂无新内容',detail:task.message};
  if(task.message.startsWith('已请求'))return {kind:'info',title:'请求已发送',detail:task.message};
  return {kind:'success',title:`${label}成功`,detail:task.message};
}

export function deferredNotice(domain:string,field:string,values:Record<string,Value>):OperationNotice|null{
  if(domain==='components'){
    const weather=values.weather as Record<string,unknown>|undefined,state=weather?.state,detail=String(weather?.message||'');
    if(state==='ready')return {kind:'success',title:'天气资源已就绪'};
    if(state==='failed')return {kind:'error',title:'天气资源下载失败',detail};
    if(state==='cancelled')return {kind:'info',title:'下载已取消'};
  }
  if(domain==='update'){
    if(field==='check'&&!values.updateChecking&&values.updateLoaded){
      if(values.updateUnavailable)return {kind:'info',title:'当前无法在线更新',detail:String(values.updateUnavailable)};
      if(values.updateCheckError)return {kind:'error',title:'检查更新失败',detail:String(values.updateCheckError)};
      const available=values.updateAvailable as Record<string,unknown>|undefined;return available?{kind:'success',title:'发现新版本',detail:String(available.version)}:{kind:'success',title:'当前渠道暂无新版本'};
    }
    if(field==='download'&&!values.updateDownloading){
      if(values.updateDownloadError)return {kind:'error',title:'安装包下载失败',detail:String(values.updateDownloadError)};
      if(values.updateReady)return {kind:'success',title:'安装包已下载并校验',detail:'请在电视继续安装'};
    }
    if(field==='install'){
      if(values.updateInstallState==='success')return {kind:'success',title:'更新安装成功'};
      if(values.updateInstallError)return {kind:'error',title:'安装结果需核对',detail:String(values.updateInstallError)};
      if(['confirmation','file_dispatched'].includes(String(values.updateInstallState)))return {kind:'info',title:'请在电视确认安装'};
    }
  }
  if(domain==='inputs'&&field==='search'){
    if(values.searchState==='COMPLETE')return {kind:'success',title:'信号源搜索完成'};
    if(['FAILED','TIMEOUT'].includes(String(values.searchState)))return {kind:'error',title:'信号源搜索未完成',detail:String(values.error||values.stage||'请重试')};
  }
  return null;
}
