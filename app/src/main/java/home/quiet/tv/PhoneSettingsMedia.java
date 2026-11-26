package home.quiet.tv;

import android.content.Context;
import android.graphics.*;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Authenticated media admission reuses the same importers as the TV and never buffers a video. */
final class PhoneSettingsMedia {
    private final PhoneSettingsFiles files;private final PhoneSettingsBackups backups;private final Context context;private final PhoneSettingsTasks tasks;private final String scope,fallback;private final Runnable changed;
    private final Map<String,Upload> tickets=new HashMap<>();private final AtomicBoolean receiving=new AtomicBoolean();
    private static final class Upload {
        String kind,name,mime,target,displayName,shape,coverKey;long bytes,manualVersion;boolean apply;
    }
    PhoneSettingsMedia(Context context,PhoneSettingsTasks tasks,String scope,String fallback,Runnable changed,PhoneSettingsFiles files,PhoneSettingsBackups backups){this.files=files;this.backups=backups;this.context=context;this.tasks=tasks;this.scope=scope;this.fallback=fallback;this.changed=changed;}
    JSONObject limits()throws JSONException,IOException{return new JSONObject().put("wallpaper",WallpaperUploadServer.MAX_UPLOAD).put("video",WallpaperStore.get(context).videoCapacity()).put("avatar",GreetingAvatarStore.MAX_UPLOAD).put("cover",AppCoverStore.MAX_UPLOAD).put("file",files.maximum()).put("backup",Math.max(0,context.getFilesDir().getUsableSpace()-DesktopBackupStore.RESERVE));}
    JSONObject begin(JSONObject value,PhoneSettingsServer.Active active)throws IOException{
        try{
            if(value.length()!=(value.has("displayName")?8:7))throw new PhoneSettingsServer.BadInput();
            for(String field:new String[]{"kind","name","mime","target"})if(!(value.opt(field) instanceof String))throw new PhoneSettingsServer.BadInput();
            if(!(value.opt("apply") instanceof Boolean))throw new PhoneSettingsServer.BadInput();
            Upload upload=new Upload();upload.kind=value.getString("kind");upload.name=value.getString("name");upload.mime=value.getString("mime");upload.target=value.getString("target");upload.apply=value.getBoolean("apply");
            upload.displayName=value.has("displayName")?PhoneSettingsMediaName.read(value.opt("displayName")):upload.name;if(value.has("displayName")&&!"wallpaper".equals(upload.kind))throw new PhoneSettingsServer.BadInput();
            upload.bytes=number(value.opt("bytes"));upload.manualVersion=number(value.opt("manualVersion"));
            if(upload.bytes<1||upload.name.isEmpty()||upload.name.length()>255||upload.name.matches(".*[\\p{Cntrl}].*"))throw new PhoneSettingsServer.BadInput();
            long maximum;
            switch(upload.kind){
                case "wallpaper":if(!upload.target.isEmpty()&&!upload.target.matches("screensaver:[a-f0-9]{64}"))throw new PhoneSettingsServer.BadInput();maximum=WallpaperImport.video(upload.name,upload.mime)?WallpaperStore.get(context).videoCapacity():WallpaperUploadServer.MAX_UPLOAD;break;
                case "file":if(upload.apply||!upload.target.isEmpty())throw new PhoneSettingsServer.BadInput();maximum=files.maximum();break;
                case "backup":if(upload.apply||!upload.target.isEmpty())throw new PhoneSettingsServer.BadInput();maximum=Math.max(0,context.getFilesDir().getUsableSpace()-DesktopBackupStore.RESERVE);break;
                case "avatar":if(!upload.apply||!upload.target.isEmpty())throw new PhoneSettingsServer.BadInput();maximum=GreetingAvatarStore.MAX_UPLOAD;break;
                case "icon":{
                    String[] target=upload.target.split("\\|",-1);if(!upload.apply||target.length!=3||!IconLibraryCatalog.contains(IconLibraryCatalog.SHAPES,target[1]))throw new PhoneSettingsServer.BadInput();
                    upload.target=target[0];upload.shape=target[1];upload.coverKey=target[2];
                    try{AppCoverPolicy.requireDesktopShape(context,upload.shape);}
                    catch(AppCoverPolicy.ShapeChanged conflict){throw new PhoneSettingsServer.Rejected(conflict.getMessage());}
                    AppCoverPolicy.require(context,upload.target);
                    if(!upload.coverKey.equals(AppCoverStore.get(context).key(upload.target)))throw new PhoneSettingsServer.Conflict();
                    maximum=AppCoverStore.MAX_UPLOAD;break;
                }
                case "cover":if(!upload.apply)throw new PhoneSettingsServer.BadInput();AppCoverPolicy.require(context,upload.target);maximum=AppCoverStore.MAX_UPLOAD;break;
                default:throw new PhoneSettingsServer.BadInput();
            }
            if(upload.bytes>maximum)throw new PhoneSettingsServer.Rejected("文件超过大小上限或电视可用空间");
            String domain="icon".equals(upload.kind)?"icons":"cover".equals(upload.kind)?"apps":"avatar".equals(upload.kind)?"greeting":"file".equals(upload.kind)?"files":"backup".equals(upload.kind)?"backup":upload.target.startsWith("screensaver:")?"screensaver":"wallpaper";
            PhoneSettingsTasks.Job job=tasks.begin(scope+"/upload/"+UUID.randomUUID(),"wallpaper".equals(upload.kind)?"上传壁纸":"avatar".equals(upload.kind)?"上传头像":"file".equals(upload.kind)?"上传文件":"backup".equals(upload.kind)?"上传备份":"icon".equals(upload.kind)?"上传应用图标":"上传应用封面",PhoneSettingsState.revision(value),active,domain,"upload");
            job.state="ready";job.message="等待上传";synchronized(tickets){for(Iterator<String> keys=tickets.keySet().iterator();keys.hasNext();){PhoneSettingsTasks.Job previous=tasks.find(keys.next());if(previous==null||previous.terminal())keys.remove();}tickets.put(job.id,upload);}
            return tasks.receipt(job).put("uploadId",job.id);
        }catch(JSONException e){throw new PhoneSettingsServer.BadInput();}
    }
    private static long number(Object value){if(!(value instanceof Number))throw new IllegalArgumentException();long n=((Number)value).longValue();if(n<0||n>9007199254740991L||((Number)value).doubleValue()!=n)throw new IllegalArgumentException();return n;}
    JSONObject upload(String id,InputStream input,long length,PhoneSettingsServer.Active active)throws IOException{
        PhoneSettingsTasks.Job job=tasks.find(id);Upload upload;synchronized(tickets){upload=tickets.get(id);}
        if(job==null||!job.key.startsWith(scope+"/"))throw new PhoneSettingsServer.BadInput();
        if(upload==null||job.terminal())return tasks.receipt(job);
        if(length!=upload.bytes)throw new PhoneSettingsServer.BadInput();
        synchronized(job){if(job.started)return tasks.receipt(job);if(!receiving.compareAndSet(false,true))throw new PhoneSettingsServer.Rejected("已有文件正在上传，请稍后重试");job.started=true;job.state="receiving";}
        File incoming=null,ready=null;WallpaperImport.Prepared prepared=null;boolean committing=false;
        WallpaperUploadServer.Active valid=()->active.valid()&&job.active();
        try{
            if(!valid.valid())throw new PhoneSettingsServer.Ended();
            InputStream progress=new FilterInputStream(input){
                long total;
                private void count(int n){if(n<1)return;total+=n;int percent=(int)Math.min(100,total*100/length);job.stage(percent==100?"正在校验素材":"正在上传",percent);if(percent==100)job.state="running";}
                public int read()throws IOException{int n=in.read();if(n>=0)count(1);return n;}
                public int read(byte[] b,int off,int count)throws IOException{int n=in.read(b,off,count);count(n);return n;}
            };
            JSONObject result=new JSONObject();String message;
            if("file".equals(upload.kind)){result=files.receive(progress,length,upload.name,valid::valid,(n,total)->job.stage("正在传输文件",(int)(n*100/total)));message="文件已保存到电视已接收";}
            else if("backup".equals(upload.kind)){committing=true;result=backups.receive(progress,length,valid::valid);message="备份已校验，请核对内容并确认导入；当前桌面未改变";}
            else if("wallpaper".equals(upload.kind)){
                prepared=WallpaperImport.prepare(context,progress,length,upload.name,WallpaperImport.video(upload.name,upload.mime),valid,"phone");
                committing=true;WallpaperCatalog.Entry entry=prepared.save(upload.displayName,valid);WallpaperStore store=WallpaperStore.get(context);boolean applied=false;
                try{if(upload.apply&&valid.valid())synchronized(store){
                    if(upload.target.startsWith("screensaver:")){
                        ScreensaverStore savers=ScreensaverStore.get(context);synchronized(savers){WallpaperCatalog catalog=store.snapshot();ScreensaverSettings current=savers.snapshot(catalog);if(PhoneSettingsState.revision(PhoneSettingsConfiguration.saverValues(current,catalog)).equals(upload.target.substring(12))&&valid.valid()){savers.save(current.media(entry.id));applied=true;}}
                    }else if(store.manualVersion()==upload.manualVersion&&valid.valid()){store.select(entry.id);applied=true;}
                }}
                catch(IOException e){tasks.unknown(job,new JSONObject().put("mediaId",entry.id).put("saved",true).put("requiresReview",true),"素材已保存，应用结果待核对，请从素材库重新读取");changed.run();return tasks.receipt(job);}
                result.put("mediaId",entry.id).put("saved",true).put("applied",applied).put("requiresReview",upload.apply&&!applied).put("width",entry.width).put("height",entry.height).put("bytes",entry.bytes).put("durationMs",entry.durationMs);
                message=upload.apply&&!applied?"素材已保存，电视背景已变化或连接已结束，请从素材库选择应用":applied?"壁纸已应用":"素材已保存";
            }else{
                incoming=File.createTempFile("phone-image-",".tmp",context.getCacheDir());
                try(FileOutputStream output=new FileOutputStream(incoming)){WallpaperImport.copy(progress,output,length,"avatar".equals(upload.kind)?GreetingAvatarStore.MAX_UPLOAD:AppCoverStore.MAX_UPLOAD,valid);output.getFD().sync();}
                job.stage("正在校验图片",100);job.check();
                if("avatar".equals(upload.kind)){
                    GreetingAvatarStore avatars=new GreetingAvatarStore(context);ready=avatars.candidate();GreetingAvatarStore.prepare(incoming,ready);job.check();
                    synchronized(DesktopSettingsStore.MUTATIONS){if(!valid.valid())throw new PhoneSettingsServer.Ended();DesktopSettingsStore desktop=new DesktopSettingsStore(context);committing=true;desktop.saveGreeting(desktop.greetingEnabled(),desktop.greetingText(fallback),true,ready.getName());}
                    result.put("avatar",ready.getName());message="头像已应用";ready=null;
                }else{
                    AppCoverPolicy.require(context,upload.target);
                    AppCoverStore covers=AppCoverStore.get(context);
                    boolean icon="icon".equals(upload.kind);
                    ready=covers.prepared();
                    if(icon){
                        AppCoverPolicy.requireDesktopShape(context,upload.shape);
                        AppCoverImages.prepareIcon(incoming,ready,upload.shape);
                    }else AppCoverImages.prepare(incoming,ready);
                    job.check();
                    AppCoverPolicy.require(context,upload.target);
                    if(icon){
                        synchronized(DesktopSettingsStore.MUTATIONS){
                            job.check();
                            AppCoverPolicy.requireDesktopShape(context,upload.shape);
                            if(!upload.coverKey.equals(covers.key(upload.target)))throw new PhoneSettingsServer.Conflict();
                            committing=true;
                            covers.applyUpload(upload.target,ready,upload.shape,upload.coverKey);
                        }
                    }else{
                        job.check();
                        committing=true;
                        covers.apply(upload.target,ready);
                    }
                    result.put("package",upload.target);
                    message=icon?"应用图标已更新":"应用封面已更新";
                    ready=null;
                }
            }
            tasks.complete(job,result,message);changed.run();return tasks.receipt(job);
        }catch(JSONException e){tasks.fail(job,"素材结果无法读取，请核对电视状态");throw new IOException(e);}
        catch(AppCoverPolicy.ShapeChanged conflict){tasks.fail(job,conflict.getMessage());throw new PhoneSettingsServer.Rejected(conflict.getMessage());}
        catch(IOException|OutOfMemoryError e){if(committing||"file".equals(upload.kind)&&job.progress==100)tasks.unknown(job,new JSONObject(Collections.singletonMap("requiresReview",true)),"保存结果待核对，请重新读取电视设置");else tasks.fail(job,job.cancelled?"已取消上传":"上传未完成，请核对文件格式、电视支持情况和存储空间");throw new IOException("上传未确认，请查询任务结果",e);}
        finally{
            receiving.set(false);synchronized(tickets){tickets.remove(id);}
            if(prepared!=null&&!"unknown".equals(job.state))prepared.discard();
            if(incoming!=null&&incoming.isFile())incoming.delete();
            if(ready!=null&&ready.isFile()&&!"unknown".equals(job.state)){if("avatar".equals(upload.kind))GreetingAvatarStore.discard(ready);else AppCoverStore.get(context).discardCandidate(ready);}
        }
    }
    JSONObject fromFile(String id,PhoneSettingsServer.Active active)throws IOException{
        long expected=WallpaperStore.get(context).manualVersion();PhoneSettingsTasks.Job job=tasks.begin(scope+"/file-wallpaper/"+id,"应用文件为壁纸",id,active);
        tasks.start(job,work->{work.check();WallpaperImport.Prepared prepared=null;boolean committing=false;
            try{boolean received=!id.startsWith("source-");String known=received?context.getSharedPreferences("file-transfer-wallpapers",0).getString(id,""):"";WallpaperCatalog.Entry previous=WallpaperStore.get(context).snapshot().find(known);if(previous!=null){synchronized(WallpaperStore.get(context)){if(!work.active()||WallpaperStore.get(context).manualVersion()!=expected)throw new PhoneSettingsTasks.Problem("电视背景已变化，请从素材库重新选择使用",false);WallpaperStore.get(context).select(previous.id);}changed.run();work.stage("文件对应的壁纸已应用",100);return new JSONObject().put("mediaId",previous.id).put("applied",true);}
            PhoneSettingsServer.Download selected=files.download(id);try(InputStream input=selected.input){prepared=WallpaperImport.prepare(context,input,selected.bytes,selected.name,WallpaperImport.video(selected.name,null),work::active,"phone_file");}
                work.check();committing=true;WallpaperCatalog.Entry entry=prepared.save(prepared.name,work::active);if(received&&!context.getSharedPreferences("file-transfer-wallpapers",0).edit().putString(id,entry.id).commit())throw new IOException("素材关联记录保存未确认");WallpaperStore store=WallpaperStore.get(context);boolean applied=false;synchronized(store){if(work.active()&&store.manualVersion()==expected){store.select(entry.id);applied=true;}}changed.run();work.stage(applied?"文件已导入并应用为壁纸":"素材已保存，电视背景已变化，请从素材库选择使用",100);return new JSONObject().put("mediaId",entry.id).put("saved",true).put("applied",applied).put("requiresReview",!applied);
            }catch(IOException failure){if(committing)throw new PhoneSettingsTasks.Problem("素材保存或应用结果待核对，请从电视素材库重新读取",true);throw failure;}finally{if(prepared!=null&&!committing)prepared.discard();}
        });return tasks.receipt(job);
    }
    JSONObject daily(boolean apply,PhoneSettingsServer.Active active)throws IOException{
        return daily(apply,DailyWallpaperLoader.source(WallpaperStore.get(context).snapshot()),!apply,false,active);
    }
    JSONObject dailySource(String source,PhoneSettingsServer.Active active)throws IOException{
        if(!WallpaperCatalog.dailyProvider(source))throw new PhoneSettingsServer.BadInput();
        return daily(true,source,false,true,active);
    }
    private JSONObject daily(boolean apply,String source,boolean force,boolean switching,PhoneSettingsServer.Active active)throws IOException{
        WallpaperStore store=WallpaperStore.get(context);long expected=store.manualVersion();
        String kind=switching?"dailySource":apply?"dailyApply":"dailyRefresh";
        PhoneSettingsTasks.Job job=tasks.begin(scope+"/daily",switching?"切换图片来源":apply?"应用每日壁纸":"刷新壁纸",source+"/"+kind,active,"wallpaper",kind);
        tasks.start(job,work->{
            DailyWallpaperClient client=new DailyWallpaperClient(context);DailyWallpaperLoader.Result result=null;long lease=0;
            try{
                work.check();lease=store.beginDaily(source);long operation=lease;changed.run();
                work.stage("正在获取壁纸",-1);
                DailyWallpaperLoader loader=new DailyWallpaperLoader(context,store,client);DailyWallpaperLoader.Check check=()->{work.check();if(!store.dailyActive(operation))throw new InterruptedIOException();};
                result=apply&&!force?loader.select(source,check):loader.load(source,force,check);
                synchronized(store){
                    work.check();
                    if(!store.commitDailyIfCurrent(result.image,result.prepared,apply,expected,lease))throw new PhoneSettingsTasks.Problem("电视背景已变化，未覆盖新的选择，请重新核对",false);
                }
                store.dailyRetry(source,0);store.finishDaily(lease);changed.run();
                boolean unchanged=force&&result.unchanged;
                work.stage(result.offline?"电视离线，正在使用缓存":unchanged?"暂无新图片，保留当前背景":switching?"图片来源已切换":apply?"每日壁纸已应用":"壁纸已刷新",100);
                return new JSONObject().put("applied",apply).put("cached",true).put("source",source).put("unchanged",unchanged).put("offline",result.offline).put("requiresReview",false);
            }catch(DailyWallpaperClient.Limited error){throw new PhoneSettingsTasks.Problem("壁纸来源请求过于频繁，已保留当前背景，请稍后重试",false);}
            catch(PhoneSettingsTasks.Problem error){throw error;}
            catch(InterruptedIOException error){throw error;}
            catch(IOException|OutOfMemoryError error){throw new PhoneSettingsTasks.Problem("壁纸获取失败，已保留当前背景，请检查电视网络或稍后重试",false);}
            finally{if(result!=null)result.close();store.finishDaily(lease);client.cancel();changed.run();}
        });return tasks.receipt(job);
    }
}
