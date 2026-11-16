package home.quiet.tv;

import android.content.Context;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import java.io.*;
import java.util.*;
import org.json.*;

/** Authenticated library previews and explicit commits share the TV artwork and cover store. */
final class PhoneSettingsIcons {
    private final Context context;
    private final PhoneSettingsTasks tasks;
    private final String scope;
    private final Runnable changed;
    private IconLibraryBatch.Plan batch;
    PhoneSettingsIcons(Context context,PhoneSettingsTasks tasks,String scope,Runnable changed){this.context=context;this.tasks=tasks;this.scope=scope;this.changed=changed;}
    JSONObject state()throws JSONException{IconLibraryCatalog online=IconLibraryRepository.get(context).catalog();return new JSONObject().put("iconRevision",AppCoverStore.get(context).revision()).put("catalogVersion",online==null?0:online.revision).put("matchRevision",online==null||online.matches==null?0:online.matches.optInt("revision"));}
    JSONObject metadata()throws IOException,JSONException{
        IconLibraryRepository.get(context).refresh();IconLibraryCatalog catalog=null;String problem="";try{catalog=IconLibraryCatalog.read(context);}catch(IOException failure){problem="图标素材无法读取，仍可上传图标";}JSONArray items=new JSONArray(),targets=new JSONArray();AppCoverStore covers=AppCoverStore.get(context);
        if(catalog!=null)for(IconLibraryCatalog.Item item:catalog.items){
            JSONObject entry=new JSONObject().put("id",item.id).put("name",item.label(true)).put("style",item.style);
            if(item.online()){JSONArray variants=new JSONArray();for(String shape:IconLibraryCatalog.SHAPES)for(String form:IconLibraryCatalog.forms(shape))if(item.supports(shape,form))variants.put(new JSONObject().put("shape",shape).put("form",form));entry.put("variants",variants);}items.put(entry);
        }
        for(Map.Entry<String,ResolveInfo> entry:new HomeAppCatalog(context).launchable().entrySet()){
            String pkg=entry.getKey();if(AppCoverPolicy.ownReadOnly(context,pkg)||!entry.getValue().activityInfo.applicationInfo.enabled||!AppAvailability.installed(context.getPackageManager(),entry.getValue().activityInfo.applicationInfo))continue;
            String encoded=java.net.URLEncoder.encode(pkg,"UTF-8"),key=covers.key(pkg);
            targets.put(new JSONObject().put("id",pkg).put("name",new AppNameStore(context).display(pkg,entry.getValue().loadLabel(context.getPackageManager()).toString())).put("coverKey",key).put("customCover",covers.custom(pkg)).put("coverFit",covers.contain(pkg)?"contain":"cover").put("cover","api/resources/cover/"+encoded+"?v="+key).put("icon","api/resources/icon/"+encoded));
        }
        return new JSONObject().put("revision",catalog==null?0:catalog.revision).put("problem",problem).put("styles",new JSONArray(java.util.Arrays.asList(IconLibraryCatalog.STYLES))).put("shapes",new JSONArray(java.util.Arrays.asList(IconLibraryCatalog.SHAPES))).put("items",items).put("targets",targets);
    }
    static IconLibraryCatalog.Item selection(IconLibraryCatalog catalog,String style,String id,String shape,String form)throws IOException{
        if(!IconLibraryCatalog.contains(IconLibraryCatalog.STYLES,style)||!IconLibraryCatalog.contains(IconLibraryCatalog.SHAPES,shape)||!IconLibraryCatalog.contains(IconLibraryCatalog.forms(shape),form))throw new PhoneSettingsServer.BadInput();
        for(IconLibraryCatalog.Item item:catalog.items)if(item.style.equals(style)&&item.id.equals(id)&&item.supports(shape,form))return item;
        throw new PhoneSettingsServer.Rejected("图标素材已变化，请重新选择");
    }
    byte[] resource(String route)throws IOException{
        String[] parts=route.split("/",-1);if(parts.length!=5||!Arrays.asList("thumb","preview").contains(parts[4]))throw new PhoneSettingsServer.BadInput();
        IconLibraryCatalog.Item item=selection(IconLibraryCatalog.read(context),parts[0],parts[1],parts[2],parts[3]);Bitmap image=null;
        if(item.online()&&item.variant(parts[2],parts[3]).shape.equals(parts[2])){
            IconLibraryCatalog.Variant variant=item.variant(parts[2],parts[3]);IconLibraryCatalog.Asset asset=parts[4].equals("thumb")?variant.thumbnail:variant.image;
            File file=IconLibraryRepository.get(context).artwork(asset,new IconLibraryTransfer.Request());try(InputStream input=new FileInputStream(file)){return IconLibraryRepository.read(input,asset.bytes);}
        }
        try{image=IconLibraryArtwork.render(context,item,parts[2],parts[3],parts[4].equals("thumb")?240:parts[2].equals("rectangle")?720:512,true,parts[4].equals("thumb"),new IconLibraryTransfer.Request());ByteArrayOutputStream out=new ByteArrayOutputStream();if(!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("图标预览无法读取");return out.toByteArray();}
        catch(OutOfMemoryError error){throw new IOException("图标预览无法读取",error);}finally{if(image!=null)image.recycle();}
    }
    JSONObject command(String action,JSONObject value,PhoneSettingsServer.Active active)throws IOException{
        if("match".equals(action)){
            if(value.length()!=3)throw new PhoneSettingsServer.BadInput();batch=IconLibraryBatch.prepare(context,context,value.optString("style"),value.optString("shape"),value.optString("form"));try{return batch.json();}catch(JSONException invalid){throw new IOException(invalid);}
        }
        if("batch".equals(action))return applyBatch(value,active);
        boolean restore="restore".equals(action);if(!restore&&!"apply".equals(action))throw new PhoneSettingsServer.BadInput();
        String[] names=restore?new String[]{"package","coverKey"}:new String[]{"package","coverKey","style","id","shape","form","catalogVersion"};
        if(value.length()!=names.length)throw new PhoneSettingsServer.BadInput();for(String name:names)if(!(value.opt(name) instanceof String)&&!name.equals("catalogVersion"))throw new PhoneSettingsServer.BadInput();
        String pkg=value.optString("package"),expected=value.optString("coverKey");AppCoverPolicy.require(context,pkg);AppCoverStore covers=AppCoverStore.get(context);
        if(!expected.equals(covers.key(pkg)))throw new PhoneSettingsServer.Conflict();
        IconLibraryCatalog catalog=restore?null:IconLibraryCatalog.read(context);IconLibraryCatalog.Item item=restore?null:selection(catalog,value.optString("style"),value.optString("id"),value.optString("shape"),value.optString("form"));
        if(!restore&&(!(value.opt("catalogVersion") instanceof Number)||value.optDouble("catalogVersion")!=catalog.revision))throw new PhoneSettingsServer.Conflict();
        PhoneSettingsTasks.Job job=tasks.begin(scope+"/icons/"+pkg,restore?"恢复默认图标":"替换图标",PhoneSettingsState.revision(value),active,"icons",action);
        tasks.start(job,work->{File ready=null;boolean committing=false;try{
            work.check();AppCoverPolicy.require(context,pkg);work.stage(restore?"正在恢复默认图标":"正在准备图标",-1);
            if(!restore){String shape=value.optString("shape");ready=covers.prepared();IconLibraryArtwork.prepare(context,item,shape,value.optString("form"),ready,true);}
            synchronized(covers){work.check();AppCoverPolicy.require(context,pkg);if(!expected.equals(covers.key(pkg)))throw new PhoneSettingsTasks.Problem("当前图标已变化，请重新选择并确认",false);committing=true;if(restore)covers.restore(pkg);else covers.applyLibrary(pkg,ready,LibraryCover.metadata(item,value.optString("shape"),value.optString("form"),catalog.revision),expected);}
            changed.run();work.stage(restore?"已恢复默认图标":"已替换图标",100);return new JSONObject().put("package",pkg).put("applied",true);
        }catch(PhoneSettingsTasks.Problem failure){throw failure;}catch(IOException failure){throw new PhoneSettingsTasks.Problem(committing?"保存结果待核对，请重新读取当前图标":failure.getMessage(),committing);}finally{covers.discardCandidate(ready);}});
        return tasks.receipt(job);
    }
    private JSONObject applyBatch(JSONObject value,PhoneSettingsServer.Active active)throws IOException{
        String request=value.optString("requestId");if(value.length()!=4||!request.matches("[a-f0-9]{32}")||!(value.opt("coverCustom") instanceof Boolean)||!(value.opt("selected") instanceof JSONArray))throw new PhoneSettingsServer.BadInput();
        String fingerprint=PhoneSettingsState.revision(value),key=scope+"/icons/batch/"+request;PhoneSettingsTasks.Job previous=tasks.previous(key,fingerprint);if(previous!=null)return tasks.receipt(previous);
        IconLibraryBatch.Plan plan=batch;if(plan==null||!plan.id.equals(value.optString("planId")))throw new PhoneSettingsServer.Conflict();Map<String,String> selected=new LinkedHashMap<>();JSONArray rows=value.optJSONArray("selected");
        try{if(rows.length()==0||rows.length()>plan.rows.size())throw new PhoneSettingsServer.BadInput();for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(row.length()!=2||selected.put(row.getString("package"),row.getString("id"))!=null)throw new PhoneSettingsServer.BadInput();}}catch(JSONException invalid){throw new PhoneSettingsServer.BadInput();}
        boolean coverCustom=value.optBoolean("coverCustom");PhoneSettingsTasks.Job job=tasks.begin(key,"一键替换图标",fingerprint,active,"icons","batch");tasks.start(job,work->{
            final boolean[] committing={false};IconLibraryTransfer.Request requestState=new IconLibraryTransfer.Request(work::active);try{int count=IconLibraryBatch.apply(context,context,plan,selected,coverCustom,new IconLibraryBatch.Control(){
                public IconLibraryTransfer.Request request(){return requestState;}
                public void check()throws IOException{work.check();}
                public void progress(int done,int total){work.stage("正在准备图标  "+done+" / "+total,done*90/total);}
                public void committing()throws IOException{synchronized(work){work.check();work.cancellable=false;committing[0]=true;}}
            });changed.run();work.stage("已替换 "+count+" 个图标",100);return new JSONObject().put("applied",count).put("planId",plan.id);
            }catch(IOException failure){throw new PhoneSettingsTasks.Problem(committing[0]?"保存结果待核对，请重新读取当前图标":failure.getMessage(),committing[0]);}
        });return tasks.receipt(job);
    }

}
