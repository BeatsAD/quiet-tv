package home.quiet.tv;

import android.content.*;
import android.net.Uri;
import android.os.Build;
import java.io.*;
import java.util.*;
import org.json.*;

/** The received-file store and the TV's existing read-only, permission-scoped source browser. */
final class PhoneSettingsFiles {
    static final String PERMISSION_JOB="phone-file-permission";
    private final Context context;private final PhoneSettingsTasks tasks;private final String scope;private final Runnable changed;
    private final Map<String,Node> nodes=new HashMap<>();private JSONArray listing=new JSONArray();private String folder="",source="received",parent="",offeredFile="";
    private String folderId="",browseStatus="";private long offerVersion;
    private static final class BrowseIssue extends IOException {
        final String status;
        BrowseIssue(String status){this.status=status;}
    }
    private static final class Node{final FileTransferSource.Item item;final File root;final Uri tree;final String parent;Node(FileTransferSource.Item item,File root,Uri tree,String parent){this.item=item;this.root=root;this.tree=tree;this.parent=parent;}}
    PhoneSettingsFiles(Context context,PhoneSettingsTasks tasks,String scope,Runnable changed){this.context=context;this.tasks=tasks;this.scope=scope;this.changed=changed;}
    private FileTransferStore store()throws IOException{return new FileTransferStore(new File(context.getFilesDir(),"received-files"));}
    long maximum()throws IOException{FileTransferStore store=store();if(store.list().size()>=FileTransferPolicy.MAX_FILES)return 0;return Math.max(0,Math.min(FileTransferPolicy.MAX_FILE,Math.min(FileTransferPolicy.MAX_TOTAL-store.used()-4096,new File(context.getFilesDir(),"received-files").getUsableSpace()-FileTransferPolicy.RESERVE)));}
    JSONObject state()throws IOException{try{JSONArray entries=new JSONArray();for(FileTransferStore.Entry file:store().list())entries.put(file.json());synchronized(this){return new JSONObject().put("receivedFiles",entries).put("sourceFiles",listing).put("fileSource",source).put("fileFolder",folder).put("fileParent",parent).put("fileFolderId",folderId).put("fileBrowseStatus",browseStatus).put("offeredFile",offeredFile).put("fileOfferVersion",offerVersion);}}catch(JSONException e){throw new IOException(e);}}
    void offer(String id)throws IOException{store().entry(id);synchronized(this){offeredFile=id;offerVersion++;}changed.run();}
    JSONObject receive(InputStream input,long length,String name,PhoneSettingsServer.Active active,FileTransferPolicy.Progress progress)throws IOException{FileTransferStore.Entry entry=store().receive(input,length,name,active::valid,progress);changed.run();try{return entry.json();}catch(JSONException e){throw new IOException(e);}}
    PhoneSettingsServer.Download download(String id)throws IOException{
        if(!id.startsWith("source-")){FileTransferStore.Entry entry=store().entry(id);return new PhoneSettingsServer.Download(new FileInputStream(store().file(id)),entry.bytes,entry.name,"application/octet-stream");}
        Node node=sourceFile(id.substring(7));require(node);if(node.item.bytes>FileTransferPolicy.MAX_FILE)throw new PhoneSettingsServer.Rejected("文件超过电视文件传输上限");
        InputStream input=node.item.file==null?context.getContentResolver().openInputStream(node.item.uri):new FileInputStream(node.item.file);if(input==null)throw new IOException("文件无法读取");return new PhoneSettingsServer.Download(new FilterInputStream(input){long received;public int read(byte[] b,int off,int count)throws IOException{int n=in.read(b,off,(int)Math.min(count,FileTransferPolicy.MAX_FILE-received+1));if(n>0)received+=n;if(received>FileTransferPolicy.MAX_FILE)throw new IOException("文件超过电视传输上限");return n;}},node.item.bytes,FileTransferPolicy.name(node.item.name),"application/octet-stream");
    }
    PhoneSettingsServer.Download preview(String id)throws IOException{
        PhoneSettingsServer.Download file=download(id);
        String name=file.name.toLowerCase(Locale.ROOT),mime="";
        if(FileTransferPolicy.image(name))mime=name.endsWith(".png")?"image/png":name.endsWith(".webp")?"image/webp":"image/jpeg";
        if(FileTransferPolicy.video(name))mime=name.endsWith(".mov")?"video/quicktime":name.endsWith(".mkv")?"video/x-matroska":name.endsWith(".webm")?"video/webm":"video/mp4";
        if(mime.isEmpty()||file.bytes<=0){file.input.close();throw new PhoneSettingsServer.Rejected("此文件暂无法在手机预览，可以下载到手机");}
        return new PhoneSettingsServer.Download(file.input,file.bytes,file.name,mime);
    }
    private void require(Node node)throws IOException{
        if(node.tree!=null){if(Build.VERSION.SDK_INT<21||!FileTransferSource.Documents.persistedRead(context,node.tree))throw new BrowseIssue("permission");return;}
        File canonical=node.item.file.getCanonicalFile(),root=node.root.getCanonicalFile();
        if(!sharedPath(root,canonical))throw new PhoneSettingsServer.BadInput();
        boolean mounted=false;List<FileTransferSource.Item> roots=new ArrayList<>(FileTransferSource.volumes(context,false));roots.addAll(FileTransferSource.volumes(context,true));for(FileTransferSource.Item item:roots)if(root.equals(item.file.getCanonicalFile()))mounted=true;if(!mounted)throw new BrowseIssue("unavailable");
        if(!canonical.canRead())throw new BrowseIssue("permission");
    }
    private static boolean sharedPath(File root,File file){
        if(file.equals(root))return true;
        String prefix=root.getPath()+File.separator,path=file.getPath();
        if(!path.startsWith(prefix))return false;
        String relative=path.substring(prefix.length()).toLowerCase(Locale.ROOT);
        return !relative.equals("android/data")&&!relative.startsWith("android/data/")
            &&!relative.equals("android/obb")&&!relative.startsWith("android/obb/");
    }
    JSONObject command(String field,Object value,PhoneSettingsServer.Active active)throws IOException{
        if(field.equals("install")&&value instanceof String){
            String id=(String)value;
            if(!id.startsWith("source-"))return openInstaller(id,active);
            Node node=sourceFile(id.substring(7));
            if(!FileTransferPolicy.apk(node.item.name))throw new PhoneSettingsServer.BadInput();
            PhoneSettingsTasks.Job job=tasks.begin(scope+"/files/install/"+id,"在电视安装应用",id,active,"files","install");
            tasks.start(job,work->{
                JSONObject received=copySource(node,work);
                work.check();
                try{openInstaller(received.getString("id"),work::active);}
                catch(IOException failure){throw new PhoneSettingsTasks.Problem("安装包已保存到已接收，请从已接收重新发起安装",false);}
                work.stage("已保存安装包，请在电视确认安装",100);
                return new JSONObject().put("waitingTelevision",true);
            });
            return tasks.receipt(job);
        }
        if(field.equals("remove")&&value instanceof String){if(!active.valid())throw new PhoneSettingsServer.Ended();store().remove((String)value);context.getSharedPreferences("file-transfer-wallpapers",0).edit().remove((String)value).apply();changed.run();return null;}
        if(field.equals("permission")){
            String requested=Boolean.TRUE.equals(value)?("usb".equals(source)?"usb":"local"):value instanceof String?(String)value:"";
            if(!"local".equals(requested)&&!"usb".equals(requested))throw new PhoneSettingsServer.BadInput();
            if(FileTransferSource.directoryPicker(context)==null&&FileTransferSource.missingPermissions(context).length==0
                &&!FileTransferSource.partialMediaAccess(context))
                throw new PhoneSettingsServer.Rejected("此电视没有可用的目录选择器，可继续浏览可读目录或使用局域网传输");
            return new PhoneSettingsActions(context).onMain(()->{
                PhoneSettingsTasks.Job job=tasks.begin(scope+"/files/permission", "电视存储授权", requested, active);
                if(!job.scheduled){
                    job.scheduled=true;job.cancellable=false;job.message="正在打开电视授权";
                    try{context.startActivity(new Intent(context,AppManagementActivity.class).putExtra("management-page",SettingsPages.FILES)
                        .putExtra(PERMISSION_JOB,job.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
                    catch(RuntimeException failure){tasks.fail(job,"无法打开电视授权，请先在电视打开 Quiet TV 后重试");}
                    if(!job.terminal())new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
                        if("queued".equals(job.state))tasks.fail(job,"电视未打开授权，请先在电视打开 Quiet TV 后重试");
                    },5000);
                }
                return tasks.receipt(job);
            },active);
        }
        if(field.equals("browse")&&value instanceof JSONObject){JSONObject request=(JSONObject)value;if(request.length()!=2||!(request.opt("source") instanceof String)||!(request.opt("id") instanceof String))throw new PhoneSettingsServer.BadInput();String nextSource=request.optString("source"),id=request.optString("id");if(!Arrays.asList("received","local","usb").contains(nextSource))throw new PhoneSettingsServer.BadInput();
            PhoneSettingsTasks.Job job=tasks.begin(scope+"/files/browse","读取电视文件",request.toString(),active);tasks.start(job,work->{
                try{browse(nextSource,id,work);}
                catch(InterruptedIOException cancelled){throw cancelled;}
                catch(BrowseIssue issue){publishBrowseIssue(nextSource,id,issue.status,work);}
                catch(IOException|SecurityException failure){publishBrowseIssue(nextSource,id,"failed",work);}
                changed.run();work.stage("目录状态已更新",100);return new JSONObject();
            });return tasks.receipt(job);
        }
        if(field.equals("receive")&&value instanceof String){
            Node node=sourceFile((String)value);
            PhoneSettingsTasks.Job job=tasks.begin(scope+"/files/receive/"+value,"保存电视文件",(String)value,active,"files","receive");
            tasks.start(job,work->{JSONObject result=copySource(node,work);work.stage("已保存到已接收",100);return result;});
            return tasks.receipt(job);
        }
        throw new PhoneSettingsServer.BadInput();
    }
    private synchronized Node sourceFile(String id)throws IOException{
        Node node=nodes.get(id);
        if(node==null||node.item.directory)throw new PhoneSettingsServer.BadInput();
        return node;
    }
    private JSONObject copySource(Node node,PhoneSettingsTasks.Job work)throws IOException{
        require(node);work.check();
        try(InputStream input=node.item.file==null?context.getContentResolver().openInputStream(node.item.uri):new FileInputStream(node.item.file)){
            if(input==null)throw new IOException("文件无法读取");
            return receive(input,node.item.bytes,node.item.name,work::active,
                (n,total)->work.stage("正在保存到已接收",total>0?(int)(n*100/total):-1));
        }
    }
    private JSONObject openInstaller(String id,PhoneSettingsServer.Active active)throws IOException{
        FileTransferStore.Entry entry=store().entry(id);
        if(!FileTransferPolicy.apk(entry.name))throw new PhoneSettingsServer.BadInput();
        return new PhoneSettingsActions(context).onMain(()->{
            context.startActivity(new Intent(context,ApkInstallActivity.class).putExtra("file",entry.id)
                .putExtra("begin-install",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return new JSONObject().put("message","已请求在电视核对安装包，请完成系统确认").put("state","waitingTelevision");
        },active);
    }
    private void browse(String nextSource,String id,PhoneSettingsTasks.Job work)throws IOException,JSONException{
        work.check();
        List<FileTransferSource.Item> values;Node parentNode=null;String nextFolder="",up="";File root=null;Uri tree=null;
        if(nextSource.equals("received")){values=Collections.emptyList();}
        else if(id.isEmpty()){
            if(Build.VERSION.SDK_INT>=21){tree=FileTransferSource.Documents.remembered(context,nextSource.equals("usb"));if(tree!=null&&!FileTransferSource.Documents.persistedRead(context,tree))tree=null;}
            if(tree!=null){values=FileTransferSource.Documents.children(context,tree,FileTransferSource.Documents.root(tree));nextFolder=nextSource.equals("usb")?"已授权 U 盘":"已授权目录";}
            else{
                values=FileTransferSource.volumes(context,nextSource.equals("usb"));
                if(values.isEmpty())throw new BrowseIssue("local".equals(nextSource)&&FileTransferSource.missingPermissions(context).length>0?"permission":"unavailable");
                if("local".equals(nextSource)&&values.size()==1){
                    FileTransferSource.Item item=values.get(0);
                    values=Collections.singletonList(new FileTransferSource.Item("电视存储根目录",item.bytes,true,item.uri,item.file));
                }
            }
        }else{
            synchronized(this){if(!source.equals(nextSource))throw new BrowseIssue("stale");parentNode=nodes.get(id);}
            if(parentNode==null||!parentNode.item.directory)throw new BrowseIssue("stale");
            require(parentNode);root=parentNode.root;tree=parentNode.tree;up=parentNode.parent;nextFolder=parentNode.item.name;
            if(tree!=null)values=FileTransferSource.Documents.children(context,tree,parentNode.item.uri);
            else{
                try{values=FileTransferSource.children(parentNode.item.file);}
                catch(IOException failure){
                    if(FileTransferSource.missingPermissions(context).length>0)throw new BrowseIssue("permission");
                    throw failure;
                }
                File canonicalRoot=root.getCanonicalFile();
                for(Iterator<FileTransferSource.Item> items=values.iterator();items.hasNext();)
                    if(!sharedPath(canonicalRoot,items.next().file.getCanonicalFile()))items.remove();
            }
        }
        JSONArray out=new JSONArray();synchronized(this){work.check();if(id.isEmpty())nodes.clear();if(nodes.size()+values.size()>2000)throw new IOException("目录项过多，请从根目录重新读取较小目录");for(FileTransferSource.Item item:values){String key=UUID.randomUUID().toString();File boundary=root==null&&tree==null?item.file:root;nodes.put(key,new Node(item,boundary,tree,id));out.put(new JSONObject().put("id",key).put("name",item.name).put("directory",item.directory).put("bytes",item.bytes));}listing=out;source=nextSource;folder=nextFolder;parent=up;folderId=id;browseStatus="ready";}
    }
    private synchronized void publishBrowseIssue(String nextSource,String id,String status,PhoneSettingsTasks.Job work)throws InterruptedIOException{
        work.check();
        if(!source.equals(nextSource)||id.isEmpty())nodes.clear();
        listing=new JSONArray();source=nextSource;folder="";parent="";folderId=id;browseStatus=status;
    }
    MemoStore memos(){return new MemoStore(new File(context.getFilesDir(),"memos"));}
    JSONObject notes()throws IOException{try{JSONArray out=new JSONArray();for(MemoStore.Note note:memos().list())out.put(new JSONObject().put("id",note.id).put("text",note.text).put("done",note.done));return new JSONObject().put("notes",out);}catch(JSONException e){throw new IOException(e);}}
    void note(String field,Object value,PhoneSettingsServer.Active active)throws IOException{
        if(!active.valid())throw new PhoneSettingsServer.Ended();
        if(field.equals("save")&&value instanceof JSONObject){JSONObject note=(JSONObject)value;if(note.length()!=2||!(note.opt("id") instanceof String)||!(note.opt("text") instanceof String))throw new PhoneSettingsServer.BadInput();String id=note.optString("id"),text=note.optString("text").trim();if(!FileTransferPolicy.id(id)||text.isEmpty()||text.length()>MemoStore.MAX_TEXT)throw new PhoneSettingsServer.BadInput();memos().save(id,text);}
        else if(field.equals("toggle")&&value instanceof String)memos().toggle((String)value);
        else if(field.equals("remove")&&value instanceof String)memos().remove((String)value);
        else throw new PhoneSettingsServer.BadInput();changed.run();
    }
}
