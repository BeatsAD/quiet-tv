package home.quiet.tv;

import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import java.io.*;
import java.util.*;
import org.json.*;

/** Real desktop entries and artwork. Package enumeration is on demand, never the polling hot path. */
final class PhoneSettingsPreview {
    private static PhoneSettingsPreview instance;
    static synchronized PhoneSettingsPreview get(Context context){if(instance==null)instance=new PhoneSettingsPreview(context.getApplicationContext());return instance;}
    private final Context context;
    private long packageEpoch;
    private final BroadcastReceiver packages=new BroadcastReceiver(){public void onReceive(Context ignored,Intent event){synchronized(PhoneSettingsPreview.this){packageEpoch++;}}};
    private PhoneSettingsPreview(Context context){
        this.context=context;
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_PACKAGE_ADDED);filter.addAction(Intent.ACTION_PACKAGE_REMOVED);filter.addAction(Intent.ACTION_PACKAGE_CHANGED);filter.addDataScheme("package");
        if(android.os.Build.VERSION.SDK_INT>=33)Api33.register(context,packages,filter);else context.registerReceiver(packages,filter);
    }
    synchronized JSONObject signature()throws IOException {
        try{
            ChildModeStore child=new ChildModeStore(context);JSONObject rules=new JSONObject(child.preferences.getString("rules","{}")),hidden=new JSONObject();
            for(Iterator<String> keys=rules.keys();keys.hasNext();){String key=keys.next();JSONObject rule=rules.optJSONObject(key);if(rule!=null&&rule.optBoolean("hidden"))hidden.put(key,true);}
            return new JSONObject().put("packages",packageEpoch).put("hidden",new HomeVisibilityStore(context).signature()).put("order",new HomeOrderStore(context).signature())
                .put("covers",AppCoverStore.get(context).revision()).put("childEnabled",child.enabled()).put("childHidden",hidden);
        }catch(JSONException e){throw new IOException("应用预览状态无法读取",e);}
    }
    JSONArray entries()throws IOException {
        try{
            ArrayList<String> pinned=new DockStore(context).load();List<HomeAppCatalog.Entry> entries=new HomeAppCatalog(context).load(pinned);JSONArray out=new JSONArray();
            String version;synchronized(this){version=packageEpoch+"-"+AppCoverStore.get(context).revision();}
            for(HomeAppCatalog.Entry entry:entries){
                String encoded=java.net.URLEncoder.encode(entry.id,"UTF-8");JSONObject app=new JSONObject().put("id",entry.id).put("name",entry.label)
                    .put("cover","api/resources/cover/"+encoded+"?v="+version).put("icon","api/resources/icon/"+encoded+"?v="+version).put("coverFit",AppCoverStore.get(context).contain(entry.id)?"contain":"cover");
                if(entry.info!=null){PackageInfo info=context.getPackageManager().getPackageInfo(entry.id,0);app.put("version",info.versionName==null?String.valueOf(info.versionCode):info.versionName);long size=apkSize(info.applicationInfo);if(size>=0)app.put("apkBytes",size);app.put("system",(info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0);}
                out.put(app);
            }
            return out;
        }catch(JSONException|PackageManager.NameNotFoundException e){throw new IOException("应用列表已变化，请重新读取",e);}
    }
    private static long apkSize(ApplicationInfo info){
        if(info.sourceDir==null)return -1;long bytes=new File(info.sourceDir).length();if(bytes<=0)return -1;
        if(android.os.Build.VERSION.SDK_INT>=21&&info.splitSourceDirs!=null)for(String path:info.splitSourceDirs){long size=new File(path).length();if(size<=0)return -1;bytes+=size;}return bytes;
    }
    byte[] resource(String route)throws IOException {
        String[] parts=route.split("/",2);if(parts.length!=2)throw new PhoneSettingsServer.BadInput();
        String kind=parts[0],id=java.net.URLDecoder.decode(parts[1],"UTF-8");Bitmap image=null;
        if("quiz-art".equals(kind)){if(!ChildQuizArtwork.validFile(id))throw new PhoneSettingsServer.BadInput();try(InputStream input=context.getAssets().open("child-quiz/artwork/"+id+".svg");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int count;while((count=input.read(buffer))!=-1)out.write(buffer,0,count);return out.toByteArray();}}
        try{
            if("child-rest".equals(kind)){
                if(!Arrays.asList("cloud","forest","space").contains(id))throw new PhoneSettingsServer.BadInput();try(InputStream input=context.getAssets().open("child-rest/"+id+".webp")){BitmapFactory.Options sample=new BitmapFactory.Options();sample.inSampleSize=4;image=BitmapFactory.decodeStream(input,null,sample);}
            }else if("avatar".equals(kind)){
                String current=new DesktopSettingsStore(context).avatarFile();if(!id.equals(current))throw new PhoneSettingsServer.BadInput();File file=new GreetingAvatarStore(context).existing(current);if(file==null)throw new IOException("头像不可用");image=BitmapFactory.decodeFile(file.getPath());
            }else if("cover".equals(kind)||"icon".equals(kind)){
                if(id.startsWith("quiet:")){if(!Arrays.asList(HomeAppCatalog.WATCH_TV,HomeAppCatalog.DESKTOP_SETTINGS).contains(id))throw new PhoneSettingsServer.BadInput();image=HomeEntryArtwork.render(context,id.equals(HomeAppCatalog.WATCH_TV)?"看电视":"桌面设置",id);}
                else{
                    if(!AppCoverStore.packageName(id))throw new PhoneSettingsServer.BadInput();
                    ApplicationInfo info=context.getPackageManager().getApplicationInfo(id,"icon".equals(kind)?HomeController.METADATA:0);
                    if("cover".equals(kind)){android.content.pm.ResolveInfo launch=new HomeAppCatalog(context).find(id);image=launch==null?AppArtwork.load(context,id,info):AppArtwork.load(context,launch);}
                    else{Drawable icon=info.loadIcon(context.getPackageManager());image=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);icon.setBounds(0,0,96,96);icon.draw(canvas);}
                }
            }else throw new PhoneSettingsServer.BadInput();
            if(image==null)throw new IOException("图片不可用");ByteArrayOutputStream out=new ByteArrayOutputStream();if(!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("图片不可用");return out.toByteArray();
        }catch(PackageManager.NameNotFoundException|OutOfMemoryError e){throw new IOException("应用图片不可用",e);}finally{if(image!=null)image.recycle();}
    }
    @android.annotation.TargetApi(33) private static final class Api33 {static void register(Context context,BroadcastReceiver receiver,IntentFilter filter){context.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);}}
}
