package home.quiet.tv;

import android.content.Context;
import android.content.pm.ResolveInfo;
import java.io.*;
import java.util.*;
import org.json.*;

/** A read-only plan and prepared files are published as one cover-index transaction. */
final class IconLibraryBatch {
    interface Control {void check()throws IOException;void progress(int done,int total);void committing()throws IOException;IconLibraryTransfer.Request request();}
    static final class Row {
        final String pkg,label,key,status,id,reason,originalKey;final List<String> candidates;final boolean custom,automatic;
        Row(String pkg,String label,String key,String status,String id,String reason,List<String> candidates,boolean custom,boolean automatic,String originalKey){this.originalKey=originalKey;this.pkg=pkg;this.label=label;this.key=key;this.status=status;this.id=id;this.reason=reason;this.candidates=candidates;this.custom=custom;this.automatic=automatic;}
        JSONObject json()throws JSONException{return new JSONObject().put("package",pkg).put("name",label).put("coverKey",key).put("status",status).put("id",id).put("reason",reason).put("candidates",new JSONArray(candidates)).put("custom",custom).put("automatic",automatic);}
    }
    static final class Plan {
        final String id=UUID.randomUUID().toString(),style,shape,form;final IconLibraryCatalog catalog;final int matchRevision;final List<Row> rows;
        Plan(String style,String shape,String form,IconLibraryCatalog catalog,int revision,List<Row> rows){this.style=style;this.shape=shape;this.form=form;this.catalog=catalog;this.matchRevision=revision;this.rows=Collections.unmodifiableList(rows);}
        IconLibraryCatalog.Item item(String id){for(IconLibraryCatalog.Item item:catalog.items)if(item.id.equals(id)&&item.style.equals(style))return item;return null;}
        List<String> choices(Row row){if(!row.status.equals("unmatched")&&!(row.custom&&row.candidates.isEmpty()))return row.candidates;List<String> ids=new ArrayList<>();for(IconLibraryCatalog.Item item:catalog.items)if(item.style.equals(style)&&item.supports(shape,form))ids.add(item.id);return ids;}
        IconLibraryCatalog.Item selection(Row row,String id,boolean coverCustom)throws IOException{
            if(row==null||row.status.equals("current")||row.custom&&!coverCustom||!choices(row).contains(id))throw new IOException("图标选择无效，请核对素材及自定义图标覆盖开关");
            if(id.equals(IconLibraryFrame.id(row.pkg))&&!row.originalKey.isEmpty()&&!row.custom)return null;
            IconLibraryCatalog.Item item=item(id);if(item==null||!item.supports(shape,form))throw new IOException("图标素材已变化，请重新匹配");return item;
        }
        JSONObject json()throws JSONException{JSONArray values=new JSONArray();for(Row row:rows)values.put(row.json());return new JSONObject().put("planId",id).put("style",style).put("shape",shape).put("form",form).put("catalogVersion",catalog.revision).put("matchRevision",matchRevision).put("rows",values);}
    }
    static Plan prepare(Context context,Context artwork,String style,String shape,String form)throws IOException{
        if(!IconLibraryCatalog.styleId(style)||!IconLibraryCatalog.contains(IconLibraryCatalog.SHAPES,shape)||!IconLibraryCatalog.contains(IconLibraryCatalog.forms(shape),form))throw new IOException("图标选项无效");
        // Batch replacements follow Home, independently of the library's shape filter.
        shape=new DesktopSettingsStore(context).iconShape();if(!IconLibraryCatalog.contains(IconLibraryCatalog.forms(shape),form))form="icon";
        IconLibraryCatalog catalog=IconLibraryCatalog.read(artwork);IconLibraryMatcher matcher=IconLibraryMatcher.read(artwork,catalog);AppCoverStore store=AppCoverStore.get(context);List<Row> rows=new ArrayList<>();
        for(Map.Entry<String,ResolveInfo> app:new HomeAppCatalog(context).launchable().entrySet()){
            String pkg=app.getKey();if(AppCoverPolicy.ownReadOnly(context,pkg)||!app.getValue().activityInfo.applicationInfo.enabled||!AppAvailability.installed(context.getPackageManager(),app.getValue().activityInfo.applicationInfo))continue;
            String name=app.getValue().loadLabel(context.getPackageManager()).toString(),key;JSONObject current;boolean custom,present;
            synchronized(store){key=store.key(pkg);current=store.entry(pkg);present=store.custom(pkg);custom=present&&!LibraryCover.generated(current);}
            IconLibraryMatcher.Match match=matcher.match(pkg,name,custom&&LibraryCover.library(current)?current.optString("theme"):"");String id=match.ids.size()==1?match.ids.get(0):"";
            IconLibraryCatalog.Item chosen=null;List<String> candidates=new ArrayList<>();for(String candidate:match.ids)for(IconLibraryCatalog.Item item:catalog.items)if(item.id.equals(candidate)&&item.style.equals(style)&&item.supports(shape,form)){candidates.add(item.id);if(item.id.equals(id))chosen=item;}
            String label=new AppNameStore(context).display(pkg,name),reason=match.reason,originalKey="";boolean automatic=match.automatic;
            String state=!match.automatic?"pending":custom?"custom":"ready";
            try{
                if(candidates.isEmpty()){
                    id=IconLibraryFrame.id(pkg);reason="original";automatic=!custom;state=custom?"custom":"ready";
                    if(!custom){originalKey=IconLibraryFrame.sourceKey(context,pkg,label);candidates.add(id);if(present&&LibraryCover.same(current,LibraryCover.frameMetadata(style,shape,form,originalKey)))state="current";}
                }else if(present&&chosen!=null&&LibraryCover.same(current,LibraryCover.metadata(chosen,shape,form,catalog.revision)))state="current";
            }catch(JSONException invalid){throw new IOException(invalid);}
            rows.add(new Row(pkg,label,key,state,id,reason,candidates,custom,automatic,originalKey));
        }
        Collections.sort(rows,(a,b)->{int n=java.text.Collator.getInstance().compare(a.label,b.label);return n==0?a.pkg.compareTo(b.pkg):n;});return new Plan(style,shape,form,catalog,matcher.revision,rows);
    }
    private static void requireCurrent(Context context,Context artwork,Plan plan)throws IOException{
        if(!plan.shape.equals(new DesktopSettingsStore(context).iconShape()))throw new IOException("桌面图标形状已变化，请重新匹配");
        IconLibraryCatalog current=IconLibraryCatalog.read(artwork);
        if(current.revision!=plan.catalog.revision||IconLibraryMatcher.read(artwork,current).revision!=plan.matchRevision)throw new IOException("图库或匹配规则已变化，请重新匹配");
    }
    static int apply(Context context,Context artwork,Plan plan,Map<String,String> selections,boolean coverCustom,Control control)throws IOException{
        DiagnosticOperation trace=new DiagnosticOperation("icons.batch","plan="+DiagnosticLog.anonymous("plan",plan.id)+" count="+selections.size()+" catalogRevision="+plan.catalog.revision);
        try{
        if(selections.isEmpty())throw new IOException("请先选择要替换的图标");requireCurrent(context,artwork,plan);
        AppCoverStore store=AppCoverStore.get(context);List<AppCoverStore.ArtworkChange> changes=new ArrayList<>();int total=selections.size(),done=0;
        try{
            trace.stage("PREPARE");
            for(Map.Entry<String,String> selected:selections.entrySet()){
                control.check();Row row=null;for(Row candidate:plan.rows)if(candidate.pkg.equals(selected.getKey()))row=candidate;
                IconLibraryCatalog.Item item=plan.selection(row,selected.getValue(),coverCustom);
                AppCoverPolicy.require(context,row.pkg);if(!row.key.equals(store.key(row.pkg)))throw new IOException(row.label+" 的图标已变化，请重新匹配");
                File ready=store.prepared();try{
                    JSONObject metadata;
                    if(item==null){if(!row.originalKey.equals(IconLibraryFrame.sourceKey(context,row.pkg,row.label)))throw new IOException("应用原图标已变化，请重新匹配");IconLibraryFrame.prepare(context,row.pkg,row.label,plan.style,plan.shape,plan.form,ready,control.request());metadata=LibraryCover.frameMetadata(plan.style,plan.shape,plan.form,row.originalKey);}
                    else{IconLibraryArtwork.prepare(artwork,item,plan.shape,plan.form,ready,true,control.request());metadata=LibraryCover.metadata(item,plan.shape,plan.form,plan.catalog.revision);}
                    changes.add(new AppCoverStore.ArtworkChange(row.pkg,ready,metadata,row.key));
                }catch(JSONException invalid){throw new IOException(invalid);}finally{if(changes.isEmpty()||changes.get(changes.size()-1).file!=ready)store.discardCandidate(ready);}
                control.progress(++done,total);
            }
            control.check();for(AppCoverStore.ArtworkChange change:changes)AppCoverPolicy.require(context,change.pkg);
            DesktopBackups.get(context).beforeIconBatch();control.check();
            // Phone snapshots also acquire the cover lock before the repository lock.
            synchronized(store){synchronized(IconLibraryRepository.get(artwork)){requireCurrent(context,artwork,plan);control.check();control.committing();trace.stage("COMMIT");int applied=store.applyLibraryBatch(changes);trace.finish("COMPLETED","committed count="+applied);return applied;}}
        }finally{for(AppCoverStore.ArtworkChange change:changes)store.discardCandidate(change.file);}
        }catch(IOException|RuntimeException|OutOfMemoryError failure){trace.finish(failure instanceof InterruptedIOException?"CANCELLED":"FAILED",failure.getClass().getSimpleName());throw failure;}
    }
}
