package home.quiet.tv;

import android.content.Context;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Formal archive, journal and rollback engine; restore starts only after the receipt is written. */
final class PhoneSettingsBackups {
    private final PhoneSettingsFiles files;private final Context context;private final PhoneSettingsTasks tasks;private final String scope;private final Runnable changed,ended;private final Map<String,JSONObject> drafts=new HashMap<>();private final Map<String,String> restores=new HashMap<>();private final Map<String,File> downloads=new HashMap<>();
    PhoneSettingsBackups(Context context,PhoneSettingsTasks tasks,String scope,Runnable changed,Runnable ended,PhoneSettingsFiles files){this.files=files;this.context=context;this.tasks=tasks;this.scope=scope;this.changed=changed;this.ended=ended;}
    JSONObject state()throws IOException{
        DesktopBackups backups=DesktopBackups.get(context);try{JSONArray entries=new JSONArray();if(backups.error().isEmpty())for(DesktopBackupStore.Entry entry:backups.store().entries()){long bytes=0;for(int i=0;i<entry.record.assets.length();i++)bytes+=entry.record.assets.getJSONObject(i).getLong("bytes");entries.put(new JSONObject().put("id",entry.record.id).put("created",entry.record.created).put("kind",entry.record.kind).put("version",entry.record.json.getString("software")).put("assets",entry.record.assets.length()).put("assetBytes",bytes));}
            JSONObject restore=new JSONObject();File journal=new File(context.getFilesDir(),"desktop-backups/restore.json");if(journal.isFile()&&journal.length()<=DesktopBackupRecord.MAX_CONFIG_BYTES)try{JSONObject record=new JSONObject(new String(new androidx.core.util.AtomicFile(journal).readFully(),Charsets.UTF_8));restore.put("phase",record.optString("phase")).put("before",record.optString("before")).put("target",record.optString("target"));}catch(IOException|JSONException unreadable){restore.put("phase","unknown");}
            JSONArray prepared=new JSONArray();synchronized(drafts){for(JSONObject draft:drafts.values())prepared.put(draft);}return new JSONObject().put("activeRestore",tasks.activeRestore()).put("backupDrafts",prepared).put("backups",entries).put("backupError",backups.error()).put("backupRecoveryError",backups.recoveryError()).put("backupAutomatic",backups.error().isEmpty()&&backups.store().automatic()).put("lastRestore",restore);
        }catch(JSONException e){throw new IOException(e);}
    }
    private <T>T run(DesktopBackups.Job<T> operation,boolean mutate)throws IOException{
        return run(operation,mutate,false);
    }
    private <T>T run(DesktopBackups.Job<T> operation,boolean mutate,boolean waitForCompletion)throws IOException{
        CountDownLatch ready=new CountDownLatch(1);Object[] result={null};String[] error={""};DesktopBackups.Result<T> callback=(value,problem)->{result[0]=value;error[0]=problem;ready.countDown();};DesktopBackups backups=DesktopBackups.get(context);if(mutate)backups.run(operation,callback);else backups.read(operation,callback);
        try{if(waitForCompletion)ready.await();else if(!ready.await(120,TimeUnit.SECONDS))throw new PhoneSettingsTasks.Problem("备份任务结果待核对，请勿重复执行",true);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new PhoneSettingsTasks.Problem("备份任务结果待核对",true);}if(!error[0].isEmpty())throw new PhoneSettingsTasks.Problem(error[0],mutate);@SuppressWarnings("unchecked") T value=(T)result[0];return value;
    }
    JSONObject receive(InputStream input,long bytes,PhoneSettingsServer.Active active)throws IOException{
        String draft=DesktopBackups.get(context).receive(input,bytes,active::valid);DesktopBackupStore.Entry prepared=run(store->{if(!active.valid())throw new PhoneSettingsServer.Ended();return store.draft(draft);},false);
        try{JSONObject summary=new JSONObject().put("id",draft).put("created",prepared.record.created).put("version",prepared.record.json.getString("software")).put("assets",prepared.record.assets.length());synchronized(drafts){drafts.put(draft,summary);}changed.run();return new JSONObject().put("draftId",draft).put("prepared",true);}catch(JSONException e){throw new IOException(e);}
    }
    JSONObject command(String field,Object value,PhoneSettingsServer.Active active)throws IOException{
        if(field.equals("restore")&&value instanceof String){String target=(String)value;DesktopBackups.get(context).store().find(target);PhoneSettingsTasks.Job job=tasks.begin(scope+"/backup/restore","恢复桌面备份",target,active);job.state="ready";job.message="恢复请求已受理，连接即将结束；请重新扫码核对结果";synchronized(restores){restores.put(job.id,target);}try{return tasks.receipt(job).put("afterReceipt",job.id).put("endsConnection",true);}catch(JSONException e){throw new IOException(e);}}
        String kind=field,title=field.equals("remove")?"删除桌面备份":"桌面备份";if(field.equals("export")&&value instanceof JSONObject){JSONObject request=(JSONObject)value;boolean media=request.optBoolean("media");kind+="/"+request.optString("id")+"/"+(media?"zip":"json");title=media?"导出配置和素材 ZIP":"导出配置 JSON";}
        PhoneSettingsTasks.Job job=tasks.begin(scope+"/backup/"+field,title,field+":"+String.valueOf(value),active,"backup",kind);
        tasks.start(job,work->{work.check();
            if(field.equals("prepareFromFile")&&value instanceof String){PhoneSettingsServer.Download file=files.download((String)value);try(InputStream input=file.input){JSONObject prepared=receive(input,file.bytes,work::active);work.stage("备份已校验，请核对内容后确认导入",100);return prepared;}}
            if(field.equals("admit")&&value instanceof String){String draft=(String)value;synchronized(drafts){if(!drafts.containsKey(draft))throw new PhoneSettingsTasks.Problem("导入草稿已不可用，请重新传入",false);}DesktopBackupStore.Entry entry=run(store->{work.check();return store.add(store.draft(draft));},true);synchronized(drafts){drafts.remove(draft);}changed.run();work.stage("备份已导入，桌面保持当前配置",100);return new JSONObject().put("backupId",entry.record.id);}
            if(field.equals("create")&&Boolean.TRUE.equals(value)){DesktopBackupStore.Entry entry=run(store->{work.check();return store.capture("manual");},true);changed.run();work.stage("桌面备份已创建",100);return new JSONObject().put("backupId",entry.record.id);}
            if(field.equals("remove")&&value instanceof String){String id=(String)value;run(store->{work.check();store.remove(id);return null;},true,true);changed.run();work.stage("备份已删除，当前桌面和素材保持不变",100);return new JSONObject().put("removed",id);}
            if(field.equals("automatic")&&value instanceof Boolean){run(store->{work.check();store.automatic((Boolean)value);return null;},true);changed.run();work.stage("自动备份设置已更新",100);return new JSONObject();}
            if(field.equals("retry")&&Boolean.TRUE.equals(value)){CountDownLatch done=new CountDownLatch(1);DesktopBackups.get(context).retryRecovery(done::countDown);if(!done.await(120,TimeUnit.SECONDS))throw new PhoneSettingsTasks.Problem("恢复记录核对尚未确认",true);work.check();changed.run();work.stage("已重新读取备份恢复记录",100);return new JSONObject();}
            if(field.equals("export")&&value instanceof JSONObject){JSONObject request=(JSONObject)value;if(request.length()!=2||!(request.opt("id") instanceof String)||!(request.opt("media") instanceof Boolean))throw new PhoneSettingsTasks.Problem("导出选项无效",false);boolean media=request.getBoolean("media");String id=request.getString("id"),ticket=UUID.randomUUID().toString();File target=new File(context.getCacheDir(),"phone-backup-"+ticket+(media?".zip":".json"));work.stage("正在校验并导出正式备份",-1);
                // The task already runs off the HTTP threads. Keep its result until the native export finishes.
                try{run(store->{work.check();DesktopBackupStore.Entry entry;if(id.isEmpty()){DesktopBackupRecord current=store.snapshots.capture("manual");JSONObject sources=new JSONObject();try{for(int i=0;i<current.assets.length();i++){String path=current.assets.getJSONObject(i).getString("path");sources.put(path,path);}}catch(JSONException invalid){throw new IOException("当前配置的素材引用无法读取",invalid);}entry=new DesktopBackupStore.Entry(current,sources);}else entry=store.find(id);DesktopBackupArchive archive=new DesktopBackupArchive(context,store);if(context.getCacheDir().getUsableSpace()<=DesktopBackupArchive.estimate(entry.record,media)+DesktopBackupStore.RESERVE)throw new IOException("导出所需空间不足");try(FileOutputStream output=new FileOutputStream(target)){archive.export(entry,media,output,work::active,(bytes,total)->work.stage("正在导出素材 "+FileTransferSource.size(bytes)+" / "+FileTransferSource.size(total),total==0?99:(int)Math.min(99,100d*bytes/total)));output.getFD().sync();}return null;},false,true);}catch(IOException failure){work.check();throw failure;}work.check();synchronized(downloads){downloads.put(ticket,target);}work.stage("备份已导出，可下载到手机",100);return new JSONObject().put("download","api/downloads/backup/"+ticket).put("bytes",target.length()).put("backupId",id).put("media",media);
            }
            throw new PhoneSettingsTasks.Problem("备份操作无效",false);
        });return tasks.receipt(job);
    }
    void delivered(String id){String target;synchronized(restores){target=restores.remove(id);}if(target==null)return;PhoneSettingsTasks.Job job=tasks.find(id);if(job==null||!tasks.commitRestore(job))return;
        ended.run();DesktopBackups nativeBackups=DesktopBackups.get(context);nativeBackups.phoneRestore(()->tasks.start(job,work->{work.stage("正在恢复，完成后请重新扫码核对",-1);try{run(store->{store.restore(target);return null;},true,true);changed.run();work.stage("恢复已完成，正式恢复记录已保存",100);return new JSONObject().put("restored",target);}catch(IOException failure){throw new PhoneSettingsTasks.Problem(failure.getMessage(),true);}finally{nativeBackups.phoneRestoreFinished();}}));
    }
    PhoneSettingsServer.Download download(String id)throws IOException{File file;synchronized(downloads){file=downloads.get(id);}if(file==null||!file.isFile())throw new PhoneSettingsServer.BadInput();return new PhoneSettingsServer.Download(new FileInputStream(file),file.length(),"QuietTV-desktop-backup"+(file.getName().endsWith(".zip")?".zip":".json"),"application/octet-stream");}
}
