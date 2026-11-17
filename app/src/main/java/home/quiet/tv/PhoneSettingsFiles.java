package home.quiet.tv;

import android.content.*;
import android.net.Uri;
import android.os.Build;
import java.io.*;
import java.util.*;
import org.json.*;

/** The received-file store and the TV's existing read-only, permission-scoped source browser. */
final class PhoneSettingsFiles {
    private final Context context;private final PhoneSettingsTasks tasks;private final String scope;private final Runnable changed;
    private final Map<String,Node> nodes=new HashMap<>();private JSONArray listing=new JSONArray();private String folder="",source="received",parent="",offeredFile="";private long offerVersion;
    private static final class Node{final FileTransferSource.Item item;final File root;final Uri tree;final String parent;Node(FileTransferSource.Item item,File root,Uri tree,String parent){this.item=item;this.root=root;this.tree=tree;this.parent=parent;}}
    PhoneSettingsFiles(Context context,PhoneSettingsTasks tasks,String scope,Runnable changed){this.context=context;this.tasks=tasks;this.scope=scope;this.changed=changed;}
    private FileTransferStore store()throws IOException{return new FileTransferStore(new File(context.getFilesDir(),"received-files"));}
    long maximum()throws IOException{FileTransferStore store=store();if(store.list().size()>=FileTransferPolicy.MAX_FILES)return 0;return Math.max(0,Math.min(FileTransferPolicy.MAX_FILE,Math.min(FileTransferPolicy.MAX_TOTAL-store.used()-4096,new File(context.getFilesDir(),"received-files").getUsableSpace()-FileTransferPolicy.RESERVE)));}
    JSONObject state()throws IOException{try{JSONArray entries=new JSONArray();for(FileTransferStore.Entry file:store().list())entries.put(file.json());synchronized(this){return new JSONObject().put("receivedFiles",entries).put("sourceFiles",listing).put("fileSource",source).put("fileFolder",folder).put("fileParent",parent).put("offeredFile",offeredFile).put("fileOfferVersion",offerVersion);}}catch(JSONException e){throw new IOException(e);}}
    void offer(String id)throws IOException{store().entry(id);synchronized(this){offeredFile=id;offerVersion++;}changed.run();}
    JSONObject receive(InputStream input,long length,String name,PhoneSettingsServer.Active active,FileTransferPolicy.Progress progress)throws IOException{FileTransferStore.Entry entry=store().receive(input,length,name,active::valid,progress);changed.run();try{return entry.json();}catch(JSONException e){throw new IOException(e);}}
    PhoneSettingsServer.Download download(String id)throws IOException{
        if(!id.startsWith("source-")){FileTransferStore.Entry entry=store().entry(id);return new PhoneSettingsServer.Download(new FileInputStream(store().file(id)),entry.bytes,entry.name,"application/octet-stream");}
        Node node;synchronized(this){node=nodes.get(id.substring(7));}if(node==null||node.item.directory)throw new PhoneSettingsServer.BadInput();require(node);if(node.item.bytes>FileTransferPolicy.MAX_FILE)throw new PhoneSettingsServer.Rejected("文件超过电视文件传输上限");
        InputStream input=node.item.file==null?context.getContentResolver().openInputStream(node.item.uri):new FileInputStream(node.item.file);if(input==null)throw new IOException("文件无法读取");return new PhoneSettingsServer.Download(new FilterInputStream(input){long received;public int read(byte[] b,int off,int count)throws IOException{int n=in.read(b,off,(int)Math.min(count,FileTransferPolicy.MAX_FILE-received+1));if(n>0)received+=n;if(received>FileTransferPolicy.MAX_FILE)throw new IOException("文件超过电视传输上限");return n;}},node.item.bytes,FileTransferPolicy.name(node.item.name),"application/octet-stream");
    }
    private void require(Node node)throws IOException{
        if(node.tree!=null){if(Build.VERSION.SDK_INT<21||!FileTransferSource.Documents.persistedRead(context,node.tree))throw new IOException("目录授权已失效，请在电视重新授权");return;}
        if(FileTransferSource.missingPermissions(context).length>0)throw new IOException("请先在电视授权存储访问");File canonical=node.item.file.getCanonicalFile(),root=node.root.getCanonicalFile();if(!canonical.equals(root)&&!canonical.getPath().startsWith(root.getPath()+File.separator))throw new PhoneSettingsServer.BadInput();
        boolean mounted=false;List<FileTransferSource.Item> roots=new ArrayList<>(FileTransferSource.volumes(context,false));roots.addAll(FileTransferSource.volumes(context,true));for(FileTransferSource.Item item:roots)if(root.equals(item.file.getCanonicalFile()))mounted=true;if(!mounted)throw new IOException("存储已移除，请重新选择目录");
    }
    JSONObject command(String field,Object value,PhoneSettingsServer.Active active)throws IOException{
        if(field.equals("install")&&value instanceof String){FileTransferStore.Entry entry=store().entry((String)value);if(!FileTransferPolicy.apk(entry.name))throw new PhoneSettingsServer.BadInput();return new PhoneSettingsActions(context).onMain(()->{context.startActivity(new Intent(context,ApkInstallActivity.class).putExtra("file",entry.id).putExtra("begin-install",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return new JSONObject().put("message","已请求在电视核对安装包并继续安装，请完成系统确认后刷新应用列表").put("state","waitingTelevision");},active);}
        if(field.equals("remove")&&value instanceof String){if(!active.valid())throw new PhoneSettingsServer.Ended();store().remove((String)value);context.getSharedPreferences("file-transfer-wallpapers",0).edit().remove((String)value).apply();changed.run();return null;}
        if(field.equals("permission")&&Boolean.TRUE.equals(value))return new PhoneSettingsActions(context).onMain(()->{context.startActivity(new Intent(context,AppManagementActivity.class).putExtra("management-page",FileTransferPanel.PAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return new JSONObject().put("message","请在电视选择本地存储或 U 盘并完成目录授权，返回手机后刷新").put("state","waitingTelevision");},active);
        if(field.equals("browse")&&value instanceof JSONObject){JSONObject request=(JSONObject)value;if(request.length()!=2||!(request.opt("source") instanceof String)||!(request.opt("id") instanceof String))throw new PhoneSettingsServer.BadInput();String nextSource=request.optString("source"),id=request.optString("id");if(!Arrays.asList("received","local","usb").contains(nextSource))throw new PhoneSettingsServer.BadInput();
            PhoneSettingsTasks.Job job=tasks.begin(scope+"/files/browse","读取电视文件",request.toString(),active);tasks.start(job,work->{work.check();browse(nextSource,id);work.check();changed.run();work.stage("文件列表已更新",100);return new JSONObject();});return tasks.receipt(job);
        }
        if(field.equals("receive")&&value instanceof String){Node node;synchronized(this){node=nodes.get(value);}if(node==null||node.item.directory)throw new PhoneSettingsServer.BadInput();PhoneSettingsTasks.Job job=tasks.begin(scope+"/files/receive/"+value,"保存电视文件",(String)value,active);tasks.start(job,work->{require(node);work.check();try(InputStream input=node.item.file==null?context.getContentResolver().openInputStream(node.item.uri):new FileInputStream(node.item.file)){if(input==null)throw new IOException("文件无法读取");JSONObject result=receive(input,node.item.bytes,node.item.name,work::active,(n,total)->work.stage("正在保存到已接收",total>0?(int)(n*100/total):-1));work.stage("已保存到已接收",100);return result;}});return tasks.receipt(job);}
        throw new PhoneSettingsServer.BadInput();
    }
    private void browse(String nextSource,String id)throws IOException,JSONException{
        List<FileTransferSource.Item> values;Node parentNode=null;String nextFolder="",up="";File root=null;Uri tree=null;
        if(nextSource.equals("received")){values=Collections.emptyList();}
        else if(id.isEmpty()){
            if(Build.VERSION.SDK_INT>=21){tree=FileTransferSource.Documents.remembered(context,nextSource.equals("usb"));if(tree!=null&&!FileTransferSource.Documents.persistedRead(context,tree))tree=null;}
            if(tree!=null){values=FileTransferSource.Documents.children(context,tree,FileTransferSource.Documents.root(tree));nextFolder=nextSource.equals("usb")?"已授权 U 盘":"已授权目录";}
            else{if(FileTransferSource.missingPermissions(context).length>0)throw new IOException("请先在电视完成存储或目录授权");values=FileTransferSource.volumes(context,nextSource.equals("usb"));}
        }else{synchronized(this){parentNode=nodes.get(id);}if(parentNode==null||!parentNode.item.directory)throw new IOException("目录已变化，请从存储根目录重新读取");require(parentNode);root=parentNode.root;tree=parentNode.tree;up=parentNode.parent;nextFolder=parentNode.item.name;values=tree==null?FileTransferSource.children(parentNode.item.file):FileTransferSource.Documents.children(context,tree,parentNode.item.uri);}
        JSONArray out=new JSONArray();synchronized(this){if(id.isEmpty())nodes.clear();if(nodes.size()+values.size()>2000)throw new IOException("目录项过多，请从根目录重新读取较小目录");for(FileTransferSource.Item item:values){String key=UUID.randomUUID().toString();File boundary=root==null&&tree==null?item.file:root;nodes.put(key,new Node(item,boundary,tree,id));out.put(new JSONObject().put("id",key).put("name",item.name).put("directory",item.directory).put("bytes",item.bytes));}listing=out;source=nextSource;folder=nextFolder;parent=up;}
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
