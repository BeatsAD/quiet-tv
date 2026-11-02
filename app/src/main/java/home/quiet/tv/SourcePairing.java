package home.quiet.tv;

import android.content.*;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** One process-owned pairing session, used by the remote UI and the authenticated computer bridge. */
final class SourcePairing {
    interface Prepared {void accept(InputPairingState receipt,SavedInputLaunch launch);}
    private static SourcePairing instance;
    static synchronized SourcePairing get(Context context){if(instance==null)instance=new SourcePairing(context.getApplicationContext());return instance;}
    static synchronized SourcePairing existing(){return instance;}
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final SourcePairingStore store;
    private final List<SourceSearch.Candidate> candidates=new ArrayList<>();
    private JSONObject channels=new JSONObject();
    private String searchId="",searchState="IDLE",stage="",owner="",error="";
    private Future<?> work;
    private InputPairingState test;
    private boolean saving;
    private Runnable searchTimeout;
    private SourcePairing(Context context){this.context=context;store=new SourcePairingStore(context);}
    private static long now(){return SystemClock.elapsedRealtime();}
    private boolean occupied(){
        if(test!=null)test.expire(now());
        return saving||"SEARCHING".equals(searchState)||test!=null&&(test.pending(now())||"DISPATCHING".equals(test.state)
            ||"DISPATCHED_UNVERIFIED".equals(test.state)||"CONFIRMED".equals(test.state))||InputSourceObservation.busy();
    }
    synchronized boolean blocksExternalInput(){return occupied();}
    private void requireIdle() {if(occupied()||SystemEntryMaintenance.inputBusy()||DeviceInputProfile.hasLiveRequest())throw new IllegalStateException("另一项信号源任务尚未结束，请先确认或停止");}
    synchronized List<SourceSearch.Candidate> candidates(){return new ArrayList<>(candidates);}
    synchronized SourceSearch.Candidate candidate(String id){for(SourceSearch.Candidate item:candidates)if(item.id.equals(id))return item;return null;}
    synchronized boolean pending(){return test!=null&&test.pending(now());}
    synchronized String pendingRequest(){return test!=null&&test.pending(now())?test.request:"";}
    synchronized void cancelPreparation(String token){
        if(test!=null&&test.request.equals(token)&&test.pending(now())){test.cancel();if(work!=null)work.cancel(true);record();}
    }
    synchronized String owner(){return owner;}
    synchronized String search(String actor)throws Exception{
        requireIdle();owner=actor;error="";test=null;candidates.clear();channels=new JSONObject();
        searchId=UUID.randomUUID().toString();searchState="SEARCHING";stage="正在读取电视输入";final String id=searchId;
        if(searchTimeout!=null)main.removeCallbacks(searchTimeout);
        searchTimeout=()->{synchronized(SourcePairing.this){if(id.equals(searchId))cancelSearch("TIMEOUT");}};main.postDelayed(searchTimeout,30000);
        work=worker.submit(()->{
            try{
                SourceSearch.collect(context,(phase,values,quality)->{
                    synchronized(SourcePairing.this){
                        if(!id.equals(searchId)||!"SEARCHING".equals(searchState)||Thread.currentThread().isInterrupted())return false;
                        stage=phase;candidates.clear();candidates.addAll(values);try{channels=new JSONObject(quality.toString());}catch(JSONException invalid){throw new IllegalStateException(invalid);}return true;
                    }
                });
                synchronized(SourcePairing.this){if(id.equals(searchId)&&"SEARCHING".equals(searchState)){searchState="COMPLETE";stage="搜索完成";}}
            }catch(Exception failure){synchronized(SourcePairing.this){if(id.equals(searchId)&&"SEARCHING".equals(searchState)){searchState="FAILED";error=InputAdb.quality(failure);}}}
            finally{synchronized(SourcePairing.this){if(id.equals(searchId)){main.removeCallbacks(searchTimeout);record();}}}
        });return id;
    }
    private synchronized void cancelSearch(String reason){
        if(!"SEARCHING".equals(searchState))return;
        searchState=reason;stage="TIMEOUT".equals(reason)?"搜索超时，已保留部分结果":"搜索已停止";
        if(work!=null)work.cancel(true);main.removeCallbacks(searchTimeout);record();
    }
    synchronized void cancel(String actor,boolean userStop){
        if(!userStop&&!owner.isEmpty()&&!owner.equals(actor))throw new IllegalStateException("请勿停止其他来源的任务");
        cancelSearch("CANCELLED");if(test!=null)test.cancel();if(work!=null&&!saving)work.cancel(true);
        InputSourceObservation.stop("cancelled");record();
    }
    synchronized void cancelSearchFromUi(){if("tv".equals(owner))cancelSearch("CANCELLED");}
    synchronized String request(String actor,String id,String scope,String expected,String label)throws Exception{
        requireIdle();SourceSearch.Candidate selected=candidate(id);
        if(selected==null||!selected.scope.equals(scope)||!store.revision().equals(expected))throw new IllegalStateException("搜索结果或配置已变化，请重新读取");
        if(label!=null&&(!label.isEmpty()&&(label.length()>40||label.matches("(?s).*[\\p{Cntrl}].*"))))throw new IllegalArgumentException("名称无效");
        owner=actor;error="";test=new InputPairingState(UUID.randomUUID().toString(),actor,id,scope,expected,now());test.label=label==null?"":label;
        try{context.startActivity(new Intent(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
        catch(RuntimeException failure){test.state="FAILED";record();throw failure;}record();return test.request;
    }
    void preparePending(Prepared callback){
        final InputPairingState receipt;
        synchronized(this){if(test==null||!"PENDING".equals(test.state)||!test.pending(now()))return;receipt=test;receipt.state="PREPARING";}
        work=worker.submit(()->{
            SavedInputLaunch launch=null;
            try{
                SourceSearch.Candidate fresh=findCurrent(receipt.candidate);
                if(fresh==null||!fresh.scope.equals(receipt.scope)||!store.revision().equals(receipt.revision))throw new IllegalStateException("入口已变化");
                launch=fresh.prepare(context,receipt);
            }catch(Exception failure){DiagnosticLog.error("input.pairing.prepare_failed",failure);}
            final SavedInputLaunch prepared=launch;
            main.post(()->{
                synchronized(SourcePairing.this){
                    receipt.expire(now());if(test!=receipt||!"PREPARING".equals(receipt.state))return;
                    if(prepared==null){receipt.state="FAILED";error="入口暂不可用，请重新检测";record();return;}
                }
                callback.accept(receipt,prepared);
            });
        });
    }
    void dispatch(Context activity,InputPairingState receipt,SavedInputLaunch launch,Runnable finished){
        synchronized(this){
            receipt.expire(now());if(test!=receipt||!"PREPARING".equals(receipt.state)){main.post(finished);return;}
            try{if(!receipt.revision.equals(store.revision())||!launch.current(activity))throw new IllegalStateException("入口或配置已变化");}
            catch(Exception changed){receipt.state="FAILED";error="入口或配置已变化，请重新测试";record();main.post(finished);return;}
            receipt.state="DISPATCHING";
        }
        HomeGuardService.bootSourceUserAction(activity,"input_pairing_test");HomeGuardService.savedInputSelected(activity);
        Runnable dispatch=()->{
            synchronized(SourcePairing.this){if(test!=receipt||!"DISPATCHING".equals(receipt.state)){main.post(finished);return;}}
            boolean opened=launch.dispatch(activity);
            if(!opened&&Build.VERSION.SDK_INT>=21&&Api21.needsChannels(launch)){
                work=worker.submit(()->{
                    SavedInputLaunch channels=null;try{channels=Api21.channels(activity.getApplicationContext(),launch);}catch(Exception failure){receipt.failure="CHANNEL_QUERY_FAILED";}
                    SavedInputLaunch prepared=channels;
                    main.post(()->{
                        synchronized(SourcePairing.this){if(test!=receipt||!"DISPATCHING".equals(receipt.state)){finished.run();return;}}
                        boolean result=prepared!=null&&prepared.current(activity)&&prepared.dispatch(activity);
                        if(!result&&prepared!=null)receipt.failure=Api21.issue(prepared);
                        completeDispatch(receipt,result,finished);
                    });
                });return;
            }
            if(!opened&&Build.VERSION.SDK_INT>=21&&receipt.failure.isEmpty())receipt.failure=Api21.issue(launch);
            completeDispatch(receipt,opened,finished);
        };
        if(launch.blocking())work=worker.submit(dispatch);else dispatch.run();
    }
    private void completeDispatch(InputPairingState receipt,boolean opened,Runnable finished){
        synchronized(this){if(test==receipt){receipt.dispatched(opened,now());
            if(!opened)error="ADB_UNAVAILABLE".equals(receipt.failure)?"本机调试连接不可用，原配置已保留":"RESTRICTED".equals(receipt.failure)?"系统拒绝此调用方式，原配置已保留"
                :"PERMISSION".equals(receipt.failure)?"系统未授予电视频道读取权限，请使用原厂信号源菜单":"NO_CHANNELS".equals(receipt.failure)?"尚未配置电视频道，请在原厂设置中搜台":"此方式未能打开，请尝试其他候选";
            record();}}
        if(Looper.myLooper()==Looper.getMainLooper())finished.run();else main.post(finished);
    }
    @android.annotation.TargetApi(21)
    private static final class Api21 {
        static boolean needsChannels(SavedInputLaunch launch){return launch instanceof InputLaunch&&((InputLaunch)launch).channelsPending;}
        static SavedInputLaunch channels(Context context,SavedInputLaunch launch)throws Exception{return ((InputLaunch)launch).channels(context);}
        static String issue(SavedInputLaunch launch){return launch instanceof InputLaunch?((InputLaunch)launch).issue.name():"";}
    }
    private SourceSearch.Candidate findCurrent(String id)throws Exception{
        SourceSearch.Candidate selected=candidate(id);if(selected==null)return null;
        if(selected.panel!=null){
            JSONObject entry=new JSONObject().put("id",SystemEntryMaintenance.panelId(selected.panel)).put("scope",selected.scope).put("transport",selected.transport);
            return preferred(context,entry);
        }
        if(selected.target!=null)return selected.target.availableNow(context)?selected:null;
        SavedInputLaunch prepared=selected.prepare(context,null);return prepared!=null&&prepared.current(context)?selected:null;
    }
    synchronized void confirm(String actor,String token,boolean worked,boolean physical,String source,String observed)throws Exception{
        if(saving||test==null||!actor.equals(test.owner)||!token.equals(test.request))throw new IllegalStateException("测试已变化或正在保存");
        final InputPairingState receipt=test;receipt.expire(now());
        if(!"DISPATCHED_UNVERIFIED".equals(receipt.state))throw new IllegalStateException("此测试尚不能确认");
        if(!worked){receipt.confirm(token,receipt.scope,store.revision(),false,source,observed,now());record();return;}
        SourceSearch.Candidate selected=candidate(receipt.candidate);
        if(selected==null)throw new IllegalStateException("候选已失效");
        if(selected.panel==null&&(!physical||!"user".equals(source)))throw new IllegalArgumentException("端口需要用户确认画面和声音");
        saving=true;error="";
        work=worker.submit(()->{
            try{
                SourceSearch.Candidate fresh=findCurrent(receipt.candidate);
                if(fresh==null)throw new IllegalStateException("入口已消失");
                synchronized(SourcePairing.this){
                    if(test!=receipt)throw new IllegalStateException("测试已变化");
                    receipt.confirm(token,fresh.scope,store.revision(),true,source,observed,now());
                    if(fresh.panel!=null){
                        JSONObject entry=new JSONObject().put("id",SystemEntryMaintenance.panelId(fresh.panel)).put("scope",fresh.scope)
                            .put("transport",fresh.transport).put("source",source).put("request",receipt.request).put("confirmedAt",System.currentTimeMillis());
                        store.saveEntry(entry,receipt.revision);
                    }else if(fresh.target!=null){
                        String label=receipt.label.isEmpty()?fresh.label:receipt.label;
                        JSONObject labels=store.value().optJSONObject("labels");
                        if(labels!=null&&!receipt.label.isEmpty())for(Iterator<String> ids=labels.keys();ids.hasNext();){
                            String other=ids.next();if(!other.equals(fresh.sourceId)&&label.equals(store.label(other)))throw new IllegalStateException("此名称已由其他配对端口使用，请先校准该端口或恢复配对默认设置");
                        }
                        store.commit(null,fresh.target,label,fresh.sourceId,fresh.owner,receipt.revision);
                    }
                    receipt.savedKind=fresh.panel!=null?"entry":fresh.target!=null?"hdmi":"observed_input";receipt.state="SAVED";
                }
            }catch(Exception failure){synchronized(SourcePairing.this){if(test==receipt){receipt.state="FAILED";error=failure.getMessage()==null?"保存失败，原配置保留":failure.getMessage();}}DiagnosticLog.error("input.pairing.save_failed",failure);}
            finally{synchronized(SourcePairing.this){saving=false;record();}}
        });
    }
    synchronized String observe(String actor,boolean raw)throws Exception{
        requireIdle();owner=actor;return InputSourceObservation.start(context,raw);
    }
    void restore(String actor,String expected,boolean defaults)throws Exception{
        synchronized(this){requireIdle();owner=actor;saving=true;error="";test=null;}
        worker.execute(()->{try{store.restore(expected,defaults);}catch(Exception failure){synchronized(this){error=failure.getMessage();}}
            finally{synchronized(this){saving=false;record();}}});
    }
    synchronized JSONObject snapshot()throws Exception{
        if(test!=null)test.expire(now());JSONArray values=new JSONArray();for(SourceSearch.Candidate candidate:candidates)values.put(candidate.json().put("confirmed",SourceSearch.confirmed(context,candidate)));
        JSONObject result=new JSONObject().put("schemaVersion",1).put("owner",owner).put("searchId",searchId).put("searchState",searchState)
            .put("stage",stage).put("channels",channels).put("candidates",values).put("revision",store.revision()).put("configuration",store.value())
            .put("watch",store.watchSnapshot()).put("previousConfiguration",store.previousSummary()).put("baseProfile",new DeviceInputProfile(context).snapshot())
            .put("saving",saving).put("error",error).put("observation",InputSourceObservation.status(context));
        if(test!=null)result.put("test",new JSONObject().put("request",test.request).put("candidate",test.candidate).put("state",test.state)
            .put("label",test.label).put("savedKind",test.savedKind).put("failure",test.failure).put("source",test.source).put("observed",test.observed).put("scope",test.scope));
        return result;
    }
    private void record(){try{
        JSONObject value=snapshot();InputSourceObservation.savePairing(context,value);
        DiagnosticLog.info("input.pairing.state","search="+searchId+" state="+searchState+" request="+(test==null?"none":test.request)+" test="+(test==null?"none":test.state));
    }catch(Exception failure){DiagnosticLog.error("input.pairing.record_failed",failure);}}
    static boolean validEntry(Context context,JSONObject entry){
        try{return preferred(context,entry)!=null;}catch(Exception unavailable){return false;}
    }
    static SourceSearch.Candidate preferred(Context context,JSONObject entry)throws Exception{
        if(entry==null||entry.optBoolean("rejected"))return null;
        for(TvInputPanels.Panel panel:new TvInputPanels(context).inspect().panels){
            boolean usable=panel.state==TvInputPanels.PanelState.READY||"adb".equals(entry.optString("transport"))&&panel.state==TvInputPanels.PanelState.RESTRICTED;
            if(usable&&panel.component!=null&&entry.optString("id").equals(SystemEntryMaintenance.panelId(panel))
                    &&entry.optString("scope").equals(SystemEntryMaintenance.panelScope(context,panel)))return SourceSearch.panel(context,panel,entry.optString("transport","native"));
        }
        return null;
    }
}
