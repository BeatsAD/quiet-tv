import { completedNotice, deferredNotice, operationLabel, quietResult, requiresTask, resultFields, taskNotice, type OperationNotice } from './operationResults';
export type Value = string | number | boolean | unknown[] | Record<string, unknown>;
export type Field = { domain: string; title: string; type: 'range'|'toggle'|'select'|'text'|'order'; min?:number; max?:number; step?:number; unit?:string; divisor?:number; options?:{value:string|number;title:string}[]; maxLength?:number; disabledReason?:string };
export type App = { id:string; name:string; originalName?:string; customName?:string; renamed?:boolean; canRename?:boolean; icon?:string; cover?:string; coverFit?:'contain'|'cover'; color?:string; version?:string; apkBytes?:number; system?:boolean; protected?:boolean; hidden?:boolean; disabled?:boolean; removed?:boolean;installed?:boolean;recoverable?:boolean;childHidden?:boolean;protection?:string; actions?:{id:string;title:string;danger?:boolean;reason?:string;explanation?:string}[] };
export type Media = { id:string; name:string; kind?:string; width:number; height:number; bytes:number; durationMs:number; video:boolean; canRename?:boolean };
export type Task = { id:string;domain:string;kind:string;title:string;state:string;message:string;progress:number;cancellable?:boolean;result?:Record<string,unknown> };
export type Metadata = { device:{name:string;version:string;build:number;sdk:number;channel:string}; fields:Record<string,Field>; wallpapers:Media[]; serverTimeMs?:number; timeZone?:string; pollIntervalMs:number; [key:string]:unknown };
export type Frame = { protocolVersion:number;generation:string;revision:string;lastSequence:number;complete:boolean;domains:Record<string,Record<string,Value>>;revisions:Record<string,string>;metadata?:Metadata;requestId?:string;requestStatus?:string;committedDomain?:string;committedRevision?:string;operation?:Record<string,unknown> };
type Receipt = { generation:string;lastSequence:number;sequence?:number;requestStatus:string;committedDomain?:string;committedRevision?:string;committedState?:Record<string,Value>;operation?:Record<string,unknown>;errorStatus?:number;message?:string };
type WriteRequest = { requestId:string;generation:string;sequence:number;domain:string;field:string;revision:string;value:Value };
type Attempt = { request:WriteRequest;version?:number;uncertain?:boolean;announce?:boolean;feedbackState?:string };
type Pending = { key:string;domain:string;value:Value;base:string;version:number;ready:boolean;error?:string;errorStatus?:number;timer?:ReturnType<typeof setTimeout> };
export class UploadError extends Error { constructor(public task:Task){super(task.message||'上传结果待核对');} }
export class ApiError extends Error { constructor(public status:number,message:string){super(message);} }
export class PhoneClient {
  frame:Frame|null=null;metadata:Metadata|null=null;status='waiting';problem='';pending=new Map<string,Pending>();
  onOperation?:(notice:OperationNotice)=>void;private resultTasks=new Map<string,{label:string;state:string}>();private deferredResults=new Set<string>();
  private listeners=new Set<()=>void>();private timer?:ReturnType<typeof setTimeout>;private sequence=0;private version=0;private running=false;private polling=false;private stopped=false;private requestCount=0;private readCount=0;private appliedRead=0;private needsMetadata=false;
  private reads:Promise<unknown>=Promise.resolve();
  private waitingCommands=0;private attempt?:Attempt;
  constructor(readonly base=new URL('./',location.href)){}
  subscribe=(listener:()=>void)=>{this.listeners.add(listener);return()=>{this.listeners.delete(listener);};};
  private publish(){this.listeners.forEach(listener=>listener());}
  resource(path:string){return new URL(path,this.base).href;}
  private fetch(path:string,body?:unknown):Promise<any>{
    if(body!==undefined)return this.fetchNow(path,body);
    const request=this.reads.then(()=>this.fetchNow(path));this.reads=request.catch(()=>{});return request;
  }
  private async fetchNow(path:string,body?:unknown){
    const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),10000);
    try{
      const response=await fetch(this.resource(path),{method:body===undefined?'GET':'POST',credentials:'same-origin',cache:'no-store',signal:controller.signal,headers:body===undefined?{}:{'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});
      if(response.status===204)return null;
      const data=await response.json();if(!response.ok)throw new ApiError(response.status,data.message||'操作未确认');return data;
    }catch(error){if(error instanceof ApiError)throw error;throw new Error(controller.signal.aborted?'电视响应超时，正在重新连接并核对结果':'暂时无法连接电视，正在重连，请保持手机与电视处于同一局域网');}finally{clearTimeout(timer);}
  }
  private merge(frame:Frame){
    if(this.frame&&this.frame.generation!==frame.generation){this.status='ended';this.problem='连接已更换，请重新扫码';this.publish();throw new ApiError(410,this.problem);}
    if(this.frame&&frame.lastSequence<this.frame.lastSequence)return;
    if(this.frame&&frame.domains.quiz?.quizGrade!==undefined&&frame.domains.quiz.quizGrade!==this.frame.domains.quiz?.quizGrade)this.needsMetadata=true;
    if(this.frame&&frame.domains.tasks){const old=this.tasks();const next=(frame.domains.tasks.jobs||[]) as unknown as Task[];if(next.some(task=>task.state==='completed'&&old.find(previous=>previous.id===task.id)?.state!=='completed'))this.needsMetadata=true;}
    if(this.frame&&frame.revisions.apps!==this.frame.revisions.apps)this.needsMetadata=true;
    if(this.frame&&['catalog','icons'].some(domain=>frame.revisions[domain]!==this.frame!.revisions[domain]))this.needsMetadata=true;
    if(this.frame&&['inputs','boot'].some(domain=>frame.revisions[domain]!==this.frame!.revisions[domain]))this.needsMetadata=true;
    if(this.frame&&frame.domains.wallpaper&&['wallMode','wallVideo','dailySource','previewKey'].some(key=>frame.domains.wallpaper[key]!==this.frame!.domains.wallpaper?.[key]))this.needsMetadata=true;
    if(this.frame&&frame.domains.greeting&&frame.domains.greeting.avatar!==this.frame.domains.greeting?.avatar)this.needsMetadata=true;
    this.frame={...frame,domains:frame.complete?frame.domains:{...this.frame?.domains,...frame.domains}};
    this.reportTasks();this.reportDeferred();
    this.sequence=Math.max(this.sequence,frame.lastSequence);if(frame.metadata){this.metadata={...frame.metadata,clockOffsetMs:Number(frame.metadata.serverTimeMs)-Date.now()};this.needsMetadata=false;}
  }
  private reportDeferred(){for(const key of this.deferredResults){const [domain,field]=key.split('/'),notice=deferredNotice(domain,field,this.frame?.domains[domain]||{});if(notice){this.deferredResults.delete(key);this.onOperation?.(notice);}}}
  private reportTasks(){for(const task of this.tasks()){const own=this.resultTasks.get(task.id);if(!own)continue;const state=`${task.state}:${!!task.result?.waitingTelevision}:${!!task.result?.requiresReview}`;if(own.state===state)continue;own.state=state;const notice=taskNotice(own.label,task);if(notice)this.onOperation?.(notice);if(['completed','failed','cancelled'].includes(task.state))this.resultTasks.delete(task.id);}}
  private reportResult(result:Receipt,attempt:Attempt){
    const {domain,field,value}=attempt.request;if(attempt.announce===false||domain==='icons'||domain==='uploads')return;
    const label=operationLabel(domain,field,this.metadata),operation=result.operation;if(quietResult(domain,field,value))return;
    if(operation?.endsConnection){this.onOperation?.(completedNotice(label,operation,domain,field));return;}
    if(requiresTask(domain,field)&&typeof operation?.taskId!=='string'){this.onOperation?.({kind:'info',title:'结果待核对',detail:'电视未返回操作任务，请重新读取当前状态'});return;}
    if(typeof operation?.taskId==='string'){this.resultTasks.set(operation.taskId,{label,state:''});this.reportTasks();return;}
    if(attempt.version!==undefined&&!resultFields.has(field))return;
    if(domain==='components'&&['start','uninstall'].includes(field)||domain==='update'&&['check','download','install'].includes(field)||domain==='inputs'&&field==='search')this.deferredResults.add(`${domain}/${field}`);
    if(domain==='update'&&field.startsWith('cancel'))this.deferredResults.delete(`update/${field==='cancelCheck'?'check':'download'}`);if(domain==='components'&&field==='cancel')this.deferredResults.delete('components/start');if(domain==='inputs'&&field==='cancel')this.deferredResults.delete('inputs/search');
    this.onOperation?.(completedNotice(label,operation,domain,field));this.reportDeferred();
  }
  private currentRead(read:number,version:number){return read>=this.appliedRead&&version===this.version&&!this.running&&this.status!=='ended';}
  private acceptRead(frame:Frame|null,read:number,version:number){if(!this.currentRead(read,version))return;this.appliedRead=read;if(frame)this.merge(frame);this.status='connected';if(!this.attempt)this.problem='';}
  value(key:string):Value|undefined{
    const pending=this.pending.get(key);if(pending)return pending.value;
    const field=this.metadata?.fields[key];return field?this.frame?.domains[field.domain]?.[key]:undefined;
  }
  values():Record<string,Value>{const values:Record<string,Value>={};for(const domain of Object.values(this.frame?.domains||{}))Object.assign(values,domain);for(const [key,pending] of this.pending)values[key]=pending.value;return values;}
  start(){this.stopped=false;document.addEventListener('visibilitychange',this.visibility);window.addEventListener('online',this.visibility);void this.poll();}
  stop(){this.stopped=true;clearTimeout(this.timer);document.removeEventListener('visibilitychange',this.visibility);window.removeEventListener('online',this.visibility);}
  private visibility=()=>{clearTimeout(this.timer);if(!document.hidden&&!this.stopped)void this.poll();};
  private async poll(){
    if(this.polling||this.stopped||document.hidden||this.status==='ended')return;this.polling=true;
    try{await this.refresh(false);}finally{this.polling=false;this.publish();if(!this.stopped&&this.status!=='ended')this.timer=setTimeout(()=>void this.poll(),1000);}
  }
  private handle(error:unknown){const e=error instanceof Error?error:new Error('连接中断');if(this.status==='ended')return;this.problem=e.message;if(error instanceof ApiError&&[403,410].includes(error.status))this.status='ended';else if(error instanceof ApiError&&error.status===409)this.status='conflict';else this.status='offline';}
  get restoring(){return !!this.frame?.domains.backup?.activeRestore&&Object.keys(this.frame.domains.backup.activeRestore as object).length>0;}
  update(key:string,value:Value,delay=0,expected?:string){
    const field=this.metadata?.fields[key];if(!field||!this.frame||this.status==='ended'||this.restoring)return;
    const previous=this.pending.get(key);clearTimeout(previous?.timer);
    const pending:Pending={key,domain:field.domain,value,base:expected||previous?.base||this.frame.revisions[field.domain],version:++this.version,ready:delay===0};
    this.pending.set(key,pending);if(delay)pending.timer=setTimeout(()=>{pending.ready=true;void this.drain();},delay);
    this.publish();void this.drain();
  }
  flush(key:string){const pending=this.pending.get(key);if(pending){clearTimeout(pending.timer);pending.ready=true;void this.drain();}}
  private acceptReceipt(result:Receipt,attempt:Attempt){
    const request=attempt.request;if(result.generation!==this.frame?.generation)throw new ApiError(410,'连接已更换，请重新扫码');
    if(result.requestStatus==='rejected')throw new ApiError(result.errorStatus||400,result.message||'电视未接受此操作，请核对设置');
    if(result.requestStatus!=='completed')throw new ApiError(500,'电视尚未确认操作结果，请先核对当前设置');
    // Older TVs return a full frame; new receipts carry only the committed domain.
    if('domains' in result)this.merge(result as Frame);
    else if(this.frame){
      if(result.committedState&&result.committedDomain&&result.committedRevision&&(result.sequence??result.lastSequence)===result.lastSequence&&result.lastSequence>=this.frame.lastSequence){
        this.merge({...this.frame,metadata:undefined,lastSequence:result.lastSequence,domains:{...this.frame.domains,[result.committedDomain]:result.committedState},revisions:{...this.frame.revisions,[result.committedDomain]:result.committedRevision}});
      }
      this.sequence=Math.max(this.sequence,result.lastSequence);
    }
    const committed=result.committedRevision||this.frame?.revisions[request.domain];
    if(committed)for(const item of this.pending.values())if(item.domain===request.domain&&item.base===request.revision)item.base=committed;
    if(attempt.version!==undefined&&this.pending.get(request.field)?.version===attempt.version)this.pending.delete(request.field);
    this.attempt=undefined;this.status='connected';this.problem='';
    this.reportResult(result,attempt);
    if(result.operation?.endsConnection){this.status='ended';this.problem='恢复请求已受理，本次连接已结束。请重新扫码核对电视恢复记录。';}
  }
  private rejectAttempt(error:unknown,attempt:Attempt){
    const detail=error instanceof Error?error.message:'操作结果待核对',known=error instanceof ApiError&&error.status<500,key=`${known}:${detail}`;if(attempt.announce!==false&&attempt.request.domain!=='icons'&&attempt.request.domain!=='uploads'&&attempt.feedbackState!==key){attempt.feedbackState=key;this.onOperation?.({kind:known?'error':'info',title:known?`${operationLabel(attempt.request.domain,attempt.request.field,this.metadata)}失败`:'结果待核对',detail});}
    if(error instanceof ApiError&&error.status<500){
      if(attempt.version!==undefined){const item=this.pending.get(attempt.request.field);if(item?.version===attempt.version){item.error=error.message;item.errorStatus=error.status;}}
      this.attempt=undefined;
    }else attempt.uncertain=true;
    this.handle(error);
  }
  private async receipt(attempt:Attempt){
    try{return await this.fetch(`api/requests/${attempt.request.requestId}`) as Receipt;}
    catch(error){
      // Absence permits only the identical request: the TV deduplicates its ID/sequence.
      if(error instanceof ApiError&&error.status===404)return await this.fetch('api/settings',attempt.request) as Receipt;
      throw error;
    }
  }
  private async send(attempt:Attempt){
    this.attempt=attempt;
    try{
      let result:Receipt;
      try{result=await this.fetch('api/settings',attempt.request);}catch(error){if(error instanceof ApiError&&error.status<500)throw error;attempt.uncertain=true;result=await this.receipt(attempt);}
      this.acceptReceipt(result,attempt);return result;
    }catch(error){this.rejectAttempt(error,attempt);throw error;}
  }
  private async reconcile(){
    const attempt=this.attempt;if(!attempt||this.running||this.status==='ended')return;
    this.running=true;this.version++;
    try{this.acceptReceipt(await this.receipt(attempt),attempt);}catch(error){this.rejectAttempt(error,attempt);}
    finally{this.running=false;this.publish();}
  }
  private async drain(){
    if(this.running||this.attempt||this.waitingCommands||!this.frame||this.status==='ended'||this.restoring)return;
    const pending=[...this.pending.values()].find(item=>item.ready&&!item.error);if(!pending)return;
    this.running=true;this.version++;
    const request={requestId:`phone_${Date.now().toString(36)}_${++this.requestCount}`,generation:this.frame.generation,sequence:++this.sequence,domain:pending.domain,field:pending.key,revision:pending.base,value:pending.value};
    this.publish();
    try{await this.send({request,version:pending.version});}catch{}finally{this.running=false;this.publish();void this.drain();}
  }
  async refresh(full=true){
    if(this.attempt)await this.reconcile();if(this.running||this.status==='ended')return;
    const read=++this.readCount,version=this.version;
    try{
      if(!this.frame&&!full){const status=await this.fetch('status');if(status.state!=='connected')return;}
      const frame:Frame|null=await this.fetch(full||!this.frame||this.needsMetadata?'api/bootstrap':`api/state?since=${encodeURIComponent(this.frame.revision)}`);
      this.acceptRead(frame,read,version);
      if(this.needsMetadata&&this.currentRead(read,version))this.acceptRead(await this.fetch('api/bootstrap'),read,version);
    }catch(error){if(this.currentRead(read,version))this.handle(error);}
    finally{this.publish();void this.drain();}
  }
  async refreshNow(){await this.refresh();this.onOperation?.(this.status==='connected'?{kind:'success',title:'状态已更新'}:{kind:'error',title:'刷新失败',detail:this.problem||'请重新连接电视后重试'});}
  async useTelevision(){while(this.running)await new Promise(resolve=>setTimeout(resolve,50));this.version++;this.attempt=undefined;for(const pending of this.pending.values())clearTimeout(pending.timer);this.pending.clear();await this.refresh();}
  async retry(){await this.refresh();if(!this.frame||this.attempt||this.status!=='connected')return;for(const pending of this.pending.values()){pending.base=this.frame.revisions[pending.domain];pending.error=undefined;pending.errorStatus=undefined;pending.ready=true;}void this.drain();}
  async command(domain:string,field:string,value:Value,expected?:string,announce=true){
    this.waitingCommands++;try{while(this.running)await new Promise(resolve=>setTimeout(resolve,50));}finally{this.waitingCommands--;}
    const problem=!this.frame||this.status==='ended'?'连接已结束':this.attempt?'上次操作结果待确认，请先核对设置':this.restoring?'桌面正在恢复，请等待完成后核对配置':'';if(problem){if(announce&&domain!=='icons')this.onOperation?.({kind:'info',title:'操作尚未提交',detail:problem});throw new Error(problem);}this.running=true;this.version++;this.publish();
    const request={requestId:`action_${Date.now().toString(36)}_${++this.requestCount}`,generation:this.frame!.generation,sequence:++this.sequence,domain,field,revision:expected??this.frame!.revisions[domain],value};
    try{return await this.send({request,announce});}finally{this.running=false;this.publish();void this.drain();}
  }
  tasks():Task[]{return (this.frame?.domains.tasks?.jobs||[]) as unknown as Task[];}
  async places(query:string){const result=await this.fetch(`api/places?q=${encodeURIComponent(query)}`);return result.places as {code:string;name:string;area:string}[];}
  async parent(action:string,pin='',newPin=''){
    const generation=this.frame?.generation;
    try{
      const result=await this.fetch('api/parent',{action,pin,newPin});
      if(this.status==='ended'||this.frame?.generation!==generation)throw new ApiError(410,'连接已更换，请重新扫码');
      if(this.frame&&typeof result.authorized==='boolean'){
        // Ignore reads started before this confirmed grant; they may still report it as unauthorized.
        this.version++;
        this.frame={...this.frame,domains:{...this.frame.domains,parent:{configured:result.configured,authorized:result.authorized,retrySeconds:result.retrySeconds}}};
        this.status='connected';if(!this.attempt)this.problem='';this.publish();
      }
      if(['setup','change','recover','verify'].includes(action))this.onOperation?.({kind:'success',title:action==='verify'?'家长验证成功':action==='change'?'密码修改成功':action==='recover'?'密码重设成功':'密码设置成功'});
      void this.refresh();
      return result as {recovery?:string;authorized?:boolean};
    }catch(error){this.onOperation?.({kind:'error',title:action==='verify'?'家长验证失败':'密码设置失败',detail:error instanceof Error?error.message:'请重试'});throw error;}
  }
  async upload(file:File,kind:string,apply:boolean,target:string,onProgress:(percent:number,stage:string)=>void,onReady:(cancel:()=>Promise<void>)=>void,displayName?:string){
    const scene=target==='screensaver'?`screensaver:${this.frame?.revisions.screensaver}`:target;
    const started=await this.command('uploads','begin',{kind,name:file.name,mime:file.type,target:scene,bytes:file.size,apply,manualVersion:Number(this.values().manualVersion||0),...(displayName===undefined?{}:{displayName})});
    const id=String(started.operation?.uploadId||'');if(!id)throw new Error('电视未返回上传凭据');
    const xhr=new XMLHttpRequest();onReady(async()=>{await this.command('uploads','cancel',id);const task=this.tasks().find(task=>task.id===id);if(task?.state!=='completed')xhr.abort();});
    try{
      await new Promise<void>((resolve,reject)=>{
        xhr.open('POST',this.resource(`api/uploads/${id}`));xhr.withCredentials=true;xhr.timeout=30*60*1000;xhr.setRequestHeader('Content-Type','application/octet-stream');
        xhr.upload.onprogress=event=>{if(event.lengthComputable){const percent=Math.round(event.loaded/event.total*100);onProgress(percent,percent===100?'正在校验素材':'正在上传');}};
        xhr.onload=()=>xhr.status>=200&&xhr.status<300?resolve():reject(new ApiError(xhr.status,'上传结果待核对'));
        xhr.onerror=()=>reject(new Error('上传连接中断'));xhr.ontimeout=()=>reject(new Error('上传结果待核对'));xhr.onabort=()=>reject(new Error('已请求取消上传'));xhr.send(file);
      });
    }catch(error){const task:Task=await this.fetch(`api/tasks/${id}`);await this.refresh();if(task.state==='completed')return task;throw new UploadError(task);}
    const task:Task=await this.fetch(`api/tasks/${id}`);await this.refresh();if(task.state!=='completed')throw new UploadError(task);return task;
  }
  get sync(){return this.status==='ended'?'ended':[...this.pending.values()].some(p=>p.error)?[...this.pending.values()].some(p=>p.errorStatus===409)?'conflict':'failed':this.status==='offline'?'offline':this.attempt?.uncertain?'checking':this.pending.size||this.running?'saving':'synced';}
}

export function newId(){const bytes=crypto.getRandomValues(new Uint8Array(16));bytes[6]=(bytes[6]&15)|64;bytes[8]=(bytes[8]&63)|128;const hex=[...bytes].map(value=>value.toString(16).padStart(2,'0')).join('');return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;}
